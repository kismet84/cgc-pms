import { loadConfig } from "./config.js";
import { createDriver, execute, jsonSafe } from "./neo4j.js";

const config = loadConfig();
const driver = createDriver(config);
try {
  const evidence = {
    versions: await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact) WHERE a.sha256 IS NOT NULL
      OPTIONAL MATCH (a)-[r:CURRENT_VERSION]->(:ArtifactVersion)
      RETURN count(a) AS artifacts, count(r) AS currentVersions
    `),
    provenance: await execute(driver, config.neo4jDatabase, `
      MATCH (n) WHERE (n:Decision OR n:Evidence)
        AND (n.sourceRef IS NULL OR NOT (n)-[:DERIVED_FROM]->())
      RETURN count(n) AS missing
    `),
    versionSources: await execute(driver, config.neo4jDatabase, `
      MATCH (v:ArtifactVersion) WHERE NOT (v)-[:DERIVED_FROM]->(:Source)
      RETURN count(v) AS missing
    `),
    runs: await execute(driver, config.neo4jDatabase, `
      MATCH (r:CollectionRun) RETURN r.status AS status, count(*) AS count ORDER BY status
    `),
    sensitive: await execute(driver, config.neo4jDatabase, `
      MATCH (n) WHERE any(k IN keys(n)
        WHERE toString(n[k]) CONTAINS 'hunter2' OR toString(n[k]) CONTAINS 'secret-value')
      RETURN count(n) AS matches
    `),
  };
  const safe = jsonSafe(evidence);
  console.log(JSON.stringify(safe, null, 2));
  const versions = safe.versions[0];
  if (versions.artifacts !== versions.currentVersions) throw new Error("Current version coverage is incomplete.");
  if (safe.provenance[0].missing !== 0) throw new Error("Decision or evidence provenance is incomplete.");
  if (safe.versionSources[0].missing !== 0) throw new Error("Artifact version source coverage is incomplete.");
  if (safe.sensitive[0].matches !== 0) throw new Error("Sensitive test values remain in the graph.");
} finally {
  await driver.close();
}
