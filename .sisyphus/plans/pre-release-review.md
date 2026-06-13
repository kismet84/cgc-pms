# CGC-PMS 测试版上线前全量审查与修复计划

## TL;DR

> **Quick Summary**: 对 CGC-PMS 项目执行 6 维度并行全量审查（安全/代码架构/前端API/数据基础设施/业务逻辑/修复回归验证），产出结构化问题清单，然后以 TDD 模式分波修复所有 P0/P1 问题，最终通过 4 项并行验证门禁确认测试版上线就绪。
>
> **Deliverables**:
> - 6 份维度审查报告（`.sisyphus/evidence/review-{dim}.md`）
> - 1 份综合问题清单（优先级排序 + 依赖映射）
> - N 个 TDD 修复任务（RED→GREEN→REFACTOR）
> - 1 份最终上线门禁验证报告
>
> **Estimated Effort**: Large（审查 1-2 天 + 修复 3-5 天 + 验证 0.5 天）
> **Parallel Execution**: YES — 6 维度审查并行 + 6 修复并行
> **Critical Path**: 预检 → 6 路审查 → 综合 → 修复波次 → 最终验证

---

## Context

### Original Request
用户要求对 CGC-PMS 项目执行"测试版上线前审查"——全新一轮全量审查（不受已有审查结果影响），审查后制定 TDD 修复计划。

### Interview Summary

**Key Discussions**:
- **审查目标**: 全新一轮全量审查，不依赖 6/11-6/13 已有审查文档
- **审查范围**: 全项目无死角（核心代码 + 数据库迁移 + 配置文件 + 部署架构 + 文档 + 移动端占位 + 辅助脚本）
- **后续行动**: 审查发现问题 → 制定 TDD 修复计划（全链路可执行）
- **测试策略**: TDD 模式 — 每个修复先写失败测试 → 修复 → 测试通过
- **交付方式**: `.sisyphus/plans/` 可执行工作包，通过 `/start-work` 触发

**Research Findings**:
- **项目规模**: 后端 ~300+ Java 文件（18 模块），前端 ~100+ TS/Vue 文件，46 Flyway 迁移
- **测试现状**: 后端 JUnit 5 206 用例，前端 Vitest 14 测试文件，E2E Playwright 10 spec
- **近期变更**: `audit-fixes.md` 计划已完成 24/24 任务，298 文件变更（+6545/-38813 行），包括 WorkflowEngine 拆分、DateTimeUtils 提取、Dashboard N+1 优化等——这些修复尚未经过独立验证
- **已知问题**: 探索 agent 发现 11 处 console.error()、空 catch 块、ESLint 34 错误、deploy/.env 硬编码密码、KPI API 桩代码等
- **移动端/脚本**: mobile/ 仅 README.md（占位），scripts/ 仅 start-dev.bat

### Metis Review

**Identified Gaps** (addressed):
- **Gap 1: 8 维度有大量重叠** → 合并为 6 个更清晰的维度（安全配置合并、代码架构合并、前端API合并、数据基础设施合并），新增修复回归验证维度
- **Gap 2: 无预检机制** → 新增 Wave 0 预检任务，验证 mvnw test / pnpm build / Flyway 同步
- **Gap 3: 无问题数量上限** → 每维度 top-15 发现上限，防止审查无限膨胀
- **Gap 4: 无标准化输出格式** → 所有审查报告统一模板
- **Gap 5: 修复回归验证缺失** → 新增维度 6 专门验证 24 项已完成的 audit-fixes
- **Gap 6: 无显式 Beta 门禁** → 定义量化门禁：P0=0, P1≤3, 测试 100%, build 零错误

---

## Work Objectives

### Core Objective
执行 6 维度并行全量审查 → 产出一份优先级排序的问题清单 → 以 TDD 模式修复所有 P0/P1 问题 → 通过 4 项并行验证门禁 → 确认测试版上线就绪。

### Concrete Deliverables
- `.sisyphus/evidence/review-1-security-config.md` — 安全与配置审查报告
- `.sisyphus/evidence/review-2-code-architecture.md` — 后端代码与架构审查报告
- `.sisyphus/evidence/review-3-frontend-api.md` — 前端与 API 契约审查报告
- `.sisyphus/evidence/review-4-data-infra.md` — 数据与基础设施审查报告
- `.sisyphus/evidence/review-5-business-logic.md` — 业务逻辑正确性审查报告
- `.sisyphus/evidence/review-6-fix-regression.md` — 修复回归验证报告
- `.sisyphus/evidence/synthesis-report.md` — 综合问题清单（去重、分级、依赖映射）

### Definition of Done
- [ ] 6 份审查报告均生成，每份含 top-15 发现问题
- [ ] 综合报告完成去重和优先排序（P0/P1/P2/P3）
- [ ] 所有 P0 修复任务完成（RED→GREEN→REFACTOR）
- [ ] 所有 P1 修复任务完成（含回归测试）
- [ ] 后端 `./mvnw test` 100% 通过（≥174 用例基线）
- [ ] 前端 `pnpm build` 零 TypeScript 错误
- [ ] ESLint 0 错误（34→0）
- [ ] 4 项最终验证门禁全部 PASS

### Must Have
- [ ] 6 路并行审查覆盖全部项目文件
- [ ] 每个发现包含：P 级别、精确 file:line、复现步骤、建议修复
- [ ] 每个修复任务含：失败测试 → 修复代码 → 通过测试
- [ ] 修复回归验证：git diff 确认 audit-fixes 变更未被破坏

### Must NOT Have (Guardrails)

> 以下明确排除，防止审查范围膨胀。

