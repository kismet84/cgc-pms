import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, lstatSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

const frontendRoot = resolve(process.cwd())
const repositoryRoot = resolve(frontendRoot, '..')
const fullGateScript = 'test:e2e:migration-gate'
const pnpmCli = process.env.npm_execpath

function resolveCommand(command, args) {
  if (command !== 'pnpm') return { executable: command, args }
  if (!pnpmCli) throw new Error('pre-push quality gate must run through pnpm check:pre-push')
  return { executable: process.execPath, args: [pnpmCli, ...args] }
}

function captureRaw(command, args, cwd = repositoryRoot) {
  const invocation = resolveCommand(command, args)
  const result = spawnSync(invocation.executable, invocation.args, {
    cwd,
    encoding: 'utf8',
  })
  if (result.status !== 0) {
    const detail = result.stderr?.trim() || result.error?.message || `exit ${result.status}`
    throw new Error(`${command} ${args.join(' ')} failed: ${detail}`)
  }
  return result.stdout
}

const capture = (command, args, cwd = repositoryRoot) => captureRaw(command, args, cwd).trim()

function gitNames(args) {
  return capture('git', args)
    .split(/\r?\n/)
    .map((name) => name.trim().replaceAll('\\', '/'))
    .filter(Boolean)
}

function gitRefExists(ref) {
  const result = spawnSync('git', ['rev-parse', '--verify', '--quiet', ref], {
    cwd: repositoryRoot,
  })
  return result.status === 0
}

const baseRef = ['origin/master', 'origin/main'].find(gitRefExists)
if (!baseRef) throw new Error('pre-push quality gate requires origin/master or origin/main')

const untrackedFiles = gitNames(['ls-files', '--others', '--exclude-standard'])
const changedFiles = new Set([
  ...gitNames(['diff', '--name-only', `${baseRef}...HEAD`]),
  ...gitNames(['diff', '--name-only']),
  ...gitNames(['diff', '--cached', '--name-only']),
  ...untrackedFiles,
])

const dependencyFiles = new Set([
  'frontend-admin-v2/package.json',
  'frontend-admin-v2/pnpm-lock.yaml',
  'frontend-admin-v2/pnpm-workspace.yaml',
  'pnpm-lock.yaml',
  'packages/frontend-contracts/package.json',
])

function affectsFrontendGate(file) {
  return (
    dependencyFiles.has(file) ||
    file.startsWith('frontend-admin-v2/') ||
    file.startsWith('patches/') ||
    file === '.githooks/pre-push' ||
    file === 'scripts/ci/test-workflow-contract.ps1' ||
    file.startsWith('.github/actions/') ||
    file.startsWith('.github/workflows/') ||
    file.startsWith('packages/frontend-contracts/')
  )
}

const affected = [...changedFiles].filter(affectsFrontendGate).sort()

if (affected.length === 0) {
  console.log('pre-push quality gate: no frontend changes; skipped')
  process.exit(0)
}

const isUnitTest = (file) => /^frontend-admin-v2\/tests\/unit\/.*\.test\.[cm]?[jt]s$/.test(file)
const isE2eSpec = (file) => /^frontend-admin-v2\/e2e\/[a-zA-Z0-9._-]+\.spec\.ts$/.test(file)
const isPage = (file) => file.startsWith('frontend-admin-v2/src/pages/')

const pageE2eSpecs = new Map([
  ['account', ['e2e/m7-account.spec.ts']],
  ['auth', ['e2e/auth.spec.ts']],
  [
    'commercial',
    [
      'e2e/engineering-tender.spec.ts',
      'e2e/m4-budget-measurement.spec.ts',
      'e2e/m4-contracts.spec.ts',
      'e2e/m4-cost-target.spec.ts',
      'e2e/m4-costs.spec.ts',
      'e2e/m4-variation-bid.spec.ts',
    ],
  ],
  ['dashboard', ['e2e/m1-shell.spec.ts']],
  [
    'delivery',
    [
      'e2e/m3-closeout.spec.ts',
      'e2e/m3-delivery.spec.ts',
      'e2e/m3-quality-safety.spec.ts',
      'e2e/m3-technical.spec.ts',
    ],
  ],
  ['errors', ['e2e/m1-shell.spec.ts']],
  ['finance', ['e2e/m6-finance-control.spec.ts', 'e2e/m6-payment-revenue-invoice.spec.ts']],
  ['master-data', ['e2e/m7-cost-subject.spec.ts', 'e2e/m7-master-data.spec.ts']],
  ['projects', ['e2e/m3-projects.spec.ts']],
  ['settlement', ['e2e/m6-settlement.spec.ts']],
  ['shell', ['e2e/m1-shell.spec.ts']],
  ['subcontract', ['e2e/m6-subcontract-workspace.spec.ts']],
  [
    'supply-chain',
    [
      'e2e/m5-inventory-ledger.spec.ts',
      'e2e/m5-purchase-receipt.spec.ts',
      'e2e/m5-requisition-return.spec.ts',
      'e2e/m5-supplier-sourcing.spec.ts',
    ],
  ],
  ['system', ['e2e/m7-system.spec.ts', 'e2e/m7-workflow-process.spec.ts']],
  ['workbench', ['e2e/m4-1-approval-workbench.spec.ts']],
])

