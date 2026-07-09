# ISSUE-008-015：报表中心平台化缺口-M6：导出审计留痕与目录一致性回归

## 结论

- 结论：通过
- 阻塞：无
- 失败分类或非失败分类：真实代码质量问题已修复；`alert-center` 导出补齐最小审计留痕，目录“支持导出”口径继续与真实可导出能力保持一致；D 最终验收与 E 最终复审通过
- 是否自动合并：否
- 是否推送：否

## 本轮范围

- 后端白名单补修：
  - `backend/src/main/java/com/cgcpms/alert/controller/AlertController.java`
  - `backend/src/main/java/com/cgcpms/alert/dto/AlertExportAuditRequest.java`
  - `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
- 前端白名单补修：
  - `frontend-admin/src/api/modules/alert.ts`
  - `frontend-admin/src/pages/alert/index.vue`
  - `frontend-admin/src/pages/alert/__tests__/index.test.ts`

## 实现摘要

### 后端

- 新增 `POST /alerts/export-audit`，复用现有 `@AuditedOperation` 能力，以 `DOWNLOAD / ALERT_EXPORT` 记最小导出审计。
- 新增 `AlertExportAuditRequest` DTO，限制：
  - `filterSignature` 必须匹配 `^alert-export-[a-f0-9]{1,19}$`
  - 长度不超过 `32`
  - `recordCount` 必须为非负整数
- `AlertControllerTest` 补三类回归：
  - 反射校验接口审计注解与权限注解；
  - 合法十六进制签名写入成功审计日志；
  - 非白名单签名 `400`，且不新增成功审计日志。

### 前端

- 导出后新增一次非阻断审计确认：下载成功后调用 `/alerts/export-audit`。
- 使用当前筛选条件构造规范化字符串，再生成 `alert-export-<hex>` 十六进制签名：
  - 仅记录字段存在性与筛选语义；
  - `keyword` 只记录 `present/empty`，不直接带原始关键字；
  - 不把导出消息文本、原始筛选串或业务敏感值直接写入签名。
- 审计确认失败时，保留导出成功结果，只追加用户提示 `导出已完成，审计确认补记失败`。

## 验收证据

- D 最终验收：通过
- E 最终复审：通过
- 后端关键证据：
  - `AlertController` 新增 `/alerts/export-audit`，审计类型为 `DOWNLOAD`，业务类型为 `ALERT_EXPORT`，`businessId` 取 `#request.filterSignature`
  - DTO 白名单约束限制签名只能是 `alert-export-<hex>`
  - `AlertControllerTest` 校验成功落库与非法签名拒绝
- 前端关键证据：
  - `alert/index.vue` 使用 `buildAlertExportFilterSignature` 生成十六进制签名
  - `alert/__tests__/index.test.ts` 断言签名匹配 `^alert-export-[a-f0-9]{1,19}$`
  - 同测试断言签名不包含 `关键字`、`首条导出消息`
  - 同测试断言审计确认失败时仍完成下载并显示非阻断 warning
- 收口门禁：
  - `git diff --check`：本轮执行并通过
  - AutoPilot flag：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`

## 风险与边界

- 本轮只补齐同步导出的最小审计留痕，不扩展为异步导出、导出文件存储、任务中心或字段级权限模型。
- 审计确认目前是下载后的补记动作；若后端审计接口瞬时失败，用户侧导出仍成功，只留下 warning。这是已接受的非阻断剩余风险，不影响本轮通过结论。
- 目录“支持导出”仍以当前真实可导出入口为准；本轮不覆盖导出内容审批、下载追踪报表或审计检索界面。
