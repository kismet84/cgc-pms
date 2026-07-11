# UI 重构检查清单

> 基于 `docs/00-UI-Design-Baselines-and-Code-Specifications.md` 和 `frontend-admin/src/assets/styles/global.css` 的 lg-* 体系。
> 更新日期：2026-06-24

## 迁移状态总览

### 布局层

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| 布局 | BasicLayoutShell.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 全用CSS变量 |
| 布局 | BasicLayoutAsync.vue | ⚠️部分对齐 | 4处 | 0 | N/A | N/A | N/A | spinner渐变硬编码#8ac1ff/#006dff等 |
| 布局 | SidebarMenu.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 全用CSS变量 |

### 仪表盘模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| dashboard | index.vue | ⚠️部分对齐 | 4处 | 0 | N/A | N/A | N/A | 使用CSS变量但样式自成一系 |
| dashboard | DashboardPmView.vue | ❌待迁移 | 4处 | 0 | ❌ | N/A | N/A | 硬编码KPI色#3b82f6/#f59e0b/#22c55e/#ef4444 |
| dashboard | DashboardMgmtView.vue | ❌待迁移 | 4处 | 0 | ❌ | N/A | N/A | 同上 |
| dashboard | DashboardFinanceView.vue | ❌待迁移 | 4处 | 0 | ❌ | N/A | N/A | 同上 |
| dashboard | DashboardCostView.vue | ❌待迁移 | 3处 | 0 | ❌ | N/A | N/A | 同上 |
| dashboard | DashboardBmView.vue | ❌待迁移 | 0 | 0 | ❌ | N/A | N/A | 未使用lg-*体系 |

### 项目管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| project | index.vue | ❌待迁移 | 20处 | 18个 | ❌ | ❌ | ❌ | 自建--project-*变量体系，未用lg-* |
| project | overview.vue | ❌待迁移 | 7处 | 0 | ❌ | ❌ | N/A | 使用CSS变量但未用lg-* |
| project | edit.vue | ⚠️部分对齐 | 1处 | 0 | N/A | N/A | N/A | 表单页，使用部分CSS变量 |
| project | members.vue | ❌待迁移 | 4处 | 0 | ❌ | ❌ | N/A | 硬编码渐变#8ac1ff/#006dff |

### 合同管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| contract | ContractLedgerPage.vue | ⚠️部分对齐 | 1处 | 0 | ✅ | ✅ | N/A | 使用lg-*，有lg-grid+lg-search-bar+lg-table-wrap |
| contract | ContractDetailPage.vue | ❌待迁移 | 15处 | 0 | N/A | N/A | N/A | 大量var()回退硬编码 |
| contract | ContractFormPage.vue | ⚠️部分对齐 | 1处 | 0 | N/A | N/A | N/A | 表单页，使用CSS变量 |
| contract | ContractKpiStrip.vue | ✅已对齐 | 0 | 0 | ✅ | N/A | N/A | 全用lg-kpi-card+CSS变量 |
| contract | ContractAnalysisPanel.vue | ✅已对齐 | 0 | 0 | N/A | N/A | ✅ | 全用lg-panel+lg-type-list |
| contract | ContractMobileCardList.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 无硬编码颜色 |

### 成本管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| cost | summary.vue | ⚠️部分对齐 | 19处 | 0 | ⚠️ | ✅ | ✅ | 全用lg-*但ECharts色值硬编码 |
| cost | ledger.vue | ⚠️部分对齐 | 1处 | 0 | ✅ | ✅ | ✅ | 全用lg-*，仅搜索图标#697380 |
| cost-target | index.vue | ⚠️部分对齐 | 3处 | 0 | N/A | ✅ | N/A | 使用lg-*，硬编码少量颜色 |
| cost-target | edit.vue | ❌待迁移 | 3处 | 0 | N/A | N/A | N/A | 表单页，未用lg-* |
| cost-subject | index.vue | ⚠️部分对齐 | 0 | 0 | N/A | ✅ | N/A | 使用lg-page+lg-table-wrap，无硬编码 |

### 采购管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| purchase | order.vue | ✅已对齐 | 0 | 0 | ✅ | ✅ | ✅ | 完整lg-*页面模板 |
| subcontract | task.vue | ✅已对齐 | 0 | 0 | ✅ | ✅ | N/A | lg-kpi-card+lg-table-wrap |
| subcontract | measure.vue | ✅已对齐 | 0 | 0 | ✅ | ✅ | N/A | lg-kpi-card+lg-table-wrap |

### 结算管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| settlement | index.vue | ⚠️部分对齐 | 12处 | 0 | ✅ | ✅ | ✅ | 完整lg-*模板，但KPI卡片内联硬编码 |
| settlement | detail.vue | ❌待迁移 | 13处 | 0 | ❌ | ❌ | N/A | 大量内联样式硬编码 |

