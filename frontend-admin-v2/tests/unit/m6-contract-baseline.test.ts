import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  FINANCE_DECIMAL_FIELDS,
  FINANCE_QUERY_PERMISSIONS,
  SUBCONTRACT_DECIMAL_FIELDS,
  SUBCONTRACT_QUERY_PERMISSIONS,
} from '@cgc-pms/frontend-contracts'
import {
  loadAccountingEntries,
  loadAccountingEntryDetail,
  loadCashForecastCycles,
  loadCashJournal,
  loadExpenseApplications,
  loadFinanceOperationsWorkspace,
  loadFinancePeriods,
  loadInvoices,
  loadPaymentApplications,
} from '@/services/finance'
import {
  loadSettlements,
  loadSubcontractMeasures,
  loadSubcontractTasks,
} from '@/services/subcontract'

const fetchMock = vi.fn<typeof fetch>()

function apiResponse<T>(data: T, status = 200): Response {
  return new Response(JSON.stringify({ code: status === 200 ? '0' : String(status), data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockImplementation(async () =>
    apiResponse({ records: [], total: 0, pageNo: 1, pageSize: 20 }),
  )
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

describe('M6 contract and read-only canary baseline', () => {
  it('freezes controller and menu query permissions without administrator fallback', () => {
    expect(SUBCONTRACT_QUERY_PERMISSIONS).toEqual({
      task: 'subtask:query',
      measure: 'subcontract:measure:query',
      settlement: 'settlement:query',
    })
    expect(FINANCE_QUERY_PERMISSIONS).toEqual({
      payment: 'payment:app:query',
      expense: 'expense:query',
      revenue: 'revenue:operations:query',
      invoice: 'invoice:query',
      operations: 'finance:operations:query',
      journal: 'cashbook:journal:query',
      forecast: 'finance:forecast:query',
      accounting: 'accounting:query',
      close: 'finance:close:query',
    })

    const catalog = readFileSync(resolve('src/navigation/catalog.ts'), 'utf-8')
    for (const permission of [
      ...Object.values(SUBCONTRACT_QUERY_PERMISSIONS),
      ...Object.values(FINANCE_QUERY_PERMISSIONS),
    ])
      expect(catalog).toContain(`permission: '${permission}'`)
    expect(catalog).not.toContain("permission: 'subcontract:task:query'")
  })

  it('sends only encoded GET canaries with abort signals', async () => {
    const signal = new AbortController().signal
    await loadSubcontractTasks({ projectId: ' P/1 ', taskName: ' A&B ' }, signal)
    await loadSubcontractMeasures({ contractId: ' C/1 ', measureCode: ' M&1 ' }, signal)
    await loadSettlements(
      { projectId: ' P/1 ', settlementType: ' FINAL ', keyword: ' A&B ' },
      signal,
    )
    await loadPaymentApplications({ contractId: ' C/1 ', applyCode: ' PAY&1 ' }, signal)
    await loadCashJournal({ projectId: ' P/1 ', hasAttachment: false }, signal)
    await loadAccountingEntries({ entryType: ' PAYMENT ', pageNo: 2 }, signal)
    await loadAccountingEntryDetail(' 9007199254740993 ', signal)
    await loadFinanceOperationsWorkspace(' P/1 ', signal)
    await loadCashForecastCycles(' P/1 ', signal)
    await loadFinancePeriods(2031, signal)

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/sub-tasks?projectId=P%2F1&taskName=A%26B',
      '/api/sub-measures?contractId=C%2F1&measureCode=M%261',
      '/api/settlements?projectId=P%2F1&settlementType=FINAL&keyword=A%26B',
      '/api/pay-applications?contractId=C%2F1&applyCode=PAY%261',
      '/api/cash-journal-entries?projectId=P%2F1&hasAttachment=false',
      '/api/accounting-entry/workspace?entryType=PAYMENT&pageNo=2',
      '/api/accounting-entry/workspace/9007199254740993',
      '/api/finance-operations/workspace?projectId=P%2F1',
      '/api/cash-forecasts/workspace?projectId=P%2F1',
      '/api/financial-close/workspace?year=2031',
    ])
    for (const [, options] of fetchMock.mock.calls)
      expect(options).toMatchObject({ method: 'GET', body: undefined, signal })
  })

  it('keeps large values, zero and null as server-returned strings', async () => {
    fetchMock
      .mockImplementationOnce(async () =>
        apiResponse({
          records: [
            {
              id: '1',
              tenantId: '0',
              projectId: '2',
              applyCode: 'PAY-1',
              applyAmount: '9007199254740993.01',
              approvedAmount: '0',
              actualPayAmount: '0.00',
              payType: 'PROGRESS',
              payStatus: 'PENDING',
              approvalStatus: 'DRAFT',
              integrityVersion: 'PAYMENT_SOURCE_V1',
            },
          ],
          total: 1,
          pageNo: 1,
          pageSize: 20,
        }),
      )
      .mockImplementationOnce(async () =>
        apiResponse({
          records: [
            {
              id: '3',
              tenantId: '0',
              projectId: '2',
              settlementCode: 'STL-1',
              contractAmount: null,
              changeAmount: '0',
              measuredAmount: '9007199254740993.0001',
              deductionAmount: '0.00',
              paidAmount: '0',
              finalAmount: '9007199254740993.0001',
              unpaidAmount: '9007199254740993.0001',
              warrantyAmount: '0',
              amountFormulaVersion: 'SETTLEMENT_V1',
              approvalStatus: 'DRAFT',
              settlementStatus: 'DRAFT',
            },
          ],
          total: 1,
          pageNo: 1,
          pageSize: 20,
        }),
      )

    await expect(loadPaymentApplications()).resolves.toMatchObject({
      records: [{ applyAmount: '9007199254740993.01', approvedAmount: '0' }],
    })
    await expect(loadSettlements()).resolves.toMatchObject({
      records: [
        {
          contractAmount: null,
          measuredAmount: '9007199254740993.0001',
          deductionAmount: '0.00',
        },
      ],
    })
    expect(Object.values(SUBCONTRACT_DECIMAL_FIELDS).flat()).toContain('finalAmount')
    expect(Object.values(FINANCE_DECIMAL_FIELDS).flat()).toContain('runningBalance')
  })

  it.each([
    ['expense 403', 403, () => loadExpenseApplications()],
    ['journal 404', 404, () => loadCashJournal()],
    ['invoice 422', 422, () => loadInvoices()],
    ['payment 500', 500, () => loadPaymentApplications()],
    ['accounting 403', 403, () => loadAccountingEntries()],
  ])('preserves %s as a typed request failure', async (_name, status, request) => {
    fetchMock.mockImplementationOnce(async () => apiResponse(null, status))
    await expect(request()).rejects.toMatchObject({ code: String(status), status })
  })

  it('keeps M6 contracts and services UI-free and Legacy-free', () => {
    const sources = [
      readFileSync(resolve('../packages/frontend-contracts/src/subcontract.ts'), 'utf-8'),
      readFileSync(resolve('../packages/frontend-contracts/src/finance.ts'), 'utf-8'),
      readFileSync(resolve('src/services/subcontract.ts'), 'utf-8'),
      readFileSync(resolve('src/services/finance.ts'), 'utf-8'),
    ].join('\n')

    expect(sources).not.toMatch(/from ["'](?:vue|pinia|vue-router)/)
    expect(sources).not.toContain('frontend-admin/')
    expect(sources).not.toMatch(/\b(?:Number|parseFloat|parseInt)\s*\(/)
    const financeService = readFileSync(resolve('src/services/finance.ts'), 'utf-8')
    expect(financeService).toContain('createOwnerSettlement')
  })

  it('exposes ISSUE-053-033 revenue and ISSUE-053-034 finance-control adapters', () => {
    const source = readFileSync(resolve('src/services/finance.ts'), 'utf-8')
    expect(source).toContain('loadRevenueSettlements')
    expect(source).toContain('loadFinanceOperationsWorkspace')
    expect(source).toContain('loadCashForecastCycles')
    expect(source).toContain('loadFinancePeriods')
  })
})
