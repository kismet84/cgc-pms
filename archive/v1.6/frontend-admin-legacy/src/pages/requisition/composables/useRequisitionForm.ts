import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  createRequisition,
  updateRequisition,
  getRequisitionItems,
  saveRequisitionItems,
} from '@/api/modules/requisition'
import type { MatRequisitionVO, MatRequisitionItemVO } from '@/types/requisition'

export function useRequisitionForm(fetchData: () => Promise<void>) {
  const modalVisible = ref(false)
  const modalTitle = ref('新增领料申请')
  const editingId = ref<string | null>(null)
  const formData = reactive<Partial<MatRequisitionVO>>({
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    warehouseId: undefined,
    requisitionerId: undefined,
    requisitionDate: undefined,
    remark: '',
  })

  // Line items for the modal
  const itemList = ref<(Partial<MatRequisitionItemVO> & { key: number })[]>([])
  let itemKeyCounter = 0

  function handleAdd() {
    modalTitle.value = '新增领料申请'
    editingId.value = null
    Object.assign(formData, {
      projectId: undefined,
      contractId: undefined,
      partnerId: undefined,
      warehouseId: undefined,
      requisitionerId: undefined,
      requisitionDate: undefined,
      remark: '',
    })
    itemList.value = []
    itemKeyCounter = 0
    modalVisible.value = true
  }

  async function handleEdit(record: MatRequisitionVO) {
    modalTitle.value = '编辑领料申请'
    editingId.value = record.id ?? null
    Object.assign(formData, {
      projectId: record.projectId,
      contractId: record.contractId,
      partnerId: record.partnerId,
      warehouseId: record.warehouseId,
      requisitionerId: record.requisitionerId,
      requisitionDate: record.requisitionDate,
      remark: record.remark,
    })
    itemList.value = []
    itemKeyCounter = 0
    // Load existing items
    try {
      const items = await getRequisitionItems(record.id!)
      itemList.value = items.map((item) => ({
        ...item,
        key: itemKeyCounter++,
      }))
    } catch (e: unknown) {
      console.error(e)
      message.error('加载领料明细失败')
      itemList.value = []
    }
    modalVisible.value = true
  }

  // --- Line items management ---
  function handleAddItem() {
    itemList.value.push({
      key: itemKeyCounter++,
      materialName: '',
      specification: '',
      unit: '',
      quantity: '0',
      unitPrice: '0',
      amount: '0',
      useLocation: '',
      batchNo: '',
    })
  }

  function handleRemoveItem(index: number) {
    itemList.value.splice(index, 1)
  }

  function handleItemQtyChange(index: number) {
    const item = itemList.value[index]
    const qty = parseFloat(item.quantity || '0')
    const price = parseFloat(item.unitPrice || '0')
    item.amount = (qty * price).toFixed(2)
  }

  function handleItemPriceChange(index: number) {
    const item = itemList.value[index]
    const qty = parseFloat(item.quantity || '0')
    const price = parseFloat(item.unitPrice || '0')
    item.amount = (qty * price).toFixed(2)
  }

  const itemsTotalAmount = computed(() => {
    let total = 0
    for (const item of itemList.value) {
      total += parseFloat(item.amount || '0')
    }
    return total.toFixed(2)
  })

  async function handleModalOk() {
    if (!formData.projectId) {
      message.warning('请选择项目')
      return
    }
    if (!formData.warehouseId) {
      message.warning('请选择仓库')
      return
    }
    const validItems = itemList.value.filter(
      (item) =>
        parseFloat(item.quantity || '0') > 0 &&
        (item.materialId != null || (item.materialName || '').trim() !== ''),
    )
    if (validItems.length === 0) {
      message.warning('请至少添加一条领料明细（材料名称不能为空且数量须大于0）')
      return
    }

    try {
      let requisitionId: string
      if (editingId.value) {
        await updateRequisition(editingId.value, formData)
        requisitionId = editingId.value
        message.success('更新成功')
      } else {
        const result = await createRequisition(formData)
        requisitionId = result
        message.success('创建成功')
      }

      // Save line items
      const items = validItems.map((item) => ({
        ...item,
        requisitionId,
      }))
      await saveRequisitionItems(requisitionId, items)

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
    formData,
    itemList,
    handleAdd,
    handleEdit,
    handleAddItem,
    handleRemoveItem,
    handleItemQtyChange,
    handleItemPriceChange,
    itemsTotalAmount,
    handleModalOk,
    handleModalCancel,
  }
}
