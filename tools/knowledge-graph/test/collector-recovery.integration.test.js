import test from "node:test";
import assert from "node:assert/strict";
import { loadConfig } from "../src/config.js";
import { createDriver, execute } from "../src/neo4j.js";
import { startCollectionRun, finishCollectionRun } from "../src/collection-run.js";

test("partial collection run preserves an explicit failure state", async () => {
  const config = loadConfig();
  const driver = createDriver(config);
  const runId = await startCollectionRun(driver, config, "test");
  try {
    const status = await finishCollectionRun(driver, config, runId, { processed: 1, added: 1 }, [{ source: "sentinel", message: "parse failed" }]);
    assert.equal(status, "PARTIAL");
    const rows = await execute(driver, config.neo4jDatabase, "MATCH (r:CollectionRun {id: $runId}) RETURN r.status AS status, r.failed AS failed", { runId });
    assert.equal(rows[0].status, "PARTIAL");
    assert.equal(Number(rows[0].failed), 1);
  } finally {
    await execute(driver, config.neo4jDatabase, "MATCH (r:CollectionRun {id: $runId}) DETACH DELETE r", { runId }, "WRITE");
    await driver.close();
  }
});
