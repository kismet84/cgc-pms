import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readPage = (path: string) => readFileSync(resolve(currentDir, path), 'utf-8')

const pages = {
  purchaseRequest: readPage('../inventory/purchase-request.vue'),
  purchaseOrder: readPage('../purchase/order.vue'),
  receipt: readPage('../receipt/index.vue'),
  warehouse: readPage('../inventory/warehouse.vue'),
  stock: readPage('../inventory/stock.vue'),
  transaction: readPage('../inventory/transaction.vue'),
  requisition: readPage('../requisition/index.vue'),
  subcontractTask: readPage('../subcontract/task.vue'),
  subcontractMeasure: readPage('../subcontract/measure.vue'),
}

const searchSurfaces = [
  readPage('../inventory/components/PurchaseRequestSearchBar.vue'),
  readPage('../purchase/components/PurchaseOrderSearchBar.vue'),
  readPage('../inventory/components/StockSearchBar.vue'),
  pages.receipt,
  pages.warehouse,
  pages.transaction,
  pages.requisition,
  pages.subcontractTask,
  pages.subcontractMeasure,
]

describe('procurement and subcontract responsive UI contract', () => {
  it('applies the shared responsive shell to every secondary page', () => {
    Object.values(pages).forEach((source) => {
      expect(source).toContain('procurement-subcontract-list-page')
      expect(source).toContain('procurement-subcontract-table-panel')
    })
  })

  it('uses one expandable filter button instead of filter field settings', () => {
    searchSurfaces.forEach((source) => {
      expect(source).toContain('procurement-subcontract-query-panel')
      expect(source).toContain('procurement-subcontract-filter-panel')
      expect(source).toContain('procurement-subcontract-filter-toggle')
      expect(source).not.toContain('筛选栏设置')
    })
  })

  it('provides compact mobile records for every list page', () => {
    ;[
      pages.purchaseRequest,
      pages.purchaseOrder,
      pages.receipt,
      pages.warehouse,
      pages.requisition,
      pages.subcontractTask,
      pages.subcontractMeasure,
    ].forEach((source) => {
      expect(source).toContain('procurement-subcontract-mobile-list')
      expect(source).toContain('procurement-subcontract-mobile-card')
    })
    expect(pages.stock).toContain('class="lg-card-list"')
  })
})
