import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

// ── Mock request (vi.mock hoisted) ──
const mockGet = vi.fn()
const mockPut = vi.fn()

vi.mock('@/api/request', () => ({
  request: {
    get: (...args: unknown[]) => mockGet(...args),
    put: (...args: unknown[]) => mockPut(...args),
  },
}))

// ── Mock ant-design-vue message ──
vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  },
}))

import { message } from 'ant-design-vue'
import SettingsPage from '@/pages/settings/index.vue'

// ── Stub Components ──
const ACardStub = defineComponent({
  name: 'ACardStub',
  props: {
    title: String,
    bordered: { type: Boolean, default: true },
    loading: Boolean,
  },
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'mock-card' }, [
        props.title ? h('div', { class: 'mock-card-title' }, props.title) : null,
        props.loading ? h('div', { class: 'mock-card-loading' }, 'loading...') : null,
        h('div', { class: 'mock-card-body' }, slots.default?.()),
      ])
  },
})

const AFormStub = defineComponent({
  name: 'AFormStub',
  props: { layout: String },
  setup(_, { slots }) {
    return () => h('form', { class: 'mock-form' }, slots.default?.())
  },
})

const AFormItemStub = defineComponent({
  name: 'AFormItemStub',
  props: { label: String },
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'mock-form-item' }, [
        props.label ? h('label', { class: 'mock-form-label' }, props.label) : null,
        h('div', { class: 'mock-form-control' }, slots.default?.()),
      ])
  },
})

const ASwitchStub = defineComponent({
  name: 'ASwitchStub',
  props: { checked: Boolean },
  emits: ['update:checked'],
  setup(props, { emit }) {
    return () =>
      h(
        'button',
        {
          class: ['mock-switch', props.checked ? 'mock-switch-checked' : ''],
          type: 'button',
          onClick: () => emit('update:checked', !props.checked),
        },
        props.checked ? 'ON' : 'OFF',
      )
  },
})

const ARadioGroupStub = defineComponent({
  name: 'ARadioGroupStub',
  props: { value: String },
  emits: ['update:value'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'div',
        {
          class: 'mock-radio-group',
          onClick: (e: MouseEvent) => {
            const target = e.target as HTMLElement
            const radio = target.closest('[data-radio-value]')
            if (radio) {
              const val = radio.getAttribute('data-radio-value') ?? ''
              emit('update:value', val)
            }
          },
        },
        slots.default?.(),
      )
  },
})

const ARadioStub = defineComponent({
  name: 'ARadioStub',
  props: { value: String },
  setup(props, { slots }) {
    return () =>
      h(
        'span',
        { class: 'mock-radio', 'data-radio-value': props.value },
        slots.default?.(),
      )
  },
})

const AButtonStub = defineComponent({
  name: 'AButtonStub',
  props: { type: String, loading: Boolean },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'button',
        {
          class: 'mock-btn',
          type: 'button',
          disabled: props.loading,
          onClick: () => emit('click'),
        },
        slots.default?.(),
      )
  },
})

const stubs = {
  'a-card': ACardStub,
  'a-form': AFormStub,
  'a-form-item': AFormItemStub,
  'a-switch': ASwitchStub,
  'a-radio-group': ARadioGroupStub,
  'a-radio': ARadioStub,
  'a-button': AButtonStub,
}

// ── Helpers ──
function findSwitch(wrapper: VueWrapper, index: number) {
  return wrapper.findAll('.mock-switch')[index]
}

function clickSwitch(wrapper: VueWrapper, index: number) {
  const sw = findSwitch(wrapper, index)
  if (sw) {
    ;(sw.element as HTMLButtonElement).click()
  }
}

function isSwitchChecked(wrapper: VueWrapper, index: number) {
  const sw = findSwitch(wrapper, index)
  return sw?.classes().includes('mock-switch-checked') ?? false
}

function clickRadio(wrapper: VueWrapper, selector: string) {
  const radio = wrapper.find(selector)
  if (radio.exists()) {
    ;(radio.element as HTMLElement).click()
  }
}

