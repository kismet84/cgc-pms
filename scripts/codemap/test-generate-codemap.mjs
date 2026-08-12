import { execFileSync, spawnSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

const fixture = mkdtempSync(join(tmpdir(), 'cgc-codemap-'))
const generator = join(import.meta.dirname, 'generate-codemap.mjs')
const run = (...args) => spawnSync(process.execPath, [generator, ...args], { cwd: fixture, encoding: 'utf8' })
const git = (...args) => execFileSync('git', args, { cwd: fixture, stdio: 'ignore' })

try {
  git('init', '-q', '-b', 'master')
  git('config', 'user.email', 'codemap-test@example.invalid')
  git('config', 'user.name', 'Codemap Test')
  mkdirSync(join(fixture, 'docs', 'codemap'), { recursive: true })
  writeFileSync(join(fixture, '.gitattributes'), '*.txt text eol=lf\n')
  writeFileSync(join(fixture, 'app.txt'), 'one\n')
  writeFileSync(join(fixture, 'docs', 'codemap', 'codemap.json'), '{}\n')
  const htmlPath = join(fixture, 'docs', 'codemap', 'codemap.html')
  const lockPath = join(fixture, 'docs', 'codemap', 'codemap.lock')
  writeFileSync(htmlPath, "<script>\nconst PACKED='';\nconst colors={};\n</script>\n")
  writeFileSync(lockPath, `${JSON.stringify({
    excluded_directories: ['docs/codemap'],
    modules: [],
  }, null, 2)}\n`)
  git('add', '.')
  git('commit', '-qm', 'fixture')

  const generated = run()
  if (generated.status !== 0) throw new Error(generated.stderr || generated.stdout)
  const mapPath = join(fixture, 'docs', 'codemap', 'codemap.json')
  const generatedMap = JSON.parse(readFileSync(mapPath, 'utf8'))
  const generatedLock = JSON.parse(readFileSync(lockPath, 'utf8'))

  const legacyMap = { ...generatedMap, generated_from_commit: generatedMap.generation_base_commit }
  delete legacyMap.generation_base_commit
  const legacyLock = {
    ...generatedLock,
    current_commit: generatedLock.generation_base_commit,
    working_tree_dirty: generatedLock.input_scope_dirty,
    comparison: generatedLock.comparison_to_previous_generation,
  }
  delete legacyLock.generation_base_commit
  delete legacyLock.input_scope_dirty
  delete legacyLock.comparison_to_previous_generation
  const legacyJson = `${JSON.stringify(legacyMap, null, 2)}\n`
  writeFileSync(mapPath, legacyJson)
  writeFileSync(lockPath, `${JSON.stringify(legacyLock, null, 2)}\n`)
  const generatedHtml = readFileSync(htmlPath, 'utf8')
  writeFileSync(htmlPath, generatedHtml.replace(
    /const PACKED='[^']*';/,
    `const PACKED='${gzipSync(Buffer.from(legacyJson)).toString('base64')}';`,
  ))
  const verifiedLegacySnapshot = run('--verify')
  if (verifiedLegacySnapshot.status !== 0) {
    throw new Error('legacy codemap metadata fallback must verify before migration')
  }
  const migrated = run()
  if (migrated.status !== 0) throw new Error(migrated.stderr || migrated.stdout)
  const migratedMap = JSON.parse(readFileSync(mapPath, 'utf8'))
  const migratedLock = JSON.parse(readFileSync(lockPath, 'utf8'))
  if (!migratedMap.generation_base_commit || migratedMap.generated_from_commit) {
    throw new Error('codemap JSON must expose generation_base_commit instead of ambiguous generated_from_commit')
  }
  if (migratedLock.generation_base_commit !== migratedMap.generation_base_commit ||
      typeof migratedLock.input_scope_dirty !== 'boolean' ||
      !migratedLock.comparison_to_previous_generation) {
    throw new Error('codemap lock lacks explicit generation input semantics')
  }
  const verifiedDirtySnapshot = run('--verify')
  if (verifiedDirtySnapshot.status !== 0) throw new Error(verifiedDirtySnapshot.stderr || verifiedDirtySnapshot.stdout)

  git('add', '.')
  git('commit', '-qm', 'bind codemap')
  const verifiedAfterCommit = run('--verify')
  if (verifiedAfterCommit.status !== 0) throw new Error('codemap must remain bound after its generated files are committed')
  const committedHead = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: fixture, encoding: 'utf8' }).trim()
  if (!verifiedAfterCommit.stdout.includes(migratedLock.generation_base_commit) ||
      verifiedAfterCommit.stdout.includes(`verified for ${committedHead}`)) {
    throw new Error('verify output must identify generation inputs, not impersonate the current HEAD')
  }
  writeFileSync(join(fixture, 'app.txt'), 'one\r\n')
  const verifiedAlternateEol = run('--verify')
  if (verifiedAlternateEol.status !== 0) throw new Error('equivalent Git-normalized line endings were rejected')
  const boundLockText = readFileSync(lockPath, 'utf8')
  const boundLock = JSON.parse(boundLockText)
  if (boundLock.fingerprint_algorithm?.version !== 'repo-path-git-object-v3' || !boundLock.fingerprint_algorithm.record_format.includes('O:<git-object-id-byte-length>')) {
    throw new Error('Git-normalized fingerprint metadata is missing')
  }
  writeFileSync(lockPath, `${JSON.stringify({ ...boundLock, fingerprint_algorithm: { ...boundLock.fingerprint_algorithm, version: 'corrupt' } }, null, 2)}\n`)
  if (run('--verify').status === 0) throw new Error('mismatched fingerprint metadata was accepted')
  writeFileSync(lockPath, boundLockText)

  const boundHtml = readFileSync(htmlPath, 'utf8')
  const activeMatch = boundHtml.match(/const PACKED='([^']*)';/)
  const activeAssignment = activeMatch?.[0]
  if (!activeAssignment || !activeMatch[1]) throw new Error('generated PACKED assignment is missing')
  const alternatePacked = gzipSync(readFileSync(join(fixture, 'docs', 'codemap', 'codemap.json')), { level: 0 }).toString('base64')
  if (alternatePacked === activeMatch[1]) throw new Error('alternate gzip fixture must use different bytes')
  writeFileSync(htmlPath, boundHtml.replace(activeAssignment, `const PACKED='${alternatePacked}';`))
  if (run('--verify').status !== 0) throw new Error('equivalent gzip encoding was rejected')
  const invalidBase64 = `${activeMatch[1].slice(0, 16)}!${activeMatch[1].slice(16)}`
  writeFileSync(htmlPath, boundHtml.replace(activeAssignment, `const PACKED='${invalidBase64}';`))
  if (run('--verify').status === 0) throw new Error('non-canonical base64 was accepted')

  writeFileSync(htmlPath, boundHtml.replace(activeAssignment, `const PACKED='corrupt';\n// ${activeAssignment}`))
  if (run('--verify').status === 0) throw new Error('comment decoy hid a corrupt active PACKED assignment')
  writeFileSync(htmlPath, boundHtml.replace(activeAssignment, `${activeAssignment}\n${activeAssignment}`))
  if (run('--verify').status === 0) throw new Error('duplicate PACKED assignments were accepted')
  writeFileSync(htmlPath, boundHtml.replace(activeAssignment, "const PACKED='corrupt';"))
  if (run('--verify').status === 0) throw new Error('corrupt active PACKED assignment was accepted')
  writeFileSync(htmlPath, boundHtml)

  writeFileSync(join(fixture, 'app.txt'), 'two\n')
  if (run('--verify').status === 0) throw new Error('stale codemap snapshot was accepted')
  if (run().status !== 0 || run('--verify').status !== 0) throw new Error('regenerated codemap snapshot did not verify')
  const dirtyLock = JSON.parse(readFileSync(lockPath, 'utf8'))
  if (dirtyLock.input_scope_dirty !== true || !dirtyLock.comparison_to_previous_generation) {
    throw new Error('dirty input scope and previous-generation comparison were not recorded')
  }

  console.log('codemap generator self-test passed')
} finally {
  rmSync(fixture, { recursive: true, force: true })
}
