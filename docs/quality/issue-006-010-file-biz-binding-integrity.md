# ISSUE-006-010 文件业务对象绑定完整性回归

日期：2026-07-09

结论：通过

## 目标

- 回归文件与业务对象的绑定校验，确保孤儿附件、错误业务对象或越权绑定在后端被拒绝。
- 确保合法业务对象绑定路径不回退，既有下载、删除、鉴权接口保持可用。
- 前端失败提示与后端错误原因一致，不误报为成功或上传完成。
- 不修改权限模型，不修改 Flyway migration，不改变生产对象存储配置。

## 实现摘要

- `backend/src/main/java/com/cgcpms/file/service/FileService.java`
  - 抽出 `validateBusinessBindingParams`，统一校验 `businessType` 与 `businessId`。
  - 上传入口在业务对象写权限校验前拒绝空业务类型、空业务 ID 和非法业务类型格式。
  - 业务对象列表入口在查询文件和生成临时链接前拒绝非法绑定参数。
  - 控制器调用的 `checkBizReadPermission` 复用同一绑定参数校验，避免非法 `businessType` 进入 authorizer。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
  - 新增非法 `businessType` 上传先于 authorizer 被拒绝的断言。
  - 新增业务对象不存在、越权绑定时不写对象存储、不新增文件记录的断言。
  - 新增列表查询非法 `businessType` 先于 authorizer 被拒绝的断言。
  - 新增列表读权限失败时不生成临时下载链接的断言。
  - 清理每个测试前的 MockBean 初始化交互，避免 Spring 启动期 MinIO bucket 检查影响本轮交互断言。

## 业务对象绑定口径

- `businessType` 不能为空；为空返回 `FILE_PARAM_MISSING`，消息为 `业务类型不能为空`。
- `businessId` 不能为空；为空返回 `FILE_PARAM_MISSING`，消息为 `业务ID不能为空`。
- `businessType` 仅允许字母、数字、下划线和短横线；非法格式返回 `FILE_PARAM_INVALID`，消息为 `业务类型格式非法`。
- 上传路径先校验绑定参数，再执行 `BusinessObjectAuthorizer.checkWriteAccess`。
- 列表与显式读权限路径先校验绑定参数，再执行 `BusinessObjectAuthorizer.checkReadAccess`。
- 业务对象不存在沿用 authorizer 返回的 `FILE_BIZ_OBJ_NOT_FOUND`。
- 绑定对象越权或租户/项目关系不匹配沿用 authorizer 返回的 `FILE_ACCESS_DENIED`。
- 合法绑定不改变既有文件上传、hash 命名、重复判定、下载、删除和临时链接口径。

## 前端提示口径

- 本轮未修改前端代码。
- 前端请求层已透传后端 `BusinessException` 的 `message`，因此非法绑定、业务对象不存在、越权绑定会展示后端明确原因。
- 未引入仅前端校验；后端仍是强制拒绝边界。

## 安全边界

- 未修改 `backend/src/main/resources/db/migration/**`。
- 未修改 `deploy/**` 或生产对象存储配置。
- 未重构权限模型；业务对象存在性、租户与项目关系仍由既有 `BusinessObjectAuthorizer` 判定。
- 非法业务类型在 authorizer 和数据库查询前被拒绝，避免路径遍历式业务类型进入对象路径或列表查询。
- 业务对象不存在、越权绑定在对象存储写入前被拒绝，避免产生孤儿附件或错误绑定记录。
- 列表读权限失败在临时链接生成前被拒绝，避免未授权业务对象拿到文件 URL。

## 验证记录

- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testUploadRejectsInvalidBusinessTypeBeforeAuthorizer+testUploadRejectsMissingBusinessObjectBeforeSideEffects+testUploadRejectsUnauthorizedBusinessObjectBeforeSideEffects+testListByBusinessRejectsInvalidBusinessTypeBeforeAuthorizer+testListByBusinessRejectsReadDeniedBeforeTemporaryUrlGeneration" test`
  - RED：先失败；失败原因为上传非法 `businessType` 会先触发 authorizer，列表非法 `businessType` 未被拒绝。
  - GREEN：修复后通过，`5` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`
  - 通过，`46` 个用例通过。
- `cd frontend-admin; pnpm type-check`
  - 通过。
- `cd backend; .\mvnw.cmd test`
  - 未通过；失败类未命中本轮文件业务对象绑定目标测试，按既有无关后端测试红灯分类。
  - 失败类包括 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`PayRecordControllerTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest`、`WorkflowApproverResolverTest`、`WorkflowConcurrencyTest`、`WorkflowCoreServiceTest`、`WorkflowEngineIntegrationTest`、`WorkflowTemplateManagementTest`。
- `git diff --check`
  - 通过，仅有换行符转换提示。

## 失败分类或非失败分类

真实代码质量问题已修复；全量测试存在既有无关失败。

## 剩余风险

- 本轮不新增业务对象 schema 或文件绑定表；绑定完整性依赖既有 `businessType/businessId` 字段和 `BusinessObjectAuthorizer`。
- 本轮未扩展为通用附件中心，也未覆盖所有未来业务类型；新增业务类型仍需在 authorizer 中补充明确口径。
- 本轮未做真实 MinIO 集成验收，MinIO 交互由 MockBean 覆盖。
