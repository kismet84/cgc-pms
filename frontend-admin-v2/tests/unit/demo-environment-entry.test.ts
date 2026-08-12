import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('V2 示例环境入口', () => {
  const loginPage = readFileSync(resolve('src/pages/auth/LoginPage.vue'), 'utf-8')
  const appShell = readFileSync(resolve('src/layouts/AppShell.vue'), 'utf-8')
  const viteConfig = readFileSync(resolve('vite.config.ts'), 'utf-8')
  const devCompose = readFileSync(resolve('../deploy/docker-compose.dev.yml'), 'utf-8')

  it('仅在开发环境提供受控免密码入口', () => {
    expect(loginPage).toContain('const showDemoEntry = import.meta.env.DEV')
    expect(loginPage).toContain('/api/auth/dev-login?username=admin')
    expect(loginPage).toContain('免密码进入示例环境')
    expect(loginPage).toContain("credentials: 'same-origin'")
    expect(loginPage).toContain('本地示例登录失败，请检查后端与代理运行状态')
    expect(loginPage).not.toContain('请检查本地示例库')
  })

  it('仅通过本机端口提供代理并覆盖后端健康检查', () => {
    expect(viteConfig).toContain("proxyRequest.setHeader('host', '127.0.0.1:8080')")
    expect(devCompose).toContain('- "127.0.0.1:8080:8080"')
    expect(devCompose).toContain('- "127.0.0.1:5173:5173"')
    expect(devCompose).toContain(
      'wget -qO- http://127.0.0.1:5173/api/actuator/health >/dev/null || exit 1',
    )
  })

  it('将账号切换明确呈现为演示角色', () => {
    expect(appShell).toContain('const showDemoRoleSwitcher = import.meta.env.DEV')
    for (const [persona, username, label] of [
      ['COMPANY_OWNER', 'ui26.gm01', '公司老板'],
      ['COMPANY_FINANCE', 'ui26.fin01', '公司财务'],
      ['PROJECT_MANAGER', 'ui26.pm01', '项目经理'],
      ['PROJECT_ACCOUNTANT', 'ui26.cost01', '项目会计'],
      ['TECHNICAL_LEAD', 'ui26.chief01', '技术负责人'],
      ['SAFETY_LEAD', 'ui26.bm01', '安全负责人'],
      ['CONSTRUCTION_LEAD', 'ui26.prod01', '施工负责人'],
      ['PROCUREMENT_LEAD', 'ui26.pur01', '采购负责人'],
      ['EMPLOYEE', 'ui26.staff01', '员工'],
    ]) {
      expect(appShell).toContain(`persona: '${persona}'`)
      expect(appShell).toContain(`username: '${username}'`)
      expect(appShell).toContain(`label: '${label}'`)
    }
    expect(appShell).not.toContain('demoRoleGroups')
    expect(appShell).not.toContain('ui26.mgmt01')
    expect(appShell).not.toContain('ui26.mat01')
    expect(appShell).toContain('query.persona = account.persona')
    expect(appShell).toContain('query.role = account.role')
    expect(appShell).toContain('aria-label="切换演示角色"')
    expect(appShell).toContain('<strong>演示角色</strong>')
    expect(appShell).not.toContain('角色测试账号')
  })
})
