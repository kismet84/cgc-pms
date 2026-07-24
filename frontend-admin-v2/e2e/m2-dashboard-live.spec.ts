import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator } from '@playwright/test'

const runLiveDashboard = process.env.V2_LIVE_DASHBOARD === '1'

function selectOption(control: Locator, value: string) {
  return control
    .click()
    .then(() => control.locator('..').locator(`[role="option"][data-value="${value}"]`).click())
}

const roles = [
  { label: '项目经理', path: '/api/dashboard/project-manager' },
  { label: '商务经理', path: '/api/dashboard/business-manager' },
  { label: '成本经理', path: '/api/dashboard/cost-manager' },
  { label: '采购经理', path: '/api/dashboard/purchase-manager' },
  { label: '生产经理', path: '/api/dashboard/production-manager' },
  { label: '总工程师', path: '/api/dashboard/chief-engineer' },
  { label: '财务', path: '/api/dashboard/finance' },
  { label: '管理层', path: '/api/dashboard/management' },
] as const

test.describe('M2 live eight-role dashboard', () => {
  test.skip(!runLiveDashboard, 'Set V2_LIVE_DASHBOARD=1 only against the local test/demo runtime')

  test('shell context survives cross-workspace navigation and reaches dashboard requests', async ({
    page,
  }) => {
    const projectId = '520000000000009002'
    const period = '2026-07'
    expect((await page.goto('/api/auth/dev-login?username=demo.manager'))?.ok()).toBe(true)
    await page.goto('/v2/project/list')
    await selectOption(page.locator('#global-project'), projectId)
    await expect(page.locator('#global-report-period')).toHaveAttribute('aria-disabled', 'true')

    await page.getByRole('link', { name: '工作台', exact: true }).click()
    await selectOption(page.locator('#global-report-period'), period)

    const dashboardResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/project-manager' &&
        url.searchParams.get('projectId') === projectId &&
        url.searchParams.get('month') === period
      )
    })
    await page.getByRole('button', { name: '刷新', exact: true }).click()

    expect((await dashboardResponse).ok()).toBe(true)
    await expect(page).toHaveURL(new RegExp(`projectId=${projectId}.*period=${period}`))
  })

  test('shell keeps project and report-period selectors active across dashboard roles', async ({
    page,
  }) => {
    const projectId = '520000000000009002'
    const period = '2026-07'
    const alertUrls: URL[] = []
    page.on('request', (request) => {
      const url = new URL(request.url())
      if (url.pathname === '/api/alerts') alertUrls.push(url)
    })
    expect((await page.goto('/api/auth/dev-login?username=admin'))?.ok()).toBe(true)
    await page.goto(`/v2/dashboard?role=mgmt&projectId=${projectId}&period=${period}`, {
      waitUntil: 'networkidle',
    })
    await expect(page.locator('#global-project')).toHaveAttribute('aria-disabled', 'false')
    await expect(page.locator('#global-report-period')).toHaveAttribute('aria-disabled', 'false')
    await expect(page.getByText('当前项目', { exact: true })).toBeVisible()
    expect(alertUrls.length).toBeGreaterThan(0)
    expect(
      alertUrls.some(
        (url) =>
          url.searchParams.get('projectId') === projectId &&
          url.searchParams.has('triggeredStart') &&
          url.searchParams.has('triggeredEnd'),
      ),
    ).toBe(true)

    alertUrls.length = 0
    await page.goto(`/v2/dashboard?role=bm&projectId=${projectId}&period=${period}`, {
      waitUntil: 'networkidle',
    })
    await expect(page.locator('#global-project')).toHaveAttribute('aria-disabled', 'false')
    await expect(page.locator('#global-report-period')).toHaveAttribute('aria-disabled', 'false')
    await expect(page.getByText('报告期（当前页面不适用）')).toHaveCount(0)
    expect(
      alertUrls.some(
        (url) =>
          url.searchParams.get('projectId') === projectId &&
          url.searchParams.has('triggeredStart') &&
          url.searchParams.has('triggeredEnd'),
      ),
    ).toBe(true)

    alertUrls.length = 0
    await page.goto(`/v2/dashboard?role=finance&projectId=${projectId}&period=${period}`, {
      waitUntil: 'networkidle',
    })
    await expect(page.locator('#global-project')).toHaveAttribute('aria-disabled', 'false')
    await expect(page.locator('#global-report-period')).toHaveAttribute('aria-disabled', 'false')
    expect(
      alertUrls.some(
        (url) =>
          url.searchParams.get('projectId') === projectId &&
          url.searchParams.has('triggeredStart') &&
          url.searchParams.has('triggeredEnd'),
      ),
    ).toBe(true)
  })

  test('cost trend follows project and card range selections without duplicate KPI summary', async ({
    page,
  }) => {
    const login = await page.goto('/api/auth/dev-login?username=demo.cost')
    expect(login?.ok()).toBe(true)
    await page.goto('/v2/dashboard?role=cost')

    const trendRows = page.locator('.trend-chart tbody tr')
    await expect(page.locator('.trend-chart canvas')).toBeVisible()
    await expect(trendRows).toHaveCount(7)
    await page.getByRole('button', { name: '近6个月' }).click()
    await expect(trendRows).toHaveCount(6)
    await page.getByRole('button', { name: '近3个月' }).click()
    await expect(trendRows).toHaveCount(3)

    const projectSelect = page.locator('#global-project')
    const activeProject = projectSelect
      .locator('..')
      .locator('[role="option"]', { hasText: '劳务分包在建演示项目' })
    const projectId = await activeProject.getAttribute('data-value')
    expect(projectId).toBeTruthy()
    const projectResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/cost-manager' &&
        url.searchParams.get('projectId') === projectId
      )
    })
    await selectOption(projectSelect, projectId!)
    expect((await projectResponse).ok()).toBe(true)
    await page.getByRole('button', { name: '当年累计' }).click()
    await expect(trendRows).toHaveCount(7)
    await expect(page.locator('.trend-summary')).toHaveCount(0)
  })

  test('cost manager expands the canonical two-level cost breakdown', async ({ page }) => {
    const projectId = '520000000000009002'
    expect((await page.goto('/api/auth/dev-login?username=demo.cost'))?.ok()).toBe(true)
    const breakdownResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === `/api/dashboard/project/${projectId}/cost-breakdown`
    })

    await page.goto(`/v2/dashboard?role=cost&projectId=${projectId}`)
    const response = await breakdownResponse
    expect(response.ok()).toBe(true)
    const envelope = (await response.json()) as {
      data: { subjectBreakdowns: Array<{ level: number; parentSubjectId: string }> }
    }
    expect(envelope.data.subjectBreakdowns.filter((item) => item.level === 1)).toHaveLength(1)
    expect(
      envelope.data.subjectBreakdowns.filter(
        (item) => item.level === 2 && item.parentSubjectId === '900001',
      ),
    ).toHaveLength(4)

    const panel = page.locator('#cost-breakdown')
    const rows = panel.locator('tbody tr')
    await expect(panel.getByText('成本科目分解', { exact: true })).toBeVisible()
    await expect(rows).toHaveCount(1)
    await expect(rows.first()).toContainText('合同履约成本')
    await panel.getByRole('button', { name: '展开', exact: true }).click()
    await expect(rows).toHaveCount(5)
    await expect(panel.getByText('招投标及前期费用', { exact: true })).toBeVisible()
    await expect(panel.getByText('采购阶段成本', { exact: true })).toBeVisible()
    await expect(panel.getByText('施工阶段成本', { exact: true })).toBeVisible()
    await expect(panel.getByText('项目间接费用', { exact: true })).toBeVisible()
    await expect(rows.nth(2)).toContainText('¥980,000.00')
    await panel.getByRole('button', { name: '收起', exact: true }).click()
    await expect(rows).toHaveCount(1)
  })

  test('finance view exposes project payment trend and closed-loop indicators', async ({
    page,
  }) => {
    const login = await page.goto('/api/auth/dev-login?username=demo.finance')
    expect(login?.ok()).toBe(true)
    await page.goto('/v2/dashboard?role=finance')

    const projectSelect = page.locator('#global-project')
    const projectId = '520000000000009002'
    await expect(
      projectSelect.locator('..').locator(`[role="option"][data-value="${projectId}"]`),
    ).toHaveText('劳务分包在建演示项目')
    const projectResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/finance' && url.searchParams.get('projectId') === projectId
      )
    })
    await selectOption(projectSelect, projectId)
    const response = await projectResponse
    expect(response.ok()).toBe(true)
    const envelope = (await response.json()) as {
      data: {
        pendingPaymentAmount: string
        approvedUnpaidAmount: string
        budgetAmount: string
        totalPaidAmount: string
        trendPoints: unknown[]
        contractFundBreakdowns: Array<{
          contractCode: string
          contractName: string
          contractAmount: string
          paidAmount: string
          approvingAmount: string
          approvedUnpaidAmount: string
          remainingAmount: string
          paymentRatio: string
          paymentRecords: unknown[]
        }>
      }
    }
    expect(envelope.data.pendingPaymentAmount).toBe('90000.00')
    expect(envelope.data.approvedUnpaidAmount).toBe('150000.00')
    expect(envelope.data.budgetAmount).toBe('3900000.00')
    expect(envelope.data.totalPaidAmount).toBe('990000.00')
    expect(envelope.data.trendPoints).toHaveLength(7)
    expect(envelope.data.contractFundBreakdowns).toHaveLength(4)
    expect(
      envelope.data.contractFundBreakdowns.find(
        (contract) => contract.contractName === '演示项目管理服务合同',
      ),
    ).toMatchObject({
      contractAmount: '800000.00',
      paidAmount: '940000.00',
      approvingAmount: '90000.00',
      approvedUnpaidAmount: '120000.00',
      remainingAmount: '0',
      paymentRatio: '117.50',
    })

    await expect(page.getByText('资金支付趋势', { exact: true })).toBeVisible()
    await expect(page.locator('.trend-chart canvas')).toBeVisible()
    await expect(page.locator('.trend-chart tbody tr')).toHaveCount(7)
    await expect(page.getByText('资金闭环指标')).toBeVisible()
    const breakdown = page.locator('#finance-contract-breakdown')
    const rows = breakdown.locator('tbody tr')
    await expect(breakdown.getByText('合同资金分解', { exact: true })).toBeVisible()
    await expect(rows).toHaveCount(4)
    const serviceContract = rows.filter({ hasText: '演示项目管理服务合同' })
    await serviceContract.getByRole('button', { name: '展开', exact: true }).click()
    await expect(rows).toHaveCount(9)
    await expect(breakdown.getByText('PROCESSING', { exact: true })).toBeVisible()
    await expect(breakdown.getByText(/^PMT-20260720-001 · \d{4}-\d{2}-\d{2}$/)).toBeVisible()
    await serviceContract.getByRole('button', { name: '收起', exact: true }).click()
    await expect(rows).toHaveCount(4)
  })

  test('business manager defaults to all and can switch between aggregate and specific context', async ({
    page,
  }) => {
    const login = await page.goto('/api/auth/dev-login?username=demo.business')
    expect(login?.ok()).toBe(true)

    const aggregateResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/business-manager' && !url.searchParams.has('projectId')
      )
    })
    await page.goto('/v2/dashboard?role=bm')
    expect((await aggregateResponse).ok()).toBe(true)

    const projectSelect = page.locator('#global-project')
    const periodSelect = page.locator('#global-report-period')
    await expect(projectSelect).toContainText('全部项目')
    await expect(
      projectSelect.locator('..').locator('[role="option"][data-value=""]'),
    ).toHaveAttribute('aria-selected', 'true')
    await expect(periodSelect).toContainText('全部报告期')
    await expect(
      periodSelect.locator('..').locator('[role="option"][data-value=""]'),
    ).toHaveAttribute('aria-selected', 'true')

    await expect(page.locator('#risk-list .risk-level').first()).toBeVisible()

    const projectId = await projectSelect
      .locator('..')
      .locator('[role="option"]')
      .nth(1)
      .getAttribute('data-value')
    expect(projectId).toBeTruthy()
    const projectResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/business-manager' &&
        url.searchParams.get('projectId') === projectId
      )
    })
    await selectOption(projectSelect, projectId!)
    expect((await projectResponse).ok()).toBe(true)
    await expect(page).toHaveURL(new RegExp(`projectId=${projectId}`))

    const period = await periodSelect
      .locator('..')
      .locator('[role="option"]')
      .nth(1)
      .getAttribute('data-value')
    expect(period).toBeTruthy()
    await selectOption(periodSelect, period!)
    await expect(page).toHaveURL(new RegExp(`period=${period}`))

    const restoredAggregateResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/business-manager' && !url.searchParams.has('projectId')
      )
    })
    await selectOption(projectSelect, '')
    await selectOption(periodSelect, '')
    expect((await restoredAggregateResponse).ok()).toBe(true)
    await expect(page).not.toHaveURL(/projectId=|period=/)
  })

  test('active project supplies real data for the remaining role dashboards', async ({ page }) => {
    const projectId = '520000000000009002'
    const cases = [
      {
        username: 'demo.manager',
        path: 'project-manager',
        assert: (data: Record<string, unknown>) => {
          expect(Number(data.pendingTaskCount)).toBe(2)
          expect(Number(data.expiringContractCount)).toBe(1)
          expect(Number(data.laggingProjectCount)).toBe(1)
        },
      },
      {
        username: 'demo.business',
        path: 'business-manager',
        assert: (data: Record<string, unknown>) => {
          expect(data.contractChangeAmount).toBe('90000.00')
          expect(data.varOrderAmount).toBe('70000.00')
          expect(data.settlementProgress).toBe('1/1')
        },
      },
      {
        username: 'demo.purchase',
        path: 'purchase-manager',
        assert: (data: Record<string, unknown>) => {
          expect(Number(data.pendingRequestCount)).toBe(1)
          expect(Number(data.activeOrderCount)).toBe(2)
          expect(Number(data.overdueDeliveryCount)).toBe(2)
          expect(Number(data.pendingReceiptCount)).toBe(1)
          expect(Number(data.lowStockItemCount)).toBe(1)
        },
      },
      {
        username: 'demo.production',
        path: 'production-manager',
        assert: (data: Record<string, unknown>) => {
          expect(Number(data.receiptCount)).toBe(1)
          expect(Number(data.requisitionCount)).toBe(1)
          expect(Number(data.pendingStockOutCount)).toBe(1)
          expect(data.confirmedMeasureAmount).toBe('460000.00')
        },
      },
      {
        username: 'demo.chief',
        path: 'chief-engineer',
        assert: (data: Record<string, unknown>) => {
          expect(Number(data.pendingReviewCount)).toBe(1)
          expect(Number(data.pendingCoordinationCount)).toBe(1)
          expect(Number(data.openIssueCount)).toBe(3)
          expect(Number(data.overdueCount)).toBe(2)
        },
      },
    ]

    for (const role of cases) {
      expect((await page.goto(`/api/auth/dev-login?username=${role.username}`))?.ok()).toBe(true)
      const response = await page.request.get(`/api/dashboard/${role.path}?projectId=${projectId}`)
      expect(response.ok()).toBe(true)
      const body = (await response.json()) as { data: Record<string, unknown> }
      role.assert(body.data)
    }

    await page.goto('/v2/dashboard?role=chiefEngineer')
    await selectOption(page.locator('#global-project'), projectId)
    await expect(page.getByText('经营动态', { exact: true })).toBeVisible()
    await expect(page.locator('#cost-trend').getByText('楼梯节点做法逾期未闭环')).toBeVisible()
    await expect(page.getByText('当前角色暂无趋势数据')).toHaveCount(0)
    const activityList = page.locator('#cost-trend .dashboard-activity-list')
    expect(
      await activityList.evaluate((element) => element.scrollHeight <= element.clientHeight),
    ).toBe(true)

    expect((await page.goto('/api/auth/dev-login?username=demo.manager'))?.ok()).toBe(true)
    await page.goto('/v2/dashboard?role=pm')
    await page.getByRole('button', { name: '查看最高风险', exact: true }).click()
    await expect(page.locator('.risk-filter summary')).toHaveText('高')
    await expect(page.locator('#risk-list .risk-level').first()).toHaveText('高')
    await page.getByRole('button', { name: '查看最高风险', exact: true }).click()
    await expect(page.locator('.risk-filter summary')).toHaveText('全部预警')
  })

  test('every role exposes scoped authoritative alerts without severity distortion', async ({
    page,
  }) => {
    const projectId = '520000000000009002'
    const roleCases = [
      { role: 'pm', username: 'demo.manager' },
      { role: 'bm', username: 'demo.business' },
      { role: 'cost', username: 'demo.cost' },
      { role: 'purchase', username: 'demo.purchase' },
      { role: 'production', username: 'demo.production' },
      { role: 'chiefEngineer', username: 'demo.chief' },
      { role: 'finance', username: 'demo.finance' },
      { role: 'mgmt', username: 'admin' },
    ] as const
    const riskLabel = (severity: string) => {
      if (['CRITICAL', 'HIGH'].includes(severity)) return '高'
      if (severity === 'MEDIUM') return '中'
      if (severity === 'LOW') return '低'
      return '其他'
    }

    for (const roleCase of roleCases) {
      expect((await page.goto(`/api/auth/dev-login?username=${roleCase.username}`))?.ok()).toBe(
        true,
      )
      const alertResponse = page.waitForResponse((response) => {
        const url = new URL(response.url())
        return url.pathname === '/api/alerts' && url.searchParams.get('projectId') === projectId
      })
      await page.goto(`/v2/dashboard?role=${roleCase.role}&projectId=${projectId}`)
      const response = await alertResponse
      expect(response.ok()).toBe(true)
      const body = (await response.json()) as {
        data: { records: Array<{ severity: string }> }
      }
      expect(body.data.records.length).toBeGreaterThan(0)
      const riskBadges = page.locator('#risk-list .risk-level')
      await expect(riskBadges).toHaveCount(body.data.records.length)
      expect(await riskBadges.allTextContents()).toEqual(
        body.data.records.map((record) => riskLabel(record.severity)),
      )
    }
  })

  test('opens the alert status menu downward without dialog clipping', async ({ page }) => {
    expect((await page.goto('/api/auth/dev-login?username=admin'))?.ok()).toBe(true)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/v2/dashboard?role=mgmt')
    await page.locator('#risk-list tbody tr').first().click()

    const dialog = page.getByRole('dialog')
    const status = dialog.getByRole('button', { name: /^目标状态：/ })
    await status.click()
    const menu = status.locator('..').locator('.v2-select__menu')
    await expect(menu).toBeVisible()

    const statusBounds = await status.boundingBox()
    const menuBounds = await menu.boundingBox()
    expect(menuBounds?.y).toBeGreaterThanOrEqual(
      (statusBounds?.y ?? Number.POSITIVE_INFINITY) + (statusBounds?.height ?? 0),
    )
    await expect(dialog).toHaveCSS('overflow', 'visible')
  })

  test('defaults to aggregate context and loads the real management view in three viewports', async ({
    page,
  }) => {
    const runtimeProblems: string[] = []
    const dashboardRequests: string[] = []
    const dashboardRequestUrls: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'warning' || message.type() === 'error') {
        runtimeProblems.push(`${message.type()} ${message.text()}`)
      }
    })
    page.on('pageerror', (error) => runtimeProblems.push(`pageerror ${error.message}`))
    page.on('response', (response) => {
      const path = new URL(response.url()).pathname
      if (path.startsWith('/api/dashboard/')) {
        dashboardRequests.push(path)
        dashboardRequestUrls.push(response.url())
        if (response.status() >= 400) runtimeProblems.push(`${response.status()} ${path}`)
      }
    })

    const login = await page.goto('/api/auth/dev-login?username=admin')
    expect(login?.ok()).toBe(true)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/v2/dashboard?role=pm')
    await expect(page.getByText('项目经营健康度')).toBeVisible()
    await expect(page.locator('#global-project')).toContainText('全部项目')
    await expect(
      page.locator('#global-project').locator('..').locator('[role="option"][data-value=""]'),
    ).toHaveAttribute('aria-selected', 'true')
    await expect(page.locator('#global-report-period')).toContainText('全部报告期')
    await expect(
      page.locator('#global-report-period').locator('..').locator('[role="option"][data-value=""]'),
    ).toHaveAttribute('aria-selected', 'true')
    expect(
      dashboardRequestUrls.some((url) => {
        const requestUrl = new URL(url)
        return (
          requestUrl.pathname === '/api/dashboard/project-manager' &&
          !requestUrl.searchParams.has('projectId') &&
          !requestUrl.searchParams.has('month')
        )
      }),
    ).toBe(true)
    await expect(
      page.getByRole('navigation', { name: '驾驶舱角色视图' }).getByRole('button'),
    ).toHaveCount(roles.length)

    const managementResponse = page.waitForResponse(
      (response) => new URL(response.url()).pathname === '/api/dashboard/management',
    )
    await page.getByRole('button', { name: '管理层', exact: true }).click()
    expect((await managementResponse).ok()).toBe(true)
    await expect(page.getByText('项目经营健康度')).toBeVisible()

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/v2/dashboard?role=mgmt')
      await expect(page.getByText('项目经营健康度')).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const axe = await new AxeBuilder({ page }).analyze()
      expect(
        axe.violations.filter((violation) =>
          ['serious', 'critical'].includes(violation.impact ?? ''),
        ),
      ).toEqual([])
    }

    expect(
      dashboardRequests.every((path) =>
        ['/api/dashboard/project-manager', '/api/dashboard/management'].includes(path),
      ),
    ).toBe(true)
    expect(runtimeProblems).toEqual([])
  })
})
