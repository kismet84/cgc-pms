# Install

## 本地目录验证

1. 保持插件位于 `plugins/cgc-pms-autopilot`
2. 校验 manifest：
   `python C:\Users\L1597\.codex\skills\.system\plugin-creator\scripts\validate_plugin.py D:\projects-test\cgc-pms\plugins\cgc-pms-autopilot`
3. 验证 PowerShell 脚本最小 dry-run
4. 插件自有计划书、收口报告、迭代摘要、run summary 默认归档到 `plugins/cgc-pms-autopilot/artifacts/**`

## 当前 MVP 不做

- marketplace 安装
- MCP 可执行工具接管
- dashboard
- 数据库
- 复杂调度器

## 使用顺序

1. 项目统一控制面：`scripts/codex-autopilot/autopilot-run-continuous.ps1`
2. 存量问题查询/补货：`node tools/knowledge-graph/src/cli.js status` 后执行有界 `issues` 查询；AutoPilot 要求 Git 游标覆盖当前 HEAD
3. 插件兼容预演：`autopilot-loop-runner.ps1 -DryRun`
4. `autopilot-checkpoint.ps1`
5. `ready-issue-writer.ps1` 或 `issue-closeout.ps1`
6. `test-failure-classifier.ps1`
7. `local-commit-closeout.ps1 -DryRun`

运行态 `state.json`、`run.lock`、events、executor/reviewer 日志只保存在 `.codex-autopilot/`，不得提交为项目事实。正式事实仍写入 `docs/backlog/**`、`docs/iterations/**`、`docs/quality/**` 和本地 Git commit。

## 项目级触发与执行边界

- 触发短语、授权与不可绕过边界只读取 [`cgc-pms-autopilot-owner`](../skills/cgc-pms-autopilot-owner/SKILL.md)。本安装文档不定义兼容触发词。
- 调度、checkpoint、恢复、并行度、等待和超时只读取 [控制面策略](control-plane-policy.md)、配置与 Schema；不在安装说明复制动态值。
- 运行态与浏览器验收读取项目 runtime Skill；失败分类读取项目 CI Skill。
- 普通交互任务不因安装插件进入 Ready；生产、数据库、Git 和外部写入仍受项目授权门禁。
