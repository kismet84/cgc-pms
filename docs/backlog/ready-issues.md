# Ready Issues

## Ready 队列状态

v1.0 队列已封存到 [backlog 快照](../archive/v1.0/backlog-snapshot/ready-issues.md)。

`ISSUE-040-034`、`ISSUE-040-035`、`ISSUE-040-036`、`ISSUE-040-037`、`ISSUE-040-038` 已完成；本次 `启动迭代-5` 已达到 5 条上限。

`ISSUE-040-039`、阻塞修复 `ISSUE-047-001`、`ISSUE-040-040`～`ISSUE-040-055`、阻塞修复 `ISSUE-047-002` 与 `ISSUE-047-003` 已完成；`启动迭代-20` 已完成 20/20。站内通知的租户/用户隔离、已读幂等、SSE与通知铃契约已完成回归证明。

### ISSUE-048-005：本地开发 MySQL 迁移基线重建

优先级：P1
任务性质：运维治理
类型：MySQL / Flyway / 本地开发基线 / 备份恢复 / 运行态
状态：Ready
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