// ── Tests ──
describe('SettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const defaultPreferences = {
    sidebarCollapsed: false,
    notificationEnabled: true,
    theme: 'light',
    tableDensity: 'middle',
  }

  it('fetches preferences on mount', async () => {
    mockGet.mockResolvedValue(defaultPreferences)

    mount(SettingsPage, { global: { stubs } })
    await flushPromises()

    expect(mockGet).toHaveBeenCalledTimes(1)
    expect(mockGet).toHaveBeenCalledWith('/profile/preferences')
  })

  it('toggles notification switch changes its value', async () => {
    mockGet.mockResolvedValue(defaultPreferences)

    const wrapper = mount(SettingsPage, { global: { stubs } })
    await flushPromises()

    // Notification switch is the first one (index 0)
    expect(isSwitchChecked(wrapper, 0)).toBe(true)

    // Toggle off
    clickSwitch(wrapper, 0)
    await nextTick()

    expect(isSwitchChecked(wrapper, 0)).toBe(false)
  })

  it('toggles sidebar collapsed switch changes its value', async () => {
    mockGet.mockResolvedValue(defaultPreferences)

    const wrapper = mount(SettingsPage, { global: { stubs } })
    await flushPromises()

    // Sidebar switch is the second one (index 1)
    expect(isSwitchChecked(wrapper, 1)).toBe(false)

    // Toggle on
    clickSwitch(wrapper, 1)
    await nextTick()

    expect(isSwitchChecked(wrapper, 1)).toBe(true)
  })

  it('selects theme radio option', async () => {
    mockGet.mockResolvedValue(defaultPreferences)

    const wrapper = mount(SettingsPage, { global: { stubs } })
    await flushPromises()

    // Theme radio group: click "dark"
    clickRadio(wrapper, '[data-radio-value="dark"]')
    await nextTick()

    const darkRadio = wrapper.find('[data-radio-value="dark"]')
    expect(darkRadio.exists()).toBe(true)
  })

  it('save button calls PUT with updated values', async () => {
    mockGet.mockResolvedValue(defaultPreferences)
    mockPut.mockResolvedValue(defaultPreferences)

    const wrapper = mount(SettingsPage, { global: { stubs } })
    await flushPromises()

    // Toggle sidebar collapsed on
    clickSwitch(wrapper, 1)
    await nextTick()

    // Click save
    const saveBtn = wrapper.find('.mock-btn')
    await saveBtn.trigger('click')
    await flushPromises()

    expect(mockPut).toHaveBeenCalledTimes(1)
    expect(mockPut).toHaveBeenCalledWith('/profile/preferences', {
      theme: 'light',
      sidebarCollapsed: true,
      notificationEnabled: true,
      tableDensity: 'middle',
    })
  })

  it('shows success message on save success', async () => {
    mockGet.mockResolvedValue(defaultPreferences)
    mockPut.mockResolvedValue(defaultPreferences)

    const wrapper = mount(SettingsPage, { global: { stubs } })
    await flushPromises()

    const saveBtn = wrapper.find('.mock-btn')
    await saveBtn.trigger('click')
    await flushPromises()

    expect(vi.mocked(message.success)).toHaveBeenCalledWith('保存成功')
  })

  it('shows error message on API fetch failure', async () => {
    mockGet.mockRejectedValue(new Error('Network error'))

    mount(SettingsPage, { global: { stubs } })
    await flushPromises()

    expect(vi.mocked(message.error)).toHaveBeenCalledWith('加载偏好设置失败')
  })

  it('shows error message on save failure', async () => {
    mockGet.mockResolvedValue(defaultPreferences)
    mockPut.mockRejectedValue(new Error('Network error'))

    const wrapper = mount(SettingsPage, { global: { stubs } })
    await flushPromises()

    const saveBtn = wrapper.find('.mock-btn')
    await saveBtn.trigger('click')
    await flushPromises()

    expect(vi.mocked(message.error)).toHaveBeenCalledWith('保存失败')
  })
})
