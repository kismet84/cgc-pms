import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const page = readFileSync(
  resolve(process.cwd(), 'src/pages/delivery/ProjectCloseoutPage.vue'),
  'utf8',
)
const service = readFileSync(resolve(process.cwd(), 'src/services/closeout.ts'), 'utf8')

describe('M91 F03 closeout server pagination', () => {
  it('uses the authoritative server page without project fan-out or client slicing', () => {
    expect(page).toContain('loadCloseoutPage(')
    expect(page).toContain(':total="scopedOverviewTotal"')
    expect(page).not.toContain('scopeProjectIds')
    expect(page).not.toContain('workspace.projects.map')
    expect(page).not.toContain('.slice((pageNo.value - 1) * pageSize')
    expect(service).toContain('CLOSEOUT_API.page')
  })

  it('keeps abort, generation and selected-project detail compatibility', () => {
    expect(page).toContain('projectController?.abort()')
    expect(page).toContain('const requestGeneration = ++generation')
    expect(page).toContain('requestGeneration === generation')
    expect(page).toContain('loadCloseoutOverview(projectId.value')
    expect(page).toContain('loadCloseoutTrace(closeoutId')
  })
})
