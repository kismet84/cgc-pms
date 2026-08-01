import { mount } from '@vue/test-utils'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { V2Pagination } from '@/components'

function pageSources(root = resolve('src/pages')): string[] {
  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(root, entry.name)
    return entry.isDirectory()
      ? pageSources(path)
      : entry.name.endsWith('.vue')
        ? [readFileSync(path, 'utf-8')]
        : []
  })
}

describe('V2Pagination', () => {
  it('keeps the 10-row contract and emits valid page changes', async () => {
    const wrapper = mount(V2Pagination, {
      props: { total: '15', pageNo: 1, label: '项目台账分页' },
    })

    expect(wrapper.attributes('aria-label')).toBe('项目台账分页')
    expect(wrapper.text()).toBe('共 15 条 上一页 第 1 页 下一页')
    expect(wrapper.get('button:first-of-type').attributes('disabled')).toBeDefined()

    await wrapper.get('button:last-of-type').trigger('click')
    expect(wrapper.emitted('update:pageNo')).toEqual([[2]])
  })

  it('keeps pagination at 10 rows and never hides a pager on short result sets', () => {
    for (const source of pageSources()) {
      const hasPager = source.includes('<V2Pagination') || /aria-label="[^"]*分页"/.test(source)
      if (!hasPager) continue

      expect(source).toMatch(/(?:pageSize|PageSize)\s*(?:=|:)\s*(?:ref\()?10/)
      expect(source).not.toMatch(
        /<template\s+v-if="[^"]+"\s+#footer>[\s\S]{0,240}aria-label="[^"]*分页"/,
      )
      for (const block of source.match(/<nav\b[^>]*aria-label="[^"]*分页"[\s\S]*?<\/nav>/g) ?? []) {
        expect(block).toMatch(/共\s*\{\{/)
      }
      for (const footer of source.match(/<template #footer>[\s\S]*?<\/template>/g) ?? []) {
        expect((footer.match(/<V2Pagination/g) ?? []).length).toBeLessThanOrEqual(1)
      }
    }
  })
})
