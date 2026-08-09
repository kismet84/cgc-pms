#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { appendFileSync, chmodSync, closeSync, existsSync, mkdirSync, openSync, readFileSync, readdirSync, realpathSync, renameSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import path from 'node:path';

const ACTIVE = new Set(['REQUESTED', 'ARMED', 'CHECKING', 'REPAIRING', 'TASK_PASSED', 'NOTIFYING']);
const TERMINAL = new Set(['COMPLETED', 'BLOCKED_GATE', 'BLOCKED_NOTIFICATION', 'CANCELLED']);
const MAX_CHECKS = 32;
const MAX_CHECK_MS = 120_000;
const MAX_TOTAL_MS = process.env.NODE_ENV === 'test' && process.env.LONG_TASK_GATE_MAX_TOTAL_MS
  ? Number(process.env.LONG_TASK_GATE_MAX_TOTAL_MS)
  : 265_000;
const MAX_CAPTURE = 128 * 1024;
const MAX_EVENT_TEXT = 240;
const MAX_TURN_REPORT_CHARS = 160;
const LOCK_STALE_MS = 120_000;
const BLOCKED_EXECUTABLES = new Set(['bash', 'bash.exe', 'cmd', 'cmd.exe', 'powershell', 'powershell.exe', 'pwsh', 'pwsh.exe', 'sh', 'sh.exe', 'wsl', 'wsl.exe']);
const TURN_NOTIFICATION = { targetType: 'chat-id', targetEnv: 'LTG_FEISHU_CHAT_ID' };

class GateError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

function sha(value) {
  return createHash('sha256').update(value).digest('hex');
}

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, stable(value[key])]));
  }
  return value;
}

function hashState(state) {
  const copy = structuredClone(state);
  delete copy.stateHash;
  return sha(JSON.stringify(stable(copy)));
}

function stateRoot() {
  if (process.env.NODE_ENV === 'test' && process.env.LONG_TASK_GATE_STATE_ROOT) {
    return path.resolve(process.env.LONG_TASK_GATE_STATE_ROOT);
  }
  const codexHome = process.env.CODEX_HOME || path.join(homedir(), '.codex');
  return path.join(codexHome, 'long-task-gate');
}

function repoRoot(cwd = process.cwd()) {
  const result = spawnSync('git', ['rev-parse', '--show-toplevel'], {
    cwd,
    encoding: 'utf8',
    shell: false,
    timeout: 10_000,
    windowsHide: true,
  });
  if (result.status !== 0 || !result.stdout.trim()) throw new GateError('not-git', '当前目录不在 Git 仓库内。');
  return realpathSync(path.resolve(result.stdout.trim()));
}

function repoKey(root) {
  return sha(process.platform === 'win32' ? root.toLowerCase() : root).slice(0, 20);
}

function sessionKey(sessionId) {
  if (typeof sessionId !== 'string' || sessionId.length < 1 || sessionId.length > 256) {
    throw new GateError('invalid-event', 'Hook session_id 无效。');
  }
  return sha(sessionId).slice(0, 20);
}

function turnHash(payload) {
  if (typeof payload.turn_id !== 'string' || !payload.turn_id) throw new GateError('invalid-event', 'Hook turn_id 无效。');
  return sha(payload.turn_id).slice(0, 20);
}

function repoStateDir(root) {
  return path.join(stateRoot(), repoKey(root));
}

function statePath(root, key) {
  return path.join(repoStateDir(root), key, 'state.json');
}

function ensurePrivateDirectory(directory) {
  mkdirSync(directory, { recursive: true, mode: 0o700 });
  try { chmodSync(directory, 0o700); } catch {}
}

function atomicWriteJson(file, value) {
  ensurePrivateDirectory(path.dirname(file));
  const complete = { ...value };
  complete.stateHash = hashState(complete);
  const temp = `${file}.tmp-${process.pid}-${randomBytes(4).toString('hex')}`;
  writeFileSync(temp, `${JSON.stringify(complete, null, 2)}\n`, { encoding: 'utf8', mode: 0o600, flag: 'wx' });
  renameSync(temp, file);
  try { chmodSync(file, 0o600); } catch {}
  return complete;
}

function readState(file) {
  let state;
  try { state = JSON.parse(readFileSync(file, 'utf8')); } catch { throw new GateError('state-corrupt', '长任务门禁状态无法解析。'); }
  if (!state || typeof state !== 'object' || typeof state.stateHash !== 'string' || hashState(state) !== state.stateHash) {
    throw new GateError('state-corrupt', '长任务门禁状态完整性校验失败。');
  }
  return state;
}