- **禁止重新争论已定设计决策**: WorkflowEngine 拆分为 6 服务、DateTimeUtils 提取、CostSubjectResolver 提取均已完成，不质疑其设计合理性，仅检查是否引入缺陷
- **禁止添加新功能**: 发现描述中不得出现"应支持 X 功能"或"缺少 Y 特性"——这些归入独立 Backlog
- **禁止深入审查移动端/脚本**: mobile/ 仅 README.md，scripts/ 仅 start-dev.bat，确认存在即可，不生成"创建移动端应用"类任务
- **禁止替代业务方决策**: 成本公式、审批流程等业务逻辑仅检查结构性正确性（并发安全、事务边界等），不质疑业务合理性
- **每维度最多 15 条发现**: 超出部分在报告中列为简要列表，不在综合阶段占用优先级槽位
- **禁止 AI slop**: 不生成过度注释、不引入不必要抽象、不写"just in case"代码

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** — 所有验证均由 agent 执行。不接受任何需要人工干预的验收标准。

### Test Decision
- **Infrastructure exists**: YES（JUnit 5 + Vitest + Playwright）
- **Automated tests**: TDD（每个修复任务：RED → GREEN → REFACTOR）
- **Framework**: JUnit 5（后端）+ Vitest（前端）+ Playwright（E2E）

### QA Policy
每个修复任务必须包含 agent 执行的 QA 场景。证据保存到 `.sisyphus/evidence/`。

- **后端修复**: Bash（curl 请求 API，断言 HTTP 状态码 + JSON 字段）
- **前端修复**: Bash（pnpm build + pnpm lint 零错误；Playwright E2E 验证关键路径）
- **数据库修复**: Bash（Flyway migrate 验证 + SQL 完整性检查）

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 0（预检 — 必须先完成）:
├── Task 0: 预检（mvnw test + pnpm build + Flyway 同步 + deploy/.env 安全检查）

Wave 1（6 路并行审查 — 预检通过后同时启动）:
├── Task 1: 安全与配置审查
├── Task 2: 后端代码与架构审查
├── Task 3: 前端与 API 契约审查
├── Task 4: 数据与基础设施审查
├── Task 5: 业务逻辑正确性审查
└── Task 6: 修复回归验证（audit-fixes 24 项）

Wave 2（综合 — 审查全部完成后）:
└── Task 7: 综合分析与修复计划制定（去重 + 分级 + 依赖映射）

Wave 3（TDD 修复 — 依赖综合报告，优先 P0，其次 P1）:
├── Task 8-N: P0 修复任务（TDD，并行度取决于依赖关系）
└── Task N+1-M: P1 修复任务（TDD，并行度取决于依赖关系）

Wave 4（最终验证 — 所有修复完成后 4 路并行）:
├── Task F1: 后端回归测试验证
├── Task F2: 前端构建与 ESLint 验证
├── Task F3: E2E 冒烟测试验证
└── Task F4: 审查维度复扫验证

