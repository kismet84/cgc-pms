import { ref, reactive, computed } from 'vue'
import type { Ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getPositionList, createPosition, updatePosition, deletePosition } from '@/api/modules/org'
import type { OrgPositionVO } from '@/types/org'
import type { PageResult } from '@/types/api'
import { flattenDeptTree } from '../utils'
import type { OrgDepartmentTreeNodeVO } from '@/types/org'

function normalizeArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (value && typeof value === 'object') {
    const records = (value as { records?: unknown }).records
    if (Array.isArray(records)) return records as T[]
  }
  return []
}

export function usePosition(deptTreeData: Ref<OrgDepartmentTreeNodeVO[]>) {
  const loading = ref(false)
  const data = ref<OrgPositionVO[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)

  const filter = reactive({
    companyId: undefined as string | undefined,
    departmentId: undefined as string | undefined,
    positionCode: '',
    positionName: '',
    status: undefined as string | undefined,
  })

  const gridColumns = computed(() => [
    { field: 'companyId', title: '所属公司', width: 112, slots: { default: 'posCompanyId' } },
    { field: 'departmentId', title: '所属部门', width: 112, slots: { default: 'posDeptId' } },
    { field: 'positionCode', title: '岗位编号', width: 120 },
    { field: 'positionName', title: '岗位名称', minWidth: 140 },
    { field: 'status', title: '状态', width: 88, slots: { default: 'posStatus' } },
    { field: 'createdAt', title: '创建时间', width: 150 },
    { title: '操作', width: 76, align: 'center' as const, slots: { default: 'posOps' } },
  ])

  const flatDeptList = computed(() => flattenDeptTree(deptTreeData.value))

  /** 筛选栏中按所选公司过滤的部门列表 */
  const filterDeptList = computed(() => {
    if (!filter.companyId) return flatDeptList.value
    return flatDeptList.value.filter((d) => d.companyId === filter.companyId)
  })

  /** 模态框中按所选公司过滤的部门列表 */
  const modalDeptList = computed(() => {
    if (!form.companyId) return flatDeptList.value
    return flatDeptList.value.filter((d) => d.companyId === form.companyId)
  })

  async function fetchData() {
    loading.value = true
    try {
      const res: PageResult<OrgPositionVO> = await getPositionList({
        pageNum: pageNo.value,
        pageSize: pageSize.value,
        companyId: filter.companyId || undefined,
        departmentId: filter.departmentId || undefined,
        positionCode: filter.positionCode || undefined,
        positionName: filter.positionName || undefined,
        status: filter.status,
      })
      data.value = normalizeArray<OrgPositionVO>(res.records)
      total.value = Number(res.total) || data.value.length
    } catch (e: unknown) {
      console.error(e)
      data.value = []
      total.value = 0
      message.error('加载岗位列表失败')
    } finally {
      loading.value = false
    }
  }

  function handleSearch() {
    pageNo.value = 1
    fetchData()
  }

  function handleReset() {
    filter.companyId = undefined
    filter.departmentId = undefined
    filter.positionCode = ''
    filter.positionName = ''
    filter.status = undefined
    pageNo.value = 1
    fetchData()
  }

  function handlePageChange(page: number) {
    pageNo.value = page
    fetchData()
  }

  function handlePageSizeChange(_cur: number, size: number) {
    pageSize.value = size
    pageNo.value = 1
    fetchData()
  }

  // ─── Modal state ───

  const modalVisible = ref(false)
  const modalTitle = ref('新增岗位')
  const form = reactive({
    id: '' as string,
    companyId: '' as string,
    departmentId: '' as string,
    positionCode: '',
    positionName: '',
    status: 'ENABLED',
    remark: '',
  })
  const saving = ref(false)

  function openAdd() {
    form.id = ''
    form.companyId = ''
    form.departmentId = ''
    form.positionCode = ''
    form.positionName = ''
    form.status = 'ENABLED'
    form.remark = ''
    modalTitle.value = '新增岗位'
    modalVisible.value = true
  }

  function openEdit(record: OrgPositionVO) {
    form.id = record.id
    form.companyId = record.companyId ?? ''
    form.departmentId = record.departmentId ?? ''
    form.positionCode = record.positionCode
    form.positionName = record.positionName
    form.status = record.status
    form.remark = record.remark ?? ''
    modalTitle.value = '编辑岗位'
    modalVisible.value = true
  }

  async function handleSave() {
    if (!form.companyId) {
      message.warning('请选择所属公司')
      return
    }
    if (!form.departmentId) {
      message.warning('请选择所属部门')
      return
    }
    saving.value = true
    try {
      const body = {
        companyId: form.companyId || undefined,
        departmentId: form.departmentId || undefined,
        positionCode: form.positionCode,
        positionName: form.positionName,
        status: form.status,
        remark: form.remark || undefined,
      }
      if (form.id) {
        await updatePosition(form.id, body)
        message.success('岗位更新成功')
      } else {
        await createPosition(body)
        message.success('岗位创建成功')
      }
      modalVisible.value = false
      await fetchData()
    } catch (e: unknown) {
      console.error(e)
      message.error('操作失败')
    } finally {
      saving.value = false
    }
  }

  async function handleDelete(record: OrgPositionVO) {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除岗位「${record.positionName}」吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      async onOk() {
        try {
          await deletePosition(record.id)
          message.success('已删除')
          await fetchData()
        } catch (e: unknown) {
          console.error(e)
          message.error('删除失败')
        }
      },
    })
  }

  return {
    loading,
    data,
    total,
    pageNo,
    pageSize,
    filter,
    gridColumns,
    flatDeptList,
    filterDeptList,
    modalDeptList,
    fetchData,
    handleSearch,
    handleReset,
    handlePageChange,
    handlePageSizeChange,
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
