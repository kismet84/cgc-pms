# 第35条主线：Codex 执行路由与子智能体自适应分配机制调整任务计划书

**Goal:** 在不削弱项目安全、验收与 AutoPilot 治理边界的前提下，将 Codex 主线程的执行路由从“实施动作默认必须派工”调整为“授权门通过后默认由主线程自主完成；只有派工的明确净收益高于上下文转述、等待和回收成本时，才选择单子智能体或多子智能体”，并用静态一致性检查、行为场景验收和新会话 smoke test 证明规则实际生效。
**Architecture:** 采用最小且原子化的规则调整方案：根规则定义授权门、状态变更前检查与默认直做原则，项目 Skill 和派工模板承接条件派工细则，AutoPilot 规则将 A–F 从固定六角色编制改为职责检查表；M1–M3 可顺序编辑复核，但九个实时文件必须作为一个原子变更集一次生效。不新增运行时框架、评分器或状态系统，不追溯改写历史交付物，并继续保留 AutoPilot Ready、checkpoint、stop/pause/enabled、禁止自动 push、测试数据重置、并行上限和失败分类等既有硬边界。

## 1. 背景与问题

当前项目规则将主线程稳定定位为项目总负责人，并通过子智能体隔离实施、验收、运维和归档动作。这一治理方式对高风险变更有效，但现行表述存在三类机械化倾向：

1. 即使任务低风险、范围单一、上下文高度集中，也倾向于强制派工，增加上下文转述、线程等待和结果回收成本。
2. A–F 角色容易被理解为每轮必须固定启动六个子智能体，而不是覆盖需求、实现、测试、审查和归档责任的检查清单。
3. `model`、`thinking`、`reason` 与模型分配表可能在未实际派工时也被要求输出，形成无效流程负担。

本主线要解决的是执行路由机制的适配性，而不是取消项目总负责人制度或放宽风险控制。核心判断是：先经过授权门，再由主线程根据任务真实属性选择成本最低且证据充分的执行方式。

## 2. 目标与非目标

### 2.1 目标

- 建立统一授权门；授权门通过后默认由主线程直接完成，不设置“必须使用子智能体”的任务类别。
- 建立可解释的自适应路由机制，支持直接执行、单派和多派三种模式。
- 强制遵守的是用户授权、安全边界、客观证据和必要复核；子智能体只是满足这些要求的一种手段。
- 明确只有实际派工时才填写 `model`、`thinking`、`reason`；只有同时派出两个及以上子智能体时才输出模型分配表。
- 将 A–F 定义为动态职责检查表，允许按任务性质合并、裁剪或分阶段覆盖，但不得遗漏必要职责。
- 允许子智能体超时、悬挂或上下文不适配时，由主线程基于风险重新判断：收回直接执行、终止后重派或补充上下文后继续。
- 通过根规则、Skill、模板、AutoPilot 引用文档和文档索引的同步修改，避免实时规则互相冲突。

### 2.2 非目标

- 不取消项目总负责人对目标、边界、优先级、验收和上线裁决的责任。
- 不取消高风险任务的客观证据和必要复核要求，也不允许以“直接执行”替代足以支撑裁决的证据。
- 不新增调度服务、模型评分器、持久化队列或其他运行时组件。
- 不追溯修改既有 `docs/plans/`、`docs/quality/`、`docs/iterations/` 或插件历史产物。
- `docs/12-codex-subagents-implementation-plan.md` 默认保持历史原貌，仅作为历史方案留存，不作为本次实时规则修改对象。
- 本计划书调整本身不包含 Git 提交、push、生产发布、生产数据库连接或运行环境变更；后续规则实施中的提交或 push 仍须取得用户明确授权并通过其他安全边界。

## 3. 保留的安全边界

以下规则不因自适应路由而削弱：

