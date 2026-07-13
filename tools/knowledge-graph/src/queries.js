import { execute, jsonSafe } from "./neo4j.js";
import { recentCollectionRuns } from "./collection-run.js";
export { recordEpisode } from "./episode-collector.js";

function rows(result) {
  return jsonSafe(result);
}

export async function status(driver, config) {
  return rows(await execute(driver, config.neo4jDatabase, `
    MATCH (p:Project {key: $projectKey})
    CALL (p) {
      OPTIONAL MATCH (a:Artifact)-[:IN_PROJECT]->(p)
      WHERE coalesce(a.active, true) = true
      RETURN count(a) AS artifacts,
             count(CASE WHEN coalesce(a.historical, false) = false THEN 1 END) AS currentArtifacts,
             count(CASE WHEN a.historical = true THEN 1 END) AS historicalArtifacts,
             max(a.lastSeenAt) AS lastIndexedAt
    }
    CALL (p) { OPTIONAL MATCH (c:GitCommit)-[:IN_PROJECT]->(p) RETURN count(c) AS commits }
    CALL (p) { OPTIONAL MATCH (e:Episode)-[:IN_PROJECT]->(p) RETURN count(e) AS episodes }
    CALL (p) {
      OPTIONAL MATCH (i:Issue)-[:IN_PROJECT]->(p)
      RETURN count(CASE WHEN coalesce(i.current, false) = true THEN 1 END) AS currentIssues,
             count(CASE WHEN coalesce(i.current, false) = true AND i.blocking = true THEN 1 END) AS blockingIssues
    }
    CALL (p) { OPTIONAL MATCH (r:CollectionRun)-[:IN_PROJECT]->(p) WITH r ORDER BY r.startedAt DESC LIMIT 1 RETURN r.status AS lastRunStatus, r.startedAt AS lastRunAt, r.failed AS lastRunFailures }
    OPTIONAL MATCH (cursor:SourceCursor)-[:CURSOR_FOR]->(source:Source)-[:IN_PROJECT]->(p)
    RETURN p.key AS project, artifacts, currentArtifacts, historicalArtifacts,
           commits, episodes, currentIssues, blockingIssues, lastIndexedAt,
           lastRunStatus, lastRunAt, lastRunFailures,
           collect({source: source.key, cursor: cursor.cursor, lastSuccessAt: cursor.lastSuccessAt}) AS cursors
  `, { projectKey: config.projectKey }));
}

function issueFilters(options = {}) {
  const currentOnly = options.currentOnly ?? true;
  const params = {
    status: options.status ?? null,
    classification: options.classification ?? null,
    priority: options.priority ?? null,
    parentIssueKey: options.parentIssueKey ?? null,
    blocking: options.blocking ?? null,
    currentOnly,
    queryText: options.query?.trim().toLowerCase() || null,
  };
  const where = `
    WHERE ($currentOnly = false OR coalesce(i.current, false) = true)
      AND ($status IS NULL OR i.status = $status)
      AND ($classification IS NULL OR i.classification = $classification)
      AND ($priority IS NULL OR i.priority = $priority)
      AND ($parentIssueKey IS NULL OR i.parentIssueKey = $parentIssueKey)
      AND ($blocking IS NULL OR i.blocking = $blocking)
      AND ($queryText IS NULL OR toLower(i.issueKey) CONTAINS $queryText
        OR toLower(i.title) CONTAINS $queryText OR toLower(i.summary) CONTAINS $queryText)
  `;
  return { params, where };
}

export async function listIssues(driver, config, options = {}) {
  const view = options.view ?? "summary";
  if (!["summary", "list"].includes(view)) throw new Error(`Unsupported issue view: ${view}`);
  const { params, where } = issueFilters(options);
  const base = `MATCH (i:Issue)-[:IN_PROJECT]->(:Project {key: $projectKey}) ${where}`;
  const queryParams = { ...params, projectKey: config.projectKey };

  if (view === "summary") {
    const [totalRows, statusRows, classificationRows, priorityRows, parentRows] = await Promise.all([
      execute(driver, config.neo4jDatabase, `${base} RETURN count(i) AS count`, queryParams),
      execute(driver, config.neo4jDatabase, `${base} RETURN i.status AS key, count(i) AS count ORDER BY key`, queryParams),
      execute(driver, config.neo4jDatabase, `${base} RETURN i.classification AS key, count(i) AS count ORDER BY key`, queryParams),
      execute(driver, config.neo4jDatabase, `${base} RETURN i.priority AS key, count(i) AS count ORDER BY key`, queryParams),
      execute(driver, config.neo4jDatabase, `${base} RETURN coalesce(i.parentIssueKey, 'ROOT') AS key, count(i) AS count ORDER BY count DESC, key`, queryParams),
    ]);
    const toCounts = (values) => Object.fromEntries(rows(values).map((item) => [item.key, item.count]));
    return {
      total: rows(totalRows)[0]?.count ?? 0,
      filters: params,
      byStatus: toCounts(statusRows),
      byClassification: toCounts(classificationRows),
      byPriority: toCounts(priorityRows),
      byParent: toCounts(parentRows),
    };
  }

  const safeLimit = Math.max(1, Math.min(Number(options.limit) || 100, 200));
  const [totalRows, issueRows] = await Promise.all([
    execute(driver, config.neo4jDatabase, `${base} RETURN count(i) AS count`, queryParams),
    execute(driver, config.neo4jDatabase, `
      ${base}
      RETURN i { .issueKey, .title, .status, .classification, .priority, .blocking,
                 .parentIssueKey, .summary, .acceptanceCriteria, .deferReason,
                 .sourceRefs, .current, .versionScope, .updatedAt } AS issue
      ORDER BY CASE i.priority WHEN 'P0' THEN 0 WHEN 'P1' THEN 1 ELSE 2 END,
               coalesce(i.parentIssueKey, i.issueKey), i.issueKey
      LIMIT toInteger($limit)
    `, { ...queryParams, limit: safeLimit }),
  ]);
  const items = rows(issueRows).map((item) => item.issue);
  return { total: rows(totalRows)[0]?.count ?? 0, returned: items.length, filters: params, issues: items };
}

