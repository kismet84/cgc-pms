# 第33条主线-Codex AutoPilot插件化封装任务计划书

**Goal:**
把 `cgc-pms` 当前已验证可运行的 AutoPilot 多角色连续迭代体系，收敛成一套可复用、可安装、可验证、可逐步演进的 Codex 插件化资产，覆盖 Skill、脚本、模板、参考说明、示例、安装验证与阶段路线；首批只封装最小闭环能力，不复制项目真实产物，不引入常驻服务、复杂调度器或生产级外部平台。

**Architecture:**
采用“`Codex Plugin` 壳 + `Skill` 编排 + `PowerShell scripts` 工具 + `templates` 产物模板 + `references` 规则说明 + `examples` 示例输出”的轻量结构；插件中只沉淀可复用模板、脚本入口和说明，不存放 `cgc-pms` 真实 backlog、quality、iteration、run 日志或业务交付物。项目正式产物继续留在仓库 `docs/plans`、`docs/quality`、`docs/iterations`、`docs/backlog`，插件只负责生成和校验，不接管项目事实源。

## 1. 结论先行

本主线建议通过最小插件化方案推进，先把已经在 `cgc-pms` 本地跑通且确有重复成本的能力抽出来，再决定是否上 marketplace。

当前最值得封装的不是“大而全 AutoPilot 平台”，而是以下 4 类重复劳动：

1. checkpoint 与 flag/state 前置检查重复。
2. Ready Issue/closeout/quality/blocked 模板重复。
3. 失败分类与收口口径重复。
4. F 归档后本地 commit、正式交付物落点、文档边界反复靠人工记忆。

因此本主线的最小方向应为：

1. 先封装 `Skill + 2 个关键脚本 + 6 个模板`。
2. 再补 `checkpoint/local commit` 与 `ready/closeout` 生成。
3. 最后补 failure classifier、插件打包、安装验证与 forward-test。

## 2. 背景与问题

### 2.1 当前基础

仓库已经具备本地 AutoPilot 运行骨架与治理规则：

1. `scripts/codex-autopilot/` 已有 `start/stop/pause/resume/status/readiness/continuous/executor` 等脚本。
2. `docs/backlog/ready-issues.md`、`current-focus.md`、`done-issues.md`、`blocked-issues.md` 已形成两级队列与收口体系。
3. 第31条主线已把 `ready-lint`、`run.lock`、`result.json`、`events.jsonl`、`status/explain`、真实 executor handoff 补成无人值守闭环。
4. `AGENTS.override.md` 已把主线程/子智能体边界、health gate、stop/pause checkpoint、失败分类、自动合并边界写成项目规则。

### 2.2 2小时完成3任务后的复盘结论

本计划承接用户指定的复盘结论，并结合当前仓库事实收敛为以下问题：

1. checkpoint 过重。
说明：开始前、选题后、改代码前、验证前、归档后都要重复核对 `branch/git status/stop.flag/pause.flag/enabled.flag/health gate`，没有统一工具入口时，重复动作高。
2. 验收重复。
说明：D、E、F 都会重复摘录 `git diff --check`、验证命令、正式交付物、阻塞/非阻塞、剩余风险，当前虽有规则，但缺少模板化生成。
3. 阻塞恢复策略分散。
说明：环境前置类、Ready Issue 配置问题、真实质量问题、命令调用问题虽然已有分类规则，但 blocked 收口、WIP 暂存、恢复条件仍需人工拼装。
4. F 后未提交会形成半收口。
说明：当前规则已要求 F 归档通过后先本地 commit 再判断下一轮，但如果缺少统一 closeout/commit 工具，容易出现正式报告已落地、Git 未收口的半完成状态。
5. 项目规则重，但可复用封装少。
说明：规则已经相对完整，重复成本主要来自“执行前后动作”和“正式产物模板”没有插件化。

### 2.3 当前根因判断

问题不在于缺一套新平台，而在于：

1. 规则、脚本、模板、交付物边界分散在仓库多个位置。
2. 相同动作缺少统一入口。
3. 可复用资产与项目真实产物混在同一仓库语义中，不利于迁移到其他项目。

## 3. 封装目标与非目标

### 3.1 封装目标

