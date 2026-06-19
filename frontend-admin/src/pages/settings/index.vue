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
  <div class="project-target-redesign app-page">
    <a-card title="通知设置" :bordered="false" :loading="loading">
      <a-form layout="horizontal">
        <a-form-item label="启用通知">
          <a-switch v-model:checked="preferences.notificationEnabled" />
        </a-form-item>
      </a-form>
    </a-card>

    <a-card title="界面偏好" :bordered="false" :loading="loading" style="margin-top: 16px">
      <a-form layout="horizontal">
        <a-form-item label="主题">
          <a-radio-group v-model:value="preferences.theme">
            <a-radio value="light">浅色</a-radio>
            <a-radio value="dark">深色</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="侧边栏">
          <a-switch v-model:checked="preferences.sidebarCollapsed" />
          <span style="margin-left: 8px; color: #8c8c8c">默认折叠侧边栏</span>
        </a-form-item>

        <a-form-item label="表格密度">
          <a-radio-group v-model:value="preferences.tableDensity">
            <a-radio value="default">默认</a-radio>
            <a-radio value="middle">中等</a-radio>
            <a-radio value="small">紧凑</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-card>

    <div style="margin-top: 24px">
      <a-button type="primary" :loading="saving" @click="handleSave">保存设置</a-button>
    </div>
  </div>
</template>

<style scoped></style>
