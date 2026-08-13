export interface BidFundAccountOption {
  id: string
  accountName: string
  accountType: string
  enabledFlag: number
}

export interface PaymentSourceOptionRecord {
  sourceType: string
  sourceRefId: string
  documentCode: string
  sourceTotalAmount: string
  committedAmount: string
  availableAmount: string
}
