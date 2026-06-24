<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { countDeptNodes } from './utils'
import { useCompany } from './composables/useCompany'
import { useDepartment } from './composables/useDepartment'
import { usePosition } from './composables/usePosition'
import OrgMetricStrip from './components/OrgMetricStrip.vue'
import CompanyModal from './components/CompanyModal.vue'
import DepartmentModal from './components/DepartmentModal.vue'
import PositionModal from './components/PositionModal.vue'
import CompanyPanel from './components/CompanyPanel.vue'
import DepartmentPanel from './components/DepartmentPanel.vue'
import PositionPanel from './components/PositionPanel.vue'

const userStore = useUserStore()

// ─── Page loading ────────────────────────────────────────

const loading = ref(true)

// ─── Permission checks ───────────────────────────────────

const canAdd = computed(() => userStore.hasPermission('org:add'))
const canEdit = computed(() => userStore.hasPermission('org:edit'))
const canDelete = computed(() => userStore.hasPermission('org:delete'))

// ─── Shared cross-module state ───────────────────────────

const selectedCompanyId = ref<string | null>(null)

// ─── Composables (destructured so refs auto-unwrap in templates) ──

const {
  loading: companyLoading,
  data: companyData,
  total: companyTotal,
  pageNo: companyPageNo,
  pageSize: companyPageSize,
  filter: companyFilter,
  gridColumns: companyGridColumns,
  fetchData: fetchCompanies,
  handleSearch: handleCompanySearch,
  handleReset: handleCompanyReset,
  handlePageChange: handleCompanyPageChange,
  handlePageSizeChange: handleCompanyPageSizeChange,
  handleRowClick: handleCompanyRowClick,
  modalVisible: companyModalVisible,
  modalTitle: companyModalTitle,
  form: companyForm,
  saving: companySaving,
  openAdd: openCompanyAdd,
  openEdit: openCompanyEdit,
  handleSave: handleCompanySave,
  handleDelete: handleCompanyDelete,
} = useCompany(selectedCompanyId)

const {
  treeLoading: deptTreeLoading,
  treeData: deptTreeData,
  selectedKeys: selectedDeptKeys,
  keyword: deptKeyword,
  filteredTree: filteredDeptTree,
  fetchTree: fetchDeptTree,
  handleSelect: handleDeptSelect,
  modalVisible: deptModalVisible,
  modalTitle: deptModalTitle,
  form: deptForm,
  saving: deptSaving,
  openAdd: openDeptAdd,
  openEdit: openDeptEdit,
  handleSave: handleDeptSave,
  handleDelete: handleDeptDelete,
} = useDepartment(selectedCompanyId)

const {
  loading: positionLoading,
  data: positionData,
  total: positionTotal,
  pageNo: positionPageNo,
  pageSize: positionPageSize,
  filter: positionFilter,
  gridColumns: positionGridColumns,
  flatDeptList,
  filterDeptList,
  modalDeptList,
  fetchData: fetchPositions,
  handleSearch: handlePositionSearch,
  handleReset: handlePositionReset,
  handlePageChange: handlePositionPageChange,
  handlePageSizeChange: handlePositionPageSizeChange,
  modalVisible: positionModalVisible,
  modalTitle: positionModalTitle,
  form: positionForm,
  saving: positionSaving,
  openAdd: openPositionAdd,
  openEdit: openPositionEdit,
  handleSave: handlePositionSave,
  handleDelete: handlePositionDelete,
} = usePosition(deptTreeData)

// ─── Page metrics (KPI) ──────────────────────────────────

const currentCompanyName = computed(() => {
  const found = companyData.value.find((c) => c.id === selectedCompanyId.value) ?? null
  return found?.companyName ?? '全部公司'
})

const departmentCount = computed(() => countDeptNodes(filteredDeptTree.value))
const enabledCompanyCount = computed(
  () => companyData.value.filter((c) => c.status === 'ENABLED').length,
)
const enabledPositionCount = computed(
  () => positionData.value.filter((p) => p.status === 'ENABLED').length,
)
const enabledRate = computed(() => {
  const total = companyData.value.length + positionData.value.length
  if (!total) return '0.0'
  return (((enabledCompanyCount.value + enabledPositionCount.value) / total) * 100).toFixed(1)
})

// ─── Init ────────────────────────────────────────────────

onMounted(async () => {
  await Promise.all([fetchCompanies(), fetchDeptTree(), fetchPositions()])
  loading.value = false
})
</script>

