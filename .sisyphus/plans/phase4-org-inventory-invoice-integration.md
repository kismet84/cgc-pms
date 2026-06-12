# 第 4 阶段开发计划：组织架构 / 库存闭环 / 发票 / 待办消息 / 联调上线

## TL;DR

> **Quick Summary**: 在已完成第 1-3 阶段（合同/采购/分包/成本/付款/结算/驾驶舱/预警）的基础上，补齐五大方向：①组织架构+项目成员+字典管理 ②待办中心增强(已办/抄送)+站内消息 ③材料库存闭环 ④发票管理 ⑤全流程联调与真实部署上线。采用 TDD + 集成测试，依赖驱动分波执行。
>
> **Deliverables**:
> - 组织架构(公司/部门/岗位)CRUD + 悬空 orgId 回填 + 项目成员管理 + 项目全景页 + 字典管理 UI
> - 站内消息全栈(sys_notification + SSE 推送 + 铃铛组件) + 待办中心(我的已办/抄送我) + 预警入消息
> - 材料库存(仓库/库存台账/出入库流水/采购申请)闭环，数量型库存（不含计价）
> - 发票管理(登记/核验状态/与付款记录关联)
> - 全流程集成测试 + 真实部署(docker-compose)到目标主机 + 冒烟脚本 + 用户/管理员手册
>
> **Estimated Effort**: XL
> **Parallel Execution**: YES - 4 waves + Final Verification Wave
> **Critical Path**: 组织架构表(T1) → orgId回填(T3) → 项目成员(T11)/项目全景(T13) → 集成测试(T31) → 部署(T34) → F1-F4 → 用户okay

---

## Context

### Original Request
用户要求"编制第4阶段开发计划"。项目为建筑工程总包项目全过程管理系统(cgc-pms)。

### Interview Summary
**关键澄清**：
- 阶段编号错位：路线图文档(07_开发计划与里程碑.md)定义6阶段，但项目实际执行已压缩重排。路线图的"付款管理"(P4)与"结算/预警/驾驶舱"(P5)在项目第2-3阶段已建成。因此"第4阶段"是补齐路线图遗漏 + 平台增强，而非路线图原 P4。
- 用户选定 5 个方向（排除移动端 uni-app）：组织/字典、待办/消息、库存、发票、联调上线。

**确认决策**：
- 测试策略：**TDD + 集成测试**（后端任务 RED→GREEN→REFACTOR；关键业务链路集成测试；所有任务含 Agent QA）。
- 上线范围：**含真实部署**（执行者 Sisyphus 实际部署到服务器/预生产并冒烟验证）。
- 计划组织：**单一计划 + 依赖驱动自动排波**。

### Research Findings（explore agents + Metis）
- **已存在**：auth/RBAC/审批引擎、project/partner/contract、purchase/receipt/subcontract/measure(CRUD+审批+成本生成)、成本科目/台账/汇总/动态成本、payment app/record、variation、cost-target、settlement、五角色驾驶舱、8 条预警规则。Flyway 当前最新 **V32**。JUnit 40 测试通过。
- **组织架构**：完全不存在。无 org_* 表、无 org 包。`PmProject.orgId`、`CtContract.orgId` 是悬空外键。`SysUser` 无 orgId 字段，但前端 `SysUserVO` 已有 orgId（契约不一致）。
- **pm_project_member**：设计文档 05 L202-213 有定义，但从未建表，无实体/UI。
- **字典**：`sys_dict_type` + `sys_dict_data` 表与种子数据已存在(V5)，菜单项已存在(V6, perm `system:dict:list`)，但零后端 CRUD、零前端页面（`src/pages/system/` 不存在）。
- **库存**：`mat_receipt.warehouse_id` 列已存在(V12 L103)，但无 `mat_warehouse` 表，无 `mat_stock`，无出入库流水，无采购申请表。`receipt` 包是 CRUD+审批+成本生成的范式模板。
- **发票**：完全不存在。`PayRecord` 仅有 `voucherNo`。
- **站内消息**：完全不存在（无表/服务/WebSocket/SSE/MQ）。
- **待办**：`getMyTodos` 存在(wf_task)。"我的已办"数据在 `WfRecord.operatorId` 但无端点。"抄送我"无任何概念/表。
- **预警**：`AlertEvaluationService` 8 规则 `@Scheduled`(每30min)，`alert_log` 项目级（无 userId）。

### Metis Review（关键新发现，已纳入护栏）
- **下一个迁移号为 V33**，顺序写入，**仅** `backend/src/main/resources/db/migration/`。`database/migration/` 是 Flyway 不读取的过时镜像（V21 事故根因），**禁止改动**。
- **审计列命名已漂移**：Phase 1/2 表用 `created_at`/`updated_at`；Phase 3+ 表(V22+)用 `created_time`/`updated_time`。**新表必须统一**，并与 `MyMetaObjectHandler` 字段映射一致。
- **Flyway `validate-on-migrate: true`、`out-of-order` 默认**；种子数据 PK 冲突(V29/V30 事故，template_id=50008 重复)与重复列(V26 事故)是历史教训。
- **Redis 已部署但仅用于 token 黑名单**；无 `@EnableAsync`/线程池/WebSocket 依赖；`@EnableScheduling` 已开。
- **HttpOnly cookie 优先鉴权**（access_token, path=/api, SameSite=Strict）。多租户是 **ThreadLocal `UserContext`**（非 MyBatis-Plus 租户插件），143 处手动 `.eq(tenantId)`。`UserContext` 在过滤器 finally 清除，**异步/定时/推送线程中为空**。

### Metis 已解决的歧义（采用最小试点默认值，记入护栏）
- 组织模型：公司/部门/岗位三表，部门自引用树；岗位为 HR 头衔（不含权限语义）。org 为租户内子结构（不与 tenant 混淆）。
- orgId 回填：每租户建一个默认"根组织"，存量 `PmProject`/`CtContract` 回填到根组织；`SysUser` 新增 `org_id` 列（nullable，information_schema 守卫）。
- 站内消息：试点用 **SSE**（非 WebSocket，规避 HttpOnly cookie 握手复杂度）；持久化 `sys_notification` 表。
- 抄送：**附加 join 表 `wf_cc`**（提交时 ad-hoc 抄送），**不改 WorkflowEngine 核心**。
- 库存：**数量型台账**（不含 FIFO/移动平均计价）；出库硬阻断负库存；并发用 `@Version` 乐观锁。
- 发票：**状态字段 + 权限端点切换**（非完整审批链）；发票↔付款记录多对一。
- 字典：**不加缓存**（与现状一致，直查 DB）。

---

## Work Objectives

### Core Objective
补齐建筑总包系统在组织架构、项目成员、字典、库存、发票、站内消息/待办中心方面的能力缺口，并完成全流程联调与真实部署上线，使系统具备试点运行条件。

### Concrete Deliverables
- Flyway V33+ 迁移：org_company/org_department/org_position、pm_project_member、SysUser.org_id、mat_warehouse/mat_stock/mat_stock_txn/mat_purchase_request、pay_invoice、sys_notification、wf_cc、相关菜单与权限种子。
- 后端模块：org、project-member、dict、inventory、invoice、notification、workflow 查询增强。
- 前端页面：组织管理、项目成员、项目全景、字典管理、库存(仓库/台账/出入库/采购申请)、发票、消息铃铛+面板、待办中心(已办/抄送 Tab)。
- 集成测试覆盖关键链路 + 真实部署 + 冒烟脚本 + 手册。

### Definition of Done
- [x] `cd backend && ./mvnw.cmd test` 全绿（含新增 TDD 单测与集成测试）
- [x] 空库从 V1 迁移到最新 V33+ 无 checksum/校验错误（`MigrationIntegrityTest` 通过）
- [x] `cd frontend-admin && pnpm build && pnpm type-check` 通过
- [x] 真实部署到目标主机后冒烟脚本全绿（各方向至少 1 个读端点 200 + flyway_schema_history 含 V33+）

### Must Have
- 组织三表 + 部门树 + 悬空 orgId 回填 + SysUser.org_id 列
- 项目成员 CRUD + 项目全景聚合页
- 字典类型/数据 CRUD + 前端管理页
- 库存：仓库/库存台账/出入库流水/采购申请，负库存硬阻断，乐观锁并发控制
- 发票：登记 + 核验状态 + 与 PayRecord 关联
- 站内消息：sys_notification + SSE 推送 + 铃铛/面板 + WorkflowEngine 生命周期挂钩（显式传 tenantId/userId）
- 待办中心：我的已办(wf_record) + 抄送我(wf_cc)
- 每个新端点的多租户隔离（list `.eq(tenantId)` + 单条 post-check）
- 真实部署 + 冒烟脚本 + 用户/管理员手册

### Must NOT Have (Guardrails)
- **禁止改动 `database/migration/`**（Flyway 不读取的过时镜像）；新迁移仅入 `backend/src/main/resources/db/migration/`，从 **V33** 顺序递增。
- **禁止** 新表使用 `created_at`/`updated_at`；**统一用 `created_time`/`updated_time`**（与 V22+ 及 `MyMetaObjectHandler` 一致）。
- **禁止** 在异步/定时/SSE 推送线程中读取 `UserContext`；tenantId/userId 必须显式传入 payload 与持久化行。
- **禁止** 引入 WebSocket（试点用 SSE）。
- **禁止** 库存计价（FIFO/移动平均）；仅数量型台账。
- **禁止** 发票 OCR/文件解析；仅手工登记。
- **禁止** 字典缓存层（`@Cacheable`/`@EnableCaching`）；直查 DB。
- **禁止** 修改 WorkflowEngine 核心来实现抄送；用附加 join 表。
- **禁止** 邮件/短信通知渠道；仅站内。
- **禁止** 新建权限框架；复用 `@PreAuthorize` + 权限码。
- **禁止** 组织架构拖拽可视化；试点仅 CRUD 树。
- **禁止** 回溯重构 Phase 1/2 的 `created_at` 历史漂移；只规范新表。
- **禁止** 多仓库调拨工作流；仅出入库 + 台账。
- 种子数据 PK 必须用预分配非重叠 ID 区间 + `INSERT IGNORE`（规避 V29/V30 冲突类）。
- 新表 `CREATE TABLE IF NOT EXISTS`；对既有表 `ALTER` 必须用 V26 的 `information_schema` 守卫。

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** - 全部由执行代理执行验证。禁止"用户手动测试/确认"类验收标准。

### Test Decision
- **Infrastructure exists**: YES（JUnit 5 + Spring Boot Test，现有 40 测试通过；前端 pnpm type-check/build）
- **Automated tests**: TDD（每个后端任务 RED→GREEN→REFACTOR）+ 关键链路集成测试
- **Framework**: JUnit 5 + Spring Boot Test（H2 local profile 为主，MySQL 8 全栈验证）
- **迁移验证**：`MigrationIntegrityTest` 风格——空库 V1→V33+ 全量迁移成功

