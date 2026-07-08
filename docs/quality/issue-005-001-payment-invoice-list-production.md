# ISSUE-005-001 付款与发票列表页生产化补强

日期：2026-07-08

## 结论

- 实现状态：最小前端补强已形成 WIP，并已安全暂存，未合入当前工作区提交。
- 验收状态：不通过。
- 阻塞类型：环境前置类。
- 阻塞原因：前端可启动，但后端 `http://localhost:8080` 未就绪，浏览器首页只呈现 `Request failed with status code 500`；`dev-login` 路径在内置浏览器中还被 `net::ERR_BLOCKED_BY_CLIENT` 拦截，无法完成付款/发票页面真实登录验收。

## WIP 暂存

- `git stash`：`ISSUE-005-001 blocked frontend WIP pending browser verification`
- 涵盖文件：
  - `frontend-admin/src/pages/payment/index.vue`
  - `frontend-admin/src/pages/invoice/index.vue`
  - `frontend-admin/src/pages/invoice/composables/useInvoiceList.ts`
  - `frontend-admin/src/composables/listPageQuery.ts`
  - `frontend-admin/src/composables/__tests__/listPageQuery.test.ts`
  - `frontend-admin/src/pages/payment/__tests__/list-production.test.ts`
  - `frontend-admin/src/pages/invoice/__tests__/list-production.test.ts`

## 本轮正式交付

- 本报告
- `docs/iterations/iteration-2026-07-08-report.md` 中的 blocked 记录
- backlog 中 ISSUE-005-001 的 blocked 状态更新

## 补强点

- 付款列表：
  - 筛选条件、分页参数写入路由 query，刷新后可回显。
  - 项目筛选清空时恢复合同下拉全量加载。
  - 新增显式错误态、空态和重试入口。
- 发票列表：
  - `useInvoiceList` 支持从路由 query 还原筛选与分页，并在查询/分页后回写 query。
  - 新增显式错误态、空态和重试入口。
- 通用：
  - 新增 `listPageQuery` 小工具，统一 query 读写与空值清理。
  - 新增最小测试，守住 query 持久化和列表态接线。

## 验收矩阵

| 项目 | 结果 | 依据 |
| --- | --- | --- |
| 付款筛选回显 | 代码已实现，运行态未验 | `payment/index.vue` 已接入 `useRoute/useRouter` 与 query 同步 |
| 付款筛选重置 | 代码已实现，运行态未验 | `handleReset` 清空筛选并刷新 query |
| 付款刷新保持分页/筛选 | 代码已实现，运行态未验 | `pageNo/pageSize` 与筛选字段写入 query |
| 付款空态 | 代码已实现，运行态未验 | `LgEmptyState` |
| 付款错误态/重试 | 代码已实现，运行态未验 | `a-result + fetchData` |
| 发票筛选回显 | 代码已实现，运行态未验 | `useInvoiceList` 从 `route.query` 还原 |
| 发票筛选重置 | 代码已实现，运行态未验 | `handleReset` 清空筛选并刷新 query |
| 发票刷新保持分页/筛选 | 代码已实现，运行态未验 | `syncRouteQuery` |
| 发票空态 | 代码已实现，运行态未验 | `LgEmptyState` |
| 发票错误态/重试 | 代码已实现，运行态未验 | `a-result + fetchData` |
| 浏览器真实页面验收 | 阻塞 | 后端未启动，`dev-login` 被浏览器拦截，首页显示 `500` toast |

## 验证命令

- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/payment/__tests__/list-production.test.ts src/pages/invoice/__tests__/list-production.test.ts`：通过，`3` 个文件、`7` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 浏览器 / 运行态证据

- `http://127.0.0.1:5173/`：前端 dev server 已启动并返回 `200`。
- `http://localhost:8080/actuator/health`：连接被拒绝。
- 内置浏览器访问 `http://127.0.0.1:5173/api/auth/dev-login?redirect=/dashboard`：返回 `net::ERR_BLOCKED_BY_CLIENT`。
- 内置浏览器访问 `http://127.0.0.1:5173/`：页面标题为“建筑工程总包项目管理系统”，可见 toast `Request failed with status code 500`，说明前端已起但后端接口不可用。

## 阻塞解除条件

- 本地后端 `8080` 恢复可用。
- 内置浏览器可完成 `dev-login`，或提供等价登录验收路径。
- 恢复上述 stash 中的前端 WIP，并在真实运行态下补做付款页、发票页的筛选回显/重置/刷新保持/空态/错误态/重试入口验收。
