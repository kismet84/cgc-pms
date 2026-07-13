import test from "node:test";
import assert from "node:assert/strict";
import { assertReadOnlyCypher } from "../src/queries.js";

test("accepts bounded read-only queries", () => {
  assert.doesNotThrow(() => assertReadOnlyCypher("MATCH (n) RETURN n"));
  assert.doesNotThrow(() => assertReadOnlyCypher("OPTIONAL MATCH (n) RETURN count(n)"));
});

test("rejects mutation and procedures", () => {
  assert.throws(() => assertReadOnlyCypher("MATCH (n) DELETE n"));
  assert.throws(() => assertReadOnlyCypher("CALL db.labels()"));
  assert.throws(() => assertReadOnlyCypher("CREATE (n) RETURN n"));
});
