# Iteration Report

Issue：ISSUE-000-001 搭建本地 Codex AutoPilot 第一轮治理框架

目标：
- 将第一轮治理框架的通过验收事实正式归档，避免该 Issue 继续作为 Ready 任务被自动选中。

修改范围摘要：
- `docs/backlog/ready-issues.md`：将 ISSUE-000-001 从 Ready 池移出，转入已完成/历史。
- `docs/backlog/done-issues.md`：追加 Done 记录，标记完成日期、合并方式、验证结果和关联报告。
- `docs/iterations/iteration-2026-07-08-report.md`：沉淀本轮正式 iteration report。
- 验收依据摘要：第一轮治理框架独立验收结论为通过/非阻塞；17 项交付物已落地；`start`、`status`、`pause`、`resume`、`stop`、`kill` 默认不强杀、config JSON parse、`git diff --check` 已验证通过。

验证命令摘要：
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-start.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-status.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-pause.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-resume.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-stop.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-kill.ps1`：通过，默认不强杀
- `git diff --check`：通过

是否自动合并：否
是否推送：否
结论：通过
阻塞：非阻塞
剩余风险：
- Windows 计划任务仍需在实机场景补做一次安装/卸载核验。
- CRLF 警告属于非阻塞项，不影响本轮归档结论。
- 后续进入真实业务迭代前，需补充新的 P0 Ready Issues。

---

Issue：ISSUE-004-001 成本台账与汇总口径回归

目标：
- 核对成本台账、成本汇总、成本科目联动与 Dashboard 成本视图主链路金额口径，形成可追溯回归证据。

修改范围摘要：
- `docs/quality/issue-004-001-cost-ledger-regression.md`：新增正式质量报告，记录金额口径、样例断言、验证命令与通过结论。
- `docs/backlog/ready-issues.md`：将 ISSUE-004-001 从 Ready 池移出。
- `docs/backlog/done-issues.md`：追加 Done 记录。
- 本轮未修改后端生产代码或测试代码；现有覆盖已满足该 Issue 验收标准。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=DashboardServiceTest,DashboardCostServiceTest,CostSummaryServiceTest,CostLedgerServiceTest" test`：通过，`Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

是否自动合并：否
是否推送：否
结论：通过
阻塞：非阻塞
剩余风险：
- 原 Ready Issue 中 `CostServiceTest` 类名不存在，本轮已使用真实成本测试类替代。
- 本轮未跑全量后端测试，结论限于 ISSUE-004-001 指定回归范围。

---

Issue：ISSUE-004-002 采购收货库存数量一致性回归

目标：
- 围绕采购订单、收货单、库存流水的数量链路做一致性回归，避免扩散到付款、发票、审批域。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/receipt/MatReceiptServiceTest.java`：新增采购订单明细 `receivedQuantity` 与收货明细保存/替换/清空的回归断言。
- `backend/src/test/java/com/cgcpms/inventory/MatStockServiceTest.java`：新增收货来源入库库存流水 `sourceType/sourceId` 回归断言。
- `backend/src/test/java/com/cgcpms/purchase/MatPurchaseOrderServiceTest.java`：补齐采购审批测试的本地审批人前置，修复测试数据与采购审批模板不一致导致的环境前置失败。
- `docs/quality/issue-004-002-purchase-receipt-inventory-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`：将 ISSUE-004-002 从 Ready 池移出。
- `docs/backlog/done-issues.md`：追加 Done 记录。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=MatPurchaseOrderServiceTest,MatReceiptServiceTest,MatStockServiceTest" test`：通过，`Tests run: 59, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：非阻塞
剩余风险：
- 原 Ready Issue 中 `InventoryServiceTest` 类名不存在，本轮已使用真实库存服务测试类 `MatStockServiceTest` 替代。
- 本轮未跑全量后端测试，结论限于 ISSUE-004-002 指定回归范围。
- 说明：`docs/plans/cgc-pms-production-enhancement-plan.md` 迁移到 `docs/backlog/cgc-pms-production-enhancement-plan.md` 属于用户手动执行/确认的既有变更，非子智能体越权；本轮不回滚该迁移，ISSUE-004-002 结论仍仅以采购/收货/库存测试结果为准。

---

Issue：ISSUE-004-003 付款发票审批状态链路回归

目标：
- 确保付款申请、发票登记、审批状态三者一致性回归通过，避免审批驳回仍可付款或发票缺少付款记录仍可登记。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/payment/service/PayRecordService.java`：付款写回入口新增 `approvalStatus=APPROVED` 门禁。
- `backend/src/test/java/com/cgcpms/payment/PayApplicationServiceTest.java`：新增驳回付款申请不可付款回归测试。
- `docs/quality/issue-004-003-payment-invoice-workflow-regression.md`：新增正式质量报告和状态矩阵。
- `docs/backlog/ready-issues.md`：将 ISSUE-004-003 从 Ready 池移出。
- `docs/backlog/done-issues.md`：追加 Done 记录。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=PayApplicationServiceTest#testWriteback_RejectedApplicationNotAllowed" test`：先红后绿，最终通过，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
- `cd backend; .\mvnw.cmd "-Dtest=PayApplicationServiceTest,InvoiceServiceTest,WorkflowSubmitServiceTest" test`：通过，`Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `cd backend; .\mvnw.cmd "-Dtest=PaymentWritebackTest,PaymentFinancialConsistencyTest" test`：通过，`Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过，仅有 CRLF 提示。

是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：非阻塞
剩余风险：
- 本轮未跑全量后端测试，结论限于 ISSUE-004-003 指定回归范围及付款写回影响面。

---

Issue：ISSUE-005-001 付款与发票列表页生产化补强

目标：
- 仅补强付款列表与发票列表的筛选回显、刷新保持、空态、错误态和重试入口，不扩到采购、库存、路由或后端。

修改范围摘要：
- `docs/quality/issue-005-001-payment-invoice-list-production.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/blocked-issues.md`：将该 Issue 从 Ready 转为 Blocked。
- 未通过浏览器验收的前端实现与测试已暂存为 WIP，未随本轮 blocked 收口提交。

验证命令摘要：
- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/payment/__tests__/list-production.test.ts src/pages/invoice/__tests__/list-production.test.ts`：通过，`3` 个文件、`7` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。
- 运行态检查：`http://127.0.0.1:5173/` 返回 `200`；`http://localhost:8080/actuator/health` 连接被拒绝；浏览器首页显示 `Request failed with status code 500`。

是否自动合并：否
是否推送：否
结论：不通过
阻塞：环境前置类，后端运行态未就绪且 `dev-login` 在内置浏览器中被拦截
剩余风险：
- 若后续继续该 Issue，需要先恢复已暂存的前端 WIP，再做真实浏览器验收。
- 付款页与发票页的真实交互验收尚未在可用后端上完成，本轮不能给出“生产化补强通过”结论。
- `frontend-admin/dist` 和 `.agent-runtime/` 属于本轮临时产物，可忽略，不应作为正式交付物。
