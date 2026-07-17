<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import {
  useStockLedger,
  TXN_TYPE_COLOR,
  TXN_TYPE_LABEL,
  getSourceTypeColor,
  getSourceTypeLabel,
} from './composables/useStockLedger'
import { ColumnSettingsButton, LgEmptyState } from '@/components/list-page'
import StockSearchBar from './components/StockSearchBar.vue'
import StockKpiStrip from './components/StockKpiStrip.vue'
import StockTxnTable from './components/StockTxnTable.vue'
import StockTxnDetailDrawer from './components/StockTxnDetailDrawer.vue'
import StockAnalysisPanel from './components/StockAnalysisPanel.vue'
import { createStockTransfer } from '@/api/modules/inventory'
import type { StockTransferCandidateVO } from '@/types/inventory'

const referenceStore = useReferenceStore()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const {
  filter,
  loading,
  listError,
  stock,
  transferCandidates,
  transferCandidatesLoading,
  incomingSupplies,
  incomingSuppliesLoading,
  consumptionBaseline,
  consumptionBaselineLoading,
  consumptionBaselineError,
  safetyThresholdDraft,
  replenishmentTargetDraft,
  replenishmentLeadDaysDraft,
  thresholdSaving,
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
  handleReplenish,
  handleReplenishmentSettingsSave,
  inOutStats,
  visibleGridColumns,
  showEmptyState,
  hasActiveFilters,
  init,
} = useStockLedger({ route, router })

const canEditStock = computed(
  () =>
    userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role)) ||
    userStore.hasPermission('inventory:stock:edit'),
)

const canTransferStock = computed(
  () =>
    userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role)) ||
    (userStore.hasPermission('inventory:stock:edit') &&
      userStore.hasPermission('inventory:transaction:add')),
)
const transferOpen = ref(false)
const transferCandidate = ref<StockTransferCandidateVO | null>(null)
const transferQuantity = ref<number | null>(null)
const transferReason = ref('')
const transferKey = ref('')
const transferSubmitting = ref(false)

function newTransferKey() {
  return globalThis.crypto?.randomUUID?.() ?? `transfer-${Date.now()}-${Math.random()}`
}

function openTransfer(candidate: StockTransferCandidateVO) {
  if (!canTransferStock.value || !stock.value) return
  transferCandidate.value = candidate
  transferQuantity.value = null
  transferReason.value = ''
  transferKey.value = newTransferKey()
  transferOpen.value = true
}

async function submitTransfer() {
  const candidate = transferCandidate.value
  const target = stock.value
  const quantity = transferQuantity.value
  if (!candidate || !target || quantity == null || quantity <= 0) {
    message.warning('请输入有效的调拨数量')
    return
  }
  if (quantity > Number(candidate.transferableQty)) {
    message.warning('调拨数量不能超过当前可调拨余量')
    return
  }
  if (!transferReason.value.trim()) {
    message.warning('请填写调拨原因')
    return
  }
  transferSubmitting.value = true
  try {
    await createStockTransfer({
      sourceStockId: candidate.stockId,
      targetStockId: target.id,
      quantity: quantity.toFixed(4),
      idempotencyKey: transferKey.value,
      reason: transferReason.value.trim(),
    })
    message.success('库存调拨已完成')
    transferOpen.value = false
    handleSearch()
  } catch (error: unknown) {
    console.error(error)
    message.error('库存调拨失败，请核对最新余量后重试')
  } finally {
    transferSubmitting.value = false
  }
}

// ---- 移动端检测 ----
const MOBILE_BP = 500
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}

