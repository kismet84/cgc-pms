<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { message } from "ant-design-vue";
import {
  ReloadOutlined,
  AimOutlined,
  LockOutlined,
  LineChartOutlined,
  DollarOutlined,
  WarningOutlined,
  RiseOutlined,
  PieChartOutlined,
  BarChartOutlined,
  AlertOutlined,
} from "@ant-design/icons-vue";
import VChart from "vue-echarts";
import { getCostSummary, refreshCostSummary } from "@/api/modules/cost";
import { getProjectList } from "@/api/modules/project";
import type { CostSummaryVO, CostSubjectSummaryVO } from "@/types/cost";
import type { ProjectVO } from "@/types/project";

const projectList = ref<ProjectVO[]>([]);
const selectedProjectId = ref<string | undefined>(undefined);
const loading = ref(false);
const summary = ref<CostSummaryVO | null>(null);

async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 50 });
    projectList.value = res.records;
  } catch (e: unknown) {
    console.error(e);
    projectList.value = [];
  }
}

async function fetchSummary() {
  if (!selectedProjectId.value) {
    summary.value = null;
    return;
  }
  loading.value = true;
  try {
    summary.value = await getCostSummary(selectedProjectId.value);
  } catch (e: unknown) {
    console.error(e);
    summary.value = null;
    message.error("加载动态成本汇总失败");
  } finally {
    loading.value = false;
  }
}

async function handleRefresh() {
  if (!selectedProjectId.value) {
    message.warning("请先选择项目");
    return;
  }
  loading.value = true;
  try {
    summary.value = await refreshCostSummary(selectedProjectId.value);
    message.success("刷新成功");
  } catch (e: unknown) {
    console.error(e);
    message.error("刷新失败");
  } finally {
    loading.value = false;
  }
}

function handleProjectChange(val: string | undefined) {
  selectedProjectId.value = val;
  if (val) fetchSummary();
  else summary.value = null;
}

function fmtAmount(val: string | undefined): string {
  if (!val) return "0.00";
  const n = parseFloat(val);
  if (isNaN(n)) return "0.00";
  return (n / 10000).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtDeviation(val: string | undefined): string {
  if (!val) return "0.00";
  const n = parseFloat(val);
  if (isNaN(n)) return "0.00";
  return (n / 10000).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function getDeviationColor(val: string | undefined): string {
  if (!val) return "#6b7280";
  const n = parseFloat(val);
  if (n > 0) return "#ef4444";
  if (n < 0) return "#22c55e";
  return "#6b7280";
}

function fmtPercent(val: string | undefined, base: string | undefined): string {
  if (!val || !base) return "0.0%";
  const v = parseFloat(val);
  const b = parseFloat(base);
  if (isNaN(v) || isNaN(b) || b === 0) return "0.0%";
  return ((v / b) * 100).toFixed(1) + "%";
}

const subjectColumns = [
  { title: "成本科目", dataIndex: "costSubjectName", width: 180 },
  { title: "目标成本(万元)", dataIndex: "targetCost", width: 140, align: "right" as const, key: "targetCost" },
  { title: "合同锁定成本(万元)", dataIndex: "contractLockedCost", width: 160, align: "right" as const, key: "contractLockedCost" },
  { title: "实际成本(万元)", dataIndex: "actualCost", width: 140, align: "right" as const, key: "actualCost" },
  { title: "已付款(万元)", dataIndex: "paidAmount", width: 130, align: "right" as const, key: "paidAmount" },
  { title: "动态成本(万元)", dataIndex: "dynamicCost", width: 140, align: "right" as const, key: "dynamicCost" },
  { title: "成本偏差(万元)", dataIndex: "costDeviation", width: 140, align: "right" as const, key: "costDeviation" },
];

// ---- Chart options ----
const executionOption = computed(() => {
  if (!summary.value) return {};
  const s = summary.value;
  return {
    tooltip: { trigger: "axis" as const },
    grid: { left: 10, right: 20, top: 20, bottom: 10, containLabel: true },
    xAxis: {
      type: "category" as const,
      data: ["目标成本", "锁定成本", "实际成本", "已付款", "动态成本"],
      axisLabel: { fontSize: 12 },
    },
    yAxis: {
      type: "value" as const,
      axisLabel: { fontSize: 11, formatter: "{value} 万" },
    },
    series: [
      {
        type: "bar",
        data: [
          parseFloat(s.targetCost) / 10000,
          parseFloat(s.contractLockedCost) / 10000,
          parseFloat(s.actualCost) / 10000,
          parseFloat(s.paidAmount) / 10000,
          parseFloat(s.dynamicCost) / 10000,
        ],
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: (params: any) => {
            const colors = ["#3b82f6", "#8b5cf6", "#f59e0b", "#22c55e", "#ef4444"];
            return colors[params.dataIndex] ?? "#3b82f6";
          },
        },
        barMaxWidth: 32,
      },
    ],
  };
});

const compositionOption = computed(() => {
  if (!summary.value || !summary.value.subjects.length) return {};
  const data = summary.value.subjects
    .filter((s) => parseFloat(s.dynamicCost) > 0)
    .map((s) => ({
      name: s.costSubjectName,
      value: parseFloat(s.dynamicCost) / 10000,
    }));
  return {
    tooltip: { trigger: "item" as const, formatter: "{b}: {c} 万元 ({d}%)" as any },
    series: [
      {
        type: "pie",
        radius: ["45%", "70%"],
        center: ["50%", "55%"],
        data,
        label: { fontSize: 11 },
        emphasis: { itemStyle: { shadowBlur: 8, shadowColor: "rgba(0,0,0,0.15)" } },
      },
    ],
  };
});

const deviationOption = computed(() => {
  if (!summary.value || !summary.value.subjects.length) return {};
  const subjects = summary.value.subjects;
  return {
    tooltip: { trigger: "axis" as const },
    grid: { left: 10, right: 20, top: 10, bottom: 10, containLabel: true },
    xAxis: {
      type: "category" as const,
      data: subjects.map((s) => s.costSubjectName),
      axisLabel: { fontSize: 10, rotate: 25 },
    },
    yAxis: {
      type: "value" as const,
      axisLabel: { fontSize: 11, formatter: "{value} 万" },
    },
    series: [
      {
        type: "bar",
        data: subjects.map((s) => parseFloat(s.costDeviation) / 10000),
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: (params: any) => (params.value > 0 ? "#ef4444" : "#22c55e"),
        },
        barMaxWidth: 24,
      },
    ],
  };
});

