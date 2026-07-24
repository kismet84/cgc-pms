import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

const e2eDir = resolve('e2e')
const specs = readdirSync(e2eDir)
  .filter((name) => /^m\d.*\.spec\.ts$/.test(name))
  .filter((name) => !readFileSync(resolve(e2eDir, name), 'utf-8').includes('const runLive'))
  .sort()
  .map((name) => `e2e/${name}`)

if (specs.length === 0) {
  console.error('No deterministic V2 migration browser specs found.')
  process.exit(1)
}

console.log(`Running ${specs.length} deterministic V2 migration browser specs`)
const result = spawnSync('pnpm', ['exec', 'playwright', 'test', ...specs], {
  shell: process.platform === 'win32',
  stdio: 'inherit',
})

process.exit(result.status ?? 1)
