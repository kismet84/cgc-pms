import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  bindFinalSettlement,
  createArchiveTransfer,
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
        quality_inspections: [],
      }),
    )
    const controller = new AbortController()

    await loadCloseoutOverview('project / 1', controller.signal)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/project-closeouts/overview?projectId=project%20%2F%201',
    )
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
    expect(() => loadCloseoutTrace('   ')).toThrow('ID不能为空')
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

  it('uses closeout write endpoints and CSRF headers without loading settlement candidates', async () => {
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
    expect(pageSource).toContain('手工输入 ownerSettlementId')
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
    expect(pageSource).toContain('loaded.filter((item) => hasCloseoutData(item.overview))')
    expect(pageSource).toContain('aria-label="竣工收尾闭环"')
    expect(pageSource).toContain('aria-live="polite"')
    expect(pageSource).toContain('@media (max-width: 64rem)')
    expect(pageSource).toContain('@media (max-width: 40rem)')
  })
})
