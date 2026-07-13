# 第 15 条主线预警中心生产化增强验收报告（2026-07-03）

## 1. 结论

- 结论：通过
- 阻塞 / 非阻塞：非阻塞
- 当前判断：第 15 条主线 `M1` 最小生产化增强已完成收口

本结论基于 2026-07-03 当天的当前代码、当前数据库、当前运行态和当前浏览器复验证据，不沿用旧“已完成”口径。

## 2. 依据

### 2.1 后端最小规则与字段增强

实际修改文件：

- `backend/src/main/java/com/cgcpms/alert/controller/AlertController.java`
- `backend/src/main/java/com/cgcpms/alert/entity/AlertLog.java`
- `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`
- `backend/src/main/resources/db/migration/V118__add_alert_domain_source_fields.sql`
- `backend/src/main/resources/db/migration/V119__backfill_alert_domain.sql`
- `backend/src/main/resources/db/migration-h2/V118__add_alert_domain_source_fields.sql`
- `backend/src/main/resources/db/migration-h2/V119__backfill_alert_domain.sql`

确认结果：

1. `/alerts` 已改为后端分页接口，支持 `pageNum/pageSize/projectId/ruleType/alertDomain/severity/isRead/triggeredStart/triggeredEnd`。
2. 预警记录已补 `alertDomain/sourceType/sourceId`。
3. 已新增采购交期逾期规则 `PURCHASE_DELIVERY_OVERDUE`。
4. `V119` 已在当前 MySQL 运行环境执行成功。
5. 当前库内历史合同预警已补回 `alertDomain=CONTRACT`。

运行态实证：

- `flyway_schema_history` 中 `118`、`119` 均成功，安装时间分别为 `2026-07-03 16:39:48`、`2026-07-03 17:00:33`
- `alert_log` 最新数据已显示：
  - `PURCHASE_DELIVERY_OVERDUE -> PURCHASE`
  - `CONTRACT_EXPIRING -> CONTRACT`
  - `MATERIAL_EXCEEDS_BUDGET -> COST`

### 2.2 前端页面最小生产化增强

实际修改文件：

- `frontend-admin/src/api/modules/alert.ts`
- `frontend-admin/src/pages/alert/index.vue`
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`
- `frontend-admin/src/router/index.ts`
- `frontend-admin/src/stores/alert.ts`
- `frontend-admin/src/types/alert.ts`

确认结果：

1. 页面已切到后端分页。
2. 已支持规则类型、分类、时间范围、已读状态、严重度等筛选。
3. 已支持“当前页未读标已读”。
4. 已支持业务单据跳转。
5. 路由权限已对齐为 `alert:view`。
6. 默认域开关点击遮挡问题已修复。

### 2.3 后端测试、前端静态验证与页面测试

2026-07-03 当天重新验证结果：

1. 前端 `pnpm type-check`：通过
2. 前端 `pnpm build`：通过
3. 前端 `pnpm vitest run src/pages/alert/__tests__/index.test.ts`：通过，`1 file / 2 tests`
4. 后端 `.\mvnw.cmd -q "-Dtest=AlertEvaluationServiceTest,AlertControllerTest" test`：通过
5. `git diff --check`：无本次修改内容格式错误；仅有行尾风格 warning，无阻塞项

### 2.4 当前运行态与浏览器验收

当前运行态：

1. 后端健康检查通过：`http://localhost:8080/api/actuator/health`
2. 前端首页可达：`http://localhost:5173/`
3. 当前容器状态：
   - `cgc-pms-backend-dev`：`Up 3 minutes (healthy)`（检查时）
   - `cgc-pms-frontend-dev`：`Up 4 minutes`（检查时）

浏览器证据目录：

- `output/playwright/mainline15-closeout-current-20260703091038`
- `output/playwright/mainline15-role-union-20260703091302`
- `output/playwright/mainline15-alert-business-entry-20260703-165155`

关键通过项：

1. 默认域开关当前态可点击：
   - `aria-checked: false -> true`
2. 合同类筛选当前态可用：
   - `alertDomain=CONTRACT`
   - 返回 `total=4`
   - 页面不再是“暂无数据”
3. 采购交期逾期真实样本当前态可见：
   - `PO-DEMO-REAL-OVD-001`
   - `PURCHASE_DELIVERY_OVERDUE`
4. 采购业务跳转已在真实页面验证：
   - 目标 URL：`/purchase/order?businessId=970000000000005301`
5. 角色默认筛选已补当前态验证：
    - 采购经理：默认请求 `alertDomain=PURCHASE`
    - 生产经理：当前按降级口径，不自动带单域筛选
6. 多角色并集已补 UI 层模拟验证：
    - `PURCHASE_MANAGER + PRODUCTION_MANAGER -> alertDomain=PURCHASE`
    - `PURCHASE_MANAGER + COMMERCIAL_MANAGER -> 全量视图`

