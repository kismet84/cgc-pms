# ISSUE-005-002 项目与合同列表页生产化补强

## 结论

通过，阻塞：无。

本轮仅在项目列表与合同列表允许范围内补齐列表查询条件回显、分页参数和 URL 参数保留行为；保留既有服务端分页、loading、空态、异常提示、详情、编辑、删除入口。

## 修改范围

- `frontend-admin/src/pages/project/index.vue`
  - 从 URL query 恢复 `keyword`、`projectType`、`status`、`pageNo`、`pageSize`。
  - 列表拉取前同步当前查询条件和分页到 URL query。
- `frontend-admin/src/pages/contract/composables/useContractLedger.ts`
  - 从 URL query 恢复 `keyword`、`projectId`、`contractType`、`contractStatus`、`startDate`、`endDate`、`pageNo`、`pageSize`。
  - 列表拉取前同步当前查询条件、签订日期范围和分页到 URL query。
- `frontend-admin/src/pages/project/__tests__/ProjectLedgerProduction.test.ts`
  - 增加项目列表 URL 参数恢复与保留的源代码守卫。
- `frontend-admin/src/pages/contract/__tests__/ContractLedgerPage.test.ts`
  - 增加合同列表 URL 参数恢复与保留的源代码守卫。

## 验收证据

- `cd frontend-admin; pnpm exec vitest run src/pages/project/__tests__/ProjectLedgerProduction.test.ts src/pages/project/__tests__/ProjectNav.test.ts src/pages/contract/__tests__/ContractLedgerPage.test.ts src/pages/contract/__tests__/ContractFormPage.test.ts src/pages/contract/__tests__/useContractLedger-ui-consistency.test.ts`
  - 通过，`5` 个测试文件、`21` 条测试全部通过。
- `cd frontend-admin; pnpm type-check`
  - 通过，`vue-tsc --noEmit` 无错误。
- `git diff --check`
  - 通过。

## 失败分类

首轮 Vitest 失败归类为测试守卫配置问题：项目页仍保持先加载项目类型字典再首刷列表，但新增 URL 恢复逻辑位于 `onMounted` 开头，旧正则只允许 `fetchDictData(PROJECT_TYPE_DICT)` 紧跟 `onMounted`。本轮已最小修正测试守卫表达式，业务行为未因此改变。

## 剩余风险

- 本轮未做真实浏览器验收，结论基于指定 Vitest、类型检查和代码审查。
- 本轮未跑全量前端测试，结论限于 `ISSUE-005-002` 指定项目与合同列表生产化范围。
- URL query 同步采用既有 Vue Router `replace` 行为，不改变后端分页接口契约。