function relatedSpecs(files) {
  const specs = new Set()
  for (const file of files) {
    if (file === 'frontend-admin-v2/src/pages/HealthPage.vue') {
      specs.add('e2e/health.spec.ts')
      continue
    }
    const area = file.split('/')[3]
    const mapped = pageE2eSpecs.get(area)
    if (!mapped) return null
    for (const spec of mapped) specs.add(spec)
  }
  return [...specs].sort()
}

const unitTests = affected.filter(isUnitTest)
const changedE2eSpecs = affected
  .filter(isE2eSpec)
  .map((file) => file.replace('frontend-admin-v2/', ''))
const pages = affected.filter(isPage)
const fullGateFiles = affected.filter(
  (file) => !isUnitTest(file) && !isE2eSpec(file) && !isPage(file),
)
const mappedPageSpecs = relatedSpecs(pages)
const fullGate = fullGateFiles.length > 0 || mappedPageSpecs === null
const selectedE2eSpecs = [...new Set([...(mappedPageSpecs ?? []), ...changedE2eSpecs])].sort()
const dependencyChanged = affected.some(
  (file) =>
    dependencyFiles.has(file) ||
    file.startsWith('frontend-admin-v2/patches/') ||
    file.startsWith('patches/'),
)

const tier = fullGate ? 'full' : pages.length > 0 ? 'local-feature' : 'tests-only'
console.log(`pre-push quality gate: tier=${tier}`)
for (const file of affected) console.log(`- ${file}`)

const workers = process.env.PLAYWRIGHT_MIGRATION_WORKERS || (process.env.CI ? '2' : '4')
const gates = []
if (dependencyChanged) {
  gates.push({
    id: 'dependency-audit',
    command: 'pnpm',
    args: [
      '--dir',
      'frontend-admin-v2',
      'audit',
      '--audit-level',
      'high',
      '--registry=https://registry.npmjs.org',
    ],
    cwd: repositoryRoot,
  })
}
gates.push({
  id: 'diff-check:branch',
  command: 'git',
  args: ['diff', '--check', `${baseRef}...HEAD`],
  cwd: repositoryRoot,
})
gates.push({
  id: 'diff-check:staged',
  command: 'git',
  args: ['diff', '--check', '--cached'],
  cwd: repositoryRoot,
})
gates.push({
  id: 'diff-check:unstaged',
  command: 'git',
  args: ['diff', '--check'],
  cwd: repositoryRoot,
})
gates.push({ id: 'lint', command: 'pnpm', args: ['lint:check'], cwd: frontendRoot })

if (fullGate || pages.length > 0) {
  gates.push({ id: 'build', command: 'pnpm', args: ['build'], cwd: frontendRoot })
  gates.push({ id: 'unit', command: 'pnpm', args: ['test:unit'], cwd: frontendRoot })
} else if (unitTests.length > 0) {
  gates.push({ id: 'unit', command: 'pnpm', args: ['test:unit'], cwd: frontendRoot })
}

if (fullGate) {
  gates.push({ id: 'e2e:full', command: 'pnpm', args: [fullGateScript], cwd: frontendRoot })
} else if (selectedE2eSpecs.length > 0) {
  gates.push({
    id: `e2e:${selectedE2eSpecs.join(',')}`,
    command: 'pnpm',
    args: ['exec', 'playwright', 'test', '--workers', workers, ...selectedE2eSpecs],
    cwd: frontendRoot,
  })
}

const hash = (value) => createHash('sha256').update(value).digest('hex')
const forbiddenPath = (file) =>
  /(^|\/)(\.omc|\.omo|\.opencode|\.claude|\.mimocode|graphify-out|\.sisyphus|\.archive)(\/|$)/.test(
    file,
  ) || file.startsWith('archive/v1.0/private/')

