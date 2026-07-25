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
} as const;

export const SUBCONTRACT_API = {
  tasks: "/sub-tasks",
  task: (id: string) => `/sub-tasks/${encodeURIComponent(id)}`,
  measures: "/sub-measures",
  measure: (id: string) => `/sub-measures/${encodeURIComponent(id)}`,
  measureItems: (id: string) => `/sub-measures/${encodeURIComponent(id)}/items`,
  measureItemsBatch: (id: string) =>
    `/sub-measures/${encodeURIComponent(id)}/items/batch`,
  measureSubmit: (id: string) =>
    `/sub-measures/${encodeURIComponent(id)}/submit`,
  settlements: "/settlements",
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
}

export type SettlementPage = PageResult<SettlementRecord>;

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
