import test from "node:test";
import assert from "node:assert/strict";
import { isBlocked, collectorPolicy } from "../src/collector.js";

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
