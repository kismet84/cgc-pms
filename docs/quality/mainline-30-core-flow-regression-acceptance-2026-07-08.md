# 第30主线 成本付款库存审批主链路回归验收报告

报告日期：2026-07-08  
报告类型：主线正式验收 / 质量归档  
报告边界：仅基于第30主线计划书、M1-M4 子阶段回报、后端定向测试结果、浏览器冒烟结果和驾驶舱 API 核验证据做正式裁决；本报告不修改业务代码、测试、配置、计划书或运行环境。

## 1. Goal / Architecture 摘要

**Goal:** 以最小可行方案完成成本、付款、库存、审批主链路回归，验证核心业务链路在后端状态流转、金额口径、前端关键页面、驾驶舱来源下钻四个层面保持一致，为短期 P0 收口提供正式验收依据。

**Architecture:** 复用现有后端测试基座、业务测试数据、Playwright、`/api/auth/dev-login?redirect=/dashboard` 浏览器入口和 `docs/quality` 归档规则；不引入新平台、新框架或大矩阵系统，不重构审批引擎，不扩大权限模型，不并行推进前端列表页生产化、文件上传/发票识别安全增强或报表中心建设。

## 2. 总体裁决

通过/不通过：通过。  
阻塞/非阻塞：非阻塞，阻塞项为 0。  
是否可收口：是，第30主线可进入合并前收口流程。  
是否可进入后续阶段：M1-M4 均已通过，M5 正式验收归档完成后可收口。

裁决依据：

1. M1 已冻结九条主链路矩阵，并明确后端、前端、驾驶舱和验收口径。
2. M2 后端定向测试集合退出码为 0，并提交 `108822ace4a236392ffa049b813eadb4042bee71`，补齐 `MAT_REQUISITION` 领料成本链路。
3. M3 真实运行态与 Chromium 浏览器冒烟通过，关键页面未出现登录回退、白屏、Vue runtime error、关键 5xx/404 或认证失败。
4. M4 驾驶舱与来源下钻 API 通过，成本、采购、生产、财务、审批和预警关键来源字段可解释。
5. 剩余风险均为非阻塞，不影响本主线“主链路回归可收口”的裁决。

## 3. M1-M4 分阶段证据表

| 阶段 | 验收目标 | 关键证据 | 结论 |
| --- | --- | --- | --- |
| M1 现状盘点与矩阵冻结 | 冻结成本、付款、库存、审批九条主链路范围 | 覆盖合同付款条件、变更成本、采购收货库存、领料出库成本、分包计量结算、付款审批财务回写、发票台账、审批通知预警、驾驶舱来源下钻 | 通过 |
| M2 后端链路一致性回归 | 验证状态、金额、关联关系和台账一致性 | 定向 Maven 测试集合退出码 0；提交 `108822ace4a236392ffa049b813eadb4042bee71` | 通过 |
| M3 前端/浏览器冒烟 | 验证关键页面真实浏览器可达且无阻塞错误 | `5173` 前端 200，dev-login 302 到 `/dashboard` 并设置 cookie，`8080` health 为 `UP`；14 个页面 Chromium 冒烟通过 | 通过 |
| M4 驾驶舱来源下钻 | 验证关键 KPI 和来源 API 可解释 | 成本经理、项目成本拆解、采购经理、生产经理、财务、审批待办、预警 API 均 200，来源字段可解释 | 通过 |
| M5 正式验收归档 | 汇总证据并形成通过/不通过裁决 | 本报告输出正式结论、阻塞/非阻塞风险和后续建议 | 通过 |

## 4. M2 后端变更范围与测试证据

### 4.1 变更范围

