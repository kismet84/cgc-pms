import type { PageResult } from "./api";

export type SubcontractDecimalString = string;

export const SUBCONTRACT_QUERY_PERMISSIONS = {
  task: "subtask:query",
  measure: "subcontract:measure:query",
  settlement: "settlement:query",
} as const;

export const SUBCONTRACT_PERMISSIONS = {
  task: {
    query: "subtask:query",
    add: "subtask:add",
    edit: "subtask:edit",
    delete: "subtask:delete",
  },
  measure: {
    query: "subcontract:measure:query",
    add: "subcontract:measure:add",
    edit: "subcontract:measure:edit",
    delete: "subcontract:measure:delete",
    submit: "subcontract:measure:submit",
  },
  settlement: {
    query: "settlement:query",
    add: "settlement:add",
    edit: "settlement:edit",
    delete: "settlement:delete",
    submit: "settlement:submit",
  },
} as const;

export const SUBCONTRACT_API = {
  tasks: "/sub-tasks",
  taskFormOptions: "/sub-tasks/form-options",
  task: (id: string) => `/sub-tasks/${encodeURIComponent(id)}`,
  measures: "/sub-measures",
  measure: (id: string) => `/sub-measures/${encodeURIComponent(id)}`,
  measureItems: (id: string) => `/sub-measures/${encodeURIComponent(id)}/items`,
  measureItemsBatch: (id: string) =>
    `/sub-measures/${encodeURIComponent(id)}/items/batch`,
  measureSubmit: (id: string) =>
    `/sub-measures/${encodeURIComponent(id)}/submit`,
  settlements: "/settlements",
  settlementKpi: "/settlements/kpi",
  settlement: (id: string) => `/settlements/${encodeURIComponent(id)}`,
  settlementItems: (id: string) =>
    `/settlements/${encodeURIComponent(id)}/items`,
  settlementItemsBatch: (id: string) =>
    `/settlements/${encodeURIComponent(id)}/items/batch`,
  settlementCompute: (contractId: string) =>
    `/settlements/compute/${encodeURIComponent(contractId)}`,
  settlementSources: (id: string) =>
    `/settlements/${encodeURIComponent(id)}/sources`,
  settlementVariations: (id: string) =>
    `/settlements/${encodeURIComponent(id)}/variations`,
  settlementPayments: (id: string) =>
    `/settlements/${encodeURIComponent(id)}/payments`,
  settlementCosts: (id: string) =>
    `/settlements/${encodeURIComponent(id)}/costs`,
  settlementAttachments: (id: string) =>
    `/settlements/${encodeURIComponent(id)}/attachments`,
  settlementApprovalRecords: (id: string) =>
    `/settlements/${encodeURIComponent(id)}/approval-records`,
  settlementSubmit: (id: string) =>
    `/settlements/${encodeURIComponent(id)}/submit`,
} as const;

export interface SubcontractTaskQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  partnerId?: string;
  status?: string;
  taskCode?: string;
  taskName?: string;
}

export interface SubcontractTaskRecord {
  id: string;
  tenantId: string;
  projectId: string;
  wbsTaskId?: string | null;
  contractId?: string | null;
  partnerId?: string | null;
  predecessorTaskId?: string | null;
  predecessorTaskName?: string | null;
  predecessorStatus?: string | null;
  predecessorPlannedEndDate?: string | null;
  predecessorActualEndDate?: string | null;
  taskCode: string;
  taskName: string;
  workArea?: string | null;
  plannedStartDate?: string | null;
  plannedEndDate?: string | null;
  actualStartDate?: string | null;
  actualEndDate?: string | null;
  progressPercent: SubcontractDecimalString;
  status: string;
  projectName?: string | null;
  contractName?: string | null;
  partnerName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
}

export type SubcontractTaskPage = PageResult<SubcontractTaskRecord>;

