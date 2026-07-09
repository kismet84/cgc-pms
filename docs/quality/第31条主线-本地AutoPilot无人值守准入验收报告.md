# 第31条主线-本地AutoPilot无人值守准入验收报告

报告日期：2026-07-09

## 结论

第31主线 M1-M5 的最小闭环能力已具备：Ready 队列门禁、单实例锁、结构化 `result.json`、JSONL 事件、状态摘要、worktree/主工作区分档策略、blocked/WIP/rollback 收口模板均有可核验落点。

准入结论：允许进入完整无人值守模式，但当前只允许在 `autoPush=false`、每轮 1 个 Ready Issue、不连接生产、不发布生产、不执行仓库外删除的边界内运行。当前 `ready-issues.md` 没有 `Ready` 任务时，只允许先进入拆单轮，不得伪造真实业务执行已完成。

阻塞：非阻塞。

## M1-M5 覆盖情况

| 阶段 | 完成情况 | 证据 |
|---|---|---|
| M1 Ready 队列质量门禁 | 完成 | `scripts/codex-autopilot/ready-lint.ps1` 校验状态、目标、允许/禁止修改、验收标准、验证命令、来源锚点、归档报告，并预检 Maven、pnpm、PowerShell、`git diff --check` 入口。 |
| M2 单实例执行与断点安全 | 完成 | `autopilot-run-continuous.ps1` 具备 `run.lock` 创建、读取、心跳、过期判断、释放；`autopilot-status.ps1` 输出锁摘要；`test-continuous-runner.ps1` 覆盖 active/stale lock。 |
| M3 Executor 与结构化结果 | 完成 | `autopilot-exec-issue.ps1` 输出 `result.json`，包含 `status`、`failureCategory`、`artifacts`、`gitSummary`、`validation`、`nextAction`、`stopReason`。 |
| M4 JSONL 日志与状态可观测性 | 完成 | runner/executor 写入 `events.jsonl`；status 输出最新 event/result 摘要；ExplainNextAction 输出 `nextAction`、`stopReason`、`missingGate`、`selectedIssue`、`shouldSplitBacklog`。 |
| M5 隔离策略、回滚与准入裁决 | 完成 | 新增 `autopilot-readiness-check.ps1` 作为只读准入检查；本报告固定 worktree 分档、blocked/WIP/rollback 模板和最终准入结论。 |

## Worktree 与主工作区分档策略

| 任务类型 | 默认工作区 | 规则 |
|---|---|---|
| 实现型任务 | 独立 worktree 优先 | 涉及后端、前端、测试或多文件实现时，使用 `codex-autopilot.config.json` 的 `worktreeRoot`；启动前必须确认主工作区干净、目标 worktree 不冲突。 |
| 验收型任务 | 主工作区只读优先 | 只跑检查、读取报告、核对状态时不创建 worktree；不得写业务代码。 |
| 运维型任务 | 主工作区或固定运维工作区 | runtime refresh、flag/state 检查等固定动作可在主工作区执行；不得连接生产或发布生产。 |
| 审计/归档型任务 | 主工作区 | 只写 `docs/quality/**`、`docs/iterations/**`、`docs/backlog/**` 等正式交付物；不得把临时日志、截图、run id 写进长期规则。 |

## Blocked/WIP/Rollback 收口模板

当无人值守轮次无法通过门禁时，按以下字段收口，不进入下一轮：

```text
任务=ISSUE-xxx
结论=blocked/failed/noop
失败分类=命令调用问题 / 环境前置类 / Ready Issue 配置问题 / 真实质量或安全问题
正式交付物=...
验证证据=...
WIP 暂存=stash hash / stash message / 恢复条件 / 未完成验收项
rollback 点=提交前分支、worktree 路径、最近 result.json、events.jsonl 摘要
nextAction=STOP / SPLIT_BACKLOG / RETRY_AFTER_FIX
剩余风险=...
```

回滚规则：

- 文档或脚本治理变更：优先 Git 回退本轮提交。
- 实现型 WIP 未验收：先 stash 并在 blocked 记录中写明恢复条件。
- runner/executor 脚本异常：保留旧入口，禁用新入口或转 blocked，不强推下一轮。
- 出现 stop/pause：当前任务只做安全收口，收口后不得派发下一任务。

## 无人值守准入清单

必须同时满足：

- `autoPush=false`。
- `maxIssuesPerRun=1`。
- `worktreeRoot` 已配置。
- `.codex-autopilot/run.lock` 不存在或已判定过期并可安全回收。
- `.codex-autopilot/stop.flag`、`.codex-autopilot/pause.flag` 不存在。
- `ready-lint.ps1` 存在且能阻断不合格 Ready Issue。
- runner 具备 `run.lock`、JSONL、ExplainNextAction、executor handoff。
- executor 必产出结构化 `result.json`。
- status 能输出锁、最近 JSONL event、最近 result 摘要。
- 测试数据 reset 只允许 dev/test/demo + localhost/127.0.0.1 + `ALLOW_TEST_DATA_RESET` marker。
- `test-continuous-runner.ps1` 覆盖 lock、event、result、explain 关键路径。

## 验收证据

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-readiness-check.ps1`：通过，输出 JSON，`status=pass`、`unattendedModeAllowed=true`。
- `powershell -NoProfile -Command "[System.Management.Automation.Language.Parser]::ParseFile('D:\projects-test\cgc-pms\scripts\codex-autopilot\autopilot-readiness-check.ps1',[ref]$null,[ref]$null) | Out-Null"`：通过。
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-continuous-runner.ps1`：通过，`continuous runner self-test passed`。
- `git diff --check`：通过。

## 剩余风险

- 当前完整无人值守准入不等于生产发布准入；仍禁止生产发布和生产数据库连接。
- 当前无 Ready Issue 时只能先拆单，不能直接声称完成真实业务 Issue。
- worktree 清理仍以人工或后续固定脚本为主；本轮只固化准入与分档策略，不实现 daemon。
- JSONL 查询仍是文件级读取，没有单独检索工具；不阻塞本地无人值守闭环。
