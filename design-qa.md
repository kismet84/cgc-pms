# 驾驶舱复刻 UI 验收记录

## 验收对象

- 页面：`/dashboard`
- 视口：`1440x1024`
- 环境：Docker 前端服务 `cgc-pms-frontend-dev`，访问 `http://localhost:5173/`
- 参考图：成本经理驾驶舱，忽略侧边菜单差异

## 已复刻内容

- 顶部标题、项目选择、月份选择、刷新、全屏入口。
- 角色页签顺序：成本经理、项目经理、生产经理、安全经理、商务经理。
- 六项 KPI 横向指标：目标成本、动态成本、成本偏差、预计利润、已确认收入、现金余额。
- 主体区域：左侧成本执行趋势图、成本科目排名；右侧超预算预警、逾期事项、待审批付款。
- 底部台账：成本台账、合同执行、资金流水 tab，筛选栏、搜索框、导出、分页和表格。

## 当前占位数据

参考图所需字段中，当前后端 `CostManagerDashboardVO` 只稳定提供部分成本 KPI 和 `overBudgetAlerts`。为保证视觉复刻，以下内容已用前端占位：

- 已确认收入、现金余额。
- 月度成本趋势：目标成本累计、动态成本累计、成本偏差累计。
- 超预算预警表的完整金额、超支率、预警时间。
- 逾期事项列表。
- 待审批付款列表。
- 成本台账、合同执行、资金流水底部表格及筛选条件。
- 参考图口径的 KPI 数值和成本科目排名。

## 缺少接口建议

- `GET /api/dashboard/cost-manager?projectId=&month=`：补齐 `confirmedIncome`、`cashBalance`、`profitRate`、`deviationRate`、环比/同比指标。
- `GET /api/dashboard/cost-manager/trend?projectId=&month=`：返回按月的目标成本累计、动态成本累计、成本偏差累计。
- `GET /api/dashboard/cost-manager/subject-rank?projectId=&month=`：返回成本科目排名金额、占比、可下钻科目 ID。
- `GET /api/dashboard/cost-manager/budget-alerts?projectId=&month=`：返回超预算预警表字段。
- `GET /api/dashboard/cost-manager/overdue-items?projectId=&month=`：返回逾期事项。
- `GET /api/dashboard/cost-manager/pending-payments?projectId=&month=`：返回待审批付款。
- `GET /api/dashboard/cost-manager/ledger?projectId=&month=&subjectId=&status=&keyword=&dateRange=`：返回底部台账分页数据。

## 验证结果

- `pnpm type-check`：通过。
- `pnpm build`：通过。
- Docker 前端已重启并等待 150 秒，日志确认 `VITE v6.4.3 ready`。
- Browser 验收：`http://localhost:5173/dashboard` 在 `1440x1024` 下可渲染参考图结构，无 Vite/框架错误遮罩，控制台无 error/warn。
- 交互证明：底部合同搜索框可输入 `HT-2024`，页面状态保持。

## 已知差异

- 左侧系统菜单沿用项目现有布局，未按参考图重做。
- 底部台账在 1440 宽下存在约 87px 横向溢出，符合宽表常见行为；若要求首屏完全无横向溢出，需要进一步压缩列宽或隐藏部分列。
