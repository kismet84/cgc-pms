# ISSUE-008-027 供应商评分排名真实渲染验收治理

## 结论

- 结论：通过
- 阻塞：无
- 是否可上线：可上线
- 失败分类或非失败分类：非失败分类；采购驾驶舱供应商交期表现排名已补齐组件级真实渲染与降级态验收证据

## 范围

- `frontend-admin/src/pages/dashboard/components/DashboardPurchaseView.vue`
- `frontend-admin/src/pages/dashboard/__tests__/DashboardPurchaseView.test.ts`

本轮只验证并收敛现有 `supplierScores` 前端可见性，不新增综合评分模型，不扩展后端契约，不新增询价、比价、定标、黑名单或补货页面。

## 验收证据

- `pnpm exec vitest run src/pages/dashboard/__tests__/DashboardPurchaseView.test.ts`：通过，`3/3`。
- `pnpm type-check`：通过。
- `pnpm build`：通过。
- `git diff --check`：通过。
- E 审查：PASS。

## 覆盖点

- 有数据时展示供应商、订单数、逾期数、交期达成率和表现评分。
- 空数据时展示 `暂无供应商采购订单交期表现数据`。
- 零值或字段缺失时不出现 `NaN`、`-%` 或误导性综合评级文案。
- 页面仍只消费后端 `supplierScores` payload，不自行重算评分。

## 剩余风险

- 当前证据为组件级 mount 测试，不是浏览器 E2E；符合本 Ready 边界，非阻塞。
