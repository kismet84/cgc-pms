# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**语言要求：所有回答必须使用中文。**

## 自动经验记录

**重要：每次解决一个错误或问题后，必须自动将经验保存到 `memory/` 目录。**

规则：
- 所有被解决的错误（编译失败、测试失败、运行时异常、配置错误、Flyway 迁移失败等）— 记录根因、修复步骤、教训
- 所有工具调用陷阱（Edit 匹配失败、编码问题、git lock 等）— 记录现象、原因、解决方法
- 每个经验单独一个 `.md` 文件，使用 frontmatter（name/short-kebab-slug + description + metadata type/feedback + tags）
- 保存后更新 `memory/MEMORY.md` 索引（一行链接 + 一句话描述）
- **不需要等用户提醒，遇到就记**

## Project Overview

CGC-PMS is a construction general-contracting project management system with a Spring Boot backend and a Vue 3 admin frontend.

- `backend/`: Spring Boot 3.3, Java 21, MyBatis-Plus, Flyway, JWT, Redis, MinIO, MySQL/H2.
- `frontend-admin/`: Vue 3 + TypeScript + Vite, Ant Design Vue, Pinia, VxeTable, ECharts.
- `deploy/`: Docker Compose definitions and environment templates for infrastructure/dev/prod.
- `docs/`: project documentation — quickstart, architecture, coding standards, API contract, security, and ops. See `docs/README.md` for index.
- `archive/`: historical documents (audit reports, dev plans, test reports, user manuals) — not used in active development. See `archive/README.md`.
- `docs/agents/`: local multi-agent workflow documentation referenced by `AGENTS.md`.

## Common Commands

### Development Environment

一键启动全部服务（推荐），在**仓库根目录**执行：

```bash
scripts/start-dev.bat
```

这会先检查后端 JAR（首次自动构建），然后通过 `docker compose -f docker-compose.dev.yml up -d` 启动全部 5 个容器：MySQL、Redis、MinIO、Backend、Frontend。所有服务在 Docker 内运行，宿主机无需安装 Java、Node 或 pnpm。

启动后访问：
- 前端：`http://localhost:5173`
- 后端 API：`http://localhost:8080/api`
- Swagger UI：`http://localhost:8080/api/swagger-ui.html`
- MinIO 控制台：`http://localhost:9001`

常用 Docker 命令（在 `deploy/` 下执行）：

```bash
docker compose -f docker-compose.dev.yml logs -f    # 查看所有日志
docker compose -f docker-compose.dev.yml restart      # 重启全部服务
docker compose -f docker-compose.dev.yml down         # 停止并移除容器
docker compose -f docker-compose.dev.yml up -d --build  # 重新构建并启动
```

### Backend

构建和测试命令在 `backend/` 下执行（这些在**宿主机**运行，不需要 Docker）：

```bash
./mvnw clean package -DskipTests                                    # 构建 JAR（首次或代码变更后）
./mvnw test -Djasypt.encryptor.password=dev-jasypt-key               # 运行全部测试
./mvnw -Dtest=ClassName test                                        # 运行单个测试类
./mvnw -Dtest=ClassName#methodName test                             # 运行单个测试方法
```

> 代码变更后，先 `./mvnw clean package -DskipTests`，再 `docker compose -f docker-compose.dev.yml restart backend` 即可生效。
> `spring-boot:run` 已被 Docker Compose 替代，无需在宿主机运行。

### Frontend

检查、测试、格式化命令在 `frontend-admin/` 下执行（这些在**宿主机**运行，不需要 Docker）：

```bash
pnpm install               # 首次安装依赖（Docker 容器会自动执行）
pnpm build                 # vue-tsc --noEmit + vite build
pnpm type-check            # TypeScript 类型检查
pnpm lint                  # ESLint 修复
pnpm format                # Prettier 格式化
pnpm test:unit             # 运行全部单元测试
pnpm test:unit -- src/path/to/file.test.ts  # 运行单个测试
pnpm exec playwright test                   # E2E 测试
pnpm exec playwright test e2e/path/to/spec.ts
```

> `pnpm dev` 已被 Docker Compose 替代（Vite dev server + HMR 在 Docker 容器内运行）。如需在宿主机运行前端调试，仍需 `pnpm dev`，但通常不需要。

### Infrastructure

```bash
cd deploy && docker compose -f docker-compose.dev.yml up -d     # 启动全部服务
cd deploy && docker compose -f docker-compose.dev.yml down      # 停止全部服务
cd deploy && docker compose -f docker-compose.dev.yml restart frontend  # 仅重启前端
```

