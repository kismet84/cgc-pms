# 第31条主线-本地AutoPilot完整无人值守闭环任务计划书

**Goal:**
在现有 `scripts/codex-autopilot/`、`docs/backlog/`、`docs/iterations/` 与 `AGENTS.override.md` 规则基础上，系统性整理本地 AutoPilot 达到“完整无人值守闭环”前的全部关键风险，分阶段补齐最小可落地能力，使其能够在本地测试环境中稳定完成 Ready Issue 选择、前置校验、执行加锁、结果归档、异常收口、停止/暂停控制与下一轮决策，而不引入大平台、常驻 daemon、生产发布或生产数据库连接。

**Architecture:**
沿用当前“PowerShell 控制脚本 + Codex 会话执行 + backlog/iteration/quality 文档归档 + `.codex-autopilot` 状态目录”的轻量架构，不新建中心化调度平台；首批只补最小闭环骨架：`ready-lint`、`result.json`、`run.lock`、`executor`、`worktree/分支隔离规则`、`JSONL` 运行日志、`status/explain` 增强，以及与 `ready-issues.md`/`current-focus.md`/`AGENTS.override.md` 对齐的门禁与回滚策略。禁止扩展为长期常驻服务、外部任务编排平台、生产运维平台或通用 Agent 平台。

## 1. 计划背景

当前仓库已具备本地 AutoPilot 第一轮治理框架：

- 已有控制脚本：`autopilot-start.ps1`、`stop`、`pause`、`resume`、`status`、`kill`。
- 已有连续执行入口：`autopilot-run-continuous.ps1`，支持 `DryRun`、`ApplyBacklogSplit`、`ExplainNextAction`、`MaxIterations`。
- 已有基础配置：`codex-autopilot.config.json`，包含 `autoMerge=true`、`autoPush=false`、`maxIssuesPerRun=1`、`worktreeRoot`、`autopilotDir` 等。
- 已有规则约束：`AGENTS.override.md` 已明确 Ready Issue 来源、health gate、stop/pause checkpoint、环境前置分类、测试数据重置边界与连续执行触发协议。
- 已有正式归档：`docs/plans/cgc-pms-local-codex-autopilot-plan.md` 与 `docs/iterations/iteration-2026-07-08-report.md`。

但当前体系仍属于“可运行的第一轮框架”，尚未达到“完整无人值守闭环”。主要原因不是缺入口，而是缺少把错误执行、脏任务、并发冲突、结果不可追溯、异常恢复不稳这几类风险彻底收口的中间层能力。

## 2. 当前系统现状

### 2.1 已存在能力

1. 通过 flag 文件控制启停、暂停、恢复与软停止。
2. 通过 `autopilot-run-continuous.ps1` 做 Ready Issue 发现、空队列拆单、迭代上限控制与 `ExplainNextAction` 说明。
3. 通过 `state.json` 记录基础状态字段，如 `status`、`lastAction`、`lastIssue`、`iterationCompleted`。
4. 通过 `ready-issues.md` 与 `current-focus.md` 管理执行队列与拆单来源。
5. 通过 `docs/iterations/`、`docs/quality/` 保留正式结论。
6. 通过 `test-continuous-runner.ps1` 覆盖连续 runner 的部分脚本行为。

### 2.2 已知现状结论

1. 现有 runner 更像“选题器 + backlog 拆单器”，不是完整执行器。
2. 现有 `status` 能看基础状态，但不够支撑无人值守排障、追责与断点恢复。
3. 现有连续执行依赖文档质量与会话纪律，缺少机器可判定的结构化结果。
4. 现有 worktree 只在配置层出现，缺少“何时创建、何时复用、何时清理、何时退回主工作区”的刚性策略。
5. 现有正式报告能做归档，但缺少统一的单轮结果对象与最小机读摘要。

## 3. 所有已识别风险问题

## 3.1 队列与任务质量风险

1. `ready-issues.md` 目前主要靠人工格式纪律维持，缺少独立 `ready-lint`，存在字段缺失、验证命令失真、允许/禁止范围不完整时仍被选中的风险。
2. 拆单产物虽然可写入 Ready 队列，但缺少“可执行性评分”或“硬门禁校验”，容易形成形式上 Ready、实际上不可执行的任务。
3. 当前对 Ready Issue 的验证命令存在性只在执行中临时判断，缺少执行前统一预检。

