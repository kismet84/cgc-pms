# Ready Issues

## Ready 队列状态

v1.0 队列已封存到 [backlog 快照](../archive/v1.0/backlog-snapshot/ready-issues.md)。

`ISSUE-040-034`、`ISSUE-040-035`、`ISSUE-040-036`、`ISSUE-040-037`、`ISSUE-040-038` 已完成；本次 `启动迭代-5` 已达到 5 条上限。

`ISSUE-040-039`、阻塞修复 `ISSUE-047-001`、`ISSUE-040-040`～`ISSUE-040-055`、阻塞修复 `ISSUE-047-002` 与 `ISSUE-047-003` 已完成；`启动迭代-20` 已完成 20/20。站内通知的租户/用户隔离、已读幂等、SSE与通知铃契约已完成回归证明。

2026-07-19 手工补货 5 条已全部完成：`ISSUE-048-011`、`ISSUE-053-001`、`ISSUE-053-002`、`ISSUE-053-003`、`ISSUE-053-004`；Clean-room V2 M1 完整退出门通过，M2 须经产品决策与 Ready 补货后再实施。

2026-07-22 第53条主线M3已完成；`ISSUE-053-010～016`全部通过并完成治理收口。当前无M3 Ready。

2026-07-23 第53条主线M4的`ISSUE-053-017～022`已通过并收口；当前无M4 Ready。`ISSUE-053-023`保持Planned，须另行补货并获授权后方可实施。

### ISSUE-053-020：M4目标成本版本V2

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 目标成本 / 四路由迁移 / 版本、金额、权限与并发
状态：Done（2026-07-23，本地dev/test验收通过）
来源锚点：`docs/plans/第53条主线-M4-商务合约任务计划书-2026-07-22.md`的ISSUE-053-020；`ISSUE-053-019`通过事实；candidateEvidenceHead=c7ca210db53edece87124ac28945479093af3e49
存量问题键：[mainline:053-M4-020-COST-TARGET-VERSION]
Migration：需要
依赖：`ISSUE-053-017～019`已通过；复用商务共享契约、V2请求核心、现有目标成本Controller/Service及公共壳项目上下文；迁移只登记既有目标成本动作权限，不修改业务表结构。
风险等级：高
运行态要求：本地dev/test；禁止连接生产。写侧浏览器验收仅使用可识别测试数据并精确回滚。
Reviewer要求：独立复核项目/租户范围、普通角色动作权限、CAS、驳回重提、并发激活唯一性、历史只读与金额字符串边界。
归档报告：`docs/quality/ISSUE-053-020-M4目标成本版本V2验收报告.md`
最小回滚：回退四路由V2页面、契约/服务、精确后端并发根修、权限登记、测试和台账状态；017～019、Legacy、业务数据及正式入口保持不变。
目标：

- 将`/cost-target`、`/cost-target/index`、`/cost-target/create`、`/cost-target/:id/edit`迁移为真实Clean-room V2；根路由只作确定性重定向。
- 完成版本列表、创建、编辑、明细保存、提交、激活、删除与历史只读追溯；服务端保证同项目活动版本唯一。
- 投标成本、责任成本、目标成本全部按服务端十进制字符串回读和对账，前端不形成权威合计。
非目标：

- 不迁移M7成本科目中心，不复制Legacy UI，不新增平行金额账本、状态库、表格库、第二请求层或前端浮点权威计算。
- 不修改正式入口、生产环境或其他M4页面；不扩大既有目标成本业务状态。
允许修改：

