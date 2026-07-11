# ISSUE-006-012 病毒扫描预留状态与失败兜底回归

日期：2026-07-09

结论：通过

## 目标

- 回归病毒扫描预留状态、错误码或扩展点口径，确保未接入真实查毒服务时行为明确。
- 不把“未扫描”“未接入能力”伪装为“已完成安全扫描”或“安全通过”。
- 合法上传主流程仍按既定策略工作，不引入误拦截或静默放行。
- 前端类型与后端返回字段一致，不使用“上传成功且已安全扫描”等误导性口径。

## 实现摘要

- `backend/src/main/java/com/cgcpms/file/vo/FileVirusScanStatus.java`
  - 新增病毒扫描预留状态枚举：`NOT_SCANNED`、`NOT_CONFIGURED`、`FAILED`。
  - 三个预留状态均为 `passed=false`，并提供稳定错误码：`VIRUS_SCAN_NOT_SCANNED`、`VIRUS_SCAN_NOT_CONFIGURED`、`VIRUS_SCAN_FAILED`。
- `backend/src/main/java/com/cgcpms/file/vo/SysFileVO.java`
  - 新增 `virusScanStatus`、`virusScanCode`、`virusScanMessage`、`virusScanPassed`。
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`
  - 上传与列表返回的 `SysFileVO` 默认附带 `NOT_CONFIGURED` 口径。
  - 不新增真实病毒扫描服务，不连接外部文件网关，不阻断合法上传。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
  - 新增合法上传返回病毒扫描预留状态的断言。
  - 新增预留状态均不代表安全检查通过的断言。
- `frontend-admin/src/types/file.ts`
  - 同步 `SysFileVO` 病毒扫描字段与前端状态枚举。
  - 前端枚举仅包含预留非通过状态，不暴露 `PASSED` 状态。
- `frontend-admin/src/types/__tests__/file.test.ts`
  - 回归前端状态枚举和类型字段，不出现“已安全扫描”误导文案。

## 病毒扫描预留/失败兜底口径

- `NOT_SCANNED`
  - `virusScanCode=VIRUS_SCAN_NOT_SCANNED`
  - `virusScanPassed=false`
  - 表示文件尚未完成病毒扫描。
- `NOT_CONFIGURED`
  - `virusScanCode=VIRUS_SCAN_NOT_CONFIGURED`
  - `virusScanPassed=false`
  - 当前默认返回状态，表示未接入病毒扫描能力。
- `FAILED`
  - `virusScanCode=VIRUS_SCAN_FAILED`
  - `virusScanPassed=false`
  - 作为未来扫描失败的稳定失败兜底口径。
- 当前未接入真实查毒服务时，合法文件上传仍成功落库与写入对象存储，但响应明确标记为 `NOT_CONFIGURED`，不会被误判为安全检查通过。

## 前端提示口径

- 本轮未新增可见成功提示。
- 前端 `SysFileVO` 类型已同步后端病毒扫描字段，调用方必须以 `virusScanStatus/virusScanPassed` 判断安全状态。
- 前端枚举不包含 `PASSED`，测试断言不出现“已安全扫描”误导文案。
- 既有上传失败提示保持后端错误优先，不用“上传成功且已安全扫描”替代后端状态。

## 安全边界

- 未修改 `backend/src/main/resources/db/migration/**`。
- 未修改 `deploy/**`、生产凭据、外部平台配置或生产对象存储配置。
- 未新增病毒扫描服务，未连接外部文件网关，未引入外部依赖。
- 未把未扫描、未接入能力或扫描失败伪装为安全通过。
- 未改变文件大小、MIME、扩展名、hash、重复文件、业务对象绑定或 MinIO 配置口径。

## 验证记录

- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testUploadReturnsVirusScanPlaceholderWithoutSafePass+testReservedVirusScanStatusesNeverPass" test`
  - RED：先失败；失败原因为缺少 `FileVirusScanStatus`、`SysFileVO` 病毒扫描字段，以及文案中仍包含“安全通过”误导词组。
  - GREEN：修复后通过，`2` 个用例通过。
- `cd frontend-admin; pnpm exec vitest run src/types/__tests__/file.test.ts`
  - RED：先失败；失败原因为前端缺少 `FILE_VIRUS_SCAN_STATUSES` 状态枚举。
  - GREEN：修复后通过，`1` 个文件、`2` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`
  - 通过，`48` 个用例通过。
- `cd frontend-admin; pnpm type-check`
  - 通过。
- `cd backend; .\mvnw.cmd test`
  - 未通过；Surefire 汇总：`156` 个报告，`1542` 个测试，`10` 个 failures，`30` 个 errors，`1` 个 skipped。
  - 失败类未命中本轮文件病毒扫描预留目标测试，按既有无关后端测试红灯分类。
  - 失败类包括 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`PayRecordControllerTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest`、`WorkflowApproverResolverTest`、`WorkflowConcurrencyTest`、`WorkflowCoreServiceTest`、`WorkflowEngineIntegrationTest`、`WorkflowTemplateManagementTest`。
- `git diff --check`
  - 通过。

## 失败分类或非失败分类

真实代码质量问题已修复；全量测试存在既有无关失败。

## 剩余风险

- 本轮只提供预留状态与失败兜底口径，不提供真实病毒扫描能力。
- 当前未新增 `sys_file` 扫描状态持久化字段；上传和列表响应统一返回 `NOT_CONFIGURED`。如后续接入真实查毒服务并需要逐文件持久化状态，需要另立 migration 任务确认。
- 本轮未做真实 MinIO 集成验收，文件存储交互由 MockBean 覆盖。
