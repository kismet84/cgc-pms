import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../ContractLedgerPage.vue'), 'utf-8')

describe('ContractLedgerPage modal flows', () => {
  it('opens the contract form inside an a-modal for create and edit', () => {
    expect(source).toMatch(/import\s+ContractFormPage\s+from\s+['"]\.\/ContractFormPage\.vue['"]/)
    expect(source).toMatch(/const\s+contractModalVisible\s*=\s*ref\(false\)/)
    expect(source).toMatch(/const\s+contractModalMode\s*=\s*ref<'create'\s*\|\s*'edit'>\('create'\)/)
    expect(source).toMatch(/const\s+contractModalId\s*=\s*ref\(''\)/)
    expect(source).toMatch(/function\s+handleCreate\(\)[\s\S]*?contractModalVisible\.value\s*=\s*true/)
    expect(source).toMatch(/function\s+handleEdit[\s\S]*?contractModalMode\.value\s*=\s*'edit'/)
    expect(source).toMatch(/<a-modal[\s\S]*v-model:open="contractModalVisible"/)
    expect(source).toMatch(/<ContractFormPage[\s\S]*:embedded="true"/)
    expect(source).toMatch(/@saved="handleContractSaved"/)
    expect(source).toMatch(/@close="handleContractClose"/)
  })

  it('refreshes ledger data after modal save', () => {
    expect(source).toMatch(/function\s+handleContractSaved\(\)[\s\S]*?fetchData\(\)/)
    expect(source).toMatch(/function\s+handleContractSaved\(\)[\s\S]*?fetchKpi\(\)/)
  })

  it('keeps existing detail and delete actions routed through the list page', () => {
    expect(source).toMatch(/function handleView[\s\S]*?router\.push\('\/contract\/'\s*\+\s*row\.id\)/)
    expect(source).toMatch(/function handleDelete[\s\S]*?Modal\.confirm\(/)
  })
})
