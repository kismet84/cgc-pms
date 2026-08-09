import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { appendFileSync, existsSync, mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const skillRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const script = path.join(skillRoot, 'scripts', 'long-task-gate.mjs');
const temporaryRoots = [];

function fixture() {
  const root = mkdtempSync(path.join(tmpdir(), 'ltg-test-'));
  const repository = path.join(root, 'repo');
  const state = path.join(root, 'state');
  const larkLog = path.join(root, 'lark.log');
  const larkMock = path.join(root, 'lark-success.mjs');
  mkdirSync(repository);
  execFileSync('git', ['init', '-q'], { cwd: repository });
  writeFileSync(larkMock, `import{appendFileSync}from'node:fs';appendFileSync(${JSON.stringify(larkLog)},process.env.LTG_IDEMPOTENCY_KEY_FOR_TEST+'\\n');process.stdout.write(JSON.stringify({data:{message_id:'om_mock'}}));`);
  temporaryRoots.push(root);
  return { root, repository, state, larkLog, larkMock };
}

function run(ctx, command, input, extraEnv = {}) {
  const result = spawnSync(process.execPath, [script, ...command], {
    cwd: ctx.repository,
    encoding: 'utf8',
    shell: false,
    timeout: 20_000,
    env: {
      ...process.env,
      NODE_ENV: 'test',
      LONG_TASK_GATE_STATE_ROOT: ctx.state,
      CODEX_THREAD_ID: 'session-a',
      ...extraEnv,
    },
    input: input === undefined ? undefined : JSON.stringify(input),
  });
  return result;
}

function json(result) {
  assert.equal(result.status, 0, result.stderr);
  return JSON.parse(result.stdout);
}

function prompt(ctx, text = '$long-task-gate') {
  return run(ctx, ['user-prompt'], {
    hook_event_name: 'UserPromptSubmit',
    session_id: 'session-a',
    turn_id: 'turn-a',
    cwd: ctx.repository,
    prompt: text,
  });
}

function stop(ctx, extraEnv = {}, payloadOverrides = {}) {
  return run(ctx, ['stop'], {
    hook_event_name: 'Stop',
    session_id: 'session-a',
    turn_id: 'turn-a',
    cwd: ctx.repository,
    stop_hook_active: false,
    last_assistant_message: 'done',
    ...payloadOverrides,
  }, { LONG_TASK_GATE_LARK_MOCK: ctx.larkMock, LTG_FEISHU_CHAT_ID: 'oc_testrecipient', ...extraEnv });
}

function promptFor(ctx, session, text = '$long-task-gate') {
  return run(ctx, ['user-prompt'], {
    hook_event_name: 'UserPromptSubmit',
    session_id: session,
    turn_id: `turn-${session}`,
    cwd: ctx.repository,
    prompt: text,
  }, { CODEX_THREAD_ID: session });
}

function stopFor(ctx, session, extraEnv = {}) {
  return run(ctx, ['stop'], {
    hook_event_name: 'Stop',
    session_id: session,
    turn_id: `turn-${session}`,
    cwd: ctx.repository,
    stop_hook_active: false,
    last_assistant_message: 'done',
  }, { LONG_TASK_GATE_LARK_MOCK: ctx.larkMock, LTG_FEISHU_CHAT_ID: 'oc_testrecipient', ...extraEnv, CODEX_THREAD_ID: session });
}

function contract(overrides = {}) {
  return {
    version: 1,
    taskId: 'test-task',
    summary: 'Test completion contract',
    checks: [{ name: 'evidence', type: 'file', path: 'done.txt', contains: 'OK' }],
    gitScope: ['done.txt'],
    notification: { enabled: false },
    ...overrides,
  };
}

function arm(ctx, value = contract()) {
  writeFileSync(path.join(ctx.repository, 'contract.json'), JSON.stringify(value));
  return run(ctx, ['arm', '--contract', 'contract.json']);
}

function status(ctx) {
  return json(run(ctx, ['status']));
}

function stateFiles(directory) {
  if (!existsSync(directory)) return [];
  const result = [];
  for (const repo of readdirSync(directory)) {
    const repoDir = path.join(directory, repo);
    for (const session of readdirSync(repoDir, { withFileTypes: true }).filter((entry) => entry.isDirectory())) {
      const file = path.join(repoDir, session.name, 'state.json');
      if (existsSync(file)) result.push(file);
    }
  }
  return result;
}

test.after(() => {
  for (const root of temporaryRoots) rmSync(root, { recursive: true, force: true });
});

test('repository Hook config has one explicit command per event', () => {
  const repository = path.resolve(skillRoot, '..', '..', '..');
  const hooks = JSON.parse(readFileSync(path.join(repository, '.codex', 'hooks.json'), 'utf8'));
  assert.equal(hooks.hooks.UserPromptSubmit.length, 1);
  assert.equal(hooks.hooks.Stop.length, 1);
  for (const event of ['UserPromptSubmit', 'Stop']) {
    const handler = hooks.hooks[event][0].hooks[0];
    assert.equal(handler.type, 'command');
    assert.match(handler.commandWindows, /git.*rev-parse.*long-task-gate\.mjs/);
    assert.doesNotMatch(handler.commandWindows, /Users\\|recipient|token/i);
  }
});

test('ordinary prompt does not arm and ordinary Stop sends a turn notification', () => {
  const ctx = fixture();
  assert.deepEqual(json(prompt(ctx, 'ordinary long task text')), {});
  assert.equal(existsSync(ctx.state), false);
  assert.deepEqual(json(stop(ctx)), {});
  assert.deepEqual(json(stop(ctx, {}, { stop_hook_active: true })), {});
  assert.deepEqual(json(stop(ctx, {}, { turn_id: 'turn-b' })), {});
  assert.equal(existsSync(ctx.state), false);
  const keys = readFileSync(ctx.larkLog, 'utf8').trim().split(/\r?\n/);
  assert.equal(keys.length, 2);
  assert.match(keys[0], /^ltg-turn-[a-f0-9]{32}$/);
  assert.notEqual(keys[0], keys[1]);
});

test('ordinary Stop notification failure warns without continuing the task', () => {
  const ctx = fixture();
  const result = json(stop(ctx, { LTG_FEISHU_CHAT_ID: '' }));
  assert.match(result.systemMessage, /飞书任务通知失败/);
  assert.equal(result.decision, undefined);
  assert.equal(result.continue, undefined);
});

test('explicit prompt requests, arms, checks, and completes', () => {
  const ctx = fixture();
  const requested = json(prompt(ctx, 'Please use $long-task-gate.'));
  assert.match(requested.hookSpecificOutput.additionalContext, /arm/);
  writeFileSync(path.join(ctx.repository, 'done.txt'), 'OK\n');
  assert.match(arm(ctx).stdout, /^ARMED test-task/);
  assert.deepEqual(json(stop(ctx)), {});
  const task = status(ctx).tasks[0];
  assert.equal(task.status, 'COMPLETED');
  assert.equal(task.notificationSent, true);
});

test('failed check continues, repaired check completes', () => {
  const ctx = fixture();
  prompt(ctx);
  assert.equal(arm(ctx).status, 0);
  const failed = json(stop(ctx));
  assert.equal(failed.decision, 'block');
  assert.match(failed.reason, /quality_or_security|ready_issue_config/);
  writeFileSync(path.join(ctx.repository, 'done.txt'), 'OK\n');
  assert.deepEqual(json(stop(ctx)), {});
  assert.equal(status(ctx).tasks[0].status, 'COMPLETED');
});

test('three identical check failures stop automatic continuation', () => {
  const ctx = fixture();
  prompt(ctx);
  arm(ctx);
  assert.equal(json(stop(ctx)).decision, 'block');
  assert.equal(json(stop(ctx)).decision, 'block');
  const terminal = json(stop(ctx));
  assert.equal(terminal.continue, false);
  assert.equal(status(ctx).tasks[0].status, 'BLOCKED_GATE');
  assert.match(readFileSync(ctx.larkLog, 'utf8'), /^ltg-turn-[a-f0-9]{32}\n$/);
});

test('missing contract is finite and a second active task is rejected', () => {
  const ctx = fixture();
  prompt(ctx);
  assert.equal(json(prompt(ctx)).decision, 'block');
  assert.equal(json(stop(ctx)).decision, 'block');
  assert.equal(json(stop(ctx)).decision, 'block');
  assert.equal(json(stop(ctx)).continue, false);
});

test('sessions are fenced and cannot arm or stop each other', () => {
  const ctx = fixture();
  promptFor(ctx, 'session-a');
  promptFor(ctx, 'session-b');
  writeFileSync(path.join(ctx.repository, 'done.txt'), 'OK\n');
  writeFileSync(path.join(ctx.repository, 'contract.json'), JSON.stringify(contract()));
  assert.equal(run(ctx, ['arm', '--contract', 'contract.json'], undefined, { CODEX_THREAD_ID: 'session-b' }).status, 0);
  assert.equal(status(ctx).tasks.filter((task) => task.status === 'ARMED').length, 1);
  const stoppedA = json(stopFor(ctx, 'session-a'));
  assert.equal(stoppedA.decision, 'block');
  assert.match(stoppedA.reason, /Completion Contract/);
  const repairing = stateFiles(ctx.state).find((file) => JSON.parse(readFileSync(file, 'utf8')).status === 'REPAIRING');
  writeFileSync(repairing, '{broken');
  assert.deepEqual(json(stopFor(ctx, 'session-b')), {});
  const summary = status(ctx);
  assert.equal(summary.corrupt, 1);
  assert.deepEqual(summary.tasks.map((task) => task.status), ['COMPLETED']);
});

test('file checks reject junction traversal outside repository', () => {
  const ctx = fixture();
  const outside = path.join(ctx.root, 'outside');
  mkdirSync(outside);
  writeFileSync(path.join(outside, 'secret.txt'), 'OK\n');
  symlinkSync(outside, path.join(ctx.repository, 'linked'), process.platform === 'win32' ? 'junction' : 'dir');
  prompt(ctx);
  arm(ctx, contract({ checks: [{ name: 'outside', type: 'file', path: 'linked/secret.txt', contains: 'OK' }], gitScope: ['linked'] }));
  const failed = json(stop(ctx));
  assert.equal(failed.decision, 'block');
  assert.match(failed.reason, /quality_or_security/);
});

test('total budget caps the last command check', () => {
  const ctx = fixture();
  writeFileSync(path.join(ctx.repository, 'slow.mjs'), 'setTimeout(() => {}, 2000);');
  prompt(ctx);
  arm(ctx, contract({
    checks: [{ name: 'slow', type: 'command', executable: 'node', args: ['slow.mjs'], cwd: '.', timeoutMs: 5000 }],
    gitScope: ['slow.mjs'],
  }));
  const started = Date.now();
  const failed = json(stop(ctx, { LONG_TASK_GATE_MAX_TOTAL_MS: '200' }));
  assert.equal(failed.decision, 'block');
  assert.match(failed.reason, /总时限/);
  assert.ok(Date.now() - started < 1500);
});

test('corrupt state and live lock fail closed', () => {
  const ctx = fixture();
  prompt(ctx);
  const [file] = stateFiles(ctx.state);
  writeFileSync(file, '{broken');
  assert.match(json(stop(ctx)).reason, /fail-close/);

  const locked = fixture();
  prompt(locked);
  const [lockedState] = stateFiles(locked.state);
  const lock = path.join(path.dirname(path.dirname(lockedState)), 'repo.lock');
  writeFileSync(lock, JSON.stringify({ pid: process.pid, at: Date.now() }));
  writeFileSync(path.join(locked.repository, 'contract.json'), JSON.stringify(contract()));
  const result = run(locked, ['arm', '--contract', 'contract.json']);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /lock-conflict/);
});

