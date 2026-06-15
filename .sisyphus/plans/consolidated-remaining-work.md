# 剩余工作合并计划 — 上线前审查 + 数据库重置

## TL;DR

> **Quick Summary**: 合并 `pre-release-review.md`（6 维度全量审查 + TDD 修复）和 `reset-database.md`（清空测试数据）两个未执行计划。先可选用数据库重置，再执行 6 维度并行审查 → 综合发现问题 → TDD 修复 P0/P1 → 最终门禁验证。
>
> **Deliverables**:
> - 可选的干净数据库（56 表 + 种子数据）
> - 6 份维度审查报告 + 1 份综合问题清单
> - N 个 TDD 修复任务（仅 P0/P1）
> - 最终部署安全检查（deploy/.env 硬编码密钥）
>
> **Estimated Effort**: Large（数据库 30min + 审查 1-2天 + 修复 3-5天）
> **Parallel Execution**: YES — 5 waves
> **Critical Path**: Wave 0(可选) → Wave 1(6路审查) → Wave 2(综合) → Wave 3(TDD修复) → Wave 4(验证)

---

## Context

### 合并来源

两个计划均已完成勾选但**从未执行**，现合并为一个：

| 原计划 | 规模 | 摘要 |
|--------|------|------|
| `pre-release-review.md` | XL | 6 维度并行全量审查 → 综合 → TDD 修复 → 验证门禁 |
| `reset-database.md` | Quick | Docker MySQL 清空后 Flyway 重建初始状态 |

### 审查发现的前置 P0 问题

在交叉验证中发现 `deploy/.env` 存在 **5 个硬编码明文密钥**，且 MySQL root 密码与应用密码相同——这是需要在审查启动前处理的安全缺陷。

---

## Work Objectives

### Core Objective
对 CGC-PMS 项目执行全新一轮上线前全量审查，修复所有 P0/P1 问题，确保测试版部署安全就绪。

### Concrete Deliverables
- `.sisyphus/evidence/review-1-security-config.md` — 安全与配置
- `.sisyphus/evidence/review-2-code-architecture.md` — 代码与架构
- `.sisyphus/evidence/review-3-frontend-api.md` — 前端与 API
- `.sisyphus/evidence/review-4-data-infra.md` — 数据与基础设施
- `.sisyphus/evidence/review-5-business-logic.md` — 业务逻辑
- `.sisyphus/evidence/review-6-fix-regression.md` — 修复回归验证
- `.sisyphus/evidence/synthesis-report.md` — 综合问题清单
- `deploy/.env.example` — 安全模板（移除真实密钥）

### Definition of Done
- [ ] 6 份审查报告生成（每份 ≤15 发现，标注 P 级别 + file:line）
- [ ] deploy/.env 硬编码密钥已替换为 `${VAR}` 占位符，真实 `.env` 已加入 `.gitignore`
- [ ] 综合报告完成去重和优先排序（P0/P1/P2/P3）
- [ ] 所有 P0 修复任务 TDD 完成（RED→GREEN→REFACTOR）
- [ ] 后端 `./mvnw test` ≥174 用例通过
- [ ] 前端 `pnpm build` 零 TypeScript 错误

### Must Have
- 6 维度审查：每维度 top-15 发现，含精确 file:line + 复现步骤
- deploy/.env 安全修复（P0 前置）
- 每个 P0 修复含 TDD 测试
- 数据库重置（可选，审查前执行）

### Must NOT Have (Guardrails)
- **不重新争论已定设计决策**：WorkflowEngine 拆分、DateTimeUtils 提取、CostSubjectResolver 提取均已完成，仅检查是否引入缺陷
- **不添加新功能**：发现描述中不得出现"应支持 X 功能"（归入 Backlog）
- **不深入审查移动端/脚本**：mobile/ 仅 README.md，确认存在即可
- **不替代业务方决策**：成本公式、审批流程仅检查结构性正确性
- **每维度 ≤15 条发现**：超出部分列报告末尾摘要
- **不修改任何 Flyway 迁移脚本**

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES（JUnit 5 + Vitest + Playwright）
- **Automated tests**: TDD（每个修复任务：RED → GREEN → REFACTOR）
- **Framework**: JUnit 5（后端）+ Vitest（前端）+ Playwright（E2E）

### QA Policy
所有验证由 agent 执行，证据保存到 `.sisyphus/evidence/`。
- **后端**: Bash (curl API + mvn test)
- **前端**: Playwright (浏览器验证 + 控制台检查)
- **数据库**: Bash (docker exec + SQL 查询)

---

## Execution Strategy

### Waves

```
Wave 0（可选 — 数据库重置 + 安全前置）:
├── T0: fix deploy/.env 硬编码密钥 [quick]
└── T0b: 数据库重置（DROP → CREATE → Flyway 重建） [quick]

Wave 1（6 路并行审查 — 最大并行度）:
├── T1: 安全与配置审查 [unspecified-high]
├── T2: 后端代码与架构审查 [deep]
├── T3: 前端与 API 契约审查 [visual-engineering]
├── T4: 数据与基础设施审查 [unspecified-high]
├── T5: 业务逻辑正确性审查 [deep]
└── T6: 修复回归验证（audit-fixes 24项） [deep]

Wave 2（综合 — 审查完成后）:
└── T7: 去重 + 分级 + 依赖映射 → 修复任务 [deep]

Wave 3（TDD 修复 — 取决于综合报告）:
├── T8-N: P0 修复（TDD，并行度取决于依赖）
└── TN+1-M: P1 修复（TDD）

Wave 4（最终门禁 — 4 路并行）:
├── F1: 后端回归测试 [oracle]
├── F2: 前端构建 + ESLint [unspecified-high]
├── F3: E2E 冒烟测试 [unspecified-high + playwright]
└── F4: 审查维度复扫 [deep]
```

### Agent Dispatch Summary

| Wave | Tasks | Agents |
|------|-------|--------|
| 0（可选） | 2 | quick × 2 |
| 1 | 6 | unspecified-high × 2, deep × 3, visual-engineering × 1 |
| 2 | 1 | deep |
| 3 | N (0-12) | quick / deep / unspecified-high |
| 4 | 4 | oracle + unspecified-high × 3 |

---

## TODOs

### Wave 0 — 可选前置（安全修复 + 数据库重置）

