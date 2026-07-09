# 第33-2条主线 Codex AutoPilot 真实Loop执行器与分类器增强收口报告

日期：2026-07-09
主线：第33-2条主线 Codex AutoPilot 真实Loop执行器与分类器增强
类型：正式质量报告 / 收口报告 / 主线程最终裁决依据
结论：通过 / 非阻塞

本报告只裁决第33-2条主线是否完成“插件内真实 loop runner 接入 + 精细化 failure classifier 增强”的最小收口，不扩成 dashboard、MCP、数据库、常驻服务或默认 commit/push 主线。

## 1. 正式交付物

本轮交付以 `plugins/cgc-pms-autopilot/artifacts/plans/第33-2条主线-Codex AutoPilot真实Loop执行器与分类器增强任务计划书.md` 与 `plugins/cgc-pms-autopilot/**` 当前 diff 为准。

已核对交付物如下：

1. 新增 `plugins/cgc-pms-autopilot/scripts/autopilot-loop-runner.ps1`，提供 `select -> checkpoint -> plan handoff -> act handoff -> observe -> classify -> repair-request -> verify -> closeout -> learn -> next` 的受控 dry-run/preview 执行入口。
2. 新增 `plugins/cgc-pms-autopilot/schemas/classification-result.schema.json`，把分类结果收敛为稳定的结构化契约。
3. 新增 `plugins/cgc-pms-autopilot/references/classifier-rules.md`，把分类规则、建议动作与 retry policy 固定为单一规则源。
4. 新增 `plugins/cgc-pms-autopilot/templates/loop-run-report.md`，提供 loop runner 预览报告模板。
5. 新增 `plugins/cgc-pms-autopilot/examples/classification-*.json` 四个示例，用于环境前置、preview、Ready 配置和真实质量分类样例。
6. 增强 `plugins/cgc-pms-autopilot/scripts/test-failure-classifier.ps1`，从粗粒度四大类升级为 `category/subcategory/confidence/evidence/suggestedNextAction/retryPolicy/reason` 结构化输出。
7. 增强 `plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1`，补充 classification assets、runner、template 与 schema/example 一致性校验。
8. 更新 `plugins/cgc-pms-autopilot/scripts/local-commit-closeout.ps1`、`references/forward-test-scenarios.md`、`references/rerun-policy.md`、`references/role-contracts.md`、`skills/cgc-pms-autopilot-owner/SKILL.md`，把新 runner、分类字段和 closeout 边界纳入正式约束。

## 2. 验收证据

本轮按最小但足够支撑裁决的口径复核，结果如下：

| 验证项 | 当前结果 | 裁决 |
| --- | --- | --- |
| `python C:\Users\L1597\.codex\skills\.system\plugin-creator\scripts\validate_plugin.py D:\projects-test\cgc-pms\plugins\cgc-pms-autopilot` | 返回 `Plugin validation passed` | 采信 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\validate-loop-artifacts.ps1` | 返回 `ok=true`，`missing=[]`，`invalidJson=[]`，`schemaMismatches=[]` | 采信 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1 -ErrorText "ParserError unexpected argument" -ExitCode 1` | 返回 `tool_config / powershell_parser_error / no_retry` | 采信 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1 -ErrorText "ECONNREFUSED 172.19.0.8:8080 vite proxy" -ExitCode 1` | 返回 `environment_prereq / vite_proxy_stale_backend / refresh_frontend_runtime / rerun_after_refresh` | 采信 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\autopilot-loop-runner.ps1 -DryRun -AllowSyntheticIssue -Scenario classify -ErrorText "ECONNREFUSED 172.19.0.8:8080 vite proxy"` | runner 在 checkpoint 检出 `stop.flag present` 与 `enabled.flag missing` 后短路为 `decision=stop`，未越过 owner/stop 边界 | 采信 |
| `powershell -NoProfile -ExecutionPolicy Bypass -Command "& 'plugins/cgc-pms-autopilot/scripts/local-commit-closeout.ps1' -IssueId 'READY-33-2-M5' -ExpectedPaths 'plugins/cgc-pms-autopilot' -DryRun"` | 返回 `diffCheckPassed=true`、`unexpectedPaths=[]`、`willCommit=false` | 采信 |
| `git -c core.autocrlf=false -c core.safecrlf=false diff --check -- plugins/cgc-pms-autopilot` | 通过，无格式阻塞输出 | 采信 |

补充说明：

