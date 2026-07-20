import {
  ALERT_API,
  NOTIFICATION_API,
  canOpenReportTarget,
  canRequestAlertNotifications,
  type AlertBatchResult,
  type ReportCatalogItem,
} from '@cgc-pms/frontend-contracts'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { batchResultMessage } from '@/pages/workbench/alert-report-model'
import { batchUpdateAlertStatus, loadAlerts, loadNotificationSummary } from '@/services/alerts'
import { loadReportCatalog } from '@/services/reports'
import { apiRequest } from '@/services/request'

vi.mock('@/services/request', () => ({ apiRequest: vi.fn() }))

beforeEach(() => vi.mocked(apiRequest).mockReset())

describe('M2 alert, notification and report contracts', () => {
  it('uses distinct view, edit and evaluate permissions without role-name shortcuts', () => {
    expect(canRequestAlertNotifications(['alert:view', 'notification:view'])).toBe(true)
    expect(canRequestAlertNotifications(['notification:view'])).toBe(false)
    expect(canRequestAlertNotifications(['alert:view'])).toBe(false)
    expect(canRequestAlertNotifications(['*'])).toBe(true)
  })

  it('preserves each batch failure instead of collapsing partial success', () => {
    const result: AlertBatchResult = {
      total: 3,
      success: 1,
      failed: 2,
      successIds: ['1'],
      failures: [
        { alertId: '2', reason: '必须由当前接单责任人完成处理' },
        { alertId: '3', reason: '预警不存在或不属于当前租户' },
      ],
    }
    expect(batchResultMessage(result)).toBe('成功 1 项，失败 2 项')
    expect(result.failures.map((failure) => failure.reason)).toHaveLength(2)
  })

  it('calls only existing alert endpoints and keeps project scope in the query', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ records: [], total: 0, pageNo: 1, pageSize: 50 })
    await loadAlerts({ pageNum: 1, pageSize: 50, projectId: '9', severity: 'HIGH' })
    expect(apiRequest).toHaveBeenCalledWith(
      `${ALERT_API.list}?pageNum=1&pageSize=50&projectId=9&severity=HIGH`,
      { signal: undefined },
    )

    await batchUpdateAlertStatus(['1', '2'], 'PROCESSED', '完成复核')
    expect(apiRequest).toHaveBeenLastCalledWith(ALERT_API.batchStatus, {
      method: 'PUT',
      body: { alertIds: ['1', '2'], processStatus: 'PROCESSED', statusRemark: '完成复核' },
    })
  })

  it('loads finite notification summary without creating an SSE request', async () => {
    vi.mocked(apiRequest)
      .mockResolvedValueOnce({ records: [], total: 0, pageNo: 1, pageSize: 8 })
      .mockResolvedValueOnce({ count: 0 })
    await loadNotificationSummary()
    expect(apiRequest).toHaveBeenCalledTimes(2)
    expect(apiRequest).toHaveBeenNthCalledWith(1, `${NOTIFICATION_API.list}?pageNo=1&pageSize=8`, {
      signal: undefined,
    })
    expect(apiRequest).toHaveBeenNthCalledWith(2, NOTIFICATION_API.unreadCount, {
      signal: undefined,
    })
    expect(
      vi.mocked(apiRequest).mock.calls.some(([path]) => String(path).includes('/stream')),
    ).toBe(false)
  })

  it('opens only known page targets and never disguises api-only or unknown targets', async () => {
    const item = (overrides: Partial<ReportCatalogItem> = {}): ReportCatalogItem => ({
      code: 'alert-center',
      name: '预警中心',
      catalog: 'alert',
      sourceType: 'page',
      target: '/alert',
      permissionCode: 'alert:view',
      filterSummary: '预警筛选',
      exportSupport: true,
      status: 'available',
      ...overrides,
    })
    expect(canOpenReportTarget(item(), ['/alert'])).toBe(true)
    expect(canOpenReportTarget(item({ sourceType: 'api', status: 'api_only' }), ['/alert'])).toBe(
      false,
    )
    expect(canOpenReportTarget(item({ target: '/unknown' }), ['/alert'])).toBe(false)
    expect(
      canOpenReportTarget(item({ target: '//external.example' }), ['//external.example']),
    ).toBe(false)

    vi.mocked(apiRequest).mockResolvedValue([item()])
    await loadReportCatalog()
    expect(apiRequest).toHaveBeenCalledWith('/reports/catalog', { signal: undefined })
  })
})
