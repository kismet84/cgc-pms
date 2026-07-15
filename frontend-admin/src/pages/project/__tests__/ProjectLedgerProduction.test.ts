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
    expect(source).toMatch(
      /import ProjectAnalysisRail from '\.\/components\/ProjectAnalysisRail\.vue'/,
    )
    expect(source).toMatch(
      /<ProjectQueryPanel[\s\S]*@search="handleSearch"[\s\S]*@reset="handleReset"/,
    )
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
    expect(queryPanelSource).toContain('v-model:value="filter.projectType"')
    expect(queryPanelSource).toContain('v-model:value="filter.status"')
    expect(queryPanelSource).toMatch(/function applyMobileFilters\(\)[\s\S]*?emit\('search'\)/)
    expect(queryPanelSource).toContain('class="project-search-submit-button"')
    expect(queryPanelSource).toContain('@click="emit(\'search\')"')
    expect(queryPanelSource).toContain('class="project-search-reset-button"')
    expect(queryPanelSource).toContain('@click="emit(\'reset\')"')
    expect(queryPanelSource).toMatch(
      /@media \(width < 500px\)[\s\S]*?\.project-search-submit-button,[\s\S]*?\.project-search-reset-button\s*\{[\s\S]*?display:\s*none;/,
    )
    expect(queryPanelSource).not.toContain('筛选栏设置')
    expect(queryPanelSource).not.toContain('SettingOutlined')
    expect(source).not.toContain('filterSettingItems')
    expect(source).not.toContain('filterVisibility')
  })

  it('restores and preserves list filters through URL query parameters', () => {
    const source = readProjectSource()
    expect(source).toMatch(/import\s+\{\s*useRoute,\s*useRouter\s*\}\s+from 'vue-router'/)
    expect(source).toMatch(/function restoreFilterFromRoute\(\)/)
    expect(source).toMatch(/filter\.keyword = readQueryString\('keyword'\) \|\| ''/)
    expect(source).toMatch(/filter\.projectType = readQueryString\('projectType'\)/)
    expect(source).toMatch(/filter\.status = readQueryString\('status'\)/)
    expect(source).toMatch(/pageNo\.value = readQueryNumber\('pageNo', 1\)/)
    expect(source).toMatch(/pageSize\.value = readQueryNumber\('pageSize', 20\)/)
    expect(source).toMatch(/function syncQueryToRoute\(\)/)
    expect(source).toMatch(/router\.replace\(\{ query \}\)/)
    expect(source).toMatch(
      /restoreFilterFromRoute\(\)[\s\S]*await fetchDictData\(PROJECT_TYPE_DICT\)/,
    )
    expect(source).toMatch(/syncQueryToRoute\(\)[\s\S]*getProjectList/)
  })

  it('keeps amount conversion helpers consistent for create and edit', () => {
    const source = readProjectSource()
    expect(source).toMatch(/function amountYuanToWan/)
    expect(source).toMatch(/function amountWanToYuan/)
    expect(source).toMatch(/editForm\.contractAmount = amountYuanToWan\(project\.contractAmount\)/)
    expect(source).toMatch(
      /contractAmount:\s*amountWanToYuan\((?:createForm|editForm)\.contractAmount\)/,
    )
  })

  it('contains a mobile card branch alongside desktop grid', () => {
    const source = readProjectComponentSource('ProjectTablePanel')
    const pageSource = readProjectSource()
    const querySource = readProjectComponentSource('ProjectQueryPanel')
    expect(source).toContain('v-if="isMobile"')
    expect(source).toContain('project-mobile-list')
    expect(source).toContain('v-else')
    expect(source).toContain('<vxe-grid')
    expect(source).toContain('role="link"')
    expect(source).toContain('@keydown.enter="emit(\'overview\', row)"')
    expect(source).toContain('project-mobile-card-summary')
    expect(source).not.toContain('class="project-mobile-card-actions"')
    expect(querySource).toContain('placeholder="搜索项目名称或编号"')
    expect(querySource).toContain('project-mobile-filter-panel')
    expect(querySource).not.toContain('<template v-else>')
    expect(querySource).toMatch(/\.project-mobile-search-row\s*\{[\s\S]*?align-items:\s*center;/)
    expect(querySource).toMatch(
      /\.project-mobile-filter-button\s*\{[\s\S]*?align-items:\s*center;[\s\S]*?justify-content:\s*center;/,
    )
    expect(source).toMatch(
      /\.project-table-panel\.lg-list-table-panel\s*\{[\s\S]*?flex:\s*0 0 auto;[\s\S]*?min-height:\s*0;/,
    )
    expect(source).toMatch(
      /\.project-table-panel \.project-table-wrap\s*\{[\s\S]*?flex:\s*0 0 auto;[\s\S]*?height:\s*auto;/,
    )
    expect(pageSource).toContain(
      "import { useMobileViewport } from '@/composables/useMobileViewport'",
    )
    expect(pageSource).toContain('v-if="!isMobile"')
    expect(pageSource).toContain('@overview="openProjectOverview"')
  })

  it('uses a full-width query row above a desktop list with a 20vw analysis rail', () => {
    const pageSource = readProjectSource()
    const analysisSource = readProjectComponentSource('ProjectAnalysisRail')
    expect(pageSource).toMatch(
      /\.project-workspace\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) 20vw;/,
    )
    expect(pageSource).toMatch(/\.project-main-column\s*\{\s*display:\s*contents;/)
    expect(pageSource).toMatch(
      /\.project-main-column\s*>\s*:deep\(\.project-query-panel\)[\s\S]*?grid-column:\s*1 \/ -1;/,
    )
    expect(pageSource).toMatch(
      /\.project-workspace\s*>\s*:deep\(\.project-analysis-rail\)[\s\S]*?width:\s*auto;[\s\S]*?margin-top:\s*0;/,
    )
    expect(pageSource).toMatch(
      /@media \(width < 500px\)[\s\S]*?\.project-workspace\s*\{\s*display:\s*block;/,
    )
    expect(analysisSource).toMatch(
      /\.project-analysis-rail\s*\{[\s\S]*?display:\s*flex;[\s\S]*?flex-direction:\s*column;/,
    )
    expect(analysisSource).toContain('@media (500px <= width < 900px)')
  })

  it('keeps ten pixels of vertical spacing around desktop toolbar buttons', () => {
    const source = readProjectComponentSource('ProjectTablePanel')
    expect(source).toMatch(/\.project-table-toolbar\s*\{[\s\S]*?padding:\s*10px 14px;/)
  })

  it('centers the desktop pagination controls with symmetric vertical padding', () => {
    const source = readProjectComponentSource('ProjectTablePanel')
    expect(source).toMatch(/\.project-pagination\s*\{[\s\S]*?padding:\s*8px 18px;/)
    expect(source).toMatch(
      /@media \(width < 500px\)[\s\S]*?\.project-pagination\s*\{[\s\S]*?padding:\s*2px 0;/,
    )
  })

  it('uses projectTypeLabel fallback instead of directly rendering raw projectType', () => {
    const source = readProjectSource()
    const queryPanelSource = readProjectComponentSource('ProjectQueryPanel')
    const tablePanelSource = readProjectComponentSource('ProjectTablePanel')
    expect(source).toContain("const PROJECT_TYPE_DICT = 'project_type'")
    expect(source).toContain('const PROJECT_TYPE_LABEL: Record<string, string>')
    expect(source).toContain("BUILDING: '施工总承包'")
    expect(source).toContain('function projectTypeLabel(value: string | undefined)')
    expect(queryPanelSource).toContain('{{ projectTypeLabel(item) }}')
    expect(tablePanelSource).toContain('{{ projectTypeLabel(row.projectType) }}')
    expect(tablePanelSource).not.toContain("{{ row.projectType || '未分类' }}")
  })

  it('preloads project type dict before the first fetch to avoid raw code flashes', () => {
    const source = readProjectSource()
    expect(source).toMatch(
      /onMounted\(async \(\) => \{[\s\S]*await fetchDictData\(PROJECT_TYPE_DICT\)\s*await fetchData\(\)/,
    )
    expect(source).not.toContain('onMounted(fetchData)')
  })
})
