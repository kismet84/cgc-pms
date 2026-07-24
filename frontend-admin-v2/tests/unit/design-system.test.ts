import { readFileSync, readdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  V2Alert,
  V2ActionMenu,
  V2Badge,
  V2Button,
  V2Card,
  V2Cluster,
  V2ConfirmDialog,
  V2Dialog,
  V2GlassButton,
  V2Grid,
  V2Input,
  V2PageState,
  V2Select,
  V2Skeleton,
  V2Stack,
} from '@/components'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourceRoot = resolve(currentDir, '../../src')
const repositoryRoot = resolve(currentDir, '../../..')
const uiStandardPath = resolve(repositoryRoot, 'docs/ui-v2/m1-design-system-baseline.md')

function migratedPageSources() {
  const pagesRoot = resolve(sourceRoot, 'pages')
  return readdirSync(pagesRoot, { recursive: true })
    .map(String)
    .filter((name) => name.endsWith('.vue'))
    .map((name) => ({ name, source: readFileSync(resolve(pagesRoot, name), 'utf-8') }))
}

const migratedPages = migratedPageSources()
const migratedSurfaces = [
  ...migratedPages,
  ...readdirSync(resolve(sourceRoot, 'layouts'))
    .filter((name) => name.endsWith('.vue'))
    .map((name) => ({
      name: `layouts/${name}`,
      source: readFileSync(resolve(sourceRoot, 'layouts', name), 'utf-8'),
    })),
]
const dialogPattern = /(<V2Dialog\b(?:(?:"[^"]*"|'[^']*')|[^'">])*>)([\s\S]*?)<\/V2Dialog\s*>/g
const paginationPattern = /(<nav\b[^>]*aria-label=(['"])[^'"]*分页\2[^>]*>)([\s\S]*?)<\/nav\s*>/g

function dialogBlocks(source: string) {
  return [...source.matchAll(dialogPattern)].map(([, openingTag, body]) => ({
    openingTag,
    body,
  }))
}

function paginationBlocks(source: string) {
  return [...source.matchAll(paginationPattern)].map(([, openingTag, , body]) => ({
    openingTag,
    body,
  }))
}

function listTablesOutsideCards(source: string) {
  const templateStart = source.indexOf('<template')
  const templateEnd = source.lastIndexOf('</template>')
  if (templateStart < 0 || templateEnd < 0) return []

  const stack: Array<{ tag: string; classes: string }> = []
  const violations: number[] = []
  const template = source.slice(templateStart, templateEnd)
  const tags = /<(?:[^>"']|"[^"]*"|'[^']*')+>/g

  for (const match of template.matchAll(tags)) {
    const markup = match[0]
    if (markup.startsWith('<!--')) continue
    const closing = /^<\s*\//.test(markup)
    const tag = /^<\s*\/?\s*([A-Za-z][\w-]*)/.exec(markup)?.[1]
    if (!tag) continue

    if (closing) {
      for (let index = stack.length - 1; index >= 0; index -= 1) {
        if (stack[index]?.tag === tag) {
          stack.length = index
          break
        }
      }
      continue
    }

    if (tag === 'table') {
      const inListRegion = stack.some(({ classes }) =>
        /(?:^|\s)(?:table-wrap|[\w-]+__table-wrap)(?:\s|$)/.test(classes),
      )
      const hasCard = stack.some(({ tag: ancestor }) => ancestor === 'V2Card')
      const inDialog = stack.some(({ tag: ancestor }) => ancestor === 'V2Dialog')
      if (inListRegion && !hasCard && !inDialog) {
        violations.push(source.slice(0, templateStart + match.index).split('\n').length)
      }
    }

    if (!/\/\s*>$/.test(markup)) {
      stack.push({ tag, classes: /\sclass=(['"])(.*?)\1/.exec(markup)?.[2] ?? '' })
    }
  }
  return violations
}

function styleRules(source: string) {
  const styles = [...source.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/g)]
    .map(([, css]) => css)
    .join('\n')
  return [...styles.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map(([, selector, declarations]) => ({
    selector,
    declarations,
  }))
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('Clean-room V2 design system', () => {
  it('exposes button variants with loading, disabled and keyboard focus semantics', async () => {
    const wrapper = mount(V2Button, {
      props: { variant: 'secondary', loading: true },
      slots: { default: '保存' },
    })

    const button = wrapper.get('button')
    expect(button.classes()).toContain('v2-button--secondary')
    expect(button.attributes('aria-busy')).toBe('true')
    expect(button.attributes()).toHaveProperty('disabled')
    expect(button.text()).toContain('保存')
    expect(wrapper.find('.v2-spinner').exists()).toBe(true)

    await wrapper.setProps({ loading: false, disabled: true })
    expect(button.attributes()).toHaveProperty('disabled')
  })

  it('forwards native form association from a footer button', async () => {
    const form = document.createElement('form')
    form.id = 'external-dialog-form'
    const submitted = vi.fn((event: SubmitEvent) => event.preventDefault())
    form.addEventListener('submit', submitted)
    document.body.appendChild(form)
    const wrapper = mount(V2Button, {
      attachTo: document.body,
      props: { type: 'submit' },
      attrs: { form: form.id },
      slots: { default: '确认提交' },
    })

    expect(wrapper.get('button').attributes('form')).toBe(form.id)
    await wrapper.get('button').trigger('click')
    expect(submitted).toHaveBeenCalledOnce()

    wrapper.unmount()
    form.remove()
  })

  it('exposes a reusable glass button with text, state and click props', async () => {
    let clicks = 0
    const wrapper = mount(V2GlassButton, {
      props: {
        text: '确认处置',
        className: 'dialog-action',
        onClick: () => {
          clicks += 1
        },
      },
    })

    const button = wrapper.get('button')
    expect(button.classes()).toContain('v2-glass-button')
    expect(button.classes()).toContain('dialog-action')
    expect(button.text()).toContain('确认处置')
    await button.trigger('click')
    expect(clicks).toBe(1)

    await wrapper.setProps({ loading: true })
    expect(button.attributes('aria-busy')).toBe('true')
    expect(button.attributes()).toHaveProperty('disabled')

    const source = readFileSync(resolve(sourceRoot, 'components/V2GlassButton.vue'), 'utf-8')
    expect(source).toMatch(
      /@media \(max-width: 48rem\)[\s\S]*min-height: var\(--v2-control-height-touch\)/,
    )
  })

  it('keeps overflow actions in a shared menu with Escape and outside-click closing', async () => {
    const wrapper = mount(V2ActionMenu, {
      attachTo: document.body,
      props: { label: '演示项目更多操作' },
      slots: { default: '<button type="button">编辑</button>' },
    })
    const details = wrapper.get('details')
    const summary = wrapper.get('summary')

    ;(details.element as HTMLDetailsElement).open = true
    await details.trigger('keydown', { key: 'Escape' })
    expect((details.element as HTMLDetailsElement).open).toBe(false)
    expect(document.activeElement).toBe(summary.element)

    ;(details.element as HTMLDetailsElement).open = true
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await flushPromises()
    expect((details.element as HTMLDetailsElement).open).toBe(false)
    wrapper.unmount()
  })

  it('associates input hints and errors and emits model updates', async () => {
    const wrapper = mount(V2Input, {
      props: { label: '项目名称', hint: '输入项目名称', modelValue: '' },
    })
    const input = wrapper.get('input')
    const hint = wrapper.get('.v2-field__hint')

    expect(input.attributes('aria-describedby')).toBe(hint.attributes('id'))
    await input.setValue('金融中心项目')
    expect(wrapper.emitted('update:modelValue')).toEqual([['金融中心项目']])

    await wrapper.setProps({ error: '项目名称不能为空' })
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(input.attributes('aria-describedby')).toBe(
      wrapper.get('.v2-field__error').attributes('id'),
    )

    await wrapper.setProps({ hideLabel: true })
    expect(wrapper.get('.v2-field__label').classes()).toContain('v2-visually-hidden')
  })

  it('renders an anchored select with placeholder and disabled options', async () => {
    const wrapper = mount(V2Select, {
      props: {
        label: '报告期',
        modelValue: '',
        options: [
          { value: '2026-07', label: '2026年7月' },
          { value: '2026-06', label: '2026年6月', disabled: true },
        ],
      },
    })

    expect(wrapper.findAll('[role="option"]')).toHaveLength(3)
    expect(wrapper.findAll('[role="option"]')[2]?.attributes()).toHaveProperty('disabled')
    await wrapper.findAll('[role="option"]')[1]?.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['2026-07']])
  })

  it('can visually hide a select label while preserving its accessible name', () => {
    const wrapper = mount(V2Select, {
      props: {
        label: '预警级别',
        hideLabel: true,
        modelValue: 'all',
        options: [{ value: 'all', label: '全部预警' }],
      },
    })

    expect(wrapper.get('.v2-field__label').classes()).toContain('v2-visually-hidden')
    expect(wrapper.get('[role="button"]').attributes('aria-label')).toBe('预警级别：全部预警')
  })

  it('supports an explicit or inferred empty option without duplicating the placeholder', async () => {
    const wrapper = mount(V2Select, {
      props: {
        label: '当前项目',
        modelValue: '',
        allowEmpty: true,
        options: [
          { value: '', label: '全部项目' },
          { value: '1', label: '项目一' },
        ],
      },
    })

    expect(wrapper.findAll('[role="option"]')).toHaveLength(2)
    expect(wrapper.get('[role="button"]').text()).toBe('全部项目')

    const inferred = mount(V2Select, {
      props: {
        label: '项目类型',
        modelValue: 'BUILDING',
        allowEmpty: true,
        placeholder: '全部类型',
        options: [{ value: 'BUILDING', label: '施工总承包' }],
      },
    })
    expect(inferred.findAll('[role="option"]')).toHaveLength(2)
    await inferred.get('[role="option"][data-value=""]').trigger('click')
    expect(inferred.emitted('update:modelValue')).toEqual([['']])
  })

  it('supports arrow, home, end and escape keyboard navigation in selects', async () => {
    const wrapper = mount(V2Select, {
      attachTo: document.body,
      props: {
        label: '报告期',
        modelValue: '',
        options: [
          { value: '2026-07', label: '2026年7月' },
          { value: '2026-06', label: '2026年6月' },
        ],
      },
    })
    const trigger = wrapper.get('summary')
    const options = wrapper.findAll<HTMLButtonElement>('[role="option"]:not(:disabled)')

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await flushPromises()
    expect(document.activeElement).toBe(options[0]?.element)

    await options[0]?.trigger('keydown', { key: 'End' })
    expect(document.activeElement).toBe(options[1]?.element)
    await options[1]?.trigger('keydown', { key: 'Home' })
    expect(document.activeElement).toBe(options[0]?.element)
    await options[0]?.trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(options[1]?.element)

    await options[1]?.trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(wrapper.get('details').attributes()).not.toHaveProperty('open')
    expect(document.activeElement).toBe(trigger.element)
  })

  it('covers card, badge, alert and skeleton status primitives', async () => {
    const card = mount(V2Card, {
      props: { title: '经营健康度', subtitle: '当前报告期', interactive: true },
      slots: { default: '面板内容', footer: '底部动作', 'title-extra': '4 项' },
    })
    expect(card.get('.v2-card__title').text()).toBe('经营健康度')
    expect(card.get('.v2-card__title-row').text()).toBe('经营健康度4 项')
    expect(card.get('h2').exists()).toBe(true)
    expect(card.classes()).toContain('v2-card--interactive')
    expect(card.get('.v2-card__body').text()).toBe('面板内容')

    const pageCard = mount(V2Card, {
      props: { title: '现场日报', headingLevel: 1, titleId: 'daily-log-title' },
    })
    expect(pageCard.get('h1').attributes('id')).toBe('daily-log-title')
    expect(pageCard.get('h1').classes()).toContain('v2-card__title--page')
    expect(pageCard.find('.v2-card__body').exists()).toBe(false)

    const nestedCard = mount(V2Card, { props: { title: '附件', headingLevel: 3 } })
    expect(nestedCard.get('h3').text()).toBe('附件')

    const badge = mount(V2Badge, {
      props: { tone: 'danger', dot: true },
      slots: { default: '高风险' },
    })
    expect(badge.classes()).toContain('v2-badge--danger')
    expect(badge.find('.v2-badge__dot').exists()).toBe(true)

    const alert = mount(V2Alert, {
      props: { title: '校验失败', tone: 'danger', dismissible: true },
      slots: { default: '请检查输入内容' },
    })
    expect(alert.attributes('role')).toBe('alert')
    expect(alert.find('h1, h2, h3').exists()).toBe(false)
    await alert.get('button').trigger('click')
    expect(alert.emitted('dismiss')).toHaveLength(1)

    const skeleton = mount(V2Skeleton, { props: { variant: 'circle' } })
    expect(skeleton.attributes('aria-busy')).toBe('true')
    expect(skeleton.classes()).toContain('v2-skeleton--circle')

    const state = mount(V2PageState, {
      props: {
        title: '正在加载',
        description: '请稍候。',
        headingLevel: 2,
        titleId: 'stable-state-title',
      },
    })
    expect(state.get('h2').attributes('id')).toBe('stable-state-title')
    expect(state.attributes('aria-labelledby')).toBe('stable-state-title')
  })

  it('closes dialog with Escape and restores focus', async () => {
    const trigger = document.createElement('button')
    trigger.textContent = '打开'
    document.body.appendChild(trigger)
    trigger.focus()

    const wrapper = mount(V2Dialog, {
      attachTo: document.body,
      props: { open: false, title: '确认操作', description: '不执行业务写入' },
      slots: { default: '<button data-testid="inside">对话框操作</button>' },
    })
    await wrapper.setProps({ open: true })
    await flushPromises()

    const dialog = document.querySelector<HTMLElement>('[role="dialog"]')
    expect(dialog).not.toBeNull()
    expect(dialog?.classList).toContain('v2-dialog-standard')
    expect(document.activeElement).toBe(dialog)
    dialog?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:open')).toEqual([[false]])
    await wrapper.setProps({ open: false })
    await flushPromises()
    expect(document.activeElement).toBe(trigger)
    wrapper.unmount()
  })

  it('keeps a dialog open and emits a signal when backdrop closing is disabled', async () => {
    const wrapper = mount(V2Dialog, {
      attachTo: document.body,
      props: { open: true, title: '编辑日报', closeOnBackdrop: false },
    })
    await flushPromises()

    document
      .querySelector<HTMLElement>('.v2-dialog__backdrop')
      ?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('backdrop-click')).toHaveLength(1)
    expect(wrapper.emitted('update:open')).toBeUndefined()
    wrapper.unmount()
  })

  it('renders branded confirmation actions and blocks closing while saving', async () => {
    const wrapper = mount(V2ConfirmDialog, {
      attachTo: document.body,
      props: {
        open: true,
        title: '删除项目',
        description: '此操作无法撤销。',
        confirmText: '永久删除',
        danger: true,
      },
    })
    await flushPromises()

    const buttons = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.v2-dialog__footer .v2-button'),
    )
    expect(document.querySelector('.v2-confirm-dialog')?.classList).toContain('v2-dialog-standard')
    expect(buttons[1]?.classList).toContain('v2-button--danger')
    buttons[0]?.click()
    await flushPromises()
    expect(wrapper.emitted('close')).toHaveLength(1)
    buttons[1]?.click()
    await flushPromises()
    expect(wrapper.emitted('confirm')).toHaveLength(1)

    await wrapper.setProps({ loading: true })
    document.querySelector<HTMLButtonElement>('.v2-dialog__close')?.click()
    await flushPromises()
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(document.querySelector<HTMLButtonElement>('.v2-button--danger')?.disabled).toBe(true)
    wrapper.unmount()
  })

  it('closes only the focused top dialog with Escape', async () => {
    const wrapper = mount(
      {
        components: { V2Dialog },
        data: () => ({ outerOpen: true, innerOpen: true }),
        template: `
          <V2Dialog v-model:open="outerOpen" title="日报详情">
            <button>详情操作</button>
          </V2Dialog>
          <V2Dialog v-model:open="innerOpen" title="确认提交">
            <button>确认操作</button>
          </V2Dialog>
        `,
      },
      { attachTo: document.body },
    )
    await flushPromises()

    expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(2)
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    )
    await flushPromises()

    expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(1)
    expect(document.querySelector('[role="dialog"]')?.textContent).toContain('日报详情')
    wrapper.unmount()
  })

  it('closes the top dialog with Escape when focus temporarily leaves it', async () => {
    const wrapper = mount(V2Dialog, {
      attachTo: document.body,
      props: { open: true, title: '审批详情' },
    })
    await flushPromises()

    document.body.tabIndex = -1
    document.body.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:open')).toEqual([[false]])
    wrapper.unmount()
    document.body.removeAttribute('tabindex')
  })

  it('keeps stack, cluster and grid spacing on the shared token scale', () => {
    const stack = mount(V2Stack, { props: { gap: 4 } })
    const cluster = mount(V2Cluster, { props: { gap: 2, justify: 'between' } })
    const grid = mount(V2Grid, { props: { gap: 3, minItemWidth: '18rem' } })

    expect(stack.attributes('style')).toContain('--v2-stack-gap: var(--v2-space-4)')
    expect(cluster.attributes('style')).toContain('--v2-cluster-justify: space-between')
    expect(grid.attributes('style')).toContain('--v2-grid-min: 18rem')
  })

  it('centralizes core visual values in tokens and keeps components clean-room', () => {
    const tokens = readFileSync(resolve(sourceRoot, 'styles/tokens.css'), 'utf-8')
    const componentCss = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')
    const componentFiles = readdirSync(resolve(sourceRoot, 'components'))
      .filter((name) => name.endsWith('.vue'))
      .map((name) => readFileSync(resolve(sourceRoot, 'components', name), 'utf-8'))
      .join('\n')

    for (const token of [
      '--v2-color-primary',
      '--v2-color-danger',
      '--v2-color-workspace-tab-accent',
      '--v2-chart-1',
      '--v2-font-sans',
      '--v2-space-4',
      '--v2-radius-md',
      '--v2-shadow-panel',
      '--v2-z-dialog',
      '--v2-motion-base',
    ]) {
      expect(tokens).toContain(token)
    }

    expect(componentCss).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    expect(componentFiles).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    expect(componentCss).toContain('.v2-card__body:empty')
    expect(componentCss).toContain('.v2-card__header + .v2-card__body:not(:empty)')
    expect(componentCss).toContain('#shell-main-content table')
  })

  describe('V2 migration gates', () => {
    it('keeps the authoritative UI checklist complete, ordered and wired into CI', () => {
      const standard = readFileSync(uiStandardPath, 'utf-8')
      const checklistIds = [...standard.matchAll(/^\| (S\d{2}) \|/gm)].map(([, id]) => id)
      const expectedIds = Array.from(
        { length: 24 },
        (_, index) => `S${String(index + 1).padStart(2, '0')}`,
      )
      const packageJson = readFileSync(resolve(sourceRoot, '../package.json'), 'utf-8')
      const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/ci.yml'), 'utf-8')

      expect(checklistIds).toEqual(expectedIds)
      expect(standard).toContain('P0 失败立即阻断迁移或合并')
      expect(standard).toContain('不设置泛化 P2')
      for (const component of [
        'V2Input',
        'V2Select',
        'V2Button',
        'V2GlassButton',
        'V2Dialog',
        'V2ConfirmDialog',
        'V2Badge',
        'V2Alert',
      ]) {
        expect(standard).toContain(`\`${component}\``)
      }
      for (const command of ['unit', 'lint', 'type-check', 'build', '迁移 E2E', 'diff-check']) {
        expect(standard).toContain(command)
      }
      expect(packageJson).toContain('"test:e2e:migration-gate"')
      expect(workflow).toContain('pnpm test:e2e:migration-gate')
    })

    it('lists every exported public V2 component in the current standard', () => {
      const standard = readFileSync(uiStandardPath, 'utf-8')
      const componentIndex = readFileSync(resolve(sourceRoot, 'components/index.ts'), 'utf-8')
      const exportedComponents = [...componentIndex.matchAll(/default as (V2[A-Za-z]+)/g)].map(
        ([, name]) => name,
      )

      expect(exportedComponents.length).toBeGreaterThan(0)
      for (const name of exportedComponents) expect(standard).toContain(`\`${name}\``)
      expect(standard).toContain('`showToast`')
      expect(standard).toContain('`useToastMessage`')
    })

    it('keeps project and report-period context selectors owned by the public shell', () => {
      const appShell = readFileSync(resolve(sourceRoot, 'layouts/AppShell.vue'), 'utf-8')
      const navigationCatalog = readFileSync(resolve(sourceRoot, 'navigation/catalog.ts'), 'utf-8')
      const router = readFileSync(resolve(sourceRoot, 'router.ts'), 'utf-8')
      expect(appShell).toContain('id="global-project"')
      expect(appShell).toContain('id="global-report-period"')
      expect(appShell).toContain(':disabled="!workspaceStore.projects.length"')
      expect(appShell).toContain(':disabled="!workspaceStore.reportPeriods.length"')
      expect(appShell).not.toContain('projectFilterUnsupported')
      expect(appShell).not.toContain('periodFilterUnsupported')
      expect(navigationCatalog).not.toContain('workspaceContext')
      expect(router).not.toContain('workspaceContext')
      expect(appShell).not.toContain('const dashboardRole')

      const copiedContextControl =
        /<V2(?:Input|Select)\b(?:(?:"[^"]*"|'[^']*')|[^'">])*\blabel=(['"])(?:当前项目|报告期)\1/
      for (const { name, source } of migratedPages) {
        expect(source, `${name} copied public-shell selector`).not.toMatch(copiedContextControl)
        expect(source, `${name} copied public-shell id`).not.toMatch(
          /\bid=['"]global-(?:project|report-period)['"]/,
        )
      }
    })

    it('wraps every top-level list table in one V2Card', () => {
      const standard = readFileSync(uiStandardPath, 'utf-8')
      expect(standard).toContain('顶层列表表格与分页必须置于同一个 `V2Card`')
      for (const { name, source } of migratedPages) {
        expect(listTablesOutsideCards(source), `${name} has an uncarded list table`).toEqual([])
      }
    })

    it('keeps migrated pages on declared typography and color tokens', () => {
      const sourceFiles = readdirSync(sourceRoot, { recursive: true })
        .map(String)
        .filter((name) => /\.(?:css|ts|vue)$/.test(name))
      const allSources = sourceFiles
        .map((name) => readFileSync(resolve(sourceRoot, name), 'utf-8'))
        .join('\n')
      const implementationSources = sourceFiles
        .filter((name) => name !== 'styles\\tokens.css' && name !== 'styles/tokens.css')
        .map((name) => readFileSync(resolve(sourceRoot, name), 'utf-8'))
        .join('\n')
      const pageAndLayoutSources = sourceFiles
        .filter((name) => /^(?:pages|layouts)[\\/]/.test(name))
        .map((name) => readFileSync(resolve(sourceRoot, name), 'utf-8'))
        .join('\n')
      const componentCss = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')
      const dashboard = readFileSync(
        resolve(sourceRoot, 'pages/dashboard/DashboardPage.vue'),
        'utf-8',
      )
      const declaredTokens = new Set(
        readFileSync(resolve(sourceRoot, 'styles/tokens.css'), 'utf-8').match(
          /--v2-[\w-]+(?=\s*:)/g,
        ) ?? [],
      )
      const runtimeLayoutTokens = new Set([
        '--v2-cluster-align',
        '--v2-cluster-gap',
        '--v2-cluster-justify',
        '--v2-grid-align',
        '--v2-grid-min',
        '--v2-stack-align',
        '--v2-stack-gap',
        '--v2-stack-justify',
      ])
      const referencedTokens = [
        ...[...allSources.matchAll(/var\((--v2-[\w-]+)/g)].map(([, token]) => token),
        ...[...allSources.matchAll(/['"](--v2-[\w-]+)['"]/g)].map(([, token]) => token),
      ]
      const unknownTokens = [
        ...new Set(
          referencedTokens.filter(
            (token) =>
              !token.endsWith('-') && !runtimeLayoutTokens.has(token) && !declaredTokens.has(token),
          ),
        ),
      ]

      expect(unknownTokens).toEqual([])
      expect(implementationSources).not.toMatch(/#[0-9a-f]{3,8}\b|rgba?\(|hsla?\(/i)
      expect(implementationSources).not.toMatch(/font-size:\s*[^;{}]*\b[0-9.]+(?:px|rem)\b/i)
      expect(implementationSources).not.toMatch(/font-weight:\s*[0-9]+\b/i)
      expect(implementationSources).not.toMatch(/line-height:\s*[0-9.]+(?:px|rem)?\b/i)
      expect(implementationSources).not.toMatch(/font:\s*[^;{}]*\/\s*[0-9.]+(?:px|rem)?\b/i)
      expect(migratedPages.map(({ source }) => source).join('\n')).not.toMatch(
        /(?:gap|padding(?:-[\w-]+)?|margin(?:-[\w-]+)?|min-height|height|border-radius)\s*:[^;{}]*\b(?:[1-9][0-9]*|0?\.[0-9]+)(?:px|rem)\b/i,
      )
      expect(componentCss).toMatch(
        /\.v2-card__title \{[\s\S]*?line-height: var\(--v2-line-height-tight\);/,
      )
      expect(componentCss).toMatch(
        /\.v2-dialog__title \{[\s\S]*?line-height: var\(--v2-line-height-tight\);/,
      )
      expect(dashboard).toMatch(
        /\.highest-risk h2 \{[\s\S]*?line-height: var\(--v2-line-height-tight\);/,
      )
      expect(dashboard).toMatch(
        /\.highest-risk p \{[\s\S]*?line-height: var\(--v2-line-height-body\);/,
      )
      expect(pageAndLayoutSources).not.toMatch(/\.(?:sr-only|v2-visually-hidden)\s*\{/)
    })

    it('keeps workspace table material centralized and prevents page-level overrides', () => {
      const componentCss = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')
      const pageTableMaterial =
        /(?:^|;)\s*(?:width|border-collapse|font-size|line-height|padding|border(?:-bottom|-block-end)?|text-align|vertical-align|color|background|font-weight|white-space)\s*:/i
      expect(componentCss).toMatch(
        /#shell-main-content table \{[\s\S]*?border-collapse: collapse;[\s\S]*?font-size: var\(--v2-font-size-12\);[\s\S]*?line-height: var\(--v2-line-height-ui\);/,
      )
      expect(componentCss).toMatch(
        /#shell-main-content table :where\(th, td\) \{[\s\S]*?padding: var\(--v2-space-3\);[\s\S]*?border-bottom: var\(--v2-border-width\) solid var\(--v2-color-border-subtle\);[\s\S]*?white-space: nowrap;/,
      )

      for (const { name, source } of migratedPages) {
        for (const rule of styleRules(source)) {
          const targetsGenericTableMaterial = rule.selector.split(',').some((part) => {
            const selector = part.trim()
            if (/[:>+~]/.test(selector)) return false
            return /^(?:(?:table|[.#][\w-]*table)(?:\s+(?:th|td))?|th|td)$/.test(selector)
          })
          if (!targetsGenericTableMaterial) continue
          expect(rule.declarations, `${name}: ${rule.selector.trim()}`).not.toContain('!important')
          expect(rule.declarations, `${name}: ${rule.selector.trim()}`).not.toMatch(
            pageTableMaterial,
          )
        }
      }
    })

    it('keeps the closeout overview table keyboard-scrollable and named', () => {
      const source = readFileSync(
        resolve(sourceRoot, 'pages/delivery/ProjectCloseoutPage.vue'),
        'utf-8',
      )
      expect(source).toMatch(
        /class="closeout-page__table-wrap"[\s\S]*?role="region"[\s\S]*?aria-label="全部项目收尾概览表格"[\s\S]*?tabindex="0"/,
      )
      expect(source).toContain('<caption class="v2-visually-hidden">')
    })

    it('keeps migrated page title ids stable and page states below the page heading', () => {
      const sources = Object.fromEntries(
        [
          'pages/dashboard/DashboardPage.vue',
          'pages/delivery/DailyLogPage.vue',
          'pages/delivery/SchedulePage.vue',
          'pages/projects/ProjectPage.vue',
          'pages/workbench/ReportCatalogPage.vue',
          'pages/workbench/WorkflowWorkbenchPage.vue',
        ].map((name) => [name, readFileSync(resolve(sourceRoot, name), 'utf-8')]),
      )

      const cardTitles = {
        'pages/delivery/DailyLogPage.vue': 'daily-log-title',
        'pages/delivery/SchedulePage.vue': 'schedule-title',
        'pages/projects/ProjectPage.vue': 'project-title',
        'pages/workbench/ReportCatalogPage.vue': 'report-catalog-title',
      }
      for (const [name, titleId] of Object.entries(cardTitles)) {
        const source = sources[name]
        expect(source).toContain(`aria-labelledby="${titleId}"`)
        const titleCards = [...source.matchAll(/<V2Card\b[^>]*>/g)].map(([tag]) => tag)
        expect(
          titleCards.some(
            (tag) => tag.includes(`title-id="${titleId}"`) && tag.includes(':heading-level="1"'),
          ),
        ).toBe(true)
      }

      const nativeTitles = {
        'pages/dashboard/DashboardPage.vue': 'dashboard-title',
        'pages/workbench/WorkflowWorkbenchPage.vue': 'workflow-title',
      }
      for (const [name, titleId] of Object.entries(nativeTitles)) {
        const source = sources[name]
        expect(source).toContain(`aria-labelledby="${titleId}"`)
        expect(source).toMatch(new RegExp(`<h1[^>]*id="${titleId}"`))
      }

      for (const source of Object.values(sources)) {
        const stateTags = [...source.matchAll(/<V2PageState\b[^>]*>/g)].map(([tag]) => tag)
        for (const tag of stateTags) expect(tag).toMatch(/:heading-level="[123]"/)
      }
    })

    it('keeps commercial list headings unified', () => {
      const headings = {
        'pages/commercial/ContractPage.vue': '合同台账',
        'pages/commercial/VariationPage.vue': '签证变更',
        'pages/commercial/BidCostPage.vue': '投标成本',
        'pages/commercial/CostTargetPage.vue': '目标成本版本',
        'pages/commercial/CostLedgerPage.vue': '成本台账',
        'pages/commercial/CostSummaryPage.vue': '成本核对',
        'pages/commercial/CostControlPage.vue': '动态利润控制',
        'pages/commercial/BudgetPage.vue': '项目预算',
        'pages/commercial/ProductionMeasurementPage.vue': '产值计量与业主结算',
      }

      for (const [name, title] of Object.entries(headings)) {
        const source = readFileSync(resolve(sourceRoot, name), 'utf-8')
        const titleCards = [...source.matchAll(/<V2Card\b[^>]*>/g)].map(([tag]) => tag)
        expect(
          titleCards.some(
            (tag) => tag.includes(`title="${title}"`) && tag.includes(':heading-level="1"'),
          ),
          `${name} missing shared H1 title card`,
        ).toBe(true)
      }

      const contract = readFileSync(
        resolve(sourceRoot, 'pages/commercial/ContractPage.vue'),
        'utf-8',
      )
      expect(contract.indexOf('title="合同台账"')).toBeLessThan(
        contract.indexOf('contract-page__kpi-grid'),
      )
    })

    it('keeps every search and filter control label hidden with an in-control prompt', () => {
      const controlPattern = /<V2(?:Input|Select)\b[\s\S]*?\/>/g
      const filterBindingPattern =
        /(?:\bv-model|:model-value)="filter\.|\btype="search"|\bclass="[^"]*filter|\bid="global-(?:project|report-period)"/
      let filterControlCount = 0

      for (const { name, source } of migratedSurfaces) {
        for (const [control] of source.matchAll(controlPattern)) {
          if (!filterBindingPattern.test(control)) continue
          filterControlCount += 1
          expect(control, `${name} filter control accessible label`).toMatch(/\blabel=/)
          expect(control, `${name} visible filter label`).toContain('hide-label')
          expect(control, `${name} missing in-control prompt`).toMatch(/\bplaceholder=/)
        }
      }

      expect(filterControlCount).toBeGreaterThanOrEqual(21)
    })

    it('keeps native browser confirmation dialogs out of V2 pages', () => {
      const pageSources = migratedPages.map(({ source }) => source).join('\n')

      expect(pageSources).not.toContain('window.confirm(')
    })

    it('reserves repeated cards for grouped navigation instead of dense records', () => {
      const repeatedCardPattern =
        /<V2Card\b(?:(?:"[^"]*"|'[^']*')|[^'">])*\bv-for=(['"])[^'"]+\1(?:(?:"[^"]*"|'[^']*')|[^'">])*>/g
      const allowedGroupedNavigation = new Set(['workbench/ReportCatalogPage.vue'])

      for (const { name, source } of migratedPages) {
        for (const [card] of source.matchAll(repeatedCardPattern)) {
          expect(
            allowedGroupedNavigation.has(name.replaceAll('\\', '/')),
            `${name} dense repeated card: ${card}`,
          ).toBe(true)
        }
      }
    })

    it('consolidates related lifecycle ledgers instead of sibling record cards', () => {
      const standard = readFileSync(uiStandardPath, 'utf-8')
      expect(standard).toContain('同一主对象或生命周期的阶段账册合并为一个复合数据区')
      expect(standard).toContain('跨项目子记录不得在概览下失去项目归属后直接铺开')

      const lifecyclePages = {
        'pages/delivery/QualitySafetyPage.vue': ['质量安全闭环台账'],
        'pages/delivery/TechnicalManagementPage.vue': [
          '方案、图纸、会审与 RFI',
          '交底、施工依据与验收归档',
        ],
        'pages/delivery/ProjectCloseoutPage.vue': ['全部项目收尾概览', '收尾主线', '收尾阶段台账'],
      }

      for (const [name, titles] of Object.entries(lifecyclePages)) {
        const source = readFileSync(resolve(sourceRoot, name), 'utf-8')
        const pageBody = source.split('<V2Dialog', 1)[0] ?? ''
        expect(pageBody, `${name} repeated record article`).not.toMatch(
          /<article\b(?:(?:"[^"]*"|'[^']*')|[^'">])*\bv-for=/,
        )
        for (const title of titles) expect(pageBody).toContain(`title="${title}"`)
      }

      const closeout = readFileSync(
        resolve(sourceRoot, 'pages/delivery/ProjectCloseoutPage.vue'),
        'utf-8',
      )
      expect(closeout).toContain('v-if="closeout && projectId"')
      expect(closeout).toContain('v-if="projectId && closeout"')
    })

    it('keeps implementation wording out of every migrated page', () => {
      const pageSources = migratedPages.map(({ source }) => source).join('\n')

      expect(pageSources).not.toMatch(/权威|回读|重读|后端状态|后端阶段/)
    })

    it('keeps text and select fields on shared V2 components across every migrated page', () => {
      const nativeInputPattern = /<input\b(?:(?:"[^"]*"|'[^']*')|[^'">])*>/g
      const allowedNativeTypes = new Set(['checkbox', 'date', 'file', 'hidden', 'number', 'radio'])

      for (const { name, source } of migratedPages) {
        expect(source, `${name} native select`).not.toMatch(/<select\b/)
        for (const [tag] of source.matchAll(nativeInputPattern)) {
          const type = tag.match(/\btype=["']([^"']+)["']/)?.[1]
          expect(allowedNativeTypes.has(type ?? ''), `${name} ${tag}`).toBe(true)
        }
      }
    })

    it('keeps page actions on the shared button component', () => {
      for (const { name, source } of migratedPages) {
        expect(source, `${name} native action button`).not.toMatch(/<button\b/)
      }
    })

    it('enforces the standard dialog contract across every migrated page', () => {
      for (const { name, source } of migratedPages) {
        const dialogs = dialogBlocks(source)
        expect(dialogs, `${name} V2Dialog parser coverage`).toHaveLength(
          [...source.matchAll(/<V2Dialog\b/g)].length,
        )
        for (const [index, { openingTag, body }] of dialogs.entries()) {
          const evidence = `${name} V2Dialog #${index + 1}`
          expect(openingTag, evidence).toMatch(/\b:?close-on-backdrop=/)

          const hasEditableControl = /<(?:form|input|textarea|select)\b|<V2(?:Input|Select)\b/.test(
            body,
          )
          if (hasEditableControl) {
            expect(openingTag, evidence).toMatch(/:close-on-backdrop="false"|@backdrop-click=/)
          }
        }
      }
    })

    it('keeps commercial dialogs business-labelled with reachable form actions', () => {
      const commercialPages = [
        'pages/commercial/ContractPage.vue',
        'pages/commercial/VariationPage.vue',
        'pages/commercial/BidCostPage.vue',
        'pages/commercial/CostTargetPage.vue',
        'pages/commercial/CostLedgerPage.vue',
        'pages/commercial/CostSummaryPage.vue',
        'pages/commercial/CostControlPage.vue',
        'pages/commercial/BudgetPage.vue',
        'pages/commercial/ProductionMeasurementPage.vue',
      ]
      const rawVisibleIdentifier =
        /<dt>\s*ID\s*<\/dt>|label=(['"`])[^'"`]*ID\1|\{\{\s*(?:item|detail|line)\.(?:costSubjectId|measurementLineId|responsibleUserId|costStatus)\s*\}\}/

      for (const name of commercialPages) {
        const source = readFileSync(resolve(sourceRoot, name), 'utf-8')
        for (const [index, { body }] of dialogBlocks(source).entries()) {
          const evidence = `${name} V2Dialog #${index + 1}`
          expect(body, `${evidence} raw visible identifier`).not.toMatch(rawVisibleIdentifier)
          if (/<form\b/.test(body)) {
            expect(body, `${evidence} missing footer actions`).toMatch(/<template\b[^>]*#footer/)
          }
        }
      }

      const componentCss = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')
      expect(componentCss).toMatch(
        /\.v2-dialog__footer \{[\s\S]*?position: sticky;[\s\S]*?bottom: 0;/,
      )
    })

    it('enforces detail dialog and shared data typography across every migrated page', () => {
      const componentCss = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')

      expect(componentCss).toMatch(
        /\.v2-dialog__body \{[\s\S]*?font-size: var\(--v2-font-size-12\);[\s\S]*?line-height: var\(--v2-line-height-body\);[\s\S]*?overflow-wrap: anywhere;/,
      )
      expect(componentCss).toMatch(
        /#shell-main-content \[class\*='__record-sections'\] > section \{[\s\S]*?min-width: 0;/,
      )
      expect(componentCss).toMatch(
        /\.v2-card__body :where\(dl, table\),[\s\S]*?\.v2-dialog__body :where\(dl, table\) \{[\s\S]*?font-size: var\(--v2-font-size-12\);[\s\S]*?line-height: var\(--v2-line-height-ui\);/,
      )

      for (const { name, source } of migratedPages) {
        for (const [index, { openingTag, body }] of dialogBlocks(source).entries()) {
          const evidence = `${name} V2Dialog #${index + 1}`
          const title = openingTag.match(/\b:?title=(['"])(.*?)\1/)?.[2] ?? ''
          if (/(?:详情|追溯|预览)/.test(title)) {
            expect(openingTag, `${evidence} ${title}`).toContain('v2-detail-dialog')
          }
          if (!openingTag.includes('v2-detail-dialog')) continue

          expect(body, `${evidence} raw detail dump`).not.toMatch(/<pre\b/)
          for (const [dl] of body.matchAll(/<dl\b[^>]*>/g)) {
            expect(dl, `${evidence} detail facts`).toMatch(
              /\bclass=(['"])[^'"]*\bv2-detail-dialog__facts\b[^'"]*\1/,
            )
          }
          for (const [button] of body.matchAll(/<V2Button\b(?:(?:"[^"]*"|'[^']*')|[^'">])*>/g)) {
            expect(button, `${evidence} detail/edit action material`).toMatch(
              /\btype=['"](?:button|submit)['"]/,
            )
          }
        }
      }
    })

    it('keeps the public shell scrollable and all transient success feedback on toast', () => {
      const standard = readFileSync(uiStandardPath, 'utf-8')
      const appShell = readFileSync(resolve(sourceRoot, 'layouts/AppShell.vue'), 'utf-8')

      expect(standard).toContain('公共壳主内容区必须可独立纵向滚动')
      expect(standard).toContain('页面不得用占据文档流的成功横幅替代 Toast')
      expect(appShell).toMatch(/\.app-shell \{[\s\S]*?height: 100dvh;[\s\S]*?overflow: hidden;/)
      expect(appShell).toMatch(
        /\.app-shell__content \{[\s\S]*?min-height: 0;[\s\S]*?flex: 1;[\s\S]*?overflow-y: auto;/,
      )
      for (const { name, source } of migratedPages) {
        if (name.replaceAll('\\', '/') === 'auth/SessionPage.vue') continue
        expect(source, `${name} renders transient success as an alert`).not.toMatch(
          /<V2Alert\b[^>]*tone="success"/,
        )
        if (source.includes('successMessage')) {
          expect(source, `${name} does not bridge success state to the shared toast`).toContain(
            'useToastMessage',
          )
        }
      }
      const dashboard = readFileSync(
        resolve(sourceRoot, 'pages/dashboard/DashboardPage.vue'),
        'utf-8',
      )
      expect(dashboard).toContain("useToastMessage('info', '操作结果')")
      expect(dashboard).not.toContain('risk-evaluate-feedback')
    })

    it('keeps long table and trace facts readable and business-labelled', () => {
      const dailyLog = readFileSync(resolve(sourceRoot, 'pages/delivery/DailyLogPage.vue'), 'utf-8')
      const technical = readFileSync(
        resolve(sourceRoot, 'pages/delivery/TechnicalManagementPage.vue'),
        'utf-8',
      )
      const quality = readFileSync(
        resolve(sourceRoot, 'pages/delivery/QualitySafetyPage.vue'),
        'utf-8',
      )
      const closeout = readFileSync(
        resolve(sourceRoot, 'pages/delivery/ProjectCloseoutPage.vue'),
        'utf-8',
      )
      const componentCss = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')

      expect(dailyLog).toMatch(/\.daily-log-page__list-table \{[\s\S]*?table-layout: fixed;/)
      expect(dailyLog).toMatch(
        /class="daily-log-page__summary daily-log-page__summary-cell v2-table-cell--wrap"/,
      )
      expect(componentCss).toMatch(
        /#shell-main-content table \.v2-table-cell--wrap \{[\s\S]*?white-space: normal;[\s\S]*?overflow-wrap: anywhere;[\s\S]*?word-break: break-word;/,
      )
      expect(technical).toContain('<th scope="col">图纸编码</th>')
      expect(technical).toContain('<th scope="col">图纸名称</th>')
      expect(technical).not.toContain('{{ drawing.drawingCode }} · {{ drawing.drawingName }}')
      expect(technical).not.toContain('technical-page__toolbar')
      expect(technical).not.toContain('subtitle="按技术闭环阶段集中核对与处理"')
      expect(technical).toMatch(
        /<V2Card title="方案、图纸、会审与 RFI"[\s\S]*?<template #title-extra>[\s\S]*?technical-page__facts[\s\S]*?<template #actions>[\s\S]*?technical-page__actions/,
      )
      expect(quality).not.toContain(':subtitle="`计划 ${plans.length}')
      expect(quality).toMatch(
        /<V2Card title="质量安全闭环台账">[\s\S]*?<template #title-extra>[\s\S]*?quality-page__facts[\s\S]*?计划 \{\{ plans\.length \}\}[\s\S]*?检查 \{\{ inspections\.length \}\}[\s\S]*?问题 \{\{ issues\.length \}\}/,
      )
      expect(closeout).toContain('deliveryLabel(item.receivableType)')
      expect(closeout).toContain('formatAmount(item.allocatedAmount)')
      for (const [tableWrap] of closeout.matchAll(
        /<div\b(?:(?:"[^"]*"|'[^']*')|[^'">])*\bclass="closeout-page__table-wrap"(?:(?:"[^"]*"|'[^']*')|[^'">])*>/g,
      )) {
        expect(tableWrap).toContain('role="region"')
        expect(tableWrap).toMatch(/aria-label(?:ledby)?="[^"]+"/)
        expect(tableWrap).toContain('tabindex="0"')
      }
    })

    it('keeps business codes in dedicated table columns on every page', () => {
      const violations: string[] = []

      for (const { name, source } of migratedPages) {
        for (const match of source.matchAll(/<(?:th|td)\b[^>]*>([\s\S]*?)<\/(?:th|td)>/g)) {
          const cell = match[1] ?? ''
          if (cell.includes('<table')) continue
          const expressions = [...cell.matchAll(/\{\{([\s\S]*?)\}\}/g)].map(
            ([, expression]) => expression?.trim() ?? '',
          )
          if (
            expressions.length > 1 &&
            expressions.some((expression) => /(?:code|_code)/i.test(expression))
          ) {
            const line = source.slice(0, match.index).split('\n').length
            violations.push(`${name}:${line}`)
          }
        }
      }

      expect(violations, 'business code cells must not combine other fields').toEqual([])
    })

    it('enforces every pagination block independently', () => {
      const componentCss = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')
      expect(componentCss).toMatch(
        /:where\(nav, \[role='navigation'\]\)\[aria-label\$='分页'\] \{[\s\S]*?font-size: var\(--v2-font-size-12\);[\s\S]*?line-height: var\(--v2-line-height-ui\);/,
      )

      for (const { name, source } of migratedPages) {
        const blocks = paginationBlocks(source)
        const landmarks = [...source.matchAll(/<nav\b[^>]*aria-label=(['"])[^'"]*分页\1[^>]*>/g)]
        expect(blocks, `${name} pagination parser coverage`).toHaveLength(landmarks.length)
        if (blocks.length > 0) {
          const firstPageSize = source.match(/\bpageSize\s*(?::|=)[^\d]*(\d+)/)?.[1]
          expect(firstPageSize, `${name} paginated list pageSize`).toBe('10')
        }
        for (const [index, { body }] of blocks.entries()) {
          if (!body.includes('上一页') && !body.includes('下一页')) continue
          const evidence = `${name} pagination #${index + 1}`
          const previous = body.indexOf('上一页')
          const current = body.search(/第\s+\{\{[^}]+\}\}\s+页/)
          const next = body.indexOf('下一页')
          expect(previous, `${evidence} previous page`).toBeGreaterThanOrEqual(0)
          expect(current, `${evidence} current page`).toBeGreaterThan(previous)
          expect(next, `${evidence} next page`).toBeGreaterThan(current)
          expect(body, `${evidence} legacy page-count separator`).not.toMatch(
            /第\s+\{\{[^}]+\}\}\s*\/\s*\{\{/,
          )
        }
      }
    })

    it('locks third-level workspace tab labels and typography', () => {
      const appShell = readFileSync(resolve(sourceRoot, 'layouts/AppShell.vue'), 'utf-8')
      expect(appShell).toMatch(
        /\.app-shell__tab \{[\s\S]*?font-size: var\(--v2-font-size-12\);[\s\S]*?font-weight: var\(--v2-font-weight-semibold\);[\s\S]*?line-height: var\(--v2-line-height-ui\);/,
      )
    })

    it('keeps shared component material owned by the shared styles', () => {
      const sharedMaterialSelector =
        /\.(?:v2-button|v2-action-menu|v2-glass-button|v2-card|v2-badge|v2-dialog__panel|v2-field__control)\b/
      const nativeFieldSelector = /\b(?:input|textarea|select)\b/
      const sharedMaterialProperty =
        /(?:^|;)\s*(?:color|background(?:-color)?|border(?:-color|-radius)?|box-shadow|backdrop-filter|font(?:-family|-size|-weight)?)\s*:/i

      for (const { name, source } of migratedSurfaces) {
        for (const { selector, declarations } of styleRules(source)) {
          if (!sharedMaterialSelector.test(selector) && !nativeFieldSelector.test(selector))
            continue
          expect(
            declarations,
            `${name} overrides shared material in ${selector.trim()}`,
          ).not.toMatch(sharedMaterialProperty)
        }
      }
    })

    it('keeps overflow action shells on the shared action-menu component', () => {
      for (const { name, source } of migratedPages) {
        expect(source, `${name} creates a private disclosure menu`).not.toMatch(/<details\b/)
        expect(source, `${name} copies public button classes onto native markup`).not.toMatch(
          /\bclass=(['"])[^'"]*\bv2-button\b[^'"]*\1/,
        )
      }
      const projectPage = readFileSync(
        resolve(sourceRoot, 'pages/projects/ProjectPage.vue'),
        'utf-8',
      )
      expect(projectPage).toContain('<V2ActionMenu')
    })

    it('keeps migrated page-specific interaction contracts', () => {
      const dailyLog = readFileSync(resolve(sourceRoot, 'pages/delivery/DailyLogPage.vue'), 'utf-8')
      const workflow = readFileSync(
        resolve(sourceRoot, 'pages/workbench/WorkflowWorkbenchPage.vue'),
        'utf-8',
      )
      const dashboard = readFileSync(
        resolve(sourceRoot, 'pages/dashboard/DashboardPage.vue'),
        'utf-8',
      )
      const components = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')
      const deliveryPages = [
        'pages/delivery/SchedulePage.vue',
        'pages/delivery/QualitySafetyPage.vue',
        'pages/delivery/TechnicalManagementPage.vue',
        'pages/delivery/ProjectCloseoutPage.vue',
      ].map((name) => readFileSync(resolve(sourceRoot, name), 'utf-8'))
      const projectPage = readFileSync(
        resolve(sourceRoot, 'pages/projects/ProjectPage.vue'),
        'utf-8',
      )
      const projectForm = readFileSync(
        resolve(sourceRoot, 'pages/projects/ProjectForm.vue'),
        'utf-8',
      )
      const costControl = readFileSync(
        resolve(sourceRoot, 'pages/commercial/CostControlPage.vue'),
        'utf-8',
      )

      expect(dailyLog).toContain("'v2-dialog-standard v2-detail-dialog'")
      expect(dailyLog).toContain("'v2-dialog-standard v2-detail-dialog daily-log-page__dialog'")
      expect(dailyLog).toContain('class="v2-detail-dialog__section"')
      expect(dailyLog).toContain('class="v2-detail-dialog__facts"')
      expect(
        dailyLog.match(/dialogMode !== 'view' && canEdit && activeRecord\.status === 'DRAFT'/g),
      ).toHaveLength(2)
      expect(dailyLog).toContain('<template v-if="dialogMode !== \'view\'" #footer>')
      expect(dailyLog).toContain(':close-on-backdrop="dialogMode === \'view\'"')
      expect(dailyLog).toContain('@backdrop-click="warnUnsavedDialog"')
      expect(dailyLog).toContain('class="daily-log-page__dialog-actions"')
      expect(dailyLog).toContain('text="选择文件"')
      expect(dailyLog).toContain('text="保存实际进度"')
      expect(dailyLog).toContain('text="保存草稿"')
      expect(dailyLog).toContain('text="提交定稿"')
      expect(dailyLog).toContain('class="daily-log-page__stack daily-log-page__linked-facts"')
      expect(workflow).toContain('panel-class="v2-dialog-standard v2-detail-dialog"')
      expect(workflow).toContain('title="审批详情"')
      expect(workflow).toContain(':open="isDetailRoute"')
      expect(workflow).toContain(':close-on-backdrop="true"')
      expect(workflow).toContain('@close="closeDetail"')
      expect(workflow).toContain('query: { returnTab: activeTab.value }')
      expect(workflow).toContain("const returnTab = String(route.query.returnTab ?? 'todo')")
      expect(workflow).toContain('<dt>审批事项</dt>')
      expect(workflow).toContain('<V2GlassButton')
      expect(workflow).toContain('class="v2-detail-dialog__section"')
      expect(workflow).toContain('class="v2-detail-dialog__facts"')
      expect(workflow).toContain('class="v2-detail-dialog__actions"')
      expect(workflow).toContain('class="workflow-table__title"')
      expect(workflow).toContain('hide-label')
      expect(workflow).toContain(':close-disabled="actionLoading"')
      expect(workflow.slice(0, workflow.indexOf('<V2Dialog'))).not.toContain('<V2GlassButton')
      expect(workflow).toContain('role="region"')
      expect(workflow).toContain('aria-label="审批任务表格"')
      expect(workflow).toContain('<caption class="v2-visually-hidden">')
      expect(workflow).toContain('<th scope="col">')
      expect(workflow).not.toContain('size="small"')
      expect(workflow).not.toContain('workflow-detail-overview')
      expect(workflow).not.toContain('workflow-summary')
      expect(dashboard).toContain('panel-class="v2-dialog-standard v2-detail-dialog"')
      expect(dashboard).toContain('class="v2-detail-dialog__section"')
      expect(dashboard).not.toContain('dashboard-alert-detail')
      for (const [index, pageClass] of [
        'schedule-page',
        'quality-page',
        'technical-page',
        'closeout-page',
      ].entries()) {
        expect(deliveryPages[index]).toMatch(
          new RegExp(`\\.${pageClass} \\{[\\s\\S]*?font-size: var\\(--v2-font-size-12\\);`),
        )
      }
      for (const [page, title] of [
        [deliveryPages[1], '质量安全整改闭环'],
        [deliveryPages[2], '图纸 RFI 技术闭环'],
        [deliveryPages[3], '竣工收尾闭环'],
      ]) {
        expect(page).toContain(`<h1 class="v2-visually-hidden">${title}</h1>`)
        expect(page).toContain('panel-class="v2-detail-dialog"')
        expect(page).not.toMatch(/<V2Card[\s\S]{0,160}v-if="trace"/)
      }
      for (const page of deliveryPages.slice(1)) {
        expect(page).toMatch(
          /__record-sections h3[\s\S]*?font-size: var\(--v2-font-size-15\);[\s\S]*?font-weight: var\(--v2-font-weight-semibold\);[\s\S]*?line-height: var\(--v2-line-height-tight\);/,
        )
        expect(page).not.toMatch(/__item\b/)
      }
      expect(deliveryPages[1]).not.toContain('quality-page__title')
      expect(deliveryPages[1]).toMatch(
        /<V2Button[\s\S]{0,180}variant="ghost"[\s\S]{0,180}:aria-pressed="selectedPlanId === plan\.id"/,
      )
      for (const page of deliveryPages) {
        expect(page).toContain('<template #footer>')
        expect(page).not.toMatch(/\{\{\s*(?:\w+\.)+(?:status|severity|conclusion)\s*\}\}/)
      }
      for (const formId of [
        'schedule-create-form',
        'schedule-wbs-form',
        'schedule-period-form',
        'schedule-corrective-form',
      ]) {
        expect(deliveryPages[0]).toContain(`id="${formId}"`)
        expect(deliveryPages[0]).toContain(`type="submit" form="${formId}"`)
      }
      expect(deliveryPages[0]).toMatch(/class="schedule-page__span-2"[\s\S]*?label="项目"/)
      expect(deliveryPages[0]).toContain('label="计划编号"')
      expect(projectPage).toContain('class="project-form--dialog"')
      expect(projectForm).toMatch(
        /\.project-form--dialog \{[\s\S]*?grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/,
      )
      expect(projectForm).not.toContain('.project-form--dialog :deep(.v2-field__control)')
      expect(components).toContain('.v2-dialog-standard label:not(.v2-field) {')
      expect(components).toContain('.v2-dialog-standard .v2-field__control,')
      expect(components).toContain('var(--v2-font-size-12) / var(--v2-line-height-ui)')
      expect(components).toContain('var(--v2-font-size-13) / var(--v2-line-height-ui)')
      expect(costControl).toContain('class="v2-detail-dialog__section"')
      for (const formId of [
        'cost-forecast-form',
        'cost-corrective-form',
        'cost-corrective-close-form',
      ]) {
        expect(costControl).toContain(`id="${formId}"`)
        expect(costControl).toContain(`type="submit" form="${formId}"`)
      }
      expect(costControl.match(/<template #footer>/g)).toHaveLength(3)
      expect(costControl.match(/<V2GlassButton/g)).toHaveLength(3)
      expect(components).toContain('.v2-detail-dialog .v2-card {')
      expect(components).toMatch(/\.v2-dialog-standard \{[\s\S]*?width: min\(32rem, 100%\);/)
      expect(components).toMatch(/@media \(min-width: 64rem\) \{[\s\S]*?width: min\(46rem, 100%\);/)
      expect(components).toContain('scrollbar-width: none;')
      expect(components).toContain('.v2-dialog__panel::-webkit-scrollbar')
      expect(components).toContain('.v2-select__menu::-webkit-scrollbar')
      expect(components).not.toContain('.v2-dialog__panel:has(.v2-select[open])')
      expect(dailyLog).toContain('class="daily-log-page__table v2-table--top"')
      expect(components).toMatch(
        /#shell-main-content table \{[\s\S]*?font-size: var\(--v2-font-size-12\);/,
      )
      expect(dailyLog).toMatch(
        /\.daily-log-page__panel \{[\s\S]*?font-size: var\(--v2-font-size-12\);/,
      )
      expect(dailyLog).toMatch(
        /\.daily-log-page__facts,[\s\S]*?\.daily-log-page__summary \{[\s\S]*?font-size: var\(--v2-font-size-12\);/,
      )
      expect(dailyLog).toMatch(
        /\.daily-log-page__form \{[\s\S]*?font-size: var\(--v2-font-size-12\);/,
      )
      expect(dailyLog).not.toContain('.daily-log-page__form :deep(.v2-field__control)')
      expect(dailyLog).toMatch(
        /\.daily-log-page__pagination \{[\s\S]*?font-size: var\(--v2-font-size-12\);/,
      )
      expect(dailyLog).toMatch(
        /\.daily-log-page__linked-facts \{[\s\S]*?grid-template-columns: repeat\(4, minmax\(0, 1fr\)\);/,
      )
      expect(components).toMatch(/\.v2-detail-dialog \.v2-card \{[\s\S]*?border: 0;/)
      expect(components).toMatch(/\.v2-detail-dialog \.v2-card \{[\s\S]*?background: transparent;/)
      expect(components).toMatch(/\.v2-detail-dialog__quick-actions \{[\s\S]*?display: flex;/)
    })

    it('keeps navigation accents separate from risk colors', () => {
      const appShell = readFileSync(resolve(sourceRoot, 'layouts/AppShell.vue'), 'utf-8')
      const placeholder = readFileSync(
        resolve(sourceRoot, 'pages/shell/ShellPlaceholderPage.vue'),
        'utf-8',
      )

      expect(appShell).toContain('var(--v2-color-workspace-tab-accent)')
      expect(placeholder).toContain('title="业务页面建设中" tone="info"')
      expect(placeholder).not.toContain('tone="warning"')
    })

    it('keeps mobile shell context controls touch sized and previews every shared state primitive', () => {
      const appShell = readFileSync(resolve(sourceRoot, 'layouts/AppShell.vue'), 'utf-8')
      const preview = readFileSync(
        resolve(sourceRoot, 'components/preview/DesignSystemPreview.vue'),
        'utf-8',
      )

      expect(appShell).toMatch(
        /\.app-shell__context-controls :deep\(\.v2-field__control\) \{[\s\S]*?min-height: var\(--v2-control-height-touch\);/,
      )
      for (const component of [
        'V2ConfirmDialog',
        'V2ErrorBoundary',
        'V2GlassButton',
        'V2PageState',
      ]) {
        expect(preview).toContain(`<${component}`)
      }
    })

    it('keeps project and report-period selectors owned by the shared shell', () => {
      const appShell = readFileSync(resolve(sourceRoot, 'layouts/AppShell.vue'), 'utf-8')
      const contextControlPattern =
        /<(?:V2Select|V2Input|select|input)\b(?:(?:"[^"]*"|'[^']*')|[^'">])*>/g
      const pageOwnedContextBinding =
        /\b(?:v-model|:model-value)=(['"])(?:workspace\.selected(?:ProjectId|ReportPeriod)|(?:filter\.)?(?:projectId|reportPeriod|period))\1/
      const pageOwnedContextUpdate =
        /@update:model-value=(['"])workspace\.select(?:Project|ReportPeriod)\1/

      expect(appShell).toContain('id="global-project"')
      expect(appShell).toContain('id="global-report-period"')
      expect(appShell).toContain(
        'grid-template-columns: minmax(16rem, 20rem) minmax(11rem, 14rem);',
      )
      expect(appShell).toMatch(
        /\.app-shell__context-controls :deep\(\.v2-field\) \{[\s\S]*?grid-template-columns: minmax\(0, 1fr\);/,
      )
      expect(appShell).toMatch(
        /@media \(max-width: 70rem\)[\s\S]*?\.app-shell__context-controls \{[\s\S]*?grid-template-columns: repeat\(2, minmax\(10rem, 1fr\)\);/,
      )
      expect(migratedPages.length).toBeGreaterThan(0)

      for (const { name, source } of migratedPages) {
        expect(source, `${name} duplicated project scope card`).not.toContain('title="项目范围"')
        expect(source, `${name} duplicated shell context update`).not.toMatch(
          pageOwnedContextUpdate,
        )
        for (const [control] of source.matchAll(contextControlPattern)) {
          expect(control, `${name} must reuse shared shell context selectors`).not.toMatch(
            pageOwnedContextBinding,
          )
        }
      }
    })

    it('locks the current V2 standard style contract', () => {
      const baseline = readFileSync(
        resolve(sourceRoot, '../../docs/ui-v2/m1-design-system-baseline.md'),
        'utf-8',
      )
      const mainline = readFileSync(
        resolve(
          sourceRoot,
          '../../docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md',
        ),
        'utf-8',
      )
      const components = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')
      const glassButton = readFileSync(resolve(sourceRoot, 'components/V2GlassButton.vue'), 'utf-8')
      const dailyLog = readFileSync(resolve(sourceRoot, 'pages/delivery/DailyLogPage.vue'), 'utf-8')
      const workflow = readFileSync(
        resolve(sourceRoot, 'pages/workbench/WorkflowWorkbenchPage.vue'),
        'utf-8',
      )

      for (const marker of [
        '现行 V2 标准样式合同',
        'v2-dialog-standard',
        'v2-detail-dialog',
        'V2ConfirmDialog',
        'V2GlassButton',
        '上一页 — 第 N 页 — 下一页',
      ]) {
        expect(baseline).toContain(marker)
      }
      expect(baseline).not.toContain('21–28')
      expect(baseline).toContain('审批工作台是受控例外')
      expect(baseline).toContain('完整详情与快速预览均使用标准只读 `V2Dialog`')
      expect(baseline).toContain('点击遮罩、按 Escape 或点击关闭按钮')
      expect(baseline).toContain('页面标题区、筛选区、表格行、分页和正文中的')
      expect(baseline).toContain('`V2ConfirmDialog` 继续使用语义明确的普通或危险按钮')
      expect(mainline).not.toContain('每个实施阶段必须在 Stitch 交付可编辑设计')
      expect(mainline).toContain('只有新增视觉方向、复杂交互或现有模式无法覆盖时')

      expect(components).toMatch(
        /\.v2-detail-dialog__section \{[\s\S]*?margin-block-end: 10px;[\s\S]*?padding-block-end: 10px;[\s\S]*?color-mix\(in srgb, var\(--v2-color-primary\) 22%, var\(--v2-color-surface\)\);/,
      )
      expect(glassButton).toContain('color-mix(in srgb, var(--v2-color-surface) 50%, transparent)')
      expect(glassButton).toContain('backdrop-filter: blur(16px) saturate(160%);')
      expect(glassButton).toContain('-webkit-backdrop-filter: blur(16px) saturate(160%);')
      expect(glassButton).toContain('.v2-glass-button.v2-button:hover:not(:disabled)')
      expect(glassButton).toContain('.v2-glass-button.v2-button:active:not(:disabled)')
      expect(glassButton).toContain('.v2-glass-button.v2-button:focus-visible')
      expect(glassButton).toContain('@media (prefers-reduced-motion: reduce)')

      for (const source of [dailyLog, workflow]) {
        const previous = source.indexOf('上一页')
        const current = source.indexOf('第 {{ pageNo }} 页')
        const next = source.indexOf('下一页')
        expect(previous).toBeGreaterThan(-1)
        expect(previous).toBeLessThan(current)
        expect(current).toBeLessThan(next)
      }
    })
  })
})
