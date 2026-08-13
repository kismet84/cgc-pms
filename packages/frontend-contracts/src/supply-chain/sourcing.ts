import type { PageResult } from "../api";
import type { SupplyChainDecimalString } from "./types";

export type SourcingEventStatus =
  "DRAFT" | "PUBLISHED" | "EVALUATING" | "AWARDED" | "CONTRACTED" | "CANCELLED";
export type SourcingInvitationStatus =
  "PENDING" | "INVITED" | "DECLINED" | "QUOTED" | "DISQUALIFIED";
export type SupplierQuoteStatus =
  "DRAFT" | "SUBMITTED" | "WINNER" | "LOST" | "INVALID";

export interface SourcingEventRecord {
  id: string;
  projectId: string;
  purchaseRequestId: string;
  sourcingCode: string;
  sourcingTitle: string;
  sourcingType: "INQUIRY" | "TENDER";
  deadline: string;
  currencyCode: string;
  status: SourcingEventStatus;
  awardedQuoteId?: string | null;
  awardedPartnerId?: string | null;
  contractId?: string | null;
  awardReason?: string | null;
  version?: number | null;
}

export interface SourcingSupplierRecord {
  id: string;
  sourcingEventId: string;
  partnerId: string;
  invitationStatus: SourcingInvitationStatus;
  disqualificationReason?: string | null;
}

export interface SupplierQuoteRecord {
  id: string;
  sourcingEventId: string;
  sourcingSupplierId: string;
  partnerId: string;
  quoteCode: string;
  totalAmount: SupplyChainDecimalString;
  taxRate: SupplyChainDecimalString;
  deliveryDays: number;
  validityDate: string;
  commercialTerms: string;
  status: SupplierQuoteStatus;
  version?: number | null;
}

export interface BidEvaluationRecord {
  id: string;
  sourcingEventId: string;
  quoteId: string;
  partnerId: string;
  commercialScore: SupplyChainDecimalString;
  technicalScore: SupplyChainDecimalString;
  deliveryScore: SupplyChainDecimalString;
  qualityScore: SupplyChainDecimalString;
  totalScore: SupplyChainDecimalString;
  evaluationComment: string;
}

export interface SupplierPerformanceRecord {
  id: string;
  projectId: string;
  partnerId: string;
  partnerCode?: string | null;
  partnerName?: string | null;
  contractId: string;
  purchaseOrderId: string;
  evaluationCode: string;
  periodStart: string;
  periodEnd: string;
  deliveryScore: SupplyChainDecimalString;
  qualityScore: SupplyChainDecimalString;
  serviceScore: SupplyChainDecimalString;
  commercialScore: SupplyChainDecimalString;
  totalScore: SupplyChainDecimalString;
  grade: "A" | "B" | "C" | "D" | "E";
  evaluationComment: string;
  recommendBlacklist: number;
  status: "DRAFT" | "CONFIRMED";
}

export interface SupplierReturnRecord {
  id: string;
  projectId: string;
  partnerId: string;
  partnerCode?: string | null;
  partnerName?: string | null;
  contractId: string;
  purchaseOrderId: string;
  receiptId: string;
  returnCode: string;
  returnDate: string;
  returnQuantity: SupplyChainDecimalString;
  returnAmount: SupplyChainDecimalString;
  reason: string;
  status: "CONFIRMED" | "REVERSED";
}

export interface SupplierSourcingWorkspacePage {
  events: PageResult<SourcingEventRecord>;
  performance: PageResult<SupplierPerformanceRecord>;
  returns: PageResult<SupplierReturnRecord>;
}

export interface SupplierSourcingWorkspaceQuery {
  eventPageNo?: number;
  performancePageNo?: number;
  returnPageNo?: number;
  pageSize?: number;
  projectId?: string;
}

export interface SupplierPerformanceCandidateRecord {
  id: string;
  projectId: string;
  orderCode: string;
  partnerId: string;
  partnerCode: string;
  partnerName: string;
}

export type SupplierPerformanceCandidatePage =
  PageResult<SupplierPerformanceCandidateRecord>;

export interface SupplierBlacklistRecord {
  id: string;
  performanceEvaluationId: string;
  partnerId: string;
  projectId: string;
  actionType: "ADD";
  reason: string;
  status: "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";
  reviewComment?: string | null;
}

export interface SourcingTraceRecord {
  event: SourcingEventRecord;
  purchaseRequest: { id: string; requestCode?: string; requestName?: string };
  invitedSuppliers: SourcingSupplierRecord[];
  quotes: SupplierQuoteRecord[];
  bidEvaluations: BidEvaluationRecord[];
  contract?: {
    id: string;
    contractCode?: string;
    contractName?: string;
  } | null;
  purchaseOrders: Array<{ id: string; orderCode?: string }>;
  receipts: Array<{ id: string; receiptCode?: string }>;
  supplierReturns: SupplierReturnRecord[];
  settlements: Array<{ id: string; settlementCode?: string }>;
  performanceEvaluations: SupplierPerformanceRecord[];
  blacklistRecords: SupplierBlacklistRecord[];
  qualitySafetyFacts: Array<{
    id: string;
    evaluationType?: string;
    score?: SupplyChainDecimalString;
  }>;
}

export interface SourcingEventCommand {
  projectId: string;
  purchaseRequestId: string;
  sourcingCode?: string;
  sourcingTitle: string;
  sourcingType: "INQUIRY" | "TENDER";
  deadline: string;
  currencyCode: string;
  remark?: string;
}

export interface SupplierQuoteCommand {
  sourcingEventId: string;
  partnerId: string;
  quoteCode: string;
  totalAmount: SupplyChainDecimalString;
  taxRate: SupplyChainDecimalString;
  deliveryDays: number;
  validityDate: string;
  commercialTerms: string;
  remark?: string;
}

export interface BidEvaluationCommand {
  quoteId: string;
  commercialScore: SupplyChainDecimalString;
  technicalScore: SupplyChainDecimalString;
  deliveryScore: SupplyChainDecimalString;
  qualityScore: SupplyChainDecimalString;
  evaluationComment: string;
}
