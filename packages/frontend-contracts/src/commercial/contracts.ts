import type { PageResult } from "../api";
import type {
  ApprovalStatus,
  ContractStatus,
  ContractType,
  DecimalString,
} from "./types";

export interface ContractKpi {
  totalCount: string;
  totalAmount: DecimalString;
  paidAmount: DecimalString;
  unpaidAmount: DecimalString;
  overdueCount: string;
}

export interface ContractQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  contractCode?: string;
  contractName?: string;
  contractType?: ContractType;
  contractStatus?: ContractStatus;
  approvalStatus?: ApprovalStatus;
  projectId?: string;
  partyAId?: string;
  partyBId?: string;
  startDate?: string;
  endDate?: string;
}

export interface ContractRecord {
  id: string;
  tenantId: string;
  orgId: string;
  projectId: string;
  contractCode: string;
  contractName: string;
  contractType: ContractType;
  partyAId: string;
  partyAName: string;
  partyBId: string;
  partyBName: string;
  contractAmount: DecimalString;
  currentAmount: DecimalString;
  taxRate: DecimalString;
  taxAmount: DecimalString;
  amountWithoutTax: DecimalString;
  signedDate: string;
  startDate: string;
  endDate: string;
  paymentMethod: string;
  settlementMethod: string;
  paidAmount: DecimalString;
  settlementAmount: DecimalString;
  payableAmount?: DecimalString | null;
  pricingMode?: "FIXED" | "ACTUAL" | null;
  contractStatus: ContractStatus;
  approvalStatus: ApprovalStatus;
  projectName: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  costGeneratedFlag?: string | number | null;
  version?: string | number | null;
  remark?: string | null;
}

export interface ContractProjectOption {
  id: string;
  projectCode: string;
  projectName: string;
  status: string;
  mainEligible: boolean;
  nonMainEligible: boolean;
}

export interface ContractItemRecord {
  id?: string | null;
  tenantId?: string | null;
  contractId?: string | null;
  materialId?: string | null;
  itemCode?: string | null;
  itemName: string;
  itemSpec?: string | null;
  unit?: string | null;
  quantity?: DecimalString | null;
  unitPrice?: DecimalString | null;
  amount?: DecimalString | null;
  taxRate?: DecimalString | null;
  taxAmount?: DecimalString | null;
  amountWithoutTax?: DecimalString | null;
  sortOrder?: string | number | null;
  remark?: string | null;
}

export interface ContractPaymentTermRecord {
  id?: string | null;
  tenantId?: string | null;
  contractId?: string | null;
  termName: string;
  paymentRatio?: DecimalString | null;
  paymentAmount?: DecimalString | null;
  paymentCondition?: string | null;
  plannedDate?: string | null;
  actualDate?: string | null;
  termStatus?: string | null;
  sortOrder?: string | number | null;
  remark?: string | null;
}

export interface ContractApprovalRecord {
  id: string;
  nodeName: string;
  operatorName: string;
  actionType: string;
  actionName: string;
  comment?: string | null;
  createdAt: string;
}

export interface ContractBudgetAllocationRecord {
  id?: string | null;
  tenantId?: string | null;
  projectId?: string | null;
  contractId: string;
  budgetLineId: string;
  allocatedAmount: DecimalString;
  reservedAmount?: DecimalString | null;
  consumedAmount?: DecimalString | null;
  version?: string | number | null;
}

export interface ContractCompositeRecord {
  contract: ContractRecord;
  items: ContractItemRecord[];
  paymentTerms: ContractPaymentTermRecord[];
  approvalRecords: ContractApprovalRecord[];
}

export interface ContractSaveCommand {
  contract: {
    id?: string | null;
    projectId?: string | null;
    contractName: string;
    contractType: ContractType;
    partyAId?: string | null;
    partyBId?: string | null;
    contractAmount?: DecimalString | null;
    taxRate?: DecimalString | null;
    taxAmount?: DecimalString | null;
    amountWithoutTax?: DecimalString | null;
    signedDate?: string | null;
    startDate?: string | null;
    endDate?: string | null;
    paymentMethod?: string | null;
    settlementMethod?: string | null;
    pricingMode?: "FIXED" | "ACTUAL" | null;
    version?: string | number | null;
    remark?: string | null;
  };
  items: ContractItemRecord[];
  paymentTerms: ContractPaymentTermRecord[];
}

export type ContractPage = PageResult<ContractRecord>;
