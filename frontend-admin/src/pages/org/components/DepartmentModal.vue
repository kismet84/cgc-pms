<script setup lang="ts">
import type { OrgCompanyVO } from '@/types/org'

defineProps<{
  open: boolean
  title: string
  loading: boolean
  form: {
    companyId: string
    parentId: string
    deptCode: string
    deptName: string
    orderNum: number
    status: string
    remark: string
  }
  companyOptions: OrgCompanyVO[]
}>()

defineEmits<{
  'update:open': [value: boolean]
  ok: []
}>()
</script>

<template>
  <a-modal
    :open="open"
    :title="title"
    :confirm-loading="loading"
    @ok="$emit('ok')"
    @update:open="$emit('update:open', $event)"
  >
    <a-form layout="vertical">
      <a-form-item label="所属公司" required>
        <a-select v-model:value="form.companyId" placeholder="选择公司" style="width: 100%">
          <a-select-option v-for="c in companyOptions" :key="c.id" :value="c.id">
            {{ c.companyName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="部门编号" required>
        <a-input v-model:value="form.deptCode" placeholder="请输入部门编号" />
      </a-form-item>
      <a-form-item label="部门名称" required>
        <a-input v-model:value="form.deptName" placeholder="请输入部门名称" />
      </a-form-item>
      <a-form-item label="排序号">
        <a-input-number v-model:value="form.orderNum" :min="0" style="width: 100%" />
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model:value="form.status" style="width: 100%">
          <a-select-option value="ENABLED">启用</a-select-option>
          <a-select-option value="DISABLED">禁用</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model:value="form.remark" placeholder="备注信息" :rows="2" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
