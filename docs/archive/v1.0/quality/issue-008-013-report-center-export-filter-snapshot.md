# ISSUE-008-013 报表中心平台化缺口-M4：导出入口与筛选快照一致性回归

日期：2026-07-10
Issue：ISSUE-008-013 报表中心平台化缺口-M4：导出入口与筛选快照一致性回归
类型：正式归档 / backlog 收口 / 质量报告 / 本地提交收口
结论：通过 / 非阻塞

## 1. 收口范围

本轮仅收口 `ISSUE-008-013` 白名单改动：

- `backend/src/main/java/com/cgcpms/report/service/ReportCatalogService.java`
- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
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

`ISSUE-008-012` 收口后保留的剩余风险是：“目录已经声明支持导出，但 `alert-center` 真实导出结果是否严格复用当前筛选快照仍未被正式约束”。

当前关闭口径：

1. 报表目录中的 `alert` 类目录项不再只看权限码，还要同时满足真实可访问范围，避免无项目访问范围的用户继续看到预警导出入口。
2. `alert-center` 页面导出按钮在当前列表为空时禁用，不再允许空快照直接触发导出。
3. 前端导出测试直接校验当前列表快照生成的 CSV 内容，覆盖项目、规则域、规则类型、处理状态、已读状态、时间与消息摘要，确保导出结果与页面当前展示口径一致。
4. 后端补充同租户无项目访问范围成员的统计导出拒绝断言，保持租户、项目、角色与导出权限边界不放宽。

裁决：`ISSUE-008-013` 的“导出入口与筛选快照一致性”口径已闭环，原剩余风险关闭。

## 3. 实现事实

本轮最小实现闭环如下：

1. `ReportCatalogService` 引入 `AlertAccessScopeResolver`，对 `alert` 目录项追加真实访问范围判定；仅当当前租户下存在可访问项目且规则域非空时，非管理员用户才可见预警类目录项。
2. `ReportCatalogServiceTest` 补强非管理员场景，确认无预警访问范围时 `alert-center` 与 `alerts-processing-report` 同时不可见。
3. `AlertControllerTest` 新增同租户但无项目访问范围成员访问 `/alerts/processing-report` 被拒绝的回归断言，保持导出/统计接口边界一致。
4. `AlertTablePanel.vue` 将导出按钮与当前列表快照绑定；`alerts.length === 0` 时按钮禁用。
5. `frontend-admin/src/pages/alert/__tests__/index.test.ts` 新增导出回归，直接验证：
   - 导出文件名格式正确
   - CSV 头与两条当前列表记录一致
   - 多行消息被压平成单行摘要
   - 空列表时导出按钮禁用

## 4. 验收与复审证据

本轮正式收口采信既有 D/E 结论，并补当前 Git/状态核对。

### D 验收

- 结论：通过
- 采信范围：
  - `cd backend; .\mvnw.cmd "-Dtest=ReportCatalogServiceTest,AlertControllerTest" test`
  - `cd frontend-admin; pnpm exec vitest run src/pages/alert/__tests__/index.test.ts`
  - `cd frontend-admin; pnpm type-check`
  - `cd frontend-admin; pnpm build`
- 验收要点：
  - 后端目录可见性与导出统计权限边界回归通过
  - 前端导出快照与空态禁用回归通过
  - 未新增 migration，未放宽租户、项目、角色或导出权限边界

### E 审查

- 结论：通过
- 审查要点：
  - 改动保持最小闭环，没有把单页导出问题扩大成异步导出平台或新的报表定义模型
  - 导出快照约束落在现有页面与现有目录服务上，没有引入额外抽象层
  - 权限边界同时覆盖目录入口与统计导出接口，不是只修前端按钮态

### 当前补充核对

- `git diff --check`：本轮收口前重新执行，结果通过
- AutoPilot flag 状态：`stop.flag` 不存在、`pause.flag` 不存在、`enabled.flag` 存在且保持启用
- 白名单核对：本轮仅 stage 允许提交文件，不包含白名单外路径

## 5. Backlog 与 AutoPilot 同步动作

本轮同步如下：

1. `docs/backlog/ready-issues.md` 将 `ISSUE-008-013` 移出 Ready，并标记当前无合格 Ready，待下一轮拆题。
2. `docs/backlog/done-issues.md` 新增 `ISSUE-008-013` 完成记录。
3. `docs/backlog/current-focus.md` 标注 `ISSUE-008-013` 已完成正式收口，当前 Ready 队列为空，待主线程下一轮拆题或解阻。
4. `docs/iterations/iteration-2026-07-10-report.md` 追加本 Issue 收口摘要。
5. `.codex-autopilot/state.json` 更新为：
   - `iterationCompleted=2`
   - `remainingIterations=8`
   - `iterationLastCountedIssue="ISSUE-008-013"`
   - `enabled=true`
   - `status="RUNNING"`

## 6. Git 收口边界

本轮 Git 收口遵守以下边界：

1. 仅 stage 本 Issue 白名单文件。
2. 不处理白名单外脏改动，不清理工作区。
3. 不执行 push，不改 AutoPilot flag 文件。

## 7. 最终裁决

正式交付物：

- `backend/src/main/java/com/cgcpms/report/service/ReportCatalogService.java`
- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
- `frontend-admin/src/pages/alert/components/AlertTablePanel.vue`
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`
- `docs/quality/issue-008-013-report-center-export-filter-snapshot.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `.codex-autopilot/state.json`

验收证据：

- D 验收：目标后端测试、前端测试、`type-check`、`build` 已通过。
- E 审查：通过，确认实现保持最小闭环且权限边界未回退。
- 当前补充核对：`git diff --check` 通过；AutoPilot flag 状态正常；白名单核对通过。

临时产物：

- `backend/target/**`、`frontend-admin/dist/**` 属于本地验证产物，未纳入提交。

结论：通过。
阻塞：无。
剩余风险：

1. 当前 Ready 队列已空；下一轮要继续平台化推进，仍需主线程基于长期任务池重新拆题或先处理当前阶段阻塞。
2. 本轮只覆盖 `alert-center` 的当前列表快照导出一致性，不覆盖超大数据量导出、异步导出、导出审计留痕或跨页全量导出能力。
