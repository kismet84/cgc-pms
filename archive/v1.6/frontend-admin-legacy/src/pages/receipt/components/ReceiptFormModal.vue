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
  proofFileName?: string
}>()

const emit = defineEmits<{
  ok: []
  cancel: []
  'update:visible': [value: boolean]
  orderChange: [orderId: string | undefined]
  itemQtyChange: [index: number]
  itemPriceChange: [index: number]
  itemQualifiedQtyChange: [index: number]
  proofFileChange: [file: File | null]
}>()

function handleProofFile(event: Event) {
  const input = event.target as HTMLInputElement
  emit('proofFileChange', input.files?.[0] ?? null)
}
</script>

<template>
  <a-modal
    :open="visible"
    :title="title"
    :width="1180"
    wrap-class-name="compact-receipt-modal"
    @ok="emit('ok')"
    @cancel="emit('cancel')"
  >
    <div class="receipt-modal-body">
      <a-form
        :label-col="{ span: 5 }"
        :wrapper-col="{ span: 18 }"
        class="receipt-modal-form"
        size="small"
      >
        <a-form-item label="项目" required>
          <a-select v-model:value="formData.projectId" placeholder="请选择项目">
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="采购订单" required>
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
        <a-form-item label="验收日期" required>
          <a-date-picker
            v-model:value="formData.receiptDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="质量状态" required>
          <a-select v-model:value="formData.qualityStatus" placeholder="请选择质量状态" allow-clear>
            <a-select-option value="QUALIFIED">合格</a-select-option>
            <a-select-option value="PARTIAL">部分合格</a-select-option>
            <a-select-option value="UNQUALIFIED">不合格</a-select-option>
            <a-select-option value="PENDING">待检验</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="验收附件" required>
          <div class="proof-file-input">
            <input type="file" @change="handleProofFile" />
            <span>{{ proofFileName || '上传验收记录、质检报告或到货凭证' }}</span>
          </div>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" :rows="1" placeholder="请输入备注" />
        </a-form-item>
      </a-form>

      <div class="receipt-items-section">
        <div class="receipt-items-head">
          <span>验收明细</span>
          <em>选择采购订单后自动加载验收明细</em>
        </div>

        <a-alert
          v-if="hasWarning"
          message="部分验收数量超过采购订单剩余数量，请核实后再保存"
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
          :scroll="{ x: 1580, y: 220 }"
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
          <a-table-column title="不合格" width="85">
            <template #default="{ record: item }">
              <span
                :style="{
                  color: Number(item.unqualifiedQuantity || 0) > 0 ? '#ff4d4f' : undefined,
                }"
              >
                {{ item.unqualifiedQuantity || '0' }}
              </span>
            </template>
          </a-table-column>
          <a-table-column title="处置方式" width="125">
            <template #default="{ record: item }">
              <a-select
                v-model:value="item.dispositionType"
                :disabled="Number(item.unqualifiedQuantity || 0) <= 0"
                allow-clear
                placeholder="选择处置"
                style="width: 100%"
              >
                <a-select-option value="RETURN">退供</a-select-option>
                <a-select-option value="REPLACE">换货</a-select-option>
                <a-select-option value="CONCESSION">让步接收</a-select-option>
              </a-select>
            </template>
          </a-table-column>
          <a-table-column title="处置原因" width="150">
            <template #default="{ record: item }">
              <a-input
                v-model:value="item.dispositionReason"
                :disabled="Number(item.unqualifiedQuantity || 0) <= 0"
                placeholder="必填"
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

        <div class="receipt-items-total">
          合计：<span>{{
            Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
          }}</span>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.receipt-modal-body {
  max-height: calc(100vh - 220px);
  overflow: auto;
  padding-right: 4px;
}

.receipt-modal-form {
  margin-bottom: 6px;
}

.receipt-modal-form :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.receipt-items-section {
  padding-top: 10px;
  margin-top: 2px;
  border-top: 1px solid #f0f0f0;
}

.receipt-items-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.receipt-items-head span {
  font-weight: 600;
  font-size: 14px;
  color: #0f172a;
}

.receipt-items-head em {
  font-style: normal;
  font-size: 12px;
  color: var(--muted);
}

.receipt-items-total {
  margin-top: 8px;
  text-align: right;
  font-size: 13px;
}

.receipt-items-total span {
  font-weight: 600;
  color: #1677ff;
}

.proof-file-input {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--muted);
  font-size: 12px;
}

:global(.compact-receipt-modal .ant-modal-body) {
  padding-top: 12px;
  padding-bottom: 12px;
}

:global(.compact-receipt-modal .ant-modal-footer) {
  margin-top: 0;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}
</style>
