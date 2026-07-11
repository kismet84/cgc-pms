<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getAuditLogs, type AuditLogVO } from '@/api/modules/audit'

const loading = ref(false)
const loaded = ref(false)
const listError = ref('')
const records = ref<AuditLogVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const filter = reactive({ businessType: '', businessId: '' })

const columns = [
  { title: '时间', dataIndex: 'createdAt', width: 170 },
  { title: '操作', dataIndex: 'operationType', width: 100 },
  { title: '业务类型', dataIndex: 'businessType', width: 140 },
  { title: '业务 ID', dataIndex: 'businessId', width: 160 },
  { title: '请求', dataIndex: 'requestPath', ellipsis: true },
  { title: '结果', dataIndex: 'successFlag', width: 90 },
  { title: '耗时(ms)', dataIndex: 'durationMs', width: 100 },
]

async function fetchData() {
  loading.value = true
  listError.value = ''
  try {
    const result = await getAuditLogs({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      businessType: filter.businessType || undefined,
      businessId: filter.businessId || undefined,
    })
    records.value = result.records ?? []
    total.value = Number(result.total ?? 0)
  } catch (error) {
    console.error(error)
    records.value = []
    total.value = 0
    listError.value = '审计日志加载失败，请稍后重试。'
    message.error('加载审计日志失败')
  } finally {
    loaded.value = true
    loading.value = false
  }
}

function search() {
  pageNo.value = 1
  fetchData()
}

function reset() {
  filter.businessType = ''
  filter.businessId = ''
  pageNo.value = 1
  fetchData()
}

function changePage(page: number, size: number) {
  pageNo.value = size === pageSize.value ? page : 1
  pageSize.value = size
  fetchData()
}

onMounted(fetchData)
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <a-breadcrumb class="lg-page-head-breadcrumb">
        <a-breadcrumb-item>系统设置</a-breadcrumb-item>
        <a-breadcrumb-item>操作审计</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <main class="lg-list-table-panel">
      <div class="lg-toolbar">
        <div class="lg-toolbar-left">
          <a-input v-model:value="filter.businessType" placeholder="业务类型" allow-clear />
          <a-input v-model:value="filter.businessId" placeholder="业务 ID" allow-clear />
          <a-button type="primary" @click="search">查询</a-button>
          <a-button @click="reset">重置</a-button>
        </div>
      </div>

      <a-alert v-if="listError" :message="listError" type="error" show-icon>
        <template #action><a-button size="small" @click="fetchData">重新加载</a-button></template>
      </a-alert>
      <a-table
        v-else-if="loading || records.length"
        row-key="id"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <a-tag v-if="column.dataIndex === 'successFlag'" :color="record.successFlag === 1 ? 'success' : 'error'">
            {{ record.successFlag === 1 ? '成功' : '失败' }}
          </a-tag>
        </template>
      </a-table>
      <a-empty v-else-if="loaded" description="暂无审计日志" />

      <a-pagination
        v-if="!listError && total > 0"
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        show-size-changer
        @change="changePage"
      />
    </main>
  </div>
</template>
