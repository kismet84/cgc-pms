<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message, Modal } from "ant-design-vue";
import { ArrowLeftOutlined } from "@ant-design/icons-vue";
import { useSettlementStore } from "@/stores/settlement";
import { submitSettlement } from "@/api/modules/settlement";
import type { SettlementStatus } from "@/types/settlement";
import { SETTLEMENT_STATUS_LABEL, SETTLEMENT_STATUS_COLOR } from "@/types/settlement";
import { SOURCE_TYPE_LABEL, SOURCE_TYPE_COLOR } from "@/types/cost";
import type { SourceType } from "@/types/cost";

const route = useRoute();
const router = useRouter();
const store = useSettlementStore();

const settlementId = route.params.id as string;
const activeTab = ref("basic");
const submitting = ref(false);

const APPROVAL_STATUS_LABEL: Record<string, string> = { DRAFT: "草稿", APPROVING: "审批中", APPROVED: "已通过", REJECTED: "已驳回", WITHDRAWN: "已撤回" };
const APPROVAL_STATUS_COLOR: Record<string, string> = { DRAFT: "default", APPROVING: "processing", APPROVED: "success", REJECTED: "error", WITHDRAWN: "warning" };
const DIRECTION_LABEL: Record<string, string> = { COST: "成本增加", DEDUCT: "成本减少", NEUTRAL: "中性变更" };
const DIRECTION_COLOR: Record<string, string> = { COST: "red", DEDUCT: "green", NEUTRAL: "blue" };
const COST_TYPE_LABEL: Record<string, string> = { MATERIAL: "材料费", LABOR: "人工费", MACHINERY: "机械费", SUBCONTRACT: "分包费", OTHER: "其他费用" };
const COST_STATUS_LABEL: Record<string, string> = { LOCKED: "已锁定", CONFIRMED: "已确认", PENDING: "待确认" };
const COST_STATUS_COLOR: Record<string, string> = { LOCKED: "blue", CONFIRMED: "green", PENDING: "orange" };
const PAY_TYPE_LABEL: Record<string, string> = { ADVANCE: "预付款", PROGRESS: "进度款", FINAL: "结算款", OTHER: "其他" };
const PAY_STATUS_LABEL: Record<string, string> = { UNPAID: "未支付", PARTIAL: "部分支付", PAID: "已支付" };
const PAY_STATUS_COLOR: Record<string, string> = { UNPAID: "default", PARTIAL: "warning", PAID: "success" };
const actionNameMap: Record<string, string> = { SUBMIT: "提交审批", APPROVE: "同意", REJECT: "驳回", WITHDRAW: "撤回", RESUBMIT: "重新提交", TRANSFER: "转办", ADD_SIGN: "加签" };

const variationColumns = [
  { title: "签证编号", dataIndex: "varCode", key: "varCode", width: 140 },
  { title: "签证名称", dataIndex: "varName", key: "varName", width: 180 },
  { title: "变更类型", dataIndex: "varType", key: "varType", width: 100 },
  { title: "方向", dataIndex: "direction", key: "direction", width: 100 },
  { title: "上报金额", dataIndex: "reportedAmount", key: "reportedAmount", width: 120, align: "right" as const },
  { title: "审批金额", dataIndex: "approvedAmount", key: "approvedAmount", width: 120, align: "right" as const },
  { title: "确认金额", dataIndex: "confirmedAmount", key: "confirmedAmount", width: 120, align: "right" as const },
  { title: "创建时间", dataIndex: "createdAt", key: "createdAt", width: 160 },
];

const paymentColumns = [
  { title: "申请编号", dataIndex: "applyCode", key: "applyCode", width: 150 },
  { title: "付款类型", dataIndex: "payType", key: "payType", width: 100 },
  { title: "申请金额", dataIndex: "applyAmount", key: "applyAmount", width: 120, align: "right" as const },
  { title: "审批金额", dataIndex: "approvedAmount", key: "approvedAmount", width: 120, align: "right" as const },
  { title: "实际付款", dataIndex: "actualPayAmount", key: "actualPayAmount", width: 120, align: "right" as const },
  { title: "付款状态", dataIndex: "payStatus", key: "payStatus", width: 100 },
  { title: "付款日期", dataIndex: "payDate", key: "payDate", width: 120 },
  { title: "凭证号", dataIndex: "voucherNo", key: "voucherNo", width: 140 },
  { title: "创建时间", dataIndex: "createdAt", key: "createdAt", width: 160 },
];

