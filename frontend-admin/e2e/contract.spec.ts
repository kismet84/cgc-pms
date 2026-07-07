import {
  test,
  expect,
  type Browser,
  type BrowserContext,
  type Locator,
  type Page,
} from '@playwright/test'

const SYSTEM_ERROR = '系统异常，请稍后重试'

let sharedContext: BrowserContext
let sharedPage: Page
let createdContractName: string | null = null

async function createAuthenticatedPage(browser: Browser) {
  const context = await browser.newContext({ storageState: 'e2e/.auth/admin.json' })
  const page = await context.newPage()
  return { context, page }
}

async function selectFirstOption(select: Locator) {
  await select.click()
  const dropdown = select
    .page()
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
    .last()
  await expect(dropdown).toBeVisible({ timeout: 10000 })
  const option = dropdown
    .locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
    .first()
  await expect(option).toBeVisible({ timeout: 10000 })
  await option.click()
}

async function selectOptionByText(select: Locator, text: string) {
  await select.click()
  const dropdown = select
    .page()
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
    .last()
  await expect(dropdown).toBeVisible({ timeout: 10000 })
  const options = dropdown.locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
  const preferredOption = options.filter({ hasText: text }).first()
  const option = (await preferredOption.isVisible().catch(() => false))
    ? preferredOption
    : options.first()
  await expect(option).toBeVisible({ timeout: 10000 })
  await option.click()
}

async function selectTodayFromPicker(picker: Locator) {
  await picker.click()
  const pickerDropdown = picker
    .page()
    .locator('.ant-picker-dropdown:not(.ant-picker-dropdown-hidden)')
    .last()
  await expect(pickerDropdown).toBeVisible({ timeout: 10000 })
  await pickerDropdown.locator('.ant-picker-cell-today .ant-picker-cell-inner').first().click()
  await expect(pickerDropdown)
    .toBeHidden({ timeout: 10000 })
    .catch(() => {})
}

async function selectTodayFromDatePicker(page: Page, label: string) {
  await selectTodayFromPicker(
    page.locator(`.ant-form-item:has(.ant-form-item-label:has-text("${label}")) .ant-picker`),
  )
}

async function fillContractBasicInfo(page: Page) {
  const contractName = `E2E草稿合同-${Date.now()}`
  await page.fill('input[placeholder="请输入合同名称"]', contractName)
  await selectOptionByText(
    page.locator('.ant-form-item:has(.ant-form-item-label:has-text("合同类型")) .ant-select'),
    '分包合同',
  )
  await selectFirstOption(
    page.locator('.ant-form-item:has(.ant-form-item-label:has-text("所属项目")) .ant-select'),
  )
  await page
    .locator(
      '.ant-form-item:has(.ant-form-item-label:has-text("合同金额")) .ant-input-number-input',
    )
    .fill('1000')
  await selectFirstOption(
    page.locator('.ant-form-item:has(.ant-form-item-label:has-text("甲方")) .ant-select'),
  )
  await selectFirstOption(
    page.locator('.ant-form-item:has(.ant-form-item-label:has-text("乙方")) .ant-select'),
  )
  await selectTodayFromDatePicker(page, '签订日期')
  return contractName
}

async function waitForContractLedger(page: Page) {
  await page.goto('/contract/ledger')
  await expect(page.locator('.cl-redesign-page')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.cl-query-panel')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.vxe-table').first()).toBeVisible({ timeout: 10000 })
}

