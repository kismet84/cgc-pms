import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createQualityConsequence,
  loadQualityIssues,
  loadQualityPlans,
  loadQualityTrace,
  reinspectQualityRectification,
  submitQualityConsequence,
  submitQualityRectification,
} from '@/services/quality'

const fetchMock = vi.fn<typeof fetch>()
const pageSource = readFileSync(
  resolve(process.cwd(), 'src/pages/delivery/QualitySafetyPage.vue'),
  'utf8',
)
const serviceSource = readFileSync(resolve(process.cwd(), 'src/services/quality.ts'), 'utf8')

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
      .mockResolvedValueOnce(
        response({
          id: '77',
          issueId: '44',
          projectId: '3',
          partnerId: '5',
          contractId: '6',
          fineAmount: 12.5,
          reworkCostAmount: 4.25,
          evaluationScore: 80,
          status: 'SUBMITTED',
          approvalInstanceId: 'WF-77',
        }),
      )
      .mockResolvedValueOnce(
        response({
          id: 9,
          issueId: 44,
          projectId: 3,
          roundNo: 1,
          actionDescription: '完成整改',
          responsibleUserId: 5,
          plannedCompleteDate: '2026-08-12',
          status: 'SUBMITTED',
          approvalInstanceId: 9009,
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
    const submittedConsequence = await submitQualityConsequence('77')
    const submittedRectification = await submitQualityRectification('9')
    await reinspectQualityRectification('9', { result: 'PASS', comment: '现场复核通过' })

    expect(consequence.fineAmount).toBe('12.5')
    expect(consequence.evaluationScore).toBe('80')
    expect(submittedConsequence).toMatchObject({ status: 'SUBMITTED', approvalInstanceId: 'WF-77' })
    expect(submittedRectification).toMatchObject({
      id: '9',
      issueId: '44',
      status: 'SUBMITTED',
      approvalInstanceId: '9009',
    })
    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/quality-safety/consequences', 'POST'],
      ['/api/quality-safety/consequences/77/submit', 'POST'],
      ['/api/quality-safety/rectifications/9/submit', 'POST'],
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
    expect(pageSource).toContain('aria-label="质量安全整改闭环"')
    expect(pageSource).toContain('v-if="!loading && !scopeProjectIds.length && !errorMessage"')
    expect(pageSource.indexOf('title="质量安全整改闭环"')).toBeLessThan(
      pageSource.indexOf('title="暂无可访问项目"'),
    )
    expect(pageSource).toContain("showToast('error', '操作未完成', value)")
    expect(pageSource).toContain('@media (max-width: 64rem)')
    expect(pageSource).toContain('@media (max-width: 40rem)')
    expect(pageSource).toContain('projectController?.abort()')
    expect(pageSource).toContain('await loadProject(true)')
    expect(pageSource).not.toMatch(/(?:label|placeholder)="[^"]*(?:\bID\b|\w+Id\b)[^"]*"/)
    expect(pageSource).toContain(':options="partnerOptions"')
    expect(pageSource).toContain(':options="contractOptions"')
    expect(pageSource).toContain(':options="userOptions(issueForm.responsibleUserId)"')
    expect(pageSource).toContain('整改已提交审批')
    expect(pageSource).toContain('后果已提交审批')
    expect(pageSource).toContain('提交既有后果审批')
    expect(pageSource).toContain("['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(status)")
    expect(serviceSource).not.toContain('postQualityConsequence')
    expect(serviceSource).not.toContain('postConsequence')
  })

  it('splits the ledger into five permission-aware tabs without resetting page state', () => {
    for (const tab of ['plan', 'inspection', 'rectification', 'reinspection', 'consequence'])
      expect(pageSource).toContain(`value: '${tab}'`)
    for (const label of ['检查计划', '检查记录', '问题整改', '复检闭环', '后果追踪'])
      expect(pageSource).toContain(`label: '${label}'`)

    expect(pageSource).toContain('v-model="activeTab"')
    expect(pageSource).toContain(':tabs="visibleTabs"')
    expect(pageSource).toContain('id-prefix="quality"')
    expect(pageSource).toContain('aria-label="质量安全业务分区"')
    expect(pageSource).toContain('role="tabpanel"')
    expect(pageSource).toContain(':id="`quality-panel-${activeTab}`"')
    expect(pageSource).toContain(':aria-labelledby="`quality-tab-${activeTab}`"')
    expect(pageSource).toContain('v-if="activeTab === \'plan\'"')
    expect(pageSource).toContain('v-else-if="activeTab === \'inspection\'"')
    expect(pageSource).toContain('v-else-if="activeTab === \'rectification\'"')
    expect(pageSource).toContain('v-else-if="activeTab === \'reinspection\'"')
    expect(pageSource).toContain('v-else-if="activeTab === \'consequence\'"')
    expect(pageSource).not.toMatch(/watch\(activeTab[\s\S]{0,200}selectedPlanId\.value\s*=/)
    expect(pageSource).toContain("() => scopeProjectIds.value.join('|')")
    expect(pageSource).toContain('plan.id === previousSelectedPlanId')
    expect(pageSource).toContain('@click="selectPlan(plan.id)"')
    expect(pageSource).toContain('query: { ...route.query, planId }')
    expect(pageSource).not.toContain('v-model="inspectionTypeFilter"')
    expect(pageSource).not.toContain('class="quality-page__facts"')
    expect(pageSource).not.toContain('质量安全闭环概览')
    expect(pageSource).not.toContain('当前项目 {{ currentProjectLabel }}')
    expect(pageSource).not.toContain("当前计划 {{ selectedPlan?.planName || '未选择' }}")
    expect(pageSource).toContain('partnerLabel(issue.responsiblePartnerId)')
    expect(pageSource.match(/v-else-if="!errorMessage"/g)).toHaveLength(6)
  })

  it('keeps existing business actions in their matching active panels', () => {
    for (const action of [
      "show('plan')",
      "show('inspection')",
      "show('issue', inspection)",
      "show('rectification', issue)",
      'showReinspection(issue)',
      "show('consequence', issue)",
      'openTrace(issue)',
    ])
      expect(pageSource).toContain(action)
    expect(pageSource).toContain('v-if="activeTab === \'plan\' && canPlan && projectId"')
    expect(pageSource).toContain("activeTab === 'inspection'")
    expect(pageSource).toContain('v-if="rectificationIssues.length"')
    expect(pageSource).toContain('v-if="reinspectionIssues.length"')
    expect(pageSource).toContain('v-if="consequenceIssues.length"')
  })
})
