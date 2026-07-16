# CGC-PMS 竞品情报与能力差距

## 2026-07-16 增量复核：供应商提前期与采购交货日期

- Odoo 19 官方 Lead Times 说明 Vendor Lead Time 是采购订单确认到收货的自然日，Expected Arrival 由订单日期/截止日加提前期得到；该自然日口径不自动跳过周末或节假日。
- 当前 CGC-PMS 已有库存项补货提前期，但它属于仓库+物料设置；合作方没有供应商默认值，采购订单已有供应商、订单日期和交货日期，可用更小的默认预填闭环补齐重复换算。
- 决策影响：先做可空供应商默认自然日与订单级可覆盖预填；供应商×物料价目表、工作日历、节假日与预测继续后置。
- 官方来源：<https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp/inventory/warehouses_storage/replenishment/lead_times.html>，核验时间 2026-07-16。

## 分析基线

| 项目 | 当前值 |
| --- | --- |
| checkedAt | 2026-07-12 |
| 项目基线 | `develop/1.5@667299a93` |
| 研究原则 | 组合对标，不复制单一产品 |
| 证据范围 | 官方文档、官方产品页、官方案例 |
| 失效提醒 | 超过 90 天未复核的来源，在关键决策前重新核验 |

## 事实、推断与动作的使用规则

- **事实**：来源页面直接支持的能力描述。
- **推断**：该事实对 CGC-PMS 可能意味着什么，不能写成竞品事实。
- **动作**：只有同时得到项目地图证据和业务价值支持时，才进入迭代决策。

## 对标对象

### 1. 广联达 PMSmart

| 字段 | 内容 |
| --- | --- |
| 能力域 | 项目经营、进度、物资、劳务、分包履约、数据决策 |
| 事实 | 官方产品页将其定位为项目经理及管理班子的工具，以进度为主线，连接算量、进度、物资和劳务数据；页面列出物资需用/缺口分析、劳务分包履约评价、进度与资源匹配等场景 |
| 来源 | <https://www.glodon.com/product/418.html> |
| 来源等级 | B |
| 版本 | 页面未给出独立版本，Unknown |
| 对 CGC-PMS 的启示 | 下一阶段价值可能不在新增孤立台账，而在复用既有采购、库存、分包和成本数据形成跨域决策 |
| 候选动作 | 采购补货建议、供应商/分包履约档案、进度与资源差异；进入决策前仍需核对现有数据完整度 |

### 2. 金蝶建筑企业数字化解决方案

| 字段 | 内容 |
| --- | --- |
| 能力域 | 项目、财务、资源、税务、供应链、计划、成本、资金一体化 |
| 事实 | 官方方案描述项目管控、财务管控、资源管控和税务管控一体化，并给出多级计划、计划预警、动态成本、挣值分析、合同到付款的资金管控等能力 |
| 来源 | <https://www.kingdee.com/solutions/architecture.html>、<https://www.kingdee.com/solutions/architecture0.html> |
| 来源等级 | B |
| 版本 | 页面未给出独立版本，Unknown |
| 对 CGC-PMS 的启示 | CGC-PMS 现有合同、成本、付款链路是优势，但计划/WBS、动态经营穿透和项目企业协同仍有明显差距 |
| 候选动作 | WBS 延期预警优先于通用任务平台；成本改进应先做来源穿透而非重复台账 |

### 3. Procore Daily Log

| 字段 | 内容 |
| --- | --- |
| 能力域 | 现场日报、移动采集、人员、设备、交付、检查、延误、安全、照片 |
| 事实 | 官方帮助中心提供 Web、iOS、Android 日志录入和报告，列出天气、人员、设备、检查、延误、交付、安全、照片和变更历史等日志类型 |
| 来源 | <https://support.procore.com/products/online/user-guide/project-level/daily-log> |
| 来源等级 | A |
| 版本 | 持续更新的在线帮助中心 |
| 对 CGC-PMS 的启示 | “现场日报”不是一个备注表，而是日期、项目、人员、设备、材料、进度、问题和附件证据的组合闭环 |
| 候选动作 | 先定义最小日报主表、施工内容、问题/延误和照片绑定；离线与原生移动端不进入第一版 |

2026-07-12 增量核验：官方 Daily Log Overview 将 Deliveries 用于记录到货来源、追踪号与内容，将 Productivity 用于记录到场和已安装材料。对 CGC-PMS 的最小动作是复用既有已审批材料验收事实做日报只读联动，不复制完整日志类型体系。来源：<https://support.procore.com/products/online/user-guide/project-level/daily-log/tutorials/daily-log-overview>。

同页的 Change History 用于列出日报当天发生的修改及修改人。对 CGC-PMS 的最小动作是复用统一操作审计并修正 CREATE 的业务 ID 归属，不新建日报历史表或字段级版本系统。