- [x] 0a. **修复 deploy/.env 硬编码密钥（P0 安全前置）**

  **What to do**:
  - 将 `deploy/.env` 重命名为 `deploy/.env.example`，所有真实密钥替换为 `${YOUR_MYSQL_ROOT_PASSWORD}` 等占位符
  - 在 `.gitignore` 中添加 `deploy/.env`（阻止真实密钥被提交）
  - 将原 `deploy/.env` 中的真实值复制到新文件 `deploy/.env`（不提交到 git）
  - 验证：`git status` 确认 `deploy/.env` 被忽略，`deploy/.env.example` 可提交

  **Must NOT do**:
  - 不要把真实密钥提交到 git
  - 不要删除原 `deploy/.env` 的内容（只是移出 git 跟踪）

  **Recommended Agent Profile**: `quick`
  **Parallelization**: Wave 0 | Blocks: none | Blocked By: none

  **QA Scenarios**:
  ```
  Scenario: deploy/.env 不再被 git 跟踪
    Tool: Bash
    Steps:
      1. git rm --cached deploy/.env
      2. git status → 确认 deploy/.env 不再出现在 staged/changed
      3. git check-ignore deploy/.env → 确认被 .gitignore 忽略
    Expected Result: deploy/.env 被 gitignore 排除
    Evidence: .sisyphus/evidence/task-0a-gitignore.txt

  Scenario: deploy/.env.example 可用作模板
    Tool: Bash
    Steps:
      1. grep "PASSWORD" deploy/.env.example
      2. 确认所有密码值均为 ${VARIABLE} 格式
    Expected Result: 零真实密码
    Evidence: .sisyphus/evidence/task-0a-env-safe.txt
  ```

  **Commit**: YES
  - Message: `fix(security): replace hardcoded secrets in deploy/.env with template`

- [ ] 0b. **数据库重置（可选 — 审查前清理测试数据）**

  **What to do**:
  - 停止后端容器
  - Redis FLUSHDB 清理缓存
  - MySQL DROP DATABASE cgc_pms → CREATE DATABASE cgc_pms
  - 清理 MinIO bucket 文件
  - 重启后端 → Flyway 自动执行 V1-V48 迁移
  - 验证：56 张表 + admin/admin123 可登录 + 种子数据完整
  - **跳过条件**：如果数据库状态已满意，可直接跳过此任务进入 Wave 1

  **Must NOT do**: 不删除 Docker 卷、不修改 Flyway 脚本

  **Recommended Agent Profile**: `quick`
  **Parallelization**: Wave 0 | Blocks: none | Blocked By: none（与 T0a 可并行）

  **QA Scenarios**:
  ```
  Scenario: Flyway 重建后表数量正确
    Tool: Bash
    Steps:
      1. docker exec cgc-pms-mysql-dev mysql -u cgc -p -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='cgc_pms'"
    Expected Result: 56
    Evidence: .sisyphus/evidence/task-0b-tables.txt

  Scenario: 种子数据完整
    Tool: Bash (curl)
    Steps:
      1. Login: admin/admin123 → 200
      2. GET /api/projects → total ≥ 2
      3. GET /api/contracts → total ≥ 3
    Expected Result: 种子项目/合同可查
    Evidence: .sisyphus/evidence/task-0b-seed-data.json
  ```

  **Commit**: NO（纯运维操作）

### Wave 1 — 6 维度并行审查

- [x] 1. **安全与配置审查**

  **What to do**:
  - 审查全部 39 个 Controller 的 `@PreAuthorize` 覆盖
  - 审查 JWT 实现：生成/刷新/黑名单/过滤器链（`auth/` 模块）
  - 审查 CORS 配置：各 profile 的 `allowedOrigins`/`allowedHeaders`
  - 审查文件上传安全：扩展名白名单（20 种）、大小限制（50MB）、路径注入
  - 审查日志脱敏：Logback 是否掩码 password/token/secret/authorization + phone/email/bankAccount
  - 审查全局异常处理：确认 `AuthorizationDeniedException` → 403（非 500）
  - 审查 Docker/Nginx 安全：非 root 用户、HEALTHCHECK、SSL、安全头
  - **产量**：≤15 发现，标注 [D1-NNN] | P级别 | file:line | 复现步骤 | 建议修复

  **Must NOT do**: 不审查 mobile/scripts/；不建议添加 WAF/IDS 等新安全功能

  **Recommended Agent Profile**: `unspecified-high`
  **Parallelization**: Wave 1 | Blocks: T7 | Blocked By: none

  **References**:
  - `backend/src/main/java/com/cgcpms/auth/` — JWT 认证完整实现
  - `backend/src/main/java/com/cgcpms/auth/config/CorsConfig.java`
  - `backend/src/main/java/com/cgcpms/common/exception/GlobalExceptionHandler.java`
  - `backend/src/main/java/com/cgcpms/file/controller/FileController.java`
  - `backend/src/main/resources/logback-spring.xml`
  - `deploy/docker-compose.prod.yml` + `frontend-admin/nginx.conf`

  **QA Scenarios**:
  ```
  Scenario: 无权限用户访问敏感端点返回 403（非 500）
    Tool: Bash (curl)
    Steps:
      1. 以普通用户登录 → curl GET /api/system/users
      2. 检查 HTTP 状态码
    Expected Result: 403 Forbidden 或 401 Unauthorized（不能是 500）
    Evidence: .sisyphus/evidence/task-1-auth-403.txt
  ```

  **Commit**: YES — `docs(review): add security and configuration review report`

- [x] 2. **后端代码与架构审查**

  **What to do**:
  - 搜索 N+1 查询：所有 Service 中循环内调用 Mapper 的模式
  - 搜索空 catch 块：`catch (Exception ignored)` / `catch (Exception e) {}`
  - 验证代码去重：DateTimeUtils 是否被 27 Service 统一引用（而非各自定义）
  - 审查输入校验：Controller create/update 端点是否有 `@Valid`
  - 审查 Mass Assignment：`@RequestBody` 是否绑定 Entity（应绑定 DTO）
  - 审查事务边界：`@Transactional` 传播行为、超时、只读优化
  - 审查超大类：Service > 500 行标记
  - **产量**：≤15 发现

  **Must NOT do**: 不质疑 WorkflowEngine 拆分等已有架构决策

  **Recommended Agent Profile**: `deep`
  **Parallelization**: Wave 1 | Blocks: T7 | Blocked By: none

  **References**:
  - `DashboardService.java` — 已知 N+1 位置
  - `WorkflowEngine.java` — 验证拆分完整性
  - `DateTimeUtils.java` — 验证 27 Service 引用
  - `PayApplicationController.java` + `InvoiceController.java` — 已知校验缺失

  **QA Scenarios**:
  ```
  Scenario: DateTimeUtils 引用一致性
    Tool: Bash (grep)
    Steps:
      1. grep -rn "DateTimeFormatter.ofPattern" backend/src/main/java/ | grep -v "DateTimeUtils"
    Expected Result: 0（所有 DTF 定义集中在 DateTimeUtils）
    Evidence: .sisyphus/evidence/task-2-dtf-consistency.txt
  ```

  **Commit**: YES — `docs(review): add backend code and architecture review report`

