# AutoPilot 迭代报告（2026-07-12）

## ISSUE-037-001：采购低库存补货建议最小闭环

- 状态：Done；计入 `启动迭代-10` 第 1 条实施型 Ready Issue。
- 修改范围：低库存补货入口、采购申请一次性预填、采购申请服务信任边界与采购经理最小权限。
- 验证：后端 59/59、前端 44/44、类型检查、Ready lint、health gate 与真实采购经理预填/创建/清理闭环通过。
- 自动合并：本地 commit `2fc8d22bf`；`autoPush=false`。
- 正式报告：`docs/quality/ISSUE-037-001-采购低库存补货建议最小闭环验收报告.md`。

## ISSUE-037-002：供应商交付档案最小闭环

- 状态：Done；计入 `启动迭代-10` 第 2 条实施型 Ready Issue。
- 修改范围：供应商交付三分类、已审批验收累计、采购驾驶舱页面与验收明细输入校验。
- 验证：后端 24/24、前端 18/18、类型检查、`git diff --check`、独立补修、180 秒稳定等待与真实采购经理页面通过。
- 自动合并：本地 commit `d7b9a872e`；`autoPush=false`。
- 正式报告：`docs/quality/ISSUE-037-002-供应商交付档案最小闭环验收报告.md`。

## ISSUE-037-003：驾驶舱项目数据范围统一收口

- 状态：Done；计入 `启动迭代-10` 第 3 条实施型 Ready Issue。
- 修改范围：项目经理与管理驾驶舱统一可见 ACTIVE projectIds、工作流/合同/风险 fail-close 与批量聚合。
- 验证：后端 21/21、17 条 SQL 性能证据、`git diff --check`、独立安全复核、180 秒稳定等待与 health gate 通过。
- 自动合并：本地 commit `f1b7e0670`；`autoPush=false`。
- 正式报告：`docs/quality/ISSUE-037-003-驾驶舱项目数据范围统一收口验收报告.md`。

## ISSUE-037-004：分包 WBS 单前置 FS 依赖与延期风险

- 状态：Done；计入 `启动迭代-10` 第 4 条实施型 Ready Issue。
- 修改范围：`sub_task` V140 migration、分包任务服务/VO、分包任务页面/类型/测试、产品情报与 backlog。
- 验证：后端 25/25，前端 9/9，类型检查、`git diff --check`、MySQL V140、health gate 与真实 API 创建/回读/拒绝/清理闭环通过。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：继续补货，优先把现场日报 Candidate 收敛为最小可验证 Ready；未命中 stop/pause。
- 正式报告：`docs/quality/ISSUE-037-004-分包WBS单前置FS依赖与延期风险验收报告.md`。

## ISSUE-037-005：现场日报最小闭环

- 状态：Done；计入 `启动迭代-10` 第 5 条实施型 Ready Issue。
- 修改范围：V141 日报表、日报 API/权限/附件授权、前端页面/路由/测试、产品情报与 backlog。
- 验证：后端 32/32，前端 33/33，类型检查、`git diff --check`、MySQL V141 与真实 API/附件闭环通过。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：产品情报刷新与候选补货；未命中 stop/pause。
- 正式报告：`docs/quality/ISSUE-037-005-现场日报最小闭环验收报告.md`。

## ISSUE-037-006：库存项安全库存阈值与补货建议联动

- 状态：Done；计入 `启动迭代-10` 第 6 条实施型 Ready Issue。
- 修改范围：V142 安全库存阈值/权限、库存服务/API/测试、库存台账动态预警与补货数量、产品情报与 backlog。
- 验证：后端 48/48，前端 4/4，类型检查、Ready lint、`git diff --check`、独立审查、MySQL V142、health gate、真实 SUPER_ADMIN KPI 差值与真实 PURCHASE_MANAGER 更新/恢复通过。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：继续产品情报刷新与候选补货；未命中 stop/pause。
- 正式报告：`docs/quality/ISSUE-037-006-库存项安全库存阈值与补货建议联动验收报告.md`。

