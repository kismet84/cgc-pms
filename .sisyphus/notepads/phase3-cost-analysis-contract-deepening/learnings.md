# Learnings - Phase 3 Cost Analysis & Contract Deepening

## Task 1: H2 审批提交权限收紧

### Permission Pattern
- Project uses `@PreAuthorize("hasRole('ADMIN') or hasAuthority('xxx:action')")` for declarative auth
- For dynamic permission checks (where permission depends on runtime data like request body), programmatic checks are needed
- `WorkflowController.submit` uses `request.businessType` to determine the required permission at runtime

### BusinessType → Permission Mapping
| BusinessType | Permission Code | Controller Where Submit Permission Defined |
|---|---|---|
| CONTRACT_APPROVAL | contract:submit | CtContractController L62 |
| PURCHASE_ORDER | purchase:order:submit | MatPurchaseOrderController L68 |
| MATERIAL_RECEIPT | receipt:submit | MatReceiptController L69 |
| SUB_MEASURE | subcontract:measure:submit | SubMeasureController L80 |
| PAY_REQUEST | payment:app:submit | PayApplicationController L80 |
| VAR_ORDER | variation:order:submit | VarOrderController L69 |
| CT_CHANGE | contract:change:submit | Phase 3 (new) |
| SETTLEMENT | settlement:submit | Phase 3 (new) |
| COST_TARGET | cost:target:submit | Phase 3 (new) |

### Implementation Approach
- Programmatic check using `SecurityContextHolder.getContext().getAuthentication().getAuthorities()`
- Check for `ROLE_ADMIN` OR specific permission code
- Throw `AccessDeniedException` if neither matches
- Kept `@PreAuthorize("isAuthenticated()")` as first line of defense
- Did NOT modify WorkflowEngine or WorkflowService (task constraint)

### Flyway V21
- Added 8 BUTTON-type sys_menu entries (ids 600-607) with `INSERT IGNORE INTO`
- These are pure permission codes - no paths/components needed
- `contract:submit` already existed in V6 (id=305)

### Integration Test Impact
- Existing tests call `workflowEngine.submit()` directly (bypassing controller layer)
- Controller-level permission changes do NOT affect existing integration tests
- This is by design - integration tests test the engine, not the API layer

### Auth Architecture Notes
- JWT tokens contain CLAIM_ROLES and CLAIM_PERMISSIONS in claims
- `JwtAuthenticationFilter.buildAuthorities()` converts: roles → `ROLE_` prefix, permissions → direct authority strings
- `UserContext` is ThreadLocal-based, populated by JWT filter, cleared after each request

## Task 2: Flyway V22 — cost_target + cost_target_item 表

### Design Doc vs Task Requirements
- Design doc §9.2 baseline: `version_no`, `target_amount`, `effective_date`, `approval_status`, `status`
- Task adds beyond design doc: `tenant_id`, `is_active`, `version_name`, `total_target_amount` (renamed from `target_amount`)
- `cost_target_item`: design doc uses `budget_amount` → task uses `target_amount`
- Task explicitly requires audit columns named `created_time`/`updated_time` (not codebase convention `created_at`/`updated_at`)

### MySQL Partial Index Limitation
- MySQL 8.0 does NOT support partial/conditional indexes (PostgreSQL has `CREATE UNIQUE INDEX ... WHERE`)
- Cannot enforce "one active version per project" at database level via unique constraint
- Constraint must be enforced at application layer (Service transaction + SELECT FOR UPDATE)
- Documented in migration header comment for future implementers

### Migration Conventions Followed
- `SET NAMES utf8mb4; SET FOREIGN_KEY_CHECKS = 0/1;` frame
- `CREATE TABLE IF NOT EXISTS` for idempotency
- `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`
- Audit columns + `deleted_flag` + `remark` on both tables
- Index naming: `idx_{table}_{column}` pattern (e.g., `idx_cost_target_project`)
- No `INSERT IGNORE` — no demo data specified for these tables
- `cost_target.remark` uses TEXT (per task), `cost_target_item.remark` uses VARCHAR(500) (per convention)
- Both tables include `tenant_id` for multi-tenant isolation
- `cost_target_item` includes `project_id` for query convenience (avoids JOIN to parent table)

## Task 3: Flyway V23 — ct_contract_change 合同变更表

### Distinct from VAR_ORDER
- `ct_contract_change` = 正式合同变更（修改合同金额/工期/条款）
- `var_order` = 现场签证（施工现场发生的变更/签证单）
- Two separate business concepts; different tables, different entities, different workflows

