<script setup lang="ts">
import type { NotificationRecord } from '@cgc-pms/frontend-contracts'
import { V2Alert, V2Button, V2Dialog, V2PageState } from '@/components'

defineProps<{
  open: boolean
  items: NotificationRecord[]
  unreadCount: number | null
  loading: boolean
  error: string
  canRequest: boolean
  canEdit: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  read: [id: string]
  readAll: []
}>()
</script>

<template>
  <V2Dialog
    :open="open"
    title="通知中心"
    description="按当前账号读取站内通知摘要；不建立实时连接。"
    @update:open="emit('update:open', $event)"
  >
    <V2Alert v-if="error" tone="danger" title="请求未完成">{{ error }}</V2Alert>
    <V2PageState
      v-if="!canRequest"
      kind="forbidden"
      :heading-level="3"
      title="当前账号无通知摘要权限"
      description="未发起通知列表或未读数请求。"
    />
    <V2PageState
      v-else-if="loading"
      kind="loading"
      :heading-level="3"
      title="正在加载通知"
      description="读取当前账号最近通知。"
    />
    <V2PageState
      v-else-if="!items.length"
      kind="empty"
      :heading-level="3"
      title="暂无站内通知"
      description="当前账号没有可见通知。"
    />
    <div v-else class="shell-notification-center__summary">
      <div class="shell-notification-center__toolbar">
        <span>未读 {{ unreadCount ?? 0 }} 条</span>
        <V2Button
          v-if="canEdit && unreadCount"
          variant="ghost"
          size="small"
          @click="emit('readAll')"
          >全部已读</V2Button
        >
      </div>
      <article
        v-for="item in items"
        :key="item.id"
        :class="['shell-notification-center__item', { 'is-unread': item.isRead !== 1 }]"
      >
        <div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.content }}</p>
          <small>{{ item.createdTime }}</small>
        </div>
        <V2Button
          v-if="canEdit && item.isRead !== 1"
          variant="ghost"
          size="small"
          @click="emit('read', item.id)"
          >已读</V2Button
        >
      </article>
    </div>
  </V2Dialog>
</template>

<style scoped>
.shell-notification-center__summary {
  display: grid;
  gap: var(--v2-space-2);
}

.shell-notification-center__toolbar,
.shell-notification-center__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-3);
}

.shell-notification-center__toolbar {
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}

.shell-notification-center__toolbar > span {
  color: var(--v2-color-danger-text);
  font-size: var(--v2-font-size-12);
  font-weight: var(--v2-font-weight-bold);
}

.shell-notification-center__item {
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.shell-notification-center__item strong {
  font-size: var(--v2-font-size-12);
}

.shell-notification-center__item.is-unread {
  border-inline-start: 0.2rem solid var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.shell-notification-center__item p,
.shell-notification-center__item small {
  margin: var(--v2-space-1) 0 0;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-11);
}
</style>
