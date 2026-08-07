import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import { contractSpecs, liveSpecs, specialSpecs } from './e2e-spec-groups.mjs'

const e2eDir = resolve('e2e')
const actualSpecs = readdirSync(e2eDir)
  .filter((name) => name.endsWith('.spec.ts'))
  .sort()
const classifiedSpecs = [...contractSpecs, ...liveSpecs, ...specialSpecs].sort()

if (JSON.stringify(actualSpecs) !== JSON.stringify(classifiedSpecs)) {
  const classified = new Set(classifiedSpecs)
  const actual = new Set(actualSpecs)
  console.error(
    `E2E classification drift. Unclassified: ${actualSpecs.filter((name) => !classified.has(name)).join(', ') || 'none'}; missing: ${classifiedSpecs.filter((name) => !actual.has(name)).join(', ') || 'none'}`,
  )
  process.exit(1)
}

const specs = contractSpecs.map((name) => `e2e/${name}`)
console.log(`Running ${specs.length} deterministic V2 browser contract specs`)
for (const spec of specs) console.log(`- ${spec}`)
const workers = process.env.PLAYWRIGHT_MIGRATION_WORKERS || (process.env.CI ? '2' : '4')
const reportDir = resolve('test-results')
const reportPath = resolve(reportDir, 'contract-results.json')
mkdirSync(reportDir, { recursive: true })
rmSync(reportPath, { force: true })
const result = spawnSync(
  'pnpm',
  ['exec', 'playwright', 'test', '--reporter=line,json', '--workers', workers, ...specs],
  {
    shell: process.platform === 'win32',
    stdio: 'inherit',
    env: { ...process.env, PLAYWRIGHT_JSON_OUTPUT_NAME: reportPath },
  },
)

if (!existsSync(reportPath)) {
  console.error(`Playwright contract report missing: ${reportPath}`)
  process.exit(1)
}

const { stats } = JSON.parse(readFileSync(reportPath, 'utf8'))
console.log(
  `Browser contract result: passed=${stats.expected}, skipped=${stats.skipped}, unexpected=${stats.unexpected}, flaky=${stats.flaky}, durationMs=${Math.round(stats.duration)}`,
)
if (stats.skipped > 0) {
  console.error('Browser contract gate forbids skipped tests.')
  process.exit(1)
}

process.exit(result.status ?? 1)
