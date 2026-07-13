import { loadConfig } from "./config.js";
import { createDriver } from "./neo4j.js";
import { applySchema } from "./schema.js";
import { collect } from "./collector.js";
import { status } from "./queries.js";

const command = process.argv[2] ?? "status";
const triggerIndex = process.argv.indexOf("--trigger");
const trigger = triggerIndex >= 0 ? process.argv[triggerIndex + 1] : process.env.CGC_KG_TRIGGER ?? "manual";
const config = loadConfig();
const driver = createDriver(config);

try {
  await driver.verifyConnectivity();
  if (command === "schema") console.log(JSON.stringify({ appliedStatements: await applySchema(driver, config) }, null, 2));
  else if (command === "collect") {
    await applySchema(driver, config);
    console.log(JSON.stringify(await collect(driver, config, { trigger }), null, 2));
  } else if (command === "status") console.log(JSON.stringify(await status(driver, config), null, 2));
  else throw new Error(`Unknown command: ${command}`);
} finally {
  await driver.close();
}
