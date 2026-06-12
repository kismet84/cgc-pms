# Decisions - Task 20: wf_cc Service

## Architecture
- WfCcService is a separate service (not merged into WorkflowEngine) — single responsibility
- WorkflowEngine.submit calls wfCcService.createCc() after notifyHandler() — cc is a side effect, not core approval logic
- NotificationService is injected into WfCcService (not WorkflowEngine) — keeps notification concerns separate

## Method Design
- createCc receives instanceId + ccUserIds + tenantId explicitly (no UserContext)
- getMyCc placed in WorkflowQueryService (alongside getMyTodos/getMyDone) for consistency
- getMyCc returns IPage<WfCcVO> with enriched instance info (same pattern as other query methods)

## ccUserIds parameter placement
- Added as LAST parameter to WorkflowEngine.submit() — minimizes churn in existing callers
- All existing callers pass null (no breaking changes for existing functionality)

## Notification
- Each cc user gets a separate notification via NotificationService.create
- bizType = instance.getBusinessType() (e.g., "CONTRACT_APPROVAL")
- Notification failures are caught and logged — do NOT roll back cc record creation
