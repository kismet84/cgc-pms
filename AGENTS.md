# AGENTS.md

若仓库根存在 `AGENTS.override.md`，必须先读取并遵守；本文件只提供项目级基础规则、项目背景、常用命令和长期约定；如与 `AGENTS.override.md` 冲突，以 `AGENTS.override.md` 为准。

本文件是 AI 编程助手在本仓库工作的高优先级入口说明。保持精简：这里只放必须马上知道的规则；详细流程放在 `docs/` 中，经验索引见 `memory/MEMORY.md`。

## 强制规则

- 所有回答必须使用中文。
- 先通过授权门再执行：普通交互任务获用户明确授权后不强制进入 Ready；用户指定主线/backlog/治理流程时遵守对应载体；AutoPilot 连续迭代仍必须来自合格 Ready Issue。
- 授权门通过后默认由主线程直接执行；只有并行收益、专业或上下文隔离、长耗时卸载、独立证据等净收益明确高于派工成本时，才选择单派或多派，不按任务类别强制使用子智能体。
- 任何代码、配置、文档、Git 或运行环境状态变更前，执行者必须核对 `git branch --show-current` 与 `git status --short`；怀疑 worktree 冲突时再查 `git worktree list`。
- 实际派工才填写 `model`、`thinking`、`reason`；同时派出两个及以上子智能体时才要求模型分配表。超时或悬挂时先只读核验，再按风险收回直接执行、补上下文或重派。
- 数据库、权限、安全、金额、租户、数据一致性与正式裁决必须有客观证据和必要复核；证据不足时明确写出“不通过”或“需要确认”，不得以派工或角色覆盖替代证据。
- 任务收口实行“非阻塞问题零悬空”：当前目标、验收标准、当前 diff 根因或本轮直接引入的问题必须本轮修复并复验，否则不得判定通过；真正超出范围的问题必须去重后写入唯一正式载体并具备证据、价值、优先级、延期原因/前置与验收标准；无明确价值或验收方式的泛化建议直接关闭，不制造 backlog。
- 收口时必须统计 `新增后续项`、`关闭后续项`、`后续项净变化`；没有正式承接载体的遗留项一律视为未收口。连续两个 Issue 净增后续项时，AutoPilot 暂停新的能力新增，优先消化缺口直至恢复到净增前基线。
- AutoPilot 死进程恢复必须优先校验活动 Issue 的 durable phase checkpoint；Ready/base/worktree/branch/diff/evidence 一致时只从首个未完成的验证、Reviewer 或收口阶段继续，不得删除有效 worktree 后重新派发实现。Reviewer `tool_config` 重试耗尽只暂停 Reviewer；控制面指纹变化后 N>1/无界执行必须先通过用户启动的单任务金丝雀。
- AutoPilot 控制面、子进程和测试入口统一使用 PowerShell 7 `pwsh`；缺失时归类为 `tool_config/AUTOPILOT_POWERSHELL7_REQUIRED` 并安全停止，不得回退到 Windows PowerShell 5.1。原生命令以退出码和命令契约裁决，stderr warning 仅作诊断证据。
- AutoPilot 只有持有当前 `runInstanceId + leaseEpoch + controlPlaneFingerprint` fencing token 的 APPLY 实例可以派发、写状态或执行 Git 变更；CPU/心跳变化不算任务进度，进度只由工作区内容或 durable checkpoint/result/evidence 变化推进。
- AutoPilot 各阶段统一返回可校验 `StageResult`，不得以自由文本路由；活动 Issue 的 checkpoint 阶段迁移只能经 `autopilot-transition.ps1`，并校验合法边、fencing、控制面指纹及 `transitionId + generation` 读回，state/checkpoint 底层模块不得自行决定业务阶段。
- AutoPilot 生产默认宿主为 Codex 桌面主线程（`executionHost=desktop-native`）：主线程直接推进 checkpoint 与 A-F 职责，PowerShell 只做确定性原子动作；禁止 runner 启动嵌套 Planner、Executor、Reviewer `codex exec`。`cli-legacy` 仅可显式兼容或经授权紧急回退，不得静默降级。
- `autoPush=false` / `no push` 禁止自动推送；提交或 push 只可在用户明确授权且其他适用门禁通过后执行。
- 修改代码后必须运行相关验证：后端至少跑相关测试/构建，前端至少跑类型检查/测试/构建中的相关项。
- 不要修改已经应用过的 Flyway 迁移脚本；数据库结构变化必须新增版本化 migration。
- 做外科手术式修改：只改必要文件，遵循现有命名、目录、异常处理和测试风格。
- 解决错误或工具陷阱后，优先在当前会话中沉淀可复用结论；只有在当前运行环境允许且用户明确要求时，才写入 `memory/` 并更新 `memory/MEMORY.md`。
- `AGENTS.md` 为仓库级协作规则入口；`CLAUDE.md` 若存在则属于本地 AI 协作配置，不影响系统运行。
- 通用任务状态机、短指令、工具路由、失败分类、浏览器/验证/Git 模板、事件驱动沟通与恢复胶囊统一读取 `docs/standards/codex-task-execution-policy.md`；本文件只保留必须马上知道的边界和入口。
- `codebase-memory-mcp` 只允许用于本地索引与只读查询；禁止由其创建、修改、删除或覆盖 `AGENTS.md`、`AGENTS.override.md`，禁止运行会自动改写智能体规则、Codex 配置、skills 或 hooks 的 `codebase-memory-mcp install` / `uninstall` 入口。需要升级时只能在用户明确授权后替换独立安装目录中的二进制，不得改写仓库规则文件。
- commentary 只在统一策略定义的状态变化事件中发送；不为每条命令、纯等待或相同状态重复播报。
- 任务结束只做清理预览；仅清理本任务创建且确认无用的临时进程和产物，不影响用户已有运行环境，不得盲删。

