import path from "node:path";
import { fileURLToPath } from "node:url";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

const moduleDir = path.dirname(fileURLToPath(import.meta.url));
const serverPath = path.resolve(moduleDir, "../src/server.js");
const client = new Client({ name: "cgc-pms-knowledge-graph-smoke", version: "0.2.0" });
const transport = new StdioClientTransport({ command: process.execPath, args: [serverPath] });

try {
  await client.connect(transport);
  const listed = await client.listTools();
  const names = listed.tools.map((tool) => tool.name).sort();
  const required = ["kg_collection_runs", "kg_get_artifact", "kg_neighbors", "kg_query", "kg_record_episode", "kg_search", "kg_status", "kg_unresolved_references"];
  if (JSON.stringify(names) !== JSON.stringify(required)) throw new Error(`Unexpected tool list: ${names.join(", ")}`);
  const response = await client.callTool({ name: "kg_status", arguments: {} });
  if (response.isError) throw new Error("kg_status returned an MCP error");
  for (const [name, args] of [["kg_collection_runs", { limit: 5 }], ["kg_unresolved_references", {}]]) {
    const observation = await client.callTool({ name, arguments: args });
    if (observation.isError) throw new Error(`${name} returned an MCP error: ${observation.content?.[0]?.text ?? "unknown"}`);
  }
  console.log(JSON.stringify({ tools: names, status: "ok" }, null, 2));
} finally {
  await client.close();
}
