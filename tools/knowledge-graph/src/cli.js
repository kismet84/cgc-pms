import { pathToFileURL } from "node:url";
import { readFileSync } from "node:fs";
import { loadConfig } from "./config.js";
import { createDriver } from "./neo4j.js";
import { applySchema } from "./schema.js";
import { collect } from "./collector.js";
import { listIssues, recordEpisode, status } from "./queries.js";

function requiredValue(args, index, option) {
  const value = args[index + 1];
  if (!value || value.startsWith("--")) throw new Error(`${option} requires a value`);
  return value;
}

export function parseIssueOptions(args) {
  const options = {};
  const allowedValues = {
    view: new Set(["summary", "list"]),
    status: new Set(["OPEN", "NEEDS_CONFIRMATION", "FROZEN", "OBSERVATION", "RELEASE_GATE"]),
    classification: new Set(["STILL_APPLICABLE", "NEEDS_CONFIRMATION", "NON_BLOCKING_OBSERVATION", "OPERATIONAL_RISK", "RELEASE_PREREQUISITE"]),
    priority: new Set(["P0", "P1", "P2"]),
  };
  const valueOptions = new Map([
    ["--view", "view"], ["--limit", "limit"], ["--status", "status"],
    ["--classification", "classification"], ["--priority", "priority"],
    ["--parent-issue-key", "parentIssueKey"], ["--blocking", "blocking"],
    ["--query", "query"],
  ]);
  for (let index = 0; index < args.length; index += 1) {
    const option = args[index];
    if (option === "--current-only") {
      options.currentOnly = true;
      continue;
    }
    const key = valueOptions.get(option);
    if (!key) throw new Error(`Unknown issues option: ${option}`);
    const value = requiredValue(args, index, option);
    index += 1;
    if (key === "limit") {
      const limit = Number(value);
      if (!Number.isInteger(limit) || limit < 1 || limit > 200) throw new Error("--limit must be an integer between 1 and 200");
      options.limit = limit;
    } else if (key === "blocking") {
      if (!["true", "false"].includes(value)) throw new Error("--blocking must be true or false");
      options.blocking = value === "true";
    } else {
      if (allowedValues[key] && !allowedValues[key].has(value)) throw new Error(`${option} has an unsupported value: ${value}`);
      options[key] = value;
    }
  }
  return options;
}

export function parseEpisodeOptions(args, read = readFileSync) {
  if (args.length !== 2 || args[0] !== "--input") throw new Error("episode requires exactly --input <json-file>");
  const inputPath = requiredValue(args, 0, "--input");
  let input;
  try { input = JSON.parse(read(inputPath, "utf8")); } catch (error) { throw new Error(`episode input is invalid JSON: ${error instanceof Error ? error.message : String(error)}`); }
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("episode input must be a JSON object");
  if (!input.id || typeof input.id !== "string") throw new Error("episode input requires an explicit stable id");
  if (!input.sourceRef || typeof input.sourceRef !== "string") throw new Error("episode input requires sourceRef");
  return input;
}

export async function runCli(argv = process.argv.slice(2), dependencies = {}) {
  const command = argv[0] ?? "status";
  const commandArgs = argv.slice(1);
  const triggerIndex = commandArgs.indexOf("--trigger");
  const trigger = triggerIndex >= 0
    ? requiredValue(commandArgs, triggerIndex, "--trigger")
    : process.env.CGC_KG_TRIGGER ?? "manual";
  const issueOptions = command === "issues" ? parseIssueOptions(commandArgs) : null;
  const episodeInput = command === "episode" ? parseEpisodeOptions(commandArgs, dependencies.readFileSync ?? readFileSync) : null;
  const config = (dependencies.loadConfig ?? loadConfig)();
  const driver = (dependencies.createDriver ?? createDriver)(config);
  try {
    await driver.verifyConnectivity();
    let result;
    if (command === "schema") result = { appliedStatements: await applySchema(driver, config) };
    else if (command === "collect") {
      await applySchema(driver, config);
      result = await collect(driver, config, { trigger });
    } else if (command === "status") result = await status(driver, config);
    else if (command === "issues") result = await listIssues(driver, config, issueOptions);
    else if (command === "episode") {
      await applySchema(driver, config);
      result = await recordEpisode(driver, config, episodeInput);
    }
    else throw new Error(`Unknown command: ${command}`);
    return result;
  } finally {
    await driver.close();
  }
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  try {
    const result = await runCli();
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}
