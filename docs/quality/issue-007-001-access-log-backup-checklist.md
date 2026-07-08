# ISSUE-007-001 访问日志上下文与备份清单补强

完成日期：2026-07-08

## 目标

- 仅补强访问日志中的 `traceId/requestId` 与基础请求上下文。
- 补齐最小备份范围、恢复入口和恢复演练清单。
- 不接入外部监控平台，不修改生产凭据，不修改数据库迁移。

## 修改范围

- `backend/src/main/java/com/cgcpms/common/filter/TraceIdFilter.java`
  - 响应头补充 `X-Request-Id`。
  - MDC 补充 `requestId`。
  - 每个请求输出 `HTTP_ACCESS` 日志，字段包含 `traceId`、`requestId`、`method`、`path`、`status`、`durationMs`、`clientIp`。
- `docs/10-部署运维手册.md`
  - 增加访问日志采集字段说明。
  - 增加备份范围、恢复入口和恢复演练清单。

## 验证记录

- 原 Ready Issue 验证命令：`cd backend; .\mvnw.cmd "-Dtest=LoggingConfigTest" test`
- 预检结果：`LoggingConfigTest` 不存在，且 `backend/src/test/**` 不在本 Issue 允许修改范围内。
- 等价替换命令：`cd backend; .\mvnw.cmd "-DskipTests" test`
  - 结果：通过，后端主代码与测试代码编译成功，`BUILD SUCCESS`。
- `git diff --check`
  - 结果：通过，仅提示 `docs/10-部署运维手册.md` 后续 Git 触碰时会 LF 转 CRLF。

## 自审结论

PASS。

依据：
- 访问日志上下文字段由既有 `TraceIdFilter` 单点生成和输出，未新增重复过滤器。
- 文档清单覆盖 MySQL、MinIO、生产 `.env`/证书、部署配置与镜像标签。
- 未触碰 `frontend-admin/**`、Flyway migration、生产凭据、外部监控平台配置或 `.codex-autopilot/**`。

## 结论

通过 / 非阻塞。

剩余风险：
- 本轮因 Ready Issue 指定的 `LoggingConfigTest` 不存在且测试目录不在允许修改范围内，未新增日志字段单元测试；结论以编译门禁、代码审查和文档清单为准。
- 本轮不接入外部日志平台，生产采集规则仍需后续在实际日志平台配置。
