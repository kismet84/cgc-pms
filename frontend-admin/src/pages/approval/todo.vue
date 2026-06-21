<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  getMyTodos,
  getMyDone,
  getMyCc,
  type WfTaskVO,
  type WfRecordVO,
  type WfCcVO,
} from '@/api/modules/workflow'
import type { PageResult } from '@/types/api'

const router = useRouter()
const route = useRoute()

const activeTab = ref(String(route.meta.approvalTab ?? 'todo'))

const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const todoData = ref<WfTaskVO[]>([])
const doneData = ref<WfRecordVO[]>([])
const ccData = ref<WfCcVO[]>([])

const businessTypeMap: Record<string, string> = {
  CONTRACT_APPROVAL: '合同审批',
  PAY_REQUEST: '付款申请',
  VAR_ORDER: '签证变更',
  MAT_RECEIPT: '材料验收',
}

async function fetchData() {
  loading.value = true
  try {
    const params = { pageNum: pageNo.value, pageSize: pageSize.value }

    if (activeTab.value === 'todo') {
      const res: PageResult<WfTaskVO> = await getMyTodos(params)
      todoData.value = res.records
      total.value = res.total
    } else if (activeTab.value === 'done') {
      const res: PageResult<WfRecordVO> = await getMyDone(params)
      doneData.value = res.records
      total.value = res.total
    } else {
      const res: PageResult<WfCcVO> = await getMyCc(params)
      ccData.value = res.records
      total.value = res.total
    }
  } catch (e: unknown) {
    console.error(e)
    if (activeTab.value === 'todo') todoData.value = []
    else if (activeTab.value === 'done') doneData.value = []
    else ccData.value = []
    total.value = 0
    message.error('加载列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleTabChange(key: string) {
  pageNo.value = 1
  router.push(`/approval/${key}`)
  fetchData()
}

function handlePageChange(pno: number, psize: number) {
  pageNo.value = pno
  pageSize.value = psize
  fetchData()
}

function handleDetail(record: { instanceId: string }) {
  router.push(`/approval/${record.instanceId}`)
}

const gridColumns = computed(() => [
  { field: 'title', title: '审批标题', ellipsis: true, slots: { default: 'title' } },
  { field: 'businessType', title: '业务类型', width: 120, slots: { default: 'businessType' } },
  { field: 'timeCol', title: '时间', width: 160, slots: { default: 'timeCol' } },
  { field: 'instanceStatus', title: '状态', width: 100, slots: { default: 'instanceStatus' } },
  { title: '操作', width: 120, slots: { default: 'action' } },
])

const tabs = [
  { key: 'todo', label: '我的待办' },
  { key: 'done', label: '我的已办' },
  { key: 'cc', label: '抄送我的' },
]

const tableData = computed<Record<string, unknown>[]>(() => {
  if (activeTab.value === 'todo') return todoData.value as unknown as Record<string, unknown>[]
  if (activeTab.value === 'done') return doneData.value as unknown as Record<string, unknown>[]
  return ccData.value as unknown as Record<string, unknown>[]
})

function getTimeCol(record: Record<string, unknown>): string {
  if (activeTab.value === 'todo') return (record.receivedAt as string) ?? ''
  if (activeTab.value === 'done') return (record.createdAt as string) ?? ''
  return (record.createdTime as string) ?? ''
}

function getActionLabel(): string {
  return activeTab.value === 'todo' ? '处理' : '查看'
}

function pageHeaderTitle(): string {
  const t = tabs.find((t) => t.key === activeTab.value)
  return t?.label ?? '我的待办'
}

function pageHeaderSubtitle(): string {
  if (activeTab.value === 'todo') return '处理需要您审批的业务单据'
  if (activeTab.value === 'done') return '查看您已处理的审批记录'
  return '查看抄送给您的业务单据'
}

onMounted(() => {
  fetchData()
})

watch(
  () => route.meta.approvalTab,
  (tab) => {
    const nextTab = String(tab ?? 'todo')
    if (nextTab === activeTab.value) return
    activeTab.value = nextTab
    pageNo.value = 1
    fetchData()
  },
)
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <a-breadcrumb style="margin-bottom:5px;font-size:13px">
        <a-breadcrumb-item>审批中心</a-breadcrumb-item>
        <a-breadcrumb-item>{{ pageHeaderTitle() }}</a-breadcrumb-item>
      </a-breadcrumb>
      <p class="app-page-subtitle" style="margin:0;color:var(--subtext);font-size:13px">{{ pageHeaderSubtitle() }}</p>
    </div>

    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane v-for="tab in tabs" :key="tab.key" :tab="tab.label" />
    </a-tabs>

    <div class="lg-table-wrap">
      <vxe-grid
        :data="tableData"
        :columns="gridColumns"
        :loading="loading"
        :column-config="{ resizable: true }"
        stripe
        border="inner"
        size="small"
        max-height="480"
      >
        <template #title="{ row }">
          <a class="lg-link" @click="handleDetail(row as { instanceId: string })">{{ row.title }}</a>
        </template>
        <template #businessType="{ row }">
          <a-tag>{{
            businessTypeMap[row.businessType as string] ||
            (row.businessType as string) ||
            '—'
          }}</a-tag>
        </template>
        <template #timeCol="{ row }">
          {{ getTimeCol(row) }}
        </template>
        <template #instanceStatus="{ row }">
          <a-tag v-if="row.instanceStatus === 'RUNNING'" color="processing">审批中</a-tag>
          <a-tag v-else-if="row.instanceStatus === 'APPROVED'" color="success">已通过</a-tag>
          <a-tag v-else-if="row.instanceStatus === 'REJECTED'" color="error">已驳回</a-tag>
          <a-tag v-else>{{ row.instanceStatus }}</a-tag>
        </template>
        <template #action="{ row }">
          <div class="lg-ops">
            <a class="lg-link" @click="handleDetail(row as { instanceId: string })">
              {{ getActionLabel() }}
            </a>
          </div>
        </template>
      </vxe-grid>
    </div>

    <div class="lg-pagination">
      <span class="lg-total">共 {{ total }} 条</span>
      <a-pagination
        v-model:current="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-size-options="['10', '20', '50', '100']"
        show-size-changer
        show-quick-jumper
        @change="(p: number, ps: number) => handlePageChange(p, ps)"
      />
    </div>
  </div>
</template>

<style scoped></style>