## 产品演进基本流程

- 当用户要求判断产品方向、生成下一轮计划，或 Ready 为空且 `docs/backlog/current-issues.json` 中没有可自动拆分的存量问题、也没有已通过决策门的候选时，先读取 `docs/product-intelligence/`，按“项目地图 → 竞品情报 → 迭代决策 → Ad-hoc Candidate → Ready → 实施 → 地图回写”推进。
- AutoPilot 的 Ready 来源、知识图谱健康/HEAD 游标门禁、补货顺序、回写与停止条件统一读取 `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`；`current-issues.json` 是正式写回源，不是默认发现入口。图谱异常时 fail-close，不静默回退到文件扫描或长期计划凑任务。
- `docs/backlog/cgc-pms-production-enhancement-plan.md` 只作为研究和候选输入，不能直接生成 Ready；产品 Candidate 必须引用当前项目地图和迭代决策证据。
- 工程治理 Candidate 默认不能替代产品方向；仅当当前证据证明它直接阻塞已选产品目标、安全边界或正式验收时，才可按 `缺口修复` 或 `运维治理` 进入 Ready，并明确关联目标、阻塞证据、解除条件、非目标和回滚方式。
- Ready 以证据和字段完整为准，不为凑数量放宽门槛；1 条合格 Ready 即可实施，队列上限仍为 5 条。
- 主线或 Ready Issue 完成后，必须回写项目地图；若结果影响候选排序，同步刷新竞品差距或迭代决策。

## 自动经验记录

每次解决一个错误或问题后，优先整理成可复用结论；只有在当前运行环境允许且用户明确要求时，才保存到 `memory/`：

- 覆盖编译失败、测试失败、运行时异常、配置错误、Flyway 失败、工具调用陷阱等。
- 每个经验单独一个 `.md` 文件，包含 frontmatter：`name`、`description`、`metadata type/feedback`、`tags`。
- 保存后更新 `memory/MEMORY.md` 索引：一行链接 + 一句话描述。
- 如果当前运行环境不允许直接写 memory，则保留在会话或普通文档中，不强制落盘。

## 新会话启动模板（可复制粘贴）

