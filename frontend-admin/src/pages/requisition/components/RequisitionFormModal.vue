<script setup lang="ts">
import type { MatRequisitionVO, MatRequisitionItemVO } from '@/types/requisition'
import type { ProjectVO } from '@/types/project'
import type { ContractVO } from '@/types/contract'
import type { WarehouseVO } from '@/types/inventory'
import type { SysUserVO } from '@/types/user'

defineProps<{
  visible: boolean
  title: string
  formData: Partial<MatRequisitionVO>
  projectList: ProjectVO[]
  contractList: ContractVO[]
  warehouseList: WarehouseVO[]
  userList: SysUserVO[]
  itemList: (Partial<MatRequisitionItemVO> & { key: number })[]
  itemsTotalAmount: string
}>()

const emit = defineEmits<{
  ok: []
  cancel: []
  'update:visible': [value: boolean]
  itemQtyChange: [index: number]
  itemPriceChange: [index: number]
  addItem: []
  removeItem: [index: number]
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
      <a-form-item label="合同">
        <a-select v-model:value="formData.contractId" placeholder="请选择合同" allow-clear>
          <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
            {{ c.contractName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="仓库" required>
        <a-select v-model:value="formData.warehouseId" placeholder="请选择仓库">
          <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
            {{ w.warehouseName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="领料人">
        <a-select v-model:value="formData.requisitionerId" placeholder="请选择领料人" allow-clear>
          <a-select-option v-for="u in userList" :key="u.id" :value="u.id">
            {{ u.realName || u.username }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="领料日期">
        <a-date-picker
          v-model:value="formData.requisitionDate"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
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
        <span style="font-weight: 600; font-size: 14px">领料明细</span>
        <a-button size="small" type="dashed" @click="emit('addItem')">新增行</a-button>
      </div>

      <a-table
        :data-source="itemList"
        :pagination="false"
        row-key="key"
        size="small"
        :scroll="{ y: 280 }"
      >
        <a-table-column title="材料名称" width="150">
          <template #default="{ record: item }">
            <a-input v-model:value="item.materialName" size="small" placeholder="材料名称" />
          </template>
        </a-table-column>
        <a-table-column title="规格" width="90">
          <template #default="{ record: item }">
            <a-input v-model:value="item.specification" size="small" placeholder="规格" />
          </template>
        </a-table-column>
        <a-table-column title="单位" width="60">
          <template #default="{ record: item }">
            <a-input v-model:value="item.unit" size="small" placeholder="单位" />
          </template>
        </a-table-column>
        <a-table-column title="数量" width="110">
          <template #default="{ record: item, index }">
            <a-input-number
              v-model:value="item.quantity"
              :min="0"
              :precision="2"
              style="width: 100%"
              @change="emit('itemQtyChange', index)"
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
        <a-table-column title="操作" width="60">
          <template #default="{ index }">
            <a-button size="small" danger type="link" @click="emit('removeItem', index)"
              >删除</a-button
            >
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
