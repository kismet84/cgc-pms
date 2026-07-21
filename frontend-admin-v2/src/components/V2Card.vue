<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    interactive?: boolean
    headingLevel?: 1 | 2 | 3
    titleId?: string
  }>(),
  {
    title: undefined,
    subtitle: undefined,
    interactive: false,
    headingLevel: 2,
    titleId: undefined,
  },
)
</script>

<template>
  <section class="v2-card" :class="{ 'v2-card--interactive': interactive }">
    <header v-if="title || subtitle || $slots.actions" class="v2-card__header">
      <div>
        <component
          :is="`h${props.headingLevel}`"
          v-if="title"
          :id="titleId"
          class="v2-card__title"
          :class="{ 'v2-card__title--page': props.headingLevel === 1 }"
        >
          {{ title }}
        </component>
        <p v-if="subtitle" class="v2-card__subtitle">{{ subtitle }}</p>
      </div>
      <slot name="actions"></slot>
    </header>
    <div class="v2-card__body"><slot /></div>
    <footer v-if="$slots.footer" class="v2-card__footer"><slot name="footer" /></footer>
  </section>
</template>
