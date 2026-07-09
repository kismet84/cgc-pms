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

1. `autopilot-checkpoint.ps1`
2. `ready-issue-writer.ps1` 或 `issue-closeout.ps1`
3. `test-failure-classifier.ps1`
4. `local-commit-closeout.ps1 -DryRun`

## 项目级触发协议

1. `启动预演`
   - 只做插件 dry-run，目标命令为 `plugins/cgc-pms-autopilot/scripts/autopilot-loop-runner.ps1 -DryRun -ReadyIssuePath docs/backlog/ready-issues.md`
   - 不启动下一任务、不提交、不 push
2. `启动迭代`
   - 进入连续迭代模式
   - 优先走插件 runner / checkpoint / classifier
   - 仍遵守主线程/子智能体边界、Ready 队列、stop/pause/enabled、A-F 分工、no push
3. `启动迭代-N`
   - `N` 为 1 到 50
   - 最多完成 N 个实施型 Ready Issue 后退出
   - dry-run、拆单、health gate、runtime refresh 不计入 N
4. `停止迭代`
   - 安全停止
   - 语义等价于旧 `停止自动迭代系统`
   - 设置停止标记并关闭 `enabled.flag`，不强杀当前任务

## Legacy 兼容

- `启动自动迭代系统`
- `启动连续自动迭代系统`
- `启动连续自动迭代系统-N`
- `停止自动迭代系统`

以上旧短语继续保留，但新会话默认优先使用 `启动预演`、`启动迭代`、`启动迭代-N`、`停止迭代`。
