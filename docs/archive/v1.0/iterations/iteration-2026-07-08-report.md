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

---

Issue：ISSUE-005-001 付款与发票列表页生产化补强（阻塞重试）

目标：
- 解除后端 `8080` 与 `dev-login` 环境前置阻塞，恢复已暂存前端 WIP，补做付款页和发票页真实浏览器验收，并将该 Issue 从 Blocked 收口为 Done。

修改范围摘要：
- `frontend-admin/src/pages/payment/index.vue`：付款列表接入 query 回显/同步，补错误态、空态和重试入口。
- `frontend-admin/src/pages/invoice/index.vue`、`frontend-admin/src/pages/invoice/composables/useInvoiceList.ts`：发票列表接入 query 回显/同步，补错误态、空态和重试入口。
- `frontend-admin/src/composables/listPageQuery.ts` 与 3 个最小测试：覆盖列表 query 读写与生产化接线。
- `docs/quality/issue-005-001-payment-invoice-list-production.md`、`docs/backlog/ready-issues.md`、`docs/backlog/blocked-issues.md`、`docs/backlog/done-issues.md`：更新通过报告与 backlog 状态。

验证命令摘要：
- `python scripts/rebuild.py frontend`：通过，前端容器重启成功。
- 稳定等待：已按项目规则等待 `180秒`。
- `http://localhost:8080/api/actuator/health`：返回 `200`。
- `http://localhost:5173/`：返回 `200`。
- `http://localhost:5173/api/auth/dev-login?redirect=/dashboard`：返回 `302 /dashboard`，Chromium 最终落点 `/dashboard`。
- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/payment/__tests__/list-production.test.ts src/pages/invoice/__tests__/list-production.test.ts`：通过，`3` 个文件、`7` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。
- Chromium 浏览器验收：付款页 `/payment/application` 与发票页 `/invoice` 的筛选回显、重置、刷新保持、空态、错误态和“重试”入口通过。

是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 付款空态与付款/发票错误态通过浏览器网络拦截触发，未改动后端数据；结论限于前端 UI 分支和重试入口可达性。
- 本轮未跑全量前端测试，结论限于 ISSUE-005-001 指定补强范围。

---

Issue：ISSUE-006-001 文件上传白名单与发票识别失败兜底

目标：
- 以最小改动补齐文件上传大小与类型白名单校验，并让发票识别失败返回可理解错误；不引入病毒扫描、外部安全服务或外部存储配置变更。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java`：发票识别入口复用 `FileTypeValidator` 的真实文件名校验，并对超过 10MB 的 PDF 返回 `FILE_TOO_LARGE`。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceServiceTest.java`：新增超限 PDF 与 PDF 内容非 PDF 扩展名的识别入口回归测试。
- `frontend-admin/src/pages/invoice/components/InvoiceFormModal.vue`：前端发票识别上传前限制调整为 10MB，普通识别失败展示可理解错误提示并复位 loading。
- `docs/quality/issue-006-001-file-upload-invoice-recognition.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-001 从 Ready 收口为 Done。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest,InvoiceServiceTest" test`：通过，`Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `cd frontend-admin; pnpm type-check`：通过。
- `git diff --check`：通过。

是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未引入病毒扫描或外部安全服务，符合 Ready Issue 的明确非目标。
- 本轮未跑全量后端/前端测试，结论限于 ISSUE-006-001 指定范围。
- `.codex-autopilot/stop.flag` 保持存在，仅阻断后续 Issue；本轮未启动 ISSUE-007-001。
- AutoPilot 停止语义已在规则文档中澄清：`停止自动迭代系统` 阻断下一任务启动，不强制中断已启动任务，当前任务收口后再检查 stop/pause/enabled。

---

Issue：ISSUE-000-001 AutoPilot 运行优化补录

目标：
- 将 2026-07-08 最近运行暴露的 8 项治理问题固化进 AutoPilot 规则、模板和本地 stop 脚本，避免把命令调用、环境前置或 Ready Issue 配置误判成业务代码失败。

