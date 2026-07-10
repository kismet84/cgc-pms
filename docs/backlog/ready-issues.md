## Ready 队列状态

- 当前有 0 条合格 Ready：`ISSUE-008-025` 已完成并转 Done；`ISSUE-008-022`、`ISSUE-008-023` 与 `ISSUE-008-024` 已完成并转 Done。
- 用户于 2026-07-10 再次触发 `启动迭代-10`，新一轮计数已初始化为 `0/10`；上一轮 `10/10` 历史仍由下列记录、`done-issues.md` 和正式质量报告保留，不计入本轮。
- `ISSUE-008-021` 已于 2026-07-10 完成正式收口，并计入 `启动迭代-10` 的第 `10/10` 个实施型 Ready Issue。
- `ISSUE-008-020` 已于 2026-07-10 完成正式收口，并计入 `启动迭代-10` 的第 `9/10` 个实施型 Ready Issue。
- `ISSUE-008-019` 已于 2026-07-10 完成正式收口，并计入 `启动迭代-10` 的第 `8/10` 个实施型 Ready Issue。
- `ISSUE-008-018：通知平台平台化缺口-M4：同告警重复通知抑制与站内信频控回归` 已于 2026-07-10 完成正式收口，并计入 `启动迭代-10` 的第 `7/10` 个实施型 Ready Issue。
- `ISSUE-008-017：通知平台平台化缺口-M3：占位渠道可见性与发送记录语义回归` 已于 2026-07-10 完成正式收口，并计入 `启动迭代-10` 的第 `6/10` 个实施型 Ready Issue。
- `ISSUE-008-016：通知平台平台化缺口-M2：状态变更通知与订阅偏好一致性回归` 已于 2026-07-10 完成正式收口，并计入 `启动迭代-10` 的第 `5/10` 个实施型 Ready Issue。
- `ISSUE-008-015：报表中心平台化缺口-M6：导出审计留痕与目录一致性回归` 已于 2026-07-10 完成正式收口，并计入 `启动迭代-10` 的第 `4/10` 个实施型 Ready Issue。
- 未证明完全无关联前，仍不并行续接其他 P2 平台化题。

## 任务来源

- 长期总任务池：`docs/backlog/cgc-pms-production-enhancement-plan.md`
- 本文件是当前执行队列，不是任务源全集。
- 当本文件没有合格 Ready Issue 时，AutoPilot 应先从长期总任务池中按 `docs/backlog/current-focus.md` 拆出最多 5 个一轮可执行 Ready Issue；拆单当轮只更新 backlog，不直接修改业务代码；连续执行模式下拆单完成后，若仍未命中 `stop.flag` / `pause.flag` 且已形成合格 Ready Issue，必须继续进入下一轮；系统允许最多 3 个完全无关联、无任何代码关联的 Ready Issue 并行，不能证明完全无关联时按串行处理。

## Ready 编写约束

- 每条新 Ready 必须显式写明 `任务性质`；可选值至少包括：`能力新增`、`缺口修复`、`回归证明`、`运维治理`。
- `回归证明` 类 Ready 只允许证明既有能力、既有配置或既有链路真实生效；不得把“证明生效”改写成“能力新增完成”或“整个平台化完成”。
- 同一能力域连续进入 `3` 条 Ready 后，继续拆同域 Ready 前必须先在 `current-focus.md` 记录候选域对比和未切换原因；连续进入 `5` 条后，必须再写清继续理由，否则不应继续同域续跑。
- 同一类非阻塞告警、观察项或建议若已连续 `3` 次在正式报告中重复出现，下一条 Ready 不得继续仅做旁路备注；必须优先拆为治理型 Ready，或在不满足执行前置时转入 `blocked-issues.md`。
- Ready 的“剩余风险”必须在收口时回流到 `ready-issues.md`、`blocked-issues.md` 或 `current-focus.md` 之一，不得只留在质量报告。

## AutoPilot 自动合并门禁

- Ready Issue 在允许修改范围内完成实现与自审后，允许自动合并；`autoPush=false`，不自动推送。
- 自动合并前必须同时满足：
  - 该 Issue 自带验证命令全部通过。
  - `git diff --check` 通过。
  - 自审结论为 PASS。
  - 已更新 iteration report。
  - 已更新 backlog 的 done/blocked 状态。
  - 合并前再次确认不存在 `.codex-autopilot/stop.flag` 或 `.codex-autopilot/pause.flag`。
- 任一门禁失败即转为 blocked，不进入人工等待态。

## 运行前置与验证命令规范

- 浏览器验收前必须先检查 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard`。
- 任一 health gate 不通，先归类为环境前置类；执行 runtime refresh，稳定等待 `180秒` 后复验，不直接判业务代码失败。
- Docker / backend / frontend 未启动或端口不通，按环境前置类处理；恢复失败再 blocked。
- PowerShell 下包含逗号的 Maven `-Dtest=` 参数必须整体加引号，避免参数拆分或 `ParserError`。
- 验证命令中的测试类、测试方法选择器、脚本入口必须先校验存在；若不存在，归类为 Ready Issue 配置问题，可做最小等价替换，但必须在正式报告写明替换前后内容与原因。

## 执行顺序建议

1. 默认先执行 `ISSUE-008-022`，完成后执行 `ISSUE-008-023`。
2. `ISSUE-008-024` 与 `ISSUE-008-022` 的代码域、测试类和业务域独立，资源允许时可双路并行；`ISSUE-008-025` 必须等待 `ISSUE-008-024` 收口。
3. 多路实现可以并行，但 iteration report、quality 归档、backlog 更新与本地 commit 必须串行，避免共享文档冲突。
4. 本轮达到 `10/10` 上限、stop/pause 或无 Ready 时，F 必须补最小总验收；未补不得视为完整收口。

## P0

## P1

### ISSUE-032-002：invoice 与 migration 全量测试红灯项目关系治理

优先级：P1
类型：后端 / 测试治理 / invoice / migration
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/plans/第32条主线-既有未解决问题统一治理任务计划书.md` M2；`docs/quality/mainline-32-m2-backend-full-test-red-triage-2026-07-09.md`
是否需要新增 migration：否；如确认必须新增表/字段，先转 Blocked 并回报人工裁决。
目标：
- 归因 `InvoiceValidationTest` 查询接口 `400` 与 `MigrationSoftDeleteBehaviorTest` 的 `发票缺少项目关系`。
- 明确是测试夹具未补项目关系、接口契约断言漂移，还是发票项目关系真实约束缺陷。
允许修改：
- `backend/src/test/java/com/cgcpms/invoice/**`
- `backend/src/test/java/com/cgcpms/MigrationSoftDeleteBehaviorTest.java`
- `backend/src/main/java/com/cgcpms/invoice/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- 已应用 Flyway migration
- 生产凭据、生产数据库连接、生产发布配置
- 放宽发票租户、项目或状态校验
验收标准：
- `InvoiceValidationTest` 三个当前失败查询用例完成分类并收敛。
- `MigrationSoftDeleteBehaviorTest#payInvoiceDeleteIsLogicalAndAllowsRecreate` 完成分类并收敛。
- 质量报告明确项目关系约束是否为真实业务口径，不能只把 `400` 改成测试期望。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceValidationTest,MigrationSoftDeleteBehaviorTest" test`
- `git diff --check`
归档报告：`docs/quality/issue-032-002-invoice-migration-full-test-red-governance.md`

### ISSUE-032-003：dashboard、purchase、revenue 全量测试红灯种子数据治理

