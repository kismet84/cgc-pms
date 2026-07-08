# 第26条主线 M6.4-B8 运行态刷新与内置浏览器最终复核报告

报告日期：2026-07-08  
报告类型：运行态刷新 / 内置浏览器最终复核 / B8 高风险操作补测  
执行角色：M6.4-B8 子智能体  
入口：`http://localhost:5173/api/auth/dev-login?redirect=/dashboard`  
浏览器：Codex 内置浏览器  
截图证据目录：`C:\Users\L1597\AppData\Local\Temp\cgc-pms-mainline-26-m6-4-b8-final-2026-07-08`

## 1. 总体结论

- B8 结论：通过。
- 阻塞/非阻塞：非阻塞。
- 依据：刷新后端后，V133 已应用，`SUPER_ADMIN` 已绑定 `inventory:transaction:add`、`requisition:add`、`requisition:submit`、`workflow:approve`；内置浏览器 dev-login 后 `userinfo` 同步包含 `SUPER_ADMIN` 与上述权限；有效库存提交不再 403；本轮专用领料审批实例已创建、提交并完成最终审批。
- 数据污染控制：库存有效提交与审批自动出库均已用同量反向流水回补，最终库存可用量回到 `42.0000`；审批实例无法物理删除，按本轮专用 ID、唯一前缀明细与审批意见隔离。

## 2. 运行态刷新证据

| 项目 | 结果 |
| --- | --- |
| 后端刷新命令 | `python scripts/rebuild.py backend` |
| 构建结果 | Maven `clean package -DskipTests -q` 成功 |
| 容器重启 | `docker compose -f docker-compose.dev.yml restart backend` 成功 |
| 等待 | 后端重启后等待约 190 秒 |
| 容器状态 | `cgc-pms-backend-dev` 为 `Up 3 minutes (healthy)` |
| 健康检查 | `http://localhost:8080/api/actuator/health` 返回 200 |
| JAR 内容 | `SecurityConfig.class`、`V133__register_requisition_add_permission.sql` 均在 `backend/target/cgc-pms-backend.jar` 中 |
| Flyway | `flyway_schema_history.version=133`，`success=1`，`installed_on=2026-07-08 09:00:16` |

## 3. 权限与登录证据

数据库权限：

- `SUPER_ADMIN -> inventory:transaction:add`
- `SUPER_ADMIN -> requisition:add`
- `SUPER_ADMIN -> requisition:submit`
- `SUPER_ADMIN -> workflow:approve`

内置浏览器 `userinfo`：

- `demo_dev_super_admin`：`roles=["SUPER_ADMIN"]`，包含 `inventory:transaction:add`、`requisition:add`、`requisition:submit`、`workflow:approve`。
- `admin`：`roles=["SUPER_ADMIN"]`，包含同样权限，用于审批本轮专用实例中归属 `approver_id=1` 的剩余待办。

说明：首次直接 POST 未带 `X-XSRF-TOKEN` 时，后端日志显示 `Invalid CSRF token` 并返回 403；带浏览器 cookie 中的 `XSRF-TOKEN` 作为 `X-XSRF-TOKEN` 后，同一接口返回 200。因此本轮有效结论以带 CSRF 的真实浏览器会话请求为准。

## 4. B8 操作结果

### 4.1 库存有效提交

| 项目 | 结果 |
| --- | --- |
| 页面 | `/inventory/transaction` |
| 仓库 | `2071033500000000001`，成本驾驶舱演示项目材料仓 |
| 物料 | `970000000000005001`，塔吊基础预埋件组合包 |
| 入库请求 | `POST /api/inventory/stock/in` |
| 请求体 | `warehouseId=2071033500000000001, materialId=970000000000005001, quantity=0.01, sourceType=M6-B8-1783472755924-retry` |
| 入库结果 | HTTP 200，`code=0`，`availableQty=42.0100` |
| 回滚方式 | `POST /api/inventory/stock/out` 同仓库、同物料、同数量、同 `sourceType/sourceId` |
| 回滚结果 | HTTP 200，`code=0`，`availableQty=42.0000` |

