<script setup lang="ts">
import {
  DollarOutlined,
  FileTextOutlined,
  FlagOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'

defineProps<{
  projectStats: {
    total: number
    ongoing: number
    completed: number
    risk: number
  }
  totalContractAmount: number
}>()
</script>

<template>
  <section class="lg-kpi-strip project-kpi-summary" aria-label="项目关键指标">
    <div class="project-kpi-item">
      <div class="project-kpi-content">
        <span class="project-kpi-label">项目总数</span>
        <span class="project-kpi-value">{{ projectStats.total || 0 }} <small>个</small></span>
      </div>
      <span class="project-kpi-icon is-total"><FileTextOutlined /></span>
    </div>
    <div class="project-kpi-item">
      <div class="project-kpi-content">
        <span class="project-kpi-label">合同总金额</span>
        <span class="project-kpi-value">
          {{
            totalContractAmount
              ? (totalContractAmount / 10000).toLocaleString('zh-CN', {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                })
              : '0.00'
          }}
          <small>万元</small>
        </span>
      </div>
      <span class="project-kpi-icon is-amount"><DollarOutlined /></span>
    </div>
    <div class="project-kpi-item">
      <div class="project-kpi-content">
        <span class="project-kpi-label">在建项目</span>
        <span class="project-kpi-value">{{ projectStats.ongoing || 0 }} <small>个</small></span>
      </div>
      <span class="project-kpi-icon is-ongoing"><SafetyCertificateOutlined /></span>
    </div>
    <div class="project-kpi-item">
      <div class="project-kpi-content">
        <span class="project-kpi-label">已竣工项目</span>
        <span class="project-kpi-value">{{ projectStats.completed || 0 }} <small>个</small></span>
      </div>
      <span class="project-kpi-icon is-completed"><FlagOutlined /></span>
    </div>
    <div class="project-kpi-item">
      <div class="project-kpi-content">
        <span class="project-kpi-label">风险项目</span>
        <span class="project-kpi-value">{{ projectStats.risk || 0 }} <small>个</small></span>
      </div>
      <span class="project-kpi-icon is-risk"><WarningOutlined /></span>
    </div>
  </section>
</template>

<style scoped>
.project-kpi-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  margin-bottom: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.project-kpi-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  column-gap: 10px;
  gap: 10px;
  min-width: 0;
  padding: 10px 18px;
}

.project-kpi-item + .project-kpi-item {
  border-left: 1px solid var(--border-subtle);
}

.project-kpi-content {
  min-width: 0;
}

.project-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 18px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 10px;
  flex: 0 0 auto;
}

.project-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.project-kpi-icon.is-ongoing,
.project-kpi-icon.is-completed {
  color: var(--success);
  background: var(--success-soft);
}

.project-kpi-icon.is-risk {
  color: var(--error);
  background: var(--error-soft);
}

.project-kpi-label {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.project-kpi-value {
  display: block;
  margin-top: 4px;
  color: var(--text);
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}

.project-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 1200px) {
  .project-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .project-kpi-summary {
    grid-template-columns: 1fr;
  }

  .project-kpi-item + .project-kpi-item {
    border-left: 0;
    border-top: 1px solid var(--border-subtle);
  }
}
</style>
