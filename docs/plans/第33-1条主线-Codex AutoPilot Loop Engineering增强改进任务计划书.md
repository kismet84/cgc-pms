# 第33-1条主线-Codex AutoPilot Loop Engineering增强改进任务计划书

**Goal:**
在第33条主线已完成 `cgc-pms-autopilot` 插件化封装 MVP 的基础上，引入 Loop Engineering、ReAct、Reflexion、Self-Refine、SWE-agent、Voyager、MetaGPT 等方法中的可落地部分，把当前“插件工具包”增强为可观测、可预算、可复盘、可自我改进但受控的 AutoPilot loop harness；首批只补状态协议、事件协议、预算、失败复跑、补修请求、反思条目和前向验证场景，不新增 dashboard、数据库、常驻服务或生产发布能力。

**Architecture:**
沿用第33条的轻量插件结构，不推翻既有 `Plugin + Skill + PowerShell scripts + templates + references + examples` 架构；本主线只在 `plugins/cgc-pms-autopilot/` 内增量增加 loop state schema、event schema、budget/policy/reference、repair/reflection 模板和最小自检脚本，使每轮 AutoPilot 从“人工可读流水线”升级为“状态机驱动、证据驱动、预算约束、失败可分类、经验可回填”的闭环系统。项目真实产物仍保留在 `docs/plans`、`docs/quality`、`docs/iterations`、`docs/backlog`，插件继续只放模板、工具、示例和规则说明。

## 1. 当前进度核实

第33条主线当前状态为：已完成，可进入增强阶段。

已核实的当前证据：

1. 插件目录已存在：`plugins/cgc-pms-autopilot/`。
2. 插件 manifest 已通过校验：`validate_plugin.py plugins/cgc-pms-autopilot` 返回通过。
3. 已交付核心 Skill：`plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`。
4. 已交付 6 个脚本：
   - `autopilot-checkpoint.ps1`
   - `render-template.ps1`
   - `ready-issue-writer.ps1`
   - `issue-closeout.ps1`
   - `test-failure-classifier.ps1`
   - `local-commit-closeout.ps1`
5. 已交付 8 个模板：
   - `mainline-plan.md`
   - `ready-issue.md`
   - `done-issue.md`
   - `blocked-issue.md`
   - `quality-closeout.md`
   - `security-review.md`
   - `iteration-report-entry.md`
   - `run-summary.md`
6. 已交付 references 与 examples。
7. `autopilot-checkpoint.ps1` 可运行并输出 JSON；当前状态为 `stop.flag=True`、`pause.flag=False`、`enabled.flag=False`。
8. `test-failure-classifier.ps1` 可把 `ECONNREFUSED` 类错误归为 `environment_prereq`。
9. `local-commit-closeout.ps1 -DryRun` 可运行，且不提交、不 push。
10. 当前 `git status --short` 为空，说明第33条成果已在当前工作区收口；用户此前手工处理的 `.serena` 删除项不再出现在当前状态中。

结论：第33条已经从“计划”推进到“插件 MVP 已完成并可运行”。第33-1不再重复做插件骨架，而是做 Loop Engineering 增强。

## 2. 外部方法来源与可采纳结论

### 2.1 Addy Osmani: Loop Engineering

Addy Osmani 将 Loop Engineering 描述为：不再由人持续手写 prompt，而是设计一个系统替人持续提示、观察和纠偏 agent。对本项目的启发是：

1. AutoPilot 不应只是脚本集合，而应有清晰 loop。
2. loop 必须有自动化、skills、plugins/connectors、sub-agents、worktrees 或等价隔离机制。
3. token 成本和失控风险必须被预算和停止条件约束。

落地建议：

1. 第33-1新增 loop state schema。
2. 第33-1新增 loop budget policy。
3. 第33-1新增 stop/pause/continue 的状态机裁决。

### 2.2 ReAct: Reasoning + Acting

ReAct 强调推理与行动交替，并通过外部观察结果修正下一步。对本项目的启发是：

