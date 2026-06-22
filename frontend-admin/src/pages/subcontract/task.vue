<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import {
  getSubTaskList,
  createSubTask,
  updateSubTask,
  deleteSubTask,
} from '@/api/modules/subcontract'
import { useReferenceStore } from '@/stores/reference'
import type { SubTaskVO } from '@/types/subcontract'
import type { SelectOption } from '@/types/ui'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  status: undefined as string | undefined,
  taskCode: '',
  taskName: '',
  keyword: '',
})

const loading = ref(false)
const tableData = ref<SubTaskVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const { projects: projectList, contracts: contractList } = storeToRefs(referenceStore)

const modalVisible = ref(false)
const modalTitle = ref('鏂板缓鍒嗗寘浠诲姟')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<SubTaskVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  taskName: '',
  workArea: '',
  plannedStartDate: undefined,
  plannedEndDate: undefined,
  actualStartDate: undefined,
  actualEndDate: undefined,
  progressPercent: '0',
  status: 'NOT_STARTED',
  remark: '',
})
const formPartnerName = computed(
  () => contractList.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)
function onContractChange(contractId: string) {
  const c = contractList.value?.find((ct) => ct.id === contractId)
  formData.partnerId = c?.partyBId
}
watch(
  () => formData.contractId,
  (val) => {
    if (!val) formData.partnerId = undefined
  },
)

const STATUS_LABEL: Record<string, string> = {
  NOT_STARTED: '鏈紑濮?,
  IN_PROGRESS: '杩涜涓?,
  COMPLETED: '宸插畬鎴?,
  SUSPENDED: '宸叉殏鍋?,
}
const STATUS_COLOR: Record<string, string> = {
  NOT_STARTED: 'default',
  IN_PROGRESS: 'processing',
  COMPLETED: 'success',
  SUSPENDED: 'warning',
}

