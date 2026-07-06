import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const configSource = readFileSync(resolve(currentDir, '../pageConfig.ts'), 'utf-8')

describe('settlement page quality guardrails', () => {
  it('shows a visible fallback when KPI loading fails', () => {
    expect(source).toContain("message.warning('结算统计加载失败，已显示空摘要')")
  })

  it('extracts static settlement table config out of the giant component', () => {
    expect(source).toContain("from './pageConfig'")
    expect(configSource).toContain('export const SETTLEMENT_GRID_COLUMNS')
    expect(configSource).toContain('export const SETTLEMENT_STATUS_COLOR_MAP')
  })
})