<template>
  <a-spin :spinning="loading">
    <div class="lg-page app-page org-page">
      <div class="lg-page-head">
        <div>
          <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
            <a-breadcrumb-item>系统管理</a-breadcrumb-item>
            <a-breadcrumb-item>组织架构</a-breadcrumb-item>
          </a-breadcrumb>
        </div>
      </div>

      <OrgMetricStrip
        :company-total="companyTotal"
        :current-company-name="currentCompanyName"
        :department-count="departmentCount"
        :position-total="positionTotal"
        :enabled-rate="enabledRate"
      />

      <div class="org-workspace">
        <CompanyPanel
          :can-add="canAdd"
          :can-edit="canEdit"
          :can-delete="canDelete"
          :loading="companyLoading"
          :data="companyData"
          :total="companyTotal"
          :page-no="companyPageNo"
          :page-size="companyPageSize"
          :filter="companyFilter"
          :grid-columns="companyGridColumns"
          @search="handleCompanySearch()"
          @reset="handleCompanyReset()"
          @page-change="handleCompanyPageChange($event)"
          @page-size-change="(cur: number, size: number) => handleCompanyPageSizeChange(cur, size)"
          @row-click="handleCompanyRowClick($event)"
          @add="openCompanyAdd()"
          @edit="openCompanyEdit($event)"
          @delete="handleCompanyDelete($event)"
        />

        <DepartmentPanel
          :can-add="canAdd"
          :can-edit="canEdit"
          :can-delete="canDelete"
          :tree-loading="deptTreeLoading"
          :filtered-tree="filteredDeptTree"
          :selected-keys="selectedDeptKeys"
          :keyword="deptKeyword"
          :current-company-name="currentCompanyName"
          :department-count="departmentCount"
          :selected-company-id="selectedCompanyId"
          @update:selected-keys="selectedDeptKeys = $event"
          @update:keyword="deptKeyword = $event"
          @update:selected-company-id="selectedCompanyId = $event"
          @select="handleDeptSelect()"
          @add="openDeptAdd()"
          @edit="openDeptEdit()"
          @delete="handleDeptDelete()"
        />
      </div>

      <PositionPanel
        :can-add="canAdd"
        :can-edit="canEdit"
        :can-delete="canDelete"
        :loading="positionLoading"
        :data="positionData"
        :total="positionTotal"
        :page-no="positionPageNo"
        :page-size="positionPageSize"
        :filter="positionFilter"
        :grid-columns="positionGridColumns"
        :company-options="companyData"
        :filter-dept-list="filterDeptList"
        :flat-dept-list="flatDeptList"
        :company-data="companyData"
        @search="handlePositionSearch()"
        @reset="handlePositionReset()"
        @page-change="handlePositionPageChange($event)"
        @page-size-change="(cur: number, size: number) => handlePositionPageSizeChange(cur, size)"
        @add="openPositionAdd()"
        @edit="openPositionEdit($event)"
        @delete="handlePositionDelete($event)"
      />

      <!-- ================== Modals ================== -->
      <CompanyModal
        :open="companyModalVisible"
        :title="companyModalTitle"
        :loading="companySaving"
        :form="companyForm"
        @update:open="companyModalVisible = $event"
        @ok="handleCompanySave()"
      />

      <DepartmentModal
        :open="deptModalVisible"
        :title="deptModalTitle"
        :loading="deptSaving"
        :form="deptForm"
        :company-options="companyData"
        @update:open="deptModalVisible = $event"
        @ok="handleDeptSave()"
      />

      <PositionModal
        :open="positionModalVisible"
        :title="positionModalTitle"
        :loading="positionSaving"
        :form="positionForm"
        :company-options="companyData"
        :dept-options="modalDeptList"
        @update:open="positionModalVisible = $event"
        @ok="handlePositionSave()"
      />
    </div>
  </a-spin>
</template>

<style scoped>
.org-panel-header p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.45;
}

.org-panel-actions,
.org-panel-footer {
  display: flex;
  align-items: center;
}

.org-panel-actions {
  gap: 8px;
  justify-content: flex-end;
}

.org-panel-actions :deep(.ant-btn),
.org-panel-header > :deep(.ant-btn) {
  height: 34px;
  padding: 0 16px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
}

.org-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  margin-bottom: 16px;
  align-items: stretch;
}

.org-panel {
  min-width: 0;
  overflow: hidden;
  background: var(--surface);
  border: 0;
  border-radius: 8px;
  box-shadow: var(--shadow-soft);
}

.org-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-height: 0;
  padding: 16px 20px 0;
  border-bottom: 0;
}

