import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DocumentFlowDesigner from '@/components/document/DocumentFlowDesigner.vue'
import DocumentTemplatePage from '@/pages/system/DocumentTemplatePage.vue'
import DocumentTemplateDesignerPage from '@/pages/system/DocumentTemplateDesignerPage.vue'
import * as service from '@/services/system-management'

const route = {
  query: {} as Record<string, string>,
  params: {} as Record<string, string>,
}
const router = { replace: vi.fn(), push: vi.fn() }

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => route,
    useRouter: () => router,
    onBeforeRouteLeave: vi.fn(),
  }
})

vi.mock('@/stores/session', () => ({
  useSessionStore: () => ({ hasAdminOrPermission: () => true }),
}))

vi.mock('@/services/system-management', async () => {
  const actual = await vi.importActual<typeof import('@/services/system-management')>(
    '@/services/system-management',
  )
  return {
    ...actual,
    loadDocumentBusinessTypes: vi.fn(),
    loadDocumentTemplates: vi.fn(),
    loadSystemDocumentTemplateStatuses: vi.fn(),
    loadDocumentTemplate: vi.fn(),
    previewDocumentTemplateVersionHtml: vi.fn(),
    installSystemDocumentTemplate: vi.fn(),
    installAllSystemDocumentTemplates: vi.fn(),
    loadDocumentFieldCatalog: vi.fn(),
    previewDocumentTemplateHtml: vi.fn(),
    createDocumentTemplate: vi.fn(),
    createDocumentVersion: vi.fn(),
    updateDocumentVersion: vi.fn(),
  }
})

const types = [
  {
    businessType: 'PAYMENT',
    displayName: '付款申请',
    schemaVersion: 'payment.v2',
    providerReady: true,
    fieldCount: 2,
  },
  {
    businessType: 'SETTLEMENT',
    displayName: '工程结算',
    schemaVersion: 'settlement.v2',
    providerReady: true,
    fieldCount: 2,
  },
]
const templates = [
  {
    id: 't1',
    templateCode: 'SYSTEM_PAYMENT_APPLICATION_V1',
    templateName: '付款申请单',
    businessType: 'PAYMENT',
    enabled: 1,
  },
  {
    id: 't2',
    templateCode: 'SYSTEM_SETTLEMENT_V1',
    templateName: '工程结算单',
    businessType: 'SETTLEMENT',
    enabled: 1,
  },
]
const versions = {
  t1: {
    template: templates[0],
    versions: [
      {
        id: 'v1',
        templateId: 't1',
        versionNo: 1,
        status: 'PUBLISHED',
        schemaVersion: 'payment.v2',
        templateContent: '',
        fieldManifest: '[]',
        contentHash: 'a',
      },
    ],
  },
  t2: {
    template: templates[1],
    versions: [
      {
        id: 'v2',
        templateId: 't2',
        versionNo: 2,
        status: 'DRAFT',
        schemaVersion: 'settlement.v2',
        templateContent: '',
        fieldManifest: '[]',
        contentHash: 'b',
      },
    ],
  },
}

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.removeItem('document-template-collapsed-modules')
  route.query = {}
  route.params = {}
  vi.mocked(service.loadDocumentBusinessTypes).mockResolvedValue(types)
  vi.mocked(service.loadDocumentTemplates).mockResolvedValue(templates)
  vi.mocked(service.loadSystemDocumentTemplateStatuses).mockResolvedValue(
    types.map((item, index) => ({
      businessType: item.businessType,
      templateCode: templates[index]!.templateCode,
      templateName: templates[index]!.templateName,
      schemaVersion: item.schemaVersion,
      orientation: 'PORTRAIT',
      templateId: templates[index]!.id,
      versionId: index ? 'v2' : 'v1',
      installed: true,
      current: true,
      defaultBinding: 'SYSTEM',
    })),
  )
  vi.mocked(service.loadDocumentTemplate).mockImplementation(
    async (id) => versions[id as keyof typeof versions] as never,
  )
  vi.mocked(service.previewDocumentTemplateVersionHtml).mockResolvedValue({
    html: '<html>preview</html>',
  })
  vi.mocked(service.loadDocumentFieldCatalog).mockImplementation(async (businessType) => ({
    businessType,
    schemaVersion: businessType === 'PAYMENT' ? 'payment.v2' : 'settlement.v2',
    fields: [
      {
        path: 'document.code',
        label: '单据编号',
        valueType: 'TEXT',
        nullable: false,
        masked: false,
      },
      {
        path: 'items.name',
        label: '明细名称',
        valueType: 'TEXT',
        nullable: false,
        masked: false,
        collectionPath: 'items',
      },
    ],
  }))
  vi.mocked(service.previewDocumentTemplateHtml).mockResolvedValue({ html: '<html>draft</html>' })
})

