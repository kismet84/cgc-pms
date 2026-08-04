import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DictionaryPage from '@/pages/system/DictionaryPage.vue'
import { apiRequest } from '@/services/request'
import { loadDictTree, loadEnabledDictDataByCode } from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/request', () => ({
  apiRequest: vi.fn(),
  isApiClientError: vi.fn(() => false),
}))

const data = Array.from({ length: 12 }, (_, index) => ({
  id: String(100 + index),
  dictTypeId: '20',
  dictLabel: `项目状态${index + 1}`,
  dictValue: `STATUS_${index + 1}`,
  orderNum: index + 1,
  status: 'ENABLE',
}))

const tree = [
  {
    id: '10',
    groupCode: 'PROJECT',
    groupName: '项目管理',
    orderNum: 1,
    status: 'ENABLE',
    types: [
      {
        id: '20',
        groupId: '10',
        dictCode: 'project_status',
        dictName: '项目状态',
        dictClass: 'BUSINESS',
        status: 'ENABLE',
        data,
      },
    ],
  },
]

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  useSessionStore().replaceUserInfo({
    userId: '1',
    username: 'admin',
    realName: '管理员',
    tenantId: '0',
    roles: ['ADMIN'],
    permissions: ['system:dict:list', 'system:dict:add', 'system:dict:edit', 'system:dict:delete'],
  })
  vi.mocked(apiRequest).mockResolvedValue(tree)
})

describe('系统字典三级管理', () => {
  it('消费服务端树事实并透传跨层搜索关键字', async () => {
    const result = await loadDictTree('项目状态')

    expect(apiRequest).toHaveBeenCalledWith(
      '/system/dict/tree?keyword=%E9%A1%B9%E7%9B%AE%E7%8A%B6%E6%80%81',
      {
        signal: undefined,
      },
    )
    expect(result[0]).toMatchObject({ id: '10', groupCode: 'PROJECT', orderNum: 1 })
    expect(result[0]?.types[0]).toMatchObject({ id: '20', groupId: '10', dictClass: 'BUSINESS' })
    expect(result[0]?.types[0]?.data[0]).toMatchObject({ id: '100', dictTypeId: '20' })
  })

  it('按服务端字典编码读取启用项并统一规范化标识与排序', async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce([
      { ...data[0], id: 100, dictTypeId: 20, orderNum: '2' },
    ])

    const result = await loadEnabledDictDataByCode('project_status')

    expect(apiRequest).toHaveBeenCalledWith('/system/dict/data/by-code/project_status', {
      signal: undefined,
    })
    expect(result).toEqual([{ ...data[0], id: '100', dictTypeId: '20', orderNum: 2 }])
  })

  it('默认常驻首个分组和类型，当前级固定十条且整卡只有一个分页页脚', async () => {
    const wrapper = mount(DictionaryPage)
    await flushPromises()
    const groupItems = () =>
      wrapper.findAll(
        '.dictionary-page__columns > section:nth-of-type(1) .dictionary-page__list-item',
      )
    const typeItems = () =>
      wrapper.findAll(
        '.dictionary-page__columns > section:nth-of-type(2) .dictionary-page__list-item',
      )

    expect(wrapper.text()).toContain('项目管理')
    expect(wrapper.text()).not.toContain('PROJECT')
    expect(wrapper.text()).toContain('项目状态')
    expect(wrapper.text()).not.toContain('project_status')
    expect(groupItems()).toHaveLength(1)
    expect(groupItems()[0]?.classes()).toContain('is-selected')
    expect(wrapper.text()).not.toContain('更换分组')
    expect(typeItems()).toHaveLength(1)
    expect(typeItems()[0]?.classes()).toContain('is-selected')
    expect(wrapper.text()).not.toContain('更换类型')
    expect(wrapper.findAll('tbody tr')).toHaveLength(10)
    expect(wrapper.text()).toContain('共 12 条')
    expect(wrapper.text()).toContain('第 1 页')
    expect(wrapper.findAll('.v2-pagination')).toHaveLength(1)
  })

  it('非管理员即使持有字典写权限也不显示维护入口', async () => {
    useSessionStore().replaceUserInfo({
      userId: '2',
      username: 'auditor',
      realName: '审计员',
      tenantId: '0',
      roles: ['AUDITOR'],
      permissions: [
        'system:dict:list',
        'system:dict:add',
        'system:dict:edit',
        'system:dict:delete',
      ],
    })

    const wrapper = mount(DictionaryPage)
    await flushPromises()

    expect(wrapper.text()).not.toContain('新增分组')
    expect(wrapper.find('.v2-action-menu').exists()).toBe(false)
    expect(wrapper.get('button[role="switch"]').attributes('disabled')).toBe('')
  })

  it('使用无选择框状态标签确认更新完整字典项并回读字典树', async () => {
    const wrapper = mount(DictionaryPage, { attachTo: document.body })
    await flushPromises()

    const statusSwitch = wrapper.get('tbody button[role="switch"]')
    expect(statusSwitch.attributes('aria-checked')).toBe('true')
    expect(wrapper.find('input[role="switch"]').exists()).toBe(false)
    await statusSwitch.trigger('click')
    await flushPromises()
    const confirmDialog = document.body.querySelector<HTMLElement>('.v2-confirm-dialog')!
    ;[...confirmDialog.querySelectorAll('button')]
      .find((candidate) => candidate.textContent?.trim() === '停用')!
      .click()
    await flushPromises()

    expect(apiRequest).toHaveBeenCalledWith('/system/dict/data/100', {
      method: 'PUT',
      body: {
        dictTypeId: '20',
        dictLabel: '项目状态1',
        dictValue: 'STATUS_1',
        cssClass: '',
        listClass: '',
        orderNum: 1,
        status: 'DISABLE',
      },
    })
    expect(
      vi
        .mocked(apiRequest)
        .mock.calls.filter(([path]) => String(path).startsWith('/system/dict/tree')),
    ).toHaveLength(2)
    wrapper.unmount()
  })

  it('搜索命中后自动定位必要父链并保留选中态', async () => {
    const wrapper = mount(DictionaryPage)
    await flushPromises()
    vi.mocked(apiRequest).mockClear()

    await wrapper.get('input[placeholder="搜索分组、类型或字典项"]').setValue('项目状态12')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(String(vi.mocked(apiRequest).mock.calls[0]?.[0])).toContain('keyword=')
    expect(wrapper.text()).toContain('项目管理')
    expect(wrapper.text()).toContain('项目状态')
    expect(wrapper.find('tr.is-selected').exists()).toBe(true)
  })
})
