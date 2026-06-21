# 库存台账表格优化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化 `stock.vue` 的流水表格 — 列定义重排、中文标签、排序、列设置、详情 Drawer、搜索栏增强。

**Architecture:** 单文件改动 `stock.vue`，复用全局 `lg-*` CSS 类和 `useReferenceStore`。不新增文件，不改后端。扩展 `StockLedgerQuery` 类型（向后兼容）。

**Tech Stack:** Vue 3 + TypeScript + VxeTable + Ant Design Vue + Pinia

**Design spec:** `docs/superpowers/specs/2026-06-21-stock-table-optimization-design.md`

## Global Constraints

- 仅修改 `frontend-admin/src/pages/inventory/stock.vue` 和 `frontend-admin/src/types/inventory.ts`
- 不修改后端代码
- 所有 enum 值映射处理 null/undefined 情况（sourceType 后端当前传 null）
- 数值列统一右对齐 + `font-variant-numeric: tabular-nums`
- 列设置持久化到 localStorage key `stock_ledger_cols`
- 后端暂不支持 keyword/projectId/sort 参数时，前端做降级处理

---

### Task 1: 扩展 StockLedgerQuery 类型

**Files:**
- Modify: `frontend-admin/src/types/inventory.ts:111-116`

**Interfaces:**
- Produces: `StockLedgerQuery` 新增 `projectId?: string`、`keyword?: string`、`sortField?: string`、`sortOrder?: 'asc' | 'desc'`

- [ ] **Step 1: 修改类型定义**

将 `StockLedgerQuery` 接口从：
```ts
export interface StockLedgerQuery {
  warehouseId: string
  materialId: string
  pageNo?: number
  pageSize?: number
}
```

改为：
```ts
export interface StockLedgerQuery {
  warehouseId: string
  materialId: string
  projectId?: string
  keyword?: string
  sortField?: string
  sortOrder?: 'asc' | 'desc'
  pageNo?: number
  pageSize?: number
}
```

- [ ] **Step 2: 类型检查**

```bash
cd D:/projects-test/cgc-pms/frontend-admin && pnpm type-check
```

- [ ] **Step 3: 提交**

```bash
git add frontend-admin/src/types/inventory.ts
git commit -m "feat: extend StockLedgerQuery with projectId, keyword, sort params"
```

---

### Task 2: 重写 stock.vue — 脚本部分

**Files:**
- Modify: `frontend-admin/src/pages/inventory/stock.vue:1-155`（script setup 块）

**Interfaces:**
- Consumes: `StockLedgerQuery`（含新字段 from Task 1）
- Produces: 
  - `SOURCE_TYPE_LABEL: Record<string, string>` — 来源类型中文映射（含 null fallback）
  - `SOURCE_TYPE_COLOR: Record<string, string>` — 来源类型颜色映射
  - `colVisible: Record<string, boolean>` — 列显隐状态（localStorage 持久化）
  - `defaultCols` — 默认列配置
  - `gridColumns` — vxe-grid 列定义（含排序、slot、对齐）
  - `sortField: Ref<string>`, `sortOrder: Ref<'asc'|'desc'>` — 排序状态
  - `detailVisible: Ref<boolean>`, `detailItem: Ref<MatStockTxnVO|null>` — Drawer 状态
  - `filter` 新增 `projectId`, `keyword` 字段
  - `handleSortChange`, `showDetail`, `closeDetail`, `handleExport`, `toggleCol` 函数
  - `rowIndex` computed — 基于分页计算行号

- [ ] **Step 1: 替换 imports 区域**

在 `<script setup>` 顶部，将现有 import 扩展为：

```ts
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  SearchOutlined,
  ReloadOutlined,
  InboxOutlined,
  FallOutlined,
  RiseOutlined,
  AlertOutlined,
  SettingOutlined,
  DownloadOutlined,
  EyeOutlined,
} from '@ant-design/icons-vue'
import { getStockLedger, getWarehouseList } from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { WarehouseVO, MatStockVO, MatStockTxnVO } from '@/types/inventory'
```

- [ ] **Step 2: 添加来源类型映射常量**

在 `TXN_TYPE_COLOR` 之后添加：

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

function getSourceTypeLabel(type: string | null | undefined): string {
  if (!type) return '-'
  return SOURCE_TYPE_LABEL[type] ?? type
}
function getSourceTypeColor(type: string | null | undefined): string {
  if (!type) return 'default'
  return SOURCE_TYPE_COLOR[type] ?? 'default'
}
```

- [ ] **Step 3: 扩展 filter 和新增状态**

将现有 `filter` 改为：

```ts
const filter = reactive({
  warehouseId: undefined as string | undefined,
  materialId: undefined as string | undefined,
  projectId: undefined as string | undefined,
  keyword: '',
})
```

在 `referenceStore` 行后增加列设置、排序、Drawer 状态：

```ts
// ---- Column visibility ----
const COLS_KEY = 'stock_ledger_cols'
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
let saved: Record<string, boolean> = defaultCols
try {
  const raw = localStorage.getItem(COLS_KEY)
  if (raw) saved = JSON.parse(raw)
} catch {
  localStorage.removeItem(COLS_KEY)
}
const colVisible = reactive<Record<string, boolean>>({ ...defaultCols, ...saved })
function toggleCol(key: string) {
  colVisible[key] = !colVisible[key]
  localStorage.setItem(COLS_KEY, JSON.stringify(colVisible))
}

