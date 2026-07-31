<script setup lang="ts">
import {
  BankOutlined,
  ClusterOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons-vue'

defineProps<{
  companyTotal: number
  currentCompanyName: string
  departmentCount: number
  positionTotal: number
  enabledRate: string
}>()
</script>

<template>
  <div class="org-metric-strip">
    <div class="org-metric lg-kpi-card">
      <div class="org-metric-icon company"><BankOutlined /></div>
      <div>
        <span>公司</span>
        <strong>{{ companyTotal }}</strong>
      </div>
    </div>
    <div class="org-metric lg-kpi-card">
      <div class="org-metric-icon dept"><ClusterOutlined /></div>
      <div>
        <span>{{ currentCompanyName }}部门</span>
        <strong>{{ departmentCount }}</strong>
      </div>
    </div>
    <div class="org-metric lg-kpi-card">
      <div class="org-metric-icon position"><TeamOutlined /></div>
      <div>
        <span>岗位</span>
        <strong>{{ positionTotal }}</strong>
      </div>
    </div>
    <div class="org-metric lg-kpi-card">
      <div class="org-metric-icon health"><SafetyCertificateOutlined /></div>
      <div>
        <span>启用率</span>
        <strong>{{ enabledRate }}<small>%</small></strong>
      </div>
    </div>
  </div>
</template>

<style scoped>
.org-metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.org-metric {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 80px;
  padding: 16px 20px 20px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.org-metric-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  color: var(--surface);
  font-size: 20px;
  border-radius: var(--radius-sm);
}

.org-metric-icon.company {
  background: var(--primary);
}
.org-metric-icon.dept {
  background: var(--primary);
}
.org-metric-icon.position {
  background: var(--info);
}
.org-metric-icon.health {
  background: var(--success);
}

.org-metric span {
  display: block;
  max-width: 180px;
  overflow: hidden;
  color: var(--muted);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-metric strong {
  display: block;
  margin-top: 4px;
  color: var(--text);
  font-size: 24px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.org-metric small {
  margin-left: 2px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 400;
}

.org-metric:last-child {
  position: relative;
}

.org-metric:last-child::after {
  position: absolute;
  right: 20px;
  bottom: 16px;
  left: 80px;
  height: 4px;
  content: '';
  background: var(--success);
  border-radius: 99px;
}

@media (max-width: 1180px) {
  .org-metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .org-metric-strip {
    grid-template-columns: 1fr;
  }
}
</style>
