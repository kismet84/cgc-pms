# Ready Issues

## Ready 队列状态

v1.0 队列已封存到 [backlog 快照](../archive/v1.0/backlog-snapshot/ready-issues.md)。

当前 Ready 队列为空；`ISSUE-040-001`、`ISSUE-040-012～018` 已完成，`ISSUE-037-021-A/B/C` 已在第40条 M0 归一化并关闭。

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
解除条件：既有权限清单页提供受控的新建菜单入口，前端只向 `POST /system/menus` 发送最小合法载荷，后端保持精确授权并拒绝非法树关系；创建成功后刷新树，权限、租户和树结构正负样本全部通过。
Migration：不需要
依赖：复用既有 `/system/permissions` 管理员页面、`getMenuTree`、`SysMenuController.create`、`SysMenuService`、`UserContext` 与当前 `sys_menu` 表；不新增路由、菜单种子、表或权限码。
风险等级：高
运行态要求：自动化先在本地 dev/test 执行；浏览器验收前必须通过 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` health gate，并从 dev-login 进入 `/system/permissions`；不得连接生产，创建或删除真实测试菜单前还必须满足 dev/test/demo、数据库 host 为 localhost/127.0.0.1 且存在 `.codex-autopilot/ALLOW_TEST_DATA_RESET`。
Reviewer要求：独立复核 `system:menu:add` 与 ADMIN/SUPER_ADMIN 授权未放宽、无权限负样本为 403、租户 ID 不能由客户端覆盖、非根父节点必须属于当前租户且不能是 BUTTON、菜单类型仅允许 DIR/MENU/BUTTON、前端隐藏不替代后端门禁；结论直接用于通过/不通过裁决。
归档报告：`docs/quality/ISSUE-040-019-系统菜单新建管理员入口验收报告.md`
最小回滚：回退本 Issue 新增的前端创建交互/API/类型和后端树约束及对应测试；无 schema 回滚。若运行态产生测试菜单，仅在满足测试数据重置三项前置后删除该唯一测试记录。
目标：
- 在既有 `/system/permissions` 页面增加“新建菜单”最小入口与表单，复用当前菜单树选择父节点；只有 ADMIN/SUPER_ADMIN 或持有 `system:menu:add` 的用户可见并可提交。
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
- ADMIN/SUPER_ADMIN 或持有 `system:menu:add` 的请求可创建；既非管理员也无该权限的请求返回 403；前端可见性与后端授权分别有证据。
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
- 既有权限清单页已增加受控“新建菜单”入口；ADMIN、SUPER_ADMIN 或 `system:menu:add` 可见，其他身份隐藏，后端原授权注解未修改且无权限请求专项返回 403。
- 前端新增类型化 `POST /system/menus`，只发送菜单业务字段；成功后关闭表单并刷新菜单树，失败时保留表单且不刷新成功态。
- `SysMenuService.create` 统一根节点 `parentId=0`，拒绝非法类型、不存在/跨租户/BUTTON 父节点，并继续以当前 `UserContext` 覆盖客户端租户。
- 后端专项 35/35、前端专项 13/13、前端类型检查与 `git diff --check` 通过；首次控制器测试失败已确认是测试 JWT 默认密钥过短的 `tool_config` 前置，补入测试专用密钥后原命令复验通过。
- `A-01-MENU-CREATE` 已从 `docs/backlog/current-issues.json` 移除；A-01 守恒更新为有用户入口225、前端调用无独立页面58、内部4、需补入口23、需要确认11，总数321。
- 正式报告：`docs/quality/ISSUE-040-019-系统菜单新建管理员入口验收报告.md`；新增后续项0、关闭后续项1（`A-01-MENU-CREATE`）、后续项净变化-1。
