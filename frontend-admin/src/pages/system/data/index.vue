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
  <div class="lg-page app-page system-data-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-page-head-breadcrumb">
          <a-breadcrumb-item>系统设置</a-breadcrumb-item>
          <a-breadcrumb-item>数据管理</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <section class="lg-section system-data-card">
      <div class="lg-section-head">
        <div>
          <h2 class="lg-section-title">数据库维护</h2>
          <div class="lg-section-subtitle">清空所有业务数据，保留系统用户和菜单。</div>
        </div>
      </div>
      <div class="lg-section-body system-data-actions">
        <a-button type="primary" danger :loading="clearing" @click="handleClearDatabase">
          清空数据库
        </a-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.system-data-card {
  max-width: 900px;
}

.system-data-actions {
  display: flex;
  align-items: center;
}
</style>
