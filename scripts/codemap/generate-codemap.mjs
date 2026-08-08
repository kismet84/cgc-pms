import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { gzipSync } from 'node:zlib'

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const jsonPath = `${root}/docs/codemap/codemap.json`
const htmlPath = `${root}/docs/codemap/codemap.html`
const lockPath = `${root}/docs/codemap/codemap.lock`
const previous = JSON.parse(readFileSync(lockPath, 'utf8'))
const generatedAt = new Date().toISOString()
const head = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8', cwd: root }).trim()

const map = JSON.parse(readFileSync(jsonPath, 'utf8'))
map.generated_at = generatedAt
map.generated_from_commit = head
const json = `${JSON.stringify(map, null, 2)}\n`
writeFileSync(jsonPath, json)

const packed = gzipSync(Buffer.from(json)).toString('base64')
const html = readFileSync(htmlPath, 'utf8').replace(/const PACKED='[^']*';/, `const PACKED='${packed}';`)
writeFileSync(htmlPath, html)

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
  .toString('utf8').split('\0').filter(Boolean).sort()
const isExcluded = path => [...excluded].some(dir => path === dir || path.startsWith(`${dir}/`) || path.includes(`/${dir}/`))
const included = allFiles.filter(path => !isExcluded(path))
const records = new Map(included.map(path => {
  const pathBytes = Buffer.from(path)
  const content = readFileSync(`${root}/${path}`)
  return [path, Buffer.concat([Buffer.from(`P:${pathBytes.length}:`), pathBytes, Buffer.from(`C:${content.length}:`), content, Buffer.from('\n')])]
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
const lock = {
  ...previous,
  current_commit: head,
  working_tree_dirty: execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8', cwd: root }).trim().length > 0,
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
