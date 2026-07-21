# 全量审计：代码质量

## 结论

**通过，存在维护债务。评分 76/100。** 编译、类型、Lint 主门禁均通过，无确认的高风险代码质量缺陷。

## 发现

- `CODE-001`（P3）：`PayApplicationService` 707 行；源码 TODO 建议拆为 Query/Write/Assembler。应按行为不变的小步重构，不建立新框架。
- `CODE-002`（P3）：OpenHTMLToPDF 废弃 API 与多处 `@MockBean` 废弃告警；需在依赖升级前处理。
- `FE-001`（P3）：Legacy 25 个 ESLint warning，主要为格式告警；V2 为 0 warning。
- `DOC-001`（P2）：README 仍写迁移至 V211，当前为 B215 + V216—V218；快速启动文档未明确 V2 5174。
- `GOV-001`（P2）：`blocked-issues.md` 仍把已被当前 master/CI 关闭的旧 `ISSUE-037-021` 列为 P0；项目地图部分 CI 状态也落后于 PR #358。

## 原则

优先修正权威载体漂移，再处理低风险重构；禁止以大范围格式化或架构重写掩盖业务变更。
