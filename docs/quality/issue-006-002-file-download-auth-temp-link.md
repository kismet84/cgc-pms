# ISSUE-006-002 附件下载鉴权与临时链接回归

完成日期：2026-07-09

## 目标

回归附件下载鉴权、业务对象读权限校验与临时下载链接生成口径，补齐未授权读取、跨业务对象读取、文本附件下载头等安全边界断言。

## 修改范围

- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
  - 新增跨租户文件 ID 获取临时链接时返回 `FILE_NOT_FOUND` 的断言。
  - 新增业务对象读权限拒绝时不调用 MinIO 临时链接生成的断言。
  - 新增文本附件临时链接 `GET`、5 分钟过期、`text/plain; charset=utf-8` 与 `attachment` 下载头断言。
- `backend/src/test/java/com/cgcpms/file/BusinessObjectAuthorizerTest.java`
  - 新增跨租户合同对象先拒绝、且不进入项目权限检查的断言。

本轮未修改生产代码、数据库 migration、前端或外部对象存储配置。

## 安全边界

- 未授权读取：`BusinessObjectAuthorizer` 抛出 `FILE_ACCESS_DENIED` 后，`FileService.getPresignedUrl` 不调用 MinIO。
- 跨租户文件读取：当前租户查不到其他租户 `sys_file` 记录，对外表现为 `FILE_NOT_FOUND`。
- 跨业务对象读取：业务对象租户不一致时直接拒绝，不继续做项目权限检查。
- 文本附件下载头：文本附件临时链接保留 `response-content-type=text/plain; charset=utf-8` 与 `response-content-disposition=attachment; filename="..."`。
- 临时链接策略：断言 `GET` 方法与 5 分钟过期时间，沿用现有 `PRESIGNED_URL_EXPIRE_MINUTES` 口径。

## 验证命令

- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest,BusinessObjectAuthorizerTest" test`
  - 首轮：测试代码使用不存在的 `TestUserContext.TENANT_1`，编译失败，归类为测试编写问题，不是业务代码失败。
  - 修正后：通过，`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

## 结论

通过 / 非阻塞。

现有生产下载鉴权与临时链接实现已满足本 Issue 的核心安全边界，本轮以最小测试补强完成回归闭环。

## 剩余风险

- 未运行全量后端测试，结论限于 ISSUE-006-002 指定的 file 模块与业务对象授权测试范围。
- 未做真实 MinIO 集成下载，临时链接参数通过 `MinioClient.getPresignedObjectUrl` 参数捕获验证。
