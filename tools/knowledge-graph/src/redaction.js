const SENSITIVE_KEY = /(^|_)(password|passwd|secret|token|authorization|cookie|api[_-]?key|private[_-]?key|connection[_-]?string)($|_)/i;
const INLINE_PATTERNS = [
  /\b(Bearer\s+)[A-Za-z0-9._~+\/-]+=*/gi,
  /\b(sk-[A-Za-z0-9_-]{12,})\b/g,
  /\b(password|passwd|secret|token|api[_-]?key)\s*[:=]\s*([^\s,;]+)/gi,
  /(bolt|neo4j|https?):\/\/([^:@/\s]+):([^@/\s]+)@/gi,
];

export function redactText(value) {
  let result = String(value ?? "");
  result = result.replace(INLINE_PATTERNS[0], "$1[REDACTED]");
  result = result.replace(INLINE_PATTERNS[1], "[REDACTED]");
  result = result.replace(INLINE_PATTERNS[2], "$1=[REDACTED]");
  result = result.replace(INLINE_PATTERNS[3], "$1://$2:[REDACTED]@");
  return result;
}

export function redactDeep(value, key = "") {
  if (SENSITIVE_KEY.test(key)) return "[REDACTED]";
  if (typeof value === "string") return redactText(value);
  if (Array.isArray(value)) return value.map((item) => redactDeep(item));
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([childKey, child]) => [childKey, redactDeep(child, childKey)]));
  }
  return value;
}
