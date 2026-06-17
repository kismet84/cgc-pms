<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from "vue";
import { useRouter } from "vue-router";
import { useReferenceStore } from "@/stores/reference";
import { storeToRefs } from "pinia";
import { PlusOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons-vue";
import { message, Modal } from "ant-design-vue";
import { getSettlementList, deleteSettlement, getSettlementKpi, computeSettlementAmount, createSettlement } from "@/api/modules/settlement";
import type { SettlementVO, SettlementQueryParams, SettlementKpiVO, SettlementStatus } from "@/types/settlement";
import { SETTLEMENT_STATUS_LABEL, SETTLEMENT_STATUS_COLOR } from "@/types/settlement";
import type { PageResult } from "@/types/api";

const router = useRouter();
const referenceStore = useReferenceStore();
const { projects, contracts, partners } = storeToRefs(referenceStore);

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  settlementStatus: undefined as SettlementStatus | undefined,
  settlementCode: "",
  settlementType: undefined as string | undefined,
});

const loading = ref(false);
const tableData = ref<SettlementVO[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);

const kpi = ref<SettlementKpiVO>({ totalCount:0, totalContractAmount:"0", totalFinalAmount:"0", totalChangeAmount:"0", totalPaidAmount:"0", totalUnpaidAmount:"0", draftCount:0, finalizedCount:0 });

const createModalVisible = ref(false);
const createLoading = ref(false);
const createForm = reactive({ contractId: undefined as string | undefined, settlementType: undefined as string | undefined, remark: "" });
const createFormPartnerName = computed(() => contracts.value?.find(c => c.id === createForm.contractId)?.partyBName ?? "");
watch(() => createForm.contractId, (val) => { if (!val) createFormPartnerName; });

function onProjectChange(val: string | undefined) { filter.contractId = undefined; if (val) referenceStore.fetchContracts({ projectId: val }); }

async function fetchData() {
  loading.value = true;
  const params: SettlementQueryParams = { projectId: filter.projectId, contractId: filter.contractId, partnerId: filter.partnerId, settlementStatus: filter.settlementStatus, settlementCode: filter.settlementCode || undefined, settlementType: filter.settlementType, pageNo: pageNo.value, pageSize: pageSize.value };
  try { const res: PageResult<SettlementVO> = await getSettlementList(params); tableData.value = res.records; total.value = res.total; }
  catch (e: unknown) { console.error(e); tableData.value = []; total.value = 0; message.error("加载结算列表失败"); }
  finally { loading.value = false; }
}

async function fetchKpi() { try { kpi.value = await getSettlementKpi(); } catch { kpi.value = { totalCount:0, totalContractAmount:"0", totalFinalAmount:"0", totalChangeAmount:"0", totalPaidAmount:"0", totalUnpaidAmount:"0", draftCount:0, finalizedCount:0 }; } }

function handleSearch() { pageNo.value = 1; fetchData(); fetchKpi(); }
function handleReset() { filter.projectId = undefined; filter.contractId = undefined; filter.partnerId = undefined; filter.settlementStatus = undefined; filter.settlementCode = ""; filter.settlementType = undefined; pageNo.value = 1; fetchData(); fetchKpi(); }
function handlePageChange(page: number) { pageNo.value = page; fetchData(); }
function handlePageSizeChange(_cur: number, size: number) { pageSize.value = size; pageNo.value = 1; fetchData(); }
function handleView(row: SettlementVO) { router.push(`/settlement/${row.id}`); }

async function handleDelete(row: SettlementVO) {
  if (row.settlementStatus === "FINALIZED") { message.warning("已定案的结算单不可删除"); return; }
  Modal.confirm({ title: "确认删除", content: `确定删除结算单 ${row.settlementCode}？`, okType: "danger", onOk: async () => { await deleteSettlement(row.id); message.success("已删除"); fetchData(); fetchKpi(); } });
}

function openCreateModal() { createForm.contractId = undefined; createForm.settlementType = undefined; createForm.remark = ""; createModalVisible.value = true; }
async function handleCreate() { createLoading.value = true; try { await createSettlement(createForm); message.success("创建成功"); createModalVisible.value = false; fetchData(); fetchKpi(); } catch (e) { console.error(e); } finally { createLoading.value = false; } }

