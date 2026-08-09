import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { listMcpCalls, recordMcpCall } from "../src/call-audit.js";

test("MCP call audit keeps bounded metadata without inputs", async (context) => {
  const runtimeDir = await fs.mkdtemp(path.join(os.tmpdir(), "cgc-pms-kg-audit-"));
  context.after(() => fs.rm(runtimeDir, { recursive: true, force: true }));

  assert.deepEqual(await listMcpCalls(runtimeDir), []);
  await recordMcpCall(runtimeDir, { startedAt: "2026-08-09T00:00:00.000Z", tool: "kg_status", status: "SUCCEEDED", durationMs: 4 });
  await recordMcpCall(runtimeDir, { startedAt: "2026-08-09T00:00:01.000Z", tool: "kg_search", status: "FAILED", durationMs: 5 });

  const calls = await listMcpCalls(runtimeDir, 1);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].tool, "kg_search");
  assert.equal(calls[0].status, "FAILED");
  assert.equal(calls[0].durationMs, 5);
  assert.equal("input" in calls[0], false);
});