- Ready Issue 边界按工作模式区分：普通交互任务在用户明确授权后不要求先进入 Ready；用户明确要求按主线、backlog 或治理流程推进时按该流程要求执行；AutoPilot 连续迭代仍严格要求任务先成为合格 Ready Issue，无合格 Ready 不实施对应业务或治理变更。
- 所有关键 checkpoint 继续检查 `stop.flag`、`pause.flag`、`enabled.flag`，并保留任务开始前、选题后、改代码前、验证前、自动合并前和报告更新后的检查要求。
- `autoPush=false` 与 no push 表示禁止自动推送，不等于永久禁止提交或 push；用户明确授权提交或 push 后，仍须通过分支、工作区、验证、checkpoint 等其他适用安全边界。不自动发布生产，不连接生产数据库。
- 测试数据删除或重置仍必须同时满足 dev/test/demo 环境、本机数据库 host 和 `.codex-autopilot/ALLOW_TEST_DATA_RESET` marker 三项条件。
- 每轮最多并行三个完全无关联、无任何代码关联的任务；无法证明独立时必须串行。
- CI、页面、接口或命令失败必须先分类为工具配置类、环境前置类或真实质量/安全类，再决定整改或阻塞。
- 数据库 schema、权限、安全、金额口径、租户隔离和上线裁决必须保留客观证据；证据不足时必须明确写出“证据不足”，不得猜测通过。
- 私有禁止目录、临时产物治理、正式报告目录用途和只读清理预览等边界保持不变。
- 任何代码、配置、文档、Git 或运行环境状态变更前，执行者必须核对 `git branch --show-current` 与 `git status --short`；怀疑并行隔离、分支归属或工作区冲突时再执行 `git worktree list`。该检查适用于主线程直接执行和子智能体执行，不只适用于派工前。
- 直接执行只改变责任承载方式，不改变验证、留痕、回滚和最终裁决标准。

## 4. 实时规则源修改矩阵

| 实时文件 | 修改口径 | 明确保留 |
| --- | --- | --- |
| `AGENTS.override.md` | 在最高优先级项目规则中定义授权门、默认直接执行、自适应路由三选一、状态变更前检查、条件派工和超时收回或重派规则 | 总负责人责任、安全硬边界、客观证据与必要复核要求 |
| `AGENTS.md` | 与 override 对齐通用表述，删除“所有工程动作一律派工”的绝对化冲突，保留下层规则覆盖说明 | 规则优先级、禁止目录、质量与 Git 边界 |
| `.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md` | 将工作流改为先过授权门，再评估路由；仅实际派工时生成派工元数据 | 计划、验收、阻塞裁决、收口责任 |
| `docs/prompt/subagent-dispatch-template.md` | 模板改为条件使用；保留完整派工字段，注明单派无需模型分配表，多派才需要 | 身份边界、目标、范围、禁止事项、验收输出 |
| `docs/prompt/README.md` | 解释模板触发条件与三类路由关系，避免把模板误解为每个任务必填 | prompt 索引与使用入口 |
| `docs/README.md` | 更新项目文档导航和当前执行路由说明，指向实时规则与模板 | 历史文档标识、文档治理边界 |
| `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md` | AutoPilot 每轮按职责缺口动态决定直接执行、单派或多派；A–F 改为职责检查表 | Ready、checkpoint、连续模式和收口流程 |
| `plugins/cgc-pms-autopilot/references/owner-boundary.md` | 明确授权门通过后默认由主线程自主完成；高风险和正式裁决强制充分证据或必要复核，但不自动等同于派工 | 总负责人最终责任、证据与安全边界 |
| `plugins/cgc-pms-autopilot/references/install.md` | 更新安装后生效规则说明和静态核对方法，确保根规则、Skill、引用文档一致 | 安装边界、插件启用方式、no push |

历史文件不在修改矩阵中。既有计划书、质量报告、迭代报告和插件产物不做追溯性文字统一；`docs/12-codex-subagents-implementation-plan.md` 默认保持历史原貌。

## 5. 自适应路由机制

### 5.1 授权门

主线程在任何写入、运行、Git 或运行环境动作前，先判断：

