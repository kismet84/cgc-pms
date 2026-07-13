import path from "node:path";
import { fileURLToPath } from "node:url";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

const moduleDir = path.dirname(fileURLToPath(import.meta.url));
const serverPath = path.resolve(moduleDir, "../src/server.js");
const client = new Client({ name: "cgc-pms-knowledge-graph-smoke", version: "0.1.0" });
const transport = new StdioClientTransport({ command: process.execPath, args: [serverPath] });

try {
  await client.connect(transport);
  const listed = await client.listTools();
  const names = listed.tools.map((tool) => tool.name).sort();
  const required = ["kg_get_artifact", "kg_neighbors", "kg_query", "kg_record_episode", "kg_search", "kg_status"];
  if (JSON.stringify(names) !== JSON.stringify(required)) throw new Error(`Unexpected tool list: ${names.join(", ")}`);
  const response = await client.callTool({ name: "kg_status", arguments: {} });
  if (response.isError) throw new Error("kg_status returned an MCP error");
  console.log(JSON.stringify({ tools: names, status: "ok" }, null, 2));
} finally {
  await client.close();
}