const rankingOption = computed(() => {
  if (!summary.value || !summary.value.subjects.length) return {};
  const subjects = [...summary.value.subjects]
    .sort((a, b) => parseFloat(b.dynamicCost) - parseFloat(a.dynamicCost))
    .slice(0, 8);
  return {
    tooltip: { trigger: "axis" as const },
    grid: { left: 10, right: 30, top: 10, bottom: 10, containLabel: true },
    xAxis: {
      type: "value" as const,
      axisLabel: { fontSize: 11, formatter: "{value} 万" },
    },
    yAxis: {
      type: "category" as const,
      data: subjects.map((s) => s.costSubjectName).reverse(),
      axisLabel: { fontSize: 11 },
    },
    series: [
      {
        type: "bar",
        data: subjects.map((s) => parseFloat(s.dynamicCost) / 10000).reverse(),
        itemStyle: {
          borderRadius: [0, 3, 3, 0],
          color: (params: any) => {
            const colors = ["#3b82f6", "#6366f1", "#8b5cf6", "#a855f7", "#d946ef"];
            return colors[params.dataIndex % colors.length];
          },
        },
        barMaxWidth: 18,
      },
    ],
  };
});

const overBudgetItems = computed(() => {
  if (!summary.value) return [];
  return summary.value.subjects.filter((s) => parseFloat(s.costDeviation) > 0);
});

const anomalyItems = computed(() => {
  if (!summary.value) return [];
  return summary.value.subjects
    .filter((s) => {
      const dev = parseFloat(s.costDeviation);
      const target = parseFloat(s.targetCost);
      return dev > 0 && target > 0 && dev / target > 0.1;
    })
    .sort((a, b) => parseFloat(b.costDeviation) - parseFloat(a.costDeviation));
});

onMounted(() => {
  fetchProjects();
});
</script>

