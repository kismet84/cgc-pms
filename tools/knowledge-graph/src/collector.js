import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { execute } from "./neo4j.js";
import { parseMarkdown, markdownLinks, normalizePath } from "./document-parser.js";
import { startCollectionRun, finishCollectionRun, advanceCursor } from "./collection-run.js";
import { collectGit } from "./git-collector.js";
import { ALLOWED_ROOTS, BLOCKED_SEGMENTS, historyScope, isBlocked } from "./policy.js";
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

export function artifactKind(relativePath) {
  if (relativePath.startsWith("docs/archive/v1.0/")) {
    if (relativePath.startsWith("docs/archive/v1.0/plans/")) return "historical-plan";
    if (relativePath.startsWith("docs/archive/v1.0/quality/")) return "historical-report";
    if (relativePath.startsWith("docs/archive/v1.0/backlog-snapshot/")) return "historical-backlog";
    if (relativePath.startsWith("docs/archive/v1.0/iterations/")) return "historical-iteration";
    if (relativePath.startsWith("docs/archive/v1.0/issues/")) return "historical-issue";
    return "historical-document";
  }
  if (relativePath.startsWith("docs/plans/")) return "plan";
  if (relativePath.startsWith("docs/quality/")) return "report";
  if (relativePath.startsWith("docs/backlog/")) return "backlog";
  if (relativePath.startsWith("docs/iterations/")) return "iteration";
  if (relativePath.includes("/artifacts/")) return "plugin-artifact";
  return "document";
}

export function artifactLinks(content, relativePath) {
  return path.extname(relativePath).toLowerCase() === ".md"
    ? markdownLinks(content, relativePath, isBlocked)
    : [];
}

const sha256 = (content) => crypto.createHash("sha256").update(content).digest("hex");

function isWithinRoot(root, candidate) {
  const normalizedRoot = path.resolve(root).toLowerCase();
  const normalizedCandidate = path.resolve(candidate).toLowerCase();
  return normalizedCandidate === normalizedRoot || normalizedCandidate.startsWith(`${normalizedRoot}${path.sep}`);
}

