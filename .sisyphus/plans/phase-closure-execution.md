# 验收收口执行计划

## TL;DR

> **Quick Summary**: 将 CGC-PMS 从 92% 功能完成度推进至可交付上线状态。包含干净环境复验、6 条业务闭环验收、安全验收、E2E 自动化、生产工程化、二期范围锁定。
>
> **Deliverables**:
> - 验收复验报告（后端测试 + 前端构建 + MySQL 迁移）
> - 业务闭环验收记录（6 个闭环 + 合同变更 + 审批异常路径）
> - 权限/安全/多租户验收报告
> - E2E 自动化脚本 + 报告
> - 性能基线 + 并发一致性报告
> - 部署手册 + Docker 镜像 + CI/CD
> - 备份恢复方案 + 监控清单
> - 二期 Backlog
>
> **Estimated Effort**: Large（约 20-30 个工作日，分散在 5 个并行波次）
> **Parallel Execution**: YES - 5 waves
> **Critical Path**: Wave 1（复验）→ Wave 2（业务验收）→ Wave 4（工程化）→ Wave Final（审核）

---

## Context

### Original Request
将《剩余工作计划书\_2026-06-12》及补充工作包转为可执行的分波次任务计划。

### Interview Summary
**Key Discussions**:
- 生产部署目标：单机 Docker Compose（非 K8s）
- E2E 框架：Playwright 推荐
- 需补充合同变更闭环、审批异常路径、H2 双环境复验
- 需增加业务方签字流程，非纯技术自判
- Docker 环境已就绪（Phase A.4 已完成）

**Research Findings**:
- 后端 162/162 全量测试通过（H2 + MySQL 双环境）
- 前端 pnpm build 通过
- Flyway V1~V40 迁移完整
- 项目已进入"可交付候选/验收收口"阶段

### Metis Review
Metis 不可用（token 不可用），以之前详细分析中的 8 个遗漏项 + 4 个风险补充 + 7 条门禁补充作为替代 gap 分析。

---

## Work Objectives

### Core Objective
将系统从"功能完成"推进到"业务验收通过 + 生产就绪 + 二期范围锁定"。

### Concrete Deliverables
- `doc/验收复验报告_YYYY-MM-DD.md`
- `doc/业务主闭环验收报告_YYYY-MM-DD.md`
- `doc/权限矩阵验收表_YYYY-MM-DD.md`
- `doc/多租户数据隔离验收报告_YYYY-MM-DD.md`
- `doc/E2E测试报告_YYYY-MM-DD.md`
- `doc/性能基线报告_YYYY-MM-DD.md`
- `doc/并发一致性测试报告_YYYY-MM-DD.md`
- `doc/部署与回滚手册_YYYY-MM-DD.md`
- `doc/备份恢复方案_YYYY-MM-DD.md`
- `doc/监控告警清单_YYYY-MM-DD.md`
- `doc/二期Backlog与范围说明_YYYY-MM-DD.md`
- `backend/Dockerfile`
- `frontend-admin/Dockerfile`
- `frontend-admin/nginx.conf`
- `deploy/docker-compose.prod.yml`
- `.github/workflows/ci.yml`

### Definition of Done
- [ ] 后端 `.\mvnw.cmd clean test` 全量通过（MySQL + H2 双 profile）
- [ ] 前端 `pnpm build` 零错误
- [ ] MySQL 全新库 Flyway V1~V40 迁移一次成功
- [ ] 6 条业务闭环 + 合同变更 + 审批异常路径全部验收通过
- [ ] 权限矩阵、数据隔离、安全基线验收通过
- [ ] E2E 核心路径脚本可重复执行通过
- [ ] 后端 + 前端 Docker 镜像可构建和运行
- [ ] 备份恢复演练通过
- [ ] 二期 Backlog 清晰可执行
- [ ] 业务方签字确认

### Must Have
- 全部 13+7=20 条上线门禁满足
- 所有 P0/P1 问题关闭
- 交付物文档齐全
- 干净环境全流程可复现

### Must NOT Have (Guardrails)
- 不新增主功能开发
- 不引入新的第三方依赖（Playwright 除外）
- 不修改已有业务逻辑（除非验收过程中发现 blocking bug）
- 不碰移动端代码（`mobile/` 目录）
- 不配置真实财务系统/BI/OA 集成
- 不进行大规模生产压测

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: YES（JUnit 5 + Surefire 后端；前端无测试框架）
- **Automated tests**: Tests-after（利用已有 162 后端测试；E2E 从零搭建）
- **Framework**: JUnit 5 + Surefire（后端）+ Playwright（前端 E2E）

### QA Policy
Every task MUST include agent-executed QA scenarios.
Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

- **Backend/CLI**: Use Bash (PowerShell) — Run Maven commands, assert exit code + report content
- **API**: Use Bash (curl) — Send requests, assert status + response fields
- **Frontend/UI**: Use Playwright — Navigate, interact, assert DOM, screenshot
- **Docker**: Use Bash — `docker compose` commands, `docker ps` health check

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately - 环境复验 + 工具搭建):
├── T1: Backend MySQL 全量测试复验 [quick]
├── T2: Backend H2 全量测试复验 [quick]
├── T3: 前端生产构建复验 [quick]
├── T4: MySQL Flyway 全新库迁移复验 [quick]
└── T5: Playwright E2E 框架搭建 [quick]

Wave 2 (After Wave 1 - 业务闭环验收 + 安全验收, MAX PARALLEL):
├── T6: 项目→合同闭环验收（含合同变更） [deep]
├── T7: 采购→材料闭环验收 [deep]
├── T8: 分包→计量闭环验收 [deep]
├── T9: 付款→发票闭环验收 [deep]
├── T10: 结算→归档闭环验收 [deep]
├── T11: 经营分析→预警闭环验收 [deep]
├── T12: 审批异常路径验收 [deep]
├── T13: 权限矩阵验收 [unspecified-high]
├── T14: 多租户数据隔离验收 [unspecified-high]
├── T15: 安全基线复核 [unspecified-high]
└── T16: E2E 脚本 Batch 1（登录+合同+审批） [visual-engineering]

Wave 3 (After Wave 2 - 质量补强 + E2E 扩展):
├── T17: E2E 脚本 Batch 2（采购+库存+发票） [visual-engineering]
├── T18: E2E 脚本 Batch 3（通知+结算+驾驶舱） [visual-engineering]
├── T19: 并发与一致性测试 [unspecified-high]
├── T20: 性能基线测试 [unspecified-high]
├── T21: 业务方验收签字协调 [deep]
└── T22: 验收报告汇总 [writing]

Wave 4 (After Wave 3 - 生产工程化, MAX PARALLEL):
├── T23: 后端 Dockerfile + 镜像构建 [quick]
├── T24: 前端 Dockerfile + Nginx 配置 [quick]
├── T25: 集成生产 docker-compose.yml [quick]
├── T26: SSL/TLS + 健康检查 + 日志轮转 + JVM 调优 [unspecified-high]
├── T27: CI/CD 流水线配置 [quick]
├── T28: 备份恢复方案 + 演练 [unspecified-high]
└── T29: 监控告警配置 [unspecified-high]

Wave 5 (After Wave 4 - 文档 + 二期规划):
├── T30: 部署与回滚手册 [writing]
├── T31: 二期范围：移动端 [writing]
├── T32: 二期范围：财务接口 + 设备租赁/劳务 + BI [writing]
└── T33: 上线就绪检查清单 [writing]