.org-panel-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0;
}

/* ---- Search bars inside org panels (overrides for panel context) ---- */
.lg-search-bar {
  gap: 12px;
  padding: 12px 20px;
  margin-bottom: 0;
  background: var(--surface);
  border-radius: 0;
  border-bottom: 1px solid var(--border-subtle);
  box-shadow: none;
}

.lg-search-bar :deep(.ant-input-affix-wrapper),
.lg-search-bar :deep(.ant-select) {
  width: 160px;
}

.lg-search-bar.one-line :deep(.ant-input-affix-wrapper) {
  width: 100%;
}

.lg-search-bar.position :deep(.ant-input-affix-wrapper),
.lg-search-bar.position :deep(.ant-select) {
  width: 160px;
}

.lg-search-bar :deep(.ant-input),
.lg-search-bar :deep(.ant-select-selector),
.lg-search-bar :deep(.ant-btn) {
  height: 34px;
  border-radius: 4px;
  font-size: 14px;
}

.lg-search-bar :deep(.ant-select-selection-item),
.lg-search-bar :deep(.ant-select-selection-placeholder) {
  line-height: 32px;
}

.org-table :deep(.ant-table) {
  color: var(--text);
  font-size: 13px;
}

.org-table :deep(.ant-table-thead > tr > th) {
  color: var(--text-secondary);
  background: #fff;
  border-bottom-color: var(--border-subtle);
  font-size: 14px;
  font-weight: 500;
}

.org-table :deep(.ant-table-tbody > tr > td) {
  padding-top: 12px;
  padding-bottom: 12px;
  border-bottom-color: var(--border-subtle);
}

.org-table :deep(.ant-table-tbody > tr:hover > td),
.org-table :deep(.ant-table-tbody > tr.org-row-selected > td) {
  background: #eef6ff;
}

.org-table :deep(.ant-btn-link) {
  height: 24px;
  padding: 0 4px;
  font-size: 12px;
  font-weight: 600;
}

.org-panel-footer {
  justify-content: space-between;
  gap: 16px;
  min-height: 60px;
  padding: 16px 20px 0;
  color: var(--text-secondary);
  border-top: 1px solid var(--border-subtle);
  margin-top: 16px;
  font-size: 14px;
}

.org-dept-panel {
  display: flex;
  flex-direction: column;
}

.org-dept-focus {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 20px 0;
  background: var(--surface);
  border-bottom: 0;
}

.org-dept-focus span {
  display: block;
  color: var(--muted);
  font-size: 13px;
}

.org-tree-wrap {
  min-height: 194px;
  padding: 0 20px 0;
  flex: 1;
}

.org-tree-wrap :deep(.ant-tree) {
  background: transparent;
  font-size: 14px;
}

.org-tree-wrap :deep(.ant-tree-node-content-wrapper) {
  min-height: 39px;
  padding: 5px 0;
  border-radius: 0;
}

.org-tree-wrap :deep(.ant-tree-node-content-wrapper:hover),
.org-tree-wrap :deep(.ant-tree-node-selected) {
  background: #eef6ff !important;
}

.org-empty-hint {
  padding: 40px 12px;
  color: #94a3b8;
  font-size: 14px;
  text-align: center;
}

.org-position-panel {
  min-height: 0;
  margin-bottom: 4px;
}

.org-position-panel .org-panel-header {
  min-height: 0;
}

.org-position-panel .org-table {
  min-height: 168px;
}

.org-table :deep(.vxe-header--column) {
  background: #fff;
  color: var(--muted);
  font-size: 14px;
  font-weight: 500;
  height: 42px;
}

.org-table :deep(.vxe-body--column) {
  color: var(--text-secondary);
  font-size: 14px;
  height: 48px;
}

.org-table :deep(.vxe-body--row:hover),
.org-table :deep(.vxe-body--row:hover .vxe-body--column),
.org-table :deep(.vxe-body--row.org-row-selected),
.org-table :deep(.vxe-body--row.org-row-selected .vxe-body--column) {
  background: #f5f9ff;
}

.org-page :deep(.org-panel-header p) {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.45;
}

.org-page :deep(.org-panel-actions),
.org-page :deep(.org-panel-footer) {
  display: flex;
  align-items: center;
}

.org-page :deep(.org-panel-actions) {
  gap: 8px;
  justify-content: flex-end;
}

.org-page :deep(.org-dept-panel .org-panel-actions .ant-btn) {
  height: auto;
  padding: 0 8px;
  background: transparent;
  border: 0;
  box-shadow: none;
  color: var(--primary);
  font-size: 14px;
}