Critical Path: Task 0 → Tasks 1-6 → Task 7 → Tasks 8-N → Tasks F1-F4
Max Concurrent: 6 (Wave 1) + 6 (Wave 3, 视依赖)
```

### Agent Dispatch Summary

| Wave | Agent Count | Profiles |
|------|-------------|----------|
| 0 | 1 | `quick` |
| 1 | 6 | `explore` × 6（6 维度并行） |
| 2 | 1 | `deep` |
| 3 | N（0-12） | `quick` / `deep` / `unspecified-high`（视任务复杂度） |
| 4 | 4 | `oracle` + `unspecified-high` × 3 |

---

## TODOs

- [x] 0. **预检 — 环境健康检查**

  **What to do**:
  - 运行 `cd backend && ./mvnw test`（H2 local profile），记录实际通过用例数（基准 ≥174）
  - 运行 `cd frontend-admin && pnpm build`（vue-tsc + vite），确认零 TypeScript 错误
  - 运行 `cd frontend-admin && pnpm lint`，记录当前 ESLint errors/warnings 数量（当前已知：34 errors, 668 warnings）
  - 对比 Flyway MySQL（`db/migration/`）与 H2（`db/migration-h2/`）迁移文件数量一致性（应为 46/46）
  - 检查 `deploy/.env` 是否包含硬编码密码（已知问题：硬编码 root123/cgc123 等），如发现则标记为 **P0 阻断**
  - 检查 `deploy/.env` 是否在 `.gitignore` 中（当前未忽略——安全风险）
  - 输出检查结果到 `.sisyphus/evidence/task-0-preflight.txt`

  **Must NOT do**:
  - 不要修复任何问题——仅记录现状
  - 不要修改任何配置文件

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件脚本执行，无复杂逻辑
  - **Skills**: []
  - **Skills Evaluated but Omitted**: N/A

  **Parallelization**:
  - **Can Run In Parallel**: NO（必须先于所有审查）
  - **Parallel Group**: Wave 0（独立）
  - **Blocks**: Tasks 1-6（所有审查依赖预检结果）
  - **Blocked By**: None（可立即开始）

  **References**:
  - `README.md:206-208` — 测试命令参考（mvnw test + pnpm build）
  - `backend/src/main/resources/db/migration/` — Flyway MySQL 迁移文件（确认数量）
  - `backend/src/main/resources/db/migration-h2/` — Flyway H2 迁移文件（确认数量）
  - `deploy/.env.example` — 正确格式参考（使用 ${VAR} 占位符）
  - `deploy/.env` — 需检查的实际文件（已知含硬编码密码）
  - `.gitignore` — 检查 deploy/.env 是否被忽略

  **Acceptance Criteria**:
  - [ ] 预检报告保存到 `.sisyphus/evidence/task-0-preflight.txt`
  - [ ] 报告包含：后端测试通过数、前端 build 状态、ESLint 错误数、Flyway 同步状态、deploy/.env 安全检查结果
  - [ ] 如发现 P0 阻断项（如 deploy/.env 硬编码密码），在报告中以 `[P0 BLOCK]` 标记

  **QA Scenarios** (MANDATORY):
  ```
  Scenario: 后端测试全部通过
    Tool: Bash
    Preconditions: JDK 21 可用，backend/ 目录存在
    Steps:
      1. cd backend && ./mvnw test -Dspring-boot.run.profiles=local 2>&1
      2. 从输出中提取 "Tests run:" 行
      3. 确认 Failures: 0, Errors: 0
    Expected Result: Tests run ≥ 174, Failures: 0, Errors: 0
    Failure Indicators: 任何 Failure 或 Error 计数 > 0
    Evidence: .sisyphus/evidence/task-0-backend-test.txt

  Scenario: deploy/.env 安全检查 — 发现硬编码密码
    Tool: Bash
    Preconditions: deploy/.env 文件存在
    Steps:
      1. grep -n "PASSWORD=\|_PASSWORD=\|_ROOT_PASSWORD=" deploy/.env
      2. 对比 deploy/.env.example 格式（应使用 ${VAR_NAME} 占位符）
      3. 如发现具体密码值（非 ${}），标记为 P0 BLOCK
    Expected Result: 识别所有硬编码密码行，标记为 P0
    Failure Indicators: 无法读取 deploy/.env 或 .gitignore 缺失该文件
    Evidence: .sisyphus/evidence/task-0-env-security.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/task-0-backend-test.txt` — mvnw test 完整输出
  - [ ] `.sisyphus/evidence/task-0-frontend-build.txt` — pnpm build 输出
  - [ ] `.sisyphus/evidence/task-0-eslint.txt` — pnpm lint 输出
  - [ ] `.sisyphus/evidence/task-0-flyway-check.txt` — 迁移文件数量对比
  - [ ] `.sisyphus/evidence/task-0-env-security.txt` — deploy/.env 安全检查

  **Commit**: YES
  - Message: `chore(pre-release): add pre-flight check evidence`
  - Files: `.sisyphus/evidence/task-0-*.txt`
  - Pre-commit: None

- [x] 1. **审查维度 1 — 安全与配置审查**

  **What to do**:
  - 审查所有 39 个 Controller 的 `@PreAuthorize` 覆盖情况：确认每个端点均有合适的权限声明
  - 审查 JWT 实现：Token 生成/刷新/黑名单逻辑（`auth/` 模块），检查是否有 Token 泄漏路径
  - 审查 CORS 配置：各 profile 的 `allowedOrigins`/`allowedHeaders` 是否合理（禁止通配符 + allowCredentials）
  - 审查文件上传安全：`FileController` 扩展名白名单（20 种）、大小限制（50MB）、businessType 路径注入防护
  - 审查敏感数据脱敏：Logback 配置是否掩码 password/token/secret/authorization
  - 审查全局异常处理：`GlobalExceptionHandler` 是否覆盖所有异常类型（已知：缺 AuthorizationDeniedException → 应返回 403）
  - 审查配置安全：各 `application-*.yml` 是否使用 `${ENV_VAR:default}` 而非硬编码密钥
  - 审查 Docker 安全：Dockerfile 非 root 用户、HEALTHCHECK、敏感环境变量处理
  - 审查 Nginx 安全：SSL 配置、安全头（HSTS/X-Frame-Options）、SSE proxy_buffering
  - 产量：最多 15 条发现（超出部分列在报告末尾）

  **Must NOT do**:
  - 不要审查 mobile/ 或 scripts/（仅 README.md / start-dev.bat）
  - 不要建议添加新安全功能（如 WAF/IDS）——仅审查现有实现
  - 不要重审已确认的安全基线（安全基线复核清单 5/5 已通过）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 安全审查需要跨文件追踪权限链和配置传播路径
  - **Skills**: []
  - **Skills Evaluated but Omitted**: `playwright`（不需要浏览器交互）

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Tasks 2-6 完全并行）
  - **Parallel Group**: Wave 1（审查组）
  - **Blocks**: Task 7（综合）
  - **Blocked By**: Task 0（预检）

  **References**:
  - `backend/src/main/java/com/cgcpms/auth/` — JWT 认证完整实现（Token 生成/刷新/黑名单/过滤器链）
  - `backend/src/main/java/com/cgcpms/auth/config/CorsConfig.java` — CORS 配置（按 Profile 分发）
  - `backend/src/main/java/com/cgcpms/common/exception/GlobalExceptionHandler.java` — 全局异常映射
  - `backend/src/main/java/com/cgcpms/file/controller/FileController.java` — 文件上传安全（白名单 + 大小限制）
  - `backend/src/main/resources/logback-spring.xml` — 日志脱敏配置
  - `deploy/docker-compose.prod.yml` — 生产部署配置（useSSL、HEALTHCHECK、环境变量）
  - `frontend-admin/nginx.conf` — Nginx 配置（SSL/安全头/SSE buffering）

  **Acceptance Criteria**:
  - [ ] 审查报告生成到 `.sisyphus/evidence/review-1-security-config.md`
  - [ ] 报告格式：每个发现标注 [D1-NNN] | P0/P1/P2/P3 | file:line | 复现步骤 | 建议修复
  - [ ] 至少包含以下领域的发现：@PreAuthorize 覆盖、JWT 安全、CORS、文件上传、异常处理、配置安全
  - [ ] ≤15 条发现（超出部分在报告末尾简要列表）

  **QA Scenarios** (MANDATORY):
  ```
  Scenario: 发现 AuthorizationDeniedException → 500 问题（已知 P1-02）
    Tool: Bash (curl)
    Preconditions: 后端运行在 localhost:8080 (H2 profile)
    Steps:
      1. 以普通用户登录获取 token
      2. curl -H "Authorization: Bearer <token>" http://localhost:8080/api/system/users（无权限端点）
      3. 检查 HTTP 状态码
    Expected Result: 当前返回 500（已知缺陷），审查报告应将其标记为 P0 安全缺陷
    Failure Indicators: 无权限端点返回 200（权限旁路）
    Evidence: .sisyphus/evidence/review-1-auth-denied-500.txt

  Scenario: deploy/.env 硬编码密码验证
    Tool: Bash (grep)
    Preconditions: deploy/.env 存在
    Steps:
      1. grep -c "PASSWORD.*=.*[a-z0-9]" deploy/.env  # 统计硬编码密码行
      2. grep -c "PASSWORD.*=.*\${" deploy/.env       # 统计占位符行
    Expected Result: 硬编码行 > 0（确认 P0 发现），占位符行 ≥ 0
    Evidence: .sisyphus/evidence/review-1-env-hardcoded.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/review-1-security-config.md` — 审查报告
  - [ ] `.sisyphus/evidence/review-1-auth-denied-500.txt` — 异常处理验证
  - [ ] `.sisyphus/evidence/review-1-env-hardcoded.txt` — 环境变量检查

  **Commit**: YES
  - Message: `docs(review): add security and configuration review report`
  - Files: `.sisyphus/evidence/review-1-*.md`, `.sisyphus/evidence/review-1-*.txt`
  - Pre-commit: None

- [x] 2. **审查维度 2 — 后端代码与架构审查**

  **What to do**:
  - 审查 N+1 查询：搜索所有 Service 中循环内调用 Mapper/Service 方法的模式
  - 审查空 catch 块：搜索 `catch (Exception ignored)` 和 `catch (Exception e) {}` 
  - 审查代码重复：搜索相似代码块（>10 行相同逻辑）——已知 27 处 DateTimeFormatter 重复（已提取为 DateTimeUtils？验证是否真正完成）
  - 审查超大类：服务类 >500 行需标记——已知 WorkflowEngine 已拆分为 6 服务（验证拆分是否完整）
  - 审查 @SuppressWarnings：搜索所有 `@SuppressWarnings` 注解（当前已知 4 处），评估是否可消除
  - 审查 Mass Assignment 防护：检查 Controller 中 `@RequestBody` 是否直接绑定 Entity（应绑定 DTO）
  - 审查输入校验：检查所有 Controller 的 create/update 端点是否有 `@Valid` 注解，对应 Entity 是否有 Jakarta Validation 注解
  - 审查事务边界：`@Transactional` 使用是否正确（传播行为、只读优化、超时设置）
  - 产量：最多 15 条发现

  **Must NOT do**:
  - 不要质疑 WorkflowEngine 拆分决策（已完成的架构改进），仅验证拆分是否引入缺陷
  - 不要建议"应使用 X 框架代替 MyBatis-Plus"等架构迁移建议

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 架构审查需要深度理解依赖关系和跨模块影响
  - **Skills**: []
  - **Skills Evaluated but Omitted**: `playwright`（不需要 UI 测试）

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Tasks 1,3-6 完全并行）
  - **Parallel Group**: Wave 1（审查组）
  - **Blocks**: Task 7（综合）
  - **Blocked By**: Task 0（预检）

  **References**:
  - `backend/src/main/java/com/cgcpms/dashboard/service/DashboardService.java` — 已知 N+1 查询位置（`getManagementView()`）
  - `backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java` — 已拆分为 6 服务（验证拆分完整性）
  - `backend/src/main/java/com/cgcpms/common/util/DateTimeUtils.java` — 提取的日期工具类（验证 27 Service 是否已统一引用）
  - `backend/src/main/java/com/cgcpms/cost/strategy/` — CostGenerationStrategy 实现（4 种策略，检查代码重复）
  - `backend/src/main/java/com/cgcpms/payment/controller/PayApplicationController.java` — 已知校验缺失（零 @Valid）
  - `backend/src/main/java/com/cgcpms/invoice/controller/InvoiceController.java` — 已知校验缺失

  **Acceptance Criteria**:
  - [ ] 审查报告生成到 `.sisyphus/evidence/review-2-code-architecture.md`
  - [ ] 报告格式：每个发现标注 [D2-NNN] | P0/P1/P2/P3 | file:line | 复现步骤 | 建议修复
  - [ ] ≤15 条发现

  **QA Scenarios** (MANDATORY):
  ```
  Scenario: 验证 WorkflowEngine 拆分完整性
    Tool: Bash (grep + lsp_find_references)
    Preconditions: backend/ 源码可访问
    Steps:
      1. grep -rn "class WorkflowEngine" backend/src/main/java/ --include="*.java"
      2. 确认只存在 1 个类定义（或 0 个——如果已完全拆分）
      3. 搜索新拆分的服务类：WorkflowSubmitService, WorkflowApprovalService 等
      4. 确认所有审批操作均有新服务类负责
    Expected Result: WorkflowEngine 类已被拆分，旧类无残留业务逻辑
    Failure Indicators: 发现 2+ 个 WorkflowEngine 类定义，或拆分后仍有审批逻辑留在原类
    Evidence: .sisyphus/evidence/review-2-workflow-split.txt

  Scenario: 输入校验缺失检查 — PayApplicationController
    Tool: Bash (grep)
    Preconditions: backend/ 源码可访问
    Steps:
      1. grep -n "@Valid" backend/src/main/java/com/cgcpms/payment/controller/PayApplicationController.java
      2. grep -n "@RequestBody" backend/src/main/java/com/cgcpms/payment/controller/PayApplicationController.java
      3. 对比 @Valid 和 @RequestBody 数量
    Expected Result: 每个 @RequestBody 参数都应有 @Valid（当前可能为零）
    Failure Indicators: @Valid 数量 < @RequestBody 数量
    Evidence: .sisyphus/evidence/review-2-validation-gap.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/review-2-code-architecture.md`
  - [ ] `.sisyphus/evidence/review-2-workflow-split.txt`
  - [ ] `.sisyphus/evidence/review-2-validation-gap.txt`

  **Commit**: YES
  - Message: `docs(review): add backend code and architecture review report`
  - Files: `.sisyphus/evidence/review-2-*`
  - Pre-commit: None

- [x] 3. **审查维度 3 — 前端与 API 契约审查**

  **What to do**:
  - 审查路由守卫：`router/index.ts` 中所有路由是否正确设置 `meta.requiresAuth`，白名单仅含 `/login`
  - 审查 API 调用层：`src/api/request.ts` Token 刷新逻辑、错误拦截器是否正确处理 401/403
  - 审查空 catch 块：搜索所有 `} catch {` 或 `} catch {}` 静默吞异常（已知：receipt/payment/dashboard 等页）
  - 审查 `(e: any)` 类型断言：搜索所有 catch 参数类型为 `any`（已知：approval/detail.vue 7 处）
  - 审查 console.log/error：搜索 `src/` 下所有 console 语句（已知：NotificationBell 6 处 + ContractChangeList 4 处 + alert.ts 1 处）
  - 审查 API 模块桩代码：`contract.ts` 中 KPI 端点是否为 TODO 桩（搜索所有 `// TODO` 注释）
  - 审查 unused imports/variables：与 ESLint 报告交叉验证（已知 34 errors）
  - 审查 API 绕过：是否有页面直接用 `request()` 而非通过 API module（已知：cost/ledger.vue、cost-target/edit.vue）
  - 审查路由映射正确性：ContractApproval 路由是否指向正确组件（已知可能指向 dashboard）
  - 产量：最多 15 条发现

  **Must NOT do**:
  - 不要审查 UI/UX 设计（颜色、布局、交互体验）
  - 不要添加新功能组件

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 前端审查需要理解 Vue 组件树、路由、状态管理
  - **Skills**: []
  - **Skills Evaluated but Omitted**: `playwright`（暂不需要端到端测试，审查用静态分析）

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Tasks 1-2,4-6 完全并行）
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 7
  - **Blocked By**: Task 0

  **References**:
  - `frontend-admin/src/router/index.ts` — 18 路由组 + 守卫逻辑 + 白名单
  - `frontend-admin/src/api/request.ts` — Axios 拦截器（Token 刷新 + 错误处理）
  - `frontend-admin/src/api/modules/contract.ts` — 已知 KPI 端点桩代码（TODO）
  - `frontend-admin/src/components/NotificationBell.vue` — 已知 6 处 console.error() + 空 catch
  - `frontend-admin/src/components/ContractChangeList.vue` — 已知 4 处 console.error()
  - `frontend-admin/src/pages/approval/detail.vue` — 已知 7 处 `(e: any)`
  - `frontend-admin/src/pages/cost/ledger.vue:17` — 已知绕过 API module
  - `frontend-admin/src/stores/contract.ts` — 已知 try/finally 无 catch

  **Acceptance Criteria**:
  - [ ] 审查报告生成到 `.sisyphus/evidence/review-3-frontend-api.md`
  - [ ] 报告格式：每个发现标注 [D3-NNN] | P0/P1/P2/P3 | file:line | 复现步骤 | 建议修复
  - [ ] ≤15 条发现

  **QA Scenarios** (MANDATORY):
  ```
  Scenario: ESLint 零错误验证
    Tool: Bash
    Preconditions: frontend-admin/ 目录存在，依赖已安装
    Steps:
      1. cd frontend-admin && pnpm lint 2>&1
      2. 提取 errors 和 warnings 计数
      3. 确认 errors = 34（当前已知基线）
    Expected Result: 34 errors, 668 warnings（审前基线）
    Failure Indicators: lint 命令失败（配置错误）
    Evidence: .sisyphus/evidence/review-3-eslint-baseline.txt

  Scenario: (e: any) 类型断言检查 — approval/detail.vue
    Tool: Bash (grep)
    Preconditions: frontend-admin/src/pages/approval/detail.vue 存在
    Steps:
      1. grep -n "catch.*(e: any)" frontend-admin/src/pages/approval/detail.vue
      2. 统计命中行数
    Expected Result: 7 处 (e: any)（当前已知）
    Evidence: .sisyphus/evidence/review-3-any-types.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/review-3-frontend-api.md`
  - [ ] `.sisyphus/evidence/review-3-eslint-baseline.txt`
  - [ ] `.sisyphus/evidence/review-3-any-types.txt`

  **Commit**: YES
  - Message: `docs(review): add frontend and API contract review report`
  - Files: `.sisyphus/evidence/review-3-*`
  - Pre-commit: None