## ISSUE-037-007：现场日报天气摘要与在场人数补强

- 状态：Done；计入 `启动迭代-10` 第 7 条实施型 Ready Issue。
- 修改范围：V143 两个可空字段、严格整数绑定、日报服务/VO/测试、现有日报表单与产品情报/backlog。
- 验证：后端 6/6、前端 2/2、类型检查、`git diff --check`、独立审查、MySQL V143、health gate 和真实 API NULL→0/提交拒写/清理通过。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：继续产品情报刷新与候选补货；未命中 stop/pause。
- 正式报告：`docs/quality/ISSUE-037-007-现场日报天气摘要与在场人数补强验收报告.md`。

## ISSUE-037-008：现场日报 dev-login 直达路由白名单修复

- 状态：Done；计入 `启动迭代-10` 第 8 条实施型 Ready Issue。
- 修改范围：DevAuthController 单一 `/site` 前缀、AuthController 安全回归、产品情报与 backlog。
- 验证：AuthController 15/15、专项安全用例、`git diff --check`、独立审查、180 秒稳定等待、真实站内 302→200 与站外回落通过。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：恢复产品差距补货；未命中 stop/pause。
- 正式报告：`docs/quality/ISSUE-037-008-现场日报dev-login直达路由验收报告.md`。

## ISSUE-037-009：库存项人工补货目标量联动

- 状态：Done；计入 `启动迭代-10` 第 9 条实施型 Ready Issue。
- 修改范围：V144 可空目标量、库存设置原子接口/兼容校验、库存台账设置与补货预填、产品情报与 backlog。
- 验证：后端 53/53、前端 4/4、类型检查、Ready lint、`git diff --check`、独立审查、MySQL V144、180 秒稳定等待和真实采购经理设置/回读/非法关系拒绝/NULL 恢复通过。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：重新刷新产品情报并裁决第 10 条；未命中 stop/pause。
- 正式报告：`docs/quality/ISSUE-037-009-库存项人工补货目标量联动验收报告.md`。

## ISSUE-037-010：库存项人工补货提前期与计划日期预填

- 状态：Done；计入 `启动迭代-10` 第 10/10 条实施型 Ready Issue。
- 修改范围：V145 可空自然日提前期、库存组合设置/兼容性、补货 plannedDate query、采购申请严格日期预填、产品情报与 backlog。
- 验证：后端 56/56、前端 15/15、类型检查、Ready lint、`git diff --check`、两轮独立审查、MySQL V145、180 秒稳定等待和真实采购经理 7/省略/小数拒绝/0/NULL 恢复通过。
- 自动合并：本地 commit only；`autoPush=false`。
- 停止原因：达到 `启动迭代-10` 的 10/10 实施上限，不派发下一任务。
- 正式报告：`docs/quality/ISSUE-037-010-库存项人工补货提前期与计划日期预填验收报告.md`。

## 本轮最小总验收

- 完成：`ISSUE-037-001` 至 `ISSUE-037-010` 共 10 条实施型 Ready，均有本地提交与正式质量报告。
- 阻塞：无。
- 非阻塞观察：Dashboard 性能测试门槛文案、库存设置独立跨项目/并发专项，以及供应商级提前期/工作日历/预测仍作为后续治理或产品候选，均已沉淀到 Current Focus。
- 当前 focus：允许在新的启动指令下重新刷新产品情报；本次运行因 10/10 上限停止。
- 发布边界：未发布生产、未连接生产数据库、未 push。

## `启动迭代-5` 当前进度

- `ISSUE-037-014`：Done；现场日报当日已审批领料只读联动，后端 10/10、前端 6/6、类型检查和独立审查通过。
- `ISSUE-037-015`：Done；WBS 单前置 FS 开工门禁，后端 26/26、前端 9/9、类型检查和补修后独立复核通过。
- `ISSUE-037-016`：Done；WBS 软删除编号冲突修复，后端 27/27、编号复用二次删除与独立事务审查通过。
- 当前计数：3/5；Ready 为空，下一步须先刷新产品情报，不从长期计划直接凑任务。
- 发布边界：本地 commit only，`autoPush=false`；未发布生产、未连接生产数据库。

