import type {
  DocumentCanvasElement,
  DocumentCanvasTable,
  DocumentDesignSchema,
  DocumentPageOrientation,
} from '@/services/system-management'

export type CanvasItem = DocumentCanvasElement | DocumentCanvasTable
export type ComponentAlignment = 'TOP' | 'MIDDLE' | 'BOTTOM' | 'LEFT' | 'CENTER' | 'RIGHT'
export type AlignmentReference = 'SELECTION' | 'CANVAS' | 'KEY'
export type LayoutAction =
  | ComponentAlignment
  | 'DISTRIBUTE_HORIZONTAL'
  | 'DISTRIBUTE_VERTICAL'
  | 'SPACE_HORIZONTAL'
  | 'SPACE_VERTICAL'
  | 'ATTACH_HORIZONTAL'
  | 'ATTACH_VERTICAL'
  | 'EQUAL_WIDTH'
  | 'EQUAL_HEIGHT'
  | 'EQUAL_SIZE'
  | 'ARRANGE_HORIZONTAL'
  | 'ARRANGE_VERTICAL'
  | 'ARRANGE_GRID'
  | 'ROUND_MM'

export interface ItemRect {
  xMm: number
  yMm: number
  widthMm: number
  heightMm: number
}

export interface GuideLines {
  xMm?: number
  yMm?: number
}

export function roundMm(value: number): number {
  return Math.round(value * 10) / 10
}

export function pageSizeFor(orientation: DocumentPageOrientation) {
  return orientation === 'PORTRAIT' ? { width: 210, height: 297 } : { width: 297, height: 210 }
}

export function flowLayoutConflict(value: DocumentDesignSchema): string {
  const tables = [...value.tables].sort((a, b) => a.yMm - b.yMm)
  for (let index = 1; index < tables.length; index += 1) {
    const previous = tables[index - 1]!
    const current = tables[index]!
    if (current.yMm < previous.yMm + previous.heightMm) {
      return `明细表 ${current.id} 与 ${previous.id} 的设计占位重叠`
    }
  }
  for (const table of tables) {
    const conflict = value.elements.find(
      (element) =>
        (element.repeat ?? 'BODY') === 'BODY' &&
        table.xMm < element.xMm + element.widthMm &&
        table.xMm + table.widthMm > element.xMm &&
        element.yMm + element.heightMm > table.yMm,
    )
    if (conflict) return `流式明细表 ${table.id} 可能与正文元素 ${conflict.id} 重叠`
  }
  return ''
}

export function validDocumentDesignSchema(value: DocumentDesignSchema): boolean {
  const margin = value.page.marginMm
  const size = pageSizeFor(value.page.orientation)
  const withinPage = (item: CanvasItem) =>
    item.xMm >= margin.left &&
    item.yMm >= margin.top &&
    item.xMm + item.widthMm <= size.width - margin.right &&
    item.yMm + item.heightMm <= size.height - margin.bottom
  const count = value.elements.length + value.tables.length
  return (
    count > 0 &&
    count <= 200 &&
    value.elements.every(withinPage) &&
    value.tables.every(withinPage) &&
    !flowLayoutConflict(value)
  )
}

export function overflowItemIds(value: DocumentDesignSchema): string[] {
  const margin = value.page.marginMm
  const size = pageSizeFor(value.page.orientation)
  return [...value.elements, ...value.tables]
    .filter(
      (item) =>
        item.xMm < margin.left ||
        item.yMm < margin.top ||
        item.xMm + item.widthMm > size.width - margin.right ||
        item.yMm + item.heightMm > size.height - margin.bottom,
    )
    .map((item) => item.id)
}

export function itemBounds(items: readonly CanvasItem[]): ItemRect {
  const left = Math.min(...items.map((item) => item.xMm))
  const top = Math.min(...items.map((item) => item.yMm))
  const right = Math.max(...items.map((item) => item.xMm + item.widthMm))
  const bottom = Math.max(...items.map((item) => item.yMm + item.heightMm))
  return { xMm: left, yMm: top, widthMm: right - left, heightMm: bottom - top }
}

export function safeCanvasBounds(value: DocumentDesignSchema): ItemRect {
  const margin = value.page.marginMm
  const size = pageSizeFor(value.page.orientation)
  return {
    xMm: margin.left,
    yMm: margin.top,
    widthMm: size.width - margin.left - margin.right,
    heightMm: size.height - margin.top - margin.bottom,
  }
}