1. 提供一个可安装的 Codex 插件骨架，能在新项目中复用 AutoPilot 规则化能力。
2. 提供一个主线程 Owner Skill，统一解释角色边界、A-F 流水线、失败分类和收口模板。
3. 提供一组最小 PowerShell 工具脚本，减少 checkpoint、模板渲染、closeout、local commit 的重复动作。
4. 提供标准模板，统一计划书、Ready、Done、Blocked、Quality、Iteration、Run Summary 口径。
5. 保持项目真实产物继续落在 `docs/`，避免插件污染业务仓库事实。
6. 为后续 marketplace 打包、安装、验证和 forward-test 留出清晰路线。

### 3.2 非目标

1. 不把 `cgc-pms` backlog、quality、iteration 真实内容搬进插件。
2. 不把插件做成常驻 daemon、服务端平台、数据库驱动调度器。
3. 不在本主线引入 dashboard、数据库、复杂任务编排中心。
4. 不把项目级规则完全泛化成无上下文的通用企业平台。
5. 不把 AutoPilot 变成自动发布生产或生产数据库执行器。
6. 不在首版里接入 MCP 可执行工具市场、远程多机调度或可视化控制台。

## 4. 总体架构

### 4.1 总体分层

```text
Codex Plugin
  -> skills/
  -> scripts/
  -> templates/
  -> references/
  -> examples/
  -> .codex-plugin/plugin.json
```

各层职责：

1. `skills/`
负责主线程 Owner 编排、执行边界、任务分档、A-F 流水线说明、派工模板与验收模板。
2. `scripts/`
负责 checkpoint、模板渲染、closeout、failure classifier、local commit 等固定动作。
3. `templates/`
负责标准 markdown/json 文本骨架。
4. `references/`
负责规则解释、字段字典、失败分类字典、目录边界、安装验证说明。
5. `examples/`
负责示例输入输出，帮助使用者快速理解格式。
6. `.codex-plugin/plugin.json`
负责插件元数据、入口说明和安装声明。

### 4.2 项目产物与插件产物边界

插件内只保留以下内容：

1. 模板。
2. 可复用脚本。
3. Skill 说明。
4. 示例与参考文档。

项目仓库正式产物仍必须落在：

1. `docs/plans`
2. `docs/quality`
3. `docs/iterations`
4. `docs/backlog`

明确禁止：

1. 在插件内存放真实 `ready-issues.md`、`done-issues.md`、`blocked-issues.md`。
2. 在插件内存放真实 `events.jsonl`、`result.json`、run id、临时日志、截图名。
3. 在插件内写入任何项目业务代码或部署配置。

## 5. 目录结构设计

建议插件目录结构如下：

```text
codex-autopilot-plugin/
  .codex-plugin/
    plugin.json
  skills/
    cgc-pms-autopilot-owner/
      SKILL.md
      references/
        owner-boundary.md
        failure-classification.md
        output-contract.md
      templates/
        owner-dispatch.md
        acceptance-closeout.md
  scripts/
    autopilot-checkpoint.ps1
    ready-issue-writer.ps1
    issue-closeout.ps1
    test-failure-classifier.ps1
    local-commit-closeout.ps1
    render-template.ps1
  templates/
    mainline-plan.md
    ready-issue.md
    done-issue.md
    blocked-issue.md
    quality-closeout.md
    security-review.md
    iteration-report-entry.md
    run-summary.md
  references/
    install.md
    repository-boundary.md
    artifact-governance.md
    model-routing.md
  examples/
    ready-issue.example.md
    blocked-issue.example.md
    quality-closeout.example.md
    run-summary.example.md
```

与项目仓库的正式落点映射如下：

| 插件资产 | 作用 | 项目正式落点 |
| --- | --- | --- |
| `templates/mainline-plan.md` | 计划书骨架 | `docs/plans/*.md` |
| `templates/ready-issue.md` | Ready 任务骨架 | `docs/backlog/ready-issues.md` |
| `templates/done-issue.md` | Done 收口骨架 | `docs/backlog/done-issues.md` |
| `templates/blocked-issue.md` | Blocked 收口骨架 | `docs/backlog/blocked-issues.md` |
| `templates/quality-closeout.md` | 正式验收/质量报告骨架 | `docs/quality/*.md` |
| `templates/iteration-report-entry.md` | 迭代记录条目骨架 | `docs/iterations/*.md` |
| `templates/run-summary.md` | 单轮运行摘要 | 项目临时控制目录或人工回报，不默认入库 |