### QA Policy
每个任务必含 agent 执行的 QA 场景（见下方 TODO 模板）。证据存 `.sisyphus/evidence/task-{N}-{slug}.{ext}`。
- **前端/UI**：Playwright（具体选择器+数据，截图证据）
- **CLI/迁移/部署**：Bash（mvnw/flyway/curl，输出证据）
- **API/后端**：Bash curl（断言状态码+响应字段）+ JUnit 集成测试
- **多租户隔离**：每个新端点必含 seed 双租户 + 跨租户访问断言空/403

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1（立即开始 — 数据库基础 + 字典后端 + 组织表）:
├── T1: Flyway V33 组织架构三表(org_company/department/position) [quick]
├── T2: Flyway V34 pm_project_member 表 + SysUser.org_id 列(守卫) [quick]
├── T3: Flyway V35 库存四表(warehouse/stock/stock_txn/purchase_request) [quick]
├── T4: Flyway V36 pay_invoice 表 [quick]
├── T5: Flyway V37 sys_notification 表 [quick]
├── T6: Flyway V38 wf_cc 表 [quick]
├── T7: Flyway V39 菜单+权限+审批模板种子(预分配ID区间) [quick]
└── T8: 审计列约定核验 + MigrationIntegrityTest 扩展(空库到V39) [deep]

Wave 2（依赖 Wave 1 — 后端核心模块，MAX PARALLEL）:
├── T9:  org 模块后端(公司/部门树/岗位 CRUD) [unspecified-high]
├── T10: 悬空 orgId 回填服务 + 根组织生成(PmProject/CtContract/SysUser) [deep]
├── T11: pm_project_member 后端 CRUD [unspecified-high]
├── T12: dict 后端(DictType/DictData CRUD) [unspecified-high]
├── T13: inventory 仓库 CRUD 后端 [unspecified-high]
├── T14: inventory 库存台账+出入库流水后端(乐观锁/负库存阻断) [ultrabrain]
├── T15: 采购申请 mat_purchase_request 后端(CRUD+审批+转PO) [deep]
├── T16: pay_invoice 后端(登记+核验状态+PayRecord关联) [unspecified-high]
├── T17: notification 后端(实体/服务/控制器/SSE端点) [deep]
└── T18: workflow 查询增强后端(我的已办 wf_record) [unspecified-high]

Wave 3（依赖 Wave 2 — 集成 + 前端，MAX PARALLEL）:
├── T19: notification 挂钩 WorkflowEngine 生命周期(显式tenant/user传递) [ultrabrain]
├── T20: wf_cc 抄送服务 + 提交时写入 + 抄送查询端点 [deep]
├── T21: 预警入消息(AlertEvaluationService→notification, 显式tenant) [unspecified-high]
├── T22: 项目全景聚合接口(合同/成本/付款/预警概况) [deep]
├── T23: 前端 组织管理页(部门树+公司/岗位) [visual-engineering]
├── T24: 前端 项目成员管理页 + project store [visual-engineering]
├── T25: 前端 项目全景页 [visual-engineering]
├── T26: 前端 字典管理页 [visual-engineering]
├── T27: 前端 库存页(仓库/台账/出入库/采购申请) [visual-engineering]
├── T28: 前端 发票管理页 [visual-engineering]
├── T29: 前端 消息铃铛+面板(SSE订阅) + SysUserVO.orgId 契约修复 [visual-engineering]
└── T30: 前端 待办中心(我的已办/抄送我 Tab) [visual-engineering]

Wave 4（依赖 Wave 3 — 联调 + 部署）:
├── T31: 关键链路集成测试(库存/发票/消息/抄送/隔离) [ultrabrain]
├── T32: 全栈 MySQL 8 验证 + 测试报告 [unspecified-high]
├── T33: 用户手册 + 管理员手册 [writing]
└── T34: 真实部署(docker-compose到目标主机)+迁移dry-run+冒烟脚本+回滚预案 [deep]

Wave FINAL（全部任务后 — 4 并行评审，再用户 okay）:
├── F1: 计划合规审计 (oracle)
├── F2: 代码质量review (unspecified-high)
├── F3: 真实手动 QA (unspecified-high)
└── F4: 范围保真核查 (deep)
→ 呈现结果 → 获取用户显式 okay

