---
name: cgc-pms-autopilot-owner
description: Owns cgc-pms AutoPilot planning, role routing, failure classification, and closeout contracts without taking over project fact storage.
---

# cgc-pms-autopilot-owner

## Use when

- 用户要求按 `cgc-pms` AutoPilot 规则做主线程编排、拆 Ready、判阻塞、验收或收口。
- 需要把连续自动迭代的 Owner 规则迁移到另一个本地仓库，但仍保持项目事实文件在目标仓库。
- 需要统一 A-F 角色边界、失败分类、正式输出口径。
- 用户直接使用项目级触发短语 `启动预演`、`启动迭代`、`启动迭代-N`、`停止迭代` 或对应 legacy 兼容短语。

## Do first

1. 先确认仓库级规则、授权边界和当前执行路由；普通交互任务获明确授权后不强制进入 Ready，只有 AutoPilot 连续迭代严格要求合格 Ready Issue。
2. 只把本插件当成规则、模板、脚本工具箱与插件自有归档目录，不当成项目真实 backlog 或业务 quality 仓库；项目业务任务正式文档仍留在项目 `docs/**`。
3. 任何状态变更前先核对 `git branch --show-current` 与 `git status --short`；进入 AutoPilot 实施前再跑 `scripts/autopilot-checkpoint.ps1`，至少看 `branch`、`gitStatus`、`stopFlag`、`pauseFlag`、`enabledFlag`。
4. 需要 loop 协议时，优先参考 `../../schemas/loop-state.schema.json`、`../../schemas/loop-event.schema.json`、`../../schemas/classification-result.schema.json`、`../../scripts/autopilot-loop-runner.ps1` 和 `../../scripts/validate-loop-artifacts.ps1`。

## Trigger protocol

1. `启动预演`
   - 只做插件 dry-run
   - 目标命令语义：`autopilot-loop-runner.ps1 -DryRun -ReadyIssuePath docs/backlog/ready-issues.md`
   - 不启动下一任务、不提交、不 push
2. `启动迭代`
   - 进入连续迭代模式
   - 优先走插件 runner / checkpoint / classifier
   - 仍受 Ready 队列、stop/pause/enabled、A-F 职责检查、no push 等约束
3. `启动迭代-N`
   - `N=1..50`
   - 最多完成 N 个实施型 Ready Issue 后退出
   - dry-run、拆单、health gate、runtime refresh 不计入 N
4. `停止迭代`
   - 安全停止
   - 等价于 legacy `停止自动迭代系统`
   - 设置停止标记并关闭 `enabled.flag`，不强杀当前任务

Legacy 兼容短语继续有效：

- `启动自动迭代系统`
- `启动连续自动迭代系统`
- `启动连续自动迭代系统-N`
- `停止自动迭代系统`

## Core flow

1. 读项目 `ready` / `blocked` / `focus` 事实源，只选择合格 Ready Issue 实施。
2. 判定当前阶段属于实现、验收、运维还是审计。
3. 按 A-F 检查实际职责，再由主线程根据风险、耦合、并行收益、上下文成本和独立证据需要选择直接执行、单派或多派；只有实际派工才给出 `model`、`thinking`、`reason`。
4. 实施前执行 checkpoint；需要运行态或浏览器验收时先过 health gate。
5. 需要正式文本时，用模板和脚本生成草稿；插件自有计划书、质量报告、迭代摘要、run summary 默认落到 `../../artifacts/**`；独立项目业务任务的计划书、质量报告、iteration、backlog 更新仍写回项目 `docs/**`。
6. 验收时先做失败分类，再给通过/不通过、阻塞/非阻塞结论；分类结果至少看 `category/subcategory/confidence/evidence/suggestedNextAction/retryPolicy`。
7. F 收口后先做 `local-commit-closeout.ps1 -DryRun`，确认 `git diff --check` 和文件范围，再决定是否本地 commit。
8. D/E 不通过时，优先用 `../../templates/repair-request.md` 生成结构化补修请求；沉淀稳定经验时用 `../../templates/reflection-entry.md`。

## Role boundaries

- 授权门通过后主线程是默认执行者，并对实施、验证和收口负责。
- 只有派工净收益明确时才使用子智能体；不存在按任务类别强制派工或固定六线程的规则。
- 子智能体只在明确授权范围内执行修改、验证、归档或运维动作，第一句必须声明身份边界。
- 实际派工单最少包含：`任务名称`、`角色边界`、`目标`、`范围`、`禁止事项`、`model`、`thinking`、`reason`、`验收输出`。

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

A-F 是职责检查表，不自动映射为六个独立线程；职责可合并、裁剪或分阶段覆盖，但责任不可遗漏。D 的裁决必需验证证据和 E 的适用风险审查证据不可省略，可由主线程、同一执行者、自动化工具或独立复核者提供，不强制拆成独立线程。

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

- AutoPilot 业务或治理变更只实施合格 Ready Issue；普通交互任务不受此限制。
- checkpoint 至少覆盖开始前、选任务后、改代码前、跑验证前、自动合并前、更新报告后；任务边界发现 `stop.flag` 或 `pause.flag` 时不启动下一任务，并持续核对 `enabled.flag`。
- 运行态或浏览器验收前检查后端 health、前端入口和 dev-login；任一不通先归为环境前置类，刷新运行态并稳定等待 180 秒后复验。
- 先做失败分类，再决定复跑、修复或阻塞；不得把一次命令失败直接定性为业务代码失败。
- 每轮最多并行 3 个完全无关联且无代码关联的 Ready Issue；不能证明无关联时串行，数据库、权限、安全、租户、金额、审批状态机等任务不得并行。
- `autoPush=false` / `no push` 禁止自动 push；只有用户明确授权且其他门禁通过后才可显式 push。
- 仅允许在 dev/test/demo、数据库 host 为 `localhost` 或 `127.0.0.1`、且存在 `.codex-autopilot/ALLOW_TEST_DATA_RESET` 时重置测试数据。
- 收口前必须完成对应验证、`git diff --check`、iteration/backlog 更新并复查 stop/pause；Ready 为空但当前 focus/阶段仍有可处理前置阻塞时，先解阻再判断停止。
- 停止条件只采用项目规则定义的停止指令、flag、迭代上限、已安全记录的不可解除阻塞、确无可拆 Ready/可处理前置或系统限制。
- 不自动发布生产，不连接生产数据库。
- 不删除仓库外文件。
- 不读取默认禁止私有目录。
- 不把 run id、临时日志名、截图名写进正式模板。
- 插件内只放模板、脚本、说明、示例和插件自有归档；项目真实事实留在项目仓库。
- 插件运行可以读取项目 `docs/**` 作为事实源与参考，但不默认复制进 `../../artifacts/**`。
