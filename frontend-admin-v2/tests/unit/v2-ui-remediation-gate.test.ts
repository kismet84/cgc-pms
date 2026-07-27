import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { relative, resolve } from 'node:path'

const sourceRoot = resolve('src')
const pageRoot = resolve(sourceRoot, 'pages')

function read(path: string): string {
  return readFileSync(resolve(path), 'utf-8')
}

function vuePages(root = pageRoot): string[] {
  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(root, entry.name)
    if (entry.isDirectory()) return vuePages(path)
    return entry.name.endsWith('.vue') ? [path] : []
  })
}

function pageName(path: string): string {
  return relative(pageRoot, path).replaceAll('\\', '/')
}

function templateOf(source: string): string {
  const start = source.indexOf('<template>')
  const end = source.lastIndexOf('</template>')
  return start >= 0 && end > start ? source.slice(start + '<template>'.length, end) : source
}

function buttonTags(source: string): string[] {
  return source.match(/<V2Button\b[^>]*>/g) ?? []
}

describe('全 V2 UI 整改门禁', () => {
  it('documents the latest all-V2 browser-comment contract', () => {
    const baseline = read(
      resolve('../docs/standards/00-UI-Design-Baselines-and-Code-Specifications.md'),
    )

    for (const marker of [
      '每页只有一个可见语义 `h1`',
      'H1与标题操作同置70px标题卡',
      'H1标题卡默认插槽必须为空',
      '桌面控件32px、移动控件44px',
      '汇总标签必须紧随对应 `h2`',
      '不得用副标题承载数量',
      '公共壳内容区保留10px内边距',
      '单一列表的数据区不得重复渲染“某某列表”等可见标题',
      '页面标题操作和表格行内操作统一使用 `size="small"`',
      '禁止卡片嵌套',
      '普通单据明细行保持透明',
      '弹窗表格必须取消页面级最小宽度继承',
      '禁止同时渲染 Toast 与页面级 `V2Alert`',
      '失败态与空态互斥',
    ]) {
      expect(baseline, marker).toContain(marker)
    }
  })

  it('enforces page chrome rules across every V2 page', () => {
    const violations: string[] = []

    for (const path of vuePages()) {
      const source = read(path)
      const template = templateOf(source)
      const name = pageName(path)
      const nativeH1Exceptions = new Set([
        'auth/LoginPage.vue',
        'auth/SessionPage.vue',
        'HealthPage.vue',
        'shell/ShellPlaceholderPage.vue',
      ])

      if (/<h1\b[^>]*\bv2-visually-hidden\b/.test(template)) {
        violations.push(`${name}: page H1 must remain visible`)
      }
      if (!nativeH1Exceptions.has(name) && /<h1\b/.test(template)) {
        violations.push(`${name}: business page H1 must use the shared H1 card`)
      }
      if (/>\s*[^<{]*不按报告期裁剪[^<{]*</.test(template)) {
        violations.push(`${name}: visible report-period implementation note`)
      }
      if (
        /<V2Card\b[^>]*\btitle="[^"]*列表[^"]*"[^>]*>/.test(template) ||
        /<V2Card\b[^>]*\b:title="[^"]*listTitle[^"]*"[^>]*>/i.test(template)
      ) {
        violations.push(`${name}: repeated visible list card title`)
      }
      const primaryTitles = [...template.matchAll(/<h1\b[^>]*>\s*([^<{]+?)\s*<\/h1>/g)].map(
        (match) => match[1].trim(),
      )
      const secondaryTitles = [
        ...[...template.matchAll(/<h[23]\b[^>]*>\s*([^<{]+?)\s*<\/h[23]>/g)].map((match) =>
          match[1].trim(),
        ),
        ...[
          ...template.matchAll(/<V2Card\b(?![^>]*:?heading-level="1")[^>]*\btitle="([^"]+)"/g),
        ].map((match) => match[1].trim()),
      ]
      if (
        secondaryTitles.some((secondary) =>
          primaryTitles.some((primary) => secondary !== primary && secondary.includes(primary)),
        )
      ) {
        violations.push(`${name}: visible data-region title repeats the page H1`)
      }

      for (const header of template.match(/<header\b[\s\S]*?<\/header>/g) ?? []) {
        if (!/<h1\b/.test(header)) continue
        for (const button of buttonTags(header)) {
          if (!/\bsize="small"/.test(button)) {
            violations.push(`${name}: H1 header action must use size="small"`)
          }
        }
      }

      for (const match of template.matchAll(
        /<V2Card\b(?=[^>]*:?heading-level="1")[^>]*>([\s\S]{0,1200}?)<template #actions>([\s\S]*?)<\/template>/g,
      )) {
        for (const button of buttonTags(match[2])) {
          if (!/\bsize="small"/.test(button)) {
            violations.push(`${name}: H1 card action must use size="small"`)
          }
        }
      }
      for (const match of template.matchAll(
        /<V2Card\b(?=[^>]*:?heading-level="1")[^>]*>([\s\S]{0,1600}?)<\/V2Card>/g,
      )) {
        if (/<template\s+#title-extra\b[^>]*>/.test(match[1])) {
          violations.push(`${name}: summary badges must follow H2, not H1`)
        }
        const body = match[1].replace(/<template\s+#actions\b[^>]*>[\s\S]*?<\/template\s*>/g, '')
        if (body.replace(/<!--[\s\S]*?-->/g, '').trim() && !body.includes('v2-ledger-kpis')) {
          violations.push(`${name}: H1 card body must stay empty`)
        }
      }
      for (const cardTag of template.match(/<V2Card\b[^>]*>/g) ?? []) {
        if (/:?heading-level="1"/.test(cardTag)) continue
        if (
          /:?subtitle="[^"]*(?:\.length|\btotal\b|\bcount\b|共\s*\d+|\d+\s*(?:条|项|个|份|笔|次|人|种))[^"]*"/i.test(
            cardTag,
          )
        ) {
          violations.push(`${name}: H2 count summary must use title-extra V2Badge`)
        }
      }
      for (const match of template.matchAll(
        /<V2Card\b(?![^>]*:?heading-level="1")[^>]*>([\s\S]{0,1600}?)<\/V2Card>/g,
      )) {
        const titleExtra = match[1].match(
          /<template\s+#title-extra\b[^>]*>([\s\S]*?)<\/template\s*>/,
        )?.[1]
        if (titleExtra && !/<V2Badge\b/.test(titleExtra)) {
          violations.push(`${name}: H2 title-extra must use summary badges`)
        }
      }
    }

    expect(violations).toEqual([])
  })

  it('keeps one shell main landmark and a primary heading on the session page', () => {
    const standaloneMainPages = new Set([
      'auth/LoginPage.vue',
      'auth/SessionPage.vue',
      'errors/GlobalErrorPage.vue',
      'HealthPage.vue',
    ])

    for (const path of vuePages()) {
      const name = pageName(path)
      if (standaloneMainPages.has(name)) continue
      expect(templateOf(read(path)), `${name} nested shell main`).not.toMatch(/<main\b/)
    }

    const session = read(resolve(pageRoot, 'auth/SessionPage.vue'))
    expect(templateOf(session)).toMatch(/<h1>安全会话已恢复<\/h1>/)
  })

  it('keeps the 10px shell content inset on workflow routes', () => {
    const shell = read(resolve(sourceRoot, 'layouts/AppShell.vue'))
    expect(shell).toMatch(
      /\.app-shell__content \{[\s\S]*?padding: 10px;[\s\S]*?\}\s*\.app-shell__content\.app-shell__content--full \{[\s\S]*?\}/,
    )
    expect(
      shell.match(/\.app-shell__content\.app-shell__content--full \{([\s\S]*?)\}/)?.[1],
    ).not.toMatch(/padding:\s*0/)
  })

  it('renders business labels instead of raw internal keys or status tokens', () => {
    const violations: string[] = []

    for (const path of vuePages()) {
      const source = read(path)
      const template = templateOf(source)
      const name = pageName(path)

      for (const binding of template.matchAll(/{{\s*([^{}]+?)\s*}}/g)) {
        const expression = binding[1].trim()
        if (/^(?:[$\w]+\.)+[$\w]*(?:status|state)$/i.test(expression)) {
          const root = expression.split('.')[0]
          const mappedBeforeRender = new RegExp(
            `status:\\s*\\w+StatusLabel\\(\\s*${root}\\.status\\s*\\)`,
          ).test(source)
          if (!mappedBeforeRender) {
            violations.push(`${name}: raw status binding ${expression}`)
          }
        }
        if (
          /^(?:[$\w]+\.)+[$\w]+Id$/.test(expression) ||
          /(?:\?\?|\|\|)\s*(?:[$\w]+\.)+[$\w]+Id\b/.test(expression)
        ) {
          violations.push(`${name}: raw internal id binding ${expression}`)
        }
      }
    }

    expect(violations).toEqual([])
  })

  it('enforces detail-dialog structure across every V2 page', () => {
    const violations: string[] = []
    const allowedPanelClasses = new Set([
      'v2-dialog-standard',
      'v2-detail-dialog',
      'v2-dialog-wide',
      'v2-dialog-bottom-sheet',
    ])

    for (const path of vuePages()) {
      const source = read(path)
      const template = templateOf(source)
      const name = pageName(path)
      const detailDialogs =
        template.match(/<V2Dialog\b(?=[^>]*v2-detail-dialog)[\s\S]*?<\/V2Dialog>/g) ?? []

      for (const dialog of detailDialogs) {
        if (/<V2Card\b/.test(dialog)) {
          violations.push(`${name}: v2-detail-dialog must not nest V2Card`)
        }

        const tableCount = (dialog.match(/<table\b/g) ?? []).length
        const namedFocusableRegions = (
          dialog.match(
            /<(?:div|section)\b(?=[^>]*\brole="region")(?=[^>]*\baria-label=)(?=[^>]*\btabindex="0")[^>]*>/g,
          ) ?? []
        ).length
        if (namedFocusableRegions < tableCount) {
          violations.push(`${name}: ${tableCount} detail tables require named focusable regions`)
        }
      }

      for (const attribute of template.matchAll(/(:?)panel-class\s*=\s*"([\s\S]*?)"/g)) {
        const values = attribute[1]
          ? [...attribute[2].matchAll(/'([^']+)'/g)].map((match) => match[1])
          : [attribute[2]]
        const panelClasses = values
          .flatMap((value) => value.split(/\s+/))
          .filter((value) => value.includes('-') || value.includes('__'))
        for (const panelClass of panelClasses) {
          if (!allowedPanelClasses.has(panelClass)) {
            violations.push(`${name}: private dialog panel class ${panelClass}`)
          }
        }
      }

      const style = source.slice(source.indexOf('<style'))
      if (/(?:\:deep|\:global)\([^)]*\.v2-dialog__|\.v2-dialog__panel\b/.test(style)) {
        violations.push(`${name}: page style must not override shared dialog internals`)
      }

      for (const rule of source.matchAll(/([^{]*(?:__items|__details)\s+li)\s*\{([\s\S]*?)\}/g)) {
        if (/\b(?:background|border-radius|box-shadow)\s*:/.test(rule[2])) {
          violations.push(`${name}: plain detail items must remain transparent`)
        }
      }
    }

    expect(violations).toEqual([])
  })

  it('keeps request failures out of empty and duplicate page-level states', () => {
    const violations: string[] = []

    for (const path of vuePages()) {
      const source = read(path)
      const template = templateOf(source)
      const name = pageName(path)
      if (name === 'auth/LoginPage.vue') continue

      if (
        /<V2Alert\b[^>]*\btitle="[^"]*(?:请求未完成|读取失败|加载失败)[^"]*"[^>]*>/.test(template)
      ) {
        violations.push(`${name}: transient read failure must use Toast only`)
      }
      if (!/\berrorMessage\b/.test(source)) continue
      for (const state of template.match(/<V2PageState\b(?:(?!\/>)[\s\S])*?\/>/g) ?? []) {
        if (!/\bkind="empty"|title="暂无/.test(state)) continue
        const condition = state.match(/v-(?:if|else-if)="([^"]*)"/)?.[1] ?? ''
        if (!condition.includes('errorMessage')) {
          violations.push(`${name}: empty state must exclude errorMessage`)
        }
      }
    }

    expect(violations).toEqual([])
  })

  it('requires every dashboard role to send the selected report month', () => {
    const service = read(resolve(sourceRoot, 'services/dashboard.ts'))
    const controller = read(
      resolve('../backend/src/main/java/com/cgcpms/dashboard/controller/DashboardController.java'),
    )

    expect(service).not.toContain('supportsMonth')
    expect(service).toMatch(/normalizeDashboardMonth\(query\.period\)/)
    for (const method of ['getBusinessManagerView', 'getFinanceView', 'getManagementView']) {
      expect(controller, `${method} month parameter`).toMatch(
        new RegExp(`${method}\\([\\s\\S]{0,180}String month`),
      )
    }
  })

  it('keeps dense table actions compact and semantic', () => {
    for (const path of vuePages()) {
      if (path === resolve(pageRoot, 'auth/LoginPage.vue')) continue
      const source = read(path)
      expect(source, `${path} touch-sized page action`).not.toContain('size="touch"')
    }

    const components = read(resolve(sourceRoot, 'styles/components.css'))
    expect(components).toContain('.v2-table-cell--numeric')
    expect(components).toContain('.v2-table-cell--status')
    expect(components).toContain('.v2-table-cell--actions')
  })

  it('keeps dialog action bars liquid and shared', () => {
    const components = read(resolve(sourceRoot, 'styles/components.css'))
    const schedule = read(resolve(sourceRoot, 'pages/delivery/SchedulePage.vue'))
    const footer = components.match(/\.v2-dialog__footer\s*\{([\s\S]*?)\}/)?.[1] ?? ''
    expect(footer).toMatch(
      /background:\s*color-mix\(in srgb, var\(--v2-color-surface\) 24%, transparent\)/,
    )
    expect(footer).not.toMatch(/background:\s*var\(--v2-dialog-surface\)/)
    expect(schedule).toContain('class="v2-table-cell--actions"')
    expect(schedule).toContain(
      '<V2Button type="button" variant="secondary" @click="createOpen = false">取消</V2Button>',
    )
  })

  it('blocks the eleven supply-chain regressions', () => {
    const supplier = read(resolve(sourceRoot, 'pages/supply-chain/SupplierSourcingPage.vue'))
    const purchase = read(resolve(sourceRoot, 'pages/supply-chain/PurchaseExecutionPage.vue'))
    const inventory = read(resolve(sourceRoot, 'pages/supply-chain/InventoryWorkspacePage.vue'))
    const contract = read(resolve('../packages/frontend-contracts/src/supply-chain.ts'))
    const requestVo = read(
      resolve('../backend/src/main/java/com/cgcpms/purchase/vo/MatPurchaseRequestVO.java'),
    )

    expect(supplier).toContain('v-model="form.projectId"')
    expect(supplier).not.toMatch(/新建招采事件[\s\S]{0,120}:disabled="!projectId"/)
    expect(supplier).not.toContain(':disabled="!projectId"')
    expect(purchase).not.toMatch(/<V2Button[^>]*:disabled="!projectId"[^>]*@click="openCreate"/)
    expect(supplier).toMatch(
      /class="v2-table__record-link"[\s\S]{0,160}@click="selectEvent\(item\.id\)"/,
    )
    expect(purchase).not.toMatch(/<p>供应链与物资(?: · M5)?<\/p>/)
    expect(inventory).not.toMatch(/<p[^>]*>供应链与物资(?: · M5)?<\/p>/)
    for (const path of vuePages(resolve(pageRoot, 'supply-chain'))) {
      expect(read(path), `${path} duplicated shell label`).not.toMatch(
        /<p[^>]*>供应链与物资(?: · M5)?<\/p>/,
      )
    }
    expect(purchase).toContain("PARTIALLY_QUALIFIED: '部分合格'")
    expect(purchase).toContain("businessCode(record.orderCode, '采购订单')")
    expect(purchase).toContain("record.requestCode || record.partnerName || '例外采购'")
    expect(purchase).not.toContain('record.requestId ||')
    expect(contract).toMatch(/interface PurchaseRequestRecord[\s\S]*?totalAmount\?:/)
    expect(requestVo).toContain('private String totalAmount;')
    expect(inventory).not.toContain('仓库主数据不按报告期裁剪')
    expect(inventory).not.toContain('title="仓库列表"')
    expect(inventory).not.toContain('<V2Card class="inventory-workspace-page__filters">')
    const kpis = inventory.match(/<dl[^>]*class="v2-ledger-kpis"[\s\S]*?<\/dl>/)?.[0]
    expect(kpis).toBeTruthy()
    expect(kpis).not.toContain('<V2Card')
  })

  it('keeps supply-chain workspace titles in H1 cards and warehouse filters optional', () => {
    for (const name of [
      'PurchaseExecutionPage.vue',
      'InventoryWorkspacePage.vue',
      'RequisitionWorkspacePage.vue',
    ]) {
      const source = read(resolve(pageRoot, 'supply-chain', name))
      expect(source, `${name} H1 card`).toMatch(/<V2Card\b(?=[^>]*:heading-level="1")[^>]*>/)
      expect(source, `${name} raw page header`).not.toMatch(/<header\b[^>]*>[\s\S]{0,500}<h1\b/)
    }

    const inventory = read(resolve(pageRoot, 'supply-chain/InventoryWorkspacePage.vue'))
    expect(inventory).toContain("{ value: '', label: '全部仓库' }")
    expect(inventory).toMatch(/v-model="filter\.warehouseId"[\s\S]{0,240}\ballow-empty\b/)
    expect(inventory).not.toContain('请选择仓库和物料')
  })

  it('keeps migrated H1 title bars title-only', () => {
    for (const path of vuePages()) {
      if (
        path === resolve(pageRoot, 'auth/LoginPage.vue') ||
        path === resolve(pageRoot, 'HealthPage.vue') ||
        path === resolve(pageRoot, 'shell/ShellPlaceholderPage.vue')
      ) {
        continue
      }
      const source = read(path)
      expect(source, `${path} H1 card subtitle`).not.toMatch(
        /<V2Card\b(?=[^>]*:heading-level="1")(?=[^>]*:?subtitle=)[^>]*>/,
      )
      expect(source, `${path} custom H1 header copy`).not.toMatch(
        /<header\b[^>]*>[\s\S]*?<h1\b[^>]*>[\s\S]*?<\/h1>\s*<(?:p|span)\b[\s\S]*?<\/header>/,
      )
    }
  })

  it('opens record details from the table identifier instead of an action button', () => {
    for (const path of vuePages()) {
      const source = read(path)
      for (const table of source.match(/<table\b[\s\S]*?<\/table>/g) ?? []) {
        expect(table, `${path} legacy detail action button`).not.toMatch(
          /<V2Button\b(?:(?!<V2Button\b)[\s\S])*?(?:查看|详情|追溯|预览|总览)(?:(?!<V2Button\b)[\s\S])*?<\/V2Button>/,
        )
      }
    }
  })

  it('requires operational tables to expose business identities instead of database ids', () => {
    const identifierHeader = /编号|编码|单号|标识|版本|日期/
    for (const path of vuePages()) {
      const source = read(path)
      for (const table of source.match(/<table\b[\s\S]*?<\/table>/g) ?? []) {
        const head = table.match(/<thead\b[\s\S]*?<\/thead>/)?.[0] ?? ''
        const headers = [...head.matchAll(/<th\b[^>]*>([\s\S]*?)<\/th>/g)].map((match) =>
          match[1]
            .replace(/<[^>]+>/g, ' ')
            .replace(/\s+/g, ' ')
            .trim(),
        )

        expect(headers, `${path} internal id header`).not.toEqual(
          expect.arrayContaining([expect.stringMatching(/ID/i)]),
        )
        expect(table, `${path} raw internal id cell`).not.toMatch(
          /<(?:td|th)\b[^>]*>\s*\{\{\s*(?:item|row|record)\.(?:partnerId|quoteId|sourceId)\b[^}]*\}\}\s*<\/(?:td|th)>/,
        )
        if (headers.includes('操作') && !table.includes('data-table-identity="contextual"')) {
          expect(
            headers.some((header) => identifierHeader.test(header)),
            `${path} action table identity`,
          ).toBe(true)
        }
        if (table.includes('v2-table__record-link')) {
          const bodyRow = table.match(/<tbody\b[\s\S]*?<tr\b[\s\S]*?<\/tr>/)?.[0] ?? ''
          const cells = bodyRow.match(/<t[dh]\b[\s\S]*?<\/t[dh]>/g) ?? []
          const linkIndex = cells.findIndex((cell) => cell.includes('v2-table__record-link'))
          expect(
            linkIndex >= 0 && identifierHeader.test(headers[linkIndex] ?? ''),
            `${path} record-link identity`,
          ).toBe(true)
        }
      }
    }
  })

  it('routes every visible refresh control through a toast-producing refresh handler', () => {
    for (const path of vuePages()) {
      const source = read(path)
      const buttons = source.match(/<V2Button\b(?:(?!<V2Button\b)[\s\S])*?<\/V2Button>/g) ?? []
      for (const button of buttons.filter((value) => />\s*刷新[^<]*<\/V2Button>/.test(value))) {
        const handler = button.match(/@click="([A-Za-z][A-Za-z0-9]*)"/)?.[1]
        expect(handler, `${path} refresh handler`).toMatch(/^refresh/)
        expect(source, `${path} ${handler} toast`).toMatch(
          new RegExp(`function\\s+${handler}[\\s\\S]{0,1200}showToast\\(`),
        )
      }
    }
  })

  it('uses toast for supply-chain detail read failures', () => {
    const main = read(resolve(sourceRoot, 'main.ts'))
    const service = read(resolve(sourceRoot, 'services/supply-chain.ts'))
    expect(main).toMatch(/onError:\s*\(notice\)\s*=>\s*showToast\('error'/)
    expect(main).not.toMatch(/onError:[^\n]*setRequestNotice/)

    for (const name of ['PurchaseExecutionPage.vue', 'RequisitionWorkspacePage.vue']) {
      const source = read(resolve(pageRoot, 'supply-chain', name))
      expect(source, `${name} inline detail failure`).not.toMatch(
        /<(?:V2Alert|V2PageState)\b[^>]*v-(?:if|else-if)="detailError"/,
      )
      expect(source, `${name} detail failure toast`).toMatch(/showToast\('error'/)
    }

    for (const name of [
      'loadRequisition',
      'loadRequisitionItems',
      'loadRequisitionTrace',
      'loadMaterialReturn',
      'loadMaterialReturnItems',
      'loadPurchaseRequest',
      'loadPurchaseRequestItems',
      'loadPurchaseOrder',
      'loadPurchaseOrderItems',
      'loadReceipt',
      'loadReceiptItems',
    ]) {
      expect(service, `${name} duplicate global failure`).toMatch(
        new RegExp(
          `export function ${name}\\b[\\s\\S]{0,320}?notifyError:\\s*false[\\s\\S]{0,80}?\\n}`,
        ),
      )
    }
  })

  it('keeps current browser-comment remediations behind static gates', () => {
    const supplier = read(resolve(sourceRoot, 'pages/supply-chain/SupplierSourcingPage.vue'))
    const purchase = read(resolve(sourceRoot, 'pages/supply-chain/PurchaseExecutionPage.vue'))
    const budget = read(resolve(sourceRoot, 'pages/commercial/BudgetPage.vue'))
    const contract = read(resolve(sourceRoot, 'pages/commercial/ContractPage.vue'))

    expect(purchase).toContain('class="v2-detail-dialog__section"')
    expect(purchase).toContain('class="v2-detail-dialog__section-heading"')
    expect(purchase).toContain('class="v2-detail-dialog__table"')
    expect(purchase).toContain(':aria-label="`${title}明细表格`"')
    expect(purchase).toContain(
      "['物料', '规格', '单位', '数量', '预计单价', '预计金额', '计划日期']",
    )
    expect(purchase).toContain("['物料', '规格', '单位', '数量', '单价', '金额', '已收数量']")
    for (const heading of [
      '实收数量',
      '合格数量',
      '不合格数量',
      '订单数量',
      '累计收货',
      '剩余数量',
      '使用部位',
    ]) {
      expect(purchase).toContain(`'${heading}'`)
    }
    expect(purchase).not.toContain('purchase-execution-page__items')
    expect(purchase).not.toContain('function itemQuantity')
    const detailTableSource = purchase.slice(
      purchase.indexOf('const detailTable'),
      purchase.indexOf('async function loadPage'),
    )
    expect(detailTableSource).not.toMatch(/\b(?:Number|parseFloat|parseInt)\s*\(/)

    expect(budget).toMatch(
      /<V2Button\b(?=[^>]*v-if="canAdd")(?=[^>]*size="small")[^>]*>\s*新建预算/,
    )
    expect(budget).toContain('class="table-wrap budget-page__availability"')
    expect(budget).toContain('class="v2-table--compact"')
    expect(budget).toMatch(/\.budget-page__availability table\s*\{[^}]*min-width:\s*0/)

    expect(contract).not.toMatch(/<V2Alert\b[^>]*v-if="errorMessage"/)
    expect(contract).toContain('v-if="!contracts.length && !errorMessage"')

    expect(supplier).toContain('class="supplier-page__trace"')
    expect(supplier).toContain('class="v2-detail-dialog__quick-actions"')
    expect(supplier).not.toMatch(
      /<V2Dialog[\s\S]*?title="招采闭环追溯"[\s\S]*?<V2Card v-else-if="selected && trace"/,
    )
  })
})
