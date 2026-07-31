import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const api = readFileSync(resolve(currentDir, '../../../../api/modules/document.ts'), 'utf-8')

describe('业务单据模板治理页', () => {
  it('keeps query, edit, publish and preview actions behind separate permissions', () => {
    expect(source).toContain("userStore.hasPermission('document:template:edit')")
    expect(source).toContain("userStore.hasPermission('document:template:publish')")
    expect(source).toContain("userStore.hasPermission('document:generate')")
    expect(source).toContain('需同时具备模板维护和单据生成权限')
  })

  it('offers field insertion, draft validation, import/export and default CAS switching', () => {
    expect(source).toContain('insertCollection')
    expect(source).toContain('校验草稿')
    expect(source).toContain('导入模板')
    expect(source).toContain('导出')
    expect(source).toContain('bindDocumentDefaultTemplate')
    expect(source).toContain('expectedLockVersion')
  })

  it('uses server-side validation and saved-version PDF preview endpoints', () => {
    expect(api).toContain("url: '/document-templates/validate'")
    expect(api).toContain('previewDocumentTemplateVersion')
    expect(api).toContain("responseType: 'blob'")
    expect(api).toContain('/document-templates/versions/${versionId}/preview')
  })
})
