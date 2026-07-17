import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('supplier sourcing performance workbench', () => {
  it('covers the required sourcing and performance chain', () => {
    expect(source).toContain(
      '采购需求 → 询价/招标 → 比价评审 → 定标 → 合同 → 交付质量 → 结算 → 综合评价/黑名单',
    )
    expect(source).toContain('publishSourcingEvent')
    expect(source).toContain('createBidEvaluation')
    expect(source).toContain('awardSourcingEvent')
    expect(source).toContain('linkSourcingContract')
    expect(source).toContain('getSourcingTrace')
  })

  it('keeps requirement, quote and supplier return evidence separate', () => {
    expect(source).toContain("'SOURCING_REQUIREMENT'")
    expect(source).toContain("'QUOTE_ATTACHMENT'")
    expect(source).toContain('createSupplierReturn')
    expect(source).toContain('confirmSupplierReturn')
    expect(source).toContain('trace.supplierReturns.length')
  })

  it('exposes segregated sourcing, performance and blacklist permissions', () => {
    expect(source).toContain("can('supplier:sourcing:maintain')")
    expect(source).toContain("can('supplier:sourcing:evaluate')")
    expect(source).toContain("can('supplier:sourcing:award')")
    expect(source).toContain("can('supplier:performance:evaluate')")
    expect(source).toContain("can('supplier:blacklist:review')")
  })
})
