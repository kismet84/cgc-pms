# 第41条主线：知识图谱优先问题路由与 Ready 契约前置门禁任务计划书

**Goal:** 把项目知识图谱正式设为存量问题查询和 AutoPilot 补货的默认结构化入口，并把 Ready 的允许/禁止路径矛盾提前到执行器启动前拦截；验收方向是普通查询不再先扫描台账文件、补货按图谱优先级稳定选出合格叶子问题、候选只在准备处理时交叉核验、矛盾 Ready 不消耗实现时间且稳定归类为 `ready_issue_config`。
**Architecture:** 复用现有 `kg_status`、`kg_list_issues`、`tools/knowledge-graph/src/queries.js::listIssues`、PowerShell AutoPilot runner 和严格 Ready parser；交互查询继续走 MCP，AutoPilot 通过轻量 Node CLI 直接复用同一图谱查询函数，不引入新的图谱服务、调度器或第二份问题数据库。Neo4j 是发现、筛选和关联索引，`current-issues.json`、来源报告、当前代码与配置仍是正式事实源；默认不回退文件选题，只有图谱异常时按失败分类安全停止，候选进入处理前才读取选中问题的 `sourceRefs` 和当前实现做交叉核验。Ready 范围门禁继续保持 forbidden 优先的运行时安全语义，只新增生成/导入阶段的矛盾检测，不扩大任何业务修改范围。

**Tech Stack:** Node.js 22、Neo4j Community、Neo4j JavaScript Driver、Model Context Protocol、PowerShell 5.1/7、Git、Node Test Runner。

## 1. 当前基线与问题

### 1.1 已有能力

- `cgc-pms-knowledge-graph` 已提供 `kg_status` 和 `kg_list_issues`，可按 `status`、`classification`、`priority`、`parentIssueKey`、`blocking` 和关键词返回有界结构化问题。
- 图谱问题实体已保存 `issueKey`、标题、摘要、状态、分类、优先级、父问题、阻塞属性、延期原因、验收标准、来源引用、版本范围和更新时间，不只是数量统计。
- `tools/knowledge-graph/src/queries.js::listIssues` 已被 MCP、验收脚本和查询测试复用。
- AutoPilot 已具备存量标记去重、P0→P2 排序、明确问题优先于观察项、叶子优先、Ready 队列上限和 stop/pause checkpoint。
- `ConvertTo-AutopilotReadyIssue` 已校验必填字段、验证命令入口和 Ready 状态；运行时 `Assert-AutopilotAllowedChanges` 会同时执行 allowlist 与 forbidden 门禁。

### 1.2 当前缺口

1. `Get-AutopilotStockIssueCandidates` 直接读取并全量解析 `docs/backlog/current-issues.json`，没有使用已经存在的结构化图谱查询。
2. Ready Planner 提示词默认要求先读取整个 `current-issues.json`，没有落实“图谱发现候选、选中后才交叉核验”。
3. 普通会话中的“查询存量问题”缺少持久化路由规则，容易退化为文件扫描。
4. 图谱索引失败、落后于当前 HEAD 或查询不可用时，AutoPilot 没有统一的健康门和安全停止分类。
5. Ready parser 分别读取允许和禁止路径，却不检查两组规则是否自相矛盾；精确允许文件可能被宽泛 forbidden 模式完全吞掉。
6. 范围矛盾只能在执行器完成后由运行时门禁发现，浪费实现时间，并可能被误报为实现越界或 `quality_security`。

## 2. 范围与非目标

### 2.1 本主线范围

- 固化普通问题查询的“知识图谱优先、深入处理时再核验”规则。
- 为知识图谱 CLI 增加结构化问题查询入口，复用现有 `listIssues`，供 PowerShell AutoPilot 使用。
- 将 AutoPilot 存量补货改为图谱健康检查、图谱候选查询、既有排序/排除/去重、选中候选核实、Ready 生成。
- 在 Ready lint 与导入阶段检测 allowlist/forbidden 矛盾，并返回可定位的规则对。
- 补充单元、控制面、连续 runner 和真实图谱验收证据。
- 更新项目规则、owner skill、插件说明和正式质量报告。