- [x] 4. **审查维度 4 — 数据与基础设施审查**

  **What to do**:
  - 审查 Flyway 迁移一致性：对比 `db/migration/` 与 `db/migration-h2/` 中 V1-V46 的校验和，确认 MySQL 与 H2 版本完全同步
  - 审查索引设计：检查各实体表是否缺少必要索引（如外键列、查询高频列）——已知 `mat_purchase_request_item.material_id` 缺索引
  - 审查命名一致性：检查 `created_at`/`updated_at` vs `created_time`/`updated_time` 分裂（V21 为分界线）
  - 审查种子数据正确性：V42 中 MATERIAL_CLERK 和 FINANCE 角色是否有关联权限（已知可能零权限）
  - 审查数据库配置文件：各 application-*.yml 的 datasource 配置是否正确
  - 审查 Dockerfile 最佳实践：多阶段构建、非 root 用户、HEALTHCHECK、环境变量默认值
  - 审查 docker-compose.prod.yml：服务依赖链（depends_on + healthcheck）、资源限制、useSSL 配置（已知可能 false）
  - 审查 CI/CD 流水线：`.github/workflows/ci.yml` 触发条件、缓存策略、服务依赖
  - 审查备份恢复方案：`doc/备份恢复方案_2026-06-13.md` 命令是否可在目标环境执行
  - 产量：最多 15 条发现

  **Must NOT do**:
  - 不要执行实际的数据库迁移（仅静态审查）
  - 不要修改 CI/CD 配置（仅报告问题）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 跨维度审查（数据库 + Docker + CI），需多份配置文件交叉验证
  - **Skills**: []
  - **Skills Evaluated but Omitted**: `git-master`（不需要 Git 历史操作）

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Tasks 1-3,5-6 完全并行）
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 7
  - **Blocked By**: Task 0

  **References**:
  - `backend/src/main/resources/db/migration/V1__*.sql` through `V46__*.sql` — MySQL 迁移
  - `backend/src/main/resources/db/migration-h2/V1__*.sql` through `V46__*.sql` — H2 迁移
  - `V42__seed_material_warehouse_cost_subject.sql` — 已知种子角色零权限风险
  - `backend/Dockerfile` — 多阶段构建（maven:3.9-jdk21 → jre21）
  - `frontend-admin/Dockerfile` — 多阶段构建（node:20-alpine → nginx:1.27-alpine）
  - `deploy/docker-compose.prod.yml` — 生产编排（已知 useSSL 问题）
  - `.github/workflows/ci.yml` — 5 job CI/CD（backend-test / frontend-build / flyway-check / docker-build / deploy）

  **Acceptance Criteria**:
  - [ ] 审查报告生成到 `.sisyphus/evidence/review-4-data-infra.md`
  - [ ] 报告格式：每个发现标注 [D4-NNN] | P0/P1/P2/P3 | file:line | 复现步骤 | 建议修复
  - [ ] ≤15 条发现

  **QA Scenarios** (MANDATORY):
  ```
  Scenario: Flyway 迁移文件计数一致性
    Tool: Bash
    Preconditions: backend/src/main/resources/db/migration/ 和 migration-h2/ 存在
    Steps:
      1. Get-ChildItem backend/src/main/resources/db/migration/V*.sql | Measure-Object | Select-Object -ExpandProperty Count
      2. Get-ChildItem backend/src/main/resources/db/migration-h2/V*.sql | Measure-Object | Select-Object -ExpandProperty Count
      3. 对比两个数字
    Expected Result: 均为 46（文件数量一致）
    Failure Indicators: 计数不匹配（不同步）
    Evidence: .sisyphus/evidence/review-4-flyway-count.txt

  Scenario: docker-compose.prod.yml useSSL 检查
    Tool: Bash (grep)
    Preconditions: deploy/docker-compose.prod.yml 存在
    Steps:
      1. grep -n "useSSL" deploy/docker-compose.prod.yml
      2. 检查值为 true 还是 false
    Expected Result: useSSL=true（生产环境要求）或发现 useSSL=false（标记 P0）
    Evidence: .sisyphus/evidence/review-4-useSSL.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/review-4-data-infra.md`
  - [ ] `.sisyphus/evidence/review-4-flyway-count.txt`
  - [ ] `.sisyphus/evidence/review-4-useSSL.txt`

  **Commit**: YES
  - Message: `docs(review): add data and infrastructure review report`
  - Files: `.sisyphus/evidence/review-4-*`
  - Pre-commit: None

