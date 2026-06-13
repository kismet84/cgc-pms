# 审查问题修复计划

## TL;DR

> **Quick Summary**: 修复全面项目审查发现的 24 项问题（P0~P3），涵盖安全加固、代码质量、架构优化、前端改进、数据库修复、配置部署。全部采用 TDD (RED-GREEN-REFACTOR)。
>
> **Deliverables**:
> - V42 角色权限补充 + sys_role_menu 种子数据
> - 生产配置修复（useSSL、SSE 缓冲、Docker HEALTHCHECK）
> - 输入校验补齐（16+ 端点 + 6 实体）
> - Mass Assignment 防护（DTO 或 @JsonIgnore）
> - 性能优化（Dashboard N+1、索引补充）
> - 代码去重（DTF ×27、resolveDefaultSubjectId ×4）
> - WorkflowEngine 拆分 + WorkflowController 鉴权硬化
> - 前端修复（空 catch、API module、aria-label）
> - CI/CD 增强 + 日志过滤 + JWT TTL 调整
>
> **Estimated Effort**: XL（24 任务 + 4 审查，分 5 个并行波次）
> **Parallel Execution**: YES — 5 waves
> **Critical Path**: Wave 1 → Wave 2 → Wave 3 → Wave 4 → Wave Final

---

## Context

### Original Request
基于 `doc/全面项目审查报告_2026-06-13.md` 的 24 项问题制定修复计划。

### Interview Summary
**Key Discussions**:
- **修复范围**: 全部 24 项（P0~P3）
- **测试策略**: TDD — 每个修复先写失败测试，再实现修复
- **技术栈**: Java 21 + Spring Boot 3.3 + MyBatis-Plus 3.5 / Vue 3 + TypeScript

### Metis Review
**Identified Gaps** (addressed):
- 独立确认了 P0-2~P0-23 的文件位置和行号
- 无新增阻塞问题
- 建议 Wave 1 的 T4/T5（输入校验 + Mass Assignment）联动处理

---

## Work Objectives

### Core Objective
将 24 项审查问题按优先级分波次修复，双环境测试全量通过为门禁。

### Definition of Done
- [ ] 后端 174/174 全量测试通过（MySQL + H2）
- [ ] 前端 `pnpm build` 零错误
- [ ] 所有新增测试通过
- [ ] P0 项全部修复验证

### Must Have
- P0 全部 5 项 100% 修复
- 每个修复有对应 TDD 测试
- 不影响已有 174 测试

### Must NOT Have (Guardrails)
- 不新增业务功能
- 不引入新第三方依赖
- 不修改业务逻辑（仅修复缺陷/加固）
- 不更改公共 API 契约（除非安全必要）

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES（后端 JUnit 5 + MockMvc；前端 Vitest）
- **Automated tests**: TDD（RED-GREEN-REFACTOR）
- **Framework**: JUnit 5 + MockMvc（后端）/ Vitest + @vue/test-utils（前端）

### QA Policy
Every task MUST include agent-executed QA scenarios.
Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (P0 阻断 — MAX PARALLEL, 5 tasks):
├── T1: V42 角色权限补充 [quick]
├── T2: docker-compose useSSL 修复 [quick]
├── T3: Nginx SSE proxy_buffering 修复 [quick]
├── T4: 输入校验补齐 + 实体 Jakarta Validation [unspecified-high]
└── T5: Mass Assignment 防护 — DTO/@JsonIgnore [unspecified-high]

Wave 2 (P1 高优 — MAX PARALLEL, 8 tasks):
├── T6: Dashboard N+1 批量查询 [deep]
├── T7: 提取共享 CostSubjectResolver [deep]
├── T8: 提取 DateTimeUtils 替换 27× DTF [quick]
├── T9: WorkflowEngine 拆分 [deep]
├── T10: WorkflowController @PreAuthorize 硬化 [quick]
├── T11: created_at/created_time 命名统一 [deep]
├── T12: Docker HEALTHCHECK + profile 修正 [quick]
└── T13: 清理 stale database/migration/ 目录 [quick]

Wave 3 (P2 中优 — MAX PARALLEL, 7 tasks):
├── T14: NotificationBell 空 catch 修复 [visual-engineering]
├── T15: cost API module 重构 [quick]
├── T16: NumberFormatException 日志补齐 [quick]
├── T17: 13 个 Service 添加 @Slf4j [quick]
├── T18: CORS allowedHeaders 收紧 [quick]
├── T19: AuthController/NotificationController @PreAuthorize 补充 [quick]
└── T20: mat_purchase_request_item.material_id 索引 [quick]

