# 全量审计：API 与后端

## 结论

**通过。评分 86/100。** API 规模大但横切控制一致；本地全量 `verify` 通过。

## 证据

- 图谱识别 773 条路由；76 个 Controller。
- `ApiResponse.success`、`UserContext.getCurrentTenantId`、`ProjectAccessChecker.checkAccess` 等高复用点形成统一响应、租户与项目访问层。
- `PayApplicationService` 在查询前合并租户、项目与可访问项目集合；无权集合失败关闭。
- `PaymentDocumentDataProvider` 只允许审批中/已通过付款事实，脱敏银行与联系方式，附件仍走授权链。
- 工作流路由按租户、业务类型与优先级选择；同优先级歧义失败关闭。
- 本地 `backend\mvnw.cmd -C verify`：249 suites、2049 tests、0 failure、0 error、3 skipped。

## 风险

- `CODE-001`（P3）：`PayApplicationService` 707 行，查询、写入与组装职责偏集中。
- `CODE-002`（P3）：构建出现 OpenHTMLToPDF 废弃 API 与 Spring `@MockBean` 废弃告警，当前不影响运行但需计划升级。
