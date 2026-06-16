# UI Page Code Template Guide

## Status

Draft for future implementation tasks.

## Purpose

This document gives code-level page template examples for the approved new UI language. It is not an implementation patch and should not be copied blindly into the app without adapting to the existing routes, APIs, stores, and component boundaries.

Use these examples as a design and development reference when creating future task files for page redesign work.

## Shared UI Language

All pages should follow the approved `清爽企业级工作台` direction:

- light gray page background;
- white content surfaces;
- compact enterprise density;
- subtle borders;
- 6-8px radius;
- blue primary actions;
- semantic red, orange, green, and blue only when meaningful;
- no marketing hero, decorative gradient blobs, nested cards, or oversized display type.

## Suggested Shared Component Contracts

These component names are illustrative. If the codebase already has equivalent patterns, reuse those names and APIs.

```vue
<template>
  <div class="app-page">
    <PageHeader
      :breadcrumb="['合同管理', '合同台账']"
      title="合同台账"
    >
      <a-button type="primary">新建合同</a-button>
    </PageHeader>

    <FilterSurface>
      <!-- compact query form -->
    </FilterSurface>

    <KpiStrip :items="kpiItems" />

    <DataPanel title="主数据">
      <!-- table or content -->
    </DataPanel>
  </div>
</template>
```

Recommended shared component responsibilities:

- `PageHeader`: breadcrumb, title, status tag, right actions.
- `FilterSurface`: compact query form with optional advanced filters.
- `KpiStrip`: horizontally scannable metrics.
- `DataPanel`: single-level bordered white panel.
- `AnalysisRail`: right-side analysis stack for ledger pages.
- `ActionToolbar`: new, export, column settings, refresh.
- `StatusTag`: consistent status colors and labels.
- `AmountText`: right-aligned amount formatting and semantic color.
- `QuietEmpty`: compact empty state.

## Template 1: Ledger List Page

Use for contract ledger, project ledger, variation list, settlement list, payment list, procurement list, inventory ledger, invoice ledger, and approval list.

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import VChart from 'vue-echarts'

interface KpiItem {
  label: string
  value: string
  unit?: string
  tone?: 'blue' | 'green' | 'orange' | 'red' | 'slate'
}

const loading = ref(false)
const query = ref({
  projectId: undefined as string | undefined,
  status: undefined as string | undefined,
  keyword: '',
  dateRange: [] as string[],
})

const rows = ref([])

const kpiItems = computed<KpiItem[]>(() => [
  { label: '总数', value: '128', unit: '条', tone: 'blue' },
  { label: '总金额', value: '268,580.00', unit: '万元', tone: 'green' },
  { label: '待处理', value: '18', unit: '项', tone: 'orange' },
  { label: '风险预警', value: '3', unit: '项', tone: 'red' },
])

const columns = [
  { title: '编号', dataIndex: 'code', width: 140 },
  { title: '名称', dataIndex: 'name', ellipsis: true },
  { title: '类型', dataIndex: 'type', width: 120 },
  { title: '金额(万元)', dataIndex: 'amount', width: 140, align: 'right' as const },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '日期', dataIndex: 'date', width: 120 },
  { title: '操作', dataIndex: 'actions', width: 150, fixed: 'right' as const },
]

const distributionOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { orient: 'vertical', right: 0, top: 'middle' },
  series: [
    {
      type: 'pie',
      radius: ['48%', '70%'],
      center: ['36%', '50%'],
      data: [
        { name: '生效中', value: 68 },
        { name: '审批中', value: 32 },
        { name: '已归档', value: 20 },
      ],
    },
  ],
}))

function handleSearch() {
  // Call existing list API with query.value.
}

function handleReset() {
  query.value = { projectId: undefined, status: undefined, keyword: '', dateRange: [] }
  handleSearch()
}
</script>

<template>
  <div class="app-page ledger-page">
    <PageHeader :breadcrumb="['模块名称', '台账列表']" title="台账列表">
      <a-button type="primary">新建</a-button>
    </PageHeader>

    <FilterSurface>
      <a-form layout="inline" :model="query">
        <a-form-item label="项目名称">
          <a-select v-model:value="query.projectId" placeholder="请选择项目" allow-clear />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="query.status" placeholder="请选择状态" allow-clear />
        </a-form-item>
        <a-form-item label="关键词">
          <a-input v-model:value="query.keyword" placeholder="请输入编号或名称" allow-clear />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">查询</a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </FilterSurface>

    <KpiStrip :items="kpiItems" />

    <div class="ledger-layout">
      <DataPanel title="数据列表" class="ledger-main-panel">
        <ActionToolbar>
          <a-button type="primary">新建</a-button>
          <a-button>导出</a-button>
          <a-button>列设置</a-button>
        </ActionToolbar>

        <a-table
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          row-key="id"
          size="small"
          :scroll="{ x: 980 }"
        />
      </DataPanel>

      <AnalysisRail>
        <DataPanel title="状态分布">
          <VChart :option="distributionOption" autoresize class="rail-chart" />
        </DataPanel>
        <DataPanel title="风险预警">
          <ul class="warning-list">
            <li>逾期 3 项</li>
            <li>审批超时 5 项</li>
          </ul>
        </DataPanel>
      </AnalysisRail>
    </div>
  </div>
