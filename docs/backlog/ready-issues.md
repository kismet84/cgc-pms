# Ready Issues

## Ready 队列状态

v1.0 队列已封存到 [backlog 快照](../archive/v1.0/backlog-snapshot/ready-issues.md)。

当前队列暂空；`ISSUE-037-005` 已完成，AutoPilot 正在刷新产品情报并继续补货。

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
