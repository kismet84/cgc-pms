import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const ledgerSource = readFileSync(resolve(currentDir, '../ContractLedgerPage.vue'), 'utf-8')
const composableSource = readFileSync(
  resolve(currentDir, '../composables/useContractLedger.ts'),
  'utf-8',
)

describe('ContractLedgerPage modal flows', () => {
  it('opens the contract form inside an a-modal for create and edit', () => {
    expect(ledgerSource).toMatch(
      /import\s+ContractFormPage\s+from\s+['"]\.\/ContractFormPage\.vue['"]/,
    )
    // Modal state now lives in composable
    expect(composableSource).toMatch(/const\s+contractModalVisible\s*=\s*ref\(false\)/)
    expect(composableSource).toMatch(
      /const\s+contractModalMode\s*=\s*ref<'create'\s*\|\s*'edit'>\('create'\)/,
    )
    expect(composableSource).toMatch(/const\s+contractModalId\s*=\s*ref\(''\)/)
    expect(composableSource).toMatch(
      /function\s+handleCreate\(\)[\s\S]*?contractModalVisible\.value\s*=\s*true/,
    )
    expect(composableSource).toMatch(
      /function\s+handleEdit[\s\S]*?contractModalMode\.value\s*=\s*'edit'/,
    )
    // Template uses composable destructured values
    expect(ledgerSource).toMatch(/<a-modal[\s\S]*v-model:open="contractModalVisible"/)
    expect(ledgerSource).toMatch(/<ContractFormPage[\s\S]*:embedded="true"/)
    expect(ledgerSource).toMatch(/@saved="handleContractSaved"/)
    expect(ledgerSource).toMatch(/@close="handleContractClose"/)
  })

  it('refreshes ledger data after modal save', () => {
    expect(composableSource).toMatch(/function\s+handleContractSaved\(\)[\s\S]*?fetchData\(\)/)
    expect(composableSource).toMatch(/function\s+handleContractSaved\(\)[\s\S]*?fetchKpi\(\)/)
  })

  it('keeps existing detail and delete actions routed through the list page', () => {
    expect(composableSource).toMatch(
      /function handleView[\s\S]*?router\.push\('\/contract\/'\s*\+\s*row\.id\)/,
    )
    expect(composableSource).toMatch(/function handleDelete[\s\S]*?Modal\.confirm\(/)
  })
})
