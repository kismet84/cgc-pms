import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const listSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const editSource = readFileSync(resolve(currentDir, '../edit.vue'), 'utf-8')

describe('CostTarget modal flows', () => {
  it('opens create/edit/view in an a-modal from the list page', () => {
    expect(listSource).toMatch(/import\s+CostTargetEditPage\s+from\s+['"]\.\/edit\.vue['"]/)
    expect(listSource).toMatch(/const\s+targetModalVisible\s*=\s*ref\(false\)/)
    expect(listSource).toMatch(
      /const\s+targetModalMode\s*=\s*ref<'create'\s*\|\s*'edit'\s*\|\s*'view'>\('create'\)/,
    )
    expect(listSource).toMatch(
      /function\s+handleCreate\(\)[\s\S]*?targetModalVisible\.value\s*=\s*true/,
    )
    expect(listSource).toMatch(/function\s+handleEdit[\s\S]*?targetModalMode\.value\s*=\s*'edit'/)
    expect(listSource).toMatch(/function\s+handleView[\s\S]*?targetModalMode\.value\s*=\s*'view'/)
    expect(listSource).toMatch(/<a-modal[\s\S]*v-model:open="targetModalVisible"/)
    expect(listSource).toMatch(/:title="targetModalTitle"/)
    expect(listSource).toMatch(
      /const\s+targetModalTitle\s*=\s*computed\(\(\)\s*=>\s*\{[\s\S]*?'成本目标详情'[\s\S]*?'编辑成本目标'[\s\S]*?'新建成本目标'/,
    )
    expect(listSource).toMatch(/<CostTargetEditPage[\s\S]*:embedded="true"/)
    expect(listSource).toMatch(/<CostTargetEditPage[\s\S]*:mode="targetModalMode"/)
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
    expect(editSource).toMatch(/mode\?: 'create' \| 'edit' \| 'view'/)
    expect(editSource).toMatch(/const\s+isEmbedded\s*=\s*computed\(\(\)\s*=>\s*props\.embedded\)/)
    expect(editSource).toMatch(
      /const\s+editId\s*=\s*computed\(\(\)\s*=>\s*props\.targetId\s*\|\|\s*String\(route\.params\.id\s*\|\|\s*''\)\)/,
    )
    expect(editSource).toMatch(/function\s+finishClose\(\)[\s\S]*?emit\('close'\)/)
    expect(editSource).toMatch(/function\s+doSubmit[\s\S]*?emit\('saved'\)/)
    expect(editSource).toMatch(/<div\s+v-if="!isEmbedded"\s+class="pt-page-head"/)
  })

  it('keeps view mode strictly readonly in the embedded detail modal', () => {
    expect(editSource).toMatch(/const\s+isView\s*=\s*computed\(\(\)\s*=>\s*props\.mode === 'view'\)/)
    expect(editSource).toMatch(/const\s+pageTitle\s*=\s*computed\(\(\)\s*=>\s*\{[\s\S]*?'成本目标详情'/)
    expect(editSource).toMatch(/const\s+closeText\s*=\s*computed\(\(\)\s*=>\s*\(isView\.value \? '关闭' : '取消'\)\)/)
    expect(editSource).toMatch(/if\s*\(isView\.value\s*\|\|\s*saving\.value\)\s*return/)
    expect(editSource).toMatch(/<a-button\s+v-if="!isView"\s+:loading="saving"\s+@click="handleSave">保存<\/a-button>/)
    expect(editSource).toMatch(
      /<a-button\s+v-if="!isView"\s+type="primary"\s+:loading="saving && !submitting"\s+@click="handleSubmit">/,
    )
    expect(editSource).toMatch(/<template v-if="!isView">[\s\S]*保存[\s\S]*提交审批[\s\S]*<\/template>/)
    expect(editSource).toMatch(/<div v-if="!isView" class="cte-toolbar">/)
    expect(editSource).toMatch(/:disabled="isView"/)
    expect(editSource).toMatch(/:readonly="isView"/)
  })
})
