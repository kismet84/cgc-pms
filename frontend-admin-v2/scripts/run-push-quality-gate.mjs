import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'

const frontendRoot = resolve(process.cwd())
const repositoryRoot = resolve(frontendRoot, '..')

function gitNames(args) {
  const result = spawnSync('git', args, { cwd: repositoryRoot, encoding: 'utf8' })
  return result.status === 0
    ? result.stdout
        .split(/\r?\n/)
        .map((name) => name.trim().replaceAll('\\', '/'))
        .filter(Boolean)
    : []
}

const changedFiles = new Set([
  ...gitNames(['diff', '--name-only', 'origin/master...HEAD']),
  ...gitNames(['diff', '--name-only']),
  ...gitNames(['diff', '--cached', '--name-only']),
  ...gitNames(['ls-files', '--others', '--exclude-standard']),
])

const affected = [...changedFiles].filter(
  (file) =>
    file === 'frontend-admin-v2/package.json' ||
    file === 'pnpm-lock.yaml' ||
    file === '.github/workflows/ci.yml' ||
    file === 'scripts/ci/test-workflow-contract.ps1' ||
    file.startsWith('frontend-admin-v2/src/components/') ||
    file.startsWith('frontend-admin-v2/src/styles/') ||
    file.startsWith('frontend-admin-v2/src/layouts/') ||
    file.startsWith('frontend-admin-v2/src/navigation/') ||
    file.startsWith('frontend-admin-v2/src/services/') ||
    file.startsWith('frontend-admin-v2/src/stores/') ||
    file.startsWith('frontend-admin-v2/tests/') ||
    file.startsWith('frontend-admin-v2/e2e/') ||
    file.startsWith('frontend-admin-v2/scripts/') ||
    file.startsWith('packages/frontend-contracts/'),
)

if (affected.length === 0) {
  console.log(
    'pre-push quality gate: no shared component/API mock/frontend contract changes; skipped',
  )
  process.exit(0)
}

console.log('pre-push quality gate: affected files')
for (const file of affected.sort()) console.log(`- ${file}`)

function run(command, args, cwd = repositoryRoot) {
  console.log(`\n$ ${command} ${args.join(' ')}`)
  const result = spawnSync(command, args, {
    cwd,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  })
  if (result.status !== 0) process.exit(result.status ?? 1)
}

run('git', ['diff', '--check'])
for (const script of [
  'lint:check',
  'type-check',
  'test:unit',
  'build',
  'test:e2e:migration-gate',
]) {
  run('pnpm', [script], frontendRoot)
}

console.log('\npre-push quality gate: passed')