// ---- vxe-grid columns ----
const gridColumns = computed(() => [
  { field: 'taskCode', title: '浠诲姟缂栧彿', width: 130, ellipsis: true },
  {
    field: 'taskName',
    title: '浠诲姟鍚嶇О',
    minWidth: 140,
    slots: { default: 'taskName' },
    ellipsis: true,
  },
  { field: 'projectName', title: '椤圭洰鍚嶇О', width: 120, ellipsis: true },
  { field: 'contractName', title: '鍚堝悓鍚嶇О', width: 120, ellipsis: true },
  { field: 'partnerName', title: '鍒嗗寘鍟?, width: 120, ellipsis: true },
  { field: 'workArea', title: '鏂藉伐鍖哄煙', width: 100, ellipsis: true },
  { field: 'progressPercent', title: '杩涘害', width: 90, slots: { default: 'progressPercent' } },
  { field: 'status', title: '鐘舵€?, width: 80, slots: { default: 'status' } },
  { field: 'plannedStartDate', title: '璁″垝寮€濮?, width: 100 },
  { field: 'plannedEndDate', title: '璁″垝缁撴潫', width: 100 },
  { title: '鎿嶄綔', width: 110, slots: { default: 'action' } },
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getSubTaskList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      status: filter.status,
      taskCode: filter.keyword || filter.taskCode || undefined,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('鍔犺浇鍒嗗寘浠诲姟鍒楄〃澶辫触锛岃绋嶅悗閲嶈瘯')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.status = undefined
  filter.taskCode = ''
  filter.taskName = ''
  filter.keyword = ''
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

function handleAdd() {
  modalTitle.value = '鏂板缓鍒嗗寘浠诲姟'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    taskName: '',
    workArea: '',
    plannedStartDate: undefined,
    plannedEndDate: undefined,
    actualStartDate: undefined,
    actualEndDate: undefined,
    progressPercent: '0',
    status: 'NOT_STARTED',
    remark: '',
  })
  modalVisible.value = true
}

function handleEdit(record: SubTaskVO) {
  modalTitle.value = '缂栬緫鍒嗗寘浠诲姟'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    taskName: record.taskName,
    workArea: record.workArea,
    plannedStartDate: record.plannedStartDate,
    plannedEndDate: record.plannedEndDate,
    actualStartDate: record.actualStartDate,
    actualEndDate: record.actualEndDate,
    progressPercent: record.progressPercent,
    status: record.status,
    remark: record.remark,
  })
  modalVisible.value = true
}

function handleDelete(record: SubTaskVO) {
  Modal.confirm({
    title: '纭鍒犻櫎',
    content: `纭畾瑕佸垹闄ゅ垎鍖呬换鍔?${record.taskName}"鍚楋紵`,
    okText: '纭畾',
    cancelText: '鍙栨秷',
    onOk: async () => {
      try {
        await deleteSubTask(record.id)
        message.success('鍒犻櫎鎴愬姛')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('鍒犻櫎澶辫触锛岃绋嶅悗閲嶈瘯')
      }
    },
  })
}

async function handleModalOk() {
  if (!formData.projectId) {
    message.warning('璇烽€夋嫨椤圭洰')
    return
  }
  if (!formData.taskName) {
    message.warning('璇疯緭鍏ヤ换鍔″悕绉?)
    return
  }

  try {
    if (editingId.value) {
      await updateSubTask(editingId.value, formData)
      message.success('鏇存柊鎴愬姛')
    } else {
      await createSubTask(formData)
      message.success('鍒涘缓鎴愬姛')
    }
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('鎿嶄綔澶辫触锛岃绋嶅悗閲嶈瘯')
  }
}

function handleModalCancel() {
  modalVisible.value = false
}

// ---- KPI ----
const kpiInProgress = computed(
  () => tableData.value.filter((r) => r.status === 'IN_PROGRESS').length,
)
const kpiCompleted = computed(() => tableData.value.filter((r) => r.status === 'COMPLETED').length)
const kpiPending = computed(() => tableData.value.filter((r) => r.status === 'NOT_STARTED').length)
const kpiSuspended = computed(() => tableData.value.filter((r) => r.status === 'SUSPENDED').length)

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'SUB' })
  referenceStore.fetchPartners({ partnerType: 'SUB' })
  fetchData()
})
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
        <a-breadcrumb-item>鍒嗗寘绠＄悊</a-breadcrumb-item>
        <a-breadcrumb-item>鍒嗗寘浠诲姟</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <!-- 鎼滅储鏍?-->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="鎼滅储浠诲姟缂栧彿銆佸悕绉扳€?
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">鏌ヨ</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        閲嶇疆
      </a-button>
    </div>

    <!-- KPI 妯潯 -->
    <div class="lg-kpi-strip">
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">杩涜涓?/span>
        <span class="lg-kpi-card-value">{{ kpiInProgress }} <small>鏉?/small></span>
        <span class="lg-kpi-card-bar"
          ><span style="width: 100%; background: var(--kpi-total)"></span
        ></span>
      </div>
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">宸插畬鎴?/span>
        <span class="lg-kpi-card-value">{{ kpiCompleted }} <small>鏉?/small></span>
        <span class="lg-kpi-card-bar"
          ><span style="width: 100%; background: var(--kpi-paid)"></span
        ></span>
      </div>
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">寰呭紑濮?/span>
        <span class="lg-kpi-card-value">{{ kpiPending }} <small>鏉?/small></span>
        <span class="lg-kpi-card-bar"
          ><span style="width: 100%; background: var(--kpi-amount)"></span
        ></span>
      </div>
      <div class="lg-kpi-card" :class="{ 'is-warn': kpiSuspended > 0 }">
        <span class="lg-kpi-card-label">宸叉殏鍋?/span>
        <span class="lg-kpi-card-value">{{ kpiSuspended }} <small>鏉?/small></span>
        <span class="lg-kpi-card-bar"
          ><span
            :style="{ width: (kpiSuspended > 0 ? 100 : 0) + '%', background: 'var(--kpi-overdue)' }"
          ></span
        ></span>
      </div>
    </div>

    <!-- 宸ュ叿鏍?-->
    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" @click="handleAdd">
          <template #icon><PlusOutlined /></template>
          鏂板缓浠诲姟
        </a-button>
        <a-button @click="fetchData">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
      <div class="lg-toolbar-right">
        <a-select
          v-model:value="filter.projectId"
          placeholder="鍏ㄩ儴椤圭洰"
          allow-clear
          style="width: 160px"
          size="small"
          @change="handleSearch"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
      </div>
    </div>

    <!-- 琛ㄦ牸 -->
    <div class="lg-table-wrap">
      <vxe-grid
        :data="tableData"
        :columns="gridColumns"
        :loading="loading"
        :column-config="{ resizable: true }"
        stripe
        border="inner"
        size="small"
        max-height="480"
      >
        <template #taskName="{ row }">
          <a class="lg-link">{{ row.taskName }}</a>
        </template>
        <template #progressPercent="{ row }">
          <a-progress
            v-if="row.progressPercent"
            :percent="parseFloat(row.progressPercent)"
            :stroke-width="8"
            size="small"
            :show-info="true"
          />
          <span v-else style="color: var(--muted)">-</span>
        </template>
        <template #status="{ row }">
          <a-tag :color="STATUS_COLOR[row.status]">
            {{ STATUS_LABEL[row.status] ?? row.status }}
          </a-tag>
        </template>
        <template #action="{ row }">
          <div class="lg-ops">
            <a class="lg-link" @click="handleEdit(row)">缂栬緫</a>
            <a class="lg-link lg-del" @click="handleDelete(row)">鍒犻櫎</a>
          </div>
        </template>
      </vxe-grid>
    </div>

    <!-- 鍒嗛〉 -->
    <div class="lg-pagination">
      <span class="lg-total">鍏?{{ total }} 鏉?/span>
      <a-pagination
        v-model:current="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-size-options="['10', '20', '50', '100']"
        show-size-changer
        show-quick-jumper
        @change="handlePageChange"
        @show-size-change="handlePageSizeChange"
      />
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="720"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="椤圭洰" required>
          <a-select
            v-model:value="formData.projectId"
            placeholder="璇烽€夋嫨椤圭洰"
            show-search
            @change="
              (v: string) => {
                formData.contractId = undefined
                formData.partnerId = undefined
                referenceStore.fetchContracts({ projectId: v })
              }
            "
            :filter-option="
              (input: string, option: SelectOption) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="鍒嗗寘鍚堝悓">
          <a-select
            v-model:value="formData.contractId"
            placeholder="璇烽€夋嫨鍚堝悓"
            allow-clear
            show-search
            :filter-option="
              (input: string, option: SelectOption) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
            @change="onContractChange"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="鍒嗗寘鍟?>
          <a-input :value="formPartnerName" disabled placeholder="閫夋嫨鍚堝悓鍚庤嚜鍔ㄥ～鍏呬箼鏂? />
        </a-form-item>
        <a-form-item label="浠诲姟鍚嶇О" required>
          <a-input v-model:value="formData.taskName" placeholder="璇疯緭鍏ヤ换鍔″悕绉? />
        </a-form-item>
        <a-form-item label="鏂藉伐鍖哄煙">
          <a-input v-model:value="formData.workArea" placeholder="璇疯緭鍏ユ柦宸ュ尯鍩? />
        </a-form-item>
        <a-form-item label="璁″垝寮€濮嬫棩鏈?>
          <a-date-picker
            v-model:value="formData.plannedStartDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="璁″垝缁撴潫鏃ユ湡">
          <a-date-picker
            v-model:value="formData.plannedEndDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="瀹為檯寮€濮嬫棩鏈?>
          <a-date-picker
            v-model:value="formData.actualStartDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="瀹為檯缁撴潫鏃ユ湡">
          <a-date-picker
            v-model:value="formData.actualEndDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="杩涘害鐧惧垎姣?>
          <a-input-number
            v-model:value="formData.progressPercent"
            :min="0"
            :max="100"
            :precision="2"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="鐘舵€?>
          <a-select v-model:value="formData.status" placeholder="璇烽€夋嫨鐘舵€?>
            <a-select-option value="NOT_STARTED">鏈紑濮?/a-select-option>
            <a-select-option value="IN_PROGRESS">杩涜涓?/a-select-option>
            <a-select-option value="COMPLETED">宸插畬鎴?/a-select-option>
            <a-select-option value="SUSPENDED">宸叉殏鍋?/a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="澶囨敞">
          <a-textarea v-model:value="formData.remark" :rows="3" placeholder="璇疯緭鍏ュ娉? />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped></style>
