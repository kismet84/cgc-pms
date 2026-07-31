<script setup lang="ts">
import { computed } from 'vue'

export interface StepConfig {
  title: string
  description?: string
}

interface Props {
  current: number
  steps: StepConfig[]
  canPrev?: boolean
  canNext?: boolean
  canSubmit?: boolean
  loading?: boolean
  prevText?: string
  nextText?: string
  submitText?: string
}

const props = withDefaults(defineProps<Props>(), {
  canPrev: true,
  canNext: true,
  canSubmit: true,
  loading: false,
  prevText: '上一步',
  nextText: '下一步',
  submitText: '提交',
})

interface Emits {
  (e: 'prev'): void
  (e: 'next'): void
  (e: 'submit'): void
}

const emit = defineEmits<Emits>()

const showPrev = computed(() => props.current > 0)
const showNext = computed(() => props.current < props.steps.length - 1)
const showSubmit = computed(() => props.current === props.steps.length - 1)
</script>

<template>
  <div class="step-wizard">
    <a-steps :current="current" class="sw-steps">
      <a-step
        v-for="(step, idx) in steps"
        :key="idx"
        :title="step.title"
        :description="step.description"
      />
    </a-steps>

    <div class="sw-content">
      <slot />
    </div>

    <div class="sw-actions">
      <a-button v-if="showPrev" :disabled="!canPrev || loading" @click="emit('prev')">
        {{ prevText }}
      </a-button>
      <a-button
        v-if="showNext"
        type="primary"
        :disabled="!canNext || loading"
        :loading="loading"
        @click="emit('next')"
      >
        {{ nextText }}
      </a-button>
      <a-button
        v-if="showSubmit"
        type="primary"
        :disabled="!canSubmit || loading"
        :loading="loading"
        @click="emit('submit')"
      >
        {{ submitText }}
      </a-button>
    </div>
  </div>
</template>

<style scoped>
.step-wizard {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.sw-steps {
  padding: 24px 48px 0;
}

.sw-content {
  min-height: 400px;
  padding: 0 24px;
}

.sw-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--border);
}
</style>