export function scaleTableColumns(table: DocumentCanvasTable): DocumentCanvasTable {
  if (!table.columns.length) return table
  const total = table.columns.reduce((sum, column) => sum + column.widthMm, 0) || 1
  const columns = table.columns.map((column) => ({
    ...column,
    widthMm: roundMm((column.widthMm / total) * table.widthMm),
  }))
  columns[columns.length - 1]!.widthMm = roundMm(
    table.widthMm - columns.slice(0, -1).reduce((sum, column) => sum + column.widthMm, 0),
  )
  return { ...table, columns }
}

export function canApplyCanvasLayout(
  action: LayoutAction,
  selectedItems: readonly CanvasItem[],
  tables: readonly DocumentCanvasTable[],
  alignmentReference: AlignmentReference,
): boolean {
  const count = selectedItems.length
  if (
    selectedItems.some((item) => tables.some((table) => table.id === item.id)) &&
    ![
      'LEFT',
      'CENTER',
      'RIGHT',
      'DISTRIBUTE_HORIZONTAL',
      'SPACE_HORIZONTAL',
      'ATTACH_HORIZONTAL',
      'EQUAL_WIDTH',
    ].includes(action)
  )
    return false
  if (action === 'ROUND_MM') return count > 0
  if (action.startsWith('DISTRIBUTE_')) return count >= 3
  if (
    ['TOP', 'MIDDLE', 'BOTTOM', 'LEFT', 'CENTER', 'RIGHT'].includes(action) &&
    alignmentReference === 'CANVAS'
  )
    return count > 0
  return count >= 2
}

function alignItems(
  items: readonly CanvasItem[],
  alignment: ComponentAlignment,
  alignmentReference: AlignmentReference,
  canvasBounds: ItemRect,
  primaryId: string,
): CanvasItem[] {
  const primary = items.find((item) => item.id === primaryId) ?? items[0]
  const reference =
    alignmentReference === 'CANVAS'
      ? canvasBounds
      : alignmentReference === 'KEY' && primary
        ? itemBounds([primary])
        : itemBounds(items)
  const right = reference.xMm + reference.widthMm
  const bottom = reference.yMm + reference.heightMm
  return items.map((item) => ({
    ...item,
    xMm:
      alignment === 'LEFT'
        ? reference.xMm
        : alignment === 'CENTER'
          ? roundMm(reference.xMm + (reference.widthMm - item.widthMm) / 2)
          : alignment === 'RIGHT'
            ? roundMm(right - item.widthMm)
            : item.xMm,
    yMm:
      alignment === 'TOP'
        ? reference.yMm
        : alignment === 'MIDDLE'
          ? roundMm(reference.yMm + (reference.heightMm - item.heightMm) / 2)
          : alignment === 'BOTTOM'
            ? roundMm(bottom - item.heightMm)
            : item.yMm,
  }))
}

function sequenceItems(
  items: readonly CanvasItem[],
  axis: 'x' | 'y',
  gap: number,
  align: boolean,
): CanvasItem[] {
  const key = axis === 'x' ? 'xMm' : 'yMm'
  const size = axis === 'x' ? 'widthMm' : 'heightMm'
  const ordered = [...items].sort((a, b) => a[key] - b[key])
  const bounds = itemBounds(ordered)
  let cursor = axis === 'x' ? bounds.xMm : bounds.yMm
  return ordered.map((item) => {
    const next = {
      ...item,
      [key]: roundMm(cursor),
      ...(align ? (axis === 'x' ? { yMm: bounds.yMm } : { xMm: bounds.xMm }) : {}),
    }
    cursor += item[size] + gap
    return next
  })
}

function distributeItems(items: readonly CanvasItem[], axis: 'x' | 'y'): CanvasItem[] {
  const key = axis === 'x' ? 'xMm' : 'yMm'
  const size = axis === 'x' ? 'widthMm' : 'heightMm'
  const ordered = [...items].sort((a, b) => a[key] - b[key])
  const first = ordered[0]!
  const last = ordered.at(-1)!
  const span = last[key] + last[size] - first[key]
  const total = ordered.reduce((sum, item) => sum + item[size], 0)
  const gap = (span - total) / (ordered.length - 1)
  let cursor = first[key]
  return ordered.map((item) => {
    const next = { ...item, [key]: roundMm(cursor) }
    cursor += item[size] + gap
    return next
  })
}