### 2.2 非目标

- 不重建第39条主线已经完成的图谱采集、版本、游标或 MCP 基础设施。
- 不把 Neo4j 变成正式问题写入源，不直接在图谱中关闭或修改 backlog 问题。
- 不允许未经核实的图谱候选直接修改业务代码。
- 不改变 Ready 的 forbidden 优先运行时安全语义。
- 不实现通用 glob 集合求交算法；本期只覆盖能够确定证明“允许规则被禁止规则完全吞掉”的矛盾。
- 不修改业务模块、数据库 migration、生产配置或 GitHub 门禁。
- 不连接生产、不发布生产、不自动 push。

## 3. 目标流程

```text
普通查询
  kg_status
    → kg_list_issues(有界过滤)
    → 直接返回结构化问题

AutoPilot 补货
  checkpoint
    → 图谱健康/HEAD 游标检查
    → 必要时执行一次增量采集并复查
    → 图谱拉取当前问题
    → 排除阻塞/发布门禁/冻结/待确认/父项/证据不完整/重复标记
    → P0→P1→P2、明确问题→观察项、叶子→根项排序
    → 选中候选
    → 读取该候选 sourceRefs、当前代码/配置和唯一台账记录进行核实
    → Ready 范围契约一致性检查
    → Ready lint
    → 实施
    → 收口回写正式事实源
    → 本地 commit 后刷新图谱
```

图谱只负责发现、筛选、排序和关联导航；进入 Ready 和最终通过/不通过裁决仍以当前分支事实与验证证据为准。

## 4. 实施任务

### Task 1：先建立失败测试与固定口径

**Files:**

- Modify: `scripts/codex-autopilot/test-refill.ps1`
- Modify: `scripts/codex-autopilot/test-ready-routing.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Modify: `tools/knowledge-graph/test/queries.test.js`
- Create: `tools/knowledge-graph/test/cli-issues.test.js`

**Interfaces:**

- Consumes: 当前文件直读补货、现有 `listIssues` 和 Ready parser。
- Produces: 图谱优先补货与 Ready 范围矛盾的 RED 证据。

- [ ] 把 `test-refill.ps1` 的候选提供者改为可注入的图谱快照桩；断言候选来源为 `knowledge-graph`，不得继续期望 `current-issues.json`。
- [ ] 覆盖 P0→P2、OPEN→OBSERVATION、叶子优先、blocking/RELEASE_GATE/FROZEN/NEEDS_CONFIRMATION/证据不完整排除和 `[stock:<issueKey>]` 去重。
- [ ] 增加图谱不可用、采集失败、游标落后当前 HEAD、刷新后仍不一致的安全停止用例。
- [ ] 增加精确允许文件被宽泛禁止目录吞掉的 Ready 样例，期望 lint 在执行前失败。
- [ ] 增加合法 carve-out 样例：允许宽泛业务目录、禁止其中敏感子目录时仍可通过，避免把安全收窄误判为矛盾。
- [ ] 在连续 runner 测试中断言矛盾 Ready 不创建 executor 进程、不创建 issue worktree、不计入实施数量。

**RED 验收：** 新增断言在现有实现上稳定失败，且失败点分别位于“候选仍来自文件”和“Ready parser 未检测范围矛盾”。

---

### Task 2：提供复用现有查询函数的轻量图谱 CLI

**Files:**

- Modify: `tools/knowledge-graph/src/cli.js`
- Modify: `tools/knowledge-graph/package.json`
- Modify: `tools/knowledge-graph/test/cli-issues.test.js`
- Modify: `tools/knowledge-graph/README.md`

**Interfaces:**

- Consumes: `queries.js::status`、`queries.js::listIssues`、现有 Neo4j 配置。
- Produces: 标准输出中的单一 JSON 文档和非零失败退出码。

- [ ] 新增 `node src/cli.js issues` 命令，支持 `--view`、`--limit`、`--status`、`--classification`、`--priority`、`--parent-issue-key`、`--blocking`、`--current-only` 和 `--query`。
- [ ] CLI 直接调用现有 `listIssues`，不得复制 Cypher、另建缓存文件或启动 Codex Planner。
- [ ] 输出保持与 MCP `kg_list_issues` 同构，便于交互查询和 AutoPilot 使用同一字段语义。
- [ ] 参数、Neo4j 连接、Schema 或查询失败必须写入 stderr 并非零退出；不得输出部分成功 JSON 冒充完整结果。
- [ ] 增加 `npm run issues -- --view summary` 快捷入口和 CLI 参数测试。
- [ ] `kg_list_issues` MCP smoke 保持通过，证明 CLI 扩展未改变现有 MCP 契约。

---

### Task 3：建立 AutoPilot 图谱健康门与候选适配器

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-refill.ps1`
- Modify: `scripts/codex-autopilot/test-refill.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`

