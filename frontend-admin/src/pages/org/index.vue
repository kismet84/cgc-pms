<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import {
  getCompanyList,
  createCompany,
  updateCompany,
  deleteCompany,
  getDepartmentTree,
  createDepartment,
  updateDepartment,
  deleteDepartment,
  getPositionList,
  createPosition,
  updatePosition,
  deletePosition,
} from '@/api/modules/org'
import type { OrgCompanyVO, OrgDepartmentTreeNodeVO, OrgPositionVO } from '@/types/org'
import type { PageResult } from '@/types/api'

const userStore = useUserStore()

// ─── Page loading ────────────────────────────────────────

const loading = ref(true)

// ─── Permission checks ───────────────────────────────────

const canAdd = computed(() => userStore.hasPermission('org:add'))
const canEdit = computed(() => userStore.hasPermission('org:edit'))
const canDelete = computed(() => userStore.hasPermission('org:delete'))

// ─── Company state ───────────────────────────────────────

const companyLoading = ref(false)
const companyData = ref<OrgCompanyVO[]>([])
const companyTotal = ref(0)
const companyPageNo = ref(1)
const companyPageSize = ref(20)
const selectedCompanyId = ref<string | null>(null)

const companyFilter = reactive({
  companyCode: '',
  companyName: '',
  status: undefined as string | undefined,
})

const companyColumns = [
  { title: '公司编号', dataIndex: 'companyCode', width: 120 },
  { title: '公司名称', dataIndex: 'companyName', minWidth: 140 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', width: 150 },
]

// ─── Department tree state ───────────────────────────────

const deptTreeLoading = ref(false)
const deptTreeData = ref<OrgDepartmentTreeNodeVO[]>([])
const selectedDeptKeys = ref<string[]>([])

const filteredDeptTree = computed(() => {
  if (!selectedCompanyId.value) return deptTreeData.value
  return deptTreeData.value.filter((n) => n.companyId === selectedCompanyId.value)
})

// ─── Position state ──────────────────────────────────────

const positionLoading = ref(false)
const positionData = ref<OrgPositionVO[]>([])
const positionTotal = ref(0)
const positionPageNo = ref(1)
const positionPageSize = ref(20)

const positionFilter = reactive({
  positionCode: '',
  positionName: '',
  status: undefined as string | undefined,
})

const positionColumns = [
  { title: '岗位编号', dataIndex: 'positionCode', width: 120 },
  { title: '岗位名称', dataIndex: 'positionName', minWidth: 140 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', width: 150 },
]

// ─── Modal state ─────────────────────────────────────────

const companyModalVisible = ref(false)
const companyModalTitle = ref('新增公司')
const companyForm = reactive({
  id: '' as string,
  companyCode: '',
  companyName: '',
  status: 'ENABLED',
  remark: '',
})
const companySaving = ref(false)

const deptModalVisible = ref(false)
const deptModalTitle = ref('新增部门')
const deptForm = reactive({
  id: '' as string,
  companyId: '' as string,
  parentId: '' as string,
  deptCode: '',
  deptName: '',
  orderNum: 1,
  status: 'ENABLED',
  remark: '',
})
const deptSaving = ref(false)

const positionModalVisible = ref(false)
const positionModalTitle = ref('新增岗位')
const positionForm = reactive({
  id: '' as string,
  positionCode: '',
  positionName: '',
  status: 'ENABLED',
  remark: '',
})
const positionSaving = ref(false)

// ─── Company data ────────────────────────────────────────

async function fetchCompanies() {
  companyLoading.value = true
  try {
    const res: PageResult<OrgCompanyVO> = await getCompanyList({
      pageNum: companyPageNo.value,
      pageSize: companyPageSize.value,
      companyCode: companyFilter.companyCode || undefined,
      companyName: companyFilter.companyName || undefined,
      status: companyFilter.status,
    })
    companyData.value = res.records
    companyTotal.value = res.total
  } catch (e: unknown) {
    console.error(e)
    companyData.value = []
    companyTotal.value = 0
    message.error('加载公司列表失败')
  } finally {
    companyLoading.value = false
  }
}

function handleCompanySearch() {
  companyPageNo.value = 1
  fetchCompanies()
}

function handleCompanyReset() {
  companyFilter.companyCode = ''
  companyFilter.companyName = ''
  companyFilter.status = undefined
  companyPageNo.value = 1
  fetchCompanies()
}

function handleCompanyPageChange(page: number) {
  companyPageNo.value = page
  fetchCompanies()
}

function handleCompanyPageSizeChange(_cur: number, size: number) {
  companyPageSize.value = size
  companyPageNo.value = 1
  fetchCompanies()
}

function handleCompanyRowClick(record: OrgCompanyVO) {
  selectedCompanyId.value = record.id
}

function getCompanyRowClass(record: OrgCompanyVO) {
  return record.id === selectedCompanyId.value ? 'org-row-selected' : ''
}

// ─── Department tree ─────────────────────────────────────

async function fetchDeptTree() {
  deptTreeLoading.value = true
  try {
    deptTreeData.value = await getDepartmentTree()
  } catch (e: unknown) {
    console.error(e)
    deptTreeData.value = []
    message.error('加载部门树失败')
  } finally {
    deptTreeLoading.value = false
  }
}

function handleDeptSelect(
  _selectedKeys: string[],
  info: { node: { dataRef: OrgDepartmentTreeNodeVO } },
) {
  // Selection handled by v-model:selectedKeys
}

// ─── Company modal ───────────────────────────────────────

function openCompanyAdd() {
  companyForm.id = ''
  companyForm.companyCode = ''
  companyForm.companyName = ''
  companyForm.status = 'ENABLED'
  companyForm.remark = ''
  companyModalTitle.value = '新增公司'
  companyModalVisible.value = true
}

function openCompanyEdit(record: OrgCompanyVO) {
  companyForm.id = record.id
  companyForm.companyCode = record.companyCode
  companyForm.companyName = record.companyName
  companyForm.status = record.status
  companyForm.remark = record.remark ?? ''
  companyModalTitle.value = '编辑公司'
  companyModalVisible.value = true
}

async function handleCompanySave() {
  companySaving.value = true
  try {
    const body = {
      companyCode: companyForm.companyCode,
      companyName: companyForm.companyName,
      status: companyForm.status,
      remark: companyForm.remark || undefined,
    }
    if (companyForm.id) {
      await updateCompany(companyForm.id, body)
      message.success('公司更新成功')
    } else {
      await createCompany(body)
      message.success('公司创建成功')
    }
    companyModalVisible.value = false
    await fetchCompanies()
  } catch (e: unknown) {
    console.error(e)
    message.error('操作失败')
  } finally {
    companySaving.value = false
  }
}

async function handleCompanyDelete(record: OrgCompanyVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除公司「${record.companyName}」吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteCompany(record.id)
        message.success('已删除')
        if (selectedCompanyId.value === record.id) selectedCompanyId.value = null
        await fetchCompanies()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败')
      }
    },
  })
}