export function applyCanvasLayout(options: {
  action: LayoutAction
  items: readonly CanvasItem[]
  primaryId: string
  alignmentReference: AlignmentReference
  canvasBounds: ItemRect
  gap: number
}): CanvasItem[] {
  const { action, items, primaryId, alignmentReference, canvasBounds, gap } = options
  if (['TOP', 'MIDDLE', 'BOTTOM', 'LEFT', 'CENTER', 'RIGHT'].includes(action)) {
    return alignItems(
      items,
      action as ComponentAlignment,
      alignmentReference,
      canvasBounds,
      primaryId,
    )
  }
  if (action === 'DISTRIBUTE_HORIZONTAL' || action === 'DISTRIBUTE_VERTICAL') {
    return distributeItems(items, action.endsWith('HORIZONTAL') ? 'x' : 'y')
  }
  if (action === 'SPACE_HORIZONTAL' || action === 'SPACE_VERTICAL') {
    return sequenceItems(items, action.endsWith('HORIZONTAL') ? 'x' : 'y', gap, false)
  }
  if (action === 'ATTACH_HORIZONTAL' || action === 'ATTACH_VERTICAL') {
    return sequenceItems(items, action.endsWith('HORIZONTAL') ? 'x' : 'y', 0, false)
  }
  if (action === 'ARRANGE_HORIZONTAL' || action === 'ARRANGE_VERTICAL') {
    return sequenceItems(items, action.endsWith('HORIZONTAL') ? 'x' : 'y', gap, true)
  }
  if (action === 'ARRANGE_GRID') {
    const bounds = itemBounds(items)
    const columns = Math.ceil(Math.sqrt(items.length))
    const width = Math.max(...items.map((item) => item.widthMm))
    const height = Math.max(...items.map((item) => item.heightMm))
    return items.map((item, index) => ({
      ...item,
      xMm: roundMm(bounds.xMm + (index % columns) * (width + gap)),
      yMm: roundMm(bounds.yMm + Math.floor(index / columns) * (height + gap)),
    }))
  }
  if (action === 'ROUND_MM') {
    return items.map((item) => ({
      ...item,
      xMm: Math.round(item.xMm),
      yMm: Math.round(item.yMm),
      widthMm: Math.max(12, Math.round(item.widthMm)),
      heightMm: Math.max(8, Math.round(item.heightMm)),
    }))
  }
  const primary = items.find((item) => item.id === primaryId) ?? items[0]!
  return items.map((item) => ({
    ...item,
    widthMm: action === 'EQUAL_WIDTH' || action === 'EQUAL_SIZE' ? primary.widthMm : item.widthMm,
    heightMm:
      action === 'EQUAL_HEIGHT' || action === 'EQUAL_SIZE' ? primary.heightMm : item.heightMm,
  }))
}

export function snapCanvasMove(options: {
  dx: number
  dy: number
  moving: readonly CanvasItem[]
  references: readonly ItemRect[]
  pxPerMm: number
  snapToGrid: boolean
  smartGuides: boolean
}): { dx: number; dy: number; guideLines: GuideLines } {
  let { dx, dy } = options
  const bounds = itemBounds(options.moving)
  if (options.snapToGrid) {
    dx = Math.round((bounds.xMm + dx) / 5) * 5 - bounds.xMm
    dy = Math.round((bounds.yMm + dy) / 5) * 5 - bounds.yMm
  }
  const guideLines: GuideLines = {}
  if (!options.smartGuides) return { dx, dy, guideLines }
  const xTargets = options.references.flatMap((item) => [
    item.xMm,
    item.xMm + item.widthMm / 2,
    item.xMm + item.widthMm,
  ])
  const yTargets = options.references.flatMap((item) => [
    item.yMm,
    item.yMm + item.heightMm / 2,
    item.yMm + item.heightMm,
  ])
  const threshold = 6 / options.pxPerMm
  const nearest = (anchors: number[], targets: number[]) => {
    let match: { delta: number; target: number } | undefined
    for (const anchor of anchors)
      for (const target of targets) {
        const delta = target - anchor
        if (Math.abs(delta) <= threshold && (!match || Math.abs(delta) < Math.abs(match.delta))) {
          match = { delta, target }
        }
      }
    return match
  }
  const xMatch = nearest(
    [bounds.xMm + dx, bounds.xMm + bounds.widthMm / 2 + dx, bounds.xMm + bounds.widthMm + dx],
    xTargets,
  )
  const yMatch = nearest(
    [bounds.yMm + dy, bounds.yMm + bounds.heightMm / 2 + dy, bounds.yMm + bounds.heightMm + dy],
    yTargets,
  )
  if (xMatch) {
    dx += xMatch.delta
    guideLines.xMm = xMatch.target
  }
  if (yMatch) {
    dy += yMatch.delta
    guideLines.yMm = yMatch.target
  }
  return { dx, dy, guideLines }
}
