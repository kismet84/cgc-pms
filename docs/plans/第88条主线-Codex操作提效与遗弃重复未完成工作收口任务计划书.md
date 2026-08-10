# 第88条主线：Codex 操作提效与遗弃、重复、未完成工作收口

**Goal:** 将两次只读审计中的可执行发现合并为一个无重复、可分阶段验收的实施载体：降低 Codex 日常上下文和推理成本，消除同名 Skill 与过密子智能体策略，补齐迁移和 CI 门禁，删除已证实的死代码与占位实现，并关闭治理资产漂移。 **Architecture:** 复用现有 Codex 配置、根/项目 `AGENTS.md`、项目运行态 Skill、Flyway、现有 CI jobs、现有前后端测试和 Codemap；先保护主线87脏工作区，再按“用户级配置 → 治理载体 → 数据与门禁 → 前端去冗余 → 通知与 PDF 测试 → 正式收口”推进。不新增 Skill、依赖、守护进程、状态库、CI job 或平行工具层，不改生产或目标环境。

> 编制日期：2026-08-10
> 计划状态：`IMPLEMENTED / G0-G5_PASSED / GIT_DELIVERY_MERGED / POST_MERGE_VERIFIED`
> 唯一载体：本计划；两项来源任务只作审计输入，不再另建重复 Backlog
> 来源：两次只读审计输入
> 当前代码基线：`master@28ca2aef30f876deb8120b858fea9d248ff08528`
> 交付结果：主线87与88由统一源 SHA `8167830fd4220a418debab9304e02d48678973a3` 经 PR #428 合并为 `master@cc72802134f2c536a22ad8c9c8c3c1ffb1edaea6`
> 并行工作树：`codex/mainline-82-83-remediation@c9e999b4`、`codex/recovery-side-ci-tier-20260809@fa7a549f`，均未取得清理裁决
> 开放 PR：`0`；这不等于现有 worktree 或脏改动可删除
> 环境：仅本地 dev/test/demo；生产、目标环境、生产数据库和非本地验收均不适用
> 授权：用户已授权实施、阻塞研判、全部项目改动的受保护 Git 交付与已合并源分支清理；不授权生产、目标环境、强推、保护绕过、Tag 或 Release

## 1. 实施裁决