1. 用户是否明确授权该类动作，或该动作是否属于已授权工作流中的正常实施步骤。
2. 项目规则是否允许该动作，是否命中禁止目录、生产环境、数据重置、适用的 Ready、checkpoint 或自动 push 限制。
3. 该任务是否属于普通交互、用户指定的主线/backlog 治理，或 AutoPilot 连续迭代；只有后两者按相应流程要求进入治理载体，其中 AutoPilot 必须来自合格 Ready Issue。
4. 任何代码、配置、文档、Git 或运行环境状态变更前，执行者是否已核对当前分支与 `git status --short`；怀疑冲突时是否补充 `git worktree list`。
5. 是否存在需要用户新增授权、外部协调或显著扩大范围的情形；提交或 push 是否已有用户明确授权。

授权门未通过时停止执行并说明阻塞；通过后才进入路由选择。

### 5.2 五项路由因素

授权门通过后，主线程默认直接完成。只有以下因素能够证明派工带来的并行收益、专业隔离、上下文隔离、长耗时卸载或独立证据价值明确高于派工成本时，才选择单派或多派；不要求建立数值评分系统：

- 风险：是否涉及数据库、权限、安全、金额、租户、并发、数据一致性或上线裁决。
- 耦合：是否跨模块、跨语言、跨运行态，是否需要多个文件或多条真实调用链共同判断。
- 并行收益：任务是否真正独立，是否能在不共享写集和状态的前提下降低总耗时。
- 上下文传递成本：派工所需背景是否接近任务本身工作量，转述是否容易遗漏关键约束。
- 独立证据需要：结果是否需要独立测试、审查、安全复核或正式裁决依据；证据可来自自动化测试、CI、静态扫描、真实账号验收或人工复核，不自动要求使用子智能体。

### 5.3 三种执行路由

#### 直接执行

这是授权门通过后的默认路由。范围集中、上下文完整，或派工成本不低于其净收益时，由主线程直接完成；风险较高本身不强制派工，但必须补足客观证据和必要复核。主线程直接执行时仍需遵守状态变更前检查、验证和收口要求，但不填写子智能体 `model`、`thinking`、`reason`，也不输出模型分配表。

#### 单子智能体派工

仅在专业隔离、上下文隔离、长耗时卸载或独立证据价值明确高于派工成本时使用。派工必须填写完整模板，包括 `model`、`thinking`、`reason` 和验收输出；单派不要求模型分配表。不得仅因任务涉及数据库、权限、安全、金额、租户或正式裁决就机械派工。

#### 多子智能体派工

仅在存在两个及以上真正独立的工作包，且并行净收益明确，或多个责任隔离确有必要时使用。必须先输出子智能体模型分配表，再逐个使用完整派工模板。多派不等于必须并行；并行仍受“最多三个完全无关联任务”限制，共享文件、模块、数据库、权限、安全、租户、金额或状态机的任务不得并行。

### 5.4 超时与收回

子智能体出现不回传、长时间 running 或等待超时时，主线程先做只读状态核验，不继续对同一悬挂线程硬等。随后重新评估五项路由因素：

- 若剩余工作已变为低风险、机械、上下文集中的任务，可中断原线程并收回主线程直接执行。
- 若仍需专业隔离或独立证据，应中断后重派短生命周期子智能体，补齐上下文、超时回报和最小证据要求。
- 若任务从普通实现升级为数据库、权限、安全、金额、租户或正式裁决问题，必须提高证据与复核强度；可由主线程补证，也可升档或补派适配角色，不得因赶进度降低裁决标准。

## 6. AutoPilot 动态职责

AutoPilot 中的 A–F 保留为职责检查表，而不是固定六个子智能体：

- A：需求澄清、架构边界、Ready 拆解与任务性质标注。
- B：前端/UI 实现责任。
- C：后端/API、数据库和业务实现责任。
- D：测试、用例、回归和裁决必需验证责任。
- E：代码审查、安全审查和风险识别责任。
- F：文档、backlog、iteration、上线清单与收口责任。

每轮先判断哪些职责实际存在，再决定由主线程直接承担、单派承担或多派隔离承担。A–F 只表示责任覆盖，不自动映射为六个独立线程。纯文档补货轮可以只覆盖 A/F；单一低风险实现可合并实现与自检。D 的裁决必需验证证据与 E 的适用风险审查证据不能省略，但可以由主线程、同一执行者、自动化工具或独立复核者提供，不必自动拆成独立线程。正式通过/不通过、上线或高风险裁决证据不足时，必须明确判定“不通过”或“需要确认”，不得以角色已覆盖代替证据。动态裁剪不得省略 Ready、checkpoint、health gate、失败分类、最终复验、归档和本地收口等适用步骤。