// ─── Department modal ────────────────────────────────────

function openDeptAdd() {
  deptForm.id = ''
  deptForm.companyId = selectedCompanyId.value ?? ''
  deptForm.parentId = selectedDeptKeys.value[0] ?? ''
  deptForm.deptCode = ''
  deptForm.deptName = ''
  deptForm.orderNum = 1
  deptForm.status = 'ENABLED'
  deptForm.remark = ''
  deptModalTitle.value = '新增部门'
  deptModalVisible.value = true
}

function openDeptEdit() {
  const key = selectedDeptKeys.value[0]
  if (!key) {
    message.warning('请先选择一个部门')
    return
  }
  const node = findDeptNode(deptTreeData.value, key)
  if (!node) {
    message.warning('未找到该部门')
    return
  }
  deptForm.id = node.id
  deptForm.companyId = node.companyId
  deptForm.parentId = node.parentId ?? ''
  deptForm.deptCode = node.deptCode
  deptForm.deptName = node.deptName
  deptForm.orderNum = node.orderNum
  deptForm.status = node.status
  deptForm.remark = ''
  deptModalTitle.value = '编辑部门'
  deptModalVisible.value = true
}

function findDeptNode(
  nodes: OrgDepartmentTreeNodeVO[],
  id: string,
): OrgDepartmentTreeNodeVO | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children && node.children.length > 0) {
      const found = findDeptNode(node.children, id)
      if (found) return found
    }
  }
  return null
}

async function handleDeptSave() {
  deptSaving.value = true
  try {
    const body = {
      companyId: deptForm.companyId,
      parentId: deptForm.parentId || undefined,
      deptCode: deptForm.deptCode,
      deptName: deptForm.deptName,
      orderNum: deptForm.orderNum,
      status: deptForm.status,
      remark: deptForm.remark || undefined,
    }
    if (deptForm.id) {
      await updateDepartment(deptForm.id, body)
      message.success('部门更新成功')
    } else {
      await createDepartment(body)
      message.success('部门创建成功')
    }
    deptModalVisible.value = false
    await fetchDeptTree()
  } catch (e: unknown) {
    console.error(e)
    message.error('操作失败')
  } finally {
    deptSaving.value = false
  }
}

