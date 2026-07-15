import test from "node:test";
import assert from "node:assert/strict";
import { parseGitLog } from "../src/git-collector.js";

test("parses commit metadata and unique changed files", () => {
  const output = "\x1eabc\x1f2026-07-13T09:00:00+08:00\x1fA\x1fa@example.com\x1fsubject\ndocs/a.md\ndocs/a.md\nsrc/b.js";
  assert.deepEqual(parseGitLog(output), [{
    hash: "abc", authoredAt: "2026-07-13T09:00:00+08:00", authorName: "A",
    authorEmail: "a@example.com", subject: "subject", files: ["docs/a.md", "src/b.js"],
  }]);
});
