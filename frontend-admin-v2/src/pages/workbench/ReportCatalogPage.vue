<script setup lang="ts">
import { canOpenReportTarget, type ReportCatalogItem } from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { V2Alert, V2Badge, V2Button, V2Card, V2PageState } from '@/components'
import { navigationDomains } from '@/navigation/catalog'
import { isApiClientError } from '@/services/request'
import { loadReportCatalog } from '@/services/reports'

const router = useRouter()
const items = ref<ReportCatalogItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
let controller: AbortController | null = null
const knownPageTargets = navigationDomains.flatMap((domain) =>
  domain.workspaces.flatMap((workspace) => workspace.tabs.map((tab) => tab.path)),
)
const groups = computed(() => {
  const result: Array<{ key: string; items: ReportCatalogItem[] }> = []
  for (const item of items.value) {
    const group = result.find((candidate) => candidate.key === item.catalog)
    if (group) group.items.push(item)
    else result.push({ key: item.catalog, items: [item] })
  }
  return result
})
const catalogLabels: Record<string, string> = {
  dashboard: '驾驶舱',
  cost: '成本',
  alert: '预警',
  workflow: '审批',
  contract: '合同',
}

function canOpen(item: ReportCatalogItem) {
  return canOpenReportTarget(item, knownPageTargets)
}

function open(item: ReportCatalogItem) {
  if (canOpen(item)) void router.push(item.target)
}

async function refresh() {
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  errorMessage.value = ''
  try {
    items.value = await loadReportCatalog(controller.signal)
  } catch (error) {
    if (!controller.signal.aborted) {
      items.value = []
      errorMessage.value = isApiClientError(error) ? error.message : '报表目录加载失败'
    }
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

void refresh()
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="report-catalog-page" aria-labelledby="report-catalog-title">
    <V2Card title="报表目录" subtitle="仅展示服务端按当前账号权限返回的报表。">
      <template #actions
        ><V2Button variant="ghost" size="small" :loading="loading" @click="refresh"
          >刷新</V2Button
        ></template
      >
      <h1 id="report-catalog-title" class="sr-only">报表目录</h1>
      <p class="report-catalog-page__summary">
        可见 {{ items.length }} 项；API-only 仅展示接口能力，不伪装成页面入口。
      </p>
    </V2Card>
    <V2Alert v-if="errorMessage" tone="danger" title="请求未完成">{{ errorMessage }}</V2Alert>
    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载报表目录"
      description="读取服务端权限过滤后的目录。"
    />
    <V2PageState
      v-else-if="!groups.length"
      kind="empty"
      title="当前账号暂无可见报表"
      description="请联系管理员核对报表权限。"
    />
    <section v-else class="report-catalog-page__groups">
      <V2Card v-for="group in groups" :key="group.key" :title="catalogLabels[group.key] || '其他'">
        <div class="report-catalog-page__items">
          <article v-for="item in group.items" :key="item.code" class="report-catalog-page__item">
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.filterSummary }}</p>
              <small>{{ item.code }}</small>
            </div>
            <div class="report-catalog-page__meta">
              <V2Badge :tone="item.sourceType === 'page' ? 'info' : 'neutral'">{{
                item.sourceType === 'page' ? '页面' : 'API-only'
              }}</V2Badge>
              <V2Button v-if="canOpen(item)" size="small" variant="secondary" @click="open(item)"
                >打开</V2Button
              >
              <span v-else class="report-catalog-page__no-entry">无页面入口</span>
            </div>
          </article>
        </div>
      </V2Card>
    </section>
  </section>
</template>

<style scoped>
.report-catalog-page {
  display: grid;
  gap: var(--v2-space-3);
  padding: 0;
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-13);
  line-height: var(--v2-line-height-body);
}
.report-catalog-page__summary {
  margin: 0;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-13);
}
.report-catalog-page__groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.report-catalog-page__items {
  display: grid;
  gap: var(--v2-space-2);
}
.report-catalog-page__item {
  display: flex;
  justify-content: space-between;
  gap: var(--v2-space-4);
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.report-catalog-page__item strong {
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-13);
  font-weight: var(--v2-font-weight-semibold);
}
.report-catalog-page__item p,
.report-catalog-page__item small {
  margin: var(--v2-space-1) 0 0;
}
.report-catalog-page__item p {
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-13);
}
.report-catalog-page__item small {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.report-catalog-page__meta {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
  flex: 0 0 auto;
}
.report-catalog-page__no-entry {
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
@media (max-width: 64rem) {
  .report-catalog-page__groups {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 48rem) {
  .report-catalog-page__item {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
