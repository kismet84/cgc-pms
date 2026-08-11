import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

describe('M91 F03 supplier workspace pagination', () => {
  it('uses one server workspace request and bounded server pages', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/supply-chain/SupplierSourcingPage.vue'),
      'utf8',
    )
    const loadPage =
      source.match(/async function loadPage[\s\S]*?\n}\n\nasync function selectEvent/)?.[0] ?? ''

    expect(loadPage).toContain('loadSupplierSourcingWorkspace')
    expect(loadPage).toContain('eventPageNo: pageNo.value')
    expect(loadPage).toContain('performancePageNo: performancePageNo.value')
    expect(loadPage).toContain('returnPageNo: returnPageNo.value')
    expect(loadPage).not.toContain('loadPartners')
    expect(loadPage).not.toContain('workspace.projects')
    expect(source).not.toContain('pagedEvents')
    expect(source).not.toContain('pagedPerformance')
    expect(source).not.toContain('pagedReturns')
    expect(source).toContain(':total="eventTotal"')
    expect(source).toContain(':total="performanceTotal"')
    expect(source).toContain(':total="returnTotal"')
    expect(source).toContain('workspacePartnerLabel(item)')
  })

  it('loads evaluated-order candidates from a server-side not-evaluated query', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/supply-chain/SupplierSourcingPage.vue'),
      'utf8',
    )
    expect(source).toContain('loadSupplierPerformanceCandidates')
    expect(source).not.toContain('evaluation.purchaseOrderId === item.id')
  })
})