### 收付款管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| payment | index.vue | ⚠️部分对齐 | 5处 | 0 | ✅ | ✅ | ✅ | 完整lg-*模板，少量内联硬编码 |
| receipt | index.vue | ⚠️部分对齐 | 1处 | 0 | N/A | ✅ | N/A | 使用lg-*，搜索图标#697380 |
| receipt | ReceiptKpiStrip.vue | ✅已对齐 | 0 | 0 | ✅ | N/A | N/A | 全用lg-kpi-card+CSS变量 |
| receipt | ReceiptFormModal.vue | ❌待迁移 | 4处 | 0 | N/A | N/A | N/A | 弹窗，硬编码颜色 |

### 发票管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| invoice | index.vue | ⚠️部分对齐 | 1处 | 0 | N/A | ✅ | N/A | 使用lg-*，搜索图标#697380 |
| invoice | InvoiceKpiStrip.vue | ⚠️部分对齐 | 1处 | 0 | ✅ | N/A | N/A | 全用lg-kpi-card，一处#ef4444 |
| invoice | InvoiceVerifyPanel.vue | ⚠️部分对齐 | 4处 | 0 | N/A | N/A | ✅ | 使用lg-panel，状态色硬编码 |
| invoice | InvoiceFormModal.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 弹窗，无硬编码 |

### 库存管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| inventory | stock.vue | ⚠️部分对齐 | 7处 | 0 | N/A | ✅ | ✅ | 使用lg-page+lg-grid+lg-panel |
| inventory | transaction.vue | ⚠️部分对齐 | 7处 | 0 | ✅ | N/A | ✅ | 使用lg-kpi-strip+lg-panel |
| inventory | warehouse.vue | ⚠️部分对齐 | 2处 | 0 | ✅ | ✅ | N/A | 使用lg-*，少量硬编码 |
| inventory | purchase-request.vue | ⚠️部分对齐 | 6处 | 0 | ✅ | ✅ | N/A | 使用lg-*，少量硬编码 |
| inventory | StockKpiStrip.vue | ✅已对齐 | 0 | 0 | ✅ | N/A | N/A | 全用lg-kpi-card+CSS变量 |
| inventory | StockAnalysisPanel.vue | ⚠️部分对齐 | 8处 | 0 | N/A | N/A | ✅ | 使用lg-panel，状态色硬编码 |
| inventory | StockSearchBar.vue | ⚠️部分对齐 | 2处 | 0 | N/A | N/A | N/A | 使用lg-search-bar，硬编码图标色 |
| inventory | StockTxnTable.vue | ⚠️部分对齐 | 4处 | 0 | N/A | ✅ | N/A | 使用lg-table-wrap，状态色硬编码 |
| inventory | StockTxnDetailDrawer.vue | ❌待迁移 | 2处 | 0 | N/A | N/A | N/A | 抽屉，未用lg-* |

### 组织管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| org | index.vue | ⚠️部分对齐 | 6处 | 0 | N/A | ✅ | N/A | 使用lg-*，面板背景硬编码 |
| org | OrgMetricStrip.vue | ✅已对齐 | 0 | 0 | ✅ | N/A | N/A | 使用CSS变量+span样式 |
| org | CompanyPanel.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 使用lg-search-bar |
| org | DepartmentPanel.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 使用lg-search-bar |
| org | PositionPanel.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 使用lg-search-bar |
| org | CompanyModal.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 弹窗，无硬编码 |
| org | DepartmentModal.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 弹窗，无硬编码 |
| org | PositionModal.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 弹窗，无硬编码 |

### 系统管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| system | users/index.vue | ⚠️部分对齐 | 0 | 0 | N/A | ✅ | N/A | 使用lg-*，无硬编码 |
| system | roles/index.vue | ⚠️部分对齐 | 0 | 0 | N/A | ✅ | N/A | 使用lg-*，无硬编码 |
| system | roles/PermissionModal.vue | ✅已对齐 | 0 | 0 | N/A | N/A | N/A | 弹窗，无硬编码 |
| system | dict/index.vue | ⚠️部分对齐 | 2处 | 0 | N/A | ✅ | N/A | 使用lg-*，CSS回退硬编码 |
| system | data/index.vue | ❌待迁移 | 1处 | 0 | N/A | ❌ | N/A | 未用lg-* |
| settings | index.vue | ❌待迁移 | 1处 | 0 | N/A | ❌ | N/A | 未用lg-* |

### 审批管理模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| approval | todo.vue | ⚠️部分对齐 | 0 | 0 | N/A | ✅ | N/A | 使用lg-page+lg-table-wrap |
| approval | process.vue | ❌待迁移 | 2处 | 0 | N/A | ❌ | N/A | 未用lg-*，使用未定义变量--radius-lg |
| approval | detail.vue | ❌待迁移 | 3处 | 0 | N/A | ❌ | N/A | 未用lg-* |

### 其他模块

