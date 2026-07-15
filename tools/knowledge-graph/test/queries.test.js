import test from "node:test";
import assert from "node:assert/strict";
import { assertReadOnlyCypher, listIssues, search } from "../src/queries.js";

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

test("structured issue query defaults to a bounded current summary", async () => {
  const calls = [];
  const driver = {
    session() {
      return {
        async run(query, params) {
          calls.push({ query, params });
          let values = [];
          if (query.includes("RETURN count(i) AS count")) values = [{ count: 57 }];
          else if (query.includes("i.status AS key")) values = [{ key: "OPEN", count: 32 }];
          else if (query.includes("i.classification AS key")) values = [{ key: "STILL_APPLICABLE", count: 33 }];
          else if (query.includes("i.priority AS key")) values = [{ key: "P0", count: 20 }];
          else if (query.includes("parentIssueKey")) values = [{ key: "A-01", count: 35 }];
          return { records: values.map((value) => ({ toObject: () => value })) };
        },
        async close() {},
      };
    },
  };
  const config = { neo4jDatabase: "neo4j", projectKey: "cgc-pms" };
  const summary = await listIssues(driver, config);
  assert.equal(summary.total, 57);
  assert.equal(summary.byStatus.OPEN, 32);
  assert.ok(calls.every((call) => call.params.currentOnly === true));
  assert.ok(calls.every((call) => !call.query.includes("db.index.fulltext")));
});