export async function search(driver, config, query, limit = 20, scope = "current") {
  const safeLimit = Math.max(1, Math.min(Number(limit) || 20, 50));
  if (!["current", "historical", "all"].includes(scope)) throw new Error(`Unsupported search scope: ${scope}`);
  return rows(await execute(driver, config.neo4jDatabase, `
    CALL db.index.fulltext.queryNodes('knowledge_text', $query, {limit: $candidateLimit})
    YIELD node, score
    OPTIONAL MATCH (a:Artifact)-[:CONTAINS]->(node)
    WITH node, score, a, coalesce(node.historical, a.historical, false) AS historical
    WHERE $scope = 'all'
       OR ($scope = 'historical' AND historical = true)
       OR ($scope = 'current' AND historical = false)
    RETURN labels(node) AS labels, node.id AS id, node.title AS title,
           coalesce(node.path, a.path) AS sourcePath,
           coalesce(node.summary, left(node.content, 500)) AS excerpt,
           historical, coalesce(node.versionScope, a.versionScope) AS versionScope, score
    ORDER BY score DESC
    LIMIT toInteger($limit)
  `, { query, limit: safeLimit, candidateLimit: Math.min(safeLimit * 10, 500), scope }));
}

export async function getArtifact(driver, config, sourcePath) {
  return rows(await execute(driver, config.neo4jDatabase, `
    MATCH (a:Artifact {path: $path})
    OPTIONAL MATCH (a)-[:CURRENT_VERSION]->(current:ArtifactVersion)
    OPTIONAL MATCH (version:ArtifactVersion)-[:VERSION_OF]->(a)
    OPTIONAL MATCH (a)-[:CONTAINS]->(s:Section)
    RETURN a { .id, .path, .title, .kind, .sha256, .modifiedAt, .lastSeenAt, .active } AS artifact,
           current { .id, .sha256, .modifiedAt, .createdAt } AS currentVersion,
           collect(DISTINCT version { .id, .sha256, .modifiedAt, .createdAt }) AS versions,
           collect(DISTINCT s { .id, .title, .level, .ordinal, .content }) AS sections
  `, { path: sourcePath }));
}

export async function collectionRuns(driver, config, limit = 20) {
  return recentCollectionRuns(driver, config, limit);
}

export async function unresolvedReferences(driver, config, limit = 20) {
  const safeLimit = Math.max(1, Math.min(Number(limit) || 20, 100));
  return rows(await execute(driver, config.neo4jDatabase, `
    MATCH (r:CollectionRun)-[:IN_PROJECT]->(:Project {key: $projectKey})
    WHERE r.unresolvedReferences > 0
    RETURN r.id AS runId, r.startedAt AS startedAt,
           r.unresolvedReferences AS unresolvedReferences, r.status AS status
    ORDER BY r.startedAt DESC LIMIT toInteger($limit)
  `, { projectKey: config.projectKey, limit: safeLimit }));
}

export async function neighbors(driver, config, id, depth = 1) {
  const safeDepth = Math.max(1, Math.min(Number(depth) || 1, 3));
  const query = `
    MATCH (n {id: $id})
    MATCH path=(n)-[*1..${safeDepth}]-(other)
    RETURN DISTINCT labels(other) AS labels, other.id AS id,
           coalesce(other.title, other.name, other.subject, other.path) AS name,
           [r IN relationships(path) | type(r)] AS relationships
    LIMIT 100
  `;
  return rows(await execute(driver, config.neo4jDatabase, query, { id }));
}

export function assertReadOnlyCypher(query) {
  const normalized = query.replace(/\/\*[\s\S]*?\*\//g, " ").replace(/\/\/.*$/gm, " ").trim();
  if (!/^(MATCH|OPTIONAL\s+MATCH|WITH|RETURN|UNWIND|SHOW)\b/i.test(normalized)) throw new Error("Only read-only Cypher is allowed.");
  if (/\b(CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP|LOAD\s+CSV|FOREACH|CALL|GRANT|DENY|REVOKE|TERMINATE)\b/i.test(normalized)) {
    throw new Error("Mutating or procedure Cypher is not allowed.");
  }
}

export async function readOnlyQuery(driver, config, query, params = {}) {
  assertReadOnlyCypher(query);
  const normalized = query.trim().replace(/;+$/, "");
  const bounded = /\bLIMIT\s+\d+\s*$/i.test(normalized) ? normalized : `${normalized}\nLIMIT 200`;
  return rows(await execute(driver, config.neo4jDatabase, bounded, params));
}
