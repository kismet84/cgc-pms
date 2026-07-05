import { getDictDataByCode } from '@/api/modules/dict'
import type { DictDataVO } from '@/types/dict'

/**
 * 字典缓存：dictCode -> DictDataVO[]
 * 缓存 5 分钟，避免重复请求
 */
const dictCache = new Map<string, { data: DictDataVO[]; timestamp: number }>()
const CACHE_TTL = 5 * 60 * 1000 // 5 分钟

/**
 * 根据字典编码获取字典数据列表（带缓存）
 */
export async function fetchDictData(dictCode: string): Promise<DictDataVO[]> {
  const cached = dictCache.get(dictCode)
  if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
    return cached.data
  }

  try {
    const data = await getDictDataByCode(dictCode)
    dictCache.set(dictCode, { data, timestamp: Date.now() })
    return data
  } catch (e) {
    console.error(`Failed to fetch dict data for code: ${dictCode}`, e)
    return []
  }
}

/**
 * 根据字典编码和键值获取标签
 */
export async function getDictLabel(dictCode: string, dictValue: string): Promise<string> {
  if (!dictValue) return dictValue
  const data = await fetchDictData(dictCode)
  const item = data.find((d) => d.dictValue === dictValue)
  return item?.dictLabel ?? dictValue
}

/**
 * 根据字典编码获取键值-标签映射
 */
export async function getDictMap(dictCode: string): Promise<Map<string, string>> {
  const data = await fetchDictData(dictCode)
  const map = new Map<string, string>()
  data.forEach((item) => {
    map.set(item.dictValue, item.dictLabel)
  })
  return map
}

/**
 * 同步获取字典标签（仅在缓存命中时有效）
 */
export function getDictLabelSync(dictCode: string, dictValue: string): string {
  if (!dictValue) return dictValue
  const cached = dictCache.get(dictCode)
  if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
    const item = cached.data.find((d) => d.dictValue === dictValue)
    return item?.dictLabel ?? dictValue
  }
  return dictValue
}

/**
 * 清除字典缓存
 */
export function clearDictCache(dictCode?: string): void {
  if (dictCode) {
    dictCache.delete(dictCode)
  } else {
    dictCache.clear()
  }
}
