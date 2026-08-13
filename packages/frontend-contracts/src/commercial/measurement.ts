import type { DecimalString } from "./types";

export interface MeasurementAmountRow extends Record<string, unknown> {
  id?: string;
  project_id?: string;
  contract_id?: string;
  period_id?: string;
  version?: string | number | null;
  unit_price?: DecimalString | null;
  current_reported_amount?: DecimalString | null;
  cumulative_reported_amount?: DecimalString | null;
  submitted_amount?: DecimalString | null;
  confirmed_amount?: DecimalString | null;
  deducted_amount?: DecimalString | null;
  tax_amount?: DecimalString | null;
  retention_amount?: DecimalString | null;
  current_reported_quantity?: DecimalString | null;
  submitted_quantity?: DecimalString | null;
  confirmed_quantity?: DecimalString | null;
  remainingQuantity?: DecimalString | null;
}

export interface MeasurementPeriodCommand {
  projectId: string;
  contractId: string;
  periodCode: string;
  periodName: string;
  startDate: string;
  endDate: string;
  cutoffDate: string;
  remark?: string | null;
}

export interface MeasurementLineCommand {
  contractItemId?: string | null;
  contractChangeId?: string | null;
  currentQuantity: DecimalString;
  evidenceCount: number;
}

export interface MeasurementSaveCommand {
  projectId: string;
  contractId: string;
  periodId: string;
  measureDate: string;
  attachmentCount: number;
  lines: MeasurementLineCommand[];
  remark?: string | null;
}

export interface OwnerMeasurementSubmissionCommand {
  externalDocumentNo?: string | null;
  attachmentCount: number;
  remark?: string | null;
  version: string | number;
}

export interface OwnerMeasurementReviewLineCommand {
  measurementLineId: string;
  confirmedQuantity: DecimalString;
  deductionReason?: string | null;
}

export interface OwnerMeasurementReviewCommand {
  decision: "CONFIRMED" | "RETURNED";
  reviewerName: string;
  reviewComment?: string | null;
  settlementDate?: string | null;
  dueDate?: string | null;
  taxAmount?: DecimalString | null;
  retentionAmount?: DecimalString | null;
  attachmentCount?: number | null;
  lines: OwnerMeasurementReviewLineCommand[];
  version: string | number;
}

export interface ProductionMeasurementAmountRow extends Record<
  string,
  unknown
> {
  unit_price?: DecimalString | null;
  current_reported_amount?: DecimalString | null;
  cumulative_reported_amount?: DecimalString | null;
  submitted_amount?: DecimalString | null;
  confirmed_amount?: DecimalString | null;
  gross_amount?: DecimalString | null;
  deducted_amount?: DecimalString | null;
  tax_amount?: DecimalString | null;
  retention_amount?: DecimalString | null;
  original_amount?: DecimalString | null;
  reported_amount?: DecimalString | null;
}
