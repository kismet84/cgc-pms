# 第26条主线 M6.3/M6.4 内置浏览器复核报告

报告日期：2026-07-08  
报告类型：浏览器复核 / 高风险操作补测  
执行角色：M6.3/M6.4 子智能体  
入口：`http://localhost:5173/api/auth/dev-login?redirect=/dashboard`  
浏览器：Codex 内置浏览器  
截图证据目录：`C:\Users\L1597\AppData\Local\Temp\cgc-pms-mainline-26-m6-3-4-browser-2026-07-08`

## 1. 总体结论

- 总体通过/不通过：不通过。
- 阻塞/非阻塞：阻塞。
- M6.3 结论：通过，B4-B7 已关闭。
- M6.4 结论：不通过，B8 未关闭。
- 主要依据：导出、空提交反馈、成本页控制台告警均已复核通过；上传和项目有效提交已有可回滚通过证据；库存有效提交返回 403；最终审批缺少可回滚/隔离测试实例，且专用领料申请创建返回 403，因此不消费既有业务待办。

运行态说明：

1. 初始访问 `localhost:5173` 连接拒绝，`5173/8080` 端口不可达。
2. 使用现有低影响运行态脚本 `python scripts/rebuild.py backend` 尝试刷新，宿主机 JAR 构建完成，但 Docker API 初始不可用。
3. 仅启动 Docker Desktop 并等待 180 秒；随后 `cgc-pms-backend-dev`、`cgc-pms-frontend-dev`、MySQL、Redis、MinIO 均 healthy，`http://localhost:5173` 返回 200，`/api/actuator/health` 返回 `UP`。

## 2. B4-B8 状态表

| 编号 | 复核项 | 结论 | 是否关闭 | 关键证据 |
| --- | --- | --- | --- | --- |
| B4 | `/dashboard` 导出 | 通过 | 已关闭 | 点击唯一“导出”按钮后，CDP 捕获 `Page.downloadWillBegin`，文件名 `成本列表.csv`；控制台无 warn/error |
| B5 | `/alert` 导出 | 通过 | 已关闭 | 首次页面空白后刷新成功；点击唯一“导出”按钮后，CDP 捕获 `Page.downloadWillBegin`，文件名 `alerts-2026-07-08.csv`；控制台无 warn/error |
| B6 | `/inventory/transaction` 空提交/重复点击反馈 | 通过 | 已关闭 | 连续两次空点“确认入库”，页面和 toast 均出现“请选择仓库”；未发起库存提交 API；控制台无 warn/error |
| B7 | `/cost/ledger` `projectList` warn | 通过 | 已关闭 | 页面加载后控制台 warn/error 计数为 0，未出现 `projectList` / `Invalid prop` / `Expected Array, got Null` |
| B8 | 上传、最终审批、有效业务提交 | 不通过 | 未关闭 | 上传和项目创建删除通过；库存有效提交 403；最终审批无可回滚/隔离实例，专用领料申请创建 403 |

## 3. 页面与操作明细

