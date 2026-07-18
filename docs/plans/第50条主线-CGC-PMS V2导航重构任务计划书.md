# 第50条主线：CGC-PMS V2 导航重构任务计划书

导航迁移审计、实施与验收基线。

状态：已完成并通过本地验收；审计基线已按当前工作区新增的“业务单据模板”能力同步校正。

**Goal:** 基于当前前端真实源码，建立 CGC-PMS V2 八个一级业务域、二级工作区、Tab、路由兼容和权限迁移的唯一执行基线；后续改造必须保留现有页面、权限语义和可访问的旧链接。

**Architecture:** 一级菜单按业务领域组织，二级菜单按完整工作流组织，Tab 仅承载同一对象或连续流程。导航展示、工作区 Tab、路由页面和权限码由同一导航描述符约束；复用既有路由与 Vue 页面，不新增业务页面、不移动页面、不修改既有权限语义。

## 1. 审计范围、边界与统计口径

### 1.1 已审计源码

| 范围                                        | 用途                                   |
| ------------------------------------------- | -------------------------------------- |
| frontend-admin/src/pages                    | 识别页面目录、路由视图与页内组件       |
| frontend-admin/src/router/index.ts          | 识别静态路由、重定向、路由权限和守卫   |
| frontend-admin/src/router/navigation.ts     | 识别当前侧栏导航结构与菜单权限         |
| frontend-admin/src/layouts                  | 识别桌面与移动端导航壳、全局账户入口   |
| frontend-admin/src/stores/user.ts           | 识别 roles、permissions 与权限查询入口 |
| frontend-admin/src/directives/permission.ts | 识别按钮级权限指令                     |

### 1.2 实施边界

- 允许修改导航描述符、侧栏、全局布局、工作区 Tab、对象上下文导航及其测试；不修改业务页面、业务 API、后端、数据库或页面文件位置。
- 不删除页面；本文件中的“废弃候选”仅可在未来获得重复、无调用和无业务价值证据后使用。
- 保留所有既有 URL、route name、route meta、路由守卫、权限码和 ADMIN/SUPER_ADMIN 边界。
- 个人资料、设置、帮助、登录和错误页属于全局工具或上下文页面，不计入八个业务一级菜单。

### 1.3 统计口径与结果

| 指标                     | 数量 | 口径                                            |
| ------------------------ | ---: | ----------------------------------------------- |
| 实施前侧栏一级菜单       |    7 | 原 navigationItems 顶层项                       |
| 实施前侧栏二级叶子入口   |   49 | 原 48 项 + 当前工作区新增的业务单据模板         |
| V2 侧栏一级菜单          |    8 | 当前 navigationItems 顶层项                     |
| V2 二级工作区            |   30 | 当前 navigationItems.children 总数              |
| V2 路由化 Tab 描述符     |   54 | 含原隐藏审批 Tab、报表、动态利润控制等归属      |
| src/pages 顶层页面目录   |   43 | 排除 tests 与 components                        |
| src/pages 全部 Vue 文件  |  127 | 递归扫描 \*.vue                                 |
| 页内 components Vue 文件 |   61 | 路径位于 pages/\*\*/components                  |
| 非 components Vue 文件   |   66 | 其中 65 个被 router 引用，另有 1 个角色权限弹窗 |
| 路由引用的独立 Vue 视图  |   65 | 去重后的 import('@/pages/...')                  |
| 页面路由记录             |   70 | 含同一 Vue 的新增/编辑与审批 Tab 等复用路由     |

说明：43 是目录数量，不是可访问页面数量；70 是路由记录数量，不等于 70 个 Vue 文件。实施和验收统一以“70 条页面路由 / 65 个独立路由视图 / 54 个 V2 Tab 描述符”为口径。

## 2. 当前导航、页面与权限结构

### 2.1 实施前侧栏导航树

实施前侧栏不是直接由 router 自动生成，而是由独立的 navigation.ts 静态配置生成。一级 key 如 /workbench、/project-operations 为导航分组 key，不是全部对应可访问路由。该树保留为迁移基线；当前实现见第 3 节和第 11 节。

```text
工作台
├─ 首页驾驶舱                 /dashboard
├─ 预警中心                   /alert
└─ 我的待办                   /approval/todo

项目经营
├─ 项目列表                   /project/list
├─ 合同台账                   /contract/ledger
├─ 签证变更                   /variation/order
├─ 成本目标                   /cost-target/index
├─ 成本台账                   /cost/ledger
├─ 成本核对                   /cost/summary
├─ 现场日报                   /site/daily-log
├─ 项目计划                   /project-schedule
├─ 质量安全整改               /quality-safety
├─ 图纸 RFI 技术闭环          /technical-management
├─ 项目竣工收尾               /project-closeout
└─ 投标成本                   /bid-cost

采购库存
├─ 采购申请                   /inventory/purchase-request
├─ 供应商招采履约             /supplier-sourcing
├─ 采购订单                   /purchase/order
├─ 材料验收                   /purchase/receipt
├─ 仓库管理                   /inventory/warehouse
├─ 库存台账                   /inventory/stock
├─ 出入库记录                 /inventory/transaction
└─ 领料申请                   /inventory/material-requisition

分包计量
├─ 分包任务                   /subcontract/task
└─ 分包计量                   /subcontract/measure

结算收付
├─ 项目预算                   /budget
├─ 结算台账                   /settlement/list
├─ 费用申请                   /payment/expense
├─ 付款申请                   /payment/application
├─ 资金运营                   /finance-operations
├─ 收入与回款                 /revenue
├─ 产值计量                   /production-measurement
├─ 资金日记账                 /cash-journal
├─ 会计凭证                   /accounting-entry
├─ 项目资金预测               /cash-forecast
├─ 财务核算与月结             /financial-close
└─ 发票管理                   /invoice

基础资料
├─ 合作方管理                 /partner
├─ 组织架构                   /org
├─ 材料字典                   /material/dictionary
└─ 成本科目                   /cost/subject

流程与系统
├─ 审批流程                   /approval/process
├─ 用户管理                   /system/users
├─ 角色管理                   /system/roles
├─ 权限清单                   /system/permissions
├─ 字典管理                   /system/dict
├─ 数据管理                   /system/data
├─ 操作审计                   /system/audit
└─ 业务单据模板               /system/document-templates
```

### 2.2 当前菜单与页面关系

- 实施前 49 个侧栏叶子入口只覆盖 70 条页面路由的一部分。新增、编辑、详情、审批详情、个人资料、设置、帮助、错误页等通过上下文或隐藏路由进入。
- 审批中心的 4 个工作 Tab 已共用 approval/todo.vue，但只有“我的待办”是工作台叶子入口；其余 3 个路由仍可独立深链访问。
- cost/control 已注册路由，但不在 navigation.ts 的叶子项或 matchPrefixes 中；当前静态导航未给出明确归属。
- 个人中心、设置、帮助均为 hidden 路由。桌面端入口位于 BasicLayoutAsync.vue 的侧栏底部账户下拉；通知中心也位于侧栏底部。移动端提供顶部头像入口。

### 2.3 当前路由清单

下列清单包含布局根路由、重定向分组与 69 条页面路由。参数化路径保持 router 中的原始语法。

