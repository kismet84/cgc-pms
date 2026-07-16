export function suggestSupplierDeliveryDate(
  orderDate: string | undefined,
  defaultLeadDays: number | null | undefined,
): string | undefined {
  if (!orderDate || defaultLeadDays == null) return undefined
  if (!Number.isInteger(defaultLeadDays) || defaultLeadDays < 0 || defaultLeadDays > 3650) {
    return undefined
  }

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(orderDate)
  if (!match) return undefined
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(year, month - 1, day, 12)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return undefined
  }

  date.setDate(date.getDate() + defaultLeadDays)
  const yyyy = String(date.getFullYear()).padStart(4, '0')
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}
