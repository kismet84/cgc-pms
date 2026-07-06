<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { stockIn, stockOut, getWarehouseList } from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import type { WarehouseVO } from '@/types/inventory'
import type { SelectOption } from '@/types/ui'

const activeTab = ref<'in' | 'out'>('in')

const warehouseList = ref<WarehouseVO[]>([])
const referenceStore = useReferenceStore()
const userStore = useUserStore()
const materialList = computed(() => referenceStore.materials ?? [])
const canSubmitTransaction = computed(() => userStore.hasPermission('inventory:transaction:add'))

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
  <div class="lg-list-page lg-page app-page transaction-page">
    <!-- 页面头部 -->
    <div class="lg-page-head transaction-page-head">
      <div class="transaction-page-meta">
        <a-breadcrumb class="transaction-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>出入库记录</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="transaction-page-title">出入库操作录入</div>
        <span class="transaction-page-subtitle">只负责入库、出库登记；流水查询与详情在库存台账查看。</span>
      </div>
      <div class="transaction-head-digest">
        <div>
          <span>可用仓库</span>
          <strong>{{ warehouseList.length }}个</strong>
        </div>
        <div>
          <span>物料范围</span>
          <strong>{{ materialList.length }}项</strong>
        </div>
        <div>
          <span>操作权限</span>
          <strong>{{ canSubmitTransaction ? '可提交' : '仅查看' }}</strong>
        </div>
      </div>
    </div>

    <div class="lg-grid transaction-workspace">
      <div class="lg-left">
        <div class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">入库操作</span>
            <span class="lg-kpi-card-value" style="color: #22c55e">登记</span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">出库操作</span>
            <span class="lg-kpi-card-value" style="color: #ef4444">登记</span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">可用仓库</span>
            <span class="lg-kpi-card-value">{{ warehouseList.length }} <small>个</small></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">物料范围</span>
            <span class="lg-kpi-card-value">{{ materialList.length }} <small>项</small></span>
          </div>
        </div>

        <main class="lg-list-table-panel transaction-form-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <span class="transaction-section-title">库存变动登记</span>
            </div>
            <div class="lg-toolbar-right">
              <a-tabs v-model:activeKey="activeTab" class="transaction-tabs">
                <a-tab-pane key="in" tab="入库" />
                <a-tab-pane key="out" tab="出库" />
              </a-tabs>
            </div>
          </div>

          <div class="transaction-form-body">
            <!-- Stock In Form -->
            <div v-if="activeTab === 'in'">
              <a-form layout="vertical" class="transaction-form">
                <a-form-item label="仓库" required>
                  <a-select
                    v-model:value="inForm.warehouseId"
                    placeholder="请选择仓库"
                    show-search
                    :filter-option="
                      (input: string, option: SelectOption) =>
                        option.label?.toLowerCase().includes(input.toLowerCase())
                    "
                  >
                    <a-select-option
                      v-for="w in warehouseList"
                      :key="w.id"
                      :value="w.id"
                      :label="w.warehouseName"
                    >
                      {{ w.warehouseName }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
                <a-form-item label="物料" required>
                  <a-select
                    v-model:value="inForm.materialId"
                    placeholder="请选择物料"
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
                      :label="`${m.materialName} ${m.materialCode ?? ''}`"
                    >
                      <div>
                        <span>{{ m.materialName }}</span>
                        <span class="transaction-option-code">{{ m.materialCode }}</span>
                      </div>
                    </a-select-option>
                  </a-select>
                </a-form-item>
                <a-form-item label="入库数量" required>
                  <a-input-number
                    v-model:value="inForm.quantity"
                    :min="0.0001"
                    :precision="4"
                    class="transaction-quantity"
                    placeholder="请输入数量"
                    addon-after=""
                  >
                    <template #addonAfter>
                      <span class="transaction-unit">
                        {{ getMaterialInfo(inForm.materialId ?? '')?.unit ?? '' }}
                      </span>
                    </template>
                  </a-input-number>
                </a-form-item>
                <a-form-item>
                  <a-button
                    v-if="canSubmitTransaction"
                    type="primary"
                    :loading="inSubmitting"
                    @click="handleStockIn"
                  >
                    确认入库
                  </a-button>
                </a-form-item>
              </a-form>
            </div>

            <!-- Stock Out Form -->
            <div v-else>
              <a-form layout="vertical" class="transaction-form">
                <a-form-item label="仓库" required>
                  <a-select
                    v-model:value="outForm.warehouseId"
                    placeholder="请选择仓库"
                    show-search
                    :filter-option="
                      (input: string, option: SelectOption) =>
                        option.label?.toLowerCase().includes(input.toLowerCase())
                    "
                  >
                    <a-select-option
                      v-for="w in warehouseList"
                      :key="w.id"
                      :value="w.id"
                      :label="w.warehouseName"
                    >
                      {{ w.warehouseName }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
                <a-form-item label="物料" required>
                  <a-select
                    v-model:value="outForm.materialId"
                    placeholder="请选择物料"
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
                      :label="`${m.materialName} ${m.materialCode ?? ''}`"
                    >
                      <div>
                        <span>{{ m.materialName }}</span>
                        <span class="transaction-option-code">{{ m.materialCode }}</span>
                      </div>
                    </a-select-option>
                  </a-select>
                </a-form-item>
                <a-form-item label="出库数量" required>
                  <a-input-number
                    v-model:value="outForm.quantity"
                    :min="0.0001"
                    :precision="4"
                    class="transaction-quantity"
                    placeholder="请输入数量"
                  >
                    <template #addonAfter>
                      <span class="transaction-unit">
                        {{ getMaterialInfo(outForm.materialId ?? '')?.unit ?? '' }}
                      </span>
                    </template>
                  </a-input-number>
                </a-form-item>
                <a-form-item>
                  <a-button
                    v-if="canSubmitTransaction"
                    type="primary"
                    danger
                    :loading="outSubmitting"
                    @click="handleStockOut"
                  >
                    确认出库
                  </a-button>
                </a-form-item>
              </a-form>
            </div>
          </div>
        </main>
      </div>

      <aside class="lg-analysis-rail transaction-rail">
        <section class="transaction-analysis-panel transaction-rail-card">
          <header class="transaction-analysis-head">
            <div>
              <div class="transaction-rail-title">操作提示</div>
              <div class="transaction-analysis-subtitle">入库、出库与权限状态</div>
            </div>
          </header>
          <ul class="transaction-rail-list">
            <li><span>入库</span><b>增加可用库存</b></li>
            <li><span>出库</span><b>扣减可用库存</b></li>
            <li>
              <span>权限</span><b>{{ canSubmitTransaction ? '可提交' : '仅查看' }}</b>
            </li>
          </ul>
        </section>
        <section class="transaction-analysis-panel transaction-rail-card">
          <div class="transaction-rail-title">库存域入口</div>
          <div class="transaction-rail-text">
            登记完成后可返回库存台账，按仓库、物料或来源单据搜索流水。
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.transaction-page-head {
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 0;
  padding: 18px 20px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-left: 4px solid var(--primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.transaction-page-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.transaction-breadcrumb {
  font-size: 13px;
}

.transaction-page-title {
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 32px;
}

.transaction-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
}

.transaction-head-digest {
  display: grid;
  grid-template-columns: repeat(3, minmax(88px, 1fr));
  gap: 10px;
  min-width: 360px;
}

.transaction-head-digest > div {
  padding: 10px 12px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.transaction-head-digest span {
  color: var(--text-secondary);
  font-size: 13px;
}

.transaction-head-digest strong {
  display: block;
  margin-top: 3px;
  color: var(--text);
  font-size: 17px;
  font-weight: 800;
  line-height: 22px;
}

.transaction-workspace {
  margin-top: 14px;
}

.transaction-form-panel {
  min-height: 360px;
  border-top: 3px solid var(--primary);
}

.transaction-section-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.transaction-tabs {
  margin-bottom: -16px;
}

.transaction-tabs :deep(.ant-tabs-nav) {
  margin: 0;
}

.transaction-form-body {
  padding: 20px;
  background: var(--surface);
}

.transaction-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr)) auto;
  gap: 12px 16px;
  align-items: end;
}

.transaction-form :deep(.ant-form-item) {
  margin-bottom: 0;
}

.transaction-quantity {
  width: 100%;
}

.transaction-option-code,
.transaction-unit {
  color: var(--muted);
  font-size: 12px;
}

.transaction-option-code {
  margin-left: 8px;
}

.transaction-unit {
  display: inline-block;
  min-width: 30px;
}

.transaction-rail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.transaction-rail-card {
  padding: 16px;
}

.transaction-analysis-panel {
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.transaction-analysis-head {
  margin-bottom: 12px;
}

.transaction-rail-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.transaction-analysis-subtitle {
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 12px;
}

.transaction-rail-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.transaction-rail-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--muted);
  font-size: 13px;
}

.transaction-rail-list b {
  color: var(--text);
  font-weight: 700;
}

.transaction-rail-text {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 1100px) {
  .transaction-page-head {
    align-items: stretch;
    flex-direction: column;
  }

  .transaction-head-digest {
    width: 100%;
    min-width: 0;
    grid-template-columns: 1fr;
  }

  .transaction-form {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .transaction-form {
    grid-template-columns: 1fr;
  }
}
</style>
