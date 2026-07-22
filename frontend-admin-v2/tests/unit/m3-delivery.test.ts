import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/services/request'
import {
  loadSchedule,
  loadSchedules,
  loadScheduleTrace,
  replaceWbsTasks,
  updateSiteDailyLog,
  uploadSiteFile,
} from '@/services/delivery'
import { deliveryLabel } from '@/pages/delivery/labels'

const fetchMock = vi.fn<typeof fetch>()

function apiResponse<T>(data: T): Response {
  return new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=csrf-value; Path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

describe('M3 delivery request and service contracts', () => {
  it('omits projectId when loading schedules for all accessible projects', async () => {
    fetchMock.mockResolvedValueOnce(apiResponse([]))

    await loadSchedules()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/project-schedules')
  })

  it('shows delivery statuses and choices as business labels', () => {
    expect(
      [
        'ACCEPTED',
        'REJECTED',
        'NORMAL',
        'HIGH',
        'URGENT',
        'CONSTRUCTION_ORGANIZATION',
        'CONDITIONAL_PASS',
        'RFI_PENDING',
      ].map(deliveryLabel),
    ).toEqual([
      '已接受',
      '已驳回',
      '普通',
      '高',
      '紧急',
      '施工组织设计',
      '有条件通过',
      '待发起 RFI',
    ])
  })

  it('keeps FormData uploads raw and does not force JSON content type', async () => {
    fetchMock.mockResolvedValueOnce(apiResponse({ id: '1', originalName: '日报.png' }))

    await uploadSiteFile(
      new File(['demo'], '日报.png', { type: 'image/png' }),
      'SITE_DAILY_LOG',
      '99',
    )

    const [url, init] = fetchMock.mock.calls[0] ?? []
    expect(url).toBe('/api/files/upload?businessType=SITE_DAILY_LOG&businessId=99')
    expect(init?.body).toBeInstanceOf(FormData)
    expect(new Headers(init?.headers).has('Content-Type')).toBe(false)
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('csrf-value')
  })

  it('sends expectedVersion and expectedUpdatedAt on controlled writes', async () => {
    fetchMock
      .mockResolvedValueOnce(
        apiResponse({
          id: '11',
          project_id: '22',
          plan_code: 'BASE-01',
          plan_name: '基线计划',
          plan_type: 'BASELINE',
          version_no: 1,
          planned_start_date: '2026-07-01',
          planned_end_date: '2026-07-31',
          status: 'DRAFT',
          version: 4,
          tasks: [],
          periodPlans: [],
          latestSnapshot: null,
          correctiveActions: [],
        }),
      )
      .mockResolvedValueOnce(apiResponse(null))

    await replaceWbsTasks('11', 4, [
      {
        taskCode: 'WBS-001',
        taskName: '土方开挖',
        plannedStartDate: '2026-07-01',
        plannedEndDate: '2026-07-02',
        weightPercent: '100',
      },
    ])
    await updateSiteDailyLog('77', {
      projectId: '22',
      reportDate: '2026-07-21',
      constructionContent: '完成开挖',
      expectedUpdatedAt: '2026-07-21T10:00:00',
    })

    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/project-schedules/11/tasks', 'PUT'],
      ['/api/site-daily-logs/77', 'PUT'],
    ])
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      expectedVersion: 4,
      tasks: [
        {
          taskCode: 'WBS-001',
          taskName: '土方开挖',
          plannedStartDate: '2026-07-01',
          plannedEndDate: '2026-07-02',
          weightPercent: '100',
        },
      ],
    })
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      projectId: '22',
      reportDate: '2026-07-21',
      constructionContent: '完成开挖',
      expectedUpdatedAt: '2026-07-21T10:00:00',
    })
  })

  it('normalizes mixed snake_case and camelCase schedule detail payloads into strict V2 shape', async () => {
    fetchMock.mockResolvedValueOnce(
      apiResponse({
        id: '11',
        project_id: '22',
        plan_code: 'BASE-01',
        plan_name: '基线计划',
        plan_type: 'BASELINE',
        version_no: 3,
        planned_start_date: '2026-07-01',
        planned_end_date: '2026-07-31',
        status: 'ACTIVE',
        version: 9,
        tasks: [
          {
            id: 'task-1',
            schedule_plan_id: '11',
            task_code: 'WBS-001',
            task_name: '基础施工',
            planned_start_date: '2026-07-01',
            planned_end_date: '2026-07-05',
            weight_percent: '100.0000',
            actual_progress: '12.5000',
            status: 'IN_PROGRESS',
          },
        ],
        periodPlans: [
          {
            id: 'period-1',
            projectId: '22',
            schedulePlanId: '11',
            periodType: 'MONTHLY',
            periodCode: 'M-202607',
            periodName: '七月计划',
            startDate: '2026-07-01',
            endDate: '2026-07-31',
            status: 'APPROVED',
          },
        ],
        latestSnapshot: {
          id: 'snapshot-1',
          project_id: '22',
          schedule_plan_id: '11',
          snapshot_date: '2026-07-21',
          planned_progress: '50.0000',
          actual_progress: '45.5000',
          deviation_percent: '-4.5000',
          lagging_task_count: 1,
          status: 'LAGGING',
        },
        correctiveActions: [
          {
            id: 'ca-1',
            schedule_plan_id: '11',
            snapshot_id: 'snapshot-1',
            action_code: 'COR-01',
            reason: '暴雨',
            action_plan: '增加夜班',
            responsible_user_id: '7',
            due_date: '2026-07-25',
            status: 'PENDING',
          },
        ],
      }),
    )

    await expect(loadSchedule('11')).resolves.toMatchObject({
      id: '11',
      projectId: '22',
      planCode: 'BASE-01',
      version: 9,
      tasks: [{ taskCode: 'WBS-001', actualProgress: '12.5000' }],
      periodPlans: [{ periodCode: 'M-202607', periodType: 'MONTHLY' }],
      latestSnapshot: { laggingTaskCount: 1, deviationPercent: '-4.5000' },
      correctiveActions: [{ responsibleUserId: '7', actionCode: 'COR-01' }],
    })
  })

  it('supports raw FormData through apiRequest without JSON serialization', async () => {
    fetchMock.mockResolvedValueOnce(apiResponse({ ok: true }))
    const formData = new FormData()
    formData.append('file', new File(['x'], 'x.txt'))

    await apiRequest('/files/upload?businessType=A&businessId=1', {
      method: 'POST',
      body: formData,
      recover401: false,
    })

    expect(fetchMock.mock.calls[0]?.[1]?.body).toBe(formData)
  })

  it('preserves and normalizes the backend trace array shape', async () => {
    fetchMock.mockResolvedValueOnce(
      apiResponse({
        schedule: {
          id: '11',
          project_id: '22',
          plan_code: 'BASE-01',
          plan_name: '基线计划',
          plan_type: 'BASELINE',
          version_no: 1,
          planned_start_date: '2026-07-01',
          planned_end_date: '2026-07-31',
          status: 'ACTIVE',
        },
        wbsTasks: [
          {
            id: '1',
            schedule_plan_id: '11',
            task_code: 'WBS-1',
            task_name: '基础',
            planned_start_date: '2026-07-01',
            planned_end_date: '2026-07-31',
            weight_percent: '100',
            actual_progress: '40',
            status: 'IN_PROGRESS',
          },
        ],
        periodPlans: [
          {
            id: '2',
            project_id: '22',
            schedule_plan_id: '11',
            period_type: 'MONTHLY',
            period_code: 'M-1',
            period_name: '月计划',
            start_date: '2026-07-01',
            end_date: '2026-07-31',
            status: 'APPROVED',
          },
          {
            id: '3',
            project_id: '22',
            schedule_plan_id: '11',
            period_type: 'WEEKLY',
            period_code: 'W-1',
            period_name: '周计划',
            start_date: '2026-07-14',
            end_date: '2026-07-20',
            status: 'APPROVED',
          },
        ],
        dailyProgress: [
          {
            id: '4',
            daily_log_id: '44',
            schedule_plan_id: '11',
            wbs_task_id: '1',
            task_code: 'WBS-1',
            task_name: '基础',
            current_progress: '40',
            completed_quantity: '40',
            work_description: '完成基础施工',
          },
        ],
        snapshots: [
          {
            id: '5',
            project_id: '22',
            schedule_plan_id: '11',
            snapshot_date: '2026-07-21',
            planned_progress: '50',
            actual_progress: '40',
            deviation_percent: '-10',
            lagging_task_count: 1,
            status: 'LAGGING',
          },
        ],
        alerts: [{ id: '6' }],
        correctiveActions: [],
        revisions: [],
      }),
    )

    await expect(loadScheduleTrace('11')).resolves.toMatchObject({
      wbsTasks: [{ id: '1' }],
      periodPlans: [{ id: '2' }, { id: '3' }],
      dailyProgress: [{ id: '4' }],
      snapshots: [{ id: '5' }],
      alerts: [{ id: '6' }],
      correctiveActions: [],
      revisions: [],
    })
  })
})
