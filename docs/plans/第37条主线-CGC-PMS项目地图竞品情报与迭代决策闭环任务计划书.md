# 第37条主线：CGC-PMS 项目地图、竞品情报与迭代决策闭环任务计划书

**Goal:** 以 `develop/1.5` 当前真实工作区为唯一现行基线，建立“项目地图 → 竞品情报 → 迭代决策 → 既有实施链”的完整闭环，使系统能够基于仓库事实和外部正式证据持续回答“CGC-PMS 下一步最值得做什么”，并把通过决策门的候选安全送入现有 Backlog 与 AutoPilot。
**Architecture:** 在现有文档治理和 AutoPilot 之上增加一个最小、文件化、可审计的产品情报前置层；复用 `docs/backlog/ad-hoc-plan.md`、`ready-issues.md`、现有 refill/runner 和质量归档，不新建服务、数据库、通用 Agent 平台或第二套任务系统，不把竞品功能直接等同于本项目需求，不读取或依赖 `archive/v1.0/private/`，先完成两个稳定人工/主线程闭环后再判断是否需要脚本化或技能化。

> 计划状态：已实施（产品情报前置层完成；业务 Candidate 尚未实施）
> 计划基线：`develop/1.5@a2d58b591`
> 编制日期：2026-07-11
> 历史衔接：v1.0 第1～36条主线已封存，本计划是 v1.5 第一条现行主线，编号延续为第37条
> 执行方式：授权后默认由项目总负责人直接执行；仅在并行收益、专业隔离、长耗时卸载或独立复核价值明确高于派工成本时使用子智能体
> Git 边界：本计划不授权提交、推送、生产发布或生产数据访问

---

## 1. 结论与实施建议

当前最需要补的不是另一套编码工具或更多固定子代理，而是现有实施系统之前的“产品情报与方向决策层”。

当前仓库已经具备较强的第四阶段能力：任务准入、Ready 合同、AutoPilot refill、执行、验证、审查、归档和安全停止。真正缺失的是前三阶段的统一事实载体和可重复决策方法：

1. 项目事实分散在代码、规范、长期计划、手册和历史报告中，没有一份现行、可更新的项目地图。
2. 现有长期计划包含竞品样本，但属于 2026-07-08 的静态分析，缺少证据更新时间、版本、能力变化和失效检查。
3. 当前候选池主要继承历史计划，没有明确记录“仓库缺口 + 外部证据 + 用户价值 + 实施代价”的完整推导链。
4. AutoPilot 能把候选拆成 Ready 并实施，但不负责联网研究，也不应自行把竞品能力直接转成开发任务。

因此本主线采用以下闭环：

```text
当前仓库与现行文档
        ↓
项目地图（事实层）
        ↓
竞品情报（外部证据层）
        ↓
迭代决策（判断层）
        ↓
Ad-hoc Candidate（候选层）
        ↓
严格 Ready Issue（实施合同）
        ↓
既有 AutoPilot / 正常开发流程
        ↓
验证、归档并刷新项目地图
        ↺
```

---

## 2. 当前工作区变化基线

### 2.1 Git 与版本状态

计划编制时的客观状态：

| 项目 | 当前事实 |
| --- | --- |
| 当前分支 | `develop/1.5` |
| 当前提交 | `a2d58b591 docs: close v1.0 workspace archive` |
| 远端跟踪 | `origin/develop/1.5` |
| 工作区 | 干净，无未提交修改 |
| v1.0 基线 | 标签 `v1.0.0` |
| 当前阶段 | v1.5 初始化与下一主线选题 |
| Ready 队列 | 空 |
| Blocked 队列 | 空 |

实施开始时必须重新执行：

```powershell
git branch --show-current
git status --short
git log -1 --oneline --decorate
```

若基线发生变化，先更新本节和项目地图，不允许带着旧快照继续裁决。

### 2.2 v1.0 归档带来的结构变化

本轮工作区已经完成以下治理调整：

