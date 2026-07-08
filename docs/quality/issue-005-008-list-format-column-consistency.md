# ISSUE-005-008 核心列表列宽/固定列/金额日期格式统一回归

完成日期：2026-07-09

## 结论

通过。

本轮在允许范围内补齐了前端共享列表 preset，统一了代表性核心列表页的金额格式、日期列宽、状态列宽和操作列固定策略；未修改后端接口语义、详情页行为或业务字段定义。

## 修改范围

- `frontend-admin/src/composables/listTablePresets.ts`
  - 新增共享金额格式与列表列 preset。
  - 统一金额列 `128`、日期列 `112`、时间列 `160`、状态列 `108`、操作列 `76/fixed=right`。
- `frontend-admin/src/pages/project/index.vue`
  - 移除本地金额格式函数，改用共享金额格式。
  - 项目列表金额列、状态列、操作列改用共享 preset。
- `frontend-admin/src/pages/contract/composables/useContractLedger.ts`
  - 移除本地金额格式函数，改用共享金额格式。
  - 合同列表金额列、签订日期列、状态列、操作列改用共享 preset。
- `frontend-admin/src/pages/payment/pageConfig.ts`、`frontend-admin/src/pages/payment/index.vue`
  - 付款列表金额列、状态列、操作列改用共享 preset。
  - 列表及摘要金额格式改用共享万金额式。
- `frontend-admin/src/pages/settlement/pageConfig.ts`、`frontend-admin/src/pages/settlement/index.vue`
  - 结算列表金额列、状态列、创建时间列、操作列改用共享 preset。
  - 列表及摘要金额格式改用共享万金额式。
- `frontend-admin/src/pages/receipt/composables/useReceiptList.ts`
  - 验收列表金额格式、日期列、状态列、操作列改用共享 preset。
- `frontend-admin/src/pages/requisition/composables/useRequisitionList.ts`
  - 领料列表金额格式、日期列、状态列、操作列改用共享 preset。
- `frontend-admin/src/pages/invoice/composables/useInvoiceList.ts`
  - 发票列表金额格式改用共享货币格式。
  - 发票金额、税额、开票日期、创建时间、核验状态、操作列改用共享 preset。
- `frontend-admin/src/pages/__tests__/list-column-format-consistency.test.ts`
  - 新增源码级一致性回归测试，覆盖共享 helper 接入、局部重复格式函数移除和代表性列表列配置统一。

## 验收证据

- `cd frontend-admin; pnpm exec vitest run src/pages/__tests__/list-column-format-consistency.test.ts`：先失败后通过；最终 `1` 个文件 `3` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 统一边界

- 金额格式：
  - `project / contract / payment / settlement / receipt / requisition` 统一复用万金额式。
  - `invoice` 统一复用元金额式。
- 列宽与固定列：
  - 统一收敛为金额列 `128`、日期列 `112`、时间列 `160`、状态列 `108`、操作列 `76`。
  - 代表性核心列表的操作列统一固定在右侧，减少窄屏横向滚动时的操作遮挡。
- 业务语义：
  - 仅统一展示层 helper 与列配置，不改状态枚举、金额单位来源、日期字段来源或接口参数。
  - `project` 的计划工期、`invoice` 的税率等非统一项保持原业务口径。

## 剩余风险

- 本轮没有做真实浏览器窄屏验收，关于“固定列不遮挡主要操作”的结论基于列配置、源码检查、类型检查和构建结果。
- 仍有部分非本轮代表性页面保留本地金额展示 helper；如需扩展到所有列表页，应另立任务做全仓统一替换。