- [x] 3. **前端与 API 契约审查**

  **What to do**:
  - 审查路由守卫：白名单仅 `/login`，所有路由 `meta.requiresAuth`
  - 审查 Axios 拦截器：Token 刷新逻辑 + 401/403 处理
  - 搜索空 catch 块 + `(e: any)` 类型断言
  - 搜索 console.log/error 残留
  - 审查 API 绕过：页面直接用 `request()` 而非 API module
  - 审查路由映射正确性：ContractApproval 等路由是否指向正确组件
  - **产量**：≤15 发现

  **Recommended Agent Profile**: `visual-engineering`
  **Parallelization**: Wave 1 | Blocks: T7 | Blocked By: none

  **References**:
  - `frontend-admin/src/router/index.ts`
  - `frontend-admin/src/api/request.ts`
  - `frontend-admin/src/api/modules/contract.ts` — 已知 KPI 桩代码
  - `frontend-admin/src/components/NotificationBell.vue` — 已知空 catch

  **QA Scenarios**:
  ```
  Scenario: ESLint 错误基线
    Tool: Bash
    Steps:
      1. cd frontend-admin && pnpm lint 2>&1 | Select-String "error|warning" | Select-Object -Last 5
    Expected Result: 记录当前 errors/warnings 数量作为修复基线
    Evidence: .sisyphus/evidence/task-3-eslint-baseline.txt
  ```

  **Commit**: YES — `docs(review): add frontend and API contract review report`

- [x] 4. **数据与基础设施审查**

  **What to do**:
  - 审查 Flyway 迁移同步：MySQL ↔ H2 文件数量 + V48 一致性
  - 审查索引设计：外键列、查询高频列是否缺索引
  - 审查命名一致性：`created_at` vs `created_time` 分裂
  - 审查 Dockerfile：多阶段构建、非 root、HEALTHCHECK
  - 审查 docker-compose.prod.yml：useSSL、depends_on、资源限制
  - 审查 CI/CD：`.github/workflows/ci.yml` 触发条件 + 服务依赖
  - **产量**：≤15 发现

  **Recommended Agent Profile**: `unspecified-high`
  **Parallelization**: Wave 1 | Blocks: T7 | Blocked By: none

  **References**:
  - `backend/src/main/resources/db/migration/` + `db/migration-h2/`
  - `backend/Dockerfile` + `frontend-admin/Dockerfile`
  - `deploy/docker-compose.prod.yml`
  - `.github/workflows/ci.yml`

  **QA Scenarios**:
  ```
  Scenario: Flyway 迁移文件数量一致
    Tool: Bash
    Steps:
      1. Get-ChildItem backend/src/main/resources/db/migration/V*.sql | Measure-Object
      2. Get-ChildItem backend/src/main/resources/db/migration-h2/V*.sql | Measure-Object
    Expected Result: 两者数量一致
    Evidence: .sisyphus/evidence/task-4-flyway-count.txt
  ```

  **Commit**: YES — `docs(review): add data and infrastructure review report`

- [x] 5. **业务逻辑正确性审查**

  **What to do**:
  - 审查审批引擎：事务一致性（`isCritical`）、乐观锁（`taskVersion`）、幂等键
  - 审查成本计算：4 种 CostGenerationStrategy 的金额联动 + 幂等保护
  - 审查库存并发：乐观锁 @Version 正确性
  - 审查结算锁定：contractId 唯一性校验（已知可能缺失 → P0）
  - 审查质保金计算：warrantyRate 百分比 vs 比率混淆（已知可能 P1）
  - 审查 SSE 推送：Nginx proxy_buffering 是否阻断事件流
  - **产量**：≤15 发现

  **Recommended Agent Profile**: `deep`
  **Parallelization**: Wave 1 | Blocks: T7 | Blocked By: none

  **References**:
  - `WorkflowCoreService.java` — 审批核心逻辑
  - `CostGenerationStrategy` 4 种实现
  - `StlSettlementService.java` — 结算服务
  - `MatStockService.java` — 库存乐观锁
  - `frontend-admin/nginx.conf:98-118` — SSE proxy_buffering

  **QA Scenarios**:
  ```
  Scenario: 结算 contractId 唯一性校验
    Tool: Bash (grep)
    Steps:
      1. grep -n "contractId\|DUPLICATE\|unique" backend/.../settlement/service/StlSettlementService.java
    Expected Result: 找到或未找到唯一性检查（标记 P0 或 PASS）
    Evidence: .sisyphus/evidence/task-5-settlement-guard.txt
  ```

  **Commit**: YES — `docs(review): add business logic correctness review report`

- [x] 6. **修复回归验证 — audit-fixes 24 项**

  **What to do**:
  - 验证 WorkflowEngine 拆分为 6 服务的完整性（无残留空壳）
  - 验证 27 Service 统一使用 DateTimeUtils（无自行定义 DTF）
  - 验证 CostSubjectResolver 消除 4 个 Strategy 中的重复代码
  - 验证 Dashboard N+1 已优化为批量查询
  - 验证 Mass Assignment 防护（DTO + @JsonIgnore）有效
  - 验证输入校验（@Valid + Jakarta Validation）已添加
  - 搜索 audit-fixes 后是否引入新 `@SuppressWarnings`、空 catch、hardcoded 值
  - **产量**：≤15 回归发现

  **Must NOT do**: 不重新审查已解决的 24 项问题

  **Recommended Agent Profile**: `deep` | **Skills**: [`git-master`]
  **Parallelization**: Wave 1 | Blocks: T7 | Blocked By: none

  **QA Scenarios**:
  ```
  Scenario: DateTimeUtils 引用一致性
    Tool: Bash (grep)
    Steps:
      1. grep -rn "DateTimeFormatter.ofPattern" backend/src/main/java/ | grep -v "DateTimeUtils"
    Expected Result: 0 匹配（无 Service 自行定义 DTF）
    Evidence: .sisyphus/evidence/task-6-dtf-regression.txt
  ```

  **Commit**: YES — `docs(review): add fix regression verification report`

### Wave 2 — 综合分析

- [x] 7. **综合分析 — 去重、分级、依赖映射**

  **What to do**:
  - 收集 6 份审查报告（review-1 至 review-6）
  - 跨维度去重：相同 file:line 的发现合并（标注原始维度来源）
  - 按标准分级：P0（阻断上线）→ P1（高优）→ P2（可延后）→ P3（优化项）
  - 构建修复依赖图：标注哪些修复相互阻塞
  - 按依赖分波：Wave 3-A（无依赖 P0）→ 3-B（依赖 A 的 P0）→ 3-C（无依赖 P1）
  - 输出综合报告：`.sisyphus/evidence/synthesis-report.md`
  - **追加修复任务到本计划**（Task 8-N），基于综合报告的 P0/P1 发现

  **Must NOT do**: 不修改原始审查报告内容；不自行决定 P 级别（严格遵循标准）

  **Recommended Agent Profile**: `deep`
  **Parallelization**: Wave 2 | Blocks: Tasks 8-N | Blocked By: T1-T6

  **QA Scenarios**:
  ```
  Scenario: 综合报告去重有效
    Tool: Bash
    Steps:
      1. 检查 synthesis-report.md 中同一 file:line 无重复出现
    Expected Result: 跨维度重复发现合并为一条
    Evidence: .sisyphus/evidence/synthesis-dedup-check.txt
  ```

  **Commit**: YES — `docs(review): add synthesis report and fix plan`