</template>
```

## Template 2: Detail Page

Use for project detail, contract detail, settlement detail, payment detail, purchase detail, and invoice detail.

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'

const activeTab = ref('basic')

const summary = computed(() => ({
  title: 'HT-2026-001 总包合同',
  status: '生效中',
  amount: '56,800.00',
  partner: '中建五局集团有限公司',
  owner: '张三',
}))

const timeline = [
  { title: '发起审批', time: '2026-06-01 09:20', user: '李四' },
  { title: '部门审核', time: '2026-06-01 14:10', user: '王五' },
  { title: '审批通过', time: '2026-06-02 10:30', user: '赵六' },
]
</script>

<template>
  <div class="app-page detail-page">
    <PageHeader :breadcrumb="['合同管理', '合同详情']" :title="summary.title">
      <a-space>
        <StatusTag :status="summary.status" />
        <a-button>返回</a-button>
        <a-button type="primary">编辑</a-button>
      </a-space>
    </PageHeader>

    <div class="detail-layout">
      <main class="detail-main">
        <DataPanel title="核心摘要">
          <div class="summary-grid">
            <div>
              <span>合同金额</span>
              <b>{{ summary.amount }} 万元</b>
            </div>
            <div>
              <span>合作方</span>
              <b>{{ summary.partner }}</b>
            </div>
            <div>
              <span>负责人</span>
              <b>{{ summary.owner }}</b>
            </div>
          </div>
        </DataPanel>

        <DataPanel class="detail-tabs-panel">
          <a-tabs v-model:activeKey="activeTab">
            <a-tab-pane key="basic" tab="基础信息">
              <DescriptionGrid />
            </a-tab-pane>
            <a-tab-pane key="business" tab="业务明细">
              <a-table size="small" />
            </a-tab-pane>
            <a-tab-pane key="approval" tab="审批记录">
              <a-timeline>
                <a-timeline-item v-for="item in timeline" :key="item.time">
                  {{ item.title }} - {{ item.user }} - {{ item.time }}
                </a-timeline-item>
              </a-timeline>
            </a-tab-pane>
            <a-tab-pane key="files" tab="附件">
              <AttachmentList />
            </a-tab-pane>
          </a-tabs>
        </DataPanel>
      </main>

      <aside class="detail-rail">
        <DataPanel title="流程状态">
          <a-steps direction="vertical" size="small" :current="2" />
        </DataPanel>
        <DataPanel title="风险提示">
          <QuietEmpty text="暂无风险提示" />
        </DataPanel>
      </aside>
    </div>
  </div>
</template>
```

## Template 3: Create Or Edit Form Page

Use for new project, new contract, new variation, settlement application, payment application, and purchase application.

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'

const submitting = ref(false)
const formRef = ref()

const form = reactive({
  projectId: undefined as string | undefined,
  name: '',
  code: '',
  partnerId: undefined as string | undefined,
  amount: undefined as number | undefined,
  signDate: '',
  remark: '',
})

const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  name: [{ required: true, message: '请输入名称' }],
  amount: [{ required: true, message: '请输入金额' }],
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    // Call existing create/update API.
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="app-page form-page">
    <PageHeader :breadcrumb="['合同管理', '新建合同']" title="新建合同">
      <a-space>
        <a-button>取消</a-button>
        <a-button type="primary" :loading="submitting" @click="handleSubmit">提交</a-button>
      </a-space>
    </PageHeader>

    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
      <DataPanel title="基础信息">
        <div class="form-grid">
          <a-form-item label="项目名称" name="projectId">
            <a-select v-model:value="form.projectId" placeholder="请选择项目" />
          </a-form-item>
          <a-form-item label="编号" name="code">
            <a-input v-model:value="form.code" placeholder="系统自动生成或手动输入" />
          </a-form-item>
          <a-form-item label="名称" name="name" class="span-2">
            <a-input v-model:value="form.name" placeholder="请输入名称" />
          </a-form-item>
        </div>
      </DataPanel>

      <DataPanel title="金额与周期">
        <div class="form-grid">
          <a-form-item label="金额(元)" name="amount">
            <a-input-number v-model:value="form.amount" :min="0" style="width: 100%" />
          </a-form-item>
          <a-form-item label="签订日期" name="signDate">
            <a-date-picker v-model:value="form.signDate" style="width: 100%" />
          </a-form-item>
        </div>
      </DataPanel>

      <DataPanel title="备注">
        <a-textarea v-model:value="form.remark" :rows="4" placeholder="请输入备注" />
      </DataPanel>
    </a-form>
  </div>
