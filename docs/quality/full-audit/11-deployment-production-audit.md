# 阶段 12：部署、配置与生产契约审计

## 十问回答

1. 全新环境部署：数据库名已统一为单一参数，Compose 展开与静态契约通过；未动态部署非本地环境。
2. 手工步骤：需准备 Secret、TLS、镜像 digest、外部 endpoint 与允许来源，均在手册说明。
3. 数据库迁移：Spring/Flyway 自动执行；CI 有 fresh baseline 和 upgrade 测试。
4. 应用回滚：镜像 digest 固定，手册要求保留上一稳定制品；本轮未演练。
5. 数据库升级恢复：Flyway 只前进，依赖升级前备份和反向修复脚本；本轮未演练。
6. 备份方案：MySQL + MinIO 原子批次、校验、保留与调度脚本存在。
7. 恢复验证：隔离 MySQL/MinIO 恢复演练已通过，未触碰当前开发数据库。
8. 健康与告警：容器健康检查、机器认证抓取配置和 Prometheus 规则闭合；外部接收端不在本地范围。
9. 单点：Compose 为单 MySQL、单 Redis、单 MinIO、单后端/前端，属于单机本地部署契约；当前仅本地环境，不据此声明生产高可用。
10. 最低上线要求：仓库与本地就绪门禁通过；当前项目规则明确无生产/目标环境，因此不产生上线许可。

## DEPLOY-001：自定义数据库名与 JDBC URL 不一致（已修复）

- 等级/状态：原 P2，2026-08-20 静态契约通过。
- 证据：`docker-compose.prod.yml:114` 允许 `MYSQL_DATABASE` 覆盖；`docker-compose.prod.yml:247` 的 JDBC URL 固定为 `/cgc_pms`。
- 触发：设置 `MYSQL_DATABASE` 为非默认值。
- 影响：MySQL 初始化目标库与后端连接库不一致，fresh deploy 启动失败或连到非预期库。
- 修复/验收：URL 使用同一变量并在 preflight 校验；Compose contract 测试以自定义库名展开配置，后端成功迁移并通过 health。

实现：MySQL 初始化和 backend JDBC URL 均使用 `${MYSQL_DATABASE:-cgc_pms}`；运行部署契约检查单一事实源，Compose overlay `config --quiet` 通过。当前未新增数据库、未执行迁移或重置。

## DR-001：本轮恢复证据缺失（已关闭）

- 等级/状态：原 P2，2026-08-20 隔离演练通过。
- 事实：`scripts/ci/test-backup-restore-drill.ps1` 可隔离创建 MySQL/MinIO 并校验行、对象 hash 和引用；执行会改变 Docker 运行环境，超出本轮授权，故未运行。
- 验收：另获授权后在本地隔离容器执行，保存退出码和 JSON 结果；不覆盖现有数据库/桶。

结果：`test-backup-restore-drill.ps1` 在隔离容器验证 MySQL 行 1、MinIO 对象 1、SHA-256 与交叉引用，退出 0，耗时 23.9 秒；临时资源由脚本清理。

## 边界

本报告只评价仓库与本地证据。不把缺少非本地环境测试列为阻塞，不声明生产部署、生产备份或上线验收已通过。