**Interfaces:**

- Consumes: `node tools/knowledge-graph/src/cli.js status/issues`、当前 Git HEAD、stop/pause/enabled 和已有 Ready/Done/Blocked 标记。
- Produces: 与现有 Planner 兼容的候选对象，`source=knowledge-graph`。

- [ ] 新增 `Get-AutopilotKnowledgeGraphIssueSnapshot`，先读取 `status`，确认最近采集成功、失败数为0、当前问题数量有效、Git 游标覆盖当前 HEAD。
- [ ] 图谱游标落后时只允许执行一次现有增量采集，触发类型标记为 `autopilot-refill`；采集后必须重新检查健康与游标。
- [ ] CLI/参数/schema/规则文件错误归类为 `tool_config`；Neo4j/端口不可用归类为 `environment_prereq`；成功采集后问题数量或游标仍不一致归类为 `quality_security` 数据一致性问题。
- [ ] 默认禁止静默回退到文件选题；失败时返回明确的 `STOP_KG_REFILL_UNAVAILABLE`、`STOP_KG_REFILL_STALE` 或等价结构化停止原因。
- [ ] 图谱查询保持有界，最多读取当前问题查询上限，不展开全文 Artifact 或历史问题。
- [ ] 复用现有排序、排除和 `[stock:<issueKey>]` 去重逻辑；只替换候选来源，不改变 Ready 队列上限和 blocker/ad-hoc/product-intelligence 顺序。
- [ ] 候选对象保留 `issueKey`、`parentIssueKey`、`summary`、`acceptanceCriteria`、`sourceRefs`、优先级、状态和分类。

**最小配置建议：**

```json
{
  "issueGraph": {
    "enabled": true,
    "cli": "tools/knowledge-graph/src/cli.js",
    "refreshWhenHeadDiffers": true,
    "allowRegistryFallback": false,
    "queryLimit": 200
  }
}
```

如现有配置结构不适合新增对象，实施时可改为等价的最少字段，但不得把 fallback 默认设为 true。

---

### Task 4：落实“图谱发现、处理前核实”

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-refill.ps1`
- Modify: `scripts/codex-autopilot/test-refill.ps1`
- Modify: `plugins/cgc-pms-autopilot/references/role-contracts.md`
- Modify: `plugins/cgc-pms-autopilot/references/forward-test-scenarios.md`

**Interfaces:**

- Consumes: 图谱候选及其 `sourceRefs`。
- Produces: 经当前分支事实核实的最小 Ready 输入。

- [ ] Ready Planner 不再默认扫描整个 `current-issues.json`；提示词只接收已选候选，并要求读取该候选的 `sourceRefs`、相关当前代码/配置和唯一台账记录。
- [ ] 核实必须回答：问题是否仍存在、用户价值是否明确、验收标准是否可执行、依赖是否满足、是否与现有 Ready/Done/Blocked 重复。
- [ ] CodeGraph 用于当前代码关系检索；跨前后端/跨语言或召回不足时补 `codebase-memory-mcp`；已知文件/符号缺失时用 `rg` 兜底并记录工具召回不足。
- [ ] 核实不成立时不得生成 Ready；应返回“关闭候选”或“需要确认”的结构化建议，由正式事实源承接后再刷新图谱。
- [ ] 核实成立时保留 `[stock:<issueKey>]`，Ready 收口仍必须更新/移除 `current-issues.json` 中的唯一源问题。
- [ ] 图谱记录与当前代码冲突时，以当前分支事实为准，结论标记为索引陈旧或源事实待更新，禁止猜测。

---

### Task 5：增加 Ready 范围契约前置矛盾检测

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-ready.ps1`
- Modify: `scripts/codex-autopilot/ready-lint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-refill.ps1`
- Modify: `scripts/codex-autopilot/test-ready-routing.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`