// ---- 初始化 ----
onMounted(async () => {
  window.addEventListener('resize', onResize)
  await referenceStore.fetchProjects()
  await referenceStore.fetchMaterials()
  init()
})
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="lg-list-page lg-page app-page stock-page procurement-subcontract-list-page">
    <div class="lg-page-head stock-page-head">
      <div class="stock-page-meta-row">
        <a-breadcrumb class="stock-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>库存台账</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid">
      <!-- 左列 -->
      <div class="lg-left">
        <!-- KPI -->
        <StockKpiStrip :kpi="kpi" :kpi-max="kpiMax" :kpi-pct="kpiPct" :is-mobile="isMobile" />

        <StockSearchBar
          class="stock-search-bar"
          :keyword="filter.keyword"
          :project-id="filter.projectId"
          :warehouse-id="filter.warehouseId"
          :material-id="filter.materialId"
          :project-list="projects"
          :warehouse-list="warehouseList"
          :material-list="materialList"
          @update:keyword="(v: string) => (filter.keyword = v)"
          @update:project-id="onProjectChange"
          @update:warehouse-id="(v: string | undefined) => (filter.warehouseId = v)"
          @update:material-id="(v: string | undefined) => (filter.materialId = v)"
          @search="handleSearch"
          @reset="handleReset"
        />

        <!-- Stock Balance Card -->
        <div v-if="stock" class="lg-panel stock-balance-panel">
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
              <span style="font-size: 13px; color: var(--text-secondary)">安全库存阈值：</span>
              <a-input-number
                v-model:value="safetyThresholdDraft"
                :min="0"
                :precision="4"
                :disabled="!canEditStock"
                style="width: 140px"
              />
            </div>
            <div>
              <span style="font-size: 13px; color: var(--text-secondary)">人工补货目标量：</span>
              <a-input-number
                v-model:value="replenishmentTargetDraft"
                :min="0"
                :precision="4"
                :disabled="!canEditStock"
                placeholder="未填则补到安全阈值"
                style="width: 180px"
              />
            </div>
            <div>
              <span style="font-size: 13px; color: var(--text-secondary)"
                >人工补货提前期（自然日）：</span
              >
              <a-input-number
                v-model:value="replenishmentLeadDaysDraft"
                :min="0"
                :max="3650"
                :precision="0"
                :disabled="!canEditStock"
                placeholder="未填则不预填日期"
                style="width: 180px"
              />
              <a-button
                v-if="canEditStock"
                type="link"
                :loading="thresholdSaving"
                @click="handleReplenishmentSettingsSave"
              >
                保存
              </a-button>
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

        <main class="lg-list-table-panel procurement-subcontract-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <span class="stock-table-title">出入库流水</span>
              <span class="stock-table-count">共 {{ txnTotal }} 条</span>
            </div>
            <div class="lg-toolbar-right">
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
          </div>

          <div v-if="listError" class="stock-list-feedback">
            <a-result status="error" title="库存台账加载失败" :sub-title="listError">
              <template #extra>
                <a-button type="primary" @click="fetchData">重试</a-button>
              </template>
            </a-result>
          </div>
          <div v-else-if="showEmptyState" class="stock-list-feedback">
            <LgEmptyState description="暂无符合条件的库存流水">
              <a-button v-if="hasActiveFilters" @click="handleReset">清空筛选</a-button>
              <a-button v-else type="primary" @click="handleSearch">重新查询</a-button>
            </LgEmptyState>
          </div>
          <!-- 桌面端表格 -->
          <StockTxnTable
            v-else-if="!isMobile"
            class="procurement-subcontract-desktop-table"
            :txn-list="txnList"
            :loading="loading"
            :grid-columns="visibleGridColumns"
            :fmt-qty="fmtQty"
            :safety-stock-qty="Number(stock?.safetyStockQty ?? 0)"
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
                    :style="{
                      color:
                        Number(row.availableAfter) < Number(stock?.safetyStockQty ?? 0)
                          ? '#ef4444'
                          : 'var(--text)',
                    }"
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
      <StockAnalysisPanel
        class="procurement-subcontract-analysis-rail"
        :low-stock-warn="lowStockWarn"
        :kpi="kpi"
        :in-out-stats="inOutStats"
        :transfer-candidates="transferCandidates"
        :transfer-candidates-loading="transferCandidatesLoading"
        :incoming-supplies="incomingSupplies"
        :incoming-supplies-loading="incomingSuppliesLoading"
        :consumption-baseline="consumptionBaseline"
        :consumption-baseline-loading="consumptionBaselineLoading"
        :consumption-baseline-error="consumptionBaselineError"
        :can-transfer="canTransferStock"
        @replenish="handleReplenish"
        @transfer="openTransfer"
      />
    </div>

    <a-modal
      v-model:open="transferOpen"
      title="同项目跨仓调拨"
      :confirm-loading="transferSubmitting"
      ok-text="确认调拨"
      cancel-text="取消"
      @ok="submitTransfer"
    >
      <a-form layout="vertical">
        <a-form-item label="来源仓库">
          <a-input :value="transferCandidate?.warehouseName" disabled />
        </a-form-item>
        <a-form-item label="可调拨量">
          <a-input :value="transferCandidate?.transferableQty" disabled />
        </a-form-item>
        <a-form-item label="调拨数量" required>
          <a-input-number
            v-model:value="transferQuantity"
            :min="0.0001"
            :max="Number(transferCandidate?.transferableQty ?? 0)"
            :precision="4"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="调拨原因" required>
          <a-textarea v-model:value="transferReason" :maxlength="500" show-count :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.stock-page-head {
  min-height: 0;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.stock-page-meta-row {
  display: flex;
  align-items: center;
  min-width: 0;
}

.stock-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.stock-search-bar {
  flex: 0 0 auto;
  align-self: auto;
  justify-content: flex-start;
  height: auto;
  margin: 0;
  min-height: 0;
}

.stock-page .lg-left {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.stock-balance-panel {
  margin: 0;
}

.stock-page .lg-list-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.stock-page .lg-list-table-panel > .lg-toolbar {
  border-bottom: 1px solid var(--border-subtle);
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

.stock-page .lg-list-table-panel .lg-table-wrap {
  flex: 1;
  min-height: 0;
}

.stock-list-feedback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  padding: 24px;
}

.stock-page :deep(.lg-analysis-rail) {
  align-self: stretch;
  min-height: 0;
}

.stock-page :deep(.stock-analysis-panel) {
  overflow: auto;
}

@media (max-width: 900px) {
  .stock-page-meta-row {
    align-items: flex-start;
  }
}

@media (min-width: 769px) {
}
</style>
