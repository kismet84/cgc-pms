import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))

function readProject(path: string) {
  return readFileSync(resolve(currentDir, '..', path), 'utf-8')
}

function readTarget(path: string) {
  return readFileSync(resolve(currentDir, '../../cost-target', path), 'utf-8')
}

const sources = {
  projectList: readProject('index.vue'),
  projectOverview: readProject('overview.vue'),
  projectMembers: readProject('members.vue'),
  projectEdit: readProject('edit.vue'),
  targetList: readTarget('index.vue'),
  targetEdit: readTarget('edit.vue'),
}

const combinedSource = Object.values(sources).join('\n')

describe('Project and target UI redesign source markers', () => {
  it('applies the approved workspace UI language to project and target pages', () => {
    for (const marker of [
      'project-target-redesign',
      'pt-kpi-strip',
      'pt-filter-surface',
      'pt-analysis-rail',
      'pt-panel',
    ]) {
      expect(combinedSource).toContain(marker)
    }

    for (const label of [
      '项目列表',
      '项目总览',
      '项目成员',
      '项目状态分布',
      '项目经营概览',
      '目标管理',
      '目标总额',
      '已锁定成本',
      '动态成本',
      '偏差预警',
    ]) {
      expect(combinedSource).toContain(label)
    }

    expect(combinedSource).not.toContain('目标成本管理')
  })
})
