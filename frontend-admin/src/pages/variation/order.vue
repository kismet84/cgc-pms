<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from "vue";
import { storeToRefs } from "pinia";
import { message, Modal } from "ant-design-vue";
import {
  PlusOutlined, ReloadOutlined, SearchOutlined, DollarOutlined,
  CheckCircleOutlined, ClockCircleOutlined, WarningOutlined,
} from "@ant-design/icons-vue";
import {
  getVarOrderList, createVarOrder, updateVarOrder, deleteVarOrder,
  getVarOrderDetail, saveVarOrderItems, submitVarOrderForApproval,
} from "@/api/modules/variation";
import { useReferenceStore } from "@/stores/reference";
import type { VarOrderVO, VarOrderItemVO } from "@/types/variation";

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  varType: undefined as string | undefined,
  direction: undefined as string | undefined,
  varCode: "",
});

const loading = ref(false);
const tableData = ref<VarOrderVO[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);

const referenceStore = useReferenceStore();
const { projects: projectList, contracts: contractList, partners: partnerList } = storeToRefs(referenceStore);

const modalVisible = ref(false);
const modalTitle = ref("新建变更签证");
const editingId = ref<string | null>(null);
const formData = reactive<Partial<VarOrderVO>>({
  projectId: undefined, contractId: undefined, partnerId: undefined,
  varType: undefined, varName: "", direction: "COST", impactDays: 0,
  ownerConfirmFlag: 0, remark: "",
});
const formPartnerName = computed(() => contractList.value?.find(c => c.id === formData.contractId)?.partyBName ?? "");
function onContractChange(contractId: string) { const c = contractList.value?.find(ct => ct.id === contractId); formData.partnerId = c?.partyBId; }
watch(() => formData.contractId, (val) => { if (!val) formData.partnerId = undefined; });

const itemList = ref<(Partial<VarOrderItemVO> & { key: number })[]>([]);
let itemKeyCounter = 0;

const VAR_TYPE_OPTIONS = [
  { label: "设计变更", value: "设计变更" }, { label: "现场签证", value: "现场签证" },
  { label: "索赔", value: "索赔" }, { label: "洽商", value: "洽商" },
];

const DIRECTION_OPTIONS = [
  { label: "成本", value: "COST" }, { label: "收入", value: "REVENUE", disabled: true },
];

const VAR_TYPE_LABEL: Record<string, string> = { 设计变更: "设计变更", 现场签证: "现场签证", 索赔: "索赔", 洽商: "洽商" };

const columns = [
  { title: "变更编号", dataIndex: "varCode", width: 160 },
  { title: "变更名称", dataIndex: "varName", width: 160 },
  { title: "变更类型", dataIndex: "varType", width: 100, key: "varType" },
  { title: "方向", dataIndex: "direction", width: 80, key: "direction" },
  { title: "项目名称", dataIndex: "projectName", width: 140 },
  { title: "合同名称", dataIndex: "contractName", width: 140 },
  { title: "合作方", dataIndex: "partnerName", width: 140 },
  { title: "上报金额", dataIndex: "reportedAmount", width: 120, key: "reportedAmount" },
  { title: "审定金额", dataIndex: "approvedAmount", width: 120, key: "approvedAmount" },
  { title: "确认金额", dataIndex: "confirmedAmount", width: 120, key: "confirmedAmount" },
  { title: "审批状态", dataIndex: "approvalStatus", width: 100, key: "approvalStatus" },
  { title: "操作", key: "action", width: 140, fixed: "right" },
];

async function fetchData() {
  loading.value = true;
  try {
    const res = await getVarOrderList({ pageNo: pageNo.value, pageSize: pageSize.value, projectId: filter.projectId, contractId: filter.contractId, partnerId: filter.partnerId, varType: filter.varType, direction: filter.direction, varCode: filter.varCode || undefined });
    tableData.value = res.records; total.value = res.total;
  } catch (e: unknown) { console.error(e); tableData.value = []; total.value = 0; message.error("加载变更签证列表失败"); }
  finally { loading.value = false; }
}

function handleSearch() { pageNo.value = 1; fetchData(); }
function handleReset() { filter.projectId = undefined; filter.contractId = undefined; filter.partnerId = undefined; filter.varType = undefined; filter.direction = undefined; filter.varCode = ""; pageNo.value = 1; fetchData(); }
function handlePageChange(page: number) { pageNo.value = page; fetchData(); }
function handlePageSizeChange(_cur: number, size: number) { pageSize.value = size; pageNo.value = 1; fetchData(); }

