# Learnings - Task 17: Notification Backend Module

## Patterns Used
- Entity: SysNotification does NOT extend BaseEntity (minimalist columns, no deleted_flag/remark/updated_by)
- Service: ALL methods use EXPLICIT tenantId/userId params — NEVER reads UserContext
- SSE: SseEmitter stored in ConcurrentHashMap<userId, SseEmitter>, pushed on create, cleaned up on error/complete/timeout
- Controller: reads UserContext in controller layer, passes explicit params to service
- VO: NotificationVO uses String-typed IDs (frontend JS compatibility), static fromEntity() converter
- Mapper: extends BaseMapper<SysNotification>, @Mapper annotated — no custom methods needed
- Tests: @SpringBootTest + @ActiveProfiles("local") + H2, UserContext set via Jwts.claims()
- Cross-tenant isolation: LambdaQueryWrapper.eq tenantId on every query, selectById then Objects.equals check

## Conventions
- Error codes: NOTIFICATION_NOT_FOUND
- Auth: @PreAuthorize("hasAuthority('notification:view') or hasRole('ADMIN')")
- SSE events: "connected" (on subscribe), "notification" (on create)
- No BaseEntity extension per sys_notification table design (V37)

## Issues Encountered
- MatPurchaseRequestService had missing PageResult import (pre-existing) — fixed to enable full compilation
- WorkflowEngineIntegrationTest fails on test profile (Flyway MySQL checksum mismatch) — pre-existing, unrelated
- MatStockServiceTest concurrency issues — pre-existing, unrelated
