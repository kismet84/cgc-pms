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
