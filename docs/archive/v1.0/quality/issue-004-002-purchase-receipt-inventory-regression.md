# ISSUE-004-002 采购收货库存数量一致性回归

日期：2026-07-08

## 结论

通过。未发现需要修改生产代码的数量一致性缺陷；本轮补齐了采购订单明细已收数量、收货明细替换边界、库存流水来源追溯的最小回归测试。

## 数量链路

- 采购订单明细：`MatPurchaseOrderService.saveItemsBatch` 保存采购订单明细，`MatPurchaseOrderItem.receivedQuantity` 初始为 0。
- 收货明细：`MatReceiptService.saveItemsBatch` 保存收货明细时，先扣回旧收货明细 `actualQuantity`，再按新明细 `actualQuantity` 增加采购订单明细 `receivedQuantity`，避免重复累计。
- 库存流水：收货审批通过后由 `MaterialReceiptWorkflowHandler` 按收货明细 `qualifiedQuantity` 调用 `MatStockService.stockIn(..., "MAT_RECEIPT", receiptId)`，库存流水保留 `sourceType/sourceId`。

## 本轮改动

- `backend/src/test/java/com/cgcpms/receipt/MatReceiptServiceTest.java`：新增收货明细保存回归，覆盖首次收货、替换收货明细、清空明细后采购已收数量归零。
- `backend/src/test/java/com/cgcpms/inventory/MatStockServiceTest.java`：新增收货来源入库流水回归，断言 `MAT_RECEIPT` 来源与 `receiptId` 被保留。
- `backend/src/test/java/com/cgcpms/purchase/MatPurchaseOrderServiceTest.java`：补齐采购审批测试的本地审批人前置。原因是历史 migration 删除了 `sys_user.id=1`，而采购审批模板仍引用该用户；该修复只影响测试前置，不改 workflow 或 migration。

## 验证证据

- 原验证命令中的 `InventoryServiceTest` 在仓库中不存在；CodeGraph 和文件检查确认真实库存服务测试类为 `MatStockServiceTest`。
- 等价最小命令：`cd backend; .\mvnw.cmd "-Dtest=MatPurchaseOrderServiceTest,MatReceiptServiceTest,MatStockServiceTest" test`
- 结果：通过，`Tests run: 59, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

## 未覆盖范围

- 未扩展到付款、发票、审批状态机整改。
- 未修改数据库 migration。
- 未跑全量后端测试，本结论限于 ISSUE-004-002 的采购、收货、库存数量链路回归范围。
