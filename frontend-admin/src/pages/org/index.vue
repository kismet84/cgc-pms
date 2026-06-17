<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  ApartmentOutlined,
  BankOutlined,
  ClusterOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
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
  { title: '操作', dataIndex: 'ops', width: 120, align: 'right' as const },
]

// ─── Department tree state ───────────────────────────────

const deptTreeLoading = ref(false)
const deptTreeData = ref<OrgDepartmentTreeNodeVO[]>([])
const selectedDeptKeys = ref<string[]>([])
const deptKeyword = ref('')

const filteredDeptTree = computed(() => {
  const companyId = selectedCompanyId.value
  const keyword = deptKeyword.value.trim()
  return filterDeptNodes(deptTreeData.value, companyId, keyword)
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
  { title: '操作', dataIndex: 'ops', width: 120, align: 'right' as const },
]

// ─── Page metrics ───────────────────────────────────────

const selectedCompany = computed(
  () => companyData.value.find((company) => company.id === selectedCompanyId.value) ?? null,
)

const departmentCount = computed(() => countDeptNodes(filteredDeptTree.value))
const enabledCompanyCount = computed(
  () => companyData.value.filter((company) => company.status === 'ENABLED').length,
)
const enabledPositionCount = computed(
  () => positionData.value.filter((position) => position.status === 'ENABLED').length,
)
const enabledRate = computed(() => {
  const total = companyData.value.length + positionData.value.length
  if (!total) return '0.0'
  return (((enabledCompanyCount.value + enabledPositionCount.value) / total) * 100).toFixed(1)
})

const currentCompanyName = computed(() => selectedCompany.value?.companyName ?? '全部公司')

function countDeptNodes(nodes: OrgDepartmentTreeNodeVO[]): number {
  return nodes.reduce((sum, node) => sum + 1 + countDeptNodes(node.children ?? []), 0)
}

function filterDeptNodes(
  nodes: OrgDepartmentTreeNodeVO[],
  companyId?: string | null,
  keyword = '',
): OrgDepartmentTreeNodeVO[] {
  return nodes
    .map((node) => {
      const children = filterDeptNodes(node.children ?? [], companyId, keyword)
      const matchesCompany = !companyId || node.companyId === companyId || children.length > 0
      const matchesKeyword =
        !keyword ||
        node.deptName.includes(keyword) ||
        node.deptCode.includes(keyword) ||
        children.length > 0

      if (!matchesCompany || !matchesKeyword) return null
      return { ...node, children }
    })
    .filter((node): node is OrgDepartmentTreeNodeVO => node !== null)
}

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
    <div class="org-redesign app-page">
      <div class="org-page-head">
        <div>
          <a-breadcrumb class="org-breadcrumb">
            <a-breadcrumb-item>基础组织</a-breadcrumb-item>
            <a-breadcrumb-item>组织架构</a-breadcrumb-item>
          </a-breadcrumb>
          <div class="org-title-row">
            <div class="org-title-mark"><ApartmentOutlined /></div>
            <div>
              <h1 class="app-page-title">组织架构</h1>
              <p>统一维护公司、部门与岗位，为项目成员、审批流和权限体系提供组织基座。</p>
            </div>
          </div>
        </div>
        <div class="org-head-actions">
          <a-button v-if="canAdd" @click="openDeptAdd">新增部门</a-button>
          <a-button v-if="canAdd" type="primary" @click="openCompanyAdd">
            <template #icon><PlusOutlined /></template>
            新增公司
          </a-button>
        </div>
      </div>

      <div class="org-metric-strip">
        <div class="org-metric">
          <div class="org-metric-icon company"><BankOutlined /></div>
          <div>
            <span>公司</span>
            <strong>{{ companyTotal }}</strong>
          </div>
        </div>
        <div class="org-metric">
          <div class="org-metric-icon dept"><ClusterOutlined /></div>
          <div>
            <span>{{ currentCompanyName }}部门</span>
            <strong>{{ departmentCount }}</strong>
          </div>
        </div>
        <div class="org-metric">
          <div class="org-metric-icon position"><TeamOutlined /></div>
          <div>
            <span>岗位</span>
            <strong>{{ positionTotal }}</strong>
          </div>
        </div>
        <div class="org-metric">
          <div class="org-metric-icon health"><SafetyCertificateOutlined /></div>
          <div>
            <span>启用率</span>
            <strong>{{ enabledRate }}<small>%</small></strong>
          </div>
        </div>
      </div>

      <div class="org-workspace">
        <section class="org-panel org-company-panel">
          <div class="org-panel-header">
            <div>
              <span class="org-panel-title">公司管理</span>
              <p>点击公司行后，右侧部门架构自动聚焦。</p>
            </div>
            <a-button v-if="canAdd" type="primary" size="small" @click="openCompanyAdd">
              <template #icon><PlusOutlined /></template>
              新增
            </a-button>
          </div>

          <div class="org-filter-bar">
            <a-input
              v-model:value="companyFilter.companyCode"
              placeholder="公司编号"
              size="small"
              allow-clear
              @press-enter="handleCompanySearch"
            />
            <a-input
              v-model:value="companyFilter.companyName"
              placeholder="公司名称"
              size="small"
              allow-clear
              @press-enter="handleCompanySearch"
            />
            <a-select
              v-model:value="companyFilter.status"
              placeholder="状态"
              size="small"
              allow-clear
            >
              <a-select-option value="ENABLED">启用</a-select-option>
              <a-select-option value="DISABLED">禁用</a-select-option>
            </a-select>
            <a-button size="small" @click="handleCompanySearch">查询</a-button>
            <a-button size="small" @click="handleCompanyReset">重置</a-button>
          </div>

          <a-table
            class="org-table"
            :columns="companyColumns"
            :data-source="companyData"
            :loading="companyLoading"
            :pagination="false"
            row-key="id"
            size="small"
            :scroll="{ x: 680 }"
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
            <span>共 {{ companyTotal }} 条</span>
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
        </section>

        <section class="org-panel org-dept-panel">
          <div class="org-panel-header">
            <div>
              <span class="org-panel-title">部门架构</span>
              <p>{{ currentCompanyName }} · {{ departmentCount }} 个部门节点</p>
            </div>
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

          <div class="org-dept-focus">
            <div>
              <span>当前范围</span>
              <strong>{{ currentCompanyName }}</strong>
            </div>
            <a-button
              v-if="selectedCompanyId"
              size="small"
              type="link"
              @click="selectedCompanyId = null"
            >
              查看全部
            </a-button>
          </div>

          <div class="org-filter-bar one-line">
            <a-input
              v-model:value="deptKeyword"
              placeholder="搜索部门名称 / 编号"
              size="small"
              allow-clear
            />
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
              <div v-if="!deptTreeLoading && !filteredDeptTree.length" class="org-empty-hint">
                暂无部门节点
              </div>
            </a-spin>
          </div>
        </section>
      </div>

      <section class="org-panel org-position-panel">
        <div class="org-panel-header">
          <div>
            <span class="org-panel-title">岗位管理</span>
            <p>岗位作为项目成员职责和流程节点的稳定枚举。</p>
          </div>
          <a-button v-if="canAdd" type="primary" size="small" @click="openPositionAdd">
            <template #icon><PlusOutlined /></template>
            新增
          </a-button>
        </div>

        <div class="org-filter-bar position">
          <a-input
            v-model:value="positionFilter.positionCode"
            placeholder="岗位编号"
            size="small"
            allow-clear
            @press-enter="handlePositionSearch"
          />
          <a-input
            v-model:value="positionFilter.positionName"
            placeholder="岗位名称"
            size="small"
            allow-clear
            @press-enter="handlePositionSearch"
          />
          <a-select
            v-model:value="positionFilter.status"
            placeholder="状态"
            size="small"
            allow-clear
          >
            <a-select-option value="ENABLED">启用</a-select-option>
            <a-select-option value="DISABLED">禁用</a-select-option>
          </a-select>
          <a-button size="small" @click="handlePositionSearch">查询</a-button>
          <a-button size="small" @click="handlePositionReset">重置</a-button>
        </div>

        <a-table
          class="org-table"
          :columns="positionColumns"
          :data-source="positionData"
          :loading="positionLoading"
          :pagination="false"
          row-key="id"
          size="small"
          :scroll="{ x: 680 }"
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
          <span>共 {{ positionTotal }} 条</span>
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
      </section>

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
.org-redesign {
  min-height: 100%;
  padding: 2px 0 18px;
  color: var(--text);
}

.org-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
  padding: 18px 20px;
  background:
    linear-gradient(135deg, rgba(22, 104, 220, 0.08), rgba(20, 184, 166, 0.08)), var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow-soft);
}

