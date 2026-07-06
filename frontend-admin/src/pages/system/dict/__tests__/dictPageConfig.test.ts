import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const configSource = readFileSync(resolve(currentDir, '../dictPageConfig.ts'), 'utf-8')

describe('dict page config extraction', () => {
  it('moves static labels and columns into a sidecar config file', () => {
    expect(source).toContain("from './dictPageConfig'")
    expect(configSource).toContain('export const DICT_STATUS_LABEL')
    expect(configSource).toContain('export const DICT_DATA_GRID_COLUMNS')
  })
})
