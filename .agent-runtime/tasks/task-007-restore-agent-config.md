# task-007-restore-agent-config

## Title

Restore local multi-agent collaboration configuration.

## Status

Completed.

## User Request

The previous agent configuration files are missing. Recreate the local multi-agent workflow files and make them available in the repository workspace.

## Root Cause Notes

- `AGENTS.md`, `docs/agents/`, and `.agent-runtime/messages/` were absent from the current `master` worktree.
- Git history did not contain those files, so the missing files were not protected by version control.
- `.agent-runtime/` still existed but only contained recent task files and logs.

## Restored Files

- `AGENTS.md`
- `docs/agents/multi-agent-workflow.md`
- `docs/agents/message-protocol.md`
- `docs/agents/prompts/main-agent.md`
- `.agent-runtime/messages/README.md`
- `.agent-runtime/tasks/task-007-restore-agent-config.md`

## Acceptance Criteria

- The main agent can bootstrap from `AGENTS.md`.
- Multi-agent tasks must be written to files.
- Agent-to-agent messages should contain only a document link and one sentence.
- The main agent remains the only orchestrator.
- Developer and test agents do not dispatch each other.
- Rework loops are capped at three.
