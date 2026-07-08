# ISSUE-005-001 付款与发票列表页生产化补强

日期：2026-07-08

## 结论

- 实现状态：最小前端补强已恢复并合入本轮本地提交范围。
- 验收状态：通过。
- 阻塞类型：已解除，原阻塞为环境前置类。
- 解除证据：后端 `http://localhost:8080/api/actuator/health` 返回 `200`，前端 `http://localhost:5173/` 返回 `200`，`http://localhost:5173/api/auth/dev-login?redirect=/dashboard` 返回 `302 /dashboard`；真实 Chromium 验收已覆盖付款页、发票页筛选回显、重置、刷新保持、空态、错误态和重试入口。

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
- `docs/iterations/iteration-2026-07-08-report.md` 中的通过记录
- backlog 中 ISSUE-005-001 的 Done 状态更新
- 付款与发票列表页前端补强代码及最小测试

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
| 付款筛选回显 | 通过 | Chromium 访问 `/payment/application?payStatus=PENDING&pageNo=1&pageSize=20`，可见“待付款” |
| 付款筛选重置 | 通过 | 点击“重置”后 URL 仅保留分页 query，不再保留筛选项 |
| 付款刷新保持分页/筛选 | 通过 | 刷新前后 URL 均保持 `?payStatus=PENDING&pageNo=1&pageSize=20` |
| 付款空态 | 通过 | 浏览器网络拦截返回空分页，页面显示“暂无符合条件的付款申请”和“清空筛选” |
| 付款错误态/重试 | 通过 | 浏览器网络拦截返回错误，页面显示“付款列表加载失败”和“重试”入口 |
| 发票筛选回显 | 通过 | Chromium 访问 `/invoice?keyword=NO_SUCH_BROWSER_ACCEPTANCE&pageNo=1&pageSize=20`，输入框回显关键字 |
| 发票筛选重置 | 通过 | 点击“重置”后 URL 仅保留分页 query，不再保留筛选项 |
| 发票刷新保持分页/筛选 | 通过 | 刷新前后 URL 均保持 `?keyword=NO_SUCH_BROWSER_ACCEPTANCE&pageNo=1&pageSize=20` |
| 发票空态 | 通过 | 真实后端按不存在发票号查询，页面显示“暂无符合条件的发票记录” |
| 发票错误态/重试 | 通过 | 浏览器网络拦截返回错误，页面显示“发票列表加载失败”和“重试”入口 |
| 浏览器真实页面验收 | 通过 | dev-login 落点 `/dashboard`，付款页落点 `/payment/application`，发票页落点 `/invoice` |

## 验证命令

- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/payment/__tests__/list-production.test.ts src/pages/invoice/__tests__/list-production.test.ts`：通过，`3` 个文件、`7` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 浏览器 / 运行态证据

- `http://localhost:8080/api/actuator/health`：返回 `200`。
- `http://localhost:5173/`：返回 `200`。
- `http://localhost:5173/api/auth/dev-login?redirect=/dashboard`：返回 `302 /dashboard`，Chromium 最终落点 `http://localhost:5173/dashboard`。
- 付款页：`http://localhost:5173/payment/application?payStatus=PENDING&pageNo=1&pageSize=20`，刷新前后 query 保持，重置后清除筛选项；空态、错误态与“重试”入口可见。
- 发票页：`http://localhost:5173/invoice?keyword=NO_SUCH_BROWSER_ACCEPTANCE&pageNo=1&pageSize=20`，刷新前后 query 保持，关键字回显，空态、错误态与“重试”入口可见。

## 剩余风险

- 付款空态与付款/发票错误态通过浏览器网络拦截触发，未改动后端数据；该方式只验证前端 UI 分支和重试入口可达性。
- 本轮未跑全量前端测试，结论限于 ISSUE-005-001 指定补强范围。
