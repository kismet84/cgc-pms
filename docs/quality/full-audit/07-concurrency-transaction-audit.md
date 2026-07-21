# 全量审计：并发与事务

## 结论

**通过。评分 84/100。** 高风险资金、审批、分摊链具备锁、版本、幂等或条件更新控制。

## 证据

- `FundAccountMapper`、`CashJournalEntryMapper` 对关键读取使用 `FOR UPDATE`。
- `FundAccount`、`CashJournalEntry` 使用 MyBatis-Plus `@Version`。
- `WorkflowConcurrencyTest` 覆盖并发审批；`WorkflowEngineIntegrationTest` 覆盖重复幂等键与租户隔离。
- `PaymentFinancialConsistencyTest` 覆盖重复外部交易号不重复入账。
- `OverheadAllocationServiceTest` 覆盖并发执行数据库幂等门、维度键不碰撞与分摊分币守恒。
- 供应商招采使用带旧状态条件的更新，失败时拒绝并发覆盖。

## 剩余风险

- 未执行目标生产数据库并发压测；当前结论来自源码、集成测试和 CI，不替代目标环境容量验收。
- `REL-001` 关闭前需在目标环境验证连接池、锁等待、死锁指标与回滚。