| ID | 审计发现 | 本计划裁决 | 完成定义 |
| --- | --- | --- | --- |
| C1 | 全局默认 `ultra + priority`，普通任务成本过高 | 降为日常 `medium`，高风险任务按次升到 `high/xhigh`；`ultra/priority` 不再全局常驻 | 新会话读取到新默认；复杂任务仍可按次覆盖 |
| C2 | 全局 `danger-full-access + never` 扩散到所有项目 | 全局恢复当前 Codex 支持的安全默认；仅经确认的受信项目保留项目级显式权限 | 新项目不继承全权限；`cgc-pms` 权限边界有明确记录 |
| C3 | Ponytail 对非编码会话和子智能体默认注入 | 通过稳定用户环境设置关闭默认注入，保留显式 `/ponytail full` | 普通新会话无注入；显式启用仍生效 |
| C4 | 用户级与项目级 `cgc-pms-runtime-refresh` 同名漂移 | 保留项目版；备份后将用户级同名 Skill 移出发现路径，不编辑插件缓存 | 项目内只解析项目版；其他项目依赖已排除 |
| C5 | 子智能体默认策略和角色矩阵过重 | 默认 `0～1` 个；仅独立并行或高风险复核最多 `2` 个；删重复角色矩阵 | 单路任务不派工；双路任务不超过 2 个；安全复核例外保留 |
| C6 | `rg` 随运行时存在但不在稳定 PATH | 安装稳定发行版或建立稳定 shim，不绑定 Codex 版本哈希目录 | 新 PowerShell 中 `Get-Command rg` 成功 |
| R1 | H2 缺 V219、V220、V221、V263 等价语义 | 新增下一可用版本的幂等 reconciliation migration；旧 migration 只读 | fresh H2 与 MySQL 迁移结果语义一致 |
| R2 | Flyway 不可变脚本只 warning/exit 0，且 CI 场景没有 staged diff | 现有脚本 fail-close，并分别支持本地 staged diff 与 CI base-ref diff；接入现有 `sql-safety-scan` | 改旧 migration 必败；新增 migration 通过；不新增 job/context |
| R3 | 三个前端页面被判定为删除候选 | 删除真正不可达的 `BudgetPage.vue`、`ShellLoadingPage.vue`；将 `/session` 改为兼容重定向后再删 `SessionPage.vue` | 旧 URL 兼容、路由台账和单测通过；预算 API 保留 |
| R4 | `commercial.ts` 有两个同实现 query helper | 在文件内复用一个现有 helper，不抽跨服务工具层 | 行为与类型不变，重复函数删除 |
| R5 | EMAIL/SMS/WECHAT sender 永远返回未配置/未实现 | 先预览订阅与枚举使用；零历史记录时删除占位渠道并只保留 IN_APP；有记录时先取得数据迁移授权 | 不再静默 `SKIPPED`；不丢历史订阅，不接入虚假供应商 |
| R6 | PDF spike 测试包含正式测试未覆盖的关键断言 | 先迁移分页、外部资源拒绝、容量和时间断言，再删除 spike | 正式 renderer 测试覆盖全部有效契约 |
| R7 | 计划、Backlog、脚本和历史资产漂移 | 关闭/承接第66条；同步旧计划头部；删除零引用资产；保留 AutoPilot 完成账本和仍被引用的 archive | 每项有删除、保留或承接证据，无第二状态源 |
| R8 | 已合并分支残留与两个未合并 worktree 并存 | 已合并分支仅在另获 Git 清理授权后删除；两个活跃 worktree 保持保护 | 删除前证明已合并、无未推送提交、无 worktree 占用 |

## 2. 范围、非目标与不变量

### 2.1 范围

- 用户级 Codex：`C:\Users\summade87114\.codex\config.toml`、`C:\Users\summade87114\.codex\AGENTS.md`、用户环境变量/PATH、用户级同名运行态 Skill。
- 项目治理：根 `AGENTS.md`、`.codex/config.toml`、计划/Backlog 导航、零引用脚本和现有 Flyway 不可变检查。
- 后端：新的 H2 reconciliation migration、migration 测试、通知渠道契约及测试、PDF renderer 正式测试。
- 前端：三个候选页面、`router.ts` 与路由测试/台账、`services/commercial.ts` 及相关单测。
- 生成物：只有路由、依赖或主要数据流实际改变时，才同步 Codemap 三件套。

### 2.2 非目标

- 不新建 Skill、插件、代理角色、守护进程、AutoPilot 状态机、CI job、通知供应商集成或通用 query 工具层。
- 不重写现有 long-task-gate；其 Completion Contract、恢复、通知和测试继续复用。
- 不批量禁用插件；未提供使用频率和失败证据的插件裁剪按“价值不足”关闭。
- 不删除或扫描受保护目录；不删除仍被回滚 Compose、路由台账或字段盘点引用的 `archive/v1.6/frontend-admin-legacy`。
- 不改写 Git 历史以压缩 `.git`；历史日志摘要化在缺少明确保留策略和文件清单时不实施。
- 不修改任何已应用 Flyway migration；修复只能用新版本 migration。
- 不连接生产、不规划目标环境验证、不将非本地证据列为阻塞项。

### 2.3 不变量

- 当前主线87成果、55 条既有工作区状态和两个 worktree 先保护，后去冗余。
- `/budget` 与 `/session` 旧 URL 保持兼容；删除页面不等于删除其仍被复用的 API、store 或业务信息。
- 通知渠道收缩不得静默丢弃历史订阅；存在非 IN_APP 数据即停在预览和授权门。
- Flyway 不可变门禁必须检测 PR/分支相对基线，不得把“无 staged diff”误判为安全。
- 用户级配置变更与仓库代码变更分开备份、验证和回滚；不混为一个 Git diff。

