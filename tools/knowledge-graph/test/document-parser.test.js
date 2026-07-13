import test from "node:test";
import assert from "node:assert/strict";
import { parseMarkdown, markdownLinks } from "../src/document-parser.js";

test("parses headings and repository-relative links", () => {
  const content = "# Plan\nIntro\n## Step\nSee [report](../quality/report.md#result).";
  const parsed = parseMarkdown(content);
  assert.equal(parsed.title, "Plan");
  assert.equal(parsed.headings.length, 2);
  assert.deepEqual(markdownLinks(content, "docs/plans/plan.md"), ["docs/quality/report.md"]);
});

test("does not treat external links as graph references", () => {
  assert.deepEqual(markdownLinks("[site](https://example.com)", "docs/a.md"), []);
});
