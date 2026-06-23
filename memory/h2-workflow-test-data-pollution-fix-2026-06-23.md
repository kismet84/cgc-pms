---
name: h2-workflow-test-data-pollution-fix-2026-06-23
description: Workflow H2 全量测试数据污染修复，收紧 WorkflowQueryServiceTest 清理范围并加固 WorkflowEngineIntegrationTest 用户种子
metadata:
  type: feedback
  tags:
    - backend
    - test
    - h2
    - workflow
    - data-isolation
---

# H2 Workflow 测试数据污染修复

## 现象

后端全量 `mvn test` 中，`WorkflowQueryServiceTest`、`WorkflowSubmitServiceTest`、`WorkflowEngineIntegrationTest` 组合运行时出现跨类污染：

- `WorkflowSubmitServiceTest` 报 `未找到业务类型 [CONTRACT_APPROVAL] 的审批模板`。
- `WorkflowEngineIntegrationTest` 报 `审批人不属于当前租户`。
- 单独运行部分测试类可以通过，组合或全量运行时失败。

## 根因

`WorkflowQueryServiceTest.cleanup()` 使用 tenant 级别的宽泛删除，清掉了后续测试依赖的 workflow 种子数据或上下文共享数据。H2 测试共用 Spring 应用上下文时，删除结果会污染后续测试类。

`WorkflowEngineIntegrationTest` 只恢复 `remark = 'test-seed'` 的用户租户，当其他测试或历史数据改变了用户 remark/status/tenant 时，审批人校验可能读取到不在当前租户的用户。

## 修复

- `WorkflowQueryServiceTest.cleanup()` 改为仅删除本测试类创建的模板、实例、任务、记录、抄送和幂等键，不再按 tenant 删除整张 workflow 数据。
- `WorkflowEngineIntegrationTest` 的测试用户种子恢复改为覆盖 `id BETWEEN 1 AND 5`，同时恢复 tenant、status、remark 和关键 real_name。

## 验证

- `.\mvnw.cmd "-Dtest=WorkflowQueryServiceTest,WorkflowSubmitServiceTest" test` 通过，21 tests，0 failures，0 errors。
- `.\mvnw.cmd -Dtest=WorkflowEngineIntegrationTest test` 通过，16 tests，0 failures，0 errors。
- `.\mvnw.cmd test` 跑到 10 分钟超时，原 3 个 workflow 污染类已不在失败报告中；剩余失败集中在其他测试类的 `审批人不属于当前租户` 或由其引发的 400/断言。

## 教训

H2 全量套件中禁止测试类用 tenant 级别清理共享种子表。清理逻辑必须绑定当前测试类的业务类型、业务 ID、模板 ID 或固定幂等键；共享测试用户恢复也不应依赖容易漂移的 remark 条件。
