import test from "node:test";
import assert from "node:assert/strict";
import { loadConfig } from "../src/config.js";
import { loadIssueRegister, parseIssueRegister } from "../src/issue-registry.js";

test("current issue register is valid, unique, and preserves release blockers", () => {
  const config = loadConfig();
  const { register, sha256 } = loadIssueRegister(config.repoRoot);
  assert.equal(register.schemaVersion, 1);
  assert.match(sha256, /^[a-f0-9]{64}$/);
  assert.ok(register.issues.length > 0);
  assert.equal(new Set(register.issues.map((issue) => issue.issueKey)).size, register.issues.length);

  const blockers = register.issues.filter((issue) => issue.blocking);
  assert.equal(blockers.length, 3);
  assert.ok(blockers.every((issue) => issue.status === "RELEASE_GATE"));
  assert.ok(blockers.every((issue) => issue.classification === "RELEASE_PREREQUISITE"));
  assert.ok(blockers.every((issue) => issue.priority === "P0"));
});

test("issue register rejects duplicate keys and missing parents", () => {
  const base = {
    schemaVersion: 1,
    versionScope: "v1.5",
    updatedAt: "2026-07-13T21:00:00+08:00",
    issues: [{
      issueKey: "A-01",
      title: "root",
      status: "OPEN",
      classification: "STILL_APPLICABLE",
      priority: "P0",
      blocking: false,
      summary: "summary",
      acceptanceCriteria: "acceptance",
      sourceRefs: ["docs/backlog/current-focus.md"],
    }],
  };
  assert.throws(() => parseIssueRegister({ ...base, issues: [...base.issues, ...base.issues] }), /Duplicate issueKey/);
  assert.throws(() => parseIssueRegister({
    ...base,
    issues: [{ ...base.issues[0], issueKey: "child", parentIssueKey: "missing" }],
  }), /Unknown parentIssueKey/);
  assert.throws(() => parseIssueRegister({
    ...base,
    issues: [
      { ...base.issues[0], issueKey: "a", parentIssueKey: "b" },
      { ...base.issues[0], issueKey: "b", parentIssueKey: "a" },
    ],
  }), /parent cycle/);
  assert.throws(() => parseIssueRegister({
    ...base,
    issues: [{ ...base.issues[0], sourceRefs: ["archive/v1.0/private/secret.md"] }],
  }), /Unsafe sourceRef/);
});

test("issue register accepts a strict optional governance candidate", () => {
  const candidate = {
    userValue: "reduce repeated control-plane work",
    minimalLoop: "collect one complete review cycle",
    nonGoals: "do not change hard gates",
    dependencies: "fresh review-cycle evidence",
    risk: "a single sample may be misleading",
    readyPreconditions: "a reproducible root cause and measurable acceptance criteria",
  };
  const base = {
    schemaVersion: 1,
    versionScope: "v1.5",
    updatedAt: "2026-07-17T13:00:00+08:00",
    issues: [{
      issueKey: "AUTO-IMPROVEMENT-TEST",
      title: "governance candidate",
      status: "NEEDS_CONFIRMATION",
      classification: "NEEDS_CONFIRMATION",
      priority: "P1",
      blocking: false,
      summary: "summary",
      acceptanceCriteria: "acceptance",
      candidate,
      sourceRefs: ["docs/backlog/current-focus.md"],
    }],
  };

  assert.deepEqual(parseIssueRegister(base).issues[0].candidate, candidate);
  assert.throws(() => parseIssueRegister({
    ...base,
    issues: [{ ...base.issues[0], candidate: { ...candidate, unexpected: "value" } }],
  }), /unrecognized_keys/);
});