const costColumns = [
  { title: "成本科目", dataIndex: "costSubjectName", key: "costSubjectName", width: 140 },
  { title: "费用类型", dataIndex: "costType", key: "costType", width: 100 },
  { title: "来源类型", dataIndex: "sourceType", key: "sourceType", width: 120 },
  { title: "来源单据", dataIndex: "sourceId", key: "sourceId", width: 140 },
  { title: "金额(含税)", dataIndex: "amount", key: "amount", width: 120, align: "right" as const },
  { title: "税额", dataIndex: "taxAmount", key: "taxAmount", width: 100, align: "right" as const },
  { title: "不含税金额", dataIndex: "amountWithoutTax", key: "amountWithoutTax", width: 120, align: "right" as const },
  { title: "状态", dataIndex: "costStatus", key: "costStatus", width: 90 },
  { title: "创建时间", dataIndex: "createdAt", key: "createdAt", width: 160 },
];

const attachmentColumns = [
  { title: "文件名", dataIndex: "originalName", key: "originalName", width: 240 },
  { title: "大小", dataIndex: "fileSize", key: "fileSize", width: 100 },
  { title: "上传人", dataIndex: "createdBy", key: "createdBy", width: 120 },
  { title: "上传时间", dataIndex: "createdAt", key: "createdAt", width: 160 },
];

const detail = computed(() => store.detail);
const loading = computed(() => store.loading);
const variations = computed(() => store.variations ?? []);
const payments = computed(() => store.payments ?? []);
const costs = computed(() => store.costs ?? []);
const attachments = computed(() => store.attachments ?? []);
const approvalRecords = computed(() => store.approvalRecords ?? []);
const variationsLoading = computed(() => store.variationsLoading);
const paymentsLoading = computed(() => store.paymentsLoading);
const costsLoading = computed(() => store.costsLoading);
const attachmentsLoading = computed(() => store.attachmentsLoading);
const recordsLoading = computed(() => store.recordsLoading);

function formatAmount(val: string | undefined): string { if (!val) return "¥0.00"; const n = parseFloat(val); return isNaN(n) ? "¥0.00" : "¥" + n.toLocaleString("zh-CN", { minimumFractionDigits: 2 }); }
function fmtFileSize(bytes: number | undefined): string { if (!bytes) return "0 B"; const units = ["B", "KB", "MB", "GB"]; let i = 0; let size = bytes; while (size >= 1024 && i < units.length - 1) { size /= 1024; i++; } return size.toFixed(2) + " " + units[i]; }
function jumpToSource(sourceType: string, sourceId: string) { const map: Record<string, string> = { CT_CONTRACT: "/contract", MAT_RECEIPT: "/receipt", SUB_MEASURE: "/subcontract", VAR_ORDER: "/variation" }; const base = map[sourceType]; if (base) router.push(base + "/" + sourceId); }
function goBack() { router.back(); }

async function handleAction(action: string) {
  if (action === "APPROVE" || action === "REJECT") {
    Modal.confirm({ title: action === "APPROVE" ? "确认通过？" : "确认驳回？", onOk: async () => { submitting.value = true; try { await submitSettlement(settlementId, action); message.success(action === "APPROVE" ? "已通过" : "已驳回"); store.fetchDetail(settlementId); } finally { submitting.value = false; } } });
  } else { submitting.value = true; try { await submitSettlement(settlementId, action); message.success("操作成功"); store.fetchDetail(settlementId); } finally { submitting.value = false; } }
}

