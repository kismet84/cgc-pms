<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from "vue";
import { storeToRefs } from "pinia";
import { message, Modal } from "ant-design-vue";
import { PlusOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons-vue";
import { getApplicationList, createApplication, updateApplication, deleteApplication, getBasisList, saveBasis, submitForApproval, doWriteback } from "@/api/modules/payment";
import { useReferenceStore } from "@/stores/reference";
import { getReceiptList } from "@/api/modules/receipt";
import { getMeasureList } from "@/api/modules/subcontract";
import type { PayApplicationVO, PayApplicationBasisVO } from "@/types/payment";
import { PAY_TYPE_LABEL, PAY_TYPE_COLOR, PAY_STATUS_LABEL, PAY_STATUS_COLOR } from "@/types/payment";
import type { MatReceiptVO } from "@/types/receipt";
import type { SubMeasureVO } from "@/types/subcontract";

const filter = reactive({ projectId: undefined as string | undefined, contractId: undefined as string | undefined, partnerId: undefined as string | undefined, payType: undefined as string | undefined, payStatus: undefined as string | undefined, approvalStatus: undefined as string | undefined });

const loading = ref(false); const tableData = ref<PayApplicationVO[]>([]); const total = ref(0); const pageNo = ref(1); const pageSize = ref(20);
const referenceStore = useReferenceStore(); const { projects, contracts, partners } = storeToRefs(referenceStore);
const receiptList = ref<MatReceiptVO[]>([]); const measureList = ref<SubMeasureVO[]>([]);
const modalVisible = ref(false); const modalTitle = ref("新建付款申请"); const editingId = ref<string | null>(null);
const formData = reactive<Partial<PayApplicationVO>>({ projectId: undefined, contractId: undefined, partnerId: undefined, payType: undefined, applyAmount: undefined, applyReason: "" });
const formPartnerName = computed(() => contracts.value?.find(c => c.id === formData.contractId)?.partyBName ?? "");
function onContractChange(contractId: string) { const c = contracts.value?.find(ct => ct.id === contractId); formData.partnerId = c?.partyBId; }
watch(() => formData.contractId, (val) => { if (!val) formData.partnerId = undefined; });
const basisList = ref<(Partial<PayApplicationBasisVO> & { key: number })[]>([]); let basisKeyCounter = 0;
const writebackVisible = ref(false); const writebackTargetId = ref("");
const writebackForm = reactive({ payAmount: undefined as number | undefined, payDate: undefined as string | undefined, payMethod: "BANK_TRANSFER", voucherNo: "" });

const columns = [
  { title: "申请编号", dataIndex: "applyCode", width: 160 }, { title: "项目", dataIndex: "projectName", width: 140 },
  { title: "合同", dataIndex: "contractName", width: 140 }, { title: "合作方", dataIndex: "partnerName", width: 140 },
  { title: "申请金额", dataIndex: "applyAmount", width: 130, key: "applyAmount" }, { title: "审批金额", dataIndex: "approvedAmount", width: 130, key: "approvedAmount" },
  { title: "实付金额", dataIndex: "actualPayAmount", width: 130, key: "actualPayAmount" }, { title: "付款类型", dataIndex: "payType", width: 100, key: "payType" },
  { title: "支付状态", dataIndex: "payStatus", width: 100, key: "payStatus" }, { title: "审批状态", dataIndex: "approvalStatus", width: 100, key: "approvalStatus" },
  { title: "操作", key: "action", width: 220, fixed: "right" },
];

async function fetchData() {
  loading.value = true;
  try { const res = await getApplicationList({ pageNum: pageNo.value, pageSize: pageSize.value, projectId: filter.projectId, contractId: filter.contractId, partnerId: filter.partnerId, payType: filter.payType, payStatus: filter.payStatus, approvalStatus: filter.approvalStatus }); tableData.value = res.records; total.value = res.total; }
  catch (e) { console.error(e); tableData.value = []; total.value = 0; message.error("加载付款申请列表失败"); }
  finally { loading.value = false; }
}
async function fetchReceipts() { try { const res = await getReceiptList({ pageNum: 1, pageSize: 50 }); receiptList.value = res.records; } catch { receiptList.value = []; } }
async function fetchMeasures() { try { const res = await getMeasureList({ pageNum: 1, pageSize: 50 }); measureList.value = res.records; } catch { measureList.value = []; } }

function handleSearch() { pageNo.value = 1; fetchData(); }
function handleReset() { filter.projectId = undefined; filter.contractId = undefined; filter.partnerId = undefined; filter.payType = undefined; filter.payStatus = undefined; filter.approvalStatus = undefined; pageNo.value = 1; fetchData(); }
function handlePageChange(page: number) { pageNo.value = page; fetchData(); }
function handlePageSizeChange(_cur: number, size: number) { pageSize.value = size; pageNo.value = 1; fetchData(); }

function handleAdd() { modalTitle.value = "新建付款申请"; editingId.value = null; Object.assign(formData, { projectId: undefined, contractId: undefined, partnerId: undefined, payType: undefined, applyAmount: undefined, applyReason: "" }); basisList.value = []; basisKeyCounter = 0; modalVisible.value = true; }
async function handleEdit(record: PayApplicationVO) { modalTitle.value = "编辑付款申请"; editingId.value = record.id; Object.assign(formData, { projectId: record.projectId, contractId: record.contractId, partnerId: record.partnerId, payType: record.payType, applyAmount: record.applyAmount, applyReason: record.applyReason ?? "" }); try { const data = await getBasisList(record.id); basisList.value = data.map((it, idx) => ({ ...it, key: idx })); basisKeyCounter = basisList.value.length; } catch { basisList.value = []; } modalVisible.value = true; }
async function handleDelete(record: PayApplicationVO) { Modal.confirm({ title: "确认删除", content: `确定删除付款申请 ${record.applyCode}？`, okType: "danger", onOk: async () => { await deleteApplication(record.id); message.success("已删除"); fetchData(); } }); }

async function handleSubmit() {
  const id = editingId.value;
  try {
    if (id) { await updateApplication(id, formData); await saveBasis(id, basisList.value); message.success("更新成功"); }
    else { const res = await createApplication(formData); await saveBasis(res.id, basisList.value); message.success("创建成功"); }
    modalVisible.value = false; fetchData();
  } catch (e) { console.error(e); }
}

async function handleApproval(record: PayApplicationVO) { try { await submitForApproval(record.id); message.success("已提交审批"); fetchData(); } catch (e) { console.error(e); } }
function openWriteback(record: PayApplicationVO) { writebackTargetId.value = record.id; writebackForm.payAmount = undefined; writebackForm.payDate = undefined; writebackForm.payMethod = "BANK_TRANSFER"; writebackForm.voucherNo = ""; writebackVisible.value = true; }
async function handleWritebackOk() { try { await doWriteback(writebackTargetId.value, writebackForm); message.success("回写成功"); writebackVisible.value = false; fetchData(); } catch (e) { console.error(e); } }
function handleWritebackCancel() { writebackVisible.value = false; }

function handleAddBasis() { basisList.value.push({ key: basisKeyCounter++, sourceType: undefined, sourceId: undefined, amount: undefined }); }
function handleRemoveBasis(idx: number) { basisList.value.splice(idx, 1); }
function getSourceOptions(sourceType: string): { id: string; label: string }[] { if (sourceType === "RECEIPT") return receiptList.value.map(r => ({ id: r.id, label: r.receiptCode ?? r.id })); if (sourceType === "MEASURE") return measureList.value.map(m => ({ id: m.id, label: m.measureCode ?? m.id })); return []; }
function handleSourceChange(idx: number) { basisList.value[idx].sourceId = undefined; }

function fmtWan(val: string | undefined): string { if (!val) return "0.00"; const n = parseFloat(val); return isNaN(n) ? "0.00" : (n / 10000).toFixed(2); }
const kpiUnpaid = computed(() => tableData.value.filter(r => r.payStatus === "UNPAID" || r.payStatus === "PARTIAL").reduce((s, r) => s + (parseFloat(r.applyAmount) || 0), 0));
const kpiApprovedUnpaid = computed(() => tableData.value.filter(r => r.approvalStatus === "APPROVED" && (r.payStatus === "UNPAID" || r.payStatus === "PARTIAL")).reduce((s, r) => s + (parseFloat(r.approvedAmount) || 0), 0));

const statusBreakdown = computed(() => { const m: Record<string, number> = {}; tableData.value.forEach(r => { m[r.payStatus] = (m[r.payStatus] || 0) + 1; }); return Object.entries(m).map(([k, v]) => ({ label: PAY_STATUS_LABEL[k] ?? k, count: v })); });

onMounted(() => { referenceStore.fetchProjects(); referenceStore.fetchContracts({}); referenceStore.fetchPartners(); fetchData(); fetchReceipts(); fetchMeasures(); });
</script>

<template>
  <div class="project-target-redesign app-page">
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb"><a-breadcrumb-item>付款管理</a-breadcrumb-item><a-breadcrumb-item>付款申请</a-breadcrumb-item></a-breadcrumb>
      <h1 class="app-page-title">付款申请</h1>
      <div class="pt-head-actions">
        <a-button type="primary" @click="handleAdd"><PlusOutlined />新建申请</a-button>
        <a-button @click="handleSearch"><ReloadOutlined />刷新</a-button>
      </div>
    </div>

    <div class="pt-kpi-strip">
      <div class="pt-kpi"><div class="pt-kpi-label">待付款金额</div><div class="pt-kpi-value" style="color:#ef4444">{{ kpiUnpaid.toLocaleString() }}<small>元</small></div></div>
      <div class="pt-kpi"><div class="pt-kpi-label">已审批未支付</div><div class="pt-kpi-value">{{ kpiApprovedUnpaid.toLocaleString() }}<small>元</small></div></div>
      <div class="pt-kpi"><div class="pt-kpi-label">今日应付</div><div class="pt-kpi-value">-<small>元</small></div></div>
      <div class="pt-kpi"><div class="pt-kpi-label">超比例付款</div><div class="pt-kpi-value">0<small>条</small></div></div>
    </div>

    <div class="pt-panel pt-filter-surface">
      <div class="pt-filter-row">
        <div class="pt-field"><label>项目：</label><a-select v-model:value="filter.projectId" placeholder="全部" allow-clear style="width:160px" @change="(v: string|undefined) => { filter.contractId = undefined; if(v) referenceStore.fetchContracts({projectId:v}) }"><a-select-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.projectName }}</a-select-option></a-select></div>
        <div class="pt-field"><label>合同：</label><a-select v-model:value="filter.contractId" placeholder="全部" allow-clear style="width:160px"><a-select-option v-for="c in contracts" :key="c.id" :value="c.id">{{ c.contractName }}</a-select-option></a-select></div>
        <div class="pt-field"><label>付款类型：</label><a-select v-model:value="filter.payType" placeholder="全部" allow-clear style="width:120px"><a-select-option v-for="(label,key) in PAY_TYPE_LABEL" :key="key" :value="key">{{ label }}</a-select-option></a-select></div>
        <div class="pt-field"><label>状态：</label><a-select v-model:value="filter.payStatus" placeholder="全部" allow-clear style="width:120px"><a-select-option v-for="(label,key) in PAY_STATUS_LABEL" :key="key" :value="key">{{ label }}</a-select-option></a-select></div>
        <div class="pt-filter-actions"><a-button type="primary" size="small" @click="handleSearch"><SearchOutlined /></a-button><a-button size="small" @click="handleReset"><ReloadOutlined /></a-button></div>
      </div>
    </div>

    <div class="pt-ledger-layout">
      <main class="pt-panel pt-table-panel">
        <div class="pt-panel-header">付款申请清单</div>
        <a-table :columns="columns" :data-source="tableData" :loading="loading" :pagination="false" row-key="id" size="small" :scroll="{ x: 1500 }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'applyAmount'"><span>{{ fmtWan(record.applyAmount) }} 万</span></template>
            <template v-else-if="column.key === 'approvedAmount'"><span>{{ fmtWan(record.approvedAmount) }} 万</span></template>
            <template v-else-if="column.key === 'actualPayAmount'"><span>{{ fmtWan(record.actualPayAmount) }} 万</span></template>
            <template v-else-if="column.key === 'payType'"><a-tag :color="PAY_TYPE_COLOR[record.payType]||'default'" size="small">{{ PAY_TYPE_LABEL[record.payType]??record.payType }}</a-tag></template>
            <template v-else-if="column.key === 'payStatus'"><a-tag :color="PAY_STATUS_COLOR[record.payStatus]||'default'" size="small">{{ PAY_STATUS_LABEL[record.payStatus]??record.payStatus }}</a-tag></template>
            <template v-else-if="column.key === 'approvalStatus'"><a-tag :color="record.approvalStatus==='APPROVED'?'success':record.approvalStatus==='REJECTED'?'error':record.approvalStatus==='APPROVING'?'processing':'default'" size="small">{{ record.approvalStatus }}</a-tag></template>
            <template v-else-if="column.key === 'action'">
              <a class="pt-link" @click="handleEdit(record)">编辑</a>
              <a v-if="record.approvalStatus==='DRAFT'" class="pt-link" style="margin-left:8px" @click="handleApproval(record)">提交审批</a>
              <a v-if="record.approvalStatus==='APPROVED' && record.payStatus!=='PAID'" class="pt-link" style="margin-left:8px" @click="openWriteback(record)">付款回写</a>
              <a class="pt-link pt-danger" style="margin-left:8px" @click="handleDelete(record)">删除</a>
            </template>
          </template>
        </a-table>
        <a-empty v-if="!loading && tableData.length===0" description="暂无付款申请" style="padding:48px 0" />
        <div class="pt-pagination"><span class="pt-total">共 {{ total }} 条</span><a-pagination :current="pageNo" :total="total" :page-size="pageSize" :show-size-changer="true" :page-size-options="['10','20','50']" show-quick-jumper size="small" @change="handlePageChange" @showSizeChange="handlePageSizeChange" /></div>
      </main>

      <aside class="pt-analysis-rail">
        <section class="pt-panel"><div class="pt-panel-header">付款状态统计</div><div class="pt-panel-body"><ul class="pt-compact-list"><li v-for="it in statusBreakdown" :key="it.label" class="pt-compact-row"><span>{{ it.label }}</span><b>{{ it.count }} 条</b></li></ul></div></section>
        <section class="pt-panel"><div class="pt-panel-header">资金风险</div><div class="pt-panel-body"><ul class="pt-compact-list"><li class="pt-compact-row"><span>待付款总额</span><b style="color:#ef4444">{{ kpiUnpaid.toLocaleString() }} 元</b></li></ul></div></section>
        <section class="pt-panel"><div class="pt-panel-header">临期付款</div><div class="pt-panel-body"><ul class="pt-compact-list"><li class="pt-compact-row"><span>暂无临期付款</span></li></ul></div></section>
      </aside>
    </div>

    <!-- Create/Edit Modal (unchanged structure) -->
    <a-modal v-model:open="modalVisible" :title="modalTitle" :width="760" @ok="handleSubmit">
      <a-form layout="vertical" :model="formData">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="项目"><a-select v-model:value="formData.projectId" placeholder="请选择项目" style="width:100%" :options="(projects??[]).map(p=>({value:p.id,label:p.projectName}))" @change="(v: string) => { formData.contractId = undefined; formData.partnerId = undefined; referenceStore.fetchContracts({ projectId: v }); }" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="合同"><a-select v-model:value="formData.contractId" placeholder="请选择合同" style="width:100%" :options="(contracts??[]).map(c=>({value:c.id,label:c.contractName}))" @change="onContractChange" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="合作方"><a-input :value="formPartnerName" disabled placeholder="选择合同后自动填充乙方" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="付款类型"><a-select v-model:value="formData.payType" placeholder="请选择付款类型" style="width:100%"><a-select-option v-for="(label,key) in PAY_TYPE_LABEL" :key="key" :value="key">{{ label }}</a-select-option></a-select></a-form-item></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="申请金额"><a-input-number v-model:value="formData.applyAmount" :min="0" :precision="2" style="width:100%" placeholder="金额（元）" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="申请原因"><a-textarea v-model:value="formData.applyReason" placeholder="申请原因" :rows="2" /></a-form-item></a-col>
        </a-row>
      </a-form>
      <div style="border-top:1px solid #f0f0f0;padding-top:12px;margin-top:4px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><span style="font-weight:600;font-size:14px">付款依据</span><a-button size="small" @click="handleAddBasis">添加依据行</a-button></div>
        <a-table :data-source="basisList" :pagination="false" row-key="key" size="small" :scroll="{y:240}">
          <a-table-column title="来源类型" width="100"><template #default="{record:item,index}"><a-select v-model:value="item.sourceType" size="small" style="width:100%" @change="handleSourceChange(index)"><a-select-option value="RECEIPT">材料验收</a-select-option><a-select-option value="MEASURE">分包计量</a-select-option></a-select></template></a-table-column>
          <a-table-column title="来源单据" width="240"><template #default="{record:item}"><a-select v-model:value="item.sourceId" size="small" placeholder="选择单据" allow-clear style="width:100%"><a-select-option v-for="opt in getSourceOptions(item.sourceType||'RECEIPT')" :key="opt.id" :value="opt.id">{{ opt.label }}</a-select-option></a-select></template></a-table-column>
          <a-table-column title="金额" width="160"><template #default="{record:item}"><a-input-number v-model:value="item.amount" :min="0" :precision="2" size="small" style="width:100%" placeholder="金额" /></template></a-table-column>
          <a-table-column title="操作" width="60"><template #default="{index}"><a-button type="link" size="small" danger @click="handleRemoveBasis(index)">删除</a-button></template></a-table-column>
        </a-table>
      </div>
    </a-modal>

    <!-- Writeback Modal -->
    <a-modal v-model:open="writebackVisible" title="付款回写" :width="480" @ok="handleWritebackOk" @cancel="handleWritebackCancel">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="支付金额" required><a-input-number v-model:value="writebackForm.payAmount" :min="0.01" :precision="2" style="width:100%" placeholder="请输入支付金额" /></a-form-item>
        <a-form-item label="支付日期" required><a-date-picker v-model:value="writebackForm.payDate" style="width:100%" /></a-form-item>
        <a-form-item label="支付方式" required><a-select v-model:value="writebackForm.payMethod" placeholder="请选择"><a-select-option value="BANK_TRANSFER">银行转账</a-select-option><a-select-option value="CASH">现金</a-select-option><a-select-option value="CHECK">支票</a-select-option><a-select-option value="OTHER">其他</a-select-option></a-select></a-form-item>
        <a-form-item label="凭证号"><a-input v-model:value="writebackForm.voucherNo" placeholder="请输入凭证号" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped></style>
