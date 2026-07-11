# 主线17：变更签证生产化增强质量报告

## 最终结论

- 结论：通过
- 阻塞：否
- 可上线：是

## 变更范围

- `frontend-admin/src/pages/variation/order.vue`
- `frontend-admin/src/pages/variation/__tests__/VariationOrderProduction.test.ts`
- 未改后端、未改 API/types。

## 关键变更摘要

- `APPROVING` 状态前端显示为`审批中`，替换 `PENDING` 口径。
- `handleProjectChange` 清空 `contractId` 与 `partnerId`。
- 工具栏提示改为“当前页结果 / 金额单位万元 / 编号可查看详情”。
- 移动端新增 `vo-mobile-list` / `vo-mobile-card`，并补齐 `loading` / `empty` / `data` 三态。
- `ColumnSettingsButton` 在移动端隐藏。
- 新增源码守卫测试 6 个用例。

## 验收证据

- Vitest：
  - `pnpm exec vitest run src/pages/variation/__tests__/VariationOrderProduction.test.ts`
  - 结果：`1` 个文件 / `6` 个用例通过。
- type-check：
  - 结果：通过。
- build：
  - 结果：通过，`vite build` 完成约 `20.60s`。
- 浏览器桌面：
  - `1920x1080` 视口下，查询栏、筛选、重置、列设置、新建弹窗、查看弹窗、行操作可用。
- 浏览器移动：
  - `375x812` 视口下，`.vo-mobile-list=1`、`.vo-mobile-card=1`，列设置隐藏，无横向溢出。
- API：
  - `/api/var-orders` 抓到 `4` 次 `GET`，均为 `pageNo=1&pageSize=20`，未出现 `pageNum`，状态 `200`。
- 控制台与失败请求：
  - 控制台红错=`0`
  - 失败 API=`0`
- 运行态刷新：
  - 刷新前：`PENDING=true` / `APPROVING=false` / `vo-mobile-list=false`
  - 刷新后：`PENDING=false` / `APPROVING=true` / `vo-mobile-list=true`
  - 页面 `200`
- 截图 / 证据路径：
  - `D:\projects-test\cgc-pms\output\m17-4-variation-order-acceptance\evidence.json`
  - `D:\projects-test\cgc-pms\output\m17-4-variation-order-acceptance\row-action-evidence.json`
  - `D:\projects-test\cgc-pms\output\m17-4-variation-order-acceptance\desktop-final.png`
  - `D:\projects-test\cgc-pms\output\m17-4-variation-order-acceptance\mobile-final.png`

## 阻塞项

- 无

## 非阻塞项

- 未做 `quantity=2 / unitPrice=5000 / amount=10000` 的真实写入回归，因为会产生持久测试数据；当前以现有 `reportedAmount=30000.00` 显示 `3.00 万 / 万元`，结合源码 `fmtWan` 与测试守卫覆盖作为替代证据。
- 动态数据无 `APPROVING` 样本；当前通过运行模块、源码和测试证明 `APPROVING` 映射，未强造不可清理数据。
- 当前测试仍是源码守卫，不是组件行为测试；本轮按非阻塞处理。

## 模型分档复盘

- `M17-2` 使用 `gpt-5.4 / medium`：足够，但任务说明遗漏移动端 `loading` 和列设置，后通过代码质量复核补齐。
- `M17-4` 使用 `gpt-5.5 / medium`：必要，发现运行态陈旧并补齐上线裁决证据。
- `M17-Ops` 使用 `gpt-5.4 / low`：足够，完成前端刷新。

## 运行残留

- Playwright 浏览器已关闭。
- `output` 证据按验收要求保留。
- 前端 dev 服务为项目运行态保留。