修改范围摘要：
- `AGENTS.override.md`：补充 health gate、PowerShell Maven 单参数规范、Ready Issue 验证命令预检、环境前置分类、stop/pause 收口边界。
- `docs/plans/cgc-pms-local-codex-autopilot-plan.md`：同步自动合并条件、失败分类、blocked WIP stash 规则、模板字段与 stop 文案。
- `docs/backlog/ready-issues.md`、`docs/backlog/blocked-issues.md`、`docs/backlog/done-issues.md`：补齐验证命令规范、失败分类、WIP stash 记录字段。
- `scripts/codex-autopilot/autopilot-stop.ps1`：仅调整提示语，明确当前任务可自然收口、后续任务不再启动。

验证命令摘要：
- `git diff --check`：通过。
- `powershell -NoProfile -Command "[System.Management.Automation.Language.Parser]::ParseFile('D:\\projects-test\\cgc-pms\\scripts\\codex-autopilot\\autopilot-stop.ps1',[ref]$null,[ref]$null) | Out-Null"`：通过。

失败分类或非失败分类：非失败分类
是否自动合并：否
是否推送：否
结论：通过
阻塞：非阻塞
剩余风险：
- 本轮只固化规则、模板和 stop 文案，没有新增自动 health-check 脚本；后续执行仍依赖子智能体按规则落实。
- `docs/quality/**` 未新增独立模板文件；质量报告分类要求已通过规则与计划书约束同步，后续正式报告需按新字段执行。

---

Issue：ISSUE-007-001 访问日志上下文与备份清单补强

目标：
- 仅补 traceId/requestId 等访问日志上下文字段及最小备份清单文档，不在本轮做完整监控平台接入。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/common/filter/TraceIdFilter.java`：复用既有请求过滤器，补充 `X-Request-Id`、MDC `requestId` 和 `HTTP_ACCESS` 访问日志字段。
- `docs/10-部署运维手册.md`：补充访问日志采集字段、备份范围、恢复入口和恢复演练清单。
- `docs/quality/issue-007-001-access-log-backup-checklist.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-001 从 Ready 收口为 Done。

验证命令摘要：
- `LoggingConfigTest` 预检：不存在；因 `backend/src/test/**` 不在本 Issue 允许修改范围内，未新增测试类。
- `cd backend; .\mvnw.cmd "-DskipTests" test`：通过，后端主代码与测试代码编译成功，`BUILD SUCCESS`。
- `git diff --check`：通过，仅有 `docs/10-部署运维手册.md` 的 LF/CRLF 提示。

失败分类或非失败分类：Ready Issue 配置问题已更正；实现与文档补强通过
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未接入外部监控平台，生产日志采集规则仍需后续在实际平台配置。
- 本轮未新增测试源码，结论限于允许范围内的代码编译、代码审查和正式报告证据。

---

Issue：ISSUE-004-004 领料出库与项目成本归集回归

目标：
- 验证领料审批通过后出库流水、库存扣减与项目成本归集三类证据一致，避免领料主链路回退。

修改范围摘要：
- `docs/quality/issue-004-004-requisition-stock-cost-regression.md`：新增正式质量报告，记录状态字段、出库流水、库存扣减和成本归集证据。
- `docs/backlog/ready-issues.md`：将 ISSUE-004-004 从 Ready 池移出，转入已完成/历史。
- `docs/backlog/done-issues.md`：追加 Done 记录。
- 本轮未修改后端生产代码或测试代码；现有 `MatRequisitionWorkflowSubmitTest` 已覆盖该 Issue 的核心断言。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=MatRequisitionWorkflowSubmitTest,MatStockServiceTest,CostLedgerServiceTest" test`：通过，`Tests run: 49, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：非失败分类
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未跑全量后端测试，结论限于 ISSUE-004-004 指定回归范围。
- 领料成本科目仍沿用既有默认材料科目解析逻辑，本轮不扩展成本科目配置能力。

---

Issue：ISSUE-004-005 分包计量与结算状态链路回归

目标：
- 验证分包计量提交、结算生成、审批状态写回、来源和付款关联结果，避免分包结算链路回退。

