# Local Agent Collaboration Rules

This project uses a local multi-agent workflow coordinated by a main agent.

## Required Reading

Before performing coordinated agent work, read and follow:

- `docs/agents/multi-agent-workflow.md`
- `docs/agents/message-protocol.md`
- `docs/agents/prompts/main-agent.md`

## Current Roles

- Main agent / orchestrator: receives user requests, creates structured task files, dispatches development and testing work, controls rework loops, and summarizes final outcomes.
- Development agent: implements changes requested by the main agent.
- Testing agent: verifies changes requested by the main agent and reports pass or needs-fix.

## Coordination Rules

- Every task must be written to a file under `.agent-runtime/tasks/`.
- Communication between agents must send only:
  - a document link, and
  - one short sentence describing the message.
- Full task details, implementation instructions, test reports, and rework notes belong in files, not chat payloads.
- The main agent is the only orchestrator.
- Development agents and testing agents must not directly dispatch or schedule each other.
- The main agent may run at most three rework loops for a task.
- Business code changes must be scoped to the task file.
- Existing uncommitted user changes must not be reverted without explicit user approval.

## Runtime Directories

- `.agent-runtime/tasks/`: structured task files and task state.
- `.agent-runtime/messages/`: document-linked agent message records.
- `.agent-runtime/reports/`: implementation reports, test reports, logs, and evidence.

## User Preference

For collaborative work, write task details to files first. When communicating with child agents, send the document path plus one sentence only.

## Windows / PowerShell Command Notes

- When running regex commands in PowerShell, wrap regex patterns in single quotes.
  Example: `rg --files frontend-admin\src\pages | rg 'project|target|costTarget|goal'`
- Do not pass unquoted regex alternation such as `project|target`; PowerShell may interpret `|` as a pipeline.
- For complex filters, prefer `Select-String -Pattern '...'` or `Where-Object { $_ -match '...' }`.
