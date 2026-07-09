# 第33-2条主线-Codex AutoPilot真实Loop执行器与分类器增强任务计划书

**Goal:**
在第33条主线已完成插件 MVP、第33-1条主线已完成 loop schema/event/repair/reflection/budget/rerun/role/forward-test 底座的前提下，单独立一条最小可执行主线，把 `plugins/cgc-pms-autopilot/` 从“受控 loop harness”推进到“受控真实 loop 执行器 + 精细化 test-failure-classifier”；本主线只规划插件内执行器接入和分类规则扩展，不接管主线程裁决，不默认 push，不扩成 dashboard、MCP、数据库或生产发布主线。

**Architecture:**
继续沿用第33与第33-1确定的轻量插件结构：`Skill + PowerShell scripts + schemas + templates + references + examples`。本主线只在 `plugins/cgc-pms-autopilot/` 内增量增加真实 loop runner 入口、分类结果 schema、规则表、示例与报告模板，复用现有 `autopilot-checkpoint.ps1`、`test-failure-classifier.ps1`、`validate-loop-artifacts.ps1`、loop schemas 和 repair/reflection 模板；执行器只负责按受控状态机组织 `select -> checkpoint -> handoff -> observe -> classify -> repair-request -> verify -> closeout -> learn -> next`，不替代项目总负责人裁决，不修改项目业务规则，不默认执行本地 commit 之外的 Git 动作，更不做 push。

## 1. 结论先行

第33-2应单独成线，且只做两个缺口：

1. 把现有 loop harness 接成真实可跑的插件内 loop 执行器。
2. 把现有 `test-failure-classifier.ps1` 从四大类粗分类增强到可支撑自动 loop 决策的精细子分类。

最小可行原则：

1. 先做 dry-run 和只读链路，再接入有限写动作。
2. 先做规则表和结构化输出，再让 loop runner 消费分类结果。
3. 不新建平台，不引入服务，不把项目真实 backlog/quality/run 产物搬进插件；本主线自有计划书与收口报告归档到 `plugins/cgc-pms-autopilot/artifacts/**`。

## 2. 背景

### 2.1 已有基础

第33条主线已完成插件 MVP，已交付：

1. 插件目录、manifest、owner skill、checkpoint/render/closeout/classifier/local-commit 等脚本。
2. 主计划、ready/done/blocked/quality/iteration/run-summary 等模板。
3. 基础 references/examples。

第33-1条主线已完成 loop engineering 底座，已交付：

1. `loop-state.schema.json`、`loop-event.schema.json`。
2. `repair-request.md`、`reflection-entry.md`。
3. `loop-budget-policy.md`、`rerun-policy.md`、`role-contracts.md`、`forward-test-scenarios.md`。
4. `validate-loop-artifacts.ps1` 与对应 examples。

### 2.2 当前剩余缺口

当前插件仍有两块空白：

1. 有 loop schema，但缺少真正串起 `select/observe/classify/repair/next` 的真实 loop 执行器。
2. 有 classifier，但粒度仍偏粗，难以支持自动复跑、环境刷新、Ready 配置修正、真实质量阻塞这类差异化后续动作。

### 2.3 本主线定位

这不是 dashboard 主线，不是 MCP 主线，不是数据库主线，也不是生产发布主线。

这是插件内受控执行器和 classifier 规则扩展主线，目标是让 AutoPilot 插件具备“能跑一轮受控 loop，并且能更稳地判断下一步该做什么”的能力。

## 3. 目标与非目标

### 3.1 目标

1. 新增真实 loop runner 脚本入口，按固定 phase 组织一轮插件内 loop。
2. 扩展 classifier 输出结构，支持 `category/subcategory/confidence/evidence/suggestedNextAction/retryPolicy`。
3. 把 loop runner 与现有 checkpoint、classifier、repair-request、closeout、reflection 模板接起来。
4. 保持所有高风险动作显式受控：不替代主线程裁决、不默认 push、不默认自动提交真实结果。
5. 补齐 forward-test 与正式质量报告所需的最小模板和示例。

### 3.2 非目标