```text
公共与布局
/login
/                              -> /dashboard
/403
/:pathMatch(.*)*

工作台
/dashboard
/dashboard/reports
/alert
/approval                       -> /approval/todo
/approval/todo
/approval/done
/approval/cc
/approval/mine
/approval/process
/approval/:instanceId

项目与商务
/project                        -> /project/list
/project/list
/project/:projectId/overview
/project/:projectId/members
/project/:projectId/edit
/site/daily-log
/project-schedule
/quality-safety
/technical-management
/project-closeout
/contract                       -> /contract/ledger
/contract/ledger
/contract/create
/contract/:id
/contract/:id/edit
/variation                      -> /variation/order
/variation/order
/bid-cost
/cost                           -> /cost/ledger
/cost/ledger
/cost/summary
/cost/control
/cost/subject
/cost-target                    -> /cost-target/index
/cost-target/index
/cost-target/create
/cost-target/:id/edit
/budget
/production-measurement

供应链与库存
/supplier-sourcing
/purchase                       -> /purchase/order
/purchase/order
/purchase/receipt
/inventory                      -> /inventory/warehouse
/inventory/warehouse
/inventory/stock
/inventory/transaction
/inventory/purchase-request
/inventory/material-requisition

分包、结算与资金
/subcontract                    -> /subcontract/task
/subcontract/task
/subcontract/measure
/settlement                     -> /settlement/list
/settlement/list
/settlement/:id
/payment                        -> /payment/application
/payment/application
/payment/expense
/finance-operations
/revenue
/cash-journal
/accounting-entry
/cash-forecast
/financial-close
/invoice

基础资料与系统
/partner
/org
/material                       -> /material/dictionary
/material/dictionary
/system                         -> /system/dict
/system/dict
/system/users
/system/data
/system/roles
/system/permissions
/system/audit
/system/document-templates

全局账户工具
/profile
/settings
/help
```

### 2.4 页面文件清单与当前功能

本表覆盖 69 条页面路由。相同 Vue 文件出现多次，表示当前以不同 URL 承载不同上下文或 Tab，而不是重复文件。

