import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = readFileSync(resolve(__dirname, '../index.vue'), 'utf8')

describe('bid cost page contract', () => {
  it('loads the typed list and exposes the required list states', () => {
    expect(source).toContain('getBidCosts')
    expect(source).toContain('onMounted(fetchRows)')
    expect(source).toContain('暂无投标项目')
    expect(source).toContain("message.error(errorMessage(error, '加载投标成本失败'))")
    expect(source).toContain('total.value = Number(result.total ?? 0)')
    expect(source).toContain('data-testid="search-button"')
    expect(source).toContain('data-testid="reset-button"')
    expect(source).toContain('data-testid="refresh-button"')
    expect(source).toContain('@change="changePage"')
  })

  it('exposes only the permission-gated controlled create operation', () => {
    expect(source).toContain("userStore.hasPermission('bid:add')")
    expect(source).toContain('v-if="canCreate"')
    expect(source).toContain('createBidCost({')
    expect(source).toContain('bidProjectName,')
    expect(source).toContain('remark: createForm.remark.trim() || undefined')
    expect(source).toContain("message.error(errorMessage(error, '新建投标项目失败'))")
    expect(source).not.toMatch(/编辑|删除|标记中标|标记未中标/)
    expect(source).not.toMatch(/updateBid|deleteBid|markAsWon|markAsLost/)
    expect(source).not.toContain('tenantId')
    expect(source).not.toContain('projectId:')
    expect(source).not.toContain('amount')
  })
})
