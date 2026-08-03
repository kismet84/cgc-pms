import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('V2 示例环境入口', () => {
  const loginPage = readFileSync(resolve('src/pages/auth/LoginPage.vue'), 'utf-8')
  const appShell = readFileSync(resolve('src/layouts/AppShell.vue'), 'utf-8')

  it('仅在开发环境提供受控免密码入口', () => {
    expect(loginPage).toContain('const showDemoEntry = import.meta.env.DEV')
    expect(loginPage).toContain('/api/auth/dev-login?username=admin')
    expect(loginPage).toContain('免密码进入示例环境')
    expect(loginPage).toContain("credentials: 'same-origin'")
  })

  it('将账号切换明确呈现为演示角色', () => {
    expect(appShell).toContain('const showDemoRoleSwitcher = import.meta.env.DEV')
    expect(appShell).toContain("{ role: 'pm', prefix: 'pm', label: '项目经理' }")
    expect(appShell).toContain("{ role: 'purchase', prefix: 'pur', label: '采购经理' }")
    expect(appShell).toContain("{ role: 'finance', prefix: 'fin', label: '财务经理' }")
    expect(appShell).toContain('const demoRoleAccounts = demoRoleGroups.map((group)')
    expect(appShell).toContain('`ui26.${group.prefix}01`')
    expect(appShell).not.toContain('[1, 2, 3].map((index)')
    expect(appShell).toContain('aria-label="切换演示角色"')
    expect(appShell).toContain('<strong>演示角色</strong>')
    expect(appShell).not.toContain('角色测试账号')
  })
})
