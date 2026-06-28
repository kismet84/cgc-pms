<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import {
  useStockLedger,
  TXN_TYPE_COLOR,
  TXN_TYPE_LABEL,
  getSourceTypeColor,
  getSourceTypeLabel,
} from './composables/useStockLedger'
import { ColumnSettingsButton } from '@/components/list-page'
import StockSearchBar from './components/StockSearchBar.vue'
import StockKpiStrip from './components/StockKpiStrip.vue'
import StockTxnTable from './components/StockTxnTable.vue'
import StockTxnDetailDrawer from './components/StockTxnDetailDrawer.vue'
import StockAnalysisPanel from './components/StockAnalysisPanel.vue'

const referenceStore = useReferenceStore()

const {
  filter,
  loading,
  stock,
  txnList,
  txnTotal,
  txnPageNo,
  txnPageSize,
  kpi,
  warehouseList,
  projects,
  materialList,
  colVisible,
  columnSettings,
  toggleCol,
  handleSortChange,
  detailVisible,
  detailItem,
  showDetail,
  closeDetail,
  fetchData,
  fetchKpi,
  fetchWarehouses,
  handleSearch,
  handleReset,
  onProjectChange,
  handleTxnPageChange,
  handleTxnPageSizeChange,
  getWarehouseName,
  getMaterialName,
  fmtQty,
  kpiMax,
  kpiPct,
  lowStockWarn,
  inOutStats,
  visibleGridColumns,
} = useStockLedger()

// ---- 移动端检测 ----
const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}