async function handleDeptDelete() {
  const key = selectedDeptKeys.value[0]
  if (!key) {
    message.warning('请先选择一个部门')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: '确定要删除该部门吗？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteDepartment(key)
        message.success('已删除')
        selectedDeptKeys.value = []
        await fetchDeptTree()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败')
      }
    },
  })
}

// ─── Position data ───────────────────────────────────────

async function fetchPositions() {
  positionLoading.value = true
  try {
    const res: PageResult<OrgPositionVO> = await getPositionList({
      pageNum: positionPageNo.value,
      pageSize: positionPageSize.value,
      positionCode: positionFilter.positionCode || undefined,
      positionName: positionFilter.positionName || undefined,
      status: positionFilter.status,
    })
    positionData.value = res.records
    positionTotal.value = res.total
  } catch (e: unknown) {
    console.error(e)
    positionData.value = []
    positionTotal.value = 0
    message.error('加载岗位列表失败')
  } finally {
    positionLoading.value = false
  }
}

function handlePositionSearch() {
  positionPageNo.value = 1
  fetchPositions()
}

function handlePositionReset() {
  positionFilter.positionCode = ''
  positionFilter.positionName = ''
  positionFilter.status = undefined
  positionPageNo.value = 1
  fetchPositions()
}

function handlePositionPageChange(page: number) {
  positionPageNo.value = page
  fetchPositions()
}

function handlePositionPageSizeChange(_cur: number, size: number) {
  positionPageSize.value = size
  positionPageNo.value = 1
  fetchPositions()
}

// ─── Position modal ──────────────────────────────────────

function openPositionAdd() {
  positionForm.id = ''
  positionForm.positionCode = ''
  positionForm.positionName = ''
  positionForm.status = 'ENABLED'
  positionForm.remark = ''
  positionModalTitle.value = '新增岗位'
  positionModalVisible.value = true
}

function openPositionEdit(record: OrgPositionVO) {
  positionForm.id = record.id
  positionForm.positionCode = record.positionCode
  positionForm.positionName = record.positionName
  positionForm.status = record.status
  positionForm.remark = record.remark ?? ''
  positionModalTitle.value = '编辑岗位'
  positionModalVisible.value = true
}

async function handlePositionSave() {
  positionSaving.value = true
  try {
    const body = {
      positionCode: positionForm.positionCode,
      positionName: positionForm.positionName,
      status: positionForm.status,
      remark: positionForm.remark || undefined,
    }
    if (positionForm.id) {
      await updatePosition(positionForm.id, body)
      message.success('岗位更新成功')
    } else {
      await createPosition(body)
      message.success('岗位创建成功')
    }
    positionModalVisible.value = false
    await fetchPositions()
  } catch (e: unknown) {
    console.error(e)
    message.error('操作失败')
  } finally {
    positionSaving.value = false
  }
}

async function handlePositionDelete(record: OrgPositionVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除岗位「${record.positionName}」吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deletePosition(record.id)
        message.success('已删除')
        await fetchPositions()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败')
      }
    },
  })
}

// ─── Init ────────────────────────────────────────────────

onMounted(async () => {
  await Promise.all([fetchCompanies(), fetchDeptTree(), fetchPositions()])
  loading.value = false
})
</script>

