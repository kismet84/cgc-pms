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
    okText: '清空数据库',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      clearing.value = true
      try {
        const msg = await request<string>({
          url: '/system/clear-database',
          method: 'DELETE',
          params: { confirm: 'CLEAR_NON_PROD_DATABASE' },
        })
        message.success(msg || '已清空数据库')
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
      <div class="lg-section-body">
        <div class="system-data-warning">
          <div class="system-data-warning-title">高风险操作</div>
          <ul>
            <li>仅用于非生产环境清理演示数据。</li>
            <li>项目、合同、发票、审批等业务数据会被清空。</li>
            <li>系统用户、角色、菜单和基础权限配置会保留。</li>
          </ul>
        </div>
        <div class="system-data-actions">
          <a-button type="primary" danger :loading="clearing" @click="handleClearDatabase">
            清空数据库
          </a-button>
        </div>
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
  justify-content: flex-end;
  padding-top: var(--spacing-md);
}

.system-data-warning {
  padding: var(--spacing-md);
  color: var(--text-secondary);
  background: var(--warning-soft);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.system-data-warning-title {
  margin-bottom: var(--spacing-xs);
  color: var(--warning);
  font-weight: 700;
}

.system-data-warning ul {
  margin: 0;
  padding-left: 18px;
}

.system-data-warning li + li {
  margin-top: 6px;
}
</style>
