import path from "node:path";

export function normalizePath(value) {
  return value.replaceAll("\\", "/");
}

export function parseMarkdown(content) {
  const lines = content.split(/\r?\n/);
  const title = lines.find((line) => /^#\s+/.test(line))?.replace(/^#\s+/, "").trim() ?? "";
  const headings = [];
  for (let index = 0; index < lines.length; index += 1) {
    const match = /^(#{1,6})\s+(.+)$/.exec(lines[index]);
    if (!match) continue;
    const level = match[1].length;
    let end = lines.length;
    for (let cursor = index + 1; cursor < lines.length; cursor += 1) {
      const next = /^(#{1,6})\s+/.exec(lines[cursor]);
      if (next && next[1].length <= level) { end = cursor; break; }
    }
    headings.push({
      level, title: match[2].trim(),
      content: lines.slice(index + 1, end).join("\n").trim().slice(0, 12000),
      ordinal: headings.length,
    });
  }
  return { title, headings };
}

export function markdownLinks(content, sourcePath, isBlocked = () => false) {
  const links = new Set();
  const prose = content
    .replace(/^(?:```|~~~)[^\r\n]*[\r\n][\s\S]*?^(?:```|~~~)\s*$/gm, "")
    .replace(/`[^`\r\n]*`/g, "");
  for (const match of prose.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const raw = match[1].trim().replace(/^<|>$/g, "").split("#", 1)[0];
    if (!raw || /^(https?:|mailto:|data:)/i.test(raw)) continue;
    let decoded;
    try { decoded = decodeURIComponent(raw); } catch { decoded = raw; }
    const target = normalizePath(path.normalize(path.join(path.dirname(sourcePath), decoded)));
    if (!target.startsWith("../") && !path.isAbsolute(target) && !isBlocked(target)) links.add(target);
  }
  return [...links];
}
