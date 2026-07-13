import neo4j from "neo4j-driver";
import { assertConfig } from "./config.js";

export function createDriver(config) {
  assertConfig(config);
  return neo4j.driver(config.neo4jUri, neo4j.auth.basic(config.neo4jUser, config.neo4jPassword));
}

export async function execute(driver, database, query, params = {}, mode = "READ") {
  const session = driver.session({ database, defaultAccessMode: mode === "WRITE" ? neo4j.session.WRITE : neo4j.session.READ });
  try {
    const result = await session.run(query, params);
    return result.records.map((record) => record.toObject());
  } finally {
    await session.close();
  }
}

export function jsonSafe(value) {
  if (neo4j.isInt(value)) return value.inSafeRange() ? value.toNumber() : value.toString();
  if (Array.isArray(value)) return value.map(jsonSafe);
  if (value && typeof value === "object") {
    if (value.properties) return { ...value, properties: jsonSafe(value.properties) };
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, jsonSafe(item)]));
  }
  return value;
}
