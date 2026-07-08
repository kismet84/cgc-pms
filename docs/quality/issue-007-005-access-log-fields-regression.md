# ISSUE-007-005 访问日志 projectId/status/duration/exception 字段回归

完成日期：2026-07-09

## 目标

- 回归访问日志中的 `method`、`path`、`status`、`duration`、`exception` 字段。
- 在不引入新依赖、不扩展日志平台配置的前提下，尽量补齐 `projectId` 可追踪口径。
- 确认访问日志不泄露 Token、Cookie、请求体等敏感内容。

## 修改范围

- `backend/src/main/java/com/cgcpms/common/filter/TraceIdFilter.java`
  - 继续复用既有访问日志单点输出入口。
  - 访问日志字段补充 `projectId`、`exception`，并将 `durationMs` 统一回归为 `duration`。
  - `projectId` 解析顺序为：`request parameter projectId` -> `HandlerMapping` 路由变量 `projectId` -> `/projects/{id}` 路径片段兜底；无法稳定识别时记录 `-`。
- `backend/src/test/java/com/cgcpms/common/filter/TraceIdFilterLoggingTest.java`
  - 新增命中 Ready Issue 原验证通配符的最小回归测试。
  - 覆盖成功请求、异常请求、`projectId` 解析和敏感信息不泄露断言。

## 根因与分类

- 首轮失败分类：Ready Issue 配置问题已更正。
- 具体现象：`cd backend; .\mvnw.cmd "-Dtest=*Trace*Test,*Logging*Test" test` 的预检结果显示仓库内原本不存在命中 `*Trace*Test` / `*Logging*Test` 的访问日志回归测试。
- 处理方式：新增单个最小测试类 `TraceIdFilterLoggingTest`，直接命中原验证通配符，同时把断言放在 `TraceIdFilter` 这一处共享日志入口，避免在控制器层重复补丁。

## 验证记录

- 预检：
  - `rg -n "Trace|Logging" backend/src/test/java`
  - 结果：只有敏感字段掩码测试，无访问日志字段回归测试。
- 绿灯验证：
  - `cd backend; .\mvnw.cmd "-Dtest=*Trace*Test,*Logging*Test" test`
  - 结果：通过，`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`
  - 结果：通过。

## 日志边界

本轮自动化断言覆盖：

- 成功请求访问日志包含：
  - `method`
  - `path`
  - `projectId`
  - `status`
  - `duration`
  - `exception=-`
- 异常请求访问日志包含：
  - `projectId`
  - `status=500`
  - `exception=RuntimeException`
- 敏感信息不泄露：
  - 不输出 Authorization Token
  - 不输出 Cookie 值
  - 不输出请求体中的敏感字段值

本轮明确边界：

- `projectId` 仅在请求参数、路由变量名为 `projectId` 或 `/projects/{id}` 路径片段可识别时输出；其他业务路径若没有统一 `projectId` 上下文，仍记为 `-`。
- `exception` 当前记录为抛出异常的类型名，不记录异常消息，避免把敏感内容带入访问日志。

## 自审结论

PASS。

依据：

- 访问日志仍由既有 `TraceIdFilter` 单点输出，改动集中且影响面清晰。
- 原验证命令已恢复为真实可执行并通过。
- 自动化断言同时覆盖成功/异常请求和敏感信息不泄露边界。

## 结论

通过 / 非阻塞。

剩余风险：

- 对于既不携带 `projectId` 参数、也不走 `/projects/{id}` 或 `projectId` 路由变量的业务请求，访问日志仍无法自动推断项目归属；如后续要做到全链路稳定归因，需要单开 Issue 建统一项目上下文。
