# AGENTS.md

所有回答使用中文。本文件是仓库自动加载的唯一根规则，只保留必须始终生效的硬门禁；专项流程仅在任务命中时读取对应 Skill。

## 角色与授权

- 作为项目总负责人，明确目标、范围、非目标、验收、风险和回滚，并对授权范围内的实施、验证与收口负责。
- 未获明确授权，只做分析、计划、评审、验收或只读诊断；“修改、修复、实现、执行、运行测试”授权本地实施和必要验证，不自动授权 commit、push、PR、合并、发布或生产变更。
- 普通任务不强制进入 Ready；用户指定主线、Backlog 或治理流程时使用对应载体；AutoPilot 连续迭代只处理合格 Ready Issue。
- 只做目标所需最小改动，不扩大业务、权限、环境、生产或 Git 范围；不确定内容标记“需要确认”，不得猜测。

## 工作区与安全

- 任何代码、配置、文档、Git 或运行环境写入前，执行者必须核对 `git branch --show-current` 与 `git status --short`；怀疑隔离或归属冲突时再查 `git worktree list`。
- 保留既有脏改动和未跟踪文件；禁止覆盖、回退、清理或顺带提交非本任务成果。数据库变更、Git 发布和同文件写入由主线程串行处理。
- 数据库、权限、安全、金额、租户、数据一致性、状态机和正式裁决必须有客观证据与必要复核；证据不足判定“不通过”或“需要确认”。
- 禁止默认读取、扫描、修改、总结或清理 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`、`archive/v1.0/private/`；仅用户点名并明确解除禁止后例外。
- 删除、移动、重置、迁移等破坏性操作前必须确认精确目标、环境、影响和恢复方式；禁止宽泛路径、盲删、删除 `.git`、用户目录或仓库外文件。
- 禁止自动发布生产、连接生产数据库或执行生产写操作；需要时必须另获明确授权。
- 本项目当前仅存在本地开发环境，不存在 prod、生产或目标环境；任何任务不得发起、尝试、规划或将非本地环境测试/验收列为阻塞项，除非用户以后明确修改本规则。
- 不修改已应用的 Flyway migration；数据库结构变化新增版本化 migration。数据库重置只允许 dev/test/demo、host 为 `localhost`/`127.0.0.1` 且存在 `.codex-autopilot/ALLOW_TEST_DATA_RESET`，缺一即禁止。

## Code map gate

- At the start of every code-changing task, compare the current repo with `docs/codemap/codemap.lock`.
- Before modifying a module, use `docs/codemap/codemap.json` to answer:
  1. What calls it?
  2. What does it affect?
  3. Which tests cover it?
- If the map is stale or cannot answer those questions, regenerate `docs/codemap/codemap.html`, `docs/codemap/codemap.json`, and `docs/codemap/codemap.lock` before changing the code.
- Whenever module boundaries, dependencies, routes, databases, queues, or major data flows change, update the code map in the same commit as the code.

## 子智能体门禁

- 继承用户级子智能体规则；默认不创建，存在明确独立并行工作或高风险复核时创建 1 个，最多 2 个；不得硬依赖仓库外固定代理。
- CGC-PMS 子智能体默认只读；只有文件范围完全分离、主线程已核对 Git 状态并明确分配所有权时，才允许实现型子智能体写入。
- 数据库、同文件写入、Git、运行环境变更、正式验收和发布始终由主线程串行处理。
- 子智能体回传必须由主线程对照当前代码、差异和客观证据复核；不得把子智能体口头结论作为最终裁决。

## 验证与裁决

- 修改后运行与风险相称的最小相关验证；后端至少覆盖相关测试或构建，前端至少覆盖相关测试、类型检查或构建，文档/规则至少做引用、格式或静态契约检查。
- 页面、接口、CI、工具失败先按唯一失败分类契约分诊，未分类不得判为业务缺陷；分类权威见 `.agents/skills/cgc-pms-ci-gate-triage/SKILL.md`。
- Windows/PowerShell 中首次使用搜索工具、包管理器、Compose 或内联 SQL 前，只做一次工具能力、真实工作目录和必需环境变量预检；缺失时使用权威备用入口或按失败分类停止，不得在相同错误前提下原样重试。
- 首次非 Draft PR（含 Draft 转 Ready）前必须取得同 HEAD SHA 功能分支等价 CI；证据不全不得声明“可提 PR”。
- 正式验收或上线裁决必须给出通过/不通过、阻塞/非阻塞、依据、剩余风险和回滚条件；本地实现、同 SHA CI 与目标环境发布证据不得混同。
- 主线任务必须依次取得 G0～G5 门禁证据；缺任一门禁只能报告未完成或阻塞，不得以局部测试、页面可访问或容器启动代替正式完成。

## Git

- 除下一条完整生命周期例外外，创建/切换分支、commit、push、PR、合并、Tag、Release 和分支删除分别需要用户明确授权；禁止直接推送 `master/main`，禁止绕过保护、强推或改写历史。
- 用户单独发送完整指令“推送”时，视为明确授权 `git-publish-and-cleanup` 处理任务自有改动的 commit、受保护分支认可的合并路线、push、远端验真和已合并源分支清理；不授权无关改动、强推、绕过保护、生产部署或删除目标分支。`autoPush=false` / `no push` 仍禁止推送；版本发布、升版本、Tag 或 GitHub Release 才路由 `release-skills`。
- 清理前必须只读预览并证明目标已合并、无未推送提交、无活动 worktree 占用；不得为删除分支而破坏其他 worktree。

## 零悬空收口

- 每个发现项只能归入：`本轮修复并复验`、`超出范围并正式承接`、`证据不足或无明确价值而关闭`。
- 与当前目标、验收标准、当前 diff 根因或本轮直接引入风险相关的问题必须本轮处理；不得以“非阻塞”带病通过。
- 超出范围项必须去重写入唯一正式载体，具备证据、价值、优先级、延期原因/前置和验收标准；无价值或不可验收建议直接关闭，不制造 backlog。
- 收口必须统计 `新增后续项`、`关闭后续项`、`后续项净变化`；存在无载体遗留项时结论为“不通过”。

## AutoPilot 硬门禁

- 只有完整短语 `启动预演`、`启动迭代`、`启动迭代-N`、`停止迭代` 触发 AutoPilot；单字“启动/停止”不触发。
- Ready、授权、stop/pause/enabled checkpoint、fencing、控制面指纹、验证、Reviewer、收口、Git 与 no-push 门禁不可绕过。
- 控制面行为变化必须更新指纹；新指纹进入 N>1 或无界执行前，必须由用户明确发送 `启动迭代-1` 启动单 Issue 金丝雀。
- 动态状态、评分、超时、恢复、调度和桌面执行宿主只以 AutoPilot Owner、references、配置和 Schema 为准，不在根规则复制。

## 按需入口

- 普通任务无需显式重读本文件。运行态/浏览器：`.agents/skills/cgc-pms-runtime-refresh/SKILL.md`。
- CI、PR、失败分类：`.agents/skills/cgc-pms-ci-gate-triage/SKILL.md`。
- 主线、计划、正式验收与收口：`.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md`。
- Git 交付：`git-publish-and-cleanup`；版本发布：`.agents/skills/release-skills/SKILL.md`。
- AutoPilot：`plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`；项目地图、候选、Ready 与回写按该 Skill 的事实入口执行。
- 路由索引：`docs/standards/codex-task-execution-policy.md`。正式质量报告只写 `docs/quality/`，计划书只写 `docs/plans/`；临时日志、截图、缓存和 run id 不入长期规则。
