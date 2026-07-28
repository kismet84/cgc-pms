<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { V2Badge, V2Button, V2Card, V2Input, V2PageState, V2Stack, showToast } from '@/components'
import { loadAuditLogs, type AuditRecord } from '@/services/system-management'
import { isApiClientError } from '@/services/request'

const loading = ref(false)
const error = ref('')
const records = ref<AuditRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const filter = reactive({ businessType: '', businessId: '', userId: '' })
let controller: AbortController | null = null

async function refresh(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    const page = await loadAuditLogs(
      {
        pageNo: pageNo.value,
        pageSize,
        businessType: filter.businessType.trim() || undefined,
        businessId: filter.businessId.trim() || undefined,
        userId: filter.userId.trim() || undefined,
      },
      current.signal,
    )
    records.value = page.records
    total.value = page.total
  } catch (value) {
    if (!current.signal.aborted) {
      records.value = []
      total.value = 0
      error.value = messageOf(value)
    }
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshPage(): Promise<void> {
  await refresh()
  if (!error.value) showToast('success', '审计日志已刷新')
}

function search(): void {
  pageNo.value = 1
  void refresh()
}

function reset(): void {
  Object.assign(filter, { businessType: '', businessId: '', userId: '' })
  search()
}

function changePage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= total.value) return
  pageNo.value = next
  void refresh()
}

function messageOf(value: unknown): string {
  return isApiClientError(value) || value instanceof Error ? value.message : '审计日志读取失败'
}

onMounted(() => void refresh())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="audit-page" :gap="4">
    <V2Card title="操作审计" :heading-level="1">
      <template #actions>
        <form class="v2-page-heading__filters" @submit.prevent="search">
          <V2Input
            v-model="filter.businessType"
            label="业务类型"
            hide-label
            placeholder="业务类型，例如 PAYMENT"
          />
          <V2Input v-model="filter.businessId" label="业务标识" hide-label placeholder="业务标识" />
          <V2Input v-model="filter.userId" label="用户标识" hide-label placeholder="用户标识" />
          <V2Button type="submit" size="small">查询</V2Button>
          <V2Button type="button" size="small" variant="secondary" @click="reset"> 重置 </V2Button>
        </form>
        <V2Badge tone="neutral">只读</V2Badge>
        <V2Button size="small" variant="secondary" @click="refreshPage">刷新</V2Button>
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取审计日志" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="审计日志加载失败" :description="error">
      <template #actions><V2Button @click="refresh">重试</V2Button></template>
    </V2PageState>
    <V2Card v-else title="审计记录">
      <V2PageState
        v-if="!records.length"
        kind="empty"
        title="暂无审计记录"
        description="当前筛选条件没有记录。"
      />
      <div v-else class="audit-page__table-wrap">
        <table data-table-identity="contextual">
          <thead>
            <tr>
              <th>时间</th>
              <th>操作</th>
              <th>业务</th>
              <th>请求</th>
              <th>结果</th>
              <th>耗时</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id">
              <td>{{ record.createdAt ?? '—' }}</td>
              <th scope="row">{{ record.operationType ?? '—' }}</th>
              <td>{{ record.businessType ?? '—' }} / {{ record.businessId ?? '—' }}</td>
              <td>{{ record.httpMethod ?? '—' }} {{ record.requestPath ?? '—' }}</td>
              <td>
                <V2Badge :tone="record.successFlag === 1 ? 'success' : 'danger'">
                  {{ record.successFlag === 1 ? '成功' : '失败' }}
                </V2Badge>
              </td>
              <td>{{ record.durationMs ?? 0 }} ms</td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <nav class="audit-page__pagination v2-pagination" aria-label="操作审计分页">
          <span>共 {{ total }} 条</span>
          <V2Button
            size="small"
            variant="secondary"
            :disabled="pageNo === 1"
            @click="changePage(pageNo - 1)"
          >
            上一页
          </V2Button>
          <span>第 {{ pageNo }} 页</span>
          <V2Button
            size="small"
            variant="secondary"
            :disabled="pageNo * pageSize >= total"
            @click="changePage(pageNo + 1)"
          >
            下一页
          </V2Button>
        </nav>
      </template>
    </V2Card>
  </V2Stack>
</template>

<style scoped>
.audit-page__pagination {
  display: flex;
  gap: var(--v2-space-2);
  align-items: center;
}

.audit-page__table-wrap {
  overflow-x: auto;
}

.audit-page__pagination {
  justify-content: flex-end;
}
</style>
