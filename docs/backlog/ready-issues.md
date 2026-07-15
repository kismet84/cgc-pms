# Ready Issues

## Ready 队列状态

v1.0 队列已封存到 [backlog 快照](../archive/v1.0/backlog-snapshot/ready-issues.md)。

`ISSUE-040-034`、`ISSUE-040-035`、`ISSUE-040-036`、`ISSUE-040-037`、`ISSUE-040-038` 已完成；本次 `启动迭代-5` 已达到 5 条上限。

`ISSUE-040-039`、阻塞修复 `ISSUE-047-001`、`ISSUE-040-040`、`ISSUE-040-041`、`ISSUE-040-042` 与 `ISSUE-040-043` 已完成；`启动迭代-20` 当前完成 6/20。明确入口叶子已清空，下一条 Ready 修复驾驶舱性能测试阈值文案与真实断言不一致。

### ISSUE-040-044：驾驶舱性能测试阈值文案对齐

优先级：P2
任务性质：缺口修复
类型：驾驶舱 / 性能回归 / 测试契约 / 文案一致性
状态：Ready
来源锚点：项目知识图谱当前问题 `OBS-DASHBOARD-PERF-LABEL`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/backlog/current-focus.md`；candidateEvidenceHead=6673afc8d292743e45cdc5e820017942ff9cc302
存量问题键：[stock:OBS-DASHBOARD-PERF-LABEL]
关联产品目标：让驾驶舱批量查询性能回归的显示名、说明、控制台输出与真实 `count <= 20` 断言采用同一门槛，避免 CI 和验收人员误读性能基线。
候选对比：当前 P1 补货负向测试变更面更大；本项是已证实、单文件、低风险且可直接关闭的测试契约缺口，可先以最小改动恢复证据可信度，不改变生产行为。
核验结论：`DashboardPerformanceTest` 两处真实断言均为 `count <= 20`，但类注释、测试显示名和成功输出仍写 `≤10`；CodeGraph 已精确命中全部不一致位置。
阻塞证据：测试通过时输出可能声称满足 `≤10`，实际只保证 `≤20`，导致正式验收证据与执行门槛不一致。
解除条件：所有描述门槛统一为 `≤20`，保留约42次 N+1 对照说明和真实断言；专项测试通过且不修改生产代码。
Migration：不需要
依赖：仅现有 `DashboardPerformanceTest` 和 local H2 测试上下文。
风险等级：低
运行态要求：仅 local/test；不需要浏览器、Docker 运行态或业务数据重置；不得连接或发布生产。
Reviewer要求：确认只修改测试描述/输出，不放宽或收紧 `count <= 20` 断言，不改变生产查询实现。
归档报告：`docs/quality/ISSUE-040-044-驾驶舱性能测试阈值文案对齐验收报告.md`
最小回滚：回退测试文案、治理回写和报告；生产代码与数据无需回滚。
目标：
- 将类注释、`@DisplayName` 和成功输出中的 `≤10` 统一为 `≤20`。
- 保留两处真实 `count <= 20` 断言和 N+1 约42次对照说明。
- 用专项测试证明文案对齐未改变驾驶舱功能与性能门槛。
非目标：
- 不优化 DashboardService、不调整 SQL 数、不改变性能阈值、不修改生产代码。
- 不新增基准测试框架、监控、页面或数据库迁移。
- 不连接生产、不发布生产、不 push。
允许修改：
- `backend/src/test/java/com/cgcpms/DashboardPerformanceTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-044-驾驶舱性能测试阈值文案对齐验收报告.md`
禁止修改：
- `backend/src/main/java/**`
- `backend/src/main/resources/db/**`
- `frontend-admin/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- `DashboardPerformanceTest` 不再包含 `≤10`，类注释、显示名、成功输出和两处真实断言均一致表达 `≤20`。
- N+1 约42次对照说明保留；生产源码和断言数值不变。
- 收口移除 `OBS-DASHBOARD-PERF-LABEL`；新增后续项0、关闭1、净变化-1。
- Ready lint、`DashboardPerformanceTest`、目标文本核对、`git diff --check` 和低风险复核 PASS。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-044`
- `cd backend; .\mvnw.cmd "-Dtest=DashboardPerformanceTest" test`
- `git diff --check`

### ISSUE-040-043：间接费规则受控删除入口与执行事实保护

优先级：P1
任务性质：缺口修复
类型：间接费规则 / 删除 / 权限 / 租户 / 引用状态 / 二次确认
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-OVERHEAD-DELETE`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=1688e2e5e1afda82bf26312d0b39ef693cd1e562
存量问题键：[stock:A-01-OVERHEAD-DELETE]
关联产品目标：在既有间接费规则弹窗提供最小受控删除入口，让 `overhead:delete` 用户删除尚未产生执行事实的规则，同时保护租户边界和历史分摊可追溯性。
候选对比：间接费规则新建与修改已完成，删除是该域最后一个明确叶子且复用同页、同一权限模型和现有软删除；它只新增引用状态门禁，不扩展数据模型，优先于需要产品决策门的新业务域。
核验结论：后端 DELETE `/overhead-allocation/rules/{id}` 已存在并校验认证租户，但未检查 `overhead_allocation_run` 引用；前端无删除动作。CodeGraph 已命中 Controller、Service 和执行事实链，前端局部召回不足由当前分支精确 `rg` 与文件读取补充核验。
阻塞证据：合格用户无法从产品页面删除误建且未执行的规则；若直接开放现有端点，已产生执行事实的规则仍会被软删除，破坏历史规则导航和审计可追溯性。
解除条件：删除前按认证租户检查目标与执行事实；存在任何 run 时 fail-close；前端权限动作和包含科目/依据/历史影响说明的二次确认通过；取消不 DELETE，成功刷新、失败保留。
Migration：不需要
依赖：复用既有规则弹窗、DELETE `/overhead-allocation/rules/{id}`、`OverheadAllocationRunMapper`、现有 Controller/Service 专项和 V80 表结构。
风险等级：高
运行态要求：仅 local/dev/test；浏览器只打开可用确认并取消，dev 无自然规则时验证真实空态且禁止造数；不得提交 DELETE、修改/重置业务数据、连接或发布生产。
Reviewer要求：确认仅 `overhead:delete` 或管理员可见；跨租户/不存在目标统一隐藏；同租户存在任何执行 run 时拒绝删除；确认文案包含科目、依据与历史事实保护；失败不移除行、不伪报成功。
归档报告：`docs/quality/ISSUE-040-043-间接费规则受控删除入口与执行事实保护验收报告.md`
最小回滚：回退 Service 引用检查、前端 API/删除动作、专项测试和治理回写；不恢复或清理任何业务数据。
目标：
- 删除前验证目标属于认证租户，并按 tenantId+ruleId 查询执行事实；已执行规则 fail-close，未执行规则沿用软删除。
- 在规则列表提供 `overhead:delete`/管理员可见动作，二次确认展示目标科目、依据和历史事实保护说明。
- 取消不请求，成功后刷新列表，失败保留服务端行且不伪报成功。
非目标：
- 不实现强制删除、级联删除、恢复、启停、批量删除或执行事实清理。
- 不新增表、迁移、权限节点、路由或页面，不改变分摊算法、运行幂等和历史成本。
- 不连接生产、不发布生产、不 push；浏览器不确认删除。
允许修改：
- `backend/src/main/java/com/cgcpms/overhead/service/OverheadAllocationService.java`
- `backend/src/test/java/com/cgcpms/overhead/OverheadAllocationControllerTest.java`
- `backend/src/test/java/com/cgcpms/overhead/OverheadAllocationServiceTest.java`
- `frontend-admin/src/api/modules/cost.ts`
- `frontend-admin/src/api/modules/__tests__/cost.test.ts`
- `frontend-admin/src/pages/cost/ledger.vue`
- `frontend-admin/src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-043-间接费规则受控删除入口与执行事实保护验收报告.md`
禁止修改：
- `backend/src/main/resources/db/**`
- `backend/src/main/java/com/cgcpms/overhead/entity/**`
- `backend/src/main/java/com/cgcpms/overhead/mapper/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- 未登录401、无权限403、显式 `overhead:delete`/管理员成功；跨租户和不存在规则统一 RULE_NOT_FOUND 且不泄露存在性。
- 同租户规则存在任一 `overhead_allocation_run` 时返回 RULE_ALREADY_EXECUTED 或等价业务错误且规则保留；无引用时仅软删除目标规则，不影响其他租户。
- 删除入口仅权限用户可见；确认文案含成本科目 ID、分摊依据及“已有执行事实不可删除”，取消不 DELETE，成功刷新，失败保留行。
- 收口移除 `A-01-OVERHEAD-DELETE`，A-01 更新为有用户入口248、前端调用无独立页面58、内部/集成/运维4、需补入口0、待废弃0、需要确认11，共321；新增后续项0、关闭1、净变化-1。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint、`git diff --check` 和只取消/空态浏览器验收通过；高风险复核 PASS。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-043`
- `cd backend; .\mvnw.cmd "-Dtest=OverheadAllocationControllerTest,OverheadAllocationServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/cost.test.ts src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/cost.ts src/api/modules/__tests__/cost.test.ts src/pages/cost/ledger.vue src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `git diff --check`

### ISSUE-040-042：间接费规则受控修改入口与字段状态边界

优先级：P1
任务性质：缺口修复
类型：间接费规则 / 修改 / 权限 / 科目 / 租户 / 状态 / 白名单 DTO
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-OVERHEAD-UPDATE`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=3b72fc0fa52364baeeaa9ef131f5b35e0319c298
存量问题键：[stock:A-01-OVERHEAD-UPDATE]
关联产品目标：在既有间接费规则弹窗提供最小受控修改入口，让 `overhead:edit` 用户调整科目、依据与周期，同时保持认证租户、既有状态和更新字段白名单边界。
候选对比：间接费规则新建闭环已通过，修改复用同页、同一科目候选和同一校验服务，变更面小于删除的引用状态门禁；库存并发和补货负向测试属于回归证明，不先于已证实的用户入口缺口。
核验结论：后端 PUT `/overhead-allocation/rules/{id}` 已存在，但直接接收实体，客户端可携带 tenantId、status 和审计字段；Service 仅修正 tenantId，未校验目标科目。前端规则列表无修改入口。CodeGraph 已命中 Controller 和 Service 更新链，前端预期局部召回不足，实施时以当前分支精确 `rg` 与文件读取补充核验。
阻塞证据：合格用户无法从产品页面修改规则；直接开放现有实体入参会暴露身份、状态和审计字段注入面，且跨租户、禁用、非间接费科目可被写入规则。
解除条件：新增更新白名单请求与目标规则/科目 fail-close；前端受控编辑表单成功后刷新、失败保留、取消不 PUT；权限、租户、字段注入、科目和枚举正负样本通过。
Migration：不需要
依赖：复用已验收的成本列表规则弹窗、新建表单科目候选、PUT `/overhead-allocation/rules/{id}`、现有 Controller/Service 专项和 V80 表结构。
风险等级：高
运行态要求：仅 local/dev/test；浏览器只打开编辑弹窗并取消，不提交 PUT、不修改或重置业务数据；不得连接或发布生产。
Reviewer要求：确认仅 `overhead:edit` 或管理员可见；DTO 不接受 id、tenantId、status、审计字段；目标规则属于认证租户，科目是当前租户 ENABLE 的 OVERHEAD/COST 类别；更新保留服务端既有状态，失败不伪报成功。
归档报告：`docs/quality/ISSUE-040-042-间接费规则受控修改入口与字段状态边界验收报告.md`
最小回滚：回退更新 DTO、Controller/Service 校验、前端 API/表单、专项测试和治理回写；不修改任何既有规则数据或分摊事实。
目标：
- 用白名单 DTO 接收 costSubjectId、allocationBasis、allocationCycle，路径 id、认证租户和服务端既有状态为唯一身份/状态来源。
- 校验目标规则与科目均属于认证租户，科目为启用的 OVERHEAD/COST 类别；非法目标、科目和枚举 fail-close。
- 在规则列表提供 `overhead:edit`/管理员可见的编辑入口，成功刷新、失败保留、取消不请求。
非目标：
- 不实现规则删除、启停、复制、批量导入或立即执行分摊。
- 不新增表、迁移、权限节点、路由或页面，不改变分摊算法、执行幂等和历史规则。
- 不连接生产、不发布生产、不 push；浏览器不确认修改。
允许修改：
- `backend/src/main/java/com/cgcpms/overhead/dto/OverheadAllocationRuleUpdateRequest.java`
- `backend/src/main/java/com/cgcpms/overhead/controller/OverheadAllocationController.java`
- `backend/src/main/java/com/cgcpms/overhead/service/OverheadAllocationService.java`
- `backend/src/test/java/com/cgcpms/overhead/OverheadAllocationControllerTest.java`
- `backend/src/test/java/com/cgcpms/overhead/OverheadAllocationServiceTest.java`
- `frontend-admin/src/api/modules/cost.ts`
- `frontend-admin/src/api/modules/__tests__/cost.test.ts`
- `frontend-admin/src/pages/cost/ledger.vue`
- `frontend-admin/src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-042-间接费规则受控修改入口与字段状态边界验收报告.md`
禁止修改：
- `backend/src/main/resources/db/**`
- `backend/src/main/java/com/cgcpms/overhead/entity/**`
- `backend/src/main/java/com/cgcpms/overhead/mapper/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- API 只发送 costSubjectId、allocationBasis、allocationCycle；无 id、tenantId、status 或审计字段，路径 id 不可被请求体覆盖。
- 入口仅 `overhead:edit` 或 ADMIN/SUPER_ADMIN 可见；表单预填当前规则，取消不请求，成功关闭并刷新，失败保留输入且不伪报成功。
- 未登录401、无权限403、显式权限/管理员成功；目标规则和科目强制认证租户，跨租户/不存在规则及跨租户/不存在/禁用/非间接费科目 fail-close。
- allocationBasis 仅 DIRECT_LABOR/CONTRACT_AMOUNT/USAGE，allocationCycle 仅 MONTHLY/PER_OCCURRENCE；非法输入不入库；更新保留既有状态。
- 收口移除 `A-01-OVERHEAD-UPDATE`，A-01 更新为有用户入口247、前端调用无独立页面58、内部/集成/运维4、需补入口1、待废弃0、需要确认11，共321；新增后续项0、关闭1、净变化-1。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint、`git diff --check` 和只取消浏览器验收通过；高风险复核 PASS。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-042`
- `cd backend; .\mvnw.cmd "-Dtest=OverheadAllocationControllerTest,OverheadAllocationServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/cost.test.ts src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/cost.ts src/api/modules/__tests__/cost.test.ts src/pages/cost/ledger.vue src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `git diff --check`

### ISSUE-040-041：间接费规则受控新建入口与科目租户边界

优先级：P1
任务性质：缺口修复
类型：间接费规则 / 新建 / 权限 / 科目 / 租户 / 白名单 DTO
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-OVERHEAD-CREATE`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=81437003e6a827630266df4859aa5ee811ed82f3
存量问题键：[stock:A-01-OVERHEAD-CREATE]
关联产品目标：在既有成本列表的间接费规则弹窗提供最小受控新建入口，让 `overhead:add` 用户配置科目、依据与周期，同时保持认证租户和规则白名单边界。
候选对比：投标域明确叶子已清空，当前切换到同为 P1 的间接费域；新建是修改和删除的前置用户闭环，优先于后二者。补货负向和库存并发属于回归证明，不先于已证实的用户入口缺口。
核验结论：后端 POST `/overhead-allocation/rules` 已存在并强制认证租户与 ENABLE，但直接接收实体且前端只有只读规则列表；需用白名单 DTO 限制客户端身份/状态字段，并验证科目属于当前租户可用的间接费科目后再开放入口。CodeGraph 未召回预期前端页面和 Controller，归类为工具召回不足，已用当前分支精确 `rg` 与文件读取交叉核验。
阻塞证据：合格用户无法在产品页面创建规则；直接开放现有实体入参会暴露 id、tenantId、status 和审计字段注入面，且现有 Service 未校验 costSubjectId 的租户、状态和间接费类别。
解除条件：新增白名单请求、科目 fail-close 与前端受控表单；取消不 POST，成功刷新规则列表，失败保留表单；权限、租户、字段注入、科目和枚举正负样本通过。
Migration：不需要
依赖：复用成本列表规则弹窗、`getCostSubjectList`、POST `/overhead-allocation/rules`、现有 Controller/Service 专项和 V80 表结构。
风险等级：高
运行态要求：仅 local/dev/test；浏览器只打开新建弹窗并取消，不提交 POST、不创建或重置业务数据；不得连接或发布生产。
Reviewer要求：确认仅 `overhead:add` 或管理员可见；DTO 不接受 id/tenantId/status/审计字段；科目必须是当前租户 ENABLE 的 OVERHEAD 类别；非法依据/周期 fail-close，失败不伪报成功。
归档报告：`docs/quality/ISSUE-040-041-间接费规则受控新建入口与科目租户边界验收报告.md`
最小回滚：回退 DTO、Controller/Service 科目校验、前端 API/表单、专项测试和治理回写；不删除任何既有规则或分摊事实。
目标：
- 用白名单 DTO 接收 costSubjectId、allocationBasis、allocationCycle，服务端固定租户与 ENABLE。
- 校验科目为当前租户可用的间接费科目；非法科目、枚举和客户端身份/状态字段 fail-close 或被忽略。
- 在规则弹窗提供 `overhead:add`/管理员可见的新建表单，成功刷新、失败保留、取消不请求。
非目标：
- 不实现规则修改、删除、启停、复制、批量导入或立即执行分摊。
- 不新增表、迁移、权限节点、路由或页面，不改变分摊算法、执行幂等和历史规则。
- 不连接生产、不发布生产、不 push；浏览器不确认新建。
允许修改：
- `backend/src/main/java/com/cgcpms/overhead/dto/OverheadAllocationRuleCreateRequest.java`
- `backend/src/main/java/com/cgcpms/overhead/controller/OverheadAllocationController.java`
- `backend/src/main/java/com/cgcpms/overhead/service/OverheadAllocationService.java`
- `backend/src/test/java/com/cgcpms/overhead/OverheadAllocationControllerTest.java`
- `backend/src/test/java/com/cgcpms/overhead/OverheadAllocationServiceTest.java`
- `frontend-admin/src/api/modules/cost.ts`
- `frontend-admin/src/api/modules/__tests__/cost.test.ts`
- `frontend-admin/src/pages/cost/ledger.vue`
- `frontend-admin/src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-041-间接费规则受控新建入口与科目租户边界验收报告.md`
禁止修改：
- `backend/src/main/resources/db/**`
- `backend/src/main/java/com/cgcpms/overhead/entity/**`
- `backend/src/main/java/com/cgcpms/overhead/mapper/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- API 只发送 costSubjectId、allocationBasis、allocationCycle；无 id、tenantId、status 或审计字段。
- 入口仅 `overhead:add` 或 ADMIN/SUPER_ADMIN 可见；表单取消不请求，成功关闭并刷新，失败保留输入且不伪报成功。
- 未登录401、无权限403、显式权限/管理员成功；服务端固定认证租户和 ENABLE，跨租户/不存在/禁用/非间接费科目 fail-close。
- allocationBasis 仅 DIRECT_LABOR/CONTRACT_AMOUNT/USAGE，allocationCycle 仅 MONTHLY/PER_OCCURRENCE；非法输入不入库。
- 收口移除 `A-01-OVERHEAD-CREATE`，A-01 更新为有用户入口246、前端调用无独立页面58、内部/集成/运维4、需补入口2、待废弃0、需要确认11，共321；新增后续项0、关闭1、净变化-1。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint、`git diff --check` 和只取消浏览器验收通过；高风险复核 PASS。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-041`
- `cd backend; .\mvnw.cmd "-Dtest=OverheadAllocationControllerTest,OverheadAllocationServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/cost.test.ts src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/cost.ts src/api/modules/__tests__/cost.test.ts src/pages/cost/ledger.vue src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `git diff --check`

### ISSUE-040-040：投标成本标记未中标入口与费用核销边界

优先级：P1
任务性质：缺口修复
类型：投标成本 / 未中标状态 / 费用核销 / 权限 / 租户 / 重复操作
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-BID-LOST`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=a5df9fe80a8e27f8e4d61d798a7002b69bc13b79
存量问题键：[stock:A-01-BID-LOST]
关联产品目标：在既有投标成本页提供受控“标记未中标”入口，复用已登记的 `bid:status` 权限和既有失标端点，将该投标的 BID_COST 费用一次性核销并关闭 A-01 的最后一个投标成本叶子。
候选对比：当前同级明确叶子还有间接费规则新建、修改和删除，另有库存并发、补货负向测试等观察项。投标域已连续超过五条 Ready，本条仍优先的唯一理由是它复用刚通过验收的同页、同一 `bid:status` 权限与同一 BIDDING 状态机，且是投标域最后一个明确叶子；完成后立即切换间接费域，不继续拆投标能力。相比同时启动间接费写入，本条无需新迁移或新数据模型，能够以最小变更关闭一个已证实用户缺口。
核验结论：问题仍存在——后端 `PUT /bid-cost/{id}/lost`、认证租户隐藏、仅 BIDDING 可转换及 BID_COST 费用核销已有实现与部分测试，V154 已注册 `bid:status`；前端 API 与桌面/移动页面仍没有同方法入口，现有页面测试还明确断言不存在失标动作。
检索交叉核验：先按 Java/TypeScript 规则查询源码图谱；投标页面召回不完整，归类为工具召回不足并使用当前分支精确 `rg` 与文件读取核验 Controller、Service、前端 API/页面及专项测试。现有后端测试已覆盖费用核销和非法状态，仍需以专项回归证明权限、租户和重复操作边界未回退。
阻塞证据：具备 `bid:status` 的用户只能标记中标，无法从产品页面触发已实现的未中标状态；缺少二次确认、取消不请求、成功刷新与失败保留的交互证据。
解除条件：BIDDING 行在桌面和移动端提供仅 `bid:status` 或管理员可见的未中标动作；确认文案包含投标名称与费用核销后果；取消不请求，成功刷新；失败不改变行状态或伪报成功；权限、租户、状态、核销和重复操作专项通过。
Migration：不需要
依赖：复用 `/bid-cost` 页面、V154 的 `bid:status`、`PUT /bid-cost/{id}/lost`、`BidCostService.markAsLost`、现有 bid API/页面测试与 BidCost Controller/Service 测试。
风险等级：高
运行态要求：仅 local/dev/test；浏览器验收前通过三项 health gate，只打开未中标确认并取消，不提交 PUT、不新增、修改、删除或重置业务数据；不得连接或发布生产。
Reviewer要求：按权限、租户、状态和金额事实复核；确认仅 `bid:edit` 不获得状态权限，不存在/跨租户投标统一隐藏，非 BIDDING 与重复操作拒绝，合法失标只把当前投标的 BID_COST 费用核销一次；确认确认框取消不发请求、接口失败不伪报成功。
归档报告：`docs/quality/ISSUE-040-040-投标成本标记未中标入口与费用核销边界验收报告.md`
最小回滚：回退前端失标 API/交互、专项测试和治理回写；不反向修改任何已失标业务数据，不回退既有投标列表、详情、创建、编辑、删除和中标能力。
目标：
- 增加类型化 `markBidCostAsLost(id)`，精确调用 `PUT /bid-cost/{id}/lost`，不发送 body、params、tenantId 或其他业务字段。
- 在既有投标成本桌面/移动 BIDDING 行提供 `bid:status` 或管理员可见的未中标动作，确认文案同时包含投标名称和费用核销后果；取消不请求，成功刷新，失败保留当前状态。
- 复跑并按需补强权限、租户、状态、费用核销和重复操作后端回归，关闭唯一存量叶子。
非目标：
- 不实现撤销失标、重新打开、批量状态迁移、投标归档、成本项编辑或状态机重构。
- 不新增权限、迁移、路由、页面、表或状态值，不修改中标项目范围、金额算法、核销口径或既有 V150～V154。
- 不连接生产、不发布生产、不 push；浏览器不确认未中标。
允许修改：
- `backend/src/test/java/com/cgcpms/bid/BidCostControllerTest.java`
- `backend/src/test/java/com/cgcpms/bid/BidCostServiceTest.java`
- `frontend-admin/src/api/modules/bid.ts`
- `frontend-admin/src/api/modules/__tests__/bid.test.ts`
- `frontend-admin/src/pages/bid-cost/index.vue`
- `frontend-admin/src/pages/bid-cost/__tests__/index.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-040-投标成本标记未中标入口与费用核销边界验收报告.md`
禁止修改：
- `backend/src/main/**`
- `backend/src/main/resources/db/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `frontend-admin/src/types/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- API 精确调用 `PUT /bid-cost/{id}/lost`，无 body、params、tenantId 或其他业务字段。
- 入口仅对 `bid:status` 或 ADMIN/SUPER_ADMIN 可见且仅 BIDDING 展示；确认文案包含投标名称和 BID_COST 费用将被核销的明确后果；取消不请求，成功刷新，失败不改行状态或伪报成功。
- 未登录401、无 `bid:status` 403、仅 `bid:edit` 403、显式 `bid:status` 或管理员成功；不存在/跨租户投标统一 `BID_COST_NOT_FOUND`，非 BIDDING 与重复操作返回 `BID_STATUS_INVALID`。
- 合法失标只将当前投标状态改为 LOST，并将其 BID_COST 费用改为 WRITE_OFF；其他投标和其他来源费用不受影响，失败路径无部分写入。
- 列表、详情、新建、编辑、删除、中标及 V154 权限不回退；不产生迁移或生产代码变更。
- 收口移除 `A-01-BID-LOST`，A-01 更新为有用户入口245、前端调用无独立页面58、内部/集成/运维4、需补入口3、待废弃0、需要确认11，共321；新增后续项0、关闭1、净变化-1。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint、`git diff --check` 和只取消浏览器验收通过；高风险复核结论为 PASS。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-040`
- `cd backend; .\mvnw.cmd "-Dtest=BidCostControllerTest,BidCostServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/bid.ts src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/index.vue src/pages/bid-cost/__tests__/index.test.ts`
- `git diff --check`

### ISSUE-047-001：AutoPilot 收口区块边界与幂等误判修复

优先级：P0
任务性质：运维治理
类型：AutoPilot / closeout / Ready 区块 / 幂等 / 双提交 / 控制面金丝雀
状态：Done
来源锚点：`ISSUE-040-039` 收口实证；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-040-039-投标成本标记中标入口与状态项目边界验收报告.md`,`scripts/codex-autopilot/autopilot-closeout.ps1`
存量问题键：[stock:AUTOPILOT-CLOSEOUT-BLOCK-BOUNDARY]
关联产品目标：恢复本地连续迭代的确定性双提交与幂等收口，使后续产品和治理 Ready 不因其他历史 Done 区块被误判为已完成。
核验结论：问题稳定复现——目标 Issue 的 worktree HEAD 等于 master HEAD 且目标仍为 Ready 时，现有跨区块正则可匹配后续任一 `状态：Done`，直接返回 idempotent/merged，未生成实现或收口提交。
阻塞证据：`ISSUE-040-039` 首次调用 `Complete-AutopilotIssueCloseout` 返回空 `preCloseoutFacts` 且工作区仍保留14个未提交文件；剩余19个任务均使用同一 closeout 入口，继续执行会重复误判。
解除条件：幂等检查严格绑定目标 Issue 区块；目标 Ready 后紧邻或隔着任意 Done 条目时仍进入真实 closeout；目标自身已 Done 且提交已合并时保持幂等。
Migration：不需要
依赖：复用 `Set-AutopilotReadyDone` 的区块边界语义、`Complete-AutopilotIssueCloseout` 与现有临时 Git 仓库自测。
风险等级：高
运行态要求：仅本地临时 Git 仓库与当前测试工作区；不访问业务服务、数据库或生产。
Reviewer要求：确认修复只收紧目标区块识别，不放宽分支、基线、脏工作区、存量问题关闭、评分、双提交和 fast-forward 门禁；确认新回归在旧实现上失败、修复后通过，真正已完成目标仍幂等。
归档报告：`docs/quality/ISSUE-047-001-AutoPilot收口区块边界与幂等误判验收报告.md`
最小回滚：回退目标区块识别函数/条件及对应回归；不修改任何业务代码、业务数据、历史 closeout ledger 或既有提交。
目标：
- 为目标 Ready 提供单一、区块有界的状态读取，禁止读取后续 Issue 的 Done。
- 增加“目标 Ready 后存在 Done 条目”的回归，并保留真正 Done 目标的幂等证据。
非目标：
- 不重构完整 closeout 状态机、评分、报告投影、登记账本或 Git 合并协议。
- 不修改执行宿主、Ready writer、知识图谱、业务代码或生产配置。
允许修改：
- `scripts/codex-autopilot/autopilot-closeout.ps1`
- `scripts/codex-autopilot/test-closeout.ps1`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-047-001-AutoPilot收口区块边界与幂等误判验收报告.md`
禁止修改：
- `backend/**`
- `frontend-admin/**`
- `scripts/codex-autopilot/autopilot-state.ps1`
- `scripts/codex-autopilot/autopilot-transition.ps1`
- `scripts/codex-autopilot/autopilot-issue-lifecycle.ps1`
- `scripts/codex-autopilot/autopilot-task-score.ps1`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- 当前目标为 Ready 且后续存在 Done 条目时，不得返回 idempotent；必须完成实现提交和独立 closeout 提交。
- 目标自身 Done 且 closeout commit 已在 master 时，重试仍返回 idempotent 且不产生新提交。
- 分支、基线、脏工作区、存量问题关闭、评分和 fast-forward 既有门禁不回退。
- 收口移除 `AUTOPILOT-CLOSEOUT-BLOCK-BOUNDARY`；新增后续项0、关闭1、净变化-1。
- Ready lint、closeout 自测、控制面回归、`git diff --check` 和独立风险复核通过。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-047-001`
- `pwsh -NoProfile -File scripts/codex-autopilot/test-closeout.ps1`
- `pwsh -NoProfile -File scripts/codex-autopilot/test-control-plane.ps1`
- `git diff --check`

收口结论：closeout 幂等状态已按目标 Issue 区块有界读取；目标 Ready 后存在其他 Done 时继续触发存量关闭门禁并完成双提交，目标自身已 Done 的重试仍保持幂等。closeout 自测、完整控制面自测、Ready lint、范围与差异检查均通过，独立风险复核 `PASS`、findings=无。`AUTOPILOT-CLOSEOUT-BLOCK-BOUNDARY` 已移除；新增后续项0、关闭后续项1、净变化-1。

### ISSUE-040-039：投标成本标记中标入口与状态项目边界

优先级：P1
任务性质：缺口修复
类型：投标成本 / 中标状态 / 费用结转 / 权限 / 租户 / 项目数据范围
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-BID-WON`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=8dfa9ebfc8ba9dcd74ed0b8de2b066333d97c465
存量问题键：[stock:A-01-BID-WON]
关联产品目标：在既有投标成本页提供受控“标记中标”入口，复用既有中标端点和项目列表，将投标费用结转到用户有权访问的项目，并关闭 A-01 的最小叶子缺口。
候选对比：同级叶子还有标记失标及间接费规则写入口；本任务复用已完成的投标列表、详情、创建、编辑、删除能力，且后端已有状态机和费用结转实现，范围小于间接费规则写入，适合作为当前控制面指纹单 Issue 金丝雀。失标保持独立 Ready，避免一次状态操作同时关闭两个存量问题。
核验结论：问题仍存在——后端 `PUT /bid-cost/{id}/won` 已使用 `bid:status` 并限制 BIDDING、认证租户和同租户项目，但前端 API/页面明确没有中标调用；`bid:status` 尚未注册为菜单权限，Service 也未复用 `ProjectAccessChecker`，因此开放入口前必须同时补齐权限注册和项目数据范围门禁。知识图谱游标与候选取证提交一致。
检索交叉核验：CodeGraph 未完整召回投标页面，按工具召回不足补充 codebase-memory 与当前分支精确 `rg`/文件读取；后端 Controller、Service、测试、前端无调用证据、V150～V153 权限迁移和 `ProjectAccessChecker` 语义均已交叉核验。
阻塞证据：当前合格用户无法从投标成本页触发中标动作；仅持 `bid:edit` 已被后端明确拒绝，但数据库没有 `bid:status` 权限节点；同租户但超出用户项目数据范围的 projectId 目前只通过租户校验，可能造成越权关联与费用结转。
解除条件：注册并绑定独立 `bid:status`；BIDDING 行在桌面和移动端提供二次确认的中标入口；项目候选来自既有只读项目列表且请求不含 tenantId；后端在任何状态或费用写入前校验项目数据范围；权限、租户、项目范围、状态、费用结转和失败不伪报成功的正负样本通过。
Migration：需要
Migration说明：新增 MySQL/H2 V154，仅在既有投标成本菜单下注册 `bid:status` BUTTON，并绑定既有 SUPER_ADMIN、ADMIN、COST_MANAGER；不修改已应用迁移。
依赖：复用 `/bid-cost` 页面、`getProjectList`、`PUT /bid-cost/{id}/won`、`BidCostService.markAsWon`、`ProjectAccessChecker`、现有 bid API/页面测试与 BidCost Controller/Service 测试。
风险等级：高
运行态要求：仅 local/dev/test；浏览器验收前通过三项 health gate，页面只打开中标确认、加载项目并取消，不提交 PUT、不新增、修改、删除或重置业务数据。自动化测试数据必须事务回滚或精确清理；不得连接或发布生产。
Reviewer要求：按高风险权限、租户、项目范围和金额事实复核；确认 `bid:status` 未被 `bid:edit`/`bid:query` 替代，401/403、管理员和显式权限样本齐全；确认跨租户/不存在投标统一隐藏，同租户无项目范围与跨租户项目均 fail-close，且失败发生在投标状态、费用来源类型、projectId 和成本汇总写入之前；确认重复操作拒绝、费用只从 BID_COST 结转一次并保持事务原子。
归档报告：`docs/quality/ISSUE-040-039-投标成本标记中标入口与状态项目边界验收报告.md`
最小回滚：回退 V154、前端中标 API/交互、Service 项目范围补强、测试及治理回写；不反向修改任何已中标业务数据，不回退既有列表、详情、创建、编辑和删除能力。
目标：
- 增加类型化 `markBidCostAsWon(id, projectId)`，精确调用 `PUT /bid-cost/{id}/won`，projectId 只作为请求参数。
- 在既有投标成本桌面/移动 BIDDING 行提供 `bid:status` 或管理员可见的中标动作，确认文案包含投标项目和目标项目；取消不请求，成功刷新，失败保留当前状态和确认上下文。
- 注册独立状态权限，并在后端结转前复用项目数据范围校验；补齐权限、状态、租户、项目范围和费用结转回归。
非目标：
- 不实现标记失标、撤销中标、重新打开、批量状态迁移、项目创建、成本项编辑或完整投标状态机重构。
- 不修改既有 V150～V153，不新增路由、页面、表或状态值，不改变金额算法、成本汇总口径和项目列表授权。
- 不连接生产、不发布生产、不 push；浏览器不确认中标。
允许修改：
- `backend/src/main/java/com/cgcpms/bid/service/BidCostService.java`
- `backend/src/main/resources/db/migration/V154__add_bid_cost_status_permission.sql`
- `backend/src/main/resources/db/migration-h2/V154__add_bid_cost_status_permission.sql`
- `backend/src/test/java/com/cgcpms/bid/BidCostControllerTest.java`
- `backend/src/test/java/com/cgcpms/bid/BidCostServiceTest.java`
- `frontend-admin/src/api/modules/bid.ts`
- `frontend-admin/src/api/modules/__tests__/bid.test.ts`
- `frontend-admin/src/pages/bid-cost/index.vue`
- `frontend-admin/src/pages/bid-cost/__tests__/index.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-039-投标成本标记中标入口与状态项目边界验收报告.md`
禁止修改：
- `backend/src/main/java/com/cgcpms/bid/controller/**`
- `backend/src/main/java/com/cgcpms/cost/**`
- `backend/src/main/java/com/cgcpms/project/**`
- `backend/src/main/resources/db/migration/V150__*`
- `backend/src/main/resources/db/migration/V151__*`
- `backend/src/main/resources/db/migration/V152__*`
- `backend/src/main/resources/db/migration/V153__*`
- `backend/src/main/resources/db/migration-h2/V150__*`
- `backend/src/main/resources/db/migration-h2/V151__*`
- `backend/src/main/resources/db/migration-h2/V152__*`
- `backend/src/main/resources/db/migration-h2/V153__*`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `frontend-admin/src/types/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- API 精确调用 `PUT /bid-cost/{id}/won`；只发送 params=`{ projectId }`，无 body、tenantId 或其他业务字段。
- 入口仅对 `bid:status` 或 ADMIN/SUPER_ADMIN 可见且仅 BIDDING 展示；目标项目从既有 GET 列表加载，确认文案包含投标名称与项目名称；取消不请求，成功刷新，失败不改行状态或伪报成功。
- 未登录401、无 `bid:status` 403、仅 `bid:edit` 403、显式 `bid:status` 或管理员成功；不存在/跨租户投标统一 `BID_COST_NOT_FOUND`，非 BIDDING 返回 `BID_STATUS_INVALID`。
- 同租户但超出项目数据范围返回 `PROJECT_ACCESS_DENIED`，跨租户/不存在项目统一 `PROJECT_NOT_FOUND`；全部拒绝发生在投标与费用写入前。合法中标关联项目、状态变 WON、BID_COST 费用转为 BID_COST_TRANSFERRED 并刷新汇总；重复操作不得二次结转。
- V154 两方言等价且只绑定既有三类角色；列表、详情、新建、编辑、删除和失标后端能力不回退。
- 收口移除 `A-01-BID-WON`，A-01 更新为有用户入口244、前端调用无独立页面58、内部/集成/运维4、需补入口4、待废弃0、需要确认11，共321；新增后续项0、关闭1、净变化-1。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint、SQL 静态、`git diff --check` 和只取消浏览器验收通过；独立风险复核结论为 PASS。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-039`
- `cd backend; .\mvnw.cmd "-Dtest=BidCostControllerTest,BidCostServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/bid.ts src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/index.vue src/pages/bid-cost/__tests__/index.test.ts`
- `git diff --check`

收口结论：独立 `bid:status`、桌面/移动 BIDDING 可见性、授权项目候选、双名称二次确认、取消不请求、成功刷新与失败保留均通过；后端专项证明401/403、显式权限、管理员、投标租户隐藏、项目数据范围、非法状态和重复操作边界，合法中标只结转一次 BID_COST 费用。测试进程默认 JWT 密钥不足归类为环境前置，注入合格测试密钥后38项稳定通过；原生 Vite 超过180秒稳定观察及浏览器取消闭环完成，中标 PUT 计数保持0。`A-01-BID-WON` 已移除；新增后续项0、关闭后续项1、净变化-1。

### ISSUE-040-038：投标成本受控删除入口与状态租户边界

优先级：P1
任务性质：缺口修复
类型：投标成本 / 删除 / 二次确认 / 权限 / 租户 / 状态
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-BID-DELETE`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=0d63f6739d0d2dcbc7610ec3c61e1bbc276ca7b0
存量问题键：[stock:A-01-BID-DELETE]
归档报告：`docs/quality/ISSUE-040-038-投标成本受控删除入口与状态租户边界验收报告.md`
Migration：需要
Migration说明：MySQL/H2 V153 仅注册 `bid:delete` BUTTON 并绑定既有 SUPER_ADMIN、ADMIN、COST_MANAGER。
依赖：复用 `/bid-cost` 页面、既有 `DELETE /bid-cost/{id}`、`BidCostService.delete` 的认证租户隐藏与 BIDDING 状态门禁。
风险等级：高
运行态要求：仅 local/dev/test；浏览器只打开并取消删除确认，不提交 DELETE、不新增、修改、删除或重置业务数据。
Reviewer要求：确认删除入口仅对 `bid:delete` 或管理员可见且只在 BIDDING 行展示；确认文案包含目标名称；取消不请求，失败不移除行或伪报成功；后端401/403、跨租户/不存在统一隐藏、非 BIDDING 拒绝。
最小回滚：回退 V153、前端删除 API/确认入口/测试及治理回写；后端生产删除逻辑不变，浏览器无数据回滚。
目标：
- 为 BIDDING 投标提供桌面和移动受控删除入口，以目标项目名称二次确认。
- 注册独立 `bid:delete` 权限；成功后刷新服务端列表，失败保留当前行并显示错误。
- 补齐权限、租户、状态、请求契约和取消不写入证据。
非目标：
- 不实现批量删除、撤销/恢复、物理清理、中标、失标或关联项目/金额/成本项修改。
- 不修改后端生产 Service、实体、Mapper、状态结转或冲销逻辑，不新增路由、页面或表。
- 不连接/发布生产、不 push；浏览器不确认删除。
允许修改：
- `backend/src/main/resources/db/migration/V153__add_bid_cost_delete_permission.sql`
- `backend/src/main/resources/db/migration-h2/V153__add_bid_cost_delete_permission.sql`
- `backend/src/test/java/com/cgcpms/bid/BidCostControllerTest.java`
- `frontend-admin/src/api/modules/bid.ts`
- `frontend-admin/src/api/modules/__tests__/bid.test.ts`
- `frontend-admin/src/pages/bid-cost/index.vue`
- `frontend-admin/src/pages/bid-cost/__tests__/index.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-038-投标成本受控删除入口与状态租户边界验收报告.md`
禁止修改：
- `backend/src/main/java/**`
- `backend/src/main/resources/db/migration/V150__*`
- `backend/src/main/resources/db/migration/V151__*`
- `backend/src/main/resources/db/migration/V152__*`
- `backend/src/main/resources/db/migration-h2/V150__*`
- `backend/src/main/resources/db/migration-h2/V151__*`
- `backend/src/main/resources/db/migration-h2/V152__*`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `frontend-admin/src/types/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- API 精确调用 `DELETE /bid-cost/{id}`，不发送 params、body、tenantId 或其他业务字段。
- 入口仅对 `bid:delete` 或 ADMIN/SUPER_ADMIN 可见且仅 BIDDING 展示；确认文案包含项目名称；取消不请求，成功刷新，失败保留当前行。
- 未登录401、无 `bid:delete` 403、显式权限或管理员成功；跨租户/不存在统一 `BID_COST_NOT_FOUND`，WON/LOST 返回 `BID_STATUS_NOT_DELETABLE`。
- V153 两方言等价且只绑定既有三类角色；列表、新建、详情、编辑、状态动作和金额事实不回退。
- 收口移除 `A-01-BID-DELETE`，A-01 更新为有用户入口243、需补入口5；新增后续项0、关闭1、净变化-1。
- Ready lint、后端专项、前端专项、类型、ESLint、SQL 静态、diff 与只取消浏览器验收通过。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-038`
- `cd backend; .\mvnw.cmd "-Dtest=BidCostControllerTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/bid.ts src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/index.vue src/pages/bid-cost/__tests__/index.test.ts`
- `git diff --check`

收口结论：独立 `bid:delete`、桌面/移动 BIDDING 可见性、目标名称二次确认、取消不请求、成功刷新与失败保留均通过；后端专项证明401/403、显式权限、管理员、跨租户隐藏及 WON/LOST 拒绝。Docker 5173 端口转发失效归类为环境前置，改用同工作区原生 Vite 后完成205秒稳定观察与浏览器取消闭环，DELETE 计数保持0。`A-01-BID-DELETE` 已移除；新增后续项0、关闭后续项1、净变化-1。

### ISSUE-040-037：投标成本受控修改入口与状态租户边界

优先级：P1
任务性质：缺口修复
类型：投标成本 / 修改 / 白名单 DTO / 权限 / 租户 / 状态
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-BID-UPDATE`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=0a801fb178c4a380bfb237d5b23ba65d5edca081
存量问题键：[stock:A-01-BID-UPDATE]
归档报告：`docs/quality/ISSUE-040-037-投标成本受控修改入口与状态租户边界验收报告.md`
Migration：需要
Migration说明：MySQL/H2 V152 仅注册 `bid:edit` BUTTON 并绑定既有 SUPER_ADMIN、ADMIN、COST_MANAGER。
依赖：复用 `/bid-cost` 页面、详情、新建 DTO 约束、`PUT /bid-cost/{id}`、BIDDING 状态与租户隐藏语义。
风险等级：高
运行态要求：仅 local/dev/test；浏览器仅打开并取消编辑，不提交 PUT 或写业务数据。
Reviewer要求：确认更新 DTO/Service 仅写名称和备注，客户端字段不能覆盖 ID、tenantId、projectId、bidStatus、金额或审计事实；非 BIDDING 和跨租户 fail-close。
最小回滚：回退 V152、更新 DTO/白名单、前端编辑/API/测试及治理回写；浏览器无数据回滚。
目标：
- 为 BIDDING 投标提供桌面和移动受控编辑入口，仅修改名称与备注。
- 服务端把实体直写收敛为专用 DTO 和既有实体白名单更新。
- 注册独立 `bid:edit` 权限并补齐权限、租户、状态和不可覆盖字段证据。
非目标：
- 不修改状态、关联项目、金额/成本项，不实现删除、中标、失标或批量编辑。
- 不新增路由、页面或表，不改变成本结转/冲销逻辑。
- 不连接/发布生产、不 push；浏览器不提交编辑。
允许修改：
- `backend/src/main/java/com/cgcpms/bid/controller/BidCostController.java`
- `backend/src/main/java/com/cgcpms/bid/service/BidCostService.java`
- `backend/src/main/java/com/cgcpms/bid/dto/BidCostUpdateRequest.java`
- `backend/src/main/resources/db/migration/V152__add_bid_cost_edit_permission.sql`
- `backend/src/main/resources/db/migration-h2/V152__add_bid_cost_edit_permission.sql`
- `backend/src/test/java/com/cgcpms/bid/BidCostControllerTest.java`
- `frontend-admin/src/types/bid.ts`
- `frontend-admin/src/api/modules/bid.ts`
- `frontend-admin/src/api/modules/__tests__/bid.test.ts`
- `frontend-admin/src/pages/bid-cost/index.vue`
- `frontend-admin/src/pages/bid-cost/__tests__/index.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-037-投标成本受控修改入口与状态租户边界验收报告.md`
禁止修改：
- `backend/src/main/resources/db/migration/V150__*`
- `backend/src/main/resources/db/migration/V151__*`
- `backend/src/main/resources/db/migration-h2/V150__*`
- `backend/src/main/resources/db/migration-h2/V151__*`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- PUT body 仅含 trim 后名称与可选备注；路径 ID 为唯一目标，tenantId/projectId/bidStatus/金额/审计字段不可覆盖。
- 入口仅对 `bid:edit` 或管理员可见且仅 BIDDING 展示；取消不请求，成功刷新，失败保留表单。
- 401/403、合法修改、跨租户/不存在统一隐藏、非 BIDDING 拒绝及白名单不变量通过。
- V152 两方言等价且只绑定既有三类角色；列表、新建、详情和金额事实不回退。
- 收口移除 `A-01-BID-UPDATE`，A-01 更新为有用户入口242、需补入口6；新增后续项0、关闭1、净变化-1。
- Ready lint、后端专项、前端专项、类型、ESLint、SQL 静态、diff 与只取消浏览器验收通过。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-037`
- `cd backend; .\mvnw.cmd "-Dtest=BidCostControllerTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/types/bid.ts src/api/modules/bid.ts src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/index.vue src/pages/bid-cost/__tests__/index.test.ts`
- `git diff --check`

收口结论：名称/备注白名单修改、独立 `bid:edit`、租户隐藏、BIDDING 状态门禁及客户端越权字段不可覆盖均通过；审查中发现并修复原状态接口复用 `bid:edit` 的权限耦合，状态迁移改用 `bid:status` 且专项证明编辑权限不能迁移状态。浏览器只打开并取消编辑，后端 PUT 前后均为0。`A-01-BID-UPDATE` 已从唯一台账移除；新增后续项0、关闭后续项1、后续项净变化-1。

### ISSUE-040-036：项目受控归档入口与项目数据范围

优先级：P1
任务性质：缺口修复
类型：项目 / 归档 / 写操作 / 权限 / 项目数据范围 / 依赖门禁
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-PROJECT-ARCHIVE`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=688fc11553918cb2c25c46a9ae2dc83225a2a1af
存量问题键：[stock:A-01-PROJECT-ARCHIVE]
归档报告：`docs/quality/ISSUE-040-036-项目受控归档入口与项目数据范围验收报告.md`
Migration：不需要
依赖：复用现有项目列表、项目 API、`PUT /projects/{id}/archive`、`project:edit`、`ProjectAccessChecker` 和 `PmProjectArchiveTest`。
风险等级：高
运行态要求：仅在 local/dev/test 验收；先通过三项 health gate，浏览器只打开并取消归档确认，不确认归档、不新增、修改、删除或重置业务数据。
Reviewer要求：复核入口仅对 `project:edit` 或管理员可见且已归档行隐藏；后端在依赖查询前执行项目数据范围校验；跨租户、无项目范围、重复归档及活动依赖均 fail-close。
最小回滚：回退项目归档 API、列表动作、数据范围补强、专项测试及治理回写；浏览器不写数据，不需要数据回滚。
目标：
- 在桌面项目列表的既有操作菜单增加受控“归档”动作，明确展示项目名称并要求二次确认。
- 精确调用既有项目归档接口；成功后刷新列表，失败保留当前列表并展示服务端门禁原因。
- 补齐服务端项目数据范围检查及前后端权限、状态、依赖门禁测试。
非目标：
- 不开放移动端写操作，不实现批量归档、撤销归档、物理删除或自动关闭依赖。
- 不改变项目归档状态值、合同/付款/结算/流程门禁规则，不新增路由、菜单、权限码或数据库迁移。
- 不连接生产、不发布生产、不自动 push；浏览器验收不提交归档。
允许修改：
- `backend/src/main/java/com/cgcpms/project/service/PmProjectService.java`
- `backend/src/test/java/com/cgcpms/project/PmProjectArchiveTest.java`
- `backend/src/test/java/com/cgcpms/project/PmProjectControllerTest.java`
- `frontend-admin/src/api/modules/project.ts`
- `frontend-admin/src/pages/project/index.vue`
- `frontend-admin/src/pages/project/components/ProjectTablePanel.vue`
- `frontend-admin/src/pages/project/__tests__/ProjectLedgerProduction.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-036-项目受控归档入口与项目数据范围验收报告.md`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- API 精确调用 `PUT /projects/{id}/archive`，不发送 params、body、tenantId 或其他项目字段。
- 归档入口仅对 `project:edit` 或 ADMIN/SUPER_ADMIN 可见，仅未归档行展示；确认文案包含项目名称，取消不发请求，成功刷新，失败不删除或伪改列表。
- 未登录401、无 `project:edit` 403；具备权限但无项目数据范围403；跨租户隐藏；管理员或可访问项目用户进入既有活动合同、付款、结算、流程、重复归档门禁。
- 归档 Service 在依赖查询和状态更新前复用 `ProjectAccessChecker`；物理删除、项目编辑、列表、移动卡片及现有依赖判断不回退。
- 收口移除 `A-01-PROJECT-ARCHIVE`，A-01 更新为有用户入口241、需补入口7；新增后续项0、关闭后续项1、净变化-1。
- Ready lint、前后端专项、类型检查、目标 ESLint、`git diff --check` 和只取消不提交的浏览器验收通过。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-036`
- `cd backend; .\mvnw.cmd "-Dtest=PmProjectArchiveTest,PmProjectControllerTest" test`
- `cd frontend-admin; pnpm test:unit -- src/pages/project/__tests__/ProjectLedgerProduction.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/project.ts src/pages/project/index.vue src/pages/project/components/ProjectTablePanel.vue src/pages/project/__tests__/ProjectLedgerProduction.test.ts`
- `git diff --check`

收口结论：项目归档精确 PUT、权限可见性、二次确认、项目数据范围、跨租户隐藏和既有四类依赖门禁均通过自动化与 180 秒稳定运行态验收；浏览器只取消确认且后端归档 PUT 前后均为0。`A-01-PROJECT-ARCHIVE` 已从唯一台账移除；新增后续项0、关闭后续项1、后续项净变化-1。

### ISSUE-040-035：间接费规则只读列表入口与租户边界

优先级：P1
任务性质：缺口修复
类型：间接费 / 规则列表 / 用户入口 / 权限 / 租户隔离
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-OVERHEAD-LIST`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=5cec5beb458496888a7e62f6da76704a79b06cd1
存量问题键：[stock:A-01-OVERHEAD-LIST]
归档报告：`docs/quality/ISSUE-040-035-间接费规则只读列表入口与租户边界验收报告.md`
Migration：不需要
依赖：复用成本台账页面、成本 API、`GET /overhead-allocation/rules`、`overhead:query` 与既有 Controller/Service 测试。
风险等级：高
运行态要求：仅在 local/dev/test 以只读方式验收；先通过三项 health gate，不新增、修改、删除或重置规则及成本数据。
Reviewer要求：复核列表入口仅对 `overhead:query` 或管理员可见，请求不含 tenantId，跨租户数据不可见；不得放宽执行权限或引入规则写操作。
最小回滚：回退前端规则类型/API/弹窗入口、专项测试及治理回写，不涉及数据回滚。
目标：
- 在现有成本台账页为具备间接费查询权限的用户或管理员提供规则只读列表弹窗。
- 展示成本科目 ID、分摊依据、周期和状态，支持服务端分页；失败保留弹窗且不显示旧数据。
- 补齐前后端权限、租户、请求契约及页面交互测试。
非目标：
- 不实现规则新建、修改、删除、执行或详情，不新增路由、菜单、权限码或数据库迁移。
- 不修改后端生产代码、成本科目模型、分摊算法或金额事实。
- 不连接生产、不发布生产、不自动 push，不写业务数据。
允许修改：
- `frontend-admin/src/api/modules/cost.ts`
- `frontend-admin/src/pages/cost/ledger.vue`
- `frontend-admin/src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `backend/src/test/java/com/cgcpms/overhead/OverheadAllocationControllerTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-035-间接费规则只读列表入口与租户边界验收报告.md`
禁止修改：
- `backend/src/main/**`
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- API 精确调用 `GET /overhead-allocation/rules`，params 仅含 pageNo/pageSize，不含 tenantId、body 或写操作。
- 入口仅对 `overhead:query` 或 ADMIN/SUPER_ADMIN 可见；打开加载第一页，分页加载，失败清空旧数据并保留弹窗。
- 未登录返回401，无权限403，`overhead:query` 或管理员成功；Service 使用认证 tenantId，跨租户规则不返回。
- 既有间接费执行权限、月份校验、幂等与成本台账能力不回退；收口移除 `A-01-OVERHEAD-LIST`，A-01 更新为有用户入口240、需补入口8。
- 新增后续项0、关闭后续项1、净变化-1；Ready lint、前后端专项、类型检查、目标 ESLint、`git diff --check` 通过。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-035`
- `cd backend; .\mvnw.cmd "-Dtest=OverheadAllocationControllerTest,TenantBoundaryTask2Test" test`
- `cd frontend-admin; pnpm test:unit -- src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/cost.ts src/pages/cost/ledger.vue src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `git diff --check`

收口结论：间接费规则只读入口、服务端分页、失败清空与请求竞态门禁、权限和认证租户边界均通过自动化及 180 秒稳定运行态验收；`A-01-OVERHEAD-LIST` 已从唯一台账移除。新增后续项0、关闭后续项1、后续项净变化-1。

### ISSUE-040-034：工作流个人效率统计只读入口与数据范围

优先级：P1
任务性质：缺口修复
类型：工作流 / 用户入口 / 个人效率 / 只读统计 / 身份与租户隔离
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-WORKFLOW-EFFICIENCY`；唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=4365b297325c0b20528375a0e7f82449bfdae918
存量问题键：[stock:A-01-WORKFLOW-EFFICIENCY]
归档报告：`docs/quality/ISSUE-040-034-工作流个人效率统计只读入口与数据范围验收报告.md`
Migration：不需要
依赖：复用现有审批工作台、筛选条件、`GET /workflow/statistics/efficiency`、`WfEfficiencyVO` 与 `WorkflowQueryService.getMyEfficiency`。
风险等级：高
运行态要求：仅在 local/dev/test 验证；浏览器验收前检查 8080 health、5173 与 dev-login，失败先按环境前置处理；验收只读，不创建、修改、删除或重置数据。
Reviewer要求：复核请求只携带当前筛选和 overdueHours，不携带 userId/tenantId；接口仅使用认证用户与租户，失败不展示旧统计；后端统计口径和既有审批列表不回退。
最小回滚：回退本 Issue 的前端类型、API、分析栏展示、专项测试及治理回写；不修改后端生产逻辑或数据。
目标：
- 为现有审批工作台增加个人效率只读统计，展示待办、逾期待办、已办、已处理任务和平均处理分钟数。
- 统计请求复用当前关键词、业务类型、实例状态和时间筛选，固定 48 小时逾期阈值；列表筛选刷新时同步刷新统计。
- 补齐前端契约测试，并以既有后端查询服务测试证明用户、租户和统计口径。
非目标：
- 不新增路由、菜单、权限码、后端生产代码、数据库迁移或跨用户/跨租户统计。
- 不新增图表平台、导出、团队排名、趋势预测或可配置阈值。
- 不连接生产、不发布生产、不自动 push，不写业务数据。
允许修改：
- `frontend-admin/src/api/modules/workflow.ts`
- `frontend-admin/src/pages/approval/todo.vue`
- `frontend-admin/src/pages/approval/__tests__/ApprovalWorkList.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-034-工作流个人效率统计只读入口与数据范围验收报告.md`
禁止修改：
- `backend/src/main/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- API 精确调用 `GET /workflow/statistics/efficiency`，params 仅来自当前筛选并含 `overdueHours: 48`，不含 userId、tenantId、body 或写请求。
- 分析栏显示五项个人效率；加载或失败时不展示旧统计，列表筛选、重置、分页、四个审批 tab 与移动端布局不回退。
- 既有 `WorkflowQueryServiceTest` 证明 tenantId、userId、筛选、逾期和平均处理时长口径；未认证接口仍由 `isAuthenticated()` fail-close。
- 收口移除 `A-01-WORKFLOW-EFFICIENCY`，更新 A-01 守恒、Ready、current-focus、project-map；新增后续项0、关闭后续项1、净变化-1。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint 与 `git diff --check` 通过。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-034`
- `cd backend; .\mvnw.cmd "-Dtest=WorkflowQueryServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/pages/approval/__tests__/ApprovalWorkList.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/workflow.ts src/pages/approval/todo.vue src/pages/approval/__tests__/ApprovalWorkList.test.ts`
- `git diff --check`

收口结论：个人效率统计 API、当前筛选同步、请求竞态门禁、五项分析栏展示、身份与租户统计口径均通过自动化和 180 秒稳定运行态验收；`A-01-WORKFLOW-EFFICIENCY` 已从唯一台账移除。新增后续项0、关闭后续项1、后续项净变化-1。

### ISSUE-040-033：投标成本详情只读入口与租户边界

优先级：P1
任务性质：缺口修复
类型：投标成本 / 用户入口 / 只读详情 / 权限 / 租户隔离 / 不存在记录 fail-close
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-BID-DETAIL`；正式唯一问题载体为 `docs/backlog/current-issues.json`，其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；候选证据基于当前 HEAD `d28d71a83a6709560166e028d64bf5b72a365ccd`。
存量问题键：[stock:A-01-BID-DETAIL]
关联产品目标：在既有投标成本列表提供最小只读详情入口，让具备 `bid:query` 的用户查看当前租户投标头信息，同时保持跨租户与不存在记录统一隐藏。
核验结论：问题仍存在——后端 `GET /bid-cost/{id}` 已由 `bid:query` 或 ADMIN/SUPER_ADMIN 保护，`BidCostService.requireExisting` 对不存在和跨租户记录统一抛出 `BID_COST_NOT_FOUND`；前端同域 API 与 `/bid-cost` 页面只实现列表和受控新建，没有详情请求或入口，现有 Controller 测试也未精确覆盖详情权限与跨租户拒绝路径。
候选对比：当前 P1 Open 叶子中，DETAIL 复用刚完成的投标列表页面与既有只读接口，不新增状态、金额或业务写入；UPDATE/DELETE/WON/LOST 和间接费 CRUD 均涉及写状态、金额或删除，风险与回滚成本更高，因此本次单 Issue 金丝雀选择 DETAIL。
检索交叉核验：CodeGraph 命中 `BidCostController.getById`、`BidCostService.getById/requireExisting` 与租户判断，但未完整召回前端同域精确文件，归类为工具召回不足；已用 `rg` 和直接文件读取确认前端缺口，并由只读 `codebase-memory-mcp` 交叉确认 Controller→Service 以及前后端 `/bid-cost` 关系。最终以当前分支源码、唯一台账与源报告为准。
阻塞证据：具备查询权限的用户只能从列表看到有限列，无法访问后端已有详情；详情接口缺少未登录、无权限、跨租户和不存在记录的精确集成测试证据。
解除条件：前端增加类型化详情 GET 并在桌面表格和移动卡片提供只读详情入口；详情弹窗覆盖加载、成功、失败保留和关闭重开；后端详情的 401、403、跨租户与不存在记录 fail-close、合法读取证据齐全，列表与新建能力不回退。
Migration：不需要
依赖：复用既有 `/bid-cost` 页面、`BidCostVO`、类型化 request、`GET /bid-cost/{id}`、`BidCostController`、`BidCostService.requireExisting`、V150 `bid:query` 菜单权限及现有前后端测试夹具；不新增路由、菜单、权限码、后端主代码、表或状态机。
风险等级：高
风险说明：详情会扩大单条租户业务数据的前端可达性，必须证明无 `bid:query`、跨租户和不存在记录均 fail-close，且前端不发送 tenantId、不引入写操作。
运行态要求：自动化只在 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，任一失败先归类为环境前置并执行 runtime refresh，稳定等待180秒后复验；浏览器只读打开现有投标详情，不创建、修改、删除或重置业务数据。
Reviewer要求：按高风险只读权限与租户边界复核；确认前端只向所选记录 ID 发起 GET、无 tenantId/请求体/写操作，详情入口不另行放宽权限；复核后端 401、无权限403、跨租户与不存在记录统一隐藏、合法读取，页面失败不伪报空数据，列表、新建、筛选、分页与移动端布局不回退；对绑定 diff 给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-033-投标成本详情只读入口与租户边界验收报告.md`
最小回滚：回退本 Issue 的前端详情 API、页面入口/弹窗、专项测试与治理回写，以及后端新增的详情安全测试；不删除或重置投标、项目或成本数据，不改变既有接口、列表、新建、状态和金额逻辑。
目标：
- 在投标 API 增加类型化详情请求，只调用既有 `GET /bid-cost/{id}`，不发送 tenantId、params、body 或写请求。
- 在既有投标成本页为桌面表格与移动卡片增加只读详情入口，以弹窗展示项目名称、状态、关联项目、备注、创建和更新时间，覆盖加载、失败保留与关闭重开。
- 补齐前端 API/页面契约测试，并在既有后端 Controller 集成测试中增加详情权限、租户及不存在记录的精确样本。
非目标：
- 不开放编辑、删除、中标、失标、项目转换、金额/成本项、批量、导入或导出，不新增详情路由或独立页面。
- 不修改后端 Controller/Service/实体/Mapper、数据库迁移、菜单、权限码、状态机、成本汇总或项目访问规则，不在前端显示 tenantId 或审计主体字段。
- 不连接生产数据库、不发布生产、不自动 push，不新增、修改、删除或重置本地业务数据。
允许修改：
- `backend/src/test/java/com/cgcpms/bid/BidCostControllerTest.java`
- `frontend-admin/src/api/modules/bid.ts`
- `frontend-admin/src/api/modules/__tests__/bid.test.ts`
- `frontend-admin/src/pages/bid-cost/index.vue`
- `frontend-admin/src/pages/bid-cost/__tests__/index.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-033-投标成本详情只读入口与租户边界验收报告.md`
禁止修改：
- `backend/src/main/**`
- `backend/src/test/java/com/cgcpms/bid/BidCostServiceTest.java`
- `frontend-admin/src/types/bid.ts`
- `frontend-admin/src/api/request.ts`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- 前端 API 测试精确断言详情请求为 `GET /bid-cost/{id}`，无 body、params 与 tenantId；复用 `BidCostVO`，不新增或展示租户字段。
- 桌面表格与移动卡片均可打开详情；点击后显示加载态，成功展示项目名称、状态、关联项目、备注、创建和更新时间；失败显示错误并保持弹窗可见且不展示上一条记录，关闭后清空，再次打开按当前记录重新请求。
- 未登录详情请求返回401；已登录但无 `bid:query` 返回403；持 `bid:query` 或 ADMIN/SUPER_ADMIN 可读取当前租户记录；跨租户 ID 与不存在 ID 均返回相同业务错误且不泄露记录内容。
- 既有列表查询、筛选、分页、空态、受控新建及移动端列表测试不回退；浏览器验收只读打开现有记录详情，控制台无新增 error/warn。
- 收口必须引用 `docs/backlog/current-issues.json` 及本项 `sourceRefs`；全部通过后移除 `A-01-BID-DETAIL`，同步更新 A-01 守恒为有用户入口238、前端调用但无独立页面58、内部/集成/运维4、需补入口10、待废弃0、需要确认11、总数321，并回写 Ready、current-focus、project-map；若差距或优先级判断变化，再更新 evolution-decision。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；所有发现项必须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。权限、租户或不存在记录 fail-close 证据不足时判不通过。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint 与 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-033`
- `cd backend; .\mvnw.cmd "-Dtest=BidCostControllerTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/bid.ts src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/index.vue src/pages/bid-cost/__tests__/index.test.ts`
- `git diff --check`

收口结论：投标详情 API、桌面/移动入口、只读弹窗、请求竞态防护，以及 `bid:query`、401/403、跨租户与不存在记录 fail-close 均已通过自动化和运行态核验；`A-01-BID-DETAIL` 已从唯一台账移除。新增后续项0、关闭后续项1、后续项净变化-1。

### ISSUE-040-032：投标成本受控新建入口与租户状态边界

优先级：P1
任务性质：缺口修复
类型：投标成本 / 用户入口 / 受控新建 / 权限 / 租户隔离 / 状态边界 / 菜单迁移
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-BID-CREATE`；正式唯一问题载体为 `docs/backlog/current-issues.json`，其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；候选证据基于当前 HEAD `163db3d0560ce3fca1c856601cee0e6d1025e0df`。
存量问题键：[stock:A-01-BID-CREATE]
关联产品目标：在现有投标成本列表提供最小受控新建入口，让具备 `bid:add` 的用户创建投标头，同时保持当前租户、初始状态和金额域边界。
核验结论：问题仍存在——后端 `POST /bid-cost` 已由 `bid:add` 或 ADMIN/SUPER_ADMIN 保护，但前端仅有只读列表，未调用创建接口；Controller 直接接收 `BidCost` 实体，当前 Service 虽覆盖 tenantId 和状态，却未显式清空 projectId。`BidCost` 头表不存金额，金额完全由 `cost_item` 聚合，因此本轮只能创建项目名称与备注，不能虚构金额字段。
候选对比：`A-01-BID-CREATE` 是当前 P1、Open、非阻塞、证据完整的最高排序叶子；上一轮 `A-01-BID-LIST` 已解除“无承载页面”前置。DETAIL 为只读但排序在后，UPDATE/DELETE/WON/LOST 涉及既有记录状态流转，本轮先完成最小创建闭环。
检索交叉核验：CodeGraph 命中 Controller、Service、实体及调用范围，但未完整召回既有前端同域文件，归类为工具召回不足；已用 `rg` 和直接文件读取补查，并由只读 `codebase-memory-mcp` 交叉确认 Controller→Service→Mapper、路由、前端页面与测试关系。最终以当前分支源码、唯一台账与源报告为准。
阻塞证据：持 `bid:add` 用户没有管理端创建入口；过宽实体载荷使客户端可尝试提交租户、状态和项目关联字段，当前测试也未证明细粒度权限、注入字段覆盖与 V151 权限注册。
解除条件：现有 `/bid-cost` 页面新增仅对 `bid:add` 或管理员可见的新建入口；请求 DTO 只接受项目名称和备注；后端强制当前租户、`BIDDING`、projectId 为空；V151 注册 `bid:add` 按钮权限并绑定既有投标菜单角色；正负权限、租户、状态、金额无写入边界和前端成功/失败态证据齐全。
Migration：需要
依赖：复用既有 `/bid-cost` 页面、类型化 request、`POST /bid-cost`、`BidCostController`、`BidCostService`、`UserContext`、V150 投标菜单及 `sys_menu` / `sys_role_menu`；新增 V151 权限按钮迁移，不改表结构。
风险等级：高
风险说明：该入口会写入租户业务数据并扩大角色可达能力，必须证明客户端不能指定 tenantId、projectId、状态或金额，且无权限请求 fail-close。
运行态要求：自动化只在 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，任一失败先归类为环境前置并执行 runtime refresh，稳定等待180秒后复验；浏览器仅验证入口可见性和表单边界，不提交或重置业务数据。
Reviewer要求：按高风险写入、权限、租户和状态边界复核；确认前端仅发送名称与可选备注，Controller 使用专用 DTO，Service 覆盖当前租户/初始状态并清空项目关联，V151 不放宽查询/编辑/删除权限；复核401/403、`bid:add` 正样本、管理员旁路、注入字段覆盖、金额无写入和页面错误保留，对绑定 diff 给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-032-投标成本受控新建入口与租户状态边界验收报告.md`
最小回滚：回退本 Issue 的创建 DTO、Controller/Service 收窄、前端创建类型/API/表单、V151 权限迁移、专项测试与治理回写；不删除或重置任何投标或成本业务数据，不改变既有列表、详情、更新、删除和状态接口。
目标：
- 为 `POST /bid-cost` 增加仅含 `bidProjectName` 与可选 `remark` 的创建 DTO，后端强制当前 tenantId、`BIDDING` 和空 projectId。
- 在既有投标成本页增加受 `bid:add` 或管理员权限控制的新建弹窗，覆盖校验、提交加载、成功关闭并刷新、失败保留表单。
- 新增 V151 `bid:add` 按钮权限及既有角色绑定，补齐前端 API/页面和后端 Controller/Service/迁移正负边界测试。
非目标：
- 不创建、录入或聚合成本项/金额；不开放详情、编辑、删除、中标、未中标、项目转换、批量、导入或导出。
- 不修改既有查询路由/菜单权限、状态机、表结构、Mapper、成本汇总或项目访问规则；不允许客户端发送 tenantId、projectId、bidStatus、金额或审计字段。
- 不连接生产数据库、不发布生产、不自动 push，不在浏览器验收中提交、修改、删除或重置业务数据。
允许修改：
- `backend/src/main/java/com/cgcpms/bid/dto/BidCostCreateRequest.java`
- `backend/src/main/java/com/cgcpms/bid/controller/BidCostController.java`
- `backend/src/main/java/com/cgcpms/bid/service/BidCostService.java`
- `backend/src/main/resources/db/migration/V151__add_bid_cost_create_permission.sql`
- `backend/src/main/resources/db/migration-h2/V151__add_bid_cost_create_permission.sql`
- `backend/src/test/java/com/cgcpms/bid/BidCostControllerTest.java`
- `backend/src/test/java/com/cgcpms/bid/BidCostServiceTest.java`
- `frontend-admin/src/types/bid.ts`
- `frontend-admin/src/api/modules/bid.ts`
- `frontend-admin/src/api/modules/__tests__/bid.test.ts`
- `frontend-admin/src/pages/bid-cost/index.vue`
- `frontend-admin/src/pages/bid-cost/__tests__/index.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-032-投标成本受控新建入口与租户状态边界验收报告.md`
禁止修改：
- `backend/src/main/java/com/cgcpms/bid/entity/**`
- `backend/src/main/java/com/cgcpms/bid/mapper/**`
- `backend/src/main/java/com/cgcpms/cost/**`
- `frontend-admin/src/api/request.ts`
- `frontend-admin/src/router/**`
- `frontend-admin/src/stores/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- 创建 API 测试精确断言 `POST /bid-cost` body 仅含去空格后的 `bidProjectName` 与可选 `remark`，不含 tenantId、projectId、bidStatus、amount 或审计字段；空名称、超长名称和超长备注返回4xx。
- 未登录创建返回401；已登录无 `bid:add` 返回403；持 `bid:add` 或 ADMIN/SUPER_ADMIN 成功。即使客户端尝试注入 tenantId、projectId 和 bidStatus，落库仍为当前租户、projectId 空、`BIDDING`，且不创建 `cost_item` 或任何金额事实。
- 页面新建按钮只对 `bid:add` 或管理员可见；表单只含项目名称和备注，成功关闭、重置并刷新列表，失败显示错误且保持弹窗与输入；只读查询、筛选、分页和空态不回退。
- V151 仅在 V150 投标菜单下新增 `bid:add` BUTTON 并绑定 SUPER_ADMIN、ADMIN、COST_MANAGER，不授予 `bid:edit` 或 `bid:delete`，MySQL/H2 迁移语义一致。
- 收口必须引用唯一台账及 `sourceRefs`；全部通过后移除 `A-01-BID-CREATE`，同步更新 A-01 守恒为有用户入口237、前端调用但无独立页面58、内部/集成/运维4、需补入口11、待废弃0、需要确认11、总数321，并回写 Ready、current-focus、project-map；若差距或优先级判断变化，再更新 evolution-decision。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；所有发现项必须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。权限、租户、状态、金额无写入或迁移证据不足时判不通过。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint、SQL 安全检查与 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-032`
- `cd backend; .\mvnw.cmd "-Dtest=BidCostControllerTest,BidCostServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/types/bid.ts src/api/modules/bid.ts src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/index.vue src/pages/bid-cost/__tests__/index.test.ts`
- `pwsh -NoProfile -File scripts/check-sql-safety.ps1`
- `git diff --check`

收口结论：受控新建入口、专用 DTO、`bid:add` 权限、当前租户、`BIDDING`、空 projectId 与金额无写入边界均已通过自动化和运行态核验；`A-01-BID-CREATE` 已从唯一台账移除。新增后续项0、关闭后续项1、后续项净变化-1。

### ISSUE-040-030：成本汇总历史只读入口与项目数据边界

优先级：P1
任务性质：缺口修复
类型：成本管理 / 历史快照 / 用户入口 / 权限 / 租户隔离 / 项目数据范围 / 金额只读
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-COST-HISTORY`；正式唯一问题载体为 `docs/backlog/current-issues.json`，其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`。
存量问题键：[stock:A-01-COST-HISTORY]
关联产品目标：在现有成本核对页提供项目成本汇总历史快照的只读入口，使已实现且受 `cost:summary:view` 与项目访问控制保护的接口对合格用户可达，并保持金额与租户边界。
核验结论：问题仍存在——后端 `GET /cost-summary/{projectId}/history` 已返回按汇总日期倒序的科目快照，并由 `cost:summary:view`、认证租户和 `ProjectAccessChecker` 保护；现有 Controller/Service 测试覆盖未登录、同租户无项目访问及正向读取，但前端成本 API 和 `/cost/summary` 页面均未调用或展示历史。前端当前聚合 `CostSummaryVO` 与后端历史行级 `CostSummaryVO` 结构不同，必须建立独立历史类型，不能把行级快照伪装为项目聚合对象。
候选对比：上一轮已关闭角色域共享布局缺口，本轮主动切换到成本域。P1 投标和间接费写操作涉及新增状态或金额写入，风险与闭环更大；A-02至A-05为需先过产品决策门的聚合父项。`A-01-COST-HISTORY` 是证据完整的只读叶子，已有页面、接口和测试夹具，最适合作为本次有界第三轮。
检索交叉核验：CodeGraph 命中 `CostSummaryService`、成本核对页及相关类型，但对精确路由召回偏噪，归类为工具召回不足；`codebase-memory-mcp` 补充确认 Controller→Service→Mapper 与前端 API/页面/测试关系；最终以当前分支精确 `rg`、直接文件读取、唯一台账与源报告为准。
阻塞证据：合格用户只能查看当前成本聚合，无法从现有页面访问既有历史快照接口，成本核对缺少最小时间追溯入口；直接复用聚合类型会丢失汇总日期与科目行语义。
解除条件：前端增加类型化历史请求并在现有成本核对页提供选定项目后的只读历史入口，明确展示汇总日期、科目和关键金额，覆盖加载、空态、错误保留；后端历史接口的401、无权限403、同租户无项目访问403、跨租户隐藏和合法访问证据齐全；当前汇总与重算能力不回退。
Migration：不需要
依赖：复用现有 `/cost/summary` 页面、成本 API request、项目选择器、`GET /cost-summary/{projectId}/history`、`CostSummaryController`、`CostSummaryService`、`ProjectAccessChecker` 及现有前后端测试夹具；不新增路由、菜单、权限码、后端主代码、表或定时任务。
风险等级：高
风险说明：历史快照包含项目成本金额，虽然本次只增加读取入口，但必须证明权限、租户和项目数据范围未被前端可达性变化放宽。
运行态要求：自动化只在 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，任一失败先归类为环境前置并使用 runtime refresh，稳定等待180秒后复验；从 dev-login 进入 `/cost/summary`，选择现有可访问项目后只读打开历史，不刷新汇总、不修改或重置业务数据。
Reviewer要求：按高风险金额只读与项目数据范围变更复核前端只向选定 projectId 发起 GET 且无 tenantId、无写操作；复核路由既有 `cost:summary:view` 门禁、后端未登录401、无权限403、同租户无项目访问403、跨租户隐藏及合法项目读取；复核历史行级类型不与项目聚合类型混淆，错误不伪报空数据，当前汇总、重算和移动端布局不回退，并对绑定 diff 给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-030-成本汇总历史只读入口与项目数据边界验收报告.md`
最小回滚：回退本 Issue 的前端历史类型、API、页面入口、测试和治理回写，以及后端新增的精确安全测试；不删除历史快照或业务数据，不改变既有接口、表、当前汇总和定时刷新。
目标：
- 在成本 API 增加类型化项目历史请求，只调用既有历史 GET 路径，不发送 tenantId、请求体或写参数。
- 在现有成本核对页增加选定项目后可用的历史入口，以只读弹窗展示汇总日期、成本科目、目标成本、实际成本、动态成本和偏差，覆盖加载、空态、错误与关闭重开。
- 补齐前端 API/页面行为测试，并在既有后端 Controller 测试中增加历史接口无权限与跨租户精确样本，证明项目金额数据 fail-close。
非目标：
- 不新增历史表、分页接口、快照生成、对比图、导出、恢复、删除、重算或手工写入能力，不改变历史排序和后端返回结构。
- 不修改路由、菜单、权限码、Service、Mapper、实体、数据库迁移、定时任务或项目访问规则，不把行级历史快照聚合成新的财务口径。
- 不连接生产数据库、不发布生产、不自动 push，不新增、修改、删除或重置本地业务数据。
允许修改：
- `frontend-admin/src/api/modules/cost.ts`
- `frontend-admin/src/api/modules/__tests__/cost.test.ts`
- `frontend-admin/src/types/cost.ts`
- `frontend-admin/src/pages/cost/summary.vue`
- `frontend-admin/src/pages/cost/__tests__/CostSummaryProduction.test.ts`
- `backend/src/test/java/com/cgcpms/cost/CostSummaryControllerTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-030-成本汇总历史只读入口与项目数据边界验收报告.md`
禁止修改：
- `backend/src/main/**`
- `backend/src/test/java/com/cgcpms/cost/CostSummaryServiceTest.java`
- `frontend-admin/src/router/**`
- `frontend-admin/src/pages/cost/components/**`
- `frontend-admin/src/pages/cost/ledger.vue`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- 前端 API 测试精确断言历史请求为 `GET /cost-summary/{projectId}/history`，无 body、params 与 tenantId；独立历史类型保留汇总日期、科目和金额字段，不复用项目聚合 `subjects` 语义。
- 历史按钮未选择项目时禁用；选择项目后点击才发请求并打开只读弹窗，成功展示日期、科目和关键金额，空数组显示明确空态，失败显示错误且弹窗保持可见、不伪报成功，关闭重开重新读取当前选定项目。
- 未登录历史请求返回401；已登录但无 `cost:summary:view` 返回403；持权限但无目标项目访问返回403；跨租户项目不可见；合法 ADMIN/SUPER_ADMIN 或持权限且满足项目数据范围的用户可读取，响应仍只含既有历史行字段。
- 既有当前成本汇总读取、动态成本重算、项目选择、移动端卡片、桌面表格和列设置测试不回退；浏览器验收只读选择现有项目并打开历史，控制台无新增 error/warn。
- 收口必须引用 `docs/backlog/current-issues.json` 及本项 `sourceRefs`；全部通过后移除 `A-01-COST-HISTORY`，未完全通过则用证据更新其唯一状态或分类。通过时同步更新 A-01 守恒为有用户入口235、前端调用但无独立页面58、内部/集成/运维4、需补入口13、待废弃0、需要确认11、总数321，并回写 Ready、current-focus、project-map；若差距或优先级判断变化，再更新 evolution-decision。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；所有发现项必须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。金额、权限、租户、项目数据范围或历史类型证据不足时判不通过。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint 与 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-030`
- `cd backend; .\mvnw.cmd "-Dtest=CostSummaryControllerTest,CostSummaryServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/cost.test.ts src/pages/cost/__tests__/CostSummaryProduction.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/cost.ts src/api/modules/__tests__/cost.test.ts src/types/cost.ts src/pages/cost/summary.vue src/pages/cost/__tests__/CostSummaryProduction.test.ts`
- `git diff --check`

### ISSUE-040-031：投标成本只读列表入口与租户边界

优先级：P1
任务性质：缺口修复
类型：投标成本 / 用户入口 / 只读列表 / 权限 / 租户隔离 / 菜单迁移
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-BID-LIST`；正式唯一问题载体为 `docs/backlog/current-issues.json`，其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；候选证据基于当前 HEAD `95767cb2e9203bdb4640a7df6ccb5556c72577b9`。
存量问题键：[stock:A-01-BID-LIST]
关联产品目标：让已实现的投标成本分页查询在管理端具备最小只读入口，使合格用户可以按状态和关键字检索投标项目，同时保持 `bid:query` 与当前租户边界。
核验结论：问题仍存在——后端 `GET /bid-cost` 已提供分页、状态和关键字筛选，并由 `bid:query` 或 ADMIN/SUPER_ADMIN 保护，Service 按 `UserContext` 当前租户过滤；当前前端不存在 bid-cost API、类型、页面、路由或导航入口，Controller 测试也只覆盖未登录与管理员正向样本，缺少细粒度权限、无权限拒绝与跨租户列表证据。
候选对比：同域 CREATE/UPDATE/DELETE/WON/LOST 均涉及业务写入、状态流转或项目关联，且当前没有既有投标页面可承载；`A-01-BID-LIST` 是其最小只读前置，接口、分页与租户过滤均已存在，用户价值、依赖和验收边界最完整，因此先处理该叶子问题。
检索交叉核验：CodeGraph 命中 `BidCostController.getPage`、`BidCostService.getPage` 与实体关系但未命中前端同域文件；已按工具召回不足使用 `rg` 和直接文件读取补查，并用只读 `codebase-memory-mcp` 核验 Controller→Service→Mapper、前端路由/导航与测试边界，最终以当前分支源码、唯一台账与源报告为准。
阻塞证据：合格用户无法从管理端访问既有投标成本列表，`bid:query` 仅停留在后端接口；缺少非管理员权限正负样本和跨租户列表样本，无法把新增入口作为安全闭环验收。
解除条件：新增只读 `/bid-cost` 页面、类型化 GET API、静态路由、侧栏导航和 `bid:query` 菜单授权；页面只提供关键字、状态、分页和刷新，不出现写操作；后端未登录401、无权限403、持 `bid:query` 成功、跨租户记录不可见及菜单角色绑定证据齐全。
Migration：需要
依赖：新增 V150 菜单及角色绑定迁移但不改表结构和业务数据；复用既有 `GET /bid-cost`、`BidCostController`、`BidCostService`、`UserContext`、管理端 request、静态路由、项目运营侧栏与 `sys_menu` / `sys_role_menu`；不新增后端生产逻辑、表、写接口、状态机或金额口径。
风险等级：高
风险说明：本次虽然只开放查询，但会扩大租户业务数据的可达性并新增角色菜单授权，必须证明路由、接口权限、角色绑定和租户过滤同时 fail-close。
运行态要求：自动化只在 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，任一失败先归类为环境前置并执行 runtime refresh，稳定等待180秒后复验；从 dev-login 只读进入 `/bid-cost`，不得新增、编辑、删除、标记中标/未中标或重置业务数据，不连接或发布生产。
Reviewer要求：按高风险权限与租户只读变更复核前端只发 GET 且不发送 tenantId、请求体或写方法；复核路由与导航均要求 `bid:query`、管理员旁路不扩大普通用户权限；复核后端401/403、持权限成功、跨租户隐藏、响应字段和 V150 角色绑定；对绑定 diff 给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-031-投标成本只读列表入口与租户边界验收报告.md`
最小回滚：回退本 Issue 的前端类型、API、页面、路由、导航、V150 菜单迁移、专项测试与治理回写；不删除投标成本业务数据，不改变既有后端接口、Service 或表结构。
目标：
- 新增类型化投标成本分页 GET，只发送 pageNo、pageSize、可选 bidStatus 与 keyword，不发送 tenantId、body 或写请求。
- 新增 `/bid-cost` 只读列表页与项目运营侧栏入口，展示投标项目、状态、关联项目和创建时间，覆盖搜索、重置、刷新、分页、加载、空态与失败态。
- 补齐路由/导航权限、API 请求和页面行为测试，并在既有 Controller 集成测试中补齐细粒度权限、无权限拒绝、跨租户隐藏及 V150 菜单角色绑定证据。
非目标：
- 不开放新建、详情、编辑、删除、中标、未中标、成本项、金额汇总、导出或项目转化操作，不新增状态机和投标工作台。
- 不修改 `BidCostController`、Service、Mapper、实体、表结构或既有权限表达式，不新增 tenantId 查询参数。
- 不连接生产数据库、不发布生产、不自动 push，不新增、修改、删除或重置投标业务数据。
允许修改：
- `frontend-admin/src/types/bid.ts`
- `frontend-admin/src/api/modules/bid.ts`
- `frontend-admin/src/api/modules/__tests__/bid.test.ts`
- `frontend-admin/src/pages/bid-cost/index.vue`
- `frontend-admin/src/pages/bid-cost/__tests__/index.test.ts`
- `frontend-admin/src/router/index.ts`
- `frontend-admin/src/router/navigation.ts`
- `frontend-admin/src/router/__tests__/router.test.ts`
- `backend/src/main/resources/db/migration/V150__add_bid_cost_query_menu.sql`
- `backend/src/main/resources/db/migration-h2/V150__add_bid_cost_query_menu.sql`
- `backend/src/test/java/com/cgcpms/bid/BidCostControllerTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-031-投标成本只读列表入口与租户边界验收报告.md`
禁止修改：
- `backend/src/main/java/**`
- `frontend-admin/src/api/request.ts`
- `frontend-admin/src/stores/**`
- `frontend-admin/src/pages/cost/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- API 测试精确断言 `GET /bid-cost` 仅携带分页、可选状态和关键字参数，无 body 与 tenantId；页面测试覆盖首载、搜索重置、状态筛选、分页、刷新、空态和失败态，且 DOM 中无新增、编辑、删除、中标或未中标操作。
- 路由和导航都将 `/bid-cost` 绑定 `bid:query`；ADMIN/SUPER_ADMIN 可按既有规则旁路，普通用户无权限被导向403，持权限可进入；V150 仅新增一个只读菜单并绑定 SUPER_ADMIN、ADMIN、COST_MANAGER，不授予 `bid:add`、`bid:edit` 或 `bid:delete`。
- 未登录列表请求返回401；已登录但无 `bid:query` 返回403；持 `bid:query` 返回200；同一请求只返回当前租户记录，跨租户投标记录不可见；响应不新增敏感字段或写能力。
- 既有投标成本 CRUD、标记中标和成本域页面不回退；浏览器验收只读进入列表，筛选和分页可用，控制台无新增 error/warn。
- 收口必须引用 `docs/backlog/current-issues.json` 及本项 `sourceRefs`；全部通过后移除 `A-01-BID-LIST`，未完全通过则用证据更新其唯一状态或分类。通过时同步更新 A-01 入口分类守恒并回写 Ready、current-focus、project-map；若差距或优先级判断变化，再更新 evolution-decision。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；所有发现项必须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。权限、租户、角色迁移或只读边界证据不足时判不通过。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint、SQL 安全检查与 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-031`
- `cd backend; .\mvnw.cmd "-Dtest=BidCostControllerTest,BidCostServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/__tests__/index.test.ts src/router/__tests__/router.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/types/bid.ts src/api/modules/bid.ts src/api/modules/__tests__/bid.test.ts src/pages/bid-cost/index.vue src/pages/bid-cost/__tests__/index.test.ts src/router/index.ts src/router/navigation.ts src/router/__tests__/router.test.ts`
- `pwsh -NoProfile -File scripts/check-sql-safety.ps1`
- `git diff --check`

收口结果：通过。Ready lint 无告警；后端专项24/24、前端专项38/38、类型检查、目标 ESLint、SQL 安全和 `git diff --check` 通过；180秒稳定观察后三项 health gate 均为200，真实浏览器 `/bid-cost` 的侧栏、筛选、空态与分页正常，写操作为0，控制台 error/warn=0。
后续项统计：新增后续项0，关闭后续项1（`A-01-BID-LIST`），后续项净变化-1；所有发现项均已修复复验或有依据关闭，无悬空问题。

### ISSUE-040-029：共享列表中窄视口表格高度链修复

优先级：P1
任务性质：缺口修复
类型：前端 / 共享列表布局 / 响应式 / 表格可达性 / 跨页面回归
状态：Done
来源锚点：项目知识图谱当前问题 `UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT`；正式唯一问题载体为 `docs/backlog/current-issues.json`，其 `sourceRefs` 为 `docs/quality/ISSUE-040-027-系统角色删除管理员入口与安全边界验收报告.md`；本轮又在 `ISSUE-040-028` 真实浏览器验收中稳定复现。
存量问题键：[stock:UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT]
关联产品目标：恢复共享 `lg-list-page` 在中窄视口下的表格可见性与操作可达性，使角色管理及同布局页面在响应式断点内仍能完成列表读取与行操作。
核验结论：问题仍存在——共享 `global-list-table.css` 将 `.lg-table-wrap` 设为 `height: 0; flex: 1`，桌面态依赖父级 flex 高度链；`global-app-redesign.css` 在 `max-width: 1200px` 把 `.lg-grid` 改为 block，却继续把表格面板最小高度设为 0。真实浏览器在 CSS 宽度 921px 时测得角色页 `.lg-table-wrap` 高度为 0，切换到 1233px 后恢复约 480px。CodeGraph 对共享样式召回不足，已立即用 `rg` 补查；`codebase-memory-mcp` 与当前分支直接读取共同确认该类被多页面复用，最终事实以当前 CSS、角色页结构和浏览器测量为准。
候选对比：P0 可自动处理叶子已在上一 Issue 关闭；本项为 P1 明确问题，已有两轮真实浏览器证据、唯一台账、可执行验收标准和最小共享样式修复路径。其他 P1 投标、间接费等候选涉及独立业务链，当前不与本项并行。
阻塞证据：在 `max-width: 1200px` 断点内，角色表格高度归零，首行与操作菜单不可见、不可交互；这直接阻断中窄视口角色管理，不是纯视觉偏好。
解除条件：共享响应式规则在不恢复固定整页桌面高度的前提下为表格面板建立有界最小高度；角色页在 CSS 宽度 1036px、1200px 的表格容器高度均大于 0，首行及操作菜单可见可交互；1201px 以上桌面双栏、高度、分页和既有角色 CRUD 不回退；共享样式契约测试、类型检查、目标 ESLint、构建或等价静态验证和浏览器验收通过。
Migration：不需要
依赖：复用现有 `lg-list-page`、`lg-grid`、`lg-list-table-panel`、`lg-table-wrap` 共享类及角色管理页；不新增组件、路由、接口、状态管理或第三方依赖。
风险等级：中
风险说明：共享响应式样式会影响多个列表页，必须以最小选择器和跨断点证据控制回归。
运行态要求：自动化只在 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，任一失败先归类为环境前置并使用 runtime refresh，稳定等待180秒后复验；验收仅浏览现有角色数据，不新增、修改、删除或重置业务数据。
Reviewer要求：按共享前端布局中风险变更复核断点选择器只作用于 `lg-list-page` 的表格面板、不会覆盖桌面 `min-width: 1201px` 工作区高度；检查表格内部 100% 高度链、分页、分析栏和页面纵向滚动边界；以绑定 diff、静态契约测试及 1036/1200/1201 三个 CSS 宽度的浏览器尺寸证据给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-029-共享列表中窄视口表格高度链修复验收报告.md`
最小回滚：回退本 Issue 对共享响应式 CSS、专项目录测试和治理载体的修改；不改业务页面、角色数据、接口或数据库，无数据回滚。
目标：
- 在共享 ≤1200px 规则中恢复列表表格面板的有界最小高度，使零高度 flex 表格容器获得可计算空间。
- 保持 1201px 以上既有固定工作区与双栏布局；中窄视口继续采用单栏和页面纵向滚动，不引入角色页特例。
- 增加共享 CSS 契约测试，锁定断点、作用域、非零最小高度及桌面规则边界，并用真实浏览器验证角色页首行与操作菜单。
非目标：
- 不重构全部列表布局、不逐页改写 `lg-*` 类、不调整表格列、分页数据、角色 CRUD、权限或后端接口。
- 不顺带修复其他页面的视觉偏好，不修改用户当前工作区中已有的业务页面和测试文件。
- 不连接生产数据库、不发布生产、不自动 push。
允许修改：
- `frontend-admin/src/assets/styles/global-app-redesign.css`
- `frontend-admin/src/assets/styles/__tests__/global-list-responsive.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-029-共享列表中窄视口表格高度链修复验收报告.md`
禁止修改：
- `frontend-admin/src/pages/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/assets/styles/global-list-table.css`
- `backend/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- 共享样式在 ≤1200px 下只为 `lg-list-page` 内三种既有表格面板容器建立有界且非零的最小高度；不改变 `.lg-table-wrap` 的桌面 flex 高度策略，不给角色页增加局部样式。
- CSS 契约测试验证 1200px 响应式块仍保持单栏布局、表格面板最小高度不是 0，并验证 `min-width: 1201px` 桌面工作区高度规则仍存在；测试不依赖用户未跟踪的二级页面视觉测试。
- 真实浏览器从 dev-login 进入 `/system/roles`，分别在 CSS 宽度 1036px、1200px 测得 `.lg-table-wrap` 高度大于 0，首个数据行与 `.lg-row-action-trigger` 可见可交互；在 1201px 以上确认桌面布局、表格高度和分页可见，控制台无新增 error/warn。
- 共享类影响面通过 `rg` 与当前分支交叉核验；若浏览器发现本次 diff 直接引入其他共享布局问题，必须本轮修复并复验，超出范围项按唯一载体规则处置，不得口头悬空。
- 收口必须引用 `docs/backlog/current-issues.json` 及本项 `sourceRefs`；全部通过后移除 `UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT`，未完全通过则用证据更新其唯一状态或分类，并同步更新 Ready、current-focus 和 project-map。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；Ready lint、前端专项、类型检查、目标 ESLint、`git diff --check` 与浏览器验收全部通过，首次失败先分类并复验。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-029`
- `cd frontend-admin; pnpm test:unit -- src/assets/styles/__tests__/global-list-responsive.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/assets/styles/__tests__/global-list-responsive.test.ts`
- `git diff --check`

### ISSUE-040-028：系统角色修改管理员入口与安全边界

优先级：P0
任务性质：缺口修复
类型：系统管理 / 角色修改入口 / 权限 / 租户隔离 / 字段白名单 / 越权防护
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-ROLE-UPDATE`；正式唯一问题载体为 `docs/backlog/current-issues.json`，其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=e6ac6994b121a4eb980ef167dd6fdb59269bce59
存量问题键：[stock:A-01-ROLE-UPDATE]
关联产品目标：在既有 admin-only 角色管理页让管理员可达已实现的角色修改能力，并把现有实体直绑更新收敛为当前租户普通自定义角色的字段白名单更新，关闭 A-01 中 `PUT /system/roles/{id}` 的最小叶子缺口。
核验结论：问题仍存在——后端 `SysRoleController.update` 已由 ADMIN/SUPER_ADMIN 或 `system:role:edit` 保护，但前端系统 API 与角色页没有角色基本信息修改调用或交互；当前 `SysRoleService.update` 仅校验租户后直接 `updateById` 请求实体，未固定角色编码、类型、等级、租户和既有菜单集合。用户价值与越权风险均明确，现有角色页、Controller、Service、角色菜单关系和前后端测试夹具可形成最小闭环。
候选对比：知识图谱当前唯一 P0 叶子为 `A-01-ROLE-UPDATE`；其父项 `A-01` 仍有子项，不作为可执行聚合任务。P1 `UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT` 与投标成本/间接费等候选均不应越过当前 P0 叶子。
检索交叉核验：CodeGraph 命中后端 PUT 路由、`system:role:edit` 与 Service 更新路径，但未完整召回当前角色页，归类为工具召回不足；`codebase-memory-mcp` 补充确认 Controller、Service、前端角色页、API 测试和角色测试关系；最终使用当前分支精确 `rg`、直接文件读取、唯一问题载体与源报告交叉确认前端无 `updateRole`，当前 HEAD 与图谱 Git 游标一致。
阻塞证据：`PUT /system/roles/{id}` 当前用户不可达；若直接补前端入口，现有 Service 会把请求实体整体写回，可能覆盖 roleCode、roleType、roleLevel、tenantId 等服务端事实，并缺少对系统/保留角色的修改保护。
解除条件：既有角色管理页提供受控修改入口并精确调用 PUT；后端保留 `system:role:edit` 与认证租户 fail-close，只允许普通自定义角色更新名称、状态和数据范围，角色编码、类型、等级、租户与菜单集合不可由该接口改变；管理员、显式权限、无权限、未登录、跨租户、保留角色和提权载荷正负样本全部通过。
Migration：不需要
依赖：复用现有 `/system/roles` 页面、`SysRoleVO`、系统 API request、`SysRoleController.update`、`SysRoleService.update`、`UserContext`、角色与角色菜单表及现有测试夹具；不新增表、路由、侧栏、权限码、角色种子或后端接口。
风险等级：高
运行态要求：自动化只在 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，任一失败先归类为环境前置并使用 runtime refresh，稳定等待180秒后复验；从 dev-login 进入 `/system/roles`，仅对本 Issue 唯一测试角色验证修改成功、失败保留和列表回读。创建、修改、还原或删除测试数据必须同时满足 dev/test/demo、数据库 host 为 localhost/127.0.0.1 和 `.codex-autopilot/ALLOW_TEST_DATA_RESET`，否则不得执行并应判定收口未完成。不得连接或发布生产。
Reviewer要求：按高风险权限与数据一致性变更复核 ADMIN/SUPER_ADMIN 与 `system:role:edit` 授权未放宽、无权限403、未登录401、前端入口仍受 admin-only 页面约束；复核路径 ID 覆盖请求 ID，tenantId、roleCode、roleType、roleLevel、menuIds、审计与逻辑删除字段不能造成提权或权限集合变化，跨租户与系统/保留角色统一 fail-close；复核失败不关闭弹窗或伪报成功、成功刷新列表且既有新建/详情/删除/菜单授权链不回退，并对绑定 diff 给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-028-系统角色修改管理员入口与安全边界验收报告.md`
最小回滚：回退本 Issue 的前端修改 API、类型、角色页表单、Service 白名单更新及对应测试与治理回写；不删除已有角色或权限数据，不回退角色列表、新建、详情、删除与菜单授权能力，无 schema 或数据迁移回滚。
目标：
- 在前端系统 API 增加类型化 `updateRole(roleId, payload)`，只发送 roleName、status 和 dataScope，精确调用 `PUT /system/roles/{id}`。
- 在既有 admin-only 角色管理页增加普通自定义角色的“编辑角色”入口；表单不允许编辑角色编码、类型、等级、租户或菜单集合，校验必填与长度，失败保留弹窗和用户输入，成功关闭并刷新列表。
- 收敛后端更新路径：按路径 ID 和认证租户读取现有角色，拒绝系统/保留/高等级角色，只把允许字段写入现有实体；任何请求中的 id、tenantId、roleCode、roleType、roleLevel 或菜单相关字段不得改变服务端事实。
- 补齐 Controller/Service 与前端 API/页面正负样本，证明 `system:role:edit`、无权限拒绝、租户隔离、字段白名单、菜单集合不变和既有角色管理能力不回退。
非目标：
- 不修改角色编码，不实现批量修改、复制、默认授权、菜单授权合并、权限差异审计平台或完整角色 CRUD 重构。
- 不修改用户分配角色、菜单管理、认证/JWT、动态路由、数据库 schema、Mapper、实体或种子数据，不新增权限码。
- 不连接生产数据库、不发布生产、不自动 push。
允许修改：
- `frontend-admin/src/api/modules/system.ts`
- `frontend-admin/src/api/modules/__tests__/system-modules.test.ts`
- `frontend-admin/src/types/system.ts`
- `frontend-admin/src/pages/system/roles/index.vue`
- `frontend-admin/src/pages/system/roles/__tests__/index.test.ts`
- `backend/src/main/java/com/cgcpms/system/service/SysRoleService.java`
- `backend/src/test/java/com/cgcpms/system/SysRoleControllerTest.java`
- `backend/src/test/java/com/cgcpms/system/SysRoleServiceTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-028-系统角色修改管理员入口与安全边界验收报告.md`
禁止修改：
- `backend/src/main/java/com/cgcpms/system/controller/**`
- `backend/src/main/java/com/cgcpms/system/entity/**`
- `backend/src/main/java/com/cgcpms/system/mapper/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/pages/system/permissions/**`
- `scripts/codex-autopilot/**`
- `plugins/cgc-pms-autopilot/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `deploy/**`
- `.github/**`
验收标准：
- 前端 API 测试精确断言 `PUT /system/roles/{id}` 只发送 roleName、status、dataScope；页面测试覆盖 ADMIN/SUPER_ADMIN 可见、普通用户不可见、系统/保留角色不可编辑、打开时回填白名单字段、必填/长度校验、失败保留、成功关闭并刷新。
- ADMIN/SUPER_ADMIN 或仅持 `system:role:edit` 的已认证请求可修改当前租户普通自定义角色；既非管理员也无该权限返回403，未登录返回401；跨租户、不存在、系统/保留/高等级角色均 fail-close 且无部分更新。
- 请求体中的 id、tenantId、roleCode、roleType、roleLevel 和等价提权字段不能覆盖服务端事实；合法更新只改变 roleName、status、dataScope，角色菜单集合、用户绑定、创建事实和审计边界不变。
- 空白/超长名称及非法状态、数据范围被拒绝且数据库不变；既有角色列表、新建、详情、删除、编辑权限、高危系统权限拒绝、自角色/系统角色保护和授权审计测试不回退。
- 收口必须引用 `docs/backlog/current-issues.json` 及该条目的 `sourceRefs`；全部验证通过后移除 `A-01-ROLE-UPDATE`，未完全通过则用证据更新其唯一状态或分类。通过时同步更新 A-01 守恒为有用户入口234、前端调用但无独立页面58、内部/集成/运维4、需补入口14、待废弃0、需要确认11、总数321，并回写 Ready、current-focus、project-map；若差距或优先级判断变化，再更新 evolution-decision。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；所有发现项必须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。权限、租户、字段白名单、菜单集合不变或运行态还原证据不足时判不通过并正式写入唯一阻塞载体。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint 和 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-028`
- `cd backend; .\mvnw.cmd "-Dtest=SysRoleControllerTest,SysRoleServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/system-modules.test.ts src/pages/system/roles/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/system.ts src/types/system.ts src/pages/system/roles/index.vue src/pages/system/roles/__tests__/index.test.ts`
- `git diff --check`

### ISSUE-040-027：系统角色删除管理员入口与安全边界

优先级：P0
任务性质：缺口修复
类型：系统管理 / 角色删除入口 / 权限 / 租户隔离 / 关联保护 / 破坏性操作
状态：Done（2026-07-15）
来源锚点：项目知识图谱当前问题 `A-01-ROLE-DELETE`；正式唯一问题载体为 `docs/backlog/current-issues.json`，其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；candidateEvidenceHead=0e4f793ff48be77416da2f8672348c12f3896167
存量问题键：[stock:A-01-ROLE-DELETE]
关联产品目标：在既有 admin-only 角色管理页让管理员可达已实现的角色删除能力，同时补齐当前 Service 对系统角色、用户绑定和角色菜单关系的 fail-close 边界，关闭 A-01 中 `DELETE /system/roles/{id}` 的最小叶子缺口。
核验结论：问题仍存在——后端 `SysRoleController.delete` 已由 ADMIN/SUPER_ADMIN 或 `system:role:delete` 保护，但前端系统 API 和角色页无删除调用或交互；当前 `SysRoleService.delete` 只校验当前租户，随后删除角色菜单并删除角色，未保护 SYSTEM/ADMIN/SUPER_ADMIN，且注入的 `SysUserRoleMapper` 未用于用户绑定门禁。用户价值与风险均明确，现有角色页、Controller、Service、Mapper 和前后端测试夹具可形成最小闭环。
候选对比：同级 P0 叶子还有 `A-01-ROLE-UPDATE`。知识图谱有界结果按叶子顺序首先返回删除项；删除入口虽风险更高，但当前后端已暴露可调用 DELETE 且缺少关键关联保护，优先补齐后端 fail-close 后再开放入口，可同时消除既有接口安全缺口。修改入口不作为本轮顺带范围。
检索交叉核验：CodeGraph 命中 `SysRoleController.delete`、`SysRoleService.delete`、`SysUserRoleMapper` 与角色管理相关测试，并显示 Service 的用户角色 Mapper 已注入但删除路径未使用；`codebase-memory-mcp` 补充确认删除调用链、用户角色关联实体和现有角色测试影响范围；最终以当前分支源码、唯一问题载体和正式验证为准。
阻塞证据：`DELETE /system/roles/{id}` 当前用户不可达；若直接补前端按钮，当前 Service 会删除系统角色，并在存在用户绑定时继续删除角色与菜单关系，可能破坏认证授权一致性。
解除条件：既有角色管理页提供受控删除入口；API 精确调用 DELETE；后端保留授权与租户 fail-close，拒绝系统/保留角色和任何仍绑定用户的角色；仅无用户绑定的当前租户自定义角色可删除，并清理其菜单关系；正负样本、现有创建/详情/菜单授权回归和破坏性确认交互全部通过。
Migration：不需要
依赖：复用现有 `/system/roles` 页面、`SysRoleVO`、系统 API request、`SysRoleController.delete`、`SysRoleService.delete`、`SysRoleMenuMapper`、`SysUserRoleMapper`、`UserContext` 和现有前后端测试夹具；不新增表、路由、侧栏、权限码或角色种子。
风险等级：高
运行态要求：自动化只在 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，任一失败先归类为环境前置并 runtime refresh，稳定等待 180 秒后复验。若做真实删除，只能创建并删除本 Issue 唯一测试角色；不得删除既有角色。清理/重置测试数据必须同时满足 dev/test/demo、数据库 host 为 localhost/127.0.0.1 和 `.codex-autopilot/ALLOW_TEST_DATA_RESET`，否则不得执行。
Reviewer要求：按高风险权限与数据一致性变更复核 ADMIN/SUPER_ADMIN 与 `system:role:delete` 授权未放宽、无权限 403、未登录 401、父路由 admin-only；复核跨租户统一 `ROLE_NOT_FOUND`，SYSTEM/ADMIN/SUPER_ADMIN 与角色等级保护，用户绑定门禁先于任何关联删除，成功路径只删除目标角色及其菜单关系；复核前端危险确认、失败不刷新/不伪报成功、成功刷新且不影响创建、详情、菜单授权，并对绑定 diff 给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-027-系统角色删除管理员入口与安全边界验收报告.md`
最小回滚：回退本 Issue 的前端删除 API、页面确认交互、Service 删除保护、测试与治理回写；不恢复或删除任何业务数据，不修改 schema，不回退既有角色创建、详情与菜单授权能力。
目标：
- 在前端系统 API 增加类型化 `deleteRole(roleId)`，精确调用无 body、无 params 的 `DELETE /system/roles/{id}`。
- 在既有 admin-only 角色管理页增加 ADMIN/SUPER_ADMIN 可见的“删除”危险操作；二次确认明确角色名称，取消不调用接口，失败保留列表并显示后端错误，成功刷新列表。
- 收敛后端删除路径：当前租户外或不存在目标统一 `ROLE_NOT_FOUND`；SYSTEM、ADMIN、SUPER_ADMIN 或等价受保护角色 fail-close；存在任一 `sys_user_role` 绑定时返回稳定业务错误且不删除任何关系；合法自定义未绑定角色才清理角色菜单并删除角色。
- 补齐 Controller/Service 与前端 API/页面正负样本，证明权限、租户、系统角色、用户绑定、事务原子性和既有角色能力不回退。
非目标：
- 不实现角色修改、批量删除、强制解绑、级联删除用户、回收在线会话、删除审计平台或完整角色 CRUD 重构。
- 不修改角色创建、详情、菜单授权接口语义，不新增数据库迁移、权限码、菜单、路由或默认授权。
- 不连接生产数据库、不发布生产、不 push，不删除任何既有开发数据。
允许修改：
- `frontend-admin/src/api/modules/system.ts`
- `frontend-admin/src/api/modules/__tests__/system-modules.test.ts`
- `frontend-admin/src/pages/system/roles/index.vue`
- `frontend-admin/src/pages/system/roles/__tests__/index.test.ts`
- `backend/src/main/java/com/cgcpms/system/service/SysRoleService.java`
- `backend/src/test/java/com/cgcpms/system/SysRoleControllerTest.java`
- `backend/src/test/java/com/cgcpms/system/SysRoleServiceTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/iterations/**`
- `docs/quality/ISSUE-040-027-系统角色删除管理员入口与安全边界验收报告.md`
禁止修改：
- `backend/src/main/java/com/cgcpms/system/controller/**`
- `backend/src/main/java/com/cgcpms/system/entity/**`
- `backend/src/main/java/com/cgcpms/system/mapper/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/pages/system/permissions/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 前端 API 测试精确断言 `DELETE /system/roles/{id}` 无 body、无 params；页面测试覆盖 ADMIN/SUPER_ADMIN 可见、普通用户即使持 `system:role:delete` 仍因 admin-only 页面不可见、确认/取消、失败不刷新且不伪报成功、成功刷新，以及 SYSTEM/ADMIN/SUPER_ADMIN 行不提供删除操作。
- 管理员或仅持 `system:role:delete` 的已认证请求可删除合格目标；既非管理员也无该权限返回 403，未登录返回 401。前端 admin-only 可见性与后端细粒度授权分别留证。
- 当前租户外或不存在目标返回 `ROLE_NOT_FOUND` 且无副作用；roleType=SYSTEM、roleCode=ADMIN/SUPER_ADMIN、roleLevel&lt;2 或其他等价受保护目标返回稳定错误且角色、用户绑定、菜单关系均不变。
- 目标存在任一用户绑定时返回 `ROLE_IN_USE`（或项目既有等价稳定错误码），角色及其菜单/用户关系均不变；合法当前租户 CUSTOM、roleLevel=2、无用户绑定目标删除成功，目标角色和角色菜单关系不可回读，不影响其他角色关系。
- 删除保护与关联清理在同一事务内；任一步失败整体回滚。既有角色列表、新建、详情、编辑权限、高危系统权限拒绝、自角色保护、超级管理员保护和授权审计测试不回退。
- 收口必须引用 `docs/backlog/current-issues.json` 及该条目的 `sourceRefs`；全部验证通过后移除 `A-01-ROLE-DELETE`，未完全通过则用证据更新其唯一状态或分类。通过时同步更新 A-01 守恒为有用户入口233、前端调用但无独立页面58、内部/集成/运维4、需补入口15、待废弃0、需要确认11、总数321，并回写 Ready、current-focus、project-map；若差距或优先级判断变化，再更新 evolution-decision。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；所有发现项必须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint 和 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-027`
- `cd backend; .\mvnw.cmd "-Dtest=SysRoleControllerTest,SysRoleServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/system-modules.test.ts src/pages/system/roles/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/system.ts src/pages/system/roles/index.vue src/pages/system/roles/__tests__/index.test.ts`
- `git diff --check`

执行收口（2026-07-15，desktop-native 金丝雀）：
- 既有 admin-only 角色页已增加 ADMIN/SUPER_ADMIN 可见的危险删除入口，精确调用无 body、无 params 的 `DELETE /system/roles/{id}`；系统/保留/高等级角色不提供前端删除项，删除前展示角色名称与不可逆二次确认。
- 后端保留 ADMIN/SUPER_ADMIN 或 `system:role:delete` 授权与租户 fail-close；SYSTEM、ADMIN、SUPER_ADMIN、roleLevel<2 和任何仍绑定用户的角色均在关联删除前拒绝，合法未绑定自定义角色才在同一事务中清理角色菜单并删除角色。
- Ready lint、后端48项、前端38项、类型检查、目标 ESLint 与差异检查通过；运行态切换至当前 worktree 后稳定观察180秒，真实浏览器完成唯一测试角色创建、确认删除、成功列表回读及 SUPER_ADMIN 无删除项验证，测试角色零残留，控制台无 error/warn。
- 主线程按高风险权限与数据一致性独立阶段完成结构化复核，对实现差异哈希 `06566e5890d1d5b7341f9295431c7fb010aa53fe09655ea0dc777e24b16f217d` 给出 `PASS`，findings=无；未派子智能体，未伪造独立 Reviewer 进程证据。
- `A-01-ROLE-DELETE` 已从 `docs/backlog/current-issues.json` 移除；A-01 守恒更新为有用户入口233、前端调用无独立页面58、内部4、需补入口15、需要确认11，总数321。
- 正式报告：`docs/quality/ISSUE-040-027-系统角色删除管理员入口与安全边界验收报告.md`；新增后续项1（`UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT`）、关闭后续项1（`A-01-ROLE-DELETE`）、后续项净变化0。新项为既有响应式布局缺陷，不阻断本轮宽屏桌面管理员入口，已写入唯一台账，产品候选排序不变。

### ISSUE-040-018：AutoPilot 存量问题优先补货与闭环门禁

优先级：P0
任务性质：运维治理 / 历史债消化控制面修复
类型：AutoPilot / Ready 补货 / 当前问题台账 / 去重 / 收口门禁
状态：Done（2026-07-13）
来源锚点：用户要求“自动迭代系统第一优先级从存量问题中拆任务”；`docs/backlog/current-issues.json` 已由 `ISSUE-040-017` 建立为当前问题唯一快照。
阻塞证据：原 refill 顺序为 Ready → 当前 focus 阻塞 → Ad-hoc → 长期增强计划，连续 runner 另有一套直接读取长期计划并生成宽泛 Ready 草稿的旧路径；系统不会优先消化结构化存量，也可能绕过现行 Candidate/Ready 决策门。
解除条件：Ready 为空时优先选择结构化台账中证据完整的可执行存量；排除阻塞、发布门禁、冻结、需要确认、聚合父项与证据不完整项；使用稳定来源标记去重；完成任务时强制同步消解或正式重分类原问题；长期增强计划不得直接拆 Ready。
风险等级：中（自动任务选择、治理事实一致性）
非目标：不自动启动迭代，不越过 `pause.flag` / `stop.flag`，不实施任何存量业务问题，不连接生产、不发布、不 push，不把发布门禁或需要确认项伪装成可执行任务。
验收结果：真实台账在单批上限5个时返回5个合格 A-01 叶子项，首项为 `A-01-MENU-CREATE`；该批无阻塞/冻结/需要确认/发布门禁或聚合父项混入。refill、连续 runner、closeout、控制面和插件级自测通过；当前 `pause.flag` 保持生效，因此未派发下一任务。
正式报告：`docs/quality/ISSUE-040-018-AutoPilot存量问题优先补货机制验收报告.md`。

### ISSUE-040-017：知识图谱当前问题结构化查询

优先级：P1
任务性质：运维治理 / 查询性能缺口修复
类型：知识图谱 / Neo4j / MCP / 当前问题台账 / 确定性采集
状态：Done（2026-07-13）
来源锚点：用户执行“查询所有既有问题”耗时3分29秒；第39条知识图谱当前只有 Artifact/Version/Section，没有归一化 Issue 节点。
阻塞证据：原查询必须通过宽泛全文搜索和文档二次推导状态，单次返回约91303 tokens并混入多个历史版本；图查询本身虽为毫秒级，但无法直接聚合当前问题。
解除条件：建立机器可读当前问题唯一快照；采集器确定性生成 Issue、PART_OF、SUPPORTED_BY；MCP 提供默认摘要的有界 `kg_list_issues`，不展开文档正文或历史版本。
风险等级：中（治理事实一致性与查询成本）
非目标：不做LLM事实抽取，不从任意质量报告猜测状态，不扫描私有区，不替代仓库、Git、Ready或正式报告。
验收结果：57个当前Issue、35条A-01父子关系、3条发布阻塞门禁全部入图；61条证据关系完整，私有Artifact 0、未解析引用0；多轮实测摘要低于50ms、57条全量明细低于125ms，MCP工具清单与21项测试通过。
正式报告：`docs/quality/ISSUE-040-017-知识图谱当前问题结构化查询验收报告.md`。

### ISSUE-040-012～016：A-01 财务核算五项接口入口治理

优先级：P0
任务性质：缺口修复 / 兼容性核实
类型：财务核算 / 前后端入口 / 权限 / 租户隔离 / 状态机 / MySQL/H2 Migration
状态：Done（2026-07-13）
来源锚点：第40条主线 A-01；`ISSUE-037-019` 的财务核算责任域五项子台账；当前项目地图和 `PI-2026-07-13-01`
关联产品目标：让财务人员可达既有会计凭证查询、详情、过账和冲销能力，同时不把尚无会计规则支撑的生成接口伪装成可用功能。
阻塞证据：列表、详情、过账、冲销只有后端 Controller，无前端 API/路由/菜单；`POST /accounting-entry/generate` 注入的 `EntryGenerationStrategy` 列表在生产代码中没有任何实现，任意来源均返回成功空值。
解除条件：四项真实能力进入同一最小页面，FINANCE 查询/编辑权限分离，状态与租户门禁通过；生成接口对不支持来源显式失败并转为“需要确认”，不授予 `accounting:add`。
Migration：V148，MySQL/H2 同号；新增菜单 960 和编辑按钮 961，仅对实际存在的 SUPER_ADMIN/ADMIN/FINANCE 角色授予查询与编辑，不开放生成权限；本地 MySQL 实测命中 SUPER_ADMIN、FINANCE（当前库无 ADMIN 角色）。
风险等级：高（金额、权限、租户和会计规则）
非目标：不编造借贷科目映射，不实现会计生成策略，不修改历史凭证或生产数据，不扩展为总账平台。
验收结果：后端 19 tests、前端 38 tests、类型检查、前端 lint、生产构建、SQL safety 和 `git diff --check` 通过；H2 与本地 MySQL 均已实际应用 V148，本地 MySQL 菜单及角色绑定只读复核通过；A-01 当前守恒为有用户入口 224、前端调用无独立页 58、内部 4、需补入口 24、需要确认 11，共 321。
正式报告：`docs/quality/ISSUE-040-012-016-A-01财务核算五项入口治理验收报告.md`。

### ISSUE-040-001：预警批量评估独立权限码修复

优先级：P0
任务性质：缺口修复
类型：权限 / 预警中心 / 高风险批处理 / MySQL/H2 Migration / 后端安全测试
状态：Done（2026-07-13）
来源锚点：第40条主线 V-01；v1.0 第10A主线对 `alert:edit` 高低风险动作共码的安全否决；当前 CodeGraph 与 `codebase-memory-mcp` 复核
关联产品目标：消除预警中心低风险单条处理与租户级批量评估共用授权码的历史债，保持最小权限和现有角色菜单语义。
阻塞证据：当前 `AlertController` 的单条/批量已读、状态处理和 `POST /alerts/batch-evaluate` 均使用 `alert:edit`；V40 的菜单 767“标记已读”和 768“批量评估”也均为 `alert:edit`。批量评估会遍历当前租户可访问活动项目并生成预警，风险显著高于标记已读。
解除条件：菜单 768 与 `batch-evaluate` 使用独立 `alert:evaluate`；只有 `alert:edit` 的非管理员请求被 403 拒绝，管理员和持有 `alert:evaluate` 的请求保持可用；既有角色—菜单关系不重写。
Migration：需要新增 V146，MySQL/H2 同号；禁止修改已应用 V40。
依赖：复用 `sys_menu` 768、既有 `sys_role_menu` 关系、Spring Method Security 和当前预警 Service；不新增表、角色或权限平台。
风险等级：高
运行态要求：先完成后端 H2 专项和迁移完整性验证；MySQL Flyway 仅在本地测试环境可用时复验，不连接生产。
Reviewer要求：复核权限拆分只影响批量评估，不放宽 `alert:edit`、管理员、租户/项目范围或角色授权；确认迁移可前向应用且不改历史 migration。
最小回滚：迁移应用前可回退本 Issue 文件；迁移应用后不得编辑 V146，应新增前向迁移恢复权限码，并同步回退 Controller 权限。
目标：
- 将 `POST /alerts/batch-evaluate` 从 `alert:edit` 收紧为 `alert:evaluate`。
- 用 V146 将菜单 768 的权限码改为 `alert:evaluate`，保留菜单 ID 和全部既有角色关系。
- 增加权限正负样本与迁移契约测试。
非目标：
- 不拆分标记已读与状态处理，不新增可编辑权限平台，不重构角色体系。
- 不新增预警页面按钮，不修改预警生成逻辑、租户/项目范围或业务数据。
- 不修改 V40/V42/V53 等已应用迁移，不连接生产数据库，不修改远端设置。
允许修改：
- `backend/src/main/java/com/cgcpms/alert/controller/AlertController.java`
- `backend/src/main/resources/db/migration/V146__split_alert_evaluate_permission.sql`
- `backend/src/main/resources/db/migration-h2/V146__split_alert_evaluate_permission.sql`
- `backend/src/test/java/com/cgcpms/alert/**`
- `docs/backlog/**`、`docs/plans/**`、`docs/product-intelligence/**`、`docs/quality/**`
禁止修改：
- 既有 migration
- `frontend-admin/**`
- 预警 Service、Entity、Mapper 与业务数据
- `deploy/**`、`.github/**`、生产凭据、生产数据库和仓库外文件
验收标准：
- `batchEvaluate` 的 `@PreAuthorize` 精确要求 `alert:evaluate` 或 ADMIN/SUPER_ADMIN。
- 仅持有 `alert:edit` 的普通角色请求返回 403；持有 `alert:evaluate` 或管理员请求返回 200。
- V146 只更新 `sys_menu.id=768` 且只在旧值为 `alert:edit` 时改为 `alert:evaluate`；菜单 767 和角色关系不变。
- MySQL/H2 迁移版本唯一，后端专项、迁移契约和 `git diff --check` 通过。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=AlertControllerTest,AlertPermissionMigrationTest,MigrationVersionUniquenessTest" test`
- `git diff --check`

执行结果：
- 修复前专项 21 tests 按预期出现 2 个失败，分别证明 Controller 仍使用 `alert:edit` 和 V146 尚不存在。
- 修复后专项 22 tests 全部通过；H2 实际迁移后菜单 767=`alert:edit`、768=`alert:evaluate`。
- 仅持有 `alert:edit` 的普通角色请求 `batch-evaluate` 返回 403；持有 `alert:evaluate` 或管理员返回 200。
- SQL safety 通过；MySQL smoke 因 `SPRING_DATASOURCE_URL` 未配置未执行，保留给远端 MySQL 门禁。
- 正式报告：`docs/quality/ISSUE-040-001-预警批量评估独立权限码修复验收报告.md`。

### ISSUE-037-022：Windows PowerShell 5.1 AutoPilot 脚本编码兼容修复

优先级：P0
任务性质：缺口修复
类型：AutoPilot / 控制面 / Windows PowerShell 5.1 / 编码兼容
状态：Done（2026-07-12；计入 `启动迭代-1` 第 1/1 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md` 的 Ready 准入控制面、`docs/product-intelligence/evolution-decision.md` 的 v1.5 AutoPilot 准入与正式验收边界
关联产品目标：恢复 v1.5 本地 AutoPilot 的合格 Ready 选单、实施、验证和收口控制面，使产品迭代仍受 stop/pause、Ready、A-F 与 no-push 门禁约束。
阻塞证据：在当前 `develop/1.5` HEAD 上，`powershell -File scripts/codex-autopilot/autopilot-run-continuous.ps1` 稳定产生 ParserError；脚本为 UTF-8 无 BOM，Windows PowerShell 5.1 按系统 ANSI/GBK 解码，显式 UTF-8 读入后的 AST 解析错误为 0，且工作区 hash 与 HEAD blob 一致。
解除条件：控制面及其 PowerShell 脚本在 Windows PowerShell 5.1 默认 `-File` 入口下均可解析；控制面自测、连续 runner 自测和 `git diff --check` 通过；本轮 runner 能继续选单或按规则补货。
Migration：不需要
依赖：仅使用仓库现有 PowerShell 自测与 AST 解析器；不新增工具、依赖、调度器或运行态服务。
风险等级：中
运行态要求：仅需 Windows PowerShell 5.1；不要求 Docker、backend、frontend、数据库、5173/8080、dev-login 或浏览器，不得修改业务运行态。
Reviewer要求：主线程必须复核修复仅改变脚本编码兼容与回归断言，不改变 stop/pause、Ready 解析、执行路由、业务代码或生产边界；输出直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-037-022-Windows-PowerShell-5.1-AutoPilot-脚本编码兼容修复验收报告.md`
最小回滚：回退本 Issue 对 PowerShell 脚本编码、控制面自测和状态文档的差异；无业务代码、数据、schema 或运行态恢复动作。
目标：
- 为 Windows PowerShell 5.1 `-File` 入口提供最小、可验证的 UTF-8 解析兼容。
- 用现有控制面自测固化所有 AutoPilot PowerShell 脚本的默认解析断言，避免中文内容再次使控制面失效。
非目标：
- 不修改业务接口、页面、权限、租户、审批、金额、数据库 migration 或产品功能。
- 不升级 PowerShell，不引入第二套 runner、编码转换器、调度器或外部依赖。
- 不处理 `ISSUE-037-021` 的 GitHub CI 红灯或分支保护问题。
允许修改：
- `scripts/codex-autopilot/*.ps1`
- `plugins/cgc-pms-autopilot/scripts/*.ps1`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/blocked-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/iterations/**`
- `docs/quality/ISSUE-037-022-Windows-PowerShell-5.1-AutoPilot-脚本编码兼容修复验收报告.md`
禁止修改：
- `backend/**`
- `frontend-admin/**`
- `deploy/**`
- `.github/workflows/**`
- `AGENTS.md`
- `AGENTS.override.md`
- 生产凭据、生产数据库连接、生产发布配置、仓库外文件
验收标准：
- `scripts/codex-autopilot/*.ps1` 在 Windows PowerShell 5.1 默认文件解码下 AST 解析错误为 0。
- `test-control-plane.ps1` 在修复前因脚本 ParserError 断言失败，修复后通过；相关连续 runner 自测通过。
- 实际控制面入口不再出现本轮 ParserError，并继续遵守 stop/pause/enabled、Ready 与 no-push 边界。
- 修改只收敛编码兼容、回归断言和正式状态文档；`git diff --check` 通过。
验证命令：
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-control-plane.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-continuous-runner.ps1`
- `git diff --check`

### ISSUE-037-017：BaseEntity 备注写入契约修复

优先级：P0
任务性质：缺口修复
类型：共享实体 / JSON 契约 / 数据正确性 / 后端测试
状态：Done（2026-07-12；计入 `启动迭代-10` 第 1/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-12-13`
Migration：不需要
依赖：复用现有 `BaseEntity` 与 Spring/Jackson 配置；不新增 DTO、依赖或兼容层。
风险等级：中
运行态要求：共享 JSON 契约专项通过；不要求 Docker、前端或真实浏览器。
Reviewer要求：复核 `remark` 可反序列化且可序列化，ID、租户、创建/更新人和时间仍保持只读；确认无 Controller、Service、前端或数据库扩散。
归档报告：`docs/quality/ISSUE-037-017-BaseEntity备注写入契约修复验收报告.md`
目标：
- 修复客户端提交 `remark` 时被 Jackson 静默忽略的问题。
- 保持共享实体其余受保护字段的反序列化边界不变。
非目标：
- 不修改业务 Controller、Service、Mapper、前端页面或数据库。
- 不开展 DTO 重构、实体直绑更新白名单审计或历史数据回填。
允许修改：
- `backend/src/main/java/com/cgcpms/common/entity/BaseEntity.java`
- `backend/src/test/java/com/cgcpms/common/entity/BaseEntityJsonContractTest.java`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `backend/src/main/java/com/cgcpms/**/controller/**`
- `backend/src/main/java/com/cgcpms/**/service/**`
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`、生产凭据、生产数据库连接、生产发布配置
验收标准：
- JSON 中的 `remark` 能反序列化到 `BaseEntity`，序列化仍返回备注。
- JSON 中的 `id`、`tenantId`、`createdBy`、`createdAt`、`updatedBy`、`updatedAt` 继续被反序列化忽略。
- 实现仅移除 `remark` 的只读标记，不改变其他共享字段、业务接口或数据库结构。
- 专项测试和 `git diff --check` 通过；回滚只恢复该标记并移除契约测试。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=BaseEntityJsonContractTest" test`
- `git diff --check`

### ISSUE-037-016：WBS 软删除编号冲突修复

优先级：P1
任务性质：缺口修复
类型：分包 WBS / 逻辑删除 / 唯一键 / 测试稳定性
状态：Done（2026-07-12；计入 `启动迭代-5` 第 3/5 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-12-11`
Migration：不需要
依赖：复用现有 `SubTaskService.delete()`、MyBatis Plus 逻辑删除和任务 ID。
风险等级：中
运行态要求：后端专项通过；不要求 Docker、前端或真实浏览器。
Reviewer要求：复核租户/项目访问、被引用任务禁止删除、墓碑编号唯一性、事务原子性和无跨表扩散。
归档报告：`docs/quality/ISSUE-037-016-WBS软删除编号冲突修复验收报告.md`
目标：
- 修复当天编号被历史软删除记录复用后，再次逻辑删除触发唯一键冲突的问题。
- 保持现有任务编号生成、引用保护、租户/项目权限和 API 语义。
非目标：
- 不新增 migration，不修改 `SUB-yyyyMMdd-XXX` 生成格式，不重构全局软删除。
- 不清理或批量改写历史数据，不修改其他业务表。
允许修改：
- `backend/src/main/java/com/cgcpms/subcontract/**`
- `backend/src/test/java/com/cgcpms/subcontract/**`
- `backend/src/test/java/com/cgcpms/TenantBoundaryTask2Test.java`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/**`
- `deploy/**`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- 删除前在同一事务内把 `task_code` 改为基于任务 ID 的唯一墓碑值，再执行既有逻辑删除；墓碑值长度不超过 64。
- 同日创建→删除→再次创建复用业务编号→再次删除全部成功，不出现 `DATA_CONFLICT`。
- 被后续任务引用时仍先返回 `SUB_TASK_DEPENDENCY_IN_USE`，不得提前改写编号。
- 不放宽租户/项目访问，不新增 migration 或其他表修改；失败必须整体回滚。
- 恢复 `ISSUE-037-015` 为规避 H2 冲突而移除的两处测试清理，并与新增回归共同通过。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SubTaskControllerTest,TenantBoundaryTask2Test" test`
- `git diff --check`

### ISSUE-037-015：WBS 单前置 FS 开工门禁

优先级：P1
任务性质：缺口修复
类型：分包 WBS / 单前置 FS / 状态门禁 / 项目数据范围 / 前后端 / 测试
状态：Done（2026-07-12；计入 `启动迭代-5` 第 2/5 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Odoo 19 Task Dependencies 官方事实；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-12-10`
Migration：不需要
依赖：复用现有 `SubTask.predecessorTaskId`、`SubTaskService.validateDependencyConsistency()`、同项目/租户/环/日期校验、前端前置任务选项和状态下拉。
风险等级：中
运行态要求：后端专项、前端分包任务单测和类型检查通过；不要求 Docker 或真实浏览器。
Reviewer要求：必须复核创建/更新/更换前置/清空前置、旧客户端省略字段兼容、租户/项目 fail-close、前置状态读取、状态绕过和前端仅做体验不替代后端门禁。
归档报告：`docs/quality/ISSUE-037-015-WBS单前置FS开工门禁验收报告.md`
目标：
- 将现有单前置 FS 从只读风险提示补成后端状态门禁。
- 有效前置任务未 `COMPLETED` 时，后续任务不得创建或更新为 `IN_PROGRESS` / `COMPLETED`。
- 前端选择未完成前置时禁用对应状态选项并显示最小提示，减少无效提交。
非目标：
- 不新增 WAITING 状态、多前置、依赖类型、lag、工作日历、自动排程、拖拽、关键路径或跨项目依赖。
- 不改变无前置任务、前置已完成任务、`NOT_STARTED` / `SUSPENDED` 的既有保存语义。
- 不修改权限、审批、日报、生产部署或数据库 migration。
允许修改：
- `backend/src/main/java/com/cgcpms/subcontract/**`
- `backend/src/test/java/com/cgcpms/subcontract/**`
- `backend/src/test/java/com/cgcpms/TenantBoundaryTask2Test.java`
- `frontend-admin/src/types/subcontract.ts`
- `frontend-admin/src/pages/subcontract/task.vue`
- `frontend-admin/src/pages/subcontract/__tests__/task.test.ts`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/java/com/cgcpms/site/**`
- `backend/src/main/java/com/cgcpms/workflow/**`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- 创建或更新任务时，若最终有效 `predecessorTaskId` 非空且前置状态不是 `COMPLETED`，最终状态为 `IN_PROGRESS` 或 `COMPLETED` 必须以稳定业务错误拒绝。
- 前置已完成、无前置、显式清空前置、最终状态为 `NOT_STARTED` 或 `SUSPENDED` 保持既有行为；旧客户端省略 `predecessorTaskId` 时沿用现有前置并正确门禁。
- 更换前置后按新前置状态判断；跨租户、跨项目、自依赖、间接环和 FS 日期校验继续 fail-close，不得被状态门禁绕过。
- 后端门禁位于统一 Service 校验路径，不能只靠 Controller 或前端；不得新增逐任务列表查询以外的无界扫描。
- 前端根据当前所选前置任务状态禁用 `IN_PROGRESS` / `COMPLETED` 并显示原因；服务端拒绝仍需显示错误，不新增状态或写入口。
- 至少覆盖创建、更新、省略字段、更换/清空前置、已完成/未完成前置、跨项目/环/日期既有回归和前端禁用提示；回滚为移除状态门禁与前端禁用，不涉及数据迁移。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SubTaskControllerTest,TenantBoundaryTask2Test" test`
- `cd frontend-admin; pnpm test:unit src/pages/subcontract/__tests__/task.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-014：现场日报当日已审批领料只读联动

优先级：P1
任务性质：能力新增
类型：现场生产 / 项目日报 / 领料出库 / 跨域只读聚合 / 项目数据范围 / 前后端 / 测试
状态：Done（2026-07-12；计入 `启动迭代-5` 第 1/5 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Procore Daily Log Productivity 与 Odoo 19 validated stock moves 官方事实；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-12-09`
Migration：不需要
依赖：复用现有 `site_daily_log`、`mat_requisition`、`mat_requisition_item`、物料主数据、`ProjectAccessChecker` 与日报详情 API；不新建日报材料表。
风险等级：中
运行态要求：后端专项、前端日报单测和类型检查通过；真实 API 或浏览器验收前执行 health gate，环境刷新后稳定等待 180 秒。
Reviewer要求：必须复核租户/项目/日期范围、仅 `APPROVED` 且 `stock_out_flag = 1`、批量查询、数量精度、敏感金额字段不出参、日报权限不放宽领料写能力及前端只读边界。
归档报告：`docs/quality/ISSUE-037-014-现场日报当日已审批领料只读联动验收报告.md`
目标：
- 在现场日报详情只读展示同租户、同项目、日报日期当天已审批且已真实出库的领料明细。
- 展示领料单号、物料、数量、单位和使用部位，复用既有审批出库事实，避免重复录入。
- 草稿与已提交日报均可查看；领料联动不参与日报提交、修改或附件状态机。
非目标：
- 不新增日报材料表，不从日报创建或修改领料单，不自动生成库存、成本或安装量动作。
- 不把领料数量解释为已安装或已消耗，不展示 DRAFT、APPROVING、REJECTED 或未出库领料单。
- 不返回单价、金额、合同、供应商，不修改领料审批、库存出库、成本生成、权限模型、migration 或生产部署。
允许修改：
- `backend/src/main/java/com/cgcpms/site/**`
- `backend/src/test/java/com/cgcpms/sitedaily/**`
- `frontend-admin/src/types/site-daily-log.ts`
- `frontend-admin/src/pages/site/**`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/java/com/cgcpms/requisition/**`
- `backend/src/main/java/com/cgcpms/inventory/**`
- `backend/src/main/java/com/cgcpms/cost/**`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- 日报详情只聚合同租户、同项目、`requisition_date = report_date`、`approval_status = APPROVED` 且 `stock_out_flag = 1` 的领料单；跨租户、跨项目、其他日期、其他状态或未出库记录不得出现。
- 每条明细只返回领料单 ID/编号、明细 ID、物料 ID/名称/单位、领料数量和使用部位；数量保持数据库精度，不转为浮点数，不返回单价、金额、合同或供应商。
- 日报不存在或无项目访问权继续沿用既有 fail-close；联动查询发生在项目访问校验之后，不新增 `requisition:query` 或领料写权限。
- 查询使用批量领料单、明细和物料读取，禁止按明细逐条查询；无命中返回空列表。
- 前端详情以只读区域展示领料明细和空态，不提供新增、编辑、删除、提交审批、库存或成本写入口，文案不得声称已安装。
- 至少覆盖 APPROVED/非 APPROVED、出库标记、租户/项目/日期隔离、空列表、数量字符串、敏感字段不出现和前端只读渲染；回滚为移除聚合字段和展示区，不涉及数据迁移。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SiteDailyLogServiceTest,SiteDailyLogControllerTest" test`
- `cd frontend-admin; pnpm test:unit src/pages/site/__tests__/daily-log.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-013：现场日报变更历史只读展示

优先级：P1
任务性质：缺口修复
类型：现场生产 / 项目日报 / 操作审计 / 变更历史 / 租户边界 / 前后端 / 测试
状态：Done（2026-07-12；计入 `启动迭代-3` 第 3/3 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Procore Daily Log Change History 官方事实；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-12-08`
Migration：不需要
依赖：复用现有 `@AuditedOperation`、`sys_operation_audit_log`、日报详情 API 与项目访问校验；不新建历史表。
风险等级：中
运行态要求：后端专项、前端日报单测和类型检查通过；真实 API 或浏览器验收前执行 health gate，环境刷新后稳定等待 180 秒。
Reviewer要求：必须复核 CREATE businessId 绑定、租户/businessType/businessId 精确过滤、失败事件可见性、敏感字段不出参、用户 ID 最小展示和无审计写路径新增。
归档报告：`docs/quality/ISSUE-037-013-现场日报变更历史只读展示验收报告.md`
目标：
- 修正日报 CREATE 审计记录缺少 businessId，使新建、修改、提交都能归属具体日报。
- 在日报详情只读展示操作类型、用户 ID、成功/失败和操作时间，形成最小变更历史。
- 复用统一操作审计表，不创建日报专用历史副本。
非目标：
- 不做字段级前后值 diff、版本恢复、审计导出、审批轨迹或用户快照。
- 不向前端返回 sourceIp、requestPath、errorCode、durationMs 等安全/运维字段。
- 不修改统一审计表结构、异步写入机制、权限模型或生产部署。
允许修改：
- `backend/src/main/java/com/cgcpms/site/**`
- `backend/src/test/java/com/cgcpms/sitedaily/**`
- `frontend-admin/src/types/site-daily-log.ts`
- `frontend-admin/src/pages/site/**`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/java/com/cgcpms/audit/**`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- 日报 CREATE 的 `@AuditedOperation` 使用 `businessIdExpression = "#log.id"`，插入成功后记录具体日报 ID；UPDATE/SUBMIT 既有表达式保持不变。
- 详情只查询当前 tenant、`business_type = SITE_DAILY_LOG`、`business_id = 日报ID` 的审计记录，并按创建时间倒序。
- 返回 operationType、userId、successFlag、createdAt；不返回 IP、路径、错误码、耗时或其他审计内部字段。
- 日报不存在或无项目访问权继续 fail-close；审计查询必须发生在项目访问校验之后。
- 前端详情只读展示动作、用户 ID、结果和时间；无记录显示空态，不提供删除、重放、恢复或导出。
- 至少覆盖 CREATE 表达式、租户/业务类型/业务 ID 过滤、成功/失败映射、空列表和前端敏感字段不出现；回滚为移除详情历史与 CREATE 表达式。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SiteDailyLogServiceTest,SiteDailyLogControllerTest" test`
- `cd frontend-admin; pnpm test:unit src/pages/site/__tests__/daily-log.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-012：现场日报当日计划任务只读联动

优先级：P1
任务性质：能力新增
类型：现场生产 / 项目日报 / 分包 WBS / 跨域只读聚合 / 项目数据范围 / 前后端 / 测试
状态：Done（2026-07-12；计入 `启动迭代-3` 第 2/3 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Procore Daily Log Scheduled Work 官方事实；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-12-07`
Migration：不需要
依赖：复用现有 `site_daily_log`、`sub_task` 计划日期/状态/进度、`ProjectAccessChecker` 与日报详情 API；不新建日报计划表。
风险等级：中
运行态要求：后端专项、前端日报单测和类型检查通过；真实 API 或浏览器验收前执行 health gate，环境刷新后稳定等待 180 秒。
Reviewer要求：必须复核租户/项目/日期范围、计划日期闭区间、空日期排除、批量查询、最小字段披露、日报权限不放宽写能力及前端只读边界。
归档报告：`docs/quality/ISSUE-037-012-现场日报当日计划任务只读联动验收报告.md`
目标：
- 在日报详情只读展示计划日期区间覆盖日报日期的同项目分包 WBS 任务。
- 展示任务编号、名称、作业区域、计划开始/结束、状态和进度，帮助现场日报对照当天计划。
- 复用既有任务事实，不从日报创建、修改或完成任务。
非目标：
- 不新增日报计划/排程表，不实现拖拽排程、自动改期、关键路径、多前置或资源负载。
- 不展示合同金额、供应商敏感信息或任务审批，不替代分包任务页面。
- 不修改分包任务写接口、权限、migration、生产部署。
允许修改：
- `backend/src/main/java/com/cgcpms/site/**`
- `backend/src/test/java/com/cgcpms/sitedaily/**`
- `frontend-admin/src/types/site-daily-log.ts`
- `frontend-admin/src/pages/site/**`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/java/com/cgcpms/subcontract/**`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- 只返回同租户、同项目，且 `planned_start_date <= report_date <= planned_end_date` 的任务；任一计划日期为空、跨租户、跨项目或区间不覆盖时不得出现。
- 返回任务 ID/编号/名称、作业区域、计划开始/结束、状态和字符串进度；不返回合同金额、供应商信息或写操作能力。
- 日报不存在或无项目访问权继续 fail-close；联动查询复用 `site:daily:query` 的项目语境，不新增任务写权限。
- 后端单次批量查询并按计划开始/任务编号排序；无命中返回空列表。
- 前端详情只读展示计划任务和空态，不提供新建、编辑、删除、进度更新或跳转写入口。
- 至少覆盖日期闭区间、空日期排除、租户/项目隔离、空列表、进度字符串和前端只读渲染；回滚为移除聚合字段与展示区。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SiteDailyLogServiceTest,SiteDailyLogControllerTest" test`
- `cd frontend-admin; pnpm test:unit src/pages/site/__tests__/daily-log.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-011：现场日报已审批材料到货只读联动

优先级：P1
任务性质：能力新增
类型：现场生产 / 项目日报 / 材料验收 / 跨域只读聚合 / 项目数据范围 / 前后端 / 测试
状态：Done（2026-07-12；计入 `启动迭代-3` 第 1/3 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Procore Daily Log Deliveries/Productivity 官方事实；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-12-06`；`docs/backlog/ad-hoc-plan.md` 的“现场日报已审批材料到货只读联动”候选
Migration：不需要
依赖：复用现有 `site_daily_log`、`mat_receipt`、`mat_receipt_item`、物料/供应商主数据、`ProjectAccessChecker` 与日报详情页；不新建日报材料表。
风险等级：中
运行态要求：后端专项、前端日报单测和类型检查通过；真实 API 或浏览器验收前执行 health gate，环境刷新后稳定等待 180 秒。
Reviewer要求：实现后必须复核租户/项目/日期范围、仅 `APPROVED` 验收单、批量查询、数量精度、空列表、日报权限和前端只读边界；证据不足不得通过。
归档报告：`docs/quality/ISSUE-037-011-现场日报已审批材料到货只读联动验收报告.md`
目标：
- 在现场日报详情中只读展示同租户、同项目、日报日期当天已审批的材料验收到货明细。
- 展示验收单号、供应商、物料、实收数量和合格数量，复用既有验收事实，避免重复录入。
- 草稿与已提交日报均可查看；到货联动不参与日报提交、修改或附件状态机。
非目标：
- 不新增日报材料/配送表，不从日报创建或修改验收单，不自动生成采购、库存或成本动作。
- 不展示 DRAFT、APPROVING、REJECTED 验收单，不扩展到设备、材料消耗、安装量或生产率。
- 不修改验收审批、库存入库、成本生成、权限模型、生产部署或历史 migration。
允许修改：
- `backend/src/main/java/com/cgcpms/site/**`
- `backend/src/test/java/com/cgcpms/sitedaily/**`
- `frontend-admin/src/types/site-daily-log.ts`
- `frontend-admin/src/api/modules/site-daily-log.ts`
- `frontend-admin/src/pages/site/**`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/java/com/cgcpms/receipt/**`
- `backend/src/main/java/com/cgcpms/inventory/**`
- `backend/src/main/java/com/cgcpms/cost/**`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- 日报详情只聚合同租户、同项目、`receipt_date = report_date` 且 `approval_status = APPROVED` 的验收单；跨租户、跨项目和其他日期记录不得出现。
- 每条到货明细返回验收单 ID/编号、供应商名称、物料 ID/名称、实收数量和合格数量；数量保持数据库精度，不转为浮点数。
- 日报不存在或无项目访问权继续沿用既有 fail-close；联动查询不得新增 `receipt:query` 要求或绕过 `site:daily:query`。
- 查询使用批量 receipt/item/物料/供应商读取，禁止按明细逐条查询；无命中返回空列表。
- 前端详情以只读区域展示到货明细和空态，不提供新增、编辑、删除、跳过审批或库存写入口。
- 至少覆盖 APPROVED/非 APPROVED、租户/项目/日期隔离、空列表、数量字符串和前端只读渲染；回滚为移除聚合字段和展示区，不涉及数据迁移。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SiteDailyLogServiceTest,SiteDailyLogControllerTest" test`
- `cd frontend-admin; pnpm test:unit src/pages/site/__tests__/daily-log.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

## v1.5 准入要求

每条 Ready Issue 必须包含：编号、任务性质、目标、范围、非目标、验收标准、真实存在的验证命令、风险与回滚方式、归档报告路径。

只有状态为 `Ready` 且字段完整的任务才能进入 AutoPilot 实施；候选项不能直接执行。

### ISSUE-037-005：现场日报最小闭环

优先级：P1
任务性质：能力新增
类型：现场生产 / 项目日报 / 状态流转 / 附件 / 项目数据范围 / 前后端 / Migration / 测试
状态：Done（2026-07-12；计入本轮第 5/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Procore Daily Log 官方事实；`docs/product-intelligence/evolution-decision.md`；`docs/backlog/ad-hoc-plan.md` 的“现场日报 / 施工日志”候选
Migration：需要
依赖：复用现有 `ProjectAccessChecker`、`sys_file`/MinIO 文件链和项目列表；不接入通用审批流。
风险等级：中
运行态要求：后端专项、前端单测/路由/类型检查通过；浏览器或真实 API 验收前执行 health gate，环境刷新后稳定等待 180 秒。
Reviewer要求：实现完成后必须独立复核同项目同日唯一、草稿/提交不可逆边界、项目数据范围、附件对象授权和跨租户引用；证据不足不得通过。
归档报告：`docs/quality/ISSUE-037-005-现场日报最小闭环验收报告.md`
目标：
- 建立按项目和日期归档的现场日报事实对象，记录当日施工内容、问题/延误和次日计划。
- 提供草稿创建/修改、提交后只读的最小状态闭环，并在现有前端增加可达的列表与表单入口。
- 复用现有文件能力，以 `SITE_DAILY_LOG` 业务类型关联日报附件，读写都执行租户和项目范围校验。
非目标：
- 不实现移动端、离线、天气接口、定位、语音、人员/班组/设备/材料结构化子表、质量安全巡检、RFI/Submittal 或日报统计驾驶舱。
- 不接入通用审批模板，不新增驳回/撤回/重新提交；提交只表示日报定稿。
- 不重构文件平台、项目权限模型、路由框架或生产部署配置。
允许修改：
- `backend/src/main/resources/db/migration/V141__create_site_daily_log.sql`
- `backend/src/main/resources/db/migration-h2/V141__create_site_daily_log.sql`
- `backend/src/main/java/com/cgcpms/site/**`
- `backend/src/main/java/com/cgcpms/file/auth/BusinessObjectAuthorizer.java`
- `backend/src/main/java/com/cgcpms/file/controller/FileController.java`
- `backend/src/test/java/com/cgcpms/TenantBoundaryTask2Test.java`
- `backend/src/test/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/sitedaily/**`
- `frontend-admin/src/api/modules/site-daily-log.ts`
- `frontend-admin/src/types/site-daily-log.ts`
- `frontend-admin/src/pages/site/**`
- `frontend-admin/src/router/index.ts`
- `frontend-admin/src/router/navigation.ts`
- `frontend-admin/src/router/__tests__/router.test.ts`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- 已应用的 `backend/src/main/resources/db/migration/V1__*.sql` 至 `V140__*.sql`
- `backend/src/main/java/com/cgcpms/workflow/**`
- 生产凭据、生产数据库连接、生产发布配置
- 新增天气、人员、设备、材料、质量、安全、定位或离线同步表
验收标准：
- `site_daily_log` 按当前租户、项目、日报日期保持有效记录唯一；字段最小限定为施工内容、问题/延误、次日计划、状态、提交人/时间和审计字段。
- 列表、详情、创建、修改、提交全部复用 `ProjectAccessChecker`；跨租户项目、同租户无项目访问权和伪造 projectId 均 fail-close。
- 只有 `DRAFT` 可修改和增删附件；日报本体不提供删除接口。`SUBMITTED` 正文只读且提交不可重复，不接入审批流。
- `SITE_DAILY_LOG` 注册进文件业务对象授权；读取/列表/下载要求 `site:daily:query` 并允许读取 `SUBMITTED`，上传/删除附件要求 `site:daily:edit` 且只允许 `DRAFT`；对象不存在、跨租户/跨项目或无项目访问权时 fail-close。
- `FileController` 与 `BusinessObjectAuthorizer` 使用同一权限口径：日报附件读为 `site:daily:query`，附件上传/删除为 `site:daily:edit`，不得要求现场角色持有泛化 `file:*` 权限。
- 前端提供项目、日期、状态筛选以及 loading/empty/error；能创建/编辑草稿、查看详情、提交定稿，并对提交态隐藏写入口。
- 前端附件复用现有上传、列表、下载、删除 API；不新建第二套文件存储或附件表。
- 迁移提供 MySQL/H2 镜像、唯一约束与项目/日期查询索引；回滚边界为回退应用后删除新表和文件业务类型注册，不改写历史 migration。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SiteDailyLogControllerTest,SiteDailyLogServiceTest,TenantBoundaryTask2Test,BusinessObjectAuthorizerTest" test`
- `cd frontend-admin; pnpm test:unit src/pages/site/__tests__/daily-log.test.ts src/router/__tests__/router.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-009：库存项人工补货目标量联动

优先级：P1
任务性质：能力新增
类型：库存 / 采购补货 / 配置关系 / 项目数据范围 / 前后端 / Migration / 测试
状态：Done（2026-07-12；计入本轮第 9/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Odoo Inventory Reordering Rules 19 最小/最大库存事实；`docs/product-intelligence/evolution-decision.md`；`docs/backlog/ad-hoc-plan.md` 的“人工补货目标量”候选
Migration：需要
依赖：复用 `ISSUE-037-006` 的安全库存阈值、`inventory:stock:edit`、stock→warehouse→project 范围校验和采购申请预填链；不新增规则表。
风险等级：中
运行态要求：后端专项、前端单测和类型检查通过；真实 API 前执行 health gate，环境刷新后稳定等待 180 秒。
Reviewer要求：实现后必须独立复核 NULL 回退、目标量与安全阈值原子关系、旧接口兼容、项目/租户/权限、乐观锁、KPI 不变和四位小数建议数量；证据不足不得通过。
归档报告：`docs/quality/ISSUE-037-009-库存项人工补货目标量联动验收报告.md`
目标：
- 为仓库+物料库存项增加可选人工补货目标量；NULL 表示未单独配置，建议数量继续回退到安全库存阈值。
- 提供一次原子保存安全阈值和目标量的设置接口，始终保持非空目标量大于等于安全阈值。
- 低库存触发后按“目标量（或安全阈值）-当前可用量”生成四位小数采购申请预填数量。
非目标：
- 目标量不是库存硬上限，不阻止超量入库，不建设最大库存控制或库存容量约束。
- 不实现供货周期、需求预测、历史消耗模型、自动下单、供应商选择、跨仓调拨或全量补货工作台。
- 不修改采购申请审批、KPI 低库存触发口径、权限模型或生产部署。
允许修改：
- `backend/src/main/resources/db/migration/V144__add_stock_replenishment_target.sql`
- `backend/src/main/resources/db/migration-h2/V144__add_stock_replenishment_target.sql`
- `backend/src/main/java/com/cgcpms/inventory/**`
- `backend/src/test/java/com/cgcpms/inventory/**`
- `frontend-admin/src/api/modules/inventory.ts`
- `frontend-admin/src/types/inventory.ts`
- `frontend-admin/src/pages/inventory/**`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- 已应用的 `backend/src/main/resources/db/migration/V1__*.sql` 至 `V143__*.sql`
- `backend/src/main/java/com/cgcpms/purchase/**`
- `frontend-admin/src/pages/inventory/purchase-request.vue`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- V144 MySQL/H2 仅增加 `replenishment_target_qty DECIMAL(18,4) NULL`，不回填旧数据；首次入库保持 NULL。
- 新设置接口一次接收安全阈值与可空目标量；两者非负、最多 4 位小数，目标量非空时必须大于等于安全阈值，使用现有 `@Version` 原子更新。
- 原有 `PUT /inventory/stock/{id}/safety-threshold` 保持兼容；若已有目标量，新安全阈值不得高于目标量。
- 设置接口继续使用 `inventory:stock:edit`，复用租户、启用仓库和 `ProjectAccessChecker`；跨租户、无项目范围、禁用仓库或伪造 stockId fail-close。
- KPI 低库存仍为 `availableQty > 0 AND availableQty < safetyStockQty`，目标量不参与触发；VO 返回 `replenishmentTargetQty`。
- 前端一次维护安全阈值和可空人工补货目标量，明确“未填则补到安全阈值”；保存后刷新库存/KPI。
- 补货建议只在低库存时产生，数量按 `(replenishmentTargetQty ?? safetyStockQty) - availableQty` 固定四位小数；NULL 回退与 0/非空值语义有测试。
- 至少覆盖迁移兼容、关系校验、NULL 回退、旧接口兼容、权限/项目范围、KPI 不变和前端建议数量；回滚为移除 V144 列和新设置接口，不改写 V142。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=MatStockServiceTest,MatStockControllerTest" test`
- `cd frontend-admin; pnpm test:unit src/pages/inventory/__tests__/stock-production.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-010：库存项人工补货提前期与计划日期预填

优先级：P1
任务性质：能力新增
类型：库存 / 采购补货 / 时间计划 / 项目数据范围 / 前后端 / Migration / 测试
状态：Done（2026-07-12；计入本轮第 10/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Odoo 19 Lead Times 与 Replenishment Report 官方事实；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-12-05`；`docs/backlog/ad-hoc-plan.md` 的“库存项人工补货提前期”候选
Migration：需要
依赖：复用 `ISSUE-037-009` 的库存组合设置、`inventory:stock:edit`、stock→warehouse→project 范围校验、补货路由预填链及采购申请明细现有 `plannedDate`；不修改采购后端。
风险等级：中
运行态要求：后端专项、库存/采购申请前端专项和类型检查通过；真实 API 前执行 health gate，环境刷新后稳定等待 180 秒。
Reviewer要求：实现后必须独立复核 NULL/0 语义、整数边界、本地自然日和 `YYYY-MM-DD` 公式、旧设置兼容、项目/租户/权限、乐观锁及采购页面 query 清理；证据不足不得通过。
归档报告：`docs/quality/ISSUE-037-010-库存项人工补货提前期与计划日期预填验收报告.md`
目标：
- 为仓库+物料库存项增加可空人工补货提前期（自然日）；NULL 保持旧行为不预填日期，0 表示当前本地日期。
- 将提前期纳入现有补货组合设置与 VO，继续使用同一库存项乐观锁和权限范围。
- 从低库存发起补货时，按“当前本地日期 + 提前期自然日”生成 `YYYY-MM-DD`，预填采购申请明细计划日期。
非目标：
- 不建设供应商级提前期/价目表，不声称为预计到货承诺或供应商交期预测。
- 不处理工作日历、节假日、时区服务、采购确认时长、历史交付学习、自动下单或全量补货报告。
- 不修改采购申请后端实体、API、审批状态机、权限模型或生产部署。
允许修改：
- `backend/src/main/resources/db/migration/V145__add_stock_replenishment_lead_days.sql`
- `backend/src/main/resources/db/migration-h2/V145__add_stock_replenishment_lead_days.sql`
- `backend/src/main/java/com/cgcpms/inventory/**`
- `backend/src/test/java/com/cgcpms/inventory/**`
- `frontend-admin/src/api/modules/inventory.ts`
- `frontend-admin/src/types/inventory.ts`
- `frontend-admin/src/pages/inventory/stock.vue`
- `frontend-admin/src/pages/inventory/composables/useStockLedger.ts`
- `frontend-admin/src/pages/inventory/purchase-request.vue`
- `frontend-admin/src/pages/inventory/__tests__/stock-production.test.ts`
- `frontend-admin/src/pages/inventory/__tests__/purchase-request.test.ts`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- 已应用的 `backend/src/main/resources/db/migration/V1__*.sql` 至 `V144__*.sql`
- `backend/src/main/java/com/cgcpms/purchase/**`
- 采购申请后端 DTO、Service、Controller、Entity 与数据库表
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- V145 MySQL/H2 仅增加 `replenishment_lead_days INT NULL`，不回填；首次入库保持 NULL。
- 组合设置接口接收可空整数 `replenishmentLeadDays`，只允许 0 至 3650；负数、超上限和小数请求 fail-close，并与安全阈值/目标量同次 `@Version` 更新。
- NULL 表示补货跳转不携带 `plannedDate`；0 表示当前本地日期；正数按当前本地日期加自然日，格式固定 `YYYY-MM-DD`，不做工作日调整。
- 采购申请预填只接受严格 `YYYY-MM-DD` 且可还原为同一日期的 query；无效日期提示并不写入，成功或失败后都清理 `plannedDate` 与既有补货 query。
- 设置接口继续使用 `inventory:stock:edit` 和现有租户/启用仓库/项目范围；旧安全阈值接口保持兼容，KPI 与补货数量公式不变。
- 至少覆盖迁移、NULL/0/正数、整数边界、日期跨月/年、无效 query、旧设置兼容、权限范围和前端 query 清理；回滚为移除 V145 列和前端日期预填，不改写历史 migration。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=MatStockServiceTest,MatStockControllerTest" test`
- `cd frontend-admin; pnpm test:unit src/pages/inventory/__tests__/stock-production.test.ts src/pages/inventory/__tests__/purchase-request.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-008：现场日报 dev-login 直达路由白名单修复

优先级：P1
任务性质：缺口修复
类型：本地验收 / dev-login / 路由安全 / 回归测试
状态：Done（2026-07-12；计入本轮第 8/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md` 的现场日报可达性事实；`docs/product-intelligence/evolution-decision.md` 的 ISSUE-037-007 运行态回写；`docs/backlog/current-focus.md` 的 `/site` 非阻塞观察；`docs/backlog/ad-hoc-plan.md` 的直达路由候选
Migration：不需要
依赖：复用现有 `DevAuthController.normalizeRedirect()` 站内前缀白名单和前端 `/site/daily-log` 路由；仅 dev/local 生效。
风险等级：低
运行态要求：AuthController 专项通过；真实 dev-login 必须保留 302 Location `/site/daily-log`，前端跟随跳转 200。
Reviewer要求：实现后必须独立复核只新增 `/site` 站内前缀，`//`、站外 URL、`..` 路径遍历仍回落 `/`，且 dev-login 仍只在 dev/local 启用。
归档报告：`docs/quality/ISSUE-037-008-现场日报dev-login直达路由验收报告.md`
目标：
- 让本地 dev-login 的 `redirect=/site/daily-log` 保留目标路径，支持现场日报真实角色验收直达。
- 通过现有安全归一化逻辑添加最小 `/site` 站内前缀，不改变 cookie、登录或权限行为。
非目标：
- 不放宽任意 URL、协议相对 URL、站外域名或路径遍历，不改为通配符/正则全放行。
- 不修改正式登录、生产 profile、前端路由、现场日报业务、权限、部署或代理配置。
- 不为其他未核验路由批量补白名单。
允许修改：
- `backend/src/main/java/com/cgcpms/auth/controller/DevAuthController.java`
- `backend/src/test/java/com/cgcpms/auth/controller/AuthControllerTest.java`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- `backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java`
- `backend/src/main/java/com/cgcpms/auth/service/**`
- `frontend-admin/**`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- `redirect=/site/daily-log` 返回 302，Location 精确为 `/site/daily-log`，并继续设置现有访问/刷新 cookie。
- `redirect=//evil.example`、完整站外 URL、含 `..` 的 `/site/../system` 仍安全回落 `/`；不得因新增前缀绕过校验。
- `@Profile({"dev", "local"})` 和 `auth.dev-login.enabled` 条件不变，生产仍不启用该入口。
- 只修改一个白名单前缀及对应测试/归档；回滚为移除 `/site` 前缀，不涉及数据迁移。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=AuthControllerTest" test`
- `git diff --check`

### ISSUE-037-007：现场日报天气摘要与在场人数补强

优先级：P1
任务性质：能力新增
类型：现场生产 / 项目日报 / 状态边界 / 前后端 / Migration / 测试
状态：Done（2026-07-12；计入本轮第 7/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Procore Daily Log 官方事实；`docs/product-intelligence/evolution-decision.md`；`docs/backlog/ad-hoc-plan.md` 的“现场日报天气摘要与在场人数”候选
Migration：需要
依赖：复用现有 `site_daily_log`、`SiteDailyLogService` 的项目范围与 DRAFT→SUBMITTED 状态闭环、现有页面和权限；不新增子表。
风险等级：低
运行态要求：后端专项、前端单测和类型检查通过；真实 API 验收前执行 health gate，环境刷新后稳定等待 180 秒。
Reviewer要求：实现完成后必须独立复核 NULL/0 人数语义、整数边界、天气长度、草稿原子更新、提交后不可变、项目/租户范围和旧数据兼容；证据不足不得通过。
归档报告：`docs/quality/ISSUE-037-007-现场日报天气摘要与在场人数补强验收报告.md`
目标：
- 在现有现场日报草稿中增加人工天气摘要与可选在场人数，补足现场日报最小事实维度。
- `onSiteHeadcount=NULL` 表示未填写，`0` 表示明确无人；已提交日报继续保持正文和新增字段只读。
- 在现有创建/编辑/查看表单中维护和展示两字段，不新增入口、权限或工作流。
非目标：
- 不接天气 API，不自动采集天气，不建设天气枚举、预报、定位或历史气象服务。
- 不建立人员名单、班组、考勤、工时、劳务实名制、设备、材料、质量安全子表或统计驾驶舱。
- 不实现移动端、离线同步，不修改附件授权、日报唯一约束、提交状态机或通用审批。
允许修改：
- `backend/src/main/resources/db/migration/V143__add_site_daily_weather_headcount.sql`
- `backend/src/main/resources/db/migration-h2/V143__add_site_daily_weather_headcount.sql`
- `backend/src/main/java/com/cgcpms/site/**`
- `backend/src/test/java/com/cgcpms/sitedaily/**`
- `frontend-admin/src/types/site-daily-log.ts`
- `frontend-admin/src/pages/site/**`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- 已应用的 `backend/src/main/resources/db/migration/V1__*.sql` 至 `V142__*.sql`
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/main/java/com/cgcpms/workflow/**`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- V143 MySQL/H2 镜像只为 `site_daily_log` 增加 `weather_summary VARCHAR(200) NULL` 与 `on_site_headcount INT NULL`；旧数据升级后保持 NULL，不回填伪造事实。
- 天气摘要可空且最多 200 字；在场人数可空，只接受 0 至 100000 的整数，负数、超上限或小数请求 fail-close。
- 创建和修改均保存两字段；修改继续使用现有 `tenant_id + id + status=DRAFT` 原子条件，SUBMITTED 状态不得修改新增字段。
- 列表/详情返回两字段并继续执行既有项目/租户范围；不新增或放宽 `site:daily:query/edit` 权限。
- 前端创建、编辑、查看表单显示人工天气摘要和在场人数；未填写人数显示“未填写”，0 显示“0”，不把两者混同。
- 至少覆盖字段创建/更新/映射、NULL/0、长度/整数边界、提交后只读和前端表单测试；回滚边界为回退应用后移除 V143 两列，不改写 V141。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SiteDailyLogControllerTest,SiteDailyLogServiceTest" test`
- `cd frontend-admin; pnpm test:unit src/pages/site/__tests__/daily-log.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-006：库存项安全库存阈值与补货建议联动

优先级：P1
任务性质：能力新增
类型：库存 / 采购补货 / 项目数据范围 / 权限 / 前后端 / Migration / 测试
状态：Done（2026-07-12；计入本轮第 6/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/product-intelligence/project-map.md`；`docs/product-intelligence/competitor-analysis.md` 的 Odoo Inventory Reordering Rules 19 官方事实；`docs/product-intelligence/evolution-decision.md`；`docs/backlog/ad-hoc-plan.md` 的“安全库存阈值配置”候选
Migration：需要
依赖：复用现有 `mat_stock`、库存台账、项目归属仓库、采购申请补货预填链、`ProjectAccessChecker` 和正式 `PURCHASE_MANAGER` 角色；不新增补货建议表。
风险等级：中
运行态要求：后端专项、前端单测与类型检查通过；真实 API 验收前执行 health gate，环境刷新后稳定等待 180 秒。
Reviewer要求：实现完成后必须独立复核租户/项目数据范围、阈值精度和非负边界、零库存既有口径、动态 KPI 与补货数量的一致性，以及新增权限最小授权；证据不足不得通过。
归档报告：`docs/quality/ISSUE-037-006-库存项安全库存阈值与补货建议联动验收报告.md`
目标：
- 为每个租户内“仓库 + 物料”库存余额维护安全库存阈值，既有记录默认 `10.0000`，保持当前行为兼容。
- 将库存 KPI、当前库存预警和采购申请补货预填数量统一改为使用库存项阈值，消除前后端固定常量分叉。
- 在现有库存台账当前库存区域提供最小阈值维护入口，保存后立即刷新当前库存和 KPI。
非目标：
- 不实现最大库存、供应商供货周期、需求预测、历史消耗建模、全量补货工作台、跨仓调拨或自动下单。
- 不改变“可用量等于零不计入低库存 KPI/当前预警”的既有口径，不新增库存建议或策略表。
- 不重构采购申请审批、库存出入库、仓库管理、菜单框架或生产部署配置。
允许修改：
- `backend/src/main/resources/db/migration/V142__add_stock_safety_threshold.sql`
- `backend/src/main/resources/db/migration-h2/V142__add_stock_safety_threshold.sql`
- `backend/src/main/java/com/cgcpms/inventory/**`
- `backend/src/test/java/com/cgcpms/inventory/**`
- `frontend-admin/src/api/modules/inventory.ts`
- `frontend-admin/src/types/inventory.ts`
- `frontend-admin/src/pages/inventory/**`
- `docs/product-intelligence/**`、`docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
- `.codex-autopilot/state.json`
禁止修改：
- `deploy/**`
- 已应用的 `backend/src/main/resources/db/migration/V1__*.sql` 至 `V141__*.sql`
- `backend/src/main/java/com/cgcpms/purchase/**`
- `frontend-admin/src/pages/inventory/purchase-request.vue`
- 生产凭据、生产数据库连接、生产发布配置
验收标准：
- V142 MySQL/H2 镜像为 `mat_stock` 增加 `safety_stock_qty DECIMAL(18,4) NOT NULL DEFAULT 10.0000`，并新增独立 `inventory:stock:edit` 按钮权限；该权限只新增授予正式 `PURCHASE_MANAGER`，管理员继续由角色兜底，普通只读库存角色不授予；既有库存记录升级后行为不变。
- 阈值更新 API 只接受大于等于 `0` 且最多 4 位小数的值；按当前租户定位库存，并通过库存所属仓库项目执行 `ProjectAccessChecker`，跨租户、无项目访问权、禁用仓库或伪造 stockId 均 fail-close。
- 库存读取继续使用 `inventory:stock:list`，阈值维护使用 `inventory:stock:edit`（管理员角色保持兜底）；不得复用出入库写权限 `inventory:transaction:add`。
- `MatStockVO` 返回 `safetyStockQty`；KPI 低库存条件统一为 `available_qty > 0 AND available_qty < safety_stock_qty`，零库存不计入。
- 前端当前库存预警、阈值可见性和补货按钮均使用 `safetyStockQty`；建议数量精确为 `safetyStockQty - availableQty`，继续复用现有采购申请预填路径。
- 阈值保存成功后刷新当前库存和 KPI；无库存记录、只读用户、非法精度或负数不得出现可用写入结果。
- 至少覆盖默认阈值兼容、动态 KPI、更新边界、项目/租户隔离、采购经理可写、仅 `inventory:stock:list` 权限用户拒绝、前端动态预警与补货数量测试；回滚边界为回退应用后移除新增权限与列，不改写历史 migration。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=MatStockServiceTest,MatStockControllerTest" test`
- `cd frontend-admin; pnpm test:unit src/pages/inventory/__tests__/stock-production.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-018：子智能体超时、悬挂执行线程退役与有限重派治理

优先级：P0
任务性质：运维治理
类型：AutoPilot / 执行器生命周期 / 悬挂检测 / 有限重派 / 状态证据 / PowerShell 测试
状态：Done（2026-07-12；计入 `启动迭代-10` 第 2/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/ad-hoc-plan.md` 的“子智能体超时、悬挂线程退役与有限重派治理” Candidate；`AGENTS.override.md` 的悬挂线程处置规则；现有 `scripts/codex-autopilot/autopilot-run-continuous.ps1` 与 `scripts/codex-autopilot/test-executor-stall.ps1` 的部分实现证据
关联目标：保障 v1.5 AutoPilot 下一主线准入、连续执行和正式验收不会复用已悬挂执行单元，也不会无限重派。
阻塞证据：现有 runner 已能在 300 秒记录 inspect、600 秒终止并新起一次 repair，但持久 state 未记录线程开始/最后进展/退役状态/超时原因，第二次超时只进入通用 BLOCKED，且长命令声明尚无可验证闭环。
解除条件：专项测试证明 5 分钟只读核验、10 分钟永久退役、仅一次新线程缩小范围重派、第二次超时同步 blocked，以及已声明长命令不被静默误判。
Migration：不需要
依赖：复用现有连续 runner、原子 state、progress fingerprint、context pack、executor prompt 和临时 Git fixture；不新增依赖、服务或调度器。
风险等级：中
运行态要求：仅需 Windows PowerShell、Git 与临时目录自测试；不要求 Docker、backend、frontend、数据库、5173/8080 或真实浏览器，测试不得改动当前业务运行态。
Reviewer要求：必须独立复核超时阈值、只读 inspect、进程树终止、旧 executorPid 永久退役、单次新线程重派、第二次超时 blocked 归档、长命令声明的有界豁免、state/schema 一致性及 no push/stop/pause 边界；输出直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-037-018-子智能体超时悬挂线程退役与有限重派治理验收报告.md`
目标：
- 在现有 runner 的部分实现上补齐最小生命周期闭环：连续 300 秒无新证据时只做只读状态核验，连续 600 秒无新证据时终止执行进程树并永久退役该 executorPid。
- 首次超时只允许以新 executorPid 重派一次，重派必须进入 repair、缩小任务范围并携带缺失上下文；同一 Issue 第二次超时不得再启动第三个执行单元，必须进入 BLOCKED 并同步 `docs/backlog/blocked-issues.md`。
- 持久状态或正式事件证据至少记录 issue/task、executorPid、startedAt、lastProgressAt、retryCount、timeoutReason、retiredAt/retiredStatus；预计超过 600 秒的命令在派工上下文中显式记录命令与预计时长，并保持总超时上限。
非目标：
- 不建设通用分布式任务平台、线程池、队列、Dashboard、MCP 服务或跨机器恢复。
- 不修改任何业务接口、页面、数据库、权限、审批、金额、租户或项目数据逻辑。
- 不扩大 repair 次数，不复用已退役 executorPid，不自动 push，不发布生产，不连接或重置数据库。
允许修改：
- `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- `scripts/codex-autopilot/autopilot-state.ps1`
- `scripts/codex-autopilot/autopilot-progress.ps1`
- `scripts/codex-autopilot/autopilot-context.ps1`
- `scripts/codex-autopilot/autopilot-exec-issue.ps1`
- `scripts/codex-autopilot/codex-autopilot.config.json`
- `scripts/codex-autopilot/test-executor-stall.ps1`
- `scripts/codex-autopilot/test-state-machine.ps1`
- `scripts/codex-autopilot/test-progress-fingerprint.ps1`
- `plugins/cgc-pms-autopilot/schemas/loop-state.schema.json`
- `plugins/cgc-pms-autopilot/schemas/context-pack.schema.json`
- `plugins/cgc-pms-autopilot/examples/loop-state.example.json`
- `plugins/cgc-pms-autopilot/references/owner-boundary.md`
- `plugins/cgc-pms-autopilot/references/rerun-policy.md`
- `docs/backlog/**`、`docs/iterations/**`、`docs/quality/**`
禁止修改：
- `backend/**`
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `deploy/**`
- `AGENTS.md`、`AGENTS.override.md`
- `.github/workflows/**`
- 生产凭据、生产数据库连接、生产发布配置、仓库外文件
验收标准：
- 无新 worktree 内容、进程树活动或显式进展证据达到 300 秒时，只写一次 inspect 证据，不终止、不重派；恢复进展后重新计时。
- 无新证据达到 600 秒时，终止该执行进程树并把对应 executorPid 记录为 retired；后续状态转换和 repair 不得再次引用该 executorPid。
- 首次 stall timeout 只创建一个新 executorPid，retryCount 精确为 1，repair context 明确缩小剩余范围并包含首次超时原因；即使 `repair.maxRepairAttempts` 大于 1，也不得因 stall 产生第二次重派。
- 同一 Issue 的新执行单元再次 stall timeout 后不创建第三个 executorPid，最终 state 为 BLOCKED，并在 `docs/backlog/blocked-issues.md` 写明失败分类、两个退役执行单元证据、解除条件、未完成验收项和安全恢复方式。
- 每次 inspect/retire/重派/blocked 证据均可关联 issueId、executorPid、startedAt、lastProgressAt、retryCount、timeoutReason 和时间；state、schema、example 与原子写入校验保持一致。
- 派工上下文对预计超过 600 秒的命令记录原始命令和预计秒数；仅在声明的后代进程仍存活且未超过预计时长时避免按“无证据”误杀，绝不绕过 issueExecutor 总超时、stop/pause checkpoint 或进程树终止。
- 专项测试使用临时仓库稳定证明“300 秒只 inspect”“600 秒退役”“恰好一次新线程重派”“第二次超时 blocked”“长命令声明有界且超时仍终止”，并清理自身临时目录；不要求修改或启动业务运行态。
- 回滚只恢复本 Issue 的 runner/state/context/config/schema/test/文档差异；无数据迁移、业务数据回填或生产恢复动作。
验证命令：
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-executor-stall.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-state-machine.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-progress-fingerprint.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1`
- `git diff --check`

### ISSUE-037-019：后端接口无前端入口只读盘点与治理裁决

优先级：P0
任务性质：回归证明
类型：工程治理 / 接口可达性 / 前后端映射 / 只读审计 / 正式验收
状态：Done（2026-07-12；计入 `启动迭代-10` 第 3/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/ad-hoc-plan.md` 的“后端接口无前端入口治理” Candidate；`docs/product-intelligence/project-map.md` 的现有能力可达性缺口；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-11-01` 候选决策卡；`docs/未来开发计划.md` 的“权限、租户、项目数据边界”验收边界
关联产品目标：为 v1.5 真实角色与正式验收建立“后端能力是否存在用户可达入口”的当前事实基线，避免把后端已实现误判为用户可用，也不以工程治理替代下一项产品方向。
阻塞证据：当前迭代决策明确记录“没有最新接口—入口全量差异报告”；因此无法对无入口接口逐项裁决为需入口、仅内部使用或待废弃，也无法为真实角色的前端可达性正式验收提供完整清单。
解除条件：形成覆盖当前用户态 Controller、前端 API 封装、路由、页面与菜单证据的逐项差异报告；所有未映射接口均有唯一分类、证据、责任域和后续处置，独立 Reviewer 复核通过。
Migration：不需要
依赖：仅复用当前 `backend/src/main/java/com/cgcpms/**/controller/**`、`frontend-admin/src/api/modules/**`、`frontend-admin/src/router/**`、`frontend-admin/src/pages/**`、`frontend-admin/src/layouts/**` 与现有菜单定义；不新增依赖、扫描框架或持久化对象。
风险等级：中
运行态要求：静态只读盘点，不要求 Docker、backend、frontend、数据库、5173/8080、dev-login 或真实浏览器；不得据此宣称真实角色运行态可见性已经通过，若静态证据冲突则标记“需要确认”而不是启动或修改业务运行态。
Reviewer要求：必须由独立 Reviewer 交叉核对 Controller 映射与权限注解、前端 request 调用、路由/页面/菜单证据，复核路径参数归一化、动态菜单和内部接口误报；至少抽查每个业务域一条“有入口”与全部“需补入口/待废弃/需要确认”项，输出直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`
最小回滚：删除本 Issue 新增的正式报告并回退本 Issue 对项目地图、迭代决策、backlog 与 iteration 状态的文档差异；无代码、配置、数据库或运行态恢复动作。
目标：
- 只读枚举 `backend/src/main/java/com/cgcpms/**/controller/**` 中面向应用的 HTTP 方法、规范化路径、Controller 方法和权限注解，并与前端 API 封装、路由、页面及菜单证据建立可追溯映射。
- 对每个未形成用户入口的接口唯一裁决为“前端调用但无独立页面”“仅内部/集成/回调/运维”“需补用户入口”“待废弃”或“需要确认”，记录证据、误报风险、责任域与最小后续动作。
- 输出当前快照的总数与分类计数，保证分项之和等于纳入范围总数，并把结论回写项目地图、迭代决策和 Ad-hoc 状态；本 Issue 只产出治理事实与后续拆题依据。
非目标：
- 不新增、修改或删除任何后端接口、Controller、Service、Entity、Mapper、权限注解、数据库 migration、前端 API、路由、菜单、页面、按钮或样式。
- 不为所有后端接口机械创建页面，不把内部接口、回调、下载、聚合子资源或开发接口自动判为缺口，不扩成 DTO 重构、OpenAPI 平台或通用扫描器。
- 不做真实角色浏览器验收，不连接或重置数据库，不启动/重建运行态，不发布生产，不自动 push；盘点结论不等同于后续入口实现已经完成。
允许修改：
- `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/backlog/ad-hoc-plan.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/iterations/**`
禁止修改：
- `backend/**`
- `frontend-admin/**`
- `deploy/**`
- `scripts/**`
- `plugins/**`
- `.github/workflows/**`
- `AGENTS.md`
- `AGENTS.override.md`
- 生产凭据、生产数据库连接、生产发布配置、仓库外文件
验收标准：
- 报告明确盘点日期、分支、提交基线、纳入/排除口径和路径归一化规则；覆盖应用 Controller 的组合类级/方法级映射，并保留 Controller 文件与方法、HTTP 方法、规范化 URL、权限注解证据。
- 每个纳入接口均能追溯到前端 API 调用及路由/页面/菜单证据，或进入且只进入一个未映射分类；动态路径参数、查询参数、聚合子资源和复用同一页面的多个接口不得因字符串不完全一致被重复或误判。
- “需补用户入口”“待废弃”“需要确认”逐项写明用户/业务价值、权限与租户风险、当前证据、责任域、最小后续动作和是否允许拆新 Ready；没有证据的项保持“需要确认”。
- 分类汇总满足“有用户入口 + 前端调用但无独立页面 + 仅内部/集成/回调/运维 + 需补用户入口 + 待废弃 + 需要确认 = 纳入接口总数”，重复项为 0；排除项单列理由，不计入分母。
- 项目地图与迭代决策仅回写本次事实基线和候选裁决；Ad-hoc Candidate 只有在报告与独立复核通过后才可标记 Done，真实入口补建、接口废弃或运行态验收必须另拆 Ready。
- 最终正式报告给出通过/不通过、阻塞/非阻塞、依据和剩余风险；剩余风险同步进入 `docs/backlog/ready-issues.md`、`docs/backlog/blocked-issues.md` 或 `docs/backlog/current-focus.md`，不得只留在质量报告。
验证命令：
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/ready-lint.ps1 -IssueTitle ISSUE-037-019`
- `cd backend; .\mvnw.cmd "-DskipTests" compile`
- `cd frontend-admin; pnpm test:unit src/router/__tests__/router.test.ts src/api/modules/__tests__/system-modules.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-037-020：长期计划描述性标题误入候选修复

优先级：P0
任务性质：缺口修复
类型：AutoPilot / 候选准入 / 长期计划解析 / PowerShell 回归
状态：Done（2026-07-12；计入 `启动迭代-10` 第 4/10 条）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `2.1 当前技术栈` 节被 `scripts/codex-autopilot/autopilot-refill.ps1` 产出为 `long-term:2.1` Candidate；`docs/backlog/current-focus.md` 要求 Ready 为空时先刷新产品情报，不得从长期计划凑任务
关联产品目标：恢复 v1.5 产品情报补货与下一主线准入的候选准确性，避免把现状、对标、优势或总体目标标题误当作可实施产品方向。
阻塞证据：本轮 Planner 实际收到 `{"name":"当前技术栈","status":"Candidate","source":"long-term:2.1"}`；当前解析器匹配长期计划全部 `### N.N` 标题，而 `2.1` 位于“当前功能现状”且项目地图已记录相同技术基线，没有待实现闭环。
解除条件：长期计划补货只返回开发计划章节中的候选，不再返回 `2.1 当前技术栈` 等描述性章节；Ad-hoc 优先级、单条 Ready 目标、stop/pause、blocked-first 和合法长期候选行为保持不变。
Migration：不需要
依赖：仅复用现有 `Get-AutopilotRefillDecision` 与 `scripts/codex-autopilot/test-refill.ps1` 临时目录自测；不新增依赖、配置或调度器。
风险等级：中
运行态要求：仅需 Windows PowerShell 和临时目录自测；不要求 Docker、backend、frontend、数据库、5173/8080、dev-login 或浏览器，不得启动或修改业务运行态。
Reviewer要求：必须由独立 Reviewer 复核长期计划章节边界、Ad-hoc 优先顺序、合法开发计划候选保留、描述性标题排除及 stop/pause/blocked-first 行为；输出直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-037-020-长期计划描述性标题误入候选修复验收报告.md`
最小回滚：回退本 Issue 对 `autopilot-refill.ps1`、`test-refill.ps1` 和状态文档的差异；无业务代码、数据、schema 或运行态恢复动作。
目标：
- 在现有长期计划候选提取处增加最小章节准入边界，使 `2.1 当前技术栈` 等现状/分析标题不再进入 fresh Planner。
- 在现有 refill 自测中覆盖误候选回归，同时证明至少一个真实开发计划标题仍可进入 Planner；不新建第二套解析器。
非目标：
- 不修改或校准技术栈，不升级 Vue、Java、Spring、数据库、中间件、依赖或部署组件。
- 不修改业务接口、页面、权限、租户、审批、金额、数据库 migration、产品功能或生产配置。
- 不重写长期计划，不从长期计划直接生成业务 Ready，不扩建通用 Markdown AST、候选评分器或新调度器。
允许修改：
- `scripts/codex-autopilot/autopilot-refill.ps1`
- `scripts/codex-autopilot/test-refill.ps1`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/blocked-issues.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/iterations/**`
- `docs/quality/ISSUE-037-020-长期计划描述性标题误入候选修复验收报告.md`
禁止修改：
- `backend/**`
- `frontend-admin/**`
- `deploy/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `.github/workflows/**`
- `AGENTS.md`
- `AGENTS.override.md`
- 生产凭据、生产数据库连接、生产发布配置、仓库外文件
验收标准：
- 空 Ready、空 Ad-hoc 的临时仓库包含 `2.1 当前技术栈` 与至少一个开发计划标题时，refill 决策不选择 `long-term:2.1`，且仍能选择合法开发计划 Candidate。
- 现有 Ad-hoc `ReadyToSplit` / `Candidate` 优先级和单条 Ready 目标保持不变；已有 Ready、stop.flag、pause.flag 与当前 focus blocked 前置仍分别返回既有决策。
- 修改只收敛长期计划候选提取及其最小回归测试，不新增依赖或并行解析实现；`git diff --check` 通过。
- 正式报告给出通过/不通过、阻塞/非阻塞、依据和剩余风险；剩余风险同步进入 backlog 或 Current Focus，不只留在质量报告。
验证命令：
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-refill.ps1`
- `git diff --check`

### ISSUE-037-021：CI/CD 与上线门禁 v1.5 现状复验与红灯分类裁决

优先级：P0
任务性质：回归证明
类型：CI/CD / GitHub Actions / 分支保护 / 上线门禁 / 只读审计 / 正式裁决
状态：Done（2026-07-12；回归证明完成，裁决为不通过 / 阻塞 / 不可上线）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 的 `7.1 P0-1：CI/CD 与上线门禁`；`docs/未来开发计划.md` 的“CI/CD 与上线门禁复验”；`docs/backlog/current-focus.md` 的 v1.0 证据不得直接作为 v1.5 验收证据约束
关联产品目标：为 v1.5 下一主线准入和正式上线裁决建立当前、可追溯的 CI/CD 门禁事实，不以 v1.0 绿灯或旧分支保护快照替代当前证据。
阻塞证据：2026-07-12 只读核验显示 master 最近 5 次 CI push run 均为 failure；最新 run 29146534529 的 frontend-lint、frontend-test、e2e 失败，而 master required checks 仍要求这 11 个核心 job 且 strict=true，因此当前不能给出上线门禁通过结论。
解除条件：完成最新失败日志分类；核对现行 workflow job 与 master required checks 一一对应；本地可复现入口结果与远端证据无矛盾；正式报告明确通过/不通过并把未解除项同步到 Ready、Blocked 或 Current Focus。
Migration：不需要
依赖：GitHub CLI 已认证且对 `kismet84/cgc-pms` 具有 actions 与 branch protection 只读权限；复用现有 `.github/workflows/ci.yml`、前后端构建测试入口和 `scripts/check-sql-safety.ps1`，不新增扫描器或依赖。
风险等级：高
运行态要求：需要 GitHub 网络只读访问、Java 21、Node.js 22、pnpm 11；默认不启动 Docker、backend、frontend 或浏览器，不触发 workflow rerun；若为复现 e2e 才先执行 8080/5173/dev-login health gate，环境刷新后稳定等待 180 秒，且不得连接生产或重置数据。
Reviewer要求：必须由独立 Reviewer 复核远端 run/job/step 证据、失败三分类、workflow 与 required checks 对应关系、strict/enforce_admins/PR review 配置、产物与回滚信息；输出直接用于通过/不通过和是否可上线裁决，任何红灯或证据缺失均不得判通过。
归档报告：`docs/quality/ISSUE-037-021-CI-CD与上线门禁v1.5复验报告.md`
最小回滚：仅回退本 Issue 新增的正式报告及 product-intelligence/backlog/iteration 状态文档；无代码、workflow、远端设置、数据库或运行态恢复动作。
目标：
- 以当前 master 分支保护、required checks、最新 CI runs 和现行 `.github/workflows/ci.yml` 为准，复验 CI/CD 与上线门禁，不沿用 v1.0 完成结论。
- 对最新失败的 workflow/job/step 按工具配置类、环境前置类、真实质量/安全类逐项分类，记录 commit、run URL、失败关键词、复现结果和解除条件。
- 形成唯一正式裁决；未解除红灯按责任域拆入 Ready 或 Blocked，本 Issue 不直接修业务代码或远端设置。
非目标：
- 不修复 frontend-lint、frontend-test、e2e 或其他业务/测试代码失败，不修改 workflow，不触发 rerun、commit、push、merge、release 或生产发布。
- 不升级 Actions、Node、Java、pnpm、Maven、Spring Boot 或依赖，不新增 CI 平台、扫描器、发布系统或通用门禁框架。
- 不连接生产数据库，不重置测试数据，不把本地单项通过替代远端 required checks 全绿。
允许修改：
- `docs/quality/ISSUE-037-021-CI-CD与上线门禁v1.5复验报告.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/iterations/**`
- `.codex-autopilot/state.json`
禁止修改：
- `backend/**`
- `frontend-admin/**`
- `.github/workflows/**`
- `scripts/**`
- `plugins/**`
- `deploy/**`
- `AGENTS.md`、`AGENTS.override.md`
- 生产凭据、生产数据库连接、GitHub 分支保护、required checks、Actions runs、release、仓库外文件
验收标准：
- 报告记录核验时间、分支、commit、最新 master push/PR run、逐 job/step 结论和 URL；至少覆盖 required 的 backend-test、backend-test-mysql、backend-dependency-scan、frontend-lint、type-check、frontend-build、frontend-test、frontend-dependency-audit、sql-safety-scan、e2e、supply-chain-security。
- master 分支保护的 strict、required checks、enforce_admins、PR review 与限制项均有当前 API 证据；required checks 与 `.github/workflows/ci.yml` job 名称逐项比对，缺失、多余、不可触发或可绕过项不得忽略。
- 最新红灯逐项先分类；frontend-lint、frontend-test、e2e 的失败日志至少记录失败 step、关键词和最小复现，不能仅凭 job 名猜测根因，一次性波动需复核后再升级为阻塞。
- 本地只读复验入口均执行并记录；本地与远端不一致时以当前远端 required checks 为上线裁决依据并解释差异，不修改业务代码或 workflow 掩盖红灯。
- 只有目标 commit 的 11 个 required checks 全部 success、分支保护与 workflow 对应且无绕过风险时才允许结论为“通过 / 非阻塞 / 可上线”；否则必须为“不通过 / 阻塞 / 不可上线或需要确认”。
- 每个未解除项写明责任域、失败分类、解除条件、最小安全恢复方式并同步到 Ready、Blocked 或 Current Focus；回滚仅删除本 Issue 文档差异。
验证命令：
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/ready-lint.ps1 -IssueTitle ISSUE-037-021`
- `cd backend; .\mvnw.cmd verify`
- `cd frontend-admin; pnpm lint:check`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `cd frontend-admin; pnpm test:coverage`
- `cd frontend-admin; pnpm audit --audit-level high --registry=https://registry.npmjs.org`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-sql-safety.ps1`
- `git diff --check`

执行复验结果（2026-07-12，独立 Reviewer 已完成）：
- 目标 master commit `781b41661cd96b2a2f7eed825f98ff3d9bdf137b` 的 frontend-lint、frontend-test、e2e required checks 为 failure；当前裁决为不通过 / 阻塞 / 不可上线。
- 未解除项已按前端质量、E2E、仓库治理三个责任域写入 `blocked-issues.md`；本 Issue 不越界修复或修改远端设置。
- 正式证据：`docs/quality/ISSUE-037-021-CI-CD与上线门禁v1.5复验报告.md`。
- Reviewer 结论：报告证据与失败分类通过复核；上线裁决仍为不通过 / 阻塞 / 不可上线。

### ISSUE-040-019：系统菜单新建管理员入口与树约束

优先级：P0
任务性质：缺口修复
类型：系统管理 / 前后端入口 / 权限 / 租户隔离 / 菜单树一致性
状态：Done
来源锚点：`docs/backlog/current-issues.json` 的 `A-01-MENU-CREATE`；`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；`docs/product-intelligence/project-map.md`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-13-02` 与“后端接口无前端入口治理”决策卡
存量标记：[stock:A-01-MENU-CREATE]
关联产品目标：让管理员从既有权限清单页可达已经实现的菜单创建能力，关闭“后端存在但用户不可达”的 A-01 最小缺口，不扩展为菜单管理平台重构。
阻塞证据：`SysMenuController.create` 已提供 `POST /system/menus` 并要求 ADMIN/SUPER_ADMIN 或 `system:menu:add`；`frontend-admin/src/api/modules/system.ts` 仅有菜单树 GET，`frontend-admin/src/pages/system/permissions/index.vue` 仅只读展示权限清单，当前没有同方法创建调用或新建交互；`SysMenuService.create` 虽强制当前租户，但尚未校验非根父菜单的同租户存在性、父节点类型和菜单类型枚举。
解除条件：既有管理员权限清单页提供受控的新建菜单入口，前端只向 `POST /system/menus` 发送最小合法载荷，后端保持精确授权并拒绝非法树关系；创建成功后刷新树，权限、租户和树结构正负样本全部通过。
Migration：不需要
依赖：复用既有 `/system/permissions` 管理员页面、`getMenuTree`、`SysMenuController.create`、`SysMenuService`、`UserContext` 与当前 `sys_menu` 表；不新增路由、菜单种子、表或权限码。
风险等级：高
运行态要求：自动化先在本地 dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，并从 dev-login 进入 `/system/permissions`；不得连接生产，创建或删除真实测试菜单前还必须满足 dev/test/demo、数据库 host 为 localhost/127.0.0.1 且存在 `.codex-autopilot/ALLOW_TEST_DATA_RESET`。
Reviewer要求：独立复核后端 `system:menu:add` 与 ADMIN/SUPER_ADMIN 授权未放宽、无权限负样本为 403；前端入口必须与 `/system` 的 admin-only 路由一致，仅 ADMIN/SUPER_ADMIN 可见，普通用户即使持有单独的 `system:menu:add` 也不得被单元测试伪装为页面可达；同时复核租户 ID 不能由客户端覆盖、非根父节点必须属于当前租户且不能是 BUTTON、菜单类型仅允许 DIR/MENU/BUTTON；结论直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-040-019-系统菜单新建管理员入口验收报告.md`
最小回滚：回退本 Issue 新增的前端创建交互/API/类型和后端树约束及对应测试；无 schema 回滚。若运行态产生测试菜单，仅在满足测试数据重置三项前置后删除该唯一测试记录。
目标：
- 在既有 admin-only `/system/permissions` 页面增加“新建菜单”最小入口与表单，复用当前菜单树选择父节点；只有 ADMIN/SUPER_ADMIN 可见并可从该页面提交。
- 在 `frontend-admin/src/api/modules/system.ts` 增加类型化的 `POST /system/menus` 调用；创建成功后关闭表单并刷新菜单树，失败时不伪造成功状态。
- 在后端创建路径补齐最小树约束：根节点统一使用 `parentId=0`；非根父节点必须存在于当前租户且不能是 BUTTON；`menuType` 只接受 DIR/MENU/BUTTON；继续强制以 `UserContext` 覆盖客户端 tenantId。
非目标：
- 不实现菜单编辑、删除、拖拽排序、批量授权、动态路由生成或完整菜单管理 CRUD。
- 不新增页面路由、侧栏项、角色、权限码、数据库 migration 或种子数据，不修改已应用 migration。
- 不重构 `SysMenu` 为全量 DTO，不修改角色授权、审计、登录或其他系统管理页面，不连接生产数据库或发布生产。
允许修改：
- `frontend-admin/src/api/modules/system.ts`
- `frontend-admin/src/api/modules/__tests__/system-modules.test.ts`
- `frontend-admin/src/types/system.ts`
- `frontend-admin/src/pages/system/permissions/index.vue`
- `frontend-admin/src/pages/system/permissions/__tests__/index.test.ts`
- `backend/src/main/java/com/cgcpms/system/service/SysMenuService.java`
- `backend/src/test/java/com/cgcpms/system/SysMenuControllerTest.java`
- `backend/src/test/java/com/cgcpms/system/SysMenuServiceTest.java`
- `docs/backlog/current-issues.json`、`docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`、`docs/product-intelligence/evolution-decision.md`、`docs/quality/**`
禁止修改：
- `backend/src/main/java/com/cgcpms/system/controller/SysMenuController.java`、`backend/src/main/java/com/cgcpms/system/entity/SysMenu.java`、`backend/src/main/java/com/cgcpms/system/mapper/**`
- `backend/src/main/resources/db/migration/**`、`backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`、`frontend-admin/src/router/navigation.ts`、其他 `frontend-admin/src/pages/system/**`
- `deploy/**`、`.github/**`、生产凭据、生产数据库、仓库外文件与受保护私有目录
验收标准：
- 前端 API 测试精确断言创建调用为 `POST /system/menus`，载荷不含 `id`、`tenantId`、审计字段或 children；权限清单页测试覆盖入口权限、必填校验、父节点选择、提交成功刷新与失败保留表单。
- 后端 ADMIN/SUPER_ADMIN 或持有 `system:menu:add` 的请求可创建，既非管理员也无该权限的请求返回 403；前端入口只对 ADMIN/SUPER_ADMIN 可见，普通用户即使单独持有 `system:menu:add` 也保持隐藏，以符合父路由 admin-only 边界；前端可见性与后端授权分别有证据。
- 根节点以 `parentId=0` 创建；非根父节点不存在、属于其他租户或类型为 BUTTON 时拒绝；非法 `menuType` 拒绝；合法同租户父节点创建成功且 `tenantId` 始终来自当前用户上下文。
- 创建成功后重新获取菜单树，新节点只出现在选定父节点下；失败不刷新为成功态，不新增菜单编辑、删除或排序能力。
- 收口时必须引用 `docs/backlog/current-issues.json` 和源报告；全部验证通过后移除 `A-01-MENU-CREATE`，或在未完全通过时以证据更新其唯一状态/分类，禁止 Done 后仍保留原 OPEN；同时更新 A-01 守恒计数、Ready/current-focus/project-map，并在归档报告统计新增后续项、关闭后续项、后续项净变化。
- 后端专项、前端专项、类型检查和 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SysMenuControllerTest,SysMenuServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/system-modules.test.ts src/pages/system/permissions/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

收口结果（2026-07-13）：
- 既有 admin-only 权限清单页已增加受控“新建菜单”入口；前端仅 ADMIN、SUPER_ADMIN 可见，普通用户即使单独持有 `system:menu:add` 也隐藏；后端原 ADMIN/SUPER_ADMIN 或 `system:menu:add` 授权注解未修改且无权限请求专项返回 403。
- 前端新增类型化 `POST /system/menus`，只发送菜单业务字段；成功后关闭表单并刷新菜单树，失败时保留表单且不刷新成功态。
- `SysMenuService.create` 统一根节点 `parentId=0`，拒绝非法类型、不存在/跨租户/BUTTON 父节点，并继续以当前 `UserContext` 覆盖客户端租户。
- 后端专项 35/35、前端专项 13/13、前端类型检查与 `git diff --check` 通过；首次控制器测试失败已确认是测试 JWT 默认密钥过短的 `tool_config` 前置，补入测试专用密钥后原命令复验通过。
- `A-01-MENU-CREATE` 已从 `docs/backlog/current-issues.json` 移除；A-01 守恒更新为有用户入口225、前端调用无独立页面58、内部4、需补入口23、需要确认11，总数321。
- 正式报告：`docs/quality/ISSUE-040-019-系统菜单新建管理员入口验收报告.md`；新增后续项0、关闭后续项1（`A-01-MENU-CREATE`）、后续项净变化-1。

### ISSUE-040-020：系统菜单删除管理员入口与安全约束

优先级：P0
任务性质：缺口修复
类型：系统管理 / 前端入口 / 破坏性操作 / 权限 / 租户隔离 / 菜单树一致性
状态：Done
来源锚点：`docs/backlog/current-issues.json` 的 `A-01-MENU-DELETE`；`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；`docs/product-intelligence/project-map.md`；`docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-13-02` 与“后端接口无前端入口治理”决策卡
存量标记：[stock:A-01-MENU-DELETE]
关联产品目标：让管理员从既有权限清单页可达已经实现的菜单删除能力，关闭 A-01 中 `DELETE /system/menus/{id}`“后端存在但用户不可达”的最小缺口；本切片只建立受控删除入口，不扩展为菜单管理平台重构。
阻塞证据：`SysMenuController.delete` 已提供 `DELETE /system/menus/{id}` 并要求 ADMIN/SUPER_ADMIN 或 `system:menu:delete`；`SysMenuService.delete` 已按当前租户校验目标，并拒绝存在子节点或角色引用的菜单；但 `frontend-admin/src/api/modules/system.ts` 只有菜单树读取与新建调用，`frontend-admin/src/pages/system/permissions/index.vue` 没有删除交互，现有页面测试还明确断言不包含 `deleteMenu`。
解除条件：既有 admin-only `/system/permissions` 页面可从完整菜单树选择目标，在明确的破坏性确认后调用现有 DELETE 接口；成功后刷新菜单树和角色引用状态，失败时保留选择并展示后端拒绝原因；权限、租户、子节点和角色引用正负样本全部通过。
Migration：不需要
依赖：复用既有 `/system/permissions` 页面、`getMenuTree`、`getRoles`、`SysMenuController.delete`、`SysMenuService.delete`、当前 `sys_menu` / `sys_role_menu` 约束与 admin-only 路由；不新增路由、菜单种子、表、权限码或后端生产逻辑。
风险等级：高
运行态要求：自动化必须在本地 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，并从 dev-login 进入 `/system/permissions`。真实删除验收只能在 dev/test/demo、数据库 host 为 localhost/127.0.0.1 且存在 `.codex-autopilot/ALLOW_TEST_DATA_RESET` 时进行：先创建带唯一名称、无子节点且未绑定角色的测试菜单，再经新入口删除并确认树刷新；任一前置缺失时禁止删除数据并按环境前置阻塞处理。不得连接或发布生产。
Reviewer要求：独立复核后端 `system:menu:delete` 与 ADMIN/SUPER_ADMIN 授权未放宽、无权限请求为 403、跨租户目标 fail-close；前端删除入口必须与 `/system` 的 admin-only 路由一致，仅 ADMIN/SUPER_ADMIN 可见，普通用户即使单独持有 `system:menu:delete` 也不得被单元测试伪装为页面可达；同时复核确认交互不能绕过，子节点和角色引用拒绝不产生删除，失败态不伪造成功；结论直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-040-020-系统菜单删除管理员入口验收报告.md`
最小回滚：回退本 Issue 新增的前端删除 API、选择/确认交互及对应测试和控制器权限测试；不回退既有后端删除逻辑，无 schema 回滚。若运行态遗留唯一测试菜单，只能在测试数据重置三项前置满足时清理；否则保留证据并判定未完成，不得扩大删除范围。
目标：
- 在既有 admin-only `/system/permissions` 页面增加“删除菜单”最小入口，基于现有完整菜单树选择一个目标；无权限码的目录/菜单节点也必须可选择，不能只覆盖当前权限码行。
- 删除前展示目标菜单名称与不可逆提示并要求显式确认；确认后调用类型化 `DELETE /system/menus/{id}`，成功关闭交互并刷新菜单树和角色引用状态，失败保留目标且显示后端错误。
- 只补控制器权限专项并复用既有 `SysMenuServiceTest` 证明租户、子节点和角色引用约束；后端生产 Controller、Service、Entity、Mapper 保持不变。
非目标：
- 不实现菜单编辑、批量删除、级联删除、强制解绑、拖拽排序、角色授权改造、动态路由生成或完整菜单 CRUD。
- 不新增页面路由、侧栏项、角色、权限码、数据库 migration 或种子数据，不修改已应用 migration。
- 不修改既有后端删除语义，不绕过 `MENU_HAS_CHILDREN` 或 `MENU_REFERENCED_BY_ROLES`，不连接生产数据库、不发布生产，也不删除非本 Issue 创建的测试数据。
允许修改：
- `frontend-admin/src/api/modules/system.ts`
- `frontend-admin/src/api/modules/__tests__/system-modules.test.ts`
- `frontend-admin/src/pages/system/permissions/index.vue`
- `frontend-admin/src/pages/system/permissions/__tests__/index.test.ts`
- `backend/src/test/java/com/cgcpms/system/SysMenuControllerTest.java`
- `docs/backlog/current-issues.json`、`docs/backlog/ready-issues.md`、`docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`、`docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-020-系统菜单删除管理员入口验收报告.md`
禁止修改：
- `backend/src/main/java/com/cgcpms/system/controller/SysMenuController.java`
- `backend/src/main/java/com/cgcpms/system/service/SysMenuService.java`
- `backend/src/main/java/com/cgcpms/system/entity/**`、`backend/src/main/java/com/cgcpms/system/mapper/**`
- `backend/src/main/resources/db/migration/**`、`backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`、`frontend-admin/src/router/navigation.ts`、其他 `frontend-admin/src/pages/system/**`
- `deploy/**`、`.github/**`、生产凭据、生产数据库、仓库外文件与受保护私有目录
验收标准：
- 前端 API 测试精确断言删除调用为 `DELETE /system/menus/{id}` 且不附带请求体；页面测试覆盖 ADMIN/SUPER_ADMIN 可见、普通用户不可见、普通用户仅持 `system:menu:delete` 仍不可见、未选择拒绝、确认后调用、成功刷新/关闭和失败保留/不刷新。
- 删除目标来自完整菜单树并显示目标名称；目录、菜单、按钮及无权限码节点都能被选择。页面必须明确提示有子节点或角色引用会被拒绝，不能提供级联或强制解绑选项。
- ADMIN/SUPER_ADMIN 或持有 `system:menu:delete` 的后端请求可删除当前租户内无子节点且无角色引用的菜单；既非管理员也无该权限的请求返回 403。前端入口只对 ADMIN/SUPER_ADMIN 可见，前端可见性与后端授权分别留证。
- 不存在或跨租户目标 fail-close；有子节点返回 `MENU_HAS_CHILDREN`，有角色引用返回 `MENU_REFERENCED_BY_ROLES`；全部拒绝路径均不删除目标、子节点或角色菜单关系。既有 `SysMenuServiceTest` 与补充后的 `SysMenuControllerTest` 提供裁决证据。
- 成功删除后重新获取菜单树与角色列表且目标消失；失败时不显示成功、不关闭删除交互、不刷新成成功态，并展示后端可理解错误；后端生产删除逻辑、路由、权限码和 schema 均无改动。
- 收口时必须引用 `docs/backlog/current-issues.json` 与其 `sourceRefs` 源报告；全部验证通过后移除 `A-01-MENU-DELETE`，或在未完全通过时以证据更新其唯一状态/分类，禁止 Done 后仍保留原 OPEN。通过时同步把 A-01 守恒更新为有用户入口 226、前端调用但无独立页面 58、内部/集成/运维 4、需补入口 22、待废弃 0、需要确认 11、总数 321，并更新 Ready/current-focus/project-map/evolution-decision。
- 归档报告必须统计新增后续项、关闭后续项和后续项净变化；所有发现项须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。
- Ready lint、后端专项、前端专项、类型检查和 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-020`
- `cd backend; .\mvnw.cmd "-Dtest=SysMenuControllerTest,SysMenuServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/system-modules.test.ts src/pages/system/permissions/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

收口结果（2026-07-13）：
- 既有 admin-only 权限清单页已增加受控“删除菜单”入口；目标直接来自完整菜单树，目录、菜单、按钮和无权限码节点均可选择，确认区展示目标名称、不可逆提示和子节点/角色引用拒绝说明。
- 前端类型化 API 固定调用无请求体的 `DELETE /system/menus/{id}`；仅 ADMIN/SUPER_ADMIN 显示入口。删除后只有菜单树与角色列表均刷新成功且目标已消失才提示成功并关闭；删除拒绝、刷新失败或刷新树仍含目标均保留交互且不伪造完整成功。
- 后端生产 Controller、Service、Entity、Mapper、migration 均未修改；新增控制器专项证明 ADMIN/SUPER_ADMIN/`system:menu:delete` 正样本和无权限403，既有服务专项证明成功删除、跨租户/不存在 fail-close、子节点与角色引用拒绝。
- Ready lint 通过且无警告；后端专项39/39、前端专项21/21、前端类型检查与 `git diff --check` 通过。首次 pnpm 并行安装竞争归类为 `tool_config` 并经串行恢复复验；独立 Reviewer 首次指出刷新异常被吞导致伪成功，按真实质量问题修复后二次复核通过。
- `A-01-MENU-DELETE` 已从 `docs/backlog/current-issues.json` 移除；A-01 守恒更新为有用户入口226、前端调用无独立页面58、内部4、需补入口22、需要确认11，总数321。
- 正式报告：`docs/quality/ISSUE-040-020-系统菜单删除管理员入口验收报告.md`；新增后续项0、关闭后续项1（`A-01-MENU-DELETE`）、后续项净变化-1。

### ISSUE-040-021：系统菜单详情管理员入口与权限边界

优先级：P0
任务性质：缺口修复
类型：系统管理 / 前端只读入口 / 权限 / 租户隔离 / 敏感字段收敛
状态：Done
来源锚点：`docs/backlog/current-issues.json` 的 `A-01-MENU-DETAIL`；其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；并引用 `docs/product-intelligence/project-map.md` 与 `docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-13-02`
存量标记：[stock:A-01-MENU-DETAIL]
关联产品目标：在既有 admin-only 权限清单页让管理员可达已实现的菜单详情能力，关闭 A-01 中 `GET /system/menus/{id}`“后端存在但用户不可达”的最小缺口；本切片只提供只读详情，不扩展为菜单管理平台。
补货排序依据：该条目是 `current-issues.json` 中 priorityOrder=0、statusOrder=0、specificityOrder=0 的 P0 非阻塞叶子项；当前 Ready 为空，按 `PI-2026-07-13-02` 的结构化存量优先规则先于 P1 投标/间接费等域，且高风险 `A-01-OVERHEAD-EXECUTE` 不应在只读菜单详情之前扩大执行面。
阻塞证据：`SysMenuController.getById` 已提供 `GET /system/menus/{id}`，授权为 ADMIN/SUPER_ADMIN 或 `system:menu:query`，`SysMenuService.getById` 已按当前租户对不存在和跨租户目标统一 `MENU_NOT_FOUND`；但 `frontend-admin/src/api/modules/system.ts` 只有菜单树、新建和删除调用，`frontend-admin/src/pages/system/permissions/index.vue` 没有详情请求或展示入口。CodeGraph 未召回预期前端组件，已按工具召回不足用 `rg` 与只读 codebase-memory 交叉确认，不能据此误判代码不存在。
解除条件：既有 `/system/permissions` 管理员页面可从完整菜单树选择目录、菜单、按钮或无权限码节点并加载当前菜单详情；前端展示只使用 `SysMenuVO` 白名单字段，后端授权、租户 fail-close、非授权拒绝和前端 admin-only 可见性均有自动化证据。
Migration：不需要
依赖：复用既有 `/system/permissions` 页面、`getMenuTree`、`SysMenuVO`、`SysMenuController.getById`、`SysMenuService.getById`、`UserContext` 与 admin-only `/system` 路由；不新增数据库对象、菜单种子或权限码。
风险等级：中
运行态要求：自动化必须在本地 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，并从 dev-login 进入 `/system/permissions` 验证选择与只读展示。任一入口不通先按环境前置类执行 runtime refresh，稳定等待 180 秒后复验；不得连接或发布生产，本 Issue 不需要测试数据删除或重置。
Reviewer要求：独立复核后端 ADMIN/SUPER_ADMIN 与 `system:menu:query` 授权未放宽、无角色且无权限请求为 403、未登录为 401、跨租户或不存在目标 fail-close；前端入口必须与 `/system` 的 admin-only 路由一致，仅 ADMIN/SUPER_ADMIN 可见，普通用户即使单独持有 `system:menu:query` 也不得被单元测试伪装为页面可达；同时复核响应与页面不暴露 tenantId、逻辑删除和审计字段，结论直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-040-021-系统菜单详情管理员入口验收报告.md`
最小回滚：回退本 Issue 新增的前端详情 API、只读详情交互及其测试和控制器权限测试；不回退既有后端生产详情逻辑，无 schema 或数据回滚，不删除任何数据。
目标：
- 在 `frontend-admin/src/api/modules/system.ts` 增加类型化的菜单详情 GET 调用，接收 `number | string` ID 并返回既有 `SysMenuVO`。
- 在既有 admin-only `/system/permissions` 页面增加“查看详情”最小入口，从完整菜单树选择一个目标后按需请求详情，并在只读交互中展示名称、类型、父节点、路径、组件、权限码、图标、排序、状态和可见性。
- 补齐前端 API/页面测试与后端控制器权限正负样本；复用既有 `SysMenuServiceTest` 的跨租户与不存在目标证据，后端生产 Controller、Service、Entity、Mapper 保持不变。
非目标：
- 不实现菜单列表新入口、编辑、创建或删除改造、批量操作、拖拽排序、角色授权、动态路由生成或完整菜单 CRUD。
- 不新增页面路由、侧栏项、角色、权限码、数据库 migration 或种子数据，不修改任何已应用 migration。
- 不修改后端生产授权或详情语义，不返回 tenantId、逻辑删除、审计字段或 children，不连接生产数据库、不发布生产。
允许修改：
- `frontend-admin/src/api/modules/system.ts`
- `frontend-admin/src/api/modules/__tests__/system-modules.test.ts`
- `frontend-admin/src/pages/system/permissions/index.vue`
- `frontend-admin/src/pages/system/permissions/__tests__/index.test.ts`
- `backend/src/test/java/com/cgcpms/system/SysMenuControllerTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-021-系统菜单详情管理员入口验收报告.md`
禁止修改：
- `backend/src/main/java/com/cgcpms/system/controller/SysMenuController.java`
- `backend/src/main/java/com/cgcpms/system/service/SysMenuService.java`
- `backend/src/main/java/com/cgcpms/system/entity/**`
- `backend/src/main/java/com/cgcpms/system/mapper/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/types/system.ts`
- `frontend-admin/src/router/**`
- 除“允许修改”中明确列出的权限清单页面及其测试外，不得修改其他系统管理页面
- `deploy/**`、`.github/**`、生产凭据、生产数据库、仓库外文件与受保护私有目录
验收标准：
- 前端 API 测试精确断言详情调用为 `GET /system/menus/{id}`，无请求体、无额外参数，并以既有 `SysMenuVO` 作为返回类型；不得复制第二套菜单详情类型。
- 页面入口仅对 ADMIN/SUPER_ADMIN 可见，普通用户无论无权限或仅持有 `system:menu:query` 均不可见；详情目标来自完整菜单树，目录、菜单、按钮和无权限码节点均可选择。
- 选择目标后只发起一次详情请求，加载期间不显示旧目标数据；成功只读展示 `SysMenuVO` 白名单字段，失败保留目标并显示可理解错误，不伪造成功、不泄露上一次详情。
- 后端 ADMIN/SUPER_ADMIN 或持有 `system:menu:query` 的请求返回 200；既非管理员也无该权限返回 403，未登录返回 401。前端 admin-only 可见性与后端细粒度授权必须分别留证，不得把两者混为同一边界。
- 详情响应不包含 `tenantId`、`deletedFlag`、`createdBy`、`updatedBy`、`createdAt`、`updatedAt`、`remark` 或 `children`；跨租户和不存在记录继续统一 `MENU_NOT_FOUND`，由现有 `SysMenuServiceTest` 与补充后的 `SysMenuControllerTest` 提供裁决证据。
- 收口时必须引用 `docs/backlog/current-issues.json` 与该条目的 `sourceRefs`；全部验证通过后移除 `A-01-MENU-DETAIL`，或在未完全通过时以证据更新其唯一状态/分类，禁止 Done 后仍保留原 OPEN；同时更新 A-01 分类守恒计数、Ready/current-focus/project-map/evolution-decision。
- 归档报告必须统计新增后续项、关闭后续项和后续项净变化；所有发现项须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。
- Ready lint、后端专项、前端专项、类型检查和 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-021`
- `cd backend; .\mvnw.cmd "-Dtest=SysMenuControllerTest,SysMenuServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/system-modules.test.ts src/pages/system/permissions/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

收口结果（2026-07-13）：
- 既有 admin-only 权限清单页已增加“查看详情”入口；目标来自完整菜单树，目录、菜单、按钮和无权限码节点均可选择。选择新目标时先清空旧数据，成功只读展示 `SysMenuVO` 白名单字段，失败保留目标并显示错误。
- 前端类型化 API 固定调用无请求体、无额外参数的 `GET /system/menus/{id}`；仅 ADMIN/SUPER_ADMIN 显示入口。后端生产 Controller、Service、Entity、Mapper、migration 均未修改，原 ADMIN/SUPER_ADMIN 或 `system:menu:query` 授权与租户 fail-close 保持不变。
- Ready lint 通过且无告警；后端专项43/43、前端专项26/26、前端类型检查、目标 ESLint 与 `git diff --check` 通过。浏览器从 dev-login 进入 `/system/permissions`，选中按钮节点后正确展示10个白名单字段，控制台无 error/warn。
- 首次 pnpm 并行依赖竞争归类为 `tool_config` 并经串行恢复复验；首次浏览器命中主工作树旧前端容器归类为环境前置，切换当前 worktree 本地 Vite、稳定等待180秒后验收通过，并已恢复原前端容器。
- `A-01-MENU-DETAIL` 已从 `docs/backlog/current-issues.json` 移除；A-01 守恒更新为有用户入口227、前端调用无独立页面58、内部4、需补入口21、需要确认11，总数321。
- 正式报告：`docs/quality/ISSUE-040-021-系统菜单详情管理员入口验收报告.md`；新增后续项0、关闭后续项1（`A-01-MENU-DETAIL`）、后续项净变化-1。

### ISSUE-040-022：系统菜单平铺列表管理员入口与权限边界

优先级：P0
任务性质：缺口修复
类型：系统管理 / 前端只读入口 / 权限 / 租户隔离 / 响应字段收敛
状态：Done
来源锚点：`docs/backlog/current-issues.json` 的 `A-01-MENU-LIST`；其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；并引用 `docs/product-intelligence/project-map.md` 与 `docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-13-02`
存量标记：[stock:A-01-MENU-LIST]
关联产品目标：在既有 admin-only 权限清单页让管理员可达已实现的平铺菜单列表能力，关闭 A-01 中 `GET /system/menus`“后端存在但同方法用户入口不可达”的最小缺口；本切片只提供只读列表，不扩展为菜单管理平台。
补货排序依据：该条目是知识图谱给出的 P0、OPEN、STILL_APPLICABLE、非阻塞叶子项；当前 Ready 为空，按 `PI-2026-07-13-02` 的结构化存量优先规则准入。当前代码仍只有 `getMenuTree` 消费树接口，未发现平铺 GET 调用；Ready/Done/Blocked 中无 `[stock:A-01-MENU-LIST]`。
阻塞证据：`SysMenuController.flatList` 已提供 `GET /system/menus`，授权为 ADMIN/SUPER_ADMIN 或 `system:menu:query`，`SysMenuService.getFlatList` 已按当前租户过滤并按 orderNum 排序，`SysMenuControllerTest` 已验证管理员成功、未登录拒绝与响应字段白名单，`SysMenuServiceTest` 已验证租户隔离；但 `frontend-admin/src/api/modules/system.ts` 没有平铺列表 GET，既有 `/system/permissions` 页面只通过树接口生成权限码表且未提供平铺菜单列表入口。
解除条件：既有 `/system/permissions` 管理员页面提供按需加载的只读“菜单列表”入口，精确调用现有平铺 GET 并展示当前租户完整菜单节点；后端细粒度授权、无权限拒绝、租户隔离、前端 admin-only 可见性及安全字段白名单均有自动化证据。
Migration：不需要
依赖：复用既有 `/system/permissions` 页面、`SysMenuVO`、`SysMenuController.flatList`、`SysMenuService.getFlatList`、`UserContext`、admin-only `/system` 路由和当前菜单测试夹具；不新增数据库对象、菜单种子、路由、侧栏项或权限码。
风险等级：中
运行态要求：自动化必须在本地 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，并从 dev-login 进入 `/system/permissions` 验证平铺列表加载、空态和失败态。任一入口不通先归类为环境前置并执行 runtime refresh，稳定等待 180 秒后复验；不得连接或发布生产，本 Issue 不写入或删除测试数据。
Reviewer要求：独立复核后端 ADMIN/SUPER_ADMIN 与 `system:menu:query` 授权未放宽、无角色且无权限请求为 403、未登录为 401；前端入口必须保持父路由 admin-only，仅 ADMIN/SUPER_ADMIN 可见，普通用户即使单独持有 `system:menu:query` 也不得被前端测试伪装为页面可达；同时复核列表只使用既有 `SysMenuVO`，不暴露 tenantId、逻辑删除、审计字段或 children，结论直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-040-022-系统菜单平铺列表管理员入口验收报告.md`
最小回滚：回退本 Issue 新增的前端平铺列表 API、只读列表交互及其测试和控制器权限测试；不回退既有后端生产列表逻辑，无 schema 或数据回滚，不删除任何数据。
目标：
- 在 `frontend-admin/src/api/modules/system.ts` 增加类型化的平铺菜单列表 GET 调用，返回既有 `SysMenuVO[]`，不复制第二套菜单类型。
- 在既有 admin-only `/system/permissions` 页面增加“菜单列表”只读入口；打开时按需调用 `GET /system/menus`，展示名称、类型、父节点、路径、权限码、排序、状态与可见性，并覆盖加载、空列表和失败保留入口状态。
- 补齐前端 API/页面测试与后端控制器权限正负样本；复用既有服务测试证明当前租户过滤和排序，后端生产 Controller、Service、Entity、Mapper 保持不变。
非目标：
- 不替换或重构现有菜单树、权限码清单、新建、删除、详情或角色绑定交互，不实现菜单编辑、批量操作、拖拽排序、动态路由生成或完整菜单 CRUD。
- 不新增页面路由、侧栏项、角色、权限码、数据库 migration、种子数据或后端生产接口，不修改任何已应用 migration。
- 不放宽前端 admin-only 边界或后端权限，不返回敏感实体字段，不连接生产数据库、不发布生产。
允许修改：
- `frontend-admin/src/api/modules/system.ts`
- `frontend-admin/src/api/modules/__tests__/system-modules.test.ts`
- `frontend-admin/src/pages/system/permissions/index.vue`
- `frontend-admin/src/pages/system/permissions/__tests__/index.test.ts`
- `backend/src/test/java/com/cgcpms/system/SysMenuControllerTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-022-系统菜单平铺列表管理员入口验收报告.md`
禁止修改：
- `backend/src/main/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/types/**`
- `deploy/**`
- `.github/**`
验收标准：
- 前端 API 测试精确断言列表调用为 `GET /system/menus`，无请求体、无额外参数，并返回既有 `SysMenuVO[]`；页面测试覆盖 ADMIN/SUPER_ADMIN 可见、普通用户不可见、普通用户仅持 `system:menu:query` 仍不可见、按需单次加载、空态、失败态和重新打开后的确定性刷新。
- 管理员从现有权限清单页可打开完整平铺菜单列表；列表不得只保留带权限码节点，目录、菜单、按钮及空权限码节点均可显示，并展示名称、类型、父节点、路径、权限码、排序、状态与可见性；现有树、新建、删除、详情和角色绑定行为不回退。
- 后端 ADMIN、SUPER_ADMIN 或持有 `system:menu:query` 的请求返回 200；既非管理员也无该权限返回 403，未登录返回 401。前端 admin-only 可见性与后端细粒度授权必须分别留证，不得混为同一边界。
- 列表按既有服务规则仅返回当前租户数据并按 orderNum 升序；响应及页面不包含 tenantId、deletedFlag、createdBy、updatedBy、createdAt、updatedAt、remark 或 children，复用 `SysMenuServiceTest` 的租户与排序证据并补齐 `SysMenuControllerTest` 的权限正负样本。
- 收口必须引用 `docs/backlog/current-issues.json` 及该条目的 `sourceRefs`；全部验证通过后移除 `A-01-MENU-LIST`，或在未完全通过时以证据更新其唯一状态/分类，禁止 Done 后仍保留原 OPEN；通过时同步把 A-01 守恒更新为有用户入口 228、前端调用但无独立页面 58、内部/集成/运维 4、需补入口 20、待废弃 0、需要确认 11、总数 321，并更新 Ready、current-focus、project-map、evolution-decision。
- 归档报告必须统计新增后续项、关闭后续项和后续项净变化；所有发现项须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。
- Ready lint、后端专项、前端专项、类型检查和 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-022`
- `cd backend; .\mvnw.cmd "-Dtest=SysMenuControllerTest,SysMenuServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/system-modules.test.ts src/pages/system/permissions/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-040-023：系统菜单修改管理员入口与树结构约束

优先级：P0
任务性质：缺口修复
类型：系统管理 / 前端修改入口 / 权限 / 租户隔离 / 菜单树一致性
状态：Done
来源锚点：`docs/backlog/current-issues.json` 的 `A-01-MENU-UPDATE`；其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；并引用 `docs/product-intelligence/project-map.md` 与 `docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-13-02`
存量标记：[stock:A-01-MENU-UPDATE]
关联产品目标：在既有管理员权限清单页让管理员可达已经实现的菜单修改能力，关闭 A-01 中后端存在但用户不可达的最小缺口；本切片只提供受控单菜单修改，不扩展为菜单管理平台。
补货核验：本轮按 P0、OPEN、STILL_APPLICABLE、非阻塞叶子项条件有界选择该任务；当前代码仍有受 `system:menu:edit` 保护的更新接口，但前端 API 与权限清单页没有同方法入口，页面测试还明确断言不存在 updateMenu；Ready、Done、Blocked 均无相同存量标记或同接口任务。
阻塞证据：`SysMenuController.update` 已提供 `PUT /system/menus/{id}` 并要求 ADMIN、SUPER_ADMIN 或 `system:menu:edit`；`SysMenuService.update` 仅校验目标属于当前租户后直接按实体更新，没有复用创建路径的菜单类型和父节点约束，也没有阻止自父节点、后代成父或带子节点菜单改为 BUTTON；当前前端只具备菜单树、平铺列表、详情、新建和删除调用，没有修改 API 或交互。
解除条件：既有 admin-only 权限清单页提供受控单菜单修改入口，精确调用现有 PUT；后端保持授权和租户 fail-close，并补齐更新场景的类型、父节点、环和子节点约束；权限码及其他业务字段可确定性回读，全部正负样本通过。
Migration：不需要
依赖：复用既有权限清单页、菜单树、菜单详情、SysMenuVO、创建表单字段、更新 Controller、SysMenuService、UserContext 和现有菜单测试夹具；不新增路由、菜单种子、表、权限码或后端接口。
风险等级：高
运行态要求：自动化必须在本地 local/dev/test 执行；浏览器验收前通过 8080 health、5173 首页和 dev-login 三项 health gate，失败先按环境前置类刷新并稳定等待 180 秒。真实修改闭环只能操作本 Issue 创建的唯一测试菜单；如需删除测试记录，必须同时满足 dev/test/demo、数据库 host 为 localhost/127.0.0.1 和 ALLOW_TEST_DATA_RESET marker。不得连接或发布生产。
Reviewer要求：独立复核后端 ADMIN、SUPER_ADMIN 与 `system:menu:edit` 授权未放宽，无权限为 403、未登录为 401、跨租户或不存在目标 fail-close；前端入口保持父路由 admin-only，仅 ADMIN/SUPER_ADMIN 可见，普通用户即使单独持有 `system:menu:edit` 也不能被前端测试伪装为页面可达；同时复核 tenantId、id、审计字段不可由客户端覆盖，权限码更新不改角色绑定，树结构约束覆盖自环、后代环、跨租户父节点、BUTTON 父节点和带子节点改 BUTTON；结论直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-040-023-系统菜单修改管理员入口验收报告.md`
最小回滚：回退本 Issue 新增的前端修改 API、类型、表单交互、后端更新约束及对应测试；无 schema 回滚。运行态测试菜单只能在测试数据重置三项前置满足时删除，否则保留证据并判定未完成。
目标：
- 在既有 admin-only 权限清单页增加“修改菜单”入口，从完整菜单树选择目标并加载当前详情，提交最小业务字段后刷新菜单树、平铺列表和详情。
- 在前端系统 API 增加类型化的菜单修改调用，精确发送 `PUT /system/menus/{id}`，不发送 ID、租户、审计、逻辑删除或 children 字段。
- 在后端更新路径补齐菜单类型、父节点、环和子节点约束，并保持当前租户、路径 ID 与权限边界不变。
非目标：
- 不实现批量修改、拖拽排序、动态路由生成、角色授权重构、权限码注册平台或完整菜单 CRUD 重构。
- 不新增页面路由、侧栏项、角色、权限码、数据库 migration 或种子数据，不修改已应用 migration。
- 不修改现有创建、删除、详情、平铺列表或角色绑定语义，不连接生产数据库、不发布生产、不自动 push。
允许修改：
- `frontend-admin/src/api/modules/system.ts`
- `frontend-admin/src/api/modules/__tests__/system-modules.test.ts`
- `frontend-admin/src/types/system.ts`
- `frontend-admin/src/pages/system/permissions/index.vue`
- `frontend-admin/src/pages/system/permissions/__tests__/index.test.ts`
- `backend/src/main/java/com/cgcpms/system/service/SysMenuService.java`
- `backend/src/test/java/com/cgcpms/system/SysMenuControllerTest.java`
- `backend/src/test/java/com/cgcpms/system/SysMenuServiceTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-023-系统菜单修改管理员入口验收报告.md`
禁止修改：
- `backend/src/main/java/com/cgcpms/system/controller/SysMenuController.java`
- `backend/src/main/java/com/cgcpms/system/entity/**`
- `backend/src/main/java/com/cgcpms/system/mapper/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 前端 API 测试精确断言修改调用为 `PUT /system/menus/{id}`，载荷只含 parentId、menuName、menuType、path、component、perms、icon、orderNum、status、visible 中的业务字段，不含 id、tenantId、审计字段、逻辑删除字段或 children。
- 修改入口仅对 ADMIN/SUPER_ADMIN 可见；普通用户无论无权限或仅持 `system:menu:edit` 均不可见。目标来自完整菜单树，打开后加载当前详情；提交成功刷新树、平铺列表和详情，失败保留表单与目标并显示错误，不伪造成功或泄露上一目标数据。
- 后端 ADMIN、SUPER_ADMIN 或持有 `system:menu:edit` 的请求可更新当前租户菜单；既非管理员也无该权限返回 403，未登录返回 401。路径 id 覆盖请求体 id，tenantId 及审计字段不可由客户端覆盖，跨租户和不存在目标统一返回 `MENU_NOT_FOUND`。
- 更新只允许 DIR、MENU、BUTTON；根节点归一为 parentId=0，非根父节点必须存在于当前租户且不能是 BUTTON；目标不能以自身或任一后代为父节点，存在子节点的目标不能改为 BUTTON。所有拒绝路径均不得产生部分更新。
- 合法更新后名称、类型、父节点、路径、组件、权限码、图标、排序、状态和可见性可由详情与树确定性回读；修改权限码不新增、删除或重写任何角色菜单关系，既有创建、删除、详情、列表与角色绑定测试不回退。
- 收口必须引用 `docs/backlog/current-issues.json` 及该条目的 `sourceRefs`；全部验证通过后移除 `A-01-MENU-UPDATE`，未完全通过则以证据更新其唯一状态或分类，禁止 Done 后仍保留原 OPEN。通过时同步更新 A-01 守恒为有用户入口229、前端调用但无独立页面58、内部/集成/运维4、需补入口19、待废弃0、需要确认11、总数321，并回写 Ready、current-focus、project-map、evolution-decision。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；所有发现项本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。
- Ready lint、后端专项、前端专项、类型检查和 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-023`
- `cd backend; .\mvnw.cmd "-Dtest=SysMenuControllerTest,SysMenuServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/system-modules.test.ts src/pages/system/permissions/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-040-024：间接费执行分摊用户入口与金额安全边界

优先级：P0
任务性质：缺口修复
类型：间接费分摊 / 高风险执行入口 / 权限 / 金额 / 幂等 / 租户隔离
状态：Done
来源锚点：正式唯一问题载体 `docs/backlog/current-issues.json` 的 `A-01-OVERHEAD-EXECUTE`；其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；并引用 `docs/product-intelligence/project-map.md` 与 `docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-13-02`
存量问题键：[stock:A-01-OVERHEAD-EXECUTE]
关联产品目标：让具备明确执行权限的财务/管理用户从既有成本台账安全触发已实现的月度间接费分摊，关闭“后端能力存在但用户不可达”的 A-01 叶子缺口；该切片同时补齐暴露写入口前不可缺少的金额守恒、重复执行与租户硬门禁。
核验结论：问题仍存在——当前 `POST /overhead-allocation/execute` 与 `overhead:execute` 后端授权存在，前端仅有 `OVERHEAD_ALLOCATION` 展示字典，未检出 API 调用、按钮、页面或路由入口；用户价值明确——授权用户可在成本台账完成可审计的月度分摊而非依赖后台调用；验收可执行——现有 Controller、Service、成本台账页及后端/前端专项入口均真实存在；依赖已满足——复用现有规则、成本项、成本汇总、认证租户、成本台账和操作审计，不依赖规则 CRUD 入口；去重通过——该精确 marker 未出现在 Ready/Done/Blocked，候选为 `OPEN`、`STILL_APPLICABLE`、`blocking=false` 的 P0 叶子且无子项。
补货排序依据：知识图谱最近采集成功、失败数为 0，Git cursor 与当前 `HEAD=d9b53e156e6b5f415a7cc1b0385f363ac0970baf` 一致；该候选是用户给定的唯一 P0 结构化存量叶子。CodeGraph 命中后端路由与服务但未召回前端入口，已按工具召回不足用精确 `rg` 和只读 codebase-memory 交叉确认；当前服务直接写 `cost_item`，既有测试又只覆盖空数据/任意 2xx，故不得只增加按钮。
阻塞证据：现有执行逻辑未排除既有 `OVERHEAD_ALLOCATION` 成本，逐项目四舍五入也没有尾差归集；`cost_item` 的既有来源唯一键与当前固定 `sourceItemId=0` 不能表达同一规则、月份下的多项目明细，重复或并发调用缺少可持久化的租户+规则+期间幂等事实。开放用户入口前必须在同一最小切片内关闭这些真实金额与一致性风险。
解除条件：既有 `/cost/ledger` 页面提供受权限控制的月度执行入口；后端对完整月份、认证租户、权限、金额守恒、重复/并发执行、事务回滚和操作审计形成自动化证据；真实本地角色链路能完成确认、执行、回读且不产生跨租户或重复成本。
Migration：需要
依赖：复用 `OverheadAllocationController`、`OverheadAllocationService`、现有分摊规则和三种分摊依据、`CostItem`/`CostSummaryService`、`UserContext`、`@AuditedOperation`、既有 `/cost/ledger` 页面与 `cost:ledger:query` 路由；新增最小版本化执行事实以持久化租户+规则+月份幂等键，不修改任何已应用 migration，也不先行实现其他间接费规则入口。
风险等级：高
运行态要求：仅在 local/dev/test 与 localhost/127.0.0.1 数据库执行；浏览器验收前依次通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard`，任一失败先按环境前置类 runtime refresh 并稳定等待 180 秒后复验；从 dev-login 进入 `/cost/ledger` 验证。涉及测试数据删除或重置时必须同时满足非生产环境、本机数据库和 `.codex-autopilot/ALLOW_TEST_DATA_RESET`，且只清理本 Issue 唯一测试数据；各 checkpoint 继续检查 stop/pause，不连接或发布生产。
Reviewer要求：独立 Reviewer 必须按高风险金额写入裁决，复核 `overhead:execute` 未被 `cost:ledger:query` 替代或放宽、ADMIN/SUPER_ADMIN 旁路与显式权限正样本、无权限 403、未登录 401、客户端 tenantId 注入无效；复核完整月口径、来源金额排除本次/既有分摊、分摊合计按分精确守恒、同租户同规则同月份串行重试与并发只形成一组有效成本、不同租户互不碰撞、异常全事务回滚、操作审计可追踪；复核新 migration 不改既有脚本、不放宽通用成本来源唯一性，并对前端二次确认、失败保留与成功回读给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-024-间接费执行分摊用户入口与金额安全边界验收报告.md`
最小回滚：回退本 Issue 的成本台账入口、执行 API 适配、执行事实代码与新版本 migration；migration 回滚只停用新入口并保留已生成的审计/成本事实，不删除生产或历史成本数据，不回退既有规则、成本台账、成本汇总和定时任务。
目标：
- 在既有成本台账页增加“执行间接费分摊”入口，仅 ADMIN/SUPER_ADMIN 或同时具备页面访问权与 `overhead:execute` 的用户可见可用；选择目标月份后显示期间、影响说明与不可重复生成提示，二次确认才调用既有执行路由。
- 将前后端 `period` 统一为目标自然月月末；非法日期、非月末和未来月份 fail-close，客户端传入 tenantId 不得影响认证租户。
- 新增最小、持久化的租户+规则+月份执行事实，并让成本项来源键可区分该次执行下的项目；相同租户/规则/月份的重复或并发触发幂等返回，不新增第二组有效成本，定时任务与手工入口复用同一门禁。
- 计算来源金额时排除 `OVERHEAD_ALLOCATION`，保持现有 EQUAL、DIRECT_LABOR、CONTRACT_AMOUNT 语义；按分处理尾差，使同一规则当月各项目分摊额之和精确等于可分摊来源金额，零金额安全跳过。
- 执行事实、成本明细和成本汇总刷新位于同一事务；任一步失败不得留下半完成 run、部分成本或伪成功响应，并记录可定位租户、期间与执行人的操作审计。
- 收口时用正式报告记录权限、租户、金额、幂等、并发、回滚、浏览器和 migration 证据；验证通过后从 `docs/backlog/current-issues.json` 移除或正式关闭 `A-01-OVERHEAD-EXECUTE`，同步更新 A-01 守恒、Ready 状态、Current Focus 和项目地图；若改变优先级判断再更新迭代决策。
非目标：
- 不实现间接费规则列表、新建、修改或删除入口，不扩成独立间接费平台、总账、结账、自动冲销、批量补算或历史重算。
- 不改变现有三种分摊依据的业务定义、定时任务频率、成本汇总口径或其他成本来源生成策略；不以本切片宣称完整成本核算平台完成。
- 不新增页面路由、侧栏菜单、角色或默认角色授权；显式 `overhead:execute` 的角色分配需另有业务授权。
- 不修改任何已应用 migration，不删除或改写历史成本，不放宽通用 `cost_item` 来源唯一约束，不连接生产数据库、不发布生产、不自动 push。
允许修改：
- `backend/src/main/java/com/cgcpms/overhead/**`
- `backend/src/test/java/com/cgcpms/overhead/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/api/modules/cost.ts`
- `frontend-admin/src/pages/cost/ledger.vue`
- `frontend-admin/src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/**`
禁止修改：
- `backend/src/main/java/com/cgcpms/accounting/**`
- `backend/src/main/java/com/cgcpms/payment/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/pages/system/**`
- `deploy/**`
- `.github/**`
- `archive/v1.0/private/**`
验收标准：
- 页面入口、API 调用和前端单测证明按钮仅对 ADMIN/SUPER_ADMIN 或同时拥有 `cost:ledger:query` 与 `overhead:execute` 的用户可达；普通成本查询用户看不到且不能触发，失败不关闭确认态或伪报成功，成功后刷新成本台账。
- 控制器专项证明未登录 401、无执行权限 403、显式 `overhead:execute` 与管理员成功；请求中的其他 tenantId 不改变认证租户，跨租户规则、项目、成本和执行事实均不可读写。
- 服务专项以至少两条规则、两个活跃项目和可整除/不可整除金额证明来源排除既有分摊、零金额跳过、三种现有分摊依据不回退、每条规则分摊合计按分等于来源金额。
- 相同租户/规则/月末的连续两次和并发两次执行只保留一条执行事实与一组有效成本；不同月份、规则、项目和租户不会错误去重，定时与手工并发走同一幂等门禁。
- 注入任一成本写入或汇总刷新失败时，执行事实、成本明细与汇总全部回滚；接口返回失败且审计不记录伪成功。
- 新增 MySQL/H2 版本化 migration，版本唯一且实际测试启动可应用；不修改 V1～V148，不改变既有通用成本来源唯一键语义，新增幂等唯一键能阻断并发重复。
- 浏览器经 health gate 和 dev-login 进入 `/cost/ledger`，完成月份选择、二次确认、成功回读、重复执行幂等提示与无权限不可见性验证；不得把环境前置失败定性为业务失败。
- 收口报告给出新增后续项、关闭后续项和净变化；`A-01-OVERHEAD-EXECUTE` 只有在全部证据通过后才从唯一台账移除/关闭，任何金额、权限、租户、幂等或迁移证据不足均判不通过并正式写入唯一阻塞载体。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=OverheadAllocationControllerTest,OverheadAllocationServiceTest,MigrationIntegrityTest,MigrationVersionUniquenessTest,FlywayMySqlSmokeTest" test`
- `cd frontend-admin; pnpm test:unit -- src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/cost.ts src/pages/cost/ledger.vue src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `pwsh -NoProfile -File scripts/check-sql-safety.ps1`
- `git diff --check`

执行收口（2026-07-14，BC）：
- 成本台账入口、月末参数、认证租户覆盖、执行事实、按分尾差、来源排除、串行/并发幂等、定时/手工共门禁、事务回滚和审计已形成实现及专项证据；本地 MySQL 与 H2 均实际应用 V149。
- health gate 与 dev-login 通过；真实浏览器完成月份/月末展示、二次确认、成功回读、重复执行幂等提示，并以临时仅具 `cost:ledger:query` 的普通用户证明页面可达但执行入口 DOM 数量为 0。验收专用零金额规则、执行事实、临时角色关系、成本与审计已按固定 ID/时间窗精确清理并读回零残留；控制器专项同时证明无执行权限直接调用返回 403。
- `A-01-OVERHEAD-EXECUTE` 已从 `docs/backlog/current-issues.json` 移除；A-01 守恒更新为有用户入口230、前端调用但无独立页面58、内部/集成/运维4、需补入口18、待废弃0、需要确认11，共321。
- 正式报告：`docs/quality/ISSUE-040-024-间接费执行分摊用户入口与金额安全边界验收报告.md`。新增后续项0、关闭后续项1（`A-01-OVERHEAD-EXECUTE`）、后续项净变化-1；未新增阻塞项，独立 Reviewer 已对绑定 diff 给出 `PASS`，发现项0。

### ISSUE-040-025：系统角色新建管理员入口与权限边界

优先级：P0
任务性质：缺口修复
类型：系统管理 / 角色新建入口 / 权限 / 租户隔离 / 越权防护
状态：Done
来源锚点：正式唯一问题载体 `docs/backlog/current-issues.json` 的 `A-01-ROLE-CREATE`；其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；并引用 `docs/product-intelligence/project-map.md` 与 `docs/product-intelligence/evolution-decision.md` 的 `PI-2026-07-13-02`；candidateEvidenceHead=8cf39ab3b46ffbf9036db62593a01322cb4c3923
存量问题键：[stock:A-01-ROLE-CREATE]
关联产品目标：在既有管理员角色管理页让管理员可达已实现的角色新建能力，关闭 A-01 中 `POST /system/roles` 后端存在但用户不可达的最小叶子缺口；创建结果必须是当前租户内、无菜单权限的普通自定义角色，后续授权继续走既有独立入口。
核验结论：问题仍存在——当前前端系统 API 只有 `GET /system/roles` 与 `PUT /system/roles/{id}/menus`，角色页只有列表和编辑权限，没有 POST 新建调用或交互；用户价值明确——管理员无需后台调用即可建立待授权角色；验收可执行——既有角色页、Controller、Service、菜单集合、前后端专项测试和 dev-login 均有真实入口；依赖已满足——复用现有角色表、租户上下文、角色列表和独立菜单授权链，不依赖角色修改或删除入口；去重通过——唯一台账中该项为 `OPEN`、`STILL_APPLICABLE`、`blocking=false` 的 P0 叶子，Ready/Done/Blocked 无相同 marker 或同接口任务。
检索交叉核验：CodeGraph 与 codebase-memory 命中后端 POST 路由、角色服务、前端角色列表和既有菜单授权函数，但没有命中前端创建函数；codebase-memory 的精确文本补查出现零召回，已按工具召回不足使用当前分支精确 `rg` 和直接文件读取确认。当前 HEAD 与候选取证提交一致。
阻塞证据：`SysRoleController.create` 已由 ADMIN/SUPER_ADMIN 或 `system:role:add` 保护并返回新角色 ID，但前端无入口；同时现有实体直绑允许客户端提交 roleType、dataScope 和 roleLevel，Service 仅补 status/tenantId，未拒绝保留角色编码或低等级角色伪造。开放用户入口前必须在同一切片内证明新角色初始菜单集合为空，并拒绝通过创建载荷伪造 ADMIN/SUPER_ADMIN、SYSTEM 或 roleLevel 0/1。
解除条件：既有 admin-only 角色管理页提供受控新建入口，精确调用现有 POST 并刷新列表；后端保持 `system:role:add` 授权和认证租户边界，将创建对象收敛为普通自定义角色且初始菜单集合为空；管理员、显式权限、无权限、未登录、保留角色伪造与跨租户注入的正负样本全部通过。
Migration：不需要
依赖：复用既有 `/system/roles` 页面、`SysRoleVO`、系统 API request、`SysRoleController.create`、`SysRoleService.create`、`UserContext`、角色表默认值和现有角色/菜单测试夹具；创建与菜单授权保持两步边界，不新增表、路由、侧栏、权限码、角色种子或后端接口。
风险等级：高
运行态要求：自动化只在 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，任一失败先归类为环境前置并 runtime refresh，稳定等待 180 秒后复验；从 dev-login 进入 `/system/roles` 验证新建、失败保留与成功刷新。运行态只创建本 Issue 唯一测试角色；删除或重置测试数据必须同时满足 dev/test/demo、数据库 host 为 localhost/127.0.0.1 和 `.codex-autopilot/ALLOW_TEST_DATA_RESET`，否则不得清理并应判定收口未完成。不得连接或发布生产。
Reviewer要求：独立 Reviewer 按高风险权限变更复核 ADMIN/SUPER_ADMIN 与 `system:role:add` 授权未放宽、无权限 403、未登录 401、前端入口仍受父路由 admin-only 且仅 ADMIN/SUPER_ADMIN 可见；复核请求中的 id、tenantId、roleLevel、SYSTEM 类型、保留角色编码和菜单集合不能造成提权，创建结果固定为普通自定义角色、认证租户、允许的数据范围且 menuIds 为空；复核角色编码唯一性、失败不伪报成功、既有编辑权限链不回退，并对绑定 diff 给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-025-系统角色新建管理员入口与权限边界验收报告.md`
最小回滚：回退本 Issue 的前端创建 API、类型、角色页表单、Service 创建收敛及对应测试；不删除已有角色或权限数据，不回退既有角色列表和菜单授权能力，无 schema 或数据迁移回滚。
目标：
- 在前端系统 API 增加类型化角色创建调用，只发送 roleCode、roleName、status 和 dataScope 等允许的普通角色业务字段，精确调用 `POST /system/roles` 并返回角色 ID。
- 在既有 admin-only 角色管理页增加“新建角色”交互，仅 ADMIN/SUPER_ADMIN 可见；校验必填和长度，提交失败保留表单并显示后端错误，成功关闭表单、刷新列表且不自动授予菜单权限。
- 收敛后端创建路径，拒绝 ADMIN/SUPER_ADMIN 等保留编码、SYSTEM 类型和 roleLevel 0/1 伪造；tenantId 必须来自认证上下文，新建角色固定为普通可编辑等级，省略值使用安全默认，初始角色菜单集合为空。
- 补齐 Controller/Service 与前端 API/页面正负样本，证明 `system:role:add`、未授权拒绝、租户注入无效、权限集合为空和既有菜单授权入口不回退。
非目标：
- 不实现角色详情、修改、删除、复制、批量创建或完整角色 CRUD 重构，不新增页面路由、侧栏项、角色或默认授权。
- 不把创建与菜单授权合并为新的原子接口，不在创建时自动授予任何菜单，不放宽或重构既有 `system:role:assign`、高危权限 diff、自角色保护或超级管理员保护规则。
- 不修改用户分配角色、菜单管理、认证/JWT、动态路由、数据库 schema 或种子数据，不修改已应用 migration，不连接生产数据库、不发布生产、不自动 push。
允许修改：
- `frontend-admin/src/api/modules/system.ts`
- `frontend-admin/src/api/modules/__tests__/system-modules.test.ts`
- `frontend-admin/src/types/system.ts`
- `frontend-admin/src/pages/system/roles/index.vue`
- `frontend-admin/src/pages/system/roles/__tests__/index.test.ts`
- `backend/src/main/java/com/cgcpms/system/service/SysRoleService.java`
- `backend/src/test/java/com/cgcpms/system/SysRoleControllerTest.java`
- `backend/src/test/java/com/cgcpms/system/SysRoleServiceTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-025-系统角色新建管理员入口与权限边界验收报告.md`
禁止修改：
- `backend/src/main/java/com/cgcpms/system/controller/**`
- `backend/src/main/java/com/cgcpms/system/entity/**`
- `backend/src/main/java/com/cgcpms/system/mapper/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/pages/system/permissions/**`
- `deploy/**`
- `.github/**`
- `AGENTS.md`
- `AGENTS.override.md`
验收标准：
- 前端 API 测试精确断言创建调用为 `POST /system/roles`，载荷不含 id、tenantId、roleLevel、menuIds、审计或逻辑删除字段；页面测试覆盖 ADMIN/SUPER_ADMIN 可见、普通用户不可见、普通用户即使仅持 `system:role:add` 仍因 admin-only 页面不可见、必填/长度校验、重复编码失败保留、成功关闭并刷新。
- 管理员或仅持 `system:role:add` 的已认证请求可创建安全的普通自定义角色；既非管理员也无该权限返回 403，未登录返回 401。前端 admin-only 可见性与后端细粒度授权分别留证，不混为同一边界。
- 请求体中的 id 和 tenantId 不能覆盖服务端事实；roleCode 为 ADMIN/SUPER_ADMIN、roleType 为 SYSTEM、roleLevel 为 0/1 或其他等价提权载荷均 fail-close，且不得插入角色或角色菜单关系。合法创建固定 roleType=CUSTOM、roleLevel=2，status 省略时为 ENABLE，dataScope 省略时为 SELF。
- 合法创建返回 ID，并能从当前租户列表回读 roleCode、roleName、CUSTOM、ENABLE、SELF 和空 menuIds；另一租户不可见。创建不得隐式调用菜单授权或写入 sys_role_menu，后续仍只能通过既有编辑权限入口和 `system:role:assign` 边界授权。
- 重复角色编码仍返回 `ROLE_CODE_EXISTS` 且无部分写入；既有角色列表、编辑权限、高危系统权限拒绝、自角色保护、超级管理员保护和授权审计测试不回退。
- 收口必须引用 `docs/backlog/current-issues.json` 及该条目的 `sourceRefs`；全部验证通过后移除 `A-01-ROLE-CREATE`，未完全通过则用证据更新其唯一状态或分类，禁止 Done 后仍保留原 OPEN。通过时同步更新 A-01 守恒为有用户入口 231、前端调用但无独立页面 58、内部/集成/运维 4、需补入口 17、待废弃 0、需要确认 11、总数 321，并回写 Ready、current-focus、project-map；若差距或优先级判断变化，再更新 evolution-decision。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；所有发现项必须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。权限、租户、初始权限集合、越权拒绝或运行态清理证据不足时判不通过并正式写入唯一阻塞载体。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint 和 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-025`
- `cd backend; .\mvnw.cmd "-Dtest=SysRoleControllerTest,SysRoleServiceTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/system-modules.test.ts src/pages/system/roles/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/system.ts src/types/system.ts src/pages/system/roles/index.vue src/pages/system/roles/__tests__/index.test.ts`
- `git diff --check`

执行收口（2026-07-14，desktop-native 金丝雀）：
- 前端在既有 admin-only 角色页提供 ADMIN/SUPER_ADMIN 可见的新建入口，精确调用 `POST /system/roles`；后端保留 ADMIN/SUPER_ADMIN 或 `system:role:add` 授权，并将认证租户、CUSTOM、roleLevel=2、ENABLE/SELF 安全默认和空菜单集合固化为服务端事实。
- Ready lint、后端38项、前端26项、类型检查、目标 ESLint 与差异检查通过；真实浏览器完成唯一角色创建、重复编码失败保留表单、成功列表回读，数据库读回 tenantId=0/CUSTOM/ENABLE/SELF/roleLevel=2/空菜单，并在 localhost dev 库按固定角色 ID 精确清理至零残留。
- 独立 Reviewer 首轮识别复核摘要仍绑定旧恢复哈希且混入提前生成的收口草稿，归类为 `tool_config/证据绑定与阶段边界错误`；撤出草稿并重跑全部命令后，Reviewer 对实现差异哈希 `6bec1258709186652b431968716e2f8ec99c822d24c4bf812a2a2d6fa3f3ac59` 给出 `PASS`，findings=无。
- `A-01-ROLE-CREATE` 已从 `docs/backlog/current-issues.json` 移除；A-01 守恒更新为有用户入口231、前端调用但无独立页面58、内部/集成/运维4、需补入口17、待废弃0、需要确认11，共321。
- 正式报告：`docs/quality/ISSUE-040-025-系统角色新建管理员入口与权限边界验收报告.md`。新增后续项0、关闭后续项1（`A-01-ROLE-CREATE`）、后续项净变化-1；未新增阻塞项。

### ISSUE-040-026：系统角色详情管理员入口与权限边界

优先级：P0
任务性质：缺口修复
类型：系统管理 / 角色详情入口 / 只读权限 / 租户隔离
状态：Done
来源锚点：项目知识图谱当前问题 `A-01-ROLE-DETAIL`；正式唯一问题载体为 `docs/backlog/current-issues.json`，其 `sourceRefs` 为 `docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`；并引用当前 `docs/product-intelligence/project-map.md` 与 `docs/product-intelligence/evolution-decision.md` 的 A-01 治理决策；candidateEvidenceHead=4e3b55341e6ed86511310be093f2bf6c206adcf5
存量问题键：[stock:A-01-ROLE-DETAIL]
关联产品目标：在既有 admin-only 角色管理页让管理员可达已实现的角色详情能力，关闭 A-01 中 `GET /system/roles/{id}` 后端存在但用户不可达的最小叶子缺口；详情保持只读，不扩展角色修改、删除或菜单授权。
核验结论：问题仍存在——后端 `SysRoleController.getById` 已受 ADMIN/SUPER_ADMIN 或 `system:role:query` 保护，`SysRoleService.getById` 已按当前租户 fail-close 并返回 `SysRoleVO`，但前端系统 API 和角色页没有详情调用或交互；用户价值明确——管理员可核对角色编码、类型、状态、数据范围、菜单集合与创建时间；验收可执行——现有角色列表、详情 Controller/Service、前端页面与测试夹具均可复用；依赖已满足——不需要 schema、migration、菜单、路由或新权限码；去重通过——Ready/Done/Blocked 无相同 marker 或同接口任务。
候选对比：同级 P0 叶子还包括角色修改与角色删除；本任务是只读详情，复用现有接口且不引入写入、解绑、系统角色保护或破坏性确认风险，能以更小范围先关闭可验证缺口，因此本轮优先。
检索交叉核验：CodeGraph 命中后端 Controller/Service 与部分测试，但未完整召回当前角色页，归类为工具召回不足；`codebase-memory-mcp` 命中 Controller、Service、admin-only 路由、现有 API/页面测试关系；最终以当前分支 `rg`、直接文件读取和正式测试为准。
阻塞证据：`GET /system/roles/{id}` 已返回当前租户角色详情且跨租户/不存在目标统一 `ROLE_NOT_FOUND`，前端目前只有角色列表、新建与菜单授权调用，表格操作菜单只有“编辑权限”，用户无法触发详情请求或查看只读字段。
解除条件：角色表格提供 ADMIN/SUPER_ADMIN 可见的“查看详情”入口，按所选 ID 调用详情 API；加载时不显示上一角色旧数据，失败保留所选目标并显示错误，成功展示允许字段；后端授权、401/403、租户 fail-close、字段白名单和既有新建/菜单授权能力均不回退。
Migration：不需要
依赖：复用现有 `/system/roles` 页面、`SysRoleVO`、系统 API request、`SysRoleController.getById`、`SysRoleService.getById`、`UserContext`、角色列表和既有前后端测试夹具。
风险等级：高
运行态要求：自动化只在 local/dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，任一失败先归类为环境前置并 runtime refresh，稳定等待180秒后复验；从 dev-login 进入 `/system/roles`，对现有角色验证详情入口、加载、字段展示、关闭后重新打开和失败不残留旧数据。详情为只读，不创建、修改或删除测试数据。
Reviewer要求：独立 Reviewer 按权限与租户边界复核 ADMIN/SUPER_ADMIN 页面可见性、父路由 admin-only、后端 ADMIN/SUPER_ADMIN 或 `system:role:query` 授权、无权限403、未登录401、跨租户/不存在 `ROLE_NOT_FOUND`；复核前端详情请求无 body/params、目标切换先清空旧数据、错误不伪报成功，展示字段不包含 tenantId、审计人、逻辑删除或其他敏感持久化字段；对绑定 diff 给出 PASS/NEEDS_REPAIR。
归档报告：`docs/quality/ISSUE-040-026-系统角色详情管理员入口与权限边界验收报告.md`
最小回滚：回退本 Issue 的前端详情 API、角色页详情交互、对应测试与报告/载体回写；不回退角色列表、新建与菜单授权能力，不涉及 schema 或数据回滚。
目标：
- 在前端系统 API 增加类型化 `getRoleDetail(roleId)`，精确调用无 body、无 params 的 `GET /system/roles/{id}` 并返回 `SysRoleVO`。
- 在既有 admin-only 角色管理页的行操作中增加“查看详情”，仅 ADMIN/SUPER_ADMIN 可见；打开新目标时先清空旧详情，成功展示角色名称、编码、类型、状态、数据范围、菜单 ID 集合和创建时间，失败保留目标并显示后端错误。
- 补齐 Controller/API/页面正负样本，证明 `system:role:query`、无权限403、未登录401、跨租户/不存在 fail-close、只读字段白名单和既有新建/菜单授权交互不回退。
非目标：
- 不新增或修改后端生产接口、Service、实体、Mapper、数据库迁移、菜单、路由、权限码或默认角色授权。
- 不实现角色修改、删除、复制、导出、批量操作或在详情弹窗内授权菜单。
- 不连接生产数据库、不发布生产、不 push。
允许修改：
- `frontend-admin/src/api/modules/system.ts`
- `frontend-admin/src/api/modules/__tests__/system-modules.test.ts`
- `frontend-admin/src/pages/system/roles/index.vue`
- `frontend-admin/src/pages/system/roles/__tests__/index.test.ts`
- `backend/src/test/java/com/cgcpms/system/SysRoleControllerTest.java`
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/quality/ISSUE-040-026-系统角色详情管理员入口与权限边界验收报告.md`
禁止修改：
- `backend/src/main/java/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/main/resources/db/migration-h2/**`
- `frontend-admin/src/router/**`
- `frontend-admin/src/types/system.ts`
- AutoPilot 控制面脚本、配置、规则和状态文件之外的正式源码
验收标准：
- 前端 API 测试精确断言 `GET /system/roles/{id}` 无 body、无 params；页面测试覆盖 ADMIN/SUPER_ADMIN 可见、普通用户即使持有 `system:role:query` 仍因 admin-only 页面不可见、加载新目标清空旧数据、失败保留目标且不展示旧详情、成功展示允许字段、关闭后重新打开重新请求。
- Controller 专项覆盖 ADMIN/SUPER_ADMIN 与仅持 `system:role:query` 的已认证请求成功、无权限403、未登录401、跨租户/不存在返回 `ROLE_NOT_FOUND`；响应不暴露 tenantId、createdBy、updatedBy、deletedFlag 等持久化敏感字段。
- 详情只读交互不调用创建、更新、删除或菜单授权；既有角色列表、新建和编辑权限测试不回退。
- 收口必须引用 `docs/backlog/current-issues.json` 及该条目的 `sourceRefs`；全部验证通过后移除 `A-01-ROLE-DETAIL`，未完全通过则用证据更新其唯一状态或分类。通过时同步更新 A-01 守恒为有用户入口232、前端调用但无独立页面58、内部/集成/运维4、需补入口16、待废弃0、需要确认11、总数321，并回写 Ready、current-focus、project-map；若差距或优先级判断变化，再更新 evolution-decision。
- 归档报告统计新增后续项、关闭后续项和后续项净变化；所有发现项必须本轮修复、唯一载体承接或有依据关闭，存在悬空项不得通过。
- Ready lint、后端专项、前端专项、类型检查、目标 ESLint 和 `git diff --check` 全部通过；首次失败先按 tool_config、环境前置、真实质量/安全分类并复验。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-026`
- `cd backend; .\mvnw.cmd "-Dtest=SysRoleControllerTest" test`
- `cd frontend-admin; pnpm test:unit -- src/api/modules/__tests__/system-modules.test.ts src/pages/system/roles/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm exec eslint src/api/modules/system.ts src/pages/system/roles/index.vue src/pages/system/roles/__tests__/index.test.ts`
- `git diff --check`
