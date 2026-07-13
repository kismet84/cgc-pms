import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execute } from "./neo4j.js";

const moduleDir = path.dirname(fileURLToPath(import.meta.url));

export async function applySchema(driver, config) {
  const source = fs.readFileSync(path.resolve(moduleDir, "../schema.cypher"), "utf8");
  const statements = source.split(";").map((value) => value.trim()).filter(Boolean);
  for (const statement of statements) {
    await execute(driver, config.neo4jDatabase, statement, {}, "WRITE");
  }
  await execute(driver, config.neo4jDatabase, `
    MERGE (p:Project {key: $key})
    ON CREATE SET p.createdAt = datetime()
    SET p.name = 'cgc-pms', p.rootPath = $rootPath, p.updatedAt = datetime()
  `, { key: config.projectKey, rootPath: config.repoRoot }, "WRITE");
  return statements.length;
}
