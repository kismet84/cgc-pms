# 第41条主线：知识图谱优先问题路由与 Ready 契约前置门禁验收报告

## 1. 裁决

- 结论：通过。
- 阻塞：非阻塞。
- 是否可上线：不可直接上线生产；本主线仅修改本地知识图谱与 AutoPilot 控制面，未授权也未执行生产发布。
- 回滚：可回退知识图谱 `issues` CLI、AutoPilot 图谱适配器和 Ready 前置矛盾检查；`current-issues.json` 正式写回能力与运行时 forbidden 优先门禁均未删除。

## 2. 正式交付物

- 知识图谱 CLI 新增 `issues` 命令，直接复用 `queries.js::listIssues`，支持有界过滤并保持 MCP 同构 JSON；错误只写 stderr 且非零退出。
- AutoPilot 补货新增图谱健康、最近采集、失败数、当前问题计数与 Git HEAD 游标门禁；游标落后最多执行一次 `collect --trigger autopilot-refill`，失败后 fail-close，不回退文件选题。
- 图谱候选继续复用原 P0→P2、Open→Observation、叶子优先、排除门禁/冻结/待确认/聚合父项和 `[stock:<issueKey>]` 去重规则；候选来源改为 `knowledge-graph`。
- Planner 输入只接收已选候选，要求按 `sourceRefs`、当前分支代码/配置和唯一载体核实，不再默认扫描整个问题台账发现替代项。
- Ready parser 新增确定性 allow/forbid 完全覆盖检查；矛盾返回 `READY_SCOPE_CONTRADICTION` / `ready_issue_config`，并在 executor 与 issue worktree 创建前停止。宽允许目录配合更窄禁止子目录的安全 carve-out 和运行时 forbidden 优先门禁保持不变。
- `AGENTS.override.md`、主线 Skill、AutoPilot owner Skill、插件参考、知识图谱 README 与项目地图已按实现证据固化。

## 3. 验收证据

### 3.1 知识图谱与 CLI

- `npm test`：23/23 通过。
- `npm run test:mcp`：通过，既有 9 个 MCP 工具保持可用，`kg_list_issues` 契约未回归。
- 真实 `node src/cli.js status`：`lastRunStatus=SUCCEEDED`、`lastRunFailures=0`、`currentIssues=54`。
- 真实 Git 游标与当前 HEAD 均为 `8317e84a6a81f13ec1b890af1c32c0374371beee`，未触发增量刷新。
- 真实 summary/list 查询：总计 54 条，P0 返回 13 条，字段与 `kg_list_issues` 同构。

### 3.2 AutoPilot 与 Ready 门禁

- `test-refill.ps1`：通过；覆盖图谱候选排序、排除、去重、不可用分类、单次刷新、刷新后仍陈旧的数据一致性停止。
- `test-ready-routing.ps1`：通过；覆盖精确文件/父子树完全覆盖矛盾与合法安全 carve-out。
- `test-continuous-runner.ps1`：通过；矛盾 Ready 在 executor/worktree 前停止，真实候选来源断言为 `knowledge-graph`。
- `test-control-plane.ps1`：通过；配置锁定 `allowRegistryFallback=false` 与有界查询。
- `validate-loop-artifacts.ps1`：通过；规则、Skill、参考、Schema 与脚本治理资产完整。
- 真实 continuous runner dry-run：输出 `candidateSource=knowledge-graph`、`[stock:A-01-MENU-LIST]` 和 `DRY_RUN_NO_BACKLOG_WRITE`，未创建 Ready、未启动 executor、未提交。
- 插件 runner dry-run：通过；保持 preview-only、未提交、未 push。
- `git diff --check`：通过。

### 3.3 失败分类与复核

- 首次完整 continuous runner 外层命令在 182 秒达到超时，但测试已输出 `continuous runner self-test passed`；分类为长耗时 fixture 清理/命令超时，不是断言或业务失败。提高外层超时后 153 秒正常退出并通过。
- `tools/knowledge-graph/test/issue-registry.test.js` 仍锁定关闭三个菜单问题前的 57 条、A-01 OPEN 24 条基线；当前正式台账为 54 条、A-01 OPEN 21 条。该当前差异已本轮修正并复验通过。

## 4. 图谱检索与交叉核验

- 查询目的：确认 `listIssues/status` 的复用边界、Git 游标语义、AutoPilot 补货调用链和 Ready 门禁位置。
- CodeGraph 命中：`listIssues` 被 MCP、acceptance、CLI 共同复用；Git collector 将 `source=git` 游标推进为当前 HEAD；runner 在执行器前解析 Ready。
- `codebase-memory-mcp` 命中：原 `Get-AutopilotStockIssueCandidates` 直接读文件，原范围违规仅在 executor 后由 `Assert-AutopilotAllowedChanges` 归为 `quality_security`，证明本主线缺口真实存在。
- 交叉核验：CodeGraph 变更后未关联新 `cli-issues.test.js`，已用明确符号 `rg` 补查并确认测试存在；归类为工具召回不足，不是缺少测试。

## 5. 审查结论与剩余风险

- 未发现静默文件回退、第二问题缓存、Cypher 复制、生产连接、生产发布或业务模块扩展。
- 图谱只用于发现与排序；正式状态仍写回 `current-issues.json`、backlog、报告和项目地图。
- 新增后续项：0。
- 关闭后续项：0。
- 后续项净变化：0。
- 剩余风险：无悬空项。Neo4j 不可用会按设计停止补货，这是明确的 fail-close 运行边界，不是未处置缺陷。

## 6. Git 边界

- 分支：`develop/1.5`。
- 本地提交：待本报告与最终门禁通过后执行。
- Push：未执行；用户未授权 push。
