import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))

function readProjectSource() {
  return readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
}

describe('ProjectLedgerProduction source guards', () => {
  it('uses pageNo and carries projectType/status filters in fetchData', () => {
    const source = readProjectSource()
    expect(source).toMatch(/pageNo:\s*pageNo\.value/)
    expect(source).toMatch(/projectType:\s*filter\.projectType\s*\|\|\s*undefined/)
    expect(source).toMatch(/status:\s*filter\.status\s*\|\|\s*undefined/)
    expect(source).not.toMatch(/projectCode:\s*filter\.projectCode\s*\|\|\s*undefined/)
    expect(source).not.toMatch(/projectName:\s*filter\.projectName\s*\|\|\s*undefined/)
    expect(source).not.toMatch(/v-model:value="filter\.projectCode"/)
    expect(source).not.toMatch(/v-model:value="filter\.projectName"/)
    expect(source).toMatch(/v-model:value="filter\.projectType"[\s\S]*?@change="handleSearch"/)
    expect(source).toMatch(/v-model:value="filter\.status"[\s\S]*?@change="handleSearch"/)
  })

  it('keeps amount conversion helpers consistent for create and edit', () => {
    const source = readProjectSource()
    expect(source).toMatch(/function amountYuanToWan/)
    expect(source).toMatch(/function amountWanToYuan/)
    expect(source).toMatch(/editForm\.contractAmount = amountYuanToWan\(project\.contractAmount\)/)
    expect(source).toMatch(/contractAmount:\s*amountWanToYuan\((?:createForm|editForm)\.contractAmount\)/)
  })

  it('contains a mobile card branch alongside desktop grid', () => {
    const source = readProjectSource()
    expect(source).toContain('v-if="isMobile"')
    expect(source).toContain('project-mobile-list')
    expect(source).toContain('v-else')
    expect(source).toContain('<vxe-grid')
    expect(source).toContain('handleEditModalOpen(row)')
    expect(source).toContain('handleDelete(row)')
  })
})
