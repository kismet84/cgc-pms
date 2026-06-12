<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { stockIn, stockOut, getWarehouseList } from '@/api/modules/inventory'
import { getMaterialList } from '@/api/modules/material'
import type { WarehouseVO } from '@/types/inventory'
import type { MaterialVO } from '@/types/material'

const activeTab = ref<'in' | 'out'>('in')

const warehouseList = ref<WarehouseVO[]>([])
const materialList = ref<MaterialVO[]>([])

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
    const res = await getWarehouseList({ pageNo: 1, pageSize: 500, status: 'ENABLE' })
    warehouseList.value = res.records
  } catch {
    warehouseList.value = []
  }
}

async function fetchMaterials() {
  try {
    const res = await getMaterialList({ pageNum: 1, pageSize: 500, status: 'ENABLE' })
    materialList.value = res.records
  } catch {
    materialList.value = []
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
  } catch {
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
  } catch {
    message.error('出库失败，请稍后重试')
  } finally {
    outSubmitting.value = false
  }
}

onMounted(() => {
  fetchWarehouses()
  fetchMaterials()
})
</script>

<template>
  <div class="pm-page">
    <a-page-header title="出入库操作" class="pm-header" />

    <div class="pm-card" style="padding: 0">
      <a-tabs v-model:activeKey="activeTab" style="padding: 0 22px">
        <a-tab-pane key="in" tab="入库" />
        <a-tab-pane key="out" tab="出库" />
      </a-tabs>

      <div style="padding: 0 22px 24px">
        <!-- Stock In Form -->
        <div v-if="activeTab === 'in'">
          <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 14 }">
            <a-form-item label="仓库" required>
              <a-select v-model:value="inForm.warehouseId" placeholder="请选择仓库" style="width: 300px">
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
                :filter-option="(input: string, option: any) => option.label?.toLowerCase().includes(input.toLowerCase())"
              >
                <a-select-option
                  v-for="m in materialList"
                  :key="m.id"
                  :value="m.id"
                  :label="m.materialName"
                >
                  <div>
                    <span>{{ m.materialName }}</span>
                    <span style="color: #9ca3af; font-size: 12px; margin-left: 8px">{{ m.materialCode }}</span>
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
              <a-button type="primary" :loading="inSubmitting" @click="handleStockIn" style="width: 120px">
                确认入库
              </a-button>
            </a-form-item>
          </a-form>
        </div>

        <!-- Stock Out Form -->
        <div v-else>
          <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 14 }">
            <a-form-item label="仓库" required>
              <a-select v-model:value="outForm.warehouseId" placeholder="请选择仓库" style="width: 300px">
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
                :filter-option="(input: string, option: any) => option.label?.toLowerCase().includes(input.toLowerCase())"
              >
                <a-select-option
                  v-for="m in materialList"
                  :key="m.id"
                  :value="m.id"
                  :label="m.materialName"
                >
                  <div>
                    <span>{{ m.materialName }}</span>
                    <span style="color: #9ca3af; font-size: 12px; margin-left: 8px">{{ m.materialCode }}</span>
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
              <a-button type="primary" danger :loading="outSubmitting" @click="handleStockOut" style="width: 120px">
                确认出库
              </a-button>
            </a-form-item>
          </a-form>
        </div>
      </div>
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
</style>
