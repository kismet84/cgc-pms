import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../transaction.vue'), 'utf-8')

describe('inventory transaction permission gate', () => {
  it('keeps page access on list permission but hides write entry behind add permission', () => {
    expect(source).toContain("userStore.hasPermission('inventory:transaction:add')")
    expect(source).not.toContain("userStore.hasPermission('inventory:transaction:list')")
    expect(source.match(/v-if=\"canSubmitTransaction\"/g)).toHaveLength(2)
  })

  it('shows stock in and stock out submit actions only when add permission is present', () => {
    expect(source).toMatch(/确认入库[\s\S]*v-if=\"canSubmitTransaction\"|v-if=\"canSubmitTransaction\"[\s\S]*确认入库/)
    expect(source).toMatch(/确认出库[\s\S]*v-if=\"canSubmitTransaction\"|v-if=\"canSubmitTransaction\"[\s\S]*确认出库/)
  })
})
