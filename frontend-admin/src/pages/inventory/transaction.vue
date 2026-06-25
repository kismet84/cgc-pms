<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { stockIn, stockOut, getWarehouseList } from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { WarehouseVO } from '@/types/inventory'
import type { SelectOption } from '@/types/ui'

const activeTab = ref<'in' | 'out'>('in')

const warehouseList = ref<WarehouseVO[]>([])
const referenceStore = useReferenceStore()
const materialList = computed(() => referenceStore.materials ?? [])

const inForm = reactive({
  warehouseId: undefined as string | undefined,
  materialId: undefined as string | undefined,
  quantity: '',
})

const outForm = reactive({
  warehouseId: undefined as string | undefined,
  materialId: undefined as string | undefined,
  quantity: '',
})

const inSubmitting = ref(false)
const outSubmitting = ref(false)

async function fetchWarehouses() {
  try {
    const res = await getWarehouseList({ pageNo: 1, pageSize: 50, status: 'ENABLE' })
    warehouseList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    warehouseList.value = []
  }
}

function getMaterialInfo(id: string) {
  return materialList.value.find((m) => m.id === id)
}

async function handleStockIn() {
  if (!inForm.warehouseId) {
    message.warning('请选择仓库')
    return
  }
  if (!inForm.materialId) {
    message.warning('请选择物料')
    return
  }
  if (!inForm.quantity || parseFloat(inForm.quantity) <= 0) {
    message.warning('请输入有效的入库数量')
    return
  }

  inSubmitting.value = true
  try {
    await stockIn({
      warehouseId: inForm.warehouseId,
      materialId: inForm.materialId,
      quantity: inForm.quantity,
    })
    message.success('入库成功')
    inForm.materialId = undefined
    inForm.quantity = ''
  } catch (e: unknown) {
    console.error(e)
    message.error('入库失败，请稍后重试')
  } finally {
    inSubmitting.value = false
  }
}

async function handleStockOut() {
  if (!outForm.warehouseId) {
    message.warning('请选择仓库')
    return
  }
  if (!outForm.materialId) {
    message.warning('请选择物料')
    return
  }
  if (!outForm.quantity || parseFloat(outForm.quantity) <= 0) {
    message.warning('请输入有效的出库数量')
    return
  }

  outSubmitting.value = true
  try {
    await stockOut({
      warehouseId: outForm.warehouseId,
      materialId: outForm.materialId,
      quantity: outForm.quantity,
    })
    message.success('出库成功')
    outForm.materialId = undefined
    outForm.quantity = ''
  } catch (e: unknown) {
    console.error(e)
    message.error('出库失败，请稍后重试')
  } finally {
    outSubmitting.value = false
  }
}

onMounted(() => {
  fetchWarehouses()
  referenceStore.fetchMaterials()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <!-- 页面头部 -->
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>库存交易</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- KPI 横条 -->
    <div class="lg-kpi-strip">
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">入库操作</span>
        <span class="lg-kpi-card-value" style="color: #22c55e">快捷</span>
        <span class="lg-kpi-card-bar"><span style="width: 100%; background: #22c55e"></span></span>
      </div>
      <div class="lg-kpi-card is-warn">
        <span class="lg-kpi-card-label">出库操作</span>
        <span class="lg-kpi-card-value" style="color: #ef4444">快捷</span>
        <span class="lg-kpi-card-bar"><span style="width: 100%; background: #ef4444"></span></span>
      </div>
    </div>

    <div class="lg-toolbar" style="margin-bottom: 0">
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="in" tab="入库" />
        <a-tab-pane key="out" tab="出库" />
      </a-tabs>
    </div>

    <div class="lg-panel" style="padding: 24px 28px">
      <!-- Stock In Form -->
      <div v-if="activeTab === 'in'">
        <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 14 }">
          <a-form-item label="仓库" required>
            <a-select
              v-model:value="inForm.warehouseId"
              placeholder="请选择仓库"
              style="width: 300px"
              show-search
              :filter-option="
                (input: string, option: SelectOption) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
            >
              <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
                {{ w.warehouseName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="物料" required>
            <a-select
              v-model:value="inForm.materialId"
              placeholder="请选择物料"
              style="width: 300px"
              show-search
              :filter-option="
                (input: string, option: SelectOption) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
            >
              <a-select-option
                v-for="m in materialList"
                :key="m.id"
                :value="m.id"
                :label="m.materialName"
              >
                <div>
                  <span>{{ m.materialName }}</span>
                  <span style="color: #9ca3af; font-size: 12px; margin-left: 8px">{{
                    m.materialCode
                  }}</span>
                </div>
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="入库数量" required>
            <a-input-number
              v-model:value="inForm.quantity"
              :min="0.0001"
              :precision="4"
              style="width: 200px"
              placeholder="请输入数量"
              addon-after=""
            >
              <template #addonAfter>
                <span style="min-width: 30px; display: inline-block">
                  {{ getMaterialInfo(inForm.materialId ?? '')?.unit ?? '' }}
                </span>
              </template>
            </a-input-number>
          </a-form-item>
          <a-form-item :wrapper-col="{ offset: 4 }">
            <a-button
              type="primary"
              :loading="inSubmitting"
              @click="handleStockIn"
              style="width: 120px"
            >
              确认入库
            </a-button>
          </a-form-item>
        </a-form>
      </div>

      <!-- Stock Out Form -->
      <div v-else>
        <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 14 }">
          <a-form-item label="仓库" required>
            <a-select
              v-model:value="outForm.warehouseId"
              placeholder="请选择仓库"
              style="width: 300px"
              show-search
              :filter-option="
                (input: string, option: SelectOption) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
            >
              <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
                {{ w.warehouseName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="物料" required>
            <a-select
              v-model:value="outForm.materialId"
              placeholder="请选择物料"
              style="width: 300px"
              show-search
              :filter-option="
                (input: string, option: SelectOption) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
            >
              <a-select-option
                v-for="m in materialList"
                :key="m.id"
                :value="m.id"
                :label="m.materialName"
              >
                <div>
                  <span>{{ m.materialName }}</span>
                  <span style="color: #9ca3af; font-size: 12px; margin-left: 8px">{{
                    m.materialCode
                  }}</span>
                </div>
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="出库数量" required>
            <a-input-number
              v-model:value="outForm.quantity"
              :min="0.0001"
              :precision="4"
              style="width: 200px"
              placeholder="请输入数量"
            >
              <template #addonAfter>
                <span style="min-width: 30px; display: inline-block">
                  {{ getMaterialInfo(outForm.materialId ?? '')?.unit ?? '' }}
                </span>
              </template>
            </a-input-number>
          </a-form-item>
          <a-form-item :wrapper-col="{ offset: 4 }">
            <a-button
              type="primary"
              danger
              :loading="outSubmitting"
              @click="handleStockOut"
              style="width: 120px"
            >
              确认出库
            </a-button>
          </a-form-item>
        </a-form>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