<template>
  <div class="project-target-redesign app-page">
    <!-- Page head -->
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb">
        <a-breadcrumb-item>成本管理</a-breadcrumb-item>
        <a-breadcrumb-item>动态成本汇总</a-breadcrumb-item>
      </a-breadcrumb>
      <h1 class="app-page-title">动态成本汇总</h1>
      <div class="pt-head-actions">
        <a-select
          v-model:value="selectedProjectId"
          placeholder="请选择项目"
          allow-clear
          style="width: 240px"
          show-search
          :filter-option="(input: string, option: any) => option.label?.toLowerCase().includes(input.toLowerCase())"
          @change="handleProjectChange"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
        <a-button type="primary" @click="handleRefresh" :disabled="!selectedProjectId">
          <ReloadOutlined />刷新
        </a-button>
      </div>
    </div>

    <template v-if="summary">
      <!-- KPI strip -->
      <div class="pt-kpi-strip" style="grid-template-columns: repeat(5, 1fr)">
        <div class="pt-kpi">
          <div class="pt-kpi-label">目标成本</div>
          <div class="pt-kpi-value">{{ fmtAmount(summary.targetCost) }} <small>万元</small></div>
        </div>
        <div class="pt-kpi">
          <div class="pt-kpi-label">锁定成本</div>
          <div class="pt-kpi-value">{{ fmtAmount(summary.contractLockedCost) }} <small>万元</small></div>
        </div>
        <div class="pt-kpi">
          <div class="pt-kpi-label">动态成本</div>
          <div class="pt-kpi-value">{{ fmtAmount(summary.dynamicCost) }} <small>万元</small></div>
        </div>
        <div class="pt-kpi">
          <div class="pt-kpi-label">偏差金额</div>
          <div class="pt-kpi-value" :style="{ color: getDeviationColor(summary.costDeviation) }">
            {{ fmtDeviation(summary.costDeviation) }} <small>万元</small>
          </div>
        </div>
        <div class="pt-kpi">
          <div class="pt-kpi-label">偏差率</div>
          <div class="pt-kpi-value" :style="{ color: getDeviationColor(summary.costDeviation) }">
            {{ fmtPercent(summary.costDeviation, summary.targetCost) }}
          </div>
        </div>
      </div>

      <!-- Analysis panels -->
      <div class="pt-ledger-layout">
        <main style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px">
          <!-- Row 1: 成本执行概览 + 成本构成分析 -->
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
            <section class="pt-panel">
              <div class="pt-panel-header">成本执行概览</div>
              <div class="pt-panel-body">
                <v-chart :option="executionOption" autoresize class="pt-chart" />
              </div>
            </section>
            <section class="pt-panel">
              <div class="pt-panel-header">成本构成分析</div>
              <div class="pt-panel-body">
                <v-chart :option="compositionOption" autoresize class="pt-chart" />
              </div>
            </section>
          </div>

          <!-- Row 2: 偏差趋势分析 + 超预算预警 -->
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
            <section class="pt-panel">
              <div class="pt-panel-header">偏差趋势分析</div>
              <div class="pt-panel-body">
                <v-chart :option="deviationOption" autoresize class="pt-chart" />
              </div>
            </section>
            <section class="pt-panel">
              <div class="pt-panel-header">超预算预警</div>
              <div class="pt-panel-body">
                <ul class="pt-compact-list">
                  <li v-for="item in overBudgetItems.slice(0, 6)" :key="item.costSubjectId" class="pt-compact-row">
                    <span>{{ item.costSubjectName }}</span>
                    <b style="color: #ef4444">+{{ fmtDeviation(item.costDeviation) }} 万</b>
                  </li>
                  <li v-if="overBudgetItems.length === 0" class="pt-compact-row">
                    <span>无超预算科目</span>
                  </li>
                </ul>
              </div>
            </section>
          </div>

          <!-- Row 3: 成本科目排行 + 异常明细 -->
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
            <section class="pt-panel">
              <div class="pt-panel-header">成本科目排行</div>
              <div class="pt-panel-body">
                <v-chart :option="rankingOption" autoresize class="pt-chart" />
              </div>
            </section>
            <section class="pt-panel">
              <div class="pt-panel-header">异常明细</div>
              <div class="pt-panel-body">
                <ul class="pt-compact-list">
                  <li v-for="item in anomalyItems.slice(0, 5)" :key="'anomaly-' + item.costSubjectId" class="pt-compact-row">
                    <span>{{ item.costSubjectName }}</span>
                    <b style="color: #ef4444">偏差 +{{ fmtDeviation(item.costDeviation) }} 万</b>
                  </li>
                  <li v-if="anomalyItems.length === 0" class="pt-compact-row">
                    <span>无异常科目</span>
                  </li>
                </ul>
              </div>
            </section>
          </div>

          <!-- Subject detail table -->
          <section class="pt-panel pt-table-panel">
            <div class="pt-panel-header">科目明细</div>
            <a-table
              :columns="subjectColumns"
              :data-source="summary.subjects"
              :loading="loading"
              :pagination="false"
              row-key="costSubjectId"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'targetCost'">
                  <span>{{ fmtAmount(record.targetCost) }}</span>
                </template>
                <template v-else-if="column.key === 'contractLockedCost'">
                  <span>{{ fmtAmount(record.contractLockedCost) }}</span>
                </template>
                <template v-else-if="column.key === 'actualCost'">
                  <span>{{ fmtAmount(record.actualCost) }}</span>
                </template>
                <template v-else-if="column.key === 'paidAmount'">
                  <span>{{ fmtAmount(record.paidAmount) }}</span>
                </template>
                <template v-else-if="column.key === 'dynamicCost'">
                  <span>{{ fmtAmount(record.dynamicCost) }}</span>
                </template>
                <template v-else-if="column.key === 'costDeviation'">
                  <span :style="{ color: getDeviationColor(record.costDeviation), fontWeight: 600 }">
                    {{ fmtDeviation(record.costDeviation) }}
                  </span>
                </template>
              </template>
            </a-table>
          </section>
        </main>
      </div>
    </template>

    <template v-else>
      <div class="pt-panel" style="text-align: center; padding: 80px 0; color: #9ca3af; font-size: 14px">
        <LineChartOutlined style="font-size: 48px; margin-bottom: 16px; display: block; color: #d1d5db" />
        请选择一个项目，查看动态成本汇总分析
      </div>
    </template>
  </div>
</template>

<style scoped>
.pt-chart {
  width: 100%;
  height: 260px;
}
</style>