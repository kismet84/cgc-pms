<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import service from '@/api/request'

interface Preferences {
  sidebarCollapsed: boolean
  notificationEnabled: boolean
  theme: 'light' | 'dark'
  tableDensity: 'default' | 'middle' | 'small'
}

const loading = ref(false)
const saving = ref(false)

const preferences = reactive<Preferences>({
  sidebarCollapsed: false,
  notificationEnabled: true,
  theme: 'light',
  tableDensity: 'middle',
})

onMounted(async () => {
  loading.value = true
  try {
    const data = await service.get<Preferences>('/profile/preferences')
    Object.assign(preferences, data)
  } catch (e: unknown) {
    console.error(e)
    message.error('加载偏好设置失败')
  } finally {
    loading.value = false
  }
})

async function handleSave() {
  saving.value = true
  try {
    await service.put('/profile/preferences', {
      theme: preferences.theme,
      sidebarCollapsed: preferences.sidebarCollapsed,
      notificationEnabled: preferences.notificationEnabled,
      tableDensity: preferences.tableDensity,
    })
    message.success('保存成功')
  } catch (e: unknown) {
    console.error(e)
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="lg-page app-page settings-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-page-head-breadcrumb">
          <a-breadcrumb-item>系统管理</a-breadcrumb-item>
          <a-breadcrumb-item>偏好设置</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-page-shell settings-shell">
      <section class="lg-section">
        <div class="lg-section-head">
          <h2 class="lg-section-title">通知设置</h2>
        </div>
        <div class="lg-section-body">
          <a-spin :spinning="loading">
            <a-form layout="horizontal" class="settings-form">
              <a-form-item label="启用通知">
                <a-switch v-model:checked="preferences.notificationEnabled" />
              </a-form-item>
            </a-form>
          </a-spin>
        </div>
      </section>

      <section class="lg-section">
        <div class="lg-section-head">
          <h2 class="lg-section-title">界面偏好</h2>
        </div>
        <div class="lg-section-body">
          <a-spin :spinning="loading">
            <a-form layout="horizontal" class="settings-form">
              <a-form-item label="主题">
                <a-radio-group v-model:value="preferences.theme">
                  <a-radio value="light">浅色</a-radio>
                  <a-radio value="dark">深色</a-radio>
                </a-radio-group>
              </a-form-item>

              <a-form-item label="侧边栏">
                <a-switch v-model:checked="preferences.sidebarCollapsed" />
                <span class="settings-hint">默认折叠侧边栏</span>
              </a-form-item>

              <a-form-item label="表格密度">
                <a-radio-group v-model:value="preferences.tableDensity">
                  <a-radio value="default">默认</a-radio>
                  <a-radio value="middle">中等</a-radio>
                  <a-radio value="small">紧凑</a-radio>
                </a-radio-group>
              </a-form-item>
            </a-form>
          </a-spin>
        </div>
      </section>

      <div class="settings-actions">
        <a-button type="primary" :loading="saving" @click="handleSave">保存设置</a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-shell {
  max-width: 900px;
}

.settings-form :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}

.settings-hint {
  margin-left: 8px;
  color: var(--muted);
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
