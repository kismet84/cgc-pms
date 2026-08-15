import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

describe('M94 project route boundaries', () => {
  it('routes all four project responsibilities to focused pages', () => {
    const router = [
      source('src/router/components.ts'),
      source('src/router/context-routes.ts'),
    ].join('\n')
    const routes = {
      '/project/list': 'ProjectListPage',
      '/project/:projectId/overview': 'ProjectOverviewPage',
      '/project/:projectId/members': 'ProjectMembersPage',
      '/project/:projectId/edit': 'ProjectEditPage',
    }

    for (const [path, component] of Object.entries(routes)) {
      expect(router).toContain(`const ${component} = () =>`)
      if (path === '/project/list') expect(router).toContain(`'${path}': ${component}`)
      else
        expect(router).toMatch(
          new RegExp(
            `path: '${path.replaceAll('/', '\\/')}'[\\s\\S]{0,180}component: ${component}`,
          ),
        )
    }
    expect(new Set(Object.values(routes)).size).toBe(4)
  })

  it('keeps the legacy public import as a thin route dispatcher', () => {
    const compatibility = source('src/pages/projects/ProjectPage.vue')

    for (const component of [
      'ProjectListPage',
      'ProjectOverviewPage',
      'ProjectMembersPage',
      'ProjectEditPage',
    ])
      expect(compatibility).toContain(component)
    expect(compatibility).not.toContain("from '@/services/projects'")
    expect(compatibility).not.toContain('onBeforeUnmount')
    expect(compatibility.split('\n').length).toBeLessThan(60)
  })

  it('keeps list query, creation and ledger actions in the list page', () => {
    const page = source('src/pages/projects/project-routes/ProjectListPage.vue')

    expect(page).toContain('loadProjectPage')
    expect(page).toContain('loadProject(contextProjectId.value')
    expect(page).toMatch(
      /async function setQuery[\s\S]*router\.resolve[\s\S]*await router\.replace/,
    )
    expect(page).toMatch(
      /async function search[\s\S]*if \(!\(await setQuery\(\)\)\) await load\(\)/,
    )
    expect(page).toContain('@update:model-value="applySelectFilter(\'projectType\', $event)"')
    expect(page).toContain('@update:model-value="applySelectFilter(\'status\', $event)"')
    for (const action of ['createProject', 'archiveProject', 'submitProject', 'deleteProject'])
      expect(page).toContain(action)
    expect(page).not.toMatch(/loadProjectOverview|loadProjectMembers|updateProject\b/)
  })

  it('keeps readiness, commencement and closeout effects in overview only', () => {
    const page = source('src/pages/projects/project-routes/ProjectOverviewPage.vue')

    expect(page).toMatch(
      /loadProjectOverview[\s\S]*loadCloseoutOverview[\s\S]*loadProjectActivationReadiness[\s\S]*loadProjectCommencement/,
    )
    expect(page).toContain("can('project:commencement:query')")
    expect(page).toContain("can('closeout:query')")
    expect(page).toContain('PROJECT_COMMENCEMENT')
    expect(page).toContain('服务端阻塞项')
    expect(page).toContain('施工事实不可见')
    expect(page).toContain('await load(true)')
    expect(page).not.toMatch(/loadProjectPage|loadProjectMembers|addProjectMember|updateProject\b/)
  })

  it('keeps member DTO, candidates and historical roles in the member page', () => {
    const page = source('src/pages/projects/project-routes/ProjectMembersPage.vue')

    expect(page).toContain('cleanMemberCommand')
    expect(page).toContain('loadProjectMembers')
    expect(page).toContain('loadProjectMemberOptions')
    expect(page).toContain('projectRoleOptions')
    expect(page).toContain('new Set(members.value.map((member) => member.userId))')
    expect(page).toContain('class="project-page__form-span"')
    expect(page).not.toContain('loadProjectUsers')
    expect(page).not.toContain('system:user:query')
    expect(page).not.toMatch(
      /loadProjectPage|loadProjectOverview|loadProjectCommencement|updateProject\b/,
    )
  })

  it('keeps editable whitelist, historical project type and server reread in edit', () => {
    const page = source('src/pages/projects/project-routes/ProjectEditPage.vue')
    const model = source('src/pages/projects/project-routes/model.ts')

    expect(page).toContain('cleanProjectCommand')
    expect(page).toContain('projectCommand')
    expect(page).toContain('updateProject')
    expect(page).toMatch(/await updateProject[\s\S]*await load\(true\)/)
    expect(page).toContain('dictionaryOptions(projectTypes.value, project.value?.projectType)')
    expect(model).toContain('（历史值，只读）')
    expect(page).not.toMatch(/loadProjectPage|loadProjectOverview|loadProjectMembers/)
  })

  it('keeps shared styling bounded and every focused page independently loaded', () => {
    const css = source('src/pages/projects/project-routes/project-pages.css')

    expect(css).toMatch(
      /\.project-page__overview-stack\s*\{[^}]*grid-template-columns: minmax\(0, 1fr\)/,
    )
    expect(css).toMatch(
      /\.project-page__overview-cost-facts\s*\{[^}]*grid-template-columns: repeat\(4, minmax\(0, 1fr\)\)/,
    )
    expect(css).toMatch(
      /\.project-page__detail-actions\s*\{[^}]*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/,
    )
    for (const component of [
      'ProjectListPage.vue',
      'ProjectOverviewPage.vue',
      'ProjectMembersPage.vue',
      'ProjectEditPage.vue',
    ]) {
      const page = source(`src/pages/projects/project-routes/${component}`)
      expect(page).toContain('watch(')
      expect(page).toContain('onBeforeUnmount')
      expect(page.split('\n').length).toBeLessThan(700)
    }
  })
})
