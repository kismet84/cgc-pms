# Fix 3 Console Errors in CGC-PMS

## TL;DR

> **Quick Summary**: 修复 Vue 3 前端 1 个 ant-design-vue 废弃 API 警告 + Spring Boot 后端 2 个 API 错误（500 / 403），共涉及 4 个文件的改动，同时补齐前后端自动化测试。
>
> **Deliverables**:
> - `NotificationBell.vue` — `@visible-change` → `@open-change`（1 行）
> - `SecurityConfig.java` + `JwtAuthenticationFilter.java` — SSE 端点加入白名单（2 行 × 2 文件）
> - `NotificationService.java` + `NotificationController.java` — 添加诊断日志定位 500 根因
> - 前端 Vitest 单元测试 + 后端 JUnit 集成测试
>
> **Estimated Effort**: Medium（~8 个任务，3 个并行 wave）
> **Parallel Execution**: YES — 3 waves
> **Critical Path**: Task 1 → Task 4 → Task 5 → Task 7

---

## Context

### Original Request
浏览器控制台报告 3 个错误：
1. `[ant-design-vue: Tooltip] onVisibleChange is deprecated, please use onOpenChange instead`
2. `GET /api/notifications/unread-count 500 (Internal Server Error)`
3. `GET /api/notifications/stream 403 (Forbidden)`

### Interview Summary
**Key Discussions**:
- **修复范围**: 三个问题一起修，一个 plan 全部覆盖
- **测试策略**: 前后端都要写自动化测试（前端装 Vitest，后端补 JUnit）
- **用户状态**: 已登录状态看到 403，排除"未登录"可能
- **技术栈确认**: 前端 Vue 3.5 + ant-design-vue 4.2.6；后端 Java 21 + Spring Boot 3.3 + Spring Security 6.3

**Research Findings**:
- **`onVisibleChange`**: 全项目仅 1 处使用（`NotificationBell.vue:170`），在 `<a-popover>` 上（popover 继承 tooltip 的废弃 props）
- **500 错误**: Controller/Service/Entity/Flyway V37 代码审查均无问题，根因大概率是数据库层面（表不存在/连接失败）
- **403 根因确认**: Spring Security 6.x `FilterChainProxy` 在 ASYNC dispatch 时重新执行安全链，但 `JwtAuthenticationFilter`（继承 `OncePerRequestFilter`）默认跳过异步分发 → `AuthorizationFilter` 看到空 `SecurityContext` → 403

### Metis Review
**Identified Gaps** (addressed):
- **403 根因更精确**: 不是 general "async boundary"，而是 `OncePerRequestFilter.shouldNotFilterAsyncDispatch()` → `true` + `FilterChainProxy` 异步重放 → 空上下文
- **500 不能盲修**: 必须先加诊断日志，定位具体异常后再写修复
- **缺失测试**: 没有通过完整 Filter 链测试 SSE 端点的集成测试（现有测试绕过 Controller）
- **Component 纠正**: 是 `<a-popover>` 不是 `<a-tooltip>`，但修复方式相同

---

## Work Objectives

### Core Objective
消除 3 个浏览器控制台错误，恢复通知功能的正常运行（未读计数 API + SSE 实时推送）。

### Concrete Deliverables
- `frontend-admin/src/components/NotificationBell.vue` — 废弃 API 修复
- `backend/.../auth/config/SecurityConfig.java` — SSE 白名单
- `backend/.../auth/filter/JwtAuthenticationFilter.java` — SSE 跳过路径
- `backend/.../notification/controller/NotificationController.java` — 诊断日志
- `backend/.../notification/service/NotificationService.java` — 诊断日志
- `frontend-admin/src/components/__tests__/NotificationBell.test.ts` — 新建
- `backend/src/test/.../notification/NotificationControllerIntegrationTest.java` — 新建
- `frontend-admin/vitest.config.ts` — 新建

### Definition of Done
- [ ] 浏览器控制台零错误（刷新页面后无 warning + 无 500 + 无 403）
- [ ] `GET /api/notifications/unread-count` → 200 `{"count": N}`
- [ ] `GET /api/notifications/stream` → 200 SSE 连接成功（`connected` 事件）
- [ ] `pnpm test:unit` 全部通过
- [ ] `mvn test -Dtest=NotificationControllerIntegrationTest` 通过

