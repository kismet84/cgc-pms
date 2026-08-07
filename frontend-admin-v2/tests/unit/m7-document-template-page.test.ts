import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DocumentTemplatePage from '@/pages/system/DocumentTemplatePage.vue'
import * as service from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/system-management', () => ({
  bindDefaultDocumentVersion: vi.fn(),
  createDocumentTemplate: vi.fn(),
  createDocumentVersion: vi.fn(),
  deleteDocumentTemplate: vi.fn(),
  disableDocumentVersion: vi.fn(),
  enableDocumentVersion: vi.fn(),
  loadDocumentBusinessTypes: vi.fn(),
  loadDocumentFieldCatalog: vi.fn(),
  loadDocumentTemplate: vi.fn(),
  loadDocumentTemplates: vi.fn(),
  publishDocumentVersion: vi.fn(),
  previewDocumentTemplateHtml: vi.fn(),
  previewDocumentTemplateVersionHtml: vi.fn(),
  updateDocumentVersion: vi.fn(),
}))

const paymentTemplate = {
  id: 'template-payment',
  templateCode: 'PAYMENT-001',
  templateName: '付款申请模板',
  businessType: 'PAYMENT' as const,
  enabled: 1,
}
const settlementTemplate = {
  id: 'template-settlement',
  templateCode: 'SETTLEMENT-001',
  templateName: '结算模板',
  businessType: 'SETTLEMENT' as const,
  enabled: 1,
}
const paymentDetail = {
  template: paymentTemplate,
  versions: [
    {
      id: 'version-1',
      templateId: paymentTemplate.id,
      versionNo: 1,
      status: 'PUBLISHED' as const,
      schemaVersion: 'payment.v1',
      templateContent: '<p>v1</p>',
      fieldManifest: '["payment.code"]',
      contentHash: 'hash-v1',
      designSchema: JSON.stringify({
        schemaVersion: 'payment.v1',
        page: {
          size: 'A4',
          orientation: 'PORTRAIT',
          marginMm: { top: 12, right: 12, bottom: 12, left: 12 },
        },
        elements: [
          {
            id: 'historical-code',
            type: 'FIELD',
            xMm: 10,
            yMm: 10,
            widthMm: 60,
            heightMm: 10,
            fieldPath: 'payment.code',
          },
        ],
        tables: [],
      }),
      publishedAt: '2026-08-01',
    },
    {
      id: 'version-2',
      templateId: paymentTemplate.id,
      versionNo: 2,
      status: 'DRAFT' as const,
      schemaVersion: 'payment.v2',
      templateContent: '<p>v2</p>',
      fieldManifest: '["payment.code","payment.amount"]',
      contentHash: 'hash-v2',
    },
  ],
  defaultBinding: {
    templateId: paymentTemplate.id,
    templateVersionId: 'version-1',
    lockVersion: 1,
  },
}
const settlementDetail = {
  template: settlementTemplate,
  versions: [
    {
      id: 'settlement-version-1',
      templateId: settlementTemplate.id,
      versionNo: 1,
      status: 'DRAFT' as const,
      schemaVersion: 'settlement.v1',
      templateContent: '<p>settlement</p>',
      fieldManifest: '["settlement.code"]',
      contentHash: 'settlement-hash',
    },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  useSessionStore().replaceUserInfo({
    userId: '1',
    username: 'admin',
    realName: '管理员',
    tenantId: '1001',
    roles: ['ADMIN'],
    permissions: ['document:template:edit', 'document:template:publish', 'document:generate'],
  })
  vi.mocked(service.loadDocumentTemplates).mockImplementation(async (businessType) =>
    businessType === 'SETTLEMENT' ? [settlementTemplate] : [paymentTemplate],
  )
  vi.mocked(service.loadDocumentTemplate).mockImplementation(async (id) =>
    id === settlementTemplate.id ? settlementDetail : paymentDetail,
  )
  vi.mocked(service.loadDocumentBusinessTypes).mockResolvedValue([
    {
      businessType: 'PAYMENT',
      displayName: '付款申请单',
      schemaVersion: 'payment.v2',
      providerReady: true,
      fieldCount: 2,
    },
    {
      businessType: 'SETTLEMENT',
      displayName: '结算单',
      schemaVersion: 'settlement.v1',
      providerReady: true,
      fieldCount: 1,
    },
    {
      businessType: 'COST_SUBJECT_MAPPING',
      displayName: '成本科目映射',
      schemaVersion: 'cost-subject-mapping.v1',
      providerReady: true,
      fieldCount: 4,
    },
  ])
  vi.mocked(service.loadDocumentFieldCatalog).mockImplementation(async (businessType) => ({
    businessType,
    schemaVersion: businessType === 'SETTLEMENT' ? 'settlement.v1' : 'payment.v2',
    fields: [
      {
        path: businessType === 'SETTLEMENT' ? 'settlement.code' : 'payment.code',
        label: '单据编号',
        valueType: 'TEXT',
        nullable: false,
        group: '基本信息',
        collectionPath: null,
        masked: false,
      },
    ],
  }))
  vi.mocked(service.previewDocumentTemplateHtml).mockResolvedValue({ html: '<p>preview</p>' })
  vi.mocked(service.previewDocumentTemplateVersionHtml).mockImplementation(async (versionId) => ({
    html: `<p>rendered-${versionId}</p>`,
  }))
  vi.mocked(service.deleteDocumentTemplate).mockResolvedValue()
})

