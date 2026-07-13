import test from "node:test";
import assert from "node:assert/strict";
import { loadConfig } from "../src/config.js";
import { createDriver, execute } from "../src/neo4j.js";
import { recordEpisode } from "../src/episode-collector.js";

test("episode writes are idempotent, sourced, and redacted", async () => {
  const config = loadConfig();
  const driver = createDriver(config);
  const id = `${config.projectKey}:episode:test-redaction`;
  let previousCursor = null;
  try {
    const cursorRows = await execute(driver, config.neo4jDatabase, "MATCH (c:SourceCursor {sourceKey: 'episodes'}) RETURN c.cursor AS cursor");
    previousCursor = cursorRows[0]?.cursor ?? null;
    const input = {
      id, kind: "log-summary", title: "test", sourceRef: "test:episode",
      summary: "password=hunter2", occurredAt: "2026-07-13T09:00:00+08:00",
      metadata: { token: "secret-value", exitCode: 1, classification: "real_quality" },
    };
    await recordEpisode(driver, config, input);
    await recordEpisode(driver, config, input);
    const rows = await execute(driver, config.neo4jDatabase, `
      MATCH (e:Episode {id: $id})-[:DERIVED_FROM]->(:Source {key: 'episodes'})
      OPTIONAL MATCH (v:Evidence)-[:DERIVED_FROM]->(e)
      RETURN count(e) AS episodes, count(v) AS evidence, e.summary AS summary, e.metadataJson AS metadataJson
    `, { id });
    assert.equal(Number(rows[0].episodes), 1);
    assert.equal(Number(rows[0].evidence), 1);
    assert.doesNotMatch(rows[0].summary, /hunter2/);
    assert.doesNotMatch(rows[0].metadataJson, /secret-value/);
  } finally {
    await execute(driver, config.neo4jDatabase, `
      MATCH (e:Episode {id: $id}) OPTIONAL MATCH (v:Evidence)-[:DERIVED_FROM]->(e)
      DETACH DELETE v, e
    `, { id }, "WRITE");
    await execute(driver, config.neo4jDatabase, `
      MATCH (c:SourceCursor {sourceKey: 'episodes'}) SET c.cursor = $cursor, c.updatedAt = datetime()
    `, { cursor: previousCursor }, "WRITE");
    await driver.close();
  }
});
