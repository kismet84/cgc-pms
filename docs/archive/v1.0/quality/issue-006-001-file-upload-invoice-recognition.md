# ISSUE-006-001 文件上传白名单与发票识别失败兜底

日期：2026-07-08

## 结论

通过 / 非阻塞。

本轮沿用现有 `FileTypeValidator`，没有新增依赖、抽象、病毒扫描或外部存储配置。通用文件上传继续在 `FileService.upload` 入口做扩展名、MIME 与魔术字节校验；发票识别入口补齐 10MB 上限拒绝、真实文件名白名单校验和前端失败提示兜底。

## 校验矩阵

| 场景 | 入口 | 结果 |
| --- | --- | --- |
| 通用上传非白名单扩展名 | `FileTypeValidator` / `FileService.upload` | 返回 `FILE_TYPE_NOT_ALLOWED` |
| 通用上传 MIME 与魔术字节不匹配 | `FileTypeValidator` / `FileService.upload` | 返回 `FILE_TYPE_NOT_ALLOWED` |
| 通用上传超限文件 | `FileTypeValidator` / `FileService.upload` | 返回 `FILE_TOO_LARGE` |
| 发票识别非 PDF MIME | `InvoiceService.recognize` | 返回 `FILE_TYPE_NOT_ALLOWED`，提示仅支持 PDF |
| 发票识别 PDF 超过 10MB | `InvoiceService.recognize` | 返回 `FILE_TOO_LARGE`，提示不能超过 10MB |
| 发票识别 PDF 内容伪装为非 PDF 扩展名 | `InvoiceService.recognize` | 返回 `FILE_TYPE_NOT_ALLOWED` |
| PDFBox 识别失败 | `InvoiceService.recognize` / 发票弹窗 | 后端返回 `PDF_RECOGNIZE_FAILED`，前端展示“发票识别失败，请检查文件后重试”或后端错误文案，并复位 loading |

## 修改摘要

- `backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java`：发票识别读取文件内容后先拒绝超过 10MB 的 PDF，并使用原始文件名调用 `FileTypeValidator`。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceServiceTest.java`：新增发票识别超限 PDF、PDF 内容非 PDF 扩展名的回归测试。
- `frontend-admin/src/pages/invoice/components/InvoiceFormModal.vue`：前端上传前限制从 50MB 收敛到 10MB；非超时识别失败也显示可理解错误提示。

## 验证命令

- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest,InvoiceServiceTest" test`：通过，`Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `cd frontend-admin; pnpm type-check`：通过。
- `git diff --check`：通过。

## 剩余风险

- 本轮未引入病毒扫描、内容安全服务或外部存储策略调整，符合 Ready Issue 的明确非目标。
- 本轮未跑全量后端/前端测试，结论限于文件上传校验与发票识别失败兜底范围。
