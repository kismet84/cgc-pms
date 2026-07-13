# AutoPilot 双图谱路由整改实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AutoPilot 增加可执行、可报告、可回归验证的 CodeGraph 与 `codebase-memory-mcp` 条件路由。

**Architecture:** 不新增 MCP 调度器；在现有项目规则、owner skill、A/F 角色契约和 iteration 模板中固化条件路由。用一个 PowerShell 静态自检锁定关键口径，再以一次真实只读查询验证补充图谱可用。

**Tech Stack:** Markdown、PowerShell、Codex MCP

## Global Constraints

- CodeGraph 仍是首选代码检索入口。
- `codebase-memory-mcp` 只允许本地索引与只读查询。
- 不修改业务代码，不新增依赖，不 push。
- `rg` 只作为明确符号/文件的事实兜底，不把图谱未命中解释为代码不存在。

---

### Task 1: 双图谱规则回归测试

**Files:**
- Create: `scripts/codex-autopilot/test-tool-routing.ps1`

**Interfaces:**
- Consumes: 仓库根规则、AutoPilot owner skill、角色契约、iteration 模板与示例。
- Produces: 退出码 0/非 0 的静态自检命令。

- [ ] **Step 1: 写失败测试**

创建 PowerShell 脚本，逐项读取目标文件并断言包含：`跨层影响`、`codebase-memory-mcp`、`图谱检索证据`、`查询目的`、`交叉核验`。缺少任一项时抛出带文件名和字段名的错误。

- [ ] **Step 2: 验证 RED**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-tool-routing.ps1`

Expected: FAIL，首个缺失项来自尚未整改的规则或模板。

- [ ] **Step 3: 保留失败证据并进入 Task 2**

记录失败摘要，不修改测试断言。

### Task 2: 最小双图谱路由实现

**Files:**
- Modify: `AGENTS.override.md`
- Modify: `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`
- Modify: `plugins/cgc-pms-autopilot/references/role-contracts.md`
- Modify: `plugins/cgc-pms-autopilot/templates/iteration-report-entry.md`
- Modify: `plugins/cgc-pms-autopilot/examples/iteration-report-entry.example.md`

**Interfaces:**
- Consumes: Task 1 的固定断言。
- Produces: A 阶段的条件路由与 F 阶段的图谱证据字段。

- [ ] **Step 1: 修改根规则**

在 CodeGraph 规则旁增加以下语义：CodeGraph 优先；跨前后端/跨语言影响、复杂多跳调用链、架构边界/聚类或 CodeGraph 召回不足时必须调用 `codebase-memory-mcp`；已知符号/文件仍需用 `rg` 核对；工具失败归为 `tool_config`。

- [ ] **Step 2: 修改 owner skill 与角色契约**

A 输出增加“图谱路由判断、查询目的、命中摘要、交叉核验”；F 输出增加“图谱检索证据或不适用原因”。

- [ ] **Step 3: 修改模板与示例**

在 iteration entry 增加：

```markdown
- 图谱检索证据：{{graph_evidence}}
```

示例写明 CodeGraph 与 `codebase-memory-mcp` 的查询目的、命中摘要和源码交叉核验。

- [ ] **Step 4: 验证 GREEN**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-tool-routing.ps1`

Expected: PASS，输出 `tool routing self-test passed`。

### Task 3: 迭代报告补记与最终验证

**Files:**
- Modify: `docs/iterations/iteration-2026-07-12-report.md`

**Interfaces:**
- Consumes: Task 2 的新口径与现场只读查询结果。
- Produces: 不追溯伪造的整改记录和最终验收证据。

- [ ] **Step 1: 补记历史事实**

明确记录 ISSUE-037-001 至 ISSUE-037-010 当时未调用 `codebase-memory-mcp`，不得把整改后的查询追算为原迭代证据。

- [ ] **Step 2: 执行现场只读查询**

用 `search_graph` 查询 `replenishmentLeadDays plannedDate`，项目名使用已索引的 `D-projects-test-cgc-pms`；摘要记录命中的 DTO、实体、测试、Controller 路由与前端 API。

- [ ] **Step 3: 跑最终验证**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-tool-routing.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1
git diff --check
git status --short
```

Expected: 两个脚本退出码均为 0，`git diff --check` 无输出，状态只包含本计划范围文件。

- [ ] **Step 4: 本地提交**

```powershell
git add -- AGENTS.override.md plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md plugins/cgc-pms-autopilot/references/role-contracts.md plugins/cgc-pms-autopilot/templates/iteration-report-entry.md plugins/cgc-pms-autopilot/examples/iteration-report-entry.example.md scripts/codex-autopilot/test-tool-routing.ps1 docs/iterations/iteration-2026-07-12-report.md docs/plans/AutoPilot双图谱路由整改实施计划-2026-07-12.md
git commit -m "fix(autopilot): enforce dual graph routing evidence"
```

Expected: 本地提交成功，不 push。