## `启动迭代-5` 最小总验收

- 完成：`ISSUE-037-014` 现场日报已审批领料、`ISSUE-037-015` WBS 单前置状态门禁、`ISSUE-037-016` WBS 软删除编号冲突，共 3 条实施型 Ready。
- 阻塞：无。
- 非阻塞观察：真实浏览器视觉、前端响应式交互、删除事务故障注入未执行；均已沉淀到 Current Focus，不影响本轮代码级裁决。
- 当前 focus：Ready 为空；人员/设备、多前置和伪状态回退候选均未通过最小闭环门，3/5 因无合格 Candidate 停止。
- 发布边界：本地 commit only；未发布生产、未连接生产数据库、未 push。

## `ISSUE-037-013` 后续风险收口

- 审计即时可见性：统一审计监听器移除 `@Async`，保留 `REQUIRES_NEW` 与异常隔离；新增事件发布返回前完成写入的回归断言。
- 验证：`OperationAuditServiceTest,OperationAuditAspectTest,SiteDailyLogServiceTest,SiteDailyLogControllerTest` 共 22/22 通过，`git diff --check` 通过。
- CodeGraph：确认旧告警为并发写锁超出重试预算；占锁写入结束后 `codegraph sync .` 成功，`codegraph status .` 为 up-to-date，无需重建索引或修改规则。

## AutoPilot 控制面整改复验

- 根因：迭代上限只由 continuous runner 主循环判断，`enabled.flag`、checkpoint 和 status 未同步终态，导致 state 已为 `LIMIT_REACHED` 时 checkpoint 仍返回 `go`。
- 修复：runner 达到上限时移除 `enabled.flag`；checkpoint 读取 state 并返回 `limit_reached`；status 回退展示 `iterationLastCountedIssue`，终态不再显示 `NEW_RUN` recovery。
- TDD：新增 limit fixture 先稳定失败于“上限未关闭 future dispatch”，最小修复后 `test-continuous-runner.ps1` 全套通过。
- 无人值守：复用现有 `test-unattended-canary.ps1` 完成 20 轮隔离验证，覆盖自动执行、本地提交、证据路径、零人工干预、零范围违规和 20/20 上限停止。
- 当前状态：`enabled.flag=false`、checkpoint=`limit_reached`、status=`LIMIT_REACHED`、lastIssue=`ISSUE-037-010`、recoveryAction=`NONE`。

## 双图谱路由整改复验

- 历史事实：`ISSUE-037-001` 至 `ISSUE-037-010` 执行期间使用了 CodeGraph 与 `rg`，但未调用 `codebase-memory-mcp`；以下整改后查询不追算为原迭代证据。
- 根因：原规则只规定 CodeGraph 优先级和 `codebase-memory-mcp` 只读边界，没有后者的必用条件，A/F 角色契约与 iteration 模板也没有图谱证据字段。
- 修复：跨层影响、跨前后端/跨语言关系、复杂多跳调用链、架构边界/聚类或 CodeGraph 召回不足时，必须补充调用 `codebase-memory-mcp`；A 记录查询目的、命中摘要和交叉核验，F 写入图谱检索证据或不适用原因。
- TDD：`test-tool-routing.ps1` 先稳定失败于 `AGENTS.override.md missing required tool-routing text: 跨层影响`，最小规则与模板整改后通过。
- 图谱检索证据：CodeGraph 查询目的=定位 AutoPilot runner、checkpoint、iteration report 与测试入口；`codebase-memory-mcp` 查询目的=核对 `replenishmentLeadDays` 到 `plannedDate` 的跨层影响，返回 43 个关联结果并命中 DTO、实体、VO、Controller/Service、后端测试、前端 API 与 `useStockLedger`；交叉核验=`rg` 在当前分支确认对应符号与文件真实存在。
- 安全边界：仅执行本地只读查询，未重建/升级索引，未运行 install/uninstall，未修改业务代码。

