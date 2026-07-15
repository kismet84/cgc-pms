import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const overviewSource = readFileSync(resolve(currentDir, '../overview.vue'), 'utf-8')
const viewportSource = readFileSync(
  resolve(currentDir, '../../../composables/useMobileViewport.ts'),
  'utf-8',
)

describe('Project overview mobile layout', () => {
  it('uses a non-overlapping 499px mobile breakpoint', () => {
    expect(viewportSource).toContain('export const MOBILE_VIEWPORT_BREAKPOINT = 500')
    expect(viewportSource).toContain('`(width < ${MOBILE_VIEWPORT_BREAKPOINT}px)`')
    expect(viewportSource).toContain('window.matchMedia(MOBILE_VIEWPORT_QUERY)')
    expect(overviewSource).toContain(
      "import { useMobileViewport } from '@/composables/useMobileViewport'",
    )
    expect(overviewSource).toContain('@media (width < 500px)')
  })

  it('renders real project data in the compact mobile detail branch', () => {
    expect(overviewSource).toContain('v-if="isMobile" class="project-mobile-detail"')
    expect(overviewSource).toContain('getProjectDetail(projectId)')
    expect(overviewSource).toContain('{{ project.projectName }}')
    expect(overviewSource).toContain('{{ project.projectCode }}')
    expect(overviewSource).toContain("CONSTRUCTION: '施工总承包'")
    expect(overviewSource).toContain('项目关键数据')
    expect(overviewSource).toContain('合同与成本')
    expect(overviewSource).toContain('项目成员（{{ fmtNum(data.memberCount) }}）')
    expect(overviewSource).not.toContain('项目进度  35%')
  })
})
