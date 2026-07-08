import { describe, expect, it } from 'vitest'
import {
  readPositiveIntQuery,
  readStringQuery,
  replaceListQuery,
} from '../listPageQuery'

describe('listPageQuery helpers', () => {
  it('reads string query values and ignores array tails', () => {
    expect(readStringQuery('invoice-1')).toBe('invoice-1')
    expect(readStringQuery(['invoice-2', 'invoice-3'])).toBe('invoice-2')
    expect(readStringQuery(undefined)).toBeUndefined()
  })

  it('reads positive integers with fallback', () => {
    expect(readPositiveIntQuery('3', 1)).toBe(3)
    expect(readPositiveIntQuery(['5'], 1)).toBe(5)
    expect(readPositiveIntQuery('0', 9)).toBe(9)
    expect(readPositiveIntQuery('abc', 7)).toBe(7)
  })

  it('drops empty query values and keeps unrelated keys', () => {
    expect(
      replaceListQuery(
        { redirect: '/dashboard', keyword: 'old', pageNo: '9' },
        {
          keyword: '',
          payRecordId: undefined,
          verifyStatus: 'ABNORMAL',
          pageNo: 1,
          pageSize: 20,
        },
        ['keyword', 'payRecordId', 'verifyStatus', 'pageNo', 'pageSize'],
      ),
    ).toEqual({
      redirect: '/dashboard',
      verifyStatus: 'ABNORMAL',
      pageNo: '1',
      pageSize: '20',
    })
  })
})