## 3.2 并发与重复执行风险

1. 目前缺少明确 `run.lock` 机制，连续执行、人工触发、自动化唤醒之间可能并发撞车。
2. `state.json` 更偏“状态快照”，不是互斥锁；不能阻止双实例重入。
3. Stop/Pause 语义已定义，但缺少“锁持有者 + 心跳超时 + 僵尸锁回收”设计。

## 3.3 执行收口风险

1. 当前 runner 遇到 Ready Issue 时输出 `READY_ISSUE_FOUND` 后即结束，未内建统一 executor 收口协议。
2. 缺少结构化 `result.json`，导致“通过/阻塞/失败分类/产物路径/剩余风险”仍依赖自然语言报告，机器无法稳定衔接下一轮。
3. 缺少统一的“本轮是否计入已完成迭代”的裁定规则，容易出现重复计数或漏计数。

## 3.4 可观测性与追溯风险

1. 当前 `state.json` 不足以重放单轮执行过程。
2. 缺少统一 JSONL 日志，无法稳定记录 checkpoint、分类判断、执行人、命令摘要、产物摘要与最终结论。
3. `status` 输出适合人工看，不适合审计或程序消费。
4. `ExplainNextAction` 已有，但缺少“为什么不能执行”“缺哪个门禁”“下一步需要谁补什么”的结构化解释。

## 3.5 环境与隔离风险

1. worktree 只在配置和旧方案里有提法，缺少最小落地策略，容易把实施型任务直接落在主工作区。
2. 文档/审计类任务不需要 worktree，实施型任务可能需要；当前没有明确分档。
3. 当前缺少“工作区脏状态、错误分支、worktree 冲突”的执行前强拦截器。

## 3.6 测试与数据边界风险

1. 规则层已定义 dev/test/demo 与 `ALLOW_TEST_DATA_RESET`，但缺少统一 executor 前置校验收口。
2. health gate、runtime refresh、等待 `180秒` 已写规则，但尚未形成统一执行模板与结果结构。
3. 失败分类已有规范，但没有机读落盘，后续无法基于历史失败类型自动决策。

## 3.7 自动合并与回滚风险

1. 当前 `autoMerge=true` 只是配置开关，不等于闭环已安全。
2. 缺少“合并前结果摘要 + Git 状态摘要 + rollback 点”三件套。
3. blocked/WIP stash 规则已在文档中出现，但缺少固定字段和固定产物位置。

## 3.8 组织与角色风险

1. 主线程/子智能体边界在规则中很强，但执行器尚未把这种边界模板化。
2. 缺少“实施型 / 验收型 / 运维型 / 审计归档型”自动分档建议输出。
3. 缺少模型与推理强度的执行前记录，导致事后难以复盘“该轮为什么失败”。

## 4. 目标状态

达到“完整无人值守闭环”时，系统至少满足以下标准：

1. 只从合格 Ready Issue 中选题，且选题前完成 `ready-lint`。
2. 任意时刻只有一个有效执行实例持有 `run.lock`。
3. 单轮执行开始、进行中、结束后都有 JSONL 事件可追溯。
4. 单轮结束必产出 `result.json`，并明确：
   - `status=done/blocked/failed/noop`
   - `failureCategory`
   - `issueId`
   - `artifacts`
   - `gitSummary`
   - `nextAction`
5. `status` 命令可读出当前锁、状态、最近一轮结果与剩余迭代数。
6. `ExplainNextAction` 可解释“为什么当前继续/停止/拆单/阻塞”。
7. 实施型任务有明确工作区隔离策略，文档型任务默认不创建 worktree。
8. 任一失败都能安全收口，不把模糊中间态留给下一轮。
9. 不自动发布生产，不连接生产数据库，不越过现有测试环境边界。

## 5. 非目标

以下内容明确不纳入本主线首批范围：

1. 不引入常驻 daemon、Windows 服务、消息队列、外部编排平台。
2. 不建设通用 Agent 平台、任务中心、可视化大盘。
3. 不处理业务域 Ready Issue 本身的实现闭环，本主线只治理 AutoPilot 机制。
4. 不改生产部署链路，不接生产数据库，不自动发布生产。
5. 不把一次性 run id、临时日志路径、截图名写成长期规则。
6. 不为文档类任务强制引入 worktree。

