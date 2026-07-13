import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { execute } from "./neo4j.js";
import { parseMarkdown, markdownLinks, normalizePath } from "./document-parser.js";
import { startCollectionRun, finishCollectionRun, advanceCursor } from "./collection-run.js";
import { collectGit } from "./git-collector.js";
import { ALLOWED_ROOTS, BLOCKED_SEGMENTS, isBlocked } from "./policy.js";
export { isBlocked } from "./policy.js";

export function walkAllowed(root, base = root) {
  if (!fs.existsSync(root)) return [];
  const result = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const absolute = path.join(root, entry.name);
    const relative = normalizePath(path.relative(base, absolute));
    if (isBlocked(relative)) continue;
    if (entry.isDirectory()) result.push(...walkAllowed(absolute, base));
    else if (entry.isFile() && /\.(md|json|ya?ml|txt)$/i.test(entry.name)) result.push(absolute);
  }
  return result;
}

function artifactKind(relativePath) {
  if (relativePath.startsWith("docs/plans/")) return "plan";
  if (relativePath.startsWith("docs/quality/")) return "report";
  if (relativePath.startsWith("docs/backlog/")) return "backlog";
  if (relativePath.startsWith("docs/iterations/")) return "iteration";
  if (relativePath.includes("/artifacts/")) return "plugin-artifact";
  return "document";
}

const sha256 = (content) => crypto.createHash("sha256").update(content).digest("hex");

