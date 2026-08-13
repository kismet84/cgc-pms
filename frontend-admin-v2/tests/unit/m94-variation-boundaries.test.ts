import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

describe('M94 variation route boundaries', () => {
  it('keeps the legacy public import as a lightweight compatibility shell', () => {
    const shell = source('src/pages/commercial/VariationPage.vue')

    expect(shell).toContain(
      "import VariationWorkspacePage from './variation/VariationWorkspacePage.vue'",
    )
    expect(shell).toContain('<VariationWorkspacePage />')
    expect(shell).not.toMatch(/@\/services|useRoute|useRouter|loadVariation|createVariation/)
    expect(shell.split(/\r?\n/)).toHaveLength(8)
  })

  it('dispatches the existing query modes to three focused owners', () => {
    const workspace = source('src/pages/commercial/variation/VariationWorkspacePage.vue')
    const ledger = source('src/pages/commercial/variation/VariationLedgerPage.vue')
    const editor = source('src/pages/commercial/variation/VariationEditorPage.vue')
    const detail = source('src/pages/commercial/variation/VariationDetailPage.vue')

    expect(workspace).toContain('VariationLedgerPage')
    expect(workspace).toContain('VariationEditorPage')
    expect(workspace).toContain('VariationDetailPage')
    expect(workspace).toContain("type WorkspaceMode = 'list' | 'create' | 'detail' | 'edit'")
    expect(workspace).toContain(
      "requested === 'create' || requested === 'detail' || requested === 'edit'",
    )

    expect(ledger).toContain('loadVariationPage')
    expect(ledger).not.toMatch(/createVariation|updateVariation|saveVariationItems|submitVariation/)
    expect(editor).toContain('createVariation')
    expect(editor).toContain('updateVariation')
    expect(editor).not.toMatch(/loadVariationPage|submitVariationToOwner|reviewVariationOwner/)
    expect(detail).toContain('saveVariationItems')
    expect(detail).toContain('submitVariationToOwner')
    expect(detail).toContain('reviewVariationOwner')
    expect(detail).not.toMatch(/loadVariationPage|createVariation|updateVariation/)
  })

  it('keeps permission, version, evidence and historical-option contracts explicit', () => {
    const focused = [
      'src/pages/commercial/variation/VariationLedgerPage.vue',
      'src/pages/commercial/variation/VariationEditorPage.vue',
      'src/pages/commercial/variation/VariationDetailPage.vue',
    ]
      .map(source)
      .join('\n')

    for (const permission of [
      'variation:order:add',
      'variation:order:edit',
      'variation:order:item:edit',
      'variation:order:delete',
      'variation:order:submit',
      'variation:owner:submit',
      'variation:owner:review',
      'variation:trace',
    ])
      expect(focused).toContain(permission)

    expect(focused).toContain('versionOf')
    expect(focused).toContain("'SITE_EVIDENCE'")
    expect(focused).toContain("'OWNER_SUBMISSION'")
    expect(focused).toContain("'OWNER_CONFIRMATION'")
    expect(focused).toContain('（历史值）')
    expect(focused).toContain('disabled: true')
    expect(focused).not.toMatch(/parseFloat|parseInt|Number\([^)]*(?:amount|quantity|unitPrice)/i)
  })

  it('maps the accepted route directly and keeps one supplier route record', () => {
    const components = source('src/router/components.ts')
    const contextRoutes = source('src/router/context-routes.ts')

    expect(components).toContain(
      "export const VariationWorkspacePage = () =>\n  import('../pages/commercial/variation/VariationWorkspacePage.vue')",
    )
    expect(components).toContain("'/variation/order': VariationWorkspacePage")
    expect(components).not.toContain("'/supplier-sourcing': SupplierSourcingPage")
    expect(contextRoutes.match(/path: '\/supplier-sourcing'/g)).toHaveLength(1)
    expect(contextRoutes).toContain("name: 'LegacySupplierSourcing'")
    expect(contextRoutes).toContain("permission: 'supplier:sourcing:query'")
  })
})
