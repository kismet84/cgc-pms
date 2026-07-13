# Rerun Policy

命令首次失败后，先分类，再决定是否复跑。

## 固定流程

```text
first failure -> classify
transient suspected -> rerun once
same failure repeated -> repair or blocked
different failure -> collect stronger evidence
```

## 分类基线

1. `ECONNREFUSED`、端口未通、`actuator/health` 不通、`dev-login` 不通：`environment_prereq`
2. `ParserError`、参数拆分错误、脚本入口不存在、工具未加载：`tool_config`
3. 测试类/方法选择器不存在、Ready issue 验证命令失真：`ready_issue_config`
4. 可稳定复现的断言失败、编译错误与当前 diff 相关、越权或安全断言失败：`real_quality_or_security`
5. 编译失败但证据无法指向当前 diff、错误模式冲突、日志不充分：`unknown`

## 结构化输出约定

1. `category` 决定大类路由。
2. `subcategory` 决定更细的处理手势，例如 `powershell_parser_error`、`vite_proxy_stale_backend`、`test_selector_missing_or_invalid`。
3. `suggestedNextAction` 只返回短动作，如 `rerun_once`、`refresh_frontend_runtime`、`fix_ready_selector`、`open_repair_request`、`mark_blocked`。
4. `retryPolicy` 只返回有限集合：`no_retry`、`rerun_once`、`rerun_after_refresh`、`retry_after_ready_fix`、`manual_review_required`。

## 保守约束

1. Maven `testCompile`、代理抖动、启动竞态只按疑似瞬时问题处理一次，不允许无限复跑。
2. 第二次仍为同类环境前置错误时，转 runtime refresh、repair 或 blocked，不再机械重试。
3. `unknown` 优先于错误归因；证据不足时不要把失败硬判成业务代码问题。

## 跨 run 阶段恢复

1. 活动 Issue 存在 durable phase checkpoint 时，必须先校验 Ready 内容、baseCommit、worktree/branch、scope、diff 与 evidence；全部一致才允许恢复。
2. `IMPLEMENTED/VALIDATING` 从 validation 恢复，`VALIDATED/REVIEWING` 从 Reviewer 恢复，`REVIEWED/CLOSING` 从 closeout 恢复；不得重新派发 implementation。
3. Reviewer `tool_config` 仅重试同一 diff 的 Reviewer；累计两次仍失败进入 `PAUSED/REVIEW_TOOL_BLOCKED`，不转 repair。若人工独立 Reviewer 写入绑定同一 Issue/diff 的结构化 PASS，应消费该证据并从 closeout 继续。
4. closeout commit 必须先写 checkpoint 再快进合并；合并后中断可用 worktree 中归一化后的 Done 合同、closeout commit 祖先关系和原始 Ready 哈希恢复 final registration。
5. closeout ledger、state、候选效率 shadow 与适用的知识图谱游标必须读回成功后才退役 checkpoint。
6. 任一绑定证据变化进入 `QUARANTINE` 并保留 worktree；没有 checkpoint 的残留提交也只允许人工证据恢复或显式放弃。