1. v1.0 第1～36条主线计划迁入 `docs/archive/v1.0/plans/`。
2. v1.0 质量报告迁入 `docs/archive/v1.0/quality/`。
3. v1.0 迭代记录迁入 `docs/archive/v1.0/iterations/`。
4. v1.0 问题与审计报告迁入 `docs/archive/v1.0/issues/`。
5. v1.0 backlog 保存为 `docs/archive/v1.0/backlog-snapshot/` 只读快照。
6. 当前规范统一迁入 `docs/standards/`，作为 v1.5 现行规范。
7. `docs/plans/`、`docs/quality/`、`docs/iterations/` 重置为 v1.5 活动目录。
8. 当前 `ready-issues.md` 和 `blocked-issues.md` 均为空，不能复用 v1.0 的 Done、测试数量或上线裁决作为 v1.5 当前证据。

### 2.3 归档边界

| 资料 | 用途 | 是否可作为当前事实 |
| --- | --- | --- |
| `docs/standards/**` | v1.5 当前规范 | 是 |
| `docs/backlog/**` | 当前选题与执行状态 | 是 |
| `docs/archive/v1.0/**` | 历史设计、决策和验收参考 | 仅作历史参考，必须复验 |
| `archive/v1.0/private/**` | 本地私有封存 | 禁止读取、扫描、总结或作为运行依赖 |

### 2.4 当前产品规模粗基线

以下数据只用于证明项目已是实质性业务系统，不代表模块完成度：

| 指标 | 2026-07-11 粗计数 |
| --- | ---: |
| 后端一级业务/技术域目录 | 32 |
| Controller 文件 | 53 |
| 前端 Vue 文件 | 129 |
| Flyway migration 文件 | 134 |

计数必须由仓库命令重新生成，禁止手工长期维护为“永远正确”的数字：

```powershell
(Get-ChildItem backend/src/main/java/com/cgcpms -Directory | Measure-Object).Count
(rg --files backend/src/main/java | rg 'Controller\.java$' | Measure-Object).Count
(rg --files frontend-admin/src | rg '\.vue$' | Measure-Object).Count
(rg --files backend/src/main/resources/db/migration | Measure-Object).Count
```

---

## 3. 现有能力与缺口重新整理

### 3.1 已经具备的能力

#### 产品与业务基础

- 产品定位已经明确：面向建筑工程总包项目全过程管理，而不是通用软件研发项目管理。
- 业务主线已覆盖项目、合同、变更签证、成本、采购、收货、库存、领料、分包、结算、付款、发票、审批、通知、预警和驾驶舱。
- 当前技术架构、业务模块、API、数据库、权限、测试、安全和运维规范已经进入 `docs/standards/`。

#### 迭代治理基础

- `docs/backlog/ad-hoc-plan.md` 保存临时候选。
- `docs/backlog/ready-issues.md` 定义严格实施合同。
- `docs/backlog/blocked-issues.md` 保存无法安全推进的前置。
- `docs/backlog/current-focus.md` 保存当前版本、阶段和执行边界。
- `scripts/codex-autopilot/autopilot-refill.ps1` 能从 Ad-hoc 和长期计划选择候选并调用 Planner 形成 Ready。
- 现有 runner 已具备 checkpoint、stop/pause、验证、审查、收口和本地提交边界。

### 3.2 当前缺口

| 阶段 | 当前状态 | 核心缺口 |
| --- | --- | --- |
| 1. 项目地图 | 有丰富输入，没有唯一现行地图 | 无统一能力树、业务闭环、实现状态、证据路径和更新时间 |
| 2. 竞品情报 | 长期计划中有静态样本 | 无刷新记录、来源等级、版本变化、国内竞品覆盖和事实/推断分离 |
| 3. 方向判断 | 有 Candidate 池和长期方向 | 无从事实到结论的统一决策卡、否决门和未选原因 |
| 4. 实施 | 已较成熟 | 不应重建，只需接收经过前三阶段筛选的候选并回写结果 |

### 3.3 当前断点

