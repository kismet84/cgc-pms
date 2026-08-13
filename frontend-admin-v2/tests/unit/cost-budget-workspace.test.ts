import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('cost budget workspace boundary', () => {
  it('uses CostTargetPage directly and keeps no transparent wrapper', () => {
    const router = readFileSync(resolve('src/router/components.ts'), 'utf8')
    const page = readFileSync(resolve('src/pages/commercial/CostTargetPage.vue'), 'utf8')

    expect(router).toContain("'/cost-budget': CostTargetPage")
    expect(router).not.toContain('CostBudgetPage')
    expect(page).toContain("route.path === '/cost-budget' || props.embedded === true")
    expect(existsSync(resolve('src/pages/commercial/CostBudgetPage.vue'))).toBe(false)
  })
})
