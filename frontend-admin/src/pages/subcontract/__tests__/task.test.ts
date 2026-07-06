import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../task.vue'), 'utf-8')
const configSource = readFileSync(resolve(currentDir, '../pageConfig.ts'), 'utf-8')

describe('subcontract task page quality guardrails', () => {
  it('extracts static status and grid config out of the giant component', () => {
    expect(source).toContain("from './pageConfig'")
    expect(configSource).toContain('export const SUBCONTRACT_TASK_STATUS_LABEL')
    expect(configSource).toContain('export const SUBCONTRACT_TASK_STATUS_COLOR')
    expect(configSource).toContain('export const SUBCONTRACT_TASK_GRID_COLUMNS')
  })
})
