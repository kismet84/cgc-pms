import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { execute } from "./neo4j.js";

const ALLOWED_ROOTS = ["docs", "plugins/cgc-pms-autopilot/artifacts"];
const BLOCKED_SEGMENTS = [
  ".omc", ".omo", ".opencode", ".claude", ".mimocode", "graphify-out",
  ".sisyphus", ".archive", "archive/v1.0/private", "docs/archive/v1.0",
];

function normalize(value) {
  return value.replaceAll("\\", "/");
}

function isBlocked(relativePath) {
  const normalized = normalize(relativePath).toLowerCase();
  return BLOCKED_SEGMENTS.some((segment) => normalized === segment || normalized.startsWith(`${segment}/`) || normalized.includes(`/${segment}/`));
}

function walk(root, base = root) {
  if (!fs.existsSync(root)) return [];
  const result = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const absolute = path.join(root, entry.name);
    const relative = normalize(path.relative(base, absolute));
    if (isBlocked(relative)) continue;
    if (entry.isDirectory()) result.push(...walk(absolute, base));
    else if (entry.isFile() && /\.(md|json|ya?ml|txt)$/i.test(entry.name)) result.push(absolute);
  }
  return result;
}

function artifactKind(relativePath) {
  const value = normalize(relativePath);
  if (value.startsWith("docs/plans/")) return "plan";
  if (value.startsWith("docs/quality/")) return "report";
  if (value.startsWith("docs/backlog/")) return "backlog";
  if (value.startsWith("docs/iterations/")) return "iteration";
  if (value.includes("/artifacts/")) return "plugin-artifact";
  return "document";
}

