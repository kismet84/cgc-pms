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
  disableDocumentVersion: vi.fn(),
  loadDocumentBusinessTypes: vi.fn(),
  loadDocumentFieldCatalog: vi.fn(),
  loadDocumentTemplate: vi.fn(),
  loadDocumentTemplates: vi.fn(),
  publishDocumentVersion: vi.fn(),
  previewDocumentTemplateHtml: vi.fn(),
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
    permissions: ['document:template:edit', 'document:template:publish'],
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
})

describe('M7 document template page', () => {
  it('loads three selection columns and changes versions without another request', async () => {
    const wrapper = mount(DocumentTemplatePage)
    await flushPromises()

    expect(wrapper.findAll('.document-template-page__columns > .v2-card')).toHaveLength(3)
    expect(
      wrapper.findAll('.document-template-page__columns > .v2-card h2').map((item) => item.text()),
    ).toEqual(['业务类型', '模板', '详情'])
    expect(service.loadDocumentTemplates).toHaveBeenCalledWith('PAYMENT', expect.any(AbortSignal))
    expect(service.loadDocumentFieldCatalog).toHaveBeenCalledWith(
      'PAYMENT',
      expect.any(AbortSignal),
    )
    expect(service.loadDocumentTemplate).toHaveBeenCalledWith(paymentTemplate.id)
    expect(wrapper.get('button[aria-pressed="true"]').text()).toContain('付款申请单')
    expect(wrapper.text()).toContain('hash-v1')

    const requestCount = vi.mocked(service.loadDocumentTemplate).mock.calls.length
    await wrapper
      .get('.document-template-page__version-button[aria-pressed="false"]')
      .trigger('click')

    expect(wrapper.text()).toContain('hash-v2')
    expect(wrapper.text()).toContain('payment.amount')
    expect(service.loadDocumentTemplate).toHaveBeenCalledTimes(requestCount)
    wrapper.unmount()
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
    inputs.find((item) => item.getAttribute('aria-label') === '模板编码')!.value = 'PAYMENT-CANVAS'
    inputs
      .find((item) => item.getAttribute('aria-label') === '模板编码')!
      .dispatchEvent(new Event('input'))
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
        templateCode: 'PAYMENT-CANVAS',
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
