# ISSUE-007-003 操作审计字段与文件操作审计回归

完成日期：2026-07-09

## 目标

- 回归上传、下载、删除等文件操作的审计类型和关键上下文字段。
- 补齐 user、tenant、path、status 等字段断言。
- 明确 trace 字段当前未进入操作审计模型的剩余风险。

## 修改范围

- `backend/src/main/java/com/cgcpms/file/controller/FileController.java`
  - 上传接口 `@AuditedOperation` 复用既有 SpEL 机制，补充 `businessIdExpression = "#businessId"`。
- `backend/src/test/java/com/cgcpms/audit/OperationAuditAspectTest.java`
  - 新增上传成功 `UPLOAD` 审计事件断言。
  - 新增下载成功 `DOWNLOAD` 审计事件断言。
  - 既有删除成功/失败 `DELETE` 审计事件断言继续覆盖。

## 验证记录

- Ready Issue 验证命令预检：`OperationAuditServiceTest`、`OperationAuditAspectTest`、`FileServiceTest` 均存在，无需等价替换。
- TDD 红灯验证：
  - `cd backend; .\mvnw.cmd "-Dtest=OperationAuditAspectTest#testFileUploadPublishesSuccessAuditEvent" test`
  - 结果：失败，`expected: <88001> but was: <null>`。
  - 失败分类：真实代码质量问题，上传审计事件未带业务对象 ID。
- 绿灯验证：
  - `cd backend; .\mvnw.cmd "-Dtest=OperationAuditAspectTest#testFileUploadPublishesSuccessAuditEvent" test`
  - 结果：通过，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- Ready Issue 指定验证：
  - `cd backend; .\mvnw.cmd "-Dtest=OperationAuditServiceTest,OperationAuditAspectTest,FileServiceTest" test`
  - 结果：通过，`Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`
  - 结果：通过。

## 审计字段覆盖

- 上传：`operationType=UPLOAD`、`businessType=FILE`、`businessId=业务对象 ID`、`tenantId`、`userId`、`httpMethod=POST`、`requestPath=/files/upload`、`successFlag=true`、`errorCode=null`。
- 下载：`operationType=DOWNLOAD`、`businessType=FILE`、`businessId=文件 ID`、`tenantId`、`userId`、`httpMethod=GET`、`requestPath=/files/{id}/url`、`successFlag=true`、`errorCode=null`。
- 删除成功：`operationType=DELETE`、`businessType=FILE`、`businessId=文件 ID`、`tenantId`、`userId`、`httpMethod=DELETE`、`requestPath=/files/{id}`、`successFlag=true`、`errorCode=null`。
- 删除拒绝：`operationType=DELETE`、`businessType=FILE`、`businessId=文件 ID`、`httpMethod=DELETE`、`requestPath=/files/{id}`、`successFlag=false`、`errorCode=BusinessException`。

## 自审结论

PASS。

依据：
- 文件上传、下载、删除的操作类型和业务对象信息均可通过审计事件追踪。
- 审计发布仍在切面 `finally` 块执行，异常路径记录失败事件；审计服务持久化异常仍被捕获，不放大为业务数据损坏。
- 本轮只复用既有 `AuditedOperation.businessIdExpression`，未引入新依赖或新审计架构。

## 结论

通过 / 非阻塞。

剩余风险：
- 当前 `OperationAuditEvent`、`OperationAuditLog`、`OperationAuditLogVO` 均无 `traceId` 字段；本轮禁止修改 migration，未扩展审计表结构。traceId 仍主要由访问日志链路承载，不属于当前操作审计持久化字段。
- 本轮未跑全量后端测试，结论限于 ISSUE-007-003 指定审计切面、审计服务和文件服务回归范围。