## ISSUE-037-011：现场日报已审批材料到货只读联动

- 状态：Done；计入 `启动迭代-3` 第 1/3 条实施型 Ready Issue。
- 修改范围：日报详情跨域只读聚合、到货 VO、前端详情 API/只读表格、产品情报与 backlog；无 migration，无验收/库存/成本写路径修改。
- 验证：后端 7/7、前端 3/3、类型检查、Ready lint、`git diff --check` 与主线程安全审查通过。
- 图谱检索证据：CodeGraph 先定位日报/验收/库存跨层调用和 blast radius；`codebase-memory-mcp` 复核后端架构边界。CodeGraph 编辑后自动同步因文件锁停用，归类 `tool_config`，以当前 diff、编译与测试交叉核验。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：继续产品情报刷新与候选补货；未命中 stop/pause。
- 正式报告：`docs/quality/ISSUE-037-011-现场日报已审批材料到货只读联动验收报告.md`。

## ISSUE-037-012：现场日报当日计划任务只读联动

- 状态：Done；计入 `启动迭代-3` 第 2/3 条实施型 Ready Issue。
- 修改范围：日报详情增加分包 WBS 当日计划任务只读聚合、最小 VO/前端表格、产品情报与 backlog；无 migration，无任务写路径修改。
- 验证：后端 8/8、前端 4/4、类型检查、Ready lint、`git diff --check` 与主线程权限审查通过。
- 图谱检索证据：CodeGraph 定位 `SubTask`/`SubTaskService` 和日报关联点，但编辑期自动同步因文件锁停用；`codebase-memory-mcp` 只读确认 `SubTask` 的租户/项目/计划日期/状态/进度字段与服务边界，当前 diff/编译/测试完成交叉核验。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：继续产品情报刷新，完成第 3/3 条后停止下一任务派发。
- 正式报告：`docs/quality/ISSUE-037-012-现场日报当日计划任务只读联动验收报告.md`。

## ISSUE-037-013：现场日报变更历史只读展示

- 状态：Done；计入 `启动迭代-3` 第 3/3 条实施型 Ready Issue。
- 修改范围：日报 CREATE 审计业务 ID 绑定、详情最小审计记录、前端只读历史、产品情报与 backlog；无 migration，无统一审计模块修改。
- 验证：后端 9/9、前端 5/5、类型检查、Ready lint、`git diff --check` 与主线程敏感字段审查通过。
- 图谱检索证据：CodeGraph 与 `codebase-memory-mcp` 只读确认日报注解、审计 aspect、异步持久化和审计实体字段；CodeGraph 自动同步文件锁问题归类 `tool_config`，当前 diff/编译/测试交叉核验。
- 自动合并：本地 commit only；`autoPush=false`。
- 停止原因：达到 `启动迭代-3` 的 3/3 实施上限，停止下一任务派发。
- 正式报告：`docs/quality/ISSUE-037-013-现场日报变更历史只读展示验收报告.md`。

## `启动迭代-3` 最小总验收

- 完成：`ISSUE-037-011` 已审批材料到货联动、`ISSUE-037-012` 当日计划任务联动、`ISSUE-037-013` 变更历史，共 3 条实施型 Ready。
- 阻塞：无。
- 非阻塞观察：三条均完成代码级/自动化验收，未构造真实角色与真实业务数据做浏览器验收；相关限制已写入各质量报告。
- 当前 focus：产品地图已回写，Ready 为空；未来新启动可继续刷新产品情报，但本轮因 3/3 上限停止。
- 发布边界：未发布生产、未连接生产数据库、未 push。

## ISSUE-037-017：BaseEntity 备注写入契约修复

