import { ref, reactive, computed } from 'vue'
import type { Ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getDepartmentTree,
  createDepartment,
  updateDepartment,
  deleteDepartment,
} from '@/api/modules/org'
import type { OrgDepartmentTreeNodeVO } from '@/types/org'
import { filterDeptNodes, findDeptNode } from '../utils'

export function useDepartment(selectedCompanyId: Ref<string | null>) {
  const treeLoading = ref(false)
  const treeData = ref<OrgDepartmentTreeNodeVO[]>([])
  const selectedKeys = ref<string[]>([])
  const keyword = ref('')

  const filteredTree = computed(() => {
    const companyId = selectedCompanyId.value
    const kw = keyword.value.trim()
    return filterDeptNodes(treeData.value, companyId, kw)
  })

  async function fetchTree() {
    treeLoading.value = true
    try {
      treeData.value = await getDepartmentTree()
    } catch (e: unknown) {
      console.error(e)
      treeData.value = []
      message.error('加载部门树失败')
    } finally {
      treeLoading.value = false
    }
  }

  function handleSelect() {
    // Selection handled by v-model:selectedKeys
  }

  // ─── Modal state ───

  const modalVisible = ref(false)
  const modalTitle = ref('新增部门')
  const form = reactive({
    id: '' as string,
    companyId: '' as string,
    parentId: '' as string,
    deptCode: '',
    deptName: '',
    orderNum: 1,
    status: 'ENABLED',
    remark: '',
  })
  const saving = ref(false)

  function openAdd() {
    form.id = ''
    form.companyId = selectedCompanyId.value ?? ''
    form.parentId = selectedKeys.value[0] ?? ''
    form.deptCode = ''
    form.deptName = ''
    form.orderNum = 1
    form.status = 'ENABLED'
    form.remark = ''
    modalTitle.value = '新增部门'
    modalVisible.value = true
  }

  function openEdit() {
    const key = selectedKeys.value[0]
    if (!key) {
      message.warning('请先选择一个部门')
      return
    }
    const node = findDeptNode(treeData.value, key)
    if (!node) {
      message.warning('未找到该部门')
      return
    }
    form.id = node.id
    form.companyId = node.companyId
    form.parentId = node.parentId ?? ''
    form.deptCode = node.deptCode
    form.deptName = node.deptName
    form.orderNum = node.orderNum
    form.status = node.status
    form.remark = ''
    modalTitle.value = '编辑部门'
    modalVisible.value = true
  }

  async function handleSave() {
    saving.value = true
    try {
      const body = {
        companyId: form.companyId,
        parentId: form.parentId || undefined,
        deptCode: form.deptCode,
        deptName: form.deptName,
        orderNum: form.orderNum,
        status: form.status,
        remark: form.remark || undefined,
      }
      if (form.id) {
        await updateDepartment(form.id, body)
        message.success('部门更新成功')
      } else {
        await createDepartment(body)
        message.success('部门创建成功')
      }
      modalVisible.value = false
      await fetchTree()
    } catch (e: unknown) {
      console.error(e)
      message.error('操作失败')
    } finally {
      saving.value = false
    }
  }

  async function handleDelete() {
    const key = selectedKeys.value[0]
    if (!key) {
      message.warning('请先选择一个部门')
      return
    }
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该部门吗？',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      async onOk() {
        try {
          await deleteDepartment(key)
          message.success('已删除')
          selectedKeys.value = []
          await fetchTree()
        } catch (e: unknown) {
          console.error(e)
          message.error('删除失败')
        }
      },
    })
  }

  return {
    treeLoading,
    treeData,
    selectedKeys,
    keyword,
    filteredTree,
    fetchTree,
    handleSelect,
    modalVisible,
    modalTitle,
    form,
    saving,
    openAdd,
    openEdit,
    handleSave,
    handleDelete,
  }
}
