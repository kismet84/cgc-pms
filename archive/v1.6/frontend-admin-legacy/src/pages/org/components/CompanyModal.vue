<script setup lang="ts">
defineProps<{
  open: boolean
  title: string
  loading: boolean
  form: {
    companyCode: string
    companyName: string
    status: string
    remark: string
  }
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
    :width="800"
    class="lg-modal-form is-vertical"
    ok-text="保存"
    cancel-text="取消"
    @ok="$emit('ok')"
    @update:open="$emit('update:open', $event)"
  >
    <a-form layout="vertical">
      <a-form-item label="公司编号" required>
        <a-input v-model:value="form.companyCode" placeholder="请输入公司编号" />
      </a-form-item>
      <a-form-item label="公司名称" required>
        <a-input v-model:value="form.companyName" placeholder="请输入公司名称" />
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model:value="form.status" class="lg-full-control">
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