Wave FINAL (After ALL - 4 并行审查):
├── F1: Plan compliance audit [oracle]
├── F2: Code quality review [unspecified-high]
├── F3: Real manual QA [unspecified-high]
└── F4: Scope fidelity check [deep]
```

### Dependency Matrix

- **T1-T5**: — — Wave 2 all + T6-T11, Wave 1
- **T6-T11**: Wave 1 — T12, T16-T21, Wave 2 (independent of each other)
- **T12**: Wave 2 completion — Wave 3, Wave 2
- **T13-T15**: Wave 1 — Wave 3, Wave 2
- **T16**: T6 — Wave 3, Wave 2
- **T17-T18**: T16 — Wave Final, Wave 3
- **T19-T20**: Wave 2 — Wave 4, Wave 3
- **T21**: Wave 2 — Wave 4, Wave 3
- **T22**: T6-T12 — Wave 4, Wave 3
- **T23-T29**: Wave 3 — Wave 5, Wave 4
- **T30-T33**: Wave 4 — Wave Final, Wave 5
- **F1-F4**: Wave 5 — —, Wave Final

### Agent Dispatch Summary

- **Wave 1**: 5 — T1-T4 → `quick`, T5 → `quick`
- **Wave 2**: 11 — T6-T12 → `deep`, T13-T15 → `unspecified-high`, T16 → `visual-engineering`
- **Wave 3**: 6 — T17-T18 → `visual-engineering`, T19-T20 → `unspecified-high`, T21 → `deep`, T22 → `writing`
- **Wave 4**: 7 — T23-T25 → `quick`, T26 → `unspecified-high`, T27 → `quick`, T28-T29 → `unspecified-high`
- **Wave 5**: 4 — T30-T33 → `writing`
- **FINAL**: 4 — F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

### Wave 1 — 干净环境复验 + 工具搭建（全部并行）

> **已完成**: Docker Compose 启动复验（MySQL + Redis + MinIO 全部 healthy，见 Phase A.4）

- [x] 1. **Backend MySQL 全量测试复验**

  **What to do**:
  - 清理 `backend/target/` 历史产物：`Remove-Item -Recurse -Force backend\target -ErrorAction SilentlyContinue`
  - 确保 Docker MySQL 在运行：`docker ps --filter name=mysql`
  - 执行全量测试：`cd backend; .\mvnw.cmd clean test`
  - 记录测试耗时、通过数/失败数
  - 与已有 Surefire 报告（162/162）比对，确认一致
  - 若失败，记录失败用例清单、所属模块、初步原因分析

  **Must NOT do**:
  - 不要跳过失败用例去"先修别的"
  - 不要修改任何源码（除非是环境适配问题，如路径、端口）

  **Recommended Agent Profile**:
  - **Category**: `quick` — 标准 Maven 命令执行 + 结果比对
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T2、T3、T4、T5 并行）
  - **Parallel Group**: Wave 1
  - **Blocks**: T6-T16（Wave 2 全部）
  - **Blocked By**: None（Docker MySQL 已就绪）

  **References**:
  - `backend/pom.xml` — Maven 配置、Surefire 插件配置
  - `backend/src/test/java/` — 测试用例目录（162 用例）
  - `README.md` — 后端测试命令参考

  **Acceptance Criteria**:
  - [ ] `.\mvnw.cmd clean test` 退出码 0
  - [ ] Surefire 报告通过数 ≥ 162
  - [ ] 失败数为 0（或有明确的失败清单 + 分析）

  **QA Scenarios**:
  ```
  Scenario: Happy path - full test suite passes
    Tool: Bash (PowerShell)
    Preconditions: Docker MySQL running healthy, backend/target cleaned
    Steps:
      1. cd D:\projects-test\cgc-pms\backend
      2. .\mvnw.cmd clean test
      3. Wait for BUILD SUCCESS
    Expected Result: Exit code 0, "Tests run: 162, Failures: 0, Errors: 0, Skipped: 0"
    Failure Indicators: Exit code non-zero, or "Failures: N" where N > 0
    Evidence: .sisyphus/evidence/task-1-test-output.txt

  Scenario: Build fails due to compilation error
    Tool: Bash (PowerShell)
    Preconditions: Intentionally break one source file (introduce syntax error)
    Steps:
      1. Add a syntax error to a random service file
      2. Run .\mvnw.cmd clean test
      3. Restore the original file
    Expected Result: BUILD FAILURE with clear compilation error pointing to file:line
    Failure Indicators: Test passes despite error (would indicate test isolation issue)
    Evidence: .sisyphus/evidence/task-1-compile-error.txt
  ```

  **Commit**: YES（随 Wave 1 组）
  - Message: `chore(verify): backend MySQL full test re-verification`
  - Files: `doc/验收复验报告_*.md`（验收复验报告追加）

- [x] 2. **Backend H2 全量测试复验**

  **What to do**:
  - 使用 `local` profile（H2 内存库），无需 MySQL 依赖
  - 执行：`cd backend; .\mvnw.cmd clean test -Dspring.profiles.active=local`
  - 确认 H2 Flyway 自动迁移（migration-h2）无漂移
  - 比对 H2 和 MySQL 测试结果，确认一致

  **Must NOT do**:
  - 不要手工维护 H2 schema（Flyway 应自动生成）
  - 不要修改 migration-h2 脚本

  **Recommended Agent Profile**:
  - **Category**: `quick` — 标准 Maven 命令执行 + profile 切换
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T1、T3、T4、T5 并行）
  - **Parallel Group**: Wave 1
  - **Blocks**: T6-T16（确保双环境一致）
  - **Blocked By**: None

  **References**:
  - `backend/src/main/resources/application-local.yml` — H2 profile 配置
  - `backend/src/test/resources/` — 测试配置

  **Acceptance Criteria**:
  - [ ] H2 profile 测试全量通过，162/162
  - [ ] H2 和 MySQL 测试结果一致
  - [ ] Flyway migration-h2 自动执行，无需手工干预

  **QA Scenarios**:
  ```
  Scenario: Happy path - H2 full test suite passes
    Tool: Bash (PowerShell)
    Preconditions: backend/target cleaned, NO MySQL required
    Steps:
      1. cd D:\projects-test\cgc-pms\backend
      2. .\mvnw.cmd clean test -Dspring.profiles.active=local
      3. Wait for BUILD SUCCESS
    Expected Result: Exit code 0, "Tests run: 162, Failures: 0"
    Failure Indicators: Any test failure or Flyway migration error
    Evidence: .sisyphus/evidence/task-2-h2-test-output.txt

  Scenario: H2 profile isolation - MySQL NOT required
    Tool: Bash (PowerShell)
    Preconditions: Docker MySQL stopped (docker compose stop mysql)
    Steps:
      1. docker compose stop mysql
      2. Run H2 test suite
      3. docker compose start mysql
    Expected Result: Tests still pass without MySQL
    Failure Indicators: "Connection refused" for MySQL
    Evidence: .sisyphus/evidence/task-2-h2-isolation.txt
  ```

  **Commit**: YES（随 Wave 1 组）
  - Message: `chore(verify): backend H2 full test re-verification`
  - Files: `doc/验收复验报告_*.md`

- [x] 3. **前端生产构建复验**

  **What to do**:
  - 清理 `frontend-admin/dist/` 和 `node_modules/`（可选，如需验证 lock 文件完整性）
  - 安装依赖：`cd frontend-admin; pnpm install`
  - 执行类型检查 + 生产构建：`pnpm build`
  - 记录构建警告（大 chunk、类型警告、路由懒加载问题）
  - 判断警告是否阻断上线

  **Must NOT do**:
  - 不要跳过 TypeScript 类型检查
  - 不要修改源码来"消除警告"（记录即可）

  **Recommended Agent Profile**:
  - **Category**: `quick` — 标准前端构建命令
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T1、T2、T4、T5 并行）
  - **Parallel Group**: Wave 1
  - **Blocks**: T16（E2E 依赖前端可构建）
  - **Blocked By**: None

  **References**:
  - `frontend-admin/package.json` — build scripts、依赖列表
  - `frontend-admin/vite.config.ts` — Vite 构建配置
  - `frontend-admin/tsconfig.json` — TypeScript 配置

  **Acceptance Criteria**:
  - [ ] `pnpm build` 退出码 0
  - [ ] 无 TypeScript 阻断错误
  - [ ] 构建警告已记录并分类（阻断/非阻断）

  **QA Scenarios**:
  ```
  Scenario: Happy path - production build succeeds
    Tool: Bash (PowerShell)
    Preconditions: dist/ cleaned, node_modules/ present
    Steps:
      1. cd D:\projects-test\cgc-pms\frontend-admin
      2. pnpm build
      3. Check dist/ directory exists with index.html
    Expected Result: Exit code 0, dist/ directory populated
    Failure Indicators: Build exit non-zero, TS errors in output
    Evidence: .sisyphus/evidence/task-3-build-output.txt

  Scenario: Build fails on TypeScript error
    Tool: Bash (PowerShell)
    Preconditions: Intentionally break a TS type
    Steps:
      1. Edit any .vue or .ts file to introduce a type error
      2. Run pnpm build
      3. Restore the file
    Expected Result: Build fails with clear TS error pointing to file:line
    Failure Indicators: Build succeeds despite type error
    Evidence: .sisyphus/evidence/task-3-ts-error.txt
  ```

  **Commit**: YES（随 Wave 1 组）
  - Message: `chore(verify): frontend production build re-verification`
  - Files: `doc/验收复验报告_*.md`

- [x] 4. **MySQL Flyway 全新库迁移复验**

  **What to do**:
  - 在 Docker MySQL 中创建全新空数据库 `cgc_pms_verify`
  - 配置临时数据库连接指向新库
  - 启动后端，触发 Flyway V1→V40 全量迁移
  - 验证关键表存在：`ct_contract`, `wf_instance`, `wf_idempotency`, `sys_notification`, `pay_invoice`, `sys_user`, `stl_settlement`
  - 抽查索引、唯一约束、权限种子、审批模板种子
  - 确认 `flyway_schema_history` 记录完整（40 条，success 状态）

  **Must NOT do**:
  - 不要在有数据的生产/开发库上操作
  - 不要跳过任何 migration 版本

  **Recommended Agent Profile**:
  - **Category**: `quick` — SQL 执行 + 表结构验证
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T1、T2、T3、T5 并行）
  - **Parallel Group**: Wave 1
  - **Blocks**: T6-T16（确保数据库 schema 正确）
  - **Blocked By**: None（Docker MySQL 已就绪）

  **References**:
  - `database/migration/` — Flyway V1~V40 迁移脚本
  - `backend/src/main/resources/application-dev.yml` — MySQL 连接配置
  - `deploy/.env` — MySQL 账号密码

  **Acceptance Criteria**:
  - [ ] 全新库迁移一次成功，无报错
  - [ ] `flyway_schema_history` 记录 40 条，全部 success
  - [ ] 关键业务表 + 索引 + 约束存在
  - [ ] 种子数据（权限、审批模板）正确

  **QA Scenarios**:
  ```
  Scenario: Happy path - fresh migration succeeds
    Tool: Bash (PowerShell) + Bash (mysql CLI via docker exec)
    Preconditions: Docker MySQL running, create empty database cgc_pms_verify
    Steps:
      1. docker exec cgc-pms-mysql mysql -uroot -p<password> -e "CREATE DATABASE cgc_pms_verify"
      2. Configure application-verify.yml with new DB, run backend with verify profile
      3. Check flyway_schema_history: SELECT COUNT(*) FROM flyway_schema_history WHERE success=1
    Expected Result: 40 rows, all success=1; key tables created
    Failure Indicators: Migration error, missing tables, seed data missing
    Evidence: .sisyphus/evidence/task-4-migration-output.txt

  Scenario: Migration idempotency check
    Tool: Bash (docker exec mysql)
    Preconditions: Migration already run successfully
    Steps:
      1. Run Flyway migration again (no new scripts)
      2. Check flyway_schema_history count unchanged
    Expected Result: No new migrations applied, schema history unchanged
    Failure Indicators: Duplicate migration attempt errors
    Evidence: .sisyphus/evidence/task-4-idempotency.txt
  ```

  **Commit**: YES（随 Wave 1 组）
  - Message: `chore(verify): MySQL Flyway fresh migration re-verification`
  - Files: `doc/验收复验报告_*.md`

- [x] 5. **Playwright E2E 框架搭建**

  **What to do**:
  - 安装 Playwright：`cd frontend-admin; pnpm add -D @playwright/test`
  - 安装 Chromium 浏览器：`npx playwright install chromium`
  - 创建 `frontend-admin/e2e/` 目录
  - 编写 `playwright.config.ts`（baseURL: `http://localhost:5173`，screenshot on failure）
  - 编写第一个冒烟用例 `e2e/login.spec.ts`（打开登录页 → 填账号密码 → 点击登录 → 断言跳转首页）
  - 执行：`npx playwright test`，确认通过

  **Must NOT do**:
  - 不要安装完整 Playwright（只装 chromium，节省空间）
  - 不要改动已有的前端源码

  **Recommended Agent Profile**:
  - **Category**: `quick` — npm 安装 + 配置文件创建
  - **Skills**: `["playwright"]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T1-T4 并行）
  - **Parallel Group**: Wave 1
  - **Blocks**: T16-T18（E2E 脚本编写依赖框架就绪）
  - **Blocked By**: None

  **References**:
  - `frontend-admin/package.json` — 项目名、scripts
  - `frontend-admin/vite.config.ts` — dev server 端口（5173）
  - `README.md` — 默认管理员账号 admin/admin123

  **Acceptance Criteria**:
  - [ ] `npx playwright test` 可执行
  - [ ] 登录冒烟用例通过
  - [ ] 失败时自动截图保存到 `e2e/test-results/`

  **QA Scenarios**:
  ```
  Scenario: Playwright installation and login smoke test
    Tool: Bash (PowerShell)
    Preconditions: frontend-admin/ directory exists, pnpm available
    Steps:
      1. cd frontend-admin
      2. pnpm add -D @playwright/test
      3. npx playwright install chromium
      4. npx playwright test
    Expected Result: All tests pass, no errors
    Failure Indicators: Playwright install fails, test times out, assertion fails
    Evidence: .sisyphus/evidence/task-5-playwright-setup.txt

  Scenario: Login page smoke test with Playwright
    Tool: Playwright (via npx playwright test)
    Preconditions: Backend running on localhost:8080, frontend dev server on localhost:5173
    Steps:
      1. Navigate to http://localhost:5173/login
      2. Fill input[name="username"] with "admin"
      3. Fill input[type="password"] with "admin123"
      4. Click button[type="submit"]
      5. Wait for URL to NOT contain "/login"
    Expected Result: Redirected to dashboard/home page
    Failure Indicators: Login failed, stayed on login page, error message visible
    Evidence: .sisyphus/evidence/task-5-login-smoke.png (screenshot on success)
  ```

  **Commit**: YES
  - Message: `test(e2e): add Playwright framework + login smoke test`
  - Files: `frontend-admin/e2e/`, `frontend-admin/playwright.config.ts`, `frontend-admin/package.json`, `frontend-admin/pnpm-lock.yaml`

### Wave 2 — 业务闭环验收 + 安全验收 + E2E 起步（最大并行）

- [x] 6. **项目→合同闭环验收（含合同变更）**

  **What to do**:
  - 新建项目（含基本信息、工期、预算）
  - 新建合作方（供应商/分包商）
  - 新建合同（采购/分包/服务），维护合同清单、付款条件、附件
  - 提交合同审批，验证 APPROVING 状态不可编辑
  - 完成审批，验证合同状态→PERFORMING、成本锁定生成
  - **合同变更**：创建变更单（调整金额），提交审批，验证 currentAmount 更新 + 成本联动
  - 验证所有数据关联 project_id + contract_id

  **Must NOT do**:
  - 不要跳过合同变更环节
  - 不要手动修改数据库来"修复"状态

  **Recommended Agent Profile**:
  - **Category**: `deep` — 多步骤业务流，需理解合同全生命周期
  - **Skills**: `["playwright"]`（前端操作验证）

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T7-T11 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None directly
  - **Blocked By**: Wave 1（环境复验通过）

  **References**:
  - `backend/contract/` — 合同模块（Controller/Service/Mapper）
  - `backend/workflow/` — 审批引擎
  - `backend/cost/CostGenerationService.java` — 成本生成逻辑
  - `frontend-admin/src/pages/contract/` — 合同前端页面

  **Acceptance Criteria**:
  - [ ] 合同从 DRAFT→APPROVING→PERFORMING 状态流转正确
  - [ ] 审批中合同不可编辑关键字段
  - [ ] 审批通过后成本记录生成（cost_item 存在）
  - [ ] 合同变更后 currentAmount 更新正确
  - [ ] 成本台账可追溯变更来源（source_type = CT_CHANGE）

  **QA Scenarios**:
  ```
  Scenario: Happy path - full contract lifecycle with change
    Tool: Playwright + Bash (curl)
    Preconditions: Wave 1 passed, backend + frontend running
    Steps:
      1. POST /api/projects - create project, capture projectId
      2. POST /api/partners - create partner, capture partnerId
      3. POST /api/contracts - create contract (DRAFT) with items + payment terms
      4. POST /api/contracts/{id}/submit → status = APPROVING
      5. PUT /api/contracts/{id} (edit) → expect 403 or error "审批中不可编辑"
      6. POST /api/workflow/tasks/{id}/approve → contract status = PERFORMING
      7. GET /api/cost/ledger?projectId=X → verify cost_item exists for contract
      8. POST /api/contracts/{id}/changes - create change order
      9. Submit + approve change → verify currentAmount updated
      10. GET /api/cost/ledger?sourceType=CT_CHANGE → verify change cost
    Expected Result: All steps pass, state transitions correct, cost records generated
    Failure Indicators: Any step returns unexpected status, missing cost records
    Evidence: .sisyphus/evidence/task-6-contract-closure.md (screenshots + curl responses)

  Scenario: Contract change rejection and re-submit
    Tool: Bash (curl)
    Preconditions: Contract in PERFORMING status
    Steps:
      1. Create change order, submit for approval
      2. Reject the change approval
      3. Edit the change order, re-submit
      4. Approve the re-submitted change
    Expected Result: Change eventually approved, currentAmount updated correctly, no duplicate costs
    Failure Indicators: Status stuck, duplicate cost records, wrong amount
    Evidence: .sisyphus/evidence/task-6-change-rejection.txt
  ```

  **Commit**: YES（随 Wave 2 组）
  - Message: `docs(acceptance): contract closed-loop acceptance record`
  - Files: `doc/业务主闭环验收报告_*.md`（合同闭环章节）

- [x] 7. **采购→材料闭环验收**

  **What to do**:
  - 基于采购合同创建采购申请，提交审批
  - 审批通过后创建采购订单（关联合同 + 供应商）
  - 完成材料验收（MAT_RECEIPT），提交审批
  - 验收入库，验证库存余额更新
  - 材料出库，验证库存流水（mat_stock_txn）
  - 验证材料成本归集到成本台账
  - 验证未验收付款限制或预警

  **Must NOT do**:
  - 不要跳过审批环节直接操作数据库
  - 不要忽略库存余额校验

  **Recommended Agent Profile**:
  - **Category**: `deep` — 多模块串联：采购→验收→库存→成本
  - **Skills**: `["playwright"]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T6、T8-T11 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None directly
  - **Blocked By**: Wave 1

  **References**:
  - `backend/purchase/` — 采购模块
  - `backend/inventory/` — 库存模块（mat_stock, mat_stock_txn）
  - `backend/cost/CostGenerationService.java` — MAT_RECEIPT 成本策略
  - `frontend-admin/src/pages/inventory/` — 库存前端页面

  **Acceptance Criteria**:
  - [ ] 采购订单可追溯 project_id + partner_id + contract_id
  - [ ] 验收入库生成材料成本（cost_item source_type = MAT_RECEIPT）
  - [ ] 出入库后 mat_stock 余额正确
  - [ ] mat_stock_txn 流水记录完整

  **QA Scenarios**:
  ```
  Scenario: Happy path - procurement to material cost
    Tool: Bash (curl) + Playwright
    Preconditions: Approved purchase contract exists
    Steps:
      1. POST /api/purchase/requests → create purchase request, submit + approve
      2. POST /api/purchase/orders → create order (linked to contract)
      3. POST /api/inventory/receipts → create material receipt, submit + approve
      4. POST /api/inventory/stock/in → inbound, check mat_stock balance
      5. POST /api/inventory/stock/out → outbound, check mat_stock_txn record
      6. GET /api/cost/ledger?sourceType=MAT_RECEIPT → verify cost generated
    Expected Result: Stock balance correct after each step, cost records generated
    Failure Indicators: Stock balance mismatch, missing cost records, broken references
    Evidence: .sisyphus/evidence/task-7-procurement-closure.md

  Scenario: Negative stock prevention
    Tool: Bash (curl)
    Preconditions: Empty warehouse, material X has stock 0
    Steps:
      1. POST /api/inventory/stock/out with quantity 10 for material X
    Expected Result: 400 or business error "库存不足"
    Failure Indicators: Stock goes negative (stock < 0)
    Evidence: .sisyphus/evidence/task-7-negative-stock.txt
  ```

  **Commit**: YES（随 Wave 2 组）
  - Message: `docs(acceptance): procurement & materials closed-loop acceptance record`
  - Files: `doc/业务主闭环验收报告_*.md`

