<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    tone?: 'info' | 'success' | 'warning' | 'danger'
    title: string
    dismissible?: boolean
    dismissLabel?: string
  }>(),
  {
    tone: 'info',
    dismissible: false,
    dismissLabel: '关闭提示',
  },
)

defineEmits<{
  dismiss: []
}>()
</script>

<template>
  <section
    class="v2-alert"
    :class="`v2-alert--${tone}`"
    :role="props.tone === 'danger' ? 'alert' : 'status'"
  >
    <div>
      <strong class="v2-alert__title">{{ title }}</strong>
      <p v-if="$slots.default" class="v2-alert__message"><slot /></p>
    </div>
    <button
      v-if="dismissible"
      type="button"
      class="v2-alert__dismiss"
      :aria-label="dismissLabel"
      @click="$emit('dismiss')"
    >
      关闭
    </button>
  </section>
</template>
