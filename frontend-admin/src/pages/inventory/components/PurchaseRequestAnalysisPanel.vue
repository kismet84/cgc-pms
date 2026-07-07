<script setup lang="ts">
defineProps<{
  statusBreakdown: { key: string; label: string; count: number; pct: number; color: string }[]
  approvalBreakdown: { key: string; label: string; count: number; pct: number; color: string }[]
  recentRequests: { id: string; projectName?: string; requestCode: string }[]
}>()

const emit = defineEmits<{
  refresh: []
}>()
</script>

<template>
  <aside class="lg-analysis-rail purchase-request-analysis-rail" aria-label="采购申请辅助分析">
    <div class="lg-analysis-panel lg-fill-card purchase-request-analysis-panel">
      <header class="purchase-request-analysis-head">
        <div>
          <div class="purchase-request-analysis-title">申请分析</div>
          <div class="purchase-request-analysis-subtitle">业务状态、审批状态与近期申请</div>
        </div>
        <a-button type="link" size="small" @click="emit('refresh')">刷新</a-button>
      </header>

      <section class="purchase-request-analysis-section">
        <div class="purchase-request-section-title">业务状态分布</div>
        <div v-for="item in statusBreakdown" :key="item.key" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: item.color }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: item.pct + '%', background: item.color }"
            ></span>
          </span>
          <span class="lg-type-num">{{ item.count }}</span>
          <span class="lg-type-pct">{{ item.pct }}%</span>
        </div>
        <div v-if="!statusBreakdown.length" class="purchase-request-analysis-empty">
          暂无业务状态数据
        </div>
      </section>

      <section class="purchase-request-analysis-section">
        <div class="purchase-request-section-title">审批状态</div>
        <div v-for="item in approvalBreakdown" :key="item.key" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: item.color }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: item.pct + '%', background: item.color }"
            ></span>
          </span>
          <span class="lg-type-num">{{ item.count }}</span>
          <span class="lg-type-pct">{{ item.pct }}%</span>
        </div>
      </section>

      <section class="purchase-request-analysis-section">
        <div class="purchase-request-warning-head">
          <div class="purchase-request-section-title">近期申请</div>
          <span class="purchase-request-warning-count">{{ recentRequests.length }} 项</span>
        </div>
        <div v-for="item in recentRequests" :key="item.id" class="lg-warning-item">
          <span class="lg-warning-project">{{ item.projectName || '-' }}</span>
          <span class="lg-warning-title">{{ item.requestCode }}</span>
        </div>
        <div v-if="!recentRequests.length" class="lg-warning-empty">暂无采购申请</div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.purchase-request-analysis-rail {
  display: flex;
  min-height: 0;
}

.purchase-request-analysis-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0;
  padding: 0 0 12px;
  overflow: auto;
  position: sticky;
  top: 0;
}

.purchase-request-analysis-head,
.purchase-request-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.purchase-request-analysis-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 20px;
}

.purchase-request-analysis-subtitle,
.purchase-request-warning-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.purchase-request-analysis-head {
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.purchase-request-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 10px 16px 0;
}

.purchase-request-analysis-section + .purchase-request-analysis-section {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.purchase-request-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.purchase-request-analysis-empty {
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: center;
}

.purchase-request-analysis-section :deep(.lg-type-row),
.lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

@media (max-width: 1200px) {
  .purchase-request-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .purchase-request-analysis-panel {
    position: static;
  }
}
</style>