| 页面/操作 | 访问路径 | 操作步骤 | 截图 | 控制台错误 | 网络/API 证据 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| 首页导出 | `/dashboard` | dev-login 进入首页，点击“导出” | `02-dashboard-export.png` | 无 warn/error | `Page.downloadWillBegin`，`suggestedFilename=成本列表.csv` | 通过 |
| 预警导出 | `/alert` | 首次进入未取到正文，刷新后点击“导出” | `05-alert-reload.png`、`06-alert-export.png` | 无 warn/error | `Page.downloadWillBegin`，`suggestedFilename=alerts-2026-07-08.csv` | 通过 |
| 出入库空提交 | `/inventory/transaction` | 空表单连续点击两次“确认入库” | `07-inventory-transaction-entry.png`、`08-inventory-empty-repeat.png` | 无 warn/error | 无 `/inventory/stock/in` 请求；页面/toast 出现“请选择仓库” | 通过 |
| 成本台账 | `/cost/ledger` | 打开页面等待加载，读取控制台 warn/error | `09-cost-ledger.png` | warn/error 计数 0 | 页面列表与分析区正常渲染 | 通过 |
| 上传补测 | CDP 页面上下文调用 `/api/files/upload` | 选择 E2E 项目 `2073802658242220033`，上传 `m6-b8-upload-test.txt`，随后删除文件并复查列表 | `10-b8-upload-api-rollback.png` | 无新增 warn/error | 上传 200，文件 ID `2074647358548131842`；删除 200；删除后 `/api/files?businessType=PROJECT&businessId=2073802658242220033` 返回空数组 | 通过 |
| 库存有效提交 | `/inventory/transaction` | 选择 E2E 仓库与物料，输入 `0.01`，点击“确认入库” | `12-inventory-transaction-before-valid-ui.png`、`13-b8-valid-stock-in-ui.png` | 无 warn/error | UI 发起 `/api/inventory/stock/in`，返回 403；toast 为 `Request failed with status code 403` 与“入库失败，请稍后重试” | 不通过 |
| 项目有效提交 | CDP 页面上下文调用 `/api/projects` | 创建临时项目 `M6-B8有效提交测试-1783469588223`，读取详情，随后物理删除，再读取确认不存在 | `14-b8-valid-project-create-delete.png` | 无新增 warn/error | 创建 200，项目 ID `2074647939719282690`；详情 200；删除 200；删除后详情返回 `PROJECT_NOT_FOUND` | 通过 |
| 最终审批 | `/approval/todo` 与 `/api/workflow/tasks/todo` | 查询 5 条待办并逐条读取实例节点；尝试创建专用领料申请作为隔离审批实例 | `15-b8-approval-todo-list.png` | 无新增 warn/error | 既有待办均非可回滚专用实例；专用 `/api/requisitions` 创建返回 403，当前账号缺 `requisition:add` | 不通过 |

## 4. B8 数据隔离与回滚记录

上传补测：

- 测试对象：E2E 项目 `2073802658242220033`，名称 `E2E库存回读项目-INVLEDGER-1783268057750-uz4jfq`。
- 上传文件：`m6-b8-upload-test.txt`。
- 上传结果：`/api/files/upload?businessType=PROJECT&businessId=2073802658242220033` 返回 200，文件 ID `2074647358548131842`。
- 回滚方式：调用 `DELETE /api/files/2074647358548131842`。
- 回滚结果：删除返回 200；随后文件列表返回 `[]`。

有效业务提交补测：

- 通过链路：创建临时项目 `M6-B8有效提交测试-1783469588223`，项目 ID `2074647939719282690`，随后删除。
- 回滚方式：`DELETE /api/projects/2074647939719282690`。
- 回滚结果：删除返回 200；再次查询返回 `PROJECT_NOT_FOUND`。
- 不通过链路：库存出入库页面显示“权限：可提交”，但有效入库提交 `/api/inventory/stock/in` 返回 403；未写入库存，库存未被污染。

最终审批补测：

- 现有待办：5 条，包含 `REQ-20260705-010/011/012` 和分包计量演示实例。
- 风险判断：现有待办不是本轮创建的专用可回滚数据；每个候选实例当前节点至少还有另一名审批人的 pending 任务，不能由当前账号一次形成最终审批。
- 专用数据尝试：尝试创建专用领料申请作为隔离审批实例，`POST /api/requisitions` 返回 403；当前账号权限清单有 `requisition:submit/query`，无 `requisition:add`。
- 结论：最终审批补测不通过，不消费既有业务待办，不写“未覆盖”。

## 5. 剩余阻塞与风险

阻塞项：

1. B8 未关闭：库存有效提交实际 403，且最终审批缺少可回滚/隔离测试数据。
2. `/inventory/transaction` 存在权限显示与实际接口不一致：页面显示“权限：可提交”，但有效入库请求返回 403。
3. 当前 dev-login 账号不能创建专用领料申请，导致无法闭环“创建专用审批实例 -> 提交 -> 最终审批 -> 隔离归档”。

非阻塞说明：

- B4-B7 已关闭。
- 上传补测和项目有效提交补测已有回滚证据。
- 本次未使用外部浏览器，未修改业务代码、配置、测试源码，未提交 Git，未清理文件，未读取或扫描禁止目录。

## 6. 禁止事项遵守情况

- 未读取或扫描 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`。
- 未修改业务源码、配置、测试源码。
- 未提交 Git，未执行 Git 清理。
- 未使用外部浏览器。
- 写入文件仅限本报告。
- 运行态动作仅限为完成内置浏览器验收而启动 Docker Desktop、等待服务健康；已在报告中记录。
