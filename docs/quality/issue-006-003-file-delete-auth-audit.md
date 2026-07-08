# ISSUE-006-003 附件删除鉴权与审计回归

完成日期：2026-07-09

## 目标

回归附件删除前的租户边界、业务对象写权限校验、MinIO 删除副作用和 DELETE 审计事件，确保未授权删除不会触发对象存储删除。

## 修改范围

- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
  - 新增跨租户附件删除拒绝断言，确认返回 `FILE_NOT_FOUND`、不进入业务对象写权限校验、不调用 MinIO `removeObject`，且原始记录仍为未删除。
  - 新增业务对象写权限拒绝断言，确认返回 `FILE_ACCESS_DENIED` 且不调用 MinIO `removeObject`。
  - 新增授权删除成功断言，捕获 `RemoveObjectArgs`，确认 bucket/object 正确，随后 `sys_file` 记录被逻辑删除。
- `backend/src/test/java/com/cgcpms/audit/OperationAuditAspectTest.java`
  - 新增 `DELETE /files/{id}` 成功审计断言，覆盖 `operationType=DELETE`、`businessType=FILE`、`businessId`、HTTP method、path、tenant/user 与 success。
  - 新增删除拒绝审计断言，覆盖 `successFlag=false` 与 `errorCode=BusinessException`。

本轮未修改后端生产代码、前端、migration、deploy 或外部对象存储配置。

## 安全边界

- 跨租户删除：当前租户无法通过其他租户附件 ID 删除文件，对外返回 `FILE_NOT_FOUND`。
- 无写权限删除：`BusinessObjectAuthorizer.checkWriteAccess` 拒绝后不调用 MinIO。
- 删除副作用顺序：授权通过后先调用 MinIO `removeObject`，再执行 `sys_file` 逻辑删除。
- 审计成功路径：附件删除成功会发布 DELETE / FILE 审计事件并记录业务 ID。
- 审计失败路径：附件删除业务拒绝会发布失败审计事件，记录 `BusinessException`。

## 验证命令

- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest,BusinessObjectAuthorizerTest,OperationAuditAspectTest" test`
  - 首轮：审计测试打开 `minio.enabled=true` 但缺少测试 MinIO endpoint，归类为测试配置问题。
  - 第二轮：方法级 `@PreAuthorize` 未设置测试认证、跨租户存在性断言被租户插件过滤，归类为测试夹具问题。
  - 修正后：通过，`Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

## 结论

通过 / 非阻塞。

现有生产删除链路已满足本 Issue 的核心安全边界，本轮以最小测试补强完成回归闭环。

## 剩余风险

- 本轮未跑全量后端测试，结论限于 ISSUE-006-003 指定 file 模块、业务对象授权和审计切面范围。
- 本轮未连接真实 MinIO，删除副作用通过 `MinioClient.removeObject` 参数捕获验证。
