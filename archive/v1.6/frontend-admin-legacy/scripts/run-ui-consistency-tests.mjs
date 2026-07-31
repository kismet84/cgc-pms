import { spawnSync } from 'node:child_process'
import { readdirSync } from 'node:fs'
import { join, relative } from 'node:path'

const rootDir = process.cwd()
const sourceDir = join(rootDir, 'src')

function normalizePath(filePath) {
  return filePath.replaceAll('\\', '/')
}

function collectTests(currentDir, tests = []) {
  for (const entry of readdirSync(currentDir, { withFileTypes: true })) {
    const absolutePath = join(currentDir, entry.name)

    if (entry.isDirectory()) {
      collectTests(absolutePath, tests)
      continue
    }

    if (entry.name.endsWith('-ui-consistency.test.ts')) {
      tests.push(normalizePath(relative(rootDir, absolutePath)))
    }
  }

  return tests
}

const tests = collectTests(sourceDir).sort()

if (tests.length === 0) {
  console.error('No UI consistency test files found.')
  process.exit(1)
}

console.log(`Running ${tests.length} UI consistency test files`)

const result = spawnSync('pnpm', ['exec', 'vitest', 'run', ...tests], {
  cwd: rootDir,
  shell: process.platform === 'win32',
  stdio: 'inherit',
})

process.exit(result.status ?? 1)