.org-page :deep(.org-dept-panel .org-panel-actions .ant-btn-dangerous) {
  color: var(--error);
}

.org-page :deep(.org-panel-header > .ant-btn),
.org-page :deep(.org-position-panel .org-panel-header > .ant-btn) {
  height: 34px;
  padding: 0 16px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
}

.org-page :deep(.org-panel-header) {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-height: 0;
  padding: 16px 20px 0;
  border-bottom: 0;
}

.org-page :deep(.org-panel-title) {
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0;
}

.org-page :deep(.lg-search-bar) {
  gap: 12px;
  padding: 12px 20px;
  margin-bottom: 0;
  background: var(--surface);
  border-bottom: 1px solid var(--border-subtle);
  border-radius: 0;
  box-shadow: none;
}

.org-page :deep(.lg-search-bar .ant-input-affix-wrapper),
.org-page :deep(.lg-search-bar .ant-select) {
  width: 160px;
}

.org-page :deep(.lg-search-bar.one-line .ant-input-affix-wrapper) {
  width: 100%;
}

.org-page :deep(.lg-search-bar .ant-input),
.org-page :deep(.lg-search-bar .ant-select-selector),
.org-page :deep(.lg-search-bar .ant-btn) {
  height: 34px;
  border-radius: 4px;
  font-size: 14px;
}

.org-page :deep(.lg-search-bar .ant-select-selection-item),
.org-page :deep(.lg-search-bar .ant-select-selection-placeholder) {
  line-height: 32px;
}

.org-page :deep(.org-table .vxe-header--column) {
  height: 42px;
  background: #fff;
  color: var(--muted);
  font-size: 14px;
  font-weight: 500;
}

.org-page :deep(.org-table .vxe-body--column) {
  height: 48px;
  color: var(--text-secondary);
  font-size: 14px;
}

.org-page :deep(.org-table .vxe-body--row:hover),
.org-page :deep(.org-table .vxe-body--row:hover .vxe-body--column),
.org-page :deep(.org-table .vxe-body--row.org-row-selected),
.org-page :deep(.org-table .vxe-body--row.org-row-selected .vxe-body--column) {
  background: #fafafa;
}

.org-page :deep(.org-table .ant-btn-link) {
  height: 24px;
  padding: 0 8px;
  font-size: 14px;
  font-weight: 400;
}

.org-page :deep(.org-panel-footer) {
  justify-content: flex-end;
  gap: 16px;
  min-height: 60px;
  padding: 16px 20px 0;
  margin-top: 16px;
  color: var(--text-secondary);
  border-top: 1px solid var(--border-subtle);
  font-size: 14px;
}

.org-page :deep(.org-dept-focus) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 20px 0;
  background: var(--surface);
  border-bottom: 0;
}

.org-page :deep(.org-dept-focus span) {
  display: block;
  color: var(--muted);
  font-size: 13px;
}

.org-page :deep(.org-tree-wrap) {
  min-height: 194px;
  padding: 0 20px;
  flex: 1;
}

.org-page :deep(.org-list) {
  flex: 1;
  margin: 0;
  padding: 0;
  list-style: none;
}

.org-page :deep(.org-list li) {
  padding: 10px 0;
  color: var(--text);
  border-bottom: 1px solid var(--border-subtle);
  font-size: 14px;
  cursor: pointer;
  transition:
    color 0.2s,
    background 0.2s,
    padding-left 0.2s;
}

.org-page :deep(.org-list li:last-child) {
  border-bottom: 0;
}

.org-page :deep(.org-list li:hover),
.org-page :deep(.org-list li.active) {
  padding-left: 8px;
  color: var(--primary);
  background: #fafafa;
}

@media (max-width: 1180px) {
  .org-workspace {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .org-dept-panel,
  .org-position-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .org-panel-header,
  .org-panel-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .org-panel-actions,
  .lg-search-bar {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .lg-search-bar :deep(.ant-input-affix-wrapper),
  .lg-search-bar :deep(.ant-select),
  .lg-search-bar.position :deep(.ant-input-affix-wrapper),
  .lg-search-bar.position :deep(.ant-select) {
    width: calc(50% - 4px);
  }

  .org-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .lg-search-bar :deep(.ant-input-affix-wrapper),
  .lg-search-bar :deep(.ant-select),
  .lg-search-bar.position :deep(.ant-input-affix-wrapper),
  .lg-search-bar.position :deep(.ant-select) {
    width: 100%;
  }
}
</style>
