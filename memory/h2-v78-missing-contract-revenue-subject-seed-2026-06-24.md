---
name: h2-v78-missing-contract-revenue-subject-seed
description: H2 的 V78 收入科目种子缺失 6001.01~6001.04，导致 ContractRevenueService 精确分支长期无法稳定测试
metadata:
  type: bugfix
  feedback: resolved
tags:
  - backend
  - h2
  - flyway
  - migration
  - contract-revenue
  - test
---

# H2 V78 缺失收入子科目种子修复

## 现象

`ContractRevenueService.resolveRevenueSubjectId()` 的设计是：

1. 优先精确匹配 `subject_code = '6001.01'`
2. 未命中时才 fallback 到首个 `REVENUE + ENABLE` 科目

但在本地 H2 环境里，`ContractRevenueServiceTest` 长期只能稳定覆盖 fallback 分支，精确分支无法有效测试，导致分支覆盖率偏低、测试意图失真。

## 根因

对比发现：

- **MySQL** `db/migration/V78__seed_standard_cost_revenue_subjects.sql`
  已包含：
  - `6001.01 合同建造收入`
  - `6001.02 变更签证收入`
  - `6001.03 索赔收入`
  - `6001.04 奖励收入`

- **H2** `db/migration-h2/V78__seed_standard_cost_revenue_subjects.sql`
  只有一级收入科目：
  - `6001`
  - `6051`
  - `6301`

也就是说，**H2 的 V78 比 MySQL 少了一整层主营业务收入子科目**，其中最关键的就是 `6001.01`。

这会把一个“应当走精确匹配”的业务路径，硬生生改写成“只能走 fallback”。

## 修复

不能修改已应用迁移，因此新增：

- `backend/src/main/resources/db/migration/V92__backfill_contract_revenue_subject_600101.sql`
- `backend/src/main/resources/db/migration-h2/V92__backfill_contract_revenue_subject_600101.sql`

回填以下科目：

- `900201 / 6001.01 / 合同建造收入`
- `900202 / 6001.02 / 变更签证收入`
- `900203 / 6001.03 / 索赔收入`
- `900204 / 6001.04 / 奖励收入`

## 测试补强

在 `ContractRevenueServiceTest` 中新增/强化两类断言：

1. **精确命中分支**
   - `6001.01` 存在时
   - `onApproved()` 生成的 `cost_item.cost_subject_id` 必须为 `900201`

2. **fallback 分支**
   - 测试里临时将 `900201` 标记 `deleted_flag = 1`
   - 验证 `onApproved()` 回退到首个启用收入科目 `900200`

## 验证

目标测试：

```bash
.\mvnw "-Dtest=ContractRevenueServiceTest" test "-Djasypt.encryptor.password=dev-jasypt-key"
```

结果：

- `ContractRevenueServiceTest` 11/11 全通过

全量验证：

```bash
.\mvnw verify "-Djasypt.encryptor.password=dev-jasypt-key"
```

结果：

- `Tests run: 1236`
- `Failures: 0`
- `Errors: 0`
- `BUILD SUCCESS`

## 教训

- “某分支不好测” 很多时候不是测试设计问题，而是 **测试数据库 seed 不完整**。
- MySQL/H2 双迁移项目里，**种子数据结构必须对齐**，否则测试会被迫验证错误分支。
- 不要为了补覆盖率硬写 mock/绕路测试；先确认真实业务前提是否在本地环境存在。
