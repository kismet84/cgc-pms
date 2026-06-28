import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { SOURCE_TYPE_LABEL } from '../cost'

const currentDir = dirname(fileURLToPath(import.meta.url))
const costLedgerSource = readFileSync(resolve(currentDir, '../../pages/cost/ledger.vue'), 'utf-8')

describe('cost source type labels', () => {
  it('maps backend source type aliases to Chinese display text', () => {
    expect(SOURCE_TYPE_LABEL.CT_MACHINE).toBe('机械使用成本')
    expect(SOURCE_TYPE_LABEL.VARIATION).toBe('签证变更成本')
    expect(SOURCE_TYPE_LABEL.MATERIAL_RECEIPT).toBe('材料验收成本')
    expect(SOURCE_TYPE_LABEL.CT_DIRECT).toBe('直接成本')
  })

  it('maps backend cost type aliases used by the cost ledger analysis rail', () => {
    expect(costLedgerSource).toMatch(/CT_MACHINE:\s*'机械使用成本'/)
    expect(costLedgerSource).toMatch(/VARIATION:\s*'签证变更成本'/)
    expect(costLedgerSource).toMatch(/MATERIAL_RECEIPT:\s*'材料验收成本'/)
  })
})
