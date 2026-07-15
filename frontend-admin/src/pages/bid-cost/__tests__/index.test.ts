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
    expect(source).not.toMatch(/删除|标记中标|标记未中标/)
    expect(source).not.toMatch(/deleteBid|markAsWon|markAsLost/)
    expect(source).not.toContain('tenantId')
    expect(source).not.toContain('projectId:')
    expect(source).not.toContain('amount')
  })

  it('opens a read-only detail for desktop and mobile without exposing tenant fields', () => {
    expect(source).toContain('getBidCost(row.id)')
    expect(source).toContain('const requestId = ++detailRequestId')
    expect(source).toContain('requestId === detailRequestId')
    expect(source).toContain('detailRequestId += 1')
    expect(source).toContain('detail.value = null')
    expect(source).toContain('detailOpen.value = true')
    expect(source).toContain("message.error(errorMessage(error, '加载投标详情失败'))")
    expect(source).toContain('data-testid="detail-button"')
    expect(source).toContain('data-testid="mobile-detail-button"')
    expect(source).toContain('data-testid="detail-modal"')
    expect(source).toContain('@after-close="clearDetail"')
    expect(source).toContain('投标项目名称')
    expect(source).toContain('关联项目')
    expect(source).toContain('更新时间')
    expect(source).not.toContain('tenantId')
    expect(source).not.toMatch(/deleteBid|markAsWon|markAsLost/)
  })

  it('offers a permission and state guarded controlled edit for desktop and mobile', () => {
    expect(source).toContain("userStore.hasPermission('bid:edit')")
    expect(source).toContain("canEdit && record.bidStatus === 'BIDDING'")
    expect(source).toContain("canEdit && row.bidStatus === 'BIDDING'")
    expect(source).toContain('data-testid="edit-button"')
    expect(source).toContain('data-testid="mobile-edit-button"')
    expect(source).toContain('data-testid="edit-modal"')
    expect(source).toContain('await updateBidCost(editingId.value, {')
    expect(source).toContain('bidProjectName,')
    expect(source).toContain('remark: editForm.remark.trim() || undefined')
    expect(source).not.toContain('tenantId')
    expect(source).not.toContain('amount')
  })
})
