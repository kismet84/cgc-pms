import {
  COMMERCIAL_API,
  type MeasurementAmountRow,
  type MeasurementPeriodCommand,
  type MeasurementSaveCommand,
  type OwnerMeasurementReviewCommand,
  type OwnerMeasurementSubmissionCommand,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId, withSearchParams, withVersion, WRITE_METHOD } from './support'

export function loadMeasurementPeriods(query: object, signal?: AbortSignal) {
  return apiRequest<MeasurementAmountRow[]>(
    withSearchParams(COMMERCIAL_API.measurementPeriods, query),
    { signal },
  )
}

export function loadMeasurementSources(
  projectId: string,
  contractId: string,
  signal?: AbortSignal,
) {
  return apiRequest<MeasurementAmountRow[]>(
    withSearchParams(COMMERCIAL_API.measurementSources, {
      projectId: requiredId(projectId, '项目ID'),
      contractId: requiredId(contractId, '合同ID'),
    }),
    { signal },
  )
}

export function loadMeasurements(query: object, signal?: AbortSignal) {
  return apiRequest<MeasurementAmountRow[]>(withSearchParams(COMMERCIAL_API.measurements, query), {
    signal,
  })
}

export function loadMeasurement(id: string, signal?: AbortSignal) {
  return apiRequest<MeasurementAmountRow>(COMMERCIAL_API.measurement(requiredId(id, '计量ID')), {
    signal,
  })
}

export function loadOwnerMeasurementSubmissions(query: object, signal?: AbortSignal) {
  return apiRequest<MeasurementAmountRow[]>(
    withSearchParams(COMMERCIAL_API.ownerMeasurementSubmissions, query),
    { signal },
  )
}

export function loadOwnerMeasurementSubmission(id: string, signal?: AbortSignal) {
  return apiRequest<MeasurementAmountRow>(
    COMMERCIAL_API.ownerMeasurementSubmission(requiredId(id, '业主报量ID')),
    { signal },
  )
}

export function createMeasurementPeriod(command: MeasurementPeriodCommand) {
  return apiRequest<MeasurementAmountRow, MeasurementPeriodCommand>(
    COMMERCIAL_API.measurementPeriods,
    { method: WRITE_METHOD.create, body: command },
  )
}

export function closeMeasurementPeriod(id: string, version: string | number) {
  return apiRequest<MeasurementAmountRow>(
    withVersion(COMMERCIAL_API.measurementPeriodClose(requiredId(id, '计量期间ID')), version),
    { method: WRITE_METHOD.submit },
  )
}

export function createMeasurement(command: MeasurementSaveCommand) {
  return apiRequest<MeasurementAmountRow, MeasurementSaveCommand>(COMMERCIAL_API.measurements, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function submitMeasurement(id: string, version: string | number) {
  return apiRequest<MeasurementAmountRow>(
    withVersion(COMMERCIAL_API.measurementSubmit(requiredId(id, '计量ID')), version),
    { method: WRITE_METHOD.submit },
  )
}

export function submitOwnerMeasurement(id: string, command: OwnerMeasurementSubmissionCommand) {
  return apiRequest<MeasurementAmountRow, OwnerMeasurementSubmissionCommand>(
    withVersion(COMMERCIAL_API.ownerMeasurementSubmit(requiredId(id, '计量ID')), command.version),
    { method: WRITE_METHOD.submit, body: command },
  )
}

export function reviewOwnerMeasurement(id: string, command: OwnerMeasurementReviewCommand) {
  return apiRequest<MeasurementAmountRow, OwnerMeasurementReviewCommand>(
    withVersion(
      COMMERCIAL_API.ownerMeasurementReview(requiredId(id, '业主报量ID')),
      command.version,
    ),
    { method: WRITE_METHOD.submit, body: command },
  )
}

export function loadMeasurementSettlementTrace(id: string, signal?: AbortSignal) {
  return apiRequest<MeasurementAmountRow>(
    COMMERCIAL_API.measurementSettlementTrace(requiredId(id, '结算ID')),
    { signal },
  )
}