### Must Have
- `@visible-change` → `@open-change` 修复
- SSE `/stream` 返回 200 而非 403
- `/unread-count` 返回 200（通过诊断定位后修复）
- 至少 1 个前端单元测试 + 1 个后端集成测试

### Must NOT Have (Guardrails)
- **禁止** 在未看到异常栈的情况下盲修 500
- **禁止** 全局开启 `@EnableAsync`（过度工程）
- **禁止** 重构 `NotificationBell.vue` 的结构/样式/其他 handler
- **禁止** 升级 ant-design-vue 版本（当前 4.2.6 已支持 `@open-change`）
- **禁止** 将 `EventSource` 改为 axios-based SSE
- **禁止** 添加 SSE 重连逻辑（功能需求，非 bug 修复）

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: 前端无（需安装 Vitest）；后端有（JUnit 5 + Spring Boot Test）
- **Automated tests**: YES（前后端都写）
- **Framework**: 前端 Vitest + @vue/test-utils + jsdom；后端 JUnit 5 + MockMvc
- **TDD**: 否（先诊断，再修复，再补测试）

### QA Policy
- **前端/UI**: Playwright 验证浏览器无 console 错误 + 通知铃铛功能正常
- **API/Backend**: curl 验证 `/unread-count` 返回 200 + `/stream` SSE 连接成功
- **Library/Module**: Vitest 验证组件行为 + JUnit 验证 Controller 集成

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately — 基础设施 + 简单修复):
├── Task 1: Vitest 安装 + 配置 [quick]
├── Task 2: 修复 @visible-change → @open-change [quick]
└── Task 3: 后端添加诊断日志（500 问题） [quick]

Wave 2 (After Wave 1 — 核心修复 + 测试, MAX PARALLEL):
├── Task 4: 修复 403 — SSE 端点加入白名单 [quick]
├── Task 5: 前端 NotificationBell 单元测试 [visual-engineering]
└── Task 6: 后端 SSE 集成测试 [unspecified-high]

Wave 3 (After Wave 2 — 500 根因修复):
├── Task 7: 诊断并修复 500 根因 [unspecified-high]
└── Task 8: 后端 unread-count 单元测试 [unspecified-high]

Wave FINAL (After ALL tasks):
├── Task F1: Plan Compliance Audit (oracle)
├── Task F2: Code Quality Review (unspecified-high)
├── Task F3: Real Manual QA (unspecified-high + playwright)
└── Task F4: Scope Fidelity Check (deep)

