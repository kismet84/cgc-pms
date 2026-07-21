<script setup lang="ts">
import { computed, useId } from 'vue'
import V2Skeleton from './V2Skeleton.vue'

const props = withDefaults(
  defineProps<{
    code?: string
    title: string
    description: string
    kind?: 'error' | 'loading' | 'empty'
    live?: 'off' | 'polite' | 'assertive'
    headingLevel?: 1 | 2 | 3
    titleId?: string
  }>(),
  {
    code: undefined,
    kind: 'empty',
    live: 'polite',
    headingLevel: 1,
  },
)

const role = computed(() => (props.kind === 'error' ? 'alert' : 'status'))
const generatedTitleId = `v2-page-state-title-${useId()}`
const resolvedTitleId = computed(() => props.titleId || generatedTitleId)
</script>

<template>
  <section
    class="v2-page-state"
    :class="`v2-page-state--${kind}`"
    :role="role"
    :aria-live="live"
    aria-atomic="true"
    :aria-labelledby="resolvedTitleId"
  >
    <div class="v2-page-state__mark" aria-hidden="true">
      {{ kind === 'loading' ? '···' : code || '—' }}
    </div>
    <div class="v2-page-state__copy">
      <p v-if="code" class="v2-page-state__code">状态 {{ code }}</p>
      <component :is="`h${headingLevel}`" :id="resolvedTitleId">{{ title }}</component>
      <p>{{ description }}</p>
    </div>
    <div v-if="kind === 'loading'" class="v2-page-state__skeletons" aria-hidden="true">
      <V2Skeleton label="" />
      <V2Skeleton label="" />
      <V2Skeleton label="" />
    </div>
    <div v-if="$slots.actions" class="v2-page-state__actions">
      <slot name="actions" />
    </div>
    <div v-if="$slots.default" class="v2-page-state__details">
      <slot />
    </div>
  </section>
</template>
