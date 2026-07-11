# ISSUE-006-006 文件上传大小与 MIME/扩展名校验回归

完成日期：2026-07-09

## 结论

通过。

本轮回归文件上传安全边界：后端继续在 `FileService.upload` 入口读取文件字节后调用 `FileTypeValidator` 做大小、扩展名、客户端 MIME 与魔术字节联合校验；前端发票上传提示与后端 20MB 上限保持一致。不新增病毒扫描服务，不改变 MinIO / 对象存储配置。

## 修改范围

- `backend/src/main/java/com/cgcpms/file/service/FileTypeValidator.java`
  - 收紧 Office Open XML MIME 对应关系，`.docx` / `.xlsx` / `.pptx` 只接受对应的 Word / Excel / PowerPoint MIME 声明或通用 octet-stream 兜底，不再因为包含 `vnd.openxmlformats` 就跨类型放行。
- `backend/src/test/java/com/cgcpms/file/FileTypeValidatorTest.java`
  - 补充 DOCX 声明为 Excel MIME 的拒绝回归。
  - 补充 XLSX 合法文件回归。
  - 将超限边界测试口径对齐为 `>20MB`。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
  - 将上传服务超限回归口径对齐为 `>20MB`。
- `frontend-admin/src/pages/invoice/components/InvoiceFormModal.vue`
  - 发票 PDF 上传前端大小提示从 10MB 对齐为 20MB，避免误导用户。
- `frontend-admin/src/pages/invoice/__tests__/invoice-pdf.test.ts`
  - 补充 15MB PDF 可进入后续手动上传流程、21MB PDF 被拦截并提示 20MB 的前端回归。
  - 补充测试内 `vue-router` mock，修复该测试入口缺路由上下文的前置问题。
- `frontend-admin/src/pages/help/index.vue`
  - 帮助中心文件上传说明从 50MB 对齐为 20MB。

## 验收证据

- `cd backend; .\mvnw.cmd "-Dtest=FileTypeValidatorTest" test`：通过，16 个用例通过。
- `cd frontend-admin; pnpm vitest run src/pages/invoice/__tests__/invoice-pdf.test.ts`：通过，1 个文件 6 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，36 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 安全边界

- 大小：后端拒绝 `>20MB` 文件并返回 `FILE_TOO_LARGE`；前端发票上传对 21MB PDF 提示“文件大小不能超过20MB”，15MB PDF 不被前端误拦。
- 扩展名：非白名单扩展名如 `.exe`、`.sh`、`.bmp`、`.jar` 继续由后端拒绝。
- MIME / 魔术字节：PDF 声明为 PNG、PDF 扩展名但 PE/EXE 魔术字节、DOCX 声明为 Excel MIME 均由后端拒绝。
- 合法文件：PDF、JPEG、PNG、WebP、DOCX、XLSX 合法路径保持通过。
- 前端提示：发票上传弹窗和帮助中心均对齐后端 20MB 上限；前端校验只是交互提示，后端仍为最终拒绝边界。

## 失败分类或非失败分类

- 真实代码质量问题已修复：Office MIME 跨类型声明过宽、前端上传大小提示与后端不一致。
- 测试前置问题已更正：发票上传测试缺少 `vue-router` mock，已在测试内补齐。

## 剩余风险

- 本轮未新增病毒扫描能力，符合 Issue 禁止事项；恶意内容深度检测仍不在本轮范围。
- Office Open XML 仍按现有轻量魔术字节 / 内容标记识别，不做完整 ZIP 包解析；后续如要精确识别 OOXML 内部 content type，需要另立任务。
