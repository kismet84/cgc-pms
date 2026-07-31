export type CostControlRow = Record<string, unknown>

export interface ForecastInputItem extends CostControlRow {
  cost_subject_id: string
  subject_code: string
  subject_name: string
  bid_cost_amount: number | string
  target_amount: number | string
  responsibility_amount: number | string
  committed_amount: number | string
  actual_amount: number | string
  recommended_remaining_amount: number | string
  estimatedRemainingAmount?: number
}

export interface CostControlOverview {
  project: CostControlRow
  activeTarget: CostControlRow
  targetItems: CostControlRow[]
  forecastInputItems: ForecastInputItem[]
  latestForecast: CostControlRow
  forecastItems: CostControlRow[]
  correctiveActions: CostControlRow[]
  forecastHistory: CostControlRow[]
  costSources: CostControlRow[]
  summary: CostControlRow[]
}

export interface ForecastPayload {
  projectId: string
  forecastCode: string
  forecastName: string
  forecastDate: string
  items: Array<{
    costSubjectId: string
    estimatedRemainingAmount: number
    remark?: string
  }>
  remark?: string
}

export interface CorrectivePayload {
  forecastId: string
  actionCode: string
  actionTitle: string
  rootCause: string
  actionPlan: string
  expectedSavingAmount: number
  responsibleUserId: string
  dueDate: string
  remark?: string
}
