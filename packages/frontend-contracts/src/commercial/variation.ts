import type { PageResult } from "../api";
import type { DecimalString } from "./types";

export interface VariationRecord {
  id: string;
  tenantId: string;
  projectId: string;
  contractId?: string | null;
  partnerId?: string | null;
  varCode: string;
  varName: string;
  reportedAmount?: DecimalString | null;
  approvedAmount?: DecimalString | null;
  confirmedAmount?: DecimalString | null;
  estimatedCostAmount?: DecimalString | null;
  eventDate?: string | null;
  claimDeadline?: string | null;
  eventDescription?: string | null;
  causeCategory?: string | null;
  responsibleParty?: string | null;
  businessMatterKey?: string | null;
  varType?: string | null;
  direction?: string | null;
  approvalStatus?: string | null;
  ownerStatus?: string | null;
  ownerConfirmFlag?: number | null;
  internalApprovalInstanceId?: string | null;
  generatedContractChangeId?: string | null;
  impactDays?: number | null;
  costGeneratedFlag?: number | null;
  projectName?: string | null;
  contractName?: string | null;
  partnerName?: string | null;
  version?: string | number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
  items?: VariationItemRecord[];
  ownerSubmissions?: VariationOwnerSubmissionRecord[];
}

export interface VariationItemRecord {
  id?: string | null;
  varOrderId?: string | null;
  itemName: string;
  unit?: string | null;
  quantity: DecimalString;
  unitPrice?: DecimalString | null;
  amount?: DecimalString | null;
  claimUnitPrice?: DecimalString | null;
  claimAmount?: DecimalString | null;
  costSubjectId: string;
  wbsTaskId: string;
  remark?: string | null;
}

export interface VariationOwnerSubmissionItemRecord extends Record<
  string,
  unknown
> {
  id: string;
  item_name?: string | null;
  unit?: string | null;
  quantity?: DecimalString | null;
  claimed_unit_price?: DecimalString | null;
  claimed_amount?: DecimalString | null;
  confirmed_amount?: DecimalString | null;
  reduction_reason?: string | null;
}

export interface VariationOwnerSubmissionRecord extends Record<
  string,
  unknown
> {
  id: string;
  revision_no?: string | number | null;
  submission_code?: string | null;
  external_document_no?: string | null;
  submitted_amount?: DecimalString | null;
  confirmed_amount?: DecimalString | null;
  status?: string | null;
  submitted_at?: string | null;
  response_document_no?: string | null;
  response_comment?: string | null;
  reviewed_at?: string | null;
  items?: VariationOwnerSubmissionItemRecord[];
}

export interface VariationQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  partnerId?: string;
  varType?: string;
  direction?: string;
  varCode?: string;
  startDate?: string;
  endDate?: string;
}

export interface VariationSaveCommand {
  projectId: string;
  contractId: string;
  partnerId?: string | null;
  varName: string;
  eventDate?: string | null;
  claimDeadline?: string | null;
  eventDescription?: string | null;
  causeCategory?: string | null;
  responsibleParty?: string | null;
  businessMatterKey?: string | null;
  varType: string;
  direction?: string | null;
  impactDays?: number | null;
  version?: string | number | null;
  remark?: string | null;
}

export interface VariationOwnerSubmissionCommand {
  externalDocumentNo: string;
  submittedAt: string;
  remark?: string | null;
}

export interface VariationOwnerReviewCommand {
  conclusion: "CONFIRMED" | "RETURNED";
  responseDocumentNo: string;
  responseComment?: string | null;
  reviewedAt: string;
  items: Array<{
    submissionItemId: string;
    confirmedAmount: DecimalString;
    reductionReason?: string | null;
  }>;
}

export type VariationPage = PageResult<VariationRecord>;
export type VariationTrace = Record<string, unknown>;
