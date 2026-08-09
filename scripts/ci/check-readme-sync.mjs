#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const ZERO_SHA = /^0+$/;
const ARCHIVE_PREFIXES = ['archive/', 'docs/archive/', 'docs/codemap/'];
const EXPLICIT_RULES = [
  {
    matches: (file) => file === '.codex/hooks.json'
      || (file.startsWith('.agents/skills/long-task-gate/') && !file.includes('/tests/')),
    documents: ['docs/manuals/codex-long-task-gate.md', 'docs/manuals/README.md'],
  },
  {
    matches: (file) => file.startsWith('.githooks/')
      || file === 'scripts/ci/check-readme-sync.mjs'
      || file === 'scripts/ci/check-readme-sync.test.mjs',
    documents: ['README.md'],
  },
];

function runGit(cwd, args, input) {
  const result = spawnSync('git', args, {
    cwd,
    encoding: 'utf8',
    input,
    shell: false,
    windowsHide: true,
  });
  if (result.error || result.status !== 0) {
    throw new Error((result.stderr || result.error?.message || `git exited ${result.status}`).trim());
  }
  return result.stdout;
}

function tryGit(cwd, args) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8', shell: false, windowsHide: true });
  return result.status === 0 ? result.stdout.trim() : '';
}

function nulPaths(text) {
  return text.split('\0').filter(Boolean).map((file) => file.replaceAll('\\', '/'));
}

function diffPaths(cwd, revisions, filter) {
  return nulPaths(runGit(cwd, [
    '-c', 'core.quotepath=false', 'diff', '--name-only', '-z', '--no-renames',
    `--diff-filter=${filter}`, ...revisions, '--',
  ]));
}

function mergeChanges(target, source) {
  for (const file of source.changed) target.changed.add(file);
  for (const file of source.deliverable) target.deliverable.add(file);
  for (const file of source.currentPaths) target.currentPaths.add(file);
  for (const file of source.currentReadmes) target.currentReadmes.add(file);
  for (const file of source.historicalReadmes) target.historicalReadmes.add(file);
}

function changedPaths(cwd, revisions) {
  return {
    changed: new Set(diffPaths(cwd, revisions, 'ACMRDTUXB')),
    deliverable: new Set(diffPaths(cwd, revisions, 'ACMRTUXB')),
  };
}

function isReadme(file) {
  return /(^|\/)README(?:\.[^/]+)?$/i.test(file);
}

function indexPaths(cwd) {
  return nulPaths(runGit(cwd, ['ls-files', '-z']));
}

function treePaths(cwd, revision) {
  const tree = tryGit(cwd, ['rev-parse', '--verify', `${revision}^{tree}`]);
  if (!tree) throw new Error(`README 基线不可解析：${revision}`);
  return nulPaths(runGit(cwd, ['ls-tree', '-r', '--name-only', '-z', tree]));
}

function rangeChanges(cwd, base, tip) {
  const currentPaths = treePaths(cwd, tip);
  return {
    ...changedPaths(cwd, [base, tip]),
    currentPaths: new Set(currentPaths),
    currentReadmes: new Set(currentPaths.filter(isReadme)),
    historicalReadmes: new Set(treePaths(cwd, base).filter(isReadme)),
  };
}

function stagedChanges(cwd) {
  const head = tryGit(cwd, ['rev-parse', '--verify', 'HEAD^{tree}']);
  const currentPaths = indexPaths(cwd);
  return {
    ...changedPaths(cwd, ['--cached']),
    currentPaths: new Set(currentPaths),
    currentReadmes: new Set(currentPaths.filter(isReadme)),
    historicalReadmes: head ? new Set(treePaths(cwd, head).filter(isReadme)) : new Set(),
  };
}

function emptyTree(cwd) {
  return runGit(cwd, ['hash-object', '-t', 'tree', '--stdin'], '').trim();
}

function pushBase(cwd, remoteName, localSha) {
  const refs = [];
  const remoteHead = tryGit(cwd, ['symbolic-ref', '--quiet', `refs/remotes/${remoteName}/HEAD`]);
  if (remoteHead) refs.push(remoteHead);
  refs.push(`refs/remotes/${remoteName}/master`, `refs/remotes/${remoteName}/main`);

  for (const ref of [...new Set(refs)]) {
    if (!tryGit(cwd, ['rev-parse', '--verify', `${ref}^{commit}`])) continue;
    const base = tryGit(cwd, ['merge-base', ref, localSha]);
    if (base) return base;
  }
  return emptyTree(cwd);
}

