# 第47条主线：Codex 执行效率与沟通降噪试运行报告

报告状态：M0–M2 已通过 / M3 试运行中
统计时区：Asia/Shanghai
计划来源：`docs/plans/第47条主线-Codex主线执行效率与沟通降噪优化任务计划书.md`

## 1. 裁决范围

本报告只保存能够支撑第47条主线阶段与最终裁决的汇总证据，不保存原始会话日志、run id、截图、临时路径或逐命令输出。目标是验证统一执行协议是否降低无效调用、重复初始化、过密播报与中断返工，同时保持授权、验证、Git 和正式裁决门禁。

## 2. M0 当前事实

- M0 取证时仓库只有 `master/origin/master`，动态配置曾声明不存在的 `develop/1.5`；本主线已获授权在功能分支把 `baseBranch` 对齐为 `master`。
- 统一策略文档在 M0 前不存在；规则分散在 `AGENTS.override.md`、`AGENTS.md`、三个项目 Skill 和 AutoPilot reference 中。
- AutoPilot 已有可复用能力：模型调用/Context/验证投影指标、不可变 Context Base + Delta、Evidence v2 复用、durable checkpoint、失败分类、控制面指纹和单 Issue 金丝雀。
- 普通交互任务的工具调用分类、commentary 数和恢复胶囊没有仓库内统一的可复现记录机制。

## 3. 现状与差距矩阵

| 能力 | M0 状态 | 本主线处置 |
| --- | --- | --- |
| 授权、数据、发布、零悬空边界 | 已存在 | 保留在 AGENTS，不降低 |
| 通用状态机、粘性模式、短指令语义 | 分散/缺失 | 新增统一策略权威正文，AGENTS 与 Skill 只保留索引 |
| 非 PowerShell/跨层/PowerShell/知识图谱路由 | AGENTS 中已有但过长 | 迁入统一策略；高优先级禁止与交叉核验边界保留索引 |
| 工具调用预算与原样重试限制 | 部分存在 | 统一为首选路径、一个有增益备用路径和必需证据例外 |
| 浏览器初始化与证据模板 | 部分存在 | 由统一策略定义通用模板，运行态 Skill 保留 cgc-pms 专项步骤 |
| CI 分类、一次复验、退避轮询 | 分类存在，退避/播报约束不足 | 补强 CI Skill 并增加静态契约测试 |
| L1–L4 验证与证据复用 | AutoPilot 已有底层能力，普通任务缺统一语义 | 统一策略定义；AutoPilot 继续复用 Context/Evidence，不建第二套系统 |
| Git 提交到清理生命周期 | 分散 | 统一策略定义独立状态变化和授权边界 |
| 事件驱动 commentary | 仅有“不要无信息量”原则 | 定义触发事件、通常上限和最终答复自包含要求 |
| 普通任务恢复胶囊 | 缺失 | 统一策略新增轻量模板；AutoPilot 仍以 durable checkpoint 为准 |
| 控制面指纹覆盖 | 已存在 | 把新策略与实际影响执行的项目 Skill 纳入 fingerprintPaths |

## 4. 历史基线可用性

M0 对仓库内 `.codex-autopilot/events.ndjson` 做只读汇总，获得以下可复现范围：

| 项目 | 汇总值 |
| --- | ---: |
| 事件文件 | 1 |
| 事件数 | 239 |
| 时间窗口 | 2026-07-12 10:37 至 2026-07-14 16:08（+08:00） |
| 可识别 run | 61 |
| 可识别 Issue | 19 |
| 去重模型调用 | 4 |
| Context Base / Delta | 1 / 2 |

该事件样本早于当前部分效率观测能力，且不覆盖普通任务 commentary、通用工具调用与全部验证复用，因此只作为“历史数据覆盖不足”的客观基线，不用于证明第47条主线目标已达成。计划书中的人工抽样数字同样不进入最终通过/不通过计算。

## 5. 前瞻试运行口径

试运行从首个满足目标、授权、开始/结束和裁决均可识别的样本开始，连续 7 个自然日。按计划书 2.3 统计：

- `tool_config + tool_invocation` 占全部工具调用的比例；环境、召回不足和真实质量失败分别报告。
- 同一工具、参数、目标和前置状态均无变化的原样重复次数。
- 普通任务 commentary 数；长等待、事故、发布和高风险验收单独分组。
- 浏览器样本的调用总数、参数错误、入口、关键动作和裁决。
- 中断恢复是否复用有效 diff、胶囊、checkpoint 和验证证据。
- 新增/关闭正式后续项与净变化。