function handleAdd() {
  modalTitle.value = "新建变更签证"; editingId.value = null;
  Object.assign(formData, { projectId: undefined, contractId: undefined, partnerId: undefined, varType: undefined, varName: "", direction: "COST", impactDays: 0, ownerConfirmFlag: 0, remark: "" });
  itemList.value = []; itemKeyCounter = 0; modalVisible.value = true;
}

async function handleEdit(record: VarOrderVO) {
  modalTitle.value = "编辑变更签证"; editingId.value = record.id;
  Object.assign(formData, { projectId: record.projectId, contractId: record.contractId, partnerId: record.partnerId, varType: record.varType, varName: record.varName, direction: record.direction, impactDays: record.impactDays ?? 0, ownerConfirmFlag: record.ownerConfirmFlag ?? 0, remark: record.remark ?? "" });
  try { const detail = await getVarOrderDetail(record.id); itemList.value = (detail.items ?? []).map((it, idx) => ({ ...it, key: idx })); itemKeyCounter = itemList.value.length; }
  catch { itemList.value = []; itemKeyCounter = 0; }
  modalVisible.value = true;
}

async function handleSubmitApproval(record: VarOrderVO) {
  Modal.confirm({
    title: '提交审批',
    content: `确认提交变更签证 ${record.varCode}？`,
    onOk: async () => {
      await submitVarOrderForApproval(record.id);
      message.success('已提交审批');
      fetchData();
    },
  });
}
async function handleDelete(record: VarOrderVO) {
  Modal.confirm({ title: "确认删除", content: `确定删除变更签证 ${record.varCode}？`, okType: "danger", onOk: async () => { await deleteVarOrder(record.id); message.success("已删除"); fetchData(); } });
}

async function handleSubmit() {
  const id = editingId.value;
  try {
    if (id) { await updateVarOrder(id, formData); await saveVarOrderItems(id, itemList.value); message.success("更新成功"); }
    else { const res = await createVarOrder(formData); await saveVarOrderItems(res.id, itemList.value); message.success("创建成功"); }
    modalVisible.value = false; fetchData();
  } catch (e: unknown) { console.error(e); }
}

function handleAddItem() { itemList.value.push({ key: itemKeyCounter++, itemName: "", unit: "", quantity: 0, unitPrice: 0, amount: 0 }); }
function handleRemoveItem(idx: number) { itemList.value.splice(idx, 1); }
function handleItemQtyChange(idx: number) { const item = itemList.value[idx]; item.amount = (item.quantity ?? 0) * (item.unitPrice ?? 0); }
function handleItemPriceChange(idx: number) { const item = itemList.value[idx]; item.amount = (item.quantity ?? 0) * (item.unitPrice ?? 0); }

const itemsTotalAmount = computed(() => itemList.value.reduce((sum, i) => sum + (i.amount ?? 0), 0));

function fmtWan(val: string | undefined): string { if (!val) return "0.00"; const n = parseFloat(val); return isNaN(n) ? "0.00" : (n / 10000).toFixed(2); }

// ---- KPI ----
const kpiTotalVar = computed(() => tableData.value.reduce((s, r) => s + (parseFloat(r.reportedAmount) || 0), 0));
const kpiApproved = computed(() => tableData.value.filter(r => r.approvalStatus === "APPROVED").reduce((s, r) => s + (parseFloat(r.approvedAmount) || 0), 0));
const kpiPending = computed(() => tableData.value.filter(r => r.approvalStatus === "DRAFT" || r.approvalStatus === "APPROVING").length);

// ---- Analysis rail ----
const varTypeBreakdown = computed(() => { const m: Record<string, number> = {}; tableData.value.forEach(r => { m[r.varType] = (m[r.varType] || 0) + 1; }); return Object.entries(m).map(([k, v]) => ({ label: VAR_TYPE_LABEL[k] ?? k, count: v })); });
const statusBreakdown = computed(() => { const m: Record<string, number> = {}; tableData.value.forEach(r => { m[r.approvalStatus] = (m[r.approvalStatus] || 0) + 1; }); return Object.entries(m).map(([k, v]) => ({ label: k, count: v })); });
const topAmount = computed(() => [...tableData.value].sort((a, b) => (parseFloat(b.reportedAmount) || 0) - (parseFloat(a.reportedAmount) || 0)).slice(0, 5));

onMounted(() => { referenceStore.fetchProjects(); referenceStore.fetchContracts({}); referenceStore.fetchPartners(); fetchData(); });
</script>

