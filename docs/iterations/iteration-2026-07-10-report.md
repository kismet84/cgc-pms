# Iteration Report - 2026-07-10

---

Issue：ISSUE-008-011 报表中心平台化缺口-M2：目录权限过滤与入口一致性回归

目标：
- 在实现与回归通过后，完成 `ISSUE-008-011` 的正式归档、backlog 状态同步与本地提交收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并补正式质量报告。
- 严格隔离 `AGENTS.override.md`、`plugins/cgc-pms-autopilot/**` 和其他非本 Issue 脏改动，不做 push。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`：补强非管理员目录过滤断言与空权限码可见性断言。
- `frontend-admin/src/pages/report/catalog.vue`：复用 `canOpenReportCatalogPage`，避免目录页对 `API-only` 项伪跳转。
- `frontend-admin/src/pages/report/catalog-entry.ts`：新增最小公共判定函数。
- `frontend-admin/src/pages/report/__tests__/catalog-entry.test.ts`：新增页面入口一致性测试。
- `docs/quality/issue-008-011-report-center-permission-target-consistency.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 同步。
- `docs/iterations/iteration-2026-07-10-report.md`：记录本次收口动作。

验证命令摘要：
- `git branch --show-current`：`master`。
- `git status --short`：确认存在非本 Issue 脏改动；本轮只处理白名单文件。
- `cd backend; .\mvnw.cmd "-Dtest=ReportCatalogServiceTest" test`：通过，`2` 个用例通过。
- `cd frontend-admin; pnpm exec vitest run src/pages/report/__tests__/catalog-entry.test.ts`：通过，`3` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；目录权限过滤与入口一致性回归已补齐；本轮完成正式归档与本地提交收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前 Ready 队列已空，后续报表中心平台化推进仍需主线程重新拆题。
- 工作区保留与本 Issue 无关的未提交改动，本轮未处理也未提交。

---

Issue：ISSUE-008-012 报表中心平台化缺口-M3：导出能力映射与元数据一致性回归

目标：
- 在 D 最终验收与 E 复审通过后，完成 `ISSUE-008-012` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确当前 Ready 已空、待下一轮拆题。
- 严格隔离白名单外脏改动，不修改后端生产服务文件，不做 push。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`：补强导出能力元数据断言，确认 `alert-center` 的导出声明与权限过滤一致。
- `frontend-admin/src/pages/report/catalog-entry.ts`：新增导出入口真实判定函数 `hasReportCatalogExportEntry`。
- `frontend-admin/src/pages/report/catalog.vue`：目录导出状态改为基于真实入口展示“支持导出 / 无导出入口”。
- `frontend-admin/src/pages/report/__tests__/catalog-entry.test.ts`：新增导出入口守卫回归测试。
- `docs/quality/issue-008-012-report-center-export-metadata-consistency.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 与当前焦点同步。
- `docs/iterations/iteration-2026-07-10-report.md`：追加本次收口动作。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 1 个实施型 Ready Issue。

验证命令摘要：
- D 最终验收：已通过 `ReportCatalogServiceTest`、`catalog-entry.test.ts`、`pnpm type-check`、`pnpm build`。
- E 复审：已通过。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- `git diff --check`：通过。
- AutoPilot flag 核对：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`。

失败分类或非失败分类：真实代码质量问题已修复；原 P1 阻塞已关闭；导出能力映射与元数据一致性已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前 Ready 队列已空；下一轮是否继续拆报表中心或切换其他平台化题，仍需主线程裁决。
- 本轮只确保目录元数据与真实导出入口一致，不等于已覆盖导出结果内容、性能或异步导出需求。

---

Issue：ISSUE-008-013 报表中心平台化缺口-M4：导出入口与筛选快照一致性回归

目标：
- 在 D 验收与 E 审查通过后，完成 `ISSUE-008-013` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确当前 Ready 已空、待下一轮拆题。
- 严格隔离白名单外脏改动，不做 push，不改 AutoPilot flag 文件。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/report/service/ReportCatalogService.java`：为 `alert` 目录项补真实访问范围判定，避免无项目访问范围用户继续看到预警导出入口。
- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`：补强无预警访问范围时目录不可见断言。
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`：新增同租户无项目访问范围成员访问 `/alerts/processing-report` 被拒绝断言。
- `frontend-admin/src/pages/alert/components/AlertTablePanel.vue`：空列表时禁用导出按钮。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`：补导出仅使用当前列表快照、空列表禁用和 CSV 内容回归。
- `docs/quality/issue-008-013-report-center-export-filter-snapshot.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 与当前焦点同步。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 2 个实施型 Ready Issue。

