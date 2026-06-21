import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../todo.vue'), 'utf-8')

describe('approval work list route titles', () => {
  it('renders breadcrumb and subtitle from the active approval tab', () => {
    expect(source).toMatch(/<a-breadcrumb-item[^>]*>审批中心<\/a-breadcrumb-item>/)
    expect(source).toMatch(
      /<a-breadcrumb-item[^>]*>\{\{ pageHeaderTitle\(\) \}\}<\/a-breadcrumb-item>/,
    )
    expect(source).toMatch(/<p[^>]*>\s*\{\{ pageHeaderSubtitle\(\) \}\}[\s\S]*?<\/p>/)
  })
})