describe('DocumentTemplatePage', () => {
  it('restores URL selection and exposes searchable two-pane workbench', async () => {
    route.query = { businessType: 'SETTLEMENT', templateId: 't2', versionId: 'v2' }
    const wrapper = mount(DocumentTemplatePage)
    await flushPromises()

    expect(service.loadDocumentTemplates).toHaveBeenCalledWith('')
    expect(service.loadDocumentTemplate).toHaveBeenCalledWith('t2')
    expect(service.previewDocumentTemplateVersionHtml).toHaveBeenCalledWith('v2')
    expect(wrapper.find('.template-workbench').exists()).toBe(true)
    expect(wrapper.find('.preview-stage iframe').attributes('srcdoc')).toContain('preview')
    expect(wrapper.find('.preview-stage iframe').attributes('srcdoc')).toContain(
      'data-document-screen-preview',
    )
    expect(wrapper.find('.preview-stage iframe').attributes('sandbox')).toBe('')

    const moduleGroups = wrapper.findAll('details.module-group')
    expect(moduleGroups.length).toBeGreaterThan(0)
    expect(moduleGroups.every((group) => group.attributes('open') !== undefined)).toBe(true)
    await moduleGroups[0]!.get('summary').trigger('click')
    expect((moduleGroups[0]!.element as HTMLDetailsElement).open).toBe(false)
    await moduleGroups[0]!.trigger('toggle')
    expect(sessionStorage.getItem('document-template-collapsed-modules')).toBe('["finance"]')

    wrapper.unmount()
    const restored = mount(DocumentTemplatePage)
    await flushPromises()
    expect((restored.findAll('details.module-group')[0]!.element as HTMLDetailsElement).open).toBe(
      false,
    )

    await restored.find('.search-box input').setValue('SYSTEM_PAYMENT')
    expect(restored.find('.template-nav').text()).toContain('付款申请单')
    expect(restored.find('.template-nav').text()).not.toContain('工程结算单')
  })

  it('opens dedicated designer routes and installs all explicitly', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(service.installAllSystemDocumentTemplates).mockResolvedValue(
      types.map((item, index) => ({
        businessType: item.businessType,
        templateId: templates[index]!.id,
        versionId: index ? 'v2' : 'v1',
        action: 'UNCHANGED',
        bindingAction: 'UPDATED_SYSTEM',
      })),
    )
    const wrapper = mount(DocumentTemplatePage)
    await flushPromises()

    await wrapper.get('.v2-card__actions button:last-child').trigger('click')
    expect(router.push).toHaveBeenCalledWith({
      path: '/system/document-templates/new',
      query: { businessType: 'PAYMENT' },
    })

    const installAll = wrapper
      .findAll('button')
      .find((button) => button.text().includes('安装全部'))!
    await installAll.trigger('click')
    wrapper.findComponent({ name: 'V2ConfirmDialog' }).vm.$emit('confirm')
    await flushPromises()
    expect(service.installAllSystemDocumentTemplates).toHaveBeenCalledTimes(1)
  })
})