export interface SubcontractTaskCommand {
  projectId: string;
  wbsTaskId: string;
  contractId?: string | null;
  partnerId?: string | null;
  predecessorTaskId?: string | null;
  taskName: string;
  workArea?: string | null;
  plannedStartDate?: string | null;
  plannedEndDate?: string | null;
  actualStartDate?: string | null;
  actualEndDate?: string | null;
  progressPercent?: SubcontractDecimalString | null;
  status?: string | null;
  remark?: string | null;
}

export interface SubcontractTaskFormOptions {
  wbsTasks: Array<{
    id: string;
    taskCode: string;
    taskName: string;
  }>;
}

export interface SubcontractMeasureQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  partnerId?: string;
  status?: string;
  measureCode?: string;
}

export interface SubcontractMeasureRecord {
  id: string;
  tenantId: string;
  projectId: string;
  contractId?: string | null;
  partnerId?: string | null;
  subTaskId?: string | null;
  subTaskCode?: string | null;
  subTaskName?: string | null;
  measureCode: string;
  measurePeriod?: string | null;
  measureDate?: string | null;
  reportedAmount?: SubcontractDecimalString | null;
  approvedAmount?: SubcontractDecimalString | null;
  deductionAmount: SubcontractDecimalString;
  netAmount?: SubcontractDecimalString | null;
  approvalStatus: string;
  status: string;
  projectName?: string | null;
  contractName?: string | null;
  partnerName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
}

export type SubcontractMeasurePage = PageResult<SubcontractMeasureRecord>;

export interface SubcontractMeasureCommand {
  projectId: string;
  contractId?: string | null;
  partnerId?: string | null;
  subTaskId?: string | null;
  measurePeriod?: string | null;
  measureDate?: string | null;
  status?: string | null;
  remark?: string | null;
}

export interface SubcontractMeasureItemRecord {
  id?: string | null;
  tenantId?: string | null;
  measureId?: string | null;
  contractItemId: string;
  itemName: string;
  unit?: string | null;
  contractQuantity?: SubcontractDecimalString | null;
  currentQuantity: SubcontractDecimalString;
  cumulativeQuantity?: SubcontractDecimalString | null;
  unitPrice?: SubcontractDecimalString | null;
  amount?: SubcontractDecimalString | null;
}

export interface SubcontractMeasureItemCommand {
  contractItemId: string;
  currentQuantity: SubcontractDecimalString;
}

export interface SettlementQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  partnerId?: string;
  settlementCode?: string;
  settlementType?: string;
  settlementStatus?: string;
  approvalStatus?: string;
  keyword?: string;
}

export interface SettlementRecord {
  id: string;
  tenantId: string;
  projectId: string;
  contractId?: string | null;
  partnerId?: string | null;
  settlementCode: string;
  settlementType?: string | null;
  contractAmount?: SubcontractDecimalString | null;
  changeAmount: SubcontractDecimalString;
  measuredAmount: SubcontractDecimalString;
  deductionAmount: SubcontractDecimalString;
  paidAmount: SubcontractDecimalString;
  finalAmount?: SubcontractDecimalString | null;
  unpaidAmount: SubcontractDecimalString;
  warrantyAmount: SubcontractDecimalString;
  amountFormulaVersion: string;
  approvalStatus: string;
  status?: string | null;
  settlementStatus: string;
  finalizedAt?: string | null;
  projectName?: string | null;
  contractName?: string | null;
  partnerName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
  items?: SettlementItemRecord[];
}

export type SettlementPage = PageResult<SettlementRecord>;

export interface SettlementKpi {
  totalCount: number;
  totalContractAmount: SubcontractDecimalString;
  totalFinalAmount: SubcontractDecimalString;
  totalChangeAmount: SubcontractDecimalString;
  totalPaidAmount: SubcontractDecimalString;
  totalUnpaidAmount: SubcontractDecimalString;
  draftCount: number;
  finalizedCount: number;
}

export interface SettlementCommand {
  contractId: string;
  deductionAmount?: SubcontractDecimalString | null;
  remark?: string | null;
}

