# Ready Issues

## Ready 队列状态

v1.0 队列已封存到 [backlog 快照](../archive/v1.0/backlog-snapshot/ready-issues.md)。

当前队列暂空；`ISSUE-037-006` 已完成，AutoPilot 继续按产品情报机制补货。

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