Critical Path: T1 → T2 → T10 → T11 → T22 → T25 → T31 → T34 → F1-F4 → user okay
Max Concurrent: 10（Wave 2/3）
```

### Dependency Matrix

- **T1-T7**: 依赖 None → 被 T8,T9-T20 依赖
- **T8**: 依赖 T1-T7 → 被 Wave2 启动门槛
- **T9**: 依赖 T1,T7 → 被 T10,T23 依赖
- **T10**: 依赖 T2,T9 → 被 T11,T22 依赖
- **T11**: 依赖 T2,T10 → 被 T24,T22 依赖
- **T12**: 依赖 T7(菜单) → 被 T26 依赖
- **T13**: 依赖 T3 → 被 T14,T27 依赖
- **T14**: 依赖 T3,T13 → 被 T27,T31 依赖
- **T15**: 依赖 T3,T7 → 被 T27,T31 依赖
- **T16**: 依赖 T4 → 被 T28,T31 依赖
- **T17**: 依赖 T5 → 被 T19,T21,T29 依赖
- **T18**: 依赖 None(读wf_record) → 被 T30 依赖
- **T19**: 依赖 T17 → 被 T31 依赖
- **T20**: 依赖 T6,T17 → 被 T30,T31 依赖
- **T21**: 依赖 T17 → 被 T31 依赖
- **T22**: 依赖 T10,T11 → 被 T25 依赖
- **T23-T30**: 依赖各自后端(T9-T22) → 被 T31 依赖
- **T31**: 依赖 T14,T16,T19,T20 → 被 T32,F3 依赖
- **T32**: 依赖 T31 → 被 T34 依赖
- **T33**: 依赖 Wave2/3 功能稳定 → 交付物
- **T34**: 依赖 T31,T32 → 被 F1-F4 依赖

### Agent Dispatch Summary
- **Wave 1**: T1-T7 → `quick`, T8 → `deep`
- **Wave 2**: T9/T11/T12/T13/T16/T18 → `unspecified-high`, T10/T15/T17 → `deep`, T14 → `ultrabrain`
- **Wave 3**: T19 → `ultrabrain`, T20/T22 → `deep`, T21 → `unspecified-high`, T23-T30 → `visual-engineering`
- **Wave 4**: T31 → `ultrabrain`, T32 → `unspecified-high`, T33 → `writing`, T34 → `deep`
- **FINAL**: F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

> 实现 + 测试 = 一个任务，不可拆分。每任务必含：Recommended Agent Profile + Parallelization + QA Scenarios。

- [x] 1. Flyway V33：组织架构三表（org_company / org_department / org_position）

  **What to do**:
  - 新建 `backend/src/main/resources/db/migration/V33__init_org_tables.sql`
  - `org_company`(id雪花/tenant_id/company_code/company_name/status/审计列/deleted_flag)
  - `org_department`(id/tenant_id/company_id/parent_id 自引用树/dept_code/dept_name/order_num/status/审计列)
  - `org_position`(id/tenant_id/position_code/position_name/status/审计列) —— 岗位为 HR 头衔，不含权限语义
  - 全部用 `CREATE TABLE IF NOT EXISTS`；审计列用 `created_time`/`updated_time`/`created_by`/`updated_by`（对齐 V22+ 与 MyMetaObjectHandler）

  **Must NOT do**:
  - 不写入 `database/migration/`（Flyway 不读取）
  - 不用 `created_at`/`updated_at`
  - 不加权限/角色语义到 position

  **Recommended Agent Profile**:
  - **Category**: `quick` — 单一 DDL 文件，模式明确
  - **Skills**: [] — 无领域技能需求
  - **Skills Evaluated but Omitted**: `git-master`: 提交由 Commit Strategy 统管

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与 T2-T7）
  - **Blocks**: T9, T10
  - **Blocked By**: None

  **References**:
  - Pattern: `backend/src/main/resources/db/migration/V22__init_cost_target_tables.sql` — V22+ 建表与审计列(`created_time`)范式
  - Pattern: `backend/src/main/resources/db/migration/V12__init_phase2_tables.sql:37-62` — 多表+雪花ID+tenant_id DDL 风格
  - API/Type: `backend/src/main/java/com/cgcpms/common/entity/BaseEntity.java` — 审计字段对应列名
  - WHY: 必须复制 V22+ 的 `created_time` 命名，否则 BaseEntity 自动填充字段映射失败

  **Acceptance Criteria**:
  - [ ] 文件创建：`V33__init_org_tables.sql`
  - QA Scenarios:
  ```
  Scenario: 空库迁移到 V33 成功（happy）
    Tool: Bash
    Preconditions: H2 local profile 干净库
    Steps:
      1. cd backend && ./mvnw.cmd -Dspring-boot.run.profiles=local -Dtest=MigrationIntegrityTest test
      2. 断言 Flyway 迁移日志含 "Migrating schema ... to version 33"
      3. 断言无 "checksum mismatch" / "Duplicate column"
    Expected Result: BUILD SUCCESS，三表创建
    Failure Indicators: 任何迁移失败/校验错误
    Evidence: .sisyphus/evidence/task-1-migrate.txt

  Scenario: 审计列命名正确（edge）
    Tool: Bash (grep)
    Steps:
      1. grep "created_time" V33__init_org_tables.sql → 命中
      2. grep "created_at" V33__init_org_tables.sql → 零命中
    Expected Result: 仅含 created_time/updated_time
    Evidence: .sisyphus/evidence/task-1-audit-cols.txt
  ```

  **Commit**: groups with T2-T7 → `feat(db): V33-V39 phase4 migrations`

- [x] 2. Flyway V34：pm_project_member 表 + SysUser.org_id 列

  **What to do**:
  - 新建 `V34__add_project_member_and_user_org.sql`
  - `pm_project_member`(id/tenant_id/project_id/user_id/role_code/position_name/start_date/end_date/status/审计列) —— 参考设计文档 05 L202-213
  - 对既有 `sys_user` 表 `ADD COLUMN org_id BIGINT NULL`，用 V26 的 `information_schema.columns` 守卫模式（幂等）
  - `pm_project_member` 用 `CREATE TABLE IF NOT EXISTS`

  **Must NOT do**:
  - 不无条件 `ADD COLUMN`（须 information_schema 守卫，规避 V26 类重复列事故）
  - 不改 `database/migration/`

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T10, T11
  - **Blocked By**: None

  **References**:
  - Pattern: `backend/src/main/resources/db/migration/V26__add_cost_target_id_to_cost_summary.sql` — information_schema 守卫的幂等 ALTER 模式
  - Doc: `doc/开发文档_v2.3/05_数据库设计方案_MySQL8正式版.md:202-213` — pm_project_member 字段定义
  - WHY: SysUser 加列必须幂等，否则在已应用库重跑会失败（V26 教训）

  **Acceptance Criteria**:
  - QA Scenarios:
  ```
  Scenario: 空库迁移到 V34 + sys_user.org_id 存在（happy）
    Tool: Bash
    Steps:
      1. cd backend && ./mvnw.cmd -Dtest=MigrationIntegrityTest test
      2. 断言迁移到 version 34 成功
    Expected Result: BUILD SUCCESS，pm_project_member 与 sys_user.org_id 就绪
    Evidence: .sisyphus/evidence/task-2-migrate.txt

  Scenario: ALTER 幂等（edge）
    Tool: Bash (grep)
    Steps:
      1. grep -i "information_schema" V34__*.sql → 命中
    Expected Result: 含守卫逻辑
    Evidence: .sisyphus/evidence/task-2-guard.txt
  ```

  **Commit**: groups → `feat(db): V33-V39 phase4 migrations`

- [x] 3. Flyway V35：库存四表（mat_warehouse / mat_stock / mat_stock_txn / mat_purchase_request）

  **What to do**:
  - 新建 `V35__init_inventory_tables.sql`
  - `mat_warehouse`(id/tenant_id/project_id/warehouse_code/warehouse_name/status/审计列)
  - `mat_stock`(id/tenant_id/warehouse_id/material_id/available_qty/version 乐观锁列/审计列, 唯一键 uk_warehouse_material)
  - `mat_stock_txn`(id/tenant_id/warehouse_id/material_id/txn_type[IN/OUT/ADJUST]/quantity/source_type/source_id/审计列) —— 出入库流水
  - `mat_purchase_request`(id/tenant_id/project_id/request_code/approval_status/审计列) + `mat_purchase_request_item`
  - 全部 `CREATE TABLE IF NOT EXISTS`，审计列 `created_time`/`updated_time`

  **Must NOT do**:
  - 不加计价/单价/金额字段到 stock（数量型台账，禁止 FIFO/移动平均）
  - 不加多仓调拨表

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T13, T14, T15
  - **Blocked By**: None

  **References**:
  - Pattern: `backend/src/main/resources/db/migration/V12__init_phase2_tables.sql:94-146` — mat_receipt/mat_receipt_item 多表风格，含 warehouse_id 列
  - Pattern: `V22__init_cost_target_tables.sql` — 审计列命名
  - WHY: stock 需 version 列以支持 MyBatis-Plus `@Version` 乐观锁（T14 并发控制）

  **Acceptance Criteria**:
  - QA Scenarios:
  ```
  Scenario: 空库迁移到 V35（happy）
    Tool: Bash
    Steps:
      1. cd backend && ./mvnw.cmd -Dtest=MigrationIntegrityTest test
      2. 断言迁移 version 35 成功
    Expected Result: 四表+item 表创建
    Evidence: .sisyphus/evidence/task-3-migrate.txt

  Scenario: stock 无计价列（edge）
    Tool: Bash (grep)
    Steps:
      1. grep -iE "unit_price|amount|fifo|average" 段落(mat_stock 定义) → 零命中
      2. grep "version" V35__*.sql → 命中(乐观锁列)
    Expected Result: 仅数量型 + version 列
    Evidence: .sisyphus/evidence/task-3-noprice.txt
  ```

  **Commit**: groups → `feat(db): V33-V39 phase4 migrations`

- [x] 4. Flyway V36：pay_invoice 表

  **What to do**:
  - 新建 `V36__init_invoice_table.sql`
  - `pay_invoice`(id/tenant_id/pay_record_id/pay_application_id/invoice_no/invoice_type/invoice_amount/tax_rate/tax_amount/invoice_date/verify_status[PENDING/VERIFIED/ABNORMAL]/审计列)
  - 唯一键 `uk_tenant_invoice_no`(tenant_id, invoice_no) 防重复发票号
  - `CREATE TABLE IF NOT EXISTS`，审计列 `created_time`/`updated_time`

  **Must NOT do**:
  - 不加 OCR/文件解析相关字段逻辑
  - 不建发票审批模板（核验是状态字段，非审批链）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T16
  - **Blocked By**: None

  **References**:
  - Pattern: `backend/src/main/resources/db/migration/V4__init_cost_payment_tables.sql:70-121` — pay_record/pay_application DDL，发票关联这两表（注意：以 backend/ 下为准，database/ 为过时镜像勿改）
  - Pattern: `V22__init_cost_target_tables.sql` — 审计列命名
  - WHY: invoice_no 唯一键支撑 T16 重复发票拒绝逻辑

  **Acceptance Criteria**:
  - QA Scenarios:
  ```
  Scenario: 空库迁移到 V36（happy）
    Tool: Bash
    Steps:
      1. cd backend && ./mvnw.cmd -Dtest=MigrationIntegrityTest test
      2. 断言迁移 version 36 成功
    Expected Result: pay_invoice 表+唯一键创建
    Evidence: .sisyphus/evidence/task-4-migrate.txt
  ```

  **Commit**: groups → `feat(db): V33-V39 phase4 migrations`

- [x] 5. Flyway V37：sys_notification 表

  **What to do**:
  - 新建 `V37__init_notification_table.sql`
  - `sys_notification`(id/tenant_id/user_id 接收人/title/content/biz_type/biz_id/notify_type/is_read[0/1]/read_time/审计列)
  - 索引 `idx_tenant_user_read`(tenant_id, user_id, is_read)
  - `CREATE TABLE IF NOT EXISTS`，审计列 `created_time`/`updated_time`

  **Must NOT do**:
  - 不加邮件/短信渠道字段逻辑
  - 不加 WebSocket session 表

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T17
  - **Blocked By**: None

  **References**:
  - Pattern: `V22__init_cost_target_tables.sql` — 审计列命名
  - Context: `backend/.../alert/entity/AlertLog.java` — 类似的 is_read 字段模式（但 alert 是项目级，notification 是用户级）
  - WHY: user_id + tenant_id 必须冗余存储，因 SSE 推送线程无 UserContext

  **Acceptance Criteria**:
  - QA Scenarios:
  ```
  Scenario: 空库迁移到 V37（happy）
    Tool: Bash
    Steps:
      1. cd backend && ./mvnw.cmd -Dtest=MigrationIntegrityTest test
      2. 断言迁移 version 37 成功
    Expected Result: sys_notification 表+索引创建
    Evidence: .sisyphus/evidence/task-5-migrate.txt
  ```

  **Commit**: groups → `feat(db): V33-V39 phase4 migrations`

- [x] 6. Flyway V38：wf_cc 抄送表

  **What to do**:
  - 新建 `V38__init_workflow_cc_table.sql`
  - `wf_cc`(id/tenant_id/instance_id/cc_user_id/cc_user_name/business_type/business_id/title/is_read/created_time/...) —— 附加 join 表，不改 wf_instance/wf_task
  - 索引 `idx_tenant_ccuser`(tenant_id, cc_user_id)
  - `CREATE TABLE IF NOT EXISTS`

  **Must NOT do**:
  - 不改 wf_instance / wf_task / wf_record 结构
  - 不改 WorkflowEngine 核心

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T20
  - **Blocked By**: None

  **References**:
  - Pattern: `backend/src/main/resources/db/migration/V3__init_workflow_tables.sql:123-176` — wf_task/wf_record DDL 风格（仅参考，不修改这些表；以 backend/ 下为准，database/ 为过时镜像）
  - Pattern: `V22__init_cost_target_tables.sql` — 审计列命名
  - WHY: 抄送作为附加表，避免触碰高风险 WorkflowEngine 核心

  **Acceptance Criteria**:
  - QA Scenarios:
  ```
  Scenario: 空库迁移到 V38（happy）
    Tool: Bash
    Steps:
      1. cd backend && ./mvnw.cmd -Dtest=MigrationIntegrityTest test
      2. 断言迁移 version 38 成功
    Expected Result: wf_cc 表创建
    Evidence: .sisyphus/evidence/task-6-migrate.txt
  ```

  **Commit**: groups → `feat(db): V33-V39 phase4 migrations`

- [x] 7. Flyway V39：菜单 + 权限 + 审批模板种子（预分配非重叠 ID 区间）

  **What to do**:
  - 新建 `V39__init_phase4_menu_perms.sql`
  - 新增 sys_menu 行：组织管理/项目成员/字典管理(已有菜单需核对)/库存/发票/消息中心，权限码 `org:*`、`project:member:*`、`system:dict:*`、`inventory:*`、`invoice:*`、`notification:*`、`purchase:request:*`
  - 新增采购申请审批模板（mat_purchase_request）
  - **预分配 ID 区间**：菜单用 700-799，权限/角色映射另段，审批模板 template_id 用 50010+（避开已用 50007-50009）
  - 全部 `INSERT IGNORE`（幂等，规避 V29/V30 PK 冲突类）

  **Must NOT do**:
  - 不复用任何已存在的 menu_id / template_id
  - 不无 IGNORE 直插

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T9, T12, T15
  - **Blocked By**: None

  **References**:
  - Pattern: `backend/src/main/resources/db/migration/V6__init_demo_data.sql:64` — 菜单+权限码 seed 风格（含 system/dict/index 已有项）
  - Pattern: `V32__add_submit_permissions.sql` — V32 用了 ID 600-608，新行须避开
  - Pattern: `V28/V29/V30` 审批模板 seed（template_id 50007-50009 已用）
  - WHY: ID 冲突是历史事故（V29/V30 template_id=50008 重复）；必须预分配区间

  **Acceptance Criteria**:
  - QA Scenarios:
  ```
  Scenario: 空库迁移到 V39 无 PK 冲突（happy）
    Tool: Bash
    Steps:
      1. cd backend && ./mvnw.cmd -Dtest=MigrationIntegrityTest test
      2. 断言迁移 version 39 成功，无 "Duplicate entry" / PK 冲突
    Expected Result: 菜单/权限/模板就绪
    Evidence: .sisyphus/evidence/task-7-migrate.txt

  Scenario: ID 区间不重叠（edge）
    Tool: Bash (grep)
    Steps:
      1. 提取 V39 中所有 menu_id/template_id
      2. 与 V6/V32/V28-30 比对无交集
      3. grep "INSERT IGNORE" V39__*.sql → 全部命中
    Expected Result: 零重叠 + 全 IGNORE
    Evidence: .sisyphus/evidence/task-7-ids.txt
  ```

  **Commit**: groups → `feat(db): V33-V39 phase4 migrations`

- [x] 8. 审计列约定核验 + MigrationIntegrityTest 扩展（空库 V1→V39）

  **What to do**:
  - 扩展现有 `MigrationIntegrityTest`，断言空库可从 V1 干净迁移到 V39（最新版本号）
  - 核验所有新表(V33-V39)的审计列与 `MyMetaObjectHandler` 自动填充字段名一致
  - 若发现命名漂移，修正迁移文件（在 Wave 1 内闭环）

  **Must NOT do**:
  - 不修改 Phase 1/2 历史表的 created_at（仅核验新表）
  - 不改 MyMetaObjectHandler 现有逻辑

  **Recommended Agent Profile**:
  - **Category**: `deep` — 需理解迁移链与 ORM 字段映射关系
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T1-T7 全部完成）
  - **Parallel Group**: Wave 1 末（门槛任务）
  - **Blocks**: Wave 2 启动
  - **Blocked By**: T1-T7

  **References**:
  - Pattern: `backend/src/test/java/com/cgcpms/MigrationIntegrityTest.java` — 现有迁移完整性测试（审查问题修复报告提及）
  - API/Type: `backend/.../common/handler/MyMetaObjectHandler.java`（或同名）— 自动填充字段名
  - API/Type: `backend/.../common/entity/BaseEntity.java` — @TableField 映射
  - WHY: 审计列漂移会静默破坏自动填充；必须在 Wave 1 验证

  **Acceptance Criteria**:
  - [ ] `MigrationIntegrityTest` 扩展到断言 V39
  - QA Scenarios:
  ```
  Scenario: 全量迁移 V1→V39 成功（happy）
    Tool: Bash
    Steps:
      1. cd backend && ./mvnw.cmd -Dtest=MigrationIntegrityTest test
      2. 断言 0 failures
    Expected Result: 空库迁移到 V39 全绿
    Evidence: .sisyphus/evidence/task-8-integrity.txt

  Scenario: 审计列与 ORM 映射一致（edge）
    Tool: Bash
    Steps:
      1. 启动 H2 local profile 后跑一个插入新表行的测试
      2. 断言 created_time/created_by 被自动填充非空
    Expected Result: 自动填充生效
    Evidence: .sisyphus/evidence/task-8-autofill.txt
  ```

  **Commit**: `test(db): migration integrity to V39`，pre-commit `./mvnw.cmd -Dtest=MigrationIntegrityTest test`

- [x] 9. org 模块后端（公司 / 部门树 / 岗位 CRUD）

  **What to do**:
  - 新建包 `backend/src/main/java/com/cgcpms/org/`，含 entity/mapper/service/controller/vo
  - 三实体 extends BaseEntity（@TableId ASSIGN_ID），三 Mapper extends BaseMapper
  - 部门树形查询（parent_id 递归，参考 cost_subject 树）
  - Controller `@PreAuthorize("hasRole('ADMIN') or hasAuthority('org:*')")`，端点 `/org/companies`、`/org/departments`(树)、`/org/positions`
  - 所有查询带 tenantId 过滤 + 单条 post-check
  - TDD：RED(写失败测试)→GREEN→REFACTOR

  **Must NOT do**:
  - 不给 position 加权限/角色逻辑
  - 不做拖拽可视化后端

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 标准 CRUD + 树，工作量中等
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T10, T23
  - **Blocked By**: T1, T7

  **References**:
  - Pattern: `backend/src/main/java/com/cgcpms/partner/` 整包 — Controller→Service→Mapper→Entity→VO 范式
  - Pattern: 成本科目树（`cost` 包中 cost_subject 递归树 + level 计算）— 部门树实现参考
  - API/Type: `backend/.../common/entity/BaseEntity.java`
  - WHY: partner 包是最接近的纯主数据 CRUD 模板；部门树复用 cost_subject 递归逻辑

  **Acceptance Criteria**:
  - [ ] org 包 JUnit 测试 RED→GREEN，`./mvnw.cmd -Dtest=Org*Test test` PASS
  - QA Scenarios:
  ```
  Scenario: 创建公司→部门→子部门→岗位（happy）
    Tool: Bash (curl, H2 local 启动)
    Steps:
      1. POST /api/org/companies {companyName:"测试公司"} → 200，返回 id
      2. POST /api/org/departments {companyId, deptName:"工程部", parentId:null} → 200
      3. POST /api/org/departments {parentId:工程部id, deptName:"土建组"} → 200
      4. GET /api/org/departments/tree → 断言 土建组 嵌套于 工程部
      5. POST /api/org/positions {positionName:"项目经理"} → 200
    Expected Result: 树结构正确返回
    Evidence: .sisyphus/evidence/task-9-org-crud.txt

  Scenario: 跨租户隔离（edge）
    Tool: Bash (curl)
    Steps:
      1. 租户A 创建公司 → 记录 id
      2. 以租户B 身份 GET /api/org/companies/{A的id} → 断言 404 或空
    Expected Result: 租户B 无法读取租户A 数据
    Evidence: .sisyphus/evidence/task-9-tenant.txt
  ```

  **Commit**: `feat(org): company/department-tree/position CRUD`，pre-commit `./mvnw.cmd test`

- [x] 10. 悬空 orgId 回填服务 + 根组织生成

  **What to do**:
  - 每租户生成一个默认"根组织"(org_company + 根 department)
  - 回填存量 `pm_project.org_id` 与 `ct_contract.org_id` 到对应租户根组织
  - 回填可作为一次性数据迁移(Flyway repeatable 或 service 方法 + 测试触发)
  - 先用 `lsp_find_references` 确认 `PmProject.getOrgId`/`CtContract.getOrgId` 的所有使用点再实施

  **Must NOT do**:
  - 不把 orgId 设为 NOT NULL（保持 nullable，避免存量约束冲突）
  - 不删除/重置已有非空 orgId

  **Recommended Agent Profile**:
  - **Category**: `deep` — 数据回填语义需谨慎，影响存量数据
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T9 org 实体）
  - **Parallel Group**: Wave 2（T9 后）
  - **Blocks**: T11, T22
  - **Blocked By**: T2, T9

  **References**:
  - Pattern: `backend/.../project/entity/PmProject.java` — orgId 字段(line 28)
  - Pattern: ct_contract 实体的 orgId
  - Tool: `lsp_find_references` on PmProject.getOrgId / CtContract.getOrgId
  - WHY: orgId 是悬空 FK；回填前须确认无代码假设其为空

  **Acceptance Criteria**:
  - QA Scenarios:
  ```
  Scenario: 存量项目/合同回填到根组织（happy）
    Tool: Bash (集成测试)
    Steps:
      1. seed 一个 org_id 为 null 的 pm_project
      2. 触发回填服务
      3. 断言该 project.org_id == 该租户根组织 id
    Expected Result: 回填成功，根组织存在
    Evidence: .sisyphus/evidence/task-10-backfill.txt

  Scenario: 已有非空 orgId 不被覆盖（edge）
    Tool: Bash
    Steps:
      1. seed 一个 org_id=999 的项目
      2. 触发回填
      3. 断言 org_id 仍为 999
    Expected Result: 仅回填 null 行
    Evidence: .sisyphus/evidence/task-10-noclobber.txt
  ```

  **Commit**: `feat(org): backfill dangling orgId to tenant root org`，pre-commit `./mvnw.cmd test`

- [x] 11. pm_project_member 后端 CRUD

  **What to do**:
  - 在 project 包下新增 member 子模块：实体/mapper/service/controller/vo
  - 端点 `/projects/{projectId}/members`(GET/POST/PUT/DELETE)，`@PreAuthorize` 权限码 `project:member:*`
  - tenantId 过滤 + projectId 归属校验（子资源归属，参考合同清单按 contractId 校验）
  - TDD

  **Must NOT do**:
  - 不改 PmProject 主实体既有逻辑
  - 不引入成员审批流

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T10 根组织/orgId 就绪以正确关联）
  - **Parallel Group**: Wave 2
  - **Blocks**: T22, T24
  - **Blocked By**: T2, T10

  **References**:
  - Pattern: `backend/.../contract/` 合同清单子资源 CRUD（按 contractId 归属校验）— 子资源范式
  - Pattern: `backend/.../project/` 项目主模块
  - Doc: `doc/开发文档_v2.3/05_数据库设计方案_MySQL8正式版.md:202-213`
  - WHY: 项目成员是项目的子资源，须复制合同清单的"按父 id 归属校验"模式

  **Acceptance Criteria**:
  - [ ] member JUnit RED→GREEN
  - QA Scenarios:
  ```
  Scenario: 增删改查项目成员（happy）
    Tool: Bash (curl)
    Steps:
      1. POST /api/projects/{pid}/members {userId, roleCode:"PM"} → 200
      2. GET /api/projects/{pid}/members → 断言含该成员
      3. DELETE /api/projects/{pid}/members/{mid} → 200
      4. GET 再查 → 不含
    Expected Result: CRUD 正常
    Evidence: .sisyphus/evidence/task-11-member.txt

  Scenario: 子资源归属校验（edge）
    Tool: Bash
    Steps:
      1. 项目A 创建成员 mid
      2. GET /api/projects/{项目B}/members/{mid} → 断言 404
    Expected Result: 跨项目无法访问
    Evidence: .sisyphus/evidence/task-11-ownership.txt
  ```

  **Commit**: `feat(project): project member CRUD`，pre-commit `./mvnw.cmd test`

- [x] 12. dict 后端（DictType / DictData CRUD）

  **What to do**:
  - 在 system 包下新增 dict 子模块：DictType/DictData 实体/mapper/service/controller/vo
  - 表已存在(sys_dict_type/sys_dict_data, V5)，仅建 Java 层
  - 端点 `/system/dict/types`、`/system/dict/data?typeId=`，`@PreAuthorize` 权限码 `system:dict:*`（菜单已存在 V6）
  - tenantId 过滤 + 单条 post-check
  - **不加缓存**，直查 DB
  - TDD

  **Must NOT do**:
  - 不加 `@Cacheable`/`@EnableCaching`
  - 不改 sys_dict_type/sys_dict_data 表结构
  - 注意表名是 `sys_dict_data`（非 sys_dict_item）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T26
  - **Blocked By**: T7（菜单/权限）

  **References**:
  - Pattern: `backend/.../partner/` — 标准 CRUD 范式
  - Pattern: `backend/src/main/resources/db/migration/V5__init_dict_data.sql:13-45` — sys_dict_type/sys_dict_data 既有表结构与列名
  - Pattern: `backend/.../system/` 包既有结构（SysUser/SysRole/SysMenu）
  - WHY: 表已存在，须严格按 V5 列名建实体；表名 sys_dict_data 易误写为 sys_dict_item

  **Acceptance Criteria**:
  - [ ] dict JUnit RED→GREEN
  - QA Scenarios:
  ```
  Scenario: 字典类型+数据 CRUD（happy）
    Tool: Bash (curl)
    Steps:
      1. GET /api/system/dict/types → 断言含已有种子(contract_type 等)
      2. POST /api/system/dict/types {dictCode:"test_type", dictName:"测试"} → 200
      3. POST /api/system/dict/data {dictTypeId, dictLabel:"标签A", dictValue:"A"} → 200
      4. GET /api/system/dict/data?typeId={id} → 断言含标签A
    Expected Result: CRUD 正常
    Evidence: .sisyphus/evidence/task-12-dict.txt

  Scenario: 无缓存直查（edge）
    Tool: Bash (grep)
    Steps:
      1. grep -rE "@Cacheable|@EnableCaching" backend/src/main/java/com/cgcpms/system/dict/ → 零命中
    Expected Result: 无缓存注解
    Evidence: .sisyphus/evidence/task-12-nocache.txt
  ```

  **Commit**: `feat(system): dictionary type/data CRUD`，pre-commit `./mvnw.cmd test`

- [x] 13. inventory 仓库 CRUD 后端

  **What to do**:
  - 新建包 `backend/src/main/java/com/cgcpms/inventory/`，仓库实体/mapper/service/controller/vo
  - 端点 `/inventory/warehouses`，`@PreAuthorize` 权限码 `inventory:warehouse:*`
  - tenantId 过滤 + 单条 post-check + projectId 关联
  - TDD

  **Must NOT do**:
  - 不在仓库加计价字段

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T14, T27
  - **Blocked By**: T3

  **References**:
  - Pattern: `backend/src/main/java/com/cgcpms/receipt/` 整包 — CRUD+审批范式模板（最佳）
  - Pattern: `backend/.../material/` — 简单主数据 CRUD（无审批）
  - WHY: 仓库是简单主数据，参考 material 包；inventory 包整体以 receipt 为模板

  **Acceptance Criteria**:
  - [ ] warehouse JUnit RED→GREEN
  - QA Scenarios:
  ```
  Scenario: 仓库 CRUD（happy）
    Tool: Bash (curl)
    Steps:
      1. POST /api/inventory/warehouses {warehouseName:"中心库", projectId} → 200
      2. GET /api/inventory/warehouses → 断言含中心库
    Expected Result: CRUD 正常
    Evidence: .sisyphus/evidence/task-13-warehouse.txt

  Scenario: 跨租户隔离（edge）
    Tool: Bash
    Steps: 租户B 读租户A 仓库 → 404/空
    Evidence: .sisyphus/evidence/task-13-tenant.txt
  ```

  **Commit**: `feat(inventory): warehouse CRUD`，pre-commit `./mvnw.cmd test`

- [x] 14. inventory 库存台账 + 出入库流水后端（乐观锁 / 负库存阻断）

  **What to do**:
  - mat_stock 实体加 `@Version`（MyBatis-Plus 乐观锁）
  - 出入库服务：IN 增加 available_qty，OUT 校验 available_qty>=出库量否则抛业务异常(非500)，ADJUST 调整
  - 每笔写 mat_stock_txn 流水(source_type/source_id 追溯)
  - 出库并发安全：乐观锁重试或失败
  - TDD + 并发递减集成测试

  **Must NOT do**:
  - 不允许负库存（硬阻断）
  - 不做计价/成本生成（库存是数量层）
  - 不读 UserContext 于任何异步路径

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain` — 并发正确性 + 乐观锁是硬逻辑
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T13 仓库）
  - **Parallel Group**: Wave 2
  - **Blocks**: T27, T31
  - **Blocked By**: T3, T13

  **References**:
  - Pattern: `backend/.../receipt/service/MatReceiptService.java` — 批量明细+事务+tenantId 范式
  - Pattern: Phase2 审计修复中"悲观锁 SELECT FOR UPDATE 两阶段校验"（合同余额）— 并发控制参考
  - API/Type: MyBatis-Plus `@Version` 乐观锁配置（确认 OptimisticLockerInnerInterceptor 已注册或需注册）
  - WHY: 出库超卖是核心风险；需乐观锁 + 并发测试

  **Acceptance Criteria**:
  - [ ] stock JUnit RED→GREEN + 并发测试
  - QA Scenarios:
  ```
  Scenario: 入库100→出库30→余70（happy）
    Tool: Bash (集成测试)
    Steps:
      1. IN qty=100 → mat_stock.available_qty==100
      2. OUT qty=30 → available_qty==70
      3. GET 台账 → 断言 70，流水含 2 笔
    Expected Result: 余额 70
    Evidence: .sisyphus/evidence/task-14-stock-flow.txt

  Scenario: 并发出库防超卖（edge）
    Tool: Bash (集成测试, 多线程)
    Steps:
      1. 库存 100，并发 OUT 80 + OUT 40
      2. 断言恰好一笔成功一笔失败，最终 available_qty>=0 且非负
    Expected Result: 无超卖，库存不为负
    Evidence: .sisyphus/evidence/task-14-concurrency.txt

  Scenario: 库存不足出库被阻断（edge）
    Tool: Bash (curl)
    Steps:
      1. 库存 10，OUT qty=50 → 断言返回业务错误码(非 500)
    Expected Result: 业务异常，库存不变
    Evidence: .sisyphus/evidence/task-14-insufficient.txt
  ```

  **Commit**: `feat(inventory): stock ledger + transactions with optimistic lock`，pre-commit `./mvnw.cmd test`