| 编号 | 当前菜单                     | 当前路由                        | Vue 文件                                  | 页面名称          | 当前功能描述                                   |
| ---: | ---------------------------- | ------------------------------- | ----------------------------------------- | ----------------- | ---------------------------------------------- |
|    1 | 全局公共                     | /login                          | pages/login/index.vue                     | 登录              | 登录入口；路由标记为 public、hidden。          |
|    2 | 工作台 > 首页驾驶舱          | /dashboard                      | pages/dashboard/index.vue                 | 首页              | 按当前角色与项目条件展示驾驶舱。               |
|    3 | 隐藏错误页                   | /403                            | pages/error/403.vue                       | 无权访问          | 路由守卫拒绝访问时的错误页。                   |
|    4 | 工作台上下文                 | /dashboard/reports              | pages/report/catalog.vue                  | 报表目录          | 展示可见报表目录。                             |
|    5 | 项目经营 > 合同台账          | /contract/ledger                | pages/contract/ContractLedgerPage.vue     | 合同列表          | 合同台账/列表。                                |
|    6 | 合同台账上下文               | /contract/create                | pages/contract/ContractFormPage.vue       | 新建合同          | 合同新增表单。                                 |
|    7 | 合同台账上下文               | /contract/:id                   | pages/contract/ContractDetailPage.vue     | 合同详情          | 单份合同详情。                                 |
|    8 | 合同台账上下文               | /contract/:id/edit              | pages/contract/ContractFormPage.vue       | 编辑合同          | 合同编辑表单；复用新增表单文件。               |
|    9 | 项目经营 > 成本台账          | /cost/ledger                    | pages/cost/ledger.vue                     | 成本列表          | 成本台账与分摊相关操作入口。                   |
|   10 | 项目经营 > 成本核对          | /cost/summary                   | pages/cost/summary.vue                    | 项目成本明细核对  | 项目成本汇总与历史核对。                       |
|   11 | 无明确静态菜单归属           | /cost/control                   | pages/cost/control.vue                    | 动态利润控制      | 成本预测、利润控制与纠偏操作。                 |
|   12 | 基础资料 > 成本科目          | /cost/subject                   | pages/cost-subject/index.vue              | 成本科目          | 成本科目维护。                                 |
|   13 | 项目经营 > 成本目标          | /cost-target/index              | pages/cost-target/index.vue               | 成本目标          | 成本目标列表。                                 |
|   14 | 成本目标上下文               | /cost-target/create             | pages/cost-target/edit.vue                | 新建成本目标      | 成本目标新增。                                 |
|   15 | 成本目标上下文               | /cost-target/:id/edit           | pages/cost-target/edit.vue                | 编辑成本目标      | 成本目标编辑；复用编辑文件。                   |
|   16 | 项目经营 > 签证变更          | /variation/order                | pages/variation/order.vue                 | 签证列表          | 签证变更单列表与维护。                         |
|   17 | 结算收付 > 结算台账          | /settlement/list                | pages/settlement/index.vue                | 结算列表          | 结算台账/列表。                                |
|   18 | 结算台账上下文               | /settlement/:id                 | pages/settlement/detail.vue               | 结算详情          | 结算详情及关联单据操作。                       |
|   19 | 项目经营 > 项目列表          | /project/list                   | pages/project/index.vue                   | 项目列表          | 项目列表与项目维护入口。                       |
|   20 | 项目列表上下文               | /project/:projectId/overview    | pages/project/overview.vue                | 项目总览          | 单项目总览。                                   |
|   21 | 项目列表上下文               | /project/:projectId/members     | pages/project/members.vue                 | 项目成员          | 单项目成员维护。                               |
|   22 | 项目列表上下文               | /project/:projectId/edit        | pages/project/edit.vue                    | 编辑项目          | 单项目编辑。                                   |
|   23 | 基础资料 > 合作方管理        | /partner                        | pages/partner/index.vue                   | 合作方管理        | 合作方主数据维护。                             |
|   24 | 项目经营 > 现场日报          | /site/daily-log                 | pages/site/daily-log.vue                  | 现场日报          | 施工现场日报填报与查询。                       |
|   25 | 项目经营 > 项目计划          | /project-schedule               | pages/project-schedule/index.vue          | 项目计划          | 项目计划管理。                                 |
|   26 | 项目经营 > 质量安全整改      | /quality-safety                 | pages/quality-safety/index.vue            | 质量安全整改      | 质量、安全检查与整改闭环。                     |
|   27 | 项目经营 > 图纸 RFI 技术闭环 | /technical-management           | pages/technical-management/index.vue      | 图纸 RFI 技术闭环 | 技术问题、图纸 RFI 的闭环管理。                |
|   28 | 项目经营 > 项目竣工收尾      | /project-closeout               | pages/project-closeout/index.vue          | 项目竣工收尾      | 项目竣工收尾管理。                             |
|   29 | 采购库存 > 供应商招采履约    | /supplier-sourcing              | pages/supplier-sourcing/index.vue         | 供应商招采履约    | 供应商招采与履约管理。                         |
|   30 | 项目经营 > 投标成本          | /bid-cost                       | pages/bid-cost/index.vue                  | 投标成本          | 投标成本管理。                                 |
|   31 | 基础资料 > 组织架构          | /org                            | pages/org/index.vue                       | 组织架构          | 公司、部门、岗位组织维护。                     |
|   32 | 分包计量 > 分包任务          | /subcontract/task               | pages/subcontract/task.vue                | 分包任务          | 分包任务管理。                                 |
|   33 | 分包计量 > 分包计量          | /subcontract/measure            | pages/subcontract/measure.vue             | 分包计量          | 分包计量管理。                                 |
|   34 | 采购库存 > 采购订单          | /purchase/order                 | pages/purchase/order.vue                  | 采购订单          | 采购订单管理。                                 |
|   35 | 采购库存 > 材料验收          | /purchase/receipt               | pages/receipt/index.vue                   | 材料验收          | 材料验收管理。                                 |
|   36 | 结算收付 > 付款申请          | /payment/application            | pages/payment/index.vue                   | 付款申请          | 付款申请与关联写回、单据操作。                 |
|   37 | 结算收付 > 费用申请          | /payment/expense                | pages/expense/index.vue                   | 费用申请          | 费用申请管理。                                 |
|   38 | 结算收付 > 项目预算          | /budget                         | pages/budget/index.vue                    | 项目预算          | 项目预算管理。                                 |
|   39 | 结算收付 > 资金运营          | /finance-operations             | pages/finance-operations/index.vue        | 资金运营          | 资金运营管理。                                 |
|   40 | 结算收付 > 收入与回款        | /revenue                        | pages/revenue/index.vue                   | 收入与回款        | 收入与回款管理。                               |
|   41 | 结算收付 > 产值计量          | /production-measurement         | pages/production-measurement/index.vue    | 产值计量          | 产值计量管理。                                 |
|   42 | 结算收付 > 资金日记账        | /cash-journal                   | pages/cash-journal/index.vue              | 资金日记账        | 资金日记账与资金账户操作。                     |
|   43 | 结算收付 > 会计凭证          | /accounting-entry               | pages/accounting-entry/index.vue          | 会计凭证          | 会计凭证处理。                                 |
|   44 | 结算收付 > 项目资金预测      | /cash-forecast                  | pages/cash-forecast/index.vue             | 项目资金预测      | 项目资金预测。                                 |
|   45 | 结算收付 > 财务核算与月结    | /financial-close                | pages/financial-close/index.vue           | 财务核算与月结    | 财务核算与月结。                               |
|   46 | 采购库存 > 仓库管理          | /inventory/warehouse            | pages/inventory/warehouse.vue             | 仓库管理          | 仓库主数据管理。                               |
|   47 | 采购库存 > 库存台账          | /inventory/stock                | pages/inventory/stock.vue                 | 库存台账          | 库存余额与调拨操作。                           |
|   48 | 采购库存 > 出入库记录        | /inventory/transaction          | pages/inventory/transaction.vue           | 出入库            | 库存出入库记录与提交操作。                     |
|   49 | 采购库存 > 采购申请          | /inventory/purchase-request     | pages/inventory/purchase-request.vue      | 采购申请          | 采购申请管理。                                 |
|   50 | 采购库存 > 领料申请          | /inventory/material-requisition | pages/requisition/index.vue               | 领料申请          | 现场领料申请管理。                             |
|   51 | 结算收付 > 发票管理          | /invoice                        | pages/invoice/index.vue                   | 发票管理          | 发票管理与核验入口。                           |
|   52 | 基础资料 > 材料字典          | /material/dictionary            | pages/material/dictionary.vue             | 材料字典          | 材料主数据/字典维护。                          |
|   53 | 工作台 > 预警中心            | /alert                          | pages/alert/index.vue                     | 预警中心          | 预警查询、订阅与管理。                         |
|   54 | 工作台 > 我的待办            | /approval/todo                  | pages/approval/todo.vue                   | 我的待办          | 审批待办 Tab。                                 |
|   55 | 审批中心上下文               | /approval/done                  | pages/approval/todo.vue                   | 我的已办          | 审批已办 Tab；复用待办页面文件。               |
|   56 | 审批中心上下文               | /approval/cc                    | pages/approval/todo.vue                   | 抄送我的          | 审批抄送 Tab；复用待办页面文件。               |
|   57 | 审批中心上下文               | /approval/mine                  | pages/approval/todo.vue                   | 我发起            | 审批发起实例 Tab；复用待办页面文件。           |
|   58 | 流程与系统 > 审批流程        | /approval/process               | pages/approval/process.vue                | 审批流程          | 审批流程配置入口；adminOnly。                  |
|   59 | 审批中心上下文               | /approval/:instanceId           | pages/approval/detail.vue                 | 审批详情          | 审批实例详情。                                 |
|   60 | 流程与系统 > 字典管理        | /system/dict                    | pages/system/dict/index.vue               | 字典管理          | 系统字典管理。                                 |
|   61 | 流程与系统 > 用户管理        | /system/users                   | pages/system/users/index.vue              | 用户管理          | 系统用户管理。                                 |
|   62 | 流程与系统 > 数据管理        | /system/data                    | pages/system/data/index.vue               | 数据管理          | 非生产环境业务数据清空维护；不是数据权限配置。 |
|   63 | 流程与系统 > 角色管理        | /system/roles                   | pages/system/roles/index.vue              | 角色管理          | 系统角色管理。                                 |
|   64 | 流程与系统 > 权限清单        | /system/permissions             | pages/system/permissions/index.vue        | 权限清单          | 菜单/权限清单管理。                            |
|   65 | 流程与系统 > 操作审计        | /system/audit                   | pages/system/audit/index.vue              | 操作审计          | 系统操作审计查询。                             |
|   66 | 流程与系统 > 系统配置        | /system/document-templates      | pages/system/document-templates/index.vue | 业务单据模板      | 业务单据模板管理；adminOnly。                  |
|   67 | 全局账户工具                 | /profile                        | pages/profile/index.vue                   | 个人中心          | 个人资料与密码修改。                           |
|   68 | 全局账户工具                 | /settings                       | pages/settings/index.vue                  | 设置              | 通知与界面偏好设置。                           |
|   69 | 全局账户工具                 | /help                           | pages/help/index.vue                      | 帮助              | 帮助、快捷键与常见问题。                       |
|   70 | 隐藏错误页                   | /:pathMatch(._)_                | pages/error/404.vue                       | 页面不存在        | 未匹配路径的错误页。                           |

页面目录中还有 61 个页面内部组件文件，以及 pages/system/roles/PermissionModal.vue。它们不是 router 直接引用的独立页面，因此不进入侧栏或本迁移矩阵；实施时不得误迁移为菜单项。

## 3. CGC-PMS V2 信息架构

### 3.1 设计规则

1. 一级菜单只表达业务领域，固定为用户指定的 8 项；不把个人账户、帮助、登录、错误页新增为业务一级菜单。
2. 二级工作区表达完整工作流。没有连续流程或共同对象的页面，不因“减少菜单数量”而强制放入同一 Tab。
3. Tab 表达同一对象的状态、列表视图或连续步骤。新增、编辑、详情、审批详情仍为上下文路由，不进入侧栏 Tab。
4. 角色只影响默认首页、排序和快捷入口；功能可见性、路由可达性、Tab 可见性与按钮能力均以 permission 为准。
5. V2 第一阶段只重组导航显示，继续使用当前路由和 Vue 文件；不以路由改名作为导航重构前置条件。

### 3.2 V2 一级菜单与工作区树

