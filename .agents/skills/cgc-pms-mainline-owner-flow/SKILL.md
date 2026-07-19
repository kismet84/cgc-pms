---
name: cgc-pms-mainline-owner-flow
description: Use when the user explicitly requests cgc-pms mainline, Backlog or AutoPilot governance, formal acceptance/release adjudication, or cross-module closeout.
---

# cgc-pms 主线总负责人流转

版本=2
最后复核日期=2026-07-14
规则来源=`AGENTS.override.md`、`AGENTS.md`
通用执行协议=`docs/standards/codex-task-execution-policy.md`
动态事实来源=`scripts/codex-autopilot/codex-autopilot.config.json`
AutoPilot 行为契约=`plugins/cgc-pms-autopilot/references/control-plane-policy.md`
验证入口=`scripts/codex-autopilot/test-mainline-owner-flow.ps1`

## 适用范围

只在以下场景使用：

- 用户明确要求按主线、Backlog 或 AutoPilot 治理流程推进。
- 用户要求正式验收、上线裁决或跨模块收口。
- 当前任务需要把多个阶段或多个正式载体统一归档、裁决。

普通问答、审查、单文件修复或已授权的普通交互实施，不因出现“计划”或“实施”字样自动升级为本流程。

## 启动门

1. 完整读取仓库根 `AGENTS.override.md`、`AGENTS.md` 与通用执行协议；本 Skill 不覆盖更高优先级规则，也不复制通用协议全文。
2. 先判断用户是否授权修改、实施、运行测试、Git 操作或运行环境变更；未授权时只分析、计划或验收。
3. 任何状态变更前，实际执行者核对：
   - `git branch --show-current`
   - `git status --short`
   - 仅在怀疑并行隔离或分支归属冲突时补 `git worktree list`
4. 明确主线五要素：目标、范围、非目标、验收标准、风险与回滚边界。
5. 按通用协议声明当前粘性模式；主线中断、阶段切换或长等待前生成任务恢复胶囊。

## 计划与路由

需要计划书时写入 `docs/plans/第N条主线-<主题>任务计划书.md`，首段包含 `**Goal:**` 与 `**Architecture:**`。

按风险和范围选择计划深度：

- 轻量：单模块、低风险，保留目标、架构、范围/非目标、任务、验收、风险/回滚。
- 标准：跨模块或控制面变更，增加阶段门、验收矩阵、文件范围和失败分类。
- 高风险：权限、金额、租户、数据一致性或状态机，增加风险分析、故障注入、恢复矩阵与金丝雀。

授权通过后由主线程直接执行。执行方案至少考虑风险、耦合、上下文传递成本和独立证据需要。不要机械打分。

## 阶段控制

在以下节点重新评估执行方案和证据强度：

- 一个阶段完成并进入下一阶段前。
- 首次出现阻塞或不通过后。
- 从实现切换为验收、审计或上线裁决时。
- 用户新增约束、缩小范围或提高验收标准时。

涉及 AutoPilot 时只读取专项行为契约、配置和实际 Schema；本 Skill 不维护评分版本、权重、回顾阈值、模型、超时或状态机字段副本。控制面行为变化必须更新策略指纹覆盖并通过相应金丝雀门禁。

工具路由、失败分类、分层验证、Git 生命周期和 commentary 触发条件统一读取通用执行协议。阶段内只有首选路径失败、参数纠正、环境恢复或出现新证据时才切换/复跑；上位规则要求的交叉核验不受默认备用路径数量限制。

## 验收与零悬空收口

主线程负责最终裁决，只采信正式交付物、绑定的验证证据、Git 状态和正式问题载体。临时日志、截图名、run id 和会话草稿不得写入长期规则。

每个发现项只能归入：本轮修复并复验、超出范围并正式承接、证据不足或无价值而关闭。存在口头后续或无唯一载体的问题时不得判定通过。

收口统计新增后续项、关闭后续项和后续项净变化，并核对剩余风险与回滚条件。

## 按任务类型输出

| 任务类型 | 最小输出 |
| --- | --- |
| 分析 | 结论、证据、待确认项 |
| 计划 | 正式计划书、范围、验收、实施前置、是否可进入实施 |
| 实施 | 修改内容、验证结果、Git 状态、剩余风险 |
| 验收 | 通过/不通过、阻塞/非阻塞、依据、剩余风险 |
| 上线裁决 | 是否可上线、回滚条件、数据与环境风险 |

## 维护检查

修改本 Skill 时必须验证：

- 引用文件存在，计划入口可执行。
- 通用执行协议可读，且本 Skill 没有复制其动态或通用流程全文。
- 没有复制配置中的动态值。
- 没有与 `AGENTS.override.md` 或 `AGENTS.md` 冲突。
- 行为性修改是否需要更新 AutoPilot 策略指纹和单 Issue 金丝雀。