</template>
```

## Template 4: Analysis Dashboard Page

Use for cost analysis, funding analysis, management overview, and role-specific dashboards.

```vue
<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'

const kpiItems = [
  { label: '目标成本', value: '86,502.35', unit: '万元', tone: 'blue' },
  { label: '动态成本', value: '76,420.18', unit: '万元', tone: 'orange' },
  { label: '成本偏差', value: '3,215.40', unit: '万元', tone: 'red' },
  { label: '预计利润', value: '6,875.45', unit: '万元', tone: 'green' },
]

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 44, right: 20, top: 36, bottom: 30 },
  xAxis: { type: 'category', data: ['1月', '2月', '3月', '4月', '5月', '6月'] },
  yAxis: { type: 'value', name: '万元' },
  series: [
    { name: '目标成本', type: 'bar', data: [82000, 76000, 72000, 78000, 81000, 89000] },
    { name: '动态成本', type: 'line', smooth: true, data: [68000, 70000, 69000, 73000, 76000, 79000] },
  ],
}))
</script>

<template>
  <div class="app-page analysis-page">
    <PageHeader :breadcrumb="['成本管理', '成本分析']" title="成本分析" />

    <KpiStrip :items="kpiItems" />

    <div class="analysis-grid">
      <DataPanel title="成本执行概览">
        <VChart :option="trendOption" autoresize class="analysis-chart" />
      </DataPanel>
      <DataPanel title="成本构成分析">
        <VChart :option="trendOption" autoresize class="analysis-chart" />
      </DataPanel>
      <DataPanel title="偏差趋势分析">
        <VChart :option="trendOption" autoresize class="analysis-chart" />
      </DataPanel>
    </div>

    <div class="analysis-table-grid">
      <DataPanel title="超预算预警">
        <a-table size="small" :pagination="false" />
      </DataPanel>
      <DataPanel title="成本科目排行">
        <a-table size="small" :pagination="false" />
      </DataPanel>
      <DataPanel title="异常明细">
        <a-table size="small" :pagination="false" />
      </DataPanel>
    </div>
  </div>
</template>
```

## Template 5: Tree And Table Management Page

Use for organization, dictionary, cost subject, material category, and permission configuration pages.

```vue
<script setup lang="ts">
import { ref } from 'vue'

const selectedTreeKey = ref<string>()
const treeData = ref([])
const rows = ref([])