### 6.1 Ready 在 AutoPilot 中的专属约束

- 普通交互任务不因 AutoPilot 规则存在而被强制写入 `ready-issues.md`；用户明确授权后按授权范围执行。
- 用户明确要求按主线、backlog 或治理流程推进时，按对应计划和治理载体执行。
- 只有进入 AutoPilot 连续迭代语义的任务，才强制遵守合格 Ready、补货、解阻和 checkpoint 全流程；动态路由不得绕过该前置。

## 7. M1–M4 实施步骤

### M1：根规则统一

**精确文件：**

- `AGENTS.override.md`
- `AGENTS.md`

**修改口径：**

- 定义授权门和直接执行、单派、多派三种路由。
- 明确授权门通过后默认直接执行；删除“按任务类别强制派工”的要求，改为强制授权、安全边界、客观证据和必要复核。
- 区分普通交互、用户指定治理流程与 AutoPilot 的 Ready 适用边界。
- 将 branch/status 前置检查扩展到任何状态变更前，并明确显式提交或 push 授权边界。
- 明确只有实际派工才写 `model/thinking/reason`，两个及以上实际派工才写模型分配表。
- 加入超时后收回直做或重派的判断规则。
- 保留所有安全、证据、Ready、checkpoint、no push 和数据边界。

**静态命令：**

```powershell
rg -n "授权门|直接执行|单.*派|多.*派|model|thinking|reason|模型分配表|超时|重派" AGENTS.override.md AGENTS.md
rg -n "Ready|stop.flag|pause.flag|enabled.flag|autoPush|ALLOW_TEST_DATA_RESET|失败分类|最多.*3" AGENTS.override.md AGENTS.md
git diff --check -- AGENTS.override.md AGENTS.md
```

**预期结果：** 两个根规则对默认直做、Ready 范围、状态变更前检查和 push 授权边界无冲突；硬边界关键字仍可定位；`git diff --check` 无输出且退出码为 0。M1 完成不代表新机制已生效。

### M2：Skill 与模板对齐

**精确文件：**

- `.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md`
- `docs/prompt/subagent-dispatch-template.md`
- `docs/prompt/README.md`
- `docs/README.md`

**修改口径：**

- owner flow 先执行授权门与五因素评估，再选择路由。
- owner flow 明确默认直接执行，派工必须证明净收益。
- 派工模板仅在实际派工时使用，保留所有身份与验收字段。
- 单派明确无需模型分配表，多派必须先列模型分配表。
- 两个 README 更新实时入口、适用范围和历史文档说明。

**静态命令：**

```powershell
rg -n "授权门|风险|耦合|并行收益|上下文传递成本|独立证据|直接执行|单.*派|多.*派" .agents/skills/cgc-pms-mainline-owner-flow/SKILL.md docs/prompt/subagent-dispatch-template.md docs/prompt/README.md docs/README.md
rg -n "model=|thinking=|reason=|验收输出=|模型分配表" docs/prompt/subagent-dispatch-template.md
git diff --check -- .agents/skills/cgc-pms-mainline-owner-flow/SKILL.md docs/prompt/subagent-dispatch-template.md docs/prompt/README.md docs/README.md
```

**预期结果：** Skill 与模板对三种路由及派工字段的触发条件一致；导航可找到实时规则；`git diff --check` 通过。M2 完成不代表新机制已生效。

### M3：AutoPilot 动态职责改造

**精确文件：**

- `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`
- `plugins/cgc-pms-autopilot/references/owner-boundary.md`
- `plugins/cgc-pms-autopilot/references/install.md`

**修改口径：**

- 将 A–F 明确为职责检查表，按每轮实际职责动态裁剪、合并或隔离。
- 保留连续迭代、补货、解阻、checkpoint、health gate、失败分类、最终复验、归档和停止条件。
- 明确高风险领域与正式裁决需要客观证据，证据不足必须明示。
- 明确 D/E 必要证据不可省略，但不自动映射为独立线程。
- 安装说明提供规则生效与静态一致性核对入口。