```text
全局工具栏（不计入一级业务菜单）
├─ 通知中心
├─ 我的待办角标                 -> /approval/todo
└─ 个人账户
   ├─ 个人资料                 -> /profile
   ├─ 偏好设置                 -> /settings
   ├─ 帮助与支持               -> /help
   └─ 退出登录

工作台
├─ 经营驾驶舱
├─ 我的工作
│  └─ Tab：待我处理｜我已处理｜抄送我的｜我发起
├─ 预警中心
└─ 报表中心

项目履约
├─ 项目管理
│  └─ 对象上下文：项目总览｜项目成员｜编辑项目
├─ 计划与现场
│  └─ Tab：项目计划｜现场日报
├─ 质量与技术
│  └─ Tab：质量安全整改｜图纸 RFI 技术闭环
└─ 项目收尾

商务合约
├─ 合同与变更
│  └─ Tab：合同台账｜签证变更
├─ 投标与成本目标
│  └─ Tab：投标成本｜成本目标
├─ 成本核算与控制
│  └─ Tab：成本台账｜成本核对｜动态利润控制
└─ 预算与产值
   └─ Tab：项目预算｜产值计量

供应链与物资
├─ 供应商管理
├─ 采购执行
│  └─ Tab：采购申请｜采购订单｜材料验收
├─ 仓储库存
│  └─ Tab：仓库管理｜库存台账｜出入库
└─ 现场领用

分包与结算
├─ 分包履约
│  └─ Tab：分包任务｜分包计量
└─ 结算管理
   └─ 对象上下文：结算详情

资金财务
├─ 收付款与发票
│  └─ Tab：付款申请｜费用申请｜收入与回款｜发票管理
├─ 资金运营
│  └─ Tab：资金运营｜资金日记账｜项目资金预测
└─ 财务核算
   └─ Tab：会计凭证｜财务核算与月结

基础资料
├─ 合作方管理
├─ 组织架构
├─ 物资主数据
└─ 财务主数据
   └─ 成本科目

系统管理
├─ 流程配置
│  └─ 审批流程
├─ 访问控制
│  └─ Tab：用户管理｜角色管理｜权限清单
├─ 系统配置
│  └─ Tab：字典管理｜业务单据模板
├─ 操作审计
└─ 数据维护（高风险、非生产环境）
```

### 3.3 V2 工作区与当前能力对应

| 一级菜单     | 二级工作区     | V2 Tab / 上下文                           | 直接复用的当前能力                                              | 设计结论                                        |
| ------------ | -------------- | ----------------------------------------- | --------------------------------------------------------------- | ----------------------------------------------- |
| 工作台       | 我的工作       | 待我处理、我已处理、抄送我的、我发起      | /approval/todo、/done、/cc、/mine                               | 已有可复用 Tab 模式。                           |
| 项目履约     | 项目管理       | 项目列表；项目总览/成员为对象上下文       | /project/\*\*                                                   | 不把单项目详情错误提升为全局菜单。              |
| 项目履约     | 计划与现场     | 项目计划、现场日报                        | /project-schedule、/site/daily-log                              | 同属项目日常履约。                              |
| 项目履约     | 质量与技术     | 质量安全整改、图纸 RFI 技术闭环           | /quality-safety、/technical-management                          | 两项均是履约过程控制，Tab 数为 2。              |
| 商务合约     | 合同与变更     | 合同台账、签证变更；合同表单/详情为上下文 | /contract/\*\*、/variation/order                                | 复用合同与变更既有路由。                        |
| 商务合约     | 成本核算与控制 | 成本台账、成本核对、动态利润控制          | /cost/ledger、/summary、/control                                | 明确补足 cost/control 的导航归属。              |
| 供应链与物资 | 采购执行       | 采购申请、采购订单、材料验收              | /inventory/purchase-request、/purchase/order、/purchase/receipt | 用户指定的连续采购流程，Tab 数为 3。            |
| 供应链与物资 | 仓储库存       | 仓库管理、库存台账、出入库                | /inventory/warehouse、/stock、/transaction                      | 当前已是同一库存域的 3 个页面。                 |
| 分包与结算   | 分包履约       | 分包任务、分包计量                        | /subcontract/task、/measure                                     | 同一分包履约链。                                |
| 资金财务     | 收付款与发票   | 付款申请、费用申请、收入与回款、发票管理  | /payment/\*\*、/revenue、/invoice                               | 现有 4 项，未超过 Tab 上限。                    |
| 系统管理     | 访问控制       | 用户管理、角色管理、权限清单              | /system/users、/roles、/permissions                             | 保留现有 adminOnly 边界，后续再统一权限描述符。 |
| 系统管理     | 系统配置       | 字典管理、业务单据模板                    | /system/dict、/system/document-templates                        | 接纳第48条主线新增入口，保留 adminOnly。        |

## 4. 页面迁移矩阵

说明：本表是迁移设计，不改变当前页面文件或权限逻辑。“操作建议”只使用保留、Tab合并、工作区调整、路由兼容、后续规划、废弃候选六种值。本轮没有足够代码证据把任何页面定为废弃候选。