onMounted(() => { store.fetchDetail(settlementId); });
</script>
<template>
  <div class="app-page">
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb"><a-breadcrumb-item>结算管理</a-breadcrumb-item><a-breadcrumb-item>结算详情</a-breadcrumb-item></a-breadcrumb>
      <h1 class="app-page-title">结算详情</h1>
      <div class="pt-head-actions">
        <a-button @click="goBack"><ArrowLeftOutlined />返回</a-button>
        <template v-if="detail">
          <a-button v-if="detail.settlementStatus === 'DRAFT'" type="primary" @click="handleAction('SUBMIT')" :loading="submitting">提交审批</a-button>
          <a-button v-if="detail.approvalStatus === 'APPROVING'" type="primary" @click="handleAction('APPROVE')" :loading="submitting">同意</a-button>
          <a-button v-if="detail.approvalStatus === 'APPROVING'" danger @click="handleAction('REJECT')" :loading="submitting">驳回</a-button>
        </template>
      </div>
    </div>

    <a-spin :spinning="loading">
      <template v-if="detail">
        <!-- Info summary -->
        <section class="pt-panel" style="padding:20px;margin-bottom:12px">
          <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px 24px;font-size:14px">
            <div><span style="color:#6b7280">结算编号：</span><strong>{{ detail.settlementCode }}</strong></div>
            <div><span style="color:#6b7280">项目：</span><strong>{{ detail.projectName }}</strong></div>
            <div><span style="color:#6b7280">合同：</span><strong>{{ detail.contractName }}</strong></div>
            <div><span style="color:#6b7280">结算状态：</span><a-tag :color="SETTLEMENT_STATUS_COLOR[detail.settlementStatus as SettlementStatus]||'default'" size="small">{{ SETTLEMENT_STATUS_LABEL[detail.settlementStatus as SettlementStatus] || detail.settlementStatus }}</a-tag></div>
            <div><span style="color:#6b7280">合同金额：</span><strong>{{ formatAmount(detail.contractAmount) }}</strong></div>
            <div><span style="color:#6b7280">结算金额：</span><strong style="color:#1677ff">{{ formatAmount(detail.settlementAmount) }}</strong></div>
            <div><span style="color:#6b7280">变更金额：</span><strong style="color:#ef4444">{{ formatAmount(detail.changeAmount) }}</strong></div>
            <div><span style="color:#6b7280">审批状态：</span><a-tag :color="APPROVAL_STATUS_COLOR[detail.approvalStatus]||'default'" size="small">{{ APPROVAL_STATUS_LABEL[detail.approvalStatus] || detail.approvalStatus }}</a-tag></div>
          </div>
        </section>

        <!-- Tabs -->
        <a-tabs v-model:activeKey="activeTab" style="background:#fff;border-radius:6px;padding:0 20px;border:1px solid #edf1f7">
          <a-tab-pane key="basic" tab="基本信息">
            <div style="padding:8px 0 20px">
              <div class="pt-panel" style="padding:16px 20px"><div class="pt-panel-header">结算信息</div><div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px 24px;font-size:14px"><div><span style="color:#6b7280">结算类型：</span><span>{{ detail.settlementType === "PROGRESS" ? "进度结算" : detail.settlementType === "FINAL" ? "竣工结算" : detail.settlementType === "INTERIM" ? "期中结算" : (detail.settlementType || "-") }}</span></div><div><span style="color:#6b7280">创建人：</span><span>{{ detail.createdBy ?? "-" }}</span></div><div><span style="color:#6b7280">创建时间：</span><span>{{ detail.createdAt ?? "-" }}</span></div><div><span style="color:#6b7280">更新时间：</span><span>{{ detail.updatedAt ?? "-" }}</span></div><div style="grid-column:span 2"><span style="color:#6b7280">备注：</span><span>{{ detail.remark || "-" }}</span></div></div></div>
            </div>
          </a-tab-pane>

          <a-tab-pane key="variations" tab="变更签证">
            <a-spin :spinning="variationsLoading">
              <a-table v-if="variations.length>0" :columns="variationColumns" :data-source="variations" :pagination="false" :scroll="{x:1060}" size="small" row-key="id" style="margin:8px 0 20px">
                <template #bodyCell="{column,record}"><template v-if="column.key==='direction'"><a-tag :color="DIRECTION_COLOR[record.direction]||'default'" size="small">{{ DIRECTION_LABEL[record.direction] || record.direction }}</a-tag></template><template v-else-if="['reportedAmount','approvedAmount','confirmedAmount'].includes(column.key)"><span>{{ formatAmount(record[column.key]) }}</span></template></template>
              </a-table>
              <a-empty v-else description="暂无变更签证" style="padding:32px 0" />
            </a-spin>
          </a-tab-pane>

          <a-tab-pane key="payments" tab="付款记录">
            <a-spin :spinning="paymentsLoading">
              <a-table v-if="payments.length>0" :columns="paymentColumns" :data-source="payments" :pagination="false" :scroll="{x:1180}" size="small" row-key="id" style="margin:8px 0 20px">
                <template #bodyCell="{column,record}"><template v-if="column.key==='payType'">{{ PAY_TYPE_LABEL[record.payType] || record.payType }}</template><template v-else-if="column.key==='payStatus'"><a-tag :color="PAY_STATUS_COLOR[record.payStatus]||'default'" size="small">{{ PAY_STATUS_LABEL[record.payStatus] || record.payStatus }}</a-tag></template><template v-else-if="['applyAmount','approvedAmount','actualPayAmount'].includes(column.key)"><span>{{ formatAmount(record[column.key]) }}</span></template></template>
              </a-table>
              <a-empty v-else description="暂无付款记录" style="padding:32px 0" />
            </a-spin>
          </a-tab-pane>

          <a-tab-pane key="costs" tab="成本明细">
            <a-spin :spinning="costsLoading">
              <a-table v-if="costs.length>0" :columns="costColumns" :data-source="costs" :pagination="false" :scroll="{x:1100}" size="small" row-key="id" style="margin:8px 0 20px">
                <template #bodyCell="{column,record}"><template v-if="column.key==='sourceType'"><a-tag :color="SOURCE_TYPE_COLOR[record.sourceType as SourceType]||'default'" size="small">{{ SOURCE_TYPE_LABEL[record.sourceType as SourceType] || record.sourceType }}</a-tag></template><template v-else-if="column.key==='sourceId'"><a v-if="record.sourceType && record.sourceId" style="color:#1677ff;cursor:pointer" @click="jumpToSource(record.sourceType,record.sourceId)">{{ record.sourceId }}</a><span v-else>-</span></template><template v-else-if="column.key==='costType'">{{ COST_TYPE_LABEL[record.costType] || record.costType || "-" }}</template><template v-else-if="column.key==='costStatus'"><a-tag :color="COST_STATUS_COLOR[record.costStatus]||'default'" size="small">{{ COST_STATUS_LABEL[record.costStatus] || record.costStatus }}</a-tag></template><template v-else-if="['amount','taxAmount','amountWithoutTax'].includes(column.key)"><span>{{ formatAmount(record[column.key]) }}</span></template></template>
              </a-table>
              <a-empty v-else description="暂无成本记录" style="padding:32px 0" />
            </a-spin>
          </a-tab-pane>

          <a-tab-pane key="attachments" tab="附件">
            <a-spin :spinning="attachmentsLoading">
              <a-table v-if="attachments.length>0" :columns="attachmentColumns" :data-source="attachments" :pagination="false" :scroll="{x:680}" size="small" row-key="id" style="margin:8px 0 20px"><template #bodyCell="{column,record}"><template v-if="column.key==='originalName'"><span style="color:#1677ff;cursor:pointer">{{ record.originalName }}</span></template><template v-else-if="column.key==='fileSize'">{{ fmtFileSize(record.fileSize) }}</template></template></a-table>
              <a-empty v-else description="暂无附件" style="padding:32px 0" />
            </a-spin>
          </a-tab-pane>

          <a-tab-pane key="approval" tab="审批记录">
            <a-spin :spinning="recordsLoading">
              <a-timeline v-if="approvalRecords.length>0" style="padding:16px 0 20px">
                <a-timeline-item v-for="r in approvalRecords" :key="r.id">
                  <div><strong>{{ r.operatorName }}</strong><a-tag style="margin-left:8px" size="small">{{ actionNameMap[r.actionType] || r.actionName }}</a-tag><span v-if="r.nodeName" style="margin-left:8px;color:#999;font-size:13px">{{ r.nodeName }}</span></div>
                  <div v-if="r.comment" style="color:#666;font-size:13px;margin-top:4px">{{ r.comment }}</div>
                  <div style="color:#999;font-size:12px;margin-top:2px">{{ r.createdAt }}</div>
                </a-timeline-item>
              </a-timeline>
              <a-empty v-else description="暂无审批记录" style="padding:32px 0" />
            </a-spin>
          </a-tab-pane>
        </a-tabs>
      </template>
      <a-empty v-else-if="!loading" description="结算单不存在" style="padding:80px 0" />
    </a-spin>
  </div>
</template>

<style scoped></style>

