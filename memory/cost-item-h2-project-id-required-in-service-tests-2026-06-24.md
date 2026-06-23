---
name: cost-item-h2-project-id-required-in-service-tests
description: 为依赖 cost_item 的服务测试手工插入前置数据时，H2 要求 project_id 非空，否则会抛 DataIntegrityViolationException
metadata:
  type: toolchain
  feedback: resolved
tags:
  - backend
  - h2
  - test
  - cost-item
  - bid-cost
---

# cost_item 测试前置数据必须补 project_id

## 现象

扩展 `BidCostServiceTest` 时，为 `markAsWon()` / `markAsLost()` 手工插入 `cost_item(source_type='BID_COST')` 作为前置数据，测试直接失败：

```text
NULL not allowed for column "project_id"
org.springframework.dao.DataIntegrityViolationException
```

## 根因

测试 helper 只填了：

- `tenantId`
- `costSubjectId`
- `costType`
- `amount`
- `sourceType`
- `sourceId`

但 H2 `cost_item` 表实际要求 `project_id` 非空。业务代码在正常流程里通常会自动带上项目维度，所以这个约束不明显；手工造测试数据时就容易漏掉。

## 修复

在测试 helper 中显式补齐：

```java
item.setProjectId(PROJECT_ID);
```

然后重跑 `BidCostServiceTest`，11/11 全通过。

## 教训

- 只要测试需要手工插入 `cost_item`，就不要假设“只填 source_* 就够了”。
- 最少要核对：`tenant_id / project_id / cost_subject_id / source_type / source_id / cost_status` 这些关键字段是否满足表约束与聚合逻辑。
- 这类失败是**测试前置数据缺失**，不要误判成业务服务代码 bug。