验证命令摘要：
- D 验收：已通过 `ReportCatalogServiceTest, AlertControllerTest`、`alert/index.test.ts`、`pnpm type-check`、`pnpm build`。
- E 审查：已通过。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- `git diff --check`：通过。
- AutoPilot flag 核对：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`。

失败分类或非失败分类：真实代码质量问题已修复；导出入口、筛选快照与权限边界一致性已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前 Ready 队列已空；下一轮仍需主线程/A 基于长期任务池与阻塞现状重新拆题。
- 本轮只覆盖 `alert-center` 当前列表快照导出，不覆盖大数据量导出、异步导出或导出审计链路。

---

Issue：ISSUE-008-014 报表中心平台化缺口-M5：跨页全量导出与筛选语义一致性回归

目标：
- 在 D 最终验收与 E 最终复审通过后，完成 `ISSUE-008-014` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确当前 Ready 已空、待主线程下一轮拆题。
- 严格隔离白名单外脏改动，不做 push，不改 AutoPilot flag 文件。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`：补强跨页项目筛选与域权限断言，确认不同分页参数下仍不越过项目访问范围，也不会混入无权限域数据。
- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`：补强无预警权限场景下目录入口不可见断言。
- `frontend-admin/src/pages/alert/index.vue`：复用统一筛选参数构造，导出改为按当前筛选条件抓取跨页全量结果，并加入 `1000` 条阈值保护。
- `frontend-admin/src/pages/alert/components/AlertTablePanel.vue`：导出按钮禁用态改为复用列表加载、导出加载与总条数判断。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`：新增跨页导出、总量超阈值和第一页真实 total 超阈值回归。
- `docs/quality/issue-008-014-report-center-cross-page-export-filter-consistency.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 与当前焦点同步。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 `3/10` 个实施型 Ready Issue。

验证命令摘要：
- D 最终验收：已通过 `AlertControllerTest,ReportCatalogServiceTest`、`pnpm exec vitest run src/pages/alert/__tests__/index.test.ts`、`pnpm type-check`。
- E 最终复审：已通过。
- Ready 前端命令替换：原 `pnpm test:unit -- src/pages/alert/__tests__/index.test.ts src/pages/report/__tests__/catalog-entry.test.ts` 采信为等价的 `pnpm exec vitest run src/pages/alert/__tests__/index.test.ts`，按工具配置类记录，不计为业务代码失败。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- `git diff --check`：通过。
- AutoPilot flag 核对：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`。

失败分类或非失败分类：真实代码质量问题已修复；原 P1/P2 阻塞已关闭；跨页全量导出与筛选语义一致性已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前 Ready 队列已空；下一轮是否继续报表中心或切换其他平台化题，仍需主线程/A 基于长期任务池重新裁决。
- 本轮只覆盖同步跨页全量导出的一致性，不覆盖异步导出、导出审计或超大数据量导出能力。

---

Issue：ISSUE-008-015 报表中心平台化缺口-M6：导出审计留痕与目录一致性回归

目标：
- 在 D 最终验收与 E 最终复审通过后，完成 `ISSUE-008-015` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确当前 Ready 已空、待主线程下一轮拆题。
- 严格隔离白名单外脏改动，不做 push，不改 AutoPilot flag 文件。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/alert/controller/AlertController.java`：新增 `/alerts/export-audit`，复用现有 `@AuditedOperation` 记录最小导出审计。
- `backend/src/main/java/com/cgcpms/alert/dto/AlertExportAuditRequest.java`：新增 DTO，限制签名必须为 `alert-export-<hex>` 且记录数非负。
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`：补接口契约、成功落库、非法签名拒绝三类回归。
- `frontend-admin/src/api/modules/alert.ts`：新增 `exportAlertAudit` API。
- `frontend-admin/src/pages/alert/index.vue`：导出成功后补发审计确认，使用十六进制筛选签名，失败只给非阻断提示。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`：补签名格式、敏感文本不入签名、审计补记失败不阻断下载回归。
- `docs/quality/issue-008-015-report-center-export-audit-trail.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 与当前焦点同步。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 `4/10` 个实施型 Ready Issue。

