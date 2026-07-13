import { execFileSync } from "node:child_process";
import { execute } from "./neo4j.js";
import { getCursor, advanceCursor } from "./collection-run.js";
import { isBlocked } from "./policy.js";

export function parseGitLog(output) {
  return output.split("\x1e").map((item) => item.trim()).filter(Boolean).map((record) => {
    const lines = record.split(/\r?\n/).filter(Boolean);
    const [hash, authoredAt, authorName, authorEmail, subject] = lines.shift().split("\x1f");
    return { hash, authoredAt, authorName, authorEmail, subject, files: [...new Set(lines)] };
  });
}

function git(config, args) {
  return execFileSync("git", args, { cwd: config.repoRoot, encoding: "utf8" }).trim();
}

export async function collectGit(driver, config, runId, limit = 250) {
  const cursor = await getCursor(driver, config, "git");
  const head = git(config, ["rev-parse", "HEAD"]);
  if (cursor === head) return { commits: 0, files: 0, cursor: head };
  let rangeArgs;
  if (cursor) {
    try {
      git(config, ["merge-base", "--is-ancestor", cursor, "HEAD"]);
      rangeArgs = [`${cursor}..HEAD`, "--reverse"];
    } catch {
      rangeArgs = [`-${limit}`, "--reverse"];
    }
  } else rangeArgs = [`-${limit}`, "--reverse"];
  const format = "%x1e%H%x1f%aI%x1f%an%x1f%ae%x1f%s";
  const output = git(config, ["log", ...rangeArgs, `--pretty=format:${format}`, "--name-only"]);
  const records = parseGitLog(output);
  let fileLinks = 0;
  for (const record of records) {
    await execute(driver, config.neo4jDatabase, `
      MATCH (p:Project {key: $projectKey}), (r:CollectionRun {id: $runId}), (s:Source {key: 'git'})
      MERGE (c:GitCommit {hash: $hash})
      SET c.authoredAt = datetime($authoredAt), c.authorName = $authorName,
          c.authorEmail = $authorEmail, c.subject = $subject
      MERGE (c)-[:IN_PROJECT]->(p)
      MERGE (c)-[:COLLECTED_IN]->(r)
      MERGE (c)-[:DERIVED_FROM]->(s)
    `, { projectKey: config.projectKey, runId, ...record }, "WRITE");
    for (const file of record.files.filter((value) => !isBlocked(value))) {
      await execute(driver, config.neo4jDatabase, `
        MATCH (p:Project {key: $projectKey}), (c:GitCommit {hash: $hash}), (source:Source {key: 'git'})
        MERGE (a:Artifact {id: $projectKey + ':artifact:' + $path})
        ON CREATE SET a.path = $path, a.title = $path, a.kind = 'repository-file',
                      a.active = true, a.createdAt = datetime()
        MERGE (a)-[:IN_PROJECT]->(p)
        MERGE (a)-[:DERIVED_FROM]->(source)
        MERGE (c)-[:CHANGES]->(a)
      `, { projectKey: config.projectKey, hash: record.hash, path: file.replaceAll("\\", "/") }, "WRITE");
      fileLinks += 1;
    }
  }
  await advanceCursor(driver, config, "git", head, runId);
  return { commits: records.length, files: fileLinks, cursor: head };
}
