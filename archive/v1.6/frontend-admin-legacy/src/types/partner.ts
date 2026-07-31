export interface PartnerVO {
  id: string
  partnerCode: string
  partnerName: string
  partnerType: string
  creditCode: string
  legalPerson: string
  contactName: string
  contactPhone: string
  bankName: string
  bankAccount: string
  qualificationLevel: string
  blacklistFlag: boolean
  riskLevel: string
  status: string
  defaultLeadDays?: number | null
  createdAt: string
}