- [x] 15. 采购申请 mat_purchase_request 后端（CRUD + 审批 + 转 PO）

  **What to do**:
  - inventory 或 purchase 包下新增采购申请：实体/mapper/service/controller/vo + WorkflowBusinessHandler
  - CRUD + submit 审批（复用审批引擎，businessType=PURCHASE_REQUEST，模板见 T7）
  - 审批通过后可转为已有 mat_purchase_order（链接 source）
  - tenantId 全链路；TDD

  **Must NOT do**:
  - 不改 mat_purchase_order 既有结构
  - 不读 UserContext 于审批回调异步路径（用 context 传入）

  **Recommended Agent Profile**:
  - **Category**: `deep` — 审批闭环 + 转单链路
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T27, T31
  - **Blocked By**: T3, T7

  **References**:
  - Pattern: `backend/.../purchase/` 整包(MatPurchaseOrder + PurchaseOrderWorkflowHandler) — CRUD+审批闭环范式
  - Pattern: `backend/.../workflow/handler/WorkflowBusinessHandler.java` + Registry — 处理器注册机制
  - Pattern: `backend/.../workflow/WorkflowBusinessTypes.java` — 新增 PURCHASE_REQUEST 常量
  - WHY: 采购申请是采购订单的前置，复用 purchase 包的审批闭环模式

  **Acceptance Criteria**:
  - [ ] purchase-request JUnit RED→GREEN
  - QA Scenarios:
  ```
  Scenario: 采购申请提交→审批→转PO（happy）
    Tool: Bash (集成测试)
    Steps:
      1. POST 创建采购申请 DRAFT
      2. POST submit → APPROVING
      3. 审批通过 → APPROVED
      4. 转 PO → 断言生成 mat_purchase_order 关联 source
    Expected Result: 闭环成功
    Evidence: .sisyphus/evidence/task-15-pr-flow.txt

  Scenario: 审批回调不依赖 UserContext（edge）
    Tool: Bash (grep)
    Steps:
      1. grep "UserContext" PurchaseRequestWorkflowHandler.java → 零命中(或仅注释)
    Expected Result: 回调用 context 传 tenant/user
    Evidence: .sisyphus/evidence/task-15-nousercontext.txt
  ```

  **Commit**: `feat(purchase): purchase request CRUD + approval + convert to PO`，pre-commit `./mvnw.cmd test`