**Interfaces:**

- Consumes: 规范化后的 `allowedPaths` 和 `forbiddenPaths`。
- Produces: 可定位冲突规则的 `ready_issue_config` 失败。

- [ ] 新增范围矛盾检查函数，在 `ConvertTo-AutopilotReadyIssue` 返回合同前运行。
- [ ] 至少检测：完全相同规则、精确允许文件命中 forbidden、允许的确定性子树被 forbidden 父树完全覆盖。
- [ ] 允许“宽泛允许 + 更窄禁止”的安全 carve-out，不因存在部分交集就一律拒绝。
- [ ] 错误信息包含 issueId、允许规则、禁止规则和可证明的冲突路径，建议稳定错误码 `READY_SCOPE_CONTRADICTION`。
- [ ] `ready-lint.ps1` 对该错误返回 `status=fail`、退出码非0，并保留 `failureCategory=ready_issue_config` 或等价可机读字段。
- [ ] Planner 导入临时 Ready 文件时复用同一 parser；矛盾块不得写入正式 `ready-issues.md`。
- [ ] runner 遇到该错误必须在 executor/worktree 创建前停止；不得记为业务实现越界、测试失败或 `quality_security`。
- [ ] 运行时 `Assert-AutopilotAllowedChanges` 保持 forbidden 优先，作为最后一道安全门禁。

---

### Task 6：固化项目规则与使用习惯

**Files:**

- Modify: `AGENTS.override.md`
- Modify: `.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md`
- Modify: `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`
- Modify: `plugins/cgc-pms-autopilot/references/install.md`
- Modify: `tools/knowledge-graph/README.md`

- [ ] 明确普通“查询存量问题”默认执行 `kg_status` + `kg_list_issues`，不先扫描仓库文件。
- [ ] 明确只有准备处理、深入分析、正式验收、图谱异常或索引过期时才交叉核验源码、配置、台账和来源报告。
- [ ] 明确 AutoPilot 补货必须从图谱按优先级拉取问题，再核实候选；`current-issues.json` 是正式写回源而不是默认查询入口。
- [ ] 明确图谱异常时 AutoPilot 安全停止，不允许为了维持迭代从长期计划或文件台账静默凑任务。
- [ ] 明确 Ready allow/forbid 冲突属于 `ready_issue_config`，必须在实施前修正。
- [ ] 不在长期规则中写入一次性 run id、截图、日志路径或临时故障细节。

---

### Task 7：集成验证、真实补货预演与正式收口

**Files:**

- Modify: `scripts/codex-autopilot/test-control-plane.ps1`
- Modify: `plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1`（仅当现有聚合入口未覆盖新增测试时）
- Create: `docs/quality/mainline-41-knowledge-graph-first-refill-and-ready-scope-acceptance-YYYY-MM-DD.md`
- Modify: `docs/quality/README.md`
- Modify: `docs/product-intelligence/project-map.md`

- [ ] 执行知识图谱 Node 单元测试、CLI 测试、MCP smoke 和真实 `kg_status`/`issues` 查询。
- [ ] 执行 refill、Ready routing、continuous runner、control plane 和 artifact validator 回归。
- [ ] 用真实图谱执行一次受控 dry-run：输出候选来源、问题键、优先级、排除原因和核实入口，不创建 Ready、不提交。
- [ ] 构造 allow/forbid 冲突 Ready，证明 lint 在 executor 前拒绝；修正规则后证明同一 Ready 可进入正常范围门禁。
- [ ] 验证图谱游标落后时只做一次增量刷新，刷新失败安全停止，成功后候选查询恢复。
- [ ] 验证候选进入 Ready 后保留唯一 `[stock:<issueKey>]`，Done 后正式源更新且下一次图谱刷新不再返回该问题。
- [ ] 正式报告记录图谱查询目的、命中摘要、交叉核验、失败分类、后续项净变化和 no-push 边界。