## 6. 总体架构方案

## 6.1 最小闭环组件

1. `ready-lint`
   - 作用：在选题前校验 Ready Issue 结构、状态、范围、验收标准、验证命令与来源锚点。
   - 产物：`ready-lint.result.json`

2. `run.lock`
   - 作用：保证同一时刻只有一个执行器实例推进无人值守轮次。
   - 内容：`owner`、`pid/session`、`startedAt`、`heartbeatAt`、`mode`、`issueId`

3. `executor`
   - 作用：把“选题成功”推进为“真正执行一轮并产出结构化结果”。
   - 职责：前置检查、派工模板拼装、结果回收、结论归档、状态推进。

4. `result.json`
   - 作用：作为单轮唯一机读结论。
   - 内容：执行类型、issue、失败分类、正式交付物、临时产物、Git 摘要、结论、阻塞、剩余风险、下一动作。

5. `JSONL` 日志
   - 作用：按事件顺序记录 `checkpoint -> decision -> execute -> collect -> close`。
   - 原则：只记结构化关键事实，不记大段冗余 stdout。

6. `status/explain` 增强
   - `status`：查看当前锁、状态、最近一轮结果摘要、是否可继续。
   - `ExplainNextAction`：给出明确下一步与停止原因，不再只有宽泛状态字样。

7. `worktree/分支隔离策略`
   - 实施型任务：按需要进入独立 worktree。
   - 审计/文档/归档任务：默认主工作区，不引入额外隔离。

## 6.2 运行链路

```text
start/continuous trigger
  -> health + stop/pause + enabled 检查
  -> ready-lint
  -> 获取 run.lock
  -> 选中 Ready Issue 或拆单
  -> executor 执行一轮
  -> 写 JSONL 事件
  -> 写 result.json
  -> 更新 state/status
  -> 判断 done/blocked/noop/stop
  -> 释放 run.lock
  -> 决定是否进入下一轮
```

## 7. 阶段拆分

建议按 6 个阶段推进，每阶段都必须独立验收，不做一口气大改。

## 7.1 M0：基线盘点与规则对齐

### 阶段目标

把现有脚本、旧计划、AGENTS 规则、backlog 模板与连续执行入口统一成一个正式基线，明确哪些已存在、哪些缺口未补。

### 阶段任务

1. 盘点 `scripts/codex-autopilot/` 现有能力与缺口。
2. 固化现有 `status/state/continuous` 的字段基线。
3. 补一份“无人值守闭环字段字典”。
4. 明确哪些任务必须 worktree、哪些任务禁止 worktree。

### 验收标准

1. 有正式基线文档。
2. 现有脚本/文档/规则之间无明显冲突口径。
3. `ready-lint`、`run.lock`、`result.json`、`executor`、`JSONL` 被明确列为首批缺口。

## 7.2 M1：Ready 队列质量门禁

### 阶段目标

上线 `ready-lint`，把“可执行 Ready Issue”从人工约定提升为机器门禁。

### 阶段任务

1. 新增 `ready-lint` 脚本或最小校验器。
2. 校验字段至少包含：
   - `状态`
   - `目标`
   - `允许修改`
   - `禁止修改`
   - `验收标准`
   - `验证命令`
   - `来源锚点`
3. 对验证命令做存在性预检。
4. 对明显越界项做阻断，如缺来源、无验证命令、状态非 Ready。

### 验收标准

1. 不合格 Ready Issue 无法进入执行器。
2. `ExplainNextAction` 能说明 lint 失败原因。
3. lint 结果可写入结构化 JSON。

## 7.3 M2：单实例执行与断点安全

### 阶段目标

用 `run.lock` 解决并发重入与僵尸执行问题。

### 阶段任务

1. 设计 `run.lock` 文件结构。
2. 增加锁获取、心跳、释放与超时回收规则。
3. 让 `status` 输出锁摘要。
4. 让 `stop/pause/kill` 对锁状态有一致解释。

### 验收标准

1. 双触发时只有一个实例继续。
2. 异常退出后能识别僵尸锁。
3. 不需要引入守护进程也能完成锁回收。

## 7.4 M3：Executor 与结构化结果

### 阶段目标

把当前“发现 Ready Issue 后退出”补成“完成一轮并产出结果对象”。

### 阶段任务

