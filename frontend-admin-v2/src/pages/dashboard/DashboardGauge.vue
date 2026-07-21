<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { normalizeGaugeValue } from './model'

const props = defineProps<{ value: number; colorToken: string }>()
const canvas = ref<HTMLCanvasElement | null>(null)

function cssValue(token: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(token).trim()
  if (!value) throw new Error(`Missing design token: ${token}`)
  return value
}

function draw(): void {
  const element = canvas.value
  const context = element?.getContext('2d')
  if (!element || !context) return
  const size = 142
  const scale = window.devicePixelRatio || 1
  element.width = size * scale
  element.height = size * scale
  context.scale(scale, scale)
  context.clearRect(0, 0, size, size)
  context.lineWidth = 9
  context.lineCap = 'round'
  context.strokeStyle = cssValue('--v2-color-border-subtle')
  context.beginPath()
  context.arc(size / 2, size / 2, 58, 0, Math.PI * 2)
  context.stroke()
  context.strokeStyle = cssValue(props.colorToken)
  context.beginPath()
  context.arc(
    size / 2,
    size / 2,
    58,
    -Math.PI / 2,
    -Math.PI / 2 + Math.PI * 2 * (normalizeGaugeValue(props.value) / 100),
  )
  context.stroke()
}

onMounted(draw)
watch(() => [props.value, props.colorToken], draw)
</script>

<template>
  <canvas ref="canvas" :aria-label="`经营健康度 ${normalizeGaugeValue(value)} 分`" role="img">
    经营健康度 {{ normalizeGaugeValue(value) }} 分
  </canvas>
</template>