2026-07-12 补货核验：Procore Daily Log Overview 将 Productivity 用于记录到场和已安装材料；Odoo 19 项目盈利官方文档则只把已验证库存移动计入项目材料成本。结合当前代码，CGC-PMS 不把领料冒充“已安装”，仅复用审批通过且已真实出库的领料单，在日报中只读展示“当日已审批领料”，作为现场材料流转证据。来源：<https://support.procore.com/products/online/user-guide/project-level/daily-log/tutorials/daily-log-overview>、<https://www.odoo.com/documentation/19.0/applications/services/project/project_management/project_profitability.html>。

### 4. Autodesk Construction Cloud

| 字段 | 内容 |
| --- | --- |
| 能力域 | 现场与办公室协同、文档、RFI、Submittal、成本和移动项目管理 |
| 事实 | 官方材料强调从设计到运营连接团队、数据和工作流，Autodesk Build 提供面向现场的移动项目管理，并将 RFI、Submittal 和成本工作流放在统一平台中 |
| 来源 | <https://old.construction.autodesk.com/why-autodesk-construction-cloud/>、<https://construction.autodesk.com/resources/cxo-executive/optimise-construction-project-management-workflows-with-the-cloud/> |
| 来源等级 | B |
| 版本 | 在线产品材料，页面未给出独立版本 |
| 对 CGC-PMS 的启示 | 长期现场管理应与文件、问题和审批关联；当前不应直接扩展成完整文档/BIM 协同平台 |
| 候选动作 | 现场日报先复用现有文件与审批能力，RFI/Submittal 保留为长期方向 |

### 5. Oracle Primavera Unifier 26

| 字段 | 内容 |
| --- | --- |
| 能力域 | 资本项目全生命周期、业务流程、成本、文档、基金、跨项目可视化 |
| 事实 | Oracle 官方文档将 Unifier 定义为覆盖规划、设计、采购、施工到运维的集成平台，支持预算、成员、规范、RFI、共享文档、审批和跨项目实时可视性 |
| 来源 | <https://docs.oracle.com/en/industries/construction-engineering/primavera-unifier/26/index.html>、<https://docs.oracle.com/en/industries/construction-engineering/primavera-unifier/26/admin-help/welcome.html> |
| 来源等级 | A |
| 版本 | 26 |
| 对 CGC-PMS 的启示 | 长期竞争力来自可配置业务流程和跨项目经营可视化；当前阶段应先保证单项目真实数据与下钻，不建设通用流程设计器 |
| 候选动作 | 强化跨项目驾驶舱和来源穿透，通用流程配置暂不立项 |

### 6. OpenProject Gantt

| 字段 | 内容 |
| --- | --- |
| 能力域 | Work Package、父子任务、里程碑、依赖、甘特图、计划调整 |
| 事实 | 官方用户文档提供工作包甘特图、层级和计划展示能力 |
| 来源 | <https://www.openproject.org/docs/user-guide/gantt-chart/> |
| 来源等级 | A |
| 版本 | 在线文档，当前页面未记录产品版本 |
| 对 CGC-PMS 的启示 | WBS 候选必须先有任务、层级、日期、责任人、状态和依赖，甘特图只是展示层，不应先做图再补数据模型 |
| 候选动作 | WBS 第一版先做任务层级、FS 依赖和延期判断；拖拽排程、资源负载后置 |

### 7. ERPNext Project

| 字段 | 内容 |
| --- | --- |
| 能力域 | 项目、任务、时间、成本与盈利关联 |
| 事实 | ERPNext 官方项目文档提供项目与任务管理，并与 ERP 业务数据连接 |
| 来源 | <https://docs.frappe.io/erpnext/project> |
| 来源等级 | A |
| 版本 | 在线文档，当前页面未记录产品版本 |
| 对 CGC-PMS 的启示 | CGC-PMS 已有更垂直的合同成本链路，不需要复制通用 ERP；应强化项目维度的收入、成本、付款和盈利聚合 |
| 候选动作 | 经营分析优先复用现有数据，不引入通用会计或 ERP 抽象 |

### 8. Odoo Project 19

| 字段 | 内容 |
| --- | --- |
| 能力域 | 项目、任务、阶段、里程碑、依赖和盈利分析 |
| 事实 | Odoo 19 官方项目文档覆盖项目和任务工作流，并与其他业务应用集成 |
| 来源 | <https://www.odoo.com/documentation/19.0/applications/services/project.html> |
| 来源等级 | A |
| 版本 | 19.0 |
| 对 CGC-PMS 的启示 | 模块间连接比继续增加独立列表价值更高；但 CGC-PMS 应保持建筑业务语义，不复制通用任务模型 |
| 候选动作 | WBS/现场任务必须与项目、施工对象和现有审批/预警结合 |

2026-07-12 补货核验：Odoo 19 官方任务依赖文档明确后续任务在前置完成前保持 Waiting，不能进入 In Progress。CGC-PMS 已有单前置 FS、同项目/环/日期校验和风险展示，但后端尚未阻止前置未完成时推进后续任务；下一最小动作只补状态门禁，不扩多前置、自动排程或依赖连线。来源：<https://www.odoo.com/documentation/19.0/applications/services/project/tasks/task_dependencies.html>。