### Wave 3 — TDD 修复（综合报告完成后追加）

> **综合报告**: `.sisyphus/evidence/synthesis-report.md`（2026-06-15）
> **P0 发现**: 3（2 Frontend, 1 Backend）
> **P1 发现**: 14（5 Backend, 4 Frontend, 3 Infra/Deploy, 2 Business）
> **修复任务**: T8–T21（14 个任务，按依赖关系分 4 波次）

#### Wave 3-A: P0 修复（3 个任务，全部可并行）

- [x] 8. **修复结算 TOCTOU 竞态条件（P0）**

  **Synthesis IDs**: S-P0-03 (D5-001), S-P1-12 (D5-002)
  **What to do**:
  - 创建 Flyway V52 迁移: `ALTER TABLE stl_settlement ADD CONSTRAINT uk_stl_tenant_contract UNIQUE (tenant_id, contract_id)`
  - 在 `StlSettlementService.create()` 中包装 insert 为 try-catch `DuplicateKeyException` → 抛出 `BusinessException("STL_DUPLICATE_SETTLEMENT")`
  - 在编码生成逻辑中添加重试循环：catch `DuplicateKeyException` on `uk_stl_settlement_code`，重新计算序列号，最多重试 3 次
  - 编写集成测试：2 个并发 POST 对同一个 contractId → 一个成功（201），一个失败（409 BusinessException）
  - 验证：`./mvnw test -Dtest=StlSettlementServiceTest` 通过

  **Must NOT do**:
  - 不修改 V1–V51 中任何已有迁移脚本
  - 不删除已有的 `uk_stl_settlement_code` 约束
  - 不在无 Flyway 迁移的情况下修改服务代码（DB 约束必须先存在）

  **Recommended Agent Profile**: `deep`
  **Parallelization**: Wave 3-A | Blocks: T19 | Blocked By: none
  **References**:
  - `backend/src/main/java/com/cgcpms/settlement/service/StlSettlementService.java:162-188`
  - `backend/src/main/resources/db/migration/V12__init_phase2_tables.sql` (uk_stl_settlement_code)
  - `backend/src/main/resources/db/migration-h2/V12__init_phase2_tables.sql` (H2 同步)

  **QA Scenarios**:
  ```
  Scenario: 同一 contractId 的并发结算只创建一个
    Tool: Bash (JUnit + curl)
    Steps:
      1. 编写 integration test: 2 threads 同时 POST /settlements，contractId 相同
      2. 验证：一个返回 201，一个返回 BusinessException
    Expected Result: 无重复结算记录
    Evidence: .sisyphus/evidence/task-8-settlement-race.txt

  Scenario: 编码生成在并发场景下无 500 错误
    Tool: Bash (JUnit)
    Steps:
      1. 编写 test: 高并发创建结算（10 线程同时）
      2. 验证：所有创建成功或返回 BusinessException，无 500
    Expected Result: 零 500 错误
    Evidence: .sisyphus/evidence/task-8-code-gen.txt
  ```

  **Commit**: YES — `fix(settlement): add DB unique constraint and DuplicateKeyException handling for contractId TOCTOU race`

- [x] 9. **修复 system/data 页面 API 模块绕过（P0）**

  **Synthesis ID**: S-P0-01 (D3-001)
  **What to do**:
  - 在 `api/modules/system.ts` 中创建 `clearDatabase()` 方法（如果文件不存在则创建）
  - 方法签名: `export function clearDatabase() { return request<void>({ url: '/system/clear-database', method: 'delete' }) }`
  - 在 `pages/system/data/index.vue` 中移除 `import service from '@/api/request'`
  - 替换为 `import { clearDatabase } from '@/api/modules/system'`
  - 修改 `handleClearDatabase()`: 移除 `: any`，直接使用返回值（拦截器已解包 data）
  - 验证: `pnpm build` 零 TypeScript 错误

  **Must NOT do**:
  - 不修改 `api/request.ts` 拦截器逻辑
  - 不修改 `/system/clear-database` 后端端点
  - 不添加新的 API 方法到 `system.ts`（仅 clearDatabase）

  **Recommended Agent Profile**: `visual-engineering`
  **Parallelization**: Wave 3-A | Blocks: T21 | Blocked By: none
  **References**:
  - `frontend-admin/src/pages/system/data/index.vue:4,19`
  - `frontend-admin/src/api/modules/system.ts`（参考其他 API 模块的模式）
  - `frontend-admin/src/api/request.ts`

  **QA Scenarios**:
  ```
  Scenario: 清除数据库功能使用 API 模块
    Tool: Bash (grep)
    Steps:
      1. grep "from '@/api/request'" pages/system/data/index.vue → 0 匹配
      2. grep "clearDatabase" pages/system/data/index.vue → 1 导入匹配
    Expected Result: 无直接 request/service 导入
    Evidence: .sisyphus/evidence/task-9-api-module.txt

  Scenario: TypeScript 构建零错误
    Tool: Bash
    Steps:
      1. cd frontend-admin && pnpm build
    Expected Result: 构建成功，零 TS 错误
    Evidence: .sisyphus/evidence/task-9-build.txt
  ```

  **Commit**: YES — `fix(frontend): replace raw axios import with API module in system/data page`

- [x] 10. **修复 profile 页面 API 模块绕过（P0）**

  **Synthesis ID**: S-P0-02 (D3-002)
  **What to do**:
  - 在 `api/modules/user.ts` 中创建两个方法（如果文件不存在则创建）:
    - `updateProfile(data: Partial<UserInfo>)` → `PUT /profile`
    - `changePassword(data: { oldPassword: string; newPassword: string })` → `PUT /profile/password`
  - 在 `pages/profile/index.vue` 中移除 `import { request } from '@/api/request'`
  - 替换为 `import { updateProfile, changePassword } from '@/api/modules/user'`
  - 修改 `handleProfileSave()` 和 `handlePasswordChange()` 使用新的 API 模块方法
  - 验证: `pnpm build` 零 TypeScript 错误

  **Must NOT do**:
  - 不修改 profile 页面的 UI 或业务逻辑
  - 不修改后端 `/profile` 或 `/profile/password` 端点

  **Recommended Agent Profile**: `visual-engineering`
  **Parallelization**: Wave 3-A | Blocks: T21 | Blocked By: none
  **References**:
  - `frontend-admin/src/pages/profile/index.vue:5,33,67`
  - `frontend-admin/src/api/modules/user.ts`（参考其他 API 模块的模式）

  **QA Scenarios**:
  ```
  Scenario: Profile 页面使用 API 模块
    Tool: Bash (grep)
    Steps:
      1. grep "from '@/api/request'" pages/profile/index.vue → 0 匹配
      2. grep "updateProfile\|changePassword" pages/profile/index.vue → API 模块导入
    Expected Result: 无直接 request 导入
    Evidence: .sisyphus/evidence/task-10-api-module.txt

  Scenario: 前端构建通过
    Tool: Bash
    Steps:
      1. cd frontend-admin && pnpm build
    Expected Result: 构建成功
    Evidence: .sisyphus/evidence/task-10-build.txt
  ```

  **Commit**: YES — `fix(frontend): replace direct request import with API module in profile page`

