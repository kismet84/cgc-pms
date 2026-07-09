<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ApiOutlined, ExportOutlined, LinkOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { getReportCatalog, type ReportCatalogItem } from '@/api/modules/report'
import { useUserStore } from '@/stores/user'
import { canOpenReportCatalogPage, hasReportCatalogExportEntry } from './catalog-entry'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const items = ref<ReportCatalogItem[]>([])
const loadError = ref('')

const catalogLabelMap: Record<string, string> = {
  dashboard: '驾驶舱',
  cost: '成本',
  alert: '预警',
  workflow: '审批',
  contract: '合同',
}

const isAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)

const visibleItems = computed(() =>
  items.value.filter(
    (item) =>
      !item.permissionCode ||
      isAdmin.value ||
      userStore.hasPermission(item.permissionCode),
  ),
)

const summary = computed(() => ({
  total: visibleItems.value.length,
  pageCount: visibleItems.value.filter((item) => item.sourceType === 'page').length,
  apiCount: visibleItems.value.filter((item) => item.sourceType === 'api').length,
}))

const columns = [
  {
    title: '报表',
    dataIndex: 'name',
    key: 'name',
    minWidth: 240,
  },
  {
    title: '目录',
    dataIndex: 'catalog',
    key: 'catalog',
    width: 110,
  },
  {
    title: '类型',
    dataIndex: 'sourceType',
    key: 'sourceType',
    width: 112,
  },
  {
    title: '筛选说明',
    dataIndex: 'filterSummary',
    key: 'filterSummary',
    minWidth: 320,
  },
  {
    title: '导出',
    dataIndex: 'exportSupport',
    key: 'exportSupport',
    width: 92,
  },
  {
    title: '权限码',
    dataIndex: 'permissionCode',
    key: 'permissionCode',
    minWidth: 180,
  },
  {
    title: '目标',
    dataIndex: 'target',
    key: 'target',
    minWidth: 220,
  },
]

function getCatalogLabel(value: string) {
  return catalogLabelMap[value] ?? value
}

function canOpenPage(item: ReportCatalogItem) {
  return canOpenReportCatalogPage(item, router.resolve)
}

function hasExportEntry(item: ReportCatalogItem) {
  return hasReportCatalogExportEntry(item, router.resolve)
}

function openTarget(item: ReportCatalogItem) {
  if (!canOpenPage(item)) {
    return
  }
  router.push(item.target)
}

async function fetchCatalog() {
  loading.value = true
  loadError.value = ''
  try {
    items.value = await getReportCatalog()
  } catch (error) {
    console.error(error)
    loadError.value = '加载报表目录失败，请稍后重试。'
    message.error('加载报表目录失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchCatalog)
</script>

<template>
  <div class="lg-page app-page report-catalog-page">
    <div class="lg-page-head">
      <div class="report-catalog-head">
        <div>
          <a-breadcrumb class="lg-page-head-breadcrumb">
            <a-breadcrumb-item>首页</a-breadcrumb-item>
            <a-breadcrumb-item>报表目录</a-breadcrumb-item>
          </a-breadcrumb>
          <div class="report-catalog-title-row">
            <h1>报表目录</h1>
            <span>按当前账号可见范围展示页面型与 API-only 报表入口。</span>
          </div>
        </div>
        <a-button type="text" :loading="loading" @click="fetchCatalog">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </div>

    <section class="report-catalog-kpis">
      <article class="report-catalog-kpi">
        <span>可见报表</span>
        <strong>{{ summary.total }}</strong>
      </article>
      <article class="report-catalog-kpi">
        <span>页面型</span>
        <strong>{{ summary.pageCount }}</strong>
      </article>
      <article class="report-catalog-kpi">
        <span>API-only</span>
        <strong>{{ summary.apiCount }}</strong>
      </article>
    </section>

    <section class="report-catalog-table">
      <a-table
        :columns="columns"
        :data-source="visibleItems"
        :loading="loading"
        :pagination="false"
        row-key="code"
        size="middle"
        :scroll="{ x: 1280 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div class="report-name-cell">
              <button
                v-if="canOpenPage(record)"
                type="button"
                class="report-link-button"
                @click="openTarget(record)"
              >
                <LinkOutlined />
                <span>{{ record.name }}</span>
              </button>
              <div v-else class="report-name-text">
                <ApiOutlined />
                <span>{{ record.name }}</span>
              </div>
              <small>{{ record.code }}</small>
            </div>
          </template>

          <template v-else-if="column.key === 'catalog'">
            <a-tag>{{ getCatalogLabel(record.catalog) }}</a-tag>
          </template>

          <template v-else-if="column.key === 'sourceType'">
            <a-tag :color="record.sourceType === 'page' ? 'blue' : 'gold'">
              {{ record.sourceType === 'page' ? '页面' : 'API-only' }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'exportSupport'">
            <span
              class="report-export-flag"
              :class="{ supported: hasExportEntry(record), muted: !hasExportEntry(record) }"
            >
              <ExportOutlined />
              {{ hasExportEntry(record) ? '支持导出' : '无导出入口' }}
            </span>
          </template>

          <template v-else-if="column.key === 'permissionCode'">
            <code>{{ record.permissionCode || '-' }}</code>
          </template>

          <template v-else-if="column.key === 'target'">
            <button
              v-if="canOpenPage(record)"
              type="button"
              class="report-target-button"
              @click="openTarget(record)"
            >
              {{ record.target }}
            </button>
            <span v-else class="report-target-text">{{ record.target }}</span>
          </template>
        </template>

        <template #emptyText>
          <a-empty :description="loadError || '当前暂无可见报表'" />
        </template>
      </a-table>
    </section>
  </div>
</template>

<style scoped>
.report-catalog-page {
  gap: 16px;
  min-height: 100%;
  background: #f5f7fb;
}

.report-catalog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.report-catalog-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.report-catalog-title-row h1 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  font-weight: 800;
  line-height: 34px;
}

.report-catalog-title-row span {
  color: var(--text-secondary);
  font-size: 13px;
}

.report-catalog-kpis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.report-catalog-kpi {
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #e4eaf3;
  border-radius: 8px;
  display: grid;
  gap: 8px;
}

.report-catalog-kpi span {
  color: var(--text-secondary);
  font-size: 13px;
}

.report-catalog-kpi strong {
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 1;
}

.report-catalog-table {
  background: #fff;
  border: 1px solid #e4eaf3;
  border-radius: 8px;
  overflow: hidden;
}

.report-name-cell {
  display: grid;
  gap: 4px;
}

.report-link-button,
.report-target-button {
  padding: 0;
  color: #1677ff;
  background: transparent;
  border: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-align: left;
  cursor: pointer;
}

.report-link-button:hover,
.report-target-button:hover {
  color: #4096ff;
}

.report-name-text {
  color: var(--text);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.report-name-cell small {
  color: var(--text-secondary);
  font-size: 12px;
}

.report-export-flag {
  color: var(--text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.report-export-flag.muted {
  opacity: 0.72;
}

.report-export-flag.supported {
  color: #1677ff;
}

.report-target-text {
  color: var(--text-secondary);
  word-break: break-all;
}

@media (max-width: 900px) {
  .report-catalog-kpis {
    grid-template-columns: 1fr;
  }

  .report-catalog-head {
    flex-direction: column;
  }
}
</style>