<template>
  <a-spin :spinning="loading">
    <div class="org-page">
      <a-page-header title="组织架构管理" class="org-header" />

      <!-- ================== Top Row: Company + Department ================== -->
      <div class="org-top-row">
        <!-- Companies -->
        <div class="org-card org-company-panel">
          <div class="org-panel-header">
            <span class="org-panel-title">公司管理</span>
            <div class="org-panel-actions">
              <a-button v-if="canAdd" type="primary" size="small" @click="openCompanyAdd">
                新增
              </a-button>
            </div>
          </div>

          <div class="org-filter-mini">
            <a-input
              v-model:value="companyFilter.companyName"
              placeholder="公司名称"
              size="small"
              style="width: 140px"
              allow-clear
              @press-enter="handleCompanySearch"
            />
            <a-select
              v-model:value="companyFilter.status"
              placeholder="状态"
              size="small"
              allow-clear
              style="width: 90px"
            >
              <a-select-option value="ENABLED">启用</a-select-option>
              <a-select-option value="DISABLED">禁用</a-select-option>
            </a-select>
            <a-button size="small" @click="handleCompanySearch">查询</a-button>
          </div>

          <a-table
            :columns="companyColumns"
            :data-source="companyData"
            :loading="companyLoading"
            :pagination="false"
            row-key="id"
            size="small"
            :custom-row="
              (record: OrgCompanyVO) => ({
                onClick: () => handleCompanyRowClick(record),
                class: getCompanyRowClass(record),
              })
            "
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'status'">
                <a-tag :color="record.status === 'ENABLED' ? 'success' : 'default'">
                  {{ record.status === 'ENABLED' ? '启用' : '禁用' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'ops'">
                <a-button
                  v-if="canEdit"
                  size="small"
                  type="link"
                  @click.stop="openCompanyEdit(record)"
                  >编辑</a-button
                >
                <a-button
                  v-if="canDelete"
                  size="small"
                  type="link"
                  danger
                  @click.stop="handleCompanyDelete(record)"
                  >删除</a-button
                >
              </template>
            </template>
          </a-table>

          <div class="org-panel-footer">
            <span class="org-total">共 {{ companyTotal }} 条</span>
            <a-pagination
              v-model:current="companyPageNo"
              v-model:page-size="companyPageSize"
              :total="companyTotal"
              size="small"
              :page-size-options="['10', '20', '50']"
              show-size-changer
              @change="handleCompanyPageChange"
              @show-size-change="handleCompanyPageSizeChange"
            />
          </div>
        </div>

        <!-- Departments Tree -->
        <div class="org-card org-dept-panel">
          <div class="org-panel-header">
            <span class="org-panel-title">部门架构</span>
            <div class="org-panel-actions">
              <a-button v-if="canAdd" size="small" @click="openDeptAdd">新增</a-button>
              <a-button
                v-if="canEdit"
                size="small"
                :disabled="!selectedDeptKeys.length"
                @click="openDeptEdit"
                >编辑</a-button
              >
              <a-button
                v-if="canDelete"
                size="small"
                danger
                :disabled="!selectedDeptKeys.length"
                @click="handleDeptDelete"
                >删除</a-button
              >
            </div>
          </div>

          <div class="org-tree-wrap">
            <a-spin :spinning="deptTreeLoading">
              <a-tree
                v-model:selected-keys="selectedDeptKeys"
                :tree-data="filteredDeptTree"
                :field-names="{ title: 'deptName', key: 'id', children: 'children' }"
                default-expand-all
                block-node
                @select="handleDeptSelect"
              />
            </a-spin>
          </div>
        </div>
      </div>

      <!-- ================== Bottom Row: Positions ================== -->
      <div class="org-card org-position-panel">
        <div class="org-panel-header">
          <span class="org-panel-title">岗位管理</span>
          <div class="org-panel-actions">
            <a-button v-if="canAdd" type="primary" size="small" @click="openPositionAdd"
              >新增</a-button
            >
          </div>
        </div>

        <div class="org-filter-mini">
          <a-input
            v-model:value="positionFilter.positionName"
            placeholder="岗位名称"
            size="small"
            style="width: 140px"
            allow-clear
            @press-enter="handlePositionSearch"
          />
          <a-select
            v-model:value="positionFilter.status"
            placeholder="状态"
            size="small"
            allow-clear
            style="width: 90px"
          >
            <a-select-option value="ENABLED">启用</a-select-option>
            <a-select-option value="DISABLED">禁用</a-select-option>
          </a-select>
          <a-button size="small" @click="handlePositionSearch">查询</a-button>
          <a-button size="small" @click="handlePositionReset">重置</a-button>
        </div>

        <a-table
          :columns="positionColumns"
          :data-source="positionData"
          :loading="positionLoading"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'status'">
              <a-tag :color="record.status === 'ENABLED' ? 'success' : 'default'">
                {{ record.status === 'ENABLED' ? '启用' : '禁用' }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'ops'">
              <a-button v-if="canEdit" size="small" type="link" @click="openPositionEdit(record)"
                >编辑</a-button
              >
              <a-button
                v-if="canDelete"
                size="small"
                type="link"
                danger
                @click="handlePositionDelete(record)"
                >删除</a-button
              >
            </template>
          </template>
        </a-table>

        <div class="org-panel-footer">
          <span class="org-total">共 {{ positionTotal }} 条</span>
          <a-pagination
            v-model:current="positionPageNo"
            v-model:page-size="positionPageSize"
            :total="positionTotal"
            size="small"
            :page-size-options="['10', '20', '50']"
            show-size-changer
            @change="handlePositionPageChange"
            @show-size-change="handlePositionPageSizeChange"
          />
        </div>
      </div>

      <!-- ================== Company Modal ================== -->
      <a-modal
        v-model:open="companyModalVisible"
        :title="companyModalTitle"
        :confirm-loading="companySaving"
        @ok="handleCompanySave"
      >
        <a-form layout="vertical">
          <a-form-item label="公司编号" required>
            <a-input v-model:value="companyForm.companyCode" placeholder="请输入公司编号" />
          </a-form-item>
          <a-form-item label="公司名称" required>
            <a-input v-model:value="companyForm.companyName" placeholder="请输入公司名称" />
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="companyForm.status" style="width: 100%">
              <a-select-option value="ENABLED">启用</a-select-option>
              <a-select-option value="DISABLED">禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="备注">
            <a-textarea v-model:value="companyForm.remark" placeholder="备注信息" :rows="2" />
          </a-form-item>
        </a-form>
      </a-modal>

      <!-- ================== Department Modal ================== -->
      <a-modal
        v-model:open="deptModalVisible"
        :title="deptModalTitle"
        :confirm-loading="deptSaving"
        @ok="handleDeptSave"
      >
        <a-form layout="vertical">
          <a-form-item label="所属公司" required>
            <a-select v-model:value="deptForm.companyId" placeholder="选择公司" style="width: 100%">
              <a-select-option v-for="c in companyData" :key="c.id" :value="c.id">
                {{ c.companyName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="部门编号" required>
            <a-input v-model:value="deptForm.deptCode" placeholder="请输入部门编号" />
          </a-form-item>
          <a-form-item label="部门名称" required>
            <a-input v-model:value="deptForm.deptName" placeholder="请输入部门名称" />
          </a-form-item>
          <a-form-item label="排序号">
            <a-input-number v-model:value="deptForm.orderNum" :min="0" style="width: 100%" />
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="deptForm.status" style="width: 100%">
              <a-select-option value="ENABLED">启用</a-select-option>
              <a-select-option value="DISABLED">禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="备注">
            <a-textarea v-model:value="deptForm.remark" placeholder="备注信息" :rows="2" />
          </a-form-item>
        </a-form>
      </a-modal>

      <!-- ================== Position Modal ================== -->
      <a-modal
        v-model:open="positionModalVisible"
        :title="positionModalTitle"
        :confirm-loading="positionSaving"
        @ok="handlePositionSave"
      >
        <a-form layout="vertical">
          <a-form-item label="岗位编号" required>
            <a-input v-model:value="positionForm.positionCode" placeholder="请输入岗位编号" />
          </a-form-item>
          <a-form-item label="岗位名称" required>
            <a-input v-model:value="positionForm.positionName" placeholder="请输入岗位名称" />
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="positionForm.status" style="width: 100%">
              <a-select-option value="ENABLED">启用</a-select-option>
              <a-select-option value="DISABLED">禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="备注">
            <a-textarea v-model:value="positionForm.remark" placeholder="备注信息" :rows="2" />
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
  </a-spin>
</template>

<style scoped>
.org-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}

.org-header {
  background: transparent;
  padding-bottom: 8px;
}

/* ─── Top row: side-by-side panels ─── */

.org-top-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}

.org-company-panel {
  flex: 0 0 42%;
  min-width: 380px;
}

.org-dept-panel {
  flex: 1;
}

/* ─── Card base ─── */

.org-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
  overflow: hidden;
}

/* ─── Panel header / footer ─── */

.org-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f5;
}

.org-panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.org-panel-actions {
  display: flex;
  gap: 6px;
}

.org-panel-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 8px 16px;
  border-top: 1px solid #f0f2f5;
}

.org-total {
  font-size: 12px;
  color: #9ca3af;
}

/* ─── Filter row ─── */

.org-filter-mini {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 16px;
  flex-wrap: wrap;
}

/* ─── Tree wrap ─── */

.org-tree-wrap {
  padding: 12px 16px;
  min-height: 280px;
  max-height: 480px;
  overflow: auto;
}

/* ─── Selected row highlight ─── */

:deep(.org-row-selected) {
  background: #e6f4ff !important;
}

:deep(.org-row-selected td) {
  background: #e6f4ff !important;
}

/* ─── Position panel ─── */

.org-position-panel {
  margin-bottom: 0;
}

/* ─── Cursor for clickable rows ─── */

:deep(.ant-table-row) {
  cursor: pointer;
}
</style>
