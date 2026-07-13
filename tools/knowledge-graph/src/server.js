import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { loadConfig } from "./config.js";
import { createDriver } from "./neo4j.js";
import { getArtifact, neighbors, readOnlyQuery, search, status, collectionRuns, unresolvedReferences } from "./queries.js";
import { recordEpisode, episodeInputSchema } from "./episode-collector.js";

const config = loadConfig();
const driver = createDriver(config);
await driver.verifyConnectivity();

const server = new McpServer({ name: "cgc-pms-knowledge-graph", version: "0.3.0" });
const result = (value) => ({ content: [{ type: "text", text: JSON.stringify(value, null, 2) }], structuredContent: { result: value } });

server.registerTool("kg_status", { description: "Return cgc-pms knowledge graph coverage and last indexing time." }, async () => result(await status(driver, config)));
server.registerTool("kg_search", {
  description: "Full-text search project plans, reports, backlog, sections, and recorded episodes with source paths.",
  inputSchema: {
    query: z.string().min(1),
    limit: z.number().int().min(1).max(50).optional(),
    scope: z.enum(["current", "historical", "all"]).optional(),
  },
}, async ({ query, limit, scope }) => result(await search(driver, config, query, limit, scope)));
server.registerTool("kg_get_artifact", {
  description: "Get one indexed artifact and its sections by repository-relative source path.",
  inputSchema: { path: z.string().min(1) },
}, async ({ path }) => result(await getArtifact(driver, config, path)));
server.registerTool("kg_neighbors", {
  description: "Traverse up to three hops around a graph node id.",
  inputSchema: { id: z.string().min(1), depth: z.number().int().min(1).max(3).optional() },
}, async ({ id, depth }) => result(await neighbors(driver, config, id, depth)));
server.registerTool("kg_query", {
  description: "Execute bounded read-only Cypher. Mutations and procedure calls are rejected.",
  inputSchema: { query: z.string().min(1), params: z.record(z.string(), z.unknown()).optional() },
}, async ({ query, params }) => result(await readOnlyQuery(driver, config, query, params)));
server.registerTool("kg_record_episode", {
  description: "Controlled write for a sourced conversation, decision, run, log summary, or observation. Raw logs should remain outside the graph.",
  inputSchema: episodeInputSchema.shape,
}, async (input) => result(await recordEpisode(driver, config, input)));
server.registerTool("kg_collection_runs", {
  description: "Return recent collection runs, metrics, status, and failure summaries.",
  inputSchema: { limit: z.number().int().min(1).max(100).optional() },
}, async ({ limit }) => result(await collectionRuns(driver, config, limit)));
server.registerTool("kg_unresolved_references", {
  description: "Return recent collection runs that contain unresolved document references.",
  inputSchema: { limit: z.number().int().min(1).max(100).optional() },
}, async ({ limit }) => result(await unresolvedReferences(driver, config, limit)));

const shutdown = async () => { await driver.close(); process.exit(0); };
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
await server.connect(new StdioServerTransport());
