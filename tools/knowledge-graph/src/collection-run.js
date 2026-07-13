import crypto from "node:crypto";
import { execute, jsonSafe } from "./neo4j.js";

export const SOURCE_DEFINITIONS = [
  { key: "documents", kind: "repository", description: "Allowlisted project documents" },
  { key: "git", kind: "git", description: "Local Git commit history" },
  { key: "episodes", kind: "mcp", description: "Controlled Codex and log summaries" },
];

export async function ensureSources(driver, config) {
  for (const source of SOURCE_DEFINITIONS) {
    await execute(driver, config.neo4jDatabase, `
      MATCH (p:Project {key: $projectKey})
      MERGE (s:Source {key: $key})
      SET s.kind = $kind, s.description = $description, s.updatedAt = datetime()
      MERGE (s)-[:IN_PROJECT]->(p)
      MERGE (c:SourceCursor {sourceKey: $key})
      ON CREATE SET c.createdAt = datetime(), c.cursor = null
      MERGE (c)-[:CURSOR_FOR]->(s)
    `, { projectKey: config.projectKey, ...source }, "WRITE");
  }
}

export async function startCollectionRun(driver, config, trigger = "manual") {
  await ensureSources(driver, config);
  const id = `${config.projectKey}:run:${crypto.randomUUID()}`;
  await execute(driver, config.neo4jDatabase, `
    MATCH (p:Project {key: $projectKey})
    CREATE (r:CollectionRun {
      id: $id, status: 'RUNNING', trigger: $trigger,
      collectorVersion: $collectorVersion, startedAt: datetime(),
      added: 0, updated: 0, skipped: 0, removed: 0, failed: 0,
      unresolvedReferences: 0
    })
    MERGE (r)-[:IN_PROJECT]->(p)
  `, { projectKey: config.projectKey, id, trigger, collectorVersion: config.collectorVersion }, "WRITE");
  return id;
}

export async function finishCollectionRun(driver, config, runId, metrics, errors = []) {
  const status = errors.length === 0 ? "SUCCEEDED" : metrics.processed > 0 ? "PARTIAL" : "FAILED";
  await execute(driver, config.neo4jDatabase, `
    MATCH (r:CollectionRun {id: $runId})
    SET r.status = $status, r.finishedAt = datetime(), r.added = $added,
        r.updated = $updated, r.skipped = $skipped, r.removed = $removed,
        r.failed = $failed, r.unresolvedReferences = $unresolvedReferences,
        r.errorSummary = $errorSummary
  `, {
    runId, status,
    added: metrics.added ?? 0, updated: metrics.updated ?? 0,
    skipped: metrics.skipped ?? 0, removed: metrics.removed ?? 0,
    failed: errors.length, unresolvedReferences: metrics.unresolvedReferences ?? 0,
    errorSummary: errors.slice(0, 20).map((error) => `${error.source}: ${error.message}`).join("\n"),
  }, "WRITE");
  return status;
}

export async function getCursor(driver, config, sourceKey) {
  const rows = await execute(driver, config.neo4jDatabase, `
    MATCH (c:SourceCursor {sourceKey: $sourceKey}) RETURN c.cursor AS cursor
  `, { sourceKey });
  return rows[0]?.cursor ?? null;
}

export async function advanceCursor(driver, config, sourceKey, cursor, runId) {
  await execute(driver, config.neo4jDatabase, `
    MATCH (c:SourceCursor {sourceKey: $sourceKey}), (r:CollectionRun {id: $runId})
    SET c.cursor = $cursor, c.lastSuccessAt = datetime(), c.updatedAt = datetime()
    MERGE (r)-[:USED_CURSOR]->(c)
  `, { sourceKey, cursor, runId }, "WRITE");
}

export async function recentCollectionRuns(driver, config, limit = 20) {
  const safeLimit = Math.max(1, Math.min(Number(limit) || 20, 100));
  return jsonSafe(await execute(driver, config.neo4jDatabase, `
    MATCH (r:CollectionRun)-[:IN_PROJECT]->(:Project {key: $projectKey})
    RETURN r { .id, .status, .trigger, .collectorVersion, .startedAt, .finishedAt,
               .added, .updated, .skipped, .removed, .failed, .unresolvedReferences,
               .errorSummary } AS run
    ORDER BY r.startedAt DESC LIMIT toInteger($limit)
  `, { projectKey: config.projectKey, limit: safeLimit }));
}
