import { describe, expect, it } from 'vitest'
import { antdTheme, chartPalette, designTokens, rootCssVariables } from '../tokens'

describe('design tokens', () => {
  it('uses one token source for Ant Design and CSS variables', () => {
    expect(designTokens.color.primary).toBe('#2563eb')
    expect(antdTheme.token.colorPrimary).toBe(designTokens.color.primary)
    expect(antdTheme.components?.Button?.colorPrimary).toBe(designTokens.color.primary)
    expect(rootCssVariables['--primary']).toBe(designTokens.color.primary)
  })

  it('keeps control height and radius consistent across semantic tokens', () => {
    expect(antdTheme.token.controlHeight).toBe(designTokens.control.height)
    expect(antdTheme.components?.Input?.controlHeight).toBe(designTokens.control.height)
    expect(antdTheme.components?.Select?.controlHeight).toBe(designTokens.control.height)
    expect(rootCssVariables['--radius-sm']).toBe(`${designTokens.radius.sm}px`)
    expect(rootCssVariables['--radius-md']).toBe(`${designTokens.radius.md}px`)
    expect(rootCssVariables['--radius-full']).toBe(`${designTokens.radius.full}px`)
  })

  it('provides shared shell visual tokens for app layout chrome', () => {
    expect(designTokens.shell.bg).toEqual(expect.any(String))
    expect(designTokens.shell.sidebarBg).toEqual(expect.any(String))
    expect(designTokens.shell.topbarBg).toEqual(expect.any(String))
    expect(designTokens.shell.maskBg).toEqual(expect.any(String))
    expect(designTokens.brand.logoForeground).toEqual(expect.any(String))
    expect(rootCssVariables['--shell-bg']).toBe(designTokens.shell.bg)
    expect(rootCssVariables['--shell-sidebar-bg']).toBe(designTokens.shell.sidebarBg)
    expect(rootCssVariables['--shell-topbar-bg']).toBe(designTokens.shell.topbarBg)
    expect(rootCssVariables['--shell-mask-bg']).toBe(designTokens.shell.maskBg)
    expect(rootCssVariables['--brand-logo-fg']).toBe(designTokens.brand.logoForeground)
  })

  it('provides a shared chart palette instead of page-level chart colors', () => {
    expect(chartPalette.categorical).toEqual([
      designTokens.color.primary,
      designTokens.color.info,
      designTokens.color.warning,
      designTokens.color.success,
      designTokens.color.error,
    ])
    expect(chartPalette.semantic.positive).toBe(designTokens.color.success)
    expect(chartPalette.semantic.negative).toBe(designTokens.color.error)
    expect(chartPalette.shadow.emphasis).toBe('rgba(21, 32, 51, 0.15)')
  })
})
