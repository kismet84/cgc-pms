# 第 21 条主线 P0-2：上线前总控状态校准与决策包重建报告（2026-07-05）

## 1. 结论

- 结论：通过
- 阻塞 / 非阻塞：非阻塞
- 当前判断：在 **2026-07-05** 当前工作区与当前运行态证据下，`P0-2` 已完成本阶段总控校准，可进入 `P1`
- 口径边界：本报告重建的是第 21 条主线 `P0-2` 的当前有效校准结论，不等于“全量门禁已重跑完毕”，也不等于“最终上线放行包已全量复验完毕”

本次结论不沿用 2026-07-03 的旧测试数量、旧 blocker 关闭口径或旧“当前有效入口”表述冒充当前状态。所有通过 / 非阻塞判断均以 **2026-07-05 实际执行结果**、当前工作区改动和当前运行态为依据。

## 2. 本轮校准范围

1. `P0-1` 当前未提交改动涉及的 dashboard / router / inventory transaction / V128 / V129 / MigrationIntegrityTest 定向门禁
2. 第 13 条旧报告中的历史 blocker 复核：
   - 后端测试
   - 前端门禁
   - `ENC(` 残留
   - 文档索引旧结论
3. 当前运行态最小核心链路复核：
   - `login`
   - `dashboard`
   - `inventory`
   - `approval`
4. `docs/README.md` 当前有效结论入口是否仍停留在 2026-07-03

## 3. 当前工作区事实

### 3.1 P0-1 已验收变更相关改动

当前 `git status --short` 中，与 `P0-1` 直接相关的改动包括：

1. `frontend-admin/src/router/index.ts`
2. `frontend-admin/src/router/__tests__/router.test.ts`
3. `frontend-admin/src/pages/dashboard/composables/useDashboardData.ts`
4. `frontend-admin/src/pages/dashboard/__tests__/DashboardDataLoading.test.ts`
5. `frontend-admin/src/pages/dashboard/__tests__/DashboardPermissionScope.test.ts`
6. `frontend-admin/src/pages/inventory/transaction.vue`
7. `frontend-admin/src/pages/inventory/__tests__/transaction.test.ts`
8. `backend/src/test/java/com/cgcpms/MigrationIntegrityTest.java`
9. `backend/src/main/resources/db/migration/V128__p01_minimal_permission_fix_pack.sql`
10. `backend/src/main/resources/db/migration/V129__clear_high_risk_db_only_permissions.sql`
11. 对应 H2 双轨迁移 `migration-h2/V128`、`migration-h2/V129`

### 3.2 非 P0-1 未归属改动

当前同时存在与 `P0-1` 不直接同域的未归属改动：

1. `frontend-admin/src/components.d.ts`

本项属于当前工作区并存事实，需要在后续提交边界收敛时单独判定，但本次未发现其直接阻塞 `P0-2` 结论。

## 4. 质量门禁校准

### 4.1 前端定向门禁

执行命令：

```powershell
pnpm vitest run src/router/__tests__/router.test.ts src/pages/dashboard/__tests__/DashboardPermissionScope.test.ts src/pages/dashboard/__tests__/DashboardDataLoading.test.ts src/pages/inventory/__tests__/transaction.test.ts
pnpm type-check
pnpm build
```

执行结果：

1. 定向单测：`4` 个文件、`53` 个测试、`53 passed`
2. `vue-tsc --noEmit` 通过
3. `vite build` 通过，当前输出为 `4415 modules transformed`

判定：

1. 已覆盖 `P0-1` 本轮前端改动的核心权限口径：
   - `dashboard:view` 仅放通路由，不再放大全 tab
   - `inventory:transaction:list` 负责页面进入
   - `inventory:transaction:add` 负责写动作按钮
   - `router` 守卫支持 legacy `dashboard:view` 与 scoped `dashboard:*:view`
2. 未跑全量 `vitest`，但本轮定向门禁足以支撑 `P0-2` 对当前改动范围的校准，不冒充全仓全量前端回归

### 4.2 后端定向门禁

执行命令：

