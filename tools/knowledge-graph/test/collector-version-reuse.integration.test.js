import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { loadConfig } from "../src/config.js";
import { upsertArtifact } from "../src/collector.js";
import { startCollectionRun, finishCollectionRun } from "../src/collection-run.js";
import { createDriver, execute } from "../src/neo4j.js";
import { applySchema } from "../src/schema.js";

const sha256 = (content) => crypto.createHash("sha256").update(content).digest("hex");

test("collector reuses sections when document content returns to a previous version", async () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), "cgc-kg-section-"));
  const documentPath = path.join(tempRoot, "sample.md");
  const projectKey = `cgc-pms-section-reuse-${crypto.randomUUID()}`;
  const config = { ...loadConfig(), repoRoot: tempRoot, projectKey };
  const driver = createDriver(config);
  const contentA = "# Alpha\n\n## One\n\nFirst version.\n";
  const contentB = "# Beta\n\n## Two\n\nSecond version.\n";
  const artifactId = `${projectKey}:artifact:sample.md`;
  const versionAId = `${artifactId}:version:${sha256(contentA)}`;

  async function collectContent(content) {
    fs.writeFileSync(documentPath, content, "utf8");
    const runId = await startCollectionRun(driver, config, "section-reuse-test");
    const result = await upsertArtifact(driver, config, runId, documentPath);
    await finishCollectionRun(driver, config, runId, {
      processed: 1,
      [result.state]: 1,
      unresolvedReferences: result.unresolved,
    });
  }

  try {
    await applySchema(driver, config);
    await execute(driver, config.neo4jDatabase, `
      MERGE (p:Project {key: $projectKey})
      SET p.name = $projectKey
    `, { projectKey }, "WRITE");

    await collectContent(contentA);
    await collectContent(contentB);
    await collectContent(contentA);
    await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact {id: $artifactId})-[r:CONTAINS]->(:Section)
      DELETE r
    `, { artifactId }, "WRITE");
    await collectContent(contentA);

    const current = await execute(driver, config.neo4jDatabase, `
      MATCH (a:Artifact {id: $artifactId})-[:CURRENT_VERSION]->(v:ArtifactVersion)
      MATCH (a)-[:CONTAINS]->(s:Section)
      RETURN v.id AS versionId, count(s) AS sections, count(DISTINCT s.id) AS distinctSections
    `, { artifactId });
    assert.equal(current[0].versionId, versionAId);
    assert.equal(Number(current[0].sections), 2);
    assert.equal(Number(current[0].distinctSections), 2);

    const allSections = await execute(driver, config.neo4jDatabase, `
      MATCH (s:Section)
      WHERE s.id STARTS WITH $sectionPrefix
      RETURN count(s) AS sections, count(DISTINCT s.id) AS distinctSections
    `, { sectionPrefix: `${artifactId}:version:` });
    assert.equal(Number(allSections[0].sections), 4);
    assert.equal(Number(allSections[0].distinctSections), 4);
  } finally {
    await execute(driver, config.neo4jDatabase, `
      MATCH (s:Section) WHERE s.id STARTS WITH $prefix DETACH DELETE s
    `, { prefix: `${artifactId}:version:` }, "WRITE");
    await execute(driver, config.neo4jDatabase, `
      MATCH (v:ArtifactVersion) WHERE v.id STARTS WITH $prefix DETACH DELETE v
    `, { prefix: `${artifactId}:version:` }, "WRITE");
    await execute(driver, config.neo4jDatabase, "MATCH (a:Artifact {id: $artifactId}) DETACH DELETE a", { artifactId }, "WRITE");
    await execute(driver, config.neo4jDatabase, `
      MATCH (r:CollectionRun) WHERE r.id STARTS WITH $runPrefix DETACH DELETE r
    `, { runPrefix: `${projectKey}:run:` }, "WRITE");
    await execute(driver, config.neo4jDatabase, "MATCH (p:Project {key: $projectKey}) DETACH DELETE p", { projectKey }, "WRITE");
    await driver.close();
    if (fs.existsSync(documentPath)) fs.unlinkSync(documentPath);
    if (fs.existsSync(tempRoot)) fs.rmdirSync(tempRoot);
  }
});