function parseMarkdown(content) {
  const lines = content.split(/\r?\n/);
  const title = lines.find((line) => /^#\s+/.test(line))?.replace(/^#\s+/, "").trim() ?? "";
  const headings = [];
  for (let index = 0; index < lines.length; index += 1) {
    const match = /^(#{1,6})\s+(.+)$/.exec(lines[index]);
    if (!match) continue;
    const level = match[1].length;
    let end = lines.length;
    for (let cursor = index + 1; cursor < lines.length; cursor += 1) {
      const next = /^(#{1,6})\s+/.exec(lines[cursor]);
      if (next && next[1].length <= level) { end = cursor; break; }
    }
    headings.push({ level, title: match[2].trim(), content: lines.slice(index + 1, end).join("\n").trim().slice(0, 12000), ordinal: headings.length });
  }
  return { title, headings };
}

function sha256(content) {
  return crypto.createHash("sha256").update(content).digest("hex");
}

function markdownLinks(content, sourcePath) {
  const links = new Set();
  for (const match of content.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const raw = match[1].trim().replace(/^<|>$/g, "").split("#", 1)[0];
    if (!raw || /^(https?:|mailto:|data:)/i.test(raw)) continue;
    let decoded;
    try { decoded = decodeURIComponent(raw); } catch { decoded = raw; }
    const target = normalize(path.normalize(path.join(path.dirname(sourcePath), decoded)));
    if (!target.startsWith("../") && !path.isAbsolute(target) && !isBlocked(target)) links.add(target);
  }
  return [...links];
}

async function upsertArtifact(driver, config, absolutePath) {
  const relativePath = normalize(path.relative(config.repoRoot, absolutePath));
  const content = fs.readFileSync(absolutePath, "utf8");
  const stat = fs.statSync(absolutePath);
  const parsed = path.extname(absolutePath).toLowerCase() === ".md" ? parseMarkdown(content) : { title: "", headings: [] };
  const id = `${config.projectKey}:artifact:${relativePath}`;
  const digest = sha256(content);
  const existing = await execute(driver, config.neo4jDatabase, "MATCH (a:Artifact {id: $id}) RETURN a.sha256 AS sha256", { id });
  const changed = existing[0]?.sha256 !== digest;
  await execute(driver, config.neo4jDatabase, `
    MATCH (p:Project {key: $projectKey})
    MERGE (a:Artifact {id: $id})
    SET a.path = $path, a.title = $title, a.kind = $kind, a.sha256 = $sha256,
        a.size = $size, a.modifiedAt = datetime($modifiedAt), a.indexedAt = datetime(),
        a.active = true, a.content = $content
    MERGE (a)-[:IN_PROJECT]->(p)
  `, {
    projectKey: config.projectKey, id, path: relativePath,
    title: parsed.title || path.basename(relativePath), kind: artifactKind(relativePath),
    sha256: digest, size: stat.size, modifiedAt: stat.mtime.toISOString(), content: content.slice(0, 50000),
  }, "WRITE");
  if (!changed) return { path: relativePath, changed: false, links: markdownLinks(content, relativePath) };
  await execute(driver, config.neo4jDatabase, `MATCH (a:Artifact {id: $id})-[r:CONTAINS]->(s:Section) DELETE r DETACH DELETE s`, { id }, "WRITE");
  for (const section of parsed.headings) {
    const sectionId = `${id}:section:${section.ordinal}`;
    await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact {id: $artifactId})
      MERGE (s:Section {id: $sectionId})
      SET s.title = $title, s.level = $level, s.ordinal = $ordinal, s.content = $content
      MERGE (a)-[:CONTAINS]->(s)
    `, { artifactId: id, sectionId, ...section }, "WRITE");
  }
  return { path: relativePath, changed: true, links: markdownLinks(content, relativePath) };
}

async function collectGit(driver, config, limit = 250) {
  const format = "%H%x1f%aI%x1f%an%x1f%ae%x1f%s%x1e";
  const output = execFileSync("git", ["log", `-${limit}`, `--pretty=format:${format}`], { cwd: config.repoRoot, encoding: "utf8" });
  const records = output.split("\x1e").map((item) => item.trim()).filter(Boolean);
  for (const record of records) {
    const [hash, authoredAt, authorName, authorEmail, subject] = record.split("\x1f");
    await execute(driver, config.neo4jDatabase, `
      MATCH (p:Project {key: $projectKey})
      MERGE (c:GitCommit {hash: $hash})
      SET c.authoredAt = datetime($authoredAt), c.authorName = $authorName,
          c.authorEmail = $authorEmail, c.subject = $subject
      MERGE (c)-[:IN_PROJECT]->(p)
    `, { projectKey: config.projectKey, hash, authoredAt, authorName, authorEmail, subject }, "WRITE");
  }
  return records.length;
}

export async function collect(driver, config) {
  const files = ALLOWED_ROOTS.flatMap((root) => walk(path.join(config.repoRoot, root), config.repoRoot));
  const artifacts = [];
  for (const file of files) artifacts.push(await upsertArtifact(driver, config, file));
  const paths = artifacts.map((artifact) => artifact.path);
  await execute(driver, config.neo4jDatabase, `
    MATCH (:Artifact)-[r:REFERENCES]->(:Artifact) DELETE r
  `, {}, "WRITE");
  for (const artifact of artifacts) {
    for (const target of artifact.links) {
      await execute(driver, config.neo4jDatabase, `
        MATCH (source:Artifact {path: $source}), (target:Artifact {path: $target})
        MERGE (source)-[:REFERENCES]->(target)
      `, { source: artifact.path, target }, "WRITE");
    }
  }
  await execute(driver, config.neo4jDatabase, `
    MATCH (a:Artifact)-[:IN_PROJECT]->(:Project {key: $projectKey})
    WHERE NOT a.path IN $paths SET a.active = false, a.removedAt = datetime()
  `, { projectKey: config.projectKey, paths }, "WRITE");
  const commits = await collectGit(driver, config);
  return {
    artifacts: paths.length,
    updatedArtifacts: artifacts.filter((artifact) => artifact.changed).length,
    skippedArtifacts: artifacts.filter((artifact) => !artifact.changed).length,
    references: artifacts.reduce((total, artifact) => total + artifact.links.length, 0),
    commits,
  };
}

export const collectorPolicy = { allowedRoots: ALLOWED_ROOTS, blockedSegments: BLOCKED_SEGMENTS };