## 3. G0～G5 门禁

| 门禁 | 进入条件与证据 | 当前状态 | 未通过动作 |
| --- | --- | --- | --- |
| G0 基线锁定 | branch/HEAD/status/open PR/worktree、主线87归属、用户配置备份与 Codemap 基线已核对；两个活跃 worktree 保持保护 | `PASS` | 禁止实施；仅保留本计划文件 |
| G1 契约完整 | C1～C6、R1～R8 文件边界、正负向验收、回滚和当前配置 schema 已锁定 | `PASS` | 不进入代码或配置修改 |
| G2 数据与迁移 | fresh H2/MySQL V292 通过；本地 demo 库非 IN_APP 订阅、渠道和发送历史只读统计均为 0 | `PASS` | 有非 IN_APP 记录且无授权时，只阻塞 R5，不阻塞其他独立阶段 |
| G3 实现闭环 | 用户级配置、治理、迁移/门禁、前端、通知和 PDF 均按最小方案完成；未新增框架、依赖或状态源 | `PASS` | 回到对应阶段，修根因 |
| G4 本地运行态与验证 | 新 Codex 会话、后端、前端、CI 契约、Codemap 和本地浏览器均通过 | `PASS` | 按统一失败分类处理，不以页面可达代替主线通过 |
| G5 正式收口 | 当前 diff、风险、回滚、计划/状态回写、独立终审和零悬空统计齐全；同 SHA Push CI、Pre-PR、独立 PR CI、合并与 post-merge 证据完整 | `PASS / GIT_MERGED / POST_MERGE_VERIFIED` | 任一远端证据缺失则保持未完成；不得声明已交付或已上线 |

## 4. 实施阶段

### M0：保护、去重与隔离

1. 重新采集当前 branch、HEAD、`git status --short`、worktree、开放 PR 和任务 owner；不复用本计划中的 55 条历史计数作为实施事实。
2. 主线87先完成收口，或由用户单独授权创建隔离 branch/worktree；未满足任一路径，不修改仓库文件。
3. 核对 `docs/codemap/codemap.lock`；在修改每个模块前，从 `codemap.json` 取得调用方、影响面和测试。地图陈旧才重新生成。
4. 对用户级 `config.toml`、`AGENTS.md`、同名 Skill 目录和相关用户环境变量做可恢复备份，并记录原值/哈希；备份不得写入仓库。
5. 将来源审计中的建议逐项映射到本计划；重复项不写入 Backlog。

验收：仓库实施位置与主线87无文件重叠；备份可读；两个活跃 worktree 未被移动或删除；无 Git 写操作。

### M1：Codex 日常操作减负

1. 用实施时当前官方 Codex 配置 schema 校验字段和值；只调整已证实的默认项：
   - `model_reasoning_effort` 改为 `medium`；复杂任务按次覆盖 `high/xhigh`。
   - 移除全局常驻 `priority`；仅用户明确要求或时效敏感任务按次启用。
   - 全局不再默认 `danger-full-access + never`；`cgc-pms` 若继续保留项目级受信配置，必须明确只对本项目生效。
   - 不改 `notify`、MCP servers、marketplace 源、桌面主题或其他无关设置。
2. 通过稳定用户环境设置 `PONYTAIL_DEFAULT_MODE=off`；不编辑会被插件升级覆盖的 cache/hook 文件。
3. 精简用户级 `AGENTS.md`：保留中文、授权、脏工作区、破坏性操作和高风险复核规则；删除重复沟通规范和动态角色矩阵；子智能体改为默认 `0～1`、明确独立或高风险最多 `2`。
4. 仅在项目根规则仍强制过密派工时，做一处对应文字调整；不复制用户级矩阵。
5. 确认无其他项目依赖用户级 `cgc-pms-runtime-refresh` 后，将其备份并移出 Skill 发现路径；保留项目版及主线87对其已有改动。
6. 安装稳定 ripgrep 或建立稳定 shim；PATH 不指向带 Codex 版本哈希的 runtime 目录。