- [x] 16. pay_invoice 后端（登记 + 核验状态 + PayRecord 关联）

  **What to do**:
  - 新建包 `backend/src/main/java/com/cgcpms/invoice/`：实体/mapper/service/controller/vo
  - 登记发票 + 关联 pay_record/pay_application（多对一）
  - 核验状态切换端点（PENDING→VERIFIED/ABNORMAL），权限码 `invoice:verify`
  - 重复发票号(同租户)拒绝（uk 唯一键 + 业务校验）
  - tenantId 全链路；TDD

  **Must NOT do**:
  - 不建发票审批工作流（核验是状态字段切换）
  - 不做 OCR

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T28, T31
  - **Blocked By**: T4

  **References**:
  - Pattern: `backend/.../payment/service/PayRecordService.java` — 付款记录服务范式，发票关联此表
  - Pattern: `backend/.../payment/` 整包 — VO/Controller 风格
  - WHY: 发票挂靠付款记录；复用 payment 包结构

  **Acceptance Criteria**:
  - [ ] invoice JUnit RED→GREEN
  - QA Scenarios:
  ```
  Scenario: 登记发票→关联付款→核验（happy）
    Tool: Bash (curl)
    Steps:
      1. POST /api/invoices {invoiceNo:"INV001", invoiceAmount:1000, payRecordId} → 200
      2. PUT /api/invoices/{id}/verify {status:"VERIFIED"} → 200
      3. GET /api/invoices/{id} → 断言 verifyStatus==VERIFIED 且关联 payRecordId
    Expected Result: 登记+核验+关联成功
    Evidence: .sisyphus/evidence/task-16-invoice.txt

  Scenario: 重复发票号拒绝（edge）
    Tool: Bash (curl)
    Steps:
      1. POST 发票 invoiceNo="INV001"（第二次，同租户）→ 断言业务错误码
    Expected Result: 拒绝重复
    Evidence: .sisyphus/evidence/task-16-dup.txt
  ```

  **Commit**: `feat(invoice): invoice registration + verification + payment linkage`，pre-commit `./mvnw.cmd test`

- [x] 17. notification 后端（实体 / 服务 / 控制器 / SSE 端点）

  **What to do**:
  - 新建包 `backend/src/main/java/com/cgcpms/notification/`：实体/mapper/service/controller/vo
  - 服务方法：`create(tenantId, userId, ...)`(显式传参，不读UserContext)、list(分页,按当前用户)、markRead、markAllRead、unreadCount
  - SSE 端点 `/notifications/stream`（SseEmitter），按当前登录用户订阅；同源 cookie 鉴权（servlet 路径，非 WS 握手）
  - tenantId + userId 过滤；TDD

  **Must NOT do**:
  - 不用 WebSocket
  - 不在 create/推送路径读 UserContext（调用方显式传 tenantId/userId）
  - 不加邮件/短信

  **Recommended Agent Profile**:
  - **Category**: `deep` — SSE + 异步租户传递是要点
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T19, T21, T29
  - **Blocked By**: T5

  **References**:
  - Pattern: `backend/.../alert/` 整包 — list/markRead 查询范式（AlertController）
  - Pattern: `backend/.../partner/` — 标准 CRUD
  - API/Type: Spring `SseEmitter`（servlet SSE）；现有 JwtAuthenticationFilter 在 servlet 路径生效（SSE 走同源 cookie 可用）
  - WHY: SSE 走标准 servlet 请求，HttpOnly cookie 自动携带，无需 WS 握手拦截器

  **Acceptance Criteria**:
  - [ ] notification JUnit RED→GREEN
  - QA Scenarios:
  ```
  Scenario: 创建通知→查询未读→标记已读（happy）
    Tool: Bash (curl)
    Steps:
      1. 服务层 create(tenantId=A, userId=X, title="审批通过")
      2. 以 X 登录 GET /api/notifications?unread=true → 断言含该通知
      3. GET /api/notifications/unread-count → 断言 >=1
      4. PUT /api/notifications/{id}/read → 200
      5. 再查 unread-count → 减少
    Expected Result: CRUD+未读计数正确
    Evidence: .sisyphus/evidence/task-17-notify.txt

  Scenario: create 不读 UserContext（edge）
    Tool: Bash (grep)
    Steps:
      1. grep "UserContext" NotificationService.java → create 路径零命中
    Expected Result: 显式传参
    Evidence: .sisyphus/evidence/task-17-explicit.txt

  Scenario: SSE 端点连通（edge）
    Tool: Bash (curl -N)
    Steps:
      1. curl -N --cookie "access_token=<jwt>" /api/notifications/stream，超时 3s
      2. 断言返回 content-type: text/event-stream
    Expected Result: SSE 流建立
    Evidence: .sisyphus/evidence/task-17-sse.txt
  ```

  **Commit**: `feat(notification): notification CRUD + SSE stream`，pre-commit `./mvnw.cmd test`

