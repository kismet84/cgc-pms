<script setup lang="ts">
defineProps<{
  open: boolean
  title: string
  isViewMode: boolean
  submitting: boolean
  formData: {
    projectId?: string
    contractId?: string
    purpose?: string
    remark?: string
  }
  projectList: { id: string; projectName: string }[]
  contractList: { id: string; contractName?: string }[]
  budgetLineOptions: Array<{ id?: string; label: string }>
  proofFileName?: string
  itemList: Array<{
    key: number
    materialId?: string
    materialName?: string
    quantity?: string
    budgetLineId?: string
    estimatedUnitPrice?: string
    unit?: string
    plannedDate?: string
    remark?: string
  }>
  itemsCount: number
  itemColumns: Array<Record<string, unknown>>
  materialList: { id: string; materialName: string; unit?: string }[]
  filterOption: (input: string, option: { label?: string }) => boolean | undefined
  getPopupContainer: () => HTMLElement
}>()

const emit = defineEmits<{
  ok: []
  cancel: []
  projectChange: [projectId: string | undefined]
  markDirty: []
  addItem: []
  removeItem: [key: number]
  materialChange: [key: number, materialId: string | undefined]
  materialClear: [key: number]
  proofFileChange: [file: File | null]
}>()

function handleProofFile(event: Event) {
  const input = event.target as HTMLInputElement
  emit('proofFileChange', input.files?.[0] ?? null)
  emit('markDirty')
}
</script>

<template>
  <a-modal
    :open="open"
    :title="title"
    :width="1120"
    :confirm-loading="isViewMode ? false : submitting"
    :ok-button-props="isViewMode ? { style: { display: 'none' } } : undefined"
    :cancel-text="isViewMode ? '关闭' : '取消'"
    destroy-on-close
    @ok="emit('ok')"
    @cancel="emit('cancel')"
  >
    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
      <a-form-item label="项目" required>
        <a-select
          v-model:value="formData.projectId"
          :disabled="isViewMode"
          placeholder="请选择项目"
          @change="(value: string | undefined) => emit('projectChange', value)"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="关联合同" required>
        <a-select
          v-model:value="formData.contractId"
          :disabled="isViewMode"
          placeholder="选择采购合同"
          allow-clear
          show-search
          :filter-option="filterOption"
          @change="emit('markDirty')"
        >
          <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
            {{ c.contractName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="采购用途" required>
        <a-textarea
          v-model:value="formData.purpose"
          :disabled="isViewMode"
          :rows="2"
          placeholder="说明本次采购用途、施工部位或需求来源"
          @change="emit('markDirty')"
        />
      </a-form-item>
      <a-form-item label="采购依据" :required="!isViewMode">
        <div v-if="isViewMode">附件已由系统在提交审批时校验</div>
        <div v-else class="proof-file-input">
          <input type="file" @change="handleProofFile" />
          <span>{{ proofFileName || '新建申请必须上传采购依据' }}</span>
        </div>
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea
          v-model:value="formData.remark"
          :disabled="isViewMode"
          :rows="2"
          placeholder="请输入备注"
          @change="emit('markDirty')"
        />
      </a-form-item>
    </a-form>

    <div class="pr-items-section">
      <div class="pr-items-header">
        <span class="pr-items-title">
          申请明细
          <span class="pr-items-count"> {{ itemsCount }} 项 </span>
        </span>
        <a-button v-if="!isViewMode" type="dashed" size="small" @click="emit('addItem')">
          + 添加物料
        </a-button>
      </div>

      <a-table
        :data-source="itemList"
        :pagination="false"
        table-layout="fixed"
        :columns="itemColumns"
        row-key="key"
        size="small"
        :scroll="{ y: 250 }"
      >
        <template #bodyCell="{ column, record: item }">
          <template v-if="column.key === 'material'">
            <div style="display: flex; gap: 4px">
              <a-select
                :value="item.materialId"
                :disabled="isViewMode"
                placeholder="选择已有物料"
                allow-clear
                :style="{ width: item.materialId ? '100%' : '50%', flexShrink: 0 }"
                show-search
                :filter-option="filterOption"
                @change="(value: string | undefined) => emit('materialChange', item.key, value)"
                @clear="emit('materialClear', item.key)"
              >
                <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
                  {{ m.materialName }}
                </a-select-option>
              </a-select>
              <a-input
                v-if="!item.materialId"
                v-model:value="item.materialName"
                :disabled="isViewMode"
                placeholder="自定义物料"
                size="small"
                style="flex: 1"
                @change="emit('markDirty')"
              />
            </div>
          </template>
          <template v-else-if="column.key === 'unit'">
            <a-input
              v-model:value="item.unit"
              :disabled="isViewMode"
              placeholder="单位"
              size="small"
              style="width: 100%"
              @change="emit('markDirty')"
            />
          </template>
          <template v-else-if="column.key === 'quantity'">
            <a-input-number
              v-model:value="item.quantity"
              :disabled="isViewMode"
              :min="0"
              :precision="4"
              style="width: 100%"
              @change="emit('markDirty')"
            />
          </template>
          <template v-else-if="column.key === 'budgetLineId'">
            <a-select
              v-model:value="item.budgetLineId"
              :disabled="isViewMode"
              placeholder="预算科目"
              style="width: 100%"
              @change="emit('markDirty')"
            >
              <a-select-option v-for="line in budgetLineOptions" :key="line.id" :value="line.id">
                {{ line.label }}
              </a-select-option>
            </a-select>
          </template>
          <template v-else-if="column.key === 'estimatedUnitPrice'">
            <a-input-number
              v-model:value="item.estimatedUnitPrice"
              :disabled="isViewMode"
              :min="0"
              :precision="2"
              style="width: 100%"
              @change="emit('markDirty')"
            />
          </template>
          <template v-else-if="column.key === 'plannedDate'">
            <a-date-picker
              v-model:value="item.plannedDate"
              :disabled="isViewMode"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              size="small"
              :get-popup-container="getPopupContainer"
              @change="emit('markDirty')"
            />
          </template>
          <template v-else-if="column.key === 'remark'">
            <a-input
              v-model:value="item.remark"
              :disabled="isViewMode"
              placeholder="备注"
              size="small"
              @change="emit('markDirty')"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-if="!isViewMode"
              type="link"
              size="small"
              danger
              @click="emit('removeItem', item.key)"
            >
              删除
            </a-button>
          </template>
        </template>
      </a-table>
    </div>
  </a-modal>
</template>

<style scoped>
.pr-items-section {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
  margin-top: 4px;
}

.proof-file-input {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--muted);
  font-size: 12px;
}

.pr-items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.pr-items-title {
  font-weight: 600;
  font-size: 14px;
}

.pr-items-count {
  color: var(--muted);
  font-weight: 400;
  font-size: 12px;
  margin-left: 6px;
}

:deep(.pr-items-section .ant-table-thead > tr > th:first-child),
:deep(.pr-items-section .ant-table-tbody > tr > td:first-child) {
  width: 240px !important;
  min-width: 240px !important;
  max-width: 240px !important;
}

:deep(.pr-items-section .ant-table colgroup col:first-child) {
  width: 240px !important;
  min-width: 240px !important;
  max-width: 240px !important;
}
</style>
