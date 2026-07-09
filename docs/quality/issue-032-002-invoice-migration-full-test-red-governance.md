# ISSUE-032-002 invoice 与 migration 全量测试红灯项目关系治理

日期：2026-07-09

## 结论

通过。`InvoiceValidationTest` 的 3 个查询相关红灯与 `MigrationSoftDeleteBehaviorTest#payInvoiceDeleteIsLogicalAndAllowsRecreate` 已收敛。

本轮归因为测试夹具缺少发票可解析的项目关系：种子 `pay_record` 仅写入 `pay_application_id`，但未写入 `project_id`，且测试库中没有对应付款申请或项目兜底记录，导致 `InvoiceService#create` 在真实业务校验 `INVOICE_PROJECT_MISSING / 发票缺少项目关系` 处 fail-close。

## 失败分类

| 用例 | 原失败 | 分类 | 处理 |
| --- | --- | --- | --- |
| `InvoiceValidationTest#shouldIgnoreTenantIdFromRequestBody` | 创建发票返回 `400 INVOICE_PROJECT_MISSING` | 测试夹具未补项目关系 | 补同租户 `pm_project`，并让种子 `pay_record.project_id` 指向该项目 |
| `InvoiceValidationTest#shouldFilterByInvoiceNoPartialMatch` | 查询前置创建发票返回 `400 INVOICE_PROJECT_MISSING` | 测试夹具未补项目关系 | 同上 |
| `InvoiceValidationTest#shouldFilterByVerifyStatus` | 查询前置创建发票返回 `400 INVOICE_PROJECT_MISSING` | 测试夹具未补项目关系 | 同上 |
| `MigrationSoftDeleteBehaviorTest#payInvoiceDeleteIsLogicalAndAllowsRecreate` | `invoiceService.create` 抛出 `发票缺少项目关系` | 测试夹具未补项目关系 | 补同租户 `pm_project`，并让种子 `pay_record.project_id` 指向该项目 |

## 业务口径

发票项目关系约束是真实业务口径，应保留。`InvoiceService` 当前会通过 `pay_record.project_id` 或付款申请项目解析发票项目；解析不到项目时返回 `INVOICE_PROJECT_MISSING`，避免发票绕过项目数据范围校验。

本轮没有把 `400` 改成测试期望，没有放宽租户、项目或状态校验，也没有新增或修改 Flyway migration。

## 修改范围

- `backend/src/test/java/com/cgcpms/invoice/InvoiceValidationTest.java`
  - 补充发票校验测试项目种子。
  - 补充 `pay_record.project_id`，让发票创建命中真实项目访问校验。
- `backend/src/test/java/com/cgcpms/MigrationSoftDeleteBehaviorTest.java`
  - 补充迁移软删测试项目种子。
  - 补充 `pay_record.project_id`，保留 invoice 软删与重建验证目标。

## 验收证据

```text
cd backend; .\mvnw.cmd "-Dtest=InvoiceValidationTest,MigrationSoftDeleteBehaviorTest" test
结果：BUILD SUCCESS，Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

```text
git diff --check
结果：通过
```

## 剩余风险

非阻塞：本轮只验证 Ready Issue 指定的两个测试类，未扩大到后端全量测试。
