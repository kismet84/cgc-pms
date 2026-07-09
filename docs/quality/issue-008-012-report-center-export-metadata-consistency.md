# ISSUE-008-012 报表中心平台化缺口-M3：导出能力映射与元数据一致性回归

日期：2026-07-10
Issue：ISSUE-008-012 报表中心平台化缺口-M3：导出能力映射与元数据一致性回归
类型：正式归档 / backlog 收口 / 质量报告 / 本地提交收口
结论：通过 / 非阻塞

## 1. 收口范围

本轮仅收口 `ISSUE-008-012` 白名单改动：

- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`
- `frontend-admin/src/pages/report/catalog-entry.ts`
- `frontend-admin/src/pages/report/catalog.vue`
- `frontend-admin/src/pages/report/__tests__/catalog-entry.test.ts`
- `docs/backlog/**`
- `docs/quality/**`
- `docs/iterations/**`
- `.codex-autopilot/state.json`

不纳入本轮：

- `backend/src/main/java/com/cgcpms/report/ReportCatalogService.java` 及其他后端生产服务文件
- migration、生产配置、插件 runner、AutoPilot flag 文件
- 白名单外其他未提交改动

## 2. 原阻塞与关闭口径

本 Issue 先前的 `P1` 阻塞点是“目录元数据宣称支持导出，但真实页面/API 导出能力映射不一致，容易出现伪导出入口或错误承诺”。

当前关闭口径：

1. 后端目录测试明确约束 `alert-center` 继续声明 `exportSupport=true`，而 `api_only` 目录项统一不得再宣称具备页面导出入口。
2. 前端把“是否存在真实导出入口”的判定收敛到 `hasReportCatalogExportEntry`，仅对 `status=available` 且真实可打开页面的目录项显示“支持导出”。
3. `API-only`、未注册页面、未声明导出的目录项统一显示“无导出入口”，避免伪页面按钮或误导性元数据。

裁决：原阻塞口径已关闭，本轮不再保留 `P1` 阻塞。

## 3. 实现与一致性事实

本轮最小实现闭环如下：

1. `ReportCatalogServiceTest` 补强导出元数据断言：
   - 管理员视角下 `alert-center` 必须保留 `exportSupport=true`。
   - 非管理员视角下只有实际具备权限的 `alert-center` 才能带着导出能力暴露。
2. `catalog-entry.ts` 新增 `hasReportCatalogExportEntry`，统一导出入口判定，复用现有路由解析逻辑，不额外引入新配置层。
3. `catalog.vue` 将导出展示从原始 `record.exportSupport` 改为真实入口判定结果，页面文案收敛为“支持导出 / 无导出入口”。
4. `catalog-entry.test.ts` 新增最小前端回归，覆盖：
   - 真实页面且声明导出时返回 `true`
   - `API-only` 目录项返回 `false`
   - 未注册页面返回 `false`
   - 未声明导出返回 `false`

## 4. 验收与复审证据

本轮收口直接采信已完成的 D/E 结论，并补充当前白名单差异核对：

### D 最终验收

- 结论：通过
- 采信范围：
  - `cd backend; .\mvnw.cmd "-Dtest=ReportCatalogServiceTest" test`
  - `cd frontend-admin; pnpm exec vitest run src/pages/report/__tests__/catalog-entry.test.ts`
  - `cd frontend-admin; pnpm type-check`
  - `cd frontend-admin; pnpm build`
- 验收要点：
  - 后端目录元数据与权限过滤断言通过
  - 前端导出入口守卫通过
  - 未新增 migration，未放宽租户、项目、角色和权限边界

### E 复审

- 结论：通过
- 复审要点：
  - 导出能力展示不再直接信任静态元数据，而是绑定真实页面入口
  - `API-only` 项不会生成伪导出入口
  - 本轮改动仍保持最小实现，未把问题扩大为异步导出平台或报表引擎改造

### 当前补充核对

- `git diff --check`：本轮收口前重新执行，结果通过
- 白名单差异核对：仅包含本 Issue 允许提交的代码与文档文件

## 5. Backlog 与 AutoPilot 同步动作

本轮最小同步如下：

1. 从 `docs/backlog/ready-issues.md` 移除 `ISSUE-008-012` 的 Ready 状态，并将当前 Ready 队列标记为“暂无合格 Ready，待下一轮拆题”。
2. 在 `docs/backlog/done-issues.md` 新增 `ISSUE-008-012` 完成记录。
3. 在 `docs/backlog/current-focus.md` 标注 `ISSUE-008-012` 已收口，当前需由主线程/A 重新拆 Ready。
4. 在 `docs/iterations/iteration-2026-07-10-report.md` 追加本 Issue 收口摘要。
5. 更新 `.codex-autopilot/state.json`：
   - `iterationCompleted=1`
   - `remainingIterations=9`
   - `iterationLastCountedIssue="ISSUE-008-012"`
   - 保持 `enabled=true`，并维持可继续下一轮拆题的运行状态

## 6. Git 收口边界

本轮 Git 收口遵守以下边界：

1. 提交前仅允许 stage 本 Issue 白名单文件。
2. 不触碰 `stop.flag`、`pause.flag`、`enabled.flag`。
3. 不执行 push，不处理白名单外脏改动。

## 7. 最终裁决

正式交付物：

- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`
- `frontend-admin/src/pages/report/catalog-entry.ts`
- `frontend-admin/src/pages/report/catalog.vue`
- `frontend-admin/src/pages/report/__tests__/catalog-entry.test.ts`
- `docs/quality/issue-008-012-report-center-export-metadata-consistency.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `.codex-autopilot/state.json`

验收证据：

- D 最终验收：后端测试、前端测试、`type-check`、`build` 已通过。
- E 复审：通过，确认导出入口与元数据口径一致。
- 当前补充核对：`git diff --check` 通过；白名单差异核对通过。

临时产物：

- `backend/target/**`、`frontend-admin/dist/**` 属于本地验证产物，未纳入提交。

结论：通过。
阻塞：无。
剩余风险：

1. 当前 Ready 队列已空，下一轮仍需主线程/A 基于长期任务池重新拆题，不能把本轮闭环外推为“报表中心平台化已整体完成”。
2. 本轮只校正目录元数据与真实导出入口映射，不覆盖导出文件内容正确性、导出性能或异步导出链路。