// ---- Sort ----
const sortField = ref<string>('createdTime')
const sortOrder = ref<'asc' | 'desc'>('desc')
function handleSortChange({ field, order }: { field: string; order: 'asc' | 'desc' | null }) {
  sortField.value = field
  sortOrder.value = order || 'desc'
  txnPageNo.value = 1
  fetchLedger()
}

// ---- Detail drawer ----
const detailVisible = ref(false)
const detailItem = ref<MatStockTxnVO | null>(null)
function showDetail(row: MatStockTxnVO) {
  detailItem.value = row
  detailVisible.value = true
}
function closeDetail() {
  detailVisible.value = false
  detailItem.value = null
}

// ---- Export ----
function handleExport() {
  message.info('导出功能开发中')
}

// ---- Row index ----
const rowIndex = computed(() => (txnPageNo.value - 1) * txnPageSize.value)
```

- [ ] **Step 4: 增强 handleReset 和 fetchWarehouses**

将 `handleReset` 改为支持新筛选字段：

```ts
function handleReset() {
  filter.warehouseId = undefined
  filter.materialId = undefined
  filter.projectId = undefined
  filter.keyword = ''
  txnPageNo.value = 1
  stock.value = null
  txnList.value = []
  txnTotal.value = 0
}
```

添加项目变更联动：

```ts
function onProjectChange(projectId: string | undefined) {
  filter.warehouseId = undefined
  if (projectId) {
    fetchWarehouses(projectId)
  } else {
    fetchWarehouses()
  }
}
```

修改 `fetchWarehouses`：

```ts
async function fetchWarehouses(projectId?: string) {
  try {
    const res = await getWarehouseList({ pageNo: 1, pageSize: 50, status: 'ENABLE', projectId })
    warehouseList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    warehouseList.value = []
  }
}
```

- [ ] **Step 5: 修改 fetchLedger 传参**

在 `fetchLedger` 函数中，API 调用传参扩展：

```ts
async function fetchLedger() {
  if (!filter.warehouseId) {
    stock.value = null
    txnList.value = []
    txnTotal.value = 0
    return
  }
  if (!filter.materialId) {
    message.warning('请先选择物料')
    return
  }
  loading.value = true
  try {
    const res = await getStockLedger({
      warehouseId: filter.warehouseId,
      materialId: filter.materialId,
      projectId: filter.projectId,
      keyword: filter.keyword || undefined,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
      pageNo: txnPageNo.value,
      pageSize: txnPageSize.value,
    })
    // ... rest unchanged
```

- [ ] **Step 6: 重写 gridColumns computed**

完全替换现有 `gridColumns`：

```ts
const gridColumns = computed(() => [
  ...(colVisible.rowIndex
    ? [{ type: 'seq' as const, title: '流水号', width: 80, align: 'center' as const }]
    : []),
  ...(colVisible.txnType
    ? [{ field: 'txnType', title: '类型', width: 80, slots: { default: 'txnType' } }]
    : []),
  ...(colVisible.quantity
    ? [
        {
          field: 'quantity',
          title: '变动量',
          width: 100,
          align: 'right' as const,
          sortable: true,
          slots: { default: 'quantity' },
        },
      ]
    : []),
  ...(colVisible.availableAfter
    ? [
        {
          field: 'availableAfter',
          title: '变动后余量',
          width: 120,
          align: 'right' as const,
          slots: { default: 'availableAfter' },
        },
      ]
    : []),
  ...(colVisible.sourceType
    ? [{ field: 'sourceType', title: '来源类型', width: 110, slots: { default: 'sourceType' } }]
    : []),
  ...(colVisible.sourceId
    ? [
        {
          field: 'sourceId',
          title: '关联单据',
          width: 130,
          ellipsis: true,
          slots: { default: 'sourceId' },
        },
      ]
    : []),
  ...(colVisible.createdTime
    ? [
        {
          field: 'createdTime',
          title: '操作时间',
          width: 150,
          sortable: true,
        },
      ]
    : []),
  ...(colVisible.ops
    ? [{ title: '操作', width: 70, align: 'center' as const, slots: { default: 'ops' } }]
    : []),
])
```

- [ ] **Step 7: 类型检查验证**

```bash
cd D:/projects-test/cgc-pms/frontend-admin && pnpm type-check
```

修改所有 type-check 报错直到通过。

- [ ] **Step 8: 提交**

```bash
git add frontend-admin/src/pages/inventory/stock.vue
git commit -m "feat: rewrite stock.vue script — filters, columns, sort, drawer state, sourceType maps"
```

---

### Task 3: 重写 stock.vue — 模板部分

**Files:**
- Modify: `frontend-admin/src/pages/inventory/stock.vue:157-399`（template 块）

**Interfaces:**
- Consumes: 所有 Task 2 产生的状态、函数、computed

- [ ] **Step 1: 替换搜索栏（行 170-208）**

```html
<!-- 搜索栏 -->
<div class="lg-search-bar">
  <a-input
    v-model:value="filter.keyword"
    placeholder="搜索流水编号、来源单号…"
    allow-clear
    size="large"
    style="flex: 1; max-width: 260px"
    @press-enter="handleSearch"
  >
    <template #prefix><SearchOutlined style="color: #697380" /></template>
  </a-input>
  <a-select
    v-model:value="filter.warehouseId"
    placeholder="请选择仓库"
    allow-clear
    size="large"
    style="min-width: 180px"
    show-search
    :filter-option="
      (input: string, option: any) =>
        option.label?.toLowerCase().includes(input.toLowerCase())
    "
  >
    <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
      {{ w.warehouseName }}
    </a-select-option>
  </a-select>
  <a-select
    v-model:value="filter.materialId"
    placeholder="选择物料"
    allow-clear
    size="large"
    style="min-width: 220px"
    show-search
    :filter-option="
      (input: string, option: any) =>
        option.label?.toLowerCase().includes(input.toLowerCase())
    "
  >
    <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
      {{ m.materialName }} <span style="color: #9ca3af">({{ m.materialCode }})</span>
    </a-select-option>
  </a-select>
  <a-select
    v-model:value="filter.projectId"
    placeholder="全部项目"
    allow-clear
    size="large"
    style="min-width: 160px"
    show-search
    :filter-option="
      (input: string, option: any) =>
        option.label?.toLowerCase().includes(input.toLowerCase())
    "
    @change="onProjectChange"
  >
    <a-select-option v-for="p in referenceStore.projects" :key="p.id" :value="p.id">
      {{ p.projectName }}
    </a-select-option>
  </a-select>
  <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
  <a-button size="large" @click="handleReset">
    <template #icon><ReloadOutlined /></template>
    重置
  </a-button>
</div>
```

- [ ] **Step 2: 在 KPI strip 之后、表格之前插入工具栏（在 `</div>` 关闭 `.lg-left` 之前）**

在分页上方、流水标题之前添加：

```html
<!-- 工具栏 -->
<div class="lg-toolbar">
  <div class="lg-toolbar-left">
    <a-button @click="handleSearch">
      <template #icon><ReloadOutlined /></template>
    </a-button>
  </div>
  <div class="lg-toolbar-right">
    <a-dropdown v-if="!isMobile">
      <a-button size="small">
        <template #icon><SettingOutlined /></template>
        列设置
      </a-button>
      <template #overlay>
        <a-menu>
          <a-menu-item v-for="(_, key) in defaultCols" :key="key" @click="toggleCol(key)">
            <a-checkbox :checked="colVisible[key]">
              {{
                {
                  rowIndex: '流水号',
                  txnType: '类型',
                  quantity: '变动量',
                  availableAfter: '变动后余量',
                  sourceType: '来源类型',
                  sourceId: '关联单据',
                  createdTime: '操作时间',
                  ops: '操作',
                }[key]
              }}
            </a-checkbox>
          </a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>
    <a-button size="small" @click="handleExport">
      <template #icon><DownloadOutlined /></template>
      导出
    </a-button>
  </div>
</div>
```

- [ ] **Step 3: 更新 vxe-grid 及其 slot 模板（替换行 294-331）**

```html
<vxe-grid
  :data="txnList"
  :columns="gridColumns"
  :loading="loading"
  :column-config="{ resizable: true }"
  stripe
  border="inner"
  size="small"
  max-height="480"
  @sort-change="handleSortChange"
>
  <template #txnType="{ row }">
    <a-tag :color="TXN_TYPE_COLOR[row.txnType]">
      {{ TXN_TYPE_LABEL[row.txnType] ?? row.txnType }}
    </a-tag>
  </template>
  <template #quantity="{ row }">
    <span
      class="lg-money"
      :style="{
        color: row.txnType === 'OUT' ? '#ef4444' : '#16a34a',
      }"
    >
      {{ row.txnType === 'OUT' ? '−' : '+' }}{{
        Number(row.quantity).toLocaleString('zh-CN', { minimumFractionDigits: 4 })
      }}
    </span>
  </template>
  <template #availableAfter="{ row }">
    <span
      class="lg-money"
      :style="{
        color: Number(row.availableAfter) < 10 ? '#ef4444' : 'var(--text)',
        fontWeight: Number(row.availableAfter) < 10 ? 700 : 600,
      }"
    >
      {{
        Number(row.availableAfter).toLocaleString('zh-CN', { minimumFractionDigits: 4 })
      }}
    </span>
  </template>
  <template #sourceType="{ row }">
    <a-tag :color="getSourceTypeColor(row.sourceType)" size="small">
      {{ getSourceTypeLabel(row.sourceType) }}
    </a-tag>
  </template>
  <template #sourceId="{ row }">
    <span v-if="row.sourceId" class="lg-link" @click.stop>
      {{ row.sourceId }}
    </span>
    <span v-else style="color: #9ca3af">-</span>
  </template>
  <template #ops="{ row }">
    <a class="lg-link" @click="showDetail(row)">详情</a>
  </template>
