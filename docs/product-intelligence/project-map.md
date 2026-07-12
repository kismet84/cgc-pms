# CGC-PMS 项目地图

## 2026-07-12 增量：现场日报与领料出库候选

- 日报已有同日已审批到货、计划任务和审计历史，但尚未呈现同日已审批并真实出库的领料事实。
- 现有 `mat_requisition` / `mat_requisition_item` 已具备租户、项目、领料日期、审批状态、出库标记、物料和数量，可在不新增表的前提下形成只读联动。
- `ISSUE-037-014` 已完成：只展示同租户、同项目、同日期、`APPROVED` 且 `stock_out_flag = 1` 的领料明细；不把领料解释为已安装，不披露价格、金额、合同或供应商信息。

## 2026-07-12 增量：现场日报变更历史

- 日报写操作已进入统一操作审计，但 CREATE 未绑定业务 ID，详情页也未展示可读历史。
- `ISSUE-037-013` 已完成：CREATE 审计归属具体日报，详情只读展示最小变更记录；未建设字段级版本或独立历史表。

## 2026-07-12 增量：计划任务与现场日报对照

- 分包 WBS 已具备项目、计划日期、状态、进度和单前置 FS；现场日报尚未呈现当天计划任务。
- `ISSUE-037-012` 已完成：日报详情只读展示计划日期覆盖当天的同项目任务，连接计划与每日现场事实；未新增排程或日报任务副本。

## 2026-07-12 增量：材料到货与现场日报联动

- 现场日报已具备项目/日期、草稿提交、附件、天气摘要和在场人数，但尚未展示已有材料验收事实。
- 材料验收已具备项目、验收日期、审批状态、供应商和物料数量；审批通过后原子触发库存与成本，日报不得复制或改写该事实。
- `ISSUE-037-011` 已完成：日报详情只读聚合同项目同日已审批验收明细，作为现场到货证据；设备、消耗、安装量和生产率继续后置。

## 地图基线

| 项目 | 当前值 |
| --- | --- |
| 产品版本 | `1.5.0-dev.0` |
| 分支 | `develop/1.5` |
| Commit | `a2d58b591` |
| 生成时间 | 2026-07-11 |
| 证据类型 | 当前代码、配置、现行规范、测试入口静态核对 |
| 验证新鲜度 | 业务测试和真实角色运行态待本轮后续复验 |
| 下次刷新 | 第37条主线首个业务 Candidate 完成后，或当前事实变化时 |

> 本地图中的 `Partial` 不等于功能不存在，表示真实代码链路已经存在，但尚未取得 v1.5 当前周期的完整运行或业务验收证据。v1.0 历史测试结论不用于升级状态。

## 产品定位

CGC-PMS 是面向建筑工程总包企业的项目经营与全过程管理平台，主线是项目、合同成本、采购库存、分包结算、付款发票、审批预警和角色驾驶舱的一体化管理。

明确不是：

- 通用软件研发项目管理工具。
- 通用 ERP 的完整替代品。
- 以多智能体、MCP 或编码工具为产品卖点的平台。

## 核心业务闭环

```text
项目立项
  → 合同 / 签证 / 采购 / 分包
  → 成本与收入归集
  → 收货 / 库存 / 领料 / 计量
  → 付款 / 发票 / 结算
  → 审批 / 通知 / 预警
  → 驾驶舱与经营分析
```

## 技术地图

```text
Vue 3 + TypeScript + Vite
        ↓ /api
Spring Boot 3 + Java 21 + Spring Security + MyBatis-Plus
        ↓
MySQL/H2 + Redis + MinIO
        ↓
Docker Compose + Nginx + Actuator + Prometheus
```

| 层级 | 现行入口 |
| --- | --- |
| 前端入口 | `frontend-admin/src/main.ts`、`frontend-admin/src/App.vue` |
| 前端路由 | `frontend-admin/src/router/` |
| 页面 | `frontend-admin/src/pages/` |
| API 封装 | `frontend-admin/src/api/modules/` |
| 后端入口 | `backend/src/main/java/com/cgcpms/CgcPmsApplication.java` |
| 后端业务域 | `backend/src/main/java/com/cgcpms/` |
| MySQL migration | `backend/src/main/resources/db/migration/` |
| H2 migration | `backend/src/main/resources/db/migration-h2/` |
| 部署 | `deploy/`、`docker-compose*.yml` |

## 当前规模快照

以下是路径级粗计数，不代表完成度：

| 指标 | 数量 |
| --- | ---: |
| 后端一级业务/技术域 | 32 |
| Controller 文件 | 53 |
| 前端 Vue 文件 | 129 |
| MySQL Flyway migration | 134 |

## 业务能力地图

