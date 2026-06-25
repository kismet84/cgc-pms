<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import { getUserList } from '@/api/modules/user'
import type { SysUserVO } from '@/types/user'
import type { SelectOption } from '@/types/ui'

import { useRequisitionList, fmtAmount } from './composables/useRequisitionList'
import { useRequisitionForm } from './composables/useRequisitionForm'
import RequisitionKpiStrip from './components/RequisitionKpiStrip.vue'
import RequisitionFormModal from './components/RequisitionFormModal.vue'

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = computed(() => referenceStore.contracts ?? [])

const userList = ref<SysUserVO[]>([])

const {
  filter,
  loading,
  tableData,
  total,
  pageNo,
  pageSize,
  warehouseList,
  kpiTotalCount,
  kpiTotalAmount,
  gridColumns,
  fetchData,
  handleSearch,
  handleReset,
  handlePageChange,
  handlePageSizeChange,
  handleDelete,
  handleSubmitApproval,
  init,
} = useRequisitionList()

const {
  modalVisible,
  modalTitle,
  formData,
  itemList,
  handleAdd,
  handleEdit,
  handleAddItem,
  handleRemoveItem,
  handleItemQtyChange,
  handleItemPriceChange,
  itemsTotalAmount,
  handleModalOk,
  handleModalCancel,
} = useRequisitionForm(fetchData)

const requisitionStatusSummary = computed(() => [
  {
    label: '已出库',
    count: tableData.value.filter((item) => item.stockOutFlag === 1).length,
    color: '#52c41a',
  },
  {
    label: '未出库',
    count: tableData.value.filter((item) => item.stockOutFlag !== 1).length,
    color: '#faad14',
  },
])

const recentRequisitions = computed(() => tableData.value.slice(0, 4))

async function fetchUsers() {
  try {
    const res = await getUserList({ pageNo: 1, pageSize: 200 })
    userList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    userList.value = []
  }
}

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'PURCHASE' })
  fetchUsers()
  init()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>领料申请</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.requisitionCode"
        placeholder="搜索领料单号…"
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

    <!-- KPI 横条 -->
    <RequisitionKpiStrip
      :total-count="kpiTotalCount"
      :total-amount="kpiTotalAmount"
      :fmt-amount="fmtAmount"
    />

    <div class="lg-grid">
      <main class="lg-list-table-panel">
        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button type="primary" @click="handleAdd">
              <template #icon><PlusOutlined /></template>
              新增领料申请
            </a-button>
            <a-button @click="fetchData">
              <template #icon><ReloadOutlined /></template>
            </a-button>
          </div>
          <div class="lg-toolbar-right">
            <a-select
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              style="width: 160px"
              size="small"
              show-search
              :filter-option="
                (input: string, option: SelectOption) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
              @change="
                (v: string | undefined) => {
                  filter.contractId = undefined
                  if (v) referenceStore.fetchContracts({ projectId: v })
                  handleSearch()
                }
              "
            >
              <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="filter.warehouseId"
              placeholder="全部仓库"
              allow-clear
              style="width: 160px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
                {{ w.warehouseName }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="filter.approvalStatus"
              placeholder="全部审批状态"
              allow-clear
              style="width: 140px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option value="DRAFT">草稿</a-select-option>
              <a-select-option value="APPROVING">审批中</a-select-option>
              <a-select-option value="APPROVED">已通过</a-select-option>
              <a-select-option value="REJECTED">已驳回</a-select-option>
            </a-select>
          </div>
        </div>

        <!-- 表格 -->
        <div class="lg-table-wrap">
          <vxe-grid
            :data="tableData"
            :columns="gridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            stripe
            border="inner"
            size="small"
          >
            <template #totalAmount="{ row }">
              <span v-if="row.totalAmount" class="lg-money">
                {{ Number(row.totalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
              </span>
              <span v-else class="lg-none">-</span>
            </template>
            <template #stockOutFlag="{ row }">
              <a-tag :color="row.stockOutFlag === 1 ? 'success' : 'default'">
                {{ row.stockOutFlag === 1 ? '已出库' : '未出库' }}
              </a-tag>
            </template>
            <template #approvalStatus="{ row }">
              <ApprovalStatusTag :status="row.approvalStatus" />
            </template>
            <template #action="{ row }">
              <div class="lg-ops">
                <a class="lg-link" @click="handleEdit(row)">编辑</a>
                <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
                <a
                  v-if="row.approvalStatus === 'DRAFT'"
                  class="lg-link"
                  @click="handleSubmitApproval(row)"
                  >提交审批</a
                >
              </div>
            </template>
          </vxe-grid>
        </div>

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
      </main>

      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">出库状态分布</div>
          <div class="lg-type-list">
            <div v-for="item in requisitionStatusSummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span style="margin-left: auto">{{ item.count }} 单</span>
            </div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">近期领料</div>
          <div class="lg-type-list">
            <div v-for="item in recentRequisitions" :key="item.id" class="lg-type-row">
              <span class="lg-type-dot" style="background: #1890ff"></span>
              <span class="lg-type-label">{{ item.requisitionCode }}</span>
            </div>
            <div v-if="!recentRequisitions.length" class="lg-warning-empty">暂无领料申请</div>
          </div>
        </section>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <RequisitionFormModal
      :visible="modalVisible"
      :title="modalTitle"
      :form-data="formData"
      :project-list="projectList"
      :contract-list="contractList"
      :warehouse-list="warehouseList"
      :user-list="userList"
      :item-list="itemList"
      :items-total-amount="itemsTotalAmount"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
      @item-qty-change="handleItemQtyChange"
      @item-price-change="handleItemPriceChange"
      @add-item="handleAddItem"
      @remove-item="handleRemoveItem"
    />
  </div>
</template>

<style scoped>
.lg-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
</style>