- [x] 8. **分包→计量闭环验收**

  **What to do**:
  - 基于分包合同创建分包任务
  - 创建分包计量单（SUB_MEASURE），提交审批
  - 审批通过后验证分包成本归集（cost_item source_type = SUB_MEASURE）
  - 发起分包付款申请，验证付款依据与计量数据一致
  - 验证超合同、未计量付款规则触发限制或预警

  **Must NOT do**:
  - 不要让付款金额超过计量确认金额

  **Recommended Agent Profile**:
  - **Category**: `deep` — 分包业务流程 + 成本归集
  - **Skills**: `["playwright"]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T6-T7、T9-T11 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None directly
  - **Blocked By**: Wave 1

  **References**:
  - `backend/` sub_task / sub_measure 相关模块
  - `backend/cost/CostGenerationService.java` — SUB_MEASURE 策略
  - `backend/payment/` — 付款申请（关联计量）
  - `frontend-admin/src/pages/` — 分包相关页面

  **Acceptance Criteria**:
  - [ ] 分包任务、计量、付款均可追溯 project_id + contract_id
  - [ ] 分包成本来自计量确认（source_type = SUB_MEASURE）
  - [ ] 超合同、未计量付款触发限制或预警

  **QA Scenarios**:
  ```
  Scenario: Happy path - subcontract to cost
    Tool: Bash (curl)
    Preconditions: Approved subcontract exists
    Steps:
      1. POST /api/sub/tasks → create sub task (linked to contract)
      2. POST /api/sub/measures → create measure, submit + approve
      3. GET /api/cost/ledger?sourceType=SUB_MEASURE → verify cost generated
      4. POST /api/payment/requests → create payment based on measure
      5. Verify payment amount matches measure amount
    Expected Result: Measure → cost → payment chain consistent
    Failure Indicators: Cost missing, payment exceeds measure, broken references
    Evidence: .sisyphus/evidence/task-8-subcontract-closure.md

  Scenario: Payment exceeds measure amount blocked
    Tool: Bash (curl)
    Preconditions: Measure approved for 100,000 CNY
    Steps:
      1. POST /api/payment/requests with amount 150,000 (exceeds measure)
    Expected Result: 400 or business error "付款金额超过计量金额"
    Failure Indicators: Payment created successfully with excess amount
    Evidence: .sisyphus/evidence/task-8-excess-payment.txt
  ```

  **Commit**: YES（随 Wave 2 组）
  - Message: `docs(acceptance): subcontract & measurement closed-loop acceptance record`
  - Files: `doc/业务主闭环验收报告_*.md`

- [x] 9. **付款→发票闭环验收**

  **What to do**:
  - 基于合同 + 验收/计量数据发起付款申请
  - 完成付款审批（PayApplicationWorkflowHandler）
  - 录入实际付款记录（pay_record），验证资金流与成本流分离
  - 创建发票、登记发票（关联 pay_record）、核验发票
  - 验证付款申请、付款记录、发票、合同金额、成本汇总之间关系
  - 验证付款比例、超额付款、质保金等规则

  **Must NOT do**:
  - 不要将 pay_record 的金额误用为成本发生依据
  - 不要跳过发票核验环节

  **Recommended Agent Profile**:
  - **Category**: `deep` — 付款+发票双模块联动
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T6-T8、T10-T11 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None directly
  - **Blocked By**: Wave 1

  **References**:
  - `backend/payment/` — 付款申请 + 回写
  - `backend/invoice/` — 发票管理（创建/登记/核验）
  - `backend/cost/CostGenerationService.java` — 付款不产生成本

  **Acceptance Criteria**:
  - [ ] 付款申请有明确业务依据（验收单或计量单）
  - [ ] pay_record 作为资金流，不被误用为成本依据
  - [ ] 发票登记可关联 pay_record
  - [ ] 付款比例、超额规则可被验证

  **QA Scenarios**:
  ```
  Scenario: Happy path - payment to invoice
    Tool: Bash (curl)
    Preconditions: Material receipt approved, measure approved
    Steps:
      1. POST /api/payment/requests → create payment with basis (receipt/measure IDs)
      2. Submit + approve payment
      3. POST /api/payment/records → record actual payment (amount <= request amount)
      4. POST /api/invoices → create invoice
      5. POST /api/invoices/register → register invoice linking to pay_record
      6. PUT /api/invoices/{id}/verify → verify invoice
    Expected Result: Payment→record→invoice chain consistent, amounts match
    Failure Indicators: Payment without basis, invoice unlinked, amount mismatch
    Evidence: .sisyphus/evidence/task-9-payment-closure.md

  Scenario: Payment exceeds contract balance blocked
    Tool: Bash (curl)
    Preconditions: Contract amount 1,000,000 CNY, already paid 900,000
    Steps:
      1. POST /api/payment/requests with amount 200,000
    Expected Result: 400 or business error "超过合同余额"
    Failure Indicators: Payment created exceeding balance
    Evidence: .sisyphus/evidence/task-9-excess-payment.txt
  ```

  **Commit**: YES（随 Wave 2 组）
  - Message: `docs(acceptance): payment & invoice closed-loop acceptance record`
  - Files: `doc/业务主闭环验收报告_*.md`

- [x] 10. **结算→归档闭环验收**

  **What to do**:
  - 基于已履约合同创建结算单
  - 验证结算单自动汇总合同金额、变更签证、付款记录、成本明细
  - 验证结算单可反查所有来源数据
  - 提交结算审批，审批通过后验证结算锁定
  - 验证合同结算金额回写（contract.settledAmount = settlement.finalAmount）

  **Must NOT do**:
  - 不要在结算单中手工录入孤立数据
  - 不要跳过结算锁定验证

  **Recommended Agent Profile**:
  - **Category**: `deep` — 结算汇总逻辑复杂，需验证来源追溯
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T6-T9、T11 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None directly
  - **Blocked By**: Wave 1

  **References**:
  - `backend/settlement/` — 结算管理
  - `backend/cost/` — 成本汇总（结算数据来源之一）
  - `backend/payment/` — 付款记录（结算数据来源之一）

  **Acceptance Criteria**:
  - [ ] 结算单数据来源可追溯（合同、变更、付款、成本）
  - [ ] 结算金额 = SUM(合同金额 + 变更金额)
  - [ ] 审批通过后结算单不可随意修改
  - [ ] 合同结算金额回写正确

  **QA Scenarios**:
  ```
  Scenario: Happy path - full settlement flow
    Tool: Bash (curl)
    Preconditions: Contract with changes, payments, costs all recorded
    Steps:
      1. POST /api/settlements → create settlement for contract
      2. GET /api/settlements/{id} → verify auto-summarized data
      3. Click through source references → verify each can be traced back
      4. Submit + approve settlement
      5. PUT /api/settlements/{id} (edit after approved) → expect 403 or error
      6. GET /api/contracts/{id} → verify settledAmount matches settlement finalAmount
    Expected Result: Settlement locked after approval, contract amount synced
    Failure Indicators: Settlement editable after approval, amount mismatch, broken traceability
    Evidence: .sisyphus/evidence/task-10-settlement-closure.md

  Scenario: Settlement prevents duplicate
    Tool: Bash (curl)
    Preconditions: Settlement already approved for contract
    Steps:
      1. POST /api/settlements → create second settlement for same contract
    Expected Result: 400 or business error "合同已结算"
    Failure Indicators: Duplicate settlement created
    Evidence: .sisyphus/evidence/task-10-duplicate-settlement.txt
  ```

  **Commit**: YES（随 Wave 2 组）
  - Message: `docs(acceptance): settlement & archive closed-loop acceptance record`
  - Files: `doc/业务主闭环验收报告_*.md`

- [x] 11. **经营分析→预警闭环验收**

  **What to do**:
  - 查看项目驾驶舱，验证合同金额、动态成本、利润测算数据正确
  - 验证付款比例、结算进度与业务数据一致
  - 手动触发预警批量评估（POST /api/alerts/batch-evaluate）
  - 验证合同类、成本类、付款类、结算类预警是否正确生成
  - 验证预警可定位到具体项目/合同/业务单据
  - 抽查预警规则与业务事实的一致性

  **Must NOT do**:
  - 不要只看驾驶舱"有数据"——要校验数据准确性
  - 不要跳过预警与业务数据的交叉比对

  **Recommended Agent Profile**:
  - **Category**: `deep` — 数据分析 + 预警规则验证
  - **Skills**: `["playwright"]`（驾驶舱前端验证）

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T6-T10 并行，但建议在 T6-T9 有一定数据后再执行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None directly
  - **Blocked By**: Wave 1

  **References**:
  - `backend/alert/` — 八类预警规则 + 批处理
  - `backend/cost/` — 动态成本汇总（cost_summary）
  - `frontend-admin/src/pages/dashboard/` — 驾驶舱
  - `frontend-admin/src/pages/alert/` — 预警页面

  **Acceptance Criteria**:
  - [ ] 驾驶舱数据反映项目真实经营状态（与源数据交叉比对一致）
  - [ ] 预警规则与业务事实一致（如超额付款预警能正确触发）
  - [ ] 预警可定位到具体单据（包含 project_id/contract_id 引用）

  **QA Scenarios**:
  ```
  Scenario: Dashboard data accuracy check
    Tool: Playwright + Bash (curl)
    Preconditions: Project has contracts, costs, payments from T6-T10
    Steps:
      1. Open dashboard for project
      2. Note displayed contract total amount
      3. curl GET /api/contracts?projectId=X → sum all contract amounts
      4. Compare: dashboard value == API sum
      5. Repeat for dynamic cost, payment ratio
    Expected Result: Dashboard values match API aggregates within rounding tolerance
    Failure Indicators: Dashboard shows stale/wrong data, mismatched by > 1%
    Evidence: .sisyphus/evidence/task-11-dashboard-accuracy.md

  Scenario: Alert triggers on excess payment
    Tool: Bash (curl)
    Preconditions: Contract with payments close to balance
    Steps:
      1. POST /api/alerts/batch-evaluate
      2. GET /api/alerts → filter by contract ID
      3. Verify payment-related alerts reference the correct contract
    Expected Result: Payment alerts generated with correct business context
    Failure Indicators: No alerts when they should exist, or wrong alert type
    Evidence: .sisyphus/evidence/task-11-alert-validation.txt
  ```

  **Commit**: YES（随 Wave 2 组）
  - Message: `docs(acceptance): analytics & alert closed-loop acceptance record`
  - Files: `doc/业务主闭环验收报告_*.md`

- [x] 12. **审批异常路径验收**

  **What to do**:
  - **驳回→重编→重提**：合同审批驳回后，验证 DRAFT 状态恢复、可编辑、重新提交进入 APPROVING
  - **撤回**：审批中合同撤回，验证状态回退、审批任务取消
  - **转办**：将审批任务转办给他人，验证原审批人失去权限、新审批人可操作
  - **加签**：审批人加签，验证加签人可参与审批
  - **会签/或签**：验证 COUNTERSIGN（全部同意）和 OR_SIGN（任一人同意）逻辑正确

  **Must NOT do**:
  - 不要只测一个模块的异常路径——至少覆盖合同和付款两个模块
  - 不要跳过审批任务清理验证（撤回后不应残留孤儿任务）

  **Recommended Agent Profile**:
  - **Category**: `deep` — 审批引擎异常路径多，需理解状态机
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与其他 Wave 2 任务并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Wave 1

  **References**:
  - `backend/workflow/` — 审批引擎（submit/approve/reject/withdraw/transfer/add-sign）
  - `backend/workflow/WfTask.java` — 任务实体
  - `backend/workflow/WfInstance.java` — 实例状态枚举
  - `backend/contract/ContractWorkflowHandler.java` — 合同审批回调

  **Acceptance Criteria**:
  - [ ] 驳回后状态恢复 DRAFT，可重新编辑提交
  - [ ] 撤回后审批任务清理干净，无孤儿记录
  - [ ] 转办后权限正确转移
  - [ ] 加签人可正常参与审批
  - [ ] 会签/或签逻辑与预期一致

  **QA Scenarios**:
  ```
  Scenario: Reject → re-edit → re-submit → approve
    Tool: Bash (curl)
    Preconditions: Contract submitted for approval
    Steps:
      1. POST /api/workflow/tasks/{id}/reject → contract status returns to DRAFT
      2. PUT /api/contracts/{id} → edit contract (should succeed)
      3. POST /api/contracts/{id}/submit → status = APPROVING
      4. POST /api/workflow/tasks/{id}/approve → status = PERFORMING
    Expected Result: Full reject→edit→resubmit→approve cycle works
    Failure Indicators: Cannot edit after reject, resubmit fails, duplicate tasks
    Evidence: .sisyphus/evidence/task-12-reject-resubmit.txt

  Scenario: Withdraw → verify task cleanup
    Tool: Bash (curl)
    Preconditions: Contract in APPROVING with pending tasks
    Steps:
      1. GET /api/workflow/tasks/todo → note pending task IDs
      2. POST /api/workflow/instances/{id}/withdraw
      3. GET /api/workflow/tasks/todo → verify previous task IDs are gone
      4. GET /api/contracts/{id} → verify status = DRAFT
    Expected Result: Tasks cleaned up, contract back to DRAFT
    Failure Indicators: Orphan tasks remain, wrong status
    Evidence: .sisyphus/evidence/task-12-withdraw.txt

  Scenario: Transfer - new approver can act
    Tool: Bash (curl)
    Preconditions: Task assigned to user A
    Steps:
      1. As user A: POST /api/workflow/tasks/{id}/transfer {newAssigneeId: user B}
      2. As user A: try to approve → expect 403
      3. As user B: POST /api/workflow/tasks/{id}/approve → success
    Expected Result: user A loses access, user B gains access
    Failure Indicators: user A can still approve, user B cannot
    Evidence: .sisyphus/evidence/task-12-transfer.txt

  Scenario: Add-sign - added user can participate
    Tool: Bash (curl)
    Preconditions: Task assigned to user A
    Steps:
      1. As user A: POST /api/workflow/tasks/{id}/add-sign {userId: user B}
      2. As user B: GET /api/workflow/tasks/todo → verify new task appears
      3. As user B: POST /api/workflow/tasks/{id}/approve → success
    Expected Result: user B can see and approve the add-sign task
    Failure Indicators: Add-sign task not created, cannot approve
    Evidence: .sisyphus/evidence/task-12-addsign.txt
  ```

  **Commit**: YES（随 Wave 2 组）
  - Message: `docs(acceptance): approval exception paths acceptance record`
  - Files: `doc/业务主闭环验收报告_*.md`

- [x] 13. **权限矩阵验收**

  **What to do**:
  - 整理角色清单：管理员、项目经理、商务经理、成本经理、合同管理员、材料员、设备管理员、分包管理员、财务人员、资料员、审计人员（11 个角色）
  - 对照 README 中 10 个 Controller 的 `@PreAuthorize` 权限码
  - 按角色逐页验证菜单可见性、按钮权限、接口权限
  - 验证无权限用户调用接口返回 403

  **Must NOT do**:
  - 不要只测前端菜单——必须测后端接口权限
  - 不要用 admin 账号测试（admin 全部能访问，无意义）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 系统化权限矩阵验证
  - **Skills**: `["playwright"]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T14-T15 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Wave 1

  **References**:
  - `backend/*/` — 各模块 Controller 的 @PreAuthorize 注解
  - `frontend-admin/src/router/` — 前端路由权限定义
  - `database/migration/` — V39/V40 权限种子数据

  **Acceptance Criteria**:
  - [ ] 管理员可完整访问所有管理能力
  - [ ] 普通角色仅能访问授权菜单和按钮
  - [ ] 后端接口权限与前端菜单按钮一致
  - [ ] 未授权接口返回 403

  **QA Scenarios**:
  ```
  Scenario: Role-based access control verification
    Tool: Playwright + Bash (curl)
    Preconditions: Multiple test users with different roles created
    Steps:
      1. Login as 材料员 → menu should NOT show 财务管理/结算管理
      2. Login as 材料员 → curl POST /api/settlements → expect 403
      3. Login as 财务人员 → menu should show 付款/发票/结算
      4. Login as 财务人员 → curl POST /api/contracts → expect 403
      5. Login as admin → all menus visible, all APIs accessible
    Expected Result: Each role sees only authorized content
    Failure Indicators: 材料员 can access settlements, 财务人员 can create contracts
    Evidence: .sisyphus/evidence/task-13-permission-matrix.md
  ```

  **Commit**: YES
  - Message: `docs(acceptance): permission matrix acceptance record`
  - Files: `doc/权限矩阵验收表_*.md`

