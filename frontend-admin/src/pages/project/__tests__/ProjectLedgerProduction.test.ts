import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))

function readProjectSource() {
  return readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
}

function readProjectComponentSource(name: string) {
  return readFileSync(resolve(currentDir, `../components/${name}.vue`), 'utf-8')
}

describe('ProjectLedgerProduction source guards', () => {
  it('mounts local split components while keeping key handlers on the entry page', () => {
    const source = readProjectSource()
    expect(source).toMatch(/import ProjectQueryPanel from '\.\/components\/ProjectQueryPanel\.vue'/)
    expect(source).toMatch(/import ProjectTablePanel from '\.\/components\/ProjectTablePanel\.vue'/)
    expect(source).toMatch(/import ProjectAnalysisRail from '\.\/components\/ProjectAnalysisRail\.vue'/)
    expect(source).toMatch(/<ProjectQueryPanel[\s\S]*@search="handleSearch"[\s\S]*@reset="handleReset"/)
    expect(source).toMatch(
      /<ProjectTablePanel[\s\S]*@refresh="fetchData"[\s\S]*@create="handleCreateModalOpen"/,
    )
    expect(source).toMatch(/function handleCreateSubmit\(/)
    expect(source).toMatch(/async function handleEditSubmit\(/)
    expect(source).toMatch(/function handleDelete\(/)
  })

  it('uses pageNo and carries projectType/status filters in fetchData', () => {
    const source = readProjectSource()
    const queryPanelSource = readProjectComponentSource('ProjectQueryPanel')
    expect(source).toMatch(/pageNo:\s*pageNo\.value/)
    expect(source).toMatch(/projectType:\s*filter\.projectType\s*\|\|\s*undefined/)
    expect(source).toMatch(/status:\s*filter\.status\s*\|\|\s*undefined/)
    expect(source).not.toMatch(/projectCode:\s*filter\.projectCode\s*\|\|\s*undefined/)
    expect(source).not.toMatch(/projectName:\s*filter\.projectName\s*\|\|\s*undefined/)
    expect(queryPanelSource).not.toMatch(/v-model:value="filter\.projectCode"/)
    expect(queryPanelSource).not.toMatch(/v-model:value="filter\.projectName"/)
    expect(queryPanelSource).toMatch(/v-model:value="filter\.projectType"[\s\S]*?@change="emit\('search'\)"/)
    expect(queryPanelSource).toMatch(/v-model:value="filter\.status"[\s\S]*?@change="emit\('search'\)"/)
  })

  it('keeps amount conversion helpers consistent for create and edit', () => {
    const source = readProjectSource()
    expect(source).toMatch(/function amountYuanToWan/)
    expect(source).toMatch(/function amountWanToYuan/)
    expect(source).toMatch(/editForm\.contractAmount = amountYuanToWan\(project\.contractAmount\)/)
    expect(source).toMatch(/contractAmount:\s*amountWanToYuan\((?:createForm|editForm)\.contractAmount\)/)
  })

  it('contains a mobile card branch alongside desktop grid', () => {
    const source = readProjectComponentSource('ProjectTablePanel')
    expect(source).toContain('v-if="isMobile"')
    expect(source).toContain('project-mobile-list')
    expect(source).toContain('v-else')
    expect(source).toContain('<vxe-grid')
    expect(source).toContain("emit('edit', row)")
    expect(source).toContain("emit('delete', row)")
  })

  it('uses projectTypeLabel fallback instead of directly rendering raw projectType', () => {
    const source = readProjectSource()
    const queryPanelSource = readProjectComponentSource('ProjectQueryPanel')
    const tablePanelSource = readProjectComponentSource('ProjectTablePanel')
    expect(source).toContain("const PROJECT_TYPE_DICT = 'project_type'")
    expect(source).toContain('const PROJECT_TYPE_LABEL: Record<string, string>')
    expect(source).toContain('function projectTypeLabel(value: string | undefined)')
    expect(queryPanelSource).toContain('{{ projectTypeLabel(item) }}')
    expect(tablePanelSource).toContain('{{ projectTypeLabel(row.projectType) }}')
    expect(tablePanelSource).not.toContain("{{ row.projectType || '未分类' }}")
  })
})
