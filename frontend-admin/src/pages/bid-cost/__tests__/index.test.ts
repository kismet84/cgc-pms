import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = readFileSync(resolve(__dirname, '../index.vue'), 'utf8')

describe('bid cost read-only page contract', () => {
  it('loads the typed list and exposes the required read-only states', () => {
    expect(source).toContain('getBidCosts')
    expect(source).toContain('onMounted(fetchRows)')
    expect(source).toContain('暂无投标项目')
    expect(source).toContain('message.error(errorMessage(error))')
    expect(source).toContain('total.value = Number(result.total ?? 0)')
    expect(source).toContain('data-testid="search-button"')
    expect(source).toContain('data-testid="reset-button"')
    expect(source).toContain('data-testid="refresh-button"')
    expect(source).toContain('@change="changePage"')
  })

  it('does not expose any bid write operation', () => {
    expect(source).not.toMatch(/新建|编辑|删除|标记中标|标记未中标/)
    expect(source).not.toMatch(/postBid|putBid|deleteBid|markAsWon|markAsLost/)
    expect(source).not.toContain('tenantId')
  })
})