- [x] 18. workflow 查询增强后端（我的已办 wf_record）

  **What to do**:
  - WorkflowQueryService 新增 `getMyDone(userId, tenantId, page)`：查 wf_record WHERE operatorId=? AND tenantId=?（已有数据，仅缺端点）
  - WorkflowController 新增 `GET /workflow/tasks/done`
  - tenantId 显式过滤；TDD

  **Must NOT do**:
  - 不改 WorkflowEngine 核心
  - 不改 wf_record 结构

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T30
  - **Blocked By**: None（wf_record 已存在）

  **References**:
  - Pattern: `backend/.../workflow/service/WorkflowQueryService.java:34-80` — getMyTodos 范式，仿写 getMyDone
  - Pattern: `backend/.../workflow/controller/WorkflowController.java:148-157` — /tasks/todo 端点
  - API/Type: WfRecord.operatorId 字段
  - WHY: 已办数据已在 wf_record.operatorId，仅需仿 getMyTodos 增查询与端点

  **Acceptance Criteria**:
  - [ ] getMyDone JUnit RED→GREEN
  - QA Scenarios:
  ```
  Scenario: 我的已办返回我处理过的记录（happy）
    Tool: Bash (curl)
    Steps:
      1. 用户X 审批一笔 → wf_record 写入 operatorId=X
      2. GET /api/workflow/tasks/done (as X) → 断言含该记录
    Expected Result: 已办列表正确
    Evidence: .sisyphus/evidence/task-18-done.txt

  Scenario: 跨租户隔离（edge）
    Tool: Bash
    Steps: 租户B 查询 → 不含租户A 记录
    Evidence: .sisyphus/evidence/task-18-tenant.txt
  ```

  **Commit**: `feat(workflow): my-done query endpoint`，pre-commit `./mvnw.cmd test`

- [x] 19. notification 挂钩 WorkflowEngine 生命周期（显式 tenant/user 传递）

  **What to do**:
  - WorkflowEngine 的 submit/approve/reject/withdraw/transfer/add-sign 等关键动作调用 NotificationService.create
  - 每次传入显式 tenantId/userId（从 wfInstance 或 task 读，不读 UserContext）
  - 消息模板："{发起人} 提交了审批"、"{审批人} 同意了你的申请"、"审批已驳回"、"{转办人} 将任务转给你" 等
  - TDD 验证租户正确传递

  **Must NOT do**:
  - 不在 WorkflowEngine 回调/异步路径读 UserContext
  - 不改审批核心流程逻辑

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain` — 集成正确性 + 租户传递是安全关键
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T17 通知服务）
  - **Parallel Group**: Wave 3
  - **Blocks**: T31
  - **Blocked By**: T17

  **References**:
  - Pattern: `backend/.../workflow/service/WorkflowEngine.java:42-646` — submit/approve/reject/withdraw 等方法体，需识别调用点
  - Pattern: T17 NotificationService.create(tenantId, userId, ...)
  - WHY: WorkflowEngine 生命周期是站内消息触发源头；租户传递错误会导致数据泄露

  **Acceptance Criteria**:
  - [ ] 集成测试：审批动作 → 断言通知创建，tenantId 正确
  - QA Scenarios:
  ```
  Scenario: 提交审批→发起人收到通知（happy）
    Tool: Bash (集成测试)
    Steps:
      1. 租户A 用户X 提交合同审批
      2. 断言 sys_notification 写入 tenant_id=A, user_id=审批人
      3. 审批人 approve → 断言写入 user_id=X(发起人)
    Expected Result: 通知租户+用户正确
    Evidence: .sisyphus/evidence/task-19-lifecycle.txt

  Scenario: 无 UserContext 读取（edge）
    Tool: Bash (grep)
    Steps:
      1. grep "UserContext" WorkflowEngine.java 通知调用段 → 零命中
    Expected Result: 显式从 instance/task 取 tenant/user
    Evidence: .sisyphus/evidence/task-19-explicit.txt
  ```

  **Commit**: `feat(notification): integrate with workflow lifecycle`，pre-commit `./mvnw.cmd test`

- [x] 20. wf_cc 抄送服务 + 提交时写入 + 抄送查询端点

  **What to do**:
  - workflow 包下新增 cc 模块：WfCc 实体/mapper/service
  - WorkflowEngine.submit 增加可选参数 `List<Long> ccUserIds`，写 wf_cc 表
  - WorkflowQueryService 新增 `getMyCc(userId, tenantId, page)`
  - WorkflowController 新增 `GET /workflow/tasks/cc`
  - 抄送时触发通知（调 T17 service）
  - tenantId 全链路；TDD

  **Must NOT do**:
  - 不改 wf_instance/wf_task/wf_record 结构
  - 不改 WorkflowEngine 核心节点流转逻辑

  **Recommended Agent Profile**:
  - **Category**: `deep` — 集成审批引擎 + 通知
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T17 通知）
  - **Parallel Group**: Wave 3
  - **Blocks**: T30, T31
  - **Blocked By**: T6, T17

  **References**:
  - Pattern: `backend/.../workflow/` 整包 — WorkflowEngine submit/query 范式
  - Pattern: T18 getMyDone 查询模式
  - WHY: 抄送是附加 join 表，仿 getMyDone；抄送写入时同步触发通知

  **Acceptance Criteria**:
  - [ ] cc JUnit RED→GREEN
  - QA Scenarios:
  ```
  Scenario: 提交审批+抄送→抄送人收到通知+查询（happy）
    Tool: Bash (集成测试)
    Steps:
      1. submit 时传 ccUserIds=[U1, U2]
      2. 断言 wf_cc 写入 2 行
      3. 断言 notification 写入 2 行(user_id=U1/U2)
      4. 以 U1 身份 GET /workflow/tasks/cc → 断言含该实例
    Expected Result: 抄送+通知+查询正确
    Evidence: .sisyphus/evidence/task-20-cc.txt

  Scenario: 跨租户隔离（edge）
    Tool: Bash
    Steps: 租户B GET /tasks/cc → 不含租户A 抄送
    Evidence: .sisyphus/evidence/task-20-tenant.txt
  ```

  **Commit**: `feat(workflow): cc(copy-to) support + query`，pre-commit `./mvnw.cmd test`

- [x] 21. 预警入消息（AlertEvaluationService → notification, 显式 tenant）

  **What to do**:
  - AlertEvaluationService 批处理 `@Scheduled` 每产生 alert_log 时，同步调 NotificationService.create
  - 接收人：项目成员 or 项目经理（从 pm_project_member 查询）
  - 显式传 tenantId（从 project 查询得到，不读 UserContext）
  - TDD

  **Must NOT do**:
  - 不改 alert_log 表结构
  - 不在 @Scheduled 路径读 UserContext

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: T31
  - **Blocked By**: T17

  **References**:
  - Pattern: `backend/.../alert/service/AlertEvaluationService.java:42-474` — @Scheduled 批处理 + alert_log 写入点
  - Pattern: T17 NotificationService.create
  - WHY: 预警是消息源之一；定时任务无 UserContext，必须从 project 查 tenant

  **Acceptance Criteria**:
  - [ ] 集成测试：触发预警 → 通知创建
  - QA Scenarios:
  ```
  Scenario: 预警触发→项目成员收通知（happy）
    Tool: Bash (集成测试)
    Steps:
      1. 触发 AlertEvaluationService 批处理
      2. 断言 alert_log 写入 + notification 写入（tenant 与 project.tenantId 一致）
    Expected Result: 预警入消息，tenant 正确
    Evidence: .sisyphus/evidence/task-21-alert.txt

  Scenario: 无 UserContext 依赖（edge）
    Tool: Bash (grep)
    Steps:
      1. grep "UserContext" AlertEvaluationService.java 通知段 → 零命中
    Expected Result: 从 project 查 tenant 显式传
    Evidence: .sisyphus/evidence/task-21-explicit.txt
  ```

  **Commit**: `feat(notification): integrate alert warnings`，pre-commit `./mvnw.cmd test`

- [x] 22. 项目全景聚合接口（合同 / 成本 / 付款 / 预警概况）

  **What to do**:
  - PmProjectController 新增 `GET /projects/{projectId}/overview`
  - 返回 ProjectOverviewVO（合同总额/数量、动态成本、已付款、本月预警数、项目成员列表等）
  - 批量预取或聚合查询避免 N+1
  - tenantId 过滤 + projectId 归属校验；TDD

  **Must NOT do**:
  - 不做实时统计（可读 cost_summary 等已有汇总表）
  - 不引入新汇总表

  **Recommended Agent Profile**:
  - **Category**: `deep` — 聚合查询 + N+1 优化
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T10 orgId 回填 + T11 member）
  - **Parallel Group**: Wave 3
  - **Blocks**: T25
  - **Blocked By**: T10, T11

  **References**:
  - Pattern: `backend/.../dashboard/` 包 — 驾驶舱聚合查询 VO
  - Pattern: `backend/.../cost/service/CostSummaryService.java` — cost_summary 汇总表读取
  - Pattern: Phase 2 审查中"N+1 优化批量预取"（用户角色预取）— 防 N+1 技巧
  - WHY: 项目全景需复用驾驶舱汇总模式；cost_summary 已有动态成本数据

  **Acceptance Criteria**:
  - [ ] overview JUnit RED→GREEN
  - QA Scenarios:
  ```
  Scenario: 项目全景返回汇总数据（happy）
    Tool: Bash (curl)
    Steps:
      1. GET /api/projects/{pid}/overview → 200
      2. 断言返回 contractCount/totalContractAmount/dynamicCost/paidAmount/warningCount/members
    Expected Result: 聚合数据完整
    Evidence: .sisyphus/evidence/task-22-overview.txt

  Scenario: 无 N+1（edge）
    Tool: Bash (日志)
    Steps:
      1. 启用 SQL 日志
      2. 调用 overview 含 10 成员
      3. 断言 SQL 执行 <5 次（批量预取）
    Expected Result: 无 N+1
    Evidence: .sisyphus/evidence/task-22-n1.txt
  ```

  **Commit**: `feat(project): project overview aggregation API`，pre-commit `./mvnw.cmd test`

- [x] 23. 前端 组织管理页（部门树 + 公司 / 岗位）

  **What to do**:
  - 新建 `frontend-admin/src/pages/org/index.vue`（公司列表、部门树 a-tree、岗位列表）
  - `api/modules/org.ts` + `types/org.ts`
  - 路由 + 菜单接入（菜单 V39 已建）

  **Must NOT do**: 不做拖拽排序可视化；不绕过后端权限

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering` — UI 页面
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: YES | Wave 3 | Blocks: T31 | Blocked By: T9

  **References**:
  - Pattern: `frontend-admin/src/pages/project/index.vue` — 列表页范式
  - Pattern: `frontend-admin/src/api/modules/project.ts` + `types/project.ts` — API/类型范式
  - Pattern: `frontend-admin/src/api/request.ts` — 请求封装(HttpOnly cookie)
  - Pattern: Ant Design Vue `a-tree` — 部门树组件
  - WHY: 严格复刻 project 页前端四件套(page/api/type/store)

  **Acceptance Criteria**:
  - [ ] `pnpm type-check` 通过
  - QA Scenarios:
  ```
  Scenario: 组织页增删改查（happy）
    Tool: Playwright
    Preconditions: 后端 H2 运行，已登录(admin/admin123)
    Steps:
      1. goto /org
      2. click 选择器 .company-add-btn，fill input[name="companyName"]="测试公司"，submit
      3. 断言 .company-table 出现"测试公司"行
      4. 部门树 click 添加根部门"工程部"，断言 .dept-tree 含"工程部"节点
    Expected Result: 列表/树更新
    Evidence: .sisyphus/evidence/task-23-org-ui.png

  Scenario: 无权限隐藏按钮（edge）
    Tool: Playwright
    Steps: 以无 org:add 权限用户登录，断言 .company-add-btn 不可见
    Evidence: .sisyphus/evidence/task-23-perm.png
  ```

  **Commit**: `feat(ui): organization management page`，pre-commit `pnpm type-check`

