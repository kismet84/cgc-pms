# 历史 H2 初始化脚本归档说明

本目录保存曾位于 `backend/src/main/resources/db/h2/` 的历史 `.bak` 初始化脚本，仅用于追溯旧版本结构，不参与任何运行时加载、Flyway 迁移或测试建库。

- 权威结构来源：`backend/src/main/resources/db/migration/` 与 `backend/src/main/resources/db/migration-h2/`。
- 禁止从本目录恢复或复制 schema 到运行资源目录。
- 如需核对历史差异，只允许只读比较；任何结构修复必须通过新的、MySQL/H2 对称的 Flyway migration 实施。
- 归档原因：关闭 `DBA-P1-016`，避免旧表定义被静态扫描、IDE 或人工排障误认为当前结构。
