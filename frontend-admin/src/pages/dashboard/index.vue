<script setup lang="ts">
import { h } from 'vue'
import {
  FileTextOutlined,
  DollarOutlined,
  PayCircleOutlined,
  WalletOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons-vue'

interface Kpi {
  title: string
  value: string
  unit: string
  change: string
  up: boolean
  icon: ReturnType<typeof h>
  color: string
}

const kpis: Kpi[] = [
  {
    title: '合同总数',
    value: '128',
    unit: '份',
    change: '8.5%',
    up: true,
    icon: h(FileTextOutlined),
    color: '#3b82f6',
  },
  {
    title: '合同总金额(含税)',
    value: '125,680.35',
    unit: '万元',
    change: '12.3%',
    up: true,
    icon: h(DollarOutlined),
    color: '#36c267',
  },
  {
    title: '已付款金额',
    value: '68,430.20',
    unit: '万元',
    change: '9.7%',
    up: true,
    icon: h(PayCircleOutlined),
    color: '#f59e0b',
  },
  {
    title: '未付款金额',
    value: '57,250.15',
    unit: '万元',
    change: '15.2%',
    up: true,
    icon: h(WalletOutlined),
    color: '#7c3aed',
  },
  {
    title: '逾期合同数',
    value: '6',
    unit: '份',
    change: '25.0%',
    up: false,
    icon: h(ClockCircleOutlined),
    color: '#31c7cf',
  },
]
</script>

<template>
  <div class="dashboard">
    <a-breadcrumb class="breadcrumb">
      <a-breadcrumb-item>首页</a-breadcrumb-item>
      <a-breadcrumb-item>工作台</a-breadcrumb-item>
    </a-breadcrumb>

    <a-row :gutter="10" class="kpi-row">
      <a-col v-for="kpi in kpis" :key="kpi.title" :span="24 / 5" :flex="1">
        <div class="kpi-card">
          <div class="kpi-icon" :style="{ background: kpi.color }">
            <component :is="kpi.icon" />
          </div>
          <div class="kpi-body">
            <div class="kpi-title">{{ kpi.title }}</div>
            <div class="kpi-value">
              {{ kpi.value }} <small>{{ kpi.unit }}</small>
            </div>
            <div class="kpi-change">
              较上月
              <span :class="kpi.up ? 'up' : 'down'">
                {{ kpi.up ? '↑' : '↓' }} {{ kpi.change }}
              </span>
            </div>
          </div>
        </div>
      </a-col>
    </a-row>

    <a-card title="工作台" class="placeholder-card">
      <a-empty description="业务页面建设中，敬请期待" />
    </a-card>
  </div>
</template>

<style scoped>
.dashboard {
  min-height: 100%;
}

.breadcrumb {
  margin-bottom: 16px;
}

.kpi-row {
  margin-bottom: 14px;
}

.kpi-card {
  height: 96px;
  padding: 18px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

.kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}

.kpi-title {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 8px;
}

.kpi-value {
  font-size: 22px;
  font-weight: 800;
  color: #111827;
}

.kpi-value small {
  font-size: 13px;
  font-weight: 500;
  margin-left: 4px;
}

.kpi-change {
  font-size: 12px;
  color: #6b7280;
  margin-top: 6px;
}

.up {
  color: #ef4444;
}

.down {
  color: #16a34a;
}

.placeholder-card {
  border-radius: 10px;
}
</style>
