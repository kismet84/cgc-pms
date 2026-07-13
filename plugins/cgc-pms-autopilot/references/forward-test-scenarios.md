# Forward Test Scenarios

## 场景1：Ready 空

- 输入：`ready` 队列为空，知识图谱健康、Git 游标覆盖当前 HEAD，且存在合格 `OPEN` / `OBSERVATION` 叶子问题
- 预期：A 从有界图谱查询发现存量候选，再按 `sourceRefs` 和当前分支事实核实；保留 `[stock:<issueKey>]`；不写业务代码；输出新的 Ready 或明确停止原因
- 通过条件：只有 backlog/计划层动作，没有业务代码 diff

## 场景1a：存量问题过滤与后备顺序

- 输入：同时存在生产 `RELEASE_GATE`、`FROZEN`、`NEEDS_CONFIRMATION`、聚合父问题、合格存量叶子问题、当前 focus 阻塞和 Ad-hoc Candidate
- 预期：只选择合格存量叶子问题；其余存量项不自动拆 Ready。合格存量问题耗尽后，才按当前 focus 可解除阻塞 → Ad-hoc Candidate → 产品情报刷新推进
- 通过条件：长期增强计划不能直接生成 Ready；没有 Ready Planner 时正式补货 fail-close，不生成宽范围通用草稿

## 场景1c：图谱异常或游标过期

- 输入：Neo4j 不可用，或 Git 游标落后当前 HEAD 且单次 `autopilot-refill` 增量刷新后仍不一致
- 预期：分别输出 `STOP_KG_REFILL_UNAVAILABLE` 或 `STOP_KG_REFILL_STALE`；不读取 `current-issues.json` 静默补货
- 通过条件：不创建 Ready、不创建 issue worktree、不启动 executor；失败分类可区分 `environment_prereq`、`tool_config` 与数据一致性 `quality_security`

## 场景1d：Ready 范围契约矛盾

- 输入：精确允许文件被禁止目录覆盖，或允许子树被禁止父树完全覆盖
- 预期：ready lint 返回 `READY_SCOPE_CONTRADICTION` / `ready_issue_config`
- 通过条件：executor/worktree 均未创建；宽允许目录配合更窄禁止子目录仍通过前置检查，并继续受运行时 forbidden 优先门禁保护

## 场景1b：无 Ready 证据直接调用 runner

- 输入：`autopilot-loop-runner.ps1 -DryRun`
- 预期：select gate 阻断执行，要求传 `-ReadyIssuePath` 或显式 `-AllowSyntheticIssue`
- 通过条件：不进入 classify、repair、closeout、local-commit

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

## 场景5：loop runner classify dry-run

- 输入：`-Scenario classify -ErrorText "ECONNREFUSED 172.19.0.8:8080"`
- 预期：runner 经 `observe -> classify -> repair-request -> next` 输出 `environment_prereq / vite_proxy_stale_backend`
- 通过条件：`suggestedNextAction=refresh_frontend_runtime`，`retryPolicy=rerun_after_refresh`

## 场景6：loop runner closeout dry-run

- 输入：`-Scenario closeout -IssueId READY-33-2-M5`
- 预期：runner 生成 closeout 预览、local commit dry-run 结果和 `next=wait_for_owner_decision`
- 通过条件：不真实 commit，不 push，且能说明正式交付物与剩余风险口径

## 场景7：DryRun 高优先级

- 输入：`-DryRun -EnableLocalCommit -Scenario closeout`
- 预期：仍保持 dry-run，不触发真实 commit
- 通过条件：输出 `dryRun=true`，且 closeout 分支最多只到 dry-run 预览
