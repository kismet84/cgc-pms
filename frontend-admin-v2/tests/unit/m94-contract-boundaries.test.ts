import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { partnerCandidates } from '@/pages/commercial/contract/model'

const read = (path: string) => readFileSync(resolve(path), 'utf8')

describe('M94 contract route and type boundaries', () => {
  it('keeps an ineligible historical contract partner readable but disabled', () => {
    const partners = [
      { id: 'active', partnerName: '有效合作方' },
      { id: 'historical', partnerName: '历史合作方' },
    ] as Parameters<typeof partnerCandidates>[0]

    const options = partnerCandidates(
      partners,
      null,
      'historical',
      '历史合作方',
      (partner) => partner.id === 'active',
    )

    expect(options.find((option) => option.value === 'active')?.disabled).toBeUndefined()
    expect(options.find((option) => option.value === 'historical')).toEqual({
      value: 'historical',
      label: '历史合作方（历史值）',
      disabled: true,
    })
  })

  it('keeps the historical ContractPage import as a small compatibility dispatcher', () => {
    const compatibilityPage = read('src/pages/commercial/ContractPage.vue')

    for (const page of [
      'ContractLedgerPage',
      'ContractCreatePage',
      'ContractDetailPage',
      'ContractEditPage',
    ]) {
      expect(compatibilityPage).toContain(page)
    }
    expect(compatibilityPage.split(/\r?\n/).length).toBeLessThan(30)
  })

  it('assigns each canonical route to its focused page', () => {
    const router = [read('src/router/components.ts'), read('src/router/context-routes.ts')].join(
      '\n',
    )

    expect(router).toContain("'/contract/ledger': ContractLedgerPage")
    expect(router).toMatch(/path: '\/contract\/create',[\s\S]{0,120}component: ContractCreatePage/)
    expect(router).toMatch(/path: '\/contract\/:id',[\s\S]{0,120}component: ContractDetailPage/)
    expect(router).toMatch(/path: '\/contract\/:id\/edit',[\s\S]{0,120}component: ContractEditPage/)
  })

  it('uses the shared PageResult without collapsing domain projections', () => {
    const api = read('../packages/frontend-contracts/src/api.ts')
    const commercial = ['contracts.ts', 'cost.ts']
      .map((file) => read(`../packages/frontend-contracts/src/commercial/${file}`))
      .join('\n')
    const systemManagement = read('src/services/system-management/support.ts')

    expect(api.match(/export interface PageResult/g)).toHaveLength(1)
    expect(systemManagement).toContain(
      "import type { PageResult } from '@cgc-pms/frontend-contracts'",
    )
    expect(systemManagement).not.toMatch(/(?:interface|type)\s+PageResult/)
    expect(commercial).toContain('export type ContractPage = PageResult<ContractRecord>')
    expect(commercial).toContain('export type BudgetPage = PageResult<ProjectBudgetRecord>')
  })
})
