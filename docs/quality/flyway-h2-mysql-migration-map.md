# Flyway H2/MySQL Migration Map

编写日期：2026-06-25

## 目的

H2 测试迁移允许为集成测试保留专用种子数据和兼容脚本，但上线门禁必须以 MySQL 迁移链路为准。历史迁移一旦进入仓库，不应通过重命名或改内容来强行对齐版本号，避免破坏 Flyway checksum。

## 当前差异

| 生产 MySQL 迁移 | H2 测试迁移 | 说明 |
| --- | --- | --- |
| `db/migration/V90__create_operation_audit_log.sql` | `db/migration-h2/V91__create_operation_audit_log.sql` | H2 的 `V90` 被 `V90__h2_integration_test_seed_data.sql` 占用，用于集成测试种子数据。 |

## 维护规则

- 新增生产迁移时，优先为 H2 提供同版本兼容脚本。
- 如 H2 需要测试专用脚本导致版本偏移，必须在本文补充映射。
- 发布前必须运行 MySQL 集成测试，不以 H2 迁移通过替代生产迁移验证。
- 已合并或已执行的迁移不得随意重命名、重排或改写内容。

## 验证命令

```bash
cd backend && ./mvnw test -Dspring.profiles.active=test -Djasypt.encryptor.password=dev-jasypt-key
```
