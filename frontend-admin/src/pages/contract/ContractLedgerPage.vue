<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import {
  PlusOutlined,
  SettingOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import ContractFormPage from './ContractFormPage.vue'
import ContractStatusTag from '@/components/ContractStatusTag.vue'
import ContractKpiStrip from './components/ContractKpiStrip.vue'
import ContractMobileCardList from './components/ContractMobileCardList.vue'
import ContractAnalysisPanel from './components/ContractAnalysisPanel.vue'
import { useContractLedger, TYPE_LABEL, TYPE_COLOR } from './composables/useContractLedger'
import type { ContractVO, ContractType, ContractStatus } from '@/types/contract'

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
  defaultCols,
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
  gridColumns,
} = useContractLedger()

// ---- Mobile detection ----
const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>合同管理</a-breadcrumb-item>
          <a-breadcrumb-item>合同台账</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索合同编号、名称、甲方、乙方…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <!-- 左列 -->
      <div class="lg-left">
        <!-- KPI -->
        <ContractKpiStrip
          :kpi="kpi"
          :is-mobile="isMobile"
          :fmt-amount="fmtAmount"
          :kpi-max="kpiMax"
          :kpi-pct="kpiPct"
        />

        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button type="primary" @click="handleCreate">
              <template #icon><PlusOutlined /></template>
              新建合同
            </a-button>
            <a-button @click="fetchData">
              <template #icon><ReloadOutlined /></template>
            </a-button>
            <a-dropdown v-if="!isMobile">
              <a-button>
                <template #icon><SettingOutlined /></template>
                列设置
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item v-for="(_, key) in defaultCols" :key="key" @click="toggleCol(key)">
                    <a-checkbox :checked="colVisible[key]">
                      {{
                        {
                          contractCode: '合同编号',
                          contractName: '合同名称',
                          contractType: '合同类型',
                          partyAName: '甲方',
                          partyBName: '乙方',
                          contractAmount: '合同金额',
                          signedDate: '签订日期',
                          contractStatus: '合同状态',
                          ops: '操作',
                        }[key]
                      }}
                    </a-checkbox>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div class="lg-toolbar-right">
            <a-select
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              style="width: 160px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
          </div>
        </div>

        <!-- 表格：桌面/平板 -->
        <div v-if="!isMobile" class="lg-table-wrap">
          <vxe-grid
            :data="tableData"
            :columns="gridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            :checkbox-config="{ highlight: true }"
            stripe
            border="inner"
            size="small"
            max-height="480"
          >
            <template #contractCode="{ row }">
              <a class="lg-link">{{ row.contractCode }}</a>
            </template>
            <template #contractType="{ row }">
              <a-tag :color="TYPE_COLOR[row.contractType as ContractType]">
                {{ TYPE_LABEL[row.contractType as ContractType] }}
              </a-tag>
            </template>
            <template #amount="{ row }">
              <span class="lg-money">{{
                parseFloat(row.contractAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
            <template #status="{ row }">
              <ContractStatusTag :status="row.contractStatus as ContractStatus" />
            </template>
            <template #ops="{ row }">
              <div class="lg-ops">
                <a class="lg-link" @click="handleView(row)">查看</a>
                <a class="lg-link" @click="handleEdit(row)">编辑</a>
                <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
              </div>
            </template>
          </vxe-grid>
        </div>

        <!-- 移动端卡片列表 -->
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

        <!-- 分页 -->
        <div class="lg-pagination">
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
      </div>

      <!-- 右侧分析面板 -->
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
    :width="1180"
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
/* 仅保留页面专属样式 — 其余已由 lg-* 全局类覆盖 */

.cl-contract-modal :deep(.ant-modal-body) {
  max-height: 82vh;
  overflow: auto;
}
.cl-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
</style>
