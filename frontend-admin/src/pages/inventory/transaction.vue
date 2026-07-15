<script setup lang="ts">
import {
  AppstoreOutlined,
  FilterOutlined,
  InboxOutlined,
  LoginOutlined,
  LogoutOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { stockIn, stockOut, getWarehouseList } from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import type { WarehouseVO } from '@/types/inventory'
import type { SelectOption } from '@/types/ui'

const activeTab = ref<'in' | 'out'>('in')
const filterPanelOpen = ref(false)

const warehouseList = ref<WarehouseVO[]>([])
const referenceStore = useReferenceStore()
const userStore = useUserStore()
const materialList = computed(() => referenceStore.materials ?? [])
const canSubmitTransaction = computed(() => userStore.hasPermission('inventory:transaction:add'))
const keyword = ref('')
const appliedKeyword = ref('')

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
const inInlineError = ref('')
const outInlineError = ref('')

const activeForm = computed(() => (activeTab.value === 'in' ? inForm : outForm))
const filteredMaterials = computed(() => {
  const value = appliedKeyword.value.trim().toLowerCase()
  if (!value) return materialList.value
  return materialList.value.filter((item) =>
    `${item.materialName ?? ''} ${item.materialCode ?? ''}`.toLowerCase().includes(value),
  )
})

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

function handleSearch() {
  appliedKeyword.value = keyword.value.trim()
}

function handleReset() {
  keyword.value = ''
  appliedKeyword.value = ''
  activeForm.value.warehouseId = undefined
  activeForm.value.materialId = undefined
  activeForm.value.quantity = ''
  if (activeTab.value === 'in') {
    inInlineError.value = ''
    return
  }
  outInlineError.value = ''
}

function getTransactionError(
  form: { warehouseId?: string; materialId?: string; quantity: string },
  action: '入库' | '出库',
) {
  if (!form.warehouseId) return '请选择仓库'
  if (!form.materialId) return '请选择物料'
  if (!form.quantity || parseFloat(form.quantity) <= 0) return `请输入有效的${action}数量`
  return ''
}

async function handleStockIn() {
  const error = getTransactionError(inForm, '入库')
  inInlineError.value = error
  if (error) {
    message.warning(error)
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
    inInlineError.value = ''
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
  const error = getTransactionError(outForm, '出库')
  outInlineError.value = error
  if (error) {
    message.warning(error)
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
    outInlineError.value = ''
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
  handleSearch()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page transaction-page procurement-subcontract-list-page">
    <!-- 页面头部 -->
    <div class="lg-page-head transaction-page-head">
      <div class="transaction-page-meta">
        <a-breadcrumb class="transaction-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>出入库记录</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid transaction-workspace">
      <div class="lg-left">
        <div class="lg-kpi-strip">
          <div class="lg-kpi-card transaction-kpi-card">
            <span class="transaction-kpi-icon is-green"><LoginOutlined /></span>
            <span class="transaction-kpi-copy">
              <span class="lg-kpi-card-label">入库操作</span>
              <span class="lg-kpi-card-value" style="color: #22c55e">登记</span>
            </span>
          </div>
          <div class="lg-kpi-card is-warn transaction-kpi-card">
            <span class="transaction-kpi-icon is-red"><LogoutOutlined /></span>
            <span class="transaction-kpi-copy">
              <span class="lg-kpi-card-label">出库操作</span>
              <span class="lg-kpi-card-value" style="color: #ef4444">登记</span>
            </span>
          </div>
          <div class="lg-kpi-card transaction-kpi-card">
            <span class="transaction-kpi-icon is-blue"><InboxOutlined /></span>
            <span class="transaction-kpi-copy">
              <span class="lg-kpi-card-label">可用仓库</span>
              <span class="lg-kpi-card-value">{{ warehouseList.length }} <small>个</small></span>
            </span>
          </div>
          <div class="lg-kpi-card transaction-kpi-card">
            <span class="transaction-kpi-icon is-purple"><AppstoreOutlined /></span>
            <span class="transaction-kpi-copy">
              <span class="lg-kpi-card-label">物料范围</span>
              <span class="lg-kpi-card-value">{{ materialList.length }} <small>项</small></span>
            </span>
          </div>
        </div>

        <div class="lg-search-bar transaction-search-bar procurement-subcontract-query-panel">
          <div
            class="transaction-search-fields procurement-subcontract-filter-panel"
            :class="{ 'is-open': filterPanelOpen }"
          >
            <a-select
              v-model:value="activeForm.warehouseId"
              placeholder="选择仓库"
              allow-clear
              class="transaction-search-select"
              size="large"
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
            <a-select
              v-model:value="activeForm.materialId"
              placeholder="选择物料"
              allow-clear
              class="transaction-search-select"
              size="large"
              show-search
              :filter-option="
                (input: string, option: SelectOption) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
            >
              <a-select-option
                v-for="m in filteredMaterials"
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
          </div>
          <div class="transaction-search-keyword-row procurement-subcontract-query-row">
            <a-input
              v-model:value="keyword"
              class="transaction-search-input"
              placeholder="按物料名称或编码筛选候选项"
              allow-clear
              size="large"
              @pressEnter="handleSearch"
            >
              <template #prefix>
                <SearchOutlined />
              </template>
            </a-input>
            <div class="transaction-search-actions procurement-subcontract-query-actions">
              <a-button
                class="procurement-subcontract-desktop-action"
                type="primary"
                size="large"
                @click="handleSearch"
                >搜索</a-button
              >
              <a-button
                class="procurement-subcontract-desktop-action"
                size="large"
                @click="handleReset"
                >重置</a-button
              >
              <a-button
                class="procurement-subcontract-filter-toggle"
                size="large"
                :aria-expanded="filterPanelOpen"
                @click="filterPanelOpen = !filterPanelOpen"
              >
                <template #icon><FilterOutlined /></template>
                筛选
              </a-button>
            </div>
          </div>
        </div>

        <main
          class="lg-list-table-panel transaction-form-panel procurement-subcontract-table-panel"
        >
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <span class="transaction-section-title">库存变动登记</span>
            </div>
            <div class="lg-toolbar-right">
              <a-tabs v-model:activeKey="activeTab" class="transaction-tabs">
                <a-tab-pane key="in" tab="入库" />
                <a-tab-pane key="out" tab="出库" />
              </a-tabs>
              <a-button
                v-if="canSubmitTransaction"
                v-show="activeTab === 'in'"
                type="primary"
                :loading="inSubmitting"
                @click="handleStockIn"
              >
                确认入库
              </a-button>
              <a-button
                v-if="canSubmitTransaction"
                v-show="activeTab === 'out'"
                type="primary"
                danger
                :loading="outSubmitting"
                @click="handleStockOut"
              >
                确认出库
              </a-button>
            </div>
          </div>

          <div class="transaction-form-body">
            <!-- Stock In Form -->
            <div v-if="activeTab === 'in'">
              <a-form layout="vertical" class="transaction-form">
                <a-alert
                  v-if="inInlineError"
                  class="transaction-form-alert"
                  type="warning"
                  show-icon
                  :message="inInlineError"
                />
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
                <div class="transaction-form-tip">
                  <span>
                    仓库：{{
                      warehouseList.find((w) => w.id === inForm.warehouseId)?.warehouseName ||
                      '未选择'
                    }}
                  </span>
                  <span>
                    物料：{{ getMaterialInfo(inForm.materialId ?? '')?.materialName || '未选择' }}
                  </span>
                </div>
              </a-form>
            </div>

            <!-- Stock Out Form -->
            <div v-else>
              <a-form layout="vertical" class="transaction-form">
                <a-alert
                  v-if="outInlineError"
                  class="transaction-form-alert"
                  type="warning"
                  show-icon
                  :message="outInlineError"
                />
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
                <div class="transaction-form-tip">
                  <span>
                    仓库：{{
                      warehouseList.find((w) => w.id === outForm.warehouseId)?.warehouseName ||
                      '未选择'
                    }}
                  </span>
                  <span>
                    物料：{{ getMaterialInfo(outForm.materialId ?? '')?.materialName || '未选择' }}
                  </span>
                </div>
              </a-form>
            </div>
          </div>
        </main>
      </div>

      <aside class="lg-analysis-rail transaction-rail procurement-subcontract-analysis-rail">
        <section class="lg-analysis-panel transaction-analysis-panel transaction-rail-card">
          <header class="transaction-analysis-head lg-analysis-header">
            <div>
              <div class="transaction-rail-title lg-analysis-heading">辅助分析</div>
              <div class="transaction-analysis-subtitle lg-analysis-description">
                操作提示与库存域入口
              </div>
            </div>
          </header>
          <section class="transaction-analysis-section">
            <div class="transaction-rail-title">操作提示</div>
            <ul class="transaction-rail-list">
              <li><span>入库</span><b>增加可用库存</b></li>
              <li><span>出库</span><b>扣减可用库存</b></li>
              <li>
                <span>权限</span><b>{{ canSubmitTransaction ? '可提交' : '仅查看' }}</b>
              </li>
            </ul>
          </section>
          <section class="transaction-analysis-section">
            <div class="transaction-rail-title">库存域入口</div>
            <div class="transaction-rail-text">
              登记完成后可返回库存台账，按仓库、物料或来源单据搜索流水。
            </div>
          </section>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.transaction-page-head {
  min-height: 0;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.transaction-page-meta {
  display: flex;
  align-items: center;
}

.transaction-breadcrumb {
  font-size: 13px;
}

.transaction-workspace {
}

.transaction-workspace .lg-left {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.transaction-workspace .lg-kpi-strip {
  flex: 0 0 auto;
  margin: 0;
}

.transaction-search-bar {
  align-items: stretch;
  flex-wrap: wrap;
  width: 100%;
  margin: 0;
}

.transaction-search-fields,
.transaction-search-keyword-row {
  display: flex;
  flex: 1 0 100%;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  min-width: 0;
  width: 100%;
}

.transaction-search-fields > :deep(.ant-select) {
  flex: 1 1 180px;
  min-width: 0;
}

.transaction-search-select {
  width: 100%;
}

.transaction-search-keyword-row > :deep(.ant-input-affix-wrapper) {
  flex: 1 1 320px;
  min-width: 0;
}

.transaction-search-input {
  flex: 1 1 320px;
  min-width: 0;
}

.transaction-search-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 8px;
}

.transaction-workspace .transaction-kpi-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 16px;
}

.transaction-workspace .transaction-kpi-card::after,
.transaction-workspace .transaction-kpi-card .lg-kpi-card-label::before {
  display: none;
}

.transaction-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  font-size: 16px;
}

.transaction-kpi-icon.is-green {
  color: var(--success);
  background: var(--success-soft);
}

.transaction-kpi-icon.is-red {
  color: var(--error);
  background: var(--error-soft);
}

.transaction-kpi-icon.is-blue {
  color: var(--primary);
  background: var(--primary-soft);
}

.transaction-kpi-icon.is-purple {
  color: #7c3aed;
  background: #f3e8ff;
}

.transaction-kpi-copy {
  display: grid;
  grid-template-rows: 18px 28px;
  align-content: center;
  row-gap: 4px;
  min-width: 0;
}

.transaction-workspace .transaction-kpi-card .lg-kpi-card-label,
.transaction-workspace .transaction-kpi-card .lg-kpi-card-value {
  min-width: 0;
}

.transaction-form-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
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
  flex: 1;
  min-height: 0;
  padding: 20px;
  background: var(--surface);
}

.transaction-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: stretch;
  width: min(100%, 360px);
}

.transaction-form :deep(.ant-form-item) {
  margin-bottom: 0;
}

.transaction-form-alert {
  margin-bottom: 4px;
}

.transaction-quantity {
  width: 100%;
}

.transaction-form-tip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
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
  min-height: 0;
}

.transaction-rail-card {
  padding: 0;
}

.transaction-analysis-panel {
  flex: 1;
  overflow: auto;
}

.transaction-analysis-head {
  margin-bottom: 0;
}

.transaction-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
}

.transaction-analysis-section + .transaction-analysis-section {
  border-top: 1px solid var(--border-subtle);
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
  .transaction-search-fields,
  .transaction-search-keyword-row {
    flex-direction: column;
  }
}

@media (max-width: 720px) {
  .transaction-form {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .transaction-workspace .lg-kpi-strip,
  .transaction-rail {
    display: none;
  }
}

@media (min-width: 769px) {
}
</style>