| 业务域 | 前端证据 | 后端证据 | 测试入口示例 | 状态 | 当前缺口 |
| --- | --- | --- | --- | --- | --- |
| 项目与成员 | `pages/project/`、`api/modules/project.ts` | `project/` | `PmProjectControllerTest`、`ProjectOverviewServiceTest`、`ProjectLedgerProduction.test.ts` | Partial | v1.5 真实角色、项目数据范围和运行态待复验 |
| 合同与付款条件 | `pages/contract/`、`api/modules/contract.ts` | `contract/` | `CtContractServiceTest`、`ContractApprovalIntegrationTest`、`ContractLedgerPage.test.ts` | Partial | 合同履约、金额口径和审批联动需当前复验 |
| 变更与签证 | `pages/variation/`、`api/modules/variation.ts` | `variation/` | `VarOrderServiceTest`、`VarOrderControllerMockMvcTest`、`VariationOrderProduction.test.ts` | Partial | 变更收入/成本联动和审批边界待复验 |
| 成本与目标成本 | `pages/cost/`、`pages/cost-target/` | `cost/`、`revenue/`、`overhead/`、`accounting/` | `CostSummaryServiceTest`、`CostLedgerServiceTest`、`CostSummaryProduction.test.ts` | Partial | 多来源成本、月份快照和下钻口径待复验 |
| 采购与采购申请 | `pages/purchase/`、`pages/inventory/purchase-request.vue` | `purchase/` | `MatPurchaseOrderServiceTest`、`PurchaseRequestServiceTest`、`purchase/order.test.ts` | Partial | 已按安全阈值给出四位小数建议；尚无独立人工补货目标量，`ISSUE-037-009` 拟补，供货周期和预测仍缺失 |
| 收货、仓库与库存 | `pages/receipt/`、`pages/inventory/` | `receipt/`、`inventory/` | `MatReceiptServiceTest`、`MatStockServiceTest`、`stock-production.test.ts` | Partial | 已维护安全阈值并联动 KPI/预警；目标量、全量建议、预测和跨仓调拨仍缺 |
| 领料 | `pages/requisition/` | `requisition/` | `MatRequisitionServiceTest`、`useRequisitionForm.test.ts` | Partial | 与计划需用量、施工部位和损耗分析尚未闭环 |
| 分包与计量 | `pages/subcontract/` | `subcontract/` | `SubMeasureServiceTest`、`SubTaskControllerTest`、`subcontract/measure.test.ts` | Partial | 已有只读 WBS/甘特概览，但无独立计划任务、依赖和完整履约档案 |
| 结算 | `pages/settlement/` | `settlement/` | `StlSettlementServiceTest`、`StlSettlementControllerMockMvcTest`、`settlement/index.test.ts` | Partial | 合同、变更、计量、付款汇总需当前一致性复验 |
| 付款与资金日记账 | `pages/payment/`、`pages/cash-journal/` | `payment/`、`accounting/` | `PaymentFinancialConsistencyTest`、`PayRecordCashJournalIntegrationTest`、`payment/save-chain.test.ts` | Partial | 金额、财务回写、附件和权限需当前复验 |
| 发票与识别 | `pages/invoice/` | `invoice/` | `InvoiceServiceTest`、`InvoiceRecognitionTest`、`invoice-pdf.test.ts` | Partial | 识别可靠性、付款关联和文件安全需当前复验 |
| 审批、抄送与通知 | `pages/approval/` | `workflow/`、`notification/` | `WorkflowCoreServiceTest`、`ApproverResolverTenantIntegrationTest`、`ApprovalWorkList.test.ts` | Partial | 真实角色矩阵、跨业务状态一致性待复验 |
| 预警 | `pages/alert/` | `alert/` | `AlertEvaluationServiceTest`、`AlertControllerTest`、`alert/index.test.ts` | Partial | 规则治理、通知渠道和抑制升级仍是后续方向 |
| 驾驶舱与报表 | `pages/dashboard/`、`pages/report/` | `dashboard/` | `DashboardServiceTest`、`DashboardControllerTest`、`DashboardDataLoading.test.ts` | Partial | 指标来源、下钻和不同角色数据边界待复验 |
| 用户、角色、菜单与审计 | `pages/system/`、`api/modules/system.ts` | `auth/`、`system/`、`audit/` | `WorkflowControllerAuthTest`、`system/permissions/index.test.ts` | Partial | 不能用超级管理员替代真实角色验收 |
| 文件 | `api/modules/file.ts` | `file/` | 现有文件安全与业务绑定测试 | Partial | 上传、病毒扫描占位和业务绑定边界需当前复验 |

## 角色驾驶舱地图

