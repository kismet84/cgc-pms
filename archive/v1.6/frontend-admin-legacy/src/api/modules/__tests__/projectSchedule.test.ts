import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  createCorrectiveAction,
  createPeriodPlan,
  getProjectScheduleTrace,
  replaceDailyProgress,
  replacePeriodPlanItems,
  replaceWbsTasks,
  submitCorrectiveAction,
  submitProjectSchedule,
} from '../projectSchedule'

describe('project schedule closed-loop API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('keeps baseline, WBS, period plan and daily progress endpoints traceable', async () => {
    await replaceWbsTasks('11', 3, [])
    await submitProjectSchedule('11')
    await createPeriodPlan('11', {
      schedulePlanId: '11',
      periodType: 'MONTHLY',
      periodCode: 'M-01',
      periodName: '月计划',
      startDate: '2099-01-01',
      endDate: '2099-01-31',
    })
    await replacePeriodPlanItems('44', 2, [])
    await replaceDailyProgress('22', [
      {
        wbsTaskId: '33',
        currentProgress: 20,
        completedQuantity: 5,
        workDescription: '完成5方',
      },
    ])

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/project-schedules/11/tasks',
      method: 'put',
      data: { expectedVersion: 3, tasks: [] },
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/project-schedules/11/submit',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/project-schedules/11/period-plans',
      method: 'post',
      data: expect.objectContaining({ periodType: 'MONTHLY' }),
    })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/project-schedules/period-plans/44/items',
      method: 'put',
      data: { expectedVersion: 2, items: [] },
    })
    expect(requestMock).toHaveBeenNthCalledWith(5, {
      url: '/project-schedules/daily-logs/22/progress',
      method: 'put',
      data: { items: [expect.objectContaining({ wbsTaskId: '33' })] },
    })
  })

  it('binds corrective approval and reverse trace to the schedule chain', async () => {
    await createCorrectiveAction('11', {
      snapshotId: '44',
      actionCode: 'COR-01',
      reason: '延期',
      actionPlan: '增加班组',
      responsibleUserId: '1',
      dueDate: '2099-02-01',
    })
    await submitCorrectiveAction('55')
    await getProjectScheduleTrace('11')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/project-schedules/11/corrective-actions',
      method: 'post',
      data: expect.objectContaining({ snapshotId: '44' }),
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/project-schedules/corrective-actions/55/submit',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/project-schedules/11/trace',
      method: 'get',
    })
  })
})