</vxe-grid>
```

- [ ] **Step 4: 在 `</div>`（.lg-left 结束）之前、分页之后添加详情 Drawer**

在 `</div>` <!-- lg-left 关闭 --> 之前，分页区块之后添加：

```html
<!-- 流水详情 Drawer -->
<a-drawer
  :open="detailVisible"
  title="流水详情"
  placement="right"
  :width="480"
  @close="closeDetail"
>
  <template v-if="detailItem">
    <a-descriptions :column="2" size="small" bordered>
      <a-descriptions-item label="流水编号">{{ detailItem.id }}</a-descriptions-item>
      <a-descriptions-item label="交易类型">
        <a-tag :color="TXN_TYPE_COLOR[detailItem.txnType]">
          {{ TXN_TYPE_LABEL[detailItem.txnType] ?? detailItem.txnType }}
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="仓库名称">
        {{ getWarehouseName(detailItem.warehouseId) }}
      </a-descriptions-item>
      <a-descriptions-item label="物料名称">
        {{ getMaterialName(detailItem.materialId) }}
      </a-descriptions-item>
      <a-descriptions-item label="变动量">
        <span :style="{ color: detailItem.txnType === 'OUT' ? '#ef4444' : '#16a34a', fontWeight: 600 }">
          {{ detailItem.txnType === 'OUT' ? '−' : '+' }}{{
            Number(detailItem.quantity).toLocaleString('zh-CN', { minimumFractionDigits: 4 })
          }}
        </span>
      </a-descriptions-item>
      <a-descriptions-item label="变动后余量">
        <span style="font-weight: 600">
          {{
            Number(detailItem.availableAfter).toLocaleString('zh-CN', { minimumFractionDigits: 4 })
          }}
        </span>
      </a-descriptions-item>
      <a-descriptions-item label="来源类型">
        <a-tag :color="getSourceTypeColor(detailItem.sourceType)" size="small">
          {{ getSourceTypeLabel(detailItem.sourceType) }}
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="来源单号">
        {{ detailItem.sourceId || '-' }}
      </a-descriptions-item>
      <a-descriptions-item label="操作时间" :span="2">
        {{ detailItem.createdTime || '-' }}
      </a-descriptions-item>
    </a-descriptions>
  </template>
