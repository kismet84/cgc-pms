import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createTechnicalRfi,
  loadDrawingTrace,
  loadTechnicalOverview,
  reviewTechnicalRfiResponse,
} from '@/services/technical'

const fetchMock = vi.fn<typeof fetch>()
const pageSource = readFileSync(
  resolve(process.cwd(), 'src/pages/delivery/TechnicalManagementPage.vue'),
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
  document.cookie = 'XSRF-TOKEN=technical-csrf; Path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

describe('M3 technical management closed loop', () => {
  it('encodes project scope, carries cancellation and rejects blank IDs', async () => {
    fetchMock.mockResolvedValueOnce(response({ schemes: [], drawings: [] }))
    const controller = new AbortController()

    await loadTechnicalOverview('project / 1', controller.signal)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/technical-management/overview?projectId=project%20%2F%201',
    )
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
    expect(() => loadDrawingTrace('   ')).toThrow('ID不能为空')
  })

  it('uses stage-specific RFI endpoints and CSRF-protected writes', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ id: 7, review_id: 9, status: 'DRAFT' }))
      .mockResolvedValueOnce(
        response({ id: 11, rfi_id: 7, status: 'ACCEPTED', review_comment: '接受' }),
      )

    const rfi = await createTechnicalRfi('review / 9', {
      rfiCode: 'RFI-1',
      subject: '节点做法',
      question: '请确认',
      priority: 'HIGH',
      responseDueDate: '2026-07-23',
    })
    const reviewed = await reviewTechnicalRfiResponse('11', {
      decision: 'ACCEPTED',
      reviewComment: '接受',
    })

    expect(rfi).toMatchObject({ id: '7', reviewId: '9' })
    expect(reviewed).toMatchObject({ id: '11', rfiId: '7', reviewComment: '接受' })
    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/technical-management/reviews/review%20%2F%209/rfis', 'POST'],
      ['/api/technical-management/rfi-responses/11/review', 'POST'],
    ])
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'technical-csrf',
    )
  })

  it('normalizes authoritative trace keys and identifier values', async () => {
    fetchMock.mockResolvedValueOnce(
      response({
        drawing: { id: 1, project_id: 2, drawing_code: 'D-1', status: 'ACTIVE' },
        versions: [{ id: 3, drawing_id: 1, previous_version_id: 2, status: 'APPROVED' }],
        reviews: [],
        rfis: [],
        responses: [],
        disclosures: [],
        schemes: [],
        scheme_approvals: [],
        construction_references: [],
        archives: [],
      }),
    )

    const trace = await loadDrawingTrace('1')
    expect(trace.drawing).toMatchObject({ id: '1', projectId: '2', drawingCode: 'D-1' })
    expect(trace.versions[0]).toMatchObject({ id: '3', drawingId: '1', previousVersionId: '2' })
    expect(trace.schemeApprovals).toEqual([])
  })

  it('keeps all technical permissions, authoritative reread and responsive semantics explicit', () => {
    for (const permission of [
      'technical:scheme:maintain',
      'technical:scheme:submit',
      'technical:drawing:receive',
      'technical:drawing:review',
      'technical:rfi:raise',
      'technical:rfi:respond',
      'technical:rfi:accept',
      'technical:disclosure:maintain',
      'technical:archive:confirm',
    ])
      expect(pageSource).toContain(permission)
    for (const stage of [
      "'TECH_SCHEME'",
      "'TECH_DRAWING_VERSION'",
      "'TECH_DRAWING_REVIEW'",
      "'TECH_RFI'",
      "'TECH_RFI_RESPONSE'",
      "'TECH_DISCLOSURE'",
      "'TECH_ARCHIVE'",
    ])
      expect(pageSource).toContain(stage)
    expect(pageSource).toContain('projectController?.abort()')
    expect(pageSource).toContain('traceController?.abort()')
    expect(pageSource).toContain('await loadProject(true)')
    expect(pageSource).toContain('kind="loading"')
    expect(pageSource).toContain('kind="empty"')
    expect(pageSource).not.toContain('state="loading"')
    expect(pageSource).not.toContain('state="empty"')
    expect(pageSource).toContain('description="正在加载方案、图纸、RFI、交底和归档状态。"')
    expect(pageSource).toContain('aria-label="图纸 RFI 技术闭环"')
    expect(pageSource).toContain('aria-live="polite"')
    expect(pageSource).toContain('@media (max-width: 64rem)')
    expect(pageSource).toContain('@media (max-width: 40rem)')
  })

  it('retries a failed evidence upload without repeating the business write', () => {
    expect(pageSource).toContain('const pendingEvidence = ref<PendingEvidence | null>(null)')
    expect(pageSource).toContain('if (pendingEvidence.value) {')
    expect(pageSource).toContain('await uploadPendingEvidence()')
    expect(pageSource).toContain(
      'pendingEvidence.value = { kind, documentType, businessType, businessId }',
    )
    expect(pageSource).toContain('pendingEvidence.value = null')
    expect(pageSource).toContain("pendingEvidence ? '重试附件上传' : '确认提交'")

    const recordPending = pageSource.indexOf(
      'pendingEvidence.value = { kind, documentType, businessType, businessId }',
    )
    const uploadPending = pageSource.indexOf('await uploadPendingEvidence()', recordPending)
    const clearPending = pageSource.indexOf('pendingEvidence.value = null', uploadPending)
    expect(recordPending).toBeGreaterThan(-1)
    expect(uploadPending).toBeGreaterThan(recordPending)
    expect(clearPending).toBeGreaterThan(uploadPending)
  })
})