| 角色 | 当前业务基础 | 状态 | 边界 |
| --- | --- | --- | --- |
| 项目经理 | 项目总览、待办、预警、审批、合同履约 | Partial | 不用经营财务图表冒充执行协同 |
| 商务经理 | 合同、变更、成本、结算、付款、预警 | Partial | 现有成本经理语义统一为商务经理 |
| 采购经理 | 采购、验收、库存、领料 | Partial | 不复用商务经理利润/结算主视图 |
| 生产经理 | 验收、领料、库存、分包计量近似数据 | Partial | 不是完整进度、劳务、机械和产值驾驶舱 |
| 总工程师 | 当前只有零散设计变更相关数据 | Frozen | 必须先建立技术方案、设计协调、技术审核和重大技术问题对象 |

## 数据与安全边界

| 边界 | 当前规则 | 地图结论 |
| --- | --- | --- |
| 租户 | 后端强制隔离，Service 必须校验 | 代码与规范存在，当前跨租户回归待复验 |
| 项目 | 大多数业务对象的主线维度 | 当前项目成员和接口范围待真实角色复验 |
| 权限 | `@PreAuthorize` 是安全边界，前端隐藏仅为体验 | 不能仅靠菜单可见性判定通过 |
| 审批 | 合同、变更、付款、结算等必须校验状态 | 跨业务状态一致性是高风险验收项 |
| 金额 | 成本、合同、采购、库存、分包、付款、结算共同影响 | 任何改动必须给出来源、月份和回滚证据 |
| 数据库 | 只新增 migration，不修改已应用脚本 | v1.5 业务候选默认优先无 migration 的最小闭环 |

## 工程与实施地图

| 能力 | 当前入口 | 状态 | 说明 |
| --- | --- | --- | --- |
| CI 门禁 | `.github/workflows/` | Partial | v1.0 结论已归档，当前远端 checks 需重新核验 |
| 本地运行 | `scripts/rebuild.py`、Docker Compose | Partial | ISSUE-037-001 已完成 8080、5173、dev-login health gate 与真实角色浏览器验收 |
| 现场日报验收直达 | `DevAuthController`、`/site/daily-log` | Implemented | `ISSUE-037-008` 已补 `/site`，直达与站外/遍历安全回落均有测试和运行态证据 |
| Ready 准入 | `docs/backlog/ready-issues.md`、`autopilot-ready.ps1` | Implemented | 当前规则和解析入口存在；ISSUE-037-004 收口后进入下一轮补货 |
| 候选补货 | `autopilot-refill.ps1` | Implemented | 读取 Ad-hoc 和长期计划，当前不读取外部情报 |
| 连续执行 | `autopilot-run-continuous.ps1` | Implemented | 20 轮隔离 canary 已验证连续执行、提交、证据、零人工干预和上限停止；LIMIT_REACHED 已统一关闭 enabled 并被 checkpoint/status 识别 |
| 质量归档 | `docs/quality/` | Implemented | 已归档第37条主线与 ISSUE-037-001 至 ISSUE-037-004 正式验收报告 |

## 当前明确缺口

### 产品候选

- 采购补货建议：已完成数量预填、安全阈值、可空人工目标量和可空自然日提前期计划日期预填；供应商级提前期、工作日历、预测与全量建议治理仍后置。
- 现场日报 / 施工日志：`ISSUE-037-005` 已建立日报对象、状态、项目范围与附件链，`ISSUE-037-007` 已增加人工天气摘要和可空在场人数；人员明细、设备材料、移动离线和质量安全继续后置。
- WBS 任务依赖与延期预警：`ISSUE-037-004` 已在分包任务上完成单前置 FS、项目数据范围和前置延期风险；仍不支持多前置、多类型、依赖连线、拖拽、自动改期或独立计划模型。
- 供应商交付档案：`ISSUE-037-002` 已用订单明细与已审批验收累计数量还原交付完成日，并区分按期完成、迟交完成和逾期未完成；质量、价格和退货仍不具备稳定口径，页面明确不是综合评级。
- 后端接口无前端入口治理：属于现有能力可达性和治理缺口，不是新业务域。
- 驾驶舱项目数据范围：`ISSUE-037-003` 已统一项目经理与管理驾驶舱的指定项目、全项目任务/审批/合同/风险聚合，空关联与不可见项目 fail-close。

### 工程治理候选

- 子智能体超时、悬挂线程退役与有限重派治理。

工程治理候选必须与产品候选分组排序，默认不能用泛化工具或流程改进替代产品方向判断。若当前证据证明治理缺口直接阻塞已选产品目标、安全边界或正式验收，可按 `缺口修复` 或 `运维治理` 进入 Ready；必须绑定产品目标、阻塞证据、解除条件、非目标和回滚方式。

## Unknown 与待复验

- 当前远端 CI checks 与分支保护实际状态。
- 当前本地 Docker、后端和前端运行态。
- 五类驾驶舱角色的真实账号与数据可见范围。
- 当前全量后端、前端单元测试结果。
- 现有长期计划中所有“已完成”项在 v1.5 的复验状态。
