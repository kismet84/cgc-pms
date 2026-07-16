import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('quality safety rectification workbench', () => {
  it('covers every node in the required business chain', () => {
    expect(source).toContain('检查计划 → 检查记录 → 问题单 → 整改 → 复验 → 处罚/成本 → 合作方评价')
    expect(source).toContain('submitQualityInspection')
    expect(source).toContain('submitQualityRectification')
    expect(source).toContain('reinspectQualityRectification')
    expect(source).toContain('postQualityConsequence')
    expect(source).toContain('getQualityTrace')
  })

  it('requires stage-specific evidence instead of a generic attachment bucket', () => {
    expect(source).toContain("'INSPECTION_EVIDENCE'")
    expect(source).toContain("'ISSUE_EVIDENCE'")
    expect(source).toContain("'RECTIFICATION_EVIDENCE'")
    expect(source).toContain("'REINSPECTION_EVIDENCE'")
  })

  it('exposes role-specific actions and reverse trace', () => {
    expect(source).toContain("can('quality:safety:rectify')")
    expect(source).toContain("can('quality:safety:reinspect')")
    expect(source).toContain("can('quality:safety:consequence')")
    expect(source).toContain('全链路')
    expect(source).toContain('成本台账')
  })
})
