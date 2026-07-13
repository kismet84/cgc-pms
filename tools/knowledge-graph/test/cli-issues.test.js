import test from "node:test";
import assert from "node:assert/strict";
import { parseIssueOptions } from "../src/cli.js";

test("issues CLI parses bounded list filters without changing query semantics", () => {
  assert.deepEqual(parseIssueOptions([
    "--view", "list", "--limit", "20", "--status", "OPEN",
    "--classification", "STILL_APPLICABLE", "--priority", "P0",
    "--parent-issue-key", "A-01", "--blocking", "false",
    "--current-only", "--query", "tenant",
  ]), {
    view: "list", limit: 20, status: "OPEN", classification: "STILL_APPLICABLE",
    priority: "P0", parentIssueKey: "A-01", blocking: false,
    currentOnly: true, query: "tenant",
  });
});

test("issues CLI rejects unknown, missing, and invalid arguments before connecting", () => {
  assert.throws(() => parseIssueOptions(["--limit", "0"]), /limit/);
  assert.throws(() => parseIssueOptions(["--blocking", "maybe"]), /blocking/);
  assert.throws(() => parseIssueOptions(["--view", "full"]), /unsupported value/);
  assert.throws(() => parseIssueOptions(["--status", "DONE"]), /unsupported value/);
  assert.throws(() => parseIssueOptions(["--unknown", "x"]), /Unknown issues option/);
  assert.throws(() => parseIssueOptions(["--query"]), /requires a value/);
});