- [x] 5. **审查维度 5 — 业务逻辑正确性审查**

  **What to do**:
  - 审查审批引擎：WorkflowEngine 拆分后的事务一致性（`isCritical()` 控制回滚 vs swallow-and-log）、乐观锁（taskVersion）、幂等键（idempotencyKey）
  - 审查成本计算：CostGenerationStrategy 4 种实现的逻辑正确性（金额联动、幂等保护 uk_cost_source_item）
  - 审查库存并发安全：乐观锁 @Version 是否正确工作（已知通过并发测试验证）、出入库余额联动
  - 审查结算锁定：不可变锁定逻辑是否正确阻止重复结算（已知无 contractId 唯一性校验——P0 缺陷）
  - 审查质保金计算：warrantyRate 计算是否正确（已知百分比 vs 比率混淆——P1 缺陷）
  - 审查付款金额校验：付款申请金额是否与合同金额联动校验
  - 审查工作流权限：PURCHASE_REQUEST switch case 是否缺失（已知 P1-04 缺陷）
  - 审查 SSE 实时推送：Nginx 缓冲是否阻断 SSE 事件流（已知 P0 缺陷）
  - 产量：最多 15 条发现

  **Must NOT do**:
  - 不要质疑业务公式的正确性（如"质保金应为 5% 还是 3%"——这是业务方决策）
  - 不要验证成本核算公式的数学精度——仅检查结构性逻辑（如是否应为 warrantyRate/100）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 业务逻辑审查需要追踪跨模块数据流和并发场景
  - **Skills**: []
  - **Skills Evaluated but Omitted**: `playwright`（runtime 验证用 curl + Bash）

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Tasks 1-4,6 完全并行）
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 7
  - **Blocked By**: Task 0

  **References**:
  - `backend/src/main/java/com/cgcpms/workflow/service/WorkflowCoreService.java` — 核心审批逻辑（事务 + 乐观锁 + 幂等）
  - `backend/src/main/java/com/cgcpms/workflow/controller/WorkflowController.java` — 已知缺失 PURCHASE_REQUEST case
  - `backend/src/main/java/com/cgcpms/cost/strategy/` — 4 种 CostGenerationStrategy 实现
  - `backend/src/main/java/com/cgcpms/settlement/service/StlSettlementService.java` — 结算服务（已知缺 contractId 唯一性校验）
  - `backend/src/main/java/com/cgcpms/inventory/service/MatStockService.java` — 库存服务（乐观锁 @Version）
  - `frontend-admin/nginx.conf:98-118` — SSE proxy_buffering 配置（已知 on→应 off）

  **Acceptance Criteria**:
  - [ ] 审查报告生成到 `.sisyphus/evidence/review-5-business-logic.md`
  - [ ] 报告格式：每个发现标注 [D5-NNN] | P0/P1/P2/P3 | file:line | 复现步骤 | 建议修复
  - [ ] ≤15 条发现

  **QA Scenarios** (MANDATORY):
  ```
  Scenario: 重复结算 guard 检查 — StlSettlementService.create()
    Tool: Bash (grep)
    Preconditions: backend/ 源码可访问
    Steps:
      1. grep -n "contractId\|DUPLICATE\|exists.*settlement\|unique.*contract" backend/src/main/java/com/cgcpms/settlement/service/StlSettlementService.java
      2. 检查 create() 方法中是否有 contractId 唯一性查询
      3. 如无查询，标记为 P0 缺陷
    Expected Result: 发现无 contractId 唯一性校验（已知 P0-01）
    Failure Indicators: 意外发现了 guard 逻辑（说明已修复）
    Evidence: .sisyphus/evidence/review-5-settlement-guard.txt

  Scenario: 质保金计算验证 — warrantyAmount
    Tool: Bash (grep)
    Preconditions: backend/ 源码可访问
    Steps:
      1. grep -rn "warrantyRate\|warrantyAmount\|DEFAULT_WARRANTY_RATE" backend/src/main/java/com/cgcpms/settlement/
      2. 检查计算公式：是否为 amount * warrantyRate / 100 或 amount * warrantyRate
      3. 对比 DEFAULT_WARRANTY_RATE 常量值（应为 0.05）
    Expected Result: 如 warrantyRate 值为 5.00 且直接相乘，标记 P1 缺陷（已知 P1-01）
    Evidence: .sisyphus/evidence/review-5-warranty-calc.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/review-5-business-logic.md`
  - [ ] `.sisyphus/evidence/review-5-settlement-guard.txt`
  - [ ] `.sisyphus/evidence/review-5-warranty-calc.txt`

  **Commit**: YES
  - Message: `docs(review): add business logic correctness review report`
  - Files: `.sisyphus/evidence/review-5-*`
  - Pre-commit: None

