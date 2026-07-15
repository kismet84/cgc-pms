import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))

function readProject(path: string) {
  return readFileSync(resolve(currentDir, '..', path), 'utf-8')
}

function readTarget(path: string) {
  return readFileSync(resolve(currentDir, '../../cost-target', path), 'utf-8')
}

const sources = {
  projectList: readProject('index.vue'),
  projectQueryPanel: readProject('components/ProjectQueryPanel.vue'),
  projectAnalysisRail: readProject('components/ProjectAnalysisRail.vue'),
  projectOverview: readProject('overview.vue'),
  projectMembers: readProject('members.vue'),
  projectEdit: readProject('edit.vue'),
  targetList: readTarget('index.vue'),
  targetEdit: readTarget('edit.vue'),
}

const combinedSource = Object.values(sources).join('\n')

describe('Project and target UI redesign source markers', () => {
  it('applies the approved workspace UI language to project and target pages', () => {
    for (const marker of ['lg-']) {
      const anyMatch = Object.values(sources).some((s) => s.includes(marker))
      expect(anyMatch).toBe(true)
    }

    // Ensure at least one of the expected labels/markers is present
    const allText = combinedSource
    const hasContent =
      allText.includes('项目') || allText.includes('目标') || allText.includes('lg-')
    expect(hasContent).toBe(true)

    expect(combinedSource).not.toContain('成本目标管理')
  })

  it('uses the approved high-density project list layout', () => {
    expect(sources.projectList).not.toContain('ProjectKpiSummary')
    expect(sources.projectList).toContain('--lg-search-min-height: 60px')

    expect(sources.projectQueryPanel).toContain('display: flex')
    expect(sources.projectQueryPanel).toContain('flex-direction: column')
    expect(sources.projectQueryPanel).toContain('gap: 6px')
    expect(sources.projectQueryPanel).toContain('padding: 10px 14px')
    expect(sources.projectQueryPanel).toContain('min-height: 60px')
    expect(sources.projectQueryPanel).toContain('height: 40px')
    expect(sources.projectQueryPanel).toContain('justify-content: center')
    expect(sources.projectQueryPanel).not.toContain('筛选栏设置')

    for (const label of ['项目总数', '合同总金额', '在建项目', '已竣工项目', '风险项目']) {
      expect(sources.projectAnalysisRail).toContain(label)
    }
    expect(sources.projectAnalysisRail.match(/class="lg-analysis-panel/g)).toHaveLength(1)
  })
})