1. 增加最小 executor 包装层。
2. 统一一轮执行的输入：
   - issue 元数据
   - 当前分支/工作区状态
   - 模型/推理强度建议
   - 前置门禁结果
3. 统一一轮执行的输出：
   - `result.json`
   - 正式交付物路径
   - Git 摘要
   - 阻塞分类
   - 剩余风险
4. 明确 `done/blocked/failed/noop` 四种收口状态。

### 验收标准

1. 每轮必有 `result.json`。
2. 下一轮决策不再依赖自由文本解析。
3. blocked 与 failed 有清晰区分。

## 7.5 M4：JSONL 日志与状态可观测性

### 阶段目标

让单轮过程可追溯、可解释、可审计。

### 阶段任务

1. 为关键 checkpoint 追加 JSONL 事件。
2. `status` 输出最近一轮摘要，而非只看 `state.json`。
3. `ExplainNextAction` 返回固定字段：
   - `nextAction`
   - `stopReason`
   - `missingGate`
   - `selectedIssue`
   - `shouldSplitBacklog`
4. 控制日志粒度，避免把大段原始输出写成长久资产。

### 验收标准

1. 单轮从开始到结束可通过 JSONL 重建关键事实。
2. 排障不需要翻大量自然语言会话。
3. 不把临时日志误归档进 `docs/quality/`。

## 7.6 M5：隔离策略、回滚与无人值守准入

### 阶段目标

补齐 worktree 使用规则、回滚策略与最终裁决标准，使系统达到“允许无人值守长跑”的准入线。

### 阶段任务

1. 固化任务分档与 worktree 策略：
   - 实施型：可独立 worktree
   - 验收型：通常主工作区只读
   - 运维型：主工作区或固定运维工作区
   - 审计归档型：默认主工作区
2. 固化 blocked/WIP stash 与 rollback 点记录。
3. 建立“无人值守准入清单”。
4. 建立“暂停/停止后的自然收口模板”。

### 验收标准

1. 实施型任务不会默认污染主工作区。
2. blocked 收口有固定恢复入口。
3. 可以明确给出“允许/不允许进入完整无人值守模式”的裁决。

## 8. 每阶段任务分解建议

## 8.1 文档与规则类任务

1. 字段字典与状态机字典整理
2. `result.json` schema
3. JSONL event schema
4. worktree 使用规则
5. rollback/checkpoint 模板

## 8.2 脚本类任务

1. `ready-lint.ps1` 或同等最小入口
2. `run.lock` 获取/释放逻辑
3. `status` 增强
4. `ExplainNextAction` 结构化增强
5. `executor` 包装器

## 8.3 验证类任务

1. 连续 runner 新增脚本级回归
2. `ready-lint` 通过/失败样例
3. 锁冲突、锁超时、stop/pause 收口样例
4. `result.json` 与 JSONL 样例验证

## 9. 验收标准

## 9.1 单阶段通用验收标准

1. 有明确正式交付物。
2. 有最小脚本/文档验证。
3. `git diff --check` 通过。
4. 不引入超出阶段范围的新平台或新守护进程。

## 9.2 全主线最终验收标准

1. 具备 `ready-lint`。
2. 具备 `run.lock`。
3. 具备 `result.json`。
4. 具备 JSONL 事件日志。
5. 具备增强版 `status/explain`。
6. 具备 worktree/主工作区分档策略。
7. 具备 blocked/WIP/rollback 固定模板。
8. 能对“下一轮是否可继续”给出机读结论。

## 10. 风险控制

1. 每阶段只做一个最小闭环，不并发扩展到业务 Issue。
2. 先补机读门禁，再补执行器，避免“会跑但不可控”。
3. 先补锁，再谈连续长跑，避免双实例破坏工作区。
4. 先补结果对象，再谈自动决策，避免下轮误判。
5. worktree 只用于有真实隔离收益的实施型任务，避免把简单任务复杂化。

## 11. 回滚策略

1. 文档/规则类变更：直接 Git 回退单轮变更。
2. 脚本类变更：保留旧入口，新增能力默认兼容旧参数，不做破坏性替换。
3. `executor` 接入：先支持 dry-run 与 explain-only，再切到真实执行。
4. `run.lock` 接入：先观测模式，再切强阻断模式。
5. 任一阶段若发现误伤现有流程：
   - 回退新增脚本入口
   - 保留旧 `autopilot-run-continuous.ps1` 路径
   - 将问题转为 blocked，不强推下一阶段

