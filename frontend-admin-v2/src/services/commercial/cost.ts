import {
  COMMERCIAL_API,
  type AccessibleCostControlOverview,
  type AccessibleCostSummary,
  type BudgetAvailabilityRecord,
  type BudgetLineRecord,
  type BudgetPage,
  type BudgetQuery,
  type BudgetSaveCommand,
  type CostBudgetDraftSaveCommand,
  type CostControlAmountRow,
  type CostControlOverview,
  type CostCorrectiveCloseCommand,
  type CostCorrectiveCommand,
  type CostCorrectiveOwnerOption,
  type CostForecastCommand,
  type CostLedgerPage,
  type CostLedgerQuery,
  type CostLedgerRecord,
  type CostLedgerSummary,
  type CostProjectSummary,
  type CostSummaryHistoryRecord,
  type CostTargetDefaultAllocation,
  type CostTargetItemRecord,
  type CostTargetPage,
  type CostTargetProjectManagerOption,
  type CostTargetQuery,
  type CostTargetRecord,
  type CostTargetSaveCommand,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId, withSearchParams, withVersion, WRITE_METHOD } from './support'

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

export function loadAccessibleCostSummary(signal?: AbortSignal): Promise<AccessibleCostSummary> {
  return apiRequest<AccessibleCostSummary>(COMMERCIAL_API.accessibleCostSummary, { signal })
}

export function refreshCostSummary(projectId: string): Promise<CostProjectSummary> {
  return apiRequest<CostProjectSummary>(
    COMMERCIAL_API.costSummaryRefresh(requiredId(projectId, '项目ID')),
    { method: WRITE_METHOD.create },
  )
}

export function loadAccessibleCostControl(
  signal?: AbortSignal,
): Promise<AccessibleCostControlOverview> {
  return apiRequest<AccessibleCostControlOverview>(COMMERCIAL_API.accessibleCostControl, { signal })
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

export function loadCostCorrectiveOwnerOptions(
  projectId: string,
  signal?: AbortSignal,
): Promise<CostCorrectiveOwnerOption[]> {
  return apiRequest<CostCorrectiveOwnerOption[]>(
    COMMERCIAL_API.costCorrectiveOwnerOptions(requiredId(projectId, '项目ID')),
    { signal, notifyError: false },
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

export function loadCostTargetDefaultAllocation(
  projectId: string,
  signal?: AbortSignal,
): Promise<CostTargetDefaultAllocation> {
  return apiRequest<CostTargetDefaultAllocation>(
    `${COMMERCIAL_API.costTargetDefaultAllocation}?projectId=${encodeURIComponent(requiredId(projectId, '项目ID'))}`,
    { signal },
  )
}

export function loadCostTargetProjectManagerOptions(
  projectId: string,
  signal?: AbortSignal,
): Promise<CostTargetProjectManagerOption[]> {
  return apiRequest<CostTargetProjectManagerOption[]>(
    withSearchParams(COMMERCIAL_API.costTargetProjectManagerOptions, { projectId }),
    { signal },
  )
}

export function createCostTarget(command: CostTargetSaveCommand): Promise<string> {
  return apiRequest<string, CostTargetSaveCommand>(COMMERCIAL_API.costTargets, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function saveCostBudgetDraft(
  id: string | null,
  command: CostBudgetDraftSaveCommand,
): Promise<string> {
  return apiRequest<string, CostBudgetDraftSaveCommand>(
    id
      ? `${COMMERCIAL_API.costTarget(requiredId(id, '项目成本预算ID'))}/draft`
      : `${COMMERCIAL_API.costTargets}/drafts`,
    { method: id ? WRITE_METHOD.update : WRITE_METHOD.create, body: command },
  )
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