验收：

- 普通新会话为 `medium` 且不默认 priority/Ponytail；显式 `/ponytail full` 和单任务升档可用。
- 一个单路任务派工 0 个；一个明确双路任务不超过 2 个；高风险独立复核规则仍生效。
- 项目内运行态任务只命中项目 Skill；`Get-Command rg` 与 `rg --version` 在新 PowerShell 成功。
- 用户级指令体积下降；不以新增 Skill 或新增规则抵消收益。

### M2：治理载体与零引用资产

1. 第66条计划做 owner 裁决：无当前价值则正式关闭；仍需空间证据则只在唯一状态源登记一个 owner、前置和验收，禁止双载体。
2. 同步第61～65、67～68条计划的旧状态头部，清除第71条内部残留 `OPEN / NOT_READY`；只校准历史状态，不重开已合并业务。
3. `docs/backlog/epics.md` 经当前引用扫描为零后删除；archive 副本保持不动。
4. `docs/backlog/done-issues.md` 保留并标注为 AutoPilot 完成账本；不改名，避免为命名引入写入方级联修改。
5. `scripts/quality-baseline.ps1` 经脚本、workflow、文档和任务入口零引用复核后删除。
6. 两个 `restart-docker` 支持入口先比较调用方和能力；只在完全等价且 owner 已确认时保留一个，否则记录保留理由并关闭合并建议。

验收：文档链接和唯一状态源检查通过；删除符号/脚本零引用；AutoPilot 写入契约不变；主线87文件若仍未收口则本阶段继续等待。

### M3：H2 迁移对称与 Flyway fail-close

1. 将 V219～V221 的菜单/角色授权语义和 V263 的退役成本科目清理语义写入“下一可用版本”的单个幂等 H2 reconciliation migration；版本号实施时按仓库现状解析，不预占编号。
2. 旧 `V219__*`、`V220__*`、`V221__*`、`V263__*` SQL 只作事实来源，不修改字节。
3. 扩展 `MigrationIntegrityTest` 或现有 fresh-H2 smoke，断言对象、权限关联与退役科目最终事实；MySQL 继续走现有 `FlywayMySqlSmokeTest`。
4. 修改 `scripts/check-flyway-immutability.bat/.sh`：
   - 本地模式检查 staged 已跟踪 migration；发现修改返回非零。
   - CI 模式接收明确 base SHA/ref，检查 `base...HEAD`；不得依赖 staged diff。
   - 新增 migration 允许通过，修改旧 migration 必须失败，未知/缺失基线 fail-close。
5. 将 CI 模式接入现有 `sql-safety-scan`，并扩展现有 workflow contract；不新增 job、required context 或依赖。

验收：fresh H2 与 MySQL smoke 通过；旧 migration 负向 fixture 失败、新 migration fixture 通过；workflow contract、Shell/PowerShell 入口和 `git diff --check` 通过。

### M4：前端死代码与局部重复

1. 删除不可达 `BudgetPage.vue` 及只服务该页面的过期测试；保留 `/budget -> /cost-budget`、`loadBudgetPage` 和预算 API，因为仍有合同、目标成本、应收和采购调用方。
2. 将 `/session` 从组件页改为兼容重定向，维持登录/角色后的目标页语义；同步 router 单测、默认 redirect 契约和路由台账后，删除 `SessionPage.vue`。
3. 删除零引用 `ShellLoadingPage.vue`。
4. 在 `services/commercial.ts` 内让 partner 查询复用现有 query helper，删除同实现 `withPartnerQuery`；不抽跨文件 util。
5. 路由边界变化后同步 Codemap 三件套；其他生成物无变化则不重写。

