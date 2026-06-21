# ESLint 遗留错误修复计划

> 日期：2026-06-21 | 来源：全量 ESLint 检查 | 错误总数：67

## 错误分类与修复策略

### 类别 1：未使用的 import/变量（unused-vars）— 54 个

**策略：直接删除未使用的声明。**

| # | 文件 | 行 | 待删除 |
|---|------|-----|--------|
| 1 | `e2e/inventory.spec.ts` | 4 | `SAVE_FAILED` 常量 |
| 2 | `e2e/invoice.spec.ts` | 235 | `invoiceNoCell` 变量 |
| 3 | `src/api/modules/inventory.ts` | 5 | `MatStockTxnVO` import |
| 4 | `src/components/ContractChangeList.vue` | 292 | `getApprovalSteps` 函数 |
| 5 | `src/components/__tests__/NotificationBell.test.ts` | 3 | `nextTick` import |
| 6 | `src/pages/alert/index.vue` | 4 | `WarningOutlined` import |
| 7 | `src/pages/approval/detail.vue` | 266 | `goBack` 函数 |
| 8-14 | `src/pages/cost/ledger.vue` | 9,12,13,18,37,38,78 | 7 个 import/变量 |
| 15 | `src/pages/cost/summary.vue` | 11 | `CostSubjectSummaryVO` import |
| 16-19 | `src/pages/dashboard/index.vue` | 10,14,19,20 | 4 个图标 import |
| 20 | `src/pages/inventory/warehouse.vue` | 191 | `filterOption` 函数 |
| 21-22 | `src/pages/org/index.vue` | 287,288 | `_selectedKeys`, `info` |
| 23-25 | `src/pages/payment/index.vue` | 39,77,154 | `partners`, `columns`, `handleReset` |
| 26 | `src/pages/project/index.vue` | 111 | `e` catch 参数 |
| 27 | `src/pages/project/members.vue` | 31 | `ROLE_COLOR` |
| 28-30 | `src/pages/project/overview.vue` | 7,9,10 | 3 个图标 import |
| 31-37 | `src/pages/purchase/order.vue` | 3,17,18,19,20,41,702 | 7 个 import/变量 |
| 38 | `src/pages/settlement/index.vue` | 25 | `partners` |
| 39-40 | `src/pages/subcontract/measure.vue` | 43,691 | `partnerList`, `_item` |
| 41 | `src/pages/subcontract/task.vue` | 35 | `partnerList` |
| 42 | `src/pages/system/roles/PermissionModal.vue` | 27 | `flattenTree` |
| 43-45 | `src/pages/system/roles/__tests__/index.test.ts` | 2,3,318 | 3 个 import/变量 |
| 46 | `src/pages/system/users/index.vue` | 44 | `columns` |
| 47 | `src/pages/variation/order.vue` | 42 | `partnerList` |
| 48-52 | `src/stores/user.ts` | 32,36,58,69,80 | 5 个参数/变量 |

### 类别 2：no-explicit-any — 13 个

**策略：能用具体类型的替换；回调参数在第三方库约束下难以避免的加 `// eslint-disable-next-line`。**

| # | 文件 | 行 | 处理方式 |
|---|------|-----|---------|
| 53-54 | `e2e/notification.spec.ts` | 208,219 | 替换为具体类型 |
| 55 | `src/pages/cost/summary.vue` | 174 | ECharts 回调，disable-next-line |
| 56 | `src/pages/cost/summary.vue` | 194 | ECharts 回调，disable-next-line |
| 57 | `src/pages/cost/summary.vue` | 229 | ECharts tooltip formatter，disable-next-line |
| 58 | `src/pages/cost/summary.vue` | 260 | ECharts label formatter，disable-next-line |
| 59 | `src/pages/inventory/purchase-request.vue` | 83 | 替换为 `SelectOption` 或具体类型 |
| 60 | `src/pages/inventory/warehouse.vue` | 191 | 替换为 `SelectOption` 类型 |
| 61-65 | `src/pages/invoice/index.vue` | 63,383-387,410 | 63: 替换；383-410: 表单 reset 场景 disable-next-line |

### 类别 3：语法错误 — 1 个

| # | 文件 | 行 | 问题 |
|---|------|-----|------|
| 66 | `src/types/contract.ts` | 46 | `projectId: string  contractCode: string` 缺分号 |

### 类别 4：vue/no-reserved-props — 1 个

| # | 文件 | 行 | 问题 |
|---|------|-----|------|
| 67 | `src/pages/help/__tests__/index.test.ts` | 54 | `:key` 是 Vue 保留 prop，改名为 `:key-name` 或其他 |

---

## 实施任务

### Task 1: 修复 src 下的 unused-vars（约 30 个文件）

逐文件删除未使用的 import/变量/函数声明。每次改动后运行 `pnpm type-check` 验证。

### Task 2: 修复 no-explicit-any（约 7 个文件）

- ECharts 回调类（summary.vue）→ disable-next-line（第三方 API 无更好类型）
- `filterOption` 类（warehouse.vue, purchase-request.vue）→ 替换为具体类型
- 表单动态赋值类（invoice/index.vue）→ disable-next-line（刻意使用 any 的场景）

### Task 3: 修复 contract.ts 语法错误 + vue/no-reserved-props

- `contract.ts:46` — 补分号
- `help/__tests__/index.test.ts:54` — 改 reserve-prop 名称

### Task 4: 修复 e2e 下的错误

- `inventory.spec.ts` — 删除未使用的常量
- `invoice.spec.ts` — 删除未使用的变量
- `notification.spec.ts` — 替换 any 类型

### Task 5: 全量验证

```bash
pnpm type-check && pnpm lint && pnpm build && pnpm test:unit
```

---

## 风险

| 风险 | 处置 |
|------|------|
| 删除的 import 被其他文件通过 barrel export 间接依赖 | 不可能 — 这些都是本地 import，删除后 type-check 会立即报 |
| `_item` 等模板变量删除后运行时出错 | 检查模板使用情况，确认未使用才删 |
| `e` catch 参数删除导致 try/catch 语法错误 | 改为 `catch { ... }`（无参数形式，ES2019+） |
| `goBack` 删除后模板中可能引用 | 搜索模板确认无引用后删除 |
