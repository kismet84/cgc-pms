import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { extname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const SCANNED_EXTENSIONS = new Set(['.vue', '.ts', '.js', '.css'])
const EXCLUDED_SEGMENTS = new Set(['node_modules', 'dist', 'output', '__tests__'])
const EXCLUDED_FILES = new Set(['components.d.ts', 'vite-env.d.ts', 'global.css'])

export const rules = [
  {
    rule: 'inline-style',
    pattern: /(?<![:@\w-])style\s*=/gi,
    description: '静态内联 style 会绕过统一 token 和组件规范',
  },
  {
    rule: 'hex-color',
    pattern: /#[0-9a-fA-F]{3,8}\b/g,
    description: '裸 hex 色值应迁移到 theme token 或语义 CSS 变量',
  },
  {
    rule: 'rgba-color',
    pattern: /\brgba?\s*\(/gi,
    description: '裸 rgb/rgba 色值应迁移到 theme token 或语义 CSS 变量',
  },
  {
    rule: 'linear-gradient',
    pattern: /\blinear-gradient\s*\(/gi,
    description: '业务页渐变应收敛到品牌/状态 token',
  },
  {
    rule: 'box-shadow',
    pattern: /\bbox-shadow\s*:/gi,
    description: '阴影应使用统一 shadow token',
    allowMatch(content, index) {
      const declaration = getDeclaration(content, index)
      return /\bbox-shadow\s*:\s*(var\(\s*--shadow-|none\b)/i.test(declaration)
    },
  },
  {
    rule: 'border-radius',
    pattern: /\bborder-radius\s*:/gi,
    description: '圆角应使用统一 radius token',
    allowMatch(content, index) {
      const declaration = getDeclaration(content, index)
      return /\bborder-radius\s*:\s*var\(\s*--radius-/i.test(declaration)
    },
  },
]

function getDeclaration(content, index) {
  const lineEnd = content.indexOf('\n', index)
  const declarationEnd = content.indexOf(';', index)
  const end =
    declarationEnd === -1
      ? lineEnd === -1
        ? content.length
        : lineEnd
      : lineEnd === -1
        ? declarationEnd + 1
        : Math.min(declarationEnd + 1, lineEnd)

  return content.slice(index, end)
}

function normalizePath(filePath) {
  return filePath.replaceAll('\\', '/')
}

export function shouldScanFile(filePath) {
  const normalized = normalizePath(filePath)
  const segments = normalized.split('/')
  const fileName = segments.at(-1)

  if (!SCANNED_EXTENSIONS.has(extname(fileName))) return false
  if (EXCLUDED_FILES.has(fileName)) return false
  if (segments.some((segment) => EXCLUDED_SEGMENTS.has(segment))) return false
  if (normalized.startsWith('src/theme/')) return false

  return true
}

function walkFiles(rootDir) {
  const files = []

  function walk(currentDir) {
    for (const entry of readdirSync(currentDir, { withFileTypes: true })) {
      const absolutePath = join(currentDir, entry.name)
      const relativePath = normalizePath(relative(rootDir, absolutePath))
      const segments = relativePath.split('/')

      if (entry.isDirectory()) {
        if (!segments.some((segment) => EXCLUDED_SEGMENTS.has(segment))) {
          walk(absolutePath)
        }
        continue
      }

      const projectPath = normalizePath(join('src', relativePath))
      if (shouldScanFile(projectPath)) {
        files.push(absolutePath)
      }
    }
  }

  walk(rootDir)
  return files
}

function lineAndColumn(content, index) {
  const before = content.slice(0, index)
  const lines = before.split('\n')
  return {
    line: lines.length,
    column: lines.at(-1).length + 1,
  }
}

export function scanContent(filePath, content) {
  const findings = []

  for (const { rule, pattern, description, allowMatch } of rules) {
    pattern.lastIndex = 0
    let match
    while ((match = pattern.exec(content)) !== null) {
      if (allowMatch?.(content, match.index)) continue
      const { line, column } = lineAndColumn(content, match.index)
      findings.push({
        file: normalizePath(filePath),
        rule,
        line,
        column,
        match: match[0],
        description,
      })
    }
  }

  return findings.sort(
    (a, b) => a.line - b.line || a.column - b.column || a.rule.localeCompare(b.rule),
  )
}

export function scanSource(rootDir = join(process.cwd(), 'src')) {
  if (!existsSync(rootDir)) {
    throw new Error(`Source directory not found: ${rootDir}`)
  }

  const findings = []
  for (const file of walkFiles(rootDir)) {
    const relativeFile = normalizePath(join('src', relative(rootDir, file)))
    const content = readFileSync(file, 'utf8')
    findings.push(...scanContent(relativeFile, content))
  }

  return findings
}

export function summarizeFindings(findings) {
  const byRule = {}
  const byFile = {}

  for (const finding of findings) {
    byRule[finding.rule] = (byRule[finding.rule] ?? 0) + 1
    byFile[finding.file] = (byFile[finding.file] ?? 0) + 1
  }

  return {
    total: findings.length,
    byRule,
    topFiles: Object.entries(byFile)
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .slice(0, 20)
      .map(([file, count]) => ({ file, count })),
  }
}

function formatTextReport(summary, findings) {
  const lines = [
    'UI style consistency scan',
    `Total findings: ${summary.total}`,
    '',
    'Findings by rule:',
    ...Object.entries(summary.byRule).map(([rule, count]) => `- ${rule}: ${count}`),
    '',
    'Top files:',
    ...summary.topFiles.map(({ file, count }) => `- ${file}: ${count}`),
    '',
    'First 50 findings:',
    ...findings
      .slice(0, 50)
      .map(
        (finding) =>
          `- ${finding.file}:${finding.line}:${finding.column} ${finding.rule} ${finding.match}`,
      ),
  ]

  return lines.join('\n')
}

function runCli() {
  const args = new Set(process.argv.slice(2))
  const findings = scanSource()
  const summary = summarizeFindings(findings)

  if (args.has('--json')) {
    console.log(JSON.stringify({ summary, findings }, null, 2))
  } else {
    console.log(formatTextReport(summary, findings))
  }
}

const isCli =
  process.argv[1] &&
  fileURLToPath(import.meta.url)
    .split(sep)
    .join('/') === normalizePath(process.argv[1])

if (isCli) {
  runCli()
}