- `packages/frontend-contracts/src/commercial.ts`
- `frontend-admin-v2/src/services/commercial.ts`
- `frontend-admin-v2/src/pages/commercial/CostTargetPage.vue`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/navigation/catalog.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/m4-cost-target.test.ts`
- `frontend-admin-v2/tests/unit/router.test.ts`
- `frontend-admin-v2/tests/unit/navigation.test.ts`
- `frontend-admin-v2/e2e/m4-cost-target.spec.ts`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `backend/src/main/java/com/cgcpms/cost/controller/CostTargetController.java`
- `backend/src/main/java/com/cgcpms/cost/service/CostTargetService.java`
- `backend/src/main/java/com/cgcpms/cost/handler/CostTargetWorkflowHandler.java`
- `backend/src/test/java/com/cgcpms/cost/**`
- `backend/src/main/resources/db/migration/V220__register_cost_target_action_permissions.sql`
- `scripts/demo/complete-project-v2/sql/150-role-test-accounts.sql`
- `scripts/demo/complete-project-v2/verify.ps1`
- `docs/plans/**`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-020-M4目标成本版本V2验收报告.md`
禁止修改：

- `frontend-admin/**`
- 其他未列明业务域后端与V2页面
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：

- 四路由由真实V2承接；台账从`60/27/0`变为`56/31/0`，重定向保留query/hash。
- 写动作按`cost:target:add/edit/delete/submit/activate`授权；只读角色前后端均不能写，普通目标成本角色可操作授权动作。
- 所有对象和明细按租户/项目失败关闭；客户端旧version、重复提交、并发明细保存、并发激活稳定冲突，不发生后写覆盖或双活动版本。
- 驳回后编辑仍走既有工作流重提；提交/活动版本保持只读，历史版本可查询不可静默覆盖。
- 页面具备loading、empty、error、403/404/409/422/500、重复点击防护、Abort与陈旧响应隔离；三视口无横向溢出，axe serious/critical为0。
- Ready lint、后端专项、契约/V2类型、V2单测、Lint、Clean-room边界、路由台账、构建、目标E2E、demo verify与`git diff --check`全部通过。
验证命令：

- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-020`
- `cd backend; .\mvnw.cmd '-Djacoco.skip=true' '-Dtest=CostTargetControllerTest,CostTargetWorkflowHandlerTest,TargetCostDynamicProfitClosedLoopIntegrationTest,CostTargetServiceConcurrencyTest' test`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit -- m4-commercial-contract-baseline.test.ts m4-cost-target.test.ts router.test.ts navigation.test.ts`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm generate:route-ledger`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm exec playwright test e2e/m4-cost-target.spec.ts`
- `pwsh -NoProfile -File scripts/demo/complete-project-v2/verify.ps1`
- `git diff --check`

### ISSUE-053-021：M4成本台账、核对与动态利润V2

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 成本与利润 / 四路由迁移 / 快照、金额、权限与并发
状态：Done（2026-07-23，本地dev/test验收通过）
来源锚点：`docs/plans/第53条主线-M4-商务合约任务计划书-2026-07-22.md`的ISSUE-053-021；`ISSUE-053-020`通过事实；candidateEvidenceHead=c7ca210db53edece87124ac28945479093af3e49
存量问题键：[mainline:053-M4-021-COST-PROFIT-CONTROL]
Migration：需要
依赖：`ISSUE-053-017～020`已通过；复用商务共享契约、V2请求核心、现有成本台账/汇总/控制Controller与公共壳项目上下文；迁移只登记成本汇总刷新权限，不修改业务表结构。
风险等级：高
运行态要求：本地dev/test；禁止连接生产。浏览器写侧仅使用可识别测试数据并精确回滚。
Reviewer要求：独立复核项目/租户范围、刷新写权限、历史快照不可变、预测/纠偏CAS、责任人项目成员、跨源金额字符串及纠偏合计并发。
归档报告：`docs/quality/ISSUE-053-021-M4成本台账核对与动态利润V2验收报告.md`
最小回滚：回退四路由V2页面、契约/服务、精确后端快照/CAS根修、权限登记、测试和台账状态；017～020、Legacy、成本科目中心、业务数据及正式入口保持不变。
目标：

- 将`/cost`、`/cost/ledger`、`/cost/summary`、`/cost/control`迁移为真实Clean-room V2；`/cost`仅确定性重定向台账。
- 成本台账提供服务端分页、汇总与详情；成本核对提供最新汇总、不可变历史与显式去重刷新；动态利润覆盖预测、确认、纠偏、提交、关闭及追溯。
- 投标、目标、责任、承诺、实际、预计剩余、完工预测与利润全部按服务端十进制字符串读取和回读，前端不形成权威合计。
非目标：

- 不迁移M7成本科目taxonomy/rules/scope/trace，不新增成本科目模型、平行账本、状态库、表格库、第二请求层或前端浮点权威计算。
- 不修改正式入口、生产环境、预算/产值页面或其他M4路由；不扩大既有成本状态机。
允许修改：

- `packages/frontend-contracts/src/commercial.ts`
- `frontend-admin-v2/src/services/commercial.ts`
- `frontend-admin-v2/src/pages/commercial/CostLedgerPage.vue`
- `frontend-admin-v2/src/pages/commercial/CostSummaryPage.vue`
- `frontend-admin-v2/src/pages/commercial/CostControlPage.vue`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/navigation/catalog.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/m4-costs.test.ts`
- `frontend-admin-v2/tests/unit/m4-costs-pages.test.ts`
- `frontend-admin-v2/tests/unit/router.test.ts`
- `frontend-admin-v2/tests/unit/navigation.test.ts`
- `frontend-admin-v2/e2e/m4-costs.spec.ts`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `backend/src/main/java/com/cgcpms/cost/controller/CostSummaryController.java`
- `backend/src/main/java/com/cgcpms/cost/service/CostSummaryService.java`
- `backend/src/main/java/com/cgcpms/cost/dto/CostControlModels.java`
- `backend/src/main/java/com/cgcpms/cost/service/CostControlService.java`
- `backend/src/main/java/com/cgcpms/cost/handler/CostCorrectiveWorkflowHandler.java`
- `backend/src/test/java/com/cgcpms/cost/**`
- `backend/src/main/resources/db/migration/V221__register_cost_summary_refresh_permission.sql`
- `scripts/demo/complete-project-v2/sql/150-role-test-accounts.sql`
- `scripts/demo/complete-project-v2/verify.ps1`
- `docs/plans/**`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-021-M4成本台账核对与动态利润V2验收报告.md`
禁止修改：

- `frontend-admin/**`
- `backend/src/main/java/com/cgcpms/cost/subject/**`
- 其他未列明业务域后端与V2页面
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：

- 四路由由真实V2承接；台账从`56/31/0`变为`52/35/0`，根重定向保留query/hash，台账生成器交叉核验V2路由与导航事实。
- 台账/详情只读且按服务端分页、汇总、项目/租户范围失败关闭；报告期转换为服务端日期窗口，详情不产生写请求。
- 汇总刷新使用独立`cost:summary:refresh`权限、审计、加载态和去重；历史日期快照不可改写，同日刷新保持单一当前快照。
- 预测/纠偏写动作强制客户端version/CAS；并发创建版本、更新、确认、提交、关闭及纠偏合计稳定冲突，不发生后写覆盖或超额节约。
- 纠偏责任人必须为目标项目有效成员；普通成本角色按动作权限可用，只读角色和项目外用户前后端均不能写或读取越权对象。
- 所有金额出口与写命令为DecimalString/BigDecimal，含超`2^53`、0、负数和小数边界；Map中的ID也以字符串稳定传输。
- 页面具备loading、empty、error、403/404/409/422/500、重复点击防护、Abort与陈旧响应隔离；三视口无横向溢出，axe serious/critical为0。
- Ready lint、后端专项、契约/V2类型、V2单测、Lint、Clean-room边界、路由台账、构建、目标E2E、demo verify与`git diff --check`全部通过。
验证命令：

- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-021`
- `cd backend; .\mvnw.cmd '-Djacoco.skip=true' '-Dtest=CostLedgerServiceTest,CostSummaryControllerTest,CostSummaryServiceTest,CostControlControllerTest,CostControlServiceConcurrencyTest,TargetCostDynamicProfitClosedLoopIntegrationTest,CostCorrectiveWorkflowHandlerTest' test`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit -- m4-commercial-contract-baseline.test.ts m4-costs.test.ts m4-costs-pages.test.ts router.test.ts navigation.test.ts`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm generate:route-ledger`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm exec playwright test e2e/m4-costs.spec.ts`
- `pwsh -NoProfile -File scripts/demo/complete-project-v2/verify.ps1`
- `git diff --check`

### ISSUE-053-022：M4项目预算与产值计量V2

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 预算与产值 / 双路由迁移 / 金额、附件、权限与并发
状态：Done（2026-07-23，本地dev/test验收通过）
来源锚点：`docs/plans/第53条主线-M4-商务合约任务计划书-2026-07-22.md`的ISSUE-053-022；`ISSUE-053-021`通过事实；candidateEvidenceHead=c7ca210db53edece87124ac28945479093af3e49
存量问题键：[mainline:053-M4-022-BUDGET-MEASUREMENT]
Migration：不需要
依赖：`ISSUE-053-017～021`已通过；复用商务共享契约、V2请求核心、现有预算/产值Controller、公共壳项目/报告期上下文及受控文件服务。
风险等级：高
运行态要求：本地dev/test；禁止连接生产。浏览器写侧仅使用可识别测试数据与文件并精确回滚。
Reviewer要求：独立复核预算项目/租户范围、版本CAS与驳回重提、可用额权威口径；产值期间/来源/申报/核定/结算状态机、真实附件事实、重复请求、金额与数量边界。
归档报告：`docs/quality/ISSUE-053-022-M4项目预算与产值计量V2验收报告.md`
最小回滚：回退双路由V2页面、契约/服务、精确预算CAS/工作流与产值附件根修、测试和台账状态；017～021、Legacy、业务数据及正式入口保持不变。
目标：

- 将`/budget`、`/production-measurement`迁移为真实Clean-room V2，复用公共壳项目/报告期选择器和服务端受权对象源。
- 预算覆盖列表、详情/编辑、明细、可用额、创建、更新、删除、提交及驳回后重提；版本/状态和权威余额由服务端判定。
- 产值覆盖计量期间、来源、计量单、内部提交、业主申报、业主核定/退回及结算追溯；阶段附件必须核对真实受控文件记录。

实施结果：已通过。两路由由真实Clean-room V2承接，台账达到`50/37/0`；预算CRUD、明细、可用额、全写version/CAS、驳回同实例重提，以及产值期间、来源、内部审批、业主申报、核定/退回与结算追溯闭环成立。公共项目/报告期服务端过滤、完整HTTP权限矩阵、真实CLEAN文件门禁和确定性文件/状态并发锁序均已验收。正式证据见`docs/quality/ISSUE-053-022-M4项目预算与产值计量V2验收报告.md`。
非目标：

- 不新增预算账本、产值算法、成本科目中心、自由附件层、状态库、表格库、第二请求层或前端金额乘价/累计权威计算。
- 不修改正式入口、生产环境、合同/成本页面或M7路由；不扩大既有预算和产值状态机。
允许修改：

- `packages/frontend-contracts/src/commercial.ts`
- `frontend-admin-v2/src/services/commercial.ts`
- `frontend-admin-v2/src/pages/commercial/BudgetPage.vue`
- `frontend-admin-v2/src/pages/commercial/ProductionMeasurementPage.vue`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/navigation/catalog.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/m4-budget-measurement.test.ts`
- `frontend-admin-v2/tests/unit/m4-budget-measurement-pages.test.ts`
- `frontend-admin-v2/tests/unit/router.test.ts`
- `frontend-admin-v2/tests/unit/navigation.test.ts`
- `frontend-admin-v2/e2e/m4-budget-measurement.spec.ts`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `backend/src/main/java/com/cgcpms/budget/controller/ProjectBudgetController.java`
- `backend/src/main/java/com/cgcpms/budget/service/ProjectBudgetService.java`
- `backend/src/main/java/com/cgcpms/budget/handler/ProjectBudgetWorkflowHandler.java`
- `backend/src/main/java/com/cgcpms/measurement/controller/ProductionMeasurementController.java`
- `backend/src/main/java/com/cgcpms/measurement/service/ProductionMeasurementService.java`
- `backend/src/main/java/com/cgcpms/measurement/dto/MeasurementModels.java`
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/main/java/com/cgcpms/workflow/**`
- `backend/src/test/java/com/cgcpms/budget/**`
- `backend/src/test/java/com/cgcpms/measurement/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/workflow/**`
- `scripts/demo/complete-project-v2/sql/150-role-test-accounts.sql`
- `scripts/demo/complete-project-v2/verify.ps1`
- `docs/plans/**`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-022-M4项目预算与产值计量V2验收报告.md`
禁止修改：

- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- 其他未列明业务域后端与V2页面
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：

- 两路由由真实V2承接；台账从`52/35/0`变为`50/37/0`，台账生成器交叉核验V2路由与导航事实。
- 预算所有写动作强制客户端version/CAS；驳回后编辑保持可重提状态并复用原实例；同项目版本/活动状态、明细和可用额按服务端失败关闭，前端不重算余额。
- 产值期间、来源、计量、内部提交、业主申报、核定/退回与结算追溯按租户/项目/合同/状态失败关闭；重复提交、核定、退回稳定幂等或冲突。
- 附件数量和阶段门禁只承认当前业务对象的真实CLEAN文件记录；客户端计数、跨项目/跨业务文件、上传失败或缺文件不能通过提交、核定或结算。
- 预算、占用、消耗、可用、申报、核定、结算金额为DecimalString/BigDecimal；数量为受控十进制字符串，数量乘价与累计金额由服务端返回。
- 普通预算/计量角色按动作权限可用；只读角色和项目外用户前后端均不能写或读取越权对象；公共项目/报告期切换产生真实服务端查询。
- 页面具备loading、empty、error、403/404/409/422/500、重复点击防护、Abort与陈旧响应隔离；三视口无横向溢出，axe serious/critical为0。
- Ready lint、后端预算/产值/文件/工作流专项、契约/V2类型、V2单测、Lint、Clean-room边界、路由台账、构建、目标E2E、demo verify与`git diff --check`全部通过。
验证命令：

- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-022`
- `cd backend; .\mvnw.cmd '-Djacoco.skip=true' '-Dtest=ProjectBudgetControllerTest,ProjectBudgetIntegrationTest,ProjectBudgetConcurrencyTest,ProductionMeasurementControllerTest,ProductionMeasurementClosedLoopIntegrationTest,ProductionMeasurementConcurrencyTest,BusinessObjectAuthorizerTest,FileControllerAuthorizationTest,WorkflowSubmitServiceTest' test`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit -- m4-commercial-contract-baseline.test.ts m4-budget-measurement.test.ts m4-budget-measurement-pages.test.ts router.test.ts navigation.test.ts`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm generate:route-ledger`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm exec playwright test e2e/m4-budget-measurement.spec.ts`
- `pwsh -NoProfile -File scripts/demo/complete-project-v2/verify.ps1`
- `git diff --check`

### ISSUE-053-017：M4商务契约、金额与权限金丝雀

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 商务合约 / 稳定契约 / 金额与权限金丝雀
目标：

- 冻结合同、变更、投标成本、目标成本、成本台账/汇总/控制、预算和产值计量的稳定DTO、只读API路径、金额字段与查询权限基线。
- 复用V2请求核心，建立合同台账、合同详情和成本汇总历史三条只读金丝雀请求，并以可重复测试证明项目/租户范围、AbortSignal和十进制字符串边界。
- 为后续18条商务合约路由迁移提供唯一共享契约，不复制Legacy UI状态或建立第二请求层。
非目标：
- 不迁移、渲染或切换任何M4路由，不修改路由迁移台账状态。
- 除“允许修改”中精确列明的金额出口、项目范围守卫与负向测试外，不修改其他后端、数据库、Legacy页面、正式入口、业务数据、权限码或状态机；不新增依赖、缓存、Store、业务mock或前端权威金额计算。
- 不纳入`/cost/subject`及其taxonomy、rules、scope、trace五条M7路由。
允许修改：
- `packages/frontend-contracts/src/commercial.ts`
- `packages/frontend-contracts/src/index.ts`
- `frontend-admin-v2/src/services/commercial.ts`
- `frontend-admin-v2/tests/unit/m4-commercial-contract-baseline.test.ts`
- `backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`
- `backend/src/main/java/com/cgcpms/variation/service/VarOrderService.java`
- `backend/src/main/java/com/cgcpms/bid/service/BidCostService.java`
- `backend/src/main/java/com/cgcpms/cost/service/CostTargetService.java`
- `backend/src/main/java/com/cgcpms/cost/service/CostLedgerService.java`
- `backend/src/main/java/com/cgcpms/budget/service/ProjectBudgetService.java`
- `backend/src/main/java/com/cgcpms/cost/vo/CostTargetVO.java`
- `backend/src/main/java/com/cgcpms/cost/vo/CostTargetItemVO.java`
- `backend/src/main/java/com/cgcpms/cost/service/CostControlService.java`
- `backend/src/main/java/com/cgcpms/measurement/service/ProductionMeasurementService.java`
- `backend/src/test/java/com/cgcpms/contract/**`
- `backend/src/test/java/com/cgcpms/variation/**`
- `backend/src/test/java/com/cgcpms/bid/**`
- `backend/src/test/java/com/cgcpms/cost/**`
- `backend/src/test/java/com/cgcpms/budget/**`
- `backend/src/test/java/com/cgcpms/measurement/**`
- `docs/plans/第53条主线-M4-商务合约任务计划书-2026-07-22.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-017-M4商务契约金额与权限金丝雀验收报告.md`
禁止修改：
- 其他未在“允许修改”中逐项列明的后端文件
- `frontend-admin/**`
- `frontend-admin-v2/src/pages/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/navigation/**`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `docs/ui-v2/**`
- `backend/src/main/resources/db/migration/**`
- `scripts/demo/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- DTO字段、枚举、查询参数与当前九个后端Controller、响应VO及Legacy类型交叉一致；未知字段不猜测，金额、税额、成本、利润、预算和产值金额统一为规范十进制字符串。
- 合同台账、合同详情和成本汇总历史只访问既有GET端点；query安全编码，空筛选不发送，AbortSignal完整传递，查询不产生POST/PUT/PATCH/DELETE。
- 页面查询权限基线固定为`contract:query`、`variation:order:query`、`bid:query`、`cost:target:query`、`cost:ledger:query`、`cost:summary:view`、`cost:control:query`、`budget:query`、`measurement:query`；不实现管理员或前端范围兜底。
- 权威金额字段不得声明为`number`或经`Number`、`parseFloat`、隐式算术处理；税率和数量须显式标注非金额边界，不在前端形成权威金额。
- 契约与服务不得依赖Vue、Pinia、DOM、CSS、Legacy源码或本地业务mock；不产生新V2页面、路由或路由台账变化。
- Ready lint、契约类型、目标单测、V2类型、Lint、Clean-room边界、构建和`git diff --check`全部通过。
状态：Done（2026-07-22，本地验收通过）
来源锚点：`docs/plans/第53条主线-M4-商务合约任务计划书-2026-07-22.md`的ISSUE-053-017；`docs/quality/第53条主线-M3-项目履约全量退出门验收报告.md`的M3本地正式收口通过事实；`docs/product-intelligence/evolution-decision.md`的`PI-2026-07-18-02`；candidateEvidenceHead=c7ca210db53edece87124ac28945479093af3e49
存量问题键：[mainline:053-M4-017-COMMERCIAL-CONTRACT-MONEY-GATE]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-017`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit -- m4-commercial-contract-baseline.test.ts`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm build`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-017-M4商务契约金额与权限金丝雀验收报告.md`
Migration：不需要
依赖：M3全量退出门已通过；当前生成台账为`68/19/0`；九个商务域Controller、Legacy API模块及现有金额类型已核实；本Issue是M4唯一实施金丝雀，通过后方可补充`ISSUE-053-018`。
风险等级：高
运行态要求：无；仅执行契约、请求构造、静态与单元验证，不连接数据库或生产，不创建业务数据。
Reviewer要求：独立交叉核对九个Controller/VO、Legacy类型与权限菜单；确认金额不转浮点、项目/租户范围不扩大、signal不丢失、查询零写、无Legacy UI依赖，并核实五条成本科目路由仍归M7。
最小回滚：回退商务共享契约、薄服务、精确后端金额/项目范围修正、目标测试及本Issue治理回写；M0～M3、Legacy入口、数据库和`68/19/0`路由台账保持不变。

### ISSUE-053-018：M4合同台账与全生命周期V2

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 商务合约 / 五路由迁移 / 金额、权限、并发与原子保存
目标：

- 将`/contract`、`/contract/ledger`、`/contract/create`、`/contract/:id`、`/contract/:id/edit`五条路由迁移为真实Clean-room V2；`/contract`仅确定性重定向台账。
- 复用017共享契约和V2请求核心，完成服务端分页/KPI/筛选、独立详情、复合原子保存、提交审批、删除、清单、付款条款与审批历史。
- 根修启动核验发现的合同子资源项目范围、复合更新受保护字段、客户端版本并发、合同金额对账及工作流重提状态缺口。
非目标：

- 不修改数据库迁移、Legacy页面、正式入口、生产环境、合同变更、投标、目标成本或M7成本科目页面。
- 不新增依赖、状态库、表格库、第二请求层、前端权威金额计算或自由附件能力；合同附件不在018计划范围。
- 复合保存是合同聚合命令，`contract:add/edit`为其权威权限；不额外叠加子资源CRUD权限。子资源细粒度权限继续只约束独立子资源端点。
允许修改：

- `packages/frontend-contracts/src/commercial.ts`
- `frontend-admin-v2/src/services/commercial.ts`
- `frontend-admin-v2/src/pages/commercial/ContractPage.vue`
- `frontend-admin-v2/src/pages/commercial/ContractForm.vue`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/m4-contracts.test.ts`
- `frontend-admin-v2/tests/unit/router.test.ts`
- `frontend-admin-v2/e2e/m4-contracts.spec.ts`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `backend/src/main/java/com/cgcpms/contract/controller/CtContractController.java`
- `backend/src/main/java/com/cgcpms/contract/entity/CtContractItem.java`
- `backend/src/main/java/com/cgcpms/contract/entity/CtContractPaymentTerm.java`
- `backend/src/main/java/com/cgcpms/contract/handler/ContractWorkflowHandler.java`
- `backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`
- `backend/src/main/java/com/cgcpms/contract/service/CtContractItemService.java`
- `backend/src/main/java/com/cgcpms/contract/service/CtContractPaymentTermService.java`
- `backend/src/main/java/com/cgcpms/contract/vo/CtContractVO.java`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowSubmitService.java`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowBusinessAccessValidator.java`
- `backend/src/test/java/com/cgcpms/contract/**`
- `backend/src/test/java/com/cgcpms/workflow/**`
- `docs/plans/第53条主线-M4-商务合约任务计划书-2026-07-22.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-018-M4合同台账与全生命周期V2验收报告.md`
禁止修改：

- 其他未在“允许修改”中逐项列明的后端和前端文件
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `scripts/demo/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：

- 五路由均由真实V2页面承接，`/contract`保留query/hash确定性重定向；生成台账由`68/19/0`变为`63/24/0`，无手写台账漂移或Shell占位。
- 台账使用服务端分页、项目范围、KPI和筛选；详情独立URL展示合同头、清单、付款条款、审批历史，不产生隐式写请求。
- 新建/编辑只调用复合原子端点；头、清单、条款任一失败整体回滚。保存成功后权威回读；保存后提交失败时明确保留草稿，不伪装整体成功。
- 创建、编辑、提交、删除严格绑定`contract:add/edit/submit/delete`，只读身份不显示且后端拒绝写动作；子资源所有独立读写按父合同项目范围失败关闭。
- 复合更新不能覆盖合同编号、已付额、结算额、成本生成标记、审批状态、租户和创建事实；旧`version`更新/提交稳定冲突，不能后写覆盖或启动重复工作流。
- 服务端校验合同金额、税额、不含税额、清单合计、付款条款合计、付款比例及日期边界；金额和比例使用十进制字符串/BigDecimal，不使用JavaScript浮点形成权威结果。
- 通用工作流合同提交与合同专用提交共享对象范围、可提交状态和handler状态；驳回/撤回重提进入`APPROVING`并锁定编辑。
- 页面具备loading、empty、error、403/404/409/422/500、重复点击防护、Abort与陈旧响应隔离；1440/1024/390三视口无横向溢出，axe serious/critical为0，控制台无error。
- Ready lint、后端合同/工作流专项、契约/V2类型、V2单测、Lint、Clean-room边界、路由台账、构建、目标E2E和`git diff --check`全部通过。
状态：Done（2026-07-22，本地验收通过）
来源锚点：`docs/plans/第53条主线-M4-商务合约任务计划书-2026-07-22.md`的ISSUE-053-018；`ISSUE-053-017`通过事实；`docs/product-intelligence/project-map.md`的M4金丝雀事实；candidateEvidenceHead=b6e46f9e37424f3f70889ccffb86bc2d662f6922
存量问题键：[mainline:053-M4-018-CONTRACT-LIFECYCLE]
验证命令：

- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-018`
- `cd backend; .\mvnw.cmd '-Djacoco.skip=true' '-Dtest=CtContractServiceTest,CtContractControllerTest,CtContractItemServiceTest,CtContractPaymentTermServiceTest,ContractWorkflowHandlerTest,WorkflowSubmitServiceTest,ContractApprovalIntegrationTest' test`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit -- m4-commercial-contract-baseline.test.ts m4-contracts.test.ts router.test.ts`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm generate:route-ledger`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm exec playwright test e2e/m4-contracts.spec.ts`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-018-M4合同台账与全生命周期V2验收报告.md`
Migration：不需要
依赖：`ISSUE-053-017`已通过；017未提交diff必须原样保留并与018一并验收；合作方候选复用既有`GET /partners`，写角色运行态验收必须具备`partner:query`，项目候选复用当前认证用户可见项目选项。
风险等级：高
运行态要求：本地dev/test；禁止连接生产。若执行真实写侧浏览器验收，只能使用可识别测试数据并在证据完成后精确回滚。
Reviewer要求：独立安全复核租户/项目、聚合权限、受保护字段、并发、工作流和金额；独立测试复核五路由、失败恢复、三视口、权限矩阵与真实HTTP金额字符串。
最小回滚：回退合同V2页面、路由、契约/服务、精确后端根修、测试和五条台账状态；017契约、M0～M3、数据库、Legacy和正式入口保持不变。

### ISSUE-053-019：M4变更签证与投标成本V2

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 商务合约 / 三路由迁移 / 跨域状态、金额、权限与幂等
目标：

- 将`/variation`、`/variation/order`、`/bid-cost`三条路由迁移为真实Clean-room V2；`/variation`仅确定性重定向变更台账。
- 复用017共享契约和V2请求核心，完成变更列表、详情/编辑、明细保存、提交审批、业主申报/核定/退回及追溯；完成投标成本列表、详情、新增、编辑、删除和中标/未中标。
- 根修启动核验发现的投标状态先查后写竞态、未中标费用跨租户更新条件、变更重复业主申报/核定幂等与项目范围缺口；跨域动作失败关闭且不产生部分副作用。
非目标：

- 不修改数据库迁移、Legacy页面、正式入口、生产环境、目标成本编辑、成本科目映射/转入页面、合同编辑或其他M4路由。
- 不新增依赖、状态库、表格库、第二请求层、前端权威金额计算或自由附件管理；附件仅复用既有受控上传能力及后端附件ID契约。
- 中标只绑定用户可见项目并保留既有投标费用事实；目标成本转入继续归020/M7，不在本Issue扩展。
允许修改：

- `packages/frontend-contracts/src/commercial.ts`
- `frontend-admin-v2/src/services/commercial.ts`
- `frontend-admin-v2/src/pages/commercial/VariationPage.vue`
- `frontend-admin-v2/src/pages/commercial/BidCostPage.vue`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/navigation/catalog.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/m4-variation.test.ts`
- `frontend-admin-v2/tests/unit/m4-bid-cost.test.ts`
- `frontend-admin-v2/tests/unit/router.test.ts`
- `frontend-admin-v2/e2e/m4-variation-bid.spec.ts`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `backend/src/main/java/com/cgcpms/variation/controller/VarOrderController.java`
- `backend/src/main/java/com/cgcpms/variation/dto/VariationClaimModels.java`
- `backend/src/main/java/com/cgcpms/variation/handler/VarOrderWorkflowHandler.java`
- `backend/src/main/java/com/cgcpms/variation/service/VarOrderService.java`
- `backend/src/main/java/com/cgcpms/variation/vo/VarOrderVO.java`
- `backend/src/main/java/com/cgcpms/variation/vo/VarOrderItemVO.java`
- `backend/src/main/java/com/cgcpms/bid/service/BidCostService.java`
- `backend/src/main/java/com/cgcpms/contract/service/CtContractChangeService.java`
- `backend/src/main/java/com/cgcpms/file/auth/BusinessObjectAuthorizer.java`
- `backend/src/main/java/com/cgcpms/file/controller/FileController.java`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowBusinessAccessValidator.java`
- `backend/src/test/java/com/cgcpms/variation/**`
- `backend/src/test/java/com/cgcpms/bid/**`
- `backend/src/test/java/com/cgcpms/contract/CtContractChangeServiceTest.java`
- `backend/src/test/java/com/cgcpms/contract/handler/CtContractChangeWorkflowHandlerTest.java`
- `backend/src/test/java/com/cgcpms/file/BusinessObjectAuthorizerTest.java`
- `backend/src/test/java/com/cgcpms/file/FileControllerAuthorizationTest.java`
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
- `backend/src/test/java/com/cgcpms/workflow/**`
- `backend/src/main/resources/db/migration/V219__register_variation_action_permissions.sql`
- `scripts/demo/complete-project-v2/sql/150-role-test-accounts.sql`
- `scripts/demo/complete-project-v2/verify.ps1`
- `docs/plans/第53条主线-M4-商务合约任务计划书-2026-07-22.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-019-M4变更签证与投标成本V2验收报告.md`
禁止修改：

- 其他未在“允许修改”中逐项列明的后端和前端文件
- `frontend-admin/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：

- 三路由均由真实V2页面承接，`/variation`保留query/hash确定性重定向；生成台账由`63/24/0`变为`60/27/0`，无手写台账漂移或Shell占位。
- 变更列表按服务端分页及项目范围筛选；详情/编辑独立工作区覆盖头信息、明细、业主申报记录与追溯，GET不产生隐式写请求。
- 变更新增/编辑/删除/明细保存/提交分别绑定`variation:order:add/edit/delete/item:edit/submit`，业主申报/核定/追溯分别绑定`variation:owner:submit`、`variation:owner:review`、`variation:trace`；只读身份不显示且后端拒绝写动作。
- 变更金额、明细金额、申报金额和核定金额使用十进制字符串/BigDecimal；前端不得直接改写合同当前额，核定后必须权威回读；退回或重复核定不新增合同变更、不重复调整合同金额。
- 变更重复提交、重复业主申报、重复核定及并发请求稳定幂等或冲突；租户、项目、合同与合作方范围失败关闭，任一跨域步骤失败整体回滚。
- 投标成本覆盖服务端分页/筛选、详情、新增、编辑、删除、选择可见项目中标及未中标；动作严格绑定`bid:add/edit/delete/status`，只读身份不能写。
- BIDDING到WON/LOST、编辑和删除使用服务端条件写；并发竞争最多一个成功。LOST费用核销具备租户条件，重复请求或跨租户同sourceId不产生额外变化；WON不能绑定无权项目。
- 页面具备loading、empty、error、403/404/409/422/500、重复点击防护、Abort与陈旧响应隔离；1440/1024/390三视口无横向溢出，axe serious/critical为0，控制台无error。
- 项目经理取得变更查询与既有变更动作，商务经理取得变更及投标查询/动作；权限码通过V219登记并授权，demo脚本只绑定测试账号，不以管理员或仅本地临时权限替代正式权限模型。
- 变更附件上传/查询按业务动作权限、项目范围和阶段共同授权，不泛授通用`file:*`；现场证据、业主申报、核定/退回附件的阶段与时序门禁均有负向测试。
- Ready lint、后端变更/投标专项、契约/V2类型、V2单测、Lint、Clean-room边界、路由台账、构建、目标E2E、demo verify和`git diff --check`全部通过。
状态：Done（2026-07-22，本地dev/test验收通过）
来源锚点：`docs/plans/第53条主线-M4-商务合约任务计划书-2026-07-22.md`的ISSUE-053-019；`ISSUE-053-017`与`ISSUE-053-018`通过事实；`docs/product-intelligence/project-map.md`的M4合同迁移事实；candidateEvidenceHead=b6e46f9e37424f3f70889ccffb86bc2d662f6922
存量问题键：[mainline:053-M4-019-VARIATION-BID]
验证命令：

- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-019`
- `cd backend; .\mvnw.cmd '-Djacoco.skip=true' '-Dtest=VarOrderServiceTest,VarOrderControllerMockMvcTest,VariationClaimClosedLoopIntegrationTest,VarOrderWorkflowHandlerTest,BidCostServiceTest,BidCostServiceConcurrencyTest,BidCostControllerTest,CostSubjectV2ServiceIntegrationTest,CtContractChangeServiceTest,CtContractChangeWorkflowHandlerTest,BusinessObjectAuthorizerTest,FileControllerAuthorizationTest,FileServiceTest,WorkflowSubmitServiceTest' test`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit -- m4-commercial-contract-baseline.test.ts m4-contracts.test.ts m4-variation.test.ts m4-bid-cost.test.ts router.test.ts`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm generate:route-ledger`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm exec playwright test e2e/m4-variation-bid.spec.ts`
- `pwsh -NoProfile -File scripts/demo/complete-project-v2/verify.ps1 -Environment dev -Database cgc_pms_demo_v2 -MySqlContainer cgc-pms-mysql-dev`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-019-M4变更签证与投标成本V2验收报告.md`
Migration：需要
依赖：`ISSUE-053-017`与`ISSUE-053-018`已通过；其未提交diff必须原样保留并与019一并验收。变更和中标项目候选复用当前认证用户可见项目选项；普通商务角色权限必须由可重复demo脚本提供。
风险等级：高
运行态要求：本地dev/test；禁止连接生产。真实写侧浏览器验收仅使用可识别测试数据，证据完成后精确回滚。
Reviewer要求：独立安全复核租户/项目/合同/合作方范围、权限、跨域事务、状态CAS、重复请求和金额；独立测试复核三路由、失败恢复、三视口、普通/只读角色矩阵、真实HTTP字符串金额及核定后合同权威回读。
最小回滚：回退变更/投标V2页面、路由、契约/服务、精确后端根修、demo权限、测试和三条台账状态；017/018、M0～M3、数据库迁移、Legacy和正式入口保持不变。

### ISSUE-053-010：M3项目契约与只读请求基线

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 项目履约 / 共享契约 / 只读请求金丝雀
目标：

- 冻结项目、项目总览和项目成员的稳定DTO及只读API路径。
- 复用V2请求核心，建立安全编码、空筛选省略和可取消请求基线。
非目标：
- 不渲染或接入M3页面，不修改路由台账状态。
- 不修改后端、数据库、Legacy、正式入口或业务数据；不新增依赖、缓存、Store或请求层。
允许修改：
- `packages/frontend-contracts/src/project.ts`
- `frontend-admin-v2/src/services/projects.ts`
- `frontend-admin-v2/tests/unit/m3-project-request-baseline.test.ts`
- `docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-010-M3项目契约与只读请求基线验收报告.md`
禁止修改：
- `backend/**`
- `frontend-admin/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/pages/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- DTO与`PmProjectVO`、`ProjectOverviewVO`、`PmProjectMemberVO`当前返回字段一致，金额保持字符串。
- 项目分页、详情、总览和成员分页只访问既有端点；query安全编码，空筛选不发送，AbortSignal完整传递。
- 不实现权限兜底、租户全量回退、业务mock、缓存或页面状态。
- Ready lint、契约类型、目标单测、V2类型、Lint、Clean-room边界、构建和`git diff --check`通过。
状态：Done
来源锚点：`docs/product-intelligence/evolution-decision.md`的`PI-2026-07-18-02`；`docs/product-intelligence/project-map.md`的M2退出事实；`docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`
存量问题键：[mainline:053-M3-010-PROJECT-CONTRACT-REQUEST]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-010`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit -- m3-project-request-baseline.test.ts`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm build`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-010-M3项目契约与只读请求基线验收报告.md`
Migration：不需要
依赖：M2全量退出门已通过；本Issue是M3唯一实施金丝雀，通过后方可补充首个页面迁移Ready。
风险等级：高
运行态要求：无；仅执行契约、请求构造和静态/单元验证，不连接生产。
Reviewer要求：交叉核对后端Controller/VO、Legacy类型和V2契约；确认项目范围不扩大、金额不转浮点、signal不丢失、无Legacy UI依赖。
最小回滚：回退项目契约、薄服务、目标测试及M3治理回写；M0～M2保持不变。

完成结果：项目、总览、成员契约与只读请求基线通过；19个V2单测文件88项、契约/V2类型、Lint、Clean-room、构建和差异检查全绿。未接页面、未改路由台账。

### ISSUE-053-011：M3项目对象工作区五路由迁移

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 项目履约 / 项目对象 / 权限与金额
目标：

- 迁移项目根重定向、列表、总览、成员和编辑五个路由，形成项目对象上下文和受控写侧闭环。
- 保持项目范围、权限、字典、金额字符串、状态机、query和深链与后端事实一致。
非目标：
- 不迁移计划、日报、质量、技术或收尾页面，不修改后端、数据库、Legacy或正式入口。
- 不新增依赖、全局Store、请求层、缓存或前端权威金额/状态推导。
允许修改：
- `packages/frontend-contracts/src/project.ts`
- `frontend-admin-v2/src/services/projects.ts`
- `frontend-admin-v2/src/pages/projects/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/navigation/catalog.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/**`
- `frontend-admin-v2/e2e/m3-projects.spec.ts`
- `backend/src/main/java/com/cgcpms/project/entity/PmProjectMember.java`（仅用户专项授权的服务端派生字段校验修复）
- `backend/src/test/java/com/cgcpms/project/PmProjectMemberControllerValidationTest.java`
- `backend/src/test/java/com/cgcpms/project/PmProjectMemberServiceTest.java`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-011-M3项目对象工作区验收报告.md`
- `docs/未来开发计划.md`
禁止修改：
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 五路由均为真实V2页面或受控重定向，台账达到`73/14/0`；无Shell占位、Legacy UI依赖或本地业务mock。
- 项目列表/详情/总览只返回当前身份可见项目；未知、跨项目和无权ID失败关闭，快速切换不回写陈旧项目。
- 创建、编辑/归档、提交、状态转换、删除分别遵守`project:add/edit/submit/status`与`SUPER_ADMIN`；成员读/增/改/删权限严格分离，按钮隐藏不替代后端403。
- 合同金额、目标成本和总览金额保持十进制字符串；写侧成功、409或业务拒绝后回读权威事实，不乐观改金额/状态。
- ADMIN、普通查询、成员只读、无权、未登录身份；1440/1024/390、axe、控制台、query/hash、刷新、深链和失败恢复通过。
- Ready lint、项目后端专项、V2单测/类型/Lint/Clean-room/台账/构建/包体、live E2E与`git diff --check`通过。
状态：Done
来源锚点：`docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`的ISSUE-053-011；`docs/product-intelligence/evolution-decision.md`的`PI-2026-07-18-02`；`ISSUE-053-010`金丝雀通过事实
存量问题键：[mainline:053-M3-011-PROJECT-WORKSPACE]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-011`
- `cd backend; .\mvnw.cmd "-Dtest=PmProjectControllerTest,PmProjectArchiveTest,ProjectOverviewServiceTest,ProjectMemberServiceTest" test`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm check:bundle-size`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-011-M3项目对象工作区验收报告.md`
Migration：不需要
依赖：`ISSUE-053-010`已通过；实施前必须核实本地demo存在项目查询、成员只读、项目写侧和无权身份，缺失时保持Ready并判`需要确认`。
风险等级：高
运行态要求：本地V2、Legacy、后端和`cgc_pms_demo_v2`健康；只在dev/demo受控项目执行最小写侧，不连接生产。
Reviewer要求：独立核对五路由计数、Controller权限、跨项目拒绝、成员分权、金额字符串、状态回读、三视口、axe、控制台和零悬空。
最小回滚：五路由恢复M2后的占位/Legacy状态，回退项目页面、写侧服务、契约增量、测试和台账；保留`ISSUE-053-010`只读基线，无数据库回滚。

完成结果：五路由达到`V2_ACCEPTED`，台账`73/14/0`；项目与成员受控写侧、真实ADMIN/query-only/member-readonly/无权/匿名、三视口、axe、旧响应隔离、故障恢复和金额字符串通过。服务端派生成员字段的错误前置校验已最小修复，Service继续强制覆盖租户/项目。收口时登记的成员删除后重新加入P1已于2026-07-21通过恢复逻辑删除原记录闭环。

### ISSUE-053-012：M3项目计划与现场日报双路由迁移

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 项目履约 / 计划日报 / 并发与附件
目标：

- 迁移`/project-schedule`与`/site/daily-log`，闭合基线计划、WBS、月周计划、日报实绩、偏差快照、纠偏、日报正文与附件。
- 保持权限、项目范围、状态机、并发令牌、写后权威回读及部分权限零请求与后端事实一致。
非目标：
- 不迁移质量安全、技术管理或竣工收尾；不切换正式入口，不发布生产。
- 不新增数据库迁移、依赖、全局Store、前端业务推导或Legacy UI复用。
允许修改：
- `backend/src/main/java/com/cgcpms/schedule/**`
- `backend/src/main/java/com/cgcpms/site/**`
- `backend/src/test/java/com/cgcpms/schedule/**`
- `backend/src/test/java/com/cgcpms/sitedaily/**`
- `frontend-admin/src/api/modules/projectSchedule.ts`
- `frontend-admin/src/api/modules/site-daily-log.ts`
- `frontend-admin/src/api/modules/__tests__/projectSchedule.test.ts`
- `frontend-admin/src/pages/project-schedule/index.vue`
- `frontend-admin/src/pages/site/daily-log.vue`
- `frontend-admin/src/types/site-daily-log.ts`
- `packages/frontend-contracts/src/delivery.ts`
- `packages/frontend-contracts/src/index.ts`
- `frontend-admin-v2/src/services/request.ts`
- `frontend-admin-v2/src/services/delivery.ts`
- `frontend-admin-v2/src/pages/delivery/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/**`
- `frontend-admin-v2/e2e/m3-delivery.spec.ts`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-012-M3项目计划与现场日报验收报告.md`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 两路由均为真实V2页面，台账达到`71/16/0`；无Shell占位、Legacy UI依赖或本地业务mock。
- 计划读、维护、提交、进度、纠偏及日报读写权限严格分离；无权限按钮隐藏且零请求，后端403仍为最终边界。
- WBS仅支持单前置FS；DRAFT/REJECTED可编辑，提交后只读；月周计划、快照、纠偏及日报单向状态机与后端一致。
- WBS/期间项携带expectedVersion，日报编辑携带expectedUpdatedAt；陈旧写、重复提交、跨项目及部分成功均fail-close并回读权威事实。
- `SITE_DAILY_LOG`附件仅DRAFT上传/删除，SUBMITTED不可变；FormData不设置JSON Content-Type，上传中断可恢复。
- 质量安全权限缺失时零请求且不阻断日报详情；进度权限缺失时零请求且正文只读事实仍可用。
- ADMIN、计划query-only、计划维护、日报读写、无权、未登录身份；1440/1024/390、键盘、axe、控制台、刷新、深链和失败恢复通过。
- Ready lint、后端专项、Legacy兼容、契约/V2单测/类型/Lint/Clean-room/台账/构建/包体、live E2E与`git diff --check`通过。
状态：Done
来源锚点：`docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`的ISSUE-053-012；`docs/product-intelligence/evolution-decision.md`的`PI-2026-07-18-02`；`ISSUE-053-011`通过事实
存量问题键：[mainline:053-M3-012-SCHEDULE-DAILY]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-012`
- `cd backend; .\mvnw.cmd "-Dtest=ProjectScheduleClosedLoopIntegrationTest,ProjectScheduleControllerPermissionTest,SiteDailyLogServiceTest,SiteDailyLogControllerTest,BusinessObjectAuthorizerTest" test`
- `cd frontend-admin; pnpm exec vitest run src/api/modules/__tests__/projectSchedule.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm check:bundle-size`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-012-M3项目计划与现场日报验收报告.md`
Migration：不需要
依赖：`ISSUE-053-011`已通过；补货前置阻塞已通过复用现有version、updated_at、行锁及唯一键完成修复与目标回归；live写侧仅使用本地`cgc_pms_demo_v2`受控身份与可识别数据。
风险等级：高
运行态要求：本地V2、Legacy、后端、MySQL、Redis和MinIO健康；身份及验收数据只在dev/demo创建并可精确回滚，不连接生产。
Reviewer要求：独立安全复核已确认权限、并发和令牌修复路线；完成后独立测试复核两路由、陈旧写、重复提交、附件不可变、权限零请求、三视口、axe和台账。
最小回滚：两路由恢复`LEGACY_ONLY`，回退V2页面/服务/契约、FormData增量及兼容性后端/Legacy改动；删除精确标记的本地验收身份与数据；无数据库结构回滚。

完成结果：两路由达到`V2_ACCEPTED`，台账`71/16/0`；后端组合34项、V2单测98项、Legacy兼容、静态/构建门禁和本地live验收通过。独立复核发现的纠偏审批并发重复修订风险已通过行锁、条件更新和双线程测试闭环；trace契约保留后端真实数组。受控身份与数据已精确回滚。

### ISSUE-053-013：M3质量安全整改闭环迁移

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 项目履约 / 质量安全 / 权限与附件
目标：

- 迁移`/quality-safety`，闭合质量计划、检查、问题、整改、复检、后果与反向追溯。
- 保持项目范围、合同关联、状态机、阶段证据附件、权限分离和写后权威回读与后端事实一致。
非目标：
- 不迁移技术管理或竣工收尾；不切换正式入口，不发布生产。
- 不新增数据库迁移、依赖、全局Store、前端业务状态推导或Legacy UI复用。
允许修改：
- `backend/src/main/java/com/cgcpms/file/auth/BusinessObjectAuthorizer.java`
- `backend/src/test/java/com/cgcpms/quality/QualitySafetyClosedLoopIntegrationTest.java`
- `backend/src/test/java/com/cgcpms/tech/TechnicalManagementClosedLoopIntegrationTest.java`
- `backend/src/test/java/com/cgcpms/m3/M3ControllerPermissionIntegrationTest.java`
- `packages/frontend-contracts/src/quality.ts`
- `packages/frontend-contracts/src/index.ts`
- `frontend-admin-v2/src/services/quality.ts`
- `frontend-admin-v2/src/pages/delivery/QualitySafetyPage.vue`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/m3-quality-safety.test.ts`
- `frontend-admin-v2/tests/unit/router.test.ts`
- `frontend-admin-v2/e2e/m3-quality-safety.spec.ts`
- `scripts/demo/complete-project-v2/sql/210-m3-domain-permission-data.sql`
- `scripts/demo/complete-project-v2/load.ps1`
- `scripts/demo/complete-project-v2/verify.ps1`
- `scripts/demo/complete-project-v2/manifest.yml`
- `scripts/demo/complete-project-v2/README.md`
- `scripts/demo/complete-project-v2/expected/acceptance-metrics.yml`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-013-M3质量安全整改闭环验收报告.md`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 路由为真实V2页面，台账达到`70/17/0`；无Shell占位、Legacy UI依赖或本地业务mock。
- 查询、计划、检查、整改、复检、后果权限严格分离；无权限动作隐藏且零请求，后端403仍为最终边界。
- 合同、责任合作方、项目范围、阶段动作、附件业务类型和不可变历史只消费后端权威事实。
- 缺合同、跨项目、重复提交、无权动作、整改提交后编辑、复检拒绝及附件失败均fail-close并可权威回读。
- ADMIN、质量查询、计划维护、检查维护、整改、复检、后果、无权和未登录身份具备可复验dev/demo数据。
- 后端`QualitySafetyClosedLoopIntegrationTest`、V2专项/全量单测、类型、Lint、Clean-room、台账、构建、包体、1440/1024/390 live E2E、axe、控制台和`git diff --check`通过。
状态：Done
来源锚点：`docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`的ISSUE-053-013；`docs/product-intelligence/evolution-decision.md`的`PI-2026-07-18-02`；`ISSUE-053-012`通过事实
存量问题键：[mainline:053-M3-013-QUALITY-SAFETY]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-013`
- `cd backend; .\mvnw.cmd "-Dtest=QualitySafetyClosedLoopIntegrationTest" test`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm check:bundle-size`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-013-M3质量安全整改闭环验收报告.md`
Migration：不需要
依赖：`ISSUE-053-012`已通过；本地`complete-project-v2`已有完整质量事实，实施时补齐分权身份/权限并纳入幂等验真。
风险等级：高
运行态要求：本地V2、Legacy、后端、MySQL、Redis和MinIO健康；身份及验收数据只在dev/demo创建并可精确回滚，不连接生产。
Reviewer要求：完成后独立安全与测试复核项目范围、合同关联、权限零请求、阶段附件、状态机、三视口、axe和台账。
最小回滚：单路由恢复`LEGACY_ONLY`，回退V2页面/服务/契约、专项测试及精确标记的本地验收权限数据；不删除既有质量事实或附件。

完成结果：`/quality-safety`达到`V2_ACCEPTED`，台账`70/17/0`；计划、检查、问题、整改、复检、后果、trace及四类CLEAN阶段证据已接入真实V2页面。六个持久化demo分权账号与六条阶段状态数据纳入幂等装载/验真，查询账号无写按钮且零写请求。后端附件授权按整改证据/复检证据精确分权；专项后端5项、V2全量24文件119项、类型/Lint/Clean-room/台账/构建/包体及live E2E 7项通过。新增后续项0、关闭后续项0、后续项净变化0。

### ISSUE-053-014：M3技术管理、图纸与RFI闭环迁移

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 项目履约 / 技术管理 / 图纸与RFI
目标：

- 迁移`/technical-management`，闭合技术方案、图纸版本/会审、RFI回复/接受、施工交底和档案确认。
- 保持项目范围、阶段状态、十类权限、附件事实和写后权威回读与现有后端一致。
非目标：
- 不迁移项目收尾，不改变技术域后端状态机、数据库结构或Legacy入口，不发布生产。
- 不新增依赖、全局Store、第二套请求层、前端业务状态推导或本地业务mock。
允许修改：
- `packages/frontend-contracts/src/technical.ts`
- `packages/frontend-contracts/src/index.ts`
- `frontend-admin-v2/src/services/technical.ts`
- `frontend-admin-v2/src/pages/delivery/TechnicalManagementPage.vue`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/m3-technical.test.ts`
- `frontend-admin-v2/tests/unit/router.test.ts`
- `frontend-admin-v2/e2e/m3-technical.spec.ts`
- `scripts/demo/complete-project-v2/sql/210-m3-domain-permission-data.sql`
- `scripts/demo/complete-project-v2/load.ps1`
- `scripts/demo/complete-project-v2/verify.ps1`
- `scripts/demo/complete-project-v2/manifest.yml`
- `scripts/demo/complete-project-v2/README.md`
- `scripts/demo/complete-project-v2/expected/acceptance-metrics.yml`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-014-M3技术管理图纸与RFI闭环验收报告.md`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 路由为真实V2页面，台账达到`69/18/0`；方案、图纸/版本、会审、RFI、交底、档案及trace均来自后端真实API。
- `technical:query`及九类动作权限严格分离；查询账号无写按钮且零写请求，后端403为最终边界，无动作权限越授。
- 图纸版本、会审、RFI回复/接受、交底、归档的阶段允许动作由后端状态决定；附件成功不替代业务提交成功。
- 跨项目、错误版本、无会审发RFI、重复回复、无权接受、附件成功但业务失败和陈旧trace均fail-close且可恢复。
- ADMIN、查询、方案维护/提交、图纸接收/会审、RFI发起/回复/接受、交底、归档及无权身份具备可复验dev/demo数据。
- 后端`TechnicalManagementClosedLoopIntegrationTest`、V2专项/全量单测、类型、Lint、Clean-room、台账、构建、包体、1440/1024/390 live E2E、axe、控制台和`git diff --check`通过。
状态：Done
来源锚点：`docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`的ISSUE-053-014；`docs/product-intelligence/evolution-decision.md`的`PI-2026-07-18-02`；`ISSUE-053-013`通过事实
存量问题键：[mainline:053-M3-014-TECHNICAL]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-014`
- `cd backend; .\mvnw.cmd "-Dtest=TechnicalManagementClosedLoopIntegrationTest" test`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm check:bundle-size`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-014-M3技术管理图纸与RFI闭环验收报告.md`
Migration：不需要
依赖：`ISSUE-053-013`已通过；当前后端技术域、Controller权限与complete-project-v2技术事实已核实，实施时补齐分权身份、可写阶段和附件验真。
风险等级：高
运行态要求：本地V2、Legacy、后端、MySQL、Redis和MinIO健康；身份及阶段数据只在dev/demo持久化，不连接生产。
Reviewer要求：完成后独立安全与测试复核项目范围、十权限分离、版本/状态机、附件与业务动作分离、三视口、axe和台账。
最小回滚：单路由恢复`LEGACY_ONLY`，回退V2技术契约/服务/页面、专项测试及M3 demo技术数据；不改写或删除既有方案、图纸、RFI、交底、归档或附件事实。

完成结果：`/technical-management`达到`V2_ACCEPTED`，台账`69/18/0`；方案、图纸/版本、会审、RFI、交底、施工依据、归档与trace接入真实V2。10个demo分权账号、9个单一动作权限、7个阶段样本和6类CLEAN附件纳入幂等验真；附件上传失败保留业务ID，重试仅上传附件。后端专项2项、V2全量25文件124项、静态/构建门及live E2E 11项通过。新增后续项0、关闭后续项0、后续项净变化0。

### ISSUE-053-015：M3竣工收尾闭环迁移

优先级：P0
任务性质：缺口修复
类型：Clean-room V2 / 项目履约 / 竣工收尾 / 权限与项目范围
目标：

- 迁移`/project-closeout`，闭合发起、分部验收、竣工验收、最终结算、尾款核验、质保、缺陷、档案移交与项目关闭。
- 保持项目范围、十一类权限、金额字符串、附件事实、状态机和写后权威回读与当前后端一致，并补齐负责人必须属于同租户有效项目成员的服务端约束。
非目标：
- 不改变收尾业务状态机、数据库结构、Legacy入口、收入域规则或生产环境，不自动提交、push或发布。
- 不新增依赖、全局Store、第二套请求层、前端业务状态推导或本地业务mock。
允许修改：
- `backend/src/main/java/com/cgcpms/closeout/service/ProjectCloseoutService.java`
- `backend/src/test/java/com/cgcpms/closeout/ProjectCloseoutClosedLoopIntegrationTest.java`
- `packages/frontend-contracts/src/closeout.ts`
- `packages/frontend-contracts/src/index.ts`
- `frontend-admin-v2/src/services/closeout.ts`
- `frontend-admin-v2/src/pages/delivery/ProjectCloseoutPage.vue`
- `frontend-admin-v2/src/navigation/catalog.ts`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/m3-closeout.test.ts`
- `frontend-admin-v2/tests/unit/router.test.ts`
- `frontend-admin-v2/tests/unit/navigation.test.ts`
- `frontend-admin-v2/e2e/m3-closeout.spec.ts`
- `scripts/demo/complete-project-v2/sql/210-m3-domain-permission-data.sql`
- `scripts/demo/complete-project-v2/load.ps1`
- `scripts/demo/complete-project-v2/verify.ps1`
- `scripts/demo/complete-project-v2/manifest.yml`
- `scripts/demo/complete-project-v2/README.md`
- `scripts/demo/complete-project-v2/expected/acceptance-metrics.yml`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-015-M3竣工收尾闭环验收报告.md`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 路由为真实V2页面，台账达到`68/19/0`；完整收尾链路与trace均来自后端真实API，金额字段保持字符串。
- `closeout:query`及十类动作权限严格分离；查询账号无写按钮且零写请求，后端403为最终边界，无动作权限越授。
- 每个阶段允许动作只由后端状态决定；结算候选按需读取，不扩大`revenue:operations:query`；附件成功不替代业务提交成功，失败重试不得重复创建业务事实。
- 质保和缺陷负责人必须是同租户、有效且属于目标项目的成员；跨租户、跨项目、缺前置、尾款未核验、质保未释放、缺陷未关闭、档案未接受及重复关闭均fail-close。
- ADMIN、查询、发起、分部验收、竣工验收、结算绑定、回款核验、质保、缺陷维护/验证、档案、关闭及无权身份具备可复验dev/demo数据；既有语义非法CLOSED样本被替换为合法链路事实。
- 后端`ProjectCloseoutClosedLoopIntegrationTest`、V2专项/全量单测、类型、Lint、Clean-room、台账、构建、包体、1440/1024/390 live E2E、axe、控制台和`git diff --check`通过。
状态：Done
来源锚点：`docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`的ISSUE-053-015；`docs/product-intelligence/evolution-decision.md`的`PI-2026-07-18-02`；`ISSUE-053-014`通过事实
存量问题键：[mainline:053-M3-015-CLOSEOUT]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-015`
- `cd backend; .\mvnw.cmd "-Dtest=ProjectCloseoutClosedLoopIntegrationTest" test`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm check:bundle-size`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-015-M3竣工收尾闭环验收报告.md`
Migration：不需要
依赖：`ISSUE-053-014`已通过；当前后端收尾状态机、Controller权限与既有demo偏差已核实，实施时补齐合法阶段事实、分权身份、项目成员约束和附件验真。
风险等级：高
运行态要求：本地V2、Legacy、后端、MySQL、Redis和MinIO健康；身份及阶段数据只在dev/demo持久化，不连接生产。
Reviewer要求：完成后独立安全与测试复核项目范围、十一权限分离、金额、状态机、负责人项目成员约束、附件与业务动作分离、三视口、axe和台账。
最小回滚：单路由恢复`LEGACY_ONLY`，回退V2收尾契约/服务/页面、专项测试、服务端负责人约束及M3 demo收尾数据；不得删除既有验收、结算、回款、质保、缺陷、档案或关闭事实。

完成结果：`/project-closeout`达到`V2_ACCEPTED`，台账`68/19/0`；完整收尾链、金额字符串、trace、附件失败恢复和责任人项目成员边界成立。11个demo账号、10个单一动作权限、10个阶段样本、1条合法关闭链及5类CLEAN附件纳入幂等验真；后端专项2项、V2全量26文件128项、静态/构建门及live E2E 9项通过。新增后续项0、关闭后续项0、后续项净变化0。

### ISSUE-053-016：M3全量退出门与治理收口

优先级：P0
任务性质：缺口修复
类型：Clean-room V2 / M3退出门 / 全量验收 / 治理收口
目标：

- 对M3十条项目履约路由执行统一退出验收，证明真实API、权限、项目范围、金额、状态机、附件恢复、查询零写和写后权威回读边界完整成立。
- 固化`68/19/0`台账、完整dev/demo复验、独立安全与测试复核及唯一治理结论，使M3形成可审计闭环。
非目标：
- 不启动M4，不切换正式根路由，不退役Legacy，不改变数据库结构、生产环境或发布配置，不自动提交、push或发布。
- 不新增依赖、业务能力、全局Store、第二套请求层、前端业务状态推导或本地业务mock。
允许修改：
- `backend/src/main/java/com/cgcpms/closeout/service/ProjectCloseoutService.java`
- `backend/src/main/java/com/cgcpms/file/auth/BusinessObjectAuthorizer.java`
- `backend/src/main/java/com/cgcpms/quality/service/QualitySafetyService.java`
- `backend/src/main/java/com/cgcpms/tech/service/TechnicalManagementService.java`
- `backend/src/test/java/com/cgcpms/closeout/ProjectCloseoutClosedLoopIntegrationTest.java`
- `backend/src/test/java/com/cgcpms/quality/QualitySafetyClosedLoopIntegrationTest.java`
- `backend/src/test/java/com/cgcpms/tech/TechnicalManagementClosedLoopIntegrationTest.java`
- `backend/src/test/java/com/cgcpms/m3/**`
- `packages/frontend-contracts/src/**`
- `frontend-admin-v2/src/pages/projects/**`
- `frontend-admin-v2/src/pages/delivery/**`
- `frontend-admin-v2/src/services/**`
- `frontend-admin-v2/src/navigation/catalog.ts`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `frontend-admin-v2/tests/unit/**`
- `frontend-admin-v2/e2e/m3-*.spec.ts`
- `frontend-admin-v2/e2e/runtime-errors.ts`
- `scripts/demo/complete-project-v2/**`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/ui-v2/route-migration-ledger.json`
- `docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`
- `docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/第53条主线-M3-项目履约全量退出门验收报告.md`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- M3十条目标路由均为真实V2页面，台账固定为`68/19/0`，无`ShellPlaceholderPage`、手写台账或本地业务mock。
- 项目范围、最小权限、跨项目/跨租户拒绝、金额字符串、后端状态机、附件与业务动作分离、失败恢复、查询账号零写请求和写后权威回读全部可复验。
- complete-project-v2补齐全部M3角色、权限、阶段、合法闭环和CLEAN附件数据；连续重载保持幂等，验证脚本通过。
- M3后端专项集、V2全量单测、契约类型、应用类型、Lint、Clean-room边界、台账生成、构建、包体、`git diff --check`全部通过。
- 五组M3 live E2E在1440/1024/390视口验证真实后端、axe、控制台、查询零写及阶段动作；独立安全与测试复核无阻塞发现。
- 计划、Ready、Current Focus、Done、项目地图、迭代决策及正式质量报告一致回写；所有发现项完成本轮修复、正式承接或有依据关闭。
状态：Done
来源锚点：`docs/plans/第53条主线-M3-项目履约任务计划书-2026-07-21.md`的ISSUE-053-016；`docs/product-intelligence/evolution-decision.md`的`PI-2026-07-18-02`；`ISSUE-053-015`通过事实
存量问题键：[mainline:053-M3-016-EXIT-GATE]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-016`
- `cd backend; .\mvnw.cmd "-Dtest=QualitySafetyClosedLoopIntegrationTest,TechnicalManagementClosedLoopIntegrationTest,ProjectCloseoutClosedLoopIntegrationTest,M3ControllerPermissionIntegrationTest" test`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm check:bundle-size`
- `pwsh -NoProfile -File scripts/demo/complete-project-v2/load.ps1 -Environment demo -Database cgc_pms_demo_v2`
- `pwsh -NoProfile -File scripts/demo/complete-project-v2/verify.ps1 -Environment demo -Database cgc_pms_demo_v2`
- `pwsh -NoProfile -File scripts/demo/complete-project-v2/verify-m3-live.ps1`
- `git diff --check`
归档报告：`docs/quality/第53条主线-M3-项目履约全量退出门验收报告.md`
Migration：不需要
依赖：`ISSUE-053-010～015`均已通过；本Issue仅执行M3全量复验、独立复核和治理收口。
风险等级：高
运行态要求：本地V2、Legacy、后端、MySQL、Redis和MinIO健康；仅使用dev/demo数据，不连接生产。
Reviewer要求：独立安全Reviewer复核权限、租户/项目范围、金额、状态机与附件边界；独立测试Reviewer复核测试覆盖、演示数据、三视口、axe、查询零写和证据完整性。
最小回滚：回退本Issue治理与报告回写；若复验发现具体缺陷，仅回退对应M3路由或数据增量，不改写既有业务事实，不影响M0～M2。

### ISSUE-048-011：修正会计凭证页面陈旧生成规则说明

优先级：P1
任务性质：缺口修复
类型：会计凭证 / 页面文案 / 自动生成边界 / 权限不扩张
目标：

- 将会计凭证页陈旧辅助说明修正为当前事实，即付款、回款凭证由权威业务写侧自动生成，手工生成入口未开放。
- 用页面单测和只读浏览器复验保证财务用户不再被“生成规则待确认”误导。
  非目标：
- 不新增生成按钮、生成 API、科目映射、凭证策略、权限、迁移或业务数据。
- 不改变复核、过账、冲销及会计期间状态机。
  允许修改：
- `frontend-admin/src/pages/accounting-entry/index.vue`
- `frontend-admin/src/pages/accounting-entry/__tests__/index.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/ad-hoc-plan.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-011-修正会计凭证页面陈旧生成规则说明验收报告.md`
  禁止修改：
- `backend/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `frontend-admin-v2/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 页面不再出现“来源单据到借贷科目的生成规则待会计确认”或其他暗示规则尚未落地的文案。
- 新文案明确付款、回款由权威业务写侧自动生成，手工生成入口未开放；页面不新增生成按钮或可触发生成的交互。
- 既有会计新增权限契约保持不变，权限码 `accounting:add`、后端、数据库与历史会计事实零修改。
- 会计凭证页专项单测、Legacy 前端类型检查、只读浏览器复验与 `git diff --check` 通过。
  状态：Done
  来源锚点：`docs/backlog/current-issues.json` 的 `A-01-ACCOUNTING-GENERATION-COPY`；`docs/backlog/ad-hoc-plan.md` 的同名 `ReadyToSplit` 候选；`docs/quality/ISSUE-048-010-会计凭证生成策略现状复核与P0问题关闭验收报告.md`
  存量问题键：[stock:A-01-ACCOUNTING-GENERATION-COPY]
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-011`
- `cd frontend-admin; pnpm test:unit -- src/pages/accounting-entry/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
  归档报告：`docs/quality/ISSUE-048-011-修正会计凭证页面陈旧生成规则说明验收报告.md`
  Migration：不需要
  依赖：`ISSUE-048-010` 已确认付款、回款生产策略与业务写侧自动生成事实；不依赖 M1。
  风险等级：低
  运行态要求：当前本地 Legacy 前端与后端；只读查看会计凭证列表，不生成、复核、过账或冲销凭证。
  Reviewer要求：核对文案与现行付款/回款实现及业务标准一致；确认无按钮、权限、API、状态机或数据变更。
  最小回滚：回退页面文案、对应单测和治理回写；不涉及迁移与业务数据恢复。

### ISSUE-053-005：M2 共享契约、项目上下文与数据请求基线

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 共享契约 / 项目范围 / 可取消请求
目标：

- 补齐八类驾驶舱稳定 DTO、权限码、端点映射和项目查询契约。
- 复用 V2 请求核心，建立合法报告期转换、按权限选择端点和陈旧请求隔离。
  非目标：
- 不渲染真实驾驶舱，不修改后端、数据库、Legacy 或正式入口。
- 不新增全局 Store、请求库、缓存层或业务 mock。
  允许修改：
- `packages/frontend-contracts/src/**`
- `frontend-admin-v2/src/services/**`
- `frontend-admin-v2/src/stores/workspace/**`
- `frontend-admin-v2/src/layouts/AppShell.vue`
- `frontend-admin-v2/tests/unit/**`
- `docs/backlog/**`
- `docs/plans/第53条主线-M2-工作台与新版驾驶舱任务计划书-2026-07-19.md`
- `docs/product-intelligence/**`
- `docs/quality/ISSUE-053-005-M2共享契约与请求基线验收报告.md`
  禁止修改：
- `backend/**`
- `frontend-admin/**`
- `frontend-admin-v2/src/pages/dashboard/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
  验收标准：
- 八角色端点、权限码和 DTO 与当前后端契约一致；项目列表不扩大当前用户数据范围。
- 非法项目或报告期不发畸形请求；仅请求当前有权角色端点，陈旧响应不能覆盖最新状态。
- 契约类型、V2 单测、类型、Lint、Clean-room 边界、构建和 `git diff --check` 通过。
  状态：Done
  来源锚点：`docs/plans/第53条主线-M2-工作台与新版驾驶舱任务计划书-2026-07-19.md` 的 ISSUE-053-005；`docs/product-intelligence/project-map.md` 驾驶舱与报表 Partial
  存量问题键：[mainline:053-M2-005-CONTRACT-REQUEST-BASELINE]
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-005`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm build`
- `git diff --check`
  归档报告：`docs/quality/ISSUE-053-005-M2共享契约与请求基线验收报告.md`
  Migration：不需要
  依赖：M1 与公共壳定稿已完成；本 Issue 是 M2 唯一本地实施金丝雀，完成后方可实施 ISSUE-053-006。
  风险等级：高
  运行态要求：本地 V2 与后端；只读项目和驾驶舱接口，不执行业务写入。
  Reviewer要求：交叉核对 Controller、共享契约和请求选择；确认无权端点请求为 0、无项目不回退租户全量、陈旧响应不回写。
  最小回滚：回退 M2 契约、薄服务、workspace 小范围接线和测试；M1 壳保持可用。

### ISSUE-053-006：八角色新版经营驾驶舱

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 驾驶舱 / 八角色 / 数据范围
目标：

- 用真实 API 落地八角色经营驾驶舱、趋势、风险、快捷入口和成本两级下钻。
  非目标：
- 不复制 Legacy UI，不新增图表库、金额公式、写操作或正式入口切换。
  允许修改：
- `frontend-admin-v2/src/pages/dashboard/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/tests/**`
- `backend/src/main/java/com/cgcpms/dashboard/service/DashboardFinanceManagementService.java`（仅风险明细完整性）
- `backend/src/test/java/com/cgcpms/dashboard/service/DashboardFinanceManagementServiceTest.java`
- `scripts/demo/complete-project-v2/**`（仅用户授权的 dev/test/demo 可重放验收数据）
- `docs/backlog/**`
- `docs/product-intelligence/**`
- `docs/quality/ISSUE-053-006-M2八角色新版经营驾驶舱验收报告.md`
  禁止修改：
- `backend/**`
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
  验收标准：
- 八角色仅消费真实 API；无权角色不展示、不请求，项目级用户无项目时保持空态。
- 金额字符串、健康辅助标签、趋势等价文本、成本两级下钻和部分失败隔离通过。
- 1440、1024、390 视觉、无障碍、单测、类型、Lint、构建、包体和边界通过。
- 风险筛选统一为“高/中/低/其他”；八角色每档至少一条真实演示数据，“全部预警”不作为风险等级。
  状态：Completed（2026-07-20，真实演示数据与八角色 live E2E 已通过）
  来源锚点：M2计划 ISSUE-053-006；用户选定经营驾驶舱视觉源；项目地图驾驶舱 Partial
  存量问题键：[mainline:053-M2-006-EIGHT-ROLE-DASHBOARD]
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-006`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm check:bundle-size`
- `git diff --check`
  归档报告：`docs/quality/ISSUE-053-006-M2八角色新版经营驾驶舱验收报告.md`
  Migration：不需要
  依赖：ISSUE-053-005 通过。
  风险等级：高
  运行态要求：本地真实后端与受控身份；浏览器只读驾驶舱。
  Reviewer要求：确认真实数据、权限/项目范围、金额口径、部分失败、视觉源与三视口证据。
  最小回滚：恢复 Dashboard 路由占位并回退 V2 驾驶舱页面与测试。

### ISSUE-053-007：审批工作台与实例深链

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 审批 / 深链 / 幂等
目标：

- 迁移四类审批列表、详情深链及后端允许的审批动作。
  非目标：
- 不迁移流程模板管理，不新增审批动作、工作流状态或数据库结构；仅补齐现有本人范围查询所需的可见业务类型与已办业务编号返回。
  允许修改：
- `packages/frontend-contracts/src/workflow.ts`
- `packages/frontend-contracts/src/index.ts`
- `frontend-admin-v2/src/services/workflow.ts`
- `frontend-admin-v2/src/pages/workbench/**`
- `frontend-admin-v2/src/components/V2Dialog.vue`
- `frontend-admin-v2/src/components/V2Select.vue`
- `frontend-admin-v2/src/layouts/AppShell.vue`
- `frontend-admin-v2/src/navigation/catalog.ts`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/styles/components.css`
- `frontend-admin-v2/tests/**`
- `frontend-admin-v2/e2e/m2-approval-workbench-live.spec.ts`
- `frontend-admin-v2/eslint.config.js`
- `frontend-admin-v2/package.json`
- `backend/src/main/java/com/cgcpms/workflow/controller/WorkflowController.java`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowQueryService.java`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowVOAssembler.java`
- `backend/src/main/java/com/cgcpms/workflow/vo/WfRecordVO.java`
- `backend/src/test/java/com/cgcpms/workflow/WorkflowQueryServiceTest.java`
- `scripts/demo/complete-project-v2/**`
- `design-qa.md`
- `docs/backlog/**`
- `docs/product-intelligence/**`
- `docs/quality/ISSUE-053-007-M2审批工作台验收报告.md`
  禁止修改：
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
  验收标准：
- 列表和详情身份范围 fail-close；动作同时受 `availableActions` 与权限控制。
- CSRF、幂等键、重复点击、冲突和业务拒绝不产生重复事实；`/approval/process` 保持未迁移。
- 单测、类型、Lint、构建、权限负向、深链和三视口通过。
  状态：Completed（2026-07-20，四类列表、实例深链、动作双门禁与 live E2E 已通过）
  来源锚点：M2计划 ISSUE-053-007；项目地图审批、抄送与通知 Partial
  存量问题键：[mainline:053-M2-007-APPROVAL-WORKBENCH]
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-007`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm build`
- `git diff --check`
  归档报告：`docs/quality/ISSUE-053-007-M2审批工作台验收报告.md`
  Migration：不需要
  依赖：ISSUE-053-006 通过。
  风险等级：高
  运行态要求：本地受控审批样本；禁止生产和无授权业务写入。
  Reviewer要求：确认身份范围、跨租户/未知实例、动作双重控制、CSRF、幂等和事实回读。
  最小回滚：恢复审批路由占位并回退 V2 workflow 契约、服务、页面和测试。

### ISSUE-053-008：驾驶舱内预警处置、通知摘要与报表目录

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 预警 / 通知 / 报表
目标：

- 将权威预警查询与单项处置闭环收敛进驾驶舱，并迁移受控通知摘要和报表目录。
非目标：
- 不接入 SSE，不伪造未读数，不新增预警规则或报表；后续授权只允许修复既有八角色预警访问域和本地 demo 范围数据。
允许修改：
- `backend/src/main/java/com/cgcpms/alert/auth/AlertAccessScopeResolver.java`
- `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`
- `backend/src/test/java/com/cgcpms/alert/AlertAccessScopeResolverTest.java`
- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`
- `packages/frontend-contracts/src/alert.ts`
- `packages/frontend-contracts/src/report.ts`
- `packages/frontend-contracts/src/index.ts`
- `frontend-admin-v2/src/services/**`
- `frontend-admin-v2/src/pages/dashboard/**`
- `frontend-admin-v2/src/pages/workbench/**`
- `frontend-admin-v2/src/layouts/AppShell.vue`
- `frontend-admin-v2/src/navigation/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/tests/**`
- `scripts/demo/complete-project-v2/sql/150-role-test-accounts.sql`
- `scripts/demo/complete-project-v2/sql/170-dashboard-risk-levels.sql`
- `scripts/demo/complete-project-v2/verify.ps1`
- `docs/backlog/**`
- `docs/product-intelligence/**`
- `docs/quality/ISSUE-053-008-M2预警与报表目录验收报告.md`
禁止修改：
- `backend/**`（上述明确允许文件除外）
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 预警查看、编辑和评估权限正负矩阵通过；权威预警行可在驾驶舱内打开悬浮窗并按权限处置。
- 不保留独立预警中心页面或工作区标签；历史 `/alert` 深链安全重定向到驾驶舱预警列表。
- 无预警权限不请求通知摘要；报表按服务端权限过滤，未知目标和 `api_only` 不伪装页面。
- 单测、类型、Lint、构建、权限、深链和三视口通过。
- 八个演示角色均通过真实登录会话读取至少一条与其业务域、活动项目范围一致的权威预警；八角色均具备单项处置权限，规则评估仍仅限财务与管理员，项目范围与角色业务域不得扩大。
状态：Completed（2026-07-20，驾驶舱内预警处置、有限通知摘要、权限过滤报表目录与运行态验收已通过）
来源锚点：M2计划 ISSUE-053-008；项目地图预警 Complete(P0) 与驾驶舱报表 Partial
存量问题键：[mainline:053-M2-008-ALERT-REPORT-WORKBENCH]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-008`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm build`
- `cd backend; .\mvnw.cmd "-Dtest=AlertAccessScopeResolverTest,AlertEvaluationServiceTest#testAccess_ProductionAndChiefEngineerDomains" test`
- `pwsh -NoProfile -File scripts/demo/complete-project-v2/verify.ps1 -Environment demo -Database cgc_pms_demo_v2`
- `git diff --check`
归档报告：`docs/quality/ISSUE-053-008-M2预警与报表目录验收报告.md`
Migration：不需要；仅更新幂等本地 demo 数据脚本。
依赖：ISSUE-053-007 通过。
风险等级：高
运行态要求：本地受控身份和数据；只执行验收所需受控预警动作。
Reviewer要求：确认权限矩阵、驾驶舱权威预警来源、通知零伪造、报表目标过滤和真实回读。
最小回滚：恢复驾驶舱派生风险只读列表和预警/报表路由占位，回退相关契约、服务、页面、壳接线、角色业务域解析及测试；本地 demo 数据按固定 ID 删除新增成员、授权与质量安全预警。

### ISSUE-053-009：M2全量退出门与治理收口

优先级：P0
任务性质：回归证明
类型：Clean-room V2 / M2验收 / 路由台账 / 治理收口
目标：

- 完成九个M2路由、八角色、四类身份、三视口和治理载体的一致性验收。
非目标：
- 不新增业务能力，不切换正式入口，不发布生产或退役 Legacy。
允许修改：
- `frontend-admin-v2/tests/**`
- `frontend-admin-v2/e2e/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/components/V2Dialog.vue`
- `frontend-admin-v2/src/pages/dashboard/DashboardPage.vue`
- `frontend-admin-v2/src/styles/components.css`
- `frontend-admin-v2/src/styles/tokens.css`
- `frontend-admin-v2/scripts/generate-route-ledger.mjs`
- `docs/ui-v2/**`
- `docs/backlog/**`
- `docs/plans/第53条主线-M2-工作台与新版驾驶舱任务计划书-2026-07-19.md`
- `docs/product-intelligence/**`
- `docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md`
禁止修改：
- `backend/src/main/**`
- `frontend-admin/src/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 九个目标路由均为 `V2_ACCEPTED`，台账达到78/9/0；无占位、mock或Legacy边。
- 权限、项目范围、金额、审批幂等、预警部分成功、报表过滤、性能、包体、axe和控制台门禁通过。
- 所有发现完成修复、正式承接或关闭；正式报告与项目地图、迭代决策、Current Focus一致。
状态：Completed（2026-07-21，九路由、八角色、四类身份、三视口与治理载体全量退出门通过）
来源锚点：M2计划 ISSUE-053-009 与 M2完成定义
存量问题键：[mainline:053-M2-009-EXIT-GATE]
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-009`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `cd frontend-admin-v2; pnpm check:bundle-size`
- `git diff --check`
归档报告：`docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md`
Migration：不需要
依赖：ISSUE-053-008 通过。
风险等级：高
运行态要求：本地V2、Legacy、后端和数据库健康；真实受控身份和1440/1024/390浏览器验收。
Reviewer要求：独立核对路由计数、权限负向、数据范围、金额、写侧事实、Clean-room、三视口和零悬空。
最小回滚：将九个M2路由恢复占位并回退M2页面、服务、契约、测试与台账；M1保留。

完成结果：
- 九个 M2 route name 全部为 `V2_ACCEPTED`，台账达到 `78/9/0`；`/approval` 与 `/approval/:instanceId` 兼容深链已补齐，`/approval/process` 保持 `LEGACY_ONLY`。
- V2 单测18文件84项、Lint、类型/构建、Clean-room边界、路由台账、8个JS包体门禁和驾驶舱性能2项通过；5项目SQL计数13，低于20门槛。
- 本地真实运行态后端、Legacy、V2与demo验真通过；最终同一diff下驾驶舱8项、审批12项、M1壳2项及live壳1项共23项通过，覆盖八角色、四身份、三视口、axe、403/404、深链、权限负向和控制台。
- 公共`V2Dialog`已统一桌面端卡片、文字和布局；通知、审批详情与预警处置弹窗复用同一视觉语言，内容按业务差异保留；弹窗内下拉菜单固定向下展开且不再被面板裁切。
- 退出门发现的Ready格式、台账生成器硬编码、审批兼容路由、陈旧E2E断言、移动端详情标题、健康分数口径提示、桌面端嵌套白卡与下拉裁切均本轮修复并复验。
- 新增后续项0、关闭后续项1、后续项净变化-1；无悬空项。未切正式入口，未发布生产，未退役Legacy。

### ISSUE-053-001：建立 Clean-room V2 设计令牌与最小组件基线

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 设计系统 / Token / 基础组件
目标：

- 从已选新版经营驾驶舱视觉基线提取颜色、排版、间距、圆角、阴影、层级和动效令牌，形成桌面与移动应用壳可复用的 V2 设计基线。
- 实现登录、应用壳和驾驶舱所需的最小无业务组件集，并保持 Clean-room 边界。
  非目标：
- 不实现登录会话、权限守卫、八域导航、真实驾驶舱或业务页面迁移。
- 不复制 Legacy Vue、Pinia、CSS、DOM 状态或消息组件，不修改 Legacy 前端、后端和数据库。
  允许修改：
- `frontend-admin-v2/src/styles/**`
- `frontend-admin-v2/src/components/**`
- `frontend-admin-v2/tests/unit/**`
- `frontend-admin-v2/package.json`
- `frontend-admin-v2/pnpm-lock.yaml`
- `docs/ui-v2/**`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-001-Clean-room-V2设计令牌与最小组件基线验收报告.md`
  禁止修改：
- `frontend-admin-v2/src/services/**`
- `frontend-admin-v2/src/stores/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/pages/**`
- `frontend-admin/**`
- `backend/**`
- `packages/frontend-contracts/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `scripts/codex-autopilot/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 令牌覆盖颜色、排版、间距、圆角、阴影、层级和动效，组件禁止散落与令牌重复的硬编码核心视觉值。
- 最小组件集覆盖按钮、输入、选择、卡片、徽标、反馈、骨架、对话与基础布局；键盘焦点、禁用、加载和错误状态可测试。
- 桌面与移动壳视觉稿/规范可追溯到已选驾驶舱基线；不引入真实业务数据或 Legacy 源码。
- V2 单测、类型、Lint、构建、Clean-room 边界和 `git diff --check` 通过。
  状态：Done
  来源锚点：`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-18-02`；`docs/plans/第53条主线-CGC-PMS全量UI Clean-room V2重构任务计划书.md` 的 M1；`docs/product-intelligence/project-map.md` 的 M0 完成事实
  存量问题键：[candidate:PI-2026-07-18-02-M1-DESIGN-SYSTEM]
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-001`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm build`
- `git diff --check`
  归档报告：`docs/quality/ISSUE-053-001-Clean-room-V2设计令牌与最小组件基线验收报告.md`
  Migration：不需要
  依赖：第53条主线 M0 已通过；用户已选新版经营驾驶舱为唯一视觉基线。完成后方可实施 `ISSUE-053-002`。
  风险等级：中
  运行态要求：本地 V2 5174 与现有后端；需在 1440、1024、390 视口检查组件基线，不切换正式入口。
  Reviewer要求：确认设计令牌来自选定视觉基线、组件范围最小且可复用、无 Legacy import、无业务功能偷跑和无正式入口变更。
  最小回滚：回退 V2 令牌、组件、测试与文档；M0 健康页、Legacy、后端和数据保持不变。

### ISSUE-053-002：实现 Clean-room V2 安全会话与请求核心

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 登录 / 同源会话 / CSRF / 401恢复
目标：

- 实现登录、当前用户恢复、登出、CSRF、401 单次恢复和重复错误去重，保持与 Legacy 后端契约一致。
- 建立仅共享 DTO、权限码和 API 相对路径的认证契约与 V2 请求核心。
  非目标：
- 不实现八域导航、对象上下文、真实驾驶舱或其他业务页面。
- 不新增认证 API、不改变 JWT/Session 后端策略、不把 token 写入 localStorage、sessionStorage 或 URL。
  允许修改：
- `packages/frontend-contracts/src/auth.ts`
- `packages/frontend-contracts/src/api.ts`
- `packages/frontend-contracts/src/index.ts`
- `frontend-admin-v2/src/services/**`
- `frontend-admin-v2/src/stores/**`
- `frontend-admin-v2/src/pages/auth/**`
- `frontend-admin-v2/src/main.ts`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/vite.config.ts`
- `frontend-admin-v2/tests/unit/**`
- `frontend-admin-v2/e2e/**`
- `frontend-admin-v2/package.json`
- `frontend-admin-v2/pnpm-lock.yaml`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-002-Clean-room-V2安全会话与请求核心验收报告.md`
  禁止修改：
- `frontend-admin/**`
- `backend/**`
- `frontend-admin-v2/src/pages/dashboard/**`
- `frontend-admin-v2/src/pages/workspace/**`
- `frontend-admin-v2/src/features/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `scripts/codex-autopilot/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 未登录用户只能访问公开健康页和登录页；有效同源会话可恢复当前用户，登出后受保护状态与缓存清空。
- CSRF 只附加到需要保护的写请求；并发 401 只触发一次恢复流程，恢复失败统一退出且不形成重试风暴。
- token 不进入 localStorage、sessionStorage、IndexedDB、URL、日志或错误提示；重复错误在同一恢复窗口只通知一次。
- ADMIN、普通有权、无权和未登录用户的认证结果与 Legacy 契约一致；V2/契约单测、类型、Lint、构建、边界和浏览器认证专项通过。
  状态：Done
  来源锚点：`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-18-02`；第53条主线计划书 M1 认证任务；M0 冻结的登录、用户、角色、权限和 API 最小契约
  存量问题键：[candidate:PI-2026-07-18-02-M1-AUTH-SESSION]
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-002`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check:contracts`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm build`
- `git diff --check`
  归档报告：`docs/quality/ISSUE-053-002-Clean-room-V2安全会话与请求核心验收报告.md`
  Migration：不需要
  依赖：`ISSUE-053-001` 完成；复用 M0 已冻结认证契约与现有后端同源会话，不新增后端前置。完成后方可实施 `ISSUE-053-003`。
  风险等级：高
  运行态要求：本地 V2 5174、Legacy 5173 与同一后端；使用测试/演示账号做登录、恢复、登出及 401 正负样本，不连接生产。
  Reviewer要求：安全复核 token 零持久化、CSRF 条件、401 并发单飞、错误去重、退出清理、权限信息来源和跨租户缓存隔离。
  最小回滚：回退 V2 请求、会话、登录页、契约和测试；不回退 M0/`ISSUE-053-001`，Legacy 入口继续可用。

### ISSUE-053-003：实现 Clean-room V2 八域应用壳与权限导航

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 应用壳 / 八域导航 / 工作区上下文
目标：

- 实现八域导航、工作区 Tab、对象上下文、全局项目和报告期选择，并由当前用户权限驱动可见性与深链守卫。
- 建立响应式应用壳的路由与状态骨架，为 M2 及后续垂直迁移提供唯一承载层。
  非目标：
- 不迁移驾驶舱、项目、合同、供应链、成本、财务或系统管理真实业务页面。
- 不按角色名称硬编码导航，不新增权限码、后端 API、数据库字段或正式入口切换。
  允许修改：
- `frontend-admin-v2/src/layouts/**`
- `frontend-admin-v2/src/navigation/**`
- `frontend-admin-v2/src/stores/workspace/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/pages/shell/**`
- `frontend-admin-v2/src/App.vue`
- `frontend-admin-v2/tests/unit/**`
- `frontend-admin-v2/e2e/**`
- `frontend-admin-v2/package.json`
- `frontend-admin-v2/pnpm-lock.yaml`
- `docs/ui-v2/route-migration-ledger.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-003-Clean-room-V2八域应用壳与权限导航验收报告.md`
  禁止修改：
- `frontend-admin/**`
- `backend/**`
- `packages/frontend-contracts/src/auth.ts`
- `packages/frontend-contracts/src/api.ts`
- `frontend-admin-v2/src/pages/dashboard/**`
- `frontend-admin-v2/src/features/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `scripts/codex-autopilot/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 八域入口、工作区 Tab、对象上下文、全局项目和报告期具有确定状态来源；刷新、深链、退出和无可用项目时不产生陈旧上下文。
- ADMIN、普通有权和无权用户只看到可访问导航；直接访问无权限路由被守卫阻断，结果与 Legacy 权限码契约一致。
- 壳层不加载真实业务 API、不伪造业务数字、不以空壳宣称业务页面迁移完成；路由迁移台账准确反映状态。
- V2 单测、类型、Lint、构建、边界、路由台账检查、浏览器权限正负样本和 `git diff --check` 通过。
  状态：Done
  来源锚点：`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-18-02`；第53条主线计划书 M1 八域应用壳任务；`docs/ui-v2/route-migration-ledger.md`
  存量问题键：[candidate:PI-2026-07-18-02-M1-APP-SHELL]
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-003`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm build`
- `git diff --check`
  归档报告：`docs/quality/ISSUE-053-003-Clean-room-V2八域应用壳与权限导航验收报告.md`
  Migration：不需要
  依赖：`ISSUE-053-001`、`ISSUE-053-002` 完成；与二者串行，完成后方可实施 `ISSUE-053-004`。
  风险等级：高
  运行态要求：本地 V2 5174、Legacy 5173 与同一后端；至少使用 ADMIN、普通有权、无权和未登录四类身份做导航与深链正负样本。
  Reviewer要求：复核权限码而非角色名驱动、项目/报告期/对象上下文隔离、深链与刷新一致性、路由台账准确性及无真实业务页偷跑。
  最小回滚：回退 V2 壳、导航、工作区状态、路由和测试；保留已验收设计系统与安全会话，Legacy 正式入口不变。

### ISSUE-053-004：完成 Clean-room V2 响应式、无障碍与错误状态基线

优先级：P0
任务性质：能力新增
类型：Clean-room V2 / 响应式 / 无障碍 / 错误边界
目标：

- 为应用壳完成桌面、紧凑桌面、移动端和减少动效模式。
- 实现 403、404、全局错误边界、加载壳和通知入口，形成 M1 完整退出门。
  非目标：
- 不迁移真实驾驶舱或其他业务页面，不接入真实通知业务列表、SSE 或写操作。
- 不改变认证、权限码、后端异常结构、数据库、Legacy 前端或正式入口。
  允许修改：
- `frontend-admin-v2/src/styles/**`
- `frontend-admin-v2/src/components/**`
- `frontend-admin-v2/src/layouts/**`
- `frontend-admin-v2/src/navigation/**`
- `frontend-admin-v2/src/router.ts`
- `frontend-admin-v2/src/pages/errors/**`
- `frontend-admin-v2/src/pages/shell/**`
- `frontend-admin-v2/src/App.vue`
- `frontend-admin-v2/tests/unit/**`
- `frontend-admin-v2/e2e/**`
- `frontend-admin-v2/playwright.config.ts`
- `frontend-admin-v2/package.json`
- `frontend-admin-v2/pnpm-lock.yaml`
- `docs/ui-v2/**`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-053-004-Clean-room-V2响应式无障碍与错误状态基线验收报告.md`
  禁止修改：
- `frontend-admin/**`
- `backend/**`
- `packages/frontend-contracts/**`
- `frontend-admin-v2/src/pages/dashboard/**`
- `frontend-admin-v2/src/features/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `scripts/codex-autopilot/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 1440、1024、390 视口的登录页和应用壳无横向溢出、遮挡、不可达主操作或焦点丢失；紧凑桌面与移动导航可键盘和触控操作。
- `prefers-reduced-motion` 下非必要动效关闭；焦点顺序、焦点可见性、语义地标、可访问名称和对比度满足核心流程要求。
- 403、404、全局异常、加载、空壳和重复错误均有明确状态；通知入口只提供壳级占位与权限边界，不伪造业务通知。
- ADMIN、普通有权、无权和未登录四类结果与 Legacy 契约一致；axe 核心扫描无 serious/critical 违规，浏览器控制台无新增 warning/error。
- V2 单测、类型、Lint、构建、边界、路由台账、1440/1024/390 Playwright 与 `git diff --check` 全部通过，M1 才可判定完成。
  状态：Done
  来源锚点：`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-18-02`；第53条主线计划书 M1 响应式、403/404、错误边界和 axe 退出标准
  存量问题键：[candidate:PI-2026-07-18-02-M1-RESPONSIVE-A11Y]
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-053-004`
- `cd frontend-admin-v2; pnpm test:unit`
- `cd frontend-admin-v2; pnpm type-check`
- `cd frontend-admin-v2; pnpm lint:check`
- `cd frontend-admin-v2; pnpm check:boundary`
- `cd frontend-admin-v2; pnpm check:route-ledger`
- `cd frontend-admin-v2; pnpm exec playwright test e2e/m1-shell.spec.ts`
- `cd frontend-admin-v2; pnpm build`
- `git diff --check`
  归档报告：`docs/quality/ISSUE-053-004-Clean-room-V2响应式无障碍与错误状态基线验收报告.md`
  Migration：不需要
  依赖：`ISSUE-053-001`、`ISSUE-053-002`、`ISSUE-053-003` 全部完成；作为 M1 最终串行收口，不得与前三项并行。
  风险等级：中
  运行态要求：本地 V2 5174、Legacy 5173 与同一后端；Edge/Chromium 在 1440、1024、390 视口实测四类身份，不连接生产。
  Reviewer要求：独立复核三视口、键盘路径、减少动效、axe、403/404/全局错误状态、控制台、通知入口非业务化及 M1 退出标准完整性。
  最小回滚：回退响应式、无障碍、错误状态、测试和文档；保留前三个已验收 M1 切片，Legacy 正式入口不变。

### ISSUE-048-010：会计凭证生成策略现状复核与P0问题关闭

优先级：P0
任务性质：回归证明
类型：会计凭证 / 付款回款 / 自动生成 / 幂等租户 / 历史问题纠偏
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json` 的 `A-01-ACCOUNTING-GENERATE`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-10`；`docs/business/financial-accounting-month-end-closed-loop.md`；candidateEvidenceHead=ba8d8c92da9744f07ed2c6e547e976e7e6dde21b
存量问题键：[stock:A-01-ACCOUNTING-GENERATE]
关联产品目标：纠正“生产环境没有会计凭证生成策略”的过时P0结论，并用当前付款、回款、月结闭环证据确认自动凭证的来源、科目、借贷、幂等、租户、期间和冲销边界。
阻塞证据：当前问题仍称 `POST /accounting-entry/generate` 没有生产 `EntryGenerationStrategy`；当前源码却已有 `PayRecordEntryGenerationStrategy` 与 `CollectionRecordEntryGenerationStrategy`，付款和回款写侧会自动调用 `EntryGenerator`，项目地图也已将财务核算与月结标记为 P0 完成。正式台账与当前实现冲突。
解除条件：证明成功付款与成功回款各自生成唯一、借贷平衡、来源可追溯的草稿待复核凭证；重复回调不重复记账，非法来源、错误类型、跨租户或非成功事实被拒绝，会计期间与冲销继续受现有闭环约束；成立后移除过时P0叶子。
Migration：不需要
依赖：现有付款闭环、收入回款闭环、`EntryGenerator`、两种生产策略、会计期间、复核/过账/冲销状态机及财务核算业务标准。
风险等级：高
运行态要求：当前本地 H2/MySQL；浏览器只读查看会计凭证列表/详情，不生成、复核、过账或冲销凭证。
Reviewer要求：确认不是仅存在类名而无真实调用；确认两类来源的租户、成功状态、来源关系、借贷金额、幂等与并发证据；确认 `accounting:add` 未因本轮扩大授权，手工生成端点仍只允许显式权限或管理员且不替代业务写侧自动生成。
归档报告：`docs/quality/ISSUE-048-010-会计凭证生成策略现状复核与P0问题关闭验收报告.md`
最小回滚：回退治理台账、项目地图、迭代决策与验收报告；不修改会计、付款、回款代码、迁移或业务数据。
目标：

- 以当前源码、业务标准和自动化证明付款、回款两类生产凭证策略真实可达，而非只做静态存在性判断。
- 验证自动生成的来源唯一、借贷平衡、租户隔离、成功状态、期间写保护和冲销链边界。
- 若全部成立，关闭唯一P0叶子并更新A-01剩余数量；若任一关键事实不成立，保留原问题并登记一个可复现的唯一剩余缺口。
  非目标：
- 不新增来源类型、科目映射、手工凭证入口、角色授权、迁移、报表、多账簿、多币种、税务或外部财务系统。
- 不创建、复核、过账、冲销真实业务凭证，不修改历史会计、付款、回款、银行或现金日记事实。
  允许修改：
- `backend/src/test/java/com/cgcpms/accounting/**`
- `backend/src/test/java/com/cgcpms/payment/**`
- `backend/src/test/java/com/cgcpms/revenue/**`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/ad-hoc-plan.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-010-会计凭证生成策略现状复核与P0问题关闭验收报告.md`
  禁止修改：
- `backend/src/main/**`
- `frontend-admin/src/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 当前生产 Spring 上下文注册且业务写侧真实调用 `PAY_RECORD`、`COLLECTION_RECORD` 两种策略；成功付款生成 `PAYMENT`，成功回款生成 `COLLECTION`，均为 `DRAFT/PENDING`。
- 付款凭证借记应付、贷记对应资金账户；回款按已分配/未分配金额贷记应收/预收并借记资金账户；总借方等于总贷方，来源ID、项目、合同和资金账户可追溯。
- 同租户同来源同凭证类型重复生成或并发重复银行回调只保留一份会计事实；跨租户、非成功事实、错误凭证类型及未知来源明确拒绝，不返回伪成功空结果。
- 会计期间、复核、过账和冲销继续受既有业务标准与专项测试保护；本轮不授予 `accounting:add`，不新增前端手工生成入口。
- 后端付款/回款/月结专项、控制器权限专项、Ready lint、允许/禁止路径和 `git diff --check` 全部通过；真实页面只读且无新增控制台错误。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-010`
- `cd backend; .\mvnw.cmd "-Dtest=AccountingEntryControllerTest,PaymentApplicationClosedLoopIntegrationTest,RevenueCollectionClosedLoopIntegrationTest,FinancialAccountingMonthEndClosedLoopIntegrationTest" test`
- `git diff --check`

### ISSUE-048-009：系统用户编辑详情权威加载

优先级：P0
任务性质：缺口修复
类型：系统用户 / 详情端点 / 编辑一致性 / 权限租户 / 陈旧响应
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-09`；candidateEvidenceHead=c06f49f1637bc21d7ad7941afea51e59bbf1c1af
存量问题键：[stock:A-01-SYSTEM-USER-DETAIL]
关联产品目标：让管理员编辑用户时以当前服务端详情和角色映射为准，避免分页行快照过期后覆盖其他管理员刚完成的用户或角色变更，同时关闭既有P0兼容性待确认项。
阻塞证据：`GET /system/users/{id}` 已按租户返回 `SysUserVO` 与角色ID/名称，用户管理页编辑入口却直接复制分页行；现有前端API模块没有详情方法，详情端点无法形成用户入口。
解除条件：用户编辑先按ID加载详情，成功后才填表并打开弹窗；加载失败不回退旧行数据，快速切换目标时只接受最后一次响应；权限、租户与无密码字段边界保持现状。
Migration：不需要
依赖：现有用户列表、用户详情端点、用户编辑弹窗、角色映射、`system:user:query`、管理员角色和租户拦截。
风险等级：中
运行态要求：当前本地 backend/Vite/MySQL；浏览器只读打开详情弹窗，不保存、不分配角色、不改变用户状态。
Reviewer要求：确认详情响应不含密码/密码哈希；管理员和 `system:user:query` 可读、无关权限403、跨租户 `USER_NOT_FOUND`；详情失败不得用分页行继续编辑；响应顺序不能让旧用户覆盖新用户。
归档报告：`docs/quality/ISSUE-048-009-系统用户编辑详情权威加载验收报告.md`
最小回滚：回退前端详情API与编辑前加载逻辑，恢复分页行填表；后端端点和数据不变。
目标：

- 为前端API补齐现有用户详情GET，并由用户管理编辑入口消费。
- 以详情返回的用户名、姓名、手机、邮箱和角色ID填表，密码始终留空；加载失败不打开弹窗。
- 为快速重复点击提供最小陈旧响应防护，新增用户动作同时使在途编辑详情失效。
  非目标：
- 不新增用户详情独立页面，不修改用户表、角色表、权限码、密码策略或更新事务。
- 不展示密码、密码哈希、令牌、登录历史、审计日志、项目成员关系或跨租户用户。
  允许修改：
- `frontend-admin/src/api/modules/user.ts`
- `frontend-admin/src/pages/system/users/index.vue`
- `frontend-admin/src/pages/system/users/__tests__/**`
- `backend/src/test/java/com/cgcpms/system/**`
- `backend/src/test/java/com/cgcpms/contract/TenantIsolationTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/ad-hoc-plan.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/competitor-analysis.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-009-系统用户编辑详情权威加载验收报告.md`
  禁止修改：
- `backend/src/main/java/**`
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/src/pages/system/roles/**`
- `frontend-admin/src/pages/system/permissions/**`
- `deploy/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- `getUserDetail(id)` 固定调用 `GET /system/users/{id}`；编辑入口必须等待详情成功后再打开弹窗，不以列表行作为失败兜底。
- 详情表单只映射 `SysUserVO` 允许字段，密码为空；角色使用详情 `roleIds`，不把 `tenantId`、密码或其他服务端字段送回更新载荷。
- 快速点击不同用户时，先返回的旧请求不能覆盖最后目标；点击新增时使既有详情请求失效。
- 详情GET仅管理员或 `system:user:query` 通过，无关权限403；跨租户保持 `USER_NOT_FOUND`，响应VO无密码字段。
- 后端权限/租户专项、前端专项、类型、ESLint、Ready lint、允许/禁止路径和 `git diff --check` 全部通过；真实页面只读打开详情弹窗并观察到GET，无新增控制台错误。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-009`
- `cd backend; .\mvnw.cmd "-Dtest=SysUserDetailControllerTest,TenantIsolationTest#testCrossTenantUserRead" test`
- `cd frontend-admin; pnpm vitest run src/pages/system/users/__tests__/user-detail.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm eslint src/api/modules/user.ts src/pages/system/users/index.vue src/pages/system/users/__tests__/user-detail.test.ts`
- `git diff --check`

### ISSUE-048-008：库存历史净领料基线

优先级：P1
任务性质：能力新增
类型：库存 / 历史领料 / 退料冲减 / 30与90日窗口 / 只读基线
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-08`；candidateEvidenceHead=0d71054a4934552a112b213de6681969ef7f4552
存量问题键：[stock:A-02-MATERIAL-CONSUMPTION-BASELINE]
关联产品目标：在引入需求预测算法前，让采购和库存人员先看到当前库存项可审计的历史净领料事实，避免把缺少口径的数据直接包装成预测或自动采购建议。
阻塞证据：`mat_stock_txn` 已记录领料 `MAT_REQUISITION/OUT` 和退料 `MATERIAL_RETURN/IN`，但库存页只能看明细流水，不能按稳定窗口判断30/90日领用与退回规模；A-02的需求预测因此缺少可见、可验真的输入基线。
解除条件：从当前库存项服务端反查租户、项目、仓库和物料，以同一次查询返回含今日的30/90个本地自然日内领料、退料和净领料数量；页面明确标注历史事实而非预测，失败不阻断库存台账。
Migration：不需要
依赖：现有库存、仓库、库存流水、领料出库、材料退料、项目数据范围、库存查询权限和当前 V211 基线。
风险等级：中
运行态要求：当前本地 MySQL/Vite/backend；端点和页面均只读，不创建领料、退料、库存流水或采购申请。
Reviewer要求：确认租户/项目/仓库/物料隔离，30/90日边界含今日且不重叠误算；只计 `MAT_REQUISITION/OUT` 与 `MATERIAL_RETURN/IN`，排除调拨、验收、供应商退货和其他流水；净值允许因窗口外领料、本窗口退料而为负，不得钳零或宣称预测。
归档报告：`docs/quality/ISSUE-048-008-库存历史净领料基线验收报告.md`
最小回滚：回退只读端点、VO、聚合查询和前端基线卡片；不涉及迁移、历史流水或采购事实。
目标：

- 为当前库存项提供30/90日历史领料、退料和净领料数量，范围完全由服务端库存与仓库关系确定。
- 用一次条件聚合查询完成两个窗口，不加载全量流水，不增加预测表或缓存副本。
- 在库存分析区展示只读基线、窗口起止和明确的“非预测”说明；加载失败只显示局部状态。
  非目标：
- 不计算日均、周转天数、缺货日期、建议采购量、工作日历、季节性、趋势外推、置信区间或自动下单。
- 不把跨仓调拨、材料验收、供应商退货、库存调整或通用手工流水计入领料需求，不修改领料/退料写侧。
  允许修改：
- `backend/src/main/java/com/cgcpms/inventory/**`
- `backend/src/test/java/com/cgcpms/inventory/**`
- `frontend-admin/src/api/modules/inventory.ts`
- `frontend-admin/src/types/inventory.ts`
- `frontend-admin/src/pages/inventory/stock.vue`
- `frontend-admin/src/pages/inventory/composables/useStockLedger.ts`
- `frontend-admin/src/pages/inventory/components/StockAnalysisPanel.vue`
- `frontend-admin/src/pages/inventory/__tests__/**`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/ad-hoc-plan.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/competitor-analysis.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-008-库存历史净领料基线验收报告.md`
  禁止修改：
- `backend/src/main/java/com/cgcpms/requisition/**`
- `backend/src/main/java/com/cgcpms/materialreturn/**`
- `backend/src/main/java/com/cgcpms/purchase/**`
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/src/pages/purchase/**`
- `deploy/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 访问控制继续复用现有库存查询权限，管理员等价通过；权限码为 `inventory:stock:list`。库存不存在、跨租户或无项目访问权均按现有库存查询边界拒绝，不接受客户端项目、仓库或物料参数扩大范围。
- 30日窗口从当前本地日期减29日的零时整开始，90日窗口从减89日的零时整开始，截止同一服务端时刻；边界流水只计一次，返回窗口起止供页面解释。
- 领料只计 `txn_type=OUT AND source_type=MAT_REQUISITION`，退料只计 `txn_type=IN AND source_type=MATERIAL_RETURN`；净领料等于领料减退料并保留四位小数，允许负值。
- 一次聚合查询同时得到30/90日结果；其他租户、仓库、物料、调拨、验收、供应商退货及窗口外流水不进入统计，空结果全部为0。
- 前端在当前库存项分析区展示30/90日领料、退料、净领料和“历史事实、非需求预测”说明；切换库存不显示旧响应，局部失败不关闭台账或污染其他数据。
- 后端专项、前端专项、类型、ESLint、Ready lint、允许/禁止路径和 `git diff --check` 全部通过；真实页面只读且无新增控制台错误。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-008`
- `cd backend; .\mvnw.cmd "-Dtest=MatStockConsumptionBaselineServiceTest,MatStockConsumptionBaselineControllerTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/inventory/__tests__/stock-consumption-baseline.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm eslint src/pages/inventory/stock.vue src/pages/inventory/composables/useStockLedger.ts src/pages/inventory/components/StockAnalysisPanel.vue src/api/modules/inventory.ts src/types/inventory.ts src/pages/inventory/__tests__/stock-consumption-baseline.test.ts`
- `git diff --check`

### ISSUE-048-007：同项目跨仓库存调拨过账

优先级：P1
任务性质：能力新增
类型：库存 / 跨仓调拨 / 原子过账 / 价值守恒 / 幂等并发
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-07`；candidateEvidenceHead=5da5808847bd7385efc215cc6dff6fda4979c960
存量问题键：[stock:A-02-STOCK-TRANSFER-POSTING]
关联产品目标：把同项目跨仓可调拨余量从只读提示闭合为安全、可追溯且保持数量/价值守恒的真实库存动作。
阻塞证据：`getTransferCandidates` 已展示来源仓、可用量、安全库存和可调拨量，但当前没有调拨事实、写端点或前端动作，采购人员仍只能看到余量而不能利用余量替代补购。
解除条件：新增同项目即时调拨事实和双权限写端点，在单事务中完成来源扣减、目标增加、成对流水和幂等记录；前端只能从服务端候选发起并刷新库存。
Migration：需要
依赖：现有库存、仓库、物料、跨仓候选、库存价值、库存流水、项目数据范围、库存编辑与事务权限、当前 V210 基线。
风险等级：高
运行态要求：dev/test 本地 MySQL；先以自动化覆盖写链，真实页面只查看候选和调拨入口，不在共享开发库提交调拨；如需写验收仅使用满足重置门禁的一次性数据库。
Reviewer要求：确认不同租户/项目/物料/同仓拒绝，来源安全库存不能被突破，成对流水与调拨事实完全一致，数量和价值守恒，重复键/并发不会双扣，任一单权限不能过账。
归档报告：`docs/quality/ISSUE-048-007-同项目跨仓库存调拨过账验收报告.md`
最小回滚：代码可回退；V211表保留不删，未投产时可停用入口，已完成事实只能通过后续反向调拨冲正，禁止直接删流水或改库存。
目标：

- 新增调拨事实、DTO/VO、服务与独立端点，复用库存价值原语完成同项目同物料跨仓即时过账。
- 以同租户幂等键唯一、提交时重算安全余量、稳定并发控制和单事务成对流水保证不重扣、不超调、不留半边事实。
- 在库存分析区从服务端候选发起调拨；成功后刷新库存、候选和流水，权限不足不展示动作。
  非目标：
- 不恢复通用手工入/出库，不做跨项目/跨租户、审批流、在途运输、批次/序列号、费用或跨币种计价。
- 不修改采购在途、补货建议、安全库存计算、领料/验收入出库或历史流水；不在共享开发库写验收数据。
  允许修改：
- `backend/src/main/java/com/cgcpms/inventory/**`
- `backend/src/test/java/com/cgcpms/inventory/**`
- `backend/src/main/resources/db/migration/V211__add_stock_transfer_posting.sql`
- `frontend-admin/src/api/modules/inventory.ts`
- `frontend-admin/src/types/inventory.ts`
- `frontend-admin/src/pages/inventory/stock.vue`
- `frontend-admin/src/pages/inventory/components/StockAnalysisPanel.vue`
- `frontend-admin/src/pages/inventory/__tests__/**`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-007-同项目跨仓库存调拨过账验收报告.md`
  禁止修改：
- `backend/src/main/java/com/cgcpms/purchase/**`
- `backend/src/main/java/com/cgcpms/requisition/**`
- `backend/src/main/java/com/cgcpms/workflow/**`
- `backend/src/main/resources/db/migration/V1__init.sql`～`V210__drop_obsolete_deleted_tokens.sql`
- `frontend-admin/src/pages/purchase/**`
- `deploy/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 调拨端点仅管理员或同时拥有 `inventory:stock:edit` 与 `inventory:transaction:add` 的用户可访问，任一单权限403；服务端校验租户、项目数据范围、两端启用仓库、同项目、不同仓、同物料。
- 数量必须大于0且不超过提交时来源 `available_qty - safety_stock_qty`；并发调拨总量不能突破安全库存，失败不得留下调拨事实、单边库存或单边流水。
- 首次成功只产生一条 `COMPLETED` 调拨事实和两条共用调拨ID的 `STOCK_TRANSFER` 流水；来源 `OUT`、目标 `IN` 数量相等，单位成本取来源历史成本，项目总数量与总价值前后相等。
- 同租户同幂等键相同载荷返回原结果，不重复扣增；相同键不同载荷冲突。不同租户可复用同一键且数据互不可见。
- 前端仅从当前库存的服务端候选发起，限制最大可调拨量并提交原因/幂等键；权限不足不展示，成功刷新列表/候选/流水，失败保留当前页面并给出错误。
- MySQL V1→V211、后端专项/并发测试、前端专项、类型、ESLint、Ready lint、允许/禁止路径和 `git diff --check` 全部通过；真实页面不产生新增控制台错误。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-007`
- `cd backend; .\mvnw.cmd "-Dtest=MatStockTransferServiceTest,MatStockTransferControllerTest,MatStockTransferConcurrencyTest,FlywayMySqlSmokeTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/inventory/__tests__/stock-transfer.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm eslint src/pages/inventory/stock.vue src/pages/inventory/components/StockAnalysisPanel.vue src/api/modules/inventory.ts src/types/inventory.ts src/pages/inventory/__tests__/stock-transfer.test.ts`
- `git diff --check`

### ISSUE-048-006：现场日报当日质量安全检查只读联动

优先级：P1
任务性质：能力新增
类型：现场日报 / 质量安全 / 同日检查 / 权限交集 / 只读聚合
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-06`；candidateEvidenceHead=3013344e44d97808b624b00fdbe8ff62000be3f0
存量问题键：[stock:A-03-QUALITY-SAFETY-DAILY-FACTS]
关联产品目标：在不复制质量安全事实的前提下，让具备双域权限的现场管理人员从日报看到当天已提交检查及问题概况。
阻塞证据：日报已联动到货、领料、WBS和审计，但尚未展示现有 `qs_inspection_record`/`qs_issue`；A-03仍明确缺少质量安全子域联动。
解除条件：新增独立只读端点，权限必须同时满足日报查询和质量安全查询；只返回同租户、同项目、日报日期、已提交检查的最小聚合字段，并在日报详情提供受权限控制的空态/失败态。
Migration：不需要
依赖：现场日报、质量安全检查/问题、项目数据范围、现有用户权限、当前 V210 开发基线。
风险等级：高
运行态要求：当前本地 MySQL/Vite/backend；浏览器只打开日报详情，不创建、提交、整改或复验任何数据。
Reviewer要求：确认双域权限不能被任一单权限绕过；确认租户/项目/日期/已提交状态和稳定排序；确认只返回计数与检查摘要，不泄露问题描述、责任人、合作方、金额或整改内容。
归档报告：`docs/quality/ISSUE-048-006-现场日报当日质量安全检查只读联动验收报告.md`
最小回滚：回退独立只读端点、VO、前端调用和展示；不涉及 schema、质量安全写侧或历史数据。
目标：

- 以日报 ID 服务端反查租户、项目和日期，聚合当天已提交质量安全检查及问题计数。
- 前端仅在管理员或拥有 `quality:safety:query` 时调用和展示，失败不阻断日报正文。
- 关闭唯一叶子，A-03继续承接人员班组、设备、自动天气、定位、离线和统计。
  非目标：
- 不从日报创建/修改检查、问题、整改、复验或后果；不新增表、字段、权限或迁移。
- 不展示问题描述、责任人、合作方、附件、金额、整改动作；不把草稿检查或日报日期外事实计入。
  允许修改：
- `backend/src/main/java/com/cgcpms/site/**`
- `backend/src/test/java/com/cgcpms/sitedaily/**`
- `frontend-admin/src/api/modules/site-daily-log.ts`
- `frontend-admin/src/types/site-daily-log.ts`
- `frontend-admin/src/pages/site/daily-log.vue`
- `frontend-admin/src/pages/site/__tests__/daily-log.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-006-现场日报当日质量安全检查只读联动验收报告.md`
  禁止修改：
- `backend/src/main/java/com/cgcpms/quality/**`
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/src/pages/quality-safety/**`
- `frontend-admin/src/api/modules/qualitySafety.ts`
- `deploy/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 新端点先按租户隐藏不存在/跨租户日报，再校验项目访问；Controller同时要求日报查询与质量安全查询，管理员可访问，任一单权限均为403。
- 只查询同租户、同项目、日报日期且状态为 `SUBMITTED` 的检查；问题必须同时限定租户、项目和检查ID，按检查编号/ID稳定排序，无检查返回空列表且无N+1。
- 每条仅返回检查ID、编号、地点、结论、问题总数、高风险数、未关闭数；不返回问题正文、责任主体、附件、金额或整改详情。
- 前端无质量权限不请求；有权限时展示加载、空、失败和数据状态，失败不关闭日报详情，不提供质量安全写操作。
- 后端专项、前端专项、类型、ESLint、Ready lint、允许/禁止路径和 `git diff --check` 通过；真实页面只读且无新增控制台错误。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-006`
- `cd backend; .\mvnw.cmd "-Dtest=SiteDailyQualitySafetyServiceTest,SiteDailyLogControllerTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/site/__tests__/daily-log.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-048-005：本地开发 MySQL 迁移基线重建

优先级：P1
任务性质：运维治理
类型：MySQL / Flyway / 本地开发基线 / 备份恢复 / 运行态
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-05`；candidateEvidenceHead=9bd7c16f2cf61b7ad1a6e2db7fc708ba36d23c71
存量问题键：[stock:OPS-DEV-MYSQL-FLYWAY-DRIFT]
关联产品目标：恢复当前 master 的本地开发 MySQL 权威基线，使供应商及后续跨域功能可以在 Flyway 启用的真实运行态验收。
阻塞证据：`flyway:validate` 显示 V188～V203 校验和与当前文件错位、V181/V183 未应用，数据库停在旧 V203 且缺少当前 V204～V210；供应商三张 V189 表缺失，当前后端查询返回500。
解除条件：完整逻辑备份通过独立恢复读回后，只重建 localhost:3307 的 `cgc_pms`；当前迁移 V1→V210 与 validate 通过，当前 backend 健康、开发登录和供应商只读接口通过。
Migration：不需要
依赖：`cgc-pms-mysql-dev`、`cgc-pms-backend-dev`、`.codex-autopilot/ALLOW_TEST_DATA_RESET`、当前 master 后端 JAR、受控本地备份目录。
风险等级：高
运行态要求：数据库 host 必须是 `127.0.0.1:3307`/Docker 本地网络且 sentinel 存在；先备份并在一次性 MySQL 恢复验真，再重建；不得连接生产或远程数据库。
Reviewer要求：确认备份先于重建且可恢复；确认只操作 `cgc_pms`，不改迁移历史文件或其他 schema；确认 V1→V210、validate、表清单、健康、开发登录和供应商 GET 均有读回证据。
归档报告：`docs/quality/ISSUE-048-005-本地开发MySQL迁移基线重建验收报告.md`
最小回滚：停止 backend，删除重建后的本地 `cgc_pms`，从已验真的完整逻辑备份恢复原库并重启；不依赖 Git 回退。
目标：

- 为当前本地开发库生成完整逻辑备份，记录 SHA-256、大小、表数和 Flyway 末版本，并在一次性 MySQL 中恢复读回。
- 在三重门禁成立后重建本地 `cgc_pms`，由当前 master Flyway 从 V1 完整执行到 V210，消除错位历史。
- 复验当前 backend、开发登录和供应商只读链路，关闭唯一运维风险。
  非目标：
- 不修改迁移 SQL、业务代码、Docker 编排、密钥、生产或远程数据库；不尝试用 `flyway repair` 掩盖错位历史。
- 不把旧开发测试数据合并进新基线；原数据只保留在已验真的回滚备份中。
  允许修改：
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-005-本地开发MySQL迁移基线重建验收报告.md`
  禁止修改：
- `backend/src/main/**`
- `backend/src/test/**`
- `frontend-admin/src/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
  验收标准：
- 操作前确认 `127.0.0.1` 的3307端口、容器名和 sentinel 三项全部成立；备份非空、SHA-256固定，并在一次性 MySQL 恢复后读回原表数、原 Flyway 末版本和关键表。
- 只删除并重建本地 `cgc_pms`；当前迁移从 V1 完整执行到 V210，`flyway_schema_history` 全部成功且 `flyway:validate` 返回0。
- 重建库含当前供应商三表及 V210，当前 backend 以 Flyway 启用方式健康；开发登录成功，供应商 events/performance/returns GET 均不再因缺表失败。
- Git 仅含允许治理文档；Ready lint、允许/禁止路径和 `git diff --check` 通过；所有一次性验证容器均精确删除，备份保留用于回滚。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-005`
- `cd backend; .\mvnw.cmd "-Dtest=FlywayMySqlSmokeTest" test`
- `git diff --check`

### ISSUE-048-004：供应商综合履约现状复核与聚合问题裁决

优先级：P1
任务性质：回归证明
类型：供应商履约 / 质量与商业评分 / 退货 / 黑名单 / 聚合问题裁决
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-04`；candidateEvidenceHead=5a7602083388fb206e1a1b03965c6234352e810d
存量问题键：[supplier:A-05-CLOSED-LOOP-RECONCILIATION]
关联产品目标：纠正 A-05 “当前只有交付维度”的过时判断，以当前实现和运行证据确认供应商综合履约闭环是否已成立，避免重复建设。
核验结论：现有 `SupplierSourcingService` 已将交付、验收质量、质量安全事实、人工服务分、确认退货和结算扣款汇总为30/35/15/20权重的履约评价，并具备确认锁定、黑名单申请/异人审核和招采准入阻断；前端已有可达工作台。
阻塞证据：唯一问题载体仍把质量、价格、退货、服务和黑名单全部列为缺失，与当前代码事实冲突，导致产品地图和后续选题可能重复。
解除条件：后端闭环、权限/租户/项目/状态负向、前端契约、真实 MySQL 只读请求和真实页面只查看全部通过后关闭 A-05；任一关键事实不成立则保留 A-05 并登记唯一可复现缺口。
Migration：不需要
依赖：既有供应商招采服务、采购订单、验收、供应商退货、结算、质量安全评价、合作方黑名单、权限与项目数据范围。
风险等级：高
运行态要求：本地 H2/MySQL 与 Vite；浏览器只选择项目、查看履约/退货/黑名单和追溯入口，不创建、确认、提交或审核任何业务数据。
Reviewer要求：确认评分事实和权重与代码一致；确认评价必须交付完成、存在已审批验收及已定案结算；确认黑名单只能基于已确认低分评价、禁止自审且审批后阻断供应商准入；确认不能把招采报价分或人工服务分包装成客观价格质量事实。
归档报告：`docs/quality/ISSUE-048-004-供应商综合履约现状复核与聚合问题裁决验收报告.md`
最小回滚：仅回退本次现状裁决、报告和问题载体回写；不修改业务代码、数据库或业务数据。
目标：

- 以当前代码、自动化和真实只读运行态证明供应商综合履约的事实来源、权限、状态与用户入口。
- 对 A-05 作唯一裁决：证据完整则关闭，证据不完整则保留并精确登记剩余缺口。
- 同步项目地图与迭代决策，消除“只有交付维度”的过时描述。
  非目标：
- 不修改评分公式、权重、供应商、订单、验收、退货、结算、质量安全、黑名单、权限或数据库。
- 不创建验收夹具，不连接生产，不发布，不 push，不扩展供应商门户、自动评分或外部征信。
  允许修改：
- `backend/src/test/java/com/cgcpms/supplier/**`
- `frontend-admin/src/pages/supplier-sourcing/__tests__/**`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-004-供应商综合履约现状复核与聚合问题裁决验收报告.md`
  禁止修改：
- `backend/src/main/java/**`
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/src/pages/supplier-sourcing/index.vue`
- `frontend-admin/src/api/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
- `deploy/**`
  验收标准：
- 后端证明交付、验收质量、质量安全、人工服务、确认退货、已定案结算及扣款进入既有评分，权重为30/35/15/20；未完成交付、无已审批验收或无已定案结算不得生成评价。
- 评价只允许同租户且有项目访问权的用户读取/生成，确认后不可重复确认；黑名单只允许已确认且触发建议的评价发起，提交人与审核人分离，审批通过后合作方进入黑名单并被招采准入拒绝。
- 前端工作台明确展示交付、质量、服务、商业、总分、等级、退货和黑名单状态，并按招采、履约评价、黑名单审核权限分离动作。
- 真实 MySQL 仅执行 GET/页面读取；浏览器无新增错误，不产生 POST/PUT/PATCH/DELETE 请求或业务数据变化。
- 目标测试、类型检查、Ready lint、允许/禁止路径和 `git diff --check` 通过；所有发现项完成本轮修复、正式承接或有依据关闭。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-004`
- `cd backend; .\mvnw.cmd "-Dtest=SupplierSourcingClosedLoopIntegrationTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/supplier-sourcing/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-040-056：WBS单前置FS门禁与风险提示回归

优先级：P1
任务性质：回归证明
类型：WBS / 单前置FS / 状态门禁 / 环依赖 / 项目隔离 / 单次提示
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/product-intelligence/project-map.md`; candidateEvidenceHead=f332d7462783d39ad9873480ce338570f6af2e88
存量问题键：[stock:OBS-WBS-PREDECESSOR-EVIDENCE]
关联产品目标：证明现有单前置FS门禁与前端风险提示在当前master保持一致，明确它不等于多前置和自动排程平台。
候选对比：现有Controller和前端任务测试已覆盖跨项目、环依赖、未完成门禁与迟交提示，独立收口成本低于扩关系表和排程引擎。
核验结论：SubTaskControllerTest覆盖前置关联、环检测、跨项目和未完成状态门禁；前端task测试覆盖前置未完成/迟交展示与错误单次提示。
阻塞证据：A-04概括多前置与平台能力仍缺，缺少当前master正式证据时容易低估既有单前置FS闭环或把它外推为完整排程。
解除条件：后端与前端目标测试通过，真实浏览器只打开/取消任务弹窗，正式报告关闭观察项。
Migration：不需要
依赖：既有 SubTask 单前置字段、状态机、任务页与目标测试。
风险等级：中
运行态要求：本地 H2/MySQL 与 Vite；浏览器只打开/取消，不写入任务。
Reviewer要求：确认跨项目、环依赖、未完成前置和单次提示边界；不得宣称多前置、lag、多类型、自动排程、基线或资源平衡。
归档报告：`docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md`
最小回滚：仅回退本项治理文档与报告。
目标：

- 证明单前置FS状态门禁、项目边界、环检测和前端风险提示在当前master生效。
- 明确A-04既有最小闭环与平台化缺口。
  非目标：
- 不修改业务代码、数据库、测试实现或运行数据。
- 不新增多前置、SS/FF/SF、lag、自动排程、基线、资源平衡或甘特拖拽。
- 不连接生产、不发布生产、不push。
  允许修改：
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md`
  禁止修改：
- `backend/**`
- `frontend-admin/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
- `deploy/**`
  验收标准：
- `SubTaskControllerTest` 全部通过，覆盖前置关联、未完成门禁、跨项目、环依赖和解除前置。
- `task.test.ts` 全部通过，覆盖未完成/迟交风险和错误单次提示；真实浏览器只取消不写入。
- Ready lint、允许路径、`git diff --check`通过；观察项关闭且新增后续项0、关闭1、净变化-1。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-056`
- `cd backend; .\mvnw.cmd "-Dtest=SubTaskControllerTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/subcontract/__tests__/task.test.ts`
- `git diff --check`

## v1.5 准入要求

每条 Ready Issue 必须包含：编号、任务性质、目标、范围、非目标、验收标准、真实存在的验证命令、风险与回滚方式、归档报告路径。

只有状态为 `Ready` 且字段完整的任务才能进入 AutoPilot 实施；候选项不能直接执行。

## 历史 Issue 主题

- [ISSUE-047-003：日报Controller测试JWT密钥环境隔离修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-052：日报人工天气摘要与在场人数契约回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-053：供应商交付评分项目与空值边界回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-054：现金日记账CSV公式注入与导出契约回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-055：站内通知租户用户隔离与已读幂等回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-047-002：V155双数据库迁移镜像与H2测试隔离修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-051：供应商默认提前期与采购订单交货日期预填](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-050：现场日报领料联动真实浏览器视觉回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-049：WBS前置门禁错误单次提示与行为回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-048：PowerShell 7控制面与UTF-8兼容回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-047：WBS软删除墓碑事务故障注入回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-046：库存阈值与出库真实并发防覆盖回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-045：补货设置权限项目与乐观锁负向回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-044：驾驶舱性能测试阈值文案对齐](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-043：间接费规则受控删除入口与执行事实保护](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-042：间接费规则受控修改入口与字段状态边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-041：间接费规则受控新建入口与科目租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-040：投标成本标记未中标入口与费用核销边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-047-001：AutoPilot 收口区块边界与幂等误判修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-039：投标成本标记中标入口与状态项目边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-038：投标成本受控删除入口与状态租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-037：投标成本受控修改入口与状态租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-036：项目受控归档入口与项目数据范围](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-035：间接费规则只读列表入口与租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-034：工作流个人效率统计只读入口与数据范围](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-033：投标成本详情只读入口与租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-032：投标成本受控新建入口与租户状态边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-030：成本汇总历史只读入口与项目数据边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-031：投标成本只读列表入口与租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-029：共享列表中窄视口表格高度链修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-028：系统角色修改管理员入口与安全边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-027：系统角色删除管理员入口与安全边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-018：AutoPilot 存量问题优先补货与闭环门禁](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-017：知识图谱当前问题结构化查询](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-012～016：A-01 财务核算五项接口入口治理](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-001：预警批量评估独立权限码修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-022：Windows PowerShell 5.1 AutoPilot 脚本编码兼容修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-017：BaseEntity 备注写入契约修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-016：WBS 软删除编号冲突修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-015：WBS 单前置 FS 开工门禁](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-014：现场日报当日已审批领料只读联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-013：现场日报变更历史只读展示](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-012：现场日报当日计划任务只读联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-011：现场日报已审批材料到货只读联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-005：现场日报最小闭环](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-009：库存项人工补货目标量联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-010：库存项人工补货提前期与计划日期预填](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-008：现场日报 dev-login 直达路由白名单修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-007：现场日报天气摘要与在场人数补强](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-006：库存项安全库存阈值与补货建议联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-018：子智能体超时、悬挂执行线程退役与有限重派治理](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-019：后端接口无前端入口只读盘点与治理裁决](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-020：长期计划描述性标题误入候选修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-021：CI/CD 与上线门禁 v1.5 现状复验与红灯分类裁决](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-019：系统菜单新建管理员入口与树约束](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-020：系统菜单删除管理员入口与安全约束](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-021：系统菜单详情管理员入口与权限边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-022：系统菜单平铺列表管理员入口与权限边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-023：系统菜单修改管理员入口与树结构约束](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-024：间接费执行分摊用户入口与金额安全边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-025：系统角色新建管理员入口与权限边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-026：系统角色详情管理员入口与权限边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)

## 当前 Ready Issue

### ISSUE-048-001：分包付款来源业务单据选择器与可付余额

优先级：P1
任务性质：能力新增
类型：分包付款 / 业务单据选择 / 可付余额 / 租户项目隔离 / 并发金额边界
状态：Done
来源锚点：`docs/backlog/ad-hoc-plan.md` 的 `SUBCONTRACT-CL-P1-001`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-01`；`docs/business/project-subcontract-performance-settlement-payment-closed-loop.md` 第 1.2、8.4、10 章；candidateEvidenceHead=4b3bb540a3ada3373c8b34d8f5d63eb243dc6de0
关联产品目标：在不改变 P0 付款事实口径的前提下，消除分包进度款与终期款来源单据 ID 的手工录入，并让申请人看到当前可申请余额。
候选对比：退款/应收、审批节点结构化审定和清单版本失效都缺少独立财务或审批口径；本项只读复用现有来源校验与占用模型，范围最小且不新增数据模型。
核验结论：`PaymentApplicationSourceService` 已校验 `SUB_MEASURE+PROGRESS+SUBCONTRACT`、`SETTLEMENT+FINAL`、租户/项目/合同/付款对象一致性及审批中/已审批占用；付款页面目前仍让用户输入来源单据 ID。
阻塞证据：人工输入 ID 无法在选择前确认单据是否为当前上下文的可付来源，也无法展示既有服务已计算的可申请余额，易造成无效提交和重复人工核对。
解除条件：分包两条付款路径的来源选择器只返回当前租户、项目、合同、付款对象和状态均合格的业务单据，并显示服务端余额；保存/提交仍以既有服务复核为最终裁决。
Migration：不需要
依赖：既有 `PaymentApplicationSourceService`、付款申请来源表、分包计量和终期结算的 P0 状态机与项目数据范围。
风险等级：高
运行态要求：本地 H2/MySQL 与 Vite；浏览器仅创建草稿并取消，不执行付款、审批或写回。
Reviewer要求：确认候选查询受付款查询权限、租户和项目数据范围约束；确认页面余额不替代提交时的权威金额/并发校验，且不扩展退款、应收、审批或清单版本口径。
归档报告：`docs/quality/ISSUE-048-001-分包付款来源业务单据选择器与可付余额验收报告.md`
最小回滚：仅回退来源候选查询和前端选择器，恢复既有手工 ID 输入；不回滚或改写任何付款、预算、结算、计量或现金事实。
目标：

- 为 `SUB_MEASURE` 进度款与 `SETTLEMENT` 终期款提供基于项目、合同、付款对象、付款类型和费用分类的只读业务单据候选。
- 在候选中展示服务端计算的可申请余额，并由页面选择单据而非人工输入来源单据 ID。
- 保持既有保存、提交、额度占用、付款、冲销和并发校验为唯一写入与最终裁决路径。
  非目标：
- 不重构 `EXPENSE` 或 `DIRECT` 来源交互，不新增或修改付款来源类型、金额公式、审批状态机、预算占用、退款/应收或合同清单版本策略。
- 不新增数据库迁移、数据回填、历史事实修改、外部支付、生产连接、发布或 push。
  允许修改：
- `backend/src/main/java/com/cgcpms/payment/**`
- `backend/src/test/java/com/cgcpms/payment/**`
- `frontend-admin/src/api/modules/payment.ts`
- `frontend-admin/src/pages/payment/**`
- `frontend-admin/src/types/payment.ts`
- `docs/backlog/ad-hoc-plan.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-001-分包付款来源业务单据选择器与可付余额验收报告.md`
  禁止修改：
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/java/com/cgcpms/settlement/**`
- `backend/src/main/java/com/cgcpms/subcontract/**`
- `frontend-admin/src/router/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
- `deploy/**`
  验收标准：
- 候选查询只能返回当前租户、项目、合同、付款对象及两条合法付款路径均匹配的已审批可付款来源；不匹配付款类型/费用分类、跨项目、跨对象、未审批、未定案或已进入终期结算的计量均不得显示。
- `SUB_MEASURE` 候选余额等于既有净额扣除其他审批中/已审批申请占用；`SETTLEMENT` 候选余额等于既有定案额扣除已付与其他审批中/已审批申请占用；余额小于等于零时不得显示。
- 页面将两类来源从 ID 输入改为选择器并显示余额；页面余额不绕过或替代保存/提交时的既有来源校验。
- 后端和前端目标测试、类型检查、Ready lint 与 `git diff --check` 通过；正式报告结论完整，新增后续项、关闭后续项和净变化明确。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-001`
- `cd backend; .\mvnw.cmd "-Dtest=PayApplicationServiceTest,PaymentApplicationClosedLoopIntegrationTest,PayApplicationControllerTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/payment/__tests__/index.test.ts src/pages/payment/__tests__/save-chain.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-048-003：采购前已审批在途余量提示

优先级：P1
任务性质：能力新增
类型：库存补货 / 已审批采购在途 / 项目隔离 / 只读决策辅助
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-03`；candidateEvidenceHead=00e9a632a214de52c55146ddafd58d77e99759b7
存量问题键：[stock:A-02-APPROVED-INCOMING-SUPPLY]
关联产品目标：在低库存发起采购前展示已有已审批采购订单的未收货数量和预计交付日期，减少重复补货判断。
候选对比：工作日历需要新增日期规则，历史消耗预测缺少审定算法，真实调拨需要完整写侧模型；本项只读复用采购订单、明细和已收货数量，边界最小。
核验结论：采购订单已有项目、交付日期、审批和业务状态；明细已有物料、采购数量与已收货数量，库存服务已具备当前库存项的租户和项目范围校验。
阻塞证据：库存补货页当前只显示库存、台账和跨仓余量，看不到已审批订单剩余到货量，用户可能在已有采购在途时再次发起采购申请。
解除条件：服务端从当前库存项反查项目和物料，仅返回已审批、未完成、交付日期明确且剩余量为正的同项目订单；页面只读展示且状态、租户、项目、数量和空结果边界通过验证。
Migration：不需要
依赖：既有 `MatStockService`、`MatPurchaseOrder`、`MatPurchaseOrderItem`、项目数据范围、库存查询权限和库存台账页面。
风险等级：高
运行态要求：本地 H2/MySQL 与 Vite；浏览器只查询和查看提示，不创建采购申请、不修改订单、验收或库存。
Reviewer要求：确认项目和物料仅由当前库存项服务端反查；确认只计订单与审批状态均为 `APPROVED`、交付日期非空的订单；确认剩余量按订单汇总 `max(quantity-receivedQuantity,0)` 且提示不等于已入库。
归档报告：`docs/quality/ISSUE-048-003-采购前已审批在途余量提示验收报告.md`
最小回滚：回退只读接口、VO、前端类型与展示，不改采购订单、验收、库存流水或数据库。
目标：

- 为当前库存项返回同项目同物料的已审批采购订单未收货余量。
- 在库存辅助分析中展示订单号、预计交付日期和剩余数量，并明确其为未入库查询快照。
- 保持采购、验收与库存写入继续走既有受控业务单据。
  非目标：
- 不计草稿、审批中、驳回、已完成或无交付日期订单，不修改订单或收货事实。
- 不实现工作日历、统计需求预测、调拨、预占、自动采购、迁移、历史回填、生产连接、发布或 push。
  允许修改：
- `backend/src/main/java/com/cgcpms/inventory/**`
- `backend/src/test/java/com/cgcpms/inventory/**`
- `frontend-admin/src/api/modules/inventory.ts`
- `frontend-admin/src/types/inventory.ts`
- `frontend-admin/src/pages/inventory/**`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-003-采购前已审批在途余量提示验收报告.md`
  禁止修改：
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/java/com/cgcpms/purchase/**`
- `backend/src/main/java/com/cgcpms/receipt/**`
- `backend/src/main/java/com/cgcpms/procurement/**`
- `frontend-admin/src/router/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
- `deploy/**`
  验收标准：
- 当前库存项不存在、跨租户、仓库停用或无项目访问权时不可获取；客户端不能通过传入项目、物料或订单扩大范围。
- 仅计同租户、同项目、同物料、`approvalStatus=APPROVED`、`orderStatus=APPROVED`、交付日期非空的订单明细；单订单剩余量为各明细 `max(quantity-receivedQuantity,0)` 之和，只返回正数。
- 结果按预计交付日期、订单 ID 升序稳定排序；数量精确到4位，超收明细按0处理，不得产生负数。
- 页面只在已加载库存项时请求并展示订单号、预计交付日期、剩余数量和“未入库快照”提示；空结果和请求失败不阻断现有台账、跨仓提示与采购补货入口。
- 后端和前端目标测试、类型检查、Ready lint、允许路径与 `git diff --check` 通过；真实 MySQL 只读请求和真实页面只查看验收通过。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-003`
- `cd backend; .\mvnw.cmd "-Dtest=MatStockServiceTest,MatStockControllerTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/inventory/__tests__/stock-production.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-048-002：采购补货前同项目跨仓可调拨余量提示

优先级：P1
任务性质：能力新增
类型：库存补货 / 跨仓余量 / 项目隔离 / 只读决策辅助
状态：Done
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-17-02`；candidateEvidenceHead=4e1766a83758685a9233185dbeb31a6e0af69194
存量问题键：[stock:A-02-CROSS-WAREHOUSE-SURPLUS]
关联产品目标：在低库存直接发起采购前，优先让采购人员看到同项目其他仓库可保留安全库存后调出的余量，减少无效采购判断。
候选对比：工作日历需要新增日期规则，需求预测缺少审定算法，真实调拨过账需要完整单据、在途和成本模型；本项只读复用现有仓库、项目、库存和安全库存，边界最小。
核验结论：`mat_stock` 已按租户、仓库和物料维护可用量与安全库存，`mat_warehouse` 已有项目归属和状态；库存页现有低库存动作只预填采购申请，手工出入库接口已停用。
阻塞证据：当前用户无法在采购前识别同项目其他仓库的可用余量，可能在已有内部余量时仍直接创建采购申请。
解除条件：服务端从当前库存项反查项目并返回其他启用仓库正余量，页面只读展示且保留现有人工发起采购路径；租户、项目、权限、数量与空结果边界通过验证。
Migration：不需要
依赖：既有 `MatStockService`、`MatWarehouse` 项目归属、库存查询权限、项目数据范围和库存台账页面。
风险等级：高
运行态要求：本地 H2/MySQL 与 Vite；浏览器只查询和查看提示，不发起采购、不修改补货设置或库存。
Reviewer要求：确认范围由当前仓库服务端反查并限制同租户同项目；确认只返回其他启用仓库且余量为 `availableQty-safetyStockQty` 的正数；确认提示不预占、不自动调拨、不绕过库存写侧。
归档报告：`docs/quality/ISSUE-048-002-采购补货前同项目跨仓可调拨余量提示验收报告.md`
最小回滚：回退只读候选接口、VO、前端类型与展示，不改数据库、库存流水或采购事实。
目标：

- 为当前库存项返回同项目其他启用仓库同物料的可调拨余量候选。
- 在库存辅助分析中展示来源仓名称与可调拨余量，并明确其为非预占的查询快照。
- 保持现有采购申请由用户明确发起，库存写入继续只走受控业务单据。
  非目标：
- 不实现调拨单、双边出入库流水、在途、预占、成本结转、跨项目调拨或自动采购。
- 不实现工作日历、需求预测，不新增迁移、历史数据回填、生产连接、发布或 push。
  允许修改：
- `backend/src/main/java/com/cgcpms/inventory/**`
- `backend/src/test/java/com/cgcpms/inventory/**`
- `frontend-admin/src/api/modules/inventory.ts`
- `frontend-admin/src/types/inventory.ts`
- `frontend-admin/src/pages/inventory/**`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-048-002-采购补货前同项目跨仓可调拨余量提示验收报告.md`
  禁止修改：
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/java/com/cgcpms/procurement/**`
- `backend/src/main/java/com/cgcpms/requisition/**`
- `frontend-admin/src/router/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
- `deploy/**`
  验收标准：
- 当前库存项不存在、跨租户、仓库禁用或无项目访问权时统一不可获取候选；客户端不能通过传入项目扩大范围。
- 仅返回同租户、同项目、其他启用仓库、同一物料且 `availableQty-safetyStockQty>0` 的候选；余量精确到4位，按余量倒序、仓库 ID 升序稳定排序。
- 页面只在已加载库存项时请求并展示来源仓、可调拨余量与“未预占”提示；空结果和请求失败不阻断现有台账与采购补货入口。
- 后端和前端目标测试、类型检查、Ready lint、允许路径与 `git diff --check` 通过；真实 MySQL 只读请求和真实页面只查看验收通过。
  验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-048-002`
- `cd backend; .\mvnw.cmd "-Dtest=MatStockServiceTest,MatStockControllerTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/inventory/__tests__/stock-production.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