**静态命令：**

```powershell
rg -n "职责检查表|A.?F|动态|直接执行|单.*派|多.*派|客观证据|证据不足" plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md plugins/cgc-pms-autopilot/references/owner-boundary.md plugins/cgc-pms-autopilot/references/install.md
rg -n "Ready|checkpoint|stop.flag|pause.flag|enabled.flag|autoPush|ALLOW_TEST_DATA_RESET|失败分类|最多.*3" plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md plugins/cgc-pms-autopilot/references/owner-boundary.md
git diff --check -- plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md plugins/cgc-pms-autopilot/references/owner-boundary.md plugins/cgc-pms-autopilot/references/install.md
```

**预期结果：** A–F 不再被描述为固定六线程；AutoPilot 硬边界完整保留；三个文件无互相矛盾表述；`git diff --check` 通过。只有 M1–M3 九个实时文件全部一致并作为一个原子变更集落地后，才可宣告新机制生效。

### M4：静态一致性与行为场景验收

**精确范围：** M1–M3 的九个实时文件及本计划书；历史计划、质量、迭代和插件产物只确认未被修改，不做内容重写。

**验收命令：**

```powershell
rg -n "所有.*必须.*子智能体|固定.*六|每轮.*六.*子智能体|未派工.*model|未派工.*thinking" AGENTS.override.md AGENTS.md .agents/skills/cgc-pms-mainline-owner-flow/SKILL.md docs/prompt/subagent-dispatch-template.md docs/prompt/README.md docs/README.md plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md plugins/cgc-pms-autopilot/references/owner-boundary.md plugins/cgc-pms-autopilot/references/install.md
rg -n "Ready|checkpoint|stop.flag|pause.flag|enabled.flag|autoPush|ALLOW_TEST_DATA_RESET|失败分类|客观证据|证据不足" AGENTS.override.md AGENTS.md .agents/skills/cgc-pms-mainline-owner-flow/SKILL.md plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md plugins/cgc-pms-autopilot/references/owner-boundary.md
git diff --check
git status --short
```

**行为场景：**

| 场景 | 预期路由/结论 |
| --- | --- |
| 用户只要求分析或评审 | 主线程只读完成，不派工，不产生状态变更 |
| 用户授权单文件低风险修改 | 完成状态变更前检查后，主线程默认直接实施并做最小充分验证 |
| 用户授权固定命令或环境检查 | 主线程可直接执行；若命令会改变状态，先完成 branch/status 检查与适用 checkpoint |
| 用户授权跨模块普通实现 | 主线程按净收益自主判断直做或单派；跨模块本身不是强制派工条件 |
| 两个真正独立且无共享写集的任务 | 可多派并行，但不是强制；并行收益不足时主线程串行直做 |
| 权限、数据库或金额任务 | 不强制派工，但强制充分客观证据和必要复核；证据不足判“不通过”或“需要确认” |
| 子智能体超时或悬挂 | 只读核验后中断，可按剩余任务风险收回直接完成或重新派工，不继续硬等 |
| 用户未授权执行动作 | 授权门不通过，停止执行并说明需要的授权，不以派工绕过授权 |

**原子生效与新会话验证：**

1. M1–M3 可按顺序编辑和复核，但必须处于同一个变更集；九个实时文件全部一致前，不得宣告或依赖新机制。
2. 九文件静态与行为场景验收均通过后，才允许按已授权的 Git 流程原子提交；未获提交授权时保持为待验收变更集，不自行提交。
3. 原子变更落地后新开一次会话，读取根规则和 owner Skill，分别以“只读分析”“单文件低风险修改（只做路由判断，不实际改文件）”“权限任务证据要求”三个 smoke 场景验证规则重新加载；若仍出现旧规则中的机械派工结论，判定规则未生效。

