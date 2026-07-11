# 第26条主线 M4 内置浏览器自动化测试验收报告

## 一、总体结论

- 总体通过/不通过：不通过。
- 阻塞/非阻塞：阻塞第26条主线 M4 浏览器自动化测试验收收口；非运行态阻塞，前端入口可达。
- 入口：`http://localhost:5173/api/auth/dev-login?redirect=/dashboard`，实际落地 `http://localhost:5173/dashboard`。
- 浏览器：Codex 内置浏览器。
- 截图证据目录：`C:\Users\L1597\AppData\Local\Temp\cgc-pms-mainline-26-m4-browser-2026-07-08`
- 禁止事项遵守情况：未读取或扫描 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`；未修改业务代码、配置、测试源码、运行环境或 Git 状态；未提交 Git；未清理文件；使用的是内置浏览器。

不通过依据：

1. 首页与预警中心“导出”按钮可点击，但 3 秒内未捕获下载事件，也未捕获导出 API 请求。
2. 出入库页面空表单连续点击“确认入库”无 API 请求、无可见校验提示，重复提交拦截/错误提示证据不足。
3. 成本管理页存在 Vue 控制台警告：`Invalid prop: type check failed for prop "projectList". Expected Array, got Null`。

## 二、页面覆盖结果

| 页面 | 访问路径 | 操作步骤 | 截图证据 | 控制台错误 | 网络/API 错误 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| 首页驾驶舱 | `/dashboard` | dev-login 后进入首页，检查标题、DOM、首屏、仪表盘数据和导出按钮 | `...\dashboard.png` | 未捕获 error/warn | `/api/auth/userinfo`、`/api/dashboard/cost-manager` 为 200 | 页面加载通过；导出操作不通过 |
| 预警中心 | `/alert` | 打开页面，检查待处理/高危/列表/筛选/批量按钮/导出按钮 | `...\alert.png` | 未捕获 error/warn | `/api/alerts/subscription`、`/api/alerts` 为 200 | 页面加载通过；导出操作不通过 |
| 审批中心 | `/approval/todo` | 打开我的待办，检查待办列表，打开第一条 `REQ-20260705-012` 详情弹窗 | `...\approval-todo.png`、`...\op-approval-detail-open.png` | 未捕获 error/warn | `/api/workflow/tasks/todo`、`/api/workflow/instances/2073768803357884417` 为 200 | 页面和审批入口通过；未执行最终同意/驳回 |
| 项目台账 | `/project/list` | 打开项目列表，点击“新建项目”，空表单点击“创建” | `...\project-list.png`、`...\op-project-create-empty-create.png` | 未捕获 error/warn | `/api/projects?pageNo=1&pageSize=20` 为 200；空表单未发起创建请求 | 通过，显示“请输入项目名称”“请选择项目类型” |
| 成本管理 | `/cost/ledger` | 打开成本列表，复核成本记录、成本分析、刷新/查询入口 | `...\cost-ledger-recheck.png` | 有 Vue warn：`projectList` 期望 Array 实际 Null | `/api/cost-ledger`、`/api/cost-ledger/summary` 为 200 | 页面可用但控制台警告需修复，判非通过 |
| 采购订单 | `/purchase/order` | 打开采购订单，检查列表、筛选、新建订单入口 | `...\purchase-order-recheck.png` | 未捕获当前页新增 error | `/api/purchase-orders` 为 200 | 页面加载通过 |
| 材料验收 | `/purchase/receipt` | 打开材料验收，检查统计卡、列表、新建验收入口 | `...\purchase-receipt.png` | 未捕获当前页新增 error | 页面接口未捕获 4xx/5xx | 页面加载通过 |
| 仓库管理 | `/inventory/warehouse` | 打开仓库管理，检查仓库统计、列表、新建仓库入口 | `...\inventory-warehouse.png` | 未捕获当前页新增 error | 页面接口未捕获 4xx/5xx | 页面加载通过 |
| 库存台账 | `/inventory/stock` | 打开库存台账，检查统计、流水列表、低库存分析 | `...\inventory-stock.png` | 未捕获当前页新增 error | 页面接口未捕获 4xx/5xx | 页面加载通过；当前流水列表为空，不等同业务通过 |
| 出入库 | `/inventory/transaction` | 打开出入库，连续点击两次空表单“确认入库” | `...\inventory-transaction.png`、`...\op-inventory-transaction-submit-empty-repeat.png` | 未捕获 error/warn | 仅加载 `/api/materials`、`/api/inventory/warehouses` 为 200；点击确认未发起提交 API | 不通过：空提交无可见提示，重复点击无拦截反馈 |
| 采购申请 | `/inventory/purchase-request` | 打开采购申请，检查列表、筛选、新建申请入口 | `...\inventory-purchase-request.png` | 未捕获当前页新增 error | 页面接口未捕获 4xx/5xx | 页面加载通过 |

## 三、高风险操作结果

| 操作 | 页面/路径 | 操作步骤 | 证据 | 控制台/网络/API 证据 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 导出 | `/alert` | 点击“导出”，等待下载事件 | `...\op-alert-export-download-check.png` | 无下载事件；无导出 API；无 4xx/5xx | 不通过 |
| 导出 | `/dashboard` | 点击“导出”，等待下载事件 | `...\op-dashboard-export-download-check.png` | 无下载事件；无导出 API；无 4xx/5xx | 不通过 |
| 提交/重复提交 | `/inventory/transaction` | 未选择仓库/物料，连续点击两次“确认入库” | `...\op-inventory-transaction-submit-empty-repeat.png` | 未发起提交 API；未显示必填校验或错误提示 | 不通过 |
| 关键表单保存 | `/project/list` | 点击“新建项目”，空表单点击“创建” | `...\op-project-create-empty-create.png` | 未发起创建 API；显示必填校验 | 通过 |
| 审批 | `/approval/todo` | 打开第一条待办详情，检查“同意/驳回”入口 | `...\op-approval-detail-open.png` | 详情 API 200；未执行最终同意/驳回 | 入口通过，最终审批未覆盖 |
| 上传 | 覆盖页面内未发现明确“上传/导入”入口 | 未执行上传 | 无 | 未覆盖，不写通过 |

## 四、失败项与风险

1. 导出功能不可验收：按钮点击没有下载事件、没有导出 API、没有提示。影响用户导出报表或预警数据的可用性。
2. 出入库空提交没有提示：用户连续点击“确认入库”时没有可见校验、没有提交请求、没有禁用态反馈。影响表单可理解性，也无法证明重复提交拦截有效。
3. 成本管理页控制台 Vue warn：`projectList` 传参为 Null。当前页面仍能渲染，但说明组件边界存在不稳定输入，建议入账为非阻塞修复项。

## 五、未覆盖项及原因

- 最终审批“同意/驳回”：未执行，原因是会改变当前审批实例状态；本次仅验证详情弹窗和操作入口可达。
- 上传：本次覆盖页面内未发现明确上传/导入入口，也未准备业务上传文件；不宣称通过。
- 有效出入库提交：未执行，原因是需要选择真实仓库/物料并写入库存流水；本次只验证空表单和重复点击拦截。
- 采购订单、材料验收、采购申请的有效保存：未执行，原因是会创建或修改业务数据；本次只验证页面、列表和入口。

## 六、剩余风险

- 当前报告是运行态浏览器验收，不替代后端接口单测、E2E 全链路测试或权限矩阵复核。
- 截图保存在系统临时目录，若后续需要长期归档，应由归档任务按项目规则迁移证据。
- 未执行会改变业务状态的最终提交/审批动作，因此无法证明成功写入链路和重复提交服务端幂等。
