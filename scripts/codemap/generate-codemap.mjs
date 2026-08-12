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
const generationBaseCommit = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8', cwd: root }).trim()
const verifyOnly = process.argv.includes('--verify')
const previousGenerationBaseCommit = previous.generation_base_commit ?? previous.current_commit ?? null
const fingerprintAlgorithm = {
  name: 'sha256',
  version: 'repo-path-git-object-v3',
  input: 'git tracked and untracked non-ignored paths plus Git-normalized object IDs',
  path_order: 'ordinal case-sensitive',
  record_format: 'P:<utf8-path-byte-length>:<utf8-path>O:<git-object-id-byte-length>:<git-object-id><LF>',
  untracked_files: 'included unless ignored, out of scope, or excluded',
  self_reference: 'docs/codemap excluded',
}

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
const splitNullPaths = output => output.toString('utf8').split('\0').filter(Boolean)
const trackedDifferences = splitNullPaths(execFileSync('git', ['diff', '--name-only', '-z', 'HEAD', '--'], { cwd: root }))
const untrackedDifferences = splitNullPaths(execFileSync('git', ['ls-files', '--others', '--exclude-standard', '-z'], { cwd: root }))
const inputScopeDirty = [...trackedDifferences, ...untrackedDifferences].some(path => !isExcluded(path))
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
  const algorithmBound = JSON.stringify(previous.fingerprint_algorithm) === JSON.stringify(fingerprintAlgorithm)
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
  const lockedGenerationBaseCommit = previous.generation_base_commit ?? previous.current_commit
  const lockedInputScopeDirty = previous.input_scope_dirty ?? previous.working_tree_dirty
  const lockedComparison = previous.comparison_to_previous_generation ?? previous.comparison
  const mapGenerationBaseCommit = map.generation_base_commit ?? map.generated_from_commit
  const metadataBound = Boolean(
    map.generated_at === previous.generation_time &&
    mapGenerationBaseCommit === lockedGenerationBaseCommit &&
    typeof lockedGenerationBaseCommit === 'string' && lockedGenerationBaseCommit.length > 0 &&
    typeof lockedInputScopeDirty === 'boolean' &&
    lockedComparison && typeof lockedComparison === 'object'
  )
  if (mismatches.length || countMismatch || !algorithmBound || !htmlBound || !metadataBound) {
    throw new Error(`codemap snapshot is stale: modules=${mismatches.join(',') || 'none'}, counts=${countMismatch}, algorithm=${algorithmBound}, html=${htmlBound}, metadata=${metadataBound}`)
  }
  console.log(`codemap snapshot verified: generation_base_commit=${lockedGenerationBaseCommit}, input_scope_dirty=${lockedInputScopeDirty}`)
  process.exit(0)
}

const mapContent = JSON.parse(readFileSync(jsonPath, 'utf8'))
delete mapContent.generated_at
delete mapContent.generated_from_commit
delete mapContent.generation_base_commit
const map = {
  generated_at: generatedAt,
  generation_base_commit: generationBaseCommit,
  ...mapContent,
}
const json = `${JSON.stringify(map, null, 2)}\n`
writeFileSync(jsonPath, json)

const packed = gzipSync(Buffer.from(json)).toString('base64')
const htmlTemplate = readFileSync(htmlPath, 'utf8')
if ([...htmlTemplate.matchAll(/const PACKED='[^']*';/g)].length !== 1) throw new Error('codemap HTML must contain exactly one PACKED assignment')
const html = htmlTemplate.replace(/const PACKED='[^']*';/, `const PACKED='${packed}';`)
writeFileSync(htmlPath, html)

const lockContent = { ...previous }
delete lockContent.current_commit
delete lockContent.working_tree_dirty
delete lockContent.comparison
const lock = {
  ...lockContent,
  generation_base_commit: generationBaseCommit,
  input_scope_dirty: inputScopeDirty,
  generation_time: generatedAt,
  fingerprint_algorithm: fingerprintAlgorithm,
  tracked_file_count: Number(execFileSync('git', ['ls-files'], { encoding: 'utf8', cwd: root }).trim().split(/\r?\n/).filter(Boolean).length),
  repository_file_count: allFiles.length,
  included_file_count: included.length,
  excluded_file_count: allFiles.length - included.length,
  comparison_to_previous_generation: {
    previous_lock_found: true,
    previous_generation_base_commit: previousGenerationBaseCommit,
    stale_modules: staleModules,
    reason: staleModules.length ? 'Generation inputs changed since the previous code map snapshot.' : 'Generation inputs match the previous code map snapshot.',
  },
  modules: moduleRows,
}
writeFileSync(lockPath, `${JSON.stringify(lock, null, 2)}\n`)
console.log(`codemap regenerated: generation_base_commit=${generationBaseCommit}, input_scope_dirty=${inputScopeDirty}, changed_modules=${staleModules.join(',') || 'none'}`)