- [x] 14. **多租户数据隔离验收**

  **What to do**:
  - 准备两个租户/项目隔离的数据
  - 使用不同用户访问项目、合同、付款、发票、文件、通知
  - 验证列表查询不返回非授权数据（跨租户）
  - 验证详情查询不能通过 IDOR 访问其他租户数据
  - 验证文件预签名 URL、删除接口受权限控制
  - 验证通知只发送给正确用户

  **Must NOT do**:
  - 不要只测前端——IDOR 攻击必须用 curl 直接调接口测
  - 不要用同一个 tenant 的多个用户测（那测的是角色隔离，不是租户隔离）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 安全测试思维，IDOR 攻击模拟
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T13、T15 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Wave 1

  **References**:
  - `backend/common/UserContext.java` — 租户上下文（getCurrentTenantId）
  - `backend/contract/ContractServiceImpl.java` — LambdaQueryWrapper 追加 tenantId
  - `backend/file/FileController.java` — 文件权限校验

  **Acceptance Criteria**:
  - [ ] 列表查询不返回非授权租户数据
  - [ ] IDOR 攻击（改 ID）返回 403 或 404（不泄露存在性）
  - [ ] 文件预签名 URL、删除接口受权限控制
  - [ ] 通知不跨租户泄露

  **QA Scenarios**:
  ```
  Scenario: Cross-tenant data isolation
    Tool: Bash (curl)
    Preconditions: Tenant A has project-1, Tenant B has project-2
    Steps:
      1. Login as Tenant A user
      2. GET /api/projects → verify only project-1 in list
      3. GET /api/projects/{project-2-id} → expect 403 or 404
      4. GET /api/contracts?projectId={project-2-id} → expect empty or error
    Expected Result: Tenant A cannot access Tenant B data
    Failure Indicators: Cross-tenant data leaked, 200 for other tenant's data
    Evidence: .sisyphus/evidence/task-14-tenant-isolation.txt

  Scenario: File IDOR attack prevention
    Tool: Bash (curl)
    Preconditions: Tenant A uploaded file-A
    Steps:
      1. Login as Tenant B user
      2. GET /api/files/{file-A-id}/url → expect 403
      3. DELETE /api/files/{file-A-id} → expect 403
    Expected Result: 403 for both operations
    Failure Indicators: File URL returned or deleted for wrong tenant
    Evidence: .sisyphus/evidence/task-14-file-idor.txt
  ```

  **Commit**: YES
  - Message: `docs(acceptance): multi-tenant data isolation acceptance record`
  - Files: `doc/多租户数据隔离验收报告_*.md`

