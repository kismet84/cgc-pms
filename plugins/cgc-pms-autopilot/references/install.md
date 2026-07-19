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

## 项目级触发协议

1. `启动预演`
   - 只做插件 dry-run，目标命令为 `plugins/cgc-pms-autopilot/scripts/autopilot-loop-runner.ps1 -DryRun -ReadyIssuePath docs/backlog/ready-issues.md`
   - 不启动下一任务、不提交、不 push
2. `启动迭代`
   - 进入连续迭代模式
   - 优先走插件 runner / checkpoint / classifier
   - A-F 作为职责检查表由主线程动态覆盖；仍遵守 Ready 队列、stop/pause/enabled、no push
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

## 连续迭代硬边界

- 普通交互任务获用户明确授权后不强制进入 Ready；AutoPilot 只实施合格 Ready Issue。
- A-F 是主线程职责检查表；D 的裁决必需验证证据与 E 的适用风险审查证据不可省略。
- 状态变更前核对 branch/status；AutoPilot 按开始、选题、改代码、验证、自动合并、报告收口等 checkpoint 检查 stop/pause/enabled。
- 运行态或浏览器验收先过 health gate；失败先分类，环境未就绪先刷新并等待 180 秒复验。
- 每轮最多并行 3 个完全无关联且无代码关联的 Ready Issue，不能证明无关联时串行。
- 测试数据重置必须同时满足 dev/test/demo、host 为 localhost/127.0.0.1、存在 `ALLOW_TEST_DATA_RESET` marker。
- `autoPush=false` / `no push` 禁止自动 push；显式 push 必须获得用户授权并通过其他门禁。
- 收口需通过对应验证与 `git diff --check`、更新 iteration/backlog 并复查 flag；Ready 为空时先从健康且 HEAD 游标新鲜的知识图谱发现合格存量问题并按来源核实，`current-issues.json` 仅作为正式写回源。图谱异常时安全停止，不静默回退文件选题；存量问题耗尽后再处理当前 focus/阶段可解除阻塞。
- Ready allow/forbid 完全覆盖矛盾在 executor/worktree 前归类为 `ready_issue_config`；运行时 forbidden 优先门禁继续作为最后一道安全边界。
- 不自动发布生产，不连接生产数据库，不删除仓库外文件。