- [x] 24. 前端 项目成员管理页 + project store

  **What to do**:
  - `frontend-admin/src/pages/project/members.vue`（成员表 + 添加成员 UserPicker）
  - 新建 `stores/project.ts`（Pinia，仿 contract store）
  - `api/modules/project.ts` 增加 member 端点 + `types/project.ts` 增 MemberVO

  **Must NOT do**: 不重写既有 project/index.vue 列表逻辑

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: YES | Wave 3 | Blocks: T31 | Blocked By: T11

  **References**:
  - Pattern: `frontend-admin/src/stores/contract.ts` — Pinia store 范式(loading/saving/data refs)
  - Pattern: `frontend-admin/src/pages/project/index.vue`
  - WHY: project store 缺失，按 contract store 模板补齐

  **Acceptance Criteria**:
  - [ ] `pnpm type-check` 通过
  - QA Scenarios:
  ```
  Scenario: 添加/移除项目成员（happy）
    Tool: Playwright
    Steps:
      1. goto /projects/{pid}/members（或项目详情成员 Tab）
      2. click .member-add-btn，选择用户，roleCode 选"项目经理"，submit
      3. 断言 .member-table 出现该用户
      4. click .member-remove-btn，确认，断言行消失
    Expected Result: 成员增删生效
    Evidence: .sisyphus/evidence/task-24-member-ui.png
  ```

  **Commit**: `feat(ui): project member management + project store`，pre-commit `pnpm type-check`

- [x] 25. 前端 项目全景页

  **What to do**:
  - `frontend-admin/src/pages/project/overview.vue`（KPI 卡片 + ECharts + 成员/预警概况）
  - 消费 T22 的 `/projects/{id}/overview` 接口

  **Must NOT do**: 不在前端做金额计算（用后端聚合值）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: YES | Wave 3 | Blocks: T31 | Blocked By: T22

  **References**:
  - Pattern: `frontend-admin/src/pages/dashboard/index.vue` — KPI + ECharts 范式
  - Pattern: `frontend-admin/src/pages/project/index.vue`
  - WHY: 全景页复用驾驶舱的 KPI/图表组件风格

  **Acceptance Criteria**:
  - [ ] `pnpm type-check` 通过
  - QA Scenarios:
  ```
  Scenario: 全景页展示项目汇总（happy）
    Tool: Playwright
    Steps:
      1. goto /projects/{pid}/overview
      2. 断言 .kpi-contract-amount 显示非空金额
      3. 断言 .kpi-warning-count 显示数字
      4. 断言 ECharts canvas 渲染(.echarts-container canvas 存在)
    Expected Result: 全景数据展示
    Evidence: .sisyphus/evidence/task-25-overview-ui.png
  ```

  **Commit**: `feat(ui): project overview page`，pre-commit `pnpm type-check`

- [x] 26. 前端 字典管理页

  **What to do**:
  - 新建 `frontend-admin/src/pages/system/dict/index.vue`（左字典类型列表 + 右字典数据表）
  - `api/modules/dict.ts` + `types/dict.ts`
  - 路由接入（菜单 system/dict/index 已存在 V6）

  **Must NOT do**: 不改 sys_dict 表；注意接口字段对应 sys_dict_data

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: YES | Wave 3 | Blocks: T31 | Blocked By: T12

  **References**:
  - Pattern: `frontend-admin/src/pages/partner/index.vue` — 列表+表单范式
  - Pattern: `frontend-admin/src/router/index.ts` — 路由注册(System path 已占位)
  - WHY: 字典页是经典主从布局，复刻 partner 列表页

  **Acceptance Criteria**:
  - [ ] `pnpm type-check` 通过
  - QA Scenarios:
  ```
  Scenario: 字典类型+数据管理（happy）
    Tool: Playwright
    Steps:
      1. goto /system/dict
      2. click .dict-type-add，fill name="测试字典" code="test_dict"，submit
      3. 断言 .dict-type-list 含"测试字典"
      4. 选中该类型，click .dict-data-add，fill label="选项A" value="A"，submit
      5. 断言 .dict-data-table 含"选项A"
    Expected Result: 类型与数据 CRUD 生效
    Evidence: .sisyphus/evidence/task-26-dict-ui.png
  ```

  **Commit**: `feat(ui): dictionary management page`，pre-commit `pnpm type-check`

- [x] 27. 前端 库存页（仓库 / 台账 / 出入库 / 采购申请）

  **What to do**:
  - 新建 `frontend-admin/src/pages/inventory/` 下：warehouse.vue、stock.vue(台账)、transaction.vue(出入库)、purchase-request.vue
  - `api/modules/inventory.ts` + `types/inventory.ts`
  - 出入库表单 + 库存台账查询 + 采购申请提交审批

  **Must NOT do**: 不在前端做库存计算（后端为准）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: YES | Wave 3 | Blocks: T31 | Blocked By: T13, T14, T15

  **References**:
  - Pattern: `frontend-admin/src/pages/purchase/` — 采购页(含审批提交)范式
  - Pattern: `frontend-admin/src/pages/payment/index.vue` — 复杂表单+表格范式
  - WHY: 库存页含审批提交，复刻 purchase 页交互

  **Acceptance Criteria**:
  - [ ] `pnpm type-check` 通过
  - QA Scenarios:
  ```
  Scenario: 入库→出库→台账更新（happy）
    Tool: Playwright
    Steps:
      1. goto /inventory/transaction
      2. click .txn-in-btn，选仓库+物料，fill quantity=100，submit
      3. goto /inventory/stock，断言 .stock-table 该物料 available=100
      4. 回 transaction，出库 30，断言台账 available=70
    Expected Result: 出入库联动台账
    Evidence: .sisyphus/evidence/task-27-inventory-ui.png

  Scenario: 库存不足出库报错（edge）
    Tool: Playwright
    Steps: 出库量>库存，submit，断言 .ant-message 显示业务错误提示
    Evidence: .sisyphus/evidence/task-27-insufficient.png
  ```

  **Commit**: `feat(ui): inventory pages`，pre-commit `pnpm type-check`

- [x] 28. 前端 发票管理页

  **What to do**:
  - 新建 `frontend-admin/src/pages/invoice/index.vue`（发票列表+登记表单+核验状态切换+关联付款记录选择）
  - `api/modules/invoice.ts` + `types/invoice.ts`

  **Must NOT do**: 不做 OCR 上传识别

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: YES | Wave 3 | Blocks: T31 | Blocked By: T16

  **References**:
  - Pattern: `frontend-admin/src/pages/payment/index.vue` — 付款页范式(发票关联付款记录)
  - WHY: 发票挂靠付款，复刻 payment 页

  **Acceptance Criteria**:
  - [ ] `pnpm type-check` 通过
  - QA Scenarios:
  ```
  Scenario: 登记发票→核验（happy）
    Tool: Playwright
    Steps:
      1. goto /invoice
      2. click .invoice-add，fill invoiceNo="INV001" amount=1000，选付款记录，submit
      3. 断言 .invoice-table 含 INV001
      4. click .invoice-verify-btn，选"已认证"，断言状态列显示"已认证"
    Expected Result: 登记+核验生效
    Evidence: .sisyphus/evidence/task-28-invoice-ui.png

  Scenario: 重复发票号提示（edge）
    Tool: Playwright
    Steps: 再次登记 INV001，submit，断言错误提示
    Evidence: .sisyphus/evidence/task-28-dup.png
  ```

  **Commit**: `feat(ui): invoice management page`，pre-commit `pnpm type-check`

- [x] 29. 前端 消息铃铛 + 面板（SSE 订阅）+ SysUserVO.orgId 契约修复

  **What to do**:
  - 顶部导航铃铛组件（未读 badge）+ 下拉通知面板（列表/标记已读/全部已读）
  - `api/modules/notification.ts` + `types/notification.ts`
  - EventSource 订阅 `/api/notifications/stream`，新消息实时更新 badge
  - 修复 `types/system.ts` 的 SysUserVO.orgId 契约（后端 T2 已加 org_id；确保前后端一致）

  **Must NOT do**: 不用 WebSocket；不轮询代替 SSE（除非 SSE 不可用降级）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: YES | Wave 3 | Blocks: T31 | Blocked By: T17

  **References**:
  - Pattern: `frontend-admin/src/pages/alert/index.vue` — 预警列表(类似通知列表)
  - Pattern: 布局组件 BasicLayout(顶部导航) — 铃铛挂载点
  - Pattern: `types/system.ts` SysUserVO — orgId 字段已存在但后端原缺，现修复
  - API/Type: 浏览器 `EventSource`(SSE)，同源自动携带 HttpOnly cookie
  - WHY: 铃铛挂在布局顶部；SSE 用 EventSource，cookie 自动携带

  **Acceptance Criteria**:
  - [ ] `pnpm type-check` 通过
  - QA Scenarios:
  ```
  Scenario: 收到通知铃铛更新（happy）
    Tool: Playwright
    Steps:
      1. 登录后断言 .notification-bell 可见
      2. 后端触发一条通知(curl 或审批动作)
      3. 等待 SSE，断言 .notification-badge 计数 +1
      4. click 铃铛，断言 .notification-panel 列表含新消息
      5. click 标记已读，断言 badge 减少
    Expected Result: 实时通知+已读
    Evidence: .sisyphus/evidence/task-29-bell.png

  Scenario: SysUserVO.orgId 契约一致（edge）
    Tool: Bash (type-check)
    Steps: pnpm type-check → 无 orgId 相关类型错误
    Evidence: .sisyphus/evidence/task-29-typecheck.txt
  ```

  **Commit**: `feat(ui): notification bell + SSE + fix orgId contract`，pre-commit `pnpm type-check`