- [x] 15. **安全基线复核**

  **What to do**:
  - 复核 JWT/Refresh Token/Redis 黑名单机制
  - 复核退出登录后旧 token 不可用、禁用账号不可刷新 token
  - 复核文件上传扩展名白名单、大小限制、路径注入防护
  - 复核敏感字段脱敏（OperationLog 切面）
  - 复核生产配置不硬编码密钥（`.env` 文件使用）

  **Must NOT do**:
  - 不要修改生产配置来"通过"检查

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 安全审计思维
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T13-T14 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Wave 1

  **References**:
  - `backend/auth/` — JWT + Refresh Token 逻辑
  - `backend/file/FileController.java` — 上传安全
  - `backend/common/OperationLogAspect.java` — 脱敏切面
  - `deploy/.env.example` — 环境变量模板

  **Acceptance Criteria**:
  - [ ] 密码/token/secret 不出现在日志和提交配置中
  - [ ] 上传接口拒绝非白名单文件（.exe, .jsp, .sh）
  - [ ] 退出登录后旧 token 不可继续使用
  - [ ] 禁用账号不能刷新 token

  **QA Scenarios**:
  ```
  Scenario: Logout invalidates access token
    Tool: Bash (curl)
    Preconditions: User logged in, has valid access token
    Steps:
      1. POST /api/auth/logout (with valid token)
      2. GET /api/auth/userinfo (with same token) → expect 401
    Expected Result: Token rejected after logout (Redis blacklist)
    Failure Indicators: Old token still works after logout
    Evidence: .sisyphus/evidence/task-15-logout-blacklist.txt

  Scenario: Disabled account cannot refresh token
    Tool: Bash (curl)
    Preconditions: User account disabled (status=0)
    Steps:
      1. POST /api/auth/refresh {refreshToken: <token_of_disabled_user>} → expect 401
    Expected Result: Refresh rejected for disabled account
    Failure Indicators: Refresh succeeds for disabled user
    Evidence: .sisyphus/evidence/task-15-disabled-refresh.txt

  Scenario: Malicious file upload blocked
    Tool: Bash (curl)
    Preconditions: Valid auth token
    Steps:
      1. curl -F "file=@evil.jsp" POST /api/files/upload → expect 400 or rejection
      2. curl -F "file=@test.exe" POST /api/files/upload → expect 400
    Expected Result: Non-whitelist extensions rejected
    Failure Indicators: .jsp or .exe uploaded successfully
    Evidence: .sisyphus/evidence/task-15-file-upload-security.txt
  ```

  **Commit**: YES
  - Message: `docs(acceptance): security baseline review record`
  - Files: `doc/安全基线复核清单_*.md`

- [x] 16. **E2E 脚本 Batch 1：登录 + 合同 + 审批**

  **What to do**:
  - 基于 T5 搭建的 Playwright 框架，编写 3 个核心场景的 E2E 脚本
  - **登录流程**：登录 → 验证跳转首页 → 验证用户信息显示
  - **合同创建+审批**：登录 → 新建合同（4 步分步表单）→ 提交审批 → 切换审批人账号 → 审批通过
  - **审批待办处理**：审批人登录 → 查看待办列表 → 打开审批详情 → 同意/驳回
  - 每个脚本包含断言（页面元素、API 响应、URL 跳转）

  **Must NOT do**:
  - 不要覆盖全部场景（Batch 2/3 会扩展）
  - 不要硬编码等待时间（使用 waitForSelector/waitForURL）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering` — 前端 E2E 脚本编写
  - **Skills**: `["playwright"]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T13-T15 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: T17-T18（后续 E2E 批次）
  - **Blocked By**: T5（框架搭建）

  **References**:
  - `frontend-admin/e2e/login.spec.ts` — T5 创建的冒烟用例（参考模式）
  - `frontend-admin/src/pages/contract/` — 合同页面组件选择器
  - `frontend-admin/src/pages/approval/` — 审批页面组件选择器

  **Acceptance Criteria**:
  - [ ] 3 个 spec 文件通过 `npx playwright test`
  - [ ] 每个场景有明确的断言（非截图对比）
  - [ ] 失败自动截图

  **QA Scenarios**:
  ```
  Scenario: E2E - full contract creation + approval flow
    Tool: Playwright
    Preconditions: Backend + frontend running
    Steps:
      1. page.goto('/login')
      2. Fill credentials, click login
      3. Assert URL contains '/dashboard'
      4. Navigate to contract list, click "新建合同"
      5. Step 1: Fill basic info (name, type, amount, project, partner)
      6. Step 2: Add contract items (at least 2 items, verify auto-sum)
      7. Step 3: Add payment terms (verify ratio sum = 100%)
      8. Step 4: Submit → assert success toast
      9. Logout, login as approver
      10. Navigate to 我的待办, find the contract approval task
      11. Open approval detail, click "同意"
      12. Assert contract status changed to PERFORMING
    Expected Result: Full flow from creation to approval succeeds
    Failure Indicators: Any step fails, toast shows error
    Evidence: .sisyphus/evidence/task-16-e2e-contract-flow.webm (trace)
  ```

  **Commit**: YES
  - Message: `test(e2e): add login, contract, approval E2E scripts`
  - Files: `frontend-admin/e2e/contract.spec.ts`, `frontend-admin/e2e/approval.spec.ts`

### Wave 3 — 质量补强 + E2E 扩展

- [x] 17. **E2E 脚本 Batch 2：采购 + 库存 + 发票**

  **What to do**:
  - **采购申请→订单**：创建采购申请 → 提交审批 → 审批通过 → 创建采购订单
  - **库存出入库**：材料入库 → 验证库存余额 → 材料出库 → 验证库存流水
  - **发票全流程**：创建发票 → 登记发票（关联付款记录）→ 核验发票
  - 每个场景包含数据断言（金额、数量、状态）

  **Must NOT do**:
  - 不要与 Batch 1 的脚本重复

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering` — 前端 E2E
  - **Skills**: `["playwright"]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T18-T20 并行）
  - **Parallel Group**: Wave 3
  - **Blocks**: None
  - **Blocked By**: T16（Batch 1 完成，框架和模式已建立）

  **References**:
  - `frontend-admin/src/pages/inventory/` — 库存页面
  - `frontend-admin/src/pages/invoice/` — 发票页面
  - `frontend-admin/e2e/contract.spec.ts` — 参考模式

  **Acceptance Criteria**:
  - [ ] 采购+库存+发票 3 个 spec 通过
  - [ ] 数据库断言正确（库存余额、发票状态）
  - [ ] 失败自动截图

  **QA Scenarios**:
  ```
  Scenario: E2E - purchase request to stock in/out
    Tool: Playwright
    Steps:
      1. Login, navigate to 采购申请, create and submit
      2. Login as approver, approve the request
      3. Create purchase order linked to approved request
      4. Navigate to 库存管理, create inbound record
      5. Assert stock balance updated on inventory ledger
      6. Create outbound record
      7. Assert stock balance decreased, transaction log shows the record
    Expected Result: Full purchase→stock flow works end-to-end
    Failure Indicators: Stock balance not updated, wrong quantities
    Evidence: .sisyphus/evidence/task-17-e2e-procurement.webm
  ```

  **Commit**: YES
  - Message: `test(e2e): add procurement, inventory, invoice E2E scripts`
  - Files: `frontend-admin/e2e/procurement.spec.ts`, `frontend-admin/e2e/inventory.spec.ts`, `frontend-admin/e2e/invoice.spec.ts`

- [x] 18. **E2E 脚本 Batch 3：通知 + 结算 + 驾驶舱**

  **What to do**:
  - **通知中心**：验证通知列表 → 未读数 → 标记已读 → SSE 实时推送
  - **结算详情**：打开结算单 → 验证汇总数据 → 提交审批
  - **驾驶舱**：打开项目驾驶舱 → 验证图表渲染 → 验证数据联动

  **Must NOT do**:
  - 不要用固定延迟等 SSE 推送——使用 Playwright 的 waitForResponse

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering` — 前端 E2E
  - **Skills**: `["playwright"]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T17、T19-T20 并行）
  - **Parallel Group**: Wave 3
  - **Blocks**: None
  - **Blocked By**: T16

  **References**:
  - `frontend-admin/src/pages/notification/` — 通知页面
  - `frontend-admin/src/pages/dashboard/` — 驾驶舱
  - `frontend-admin/stores/notification.ts` — SSE 连接

  **Acceptance Criteria**:
  - [ ] 通知+结算+驾驶舱 3 个 spec 通过
  - [ ] SSE 实时推送可验证（不依赖固定延迟）
  - [ ] 驾驶舱图表渲染验证（非空白）

  **QA Scenarios**:
  ```
  Scenario: E2E - notification SSE real-time push
    Tool: Playwright
    Steps:
      1. Login as user A, navigate to notification center, note unread count
      2. Trigger an event that generates notification for user A (via curl)
      3. Assert unread count increments within 10 seconds
      4. Click the new notification, assert marked as read
    Expected Result: SSE push updates UI without page refresh
    Failure Indicators: Notification never appears, count unchanged
    Evidence: .sisyphus/evidence/task-18-e2e-notification.webm

  Scenario: E2E - dashboard chart rendering
    Tool: Playwright
    Steps:
      1. Login, navigate to project dashboard
      2. Wait for ECharts canvas to render (waitForSelector('canvas'))
      3. Assert at least 3 chart containers visible
      4. Click on a chart segment → verify drill-down or detail shown
    Expected Result: Charts render with data, interactions work
    Failure Indicators: Blank chart areas, JS errors in console
    Evidence: .sisyphus/evidence/task-18-e2e-dashboard.png
  ```

  **Commit**: YES
  - Message: `test(e2e): add notification, settlement, dashboard E2E scripts`
  - Files: `frontend-admin/e2e/notification.spec.ts`, `frontend-admin/e2e/settlement.spec.ts`, `frontend-admin/e2e/dashboard.spec.ts`