验证命令摘要：
- D 最终验收：已通过 `AlertControllerTest,OperationAuditServiceTest,ReportCatalogServiceTest`、`pnpm vitest run src/pages/alert/__tests__/index.test.ts src/pages/report/__tests__/catalog-entry.test.ts` 与 `git diff --check`。
- E 最终复审：已通过。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- `git diff --check`：通过。
- AutoPilot flag 核对：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`。

失败分类或非失败分类：真实代码质量问题已修复；导出审计留痕与目录一致性已收口；原 P1 已关闭
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前 Ready 队列已空；下一轮是否继续拆报表中心后续缺口或切换其他平台化题，仍需主线程/A 基于长期任务池重新裁决。
- 本轮审计确认采用下载后补记；若补记失败，前端仅做非阻断 warning，不回滚已生成的下载结果。

---

Issue：ISSUE-008-016 通知平台平台化缺口-M2：状态变更通知与订阅偏好一致性回归

目标：
- 在 D 验收通过与 E 审查 PASS 的前提下，完成 `ISSUE-008-016` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确当前 Ready 已空、待主线程/A 下一轮拆题。
- 严格隔离白名单外脏改动，不做 push，不改 AutoPilot flag 文件。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`：补三种状态流转回归，确认状态通知严格受 `notifyOnStatusChanged`、`minSeverity` 与角色域边界约束。
- `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`：补渠道大小写与空白归一化回归，确认状态通知不会因 ` in_app ` 这类配置被静默跳过。
- `frontend-admin/src/pages/alert/index.vue`：新增订阅边界归一化与摘要展示，保证用户覆盖不能放大默认渠道、预警域、严重度或状态变更范围。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`：补订阅弹窗展示/保存边界回归。
- `docs/quality/issue-008-016-notification-status-subscription-consistency.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 与当前焦点同步。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 `5/10` 个实施型 Ready Issue。

验证命令摘要：
- D 验收：已通过后端 `57` 项测试（`AlertEvaluationServiceTest`、`AlertControllerTest`、`AlertNotificationDispatcherTest`）、前端 `17` 项测试（`pnpm vitest run src/pages/alert/__tests__/index.test.ts`）与 `pnpm type-check`。
- E 审查：PASS。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- `git diff --check`：通过。
- AutoPilot flag 核对：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`。

失败分类或非失败分类：真实代码质量问题已修复；状态变更通知、订阅默认边界与前端订阅弹窗一致性回归已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前 Ready 队列已空；下一轮是否继续拆通知平台后续缺口或切换其他 P2 平台化题，仍需主线程/A 基于长期任务池重新裁决。
- 本轮只收敛站内通知状态变更与订阅偏好一致性，不覆盖外部通知渠道、模板中心或通知平台更大范围重构。

---

Issue：ISSUE-008-017 通知平台平台化缺口-M3：占位渠道可见性与发送记录语义回归

目标：
- 在 D 验收通过与 E 审查 PASS 的前提下，完成 `ISSUE-008-017` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确当前 Ready 已空、待主线程/A 下一轮拆题。
- 严格隔离白名单外脏改动，不做 push，不改 AutoPilot flag 文件。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/alert/service/AlertSubscriptionService.java`：默认订阅渠道改为基于真实已配置渠道计算，不再写死占位渠道。
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`：补订阅接口返回渠道边界断言，确认当前只暴露 `IN_APP`。
- `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`：补未配置/未实现占位渠道的发送记录回归，确认统一落 `SKIPPED` 与明确失败原因。
- `frontend-admin/src/pages/alert/index.vue`：订阅摘要在接口未返回前不再回退展示 `EMAIL / WECHAT / SMS` 占位渠道。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`：补前端订阅摘要回归，确认页面只展示真实可生效渠道。
- `docs/quality/issue-008-017-notification-channel-visibility-send-record-semantics.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 与当前焦点同步。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 `6/10` 个实施型 Ready Issue。

验证命令摘要：
- D 验收：已通过后端 `59` 项测试（`AlertNotificationDispatcherTest`、`AlertControllerTest`、`AlertEvaluationServiceTest`）、前端 `18` 项测试（`pnpm vitest run src/pages/alert/__tests__/index.test.ts`）、`pnpm type-check` 与 `git diff --check`。
- E 审查：PASS。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- AutoPilot flag 核对：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`。