### 2.5 剩余风险补关复验（2026-07-03 17:33）

本轮针对上一版报告中的 3 个剩余风险重新做了当前态复验，结论如下：

1. 真实账号样本不足：已关闭
2. `onlyDefaultScope` 仍是空语义：已关闭
3. 后续增强项被误写成当前剩余风险：已修正

当前补关依据：

1. `V120__seed_alert_center_demo_accounts.sql` 已在当前 MySQL 执行成功：
   - `flyway_schema_history` 已有 `120`
   - `COMMERCIAL_MANAGER` 已存在
   - `demo_alert_purchase`
   - `demo_alert_production`
   - `demo_alert_commercial`
   - `demo_alert_purchase_production`
   - `demo_alert_purchase_commercial`
   - 角色 `4 / 7 / 8` 已补齐 `765 / 766`
2. 真实浏览器账号复验目录：
   - `output/playwright/mainline15-risk-closeout-2026-07-03T09-33-02`
3. 真实账号首个预警请求与默认域开关状态：
   - 采购经理：开关可用；首个请求 `alertDomain=PURCHASE`
   - 生产经理：开关禁用；首个请求不带 `alertDomain`
   - 商务经理：开关禁用；首个请求不带 `alertDomain`
   - 采购+生产：开关可用；首个请求 `alertDomain=PURCHASE`
   - 采购+商务：开关禁用；首个请求不带 `alertDomain`
4. 采购经理与采购+生产账号在打开“只看默认域”后，真实请求变为：
   - `alertDomain=PURCHASE&onlyDefaultScope=true`
5. 当前前端实现已把“只看默认域”从空开关收敛为真实页面筛选语义：
   - 有默认域角色：允许开启，并强制回到默认域请求
   - 无默认域角色：直接禁用开关，避免制造“看起来可切换、实际无含义”的假交互

### 2.6 角色默认策略与边界复核

复核结论：

1. 当前 `M1` 只把“默认筛选”作为页面默认视图，不冒充权限隔离。
2. 采购经理默认域已正式落地。
3. 生产经理当前按降级口径处理，不假装已经具备人工/机械细标签。
4. 总工程师当前仍按冻结角色处理，不作为 `M1` 已落地默认筛选对象。
5. 商务经理与全量视图角色的并集不再被误判为单域筛选。

## 3. 通过 / 不通过 / 阻塞 / 非阻塞判断

| 项目 | 结论 | 依据 |
| --- | --- | --- |
| 是否通过 | 通过 | 后端分页、字段增强、采购逾期规则、前端筛选、批量已读、业务跳转、测试与浏览器验收均已闭环 |
| 是否阻塞 | 非阻塞 | 当前剩余项属于 `M2/M3` 后续增强，不阻塞 `M1` 收口 |
| 是否可关闭第 15 条主线 `M1` | 可关闭 | 当前证据已覆盖计划书的最小生产化增强要求 |

## 4. 与计划书逐项对照

1. 预警列表已改为后端分页：通过
2. 现有 8 条规则不回退：通过
3. 采购交期逾期预警已按真实数据源落地：通过
4. 页面最小筛选能力已具备：通过
5. 角色默认筛选与多角色并集：通过
6. 用户可手工覆盖默认视图：通过
7. 批量已读可用：通过
8. 可定位预警可跳转业务单据：通过
9. 后端测试通过：通过
10. 前端静态验证通过：通过
11. 真实浏览器验收并保留证据：通过
12. 质量归档报告完成：通过

## 5. 剩余风险

本轮复验后，无新增阻塞性剩余风险。

已关闭事项：

1. 真实非管理员账号样本不足：已由 `V120` 真实账号和真实浏览器登录验收关闭。
2. `onlyDefaultScope` 是空语义：已由真实请求链路和开关禁用策略关闭。
3. 后续增强项被误挂到“剩余风险”：已移出本节。

## 6. 不纳入本次收口

1. 通用规则引擎或规则设计后台
2. 通知订阅中心、消息中心、多渠道触达
3. 强权限隔离模型
4. 未确认数据源的全量逾期规则
5. 更细粒度的生产经理 / 总工程师专属标签体系

说明：

- 上述事项属于预警中心后续增强主线，不是当前 `M1` 的已知缺陷，也不是本次收口遗留风险。
- 本轮 `M1` 只要求完成最小生产化增强，不把规则引擎、通知平台、多渠道触达、强权限隔离提前包装成本轮未完成项。

## 7. 主负责人建议

1. 按“通过 / 非阻塞”关闭第 15 条主线 `M1`。
2. 后续串行推进 `M2` 时，优先补规则分类治理、抑制策略、去重策略和更细标签。
3. 后续进入 `M3` 时，再推进通知平台、多渠道触达、强权限隔离和订阅策略，不要把这些项回灌为本次 `M1` 未完成。
