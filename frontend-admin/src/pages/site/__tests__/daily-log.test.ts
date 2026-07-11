import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../daily-log.vue'), 'utf-8')

describe('site daily log page', () => {
  it('keeps the first release to project daily facts and one-way submission', () => {
    expect(source).toContain('constructionContent')
    expect(source).toContain('issuesDelays')
    expect(source).toContain('nextDayPlan')
    expect(source).toContain("record.status === 'DRAFT'")
    expect(source).toContain('submitSiteDailyLog')
    expect(source).not.toMatch(/weather|equipment|offline|geolocation/i)
  })

  it('reuses the file API and renders loading empty and error states', () => {
    expect(source).toContain("const SITE_DAILY_LOG = 'SITE_DAILY_LOG'")
    expect(source).toContain('uploadFile(file, SITE_DAILY_LOG')
    expect(source).toContain('listFiles(SITE_DAILY_LOG')
    expect(source).toContain('deleteFile(file.id)')
    expect(source).toContain('listError')
    expect(source).toContain('暂无现场日报')
  })
})
