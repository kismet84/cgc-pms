import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execute } from "./neo4j.js";
import { ensureSources } from "./collection-run.js";

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
  await ensureSources(driver, config);
  await execute(driver, config.neo4jDatabase, `
    MATCH (a:Artifact), (source:Source {key: 'documents'})
    WHERE a.sha256 IS NOT NULL
    MERGE (v:ArtifactVersion {id: a.id + ':version:' + a.sha256})
    ON CREATE SET v.sha256 = a.sha256, v.content = a.content,
                  v.size = a.size, v.modifiedAt = a.modifiedAt,
                  v.createdAt = coalesce(a.indexedAt, datetime())
    MERGE (v)-[:VERSION_OF]->(a)
    MERGE (v)-[:DERIVED_FROM]->(source)
    WITH a, v
    OPTIONAL MATCH (a)-[old:CURRENT_VERSION]->(:ArtifactVersion)
    DELETE old
    MERGE (a)-[:CURRENT_VERSION]->(v)
  `, {}, "WRITE");
  await execute(driver, config.neo4jDatabase, `
    MATCH (e:Episode), (source:Source {key: 'episodes'})
    MERGE (e)-[:DERIVED_FROM]->(source)
  `, {}, "WRITE");
  await execute(driver, config.neo4jDatabase, `
    MATCH (a:Artifact {kind: 'repository-file'}), (source:Source {key: 'git'})
    MERGE (a)-[:DERIVED_FROM]->(source)
  `, {}, "WRITE");
  return statements.length;
}
