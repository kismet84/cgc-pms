<script setup lang="ts">
import { computed } from 'vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { ContractItem } from '@/types/contract'

/** 编辑器使用的行类型：id 可为空（新增行使用临时 key） */
export type EditableContractItem = Partial<ContractItem> & { _key: string }

const items = defineModel<EditableContractItem[]>({ default: () => [] })

function genKey(): string {
  return `item_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function addRow() {
  items.value.push({
    _key: genKey(),
    itemName: '',
    itemSpec: '',
    unit: '',
    quantity: 0,
    unitPrice: '0',
    amount: '0',
    taxRate: 9,
    taxAmount: '0',
    amountWithoutTax: '0',
    sortOrder: items.value.length + 1,
  })
}

function removeRow(key: string) {
  const idx = items.value.findIndex((r) => r._key === key)
  if (idx !== -1) items.value.splice(idx, 1)
}

/** 重新计算单行金额：amount = quantity * unitPrice，taxAmount = amount * taxRate，amountWithoutTax = amount - taxAmount */
function recalc(row: EditableContractItem) {
  const qty = Number(row.quantity) || 0
  const price = Number(row.unitPrice) || 0
  const amount = qty * price
  const taxRate = Number(row.taxRate) || 0
  const taxAmount = (amount * taxRate) / 100
  row.amount = amount.toFixed(2)
  row.taxAmount = taxAmount.toFixed(2)
  row.amountWithoutTax = (amount - taxAmount).toFixed(2)
}

const totalAmount = computed(() =>
  items.value.reduce((sum, r) => sum + (Number(r.amount) || 0), 0).toFixed(2),
)
const totalTax = computed(() =>
  items.value.reduce((sum, r) => sum + (Number(r.taxAmount) || 0), 0).toFixed(2),
)

const columns = [
  { title: '序号', dataIndex: 'index', width: 60, align: 'center' as const },
  { title: '名称', dataIndex: 'itemName', minWidth: 160 },
  { title: '规格型号', dataIndex: 'itemSpec', width: 140 },
  { title: '单位', dataIndex: 'unit', width: 90 },
  { title: '数量', dataIndex: 'quantity', width: 110, align: 'right' as const },
  { title: '单价(元)', dataIndex: 'unitPrice', width: 130, align: 'right' as const },
  { title: '金额(元)', dataIndex: 'amount', width: 130, align: 'right' as const },
  { title: '税率(%)', dataIndex: 'taxRate', width: 100, align: 'right' as const },
  { title: '税额(元)', dataIndex: 'taxAmount', width: 130, align: 'right' as const },
  { title: '操作', dataIndex: 'ops', width: 70, align: 'center' as const, fixed: 'right' as const },
]

function fmtMoney(val: string | number | undefined): string {
  const n = Number(val) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

defineExpose({ addRow })
</script>

<template>
  <div class="item-editor">
    <div class="ie-toolbar">
      <a-button type="dashed" size="small" @click="addRow">
        <template #icon><PlusOutlined /></template>
        添加明细
      </a-button>
      <span class="ie-hint">金额 = 数量 × 单价，税额 = 金额 × 税率</span>
    </div>

    <a-table
      :data-source="items"
      :columns="columns"
      :pagination="false"
      row-key="_key"
      size="small"
      bordered
      :scroll="{ x: 1100 }"
    >
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.dataIndex === 'index'">
          {{ index + 1 }}
        </template>
        <template v-else-if="column.dataIndex === 'itemName'">
          <a-input v-model:value="record.itemName" placeholder="请输入名称" size="small" />
        </template>
        <template v-else-if="column.dataIndex === 'itemSpec'">
          <a-input v-model:value="record.itemSpec" placeholder="规格" size="small" />
        </template>
        <template v-else-if="column.dataIndex === 'unit'">
          <a-input v-model:value="record.unit" placeholder="单位" size="small" />
        </template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <a-input-number
            v-model:value="record.quantity"
            :min="0"
            :precision="2"
            size="small"
            style="width: 100%"
            @change="recalc(record)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'unitPrice'">
          <a-input-number
            v-model:value="record.unitPrice"
            :min="0"
            :precision="2"
            size="small"
            style="width: 100%"
            @change="recalc(record)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'amount'">
          <span class="ie-money">{{ fmtMoney(record.amount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'taxRate'">
          <a-input-number
            v-model:value="record.taxRate"
            :min="0"
            :max="100"
            :precision="0"
            size="small"
            style="width: 100%"
            @change="recalc(record)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'taxAmount'">
          <span class="ie-money">{{ fmtMoney(record.taxAmount) }}</span>
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
        <div class="ie-footer">
          <span>合计：</span>
          <span class="ie-total">金额 {{ fmtMoney(totalAmount) }} 元</span>
          <span class="ie-total">税额 {{ fmtMoney(totalTax) }} 元</span>
        </div>
      </template>
    </a-table>

    <a-empty
      v-if="items.length === 0"
      description="暂无明细，点击“添加明细”新增"
      class="ie-empty"
    />
  </div>
</template>

<style scoped>
.item-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ie-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
}
.ie-hint {
  font-size: 12px;
  color: var(--muted);
}
.ie-money {
  font-variant-numeric: tabular-nums;
  color: var(--text);
}
.ie-footer {
  display: flex;
  align-items: center;
  gap: 18px;
  justify-content: flex-end;
  font-size: 13px;
  color: var(--text);
}
.ie-total {
  font-weight: 600;
}
.ie-empty {
  padding: 12px 0;
}
</style>