| 当前页面          | 当前路由                        | 当前文件                                  | 新一级菜单   | 新工作区       | Tab                  | 操作建议   |
| ----------------- | ------------------------------- | ----------------------------------------- | ------------ | -------------- | -------------------- | ---------- |
| 登录              | /login                          | pages/login/index.vue                     | 全局公共     | 认证           | —                    | 保留       |
| 首页驾驶舱        | /dashboard                      | pages/dashboard/index.vue                 | 工作台       | 经营驾驶舱     | 驾驶舱               | 工作区调整 |
| 无权访问          | /403                            | pages/error/403.vue                       | 全局公共     | 错误页         | —                    | 保留       |
| 报表目录          | /dashboard/reports              | pages/report/catalog.vue                  | 工作台       | 报表中心       | 报表目录             | 工作区调整 |
| 合同列表          | /contract/ledger                | pages/contract/ContractLedgerPage.vue     | 商务合约     | 合同与变更     | 合同台账             | Tab合并    |
| 新建合同          | /contract/create                | pages/contract/ContractFormPage.vue       | 商务合约     | 合同与变更     | 合同台账的新增上下文 | 路由兼容   |
| 合同详情          | /contract/:id                   | pages/contract/ContractDetailPage.vue     | 商务合约     | 合同与变更     | 合同台账的详情上下文 | 路由兼容   |
| 编辑合同          | /contract/:id/edit              | pages/contract/ContractFormPage.vue       | 商务合约     | 合同与变更     | 合同台账的编辑上下文 | 路由兼容   |
| 成本列表          | /cost/ledger                    | pages/cost/ledger.vue                     | 商务合约     | 成本核算与控制 | 成本台账             | Tab合并    |
| 项目成本明细核对  | /cost/summary                   | pages/cost/summary.vue                    | 商务合约     | 成本核算与控制 | 成本核对             | Tab合并    |
| 动态利润控制      | /cost/control                   | pages/cost/control.vue                    | 商务合约     | 成本核算与控制 | 动态利润控制         | Tab合并    |
| 成本科目          | /cost/subject                   | pages/cost-subject/index.vue              | 基础资料     | 财务主数据     | 成本科目             | 工作区调整 |
| 成本目标          | /cost-target/index              | pages/cost-target/index.vue               | 商务合约     | 投标与成本目标 | 成本目标             | Tab合并    |
| 新建成本目标      | /cost-target/create             | pages/cost-target/edit.vue                | 商务合约     | 投标与成本目标 | 成本目标的新增上下文 | 路由兼容   |
| 编辑成本目标      | /cost-target/:id/edit           | pages/cost-target/edit.vue                | 商务合约     | 投标与成本目标 | 成本目标的编辑上下文 | 路由兼容   |
| 签证列表          | /variation/order                | pages/variation/order.vue                 | 商务合约     | 合同与变更     | 签证变更             | Tab合并    |
| 结算列表          | /settlement/list                | pages/settlement/index.vue                | 分包与结算   | 结算管理       | 结算台账             | 工作区调整 |
| 结算详情          | /settlement/:id                 | pages/settlement/detail.vue               | 分包与结算   | 结算管理       | 结算详情上下文       | 路由兼容   |
| 项目列表          | /project/list                   | pages/project/index.vue                   | 项目履约     | 项目管理       | 项目列表             | 工作区调整 |
| 项目总览          | /project/:projectId/overview    | pages/project/overview.vue                | 项目履约     | 项目管理       | 项目详情上下文       | 路由兼容   |
| 项目成员          | /project/:projectId/members     | pages/project/members.vue                 | 项目履约     | 项目管理       | 项目详情上下文       | 路由兼容   |
| 编辑项目          | /project/:projectId/edit        | pages/project/edit.vue                    | 项目履约     | 项目管理       | 项目详情上下文       | 路由兼容   |
| 合作方管理        | /partner                        | pages/partner/index.vue                   | 基础资料     | 合作方管理     | —                    | 工作区调整 |
| 现场日报          | /site/daily-log                 | pages/site/daily-log.vue                  | 项目履约     | 计划与现场     | 现场日报             | Tab合并    |
| 项目计划          | /project-schedule               | pages/project-schedule/index.vue          | 项目履约     | 计划与现场     | 项目计划             | Tab合并    |
| 质量安全整改      | /quality-safety                 | pages/quality-safety/index.vue            | 项目履约     | 质量与技术     | 质量安全整改         | Tab合并    |
| 图纸 RFI 技术闭环 | /technical-management           | pages/technical-management/index.vue      | 项目履约     | 质量与技术     | 图纸 RFI 技术闭环    | Tab合并    |
| 项目竣工收尾      | /project-closeout               | pages/project-closeout/index.vue          | 项目履约     | 项目收尾       | 竣工收尾             | 工作区调整 |
| 供应商招采履约    | /supplier-sourcing              | pages/supplier-sourcing/index.vue         | 供应链与物资 | 供应商管理     | 供应商招采履约       | 工作区调整 |
| 投标成本          | /bid-cost                       | pages/bid-cost/index.vue                  | 商务合约     | 投标与成本目标 | 投标成本             | Tab合并    |
| 组织架构          | /org                            | pages/org/index.vue                       | 基础资料     | 组织架构       | —                    | 工作区调整 |
| 分包任务          | /subcontract/task               | pages/subcontract/task.vue                | 分包与结算   | 分包履约       | 分包任务             | Tab合并    |
| 分包计量          | /subcontract/measure            | pages/subcontract/measure.vue             | 分包与结算   | 分包履约       | 分包计量             | Tab合并    |
| 采购订单          | /purchase/order                 | pages/purchase/order.vue                  | 供应链与物资 | 采购执行       | 采购订单             | Tab合并    |
| 材料验收          | /purchase/receipt               | pages/receipt/index.vue                   | 供应链与物资 | 采购执行       | 材料验收             | Tab合并    |
| 付款申请          | /payment/application            | pages/payment/index.vue                   | 资金财务     | 收付款与发票   | 付款申请             | Tab合并    |
| 费用申请          | /payment/expense                | pages/expense/index.vue                   | 资金财务     | 收付款与发票   | 费用申请             | Tab合并    |
| 项目预算          | /budget                         | pages/budget/index.vue                    | 商务合约     | 预算与产值     | 项目预算             | Tab合并    |
| 资金运营          | /finance-operations             | pages/finance-operations/index.vue        | 资金财务     | 资金运营       | 资金运营             | Tab合并    |
| 收入与回款        | /revenue                        | pages/revenue/index.vue                   | 资金财务     | 收付款与发票   | 收入与回款           | Tab合并    |
| 产值计量          | /production-measurement         | pages/production-measurement/index.vue    | 商务合约     | 预算与产值     | 产值计量             | Tab合并    |
| 资金日记账        | /cash-journal                   | pages/cash-journal/index.vue              | 资金财务     | 资金运营       | 资金日记账           | Tab合并    |
| 会计凭证          | /accounting-entry               | pages/accounting-entry/index.vue          | 资金财务     | 财务核算       | 会计凭证             | Tab合并    |
| 项目资金预测      | /cash-forecast                  | pages/cash-forecast/index.vue             | 资金财务     | 资金运营       | 项目资金预测         | Tab合并    |
| 财务核算与月结    | /financial-close                | pages/financial-close/index.vue           | 资金财务     | 财务核算       | 财务核算与月结       | Tab合并    |
| 仓库管理          | /inventory/warehouse            | pages/inventory/warehouse.vue             | 供应链与物资 | 仓储库存       | 仓库管理             | Tab合并    |
| 库存台账          | /inventory/stock                | pages/inventory/stock.vue                 | 供应链与物资 | 仓储库存       | 库存台账             | Tab合并    |
| 出入库            | /inventory/transaction          | pages/inventory/transaction.vue           | 供应链与物资 | 仓储库存       | 出入库               | Tab合并    |
| 采购申请          | /inventory/purchase-request     | pages/inventory/purchase-request.vue      | 供应链与物资 | 采购执行       | 采购申请             | Tab合并    |
| 领料申请          | /inventory/material-requisition | pages/requisition/index.vue               | 供应链与物资 | 现场领用       | 领料申请             | 工作区调整 |
| 发票管理          | /invoice                        | pages/invoice/index.vue                   | 资金财务     | 收付款与发票   | 发票管理             | Tab合并    |
| 材料字典          | /material/dictionary            | pages/material/dictionary.vue             | 基础资料     | 物资主数据     | 材料字典             | 工作区调整 |
| 预警中心          | /alert                          | pages/alert/index.vue                     | 工作台       | 预警中心       | 预警中心             | 工作区调整 |
| 我的待办          | /approval/todo                  | pages/approval/todo.vue                   | 工作台       | 我的工作       | 待我处理             | Tab合并    |
| 我的已办          | /approval/done                  | pages/approval/todo.vue                   | 工作台       | 我的工作       | 我已处理             | Tab合并    |
| 抄送我的          | /approval/cc                    | pages/approval/todo.vue                   | 工作台       | 我的工作       | 抄送我的             | Tab合并    |
| 我发起            | /approval/mine                  | pages/approval/todo.vue                   | 工作台       | 我的工作       | 我发起               | Tab合并    |
| 审批流程          | /approval/process               | pages/approval/process.vue                | 系统管理     | 流程配置       | 审批流程             | 工作区调整 |
| 审批详情          | /approval/:instanceId           | pages/approval/detail.vue                 | 工作台       | 我的工作       | 审批详情上下文       | 路由兼容   |
| 字典管理          | /system/dict                    | pages/system/dict/index.vue               | 系统管理     | 系统配置       | 字典管理             | 工作区调整 |
| 用户管理          | /system/users                   | pages/system/users/index.vue              | 系统管理     | 访问控制       | 用户管理             | Tab合并    |
| 数据管理          | /system/data                    | pages/system/data/index.vue               | 系统管理     | 数据维护       | 非生产环境数据维护   | 工作区调整 |
| 角色管理          | /system/roles                   | pages/system/roles/index.vue              | 系统管理     | 访问控制       | 角色管理             | Tab合并    |
| 权限清单          | /system/permissions             | pages/system/permissions/index.vue        | 系统管理     | 访问控制       | 权限清单             | Tab合并    |
| 操作审计          | /system/audit                   | pages/system/audit/index.vue              | 系统管理     | 操作审计       | 操作审计             | 工作区调整 |
| 业务单据模板      | /system/document-templates      | pages/system/document-templates/index.vue | 系统管理     | 系统配置       | 业务单据模板         | Tab合并    |
| 个人中心          | /profile                        | pages/profile/index.vue                   | 全局账户工具 | 个人账户       | 个人资料             | 保留       |
| 设置              | /settings                       | pages/settings/index.vue                  | 全局账户工具 | 个人账户       | 偏好设置             | 保留       |
| 帮助              | /help                           | pages/help/index.vue                      | 全局账户工具 | 帮助与支持     | 帮助                 | 保留       |
| 页面不存在        | /:pathMatch(._)_                | pages/error/404.vue                       | 全局公共     | 错误页         | —                    | 保留       |