修改范围摘要：
- `docs/quality/issue-004-005-subcontract-settlement-regression.md`：新增正式质量报告，记录状态、金额、来源、付款和保护条件证据。
- `docs/backlog/ready-issues.md`：将 ISSUE-004-005 从 Ready 池移出，转入已完成/历史。
- `docs/backlog/done-issues.md`：追加 Done 记录。
- 本轮未修改后端生产代码或测试代码；现有四个测试类已覆盖该 Issue 的核心断言。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=SubMeasureServiceTest,StlSettlementServiceTest,SettlementWorkflowHandlerTest,StlSettlementQueryServiceTest" test`：通过，`Tests run: 62, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：非失败分类
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未跑全量后端测试，结论限于 ISSUE-004-005 指定回归范围。
- 本轮未新增浏览器或前端验收，结算页面交互不属于该 Ready Issue 的允许范围。

---

Issue：ISSUE-004-006 审批中心待办/已办/我发起统一筛选回归

目标：
- 验证审批中心待办、已办、抄送、我发起四类查询口径与前端工作台冻结口径，确保分页 total、筛选条件和身份边界一致。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/workflow/WorkflowQueryServiceTest.java`：修正测试业务类型、合同测试数据和模板隔离，避免测试配置与真实业务访问校验冲突。
- `docs/quality/issue-004-006-approval-workbench-regression.md`：新增正式质量报告，记录后端查询与前端工作台回归证据。
- `docs/backlog/ready-issues.md`：将 ISSUE-004-006 从 Ready 池移出，转入已完成/历史。
- `docs/backlog/done-issues.md`：追加 Done 记录。
- 未修改后端生产代码或前端页面代码。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=WorkflowQueryServiceTest,WorkflowTaskServiceTest,WorkflowSubmitServiceTest" test`：首轮失败，分类为 Ready Issue 测试配置问题；修正后通过，`Tests run: 60, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `cd frontend-admin; pnpm exec vitest run src/pages/approval/__tests__/ApprovalWorkList.test.ts src/pages/approval/__tests__/ApprovalConfirm.test.ts src/pages/approval/__tests__/workflowDisplay.test.ts`：通过，`3` 个文件、`22` 个用例全部通过。
- `git diff --check`：通过。

失败分类或非失败分类：Ready Issue 测试配置问题已更正
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未跑全量后端/前端测试，结论限于 ISSUE-004-006 指定范围。
- 本轮未做真实浏览器验收，前端结论来自 Vitest 组件/逻辑测试。

---

Issue：ISSUE-005-002 项目与合同列表页生产化补强

目标：
- 统一项目与合同列表页的查询条件回显、分页参数、loading/empty/error 态与 URL 参数保留行为；不扩展详情页重构或新业务字段。

修改范围摘要：
- `frontend-admin/src/pages/project/index.vue`：新增项目列表 URL query 恢复与同步，覆盖 `keyword`、`projectType`、`status`、`pageNo`、`pageSize`。
- `frontend-admin/src/pages/contract/composables/useContractLedger.ts`：新增合同列表 URL query 恢复与同步，覆盖 `keyword`、`projectId`、`contractType`、`contractStatus`、`startDate`、`endDate`、`pageNo`、`pageSize`。
- `frontend-admin/src/pages/project/__tests__/ProjectLedgerProduction.test.ts`、`frontend-admin/src/pages/contract/__tests__/ContractLedgerPage.test.ts`：补充 URL 参数恢复与保留的源代码守卫。
- `docs/quality/issue-005-002-project-contract-list-production.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-005-002 从 Ready 收口为 Done。

验证命令摘要：
- `cd frontend-admin; pnpm exec vitest run src/pages/project/__tests__/ProjectLedgerProduction.test.ts src/pages/project/__tests__/ProjectNav.test.ts src/pages/contract/__tests__/ContractLedgerPage.test.ts src/pages/contract/__tests__/ContractFormPage.test.ts src/pages/contract/__tests__/useContractLedger-ui-consistency.test.ts`：首轮失败归类为测试守卫配置问题；修正后通过，`5` 个文件、`21` 条用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过，`vue-tsc --noEmit` 无错误。
- `git diff --check`：通过。