1. 不接入 dashboard 或任何可视化控制台。
2. 不接入 MCP 可执行工具市场或远程调度。
3. 不新增数据库、消息队列或常驻服务。
4. 不改项目业务代码、部署链路或生产发布流程。
5. 不把插件变成主线程裁决者。
6. 不默认自动 push。

## 4. 真实 Loop 执行器功能范围

目标状态机：

```text
select -> checkpoint -> plan handoff -> act handoff -> observe -> classify -> repair-request -> verify -> closeout -> learn -> next
```

各 phase 定义如下：

1. `select`
   从 Ready 队列、blocked 恢复条件或显式输入中选择当前 issue；若无合格 issue，只输出停止原因，不进入后续 phase。
2. `checkpoint`
   复用 `autopilot-checkpoint.ps1`，检查 `branch/git status/stop.flag/pause.flag/enabled.flag` 以及可选 health gate。
3. `plan handoff`
   生成给 A 或主线程可消费的最小计划摘要，包含目标、范围、禁止事项、验收命令和风险。
4. `act handoff`
   生成给 B/C 的最小执行指令或 dry-run 指令，不在 runner 内直接实现业务改动。
5. `observe`
   收集命令结果、结构化 diff 摘要、测试/构建/页面证据，不保存大段原始日志。
6. `classify`
   调用精细化 `test-failure-classifier.ps1`，产出分类结果并决定后续策略。
7. `repair-request`
   当结果未通过但仍可补修时，渲染 `repair-request.md` 供后续子智能体消费。
8. `verify`
   组织最小复验链路，只验证本轮裁决所需项目。
9. `closeout`
   生成 done/blocked/quality/iteration/loop-run-report 摘要；本地 commit 仅支持 dry-run 或显式开启。
10. `learn`
   只沉淀稳定经验到 `reflection-entry` 风格产物，不保存 run id、临时日志名、子智能体昵称。
11. `next`
   基于 `stop/pause/ready/budget` 决定继续、阻塞、停止或等待裁决。

约束：

1. loop runner 不能替代主线程“通过/不通过、阻塞/非阻塞”的最终裁决。
2. loop runner 不默认 push。
3. loop runner 对真实提交动作必须显式 `-EnableLocalCommit` 或等价开关。

## 5. classifier 精细化功能范围

### 5.1 输出结构

精细化分类结果至少包含：

1. `category`
2. `subcategory`
3. `confidence`
4. `evidence`
5. `suggestedNextAction`
6. `retryPolicy`

### 5.2 目标分类层级

一级分类保持最小稳定集合：

1. `tool_config`
2. `environment_prereq`
3. `ready_issue_config`
4. `real_quality_or_security`
5. `unknown`

二级子分类至少覆盖：

1. `maven_test_compile`
2. `maven_surefire`
3. `test_selector_missing_or_invalid`
4. `powershell_parser_error`
5. `vite_proxy_stale_backend`
6. `dev_login_unreachable`
7. `docker_not_ready`
8. `backend_not_ready`
9. `frontend_not_ready`
10. `ready_issue_verification_config`
11. `real_test_failure`
12. `real_build_failure`
13. `real_permission_or_security_failure`

### 5.3 策略要求

1. `confidence` 只用有限档位，例如 `high|medium|low`。
2. `evidence` 只保留支持裁决的短摘要，不回填整段 stderr。
3. `suggestedNextAction` 必须是可执行短动作，如 `rerun_once`、`refresh_frontend_runtime`、`fix_ready_selector`、`open_repair_request`、`mark_blocked`。
4. `retryPolicy` 至少区分：
   - `no_retry`
   - `rerun_once`
   - `rerun_after_refresh`
   - `retry_after_ready_fix`
   - `manual_review_required`

## 6. 建议新增或修改文件

优先只改 `plugins/cgc-pms-autopilot/`，建议清单如下：

### 6.1 建议新增

1. `plugins/cgc-pms-autopilot/scripts/autopilot-loop-runner.ps1`
2. `plugins/cgc-pms-autopilot/references/classifier-rules.md`
3. `plugins/cgc-pms-autopilot/schemas/classification-result.schema.json`
4. `plugins/cgc-pms-autopilot/examples/classification-environment.example.json`
5. `plugins/cgc-pms-autopilot/examples/classification-ready-config.example.json`
6. `plugins/cgc-pms-autopilot/examples/classification-real-quality.example.json`
7. `plugins/cgc-pms-autopilot/templates/loop-run-report.md`

