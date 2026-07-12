# AutoPilot 双图谱路由整改设计

**Goal:** 让 AutoPilot 在代码检索中按明确条件使用 CodeGraph 与 `codebase-memory-mcp`，并在迭代报告中留下可复核证据，避免工具已安装但执行期未利用。

**Architecture:** 保留 CodeGraph 作为首选代码入口，不新增调度器。A 阶段先判断检索类型；跨层影响、复杂调用链、架构聚类或 CodeGraph 召回不足时，补充调用只读的 `codebase-memory-mcp`，必要时再用 `rg` 核对明确符号或文件。F 阶段把工具、目的、结果与核验方式写入现有迭代报告模板。

## 根因

现有规则只描述了 CodeGraph 的优先级和 `codebase-memory-mcp` 的只读安全边界，没有定义后者的必用条件；现有 AutoPilot 角色契约和迭代报告模板也没有图谱路由与证据字段。因此 ISSUE-037-001 至 ISSUE-037-010 执行时使用了 CodeGraph 和 `rg`，但没有调用补充图谱。

## 方案选择

采用条件触发的双图谱路由：

1. CodeGraph 负责首轮代码定位、源码读取和直接 blast radius。
2. 出现跨前后端/跨语言关系、复杂多跳调用链、架构边界/聚类判断，或 CodeGraph 召回不足时，必须补充调用 `codebase-memory-mcp`。
3. 已知符号、文件或字段仍未命中时，用 `rg` 做源码事实核对；不得把工具未命中解释为代码不存在。
4. 单纯文档、配置或无需代码定位的任务允许标记“不适用”，但必须写明原因。

不采用以下方案：

- 每个 Issue 无条件调用两套图谱：会制造重复查询，且对纯文档任务没有价值。
- 新增 PowerShell MCP 调度器：MCP 调用属于智能体工具上下文，额外包装会产生第二套执行链路。

## 改动范围

- `AGENTS.override.md`：补充双图谱路由顺序、条件和证据要求。
- `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`：把路由判断加入 A 阶段，把证据归档加入 F 阶段。
- `plugins/cgc-pms-autopilot/references/role-contracts.md`：定义 A/F 的最小输入输出。
- `plugins/cgc-pms-autopilot/templates/iteration-report-entry.md`：增加图谱检索证据字段。
- `plugins/cgc-pms-autopilot/examples/iteration-report-entry.example.md`：提供可复用示例。
- `scripts/codex-autopilot/test-tool-routing.ps1`：用最小静态自检防止规则和模板字段回退。
- `docs/iterations/iteration-2026-07-12-report.md`：如实记录本轮漏用事实、整改口径和现场只读验证结果。

## 数据流

1. A 读取 Ready Issue 和当前代码范围。
2. A 记录检索目的并先调用 CodeGraph。
3. A 根据触发条件决定是否调用 `codebase-memory-mcp`；调用失败按工具配置问题处理，不伪造命中结果。
4. 实现和审查仅使用已核对的源码事实。
5. F 在 iteration report 中记录 `工具 / 查询目的 / 命中摘要 / 交叉核验`；不适用时记录原因。

## 错误处理

- MCP 未加载、项目未索引或查询失败：归类为 `tool_config`，使用 CodeGraph 与 `rg` 完成本轮安全核对，并把缺失证据记入报告。
- 图谱结果与源码不一致：以当前分支源码和测试为准，记录索引陈旧或召回不足。
- `codebase-memory-mcp` 继续只允许本地索引和只读查询；不得调用会改写规则或 Codex 配置的安装/卸载入口。

## 验收

1. 静态自检在整改前因缺少双图谱路由和报告字段而失败。
2. 整改后静态自检通过，并确认规则、owner skill、角色契约和模板口径一致。
3. 对 `replenishmentLeadDays` 做一次 `codebase-memory-mcp` 只读检索，能返回后端 DTO/实体/测试、Controller 路由和前端 API 等跨层结果。
4. `git diff --check` 通过。
5. 不修改业务代码，不新增依赖，不 push。

## 非目标

- 不要求纯文档任务机械调用图谱。
- 不自动重建或升级 `codebase-memory-mcp` 索引。
- 不把 MCP 命中结果替代编译、测试或真实运行态验收。
- 不新增第三套图谱或新的 AutoPilot 状态机。
