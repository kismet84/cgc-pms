import {
  COMMERCIAL_API,
  type ContractApprovalRecord,
  type ContractCompositeRecord,
  type ContractItemRecord,
  type ContractKpi,
  type ContractPaymentTermRecord,
  type ContractPage,
  type ContractQuery,
  type ContractRecord,
  type ContractSaveCommand,
  type BidCostPage,
  type BidCostQuery,
  type BidCostRecord,
  type BidCostSaveCommand,
  type CostTargetItemRecord,
  type CostTargetPage,
  type CostTargetQuery,
  type CostTargetRecord,
  type CostTargetSaveCommand,
  type CostSummaryHistoryRecord,
  type CostProjectSummary,
  type CostLedgerPage,
  type CostLedgerQuery,
  type CostLedgerRecord,
  type CostLedgerSummary,
  type CostControlOverview,
  type CostControlAmountRow,
  type CostForecastCommand,
  type CostCorrectiveCommand,
  type CostCorrectiveCloseCommand,
  type BudgetAvailabilityRecord,
  type BudgetLineRecord,
  type BudgetPage,
  type BudgetQuery,
  type BudgetSaveCommand,
  type MeasurementAmountRow,
  type MeasurementPeriodCommand,
  type MeasurementSaveCommand,
  type OwnerMeasurementSubmissionCommand,
  type OwnerMeasurementReviewCommand,
  type PartnerQuery,
  type PartnerRecord,
  type ProjectContextOption,
  type VariationItemRecord,
  type VariationOwnerReviewCommand,
  type VariationOwnerSubmissionCommand,
  type VariationOwnerSubmissionRecord,
  type VariationPage,
  type VariationQuery,
  type VariationRecord,
  type VariationSaveCommand,
  type VariationTrace,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

const WRITE_METHOD = {
  create: 'POST' as const,
  update: 'PUT' as const,
  remove: 'DELETE' as const,
  submit: 'POST' as const,
}

export function loadContractPage(
  query: ContractQuery = {},
  signal?: AbortSignal,
): Promise<ContractPage> {
  return apiRequest<ContractPage>(withQuery(COMMERCIAL_API.contracts, query), { signal })
}

export function loadContractKpi(
  query: Omit<ContractQuery, 'pageNo' | 'pageSize' | 'keyword'> = {},
  signal?: AbortSignal,
): Promise<ContractKpi> {
  return apiRequest<ContractKpi>(withQuery(COMMERCIAL_API.contractKpi, query), { signal })
}

export function loadContract(id: string, signal?: AbortSignal): Promise<ContractRecord> {
  return apiRequest<ContractRecord>(COMMERCIAL_API.contract(requiredId(id, '合同ID')), { signal })
}

export function loadContractItems(id: string, signal?: AbortSignal): Promise<ContractItemRecord[]> {
  return apiRequest<ContractItemRecord[]>(COMMERCIAL_API.contractItems(requiredId(id, '合同ID')), {
    signal,
  })
}

export function loadContractPaymentTerms(
  id: string,
  signal?: AbortSignal,
): Promise<ContractPaymentTermRecord[]> {
  return apiRequest<ContractPaymentTermRecord[]>(
    COMMERCIAL_API.contractPaymentTerms(requiredId(id, '合同ID')),
    { signal },
  )
}

export function loadContractApprovalRecords(
  id: string,
  signal?: AbortSignal,
): Promise<ContractApprovalRecord[]> {
  return apiRequest<ContractApprovalRecord[]>(
    COMMERCIAL_API.contractApprovalRecords(requiredId(id, '合同ID')),
    { signal },
  )
}

export async function loadContractComposite(
  id: string,
  signal?: AbortSignal,
): Promise<ContractCompositeRecord> {
  const contractId = requiredId(id, '合同ID')
  const [contract, items, paymentTerms, approvalRecords] = await Promise.all([
    loadContract(contractId, signal),
    loadContractItems(contractId, signal),
    loadContractPaymentTerms(contractId, signal),
    loadContractApprovalRecords(contractId, signal),
  ])
  return { contract, items, paymentTerms, approvalRecords }
}

export function createContractComposite(command: ContractSaveCommand): Promise<string> {
  return apiRequest<string, ContractSaveCommand>(COMMERCIAL_API.contractCompositeCreate, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function updateContractComposite(id: string, command: ContractSaveCommand): Promise<void> {
  return apiRequest<void, ContractSaveCommand>(
    COMMERCIAL_API.contractCompositeUpdate(requiredId(id, '合同ID')),
    {
      method: WRITE_METHOD.update,
      body: command,
    },
  )
}

export function submitContract(id: string, version?: string | number | null): Promise<void> {
  const params = new URLSearchParams()
  const normalizedVersion = String(version ?? '').trim()
  if (normalizedVersion) params.set('version', normalizedVersion)
  const path = `${COMMERCIAL_API.contractSubmit(requiredId(id, '合同ID'))}${
    params.size ? `?${params.toString()}` : ''
  }`
  return apiRequest<void>(path, {
    method: WRITE_METHOD.submit,
  })
}

export function deleteContract(id: string): Promise<void> {
  return apiRequest<void>(COMMERCIAL_API.contract(requiredId(id, '合同ID')), {
    method: WRITE_METHOD.remove,
  })
}

export function loadPartners(
  query: PartnerQuery = { pageNo: 1, pageSize: 200, status: 'ENABLE' },
  signal?: AbortSignal,
): Promise<{ records: PartnerRecord[] }> {
  return apiRequest<{ records: PartnerRecord[] }>(
    withPartnerQuery(COMMERCIAL_API.partners, query),
    {
      signal,
    },
  )
}

export function loadProjectContextOptions(signal?: AbortSignal): Promise<ProjectContextOption[]> {
  return apiRequest<ProjectContextOption[]>(COMMERCIAL_API.projectContextOptions, { signal })
}

export function loadVariationPage(
  query: VariationQuery = {},
  signal?: AbortSignal,
): Promise<VariationPage> {
  return apiRequest<VariationPage>(withSearchParams(COMMERCIAL_API.variations, query), { signal })
}

export function loadVariation(id: string, signal?: AbortSignal): Promise<VariationRecord> {
  return apiRequest<VariationRecord>(COMMERCIAL_API.variation(requiredId(id, '变更ID')), { signal })
}

export function loadVariationTrace(id: string, signal?: AbortSignal): Promise<VariationTrace> {
  return apiRequest<VariationTrace>(COMMERCIAL_API.variationTrace(requiredId(id, '变更ID')), {
    signal,
  })
}

export function createVariation(command: VariationSaveCommand): Promise<string> {
  return apiRequest<string, VariationSaveCommand>(COMMERCIAL_API.variations, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function updateVariation(id: string, command: VariationSaveCommand): Promise<void> {
  return apiRequest<void, VariationSaveCommand>(
    COMMERCIAL_API.variation(requiredId(id, '变更ID')),
    {
      method: WRITE_METHOD.update,
      body: command,
    },
  )
}

export function deleteVariation(id: string, version: string | number): Promise<void> {
  return apiRequest<void>(
    withVersion(COMMERCIAL_API.variation(requiredId(id, '变更ID')), version),
    {
      method: WRITE_METHOD.remove,
    },
  )
}

export function saveVariationItems(
  id: string,
  items: VariationItemRecord[],
  version: string | number,
): Promise<void> {
  return apiRequest<void, VariationItemRecord[]>(
    withVersion(COMMERCIAL_API.variationItems(requiredId(id, '变更ID')), version),
    { method: WRITE_METHOD.create, body: items },
  )
}

export function submitVariation(id: string, version: string | number): Promise<void> {
  return apiRequest<void>(
    withVersion(COMMERCIAL_API.variationSubmit(requiredId(id, '变更ID')), version),
    { method: WRITE_METHOD.submit },
  )
}

export function submitVariationToOwner(
  id: string,
  command: VariationOwnerSubmissionCommand,
  version: string | number,
): Promise<VariationOwnerSubmissionRecord> {
  return apiRequest<VariationOwnerSubmissionRecord, VariationOwnerSubmissionCommand>(
    withVersion(COMMERCIAL_API.variationOwnerSubmissions(requiredId(id, '变更ID')), version),
    { method: WRITE_METHOD.create, body: command },
  )
}

export function reviewVariationOwner(
  id: string,
  submissionId: string,
  command: VariationOwnerReviewCommand,
  version: string | number,
): Promise<VariationOwnerSubmissionRecord> {
  return apiRequest<VariationOwnerSubmissionRecord, VariationOwnerReviewCommand>(
    withVersion(
      COMMERCIAL_API.variationOwnerReview(
        requiredId(id, '变更ID'),
        requiredId(submissionId, '申报ID'),
      ),
      version,
    ),
    { method: WRITE_METHOD.create, body: command },
  )
}

export function loadBidCostPage(
  query: BidCostQuery = {},
  signal?: AbortSignal,
): Promise<BidCostPage> {
  return apiRequest<BidCostPage>(withSearchParams(COMMERCIAL_API.bidCosts, query), { signal })
}

export function loadBidCost(id: string, signal?: AbortSignal): Promise<BidCostRecord> {
  return apiRequest<BidCostRecord>(COMMERCIAL_API.bidCost(requiredId(id, '投标成本ID')), { signal })
}

export function createBidCost(command: BidCostSaveCommand): Promise<string> {
  return apiRequest<string, BidCostSaveCommand>(COMMERCIAL_API.bidCosts, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function updateBidCost(id: string, command: BidCostSaveCommand): Promise<void> {
  return apiRequest<void, BidCostSaveCommand>(
    COMMERCIAL_API.bidCost(requiredId(id, '投标成本ID')),
    {
      method: WRITE_METHOD.update,
      body: command,
    },
  )
}

export function deleteBidCost(id: string): Promise<void> {
  return apiRequest<void>(COMMERCIAL_API.bidCost(requiredId(id, '投标成本ID')), {
    method: WRITE_METHOD.remove,
  })
}

export function markBidCostWon(id: string, projectId: string): Promise<void> {
  const path = `${COMMERCIAL_API.bidWon(requiredId(id, '投标成本ID'))}?projectId=${encodeURIComponent(requiredId(projectId, '项目ID'))}`
  return apiRequest<void>(path, { method: WRITE_METHOD.update })
}

export function markBidCostLost(id: string): Promise<void> {
  return apiRequest<void>(COMMERCIAL_API.bidLost(requiredId(id, '投标成本ID')), {
    method: WRITE_METHOD.update,
  })
}

export function loadCostSummaryHistory(
  projectId: string,
  signal?: AbortSignal,
): Promise<CostSummaryHistoryRecord[]> {
  return apiRequest<CostSummaryHistoryRecord[]>(
    COMMERCIAL_API.costSummaryHistory(requiredId(projectId, '项目ID')),
    { signal },
  )
}

export function loadCostLedgerPage(
  query: CostLedgerQuery = {},
  signal?: AbortSignal,
): Promise<CostLedgerPage> {
  return apiRequest<CostLedgerPage>(withSearchParams(COMMERCIAL_API.costLedger, query), { signal })
}

export function loadCostLedgerSummary(
  query: Omit<CostLedgerQuery, 'pageNo' | 'pageSize'> = {},
  signal?: AbortSignal,
): Promise<CostLedgerSummary> {
  return apiRequest<CostLedgerSummary>(withSearchParams(COMMERCIAL_API.costLedgerSummary, query), {
    signal,
  })
}

export function loadCostLedger(id: string, signal?: AbortSignal): Promise<CostLedgerRecord> {
  return apiRequest<CostLedgerRecord>(
    COMMERCIAL_API.costLedgerDetail(requiredId(id, '成本台账ID')),
    { signal },
  )
}

export function loadCostSummary(
  projectId: string,
  signal?: AbortSignal,
): Promise<CostProjectSummary> {
  return apiRequest<CostProjectSummary>(
    COMMERCIAL_API.costSummary(requiredId(projectId, '项目ID')),
    { signal },
  )
}

export function refreshCostSummary(projectId: string): Promise<CostProjectSummary> {
  return apiRequest<CostProjectSummary>(
    COMMERCIAL_API.costSummaryRefresh(requiredId(projectId, '项目ID')),
    { method: WRITE_METHOD.create },
  )
}

export function loadCostControl(
  projectId: string,
  signal?: AbortSignal,
): Promise<CostControlOverview> {
  return apiRequest<CostControlOverview>(
    COMMERCIAL_API.costControl(requiredId(projectId, '项目ID')),
    { signal },
  )
}

export function loadCostForecastTrace(
  id: string,
  signal?: AbortSignal,
): Promise<CostControlOverview> {
  return apiRequest<CostControlOverview>(
    COMMERCIAL_API.costForecastTrace(requiredId(id, '预测ID')),
    { signal },
  )
}

export function createCostForecast(command: CostForecastCommand): Promise<CostControlAmountRow> {
  return apiRequest<CostControlAmountRow, CostForecastCommand>(COMMERCIAL_API.costForecasts, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function updateCostForecast(
  id: string,
  command: CostForecastCommand,
): Promise<CostControlAmountRow> {
  return apiRequest<CostControlAmountRow, CostForecastCommand>(
    withVersion(COMMERCIAL_API.costForecast(requiredId(id, '预测ID')), command.version ?? ''),
    { method: WRITE_METHOD.update, body: command },
  )
}

export function confirmCostForecast(
  id: string,
  version: string | number,
): Promise<CostControlAmountRow> {
  return apiRequest<CostControlAmountRow>(
    withVersion(COMMERCIAL_API.costForecastConfirm(requiredId(id, '预测ID')), version),
    { method: WRITE_METHOD.submit },
  )
}

export function createCostCorrective(
  command: CostCorrectiveCommand,
): Promise<CostControlAmountRow> {
  return apiRequest<CostControlAmountRow, CostCorrectiveCommand>(
    COMMERCIAL_API.costCorrectiveActions,
    { method: WRITE_METHOD.create, body: command },
  )
}

export function updateCostCorrective(
  id: string,
  command: CostCorrectiveCommand,
): Promise<CostControlAmountRow> {
  return apiRequest<CostControlAmountRow, CostCorrectiveCommand>(
    withVersion(
      COMMERCIAL_API.costCorrectiveAction(requiredId(id, '纠偏措施ID')),
      command.version ?? '',
    ),
    { method: WRITE_METHOD.update, body: command },
  )
}

export function submitCostCorrective(
  id: string,
  version: string | number,
): Promise<CostControlAmountRow> {
  return apiRequest<CostControlAmountRow>(
    withVersion(COMMERCIAL_API.costCorrectiveSubmit(requiredId(id, '纠偏措施ID')), version),
    { method: WRITE_METHOD.submit },
  )
}

export function closeCostCorrective(
  id: string,
  command: CostCorrectiveCloseCommand,
): Promise<CostControlAmountRow> {
  return apiRequest<CostControlAmountRow, CostCorrectiveCloseCommand>(
    withVersion(COMMERCIAL_API.costCorrectiveClose(requiredId(id, '纠偏措施ID')), command.version),
    { method: WRITE_METHOD.submit, body: command },
  )
}

export function loadCostTargetPage(
  query: CostTargetQuery = {},
  signal?: AbortSignal,
): Promise<CostTargetPage> {
  return apiRequest<CostTargetPage>(withSearchParams(COMMERCIAL_API.costTargets, query), {
    signal,
  })
}

export function loadCostTarget(id: string, signal?: AbortSignal): Promise<CostTargetRecord> {
  return apiRequest<CostTargetRecord>(COMMERCIAL_API.costTarget(requiredId(id, '目标成本ID')), {
    signal,
  })
}

export function loadCostTargetItems(
  id: string,
  signal?: AbortSignal,
): Promise<CostTargetItemRecord[]> {
  return apiRequest<CostTargetItemRecord[]>(
    COMMERCIAL_API.costTargetItems(requiredId(id, '目标成本ID')),
    { signal },
  )
}

export function createCostTarget(command: CostTargetSaveCommand): Promise<string> {
  return apiRequest<string, CostTargetSaveCommand>(COMMERCIAL_API.costTargets, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function updateCostTarget(id: string, command: CostTargetSaveCommand): Promise<void> {
  return apiRequest<void, CostTargetSaveCommand>(
    COMMERCIAL_API.costTarget(requiredId(id, '目标成本ID')),
    {
      method: WRITE_METHOD.update,
      body: command,
    },
  )
}

export function saveCostTargetItems(
  id: string,
  items: CostTargetItemRecord[],
  version: string | number,
): Promise<void> {
  return apiRequest<void, CostTargetItemRecord[]>(
    withVersion(COMMERCIAL_API.costTargetItems(requiredId(id, '目标成本ID')), version),
    { method: WRITE_METHOD.create, body: items },
  )
}

export function submitCostTarget(id: string, version: string | number): Promise<void> {
  return apiRequest<void>(
    withVersion(COMMERCIAL_API.costTargetSubmit(requiredId(id, '目标成本ID')), version),
    { method: WRITE_METHOD.submit },
  )
}

export function activateCostTarget(id: string, version: string | number): Promise<void> {
  return apiRequest<void>(
    withVersion(COMMERCIAL_API.costTargetActivate(requiredId(id, '目标成本ID')), version),
    { method: WRITE_METHOD.submit },
  )
}

export function deleteCostTarget(id: string, version: string | number): Promise<void> {
  return apiRequest<void>(
    withVersion(COMMERCIAL_API.costTarget(requiredId(id, '目标成本ID')), version),
    { method: WRITE_METHOD.remove },
  )
}

export function loadBudgetPage(query: BudgetQuery = {}, signal?: AbortSignal): Promise<BudgetPage> {
  return apiRequest<BudgetPage>(withSearchParams(COMMERCIAL_API.budgets, query), { signal })
}

export function loadBudget(id: string, signal?: AbortSignal) {
  return apiRequest<import('@cgc-pms/frontend-contracts').ProjectBudgetRecord>(
    COMMERCIAL_API.budget(requiredId(id, '预算ID')),
    { signal },
  )
}

export function loadBudgetAvailability(id: string, signal?: AbortSignal) {
  return apiRequest<BudgetAvailabilityRecord[]>(
    COMMERCIAL_API.budgetAvailability(requiredId(id, '预算ID')),
    { signal },
  )
}

export function createBudget(command: BudgetSaveCommand): Promise<string> {
  return apiRequest<string, BudgetSaveCommand>(COMMERCIAL_API.budgets, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function updateBudget(id: string, command: BudgetSaveCommand): Promise<void> {
  return apiRequest<void, BudgetSaveCommand>(
    withVersion(COMMERCIAL_API.budget(requiredId(id, '预算ID')), command.version),
    { method: WRITE_METHOD.update, body: command },
  )
}

export function saveBudgetLines(
  id: string,
  lines: BudgetLineRecord[],
  version: string | number,
): Promise<void> {
  return apiRequest<void, BudgetLineRecord[]>(
    withVersion(COMMERCIAL_API.budgetLines(requiredId(id, '预算ID')), version),
    { method: WRITE_METHOD.create, body: lines },
  )
}

export function submitBudget(id: string, version: string | number): Promise<void> {
  return apiRequest<void>(
    withVersion(COMMERCIAL_API.budgetSubmit(requiredId(id, '预算ID')), version),
    {
      method: WRITE_METHOD.submit,
    },
  )
}

export function deleteBudget(id: string, version: string | number): Promise<void> {
  return apiRequest<void>(withVersion(COMMERCIAL_API.budget(requiredId(id, '预算ID')), version), {
    method: WRITE_METHOD.remove,
  })
}

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

function withQuery(path: string, query: ContractQuery): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number') {
      if (Number.isInteger(value) && value > 0) params.set(key, String(value))
    } else if (value?.trim()) {
      params.set(key, value.trim())
    }
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}

function withPartnerQuery(path: string, query: PartnerQuery): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number') {
      if (Number.isInteger(value) && value > 0) params.set(key, String(value))
    } else if (value?.trim()) {
      params.set(key, value.trim())
    }
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}

function withSearchParams(path: string, query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number') {
      if (Number.isInteger(value) && value > 0) params.set(key, String(value))
    } else if (typeof value === 'string' && value.trim()) {
      params.set(key, value.trim())
    }
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}

function withVersion(path: string, version: string | number | null | undefined): string {
  const normalized = String(version ?? '').trim()
  if (!normalized) throw new TypeError('版本不能为空')
  return `${path}?version=${encodeURIComponent(normalized)}`
}

function requiredId(value: string, label: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError(`${label}不能为空`)
  return normalized
}