- [x] 6. **审查维度 6 — 修复回归验证**

  **What to do**:
  - 从 `audit-fixes.md` 完成的 24 项任务中，提取近期变更文件列表（利用 git diff f63f342~10..f63f342 --stat）
  - 对 10 个随机抽取的已修复文件进行回归检查：
    - WorkflowEngine 拆分后的子服务类是否功能完整（WorkflowSubmitService/ApprovalService/TaskService 等）
    - DateTimeUtils 是否被所有 27 个 Service 正确引用（而非继续各自定义）
    - CostSubjectResolver 是否消除了 4 个策略中的 `resolveDefaultSubjectId()` 重复
    - DashboardService N+1 查询是否已优化（批量查询替代循环内查询）
    - Mass Assignment 防护是否正确实施（DTO 引入 + @JsonIgnore）
    - 输入校验是否已添加（@Valid + Jakarta Validation 注解）
  - 检查 audit-fixes 之后是否引入了新的 `@SuppressWarnings`、空 catch 块、或 hardcoded 值
  - 验证 audit-fixes 是否引入了新的 TypeScript 编译错误或测试失败
  - 产量：最多 15 条回归发现

  **Must NOT do**:
  - 不要重新审查 audit-fixes 已解决的问题（如再次报告相同的"27 处 DTF 重复"）
  - 不要质疑 audit-fixes 的设计选择（如"为什么拆分为 6 个服务而不是 4 个"）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 需要对近期 298 文件变更进行结构化回归验证
  - **Skills**: [`git-master`]
    - `git-master`: 需要 git diff/log 精确追踪变更文件列表

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Tasks 1-5 完全并行）
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 7
  - **Blocked By**: Task 0

  **References**:
  - `git log --oneline -20` — 近期提交历史（audit-fixes 在 f63f342~10 范围内）
  - `git diff f63f342~10..f63f342 --stat` — 变更文件清单（298 files）
  - `backend/src/main/java/com/cgcpms/workflow/service/WorkflowSubmitService.java` — 拆分出的新服务
  - `backend/src/main/java/com/cgcpms/common/util/DateTimeUtils.java` — 提取的日期工具
  - `backend/src/main/java/com/cgcpms/cost/strategy/` — CostSubjectResolver 消除重复
  - `backend/src/main/java/com/cgcpms/dashboard/service/DashboardService.java` — N+1 优化
  - `.sisyphus/plans/audit-fixes.md` — 原始修复计划（24 任务）

  **Acceptance Criteria**:
  - [ ] 审查报告生成到 `.sisyphus/evidence/review-6-fix-regression.md`
  - [ ] 报告格式：每个发现标注 [D6-NNN] | P0/P1/P2/P3 | file:line | 原始任务编号 | 回归描述 | 建议修复
  - [ ] ≤15 条发现

  **QA Scenarios** (MANDATORY):
  ```
  Scenario: git diff 变更文件清单提取
    Tool: Bash (git)
    Preconditions: git repo 存在
    Steps:
      1. git diff f63f342~10..f63f342 --stat --name-only
      2. 统计变更文件总数
      3. 随机选取 10 个文件进行详细回归检查
    Expected Result: ~298 文件变更
    Evidence: .sisyphus/evidence/review-6-git-diff.txt

  Scenario: DateTimeUtils 引用一致性检查
    Tool: Bash (grep)
    Preconditions: backend/src/main/java/ 可访问
    Steps:
      1. grep -rn "DateTimeFormatter.ofPattern" backend/src/main/java/com/cgcpms/ --include="*.java" | grep -v "DateTimeUtils"
      2. 统计仍有独立定义 DTF 的 Service 数量
    Expected Result: 应为 0（所有 Service 引用 DateTimeUtils）
    Failure Indicators: 仍有 >0 个 Service 自行定义 DTF（回归）
    Evidence: .sisyphus/evidence/review-6-dtf-regression.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/review-6-fix-regression.md`
  - [ ] `.sisyphus/evidence/review-6-git-diff.txt`
  - [ ] `.sisyphus/evidence/review-6-dtf-regression.txt`

  **Commit**: YES
  - Message: `docs(review): add fix regression verification report`
  - Files: `.sisyphus/evidence/review-6-*`
  - Pre-commit: None