function hashUntrackedFiles(files) {
  const digest = createHash('sha256')
  for (const file of files.sort()) {
    const path = resolve(repositoryRoot, file)
    const stat = lstatSync(path)
    if (!stat.isFile()) throw new Error(`cannot fingerprint non-regular file: ${file}`)
    const pathBytes = Buffer.from(file)
    const content = readFileSync(path)
    digest.update(`P:${pathBytes.length}:`)
    digest.update(pathBytes)
    digest.update(`C:${content.length}:`)
    digest.update(content)
    digest.update('\n')
  }
  return digest.digest('hex')
}

function createFingerprint() {
  const pnpmVersion = capture('pnpm', ['--version'])
  const lockfile = resolve(frontendRoot, 'pnpm-lock.yaml')
  const gateEnvironment = Object.fromEntries(
    Object.entries(process.env)
      .filter(
        ([name]) =>
          ['CI', 'NODE_ENV', 'NODE_OPTIONS', 'PWDEBUG'].includes(name) ||
          name.startsWith('PLAYWRIGHT_') ||
          name.startsWith('VITE_'),
      )
      .sort(([left], [right]) => left.localeCompare(right)),
  )
  const inputs = {
    schema: 1,
    headSha: capture('git', ['rev-parse', 'HEAD']),
    baseRef,
    baseSha: capture('git', ['rev-parse', baseRef]),
    branchDiff: hash(captureRaw('git', ['diff', '--binary', `${baseRef}...HEAD`])),
    uncommittedDiff: hash(captureRaw('git', ['diff', '--binary', 'HEAD'])),
    untrackedFiles: hashUntrackedFiles(untrackedFiles),
    lockfile: existsSync(lockfile) ? hash(readFileSync(lockfile)) : 'missing',
    nodeVersion: process.version,
    pnpmVersion,
    platform: `${process.platform}-${process.arch}`,
    gateEnvironment: hash(JSON.stringify(gateEnvironment)),
    gatePlan: gates.map(({ id, command, args }) => ({ id, command, args })),
    gateScript: hash(readFileSync(new URL(import.meta.url))),
  }
  return { inputs, fingerprint: hash(JSON.stringify(inputs)) }
}

const hasForbiddenChanges = [...changedFiles].some(forbiddenPath)
const untrackedFilesAreRegular =
  !hasForbiddenChanges &&
  untrackedFiles.every((file) => lstatSync(resolve(repositoryRoot, file)).isFile())
const cacheSafe = !hasForbiddenChanges && untrackedFilesAreRegular
let cachePath
let fingerprint
let fingerprintInputs

if (cacheSafe) {
  ;({ inputs: fingerprintInputs, fingerprint } = createFingerprint())
  cachePath = resolve(
    repositoryRoot,
    capture('git', ['rev-parse', '--git-path', 'codex/pre-push-quality-gate-v1.json']),
  )
  if (existsSync(cachePath)) {
    try {
      const cached = JSON.parse(readFileSync(cachePath, 'utf8'))
      if (
        cached.fingerprint === fingerprint &&
        gates.every(({ id }) => cached.gates?.[id] === true)
      ) {
        console.log('pre-push quality gate: identical successful fingerprint reused')
        process.exit(0)
      }
    } catch {
      // Corrupt local cache fails closed and is replaced after a successful run.
    }
  }
} else {
  console.log(
    'pre-push quality gate: cache disabled by excluded private state or non-regular untracked file',
  )
}

function run(command, args, cwd) {
  console.log(`\n$ ${command} ${args.join(' ')}`)
  const invocation = resolveCommand(command, args)
  const result = spawnSync(invocation.executable, invocation.args, {
    cwd,
    stdio: 'inherit',
  })
  if (result.status !== 0) process.exit(result.status ?? 1)
}

const results = {}
for (const gate of gates) {
  run(gate.command, gate.args, gate.cwd)
  results[gate.id] = true
}

if (cacheSafe) {
  try {
    mkdirSync(dirname(cachePath), { recursive: true })
    writeFileSync(
      cachePath,
      `${JSON.stringify(
        {
          schema: 1,
          fingerprint,
          inputs: fingerprintInputs,
          gates: results,
          passedAt: new Date().toISOString(),
        },
        null,
        2,
      )}\n`,
    )
  } catch (error) {
    console.warn(`pre-push quality gate: cache write skipped: ${error.message}`)
  }
}

console.log('\npre-push quality gate: passed')
