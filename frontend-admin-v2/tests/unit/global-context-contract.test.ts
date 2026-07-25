import { readdirSync, readFileSync } from 'node:fs'
import { extname, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { navigationDomains } from '../../src/navigation/catalog'
import { routes } from '../../src/router'

const sourceRoot = resolve(import.meta.dirname, '../../src')

function filesUnder(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name)
    return entry.isDirectory() ? filesUnder(path) : [path]
  })
}

describe('full V2 public context contract', () => {
  it('keeps navigation paths unique and owned by the shared catalog', () => {
    const paths = navigationDomains.flatMap((domain) =>
      domain.workspaces.flatMap((workspace) => workspace.tabs.map((tab) => tab.path)),
    )
    expect(paths.length).toBeGreaterThan(0)
    expect(new Set(paths).size).toBe(paths.length)
  })

  it('discovers every navigation page from the shared catalog and permits placeholders only while migration is pending', () => {
    const shell = routes.find((route) => route.path === '/shell')
    const children = shell?.children ?? []
    const tabs = navigationDomains.flatMap((domain) =>
      domain.workspaces.flatMap((workspace) => workspace.tabs),
    )

    expect(children.length).toBeGreaterThan(tabs.length)
    for (const tab of tabs) {
      const matchingRoutes = children.filter((route) => route.path === tab.path)
      expect(matchingRoutes, `${tab.path} must resolve exactly once`).toHaveLength(1)
      const [route] = matchingRoutes
      expect(route?.meta?.permission, `${tab.path} permission`).toBe(tab.permission)
      expect(route?.meta?.migration, `${tab.path} migration state`).toBe(tab.migration)
      expect(
        String(route?.component).includes('ShellPlaceholderPage'),
        `${tab.path} placeholder state`,
      ).toBe(tab.migration === 'pending')
    }

    for (const route of children.filter((candidate) => candidate.component)) {
      expect(
        String(route.component).includes('ShellPlaceholderPage'),
        `${String(route.path)} placeholder must be explicitly migration-pending`,
      ).toBe(route.meta?.migration === 'pending')
    }
  })

  it('never treats all projects as a missing page context', () => {
    const pages = filesUnder(resolve(sourceRoot, 'pages')).filter(
      (path) => extname(path) === '.vue',
    )
    for (const path of pages) {
      const source = readFileSync(path, 'utf-8')
      expect(source, path).not.toMatch(/title="请(?:先)?选择项目"/)
    }
  })

  it('locks the global standard and executable browser gate', () => {
    const baseline = readFileSync(
      resolve(sourceRoot, '../../docs/ui-v2/m1-design-system-baseline.md'),
      'utf-8',
    )
    const browserGate = readFileSync(
      resolve(import.meta.dirname, '../../e2e/m1-global-context-contract.spec.ts'),
      'utf-8',
    )
    const migrationGate = readFileSync(
      resolve(import.meta.dirname, '../../scripts/run-migration-ui-gate.mjs'),
      'utf-8',
    )

    expect(baseline).toContain('全 V2 强制退出门')
    expect(baseline).toContain('不得维护会遗漏新路由的第二份手工页面清单')
    expect(browserGate).toContain("await select(page, '#global-project', 'P1')")
    expect(browserGate).toContain("await select(page, '#global-project', '')")
    expect(browserGate).toContain("await select(page, '#global-report-period', '')")
    expect(migrationGate).toContain('/^m\\d.*\\.spec\\.ts$/')
  })
})