## 5. 路由兼容策略

### 5.1 总原则

V2 的业务分组不要求先改 URL。第一阶段的导航框架只将现有 URL 重新归入八个业务域；这既保留书签、浏览器历史、审批跳转和业务页面内的 router.push，也避免把信息架构调整扩大为路由重写。

| 策略     | 使用条件                                             | V2 要求                                                          |
| -------- | ---------------------------------------------------- | ---------------------------------------------------------------- |
| 保留     | 登录、账户工具、错误页及已正确表达对象上下文的链接   | 原路径继续是正式可访问链接。                                     |
| 暂不调整 | 第一阶段所有业务路由                                 | 新导航直接指向当前路径；不新增 redirect 或 alias。               |
| redirect | 第二阶段获批改造为新规范路径后                       | 旧路径保留路由记录，并将动态参数、query、hash 完整转发到新路径。 |
| alias    | 仅当旧路径与新路径使用相同组件、相同权限和相同语义时 | 不作为默认迁移手段，避免同一业务状态出现两个正式 URL。           |

### 5.2 未来规范路径候选

下表是第二阶段以后可讨论的规范路径候选，不是本轮 router 修改清单。所有业务行在 Phase 1 的实际策略均为“暂不调整”。

| 旧路由                                                          | 建议 V2 规范路径                                                | Phase 1 策略 | 获批改名后的策略                         |
| --------------------------------------------------------------- | --------------------------------------------------------------- | ------------ | ---------------------------------------- |
| /dashboard                                                      | /workbench/dashboard                                            | 暂不调整     | redirect                                 |
| /dashboard/reports                                              | /workbench/reports                                              | 暂不调整     | redirect                                 |
| /alert                                                          | /workbench/alerts                                               | 暂不调整     | redirect                                 |
| /approval/todo、/done、/cc、/mine                               | 保留 /approval/{tab}                                            | 保留         | 保留；现有 URL 已满足 Tab 深链接。       |
| /approval/process                                               | /system/workflow/process                                        | 暂不调整     | redirect                                 |
| /approval/:instanceId                                           | 保留 /approval/:instanceId                                      | 保留         | 保留；审批跨业务对象，保持独立详情链接。 |
| /project/list                                                   | /delivery/projects                                              | 暂不调整     | redirect                                 |
| /project/:projectId/overview、/members、/edit                   | /delivery/projects/:projectId/{overview,members,edit}           | 暂不调整     | redirect                                 |
| /site/daily-log、/project-schedule                              | /delivery/execution/{daily-log,schedule}                        | 暂不调整     | redirect                                 |
| /quality-safety、/technical-management                          | /delivery/control/{quality-safety,technical}                    | 暂不调整     | redirect                                 |
| /project-closeout                                               | /delivery/closeout                                              | 暂不调整     | redirect                                 |
| /contract/\*\*、/variation/order                                | /commercial/contracts/\*\*、/commercial/contracts/variations    | 暂不调整     | redirect                                 |
| /bid-cost、/cost-target/\*\*                                    | /commercial/cost-target/{bid,cost-target/\*\*}                  | 暂不调整     | redirect                                 |
| /cost/ledger、/summary、/control                                | /commercial/cost/{ledger,summary,control}                       | 暂不调整     | redirect                                 |
| /budget、/production-measurement                                | /commercial/value/{budget,measurement}                          | 暂不调整     | redirect                                 |
| /supplier-sourcing                                              | /supply/suppliers/sourcing                                      | 暂不调整     | redirect                                 |
| /inventory/purchase-request、/purchase/order、/purchase/receipt | /supply/procurement/{request,order,receipt}                     | 暂不调整     | redirect                                 |
| /inventory/warehouse、/stock、/transaction                      | /supply/inventory/{warehouse,stock,transaction}                 | 暂不调整     | redirect                                 |
| /inventory/material-requisition                                 | /supply/requisition                                             | 暂不调整     | redirect                                 |
| /subcontract/task、/subcontract/measure                         | /subcontract/performance/{task,measure}                         | 暂不调整     | redirect                                 |
| /settlement/list、/settlement/:id                               | /subcontract/settlements/{list,:id}                             | 暂不调整     | redirect                                 |
| /payment/application、/payment/expense、/revenue、/invoice      | /finance/receivables-payables/{payment,expense,revenue,invoice} | 暂不调整     | redirect                                 |
| /finance-operations、/cash-journal、/cash-forecast              | /finance/cash/{operations,journal,forecast}                     | 暂不调整     | redirect                                 |
| /accounting-entry、/financial-close                             | /finance/accounting/{entries,close}                             | 暂不调整     | redirect                                 |
| /partner、/org、/material/dictionary、/cost/subject             | /master-data/{partners,organization,materials,cost-subjects}    | 暂不调整     | redirect                                 |
| /system/\*\*                                                    | /system/\*\*                                                    | 保留         | 保留；已有系统域前缀。                   |
| /profile、/settings、/help、/login、/403、/:pathMatch(._)_      | 保留原路径                                                      | 保留         | 保留。                                   |

### 5.3 路由迁移验收规则

- 旧 URL 直接打开、刷新、浏览器后退/前进、query 和 hash 均保持可用。
- 含 :id、:projectId、:instanceId 的旧路由必须保留原参数值。
- 新旧路径不得出现不同 permission、不同 Tab、不同页面标题或不同数据作用域。
- 不得以 alias 掩盖权限变化；权限语义变化必须另立安全评审。

## 6. 权限迁移方案

### 6.1 当前前端权限实现

| 层级       | 当前实现                                     | 已确认事实                                                                                                            |
| ---------- | -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| 身份信息   | types/user.ts、stores/user.ts                | UserInfo 含 roles 与 permissions 两个字符串数组。                                                                     |
| 功能权限   | userStore.hasPermission                      | 支持通配权限 \* 或精确权限码。                                                                                        |
| 路由权限   | router/index.ts                              | ROUTE_PERMISSION_MAP 与现有 route.meta.permission 经 applyRoutePermissions 汇合；handleAuthGuard 恢复用户信息后校验。 |
| 管理员边界 | router/index.ts、navigation.ts、页面局部逻辑 | ADMIN、SUPER_ADMIN 对 adminOnly 和部分功能判断有既有旁路。                                                            |
| 菜单权限   | layouts/components/SidebarMenu.vue           | navigationItems 的 permission、adminOnly 经 isMenuVisible 过滤。                                                      |
| 按钮权限   | directives/permission.ts、页面局部 computed  | v-permission 缺权时删除元素；部分页面以 hasPermission 或管理员旁路控制按钮。                                          |
| 数据权限   | 当前审计范围内未发现统一前端数据作用域模型   | UserInfo 不含数据范围字段；system/data 页面是非生产环境清库，不是数据权限配置。                                       |