export interface SettlementItemRecord {
  id: string;
  settlementId: string;
  itemName: string;
  unit?: string | null;
  quantity?: SubcontractDecimalString | null;
  unitPrice?: SubcontractDecimalString | null;
  amount?: SubcontractDecimalString | null;
  sourceType?: string | null;
  sourceId?: string | null;
  remark?: string | null;
}

export interface SettlementItemCommand {
  sourceType: "CT_CONTRACT";
  sourceId: string;
  remark?: string | null;
}

export interface SettlementCompute {
  contractId?: string | null;
  contractAmount?: SubcontractDecimalString | null;
  changeAmount: SubcontractDecimalString;
  measuredAmount: SubcontractDecimalString;
  deductionAmount: SubcontractDecimalString;
  paidAmount: SubcontractDecimalString;
  finalAmount?: SubcontractDecimalString | null;
  unpaidAmount: SubcontractDecimalString;
  warrantyAmount: SubcontractDecimalString;
  amountFormulaVersion: string;
}

export interface SettlementContractItemSource {
  id: string;
  itemCode?: string | null;
  itemName: string;
  unit?: string | null;
  measuredQuantity: SubcontractDecimalString;
  unitPrice: SubcontractDecimalString;
  amount: SubcontractDecimalString;
}

export interface SettlementMeasureSource {
  id: string;
  measureCode: string;
  measurePeriod?: string | null;
  approvedAmount?: SubcontractDecimalString | null;
  approvalStatus: string;
}

export interface SettlementVariationSource {
  id: string;
  varCode: string;
  varName: string;
  varType?: string | null;
  confirmedAmount?: SubcontractDecimalString | null;
  approvalStatus?: string | null;
}

export interface SettlementPaySource {
  id: string;
  payAmount?: SubcontractDecimalString | null;
  payDate?: string | null;
  payMethod?: string | null;
  voucherNo?: string | null;
  payStatus?: string | null;
}

export interface SettlementSources {
  contractItems: SettlementContractItemSource[];
  varOrders: SettlementVariationSource[];
  subMeasures: SettlementMeasureSource[];
  payRecords: SettlementPaySource[];
}

export interface SettlementVariationRecord {
  id: string;
  varCode: string;
  varName: string;
  varType?: string | null;
  direction?: string | null;
  confirmedAmount?: SubcontractDecimalString | null;
  approvalStatus?: string | null;
}

export interface SettlementPaymentRecord {
  id: string;
  applicationId?: string | null;
  applyCode?: string | null;
  payType?: string | null;
  applyAmount?: SubcontractDecimalString | null;
  approvedAmount?: SubcontractDecimalString | null;
  actualPayAmount?: SubcontractDecimalString | null;
  payStatus?: string | null;
  payDate?: string | null;
  voucherNo?: string | null;
}

export interface SettlementCostRecord {
  id: string;
  costSubjectName?: string | null;
  costType?: string | null;
  sourceType?: string | null;
  amount?: SubcontractDecimalString | null;
  taxAmount?: SubcontractDecimalString | null;
  amountWithoutTax?: SubcontractDecimalString | null;
  costDate?: string | null;
  costStatus?: string | null;
}

export interface SettlementAttachmentRecord {
  id: string;
  originalName: string;
  fileSize?: number | null;
  fileType?: string | null;
  uploadedBy?: string | null;
  uploadedAt?: string | null;
}

export interface SettlementApprovalRecord {
  id: string;
  nodeName?: string | null;
  operatorName?: string | null;
  actionType?: string | null;
  actionName?: string | null;
  comment?: string | null;
  createdAt?: string | null;
}

export const SUBCONTRACT_DECIMAL_FIELDS = {
  task: ["progressPercent"],
  measure: ["reportedAmount", "approvedAmount", "deductionAmount", "netAmount"],
  settlement: [
    "contractAmount",
    "changeAmount",
    "measuredAmount",
    "deductionAmount",
    "paidAmount",
    "finalAmount",
    "unpaidAmount",
    "warrantyAmount",
  ],
} as const;
