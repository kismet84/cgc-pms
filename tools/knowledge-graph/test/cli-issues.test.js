import test from "node:test";
import assert from "node:assert/strict";
import { parseEpisodeOptions, parseIssueOptions } from "../src/cli.js";

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

test("episode CLI requires a sourced payload with an explicit stable id", () => {
  const input = parseEpisodeOptions(["--input", "episode.json"], () => JSON.stringify({
    id: "cgc-pms:episode:autopilot-retrospective:review-1:v1",
    kind: "run",
    summary: "retrospective",
    sourceRef: "docs/iterations/review.md",
    occurredAt: "2026-07-13T12:00:00+08:00",
  }));
  assert.equal(input.kind, "run");
  assert.throws(() => parseEpisodeOptions([], () => "{}"), /--input/);
  assert.throws(() => parseEpisodeOptions(["--input", "x"], () => "{}"), /stable id/);
  assert.throws(() => parseEpisodeOptions(["--input", "x"], () => "not-json"), /invalid JSON/);
});

test("issues CLI rejects unknown, missing, and invalid arguments before connecting", () => {
  assert.throws(() => parseIssueOptions(["--limit", "0"]), /limit/);
  assert.throws(() => parseIssueOptions(["--blocking", "maybe"]), /blocking/);
  assert.throws(() => parseIssueOptions(["--view", "full"]), /unsupported value/);
  assert.throws(() => parseIssueOptions(["--status", "DONE"]), /unsupported value/);
  assert.throws(() => parseIssueOptions(["--unknown", "x"]), /Unknown issues option/);
  assert.throws(() => parseIssueOptions(["--query"]), /requires a value/);
});
