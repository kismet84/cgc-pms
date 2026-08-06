import { expect, test } from '@playwright/test'
import type { Route } from '@playwright/test'

test('master-data deep links render server facts and preserve material redirect state', async ({
  page,
}) => {
  const success = (route: Route, data: unknown) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', traceId: 'e2e', data }),
    })

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/api/auth/userinfo')) {
      return success(route, {
        tenantId: '0',
        userId: '7',
        username: 'master.user',
        roles: ['USER'],
        permissions: ['partner:query', 'org:list', 'material:dict:list'],
      })
    }
    if (path.endsWith('/api/system/dict/data/by-code/partner_type')) {
      return success(route, [{ dictLabel: '供应商', dictValue: 'SUPPLIER', status: 'ENABLE' }])
    }
    if (path.endsWith('/api/partners')) {
      return success(route, {
        records: [
          {
            id: '101',
            partnerCode: 'PTN-101',
            partnerName: '服务端合作方',
            partnerType: 'SUPPLIER',
            status: 'ENABLE',
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      })
    }
    if (path.endsWith('/api/partners/101')) {
      return success(route, {
        id: '101',
        partnerCode: 'PTN-101',
        partnerName: '服务端合作方',
        partnerType: 'SUPPLIER',
        contactPhone: '13800000000',
        status: 'ENABLE',
      })
    }
    if (path.endsWith('/api/org/companies')) {
      return success(route, {
        records: [{ id: '1', companyCode: 'C1', companyName: '一公司', status: 'ENABLE' }],
        total: 1,
        pageNo: 1,
        pageSize: 200,
      })
    }
    if (path.endsWith('/api/org/departments/tree')) {
      return success(route, [
        {
          id: '2',
          companyId: '1',
          parentId: '0',
          deptCode: 'D1',
          deptName: '工程部',
          orderNum: 0,
          status: 'ENABLE',
          children: [],
        },
      ])
    }
    if (path.endsWith('/api/org/positions')) {
      return success(route, {
        records: [
          {
            id: '3',
            companyId: '1',
            departmentId: '2',
            positionCode: 'P1',
            positionName: '经理',
            status: 'ENABLE',
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 200,
      })
    }
    if (path.endsWith('/api/material-categories')) {
      return success(route, [
        { id: '9', categoryCode: 'STEEL', categoryName: '钢材', status: 'ENABLE' },
      ])
    }
    if (path.endsWith('/api/materials')) {
      return success(route, {
        records: [
          {
            id: '8',
            materialCode: 'MAT-8',
            materialName: '钢筋',
            categoryId: '9',
            taxInclusiveInfoPrice: '43.000000',
            purchasePrice: '41.500000',
            defaultTaxRate: '13.00',
            status: 'ENABLE',
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      })
    }
    return route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'E2E_API_UNSTUBBED',
        message: path,
        traceId: 'e2e',
        data: null,
      }),
    })
  })

  await page.goto('/partner?source=e2e#list')
  await expect(page).toHaveURL(/\/partner\?source=e2e#list$/)
  await expect(page.getByRole('heading', { level: 1, name: '客户管理' })).toBeVisible()
  await expect(page.getByText('服务端合作方')).toBeVisible()
  await expect(page.getByText('13800000000')).toHaveCount(0)
  await page.getByRole('button', { name: '打开合作方 PTN-101' }).click()
  const partnerDetail = page.getByRole('dialog', { name: '合作方详情' })
  await expect(partnerDetail).toContainText('13800000000')
  await expect(page).toHaveURL(/\/partner\?source=e2e#list$/)

  await page.goto('/org?view=tree')
  await expect(page.getByRole('heading', { level: 1, name: '组织架构' })).toBeVisible()
  await expect(page.getByText('一公司').first()).toBeVisible()
  await expect(page.getByText('工程部').first()).toBeVisible()
  await expect(page.getByText('经理')).toBeVisible()

  await page.goto('/material?source=legacy#dictionary')
  await expect(page).toHaveURL(/\/material\/dictionary\?source=legacy#dictionary$/)
  await expect(page.getByRole('heading', { level: 1, name: '材料字典' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '含税信息价' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '采购价' })).toBeVisible()
  await expect(page.getByText('¥43.00')).toBeVisible()
  await expect(page.getByText('¥41.50')).toBeVisible()
  await expect(page.getByText('13.00')).toBeVisible()
  await expect(page.getByRole('button', { name: '导出模板' })).toBeVisible()
  await expect(page.getByRole('button', { name: '导入资料' })).toHaveCount(0)
})