const columns = [
  { title: '名称', dataIndex: 'name', ellipsis: true },
  { title: '编码', dataIndex: 'code', width: 140 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
  { title: '操作', dataIndex: 'actions', width: 140 },
]
</script>

<template>
  <div class="app-page tree-table-page">
    <PageHeader :breadcrumb="['基础数据', '字典管理']" title="字典管理">
      <a-button type="primary">新增字典</a-button>
    </PageHeader>

    <div class="tree-table-layout">
      <DataPanel title="分类">
        <a-input-search placeholder="搜索分类" style="margin-bottom: 12px" />
        <a-tree
          v-model:selectedKeys="selectedTreeKey"
          :tree-data="treeData"
          block-node
        />
      </DataPanel>

      <DataPanel title="明细">
        <ActionToolbar>
          <a-button type="primary">新增</a-button>
          <a-button>刷新</a-button>
        </ActionToolbar>
        <a-table
          :columns="columns"
          :data-source="rows"
          size="small"
          row-key="id"
        />
      </DataPanel>
    </div>
  </div>
</template>
```

## Module Example Mapping

### Project Management

Use `Ledger List Page` for project list.

KPI examples:

- `在建项目`
- `已完工项目`
- `风险项目`
- `合同总额`

Right rail examples:

- `项目状态分布`
- `区域项目分布`
- `风险项目排行`

### Target Management

Use `Tree And Table Management Page` plus `Analysis Dashboard Page`.

Main structure:

- left cost subject tree;
- right target cost table;
- top KPI: `目标总额`, `已锁定成本`, `动态成本`, `偏差金额`;
- analysis rail: `目标占比`, `偏差预警`.

### Cost Management

Use `Analysis Dashboard Page`.

Panels:

- `成本执行概览`
- `成本构成分析`
- `偏差趋势分析`
- `超预算预警`
- `成本科目排行`
- `异常明细`

### Variation And Visa

Use `Ledger List Page` and `Detail Page`.

KPI examples:

- `变更总额`
- `已审批金额`
- `待审批数量`
- `影响利润`

Right rail examples:

- `变更类型分布`
- `审批状态统计`
- `金额 Top5`

### Settlement Management

Use `Ledger List Page`.

KPI examples:

- `累计结算金额`
- `待审核金额`
- `已确认金额`
- `结算进度`

Right rail examples:

- `结算状态分布`
- `月度结算趋势`
- `异常结算提醒`

### Payment Management

Use `Ledger List Page`.

KPI examples:

- `待付款金额`
- `已审批未支付`
- `今日应付`
- `超比例付款`

Right rail examples:

- `付款状态统计`
- `资金风险`
- `临期付款`

### Subcontract Management

Use `Ledger List Page`.

KPI examples:

- `分包合同额`
- `已计量`
- `已付款`
- `待结算`

Right rail examples:

- `分包类型分布`
- `单位排行`
- `异常提醒`

### Procurement Management

Use `Ledger List Page`.

KPI examples:

- `采购申请数`
- `待审批`
- `已下单金额`
- `未入库金额`

Right rail examples:

- `采购状态`
- `供应商排行`
- `异常采购`

### Inventory Management

Use `Ledger List Page`.

KPI examples:

- `库存总值`
- `低库存物料`
- `近期待入库`
- `近期待出库`

Right rail examples:

- `物料类别分布`
- `低库存预警`
- `库存金额 Top5`

### Invoice Management

Use `Ledger List Page`.

KPI examples:

- `发票总额`
- `已开票`
- `未开票`
- `异常发票`

Right rail examples:

- `发票状态分布`
- `税率分布`
- `异常提醒`

### Approval Management

Use `Ledger List Page` plus a workflow-oriented `Detail Page`.

Tabs:

- `待办`
- `已办`
- `我发起`
- `抄送我`

KPI examples:

- `待办数量`
- `超时审批`
- `今日新增`
- `已处理`

### Alert Center

Use `Ledger List Page`.

KPI examples:

- `高风险`
- `中风险`
- `低风险`
- `已处理`

Right rail examples:

- `风险等级分布`
- `模块分布`
- `未处理排行`

### Basic Data, Organization, System Settings

Use `Tree And Table Management Page` or compact setting panels.

Avoid heavy charts. Prioritize:

- stable navigation;
- compact tables;
- clear switches and forms;
- drawer-based editing;
- permission grouping.

## Suggested Task Split For Agents

### Task 014: Project And Target Pages

```text
D:\projects-test\cgc-pms\.agent-runtime\tasks\task-014-project-target-ui-redesign.md
请按该任务文档重设计项目管理和目标管理页面，结果写入 implementation report。
```

### Task 015: Cost Pages

```text
D:\projects-test\cgc-pms\.agent-runtime\tasks\task-015-cost-ui-redesign.md
请按该任务文档重设计成本管理页面，结果写入 implementation report。
```

### Task 016: Variation, Settlement, Payment Pages

```text
D:\projects-test\cgc-pms\.agent-runtime\tasks\task-016-commercial-flow-ui-redesign.md
请按该任务文档重设计变更签证、结算管理、付款管理页面，结果写入 implementation report。
```

### Task 017: Subcontract, Procurement, Inventory, Invoice Pages

```text
D:\projects-test\cgc-pms\.agent-runtime\tasks\task-017-execution-support-ui-redesign.md
请按该任务文档重设计分包、采购、库存、发票页面，结果写入 implementation report。
```

### Task 018: Approval, Alert, Foundation Pages

```text
D:\projects-test\cgc-pms\.agent-runtime\tasks\task-018-workflow-foundation-ui-redesign.md
请按该任务文档重设计审批、预警、基础数据、组织架构、系统设置页面，结果写入 implementation report。
```

## Verification Guidance

Each future implementation task should include:

```powershell
cd frontend-admin
pnpm build
```

Add focused tests for source-level page markers when practical:

- required titles;
- required KPI labels;
- required analysis rail labels;
- no reintroduction of old labels;
- responsive shell test when layout changes can affect overflow.

Browser verification should cover:

- `1440x900`;
- around `937x900`;
- `390x844`;
- no horizontal overflow;
- no console error;
- no blank page;
- primary table/action/filter still visible.

