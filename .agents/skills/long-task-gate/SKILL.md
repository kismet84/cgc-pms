---
name: long-task-gate
description: Register and enforce deterministic completion checks for an explicitly invoked long-running Codex task, then send one idempotent Feishu terminal notification. Use only when the user writes `$long-task-gate` and wants finite Stop-hook continuation backed by a Completion Contract; never infer activation from ordinary mentions of long tasks.
---

# Long Task Gate

Use repository Hook and private per-user state. Keep business work, Git delivery, AutoPilot, and existing notifications authoritative in their own workflows.

## Activation workflow

1. Require literal `$long-task-gate` in user prompt. Do not activate implicitly.
2. Read [references/completion-contract.md](references/completion-contract.md).
3. Create contract JSON in ignored task-local scratch space. Never include credentials, recipient IDs, prompt text, shell fragments, or production actions.
4. Arm only requested repository session:

   ```powershell
   node .agents/skills/long-task-gate/scripts/long-task-gate.mjs arm --contract <repo-relative-json>
   ```

5. Perform task normally. Hook runs checks only at Stop.
6. On continuation, fix reported failure and re-run no broad unrelated work. Three identical gate failures stop at `BLOCKED_GATE`.
7. Notification failure never re-runs checks. Retry only outbox:

   ```powershell
   node .agents/skills/long-task-gate/scripts/long-task-gate.mjs notify-retry
   ```

## Safe operations

Inspect current task without secrets:

```powershell
node .agents/skills/long-task-gate/scripts/long-task-gate.mjs status
```

Cancel current requested or active task:

```powershell
node .agents/skills/long-task-gate/scripts/long-task-gate.mjs cancel
```

If a crashed process leaves a stale repository lock, normal commands fail closed. Inspect state, then explicitly recover only that stale lock:

```powershell
node .agents/skills/long-task-gate/scripts/long-task-gate.mjs recover-lock
```

Use Bot identity. Store recipient only in private environment variable named by contract. Never write recipient value, access token, message ID, prompt, command output, or absolute user path to repository or event ledger.

## Boundaries

- Checks execute exact executable plus argument arrays with `shell: false`; shell executables and inline command switches are rejected.
- Paths and check working directories must remain under Git root.
- State lives under `${CODEX_HOME}/long-task-gate` or default Codex user directory, never repository.
- Ordinary prompts no-op. Corrupt active state, lock conflict, or contract drift fails closed.
- Treat Hook as deterministic workflow guardrail, not security sandbox or replacement for native `/goal` persistence.
- Disable repository Hook through `/hooks` before rollback; do not edit user global Hook or notification config.
