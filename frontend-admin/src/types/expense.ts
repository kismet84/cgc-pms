export interface ExpenseApplicationVO {
  id: string
  projectId: string
  contractId: string
  costSubjectId: string
  budgetLineId: string
  payeePartnerId: string
  expenseCode: string
  expenseCategory: string
  expenseDate: string
  amount: string
  convertedAmount?: string
  paidAmount?: string
  availableToConvert?: string
  description: string
  status: string
  approvalStatus: string
  version?: number
  createdAt?: string
  updatedAt?: string
  remark?: string
}