## 6. 核心 Skill 设计

### 6.1 Skill 名称

`cgc-pms-autopilot-owner`

### 6.2 触发场景

1. 用户要求启动连续自动迭代系统前，需要先做主线程编排。
2. 用户要求拆 Ready Issue、判断阻塞恢复、审查当前队列、组织 A-F 分工。
3. 用户要求生成计划书、质量收口、blocked 裁决、local commit 前收口说明。
4. 用户要把当前 `cgc-pms` AutoPilot 规则迁移到其他仓库时。

### 6.3 Skill 职责

1. 明确主线程与子智能体身份边界。
2. 固化 A-F 流水线职责分层。
3. 固化失败分类与升级/降级路径。
4. 固化 D、E、F 的正式输出模板。
5. 约束插件只生成工具和模板，不接管项目真实产物。

### 6.4 主线程/子智能体边界

主线程职责：

1. 规划。
2. 拆题。
3. 分档。
4. 派工。
5. 验收。
6. 裁决。

子智能体职责：

1. 在派工范围内执行修改、验证、归档或运维动作。
2. 严格按允许范围返回正式交付物、验证结果、阻塞与剩余风险。

Skill 中必须写死的边界：

1. 主线程不直接改代码。
2. 子智能体第一句必须先声明身份边界。
3. 派工必须带 `model/thinking/reason/验收输出`。

### 6.5 A-F 流水线定义

1. A 需求/架构分析
负责读 backlog、拆 Ready、识别依赖、判断是否需要重新分档。
2. B 前端/UI 实现
负责页面、组件、交互、前端验证。
3. C 后端/API 实现
负责后端逻辑、测试、数据边界。
4. D 测试/回归
负责最小裁决验证、失败分类和通过/不通过判断。
5. E 代码审查/安全审查
负责越权、误分类、扩大范围、遗留风险审查。
6. F 文档/上线清单
负责正式报告、backlog 状态、iteration entry、local commit 收口建议。

### 6.6 失败分类规范

Skill 必须内置以下四类失败分类：

1. 工具配置类
2. 环境前置类
3. Ready Issue 配置问题
4. 真实质量或安全问题

并要求：

1. 未先分类不得直接判代码失败。
2. 分类结果必须进入 D/E/F 模板。
3. blocked 恢复动作必须引用分类结果。

### 6.7 D/E/F 模板口径

D 模板最小字段：

1. 验证命令
2. 结果
3. 失败分类
4. 通过/不通过
5. 阻塞/非阻塞

E 模板最小字段：

1. 高风险点
2. 非阻塞建议
3. 是否需补修
4. 是否升级模型/角色

F 模板最小字段：

1. 正式交付物
2. 验收证据
3. 临时产物
4. git 状态
5. 结论
6. 阻塞
7. 剩余风险

## 7. 脚本设计

### 7.1 `autopilot-checkpoint.ps1`

输入：

1. repoRoot
2. autopilotDir
3. 可选 `-CheckHealth`
4. 可选 `-CheckGit`

输出：

1. 结构化 JSON
2. `branch`
3. `gitStatus`
4. `stopFlag`
5. `pauseFlag`
6. `enabledFlag`
7. 可选 `healthGate`
8. `decision`

是否允许写文件：

1. 默认否。
2. 仅允许可选写临时 JSON 到调用方指定目录。

验收：

1. 能稳定输出主工作区最小 checkpoint 摘要。
2. stop/pause/enabled 缺失或异常时能明确给出阻断原因。
3. 不修改 Git 状态。

### 7.2 `ready-issue-writer.ps1`

输入：

1. issue 元数据
2. 模板变量
3. 目标输出路径

输出：

1. 渲染后的 Ready Issue 文本
2. 可选 lint 结果

是否允许写文件：

1. 允许。
2. 仅写调用方显式指定的 `docs/backlog/ready-issues.md` 或临时文件。

验收：

1. 能生成符合项目字段要求的 Ready 条目。
2. 缺必填字段时直接失败。
3. 不写非目标目录。

### 7.3 `issue-closeout.ps1`

输入：

1. issueId
2. closeoutType=`done|blocked`
3. 正式交付物列表
4. 验收证据摘要
5. 失败分类
6. 剩余风险

输出：

1. Done/Blocked markdown 片段
2. 可选质量报告摘要

是否允许写文件：