现有 refill 的输入仅包括当前 focus、Ready、Blocked、Ad-hoc 和长期计划；它没有也不应直接承担互联网研究。因此目前存在以下断点：

```text
外部市场变化 ──X──> Candidate
当前代码能力 ──分散──> Candidate
Candidate ──已有──> Ready ──已有──> 实施
```

本主线只修复前两个断点，不改写已经稳定的后半段。

---

## 4. 方案比较与选型

### 4.1 方案 A：只更新长期计划书

优点：修改最少。
缺点：项目事实、竞品资料和决策混在一个大文件中，无法独立刷新，仍会快速过期。
结论：不采用。

### 4.2 方案 B：文件化产品情报前置层，复用现有实施链

优点：可审计、低成本、Git 友好、能立即接入现有 Candidate/Ready/AutoPilot，失败时可整组回滚。
缺点：第一阶段仍需要主线程主动触发刷新，不是后台定时服务。
结论：采用。

### 4.3 方案 C：新建产品情报服务、数据库和定时抓取系统

优点：长期可高度自动化。
缺点：在流程和证据模型尚未经过真实迭代验证前，会引入抓取、去重、版本、调度、认证、存储和运维复杂度。
结论：当前禁止；完成两个稳定闭环后另行评估。

---

## 5. 目标产物与信息架构

### 5.1 新增目录

计划新增：

```text
docs/product-intelligence/
├── README.md                 # 入口、边界、更新规则、状态词典
├── project-map.md            # 当前项目事实地图
├── competitor-analysis.md    # 竞品、来源和能力差距
└── evolution-decision.md     # 候选方向、裁决和进入 backlog 的记录
```

### 5.2 需要修改的现有文件

| 文件 | 修改目的 |
| --- | --- |
| `docs/README.md` | 增加产品情报入口 |
| `docs/backlog/ad-hoc-plan.md` | 仅写入通过方向门的 Candidate/ReadyToSplit |
| `docs/backlog/current-focus.md` | 记录当前决策周期、选中方向和继续条件 |
| `docs/未来开发计划.md` | 只保留长期未完成方向和边界，不复制详细竞品资料 |

### 5.3 明确不修改

第一版不修改：

- 业务代码、数据库表和 Flyway migration。
- 前后端 API。
- `autopilot-run-continuous.ps1`、`autopilot-exec-issue.ps1` 和 runner 控制面。
- Ready 准入规则与 stop/pause 语义。
- v1.0 归档内容。
- 任何私有智能体目录或 `archive/v1.0/private/`。

---

## 6. 核心数据契约

### 6.1 项目地图状态词典

每项能力只能使用以下状态：

| 状态 | 定义 | 最低证据 |
| --- | --- | --- |
| `Implemented` | 存在真实前后端/数据链路，且有当前验证证据 | 代码路径 + 当前验证命令/报告 |
| `Partial` | 已有部分链路，但缺少关键写入、权限、数据或验收 | 已有路径 + 明确缺口 |
| `Documented` | 只在现行规范或计划中定义 | 文档路径 |
| `Planned` | 已形成 Candidate 或 Ready，但尚未完成 | backlog 锚点 |
| `Frozen` | 因业务对象、数据或边界不足明确冻结 | 冻结原因 + 解冻条件 |
| `Unknown` | 证据不足，不能判断 | 待核验清单 |

禁止使用“基本完成”“大致可用”等无法验收的模糊状态。

### 6.2 项目地图维度

`project-map.md` 至少包含：

1. 产品定位与目标用户。
2. 核心业务闭环。
3. 业务域与功能入口。
4. 后端域、Controller、Service、数据库对象和前端页面映射。
5. 权限、租户、项目隔离和审批边界。
6. 关键经营口径与金额链路。
7. 测试、CI、部署和运行态能力。
8. 已知缺口、冻结域和待确认项。
9. 地图生成基线：分支、commit、生成时间、证据路径。

### 6.3 竞品证据记录

