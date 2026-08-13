import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')
const templateOf = (value: string) =>
  value.slice(value.indexOf('<template>') + '<template>'.length, value.lastIndexOf('</template>'))
const hash = (value: unknown) => createHash('sha256').update(JSON.stringify(value)).digest('hex')

function templateContract(paths: string[], prefix: string): string {
  const template = paths.map((path) => templateOf(source(path))).join('\n')
  const classes = [...template.matchAll(new RegExp(`${prefix}(?:--|__)[\\w-]+`, 'g'))]
    .map((match) => match[0])
    .sort()
  const aria = [...template.matchAll(/:?aria-[\w-]+(?:\s*=\s*"[^"]*")?/gs)]
    .map((match) => match[0].replaceAll(/\s+/g, ' ').trim())
    .sort()
  const allowedEvents = new Set([
    'change',
    'click',
    'dismiss',
    'dragover',
    'dragstart',
    'drop',
    'focus',
    'input',
    'keydown',
    'pointercancel',
    'pointerdown',
    'pointermove',
    'pointerup',
    'update:model-value',
  ])
  const events = [...template.matchAll(/@([\w:-]+)((?:\.[\w-]+)*)/g)]
    .flatMap((match) => (allowedEvents.has(match[1]!) ? [`${match[1]}${match[2]}`] : []))
    .sort()
  const tags = [
    ...template.matchAll(
      /<(aside|header|main|nav|section|button|a|input|select|iframe|table|thead|tbody|tr|th|td)\b/g,
    ),
  ]
    .map((match) => match[1])
    .sort()
  return hash({ classes, aria, events, tags })
}

describe('M94 shell and document canvas boundaries', () => {
  it('freezes shell DOM, class, ARIA and interaction tokens across focused components', () => {
    expect(
      templateContract(
        [
          'src/layouts/AppShell.vue',
          'src/layouts/ShellSidebar.vue',
          'src/layouts/ShellHeaderWorkspace.vue',
        ],
        'app-shell',
      ),
    ).toBe('831b34a41c9c1e84131ae0bae7f5461fee5f2f6a06eb7b117c2d03d52fba5ee7')
  })

  it('freezes canvas DOM, class, ARIA and interaction tokens across focused components', () => {
    expect(
      templateContract(
        [
          'src/components/document/DocumentCanvas.vue',
          'src/components/document/DocumentFieldLibrary.vue',
          'src/components/document/DocumentPropertiesPanel.vue',
        ],
        'document-canvas',
      ),
    ).toBe('17d75d418ba1aa96b1d5c643d06315d36196c8a888eb945da1797dbbd9390dd9')
  })

  it('keeps shell coordination in the façade and only visual regions in two children', () => {
    const shell = source('src/layouts/AppShell.vue')
    expect(existsSync(resolve('src/layouts/ShellSidebar.vue'))).toBe(true)
    expect(existsSync(resolve('src/layouts/ShellHeaderWorkspace.vue'))).toBe(true)
    expect(shell).toContain('<ShellSidebar')
    expect(shell).toContain('<ShellHeaderWorkspace')
    expect(shell).toContain('workspaceStore.syncRoute')
    expect(shell).toContain('router.afterEach')
    expect(shell).not.toContain('<aside')
    expect(shell).not.toContain('<header class="app-shell__header"')
  })

  it('keeps pointer canvas and schema commit in the façade with two visual side panels', () => {
    const canvas = source('src/components/document/DocumentCanvas.vue')
    expect(existsSync(resolve('src/components/document/DocumentFieldLibrary.vue'))).toBe(true)
    expect(existsSync(resolve('src/components/document/DocumentPropertiesPanel.vue'))).toBe(true)
    expect(canvas).toContain('<DocumentFieldLibrary')
    expect(canvas).toContain('<DocumentPropertiesPanel')
    expect(canvas).toContain('function commit(')
    expect(canvas).toContain('function startInteraction(')
    expect(canvas).toContain('function startBoxSelection(')
    expect(canvas).not.toContain('aria-label="字段目录"')
    expect(canvas).not.toContain('aria-label="元素属性"')
  })

  it('keeps the DocumentCanvas public props and emit order contract unchanged', () => {
    const canvas = source('src/components/document/DocumentCanvas.vue')
    const protocol = canvas
      .slice(canvas.indexOf('const props = withDefaults('), canvas.indexOf('\n\nconst selectedId'))
      .replaceAll(/\s+/g, ' ')
      .trim()

    expect(createHash('sha256').update(protocol).digest('hex')).toBe(
      '06f5e58ba1d83da7de9491e6e03150e40c902f8d3800761c318e8af97b8956d1',
    )
    expect(canvas.indexOf("emit('update:modelValue', value)")).toBeLessThan(
      canvas.indexOf("emit('update:valid', validDocumentDesignSchema(value))"),
    )
  })

  it('loads prefixed shared CSS without leaving visual responsibility in the façades', () => {
    const shell = source('src/layouts/AppShell.vue')
    const canvas = source('src/components/document/DocumentCanvas.vue')
    expect(shell).toContain('<style src="./app-shell.css"></style>')
    expect(canvas).toContain('<style src="./document-canvas.css"></style>')
    expect(shell.split('\n').length).toBeLessThan(450)
    expect(canvas.split('\n').length).toBeLessThan(820)
  })
})