1. 允许。
2. 仅写 `docs/backlog/done-issues.md`、`docs/backlog/blocked-issues.md` 或调用方指定临时文件。

验收：

1. done/blocked 两种口径字段一致。
2. 能区分正式交付物与临时产物。
3. 不把 run id、日志文件名写入正式报告。

### 7.4 `test-failure-classifier.ps1`

输入：

1. 命令返回码
2. 错误文本
3. 可选 stdout/stderr 路径

输出：

1. `category`
2. `reason`
3. `suggestedNextAction`

是否允许写文件：

1. 默认否。
2. 可选输出 JSON 到调用方指定路径。

验收：

1. 至少能稳定区分工具配置类、环境前置类、Ready Issue 配置问题、真实质量或安全问题。
2. 允许不命中细粒度，但不能把明显环境问题误判为代码失败。

### 7.5 `local-commit-closeout.ps1`

输入：

1. issueId
2. commitMessage
3. 预期文件范围
4. 可选 `-DryRun`

输出：

1. Git 收口建议
2. `git status --short`
3. 可选提交结果摘要

是否允许写文件：

1. 默认否。
2. 在非 `-DryRun` 模式下允许执行本地 commit。

验收：

1. 提交前必须检查 `git diff --check`。
2. 若 stop/pause 已出现，则只输出收口建议，不提交新任务结果之外的内容。
3. 不 push。

### 7.6 `render-template.ps1`

输入：

1. 模板路径
2. 变量字典
3. 目标输出路径

输出：

1. 渲染后的 markdown/text

是否允许写文件：

1. 允许。
2. 仅写显式给定路径。

验收：

1. 所有模板变量缺失时直接报错。
2. 输出保持可读 markdown，不引入额外格式噪音。

## 8. 模板设计

### 8.1 模板清单

1. `mainline-plan`
2. `ready-issue`
3. `done-issue`
4. `blocked-issue`
5. `quality-closeout`
6. `security-review`
7. `iteration-report-entry`
8. `run-summary`

### 8.2 模板变量

`mainline-plan`：

1. `mainline_no`
2. `title`
3. `goal`
4. `architecture`
5. `scope`
6. `non_goals`
7. `milestones`
8. `acceptance`
9. `risks`

`ready-issue`：

1. `issue_id`
2. `priority`
3. `type`
4. `source_anchor`
5. `goal`
6. `allowed_paths`
7. `forbidden_paths`
8. `acceptance`
9. `verify_commands`
10. `report_path`

`done-issue`：

1. `issue_id`
2. `summary`
3. `artifacts`
4. `evidence`
5. `verify_commands`
6. `merge_mode`
7. `risks`

`blocked-issue`：

1. `issue_id`
2. `category`
3. `block_reason`
4. `wip_state`
5. `recovery_condition`
6. `evidence`
7. `next_action`

`quality-closeout`：

1. `title`
2. `scope`
3. `artifacts`
4. `verification`
5. `result`
6. `blocking`
7. `residual_risk`

`security-review`：

1. `target`
2. `threats`
3. `findings`
4. `severity`
5. `fix_decision`
6. `residual_risk`

`iteration-report-entry`：

1. `issue_id`
2. `goal`
3. `change_summary`
4. `verification_summary`
5. `auto_merge`
6. `push`
7. `decision`
8. `blocking`
9. `residual_risk`

`run-summary`：

1. `run_mode`
2. `selected_issue`
3. `checkpoint`
4. `result`
5. `next_action`
6. `stop_reason`
7. `artifacts`

## 9. 产出物治理

### 9.1 治理原则

1. 模板在插件。
2. 正式产物在项目 `docs`。
3. 临时日志不入库。
4. `docs/quality` 只放正式质量/审计/验收/裁决报告。

### 9.2 插件中允许长期存在的内容

1. 固定模板。
2. 通用脚本。
3. 说明文档。
4. 示例文件。

### 9.3 项目中允许长期存在的内容

1. 计划书。
2. quality 报告。
3. iteration 报告。
4. backlog 状态文档。

### 9.4 明确禁止入库的内容

1. 临时日志。
2. 临时截图。
3. 一次性 run id。
4. 子智能体昵称。
5. stdout/stderr 原始大段输出。
6. 临时缓存和本地运行态文件。

## 10. 阶段实施计划

### 10.1 M1 Skill 最小版