每条竞品能力必须包含：

```text
对象：产品/项目名称
能力域：成本 / 合同 / 现场 / 计划 / 供应链 / 协同 / AI 等
事实描述：来源直接支持的事实
来源：官方产品页、官方文档、官方发布说明或一手仓库
来源等级：A 官方文档 / B 官方产品材料 / C 一手开源仓库
checkedAt：YYYY-MM-DD
版本或页面状态：能确认则记录，不能确认写 Unknown
对 CGC-PMS 的启示：推断，必须与事实分开
候选动作：可为空，不得由单条竞品事实直接触发开发
```

不得把搜索摘要、营销口号、第三方转载或模型记忆当成唯一事实依据。

### 6.4 迭代决策卡

每个候选方向必须包含：

| 字段 | 要求 |
| --- | --- |
| 候选名称 | 面向业务能力，不使用工具名代替需求 |
| 当前缺口 | 必须引用项目地图证据 |
| 外部证据 | 至少一个 A/B/C 级来源，核心方向优先两个独立来源 |
| 用户与业务价值 | 明确服务对象、场景和结果 |
| 战略匹配 | 必须符合建筑总包全过程管理定位 |
| 最小闭环 | 能在当前架构内形成可验收结果 |
| 依赖与前置 | 数据、权限、流程、真实角色和运行环境 |
| 风险 | 金额、权限、租户、数据一致性、审批和迁移风险 |
| 工作量等级 | S / M / L，仅用于排序，不虚构精确工时 |
| 证据置信度 | 高 / 中 / 低 |
| 裁决 | 推荐 / 保留 / 冻结 / 否决 |
| 未选原因 | 未进入本轮的明确原因 |

### 6.5 方向否决门

满足任一项时不得进入 `ad-hoc-plan.md`：

1. 当前项目缺口未经仓库证据确认。
2. 只有竞品“有这个功能”，没有本项目用户价值。
3. 需要生产数据、生产发布或未授权外部系统才能验证。
4. 依赖真实业务对象尚不存在，例如冻结的总工程师技术域。
5. 涉及金额、权限、租户、审批或数据一致性，但没有验证与回滚方案。
6. 最小闭环无法在一次主线或可控 Epic 中拆解。
7. 与现有 Candidate 重复，且没有新的证据或边界变化。

---

## 7. 竞品研究范围与首批证据基线

### 7.1 组合对标，不复制单一产品

首批对标分为四组：

| 分组 | 代表对象 | 研究重点 |
| --- | --- | --- |
| 国内施工总包数字化 | 广联达数字施工、PMSmart 等 | 成本、进度、现场、物料、企业与项目协同 |
| 国际施工现场与协同 | Procore、Autodesk Construction Cloud | 日报、照片、RFI、Submittal、移动现场、文档协同 |
| 资本项目与计划控制 | Oracle Primavera Unifier/P6、OpenProject | WBS、计划、成本控制、跨项目可视性、流程配置 |
| 轻 ERP 与项目经营 | ERPNext、Odoo | 采购、库存、供应商、项目成本与盈利 |

### 7.2 2026-07-11 已核验的官方入口

以下链接只作为首批研究入口，实施时必须记录新的 `checkedAt`：

- 广联达产品中心：<https://www.glodon.com/product.html>
- 广联达数字项目管理平台说明：<https://www.glodon.com/news/678>
- Procore Daily Log 官方文档：<https://support.procore.com/products/online/user-guide/project-level/daily-log>
- Autodesk Construction Cloud 官方说明：<https://old.construction.autodesk.com/why-autodesk-construction-cloud/>
- Oracle Primavera Unifier 26 官方文档：<https://docs.oracle.com/en/industries/construction-engineering/primavera-unifier/26/index.html>
- OpenProject Gantt 官方文档：<https://www.openproject.org/docs/user-guide/gantt-chart/>
- ERPNext Project 官方文档：<https://docs.frappe.io/erpnext/project>
- Odoo Project 19 官方文档：<https://www.odoo.com/documentation/19.0/applications/services/project.html>

