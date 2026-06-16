<script setup lang="ts">
import { ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { request } from '@/api/request'

const clearing = ref(false)

function handleClearDatabase() {
  Modal.confirm({
    title: '清空数据库',
    content:
      '此操作将清空所有业务数据（项目、合同、发票、审批等），系统用户和菜单不受影响。数据不可恢复，确定继续？',
    okText: '确定清空',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      clearing.value = true
      try {
        const msg = await request<string>({ url: '/system/clear-database', method: 'DELETE' })
        message.success(msg || '数据库已清空')
      } catch (e: unknown) {
        console.error(e)
        message.error('清空失败，请稍后重试')
      } finally {
        clearing.value = false
      }
    },
  })
}
</script>

<template>
  <a-card title="数据管理" :bordered="false">
    <a-button type="primary" danger :loading="clearing" @click="handleClearDatabase">
      清空数据库
    </a-button>
    <span style="margin-left: 12px; color: #8c8c8c; font-size: 13px">
      清空所有业务数据，保留系统用户和菜单
    </span>
  </a-card>
</template>