<template>
  <div class="project-target-redesign app-page">
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb"><a-breadcrumb-item>变更签证</a-breadcrumb-item></a-breadcrumb>
      <div class="pt-head-actions">
        <a-button type="primary" @click="handleAdd"><PlusOutlined />新建</a-button>
        <a-button @click="handleSearch"><ReloadOutlined />刷新</a-button>
      </div>
    </div>

    <div class="pt-kpi-strip">
      <div class="pt-kpi"><div class="pt-kpi-label">变更总额</div><div class="pt-kpi-value">{{ kpiTotalVar.toLocaleString() }}<small>元</small></div></div>
      <div class="pt-kpi"><div class="pt-kpi-label">已审批金额</div><div class="pt-kpi-value">{{ kpiApproved.toLocaleString() }}<small>元</small></div></div>
      <div class="pt-kpi"><div class="pt-kpi-label">待审批数量</div><div class="pt-kpi-value">{{ kpiPending }}<small>条</small></div></div>
      <div class="pt-kpi"><div class="pt-kpi-label">影响利润</div><div class="pt-kpi-value" style="color: #ef4444">{{ (kpiTotalVar - kpiApproved).toLocaleString() }}<small>元</small></div></div>
    </div>

    <div class="pt-panel pt-filter-surface">
      <div class="pt-filter-row">
        <div class="pt-field"><label>项目：</label><a-select v-model:value="filter.projectId" placeholder="全部" allow-clear style="width:160px" @change="(v: string|undefined) => { if(v) referenceStore.fetchContracts({projectId:v}) }"><a-select-option v-for="p in projectList" :key="p.id" :value="p.id">{{ p.projectName }}</a-select-option></a-select></div>
        <div class="pt-field"><label>合同：</label><a-select v-model:value="filter.contractId" placeholder="全部" allow-clear style="width:160px"><a-select-option v-for="c in contractList" :key="c.id" :value="c.id">{{ c.contractName }}</a-select-option></a-select></div>
        <div class="pt-field"><label>类型：</label><a-select v-model:value="filter.varType" placeholder="全部" allow-clear style="width:120px"><a-select-option v-for="o in VAR_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</a-select-option></a-select></div>
        <div class="pt-field"><label>编号：</label><a-input v-model:value="filter.varCode" placeholder="变更编号" allow-clear style="width:150px" @press-enter="handleSearch" /></div>
        <div class="pt-filter-actions"><a-button type="primary" size="small" @click="handleSearch"><SearchOutlined /></a-button><a-button size="small" @click="handleReset"><ReloadOutlined /></a-button></div>
      </div>
    </div>

    <div class="pt-ledger-layout">
      <main class="pt-panel pt-table-panel">
        <div class="pt-panel-header">变更签证清单</div>
        <a-table :columns="columns" :data-source="tableData" :loading="loading" :pagination="false" row-key="id" size="small" :scroll="{ x: 1500 }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'varType'"><a-tag size="small">{{ VAR_TYPE_LABEL[record.varType] ?? record.varType }}</a-tag></template>
            <template v-else-if="column.key === 'direction'"><a-tag :color="record.direction === 'COST' ? 'red' : 'green'" size="small">{{ record.direction === 'COST' ? '成本' : record.direction }}</a-tag></template>
            <template v-else-if="['reportedAmount','approvedAmount','confirmedAmount'].includes(column.key)"><span>{{ fmtWan(record[column.key]) }} 万</span></template>
            <template v-else-if="column.key === 'approvalStatus'"><a-tag :color="record.approvalStatus === 'APPROVED' ? 'success' : record.approvalStatus === 'REJECTED' ? 'error' : 'processing'" size="small">{{ record.approvalStatus }}</a-tag></template>
            <template v-else-if="column.key === 'action'"><a v-if="record.approvalStatus === 'DRAFT'" class="pt-link" @click="handleSubmitApproval(record)">提交审批</a><a class="pt-link" @click="handleEdit(record)">编辑</a><a class="pt-link pt-danger" style="margin-left:10px" @click="handleDelete(record)">删除</a></template>
          </template>
        </a-table>
        <a-empty v-if="!loading && tableData.length===0" description="暂无变更签证" style="padding:48px 0" />
        <div class="pt-pagination"><span class="pt-total">共 {{ total }} 条</span><a-pagination :current="pageNo" :total="total" :page-size="pageSize" :show-size-changer="true" :page-size-options="['10','20','50']" show-quick-jumper size="small" @change="handlePageChange" @showSizeChange="handlePageSizeChange" /></div>
      </main>

      <aside class="pt-analysis-rail">
        <section class="pt-panel"><div class="pt-panel-header">变更类型分布</div><div class="pt-panel-body"><ul class="pt-compact-list"><li v-for="it in varTypeBreakdown" :key="it.label" class="pt-compact-row"><span>{{ it.label }}</span><b>{{ it.count }} 条</b></li></ul></div></section>
        <section class="pt-panel"><div class="pt-panel-header">审批状态统计</div><div class="pt-panel-body"><ul class="pt-compact-list"><li v-for="it in statusBreakdown" :key="it.label" class="pt-compact-row"><span>{{ it.label }}</span><b>{{ it.count }} 条</b></li></ul></div></section>
        <section class="pt-panel"><div class="pt-panel-header">金额 Top5</div><div class="pt-panel-body"><ul class="pt-compact-list"><li v-for="it in topAmount" :key="it.id" class="pt-compact-row"><span>{{ it.varName }}</span><b>{{ fmtWan(it.reportedAmount) }} 万</b></li></ul></div></section>
      </aside>
    </div>

    <!-- Modal kept unchanged -->
    <a-modal v-model:open="modalVisible" :title="modalTitle" :width="860" @ok="handleSubmit">
      <a-form layout="vertical" :model="formData">
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="项目"><a-select v-model:value="formData.projectId" placeholder="请选择项目" style="width:100%" :options="(projectList??[]).map(p=>({value:p.id,label:p.projectName}))" @change="(v: string) => { formData.contractId = undefined; formData.partnerId = undefined; referenceStore.fetchContracts({ projectId: v }); }" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="合同"><a-select v-model:value="formData.contractId" placeholder="请选择合同" style="width:100%" :options="(contractList??[]).filter(c => !formData.projectId || c.projectId === formData.projectId).map(c=>({value:c.id,label:c.contractName}))" @change="onContractChange" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="合作方"><a-input :value="formPartnerName" disabled placeholder="选择合同后自动填充乙方" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="变更类型"><a-select v-model:value="formData.varType" placeholder="请选择" style="width:100%"><a-select-option v-for="o in VAR_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="变更名称"><a-input v-model:value="formData.varName" placeholder="变更名称" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="方向"><a-select v-model:value="formData.direction" placeholder="请选择"><a-select-option v-for="o in DIRECTION_OPTIONS" :key="o.value" :value="o.value" :disabled="o.disabled">{{ o.label }}</a-select-option></a-select></a-form-item></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="影响工期(天)"><a-input-number v-model:value="formData.impactDays" :min="0" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="业主确认"><a-switch :checked="formData.ownerConfirmFlag===1" @change="(v:boolean)=>formData.ownerConfirmFlag=v?1:0" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="备注"><a-textarea v-model:value="formData.remark" :rows="2" /></a-form-item></a-col>
        </a-row>
      </a-form>
      <div style="border-top:1px solid #f0f0f0;padding-top:12px;margin-top:4px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><span style="font-weight:600;font-size:14px">变更明细</span><a-button type="dashed" size="small" @click="handleAddItem">+ 添加明细</a-button></div>
        <a-table :data-source="itemList" :pagination="false" row-key="key" size="small" :scroll="{y:250}">
          <a-table-column title="清单项名称" width="160"><template #default="{record:item}"><a-input v-model:value="item.itemName" placeholder="名称" style="width:100%" /></template></a-table-column>
          <a-table-column title="单位" width="70"><template #default="{record:item}"><a-input v-model:value="item.unit" placeholder="单位" style="width:100%" /></template></a-table-column>
          <a-table-column title="数量" width="120"><template #default="{record:item,index}"><a-input-number v-model:value="item.quantity" :min="0" :precision="4" style="width:100%" @change="handleItemQtyChange(index)" /></template></a-table-column>
          <a-table-column title="单价(元)" width="130"><template #default="{record:item,index}"><a-input-number v-model:value="item.unitPrice" :min="0" :precision="4" style="width:100%" @change="handleItemPriceChange(index)" /></template></a-table-column>
          <a-table-column title="金额(元)" width="130"><template #default="{record:item}"><span>{{ Number(item.amount||0).toLocaleString("zh-CN",{minimumFractionDigits:2}) }}</span></template></a-table-column>
          <a-table-column title="操作" width="60"><template #default="{index}"><a-button type="link" size="small" danger @click="handleRemoveItem(index)">删除</a-button></template></a-table-column>
        </a-table>
        <div style="text-align:right;margin-top:8px;font-size:14px">合计：<span style="font-weight:600;color:#1677ff">¥{{ Number(itemsTotalAmount).toLocaleString("zh-CN",{minimumFractionDigits:2}) }}</span></div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped></style>