function appendEvent(file, state, event, detail = '') {
  const entry = {
    at: new Date().toISOString(),
    event,
    status: state.status,
    taskId: state.contract?.taskId || state.taskId || 'unarmed',
    detail: String(detail).replace(/[\r\n]+/g, ' ').slice(0, MAX_EVENT_TEXT),
  };
  const ledger = path.join(path.dirname(file), 'events.ndjson');
  appendFileSync(ledger, `${JSON.stringify(entry)}\n`, { encoding: 'utf8', mode: 0o600 });
  try { chmodSync(ledger, 0o600); } catch {}
}

function processAlive(pid) {
  if (!Number.isInteger(pid) || pid <= 0) return false;
  try { process.kill(pid, 0); return true; } catch { return false; }
}

function acquireLock(root) {
  const directory = repoStateDir(root);
  ensurePrivateDirectory(directory);
  const file = path.join(directory, 'repo.lock');
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const fd = openSync(file, 'wx', 0o600);
      writeFileSync(fd, JSON.stringify({ pid: process.pid, at: Date.now() }));
      closeSync(fd);
      return () => { try { rmSync(file, { force: true }); } catch {} };
    } catch (error) {
      if (error.code !== 'EEXIST') throw error;
      let stale = false;
      try {
        const lock = JSON.parse(readFileSync(file, 'utf8'));
        stale = Date.now() - Number(lock.at) > LOCK_STALE_MS || !processAlive(Number(lock.pid));
      } catch { stale = true; }
      if (stale) throw new GateError('stale-lock', '检测到陈旧锁；为避免并发误回收，请显式执行 recover-lock。');
      throw new GateError('lock-conflict', '长任务门禁正在处理同仓库状态，请稍后重试。');
    }
  }
  throw new GateError('lock-conflict', '无法取得长任务门禁锁。');
}

function withLock(root, operation) {
  const release = acquireLock(root);
  try { return operation(); } finally { release(); }
}

function discoverStates(root) {
  const directory = repoStateDir(root);
  if (!existsSync(directory)) return [];
  const result = [];
  for (const entry of statelessDirectoryNames(directory)) {
    const file = path.join(directory, entry, 'state.json');
    if (!existsSync(file)) continue;
    try { result.push({ file, key: entry, state: readState(file) }); }
    catch (error) { result.push({ file, key: entry, error }); }
  }
  return result;
}

function statelessDirectoryNames(directory) {
  return readdirSync(directory, { withFileTypes: true }).filter((entry) => entry.isDirectory()).map((entry) => entry.name);
}

function selectState(root, { requestedOnly = false, key } = {}) {
  const states = discoverStates(root).filter((item) => !key || item.key === key);
  const corrupt = states.find((item) => item.error);
  if (corrupt) throw corrupt.error;
  const candidates = states.filter(({ state }) => {
    if (key && state.sessionKey !== key) return false;
    return requestedOnly ? state.status === 'REQUESTED' : ACTIVE.has(state.status);
  });
  if (candidates.length > 1) throw new GateError('multiple-active', '同仓库存在多个活动门禁状态，拒绝猜测。');
  return candidates[0] || null;
}

function assertKeys(object, allowed, label) {
  if (!object || typeof object !== 'object' || Array.isArray(object)) throw new GateError('contract-schema', `${label} 必须是对象。`);
  const unknown = Object.keys(object).filter((key) => !allowed.includes(key));
  if (unknown.length) throw new GateError('contract-schema', `${label} 含未知字段: ${unknown.join(', ')}`);
}

function cleanText(value, label, max) {
  if (typeof value !== 'string' || value.length < 1 || value.length > max || /[\r\n]/.test(value)) {
    throw new GateError('contract-schema', `${label} 无效。`);
  }
  return value;
}

function relativeInside(root, input, { mustExist = true, directory = false } = {}) {
  if (typeof input !== 'string' || !input || path.isAbsolute(input)) throw new GateError('path-scope', '契约路径必须是仓库相对路径。');
  const resolved = path.resolve(root, input);
  const prefix = root.endsWith(path.sep) ? root : `${root}${path.sep}`;
  const comparable = process.platform === 'win32' ? resolved.toLowerCase() : resolved;
  const rootComparable = process.platform === 'win32' ? prefix.toLowerCase() : prefix;
  if (comparable !== (process.platform === 'win32' ? root.toLowerCase() : root) && !comparable.startsWith(rootComparable)) {
    throw new GateError('path-scope', '契约路径逃逸 Git 根目录。');
  }
  if (mustExist) {
    const real = realpathSync(resolved);
    const realComparable = process.platform === 'win32' ? real.toLowerCase() : real;
    if (realComparable !== (process.platform === 'win32' ? root.toLowerCase() : root) && !realComparable.startsWith(rootComparable)) {
      throw new GateError('path-scope', '契约路径经重解析后逃逸 Git 根目录。');
    }
    if (directory && !statSync(real).isDirectory()) throw new GateError('path-scope', '检查 cwd 不是目录。');
    return real;
  }
  return resolved;
}