```powershell
.\mvnw.cmd -q "-Dtest=MigrationIntegrityTest,com.cgcpms.dashboard.controller.DashboardControllerTest,com.cgcpms.inventory.MatStockControllerTest,com.cgcpms.requisition.service.MatRequisitionWorkflowSubmitTest" test
```

执行结果（以 surefire XML 为准）：

1. `MigrationIntegrityTest`：`17 tests`，`0 failures`，`0 errors`
2. `DashboardControllerTest`：`26 tests`，`0 failures`，`0 errors`
3. `MatStockControllerTest`：`5 tests`，`0 failures`，`0 errors`
4. `MatRequisitionWorkflowSubmitTest`：`1 test`，`0 failures`，`0 errors`

合计：

1. 定向后端测试 `49` 个
2. 覆盖迁移完整性、dashboard 控制器、库存出入库写权限相关控制器、领料提交流程

判定：

1. `V128` / `V129` 双轨迁移及其最小边界通过静态完整性校验
2. `requisition:submit` 的流程提交链路在当前测试环境中可走通
3. 未跑全量 `mvn verify`，因此本次不复用 2026-07-03 的 `1397 tests` 旧数字冒充当前；本轮只认当前定向门禁通过

## 5. 历史阻断项复核

### 5.1 后端测试 blocker

当前复核结果：关闭

依据：

1. 本轮后端定向测试 `49/49` 通过
2. 本轮改动涉及的迁移、dashboard、inventory、requisition submit 已被当前测试覆盖

说明：

1. 2026-07-03 的全量后端验证是历史背景，不作为当前直接证据
2. 当前结论依赖的是本轮定向门禁通过，而不是沿用旧套数

### 5.2 前端门禁 blocker

当前复核结果：关闭

依据：

1. 定向 Vitest `53/53` 通过
2. `pnpm type-check` 通过
3. `pnpm build` 通过

### 5.3 `ENC(` 残留 blocker

当前复核结果：关闭

执行命令：

```powershell
git grep -n "ENC(" -- deploy/.env.example deploy
```

执行结果：

1. 无输出
2. `git grep` 返回空匹配

### 5.4 文档索引旧结论 blocker

当前复核结果：关闭

复核事实：

1. 校准前 `docs/README.md` 仍把 2026-07-03 的第 13 条主线结论标为“当前有效结论入口”
2. 本轮已按当前证据新增第 21 条主线 `P0-2` 当前校准入口，并将 2026-07-03 结论明确回退为历史背景入口

## 6. 核心业务链路复核

### 6.1 运行态可用性

执行结果：

1. `http://localhost:8080/api/actuator/health` 可达，返回 `{"status":"UP"}`
2. `http://localhost:5173` 可达，返回 `200`

说明：

1. 本轮未重启服务，只做只读健康检查
2. 因当前前后端均在线，`P0-2` 有条件做最小运行态链路复核

### 6.2 运行态最小回归

执行命令：

```powershell
pnpm exec playwright test e2e/login.spec.ts e2e/dashboard.spec.ts e2e/inventory.spec.ts e2e/approval.spec.ts --reporter=list
```

执行结果：

1. `15` 个用例中 `12 passed`
2. `1 failed`
3. `2 did not run`

通过项：

1. `login` 两个用例通过
2. `inventory` 五个用例通过，确认库存出入库页可进入、入库/出库表单可见、库存台账页可读
3. `approval` 三个用例通过，确认待办可见、审批详情可见、同意 / 驳回链路可走通
4. `dashboard` 的结构与图表基础可见性用例通过

失败项：

1. `dashboard.spec.ts` 中 `should display visible dashboard panel areas` 失败
2. 失败原因不是页面不可达，也不是权限挡住页面，而是断言仍查找已不存在的旧文案 `成本台账`
3. 当前页面实际可见文案为 `成本列表 / 合同执行 / 资金流水`，失败属于自动化断言口径滞后

跳过项：

1. 两个后续 dashboard 用例因串行执行前置失败未继续运行
2. 其中“项目经理标签切换后内容区数据是否返回”在旧报告中曾被列为非阻塞观察项；本轮未拿旧结论直接顶替，保留为当前剩余风险

判定：

