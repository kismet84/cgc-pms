import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  closeCostCorrective,
  confirmCostForecast,
  loadCostLedgerPage,
  loadCostLedgerSummary,
  refreshCostSummary,
  updateCostCorrective,
  updateCostForecast,
} from '@/services/commercial'

const fetchMock = vi.fn<typeof fetch>()
const ok = (data: unknown = {}) =>
  new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  fetchMock.mockReset().mockImplementation(async () => ok())
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('M4 costs contract and service', () => {
  it('passes project and report-period bounds to page and server summary with abort', async () => {
    const signal = new AbortController().signal
    const query = {
      pageNo: 2,
      pageSize: 20,
      projectId: '9007199254740993',
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      keyword: ' 材料 A&B ',
    }
    await loadCostLedgerPage(query, signal)
    await loadCostLedgerSummary(query, signal)
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/cost-ledger?pageNo=2&pageSize=20&projectId=9007199254740993&startDate=2026-07-01&endDate=2026-07-31&keyword=%E6%9D%90%E6%96%99+A%26B',
      '/api/cost-ledger/summary?pageNo=2&pageSize=20&projectId=9007199254740993&startDate=2026-07-01&endDate=2026-07-31&keyword=%E6%9D%90%E6%96%99+A%26B',
    ])
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ signal })
  })

  it('uses an independent refresh endpoint and preserves returned decimals', async () => {
    fetchMock.mockImplementationOnce(async () =>
      ok({ projectId: '1', actualCost: '9007199254740993.12', forecastProfit: '-0.01' }),
    )
    await expect(refreshCostSummary('1')).resolves.toMatchObject({
      actualCost: '9007199254740993.12',
      forecastProfit: '-0.01',
    })
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/cost-summary/1/refresh')
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'POST' })
  })

  it('forces CAS version on every forecast and corrective mutation', async () => {
    const forecast = {
      projectId: '1',
      forecastCode: 'F1',
      forecastName: '七月预测',
      forecastDate: '2026-07-23',
      items: [{ costSubjectId: '9', estimatedRemainingAmount: '9007199254740993.12' }],
      version: '7',
    }
    const corrective = {
      forecastId: '2',
      actionCode: 'A1',
      actionTitle: '纠偏',
      rootCause: '偏差',
      actionPlan: '执行',
      expectedSavingAmount: '0.01',
      responsibleUserId: '8',
      dueDate: '2026-07-24',
      version: '11',
    }
    await updateCostForecast('2', forecast)
    await confirmCostForecast('2', '7')
    await updateCostCorrective('3', corrective)
    await closeCostCorrective('3', {
      actualSavingAmount: '0',
      resultDescription: '已完成',
      version: '12',
    })
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/cost-controls/forecasts/2?version=7',
      '/api/cost-controls/forecasts/2/confirm?version=7',
      '/api/cost-controls/corrective-actions/3?version=11',
      '/api/cost-controls/corrective-actions/3/close?version=12',
    ])
    expect(() => updateCostForecast('2', { ...forecast, version: null })).toThrow('版本不能为空')
  })

  it('keeps amount handling as DecimalString and leaves M7 pages untouched', () => {
    const files = ['CostLedgerPage.vue', 'CostSummaryPage.vue', 'CostControlPage.vue']
    const source = files
      .map((name) => readFileSync(resolve('src/pages/commercial', name), 'utf8'))
      .join('\n')
    expect(source).not.toMatch(/\b(?:parseFloat|parseInt)\s*\(/)
    expect(source).not.toMatch(/\bNumber\s*\([^)]*(?:amount|cost|profit|saving)/i)
    expect(source).not.toContain('/cost/subject/')
  })
})
