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

- [ ] 7. **综合分析 — 去重、分级、依赖映射**

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

> **Task 8-N 将在 Task 7 综合分析完成后按 P0/P1 优先顺序追加。**
> 此波次结构待定，取决于审查发现的具体问题。

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