- [x] 30. 前端 待办中心（我的已办 / 抄送我 Tab）

  **What to do**:
  - 扩展 `frontend-admin/src/pages/approval/` 增加"我的已办""抄送我"Tab/页
  - `api/modules/workflow.ts` 增加 done/cc 端点
  - 路由接入

  **Must NOT do**: 不改既有 todo.vue/detail.vue 核心逻辑

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: YES | Wave 3 | Blocks: T31 | Blocked By: T18, T20

  **References**:
  - Pattern: `frontend-admin/src/pages/approval/todo.vue` — 待办列表范式，仿写已办/抄送
  - Pattern: `frontend-admin/src/api/modules/workflow.ts` — workflow API 模块
  - WHY: 已办/抄送是待办的同构列表，复刻 todo.vue

  **Acceptance Criteria**:
  - [ ] `pnpm type-check` 通过
  - QA Scenarios:
  ```
  Scenario: 已办/抄送 Tab 展示（happy）
    Tool: Playwright
    Steps:
      1. goto /approval（含 Tab）
      2. click "我的已办" Tab，断言 .done-table 渲染(含处理过的记录)
      3. click "抄送我" Tab，断言 .cc-table 渲染
    Expected Result: 三 Tab 切换正常
    Evidence: .sisyphus/evidence/task-30-todocenter.png
  ```

  **Commit**: `feat(ui): todo center my-done/cc tabs`，pre-commit `pnpm type-check`

- [x] 31. 关键链路集成测试（库存 / 发票 / 消息 / 抄送 / 隔离）

  **What to do**:
  - 新建集成测试类（H2 local profile，仿 Phase3IntegrationTest），覆盖：
    - 库存：采购申请→入库 100→出库 30→台账 70→并发出库 80+40 仅一成功且不为负
    - 发票：登记 amount=1000→关联 PayRecord→核验→断言关联+状态；重复发票号拒绝
    - 消息：审批事件(租户A 用户X)→断言 notification 写入 tenant=A user=X；租户B 零可见
    - 抄送：submit + ccUserIds → wf_cc + notification 写入
    - 隔离：每个新端点 seed 双租户，跨租户访问断言空/403

  **Must NOT do**: 不依赖外部 MySQL（用 H2）；不跳过任一关键链路

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain` — 多链路集成正确性
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: NO | Wave 4 | Blocks: T32, F3 | Blocked By: T14,T16,T19,T20,T15

  **References**:
  - Pattern: `Phase3IntegrationTest`（6 场景 H2）— 集成测试范式
  - Pattern: 全链路集成测试(7 tests, 采购→验收→成本→付款→回写)
  - WHY: 复刻既有集成测试框架，覆盖 Phase 4 新链路

  **Acceptance Criteria**:
  - [ ] 集成测试类 PASS
  - QA Scenarios:
  ```
  Scenario: 全部 Phase4 集成场景通过（happy）
    Tool: Bash
    Steps:
      1. cd backend && ./mvnw.cmd -Dtest=Phase4IntegrationTest test
      2. 断言所有场景 PASS, 0 failures
    Expected Result: 全绿
    Evidence: .sisyphus/evidence/task-31-integration.txt

  Scenario: 多租户隔离矩阵（edge）
    Tool: Bash
    Steps: 断言每个新端点跨租户访问返回空/403
    Evidence: .sisyphus/evidence/task-31-isolation.txt
  ```

  **Commit**: `test: phase4 integration tests`，pre-commit `./mvnw.cmd -Dtest=Phase4IntegrationTest test`

- [x] 32. 全栈 MySQL 8 验证 + 测试报告

  **What to do**:
  - 在 MySQL 8 (local profile 连真实库) 跑全量迁移 V1→V39 + 全测试套件
  - 撰写测试报告 `doc/第4阶段测试报告.md`（迁移状态、测试用例结果、覆盖场景）

  **Must NOT do**: 不改生产配置；不跳过迁移完整性

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: NO | Wave 4 | Blocks: T34 | Blocked By: T31

  **References**:
  - Pattern: `doc/第3阶段成本分析与合同深化测试报告.md` — 测试报告结构
  - Pattern: README"开发进度"表 — 需更新第4阶段行
  - WHY: 沿用第3阶段测试报告格式，证明 MySQL 8 全栈通过

  **Acceptance Criteria**:
  - [ ] `doc/第4阶段测试报告.md` 生成
  - QA Scenarios:
  ```
  Scenario: MySQL 8 全栈迁移+测试（happy）
    Tool: Bash
    Steps:
      1. 连 MySQL 8 cgc_pms 库，./mvnw.cmd test
      2. 断言 V1→V39 迁移成功 + 测试全绿
    Expected Result: BUILD SUCCESS
    Evidence: .sisyphus/evidence/task-32-mysql.txt
  ```

  **Commit**: `docs: phase4 test report`

- [x] 33. 用户手册 + 管理员手册

  **What to do**:
  - `doc/第4阶段用户操作手册.md`（组织/成员/字典/库存/发票/消息/待办 操作指引）
  - `doc/第4阶段管理员手册.md`（部署、迁移、权限配置、审批模板配置）

  **Must NOT do**: 不编造未实现的功能

  **Recommended Agent Profile**:
  - **Category**: `writing`
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: YES | Wave 4 | Blocks: 无(交付物) | Blocked By: Wave2/3 功能稳定

  **References**:
  - Pattern: `README.md` — 文档风格
  - Pattern: `doc/开发文档_v2.3/08_测试验收与上线运维方案.md` — 运维手册参考
  - WHY: 手册需与实际实现一致，依据已完成功能撰写

  **Acceptance Criteria**:
  - [ ] 两份手册生成
  - QA Scenarios:
  ```
  Scenario: 手册覆盖全部 Phase4 功能（happy）
    Tool: Bash (grep)
    Steps:
      1. 断言用户手册含 组织/成员/字典/库存/发票/消息/待办 各章节
      2. 断言管理员手册含 部署/迁移/权限 章节
    Expected Result: 章节完整
    Evidence: .sisyphus/evidence/task-33-manuals.txt
  ```

  **Commit**: `docs: phase4 user + admin manuals`

- [x] 34. 真实部署（docker-compose 到目标主机）+ 迁移 dry-run + 冒烟脚本 + 回滚预案

  **What to do**:
  - 先对生产克隆库 dry-run V33→V39 迁移，捕获 flyway_schema_history 状态
  - 用 `deploy/docker-compose.yml` 部署到目标主机（MySQL+Redis+MinIO+app）
  - 冒烟脚本 `scripts/phase4-smoke.sh`：post-deploy curl health/login + 各方向至少 1 读端点 200 + 查 flyway_schema_history 含 V33+
  - 回滚预案：DB 快照 + 上一镜像 tag
  - **高风险动作**：部署前确认目标主机、是否有存量数据、维护窗口、回滚责任人

  **Must NOT do**:
  - 不在未确认目标主机/无备份情况下对生产库迁移
  - 不跳过 dry-run

  **Recommended Agent Profile**:
  - **Category**: `deep` — 部署高风险，需谨慎编排
  - **Skills**: []

  **Parallelization**: Can Run In Parallel: NO | Wave 4 末 | Blocks: F1-F4 | Blocked By: T31, T32

  **References**:
  - Pattern: `deploy/docker-compose.yml` + `deploy/.env.example` — 部署配置
  - Pattern: `doc/开发文档_v2.3/08_测试验收与上线运维方案.md` — 上线步骤/回滚方案
  - Pattern: README"快速启动" — 启动命令
  - WHY: 真实部署须用既有 docker-compose；回滚遵循运维方案

  **Acceptance Criteria**:
  - [ ] 部署完成 + 冒烟脚本全绿
  - QA Scenarios:
  ```
  Scenario: 部署后冒烟（happy）
    Tool: Bash
    Steps:
      1. bash scripts/phase4-smoke.sh <host>
      2. 断言 /api/actuator/health 200 UP
      3. 断言各方向读端点(org/inventory/invoice/notifications) 200
      4. 断言 flyway_schema_history 含 version>=33
    Expected Result: 冒烟全绿
    Evidence: .sisyphus/evidence/task-34-smoke.txt

  Scenario: 迁移 dry-run 安全（edge）
    Tool: Bash
    Steps:
      1. 对生产克隆库跑 V33→V39
      2. 断言无 checksum/重复列/PK 冲突
    Expected Result: dry-run 成功
    Evidence: .sisyphus/evidence/task-34-dryrun.txt
  ```

  **Commit**: `chore(deploy): phase4 go-live scripts + smoke test`

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 评审代理并行运行。全部 APPROVE 后，呈现汇总给用户并获取显式"okay"才算完成。
> **不得在验证后自动收尾。等待用户显式批准。** 拒绝/反馈 → 修复 → 重跑 → 再呈现 → 等 okay。

- [x] F1. **计划合规审计** — `oracle`
  通读计划。逐条"Must Have"验证实现存在（读文件、curl 端点、跑命令）。逐条"Must NOT Have"在代码库搜禁止模式——发现即以 file:line 拒绝（重点：database/migration 改动、created_at 新表、UserContext 异步读取、WebSocket、库存计价、字典缓存、WorkflowEngine 核心改动）。核对 `.sisyphus/evidence/` 证据文件存在。
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **代码质量review** — `unspecified-high`
  跑 `./mvnw.cmd test` + 前端 `pnpm type-check`/`build`。审查改动文件：`as any`/`@ts-ignore`、空 catch、生产 console.log、注释掉的代码、未用 import。检查 AI slop：冗余注释、过度抽象、泛型命名(data/result/item/temp)。核验每个新 mapper 查询带 tenant `.eq`（ast_grep）。
  Output: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [x] F3. **真实手动 QA** — `unspecified-high`（含 `playwright` skill）
  干净状态起步。执行每个任务的每个 QA 场景——按精确步骤、采集证据。测试跨任务集成（出入库→台账→消息→抄送联动）。边界：空库存出库、重复发票号、跨租户访问、UserContext 缺失时消息 tenant 正确。存 `.sisyphus/evidence/final-qa/`。
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **范围保真核查** — `deep`
  逐任务：读"What to do"，读实际 diff(git log/diff)。验证 1:1——规格内全部实现(无遗漏)，规格外无多建(无蔓延)。核对"Must NOT do"合规。检测跨任务污染：任务 N 改了任务 M 的文件。标记账外改动。
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

> 每个任务完成后按 `type(scope): desc` 提交。后端先于前端。迁移单独提交。
- 迁移类(T1-T7): `feat(db): V33-V39 phase4 migrations`
- 后端模块(T9-T22): `feat(<module>): ...`，pre-commit `./mvnw.cmd test`
- 前端(T23-T30): `feat(ui): ...`，pre-commit `pnpm type-check`
- 部署(T34): `chore(deploy): phase4 go-live scripts`

---

## Success Criteria

### Verification Commands
```bash
cd backend && ./mvnw.cmd test                    # 期望: BUILD SUCCESS, 0 failures
cd backend && ./mvnw.cmd -Dtest=MigrationIntegrityTest test  # 期望: 空库 V1→V39 全绿
cd frontend-admin && pnpm type-check && pnpm build           # 期望: 0 errors
# 部署后冒烟(目标主机):
curl -i https://<host>/api/actuator/health      # 期望: 200 UP
```

### Final Checklist
- [x] All "Must Have" present
- [x] All "Must NOT Have" absent（F1/F4 验证）
- [x] All tests pass（含新 TDD + 集成）
- [x] 空库迁移 V1→V39 无错误
- [x] 真实部署冒烟全绿 + flyway_schema_history 含 V33+
