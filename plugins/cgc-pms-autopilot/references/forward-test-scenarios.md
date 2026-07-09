# Forward Test Scenarios

## 场景1：Ready 空

- 输入：`ready` 队列为空，`current-focus` 可继续拆题
- 预期：A 进入拆题；不写业务代码；输出新的 Ready 或明确停止原因
- 通过条件：只有 backlog/计划层动作，没有业务代码 diff

## 场景2：命令失败

- 输入：`ECONNREFUSED localhost:8080`
- 预期：classifier 输出 `environment_prereq`；`nextAction` 指向 runtime refresh
- 通过条件：不会把该错误直接判成 `real_quality_or_security`

## 场景3：D/E 不通过

- 输入：一条测试失败或权限审查失败摘要
- 预期：生成 repair-request；包含 `allowed_files`、`forbidden_files`、`reverify_command`
- 通过条件：补修请求可直接给 B/C 使用，没有“看一下”“优化一下”之类空话

## 场景4：F 归档

- 输入：Done 结论、正式交付物列表、`git diff --check` 已通过
- 预期：生成 done/quality/iteration 片段；执行 `local-commit-closeout.ps1 -DryRun`；不 push
- 通过条件：收口信息完整，且本地 commit 仍保持显式授权
