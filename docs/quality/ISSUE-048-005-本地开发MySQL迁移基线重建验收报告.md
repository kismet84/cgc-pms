# ISSUE-048-005 本地开发 MySQL 迁移基线重建验收报告

结论：通过；阻塞：无；可合并：是；可推送：否（`autoPush=false`）。

## 根因与方案

- 原开发库停在旧 V203；当前 `flyway:validate` 检出 V188～V203 共16个校验和错位，另有 V181、V183、V204～V210 未应用。旧历史描述证明旧 V188～V203 实际对应当前重新编号后的 V195～V210，供应商当前 V189 等迁移没有执行。
- 直接 `flyway repair` 只会改写校验和，无法补执行同版本号下不同业务内容，因此会产生“校验通过但结构缺失”的假基线，本轮明确禁止。
- 在确认数据库仅暴露于 `127.0.0.1:3307`、容器为 `cgc-pms-mysql-dev` 且 `.codex-autopilot/ALLOW_TEST_DATA_RESET` 存在后，先停 backend、完整备份、独立恢复验真，再只重建本地 `cgc_pms`。
- 本项未修改迁移 SQL、业务代码、测试、前端、Docker 编排、密钥或其他 schema；未连接生产或远程数据库。

## 备份与回滚证据

- 原库逻辑备份包含 schema、数据、routines、triggers 和 events，大小524,349字节，SHA-256为 `3cd81335b6900d06e5b14eea701aee9f03ac4d8b3e3b8be3ce3c7fb496399ddf`。
- 备份在无持久卷的一次性 MySQL 8.0 容器中完整导入；恢复后读回149张表、198条成功迁移、旧末版本V203及3张抽样关键表，与原库一致。
- 已验真备份保留在本地忽略目录 `.agent-runtime/db-backups/ISSUE-048-005/pre-rebuild.sql`；一次性恢复容器已精确删除。
- 回滚方式：停止 backend，重新创建空 `cgc_pms`，导入该备份并重启；执行前必须再次核对备份哈希。

## 重建与验收证据

- 当前 master JAR 构建成功后才执行重建；Flyway 在空 `cgc_pms` 上成功应用207条迁移，末版本V210，执行失败0。
- 重建后共有184个表/视图；`sp_performance_evaluation`、`sp_supplier_return`、`sp_blacklist_record` 全部存在。
- Maven Flyway 11.7.2 `validate` 返回0；`FlywayMySqlSmokeTest` 1/1通过，0失败、0错误、0跳过。
- `cgc-pms-backend-dev` 健康为UP。开发登录成功后，`/supplier-sourcing` 页面加载正常，events、performance、returns三个GET均返回200；控制台错误0，业务写请求0。
- Ready lint、JSON解析、允许/禁止路径与 `git diff --check` 通过；Git 只回写治理文档和本报告。

## Reviewer 复核

- 结论：PASS；findings：无。
- 安全边界：目标由本地绑定端口、固定容器名和 reset sentinel 三重限定；操作只触及 `cgc_pms`，没有输出或修改凭据。
- 数据可逆：完整备份在独立 MySQL 中真实恢复并读回后才重建；哈希、大小和原始计数均固定，回滚不依赖当前新库。
- 正确性：没有 repair 或手工伪造历史；当前迁移按权威顺序 V1→V210 执行，并由 validate、历史表、关键表、后端与真实用户入口交叉验证。
- 清理：一次性恢复容器已删除；本地回滚备份按验收要求保留，未进入版本管理。

## 治理收口

- `PI-2026-07-17-05` 已实施回写；唯一运维项 `OPS-DEV-MYSQL-FLYWAY-DRIFT` 已关闭。
- 新增后续项：0
- 关闭后续项：1
- 后续项净变化：-1
- 最小回滚：使用已验真的完整备份恢复原本地库；Git 文档回退不能替代数据库回滚。

剩余风险：回滚备份包含本地开发测试数据，应按本机敏感数据管理且不得提交；生产发布、生产数据库和 push 均未执行。