### 7.3 研究输出要求

- 首轮至少覆盖 6 个对象，其中至少 2 个国内施工/工程产品、2 个国际施工产品、1 个计划控制产品、1 个 ERP/项目经营产品。
- 每个对象最多提炼 3～5 个与 CGC-PMS 当前缺口直接相关的能力，禁止百科式抄录。
- 每项差距必须标注“竞品领先、本项目领先、能力不同或证据不足”。
- 外部产品变更不自动改变路线；只有项目地图和决策卡共同支持时才形成 Candidate。

---

## 8. 分阶段实施任务

## M0：建立 v1.5 事实基线与边界

### 目标

确认当前分支、归档边界、文档入口、Backlog 状态和实施链真实有效，防止把 v1.0 历史结论误当成当前事实。

### 执行任务

1. 核对 `AGENTS.override.md`、`AGENTS.md` 和子目录规则。
2. 记录 branch、HEAD、status、`v1.0.0` 标签和 v1.5 活动目录。
3. 核对 `docs/plans/README.md`、`docs/quality/README.md`、`docs/iterations/README.md`。
4. 核对 Ready、Blocked、Current Focus 和 Ad-hoc 当前状态。
5. 确认 `docs/archive/v1.0/**` 只读、`archive/v1.0/private/**` 禁止访问。
6. 用 CodeGraph 优先确认关键业务链，再以 `rg` 补足文件名、跨语言入口和工具召回不足。

### 验收标准

- `project-map.md` 的基线信息与当前 Git 状态一致。
- 不引用 v1.0 测试数量作为 v1.5 当前通过证据。
- 没有读取、扫描或依赖私有归档。

## M1：建立当前项目地图

### 目标

形成一份可以定位到真实代码、规范和验证入口的现行项目地图，而不是 README 摘要。

### 执行任务

1. 创建 `docs/product-intelligence/README.md`，定义用途、状态词典、证据等级和更新触发器。
2. 创建 `docs/product-intelligence/project-map.md`。
3. 从 `docs/standards/02-系统架构.md` 和 `03-业务模块说明.md` 提取现行架构与业务主线。
4. 对每个核心业务域建立“前端入口 → API → Controller/Service → 数据对象 → 权限/审批 → 测试”的证据链。
5. 单独标注金额、租户、项目隔离、审批状态机和真实角色验收边界。
6. 把长期计划中的“已完成”结论重新分类为历史记录、当前已验证或待复验。
7. 记录 `Unknown` 和 `Frozen`，禁止为了地图完整度猜测。

### 首批地图范围

- 项目与主数据。
- 合同、签证与收入。
- 成本、目标成本与经营分析。
- 采购、收货、库存与领料。
- 分包、计量与结算。
- 付款、发票与资金日记账。
- 审批、通知、预警与驾驶舱。
- 用户、角色、菜单、数据范围、审计和文件。
- 测试、CI、部署、监控与 AutoPilot。

### 验收标准

- 每个核心域至少有一个真实代码/文档证据锚点。
- `Implemented` 项必须有当前验证证据；没有验证则降为 `Partial` 或 `Unknown`。
- 地图注明 branch、commit、generatedAt 和 nextRefreshTrigger。
- 地图能明确回答“已有、部分、计划、冻结、未知”五类问题。

## M2：建立可刷新的竞品情报

### 目标

把现有长期计划里的静态对标升级为带来源、时间、版本和差距结论的证据库。

### 执行任务

1. 创建 `docs/product-intelligence/competitor-analysis.md`。
2. 按第7节完成首批组合对标。
3. 每条资料区分“来源事实、对本项目的推断、候选动作”。
4. 对与当前 Candidate 直接相关的方向重点研究：采购补货、现场日报、WBS 延期预警、供应商履约。
5. 补充国内施工总包产品证据，修正现有长期计划偏国际/通用产品的问题。
6. 对无法从官方资料确认的功能标记 `Unknown`，不得推断为“竞品已实现”。
7. 给每个来源记录 checkedAt；超过 90 天未复核时标记为待刷新，不自动失效。

