# ISSUE-006-009 上传文件 hash 生成与重复文件口径回归

日期：2026-07-09

结论：通过

## 目标

- 回归上传链路中的文件 hash 生成、持久化与重复文件判定口径。
- 确保同内容文件不会绕过既有审计与业务绑定约束。
- 确保合法非重复文件上传不回退。
- 不新增病毒扫描服务，不改变生产对象存储配置，不修改 Flyway migration。

## 实现摘要

- `backend/src/main/java/com/cgcpms/file/service/FileService.java`
  - 上传文件通过 `SHA-256` 内容摘要生成稳定 hash。
  - 存储文件名改为 `{sha256}.{extension}`，并通过既有 `file_name` 与 `storage_path` 字段持久化 hash 结果，不新增数据库字段。
  - 重复判定范围为当前租户、同一 `businessType`、同一 `businessId`、同一 hash 文件名。
  - 重复文件在对象存储写入前拒绝，错误码为 `FILE_DUPLICATE`，消息为 `文件已存在，请勿重复上传`。
  - 重复检查发生在业务对象写权限校验之后，避免绕过业务绑定和授权约束。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
  - 新增 SHA-256 文件名与 storagePath 持久化断言。
  - 新增同业务对象重复内容拒绝断言，确认第二次上传不再调用 MinIO `putObject`。
  - 新增同业务对象不同内容可正常上传断言。

## hash 与重复文件口径

- hash 算法：`SHA-256`。
- 持久化方式：不新增 schema；hash 作为 `sys_file.file_name` 的主体，并自然进入 `sys_file.storage_path`。
- 文件名格式：`64位小写十六进制SHA-256 + 原始校验后扩展名`，例如 `{sha256}.pdf`。
- 重复范围：同租户 + 同业务类型 + 同业务 ID + 同 hash 文件名。
- 重复结果：拒绝上传，返回 `FILE_DUPLICATE`，不写对象存储，不新增 `sys_file` 记录。
- 合法非重复：同业务对象下不同内容生成不同 hash，可新增多条文件记录。

## 安全边界

- 未修改 `backend/src/main/resources/db/migration/**`，未新增数据库字段。
- 未新增病毒扫描服务。
- 未修改生产对象存储配置。
- 业务对象写权限校验仍先于重复判定和对象存储写入执行。
- 原始文件名仅作为 `original_name` 保留；实际对象路径使用 hash 文件名，降低客户端文件名影响存储路径的风险。
- 重复拒绝通过既有 `BusinessException` 进入审计/异常处理链路，文件上传失败指标也会记录 `FILE_DUPLICATE`。

## 验证记录

- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testUploadStoresStableContentHashInFileNameAndStoragePath+testUploadRejectsDuplicateContentForSameBusinessObject+testUploadAllowsDifferentContentForSameBusinessObject" test`
  - RED：先失败，失败原因为既有实现使用 UUID 文件名且重复内容未拦截。
  - GREEN：修复后通过，`3` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest" test`
  - 通过，`25` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`
  - 通过，`41` 个用例通过。
- `cd frontend-admin; pnpm type-check`
  - 通过。
- `cd backend; .\mvnw.cmd test`
  - 未通过；失败类未命中本轮文件上传目标测试，按既有无关后端测试红灯分类。
  - 失败类包括 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`PayRecordControllerTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest`、`WorkflowApproverResolverTest`、`WorkflowConcurrencyTest`、`WorkflowCoreServiceTest`、`WorkflowEngineIntegrationTest`、`WorkflowTemplateManagementTest`。
- `git diff --check`
  - 通过，仅有换行符转换提示。

## 失败分类或非失败分类

真实代码质量问题已修复；全量测试存在既有无关失败。

## 剩余风险

- 当前方案在不改 schema 前提下通过 `file_name/storage_path` 持久化 hash；如后续需要独立 `file_hash` 字段、索引或跨业务对象去重，需要单独拆 migration 任务确认。
- 重复判定范围限定为同租户同业务对象，不做跨业务对象、跨租户或全桶级去重。
- 本轮未做真实对象存储集成验收，MinIO 交互仍由测试 mock 覆盖。
