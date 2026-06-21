# 库存台账表格优化设计

> 日期：2026-06-21 | 页面：`/inventory/stock` | 文件：`frontend-admin/src/pages/inventory/stock.vue`

## 目标

优化库存台账页面的出入库流水表格，提升视觉可读性和交互功能，与合同台账、成本台账等同类页面保持一致的用户体验。

## 范围

仅修改 `stock.vue` 的 `<script>` 和 `<template>` 部分，不新增文件，复用现有 `lg-*` 全局 CSS 类和项目设计系统。

---

## 一、搜索栏增强

**现状：** 仅仓库 + 物料两个下拉 + 查询/重置按钮。

**改进：**

```
[🔍 关键词搜索…]  [仓库 ▼]  [物料 ▼]  [项目 ▼]  [查询]  [重置]
```

| 控件 | 说明 |
|---|---|
| 关键词输入框 | `a-input`，搜索流水编号、来源单号等 |
| 仓库下拉 | 保留，`allow-clear`，`show-search` |
| 物料下拉 | 保留，`allow-clear`，`show-search` |
| 项目下拉 | 新增，数据来自 `useReferenceStore().projects`；选中后联动过滤仓库列表 |
| 查询按钮 | 触发 `handleSearch` |
| 重置按钮 | 清空所有筛选，重置分页 |

**实现要点：**
- `filter` 新增 `projectId` 和 `keyword` 字段
- 项目变更时重新调用 `fetchWarehouses({ projectId })` 过滤仓库
- 关键词支持 `@press-enter` 触发表搜索

---

## 二、工具栏（新增）

在表格上方增加工具栏行，参照 `ContractLedgerPage` 的 `lg-toolbar` 布局：

```
[🔄 刷新]                    [⚙ 列设置 ▼]  [📥 导出]
```

### 2.1 刷新按钮
- 重新加载当前页数据

### 2.2 列设置下拉
- `a-dropdown` + `a-menu` + `a-checkbox`
- 控制每列的显示/隐藏
- 选择持久化到 `localStorage`（key: `stock_ledger_cols`）
- 默认全部可见，列标签用中文

### 2.3 导出按钮
- 调用后端导出接口（若存在），或前端生成 CSV 下载
- 先埋按钮 UI，后端接口就绪后再接通；若后端暂无接口，点击时 toast 提示"导出功能开发中"

---

## 三、表格列优化

### 3.1 列定义

| 列 key | 标题 | 宽度 | 对齐 | 说明 |
|---|---|---|---|---|
| `rowIndex` | 流水号 | 80px | center | 序号（基于分页自动递增），替代无意义的 UUID |
| `txnType` | 类型 | 80px | center | 彩色标签 + 图标前缀（↑入库 ↓出库 ↔调整），复用现有 slot |
| `quantity` | 变动量 | 100px | right | `lg-money` 类（tabular-nums），入库绿色 + 号，出库红色 - 号，复用现有 slot |
| `availableAfter` | 变动后余量 | 110px | right | `lg-money` 类，当余量 < 10 时红色预警 |
| `sourceType` | 来源类型 | 110px | left | 中文标签 + 颜色（如"采购入库"/"领料出库"/"库存调整"） |
| `sourceId` | 关联单据 | 130px | left | 可点击链接，跳转到来源单据页面（`ellipsis: true`） |
| `createdTime` | 操作时间 | 140px | left | 支持点击列头排序（默认按时间降序） |
| `ops` | 操作 | 60px | center | "详情"链接，点击打开 Drawer |

### 3.2 列显隐配置

```ts
const defaultCols: Record<string, boolean> = {
  rowIndex: true,
  txnType: true,
  quantity: true,
  availableAfter: true,
  sourceType: true,
  sourceId: true,
  createdTime: true,
  ops: true,
}
```

### 3.3 排序

- 操作时间列支持服务端排序（`sortable: true`），默认降序
- 变动量列支持服务端排序
- 排序参数加入 API 请求

### 3.4 列配置

- `column-config="{ resizable: true }"` 保留
- 默认 `stripe`、`border="inner"`、`size="small"` 保留
- `max-height="480"` 保留
- 表头统一样式由 `lg-table-wrap :deep(.vxe-header--column)` 全局 CSS 控制

---

