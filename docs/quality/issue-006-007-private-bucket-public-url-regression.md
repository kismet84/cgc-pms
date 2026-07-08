# ISSUE-006-007 私有桶默认策略与公开 URL 禁用回归

完成日期：2026-07-09

## 结论

通过。

本轮回归文件访问安全边界：文件下载仍必须先经过 `FileService.getPresignedUrl` 的租户隔离与业务对象读权限校验，再由 MinIO SDK 生成带签名参数的临时链接；服务层新增未签名 URL 兜底拒绝，避免误把公开桶直链或永久 URL 透传给前端。不修改生产 MinIO 配置，不连接生产对象存储。

## 修改范围

- `backend/src/main/java/com/cgcpms/file/service/FileService.java`
  - 在 `genPresignedUrl` 统一出口校验返回 URL 必须包含 `X-Amz-Signature=`。
  - 未签名 URL 统一转为 `FILE_URL_ERROR`，不向调用方暴露公开桶直链。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
  - 新增未签名公开桶 URL 拒绝回归。
  - 补齐上传成功测试的签名临时链接 mock，保持合法上传路径不回退。

## 验收证据

- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testGetPresignedUrlRejectsUnsignedPublicUrl" test`：先失败，原因是未签名公开桶 URL 被原样透传。
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testGetPresignedUrlRejectsUnsignedPublicUrl" test`：修复后通过，1 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，37 个用例通过。
- `git diff --check`：通过。

## 安全边界

- 公开 URL 禁用：`http://minio.local/test-bucket/CONTRACT/30007/public.pdf` 这类不含签名参数的桶直链被拒绝，不返回给调用方。
- 临时链接：合法路径继续通过 `minioClient.getPresignedObjectUrl` 生成 `Method.GET`、5 分钟过期的签名 URL。
- 未授权下载：跨租户文件在读权限检查前返回 `FILE_NOT_FOUND`，业务对象读权限拒绝返回 `FILE_ACCESS_DENIED`，且均不调用 MinIO。
- 合法授权下载：已有文本下载回归继续验证 `X-Amz-Signature`、5 分钟 expiry 与附件响应头，上传成功路径补齐签名 URL mock 后不回退。

## 失败分类或非失败分类

- 真实代码质量问题已修复：服务层此前未兜底校验 MinIO 返回值是否为签名临时链接。
- 测试夹具问题已更正：上传成功用例此前未模拟签名下载链接，新增出口校验后补齐 mock。

## 剩余风险

- 本轮不修改 MinIO 桶策略和生产对象存储配置；真实桶私有策略仍需由部署环境配置保证。
- 当前兜底以 MinIO/S3 预签名 URL 的 `X-Amz-Signature` 参数作为临时链接判据；如未来切换非 S3 签名方案，需要同步调整判据。