| 文件 | 作用 |
| --- | --- |
| `backend/src/main/java/com/cgcpms/cost/service/CostSummaryAssembler.java` | 将 `MAT_REQUISITION` 纳入实际成本汇总来源 |
| `backend/src/main/java/com/cgcpms/cost/strategy/MaterialRequisitionCostStrategy.java` | 新增领料成本生成策略 |
| `backend/src/main/java/com/cgcpms/requisition/handler/MaterialRequisitionWorkflowHandler.java` | 领料审批通过后触发出库和项目材料成本生成 |
| `backend/src/test/java/com/cgcpms/requisition/service/MatRequisitionWorkflowSubmitTest.java` | 增加 `stockOutFlag`、`mat_stock_txn`、`MAT_REQUISITION` 成本断言 |

核心修复：审批通过的领料单现在可生成库存出库流水和项目材料成本，且成本汇总将 `MAT_REQUISITION` 作为实际成本来源。

### 4.2 测试命令

```powershell
.\mvnw.cmd -q "-Dtest=CtContractServiceTest,ContractCompositeSaveTest,CtContractPaymentTermServiceTest,VarOrderServiceTest,CtContractChangeServiceTest,CtContractChangeWorkflowHandlerTest,MatPurchaseOrderServiceTest,MatReceiptServiceTest,MaterialReceiptWorkflowHandlerTest,MatStockServiceTest,MatRequisitionServiceTest,MatRequisitionWorkflowSubmitTest,SubMeasureServiceTest,SubMeasureWorkflowHandlerTest,StlSettlementQueryServiceTest,SettlementWorkflowHandlerTest,PayApplicationServiceTest,PaymentWritebackTest,PaymentFinancialConsistencyTest,InvoiceServiceTest,WorkflowTaskServiceTest" test
```

结果：退出码 0。  
补充复核：`MatRequisitionWorkflowSubmitTest` 独立复跑通过。  
非阻塞提示：测试输出存在 Mockito 动态 agent 未来兼容性警告和 Spring Boot generated password 提示，不影响本轮测试结论。

## 5. M3 前端浏览器冒烟证据

### 5.1 运行态前置

| 验收项 | 结果 |
| --- | --- |
| `http://localhost:5173/` | HTTP 200，页面标题为“建筑工程总包项目管理系统” |
| `http://localhost:5173/api/auth/dev-login?redirect=/dashboard` | HTTP 302 到 `/dashboard`，设置 `XSRF-TOKEN`、`access_token`、`refresh_token` |
| `http://localhost:8080/api/actuator/health` | HTTP 200，返回 `{"status":"UP"}` |

### 5.2 页面冒烟范围

| 页面 | 结论 |
| --- | --- |
| `/contract/ledger` | 通过 |
| `/variation/order` | 通过 |
| `/purchase/order` | 通过 |
| `/purchase/receipt` | 通过 |
| `/inventory/stock` | 通过 |
| `/inventory/transaction` | 通过 |
| `/inventory/material-requisition` | 通过 |
| `/subcontract/measure` | 通过 |
| `/settlement/list` | 通过 |
| `/payment/application` | 通过 |
| `/invoice` | 通过 |
| `/approval/todo` | 通过 |
| `/approval/done` | 通过 |
| `/alert` | 通过 |

浏览器结论：14 个关键页面均未回退 `/login`，未发现白屏、Vue runtime error、关键 5xx/404、认证失败或稳定加载失败。

## 6. M4 驾驶舱/API 证据表

| 页面或 API | 状态 | 关键字段 / 来源证据 | 结论 |
| --- | --- | --- | --- |
| `/dashboard` | 200，实际落点 `/dashboard` | 首页驾驶舱文本可见 | 通过 |
| `/api/dashboard/cost-manager` | 200 | 含 `targetCost`、`actualCost`、`dynamicCost`、`ledgerRows[26]`、`subjectRankings[5]`、`pendingPayments[2]` | 通过 |
| `/api/dashboard/project/2071032241708793858/cost-breakdown` | 200 | 含项目成本拆解、`subjectBreakdowns[5]` | 通过 |
| `/api/dashboard/purchase-manager` | 200 | 来源类型含 `PURCHASE_REQUEST`、`PURCHASE_ORDER`、`MATERIAL_RECEIPT` | 通过 |
| `/api/dashboard/production-manager` | 200 | 来源类型含 `MATERIAL_RECEIPT`、`MATERIAL_REQUISITION`、`SUB_MEASURE` | 通过 |
| `/api/dashboard/finance` | 200 | 含 `pendingPaymentAmount=5600000.00`、`approvedUnpaidAmount=5600000.00`、`pendingPayments[2]` | 通过 |
| `/api/workflow/tasks/todo` | 200 | `records[5]`，审批待办 API 可达 | 通过 |
| `/api/alerts?pageNum=1&pageSize=10` | 200 | `records[10]`、`total=23`，含 `projectId/sourceType/sourceId` | 通过 |

