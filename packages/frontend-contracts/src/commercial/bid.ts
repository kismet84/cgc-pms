import type { PageResult } from "../api";
import type { BidStatus, DecimalString } from "./types";

export interface BidCostRecord {
  id: string;
  projectId?: string | null;
  bidCode: string;
  bidProjectName: string;
  bidStatus: BidStatus;
  bidSectionName?: string | null;
  tendereeName?: string | null;
  agencyName?: string | null;
  projectLocation?: string | null;
  tenderMethod?: string | null;
  sourcePlatform?: string | null;
  externalBidNo?: string | null;
  sourceUrl?: string | null;
  ownerId?: string | null;
  ownerName?: string | null;
  documentReceivedDate?: string | null;
  bidDeadlineAt?: string | null;
  openingAt?: string | null;
  bidValidUntil?: string | null;
  plannedStartDate?: string | null;
  plannedEndDate?: string | null;
  ceilingPrice?: DecimalString | null;
  finalBidPrice?: DecimalString | null;
  resultAt?: string | null;
  resultReason?: string | null;
  bidExpense?: DecimalString | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
}

export interface BidCostQuery {
  pageNo?: number;
  pageSize?: number;
  bidStatus?: BidStatus;
  result?: string;
  ownerId?: string;
  deadlineFrom?: string;
  deadlineTo?: string;
  keyword?: string;
  projectId?: string;
  startDate?: string;
  endDate?: string;
}

export interface BidCostSaveCommand {
  bidProjectName: string;
  bidSectionName?: string | null;
  tendereeName?: string | null;
  agencyName?: string | null;
  projectLocation?: string | null;
  tenderMethod?: string | null;
  sourcePlatform?: string | null;
  externalBidNo?: string | null;
  sourceUrl?: string | null;
  ownerId?: string | null;
  documentReceivedDate?: string | null;
  bidDeadlineAt?: string | null;
  openingAt?: string | null;
  bidValidUntil?: string | null;
  plannedStartDate?: string | null;
  plannedEndDate?: string | null;
  ceilingPrice?: DecimalString | null;
  finalBidPrice?: DecimalString | null;
  remark?: string | null;
}

export interface BidDocumentVersionRecord {
  id: string;
  bidCostId: string;
  documentGroup: "TENDER" | "SUBMISSION" | "RESULT";
  documentType: string;
  logicalName: string;
  versionNo: number;
  supersedesId?: string | null;
  sysFileId: string;
  status: "DRAFT" | "FINAL" | "SUPERSEDED" | "VOID";
  contentSha256: string;
  sourceName?: string | null;
  sourceUrl?: string | null;
  publishedAt?: string | null;
  receivedAt?: string | null;
  submittedAt?: string | null;
  externalReceiptNo?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
  remark?: string | null;
}

export interface BidDocumentCreateCommand {
  documentGroup: BidDocumentVersionRecord["documentGroup"];
  documentType: string;
  logicalName: string;
  sysFileId: string;
  sourceName?: string | null;
  sourceUrl?: string | null;
  publishedAt?: string | null;
  receivedAt?: string | null;
  submittedAt?: string | null;
  externalReceiptNo?: string | null;
}

export type BidCostPage = PageResult<BidCostRecord>;