## 5. 验收命令

```powershell
cd D:\projects-test\cgc-pms\tools\knowledge-graph
npm test
npm run test:mcp
node src/cli.js status
node src/cli.js issues --view summary --current-only
node src/cli.js issues --view list --current-only --priority P0 --limit 20

cd D:\projects-test\cgc-pms
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-refill.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-ready-routing.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-continuous-runner.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-control-plane.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File plugins/cgc-pms-autopilot/scripts/autopilot-loop-runner.ps1 -DryRun -ReadyIssuePath D:\projects-test\cgc-pms\docs\backlog\ready-issues.md
git diff --check
git status --short
```

正式验收至少断言：

- 普通问题查询只调用图谱接口即可返回具体问题实体和汇总。
- AutoPilot 候选 `source=knowledge-graph`，排序与排除规则保持不变。
- 图谱不可用或游标不新鲜时不会静默回退文件选题。
- 选中候选未经 `sourceRefs` 和当前代码核实不得生成 Ready。
- allow/forbid 矛盾在执行器启动前稳定失败，分类为 `ready_issue_config`。
- 合法安全 carve-out、运行时 forbidden 门禁和最终范围检查均未回退。
- 收口更新正式事实源并刷新图谱后，同一存量问题不再重复补货。

## 6. 风险与控制

| 风险 | 等级 | 控制措施 |
| --- | --- | --- |
| 图谱索引落后导致选择已关闭问题 | 高 | HEAD 游标健康门、必要时单次增量刷新、选中后交叉核验 |
| Neo4j 不可用导致 AutoPilot 无候选 | 中 | 明确环境分类和安全停止，不静默从文件或长期计划凑任务 |
| 图谱成为第二事实源并产生双写漂移 | 高 | 图谱只读发现；正式状态仍写回 backlog/报告/项目地图，随后重新采集 |
| CLI 与 MCP 查询语义分叉 | 中 | 两者复用同一 `listIssues`，共享测试和同构输出 |
| 范围矛盾检测误伤合法 forbidden carve-out | 高 | 只拒绝可证明的完全覆盖，保留“宽允许+窄禁止”正样本 |
| Planner 仍全量扫描文件抵消图谱收益 | 中 | 提示词只传选中候选和来源引用，控制面测试锁定候选来源 |
| 失败分类被误记为业务问题 | 中 | 图谱/Ready 配置/环境/真实质量分别编码，报告保留分类证据 |

## 7. 回滚方案

- 回退 AutoPilot 图谱候选适配器和 CLI `issues` 命令，不删除 Neo4j 数据。
- 保留现有 `current-issues.json` 正式事实源和 closeout 写回能力。
- 若图谱补货未通过验收，禁用连续补货并安全停止，不自动恢复文件选题；是否临时启用显式 fallback 由用户单独确认。
- 回退 Ready 前置矛盾检查时，运行时 forbidden 门禁仍保留，不降低已有安全边界。
- 不删除历史报告、计划、图谱节点或 Git 提交。

## 8. 完成定义

只有同时满足以下条件，本主线才可判定通过：

- 普通存量问题查询和 AutoPilot 补货均默认走知识图谱。
- 图谱健康、刷新、失败分类和安全停止路径有自动化证据。
- 候选进入处理前完成最小必要交叉核验，未退化为默认全库扫描。
- Ready allow/forbid 矛盾在执行器前被拒绝，合法安全 carve-out 不受影响。
- refill、Ready lint、连续 runner、控制面、MCP 和图谱测试全部通过。
- 正式质量报告和项目地图完成回写，新增后续项、关闭后续项和净变化明确。
- `git diff --check` 通过，工作区只包含本主线授权范围。
- 不发布生产、不连接生产库、不自动 push。

**计划裁决：** 方案可进入实施，但必须等当前主线程任务完全收口、根工作区恢复稳定后再启动。实施顺序固定为“测试锁口径 → 图谱 CLI → refill 候选适配 → 候选核实 → Ready 矛盾前置门禁 → 规则固化 → 集成验收”，不得先改长期规则再补控制面证据。
