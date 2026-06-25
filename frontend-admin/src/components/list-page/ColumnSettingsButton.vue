<script setup lang="ts">
import { SettingOutlined } from '@ant-design/icons-vue'
import type { ColumnSettingItem } from '@/composables/useColumnSettings'

defineProps<{
  columns: ColumnSettingItem[]
  visible: Record<string, boolean>
}>()

const emit = defineEmits<{
  (e: 'toggle', key: string): void
}>()
</script>

<template>
  <a-dropdown trigger="click">
    <a-button>
      <SettingOutlined />
      列设置
    </a-button>
    <template #overlay>
      <a-menu>
        <a-menu-item
          v-for="column in columns"
          :key="column.key"
          @click="emit('toggle', column.key)"
        >
          <a-checkbox :checked="visible[column.key] !== false">
            {{ column.label }}
          </a-checkbox>
        </a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>
