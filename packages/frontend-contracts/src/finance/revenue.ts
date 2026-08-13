import type { PageResult } from "../api";
import type { FinanceDecimalString } from "./types";

export interface OwnerSettlementRecord {
  id: string;
  projectId: string;
  contractId: string;
  revenueId?: string | null;
  customerId: string;
  settlementCode: string;
  settlementPeriod: string;
  settlementDate: string;
  grossAmount: FinanceDecimalString;
  taxAmount: FinanceDecimalString;
  retentionAmount: FinanceDecimalString;
  netReceivableAmount: FinanceDecimalString;
  dueDate: string;
  status: string;
  attachmentCount: number;
  approvalInstanceId?: string | null;
  formulaVersion: string;
  version: string;
  remark?: string | null;
}
export interface ReceivableRecord {
  id: string;
  projectId: string;
  contractId: string;
  settlementId: string;
  customerId: string;
  receivableCode: string;
  receivableType: string;
  originalAmount: FinanceDecimalString;
  collectedAmount: FinanceDecimalString;
  creditedAmount: FinanceDecimalString;
  outstandingAmount: FinanceDecimalString;
  dueDate: string;
  status: string;
  overdue: boolean;
  version: string;
}
export interface SalesInvoiceRecord {
  id: string;
  projectId: string;
  contractId: string;
  customerId: string;
  invoiceCode?: string | null;
  invoiceNo: string;
  invoiceType: string;
  invoiceDate: string;
  amountWithoutTax: FinanceDecimalString;
  taxAmount: FinanceDecimalString;
  totalAmount: FinanceDecimalString;
  allocatedAmount: FinanceDecimalString;
  status: string;
  verificationStatus: string;
  attachmentCount: number;
  version: string;
  remark?: string | null;
}
export interface CollectionRecord {
  id: string;
  projectId: string;
  contractId: string;
  customerId: string;
  fundAccountId: string;
  collectionCode: string;
  externalTxnNo: string;
  collectedAt: string;
  amount: FinanceDecimalString;
  allocatedAmount: FinanceDecimalString;
  unallocatedAmount: FinanceDecimalString;
  payerName: string;
  status: string;
  attachmentCount: number;
  version: string;
  remark?: string | null;
}
export type RevenueRecord =
  | OwnerSettlementRecord
  | ReceivableRecord
  | SalesInvoiceRecord
  | CollectionRecord;
export interface RevenueQuery {
  projectId?: string;
  status?: string;
}
export interface ContractRevenueRecord {
  id: string;
  projectId: string;
  contractId: string;
  revenueCode: string;
  revenueAmount: FinanceDecimalString;
  approvalStatus: string;
}
export type ContractRevenuePage = PageResult<ContractRevenueRecord>;
export interface AmountAllocation {
  receivableId: string;
  amount: FinanceDecimalString;
}
export interface OwnerSettlementCommand {
  projectId: string;
  contractId: string;
  revenueId: string;
  settlementPeriod: string;
  settlementDate: string;
  grossAmount: FinanceDecimalString;
  taxAmount: FinanceDecimalString;
  retentionAmount: FinanceDecimalString;
  dueDate: string;
  customerId: string;
  attachmentCount: number;
  remark?: string;
}
export interface SalesInvoiceCommand {
  projectId: string;
  contractId: string;
  customerId: string;
  invoiceCode?: string;
  invoiceNo: string;
  invoiceType: string;
  invoiceDate: string;
  amountWithoutTax: FinanceDecimalString;
  taxAmount: FinanceDecimalString;
  allocations: AmountAllocation[];
  remark?: string;
}
export interface CollectionCommand {
  projectId: string;
  contractId: string;
  customerId: string;
  fundAccountId: string;
  externalTxnNo: string;
  collectedAt: string;
  amount: FinanceDecimalString;
  payerName: string;
  allocations?: AmountAllocation[];
  remark?: string;
}
