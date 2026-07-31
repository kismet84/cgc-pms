import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from '@/router'
import { canOpenReportCatalogPage, hasReportCatalogExportEntry } from '../catalog-entry'

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

describe('hasReportCatalogExportEntry', () => {
  it('仅对具备真实页面且声明支持导出的目录项返回 true', () => {
    expect(
      hasReportCatalogExportEntry(
        {
          sourceType: 'page',
          status: 'available',
          target: '/alert',
          exportSupport: true,
        },
        router.resolve,
      ),
    ).toBe(true)
  })

  it('拒绝 API-only 或未声明导出的目录项，避免伪导出入口', () => {
    expect(
      hasReportCatalogExportEntry(
        {
          sourceType: 'api',
          status: 'api_only',
          target: '/alerts/processing-report',
          exportSupport: true,
        },
        router.resolve,
      ),
    ).toBe(false)

    expect(
      hasReportCatalogExportEntry(
        {
          sourceType: 'page',
          status: 'available',
          target: '/report/not-found',
          exportSupport: true,
        },
        router.resolve,
      ),
    ).toBe(false)

    expect(
      hasReportCatalogExportEntry(
        {
          sourceType: 'page',
          status: 'available',
          target: '/dashboard',
          exportSupport: false,
        },
        router.resolve,
      ),
    ).toBe(false)
  })
})