- [x] 7. **综合分析 — 去重、分级、依赖映射**

  **What to do**:
  - 收集所有 6 份审查报告（review-1 至 review-6）
  - 执行跨维度去重：相同 file:line 的发现合并为一个（标注原始维度来源）
  - 按统一标准分类：P0（阻断上线）、P1（高优/首个迭代）、P2（中优/可延后）、P3（低优/优化项）
  - 构建修复依赖图：哪些修复依赖其他修复先完成？（如 P0-02 角色修复阻塞并发测试）
  - 将修复任务按依赖关系分波：
    - Wave 3-A: 无依赖 P0（可立即并行修复）
    - Wave 3-B: 依赖 A 的 P0
    - Wave 3-C: 无依赖 P1（可并行）
    - Wave 3-D: 依赖 C 的 P1
  - 输出综合报告到 `.sisyphus/evidence/synthesis-report.md`
  - 在计划中追加具体的修复任务（Task 8-N），基于综合报告

  **Must NOT do**:
  - 不要修改原始审查报告的发现内容
  - 不要自行决定 P 级别（严格遵循 P0=上线阻断、P1=高优、P2=中优、P3=低优 标准）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 需要全局视角进行跨维度综合分析
  - **Skills**: []
  - **Skills Evaluated but Omitted**: `git-master`（不需要 Git 历史）

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖所有审查报告）
  - **Parallel Group**: Wave 2（单独）
  - **Blocks**: Tasks 8-N（所有修复依赖综合报告）
  - **Blocked By**: Tasks 1-6（所有审查必须完成）

  **References**:
  - `.sisyphus/evidence/review-1-security-config.md` — 安全与配置审查
  - `.sisyphus/evidence/review-2-code-architecture.md` — 代码与架构审查
  - `.sisyphus/evidence/review-3-frontend-api.md` — 前端与 API 审查
  - `.sisyphus/evidence/review-4-data-infra.md` — 数据与基础设施审查
  - `.sisyphus/evidence/review-5-business-logic.md` — 业务逻辑审查
  - `.sisyphus/evidence/review-6-fix-regression.md` — 修复回归验证
  - `doc/05-上线部署/上线就绪检查清单_2026-06-13.md` — P 级别参考标准（P0/P1/P2/P3 定义）

  **Acceptance Criteria**:
  - [ ] 综合报告生成到 `.sisyphus/evidence/synthesis-report.md`
  - [ ] 报告包含：去重后的发现总数、P 级别分布、依赖图、修复波次建议
  - [ ] 所有 P0 修复任务已生成为 Task 8-N（追加到本计划）
  - [ ] 所有 P1 修复任务已生成为 Task N+1-M（追加到本计划）

  **QA Scenarios** (MANDATORY):
  ```
  Scenario: 跨维度去重验证 — 相同 file:line 只出现一次
    Tool: Bash (check)
    Preconditions: 6 份审查报告均存在
    Steps:
      1. grep -h "file:line" .sisyphus/evidence/review-*.md | sort | uniq -d
      2. 检查是否输出了重复项（如 review-1 和 review-4 都报告了同一个文件）
    Expected Result: 综合报告将重复发现合并，所有原始来源标注在合并记录中
    Failure Indicators: 综合报告中同一 file:line 出现 2+ 次（未去重）
    Evidence: .sisyphus/evidence/synthesis-dedup-check.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/synthesis-report.md`
  - [ ] `.sisyphus/evidence/synthesis-dedup-check.txt`

  **Commit**: YES
  - Message: `docs(review): add synthesis report and fix plan`
  - Files: `.sisyphus/evidence/synthesis-*`
  - Pre-commit: None

