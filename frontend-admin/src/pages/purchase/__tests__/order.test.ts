import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PurchaseOrderPage from '../order.vue'

const mocks = vi.hoisted(() => ({
  getOrderList: vi.fn(),
  routerReplace: vi.fn(),
  route: {
    path: '/purchase/order',
    query: {} as Record<string, string>,
  },
  fetchPartners: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.routerReplace }),
}))

vi.mock('@/api/modules/purchase', () => ({
  getOrderList: mocks.getOrderList,
  getOrderDetail: vi.fn(),
  createOrder: vi.fn(),
  updateOrder: vi.fn(),
  deleteOrder: vi.fn(),
  getOrderItems: vi.fn(),
  saveOrderItems: vi.fn(),
  submitOrderForApproval: vi.fn(),
}))

vi.mock('@/stores/reference', () => ({
  useReferenceStore: () => ({
    projects: [],
    contracts: [],
    materials: [],
    fetchProjects: vi.fn(),
    fetchContracts: vi.fn(),
    fetchMaterials: vi.fn(),
    fetchPartners: mocks.fetchPartners,
  }),
}))

vi.mock('@/utils/dict', () => ({
  fetchDictData: vi.fn(),
  getDictLabelSync: (_dict: string, value: string, fallback: Record<string, string>) =>
    fallback[value] ?? value,
  getDictTagColorSync: (_dict: string, value: string, fallback: Record<string, string>) =>
    fallback[value] ?? 'default',
}))

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../order.vue'), 'utf-8')
const searchBarSource = readFileSync(
  resolve(currentDir, '../components/PurchaseOrderSearchBar.vue'),
  'utf-8',
)
const analysisRailSource = readFileSync(
  resolve(currentDir, '../components/PurchaseOrderAnalysisRail.vue'),
  'utf-8',
)
const modalSource = readFileSync(
  resolve(currentDir, '../components/PurchaseOrderModal.vue'),
  'utf-8',
)

