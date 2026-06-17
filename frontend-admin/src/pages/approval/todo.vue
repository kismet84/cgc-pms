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

const columns = [
  { title: '审批标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '业务类型', dataIndex: 'businessType', key: 'businessType', width: 120 },
  { title: '时间', dataIndex: 'timeCol', key: 'timeCol', width: 160 },
  { title: '状态', dataIndex: 'instanceStatus', key: 'instanceStatus', width: 100 },
  { title: '操作', key: 'action', width: 120 },
]

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
  <div class="project-target-redesign app-page">
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb">
        <a-breadcrumb-item>审批中心</a-breadcrumb-item>
        <a-breadcrumb-item>{{ pageHeaderTitle() }}</a-breadcrumb-item>
      </a-breadcrumb>
      <h1 class="app-page-title">{{ pageHeaderTitle() }}</h1>
      <p class="app-page-subtitle">{{ pageHeaderSubtitle() }}</p>
      <div class="pt-head-actions"></div>
    </div>

    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane v-for="tab in tabs" :key="tab.key" :tab="tab.label" />
    </a-tabs>

    <div class="pt-panel">
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
        }"
        row-key="id"
        @change="({ current, pageSize: ps }: any) => handlePageChange(current, ps)"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <a @click="handleDetail(record as { instanceId: string })">{{ record.title }}</a>
          </template>
          <template v-else-if="column.key === 'businessType'">
            <a-tag>{{
              businessTypeMap[record.businessType as string] ||
              (record.businessType as string) ||
              '—'
            }}</a-tag>
          </template>
          <template v-else-if="column.key === 'timeCol'">
            {{ getTimeCol(record) }}
          </template>
          <template v-else-if="column.key === 'instanceStatus'">
            <a-tag v-if="record.instanceStatus === 'RUNNING'" color="processing">审批中</a-tag>
            <a-tag v-else-if="record.instanceStatus === 'APPROVED'" color="success">已通过</a-tag>
            <a-tag v-else-if="record.instanceStatus === 'REJECTED'" color="error">已驳回</a-tag>
            <a-tag v-else>{{ record.instanceStatus }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              type="link"
              size="small"
              @click="handleDetail(record as { instanceId: string })"
            >
              {{ getActionLabel() }}
            </a-button>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<style scoped></style>

