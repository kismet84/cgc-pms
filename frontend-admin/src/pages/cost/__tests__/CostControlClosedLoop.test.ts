import { describe, expect, it, vi, beforeEach } from 'vitest'

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/api/request', () => ({ request }))

import {
  closeCostCorrective,
  confirmCostForecast,
  createCostCorrective,
  createCostForecast,
  getCostControlOverview,
  getCostForecastTrace,
  submitCostCorrective,
  updateCostForecast,
} from '@/api/modules/costControl'

describe('目标成本与动态利润闭环 API', () => {
  beforeEach(() => request.mockReset())

  it('覆盖预测创建、更新、确认与追溯', () => {
    const payload = {
      projectId: '1',
      forecastCode: 'FC-001',
      forecastName: '月度预测',
      forecastDate: '2026-07-16',
      items: [{ costSubjectId: '10', estimatedRemainingAmount: 100 }],
    }
    createCostForecast(payload)
    updateCostForecast('20', payload)
    confirmCostForecast('20')
    getCostControlOverview('1')
    getCostForecastTrace('20')
    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/cost-controls/forecasts',
      method: 'post',
      data: payload,
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/cost-controls/forecasts/20/confirm',
      method: 'post',
    })
    expect(request).toHaveBeenNthCalledWith(5, {
      url: '/cost-controls/forecasts/20/trace',
      method: 'get',
    })
  })

  it('覆盖纠偏建立、审批提交与结果关闭', () => {
    const payload = {
      forecastId: '20',
      actionCode: 'CA-001',
      actionTitle: '压降成本',
      rootCause: '偏差',
      actionPlan: '执行措施',
      expectedSavingAmount: 100,
      responsibleUserId: '1',
      dueDate: '2026-07-31',
    }
    createCostCorrective(payload)
    submitCostCorrective('30')
    closeCostCorrective('30', { actualSavingAmount: 80, resultDescription: '已完成' })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/cost-controls/corrective-actions/30/submit',
      method: 'post',
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/cost-controls/corrective-actions/30/close',
      method: 'post',
      data: { actualSavingAmount: 80, resultDescription: '已完成' },
    })
  })
})
