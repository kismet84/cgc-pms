import { loadConfig } from "./config.js";
import { createDriver, execute, jsonSafe } from "./neo4j.js";
import { search } from "./queries.js";

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
    latestRun: await execute(driver, config.neo4jDatabase, `
      MATCH (r:CollectionRun)-[:IN_PROJECT]->(:Project {key: $projectKey})
      RETURN r.status AS status, r.unresolvedReferences AS unresolvedReferences
      ORDER BY r.startedAt DESC LIMIT 1
    `, { projectKey: config.projectKey }),
    sensitive: await execute(driver, config.neo4jDatabase, `
      MATCH (n) WHERE any(k IN keys(n)
        WHERE toString(n[k]) CONTAINS 'hunter2' OR toString(n[k]) CONTAINS 'secret-value')
      RETURN count(n) AS matches
    `),
    historyMarkdown: await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact)
      WHERE a.historical = true AND a.versionScope = 'v1.0' AND a.path ENDS WITH '.md'
      OPTIONAL MATCH (a)-[r:CURRENT_VERSION]->(:ArtifactVersion)
      RETURN count(a) AS artifacts,
             count(CASE WHEN a.content IS NOT NULL AND size(a.content) > 0 THEN 1 END) AS withContent,
             count(r) AS currentVersions
    `),
    privateArchive: await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact)
      WHERE a.path STARTS WITH 'archive/v1.0/private/'
      RETURN count(a) AS artifacts
    `),
    referenceOnly: await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact {referenceOnly: true})
      RETURN count(a) AS artifacts,
             count(CASE WHEN a.content IS NOT NULL THEN 1 END) AS withContent,
             count(CASE WHEN NOT (a)-[:DERIVED_FROM]->(:Source {key: 'references'}) THEN 1 END) AS missingSource
    `),
  };
  const currentSearch = await search(driver, config, "剩余风险", 20, "current");
  const historicalSearch = await search(driver, config, "剩余风险", 20, "historical");
  evidence.searchScopes = [{
    currentResults: currentSearch.length,
    currentHistoricalMatches: currentSearch.filter((item) => item.historical).length,
    historicalResults: historicalSearch.length,
    historicalCurrentMatches: historicalSearch.filter((item) => !item.historical).length,
  }];
  const safe = jsonSafe(evidence);
  console.log(JSON.stringify(safe, null, 2));
  const versions = safe.versions[0];
  if (versions.artifacts !== versions.currentVersions) throw new Error("Current version coverage is incomplete.");
  if (safe.provenance[0].missing !== 0) throw new Error("Decision or evidence provenance is incomplete.");
  if (safe.versionSources[0].missing !== 0) throw new Error("Artifact version source coverage is incomplete.");
  if (safe.latestRun[0]?.status !== "SUCCEEDED" || safe.latestRun[0]?.unresolvedReferences !== 0) {
    throw new Error("Latest collection run still has unresolved references.");
  }
  if (safe.sensitive[0].matches !== 0) throw new Error("Sensitive test values remain in the graph.");
  const history = safe.historyMarkdown[0];
  if (history.artifacts !== 254 || history.withContent !== 254 || history.currentVersions !== 254) {
    throw new Error("Tracked v1.0 Markdown coverage is incomplete.");
  }
  if (safe.privateArchive[0].artifacts !== 0) throw new Error("Private v1.0 archive content entered the graph.");
  if (safe.referenceOnly[0].withContent !== 0 || safe.referenceOnly[0].missingSource !== 0) {
    throw new Error("Reference-only artifacts contain content or lack provenance.");
  }
  if (safe.searchScopes[0].currentHistoricalMatches !== 0 || safe.searchScopes[0].historicalCurrentMatches !== 0) {
    throw new Error("Current and historical search scopes are not isolated.");
  }
} finally {
  await driver.close();
}
