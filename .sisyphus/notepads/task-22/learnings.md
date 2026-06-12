## Task 22 - Project Overview API

### Patterns Used
- DashboardService batch-load pattern: `selectBatchIds` / `selectList` with `in` conditions
- VO convention: All fields are `String` (even numeric IDs, counts, BigDecimal)
- BigDecimal.toPlainString() for zero returns "0" not "0.00" — match expectations accordingly
- `@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})` needed for circular refs
- UserContext.set() with Jwts.claims() for test tenant/user setup

### Gotchas
- WorkflowEngine.submit had pre-existing signature mismatches (8 files + 1 test) — added null for 12th ccUserIds param
- PayRecord.payApplicationId is NOT NULL in DB — test must set it
- Flyway V36 checksum mismatch in `test` profile (MySQL) — pre-existing, not related to changes
- The LSP (jdtls) is not installed — cannot check diagnostics before build

### Decisions
- ProjectOverviewService follows DashboardService pattern: standalone service, injected into controller
- N+1 prevention: each data source queried exactly once (7 total queries max per overview)
- CostSummary paidAmount can be inconsistent with pay_record — we use pay_record (SUCCESS) for accuracy
- Alert warningCount filters by current month (triggered_at range)
- Member names batch-loaded via sys_user selectBatchIds (not per-member queries)