#### Wave 3-B: P1 独立修复（6 个任务，全部可并行）

- [x] 11. **修复 deploy/.env 安全问题（P1）**

  **Synthesis IDs**: S-P1-01 (D1-001), S-P1-11 (D4-015)
  **What to do**:
  - 在 `deploy/.env.example` 中：将所有 `${VAR_NAME}` 自引用占位符替换为明确的"请修改"占位符:
    - `MYSQL_ROOT_PASSWORD=CHANGE-ME-ROOT-PASSWORD`
    - `MYSQL_PASSWORD=CHANGE-ME-CGC-PASSWORD`
    - `REDIS_PASSWORD=CHANGE-ME-REDIS-PASSWORD`
    - `JWT_SECRET=CHANGE-ME-JWT-SECRET-MIN-32-CHARS`
    - `MINIO_ROOT_PASSWORD=CHANGE-ME-MINIO-PASSWORD`
  - 同样替换 `MINIO_ROOT_USER` 为 `CHANGE-ME-MINIO-USER`（覆盖 D1-015 P3）
  - 在 `.gitignore` 中验证 `deploy/.env` 已存在且生效
  - 在 `deploy/.env.example` 顶部添加注释块，说明如何使用（"复制为 .env 并填入真实值"）
  - 验证: `docker compose config` 不再输出空凭据（如果 .env 存在）

  **Must NOT do**:
  - 不提交真实的 `deploy/.env`（确认 gitignore 生效）
  - 不修改 docker-compose 文件的环境变量结构

  **Recommended Agent Profile**: `quick`
  **Parallelization**: Wave 3-B | Blocks: none | Blocked By: none
  **References**:
  - `deploy/.env.example:3,6,8,12,16,17`
  - `deploy/.gitignore`

  **QA Scenarios**:
  ```
  Scenario: .env.example 无自引用占位符
    Tool: Bash
    Steps:
      1. grep '\${[A-Z_]*}' deploy/.env.example → 0 匹配
    Expected Result: 所有变量有明确的占位符值
    Evidence: .sisyphus/evidence/task-11-env-template.txt

  Scenario: deploy/.env 被 gitignore 忽略
    Tool: Bash
    Steps:
      1. git check-ignore deploy/.env → 确认忽略
    Expected Result: deploy/.env 被排除在 git 跟踪之外
    Evidence: .sisyphus/evidence/task-11-gitignore.txt
  ```

  **Commit**: YES — `fix(security): replace self-referencing placeholders in deploy/.env.example with explicit defaults`

- [x] 12. **修复 backend/Dockerfile 安全加固（P1）**

  **Synthesis IDs**: S-P1-02 (D1-002 + D1-010), S-P1-10 (D4-007 + D1-009)
  **What to do**:
  - 在 `backend/Dockerfile` 中：将空 ENV 默认值替换为哨兵值:
    - `ENV JWT_SECRET=__MUST_OVERRIDE_IN_PRODUCTION__`
    - `ENV DB_PASSWORD=__MUST_OVERRIDE_IN_PRODUCTION__`
    - `ENV SPRING_DATA_REDIS_PASSWORD=__MUST_OVERRIDE_IN_PRODUCTION__`
    - `ENV MINIO_SECRET_KEY=__MUST_OVERRIDE_IN_PRODUCTION__`
  - 移除 Dockerfile 中的硬编码 `SPRING_DATASOURCE_URL`（使用 compose 注入）
  - 将所有 Docker 配置中的 `useSSL=false` 替换为 `useSSL=${DB_USE_SSL:-true}`:
    - `deploy/docker-compose.prod.yml:133`
    - `deploy/docker-compose.dev.yml:117`
  - 在后端添加 `@PostConstruct` 启动验证：如果任何 secret 仍为哨兵值则启动失败
  - 验证: `docker compose config` 显示正确的 URL 模板

  **Must NOT do**:
  - 不删除 Dockerfile 中的 ENV 声明（compose 需要它们作为可覆盖的默认值）
  - 不修改 `application-prod.yml` 中的 `useSSL=true` 默认值

  **Recommended Agent Profile**: `quick`
  **Parallelization**: Wave 3-B | Blocks: none | Blocked By: none
  **References**:
  - `backend/Dockerfile:54-75`
  - `deploy/docker-compose.prod.yml:132-133`
  - `deploy/docker-compose.dev.yml:117`
  - `backend/src/main/resources/application-prod.yml` (useSSL=true)

  **QA Scenarios**:
  ```
  Scenario: Dockerfile 无空凭据
    Tool: Bash
    Steps:
      1. grep 'ENV.*=""' backend/Dockerfile → 0 匹配（排除空默认值）
      2. grep '__MUST_OVERRIDE' backend/Dockerfile → ≥4 匹配
    Expected Result: 所有 secret ENV 使用哨兵值
    Evidence: .sisyphus/evidence/task-12-dockerfile.txt

  Scenario: useSSL 可通过环境变量配置
    Tool: Bash
    Steps:
      1. grep 'useSSL=' deploy/docker-compose.prod.yml → 包含 ${DB_USE_SSL:-true}
      2. grep 'useSSL=false' deploy/docker-compose.prod.yml → 0 匹配（无硬编码 false）
    Expected Result: useSSL 参数化
    Evidence: .sisyphus/evidence/task-12-useSSL.txt
  ```

  **Commit**: YES — `fix(security): harden backend Dockerfile with sentinel defaults and parameterized useSSL`

