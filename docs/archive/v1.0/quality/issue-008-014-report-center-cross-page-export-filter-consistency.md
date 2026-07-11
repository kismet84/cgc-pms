# ISSUE-008-014 报表中心平台化缺口-M5：跨页全量导出与筛选语义一致性回归

日期：2026-07-10
Issue：ISSUE-008-014 报表中心平台化缺口-M5：跨页全量导出与筛选语义一致性回归
类型：正式归档 / backlog 收口 / 质量报告 / 本地提交收口
结论：通过 / 非阻塞

## 1. 收口范围

本轮仅收口 `ISSUE-008-014` 白名单改动：

- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`
- `frontend-admin/src/pages/alert/index.vue`
- `frontend-admin/src/pages/alert/components/AlertTablePanel.vue`
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`
- `docs/backlog/**`
- `docs/quality/**`
- `docs/iterations/**`
- `.codex-autopilot/state.json`

不纳入本轮：

- migration、deploy、生产配置、插件 runner
- `stop.flag`、`pause.flag`、`enabled.flag`
- 白名单外其他未提交改动

## 2. 关闭口径

`ISSUE-008-013` 收口后保留的剩余风险是：“`alert-center` 导出虽然已经与目录入口、权限边界和当前页快照一致，但跨页/全量导出是否仍严格受当前筛选语义约束还没有正式回归”。

当前关闭口径：

1. `alert-center` 导出不再只取当前页内存快照，而是按当前筛选参数重新抓取分页数据，直到覆盖当前筛选结果全集。
2. 分页切换后，项目、规则域、规则类型、严重度、处理状态、是否默认作用域等筛选条件继续保留，不因导出动作回退为无筛选或仅第一页。
3. 导出总量超过阈值时前端直接阻断，提示先收窄筛选，不继续请求后续分页，也不伪造截断结果。
4. 后端目录与接口访问边界继续保持不放宽；无告警权限或无可访问域的成员，不因跨页导出补强而重新拿回入口。

裁决：`ISSUE-008-014` 的“跨页全量导出与筛选语义一致性”口径已闭环，原 P1/P2 阻塞均关闭。

## 3. 实现事实

本轮最小实现闭环如下：

1. `frontend-admin/src/pages/alert/index.vue` 抽出 `buildAlertListParams`，把列表查询与导出查询统一到同一组筛选参数构造，避免页面展示与导出条件分叉。
2. 导出逻辑改为先请求第一页，再根据真实 `total/pageSize` 继续抓取后续分页；若首屏或第一页真实总量超过 `1000` 条，则直接提示收窄筛选并停止导出。
3. `AlertTablePanel.vue` 的导出按钮禁用态改为复用 `store.loading`、`exportLoading` 和总条数判定，避免空列表或导出中重复点击。
4. `AlertControllerTest` 新增跨页项目筛选与域权限断言，确认不同分页参数下仍不越过项目访问范围，也不会把无权限域数据混入结果。
5. `ReportCatalogServiceTest` 新增无预警权限但有其他范围时不显示 `alert-center` / `alerts-processing-report` 的断言，保持目录入口边界不回退。
6. `frontend-admin/src/pages/alert/__tests__/index.test.ts` 新增三类回归：
   - 跨页导出按当前筛选条件抓取第 1 页和后续页
   - 总量超过阈值时只提示，不下载
   - 第 1 页真实总量超过阈值时中断后续分页抓取

## 4. 验收与复审证据

本轮正式收口采信既有 D/E 结论，并补当前 Git/状态核对。

### D 最终验收

- 结论：通过
- 采信范围：
  - `cd backend; .\mvnw.cmd "-Dtest=AlertControllerTest,ReportCatalogServiceTest" test`
  - `cd frontend-admin; pnpm exec vitest run src/pages/alert/__tests__/index.test.ts`
  - `cd frontend-admin; pnpm type-check`
- 验收要点：
  - 跨页查询与导出仍受项目筛选、规则域和角色权限约束
  - 前端导出改为按筛选条件抓取全量分页结果，空结果和超阈值路径均有回归
  - 未新增 migration，未放宽租户、项目、角色或导出权限边界

### E 最终复审

- 结论：通过
- 复审要点：
  - 改动保持在现有 `alert-center` 页面与现有测试内完成，没有扩写为异步导出平台或新表
  - 共享筛选参数构造比在导出路径重复拼装条件更稳，属于同链路最小修复
  - 目录入口边界、接口返回边界和导出结果边界三处口径一致

### Ready 前端命令替换说明

- Ready 原命令：`cd frontend-admin; pnpm test:unit -- src/pages/alert/__tests__/index.test.ts src/pages/report/__tests__/catalog-entry.test.ts`
- 本轮采信命令：`cd frontend-admin; pnpm exec vitest run src/pages/alert/__tests__/index.test.ts`
- 分类：工具配置类 / 最小等价替换，不属于业务代码失败
- 说明：
  - 本轮白名单改动未触达 `src/pages/report/__tests__/catalog-entry.test.ts`，收口复验只保留裁决必需的 `alert` 页面测试。
  - `package.json` 中 `test:unit` 本质也是 `vitest run`，改用 `pnpm exec vitest run` 属于直跑同一工具入口，减少脚本封装噪音，不改变测试语义。

### 当前补充核对

- `git diff --check`：本轮收口前重新执行，结果通过
- AutoPilot flag 状态：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`
- 白名单核对：本轮仅 stage 允许提交文件，不包含白名单外路径

## 5. Backlog 与 AutoPilot 同步动作

本轮同步如下：

1. `docs/backlog/ready-issues.md` 将 `ISSUE-008-014` 移出当前 Ready 状态，并标记当前无合格 Ready，待主线程下一轮拆题。
2. `docs/backlog/done-issues.md` 新增 `ISSUE-008-014` 完成记录。
3. `docs/backlog/current-focus.md` 标注 `ISSUE-008-014` 已完成正式收口，当前 Ready 队列为空或待下一轮拆题。
4. `docs/iterations/iteration-2026-07-10-report.md` 追加本 Issue 收口摘要。
5. `.codex-autopilot/state.json` 更新为：
   - `iterationCompleted=3`
   - `remainingIterations=7`
   - `iterationLastCountedIssue="ISSUE-008-014"`
   - `enabled=true`
   - `status="RUNNING"`

## 6. Git 收口边界

本轮 Git 收口遵守以下边界：

1. 仅 stage 本 Issue 白名单文件。
2. 不处理白名单外脏改动，不清理工作区。
3. 不执行 push，不改 AutoPilot flag 文件。

## 7. 最终裁决

正式交付物：

- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`
- `frontend-admin/src/pages/alert/index.vue`
- `frontend-admin/src/pages/alert/components/AlertTablePanel.vue`
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`
- `docs/quality/issue-008-014-report-center-cross-page-export-filter-consistency.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `.codex-autopilot/state.json`

验收证据：

- D 最终验收：目标后端测试、前端单测与 `type-check` 已通过。
- E 最终复审：通过，确认跨页导出与筛选语义统一，且未扩大设计范围。
- 当前补充核对：`git diff --check` 通过；AutoPilot flag 状态正常；白名单核对通过。

临时产物：

- `backend/target/**` 等本地测试产物未纳入提交。

结论：通过。
阻塞：无。
剩余风险：

1. 当前 Ready 队列已空；是否继续拆报表中心后续平台化题，仍需主线程/A 基于长期任务池重新裁决。
2. 本轮只覆盖同步跨页全量导出的一致性，不覆盖异步导出、导出审计留痕或超大数据量导出能力。
