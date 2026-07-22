import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  loadProject,
  loadProjectMembers,
  loadProjectOverview,
  loadProjectPage,
} from '@/services/projects'

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
  it('applies both project selects through the route before loading', () => {
    const source = readFileSync(resolve('src/pages/projects/ProjectPage.vue'), 'utf-8')
    expect(source).toContain(`@update:model-value="applySelectFilter('projectType', $event)"`)
    expect(source).toContain(`@update:model-value="applySelectFilter('status', $event)"`)
    expect(source).toMatch(
      /async function setQuery[\s\S]*router\.resolve[\s\S]*await router\.replace/,
    )
    expect(source).toMatch(
      /async function search[\s\S]*if \(!\(await setQuery\(\)\)\) await load\(\)/,
    )
  })

  it('snapshots the selected schedule directly and keeps daily actions behind confirmation', () => {
    const schedule = readFileSync(resolve('src/pages/delivery/SchedulePage.vue'), 'utf-8')
    const dailyLog = readFileSync(resolve('src/pages/delivery/DailyLogPage.vue'), 'utf-8')

    expect(schedule).toContain('@click="requestScheduleSubmit(item)"')
    expect(schedule).not.toContain('openDetail(item.id).then(() => requestScheduleSubmit())')
    expect(schedule).not.toContain('集中管理基线计划、WBS、月周计划、进度偏差与纠偏。')
    expect(dailyLog).toContain(':on-click="requestDailySubmit"')
    expect(dailyLog).toContain('@click="requestFileRemoval(file.id, file.originalName)"')
  })

  it('uses the validated public-shell project context and keeps aggregate routes on all projects', () => {
    const shell = readFileSync(resolve('src/layouts/AppShell.vue'), 'utf-8')
    const catalog = readFileSync(resolve('src/navigation/catalog.ts'), 'utf-8')

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
    for (const page of [
      'QualitySafetyPage.vue',
      'TechnicalManagementPage.vue',
      'ProjectCloseoutPage.vue',
    ]) {
      const source = readFileSync(resolve(`src/pages/delivery/${page}`), 'utf-8')
      expect(source).toContain('scopeProjectIds')
    }
    expect(readFileSync(resolve('src/pages/delivery/QualitySafetyPage.vue'), 'utf-8')).toMatch(
      /async function runWrite[\s\S]*if \(!projectId\.value\)/,
    )
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