当前实现有三个独立描述源：route.meta / ROUTE_PERMISSION_MAP、navigation.ts、页面按钮判断。V2 迁移必须保持三者现有结果一致，不能只改菜单而削弱路由守卫，也不能借导航重构更改管理员边界。

### 6.2 V2 权限链

```text
角色（roles）
  └─ 默认首页、常用工作区排序、角色展示名
       ↓
功能权限（permissions）
  └─ 路由与业务能力的唯一可访问依据
       ↓
菜单权限
  └─ 工作区入口可见性
       ↓
Tab 权限
  └─ 每个 Tab 的独立路由 meta.permission
       ↓
按钮权限
  └─ v-permission 或同一 permission 描述符
       ↓
数据权限
  └─ 由后端按项目/组织/租户等真实范围裁决；前端只消费已授权结果
```

### 6.3 强制规则

1. 禁止在新增导航、工作区或 Tab 中写 if(role === "xxx") 一类角色硬编码。
2. roles 只能选择默认入口与排序，不能代替 permission 判断路由、菜单、Tab 或按钮的可访问性。
3. 每个 V2 菜单叶子和 Tab 必须声明或继承对应的 permission；菜单可见不等于接口可访问。
4. 第一阶段保留现有 adminOnly、ADMIN、SUPER_ADMIN 与页面内管理员旁路，不在导航改造中静默删除或放宽。
5. 第二阶段若要消除既有管理员旁路，必须先完成权限码归一、后端接口复核和独立安全验收。
6. 当前没有证据表明存在统一前端数据权限模型；不得把 system/data 误称为“数据权限”，也不得在导航层虚构项目/组织数据范围。

### 6.4 迁移实现方式

| 对象     | Phase 1 要求                                                            | 后续归一目标                                          |
| -------- | ----------------------------------------------------------------------- | ----------------------------------------------------- |
| 路由     | 保留现有 ROUTE_PERMISSION_MAP、meta.permission、adminOnly 和 guard 行为 | 由统一路由描述符生成 route meta。                     |
| 侧栏菜单 | 仅变更分组与标签时，复用当前 permission/adminOnly 值                    | 菜单叶子读取同一描述符，不再手工复制权限码。          |
| Tab      | 审批 Tab 保留现有独立 URL 与 permission；新 Tab 不得只做前端状态切换    | 每个 Tab 有独立 route name、meta.permission、tabKey。 |
| 按钮     | 保留 v-permission 与现有页面局部判断                                    | 按钮权限码与路由/Tab 权限在描述符中可追溯。           |
| 数据     | 不改现有接口与数据过滤逻辑                                              | 仅在后端确认数据范围模型后增加前端消费层。            |

## 7. Tab 规范

### 7.1 使用边界

- 一个工作区最多 5 个 Tab；超过 5 个必须拆分工作区。
- Tab 只能表示同一对象的状态/视图，或有明确先后关系的连续流程。
- 新增、编辑、详情、打印、附件、审批详情、历史版本均为上下文路由，不计入工作区 Tab。
- 不得仅因页面数量多而把“无共同对象、无连续流程”的页面塞入同一 Tab。

### 7.2 必须满足的技术语义

| 要求       | 规范                                                                     |
| ---------- | ------------------------------------------------------------------------ |
| 独立 URL   | 每个 Tab 都有唯一 URL 和 route name。                                    |
| 独立权限   | 每个 Tab 有独立 meta.permission；无权时路由守卫拒绝，菜单/Tab 同步隐藏。 |
| 可刷新     | Tab 状态、筛选条件和对象标识从 URL 参数或 query 恢复。                   |
| 浏览器历史 | 用户切 Tab 必须使用 router.push；后退/前进可回到先前 Tab。               |
| 深链接     | 复制 URL 能直接打开目标 Tab；不能依赖仅保存在内存中的 activeTab。        |
| 参数保持   | Tab 切换时保留必要的项目、对象、筛选、分页 query。                       |

### 7.3 审批中心参考实现

当前 approval/todo.vue 已符合 V2 Tab 的关键模式：

1. 以 route.meta.approvalTab 初始化 activeTab。
2. Tab 切换时执行 router.push 到 /approval/{tab}。
3. 监听 route.meta.approvalTab，在刷新、深链或浏览器历史变更时重新同步页面状态。
4. todo、done、cc、mine 四个 Tab 使用不同的后端查询入口，并共享一个 Vue 工作区文件。

后续采购执行、仓储库存、分包履约等工作区可复用此模式，但不得复制审批业务字段、API 或权限码。

## 8. 实施计划

实施路由：主线程直接实施。范围为单一前端壳与导航描述符，未改变后端、数据库、金额、租户或数据权限；各阶段均由当前源码与自动化测试复核。

### Phase 0：文档确认

状态：已完成。用户已确认八个一级域、工作区/Tab 方向及正式计划书命名。

| 项目     | 内容                                                                                             |
| -------- | ------------------------------------------------------------------------------------------------ |
| 修改范围 | 仅确认本文件中的八个一级菜单、迁移矩阵、命名、路由策略和权限边界。                               |
| 风险     | 未确认就实施会把 V2 工作区名称、路由候选或角色默认入口误当成既定需求。                           |
| 验收标准 | 69 条页面路由都有唯一 V2 归属；任何页面不被删除；采购执行、审批我的工作等 Tab 分组获得产品确认。 |

### Phase 1：导航框架调整

状态：已完成。侧栏已收敛为 8 个一级域与 30 个二级工作区；个人资料、偏好设置、帮助、通知和待办进入全局工具栏。

| 项目     | 内容                                                                                                                    |
| -------- | ----------------------------------------------------------------------------------------------------------------------- |
| 修改范围 | 后续仅调整 navigation.ts、SidebarMenu、BasicLayoutAsync 及其测试；新侧栏显示 8 个一级业务域，个人账户移入全局账户工具。 |
| 风险     | 静态导航与 router 权限描述不一致；折叠态选中逻辑、移动端入口或深链高亮退化。                                            |
| 验收标准 | 8 个一级菜单均可展开；48 个现有业务入口均有 V2 归属；全部旧路径仍可访问；无权入口不显示且直接访问仍由现有 guard 拒绝。  |

### Phase 2：工作区 Tab 改造

状态：已完成。54 个 Tab 描述符复用既有独立 URL 和路由权限；多 Tab 工作区由统一外壳渲染，审批保留页内既有实现。

| 项目     | 内容                                                                                                                      |
| -------- | ------------------------------------------------------------------------------------------------------------------------- |
| 修改范围 | 后续为采购执行、仓储库存、分包履约、成本核算、资金运营、访问控制等工作区建立路由化 Tab 外壳；复用当前页面文件和业务 API。 |
| 风险     | Tab 与 URL 不同步、筛选条件丢失、权限只在侧栏隐藏、同页不同 Tab 数据污染。                                                |
| 验收标准 | 每个 Tab 小于等于 5；每个 Tab 有独立 URL、permission、刷新恢复、后退/前进与深链测试；现有详情/新增/编辑路由仍可达。       |

### Phase 3：核心业务流程优化

状态：已完成。采购、库存、分包、合同/变更/成本、收付款、资金运营和访问控制流程均可通过按业务顺序排列的路由化 Tab 连续进入，切换保留 query 与 hash。

