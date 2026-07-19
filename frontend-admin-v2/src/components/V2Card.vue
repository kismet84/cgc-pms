<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    interactive?: boolean
  }>(),
  {
    title: undefined,
    subtitle: undefined,
    interactive: false,
  },
)
</script>

<template>
  <section class="v2-card" :class="{ 'v2-card--interactive': interactive }">
    <header v-if="title || subtitle || $slots.actions" class="v2-card__header">
      <div>
        <h2 v-if="title" class="v2-card__title">{{ title }}</h2>
        <p v-if="subtitle" class="v2-card__subtitle">{{ subtitle }}</p>
      </div>
      <slot name="actions"></slot>
    </header>
    <div class="v2-card__body"><slot /></div>
    <footer v-if="$slots.footer" class="v2-card__footer"><slot name="footer" /></footer>
  </section>
</template>