## 12. 数据与生产边界

1. 仅允许本地 dev/test/demo。
2. 数据库 host 必须是 `localhost` 或 `127.0.0.1`。
3. 需要测试数据重置时，必须存在 `.codex-autopilot/ALLOW_TEST_DATA_RESET`。
4. 禁止连接生产数据库。
5. 禁止自动发布生产。
6. 禁止删除仓库外文件、`.git`、用户目录。
7. 正式报告只保留结论性证据，不把临时日志/截图/路径写成长久规则。

## 13. 测试策略

遵循“最小但能兜底”的原则：

1. 脚本级验证优先：
   - `test-continuous-runner.ps1` 扩容
   - 新增 `ready-lint` 样例校验
   - 新增锁冲突/锁回收样例
2. 结构化结果验证：
   - `result.json` schema 样例
   - JSONL event 样例
3. 行为验证：
   - stop/pause 后自然收口
   - 空队列拆单后继续/停止判定
   - blocked 后不重复计数
4. 不要求为每个简单状态字段单独建重型测试框架。

## 14. 子智能体分档建议

本主线以治理、脚本、归档为主，建议按阶段分档，不统一一个档位到底。

| 阶段/任务类型 | 建议 model | 建议 thinking | 理由 |
|---|---|---|---|
| M0 基线盘点/计划书/字段字典 | gpt-5.4 | medium | 以规则对齐、结构化归档为主 |
| M1 ready-lint 规则设计与落盘 | gpt-5.4 | medium | 单模块脚本+规则治理，判断空间中等 |
| M2 run.lock 与并发收口 | gpt-5.5 | medium | 涉及并发、锁超时、断点恢复，风险高于普通脚本 |
| M3 executor 闭环与 result.json | gpt-5.5 | medium | 跨脚本、状态、归档，多证据汇总 |
| M4 status/explain/JSONL 增强 | gpt-5.4 | medium | 结构化输出与观测增强为主 |
| M5 无人值守准入裁决/最终验收 | gpt-5.5 | medium | 输出将直接用于通过/不通过裁决 |
| 命令执行、状态采样、日志摘录 | gpt-5.4 | low | 固定动作、判断空间小 |

## 15. 上线/收口裁决标准

这里的“上线”仅指“允许进入完整无人值守运行模式”，不指生产发布。

## 15.1 通过

满足以下条件可判“通过/非阻塞/允许进入完整无人值守模式”：

1. M1-M5 关键能力全部落地。
2. 至少完成一轮 dry-run、explain、真实执行、blocked 收口四类场景验证。
3. `status`、`result.json`、JSONL 三者口径一致。
4. 双实例冲突可被 `run.lock` 阻断。
5. stop/pause 后不会丢失当前轮结论。

## 15.2 不通过

存在以下任一项即判“不通过/阻塞”：

1. 无法证明单实例互斥。
2. 无法证明结果对象稳定产出。
3. 无法证明 Ready Issue 质量门禁生效。
4. 无法说明 blocked/WIP/rollback 恢复路径。
5. 仍需依赖人工阅读大量自然语言才能决定下一轮。

## 15.3 非阻塞剩余风险

即使通过，以下风险可以暂列非阻塞：

1. 尚未引入更强的多机协同能力。
2. JSONL 查询工具还较原始。
3. worktree 清理可能仍以手动命令为主。
4. 质量报告模板还可以继续统一，但不影响无人值守闭环本身。

## 16. 建议执行顺序

1. 先做 M0，冻结现状与缺口口径。
2. 再做 M1，先把入口门禁卡严。
3. 再做 M2，解决并发与锁。
4. 再做 M3，补执行器与结构化结果。
5. 再做 M4，补观测与解释。
6. 最后做 M5，补隔离、回滚与最终准入裁决。

## 17. 一句话结论

本主线的正确方向不是“再造一个自动化平台”，而是在当前已可运行的本地 AutoPilot 骨架上，把 `ready-lint`、`run.lock`、`executor`、`result.json`、`JSONL`、`status/explain`、`worktree 策略` 这 7 个最小缺口补齐；补齐后，系统才有资格被判定为“完整无人值守闭环”。