自完成性审计起，每个新增样本必须记录统一策略中的最小样本胶囊。此前样本虽有失败分类计数，但缺少可可靠复核的工具调用总数与控制面/有效执行耗时分母，因此只用于定性分析，不进入工具失败率和控制面耗时占比的最终达标计算；不得用估算值回填。

7 日内缺少普通修复、跨层分析、浏览器验收、CI 排障、连续迭代或发布收口任一样本时，本报告只能裁决“部分完成/需要确认”，不得用模拟数据补齐。

## 6. M0 裁决

正式交付物=现状与差距矩阵、历史基线可用性结论、前瞻试运行口径
验收证据=当前分支/配置事实、现行规则与 Skill、AutoPilot 指标/Context/Evidence/指纹测试
临时产物=无
git 状态=功能分支实施中，未提交、未推送
结论=通过，允许进入 M1
阻塞=无
剩余风险=完整指标只能由 7 日真实样本证明；控制面行为变化后必须完成单 Issue 金丝雀
新增后续项=0
关闭后续项=0
后续项净变化=0

## 7. M1 实施与验收

### 7.1 正式交付物

- `docs/standards/codex-task-execution-policy.md`：统一状态机、模式、短指令、工具路由、失败分类、浏览器、L1–L4 验证、CI、Git、commentary 与恢复胶囊。
- `AGENTS.override.md`、`AGENTS.md`：保留授权、安全、数据、生产、零悬空和 AutoPilot 硬门禁，把通用流程收敛为策略索引。
- 主线、CI、运行态三个项目 Skill：引用统一策略，只保留专项入口、步骤和回报骨架。
- `scripts/codex-autopilot/test-codex-task-execution-policy.ps1`：验证策略结构、路由场景、失败分类、恢复胶囊、专项 Skill 和指纹覆盖。

### 7.2 验收结果

| 验证 | 结果 |
| --- | --- |
| 通用执行策略契约测试 | 通过 |
| 主线计划 Standard profile | 通过 |
| 工具路由自测 | 通过 |
| 控制面指纹自测 | 通过 |
| 效率观测、Context Delta、Evidence 复用自测 | 通过 |

M1 裁决=通过。规则没有复制模型、评分权重、超时或回顾阈值等动态事实；AutoPilot 继续复用既有 Context/Evidence/checkpoint，不存在第二套编排系统。

## 8. M2 实施与验收

### 8.1 浏览器模板

- HTTP health：后端 `200`，前端 `200`，dev-login `302` 到 `/dashboard`。
- 内置浏览器：最终 URL 为 `/dashboard`，页面标题为“建筑工程总包项目管理系统”，主区域可见，页面标题“驾驶舱”可识别。
- 浏览器 warning/error：0。
- 本轮只初始化一次浏览器能力，没有因参数错误重复初始化。

### 8.2 Git 生命周期

临时 Git 仓库自测验证：

- 功能分支推送后远端 `master` 提交保持不变。
- 远端功能分支与本地功能分支提交一致。
- 未合并分支不会出现在基于 `master` 的清理候选中。
- 活动 worktree 占用分支能够被确定性识别。

### 8.3 失败分类样本

| 失败 | 分类 | 修正与结果 |
| --- | --- | --- |
| health 汇总命令的 PowerShell 管道语法错误 | `tool_invocation` | 改为先收集结果再序列化；一次通过 |
| Git 生命周期测试字符串变量边界错误 | `tool_invocation` | 使用显式变量边界；一次通过 |
| Markdown 行尾空白修复补丁与当前文本不一致 | `tool_invocation` | 先读取精确行内容，再做一次最小补丁；通过 |
| 桌面宿主测试入口路径假设错误 | `tool_invocation` | 用 `rg --files` 定位真实入口，只补跑该测试组；通过 |

上述失败均在参数/脚本纠正后复验，没有在相同前置与相同参数下原样重试，也没有误判为业务代码失败。

### 8.4 最终门禁

| 验证 | 结果 |
| --- | --- |
| Git 生命周期临时仓库自测 | 通过 |
| 桌面执行宿主约束 | 通过 |
| 禁止嵌套 Codex | 通过 |
| 桌面金丝雀登记契约 | 通过 |
| 配置 JSON 与 `baseBranch=master` | 通过 |
| `git diff --check` 与新增文件行尾空白检查 | 通过 |

