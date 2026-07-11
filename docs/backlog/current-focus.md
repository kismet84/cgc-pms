# Current Focus

## 当前版本

- 分支：`develop/1.5`
- 基线：`v1.0.0`
- 阶段：v1.5 产品情报首轮闭环与下一主线准入
- v1.0 backlog：[只读快照](../archive/v1.0/backlog-snapshot/)

## 当前执行边界

- 第37条主线已建立项目地图、竞品情报和首轮迭代决策闭环。
- `ISSUE-037-001`、`ISSUE-037-002` 已完成并通过验收；当前 Ready 队列进入补货。
- 下一项实施任务必须先进入 [Ready 队列](ready-issues.md)。
- 候选来源依次为 [Ad-hoc 计划](ad-hoc-plan.md)、[长期增强计划](cgc-pms-production-enhancement-plan.md)。
- v1.0 的完成记录、测试数量和质量结论不得直接作为 v1.5 验收证据。

## 当前方向决策

- 决策周期：`PI-2026-07-11-01`。
- 已完成方向：采购低库存补货建议最小闭环。
- 当前状态：`Done`，真实采购经理角色已完成“低库存库存项 → 采购申请预填 → 保存 → 测试数据清理”的本地闭环。
- 决策依据：[项目地图](../product-intelligence/project-map.md)、[竞品情报](../product-intelligence/competitor-analysis.md)、[迭代决策](../product-intelligence/evolution-decision.md)。
- 补货结论：`PI-2026-07-11-02` 已确认现有订单与已审批验收数据足以支撑“仅交付维度”的供应商档案，原阻塞已解除。
- 已完成：`ISSUE-037-002` 供应商交付档案最小闭环，真实采购经理角色已看到迟交完成与逾期未完成两类交付状态。
- 当前补货：先把 `DashboardProjectBusinessService` 的滞后项目、任务、审批、到期合同，以及 `DashboardFinanceManagementService` 的管理任务/风险租户全量聚合拆为“驾驶舱项目数据范围统一收口”Ready，再评估分包 WBS 与现场日报。
- 后续候选：分包 WBS 单前置 FS 依赖与延期风险、现场日报最小闭环；必须在当前 Issue 收口后重新检查 Ready、flags 和同域连续次数。
