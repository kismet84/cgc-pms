import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardGauge from '@/pages/dashboard/DashboardGauge.vue'
import DashboardTrendChart from '@/pages/dashboard/DashboardTrendChart.vue'

function canvasContext(): CanvasRenderingContext2D {
  return {
    arc: vi.fn(),
    beginPath: vi.fn(),
    clearRect: vi.fn(),
    fill: vi.fn(),
    fillText: vi.fn(),
    lineCap: 'butt',
    lineTo: vi.fn(),
    lineWidth: 1,
    moveTo: vi.fn(),
    scale: vi.fn(),
    setTransform: vi.fn(),
    stroke: vi.fn(),
    strokeStyle: '',
    fillStyle: '',
    font: '',
    textAlign: 'start',
    textBaseline: 'alphabetic',
  } as unknown as CanvasRenderingContext2D
}

describe('Dashboard canvas design tokens', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe(): void {}
        disconnect(): void {}
      },
    )
    const root = document.documentElement.style
    root.setProperty('--v2-color-border-subtle', '#edf1f6')
    root.setProperty('--v2-color-primary', '#2563eb')
    root.setProperty('--v2-color-text-secondary', '#53627a')
    root.setProperty('--v2-color-border', '#e1e8f2')
    root.setProperty('--v2-color-text-disabled', '#94a3b8')
    root.setProperty('--v2-font-size-11', '0.6875rem')
    root.setProperty('--v2-font-sans', 'Inter, sans-serif')
  })

  afterEach(() => {
    document.documentElement.removeAttribute('style')
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('resolves gauge colors from declared CSS tokens', () => {
    const context = canvasContext()
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context)

    mount(DashboardGauge, { props: { value: 72, colorToken: '--v2-color-primary' } })

    expect(context.strokeStyle).toBe('#2563eb')
    expect(context.stroke).toHaveBeenCalledTimes(2)
  })

  it('resolves trend colors and font from declared CSS tokens', () => {
    const context = canvasContext()
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context)
    vi.spyOn(HTMLCanvasElement.prototype, 'clientWidth', 'get').mockReturnValue(480)
    vi.spyOn(HTMLCanvasElement.prototype, 'clientHeight', 'get').mockReturnValue(180)

    mount(DashboardTrendChart, {
      props: {
        ariaLabel: '成本趋势',
        caption: '月度成本趋势',
        points: [{ month: '2026-07', values: { cost: '120000' } }],
        series: [{ key: 'cost', label: '成本', color: '--v2-color-primary' }],
      },
    })

    expect(context.strokeStyle).toBe('#2563eb')
    expect(context.fillStyle).toBe('#2563eb')
    expect(context.font).toBe('0.6875rem Inter, sans-serif')
  })
})