失败分类或非失败分类：测试守卫配置问题已更正；实现与前端验证通过
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未做真实浏览器验收，结论基于指定 Vitest、类型检查和代码审查。
- 本轮未跑全量前端测试，结论限于 ISSUE-005-002 指定项目与合同列表生产化范围。
- 下一 Ready Issue 为 `ISSUE-006-002`，涉及附件下载鉴权与临时链接安全边界，需要主线程重新分档后再继续。

---

Issue：ISSUE-005-003 采购与收货列表页生产化补强

目标：
- 补强采购订单列表与材料验收列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口；不扩展详情页重构、新业务字段或后端接口语义。

修改范围摘要：
- `frontend-admin/src/pages/purchase/order.vue`：采购订单列表新增 URL query 恢复与同步，覆盖 `projectId`、`contractId`、`partnerId`、`orderStatus`、`orderType`、`keyword`、`pageNo`、`pageSize`；补错误态、空态和重试入口。
- `frontend-admin/src/pages/receipt/index.vue`、`frontend-admin/src/pages/receipt/composables/useReceiptList.ts`：材料验收列表新增 URL query 恢复与同步，覆盖 `projectId`、`orderId`、`receiptCode`、`qualityStatus`、`pageNo`、`pageSize`；补错误态、空态和重试入口。
- `frontend-admin/src/pages/purchase/__tests__/list-production.test.ts`、`frontend-admin/src/pages/receipt/__tests__/list-production.test.ts`：补充生产化守卫测试。
- `docs/quality/issue-005-003-purchase-receipt-list-production.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-005-003 从 Ready 收口为 Done。

验证命令摘要：
- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/purchase/__tests__/order.test.ts src/pages/purchase/__tests__/list-production.test.ts src/pages/receipt/__tests__/index.test.ts src/pages/receipt/__tests__/list-production.test.ts`：通过，`5` 个文件、`32` 个用例全部通过。
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
- 本轮未跑全量前端测试，结论限于 ISSUE-005-003 指定采购/收货列表生产化范围。

---

Issue：ISSUE-006-002 附件下载鉴权与临时链接回归

目标：
- 回归文件下载鉴权、业务对象读权限校验与临时下载链接生成口径。
- 补齐未授权读取、跨业务对象读取、文本附件下载头等安全边界断言。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：新增跨租户文件隐藏、业务对象读权限拒绝不生成 MinIO 链接、文本附件下载头和 5 分钟 GET 临时链接断言。
- `backend/src/test/java/com/cgcpms/file/BusinessObjectAuthorizerTest.java`：新增跨租户合同对象拒绝且不进入项目权限检查断言。
- `docs/quality/issue-006-002-file-download-auth-temp-link.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-002 从 Ready 收口为 Done，并按 current-focus 拆出下一轮 Ready Issue。
- 本轮未修改后端生产代码、前端、migration 或外部对象存储配置。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest,BusinessObjectAuthorizerTest" test`：首轮因测试代码引用不存在的 `TestUserContext.TENANT_1` 编译失败，归类为测试编写问题；修正后通过，`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：测试编写问题已更正；现有生产实现通过新增安全边界回归
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未跑全量后端测试，结论限于 ISSUE-006-002 指定 file 模块与业务对象授权范围。
- 本轮未连接真实 MinIO，临时链接策略通过 `GetPresignedObjectUrlArgs` 参数捕获验证。

---

Issue：ISSUE-006-003 附件删除鉴权与审计回归

目标：
- 回归附件删除前的租户边界、业务对象写权限校验、MinIO 删除调用顺序与 DELETE 审计事件。
- 补齐删除成功、删除失败和权限拒绝路径的最小安全断言。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：新增跨租户删除拒绝、业务对象写权限拒绝不触发 MinIO、授权成功调用 `removeObject` 后逻辑删除记录的回归断言。
- `backend/src/test/java/com/cgcpms/audit/OperationAuditAspectTest.java`：新增 `DELETE /files/{id}` 成功与业务拒绝两条审计事件断言，覆盖操作类型、业务类型、业务 ID、请求路径、成功标记和错误码。
- `docs/quality/issue-006-003-file-delete-auth-audit.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-003 从 Ready 收口为 Done。
- 本轮未修改后端生产代码、前端、migration、deploy 或外部对象存储配置。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest,BusinessObjectAuthorizerTest,OperationAuditAspectTest" test`：先后暴露测试配置/测试夹具问题；修正后通过，`Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：测试配置与测试夹具问题已更正；现有生产实现通过新增安全边界回归
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未跑全量后端测试，结论限于 ISSUE-006-003 指定 file 模块、业务对象授权和审计切面范围。
- 本轮未连接真实 MinIO，删除副作用通过 `MinioClient.removeObject` 参数捕获验证。

