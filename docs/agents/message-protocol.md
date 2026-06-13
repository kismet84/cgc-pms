# Agent Message Protocol

## Core Rule

Agent-to-agent chat messages must be short. Put details in files.

Each message should contain only:

1. A document path.
2. One sentence describing the document.

## Message Shape

Use this shape when a structured envelope is needed:

```xml
<multi_agent_message>
{
  "message_id": "msg-###",
  "task_id": "task-###-short-name",
  "from": "sender-session-id",
  "to": "receiver-session-id",
  "type": "task_assignment | implementation_result | test_report | rework_request | coordination_notice",
  "document": "D:\\projects-test\\cgc-pms\\.agent-runtime\\path\\file.md",
  "summary": "One short sentence only."
}
</multi_agent_message>
```

Do not include full implementation details, large logs, stack traces, or test output in the chat message. Put those in the linked document.

## Message Types

- `task_assignment`: main agent assigns work to a child agent.
- `implementation_result`: development agent reports implementation outcome.
- `test_report`: testing agent reports verification outcome.
- `rework_request`: main agent requests another implementation pass.
- `coordination_notice`: main agent updates collaboration rules or task state.

## File Locations

- Task files: `.agent-runtime/tasks/task-###-name.md`
- Implementation reports: `.agent-runtime/reports/task-###-name-implementation-result.md`
- Test reports: `.agent-runtime/reports/task-###-name-test-report.md`
- Message records: `.agent-runtime/messages/msg-###-name.md`

## Main Agent Requirements

The main agent must:

- create task files before dispatching,
- keep task state current,
- send only document links and one-sentence summaries to child agents,
- review reports before deciding pass, rework, or stop,
- enforce the three-rework limit.

## Child Agent Requirements

Child agents must:

- read the linked document before acting,
- stay within the documented scope,
- write detailed results to a report file,
- return only the report path and one sentence,
- not dispatch other child agents.

## Example

```text
D:\projects-test\cgc-pms\.agent-runtime\tasks\task-010-example.md
请按该任务文档实现示例修复并写入 implementation report。
```

```text
D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-example-test-report.md
测试报告已落盘，结论为 pass。
```
