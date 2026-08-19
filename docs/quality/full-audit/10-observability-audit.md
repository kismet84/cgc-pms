# 阶段 11：日志、监控与审计轨迹

## 已确认能力

- `TraceIdFilter.java:45-83` 生成/传递 traceId 与 requestId，访问日志含方法、路径、项目、用户、租户、状态、时长、异常类型和客户端 IP。
- `logback-spring.xml:20-113` 的 prod 日志为 JSON，包含字段/值脱敏；文件按 100 MB、30 天、总计 3 GB 轮转。
- `@AuditedOperation` 广泛覆盖登录、合同、付款、现金、工作流、文件、导出及关键业务命令。
- 财务与收入关键命令使用 `MandatoryAuditService`，审计事件与期望分母同事务写入，带规范化 payload hash 与完整性检查。
- 工作流另有事务内 `wf_record`，付款链有来源、支付、现金与审计事实。

## OBS-001（已修复）

- 等级/状态：原 P2，2026-08-20 本地配置契约通过。
- 证据：后端 prod 将文件写到 `/var/log/cgc-pms/application.log`（`logback-spring.xml:59-67`）；`docker-compose.prod.yml:280-282` 只挂载 heapdump，不挂载 `/var/log/cgc-pms`；仓库未配置日志采集器。
- 影响：容器重建后文件日志丢失；若宿主未采集 stdout，故障与取证证据无法跨容器保留。
- 现有载体：`A-10` 涵盖外部监控日志平台方向，但未精确承接本部署持久化缺口，标记待承接。
- 验收：明确 stdout 集中采集或只读日志卷方案；重建容器后可按 traceId 检索重建前日志；验证轮转、脱敏、容量上限和访问控制。

实现：prod backend 将 `/var/log/cgc-pms` 挂载至独立 `backend-logs` 卷；保留现有 JSON stdout 与文件轮转。当前只有本地环境，未擅自引入外部采集平台；卷存在性和挂载路径已纳入部署静态契约。

## OBS-002（已修复）

通用操作审计保留 best-effort，避免审计存储故障反向破坏普通业务；新增 `operation.audit.persistence` 的 attempt/success/failure 计数与失败告警，故障注入证明失败被计量且不向业务传播。关键财务/收入命令继续使用既有同事务 `MandatoryAuditService`、期望分母与 hash 校验，不重复建设 outbox。

`OPS-001` 已闭合机器抓取身份与密码文件链路；静态契约、认证拒绝矩阵和告警配置均通过。本地未持续启动 Prometheus 栈，故不声称外部告警接收端已验收。

## OBS-003（整改中新发现并修复）

全量测试中的 Prometheus registry 告警证明 `taskExecutor` 同时被 Spring Boot 自动观测和 `AsyncConfig` 手工绑定；两者复用 `executor.completed` 名称却使用不同标签集合，导致 Prometheus 拒绝后一组注册。整改删除重复手工 binder，统一使用 Boot 原生 `executor.completed{name="taskExecutor"}`；`AsyncConfigTest` 与 Actuator 集成测试锁定该契约，复验不再出现同名异标签告警。