| 模块 | 页面 | 状态 | 硬编码颜色 | 自定义变量 | KPI 对齐 | 表格对齐 | 分析面板 | 备注 |
|------|------|------|-----------|-----------|---------|---------|---------|------|
| partner | index.vue | ⚠️部分对齐 | 1处 | 0 | N/A | ✅ | N/A | 使用lg-*，搜索图标#697380 |
| material | dictionary.vue | ⚠️部分对齐 | 1处 | 0 | N/A | ✅ | N/A | 使用lg-*，搜索图标#697380 |
| variation | order.vue | ⚠️部分对齐 | 3处 | 0 | N/A | ✅ | N/A | 使用lg-*，内联硬编码 |
| alert | index.vue | ⚠️部分对齐 | 2处 | 0 | ✅ | ✅ | N/A | 使用lg-*完整模板，少量硬编码 |
| login | index.vue | ❌待迁移 | 9处 | 0 | N/A | N/A | N/A | 登录页自成体系，未用lg-* |
| profile | index.vue | ❌待迁移 | 4处 | 0 | N/A | N/A | N/A | 个人中心，未用lg-* |
| help | index.vue | ❌待迁移 | 0 | 0 | N/A | N/A | N/A | 帮助页，未用lg-* |
| error | 404.vue | ❌待迁移 | 0 | 0 | N/A | N/A | N/A | 错误页，未用lg-* |

## 统计汇总

| 指标 | 数量 |
|------|------|
| 总页面/组件数 | 66 |
| ✅ 已对齐 | 14 (21.2%) |
| ⚠️ 部分对齐 | 31 (47.0%) |
| ❌ 待迁移 | 21 (31.8%) |

### 按模块统计

| 模块 | 总数 | ✅已对齐 | ⚠️部分对齐 | ❌待迁移 |
|------|------|---------|-----------|---------|
| 布局 | 3 | 2 | 1 | 0 |
| 仪表盘 | 6 | 0 | 1 | 5 |
| 项目管理 | 4 | 0 | 1 | 3 |
| 合同管理 | 6 | 3 | 2 | 1 |
| 成本管理 | 5 | 0 | 5 | 0 |
| 采购管理 | 3 | 3 | 0 | 0 |
| 结算管理 | 2 | 0 | 1 | 1 |
| 收付款管理 | 4 | 1 | 2 | 1 |
| 发票管理 | 4 | 1 | 3 | 0 |
| 库存管理 | 9 | 1 | 7 | 1 |
| 组织管理 | 8 | 7 | 1 | 0 |
| 系统管理 | 5 | 1 | 3 | 1 |
| 审批管理 | 3 | 0 | 1 | 2 |
| 其他 | 4 | 0 | 1 | 3 |

## 高频问题模式

### 1. 搜索图标硬编码 `#697380`（8处）
以下页面在搜索栏前缀图标上使用相同硬编码色值，应统一为 `--text-secondary` CSS变量：
- `cost/ledger.vue`、`receipt/index.vue`、`invoice/index.vue`、`partner/index.vue`
- `material/dictionary.vue`、`inventory/StockSearchBar.vue`、`variation/order.vue`、`alert/index.vue`

### 2. 状态色硬编码（15+处）
`#ef4444`（红）、`#22c55e`（绿）、`#f59e0b`（黄）、`#3b82f6`（蓝）在多处重复硬编码，应替换为 `--error`、`--success`、`--warning`、`--info`。

### 3. ECharts 图表色硬编码
`cost/summary.vue`（19处）和 `project/overview.vue`（6处）的 ECharts 配置中色值硬编码。ECharts 颜色可抽为 CSS变量引用或主题配置。

### 4. 页面级自定义变量体系（不推荐）
`project/index.vue` 定义了18个 `--project-*` 局部变量，其他页面均已统一使用全局 `global.css` 变量。建议迁移到全局变量体系或使用 `lg-*` 类名。

### 5. 未定义的 CSS 变量引用
- `approval/process.vue` 引用 `--radius-lg`（全局变量中不存在）
- `approval/todo.vue` 引用 `--subtext`（全局变量中不存在）

## 迁移优先级建议

### P0 - 高优先级（影响一致性，改动小）
1. 搜索图标 `#697380` 替换为 `var(--text-secondary)` -- 8个文件
2. 状态色硬编码替换为语义变量 -- 15+个文件
3. 修复未定义CSS变量引用 -- 2个文件

### P1 - 中优先级（模块级对齐）
4. `project/index.vue` -- 迁移 `--project-*` 到全局变量 + lg-* 类名
5. `settlement/index.vue` -- KPI 卡片内联颜色替换
6. `settlement/detail.vue` -- 全面迁移
7. `dashboard/` 全部5个视图 -- 对齐 KPI 颜色体系

### P2 - 低优先级（独立页面，影响小）
8. `login/index.vue` -- 登录页自成体系
9. `profile/index.vue` -- 个人中心
10. `approval/detail.vue`、`approval/process.vue` -- 审批详情/流程
