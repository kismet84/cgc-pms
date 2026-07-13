import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const moduleDir = path.dirname(fileURLToPath(import.meta.url));

function readEnvFile(file) {
  if (!fs.existsSync(file)) return {};
  return Object.fromEntries(
    fs.readFileSync(file, "utf8")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith("#") && line.includes("="))
      .map((line) => {
        const separator = line.indexOf("=");
        return [line.slice(0, separator), line.slice(separator + 1)];
      }),
  );
}

export function loadConfig() {
  const repoRoot = path.resolve(moduleDir, "../../..");
  const secretsFile = process.env.CGC_KG_SECRETS_FILE
    ?? path.join(os.homedir(), ".cgc-pms-secrets", "neo4j.env");
  const fileEnv = readEnvFile(secretsFile);
  return {
    repoRoot,
    projectKey: process.env.CGC_KG_PROJECT_KEY ?? "cgc-pms",
    neo4jUri: process.env.CGC_KG_NEO4J_URI ?? "bolt://127.0.0.1:7687",
    neo4jUser: process.env.CGC_KG_NEO4J_USER ?? fileEnv.NEO4J_USERNAME ?? "neo4j",
    neo4jPassword: process.env.CGC_KG_NEO4J_PASSWORD ?? fileEnv.NEO4J_PASSWORD,
    neo4jDatabase: process.env.CGC_KG_NEO4J_DATABASE ?? "neo4j",
    secretsFile,
    collectorVersion: "0.4.0",
    runtimeDir: path.join(repoRoot, ".agent-runtime", "knowledge-graph"),
  };
}

export function assertConfig(config) {
  if (!config.neo4jPassword) {
    throw new Error(`Neo4j password is missing. Set CGC_KG_NEO4J_PASSWORD or ${config.secretsFile}.`);
  }
}