describe('PurchaseOrderPage submit-approval button', () => {
  it('imports submitOrderForApproval from API module', () => {
    expect(source).toMatch(/import\s+\{[^}]*submitOrderForApproval[^}]*\}\s+from/)
  })

  it('has handleSubmitApproval function with Modal.confirm', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?Modal\.confirm\(/)
  })

  it('calls submitOrderForApproval inside handleSubmitApproval onOk', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?submitOrderForApproval\(/)
  })

  it('calls fetchData after successful submit', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?fetchData\(/)
  })

  it('renders 提交审批 button only when approvalStatus is DRAFT', () => {
    expect(source).toMatch(/approvalStatus\s*===\s*APPROVAL_DRAFT/)
  })

  it('wires 提交审批 button to handleSubmitApproval handler', () => {
    expect(source).toMatch(/handleSubmitApproval\(row\)/)
  })

  it('maps contract purchase order type to Chinese display text', () => {
    expect(source).toMatch(/PURCHASE:\s*'采购订单'/)
    expect(source).toMatch(/CONTRACT:\s*'合同采购'/)
    expect(source).toMatch(/ORDER_TYPE_LABEL\[row\.orderType\]\s*\?\?\s*row\.orderType/)
    expect(source).toMatch(/label:\s*ORDER_TYPE_LABEL\[key\]\s*\?\?\s*key/)
    expect(modalSource).toContain('<a-select-option value="PURCHASE">采购订单</a-select-option>')
  })

  it('loads only approved performing purchase contracts on mount', () => {
    expect(source).toContain("contractType: 'PURCHASE'")
    expect(source).toContain("contractStatus: 'PERFORMING'")
    expect(source).toContain("approvalStatus: 'APPROVED'")
  })

  it('reloads project contracts with approved performing purchase filters', () => {
    expect(source).toContain('projectId: v')
    expect(source).toContain('referenceStore.fetchContracts({')
  })

  it('reloads modal contract options with approved performing purchase filters after project change', () => {
    expect(source).toContain('formData.contractId = undefined')
    expect(source).toContain('formData.partnerId = undefined')
  })

  it('keeps supplier filter when fetching partner options', () => {
    expect(source).toContain('const supplierList = ref<PartnerVO[]>([])')
    expect(source).toContain('async function loadSuppliers()')
    expect(source).toContain(
      "supplierList.value = await referenceStore.fetchPartners({ partnerType: 'SUPPLIER' })",
    )
    expect(source).toMatch(/onMounted\([\s\S]*?loadSuppliers\(\)/)
    expect(searchBarSource).toMatch(/v-for="p in supplierList"/)
    expect(searchBarSource).not.toMatch(/v-for="p in partnerList"/)
  })

  it('prefills a new order delivery date from the selected supplier without overwriting manual input', () => {
    expect(source).toContain("import { suggestSupplierDeliveryDate } from './supplierLeadTime'")
    expect(source).toContain("partner.partnerType === 'SUPPLIER'")
    expect(source).toContain("if (modalMode.value !== 'create') return")
    expect(source).toContain(
      'if (formData.deliveryDate && formData.deliveryDate !== autoDeliveryDate.value) return',
    )
    expect(source).toContain(
      'watch([() => formData.partnerId, () => formData.orderDate], applySupplierDefaultDeliveryDate)',
    )
  })

  it('exposes contractId and partnerId filters in the search bar', () => {
    expect(searchBarSource).toMatch(/v-model:value="filter\.contractId"/)
    expect(searchBarSource).toMatch(/v-model:value="filter\.partnerId"/)
    expect(searchBarSource).toMatch(/placeholder="全部合同"/)
    expect(searchBarSource).toMatch(/placeholder="全部供应商"/)
  })

  it('opens businessId deeplink through order detail API and clears query', () => {
    expect(source).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(source).toContain('getOrderDetail')
    expect(source).toContain('const route = useRoute()')
    expect(source).toContain('const router = useRouter()')
    expect(source).toContain('async function openBusinessIdFromQuery()')
    expect(source).toContain('route.query.businessId')
    expect(source).toContain('await getOrderDetail(String(businessId))')
    expect(source).toContain('await handleView(record)')
    expect(source).toContain('delete nextQuery.businessId')
    expect(source).toContain('await router.replace({ path: route.path, query: nextQuery })')
    expect(source).toMatch(/onMounted\([\s\S]*?openBusinessIdFromQuery\(\)/)
  })

  it('uses explicit view mode so 查看态 cannot save or edit', () => {
    expect(source).toMatch(/type ModalMode = 'create' \| 'edit' \| 'view'/)
    expect(source).toMatch(/const modalMode = ref<ModalMode>\('create'\)/)
    expect(source).toMatch(/const isViewMode = computed\(\(\) => modalMode\.value === 'view'\)/)
    expect(source).toMatch(
      /async function handleView\(record: MatPurchaseOrderVO\)[\s\S]*?modalMode\.value = 'view'/,
    )
    expect(modalSource).toMatch(
      /<a-modal[\s\S]*?:ok-button-props="isViewMode \? \{ style: \{ display: 'none' \} \} : undefined"/,
    )
    expect(modalSource).toMatch(/<a-modal[\s\S]*?:cancel-text="isViewMode \? '关闭' : '取消'"/)
    expect(modalSource).toMatch(/v-if="!isViewMode"[\s\S]*?\+ 添加明细/)
    expect(modalSource).toMatch(
      /<a-table-column title="操作" width="76">[\s\S]*?v-if="!isViewMode"/,
    )
    expect(modalSource).toContain(':disabled="isViewMode"')
    expect(source).toMatch(
      /async function handleModalOk\(\) \{[\s\S]*?if \(isViewMode\.value\) return/,
    )
  })
})

describe('purchase order page quality guardrails', () => {
  it('delegates visual-only sections to local order components while keeping page wiring', () => {
    expect(source).toContain(
      "import PurchaseOrderSearchBar from './components/PurchaseOrderSearchBar.vue'",
    )
    expect(source).toContain(
      "import PurchaseOrderAnalysisRail from './components/PurchaseOrderAnalysisRail.vue'",
    )
    expect(source).toContain("import PurchaseOrderModal from './components/PurchaseOrderModal.vue'")
    expect(source).toMatch(/<PurchaseOrderSearchBar[\s\S]*?class=\"purchase-order-search-bar\"/)
    expect(source).toMatch(
      /<PurchaseOrderAnalysisRail[\s\S]*?class=\"purchase-order-analysis-rail procurement-subcontract-analysis-rail\"/,
    )
    expect(source).toMatch(/<PurchaseOrderModal[\s\S]*?v-model:open=\"modalVisible\"/)
  })

  it('keeps save chain in parent page with create update and saveOrderItems orchestration', () => {
    expect(source).toMatch(
      /if \(editingId\.value\)[\s\S]*?await updateOrder\(editingId\.value, formData\)/,
    )
    expect(source).toMatch(/else \{[\s\S]*?const result = await createOrder\(formData\)/)
    expect(source).toMatch(
      /if \(itemList\.value\.length > 0\)[\s\S]*?await saveOrderItems\(orderId, items\)/,
    )
  })

  it('keeps search and analysis root DOM classes in local components', () => {
    expect(searchBarSource).toContain('class="lg-search-bar procurement-subcontract-query-panel"')
    expect(analysisRailSource).toContain(
      '<aside class="lg-analysis-rail" aria-label="采购订单辅助分析">',
    )
  })

  it('does not open the modal after detail loading fails', () => {
    expect(source).toMatch(/catch[\s\S]*?message\.error\('加载明细失败'\)[\s\S]*?return/)
  })
})

describe('PurchaseOrderPage route filters', () => {
  beforeEach(() => {
    mocks.route.query = { partnerId: 'supplier-1' }
    mocks.getOrderList.mockReset()
    mocks.getOrderList.mockResolvedValue({ records: [], total: 0 })
    mocks.routerReplace.mockReset()
    mocks.routerReplace.mockResolvedValue(undefined)
    mocks.fetchPartners.mockReset()
    mocks.fetchPartners.mockResolvedValue([])
  })

  it('restores partnerId from route query before loading purchase orders', async () => {
    mount(PurchaseOrderPage, {
      global: {
        stubs: {
          PurchaseOrderSearchBar: true,
          PurchaseOrderAnalysisRail: true,
          PurchaseOrderModal: true,
          ColumnSettingsButton: true,
          LgEmptyState: true,
          VxeGrid: true,
          ClockCircleOutlined: true,
          DollarOutlined: true,
          FileDoneOutlined: true,
          MoreOutlined: true,
          PlusOutlined: true,
          ReloadOutlined: true,
          ShoppingCartOutlined: true,
          WalletOutlined: true,
        },
      },
    })
    await flushPromises()

    expect(mocks.getOrderList).toHaveBeenCalledWith(
      expect.objectContaining({ partnerId: 'supplier-1' }),
    )
    expect(mocks.routerReplace).toHaveBeenCalledWith({
      path: '/purchase/order',
      query: expect.objectContaining({ partnerId: 'supplier-1' }),
    })
  })
})