### 验收标准

- 至少 6 个对标对象满足来源与 checkedAt 要求。
- 至少形成 10 条与当前项目地图直接对应的能力差距。
- 每个关键结论能追溯到官方文档、官方产品材料或一手仓库。
- 竞品事实和本项目建议在文档结构上明确分开。

## M3：建立迭代方向决策机制

### 目标

把“想做的功能列表”转换为有证据、有边界、有未选原因的决策结果。

### 执行任务

1. 创建 `docs/product-intelligence/evolution-decision.md`。
2. 使用第6.4节决策卡评估现有五个业务候选：
   - 后端接口无前端入口治理。
   - 采购补货建议。
   - 现场日报 / 施工日志。
   - WBS 任务依赖与延期预警。
   - 供应商履约档案。
3. 将“子智能体超时治理”保留为工程治理候选，与产品方向分组比较，禁止混成产品价值排序。
4. 对候选执行否决门检查。
5. 形成 3～5 个有序候选，并明确本轮推荐 1 个、保留项、冻结项和未选原因。
6. 推荐项必须给出最小业务闭环、预期影响、验证路径和回滚边界。
7. 需要产品所有者决定的方向标记 `需要确认`，不得由自动系统虚构偏好。

### 排序原则

依次考虑：

1. 用户与经营价值。
2. 与建筑总包全过程定位的匹配度。
3. 当前仓库缺口证据强度。
4. 现有数据和架构复用程度。
5. 权限、金额、租户、审批和迁移风险。
6. 最小闭环可验证性。

不采用虚假的精确 ROI 或精确工时模型；信息不足时降低置信度，不补造数字。

### 验收标准

- 至少 3 个候选完成完整决策卡。
- 推荐项同时具备项目证据和外部证据。
- 每个未选项都有明确原因。
- 决策结果不包含 Claude Code、OpenCode、MCP 等与产品需求无关的工具选型。

## M4：接入现有 Backlog 与实施链

### 目标

只把通过决策门的方向送入现有治理链，不绕过 Candidate 和 Ready 准入。

### 执行任务

1. 将本轮推荐项以 `Candidate` 写入 `docs/backlog/ad-hoc-plan.md`，带上 `evolution-decision.md` 锚点。
2. 更新 `docs/backlog/current-focus.md`，记录当前决策周期、推荐方向、前置和继续条件。
3. 只有当范围、非目标、验收命令、风险和回滚完整时，才将 Candidate 升为 `ReadyToSplit`。
4. 由现有 `autopilot-refill.ps1` 把候选拆成严格 Ready；本主线不直接修改 runner。
5. 先执行 refill 决策/预演，确认来源选择正确且不超过 5 条 Ready。
6. 未经新授权，不启动业务实现、不提交、不 push。

### 计划中的验证命令

```powershell
. scripts/codex-autopilot/autopilot-refill.ps1
Get-AutopilotRefillDecision -RepoRoot (Get-Location).Path | ConvertTo-Json -Depth 6

powershell -NoProfile -ExecutionPolicy Bypass `
  -File plugins/cgc-pms-autopilot/scripts/autopilot-loop-runner.ps1 `
  -DryRun `
  -ReadyIssuePath docs/backlog/ready-issues.md