// ---- 初始化 ----
onMounted(async () => {
  window.addEventListener('resize', onResize)
  await referenceStore.fetchProjects()
  await referenceStore.fetchMaterials()
  fetchWarehouses()
  fetchKpi()
})
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="lg-list-page lg-page app-page stock-page">
    <div class="lg-page-head stock-page-head">
      <div class="stock-page-meta-row">
        <a-breadcrumb class="stock-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>库存台账</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="stock-page-subtitle"
          >按仓库和物料核对库存余额，追踪出入库流水与低库存风险</span
        >
      </div>
    </div>

    <!-- 搜索栏 -->
    <StockSearchBar
      class="stock-search-bar"
      :keyword="filter.keyword"
      :warehouse-id="filter.warehouseId"
      :material-id="filter.materialId"
      :warehouse-list="warehouseList"
      :material-list="materialList"
      @update:keyword="(v: string) => (filter.keyword = v)"
      @update:warehouse-id="(v: string | undefined) => (filter.warehouseId = v)"
      @update:material-id="(v: string | undefined) => (filter.materialId = v)"
      @search="handleSearch"
      @reset="handleReset"
    />

    <div class="lg-grid">
      <!-- 左列 -->
      <div class="lg-left">
        <!-- KPI -->
        <StockKpiStrip :kpi="kpi" :kpi-max="kpiMax" :kpi-pct="kpiPct" :is-mobile="isMobile" />

        <!-- Stock Balance Card -->
        <div v-if="stock" class="lg-panel" style="margin-bottom: 12px">
          <div
            style="display: flex; gap: 40px; align-items: center; flex-wrap: wrap; padding: 4px 0"
          >
            <div>
              <span style="font-size: 13px; color: var(--text-secondary)">仓库：</span>
              <span style="font-weight: 600">
                {{ stock.warehouseName || getWarehouseName(stock.warehouseId) }}
              </span>
            </div>
            <div>
              <span style="font-size: 13px; color: var(--text-secondary)">物料：</span>
              <span style="font-weight: 600">
                {{ stock.materialName || getMaterialName(stock.materialId) }}
              </span>
            </div>
            <div>
              <span style="font-size: 13px; color: var(--text-secondary)">当前库存：</span>
              <span style="font-weight: 700; font-size: 18px; color: #1677ff">
                {{ fmtQty(stock.availableQty) }}
              </span>
              <span style="font-size: 13px; color: var(--text-secondary); margin-left: 4px">{{
                stock.unit
              }}</span>
            </div>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <span class="stock-table-title">出入库流水</span>
              <span class="stock-table-count">共 {{ txnTotal }} 条</span>
              <ColumnSettingsButton
                v-if="!isMobile"
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button @click="handleSearch">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <a-select
                v-model:value="filter.projectId"
                placeholder="全部项目"
                allow-clear
                style="width: 160px"
                size="small"
                @change="onProjectChange"
              >
                <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
                  {{ p.projectName }}
                </a-select-option>
              </a-select>
            </div>
          </div>

          <!-- 桌面端表格 -->
          <StockTxnTable
            v-if="!isMobile"
            :txn-list="txnList"
            :loading="loading"
            :grid-columns="visibleGridColumns"
            :fmt-qty="fmtQty"
            @sort-change="handleSortChange"
            @show-detail="showDetail"
          />

          <!-- 移动端卡片列表 -->
          <div v-else class="lg-card-list">
            <div v-if="loading" class="lg-card-list-loading">
              <a-spin size="large" />
            </div>
            <div v-else-if="!txnList.length" class="lg-card-list-empty">
              <a-empty />
            </div>
            <div v-for="row in txnList" :key="row.id" class="lg-card-item">
              <div class="lg-card-item-head">
                <span class="lg-card-code">
                  <a-tag :color="TXN_TYPE_COLOR[row.txnType]">
                    {{ TXN_TYPE_LABEL[row.txnType] ?? row.txnType }}
                  </a-tag>
                </span>
                <span class="lg-card-head-right">
                  <a-tag
                    v-if="colVisible.sourceType"
                    :color="getSourceTypeColor(row.sourceType)"
                    size="small"
                  >
                    {{ getSourceTypeLabel(row.sourceType) }}
                  </a-tag>
                </span>
              </div>
              <div class="lg-card-item-body">
                <div v-if="colVisible.quantity" class="lg-card-field">
                  <span class="lg-card-label">变动量</span>
                  <span
                    class="lg-card-value lg-card-money"
                    :style="{ color: row.txnType === 'OUT' ? '#ef4444' : '#16a34a' }"
                  >
                    {{ row.txnType === 'OUT' ? '−' : '+' }}{{ fmtQty(row.quantity) }}
                  </span>
                </div>
                <div v-if="colVisible.availableAfter" class="lg-card-field">
                  <span class="lg-card-label">变动后余量</span>
                  <span
                    class="lg-card-value lg-card-money"
                    :style="{ color: Number(row.availableAfter) < 10 ? '#ef4444' : 'var(--text)' }"
                  >
                    {{ fmtQty(row.availableAfter) }}
                  </span>
                </div>
                <div class="lg-card-field-row">
                  <div v-if="colVisible.sourceId" class="lg-card-field">
                    <span class="lg-card-label">关联单据</span>
                    <span class="lg-card-value">{{ row.sourceId || '-' }}</span>
                  </div>
                  <div v-if="colVisible.createdTime" class="lg-card-field">
                    <span class="lg-card-label">操作时间</span>
                    <span class="lg-card-value">{{ row.createdTime || '-' }}</span>
                  </div>
                </div>
              </div>
              <div class="lg-card-item-foot">
                <a-space :size="4">
                  <a-button size="small" type="link" @click="showDetail(row)">详情</a-button>
                </a-space>
              </div>
            </div>
          </div>

          <!-- 分页 -->
          <div class="lg-pagination">
            <span class="lg-total">共 {{ txnTotal }} 条流水</span>
            <a-pagination
              v-model:current="txnPageNo"
              v-model:page-size="txnPageSize"
              :total="txnTotal"
              :page-size-options="['10', '20', '50', '100']"
              show-size-changer
              show-quick-jumper
              @change="handleTxnPageChange"
              @show-size-change="handleTxnPageSizeChange"
            />
          </div>
        </main>

        <!-- 流水详情 Drawer -->
        <StockTxnDetailDrawer
          v-model:open="detailVisible"
          :detail-item="detailItem"
          :fmt-qty="fmtQty"
          :get-warehouse-name="getWarehouseName"
          :get-material-name="getMaterialName"
          @close="closeDetail"
        />
      </div>

      <!-- 右侧分析面板 -->
      <StockAnalysisPanel :low-stock-warn="lowStockWarn" :kpi="kpi" :in-out-stats="inOutStats" />
    </div>
  </div>
</template>

<style scoped>
.stock-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  margin-bottom: 7px;
  padding: 0;
}

.stock-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.stock-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.stock-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.stock-search-bar {
  margin-top: 21px;
  min-height: 74px;
}

.stock-page .lg-grid {
  margin-top: 14px;
}

.stock-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.stock-table-count {
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
