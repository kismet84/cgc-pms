# Learnings - Task 14: Stock Ledger

## Patterns Used
- MatWarehouse entity pattern: @Data + @EqualsAndHashCode(callSuper=true) + @TableName
- created_time/updated_time column overrides matching V35 migration (not createdAt/updatedAt)
- @TableField(exist = false) on BaseEntity's createdAt/updatedAt to avoid mapping conflicts
- MatWarehouseService pattern: LambdaQueryWrapper + UserContext.getCurrentTenantId()

## MyBatis-Plus @Version Optimistic Lock
- Add @Version on Integer version field
- MyBatis-Plus auto-adds `version = ?` to UPDATE WHERE clause
- When updateById returns 0 rows → version conflict → retry
- NOTE: MyBatis-Plus does NOT throw exception on version conflict, just returns 0

## Concurrent INSERT Handling
- When two threads both find no stock and try INSERT
- UNIQUE constraint on (warehouse_id, material_id) causes DuplicateKeyException
- Must catch DuplicateKeyException and fall back to UPDATE (increment) path
- Without this, concurrent first-stock-in will fail for one thread

## Test Patterns
- @Transactional on test methods ensures rollback after each test
- Concurrent tests CANNOT use @Transactional (thread pool breaks transaction)
- Use unique warehouse IDs for concurrent tests to avoid data conflicts
- CountDownLatch for synchronized thread start

## H2 Schema
- Must match V35 MySQL migration column definitions
- UNIQUE constraint on mat_stock matches V35: (warehouse_id, material_id) without tenant_id
- warehouse_id is tenant-scoped (each tenant has own warehouses), so cross-tenant isolation works
