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
  V2Dialog,
  V2Grid,
  V2Input,
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

  it('renders a native select with placeholder and disabled options', async () => {
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

    expect(wrapper.findAll('option')).toHaveLength(3)
    expect(wrapper.findAll('option')[2]?.attributes()).toHaveProperty('disabled')
    await wrapper.get('select').setValue('2026-07')
    expect(wrapper.emitted('update:modelValue')).toEqual([['2026-07']])
  })

  it('supports a selectable empty option without duplicating the placeholder', async () => {
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

    expect(wrapper.findAll('option')).toHaveLength(2)
    expect(wrapper.get('select').element.selectedOptions[0]?.textContent).toBe('全部项目')
  })

  it('covers card, badge, alert and skeleton status primitives', async () => {
    const card = mount(V2Card, {
      props: { title: '经营健康度', subtitle: '当前报告期', interactive: true },
      slots: { default: '面板内容', footer: '底部动作' },
    })
    expect(card.get('.v2-card__title').text()).toBe('经营健康度')
    expect(card.classes()).toContain('v2-card--interactive')

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
    await alert.get('button').trigger('click')
    expect(alert.emitted('dismiss')).toHaveLength(1)

    const skeleton = mount(V2Skeleton, { props: { variant: 'circle' } })
    expect(skeleton.attributes('aria-busy')).toBe('true')
    expect(skeleton.classes()).toContain('v2-skeleton--circle')
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
})
