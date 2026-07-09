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
