import type {
  DocumentCanvasElement,
  DocumentCanvasTable,
  DocumentCatalogField,
  DocumentDesignSchema,
  DocumentTemplateVersion,
} from '@/services/system-management'

export interface LegacyDesignConversion {
  designSchema: DocumentDesignSchema
  issues: string[]
  notices: string[]
}

export function blankDocumentDesign(schemaVersion: string): DocumentDesignSchema {
  return {
    schemaVersion,
    page: {
      size: 'A4',
      orientation: 'PORTRAIT',
      marginMm: { top: 12, right: 12, bottom: 12, left: 12 },
    },
    elements: [],
    tables: [],
  }
}

export function convertLegacyDocumentDesign(options: {
  version: DocumentTemplateVersion
  schemaVersion: string
  templateName: string
  catalogFields: readonly DocumentCatalogField[]
}): LegacyDesignConversion {
  const { version, schemaVersion, templateName, catalogFields } = options
  const issues: string[] = []
  const notices: string[] = []
  const orientation = /@page\s*\{[^}]*\blandscape\b/i.test(version.templateContent)
    ? 'LANDSCAPE'
    : 'PORTRAIT'
  const pageWidth = orientation === 'PORTRAIT' ? 210 : 297
  const margin = 12
  const width = pageWidth - margin * 2
  let manifest = new Set<string>()
  try {
    const parsed: unknown = JSON.parse(version.fieldManifest)
    if (!Array.isArray(parsed) || !parsed.every((item) => typeof item === 'string')) {
      throw new Error('invalid manifest')
    }
    manifest = new Set(parsed)
  } catch {
    issues.push('历史字段清单无效，需修复后再保存')
  }
  const fieldByPath = new Map(catalogFields.map((field) => [field.path, field]))
  const legacyAliases: Record<string, string> = {
    'items.orderQuantity': 'items.orderedQuantity',
    'items.cumulativeReceivedQuantity': 'items.receivedQuantity',
  }
  const resolvedFields = [...manifest].map((path) => ({
    originalPath: path,
    field: fieldByPath.get(legacyAliases[path] ?? path),
  }))
  const aliasFields = resolvedFields.filter(
    ({ originalPath, field }) => field && field.path !== originalPath,
  )
  if (aliasFields.length) {
    notices.push(
      `旧字段已映射：${aliasFields.map(({ originalPath, field }) => `${originalPath}→${field!.path}`).join('、')}`,
    )
  }
  const missingFields = resolvedFields
    .filter(({ field }) => !field)
    .map(({ originalPath }) => originalPath)
  if (missingFields.length) {
    notices.push(`无现行数据源字段已转为文本占位：${missingFields.join('、')}`)
  }
  const fields = resolvedFields.flatMap(({ field }) => (field ? [field] : []))
  const scalarFields = fields.filter((field) => !field.collectionPath)
  const collectionGroups = new Map<string, typeof fields>()
  fields
    .filter((field) => field.collectionPath)
    .forEach((field) => {
      const collectionPath = field.collectionPath!
      collectionGroups.set(collectionPath, [...(collectionGroups.get(collectionPath) ?? []), field])
    })
  for (const [collectionPath, collectionFields] of collectionGroups) {
    if (collectionFields.length > 30) issues.push(`${collectionPath} 超过30列，需精简后再保存`)
  }
  const tableGroups = [...collectionGroups.entries()].map(
    ([collectionPath, collectionFields]) =>
      [collectionPath, collectionFields.slice(0, 30)] as const,
  )
  const gap = 6
  const columnWidth = (width - gap) / 2
  const elements: DocumentCanvasElement[] = [
    {
      id: 'legacy-title',
      type: 'TEXT',
      text: templateName || '单据标题',
      xMm: margin,
      yMm: margin,
      widthMm: width,
      heightMm: 14,
      fontSizePt: 18,
      align: 'CENTER',
      repeat: 'BODY',
      zIndex: 0,
    },
    ...scalarFields.map((field, index) => ({
      id: `legacy-field-${index + 1}`,
      type: 'FIELD' as const,
      text: field.label,
      fieldPath: field.path,
      xMm: margin + (index % 2) * (columnWidth + gap),
      yMm: 34 + Math.floor(index / 2) * 13,
      widthMm: columnWidth,
      heightMm: 10,
      fontSizePt: 10,
      align: 'LEFT' as const,
      repeat: 'BODY' as const,
      zIndex: index + 1,
    })),
    ...missingFields.map((path, index) => ({
      id: `legacy-placeholder-${index + 1}`,
      type: 'TEXT' as const,
      text: `${legacyPlaceholderLabel(path)}：________`,
      xMm: margin + ((scalarFields.length + index) % 2) * (columnWidth + gap),
      yMm: 34 + Math.floor((scalarFields.length + index) / 2) * 13,
      widthMm: columnWidth,
      heightMm: 10,
      fontSizePt: 10,
      align: 'LEFT' as const,
      repeat: 'BODY' as const,
      zIndex: scalarFields.length + index + 1,
    })),
  ]
  const tableY = 38 + Math.ceil((scalarFields.length + missingFields.length) / 2) * 13
  const tables: DocumentCanvasTable[] = tableGroups.map(
    ([collectionPath, collectionFields], tableIndex) => {
      const columnWidthMm = Math.round((width / collectionFields.length) * 10) / 10
      return {
        id: `legacy-table-${tableIndex + 1}`,
        collectionPath,
        xMm: margin,
        yMm: tableY + tableIndex * 46,
        widthMm: width,
        heightMm: 38,
        columns: collectionFields.map((field, index) => ({
          fieldPath: field.path,
          header: field.label,
          widthMm:
            index === collectionFields.length - 1
              ? Math.round((width - columnWidthMm * index) * 10) / 10
              : columnWidthMm,
        })),
      }
    },
  )
  return {
    designSchema: {
      schemaVersion,
      page: {
        size: 'A4',
        orientation,
        marginMm: { top: margin, right: margin, bottom: margin, left: margin },
      },
      elements,
      tables,
    },
    issues,
    notices,
  }
}

function legacyPlaceholderLabel(path: string): string {
  return (
    {
      'receipt.totalAmountChinese': '本次合计金额（大写）',
      'signatures.supplierRepresentative': '供应商代表',
      'signatures.receiver': '验收人',
      'signatures.projectManager': '项目负责人',
      'signatures.warehouseKeeperOrUser': '仓库管理员/使用人',
    }[path] ?? path
  )
}