`docker-compose.dev.yml` 端口映射：MySQL `3307`、Redis `6379`、MinIO `9000/9001`、Backend `8080`、Frontend `5173`。

### Docker 故障恢复

如果 Docker Desktop 无法正常启动（例如 `docker ps` 报错、容器启动失败），使用 WSL2 + Docker 完全重启脚本：

```bash
scripts/restart-docker.bat    # Windows 直接双击
scripts/restart-docker.sh     # Git Bash
```

此脚本会依次：关闭 Docker Desktop → 终止所有 WSL 发行版 → 等待 VM 释放 → 重新启动 Docker Desktop → 等待引擎就绪。重启完成后运行 `scripts/start-dev.bat` 即可恢复开发环境。

## Backend Architecture

- Entry point: `backend/src/main/java/com/cgcpms/CgcPmsApplication.java` enables scheduling, AspectJ proxies, and `@MapperScan("com.cgcpms.**.mapper")`.
- API modules are organized by business domain under `com.cgcpms`: `auth`, `system`, `project`, `partner`, `contract`, `cost`, `payment`, `purchase`, `receipt`, `subcontract`, `settlement`, `variation`, `inventory`, `invoice`, `workflow`, `dashboard`, `alert`, `notification`, `file`, `org`, and `material`.
- Typical backend layering is `controller` → `service` → `mapper` plus `entity`/`vo`/`dto` types. MyBatis XML mapper files live under `backend/src/main/resources/mapper/**`.
- Global response/error/pagination/audit/rate-limit infrastructure lives in `common`; JWT/CORS/security support lives in `auth/config`, `auth/filter`, and `auth/service`.
- Workflow behavior is centralized in `workflow` and extended by domain handlers implementing `WorkflowBusinessHandler`, such as contract, payment, receipt, variation, settlement, and cost-target handlers.
- Cost generation is strategy-based via `CostGenerationStrategy` implementations for contracts, receipts, subcontract measures, variation orders, and contract changes.
- Scheduled jobs are enabled globally; alert evaluation and cost summary refresh use `@Scheduled`.

## Database and Profiles

- `application.yml` defaults to the `dev` profile and sets `server.servlet.context-path: /api`.
- `application-dev.yml` uses MySQL on `localhost:3307`, Redis on `localhost:6379`, MinIO on `localhost:9000`, and Flyway migrations from `db/migration`.
- `application-local.yml` uses an in-memory H2 database, disables Redis auto-configuration, disables MinIO, and runs H2-compatible Flyway migrations from `db/migration-h2`.
- `application-test.yml` targets a local MySQL test database `cgc_pms_test` and Redis database `1`.
- When changing schema, add a new versioned migration in both `db/migration` and `db/migration-h2` when tests/local H2 need the same structure. Do not edit already-applied Flyway migrations.

## Frontend Architecture

- Entry point: `frontend-admin/src/main.ts`; root component: `frontend-admin/src/App.vue`.
- Routes live in `frontend-admin/src/router`; page views live under `frontend-admin/src/pages` by business module.
- API clients live in `frontend-admin/src/api/modules`; shared Axios setup is `frontend-admin/src/api/request.ts` with base URL `VITE_API_BASE_URL ?? '/api'`.
- Pinia stores live in `frontend-admin/src/stores`; shared DTO/type definitions live in `frontend-admin/src/types`.
- Shared UI components live in `frontend-admin/src/components`; layout shell and navigation components live in `frontend-admin/src/layouts`.
- Vite config defines `@` as `frontend-admin/src`, auto-imports Ant Design Vue components, and manually chunks Ant Design Vue, ECharts, VxeTable, Vue/Pinia/router vendors.
- Unit tests use Vitest + jsdom and are colocated under `__tests__`; Playwright E2E tests live in `frontend-admin/e2e`.

## Local Agent Workflow

This repository also has `AGENTS.md` with local multi-agent coordination rules. For coordinated agent work, follow that file and read the documents it lists under `docs/agents/` before dispatching or coordinating child agents.

## 避坑指南 (Pitfall Avoidance)

### 后端测试无法执行 — 根因与正确执行方式

**症状**: `./mvnw test` 在 Git Bash 中启动后进程立即消失，无输出、无错误信息、无 surefire 报告生成。所有测试类显示为"0 tests run"。

**根因**: Maven Surefire 插件在 fork 模式下通过 JVM 的 `java.io.tmpdir` 创建临时文件（surefirebooter jar + communication pipe）。Windows 11 + Git Bash (MSYS2) 环境下，Java 21 读取的 `TMPDIR`/`TEMP` 环境变量可能指向 MSYS 映射的路径（如 `/tmp`），导致 JVM 子进程无法正确写入临时文件、surefire 通信通道静默破裂，Maven 收到空结果后报告 BUILD SUCCESS（假绿）。