1. `login / inventory / approval` 当前最小核心链路可用
2. `dashboard` 当前不是页面不可进或权限不可用，而是至少存在一条 **自动化断言需要同步 UI 当前文案** 的测试债务
3. 该失败当前归类为 **非阻塞自动化缺口**，不足以推翻 `P0-2` 当前通过结论

## 7. 文档索引校准

本轮判断：

1. 需要更新 `docs/README.md`
2. 原因不是新增大量文档体系，而是原索引仍把 2026-07-03 作为“当前有效上线结论入口”
3. 对于本次 `P0-2`，这是明确需要最小修正的文档治理项

本轮处理：

1. 已新增第 21 条主线 `P0-2` 当前有效校准入口
2. 已将第 13 条 2026-07-03 入口降为历史背景，不再冒充当前判断

## 8. 上线决策包

| 项目 | 当前结论 | 依据 |
| --- | --- | --- |
| 是否通过 | 通过 | 当前工作区相关定向前后端门禁通过，运行态最小核心链路主要通过 |
| 是否阻塞 | 非阻塞 | 当前失败点集中在 dashboard 自动化断言滞后与未跑全量门禁，不构成 `P0-2` 阻塞 |
| 是否可进入 `P1` | 可进入 | `P0-2` 的门禁、历史 blocker、核心链路和文档索引均已形成当前有效口径 |
| 是否等于最终上线放行 | 不等于 | 尚未重跑全量后端 / 前端 / 全链路套件，本报告只完成当前总控校准 |

## 9. 阻塞 / 非阻塞清单

### 9.1 阻塞项

当前无新增阻塞项。

### 9.2 非阻塞项

1. `dashboard.spec.ts` 仍使用旧文案 `成本台账` 作为断言，导致 1 条 E2E 失败、2 条串行用例未继续执行
2. “项目经理标签切换后内容区数据是否稳定返回”本轮未形成新的正向运行态证据，仍属观察项
3. 本轮未跑全量 `mvn verify`、全量 `vitest` 或全量 Playwright；当前只对 `P0-1` / `P0-2` 相关面做定向校准
4. 当前工作区仍有未提交改动，且存在非 `P0-1` 未归属文件 `frontend-admin/src/components.d.ts`

## 10. 风险与建议

### 10.1 剩余风险

1. 若后续把 dashboard E2E 当成最终上线硬门禁，需先同步自动化断言到当前 UI 文案，否则会继续出现假失败
2. 若 `P1` 引入跨模块后端或权限边界扩面改动，本报告不能替代新的阶段验证
3. 当前未提交改动未完成合并边界收敛，提交批次仍需后续单独管控

### 10.2 建议

1. 按“通过 / 非阻塞 / 可进入 `P1`”推进下一阶段
2. 将 dashboard 失败归档为自动化用例口径同步事项，不将其误判成当前业务阻塞
3. 进入最终上线前，如存在新的跨模块改动，应重新补一次当周总控校准，不沿用本报告直接放行

## 11. 运行命令与结果摘要

```powershell
pnpm vitest run src/router/__tests__/router.test.ts src/pages/dashboard/__tests__/DashboardPermissionScope.test.ts src/pages/dashboard/__tests__/DashboardDataLoading.test.ts src/pages/inventory/__tests__/transaction.test.ts
# 结果：4 files / 53 tests passed

pnpm type-check
# 结果：通过

pnpm build
# 结果：通过，4415 modules transformed

.\mvnw.cmd -q "-Dtest=MigrationIntegrityTest,com.cgcpms.dashboard.controller.DashboardControllerTest,com.cgcpms.inventory.MatStockControllerTest,com.cgcpms.requisition.service.MatRequisitionWorkflowSubmitTest" test
# 结果：49 tests passed（17 + 26 + 5 + 1）

git grep -n "ENC(" -- deploy/.env.example deploy
# 结果：无输出

Invoke-WebRequest http://localhost:8080/api/actuator/health
# 结果：{"status":"UP"}

Invoke-WebRequest http://localhost:5173
# 结果：200

pnpm exec playwright test e2e/login.spec.ts e2e/dashboard.spec.ts e2e/inventory.spec.ts e2e/approval.spec.ts --reporter=list
# 结果：12 passed / 1 failed / 2 did not run
```