1. 每轮不应只有“执行命令”，还要保留观察与决策。
2. `checkpoint -> act -> observe -> classify -> next_action` 应成为固定结构。

落地建议：

1. 每个脚本输出都统一包含 `ok`、`summary`、`evidence`、`nextAction`。
2. D/E/F 的 markdown 报告之外，运行态 JSON 应记录观察与下一步。

### 2.3 Reflexion: Verbal Feedback Memory

Reflexion 使用语言反馈作为记忆，不更新模型权重。对本项目的启发是：

1. 不保存大段日志，只保存失败模式和修正经验。
2. 阻塞解除、瞬时失败、误分类等经验应沉淀成短条目。

落地建议：

1. 新增 `templates/reflection-entry.md`。
2. 新增 `references/lessons-learned.md` 或 `references/reflection-policy.md`。
3. 仅保存稳定经验，不保存 run id、截图名、子智能体昵称。

### 2.4 Self-Refine: Generate -> Feedback -> Refine

Self-Refine 说明反馈和修订可以独立成模板化循环。对本项目的启发是：

1. D/E 不应只输出“不通过”，还应输出可执行补修请求。
2. C/B 的补修输入要结构化，避免反复解释。

落地建议：

1. 新增 `templates/repair-request.md`。
2. D/E 输出中增加 `failed_check`、`evidence`、`required_change`、`allowed_files`、`reverify_command`。

### 2.5 SWE-agent: Agent-Computer Interface

SWE-agent 说明 agent 的工具接口质量会显著影响软件工程任务表现。对本项目的启发是：

1. PowerShell 脚本不应输出长篇自然语言。
2. 工具输出应稳定、短、机器可读。
3. 文件操作必须显式路径、显式边界、默认 dry-run。

落地建议：

1. 统一脚本 JSON 输出 envelope。
2. 增加 `scripts/validate-loop-artifacts.ps1` 自检工具。
3. 将 `local-commit-closeout.ps1` 的真实 commit 行为保持显式授权，不作为默认动作。

### 2.6 Voyager: Curriculum + Skill Library + Self Verification

Voyager 的 automatic curriculum、skill library、自验证机制对 AutoPilot 很适合。对本项目的启发是：

1. backlog/ready issue 可视为 curriculum。
2. 插件 Skill/scripts/templates 是 skill library。
3. 每轮必须有自验证和收口规则。

落地建议：

1. Ready Issue 增加可选元数据：`difficulty`、`dependency`、`blast_radius`、`verification_cost`、`confidence`。
2. A 拆题时按这些字段决定串行/并行/升档。

### 2.7 MetaGPT: SOP + Roles + Intermediate Verification

MetaGPT 将标准操作流程编码到多智能体协作中。对本项目的启发是：

1. A-F 分工是正确方向，但要有中间产物校验。
2. 每个角色的输入/输出 contract 要固定。

落地建议：

1. 新增 `references/role-contracts.md`。
2. 新增 D/E/F 输出 JSON contract。
3. 禁止实现型子智能体自评最终通过。

## 3. 第33-1目标与非目标

### 3.1 目标

1. 把第33条插件从“模板与脚本工具包”增强为 loop harness。
2. 统一每轮 loop 的状态、事件、预算、失败复跑、补修请求、反思条目。
3. 让脚本输出更适合 agent 使用：短、稳定、JSON、可机器读取。
4. 让 D/E/F 的反馈可直接转成 C/B 的补修输入。
5. 增加 forward-test 场景，验证 Ready 空、命令失败、F 归档、local commit dry-run 四条关键路径。

### 3.2 非目标

1. 不改第31条既有 `scripts/codex-autopilot/**` 主运行系统。
2. 不新增数据库。
3. 不新增 dashboard。
4. 不新增 MCP 可执行工具。
5. 不自动发布 plugin marketplace。
6. 不让插件自动修改自己的主规则并提交。
7. 不把运行态 JSON 作为长期正式产物入库。

## 4. Loop Harness 目标状态

