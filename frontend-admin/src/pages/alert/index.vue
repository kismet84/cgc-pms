<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useReferenceStore } from '@/stores/reference'
import { useAlertStore } from '@/stores/alert'
import { RULE_TYPE_LABELS, SEVERITY_COLOR, type AlertLogVO } from '@/types/alert'

const store = useAlertStore()

// ── Filters ──
const filter = reactive({
  projectId: undefined as number | undefined,
  severity: undefined as string | undefined,
  isRead: undefined as number | undefined,
})

// ── Project dropdown ──
const referenceStore = useReferenceStore()
const projectOptions = computed(() => referenceStore.projects ?? [])
const projectsLoading = ref(false)

// ── Fetch ──
const pageNo = ref(1)
const pageSize = ref(20)

const pagedAlerts = computed(() => {
  const start = (pageNo.value - 1) * pageSize.value
  return store.alerts.slice(start, start + pageSize.value)
})

const total = computed(() => store.alerts.length)

// ── Fetch ──
async function fetchData() {
  try {
    await store.fetchAlerts({
      projectId: filter.projectId,
      severity: filter.severity,
      isRead: filter.isRead,
    })
  } catch {
    message.error('加载预警列表失败，请稍后重试')
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.projectId = undefined
  filter.severity = undefined
  filter.isRead = undefined
  pageNo.value = 1
  fetchData()
}

function handlePageChange(page: number) {
  pageNo.value = page
}

function handlePageSizeChange(_cur: number, size: number) {
  pageSize.value = size
  pageNo.value = 1
}

// ── Actions ──
async function handleMarkRead(record: AlertLogVO) {
  try {
    await store.markRead(record.id)
    message.success('已标记为已读')
  } catch {
    message.error('操作失败')
  }
}

async function handleBatchEvaluate() {
  try {
    const result = await store.triggerBatchEvaluate()
    message.success(`评估完成，生成 ${result.alertsGenerated} 条预警`)
    await fetchData()
  } catch {
    message.error('触发评估失败')
  }
}

// ── Columns ──
const columns = [
  { title: '预警内容', dataIndex: 'message', key: 'message', ellipsis: true, width: 320 },
  {
    title: '项目',
    dataIndex: 'projectId',
    key: 'projectId',
    width: 140,
  },
  {
    title: '严重度',
    dataIndex: 'severity',
    key: 'severity',
    width: 90,
  },
  {
    title: '规则类型',
    dataIndex: 'ruleType',
    key: 'ruleType',
    width: 130,
  },
  { title: '触发时间', dataIndex: 'triggeredAt', key: 'triggeredAt', width: 170 },
  {
    title: '状态',
    dataIndex: 'isRead',
    key: 'isRead',
    width: 80,
  },
  { title: '操作', key: 'action', width: 100, fixed: 'right' },
]

function getProjectName(projectId: number): string {
  const p = projectOptions.value.find((o) => String(o.id) === String(projectId))
  return p ? `${p.projectCode} ${p.projectName}` : `项目#${projectId}`
}

// ── Init ──
onMounted(async () => {
  projectsLoading.value = true
  try {
    await referenceStore.fetchProjects()
  } finally {
    projectsLoading.value = false
  }
  fetchData()
})
</script>

<template>
  <div class="al-page">
    <a-page-header title="预警中心" class="al-header">
      <template #extra>
        <a-button type="primary" danger :loading="store.evaluating" @click="handleBatchEvaluate">
          触发评估
        </a-button>
      </template>
    </a-page-header>

    <!-- Filter -->
    <div class="al-card al-filter">
      <div class="al-filter-row">
        <div class="al-field">
          <label>项目：</label>
          <a-select
            v-model:value="filter.projectId"
            placeholder="全部项目"
            allow-clear
            style="width: 220px"
            :loading="projectsLoading"
            show-search
            :filter-option="
              (input: string, option: any) =>
                (option.label ?? '').toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option
              v-for="p in projectOptions"
              :key="p.id"
              :value="Number(p.id)"
              :label="`${p.projectCode} ${p.projectName}`"
            >
              {{ p.projectCode }} {{ p.projectName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="al-field">
          <label>严重度：</label>
          <a-select
            v-model:value="filter.severity"
            placeholder="全部"
            allow-clear
            style="width: 120px"
          >
            <a-select-option value="HIGH">高</a-select-option>
            <a-select-option value="MEDIUM">中</a-select-option>
            <a-select-option value="LOW">低</a-select-option>
          </a-select>
        </div>
        <div class="al-field">
          <label>读取状态：</label>
          <a-select
            v-model:value="filter.isRead"
            placeholder="全部"
            allow-clear
            style="width: 120px"
          >
            <a-select-option :value="0">未读</a-select-option>
            <a-select-option :value="1">已读</a-select-option>
          </a-select>
        </div>
        <div class="al-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="al-card al-table-wrap">
      <a-table
        :data-source="pagedAlerts"
        :columns="columns"
        :loading="store.loading"
        :pagination="false"
        row-key="id"
        size="middle"
        :scroll="{ x: 1050 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'projectId'">
            <span class="al-muted">{{ getProjectName(record.projectId) }}</span>
          </template>
          <template v-else-if="column.key === 'severity'">
            <a-tag :color="SEVERITY_COLOR[record.severity] ?? 'default'">
              {{ record.severity === 'HIGH' ? '高' : record.severity === 'MEDIUM' ? '中' : '低' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'ruleType'">
            <a-tag>{{ RULE_TYPE_LABELS[record.ruleType] || record.ruleType }}</a-tag>
          </template>
          <template v-else-if="column.key === 'isRead'">
            <a-badge v-if="record.isRead === 0" status="processing" text="未读" />
            <span v-else class="al-muted">已读</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-if="record.isRead === 0"
              type="link"
              size="small"
              :loading="store.markingRead.has(record.id)"
              @click="handleMarkRead(record)"
            >
              标为已读
            </a-button>
            <span v-else class="al-muted">—</span>
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="al-pagination">
      <span class="al-total">共 {{ total }} 条</span>
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
</template>

<style scoped>
.al-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.al-header {
  background: transparent;
  padding-bottom: 8px;
}
.al-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.al-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.al-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
}
.al-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.al-field label {
  color: #374151;
  min-width: 56px;
}
.al-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}
.al-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
}
.al-muted {
  color: #9ca3af;
  font-size: 13px;
}
.al-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.al-total {
  font-size: 13px;
  color: #4b5563;
}
</style>