优先级：P1
类型：后端 / 测试治理 / dashboard / purchase / revenue
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/plans/第32条主线-既有未解决问题统一治理任务计划书.md` M2；`docs/quality/mainline-32-m2-backend-full-test-red-triage-2026-07-09.md`
是否需要新增 migration：否。
目标：
- 归因 `DashboardChiefEngineerServiceTest` 的 `No value present`、`PurchaseRequestServiceTest` 的 `项目不存在`、`ContractRevenueServiceTest` 的提交断言失败。
- 优先收敛测试种子数据前置，不先改生产查询或审批逻辑。
允许修改：
- `backend/src/test/java/com/cgcpms/dashboard/**`
- `backend/src/test/java/com/cgcpms/purchase/**`
- `backend/src/test/java/com/cgcpms/revenue/**`
- `backend/src/main/java/com/cgcpms/dashboard/**`
- `backend/src/main/java/com/cgcpms/purchase/**`
- `backend/src/main/java/com/cgcpms/revenue/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- 生产凭据、生产数据库连接、生产发布配置
- 无证据修改驾驶舱、采购、收入生产口径
验收标准：
- 三个失败类当前失败项完成分类并收敛。
- 如只是种子数据问题，修复应落在测试夹具或测试前置，不改生产代码。
- 如确认生产逻辑存在真实缺陷，质量报告必须标明影响范围和阻塞等级。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=DashboardChiefEngineerServiceTest,PurchaseRequestServiceTest,ContractRevenueServiceTest" test`
- `git diff --check`
归档报告：`docs/quality/issue-032-003-dashboard-purchase-revenue-full-test-red-governance.md`

### ISSUE-032-004：Phase2 与 Phase4 历史集成链路红灯治理

优先级：P1
类型：后端 / 测试治理 / 集成链路
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/plans/第32条主线-既有未解决问题统一治理任务计划书.md` M2；`docs/quality/mainline-32-m2-backend-full-test-red-triage-2026-07-09.md`
是否需要新增 migration：否；涉及金额口径或审批状态机变更时先转 Blocked。
目标：
- 归因 `Phase2FullChainIntegrationTest` 的合同可用余额、付款审批状态链路失败。
- 归因 `Phase4IntegrationTest` 的抄送 / 矩阵审批业务对象失败。
- 在 workflow、invoice、dashboard/purchase/revenue 专项完成或有明确结论后再执行，避免重复修底层夹具问题。
允许修改：
- `backend/src/test/java/com/cgcpms/Phase2FullChainIntegrationTest.java`
- `backend/src/test/java/com/cgcpms/Phase4IntegrationTest.java`
- 必要时限于相关主链路的 `backend/src/main/java/com/cgcpms/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- 已应用 Flyway migration
- 生产凭据、生产数据库连接、生产发布配置
- 未经证据重写金额口径、审批状态机或租户隔离规则
验收标准：
- `Phase2FullChainIntegrationTest`、`Phase4IntegrationTest` 当前失败项完成分类并收敛。
- 若依赖前序专项，应在报告中引用对应结论，不重复引入临时夹具。
- 明确剩余红灯是否仍阻塞后端全量门禁。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=Phase2FullChainIntegrationTest,Phase4IntegrationTest" test`
- `git diff --check`
归档报告：`docs/quality/issue-032-004-phase-integration-full-test-red-governance.md`

### ISSUE-032-006：Mockito 动态 agent 与 Spring Boot generated password 提示治理

优先级：P1
类型：构建兼容性 / 运行提示 / 后端治理
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/plans/第32条主线-既有未解决问题统一治理任务计划书.md` M4；`docs/quality/mainline-30-core-flow-regression-acceptance-2026-07-08.md`；`docs/quality/mainline-32-m4-build-compatibility-debt-2026-07-09.md`
是否需要新增 migration：否。
目标：
- 收敛 Mockito 动态 agent 未来兼容性告警，避免未来 JDK / 安全策略升级时从 warning 演变为测试门禁失败。
- 收敛 Spring Boot generated password 开发提示，减少开发/验收日志噪音，但不重做当前 JWT / dev-login 鉴权链路。
允许修改：
- `backend/pom.xml`
- `backend/src/main/resources/application*.yml`
- `backend/src/main/java/com/cgcpms/auth/config/**`
- `backend/src/test/java/com/cgcpms/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据、生产数据库连接、生产发布配置
- 登录体系重构、权限模型重构、无证据的大范围测试框架迁移
验收标准：
- Mockito 动态 agent 告警完成最小治理或被明确降级为可接受观察项，并留下可复验命令。
- Spring Boot generated password 提示完成最小治理或被明确限定在不影响现有鉴权链路的范围内。
- 质量报告必须明确两类提示的最终分类、是否阻塞、剩余风险；无明确反证前维持非阻塞。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=AuthControllerTest,AuthServiceDevLoginTest,WorkflowCoreServiceTest" test`
- `git diff --check`
归档报告：`docs/quality/issue-032-006-build-compatibility-warning-governance.md`

### ISSUE-032-007：UI / 可访问性 / 登录页品牌 / 440px 移动端复验基线

优先级：P1
类型：前端 / 质量复验 / UI 基线
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/plans/第32条主线-既有未解决问题统一治理任务计划书.md` M6；`docs/quality/ui-consistency-audit-2026-06-26.md`；`docs/quality/mainline-32-m6-ui-quality-regression-entry-2026-07-09.md`
是否需要新增 migration：否。
目标：
- 统一复验 UI 一致性、可访问性、登录页品牌和 `440px` 移动端布局四类遗留，形成当前基线。
- 先分类为设计一致性问题、真实功能风险、环境前置问题或观察项，不直接承诺全站整改。
允许修改：
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `frontend-admin/src/**`
- `backend/**`
- `deploy/**`
- 运行环境、生产凭据、生产数据库连接、生产发布配置
- 把本轮扩大为全站 UI 重构、设计系统重写或移动端专项实现
验收标准：
- 至少覆盖合同台账、成本汇总、结算列表、项目列表、系统字典、登录页六类页面或入口。
- 至少留下一轮 `1600 x 1200` 与 `440 x 956` 的正式复验结论或明确前置阻塞。
- 明确列出 UI 一致性、可访问性、登录页品牌、`440px` 布局四类问题的“通过 / 不通过 / 观察项”。
- 不把历史截图或旧报告直接当成当前已验证事实。
验证命令：
- `git diff --check`
归档报告：`docs/quality/issue-032-007-ui-accessibility-mobile-baseline.md`

### ISSUE-032-008：覆盖率与 E2E CI 基线复验

优先级：P1
类型：测试治理 / CI 基线复验
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/plans/第32条主线-既有未解决问题统一治理任务计划书.md` M6；`docs/quality/quality-hardening-acceptance.md`；`docs/quality/mainline-32-m6-ui-quality-regression-entry-2026-07-09.md`
是否需要新增 migration：否。
目标：
- 复验后端/前端覆盖率与 E2E CI 的当前基线，区分“声明阈值”“当前测量”“CI 实际执行”三层事实。
- 在没有新基线前，不直接提高阈值，不直接硬上门禁。
允许修改：
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/**`
- `frontend-admin/**`
- `.github/workflows/**`
- 运行环境、生产凭据、生产数据库连接、生产发布配置
- 在无新基线前直接把覆盖率或 E2E 升级为硬门禁整改任务
验收标准：
- 正式报告中明确区分后端覆盖率、前端覆盖率、E2E CI 三类现状。
- 每类都要写清“当前测量是否已复验”“配置阈值是什么”“CI 是否真阻断”。
- 若因工具、环境或脚本入口导致无法复验，必须按工具配置类或环境前置类归档，不得直接判业务代码失败。
- 不把 `docs/quality/quality-hardening-acceptance.md` 的旧数字直接当作当前已验证结论。
验证命令：
- `git diff --check`
归档报告：`docs/quality/issue-032-008-coverage-e2e-baseline.md`

## P2

### ISSUE-008-022：WBS 平台化缺口-M2：计划/实际日期、进度与状态一致性回归

优先级：P2
任务性质：缺口修复
类型：WBS / 分包任务 / 后端 / 测试
状态：Done（2026-07-10；计入本轮第 1 个实施型 Ready Issue）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.4 WBS、进度计划与甘特图`；`docs/quality/issue-008-008-wbs-进度计划与甘特图.md`
依赖：无；本轮第一执行项。与 `ISSUE-008-024` 代码域独立，可双路并行。
是否需要新增 migration：否；必须复用现有 `sub_task` 字段与现有接口。若确认缺少完成目标所需的持久化字段，立即转 Blocked，不得临时新增或修改已应用 migration。
目标：
- 在现有分包任务/WBS 载体上补齐计划开始/完成、实际开始/完成、进度百分比和状态之间的最小一致性边界。
- 从共享写入/更新链路修复根因并留下回归断言，不在单个调用方重复加临时判断。
- 只声明现有 WBS 载体的一致性闭环，不表述为完整 `schedule` 平台完成。
允许修改：
- `backend/src/main/java/com/cgcpms/subcontract/**`
- `backend/src/test/java/com/cgcpms/subcontract/SubTaskControllerTest.java`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/**`
- `deploy/**`
- 新建 `schedule_*` 表、引入新排程模块、放宽租户/项目鉴权
验收标准：
- 进度百分比保持 `0~100`；无效值被明确拒绝且不落库。
- 完成态、实际完成时间和 `100%` 进度的关系遵循现有业务口径并有测试保护；不得凭 Ready 猜测新状态机。
- 计划结束早于开始、实际结束早于开始等非法时间组合有统一校验；合法的未开始/进行中/完成组合不回退。
- 现有项目、租户过滤与 WBS 编码生成断言继续通过。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SubTaskControllerTest" test`
- `git diff --check`
归档报告：`docs/quality/issue-008-022-wbs-progress-date-status-consistency.md`

### ISSUE-008-023：WBS 平台化缺口-M3：项目内 WBS 树与只读甘特展示最小落地

优先级：P2
任务性质：能力新增
类型：WBS / 甘特展示 / 前端 / 后端契约回归
状态：Done（2026-07-10；计入本轮第 2 个实施型 Ready Issue）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.4 WBS、进度计划与甘特图`；`docs/quality/issue-008-008-wbs-进度计划与甘特图.md`
依赖：必须等待 `ISSUE-008-022` 收口；不得与 `008-022` 并行修改同域文件。
是否需要新增 migration：否；只消费现有分包任务字段与接口，不新增任务依赖、基线、里程碑或变更日志表。
目标：
- 基于现有项目内任务行，提供最小只读 WBS 层级和时间条展示，让现有 WBS 编码、任务名、计划日期、实际日期、进度和状态可见。
- 优先复用现有前端组件、CSS 和已安装依赖；不得引入甘特图库或拖拽排程抽象。
- 不伪造父子关系或依赖线；现有数据没有可靠层级/依赖时，明确以 WBS 编码排序的平铺/分组降级展示。
允许修改：
- `frontend-admin/src/**` 中现有分包任务页面、API、类型和对应测试；最多 8 个前端文件
- `backend/src/test/java/com/cgcpms/subcontract/SubTaskControllerTest.java`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/java/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 新增 npm 依赖、拖拽排程、任务依赖编辑、基线管理、计划变更审计、完整 schedule 模块
验收标准：
- 用户只能看到当前项目/租户已授权任务；前端不绕过现有后端过滤。
- 空数据、缺失计划日期和跨期任务均有稳定降级展示，不出现脚本异常。
- 展示至少覆盖 WBS 编码、任务名、计划起止、实际起止、进度、状态；不把只读展示包装为完整甘特平台。
- 若新增前端测试入口，先校验测试文件/选择器存在；不存在时以 type-check、build 和最小等价既有测试收口并在报告说明。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SubTaskControllerTest" test`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-008-023-wbs-readonly-gantt-view.md`

### ISSUE-008-024：供应商评分平台化缺口-M2：交期评分范围、排序与空值一致性回归

优先级：P2
任务性质：缺口修复
类型：供应商评分 / 采购驾驶舱 / 后端 / 测试
状态：Done（2026-07-10；计入本轮第 2 个实施型 Ready Issue）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.5 供应商评分与采购增强`；`docs/quality/issue-008-009-供应商评分与采购增强.md`
依赖：无；与 `ISSUE-008-022` 代码域独立，可双路并行。
是否需要新增 migration：否；只复用现有采购订单和当前 `supplierScores` 聚合。若需要新评分事实表、质量/价格/服务字段，立即转 Blocked。
目标：
- 固化现有交期达成率的项目/权限范围、逾期判定、空供应商、零订单和同分排序语义。
- 修复应落在现有共享聚合链路，不增加第二套评分器。
- 评分仍只代表现有采购订单交期表现，不扩写为质量、价格、服务或综合供应商评级。
允许修改：
- `backend/src/main/java/com/cgcpms/dashboard/**`
- `backend/src/test/java/com/cgcpms/dashboard/DashboardMaterialRoleServiceTest.java`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/**`
- `deploy/**`
- 新建 supplier_score/询价/报价/比价/定标/黑名单/补货表
- 使用越权项目订单或伪造不存在的质量、价格、服务数据
验收标准：
- 评分只聚合当前用户可访问项目范围内的采购订单，不跨租户、不跨项目泄漏。
- 空供应商不生成伪评分；零订单不出现除零或 NaN；同分排序有稳定次序。
- 逾期未完成订单与已完成订单的口径延续现有定义，并由边界测试保护。
- 原 `testPurchaseView_SupplierScores` 与新增边界断言通过。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=DashboardMaterialRoleServiceTest" test`
- `git diff --check`
归档报告：`docs/quality/issue-008-024-supplier-delivery-score-boundaries.md`

### ISSUE-008-025：供应商评分平台化缺口-M3：采购驾驶舱评分排名可见性最小落地

优先级：P2
任务性质：能力新增
类型：供应商评分 / 采购驾驶舱 / 前端 / 契约回归
状态：Done（2026-07-10；计入本轮第 3 个实施型 Ready Issue）
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.5 供应商评分与采购增强`；`docs/quality/issue-008-009-供应商评分与采购增强.md`
依赖：必须等待 `ISSUE-008-024` 收口；不得与 `008-024` 并行修改供应商评分契约。
是否需要新增 migration：否；只展示现有 `PurchaseManagerDashboardVO.supplierScores`，不新建供应商评分数据模型。
目标：
- 在现有采购经理驾驶舱展示交期评分排名，明确订单数、逾期未完成数、交期达成率和当前评分口径。
- 复用现有驾驶舱卡片/表格样式与类型，不新增通用评分框架或图表依赖。
- 页面文案必须说明这是“采购订单交期表现”，不得展示为综合供应商评级。
允许修改：
- `frontend-admin/src/**` 中现有采购经理驾驶舱页面、类型和对应测试；最多 6 个前端文件
- `backend/src/test/java/com/cgcpms/dashboard/DashboardMaterialRoleServiceTest.java`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/java/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 新增 npm 依赖、询价/比价/定标页面、黑名单、综合评分配置器
验收标准：
- 有数据时按后端稳定顺序展示供应商、订单数、逾期数、交期达成率/评分；无数据时显示明确空态。
- 页面不自行重新计算评分，不扩展后端契约，不暴露无权限项目数据。
- 文案不把交期评分误称为综合评分；现有采购驾驶舱布局和可访问性不回退。
- 若新增前端测试入口，先校验存在；不存在时以 type-check、build 和最小等价既有测试收口并在报告说明。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=DashboardMaterialRoleServiceTest" test`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-008-025-supplier-score-ranking-visibility.md`

### ISSUE-008-021：规则治理中心平台化缺口-M3：规则侧去重时窗与重复预警抑制生效回归

优先级：P2
类型：规则治理中心 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.2 规则治理中心` 节“去重策略 / 抑制策略”；`docs/quality/issue-008-006-规则治理中心.md`；`docs/quality/issue-008-020-rule-governance-config-effectiveness.md`；`docs/quality/issue-008-018-notification-dedup-frequency-guard.md`
是否需要新增 migration：否；优先复用现有 `alert_rule_config.dedup_hours`、`AlertRuleEvaluator` 与 `AlertEvaluationServiceTest`，不新增规则治理表结构。
目标：
- 补齐 `alert_rule_config.dedup_hours` 在规则评估侧的最小生效回归，证明同一规则、同一业务对象在去重窗口内不会重复生成第二条告警，而超出窗口后仍可再次生成。
- 明确“规则侧重复告警抑制”与已完成的“通知平台分发侧抑制 / 并发幂等”边界，避免把通知发送去重当成规则治理已闭环。
- 不扩大为规则治理中心页面、规则设计器、执行日志、效果分析、外部渠道通知或新一轮 migration。
允许修改：
- `backend/src/main/java/com/cgcpms/alert/**`
- `backend/src/test/java/com/cgcpms/alert/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/**`
- `deploy/**`
- 生产凭据、生产数据库连接、生产发布配置
- 新增规则治理表、规则设计器、执行日志页、效果分析页
- 邮件、短信、企业微信、钉钉、WebSocket 等外部渠道真实接入
- 借机放宽预警域、租户、角色、项目边界或把现有规则评估链路改造成大范围通用规则引擎
验收标准：
- 至少一组后端回归证明：同一规则、同一业务对象在 `dedup_hours` 窗口内重复评估时，不会生成第二条有效告警。
- 至少一组后端回归证明：超出 `dedup_hours` 窗口或显式缩小窗口后，规则可重新生成新的告警，不会被永久抑制。
- 至少一组后端回归证明：不同规则类型、不同业务键或不同项目边界不会被错误串并，既有 `enabled`、`threshold_ratio`、`window_days`、`severity_override` 生效语义不回退。
- 不新增 migration，不修改已应用 migration；不把通知平台分发侧抑制、并发幂等或发送记录语义误包装为本 Issue 收口依据。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=AlertEvaluationServiceTest,AlertControllerTest" test`
- `git diff --check`
归档报告：`docs/quality/issue-008-021-rule-governance-dedup-suppression-window.md`

### ISSUE-008-020：规则治理中心平台化缺口-M2：阈值/窗口/严重度配置生效回归

优先级：P2
类型：规则治理中心 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.2 规则治理中心` 节“规则阈值 / 执行周期 / 抑制策略”；`docs/quality/issue-008-006-规则治理中心.md`；`docs/quality/mainline-15-m2-alert-rule-governance-acceptance-2026-07-03.md`
是否需要新增 migration：否；优先复用现有 `alert_rule_config`、`AlertRuleEvaluator` 与 `AlertEvaluationServiceTest`，不新增规则治理表结构。
目标：
- 补齐 `alert_rule_config` 中 `threshold_ratio`、`window_days`、`severity_override` 三类现有字段的最小生效回归，避免当前只有 `enabled=0` 有正式验证、其余配置字段停留在“存在但未证实生效”状态。
- 验证规则配置生效边界应落在现有告警生成链路，不新增规则设计器、规则详情页、执行日志页、效果分析页或新一轮 migration。
- 不扩大为完整规则治理中心建设，不串入通知平台、权限模型、项目数据隔离或外部渠道能力。
允许修改：
- `backend/src/main/java/com/cgcpms/alert/**`
- `backend/src/test/java/com/cgcpms/alert/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/**`
- `deploy/**`
- 生产凭据、生产数据库连接、生产发布配置
- 新增规则治理表、通用规则设计器、执行日志页、效果分析页
- 借机放宽告警域、租户、角色、项目边界或把固定字段改造成大范围可配置引擎
验收标准：
- 至少一组后端回归证明：`threshold_ratio` 调整后，金额/比例型规则的触发阈值随配置变化而变化，不再只依赖硬编码默认值。
- 至少一组后端回归证明：`window_days` 调整后，时效类规则的扫描窗口随配置变化而变化，不回退到固定默认天数。
- 至少一组后端回归证明：`severity_override` 生效后，生成的预警严重度与配置一致，且未配置时仍保留既有默认严重度。
- 不新增 migration，不修改已应用 migration；既有 `enabled`、`dedup_hours`、订阅偏好、通知分发和告警处理状态口径不回退。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=AlertEvaluationServiceTest,AlertControllerTest" test`
- `git diff --check`
归档报告：`docs/quality/issue-008-020-rule-governance-config-effectiveness.md`

### ISSUE-008-019：通知平台平台化缺口-M5：并发重复分发幂等与发送记录一致性回归

优先级：P2
类型：通知平台 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.3 通知平台` 节“同类通知不会重复轰炸 / 所有通知有发送记录”；`docs/quality/issue-008-018-notification-dedup-frequency-guard.md`
是否需要新增 migration：否；优先复用现有 `AlertNotificationDispatcher`、`AlertNotificationSendRecord`、`NotificationService` 与既有测试基座，不新增通知平台表结构。
目标：
- 补齐同一 `tenantId + alertId + targetUserId + eventType + IN_APP` 在并发重复触发时的最小幂等语义，避免串行场景已抑制、并发场景仍重复落站内信或重复记 `SENT`。
- 约束并发竞争下的发送记录一致性：允许留下明确 `SKIPPED`/抑制原因，但不能让同一并发批次产生多条有效 `SENT` 记录。
- 不扩大为模板中心、失败重试队列、全局频控配置、外部渠道真实接入或通知平台新表设计。
允许修改：
- `backend/src/main/java/com/cgcpms/alert/notification/**`
- `backend/src/main/java/com/cgcpms/notification/**`
- `backend/src/test/java/com/cgcpms/alert/**`
- `backend/src/test/java/com/cgcpms/notification/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/**`
- `deploy/**`
- 生产凭据、生产数据库连接、生产发布配置
- 邮件、短信、企业微信、钉钉、WebSocket 等外部渠道真实接入
- 模板中心、失败重试队列、全局可配置频控、权限模型重构
验收标准：
- 至少一组后端并发回归证明：同一 `alertId + targetUserId + eventType + IN_APP` 在并发重复分发时，最终只产生一条有效站内通知或一条有效 `SENT` 发送记录。
- 至少一组后端并发回归证明：被抑制的并发重复分发必须留下明确 `SKIPPED`/原因，不能误记为第二条 `SENT`。
- 至少一组后端回归证明：不同事件类型、不同告警 ID 或不同目标用户不会被错误串并，既有串行抑制语义不回退。
- 不放宽预警域、租户、角色和项目边界；既有订阅偏好、占位渠道语义、SSE 通知链路与发送记录留痕不回退。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=AlertNotificationDispatcherTest,NotificationServiceTest,AlertEvaluationServiceTest" test`
- `git diff --check`
归档报告：`docs/quality/issue-008-019-notification-concurrency-idempotency.md`

### ISSUE-008-018：通知平台平台化缺口-M4：同告警重复通知抑制与站内信频控回归

优先级：P2
类型：通知平台 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.3 通知平台` 节“同类通知不会重复轰炸”；`docs/quality/issue-008-007-通知平台.md`；`docs/quality/issue-008-016-notification-status-subscription-consistency.md`；`docs/quality/issue-008-017-notification-channel-visibility-send-record-semantics.md`
是否需要新增 migration：否；优先复用现有 `AlertLog.dedupKey`、`AlertNotificationDispatcher`、`AlertNotificationSendRecord` 与 `NotificationService`，不新增通知平台表结构。
目标：
- 补齐同一用户、同一告警、同一事件类型在短时间内被重复分发时的最小抑制语义，避免站内通知和发送记录对同一条告警连续轰炸。
- 回归“规则侧已做告警去重，但分发侧仍可能重复发送”的边界，确保通知平台最小能力从“能发、有记录”推进到“不会对同一告警重复刷屏”。
- 不扩大为邮件、短信、企业微信、钉钉真实接入，不建设模板中心、失败重试队列、全局频控配置或新表结构。
- 允许修改：
- `backend/src/main/java/com/cgcpms/alert/**`
- `backend/src/main/java/com/cgcpms/notification/**`
- `backend/src/test/java/com/cgcpms/alert/**`
- `backend/src/test/java/com/cgcpms/notification/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
- 禁止修改：
- `backend/src/main/resources/db/migration/**`
- `frontend-admin/**`
- `deploy/**`
- 生产凭据、生产数据库连接、生产发布配置
- 邮件、短信、企业微信、钉钉等外部渠道真实接入
- 通知模板中心、失败重试队列、全局可配置频控、权限模型重构
- 验收标准：
- 至少一组后端回归证明：同一 `alertId + targetUserId + eventType + IN_APP` 在一次串行操作中重复触发时，只保留一条有效站内通知，不产生重复刷屏。
- 至少一组后端回归证明：发送记录对被抑制的重复分发写明明确状态或原因，不能把被抑制记录误记为 `SENT`。
- 至少一组后端/服务回归证明：不同事件类型（如 `ALERT_CREATED` 与 `STATUS_CHANGED`）或不同告警 ID 不会被错误合并，既有通知链路不回退。
- 不放宽预警域、角色、租户和项目边界；既有订阅偏好、占位渠道跳过语义和发送记录留痕不回退。
- 验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=AlertNotificationDispatcherTest,AlertEvaluationServiceTest,AlertControllerTest" test`
- `git diff --check`
- 归档报告：`docs/quality/issue-008-018-notification-dedup-frequency-guard.md`
### ISSUE-008-017：通知平台平台化缺口-M3：占位渠道可见性与发送记录语义回归

优先级：P2
类型：通知平台 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.3 通知平台` 节“用户可配置接收渠道 / 所有通知有发送记录”；`docs/quality/issue-008-007-通知平台.md`；`docs/quality/issue-008-016-notification-status-subscription-consistency.md`
是否需要新增 migration：否；优先复用现有 `AlertNotificationDispatcher`、`AlertNotificationChannelProperties`、`AlertSubscriptionService`、`AlertController` 与预警页订阅弹窗，不新增通知平台表结构。
目标：
- 补齐当前“只有 `IN_APP` 真正可达，`EMAIL / WECHAT / SMS` 仍是未配置或未实现占位渠道”这一事实，在后端发送记录与前端订阅可见性上的最小一致性闭环。
- 回归未配置/未实现渠道的发送记录语义，确保不会被误记为已发送成功，也不会在预警页把占位渠道误展示成当前可用能力。
- 不扩大为邮件、短信、企业微信、钉钉真实接入，不新增模板中心、重试队列、频控或新表结构。
允许修改：
- `backend/src/main/java/com/cgcpms/alert/**`
- `backend/src/test/java/com/cgcpms/alert/**`
- `frontend-admin/src/pages/alert/**`
- `frontend-admin/src/api/modules/alert.ts`
- `frontend-admin/src/types/alert.ts`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据、生产数据库连接、生产发布配置
- 邮件、短信、企业微信、钉钉等外部渠道真实接入
- 通知模板中心、重试队列、频控策略、权限模型重构
验收标准：
- 至少一组后端回归证明：当订阅中请求 `EMAIL / WECHAT / SMS` 等占位渠道时，`alert_notification_send_record` 会保留明确的 `sendStatus/failReason`，不会被误记为 `SENT`。
- 至少一组后端/接口回归证明：订阅接口返回的“可选渠道/有效渠道”与当前真实可达能力一致，不把未配置或未实现渠道冒充为当前可用渠道。
- 至少一组前端回归证明：预警页订阅弹窗和订阅摘要/明细对占位渠道的展示与保存保持一致；当前仅支持站内信时，不把 `EMAIL / WECHAT / SMS` 展示成用户可直接生效的能力。
- 不放宽预警域、角色、租户和项目边界；既有“预警创建通知”和“状态变更通知”路径不回退。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=AlertNotificationDispatcherTest,AlertControllerTest,AlertEvaluationServiceTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/alert/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-008-017-notification-channel-visibility-send-record-semantics.md`

### ISSUE-008-016：通知平台平台化缺口-M2：状态变更通知与订阅偏好一致性回归

优先级：P2
类型：通知平台 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.3 通知平台` 节；`docs/quality/issue-008-007-通知平台.md`
是否需要新增 migration：否；优先复用现有 `AlertEvaluationService`、`AlertSubscriptionService`、`AlertNotificationDispatcher` 与预警页订阅弹窗，不新增通知平台表结构。
目标：
- 补齐“预警状态变更通知”这一段平台化最小闭环，确保 `PROCESSED / ARCHIVED / INVALID` 通知链路与订阅偏好保持一致。
- 回归 `notifyOnStatusChanged`、`minSeverity`、预警域和通知渠道配置在 API、分发与前端展示上的一致性。
- 不扩大为邮件、短信、企业微信、钉钉或 WebSocket / SSE 外部渠道接入，不重做通知模板中心。
允许修改：
- `backend/src/main/java/com/cgcpms/alert/**`
- `backend/src/test/java/com/cgcpms/alert/**`
- `frontend-admin/src/pages/alert/**`
- `frontend-admin/src/api/modules/alert.ts`
- `frontend-admin/src/types/alert.ts`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据、生产数据库连接、生产发布配置
- 邮件、短信、企业微信、钉钉等外部渠道接入
- 通知中心大范围重构、模板系统重构、权限模型重构
验收标准：
- 至少一组后端回归证明：当用户关闭 `notifyOnStatusChanged` 或提高 `minSeverity` 后，状态变更通知不会越过订阅偏好发送。
- 至少一组后端回归证明：允许接收状态变更通知的用户在 `PROCESSED / ARCHIVED / INVALID` 场景下能收到符合当前渠道配置的通知，且不因渠道大小写/空白差异被静默跳过。
- 订阅 API 与预警页订阅弹窗对有效配置的展示和保存保持一致；用户覆盖仍只能收窄默认范围，不能放大角色默认域/渠道边界。
- 不放宽预警域、角色、租户和项目边界；既有“预警创建通知”路径不回退。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=AlertEvaluationServiceTest,AlertControllerTest,AlertNotificationDispatcherTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/alert/__tests__/index.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-008-016-notification-status-subscription-consistency.md`

### ISSUE-008-001：经营总览报表口径与来源下钻回归

优先级：P2
类型：报表中心 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.1 报表中心` 节“项目经营总览报表”
是否需要新增 migration：否；优先复用现有 dashboard / cost / revenue / contract 数据与接口，不新增报表定义表。
目标：
- 建立项目经营总览报表的最小可用口径，确保汇总指标能追溯到现有来源单据或下钻数据。
- 不扩大为完整报表中心、异步导出平台或报表定义模型。
允许修改：
- `backend/src/main/java/com/cgcpms/dashboard/**`
- `backend/src/main/java/com/cgcpms/cost/**`
- `backend/src/main/java/com/cgcpms/revenue/**`
- `backend/src/main/java/com/cgcpms/contract/**`
- `backend/src/test/java/com/cgcpms/dashboard/**`
- `backend/src/test/java/com/cgcpms/cost/**`
- `backend/src/test/java/com/cgcpms/revenue/**`
- `backend/src/test/java/com/cgcpms/contract/**`
- `frontend-admin/src/pages/dashboard/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据、生产数据库连接、外部报表平台
- 新增通用报表中心、异步导出任务表、报表定义表
验收标准：
- 经营总览核心金额、成本、利润、付款或风险指标至少一组有稳定回归断言。
- 合法下钻能定位到现有来源数据；缺失来源时不伪造明细。
- 不放宽 dashboard 现有鉴权、租户与项目边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-008-001-management-report-source-drilldown.md`

### ISSUE-008-002：合同履约报表口径回归

优先级：P2
类型：报表中心 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.1 报表中心` 节“合同履约报表”
是否需要新增 migration：否；优先复用现有 contract / payment 数据结构。
目标：
- 回归合同履约报表的合同金额、变更金额、付款进度和履约状态口径。
- 不改合同业务语义，不新增合同履约专用表。
允许修改：
- `backend/src/main/java/com/cgcpms/contract/**`
- `backend/src/main/java/com/cgcpms/payment/**`
- `backend/src/test/java/com/cgcpms/contract/**`
- `backend/src/test/java/com/cgcpms/payment/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 合同状态机重构、生产数据库连接、通用报表中心新增表
验收标准：
- 合同金额、变更金额和付款进度之间有稳定断言。
- 履约状态与来源合同、付款记录一致，不出现静默漏算或重复累计。
- 不放宽合同查询的租户、项目和角色边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-008-002-contract-performance-report.md`

### ISSUE-008-003：成本动态汇总报表口径回归

优先级：P2
类型：报表中心 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.1 报表中心` 节“成本动态汇总报表”
是否需要新增 migration：否；优先复用现有 cost / dashboard 成本汇总口径。
目标：
- 回归目标成本、实际成本、动态成本和偏差金额的汇总口径。
- 不新增成本快照表，不扩大为完整成本报表中心。
允许修改：
- `backend/src/main/java/com/cgcpms/cost/**`
- `backend/src/main/java/com/cgcpms/dashboard/**`
- `backend/src/test/java/com/cgcpms/cost/**`
- `backend/src/test/java/com/cgcpms/dashboard/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 成本核算模型重构、生产数据库连接、通用报表中心新增表
验收标准：
- 至少覆盖一组目标成本、实际成本、动态成本、偏差金额的稳定断言。
- 汇总值与现有成本来源数据一致，不重复累计、不漏计。
- 不放宽成本数据的租户、项目和角色边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-008-003-cost-dynamic-summary-report.md`

### ISSUE-008-004：预警处理报表口径回归

优先级：P2
类型：报表中心 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.1 报表中心` 节“预警处理报表”
是否需要新增 migration：否；优先复用现有 alert 列表、状态和统计口径。
目标：
- 回归预警数量、严重度、处理状态和处理结果的报表口径。
- 不扩大为规则治理中心 M2，不新增预警规则表。
允许修改：
- `backend/src/main/java/com/cgcpms/alert/**`
- `backend/src/test/java/com/cgcpms/alert/**`
- `frontend-admin/src/pages/alert/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 规则治理中心新增表、预警规则引擎重构、生产数据库连接
验收标准：
- 预警总数、严重度分布、已读/处理状态至少一组有稳定断言。
- 报表口径与预警列表筛选结果一致。
- 不放宽预警域、角色、租户和项目边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-008-004-alert-processing-report.md`

### ISSUE-008-005：审批效率报表口径回归

优先级：P2
类型：报表中心 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.1 报表中心` 节“审批效率报表”
是否需要新增 migration：否；优先复用现有 workflow 任务、实例和状态数据。
目标：
- 回归审批效率报表的待办数量、已办数量、超时/耗时和审批状态口径。
- 不改审批状态机，不新增审批分析专用表。
允许修改：
- `backend/src/main/java/com/cgcpms/workflow/**`
- `backend/src/test/java/com/cgcpms/workflow/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 审批状态机重构、生产数据库连接、通用报表中心新增表
验收标准：
- 待办、已办、超时或平均耗时至少一组有稳定断言。
- 报表统计与审批中心列表口径一致。
- 不放宽审批数据的租户、项目、发起人和处理人边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-008-005-workflow-efficiency-report.md`

### ISSUE-004-007：合同清单金额与付款条件回归

优先级：P1
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.4 P1-1` 节“合同 → 合同清单 → 付款条件”
目标：
- 回归合同主表、合同清单和付款条件之间的金额、日期与状态口径，确保来源单据与汇总字段一致。
- 不改合同业务语义，不扩大为合同模块重构或数据库结构调整。
允许修改：
- `backend/src/main/java/com/cgcpms/contract/**`
- `backend/src/test/java/com/cgcpms/contract/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 合同业务重构、生产数据库连接
验收标准：
- 合同金额、清单合计与付款条件汇总在测试中可稳定断言。
- 状态、日期与金额字段不出现前后不一致或回写缺失。
- 不引入新的越权、跨租户或跨项目读取路径。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-004-007-contract-payment-terms-regression.md`

### ISSUE-004-008：签证变更成本与收入调整回归

优先级：P1
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.4 P1-1` 节“签证 / 变更 → 成本 / 收入调整”
目标：
- 回归签证、合同变更对成本与收入调整链路的影响，确保调整结果与来源单据一致。
- 不扩大为收入模块重构，不修改已存在的 migration。
允许修改：
- `backend/src/main/java/com/cgcpms/variation/**`
- `backend/src/main/java/com/cgcpms/contract/**`
- `backend/src/main/java/com/cgcpms/cost/**`
- `backend/src/main/java/com/cgcpms/revenue/**`
- `backend/src/test/java/com/cgcpms/variation/**`
- `backend/src/test/java/com/cgcpms/contract/**`
- `backend/src/test/java/com/cgcpms/cost/**`
- `backend/src/test/java/com/cgcpms/revenue/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 收入口径重构、生产数据库连接
验收标准：
- 签证/变更金额对成本或收入调整结果有稳定断言。
- 调整后的汇总口径与来源单据一致，不出现重复累计或漏记。
- 不放宽租户、项目或业务状态边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-004-008-variation-cost-revenue-regression.md`

### ISSUE-004-009：付款审批与财务回写状态同步回归

优先级：P1
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.4 P1-1` 节“付款申请 → 审批 → 财务回写”
目标：
- 回归付款申请从审批通过到财务回写的状态同步口径，确保付款状态、审批状态与回写结果一致。
- 不改审批状态机定义，不扩大为财务集成改造。
允许修改：
- `backend/src/main/java/com/cgcpms/payment/**`
- `backend/src/main/java/com/cgcpms/workflow/**`
- `backend/src/test/java/com/cgcpms/payment/**`
- `backend/src/test/java/com/cgcpms/workflow/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 财务生产集成、审批状态机重构、生产数据库连接
验收标准：
- 付款审批通过、驳回、财务回写后三类状态同步关系有稳定断言。
- 付款记录不会出现“审批状态已完成但财务状态未同步”之类静默不一致。
- 不新增对外部财务系统的真实连接或生产配置变更。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-004-009-payment-workflow-finance-regression.md`

### ISSUE-004-010：审批流转通知与预警联动回归

优先级：P1
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.4 P1-1` 节“审批中心 → 状态流转 → 通知 / 预警”
目标：
- 回归审批状态流转到通知与预警的联动口径，确保关键流转事件能触发正确的通知或预警信号。
- 不扩大为通知平台或预警规则中心重构。
允许修改：
- `backend/src/main/java/com/cgcpms/workflow/**`
- `backend/src/main/java/com/cgcpms/notification/**`
- `backend/src/main/java/com/cgcpms/alert/**`
- `backend/src/test/java/com/cgcpms/workflow/**`
- `backend/src/test/java/com/cgcpms/notification/**`
- `backend/src/test/java/com/cgcpms/alert/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 通知外部渠道接入、预警规则中心重构、生产数据库连接
验收标准：
- 至少覆盖审批提交、审批完成、审批异常三类流转下的通知/预警联动断言。
- 通知与预警不重复触发、不静默丢失。
- 不放宽现有权限、租户与项目边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-004-010-workflow-notification-alert-regression.md`

### ISSUE-004-011：驾驶舱汇总指标来源单据下钻回归

优先级：P1
类型：回归 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.4 P1-1` 节“驾驶舱 → 汇总指标 → 来源单据下钻”
目标：
- 回归驾驶舱汇总指标与来源单据下钻链路，确保指标可解释、来源可定位。
- 不扩大为驾驶舱重设计或新增报表中心能力。
允许修改：
- `backend/src/main/java/com/cgcpms/dashboard/**`
- `backend/src/main/java/com/cgcpms/cost/**`
- `backend/src/main/java/com/cgcpms/revenue/**`
- `backend/src/test/java/com/cgcpms/dashboard/**`
- `backend/src/test/java/com/cgcpms/cost/**`
- `backend/src/test/java/com/cgcpms/revenue/**`
- `frontend-admin/src/pages/dashboard/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 驾驶舱大改版、报表中心扩展、生产数据库连接
验收标准：
- 至少一组核心驾驶舱指标与来源单据之间存在稳定回归断言。
- 下钻入口或接口在合法场景可定位来源单据，非法或缺失来源时不伪造结果。
- 不放宽驾驶舱现有鉴权与数据边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-004-011-dashboard-source-drilldown-regression.md`

### ISSUE-006-009：上传文件 hash 生成与重复文件口径回归

优先级：P1
类型：后端 / 文件安全 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“文件 hash”
目标：
- 回归上传链路中的文件 hash 生成、持久化与重复文件判定口径，确保同内容文件不会绕过既有审计与业务绑定约束。
- 不引入外部查毒服务，不改变对象存储生产配置，不扩大为文件中心重构。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 病毒扫描服务、生产对象存储配置
验收标准：
- 后端对上传文件生成稳定 hash，并对重复上传场景给出明确、可断言的处理结果。
- 合法非重复文件上传不回退，既有业务绑定与审计逻辑不被绕过。
- 若前端需要提示，提示口径与后端返回一致，不误导为网络或权限问题。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-006-009-file-hash-duplication-guard.md`

### ISSUE-006-010：文件业务对象绑定完整性回归

优先级：P1
类型：后端 / 文件安全 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“文件必须绑定业务对象”
目标：
- 回归文件与业务对象的绑定校验，确保孤儿附件、错误业务对象或越权绑定在后端被拒绝。
- 不改变现有权限模型，不扩展为通用附件中心改造。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 权限模型重构、生产对象存储配置
验收标准：
- 后端拒绝未绑定业务对象、绑定对象不存在或绑定关系非法的上传/关联请求。
- 合法业务对象绑定路径不回退，既有下载、删除、鉴权接口保持可用。
- 前端失败提示与后端错误原因一致，不误报为成功或上传完成。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-006-010-file-biz-binding-integrity.md`

### ISSUE-006-011：发票识别记录与人工确认审计回归

优先级：P1
类型：后端 / 审计 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“识别记录、人工确认记录”
目标：
- 回归发票识别与人工确认链路的审计记录，确保识别成功、识别失败、人工确认三类关键动作均可追踪。
- 不改变发票识别业务口径，不引入外部平台依赖。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 发票识别供应商配置、外部平台接入
验收标准：
- 识别成功、识别失败、人工确认三类操作均有稳定审计断言，包含必要但不敏感的上下文字段。
- 审计记录不得泄露票据图片直链、凭据、token 或完整敏感载荷。
- 合法识别与人工确认流程不回退，前端提示与后端结果一致。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-006-011-invoice-recognition-audit-regression.md`

### ISSUE-006-012：病毒扫描预留状态与失败兜底回归

优先级：P1
类型：后端 / 文件安全 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“病毒扫描预留接口”
目标：
- 回归病毒扫描预留状态、错误码或扩展点口径，确保在未接入真实查毒服务时，系统行为明确且不误判为已完成安全扫描。
- 不新增病毒扫描服务，不连接外部文件网关，不改变生产对象存储配置。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 新增病毒扫描服务、生产对象存储配置
验收标准：
- 系统对“未扫描”“扫描失败”“未接入查毒能力”等预留状态有明确口径，不伪装为安全通过。
- 未接入真实查毒服务时，合法上传主流程仍按既定策略工作，不引入误拦截或静默放行。
- 前端提示与后端返回一致，不使用“上传成功且已安全扫描”之类误导性文案。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-006-012-virus-scan-placeholder-regression.md`

### ISSUE-007-015：访问日志 traceId/requestId 透传与响应头回归

优先级：P1
类型：后端 / 可观测性 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.7 P1-4` 节“日志字段：traceId/requestId”
目标：
- 回归访问日志中的 `traceId`、`requestId` 字段透传、生成与响应头回写，确保成功请求、匿名请求、异常请求均可稳定关联。
- 不放宽鉴权边界，不扩大为整套日志平台改造。
允许修改：
- `backend/src/main/java/com/cgcpms/**`
- `backend/src/test/java/com/cgcpms/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 外部日志平台、生产部署配置
验收标准：
- 成功请求、匿名请求、异常请求三类路径均能稳定产出 `traceId/requestId`，缺失时有明确兜底规则。
- 若请求已携带相关标识，日志与响应头透传口径一致；若未携带，系统生成值可被测试稳定断言。
- 日志与响应头不得泄露 token、cookie、密码、完整请求体等敏感内容。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-007-015-trace-request-id-regression.md`

### ISSUE-005-003：采购与收货列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节“采购列表、收货列表”
目标：
- 补强采购列表与收货列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口。
- 不扩展详情页重构、新业务字段或后端接口语义。
允许修改：
- `frontend-admin/src/pages/purchase/**`
- `frontend-admin/src/pages/receipt/**`
- `frontend-admin/src/api/modules/purchase.ts`
- `frontend-admin/src/api/modules/receipt.ts`
- `frontend-admin/src/composables/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 查询条件刷新后可回显，分页参数不丢失。
- loading、empty、error、retry 状态可达且不遮挡主要操作。
- 无权限按钮不可见的既有逻辑不回退。
验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-005-003-purchase-receipt-list-production.md`

### ISSUE-005-004：库存与领料列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节“库存列表、领料列表”
目标：
- 补强库存列表与领料列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口。
- 不改库存数量业务口径，不扩展后端接口语义。
允许修改：
- `frontend-admin/src/pages/inventory/**`
- `frontend-admin/src/pages/requisition/**`
- `frontend-admin/src/api/modules/inventory.ts`
- `frontend-admin/src/api/modules/requisition.ts`
- `frontend-admin/src/composables/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 查询条件刷新后可回显，分页参数不丢失。
- loading、empty、error、retry 状态可达且不遮挡主要操作。
- 库存数量和领料状态展示不因前端补强改变业务含义。
验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-005-004-inventory-requisition-list-production.md`

### ISSUE-005-006：预警与审批列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节“预警列表、审批列表”
目标：
- 补强预警列表与审批列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口。
- 不改预警规则、审批状态机或后端权限边界。
允许修改：
- `frontend-admin/src/pages/alert/**`
- `frontend-admin/src/pages/approval/**`
- `frontend-admin/src/stores/alert.ts`
- `frontend-admin/src/api/modules/alert.ts`
- `frontend-admin/src/api/modules/workflow.ts`
- `frontend-admin/src/composables/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 查询条件刷新后可回显，分页参数不丢失。
- loading、empty、error、retry 状态可达且不遮挡主要操作。
- 预警权限域、审批待办/已办/我发起口径不回退。
验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-005-006-alert-approval-list-production.md`

### ISSUE-005-007：列表页导出与批量操作权限态回归

优先级：P1
类型：前端 / 权限 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节“批量操作、权限按钮控制、导出权限”
目标：
- 回归核心列表页导出按钮、批量操作按钮和权限态展示。
- 不新增导出后端能力，不改变权限模型。
允许修改：
- `frontend-admin/src/pages/project/**`
- `frontend-admin/src/pages/contract/**`
- `frontend-admin/src/pages/purchase/**`
- `frontend-admin/src/pages/receipt/**`
- `frontend-admin/src/pages/inventory/**`
- `frontend-admin/src/pages/requisition/**`
- `frontend-admin/src/pages/subcontract/**`
- `frontend-admin/src/pages/settlement/**`
- `frontend-admin/src/pages/payment/**`
- `frontend-admin/src/pages/invoice/**`
- `frontend-admin/src/pages/alert/**`
- `frontend-admin/src/pages/approval/**`
- `frontend-admin/src/composables/**`
- `frontend-admin/src/stores/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 无权限时导出和批量操作入口不可见或不可用。
- 有权限时既有可用入口不回退。
- 不通过前端按钮隐藏替代后端权限校验；本轮只回归前端权限态。
验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-005-007-list-export-batch-permission.md`

### ISSUE-006-006：文件上传大小与 MIME/扩展名校验回归

优先级：P1
类型：安全 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“文件上传限制”
目标：
- 回归文件大小、MIME、扩展名三类上传限制，确保非法文件在后端被拒绝，前端提示不误导。
- 不新增病毒扫描服务，不改变生产对象存储配置。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 非白名单扩展名、伪造 MIME、超大文件均不可上传。
- 前端提示与后端拒绝原因一致，不只依赖前端校验。
- 不影响合法 PDF/Word/Excel/图片上传。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-006-006-file-upload-validation.md`

### ISSUE-006-007：私有桶默认策略与公开 URL 禁用回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“文件访问控制”
目标：
- 回归文件访问必须经鉴权接口或临时链接，不暴露公开桶直链。
- 不修改生产 MinIO 配置，不连接生产对象存储。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 文件服务不返回永久公开 URL。
- 未授权下载仍被拒绝。
- 合法授权下载路径不回退。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`
- `git diff --check`
归档报告：`docs/quality/issue-006-007-private-bucket-public-url-regression.md`

### ISSUE-006-008：文件下载临时链接过期与鉴权失败提示回归

优先级：P1
类型：安全 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“临时访问链接设置过期时间”
目标：
- 回归下载临时链接的过期时间、鉴权失败响应和前端失败提示。
- 不新增外部文件网关，不改变权限模型。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 临时链接具备明确过期时间。
- 过期或无权下载时返回可识别错误。
- 前端下载失败提示清晰，合法下载不回退。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-006-008-file-download-expiry-auth-prompt.md`

### ISSUE-007-009：JVM 与数据库连接池指标回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.7 P1-4` 节“监控指标”
目标：
- 回归 actuator 暴露 JVM 与数据库连接池关键指标，确保本地监控入口可用。
- 不引入外部监控平台，不修改生产部署配置。
允许修改：
- `backend/src/main/java/com/cgcpms/**`
- `backend/src/main/resources/**`
- `backend/src/test/java/com/cgcpms/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- health/metrics 可读取 JVM 与 datasource 相关指标。
- 指标缺失时有明确测试失败或质量报告说明。
- 不放宽鉴权边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-007-009-jvm-datasource-metrics-regression.md`

### ISSUE-007-010：备份清单脱敏与恢复演练报告模板回归

优先级：P1
类型：运维 / 文档 / 归档
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.7 P1-4` 节“备份范围、恢复演练”
目标：
- 回归备份范围清单、敏感配置脱敏要求和恢复演练报告模板。
- 不执行真实备份恢复，不读取生产凭据。
允许修改：
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
- `docs/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 清单覆盖数据库、MinIO 文件、配置文件、密钥、日志归档。
- 模板包含恢复耗时、恢复数据范围和失败原因字段。
- 文档不得包含真实密钥或生产连接串。
验证命令：
- `git diff --check`
归档报告：`docs/quality/issue-007-010-backup-scope-redaction-restore-template.md`

## 已完成/历史

### ISSUE-005-008：核心列表列宽/固定列/金额日期格式统一回归

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-005-008-list-format-column-consistency.md`

### ISSUE-007-008：预警批处理执行结果指标回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-008-alert-batch-result-metrics.md`

### ISSUE-007-009：JVM 与数据库连接池指标回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-009-jvm-datasource-metrics-regression.md`

### ISSUE-007-010：备份清单脱敏与恢复演练报告模板回归

优先级：P1
类型：运维 / 文档 / 归档
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-010-backup-scope-redaction-restore-template.md`

### ISSUE-007-011：CPU/内存/进程指标回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-011-process-memory-metrics-regression.md`

### ISSUE-007-012：Redis 健康与黑名单降级告警回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-012-redis-blacklist-observability.md`

### ISSUE-007-007：登录失败与文件失败次数指标回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-007-login-file-failure-metrics.md`

### ISSUE-007-003：操作审计字段与文件操作审计回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-003-operation-audit-file-actions.md`

### ISSUE-007-004：接口性能与错误率监控指标回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-004-actuator-metrics-regression.md`

### ISSUE-007-005：访问日志 projectId/status/duration/exception 字段回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-005-access-log-fields-regression.md`

### ISSUE-007-006：备份范围与恢复演练报告模板补强

优先级：P1
类型：运维 / 文档 / 归档
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-006-backup-scope-restore-drill-template.md`

### ISSUE-006-005：发票识别失败原因与人工确认口径回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-005-invoice-recognition-manual-confirmation.md`

### ISSUE-006-004：发票识别重复发票与付款关联回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-004-invoice-duplicate-payment-link.md`

### ISSUE-006-003：附件删除鉴权与审计回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-003-file-delete-auth-audit.md`

### ISSUE-006-002：附件下载鉴权与临时链接回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-002-file-download-auth-temp-link.md`

### ISSUE-007-001：访问日志上下文与备份清单补强

优先级：P1
类型：运维 / 文档 / 后端
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-001-access-log-backup-checklist.md`

### ISSUE-007-002：MinIO 健康指标与文件失败监控回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-002-minio-health-upload-monitoring.md`

### ISSUE-000-001：搭建本地 Codex AutoPilot 第一轮治理框架

优先级：P0  
类型：治理 / 脚本  
状态：Done  
自动合并：否  
归档报告：`docs/iterations/iteration-2026-07-08-report.md`

### ISSUE-004-002：采购收货库存数量一致性回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-002-purchase-receipt-inventory-regression.md`

### ISSUE-004-003：付款发票审批状态链路回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-003-payment-invoice-workflow-regression.md`

### ISSUE-004-004：领料出库与项目成本归集回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-004-requisition-stock-cost-regression.md`

### ISSUE-004-005：分包计量与结算状态链路回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-005-subcontract-settlement-regression.md`

### ISSUE-004-006：审批中心待办/已办/我发起统一筛选回归

优先级：P0
类型：回归 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-006-approval-workbench-regression.md`

### ISSUE-005-002：项目与合同列表页生产化补强

优先级：P1
类型：前端 / 生产化
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-005-002-project-contract-list-production.md`

### ISSUE-005-001：付款与发票列表页生产化补强

优先级：P1
类型：前端 / 生产化
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-005-001-payment-invoice-list-production.md`

### ISSUE-005-006：预警与审批列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-005-006-alert-approval-list-production.md`

### ISSUE-006-001：文件上传白名单与发票识别失败兜底

优先级：P1
类型：安全 / 后端 / 前端
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-001-file-upload-invoice-recognition.md`

### ISSUE-008-006：规则治理中心 最小可行回归

优先级：P2
类型：生产增强 / 回归 / 最小实现
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.2 规则治理中心` 节
是否需要新增 migration：否；如执行中确认必须新增表/字段，先转 Blocked 并回报人工裁决。
目标：
- 基于现有架构补齐“规则治理中心”的一轮最小可验收能力或回归断言。
- 不扩大为完整平台化改造，不连接生产环境。
允许修改：
- `backend/**`
- `frontend-admin/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- 已应用 Flyway migration
- 生产凭据、生产数据库连接、生产发布配置
- 与本 Issue 无关的大范围重构或新依赖
验收标准：
- 至少留下一个能证明核心口径或页面/接口行为的自动化验证。
- 不放宽现有鉴权、租户、项目边界。
- 更新 iteration 或 quality 报告，并同步 backlog 状态。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-008-006-规则治理中心.md`

### ISSUE-008-007：通知平台 最小可行回归

优先级：P2
类型：生产增强 / 回归 / 最小实现
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.3 通知平台` 节
是否需要新增 migration：否；如执行中确认必须新增表/字段，先转 Blocked 并回报人工裁决。
目标：
- 基于现有架构补齐“通知平台”的一轮最小可验收能力或回归断言。
- 不扩大为完整平台化改造，不连接生产环境。
允许修改：
- `backend/**`
- `frontend-admin/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- 已应用 Flyway migration
- 生产凭据、生产数据库连接、生产发布配置
- 与本 Issue 无关的大范围重构或新依赖
验收标准：
- 至少留下一个能证明核心口径或页面/接口行为的自动化验证。
- 不放宽现有鉴权、租户、项目边界。
- 更新 iteration 或 quality 报告，并同步 backlog 状态。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-008-007-通知平台.md`

### ISSUE-008-008：WBS、进度计划与甘特图 最小可行回归

优先级：P2
类型：生产增强 / 回归 / 最小实现
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.4 WBS、进度计划与甘特图` 节
是否需要新增 migration：否；如执行中确认必须新增表/字段，先转 Blocked 并回报人工裁决。
目标：
- 基于现有架构补齐“WBS、进度计划与甘特图”的一轮最小可验收能力或回归断言。
- 不扩大为完整平台化改造，不连接生产环境。
允许修改：
- `backend/**`
- `frontend-admin/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- 已应用 Flyway migration
- 生产凭据、生产数据库连接、生产发布配置
- 与本 Issue 无关的大范围重构或新依赖
验收标准：
- 至少留下一个能证明核心口径或页面/接口行为的自动化验证。
- 不放宽现有鉴权、租户、项目边界。
- 更新 iteration 或 quality 报告，并同步 backlog 状态。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-008-008-wbs-进度计划与甘特图.md`

### ISSUE-008-009：供应商评分与采购增强 最小可行回归

优先级：P2
类型：生产增强 / 回归 / 最小实现
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `8.5 供应商评分与采购增强` 节
是否需要新增 migration：否；如执行中确认必须新增表/字段，先转 Blocked 并回报人工裁决。
目标：
- 基于现有架构补齐“供应商评分与采购增强”的一轮最小可验收能力或回归断言。
- 不扩大为完整平台化改造，不连接生产环境。
允许修改：
- `backend/**`
- `frontend-admin/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- 已应用 Flyway migration
- 生产凭据、生产数据库连接、生产发布配置
- 与本 Issue 无关的大范围重构或新依赖
验收标准：
- 至少留下一个能证明核心口径或页面/接口行为的自动化验证。
- 不放宽现有鉴权、租户、项目边界。
- 更新 iteration 或 quality 报告，并同步 backlog 状态。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-008-009-供应商评分与采购增强.md`