**预期结果：** 第一条冲突扫描无有效命中，若命中历史引用则人工确认其已明确标注为历史或反例；第二条硬边界扫描覆盖各实时规则；八个行为场景全部得到预期路由；`git diff --check` 无输出；`git status --short` 仅包含本主线授权修改，且不包含历史 plans、quality、iterations、plugin artifacts、memory、私有目录或其他临时产物变更；新会话 smoke test 证明实际加载新规则。

## 8. 验收矩阵

| 验收项 | 通过标准 | 证据 |
| --- | --- | --- |
| 授权门 | 所有执行路由前均先判断授权与项目硬边界 | 根规则与 owner Skill 的对应条款、`rg` 命中 |
| Ready 适用边界 | 普通交互不强制 Ready；用户指定治理按其要求；AutoPilot 严格要求合格 Ready | 根规则、AutoPilot Skill 与行为场景 |
| 状态变更前检查 | 任何代码、配置、文档、Git 或运行环境状态变更前均核对 branch/status，疑似冲突再查 worktree | 根规则、owner Skill 与行为场景 |
| 路由完整性 | 明确定义直接执行、单派、多派，且五项因素可定位 | 九个实时文件的静态扫描结果 |
| 默认路由 | 授权门通过后默认主线程直做；派工须有高于成本的明确净收益 | 根规则、owner Skill 与行为场景 |
| 派工元数据 | 未派工不要求 `model/thinking/reason`；实际派工必填 | 根规则、Skill、模板一致表述 |
| 模型分配表 | 仅同时派工两个及以上时必需 | 根规则与模板一致表述 |
| 超时处置 | 可基于重新评估选择收回直做、补上下文或重派 | 根规则与 owner Skill 条款 |
| A–F 动态化 | A–F 被定义为职责检查表，不是固定六子智能体；D/E 必要证据不可省略但不强制独立线程 | AutoPilot Skill、owner boundary 与行为场景 |
| 安全边界 | AutoPilot Ready、checkpoint、flags、禁止自动 push、数据重置、并行上限、失败分类均保留 | 关键字扫描和人工复核 |
| push 授权边界 | `autoPush=false/no push` 禁止自动推送；仅用户明确授权且其他门禁通过后可提交或 push | 根规则、安装说明与行为场景 |
| 高风险证据 | 数据库、权限、安全、金额、租户、上线裁决均要求客观证据；不足时明示 | 根规则、AutoPilot Skill 和 owner boundary 条款 |
| 原子生效 | M1–M3 九个实时文件在同一变更集中全部一致，局部完成不宣告生效 | `git diff --name-only`、完整矩阵复核 |
| 新会话加载 | 原子变更落地后，新会话 smoke test 得到默认直做和证据优先结论 | 新会话三场景记录 |
| 历史边界 | 既有 plans/quality/iterations/plugin artifacts 未被追溯修改，指定历史文档保持原貌 | `git status --short` 与 `git diff --name-only` |
| 文档质量 | 无空白错误、冲突标记或格式问题 | `git diff --check` 退出码 0 |

## 9. 风险与回滚

### 9.1 主要风险

- **直接执行被扩大解释：** 低风险判断若缺少边界，可能绕过必要的独立复核。缓解方式是保留五因素评估和高风险强证据要求。
- **Ready 被错误全局化：** 若继续把普通交互任务也强制写入 Ready，取消机械派工仍无法提速。缓解方式是明确普通交互、用户指定治理与 AutoPilot 三类边界并做行为验收。
- **状态变更绕过前置检查：** 直接执行若只在派工前检查工作区，可能落到错误分支或脏工作区。缓解方式是把 branch/status 检查提升为任何状态变更前的通用门禁。
- **规则源漂移或混合状态：** 根规则、Skill、模板和插件引用文档可能只改一部分，产生互相冲突。缓解方式是九文件原子生效；M1–M3 局部完成时不得宣告新机制生效。
- **A–F 过度合并：** 动态职责可能被误解为可以省略测试、审查或归档。缓解方式是明确“职责可合并、责任不可遗漏”，高风险与正式裁决继续要求独立证据。
- **超时收回误用：** 为节省等待时间直接接管高风险任务。缓解方式是超时后重新评估风险并补足证据；是否重派由净收益决定，不允许降低裁决标准。
- **push 边界误读：** 将 no push 理解为永久禁止会阻断用户明确授权的正常 Git 收口，将其理解为可自动推送又会扩大权限。缓解方式是明确其只禁止自动推送，显式授权和其他门禁缺一不可。
- **旧会话缓存：** 九文件已修改但现有会话仍沿用旧规则。缓解方式是原子变更落地后执行新会话 smoke test，未加载新规则则判不通过。
- **历史文档混淆：** 历史方案与实时规则并存可能造成阅读误判。缓解方式是在 README 标注实时入口，不追溯篡改历史交付物。