交付物：

1. `.codex-plugin/plugin.json`
2. `skills/cgc-pms-autopilot-owner/SKILL.md`
3. `references/owner-boundary.md`
4. `references/failure-classification.md`

验收标准：

1. 能说明主线程/子智能体边界。
2. 能输出 A-F 流水线和模型分档建议。
3. 能引用 D/E/F 最小模板口径。

测试命令：

1. `Get-Content skills/cgc-pms-autopilot-owner/SKILL.md`
2. `Get-Content .codex-plugin/plugin.json`

回滚方式：

1. 删除新增插件目录即可，不影响项目运行态。

### 10.2 M2 checkpoint/local commit 脚本

交付物：

1. `scripts/autopilot-checkpoint.ps1`
2. `scripts/local-commit-closeout.ps1`

验收标准：

1. checkpoint 输出固定字段。
2. local commit 支持 dry-run。
3. 两脚本都不 push。

测试命令：

1. `powershell -NoProfile -File scripts/autopilot-checkpoint.ps1`
2. `powershell -NoProfile -File scripts/local-commit-closeout.ps1 -DryRun`

回滚方式：

1. 保留旧人工命令流程，脚本删除后人工流程仍可运行。

### 10.3 M3 ready/closeout 模板生成

交付物：

1. `scripts/render-template.ps1`
2. `scripts/ready-issue-writer.ps1`
3. `scripts/issue-closeout.ps1`
4. `templates/ready-issue.md`
5. `templates/done-issue.md`
6. `templates/blocked-issue.md`
7. `templates/quality-closeout.md`
8. `templates/iteration-report-entry.md`

验收标准：

1. Ready/Done/Blocked/Quality/Iteration 模板变量完整。
2. 缺变量时直接失败。
3. 不把模板输出写到错误目录。

测试命令：

1. `powershell -NoProfile -File scripts/render-template.ps1`
2. `powershell -NoProfile -File scripts/ready-issue-writer.ps1`
3. `powershell -NoProfile -File scripts/issue-closeout.ps1`

回滚方式：

1. 模板生成失败时回退到手工写文档，不阻断项目正常执行。

### 10.4 M4 failure classifier

交付物：

1. `scripts/test-failure-classifier.ps1`
2. `references/failure-classification.md`
3. `examples/*classifier*`

验收标准：

1. 至少覆盖四类失败分类。
2. 常见环境问题不误报为真实代码失败。

测试命令：

1. `powershell -NoProfile -File scripts/test-failure-classifier.ps1`

回滚方式：

1. 分类判断不稳定时可退回人工分类，不影响已有模板与脚本使用。

### 10.5 M5 插件打包和 marketplace

交付物：

1. 插件元数据完善版
2. 安装说明
3. 发布清单

验收标准：

1. 本地可安装。
2. 安装后可读取 Skill、模板、脚本说明。
3. 不包含项目真实产物。

测试命令：

1. `codex plugin` 相关本地安装验证命令
2. 读取插件目录结构检查

回滚方式：

1. 保留本地目录安装方式，不把 marketplace 作为唯一分发渠道。

### 10.6 M6 forward-test 与规则回填

交付物：

1. forward-test 记录
2. 插件使用说明修订
3. 项目规则回填建议

验收标准：

1. 在新仓库或模拟仓库中完成一次最小前向验证。
2. 识别哪些规则必须留在项目、哪些可继续插件化。

测试命令：

1. 在样例仓库执行 checkpoint/template/render 最小链路
2. `git diff --check`

回滚方式：

1. forward-test 若不稳定，只冻结已验证的 M1-M5，不强行放大使用范围。

## 11. 每阶段交付物、验收、测试、回滚总表

| 阶段 | 主要交付物 | 验收标准 | 测试命令 | 回滚方式 |
| --- | --- | --- | --- | --- |
| M1 | Skill、plugin metadata、references | 能解释边界与 A-F 流水线 | 读取文件检查 | 直接移除插件目录 |
| M2 | checkpoint/local commit 脚本 | 输出字段固定，不 push | PowerShell dry-run | 回退到人工命令 |
| M3 | 模板生成脚本与 5 类模板 | 模板变量完整、目录边界正确 | render/writer/closeout 脚本 | 回退到手工文档 |
| M4 | failure classifier | 四类失败能稳定分类 | classifier 脚本 | 改回人工分类 |
| M5 | 打包与安装说明 | 本地可安装，不带真实产物 | 安装与读取验证 | 保留目录安装 |
| M6 | forward-test 与规则回填 | 新仓最小链路可跑通 | forward-test + `git diff --check` | 冻结已验证范围 |

