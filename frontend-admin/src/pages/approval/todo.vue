<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getMyTodos, type WfTaskVO } from '@/api/modules/workflow'
import type { PageResult } from '@/types/api'

const router = useRouter()

const loading = ref(false)
const tableData = ref<WfTaskVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)


const businessTypeMap: Record<string, string> = {
  CONTRACT_APPROVAL: '合同审批',
  PAY_REQUEST: '付款申请',
  VAR_ORDER: '签证变更',
  MAT_RECEIPT: '材料验收',
}

async function fetchData() {
  loading.value = true
  try {
    const res: PageResult<WfTaskVO> = await getMyTodos({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
    })
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
    message.error('加载待办列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  pageNo.value = 1
  fetchData()
}

function handlePageChange(pno: number, psize: number) {
  pageNo.value = pno
  pageSize.value = psize
  fetchData()
}

function handleDetail(task: WfTaskVO) {
  router.push(`/approval/${task.instanceId}`)
}

const columns = [
  { title: '审批标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '业务类型', dataIndex: 'businessType', key: 'businessType', width: 120 },
  { title: '接收时间', dataIndex: 'receivedAt', key: 'receivedAt', width: 160 },
  { title: '当前轮次', dataIndex: 'roundNo', key: 'roundNo', width: 80 },
  { title: '状态', dataIndex: 'instanceStatus', key: 'instanceStatus', width: 100 },
  { title: '操作', key: 'action', width: 120 },
]

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="wf-todo-page">
    <a-page-header title="我的待办" sub-title="处理需要您审批的业务单据" />

    <div class="wf-card">
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
            <a @click="handleDetail(record)">{{ record.title }}</a>
          </template>
          <template v-else-if="column.key === 'businessType'">
            <a-tag>{{ businessTypeMap[record.businessType] || record.businessType }}</a-tag>
          </template>
          <template v-else-if="column.key === 'instanceStatus'">
            <a-tag v-if="record.instanceStatus === 'RUNNING'" color="processing">审批中</a-tag>
            <a-tag v-else-if="record.instanceStatus === 'APPROVED'" color="success">已通过</a-tag>
            <a-tag v-else-if="record.instanceStatus === 'REJECTED'" color="error">已驳回</a-tag>
            <a-tag v-else>{{ record.instanceStatus }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="handleDetail(record)">处理</a-button>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<style scoped>
.wf-todo-page {
  padding: 0;
}
.wf-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px;
}
</style>