Critical Path: Task 1 → Task 4 → Task 5 → Task 7
Parallel Speedup: ~50% faster than sequential
Max Concurrent: 3 (Wave 2)
```

### Dependency Matrix
- **1**: — — 2, 4, 5, 1
- **2**: — — 5, 1
- **3**: — — 7, 1
- **4**: 1 — 5, 6, 2
- **5**: 1, 2, 4 — — 2
- **6**: 4 — — 2
- **7**: 3 — 8, 2
- **8**: 7 — — 3

### Agent Dispatch Summary
- **Wave 1**: 3 — T1 → `quick`, T2 → `quick`, T3 → `quick`
- **Wave 2**: 3 — T4 → `quick`, T5 → `visual-engineering`, T6 → `unspecified-high`
- **Wave 3**: 2 — T7 → `unspecified-high`, T8 → `unspecified-high`
- **FINAL**: 4 — F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

- [ ] 1. **Vitest 安装 + 配置**

  **What to do**:
  - 在 `frontend-admin/` 目录安装依赖：`pnpm add -D vitest @vue/test-utils jsdom`
  - 新建 `frontend-admin/vitest.config.ts`，内容参照 `vite.config.ts` 的别名和插件配置
  - 在 `frontend-admin/package.json` 的 `scripts` 中添加 `"test:unit": "vitest run"`
  - 创建 `frontend-admin/src/components/__tests__/` 目录
  - 写一个简单的 sanity test 验证 Vitest 能正常运行

  **Must NOT do**:
  - 不要安装 `happy-dom`（用 `jsdom`）
  - 不要修改 `vite.config.ts`
  - 不要改 `tsconfig.json`

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 标准工具链安装 + 配置文件创建，任务简单直接
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与 Task 2, 3 并行）
  - **Blocks**: Task 5
  - **Blocked By**: None

  **References**:
  - `frontend-admin/vite.config.ts` — Vite 别名 `@/` → `src/`、Vue 插件配置——vitest.config.ts 需对齐
  - `frontend-admin/tsconfig.app.json` — TypeScript 配置基准
  - `frontend-admin/package.json` — 确认已有 `@vitejs/plugin-vue` 版本（6.0.7），Vitest 可直接复用

  **Acceptance Criteria**:
  - [ ] `pnpm test:unit` 执行成功（至少 1 个 sanity test 通过）
  - [ ] `vitest.config.ts` 文件存在且配置正确

  **QA Scenarios**:
  ```
  Scenario: Vitest 安装后可运行测试
    Tool: Bash
    Preconditions: pnpm 已安装
    Steps:
      1. cd frontend-admin && pnpm add -D vitest @vue/test-utils jsdom
      2. 创建 vitest.config.ts 和简单测试文件
      3. pnpm test:unit
    Expected Result: 测试通过，输出 "Tests 1 passed"
    Failure Indicators: 安装失败、配置错误、测试运行报错
    Evidence: .sisyphus/evidence/task-1-vitest-install.txt
  ```

  **Commit**: YES（独立提交）
  - Message: `chore(frontend): install vitest + test infrastructure`
  - Files: `frontend-admin/package.json`, `pnpm-lock.yaml`, `vitest.config.ts`, `src/components/__tests__/sanity.test.ts`

- [ ] 2. **修复 `@visible-change` → `@open-change`**

  **What to do**:
  - 在 `NotificationBell.vue` 第 170 行，将 `@visible-change="handlePopoverChange"` 改为 `@open-change="handlePopoverChange"`
  - 不修改 `handlePopoverChange` 函数（签名 `(visible: boolean) => void` 与 `(open: boolean) => void` 兼容）
  - 不修改其他任何代码

  **Must NOT do**:
  - 不要改变 `<a-popover>` 的其他属性
  - 不要修改 `handlePopoverChange` 函数体
  - 不要同时修改其他文件

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件单行修改，零风险
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与 Task 1, 3 并行）
  - **Blocks**: Task 5
  - **Blocked By**: None

  **References**:
  - `frontend-admin/src/components/NotificationBell.vue:170` — 当前代码：`@visible-change="handlePopoverChange"`
  - `frontend-admin/src/components/NotificationBell.vue:143` — handler 定义：`function handlePopoverChange(visible: boolean)`
  - ant-design-vue 4.x 源码中 `abstractTooltipProps.js` — `onVisibleChange` → `onOpenChange` 的迁移指南

  **Acceptance Criteria**:
  - [ ] `NotificationBell.vue:170` 已改为 `@open-change`
  - [ ] 浏览器控制台不再出现 `onVisibleChange is deprecated` 警告

  **QA Scenarios**:
  ```
  Scenario: 点击通知铃铛弹出 popover 无废弃警告
    Tool: Playwright
    Preconditions: 开发服务器运行中，用户已登录
    Steps:
      1. 打开 http://localhost:5173
      2. 打开浏览器控制台（监听 warning）
      3. 点击通知铃铛图标（.nb-trigger）
      4. 检查控制台有无 "onVisibleChange is deprecated" 警告
    Expected Result: popover 正常打开，控制台无 ant-design-vue Tooltip 废弃警告
    Failure Indicators: 控制台仍有 "onVisibleChange is deprecated" 警告
    Evidence: .sisyphus/evidence/task-2-no-deprecation-warning.png（截图）
  ```

  **Commit**: YES（独立提交）
  - Message: `fix(frontend): replace deprecated onVisibleChange with onOpenChange in NotificationBell`
  - Files: `frontend-admin/src/components/NotificationBell.vue`

- [ ] 3. **后端添加 500 诊断日志**

  **What to do**:
  - 在 `NotificationController.unreadCount()` 方法中添加 try-catch，捕获所有异常并打印完整堆栈
  - 在 `NotificationService.getUnreadCount()` 方法中添加 debug 日志（打印 userId、tenantId 参数）
  - 日志格式：`log.error("unreadCount failed: userId={}, tenantId={}", userId, tenantId, e)`

  **Must NOT do**:
  - 不要修改业务逻辑
  - 不要吞掉异常（catch 后必须重新 throw）
  - 不要用 `System.out.println`（用 `log.error`）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 标准诊断日志添加，简单代码改动
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与 Task 1, 2 并行）
  - **Blocks**: Task 7
  - **Blocked By**: None

  **References**:
  - `backend/.../notification/controller/NotificationController.java:54-61` — `unreadCount()` 方法
  - `backend/.../notification/service/NotificationService.java:99-105` — `getUnreadCount()` 方法
  - `backend/.../notification/service/NotificationService.java` — 已有 `@Slf4j` 注解，日志器 `log` 可直接使用

  **Acceptance Criteria**:
  - [ ] `unreadCount()` 异常时后端控制台打印完整堆栈
  - [ ] `getUnreadCount()` 调用时打印 userId + tenantId 参数

  **QA Scenarios**:
  ```
  Scenario: 触发 500 错误后后端打印异常日志
    Tool: Bash (curl)
    Preconditions: 后端正在运行，数据库可能不可用
    Steps:
      1. curl -v http://localhost:8080/api/notifications/unread-count（带有效 JWT Cookie）
      2. 观察后端控制台输出
    Expected Result: 后端打印包含异常类名 + 堆栈的错误日志
    Failure Indicators: 500 出现但无日志输出
    Evidence: .sisyphus/evidence/task-3-error-log.txt
  ```

  **Commit**: YES（与 Task 7 合并提交，或在 Task 7 中一起提交）
  - Message: `debug(backend): add diagnostic logging for unread-count 500`
  - Files: `backend/.../notification/controller/NotificationController.java`, `backend/.../notification/service/NotificationService.java`

- [ ] 4. **修复 403 — SSE `/stream` 端点加入白名单**

  **What to do**:
  - 在 `SecurityConfig.java` 的 `WHITELIST` 数组中添加 `"/notifications/stream"`
  - 在 `JwtAuthenticationFilter.java` 的 `SKIP_PATHS` 列表中同步添加 `"/notifications/stream"`
  - **Why this approach**: Spring Security 6.x `FilterChainProxy` 在 SSE 异步分发时重放安全链，`OncePerRequestFilter` 默认跳过异步分发导致 `SecurityContext` 为空。将 SSE 端点加入白名单绕过 HTTP 层授权，实际认证在 `NotificationController.stream()` 中通过 `UserContext` 完成（JWT filter 在初始同步请求时已设置 UserContext）
  - SSE 端点没有 `@PreAuthorize` 注解（本身就是 `anyRequest().authenticated()` 级别），加白名单不降低安全性

  **Must NOT do**:
  - 不要全局修改 `SecurityContextHolder` 策略（`MODE_INHERITABLETHREADLOCAL`）
  - 不要添加 `@EnableAsync`
  - 不要用方案 2（`SecurityContextRepository`）或方案 3（`MODE_INHERITABLETHREADLOCAL`）——过度工程
  - 不要把整个 `/notifications/**` 加入白名单（只加 `/notifications/stream`）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 两处数组追加，简单改动
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2（与 Task 5, 6 并行）
  - **Blocks**: None
  - **Blocked By**: Task 1（Vitest 安装，确保前后端修改可分别验证）

  **References**:
  - `backend/.../auth/config/SecurityConfig.java:24-31` — `WHITELIST` 数组，需追加
  - `backend/.../auth/filter/JwtAuthenticationFilter.java:38-45` — `SKIP_PATHS` 列表，需同步追加
  - `backend/.../notification/controller/NotificationController.java:101-106` — `stream()` 方法，通过 `UserContext.getCurrentUserId()` 获取已认证用户
  - Spring Security 6.3.x `FilterChainProxy` — 异步分发时重放安全过滤链的行为（参考官方文档 `spring-security-web` 6.3.4）

  **Acceptance Criteria**:
  - [ ] `SecurityConfig.WHITELIST` 包含 `"/notifications/stream"`
  - [ ] `JwtAuthenticationFilter.SKIP_PATHS` 包含 `"/notifications/stream"`
  - [ ] `curl` 访问 `/api/notifications/stream` 返回 `200` 并收到 SSE `connected` 事件

  **QA Scenarios**:
  ```
  Scenario: SSE 端点返回 200 并收到 connected 事件
    Tool: Bash (curl)
    Preconditions: 后端运行中，用户已登录并持有有效 JWT Cookie
    Steps:
      1. 从浏览器 DevTools → Application → Cookies 复制 access_token 值
      2. curl -N -H "Cookie: access_token=<token>" http://localhost:8080/api/notifications/stream
      3. 等待 3 秒观察输出
    Expected Result: HTTP 200，输出包含 `event:connected` 和 `data:{"userId":...}`
    Failure Indicators: HTTP 403 或连接立即断开
    Evidence: .sisyphus/evidence/task-4-sse-success.txt

  Scenario: 无 Cookie 访问 SSE 返回 401
    Tool: Bash (curl)
    Preconditions: 无
    Steps:
      1. curl -v http://localhost:8080/api/notifications/stream
    Expected Result: HTTP 401（或由 Spring Security 默认行为处理）
    Failure Indicators: HTTP 200（白名单不应允许完全无认证访问——但当前设计依赖 JWT filter 在初始同步请求阶段处理）
    Evidence: .sisyphus/evidence/task-4-sse-no-auth.txt
  ```

  **Commit**: YES（独立提交）
  - Message: `fix(backend): add SSE stream endpoint to security whitelist to fix 403 on async dispatch`
  - Files: `backend/.../auth/config/SecurityConfig.java`, `backend/.../auth/filter/JwtAuthenticationFilter.java`

- [ ] 5. **前端 NotificationBell 单元测试**

  **What to do**:
  - 新建 `frontend-admin/src/components/__tests__/NotificationBell.test.ts`
  - 测试要点：
    1. 组件挂载时调用 `fetchUnreadCount()`（mock `getUnreadCount` API）
    2. 组件挂载时调用 `connectSSE()`（mock `createNotificationStream`）
    3. `handlePopoverChange(true)` 触发 `fetchNotifications()` + `fetchUnreadCount()`
    4. `handlePopoverChange(false)` 设置 `popoverOpen = false`
    5. 未读数为 0 时 badge 不显示
    6. 点击 `handleMarkRead` 后未读数减 1
  - 使用 `vi.mock()` mock API 模块和 `EventSource`

  **Must NOT do**:
  - 不要创建真实的 `EventSource` 连接
  - 不要测试 ant-design-vue 内部行为（测试自己的逻辑）
  - 不要 snapshot 测试

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 前端 Vue 组件测试，需要理解 Vue 3 Composition API + 组件交互
  - **Skills**: [`/frontend-ui-ux`]
    - `frontend-ui-ux`: Vue 组件测试需要前端框架知识
  - **Skills Evaluated but Omitted**:
    - `playwright`: 单元测试不需要浏览器自动化

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2（与 Task 4, 6 并行）
  - **Blocks**: None
  - **Blocked By**: Task 1（Vitest）, Task 2（修复）, Task 4（SSE 修复以便 mock 对齐）

  **References**:
  - `frontend-admin/src/components/NotificationBell.vue` — 完整组件源码，包含所有需测试的函数
  - `frontend-admin/src/api/modules/notification.ts` — API 模块，需 mock：`getUnreadCount`, `getNotifications`, `markAsRead`, `markAllAsRead`, `createNotificationStream`
  - `frontend-admin/src/types/notification.ts` — TypeScript 类型定义：`NotificationVO`, `UnreadCountResult`, `SseNotificationEvent`
  - `frontend-admin/vitest.config.ts` — Vitest 配置（Task 1 创建）

  **Acceptance Criteria**:
  - [ ] `pnpm test:unit` 运行 NotificationBell 测试，≥ 5 个测试用例全部通过
  - [ ] 测试覆盖: mount → fetch → SSE connect → popover toggle → mark read

  **QA Scenarios**:
  ```
  Scenario: 组件挂载后自动获取未读数
    Tool: Bash (pnpm test:unit)
    Preconditions: Vitest 已安装
    Steps:
      1. cd frontend-admin && pnpm test:unit -- --reporter=verbose
      2. 观察输出：test("fetches unread count on mount") 通过
    Expected Result: mock getUnreadCount 被调用一次，badge 显示正确未读数
    Failure Indicators: 测试失败或 API mock 未被调用
    Evidence: .sisyphus/evidence/task-5-test-output.txt

  Scenario: 点击通知铃铛触发 popover 回调
    Tool: Bash (pnpm test:unit)
    Preconditions: 同上
    Steps:
      1. pnpm test:unit -- --reporter=verbose
    Expected Result: test("handlePopoverChange triggers fetch on open") 通过
    Evidence: .sisyphus/evidence/task-5-test-output.txt（同文件追加）
  ```

  **Commit**: YES（与 Task 2 合并或独立提交）
  - Message: `test(frontend): add NotificationBell unit tests`
  - Files: `frontend-admin/src/components/__tests__/NotificationBell.test.ts`

- [ ] 6. **后端 SSE 集成测试（通过完整 Filter 链）**

  **What to do**:
  - 新建 `backend/src/test/java/com/cgcpms/notification/NotificationControllerIntegrationTest.java`
  - 使用 `MockMvc` 模拟完整 Spring Security 过滤链
  - 测试要点：
    1. `GET /api/notifications/stream` + 有效 JWT → 200 + `text/event-stream`
    2. `GET /api/notifications/stream` + 无 JWT → 401
    3. SSE 返回 `connected` 事件
  - 测试配置使用 `local` profile（H2 内存库）

  **Must NOT do**:
  - 不要绕过 Spring Security（必须测试完整过滤链）
  - 不要用 `NotificationService` 单元测试替代（那不测 Security）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Java Spring Security 集成测试，涉及 JWT 生成 + MockMvc + SSE 异步验证
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2（与 Task 4, 5 并行）
  - **Blocks**: None
  - **Blocked By**: Task 4（SSE 修复后才能通过测试）

  **References**:
  - `backend/.../notification/controller/NotificationController.java:101-106` — `stream()` 方法
  - `backend/.../auth/config/SecurityConfig.java` — WHITELIST 配置（Task 4 修改后）
  - `backend/.../auth/util/JwtUtils.java` — JWT 生成工具（测试需生成有效 token）
  - `backend/src/test/java/com/cgcpms/common/TestUserContext.java` — 现有测试辅助类
  - `backend/src/test/java/com/cgcpms/notification/NotificationServiceTest.java` — 现有 Service 层测试（参考 mock 模式）
  - `backend/src/test/resources/application-local.yml` — 测试配置（H2 内存库）

  **Acceptance Criteria**:
  - [ ] `mvn test -Dtest=NotificationControllerIntegrationTest -Dspring.profiles.active=local` 全部通过
  - [ ] ≥ 2 个测试用例：有效 JWT → 200 + 无效/缺失 JWT → 401

  **QA Scenarios**:
  ```
  Scenario: 有效 JWT 访问 SSE 返回 200 + connected 事件
    Tool: Bash (mvn test)
    Preconditions: 测试 profile 配置正确
    Steps:
      1. cd backend
      2. mvn test -Dtest=NotificationControllerIntegrationTest -Dspring.profiles.active=local
    Expected Result: test("stream endpoint returns 200 with valid JWT") PASS
    Failure Indicators: 测试返回 403 或连接失败
    Evidence: .sisyphus/evidence/task-6-integration-test.txt

  Scenario: 无 JWT 访问 SSE 返回 401
    Tool: Bash (mvn test)
    Steps:
      1. mvn test -Dtest=NotificationControllerIntegrationTest -Dspring.profiles.active=local
    Expected Result: test("stream endpoint returns 401 without JWT") PASS
    Evidence: .sisyphus/evidence/task-6-integration-test.txt（同文件）
  ```

  **Commit**: YES（独立提交）
  - Message: `test(backend): add SSE integration test through full security filter chain`
  - Files: `backend/src/test/java/com/cgcpms/notification/NotificationControllerIntegrationTest.java`

- [ ] 7. **诊断并修复 500 根因**

  **What to do**:
  - 重启后端，触发 `/api/notifications/unread-count` 请求，读取 Task 3 添加的诊断日志
  - 根据异常栈分析根因：
    - **若 `Table 'cgc_pms.sys_notification' doesn't exist`**: 执行 Flyway 迁移 `mvn flyway:migrate`
    - **若 `CommunicationsException`**: 启动 MySQL / Docker Compose
    - **若 `NullPointerException` at `UserContext.getCurrentUserId()`**: JWT claims 中缺少 userId 字段——修复 JWT 生成逻辑
    - **若 `AccessDeniedException`**: 用户缺少 `notification:view` 权限——补充权限配置
    - **若其他异常**: 根据具体异常写修复
  - 修复后验证 `curl` 返回 `200 {"code":"0","data":{"count":N}}`

  **Must NOT do**:
  - 不要在未看到异常栈的情况下猜测修复
  - 不要修改 Flyway 迁移脚本（如果表不存在，执行迁移，不是改脚本）
  - 不要修改 `@PreAuthorize` 表达式

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 需要根据实际异常灵活判断，涉及 Java/Spring Boot/MySQL 多层面诊断
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**:
  - **Can Run In Parallel**: NO（必须在 Task 3 完成并获取日志后执行）
  - **Parallel Group**: Wave 3（单独或与 Task 8 串行）
  - **Blocks**: Task 8
  - **Blocked By**: Task 3

  **References**:
  - `backend/.../notification/controller/NotificationController.java:54-61` — `unreadCount()` 方法
  - `backend/.../notification/service/NotificationService.java:99-105` — `getUnreadCount()` 方法
  - `backend/.../notification/entity/SysNotification.java` — 实体映射
  - `backend/src/main/resources/db/migration/V37__init_notification_table.sql` — Flyway 迁移脚本
  - `deploy/docker-compose.yml` — MySQL/Redis/MinIO 服务
  - `backend/src/main/resources/application-dev.yml` — 数据源配置
  - Task 3 输出的诊断日志 — 具体异常栈

  **Acceptance Criteria**:
  - [ ] `curl http://localhost:8080/api/notifications/unread-count`（带有效 JWT）→ 200
  - [ ] 响应体格式：`{"code":"0","data":{"count":N}}`
  - [ ] 浏览器中 `NotificationBell.vue` 正常显示未读数量

  **QA Scenarios**:
  ```
  Scenario: unread-count API 返回 200
    Tool: Bash (curl)
    Preconditions: MySQL 运行中，Flyway 迁移已执行，用户已登录
    Steps:
      1. curl -v -H "Cookie: access_token=<token>" http://localhost:8080/api/notifications/unread-count
      2. 检查 HTTP 状态码和响应体
    Expected Result: HTTP 200，响应体为 JSON `{"code":"0","data":{"count":0}}`（或实际未读数）
    Failure Indicators: HTTP 500 + 无 data 返回
    Evidence: .sisyphus/evidence/task-7-unread-count-200.json

  Scenario: 无权限用户访问返回 403（不是 500）
    Tool: Bash (curl)
    Preconditions: 使用缺少 notification:view 权限的 JWT
    Steps:
      1. curl -v -H "Cookie: access_token=<restricted-token>" http://localhost:8080/api/notifications/unread-count
    Expected Result: HTTP 403（不是 500）
    Evidence: .sisyphus/evidence/task-7-unread-count-403.txt
  ```

  **Commit**: YES（包含诊断日志的清理或保留）
  - Message: `fix(backend): resolve 500 error on /api/notifications/unread-count`
  - Files: 根据诊断结果确定，可能不含新文件

- [ ] 8. **后端 unread-count 单元测试**

  **What to do**:
  - 在现有 `NotificationServiceTest.java` 或新建测试类中添加 `unreadCount()` 相关测试
  - 测试要点：
    1. 无通知时返回 0
    2. 创建 3 条未读通知后返回 3
    3. 标记 1 条已读后返回 2
    4. 跨租户隔离：租户 A 的通知不影响租户 B 的计数
    5. 跨用户隔离：用户 A 的通知不影响用户 B 的计数
  - 测试 profile: `local`（H2 内存库）

  **Must NOT do**:
  - 不要测试 Controller 层（那是 Task 6 的范围）
  - 不要重复已有的 Service 测试

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Java 服务层测试，涉及多租户隔离验证
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 Task 7 的修复结果）
  - **Parallel Group**: Wave 3（在 Task 7 之后）
  - **Blocks**: None
  - **Blocked By**: Task 7

  **References**:
  - `backend/src/test/java/com/cgcpms/notification/NotificationServiceTest.java` — 现有测试（330 行，11 个测试），参考 mock 和断言模式
  - `backend/.../notification/service/NotificationService.java:99-105` — `getUnreadCount()` 方法
  - `backend/src/test/java/com/cgcpms/common/TestUserContext.java` — 测试辅助类
  - `backend/src/test/resources/application-local.yml` — 测试配置

  **Acceptance Criteria**:
  - [ ] `mvn test -Dtest=NotificationServiceTest -Dspring.profiles.active=local` 全部通过（包括新增的 unread-count 测试）
  - [ ] ≥ 4 个 unread-count 相关测试用例

  **QA Scenarios**:
  ```
  Scenario: 创建通知后未读数正确递增
    Tool: Bash (mvn test)
    Steps:
      1. cd backend && mvn test -Dtest=NotificationServiceTest -Dspring.profiles.active=local
    Expected Result: test("getUnreadCount returns correct count after create") PASS
    Evidence: .sisyphus/evidence/task-8-test-output.txt
  ```

  **Commit**: YES（与 Task 7 合并或独立提交）
  - Message: `test(backend): add unread-count unit tests`
  - Files: `backend/src/test/java/com/cgcpms/notification/NotificationServiceTest.java`（或新文件）

---

## Final Verification Wave

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists. For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in `.sisyphus/evidence/`. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `tsc --noEmit` + linter + `pnpm test:unit` (frontend). Run `mvn test` (backend). Review all changed files for: `as any`/`@ts-ignore`, empty catches, console.log in prod, commented-out code. Check AI slop: excessive comments, over-abstraction.
  Output: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | VERDICT`

- [ ] F3. **Real Manual QA** — `unspecified-high` (+ `playwright` skill)
  Start from clean state. Execute EVERY QA scenario from EVERY task. Test cross-task integration (notification bell + SSE + unread count all working together). Test edge cases: empty state, invalid JWT, database down. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (git log/diff). Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance. Flag unaccounted changes.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

| Task | Commit Message | Files |
|------|---------------|-------|
| 1 | `chore(frontend): install vitest + test infrastructure` | `package.json`, `pnpm-lock.yaml`, `vitest.config.ts`, `__tests__/sanity.test.ts` |
| 2 | `fix(frontend): replace deprecated onVisibleChange with onOpenChange` | `NotificationBell.vue` |
| 3 | `debug(backend): add diagnostic logging for unread-count 500` | `NotificationController.java`, `NotificationService.java` |
| 4 | `fix(backend): add SSE stream endpoint to security whitelist` | `SecurityConfig.java`, `JwtAuthenticationFilter.java` |
| 5 | `test(frontend): add NotificationBell unit tests` | `__tests__/NotificationBell.test.ts` |
| 6 | `test(backend): add SSE integration test through full filter chain` | `NotificationControllerIntegrationTest.java` |
| 7 | `fix(backend): resolve 500 error on /api/notifications/unread-count` | 根据诊断结果确定 |
| 8 | `test(backend): add unread-count unit tests` | `NotificationServiceTest.java` |

**Recommendation**: Tasks 2+5 可合并提交（修复+测试），Tasks 4+6 可合并提交（修复+测试），Tasks 7+8 可合并提交（修复+测试）。

---

## Success Criteria

### Verification Commands
```bash
# 前端
cd frontend-admin && pnpm test:unit        # Vitest 全部通过
cd frontend-admin && pnpm build            # 构建成功

# 后端
cd backend && mvn test -Dspring.profiles.active=local   # 全部测试通过

# 端到端
curl http://localhost:8080/api/notifications/unread-count -H "Cookie: access_token=<token>"  # → 200
curl -N http://localhost:8080/api/notifications/stream -H "Cookie: access_token=<token>"     # → SSE connected
```

### Final Checklist
- [ ] 浏览器控制台无 `onVisibleChange is deprecated` 警告
- [ ] `GET /api/notifications/unread-count` → 200
- [ ] `GET /api/notifications/stream` → 200（SSE connected 事件）
- [ ] `pnpm test:unit` 全部通过
- [ ] `mvn test` 全部通过（包括新增测试）
- [ ] 所有 "Must NOT Have" 检查通过