function validateContract(raw, root) {
  assertKeys(raw, ['version', 'taskId', 'summary', 'checks', 'gitScope', 'notification'], 'contract');
  if (raw.version !== 1) throw new GateError('contract-schema', 'contract.version 必须为 1。');
  const taskId = cleanText(raw.taskId, 'taskId', 64);
  if (!/^[a-z0-9][a-z0-9._-]*$/.test(taskId)) throw new GateError('contract-schema', 'taskId 格式无效。');
  const summary = cleanText(raw.summary, 'summary', 160);
  if (/(?:token|secret|password)\s*[:=]|\b(?:oc|ou)_[A-Za-z0-9]{8,}/i.test(JSON.stringify(raw))) {
    throw new GateError('contract-sensitive', '契约疑似包含凭据或真实收件 ID。');
  }
  if (!Array.isArray(raw.checks) || raw.checks.length < 1 || raw.checks.length > MAX_CHECKS) {
    throw new GateError('contract-schema', `checks 数量必须为 1-${MAX_CHECKS}。`);
  }
  const names = new Set();
  const checks = raw.checks.map((check) => {
    const name = cleanText(check?.name, 'check.name', 64);
    if (names.has(name)) throw new GateError('contract-schema', `检查名重复: ${name}`);
    names.add(name);
    if (check.type === 'command') {
      assertKeys(check, ['name', 'type', 'executable', 'args', 'cwd', 'timeoutMs', 'exitCode', 'stdoutIncludes'], `check ${name}`);
      const executable = cleanText(check.executable, 'executable', 64);
      if (!/^[A-Za-z0-9._-]+$/.test(executable) || BLOCKED_EXECUTABLES.has(executable.toLowerCase())) {
        throw new GateError('contract-schema', `检查 ${name} 使用了禁止的 executable。`);
      }
      if (!Array.isArray(check.args) || check.args.length > 64 || check.args.some((arg) => typeof arg !== 'string' || arg.length > 512 || /[\r\n\0]/.test(arg))) {
        throw new GateError('contract-schema', `检查 ${name} 的 args 无效。`);
      }
      const cwd = check.cwd ?? '.';
      relativeInside(root, cwd, { mustExist: true, directory: true });
      const timeoutMs = check.timeoutMs ?? 60_000;
      if (!Number.isInteger(timeoutMs) || timeoutMs < 100 || timeoutMs > MAX_CHECK_MS) throw new GateError('contract-schema', `检查 ${name} 的 timeoutMs 无效。`);
      const exitCode = check.exitCode ?? 0;
      if (!Number.isInteger(exitCode) || exitCode < 0 || exitCode > 255) throw new GateError('contract-schema', `检查 ${name} 的 exitCode 无效。`);
      if (check.stdoutIncludes !== undefined) cleanText(check.stdoutIncludes, 'stdoutIncludes', 200);
      return { name, type: 'command', executable, args: [...check.args], cwd, timeoutMs, exitCode, ...(check.stdoutIncludes ? { stdoutIncludes: check.stdoutIncludes } : {}) };
    }
    if (check.type === 'file') {
      assertKeys(check, ['name', 'type', 'path', 'contains', 'sha256'], `check ${name}`);
      const filePath = cleanText(check.path, 'file.path', 240);
      relativeInside(root, filePath, { mustExist: false });
      if (check.contains !== undefined) cleanText(check.contains, 'contains', 200);
      if (check.sha256 !== undefined && !/^[a-fA-F0-9]{64}$/.test(check.sha256)) throw new GateError('contract-schema', `检查 ${name} 的 sha256 无效。`);
      return { name, type: 'file', path: filePath, ...(check.contains ? { contains: check.contains } : {}), ...(check.sha256 ? { sha256: check.sha256.toLowerCase() } : {}) };
    }
    throw new GateError('contract-schema', `检查 ${name} 的 type 无效。`);
  });
  const gitScope = raw.gitScope ?? [];
  if (!Array.isArray(gitScope) || gitScope.length > 64) throw new GateError('contract-schema', 'gitScope 无效。');
  for (const scope of gitScope) relativeInside(root, cleanText(scope, 'gitScope', 240), { mustExist: false });
  const notification = raw.notification ?? { enabled: false };
  assertKeys(notification, ['enabled', 'identity', 'targetType', 'targetEnv'], 'notification');
  if (typeof notification.enabled !== 'boolean') throw new GateError('contract-schema', 'notification.enabled 必须是布尔值。');
  let normalizedNotification = { enabled: false };
  if (notification.enabled) {
    if (notification.identity !== 'bot') throw new GateError('contract-schema', 'V1 notification.identity 只允许 bot。');
    if (!['chat-id', 'user-id'].includes(notification.targetType)) throw new GateError('contract-schema', 'notification.targetType 无效。');
    if (typeof notification.targetEnv !== 'string' || !/^LTG_[A-Z0-9_]{3,60}$/.test(notification.targetEnv)) throw new GateError('contract-schema', 'notification.targetEnv 无效。');
    normalizedNotification = { enabled: true, identity: 'bot', targetType: notification.targetType, targetEnv: notification.targetEnv };
  }
  return { version: 1, taskId, summary, checks, gitScope: [...gitScope], notification: normalizedNotification };
}