### 6.2 建议修改

1. `plugins/cgc-pms-autopilot/scripts/test-failure-classifier.ps1`
2. `plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1`
3. `plugins/cgc-pms-autopilot/references/forward-test-scenarios.md`
4. `plugins/cgc-pms-autopilot/references/rerun-policy.md`
5. `plugins/cgc-pms-autopilot/references/role-contracts.md`
6. `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`

原则：

1. 能复用现有文件就不新建同义资产。
2. 能在规则表中表达的，不硬编码到多个脚本里。
3. 示例只保留最小代表性案例，不堆大样本。

## 7. 阶段计划

### M1 classifier schema + 规则表

目标：

1. 定义 `classification-result.schema.json`。
2. 新增 `references/classifier-rules.md`，写清一级分类、二级子分类、证据片段和建议动作。

交付物：

1. `schemas/classification-result.schema.json`
2. `references/classifier-rules.md`
3. 最少 2 个 classification examples

验收命令：

```powershell
Get-Content plugins\cgc-pms-autopilot\schemas\classification-result.schema.json
Get-Content plugins\cgc-pms-autopilot\references\classifier-rules.md
```

回滚方式：

1. 删除新增 schema/rules，classifier 继续沿用旧四大类粗分类。

### M2 classifier 脚本精细化

目标：

1. 扩展 `test-failure-classifier.ps1` 支持子分类与结构化输出。
2. 输出 `category/subcategory/confidence/evidence/suggestedNextAction/retryPolicy`。

交付物：

1. 增强版 `scripts/test-failure-classifier.ps1`
2. `examples/classification-*.json`

验收命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1 -ErrorText "ParserError" -ExitCode 1
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1 -ErrorText "ECONNREFUSED 172.19.0.8:8080" -ExitCode 1
```

回滚方式：

1. 保留旧输出字段兼容层；新规则不稳定时退回一级分类。

### M3 loop runner dry-run/select/checkpoint

目标：

1. 新增 `autopilot-loop-runner.ps1`。
2. 先支持 `select/checkpoint/next` 和 dry-run。

交付物：

1. `scripts/autopilot-loop-runner.ps1`
2. `templates/loop-run-report.md`

验收命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\autopilot-loop-runner.ps1 -DryRun
```

回滚方式：

1. 删掉 runner，继续由人工串行调现有脚本。

### M4 observe/classify/repair 连接

目标：

1. runner 接入 `observe -> classify -> repair-request`。
2. classifier 结果直接驱动 rerun/refresh/repair/blocked 建议。

交付物：

1. runner 与 classifier 的连接逻辑
2. `repair-request` 联动说明
3. 更新 `forward-test-scenarios.md`

验收命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\autopilot-loop-runner.ps1 -DryRun -Scenario classify
```

回滚方式：

1. 保留 observe 与 classify 分离模式，repair-request 退回人工生成。

### M5 closeout/local commit dry-run 连接

目标：

1. runner 接入 closeout 与本地 commit dry-run。
2. 仍保持不默认真实 commit，不做 push。

交付物：

1. runner closeout 分支
2. `loop-run-report.md`
3. `local-commit-closeout.ps1` 接口约定补充

验收命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\autopilot-loop-runner.ps1 -DryRun -Scenario closeout
git diff --check
```

回滚方式：

1. closeout 继续由 F 手工或现有 closeout 脚本执行。

### M6 forward-test 场景和质量报告

目标：

1. 补齐真实 loop runner 的 forward-test。
2. 输出正式质量报告模板或摘要口径。

交付物：

1. 更新 `references/forward-test-scenarios.md`
2. `templates/loop-run-report.md`
3. 正式质量报告示例或验收指引