验收：前端 focused unit、router test、`type-check`、`build`、route-ledger 和 Codemap verify 通过；本地浏览器验证 `/budget`、`/session`、403/404/全局错误页入口，无控制台错误。

### M5：通知渠道与 PDF 测试收敛

1. 在已证明为本地 dev/test/demo 的数据源上只读统计通知订阅、规则配置和历史记录中的 EMAIL/SMS/WECHAT；记录数量、租户和恢复方式，不先改数据。
2. 若三类历史记录均为 0：
   - 删除三个占位 sender 和对应注册；创建/更新入口拒绝非 IN_APP 渠道。
   - 收缩 enum、Dispatcher 和测试中的占位分支，禁止继续返回伪成功或永久 `SKIPPED`。
3. 若任一历史记录大于 0：先停止本子阶段；补充映射/拒绝契约、备份与恢复测试，并取得单独数据写授权后，使用新版本 migration 处理，禁止静默改成 IN_APP。
4. 将 `OpenHtmlToPdfRendererSpikeTest` 的多页、外部资源拒绝、容量和时间断言迁入 `OpenHtmlToPdfDocumentRendererTest`；全部通过后删除 spike。

验收：通知 API、Dispatcher 和数据事实一致；不再暴露无实现渠道；renderer 正式测试覆盖原 spike 的有效安全/容量契约；后端 focused tests 与构建通过。

### M6：综合验证、Git 边界与收口

1. 最小综合验证：
   - 后端：`MigrationIntegrityTest`、fresh H2、`FlywayMySqlSmokeTest`、`AlertNotificationDispatcherTest`、`OpenHtmlToPdfDocumentRendererTest` 及受影响模块构建。
   - 前端：focused unit/router、`pnpm --dir frontend-admin-v2 type-check`、`pnpm --dir frontend-admin-v2 build`、route-ledger。
   - 治理：workflow contract、引用/链接、`node scripts/codemap/generate-codemap.mjs --verify`、`git diff --check`。
   - 运行态：按项目 `cgc-pms-runtime-refresh` 进行本地 URL、DOM、console 验真；每阶段最多一次完整 DOM，其余只取目标差异。
2. 独立复核当前 diff：没有主线87或其他 owner 文件被覆盖；删除文件均有零引用或兼容替代证据。
3. `codex/all-changes`、`codex/post-merge-policy-path-fix` 等已合并分支，只在另获 Git 清理授权后核验 merge-base、`git cherry`、未推送提交和 worktree 占用，再执行删除。
4. 两个未合并 worktree 继续由各自 owner 裁决；不得为本计划收口而强行删除。
5. 未获 Git 交付授权时，G5 只能记为 `LOCAL_PASSED / GIT_NOT_AUTHORIZED`，不得声明远端交付完成。

## 5. 文件边界

| 工作包 | 允许范围 | 明确禁止 |
| --- | --- | --- |
| 用户级配置 | 用户 `config.toml`、`AGENTS.md`、用户环境、用户级同名 Skill 的可恢复禁用 | Codex plugin cache、notify、MCP、marketplace、会话/rollout 删除 |
| 治理 | 本计划列出的计划头部、Backlog 导航、零引用脚本、现有 workflow contract | 新状态源、新 Skill、重写 long-task-gate、受保护目录 |
| 迁移/CI | 新 H2 migration、现有 migration tests、两个 immutability 脚本、现有 `sql-safety-scan` | 任何旧 migration、新 CI job、required context 改名 |
| 前端 | 三个候选页面、`router.ts`、相关测试/路由台账、`commercial.ts` | 删除预算 API/store、跨服务 query 框架、业务信息缩减 |
| 通知/PDF | notification sender/enum/dispatcher/测试、renderer 两个测试文件；必要时新版本数据 migration | 虚假渠道实现、静默数据改写、修改旧 migration |
| 生成物 | Codemap 三件套，仅在实际边界变化后同步 | 无行为变化时机械重写 |