- [x] 13. **修复 nginx 安全头配置（P1）**

  **Synthesis ID**: S-P1-03 (D1-003)
  **What to do**:
  - 在 `frontend-admin/nginx.conf` 的 HTTPS server block 中：
    - 取消注释第 107 行的 `add_header Strict-Transport-Security "max-age=31536000" always;`
    - 添加 `max-age=31536000; includeSubDomains; preload` 以获得更全面的 HSTS
  - 将 HTTP server block 替换为 HTTPS 永久重定向：
    - 移除所有现有 HTTP block 内容（location blocks, root, index）
    - 替换为单个: `return 301 https://$host$request_uri;`
  - 在 HTTPS block 中添加 Content-Security-Policy header:
    - `add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'" always;`
  - 验证: `docker compose -f docker-compose.prod.yml config` 显示正确的重定向

  **Must NOT do**:
  - 不移除现有的安全头（X-Frame-Options, X-Content-Type-Options 等）
  - 不修改 SSE proxy_buffering 配置（已确认正确）
  - 不修改 upstream 或 location 代理规则

  **Recommended Agent Profile**: `quick`
  **Parallelization**: Wave 3-B | Blocks: none | Blocked By: none
  **References**:
  - `frontend-admin/nginx.conf:36-80` (HTTP block), `:85-164` (HTTPS block), `:107` (注释掉的 HSTS)

  **QA Scenarios**:
  ```
  Scenario: HTTP 端点重定向到 HTTPS
    Tool: Bash (curl)
    Steps:
      1. curl -I http://localhost/ → 301 重定向 + Location: https://...
    Expected Result: 301 状态码
    Evidence: .sisyphus/evidence/task-13-redirect.txt

  Scenario: HTTPS 端点包含安全头
    Tool: Bash (curl)
    Steps:
      1. curl -I https://localhost/ 2>/dev/null（或检查 nginx 配置）
      2. 验证 Strict-Transport-Security 和 Content-Security-Policy header
    Expected Result: HSTS + CSP header 存在
    Evidence: .sisyphus/evidence/task-13-headers.txt
  ```

  **Commit**: YES — `fix(security): enable HSTS, HTTP-to-HTTPS redirect, and Content-Security-Policy in nginx`

- [x] 14. **为登录/刷新端点添加速率限制（P1）**

  **Synthesis ID**: S-P1-04 (D1-004)
  **What to do**:
  - 为 `/auth/login` 和 `/auth/refresh` 添加基于 Redis 的速率限制
  - 策略：每个 IP + 用户名组合每分钟最多 5 次尝试
  - 使用现有 Redis 基础设施（`RedisTemplate`）实现计数器
  - 创建 `RateLimitFilter` 或自定义注解 `@RateLimit(maxAttempts=5, windowSeconds=60)` 应用于 AuthController 方法
  - 超过限制时返回 429（Too Many Requests）
  - 编写测试：5 次失败登录 → 第 6 次返回 429
  - 考虑：N 次失败后账户锁定（Redis TTL 15 分钟）—— 可选增强

  **Must NOT do**:
  - 不引入重量级库（如 Bucket4j、Resilience4j）—— 使用现有 Redis
  - 不修改 JWT token 生成或验证逻辑

  **Recommended Agent Profile**: `deep`
  **Parallelization**: Wave 3-B | Blocks: none | Blocked By: none
  **References**:
  - `backend/src/main/java/com/cgcpms/auth/controller/AuthController.java:42,79`
  - `backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java`
  - 用于速率限制的现有 Redis 基础设施

  **QA Scenarios**:
  ```
  Scenario: 速率限制在 N 次尝试后触发
    Tool: Bash (curl)
    Steps:
      1. 快速发送 6 次 POST /auth/login，使用错误的密码
      2. 第 6 次: 429 状态码
    Expected Result: 前 5 次返回 401，第 6 次返回 429
    Evidence: .sisyphus/evidence/task-14-rate-limit.txt

  Scenario: 速率限制窗口过期后重置
    Tool: Bash (JUnit)
    Steps:
      1. 触发速率限制 → 等待 61 秒 → 重试 → 应允许
    Expected Result: 窗口过期后计数器重置
    Evidence: .sisyphus/evidence/task-14-rate-window.txt
  ```

  **Commit**: YES — `fix(security): add Redis-based rate limiting to auth login and refresh endpoints`

- [x] 15. **修复 InvoiceService 空 catch 块（P1）**

  **Synthesis ID**: S-P1-06 (D2-002)
  **What to do**:
  - 在 `InvoiceService.java:210` 中：将 `catch (Exception ignored) { // ignore close errors }` 替换为:
    ```java
    catch (Exception e) {
        log.debug("Failed to close PDF document", e);
    }
    ```
  - 验证：`./mvnw test` 全部通过（0 回归）
  - 同时检查代码库中是否还有其他无日志的 catch 块（D6 已确认 0 个，但请验证）

  **Must NOT do**:
  - 不改变 PDF 生成逻辑或资源管理流程
  - 不将日志级别提升至 warn/error（close 失败不算错误）

  **Recommended Agent Profile**: `quick`
  **Parallelization**: Wave 3-B | Blocks: none | Blocked By: none
  **References**:
  - `backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java:210`

  **QA Scenarios**:
  ```
  Scenario: Catch 块包含日志
    Tool: Bash (grep)
    Steps:
      1. grep -A2 "catch.*ignored" InvoiceService.java → 0 匹配（变量重命名）
      2. grep -A2 "catch.*Exception.*close" InvoiceService.java → 包含 log.debug
    Expected Result: 无被忽略的异常，所有 catch 块均包含日志
    Evidence: .sisyphus/evidence/task-15-empty-catch.txt
  ```

  **Commit**: YES — `fix(backend): add logging to empty catch block in InvoiceService PDF close`

- [x] 16. **填充 CostItem taxAmount/amountWithoutTax 字段（P1）**

  **Synthesis ID**: S-P1-14 (D5-004)
  **What to do**:
  - 在 `MaterialReceiptCostStrategy.java`、`SubMeasureCostStrategy.java`、`VarOrderCostStrategy.java` 中：
    - 如果源实体包含税务数据：填充 `cost.setTaxAmount(...)` 和 `cost.setAmountWithoutTax(...)`
    - 如果源实体不包含税务数据：显式设置 `cost.setTaxAmount(BigDecimal.ZERO)` 和 `cost.setAmountWithoutTax(cost.getAmount())`（默认无税）
  - 如果不确定税务数据可用性：将 `amountWithoutTax` 默认设置为 `amount`（即，假设全款未税），将 `taxAmount` 设置为 0，并在代码注释中说明
  - 编写测试：验证所有 4 个策略生成的 CostItem 的 taxAmount 和 amountWithoutTax 均不为 null
  - 验证：`./mvnw test -Dtest=CostStrategyTest` 通过

  **Must NOT do**:
  - 不修改 `ContractCostStrategy`（已正确填充税务字段）
  - 不向没有税务数据的源实体添加税务字段
  - 不改变成本科目解析逻辑

  **Recommended Agent Profile**: `deep`
  **Parallelization**: Wave 3-B | Blocks: none | Blocked By: none
  **References**:
  - `backend/src/main/java/com/cgcpms/cost/strategy/MaterialReceiptCostStrategy.java:78`
  - `backend/src/main/java/com/cgcpms/cost/strategy/SubMeasureCostStrategy.java`
  - `backend/src/main/java/com/cgcpms/cost/strategy/VarOrderCostStrategy.java`
  - `backend/src/main/java/com/cgcpms/cost/strategy/ContractCostStrategy.java`（参考实现）
  - `backend/src/main/java/com/cgcpms/cost/entity/CostItem.java`（taxAmount、amountWithoutTax 字段）

  **QA Scenarios**:
  ```
  Scenario: 所有策略均填充税务字段
    Tool: Bash (JUnit)
    Steps:
      1. 测试: 对每种策略，创建 CostItem 并断言 taxAmount != null 且 amountWithoutTax != null
    Expected Result: 所有策略生成的税务字段均非空
    Evidence: .sisyphus/evidence/task-16-tax-fields.txt

  Scenario: 回归测试通过
    Tool: Bash
    Steps:
      1. ./mvnw test → 全部通过
    Expected Result: 零回归
    Evidence: .sisyphus/evidence/task-16-test-pass.txt
  ```

  **Commit**: YES — `fix(cost): populate taxAmount and amountWithoutTax in MaterialReceipt, SubMeasure, and VarOrder cost strategies`

