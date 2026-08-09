# Completion Contract

Contract is copied into private state when armed. Repository file may then remain in ignored scratch space until task closes.

## Schema

```json
{
  "version": 1,
  "taskId": "mainline-example",
  "summary": "Concise terminal notification summary",
  "checks": [
    {
      "name": "node-tests",
      "type": "command",
      "executable": "node",
      "args": ["--test", "path/to/test.mjs"],
      "cwd": ".",
      "timeoutMs": 120000,
      "stdoutIncludes": "pass"
    },
    {
      "name": "quality-report",
      "type": "file",
      "path": "docs/quality/report.md",
      "contains": "G0-G5"
    }
  ],
  "gitScope": ["path/to/module", "docs/quality/report.md"],
  "notification": {
    "enabled": true,
    "identity": "bot",
    "targetType": "user-id",
    "targetEnv": "LTG_FEISHU_USER_ID"
  }
}
```

## Rules

- Allowed top-level fields: `version`, `taskId`, `summary`, `checks`, `gitScope`, `notification`.
- `taskId`: lowercase letters, digits, dots, underscores, hyphens; 1–64 characters.
- `summary`: one line, 1–160 characters; no recipient or credential.
- `checks`: 1–32 ordered checks.
- Command check fields: `name`, `type`, `executable`, `args`, optional `cwd`, `timeoutMs`, `exitCode`, `stdoutIncludes`.
- File check fields: `name`, `type`, `path`, optional `contains`, `sha256`.
- `executable` is a bare program name. Shells (`cmd`, PowerShell, Bash, sh, WSL) are forbidden. Arguments are passed without shell expansion.
- All paths are repository-relative, cannot escape Git root, and cannot use symlink traversal.
- `gitScope` compares newly dirty paths against arm-time baseline; pre-existing dirty paths remain outside task ownership.
- Notification target value must come from named environment variable. `targetType` is `chat-id` or `user-id`; target value never enters state.
- Notification disabled is valid for the contract-specific target; repository Stop falls back to `LTG_FEISHU_CHAT_ID`. Missing or invalid notification targets leave the task `COMPLETED`, surface a warning, and keep an unsent outbox for explicit retry.

State transitions:

```text
REQUESTED -> ARMED -> CHECKING -> REPAIRING -> CHECKING
                          |             \-> BLOCKED_GATE (same failure x3)
                          \-> TASK_PASSED -> NOTIFYING -> COMPLETED
                                                \-> COMPLETED (notification pending)
REQUESTED|ARMED|REPAIRING|TASK_PASSED -> CANCELLED
```
