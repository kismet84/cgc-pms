<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

interface TrendPoint {
  month: string
  values: Record<string, string>
}

interface TrendSeries {
  key: string
  label: string
  color: string
}

const props = defineProps<{
  points: TrendPoint[]
  series: TrendSeries[]
  ariaLabel: string
  caption: string
}>()
const canvas = ref<HTMLCanvasElement | null>(null)
let observer: ResizeObserver | null = null

function draw(): void {
  const element = canvas.value
  const context = element?.getContext('2d')
  if (!element || !context || !props.points.length) return

  const width = element.clientWidth || 480
  const height = element.clientHeight || 180
  const scale = window.devicePixelRatio || 1
  element.width = width * scale
  element.height = height * scale
  context.setTransform(scale, 0, 0, scale, 0, 0)
  context.clearRect(0, 0, width, height)

  const values = props.points.flatMap((point) =>
    props.series.map(({ key }) => Number(point.values[key] ?? 0) / 10000),
  )
  const minimum = Math.min(0, ...values)
  const maximum = Math.max(0, ...values)
  const span = maximum - minimum || 1
  const area = { left: 46, right: width - 18, top: 38, bottom: height - 26 }
  const x = (index: number) =>
    area.left + ((area.right - area.left) * index) / Math.max(1, props.points.length - 1)
  const y = (value: number) => area.bottom - ((value - minimum) / span) * (area.bottom - area.top)

  context.font = '11px sans-serif'
  context.textBaseline = 'middle'
  let legendX = area.left
  for (const item of props.series) {
    context.strokeStyle = item.color
    context.lineWidth = 2
    context.beginPath()
    context.moveTo(legendX, 16)
    context.lineTo(legendX + 16, 16)
    context.stroke()
    context.fillStyle = '#52627a'
    context.fillText(item.label, legendX + 21, 16)
    legendX += 86
  }

  context.strokeStyle = '#e7edf5'
  context.lineWidth = 1
  context.fillStyle = '#8a98ad'
  context.textAlign = 'right'
  for (let index = 0; index <= 4; index += 1) {
    const value = minimum + (span * index) / 4
    const lineY = y(value)
    context.beginPath()
    context.moveTo(area.left, lineY)
    context.lineTo(area.right, lineY)
    context.stroke()
    context.fillText(value.toFixed(0), area.left - 8, lineY)
  }

  const labelStep = Math.max(1, Math.ceil(props.points.length / 6))
  context.textAlign = 'center'
  props.points.forEach((point, index) => {
    if (index % labelStep === 0 || index === props.points.length - 1) {
      context.fillText(point.month.slice(5), x(index), height - 10)
    }
  })

  for (const item of props.series) {
    context.strokeStyle = item.color
    context.fillStyle = item.color
    context.lineWidth = 2
    context.beginPath()
    props.points.forEach((point, index) => {
      const pointX = x(index)
      const pointY = y(Number(point.values[item.key] ?? 0) / 10000)
      if (index === 0) context.moveTo(pointX, pointY)
      else context.lineTo(pointX, pointY)
    })
    context.stroke()
    props.points.forEach((point, index) => {
      context.beginPath()
      context.arc(x(index), y(Number(point.values[item.key] ?? 0) / 10000), 2.5, 0, Math.PI * 2)
      context.fill()
    })
  }
}

onMounted(() => {
  observer = new ResizeObserver(draw)
  if (canvas.value) observer.observe(canvas.value)
  draw()
})
onBeforeUnmount(() => observer?.disconnect())
watch(() => [props.points, props.series], draw, { deep: true })
</script>

<template>
  <div class="trend-chart">
    <canvas ref="canvas" :aria-label="ariaLabel" role="img" />
    <table class="v2-visually-hidden">
      <caption>
        {{
          caption
        }}
      </caption>
      <thead>
        <tr>
          <th>月份</th>
          <th v-for="item in series" :key="item.key">{{ item.label }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="point in points" :key="point.month">
          <td>{{ point.month }}</td>
          <td v-for="item in series" :key="item.key">{{ point.values[item.key] }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.trend-chart,
canvas {
  width: 100%;
  height: 100%;
}
canvas {
  display: block;
}
.v2-visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}
</style>