function prePushChanges(cwd, remoteName) {
  const input = readFileSync(0, 'utf8').trim();
  const result = {
    changed: new Set(), deliverable: new Set(), currentPaths: new Set(),
    currentReadmes: new Set(), historicalReadmes: new Set(),
  };
  if (!input) {
    const tip = runGit(cwd, ['rev-parse', 'HEAD']).trim();
    mergeChanges(result, rangeChanges(cwd, pushBase(cwd, remoteName, tip), tip));
    return result;
  }

  for (const line of input.split(/\r?\n/).filter(Boolean)) {
    const [, localSha, , remoteSha] = line.trim().split(/\s+/);
    if (!localSha || !remoteSha) throw new Error(`pre-push 输入无效：${line}`);
    if (ZERO_SHA.test(localSha)) continue;
    const base = ZERO_SHA.test(remoteSha) ? pushBase(cwd, remoteName, localSha) : remoteSha;
    mergeChanges(result, rangeChanges(cwd, base, localSha));
  }
  return result;
}

function nearestReadme(file, readmes) {
  let directory = path.posix.dirname(file);
  while (directory !== '.' && directory !== '/') {
    const candidates = [...readmes]
      .filter((readme) => path.posix.dirname(readme) === directory)
      .sort((left, right) => left.localeCompare(right));
    if (candidates.length) return candidates.find((candidate) => candidate.endsWith('/README.md')) || candidates[0];
    directory = path.posix.dirname(directory);
  }
  return null;
}

function requiredDocuments(changes) {
  const required = new Map();
  const add = (document, cause) => {
    if (!required.has(document)) required.set(document, new Set());
    required.get(document).add(cause);
  };

  for (const file of changes.changed) {
    if (ARCHIVE_PREFIXES.some((prefix) => file.startsWith(prefix))) continue;
    const rootReadme = isReadme(file) && path.posix.dirname(file) === '.'
      ? [...changes.currentReadmes, ...changes.historicalReadmes]
        .find((candidate) => path.posix.dirname(candidate) === '.')
      : null;
    const readme = rootReadme
      || nearestReadme(file, changes.currentReadmes)
      || nearestReadme(file, changes.historicalReadmes);
    if (readme) {
      const directory = path.posix.dirname(readme);
      const directoryStillExists = directory === '.'
        ? changes.currentPaths.size > 0
        : [...changes.currentPaths].some((candidate) => candidate.startsWith(`${directory}/`));
      if (changes.currentReadmes.has(readme) || directoryStillExists) add(readme, file);
    }
    for (const rule of EXPLICIT_RULES) {
      if (rule.matches(file)) for (const document of rule.documents) add(document, file);
    }
  }
  return required;
}

function check(cwd, changes, mode) {
  const required = requiredDocuments(changes);
  const missing = [...required].filter(([document]) => !changes.deliverable.has(document));
  if (!missing.length) {
    process.stdout.write(`README sync gate passed: ${changes.changed.size} changed path(s).\n`);
    return;
  }

  process.stderr.write('README/手册同步门禁未通过。请更新并纳入同一提交或推送范围：\n');
  for (const [document, causes] of missing) {
    const unstaged = mode === '--staged'
      && nulPaths(runGit(cwd, ['diff', '--name-only', '-z', '--', document])).includes(document);
    process.stderr.write(`- ${document}${unstaged ? '（已修改但未暂存）' : ''}\n`);
    for (const cause of [...causes].sort()) process.stderr.write(`  涉及: ${cause}\n`);
  }
  process.exitCode = 1;
}

function main() {
  const cwd = runGit(process.cwd(), ['rev-parse', '--show-toplevel']).trim();
  const mode = process.argv[2];
  if (mode === '--staged') return check(cwd, stagedChanges(cwd), mode);
  if (mode === '--pre-push') return check(cwd, prePushChanges(cwd, process.argv[3] || 'origin'), mode);
  if (mode === '--range') {
    const tip = process.argv[4] || runGit(cwd, ['rev-parse', 'HEAD']).trim();
    const requestedBase = process.argv[3];
    const base = !requestedBase || ZERO_SHA.test(requestedBase) ? pushBase(cwd, 'origin', tip) : requestedBase;
    return check(cwd, rangeChanges(cwd, base, tip), mode);
  }
  throw new Error('用法: check-readme-sync.mjs --staged | --pre-push [remote-name] | --range [base] [tip]');
}

try {
  main();
} catch (error) {
  process.stderr.write(`README sync gate failed: ${error.message}\n`);
  process.exitCode = 1;
}
