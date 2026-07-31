<script setup lang="ts">
import { computed } from 'vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { ContractPaymentTerm } from '@/types/contract'

/** 编辑器使用的行类型：id 可为空（新增行使用临时 key） */
export type EditablePaymentTerm = Partial<ContractPaymentTerm> & { _key: string }

const terms = defineModel<EditablePaymentTerm[]>({ default: () => [] })

function genKey(): string {
  return `term_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function addRow() {
  terms.value.push({
    _key: genKey(),
    termName: '',
    paymentRatio: 0,
    paymentAmount: '0',
    paymentCondition: '',
    plannedDate: '',
    termStatus: 'PENDING',
    sortOrder: terms.value.length + 1,
  })
}

function removeRow(key: string) {
  const idx = terms.value.findIndex((r) => r._key === key)
  if (idx !== -1) terms.value.splice(idx, 1)
}

const totalRatio = computed(() =>
  terms.value.reduce((sum, r) => sum + (Number(r.paymentRatio) || 0), 0).toFixed(1),
)
const totalAmount = computed(() =>
  terms.value.reduce((sum, r) => sum + (Number(r.paymentAmount) || 0), 0).toFixed(2),
)

const columns = [
  { title: '序号', dataIndex: 'index', width: 60, align: 'center' as const },
  { title: '付款节点', dataIndex: 'termName', minWidth: 160 },
  { title: '付款比例(%)', dataIndex: 'paymentRatio', width: 120, align: 'right' as const },
  { title: '付款金额(元)', dataIndex: 'paymentAmount', width: 140, align: 'right' as const },
  { title: '付款条件', dataIndex: 'paymentCondition', minWidth: 180 },
  { title: '计划付款日期', dataIndex: 'plannedDate', width: 150 },
  { title: '操作', dataIndex: 'ops', width: 76, align: 'center' as const },
]

function fmtMoney(val: string | number | undefined): string {
  const n = Number(val) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

defineExpose({ addRow })
</script>

<template>
  <div class="term-editor">
    <div class="te-toolbar">
      <a-button type="dashed" size="small" @click="addRow">
        <template #icon><PlusOutlined /></template>
        添加付款条款
      </a-button>
      <span class="te-hint">付款比例合计应为 100%</span>
    </div>

    <a-table
      :data-source="terms"
      :columns="columns"
      :pagination="false"
      row-key="_key"
      size="small"
      bordered
      :scroll="{ x: 1000 }"
    >
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.dataIndex === 'index'">
          {{ index + 1 }}
        </template>
        <template v-else-if="column.dataIndex === 'termName'">
          <a-input v-model:value="record.termName" placeholder="如：预付款、进度款" size="small" />
        </template>
        <template v-else-if="column.dataIndex === 'paymentRatio'">
          <a-input-number
            v-model:value="record.paymentRatio"
            :min="0"
            :max="100"
            :precision="1"
            size="small"
            style="width: 100%"
          />
        </template>
        <template v-else-if="column.dataIndex === 'paymentAmount'">
          <a-input-number
            v-model:value="record.paymentAmount"
            :min="0"
            :precision="2"
            size="small"
            style="width: 100%"
          />
        </template>
        <template v-else-if="column.dataIndex === 'paymentCondition'">
          <a-input
            v-model:value="record.paymentCondition"
            placeholder="付款触发条件"
            size="small"
          />
        </template>
        <template v-else-if="column.dataIndex === 'plannedDate'">
          <a-date-picker
            v-model:value="record.plannedDate"
            size="small"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </template>
        <template v-else-if="column.dataIndex === 'ops'">
          <a-popconfirm title="确认删除该行？" @confirm="removeRow(record._key)">
            <a-button type="text" danger size="small">
              <template #icon><DeleteOutlined /></template>
            </a-button>
          </a-popconfirm>
        </template>
      </template>

      <template #footer>
        <div class="te-footer">
          <span>合计：</span>
          <span class="te-total" :class="{ 'te-warn': Number(totalRatio) !== 100 }">
            比例 {{ totalRatio }}%
          </span>
          <span class="te-total">金额 {{ fmtMoney(totalAmount) }} 元</span>
        </div>
      </template>
    </a-table>

    <a-empty
      v-if="terms.length === 0"
      description="暂无付款条款，点击上方按钮新增"
      class="te-empty"
    />
  </div>
</template>

<style scoped>
.term-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.te-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
}
.te-hint {
  font-size: 12px;
  color: var(--muted);
}
.te-footer {
  display: flex;
  align-items: center;
  gap: 18px;
  justify-content: flex-end;
  font-size: 13px;
  color: var(--text);
}
.te-total {
  font-weight: 600;
}
.te-warn {
  color: #f59e0b;
}
.te-empty {
  padding: 12px 0;
}
</style>