## 6. 风险与恢复矩阵

| 风险/触发 | 预防 | 恢复 |
| --- | --- | --- |
| 覆盖主线87或他人脏改动 | G0 owner 复核；等待 clean 或另获隔离授权 | 只撤销本计划自有文件；禁止 reset/checkout 他人成果 |
| Codex 新会话无法启动或行为异常 | 变更前备份和 schema 校验；一次只改一个配置组 | 恢复用户级配置/AGENTS 备份，撤销环境变量/PATH 条目 |
| 其他项目需要用户级 runtime Skill | 禁用前查调用/使用方并保留备份 | 将 Skill 目录恢复到原发现路径 |
| 默认推理降低影响复杂任务 | 日常默认与任务级覆盖分离 | 单任务升 `high/xhigh/ultra`，不回退全局常驻 ultra |
| H2 reconciliation 语义错误 | fresh H2、MySQL 事实对照、幂等断言 | 未应用时撤销新文件；已应用后只用更高版本 forward-fix |
| Flyway 门禁在 CI 无基线或误放行 | 显式 base SHA/ref，缺失 fail-close，正负 fixture | 回退本任务脚本/workflow 差异；旧 migration 仍保持不变 |
| `/session` 或 `/budget` 兼容回归 | 先写/更新路由契约，再删页面 | 恢复页面/路由任务自有差异；不改 API 数据 |
| 删除通知渠道导致历史订阅丢失 | G2 只读预览；非零记录需备份、恢复测试和单独授权 | 恢复表备份并使用 forward migration；禁止手改旧 migration |
| renderer 测试收敛丢失安全断言 | 先迁移断言并验证，再删 spike | 恢复 spike 文件或补回同一正式测试，不放宽生产安全限制 |

## 7. 最终验收清单

- [x] G0～G5 依次有当前事实证据；受保护 Git 交付与 post-merge 已按实际结果完成。
- [x] 日常 Codex 默认 medium、非全局 priority、非默认 Ponytail；权限只在明确受信项目放宽。
- [x] 单路任务默认 0 个子智能体；双路/高风险任务不超过计划上限；不新增 Skill。
- [x] 项目运行态 Skill 唯一；稳定 `rg` 已安装到用户 PATH 且无版本哈希；当前 Codex 宿主重启后刷新继承环境。
- [x] V219～V221、V263 在 fresh H2 与 MySQL 上最终语义对称；旧 migration 字节未改。
- [x] Flyway 旧文件修改在本地/CI 均 fail-close；新增 migration 通过；未新增 job/context。
- [x] 三个前端候选页面按裁决处理，旧 URL、预算 API、业务信息和权限未丢失。
- [x] 通知历史数据已预览并有明确处理；无占位渠道伪成功或永久 SKIPPED。
- [x] renderer 正式测试保留分页、资源拒绝、容量和时间契约。
- [x] 计划/Backlog/脚本删除均有当前引用证据；archive 和 AutoPilot 账本未误删。
- [x] 前端、后端、CI 契约、Codemap、浏览器和 diff 检查通过。
- [x] Git 操作权限、远端状态和本地验证分开表述；交付只按实际完成证据声明。
- [x] 新增后续项、关闭后续项、净变化和每个发现项归属已重算，无无载体遗留。

## 8. 零悬空与关闭项

### 8.1 本计划直接承接

- Codex 默认推理/服务档位/权限边界、Ponytail 注入、同名 Skill、子智能体规则、稳定 `rg`。
- H2 四项语义、Flyway fail-close、三页兼容删除、局部 query helper、通知占位渠道、renderer 测试。
- 第66条裁决、历史计划状态、`epics.md`、`done-issues.md` 定位、零引用脚本和条件式分支清理。