## 四、详情抽屉 Drawer（新增）

点击表格操作列的"详情"链接，打开右侧 `a-drawer`：

**展示内容：**

| 字段 | 说明 |
|---|---|
| 流水编号 | 数据库 ID（原始 UUID，对调试有用） |
| 交易类型 | 标签展示 |
| 仓库名称 | 文本 |
| 物料名称 + 编码 | 文本 |
| 变动量 | 数值 + 单位 |
| 变动前余量 | 数值 + 单位 |
| 变动后余量 | 数值 + 单位 |
| 来源类型 | 中文标签 |
| 来源单号 | 文本（如有关联单据，可点击跳转） |
| 操作时间 | 格式化时间 |
| 操作人 | 创建者名称 |

> 注意：当前 `MatStockTxnVO` 接口缺少部分字段（变动前余量、操作人），Drawer 中仅展示接口已有数据，缺失字段显示 "-"，后续可与后端协商扩展接口。

---

## 五、API 与数据层

### 5.1 现有接口（不变）

- `GET /inventory/stock/ledger` — 入参已支持 `warehouseId`、`materialId`、`pageNo`、`pageSize`
- 需前端扩展传参：`projectId`、`keyword`、`sortField`、`sortOrder`

### 5.2 后端可能需要的改动

| 变更 | 必要性 | 说明 |
|---|---|---|
| 流水明细详情接口 | 建议新增 | `GET /inventory/stock/txn/{id}` 返回完整流水详情 |
| ledger 接口增加入参 | 建议新增 | `projectId`、`keyword`、`sortField`、`sortOrder` |
| 仓库列表按项目过滤 | 已有 | `getWarehouseList` 支持 `projectId` 参数 |
| 导出接口 | 可选 | `GET /inventory/stock/ledger/export` |

> 前端先做好 UI 和参数传递，后端未支持时降级处理（关键词客户端过滤、排序仅当前页生效、导出提示开发中）。

### 5.3 类型扩展

```ts
// StockLedgerQuery 扩展
export interface StockLedgerQuery {
  warehouseId?: string
  materialId?: string
  projectId?: string
  keyword?: string
  sortField?: string
  sortOrder?: 'asc' | 'desc'
  pageNo?: number
  pageSize?: number
}
```

---

## 六、来源类型中文映射

```ts
const SOURCE_TYPE_LABEL: Record<string, string> = {
  PURCHASE_IN: '采购入库',
  PURCHASE_RETURN: '采购退货',
  MATERIAL_OUT: '领料出库',
  MATERIAL_RETURN: '退料入库',
  INVENTORY_IN: '盘点入库',
  INVENTORY_OUT: '盘点出库',
  ADJUST: '库存调整',
  TRANSFER_IN: '调拨入库',
  TRANSFER_OUT: '调拨出库',
  INIT: '期初导入',
}

const SOURCE_TYPE_COLOR: Record<string, string> = {
  PURCHASE_IN: 'success',
  PURCHASE_RETURN: 'warning',
  MATERIAL_OUT: 'error',
  MATERIAL_RETURN: 'blue',
  INVENTORY_IN: 'processing',
  INVENTORY_OUT: 'warning',
  ADJUST: 'orange',
  TRANSFER_IN: 'cyan',
  TRANSFER_OUT: 'purple',
  INIT: 'default',
}
```

> 注意：需确认后端实际返回的 `sourceType` 枚举值，映射表以上述值为默认，后续根据实际值调整。

---

## 七、兼容性与降级

| 场景 | 处理 |
|---|---|
| 后端不支持排序参数 | 排序仅对当前页生效（前端排序），不影响功能 |
| 后端不支持 keyword 搜索 | 关键词在前端对已加载数据做客户端过滤 |
| 后端不支持 projectId | 前端直接传递，后端忽略即可 |
| 详情接口不存在 | Drawer 用当前行数据渲染，缺失字段显示 "-" |
| 导出接口不存在 | 点击导出时 `message.info('导出功能开发中')` |

---

## 八、不做的

- 不改变页面整体布局（左侧表格 + 右侧分析面板）
- 不改变 KPI 卡片、低库存预警、出入库统计面板
- 不改变移动端适配逻辑
- 不在本次新增批量操作（checkbox 选择）
- 不修改后端代码
