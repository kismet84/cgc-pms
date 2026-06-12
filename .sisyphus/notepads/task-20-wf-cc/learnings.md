# Learnings - Task 20: wf_cc Service

## Patterns
- WfCc entity does NOT extend BaseEntity (spec requirement) — uses its own id, tenantId, createdTime fields
- NotificationService.create takes EXPLICIT params (tenantId, userId, title, content, bizType, bizId) — never reads UserContext
- WfCcService follows same pattern: all params explicit, tenantId from wfInstance
- WorkflowQueryService.getMyCc follows same batch-enrichment pattern as getMyTodos/getMyDone (batch-fetch instances to avoid N+1)
- VO pattern: String-typed IDs, DateTimeFormatter for dates, enriched instance info

## Successful Approaches
- Adding ccUserIds as the LAST parameter to WorkflowEngine.submit (minimal disruption to existing callers)
- Using replaceAll in test files for the `"{}", "{}");` → `"{}", "{}", null);` pattern
- CtContractChangeService already had `null, null, null);` (3 nulls) — no change needed
- Notification sending wrapped in try-catch so cc record persists even if notification fails

## Gotchas
- ast_grep_replace with `$$$` metavariable can produce literal `$$$` in output — manual edits safer
- CtContractService uses `contractId` (not `contract.getContractId()`) in submit call
- PayApplicationService uses `payApp.getApplyReason()` as businessSummary (not null) — only 1 null for variables
