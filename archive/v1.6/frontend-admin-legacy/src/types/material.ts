export interface MaterialVO {
  id: string
  tenantId: string
  materialCode: string
  materialName: string
  categoryId?: string
  specification?: string
  unit?: string
  brand?: string
  defaultTaxRate?: string
  status: string
  createdBy?: string
  createdAt: string
  updatedAt: string
  remark?: string
}