```

### 验收标准

- 推荐项进入 Ad-hoc 后能被 refill 识别。
- Candidate 没有被直接当成已实施能力。
- Ready 仍通过现有 lint 和字段完整性校验。
- stop/pause/enabled 与 no-push 边界没有被改变。

## M5：完成首轮闭环验收与回写

### 目标

证明前三阶段产物真的能改善选题，而不是新增三份无人维护的文档。

### 执行任务

1. 选取一个候选完成“地图证据 → 竞品证据 → 决策卡 → Ad-hoc Candidate”的全链路演练。
2. 对照原长期计划，记录本轮排序发生变化或保持不变的原因。
3. 校验所有链接、路径、状态词和时间戳。
4. 更新 `docs/README.md` 增加产品情报入口。
5. 更新 `docs/未来开发计划.md`，只保留长期方向和到决策记录的链接，删除重复叙述。
6. 形成 `docs/quality/mainline-37-product-intelligence-acceptance.md` 正式验收报告。
7. 将剩余风险同步到 Ready、Blocked 或 Current Focus，禁止只留在质量报告。

### 验收标准

- 四阶段链路可以从文档入口完整追踪。
- 至少一个推荐 Candidate 被现有 refill 正确识别。
- 没有把外部产品宣传内容直接转为 Ready。
- 项目地图与 Git 基线一致。
- `git diff --check` 通过。
- 只有正式长期资产进入版本管理，没有日志、截图、缓存和临时输出。

---

## 9. 执行顺序与检查点

| 顺序 | 阶段 | 允许状态变化 | 检查点 |
| ---: | --- | --- | --- |
| 1 | M0 | 只读核验 | branch/status/archive boundary |
| 2 | M1 | 新增产品地图文档 | 事实、状态、证据完整性 |
| 3 | M2 | 新增竞品情报文档 | 官方来源、checkedAt、事实/推断分离 |
| 4 | M3 | 新增决策记录 | 否决门、排序、未选原因 |
| 5 | M4 | 更新 Ad-hoc/Focus，必要时生成 Ready | 不绕过 Ready 准入，不改 runner |
| 6 | M5 | 更新索引、长期计划并写验收报告 | diff、链接、backlog、剩余风险 |

每阶段完成后，主线程重新判断：

1. 当前属于实现、验收、运维还是审计。
2. 是否升级为跨模块问题。
3. 是否涉及权限、安全、金额、租户或数据一致性。
4. 输出是否直接用于通过/不通过或上线裁决。
5. 继续直接执行是否仍比派工更安全高效。

---

## 10. 更新触发器与生命周期

### 10.1 项目地图刷新

满足任一条件时刷新：

- 一个产品主线或 Epic 完成并归档。
- 核心业务域、权限模型、数据库关系或部署架构变化。
- 当前地图基线落后当前分支 30 天以上。
- 方向判断发现地图证据与代码不一致。

### 10.2 竞品情报刷新

满足任一条件时刷新：

- Ready 为空且需要下一轮选题。
- 某候选需要外部证据才能判断。
- 已引用来源超过 90 天未核验。
- 对标产品发布与当前候选直接相关的重大能力变化。

### 10.3 迭代决策刷新

满足任一条件时重新裁决：

- 项目地图发生影响候选的实质变化。
- 竞品证据更新导致差距判断变化。
- 用户改变产品目标、范围或优先级。
- 推荐项进入 Blocked 或实施成本显著变化。
- 当前 Candidate/Ready 队列耗尽。

### 10.4 自动化升级条件

只有连续完成两个真实闭环且字段稳定后，才允许评估：

- 只读项目清单脚本。
- 产品情报刷新技能。
- 固定触发短语或定时监控。
- 竞品来源变更检测。

升级前必须证明能减少维护成本，不能为了“全自动”引入新服务和数据库。

---

## 11. 测试与验收矩阵

| 类别 | 验证内容 | 通过标准 |
| --- | --- | --- |
| Git 基线 | branch、HEAD、status | 与文档记录一致，实施前无未知修改 |
| 规则边界 | 归档与私有目录 | 不读私有归档，历史结论不冒充当前证据 |
| 项目地图 | 业务域、实现状态、证据链 | 核心域有路径和状态，Unknown 不被猜测填充 |
| 竞品证据 | 来源、日期、事实/推断 | 首轮至少 6 个对象，核心结论可追溯 |
| 决策质量 | 候选、否决门、未选原因 | 至少 3 张完整决策卡，1 个明确推荐项 |
| Backlog 接入 | Candidate/Ready 边界 | refill 能识别，Candidate 不直接实施 |
| AutoPilot 兼容 | dry-run/checkpoint | 现有 stop/pause/no-push 语义不变 |
| 文档质量 | 链接、重复、格式 | 文档入口清晰，无相互冲突的当前结论 |
| 工作区质量 | `git diff --check`、status | 无空白错误，无临时产物和越界修改 |

---

## 12. 风险与控制措施

### 风险 1：项目地图变成手工百科

控制：只记录会影响产品判断的能力和证据；粗计数由命令生成；不逐文件抄录代码。

### 风险 2：竞品功能绑架产品路线

控制：竞品只是证据之一；必须同时通过用户价值、产品定位、当前缺口和最小闭环四项判断。

### 风险 3：营销页面导致错误结论

控制：优先官方文档和发布说明；营销页只能作为 B 级证据；无法确认时标记 Unknown。

### 风险 4：静态资料再次过期

控制：记录 checkedAt、版本和刷新触发器；超过 90 天先复核再用于关键决策。

### 风险 5：Candidate 绕过 Ready 准入

控制：方向层只允许写 Candidate/ReadyToSplit；实施仍由现有 Ready lint 和 runner 门禁控制。

### 风险 6：重复建设 AutoPilot

控制：MVP 不修改 runner、不新增任务数据库、不建立第二套状态机。

### 风险 7：历史归档污染当前结论

控制：历史报告只用于发现线索；所有当前能力重新核对；私有归档完全排除。

### 风险 8：方向选择需要产品所有者偏好

控制：证据不能替代产品选择；涉及市场定位、商业模式或目标客户变化时标记“需要确认”。

---

## 13. 回滚方案

本主线第一版以文档和 Backlog 连接为主，回滚按以下层级执行：

1. 未进入 Ad-hoc：删除或回退 `docs/product-intelligence/**` 和索引变更即可。
2. 已进入 Ad-hoc、未进入 Ready：移除本主线新增 Candidate，保留决策记录并标记撤回原因。
3. 已拆 Ready、未实施：按 Ready 规则撤回并在 Current Focus 记录原因，不启动 runner。
4. 已开始实施：不由本计划直接回滚业务代码，转入对应实施主线的回滚和验收流程。

禁止通过删除 v1.0 归档、重写 Git 历史或清理私有目录完成回滚。

---

## 14. 完成定义（Definition of Done）

本主线只有同时满足以下条件才判定通过：

- [x] `docs/product-intelligence/README.md` 建立并定义边界、状态、证据和刷新规则。
- [x] `project-map.md` 基于当前 branch/commit 建立，核心域可追溯。
- [x] `competitor-analysis.md` 覆盖至少 6 个对标对象并记录 checkedAt。
- [x] `evolution-decision.md` 至少完成 3 个候选决策卡和 1 个推荐结论。
- [x] 推荐项通过全部方向否决门。
- [x] 推荐项以 Candidate 或 ReadyToSplit 接入 Ad-hoc，而非直接实施。
- [x] 现有 refill 能正确识别推荐项。
- [x] `docs/README.md`、Current Focus 和未来开发计划完成一致性更新。
- [x] 正式验收报告写入 `docs/quality/`。
- [x] 所有剩余风险进入 Ready、Blocked 或 Current Focus。
- [x] `git diff --check` 通过，工作区无临时产物。
- [x] 未读取私有归档，未连接生产，未发布生产。
- [x] 提交和推送仅在用户另行明确授权后执行。

---

## 15. 主线结束后的下一步

通过本主线后，下一步不是继续扩展产品情报框架，而是从 `evolution-decision.md` 的首选方向中拆出一个最小 Ready Issue，按现有 AutoPilot 或正常开发流程完成真实业务闭环。该业务闭环完成后立即回写项目地图，并以此验证本机制是否真正改善了选题质量。

若连续两个闭环证明有效，再单独编写自动刷新计划；若没有减少误选、返工或重复研究，则保留文件化流程，不继续自动化。
