import test from "node:test";
import assert from "node:assert/strict";
import { artifactLinks, isBlocked, collectorPolicy, referenceTargetDescriptor } from "../src/collector.js";
import { SOURCE_DEFINITIONS } from "../src/collection-run.js";
import { historyScope } from "../src/policy.js";

test("all repository private zones remain blocked", () => {
  for (const segment of collectorPolicy.blockedSegments) {
    assert.equal(isBlocked(segment), true, segment);
    assert.equal(isBlocked(`${segment}/sentinel.md`), true, `${segment}/sentinel.md`);
  }
});

test("formal plan and report paths remain allowed", () => {
  assert.equal(isBlocked("docs/plans/plan.md"), false);
  assert.equal(isBlocked("docs/quality/report.md"), false);
});

test("tracked v1.0 archive is readable history while private archive stays blocked", () => {
  assert.equal(isBlocked("docs/archive/v1.0/quality/report.md"), false);
  assert.equal(historyScope("docs/archive/v1.0/quality/report.md"), "v1.0");
  assert.equal(isBlocked("archive/v1.0/private/report.md"), true);
  assert.equal(historyScope("docs/quality/report.md"), null);
});

test("existing explicit link targets become metadata-only reference artifacts", () => {
  const repoRoot = process.cwd().replace(/[\\/]tools[\\/]knowledge-graph$/, "");
  const directory = referenceTargetDescriptor(repoRoot, "cgc-pms", "docs/archive/v1.0/quality/");
  const rootFile = referenceTargetDescriptor(repoRoot, "cgc-pms", "AGENTS.md");

  assert.equal(directory?.kind, "repository-directory");
  assert.equal(directory?.historical, true);
  assert.equal(directory?.versionScope, "v1.0");
  assert.equal(directory?.referenceOnly, true);
  assert.equal(rootFile?.kind, "repository-reference");
  assert.equal(rootFile?.historical, false);
  assert.equal(referenceTargetDescriptor(repoRoot, "cgc-pms", "missing-target.md"), null);
  assert.equal(referenceTargetDescriptor(repoRoot, "cgc-pms", "archive/v1.0/private/report.md"), null);
});

test("reference metadata has an explicit provenance source", () => {
  assert.equal(SOURCE_DEFINITIONS.some((source) => source.key === "references"), true);
});

test("non-Markdown evidence logs do not create false references", () => {
  const logLine = "Parameters: [1,2](String), [](String), 1(Integer)";
  assert.deepEqual(artifactLinks(logLine, "docs/quality/evidence.txt"), []);
  assert.deepEqual(artifactLinks("`[1,2](String)`", "docs/quality/index.md"), []);
  assert.deepEqual(artifactLinks("```text\n[1,2](String)\n```", "docs/quality/index.md"), []);
  assert.deepEqual(artifactLinks("[report](report.md)", "docs/quality/index.md"), ["docs/quality/report.md"]);
});
