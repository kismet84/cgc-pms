<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import {
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import ContractFormPage from './ContractFormPage.vue'
import ContractStatusTag from '@/components/ContractStatusTag.vue'
import ContractKpiStrip from './components/ContractKpiStrip.vue'
import ContractMobileCardList from './components/ContractMobileCardList.vue'
import ContractAnalysisPanel from './components/ContractAnalysisPanel.vue'
import { useContractLedger, TYPE_LABEL, TYPE_COLOR } from './composables/useContractLedger'
import type { ContractType, ContractStatus } from '@/types/contract'
import { ColumnSettingsButton } from '@/components/list-page'

const {
  contractModalVisible,
  contractModalMode,
  contractModalId,
  handleCreate,
  handleView,
  handleEdit,
  handleDelete,
  handleAllAlerts,
  handleContractSaved,
  handleContractClose,
  filter,
  projects,
  loading,
  tableData,
  total,
  pageNo,
  pageSize,
  kpi,
  colVisible,
  columnSettings,
  toggleCol,
  fetchData,
  handleSearch,
  handleReset,
  handlePageChange,
  handlePageSizeChange,
  fmtAmount,
  kpiMax,
  kpiPct,
  typeDistribution,
  typePercent,
  statusBars,
  warningRows,
  visibleColumns,
} = useContractLedger()

// ---- Mobile detection ----
const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
const filterVisibility = reactive({
  projectId: true,
  contractType: true,
  contractStatus: true,
  dateRange: true,
})
const filterSettingItems = [
  { key: 'projectId', label: '项目' },
  { key: 'contractType', label: '合同类型' },
  { key: 'contractStatus', label: '合同状态' },
  { key: 'dateRange', label: '签订日期' },
] as const
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}
function toggleFilterVisibility(key: (typeof filterSettingItems)[number]['key']) {
  filterVisibility[key] = !filterVisibility[key]
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="lg-list-page lg-page app-page cl-redesign-page">
    <div class="lg-page-head cl-page-head">
      <div class="cl-page-meta-row">
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>合同管理</a-breadcrumb-item>
          <a-breadcrumb-item>合同台账</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid cl-workspace">
      <div class="lg-left cl-main-column">
        <ContractKpiStrip
          :kpi="kpi"
          :is-mobile="isMobile"
          :fmt-amount="fmtAmount"
          :kpi-max="kpiMax"
          :kpi-pct="kpiPct"
        />

        <section class="lg-search-bar cl-query-panel" aria-label="合同台账查询条件">
          <div class="cl-query-primary">
            <a-select
              v-if="filterVisibility.projectId"
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              class="cl-query-select"
              size="large"
              @change="handleSearch"
            >
              <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.contractType"
              v-model:value="filter.contractType"
              placeholder="合同类型"
              allow-clear
              class="cl-query-select"
              size="large"
              @change="handleSearch"
            >
              <a-select-option v-for="(label, value) in TYPE_LABEL" :key="value" :value="value">
                {{ label }}
              </a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.contractStatus"
              v-model:value="filter.contractStatus"
              placeholder="合同状态"
              allow-clear
              class="cl-query-select"
              size="large"
              @change="handleSearch"
            >
              <a-select-option value="DRAFT">草稿</a-select-option>
              <a-select-option value="PERFORMING">履约中</a-select-option>
              <a-select-option value="SETTLED">已完成</a-select-option>
              <a-select-option value="TERMINATED">已终止</a-select-option>
            </a-select>
            <a-range-picker
              v-if="filterVisibility.dateRange"
              v-model:value="filter.dateRange"
              class="cl-date-filter"
              value-format="YYYY-MM-DD"
              size="large"
              @change="handleSearch"
            />
          </div>
          <div class="cl-query-keyword-row">
            <a-input
              v-model:value="filter.keyword"
              class="cl-keyword-search"
              placeholder="搜索合同编号、名称、甲方、乙方…"
              allow-clear
              size="large"
              @press-enter="handleSearch"
            >
              <template #prefix><SearchOutlined class="cl-search-prefix-icon" /></template>
            </a-input>
            <div class="cl-query-actions">
              <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
              <a-button size="large" @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
              <a-dropdown trigger="click">
                <a-button size="large">
                  <template #icon><SettingOutlined /></template>
                  筛选栏设置
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item
                      v-for="item in filterSettingItems"
                      :key="item.key"
                      @click="toggleFilterVisibility(item.key)"
                    >
                      <a-checkbox :checked="filterVisibility[item.key]">
                        {{ item.label }}
                      </a-checkbox>
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
          </div>
        </section>

        <main class="lg-list-table-panel cl-table-panel">
          <div class="lg-toolbar cl-table-toolbar">
            <div class="lg-toolbar-left">
              <div class="cl-table-heading">
                <span class="cl-table-title">合同列表</span>
                <span class="cl-table-count">共 {{ total }} 条</span>
              </div>
            </div>
            <div class="lg-toolbar-right cl-table-toolbar-right">
              <ColumnSettingsButton
                v-if="!isMobile"
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button aria-label="刷新合同台账" title="刷新合同台账" @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
              <a-button type="primary" @click="handleCreate">
                <template #icon><PlusOutlined /></template>
                新建合同
              </a-button>
            </div>
          </div>

          <div v-if="!isMobile" class="lg-table-wrap cl-table-wrap">
            <vxe-grid
              :data="tableData"
              :columns="visibleColumns"
              :loading="loading"
              :column-config="{ resizable: true, useKey: true }"
              show-overflow="title"
              show-header-overflow="title"
              stripe
              border="inner"
              size="small"
            >
              <template #contractCode="{ row }">
                <a-button class="cl-contract-link" type="link" @click="handleView(row)">
                  {{ row.contractCode }}
                </a-button>
              </template>
              <template #contractType="{ row }">
                <a-tag :color="TYPE_COLOR[row.contractType as ContractType]">
                  {{ TYPE_LABEL[row.contractType as ContractType] }}
                </a-tag>
              </template>
              <template #amount="{ row }">
                <span class="lg-money">{{
                  parseFloat(row.contractAmount).toLocaleString('zh-CN', {
                    minimumFractionDigits: 2,
                  })
                }}</span>
              </template>
              <template #status="{ row }">
                <ContractStatusTag :status="row.contractStatus as ContractStatus" />
              </template>
              <template #ops="{ row }">
                <a-dropdown class="cl-row-actions" :trigger="['click']">
                  <a-button
                    class="lg-row-action-trigger"
                    size="small"
                    type="text"
                    :aria-label="`打开合同操作菜单：${row.contractCode}`"
                    :title="`打开合同操作菜单：${row.contractCode}`"
                  >
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
            </vxe-grid>
          </div>

          <ContractMobileCardList
            v-else
            :data="tableData"
            :loading="loading"
            :col-visible="colVisible"
            :type-color="TYPE_COLOR"
            :type-label="TYPE_LABEL"
            @view="handleView"
            @edit="handleEdit"
            @delete="handleDelete"
          />

          <div class="lg-pagination cl-pagination">
            <span class="lg-total">共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :page-size-options="['10', '20', '50', '100']"
              show-size-changer
              show-quick-jumper
              @change="handlePageChange"
              @show-size-change="handlePageSizeChange"
            />
          </div>
        </main>
      </div>

      <ContractAnalysisPanel
        :type-distribution="typeDistribution"
        :type-percent="typePercent"
        :status-bars="statusBars"
        :warning-rows="warningRows"
        @all-alerts="handleAllAlerts"
      />
    </div>
  </div>

  <a-modal
    v-model:open="contractModalVisible"
    :title="contractModalMode === 'edit' ? '编辑合同' : '新建合同'"
    :width="800"
    :destroy-on-close="true"
    :footer="null"
    :mask-closable="false"
    centered
    class="cl-contract-modal"
    @cancel="handleContractClose"
  >
    <ContractFormPage
      :embedded="true"
      :mode="contractModalMode"
      :contract-id="contractModalId"
      @saved="handleContractSaved"
      @close="handleContractClose"
    />
  </a-modal>
</template>

<style scoped>
.cl-contract-modal :deep(.ant-modal-body) {
  max-height: calc(100vh - 96px);
  overflow: auto;
  padding: 12px 16px 0;
}

.cl-contract-modal :deep(.ant-modal-header) {
  padding: 12px 16px;
}

.cl-contract-modal :deep(.ant-modal-close) {
  top: 10px;
}

.cl-contract-modal :deep(.ant-modal-title) {
  font-size: 15px;
  line-height: 22px;
}

.cl-redesign-page {
  background: var(--surface-subtle);
}

.cl-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.cl-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.cl-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.cl-query-panel {
  align-items: stretch;
  flex-direction: column;
  gap: 12px;
  margin: 0;
}

.cl-query-primary {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.cl-query-keyword-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.cl-keyword-search {
  flex: 1 1 auto;
  min-width: 320px;
}

.cl-search-prefix-icon {
  color: var(--text-secondary);
}

.cl-query-select {
  width: 160px;
}

.cl-date-filter {
  width: 260px;
}

.cl-query-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.cl-workspace {
}

.cl-main-column {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.cl-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  min-height: 0;
}

.cl-table-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}

.cl-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.cl-table-heading,
.cl-table-toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cl-table-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.cl-table-wrap {
  flex: 1;
  min-height: 0;
}

.cl-table-wrap :deep(.vxe-header--column .vxe-cell) {
  justify-content: center;
  text-align: center;
}

.cl-table-wrap :deep(.vxe-grid) {
  height: 100%;
}

.cl-workspace :deep(.cl-analysis-rail) {
  min-height: 0;
}

.cl-workspace :deep(.cl-analysis-panel) {
  height: 100%;
}

.cl-contract-link {
  height: auto;
  padding: 0;
  font-weight: 700;
}

.cl-pagination {
  border-top: 1px solid var(--border-subtle);
}

@media (max-width: 1200px) {
  .cl-page-head,
  .cl-query-panel,
  .cl-query-keyword-row,
  .cl-query-primary {
    align-items: stretch;
    flex-direction: column;
  }

  .cl-query-actions {
    justify-content: flex-start;
  }

  .cl-table-toolbar-right {
    flex-wrap: wrap;
  }

  .cl-keyword-search,
  .cl-query-select,
  .cl-date-filter {
    width: 100%;
    min-width: 0;
  }
}
</style>
