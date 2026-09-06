# 历史 Live 测试夹具分支核对与本地恢复

> 日期：2026-09-06；当前基线：`00ff836fd2a4f79c2e7ad6047190e99600214646`。
> 历史提交：`6b8be7575b1bcc640a910d1c22527943571cfaa7`，分支 `codex/preserve-live-fixtures-6b8`。
> 范围：用户要求实施历史分支核对与处置，随后明确授权提交、推送、合并后清理；不含数据库加载、运行环境变更或发布。
> Git证据：本报告随实现提交，最终同SHA CI、受保护PR、合并及两条分支清理证明记录于关联PR，避免循环追加自引用提交。

## 核对结论

旧修复尚未被当前实现替代，不应当作冗余分支丢弃。`git cherry master`显示独占补丁；历史提交的11份非Codemap文件在恢复前均与该提交父版本一致。首次恢复后这11份文件与历史提交逐文件差异为空；随后根据当前权限复核修正专业职责映射、补充受控节点快照和负向契约，不盲目照搬历史补丁。历史3份Codemap只改变生成元数据，不复用旧快照，按当前基线重新生成并验证。

| 范围 | 当前证据与本轮恢复 |
| --- | --- |
| 登录、shell、根路径测试标题 | 当前Dashboard按persona显示角色名称；修正旧“经营驾驶舱”硬编码，登录测试允许角色驾驶舱后缀 |
| 项目会计与技术角色测试 | `AppShell.vue`及账号夹具将`ui26.bm01`映射为`SAFETY_LEAD`；使用`ui26.cost01`验证bm视图，技术活动标题与`dashboard/model.ts`一致 |
| 审批动作 | 受控详情直接断言服务端`availableActions`，不假设每个审批节点均可转办/加签；保留权限缺失时隐藏动作测试 |
| 根路径通知流夹具 | 与`notificationStream.ts`的clientId/sessionStorage合同一致，固定本测试文档身份，不改SDK或业务逻辑 |
| 项目成员夹具 | V295仅允许七类项目职责码；29条自定义权限测试成员使用EMPLOYEE，2条既有管理员责任人保留PROJECT_MANAGER。专业职责会被ApproverResolver直接用于PROJECT_ROLE分派，不将27条测试账号提升为专业负责人；无新增系统角色授予 |
| 工作流夹具 | V301停用`COST_SUBJECT_MAPPING`；演示矩阵改为24类并将该阶段版本从5升级到6，静态契约同时更新 |

调用与影响边界：`verify-live-all.ps1`经`load.ps1`加载版本化演示SQL并调用`verify.ps1`、显式`liveSpecs`；五份E2E由Playwright消费。仅改变测试期待和演示夹具，未改业务API、权限授予、迁移或模块关系。Codemap定向核对`local-live-e2e`、`v2-client`、`workflow`、`dashboard-query-orchestration`节点及相关tests/evidence。

独立只读复核发现并由主线程验证两项直接风险：专业职责映射会污染审批候选；受控合同节点未写入V293快照字段，结果可能受加载历史影响。前者改为普通测试成员，后者仅在`160-role-dashboard-data.sql`的受控节点补齐五个字段的模板复制及upsert。V293当前合同模板本身关闭转办/加签，三动作断言不变。其他历史夹具不顺带重写。

项目成员映射为测试范围夹具，不给自定义账号授予系统EMPLOYEE角色；正常成员创建API仍要求系统角色匹配。质安/技术责任人验证和收尾候选只要求有效成员，不依赖专业职责码，因此保持精确权限测试目标。

修正后独立只读复验通过，两项直接风险关闭；主线程核对当前差异与相同静态契约通过结果。没有把未运行的数据库或Live验证改称通过。

## 本轮验证

- `test-verify-live-all.ps1`通过，包含31条canonical成员检查、24模板和版本6约束，以及7项负向前置、9组live开关契约。
- 新增用户/职责映射与五个快照字段双路径契约，先分别复现`LIVE_EVIDENCE_CUSTOM_ACCOUNT_DUTY_ESCALATION`和`LIVE_EVIDENCE_CONTROLLED_NODE_SNAPSHOT_MISSING:node_type`；分类`quality_or_security`，修复后同命令通过。
- 定向ESLint通过，无新增格式警告；`pnpm --dir frontend-admin-v2 type-check`通过。
- `global-context-contract`、`dashboard-model`共44项通过；`workflow-page`、`workflow`、`dashboard-page`共29项通过，合计73项。
- 五份Playwright文件`--list`成功发现30项测试；这只是收集/编译检查，**没有执行真实Live测试**，不能代替当前基线的运行态通过证据。
- Codemap首次验证准确检出`delivery_tooling/frontend_v2`陈旧，分类`quality_or_security`；重建后验证通过。未将旧快照标记为不适用。
- 未执行SQL、未重放演示加载器、未修改当前dev数据库。历史81/81结果只用于定位来源，不作为本轮通过依据。

## 保留与交付边界

- 新本地实施分支：`codex/restore-historical-live-fixtures`，不移动其他工作树。
- 历史分支已创建完整Git bundle并通过`git bundle verify`，包含精确原分支头；恢复文件保留在本地忽略的测试产物目录，未纳入版本库。
- 清理门禁：实现通过同SHA CI并受保护合并、post-merge核验和本地master同步后，才清理实施分支；历史分支还须证明11份原修复已完整承接或按本报告修正、旧Codemap仅元数据且已重建，完整bundle独立保留。不能因旧分支没有同名远端而直接丢弃。
- 新增正式后续项0、关闭正式后续项0、净变化0；数据库重放和Live证据未执行，不宣称完整运行态验收。
- 回滚仅限本轮修改；历史分支与bundle均可恢复原成果。不得清理其他工作树、缓存、stash或既有恢复备份。
