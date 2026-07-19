import { readFile, readdir } from 'node:fs/promises'
import { extname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { findBoundaryViolations, findContractViolations } from './boundary-rules.mjs'

const appRoot = resolve(fileURLToPath(new URL('..', import.meta.url)))
const repositoryRoot = resolve(appRoot, '..')
const contractRoot = resolve(repositoryRoot, 'packages/frontend-contracts/src')
const scanRoots = ['src', 'e2e', 'tests/unit']
const rootFiles = ['vite.config.ts', 'vitest.config.ts', 'playwright.config.ts']
const scannedExtensions = new Set(['.ts', '.tsx', '.js', '.mjs', '.vue', '.css', '.scss', '.less'])

async function collectFiles(path) {
  const entries = await readdir(path, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const entryPath = resolve(path, entry.name)
    if (entry.isDirectory()) files.push(...(await collectFiles(entryPath)))
    else if (scannedExtensions.has(extname(entry.name))) files.push(entryPath)
  }
  return files
}

const files = [
  ...(await Promise.all(scanRoots.map((root) => collectFiles(resolve(appRoot, root))))).flat(),
  ...rootFiles.map((file) => resolve(appRoot, file)),
]
const violations = []

for (const file of files) {
  const source = await readFile(file, 'utf8')
  violations.push(...findBoundaryViolations(source, file.replace(`${appRoot}\\`, '')))
}

const contractFiles = await collectFiles(contractRoot)
for (const file of contractFiles) {
  const source = await readFile(file, 'utf8')
  violations.push(...findBoundaryViolations(source, file))
  violations.push(...findContractViolations(source, file))
}

if (violations.length > 0) {
  for (const violation of violations) {
    console.error(`${violation.file}: ${violation.message} [${violation.rule}]`)
  }
  process.exit(1)
}

console.log(
  `Clean-room boundary passed: ${files.length} V2 files and ${contractFiles.length} contract files scanned.`,
)
