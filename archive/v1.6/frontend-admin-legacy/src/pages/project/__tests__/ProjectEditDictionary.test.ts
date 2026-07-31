import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../edit.vue'), 'utf-8')

describe('project edit dictionary contract', () => {
  it('loads project type codes from dictionary and blocks unknown values', () => {
    expect(source).toContain("getDictDataByCode('project_type')")
    expect(source).toContain(
      'projectTypeOptions.value.some((item) => item.value === formData.projectType)',
    )
    expect(source).not.toContain("value: '施工总承包'")
    expect(source).not.toContain("value: '专业分包'")
  })
})