---

## Final Verification Wave (MANDATORY — after ALL fix tasks)

- [x] F1. **后端回归测试** — `oracle`
  运行 `cd backend && ./mvnw test`，确认 ≥174 用例全部通过。检查新增测试类结构合规性（@Test 注解、断言充分、命名规范）。搜索代码库确认无新增 `@SuppressWarnings`、空 catch、System.out.println。
  Output: `Tests [N/N PASS] | New Tests [N] | Code Smells [N] | VERDICT: APPROVE/REJECT`

- [x] F2. **前端构建与 ESLint** — `unspecified-high`
  运行 `cd frontend-admin && pnpm build`（vue-tsc + vite build），确认零 TypeScript 错误。运行 `pnpm lint` 确认 ESLint 0 错误 + 0 warning。检查 dist/ 产物完整性。
  Output: `Build [PASS/FAIL] | Lint [N errors/N warnings] | Dist [N files] | VERDICT`

- [x] F3. **E2E 冒烟测试** — `unspecified-high`（+ `playwright` skill）
  启动后端（H2 profile）+ 前端 dev server，执行全部 10 个 Playwright spec。覆盖登录/合同/审批/库存/发票核心路径。截图保存到 `.sisyphus/evidence/final-qa/`。
  Output: `Specs [N/N pass] | Tests [N/N] | Screenshots [N] | VERDICT`

- [x] F4. **审查维度复扫** — `deep`
  对每个审查维度的 top-3 发现执行快速复扫（相同 grep 命令），确认已修复项不再出现。检查前 24 项 audit-fixes 变更文件的 git diff 无回归。
  Output: `Dim 1 [CLEAN/N residuals] | ... | Dim 6 [CLEAN/N] | VERDICT`

---

## Commit Strategy

- **Wave 0**: `chore(pre-release): add pre-flight check evidence` — `.sisyphus/evidence/task-0-*.txt`
- **Wave 1**: `docs(review): add review dimension reports` — `.sisyphus/evidence/review-*.md`
- **Wave 2**: `docs(review): add synthesis and fix plan` — `.sisyphus/evidence/synthesis-report.md`
- **Wave 3**: `fix(N): description` — 每个修复独立提交，含测试文件
- **Wave 4**: `chore(review): add final verification evidence` — `.sisyphus/evidence/task-f*-*.txt`

---

## Success Criteria

### Verification Commands
```bash
cd backend && ./mvnw test 2>&1 | Select-String "Tests run:"
# Expected: Tests run: 174+, Failures: 0, Errors: 0

cd frontend-admin && pnpm build 2>&1
# Expected: exit code 0, no TypeScript errors

cd frontend-admin && pnpm lint 2>&1
# Expected: 0 errors, 0 warnings

docker compose -f deploy/docker-compose.prod.yml config 2>&1
# Expected: valid compose file, no syntax errors
```

### Final Checklist
- [ ] 所有 P0 问题已修复（目标 0 个）
- [ ] P1 问题 ≤3 个（均有文档化接受理由）
- [ ] 后端测试 100% 通过（≥174 用例）
- [ ] 前端 pnpm build 零 TypeScript 错误
- [ ] 前端 ESLint 0 errors + 0 warnings
- [ ] E2E 10 spec 全部通过
- [ ] deploy/.env 无硬编码密钥
- [ ] 所有 46 Flyway 迁移校验和匹配（MySQL ↔ H2）
