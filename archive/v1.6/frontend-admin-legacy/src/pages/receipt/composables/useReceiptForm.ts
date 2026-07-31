import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  createReceipt,
  updateReceipt,
  getReceiptItems,
  saveReceiptItems,
  getOrderItemsForReceipt,
} from '@/api/modules/receipt'
import type { MatReceiptVO, MatReceiptItemVO } from '@/types/receipt'
import type { MatPurchaseOrderVO } from '@/types/purchase'
import { uploadFile } from '@/api/modules/file'

export function useReceiptForm(
  fetchData: () => Promise<void>,
  orderList: ReturnType<typeof ref<MatPurchaseOrderVO[]>>,
) {
  const modalVisible = ref(false)
  const modalTitle = ref('新建材料验收')
  const editingId = ref<string | null>(null)
  const proofFile = ref<File | null>(null)
  const formData = reactive<Partial<MatReceiptVO>>({
    projectId: undefined,
    orderId: undefined,
    contractId: undefined,
    partnerId: undefined,
    receiptDate: undefined,
    qualityStatus: undefined,
    warehouseId: undefined,
    receiverId: undefined,
    remark: '',
  })

  // Line items for the modal
  const itemList = ref<(Partial<MatReceiptItemVO> & { key: number; warning?: boolean })[]>([])
  let itemKeyCounter = 0

  function handleAdd() {
    modalTitle.value = '新建材料验收'
    editingId.value = null
    Object.assign(formData, {
      projectId: undefined,
      orderId: undefined,
      contractId: undefined,
      partnerId: undefined,
      receiptDate: undefined,
      qualityStatus: undefined,
      warehouseId: undefined,
      receiverId: undefined,
      remark: '',
    })
    itemList.value = []
    itemKeyCounter = 0
    proofFile.value = null
    modalVisible.value = true
  }

  async function handleEdit(record: MatReceiptVO) {
    modalTitle.value = '编辑材料验收'
    editingId.value = record.id
    Object.assign(formData, {
      projectId: record.projectId,
      orderId: record.orderId,
      contractId: record.contractId,
      partnerId: record.partnerId,
      receiptDate: record.receiptDate,
      qualityStatus: record.qualityStatus,
      warehouseId: record.warehouseId,
      receiverId: record.receiverId,
      remark: record.remark,
    })
    itemList.value = []
    itemKeyCounter = 0
    proofFile.value = null
    // Load existing items
    try {
      const items = await getReceiptItems(record.id)
      itemList.value = items.map((item) => ({
        ...item,
        key: itemKeyCounter++,
      }))
    } catch (e: unknown) {
      console.error(e)
      message.error('加载验收明细失败，请稍后重试')
      itemList.value = []
    }
    modalVisible.value = true
  }

  // --- Order selection → load order items for receipt ---
  async function handleOrderChange(orderId: string | undefined) {
    itemList.value = []
    itemKeyCounter = 0
    if (!orderId) {
      formData.contractId = undefined
      formData.partnerId = undefined
      return
    }
    // Auto-fill contract and partner from selected order
    const order = orderList.value.find((o) => o.id === orderId)
    if (order) {
      formData.contractId = order.contractId
      formData.partnerId = order.partnerId
    }
    // Load order items for receipt selection
    try {
      const items = await getOrderItemsForReceipt(orderId)
      itemList.value = items.map((item) => ({
        ...item,
        key: itemKeyCounter++,
      }))
    } catch (e: unknown) {
      console.error(e)
      message.error('加载采购订单明细失败，请稍后重试')
      itemList.value = []
    }
  }

  // --- Line items management ---
  function handleItemQtyChange(index: number) {
    const item = itemList.value[index]
    const qty = parseFloat(item.actualQuantity || '0')
    const price = parseFloat(item.unitPrice || '0')
    item.amount = (qty * price).toFixed(2)

    // Quantity validation (W0 Decision 3: warn only)
    const remaining = parseFloat(item.remainingQuantity || '0')
    if (qty > remaining) {
      item.warning = true
    } else {
      item.warning = false
    }
  }

  function handleItemPriceChange(index: number) {
    const item = itemList.value[index]
    const qty = parseFloat(item.actualQuantity || '0')
    const price = parseFloat(item.unitPrice || '0')
    item.amount = (qty * price).toFixed(2)
  }

  function handleItemQualifiedQtyChange(index: number) {
    const item = itemList.value[index]
    const qualified = parseFloat(item.qualifiedQuantity || '0')
    const actual = parseFloat(item.actualQuantity || '0')
    if (qualified > actual) {
      message.warning('合格数量不能超过实际到货数量')
      item.qualifiedQuantity = item.actualQuantity
    }
    item.unqualifiedQuantity = Math.max(0, actual - Number(item.qualifiedQuantity || 0)).toFixed(2)
    if (Number(item.unqualifiedQuantity) === 0) {
      item.dispositionType = undefined
      item.dispositionReason = undefined
    }
  }

  const itemsTotalAmount = computed(() => {
    let total = 0
    for (const item of itemList.value) {
      total += parseFloat(item.amount || '0')
    }
    return total.toFixed(2)
  })

  // Check if any item has warning
  const hasWarning = computed(() => {
    return itemList.value.some((item) => item.warning)
  })

  async function handleModalOk() {
    if (!formData.projectId) {
      message.warning('请选择项目')
      return
    }
    if (!formData.orderId || !formData.contractId || !formData.partnerId) {
      message.warning('请选择有效采购订单，系统将自动关联合同和供应商')
      return
    }
    if (!formData.receiptDate || !formData.qualityStatus) {
      message.warning('请填写验收日期和质量状态')
      return
    }
    if (!editingId.value && !proofFile.value) {
      message.warning('请上传验收记录或质量证明附件')
      return
    }
    if (!itemList.value.some((item) => Number(item.actualQuantity) > 0)) {
      message.warning('至少一条明细的本次到货数量必须大于 0')
      return
    }
    for (const item of itemList.value) {
      const actual = Number(item.actualQuantity || 0)
      const qualified = Number(item.qualifiedQuantity || 0)
      item.unqualifiedQuantity = Math.max(0, actual - qualified).toFixed(2)
      if (
        Number(item.unqualifiedQuantity) > 0 &&
        (!item.dispositionType || !item.dispositionReason?.trim())
      ) {
        message.warning('不合格数量必须选择处置方式并填写原因')
        return
      }
    }

    // Show warning but don't block (W0 Decision 3)
    if (hasWarning.value) {
      message.warning('部分验收数量超过采购订单剩余数量，请注意核对')
    }

    try {
      let receiptId: string
      if (editingId.value) {
        await updateReceipt(editingId.value, formData)
        receiptId = editingId.value
        message.success('更新成功')
      } else {
        const result = await createReceipt(formData)
        receiptId = result
        message.success('保存成功')
      }

      // Save line items
      if (itemList.value.length > 0) {
        const items = itemList.value.map((item) => ({
          ...item,
          receiptId: receiptId,
          warning: undefined,
        }))
        await saveReceiptItems(receiptId, items)
      }
      if (proofFile.value) {
        await uploadFile(proofFile.value, 'MATERIAL_RECEIPT', receiptId, 'RECEIPT_PROOF')
      }

      modalVisible.value = false
      fetchData()
    } catch (e: unknown) {
      console.error(e)
      message.error('操作失败，请稍后重试')
    }
  }

  function handleModalCancel() {
    modalVisible.value = false
  }

  return {
    modalVisible,
    modalTitle,
    editingId,
    proofFile,
    formData,
    itemList,
    handleAdd,
    handleEdit,
    handleOrderChange,
    handleItemQtyChange,
    handleItemPriceChange,
    handleItemQualifiedQtyChange,
    itemsTotalAmount,
    hasWarning,
    handleModalOk,
    handleModalCancel,
  }
}