</a-drawer>
```

- [ ] **Step 5: 删除无用的 `<style scoped>` 块（行 398-399）**

替换为空（页面的样式全部由全局 `lg-*` 类覆盖）：

```html
<style scoped>
/* 样式全部由 lg-* 全局类提供 */
</style>
```

- [ ] **Step 6: 类型检查**

```bash
cd D:/projects-test/cgc-pms/frontend-admin && pnpm type-check
```

修复所有错误。

- [ ] **Step 7: 提交**

```bash
git add frontend-admin/src/pages/inventory/stock.vue
git commit -m "feat: rewrite stock.vue template — search bar, toolbar, grid columns, detail drawer"
```

---

### Task 4: 最终验证

**Files:**
- 无新文件创建

- [ ] **Step 1: 类型检查**

```bash
cd D:/projects-test/cgc-pms/frontend-admin && pnpm type-check
```

- [ ] **Step 2: ESLint 检查**

```bash
cd D:/projects-test/cgc-pms/frontend-admin && pnpm lint
```

- [ ] **Step 3: 格式化**

```bash
cd D:/projects-test/cgc-pms/frontend-admin && pnpm format
```

- [ ] **Step 4: 运行测试**

```bash
cd D:/projects-test/cgc-pms/frontend-admin && pnpm test:unit
```

- [ ] **Step 5: 构建验证**

```bash
cd D:/projects-test/cgc-pms/frontend-admin && pnpm build
```

- [ ] **Step 6: 提交（如需修复）**

```bash
git add -A && git commit -m "chore: lint & format fixes for stock table optimization"
```
