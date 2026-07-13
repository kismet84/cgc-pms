import test from "node:test";
import assert from "node:assert/strict";
import { assertReadOnlyCypher, search } from "../src/queries.js";

test("accepts bounded read-only queries", () => {
  assert.doesNotThrow(() => assertReadOnlyCypher("MATCH (n) RETURN n"));
  assert.doesNotThrow(() => assertReadOnlyCypher("OPTIONAL MATCH (n) RETURN count(n)"));
});

test("rejects mutation and procedures", () => {
  assert.throws(() => assertReadOnlyCypher("MATCH (n) DELETE n"));
  assert.throws(() => assertReadOnlyCypher("CALL db.labels()"));
  assert.throws(() => assertReadOnlyCypher("CREATE (n) RETURN n"));
});

test("knowledge search defaults to current scope and accepts explicit history", async () => {
  const calls = [];
  const driver = {
    session() {
      return {
        async run(query, params) { calls.push({ query, params }); return { records: [] }; },
        async close() {},
      };
    },
  };
  const config = { neo4jDatabase: "neo4j" };
  await search(driver, config, "risk", 10);
  await search(driver, config, "risk", 10, "historical");
  assert.equal(calls[0].params.scope, "current");
  assert.equal(calls[1].params.scope, "historical");
  assert.match(calls[0].query, /historical/);
  await assert.rejects(() => search(driver, config, "risk", 10, "invalid"), /Unsupported search scope/);
});