test('stale lock requires explicit race-safe recovery', () => {
  const ctx = fixture();
  prompt(ctx);
  const [requested] = stateFiles(ctx.state);
  const lock = path.join(path.dirname(path.dirname(requested)), 'repo.lock');
  writeFileSync(lock, JSON.stringify({ pid: 999999999, at: 0 }));
  writeFileSync(path.join(ctx.repository, 'contract.json'), JSON.stringify(contract()));
  const blocked = run(ctx, ['arm', '--contract', 'contract.json']);
  assert.equal(blocked.status, 1);
  assert.match(blocked.stderr, /stale-lock/);
  const recovered = run(ctx, ['recover-lock']);
  assert.equal(recovered.status, 0, recovered.stderr);
  assert.match(recovered.stdout, /^RECOVERED/);
  assert.equal(run(ctx, ['arm', '--contract', 'contract.json']).status, 0);
});

test('successful notification is sent once with stable idempotency', () => {
  const ctx = fixture();
  const log = path.join(ctx.root, 'lark.log');
  const mock = path.join(ctx.root, 'lark-mock.mjs');
  writeFileSync(mock, `import{appendFileSync}from'node:fs';appendFileSync(${JSON.stringify(log)},process.env.LTG_IDEMPOTENCY_KEY_FOR_TEST+'\\n');process.stdout.write(JSON.stringify({data:{message_id:'om_mock'}}));`);
  prompt(ctx);
  writeFileSync(path.join(ctx.repository, 'done.txt'), 'OK\n');
  arm(ctx, contract({ notification: { enabled: true, identity: 'bot', targetType: 'user-id', targetEnv: 'LTG_TEST_USER_ID' } }));
  const env = { LONG_TASK_GATE_LARK_MOCK: mock, LTG_TEST_USER_ID: 'ou' + '_testrecipient' };
  assert.deepEqual(json(stop(ctx, env)), {});
  assert.deepEqual(json(stop(ctx, env)), {});
  const keys = readFileSync(log, 'utf8').trim().split(/\r?\n/);
  assert.equal(keys.length, 1);
  assert.match(keys[0], /^ltg-[a-f0-9]{32}$/);
  assert.equal(status(ctx).tasks[0].notificationSent, true);
});

