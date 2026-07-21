<script setup lang="ts">
import V2Button from './V2Button.vue'
import V2Dialog from './V2Dialog.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    confirmText?: string
    cancelText?: string
    danger?: boolean
    loading?: boolean
  }>(),
  {
    description: undefined,
    confirmText: '确认',
    cancelText: '取消',
    danger: false,
    loading: false,
  },
)

const emit = defineEmits<{
  close: []
  confirm: []
}>()

function close() {
  if (!props.loading) emit('close')
}
</script>

<template>
  <V2Dialog
    :open="open"
    :title="title"
    :description="description"
    :close-label="`关闭${title}`"
    :close-on-backdrop="!loading"
    :close-disabled="loading"
    panel-class="v2-dialog-standard v2-confirm-dialog"
    @close="close"
  >
    <slot />
    <template #footer>
      <V2Button variant="secondary" :disabled="loading" @click="close">{{ cancelText }}</V2Button>
      <V2Button
        :variant="danger ? 'danger' : 'primary'"
        :loading="loading"
        @click="emit('confirm')"
      >
        {{ confirmText }}
      </V2Button>
    </template>
  </V2Dialog>
</template>