- [ ] 19. **并发与一致性测试**

  **What to do**:
  - **审批幂等**：同一 idempotencyKey 重复提交 → 验证第二次被拒绝
  - **库存并发出库**：两个请求同时对同一物料出库 → 验证 @Version 乐观锁生效
  - **付款余额校验**：并发付款申请总额超过合同余额 → 验证只通过一个
  - **成本重复生成**：同一合同两次触发成本生成 → 验证 uk_cost_source_item 唯一键防护
  - **结算二次修改**：审批通过后尝试修改结算单 → 验证被拦截

  **Must NOT do**:
  - 不要用 Thread.sleep() 模拟并发——使用真正的并发工具（如 ab、JMeter、或 PowerShell 多线程）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 并发测试需理解锁机制和竞态条件
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T17-T18、T20 并行）
  - **Parallel Group**: Wave 3
  - **Blocks**: None
  - **Blocked By**: Wave 2（需要业务数据）

  **References**:
  - `backend/workflow/WfInstance.java` — @Version 乐观锁
  - `backend/inventory/mat_stock.java` — @Version 乐观锁（库存）
  - `backend/cost/CostGenerationService.java` — 唯一键幂等
  - `backend/payment/PayApplicationWorkflowHandler.java` — 余额校验

  **Acceptance Criteria**:
  - [ ] 幂等、唯一约束、乐观锁、悲观锁均有测试覆盖
  - [ ] 并发失败路径有明确业务异常（非 500 堆栈）
  - [ ] 不产生重复成本、重复付款、负库存

  **QA Scenarios**:
  ```
  Scenario: Concurrent stock outbound with optimistic lock
    Tool: Bash (PowerShell - Start-Job for parallel requests)
    Preconditions: Material X stock = 10
    Steps:
      1. Start-Job: POST /api/inventory/stock/out {materialId: X, quantity: 8}
      2. Start-Job: POST /api/inventory/stock/out {materialId: X, quantity: 8}
      3. Wait-Job for both, collect results
      4. GET /api/inventory/stock/ledger?materialId=X → verify final balance
    Expected Result: One succeeds (stock=2), other fails with optimistic lock error
    Failure Indicators: Both succeed (stock goes to -6), or 500 internal error
    Evidence: .sisyphus/evidence/task-19-concurrent-stock.txt

  Scenario: Duplicate cost generation prevention
    Tool: Bash (curl)
    Preconditions: Contract approved, cost already generated once
    Steps:
      1. Manually trigger cost generation for same contract again
    Expected Result: DuplicateKeyException caught gracefully, no duplicate cost_item
    Failure Indicators: Duplicate cost_item created, or unhandled exception
    Evidence: .sisyphus/evidence/task-19-duplicate-cost.txt

  Scenario: Settlement modification blocked after approval
    Tool: Bash (curl)
    Preconditions: Settlement approved
    Steps:
      1. PUT /api/settlements/{id} {finalAmount: 999999} → expect 403 or error
    Expected Result: Modification rejected
    Failure Indicators: Settlement amount changed after approval
    Evidence: .sisyphus/evidence/task-19-settlement-lock.txt
  ```

  **Commit**: YES
  - Message: `test(concurrency): concurrency & consistency test report`
  - Files: `doc/并发一致性测试报告_*.md`

- [ ] 20. **性能基线测试**

  **What to do**:
  - 准备测试数据：SQL 脚本批量插入（合同 10,000 条、成本明细 100,000 条、通知 50,000 条）
  - 测试接口：合同台账分页、成本台账分页、项目驾驶舱、预警批量评估、消息分页、结算金额计算
  - 记录每个接口的 P50/P95/P99 响应时间
  - 明确测试数据量级和期望基线

  **Must NOT do**:
  - 不要在生产数据上测试
  - 不要省略数据准备——无数据量的性能测试无意义

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 性能测试 + 数据分析
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T17-T19 并行）
  - **Parallel Group**: Wave 3
  - **Blocks**: None
  - **Blocked By**: Wave 2（需要业务数据存在）

  **References**:
  - `backend/` — 各 Controller 的分页查询实现
  - `database/migration/` — 表结构（确认索引）

  **Acceptance Criteria**:

  | 接口 | 数据量 | P95 基线 |
  |------|--------|---------|
  | 合同台账分页 | 10,000 条 | < 500ms |
  | 成本台账分页 | 100,000 条 | < 1s |
  | 项目驾驶舱 | 5 项目×50 合同 | < 2s |
  | 预警批量评估 | 10 项目×100 合同 | < 5s |
  | 消息通知分页 | 50,000 条 | < 300ms |
  | 结算金额计算 | 200 条明细/合同 | < 1s |

  **QA Scenarios**:
  ```
  Scenario: Contract ledger pagination performance
    Tool: Bash (curl + Measure-Command)
    Preconditions: 10,000 contracts inserted
    Steps:
      1. Measure-Command { curl GET /api/contracts?page=1&size=20 }
      2. Repeat 10 times, calculate P50/P95/P99
    Expected Result: P95 < 500ms
    Failure Indicators: P95 > 1s (needs optimization)
    Evidence: .sisyphus/evidence/task-20-performance-baseline.md

  Scenario: Dashboard load time
    Tool: Bash (curl)
    Preconditions: 5 projects, each with 50 contracts + costs + payments
    Steps:
      1. Measure-Command { curl GET /api/dashboard/project/{id} }
      2. Verify response contains all chart data
    Expected Result: P95 < 2s
    Failure Indicators: > 5s or missing data
    Evidence: .sisyphus/evidence/task-20-dashboard-perf.txt
  ```

  **Commit**: YES
  - Message: `test(performance): performance baseline report`
  - Files: `doc/性能基线报告_*.md`

- [ ] 21. **业务方验收签字协调**

  **What to do**:
  - 准备业务验收签字表（按闭环分表：合同、采购、分包、付款、结算、分析）
  - 每份表包含：验收场景、验收步骤、结果栏（通过/不通过/有条件通过）、签字栏、日期
  - 协调业务方（项目经理、财务、采购、仓库管理员等）逐项验收
  - 记录所有不通过项，形成问题跟踪清单
  - 修复后复验，更新签字表

  **Must NOT do**:
  - 不要让技术团队自判"业务通过"
  - 不要跳过任何一个业务角色的签字

  **Recommended Agent Profile**:
  - **Category**: `writing` — 文档模板编写 + 协调跟踪
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T17-T20 并行）
  - **Parallel Group**: Wave 3
  - **Blocks**: Wave 4（业务验收签字是上线前提）
  - **Blocked By**: T6-T12（闭环验收完成，有数据可供业务方查看）

  **References**:
  - `doc/业务主闭环验收报告_*.md` — T6-T12 产出的验收记录
  - `doc/剩余工作计划书_2026-06-12.md` — 角色清单

  **Acceptance Criteria**:
  - [ ] 每个主闭环有业务方签字确认
  - [ ] 不通过项有明确的修复跟踪和复验记录
  - [ ] 所有签字表归档

  **QA Scenarios**:
  ```
  Scenario: Business sign-off template validation
    Tool: Bash (file check)
    Steps:
      1. Verify sign-off sheets exist for all 6 closed loops + contract change + approval exceptions
      2. Verify each sheet has: scenario description, pass/fail/conditional column, signature block, date
    Expected Result: 8 sign-off sheets with proper structure
    Failure Indicators: Missing any loop, no signature blocks
    Evidence: .sisyphus/evidence/task-21-signoff-sheets.md
  ```

  **Commit**: YES
  - Message: `docs(acceptance): business stakeholder sign-off sheets`
  - Files: `doc/业务验收签字表_*.md`

- [ ] 22. **验收报告汇总**

  **What to do**:
  - 汇总 T1-T21 所有产出物
  - 编写验收总结报告，包含：
    - 复验结果（后端测试/前端构建/数据库迁移）
    - 业务闭环验收结果（6+1 条闭环 + 审批异常路径）
    - 安全验收结果（权限/多租户/安全基线）
    - E2E 测试覆盖率
    - 性能基线结论
    - 已知问题清单
    - 上线建议

  **Must NOT do**:
  - 不要遗漏任何不通过项的记录
  - 不要粉饰数据（如实记录）

  **Recommended Agent Profile**:
  - **Category**: `writing` — 报告撰写
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T1-T21 全部完成）
  - **Parallel Group**: Wave 3（最后执行）
  - **Blocks**: Wave 4（上线工程化需基于报告结论）
  - **Blocked By**: T1-T21 全部

  **References**:
  - `doc/` — 所有验收产出物
  - `.sisyphus/evidence/` — 所有 QA 证据

  **Acceptance Criteria**:
  - [ ] 报告包含所有必需章节
  - [ ] 每个闭环有明确的通过/不通过结论
  - [ ] 已知问题清单完整

  **QA Scenarios**:
  ```
  Scenario: Report completeness check
    Tool: Bash (file check)
    Steps:
      1. Check report has: 复验结果 / 业务闭环 / 安全验收 / E2E / 性能 / 问题清单 / 上线建议
      2. Verify each section has concrete data (not "待补充")
    Expected Result: All sections populated with actual results
    Failure Indicators: Any section empty or marked "TBD"
    Evidence: .sisyphus/evidence/task-22-report-completeness.txt
  ```

  **Commit**: YES
  - Message: `docs(acceptance): final acceptance summary report`
  - Files: `doc/验收总结报告_*.md`

### Wave 4 — 生产工程化（最大并行）

- [ ] 23. **后端 Dockerfile + 镜像构建**

  **What to do**:
  - 编写 `backend/Dockerfile`（多阶段构建：Maven 编译 + JRE 运行）
  - 使用 Eclipse Temurin JRE 21 作为运行基础镜像
  - 配置环境变量注入（数据库连接、Redis、MinIO 等通过 `-e` 传入）
  - 构建镜像：`docker build -t cgc-pms-backend:latest ./backend`
  - 验证镜像可启动并连接 Docker MySQL/Redis/MinIO
  - 禁止硬编码任何密钥

  **Must NOT do**:
  - 不要在 Dockerfile 中硬编码密码
  - 不要用 `COPY . .` 拷贝整个项目（用多阶段构建只拷贝 jar）

  **Recommended Agent Profile**:
  - **Category**: `quick` — Dockerfile 编写 + 构建
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T24-T26 并行）
  - **Parallel Group**: Wave 4
  - **Blocks**: T25（docker-compose 整合）
  - **Blocked By**: Wave 3

  **References**:
  - `backend/pom.xml` — 构建配置、JDK 版本
  - `deploy/docker-compose.yml` — 现有服务编排参考
  - `deploy/.env.example` — 环境变量命名规范

  **Acceptance Criteria**:
  - [ ] `docker build` 成功构建镜像
  - [ ] 镜像启动后可连接 MySQL/Redis/MinIO
  - [ ] 健康检查端点可访问
  - [ ] 无硬编码密钥

  **QA Scenarios**:
  ```
  Scenario: Backend Docker image builds and runs
    Tool: Bash (PowerShell)
    Preconditions: backend/ directory with source code
    Steps:
      1. docker build -t cgc-pms-backend:test -f backend/Dockerfile ./backend
      2. docker run --rm -d --name backend-test --network deploy_default -e SPRING_PROFILES_ACTIVE=dev cgc-pms-backend:test
      3. docker logs backend-test --tail 20
      4. curl http://localhost:8080/api/actuator/health → expect 200
    Expected Result: Container starts without error, health check returns 200
    Failure Indicators: Container exits immediately, connection refused, health check fails
    Evidence: .sisyphus/evidence/task-23-backend-docker.txt
  ```

  **Commit**: YES
  - Message: `feat(deploy): add backend Dockerfile`
  - Files: `backend/Dockerfile`

