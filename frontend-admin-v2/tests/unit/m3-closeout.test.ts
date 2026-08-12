import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  bindFinalSettlement,
  createArchiveTransfer,
  loadCloseoutPage,
  loadCloseoutOverview,
  loadCloseoutTrace,
  verifyCloseoutDefect,
} from '@/services/closeout'

const fetchMock = vi.fn<typeof fetch>()
const pageSource = readFileSync(
  resolve(process.cwd(), 'src/pages/delivery/ProjectCloseoutPage.vue'),
  'utf8',
)

function response(data: unknown): Response {
  return new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=closeout-csrf; Path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

describe('M3 closeout closed loop', () => {
  it('encodes project scope, carries cancellation and rejects blank IDs', async () => {
    fetchMock.mockResolvedValueOnce(
      response({
        closeout: null,
        section_acceptances: [],
        final_acceptances: [],
        settlements: [],
        receivables: [],
        warranties: [],
        defects: [],
        archive_transfers: [],
        wbs_readiness: { total_tasks: 1, incomplete_tasks: 0 },
        wbs_tasks: [],
        quality_inspections: [
          {
            id: 9,
            wbs_task_id: 8,
            inspection_code: 'QI-9',
            inspection_date: '2026-08-01',
            conclusion: 'PASS',
            status: 'SUBMITTED',
          },
        ],
      }),
    )
    const controller = new AbortController()

    const overview = await loadCloseoutOverview('project / 1', controller.signal)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/project-closeouts/overview?projectId=project%20%2F%201',
    )
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
    expect(overview.qualityInspections[0]).toMatchObject({ id: '9', wbsTaskId: '8' })
    expect(() => loadCloseoutTrace('   ')).toThrow('ID不能为空')
  })

  it('loads one bounded server page for the all-project workspace', async () => {
    fetchMock.mockResolvedValueOnce(
      response({
        pageNo: 2,
        pageSize: 10,
        total: 11,
        records: [
          {
            project_id: 9,
            project_name: '项目九',
            closeout_id: 19,
            closeout_code: 'CO-9',
            status: 'INITIATED',
            section_acceptance_count: 2,
            final_acceptance_count: 1,
            warranty_count: 0,
            defect_count: 0,
          },
        ],
      }),
    )
    const controller = new AbortController()

    const page = await loadCloseoutPage({ pageNo: 2, pageSize: 10 }, controller.signal)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/project-closeouts/page?pageNo=2&pageSize=10')
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
    expect(page.records[0]).toMatchObject({
      projectId: '9',
      closeoutId: '19',
      sectionAcceptanceCount: 2,
    })
  })

  it('sends one bounded detail page for the selected-project overview', async () => {
    fetchMock.mockResolvedValueOnce(response({ detail_page_no: 2, detail_page_size: 25 }))
    const controller = new AbortController()

    await loadCloseoutOverview('project-9', controller.signal, {
      detailPageNo: 2,
      detailPageSize: 25,
    })

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/project-closeouts/overview?projectId=project-9&detailPageNo=2&detailPageSize=25',
    )
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
  })

  it('keeps settlement and allocation money as strings and normalizes snake_case trace keys', async () => {
    fetchMock.mockResolvedValueOnce(
      response({
        closeout: { id: 1, project_id: 2, closeout_code: 'PC-1', status: 'READY_TO_CLOSE' },
        project: { id: 2 },
        section_acceptances: [],
        final_acceptances: [],
        approval_records: [{ id: 3, created_by: 1 }],
        final_settlement: { id: 4, net_receivable_amount: 900.25 },
        receivables: [{ id: 5, outstanding_amount: 0 }],
        collection_allocations: [
          {
            id: 6,
            collection_id: 7,
            receivable_id: 5,
            allocated_amount: 900.25,
            collection_code: 'COL-1',
            receivable_type: 'REGULAR',
          },
        ],
        warranties: [],
        defects: [],
        archive_transfers: [],
      }),
    )

    const trace = await loadCloseoutTrace('1')

    expect(trace.closeout).toMatchObject({ id: '1', projectId: '2', closeoutCode: 'PC-1' })
    expect(trace.collectionAllocations[0]).toMatchObject({
      id: '6',
      collectionId: '7',
      receivableId: '5',
      allocatedAmount: '900.25',
      collectionCode: 'COL-1',
      receivableType: 'REGULAR',
    })
  })

  it('uses closeout write endpoints, CSRF headers and business-labelled settlement candidates', async () => {
    fetchMock
      .mockResolvedValueOnce(
        response({ id: 11, final_owner_settlement_id: 22, status: 'FINAL_SETTLEMENT_BOUND' }),
      )
      .mockResolvedValueOnce(response({ id: 33, transfer_code: 'AT-1', status: 'DRAFT' }))
      .mockResolvedValueOnce(response({ id: 44, verification_comment: '通过', status: 'CLOSED' }))

    await bindFinalSettlement('closeout / 1', { ownerSettlementId: '22' })
    await createArchiveTransfer('33', {
      transferCode: 'AT-1',
      transferDate: '2026-07-21',
      recipientOrganization: '建设单位档案室',
      recipientName: '档案员',
      archiveLocation: 'A-01',
      transferScope: '竣工资料',
    })
    const defect = await verifyCloseoutDefect('44', {
      decision: 'ACCEPTED',
      verificationComment: '通过',
    })

    expect(defect).toMatchObject({ id: '44', verificationComment: '通过' })
    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/project-closeouts/closeout%20%2F%201/final-settlement', 'POST'],
      ['/api/project-closeouts/33/archive-transfer', 'POST'],
      ['/api/project-closeouts/defects/44/verify', 'POST'],
    ])
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'closeout-csrf',
    )
    expect(pageSource).not.toContain('revenue:operations:query')
    expect(pageSource).toContain(':options="settlementOptions"')
    expect(pageSource).toContain("item.status === 'RECEIVABLE_CREATED'")
    expect(pageSource).toContain("receivable.receivableType === 'PROGRESS'")
    expect(pageSource).toContain("item.status === 'COMPLETED'")
    expect(pageSource).toContain("item.status === 'SUBMITTED'")
    expect(pageSource).toContain('item.wbsTaskId === sectionForm.wbsTaskId')
    expect(pageSource).not.toContain('手工输入 ownerSettlementId')
  })

  it('keeps permissions, evidence stages, reread and responsive semantics explicit', () => {
    for (const permission of [
      'closeout:query',
      'closeout:initiate',
      'closeout:section:maintain',
      'closeout:acceptance:submit',
      'closeout:settlement:bind',
      'closeout:collection:verify',
      'closeout:warranty:maintain',
      'closeout:defect:maintain',
      'closeout:defect:verify',
      'closeout:archive:maintain',
      'closeout:close',
    ])
      expect(pageSource).toContain(permission)
    for (const stage of [
      "'CLOSEOUT_SECTION_ACCEPTANCE'",
      "'SECTION_ACCEPTANCE_RECORD'",
      "'CLOSEOUT_FINAL_ACCEPTANCE'",
      "'FINAL_ACCEPTANCE_CERTIFICATE'",
      "'CLOSEOUT_DEFECT'",
      "'DEFECT_RECTIFICATION_EVIDENCE'",
      "'CLOSEOUT_WARRANTY'",
      "'WARRANTY_RELEASE_VOUCHER'",
      "'CLOSEOUT_ARCHIVE_TRANSFER'",
      "'ARCHIVE_TRANSFER_LIST'",
    ])
      expect(pageSource).toContain(stage)
    expect(pageSource).toContain('const pendingEvidence = ref<PendingEvidence | null>(null)')
    expect(pageSource).toContain('await uploadPendingEvidence()')
    expect(pageSource).toContain('projectController?.abort()')
    expect(pageSource).toContain('traceController?.abort()')
    expect(pageSource).toContain('await loadProject(true)')
    expect(pageSource).toContain('const loaded = await loadCloseoutPage(')
    expect(pageSource).not.toContain('workspace.projects.map')
    expect(pageSource).toContain(':total="scopedOverviewTotal"')
    expect(pageSource).toContain('aria-label="竣工收尾闭环"')
    expect(pageSource).toContain('<V2Card v-if="!projectId && scopedOverviews.length">')
    expect(pageSource).not.toContain('title="全部项目收尾概览"')
    expect(pageSource).not.toContain('<V2Badge>{{ scopedOverviews.length }} 个项目</V2Badge>')
    expect(pageSource).not.toContain(':subtitle="`共 ${scopedOverviews.length} 个项目`"')
    expect(pageSource).not.toMatch(/(?:label|placeholder)="[^"]*(?:\bID\b|\w+Id\b)[^"]*"/)
    expect(pageSource).toContain(':options="userOptions(warrantyForm.responsibleUserId)"')
    expect(pageSource).toContain("showToast('error', '操作未完成', value)")
    expect(pageSource).toContain('@media (max-width: 64rem)')
    expect(pageSource).toContain('@media (max-width: 40rem)')
  })
})
