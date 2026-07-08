# Iteration Report - 2026-07-09

Issue：ISSUE-005-007 列表页导出与批量操作权限态回归

目标：
- 回归核心列表页导出按钮、批量操作按钮和权限态展示。
- 不新增导出后端能力，不改变权限模型；不通过前端按钮隐藏替代后端权限校验。

修改范围摘要：
- `frontend-admin/src/pages/alert/index.vue`：新增预警列表管理/导出权限计算态，并在批量、行级写操作和导出函数入口加前端权限态 guard。
- `frontend-admin/src/pages/alert/components/AlertTablePanel.vue`：按权限态控制批量处理、标记已读、归档、导出和行级写操作入口展示。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`：补充无编辑/导出权限时入口隐藏的回归测试。
- `docs/quality/issue-005-007-list-export-batch-permission.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-005-007 收口为 Done，并在 Ready 队列为空后拆出 5 个新 Ready Issue。

验证命令摘要：
- `cd frontend-admin; pnpm test:unit src/pages/alert/__tests__/index.test.ts -- --runInBand`：通过，`1` 个文件、`12` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：非失败分类
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未做真实浏览器验收，结论基于指定 Vitest、类型检查、构建和代码审查。
- 本轮只覆盖实际存在导出/批量入口的预警列表权限态；未新增导出后端能力。

---

Backlog 拆解：Ready 队列补充

来源：
- `docs/backlog/current-focus.md`
- `docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3`、`7.7 P1-4`

新增 Ready Issue：
- `ISSUE-006-006`：文件上传大小与 MIME/扩展名校验回归。
- `ISSUE-006-007`：私有桶默认策略与公开 URL 禁用回归。
- `ISSUE-006-008`：文件下载临时链接过期与鉴权失败提示回归。
- `ISSUE-007-009`：JVM 与数据库连接池指标回归。
- `ISSUE-007-010`：备份清单脱敏与恢复演练报告模板回归。

拆解边界：
- 本轮只更新 backlog，不继续执行新拆出的业务任务。
- 新任务均禁止修改已应用 Flyway migration、生产凭据、外部平台配置和生产部署配置。

---

Issue：ISSUE-006-006 文件上传大小与 MIME/扩展名校验回归

目标：
- 回归文件大小、MIME、扩展名三类上传限制，确保非法文件在后端被拒绝。
- 对齐前端上传大小提示，避免前端提示与后端拒绝原因不一致。
- 不新增病毒扫描服务，不改变生产对象存储配置。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/service/FileTypeValidator.java`：收紧 Office Open XML MIME 对应关系，拒绝 DOCX 声明为 Excel/PPT 等跨类型 MIME。
- `backend/src/test/java/com/cgcpms/file/FileTypeValidatorTest.java`：补充 Office MIME 错配拒绝、XLSX 合法文件、20MB 超限边界回归。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：上传服务超限边界测试口径对齐为 `>20MB`。
- `frontend-admin/src/pages/invoice/components/InvoiceFormModal.vue`：发票 PDF 上传大小提示对齐后端 20MB。
- `frontend-admin/src/pages/invoice/__tests__/invoice-pdf.test.ts`：补充 15MB 可通过、21MB 被拦截的前端回归，并补齐测试路由 mock。
- `frontend-admin/src/pages/help/index.vue`：帮助中心上传说明从 50MB 对齐为 20MB。
- `docs/quality/issue-006-006-file-upload-validation.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-006 收口为 Done，Ready 队列推进到 ISSUE-006-007。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileTypeValidatorTest" test`：通过，`16` 个用例通过。
- `cd frontend-admin; pnpm vitest run src/pages/invoice/__tests__/invoice-pdf.test.ts`：通过，`1` 个文件、`6` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，`36` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；测试前置问题已更正
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未新增病毒扫描能力，符合 Issue 禁止事项；恶意内容深度检测仍不在本轮范围。
- Office Open XML 仍按现有轻量魔术字节 / 内容标记识别，不做完整 ZIP 包解析；如需更精确内部 content type 识别，需另立任务。

---

Issue：ISSUE-006-007 私有桶默认策略与公开 URL 禁用回归

目标：
- 回归文件访问必须经鉴权接口或临时链接。
- 禁止服务层透传公开桶直链或永久 URL。
- 验证未授权下载被拒、合法授权下载不回退。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`：在 `genPresignedUrl` 统一出口校验返回 URL 必须包含 `X-Amz-Signature=`，未签名 URL 转为 `FILE_URL_ERROR`。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：新增未签名公开桶 URL 拒绝回归，并补齐上传成功测试的签名临时链接 mock。
- `docs/quality/issue-006-007-private-bucket-public-url-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-007 收口为 Done，Ready 队列推进到 ISSUE-006-008。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testGetPresignedUrlRejectsUnsignedPublicUrl" test`：先失败，原因是未签名公开桶 URL 被原样透传。
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testGetPresignedUrlRejectsUnsignedPublicUrl" test`：修复后通过，`1` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，`37` 个用例通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；测试夹具问题已更正
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮不修改 MinIO 桶策略和生产对象存储配置；真实桶私有策略仍需由部署环境配置保证。
- 当前兜底以 MinIO/S3 预签名 URL 的 `X-Amz-Signature` 参数作为临时链接判据；如未来切换非 S3 签名方案，需要同步调整判据。