**已验证的修复**:

```bash
# ✅ 正确 — 显式设置 JAVA_HOME + 使用 bash 执行 mvnw
export JAVA_HOME="D:\projects-test\jdk-21\jdk-21.0.11+10"
cd D:/projects-test/cgc-pms/backend
bash ./mvnw test -Djasypt.encryptor.password=dev-jasypt-key -Dtest="com.cgcpms.xxx.XxxTest"
```

**关键点**:
1. **必须用 `bash ./mvnw`** 而不是直接 `./mvnw`。直接 `./mvnw` 在 PowerShell 中启动 Maven Wrapper 时，JAVA_HOME 传递可能失败，Maven 使用错误的 JVM 版本
2. **必须显式 `export JAVA_HOME`**，指向 JDK 21 路径。项目中有多个 JDK 版本共存，不指定就会用错
3. **Jasypt 密钥必须传**: `-Djasypt.encryptor.password=dev-jasypt-key`
4. **分批执行，不要一次跑全量**: 54 个测试类全量跑耗时 5+ 分钟且有部分类在 H2 local profile 下失败。分批跑可以精准定位失败根因

**正确做法**（每次执行 Maven 命令前）:
```bash
export JAVA_HOME="D:\projects-test\jdk-21\jdk-21.0.11+10"
cd D:/projects-test/cgc-pms/backend
bash ./mvnw <goal> -Djasypt.encryptor.password=dev-jasypt-key [额外参数]
```

### Node 无法识别 MSYS 路径

在 Git Bash (MSYS) 环境下，Node 和 npm/pnpm 等工具**不认识** `/c/Users/...` 或 `/d/projects/...` 这类 Unix 风格的路径。传入路径时必须使用 Windows 格式：

```bash
# ❌ 错误 — Node 报 "no such file or directory"
node /d/projects-test/cgc-pms/some-script.js

# ✅ 正确 — 使用 Windows 路径
node D:\\projects-test\\cgc-pms\\some-script.js
# 或者
node D:/projects-test/cgc-pms/some-script.js
```

**适用场景**：
- `node <script>` 或 `npm <command>` 的参数中引用文件路径
- `pnpm <command>` 的参数中引用文件路径
- 任何传给 Node.js 进程的路径参数

**关键规则**：当命令由 Node 进程解析时（而非 bash 内置命令），MSYS 不会自动转换路径，必须手动使用 Windows 格式。

### Flyway H2 迁移与匿名唯一约束 — 本次任务经验

**症状**：本地 `@ActiveProfiles("local")` 启动时，H2 上的软删唯一约束修复看似已经加了 `db/migration-h2` / Java migration，但测试仍然报 `CONSTRAINT_INDEX_*` / `DuplicateKeyException`，软删后无法重建同编码记录。

**根因 1**：H2 对 `CREATE TABLE ... UNIQUE(...)` 生成的是匿名唯一约束/索引，单纯按固定索引名 `DROP INDEX` 很容易失效；应先从 `INFORMATION_SCHEMA.TABLE_CONSTRAINTS` 和 `INFORMATION_SCHEMA.INDEXES` 两侧收集，再 `ALTER TABLE ... DROP CONSTRAINT` / `DROP INDEX` 兜底重建。

**根因 2**：`application-local.yml` 的 Flyway Java migration 扫描路径必须写成 classpath 可发现的实际路径（例如 `classpath:com/cgcpms/common/migration`），否则 H2 里新增的 Java migration 不会执行，SQL 迁移虽然存在但约束不会真正被重建。

**根因 3**：逻辑删除实体与软删唯一约束必须成对修复；只改实体、只改 SQL、或只改测试都不够。像 `pay_invoice` 这类实体如果覆盖了 `BaseEntity.deletedFlag`，就会在代码层与数据库层制造不一致，最终导致删除后重建仍失败。

**How to apply**：
- 新增软删唯一约束时，优先复用 `com.cgcpms.common.migration.H2SoftDeleteUniqueMigration` 这类 Java 迁移，确保 H2 匿名约束能被真实重建。
- 写 H2 回归测试时，不要只看迁移文件是否存在；还要跑一个能在 H2 内存库里插入、软删、再重建的实测用例。
- 每次改本地 profile 的 Flyway 配置后，先用最小测试类确认 Java migration 被扫描，再跑真正的软删行为回归。

**Related**: [[H2 软删迁移]]、[[Flyway 规则]]

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
