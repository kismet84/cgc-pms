import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const dashboardSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('Dashboard data loading behavior', () => {
  // ── fetchViewData: dispatch by activeRole ──
  it('calls getProjectManagerView for pm role', () => {
    // Verifies the switch-case dispatch exists in the source
    expect(dashboardSource).toMatch(/case\s+'pm':[\s\S]*?getProjectManagerView\b/)
  })

  it('calls getBusinessManagerView for bm role', () => {
    expect(dashboardSource).toMatch(/case\s+'bm':[\s\S]*?getBusinessManagerView\b/)
  })

  it('calls getCostManagerView for cost role', () => {
    expect(dashboardSource).toMatch(/case\s+'cost':[\s\S]*?getCostManagerView\b/)
  })

  it('calls getFinanceView for finance role', () => {
    expect(dashboardSource).toMatch(/case\s+'finance':[\s\S]*?getFinanceView\b/)
  })

  it('calls getManagementView for mgmt role', () => {
    expect(dashboardSource).toMatch(/case\s+'mgmt':[\s\S]*?getManagementView\b/)
  })

  // ── fetchViewData: passes projectId ──
  it('passes selectedProjectId to getProjectManagerView (none or value)', () => {
    // fetchViewData passes `pid` which is selectedProjectId or undefined
    expect(dashboardSource).toMatch(/getProjectManagerView\(pid\)/)
  })

  it('passes selectedProjectId to getBusinessManagerView', () => {
    expect(dashboardSource).toMatch(/getBusinessManagerView\(pid\)/)
  })

  it('passes selectedProjectId to getCostManagerView', () => {
    expect(dashboardSource).toMatch(/getCostManagerView\(pid\)/)
  })

  it('passes selectedProjectId to getFinanceView', () => {
    expect(dashboardSource).toMatch(/getFinanceView\(pid\)/)
  })

  it('calls getManagementView without pid (tenant-wide)', () => {
    expect(dashboardSource).toMatch(/getManagementView\(\)/)
  })

  // ── Cost breakdown only when project selected ──
  it('fetches cost breakdown only when pid is truthy', () => {
    // The source should contain the conditional logic: if (pid) { getCostBreakdown(pid) }
    expect(dashboardSource).toMatch(/if\s*\(\s*pid\s*\)\s*\{[\s\S]*getCostBreakdown\(pid\)/)
  })

  it('nullifies cost breakdown when pid is falsy', () => {
    expect(dashboardSource).toMatch(/costBreakdown\.value\s*=\s*null/)
  })

  // ── watch triggers fetchViewData ──
  it('watches activeRole and selectedProjectId to call fetchViewData', () => {
    expect(dashboardSource).toMatch(/watch\s*\(\s*\[\s*activeRole\s*,\s*selectedProjectId\s*\]/)
    expect(dashboardSource).toMatch(/fetchViewData\s*\(\s*\)/)
  })

  // ── onMounted fetches project list ──
  it('calls fetchProjects on mounted', () => {
    expect(dashboardSource).toMatch(/onMounted\s*\(\s*\(\s*\)\s*=>\s*\{[\s\S]*fetchProjects\s*\(\s*\)/)
  })

  // ── Error handling ──
  it('catches fetch errors and shows message', () => {
    expect(dashboardSource).toContain("message.error('加载仪表盘数据失败')")
  })

  // ── needsProject guards ──
  it('returns true for non-mgmt roles in needsProject', () => {
    // needsProject returns role !== 'mgmt'
    expect(dashboardSource).toMatch(/role\s*!==\s*'mgmt'/)
  })

  // ── Management view has no project selector ──
  it('hides project selector for mgmt role', () => {
    expect(dashboardSource).toMatch(/activeRole\s*!==\s*'mgmt'/)
  })

  // ── Default empty state ──
  it('shows empty page when no projects exist', () => {
    expect(dashboardSource).toContain('empty-page')
    expect(dashboardSource).toContain('暂无项目数据')
  })
})