第33-1完成后，每轮 AutoPilot 至少能被描述成以下状态机：

```text
select -> plan -> act -> observe -> classify -> repair -> verify -> closeout -> learn -> next
```

其中：

1. `select`：从 Ready/blocked/current-focus 选择任务。
2. `plan`：A 生成目标、边界、验收、风险。
3. `act`：B/C 执行最小实现。
4. `observe`：D/E/脚本读取命令、diff、运行态证据。
5. `classify`：把失败归入工具、环境、Ready 配置、真实质量、安全或 unknown。
6. `repair`：必要时生成结构化补修请求给 B/C。
7. `verify`：D 最终复验。
8. `closeout`：F 归档和本地 commit dry-run/commit。
9. `learn`：只沉淀稳定经验。
10. `next`：检查 stop/pause/ready，决定下一轮。

## 5. 新增目录与文件设计

本主线只在既有插件内增量新增：

```text
plugins/cgc-pms-autopilot/
  schemas/
    loop-state.schema.json
    loop-event.schema.json
    repair-request.schema.json
  references/
    loop-engineering.md
    loop-budget-policy.md
    rerun-policy.md
    role-contracts.md
    reflection-policy.md
    forward-test-scenarios.md
  templates/
    repair-request.md
    reflection-entry.md
    loop-budget.md
  examples/
    loop-state.example.json
    loop-event.example.json
    repair-request.example.md
    reflection-entry.example.md
  scripts/
    validate-loop-artifacts.ps1
```

如果要更懒，MVP 可以只做：

1. `schemas/loop-state.schema.json`
2. `schemas/loop-event.schema.json`
3. `templates/repair-request.md`
4. `templates/reflection-entry.md`
5. `references/loop-budget-policy.md`
6. `references/forward-test-scenarios.md`

其余留到 M2。

## 6. Loop State Schema

`loop-state.schema.json` 最小字段：

```json
{
  "loopId": "string",
  "issueId": "string",
  "phase": "select|plan|act|observe|classify|repair|verify|closeout|learn|next",
  "actorRole": "owner|A|B|C|D|E|F|ops",
  "inputArtifacts": [],
  "outputArtifacts": [],
  "verification": [],
  "failureCategory": "tool_config|environment_prereq|ready_issue_config|real_quality_or_security|unknown|none",
  "decision": "continue|repair|blocked|done|stop|pause",
  "nextAction": "string",
  "stopReason": "string"
}
```

验收标准：

1. 所有字段都有说明。
2. `failureCategory` 与现有 classifier 对齐。
3. `decision` 可表达 stop/pause/blocked/done。
4. 不要求引入运行态数据库。

## 7. Loop Event Schema

`loop-event.schema.json` 最小字段：

```json
{
  "time": "ISO-8601",
  "loopId": "string",
  "issueId": "string",
  "phase": "string",
  "role": "string",
  "eventType": "checkpoint|dispatch|command|observation|classification|repair|verification|closeout|reflection",
  "summary": "string",
  "evidence": [],
  "nextAction": "string"
}
```

用途：

1. 运行态追踪。
2. 支撑事后复盘。
3. 给 F 归档提供摘要来源。

禁止：

1. 不把完整 stdout/stderr 默认写入 event。
2. 不记录密钥、cookie、token。
3. 不记录子智能体昵称。

## 8. Repair Request 模板

`repair-request.md` 字段：

1. `issue_id`
2. `failed_check`
3. `evidence`
4. `failure_category`
5. `required_change`
6. `allowed_files`
7. `forbidden_files`
8. `reverify_command`
9. `stop_condition`

用途：

1. D/E 不通过时直接生成 C/B 可执行补修输入。
2. 减少主线程重复翻译验收结果。

验收标准：

1. 能覆盖测试失败、权限审查失败、文档归档失败三类场景。
2. 不包含模糊指令，如“看一下”“优化一下”。

## 9. Reflection Entry 模板

`reflection-entry.md` 字段：