验收命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\validate-loop-artifacts.ps1
git status --short
```

回滚方式：

1. forward-test 不稳定时冻结在 M5，只保留 dry-run runner 与精细化 classifier。

## 8. 每阶段交付物、验收与回滚总表

| 阶段 | 主要交付物 | 核心验收 | 回滚方式 |
| --- | --- | --- | --- |
| M1 | classification schema、rules、examples | schema/rules 可读且字段闭环 | 回退到旧粗分类 |
| M2 | 增强版 classifier 脚本 | 子分类输出稳定、兼容旧一级分类 | 仅保留一级分类输出 |
| M3 | loop runner dry-run/select/checkpoint | dry-run 可跑、不会越权写入 | 回到人工串脚本 |
| M4 | observe/classify/repair 连接 | 能生成 repair-request 或阻塞建议 | classify 与 repair 分离 |
| M5 | closeout/local commit dry-run 连接 | closeout 可产出、仍不默认 commit/push | 回到现有 closeout 流程 |
| M6 | forward-test 与质量报告 | 自检和场景复验通过 | 冻结在 M5 范围 |

## 9. 风险控制

1. 误分类
   控制：保留 `unknown`，低置信度时禁止自动推进到补修或阻塞结论。
2. 误执行
   控制：runner 默认 dry-run；写动作必须显式开关。
3. 越权提交
   控制：本地主提交只支持 dry-run 或显式开启；永不默认 push。
4. 真实产物入插件
   控制：rules/examples/templates 只放示例与模板，不放真实 backlog/quality/run 产物。
5. 忽略 stop/pause
   控制：`checkpoint` 和 `next` 都必须检查 `stop.flag/pause.flag/enabled.flag`。
6. token/时间预算膨胀
   控制：复用第33-1 budget policy，限制 subagents、retries、repair rounds、wall time。
7. 规则表与脚本分叉
   控制：分类规则优先单源写在 `classifier-rules.md`，脚本只消费结构化规则。

## 10. 模型与子智能体分工建议

### 10.1 推荐分工

1. A 规则/架构子智能体
   负责 schema、classifier-rules、owner skill 约束更新。
2. C 脚本实现子智能体
   负责 `autopilot-loop-runner.ps1` 和 `test-failure-classifier.ps1` 增量实现。
3. D 验收子智能体
   负责 dry-run、分类样例、forward-test、`git diff --check`。
4. F 归档子智能体
   负责质量报告、计划书回填、示例与模板口径收口。

### 10.2 推荐模型档位

| 任务 | model | thinking | reason |
| --- | --- | --- | --- |
| classifier schema/rules 设计 | `gpt-5.4` | `medium` | 规则收敛为主，复杂度中等 |
| classifier 脚本增强 | `gpt-5.5` | `medium` | 需要排除式判断，误分类风险较高 |
| loop runner 实现 | `gpt-5.5` | `high` | 涉及多阶段状态流转与边界控制 |
| dry-run/forward-test 验收 | `gpt-5.5` | `medium` | 结果将直接影响通过/不通过裁决 |
| 文档/质量归档 | `gpt-5.4` | `medium` | 结构化收口，不涉及高风险实现 |

## 11. 通过/不通过裁决标准

### 11.1 通过

满足以下条件可判通过：

1. 第33-2计划书首段同时包含 `Goal` 与 `Architecture`。
2. 明确写清这不是 dashboard/MCP/数据库/生产发布主线。
3. 覆盖真实 loop 执行器 phase、classifier 精细化、建议文件、分阶段计划、风险控制、模型分工。
4. 明确“不替代主线程裁决、不默认 push”。
5. 保持最小可行，不把范围扩成新平台。

### 11.2 不通过

出现以下任一项即不通过：

1. 把主线写成通用平台重构或生产编排中心。
2. 未覆盖 classifier 子分类与结构化输出。
3. 未覆盖 `select -> ... -> next` 真实 loop phase。
4. 未写清 stop/pause、预算、越权提交等控制边界。
5. 计划书把真实项目产物或一次性 run 信息写入插件长期资产。

## 12. 一句话结论

第33-2条主线应按“先补 classifier 结构化规则，再接 dry-run loop runner，最后接 closeout 与 forward-test”的顺序推进，用最小增量把第33与第33-1的底座真正接成受控可执行 loop，而不是继续扩成新平台。
