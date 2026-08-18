import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { RouteRecordRaw } from 'vue-router'
import { describe, expect, it } from 'vitest'
import { navigationDomains } from '@/navigation/catalog'
import { routes } from '@/router'

const source = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')
const hash = (value: unknown) => createHash('sha256').update(JSON.stringify(value)).digest('hex')
const sourceHash = (value: string) => createHash('sha256').update(value).digest('hex')

function functionToken(value: unknown): string | null {
  if (typeof value !== 'function') return null
  const normalized = String(value).replaceAll(/\s+/g, ' ').trim()
  const imported = normalized.match(/import\(["']([^"']+)["']\)/)?.[1]
  return imported?.replace(/^(?:\.\.\/|\.\/)+/, '') ?? normalized
}

function routeContract(route: RouteRecordRaw): unknown {
  return {
    path: route.path,
    name: route.name ? String(route.name) : null,
    component: functionToken(route.component),
    redirect:
      typeof route.redirect === 'string'
        ? route.redirect
        : route.redirect
          ? functionToken(route.redirect)
          : null,
    meta: route.meta ?? null,
    children: route.children?.map(routeContract) ?? [],
  }
}

describe('M94 navigation and router boundaries', () => {
  it('freezes navigation domain data and public selector behavior', () => {
    expect(hash(navigationDomains)).toBe(
      '519e8daa749d14781c8bc0a0112ce5719572de08b4b50649087253c24c99b060',
    )
    expect(navigationDomains.map((domain) => domain.id)).toEqual([
      'workbench',
      'delivery',
      'construction',
      'commercial',
      'supply',
      'subcontract-settlement',
      'finance',
      'master-data',
      'system-management',
    ])
  })

  it('freezes route path, name, component, redirect and meta contracts', () => {
    expect(hash(routes.map(routeContract))).toBe(
      'e69dd18537214780a51c05e504a9b01a88b3aeffbb3d3b37ed1680d2ed50398f',
    )
  })

  it('freezes guest, session, permission and admin guard implementation', () => {
    const router = source('src/router.ts')
    const guard = router.slice(
      router.indexOf('export function installSessionGuard'),
      router.indexOf('function safeRedirect'),
    )
    expect(sourceHash(guard.replaceAll(/\s+/g, ' ').trim())).toBe(
      '8d62a662bb490813510b9f3fb215217e35d2bace93a55e10844d0373a7f92be5',
    )
  })

  it('keeps catalog as a small stable façade over explicit domain files', () => {
    const catalog = source('src/navigation/catalog.ts')
    const domainFiles = [
      'workbench.ts',
      'delivery.ts',
      'commercial.ts',
      'supply.ts',
      'finance.ts',
      'administration.ts',
    ]

    expect(catalog.split('\n').length).toBeLessThan(100)
    expect(catalog).not.toMatch(/\bid:\s*['"]/)
    for (const file of domainFiles) {
      const path = `src/navigation/domains/${file}`
      expect(existsSync(resolve(process.cwd(), path))).toBe(true)
      expect(catalog).toContain(`./domains/${file.replace('.ts', '')}`)
      expect(source(path)).toContain("from '../types'")
    }
  })

  it('keeps route records separate from router bootstrap and guards', () => {
    const router = source('src/router.ts')
    const components = source('src/router/components.ts')
    const contextRoutes = source('src/router/context-routes.ts')
    const registry = source('src/router/route-registry.ts')

    expect(router.split('\n').length).toBeLessThan(140)
    expect(router).toContain('installSessionGuard')
    expect(router).not.toMatch(/navigationComponents|contextRoutes|const \w+Page = \(\) => import/)
    expect(components).toContain("'/project/list': ProjectListPage")
    expect(components).toContain("'/contract/ledger': ContractLedgerPage")
    expect(components).toContain("'/purchase/order': PurchaseOrderWorkspace")
    expect(components).not.toMatch(/ProjectPage|ContractPage|PurchaseExecutionPage/)
    expect(contextRoutes).toContain('V2ShellProjectOverview')
    expect(contextRoutes).toContain('ProjectOverviewPage')
    expect(contextRoutes).toContain('ContractDetailPage')
    expect(registry).toContain('navigationDomains.flatMap')
    expect(registry).toContain('...contextRoutes')
    for (const routeSource of [components, contextRoutes, registry])
      expect(routeSource).not.toContain('useSessionStore')
  })
})
