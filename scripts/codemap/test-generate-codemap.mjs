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
  writeFileSync(htmlPath, "<script>\nconst PACKED='';\nconst colors={};\n</script>\n")
  writeFileSync(join(fixture, 'docs', 'codemap', 'codemap.lock'), `${JSON.stringify({
    excluded_directories: ['docs/codemap'],
    modules: [],
  }, null, 2)}\n`)
  git('add', '.')
  git('commit', '-qm', 'fixture')

  const generated = run()
  if (generated.status !== 0) throw new Error(generated.stderr || generated.stdout)
  const verifiedDirtySnapshot = run('--verify')
  if (verifiedDirtySnapshot.status !== 0) throw new Error(verifiedDirtySnapshot.stderr || verifiedDirtySnapshot.stdout)

  git('add', '.')
  git('commit', '-qm', 'bind codemap')
  const verifiedAfterCommit = run('--verify')
  if (verifiedAfterCommit.status !== 0) throw new Error('codemap must remain bound after its generated files are committed')
  writeFileSync(join(fixture, 'app.txt'), 'one\r\n')
  const verifiedAlternateEol = run('--verify')
  if (verifiedAlternateEol.status !== 0) throw new Error('equivalent Git-normalized line endings were rejected')

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

  console.log('codemap generator self-test passed')
} finally {
  rmSync(fixture, { recursive: true, force: true })
}
