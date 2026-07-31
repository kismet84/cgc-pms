import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

describe('cash forecast page source contract', () => {
  const source = readFileSync(
    resolve(dirname(fileURLToPath(import.meta.url)), '../index.vue'),
    'utf-8',
  )

  it('exposes the complete plan-gap-action-actual-roll lifecycle', () => {
    expect(source).toContain('新建预测版本')
    expect(source).toContain('按计划重算')
    expect(source).toContain('制定措施')
    expect(source).toContain('提交审批')
    expect(source).toContain('审批通过')
    expect(source).toContain('刷新实际偏差')
    expect(source).toContain('建立滚动版本')
  })

  it('shows sources, daily variance and immutable audit evidence', () => {
    expect(source).toContain('计划来源追溯')
    expect(source).toContain('实际资金流水')
    expect(source).toContain('收款偏差')
    expect(source).toContain('付款偏差')
    expect(source).toContain('审批与审计留痕')
    expect(source).toContain('payload_hash')
  })

  it('refreshes the version list after submission to keep list and detail status consistent', () => {
    expect(source).toMatch(
      /async function submitCycle\(\) \{[\s\S]*await submitCashForecast\(cycleId\.value\)[\s\S]*await loadCycles\(\)/,
    )
  })
})
