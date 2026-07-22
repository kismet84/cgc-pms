import { readFileSync, readdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import {
  V2Alert,
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

  it('covers card, badge, alert and skeleton status primitives', async () => {
    const card = mount(V2Card, {
      props: { title: '经营健康度', subtitle: '当前报告期', interactive: true },
      slots: { default: '面板内容', footer: '底部动作' },
    })
    expect(card.get('.v2-card__title').text()).toBe('经营健康度')
    expect(card.get('h2').exists()).toBe(true)
    expect(card.classes()).toContain('v2-card--interactive')

    const pageCard = mount(V2Card, {
      props: { title: '现场日报', headingLevel: 1, titleId: 'daily-log-title' },
    })
    expect(pageCard.get('h1').attributes('id')).toBe('daily-log-title')
    expect(pageCard.get('h1').classes()).toContain('v2-card__title--page')

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
    expect(implementationSources).not.toMatch(/font-size:\s*[0-9.]+(?:px|rem)\b/i)
    expect(pageAndLayoutSources).not.toMatch(/\.(?:sr-only|v2-visually-hidden)\s*\{/)
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

  it('keeps native browser confirmation dialogs out of V2 pages', () => {
    const pageSources = readdirSync(resolve(sourceRoot, 'pages'), { recursive: true })
      .filter((name) => name.endsWith('.vue'))
      .map((name) => readFileSync(resolve(sourceRoot, 'pages', name), 'utf-8'))
      .join('\n')

    expect(pageSources).not.toContain('window.confirm(')
  })

  it('uses the standard dialog shell for confirmation and detail dialogs', () => {
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
    expect(workflow).toContain('<dt>审批事项</dt>')
    expect(workflow).toContain('<V2GlassButton')
    expect(workflow).toContain('class="v2-detail-dialog__section"')
    expect(workflow).toContain('class="v2-detail-dialog__facts"')
    expect(workflow).toContain('class="v2-detail-dialog__actions"')
    expect(workflow).toContain('<span>第 {{ pageNo }} 页</span>')
    expect(workflow).toMatch(
      /\.workflow-pagination \{[\s\S]*?justify-content: flex-end;[\s\S]*?font-size: var\(--v2-font-size-12\);/,
    )
    expect(workflow).not.toContain('workflow-detail-overview')
    expect(workflow).not.toContain('workflow-summary')
    expect(dashboard).toContain('panel-class="v2-dialog-standard v2-detail-dialog"')
    expect(dashboard).toContain('class="v2-detail-dialog__section"')
    expect(dashboard).not.toContain('dashboard-alert-detail')
    for (const page of deliveryPages) {
      expect(page).toContain('panel-class="v2-dialog-standard')
      expect(page).toContain(':close-on-backdrop="false"')
      expect(page).toContain('<template #footer>')
      expect(page).not.toMatch(/权威|回读|重读|后端状态|后端阶段/)
      expect(page).not.toMatch(/\{\{\s*(?:\w+\.)+(?:status|severity|conclusion)\s*\}\}/)
    }
    for (const page of deliveryPages.slice(1)) {
      expect(page).not.toContain('title="项目范围"')
      expect(page).not.toContain('v-model="workspace.selectedProjectId"')
      expect(page).not.toContain('@update:model-value="workspace.selectProject"')
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
    expect(components).toContain('.v2-detail-dialog .v2-card {')
    expect(components).toMatch(/\.v2-dialog-standard \{[\s\S]*?width: min\(32rem, 100%\);/)
    expect(components).toMatch(/@media \(min-width: 64rem\) \{[\s\S]*?width: min\(46rem, 100%\);/)
    expect(components).toContain('scrollbar-width: none;')
    expect(components).toContain('.v2-dialog__panel::-webkit-scrollbar')
    expect(components).toContain('.v2-select__menu::-webkit-scrollbar')
    expect(components).not.toContain('.v2-dialog__panel:has(.v2-select[open])')
    expect(dailyLog).toMatch(
      /\.daily-log-page__table th,[\s\S]*?\.daily-log-page__table td \{[\s\S]*?font-size: var\(--v2-font-size-12\);/,
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
    expect(dailyLog).toMatch(
      /\.daily-log-page__form input,[\s\S]*?\.daily-log-page__form textarea \{[\s\S]*?background: transparent;[\s\S]*?border: 1px solid color-mix\(in srgb, var\(--v2-color-primary\) 22%, var\(--v2-color-surface\)\);/,
    )
    expect(dailyLog).toMatch(
      /\.daily-log-page__form :deep\(\.v2-field__control\) \{[\s\S]*?background: transparent;[\s\S]*?border-color: color-mix\(in srgb, var\(--v2-color-primary\) 22%, var\(--v2-color-surface\)\);/,
    )
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

  it('locks the current V2 standard style contract', () => {
    const baseline = readFileSync(
      resolve(sourceRoot, '../../docs/ui-v2/m1-design-system-baseline.md'),
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
