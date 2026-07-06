# 主线22：采购库存模块生产级增强质量验收报告

## 最终结论

- 结论：通过
- 阻塞 / 非阻塞：非阻塞
- 可上线：可上线（限定第 22 条主线冻结范围）
- 口径边界：本报告只覆盖采购申请、采购订单、库存台账的桌面端生产级增强验收，不覆盖创建 / 编辑 / 提交审批等写链路，不覆盖 UI 视觉和移动端验收。

## 变更范围

当前工作区与主线 22 直接相关的交付文件如下：

1. 后端实现 / 测试 / migration
   - `backend/src/main/java/com/cgcpms/inventory/service/MatStockService.java`
   - `backend/src/test/java/com/cgcpms/inventory/MatStockServiceTest.java`
   - `backend/src/main/java/com/cgcpms/common/migration/V131__fix_mat_stock_active_unique_constraint.java`
   - `backend/src/main/resources/db/migration/V131__fix_mat_stock_active_unique_constraint.sql`
2. 前端页面 / 组合式逻辑 / 测试
   - `frontend-admin/src/pages/inventory/purchase-request.vue`
   - `frontend-admin/src/pages/purchase/order.vue`
   - `frontend-admin/src/pages/inventory/composables/useStockLedger.ts`
   - `frontend-admin/src/pages/inventory/__tests__/purchase-request.test.ts`
   - `frontend-admin/src/pages/purchase/__tests__/order.test.ts`
   - `frontend-admin/src/pages/inventory/__tests__/stock-ledger-contract.test.ts`
3. 计划与归档
   - `docs/plans/第22条主线-采购库存模块生产级增强方案.md`

说明：

1. `output/` 目录仅作为 M22-5 桌面真实验收证据保留，不属于代码交付物，不纳入提交范围判定。
2. 本轮质量归档只写正式报告文件，不对上述实现文件做任何修改。

## 验收证据

### 1. M22-2 后端最小加固

按本主线执行回传与当前工作区文件边界，后端验收结论为通过：

1. `MatStockServiceTest`：`34 tests pass`
2. migration 相关测试：`20 tests pass`
3. `V131`：执行成功，`success=1`
4. 重复活动库存上线前预查：当前验收记录为 `0`

当前工作区也可对应看到本轮后端交付落点：

1. `MatStockService.java`
2. `MatStockServiceTest.java`
3. `V131__fix_mat_stock_active_unique_constraint.java`
4. `V131__fix_mat_stock_active_unique_constraint.sql`

### 2. M22-3 前端业务契约最小修正

按本主线执行回传与当前工作区文件边界，前端门禁结论为通过：

1. 定向 Vitest：`22 tests pass`
2. `pnpm type-check`：通过
3. `pnpm build`：通过

当前工作区对应前端收口文件：

1. `purchase-request.vue` 与 `purchase-request.test.ts`
2. `order.vue` 与 `order.test.ts`
3. `useStockLedger.ts` 与 `stock-ledger-contract.test.ts`

说明：

1. 本轮部分前端测试属于源码守卫，主要用于锁定业务契约、只读查看态、深链和筛选行为。
2. 真实浏览器验收已补覆盖关键只读 / 深链 / 筛选链路，因此不把“只有源码守卫”误写成全量浏览器覆盖。

### 3. M22-4 运行态刷新

运行态验收结论为通过：

1. backend health：`200`
2. frontend `http://localhost:5173`：`200`
3. 前端重启后 ready，后续 M22-5 基于最新运行态继续桌面验收

### 4. M22-5 桌面真实验收

桌面真实验收结论为通过，覆盖页面限定为：

1. 采购申请
2. 采购订单
3. 库存台账

关键接口口径（按本轮页面与深链能力归档）：

1. 采购申请列表 / 详情相关接口
2. 采购订单列表 / 详情相关接口
3. 库存台账列表 / 筛选相关接口

截图证据路径：

1. 采购申请列表：`D:\projects-test\cgc-pms\output\playwright\m22-5-acceptance\purchase-request-list.png`
2. 采购申请详情：`D:\projects-test\cgc-pms\output\playwright\m22-5-acceptance\purchase-request-detail.png`
3. 采购申请深链：`D:\projects-test\cgc-pms\output\playwright\m22-5-acceptance\pr-2026-07-06T04-36-49-355Z-deeplink.png`
4. 采购订单列表：`D:\projects-test\cgc-pms\output\playwright\m22-5-acceptance\purchase-order-list.png`
5. 采购订单详情：`D:\projects-test\cgc-pms\output\playwright\m22-5-acceptance\purchase-order-detail.png`
6. 采购订单深链：`D:\projects-test\cgc-pms\output\playwright\m22-5-acceptance\po-2026-07-06T04-37-18-919Z-deeplink.png`
7. 库存台账列表：`D:\projects-test\cgc-pms\output\playwright\m22-5-acceptance\inventory-stock.png`
8. 库存台账初始态：`D:\projects-test\cgc-pms\output\playwright\m22-5-acceptance\stock-2026-07-06T04-42-30-565Z-initial.png`
9. 库存台账筛选态：`D:\projects-test\cgc-pms\output\playwright\m22-5-acceptance\stock-2026-07-06T04-42-30-565Z-filtered.png`

