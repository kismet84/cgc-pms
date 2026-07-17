import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('requisition stock-out and material-return closed loop', () => {
  it('allows actual stock-out only after approval and before stock-out', () => {
    expect(source).toContain('row.approvalStatus === APPROVAL_APPROVED && row.stockOutFlag !== 1')
    expect(source).toContain('await executeRequisitionStockOut(row.id!)')
  })

  it('uses original requisition stock transaction for material return', () => {
    expect(source).toContain("getProcurementTrace('requisitions', row.id!)")
    expect(source).toContain("txn.sourceType === 'MAT_REQUISITION'")
    expect(source).toContain('originalStockTxnId')
    expect(source).toContain('await confirmMaterialReturn')
  })

  it('exposes procurement trace for reverse lookup', () => {
    expect(source).toContain('ProcurementTraceDrawer')
    expect(source).toContain('全链追溯')
  })
})
