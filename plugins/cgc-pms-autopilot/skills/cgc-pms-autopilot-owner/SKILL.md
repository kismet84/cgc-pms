---
name: cgc-pms-autopilot-owner
description: Owns cgc-pms AutoPilot planning, role routing, failure classification, and closeout contracts without taking over project fact storage.
---

# cgc-pms-autopilot-owner

## Use when

- 用户要求按 `cgc-pms` AutoPilot 规则做主线程编排、拆 Ready、判阻塞、验收或收口。
- 需要把连续自动迭代的 Owner 规则迁移到另一个本地仓库，但仍保持项目事实文件在目标仓库。
- 需要统一 A-F 角色边界、失败分类、正式输出口径。

## Do first

1. 先确认仓库级规则文件和当前派工边界。
2. 只把本插件当成规则、模板、脚本工具箱与插件自有归档目录，不当成项目真实 backlog 或业务 quality 仓库；项目业务任务正式文档仍留在项目 `docs/**`。
3. 进入实施前先跑 `scripts/autopilot-checkpoint.ps1`，至少看 `branch`、`gitStatus`、`stopFlag`、`pauseFlag`、`enabledFlag`。
4. 需要 loop 协议时，优先参考 `../../schemas/loop-state.schema.json`、`../../schemas/loop-event.schema.json`、`../../schemas/classification-result.schema.json`、`../../scripts/autopilot-loop-runner.ps1` 和 `../../scripts/validate-loop-artifacts.ps1`。

## Core flow

1. 读项目 `ready` / `blocked` / `focus` 事实源。
2. 判定当前阶段属于实现、验收、运维还是审计。
3. 按 A-F 拆角色并给出 `model`、`thinking`、`reason`。
4. 实施前执行 checkpoint。
5. 需要正式文本时，用模板和脚本生成草稿；插件自有计划书、质量报告、迭代摘要、run summary 默认落到 `../../artifacts/**`；独立项目业务任务的计划书、质量报告、iteration、backlog 更新仍写回项目 `docs/**`。
6. 验收时先做失败分类，再给通过/不通过、阻塞/非阻塞结论；分类结果至少看 `category/subcategory/confidence/evidence/suggestedNextAction/retryPolicy`。
7. F 收口后先做 `local-commit-closeout.ps1 -DryRun`，确认 `git diff --check` 和文件范围，再决定是否本地 commit。
8. D/E 不通过时，优先用 `../../templates/repair-request.md` 生成结构化补修请求；沉淀稳定经验时用 `../../templates/reflection-entry.md`。

## Role boundaries

- 主线程负责：规划、拆题、分档、派工、验收、裁决。
- 子智能体负责：在明确授权范围内执行修改、验证、归档或运维动作。
- 主线程不直接改代码；子智能体第一句必须声明身份边界。
- 派工单最少包含：`任务名称`、`角色边界`、`目标`、`范围`、`禁止事项`、`model`、`thinking`、`reason`、`验收输出`。

细则见：

- `../../references/owner-boundary.md`
- `../../references/failure-classification.md`
- `../../references/classifier-rules.md`
- `../../references/output-contract.md`
- `../../references/artifact-governance.md`
- `../../references/loop-budget-policy.md`
- `../../references/rerun-policy.md`
- `../../references/role-contracts.md`
- `../../references/forward-test-scenarios.md`

## A-F routing

- A 需求/架构分析：读 backlog、拆 Ready、识别依赖和重新分档时机。
- B 前端/UI 实现：只处理前端页面、交互和前端验证。
- C 后端/API 实现：只处理后端逻辑、测试、数据边界。
- D 测试/回归：只做裁决必需验证、失败分类和通过/不通过判断。
- E 代码审查/安全审查：只看高风险点、越权、扩大范围、补修必要性。
- F 文档/上线清单：只做正式收口、backlog 状态更新、iteration 和本地 commit 建议。

## Failure classification

先分类，再定性：

1. `tool_config`
2. `environment_prereq`
3. `ready_issue_config`
4. `real_quality_or_security`
5. `unknown`

若不能稳定归类，输出 `unknown` 并要求人工复核，不得直接判业务代码失败。
若需要串联一轮最小闭环，先用 `../../scripts/autopilot-loop-runner.ps1 -DryRun` 跑 `checkpoint -> classify -> repair-request/closeout -> next` 预演。

## Output contract

- D 最小字段：验证命令、结果、失败分类、通过/不通过、阻塞/非阻塞。
- E 最小字段：高风险点、非阻塞建议、是否需补修、是否需升档或换角色。
- F 最小字段：正式交付物、验收证据、临时产物、git 状态、结论、阻塞、剩余风险。

模板位置：

- `../../templates/ready-issue.md`
- `../../templates/done-issue.md`
- `../../templates/blocked-issue.md`
- `../../templates/quality-closeout.md`
- `../../templates/iteration-report-entry.md`
- `../../templates/run-summary.md`
- `../../templates/repair-request.md`
- `../../templates/reflection-entry.md`

插件自有产物默认目录：

- `../../artifacts/plans/`
- `../../artifacts/quality/`
- `../../artifacts/iterations/`
- `../../artifacts/runs/`

项目业务任务正式目录：

- `docs/plans`
- `docs/quality`
- `docs/iterations`
- `docs/backlog`

## Safety rules

- 不 push。
- 不删除仓库外文件。
- 不读取默认禁止私有目录。
- 不把 run id、临时日志名、截图名写进正式模板。
- 插件内只放模板、脚本、说明、示例和插件自有归档；项目真实事实留在项目仓库。
- 插件运行可以读取项目 `docs/**` 作为事实源与参考，但不默认复制进 `../../artifacts/**`。
