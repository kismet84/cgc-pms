import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { z } from "zod";
import { execute } from "./neo4j.js";
import { isBlocked } from "./policy.js";

export const ISSUE_REGISTER_PATH = "docs/backlog/current-issues.json";

const issueSchema = z.object({
  issueKey: z.string().min(1).max(200),
  title: z.string().min(1).max(500),
  status: z.enum(["OPEN", "NEEDS_CONFIRMATION", "FROZEN", "OBSERVATION", "RELEASE_GATE"]),
  classification: z.enum([
    "STILL_APPLICABLE",
    "NEEDS_CONFIRMATION",
    "NON_BLOCKING_OBSERVATION",
    "OPERATIONAL_RISK",
    "RELEASE_PREREQUISITE",
  ]),
  priority: z.enum(["P0", "P1", "P2"]),
  blocking: z.boolean(),
  parentIssueKey: z.string().min(1).max(200).nullable().optional(),
  summary: z.string().min(1).max(4000),
  acceptanceCriteria: z.string().min(1).max(4000),
  deferReason: z.string().max(2000).nullable().optional(),
  sourceRefs: z.array(z.string().min(1).max(1000)).min(1).max(20),
}).strict();

export const issueRegisterSchema = z.object({
  schemaVersion: z.literal(1),
  versionScope: z.literal("v1.5"),
  updatedAt: z.string().datetime({ offset: true }),
  issues: z.array(issueSchema).max(500),
}).strict();

export function parseIssueRegister(raw) {
  const register = issueRegisterSchema.parse(typeof raw === "string" ? JSON.parse(raw) : raw);
  const keys = new Set();
  const byKey = new Map();
  for (const issue of register.issues) {
    if (keys.has(issue.issueKey)) throw new Error(`Duplicate issueKey: ${issue.issueKey}`);
    keys.add(issue.issueKey);
    byKey.set(issue.issueKey, issue);
    if (new Set(issue.sourceRefs).size !== issue.sourceRefs.length) {
      throw new Error(`Duplicate sourceRefs for ${issue.issueKey}`);
    }
    for (const sourceRef of issue.sourceRefs) {
      const normalized = sourceRef.replace(/\\/g, "/");
      if (path.isAbsolute(sourceRef) || normalized.startsWith("../") || isBlocked(normalized)) {
        throw new Error(`Unsafe sourceRef ${sourceRef} for ${issue.issueKey}`);
      }
    }
  }
  for (const issue of register.issues) {
    if (issue.parentIssueKey && !keys.has(issue.parentIssueKey)) {
      throw new Error(`Unknown parentIssueKey ${issue.parentIssueKey} for ${issue.issueKey}`);
    }
    if (issue.parentIssueKey === issue.issueKey) throw new Error(`Issue cannot parent itself: ${issue.issueKey}`);
    const visited = new Set([issue.issueKey]);
    let parentKey = issue.parentIssueKey;
    while (parentKey) {
      if (visited.has(parentKey)) throw new Error(`Issue parent cycle detected at ${parentKey}`);
      visited.add(parentKey);
      parentKey = byKey.get(parentKey)?.parentIssueKey;
    }
  }
  return register;
}

export function loadIssueRegister(repoRoot) {
  const absolutePath = path.join(repoRoot, ISSUE_REGISTER_PATH);
  if (!fs.existsSync(absolutePath)) throw new Error(`Current issue register is missing: ${ISSUE_REGISTER_PATH}`);
  const content = fs.readFileSync(absolutePath, "utf8");
  return {
    register: parseIssueRegister(content),
    sha256: crypto.createHash("sha256").update(content).digest("hex"),
  };
}

export async function collectIssueRegistry(driver, config, runId) {
  const { register, sha256 } = loadIssueRegister(config.repoRoot);
  const issues = register.issues.map((issue) => ({
    ...issue,
    id: `${config.projectKey}:issue:${issue.issueKey}`,
    parentIssueKey: issue.parentIssueKey ?? null,
    deferReason: issue.deferReason ?? null,
  }));
  const issueKeys = issues.map((issue) => issue.issueKey);

  await execute(driver, config.neo4jDatabase, `
    MATCH (p:Project {key: $projectKey}),
          (r:CollectionRun {id: $runId}),
          (register:Artifact {path: $registerPath})
    UNWIND $issues AS issue
    MERGE (i:Issue {id: issue.id})
    SET i.issueKey = issue.issueKey, i.title = issue.title,
        i.status = issue.status, i.classification = issue.classification,
        i.priority = issue.priority, i.blocking = issue.blocking,
        i.parentIssueKey = issue.parentIssueKey, i.summary = issue.summary,
        i.acceptanceCriteria = issue.acceptanceCriteria,
        i.deferReason = issue.deferReason, i.sourceRefs = issue.sourceRefs,
        i.current = true, i.active = true, i.historical = false,
        i.versionScope = $versionScope, i.registerSha256 = $sha256,
        i.updatedAt = datetime($updatedAt), i.lastSeenAt = datetime()
    REMOVE i.closedAt
    MERGE (i)-[:IN_PROJECT]->(p)
    MERGE (i)-[:DEFINED_IN]->(register)
    MERGE (i)-[:SEEN_IN]->(r)
  `, {
    projectKey: config.projectKey,
    runId,
    registerPath: ISSUE_REGISTER_PATH,
    issues,
    versionScope: register.versionScope,
    updatedAt: register.updatedAt,
    sha256,
  }, "WRITE");

  const deactivated = await execute(driver, config.neo4jDatabase, `
    MATCH (i:Issue)-[:IN_PROJECT]->(:Project {key: $projectKey})
    WHERE coalesce(i.current, false) = true AND NOT i.issueKey IN $issueKeys
    SET i.current = false, i.active = false, i.closedAt = datetime(), i.lastSeenAt = datetime()
    RETURN count(i) AS count
  `, { projectKey: config.projectKey, issueKeys }, "WRITE");

  await execute(driver, config.neo4jDatabase, `
    MATCH (i:Issue)-[:IN_PROJECT]->(:Project {key: $projectKey})
    WHERE i.issueKey IN $issueKeys
    OPTIONAL MATCH (i)-[oldParent:PART_OF]->(:Issue)
    DELETE oldParent
    WITH DISTINCT i
    OPTIONAL MATCH (i)-[oldSource:SUPPORTED_BY]->(:Artifact)
    DELETE oldSource
  `, { projectKey: config.projectKey, issueKeys }, "WRITE");

  await execute(driver, config.neo4jDatabase, `
    MATCH (i:Issue)-[:IN_PROJECT]->(:Project {key: $projectKey})
    WHERE i.issueKey IN $issueKeys AND i.parentIssueKey IS NOT NULL
    MATCH (parent:Issue {issueKey: i.parentIssueKey})-[:IN_PROJECT]->(:Project {key: $projectKey})
    MERGE (i)-[:PART_OF]->(parent)
  `, { projectKey: config.projectKey, issueKeys }, "WRITE");

  await execute(driver, config.neo4jDatabase, `
    MATCH (i:Issue)-[:IN_PROJECT]->(:Project {key: $projectKey})
    WHERE i.issueKey IN $issueKeys
    UNWIND i.sourceRefs AS sourceRef
    MATCH (a:Artifact {path: sourceRef})-[:IN_PROJECT]->(:Project {key: $projectKey})
    MERGE (i)-[:SUPPORTED_BY]->(a)
  `, { projectKey: config.projectKey, issueKeys }, "WRITE");

  return { indexed: issues.length, deactivated: Number(deactivated[0]?.count ?? 0), sha256 };
}