---

Issue：ISSUE-006-004 发票识别重复发票与付款关联回归

目标：
- 回归发票登记/更新链路中的重复发票号、金额、日期和付款记录关联一致性。
- 补齐发票号码、金额、日期、付款记录关联的最小安全断言。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java`：更新携带新 `payRecordId` 时校验付款记录存在、同租户并按新付款记录做项目权限校验。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceServiceTest.java`：补齐重复发票失败后原金额、日期、付款记录不变，以及无效付款记录更新被拒绝的断言。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceControllerTest.java`：补齐 register 端到端付款记录、金额、日期追溯和重复 register 错误码断言；修正控制器测试夹具顺序耦合。
- `docs/quality/issue-006-004-invoice-duplicate-payment-link.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-004 从 Ready 收口为 Done。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceServiceTest#shouldRejectUpdateToInvalidPayRecordAndKeepOriginalFields" test`：先红后绿，最终通过，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceServiceTest,InvoiceControllerTest" test`：通过，`Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：测试前置配置、测试编写和测试夹具问题已更正；生产更新路径一致性缺口已修复
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未跑全量后端测试，结论限于 ISSUE-006-004 指定 invoice 模块和控制器回归范围。
- 外部发票识别服务、PDF 解析失败和人工确认口径留给 ISSUE-006-005。

---

Issue：ISSUE-006-005 发票识别失败原因与人工确认口径回归

目标：
- 回归 PDF 解析失败时的错误原因、主流程不被阻断和人工确认前不自动写入高风险识别结果的口径。
- 补齐失败兜底、人工确认状态和异常路径断言。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java`：PDFBox 加密/解析异常改为返回失败识别结果，不再阻断调用方主流程；成功和失败结果均要求人工确认。
- `backend/src/main/java/com/cgcpms/invoice/vo/InvoiceRecognizeResultVO.java`：新增 `success`、`manualConfirmationRequired`、`errorCode`、`errorMessage`。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceServiceTest.java`：新增失败原因、主流程继续、识别不落库和人工确认状态断言。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceRecognitionTest.java`：加密 PDF、损坏 PDF 失败路径改为失败 VO 回归。
- `docs/quality/issue-006-005-invoice-recognition-manual-confirmation.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-005 从 Ready 收口为 Done。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceServiceTest,InvoiceRecognitionTest" test`：通过，`Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceServiceTest" test`：通过，`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；失败路径和人工确认边界已补强
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未修改前端，结论限于后端识别返回口径和服务层人工确认边界。
- 本轮未接入外部发票识别服务，仍沿用本地 PDFBox 文本解析。
- 本轮未跑全量后端测试，结论限于 ISSUE-006-005 指定 invoice 模块回归范围。

---

Issue：ISSUE-007-002 MinIO 健康指标与文件失败监控回归

