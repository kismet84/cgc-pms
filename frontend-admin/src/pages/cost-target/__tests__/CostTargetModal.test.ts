import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const listSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const editSource = readFileSync(resolve(currentDir, '../edit.vue'), 'utf-8')

describe('CostTarget modal flows', () => {
  it('opens create/edit in an a-modal from the list page', () => {
    expect(listSource).toMatch(/import\s+CostTargetEditPage\s+from\s+['"]\.\/edit\.vue['"]/)
    expect(listSource).toMatch(/const\s+targetModalVisible\s*=\s*ref\(false\)/)
    expect(listSource).toMatch(/const\s+targetModalMode\s*=\s*ref<'create'\s*\|\s*'edit'>\('create'\)/)
    expect(listSource).toMatch(/function\s+handleCreate\(\)[\s\S]*?targetModalVisible\.value\s*=\s*true/)
    expect(listSource).toMatch(/function\s+handleEdit[\s\S]*?targetModalMode\.value\s*=\s*'edit'/)
    expect(listSource).toMatch(/<a-modal[\s\S]*v-model:open="targetModalVisible"/)
    expect(listSource).toMatch(/<CostTargetEditPage[\s\S]*:embedded="true"/)
    expect(listSource).toMatch(/@saved="handleTargetSaved"/)
    expect(listSource).toMatch(/@close="handleTargetClose"/)
  })

  it('refreshes the list after modal save', () => {
    expect(listSource).toMatch(/function\s+handleTargetSaved\(\)[\s\S]*?fetchData\(\)/)
  })

  it('keeps the edit page route-capable while supporting embedded mode', () => {
    expect(editSource).toMatch(/interface Props/)
    expect(editSource).toMatch(/embedded\?: boolean/)
    expect(editSource).toMatch(/targetId\?: string/)
    expect(editSource).toMatch(/mode\?: 'create' \| 'edit'/)
    expect(editSource).toMatch(/const\s+isEmbedded\s*=\s*computed\(\(\)\s*=>\s*props\.embedded\)/)
    expect(editSource).toMatch(/const\s+editId\s*=\s*computed\(\(\)\s*=>\s*props\.targetId\s*\|\|\s*String\(route\.params\.id\s*\|\|\s*''\)\)/)
    expect(editSource).toMatch(/function\s+finishClose\(\)[\s\S]*?emit\('close'\)/)
    expect(editSource).toMatch(/function\s+doSubmit[\s\S]*?emit\('saved'\)/)
    expect(editSource).toMatch(/<div\s+v-if="!isEmbedded"\s+class="pt-page-head"/)
  })
})