## 12. 风险与控制

1. 过度自动化。
控制：首版只做 Skill、脚本、模板，不做复杂调度器。
2. 误提交。
控制：`local-commit-closeout.ps1` 默认 dry-run，必须先过 `git diff --check`。
3. 误读禁止目录。
控制：references 中写死禁止目录名单，脚本输入禁止递归这些目录。
4. 失败误分类。
控制：classifier 只做四大类最小分类，不追求过细；不确定时退回人工。
5. 上下文污染。
控制：插件不存项目真实产物，不把 run id、日志路径、个人昵称写入模板。
6. 脚本破坏性。
控制：默认只读或显式目标路径写入；不提供删除仓库外文件能力。
7. 行尾噪音。
控制：模板和脚本统一输出 markdown/plain text，避免混入临时日志。

## 13. 模型/子智能体分工建议

| 任务类型 | 建议 model | 建议 thinking | 理由 |
| --- | --- | --- | --- |
| Skill 设计、计划书、模板变量设计 | `gpt-5.4` | `medium` | 结构化归档与规则收敛为主 |
| checkpoint/local commit 脚本 | `gpt-5.4` | `medium` | 单模块脚本实现，风险中等 |
| failure classifier | `gpt-5.5` | `medium` | 需要做排除式判断，误分类风险高于普通脚本 |
| marketplace 打包与安装验证 | `gpt-5.4` | `low` | 固定步骤执行为主 |
| forward-test 与准入裁决 | `gpt-5.5` | `medium` | 结论会直接影响是否推广使用 |

建议角色拆分：

1. A 架构/规则子智能体：负责 Skill、references、模板边界。
2. B 工具脚本子智能体：负责 checkpoint/render/closeout/local commit。
3. D 验收子智能体：负责安装验证、前向验证、失败分类复核。
4. F 归档子智能体：负责计划书、说明文档、发布清单。

## 14. 最小可行版本边界

首版 MVP 只做：

1. 1 个 Skill：`cgc-pms-autopilot-owner`
2. 2 个关键脚本：`autopilot-checkpoint.ps1`、`render-template.ps1`
3. 6 个模板：
   - `mainline-plan`
   - `ready-issue`
   - `done-issue`
   - `blocked-issue`
   - `quality-closeout`
   - `iteration-report-entry`

首版明确不做：

1. dashboard
2. 数据库
3. 复杂调度器
4. 常驻服务
5. MCP 可执行工具接管
6. 生产发布链路

## 15. 后续可扩展方向

后续可扩展，但不纳入本主线首版交付：

1. 插件内增加 `security-review`、`run-summary`、`blocked-recovery` 专项模板。
2. 接入更多仓库通用 references，例如 Java/Vue 双栈验证命令规范。
3. 增加只读型 MCP/app 方向，用于读取 backlog/quality 状态摘要，而不是执行修改。
4. 增加插件安装后的一键 self-check。
5. 增加通用 `repo profile` 配置，使同一插件适配其他项目，但仍由项目仓库保留事实规则。

## 16. 通过/不通过裁决口径

### 16.1 通过

满足以下条件可判通过：

1. 计划书覆盖 Skill、脚本、模板、目录、治理、阶段、风险、验收、模型分工。
2. 首段同时具备 `Goal` 与 `Architecture`。
3. 明确插件与项目正式产物边界。
4. 明确 MVP 边界与非目标，未扩成新平台。

### 16.2 不通过

存在以下任一项即判不通过：

1. 把插件写成承载项目真实 backlog/quality 的事实仓。
2. 未明确 docs 正式落点。
3. 未覆盖 checkpoint、closeout、failure classifier、F 后本地 commit 问题。
4. 把首版范围扩到 dashboard、数据库、复杂调度器。

## 17. 一句话结论

第33条主线应按“先封装规则和模板，再封装脚本入口，最后做安装验证和前向验证”的顺序推进；先把 `Owner Skill + checkpoint/render 两脚本 + 6 个模板` 做成最小可用插件，再决定是否继续上 marketplace，而不是先造一套新的 AutoPilot 平台。
