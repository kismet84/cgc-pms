# ISSUE-006-008 文件下载临时链接过期与鉴权失败提示回归

日期：2026-07-09
结论：通过
阻塞：非阻塞

## 范围

- 后端文件下载临时链接生成与鉴权失败路径。
- 前端文件下载 URL 请求失败提示。
- 不新增外部文件网关，不改变权限模型，不修改生产对象存储配置。

## 变更摘要

- `backend/src/main/java/com/cgcpms/file/service/FileService.java`：预签名 URL 兜底校验从仅要求 `X-Amz-Signature` 收紧为同时要求 `X-Amz-Expires=300`，确保返回链接具备明确 5 分钟过期时间。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：新增“有签名但缺少明确过期参数”拒绝回归，并补齐合法临时链接夹具的 `X-Amz-Expires=300`。
- `frontend-admin/src/api/modules/file.ts`、`frontend-admin/src/api/request.ts`：文件下载 URL 请求携带专用失败提示，API 拦截器优先展示该提示。
- `frontend-admin/src/api/__tests__/request.test.ts`、`frontend-admin/src/api/modules/__tests__/system-modules.test.ts`：补充前端下载失败提示配置与展示回归。

## 验收证据

- RED：`cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testGetPresignedUrlRejectsSignedUrlWithoutExplicitExpiry" test` 初次失败，当前实现接受了缺少 `X-Amz-Expires=300` 的签名 URL。
- RED：`cd frontend-admin; pnpm exec vitest run src/api/modules/__tests__/system-modules.test.ts src/api/__tests__/request.test.ts` 初次失败，文件下载 URL 请求未携带专用失败提示，拦截器展示后端原始消息。
- GREEN：上述后端单测修复后通过，`1` 个用例通过。
- GREEN：上述前端目标测试修复后通过，`2` 个文件、`10` 个用例通过。
- Ready Issue 指定验证：`cd backend; .\mvnw.cmd "-Dtest=*File*" test` 通过，`38` 个用例通过。
- Ready Issue 指定验证：`cd frontend-admin; pnpm type-check` 通过。
- Ready Issue 指定验证：`cd frontend-admin; pnpm build` 通过。
- `git diff --check`：通过。

## 安全边界

- 临时链接过期：服务层要求 MinIO/S3 预签名 URL 同时包含 `X-Amz-Signature` 与 `X-Amz-Expires=300`。
- 鉴权失败：跨租户文件仍隐藏为 `FILE_NOT_FOUND`；业务对象读权限失败仍返回 `FILE_ACCESS_DENIED`，且不会触发 MinIO 取链接。
- 过期/异常链接：缺少明确 5 分钟过期参数的签名 URL 转为 `FILE_URL_ERROR`，前端展示“文件下载失败，请确认权限或链接是否已过期”。
- 合法下载不回退：合法授权下载仍使用 `GET`、`300` 秒过期、文本附件响应头；上传成功后的临时链接回写仍通过。

## 剩余风险

- 本轮不修改真实 MinIO 桶策略、生产对象存储配置或外部网关。
- 当前过期判据绑定 MinIO/S3 的 `X-Amz-Expires=300` 参数；如未来切换非 S3 签名方案，需要同步调整判据与测试。
