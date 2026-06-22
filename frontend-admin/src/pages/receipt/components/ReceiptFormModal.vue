<script setup lang="ts">
import type { MatReceiptVO, MatReceiptItemVO } from '@/types/receipt'
import type { MatPurchaseOrderVO } from '@/types/purchase'
import type { ProjectVO } from '@/types/project'
import type { ContractVO } from '@/types/contract'
import type { PartnerVO } from '@/types/partner'

defineProps<{
  visible: boolean
  title: string
  formData: Partial<MatReceiptVO>
  projectList: ProjectVO[]
  orderList: MatPurchaseOrderVO[]
  contractList: ContractVO[]
  partnerList: PartnerVO[]
  itemList: (Partial<MatReceiptItemVO> & { key: number; warning?: boolean })[]
  hasWarning: boolean
  itemsTotalAmount: string
}>()

const emit = defineEmits<{
  ok: []
  cancel: []
  'update:visible': [value: boolean]
  orderChange: [orderId: string | undefined]
  itemQtyChange: [index: number]
  itemPriceChange: [index: number]
  itemQualifiedQtyChange: [index: number]
}>()
</script>

<template>
  <a-modal :open="visible" :title="title" :width="1000" @ok="emit('ok')" @cancel="emit('cancel')">
    <!-- Header Form -->
    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
      <a-form-item label="项目" required>
        <a-select v-model:value="formData.projectId" placeholder="请选择项目">
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="采购订单">
        <a-select
          v-model:value="formData.orderId"
          placeholder="请选择采购订单"
          allow-clear
          @change="(val: string) => emit('orderChange', val)"
        >
          <a-select-option v-for="o in orderList" :key="o.id" :value="o.id">
            {{ o.orderCode }} - {{ o.projectName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="采购合同">
        <a-select v-model:value="formData.contractId" placeholder="自动填充" disabled>
          <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
            {{ c.contractName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="供应商">
        <a-select v-model:value="formData.partnerId" placeholder="自动填充" disabled>
          <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
            {{ p.partnerName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="验收日期">
        <a-date-picker
          v-model:value="formData.receiptDate"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="质量状态">
        <a-select v-model:value="formData.qualityStatus" placeholder="请选择质量状态" allow-clear>
          <a-select-option value="QUALIFIED">合格</a-select-option>
          <a-select-option value="PARTIAL">部分合格</a-select-option>
          <a-select-option value="UNQUALIFIED">不合格</a-select-option>
          <a-select-option value="PENDING">待检验</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model:value="formData.remark" :rows="2" placeholder="请输入备注" />
      </a-form-item>
    </a-form>

    <!-- Line Items Section -->
    <div style="border-top: 1px solid #f0f0f0; padding-top: 12px; margin-top: 4px">
      <div
        style="
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 10px;
        "
      >
        <span style="font-weight: 600; font-size: 14px">验收明细</span>
        <span style="font-size: 12px; color: #9ca3af">（选择采购订单后自动加载订单明细）</span>
      </div>

      <!-- Quantity warning -->
      <a-alert
        v-if="hasWarning"
        message="部分验收数量超过采购订单剩余数量，核实后可继续保存"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 10px"
      />

      <a-table
        :data-source="itemList"
        :pagination="false"
        row-key="key"
        size="small"
        :scroll="{ y: 280 }"
      >
        <a-table-column title="材料" width="150">
          <template #default="{ record: item }">
            <span>{{ item.materialName || '-' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="规格" width="90">
          <template #default="{ record: item }">
            <span>{{ item.specification || '-' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="单位" width="60">
          <template #default="{ record: item }">
            <span>{{ item.unit || '-' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="订单数量" width="90">
          <template #default="{ record: item }">
            <span>{{ item.orderedQuantity || '0' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="已验收" width="80">
          <template #default="{ record: item }">
            <span>{{ item.receivedQuantity || '0' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="剩余" width="80">
          <template #default="{ record: item }">
            <span
              :style="{
                color: parseFloat(item.remainingQuantity || '0') < 0 ? '#ff4d4f' : undefined,
              }"
            >
              {{ item.remainingQuantity || '0' }}
            </span>
          </template>
        </a-table-column>
        <a-table-column title="本次到货" width="110">
          <template #default="{ record: item, index }">
            <a-input-number
              v-model:value="item.actualQuantity"
              :min="0"
              :precision="2"
              style="width: 100%"
              :status="item.warning ? 'warning' : undefined"
              @change="emit('itemQtyChange', index)"
            />
          </template>
        </a-table-column>
        <a-table-column title="合格数量" width="110">
          <template #default="{ record: item, index }">
            <a-input-number
              v-model:value="item.qualifiedQuantity"
              :min="0"
              :precision="2"
              style="width: 100%"
              @change="emit('itemQualifiedQtyChange', index)"
            />
          </template>
        </a-table-column>
        <a-table-column title="单价(元)" width="110">
          <template #default="{ record: item, index }">
            <a-input-number
              v-model:value="item.unitPrice"
              :min="0"
              :precision="2"
              style="width: 100%"
              @change="emit('itemPriceChange', index)"
            />
          </template>
        </a-table-column>
        <a-table-column title="金额(元)" width="120">
          <template #default="{ record: item }">
            <span>{{
              Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
            }}</span>
          </template>
        </a-table-column>
        <a-table-column title="使用部位" width="120">
          <template #default="{ record: item }">
            <a-input v-model:value="item.useLocation" size="small" placeholder="部位" />
          </template>
        </a-table-column>
        <a-table-column title="批号" width="100">
          <template #default="{ record: item }">
            <a-input v-model:value="item.batchNo" size="small" placeholder="批号" />
          </template>
        </a-table-column>
      </a-table>

      <div style="text-align: right; margin-top: 8px; font-size: 14px">
        合计：<span style="font-weight: 600; color: #1677ff">{{
          Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
        }}</span>
      </div>
    </div>
  </a-modal>
</template>

<style scoped></style>
