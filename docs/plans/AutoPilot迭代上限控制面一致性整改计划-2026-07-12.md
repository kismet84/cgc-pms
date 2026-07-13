# AutoPilot 迭代上限控制面一致性整改计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development and superpowers:verification-before-completion.

**Goal:** 让 runner、checkpoint、status 和当前 flags 对 `LIMIT_REACHED` 给出一致结论，补齐本轮报告索引，并用现有无人值守 canary 验证连续闭环。

**Architecture:** 复用现有 `state.json` 作为迭代计数事实源；checkpoint/status 只增加终态读取，runner 到达上限时关闭 `enabled.flag`。不新增状态机、脚本框架或依赖。

**Tech Stack:** PowerShell、现有 AutoPilot fixture、Git。

## Global Constraints

- 只修改 AutoPilot 控制脚本、对应测试和正式迭代文档。
- 不修改业务代码，不发布、不 push。
- 测试先失败再做最小实现。

### Task 1: 上限控制面一致性

**Files:**
- Modify: `plugins/cgc-pms-autopilot/scripts/autopilot-checkpoint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-status.ps1`
- Test: `scripts/codex-autopilot/test-continuous-runner.ps1`

- [x] 在现有 limit fixture 中断言：到达上限后 `enabled.flag` 不存在，checkpoint 返回 `limit_reached`，status 返回最后计数 Issue 且 recovery 不显示 `NEW_RUN`。
- [x] 运行测试并确认因当前行为不一致而失败。
- [x] 最小修改三个入口，复跑测试通过。

### Task 2: 迭代报告索引补齐

**Files:**
- Modify: `docs/iterations/iteration-2026-07-12-report.md`

- [x] 从既有质量报告和 Done 记录补入 ISSUE-037-001～003 摘要，不复制长日志。
- [x] 用 `rg` 校验报告正文包含 001～010。

### Task 3: 无人值守与收口验证

**Files:**
- Reuse: `scripts/codex-autopilot/test-unattended-canary.ps1`

- [x] 运行控制脚本专项测试。
- [x] 运行现有 20 轮无人值守 canary。
- [x] 对当前仓库关闭遗留 `enabled.flag`，同步 state 心跳与 enabled 字段。
- [x] 运行 `git diff --check`、状态检查并本地提交。
