import { normalizePath } from "./document-parser.js";

export const ALLOWED_ROOTS = ["docs", "plugins/cgc-pms-autopilot/artifacts"];
export const BLOCKED_SEGMENTS = [
  ".omc", ".omo", ".opencode", ".claude", ".mimocode", "graphify-out",
  ".sisyphus", ".archive", "archive/v1.0/private", "docs/archive/v1.0",
];

export function isBlocked(relativePath) {
  const normalized = normalizePath(relativePath).toLowerCase();
  return BLOCKED_SEGMENTS.some((segment) => normalized === segment || normalized.startsWith(`${segment}/`) || normalized.includes(`/${segment}/`));
}
