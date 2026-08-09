import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('./check-readme-sync.mjs', import.meta.url));

function git(cwd, ...args) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8', shell: false, windowsHide: true });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.trim();
}

function put(cwd, relativePath, text) {
  const target = path.join(cwd, relativePath);
  mkdirSync(path.dirname(target), { recursive: true });
  writeFileSync(target, text);
}

function fixture() {
  const cwd = mkdtempSync(path.join(tmpdir(), 'readme-sync-'));
  git(cwd, 'init', '-q');
  git(cwd, 'config', 'user.email', 'readme-sync@example.invalid');
  git(cwd, 'config', 'user.name', 'README Sync Test');
  put(cwd, 'README.md', '# Repo\n');
  put(cwd, 'backend/app.txt', 'v1\n');
  put(cwd, 'desktop-launcher/README.md', '# Desktop\n');
  put(cwd, 'desktop-launcher/src/app.txt', 'v1\n');
  put(cwd, 'docs/manuals/README.md', '# Manuals\n');
  put(cwd, 'docs/manuals/codex-long-task-gate.md', '# Long task\n');
  put(cwd, '.agents/skills/long-task-gate/scripts/gate.mjs', 'export {};\n');
  git(cwd, 'add', '.');
  git(cwd, 'commit', '-qm', 'baseline');
  return cwd;
}

function staged(cwd) {
  return spawnSync(process.execPath, [script, '--staged'], { cwd, encoding: 'utf8', shell: false, windowsHide: true });
}

function prePush(cwd, input) {
  return spawnSync(process.execPath, [script, '--pre-push', 'origin'], {
    cwd, encoding: 'utf8', input, shell: false, windowsHide: true,
  });
}

test('staged module change requires nearest README', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  put(cwd, 'desktop-launcher/src/app.txt', 'v2\n');
  git(cwd, 'add', 'desktop-launcher/src/app.txt');
  const result = staged(cwd);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /desktop-launcher\/README\.md/);
});

test('staged module change passes with README update', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  put(cwd, 'desktop-launcher/src/app.txt', 'v2\n');
  put(cwd, 'desktop-launcher/README.md', '# Desktop v2\n');
  git(cwd, 'add', 'desktop-launcher');
  assert.equal(staged(cwd).status, 0);
});

test('path without module README does not force root README churn', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  put(cwd, 'backend/app.txt', 'v2\n');
  git(cwd, 'add', 'backend/app.txt');
  assert.equal(staged(cwd).status, 0);
});

test('long-task implementation requires manual and manual index', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  put(cwd, '.agents/skills/long-task-gate/scripts/gate.mjs', 'export const version = 2;\n');
  put(cwd, 'docs/manuals/codex-long-task-gate.md', '# Long task v2\n');
  git(cwd, 'add', '.agents', 'docs/manuals/codex-long-task-gate.md');
  const result = staged(cwd);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /docs\/manuals\/README\.md/);
});

test('pre-push checks outgoing commit range', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  const base = git(cwd, 'rev-parse', 'HEAD');
  put(cwd, 'desktop-launcher/src/app.txt', 'v2\n');
  git(cwd, 'add', 'desktop-launcher/src/app.txt');
  git(cwd, 'commit', '-qm', 'desktop change');
  const tip = git(cwd, 'rev-parse', 'HEAD');
  const input = `refs/heads/topic ${tip} refs/heads/topic ${base}\n`;
  const result = prePush(cwd, input);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /desktop-launcher\/README\.md/);
});

test('staged README deletion cannot hide its module requirement', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  put(cwd, 'desktop-launcher/src/app.txt', 'v2\n');
  git(cwd, 'rm', '-q', 'desktop-launcher/README.md');
  git(cwd, 'add', 'desktop-launcher/src/app.txt');
  const result = staged(cwd);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /desktop-launcher\/README\.md/);
});

test('renaming README to a non-README name cannot bypass the gate', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  git(cwd, 'mv', 'desktop-launcher/README.md', 'desktop-launcher/GUIDE.md');
  put(cwd, 'desktop-launcher/src/app.txt', 'v2\n');
  git(cwd, 'add', 'desktop-launcher');
  assert.equal(staged(cwd).status, 1);
});

test('renaming README to another README format satisfies the gate', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  git(cwd, 'mv', 'desktop-launcher/README.md', 'desktop-launcher/README.adoc');
  put(cwd, 'desktop-launcher/src/app.txt', 'v2\n');
  git(cwd, 'add', 'desktop-launcher');
  assert.equal(staged(cwd).status, 0);
});

test('pre-push passes when outgoing range includes README update', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  const base = git(cwd, 'rev-parse', 'HEAD');
  put(cwd, 'desktop-launcher/src/app.txt', 'v2\n');
  put(cwd, 'desktop-launcher/README.md', '# Desktop v2\n');
  git(cwd, 'add', 'desktop-launcher');
  git(cwd, 'commit', '-qm', 'desktop docs');
  const tip = git(cwd, 'rev-parse', 'HEAD');
  const input = `refs/heads/topic ${tip} refs/heads/topic ${base}\n`;
  assert.equal(prePush(cwd, input).status, 0);
});

test('new remote branch uses deterministic base fallback', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  const tip = git(cwd, 'rev-parse', 'HEAD');
  const zero = '0'.repeat(40);
  const input = `refs/heads/topic ${tip} refs/heads/topic ${zero}\n`;
  assert.equal(prePush(cwd, input).status, 0);
});

test('explicit CI range rejects missing README update', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  const base = git(cwd, 'rev-parse', 'HEAD');
  put(cwd, 'desktop-launcher/src/app.txt', 'v2\n');
  git(cwd, 'add', 'desktop-launcher/src/app.txt');
  git(cwd, 'commit', '-qm', 'desktop change');
  const tip = git(cwd, 'rev-parse', 'HEAD');
  const result = spawnSync(process.execPath, [script, '--range', base, tip], {
    cwd, encoding: 'utf8', shell: false, windowsHide: true,
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /desktop-launcher\/README\.md/);
});

test('root README deletion is rejected without forcing root churn for other paths', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  git(cwd, 'rm', '-q', 'README.md');
  const result = staged(cwd);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /README\.md/);
});

test('root README may be replaced by another README format', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  git(cwd, 'mv', 'README.md', 'README.adoc');
  assert.equal(staged(cwd).status, 0);
});

test('removing an entire module does not require its deleted README', (t) => {
  const cwd = fixture();
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  git(cwd, 'rm', '-qr', 'desktop-launcher');
  assert.equal(staged(cwd).status, 0);
});
