import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createQualityConsequence,
  loadQualityIssues,
  loadQualityPlans,
  loadQualityTrace,
  reinspectQualityRectification,
} from '@/services/quality'

const fetchMock = vi.fn<typeof fetch>()
const pageSource = readFileSync(
  resolve(process.cwd(), 'src/pages/delivery/QualitySafetyPage.vue'),
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
  document.cookie = 'XSRF-TOKEN=quality-csrf; Path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

describe('M3 quality safety closed loop', () => {
  it('encodes filters, carries cancellation and rejects blank project scope', async () => {
    fetchMock.mockResolvedValueOnce(response([])).mockResolvedValueOnce(response([]))
    const controller = new AbortController()

    await loadQualityPlans('project / 1', controller.signal)
    await loadQualityIssues('project / 1', 'PENDING REINSPECTION', controller.signal)

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/quality-safety/plans?projectId=project%20%2F%201',
      '/api/quality-safety/issues?projectId=project+%2F+1&status=PENDING+REINSPECTION',
    ])
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
    expect(() => loadQualityPlans('   ')).toThrow('ID不能为空')
  })

  it('keeps consequence decimals as strings and uses stage-specific write endpoints', async () => {
    fetchMock
      .mockResolvedValueOnce(
        response({
          id: '77',
          issueId: '44',
          projectId: '3',
          partnerId: '5',
          contractId: '6',
          consequenceCode: 'QS-C-1',
          decisionType: 'BOTH',
          fineAmount: 12.5,
          reworkCostAmount: 4.25,
          evaluationScore: 80,
          evaluationComment: '整改完成',
          status: 'DRAFT',
        }),
      )
      .mockResolvedValueOnce(response({ id: '9', status: 'PASSED' }))

    const consequence = await createQualityConsequence({
      issueId: '44',
      partnerId: '5',
      contractId: '6',
      consequenceCode: 'QS-C-1',
      decisionType: 'BOTH',
      fineAmount: '12.50',
      reworkCostAmount: '4.25',
      evaluationScore: '80.00',
      evaluationComment: '整改完成',
    })
    await reinspectQualityRectification('9', { result: 'PASS', comment: '现场复核通过' })

    expect(consequence.fineAmount).toBe('12.5')
    expect(consequence.evaluationScore).toBe('80')
    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/quality-safety/consequences', 'POST'],
      ['/api/quality-safety/rectifications/9/reinspect', 'POST'],
    ])
  })

  it('normalizes trace consequence amounts without deriving authoritative state', async () => {
    fetchMock.mockResolvedValueOnce(
      response({
        plan: {},
        inspection: {},
        issue: {},
        rectifications: [],
        consequence: {
          id: 1,
          issueId: 2,
          projectId: 3,
          partnerId: 4,
          contractId: 5,
          fineAmount: 0,
          reworkCostAmount: 999999999999.99,
          evaluationScore: 60,
        },
      }),
    )

    const trace = await loadQualityTrace('2')
    expect(trace.consequence).toMatchObject({
      contractId: '5',
      fineAmount: '0',
      reworkCostAmount: '999999999999.99',
    })
  })

  it('keeps actions permission-separated, evidence staged and responsive semantics explicit', () => {
    for (const permission of [
      'quality:safety:plan:maintain',
      'quality:safety:inspection:maintain',
      'quality:safety:rectify',
      'quality:safety:reinspect',
      'quality:safety:consequence',
    ])
      expect(pageSource).toContain(permission)
    for (const stage of [
      "'QS_INSPECTION'",
      "'INSPECTION_EVIDENCE'",
      "'QS_ISSUE'",
      "'ISSUE_EVIDENCE'",
      "'QS_RECTIFICATION'",
      "'RECTIFICATION_EVIDENCE'",
      "'REINSPECTION_EVIDENCE'",
    ])
      expect(pageSource).toContain(stage)
    expect(pageSource).toContain('aria-labelledby="quality-title"')
    expect(pageSource).toContain('aria-live="polite"')
    expect(pageSource).toContain('@media (max-width: 64rem)')
    expect(pageSource).toContain('@media (max-width: 40rem)')
    expect(pageSource).toContain('projectController?.abort()')
    expect(pageSource).toContain('await loadProject(true)')
  })
})