describe('DocumentFlowDesigner', () => {
  it('selects the exact preview section before deleting it and removes screen-only page reserves', async () => {
    const design = {
      layoutVersion: 2 as const,
      schemaVersion: 'measurement.v1',
      page: {
        size: 'A4' as const,
        orientation: 'LANDSCAPE' as const,
        marginMm: { top: 12, right: 12, bottom: 12, left: 12 },
      },
      elements: [],
      tables: [],
      sections: [
        {
          id: 'signature-1',
          type: 'SIGNATURE_GRID' as const,
          title: '签认栏',
          labels: ['编制', '复核'],
        },
        {
          id: 'signature-2',
          type: 'SIGNATURE_GRID' as const,
          title: '签认栏',
          labels: ['审批', '日期'],
        },
      ],
    }
    const wrapper = mount(DocumentFlowDesigner, {
      props: {
        modelValue: design,
        fields: [],
        previewHtml:
          '<html><head></head><body><div class="page-header"></div><div class="page-footer"></div><main><section class="flow-section"><h2>签认栏</h2></section><section class="flow-section"><h2>签认栏</h2></section></main></body></html>',
      },
    })
    const iframe = wrapper.get('iframe')
    expect(iframe.attributes('sandbox')).toBe('allow-same-origin')
    expect(iframe.attributes('srcdoc')).toContain('data-document-screen-preview')
    expect(iframe.attributes('srcdoc')).toContain('height:0!important')

    const previewDocument = window.document.implementation.createHTMLDocument('preview')
    Object.defineProperty(iframe.element, 'contentDocument', { value: previewDocument })
    previewDocument.body.innerHTML =
      '<main><section class="flow-section"><h2>签认栏</h2></section><section class="flow-section"><h2>签认栏</h2></section></main>'
    await iframe.trigger('load')
    const previewSections = previewDocument.querySelectorAll<HTMLElement>('.flow-section')
    expect(previewSections[1]!.dataset.editorSectionId).toBe('signature-2')
    previewSections[1]!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(wrapper.text()).toContain('已选第 2 个区块')

    await wrapper.get('button[aria-label="删除第 2 个区块：签认栏"]').trigger('click')
    const updates = wrapper.emitted('update:modelValue')!
    expect(updates.at(-1)![0]).toMatchObject({
      sections: [{ id: 'signature-1' }],
    })
  })
})

describe('DocumentTemplateDesignerPage', () => {
  it('creates v2 draft and returns with persisted selection', async () => {
    route.query = { businessType: 'PAYMENT' }
    vi.mocked(service.createDocumentTemplate).mockResolvedValue({
      id: 'draft-1',
      templateId: 'new-template',
      versionNo: 1,
      status: 'DRAFT',
      schemaVersion: 'payment.v2',
      templateContent: '',
      fieldManifest: '[]',
      contentHash: 'c',
    })
    const wrapper = mount(DocumentTemplateDesignerPage, {
      global: {
        stubs: {
          DocumentFlowDesigner: {
            props: ['modelValue'],
            emits: ['update:modelValue', 'update:valid'],
            template: '<div class="flow-designer-stub">流式设计器</div>',
          },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('新建业务单据模板')
    expect(wrapper.text()).toContain('流式设计器')
    const save = wrapper.findAll('button').find((button) => button.text().includes('保存'))!
    await save.trigger('click')
    await flushPromises()

    const command = vi.mocked(service.createDocumentTemplate).mock.calls[0]![0]
    expect(JSON.parse(command.designSchema!)).toMatchObject({
      layoutVersion: 2,
      sections: expect.any(Array),
    })
    expect(router.replace).toHaveBeenCalledWith({
      path: '/system/document-templates',
      query: { businessType: 'PAYMENT', templateId: 'new-template', versionId: 'draft-1' },
    })
  })

  it('loads edit mode and keeps published versions immutable', async () => {
    route.params = { templateId: 't1', versionId: 'v1' }
    vi.mocked(service.loadDocumentTemplate).mockResolvedValue(versions.t1 as never)
    const wrapper = mount(DocumentTemplateDesignerPage, {
      global: { stubs: { DocumentFlowDesigner: { template: '<div>designer</div>' } } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('已发布版本不可修改')
    const save = wrapper.findAll('button').find((button) => button.text().includes('保存'))!
    expect(save.attributes('disabled')).toBeDefined()
  })
})
