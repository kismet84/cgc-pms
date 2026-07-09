# 第32条主线 M3 真实角色抽样复核报告

报告日期：2026-07-09
报告类型：真实角色抽样复核 / 质量归档
报告边界：仅基于本地 dev/test 运行态、真实业务角色账号登录、浏览器页面抽样、只读数据库账号核对和相关源码/文档证据形成裁决；本报告不修改业务代码、配置、脚本、运行环境或 Git 状态。

## 1. 结论

通过/不通过：不通过。
阻塞/非阻塞：前置阻塞，阻塞项为 1。
是否满足 M3 验收标准：不满足。

裁决依据：

1. 采购经理真实账号 `demo_alert_purchase / admin123` 可登录，`/auth/userinfo` 返回 `PURCHASE_MANAGER`，浏览器可进入 `/dashboard`，仅展示“采购经理”驾驶舱标签。
2. 生产经理真实账号 `demo_alert_production / admin123` 可登录，`/auth/userinfo` 返回 `PRODUCTION_MANAGER`，浏览器可进入 `/dashboard`，仅展示“生产经理”驾驶舱标签。
3. 本地 dev 数据库只读核对未发现任何绑定 `FINANCE` 角色的非超管用户；仓库种子迁移仅定义 `FINANCE` 角色及菜单授权，未提供同类可登录财务真实角色账号。
4. 因缺少真实财务账号，本轮不能用超管 `dev-login` 或其他非财务账号冒充财务角色体验。

## 2. Health Gate

| 检查项 | 结果 | 结论 |
| --- | --- | --- |
| `http://localhost:8080/api/actuator/health` | HTTP 200，返回 `{"status":"UP"}` | 通过 |
| `http://localhost:5173/` | HTTP 200，标题为“建筑工程总包项目管理系统” | 通过 |
| `http://localhost:5173/api/auth/dev-login?redirect=/dashboard` | HTTP 302 | 通过 |

runtime refresh：未执行。
原因：health gate 全部可达，问题不属于 Docker/backend/frontend 未就绪或 Vite 代理漂移。

## 3. 账号来源与只读核对

| 角色 | 账号来源 | live 账号核对 | 结论 |
| --- | --- | --- | --- |
| 采购经理 | `V120__seed_alert_center_demo_accounts.sql`，用户 `demo_alert_purchase` 绑定 role_id `7`；`V97__seed_phase2_dashboard_roles.sql` 定义 `PURCHASE_MANAGER` | `sys_user/sys_user_role/sys_role` 查询到 `demo_alert_purchase`，`is_admin=0`，角色为 `PURCHASE_MANAGER` | 可验收 |
| 生产经理 | `V120__seed_alert_center_demo_accounts.sql`，用户 `demo_alert_production` 绑定 role_id `8`；`V97__seed_phase2_dashboard_roles.sql` 定义 `PRODUCTION_MANAGER` | `sys_user/sys_user_role/sys_role` 查询到 `demo_alert_production`，`is_admin=0`，角色为 `PRODUCTION_MANAGER` | 可验收 |
| 财务 | `V42__seed_material_warehouse_cost_subject.sql` 定义 `FINANCE` 角色；`V53__fix_seed_role_permissions.sql` 补财务菜单授权 | live 数据库未查询到任何绑定 `FINANCE` 的非超管用户 | 前置阻塞 |

只读查询摘要：

```text
demo_alert_purchase    PURCHASE_MANAGER
demo_alert_production  PRODUCTION_MANAGER
demo_alert_purchase_production  PRODUCTION_MANAGER,PURCHASE_MANAGER
demo_alert_purchase_commercial  COMMERCIAL_MANAGER,PURCHASE_MANAGER
未发现 FINANCE 绑定用户
```

## 4. 抽样结果

### 4.1 采购经理

账号：`demo_alert_purchase / admin123`
用户信息：`userId=980000000000000021`，`roles=PURCHASE_MANAGER`，权限数量 `4`。
页面：`http://localhost:5173/dashboard`。
API：`/api/dashboard/purchase-manager`、`/api/alerts?pageNum=1&pageSize=10`。

证据摘要：

1. 登录 API 返回 HTTP 200，`/auth/userinfo` 返回 `PURCHASE_MANAGER`。
2. 浏览器进入 `/dashboard` 后页面显示“预警采购经理演示账号 / PURCHASE_MANAGER”。
3. 驾驶舱标签仅显示“采购经理”。
4. 页面显示采购语义内容：待审批采购、执行中订单、逾期交货、库存预警、采购执行总览、待验收入库。
5. `/api/dashboard/purchase-manager` 返回 HTTP 200，数据包含 `overdueOrders=5`、`pendingReceipts=5` 等采购抽样数据。
6. 浏览器控制台未记录相关 error/warn。

结论：通过。

### 4.2 生产经理

账号：`demo_alert_production / admin123`
用户信息：`userId=980000000000000022`，`roles=PRODUCTION_MANAGER`，权限数量 `4`。
页面：`http://localhost:5173/dashboard`。
API：`/api/dashboard/production-manager`、`/api/alerts?pageNum=1&pageSize=10`。

证据摘要：

1. 登录 API 返回 HTTP 200，`/auth/userinfo` 返回 `PRODUCTION_MANAGER`。
2. 浏览器进入 `/dashboard` 后页面显示“预警生产经理演示账号 / PRODUCTION_MANAGER”。
3. 驾驶舱标签仅显示“生产经理”。
4. 页面显示生产语义内容：验收记录、领料申请、待出库、分包计量、现场执行协同、近期验收、近期领料。
5. `/api/dashboard/production-manager` 返回 HTTP 200，数据包含 `recentReceipts=5` 等生产抽样数据。
6. 浏览器控制台未记录相关 error/warn。

结论：通过。

### 4.3 财务

账号：未发现可用真实财务账号。
页面：未执行真实财务账号浏览器验收。
API：未执行真实财务账号 API 验收。

阻塞依据：

1. `FINANCE` 角色存在，且已有 `dashboard:finance:view` 等菜单授权种子。
2. live dev 数据库没有绑定 `FINANCE` 的非超管用户。
3. 第32条主线 M3 明确禁止用超管 `dev-login` 结果冒充真实角色体验。

结论：前置阻塞。

## 5. 工具与异常分类

1. Browser 插件可连接，但 `domSnapshot()` 报 `incrementalAriaSnapshot is not a function`；已改用 Browser 的表单填充、页面跳转、只读 `evaluate` 和 console log 读取完成页面验收。
2. 中途连续登录触发一次本地登录接口 `RATE_LIMIT_EXCEEDED`，属于工具调用节奏问题；最终采购、生产表单登录与页面进入均完成，不计为业务失败。
3. 本轮未生成、入库或保留截图、trace、临时日志。

## 6. 剩余风险与后续入口

阻塞项：

1. 缺少真实财务角色账号。需要新增或确认一个 `FINANCE` 非超管账号，并完成 `/dashboard` 财务标签、`/api/dashboard/finance`、付款/发票/结算相关入口抽样。

非阻塞观察项：

1. 采购/生产账号的 `alert` API 返回 200 但当前样本为 `total=0`，不影响驾驶舱角色抽样结论；若后续要验收预警域角色过滤，需要单独准备对应预警样本。
2. 生产经理页面中部分领料/计量行责任人仍显示“开发演示超级管理员”或 `-`，本轮只裁决角色入口与驾驶舱语义可达；是否需要补齐演示数据责任人，建议另列数据质量观察项。

最终裁决：M3 不通过 / 前置阻塞 / 需要补真实财务账号后复验。