### Key Design Decisions
- `change_type` = `AMOUNT` | `DURATION` | `CLAUSE` — enumerated in COMMENT for self-documenting SQL
- `before_amount` / `change_amount` / `after_amount` — three-value pattern for audit trail (before → delta → after)
- `effective_flag` — separates approval from effect (approved ≠ effective yet)
- `cost_generated_flag TINYINT NOT NULL DEFAULT 0` — required for cost idempotency (same pattern as var_order, mat_receipt, sub_measure)
- `contract_id` is NOT NULL — every change must belong to a contract
- `reason TEXT` — free-form long text for business justification

### Index Strategy
- `uk_ct_change_code (tenant_id, change_code)` — tenant-scoped unique change number
- `idx_change_contract (contract_id)` — query all changes for a contract
- `idx_change_project (project_id)` — query all changes within a project

### Style Alignment
- Followed V22 conventions: `created_time`/`updated_time` (not V12's `created_at`/`updated_at`)
- `VARCHAR(500)` for `remark`, `TEXT` for `reason` (substantive content)
- All DECIMAL columns use `(18,2)` precision
- `IF NOT EXISTS` for idempotency
- Multi-tenant pattern: `tenant_id BIGINT NOT NULL DEFAULT 0`

## Task 5: 修复 4 个 CostGenerationStrategy 的 costSubjectId 缺失

### Root Cause
- All 4 strategies generated CostItem without ever setting `costSubjectId`
- `CostSummaryService.refreshSummary()` line 77 filters out null costSubjectId items: `.filter(item -> item.getCostSubjectId() != null)`
- Result: all costs collapsed to null group → subject-level drill-down completely broken in dashboard

### Source Entity Analysis
| Strategy | Source Entity | Has costSubjectId? | Resolution |
|---|---|---|---|
| VarOrderCostStrategy | VarOrderItem | ✅ Yes (V12 schema L278) | Direct read: `item.getCostSubjectId()` |
| ContractCostStrategy | CtContractItem | ❌ No | Lookup `cost_subject.subject_type = '合同'` |
| MaterialReceiptCostStrategy | MatReceiptItem | ❌ No | Lookup `cost_subject.subject_type = '材料'` |
| SubMeasureCostStrategy | SubMeasureItem | ❌ No | Lookup `cost_subject.subject_type = '分包'` |

### cost_type → subject_type Mapping
| cost_type | subject_type | Strategy |
|---|---|---|
| CONTRACT_LOCKED | 合同 | ContractCostStrategy |
| MATERIAL | 材料 | MaterialReceiptCostStrategy |
| SUBCONTRACT | 分包 | SubMeasureCostStrategy |
| VARIATION | (from VarOrderItem) | VarOrderCostStrategy |

### Default Subject Resolution Strategy
- **1st attempt**: exact match on tenant_id + subject_type + status=ENABLE, ordered by level ASC
- **2nd fallback**: any root-level subject (parent_id=0) for the tenant
- **3rd fallback**: any enabled subject for the tenant
- **Last resort**: return null + log ERROR (should never happen in production — system requires at least one cost_subject)

### Idempotency
- `uk_cost_source_item (source_type, source_id, source_item_id, cost_type)` does NOT include `cost_subject_id`
- Adding costSubjectId to CostItem does NOT affect the unique constraint → safe to add without breaking idempotency

### Flyway V25 Backfill
- 4 UPDATE statements, one per source_type
- VAR_ORDER: direct JOIN to var_order_item.cost_subject_id
- MAT_RECEIPT/SUB_MEASURE/CT_CONTRACT: subquery to cost_subject by matching subject_type
- Uses `cost_subject_id IS NULL` guard to avoid overwriting already-correct data
- MySQL derived table workaround for same-table subquery UPDATE restriction

### Duplicate Import Fixed
- ContractCostStrategy had accidental double-import of `LambdaQueryWrapper` during initial edit — fixed

## Task 6: CostTarget/CostTargetItem Java 层

### V22 Column Naming vs BaseEntity Mismatch
- V22/V23 tables use `created_time` / `updated_time` (not Phase 1/2 convention `created_at` / `updated_at`)
- `BaseEntity` maps `createdAt` → `created_at`, `updatedAt` → `updated_at` via MyBatis-Plus camel-to-underscore
- Solution: add `createdTime`/`updatedTime` fields with explicit `@TableField("created_time")` / `@TableField("updated_time")`, and shadow `BaseEntity.createdAt`/`updatedAt` with `@TableField(exist = false)` to prevent mapping conflicts
- Other audit fields (`createdBy`→`created_by`, `updatedBy`→`updated_by`, `deletedFlag`→`deleted_flag`, `remark`→`remark`) match and are inherited normally
- This pattern should be used for all V22+ entities

### Files Created
| File | Location | Notes |
|------|----------|-------|
| CostTarget.java | `cost/entity/` | Extends BaseEntity, shadows createdAt/updatedAt |
| CostTargetItem.java | `cost/entity/` | Same BaseEntity override pattern |
| CostTargetMapper.java | `cost/mapper/` | MyBatis-Plus BaseMapper |
| CostTargetItemMapper.java | `cost/mapper/` | MyBatis-Plus BaseMapper |
| CostTargetService.java | `cost/service/` | CRUD + activate (version switching) + delete guard |
| CostTargetController.java | `cost/controller/` | 6 endpoints, @PreAuthorize per operation |
| V26__add_cost_target_id_to_cost_summary.sql | `db/migration/` | Adds cost_target_id to cost_summary |

### CostSummary Modified
- Added `costTargetId` field to `CostSummary.java` — enables delete guard check

### Version Switching (Activate)
- `@Transactional` + `SELECT FOR UPDATE` (`LambdaQueryWrapper.last("FOR UPDATE")`)
- Within transaction: deactivate all other versions (`is_active=0`), then activate target (`is_active=1, status=ACTIVE`)
- Concurrent-safety: row-level lock prevents two threads activating different versions for same project

### Delete Guard
- Checks `cost_summary` for rows referencing this `cost_target_id`
- Also blocks deletion when `approvalStatus=APPROVING`

### Permission Codes Used
- `cost:target:query` — list + getById
- `cost:target:add` — create
- `cost:target:edit` — update
- `cost:target:delete` — delete
- `cost:target:activate` — activate (version switching)
- `cost:target:submit` — reserved for Task 13 (workflow submission)

### No Approval Logic
- Task constraint: approval workflow is implemented in Task 13
- Service sets default `approvalStatus=DRAFT`, `status=DRAFT`, `isActive=0` on create
- Update guards against editing when `APPROVING`

## Task 11: CtContractChangeWorkflowHandler + 审批 + 成本联动

### Key Distinction from VAR_ORDER
- **CT_CHANGE** updates `ct_contract.currentAmount` (VAR_ORDER does NOT touch contract amounts)
- **VAR_ORDER** is a separate entity for on-site variations; CT_CHANGE is formal contract amendment
- `contractAmount` is NEVER modified — it's the original signed amount

### Handler Design
- `supportBusinessType()` returns `WorkflowBusinessTypes.CT_CHANGE` (constant already defined in Task 1)
- `isCritical()` = true — cost generation failure rolls back entire approval transaction
- `onApproved()`: updates change approvalStatus=APPROVED + effectiveFlag=1, then updates contract currentAmount, then generates cost via CostGenerationService, then refreshes cost_summary
- `onRejected()`: only sets approvalStatus=REJECTED, no amount/cost changes
- `onWithdrawn()`: resets to DRAFT status
- Pattern follows VarOrderWorkflowHandler but with the critical currentAmount update

### Flyway V28 Migration
- Template ID: 50007 (follows V9=50001, V13=50002, V14=50003, V15=50004, V16=50005, V17=50006)
- Node IDs: 50701-50703
- Table names: `wf_template` and `wf_template_node` (NOT `wf_approval_template` as task spec might suggest in comment)
- Same 3-node sequential pattern as all other approval templates

### Submit Endpoint
- `CtContractChangeService.submitForApproval()` was a stub (Task 7 left it as TODO)
- Implemented following VarOrderService pattern:
  1. Validate DRAFT status
  2. Update to APPROVING
  3. Call `workflowEngine.submit()` with CT_CHANGE business type
- Required injecting `WorkflowEngine` into the service

### Cost Summary Refresh
- Task explicitly requires `cost_summary` refresh after cost generation
- Uses `CostSummaryService.refreshSummary(tenantId, projectId)`
- CostSummaryService already handles CT_CHANGE source items via general cost item grouping (no special CT_CHANGE filtering needed in refreshSummary)

## Task 14: 预警中心 — AlertLog 实体 + 8 规则评估器 + @Scheduled

### AlertLog Column Naming Mismatch
- alert_log table (V24) uses `created_time` / `updated_time` (same as V22/V23 convention)
- BaseEntity maps `createdAt` → `created_at`, `updatedAt` → `updated_at` — mismatch
- Pattern from Task 6: use `createdAt`/`updatedAt` field names with `@TableField("created_time")` / `@TableField("updated_time")`
- MetaObjectHandler fills fields BY NAME (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `tenantId`), so using these field names ensures auto-fill works
- AlertLog does NOT extend BaseEntity — defines all fields directly for clarity since column names deviate

### 8 Rules Implemented
| # | rule_type | Severity | Check Logic |
|---|-----------|----------|-------------|
| 1 | DYNAMIC_COST_EXCEEDS_TARGET | HIGH | cost_summary.dynamicCost > targetCost |
| 2 | MATERIAL_EXCEEDS_BUDGET | MEDIUM | SUM(mat_receipt totalAmount, APPROVED) > contract.contractAmount |
| 3 | SUBCONTRACT_EXCEEDS_CONTRACT | HIGH | SUM(sub_measure approvedAmount, APPROVED) > contract.contractAmount |
| 4 | CONTRACT_OVERDUE | HIGH | endDate < today, contractStatus=PERFORMING |
| 5 | PAYMENT_EXCEEDS_RATIO | HIGH | SUM(pay_record payAmount, SUCCESS) > contract.contractAmount |
| 6 | WARRANTY_EARLY_RELEASE | MEDIUM | settlementStatus=FINALIZED, warrantyAmount>0, contract.endDate>today |
| 7 | CONTRACT_EXPIRING | LOW | endDate within 30 days, contractStatus=PERFORMING |
| 8 | VARIATION_UNCONFIRMED | MEDIUM | ownerConfirmFlag=0, approvalStatus=APPROVED, created > 30 days ago |

### @Scheduled Pattern
- Followed CostSummaryService pattern exactly: iterate active projects, per-project try/catch, @Scheduled(cron="0 */30 * * * ?")
- @EnableScheduling already present in CgcPmsApplication

### Deduplication Strategy
- 24-hour window: check if unread alert (is_read=0) with same rule_type + project_id exists within last 24 hours
- Prevents flooding on every 30-min cycle

### Javadoc Warning
- `*&#47;` in Javadoc is interpreted as end-of-comment
- Must escape `*/` as `*&#47;` when it appears inside @Scheduled cron expressions in Javadoc

## Task 12: SettlementWorkflowHandler + 审批模板 + 不可变锁定

### Key Design Decisions

#### Settlement is Read-Only (NO CostGenerationService)
- Settlement is the FINAL step in the cost chain — it locks amounts, never generates new costs
- `ContractWorkflowHandler` calls `CostGenerationService.generateLockedCost()` but SettlementWorkflowHandler does NOT
- Settlement aggregates: contract amount + confirmed var orders + approved sub measures - paid amounts = final amount
- 禁止调用 CostGenerationService — 防循环依赖（结算汇总→成本→结算循环）

#### CtContract.settlementAmount Field Did Not Exist
- `CtContract` had no `settlementAmount` field before this task
- Added via V29 migration: `ALTER TABLE ct_contract ADD COLUMN settlement_amount DECIMAL(18,2)`
- Added field to entity with `@JsonSerialize(using = ToStringSerializer.class)` for JS precision safety
- Field is written ONLY on settlement approval (onApproved callback)

#### ID Assignment Conflict Prevention
- V28 (CT_CHANGE) already uses template ID 50007, node IDs 50701-50703
- V29 settlement template uses template ID 50008, node IDs 50801-50803
- IMPORTANT: Always check the latest existing V*_template.sql before assigning IDs for new templates

#### Pre-Submit Validation (beforeSubmit)
- Check 1: No unapproved variation orders (VarOrder, direction=COST, approvalStatus not APPROVED/REJECTED)
- Check 2: No unapproved sub measures (SubMeasure, approvalStatus not APPROVED/REJECTED)
- Both queries enforce tenantId filtering manually
- Uses `BusinessException` (not `IllegalStateException`) for user-facing validation errors

#### Immutability Guard (FINALIZED)
- StlSettlementService.update/delete already check `!"DRAFT".equals(approvalStatus)` — covers FINALIZED
- onApproved sets settlementStatus=FINALIZED and approvalStatus=APPROVED → any subsequent edit/delete attempt will fail
- No additional service changes needed; existing guards suffice

#### Template Pattern Consistency
- Same 3-node sequential pattern: 项目经理 → 部门经理 → 总经理
- Same approver config: `JSON_OBJECT('type', 'USER', 'userId', 1)` (placeholder)
- Same timeout_hours: 48/48/72
- INSERT IGNORE INTO wf_template / wf_template_node for idempotency

### Files Created/Modified

| File | Action | Description |
|------|--------|-------------|
| `settlement/handler/SettlementWorkflowHandler.java` | NEW | Handler with beforeSubmit/onApproved/onRejected/onWithdrawn |
| `db/migration/V29__init_settlement_approval_template.sql` | NEW | ALTER TABLE + INSERT wf_template + INSERT wf_template_node |
| `database/migration/V29__init_settlement_approval_template.sql` | NEW | Mirror copy for database folder |
| `contract/entity/CtContract.java` | MODIFIED | Added `settlementAmount` field (BigDecimal) |

### Verification
- `./mvnw clean compile`: BUILD SUCCESS, 231 source files compiled, 0 errors

## Task 20: 驾驶舱前端 — 项目经理 + 商务经理视图 (ECharts)

### Files Created
| File | Description |
|------|-------------|
| `frontend-admin/src/types/dashboard.ts` | Dashboard type definitions (PM/BM VOs, shared VOs, enums) |
| `frontend-admin/src/api/modules/dashboard.ts` | Dashboard API functions (PM, BM, project contracts) |

### Files Modified
| File | Description |
|------|-------------|
| `frontend-admin/src/pages/dashboard/index.vue` | Complete rewrite: role tabs, PM/BM views, ECharts pie, polling |

### Design System Patterns Used
- CSS variables from `global.css`: `--bg: #f6f8fc`, `--border: #e5eaf3`, `--text: #1f2937`, `--muted: #6b7280`, `--primary: #1677ff`
- KPI cards: 96px height, 18px padding, 32px icon circle, 13px title, 21px value, 10px border-radius
- Panel cards: `.dash-card` with `box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05)`
- Color palette matches existing convention: `#3b82f6` (blue), `#f59e0b` (amber), `#8b5cf6` (purple), `#22c55e` (green), `#ef4444` (red), `#14b8c7` (teal)

### Backend API Mapping
- `GET /dashboard/project-manager?projectId=` → `ProjectManagerDashboardVO` (pendingTaskCount, laggingProjectCount, pendingApprovalCount, expiringContractCount + detail lists)
- `GET /dashboard/business-manager?projectId=` → `BusinessManagerDashboardVO` (totalContractAmount, contractChangeAmount, varOrderAmount, subMeasureAmount, paidRatio, settlementProgress + recentChanges)
- `GET /contracts?projectId=&pageNum=1&pageSize=1000` → Used for pie chart contract type distribution aggregation (backend doesn't expose type distribution directly)

### ECharts Integration
- `vue-echarts` v7.0.3 with `VChart` component, `autoresize` prop
- Chart types already registered in `main.ts`: PieChart, BarChart, LineChart, CanvasRenderer, TitleComponent, TooltipComponent, LegendComponent, GridComponent
- Pie chart: donut style (`radius: ['45%', '72%']`), contract type colors, click-to-drill-down to contract ledger with pre-filtered type

### Implementation Notes
- **Time range filter** (本月/本季度/本年): UI present but backend doesn't support time-based filtering for dashboard endpoints. Ready for future backend support.
- **Polling**: 30-second `setInterval` on `onMounted`, cleared on `onUnmounted`. Only refreshes active role view.
- **Role switch**: Auto-triggers data fetch if target view not yet loaded.
- **Project auto-select**: First project auto-selected on load.
- **Pie chart drill-down**: Click segment → navigate to `/contract/ledger?contractType=X&projectId=Y` (1-level drill, per constraint).
- **No static KPIs**: All data from real API. Zero hardcoded mock values.
- **No custom chart builder**: Uses standard vue-echarts VChart with ECharts option object.

## MySQL 验证经验 (2026-06-12)

### 有效做法
1. 使用 strictInsertFill/strictUpdateFill 处理多种字段命名（createdAt + createdTime）
2. MySQL JSON 列不适合存储纯文本，应使用 TEXT/VARCHAR
3. Flyway INSERT IGNORE 在 ID 冲突时静默跳过，需人工检查
4. validate-on-migrate: false 可绕过 checksum 校验，但不能解决重复列问题

### 测试验证流程
1. 先检查 Flyway history 确认所有迁移状态
2. 逐一排查失败迁移的根因
3. 修复后重新运行完整测试套件
4. 如实记录通过/失败/限制
