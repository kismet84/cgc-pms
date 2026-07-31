import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { extname, join, relative } from 'node:path'
import { pathToFileURL } from 'node:url'

const SCANNED_EXTENSIONS = new Set(['.vue'])
const EXCLUDED_SEGMENTS = new Set(['node_modules', 'dist', 'output', '__tests__'])

function normalizePath(filePath) {
  return filePath.replaceAll('\\', '/')
}

function lineAndColumn(content, index) {
  const before = content.slice(0, index)
  const lines = before.split('\n')
  return {
    line: lines.length,
    column: lines.at(-1).length + 1,
  }
}

function hasAccessibleName(openTag) {
  return (
    /\s:?aria-label\s*=/.test(openTag) ||
    /\s:?title\s*=/.test(openTag) ||
    /\s:?aria-labelledby\s*=/.test(openTag)
  )
}

function hasIconOnlyContent(buttonBlock) {
  const withoutTemplates = buttonBlock.replace(/<template\s+#icon[\s\S]*?<\/template>/g, '')
  const withoutTags = withoutTemplates.replace(/<[^>]+>/g, '')
  return (
    withoutTags.trim().length === 0 &&
    /Outlined\s*\/>|<MoreOutlined|<ReloadOutlined|<DeleteOutlined/.test(buttonBlock)
  )
}

export function scanAccessibilityContent(filePath, content) {
  const findings = []
  const buttonPattern = /<a-button\b[^>]*>[\s\S]*?<\/a-button>/g
  let match

  while ((match = buttonPattern.exec(content)) !== null) {
    const block = match[0]
    const openTag = block.match(/<a-button\b[^>]*>/)?.[0] ?? ''
    const isRowAction = /lg-row-action-trigger/.test(openTag)
    const isIconOnly = hasIconOnlyContent(block)

    if ((isRowAction || isIconOnly) && !hasAccessibleName(openTag)) {
      const position = lineAndColumn(content, match.index)
      findings.push({
        file: normalizePath(filePath),
        rule: 'icon-button-accessible-name',
        line: position.line,
        column: position.column,
        match: openTag,
        description:
          'Icon-only or row action button must provide aria-label, aria-labelledby, or title.',
      })
    }
  }

  return findings.sort((a, b) => a.line - b.line || a.column - b.column)
}

function shouldScanFile(filePath) {
  const normalized = normalizePath(filePath)
  const segments = normalized.split('/')
  const fileName = segments.at(-1)
  return (
    SCANNED_EXTENSIONS.has(extname(fileName)) &&
    !segments.some((segment) => EXCLUDED_SEGMENTS.has(segment))
  )
}

function walkFiles(rootDir) {
  const files = []

  function walk(currentDir) {
    for (const entry of readdirSync(currentDir, { withFileTypes: true })) {
      const absolutePath = join(currentDir, entry.name)
      const relativePath = normalizePath(relative(rootDir, absolutePath))

      if (entry.isDirectory()) {
        if (!relativePath.split('/').some((segment) => EXCLUDED_SEGMENTS.has(segment))) {
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

export function scanAccessibilitySource(rootDir = join(process.cwd(), 'src')) {
  if (!existsSync(rootDir)) {
    throw new Error(`Source directory not found: ${rootDir}`)
  }

  const findings = []
  for (const file of walkFiles(rootDir)) {
    const relativeFile = normalizePath(join('src', relative(rootDir, file)))
    const content = readFileSync(file, 'utf8')
    findings.push(...scanAccessibilityContent(relativeFile, content))
  }
  return findings
}

function printFindings(findings) {
  console.log('UI accessibility consistency scan')
  console.log(`Total findings: ${findings.length}`)

  if (findings.length > 0) {
    console.log('\nFirst 50 findings:')
    for (const item of findings.slice(0, 50)) {
      console.log(`- ${item.file}:${item.line}:${item.column} ${item.rule} ${item.match}`)
    }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const findings = scanAccessibilitySource()
  printFindings(findings)
  if (findings.length > 0) {
    process.exitCode = 1
  }
}