async function upsertArtifact(driver, config, runId, absolutePath) {
  const relativePath = normalizePath(path.relative(config.repoRoot, absolutePath));
  const content = fs.readFileSync(absolutePath, "utf8");
  const stat = fs.statSync(absolutePath);
  const parsed = path.extname(absolutePath).toLowerCase() === ".md" ? parseMarkdown(content) : { title: "", headings: [] };
  const id = `${config.projectKey}:artifact:${relativePath}`;
  const digest = sha256(content);
  const versionId = `${id}:version:${digest}`;
  const existing = await execute(driver, config.neo4jDatabase, "MATCH (a:Artifact {id: $id}) RETURN a.sha256 AS sha256", { id });
  const existed = existing.length > 0;
  const changed = existing[0]?.sha256 !== digest;
  await execute(driver, config.neo4jDatabase, `
    MATCH (p:Project {key: $projectKey}), (r:CollectionRun {id: $runId}), (source:Source {key: 'documents'})
    MERGE (a:Artifact {id: $id})
    SET a.path = $path, a.title = $title, a.kind = $kind, a.sha256 = $sha256,
        a.size = $size, a.modifiedAt = datetime($modifiedAt), a.lastSeenAt = datetime(),
        a.active = true, a.content = $content
    REMOVE a.removedAt
    MERGE (a)-[:IN_PROJECT]->(p)
    MERGE (a)-[:DERIVED_FROM]->(source)
    MERGE (a)-[:SEEN_IN]->(r)
  `, {
    projectKey: config.projectKey, runId, id, path: relativePath,
    title: parsed.title || path.basename(relativePath), kind: artifactKind(relativePath),
    sha256: digest, size: stat.size, modifiedAt: stat.mtime.toISOString(), content: content.slice(0, 50000),
  }, "WRITE");
  if (!changed) return { path: relativePath, state: "skipped", unresolved: 0 };
  await execute(driver, config.neo4jDatabase, `
    MATCH (a:Artifact {id: $id}), (r:CollectionRun {id: $runId}), (source:Source {key: 'documents'})
    MERGE (v:ArtifactVersion {id: $versionId})
    ON CREATE SET v.sha256 = $sha256, v.content = $content, v.size = $size,
                  v.modifiedAt = datetime($modifiedAt), v.createdAt = datetime()
    MERGE (v)-[:VERSION_OF]->(a)
    MERGE (v)-[:COLLECTED_IN]->(r)
    MERGE (v)-[:DERIVED_FROM]->(source)
    WITH a, v
    OPTIONAL MATCH (a)-[old:CURRENT_VERSION]->(:ArtifactVersion)
    DELETE old
    MERGE (a)-[:CURRENT_VERSION]->(v)
  `, { id, versionId, runId, sha256: digest, content: content.slice(0, 50000), size: stat.size, modifiedAt: stat.mtime.toISOString() }, "WRITE");
  await execute(driver, config.neo4jDatabase, `
    MATCH (a:Artifact {id: $id})
    OPTIONAL MATCH (a)-[sectionRel:CONTAINS]->(:Section)
    DELETE sectionRel
  `, { id }, "WRITE");
  for (const section of parsed.headings) {
    const sectionId = `${versionId}:section:${section.ordinal}`;
    await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact {id: $artifactId}), (v:ArtifactVersion {id: $versionId})
      CREATE (s:Section {id: $sectionId, title: $title, level: $level, ordinal: $ordinal, content: $content})
      MERGE (a)-[:CONTAINS]->(s)
      MERGE (v)-[:CONTAINS]->(s)
    `, { artifactId: id, versionId, sectionId, ...section }, "WRITE");
  }
  await execute(driver, config.neo4jDatabase, `MATCH (:Artifact {id: $id})-[r:REFERENCES]->(:Artifact) DELETE r`, { id }, "WRITE");
  const links = markdownLinks(content, relativePath, isBlocked);
  let resolved = 0;
  for (const target of links) {
    const result = await execute(driver, config.neo4jDatabase, `
      MATCH (source:Artifact {id: $id}), (target:Artifact {path: $target})
      MERGE (source)-[r:REFERENCES]->(target)
      SET r.sourceRef = $path, r.extractedAt = datetime(),
          r.extractor = 'deterministic', r.confidence = 1.0,
          r.reviewStatus = 'accepted'
      RETURN count(r) AS count
    `, { id, target, path: relativePath }, "WRITE");
    resolved += Number(result[0]?.count ?? 0);
  }
  return { path: relativePath, state: existed ? "updated" : "added", unresolved: links.length - resolved };
}

export async function collect(driver, config, options = {}) {
  const runId = await startCollectionRun(driver, config, options.trigger ?? "manual");
  const metrics = { processed: 0, added: 0, updated: 0, skipped: 0, removed: 0, unresolvedReferences: 0 };
  const errors = [];
  try {
    const files = ALLOWED_ROOTS.flatMap((root) => walkAllowed(path.join(config.repoRoot, root), config.repoRoot));
    const paths = [];
    for (const file of files) {
      try {
        const result = await upsertArtifact(driver, config, runId, file);
        paths.push(result.path); metrics.processed += 1; metrics[result.state] += 1;
        metrics.unresolvedReferences += result.unresolved;
      } catch (error) {
        errors.push({ source: normalizePath(path.relative(config.repoRoot, file)), message: error.message });
      }
    }
    const removed = await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact)-[:DERIVED_FROM]->(:Source {key: 'documents'})
      WHERE a.active = true AND NOT a.path IN $paths
      SET a.active = false, a.removedAt = datetime()
      RETURN count(a) AS count
    `, { paths }, "WRITE");
    metrics.removed = Number(removed[0]?.count ?? 0);
    if (errors.length === 0) {
      await advanceCursor(driver, config, "documents", crypto.createHash("sha256").update(paths.sort().join("\n")).digest("hex"), runId);
    }
    let gitResult = { commits: 0, files: 0 };
    try { gitResult = await collectGit(driver, config, runId); }
    catch (error) { errors.push({ source: "git", message: error.message }); }
    const status = await finishCollectionRun(driver, config, runId, metrics, errors);
    return { runId, status, ...metrics, gitCommits: gitResult.commits, gitFileLinks: gitResult.files, errors };
  } catch (error) {
    errors.push({ source: "collector", message: error.message });
    await finishCollectionRun(driver, config, runId, metrics, errors);
    throw error;
  }
}

export const collectorPolicy = { allowedRoots: ALLOWED_ROOTS, blockedSegments: BLOCKED_SEGMENTS };
