import crypto from "node:crypto";
import { execute, jsonSafe } from "./neo4j.js";

function rows(result) {
  return jsonSafe(result);
}

export async function status(driver, config) {
  return rows(await execute(driver, config.neo4jDatabase, `
    MATCH (p:Project {key: $projectKey})
    OPTIONAL MATCH (a:Artifact)-[:IN_PROJECT]->(p)
    OPTIONAL MATCH (c:GitCommit)-[:IN_PROJECT]->(p)
    OPTIONAL MATCH (e:Episode)-[:IN_PROJECT]->(p)
    RETURN p.key AS project, count(DISTINCT a) AS artifacts,
           count(DISTINCT c) AS commits, count(DISTINCT e) AS episodes,
           max(a.indexedAt) AS lastIndexedAt
  `, { projectKey: config.projectKey }));
}

export async function search(driver, config, query, limit = 20) {
  const safeLimit = Math.max(1, Math.min(Number(limit) || 20, 50));
  return rows(await execute(driver, config.neo4jDatabase, `
    CALL db.index.fulltext.queryNodes('knowledge_text', $query, {limit: $limit})
    YIELD node, score
    OPTIONAL MATCH (a:Artifact)-[:CONTAINS]->(node)
    RETURN labels(node) AS labels, node.id AS id, node.title AS title,
           coalesce(node.path, a.path) AS sourcePath,
           coalesce(node.summary, left(node.content, 500)) AS excerpt, score
    ORDER BY score DESC
  `, { query, limit: safeLimit }));
}

export async function getArtifact(driver, config, sourcePath) {
  return rows(await execute(driver, config.neo4jDatabase, `
    MATCH (a:Artifact {path: $path})
    OPTIONAL MATCH (a)-[:CONTAINS]->(s:Section)
    RETURN a { .id, .path, .title, .kind, .sha256, .modifiedAt, .indexedAt, .active } AS artifact,
           collect(s { .id, .title, .level, .ordinal, .content }) AS sections
  `, { path: sourcePath }));
}

export async function neighbors(driver, config, id, depth = 1) {
  const safeDepth = Math.max(1, Math.min(Number(depth) || 1, 3));
  const query = `
    MATCH (n {id: $id})
    MATCH path=(n)-[*1..${safeDepth}]-(other)
    RETURN DISTINCT labels(other) AS labels, other.id AS id,
           coalesce(other.title, other.name, other.subject, other.path) AS name,
           [r IN relationships(path) | type(r)] AS relationships
    LIMIT 100
  `;
  return rows(await execute(driver, config.neo4jDatabase, query, { id }));
}

export function assertReadOnlyCypher(query) {
  const normalized = query.replace(/\/\*[\s\S]*?\*\//g, " ").replace(/\/\/.*$/gm, " ").trim();
  if (!/^(MATCH|OPTIONAL\s+MATCH|WITH|RETURN|UNWIND|SHOW)\b/i.test(normalized)) throw new Error("Only read-only Cypher is allowed.");
  if (/\b(CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP|LOAD\s+CSV|FOREACH|CALL|GRANT|DENY|REVOKE|TERMINATE)\b/i.test(normalized)) {
    throw new Error("Mutating or procedure Cypher is not allowed.");
  }
}

export async function readOnlyQuery(driver, config, query, params = {}) {
  assertReadOnlyCypher(query);
  const normalized = query.trim().replace(/;+$/, "");
  const bounded = /\bLIMIT\s+\d+\s*$/i.test(normalized) ? normalized : `${normalized}\nLIMIT 200`;
  return rows(await execute(driver, config.neo4jDatabase, bounded, params));
}

export async function recordEpisode(driver, config, input) {
  const allowedKinds = new Set(["conversation", "decision", "run", "log-summary", "observation"]);
  if (!allowedKinds.has(input.kind)) throw new Error(`Unsupported episode kind: ${input.kind}`);
  if (!input.summary?.trim() || input.summary.length > 12000) throw new Error("summary is required and must be <= 12000 characters.");
  if (!input.sourceRef?.trim() || input.sourceRef.length > 1000) throw new Error("sourceRef is required and must be <= 1000 characters.");
  const occurredAt = input.occurredAt ?? new Date().toISOString();
  const id = input.id ?? `${config.projectKey}:episode:${crypto.createHash("sha256").update(`${input.kind}|${input.sourceRef}|${occurredAt}`).digest("hex")}`;
  return rows(await execute(driver, config.neo4jDatabase, `
    MATCH (p:Project {key: $projectKey})
    MERGE (e:Episode {id: $id})
    SET e.kind = $kind, e.title = $title, e.summary = $summary,
        e.sourceRef = $sourceRef, e.occurredAt = datetime($occurredAt),
        e.updatedAt = datetime()
    MERGE (e)-[:IN_PROJECT]->(p)
    RETURN e { .id, .kind, .title, .sourceRef, .occurredAt } AS episode
  `, { projectKey: config.projectKey, id, kind: input.kind, title: input.title ?? "", summary: input.summary, sourceRef: input.sourceRef, occurredAt }, "WRITE"));
}
