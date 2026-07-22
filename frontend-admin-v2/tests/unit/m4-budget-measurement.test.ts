import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  closeMeasurementPeriod,
  createBudget,
  createMeasurement,
  createMeasurementPeriod,
  deleteBudget,
  loadBudgetAvailability,
  loadBudgetPage,
  loadMeasurementPeriods,
  loadMeasurements,
  reviewOwnerMeasurement,
  saveBudgetLines,
  submitBudget,
  submitMeasurement,
  submitOwnerMeasurement,
  updateBudget,
} from '@/services/commercial'
const fetchMock = vi.fn<typeof fetch>()
const response = (data: unknown = {}) =>
  new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
beforeEach(() => {
  fetchMock.mockReset().mockImplementation(async () => response())
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())
describe('M4 budget and measurement contracts', () => {
  it('sends project and report-period filters with abort signals', async () => {
    const signal = new AbortController().signal
    await loadBudgetPage(
      {
        pageNo: 2,
        pageSize: 20,
        projectId: '9007199254740993',
        startDate: '2026-07-01',
        endDate: '2026-07-31',
      },
      signal,
    )
    await loadMeasurementPeriods(
      {
        projectId: '9007199254740993',
        contractId: '8',
        startDate: '2026-07-01',
        endDate: '2026-07-31',
      },
      signal,
    )
    await loadMeasurements(
      {
        projectId: '9007199254740993',
        status: 'DRAFT',
        startDate: '2026-07-01',
        endDate: '2026-07-31',
      },
      signal,
    )
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/project-budgets?pageNo=2&pageSize=20&projectId=9007199254740993&startDate=2026-07-01&endDate=2026-07-31',
      '/api/production-measurements/periods?projectId=9007199254740993&contractId=8&startDate=2026-07-01&endDate=2026-07-31',
      '/api/production-measurements?projectId=9007199254740993&status=DRAFT&startDate=2026-07-01&endDate=2026-07-31',
    ])
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ signal })
  })
  it('covers budget create, authoritative availability and versioned update/lines/submit/delete', async () => {
    fetchMock.mockImplementation(async (url) =>
      response(String(url).endsWith('/availability') ? [{ availableAmount: '8.88' }] : '10'),
    )
    await createBudget({
      projectId: '2',
      versionNo: 'V1',
      budgetName: '预算',
      totalAmount: '9007199254740993.12',
      version: null,
    })
    await updateBudget('1', {
      projectId: '2',
      versionNo: 'V1',
      budgetName: '预算',
      totalAmount: '9007199254740993.12',
      version: '7',
    })
    await saveBudgetLines('1', [{ costSubjectId: '3', budgetAmount: '0.01' }], '7')
    await loadBudgetAvailability('1')
    await submitBudget('1', '7')
    await deleteBudget('1', '7')
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/project-budgets',
      '/api/project-budgets/1?version=7',
      '/api/project-budgets/1/lines?version=7',
      '/api/project-budgets/1/availability',
      '/api/project-budgets/1/submit?version=7',
      '/api/project-budgets/1?version=7',
    ])
    expect(fetchMock.mock.calls.map(([, init]) => init?.method)).toEqual([
      'POST',
      'PUT',
      'POST',
      'GET',
      'POST',
      'DELETE',
    ])
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toMatchObject({ version: null })
  })
  it('forces version CAS on every measurement transition while creates carry no fake CAS', async () => {
    await createMeasurementPeriod({
      projectId: '2',
      contractId: '3',
      periodCode: '2026-07',
      periodName: '2026-07',
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      cutoffDate: '2026-07-31',
    })
    await createMeasurement({
      projectId: '2',
      contractId: '3',
      periodId: '4',
      measureDate: '2026-07-23',
      attachmentCount: 1,
      lines: [
        {
          contractItemId: '5',
          contractChangeId: null,
          currentQuantity: '9999999999999999.9999',
          evidenceCount: 1,
        },
      ],
    })
    await closeMeasurementPeriod('4', '5')
    await submitMeasurement('6', '9')
    await submitOwnerMeasurement('6', { attachmentCount: 1, version: '9' })
    await reviewOwnerMeasurement('8', {
      decision: 'RETURNED',
      reviewerName: '业主',
      reviewComment: '退回',
      lines: [],
      version: '11',
    })
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/production-measurements/periods',
      '/api/production-measurements',
      '/api/production-measurements/periods/4/close?version=5',
      '/api/production-measurements/6/submit?version=9',
      '/api/production-measurements/6/owner-submissions?version=9',
      '/api/production-measurements/owner-submissions/8/review?version=11',
    ])
  })
  it('preserves DecimalString and quantity strings without arithmetic coercion', async () => {
    fetchMock.mockImplementationOnce(async () =>
      response({
        records: [{ id: '1', totalAmount: '9007199254740993.12' }],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      }),
    )
    await expect(loadBudgetPage()).resolves.toMatchObject({
      records: [{ totalAmount: '9007199254740993.12' }],
    })
    const sources = [
      '../packages/frontend-contracts/src/commercial.ts',
      'src/services/commercial.ts',
      'src/pages/commercial/BudgetPage.vue',
      'src/pages/commercial/ProductionMeasurementPage.vue',
    ]
      .map((file) => readFileSync(resolve(file), 'utf8'))
      .join('\n')
    expect(sources).not.toMatch(/\b(?:parseFloat|parseInt)\s*\(/)
    expect(sources).not.toMatch(/\bNumber\s*\([^)]*(?:amount|quantity|price|budget)/i)
  })
  it('propagates budget 422 with the versioned request intact', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ code: 'BUDGET_VALIDATION_FAILED', message: '明细校验失败', data: null }),
        { status: 422, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    await expect(
      saveBudgetLines('1', [{ costSubjectId: '3', budgetAmount: '77.77' }], '7'),
    ).rejects.toMatchObject({ status: 422, code: 'BUDGET_VALIDATION_FAILED' })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/project-budgets/1/lines?version=7',
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
