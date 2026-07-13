import { loadConfig } from "./config.js";
import { createDriver, execute, jsonSafe } from "./neo4j.js";
import { listIssues, search } from "./queries.js";
import { loadIssueRegister } from "./issue-registry.js";

const config = loadConfig();
const driver = createDriver(config);
try {
  const issueRegister = loadIssueRegister(config.repoRoot).register;
  const issueQueryStartedAt = performance.now();
  const structuredIssueSummary = await listIssues(driver, config, { view: "summary" });
  const issueSummaryElapsedMs = performance.now() - issueQueryStartedAt;
  const issueListStartedAt = performance.now();
  const structuredIssueList = await listIssues(driver, config, { view: "list", limit: 200 });
  const issueListElapsedMs = performance.now() - issueListStartedAt;
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
      MATCH (n) WHERE any(k IN keys(n) WHERE any(value IN
        CASE WHEN valueType(n[k]) STARTS WITH 'LIST' THEN n[k] ELSE [n[k]] END
        WHERE toString(value) CONTAINS 'hunter2' OR toString(value) CONTAINS 'secret-value'))
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
    issues: await execute(driver, config.neo4jDatabase, `
      MATCH (i:Issue)-[:IN_PROJECT]->(:Project {key: $projectKey})
      WHERE i.current = true
      OPTIONAL MATCH (i)-[defined:DEFINED_IN]->(:Artifact {path: 'docs/backlog/current-issues.json'})
      OPTIONAL MATCH (i)-[supported:SUPPORTED_BY]->(:Artifact)
      WITH i, count(DISTINCT defined) AS registerRelations,
              count(DISTINCT supported) AS supportedRelations
      RETURN count(i) AS issues,
             sum(CASE WHEN registerRelations = 0 THEN 1 ELSE 0 END) AS missingRegister,
             sum(CASE WHEN supportedRelations = 0 THEN 1 ELSE 0 END) AS missingEvidence,
             sum(CASE WHEN i.blocking = true THEN 1 ELSE 0 END) AS blocking,
             sum(size(i.sourceRefs)) AS expectedEvidenceRelations,
             sum(supportedRelations) AS evidenceRelations
    `, { projectKey: config.projectKey }),
    issueParents: await execute(driver, config.neo4jDatabase, `
      MATCH (i:Issue)-[:IN_PROJECT]->(:Project {key: $projectKey})
      WHERE i.current = true AND i.parentIssueKey IS NOT NULL
      OPTIONAL MATCH (i)-[parent:PART_OF]->(:Issue {issueKey: i.parentIssueKey})
      RETURN count(i) AS children, count(parent) AS parentRelations
    `, { projectKey: config.projectKey }),
    issueQuery: [{
      total: structuredIssueSummary.total,
      returned: structuredIssueList.returned,
      summaryElapsedMs: issueSummaryElapsedMs,
      listElapsedMs: issueListElapsedMs,
    }],
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
  const issues = safe.issues[0];
  if (issues.issues !== issueRegister.issues.length
      || issues.missingRegister !== 0
      || issues.missingEvidence !== 0
      || issues.expectedEvidenceRelations !== issues.evidenceRelations) {
    throw new Error("Current issue registry coverage or provenance is incomplete.");
  }
  if (issues.blocking !== issueRegister.issues.filter((issue) => issue.blocking).length) {
    throw new Error("Current issue blocking count differs from the canonical register.");
  }
  if (safe.issueParents[0].children !== safe.issueParents[0].parentRelations) {
    throw new Error("Current issue parent relationships are incomplete.");
  }
  if (safe.issueQuery[0].total !== issueRegister.issues.length
      || safe.issueQuery[0].returned !== issueRegister.issues.length
      || safe.issueQuery[0].summaryElapsedMs > 2000
      || safe.issueQuery[0].listElapsedMs > 2000) {
    throw new Error("Structured current issue query is incomplete or exceeds the 2 second acceptance budget.");
  }
  if (safe.searchScopes[0].currentHistoricalMatches !== 0 || safe.searchScopes[0].historicalCurrentMatches !== 0) {
    throw new Error("Current and historical search scopes are not isolated.");
  }
} finally {
  await driver.close();
}