test('notification failure does not block and manual retry does not rerun checks', () => {
  const ctx = fixture();
  const count = path.join(ctx.root, 'check-count');
  const check = path.join(ctx.repository, 'check.mjs');
  writeFileSync(check, `import{appendFileSync}from'node:fs';appendFileSync(${JSON.stringify(count)},'1');console.log('PASS');`);
  const mock = path.join(ctx.root, 'lark-fail.mjs');
  writeFileSync(mock, `process.exit(9);`);
  prompt(ctx);
  arm(ctx, contract({
    checks: [{ name: 'command', type: 'command', executable: 'node', args: ['check.mjs'], cwd: '.', stdoutIncludes: 'PASS' }],
    gitScope: ['check.mjs'],
    notification: { enabled: true, identity: 'bot', targetType: 'user-id', targetEnv: 'LTG_TEST_USER_ID' },
  }));
  const env = { LONG_TASK_GATE_LARK_MOCK: mock, LTG_TEST_USER_ID: 'ou' + '_testrecipient' };
  const stopped = json(stop(ctx, env));
  assert.match(stopped.systemMessage, /飞书通知失败/);
  assert.equal(stopped.decision, undefined);
  assert.equal(stopped.continue, undefined);
  assert.deepEqual(json(stop(ctx, env)), {});
  assert.equal(readFileSync(count, 'utf8'), '1');
  assert.equal(status(ctx).tasks[0].status, 'COMPLETED');
  assert.equal(status(ctx).tasks[0].notificationSent, false);
  const retried = run(ctx, ['notify-retry'], undefined, {
    LONG_TASK_GATE_LARK_MOCK: ctx.larkMock,
    LTG_TEST_USER_ID: 'ou_testrecipient',
  });
  assert.equal(retried.status, 0, retried.stderr);
  assert.equal(readFileSync(count, 'utf8'), '1');
  assert.equal(status(ctx).tasks[0].notificationSent, true);
});

test('cancel closes requested state', () => {
  const ctx = fixture();
  prompt(ctx);
  assert.match(run(ctx, ['cancel']).stdout, /^CANCELLED/);
  assert.equal(status(ctx).tasks[0].status, 'CANCELLED');
});