test.describe('Contract draft-save and ledger regression', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async ({ browser }) => {
    const auth = await createAuthenticatedPage(browser)
    sharedContext = auth.context
    sharedPage = auth.page
  })

  test.afterAll(async () => {
    await sharedPage?.close()
    await sharedContext?.close()
  })

  test('blocks next step when required basic fields are missing', async () => {
    await sharedPage.goto('/contract/create')
    await expect(sharedPage.locator('input[placeholder="请输入合同名称"]')).toBeVisible({
      timeout: 10000,
    })

    await sharedPage.getByRole('button', { name: '下一步' }).click()

    await expect(
      sharedPage.locator('.ant-form-item-explain-error').filter({ hasText: '请输入合同名称' }),
    ).toBeVisible()
    await expect(
      sharedPage.locator('.ant-form-item-explain-error').filter({ hasText: '请选择合同类型' }),
    ).toBeVisible()
    await expect(sharedPage).toHaveURL(/\/contract\/create/)
    await expect(sharedPage.locator('input[placeholder="请输入合同名称"]')).toBeVisible()
    await expect(sharedPage.getByText('保存失败，请稍后重试')).toHaveCount(0)
    await expect(sharedPage.getByText(SYSTEM_ERROR)).toHaveCount(0)
  })

  test('saves draft with one detail and one payment term through composite endpoint', async () => {
    await sharedPage.goto('/contract/create')
    await expect(sharedPage.locator('input[placeholder="请输入合同名称"]')).toBeVisible({
      timeout: 10000,
    })
    await expect(sharedPage.getByRole('button', { name: '下一步' })).toBeVisible({ timeout: 10000 })

    const contractName = await fillContractBasicInfo(sharedPage)
    await sharedPage.getByRole('button', { name: '下一步' }).click()

    await expect(sharedPage.locator('.item-editor')).toBeVisible({ timeout: 10000 })
    await sharedPage.getByRole('button', { name: '添加明细' }).click()
    const itemRow = sharedPage.locator('.item-editor .ant-table-tbody tr.ant-table-row').first()
    await itemRow.locator('input[placeholder="请输入名称"]').fill('AUTO')
    await itemRow.locator('input[placeholder="规格"]').fill('1')
    await itemRow.locator('.ant-input-number-input').nth(0).fill('1')
    await itemRow.locator('.ant-input-number-input').nth(1).fill('1000')
    await sharedPage.getByRole('button', { name: '下一步' }).click()

    await expect(sharedPage.locator('.term-editor')).toBeVisible({ timeout: 10000 })
    await sharedPage.getByRole('button', { name: '添加付款条款' }).click()
    const termRow = sharedPage.locator('.term-editor .ant-table-tbody tr.ant-table-row').first()
    await termRow.locator('input[placeholder="如：预付款、进度款"]').fill('term1')
    await termRow.locator('.ant-input-number-input').nth(0).fill('100')
    await termRow.locator('.ant-input-number-input').nth(1).fill('1000')
    await termRow.locator('input[placeholder="付款触发条件"]').fill('auto')
    await selectTodayFromPicker(termRow.locator('.ant-picker'))
    await sharedPage.getByRole('button', { name: '下一步' }).click()

    await expect(sharedPage.locator('.cf-review')).toBeVisible({ timeout: 10000 })
    await sharedPage.getByRole('button', { name: '保存草稿' }).click()

    await expect(sharedPage.getByText('合同已保存为草稿')).toBeVisible({ timeout: 15000 })
    await sharedPage.waitForURL(/\/contract\/ledger/, { timeout: 15000 })
    await expect(sharedPage.getByText('保存失败，请稍后重试')).toHaveCount(0)
    await expect(sharedPage.getByText(SYSTEM_ERROR)).toHaveCount(0)
    createdContractName = contractName
  })

  test('created contract appears in ledger after draft save', async () => {
    expect(createdContractName, '需要确认：前置草稿合同未创建成功').toBeTruthy()
    await waitForContractLedger(sharedPage)

    await expect(
      sharedPage.locator('.cl-table-title').filter({ hasText: '合同列表' }),
    ).toBeVisible()
    const headerText = (await sharedPage.locator('.vxe-header--column').allTextContents()).join(' ')
    expect(headerText).toContain('合同编号')
    expect(headerText).toContain('合同名称')
    expect(headerText).toContain('合同状态')

    await expect(
      sharedPage.locator('.cl-query-actions button').filter({ hasText: '查询' }),
    ).toBeVisible()
    await expect(
      sharedPage.locator('.cl-query-actions button').filter({ hasText: '重置' }),
    ).toBeVisible()
    await sharedPage
      .locator('.cl-query-panel input[placeholder*="搜索合同编号"]')
      .fill(createdContractName!)
    await sharedPage.locator('.cl-query-actions button').filter({ hasText: '查询' }).click()

    const createdRow = sharedPage
      .locator('.vxe-body--row')
      .filter({ hasText: createdContractName! })
      .first()
    await expect(createdRow).toBeVisible({ timeout: 10000 })
    await expect(createdRow).toContainText(/草稿|DRAFT/)

    await sharedPage.screenshot({
      path: 'e2e/screenshots/contract-ledger-list.png',
      fullPage: true,
    })
  })

  test('created contract detail shows core tabs from ledger link', async () => {
    expect(createdContractName, '需要确认：前置草稿合同未创建成功').toBeTruthy()
    await waitForContractLedger(sharedPage)

    await sharedPage
      .locator('.cl-query-panel input[placeholder*="搜索合同编号"]')
      .fill(createdContractName!)
    await sharedPage.locator('.cl-query-actions button').filter({ hasText: '查询' }).click()

    const createdRow = sharedPage
      .locator('.vxe-body--row')
      .filter({ hasText: createdContractName! })
      .first()
    await expect(createdRow).toBeVisible({ timeout: 10000 })
    await createdRow.locator('button.cl-contract-link').click()
    await expect(sharedPage.locator('.contract-detail-page')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.locator('.contract-detail-page')).toContainText(createdContractName!)
    await expect(sharedPage.locator('.ant-tabs-tab').filter({ hasText: '合同清单' })).toBeVisible()
    await expect(sharedPage.locator('.ant-tabs-tab').filter({ hasText: '付款条件' })).toBeVisible()
    await expect(sharedPage.locator('.ant-tabs-tab').filter({ hasText: '审批记录' })).toBeVisible()

    await sharedPage.screenshot({ path: 'e2e/screenshots/contract-detail.png', fullPage: true })
  })

  test('contract search filter by contract type works', async () => {
    await waitForContractLedger(sharedPage)

    const typeSelect = sharedPage.locator('.cl-query-panel .cl-query-select').nth(1)
    if (await typeSelect.isVisible({ timeout: 3000 }).catch(() => false)) {
      await selectFirstOption(typeSelect)
      await sharedPage.locator('.cl-query-actions button').filter({ hasText: '查询' }).click()
      await expect(sharedPage.locator('.vxe-table').first()).toBeVisible({ timeout: 5000 })
      await sharedPage.locator('.cl-query-actions button').filter({ hasText: '重置' }).click()
    }

    await sharedPage.screenshot({ path: 'e2e/screenshots/contract-filter.png', fullPage: true })
  })

  test('contract list KPI cards are visible', async () => {
    await waitForContractLedger(sharedPage)

    await expect(sharedPage.getByText('合同总数')).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('合同总金额(含税)')).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('已付款')).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({ path: 'e2e/screenshots/contract-kpi.png', fullPage: true })
  })
})
