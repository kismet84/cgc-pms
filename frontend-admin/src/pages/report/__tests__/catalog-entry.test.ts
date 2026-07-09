import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from '@/router'
import { canOpenReportCatalogPage } from '../catalog-entry'

const router = createRouter({
  history: createMemoryHistory(),
  routes,
})

describe('canOpenReportCatalogPage', () => {
  it('允许真实页面路由打开', () => {
    expect(
      canOpenReportCatalogPage(
        {
          sourceType: 'page',
          target: '/dashboard',
        },
        router.resolve,
      ),
    ).toBe(true)
  })

  it('拒绝 API-only 目录项，避免伪跳转到 404', () => {
    expect(
      canOpenReportCatalogPage(
        {
          sourceType: 'api',
          target: '/contracts/kpi',
        },
        router.resolve,
      ),
    ).toBe(false)
  })

  it('拒绝未注册的页面目标', () => {
    expect(
      canOpenReportCatalogPage(
        {
          sourceType: 'page',
          target: '/report/not-found',
        },
        router.resolve,
      ),
    ).toBe(false)
  })
})
