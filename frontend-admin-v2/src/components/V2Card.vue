<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    interactive?: boolean
    headingLevel?: 1 | 2 | 3
    titleId?: string
    leadingActionLabel?: string
  }>(),
  {
    title: undefined,
    subtitle: undefined,
    interactive: false,
    headingLevel: 2,
    titleId: undefined,
    leadingActionLabel: undefined,
  },
)
const emit = defineEmits<{ leadingAction: [] }>()
</script>

<template>
  <section
    class="v2-card"
    :class="{
      'v2-card--interactive': interactive,
      'v2-card--page-heading': props.headingLevel === 1,
    }"
  >
    <header
      v-if="title || subtitle || leadingActionLabel || $slots.actions || $slots['title-extra']"
      class="v2-card__header"
    >
      <div
        v-if="title || subtitle || leadingActionLabel || $slots['title-extra']"
        class="v2-card__heading"
      >
        <div class="v2-card__title-row">
          <button
            v-if="leadingActionLabel"
            type="button"
            class="v2-button v2-button--primary v2-button--small"
            @click="emit('leadingAction')"
          >
            <span>{{ leadingActionLabel }}</span>
          </button>
          <component
            :is="`h${props.headingLevel}`"
            v-if="title"
            :id="titleId"
            class="v2-card__title"
            :class="{ 'v2-card__title--page': props.headingLevel === 1 }"
          >
            {{ title }}
          </component>
          <slot name="title-extra"></slot>
        </div>
        <p v-if="subtitle" class="v2-card__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="v2-card__actions"><slot name="actions" /></div>
    </header>
    <div v-if="$slots.default" class="v2-card__body"><slot /></div>
    <footer v-if="$slots.footer" class="v2-card__footer"><slot name="footer" /></footer>
  </section>
</template>
