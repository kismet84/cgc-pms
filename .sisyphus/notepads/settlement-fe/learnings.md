# Settlement Management Frontend — Learnings

## Patterns Used

### Design System (consistent with existing pages)
- Background: `#f6f8fc` / `#f0f2f5`
- Cards: `#fff` bg, 1px border `#e5eaf3`, border-radius `10px`, box-shadow `0 10px 30px rgba(17,24,39,0.05)`
- KPI cards: height 96px, padding 18px, icon circle 32px, value font 21px/800
- Typography: title 15px/700, KPI value 21px/800, labels 13px/14px
- Spacing: gaps 10px, 12px, 14px, 16px
- Links: `#1677ff`, danger: `#ef4444`

### Component Library
- Ant Design Vue: `a-page-header`, `a-card`, `a-tabs`/`a-tab-pane`, `a-descriptions`, `a-table`, `a-timeline`, `a-pagination`, `a-breadcrumb`, `a-modal`, `a-spin`, `a-tag`, `a-empty`
- VxeGrid (vxe-grid) for list table with resizable columns, stripe, border, checkbox
- Icons from `@ant-design/icons-vue`

### API Pattern
- TypeScript files in `api/modules/` exporting functions that call `request<T>()` from `api/request.ts`
- Axios interceptor unwraps `ApiResponse.data` automatically on code='0'

### Store Pattern (Pinia)
- Composition API style with `defineStore`
- Separate loading flags per data type
- `resetState()` for cleanup

### Backend Alignment
- Backend entity: `StlSettlement` (table: `stl_settlement`) 
- Fields: settlementCode, contractAmount, changeAmount, measuredAmount, deductionAmount, paidAmount, finalAmount, unpaidAmount, warrantyAmount
- Settlement status: DRAFT/FINALIZED/CANCELLED
- Approval status: DRAFT/APPROVING/APPROVED/REJECTED/WITHDRAWN
- Formula: finalAmount = contractAmount + changeAmount + measuredAmount - deductionAmount
- Formula: unpaidAmount = finalAmount - paidAmount - warrantyAmount

### MUST DO Rules Implemented
- FINALIZED settlements: delete button hidden, edit button hidden, submit button hidden
- All amounts in summary tab are read-only (no input fields)
- Submit button required for workflow transition (never direct status mutation)
- source_type/source_id clickable links in cost detail tab with jumpToSource()
