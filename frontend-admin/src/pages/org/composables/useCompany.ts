import { ref, reactive, computed } from 'vue'
import type { Ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getCompanyList, createCompany, updateCompany, deleteCompany } from '@/api/modules/org'
import type { OrgCompanyVO } from '@/types/org'
import type { PageResult } from '@/types/api'

export function useCompany(selectedCompanyId: Ref<string | null>) {
  const loading = ref(false)
  const data = ref<OrgCompanyVO[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)

  const filter = reactive({
    companyCode: '',
    companyName: '',
    status: undefined as string | undefined,
  })

  const gridColumns = computed(() => [
    { field: 'companyCode', title: '公司编号', width: 120 },
    { field: 'companyName', title: '公司名称', minWidth: 140 },
    { field: 'status', title: '状态', width: 80, slots: { default: 'companyStatus' } },
    { field: 'createdAt', title: '创建时间', width: 150 },
    { title: '操作', width: 120, align: 'right' as const, slots: { default: 'companyOps' } },
  ])

  async function fetchData() {
    loading.value = true
    try {
      const res: PageResult<OrgCompanyVO> = await getCompanyList({
        pageNum: pageNo.value,
        pageSize: pageSize.value,
        companyCode: filter.companyCode || undefined,
        companyName: filter.companyName || undefined,
        status: filter.status,
      })
      data.value = res.records
      total.value = res.total
    } catch (e: unknown) {
      console.error(e)
      data.value = []
      total.value = 0
      message.error('加载公司列表失败')
    } finally {
      loading.value = false
    }
  }

  function handleSearch() {
    pageNo.value = 1
    fetchData()
  }

  function handleReset() {
    filter.companyCode = ''
    filter.companyName = ''
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

  function handleRowClick(record: OrgCompanyVO) {
    selectedCompanyId.value = record.id
  }

  // ─── Modal state ───

  const modalVisible = ref(false)
  const modalTitle = ref('新增公司')
  const form = reactive({
    id: '' as string,
    companyCode: '',
    companyName: '',
    status: 'ENABLED',
    remark: '',
  })
  const saving = ref(false)

  function openAdd() {
    form.id = ''
    form.companyCode = ''
    form.companyName = ''
    form.status = 'ENABLED'
    form.remark = ''
    modalTitle.value = '新增公司'
    modalVisible.value = true
  }

  function openEdit(record: OrgCompanyVO) {
    form.id = record.id
    form.companyCode = record.companyCode
    form.companyName = record.companyName
    form.status = record.status
    form.remark = record.remark ?? ''
    modalTitle.value = '编辑公司'
    modalVisible.value = true
  }

  async function handleSave() {
    saving.value = true
    try {
      const body = {
        companyCode: form.companyCode,
        companyName: form.companyName,
        status: form.status,
        remark: form.remark || undefined,
      }
      if (form.id) {
        await updateCompany(form.id, body)
        message.success('公司更新成功')
      } else {
        await createCompany(body)
        message.success('公司创建成功')
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

  async function handleDelete(record: OrgCompanyVO) {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除公司「${record.companyName}」吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      async onOk() {
        try {
          await deleteCompany(record.id)
          message.success('已删除')
          if (selectedCompanyId.value === record.id) selectedCompanyId.value = null
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
    fetchData,
    handleSearch,
    handleReset,
    handlePageChange,
    handlePageSizeChange,
    handleRowClick,
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