function fmtWan(val: string | undefined): string { if (!val) return "0.00"; const n = parseFloat(val); return isNaN(n) ? "0.00" : (n / 10000).toLocaleString("zh-CN", { minimumFractionDigits:2, maximumFractionDigits:2 }); }

// ---- Computed KPI ----
const progressPct = computed(() => { const t = kpi.value.draftCount + kpi.value.finalizedCount; return t > 0 ? ((kpi.value.finalizedCount / t) * 100).toFixed(0) + "%" : "0%"; });

// ---- Analysis rail ----
const statusBreakdown = computed(() => { const m: Record<string, number> = {}; tableData.value.forEach(r => { m[r.settlementStatus] = (m[r.settlementStatus] || 0) + 1; }); return Object.entries(m).map(([k,v]) => ({ label: SETTLEMENT_STATUS_LABEL[k as SettlementStatus] ?? k, count: v })); });

onMounted(() => { referenceStore.fetchProjects(); referenceStore.fetchContracts({}); referenceStore.fetchPartners(); fetchData(); fetchKpi(); });
</script>

<template>
  <div class="project-target-redesign app-page">
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb"><a-breadcrumb-item>结算管理</a-breadcrumb-item><a-breadcrumb-item>结算列表</a-breadcrumb-item></a-breadcrumb>
      <h1 class="app-page-title">结算列表</h1>
      <div class="pt-head-actions">
        <a-button type="primary" @click="openCreateModal"><PlusOutlined />新建结算</a-button>
        <a-button @click="handleSearch"><ReloadOutlined />刷新</a-button>
      </div>
    </div>

    <div class="pt-kpi-strip" style="grid-template-columns:repeat(4,1fr)">
      <div class="pt-kpi"><div class="pt-kpi-label">累计结算金额</div><div class="pt-kpi-value">{{ fmtWan(kpi.totalFinalAmount) }}<small>万元</small></div></div>
      <div class="pt-kpi"><div class="pt-kpi-label">待审核金额</div><div class="pt-kpi-value">{{ fmtWan(kpi.totalContractAmount) }}<small>万元</small></div></div>
      <div class="pt-kpi"><div class="pt-kpi-label">已确认金额</div><div class="pt-kpi-value">{{ fmtWan(kpi.totalPaidAmount) }}<small>万元</small></div></div>
      <div class="pt-kpi"><div class="pt-kpi-label">结算进度</div><div class="pt-kpi-value">{{ progressPct }}<small>已定案</small></div></div>
    </div>

    <div class="pt-panel pt-filter-surface">
      <div class="pt-filter-row">
        <div class="pt-field"><label>项目：</label><a-select v-model:value="filter.projectId" placeholder="全部" allow-clear style="width:160px" @change="onProjectChange"><a-select-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.projectName }}</a-select-option></a-select></div>
        <div class="pt-field"><label>合同：</label><a-select v-model:value="filter.contractId" placeholder="全部" allow-clear style="width:160px"><a-select-option v-for="c in contracts" :key="c.id" :value="c.id">{{ c.contractName }}</a-select-option></a-select></div>
        <div class="pt-field"><label>状态：</label><a-select v-model:value="filter.settlementStatus" placeholder="全部" allow-clear style="width:120px"><a-select-option v-for="(label,key) in SETTLEMENT_STATUS_LABEL" :key="key" :value="key">{{ label }}</a-select-option></a-select></div>
        <div class="pt-field"><label>编号：</label><a-input v-model:value="filter.settlementCode" placeholder="结算编号" allow-clear style="width:150px" @press-enter="handleSearch" /></div>
        <div class="pt-filter-actions"><a-button type="primary" size="small" @click="handleSearch"><SearchOutlined /></a-button><a-button size="small" @click="handleReset"><ReloadOutlined /></a-button></div>
      </div>
    </div>

    <div class="pt-ledger-layout">
      <main class="pt-panel pt-table-panel">
        <div class="pt-panel-header">结算清单</div>
        <a-table :columns="[{title:'结算编号',dataIndex:'settlementCode',width:160},{title:'项目',dataIndex:'projectName',width:140},{title:'合同',dataIndex:'contractName',width:140},{title:'结算金额(万)',dataIndex:'settlementAmount',width:140,key:'settlementAmount'},{title:'状态',dataIndex:'settlementStatus',width:100,key:'settlementStatus'},{title:'创建时间',dataIndex:'createdAt',width:160},{title:'操作',key:'ops',width:120}]" :data-source="tableData" :loading="loading" :pagination="false" row-key="id" size="small" :scroll="{x:960}">
          <template #bodyCell="{column,record}">
            <template v-if="column.key === 'settlementAmount'"><span>{{ fmtWan(record.settlementAmount) }}</span></template>
            <template v-else-if="column.key === 'settlementStatus'"><a-tag :color="SETTLEMENT_STATUS_COLOR[record.settlementStatus as SettlementStatus]||'default'" size="small">{{ SETTLEMENT_STATUS_LABEL[record.settlementStatus as SettlementStatus]??record.settlementStatus }}</a-tag></template>
            <template v-else-if="column.key === 'ops'"><a class="pt-link" @click="handleView(record)">查看</a><a v-if="record.settlementStatus !== 'FINALIZED'" class="pt-link pt-danger" style="margin-left:10px" @click="handleDelete(record)">删除</a></template>
          </template>
        </a-table>
        <a-empty v-if="!loading && tableData.length===0" description="暂无结算记录" style="padding:48px 0" />
        <div class="pt-pagination"><span class="pt-total">共 {{ total }} 条</span><a-pagination v-model:current="pageNo" v-model:page-size="pageSize" :total="total" :page-size-options="['10','20','50']" show-size-changer show-quick-jumper @change="handlePageChange" @showSizeChange="handlePageSizeChange" /></div>
      </main>

      <aside class="pt-analysis-rail">
        <section class="pt-panel"><div class="pt-panel-header">结算状态分布</div><div class="pt-panel-body"><ul class="pt-compact-list"><li v-for="it in statusBreakdown" :key="it.label" class="pt-compact-row"><span>{{ it.label }}</span><b>{{ it.count }} 条</b></li></ul></div></section>
        <section class="pt-panel"><div class="pt-panel-header">月度结算趋势</div><div class="pt-panel-body"><ul class="pt-compact-list"><li class="pt-compact-row"><span>草稿</span><b>{{ kpi.draftCount }} 条</b></li><li class="pt-compact-row"><span>已定案</span><b>{{ kpi.finalizedCount }} 条</b></li></ul></div></section>
        <section class="pt-panel"><div class="pt-panel-header">异常结算提醒</div><div class="pt-panel-body"><ul class="pt-compact-list"><li class="pt-compact-row"><span>未付金额</span><b style="color:#ef4444">{{ fmtWan(kpi.totalUnpaidAmount) }} 万</b></li></ul></div></section>
      </aside>
    </div>

    <a-modal v-model:open="createModalVisible" title="新建结算单" :confirm-loading="createLoading" @ok="handleCreate">
      <a-form layout="vertical">
        <a-form-item label="关联合同" required><a-select v-model:value="createForm.contractId" placeholder="请选择合同" style="width:100%" show-search option-filter-prop="label"><a-select-option v-for="c in contracts" :key="c.id" :value="c.id" :label="c.contractName">{{ c.contractName }}</a-select-option></a-select></a-form-item>
        <a-form-item label="合作方"><a-input :value="createFormPartnerName" disabled placeholder="选择合同后自动填充乙方" /></a-form-item>
        <a-form-item label="结算类型"><a-select v-model:value="createForm.settlementType" placeholder="请选择" allow-clear style="width:100%"><a-select-option value="PROGRESS">进度结算</a-select-option><a-select-option value="FINAL">竣工结算</a-select-option><a-select-option value="INTERIM">期中结算</a-select-option></a-select></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="createForm.remark" placeholder="备注（选填）" :rows="3" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped></style>
