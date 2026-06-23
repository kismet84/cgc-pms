<script setup lang="ts">
import type { OrgCompanyVO } from '@/types/org'
import type { FlatDeptItem } from '../utils'

const props = defineProps<{
  open: boolean
  title: string
  loading: boolean
  form: {
    companyId: string
    departmentId: string
    positionCode: string
    positionName: string
    status: string
    remark: string
  }
  companyOptions: OrgCompanyVO[]
  deptOptions: FlatDeptItem[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  ok: []
}>()

function onCompanyChange(_val: string) {
  props.form.departmentId = ''
}
</script>

<template>
  <a-modal
    :open="open"
    :title="title"
    :confirm-loading="loading"
    :width="520"
    class="lg-modal-form is-vertical"
    ok-text="保存"
    cancel-text="取消"
    @ok="emit('ok')"
    @update:open="emit('update:open', $event)"
  >
    <a-form layout="vertical">
      <a-form-item label="所属公司" required>
        <a-select
          v-model:value="form.companyId"
          placeholder="请选择公司"
          style="width: 100%"
          @change="onCompanyChange"
        >
          <a-select-option v-for="c in companyOptions" :key="c.id" :value="c.id">
            {{ c.companyName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="所属部门" required>
        <a-select
          v-model:value="form.departmentId"
          placeholder="请先选择公司"
          style="width: 100%"
          :disabled="!form.companyId"
        >
          <a-select-option v-for="d in deptOptions" :key="d.id" :value="d.id">
            {{ d.name }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="岗位编号" required>
        <a-input v-model:value="form.positionCode" placeholder="请输入岗位编号" />
      </a-form-item>
      <a-form-item label="岗位名称" required>
        <a-input v-model:value="form.positionName" placeholder="请输入岗位名称" />
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
