# ISSUE-006-005 发票识别失败原因与人工确认口径回归

完成日期：2026-07-09

## 目标

回归发票 PDF 解析失败时的错误原因、主流程持续性和人工确认前不自动写入识别结果的边界。

## 修改范围

- `backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java`
  - PDFBox 解析失败不再抛出业务异常阻断识别调用，改为返回失败识别结果。
  - 加密 PDF 返回 `PDF_ENCRYPTED`；结构损坏或其他解析异常返回 `PDF_RECOGNIZE_FAILED`。
  - 成功和失败识别结果均标记 `manualConfirmationRequired=true`，识别结果只作为人工确认候选值。
- `backend/src/main/java/com/cgcpms/invoice/vo/InvoiceRecognizeResultVO.java`
  - 增加 `success`、`manualConfirmationRequired`、`errorCode`、`errorMessage`。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceServiceTest.java`
  - 补齐坏 PDF 返回失败原因后仍可创建发票的主流程断言。
  - 补齐识别动作不新增发票记录、人工确认仍由创建/核验链路控制的断言。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceRecognitionTest.java`
  - 将加密 PDF 和损坏 PDF 回归为失败 VO，不再要求抛异常。

## 安全边界

- 文件类型、扩展名、大小等入口安全校验仍返回业务异常，不放宽上传白名单。
- PDF 解析失败只影响识别候选值，不写入 `pay_invoice`。
- 发票正式字段仍只能通过创建/更新保存，并保持默认 `PENDING` 人工确认状态。

## 验证命令

- `cd backend; .\mvnw.cmd "-Dtest=InvoiceServiceTest,InvoiceRecognitionTest" test`：通过，`Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceServiceTest" test`：通过，`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

## 结论

通过 / 非阻塞。

发票识别失败原因、加密 PDF、损坏 PDF、失败后主流程继续、人工确认前不落库的关键边界已由后端回归测试覆盖。

## 剩余风险

- 本轮未修改前端，前端是否展示新增 `errorMessage` 依赖既有错误/结果展示逻辑；结论限于后端返回口径。
- 本轮未接入外部发票识别服务，仍沿用本地 PDFBox 文本解析。
- 本轮未跑全量后端测试，结论限于 ISSUE-006-005 指定 invoice 识别与服务回归范围。