每次新会话（尤其是新线程）建议先发送：

```text
请先读取并严格遵循 D:\projects-test\cgc-pms\AGENTS.override.md、D:\projects-test\cgc-pms\AGENTS.md 和本仓库可见的工具说明；先按 CODEGRAPH 要求在检索代码时优先使用 CodeGraph；
如任务涉及审计，按仓库现状直接输出基于代码证据的审计结论；如需归档正式审计报告，按 AGENTS.override.md 的审计与归档边界写入 docs/quality/。
```

## 项目与运行入口

- 项目目录：`backend/` 后端、`frontend-admin/` 前端、`deploy/` 部署、`docs/` 文档中心。
- 快速启动、本地访问地址、运行态刷新、常见验证：`docs/standards/01-快速开始.md`
- 系统分层、模块域、数据与部署架构：`docs/standards/02-系统架构.md`
- Docker、生产部署、回滚、备份、监控：`docs/standards/10-部署运维手册.md`
- 前端本地验收默认入口：`http://localhost:5173`
- 前后端重启后的统一稳定等待时间按 `180秒` 执行；后端至少等待 `180秒` 后再做 health / Flyway / 接口验收，前端至少等待 `180秒` 并确认 Vite ready 后，再做 Playwright UI 验收。
- 若 Docker 连续多次不可用（如 `dockerDesktopLinuxEngine` 管道不存在、`docker ps` 无法连接、`5173/8080` 均拒绝连接），在需要运行态验收且用户允许运维动作时，先尝试重启 WSL2 与 Docker Desktop，再等待 `180秒` 后复查 `docker ps`、后端 health、前端入口；不得把 Docker 不可用伪装成业务失败或验收通过。
- 若 `http://localhost:5173` 回退到 `/login`，且前端日志出现 `/api/*` 代理到旧 `172.19.x.x:8080` 的错误，优先判定为前端 dev server 持有旧 backend 容器 IP；先执行 `python scripts/rebuild.py frontend` 再复验，不要先误判为路由守卫或后端业务回退。

## 质量与避坑入口

- 高频陷阱索引：`memory/MEMORY.md`。
- 测试规范与测试数据隔离：`docs/standards/09-测试规范.md`。
- 后端开发规范：`docs/standards/04-后端开发规范.md`。
- 前端开发规范与 `lg-*` 设计系统：`docs/standards/05-前端开发规范.md`、`frontend-admin/src/assets/styles/global.css`。
- 数据库与迁移规范：`docs/standards/07-数据库与迁移规范.md`。
- UI 基线：`docs/standards/00-UI-Design-Baselines-and-Code-Specifications.md`。
- CI、页面异常、接口失败先分三类再定性：`工具配置/规则加载问题`、`环境前置问题`、`真实质量/安全问题`；不要把所有红灯都直接归因为业务代码缺陷。
- Codex 通用执行协议：`docs/standards/codex-task-execution-policy.md`。

## 触发协议

### 代码审计 / 代码评审 / 安全审计 / 生产可用性审计

审计报告写入：

```text
docs/quality/code-audit-YYYY-MM-DD-<short-topic>.md
```

落盘动作遵循 `AGENTS.override.md` 的授权门、自适应路由与审计归档边界；主线程默认直接完成，只有派工净收益明确时才使用审计/归档型子智能体，且始终负责结论和验收。

### 飞书确认交互

遇到必须用户决策且需要飞书确认时，读取：

```text
docs/prompt/lark-confirmation-flow.md
```

## 文档入口

- 文档索引：`docs/README.md`
- 快速开始：`docs/standards/01-快速开始.md`
- 系统架构：`docs/standards/02-系统架构.md`
- 业务模块说明：`docs/standards/03-业务模块说明.md`
- API 契约：`docs/standards/06-API契约规范.md`
- 权限与审批：`docs/standards/08-权限与审批流程.md`
- 安全规范：`docs/standards/11-安全规范.md`
- Codex 任务执行策略：`docs/standards/codex-task-execution-policy.md`
