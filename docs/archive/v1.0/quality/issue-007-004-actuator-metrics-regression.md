# ISSUE-007-004 接口性能与错误率监控指标回归

完成日期：2026-07-09

## 目标

- 回归本地可验证的接口耗时与错误率指标记录口径。
- 回归 JVM、数据库连接池、异步线程池等现有 Micrometer 指标注册是否可用。
- 在不接入外部监控平台的前提下，补齐项目内最小自动化断言。

## 修改范围

- `backend/src/test/java/com/cgcpms/config/ActuatorMetricsTest.java`
  - 新增命中 Ready Issue 验证通配符的回归测试类。
  - 覆盖 `HealthEndpoint` 注册、`http.server.requests` 成功/失败指标、JVM/Hikari/异步线程池指标存在性断言。
- `docs/quality/issue-007-004-actuator-metrics-regression.md`
  - 新增正式质量报告。
- `docs/iterations/iteration-2026-07-08-report.md`
  - 追加本轮 iteration 收口记录。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`
  - 将 ISSUE-007-004 从 Ready 收口为 Done。

## 根因与分类

- 首轮失败分类：Ready Issue 配置问题已更正。
- 具体现象：`cd backend; .\mvnw.cmd "-Dtest=*Metrics*Test,*Actuator*Test" test` 失败，原因是仓库内不存在匹配 `*Metrics*Test` / `*Actuator*Test` 的测试类，导致 Surefire 报 `No tests matching pattern ... were executed`。
- 处理方式：新增单个最小回归测试类 `ActuatorMetricsTest`，直接命中原验证通配符，不修改外部监控平台配置，不新增依赖。

## 验证记录

- 红灯验证：
  - `cd backend; .\mvnw.cmd "-Dtest=*Metrics*Test,*Actuator*Test" test`
  - 结果：失败，`No tests matching pattern "*Metrics*Test, *Actuator*Test" were executed!`
- 单类调试验证：
  - `cd backend; .\mvnw.cmd "-Dtest=ActuatorMetricsTest" test`
  - 结果：先后暴露 `spring-security-test` 依赖不存在、匿名 health URL 不适合作为本地 `MockMvc` 断言入口、Actuator Web 路由在该测试上下文下不经由业务路由链等问题；最终调整为复用现有 JWT 测试方式，并改测 `HealthEndpoint` 注册逻辑。
- 绿灯验证：
  - `cd backend; .\mvnw.cmd "-Dtest=*Metrics*Test,*Actuator*Test" test`
  - 结果：通过，`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`
  - 结果：通过。

## 监控边界

本轮自动化断言覆盖：

- 接口耗时 / 错误率：
  - `http.server.requests` 中至少存在一条 `GET + status=200` 指标；
  - `http.server.requests` 中至少存在一条 `GET + status=500` 指标。
- Actuator / 健康注册：
  - `HealthEndpoint` Bean 已注册且可返回健康状态。
- 基础运行指标：
  - `jvm.threads.live`
  - `hikaricp.connections.max`
  - `executor.completed{name=cgc.pms.async, executor=taskExecutor}`

未覆盖内容：

- Prometheus 抓取、外部告警平台、Grafana 面板等外部平台接入。
- 匿名访问 `/api/actuator/health` 的安全策略；本轮未修改 `auth/config`，因此不把该口径纳入通过依据。

## 自审结论

PASS。

依据：

- Ready Issue 指定验证命令已从“无匹配测试”恢复为真实通过。
- 回归断言全部基于本地 `SpringBootTest + MockMvc + MeterRegistry`，不依赖外部监控平台。
- 变更仅落在测试与正式文档，未扩大到前端、migration、deploy 或生产凭据。

## 结论

通过 / 非阻塞。

剩余风险：

- 本轮只验证了项目内可观测性基础口径，不包含 Prometheus、告警规则或可视化面板联调。
- `MockMvc` 场景下未以 URL 形式验通 `/api/actuator/health`，而是验 `HealthEndpoint` 注册逻辑；若后续要把匿名 health 暴露策略纳入验收，需要单开安全/运维范围 Issue 处理。
