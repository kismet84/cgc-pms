# 风险登记表（整改后）

本表保留原审计严重度，状态表示 2026-08-20 本地整改结果。生产/目标环境不存在，任何“通过”只代表仓库与本地证据。

| ID | 原等级 | 最终状态 | 根因处置 | 关键复验证据 | 回归/边界 |
| --- | --- | --- | --- | --- | --- |
| SEC-001 | P1 | 本轮修复并复验 | 服务端拒绝事件属性；两个 `srcdoc` iframe 禁止脚本 | 后端参数化测试、前端 sandbox 断言、真实 DOM/console | 设计器仅保留 `allow-same-origin` 以支持选区联动，不含 `allow-scripts` |
| OPS-001 | P1 | 本轮修复并复验 | 独立 `MONITORING` Basic 安全链；只读密码文件；UI 回环绑定 | 正确 Basic 200；空/错/JWT 401；密码文件/fail-closed 单测；Compose contract | 本地未持续启动 Prometheus 告警接收链 |
| API-001 | P2 | 本轮修复并复验 | presign 对外固定错误码/消息 | 注入内部 endpoint/secret 后无响应泄漏；文件服务回归 | 详细异常仅保留服务端日志 |
| OBS-001 | P2 | 本轮修复并复验 | prod 文件日志挂独立持久卷 | 部署契约检查 `/var/log/cgc-pms` 与 `backend-logs` | 外部日志平台不在当前本地范围 |
| DEPLOY-001 | P2 | 本轮修复并复验 | MySQL 初始化与 JDBC 共用 `MYSQL_DATABASE` | 静态契约与 Compose overlay 展开通过 | 未执行非本地 fresh deploy |
| OBS-002 | P2 | 本轮修复并复验 | 通用审计增加 attempt/success/failure 指标与失败告警；关键命令继续强审计 | 故障注入计数 failure 且不传播；强审计现有分母/hash 证据 | 不为普通操作引入第二套 outbox |
| OBS-003 | P2 | 本轮发现、修复并复验 | 删除与 Spring Boot 自动线程池观测重复的 Micrometer binder | Prometheus registry 无同名异标签告警；`executor.completed{name="taskExecutor"}` 存在 | 保留 Boot 原生观测，不维护第二套标签契约 |
| DEP-001 | P2 | 本轮补证并关闭 | 执行固定摘要 Trivy 当前库 manifest 扫描，并由同 SHA CI 扫描构建 JAR | backend manifest HIGH/CRITICAL 0；前端 audit 0；`supply-chain-security` 通过 | 后续代码或依赖变化必须重新扫描 |
| DR-001 | P2 | 本轮补证并关闭 | 隔离执行现有恢复演练 | MySQL 行、MinIO 对象、SHA、交叉引用通过，23.9 秒 | 未触碰当前开发数据库/桶 |
| PERF-001 | P3 | 本轮补证并关闭 | 固定 JVM PDF 基准 + 查询预算 + bundle 预算 | 20/120/200 页各 30 次无失败；Dashboard 4 项预算通过 | 不是生产 SLA 或真实容量签认 |
| ARCH-001 | P3 | 证据不足/无明确价值而关闭 | 对照第94条职责审查，行数不等于多职责；保留事务/锁/补偿门面 | 第94条职责拆分报告、全量测试/构建 | 出现职责混杂或冲突证据时重新登记 |
| STYLE-001 | P3 | 本轮修复并复验 | 7 个告警文件最小格式化；分页表达式改具名处理函数 | lint 0/0、644 单测、type/build | 未格式化无关文件 |

## 统计

| 口径 | P0 | P1 | P2 | P3 | 合计 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 原始发现 | 0 | 2 | 6 | 3 | 11 |
| 整改中新发现 | 0 | 0 | 1 | 0 | 1 |
| 当前开放 | 0 | 0 | 0 | 0 | 0 |

最终处置：原 11 项中，7 项“本轮修复并复验”，3 项“本轮补证并关闭”，1 项“证据不足/无明确价值而关闭”；整改中新发现的 `OBS-003` 同轮修复。12 项均只有一个最终处置，不存在待承接项。`TEST-001` 继续并入 `SEC-001`，不重复计数。

## 零悬空

- 新增后续项：0。
- 关闭后续项：1（`AUDIT-PROMETHEUS-SCRAPE-AUTH` 已由 `OPS-001` 实现覆盖，并已从 Backlog 去重移除）。
- 后续项净变化：-1。
- 无载体遗留项：0。
- 零悬空裁决：通过；受保护 Git 交付已完成，生产/目标环境不存在且不在本轮范围。