- [ ] 24. **前端 Dockerfile + Nginx 配置**

  **What to do**:
  - 编写 `frontend-admin/Dockerfile`（多阶段构建：Node 编译 + Nginx 托管）
  - 编写 `frontend-admin/nginx.conf`（反向代理 API 到后端、gzip、缓存策略、SPA 路由 fallback）
  - 构建镜像：`docker build -t cgc-pms-frontend:latest ./frontend-admin`
  - 验证 Nginx 正确代理 API 请求到后端

  **Must NOT do**:
  - 不要在 Nginx 中硬编码后端地址（通过环境变量或 docker-compose 网络解析）
  - 不要忘记 SPA 路由 fallback（`try_files $uri /index.html`）

  **Recommended Agent Profile**:
  - **Category**: `quick` — Dockerfile + Nginx 配置
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T23、T25-T26 并行）
  - **Parallel Group**: Wave 4
  - **Blocks**: T25（docker-compose 整合）
  - **Blocked By**: Wave 3

  **References**:
  - `frontend-admin/vite.config.ts` — 构建输出目录、base path
  - `frontend-admin/package.json` — pnpm build 命令
  - `deploy/docker-compose.yml` — 服务网络名

  **Acceptance Criteria**:
  - [ ] `docker build` 成功构建前端镜像
  - [ ] Nginx 正确托管静态文件并提供 API 反向代理
  - [ ] SPA 路由刷新不 404
  - [ ] gzip 压缩生效

  **QA Scenarios**:
  ```
  Scenario: Frontend Docker image builds and serves correctly
    Tool: Bash (PowerShell)
    Preconditions: frontend-admin/ built successfully
    Steps:
      1. docker build -t cgc-pms-frontend:test -f frontend-admin/Dockerfile ./frontend-admin
      2. docker run --rm -d --name frontend-test -p 8081:80 cgc-pms-frontend:test
      3. curl http://localhost:8081/ → expect 200, HTML content
      4. curl http://localhost:8081/login → expect 200 (SPA fallback)
      5. curl http://localhost:8081/api/ → expect proxied to backend
    Expected Result: Frontend served, API proxied, SPA routing works
    Failure Indicators: 404 on SPA routes, API proxy fails, build error
    Evidence: .sisyphus/evidence/task-24-frontend-docker.txt
  ```

  **Commit**: YES
  - Message: `feat(deploy): add frontend Dockerfile + Nginx config`
  - Files: `frontend-admin/Dockerfile`, `frontend-admin/nginx.conf`

- [ ] 25. **集成生产 docker-compose.yml**

  **What to do**:
  - 基于 `deploy/docker-compose.yml` 创建 `deploy/docker-compose.prod.yml`
  - 增加 `backend` 和 `frontend` 服务定义（使用 T23/T24 构建的镜像）
  - 配置服务依赖（backend depends_on mysql/redis/minio）
  - 前端 Nginx 通过 Docker 网络代理到后端（`http://backend:8080`）
  - 配置重启策略、资源限制、健康检查
  - 一键启动全部 5 个服务：`docker compose -f deploy/docker-compose.prod.yml up -d`

  **Must NOT do**:
  - 不要暴露不必要的端口到宿主机（MySQL/Redis 可仅内部网络）
  - 不要忘记添加 depends_on + healthcheck 确保启动顺序

  **Recommended Agent Profile**:
  - **Category**: `quick` — Docker Compose 配置
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T23、T24 镜像就绪）
  - **Parallel Group**: Wave 4
  - **Blocks**: T28（部署手册）、T30（上线检查清单）
  - **Blocked By**: T23、T24

  **References**:
  - `deploy/docker-compose.yml` — 现有基础设施编排
  - `deploy/.env.example` — 环境变量模板
  - T23 `backend/Dockerfile`、T24 `frontend-admin/Dockerfile`

  **Acceptance Criteria**:
  - [ ] `docker compose -f deploy/docker-compose.prod.yml up -d` 全部服务启动
  - [ ] 浏览器访问 `http://localhost` 可打开前端
  - [ ] 前端可正常调用后端 API
  - [ ] 全部 5 个服务 health check 通过

  **QA Scenarios**:
  ```
  Scenario: Full production stack starts and works
    Tool: Bash (PowerShell)
    Preconditions: T23 + T24 Docker images built
    Steps:
      1. docker compose -f deploy/docker-compose.prod.yml up -d
      2. docker compose -f deploy/docker-compose.prod.yml ps → all healthy
      3. curl http://localhost/ → expect frontend HTML
      4. curl http://localhost/api/auth/login → expect JSON response (from backend)
    Expected Result: All 5 services healthy, frontend→backend proxy works
    Failure Indicators: Any service unhealthy, 502 from Nginx, connection refused
    Evidence: .sisyphus/evidence/task-25-prod-stack.txt
  ```

  **Commit**: YES
  - Message: `feat(deploy): add production docker-compose with backend + frontend`
  - Files: `deploy/docker-compose.prod.yml`

- [ ] 26. **SSL/TLS + 健康检查 + 日志轮转 + JVM 调优**

  **What to do**:
  - 在 Nginx 配置中添加 HTTPS 支持（自签名证书用于测试，正式证书说明文档化）
  - 配置 HTTP→HTTPS 强制重定向
  - 暴露 Spring Boot Actuator `/actuator/health`（含 DB/Redis/MinIO 依赖检查）
  - 在 docker-compose.prod.yml 中为后端添加 healthcheck
  - 配置 Logback `SizeAndTimeBasedRollingPolicy`（100MB/文件，保留 30 天）
  - 设置 JVM 参数（`-Xms512m -Xmx1g -XX:+HeapDumpOnOutOfMemoryError`）

  **Must NOT do**:
  - 不要使用生产 CA 证书（本地用自签名即可，文档中说明正式证书获取方式）
  - 不要忘记配置 HSTS 安全头

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 多领域配置（Nginx + Spring Boot + Logback + JVM）
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T23-T24 并行）
  - **Parallel Group**: Wave 4
  - **Blocks**: None
  - **Blocked By**: T23、T24（需要 Dockerfile 已创建）

  **References**:
  - `backend/src/main/resources/application.yml` — 当前配置
  - `backend/src/main/resources/logback-spring.xml` — 日志配置（如有）
  - `frontend-admin/nginx.conf` — T24 创建的 Nginx 配置

  **Acceptance Criteria**:
  - [ ] HTTPS 访问可用（自签名证书警告可接受）
  - [ ] `/actuator/health` 返回 DB/Redis/MinIO 状态
  - [ ] 后端容器有 healthcheck 定义
  - [ ] 日志文件按配置轮转
  - [ ] JVM 参数生效

  **QA Scenarios**:
  ```
  Scenario: HTTPS + health check verification
    Tool: Bash (curl + docker)
    Preconditions: docker-compose.prod.yml running
    Steps:
      1. curl -k https://localhost/ → expect 200 (accept self-signed)
      2. curl http://localhost/ → expect 301 redirect to https
      3. curl http://localhost:8080/actuator/health → expect JSON with db/redis/minio status UP
      4. docker inspect cgc-pms-backend → verify healthcheck configured
    Expected Result: HTTPS works, health check shows dependencies UP
    Failure Indicators: HTTPS fails, health check shows DOWN, no redirect
    Evidence: .sisyphus/evidence/task-26-ssl-health.txt
  ```

  **Commit**: YES
  - Message: `feat(deploy): add SSL/TLS, health check, log rotation, JVM tuning`
  - Files: `frontend-admin/nginx.conf`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/logback-spring.xml`

- [ ] 27. **CI/CD 流水线配置**

  **What to do**:
  - 编写 `.github/workflows/ci.yml`
  - 配置触发条件：push 到 main/develop、PR 到 main
  - Job 1：后端测试（`.\mvnw.cmd clean test`）
  - Job 2：前端类型检查 + 构建（`pnpm build`）
  - Job 3：Flyway 迁移检查（`.\mvnw.cmd flyway:info`）
  - 配置失败阻断合并规则

  **Must NOT do**:
  - 不要在 CI 中硬编码密钥
  - 不要配置自动部署（一期手动部署即可）

  **Recommended Agent Profile**:
  - **Category**: `quick` — GitHub Actions YAML 配置
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T28-T29 并行）
  - **Parallel Group**: Wave 4
  - **Blocks**: None
  - **Blocked By**: Wave 3

  **References**:
  - `backend/pom.xml` — Maven 命令
  - `frontend-admin/package.json` — pnpm 命令
  - `.github/` — 现有 CI 配置（如有）

  **Acceptance Criteria**:
  - [ ] CI 配置文件语法正确
  - [ ] 提交代码后自动触发测试和构建
  - [ ] 测试失败阻断合并
  - [ ] 构建产物可追溯

  **QA Scenarios**:
  ```
  Scenario: CI config syntax validation
    Tool: Bash (PowerShell)
    Steps:
      1. Validate YAML syntax: check .github/workflows/ci.yml is valid YAML
      2. Check all referenced commands exist: mvnw.cmd, pnpm, etc.
    Expected Result: Valid YAML, all commands resolvable
    Failure Indicators: YAML parse error, missing command references
    Evidence: .sisyphus/evidence/task-27-ci-config.md
  ```

  **Commit**: YES
  - Message: `feat(ci): add CI/CD pipeline configuration`
  - Files: `.github/workflows/ci.yml`

- [ ] 28. **备份恢复方案 + 演练**

  **What to do**:
  - 编写 MySQL 备份策略（每日全量 `mysqldump` + binlog 增量）
  - 编写 MinIO 文件备份策略（`mc mirror` 或 `rclone`）
  - 明确 Redis 数据边界（可丢失：缓存、黑名单；不可丢失：——，Redis 不做持久化依赖）
  - 编写恢复步骤文档
  - 执行一次完整恢复演练（备份→删库→恢复→验证数据完整性）
  - 明确 RPO（24h）和 RTO（2h）目标

  **Must NOT do**:
  - 不要在演练中操作生产数据
  - 不要只写文档不做演练

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 运维备份策略 + 实际演练
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T27、T29 并行）
  - **Parallel Group**: Wave 4
  - **Blocks**: None
  - **Blocked By**: Wave 3

  **References**:
  - `deploy/docker-compose.yml` — MySQL/MinIO/Redis 服务定义
  - `deploy/.env` — 数据库账号密码

  **Acceptance Criteria**:
  - [ ] 每日备份策略文档化
  - [ ] 恢复步骤可执行
  - [ ] 恢复演练成功（数据完整性验证通过）
  - [ ] RPO ≤ 24h、RTO ≤ 2h 明确

  **QA Scenarios**:
  ```
  Scenario: MySQL backup & restore drill
    Tool: Bash (PowerShell + docker exec)
    Preconditions: Database has test data from business acceptance
    Steps:
      1. docker exec cgc-pms-mysql mysqldump -uroot -p<pass> cgc_pms > backup_test.sql
      2. docker exec cgc-pms-mysql mysql -uroot -p<pass> -e "DROP DATABASE cgc_pms; CREATE DATABASE cgc_pms"
      3. docker exec -i cgc-pms-mysql mysql -uroot -p<pass> cgc_pms < backup_test.sql
      4. Verify: SELECT COUNT(*) FROM ct_contract → matches original
    Expected Result: Data fully restored, row counts match
    Failure Indicators: Import errors, missing data, row count mismatch
    Evidence: .sisyphus/evidence/task-28-backup-restore.txt
  ```

  **Commit**: YES
  - Message: `docs(ops): backup & restore strategy + drill record`
  - Files: `doc/备份恢复方案_*.md`

- [ ] 29. **监控告警配置**

  **What to do**:
  - 文档化监控项清单（非实现完整监控系统，定义需要监控什么）：
    - 后端应用存活（HTTP health check）
    - MySQL 连接池使用率
    - Redis 可用性
    - MinIO 可用性
    - 接口 5xx 错误率
    - 登录失败次数（可疑行为）
    - 审批失败数
    - 文件上传失败数
    - 预警批处理失败数
  - 建议监控工具（Prometheus + Grafana 或简易方案）
  - 配置关键服务的 Docker healthcheck

  **Must NOT do**:
  - 不要试图搭建完整 Prometheus/Grafana（一期只做清单 + 简易脚本）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` — 运维监控知识
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T27-T28 并行）
  - **Parallel Group**: Wave 4
  - **Blocks**: None
  - **Blocked By**: Wave 3

  **References**:
  - `deploy/docker-compose.yml` — 服务定义
  - `backend/src/main/resources/application.yml` — Actuator 配置
  - `backend/common/` — 全局异常处理（5xx 统计点）

  **Acceptance Criteria**:
  - [ ] 监控项清单完整（至少 9 项）
  - [ ] 每项有明确的告警阈值
  - [ ] 关键服务有 Docker healthcheck

  **QA Scenarios**:
  ```
  Scenario: Health check endpoint verification
    Tool: Bash (curl)
    Steps:
      1. curl http://localhost:8080/actuator/health → verify 200 + JSON with component status
      2. docker ps --filter health=healthy → verify all services healthy
    Expected Result: All health indicators green
    Failure Indicators: Any service "starting" for > 60s, health endpoint 503
    Evidence: .sisyphus/evidence/task-29-monitoring-health.txt
  ```

  **Commit**: YES
  - Message: `docs(ops): monitoring & alerting checklist`
  - Files: `doc/监控告警清单_*.md`