| 项目     | 内容                                                                                                              |
| -------- | ----------------------------------------------------------------------------------------------------------------- |
| 修改范围 | 后续按本矩阵梳理采购申请→采购订单→材料验收、仓储库存、分包任务→分包计量、合同→变更→成本等现有流程入口和页面跳转。 |
| 风险     | 跨模块跳转未保留项目/合同上下文；财务、审批和单据接口的权限与数据边界被误改。                                     |
| 验收标准 | 每条已列流程均能从起始页进入下一步骤；业务上下文、权限拒绝和旧 URL 回归通过；不新增未经证实的业务功能。           |

### Phase 4：对象中心建设

状态：已完成。项目、合同、结算详情获得对象上下文导航；详情、新增、编辑仍不进入侧栏，原深链接与权限不变。

| 项目     | 内容                                                                                                      |
| -------- | --------------------------------------------------------------------------------------------------------- |
| 修改范围 | 后续强化项目、合同、结算等已有对象详情的上下文导航，整合当前 overview、members、detail、edit 等对象路由。 |
| 风险     | 将详情页面错误升格为全局侧栏入口；对象级数据权限、附件/审批/单据关联缺少后端证据。                        |
| 验收标准 | 对象详情保留独立深链接；上下文页不污染侧栏；对象相关 Tab 和操作仍由权限、路由守卫及后端接口共同保护。     |

## 9. 实施前必须处理的审计结论

| 结论                                                     | 证据                                                                                                      | 实施结果                                                                      |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| 当前只有 7 个一级侧栏域，目标需要 8 个                   | navigation.ts 顶层 navigationItems 为工作台、项目经营、采购库存、分包计量、结算收付、基础资料、流程与系统 | 已重组为固定 8 个一级域。                                                     |
| 当前菜单、路由、权限为三套独立描述，存在同步风险         | navigation.ts、router/index.ts、SidebarMenu.vue 分别维护                                                  | 菜单与 Tab 已读取同一导航描述符；路由守卫保持独立安全边界并由一致性测试约束。 |
| 动态利润控制没有明确静态导航归属                         | /cost/control 已注册，未列为 navigationItems 叶子或 matchPrefixes                                         | 已纳入“商务合约 > 成本核算与控制”。                                           |
| 审批已办、抄送、我发起已具备路由化 Tab，但侧栏归属不一致 | approval/todo.vue 与 navigation.ts                                                                        | 已统一为“工作台 > 我的工作”四个 Tab，继续复用页内实现。                       |
| 个人中心、设置、帮助是隐藏路由，但桌面端全局入口不完整   | router/index.ts 与 BasicLayoutAsync.vue                                                                   | 已进入桌面全局账户菜单并补齐帮助入口；未创建业务一级菜单。                    |
| 当前前端没有统一数据权限模型                             | UserInfo 仅含 roles/permissions；system/data 为清库维护页                                                 | Phase 0/后续安全专项：确认后端数据范围前，不在菜单或 Tab 层假设数据权限。     |
| 现有页面存在 ADMIN、SUPER_ADMIN 的局部旁路               | router 守卫、SidebarMenu 与多个页面 computed 判断                                                         | 保留既有边界；导航访问判断集中到 useNavigationAccess。                        |

## 10. 交付验收与非目标

### 10.1 交付验收

- 已覆盖当前 70 条页面路由、65 个独立路由视图、原 49 个侧栏叶子入口与 54 个 V2 Tab 描述符。
- 已落地固定的 8 个一级菜单、30 个二级工作区、路由化 Tab、对象上下文导航、路由兼容和权限一致性约束。
- 未宣称当前不存在的“全局搜索、收藏、最近访问、登录设备、统一数据权限”等功能已经存在。
- 未提出删除任何页面，也未将详情、新增、编辑页面错误改为侧栏菜单。

### 10.2 非目标

- 本主线不修改业务页面、业务 API、后端权限、数据库或页面文件位置。
- 本主线不新增功能权限码、不修改 ADMIN/SUPER_ADMIN 的既有语义。
- 本文件不把 V2 规范路径候选视为已批准的 URL 改造。

## 11. 实际交付物与验收证据

### 11.1 实现文件

| 文件                                                              | 作用                                                      |
| ----------------------------------------------------------------- | --------------------------------------------------------- |
| frontend-admin/src/router/navigation.ts                           | 八大业务域、工作区、Tab、权限描述符与路径归属解析。       |
| frontend-admin/src/composables/useNavigationAccess.ts             | 统一菜单与 Tab 的 permission/adminOnly 可见性判断。       |
| frontend-admin/src/layouts/components/SidebarMenu.vue             | 仅展示一级业务域与二级工作区，按首个有权 Tab 导航。       |
| frontend-admin/src/layouts/components/WorkspaceTabs.vue           | 路由化工作区 Tab；保留 query/hash，支持刷新、历史与深链。 |
| frontend-admin/src/layouts/components/ObjectContextNavigation.vue | 项目、合同、结算对象上下文导航。                          |
| frontend-admin/src/layouts/BasicLayoutAsync.vue                   | 桌面全局工具栏、账户菜单、工作区与对象导航装配。          |

### 11.2 自动化验收范围

- navigation-v2.test.ts：固定八域、Tab 上限、默认路径、路由存在性、权限一致性与代表性上下文归属。
- SidebarMenu.test.ts：八域/工作区结构、选中与展开、管理员边界、权限过滤及默认入口。
- WorkspaceTabs.test.ts：采购三段流程、权限过滤、router.push、query/hash 保持及审批页内 Tab 去重。
- ObjectContextNavigation.test.ts：项目对象路由、对象权限与结算返回路径。
- BasicLayout 系列测试：全局账户入口、帮助、通知、移动端和可访问性。
- router.test.ts：全部旧路由、路由守卫与第48条主线新增模板入口保持有效。

自动化结果：相关导航/布局测试 8 个文件、77 项通过；前端全量 129 个文件、727 项通过；ESLint 0 error；生产构建与包体门禁通过。

### 11.3 运行态验收

| 项目       | 实际结果                                                                                                          |
| ---------- | ----------------------------------------------------------------------------------------------------------------- |
| 后端健康   | http://localhost:8080/api/actuator/health 返回 200。                                                              |
| 前端入口   | http://localhost:5173 返回 200；刷新前端并完成 180 秒稳定等待。                                                   |
| 八个一级域 | 工作台、项目履约、商务合约、供应链与物资、分包与结算、资金财务、基础资料、系统管理全部真实渲染。                  |
| 采购执行   | `/inventory/purchase-request` 显示采购申请/采购订单/材料验收；切换到 `/purchase/order` 后浏览器返回恢复采购申请。 |
| 深链刷新   | `/purchase/receipt?projectId=7` 刷新后保持材料验收激活态和采购执行工作区。                                        |
| 对象中心   | `/project/1/overview` 显示项目总览、项目成员、编辑项目与返回项目列表。                                            |
| 全局账户   | 个人资料、偏好设置、帮助与支持、退出登录均位于账户菜单；侧栏无个人中心一级域。                                    |
| 控制台     | 验收页面无 warning/error。                                                                                        |

### 11.4 回滚边界

- 导航重构不改旧 URL、route name、业务 Vue 文件、API 或数据库，回滚只涉及第 11.1 节前端壳与导航文件。
- 第48条主线的 `/system/document-templates` 路由和业务页面不属于本主线回滚对象；本主线只提供其 V2 工作区归属。