补充说明：M4 兜底核验曾启发式尝试一个非指定项目 ID，返回 `400 PROJECT_NOT_FOUND`；该路径不是验收指定项目 `2071032241708793858`，不计为阻塞。

## 7. `MAT_REQUISITION` 证据强弱

结论：不阻塞 M5，证据强度足以支撑本主线收口。

强证据：

1. M2 代码证据：`MaterialRequisitionWorkflowHandler` 审批通过后触发 `costGenerationService.generateCost("MAT_REQUISITION", requisitionId)`。
2. M2 代码证据：`MaterialRequisitionCostStrategy` 以 `MAT_REQUISITION` 为来源生成项目材料成本。
3. M2 代码证据：`CostSummaryAssembler.isActualCostSource` 已纳入 `MAT_REQUISITION`。
4. M2 测试证据：`MatRequisitionWorkflowSubmitTest` 断言审批通过后生成 `MAT_REQUISITION` 出库流水和成本项。
5. M4 API 证据：`/api/dashboard/production-manager` 直接返回来源类型 `MATERIAL_REQUISITION`。

弱证据 / 缺口：

1. 当前演示项目成本台账实测 `/api/cost-ledger?projectId=2071032241708793858` 来源分布未直接枚举到 `MAT_REQUISITION`。
2. 该缺口更接近演示数据分布不足，而不是代码链路无法解释；已有代码、M2 测试和生产驾驶舱 API 可解释领料来源。

裁决：作为非阻塞剩余风险记录，建议后续用定向演示数据补一次人工复核，不影响第30主线本轮收口。

## 8. 剩余风险

### 8.1 阻塞风险

无。

### 8.2 非阻塞风险

| 编号 | 风险 | 影响 | 建议 |
| --- | --- | --- | --- |
| R1 | 当前演示数据成本台账未直接枚举 `MAT_REQUISITION` 来源 | 不影响代码链路和 M2/M4 结论，但成本台账页面的直接演示证据不足 | 后续补一组定向领料演示数据，人工复核成本台账来源分布 |
| R2 | Mockito 动态 agent 未来兼容性警告 | 当前测试退出码为 0，不影响本轮验收；未来 JDK 策略变化可能影响测试运行方式 | 后续构建兼容性任务中按 Mockito 官方建议配置 agent |
| R3 | Spring Boot generated password 提示 | 本地测试/运行态提示，不影响 dev-login 验收和本轮主链路结论 | 后续运行态/安全配置任务中统一收敛开发环境提示 |
| R4 | M3/M4 浏览器验收使用 dev-login 超管账号 | 能证明页面和 API 主链路可达，但不能替代真实采购、生产、财务角色的权限体验复核 | 后续用真实采购/生产/财务账号做一次人工抽样复核 |

## 9. 最终建议

第30主线“成本、付款、库存、审批主链路回归”可收口。

建议主线程后续进入合并前 Git 收口流程；后续非阻塞补强可作为独立小任务处理，重点是：

1. 用定向演示数据补一次 `MAT_REQUISITION` 成本台账直接枚举复核。
2. 用真实采购、生产、财务账号抽样复核关键页面和驾驶舱。
3. 在构建兼容性任务中处理 Mockito 动态 agent 未来兼容性提示。

最终裁决：通过 / 无阻塞 / 可收口。