失败分类或非失败分类：真实代码质量问题已修复；占位渠道可见性、发送记录跳过语义与前端订阅展示一致性回归已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前通知平台仍只有站内信真实可达；外部渠道接入、模板中心、重试与频控仍是后续题，不在本轮。
- 当前 Ready 队列已空；下一轮是否继续拆通知平台后续缺口或切换其他 P2 平台化题，仍需主线程/A 基于长期任务池重新裁决。

---

Issue：ISSUE-008-018 通知平台平台化缺口-M4：同告警重复通知抑制与站内信频控回归

目标：
- 在 D 最终验收通过与 E 最终复审 PASS 的前提下，完成 `ISSUE-008-018` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确当前 Ready 已空、待主线程/A 下一轮拆题。
- 严格隔离白名单外脏改动，不做 push，不改 AutoPilot flag 文件。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/alert/notification/AlertNotificationDispatcher.java`：补齐同告警重复分发抑制与站内信频控边界。
- `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`：补同告警同事件抑制与非同事件/非同告警不误合并回归。
- `docs/quality/issue-008-018-notification-dedup-frequency-guard.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 与当前焦点同步。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 `7/10` 个实施型 Ready Issue。

验证命令摘要：
- D 最终验收：已通过后端 `62` 项测试（`AlertNotificationDispatcherTest`、`AlertEvaluationServiceTest`、`AlertControllerTest`）与 `git diff --check`。
- E 最终复审：PASS，原 P1 已关闭。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- AutoPilot flag 核对：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`。

失败分类或非失败分类：真实代码质量问题已修复；原 P1 已关闭；同告警重复通知抑制与站内信频控回归已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前并发竞态仍是通知平台后续非本轮剩余风险，本轮未扩写到并发级别治理。
- 当前 Ready 队列已空；下一轮是否继续拆通知平台后续缺口或切换其他 P2 平台化题，仍需主线程/A 基于长期任务池重新裁决。

---

Issue：ISSUE-008-019 通知平台平台化缺口-M5：并发重复分发幂等与发送记录一致性回归

目标：
- 在 D 最终验收通过与 E 最终复审 PASS 的前提下，完成 `ISSUE-008-019` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确当前 Ready 已空、待主线程/A 下一轮拆题。
- 严格隔离白名单外脏改动，不做 push，不改 AutoPilot flag 文件。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/alert/notification/AlertNotificationDispatcher.java`：补齐并发重复分发下的最小幂等保护与发送记录一致性边界。
- `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`：补并发重复分发只留一条有效发送与异键不误串并回归。
- `docs/quality/issue-008-019-notification-concurrency-idempotency.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 与当前焦点同步。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 `8/10` 个实施型 Ready Issue。

验证命令摘要：
- D 最终验收：已通过后端 `63` 项测试（`AlertNotificationDispatcherTest`、`NotificationServiceTest`、`AlertEvaluationServiceTest`）与 `git diff --check`。
- E 最终复审：PASS；多 JVM 幂等为非本轮剩余风险。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- AutoPilot flag 核对：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`。

失败分类或非失败分类：真实代码质量问题已修复；并发重复分发幂等与发送记录一致性回归已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前幂等保护基于单 JVM / 当前进程内并发闭环，多 JVM / 分布式实例间幂等仍是后续平台化题。
- 当前 Ready 队列已空；下一轮是否继续拆通知平台后续缺口或切换其他 P2 平台化题，仍需主线程/A 基于长期任务池重新裁决。

---

Issue：ISSUE-008-020 规则治理中心平台化缺口-M2：阈值/窗口/严重度配置生效回归

目标：
- 在 D 最终验收通过与 E 最终复审 PASS 的前提下，完成 `ISSUE-008-020` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确当前 Ready 已空、待主线程/A 下一轮拆题。
- 严格隔离白名单外脏改动，不做 push，不改 AutoPilot flag 文件。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`：补齐 `threshold_ratio`、`window_days`、`severity_override` 生效回归与最小辅助清理方法。
- `docs/quality/issue-008-020-rule-governance-config-effectiveness.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done 与当前焦点同步。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 `9/10` 个实施型 Ready Issue。