目标：
- 回归 MinIO 健康检查成功、桶缺失、连接失败三类口径。
- 回归文件上传失败可观测性，区分文件服务暂不可用与普通上传失败。
- 确认失败路径不泄露对象存储敏感配置。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/config/MinioHealthIndicator.java`：失败路径补充稳定 `category`，连接失败统一返回 `reason=MinIO connection failed`。
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`：上传失败单点分类，连接/超时/域名解析异常映射为 `FILE_STORAGE_UNAVAILABLE`。
- `backend/src/test/java/com/cgcpms/config/MinioHealthIndicatorTest.java`、`backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：新增健康指标分类、上传失败分类和敏感配置不泄露断言。
- `docs/quality/issue-007-002-minio-health-upload-monitoring.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-002 从 Ready 收口为 Done。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=MinioHealthIndicatorTest,FileServiceTest" test`：先红后绿，最终通过，`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；健康指标分类与上传失败口径已补强
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前连接类故障识别覆盖 `ConnectException`、`SocketTimeoutException`、`UnknownHostException`；若后续需要细分更多 MinIO SDK 异常，可在现有单点分类入口扩展。
- 本轮未连接真实 MinIO，结论限于 mock 条件下的健康指标与异常分类回归。

---

Issue：ISSUE-007-003 操作审计字段与文件操作审计回归

目标：
- 回归上传、下载、删除等文件操作的审计类型和关键上下文字段。
- 补齐 trace/user/tenant/path/status 等字段缺失时的正式说明或测试断言。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/controller/FileController.java`：上传审计注解补充 `businessIdExpression = "#businessId"`，让上传事件保留业务对象上下文。
- `backend/src/test/java/com/cgcpms/audit/OperationAuditAspectTest.java`：新增上传、下载审计事件断言，并复用删除成功/失败断言覆盖文件操作审计。
- `docs/quality/issue-007-003-operation-audit-file-actions.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-003 从 Ready 收口为 Done，并按 current-focus 拆出下一轮 Ready Issue。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=OperationAuditAspectTest#testFileUploadPublishesSuccessAuditEvent" test`：先红后绿，最终通过，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
- `cd backend; .\mvnw.cmd "-Dtest=OperationAuditServiceTest,OperationAuditAspectTest,FileServiceTest" test`：通过，`Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；上传审计业务对象上下文已补强
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前操作审计事件和持久化表无 `traceId` 字段；本轮禁止修改 migration，未扩展审计表结构。traceId 仍由访问日志链路承载。
- 本轮未跑全量后端测试，结论限于 ISSUE-007-003 指定审计切面、审计服务和文件服务回归范围。

---

Issue：ISSUE-007-004 接口性能与错误率监控指标回归

目标：
- 回归接口耗时、错误率与基础运行指标的本地可观测性，不接入外部监控平台。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/config/ActuatorMetricsTest.java`：新增命中 Ready Issue 通配符的回归测试，覆盖 `HealthEndpoint` 注册、`http.server.requests` 成功/失败指标、JVM/Hikari/异步线程池指标存在性。
- `docs/quality/issue-007-004-actuator-metrics-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-004 从 Ready 收口为 Done。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=*Metrics*Test,*Actuator*Test" test`：首轮失败，归类为 Ready Issue 配置问题；补齐 `ActuatorMetricsTest` 后通过，`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：Ready Issue 配置问题已更正；本地监控指标断言已补齐
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未接入 Prometheus、告警平台或可视化面板，结论限于项目内 Micrometer/Actuator 本地断言。
- 匿名 `/api/actuator/health` 安全策略未纳入本轮修改范围；如需把匿名健康检查作为上线门禁，应另开安全/运维范围 Issue。

---

Issue：ISSUE-007-005 访问日志 projectId/status/duration/exception 字段回归

目标：
- 回归访问日志中的 `method`、`path`、`projectId`、`status`、`duration`、`exception` 字段。
- 在不扩展日志平台和不引入新依赖的前提下，补齐本地可验证的 `projectId` 归因口径与敏感信息不泄露断言。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/common/filter/TraceIdFilter.java`：访问日志字段补充 `projectId`、`exception`，并将 `durationMs` 回归为 `duration`；`projectId` 解析顺序为请求参数、`projectId` 路由变量和 `/projects/{id}` 路径片段兜底。
- `backend/src/test/java/com/cgcpms/common/filter/TraceIdFilterLoggingTest.java`：新增命中 Ready Issue 原验证通配符的回归测试，覆盖成功/异常请求日志字段与敏感信息不泄露断言。
- `docs/quality/issue-007-005-access-log-fields-regression.md`、`docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：新增正式报告并完成 backlog 收口。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=*Trace*Test,*Logging*Test" test`：预检发现原仓库不存在对应访问日志回归测试；新增 `TraceIdFilterLoggingTest` 后通过，`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：Ready Issue 配置问题已更正；访问日志字段回归断言已补齐
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 当前 `projectId` 只在请求参数、`projectId` 路由变量或 `/projects/{id}` 路径模式下可自动识别；其他业务路径没有统一项目上下文时仍记为 `-`。
- `exception` 当前只记录异常类型名，不记录异常消息；这有利于避免敏感信息泄露，但不提供更细的错误细节。

---

Issue：ISSUE-007-006 备份范围与恢复演练报告模板补强

目标：
- 补齐 MySQL、MinIO、配置、密钥、日志归档的备份范围清单。
- 补齐恢复演练报告模板，固定恢复耗时、数据范围和失败原因字段。
- 保持本轮只做文档补强，不连接生产环境，不执行真实备份/恢复。

修改范围摘要：
- `docs/10-部署运维手册.md`：扩展备份范围清单，新增密钥元数据、日志归档、配置与证书范围说明；补入恢复演练模板与最小示例。
- `docs/quality/issue-007-006-backup-scope-restore-drill-template.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：完成 backlog 收口。