.org-breadcrumb {
  margin-bottom: 10px;
  color: var(--muted);
  font-size: 13px;
}

.org-title-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.org-title-mark,
.org-metric-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.org-title-mark {
  width: 42px;
  height: 42px;
  color: #fff;
  font-size: 19px;
  background: #1668dc;
  border-radius: 8px;
  box-shadow: 0 10px 22px rgba(22, 104, 220, 0.2);
}

.org-title-row p,
.org-panel-header p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.45;
}

.org-head-actions,
.org-panel-actions,
.org-filter-bar,
.org-panel-footer {
  display: flex;
  align-items: center;
}

.org-head-actions,
.org-panel-actions {
  gap: 8px;
  justify-content: flex-end;
}

.org-metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.org-metric {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 82px;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow-soft);
}

.org-metric-icon {
  width: 34px;
  height: 34px;
  color: #fff;
  font-size: 15px;
  border-radius: 8px;
}

.org-metric-icon.company {
  background: #1668dc;
}

.org-metric-icon.dept {
  background: #0ea5e9;
}

.org-metric-icon.position {
  background: #14b8a6;
}

.org-metric-icon.health {
  background: #16a34a;
}

.org-metric span {
  display: block;
  max-width: 180px;
  overflow: hidden;
  color: var(--muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-metric strong {
  display: block;
  margin-top: 4px;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.org-metric small {
  margin-left: 2px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.org-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(320px, 0.95fr);
  gap: 14px;
  margin-bottom: 14px;
  align-items: stretch;
}

.org-panel {
  min-width: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow-soft);
}

.org-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 62px;
  padding: 13px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.org-panel-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0;
}

.org-filter-bar {
  gap: 8px;
  padding: 10px 12px;
  background: #fbfdff;
  border-bottom: 1px solid var(--border-subtle);
}

.org-filter-bar :deep(.ant-input-affix-wrapper),
.org-filter-bar :deep(.ant-select) {
  width: 130px;
}

.org-filter-bar.one-line :deep(.ant-input-affix-wrapper) {
  width: 100%;
}

.org-filter-bar.position :deep(.ant-input-affix-wrapper),
.org-filter-bar.position :deep(.ant-select) {
  width: 150px;
}

.org-table :deep(.ant-table) {
  color: var(--text);
  font-size: 13px;
}

.org-table :deep(.ant-table-thead > tr > th) {
  color: var(--text-secondary);
  background: #f8fafc;
  border-bottom-color: var(--border-subtle);
  font-size: 12px;
  font-weight: 800;
}

.org-table :deep(.ant-table-tbody > tr > td) {
  padding-top: 9px;
  padding-bottom: 9px;
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
  gap: 12px;
  min-height: 48px;
  padding: 10px 12px;
  color: var(--text-secondary);
  border-top: 1px solid var(--border-subtle);
  font-size: 13px;
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
  padding: 12px 14px;
  background: #f8fbff;
  border-bottom: 1px solid var(--border-subtle);
}

.org-dept-focus span {
  display: block;
  color: var(--muted);
  font-size: 12px;
}

.org-dept-focus strong {
  display: block;
  margin-top: 3px;
  color: var(--text);
  font-size: 15px;
}

.org-tree-wrap {
  min-height: 286px;
  padding: 12px 10px 14px;
  flex: 1;
}

.org-tree-wrap :deep(.ant-tree) {
  background: transparent;
  font-size: 13px;
}

.org-tree-wrap :deep(.ant-tree-node-content-wrapper) {
  min-height: 30px;
  padding: 3px 8px;
  border-radius: 6px;
}

.org-tree-wrap :deep(.ant-tree-node-content-wrapper:hover),
.org-tree-wrap :deep(.ant-tree-node-selected) {
  background: #eef6ff !important;
}

.org-empty-hint {
  padding: 54px 12px;
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
}

.org-position-panel {
  margin-bottom: 4px;
}

@media (max-width: 1180px) {
  .org-workspace,
  .org-metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .org-dept-panel,
  .org-position-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .org-page-head,
  .org-panel-header,
  .org-panel-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .org-head-actions,
  .org-panel-actions,
  .org-filter-bar {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .org-filter-bar :deep(.ant-input-affix-wrapper),
  .org-filter-bar :deep(.ant-select),
  .org-filter-bar.position :deep(.ant-input-affix-wrapper),
  .org-filter-bar.position :deep(.ant-select) {
    width: calc(50% - 4px);
  }

  .org-metric-strip,
  .org-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .org-title-row {
    align-items: flex-start;
  }

  .org-title-mark {
    width: 36px;
    height: 36px;
  }

  .org-filter-bar :deep(.ant-input-affix-wrapper),
  .org-filter-bar :deep(.ant-select),
  .org-filter-bar.position :deep(.ant-input-affix-wrapper),
  .org-filter-bar.position :deep(.ant-select) {
    width: 100%;
  }
}
</style>
