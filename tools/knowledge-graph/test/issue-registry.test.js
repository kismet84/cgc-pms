import test from "node:test";
import assert from "node:assert/strict";
import { loadConfig } from "../src/config.js";
import { loadIssueRegister, parseIssueRegister } from "../src/issue-registry.js";

test("current issue register is valid, unique, and preserves the A-01 21+11 invariant", () => {
  const config = loadConfig();
  const { register, sha256 } = loadIssueRegister(config.repoRoot);
  assert.equal(register.schemaVersion, 1);
  assert.match(sha256, /^[a-f0-9]{64}$/);
  assert.equal(register.issues.length, 54);

  const a01Children = register.issues.filter((issue) => issue.parentIssueKey === "A-01");
  assert.equal(a01Children.filter((issue) => issue.status === "OPEN").length, 21);
  assert.equal(a01Children.filter((issue) => issue.status === "NEEDS_CONFIRMATION").length, 11);
  assert.equal(register.issues.filter((issue) => issue.blocking).length, 3);
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
