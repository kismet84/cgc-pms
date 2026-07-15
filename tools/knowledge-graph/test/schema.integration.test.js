import test from "node:test";
import assert from "node:assert/strict";
import { loadConfig } from "../src/config.js";
import { createDriver, execute } from "../src/neo4j.js";
import { applySchema } from "../src/schema.js";

test("schema is idempotent and current artifacts have one current version", async () => {
  const config = loadConfig();
  const driver = createDriver(config);
  try {
    await applySchema(driver, config);
    await applySchema(driver, config);
    const rows = await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact) WHERE a.sha256 IS NOT NULL
      OPTIONAL MATCH (a)-[r:CURRENT_VERSION]->(:ArtifactVersion)
      RETURN count(a) AS artifacts, count(r) AS currentRelations
    `);
    assert.equal(Number(rows[0].artifacts), Number(rows[0].currentRelations));
  } finally { await driver.close(); }
});
