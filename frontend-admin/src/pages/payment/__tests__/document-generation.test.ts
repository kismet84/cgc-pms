import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const api = readFileSync(resolve(currentDir, '../../../api/modules/document.ts'), 'utf-8')

describe('付款申请单生成入口', () => {
  it('审批中仅预览，审批通过才允许正式生成', () => {
    expect(source).toContain('[APPROVAL_APPROVING, APPROVAL_APPROVED].includes(row.approvalStatus)')
    expect(source).toContain('row.approvalStatus === APPROVAL_APPROVED')
    expect(source).toContain('预览付款申请单')
    expect(source).toContain('生成并归档PDF')
  })

  it('按文档动作权限显示入口并提供历史下载', () => {
    expect(source).toContain("userStore.hasPermission('document:generate')")
    expect(source).toContain("userStore.hasPermission('document:history:query')")
    expect(source).toContain('单据生成历史')
    expect(source).toContain('getBusinessDocumentDownloadUrl')
  })

  it('文档API使用固定业务类型、幂等键和blob预览', () => {
    expect(source).toContain("generateBusinessDocument('PAYMENT', record.id, key)")
    expect(source).toContain('payment:${record.id}:approved:')
    expect(api).toContain("responseType: 'blob'")
    expect(api).toContain("url: '/documents/generations/preview'")
  })
})
