/** 字典类型 */
export interface DictTypeVO {
  id: string
  dictCode: string
  dictName: string
  status: string
  createdAt: string
  updatedAt: string
}

/** 字典数据 */
export interface DictDataVO {
  id: string
  dictTypeId: string
  dictLabel: string
  dictValue: string
  cssClass: string
  listClass: string
  orderNum: number
  status: string
  createdAt: string
  updatedAt: string
}
