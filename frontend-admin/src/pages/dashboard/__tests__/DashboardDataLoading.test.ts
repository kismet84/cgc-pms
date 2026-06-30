import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const { mockRequest } = vi.hoisted(() => ({
  mockRequest: vi.fn(() => Promise.resolve({})),
}))

vi.mock('@/api/request', () => ({
  request: mockRequest,
}))

import {
  getChiefEngineerView,
  getCostManagerView,
  getProductionManagerView,
  getProjectManagerView,
  getPurchaseManagerView,
} from '@/api/modules/dashboard'

const currentDir = dirname(fileURLToPath(import.meta.url))
const composableSource = readFileSync(
  resolve(currentDir, '../composables/useDashboardData.ts'),
  'utf-8',
)
const dashboardApiSource = readFileSync(
  resolve(currentDir, '../../../api/modules/dashboard.ts'),
  'utf-8',
)

describe('Dashboard data loading behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── fetchViewData: dispatch by activeRole ──
  it('calls getProjectManagerView for pm role', () => {
    // The switch-case dispatch lives in useDashboardData.ts
    expect(composableSource).toMatch(/case\s+'pm':[\s\S]*?getProjectManagerView\b/)
  })

  it('calls getBusinessManagerView for bm role', () => {
    expect(composableSource).toMatch(/case\s+'bm':[\s\S]*?getBusinessManagerView\b/)
  })

  it('calls getCostManagerView for cost role', () => {
    expect(composableSource).toMatch(/case\s+'cost':[\s\S]*?getCostManagerView\b/)
  })

  it('calls getPurchaseManagerView for purchase role', () => {
    expect(composableSource).toMatch(/case\s+'purchase':[\s\S]*?getPurchaseManagerView\b/)
  })

  it('calls getProductionManagerView for production role', () => {
    expect(composableSource).toMatch(/case\s+'production':[\s\S]*?getProductionManagerView\b/)
  })

  it('calls getChiefEngineerView for chiefEngineer role', () => {
    expect(composableSource).toMatch(/case\s+'chiefEngineer':[\s\S]*?getChiefEngineerView\b/)
  })

  it('calls getFinanceView for finance role', () => {
    expect(composableSource).toMatch(/case\s+'finance':[\s\S]*?getFinanceView\b/)
  })

  it('calls getManagementView for mgmt role', () => {
    expect(composableSource).toMatch(/case\s+'mgmt':[\s\S]*?getManagementView\b/)
  })

  // ── fetchViewData: passes projectId ──
  it('passes selectedProjectId and selectedMonth to getProjectManagerView', () => {
    expect(composableSource).toMatch(/getProjectManagerView\(pid,\s*month\)/)
  })

  it('passes selectedProjectId to getBusinessManagerView', () => {
    expect(composableSource).toMatch(/getBusinessManagerView\(pid\)/)
  })

  it('passes selectedProjectId and selectedMonth to getCostManagerView', () => {
    expect(composableSource).toMatch(/getCostManagerView\(pid,\s*month\)/)
  })

  it('passes selectedProjectId to getFinanceView', () => {
    expect(composableSource).toMatch(/getFinanceView\(pid\)/)
  })

  it('passes selectedProjectId and selectedMonth to purchase/production/chiefEngineer views', () => {
    expect(composableSource).toMatch(/getPurchaseManagerView\(pid,\s*month\)/)
    expect(composableSource).toMatch(/getProductionManagerView\(pid,\s*month\)/)
    expect(composableSource).toMatch(/getChiefEngineerView\(pid,\s*month\)/)
  })

  it('calls getManagementView without pid (tenant-wide)', () => {
    expect(composableSource).toMatch(/getManagementView\(\)/)
  })

  // ── Cost breakdown only when project selected ──
  it('fetches cost breakdown only when pid is truthy', () => {
    // The source should contain the conditional logic: if (pid) { getCostBreakdown(pid) }
    expect(composableSource).toMatch(/if\s*\(\s*pid\s*\)\s*\{[\s\S]*getCostBreakdown\(pid\)/)
  })

  it('nullifies cost breakdown when pid is falsy', () => {
    expect(composableSource).toMatch(/costBreakdown\.value\s*=\s*null/)
  })

  // ── watch triggers fetchViewData ──
  it('watches activeRole and selectedProjectId to call fetchViewData', () => {
    expect(composableSource).toMatch(
      /watch\s*\(\s*\[\s*activeRole\s*,\s*selectedProjectId\s*,\s*selectedMonth\s*\]/,
    )
    expect(composableSource).toMatch(/fetchViewData\s*\(\s*\)/)
  })

  // ── onMounted fetches project list and initializes first-screen data ──
  it('loads projects and initializes dashboard data on mounted', () => {
    expect(composableSource).toMatch(
      /onMounted\s*\(\s*async\s*\(\s*\)\s*=>\s*\{[\s\S]*Promise\.allSettled\(\[\s*fetchProjects\(\),\s*fetchViewData\(\)\s*\]\)/,
    )
    expect(composableSource).toMatch(/selectedProjectId\.value\s*=\s*projectList\.value\[0\]\.id/)
    expect(composableSource).toMatch(/await\s+fetchViewData\s*\(\s*\)/)
    const mountedBlock = composableSource.match(
      /onMounted\s*\(\s*async\s*\(\s*\)\s*=>\s*\{[\s\S]*?\n  \}\)/,
    )?.[0]
    expect(mountedBlock).toBeDefined()
    expect(mountedBlock).not.toContain('return')
  })

  // ── Error handling ──
  it('catches fetch errors and shows message', () => {
    expect(composableSource).toContain("message.error('加载仪表盘数据失败')")
  })

  // ── needsProject guards ──
  it('returns true for non-mgmt roles in needsProject', () => {
    // needsProject returns role !== 'mgmt'
    expect(composableSource).toMatch(/role\s*!==\s*'mgmt'/)
  })

  it('treats SUPER_ADMIN as full-access role', () => {
    expect(composableSource).toMatch(/role\s*===\s*'ADMIN'\s*\|\|\s*role\s*===\s*'SUPER_ADMIN'/)
    expect(composableSource).toContain("'chiefEngineer'")
  })

  // ── Management view has no project selector ──
  it('hides project selector for mgmt role', () => {
    const indexSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
    expect(indexSource).toMatch(/activeRole\s*!==\s*'mgmt'/)
  })

  // ── Default empty state ──
  it('shows empty page when no projects exist', () => {
    const indexSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
    expect(indexSource).toContain('empty-page')
    expect(indexSource).toContain('暂无项目数据')
  })

  // ── API module: month param contract for the five visible tabs ──
  it('dashboard API functions for cost/pm/purchase/production/chiefEngineer accept month via dashboardParams', () => {
    const funcs = [
      'getProjectManagerView',
      'getCostManagerView',
      'getPurchaseManagerView',
      'getProductionManagerView',
      'getChiefEngineerView',
    ]
    for (const name of funcs) {
      // Each function declares optional month param
      expect(dashboardApiSource).toMatch(
        new RegExp(
          `export function ${name}\\(projectId\\?: string,\\s*month\\?: string\\)`,
        ),
      )
      // Each function body delegates to dashboardParams(projectId, month)
      expect(dashboardApiSource).toMatch(
        new RegExp(`${name}[\\s\\S]*?dashboardParams\\(projectId,\\s*month\\)`),
      )
    }
  })

  it('builds real request params with month for the five visible dashboard tabs', () => {
    const projectId = 'project-001'
    const month = '2026-05'

    getCostManagerView(projectId, month)
    getProjectManagerView(projectId, month)
    getPurchaseManagerView(projectId, month)
    getProductionManagerView(projectId, month)
    getChiefEngineerView(projectId, month)

    expect(mockRequest).toHaveBeenNthCalledWith(1, {
      url: '/dashboard/cost-manager',
      method: 'get',
      params: { projectId, month },
    })
    expect(mockRequest).toHaveBeenNthCalledWith(2, {
      url: '/dashboard/project-manager',
      method: 'get',
      params: { projectId, month },
    })
    expect(mockRequest).toHaveBeenNthCalledWith(3, {
      url: '/dashboard/purchase-manager',
      method: 'get',
      params: { projectId, month },
    })
    expect(mockRequest).toHaveBeenNthCalledWith(4, {
      url: '/dashboard/production-manager',
      method: 'get',
      params: { projectId, month },
    })
    expect(mockRequest).toHaveBeenNthCalledWith(5, {
      url: '/dashboard/chief-engineer',
      method: 'get',
      params: { projectId, month },
    })
  })
})
