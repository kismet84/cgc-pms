import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ProjectPage from '@/pages/projects/ProjectPage.vue'
import { PROJECT_ROLE_OPTIONS, projectRoleLabel, projectRoleOptions } from '@/pages/projects/model'
import {
  loadProject,
  loadProjectMembers,
  loadProjectOverview,
  loadProjectPage,
} from '@/services/projects'
import { useSessionStore } from '@/stores/session'

const fetchMock = vi.fn<typeof fetch>()

function apiResponse<T>(data: T): Response {
  return new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockImplementation(async () => apiResponse({ records: [], total: 0 }))
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

describe('M3 project request baseline', () => {
  it('uses seven project-scoped system roles and keeps historical values read-only', () => {
    expect(PROJECT_ROLE_OPTIONS.map((option) => option.value)).toEqual([
      'PROJECT_MANAGER',
      'PROJECT_ACCOUNTANT',
      'TECHNICAL_LEAD',
      'SAFETY_LEAD',
      'CONSTRUCTION_LEAD',
      'PROCUREMENT_LEAD',
      'EMPLOYEE',
    ])
    expect(projectRoleLabel('PM')).toBe('项目经理')
    expect(projectRoleOptions('PM').at(-1)).toEqual({
      value: 'PM',
      label: '项目经理（历史角色，只读）',
      disabled: true,
    })
  })

  it('applies both project selects through the route before loading', () => {
    const source = [
      readFileSync(resolve('src/pages/projects/project-routes/ProjectListPage.vue'), 'utf-8'),
      readFileSync(resolve('src/pages/projects/project-routes/project-pages.css'), 'utf-8'),
    ].join('\n')
    expect(source).toContain(`@update:model-value="applySelectFilter('projectType', $event)"`)
    expect(source).toContain(`@update:model-value="applySelectFilter('status', $event)"`)
    expect(source).toMatch(
      /\.project-page__detail-actions\s*\{[^}]*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/,
    )
    expect(source).toMatch(
      /async function setQuery[\s\S]*router\.resolve[\s\S]*await router\.replace/,
    )
    expect(source).toMatch(
      /async function search[\s\S]*if \(!\(await setQuery\(\)\)\) await load\(\)/,
    )
  })

  it('renders project details as single-column cards with server-authoritative amounts', () => {
    const source = [
      readFileSync(resolve('src/pages/projects/project-routes/ProjectOverviewPage.vue'), 'utf-8'),
      readFileSync(resolve('src/pages/projects/project-routes/model.ts'), 'utf-8'),
      readFileSync(resolve('src/pages/projects/project-routes/project-pages.css'), 'utf-8'),
    ].join('\n')
    const components = readFileSync(resolve('src/styles/components/dialogs.css'), 'utf-8')

    expect(source).toContain('class="project-page__overview-stack"')
    expect(source).toMatch(
      /\.project-page__overview-stack\s*\{[^}]*grid-template-columns: minmax\(0, 1fr\)/,
    )
    expect(source).toContain('class="v2-detail-dialog__section project-page__overview-intro"')
    expect(source).toMatch(
      /\.project-page__overview-intro\s*\{[^}]*padding-block: var\(--v2-space-4\)/,
    )
    expect(components).toMatch(
      /\.v2-detail-dialog__facts > div\s*\{[^}]*min-height: var\(--v2-control-height-touch\)/,
    )
    expect(components).toMatch(
      /@media \(max-width: 30rem\)[\s\S]*\.v2-detail-dialog__facts > div\s*\{[^}]*min-height: calc\(var\(--v2-space-12\) \+ var\(--v2-space-4\)\)/,
    )
    expect(source).toContain('class="v2-detail-dialog__facts project-page__overview-cost-facts"')
    expect(source).toMatch(
      /\.project-page__overview-cost-facts\s*\{[^}]*grid-template-columns: repeat\(4, minmax\(0, 1fr\)\)/,
    )
    for (const label of [
      '项目名称',
      '项目类型',
      '项目地址',
      '建设单位',
      '监理单位',
      '设计单位',
      '合同金额（元）',
      '目标成本（元）',
      '计划开工',
      '计划完工',
    ]) {
      expect(source).toContain(`<dt>${label}</dt>`)
    }
    const form = readFileSync(resolve('src/pages/projects/ProjectForm.vue'), 'utf-8')
    expect(form).not.toContain('合同金额（元）')
    expect(form).not.toContain('目标成本（元）')
    expect(source).toContain('服务端阻塞项')
    expect(source).toContain('PROJECT_COMMENCEMENT')
    for (const contract of [
      "PROJECT_OWNER_CONTRACT_REQUIRED: '缺少已批准业主主合同'",
      "COST_TARGET_ACTIVE_UNIQUE_REQUIRED: '缺少唯一生效目标成本'",
      "PROJECT_BUDGET_ACTIVE_UNIQUE_REQUIRED: '缺少唯一生效项目预算'",
      "PROJECT_WBS_ACTIVE_UNIQUE_REQUIRED: '缺少唯一生效WBS计划'",
      "PROJECT_COMMENCEMENT_BASIS_FILE_REQUIRED: '缺少已通过扫描的开工依据附件'",
      "code.startsWith('PROJECT_OWNER_CONTRACT')",
      "code.startsWith('PROJECT_WBS')",
    ])
      expect(source).toContain(contract)
    expect(source).not.toContain("code.startsWith('OWNER_MAIN_CONTRACT')")
    expect(source).not.toContain("code.startsWith('PROJECT_SCHEDULE')")
  })

  it('snapshots the selected schedule directly and keeps daily actions behind confirmation', () => {
    const schedule = readFileSync(resolve('src/pages/delivery/SchedulePage.vue'), 'utf-8')
    const dailyLog = readFileSync(resolve('src/pages/delivery/DailyLogPage.vue'), 'utf-8')

    expect(schedule).toContain('@click="requestScheduleSubmit(item)"')
    expect(schedule).not.toContain('openDetail(item.id).then(() => requestScheduleSubmit())')
    expect(schedule).not.toContain('集中管理基线计划、WBS、月周计划、进度偏差与纠偏。')
    expect(schedule).toContain('const controller = new AbortController()')
    expect(schedule).toContain('if (listController !== controller) return false')
    expect(schedule).toContain('!controller.signal.aborted && listController === controller')
    expect(dailyLog).toContain('@click="requestDailySubmit"')
    expect(dailyLog).toContain('@click="requestFileRemoval(file.id, file.originalName)"')
    expect(dailyLog).toContain('const controller = new AbortController()')
    expect(dailyLog).toContain('if (listController !== controller) return false')
    expect(dailyLog).toContain('!controller.signal.aborted && listController === controller')
  })

  it('uses the validated public-shell project context and keeps aggregate routes on all projects', () => {
    const shell = [
      readFileSync(resolve('src/layouts/AppShell.vue'), 'utf-8'),
      readFileSync(resolve('src/layouts/ShellHeaderWorkspace.vue'), 'utf-8'),
    ].join('\n')
    const catalog = readFileSync(resolve('src/navigation/domains/delivery.ts'), 'utf-8')

    expect(shell).toContain("{ value: '', label: '全部项目' }")
    expect(shell).toContain('allow-empty')
    for (const page of [
      'SchedulePage.vue',
      'DailyLogPage.vue',
      'QualitySafetyPage.vue',
      'TechnicalManagementPage.vue',
      'ProjectCloseoutPage.vue',
    ]) {
      const source = readFileSync(resolve(`src/pages/delivery/${page}`), 'utf-8')
      expect(source).not.toMatch(/route\.query\.projectId/)
      expect(source).toContain('workspace.selectedProjectId')
    }
    expect(catalog).not.toContain('projectAllowAll: false')
    const boundedWorkspaces = {
      'QualitySafetyPage.vue': 'loadQualityWorkspace',
      'TechnicalManagementPage.vue': 'loadTechnicalWorkspace',
      'ProjectCloseoutPage.vue': 'loadCloseoutPage',
    }
    for (const [page, loader] of Object.entries(boundedWorkspaces)) {
      const source = readFileSync(resolve(`src/pages/delivery/${page}`), 'utf-8')
      expect(source).not.toContain('scopeProjectIds')
      expect(source).not.toContain('workspace.projects.map')
      expect(source).toContain(loader)
    }
    expect(readFileSync(resolve('src/pages/delivery/QualitySafetyPage.vue'), 'utf-8')).toMatch(
      /async function runWrite[\s\S]*if \(!projectId\.value\)/,
    )
  })

  it('never renders a database project id as a project label', () => {
    const pages = [
      'commercial/BidCostPage.vue',
      'commercial/ContractPage.vue',
      'commercial/CostLedgerPage.vue',
      'commercial/CostTargetPage.vue',
      'commercial/VariationPage.vue',
      'dashboard/DashboardPage.vue',
      'delivery/DailyLogPage.vue',
      'delivery/ProjectCloseoutPage.vue',
      'delivery/SchedulePage.vue',
    ].map((page) => readFileSync(resolve(`src/pages/${page}`), 'utf-8'))

    for (const source of pages) {
      expect(source).not.toMatch(/\|\|\s*(?:record|row|detail|activeRecord)\.projectId/)
      expect(source).not.toMatch(/\?\?\s*(?:projectId(?:\.value)?|id)\s*(?:\?\?|[,):])/)
      expect(source).not.toMatch(/\{\{\s*(?:record|detail|selectedAlert)\.projectId\s*\}\}/)
      expect(source).not.toContain('`项目 ${item.projectId}`')
    }
  })

  it('keeps delivery filters business-labelled and hides raw identifier prompts', () => {
    const dailyLog = readFileSync(resolve('src/pages/delivery/DailyLogPage.vue'), 'utf-8')
    const schedule = readFileSync(resolve('src/pages/delivery/SchedulePage.vue'), 'utf-8')

    for (const source of [dailyLog, schedule])
      expect(source).not.toMatch(/(?:label|placeholder)="[^"]*(?:\bID\b|\w+Id\b)[^"]*"/)
    expect(dailyLog).toContain(':options="projectOptions"')
    expect(schedule).toContain(':options="currentUserOptions"')
    expect(schedule).not.toContain('最近预警ID')
  })

  it('encodes non-empty project filters and passes the abort signal', async () => {
    const controller = new AbortController()
    await loadProjectPage(
      { pageNo: 2, pageSize: 20, keyword: ' 项目 A&B ', projectType: '', status: 'ACTIVE' },
      controller.signal,
    )

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/projects?pageNo=2&pageSize=20&keyword=%E9%A1%B9%E7%9B%AE+A%26B&status=ACTIVE',
    )
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
  })

  it('uses only current project detail, overview and member endpoints', async () => {
    fetchMock.mockImplementation(async () => apiResponse({}))

    await loadProject('P/1')
    await loadProjectOverview('P/1')
    await loadProjectMembers('P/1', { pageNo: 1, roleCode: 'PM' })

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/projects/P%2F1',
      '/api/projects/P%2F1/overview',
      '/api/projects/P%2F1/members?pageNo=1&roleCode=PM',
    ])
  })

  it('excludes every existing non-deleted member from add-member candidates', async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/system/dict/data/by-code/')) return apiResponse([])
      if (url === '/api/projects/P1')
        return apiResponse({
          id: 'P1',
          tenantId: 'T1',
          orgId: 'O1',
          projectCode: 'P-1',
          projectName: '项目一',
          projectType: 'BUILD',
          projectAddress: '',
          ownerUnit: '',
          supervisorUnit: '',
          designUnit: '',
          contractAmount: '0',
          targetCost: '0',
          plannedStartDate: '2026-08-01',
          plannedEndDate: '2026-12-31',
          projectManagerId: 'U1',
          approvalStatus: 'APPROVED',
          status: 'ACTIVE',
          createdBy: '1',
          createdAt: '2026-08-11T00:00:00',
          updatedAt: '2026-08-11T00:00:00',
        })
      if (url === '/api/projects/P1/members?pageNo=1&pageSize=200')
        return apiResponse({
          records: [
            {
              id: 'M1',
              tenantId: 'T1',
              projectId: 'P1',
              userId: 'U1',
              roleCode: 'PM',
              status: 'INACTIVE',
              createdBy: '1',
              createdAt: '2026-08-11T00:00:00',
              updatedAt: '2026-08-11T00:00:00',
            },
          ],
          total: 1,
          pageNo: 1,
          pageSize: 200,
        })
      if (url === '/api/system/users?pageNo=1&pageSize=200')
        return apiResponse({
          records: [
            { id: 'U1', username: 'existing', realName: '现有离岗成员', status: 'ENABLE' },
            { id: 'U2', username: 'candidate', realName: '可选用户', status: 'ENABLE' },
          ],
          total: 2,
          pageNo: 1,
          pageSize: 200,
        })
      throw new Error(`unexpected request: ${url}`)
    })
    setActivePinia(createPinia())
    useSessionStore().replaceUserInfo({
      tenantId: 'T1',
      userId: 'ADMIN',
      username: 'admin',
      roles: ['ADMIN'],
      permissions: ['project:member:list', 'project:member:add', 'system:user:query'],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/project/:projectId/members', component: ProjectPage }],
    })
    await router.push('/project/P1/members')
    await router.isReady()
    const wrapper = mount(ProjectPage, {
      attachTo: document.body,
      global: { plugins: [router] },
    })
    await flushPromises()
    const addMember = [...document.querySelectorAll<HTMLButtonElement>('button')].find(
      (item) => item.textContent?.trim() === '添加成员',
    )
    if (!addMember) throw new Error('missing add-member button')
    addMember.click()
    await flushPromises()

    expect(
      [...document.querySelectorAll<HTMLSelectElement>('select[aria-label="用户"] option')].map(
        (option) => option.value,
      ),
    ).toEqual(['', 'U2'])
    expect(
      [...document.querySelectorAll<HTMLSelectElement>('select[aria-label="项目角色"] option')].map(
        (option) => option.value,
      ),
    ).toEqual([
      '',
      'PROJECT_MANAGER',
      'PROJECT_ACCOUNTANT',
      'TECHNICAL_LEAD',
      'SAFETY_LEAD',
      'CONSTRUCTION_LEAD',
      'PROCUREMENT_LEAD',
      'EMPLOYEE',
    ])
    wrapper.unmount()
  })

  it('rejects an empty project id before sending a request', () => {
    expect(() => loadProject('  ')).toThrow('项目ID不能为空')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('keeps authoritative overview amounts as strings', async () => {
    fetchMock.mockImplementationOnce(async () =>
      apiResponse({
        projectId: '1',
        contractCount: '2',
        totalContractAmount: '9007199254740993.01',
        dynamicCost: '10.00',
        paidAmount: '3.20',
        warningCount: '0',
        memberCount: '0',
        members: [],
      }),
    )

    await expect(loadProjectOverview('1')).resolves.toMatchObject({
      totalContractAmount: '9007199254740993.01',
      dynamicCost: '10.00',
    })
  })
})
