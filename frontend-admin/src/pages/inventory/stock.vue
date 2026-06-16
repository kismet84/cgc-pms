<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getStockLedger } from '@/api/modules/inventory'
import { getWarehouseList } from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { WarehouseVO, MatStockVO, MatStockTxnVO } from '@/types/inventory'

const filter = reactive({
  warehouseId: undefined as string | undefined,
  materialId: undefined as string | undefined,
})

const loading = ref(false)
const stock = ref<MatStockVO | null>(null)
const txnList = ref<MatStockTxnVO[]>([])
const txnTotal = ref(0)
const txnPageNo = ref(1)
const txnPageSize = ref(20)

const warehouseList = ref<WarehouseVO[]>([])
const referenceStore = useReferenceStore()
const materialList = computed(() => referenceStore.materials ?? [])

const TXN_TYPE_LABEL: Record<string, string> = {
  IN: '入库',
  OUT: '出库',
  ADJUST: '调整',
}
const TXN_TYPE_COLOR: Record<string, string> = {
  IN: 'success',
  OUT: 'error',
  ADJUST: 'warning',
}

const txnColumns = [
  { title: '流水编号', dataIndex: 'id', width: 100 },
  { title: '类型', dataIndex: 'txnType', width: 70, key: 'txnType' },
  { title: '变动量', dataIndex: 'quantity', width: 100, key: 'quantity' },
  { title: '变动后余量', dataIndex: 'availableAfter', width: 120, key: 'availableAfter' },
  { title: '来源类型', dataIndex: 'sourceType', width: 100 },
  { title: '操作时间', dataIndex: 'createdTime', width: 160 },
]

async function fetchLedger() {
  if (!filter.warehouseId) {
    stock.value = null
    txnList.value = []
    txnTotal.value = 0
    return
  }
  loading.value = true
  try {
    const res = await getStockLedger({
      warehouseId: filter.warehouseId,
      materialId: filter.materialId,
      pageNo: txnPageNo.value,
      pageSize: txnPageSize.value,
    })
    stock.value = res.stock
    if (res.txns) {
      txnList.value = res.txns.records ?? []
      txnTotal.value = res.txns.total ?? 0
    } else {
      txnList.value = []
      txnTotal.value = 0
    }
  } catch (e: unknown) {
    console.error(e)
    stock.value = null
    txnList.value = []
    txnTotal.value = 0
    message.error('加载库存台账失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchWarehouses() {
  try {
    const res = await getWarehouseList({ pageNo: 1, pageSize: 50, status: 'ENABLE' })
    warehouseList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    warehouseList.value = []
  }
}

function handleSearch() {
  txnPageNo.value = 1
  fetchLedger()
}

function handleReset() {
  filter.warehouseId = undefined
  filter.materialId = undefined
  txnPageNo.value = 1
  stock.value = null
  txnList.value = []
  txnTotal.value = 0
}

function handleTxnPageChange(page: number) {
  txnPageNo.value = page
  fetchLedger()
}

function handleTxnPageSizeChange(_cur: number, size: number) {
  txnPageSize.value = size
  txnPageNo.value = 1
  fetchLedger()
}

function getWarehouseName(id: string): string {
  return warehouseList.value.find((w) => w.id === id)?.warehouseName ?? id
}

function getMaterialName(id: string): string {
  return materialList.value.find((m) => m.id === id)?.materialName ?? id
}

onMounted(() => {
  fetchWarehouses()
  referenceStore.fetchMaterials()
})
</script>

<template>
  <div class="pm-page">
    <a-page-header title="库存台账" class="pm-header" />

    <!-- Filter -->
    <div class="pm-card pm-filter">
      <div class="pm-filter-row">
        <div class="pm-field">
          <label>仓库：</label>
          <a-select
            v-model:value="filter.warehouseId"
            placeholder="请选择仓库"
            allow-clear
            style="width: 200px"
            show-search
            :filter-option="
              (input: string, option: any) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
              {{ w.warehouseName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>物料：</label>
          <a-select
            v-model:value="filter.materialId"
            placeholder="全部物料"
            allow-clear
            style="width: 200px"
            show-search
            :filter-option="
              (input: string, option: any) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
              {{ m.materialName }} <span style="color: #9ca3af">({{ m.materialCode }})</span>
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <!-- Stock Balance Card -->
    <div v-if="stock" class="pm-card" style="padding: 20px 22px; margin-bottom: 14px">
      <div style="display: flex; gap: 40px; align-items: center; flex-wrap: wrap">
        <div>
          <span style="font-size: 13px; color: #6b7280">仓库：</span>
          <span style="font-weight: 600">{{ getWarehouseName(stock.warehouseId) }}</span>
        </div>
        <div>
          <span style="font-size: 13px; color: #6b7280">物料：</span>
          <span style="font-weight: 600">{{ getMaterialName(stock.materialId) }}</span>
        </div>
        <div>
          <span style="font-size: 13px; color: #6b7280">当前库存：</span>
          <span style="font-weight: 700; font-size: 18px; color: #1677ff">
            {{ Number(stock.availableQty).toLocaleString('zh-CN', { minimumFractionDigits: 4 }) }}
          </span>
          <span style="font-size: 13px; color: #6b7280; margin-left: 4px">{{ stock.unit }}</span>
        </div>
      </div>
    </div>
    <div
      v-else-if="filter.warehouseId"
      class="pm-card"
      style="padding: 20px 22px; margin-bottom: 14px; color: #9ca3af"
    >
      该仓库暂无选中物料库存记录
    </div>

    <!-- Transaction Ledger -->
    <div class="pm-card pm-table-wrap">
      <div style="padding: 16px 22px 0; font-weight: 600; font-size: 14px; color: #374151">
        出入库流水
      </div>
      <a-table
        :columns="txnColumns"
        :data-source="txnList"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ x: 800 }"
        style="margin-top: 8px"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'txnType'">
            <a-tag :color="TXN_TYPE_COLOR[record.txnType]">
              {{ TXN_TYPE_LABEL[record.txnType] ?? record.txnType }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'quantity'">
            <span
              :style="{ color: record.txnType === 'OUT' ? '#ef4444' : '#16a34a', fontWeight: 600 }"
            >
              {{ record.txnType === 'OUT' ? '-' : '+'
              }}{{ Number(record.quantity).toLocaleString('zh-CN', { minimumFractionDigits: 4 }) }}
            </span>
          </template>
          <template v-else-if="column.key === 'availableAfter'">
            <span style="font-weight: 600">
              {{
                Number(record.availableAfter).toLocaleString('zh-CN', { minimumFractionDigits: 4 })
              }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'sourceType'">
            <span>{{ record.sourceType || '-' }}</span>
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="pm-pagination">
      <span class="pm-total">共 {{ txnTotal }} 条流水</span>
      <a-pagination
        v-model:current="txnPageNo"
        v-model:page-size="txnPageSize"
        :total="txnTotal"
        :page-size-options="['10', '20', '50', '100']"
        show-size-changer
        show-quick-jumper
        @change="handleTxnPageChange"
        @show-size-change="handleTxnPageSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.pm-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.pm-header {
  background: transparent;
  padding-bottom: 12px;
}
.pm-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.pm-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.pm-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
}
.pm-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.pm-field label {
  color: #374151;
}
.pm-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}
.pm-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
}
.pm-link {
  color: #1677ff;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
}
.pm-none {
  color: #9ca3af;
}
.pm-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.pm-total {
  font-size: 13px;
  color: #4b5563;
}
</style>
