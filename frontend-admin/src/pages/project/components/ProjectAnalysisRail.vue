<script setup lang="ts">
defineProps<{
  projectStats: {
    total: number
    ongoing: number
    completed: number
    risk: number
  }
  totalContractAmount: number
  typeDistribution: Array<{
    label: string
    value: number
    percent: number
  }>
  statusDistribution: Array<{
    key: string
    label: string
    value: number
    percent: number
  }>
  riskProjects: Array<{
    name: string
    status: string
  }>
  recentProjects: Array<{
    name: string
    status: string
  }>
}>()
</script>

<template>
  <aside class="lg-analysis-rail project-analysis-rail" aria-label="项目辅助分析">
    <div class="lg-analysis-panel lg-fill-card project-analysis-panel">
      <header class="project-analysis-head">
        <div>
          <div class="project-analysis-title">辅助分析</div>
          <div class="project-analysis-subtitle">项目概览与重点分组</div>
        </div>
      </header>

      <section class="project-analysis-section">
        <div class="project-section-title">基本盘</div>
        <div class="project-overview-list">
          <div class="project-overview-row">
            <span>项目总数</span>
            <strong>{{ projectStats.total || 0 }} 个</strong>
          </div>
          <div class="project-overview-row">
            <span>合同总金额</span>
            <strong>
              {{
                totalContractAmount
                  ? (totalContractAmount / 10000).toLocaleString('zh-CN', {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    })
                  : '0.00'
              }}
              万元
            </strong>
          </div>
          <div class="project-overview-row">
            <span>在建 / 已竣工</span>
            <strong>{{ projectStats.ongoing || 0 }} / {{ projectStats.completed || 0 }}</strong>
          </div>
        </div>
      </section>

      <section class="project-analysis-section">
        <div class="project-section-title">项目类型分布</div>
        <div v-for="item in typeDistribution" :key="item.label" class="lg-type-row">
          <span class="lg-type-dot project-dot-primary"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-num">{{ item.value }}</span>
          <span class="lg-type-pct">{{ item.percent }}%</span>
        </div>
        <div v-if="!typeDistribution.length" class="lg-warning-empty">暂无项目类型</div>
      </section>

      <section class="project-analysis-section">
        <div class="project-section-title">项目状态</div>
        <div v-for="item in statusDistribution" :key="item.key" class="lg-type-row">
          <span class="lg-type-dot project-dot-success"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-num">{{ item.value }}</span>
          <span class="lg-type-pct">{{ item.percent }}%</span>
        </div>
        <div v-if="!statusDistribution.length" class="lg-warning-empty">暂无项目状态</div>
      </section>

      <section class="project-analysis-section">
        <div class="project-warning-head">
          <div class="project-section-title">风险项目</div>
          <span class="project-warning-count">{{ projectStats.risk }} 项</span>
        </div>
        <div v-for="item in riskProjects" :key="item.name" class="lg-type-row">
          <span class="lg-type-dot project-dot-warning"></span>
          <span class="lg-type-label">{{ item.name }}</span>
          <span class="project-risk-status">{{ item.status }}</span>
        </div>
      </section>

      <section class="project-analysis-section">
        <div class="project-warning-head">
          <div class="project-section-title">近期项目</div>
          <span class="project-warning-count">{{ recentProjects.length }} 项</span>
        </div>
        <div v-for="item in recentProjects" :key="item.name" class="lg-type-row">
          <span class="lg-type-dot project-dot-primary"></span>
          <span class="lg-type-label">{{ item.name }}</span>
          <span class="project-risk-status">{{ item.status }}</span>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.project-analysis-rail {
}

.project-analysis-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0;
  padding: 0 0 12px;
  overflow: auto;
  position: sticky;
  top: 0;
}

.project-analysis-head {
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.project-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.project-analysis-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 20px;
}

.project-analysis-subtitle,
.project-warning-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.project-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 10px 16px 0;
}

.project-analysis-section + .project-analysis-section {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.project-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 18px;
}

.project-overview-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.project-overview-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 18px;
}

.project-overview-row strong {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  text-align: right;
  white-space: nowrap;
}

.project-analysis-section :deep(.lg-type-row),
.project-analysis-section .lg-type-row {
  display: grid;
  grid-template-columns: 9px minmax(0, 1fr) auto auto;
  align-items: center;
  column-gap: 8px;
  min-height: 0;
  padding: 0;
}

.project-analysis-section :deep(.lg-type-label),
.project-analysis-section .lg-type-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-analysis-section :deep(.lg-type-num),
.project-analysis-section .lg-type-num,
.project-analysis-section :deep(.lg-type-pct),
.project-analysis-section .lg-type-pct {
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
}

.project-dot-primary {
  background: var(--primary);
}

.project-dot-success {
  background: var(--success);
}

.project-dot-warning {
  background: var(--warning);
}

.project-risk-status {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .project-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .project-analysis-panel {
    position: static;
  }
}
</style>
