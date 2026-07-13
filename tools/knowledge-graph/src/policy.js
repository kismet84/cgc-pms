import { normalizePath } from "./document-parser.js";

export const ALLOWED_ROOTS = ["docs", "plugins/cgc-pms-autopilot/artifacts"];
export const BLOCKED_SEGMENTS = [
  ".omc", ".omo", ".opencode", ".claude", ".mimocode", "graphify-out",
  ".sisyphus", ".archive", "archive/v1.0/private",
];

export function historyScope(relativePath) {
  const normalized = normalizePath(relativePath).toLowerCase();
  return normalized === "docs/archive/v1.0" || normalized.startsWith("docs/archive/v1.0/") ? "v1.0" : null;
}

export function isBlocked(relativePath) {
  const normalized = normalizePath(relativePath).toLowerCase();
  return BLOCKED_SEGMENTS.some((segment) => normalized === segment || normalized.startsWith(`${segment}/`) || normalized.includes(`/${segment}/`));
}
