import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve(import.meta.dirname, '../..')
const read = (path: string) => readFileSync(resolve(root, path), 'utf8')

describe('M4 bid-cost compatibility', () => {
  it('keeps legacy bookmark as an explicit redirect', () => {
    const router = read('src/router.ts')
    expect(router).toContain("path: '/bid-cost'")
    expect(router).toContain("path: '/engineering-tender/records'")
    expect(router).toContain("name: 'LegacyBidCostRedirect'")
  })

  it('keeps the existing backend API while moving product routes', () => {
    const contract = read('../packages/frontend-contracts/src/commercial.ts')
    const service = read('src/services/commercial.ts')
    expect(contract).toContain('bidCosts: "/bid-cost"')
    expect(service).toContain('COMMERCIAL_API.bidCosts')
  })

  it('uses the independent record detail route', () => {
    const page = read('src/pages/commercial/BidCostPage.vue')
    expect(page).toContain('/engineering-tender/records/${record.id}')
    expect(page).toContain("record.bidStatus === 'PREPARING'")
    expect(page).not.toContain("record.bidStatus === 'BIDDING'")
  })

  it('retains backend-authoritative CRUD and permission gates', () => {
    const page = read('src/pages/commercial/BidCostPage.vue')
    for (const permission of ['bid:query', 'bid:add', 'bid:edit', 'bid:delete', 'bid:status']) {
      expect(page).toContain(permission)
    }
    for (const call of ['loadBidCostPage', 'createBidCost', 'updateBidCost', 'deleteBidCost']) {
      expect(page).toContain(call)
    }
  })
})
