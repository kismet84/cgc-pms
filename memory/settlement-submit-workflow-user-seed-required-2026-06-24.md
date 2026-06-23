---
name: settlement-submit-workflow-user-seed-required
description: StlSettlementWriteService 的 submitForApproval 测试依赖 workflow 模板审批人用户种子；缺少 sys_user 1~3 会报审批人不属于当前租户
metadata:
  type: toolchain
  feedback: resolved
tags:
  - backend
  - settlement
  - workflow
  - h2
  - test
---

# 结算提交审批测试需要 workflow 用户种子

## 现象

扩展 `StlSettlementServiceTest` 覆盖 `submitForApproval()` 后，目标测试失败：

```text
BusinessException: 审批人不属于当前租户
at ApproverResolver.resolveUser(...)
```

## 根因

`StlSettlementWriteService.submitForApproval()` 会调用 workflow 模板 `SETTLEMENT`，模板节点中的 `approverConfig` 依赖固定审批人（`sys_user.id = 1~3`）。

当前测试类只设置了 `UserContext`，但没有像 `WorkflowEngineIntegrationTest` 那样为 H2 本地测试环境补齐：

- `sys_user.id = 1` 系统管理员
- `sys_user.id = 2` 项目经理
- `sys_user.id = 3` 总经理

因此在 ApproverResolver 解析用户时，虽然模板存在，但审批人用户不在当前租户或根本不存在，最终报错。

## 修复

在 `StlSettlementServiceTest.setUp()` 中增加最小 workflow 用户种子：

- `INSERT ... WHERE NOT EXISTS` 写入 `sys_user` 1~3
- 再统一 `UPDATE sys_user SET tenant_id = 0, status = 'ENABLE', remark = 'test-seed' WHERE id BETWEEN 1 AND 3`

这样可保证测试环境与 workflow 模板的审批人配置一致。

## 验证

```bash
.\mvnw "-Dtest=StlSettlementServiceTest" test "-Djasypt.encryptor.password=dev-jasypt-key"
```

结果：

- `StlSettlementServiceTest` 13/13 全通过

## 教训

- 只要服务测试触发 workflow submit，就不能只 seed 业务表，还必须检查模板引用的审批人是否真实存在于当前租户。
- 这类失败常伪装成“业务 submit 逻辑坏了”，实际上是 **workflow 依赖用户 seed 缺失**。
