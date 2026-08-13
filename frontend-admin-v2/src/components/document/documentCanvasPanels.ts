import type { DocumentCanvasElement } from '@/services/system-management'
import type { AlignmentReference, LayoutAction } from './documentCanvasEngine'

export type ComponentPreset = 'TITLE' | 'TEXT' | 'DIVIDER' | 'TABLE' | 'HEADER' | 'FOOTER'
export type DocumentCanvasViewMode = 'DESIGN' | 'PREVIEW'

export interface DocumentCanvasControls {
  zoom: string
  viewMode: DocumentCanvasViewMode
  gridVisible: boolean
  snapToGrid: boolean
  smartGuides: boolean
  alignmentReference: AlignmentReference
  spacingMm: string
}

export type DocumentPropertiesCommand =
  | { type: 'toggle-orientation' }
  | { type: 'update-margin'; value: string }
  | { type: 'apply-layout'; action: LayoutAction }
  | { type: 'update-selected'; key: keyof DocumentCanvasElement; value: string }
  | { type: 'update-table-column'; index: number; patch: { header?: string; widthMm?: number } }
  | { type: 'move-table-column'; index: number; offset: -1 | 1 }
  | { type: 'remove-table-column'; index: number }
  | { type: 'remove-selected' }
