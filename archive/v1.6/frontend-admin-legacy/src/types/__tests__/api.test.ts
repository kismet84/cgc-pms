import { describe, expect, it, expectTypeOf } from 'vitest'
import type { PageParams } from '../api'

describe('PageParams', () => {
  it('accepts flat scalar query params only', () => {
    const params: PageParams = {
      pageNo: 1,
      pageSize: 20,
      keyword: '合同',
      projectId: 'p1',
      includeDisabled: false,
    }

    expect(params.pageSize).toBe(20)
    expectTypeOf(params.keyword).toEqualTypeOf<string | number | boolean | null | undefined>()
  })

  it('rejects nested object query params at type level', () => {
    expect(true).toBe(true)

    // @ts-expect-error PageParams should stay flat for query serialization
    const invalid: PageParams = { pageSize: 20, filters: { keyword: '合同' } }
    void invalid
  })
})