1. `failure_pattern`
2. `root_cause`
3. `fix_action`
4. `next_rule`
5. `confidence`
6. `scope`

用途：

1. 只记录稳定经验。
2. 用于后续 Skill 或 AGENTS 规则回填。

禁止：

1. 不保存 run id。
2. 不保存大段日志。
3. 不保存一次性路径。
4. 不把未复现问题写成长期规则。

## 10. Loop Budget Policy

预算字段：

1. `max_subagents`
2. `max_retries_per_command`
3. `max_reverify_commands`
4. `max_wall_time_minutes`
5. `max_repair_rounds`
6. `stop_on_repeated_blocker`

建议默认值：

```text
max_subagents=3
max_retries_per_command=1
max_reverify_commands=3
max_repair_rounds=2
stop_on_repeated_blocker=true
```

规则：

1. 高风险安全/权限问题可升档，但不能无限复验。
2. 首次疑似瞬时失败允许复跑一次。
3. 同一阻塞重复出现两轮后应写 blocked 或请求人工裁决。

## 11. Rerun Policy

命令失败处理：

```text
first failure -> classify
transient suspected -> rerun once
same failure repeated -> repair or blocked
different failure -> collect stronger evidence
```

分类建议：

1. `ECONNREFUSED`、端口未通：环境前置。
2. `ParserError`、命令参数错误：工具/命令调用。
3. 测试选择器不存在：Ready 配置。
4. 可复现断言失败：真实质量。
5. 编译失败但未指向当前 diff：unknown，需补证据。

## 12. Role Contracts

### A Contract

输入：

1. Ready/current-focus/blocked/done。
2. 计划书。

输出：

1. 问题分类。
2. 最小实现边界。
3. 验收命令建议。

### B/C Contract

输入：

1. 允许文件。
2. 禁止文件。
3. 验收命令。
4. repair-request。

输出：

1. 修改文件。
2. 修改点。
3. 局部验证。
4. 剩余风险。

### D Contract

输入：

1. 目标测试。
2. C/B 结果。

输出：

1. 通过/不通过。
2. 失败分类。
3. repair-request 或通过证据。

### E Contract

输入：

1. diff。
2. 风险点。

输出：

1. 阻塞问题。
2. 非阻塞建议。
3. 是否需要纳入本轮。

### F Contract

输入：

1. D/E 结论。
2. 正式交付物。

输出：

1. quality/done/blocked/iteration。
2. local commit 建议。
3. 剩余风险。

## 13. Forward-Test 场景

第33-1必须补 4 个前向验证场景：

### 场景1：Ready 空

输入：

1. Ready 队列为空。
2. current-focus 有长期池入口。

预期：

1. A 进入拆题。
2. 不写业务代码。
3. 生成 Ready 或停止原因。

### 场景2：命令失败

输入：

1. `ECONNREFUSED` 示例。

预期：

1. classifier 输出 `environment_prereq`。
2. nextAction 指向 runtime refresh。

### 场景3：D/E 不通过

输入：

1. 一条测试失败或权限审查失败摘要。

预期：

1. 生成 repair-request。
2. 包含 allowed/forbidden/reverify。

### 场景4：F 归档

输入：

1. Done 结论。
2. 正式交付物列表。

预期：

1. 生成 done/quality/iteration 片段。
2. local commit dry-run 通过。
3. 不 push。

## 14. 分阶段实施计划

### M1 状态与事件协议

交付物：

1. `schemas/loop-state.schema.json`
2. `schemas/loop-event.schema.json`
3. `examples/loop-state.example.json`
4. `examples/loop-event.example.json`

验收：

1. JSON 可被 PowerShell `ConvertFrom-Json` 读取。
2. 字段覆盖 phase/role/decision/failureCategory。
3. 不包含项目真实产物。

### M2 补修与反思模板

交付物：

1. `templates/repair-request.md`
2. `templates/reflection-entry.md`
3. `examples/repair-request.example.md`
4. `examples/reflection-entry.example.md`

验收：

1. render-template 可渲染两个模板。
2. 缺变量时失败。
3. 模板不含临时日志字段。

