import { computed, reactive, type ComputedRef } from 'vue'

export interface ColumnSettingItem {
  key: string
  label: string
}

type GridColumn = Record<string, unknown>

function getColumnKey(column: GridColumn, index: number): string {
  return String(column.field ?? column.key ?? column.dataIndex ?? column.title ?? `column_${index}`)
}

function getColumnLabel(column: GridColumn, index: number): string {
  return String(column.title ?? column.field ?? column.key ?? column.dataIndex ?? `列 ${index + 1}`)
}

function loadSavedColumns(storageKey: string, defaults: Record<string, boolean>) {
  try {
    const raw = localStorage.getItem(storageKey)
    return raw ? { ...defaults, ...JSON.parse(raw) } : defaults
  } catch (e: unknown) {
    console.error(e)
    localStorage.removeItem(storageKey)
    return defaults
  }
}

export function useColumnSettings(storageKey: string, columns: ComputedRef<GridColumn[]>) {
  const defaultCols = computed<Record<string, boolean>>(() =>
    Object.fromEntries(columns.value.map((column, index) => [getColumnKey(column, index), true])),
  )

  const colVisible = reactive<Record<string, boolean>>(
    loadSavedColumns(storageKey, defaultCols.value),
  )

  const columnSettings = computed<ColumnSettingItem[]>(() =>
    columns.value.map((column, index) => ({
      key: getColumnKey(column, index),
      label: getColumnLabel(column, index),
    })),
  )

  const visibleColumns = computed(() =>
    columns.value.filter((column, index) => colVisible[getColumnKey(column, index)] !== false),
  )

  function toggleCol(key: string) {
    colVisible[key] = colVisible[key] === false
    localStorage.setItem(storageKey, JSON.stringify({ ...defaultCols.value, ...colVisible }))
  }

  return {
    colVisible,
    columnSettings,
    visibleColumns,
    toggleCol,
  }
}