### 9.2 回滚方案

本主线只修改规则和文档，不涉及数据库、服务或生产运行态。若实施后发现执行边界不清或规则冲突，必须整体回退九个实时文件的原子变更集，不得只回退 M1、M2 或 M3 的一部分而制造混合状态；回滚前后均执行 `git diff --check`、九文件静态扫描和新会话 smoke test。不得通过删除历史计划、质量报告或迭代报告完成回滚。

## 10. DoD 与过渡期实施建议

### 10.1 Definition of Done

- M1–M3 九个实时文件均按矩阵完成最小必要修改。
- M1–M3 九个实时文件处于同一个原子变更集，未全部一致前未宣告新机制生效。
- 授权门、五项路由因素和三种执行路由在根规则与 owner Skill 中一致。
- 授权门通过后默认主线程直接完成；不存在按任务类别强制使用子智能体的条款。
- 普通交互、用户指定治理和 AutoPilot 的 Ready 适用边界明确且行为验收通过。
- 任何代码、配置、文档、Git 或运行环境状态变更前的 branch/status 检查已覆盖主线程与子智能体；疑似冲突时补查 worktree。
- 只有实际派工才要求 `model/thinking/reason`，两个及以上实际派工才要求模型分配表。
- A–F 在 AutoPilot 中明确为职责检查表，D/E 的必要证据没有被省略，也没有被机械映射为独立线程。
- AutoPilot Ready、checkpoint、stop/pause/enabled、禁止自动 push、测试数据重置、最多三个完全无关联任务并行和失败分类完整保留。
- `autoPush=false/no push` 与用户显式提交或 push 授权边界清晰，未授权时不得执行。
- 数据库、权限、安全、金额、租户和上线裁决的客观证据要求完整；证据不足时必须明示。
- `docs/12-codex-subagents-implementation-plan.md` 和其他既有 plans/quality/iterations/plugin artifacts 未被追溯修改。
- M4 静态检查与八个行为场景全部通过，`git diff --check` 无输出，无未授权文件变更。
- 原子变更落地后已通过新会话 smoke test，确认实际加载的是新规则。
- 本主线不自动提交或 push；仅在用户明确授权且其他安全边界通过后执行相应 Git 动作。

### 10.2 过渡期实施建议

本节只服务于旧规则向新规则迁移的一次性实施，不构成新机制生效后的默认派工模板：

1. 过渡期仍按当前有效规则取得执行授权并满足现行角色边界；可由一个治理执行者串行完成 M1–M3 的九文件修改，以保证同一上下文和同一原子变更集。
2. 禁止机械地为 M1、M2、M3、M4 各启动一个线程。阶段可以顺序编辑和复核，但变更集必须整体一致，M4 必须面向完整九文件执行。
3. 若当前规则要求通过子智能体实施，只需派一个能够覆盖整套九文件原子修改的治理执行者；是否另设验收者只取决于正式裁决所需证据，不因阶段编号自动增加线程。
4. M4 的静态检查和行为场景必须留下充分客观证据；若实施者自验不足以支撑正式裁决，再按净收益补充独立复核。
5. 九文件原子变更落地后，以新会话 smoke test 作为生效门；通过后后续任务一律按“默认主线程直接完成、派工须有明确净收益”的新机制判断。

最终裁决格式必须明确：`通过/不通过`、`阻塞/非阻塞`、依据、剩余风险。任何剩余风险若影响后续执行，应进入适当的 Ready、blocked 或 current-focus 载体，而不是只停留在口头结论中。
