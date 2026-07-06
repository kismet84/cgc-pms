import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pagePath = resolve(currentDir, '../index.vue')
const source = existsSync(pagePath) ? readFileSync(pagePath, 'utf-8') : ''
const routerSource = readFileSync(resolve(currentDir, '../../../../router/index.ts'), 'utf-8')
const navigationSource = readFileSync(resolve(currentDir, '../../../../router/navigation.ts'), 'utf-8')
const typeSource = readFileSync(resolve(currentDir, '../../../../types/system.ts'), 'utf-8')

describe('readonly permission governance page', () => {
  it('uses existing menu tree and role APIs without edit actions', () => {
    expect(source).toContain('getMenuTree')
    expect(source).toContain('getRoles')
    expect(source).toContain('function flattenPermissions')
    expect(source).toContain('permissionCode')
    expect(source).toContain('menuName')
    expect(source).toContain('path')
    expect(source).toContain('sourceRemark')
    expect(source).toContain('bindingStatus')
    expect(source).toContain("sourceRemark: '/system/menus/tree'")
    expect(source).toContain("bindingStatus: boundMenuIds.has(String(menu.id)) ? '已绑定' : '未绑定'")
    expect(source).not.toContain('updateRoleMenus')
    expect(source).not.toContain('保存权限')
  })

  it('registers the read-only permission list under system management', () => {
    expect(routerSource).toContain("path: 'permissions'")
    expect(routerSource).toContain("name: 'SystemPermissions'")
    expect(routerSource).toContain("@/pages/system/permissions/index.vue")
    expect(navigationSource).toContain("{ key: '/system/permissions', label: '权限清单', adminOnly: true }")
  })

  it('keeps menu permission code in the front-end menu tree type', () => {
    expect(typeSource).toContain('perms?: string')
  })
})