### 8.2 证据不足或无明确价值而关闭

1. 新建“Codex 优化 Skill”。现有规则和 Skill 足够，新增只会扩大冲突面。
2. 重写 long-task-gate。现有实现已有契约、恢复和测试，本轮无缺陷证据。
3. 批量禁用插件。没有逐插件使用频率和失败证据。
4. 为 query 参数建设跨服务通用工具层。单文件复用足够。
5. 改写 Git 历史压缩 `.git`。收益与恢复风险不匹配。
6. 当前删除 legacy archive。仍有回滚和台账引用。
7. 当前摘要化历史原始日志。缺少精确文件清单、保留策略和消费者证据。

实施阶段正式 Issue：新增 `0`、关闭 `0`、净变化 `0`。另删除零引用资产 `2` 个；`restart-docker` 合并建议与第66条历史空间证据阻塞均按证据不足关闭且不创建 Issue。所有 C1～C6、R1～R8 已有本轮归属，无无载体遗留项。

## 9. 实施与交付授权

- 用户已明确要求实施、研判阻塞、全部任务收口后提交并推送，授权本轮 branch、commit、push、PR、受保护合并和已合并源分支清理。
- 主线87既有成果与本计划在 `codex/mainline-87-88-closeout` 统一交付；两个其他活跃 worktree 保持原位，不纳入清理。
- 授权不扩展到生产、目标环境、强推、保护绕过、Tag、Release 或数据库历史数据写入。

## 10. 实施证据

- C1～C6：日常推理改为 `medium`，移除全局 priority；全局权限收紧为 `workspace-write + on-request`，受信项目保留显式覆盖；默认 Ponytail 关闭；同名用户 Skill 可恢复移出；子智能体默认 0、独立/高风险最多 2；稳定 ripgrep 15.2.0 已写入用户 PATH，当前 Codex/App 宿主需重启后继承。用户配置备份位于 `C:\Users\summade87114\.codex\backups\mainline-88-20260810-230924`。
- R1～R2：MySQL/H2 新增同号 V292 reconciliation；旧 migration 未改。Flyway 本地 staged 与 CI base 模式均覆盖四目录，旧文件修改 fail-close、新文件通过、缺失基线失败。
- R3～R4：三个不可达页面删除；`/session` 和 `/budget` 保持兼容；预算 API 保留；partner 查询复用现有 helper。
- R5～R6：只读数据预览为零后通知渠道收缩为 IN_APP；更新入口拒绝不支持渠道。PDF 正式测试承接分页、嵌图、外部资源拒绝、容量与时间契约，spike 删除。
- R7～R8：删除两个零引用资产；第66条历史阻塞关闭；旧计划状态校准；两套不等价 restart 入口与两个活跃 worktree 均保留。既有产品情报三文件由本轮统一承接，官方引用和本地链接已复核。PR #421/#422 的补丁等价、无占用旧分支已删除本地与远端引用。
- 验证：后端迁移/PDF `35/35`、通知 `75/75`、MySQL fresh smoke `1/1`；前端 focused `66/66`、全量 `506/506`、type-check/build/route-ledger/bundle/边界门禁通过；long-task `15/15`、workflow `14/14`、控制面 12 组及 policy `7/7` 通过；浏览器 403/404、兼容路由和控制台通过。
- 已处理阻塞：共享 Maven target 竞态、V292 后历史断言漂移、PowerShell 变量插值、旧子智能体策略断言和长 runner 超时均按失败分类修正或串行复验；未放宽生产逻辑或门禁。
- Git：源 SHA `8167830fd4220a418debab9304e02d48678973a3` 的 Push CI `31413245480` 与 Pre-PR verifier 通过；PR #428 独立 CI `31414270325` 通过并 squash 合并为 `cc72802134f2c536a22ad8c9c8c3c1ffb1edaea6`；post-merge run `31415288851` 成功。
