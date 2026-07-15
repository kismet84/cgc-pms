import crypto from "node:crypto";
import { z } from "zod";
import { execute, jsonSafe } from "./neo4j.js";
import { ensureSources } from "./collection-run.js";
import { redactDeep, redactText } from "./redaction.js";

export const episodeInputSchema = z.object({
  id: z.string().max(500).optional(),
  kind: z.enum(["conversation", "decision", "run", "log-summary", "observation"]),
  title: z.string().max(500).optional(),
  summary: z.string().min(1).max(12000),
  sourceRef: z.string().min(1).max(1000),
  occurredAt: z.string().datetime({ offset: true }).optional(),
  sha256: z.string().regex(/^[a-fA-F0-9]{64}$/).optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
}).strict();

export async function recordEpisode(driver, config, rawInput) {
  const input = episodeInputSchema.parse(rawInput);
  await ensureSources(driver, config);
  const occurredAt = input.occurredAt ?? new Date().toISOString();
  const id = input.id ?? `${config.projectKey}:episode:${crypto.createHash("sha256").update(`${input.kind}|${input.sourceRef}|${occurredAt}`).digest("hex")}`;
  const title = redactText(input.title ?? "");
  const summary = redactText(input.summary);
  const sourceRef = redactText(input.sourceRef);
  const metadata = redactDeep(input.metadata ?? {});
  const rows = await execute(driver, config.neo4jDatabase, `
    MATCH (p:Project {key: $projectKey}), (source:Source {key: 'episodes'})
    MERGE (e:Episode {id: $id})
    SET e.kind = $kind, e.title = $title, e.summary = $summary,
        e.sourceRef = $sourceRef, e.occurredAt = datetime($occurredAt),
        e.sha256 = $sha256, e.metadataJson = $metadataJson, e.updatedAt = datetime()
    MERGE (e)-[:IN_PROJECT]->(p)
    MERGE (e)-[:DERIVED_FROM]->(source)
    RETURN e { .id, .kind, .title, .sourceRef, .occurredAt, .sha256 } AS episode
  `, {
    projectKey: config.projectKey, id, kind: input.kind, title, summary, sourceRef,
    occurredAt, sha256: input.sha256 ?? null, metadataJson: JSON.stringify(metadata),
  }, "WRITE");
  await execute(driver, config.neo4jDatabase, `
    MATCH (c:SourceCursor {sourceKey: 'episodes'})
    SET c.cursor = $id, c.lastSuccessAt = datetime(), c.updatedAt = datetime()
  `, { id }, "WRITE");
  if (input.kind === "log-summary" || input.kind === "run") {
    await execute(driver, config.neo4jDatabase, `
      MATCH (e:Episode {id: $id})
      MERGE (v:Evidence {id: $evidenceId})
      SET v.title = $title, v.summary = $summary, v.sourceRef = $sourceRef,
          v.extractedAt = datetime(), v.extractor = 'controlled-input',
          v.confidence = 1.0, v.reviewStatus = 'accepted'
      MERGE (v)-[:DERIVED_FROM]->(e)
    `, { id, evidenceId: `${id}:evidence`, title, summary, sourceRef }, "WRITE");
  }
  if (input.kind === "decision") {
    await execute(driver, config.neo4jDatabase, `
      MATCH (e:Episode {id: $id})
      MERGE (d:Decision {id: $decisionId})
      SET d.title = $title, d.summary = $summary, d.sourceRef = $sourceRef,
          d.extractedAt = datetime(), d.extractor = 'controlled-input',
          d.confidence = 1.0, d.reviewStatus = 'accepted'
      MERGE (d)-[:DERIVED_FROM]->(e)
    `, { id, decisionId: `${id}:decision`, title, summary, sourceRef }, "WRITE");
  }
  return jsonSafe(rows);
}
