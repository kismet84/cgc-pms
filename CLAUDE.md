# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**语言要求：所有回答必须使用中文。**

## Project Overview

CGC-PMS is a construction general-contracting project management system with a Spring Boot backend and a Vue 3 admin frontend.

- `backend/`: Spring Boot 3.3, Java 21, MyBatis-Plus, Flyway, JWT, Redis, MinIO, MySQL/H2.
- `frontend-admin/`: Vue 3 + TypeScript + Vite, Ant Design Vue, Pinia, VxeTable, ECharts.
- `deploy/`: Docker Compose definitions and environment templates for infrastructure/dev/prod.
- `doc/`: project documentation, audit reports, testing reports, user manuals, and technical specs.
- `docs/agents/`: local multi-agent workflow documentation referenced by `AGENTS.md`.

## Common Commands

### Backend

Run commands from `backend/` unless noted.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Djasypt.encryptor.password=dev-jasypt-key   # H2, no Redis/MinIO required
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Djasypt.encryptor.password=dev-jasypt-key     # MySQL/Redis/MinIO on local ports
./mvnw test -Djasypt.encryptor.password=dev-jasypt-key
./mvnw -Dtest=ClassName test
./mvnw -Dtest=ClassName#methodName test
./mvnw clean package -DskipTests
```

The backend context path is `/api`; Swagger is available at `http://localhost:8080/api/swagger-ui.html` when enabled by the active profile.

### Frontend

Run commands from `frontend-admin/`.

```bash
pnpm install
pnpm dev
pnpm build              # vue-tsc --noEmit + vite build
pnpm type-check
pnpm lint               # runs eslint with --fix
pnpm format
pnpm test:unit
pnpm test:unit -- src/path/to/file.test.ts
pnpm exec playwright test
pnpm exec playwright test e2e/path/to/spec.ts
```

The Vite dev server runs on `http://localhost:5173` and proxies `/api` to `VITE_API_TARGET` or `http://localhost:8080`.

### Infrastructure

Run from the repository root unless noted.

```bash
cd deploy && cp .env.example .env && docker compose up -d
cd deploy && docker compose -f docker-compose.dev.yml up -d
scripts/start-dev.bat
```

`docker-compose.dev.yml` runs MySQL on host port `3307`, Redis on `6379`, MinIO on `9000/9001`, backend on `8080`, and frontend on `5173`.

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
