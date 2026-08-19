# 整改路线图（已执行）

## G1：立即止血——完成

1. `SEC-001`：服务端拒绝事件属性；列表预览空 sandbox；设计器不允许脚本；后端、前端、浏览器回归通过。
2. `OPS-001`：独立机器 Basic 身份、只读密码文件、prod 内联口令 fail-closed、UI 回环绑定与拒绝矩阵通过。
3. `API-001`：对象存储异常统一固定公开消息，内部诊断不再进入响应。

## G2：可靠性与部署——完成

1. `DEPLOY-001`：MySQL/JDBC 数据库名统一到 `MYSQL_DATABASE`，静态 contract 和 Compose 展开通过。
2. `OBS-001`：prod 日志目录挂独立持久卷。
3. `OBS-002`：通用审计增加结果计数与失败告警；关键审计继续使用现有强一致链。
4. `OBS-003`：删除重复线程池指标 binder，统一 Spring Boot 原生指标标签。

## G3：补证与质量——完成

1. `DEP-001`：前端 audit、固定摘要 Trivy 当前库 manifest 扫描与同 SHA 构建 JAR 供应链扫描通过。
2. `DR-001`：隔离恢复演练通过。
3. `PERF-001`：固定 JVM PDF 基准、Dashboard 查询预算与 bundle 预算通过。
4. `ARCH-001`：对照第94条职责证据关闭行数型误报。
5. `STYLE-001`：7 个告警文件清零，分页解析回归已修复。

## 验收与回滚

- 安全回滚必须同时处理服务端校验、iframe sandbox 与测试，禁止单层回退。
- 机器认证缺失时保持 401，不得回退匿名指标端点；密钥轮换按部署手册重建 backend/prometheus。
- Compose 回滚只恢复配置，不删除日志、Prometheus 或数据库卷。
- 审计计数器故障不得阻断普通业务；关键财务/收入命令仍由 `MandatoryAuditService` 保证。

## 正式承接与零悬空

- 原有载体 `AUDIT-PROMETHEUS-SCRAPE-AUTH` 已由实现覆盖并从 `current-issues.json` 移除。
- 新增后续项 0；关闭后续项 1；净变化 -1。
- 当前开放风险 0；零悬空通过。受保护 Git 交付已完成；发布和非本地环境未执行。
