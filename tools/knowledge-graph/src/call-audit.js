import fs from "node:fs/promises";
import path from "node:path";

export const MCP_CALL_AUDIT_FILE = "mcp-calls.jsonl";

export async function recordMcpCall(runtimeDir, call) {
  await fs.mkdir(runtimeDir, { recursive: true });
  const record = {
    startedAt: call.startedAt,
    finishedAt: new Date().toISOString(),
    tool: call.tool,
    status: call.status,
    durationMs: call.durationMs,
    pid: process.pid,
  };
  await fs.appendFile(path.join(runtimeDir, MCP_CALL_AUDIT_FILE), `${JSON.stringify(record)}\n`, "utf8");
  return record;
}

export async function listMcpCalls(runtimeDir, limit = 20) {
  const safeLimit = Math.max(1, Math.min(Number(limit) || 20, 200));
  let content;
  try {
    content = await fs.readFile(path.join(runtimeDir, MCP_CALL_AUDIT_FILE), "utf8");
  } catch (error) {
    if (error?.code === "ENOENT") return [];
    throw error;
  }
  return content.trim().split(/\r?\n/).filter(Boolean).slice(-safeLimit).reverse().map(JSON.parse);
}