- 状态：Done；计入 `启动迭代-10` 第 1/10 条实施型 Ready Issue。
- 修改范围：只移除 `BaseEntity.remark` 的 `READ_ONLY`，新增一个共享 JSON 契约测试；无 Controller、Service、前端或 migration 变更。
- 验证：`BaseEntityJsonContractTest` 1/1、`git diff --check`、严格范围核对和独立安全审查通过。
- 图谱检索证据：CodeGraph 定位 `BaseEntity` 及实体直绑 Controller/Service 调用面；`codebase-memory-mcp` 交叉核验备注在后端与前端的跨层使用，当前 diff 与专项测试完成最终确认。
- 失败分类：首次 runner 阻塞属于 `tool_config`；scope gate 将未跟踪目录折叠为路径而误报越界，业务实现与专项测试无失败。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：Ready 清空后进入产品情报补货；未命中 stop/pause。
- 正式报告：`docs/quality/ISSUE-037-017-BaseEntity备注写入契约修复验收报告.md`。

## ISSUE-037-018：子智能体超时、悬挂执行线程退役与有限重派治理

- 状态：Done；计入本次 `启动迭代-10` 第 2/10 条实施型 Ready Issue。
- 修改范围：复用连续 runner/state/progress/context，补齐 300/600 秒生命周期证据、一次 repair、第二次 blocked、有界长命令声明和 schema；无业务代码或数据库变更。
- 验证：executor stall、state machine、progress fingerprint、制品 schema、`git diff --check` 全部通过。
- 独立审查：首次 FAIL 并发现 PID 复用误杀与长命令错误计时；两项按 TDD 补修后由主线程复验，阻塞解除。
- 失败分类：执行器内部 reviewer 无回传及 Git CRLF stderr 中断均为 `tool_config`；未定性为业务质量失败。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：Ready 清空后继续产品情报补货；未命中 stop/pause。
- 正式报告：`docs/quality/ISSUE-037-018-子智能体超时悬挂线程退役与有限重派治理验收报告.md`。

## ISSUE-037-019：后端接口无前端入口只读盘点与治理裁决

- 状态：Done；计入本次 `启动迭代-10` 第 3/10 条实施型 Ready Issue。
- 交付：53 个 Controller、321 个唯一 HTTP 方法的前端 API/路由/页面/菜单静态映射；无业务代码、配置、数据库或运行态变更。
- 分类：有用户入口 219、前端调用但无独立页面 57、内部/集成/运维 4、需补入口 30、待废弃 0、需要确认 11；总计 321、重复 0。
- 验证：Ready lint、后端 compile、前端 35 tests、类型检查与 `git diff --check` 通过。
- 独立审查：PASS；全查 30 条需补入口、11 条需要确认，并抽查 24 个存在入口的业务域，后端引用与权限注解无不符。
- 失败分类：CLI Reviewer 因 response schema 缺少显式类型返回 400，归类 `tool_config`；修复 schema 后由新鲜独立 reviewer 完成裁决。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：按责任域另拆入口补建/兼容性核实；Ready 清空后继续产品情报补货。
- 正式报告：`docs/quality/ISSUE-037-019-后端接口无前端入口只读盘点与治理裁决验收报告.md`。

## ISSUE-037-020：长期计划描述性标题误入候选修复

- 状态：Done；计入本次 `启动迭代-10` 第 4/10 条实施型 Ready Issue。
- 修改范围：长期计划候选提取增加 major >= 7 的最小边界，并扩充现有 refill 自测；无业务代码、依赖或运行态变更。
- 验证：`test-refill.ps1` 与 `git diff --check` 通过。
- 独立审查：PASS；第 2–6 章描述性标题被排除，第 7–9 章开发计划保留，Ad-hoc/Ready/stop/pause/blocked-first 未回退。
- 失败分类：首次 Reviewer 因验证哈希与 review 文件 BOM/换行哈希不一致而阻塞，归类 `tool_config`；统一无 BOM UTF-8 review diff 后全套 runner 自测通过。
- 自动合并：本地 commit only；`autoPush=false`。
- 下一轮：Ready 清空后继续产品情报补货。
- 正式报告：`docs/quality/ISSUE-037-020-长期计划描述性标题误入候选修复验收报告.md`。
