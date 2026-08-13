import type { PageResult } from "../api";
import type { FinanceDecimalString } from "./types";

export interface PaymentApplicationCommand {
  projectId: string;
  contractId: string;
  partnerId: string;
  costSubjectId: string;
  budgetLineId: string;
  payType: string;
  applyAmount: FinanceDecimalString;
  applyReason?: string;
  expenseCategory?: string;
}
export interface PayRecordWritebackCommand {
  payApplicationId: string;
  payAmount: FinanceDecimalString;
  paidAt: string;
  fundAccountId: string;
  payMethod: string;
  voucherNo?: string;
  externalTxnNo: string;
  remark?: string;
}
export interface ExpenseApplicationCommand {
  projectId: string;
  contractId: string;
  costSubjectId: string;
  budgetLineId: string;
  payeePartnerId: string;
  expenseCategory: string;
  expenseDate: string;
  amount: FinanceDecimalString;
  description: string;
}
export interface InvoiceCommand {
  payRecordId?: string;
  payApplicationId?: string;
  invoiceNo: string;
  invoiceType: string;
  invoiceAmount: FinanceDecimalString;
  taxRate?: FinanceDecimalString;
  taxAmount?: FinanceDecimalString;
  invoiceDate: string;
  sellerName?: string;
  buyerName?: string;
}

export interface PaymentApplicationQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  partnerId?: string;
  payStatus?: string;
  approvalStatus?: string;
  applyCode?: string;
}

export interface PaymentApplicationRecord {
  id: string;
  tenantId: string;
  projectId: string;
  contractId?: string | null;
  partnerId?: string | null;
  costSubjectId?: string | null;
  budgetLineId?: string | null;
  expenseCategory?: string | null;
  applyCode: string;
  applyAmount: FinanceDecimalString;
  approvedAmount: FinanceDecimalString;
  actualPayAmount: FinanceDecimalString;
  payType: string;
  payStatus: string;
  approvalStatus: string;
  applyReason?: string | null;
  integrityVersion: string;
  projectName?: string | null;
  contractName?: string | null;
  partnerName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type PaymentApplicationPage = PageResult<PaymentApplicationRecord>;

export interface PaymentApplicationBasisRecord {
  id?: string | null;
  payApplicationId?: string | null;
  basisType: string;
  basisId: string;
  basisAmount: FinanceDecimalString;
  remark?: string | null;
}

export interface PaymentApplicationSourceRecord {
  id?: string | null;
  payApplicationId?: string | null;
  sourceType: string;
  sourceRefId: string;
  receiptItemId?: string | null;
  sourceAmount: FinanceDecimalString;
  paidAmount?: FinanceDecimalString | null;
  remark?: string | null;
}

export interface ExpenseApplicationQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  approvalStatus?: string;
}

export interface ExpenseApplicationRecord {
  id: string;
  projectId: string;
  contractId?: string | null;
  costSubjectId?: string | null;
  budgetLineId?: string | null;
  payeePartnerId?: string | null;
  expenseCode: string;
  expenseCategory: string;
  expenseDate: string;
  amount: FinanceDecimalString;
  convertedAmount: FinanceDecimalString;
  paidAmount: FinanceDecimalString;
  availableToConvert: FinanceDecimalString;
  status: string;
  approvalStatus: string;
  description?: string | null;
  version?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type ExpenseApplicationPage = PageResult<ExpenseApplicationRecord>;

export interface InvoiceRecord {
  id: string;
  projectId?: string | null;
  contractId?: string | null;
  payApplicationId?: string | null;
  payRecordId?: string | null;
  invoiceNo: string;
  invoiceType?: string | null;
  invoiceAmount: FinanceDecimalString;
  taxRate?: FinanceDecimalString | null;
  taxAmount?: FinanceDecimalString | null;
  invoiceDate?: string | null;
  verifyStatus: string;
  sellerName?: string | null;
  buyerName?: string | null;
  version?: number | null;
}
export interface InvoiceQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  invoiceNo?: string;
  verifyStatus?: string;
}

export type InvoicePage = PageResult<InvoiceRecord>;

export interface PayRecordOption {
  id: string;
  payApplicationId?: string | null;
  contractId?: string | null;
  payAmount: FinanceDecimalString;
  payDate?: string | null;
  voucherNo?: string | null;
}

export type PayRecordOptionPage = PageResult<PayRecordOption>;