### 9. Odoo Inventory Reordering Rules 19

| 字段 | 内容 |
| --- | --- |
| 能力域 | 最小/最大库存、补货规则、供货周期、预测库存和采购建议 |
| 事实 | Odoo 19 官方文档说明补货报告结合库存、提前期和到货日期；供应商提前期表示采购订单确认到收货的自然日，并影响预计到货日期 |
| 来源 | <https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp/inventory/warehouses_storage/replenishment/reordering_rules.html>；<https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp/inventory/warehouses_storage/replenishment/lead_times.html> |
| 来源等级 | A |
| 版本 | 19.0，2026-07-12 复核 |
| 对 CGC-PMS 的启示 | 当前统一“库存小于 10”只能算预警，不是完整补货策略；第一版可先打通预警到采购申请，再决定是否引入物料级阈值和供货周期 |
| 候选动作 | 预填、安全阈值和人工目标量已完成；下一最小候选只增加库存项人工补货提前期并预填采购计划日期，供应商级提前期、工作日历与预测继续后置 |

### 10. Odoo Purchase & Vendor Analysis

| 字段 | 内容 |
| --- | --- |
| 能力域 | 供应商交期、收货时长、到货数量和采购金额分析 |
| 事实 | 官方仪表盘按供应商展示按期交付率，并以采购订单的预计到货与实际收货数据计算交付表现；同时区分供应商服务水平和采购金额 |
| 来源 | <https://www.odoo.com/documentation/master/applications/inventory_and_mrp/purchase/advanced/purchase_dashboard.html> |
| 来源等级 | A |
| 版本 | master 在线文档，2026-07-11 复核 |
| 对 CGC-PMS 的启示 | 首版供应商档案应只使用可追溯的计划交期与已审批验收数量，不把当前未完成状态冒充综合履约评分 |
| 候选动作 | 先完成仅交付维度的供应商档案；质量、价格和退货口径后置 |

## 能力差距矩阵

| 能力 | CGC-PMS 当前事实 | 外部证据 | 差距结论 | 决策影响 |
| --- | --- | --- | --- | --- |
| 合同—成本—付款经营链 | 已有真实业务域和测试入口 | 金蝶、ERPNext、Unifier 强调一体化与成本控制 | CGC-PMS 基础较强，当前主要缺当前验证和来源穿透 | 不新增重复台账，优先下钻与一致性 |
| 采购补货 | 已有低库存到采购申请预填、安全阈值和人工目标量；采购明细已有计划日期但补货入口不预填 | PMSmart 强调物资需用/缺口；Odoo 19 的补货报告结合提前期与到货日期 | 数量闭环已具备，人工时间闭环仍缺一环 | 先做可空自然日提前期，不扩供应商级预测 |
| 供应商/分包履约 | 有供应商、采购、验收、分包计量数据 | PMSmart 提供劳务分包履约评价 | 中等差距，但需确认评分数据质量 | 适合在补货或现场之后推进 |
| 现场日报 | 没有独立日报对象 | Procore 提供多类型日志、移动采集、报告和历史 | 明显差距，涉及新业务对象 | 价值高但工作量和数据设计较大 |
| WBS 与延期预警 | 没有完整计划任务、依赖和延期闭环 | OpenProject、金蝶、Odoo 均有计划/依赖/预警 | 明显差距，属于平台主干能力 | 候选价值高，需先做数据模型而非甘特图 |
| 现场文件与问题协同 | 有文件、审批、通知，但无 RFI/Submittal | Autodesk、Unifier 提供统一协同 | 长期差距 | 本轮不扩展，先复用现有文件能力 |
| 跨项目经营可视化 | 有角色驾驶舱和报表入口 | Unifier、金蝶强调跨项目与经营管控 | 部分差距 | 先复验指标来源和下钻，再扩展组合视图 |
| AI 决策 | 当前 AI 主要用于研发流程，产品 AI 能力有限 | PMSmart 官方材料强调连接业务数据辅助决策 | 证据和数据基础不足 | 不直接立项 AI 助手，先补真实数据闭环 |

## 初步结论

1. CGC-PMS 的优势是垂直合同、成本、采购、付款和审批链路，不能因竞品存在通用模块就重做。
2. 最适合当前基线的短闭环是采购补货建议：已有数据最多、对现有架构侵入较小、能直接服务采购经理。
3. WBS 单前置 FS 已复用现有分包任务完成最小切片；完整计划平台仍需新业务对象。现场日报仍需要独立事实对象，实施成本和风险高于已完成切片。
4. 供应商履约档案可以复用采购、验收和评分数据，但应先核对评分数据是否足够稳定。
5. AI、BIM、RFI、Submittal 和通用流程设计器保留为长期方向，不进入本轮候选实施。

这些结论将在 [迭代决策](evolution-decision.md) 中结合工作量、风险和最小验收路径正式裁决。
