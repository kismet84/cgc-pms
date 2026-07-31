# 全量审计：前端

## 结论

**已迁移范围通过；全量 V2 替代不通过。评分 78/100。**

## Legacy 验证

- ESLint：0 error、25 warnings。
- Vitest：131 files、732 tests 全部通过。
- type-check、build、bundle-size gate 全部通过。
- 最大 JS chunk `vendor-vxe-table` 457.14 KiB，在当前门禁内。

## Clean-room V2 验证

- Clean-room boundary：82 个 V2 文件 + 8 个共享契约文件通过。
- 路由账本：87 routes、73 view entries、65 unique views。
- ESLint：0 error、0 warning。
- Vitest：18 files、83 tests 全部通过。
- contract type-check、type-check、build、bundle-size gate 全部通过；最大 JS chunk 111.33 KiB。

## 风险

- `BIZ-001`（P1）：大量导航路由仍使用 `ShellPlaceholderPage`，对象详情另有 6 个显式占位路由；V2 不是完整生产替代入口。
- `OBS-001`（P2）：`frontend-admin/src/main.ts:55` 明确 TODO 接入 Sentry/日志上报，浏览器异常缺中央闭环。
- `FE-001`（P3）：Legacy 25 个 lint warning，主要为文档功能 Prettier 告警；不阻断构建。
