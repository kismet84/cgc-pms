# Ready Issues

## Ready 队列状态

v1.0 队列已封存到 [backlog 快照](../archive/v1.0/backlog-snapshot/ready-issues.md)。

当前队列暂无待实施 Ready；`ISSUE-037-012` 已完成，等待下一轮产品情报补货。

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
