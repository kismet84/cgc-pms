import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { gunzipSync, gzipSync } from 'node:zlib'

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const jsonPath = `${root}/docs/codemap/codemap.json`
const htmlPath = `${root}/docs/codemap/codemap.html`
const lockPath = `${root}/docs/codemap/codemap.lock`
const previous = JSON.parse(readFileSync(lockPath, 'utf8'))
const generatedAt = new Date().toISOString()
const head = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8', cwd: root }).trim()
const verifyOnly = process.argv.includes('--verify')

const excluded = new Set(previous.excluded_directories.map(path => path.replaceAll('\\', '/')))
const modules = [
  ['agent_platform', ['.agents/', '.codex/', '.codex-autopilot/', 'plugins/']],
  ['backend', ['backend/']],
  ['delivery_tooling', ['.github/', '.githooks/', 'deploy/', 'scripts/', 'tools/']],
  ['desktop_launcher', ['desktop-launcher/']],
  ['documentation', ['docs/']],
  ['frontend_v2', ['frontend-admin-v2/']],
  ['mobile', ['mobile/']],
  ['repository_meta', ['']],
  ['shared_packages', ['packages/']],
]
const allFiles = execFileSync('git', ['ls-files', '-c', '-o', '--exclude-standard', '-z'], { cwd: root })
  .toString('utf8').split('\0').filter(Boolean).filter(path => existsSync(`${root}/${path}`)).sort()
const isExcluded = path => [...excluded].some(dir => path === dir || path.startsWith(`${dir}/`) || path.includes(`/${dir}/`))
const included = allFiles.filter(path => !isExcluded(path))
const objectIds = included.length
  ? execFileSync('git', ['hash-object', '--stdin-paths'], {
      cwd: root,
      encoding: 'utf8',
      input: `${included.join('\n')}\n`,
    }).trim().split(/\r?\n/)
  : []
if (objectIds.length !== included.length) throw new Error('codemap content hash count mismatch')
const records = new Map(included.map((path, index) => {
  const pathBytes = Buffer.from(path)
  const objectId = Buffer.from(objectIds[index])
  return [path, Buffer.concat([Buffer.from(`P:${pathBytes.length}:`), pathBytes, Buffer.from(`O:${objectId.length}:`), objectId, Buffer.from('\n')])]
}))
const fingerprint = paths => {
  const hash = createHash('sha256')
  for (const path of paths.sort()) hash.update(records.get(path))
  return hash.digest('hex')
}
const matches = (path, prefixes) => prefixes[0] === '' ? !path.includes('/') : prefixes.some(prefix => path.startsWith(prefix))
const moduleRows = modules.map(([id, prefixes]) => {
  const paths = included.filter(path => matches(path, prefixes))
  const priorScope = previous.modules.find(module => module.id === id)?.scope
  return { id, scope: priorScope ?? prefixes.map(prefix => `${prefix}**`), file_count: paths.length, fingerprint: fingerprint(paths) }
})
const oldById = new Map(previous.modules.map(module => [module.id, module.fingerprint]))
const staleModules = moduleRows.filter(module => oldById.get(module.id) !== module.fingerprint).map(module => module.id)

if (verifyOnly) {
  const mismatches = moduleRows.flatMap(module => {
    const locked = previous.modules.find(candidate => candidate.id === module.id)
    return !locked || locked.file_count !== module.file_count || locked.fingerprint !== module.fingerprint ? [module.id] : []
  })
  const countMismatch = previous.repository_file_count !== allFiles.length ||
    previous.included_file_count !== included.length || previous.excluded_file_count !== allFiles.length - included.length
  const json = readFileSync(jsonPath, 'utf8')
  const html = readFileSync(htmlPath, 'utf8')
  const assignments = [...html.matchAll(/const PACKED='([^']*)';/g)]
  const activeAssignments = [...html.matchAll(/<script>\r?\nconst PACKED='([^']*)';\r?\nconst colors=/g)]
  let htmlBound = false
  if (assignments.length === 1 && activeAssignments.length === 1) {
    try {
      const encoded = activeAssignments[0][1]
      const packed = Buffer.from(encoded, 'base64')
      htmlBound = packed.toString('base64') === encoded &&
        gunzipSync(packed, { maxOutputLength: Buffer.byteLength(json) + 1 }).equals(Buffer.from(json))
    } catch {}
  }
  const map = JSON.parse(json)
  const metadataBound = map.generated_at === previous.generation_time && map.generated_from_commit === previous.current_commit
  if (mismatches.length || countMismatch || !htmlBound || !metadataBound) {
    throw new Error(`codemap snapshot is stale: modules=${mismatches.join(',') || 'none'}, counts=${countMismatch}, html=${htmlBound}, metadata=${metadataBound}`)
  }
  console.log(`codemap snapshot verified for ${head}`)
  process.exit(0)
}

const map = JSON.parse(readFileSync(jsonPath, 'utf8'))
map.generated_at = generatedAt
map.generated_from_commit = head
const json = `${JSON.stringify(map, null, 2)}\n`
writeFileSync(jsonPath, json)

const packed = gzipSync(Buffer.from(json)).toString('base64')
const htmlTemplate = readFileSync(htmlPath, 'utf8')
if ([...htmlTemplate.matchAll(/const PACKED='[^']*';/g)].length !== 1) throw new Error('codemap HTML must contain exactly one PACKED assignment')
const html = htmlTemplate.replace(/const PACKED='[^']*';/, `const PACKED='${packed}';`)
writeFileSync(htmlPath, html)

const nonCodemapStatus = execFileSync(
  'git', ['status', '--porcelain', '--', '.', ':(exclude)docs/codemap/**'], { encoding: 'utf8', cwd: root }
).trim()
const lock = {
  ...previous,
  current_commit: head,
  working_tree_dirty: nonCodemapStatus.length > 0,
  generation_time: generatedAt,
  tracked_file_count: Number(execFileSync('git', ['ls-files'], { encoding: 'utf8', cwd: root }).trim().split(/\r?\n/).filter(Boolean).length),
  repository_file_count: allFiles.length,
  included_file_count: included.length,
  excluded_file_count: allFiles.length - included.length,
  comparison: {
    previous_lock_found: true,
    stale_modules: staleModules,
    reason: staleModules.length ? 'Current tracked and untracked repository snapshot regenerated before module changes.' : 'Current snapshot matches previous code map inputs.',
  },
  modules: moduleRows,
}
writeFileSync(lockPath, `${JSON.stringify(lock, null, 2)}\n`)
console.log(`codemap regenerated for ${head}: ${staleModules.join(', ') || 'no stale modules'}`)
