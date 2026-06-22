<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  PlusOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getCostTargetList, activateCostTarget, deleteCostTarget } from '@/api/modules/costTarget'
import { useReferenceStore } from '@/stores/reference'
import CostTargetEditPage from './edit.vue'
import type { CostTargetVO, CostTargetQueryParams } from '@/types/costTarget'
import type { SelectOption } from '@/types/ui'
import {
  APPROVAL_STATUS_LABEL,
  APPROVAL_STATUS_COLOR,
  TARGET_STATUS_LABEL,
  TARGET_STATUS_COLOR,
} from '@/types/costTarget'

// ---- Dropdown data ----
const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])

// ---- Filter state ----
const filter = reactive({
  projectId: undefined as string | undefined,
  versionNo: '',
  approvalStatus: undefined as string | undefined,
  isActive: undefined as number | undefined,
})

// ---- Table state ----
const loading = ref(false)
const activating = ref(false)
const tableData = ref<CostTargetVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const targetModalVisible = ref(false)
const targetModalMode = ref<'create' | 'edit'>('create')
const targetModalId = ref('')

// ---- Fetch data ----
async function fetchData() {
  loading.value = true
  const params: CostTargetQueryParams = {
    pageNo: pageNo.value,
    pageSize: pageSize.value,
    projectId: filter.projectId,
    versionNo: filter.versionNo || undefined,
    approvalStatus: filter.approvalStatus,
    isActive: filter.isActive,
  }
  try {
    const res: PageResult<CostTargetVO> = await getCostTargetList(params)
    tableData.value = res.records
    total.value = Number(res.total) || 0
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('鍔犺浇鐩爣鎴愭湰鐗堟湰鍒楄〃澶辫触')
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
  filter.versionNo = ''
  filter.approvalStatus = undefined
  filter.isActive = undefined
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

// ---- Actions ----
function handleCreate() {
  targetModalMode.value = 'create'
  targetModalId.value = ''
  targetModalVisible.value = true
}

function handleEdit(row: CostTargetVO) {
  targetModalMode.value = 'edit'
  targetModalId.value = String(row.id)
  targetModalVisible.value = true
}

function handleActivate(row: CostTargetVO) {
  Modal.confirm({
    title: '纭鍒囨崲鐗堟湰锛?,
    content: `灏嗘縺娲荤増鏈€?{row.versionNo} ${row.versionName}銆嶏紝璇ラ」鐩笅鍏朵粬鐗堟湰灏嗚嚜鍔ㄥけ鏁堛€俙,
    okText: '纭鍒囨崲',
    cancelText: '鍙栨秷',
    onOk: async () => {
      activating.value = true
      try {
        await activateCostTarget(row.id)
        message.success('鐗堟湰宸叉縺娲?)
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('鐗堟湰婵€娲诲け璐?)
      } finally {
        activating.value = false
      }
    },
  })
}

function handleDelete(row: CostTargetVO) {
  Modal.confirm({
    title: '纭鍒犻櫎锛?,
    content: `灏嗗垹闄ょ増鏈€?{row.versionNo} ${row.versionName}銆嶏紝鍒犻櫎鍚庝笉鍙仮澶嶃€俙,
    okText: '纭鍒犻櫎',
    okType: 'danger',
    cancelText: '鍙栨秷',
    onOk: async () => {
      try {
        await deleteCostTarget(row.id)
        message.success('宸插垹闄?)
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        // 鍝嶅簲鎷︽埅鍣ㄥ凡寮瑰嚭鍚庣杩斿洖鐨勯敊璇秷鎭紝姝ゅ鍒锋柊鍒楄〃浠ョЩ闄ゅ凡鍒犻櫎鐨勮褰?        fetchData()
      }
    },
  })
}

function handleTargetSaved() {
  targetModalVisible.value = false
  fetchData()
}

function handleTargetClose() {
  targetModalVisible.value = false
}

// ---- Helpers ----
function fmtAmount(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// ---- VxeGrid columns ----
const columns = [
  { field: 'versionNo', title: '鐗堟湰鍙?, width: 130 },
  { field: 'versionName', title: '鐗堟湰鍚嶇О', minWidth: 160 },
  { field: 'projectName', title: '鎵€灞為」鐩?, width: 150 },
  {
    field: 'totalTargetAmount',
    title: '鐩爣鎴愭湰鍚堣(涓囧厓)',
    width: 150,
    align: 'right' as const,
    slots: { default: 'amount' },
  },
  { field: 'effectiveDate', title: '鐢熸晥鏃ユ湡', width: 110 },
  { field: 'approvalStatus', title: '瀹℃壒鐘舵€?, width: 100, slots: { default: 'approvalStatus' } },
  { field: 'status', title: '涓氬姟鐘舵€?, width: 100, slots: { default: 'status' } },
  { field: 'isActive', title: '鐗堟湰鏍囪瘑', width: 100, slots: { default: 'isActive' } },
  { title: '鎿嶄綔', width: 160, slots: { default: 'ops' } },
]

onMounted(() => {
  referenceStore.fetchProjects()
  fetchData()
})
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
        <a-breadcrumb-item>鐩爣绠＄悊</a-breadcrumb-item>
        <a-breadcrumb-item>鐩爣鎴愭湰</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.versionNo"
        placeholder="鎼滅储鐗堟湰鍙封€?
        allow-clear
        style="flex: 1; max-width: 240px"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" @click="handleSearch">鏌ヨ</a-button>
      <a-button @click="handleReset">閲嶇疆</a-button>
    </div>

    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" @click="handleCreate">
          <template #icon><PlusOutlined /></template>
          鏂板缓鐩爣鎴愭湰
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
          show-search
          :filter-option="
            (input: string, option: SelectOption) =>
              option.label?.toLowerCase().includes(input.toLowerCase())
          "
          @change="handleSearch"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">{{
            p.projectName
          }}</a-select-option>
        </a-select>
      </div>
    </div>

    <div class="lg-table-wrap">
      <vxe-grid
        :data="tableData"
        :columns="columns"
        :loading="loading"
        :column-config="{ resizable: true }"
        stripe
        border="inner"
        size="small"
        max-height="480"
      >
        <template #amount="{ row }">
          <span class="ct-money">{{ fmtAmount(row.totalTargetAmount) }}</span>
        </template>
        <template #approvalStatus="{ row }">
          <a-tag :color="APPROVAL_STATUS_COLOR[row.approvalStatus] || 'default'">
            {{ APPROVAL_STATUS_LABEL[row.approvalStatus] || row.approvalStatus }}
          </a-tag>
        </template>
        <template #status="{ row }">
          <a-tag :color="TARGET_STATUS_COLOR[row.status] || 'default'">
            {{ TARGET_STATUS_LABEL[row.status] || row.status }}
          </a-tag>
        </template>
        <template #isActive="{ row }">
          <a-tag v-if="row.isActive === 1" color="green">褰撳墠鐗堟湰</a-tag>
          <span v-else class="ct-muted">鍘嗗彶鐗堟湰</span>
        </template>
        <template #ops="{ row }">
          <div class="ct-ops">
            <a class="lg-link" @click="handleEdit(row)">缂栬緫</a>
            <a
              v-if="row.isActive !== 1 && row.approvalStatus === 'APPROVED'"
              class="lg-link"
              :class="{ 'lg-link--disabled': activating }"
              @click="handleActivate(row)"
            >
              <CheckCircleOutlined style="margin-right: 4px" />鍒囨崲鐗堟湰
            </a>
            <a
              v-if="row.approvalStatus === 'DRAFT' || row.approvalStatus === 'REJECTED'"
              class="lg-link lg-link--danger"
              @click="handleDelete(row)"
              >鍒犻櫎</a
            >
          </div>
        </template>
      </vxe-grid>
    </div>

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

    <a-modal
      v-model:open="targetModalVisible"
      :title="targetModalMode === 'edit' ? '缂栬緫鐩爣鎴愭湰' : '鏂板缓鐩爣鎴愭湰'"
      :width="1160"
      :destroy-on-close="true"
      :footer="null"
      :mask-closable="false"
      centered
      class="ct-target-modal"
      @cancel="handleTargetClose"
    >
      <CostTargetEditPage
        :embedded="true"
        :mode="targetModalMode"
        :target-id="targetModalId"
        @saved="handleTargetSaved"
        @close="handleTargetClose"
      />
    </a-modal>
  </div>
</template>

<style scoped>
.ct-target-modal :deep(.ant-modal-body) {
  max-height: 82vh;
  overflow: auto;
}
.lg-link--disabled {
  color: #9ca3af;
  pointer-events: none;
}
.ct-money {
  font-variant-numeric: tabular-nums;
}
.ct-ops {
  display: flex;
  gap: 10px;
  justify-content: center;
}
.ct-muted {
  color: #9ca3af;
  font-size: 13px;
}
</style>