### M3 预算与复跑策略

交付物：

1. `references/loop-budget-policy.md`
2. `references/rerun-policy.md`

验收：

1. 明确 max retries、max repair rounds。
2. Maven/testCompile/环境波动有保守分类规则。

### M4 角色 contract 与 forward-test

交付物：

1. `references/role-contracts.md`
2. `references/forward-test-scenarios.md`

验收：

1. A-F 输入输出清晰。
2. 四个 forward-test 场景具备输入、预期、通过条件。

### M5 自检脚本

交付物：

1. `scripts/validate-loop-artifacts.ps1`

验收：

1. 能检查 schemas/templates/examples 是否存在。
2. 能检查 JSON 示例可解析。
3. 不写文件。

### M6 Skill 回填

交付物：

1. 更新 `skills/cgc-pms-autopilot-owner/SKILL.md`

验收：

1. Skill 能指向 loop state、repair-request、reflection-entry、budget policy。
2. 不膨胀成长篇论文综述。

## 15. 验收命令

最小验收命令：

```powershell
git status --short
python C:\Users\L1597\.codex\skills\.system\plugin-creator\scripts\validate_plugin.py plugins\cgc-pms-autopilot
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\autopilot-checkpoint.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1 -ErrorText "ECONNREFUSED localhost:8080" -ExitCode 1
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\local-commit-closeout.ps1 -IssueId ISSUE-DRY-RUN -DryRun
git diff --check
```

第33-1新增自检后增加：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\validate-loop-artifacts.ps1
```

## 16. 风险与控制

| 风险 | 控制 |
| --- | --- |
| 过度平台化 | 只加 schema/template/reference/self-check，不加服务 |
| 运行态污染入库 | JSON 示例可入库，真实事件不入库 |
| 反思记忆污染规则 | 只沉淀稳定复现经验 |
| classifier 误判 | `unknown` 优先于错误归因 |
| 子智能体自评 | 实现角色不得给最终通过结论 |
| token 成本膨胀 | loop budget 限制 subagents/retries/repair rounds |
| 自动提交误伤 | local commit 继续默认 dry-run，不 push |

## 17. 通过/不通过裁决

### 通过

满足以下条件即通过：

1. loop state/event schema 存在且示例可解析。
2. repair/reflection 模板可渲染。
3. budget/rerun/role contract/forward-test references 存在。
4. self-check 脚本只读通过。
5. plugin manifest 仍通过校验。
6. 不引入 dashboard、数据库、MCP、常驻服务。

### 不通过

任一项出现即不通过：

1. 把真实 run 日志或 backlog 复制进插件。
2. 自动修改项目业务代码。
3. 默认执行 commit/push。
4. classifier 把不确定编译错误直接判真实质量。
5. Skill 膨胀成论文综述，影响可执行性。

## 18. 一句话结论

第33条已经把 AutoPilot 封装成可运行插件；第33-1要做的不是继续堆功能，而是把插件升级成真正的 Loop Engineering harness：有状态、有事件、有预算、有补修协议、有反思回填、有前向验证，同时保持最小、只读优先、不接管项目事实源。

## 19. 参考来源

1. Addy Osmani, Loop Engineering: https://addyosmani.com/blog/loop-engineering/
2. ReAct, Synergizing Reasoning and Acting in Language Models: https://arxiv.org/abs/2210.03629
3. Reflexion, Language Agents with Verbal Reinforcement Learning: https://arxiv.org/abs/2303.11366
4. Self-Refine, Iterative Refinement with Self-Feedback: https://arxiv.org/abs/2303.17651
5. SWE-agent, Agent-Computer Interfaces Enable Automated Software Engineering: https://arxiv.org/abs/2405.15793
6. Voyager, An Open-Ended Embodied Agent with Large Language Models: https://arxiv.org/abs/2305.16291
7. MetaGPT, Meta Programming for A Multi-Agent Collaborative Framework: https://arxiv.org/abs/2308.00352