### Wave 5 — 文档 + 二期规划

- [ ] 30. **部署与回滚手册**

  **What to do**:
  - 编写完整部署步骤（环境准备 → 配置 → 构建镜像 → 启动服务 → 验证）
  - 编写回滚步骤（停止服务 → 切换镜像 tag → 重新启动 → 验证）
  - 包含环境变量清单（每个变量的含义、默认值、生产建议值）
  - 包含常见问题排查（数据库连接失败、端口冲突、磁盘空间不足）

  **Must NOT do**:
  - 不要假设读者熟悉 Docker

  **Recommended Agent Profile**:
  - **Category**: `writing` — 技术文档编写
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T31-T33 并行）
  - **Parallel Group**: Wave 5
  - **Blocks**: None
  - **Blocked By**: T25（docker-compose.prod.yml 就绪）

  **References**:
  - `deploy/docker-compose.prod.yml` — T25 创建的生产编排
  - `deploy/.env.example` — 环境变量模板
  - `README.md` — 现有快速启动文档

  **Acceptance Criteria**:
  - [ ] 部署步骤可从头执行到系统可访问
  - [ ] 回滚步骤可执行
  - [ ] 环境变量清单完整

  **QA Scenarios**:
  ```
  Scenario: Deploy manual completeness
    Tool: Bash (file check)
    Steps:
      1. Check manual has: prerequisites, config, build, deploy, verify, rollback sections
      2. Execute deploy steps from scratch → verify system accessible
    Expected Result: All sections present, steps executable
    Failure Indicators: Missing sections, broken commands in manual
    Evidence: .sisyphus/evidence/task-30-deploy-manual.md
  ```

  **Commit**: YES
  - Message: `docs(ops): deployment & rollback manual`
  - Files: `doc/部署与回滚手册_*.md`

- [ ] 31. **二期范围：移动端定义**

  **What to do**:
  - 明确一期不包含移动端上线
  - 定义二期最小闭环（MVP）：登录、待办列表、审批详情、审批操作、通知中心、材料验收拍照上传
  - 定义三期扩展：现场签证、出入库扫码、进度填报、质量安全记录
  - 估算每个阶段的页面数、接口数、工作周期

  **Must NOT do**:
  - 不要动 `mobile/` 目录代码

  **Recommended Agent Profile**:
  - **Category**: `writing` — 规划文档
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T30、T32-T33 并行）
  - **Parallel Group**: Wave 5
  - **Blocks**: None
  - **Blocked By**: None

  **References**:
  - `doc/开发文档_v2.3/01_项目总体方案与业务闭环设计.md` — 移动端定位
  - `mobile/` — 现有 uni-app 预留目录
  - `README.md` — 技术栈说明

  **Acceptance Criteria**:
  - [ ] 移动端一期/二期/三期边界清晰
  - [ ] 二期 MVP 有明确的页面、接口、权限清单

  **QA Scenarios**:
  ```
  Scenario: Mobile scope document completeness
    Tool: Bash (file check)
    Steps:
      1. Verify document has: 一期排除声明, 二期 MVP 清单, 三期扩展清单, 工作量估算
    Expected Result: Clear boundaries, actionable MVP definition
    Failure Indicators: Vague scope, no concrete page/API list
    Evidence: .sisyphus/evidence/task-31-mobile-scope.md
  ```

  **Commit**: YES
  - Message: `docs(planning): phase 2 mobile scope definition`
  - Files: `doc/二期Backlog与范围说明_*.md`

- [ ] 32. **二期范围：财务接口 + 设备租赁/劳务 + BI**

  **What to do**:
  - **财务接口**：定义付款回写接口、发票同步接口、供应商校验接口、对账异常处理
  - **设备租赁**：设备租赁合同引用、进退场记录、台班记录、维保费用、成本归集
  - **劳务管理**：劳务班组、劳务记录、人工成本归集
  - **BI 分析**：合同分析、成本分析、付款分析、结算分析、供应商分析主题定义
  - **审计归档**：资料归档目录、自动归档索引、资料缺失预警

  **Must NOT do**:
  - 不要写具体实现方案——只定义范围和接口契约

  **Recommended Agent Profile**:
  - **Category**: `writing` — 规划文档
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T30-T31、T33 并行）
  - **Parallel Group**: Wave 5
  - **Blocks**: None
  - **Blocked By**: None

  **References**:
  - `doc/开发文档_v2.3/01_项目总体方案与业务闭环设计.md` — 财务接口/设备/劳务/BI 定位
  - `backend/payment/` — 现有付款回写模式
  - `backend/invoice/` — 现有发票模式

  **Acceptance Criteria**:
  - [ ] 每个模块有明确的二期入口标准和交付物定义
  - [ ] 接口契约有请求/响应结构定义
  - [ ] 成本来源可通过 source_type/source_id 反查

  **QA Scenarios**:
  ```
  Scenario: Phase 2 scope document completeness
    Tool: Bash (file check)
    Steps:
      1. Verify document covers: financial interface, equipment rental, labor, BI, audit archive
      2. Each section has: current state, gap, proposed work, acceptance criteria
    Expected Result: All 5 areas covered with actionable backlog items
    Failure Indicators: Missing areas, vague descriptions without concrete deliverables
    Evidence: .sisyphus/evidence/task-32-phase2-scope.md
  ```

  **Commit**: YES
  - Message: `docs(planning): phase 2 financial, equipment, labor, BI scope`
  - Files: `doc/二期Backlog与范围说明_*.md`

- [ ] 33. **上线就绪检查清单**

  **What to do**:
  - 汇总全部 20 条上线门禁（原 13 条 + 补充 7 条）
  - 逐项勾选，记录状态（通过/不通过/不适用）
  - 对不通过项标注责任人和预计修复时间
  - 形成最终上线建议（建议上线 / 有条件上线 / 不建议上线）

  **Must NOT do**:
  - 不要遗漏任何门禁项

  **Recommended Agent Profile**:
  - **Category**: `writing` — 清单整理
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T30-T32 并行）
  - **Parallel Group**: Wave 5
  - **Blocks**: Wave FINAL
  - **Blocked By**: T1-T29（需要所有验收结果）

  **References**:
  - `doc/剩余工作计划书_2026-06-12.md` — 第 12 章原门禁清单
  - `.sisyphus/drafts/剩余工作计划书_补充工作包.md` — 补充 7 条门禁
  - T1-T29 所有验收产出物

  **Acceptance Criteria**:
  - [ ] 20 条门禁全部有明确状态
  - [ ] 不通过项有跟踪计划
  - [ ] 上线建议明确

  **Commit**: YES
  - Message: `docs(ops): production readiness checklist`
  - Files: `doc/上线就绪检查清单_*.md`

<!-- TASK_INSERTION_POINT -->

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, curl endpoint, run command). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in `.sisyphus/evidence/`. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `.\mvnw.cmd test` + `pnpm build`. Review all changed files for: `as any`/`@ts-ignore`, empty catches, console.log in prod, commented-out code, unused imports. Check for AI slop patterns.
  Output: `Build [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [ ] F3. **Real Manual QA** — `unspecified-high` (+ `playwright` skill)
  Start from clean state. Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test cross-task integration. Test edge cases: empty state, invalid input, rapid actions. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (git log/diff). Verify 1:1 — everything in spec was built, nothing beyond spec was built. Check "Must NOT do" compliance. Detect cross-task contamination. Flag unaccounted changes.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **Wave 1**: `chore(verify): clean environment re-verification` - doc/验收复验报告_*.md
- **Wave 2**: `docs(acceptance): business closed-loop acceptance records` - doc/业务主闭环验收报告_*.md
- **Wave 3**: `test(e2e): E2E scripts + concurrency + performance baseline` - frontend-admin/e2e/, doc/*测试报告_*.md
- **Wave 4**: `feat(deploy): production dockerization + CI/CD + monitoring` - Dockerfile, deploy/, .github/
- **Wave 5**: `docs(planning): deployment manual + phase 2 backlog` - doc/

---

## Success Criteria

### Verification Commands
```powershell
# Backend tests (MySQL)
cd backend && .\mvnw.cmd clean test

# Backend tests (H2)
cd backend && .\mvnw.cmd clean test -Dspring.profiles.active=local

# Frontend build
cd frontend-admin && pnpm build

# Docker services
cd deploy && docker compose ps

# E2E tests
cd frontend-admin && npx playwright test

# Production Docker build
docker compose -f deploy\docker-compose.prod.yml build
docker compose -f deploy\docker-compose.prod.yml up -d
```

### Final Checklist
- [ ] All 20 go-live gates satisfied
- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent
- [ ] 后端 162/162 测试通过（MySQL + H2 双环境）
- [ ] 前端 pnpm build 零错误
- [ ] 6+1 条业务闭环 + 审批异常路径验收通过
- [ ] 权限 + 多租户 + 安全基线验收通过
- [ ] E2E 脚本可重复执行
- [ ] Docker 镜像构建和运行成功
- [ ] 备份恢复演练通过
- [ ] 业务方签字确认
- [ ] 二期 Backlog 就绪