验证命令摘要：
- `git diff --check`：通过。

失败分类或非失败分类：非失败分类
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮仅补模板与清单，不代表宿主机侧日志归档、密钥保管和月度恢复演练已自动落地。
- 当前没有新增独立脚本或自动校验，后续仍需运维按模板完成真实演练并保留证据。

---

Issue：ISSUE-007-007 登录失败与文件失败次数指标回归

目标：
- 回归登录失败次数和文件上传失败次数的本地可观测性。
- 若当前仅有日志没有指标，补最小指标或正式说明，不接入外部平台。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/auth/controller/AuthController.java`：正式登录失败分支记录 `auth.login.failures{code=...}`。
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`：文件上传失败分支记录 `file.upload.failures{code=...}`。
- `backend/src/test/java/com/cgcpms/auth/controller/AuthControllerTest.java`：断言 `AUTH_FAILED` 登录失败指标递增。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：断言 `FILE_UPLOAD_FAILED` 文件上传失败指标递增，并验证指标标签不包含敏感键。
- `docs/quality/issue-007-007-login-file-failure-metrics.md`、`docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：新增正式报告并完成 backlog 收口。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=AuthControllerTest,FileServiceTest" test`：通过，`Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；登录失败与文件上传失败本地指标已补齐
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未接入 Prometheus、告警平台或可视化面板，结论限于项目内 Micrometer 本地断言。
- 指标按错误码聚合，不包含租户、用户、文件名、文件内容、密码、Token 等敏感维度。

---

Issue：ISSUE-007-008 预警批处理执行结果指标回归

目标：
- 回归预警批处理执行结果的本地可观测性。
- 若当前仅有接口结果没有指标，补最小指标或正式说明，不接入外部平台。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`：为批量已读、批量状态更新响应补充 `metrics={total,success,failed,skipped}`。
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`：新增批量已读部分成功、批量状态非法失败的指标断言。
- `docs/quality/issue-007-008-alert-batch-result-metrics.md`、`docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：新增正式报告并完成 backlog 收口。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=AlertControllerTest" test`：红灯验证通过，失败原因为缺少 `$.data.metrics.total`。
- `cd backend; .\mvnw.cmd "-Dtest=AlertEvaluationServiceTest,AlertControllerTest" test`：通过，`Tests run: 46, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；预警批处理响应指标已补齐
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮只覆盖现有预警批处理接口响应指标，不提供外部监控采集、告警规则或面板。
- `metrics` 只包含整数计数，不包含失败原因、消息正文、Token、凭据或用户隐私。

---

Issue：ISSUE-005-004 库存与领料列表页生产化补强

目标：
- 补强库存台账列表与领料申请列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口；不改库存数量业务口径，不扩展后端接口语义。

修改范围摘要：
- `frontend-admin/src/pages/inventory/stock.vue`、`frontend-admin/src/pages/inventory/composables/useStockLedger.ts`：库存台账新增 URL query 恢复与同步，覆盖 `projectId`、`warehouseId`、`materialId`、`keyword`、`pageNo`、`pageSize`；补错误态、空态和重试入口。
- `frontend-admin/src/pages/requisition/index.vue`、`frontend-admin/src/pages/requisition/composables/useRequisitionList.ts`：领料申请列表新增 URL query 恢复与同步，覆盖 `projectId`、`warehouseId`、`approvalStatus`、`requisitionCode`、`pageNo`、`pageSize`；补错误态、空态和重试入口。
- `frontend-admin/src/pages/inventory/__tests__/stock-production.test.ts`、`frontend-admin/src/pages/requisition/__tests__/list-production.test.ts`：补充生产化守卫测试。
- `docs/quality/issue-005-004-inventory-requisition-list-production.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-005-004 从 Ready 收口为 Done。