M2 裁决=通过。控制面行为变更判定=已触发；依据是 AGENTS、统一策略、三个项目 Skill、桌面执行策略和 fingerprintPaths 均发生行为性变化。

## 9. M3 前瞻试运行

试运行窗口：2026-07-15 至 2026-07-21（Asia/Shanghai）。当前覆盖：

| 样本类型 | 状态 | 证据摘要 |
| --- | --- | --- |
| 普通修复 | 已覆盖 | 2026-07-16 先后补齐无持久化样本计算器与单一策略测试套件；两个低风险治理修复均具备目标、授权、实现、验证与收口证据，commentary 分别为 3 次和 1 次 |
| 跨层分析 | 已覆盖 | 2026-07-16 完成 Playwright → Vite `/api` 代理 → 后端 dev-login → profile/config/redirect 安全边界分析 |
| 浏览器验收 | 已覆盖 | health、dev-login、dashboard 落点及控制台结果 |
| CI 排障 | 已覆盖 | 已用失败 step 的 Range 尾段日志取得远端测试摘要，并用本地显式顺序回归复现；根因为 `StlSettlementServiceTest` 污染共享 V105 数据，分类为 `quality_or_security`；本地隔离修复及目标、顺序、全量验证均已通过，远端修复提交验证待授权 |
| 连续迭代 | 待授权 | 新控制面指纹必须先完成用户启动的 `启动迭代-1` 金丝雀 |
| 发布收口 | 待覆盖 | 临时 Git 自测不替代真实授权发布样本 |

### 9.1 2026-07-16 样本证据

