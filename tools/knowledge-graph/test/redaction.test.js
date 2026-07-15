import test from "node:test";
import assert from "node:assert/strict";
import { redactDeep, redactText } from "../src/redaction.js";

test("redacts inline credentials", () => {
  const value = redactText("Authorization: Bearer abc.def.ghi password=hunter2 neo4j://u:p@localhost");
  assert.doesNotMatch(value, /abc\.def|hunter2|:p@/);
  assert.match(value, /REDACTED/);
});

test("redacts sensitive metadata keys recursively", () => {
  assert.deepEqual(redactDeep({ token: "abc", nested: { api_key: "def", exitCode: 1 } }), {
    token: "[REDACTED]", nested: { api_key: "[REDACTED]", exitCode: 1 },
  });
});