function gitDirtyPaths(root) {
  const result = spawnSync('git', ['status', '--porcelain=v1', '-z', '--untracked-files=all'], {
    cwd: root, encoding: 'utf8', shell: false, timeout: 20_000, windowsHide: true,
  });
  if (result.status !== 0) throw new GateError('git-status', '无法读取 Git 变更范围。');
  const paths = [];
  for (const record of result.stdout.split('\0').filter(Boolean)) {
    if (record.length < 4) continue;
    let name = record.slice(3);
    if (name.includes(' -> ')) name = name.split(' -> ').at(-1);
    paths.push(name.replaceAll('\\', '/'));
  }
  return [...new Set(paths)].sort();
}

function scopeAllows(file, scopes) {
  return scopes.some((scope) => {
    const normalized = scope.replaceAll('\\', '/').replace(/^\.\//, '').replace(/\/$/, '');
    return file === normalized || file.startsWith(`${normalized}/`);
  });
}

function runChecks(state, root) {
  const started = Date.now();
  const currentDirty = gitDirtyPaths(root);
  const baseline = new Set(state.gitBaseline || []);
  const newlyDirty = currentDirty.filter((file) => !baseline.has(file));
  const outOfScope = state.contract.gitScope.length ? newlyDirty.filter((file) => !scopeAllows(file, state.contract.gitScope)) : [];
  if (outOfScope.length) {
    return { ok: false, name: 'git-scope', category: 'quality_or_security', detail: `新增变更超出 gitScope: ${outOfScope.slice(0, 5).join(', ')}` };
  }
  for (const check of state.contract.checks) {
    const remainingMs = MAX_TOTAL_MS - (Date.now() - started);
    if (remainingMs < 100) return { ok: false, name: check.name, category: 'environment_prerequisite', detail: '检查总时限耗尽。' };
    if (check.type === 'file') {
      const unresolved = relativeInside(root, check.path, { mustExist: false });
      if (!existsSync(unresolved)) return { ok: false, name: check.name, category: 'ready_issue_config', detail: '要求文件不存在。' };
      let file;
      try { file = relativeInside(root, check.path, { mustExist: true }); }
      catch (error) {
        return { ok: false, name: check.name, category: 'quality_or_security', detail: error instanceof GateError ? error.message : '要求文件路径无法安全重解析。' };
      }
      if (!statSync(file).isFile()) return { ok: false, name: check.name, category: 'ready_issue_config', detail: '要求路径不是文件。' };
      if (statSync(file).size > 1024 * 1024) return { ok: false, name: check.name, category: 'ready_issue_config', detail: '要求文件超过 1 MiB 上限。' };
      const content = readFileSync(file);
      if (check.contains && !content.toString('utf8').includes(check.contains)) return { ok: false, name: check.name, category: 'quality_or_security', detail: '要求文本不存在。' };
      if (check.sha256 && sha(content) !== check.sha256) return { ok: false, name: check.name, category: 'quality_or_security', detail: 'SHA-256 不匹配。' };
      continue;
    }
    const cwd = relativeInside(root, check.cwd, { mustExist: true, directory: true });
    const spawnTimeout = Math.min(check.timeoutMs, remainingMs);
    const result = spawnSync(check.executable, check.args, {
      cwd, encoding: 'utf8', shell: false, timeout: spawnTimeout, maxBuffer: MAX_CAPTURE, windowsHide: true,
    });
    if (result.error?.code === 'ETIMEDOUT') {
      const detail = spawnTimeout < check.timeoutMs ? '检查总时限耗尽。' : '检查超时。';
      return { ok: false, name: check.name, category: 'environment_prerequisite', detail };
    }
    if (result.error) return { ok: false, name: check.name, category: 'tool_invocation', detail: `检查无法启动: ${result.error.code || 'spawn_error'}` };
    if (result.status !== check.exitCode) return { ok: false, name: check.name, category: 'quality_or_security', detail: `退出码 ${result.status}，期望 ${check.exitCode}。` };
    if (check.stdoutIncludes && !String(result.stdout).includes(check.stdoutIncludes)) return { ok: false, name: check.name, category: 'quality_or_security', detail: '标准输出缺少要求文本。' };
  }
  return { ok: true };
}

function failureFingerprint(failure) {
  return sha(JSON.stringify({ name: failure.name, category: failure.category, detail: failure.detail })).slice(0, 20);
}

function notificationMessage(state) {
  return `CGC-PMS 长任务已通过\n任务: ${state.contract.taskId}\n摘要: ${state.contract.summary}\n检查: ${state.contract.checks.length}\n状态: COMPLETED`;
}

function turnReport(payload) {
  const fallback = '本轮未生成可用汇报。';
  const source = typeof payload.last_assistant_message === 'string' ? payload.last_assistant_message : '';
  let report = source.split(/\r?\n/).map((line) => line.trim()).find((line) => line && !line.startsWith('```')) || fallback;
  report = report
    .replace(/^(?:#{1,6}|[-*+]|>)\s+/, '')
    .replace(/-----BEGIN\s+(?:RSA\s+|EC\s+|OPENSSH\s+)?PRIVATE KEY-----/gi, '[已脱敏]')
    .replace(/\b(?:sk|gh[pousr]|xox[baprs])[-_][A-Za-z0-9_-]{8,}\b/gi, '[已脱敏]')
    .replace(/\bBearer\s+[A-Za-z0-9._~+/=-]{8,}\b/gi, 'Bearer [已脱敏]')
    .replace(/\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/g, '[已脱敏]')
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, '[已脱敏]')
    .replace(
      /((?:^|[^A-Za-z0-9_-])(?:[A-Za-z0-9]+[_-])*(?:api[_-]?key|secret|token|password|passwd|pwd|authorization|cookie)(?:[_-][A-Za-z0-9]+)*\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;&#。！？!?]+)/gi,
      '$1[已脱敏]',
    )
    .replace(/\s+/g, ' ')
    .trim() || fallback;
  const sentenceEnd = report.search(/[。！？!?]/u);
  if (sentenceEnd >= 0) report = report.slice(0, sentenceEnd + 1);
  const characters = Array.from(report);
  return characters.length <= MAX_TURN_REPORT_CHARS ? report : `${characters.slice(0, MAX_TURN_REPORT_CHARS - 1).join('').trimEnd()}…`;
}

function larkInvocation(args) {
  if (process.env.NODE_ENV === 'test' && process.env.LONG_TASK_GATE_LARK_MOCK) {
    return { executable: process.execPath, args: [process.env.LONG_TASK_GATE_LARK_MOCK, ...args] };
  }
  const locator = process.platform === 'win32' ? ['where.exe', ['lark-cli']] : ['which', ['lark-cli']];
  const located = spawnSync(locator[0], locator[1], { encoding: 'utf8', shell: false, timeout: 10_000, windowsHide: true });
  if (located.status === 0) {
    for (const candidate of located.stdout.split(/\r?\n/).filter(Boolean)) {
      const entrypoint = path.join(path.dirname(candidate.trim()), 'node_modules', '@larksuite', 'cli', 'scripts', 'run.js');
      if (existsSync(entrypoint)) return { executable: process.execPath, args: [entrypoint, ...args] };
    }
  }
  throw new GateError('lark-cli-missing', 'lark-cli Node 入口不可解析。');
}

function sendLarkMessage(notification, text, idempotencyKey) {
  const target = process.env[notification.targetEnv];
  if (typeof target !== 'string' || (notification.targetType === 'chat-id' ? !/^oc_[A-Za-z0-9]+$/.test(target) : !/^ou_[A-Za-z0-9]+$/.test(target))) {
    return { ok: false, detail: '目标环境变量缺失或格式无效。' };
  }
  const targetFlag = notification.targetType === 'chat-id' ? '--chat-id' : '--user-id';
  const args = ['im', '+messages-send', '--as', 'bot', targetFlag, target, '--text', text, '--idempotency-key', idempotencyKey, '--format', 'json'];
  let invocation;
  try { invocation = larkInvocation(args); } catch (error) { return { ok: false, detail: error.code || 'lark-cli-missing' }; }
  const result = spawnSync(invocation.executable, invocation.args, {
    encoding: 'utf8', shell: false, timeout: 30_000, maxBuffer: MAX_CAPTURE, windowsHide: true,
    env: { ...process.env, LTG_IDEMPOTENCY_KEY_FOR_TEST: idempotencyKey },
  });
  if (result.error || result.status !== 0) return { ok: false, detail: result.error?.code || `exit-${result.status}` };
  let payload;
  try { payload = JSON.parse(result.stdout); } catch { return { ok: false, detail: 'lark-cli 返回非 JSON。' }; }
  const messageId = payload.message_id || payload.messageId || payload.data?.message_id || payload.data?.messageId;
  if (typeof messageId !== 'string' || !messageId) return { ok: false, detail: 'lark-cli JSON 缺少 message id。' };
  return { ok: true, messageId };
}

function notifyTurnStop(payload, root, status = 'STOPPED') {
  try {
    const idempotencyKey = `ltg-turn-${sha(`${repoKey(root)}:${sessionKey(payload.session_id)}:${turnHash(payload)}`).slice(0, 32)}`;
    const message = `CGC-PMS Codex 任务已停止\n仓库: ${path.basename(root)}\n状态: ${status}\n汇报: ${turnReport(payload)}`;
    const result = sendLarkMessage(TURN_NOTIFICATION, message, idempotencyKey);
    return result.ok ? {} : { systemMessage: `飞书任务通知失败：${result.detail}` };
  } catch (error) {
    return { systemMessage: `飞书任务通知失败：${error instanceof GateError ? error.message : '未分类错误。'}` };
  }
}

function notify(state, file) {
  const notification = state.contract.notification.enabled ? state.contract.notification : TURN_NOTIFICATION;
  if (!state.outbox) {
    state.outbox = {
      idempotencyKey: `ltg-${sha(`${repoKeyValue(state)}:${state.sessionKey}:${state.contractHash}`).slice(0, 32)}`,
      attempts: 0,
      sent: false,
    };
  }
  state.status = 'NOTIFYING';
  state.outbox.attempts += 1;
  state = atomicWriteJson(file, state);
  appendEvent(file, state, 'notification-attempt', `attempt=${state.outbox.attempts}`);
  const result = sendLarkMessage(notification, notificationMessage(state), state.outbox.idempotencyKey);
  if (!result.ok) return notificationFailure(state, file, result.detail);
  state.outbox.sent = true;
  state.outbox.messageIdHash = sha(result.messageId).slice(0, 20);
  state.status = 'COMPLETED';
  state.completedAt = new Date().toISOString();
  state = atomicWriteJson(file, state);
  appendEvent(file, state, 'completed', 'notification-sent');
  return { state, ok: true, notified: true };
}

function repoKeyValue(state) {
  return state.repoKey || 'repo';
}

function notificationFailure(state, file, detail) {
  state.outbox ||= { idempotencyKey: `ltg-${sha(`${repoKeyValue(state)}:${state.sessionKey}:${state.contractHash}`).slice(0, 32)}`, attempts: 0, sent: false };
  if (state.status !== 'NOTIFYING') state.outbox.attempts += 1;
  state.outbox.lastFailure = String(detail).slice(0, 80);
  state.status = 'COMPLETED';
  state.completedAt = new Date().toISOString();
  state = atomicWriteJson(file, state);
  appendEvent(file, state, 'notification-failed', `attempt=${state.outbox.attempts}`);
  return { state, ok: false, detail: '飞书通知失败；任务已结束，业务检查未重跑。' };
}

function parseHookInput(expectedEvent) {
  let payload;
  try { payload = JSON.parse(readFileSync(0, 'utf8')); } catch { throw new GateError('invalid-event', 'Hook stdin 不是有效 JSON。'); }
  if (payload.hook_event_name !== expectedEvent) throw new GateError('invalid-event', `期望 ${expectedEvent} Hook。`);
  return payload;
}

function output(value) {
  process.stdout.write(`${JSON.stringify(value)}\n`);
}

function handleUserPrompt() {
  const payload = parseHookInput('UserPromptSubmit');
  if (typeof payload.prompt !== 'string' || !/(^|\s)\$long-task-gate(?![A-Za-z0-9_-])/.test(payload.prompt)) return output({});
  const root = repoRoot(payload.cwd);
  return withLock(root, () => {
    const key = sessionKey(payload.session_id);
    const existing = selectState(root, { key });
    if (existing) return output({ decision: 'block', reason: '当前会话已有活动 long-task-gate；先完成或显式 cancel。' });
    const file = statePath(root, key);
    let prior = null;
    if (existsSync(file)) prior = readState(file);
    let state = {
      schemaVersion: 1,
      repoKey: repoKey(root),
      sessionKey: key,
      taskInstanceId: sha(randomBytes(32)).slice(0, 20),
      generation: Number(prior?.generation || 0) + 1,
      requestedTurnHash: sha(String(payload.turn_id || '')).slice(0, 20),
      taskId: 'unarmed',
      status: 'REQUESTED',
      requestedAt: new Date().toISOString(),
      failure: null,
    };
    state = atomicWriteJson(file, state);
    appendEvent(file, state, 'requested');
    output({
      hookSpecificOutput: {
        hookEventName: 'UserPromptSubmit',
        additionalContext: 'long-task-gate 已请求。按 $long-task-gate Skill 创建 Completion Contract，并在结束本轮前执行 arm；未 arm 时 Stop 将继续任务。',
      },
    });
  });
}

function handleArm(args) {
  const index = args.indexOf('--contract');
  if (index < 0 || !args[index + 1] || args.length !== 2) throw new GateError('usage', '用法: arm --contract <repo-relative-json>');
  const root = repoRoot();
  return withLock(root, () => {
    const key = sessionKey(process.env.CODEX_THREAD_ID);
    const selected = selectState(root, { requestedOnly: true, key });
    if (!selected) throw new GateError('not-requested', '当前会话没有 REQUESTED 状态。');
    const contractFile = relativeInside(root, args[index + 1], { mustExist: true });
    if (statSync(contractFile).size > 256 * 1024) throw new GateError('contract-schema', '契约文件超过 256 KiB。');
    let raw;
    try { raw = JSON.parse(readFileSync(contractFile, 'utf8')); } catch { throw new GateError('contract-schema', '契约不是有效 JSON。'); }
    const contract = validateContract(raw, root);
    let state = selected.state;
    state.taskId = contract.taskId;
    state.contract = contract;
    state.contractHash = sha(JSON.stringify(stable(contract)));
    state.gitBaseline = gitDirtyPaths(root);
    state.status = 'ARMED';
    state.armedAt = new Date().toISOString();
    state.failure = null;
    state = atomicWriteJson(selected.file, state);
    appendEvent(selected.file, state, 'armed', `checks=${contract.checks.length}`);
    process.stdout.write(`ARMED ${contract.taskId} checks=${contract.checks.length}\n`);
  });
}

function handleStop() {
  const payload = parseHookInput('Stop');
  const root = repoRoot(payload.cwd);
  if (!existsSync(repoStateDir(root))) return output(payload.stop_hook_active ? {} : notifyTurnStop(payload, root));
  return withLock(root, () => {
    const key = sessionKey(payload.session_id);
    const selected = selectState(root, { key });
    if (!selected) {
      const priorFile = statePath(root, key);
      if (existsSync(priorFile)) {
        const prior = readState(priorFile);
        if (TERMINAL.has(prior.status) && prior.terminalTurnHash === turnHash(payload)) return output({});
      }
      return output(payload.stop_hook_active ? {} : notifyTurnStop(payload, root));
    }
    let state = selected.state;
    if (!state.contract) {
      const fingerprint = sha('missing-contract').slice(0, 20);
      const attempts = state.failure?.fingerprint === fingerprint ? state.failure.attempts + 1 : 1;
      state.failure = { fingerprint, attempts, category: 'ready_issue_config', check: 'completion-contract' };
      state.status = attempts >= 3 ? 'BLOCKED_GATE' : 'REPAIRING';
      if (state.status === 'BLOCKED_GATE') state.terminalTurnHash = turnHash(payload);
      state = atomicWriteJson(selected.file, state);
      appendEvent(selected.file, state, 'gate-failed', `completion-contract attempt=${attempts}`);
      if (state.status === 'BLOCKED_GATE') return output({ continue: false, stopReason: 'Completion Contract 连续缺失 3 次，门禁停止自动续跑。', ...notifyTurnStop(payload, root, 'BLOCKED_GATE') });
      return output({ decision: 'block', reason: '失败分类 ready_issue_config：Completion Contract 尚未 arm。创建并校验契约后执行 arm，再继续任务。' });
    }
    if (state.status === 'TASK_PASSED' || state.status === 'NOTIFYING') {
      state.terminalTurnHash = turnHash(payload);
      state = atomicWriteJson(selected.file, state);
      const result = notify(state, selected.file);
      return output(result.ok ? {} : { systemMessage: result.detail });
    }
    if (!['ARMED', 'REPAIRING', 'CHECKING'].includes(state.status)) return output({});
    state.status = 'CHECKING';
    state = atomicWriteJson(selected.file, state);
    appendEvent(selected.file, state, 'checking');
    const result = runChecks(state, root);
    if (!result.ok) {
      const fingerprint = failureFingerprint(result);
      const attempts = state.failure?.fingerprint === fingerprint ? state.failure.attempts + 1 : 1;
      state.failure = { fingerprint, attempts, category: result.category, check: result.name };
      state.status = attempts >= 3 ? 'BLOCKED_GATE' : 'REPAIRING';
      if (state.status === 'BLOCKED_GATE') state.terminalTurnHash = turnHash(payload);
      state = atomicWriteJson(selected.file, state);
      appendEvent(selected.file, state, 'gate-failed', `${result.name} attempt=${attempts}`);
      const reason = `失败分类 ${result.category}；检查 ${result.name}：${result.detail} 修复后从首项重跑全部 Completion Contract 检查。`;
      if (state.status === 'BLOCKED_GATE') return output({ continue: false, stopReason: `${reason} 相同失败已达 3 次，停止自动续跑。`, ...notifyTurnStop(payload, root, 'BLOCKED_GATE') });
      return output({ decision: 'block', reason });
    }
    state.status = 'TASK_PASSED';
    state.failure = null;
    state.checksPassedAt = new Date().toISOString();
    state.terminalTurnHash = turnHash(payload);
    state = atomicWriteJson(selected.file, state);
    appendEvent(selected.file, state, 'checks-passed');
    const notification = notify(state, selected.file);
    return output(notification.ok ? {} : { systemMessage: notification.detail });
  });
}

function handleStatus() {
  const root = repoRoot();
  const states = discoverStates(root);
  const corrupt = states.filter((item) => item.error).length;
  const summaries = states.filter((item) => item.state).map(({ state }) => ({
    taskId: state.contract?.taskId || state.taskId,
    status: state.status,
    checks: state.contract?.checks?.length || 0,
    failure: state.failure ? { category: state.failure.category, check: state.failure.check, attempts: state.failure.attempts } : null,
    notificationAttempts: state.outbox?.attempts || 0,
    notificationSent: state.outbox?.sent === true,
  }));
  output({ repository: repoKey(root), corrupt, tasks: summaries });
}

function handleCancel() {
  const root = repoRoot();
  return withLock(root, () => {
    const selected = selectState(root, { key: sessionKey(process.env.CODEX_THREAD_ID) });
    if (!selected) throw new GateError('not-active', '没有可取消的活动门禁状态。');
    let state = selected.state;
    state.status = 'CANCELLED';
    state.cancelledAt = new Date().toISOString();
    state = atomicWriteJson(selected.file, state);
    appendEvent(selected.file, state, 'cancelled');
    process.stdout.write(`CANCELLED ${state.contract?.taskId || 'unarmed'}\n`);
  });
}

function handleNotifyRetry() {
  const root = repoRoot();
  return withLock(root, () => {
    const key = sessionKey(process.env.CODEX_THREAD_ID);
    const states = discoverStates(root).filter((item) => item.key === key);
    const corrupt = states.find((item) => item.error);
    if (corrupt) throw corrupt.error;
    const candidates = states.filter(({ state }) => state.sessionKey === key && (
      ['TASK_PASSED', 'NOTIFYING', 'BLOCKED_NOTIFICATION'].includes(state.status)
      || (state.status === 'COMPLETED' && state.outbox?.sent !== true)
    ));
    if (candidates.length !== 1) throw new GateError('not-notifiable', '没有唯一可重试通知状态。');
    let state = candidates[0].state;
    if (state.status === 'BLOCKED_NOTIFICATION' || state.status === 'COMPLETED') state.status = 'TASK_PASSED';
    const result = notify(state, candidates[0].file);
    if (!result.ok) throw new GateError('notification-failed', result.detail);
    process.stdout.write(`COMPLETED ${result.state.contract.taskId}\n`);
  });
}

function handleRecoverLock() {
  const root = repoRoot();
  const directory = repoStateDir(root);
  ensurePrivateDirectory(directory);
  const lockFile = path.join(directory, 'repo.lock');
  const guardFile = path.join(directory, 'repo.lock.recover');
  let guard;
  try {
    guard = openSync(guardFile, 'wx', 0o600);
    writeFileSync(guard, JSON.stringify({ pid: process.pid, at: Date.now() }));
    closeSync(guard);
    guard = undefined;
  } catch (error) {
    if (guard !== undefined) closeSync(guard);
    if (error.code === 'EEXIST') throw new GateError('recover-conflict', '另一个显式锁恢复正在进行。');
    throw error;
  }
  try {
    if (!existsSync(lockFile)) throw new GateError('no-stale-lock', '当前仓库没有可恢复锁。');
    let lock;
    try { lock = JSON.parse(readFileSync(lockFile, 'utf8')); }
    catch { lock = { pid: 0, at: 0 }; }
    const stale = Date.now() - Number(lock.at) > LOCK_STALE_MS || !processAlive(Number(lock.pid));
    if (!stale) throw new GateError('lock-live', '锁所属进程仍存活，拒绝恢复。');
    rmSync(lockFile);
    process.stdout.write('RECOVERED stale repository lock\n');
  } finally {
    rmSync(guardFile, { force: true });
  }
}

function main() {
  const [command, ...args] = process.argv.slice(2);
  if (command === 'user-prompt') return handleUserPrompt();
  if (command === 'stop') return handleStop();
  if (command === 'arm') return handleArm(args);
  if (command === 'status') return handleStatus();
  if (command === 'cancel') return handleCancel();
  if (command === 'notify-retry') return handleNotifyRetry();
  if (command === 'recover-lock') return handleRecoverLock();
  throw new GateError('usage', '命令必须是 user-prompt|stop|arm|status|cancel|notify-retry|recover-lock。');
}

try {
  main();
} catch (error) {
  const command = process.argv[2];
  const message = error instanceof GateError ? error.message : '长任务门禁发生未分类错误。';
  if (command === 'stop') output({ decision: 'block', reason: `long-task-gate fail-close：${message}` });
  else if (command === 'user-prompt') output({ decision: 'block', reason: message });
  else {
    process.stderr.write(`${error.code || 'unknown'}: ${message}\n`);
    process.exitCode = 1;
  }
}
