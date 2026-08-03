import { readFileSync, readdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { V2Button, V2Input, V2Select } from '@/components'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourceRoot = resolve(currentDir, '../../src')
const repositoryRoot = resolve(currentDir, '../../..')
const uiStandardPath = resolve(
  repositoryRoot,
  'docs/standards/00-UI-Design-Baselines-and-Code-Specifications.md',
)

function vueSources(root: string): string[] {
  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(root, entry.name)
    return entry.isDirectory()
      ? vueSources(path)
      : entry.name.endsWith('.vue')
        ? [readFileSync(path, 'utf-8')]
        : []
  })
}

describe('V2 design system', () => {
  it('keeps public components documented and design checks wired into CI', () => {
    const standard = readFileSync(uiStandardPath, 'utf-8')
    const componentIndex = readFileSync(resolve(sourceRoot, 'components/index.ts'), 'utf-8')
    const packageJson = readFileSync(resolve(sourceRoot, '../package.json'), 'utf-8')
    const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/ci.yml'), 'utf-8')
    const exportedComponents = [...componentIndex.matchAll(/default as (V2[A-Za-z]+)/g)].map(
      ([, name]) => name,
    )

    expect(exportedComponents.length).toBeGreaterThan(0)
    for (const name of exportedComponents) expect(standard).toContain(`\`${name}\``)
    expect(packageJson).toContain('"check:design-system"')
    expect(workflow).toContain('pnpm check:design-system')
  })

  it('forwards native button form association', async () => {
    const form = document.createElement('form')
    form.id = 'external-form'
    const submitted = vi.fn((event: SubmitEvent) => event.preventDefault())
    form.addEventListener('submit', submitted)
    document.body.appendChild(form)
    const wrapper = mount(V2Button, {
      attachTo: document.body,
      props: { type: 'submit' },
      attrs: { form: form.id },
      slots: { default: '提交' },
    })

    await wrapper.get('button').trigger('click')
    expect(wrapper.get('button').attributes('form')).toBe(form.id)
    expect(submitted).toHaveBeenCalledOnce()

    wrapper.unmount()
    form.remove()
  })

  it('associates input feedback and emits model updates', async () => {
    const wrapper = mount(V2Input, {
      props: { label: '项目名称', hint: '输入项目名称', modelValue: '' },
    })
    const input = wrapper.get('input')

    expect(wrapper.get('label').attributes('for')).toBe(input.attributes('id'))
    expect(input.attributes('aria-describedby')).toBe(
      wrapper.get('.v2-field__hint').attributes('id'),
    )
    await input.setValue('金融中心项目')
    expect(wrapper.emitted('update:modelValue')).toEqual([['金融中心项目']])

    await wrapper.setProps({ error: '项目名称不能为空', hideLabel: true })
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(input.attributes('aria-describedby')).toBe(
      wrapper.get('.v2-field__error').attributes('id'),
    )
    expect(wrapper.get('.v2-field__label').classes()).toContain('v2-visually-hidden')
  })

  it('pads valid two-decimal inputs on blur without changing over-precision values', async () => {
    const wrapper = mount(V2Input, { props: { modelValue: '', decimalScale: 2 } })
    const input = wrapper.get('input')

    expect(input.attributes('inputmode')).toBe('decimal')
    await input.setValue('12.3')
    await input.trigger('blur')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['12.30'])

    await input.setValue('12.345')
    const overPrecisionEventCount = wrapper.emitted('update:modelValue')?.length
    await input.trigger('blur')
    expect(wrapper.emitted('update:modelValue')).toHaveLength(overPrecisionEventCount ?? 0)
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['12.345'])
  })

  it('uses native select semantics for labels, options and model updates', async () => {
    const wrapper = mount(V2Select, {
      props: {
        label: '报告期',
        hint: '选择月份',
        modelValue: '',
        required: true,
        options: [
          { value: '2026-07', label: '2026年7月' },
          { value: '2026-06', label: '2026年6月', disabled: true },
        ],
      },
    })
    const select = wrapper.get('select')
    const options = wrapper.findAll('option')

    expect(wrapper.get('label').attributes('for')).toBe(select.attributes('id'))
    expect(select.attributes()).toHaveProperty('required')
    expect(select.attributes('aria-describedby')).toBe(
      wrapper.get('.v2-field__hint').attributes('id'),
    )
    expect(options).toHaveLength(3)
    expect(options[0]?.attributes()).toHaveProperty('disabled')
    expect(options[2]?.attributes()).toHaveProperty('disabled')
    await select.setValue('2026-07')
    expect(wrapper.emitted('update:modelValue')).toEqual([['2026-07']])
  })

  it('keeps native select state and accessible naming when label is visually hidden', () => {
    const wrapper = mount(V2Select, {
      props: {
        label: '项目状态',
        hideLabel: true,
        disabled: true,
        required: true,
        error: '请选择项目状态',
        ariaLabelledby: 'project-status-heading',
        options: [{ value: 'ACTIVE', label: '在建' }],
      },
    })
    const select = wrapper.get('select')

    expect(select.attributes()).toMatchObject({
      disabled: '',
      required: '',
      'aria-label': '项目状态',
      'aria-labelledby': 'project-status-heading',
      'aria-invalid': 'true',
    })
    expect(select.attributes('aria-describedby')).toBe(
      wrapper.get('.v2-field__error').attributes('id'),
    )
    expect(wrapper.get('.v2-field__label').classes()).toContain('v2-visually-hidden')
  })

  it('supports explicit and inferred empty select options without duplicates', () => {
    const explicit = mount(V2Select, {
      props: {
        modelValue: '',
        allowEmpty: true,
        options: [
          { value: '', label: '全部项目' },
          { value: '1', label: '项目一' },
        ],
      },
    })
    const inferred = mount(V2Select, {
      props: {
        modelValue: 'BUILDING',
        allowEmpty: true,
        placeholder: '全部类型',
        options: [{ value: 'BUILDING', label: '施工总承包' }],
      },
    })

    expect(explicit.findAll('option')).toHaveLength(2)
    expect(explicit.findAll('option')[0]?.text()).toBe('全部项目')
    expect(inferred.findAll('option')).toHaveLength(2)
    expect(inferred.findAll('option')[0]?.text()).toBe('全部类型')
  })

  it('keeps a small set of architectural bans', () => {
    const pageSources = vueSources(resolve(sourceRoot, 'pages')).join('\n')
    const componentSources = vueSources(resolve(sourceRoot, 'components')).join('\n')
    const componentCss = readFileSync(resolve(sourceRoot, 'styles/components.css'), 'utf-8')

    expect(pageSources).not.toContain('window.confirm(')
    expect(pageSources).not.toContain('<V2GlassButton')
    expect(componentSources).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    expect(componentCss).not.toMatch(/#[0-9a-f]{3,8}\b/i)
  })
})
