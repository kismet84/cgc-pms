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
    <div class="org-metric">
      <div class="org-metric-icon company"><BankOutlined /></div>
      <div>
        <span>公司</span>
        <strong>{{ companyTotal }}</strong>
      </div>
    </div>
    <div class="org-metric">
      <div class="org-metric-icon dept"><ClusterOutlined /></div>
      <div>
        <span>{{ currentCompanyName }}部门</span>
        <strong>{{ departmentCount }}</strong>
      </div>
    </div>
    <div class="org-metric">
      <div class="org-metric-icon position"><TeamOutlined /></div>
      <div>
        <span>岗位</span>
        <strong>{{ positionTotal }}</strong>
      </div>
    </div>
    <div class="org-metric">
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
  gap: 10px;
  margin-bottom: 14px;
}

.org-metric {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 82px;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow-soft);
}

.org-metric-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  color: #fff;
  font-size: 15px;
  border-radius: 8px;
}

.org-metric-icon.company { background: #1668dc; }
.org-metric-icon.dept { background: #0ea5e9; }
.org-metric-icon.position { background: #14b8a6; }
.org-metric-icon.health { background: #16a34a; }

.org-metric span {
  display: block;
  max-width: 180px;
  overflow: hidden;
  color: var(--muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-metric strong {
  display: block;
  margin-top: 4px;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.org-metric small {
  margin-left: 2px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
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