Wave 4 (P3 低优 — MAX PARALLEL, 4 tasks):
├── T21: JWT access token TTL 缩减 [quick]
├── T22: 日志敏感数据 RegexFilter [quick]
├── T23: CI/CD Docker build/push + deploy [quick]
└── T24: Vue 组件 aria-label 补齐 [visual-engineering]

Wave FINAL (After ALL — 4 并行审查):
├── F1: Plan Compliance Audit [oracle]
├── F2: Code Quality Review [unspecified-high]
├── F3: Real Manual QA [unspecified-high]
└── F4: Scope Fidelity Check [deep]
```

---

## TODOs

### Wave 1 — P0 阻断（全部并行）

- [x] 1. **V42 种子角色权限补充**

  **What to do**:
  - 在 V42 MySQL 迁移末尾追加 `INSERT IGNORE INTO sys_role_menu (role_id, menu_id)` 语句
  - MATERIAL_CLERK (role_id=5): 关联库存管理、仓库管理、出入库、采购申请等菜单
  - FINANCE (role_id=6): 关联付款管理、发票管理、结算管理、成本台账等菜单
  - 同步更新 H2 版本 V42 迁移
  - 写测试：创建 MATERIAL_CLERK 用户 → 登录 → 验证菜单非空

  **Must NOT do**:
  - 不要创建新菜单（使用已有菜单 ID）
  - 不要修改已有角色权限

  **Recommended Agent Profile**:
  - **Category**: `quick` — SQL 迁移 + 测试
  - **Skills**: `[]`

  **Parallelization**: Wave 1（与 T2-T5 并行）

  **References**:
  - `backend/src/main/resources/db/migration/V42__seed_material_warehouse_cost_subject.sql` — 种子数据位置
  - `backend/src/main/resources/db/migration-h2/V42__seed_material_warehouse_cost_subject.sql` — H2 版本
  - `backend/src/main/resources/db/migration/V39__seed_permissions.sql` — 参考已有 sys_role_menu 模式

  **QA Scenarios**:
  ```
  Scenario: MATERIAL_CLERK role has non-empty menu
    Tool: Bash (curl)
    Preconditions: Flyway migration completed
    Steps:
      1. Create test user with role_id=5 (MATERIAL_CLERK)
      2. Login as test user
      3. GET /api/auth/userinfo → verify roleCodes includes MATERIAL_CLERK
      4. GET /api/sys/menu/tree → verify list is non-empty
    Expected Result: Menu list non-empty, includes inventory-related items
    Evidence: .sisyphus/evidence/task-1-role-menu.txt

  Scenario: FINANCE role has non-empty menu
    Tool: Bash (curl)
    Steps:
      1. Create test user with role_id=6 (FINANCE)
      2. Login and verify menu includes payment/invoice/settlement items
    Expected Result: Menu non-empty, finance-related items present
    Evidence: .sisyphus/evidence/task-1-finance-menu.txt
  ```

  **Commit**: YES
  - Message: `fix(db): add sys_role_menu entries for MATERIAL_CLERK and FINANCE roles`
  - Files: `V42__seed_*.sql` (MySQL + H2)

- [x] 2. **docker-compose.prod.yml useSSL 修复**

  **What to do**:
  - 修改 `deploy/docker-compose.prod.yml:132` 的 `SPRING_DATASOURCE_URL`，将 `useSSL=false` 改为 `useSSL=true`
  - 如 MySQL 容器未配置 TLS 证书，先添加 `requireSSL=true` 到 MySQL 配置
  - 写测试：启动 compose → curl health → 验证 SSL 连接日志

  **Must NOT do**:
  - 不要跳过 MySQL 端的 SSL 配置就强制 useSSL=true（会导致连接失败）

  **Recommended Agent Profile**:
  - **Category**: `quick` — 配置修改 + Docker 验证
  - **Skills**: `[]`

  **Parallelization**: Wave 1（与 T1/T3-T5 并行）

  **References**:
  - `deploy/docker-compose.prod.yml:132` — 需修改行
  - `backend/src/main/resources/application-prod.yml` — 确认 prod 配置已启用 useSSL

  **QA Scenarios**:
  ```
  Scenario: Backend connects to MySQL with SSL
    Tool: Bash (docker compose)
    Steps:
      1. docker compose -f deploy/docker-compose.prod.yml up -d
      2. docker logs cgc-pms-backend | Select-String "SSL"
    Expected Result: Log shows SSL connection established (no "useSSL=false" warning)
    Evidence: .sisyphus/evidence/task-2-ssl-log.txt
  ```

  **Commit**: YES
  - Message: `fix(deploy): enable useSSL in production docker-compose`
  - Files: `deploy/docker-compose.prod.yml`

- [x] 3. **Nginx SSE proxy_buffering 修复**

  **What to do**:
  - 在 `frontend-admin/nginx.conf` 的 `/api/` location 块中添加 `proxy_buffering off;`
  - 添加 SSE 相关超时配置：`proxy_read_timeout 86400s;` `proxy_send_timeout 86400s;`
  - 写测试：curl SSE endpoint → 验证事件流实时到达

  **Must NOT do**:
  - 不要全局关闭 buffering（仅 /api/ 或 /api/notifications/stream）

  **Recommended Agent Profile**:
  - **Category**: `quick` — Nginx 配置修改
  - **Skills**: `[]`

  **Parallelization**: Wave 1（与 T1-T2/T4-T5 并行）

  **References**:
  - `frontend-admin/nginx.conf:98-118` — /api/ location 块

  **QA Scenarios**:
  ```
  Scenario: SSE events arrive without buffering delay
    Tool: Bash (curl)
    Preconditions: Docker compose with nginx + backend running
    Steps:
      1. curl -N -H "Authorization: Bearer <token>" https://localhost/api/notifications/stream
      2. Trigger a notification via another curl
      3. Verify SSE event received within 5 seconds
    Expected Result: SSE event arrives immediately (no buffering delay)
    Evidence: .sisyphus/evidence/task-3-sse-stream.txt
  ```

  **Commit**: YES
  - Message: `fix(deploy): disable proxy_buffering for SSE in nginx config`
  - Files: `frontend-admin/nginx.conf`

- [x] 4. **输入校验补齐 — 实体 + Controller @Valid**

  **What to do**:
  - 为 `PayInvoice` 实体添加: `@NotNull` on invoiceNo/amount, `@NotBlank` on invoiceType
  - 为 `PayApplication` 实体添加: `@NotNull` on contractId/amount, `@Positive` on amount
  - 为 `PayRecord` 实体添加: `@NotNull` on payApplicationId/amount
  - InvoiceController: 所有 @RequestBody 加 `@Valid`
  - PayApplicationController: 所有 @RequestBody 加 `@Valid`
  - PayRecordController: 所有 @RequestBody 加 `@Valid`
  - VarOrderController.batchSaveItems: 加 `@Valid`
  - StlSettlementController.batchSaveItems: 加 `@Valid`
  - 写测试：发送无效数据 → 验证 400 + 校验错误消息

  **Must NOT do**:
  - 不要修改业务逻辑
  - 不要过度校验（只加必要的 @NotNull/@NotBlank/@Positive）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 多文件实体 + Controller 修改
  - **Skills**: `[]`

  **Parallelization**: Wave 1（与 T1-T3/T5 并行）

  **References**:
  - `backend/.../invoice/entity/PayInvoice.java` — 实体
  - `backend/.../payment/entity/PayApplication.java` — 实体
  - `backend/.../payment/entity/PayRecord.java` — 实体
  - `backend/.../invoice/controller/InvoiceController.java` — Controller
  - `backend/.../contract/entity/CtContract.java` — 参考已有校验注解模式

  **QA Scenarios**:
  ```
  Scenario: Create invoice with null amount returns 400
    Tool: Bash (curl)
    Steps:
      1. POST /api/invoices with JSON body missing "amount" field
    Expected Result: HTTP 400 with validation error message containing "amount"
    Evidence: .sisyphus/evidence/task-4-validation-400.txt

  Scenario: Create invoice with valid data returns 200
    Tool: Bash (curl)
    Steps:
      1. POST /api/invoices with valid JSON body (all required fields)
    Expected Result: HTTP 200, invoice created
    Evidence: .sisyphus/evidence/task-4-validation-ok.txt
  ```

  **Commit**: YES
  - Message: `fix(security): add input validation to invoice/payment entities and controllers`
  - Files: `PayInvoice.java`, `PayApplication.java`, `PayRecord.java`, `InvoiceController.java`, `PayApplicationController.java`, `PayRecordController.java`

- [x] 5. **Mass Assignment 防护**

  **What to do**:
  - 为 `PayInvoice`、`PayApplication`、`PayRecord`、`VarOrder`、`StlSettlement` 实体添加 DTO 类
  - 在 DTO 中只暴露客户端可设置的字段
  - 修改 Controller 使用 DTO 而非 Entity 作为 @RequestBody
  - 或在敏感字段（tenantId, createdBy, updatedBy, deletedFlag）加 `@JsonProperty(access = READ_ONLY)` 或 `@JsonIgnore`
  - 写测试：发送包含 tenantId=999 的请求 → 验证 tenantId 不被篡改

  **Must NOT do**:
  - 不要改动已有正常工作的接口（逐步迁移）
  - 优先使用 @JsonIgnore 方案（改动最小），DTO 方案留 P2

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 安全加固，需理解 MyBatis-Plus FieldFill 机制
  - **Skills**: `[]`

  **Parallelization**: Wave 1（与 T1-T4 并行）

  **References**:
  - `backend/.../common/entity/BaseEntity.java` — 含 createdBy/updatedBy/deletedFlag/remark
  - `backend/.../invoice/controller/InvoiceController.java` — Controller
  - `backend/.../contract/controller/CtContractController.java` — 参考已有 DTO 模式

  **QA Scenarios**:
  ```
  Scenario: Attacker cannot override tenantId via request body
    Tool: Bash (curl)
    Steps:
      1. POST /api/invoices with JSON including "tenantId": 999
      2. GET /api/invoices/{id} → verify tenantId is NOT 999 (should be original user's tenantId)
    Expected Result: tenantId unchanged (retains authenticated user's tenant)
    Evidence: .sisyphus/evidence/task-5-mass-assign.txt
  ```

  **Commit**: YES
  - Message: `fix(security): prevent mass assignment on sensitive entity fields`
  - Files: Entity files + Controller files


### Wave 2 — P1 高优（全部并行，依赖 Wave 1 完成）

- [x] 6. **Dashboard N+1 批量查询优化**

  **What to do**:
  - 在 `DashboardService.getManagementView()` 中将循环内的 `getProjectSummary()` 改为批量查询
  - 新增 `CostSummaryService.getBatchProjectSummaries(List<Long> projectIds)` 方法
  - 一次 SQL 查询获取所有项目的成本汇总数据
  - 写测试：创建 5 个项目 → 验证 SQL 查询数 ≤ 10（而非 25+）

  **Must NOT do**: 不改变 Dashboard 返回数据结构

  **Recommended Agent Profile**: `deep` | **Skills**: `[]`
  **Parallelization**: Wave 2（与 T7-T13 并行）| **Blocked By**: Wave 1

  **References**: `DashboardService.java:338` / `CostSummaryService.java`

  **QA Scenarios**:
  ```
  Scenario: 5 projects dashboard uses batch query
    Steps: Create 5 projects → GET /api/dashboard/management → SQL log ≤ 2 for cost
    Expected: SQL queries ≤ 2 (batch)
    Evidence: .sisyphus/evidence/task-6-n1-fix.txt
  ```
  **Commit**: `perf(dashboard): batch-load project summaries to fix N+1`

- [x] 7. **提取共享 CostSubjectResolver**

  **What to do**:
  - 创建 `com.cgcpms.cost.strategy.CostSubjectResolver` 工具类
  - 将 4 个 Strategy 中的 `resolveDefaultSubjectId()` / `findSubjectByType()` 提取
  - 4 个策略类改为注入 CostSubjectResolver
  - 写测试：全量测试通过

  **Must NOT do**: 不改变 resolve 业务逻辑

  **Recommended Agent Profile**: `deep` | **Skills**: `[]`
  **Parallelization**: Wave 2（与 T6/T8-T13 并行）

  **References**: `ContractCostStrategy.java:109` / `SubMeasureCostStrategy.java:107` / `MaterialReceiptCostStrategy.java:107` / `CtContractChangeCostStrategy.java:118`

  **QA Scenarios**:
  ```
  Scenario: Full test suite passes after extract
    Steps: .\mvnw.cmd test → 174/174 pass
    Evidence: .sisyphus/evidence/task-7-refactor-test.txt
  ```
  **Commit**: `refactor(cost): extract duplicate CostSubjectResolver`

- [x] 8. **提取 DateTimeUtils 替换 27× DTF**

  **What to do**:
  - 创建 `com.cgcpms.common.util.DateTimeUtils` 含 `DTF`, `DATE_FMT`, `DATE_COMPACT`
  - 全局搜索替换 27 个 Service 中的局部 DTF 为 `DateTimeUtils.DTF`
  - 写测试：全量测试通过

  **Must NOT do**: 不遗漏任何 Service

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 2（与 T6-T7/T9-T13 并行）

  **References**: 27 个 Service 文件（grep `DateTimeFormatter.ofPattern`）

  **QA Scenarios**:
  ```
  Scenario: All tests pass after DTF refactor
    Steps: .\mvnw.cmd clean test → 174/174
    Evidence: .sisyphus/evidence/task-8-dtf-test.txt
  ```
  **Commit**: `refactor(common): extract DateTimeFormatter to DateTimeUtils`

- [x] 9. **WorkflowEngine 拆分**

  **What to do**:
  - 提取 `WorkflowSubmitService` (submit/resubmit)、`WorkflowApprovalService` (approve/reject)、`WorkflowTaskService` (transfer/addSign)、`WorkflowWithdrawService` (withdraw)
  - WorkflowEngine 保留为门面委托
  - 写测试：16/16 WorkflowEngineIntegrationTest 通过

  **Must NOT do**: 不改变审批行为

  **Recommended Agent Profile**: `deep` | **Skills**: `[]`
  **Parallelization**: Wave 2（与 T6-T8/T10-T13 并行）

  **References**: `WorkflowEngine.java` (823 行) / `WorkflowEngineIntegrationTest.java`

  **QA Scenarios**:
  ```
  Scenario: 16/16 workflow tests pass after split
    Steps: .\mvnw.cmd test -Dtest=WorkflowEngineIntegrationTest → 16/16
    Evidence: .sisyphus/evidence/task-9-workflow-test.txt
  ```
  **Commit**: `refactor(workflow): split WorkflowEngine into focused services`

- [x] 10. **WorkflowController @PreAuthorize 硬化**

  **What to do**:
  - approve → `@PreAuthorize("hasAuthority('workflow:approve')")`
  - reject → `@PreAuthorize("hasAuthority('workflow:reject')")`
  - transfer → `@PreAuthorize("hasAuthority('workflow:transfer')")`
  - addSign → `@PreAuthorize("hasAuthority('workflow:add-sign')")`
  - withdraw/resubmit → 对应权限码
  - 确认权限码存在于 V39/V40 种子数据
  - 写测试：无权限用户 → 403

  **Must NOT do**: 不破坏已有审批流程

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 2（与 T6-T9/T11-T13 并行）

  **References**: `WorkflowController.java:91-143`

  **QA Scenarios**:
  ```
  Scenario: Unauthorized user gets 403 on approve
    Steps: Login as no-permission user → POST /api/workflow/tasks/{id}/approve
    Expected: 403 Forbidden
    Evidence: .sisyphus/evidence/task-10-auth-403.txt
  ```
  **Commit**: `fix(security): harden WorkflowController with specific @PreAuthorize`

- [x] 11. **created_at/created_time 命名统一**

  **What to do**:
  - 创建 V44 迁移：`ALTER TABLE ... RENAME COLUMN created_time TO created_at`
  - 覆盖 V22~V38 所有使用 created_time/updated_time 的表
  - 同步 H2 迁移 + MyMetaObjectHandler 统一
  - 写测试：全量 174/174 通过

  **Must NOT do**: 不直接改生产库

  **Recommended Agent Profile**: `deep` | **Skills**: `[]`
  **Parallelization**: Wave 2（与 T6-T10/T12-T13 并行）

  **References**: V22~V38 迁移 / `MyMetaObjectHandler.java`

  **QA Scenarios**:
  ```
  Scenario: All tests pass after column rename
    Steps: Run V44 → .\mvnw.cmd clean test → 174/174
    Evidence: .sisyphus/evidence/task-11-rename-test.txt
  ```
  **Commit**: `refactor(db): unify created_at naming across all tables (V44)`

- [x] 12. **Docker HEALTHCHECK + profile 修正**

  **What to do**:
  - backend/Dockerfile: `HEALTHCHECK CMD curl -f http://localhost:8080/api/actuator/health || exit 1`
  - frontend-admin/Dockerfile: `HEALTHCHECK CMD curl -f http://localhost:80/ || exit 1`
  - backend/Dockerfile: `ENV SPRING_PROFILES_ACTIVE=dev` → `prod`
  - 添加 `frontend-admin/.dockerignore`

  **Must NOT do**: 不在 HEALTHCHECK 暴露敏感端点

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 2（与 T6-T11/T13 并行）

  **QA Scenarios**:
  ```
  Scenario: Container shows healthy
    Steps: docker build → docker run → sleep 30 → docker inspect → "healthy"
    Evidence: .sisyphus/evidence/task-12-healthcheck.txt
  ```
  **Commit**: `fix(deploy): add HEALTHCHECK, default to prod profile`

- [x] 13. **清理 stale database/migration/ 目录**

  **What to do**:
  - 删除 `database/migration/`（21 stale 文件）
  - README.md: 路径引用改为 `backend/src/main/resources/db/migration/`
  - 验证 43 迁移在正确位置

  **Must NOT do**: 不删 `backend/.../db/migration/` 中文件

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 2（与 T6-T12 并行）

  **QA Scenarios**:
  ```
  Scenario: Flyway shows 43 migrations from correct path
    Steps: .\mvnw.cmd flyway:info -Dspring.profiles.active=dev → 43 rows
    Evidence: .sisyphus/evidence/task-13-flyway-info.txt
  ```
  **Commit**: `chore(db): remove stale database/migration/ directory`


### Wave 3 — P2 中优（全部并行，依赖 Wave 2 完成）

- [x] 14. **NotificationBell 空 catch 修复**

  **What to do**:
  - `NotificationBell.vue:52,65,100,109`: 4 处空 `catch {}` 添加 `console.error()` + 用户反馈
  - 写 Vitest 测试：模拟 API 失败 → 验证错误被记录

  **Must NOT do**: 不改变组件结构

  **Recommended Agent Profile**: `visual-engineering` | **Skills**: `["playwright"]`
  **Parallelization**: Wave 3（与 T15-T20 并行）

  **References**: `components/NotificationBell.vue:52,65,100,109`

  **QA Scenarios**:
  ```
  Scenario: API error shows console.error and user feedback
    Tool: Playwright
    Steps: Mock API to fail → observe console.error + UI feedback
    Evidence: .sisyphus/evidence/task-14-catch-fix.png
  ```
  **Commit**: `fix(frontend): add error logging to NotificationBell catch blocks`

- [x] 15. **cost API module 重构**

  **What to do**:
  - 创建 `api/modules/costSubject.ts` 封装 `/cost-subjects/tree` 端点
  - `cost/ledger.vue` 和 `cost-target/edit.vue` 改为使用新 API module
  - 移除直接 `import { request } from '@/api/request'`
  - 写测试：API module 返回正确类型

  **Must NOT do**: 不改变 API 行为

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 3（与 T14/T16-T20 并行）

  **References**: `pages/cost/ledger.vue:17` / `pages/cost-target/edit.vue:8` / 参考 `api/modules/contract.ts`

  **QA Scenarios**:
  ```
  Scenario: Cost subject tree loads via API module
    Tool: Playwright
    Steps: Navigate to cost ledger → verify tree data loaded
    Evidence: .sisyphus/evidence/task-15-api-module.png
  ```
  **Commit**: `refactor(frontend): extract cost-subject API to module`

- [x] 16. **NumberFormatException 日志补齐**

  **What to do**:
  - 10 个 Service 中的 `catch (NumberFormatException ignored) {}` 改为 `catch (NumberFormatException e) { log.warn("...", e); }`
  - 记录具体哪个 code 解析失败
  - 写测试：验证日志输出

  **Must NOT do**: 不改变异常处理后的逻辑

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 3（与 T14-T15/T17-T20 并行）

  **References**: `CtContractService.java:108` / `CtContractChangeService.java:81` / `VarOrderService.java:124` 等

  **QA Scenarios**:
  ```
  Scenario: NumberFormatException is logged
    Steps: Trigger sequence number parse failure → verify log.warn output
    Evidence: .sisyphus/evidence/task-16-nfe-log.txt
  ```
  **Commit**: `fix(logging): add warn logging to NumberFormatException catches`

- [x] 17. **13 个 Service 添加 @Slf4j**

  **What to do**:
  - 为 `PmProjectService`、`PmProjectMemberService`、`MatWarehouseService`、`CostLedgerService`、`CostTargetService`、`SysUserService`、`SysRoleService`、`SysMenuService`、`SysDictTypeService`、`SysDictDataService`、`MdPartnerService`、`AuthService`、`TokenBlacklistService` 添加 `@Slf4j`
  - 在关键方法添加 log.info/debug

  **Must NOT do**: 不过度日志

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 3（与 T14-T16/T18-T20 并行）

  **References**: 13 个 Service 文件

  **QA Scenarios**:
  ```
  Scenario: Services log on key operations
    Steps: Run backend → trigger operations → verify log output
    Evidence: .sisyphus/evidence/task-17-slf4j.txt
  ```
  **Commit**: `chore(logging): add @Slf4j to 13 service classes`

- [x] 18. **CORS allowedHeaders 收紧**

  **What to do**:
  - `CorsConfig.java:24`: 将 `allowedHeaders("*")` 改为具体列表: `"Authorization", "Content-Type", "X-Refresh-Token"`
  - 写测试：OPTIONS preflight → 验证 Access-Control-Allow-Headers 非通配符

  **Must NOT do**: 不遗漏前端实际使用的 header

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 3（与 T14-T17/T19-T20 并行）

  **References**: `CorsConfig.java:24`

  **QA Scenarios**:
  ```
  Scenario: CORS preflight returns specific headers
    Steps: curl -X OPTIONS -H "Origin: http://localhost:5173" /api/auth/login
    Expected: Access-Control-Allow-Headers is "Authorization, Content-Type, X-Refresh-Token"
    Evidence: .sisyphus/evidence/task-18-cors.txt
  ```
  **Commit**: `fix(security): restrict CORS allowedHeaders to specific values`

- [x] 19. **AuthController + NotificationController @PreAuthorize 补充**

  **What to do**:
  - AuthController: GET /userinfo → `@PreAuthorize("isAuthenticated()")` + POST /logout → `@PreAuthorize("isAuthenticated()")`
  - NotificationController: GET /stream → `@PreAuthorize("isAuthenticated()")`
  - 写测试：未认证用户 → 401

  **Must NOT do**: 不加过度的 hasAuthority（这些端点只需认证即可）

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 3（与 T14-T18/T20 并行）

  **References**: `AuthController.java:51,56` / `NotificationController.java:108`

  **QA Scenarios**:
  ```
  Scenario: Unauthenticated SSE returns 401
    Steps: curl /api/notifications/stream without token → 401
    Evidence: .sisyphus/evidence/task-19-auth-401.txt
  ```
  **Commit**: `fix(security): add @PreAuthorize to AuthController and NotificationController`

- [x] 20. **mat_purchase_request_item.material_id 索引**

  **What to do**:
  - 创建 V45 迁移：`CREATE INDEX idx_mpi_material ON mat_purchase_request_item(material_id)`
  - 同步 H2 迁移
  - 写测试：EXPLAIN 验证索引被使用

  **Must NOT do**: 不创建多余索引

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 3（与 T14-T19 并行）

  **References**: V35 迁移中 `mat_purchase_request_item` 表定义

  **QA Scenarios**:
  ```
  Scenario: EXPLAIN shows index usage on material_id query
    Steps: EXPLAIN SELECT * FROM mat_purchase_request_item WHERE material_id = 1
    Expected: key = idx_mpi_material
    Evidence: .sisyphus/evidence/task-20-index.txt
  ```
  **Commit**: `perf(db): add index on mat_purchase_request_item.material_id (V45)`


### Wave 4 — P3 低优（全部并行，依赖 Wave 3 完成）

- [x] 21. **JWT access token TTL 缩减**

  **What to do**:
  - 修改 `application-dev.yml:48` 和 `application-prod.yml`: `access-token-expiration` 从 86400000 (24h) → 900000 (15min)
  - 确认 Refresh Token 流程正常（前端 401 自动 refresh）
  - 写测试：token 15min 后过期 → refresh 成功

  **Must NOT do**: 不改变 Refresh Token TTL

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 4（与 T22-T24 并行）

  **References**: `application-dev.yml:48` / `JwtProperties.java`

  **QA Scenarios**:
  ```
  Scenario: Token expires after 15min, refresh works
    Steps: Login → wait/simulate expiry → call API with expired token → refresh → retry
    Expected: 401 → refresh → 200
    Evidence: .sisyphus/evidence/task-21-jwt-ttl.txt
  ```
  **Commit**: `fix(security): reduce JWT access token TTL to 15 minutes`

- [x] 22. **日志敏感数据 RegexFilter**

  **What to do**:
  - 在 `logback-spring.xml` 添加 `RegexFilter` 过滤 `password|token|secret|authorization` 模式
  - 替换为 `***MASKED***`
  - 写测试：log.info("password=secret123") → 验证输出被脱敏

  **Must NOT do**: 不过滤级别/时间戳/类名

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 4（与 T21/T23-T24 并行）

  **References**: `logback-spring.xml`

  **QA Scenarios**:
  ```
  Scenario: Sensitive data masked in logs
    Steps: Trigger login with password → check log output
    Expected: password field shows ***MASKED***
    Evidence: .sisyphus/evidence/task-22-log-filter.txt
  ```
  **Commit**: `fix(logging): add sensitive data mask filter to logback`

- [x] 23. **CI/CD Docker build/push + deploy**

  **What to do**:
  - 在 `.github/workflows/ci.yml` 添加 `docker-build` job：构建 backend + frontend 镜像
  - 添加 `deploy` job（手动触发）：推送到 registry + SSH 部署
  - 写测试：CI 运行验证 YAML 语法正确

  **Must NOT do**: 不配置真实生产 registry 凭证（用 GitHub Secrets 引用）

  **Recommended Agent Profile**: `quick` | **Skills**: `[]`
  **Parallelization**: Wave 4（与 T21-T22/T24 并行）

  **References**: `.github/workflows/ci.yml`

  **QA Scenarios**:
  ```
  Scenario: CI builds Docker images successfully
    Steps: Push to branch → observe CI pipeline → docker-build job passes
    Evidence: .sisyphus/evidence/task-23-ci-docker.md
  ```
  **Commit**: `feat(ci): add Docker build/push and deploy jobs to CI pipeline`

- [x] 24. **Vue 组件 aria-label 补齐**

  **What to do**:
  - 为 `BasicLayout.vue` 图标按钮添加 `aria-label`（MenuFoldOutlined/MenuUnfoldOutlined/BellOutlined/QuestionCircleOutlined）
  - 为 `login/index.vue` logo 添加 `aria-hidden="true"`
  - 为"忘记密码"链接添加 `role="button"` + 键盘事件
  - 写测试：Playwright 验证 aria-label 属性存在

  **Must NOT do**: 不改变 UI 外观

  **Recommended Agent Profile**: `visual-engineering` | **Skills**: `["playwright"]`
  **Parallelization**: Wave 4（与 T21-T23 并行）

  **References**: `layouts/BasicLayout.vue` / `pages/login/index.vue`

  **QA Scenarios**:
  ```
  Scenario: Icon buttons have aria-label
    Tool: Playwright
    Steps: Navigate to dashboard → check sidebar icons for aria-label attribute
    Expected: Each icon button has non-empty aria-label
    Evidence: .sisyphus/evidence/task-24-aria.png
  ```
  **Commit**: `fix(a11y): add aria-label to interactive elements`


---

## Final Verification Wave

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results.

- [x] F1. **Plan Compliance Audit** — `oracle`
  Verify all 24 "What to do" items implemented. Check each "Must Have" present and "Must NOT Have" absent.
  Output: `Tasks [24/24] | Must Have [N/N] | Must NOT Have [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Run `.\mvnw.cmd clean test` + `pnpm build`. Check for regressions, new warnings, AI slop.
  Output: `Build [PASS/FAIL] | Tests [174/174] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high` (+ `playwright`)
  Execute all QA scenarios from all 24 tasks. Test cross-task integration.
  Output: `Scenarios [N/N pass] | Integration [N/N] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  Verify 1:1 — everything in spec was built, nothing beyond spec was built.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | VERDICT`

---

## Commit Strategy

- **Wave 1**: P0 fixes — V42 SQL, docker-compose, nginx, validation, mass assignment
- **Wave 2**: P1 fixes — Dashboard N+1, CostSubjectResolver, DateTimeUtils, WorkflowEngine split, @PreAuthorize, column rename (V44), Docker HEALTHCHECK, stale dir cleanup
- **Wave 3**: P2 fixes — NotificationBell, API module, NFE logging, @Slf4j, CORS, @PreAuthorize gaps, index (V45)
- **Wave 4**: P3 fixes — JWT TTL, log filter, CI/CD, aria-label

---

## Success Criteria

### Verification Commands
```powershell
# Backend tests (MySQL)
cd backend && .\mvnw.cmd clean test

# Backend tests (H2)
cd backend && .\mvnw.cmd clean test -Dspring.profiles.active=local

# Frontend build + test
cd frontend-admin && pnpm build && pnpm test:unit

# Docker verification
docker compose -f deploy/docker-compose.prod.yml config  # validate syntax
```

### Final Checklist
- [ ] All 24 items implemented
- [ ] 后端 174/174 测试通过（MySQL + H2）
- [ ] 前端 `pnpm build` + `pnpm test:unit` 通过
- [ ] P0 全部 5 项修复验证通过
- [ ] 无新增编译警告
- [ ] 无回归 bug