export function referenceTargetDescriptor(repoRoot, projectKey, target) {
  const normalized = normalizePath(path.normalize(target)).replace(/^\.\//, "").replace(/\/$/, "");
  if (!normalized || path.isAbsolute(normalized) || normalized.startsWith("../") || isBlocked(normalized)) return null;
  const absolute = path.resolve(repoRoot, normalized);
  if (!isWithinRoot(repoRoot, absolute) || !fs.existsSync(absolute)) return null;
  try {
    if (!isWithinRoot(fs.realpathSync(repoRoot), fs.realpathSync(absolute))) return null;
    const stat = fs.statSync(absolute);
    const versionScope = historyScope(normalized);
    return {
      id: `${projectKey}:artifact:${normalized}`,
      path: normalized,
      title: path.basename(normalized) || normalized,
      kind: stat.isDirectory() ? "repository-directory" : "repository-reference",
      active: true,
      referenceOnly: true,
      historical: versionScope !== null,
      versionScope,
    };
  } catch {
    return null;
  }
}

async function ensureReferenceTargets(driver, config, runId, links) {
  const targets = links
    .map((target) => referenceTargetDescriptor(config.repoRoot, config.projectKey, target))
    .filter(Boolean);
  if (targets.length === 0) return;
  await execute(driver, config.neo4jDatabase, `
    MATCH (p:Project {key: $projectKey}), (r:CollectionRun {id: $runId}),
          (source:Source {key: 'references'})
    UNWIND $targets AS target
    MERGE (a:Artifact {id: target.id})
    ON CREATE SET a.path = target.path, a.title = target.title, a.kind = target.kind,
                  a.active = true, a.referenceOnly = true,
                  a.historical = target.historical, a.versionScope = target.versionScope,
                  a.createdAt = datetime()
    SET a.active = true, a.lastSeenAt = datetime(),
        a.historical = coalesce(a.historical, target.historical),
        a.versionScope = coalesce(a.versionScope, target.versionScope)
    MERGE (a)-[:IN_PROJECT]->(p)
    MERGE (a)-[:DERIVED_FROM]->(source)
    MERGE (a)-[:SEEN_IN]->(r)
  `, { projectKey: config.projectKey, runId, targets }, "WRITE");
}

async function upsertArtifact(driver, config, runId, absolutePath) {
  const relativePath = normalizePath(path.relative(config.repoRoot, absolutePath));
  const content = fs.readFileSync(absolutePath, "utf8");
  const stat = fs.statSync(absolutePath);
  const parsed = path.extname(absolutePath).toLowerCase() === ".md" ? parseMarkdown(content) : { title: "", headings: [] };
  const id = `${config.projectKey}:artifact:${relativePath}`;
  const versionScope = historyScope(relativePath);
  const historical = versionScope !== null;
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
        a.active = true, a.content = $content, a.historical = $historical,
        a.versionScope = $versionScope
    REMOVE a.removedAt, a.referenceOnly
    MERGE (a)-[:IN_PROJECT]->(p)
    MERGE (a)-[:DERIVED_FROM]->(source)
    MERGE (a)-[:SEEN_IN]->(r)
  `, {
    projectKey: config.projectKey, runId, id, path: relativePath,
    title: parsed.title || path.basename(relativePath), kind: artifactKind(relativePath),
    sha256: digest, size: stat.size, modifiedAt: stat.mtime.toISOString(), content: content.slice(0, 50000),
    historical, versionScope,
  }, "WRITE");
  if (changed) {
    await execute(driver, config.neo4jDatabase, `
    MATCH (a:Artifact {id: $id}), (r:CollectionRun {id: $runId}), (source:Source {key: 'documents'})
    MERGE (v:ArtifactVersion {id: $versionId})
    ON CREATE SET v.sha256 = $sha256, v.content = $content, v.size = $size,
                  v.modifiedAt = datetime($modifiedAt), v.createdAt = datetime(),
                  v.historical = $historical, v.versionScope = $versionScope
    MERGE (v)-[:VERSION_OF]->(a)
    MERGE (v)-[:COLLECTED_IN]->(r)
    MERGE (v)-[:DERIVED_FROM]->(source)
    WITH a, v
    OPTIONAL MATCH (a)-[old:CURRENT_VERSION]->(:ArtifactVersion)
    DELETE old
    MERGE (a)-[:CURRENT_VERSION]->(v)
    `, { id, versionId, runId, sha256: digest, content: content.slice(0, 50000), size: stat.size, modifiedAt: stat.mtime.toISOString(), historical, versionScope }, "WRITE");
    await execute(driver, config.neo4jDatabase, `
    MATCH (a:Artifact {id: $id})
    OPTIONAL MATCH (a)-[sectionRel:CONTAINS]->(:Section)
    DELETE sectionRel
    `, { id }, "WRITE");
    for (const section of parsed.headings) {
      const sectionId = `${versionId}:section:${section.ordinal}`;
      await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact {id: $artifactId}), (v:ArtifactVersion {id: $versionId})
      CREATE (s:Section {id: $sectionId, title: $title, level: $level, ordinal: $ordinal,
                         content: $content, sourcePath: $sourcePath,
                         historical: $historical, versionScope: $versionScope})
      MERGE (a)-[:CONTAINS]->(s)
      MERGE (v)-[:CONTAINS]->(s)
      `, { artifactId: id, versionId, sectionId, sourcePath: relativePath, historical, versionScope, ...section }, "WRITE");
    }
  }
  const links = artifactLinks(content, relativePath);
  await ensureReferenceTargets(driver, config, runId, links);
  await execute(driver, config.neo4jDatabase, `MATCH (:Artifact {id: $id})-[r:REFERENCES]->(:Artifact) DELETE r`, { id }, "WRITE");
  let resolved = 0;
  for (const target of links) {
    const canonicalTarget = referenceTargetDescriptor(config.repoRoot, config.projectKey, target)?.path ?? target;
    const result = await execute(driver, config.neo4jDatabase, `
      MATCH (source:Artifact {id: $id}), (target:Artifact {path: $target})
      MERGE (source)-[r:REFERENCES]->(target)
      SET r.sourceRef = $path, r.extractedAt = datetime(),
          r.extractor = 'deterministic', r.confidence = 1.0,
          r.reviewStatus = 'accepted'
      RETURN count(r) AS count
    `, { id, target: canonicalTarget, path: relativePath }, "WRITE");
    resolved += Number(result[0]?.count ?? 0);
  }
  const unresolved = links.length - resolved;
  return {
    path: relativePath,
    state: changed ? (existed ? "updated" : "added") : "skipped",
    unresolved,
  };
}

export async function collect(driver, config, options = {}) {
  const runId = await startCollectionRun(driver, config, options.trigger ?? "manual");
  const metrics = { processed: 0, added: 0, updated: 0, skipped: 0, removed: 0, unresolvedReferences: 0 };
  const errors = [];
  try {
    const files = ALLOWED_ROOTS.flatMap((root) => walkAllowed(path.join(config.repoRoot, root), config.repoRoot));
    const targets = files.map((file) => {
      const relativePath = normalizePath(path.relative(config.repoRoot, file));
      const versionScope = historyScope(relativePath);
      return {
        id: `${config.projectKey}:artifact:${relativePath}`,
        path: relativePath,
        title: path.basename(relativePath),
        kind: artifactKind(relativePath),
        historical: versionScope !== null,
        versionScope,
      };
    });
    await execute(driver, config.neo4jDatabase, `
      MATCH (p:Project {key: $projectKey}), (r:CollectionRun {id: $runId}), (source:Source {key: 'documents'})
      UNWIND $targets AS target
      MERGE (a:Artifact {id: target.id})
      ON CREATE SET a.path = target.path, a.title = target.title, a.kind = target.kind,
                    a.active = true, a.historical = target.historical,
                    a.versionScope = target.versionScope, a.createdAt = datetime()
      MERGE (a)-[:IN_PROJECT]->(p)
      MERGE (a)-[:DERIVED_FROM]->(source)
      MERGE (a)-[:SEEN_IN]->(r)
    `, { projectKey: config.projectKey, runId, targets }, "WRITE");
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
