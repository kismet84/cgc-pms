import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function source(path: string): string {
  return readFileSync(resolve('src', path), 'utf8')
}

describe('M94 focused route page boundaries', () => {
  it('routes the four cost-subject entries to focused pages', () => {
    const router = source('router/components.ts')

    expect(router).toMatch(
      /const CostSubjectTaxonomyPage = \(\) =>\s*import\('\.\.\/pages\/master-data\/cost-subject\/CostSubjectTaxonomyPage\.vue'\)/,
    )
    expect(router).toMatch(
      /const CostSubjectRulesPage = \(\) =>\s*import\('\.\.\/pages\/master-data\/cost-subject\/CostSubjectRulesPage\.vue'\)/,
    )
    expect(router).toMatch(
      /const CostSubjectScopePage = \(\) =>\s*import\('\.\.\/pages\/master-data\/cost-subject\/CostSubjectScopePage\.vue'\)/,
    )
    expect(router).toMatch(
      /const CostSubjectTracePage = \(\) =>\s*import\('\.\.\/pages\/master-data\/cost-subject\/CostSubjectTracePage\.vue'\)/,
    )
    expect(router).toContain("'/cost/subject/taxonomy': CostSubjectTaxonomyPage")
    expect(router).toContain("'/cost/subject/rules': CostSubjectRulesPage")
    expect(router).toContain("'/cost/subject/scope': CostSubjectScopePage")
    expect(router).toContain("'/cost/subject/trace': CostSubjectTracePage")
  })

  it('keeps cost-subject route state and service effects local to each page', () => {
    const taxonomy = source('pages/master-data/cost-subject/CostSubjectTaxonomyPage.vue')
    const rules = source('pages/master-data/cost-subject/CostSubjectRulesPage.vue')
    const scope = source('pages/master-data/cost-subject/CostSubjectScopePage.vue')
    const trace = source('pages/master-data/cost-subject/CostSubjectTracePage.vue')

    expect(taxonomy).toContain('loadCostSubjectTree')
    expect(taxonomy).not.toMatch(/loadMappingVersions|loadProjectScopes|loadBidTransfers/)
    expect(rules).toContain('loadMappingVersions')
    expect(rules).toContain('loadAssignmentRules')
    expect(rules).not.toMatch(/loadCostSubjectTree|loadProjectScopes|loadBidTransfers/)
    expect(scope).toContain('loadProjectConfiguration')
    expect(scope).toContain('createProjectConfigRequest')
    expect(scope).not.toMatch(/loadCostSubjectTree|loadMappingVersions|loadBidTransfers/)
    expect(trace).toMatch(/loadBidTransferRequests[\s\S]*loadFinanceAllocationRequests/)
    expect(trace).toContain('loadRecalculationBatches')
    expect(trace).toContain('loadReversalRequests')
    expect(trace).not.toMatch(/loadCostSubjectTree|loadMappingVersions|loadProjectScopes/)

    for (const focused of [taxonomy, rules, trace]) {
      expect(focused).not.toContain("from 'vue-router'")
    }
    expect(scope).toContain("from 'vue-router'")
  })

  it('routes the three access-control entries to focused pages', () => {
    const router = source('router/components.ts')

    expect(router).toMatch(
      /const UserManagementPage = \(\) =>\s*import\('\.\.\/pages\/system\/access-control\/UserManagementPage\.vue'\)/,
    )
    expect(router).toMatch(
      /const RoleManagementPage = \(\) =>\s*import\('\.\.\/pages\/system\/access-control\/RoleManagementPage\.vue'\)/,
    )
    expect(router).toMatch(
      /const PermissionListPage = \(\) =>\s*import\('\.\.\/pages\/system\/access-control\/PermissionListPage\.vue'\)/,
    )
    expect(router).toContain("'/system/users': UserManagementPage")
    expect(router).toContain("'/system/roles': RoleManagementPage")
    expect(router).toContain("'/system/permissions': PermissionListPage")
  })

  it('keeps user, role, and permission state in their focused pages', () => {
    const users = source('pages/system/access-control/UserManagementPage.vue')
    const roles = source('pages/system/access-control/RoleManagementPage.vue')
    const permissions = source('pages/system/access-control/PermissionListPage.vue')

    expect(users).toContain('loadUsers')
    expect(users).toContain('loadUser')
    expect(users).not.toMatch(/loadMenus|loadRole\b|assignRoleMenus/)
    expect(roles).toContain('loadRoles')
    expect(roles).not.toMatch(/loadUsers|loadMenus|loadRole\b|assignRoleMenus/)
    expect(permissions).toMatch(/assignRoleMenus[\s\S]*loadMenus[\s\S]*loadRole/)
    expect(permissions).not.toMatch(/loadUsers|loadUser\b/)

    for (const focused of [users, roles, permissions]) {
      expect(focused).not.toContain("from 'vue-router'")
    }
  })

  it('keeps legacy public page imports as thin route dispatchers', () => {
    const costCompatibility = source('pages/master-data/CostSubjectPage.vue')
    const accessCompatibility = source('pages/system/AccessControlPage.vue')

    expect(costCompatibility).toContain('CostSubjectTaxonomyPage')
    expect(costCompatibility).toContain('CostSubjectTracePage')
    expect(costCompatibility).not.toContain("from '@/services/cost-subject'")
    expect(accessCompatibility).toContain('UserManagementPage')
    expect(accessCompatibility).toContain('PermissionListPage')
    expect(accessCompatibility).not.toContain("from '@/services/system-management'")
  })
})