结论：库存有效提交不再 403，通过。

### 4.2 专用领料/审批实例

| 项目 | 结果 |
| --- | --- |
| 唯一前缀 | `M6-B8-1783472755924` |
| 领料单 ID | `2074661504812953602` |
| 领料编号 | `REQ-20260708-002` |
| 明细隔离字段 | `useLocation=M6-B8-1783472755924`，`batchNo=M6-B8-1783472755924` |
| 工作流实例 ID | `2074661549155135489` |
| 创建 | `POST /api/requisitions` 返回 200，`code=0` |
| 明细保存 | `POST /api/requisitions/2074661504812953602/items/batch` 返回 200，`code=0` |
| 提交审批 | `POST /api/requisitions/2074661504812953602/submit` 返回 200，`code=0` |
| 当前用户审批 | task `2074661549406793729` 返回 200，状态 `APPROVED` |
| admin 最终审批 | task `2074661549381627905` 返回 200，状态 `APPROVED` |
| 最终实例状态 | `wf_instance.instance_status=APPROVED` |
| 最终业务状态 | `mat_requisition.approval_status=APPROVED`，`stock_out_flag=1` |

说明：审批流生成两个并行待办，分别归属 `2073052562872508418` 和 `1`。本轮只处理上述专用实例的两个 task，没有消费既有业务待办。

### 4.3 审批自动出库回补

最终审批后业务自动生成一笔出库：

- `txnType=OUT`
- `quantity=0.0100`
- `sourceType=MAT_REQUISITION`
- `sourceId=2074661504812953602`
- `availableAfter=41.9900`

为恢复库存净额，随后执行回补入库：

- `POST /api/inventory/stock/in`
- `sourceType=M6-B8-1783472755924-approval-stock-rollback`
- `quantity=0.01`
- 返回 200，`availableQty=42.0000`

最终只读核对：`mat_stock.available_qty=42.0000`。

## 5. 截图、控制台与网络/API 证据

截图：

- `01-inventory-transaction-after-b8.png`
- `02-approval-todo-after-b8.png`

控制台：

- dev-login、库存页、审批页最终 `warn/error` 为空。

网络/API 摘要：

- `/api/auth/userinfo`：200，`roles=["SUPER_ADMIN"]`，包含本轮要求权限。
- `/api/inventory/stock/in`：带 CSRF 后 200，库存有效提交成功。
- `/api/inventory/stock/out`：200，库存入库反冲成功。
- `/api/requisitions`：200，专用领料主表创建成功。
- `/api/requisitions/{id}/items/batch`：200，专用明细保存成功。
- `/api/requisitions/{id}/submit`：200，专用实例提交成功。
- `/api/workflow/tasks/{taskId}/approve`：两个专用 task 均 200。
- `/api/workflow/instances/2074661549155135489`：200，`instanceStatus=APPROVED`。
- `/api/requisitions/2074661504812953602`：200，`approvalStatus=APPROVED`。

## 6. 剩余风险

- 专用审批实例无法物理删除，作为已审批业务记录保留；已通过唯一明细前缀、审批意见前缀、实例 ID 和业务 ID 隔离。
- 领料编号由系统自动生成，不能直接带 `M6-B8` 前缀；隔离标识落在明细 `useLocation/batchNo`、审批 comment、库存流水 `sourceType` 与报告记录中。
- 本轮未重启前端；前端在刷新前已可访问，且本任务聚焦 B8，未重新回归 B4-B7。

## 7. 禁止事项遵守情况

- 未读取或扫描 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`。
- 未修改源码、配置、测试源码。
- 未提交 Git，未清理文件，未变更 Git 状态。
- 未使用外部浏览器。
- 前端已运行且可访问，未重启前端。
- 后端运行态动作仅限授权范围内的 `scripts/rebuild.py backend`。
- 写入文件仅限本验收报告。