验证命令摘要：
- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/inventory/__tests__/stock-production.test.ts src/pages/requisition/__tests__/list-production.test.ts`：通过，`3` 个文件、`7` 个用例全部通过。
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
- 本轮未跑全量前端测试，结论限于 ISSUE-005-004 指定库存/领料列表生产化范围。

---

Issue：ISSUE-005-005 分包与结算列表页生产化补强

目标：
- 补强分包任务列表与结算列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口；不改分包计量、结算审批或付款关联业务口径。

修改范围摘要：
- `frontend-admin/src/pages/subcontract/task.vue`：分包任务页新增 URL query 恢复与同步，覆盖 `projectId`、`status`、`keyword`、`pageNo`、`pageSize`；补错误态、空态和重试入口。
- `frontend-admin/src/pages/settlement/index.vue`：结算列表页新增 URL query 恢复与同步，覆盖 `projectId`、`settlementStatus`、`keyword`、`pageNo`、`pageSize`；补错误态、空态和重试入口。
- `frontend-admin/src/pages/subcontract/__tests__/task.test.ts`、`frontend-admin/src/pages/settlement/__tests__/index.test.ts`：补充生产化守卫测试。
- `docs/quality/issue-005-005-subcontract-settlement-list-production.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-005-005 从 Ready 收口为 Done。

验证命令摘要：
- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/subcontract/__tests__/task.test.ts src/pages/settlement/__tests__/index.test.ts`：通过，`3` 个文件、`10` 个用例全部通过。
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
- 本轮未跑全量前端测试，结论限于 ISSUE-005-005 指定分包/结算列表生产化范围。

---

Issue：ISSUE-005-006 预警与审批列表页生产化补强

目标：
- 补强预警列表与审批列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口。
- 不改预警规则、审批状态机或后端权限边界。

修改范围摘要：
- `frontend-admin/src/pages/alert/index.vue`、`frontend-admin/src/pages/alert/components/AlertTablePanel.vue`：预警列表新增 URL query 恢复与同步，覆盖 `keyword`、`projectId`、`alertDomain`、`ruleType`、`severity`、`isRead`、`processStatus`、`triggeredStart`、`triggeredEnd`、`onlyDefaultScope`、`pageNo`、`pageSize`；补错误态、空态和重试入口。
- `frontend-admin/src/pages/approval/todo.vue`：审批列表新增 URL query 恢复与同步，覆盖 `keyword`、`businessType`、`instanceStatus`、`startTime`、`endTime`、`pageNo`、`pageSize`；补错误态、空态和重试入口。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`、`frontend-admin/src/pages/approval/__tests__/ApprovalWorkList.test.ts`：补充生产化守卫测试。
- `docs/quality/issue-005-006-alert-approval-list-production.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-005-006 从 Ready 收口为 Done。

验证命令摘要：
- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/approval/__tests__/ApprovalWorkList.test.ts src/pages/alert/__tests__/index.test.ts`：通过，`3` 个文件、`25` 个用例全部通过。
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
- 本轮未跑全量前端测试，结论限于 ISSUE-005-006 指定预警/审批列表生产化范围。
