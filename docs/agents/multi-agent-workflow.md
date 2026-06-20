# Multi-Agent Workflow

## Purpose

This workflow keeps multi-agent development explicit, auditable, and recoverable. The main agent owns orchestration. Child agents execute scoped work and report results through files.

## Roles

### Main Agent

Responsibilities:

- Receive user requests.
- Create or update a task file in `.agent-runtime/tasks/`.
- Dispatch development work when code changes are required.
- Dispatch testing work after implementation.
- Review implementation and test reports.
- Control rework loops, up to a maximum of three.
- Summarize final results to the user.

The main agent may inspect code, run commands, and perform small operational fixes directly when this is safer or faster than dispatching, but it must still record collaborative tasks in a file.

### Development Agent

Responsibilities:

- Read the linked task file.
- Implement only the requested scope.
- Avoid unrelated refactors.
- Run focused verification where practical.
- Write an implementation report under `.agent-runtime/reports/`.
- Return only the report path plus one sentence to the main agent.

Development agents must not dispatch testing agents or other development agents.

### Testing Agent

Responsibilities:

- Read the linked task or implementation report.
- Verify the requested behavior.
- Run focused tests and record commands, results, and risks.
- Write a test report under `.agent-runtime/reports/`.
- Return only the report path plus one sentence to the main agent.

Testing agents must not dispatch development agents or other testing agents.

## Task Lifecycle

1. Create task file.
2. Dispatch development work if implementation is required.
3. Receive implementation report.
4. Dispatch testing work.
5. Receive test report.
6. If testing passes, close the task.
7. If testing needs a fix, update the task with rework notes and dispatch the development agent again.
8. Stop after three rework loops and ask the user how to proceed if the task still cannot pass.

## Task File Requirements

Each task file should include:

- Title
- Status
- User request
- Goal
- Scope
- Constraints
- Acceptance criteria
- Relevant files or suspected areas
- Verification expectations
- Rework history, if any

## Report Requirements

Implementation reports should include:

- Status
- Summary
- Changed files
- Verification commands and results
- Known risks

Test reports should include:

- Decision: `pass` or `needs_fix`
- Executed commands
- Passed checks
- Failed checks
- Recommendation
- Notes and residual risks

## Rework Limit

The main agent controls rework. A single task may have at most three rework loops:

- Rework 1
- Rework 2
- Rework 3

After the third failed rework, the main agent must stop the loop and summarize the blocker to the user.

## Direct Work By Main Agent

The main agent may handle simple or operational tasks directly, such as:

- restarting a local service,
- checking a port,
- restoring documentation,
- making a narrowly scoped config fix,
- validating a deployed page.

Even then, if the task is part of coordinated work, the main agent must write a task file and record results.
