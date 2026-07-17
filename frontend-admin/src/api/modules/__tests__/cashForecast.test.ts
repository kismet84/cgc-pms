import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  approveCashForecast,
  approveFundingAction,
  completeFundingAction,
  createCashForecastCycle,
  createFundingAction,
  getCashForecastCycles,
  getCashForecastTrace,
  refreshCashForecastActuals,
  regenerateCashForecast,
  rollCashForecast,
  submitCashForecast,
  submitFundingAction,
} from '../cashForecast'

describe('cash forecast API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('maps the forecast version lifecycle and actual variance endpoints', async () => {
    await getCashForecastCycles('1')
    await createCashForecastCycle({
      projectId: '1',
      forecastName: '基准预测',
      asOfDate: '2031-01-01',
      horizonStart: '2031-01-01',
      horizonEnd: '2031-03-31',
      scenario: 'BASE',
      openingBalance: 100,
    })
    await getCashForecastTrace('10')
    await regenerateCashForecast('10')
    await submitCashForecast('10')
    await approveCashForecast('10', true, '同意')
    await refreshCashForecastActuals('10')
    await rollCashForecast('10', {
      asOfDate: '2031-02-01',
      horizonEnd: '2031-04-30',
      forecastName: '二月滚动预测',
    })
    expect(requestMock.mock.calls.map((call) => call[0].url)).toEqual([
      '/cash-forecasts/cycles',
      '/cash-forecasts/cycles',
      '/cash-forecasts/cycles/10/trace',
      '/cash-forecasts/cycles/10/regenerate',
      '/cash-forecasts/cycles/10/submit',
      '/cash-forecasts/cycles/10/approve',
      '/cash-forecasts/cycles/10/actuals/refresh',
      '/cash-forecasts/cycles/10/roll',
    ])
  })

  it('maps the gap action maker-checker lifecycle', async () => {
    const action = {
      lineId: '20',
      actionType: 'FINANCING' as const,
      plannedDate: '2031-01-10',
      amount: 500,
      reason: '覆盖缺口',
    }
    await createFundingAction('10', action)
    await submitFundingAction('30')
    await approveFundingAction('30', true, '同意')
    await completeFundingAction('30', 500, 'BANK-001')
    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/cash-forecasts/cycles/10/actions',
      method: 'post',
      data: action,
    })
    expect(requestMock.mock.calls.slice(1).map((call) => call[0].url)).toEqual([
      '/cash-forecasts/actions/30/submit',
      '/cash-forecasts/actions/30/approve',
      '/cash-forecasts/actions/30/complete',
    ])
  })
})