验收判定：

1. 采购申请：桌面端列表可读、可定位、可查看详情，深链可落到目标页面并打开只读查看态
2. 采购订单：桌面端列表可读、可定位、可查看详情，深链可落到目标页面并打开只读查看态
3. 库存台账：桌面端列表可读，关键筛选链路可用

## 阻塞项

无。

## 非阻塞项 / 剩余风险

1. `V131` 上线前仍需预查生产库是否存在重复活动库存。
2. 真实 MySQL 未在本轮执行完整 Flyway，只完成本地 MySQL dev 与 H2 / Flyway 测试链路验证。
3. 本轮未覆盖创建 / 编辑 / 提交审批等写链路。
4. UI 视觉和移动端明确不属于本主线范围，不纳入阻塞判断。
5. 前端部分测试属于源码守卫；真实浏览器已覆盖关键只读 / 深链 / 筛选链路，但不等于全量交互闭环都已覆盖。

## 上线裁决标准逐项对照

| 阶段 | 结果 | 依据 |
| --- | --- | --- |
| M22-1 契约与现状复核 | 已完成 | 已形成冻结边界，明确只收口桌面端采购申请 / 采购订单 / 库存台账，不扩到 UI、移动端、WMS 全量能力 |
| M22-2 后端最小加固 | 已完成 | `MatStockServiceTest 34 tests pass`、migration 相关 `20 tests pass`、`V131 success=1`、重复活动库存预查 `0` |
| M22-3 前端业务契约最小修正 | 已完成 | 定向 Vitest `22 tests pass`、`type-check` 通过、`build` 通过 |
| M22-4 运行态刷新 | 已完成 | backend health `200`、frontend `5173` `200`、前端重启后 ready |
| M22-5 桌面真实验收 | 已完成 | 采购申请 / 采购订单 / 库存台账真实桌面验收通过，截图证据已归档到 `output/playwright/m22-5-acceptance/` |
| M22-6 质量归档 | 已完成 | 本报告已落盘 |

## 模型分档复盘

1. `M22-2` 后端任务升档到高档位是合理的。该阶段涉及库存口径、活动库存唯一约束和 migration 风险，不能按普通小改处理。
2. `M22-3` 前端任务使用 `medium` 足够，但实际收口仍需要多轮质量复核，原因不是实现复杂度过高，而是需要反复确认契约、深链和只读查看态是否与桌面验收口径一致。
3. `M22-4` 运行态刷新使用 `low` 足够。该阶段主要是固定动作、健康检查和 ready 复核。
4. `M22-5` 桌面真实验收使用 `medium` 是必要的。该阶段必须结合运行态、页面、截图和业务范围边界形成最终裁决，不能退化成机械截图摘录。

## 当前 git status 摘要

本次归档时，`git status --short` 与主线 22 直接相关的摘要如下：

```text
 M backend/src/main/java/com/cgcpms/inventory/service/MatStockService.java
 M backend/src/test/java/com/cgcpms/inventory/MatStockServiceTest.java
 M frontend-admin/src/pages/inventory/__tests__/purchase-request.test.ts
 M frontend-admin/src/pages/inventory/composables/useStockLedger.ts
 M frontend-admin/src/pages/inventory/purchase-request.vue
 M frontend-admin/src/pages/purchase/__tests__/order.test.ts
 M frontend-admin/src/pages/purchase/order.vue
?? backend/src/main/java/com/cgcpms/common/migration/V131__fix_mat_stock_active_unique_constraint.java
?? backend/src/main/resources/db/migration/V131__fix_mat_stock_active_unique_constraint.sql
?? docs/plans/第22条主线-采购库存模块生产级增强方案.md
?? frontend-admin/src/pages/inventory/__tests__/stock-ledger-contract.test.ts
?? output/
```

判定：

1. 当前未见与本报告结论冲突的额外业务代码改动范围。
2. `output/` 仍为验收证据目录，不计入代码交付结论。

## 运行残留说明

1. `output/playwright/m22-5-acceptance/` 及其子目录需要保留，原因是其中包含本轮正式桌面验收截图与 Playwright 产物，是本报告对应的原始证据。
2. 当前环境可见 `chrome`、`node`、`node_repl` 等进程，但仅能确认其处于运行中，不能确认都由本轮主线 22 验收独占创建。
3. 由于进程归属不明，本轮未执行任何清理或杀进程动作，避免误伤用户现有开发 / 验收环境。
4. 本轮质量归档未清理 `output/`，符合“证据保留、不盲删”的边界要求。
