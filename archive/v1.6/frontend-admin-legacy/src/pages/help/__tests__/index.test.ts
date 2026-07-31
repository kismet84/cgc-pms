import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { h, defineComponent } from 'vue'

// ── Component stubs that render slots ──
const ATypographyTitle = defineComponent({
  props: { level: Number },
  setup(_, { slots }) {
    return () => h('h2', { 'data-level': _.level }, slots.default?.())
  },
})

const ATable = defineComponent({
  props: { columns: Array, dataSource: Array, pagination: [Object, Boolean], size: String },
  setup(props) {
    return () => {
      const rows = (props.dataSource as Record<string, string>[]) ?? []
      return h(
        'table',
        { class: 'stub-table' },
        h(
          'tbody',
          rows.map((row) =>
            h(
              'tr',
              { key: row.key },
              Object.values(row).map((val) => h('td', { key: val }, String(val))),
            ),
          ),
        ),
      )
    }
  },
})

const ACollapse = defineComponent({
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-collapse' }, slots.default?.())
  },
})

const ACollapsePanel = defineComponent({
  props: { header: String, panelKey: String },
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'stub-collapse-panel' }, [
        h('div', { class: 'stub-panel-header' }, props.header),
        h('div', { class: 'stub-panel-body' }, slots.default?.()),
      ])
  },
})

const ADescriptions = defineComponent({
  props: { column: Number, size: String },
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-descriptions' }, slots.default?.())
  },
})

const ADescriptionsItem = defineComponent({
  props: { label: String },
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'stub-descriptions-item' }, [
        h('span', { class: 'stub-label' }, props.label),
        h('span', { class: 'stub-value' }, slots.default?.()),
      ])
  },
})

const stubs = {
  'a-breadcrumb': { template: '<div class="stub-breadcrumb"><slot /></div>' },
  'a-breadcrumb-item': { template: '<span class="stub-breadcrumb-item"><slot /></span>' },
  'a-typography-title': ATypographyTitle,
  'a-table': ATable,
  'a-collapse': ACollapse,
  'a-collapse-panel': ACollapsePanel,
  'a-descriptions': ADescriptions,
  'a-descriptions-item': ADescriptionsItem,
}

// ── Import after stubs defined ──
import HelpPage from '@/pages/help/index.vue'

describe('HelpPage', () => {
  const mountHelp = () => mount(HelpPage, { global: { stubs } })

  /* ── Test 1: renders all 3 section titles ── */
  it('renders three sections: 快捷键, 常见问题, 联系我们', () => {
    const wrapper = mountHelp()

    const titles = wrapper.findAll('.lg-section-title')
    expect(titles.length).toBe(3)
    expect(titles[0].text()).toBe('快捷键')
    expect(titles[1].text()).toBe('常见问题')
    expect(titles[2].text()).toBe('联系我们')
  })

  /* ── Test 2: FAQ section has 6 collapse panels ── */
  it('FAQ section contains 6 a-collapse-panel items', () => {
    const wrapper = mountHelp()

    const panels = wrapper.findAll('.stub-collapse-panel')
    expect(panels.length).toBe(6)

    // Verify first and last FAQ headers
    expect(panels[0].find('.stub-panel-header').text()).toBe('如何新建合同？')
    expect(panels[5].find('.stub-panel-header').text()).toBe('页面加载慢怎么办？')

    // Verify an answer is present
    expect(panels[0].find('.stub-panel-body').text()).toBe(
      '导航到"合同管理"→"新建合同"，按向导填写基本信息、清单和付款条件后提交。',
    )
  })

  /* ── Test 3: contact section has email and phone ── */
  it('contact section displays email and phone', () => {
    const wrapper = mountHelp()

    const labels = wrapper.findAll('.stub-label')
    const values = wrapper.findAll('.stub-value')

    const labelTexts = labels.map((l) => l.text())
    const valueTexts = values.map((v) => v.text())

    expect(labelTexts).toContain('技术支持邮箱')
    expect(labelTexts).toContain('技术支持电话')
    expect(labelTexts).toContain('工作时间')
    expect(labelTexts).toContain('系统版本')

    expect(valueTexts).toContain('support@cgc-pms.com')
    expect(valueTexts).toContain('400-XXX-XXXX')
    expect(valueTexts).toContain('周一至周五 9:00 - 18:00')
    expect(valueTexts).toContain('v1.0.0')
  })

  /* ── Test 4: shortcut table has 5 rows ── */
  it('shortcut table contains 5 rows of keyboard shortcuts', () => {
    const wrapper = mountHelp()

    const rows = wrapper.findAll('.stub-table tr')
    expect(rows.length).toBe(5)

    // Check first and last shortcuts
    const firstRowCells = rows[0].findAll('td')
    expect(firstRowCells.some((td) => td.text() === 'Ctrl+S / Cmd+S')).toBe(true)
    expect(firstRowCells.some((td) => td.text() === '保存当前表单')).toBe(true)

    const lastRowCells = rows[4].findAll('td')
    expect(lastRowCells.some((td) => td.text() === 'Tab')).toBe(true)
    expect(lastRowCells.some((td) => td.text() === '切换输入框')).toBe(true)
  })

  /* ── Test 5: page has help center breadcrumb ── */
  it('renders the help center breadcrumb', () => {
    const wrapper = mountHelp()

    const breadcrumb = wrapper.find('.stub-breadcrumb')
    expect(breadcrumb.exists()).toBe(true)
    expect(breadcrumb.text()).toContain('首页')
    expect(breadcrumb.text()).toContain('帮助中心')
  })
})