- 跨层分析：`codebase-memory-mcp` 定位 frontend/backend/config/test 关联，`rg` 与当前源码交叉核验；Playwright 的 `/api/auth/dev-login` 经 Vite 代理到后端，入口仅在 `dev/local` 且显式启用时暴露，生产配置关闭，redirect 拒绝站外地址与路径遍历。
- 目标验证：`AuthControllerTest#testDevLoginRedirectToSiteDailyLogKeepsSecurityBoundary+testDevLoginControllerProfileScope` 共 2 项，失败 0、错误 0、跳过 0，`BUILD SUCCESS`。
- CI 分诊：远端 `master`、本地主线 HEAD 与本地 `master/origin/master` 均为 `ed47aabb39198f343742009d52c68080530b79a9`；[CI run #186](https://github.com/kismet84/cgc-pms/actions/runs/29428204584) 的 `backend-test / Run backend tests with coverage` 失败，`supply-chain-security` 跳过，其余 required jobs 成功；分支保护保持 `strict=true`、`enforce_admins=true` 与 11 个 required checks。
- CI 修复前本地等价复验：在同一提交和 workflow 使用的同一 `TEST_JWT_SECRET` 下执行 `backend\\mvnw.cmd -C verify`，1814 项测试失败 0、错误 0、跳过 1，jar 构建及 JaCoCo report/check 均成功。该结果只证明本地默认顺序未触发污染，不能覆盖远端顺序依赖。
- CI 稳定取证路径：整份 job 日志经 Windows Schannel 下载约 6.9 MB 后因 `missing close_notify` 中止；随后从已登录 job 页面定位失败 step 的临时日志入口，确认支持字节区间后强制 IPv4，仅取 15,823,994 字节日志的末段 `256 KB`，成功返回最终 Maven 摘要。Git 仓库 SSH 只影响 clone/fetch/pull/push，不是 Actions 日志通道，因此未改写 `known_hosts` 或 `origin`。
- CI 根因裁决：远端唯一失败为 `DashboardMaterialRoleServiceTest.testDefaultDemoProject_DashboardRealisticDemoDistribution` 第 349 行，Maven 汇总 1814 项、失败 1、错误 0、跳过 3；断言要求 V105 子清单状态至少三种。`StlSettlementServiceTest#setUp` 会把相同合同下全部 `sub_measure` 改为 `CONFIRMED` 且不恢复。目标 Dashboard 测试单独运行通过；显式按 Settlement 后接 Dashboard 的顺序运行后稳定复现同一失败，26 项中失败 1。分类从 `unknown` 更新为 `quality_or_security`（测试隔离/顺序依赖）。
- CI 隔离修复：2026-07-16 获用户授权后，`StlSettlementServiceTest` 删除对共享 `sub_measure.status` 的广域改写，只临时设置结算前置校验所需的 `approval_status`，并在每个测试结束后按原值恢复；没有采用无法覆盖并发子线程提交的类级测试事务。目标类 13 项通过；显式按 Settlement 后接 Dashboard 的失败顺序运行 26 项通过；`backend\\mvnw.cmd -C verify` 为 1814 项、失败 0、错误 0、跳过 1，退出码 0；全仓 `git diff --check` 通过。
- CI 承接边界：根因与本地修复证据均更新到 [blocked-issues.md](../backlog/blocked-issues.md) 的唯一 `ISSUE-037-021`，没有创建重复 Issue。本地实现和回归已关闭；用户明确要求暂不提交、不推送，因此远端修复提交的 11 项 required checks 与 `supply-chain-security` 仍待后续授权验证，旧 run 不得替代新提交裁决。

当前指标摘要：

- 已记录 `tool_invocation`：21 次；在既有 17 次之外，计时治理修复首次补丁因计划书上下文不精确而拒绝应用；恢复审计因错误假设评分配置嵌套结构而读取失败；本次未跟踪文件检查再次使用已知无效的 PowerShell `$file:` 变量边界，随后又因 Git 默认 quoted path 跳过中文计划书。四次均在纠正前提后只复验一次，但历史已知错误复发和首次漏检都不能按后续成功抹除。
- 已记录 `tool_config`：6 次；在既有 4 次之外，普通治理修复首次以错误项目名调用 `codebase-memory-mcp`，本次最高级规则完成性审计又重复使用同一已拒绝值；两次均在工具返回唯一可用项目后修正参数并命中，没有回退 CodeGraph 或伪造图谱证据。复发证明仅靠会话记忆不足，现已补充运行态 project 标识不可猜测、不可再次使用已拒绝值的统一协议和契约测试。
- 已记录 `environment_prerequisite`：11 次；在既有 9 次之外，本样本新增 job 页面导航超时但实际已加载、Node TLS 首次握手需强制 IPv4 两项。此前日志/artifact 端点的 EOF、Schannel 与 TLS 异常没有被误判为业务代码失败。
- 已记录 `quality_or_security`：1 次；样本计算器首轮目标测试确认 `Nullable[double]` 已绑定参数不应访问 `.Value`，修正为显式数值转换后目标、策略和计划测试通过。
- 同一已知无效查询/命令路径复发：3。分别为 Windows `rg` 路径通配符错误、再次使用已被 `codebase-memory-mcp` 拒绝的 project 名，以及再次使用 PowerShell `$file:` 变量边界；三者均说明执行未遵守已有事实。纠正方式分别为明确根目录配合 `-g`、复用已验证 project 标识、使用 `${file}:` 显式变量边界，当前效率指标仍不得判定达标。
- 指标可计算性：现有样本缺少可靠的工具调用总数和控制面/有效执行耗时分母，工具调用失败率与控制面耗时占比当前均为 `not_available`。最小样本胶囊已写入统一策略及 Task 7，后续样本必须先具备分母才可进入比例裁决。
- 备用路径预算超限样本：1（CI 证据取回）；最终收敛为“已登录 job 页面定位失败 step + Range 末段 `256 KB`”，并将该通用恢复规则写入 CI Skill，不保留临时 URL。
- 浏览器工具调用错误：本样本 6 次，一周累计 6 次；包含 3 次调用时序/定位错误、2 次能力限制和 1 次导航超时。该样本超过单样本 `<= 1` 的目标，当前不能判定浏览器效率指标通过；稳定路径形成后没有再次重复初始化，主动重复初始化为 0，底层内核重置后发生 1 次被动重连。
- 中断后重复实施：0；本主线按 M0–M3 阶段胶囊继续，未重新执行已通过的 M1 测试之外的无意义全量验证。
- 新增正式后续项：1（复发更新 `ISSUE-037-021`）；关闭后续项：0；净变化：+1。

### 9.2 完成性审计样本胶囊

```text
样本类型=验收/治理审计（不替代 Task 7 规定的六类样本）
统计起止=2026-07-16 本次连续完成性审计
工具调用总数=16
tool_config/tool_invocation/retrieval_gap/environment_prerequisite/quality_or_security=0/1/0/0/0
原样重复调用=1
浏览器调用总数/调用错误=0/0
commentary 数=2
控制面耗时/有效执行耗时=not_available/not_available
中断后重复实施=0
证据复用=复用未受当前文本变更影响的 M1/M2 证据；只重跑策略契约、带计划路径的 Standard profile 和控制面指纹
新增后续项/关闭后续项/净变化=0/0/0
样本结论=不通过效率指标；工具失败率 6.25%，且原样重复 1 次。命令已改为明确根目录配合 -g 并成功，未形成悬空项
```

### 9.3 普通治理修复样本胶囊

```text
样本类型=普通修复（低风险工程治理）
统计起止=2026-07-16 样本计算器实现与收口
工具调用总数=21
tool_config/tool_invocation/retrieval_gap/environment_prerequisite/quality_or_security=1/0/0/0/1
原样重复调用=0
浏览器调用总数/调用错误=0/0
commentary 数=3
控制面耗时/有效执行耗时=not_available/not_available
中断后重复实施=0
证据复用=复用现有 AutoPilot 指标与测试模式；实现变化后只重跑目标计算器、统一策略契约、带计划路径 Standard profile 和差异门禁
新增后续项/关闭后续项/净变化=0/0/0
样本结论=部分通过；commentary、原样重复和零悬空满足要求，工具失败率由统一计算器算得 4.76%，高于 3% 目标，控制面耗时占比因缺少可靠分母保持 not_available
```

计算器验收覆盖：16 次调用/1 次调用错误得到 `6.25%`；40 次调用/1 次工具配置错误得到 `2.5%`；12/80 秒得到 `15%`；分类总数超限、原样重试超限、浏览器错误超限、单边耗时和控制面耗时大于有效执行耗时均被拒绝。源码不含持久化写入原语，也不复制计划目标阈值。

### 9.4 计时策略套件修复样本胶囊

```text
样本类型=普通修复（低风险验证治理）
统计起止=2026-07-16T01:03:14.3876563+08:00 至 2026-07-16T01:04:39.6887640+08:00
工具调用总数=8
tool_config/tool_invocation/retrieval_gap/environment_prerequisite/quality_or_security=0/1/0/0/0
原样重复调用=0
浏览器调用总数/调用错误=0/0
commentary 数=1
控制面耗时/有效执行耗时=14.6154749/85.3011077 秒
中断后重复实施=0
证据复用=单一入口顺序复用样本计算、统一策略、Git 生命周期、工具路由、控制面指纹和主线计划结构六项底层测试
新增后续项/关闭后续项/净变化=0/0/0
样本结论=部分通过；控制面耗时占比 17.13%、commentary、原样重复和零悬空达到目标，工具失败率 12.5% 高于 3% 目标
```

时间边界按路由、实施、收口三段显式记录：路由 5.3702101 秒，实施 70.6856328 秒，收口 9.2452648 秒；控制面只取路由与收口，分子 14.6154749 秒，完整样本分母 85.3011077 秒。单一套件入口执行六项底层测试并通过，替代手工拼接多条命令，但不替代底层测试本身。

### 9.5 当前完成性审计

| 计划任务 | 当前证据 | 裁决 |
| --- | --- | --- |
| Task 1 统一执行策略 | 权威正文、AGENTS 索引、专项 Skill 引用与契约测试均存在；`AGENTS.override.md` 的 AutoPilot 章节已从 84 行专项细节压缩为 25 行最高级边界与唯一事实索引 | 已完成 |
| Task 2 确定性检索路由 | 静态路由测试、PowerShell 硬边界及 dev-login 跨层真实样本均有交叉核验；AutoPilot owner Skill 的 memory 故障降级已明确区分非 PowerShell 与 PowerShell | 已完成 |
| Task 3 浏览器模板 | health、dev-login、dashboard、控制台与一次初始化证据齐全 | 已完成 |
| Task 4 测试与 CI 分层 | L1–L4、失败分类和退避规则已验证；失败 step 尾段 Range 取证取得准确摘要；测试隔离修复后目标类、显式失败顺序与后端全量验证均通过 | 本地实施与 CI 根因闭环完成；修复提交的远端门禁验证待授权 |
| Task 5 Git 生命周期 | 功能分支、禁止直推 master、worktree/未合并清理自测通过 | 模板完成，真实发布样本待授权 |
| Task 6 沟通与恢复胶囊 | 跨轮恢复未重复实施；普通治理修复 3 次事件驱动 commentary，最终答复自包含 | 已完成 |
| Task 7 一周试运行 | 普通修复、浏览器、跨层分析与 CI 根因样本已覆盖；最小样本胶囊、无持久化比例计算器和控制面耗时样本已补齐 | 未完成；连续迭代、发布收口和完整时间窗仍缺失，浏览器单样本错误、原样重试与两个普通样本工具失败率未达标 |

未满足的 DoD 固定为四项：原样重复调用回到零、一周完整试运行（仍缺连续迭代与发布收口）、用户授权的单 Issue 金丝雀、最终裁决。普通任务 commentary 与控制面耗时样本已补齐；其余已勾选项均有当前文件或命令证据，不得用本矩阵把缺失项改判为完成。

M3 当前裁决=部分完成。事件驱动沟通、恢复胶囊、普通治理修复、控制面耗时、跨层分析和真实 CI 根因样本已落地；完整 7 日窗口、控制面金丝雀、连续迭代与发布收口样本仍未完成，浏览器单样本错误、原样重试与普通样本工具失败率当前未达标。CI 本地测试隔离根因已修复并完成目标、顺序与全量复验，但修复尚未提交、推送，远端 required checks 仍缺失，当前不得判定主线最终通过。

控制面行为变更判定=已触发
金丝雀状态=待本地提交授权及用户明确启动 `启动迭代-1`
提交/推送状态=按用户授权保持未提交、未推送

### 9.6 2026-07-16 恢复审计与批次复验

- 路由复核：当前阶段为验收/审计，范围跨规则、Skill、控制面配置与正式报告，但不涉及权限、安全或数据一致性变更；输出用于阶段通过/不通过裁决。主线程直接执行仍安全，不需要为固定命令和证据摘录引入派工成本。
- 控制面事实：`baseBranch=master`、`executionHost=desktop-native`、`autoPush=false`；`stop.flag` 与 `pause.flag` 不存在，`enabled.flag` 存在；state 为 `LIMIT_REACHED`，无活动 Issue。该状态不构成用户启动 `启动迭代-1` 的授权，本轮未改变 flag、未派发 Ready、未启动 AutoPilot。
- 当前验证：按计划书唯一批次入口执行 `test-codex-task-policy-suite.ps1`，样本计算、统一策略、Git 生命周期、工具路由、控制面指纹和主线计划结构共 6 项全部通过；全仓 `git diff --check` 与最终分支/工作区核验也已通过。
- 失败分类：一次读取配置时错误假设评分配置嵌套结构，归类为 `tool_invocation`；修正为已知配置键后一次通过，没有原参数重试。随后最高级规则完成性审计再次使用已被拒绝的 codebase-memory project 名，归类为 `tool_config`，使原样重复调用累计由 1 次增至 2 次；工具失败率仍不得改判为达标。
- 当前裁决：Task 1–6 的本地交付物和契约证据仍有效；Task 7 继续缺完整 7 日窗口、连续迭代样本和真实发布收口样本。控制面行为变化已触发，但单 Issue 金丝雀仍需本地提交授权及用户明确输入 `启动迭代-1`；CI 修复也仍需新提交的远端门禁证据。

```text
任务目标=在不降低授权、验证、Git 与正式裁决边界下完成第47条主线效率治理及7日试运行
当前模式=验收模式 / M3 试运行
授权范围=允许主线文件修改与本地验证；暂不提交、不推送、不启动 AutoPilot、不改变 flag
当前分支与工作区=codex/mainline-47-execution-efficiency；主线交付物与 CI 测试隔离修复均为未提交 diff
已完成变更=统一策略、最高级规则去重与唯一事实索引、三项专项 Skill、策略批次入口、效率计算器、控制面指纹覆盖、CI 测试隔离与正式载体更新
已通过验证=策略批次 6/6；CI 目标类 13/13、显式失败顺序 26/26、后端全量 1814 项 0 失败 0 错误 1 跳过
失败分类与阻塞=历史已知无效路径复发 3 次；工具失败率未达标；7日窗口、连续迭代、发布收口、单 Issue 金丝雀和修复提交远端 checks 缺失
剩余步骤=继续收集至2026-07-21的合格样本；另获提交/推送授权与启动迭代-1授权后完成发布样本、金丝雀及远端 CI 裁决
禁止重复执行=不重复下载旧 CI 整份日志；代码与验证前提未变化时不重复跑后端全量；不把 enabled.flag 解释为本轮启动授权
是否允许提交/推送/合并=否/否/否
正式承接的后续项=ISSUE-037-021 承接修复提交远端门禁；M3 缺项由本报告唯一承接
```

### 9.7 最高级规则去重与运行态标识复发修复

- 结构审计确认 `AGENTS.override.md` 仍复制 AutoPilot 的评分版本、权重、回顾阈值、阶段机、Ready 细节和触发实现，与“最高级规则只保留硬边界与入口索引”的架构目标冲突；对应事实已分别存在于 owner Skill、控制面策略、桌面执行策略和动态配置中。
- 本轮将 AutoPilot 章节由 84 行压缩为 25 行，只保留生产/数据/Git/授权/checkpoint/no-push/金丝雀等不可绕过边界，以及通用策略、owner Skill、控制面策略、桌面策略和配置/schema 的唯一入口；没有降低 Ready、验证、复核或正式收口门禁。
- 统一策略新增 `codebase-memory-mcp` project 标识运行态约束：复用已确认值，不得猜测或再次使用已拒绝值；契约测试同时禁止将评分版本、权重、回顾阈值和超时复制回 `AGENTS.override.md`。
- 目标契约 `test-codex-task-execution-policy.ps1` 已通过；完整六项策略批次 6/6 通过，全仓 `git diff --check` 通过，最终分支仍为 `codex/mainline-47-execution-efficiency`。
- 发现项处置：最高级规则重复和 project 标识复发均为当前目标直接相关问题，已在本轮修复并纳入自动契约；新增后续项 0、关闭后续项 0、后续项净变化 0。

### 9.8 专项路由与分类口径一致性审计

- 完成性审计发现 AutoPilot owner Skill 在 memory 工具不可用时笼统回退“CodeGraph 与 `rg`”，可能覆盖 PowerShell 禁止 CodeGraph 的最高级硬边界；现已改为非 PowerShell 才允许回退 CodeGraph，PowerShell 固定回退 `rg` 与直接读取。
- AutoPilot 结构化分类 Schema 为运行态兼容将 `powershell_parser_error` 编码为 `tool_config`，而统一策略、CI Skill 与第 47 条效率指标把参数/schema/转义错误统计为 `tool_invocation`。现已明确“只做报表归一化、不改写 AutoPilot 历史状态、不放宽 fail-close/重试”的兼容映射，避免同一事件在试运行报告中漂移。
- 同一 owner Skill 还复制了 Implementer 45/5/10 分钟、运行态等待 180 秒和每轮最多并行 3 等动态值，其中“并行 3”与当前配置上限 1 直接冲突；现已全部改为读取 `issueExecutor`、`runtimeRefresh.waitSeconds`、`maxParallel`、`maxParallelIssues` 与 `parallelSafetyMode`，配置缺失时串行 fail-safe。
- `AGENTS.md` 仍保留“直接从 current-issues.json 补货”的旧固定优先级，与 owner Skill 的“知识图谱健康/HEAD 游标门禁后按 sourceRefs 核实”冲突；现已改为只索引 owner Skill，并明确 `current-issues.json` 仅为正式写回源、图谱异常时不得回退文件扫描。`AGENTS.override.md` 的产品情报入口同步声明不推导 AutoPilot 发现顺序。
- `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md` 已加入控制面 `fingerprintPaths`，对应指纹测试与主线 Skill 契约新增防回归断言；本变化继续沿用“控制面已触发、单 Issue 金丝雀待授权”的既有裁决，不复用旧指纹证据。
- 当前指纹只读核验确认：工作区指纹与 state 保存值、上次金丝雀值均不一致；单 Issue 金丝雀门允许进入，N>1 仍返回需金丝雀。state 保持 `LIMIT_REACHED` 且无活动 Issue，本轮未改变任何 flag 或状态。
- 完整策略批次再次 6/6 通过；全仓已跟踪 diff 无空白错误，8 个未跟踪文本交付物也完成行尾空白检查。未跟踪文件检查先因历史已知的 `$file:` PowerShell 变量边界错误在解析期失败，再因 Git 默认 quoted path 漏掉中文计划书；分别改用 `${file}:` 和 `git -c core.quotepath=false` 后完成全量核验，两项均归类为 `tool_invocation`，且未触及仓库状态。
- 发现项处置：PowerShell 降级冲突、分类口径缺映射和 owner Skill 未纳入指纹三项均属当前目标直接缺口，已本轮修复并进入自动契约；新增后续项 0、关闭后续项 0、后续项净变化 0。