验证命令摘要：
- D 最终验收：已通过后端 `58` 项测试（`AlertEvaluationServiceTest`、`AlertControllerTest`）与 `git diff --check`。
- E 最终复审：PASS，原 `P2` 已关闭。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- AutoPilot flag 核对：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`。

失败分类或非失败分类：真实代码质量问题已修复；规则配置字段真实生效边界回归已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前规则治理中心仍只有配置字段生效层面的最小回归，不等于完整平台化完成。
- 当前 Ready 队列已空；下一轮是否继续规则治理中心后续缺口或切换其他 P2 平台化题，仍需主线程/A 基于长期任务池重新裁决。

---

Issue：ISSUE-008-021 规则治理中心平台化缺口-M3：规则侧去重时窗与重复预警抑制生效回归

目标：
- 在 D 最终验收通过与 E 最终复审 PASS 的前提下，完成 `ISSUE-008-021` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 从 `Ready` 队列移除，写入 `Done`，并明确 `启动迭代-10` 已达到 `10/10` 上限、停止继续派发下一任务。
- 严格隔离白名单外脏改动，不做 push；按项目既有方式关闭 `enabled.flag`，不启动第 11 轮。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`：补齐 `dedup_hours` 生效回归与最小测试辅助方法。
- `docs/quality/issue-008-021-rule-governance-dedup-suppression-window.md`：新增正式收口报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`、`docs/backlog/done-issues.md`：完成 Ready -> Done、达到上限停止与当前焦点同步。
- `.codex-autopilot/state.json`：将本轮计入 `启动迭代-10` 的第 `10/10` 个实施型 Ready Issue，并切到 `ITERATION_LIMIT_REACHED` 停止态。
- `.codex-autopilot/enabled.flag`：删除，阻断第 11 轮。

验证命令摘要：
- D 最终验收：已通过后端 `61` 项测试（`AlertEvaluationServiceTest`、`AlertControllerTest`）与 `git diff --check`。
- E 最终复审：PASS。
- `git branch --show-current`：`master`。
- `git status --short`：确认工作区存在白名单内实现改动，本轮只收口白名单文件。
- AutoPilot flag 核对：提交前 `stop.flag=False`、`pause.flag=False`；收口后 `enabled.flag=False`。

失败分类或非失败分类：真实代码质量问题已修复；规则侧去重时窗与重复预警抑制生效回归已收口；已达到迭代上限并安全停止下一任务派发
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前规则治理中心仍只有最小规则评估回归，不等于完整规则治理平台已完成。
- 若后续需要继续连续迭代，必须由主线程重新裁决下一条 Ready 并显式重新启用 AutoPilot。

---

Issue：ISSUE-008-025 供应商履约评分排名可见性回归

目标：
- 在实现、D 验收与 E 审查通过后，完成 `ISSUE-008-025` 的正式归档、backlog 状态同步、本地提交与 AutoPilot 计数收口。
- 将该 Issue 计入本轮 `启动迭代-10` 的第 `3/10` 个实施型 Ready Issue。
- 严格隔离白名单外脏改动，不做 push，不补写未实际完成的浏览器验收。

修改范围摘要：
- 供应商履约评分排名可见性相关实现与回归已完成。
- `docs/quality/issue-008-025-supplier-score-ranking-visibility.md`：新增正式收口报告。
- 已本地提交：`e3cee8e6b`。

验证命令摘要：
- D 验收：已通过 `DashboardMaterialRoleServiceTest`，`12/12` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；供应商履约评分排名可见性回归已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 真实浏览器渲染证据不在本 Issue 内补写，由 `ISSUE-008-027` 承接。

---

Issue：ISSUE-008-023 WBS 只读甘特视图回归

目标：
- 在实现、D 验收与 E 审查通过后，完成 `ISSUE-008-023` 的正式归档、backlog 状态同步与 AutoPilot 计数收口。
- 将该 Issue 计入本轮 `启动迭代-10` 的第 `4/10` 个实施型 Ready Issue。
- 严格隔离白名单外脏改动，不做 push，不补写未实际完成的浏览器验收。

修改范围摘要：
- WBS 只读甘特视图相关实现与回归已完成。
- `docs/quality/issue-008-023-wbs-readonly-gantt-view.md`：新增正式收口报告。

验证命令摘要：
- D 验收：已通过 `SubTaskControllerTest`，`10/10` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；WBS 只读甘特视图回归已收口
是否自动合并：否
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 真实浏览器渲染证据不在本 Issue 内补写，由 `ISSUE-008-026` 承接。