#### Wave 3-C: P1 顺序修复（3 个任务，部分并行）

- [x] 17. **修复 PayApplicationService N+1 查询（P1）**

  **Synthesis ID**: S-P1-05 (D2-001 + D2-004)
  **What to do**:
  - 重构 `PayApplicationService.getById()` 使用批量预取模式（类似现已生效的 `getPage()` 方法）:
    - 收集 projectId、contractId、partnerId
    - 使用 `selectBatchIds()` 批量查询
    - 构建查找 Map
    - 调用 batch toVO 重载方法
  - 将单参数 `toVO(PayApplication)` 方法标记为 `@Deprecated` 或设置为 private
  - 编写测试：`GET /pay-applications/{id}` 仅执行 1 次 DB 查询（非 4 次）
  - 验证：`./mvnw test` 通过

  **Must NOT do**:
  - 不删除单参数 toVO 重载（用于向后兼容，仅标记为 deprecated）
  - 不改变 getPage()（已针对 N+1 优化）

  **Recommended Agent Profile**: `deep`
  **Parallelization**: Wave 3-C | Blocks: T18 | Blocked By: none
  **References**:
  - `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java:139,471-483`
  - `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java:97`（getPage 批量模式 — 参考）

  **QA Scenarios**:
  ```
  Scenario: getById 无 N+1 查询
    Tool: Bash (JUnit + SQL 日志)
    Steps:
      1. 开启 SQL 日志 → GET /pay-applications/{id}
      2. 统计 SELECT 语句数量 → 应 ≤2（1 次查询 application + 1 次批量查询关联表）
    Expected Result: ≤2 次 DB 查询（非 4 次）
    Evidence: .sisyphus/evidence/task-17-nplus1.txt
  ```

  **Commit**: YES — `fix(payment): fix N+1 queries in PayApplicationService.getById by using batch pre-fetch`

- [x] 18. **为所有只读服务方法添加 @Transactional(readOnly=true)（P1）**

  **Synthesis ID**: S-P1-07 (D2-003)
  **What to do**:
  - 审计所有 42+ 服务文件：分类方法为只读（getPage、getById、list、query、search）或写入（create、update、delete、submit、approve）
  - 在类级别已有 `@Transactional` 的服务中：在只读方法上添加 `@Transactional(readOnly = true)`
  - 在无类级别 `@Transactional` 但包含只读方法的服务中：添加方法级 `@Transactional(readOnly = true)`
  - 不要覆盖写入方法上的 `@Transactional(rollbackFor = Exception.class)`（策略类已正确设置）
  - 验证：`./mvnw test` 全部通过（≥174 用例）

  **Must NOT do**:
  - 不将 `readOnly=true` 应用于写入方法
  - 不移除方法上已有的 `@Transactional` 注解
  - 不在本任务中重构任何业务逻辑

  **Recommended Agent Profile**: `deep`
  **Parallelization**: Wave 3-C | Blocks: none | Blocked By: T17（同一文件，避免冲突）
  **References**:
  - 所有 42+ 服务文件（`**/service/*Service.java`）
  - `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java`（T17 中已修改 — 在此之后修改）

  **QA Scenarios**:
  ```
  Scenario: 所有服务中的只读方法均有 readOnly=true
    Tool: Bash (grep)
    Steps:
      1. 对每个服务文件，手动检查只读方法是否有 @Transactional(readOnly=true)
      2. 抽查具体服务：DashboardService、CostSummaryService、WorkflowQueryService
    Expected Result: 主要查询方法有 readOnly=true
    Evidence: .sisyphus/evidence/task-18-readonly.txt

  Scenario: 回归测试通过
    Tool: Bash
    Steps:
      1. ./mvnw test → 全部通过
    Expected Result: ≥174 用例通过
    Evidence: .sisyphus/evidence/task-18-test-pass.txt
  ```

  **Commit**: YES — `perf(backend): add @Transactional(readOnly=true) to all read-only service methods`

- [x] 19. **修复结算完成时的乐观锁缺失问题（P1）**

  **Synthesis ID**: S-P1-13 (D5-003)
  **What to do**:
  - 在 `SettlementWorkflowHandler.onApproved()` 中（第 106-110 行）:
    - 在 `LambdaUpdateWrapper` 上添加 `.eq(StlSettlement::getSettlementStatus, "DRAFT")` 守卫
    - 检查受影响行数：如果 `updated == 0`，记录警告并返回（最终状态可能已被另一个并发审批设置）
  - 或者：向 `StlSettlement` 实体添加 `@Version` 字段，并使用 `updateById()` 携带版本号
  - 编写测试：验证并发审批不会导致状态覆盖
  - 验证：`./mvnw test -Dtest=SettlementWorkflowHandlerTest` 通过

  **Must NOT do**:
  - 不修改工作流引擎的核心审批逻辑（WorkflowCoreService、WorkflowApprovalService）
  - 不改变 `isCritical()` 行为（当前所有 handler 返回 true）

  **Recommended Agent Profile**: `deep`
  **Parallelization**: Wave 3-C | Blocks: none | Blocked By: T8（同一结算模块 — 在 T8 修复之后）
  **References**:
  - `backend/src/main/java/com/cgcpms/settlement/handler/SettlementWorkflowHandler.java:106-118`
  - `backend/src/main/java/com/cgcpms/settlement/entity/StlSettlement.java`（当前无 @Version）
  - `backend/src/main/java/com/cgcpms/workflow/entity/WfTask.java:40-41`（@Version 参考实现）

  **QA Scenarios**:
  ```
  Scenario: 并发审批不覆盖状态
    Tool: Bash (JUnit)
    Steps:
      1. 测试: 2 个线程同时调用 onApproved()
      2. 验证: 只执行一次完成操作
    Expected Result: 第二次 update 返回 0 行，记录警告日志
    Evidence: .sisyphus/evidence/task-19-lock.txt
  ```

  **Commit**: YES — `fix(settlement): add optimistic lock guard to SettlementWorkflowHandler.onApproved() finalization`

