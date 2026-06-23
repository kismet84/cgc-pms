---
name: contract-revenue-subject-selectone-too-many-results
description: ContractRevenueService 收入科目兜底查询在 H2 种子存在多个 REVENUE 科目时 selectOne 抛 TooManyResultsException 的修复记录
metadata:
  type: bugfix
  feedback: resolved
tags:
  - backend
  - test
  - h2
  - contract-revenue
  - mybatis-plus
---

# ContractRevenueService 收入科目兜底 selectOne 多结果修复

## 现象

补充 `ContractRevenueServiceTest.testOnApproved_GeneratesRevenueCostItem` 时，`service.onApproved()` 在生成收入 `cost_item` 前失败：

```text
org.apache.ibatis.exceptions.TooManyResultsException: Expected one result (or null) to be returned by selectOne(), but found: 3
  at com.cgcpms.revenue.service.ContractRevenueService.resolveRevenueSubjectId
```

## 根因

`ContractRevenueService.resolveRevenueSubjectId()` 的精确科目 `subject_code = '6001.01'` 在 H2 本地种子中不存在，于是进入兜底分支：按 `account_category = 'REVENUE'`、`status = 'ENABLE'` 查询收入科目。

兜底分支原本使用 `costSubjectMapper.selectOne(wrapper)`，但 H2 种子里有 3 条启用的收入科目。MyBatis-Plus 的 `selectOne()` 发现多条记录时会抛 `TooManyResultsException`，不会自动取第一条。

## 修复

按代码注释“兜底按 accountCategory='REVENUE' 查找第一个启用的收入科目”的真实语义，把兜底查询从 `selectOne()` 改为：

```java
List<CostSubject> subjects = costSubjectMapper.selectList(wrapper);
subject = subjects.isEmpty() ? null : subjects.get(0);
```

仍保留原排序：`orderByAsc(CostSubject::getLevel, CostSubject::getSortOrder)`，因此取到的是稳定的第一条收入科目。

## 验证

目标测试通过：

```bash
.\mvnw "-Dtest=ContractRevenueServiceTest,StlSettlementQueryServiceTest" test "-Djasypt.encryptor.password=dev-jasypt-key"
```

结果：15 个测试全部通过。

全量 `mvn verify` 复核后，失败仍只来自既有 `OverheadAllocationServiceTest` H2 `CONSTRAINT_INDEX_E` 碰撞（4 failures + 1 error），未新增收入或结算测试失败。

## 教训

- MyBatis-Plus `selectOne()` 只适合数据库或业务条件能保证唯一的查询。
- “按排序取第一条”的兜底语义必须显式使用 `selectList()` + `get(0)`，否则种子数据一多就会变成运行时异常。
- H2 本地种子可能比生产精确科目少，但同类科目多；测试应覆盖这类兜底路径。