1. `autopilot-loop-runner.ps1` 在 stop 状态下主动短路，证明它遵守项目约定的 `stop.flag / enabled.flag` 护栏，而不是绕过主线程继续跑下去。
2. classifier 的结构化结果已能直接映射到 refresh、ready 修正、repair-request 或 blocked 等后续动作，满足第33-2计划书要求的最小决策粒度。
3. `local-commit-closeout.ps1` dry-run 已验证“只允许插件目录与计划书路径、且不默认真实 commit”的收口边界。

## 3. 补修过程

本轮归档过程中识别到一处非质量型误报，并已按最小方式排除：

1. 首次调用 `local-commit-closeout.ps1` 时把 `-ExpectedPaths` 写成单个逗号拼接参数，导致输出把全部合法变更误列为 `unexpectedPaths`。
2. 该问题属于 PowerShell 调用方式错误，不是插件脚本或交付物质量失败。
3. 按脚本真实参数形态复跑为 `-ExpectedPaths 'plugins/cgc-pms-autopilot'` 后，结果恢复为 `unexpectedPaths=[]`，因此不构成阻塞。

## 4. 阻塞解除

本主线原始缺口有两项，当前均已解除：

1. “只有 loop schema / harness，没有真实 runner 入口”的阻塞已解除：`autopilot-loop-runner.ps1` 已存在，且能输出 loop-run-report preview、checkpoint 决策和 next action。
2. “classifier 只有粗粒度四大类，无法支撑差异化后续动作”的阻塞已解除：schema、rules、examples 与脚本输出已对齐，能稳定表达子分类、建议动作和 retry policy。

仍保留的 stop 状态不是本主线阻塞，而是当前仓库 AutoPilot 停机边界；runner 正确遵守该边界。

## 5. 剩余风险

1. 当前 classifier 仍是规则表驱动的有限枚举，复杂混合错误或证据冲突场景仍会落到 `unknown` 或较保守分支，后续如要细粒度根因定位应另立任务。
2. 本轮验证覆盖 dry-run、schema/example、自检和 closeout 护栏，未在“未停机的连续真实 loop”场景下全链路执行 `observe -> classify -> repair-request -> closeout`；这属于当前 stop 状态下的边界保留，不构成本次阻塞。
3. `local-commit-closeout.ps1` 仍依赖调用方正确传递 PowerShell 数组参数；主线程或后续运维执行时需沿用本次已验证的调用方式。

## 6. 未纳入范围

以下内容明确不纳入第33-2通过口径：

1. dashboard、MCP、数据库、常驻服务或生产发布能力。
2. 主线程最终裁决自动化；runner 只给建议，不替代通过/不通过、阻塞/非阻塞裁决。
3. 默认真实 commit 或任何 push 动作。
4. 项目业务代码、部署链路、真实 backlog/quality/run 产物入插件。

## 7. 模型分配经验

本轮实际分档与计划书建议基本匹配，经验如下：

1. 规则/schema/正式归档这类结构化收敛任务，`gpt-5.4 / medium` 足够，重点是证据归并，不需要升到高推理。
2. classifier 脚本增强仍然适合高于普通文档档位；原因不是代码量，而是误分类会直接影响后续动作路由。
3. loop runner 的价值在于边界控制而不是多写逻辑；后续如扩到真实连续执行，仍应保持实现型与验收型分角色情况，不宜让单一长跑子智能体包办。
4. 像 `local-commit-closeout` 这类运维/收口命令，最容易出现的是调用方式误报，先做失败分类再决定是否升级为代码问题是对的。

## 8. 最终建议

建议主线程判定第33-2条主线通过，理由如下：

1. 计划书要求的核心交付物已齐备，且全部保持在 `plugins/cgc-pms-autopilot/**` 的最小增量边界内。
2. 结构化 classifier 已从“能粗分类”提升到“能给下一步动作建议”，满足真实 loop runner 消费的最低要求。
3. loop runner 已具备选择、checkpoint、分类、补修预览、closeout 预览和 next 决策骨架，并且能在 `stop.flag`/`enabled.flag` 护栏下正确短路。
4. 自检、插件校验、分类样例、closeout dry-run 与 `git diff --check` 均通过，没有发现阻塞性质量缺口。

综合判断：第33-2可作为“真实Loop执行器与分类器增强”的正式收口版本通过；后续若要继续做无停机真实长跑、多轮 repair 自动回流或更细分类规则，应另立新主线，不应回灌进本次通过口径。
