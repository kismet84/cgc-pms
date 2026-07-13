# 第37条主线：项目地图、竞品情报与迭代决策闭环验收报告

## 验收结论

| 项目 | 结论 |
| --- | --- |
| 主线结论 | 通过 |
| 阻塞 | 非阻塞 |
| 是否可进入下一步 | 可以继续做 Candidate 准入核对；不可直接实施业务功能 |
| 是否可上线 | 不适用，本主线没有业务代码和生产变更 |
| 基线 | `develop/1.5@a2d58b591` |
| 验收日期 | 2026-07-11 |

第37条主线已经补齐现有 AutoPilot 之前的产品情报前置层，形成“项目地图 → 竞品情报 → 迭代决策 → Ad-hoc Candidate → 既有 Ready/AutoPilot”的可追踪链路。

## 正式交付物

- `docs/plans/第37条主线-CGC-PMS项目地图竞品情报与迭代决策闭环任务计划书.md`
- `docs/product-intelligence/README.md`
- `docs/product-intelligence/project-map.md`
- `docs/product-intelligence/competitor-analysis.md`
- `docs/product-intelligence/evolution-decision.md`
- `docs/backlog/ad-hoc-plan.md`
- `docs/backlog/current-focus.md`
- `docs/README.md`
- `docs/未来开发计划.md`

## 阶段验收

| 阶段 | 结果 | 证据 |
| --- | --- | --- |
| M0 v1.5 事实基线 | 通过 | 当前分支、HEAD、空 Ready/Blocked、v1.0 归档边界已核对 |
| M1 项目地图 | 通过 | 核心业务域、技术入口、角色、数据安全边界和 Unknown 已形成现行地图 |
| M2 竞品情报 | 通过 | 覆盖广联达、金蝶、Procore、Autodesk、Oracle、OpenProject、ERPNext、Odoo 等官方来源，记录 `checkedAt` 和证据等级 |
| M3 方向决策 | 通过 | 5 个候选完成分组排序，采购低库存补货建议通过方向否决门 |
| M4 Backlog 接入 | 通过 | 推荐项以 `Candidate` 进入 Ad-hoc，refill 能识别且未绕过 Ready |
| M5 索引与收口 | 通过 | 文档入口、Current Focus、未来计划和正式验收报告已同步 |

## 关键验收证据

### 项目事实

- 当前代码存在库存余额、库存流水、低库存 KPI、采购申请和采购审批链。
- 当前低库存口径是可用量小于 `10`，尚不是物料级安全库存或需求预测。
- 当前存在供应商交付评分和分包任务页只读 WBS/甘特，不再把它们误判为“完全没有”。
- 当前没有独立现场日报对象，也没有完整计划任务依赖模型。

### 方向决策

推荐项的最小范围是：

```text
低库存识别
  → 用户确认建议数量
  → 复用现有采购申请预填
  → 用户修改并提交现有审批
```

明确排除自动下单、自动审批、需求预测、供应商自动选择、多仓调拨和物料级阈值配置。

### refill 识别

只读调用 `Get-AutopilotRefillDecision` 返回：

- `action=PLAN_READY`
- `targetReadyCount=3`
- 第一候选=`采购低库存补货建议最小闭环`
- `source=ad-hoc-plan.md`

证明产品情报结果已经接入现有候选补货链。

### AutoPilot dry-run

插件 dry-run 在 Select Gate 安全停止，因为当前 `ready-issues.md` 不包含合格 Ready Issue：

- 未进入 checkpoint、实现或修复。
- 未请求本地提交，未 push。
- 下一动作是等待负责人决策。

失败分类：**工具和业务均未失败；属于预期准入门阻断。**

## AutoPilot 状态

| 标记 | 状态 |
| --- | --- |
| `stop.flag` | 不存在 |
| `pause.flag` | 不存在 |
| `enabled.flag` | 存在 |
| 下一任务派发 | 未启动；当前无合格 Ready |

## 剩余风险

| 风险 | 阻塞性 | 沉淀位置 | 处理要求 |
| --- | --- | --- | --- |
| 库存记录到项目的安全解析路径尚未核对 | 阻塞 Candidate 升 Ready | `docs/backlog/current-focus.md` | 准入核对必须覆盖租户和项目隔离 |
| 采购申请预填路由与保存状态尚未核对 | 阻塞 Candidate 升 Ready | `docs/backlog/current-focus.md` | 复用现有页面和保存链，不建第二套采购对象 |
| 阈值 10 与“补到 10”的产品口径需要确认 | 阻塞 Candidate 升 Ready | `docs/backlog/current-focus.md` | 未确认前保持 Candidate |
| v1.5 全量测试、真实角色和运行态未复验 | 非本主线阻塞 | `docs/product-intelligence/project-map.md` | 业务实施或上线裁决时重新验证 |

## 最终裁决

- 正式交付物：通过。
- 产品情报闭环：通过。
- 推荐方向：通过 Candidate 门，未通过 Ready 门。
- 业务实现：未开始，符合授权和准入边界。
- Git 提交与推送：未执行，需要用户另行授权。