describe('M7 document template page', () => {
  it('loads server-rendered HTML and changes versions without reloading template details', async () => {
    const wrapper = mount(DocumentTemplatePage)
    await flushPromises()

    expect(wrapper.findAll('.document-template-page__columns > .v2-card')).toHaveLength(3)
    expect(
      wrapper.findAll('.document-template-page__columns > .v2-card h2').map((item) => item.text()),
    ).toEqual(['业务模块', '模板', 'HTML预览'])
    expect(
      wrapper
        .findAll('.document-template-page__business-group-heading h3')
        .map((item) => item.text()),
    ).toEqual(['资金财务', '分包结算'])
    expect(
      wrapper
        .findAll('.document-template-page__business-group-heading span')
        .map((item) => item.text()),
    ).toEqual(['1', '1'])
    expect(wrapper.text()).not.toContain('基础资料')
    expect(wrapper.text()).not.toContain('成本科目映射')
    expect(service.loadDocumentTemplates).toHaveBeenCalledWith('PAYMENT', expect.any(AbortSignal))
    expect(service.loadDocumentFieldCatalog).toHaveBeenCalledWith(
      'PAYMENT',
      expect.any(AbortSignal),
    )
    expect(service.loadDocumentTemplate).toHaveBeenCalledWith(paymentTemplate.id)
    expect(wrapper.get('button[aria-pressed="true"]').text()).toContain('付款申请单')
    expect(wrapper.text()).not.toContain('PAYMENT-001')
    expect(wrapper.text()).toContain('hash-v1')
    expect(wrapper.get('iframe[title="选中模板版本 HTML 预览"]').attributes('srcdoc')).toContain(
      '<p>rendered-version-1</p>',
    )
    expect(service.previewDocumentTemplateVersionHtml).toHaveBeenCalledWith('version-1')

    const requestCount = vi.mocked(service.loadDocumentTemplate).mock.calls.length
    await wrapper
      .get('.document-template-page__version-button[aria-pressed="false"]')
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('hash-v2')
    expect(wrapper.text()).toContain('payment.amount')
    expect(service.loadDocumentTemplate).toHaveBeenCalledTimes(requestCount)
    expect(service.previewDocumentTemplateVersionHtml).toHaveBeenLastCalledWith('version-2')
    expect(wrapper.get('iframe[title="选中模板版本 HTML 预览"]').attributes('srcdoc')).toContain(
      '<p>rendered-version-2</p>',
    )
    wrapper.unmount()
  })

  it('keeps the newest version preview when requests finish out of order', async () => {
    let resolveV1!: (value: { html: string }) => void
    let resolveV2!: (value: { html: string }) => void
    vi.mocked(service.previewDocumentTemplateVersionHtml).mockImplementation(
      (versionId) =>
        new Promise((resolve) => {
          if (versionId === 'version-1') resolveV1 = resolve
          else resolveV2 = resolve
        }),
    )
    const wrapper = mount(DocumentTemplatePage)
    await flushPromises()
    await wrapper
      .get('.document-template-page__version-button[aria-pressed="false"]')
      .trigger('click')
    resolveV2({ html: '<p>newest</p>' })
    await flushPromises()
    resolveV1({ html: '<p>stale</p>' })
    await flushPromises()

    expect(wrapper.get('iframe[title="选中模板版本 HTML 预览"]').attributes('srcdoc')).toBe(
      '<p>newest</p>',
    )
    wrapper.unmount()
  })

  it('does not request or expose template source without preview permissions', async () => {
    useSessionStore().replaceUserInfo({
      userId: '2',
      username: 'reader',
      realName: '只读用户',
      tenantId: '1001',
      roles: [],
      permissions: ['document:template:query'],
    })
    const wrapper = mount(DocumentTemplatePage)
    await flushPromises()

    expect(wrapper.text()).toContain('无 HTML 预览权限')
    expect(service.previewDocumentTemplateVersionHtml).not.toHaveBeenCalled()
    expect(wrapper.find('iframe[title="选中模板版本 HTML 预览"]').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('&lt;p&gt;v1&lt;/p&gt;')
    wrapper.unmount()
  })

  it('allows an admin role to use server preview without explicit permission codes', async () => {
    useSessionStore().replaceUserInfo({
      userId: '1',
      username: 'admin',
      realName: '管理员',
      tenantId: '1001',
      roles: ['ADMIN'],
      permissions: [],
    })
    const wrapper = mount(DocumentTemplatePage)
    await flushPromises()

    expect(service.previewDocumentTemplateVersionHtml).toHaveBeenCalledWith('version-1')
    expect(wrapper.get('iframe[title="选中模板版本 HTML 预览"]').attributes('srcdoc')).toContain(
      '<p>rendered-version-1</p>',
    )
    wrapper.unmount()
  })

  it('shows edit and delete entries and opens the editor full screen', async () => {
    const host = document.createElement('div')
    document.body.append(host)
    const wrapper = mount(DocumentTemplatePage, { attachTo: host })
    await flushPromises()

    expect(wrapper.findAll('button').some((item) => item.text() === '编辑模板')).toBe(true)
    expect(wrapper.findAll('button').some((item) => item.text() === '删除模板')).toBe(true)
    await wrapper
      .findAll('button')
      .find((item) => item.text() === '编辑模板')!
      .trigger('click')
    await flushPromises()
    expect(document.querySelector('.v2-dialog__panel--fullscreen')).not.toBeNull()

    ;(document.querySelector<HTMLButtonElement>('.v2-dialog__close') as HTMLButtonElement).click()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((item) => item.text() === '删除模板')!
      .trigger('click')
    await flushPromises()
    ;[...document.querySelectorAll<HTMLButtonElement>('.v2-dialog__footer button')]
      .find((item) => item.textContent?.trim() === '删除')!
      .click()
    await flushPromises()
    expect(service.deleteDocumentTemplate).toHaveBeenCalledWith(paymentTemplate.id)

    wrapper.unmount()
    host.remove()
  })

  it('offers enable after a version is disabled', async () => {
    vi.mocked(service.loadDocumentTemplate).mockResolvedValue({
      ...paymentDetail,
      versions: [{ ...paymentDetail.versions[0]!, status: 'DISABLED' }],
    })
    const wrapper = mount(DocumentTemplatePage)
    await flushPromises()

    expect(wrapper.findAll('button').some((item) => item.text() === '启用')).toBe(true)
    wrapper.unmount()
  })

  it('imports a historical version into the new-template canvas', async () => {
    const host = document.createElement('div')
    document.body.append(host)
    const wrapper = mount(DocumentTemplatePage, { attachTo: host })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '新增模板')!
      .trigger('click')
    await flushPromises()
    const importButton = [
      ...document.querySelectorAll<HTMLButtonElement>('.v2-dialog__body button'),
    ].find((item) => item.textContent?.includes('导入 V1'))!
    importButton.click()
    await flushPromises()

    expect(document.querySelector('.document-canvas__element')?.textContent).toContain(
      '{{payment.code}}',
    )
    wrapper.unmount()
    host.remove()
  })

  it('opens a published legacy template as a canvas draft', async () => {
    vi.mocked(service.loadDocumentTemplate).mockResolvedValue({
      ...paymentDetail,
      versions: [
        {
          ...paymentDetail.versions[0]!,
          designSchema: undefined,
          templateContent: '<style>@page{size:A4 landscape}</style>',
        },
      ],
    })
    const host = document.createElement('div')
    document.body.append(host)
    const wrapper = mount(DocumentTemplatePage, { attachTo: host })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '编辑模板')!
      .trigger('click')
    await flushPromises()

    expect(document.querySelector('.document-canvas')).not.toBeNull()
    expect(document.querySelector('.document-template-page__textarea')).toBeNull()
    expect(document.querySelector('.document-template-page__conversion-warning')).toBeNull()
    expect(document.querySelector('.document-canvas__element code')?.textContent).toContain(
      '{{payment.code}}',
    )
    expect(document.querySelector('[data-testid="orientation-toggle"]')?.textContent).toContain(
      '横向 A4',
    )
    wrapper.unmount()
    host.remove()
  })

  it('upgrades an old draft to the current field-catalog schema before editing', async () => {
    vi.mocked(service.loadDocumentTemplate).mockResolvedValue({
      ...paymentDetail,
      versions: [
        {
          ...paymentDetail.versions[1]!,
          schemaVersion: 'payment.v1',
          designSchema: undefined,
          fieldManifest: '["payment.code"]',
        },
      ],
    })
    const host = document.createElement('div')
    document.body.append(host)
    const wrapper = mount(DocumentTemplatePage, { attachTo: host })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '编辑模板')!
      .trigger('click')
    await flushPromises()

    expect(document.querySelector<HTMLInputElement>('input[aria-label="契约版本"]')?.value).toBe(
      'payment.v2',
    )
    wrapper.unmount()
    host.remove()
  })

  it('keeps an unsupported legacy field as an editable placeholder without blocking save', async () => {
    vi.mocked(service.loadDocumentTemplate).mockResolvedValue({
      ...paymentDetail,
      versions: [
        {
          ...paymentDetail.versions[0]!,
          designSchema: undefined,
          fieldManifest: '["payment.code","retired.value"]',
        },
      ],
    })
    const host = document.createElement('div')
    document.body.append(host)
    const wrapper = mount(DocumentTemplatePage, { attachTo: host })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '编辑模板')!
      .trigger('click')
    await flushPromises()

    expect(
      document.querySelector('.document-template-page__conversion-warning')?.textContent,
    ).toContain('retired.value')
    expect(
      [...document.querySelectorAll('.document-canvas__element')].some((element) =>
        element.textContent?.includes('retired.value：________'),
      ),
    ).toBe(true)
    expect(
      document.querySelector<HTMLButtonElement>('.v2-dialog__footer button:last-child')?.disabled,
    ).toBe(false)
    wrapper.unmount()
    host.remove()
  })

  it('changes business type once and selects its first template and version', async () => {
    const wrapper = mount(DocumentTemplatePage)
    await flushPromises()

    const settlement = wrapper
      .findAll('.document-template-page__business-option')
      .find((item) => item.text().includes('结算单'))!
    await settlement.trigger('click')
    await flushPromises()

    expect(service.loadDocumentTemplates).toHaveBeenLastCalledWith(
      'SETTLEMENT',
      expect.any(AbortSignal),
    )
    expect(service.loadDocumentTemplate).toHaveBeenLastCalledWith(settlementTemplate.id)
    expect(wrapper.text()).toContain('settlement-hash')
    const selectedSettlement = wrapper
      .findAll('.document-template-page__business-option')
      .find((item) => item.text().includes('结算单'))!
    expect(selectedSettlement.attributes('aria-pressed')).toBe('true')

    const requestCount = vi.mocked(service.loadDocumentTemplates).mock.calls.length
    await selectedSettlement.trigger('click')
    expect(service.loadDocumentTemplates).toHaveBeenCalledTimes(requestCount)
    wrapper.unmount()
  })

  it('submits design schema while leaving HTML and field manifest server-owned', async () => {
    vi.mocked(service.createDocumentTemplate).mockResolvedValue({
      ...paymentDetail.versions[1]!,
      designSchema: '{}',
    })
    const host = document.createElement('div')
    document.body.append(host)
    const wrapper = mount(DocumentTemplatePage, { attachTo: host })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '新增模板')!
      .trigger('click')
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 300))
    expect(service.previewDocumentTemplateHtml).not.toHaveBeenCalled()
    const inputs = [...document.querySelectorAll<HTMLInputElement>('.v2-dialog__panel input')]
    expect(inputs.find((item) => item.getAttribute('aria-label') === '模板编码')?.disabled).toBe(
      true,
    )
    inputs.find((item) => item.getAttribute('aria-label') === '模板名称')!.value = '付款画布模板'
    inputs
      .find((item) => item.getAttribute('aria-label') === '模板名称')!
      .dispatchEvent(new Event('input'))
    ;[...document.querySelectorAll<HTMLButtonElement>('.document-canvas__field')]
      .find((item) => item.textContent?.includes('单据编号'))!
      .click()
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 300))
    expect(service.previewDocumentTemplateHtml).toHaveBeenCalledTimes(1)
    ;[...document.querySelectorAll<HTMLButtonElement>('.v2-dialog__footer button')]
      .find((item) => item.textContent?.includes('保存草稿'))!
      .click()
    await flushPromises()

    expect(service.createDocumentTemplate).toHaveBeenCalledWith(
      expect.objectContaining({
        templateName: '付款画布模板',
        businessType: 'PAYMENT',
        designSchema: expect.stringContaining('"fieldPath":"payment.code"'),
      }),
    )
    const command = vi.mocked(service.createDocumentTemplate).mock.calls[0]![0]
    expect(command.templateContent).toBeUndefined()
    expect(command.fieldManifest).toBeUndefined()
    wrapper.unmount()
    host.remove()
  })
})