#### Wave 3-D: P1 前端顺序修复（2 个任务，顺序执行）

- [x] 20. **替换 catch (e: any) 为 catch (e: unknown)（P1）**

  **Synthesis ID**: S-P1-09 (D3-004 + D3-005)
  **What to do**:
  - 在 `pages/invoice/index.vue:265` 中：将 `catch (error: any)` 替换为 `catch (e: unknown)`，使用安全属性访问或 `instanceof` 检查
  - 在 `pages/invoice/index.vue:322` 中：将 `catch (error: any)` 替换为 `catch (e: unknown)`，使用 `axios.isCancel(e)` 守卫
  - 在 `pages/system/users/index.vue:131` 中：将 `catch (err: any)` 替换为 `catch (e: unknown)`，使用安全属性访问
  - 验证：`pnpm lint` — `@typescript-eslint/no-explicit-any` 错误减少 ≥3 条
  - 验证：`pnpm build` 零 TS 错误

  **Must NOT do**:
  - 不删除 catch 块本身（仅替换类型注解）
  - 不改变 catch 块内的任何业务/UI 逻辑

  **Recommended Agent Profile**: `visual-engineering`
  **Parallelization**: Wave 3-D | Blocks: none | Blocked By: T9, T10（P0 前端修复；避免冲突）
  **References**:
  - `frontend-admin/src/pages/invoice/index.vue:265,322`
  - `frontend-admin/src/pages/system/users/index.vue:131`

  **QA Scenarios**:
  ```
  Scenario: 无 catch (e: any) 残留
    Tool: Bash (grep)
    Steps:
      1. grep -rn "catch.*:\s*any" pages/ → 0 匹配
    Expected Result: 所有 catch any 均已替换为 unknown
    Evidence: .sisyphus/evidence/task-20-catch-any.txt

  Scenario: 前端构建通过
    Tool: Bash
    Steps:
      1. cd frontend-admin && pnpm build && pnpm lint
    Expected Result: 构建成功，ESLint any 错误减少
    Evidence: .sisyphus/evidence/task-20-build.txt
  ```

  **Commit**: YES — `fix(frontend): replace catch (e: any) with catch (e: unknown) in invoice and users pages`

- [ ] 21. **修复 122 个无参数 catch 块（P1）**

  **Synthesis ID**: S-P1-08 (D3-003)
  **What to do**:
  - 在所有 37 个文件、共 122 个 `} catch {` 实例中替换为 `} catch (e: unknown) {`
  - 添加最小日志：`if (import.meta.env.DEV) console.error('ContextName:', e)`
  - 对于面向用户的错误：回退到 `message.error('操作失败')` 或等效提示
  - 优先处理最严重的文件（9 个 error context 丢失的 org/index.vue，8 个 payment/index.vue，8 个 stores/contract.ts，等等）
  - 验证：`pnpm build` 零 TS 错误
  - 验证：`pnpm lint` — 与基线相比无新增错误

  **Must NOT do**:
  - 不删除 catch 块或改变恢复逻辑（仅添加 error 参数 + 最小日志）
  - 不在本任务中触及 catch (e: any) 实例（T20 负责处理）
  - 不在无用户影响的情况下添加 `message.error()`（避免向用户发送垃圾错误信息）

  **Recommended Agent Profile**: `visual-engineering`
  **Parallelization**: Wave 3-D | Blocks: none | Blocked By: T9, T10, T20（最后执行的前端修复 — 影响范围最大）
  **References**:
  - 37 个文件，共 122 个实例（完整列表见 `review-3-frontend-api.md` 第 74-87 行）
  - 最严重文件：`pages/org/index.vue` (9), `pages/payment/index.vue` (8), `stores/contract.ts` (8), `pages/invoice/index.vue` (6), `pages/system/dict/index.vue` (6)

  **QA Scenarios**:
  ```
  Scenario: 无无参数 catch 块残留
    Tool: Bash (grep)
    Steps:
      1. grep -rn "}\s*catch\s*{" src/ → 0 匹配
    Expected Result: 所有 catch 块均有 error parameter
    Evidence: .sisyphus/evidence/task-21-no-empty-catch.txt

  Scenario: 前端构建通过，无新增 lint 错误
    Tool: Bash
    Steps:
      1. cd frontend-admin && pnpm build && pnpm lint
    Expected Result: 构建成功，lint 错误 ≤ T20 后的基线
    Evidence: .sisyphus/evidence/task-21-build.txt
  ```

  **Commit**: YES — `fix(frontend): add error parameter to 122 empty catch blocks across 37 files`

---

## Final Verification Wave

- [ ] F1. **后端回归测试** — `oracle`
  运行 `cd backend && ./mvnw test`，确认 ≥174 用例全通过。检查新增测试结构合规性。
  输出: `Tests [N/N] | New Tests [N] | Code Smells [N] | VERDICT`

- [ ] F2. **前端构建 + ESLint** — `unspecified-high`
  运行 `pnpm build` + `pnpm lint`，确认零 TypeScript 错误 + ESLint 0 error。
  输出: `Build [PASS/FAIL] | Lint [N errors] | VERDICT`

- [ ] F3. **E2E 冒烟测试** — `unspecified-high` (+ `playwright`)
  执行全部 10 个 Playwright spec，覆盖核心路径。
  输出: `Specs [N/N] | Tests [N/N] | Screenshots [N] | VERDICT`

- [ ] F4. **审查维度复扫** — `deep`
  对 top-3 发现执行快速复扫，确认已修复项不再出现。
  输出: `Dim1-6 residuals | VERDICT`

---

## Commit Strategy

- **T0**: `fix(security): replace hardcoded secrets in deploy/.env with placeholders` → `deploy/.env.example`, `.gitignore`
- **Wave 1**: `docs(review): add dimension review reports` → `.sisyphus/evidence/review-*.md`
- **Wave 2**: `docs(review): add synthesis and fix plan` → `.sisyphus/evidence/synthesis-report.md`
- **Wave 3**: `fix(N): description` → 每个修复独立提交
- **Wave 4**: 验证证据（可选提交）

---

## Success Criteria

### Verification Commands
```bash
cd backend && ./mvnw test
# Expected: Tests run: 174+, Failures: 0

cd frontend-admin && pnpm build
# Expected: exit 0, zero TypeScript errors

cd frontend-admin && pnpm lint
# Expected: 0 errors

# 安全验证
grep -c 'PASSWORD.*=.*[a-z0-9]\{8,\}' deploy/.env
# Expected: 0 (无硬编码密码)
```

### Final Checklist
- [ ] All P0 issues fixed
- [ ] P1 issues ≤ 3 (all with documented acceptance justification)
- [ ] Backend tests 100% pass (≥174 cases)
- [ ] Frontend build zero TypeScript errors
- [ ] Frontend ESLint 0 errors
- [ ] E2E 10 specs all pass
- [ ] deploy/.env zero hardcoded secrets
