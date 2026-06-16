# task-018 Implementation Report

## Status: complete

## Summary
完成了审批管理、预警中心、组织架构、基础数据和系统设置共 10 个页面的新 UI 语言迁移。修复了批量改写遗留的 3 类模板问题（多余的 `</div>`、`<template #extra>` 和 `</div>` 溢出），补齐了 3 个未转换的 system 页面。

## Changed Files
1. `frontend-admin/src/pages/approval/todo.vue` - 待办审批 Ledger List
2. `frontend-admin/src/pages/approval/detail.vue` - 审批详情 Detail Page（修复 `<template #tags>` 遗留）
3. `frontend-admin/src/pages/alert/index.vue` - 预警中心（修复 `<template #extra>` 遗留）
4. `frontend-admin/src/pages/org/index.vue` - 组织架构 Tree+Table
5. `frontend-admin/src/pages/material/dictionary.vue` - 材料字典（修复多余的 `</div>` 和 `<template #extra>`）
6. `frontend-admin/src/pages/system/dict/index.vue` - 字典管理
7. `frontend-admin/src/pages/system/users/index.vue` - 用户管理（新转换）
8. `frontend-admin/src/pages/system/data/index.vue` - 数据管理（新转换）
9. `frontend-admin/src/pages/system/roles/index.vue` - 角色管理（新转换）
10. `frontend-admin/src/pages/settings/index.vue` - 设置

## Fixes Applied
- **dictionary.vue**: 删除了提前关闭根 div 的 `</div>`（L216），将 `<template #extra>` 按钮移入 `pt-head-actions`
- **alert/index.vue**: 同样将 `<template #extra>` 按钮移入 `pt-head-actions`
- **approval/detail.vue**: 将 `<template #tags>` 状态标签移入 `pt-head-actions`，删除多余 `</div>`
- **users/roles/data/index.vue**: 从旧内联样式迁移到 `project-target-redesign` + `pt-page-head` + `pt-filter-surface` + `pt-table-panel` + `pt-pagination` + `pt-link` 共享 UI 类名

## Verification
- pnpm build: PASS
- pnpm vitest run: 26/27 test files passed, 135/136 tests passed
  - 1 pre-existing failure in ContractLedgerPage.test.ts (unrelated to task-018)

## Known Risks
- system 页面沿用紧凑密度，未添加 KPI strip 和 AnalysisRail（符合设计要求）
- 部分旧页面的 `<style scoped>` 块保留为空白 `<style scoped></style>`，体积影响可忽略
