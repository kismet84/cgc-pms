# CGC-PMS 项目资金支出闭环业务标准

状态：Implemented Baseline
基线日期：2026-07-16
适用范围：项目资金支出主线
事实基线：master 当前源码、数据库迁移、前后端测试
结论：第 9 章 P0—P3 开发工作包已完成并通过本地全量自动化与运行态验收；生产启用仍受历史数据处置和外部平台联调两项已登记前置约束

> 本文是 CGC-PMS “项目资金支出”主线的唯一业务标准。后续需求、数据迁移、接口、页面、审批、测试和上线验收若与本文冲突，必须先修订本文并完成评审，禁止在实现阶段自行改变金额口径、状态机或追溯关系。

## 1. 目标、边界与强制原则

### 1.1 业务目标

任何一笔项目支出必须形成以下正向链路，并可从任一财务事实反向追溯：

项目 → 项目预算 → 合同 → 合同成本计划 → 业务申请 → 完整性校验 → 审批 → 财务付款 → 现金日记 → 发票/付款凭证 → 成本与预算更新 → 驾驶舱

最低追溯要求：

现金日记 → 付款记录 → 付款申请 → 原始费用/结算依据 → 审批实例与审批记录 → 合同 → 项目 → 预算占用与消耗记录。

### 1.2 非目标

- 不新增与资金支出闭环无关的 BIM、AI、现场施工、采购预测或外部协同功能。
- P0 不建设独立总账、税务平台或银行直联平台；只补齐付款凭证生成所需的最小会计关系。
- 不以驾驶舱二次计算替代业务事实表，不以页面拼接替代数据库关系。
- 不允许通过人工补录现金日记来替代付款成功后的系统生成。
- 不允许删除已审批、已付款、已归档或已过账的财务事实；纠错必须采用撤回、作废、红冲或冲销。

### 1.3 金额口径

| 名称 | 唯一口径 |
| --- | --- |
| 项目预算 | 已审批且生效的项目预算版本金额 |
| 预算占用 | 已提交审批但尚未被驳回、撤回、作废或支付完成释放的申请金额 |
| 预算消耗 | 成功付款并形成有效现金日记的金额；红冲时反向恢复 |
| 预算可用余额 | 生效预算 - 已占用 - 已消耗 |
| 合同当前金额 | 原合同金额 + 已生效正式合同变更（CT_CHANGE） |
| 已确认现场签证 | 业主已确认的成本向现场签证（VAR_ORDER）；与正式合同变更是不同事实，禁止同一事项在两类单据重复登记 |
| 最终结算金额 | 已审批计量 + 已确认现场签证 - 终期扣款；合同当前金额只作为履约上限，不与已审批产值重复相加；新单统一使用 `APPROVED_MEASURE_VARIATION_DEDUCTION_V2` 口径，历史 V1 定案数据不直接覆盖 |
| 合同累计付款 | 有效 SUCCESS 付款记录合计 - 已冲销付款合计 |
| 申请可付余额 | 审批金额 - 有效累计付款 |
| 项目实际成本 | 已确认且未冲销的成本明细合计，不等于付款金额 |
| 现金余额 | 期初余额 + 已归档收入 - 已归档支出；待归档流水不进入余额 |
| 项目利润 | 已确认收入 - 动态成本；未确认收入不得进入已实现利润 |

所有金额字段统一 DECIMAL(18,2)，计算中间值可保留更高精度，但入账必须按 HALF_UP 保留两位。禁止使用浮点数。

## 2. 当前业务完成度分析

### 2.1 评估方法

> 本章保留 2026-07-16 实施前源码扫描基线，用于说明缺口来源；当前实施结果和上线裁决以第 13 章为准。

| 等级 | 定义 |
| --- | --- |
| C0 | 对象、接口、页面和测试均缺失 |
| C1 | 仅有页面、静态展示、种子数据或不可达接口 |
| C2 | 有 CRUD 或局部规则，但缺少完整状态、关联或关键校验 |
| C3 | 单域内业务基本可用，有接口和测试，但尚未与上下游闭环 |
| C4 | 跨域事务、审计、反向追溯和自动化验收全部成立 |

实施前裁决：C2。模块覆盖较高，但当时 P0 资金闭环未形成，不具备以本主线名义上线的条件。

### 2.2 节点完成度矩阵

| 节点 | 当前等级 | 已实现证据 | 缺失或不成立 | 裁决 |
| --- | --- | --- | --- | --- |
| 项目 Project | C3 | pm_project、项目 CRUD、成员、归档依赖校验、页面和测试存在 | 无受控 ACTIVE/SUSPENDED/CLOSED 状态机；update 可直接覆盖状态；付款链不校验项目暂停/归档 | 域内可用 |
| 项目预算 Budget | C1 | pm_project.target_cost 与 CostTarget/CostTargetItem 可表达目标成本 | 没有 ProjectBudget、预算科目额度、占用/释放/消耗台账、预算余额和并发锁 | 实质缺失 |
| 合同 Contract | C3 | 合同 CRUD、付款条款、审批、变更后 currentAmount、锁定成本、页面与测试 | 创建/付款未强制合同必须已审批且履约中；数据库无 project FK；关闭/终止对付款无统一门禁 | 域内可用 |
| 合同预算/成本计划 | C2 | 合同明细、付款条款、CostTarget、CostItem、CostSummary、合同审批后锁定成本 | 没有合同预算分配；成本目标与合同支出额度没有直接关系；目标成本可绕过审批直接 activate | 局部可用 |
| 费用申请 Expense | C0 | 无 | 无实体、表、接口、页面、状态、审批处理器和测试 | 缺失 |
| 结算申请 Settlement | C3 | 结算实体、明细、来源聚合、审批、合同回写、页面和测试 | 提交不要求附件；合同关闭/项目暂停门禁缺失；结算金额存在重复计算变更的 P0 风险 | 有阻塞缺陷 |
| 付款申请 PaymentApplication | C3 | 项目/合同一致性、付款依据、金额合计、合同余额、付款比例、审批、驳回与重提 | 不校验项目状态、合同履约状态、项目预算、附件、费用分类、付款对象必填；不支持费用/结算显式来源 FK | 有阻塞缺口 |
| 审批 Approval | C3 | 多级节点、同意、驳回、撤回、重新提交、转办、加签、幂等、审批记录和测试 | 业务对象与审批实例仍为多态弱关联；部分提交服务缺直接覆盖；业务完整性校验不完整 | 基础能力可复用 |
| 财务付款 Payment | C3 | 单一 writeback 入口、审批通过门禁、合同余额与申请余额、外部流水幂等、并发锁、级联更新 | 缺付款账户、精确付款时间、失败/冲销业务入口；不校验项目/合同状态；付款凭证号可空 | 有阻塞缺口 |
| 现金日记 CashJournal | C3 | 付款成功自动生成一条 PENDING_ARCHIVE 支出流水；账户、附件、归档、余额、红冲、审计测试较完整 | 仅以 sourceType/sourceId 间接关联 PayRecord；无申请/审批显式 FK；付款账户没有从付款记录传入 | 域内强、链路弱 |
| 发票 Invoice | C3 | 必须关联 PayRecord、重复发票校验、核验、OCR、页面和测试 | payApplicationId 可与 PayRecord 不一致；无项目/合同显式 FK；无一票多付/一付多票分配关系；附件与发票记录不是同一事务 | 域内可用 |
| 付款凭证/附件 | C2 | 通用 SysFile 支持业务对象绑定；现金日记归档强制至少一个附件 | 没有 document_type；银行回单、付款凭证、合同附件无法结构化区分；付款申请提交不强制附件 | 局部可用 |
| 会计凭证 Voucher | C1 | AccountingEntry/Line、查询、过账、冲销 API 与页面存在 | EntryGenerationStrategy 没有任何实现；付款/现金日记不会自动生成凭证；页面明确提示生成入口未开放 | 页面/API 壳 |
| 自动更新 | C2 | 合同 paidAmount、申请 actualPayAmount/payStatus、CostSummary.paidAmount、现金余额可更新 | 无预算余额；CostSummary 把项目总付款写到项目下所有汇总行；发票、凭证、驾驶舱未统一事务/事件收敛 | 不可作为闭环 |
| 驾驶舱 Dashboard | C2 | 合同、成本、付款、利润等多角色视图和测试存在 | 财务“已批未付”取自非 SUCCESS 的 PayRecord，而付款服务只创建 SUCCESS；无现金日记现金流、预算执行率和闭环追溯读模型 | 口径不可靠 |
| 全链路追溯 | C1 | 页面可从现金日记 sourceId 跳向付款页，部分详情页各自查询来源 | 没有统一 trace API；现金日记不能一次返回付款、审批、申请、原始依据、合同、项目、预算；数据库无 FK | 缺失 |

### 2.3 当前已实现的关键业务能力

1. 项目、合同、目标成本、结算、付款申请、审批、付款记录、现金日记、发票、驾驶舱均存在后端实体、接口或服务。
2. 付款写回是唯一付款记录创建入口，并通过 external_txn_no 实现幂等；合同与申请行使用锁避免并发超付。
3. 付款成功后在同一事务中调用 CashJournalService.createPendingFromPayRecord，现金日记生成失败会回滚付款。
4. 付款写回会更新合同累计付款、付款申请状态与项目 CostSummary.paidAmount。
5. 现金日记具备附件归档、账户余额、负余额拒绝、红冲、撤销归档和变更日志。
6. 审批引擎支持多级审批、驳回、撤回、重新提交、转办、加签、幂等与记录留痕。
7. 发票创建强制绑定有效付款记录，同租户发票号唯一，已核验发票不可编辑删除。

### 2.4 只有页面、展示或不可用业务

| 能力 | 现状 |
| --- | --- |
| 会计凭证生成 | 页面、查询、过账、冲销存在，但没有 EntryGenerationStrategy 实现，任何生成请求均会返回来源未配置 |
| 项目附件 | 项目编辑页显示“当前接口暂未提供附件字段”的占位说明 |
| 驾驶舱已批未付 | 页面有指标，但数据源使用非 SUCCESS PayRecord；当前权威付款入口只写 SUCCESS，指标不能代表付款申请 |
| 结算附件 | 有只读附件页签和通用文件能力，但结算提交没有附件完整性门禁 |
| 全链路追溯 | 有局部跳转和局部来源查询，没有统一业务对象或 API |

### 2.5 数据关联与数据库关系缺口

当前 closed-loop 相关表虽然普遍有 project_id、contract_id、source_id 等列和普通索引，但 MySQL/H2 迁移中没有为这些表建立外键。全库检索仅发现 sub_task.predecessor_task_id 存在外键，资金闭环表之间为零数据库 FK。

重点弱关联如下：

| 当前关系 | 当前实现 | 风险 |
| --- | --- | --- |
| Contract → Project | ct_contract.project_id 普通列 | 可产生孤儿合同或跨租户错配 |
| CostTarget → Project | cost_target.project_id 普通列 | 目标成本可引用不存在项目 |
| PaymentApplication → Project/Contract/Partner | 应用层创建时部分校验 | partner 可空；历史数据与批量写入无 DB 保护 |
| PaymentApplicationBasis → 来源 | basis_type + basis_id 多态关系 | 未知 basis_type 会绕过来源校验，数据库无法保证来源存在 |
| Approval → 业务对象 | business_type + business_id 多态关系 | 无 FK；删除业务对象会留下孤儿审批 |
| PayRecord → PaymentApplication | pay_application_id 普通列 | 依赖应用层入口，数据库无保护 |
| CashJournal → PayRecord | source_type + source_id 多态关系 | 无显式付款申请、审批和付款 FK |
| Invoice → PayRecord/Application | 两个可独立填写的普通列 | 可能同时指向不同业务链 |
| SysFile → 业务对象 | business_type + business_id 多态关系 | 无文档类型和 FK；难以证明附件完整性 |
| AccountingEntry → 来源 | source_type + source_id 多态关系 | 无生成策略、无付款/现金日记显式关系 |

### 2.6 接口缺口

- 缺少项目预算版本、预算明细、预算占用/释放/消耗接口。
- 缺少费用申请 CRUD、提交、审批回调和转付款申请接口。
- 缺少付款申请“完整性预检”接口；目前校验分散在保存、提交和付款。
- 缺少付款失败、付款冲销、退款和反向更新接口。
- 缺少付款记录的付款账户、精确时间和凭证附件提交契约。
- 缺少发票与付款多对多金额分配接口。
- 缺少自动会计凭证生成策略与付款触发接口。
- 缺少统一 GET /cash-journals/{id}/trace 或等价全链路追溯接口。
- 缺少驾驶舱预算执行率、现金流和指标来源明细接口。

### 2.7 测试现状与缺口

已有强证据：

- PaymentWritebackTest 覆盖幂等、超付、合同余额、部分/全额付款、合同和成本汇总级联。
- PaymentFinancialConsistencyTest 覆盖并发付款与外部流水。
- PayRecordCashJournalIntegrationTest 覆盖自动生成一条现金日记和事务回滚。
- CashJournalArchiveTest 覆盖附件、账户、余额与开户日期。
- WorkflowEngineIntegrationTest 覆盖多级审批、驳回、重提、撤回、加签、转办、并发和幂等。
- InvoiceServiceTest 覆盖付款关联、重复、核验锁定、OCR 边界。
- Phase2/Phase3/Phase4IntegrationTest 分段覆盖采购/成本/付款、结算、目标成本和发票。

尚无测试：

1. 无 Project → Budget → Contract → Expense/Settlement → PaymentApplication → Approval → Payment → CashJournal → Invoice → Voucher → Dashboard → Trace 的单一端到端用例。
2. 无独立 Expense 测试。
3. 无预算不足、预算占用并发、驳回释放预算、付款消耗预算、红冲恢复预算测试。
4. 无付款申请缺附件、缺费用分类、缺付款对象的提交拒绝测试。
5. 无项目暂停/归档、合同未审批/已关闭时禁止申请和付款测试。
6. 无付款账户、付款精确时间、银行回单必填测试。
7. 无现金日记反查完整审批与原始申请链测试。
8. 无发票金额分配、发票与付款申请一致性测试。
9. 无付款自动生成会计凭证测试。
10. 前端 payment-invoice E2E 主要覆盖列表、筛选和创建付款申请，没有覆盖真实审批后付款、现金日记归档和驾驶舱更新。

### 2.8 实施前 P0 缺陷（均已在第 13 章关闭）

| 编号 | 缺陷 | 影响 | 当前处置 |
| --- | --- | --- | --- |
| P0-01 | 正式合同变更（CT_CHANGE）与现场签证（VAR_ORDER）是两套独立事实，但原实现未声明口径版本、没有历史差异预览，也不能识别同一事项跨域重复登记 | 口径漂移、历史快照失真或业务重复登记时无法及时发现 | 使用唯一金额策略与版本号；提供只读历史差异预览；P0-2 补跨域关系和重复登记约束 |
| P0-02 | 没有项目预算与预算台账 | 无法证明“预算是否足够”，无法防止并发超预算 | P0 建设 |
| P0-03 | 付款提交缺附件、费用分类、付款对象、项目/合同状态门禁 | 不完整申请可进入审批并付款 | P0 建设 |
| P0-04 | 付款无账户与精确时间，现金日记事后人工选账户 | 付款事实与资金账户脱节 | P0 建设 |
| P0-05 | 资金闭环表没有数据库 FK | 孤儿数据、错链和跨租户错误无法由数据库阻断 | P0 迁移治理 |
| P0-06 | 会计凭证生成无策略实现 | Voucher 只是壳，付款不能形成会计事实 | P0 最小策略 |
| P0-07 | 驾驶舱已批未付口径错误且无预算/现金流 | 管理数据不可作为经营决策依据 | P0 重构读模型 |

## 3. 目标项目资金闭环流程

### 3.1 Mermaid Flowchart

~~~mermaid
flowchart TD
    A[新建项目 Project] --> B[编制项目预算 ProjectBudget]
    B --> B1{预算审批通过并生效?}
    B1 -- 否 --> B2[驳回或修改预算]
    B2 --> B
    B1 -- 是 --> C[创建并审批合同 Contract]
    C --> D[建立合同预算分配与成本计划]
    D --> E{申请来源}
    E --> E1[费用申请 Expense]
    E --> E2[结算申请 Settlement]
    E --> E3[直接付款申请 Direct Payment]
    E1 --> F[生成或关联付款申请 PaymentApplication]
    E2 --> F
    E3 --> F
    F --> G{完整性校验}
    G -- 项目/合同/预算/附件/分类/付款对象不完整 --> G1[禁止提交并返回字段级错误]
    G1 --> F
    G -- 通过 --> H[冻结申请快照并占用预算]
    H --> I[多级审批 Approval]
    I -- 驳回 --> I1[释放预算占用并保留审批记录]
    I1 --> F
    I -- 撤回 --> I2[释放预算占用并恢复草稿]
    I2 --> F
    I -- 通过 --> J[财务付款 Payment]
    J --> J1{付款写回幂等与余额校验}
    J1 -- 失败 --> J2[不落付款/不记账/不消耗预算]
    J1 -- 成功 --> K[自动生成待归档现金日记 CashJournal]
    K --> L[上传银行回单/付款凭证/发票]
    L --> M{账户与必要凭证齐全?}
    M -- 否 --> M1[保持待归档并预警]
    M1 --> L
    M -- 是 --> N[归档现金日记并消耗预算]
    N --> O[更新合同累计付款/申请实付/现金余额]
    O --> P[生成会计凭证 Voucher]
    P --> Q[刷新驾驶舱 Dashboard]
    Q --> R[全链路 Trace API]
    R --> S[现金日记可反查付款/审批/申请/来源/合同/项目/预算]
~~~

### 3.2 Mermaid Sequence Diagram

~~~mermaid
sequenceDiagram
    autonumber
    actor PM as 项目经理/申请人
    participant Project as 项目服务
    participant Budget as 预算服务
    participant Contract as 合同服务
    participant App as 费用/结算/付款申请服务
    participant WF as 审批引擎
    actor Finance as 财务人员
    participant Pay as 付款服务
    participant Cash as 现金日记服务
    participant File as 文件/发票服务
    participant Voucher as 会计凭证服务
    participant Dash as 驾驶舱读模型

    PM->>Project: 新建并启用项目
    PM->>Budget: 编制预算版本与科目额度
    Budget->>WF: 提交预算审批
    WF-->>Budget: 审批通过并激活预算
    PM->>Contract: 创建合同及成本计划
    Contract->>WF: 提交合同审批
    WF-->>Contract: 审批通过，合同进入履约
    PM->>App: 创建费用/结算/直接付款申请
    App->>Budget: 校验可用预算并锁定预算行
    Budget-->>App: 返回预算占用凭证
    App->>File: 校验必需附件与文档类型
    App->>WF: 提交申请审批并冻结业务快照
    alt 审批驳回或撤回
        WF-->>App: REJECTED/WITHDRAWN
        App->>Budget: 释放预算占用
        App-->>PM: 允许修订并重新提交
    else 审批通过
        WF-->>App: APPROVED
        Finance->>Pay: 提交付款账户、金额、时间、流水号
        Pay->>Pay: 锁申请/合同/预算并做幂等与超付校验
        Pay->>Cash: 同事务生成 PENDING_ARCHIVE 日记
        Cash-->>Pay: 返回唯一日记ID
        Pay-->>Finance: 付款成功
        Finance->>File: 上传银行回单/付款凭证/发票
        File-->>Cash: 必要凭证齐全事件
        Cash->>Budget: 将占用转为消耗
        Cash->>Cash: 归档并计入账户余额
        Cash->>Voucher: 生成付款会计凭证草稿
        Voucher-->>Dash: 发布财务事实变更
        Dash->>Dash: 刷新合同/付款/预算/成本/现金流/利润
    end
~~~

## 4. 目标数据关系

### 4.1 Mermaid ER

~~~mermaid
erDiagram
    PROJECT ||--o{ PROJECT_BUDGET : owns
    PROJECT_BUDGET ||--|{ PROJECT_BUDGET_LINE : contains
    PROJECT_BUDGET_LINE ||--o{ BUDGET_LEDGER : records
    PROJECT ||--o{ CONTRACT : contains
    CONTRACT ||--o{ CONTRACT_BUDGET_ALLOCATION : allocates
    PROJECT_BUDGET_LINE ||--o{ CONTRACT_BUDGET_ALLOCATION : funds
    CONTRACT ||--o{ COST_ITEM : produces
    PROJECT ||--o{ EXPENSE_APPLICATION : owns
    CONTRACT ||--o{ EXPENSE_APPLICATION : constrains
    CONTRACT ||--o| SETTLEMENT : settles
    EXPENSE_APPLICATION ||--o{ PAYMENT_APPLICATION_SOURCE : sources
    SETTLEMENT ||--o{ PAYMENT_APPLICATION_SOURCE : sources
    PAYMENT_APPLICATION ||--o{ PAYMENT_APPLICATION_SOURCE : has
    PAYMENT_APPLICATION ||--o| WORKFLOW_INSTANCE : approved_by
    WORKFLOW_INSTANCE ||--o{ WORKFLOW_RECORD : leaves
    PAYMENT_APPLICATION ||--o{ PAYMENT : paid_by
    FUND_ACCOUNT ||--o{ PAYMENT : pays_from
    PAYMENT ||--|| CASH_JOURNAL : generates
    PAYMENT ||--o{ INVOICE_PAYMENT_ALLOCATION : allocates
    INVOICE ||--o{ INVOICE_PAYMENT_ALLOCATION : covers
    PAYMENT ||--o{ PAYMENT_DOCUMENT_LINK : evidenced_by
    CASH_JOURNAL ||--o{ PAYMENT_DOCUMENT_LINK : evidenced_by
    SYS_FILE ||--o{ PAYMENT_DOCUMENT_LINK : links
    PAYMENT ||--o| ACCOUNTING_VOUCHER : generates
    PROJECT ||--o{ DASHBOARD_FACT : aggregates
~~~

### 4.2 实体与主外键

| 业务实体 | 目标表/复用表 | 主键 | 强制外键或唯一约束 | 说明 |
| --- | --- | --- | --- | --- |
| Project | pm_project | id | tenant_id + project_code 唯一 | 复用；补受控状态机 |
| Budget | project_budget | id | project_id → pm_project.id；tenant/project/version 唯一 | 新增预算版本头 |
| BudgetLine | project_budget_line | id | budget_id → project_budget.id；cost_subject_id → cost_subject.id；budget/subject 唯一 | 新增科目额度 |
| BudgetLedger | budget_ledger | id | budget_line_id FK；application_id FK；idempotency_key 唯一 | 新增不可变占用/释放/消耗/冲销台账 |
| Contract | ct_contract | id | project_id FK；party_a_id/party_b_id FK | 复用；历史脏数据先修复再加 FK |
| ContractBudgetAllocation | contract_budget_allocation | id | contract_id FK；budget_line_id FK；两列唯一 | 新增合同额度分配 |
| Cost | cost_item / cost_summary | id | project_id、contract_id、cost_subject_id、source 关系 | 复用；CostItem 是事实，CostSummary 是读模型 |
| Expense | expense_application | id | project_id、contract_id、cost_subject_id、payee_partner_id FK | 新增费用申请 |
| Settlement | stl_settlement | id | project_id、contract_id、partner_id FK；tenant/contract/active 唯一 | 复用并修正金额公式 |
| PaymentApplication | pay_application | id | project_id、contract_id、partner_id、approval_instance_id FK | 复用并补完整性字段 |
| PaymentApplicationSource | payment_application_source | id | payment_application_id FK；expense_id/settlement_id 等显式 FK | 新增；禁止只用无 FK 的 type+id |
| Approval | wf_instance/wf_record/wf_task | id | pay_application.approval_instance_id 显式 FK；record.instance_id FK | 复用；记录不可变 |
| Payment | pay_record | id | pay_application_id、project_id、contract_id、partner_id、fund_account_id、approval_instance_id FK；external_txn_no 唯一 | 复用并扩字段 |
| CashJournal | cash_journal_entry | id | pay_record_id、pay_application_id、approval_instance_id、project_id、contract_id、account_id FK；pay_record_id 唯一 | 复用并扩显式关系 |
| Invoice | pay_invoice | id | project_id、contract_id、pay_application_id FK；invoice_no 租户唯一 | 复用并改为发票头 |
| InvoicePaymentAllocation | invoice_payment_allocation | id | invoice_id、pay_record_id FK；两列唯一 | 新增，支持一票多付/一付多票 |
| PaymentEvidence | payment_document_link | id | file_id、pay_record_id/cash_journal_id/invoice_id FK | 新增结构化附件关系 |
| Voucher | accounting_entry/accounting_entry_line | id | project_id、contract_id、pay_record_id、cash_journal_id FK；source 唯一 | 复用并实现付款策略 |
| Dashboard | 查询读模型/物化视图 | 无业务主键 | 只读取上述事实，不作为权威写入源 | 不新建第二套金额事实 |

### 4.3 关键字段补充

#### ProjectBudget

- version_no、total_amount、approval_status、status、effective_from、effective_to、version。
- 同一项目同一时间只允许一个 ACTIVE 版本。
- 生效后不修改原版本；调整必须新建版本并保留迁移台账。

#### BudgetLedger

- event_type：RESERVE、RELEASE、CONSUME、REVERSAL。
- amount、budget_line_id、payment_application_id、pay_record_id、cash_journal_id、idempotency_key。
- 只追加不更新不删除；余额由台账汇总或受控快照计算。

#### PaymentApplication

- application_type：EXPENSE、SETTLEMENT、DIRECT。
- expense_category_id、payee_partner_id、approval_instance_id、budget_reservation_no。
- completeness_status、snapshot_json、version。
- 提交后项目、合同、付款对象、分类、金额、来源和附件摘要冻结。

#### Payment

- fund_account_id、paid_at DATETIME、pay_method、external_txn_no、bank_reference_no、status、reversal_of_id、version。
- voucher_no 只表示外部付款凭证号，不等同会计凭证。

#### PaymentDocumentLink

- document_type：E_INVOICE、INVOICE_SCAN、BANK_RECEIPT、PAYMENT_VOUCHER、CONTRACT_ATTACHMENT、OTHER。
- 文件必须通过病毒扫描且状态有效后才能计入完整性。

### 4.4 删除与保留策略

| 对象 | 草稿 | 审批中/已审批 | 已付款/已归档/已过账 |
| --- | --- | --- | --- |
| Project | 仅空项目可删 | 禁止删除，可暂停 | 仅归档 |
| Budget | 可软删未引用草稿 | 禁止删除 | 新版本调整，旧版本永久保留 |
| Contract | 无下游引用时软删 | 禁止删除 | SETTLED/TERMINATED，保留历史 |
| Expense/Settlement/PaymentApplication | 草稿可软删 | 仅撤回/驳回 | 禁止删除 |
| Approval | 不删除 | 不删除 | 永久保留 |
| Payment | 不提供删除 | 不提供删除 | 失败记录保留，成功记录只能冲销 |
| CashJournal | 手工草稿可删 | 待归档不可物理删 | 只能红冲 |
| Invoice | PENDING 且未分配可软删 | VERIFIED/ABNORMAL 不可删 | 作废需留痕 |
| Voucher | 草稿可受控作废 | POSTED 不可删 | 只能冲销 |
| File | 草稿对象可删 | 冻结后禁止删除 | 通过补充/作废关系处理 |

数据库 FK 默认 ON DELETE RESTRICT。仅纯明细在父对象仍为草稿时允许应用层先删除明细；禁止数据库级 CASCADE 删除财务、审批、付款、日记、发票和凭证事实。

## 5. 生命周期与状态流转

### 5.1 状态机

| 对象 | 状态流转 | 关键门禁 |
| --- | --- | --- |
| Project | DRAFT → ACTIVE ↔ SUSPENDED → CLOSED → ARCHIVED | 只有 ACTIVE 可新增合同和提交支出；SUSPENDED 禁止新申请和付款；CLOSED 禁止新增，只允许查询/冲销 |
| Budget | DRAFT → APPROVING → APPROVED → ACTIVE → SUPERSEDED/CLOSED；APPROVING → REJECTED → DRAFT | ACTIVE 前必须审批；同项目唯一 ACTIVE |
| Contract | DRAFT → APPROVING → APPROVED/PERFORMING → SETTLING → SETTLED；任意履约态 → TERMINATED | 只有 PERFORMING/SETTLING 且未超额度可申请；SETTLED/TERMINATED 禁止新付款 |
| Expense | DRAFT → APPROVING → APPROVED/REJECTED → CONVERTED/PARTIALLY_PAID/PAID；DRAFT → CANCELLED | APPROVED 才能成为付款来源 |
| Settlement | DRAFT → APPROVING → APPROVED/REJECTED → FINALIZED | FINALIZED 后不可编辑；金额快照冻结 |
| PaymentApplication.approvalStatus | DRAFT → APPROVING → APPROVED/REJECTED；APPROVING → WITHDRAWN → DRAFT | 重提必须产生新审批轮次 |
| PaymentApplication.payStatus | PENDING → APPROVED → PARTIALLY_PAID → PAID；APPROVED → CANCELLED | 支付合计不得超过批准金额 |
| Approval | RUNNING → APPROVED/REJECTED/WITHDRAWN；REJECTED/WITHDRAWN → RUNNING 新轮次 | 旧轮次记录永久保留 |
| Payment | PROCESSING → SUCCESS/FAILED；SUCCESS → REVERSED | external_txn_no 幂等；冲销必须引用原付款 |
| CashJournal | DRAFT/PENDING_ARCHIVE → ARCHIVED → REVERSED；ARCHIVED → PENDING_ARCHIVE 仅超级管理员撤销并留痕 | 付款来源日记禁止改金额、项目、合同、来源 |
| Invoice | PENDING → VERIFIED/ABNORMAL；PENDING/ABNORMAL → VOIDED | 已分配或已核验不可删除 |
| Voucher | DRAFT → POSTED → REVERSED | 借贷平衡后才能 POSTED |

### 5.2 驳回与重新提交

1. 驳回必须写 WfRecord，保存操作人、节点、意见、时间、轮次和幂等键。
2. 驳回事务中将付款申请 approvalStatus 置为 REJECTED，并追加 RELEASE 预算台账。
3. 申请人修改后重新提交，生成新审批轮次和新 RESERVE；不得覆盖旧审批记录。
4. 同一轮次同一 idempotencyKey 重复操作只允许一次成功。
5. 审批通过前再次执行预算、合同余额、项目状态和来源金额二次校验。

## 6. 各节点业务契约

### 6.1 新建项目

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 项目名称、类型、组织、项目经理、计划日期、建设单位等 |
| 输出数据 | 唯一 projectId、项目编号、DRAFT 状态 |
| 前置条件 | 当前租户有效；用户有 project:add；组织和项目经理在授权范围内 |
| 后置条件 | 可编制预算；尚不能提交支出 |
| 业务规则 | 项目编号租户内唯一；启用前必须有项目经理和生效预算 |
| 异常处理 | 编号冲突重试；跨租户组织/人员按不存在处理 |
| 数据校验 | 名称非空；日期顺序合法；金额非负 |
| 权限要求 | 创建、编辑、启用、暂停、关闭、归档分权 |
| 日志要求 | CREATE、ACTIVATE、SUSPEND、RESUME、CLOSE、ARCHIVE |
| 审计要求 | 状态变更记录前后值、操作人、原因、时间 |

验收标准：

- 必须生成唯一项目编号。
- DRAFT 项目不得发起资金申请。
- ACTIVE 项目必须存在生效预算。
- SUSPENDED/CLOSED/ARCHIVED 项目不得新建或支付申请。
- 有财务事实的项目不得删除，只能归档。

### 6.2 项目预算

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 项目、预算版本、预算科目、金额、生效期、说明 |
| 输出数据 | 预算头、预算明细、审批实例、生效版本 |
| 前置条件 | 项目存在且非关闭/归档；成本科目有效 |
| 后置条件 | ACTIVE 预算可供合同分配和申请占用 |
| 业务规则 | 明细合计=预算总额；同项目仅一个 ACTIVE；金额不可为负 |
| 异常处理 | 并发激活仅一个成功；历史脏数据阻断迁移 |
| 数据校验 | 科目不得重复；版本号唯一；生效日期合法 |
| 权限要求 | budget:add/edit/submit/approve/activate/query |
| 日志要求 | 预算版本和金额变更完整记录 |
| 审计要求 | 生效版本不可原地修改；调整必须新版本 |

验收标准：

- 没有 ACTIVE 预算不得提交支出申请。
- 预算明细合计必须等于总额。
- 预算可用余额必须可由不可变台账重算。
- 并发申请不得导致可用余额为负。
- 驳回/撤回必须释放占用，付款归档必须把占用转为消耗。

### 6.3 合同

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 项目、合同双方、合同类型、金额、税率、日期、付款条款 |
| 输出数据 | contractId、合同编号、审批实例、履约合同 |
| 前置条件 | 项目 ACTIVE；合同双方有效；项目预算存在 |
| 后置条件 | 审批通过后生成锁定成本并进入 PERFORMING |
| 业务规则 | currentAmount=contractAmount+有效变更；付款条款比例和金额受合同额约束 |
| 异常处理 | 编号冲突重试；审批失败整体回滚 |
| 数据校验 | 项目/租户一致；甲乙方不同；金额和日期合法 |
| 权限要求 | contract:add/edit/submit/approve/query |
| 日志要求 | 合同头、明细、条款和状态变更记录 |
| 审计要求 | 已审批合同只能走变更单，不得直接改金额 |

验收标准：

- 必须绑定 ACTIVE 项目。
- 必须通过审批后才能作为付款合同。
- SETTLED/TERMINATED 合同不得新增付款申请。
- 合同当前金额不得重复计算变更。
- 有付款、结算或审批记录时不得删除。

### 6.4 合同预算/成本计划

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 合同、预算科目、分配额度、合同清单、成本科目 |
| 输出数据 | 合同预算分配、锁定成本、成本计划版本 |
| 前置条件 | 生效项目预算；合同为草稿或审批前 |
| 后置条件 | 合同审批后锁定额度和成本 |
| 业务规则 | 合同分配合计不得超过对应项目预算行；成本计划合计与合同控制额一致或有审批差异说明 |
| 异常处理 | 超分配拒绝；版本冲突重试 |
| 数据校验 | 科目有效；项目/合同/预算同租户同项目 |
| 权限要求 | cost:target 与 contract:budget 分权 |
| 日志要求 | 分配、调整、生效和释放记录 |
| 审计要求 | 已锁定分配不可删除，只能调整台账 |

验收标准：

- 每个支出合同必须至少关联一个预算科目。
- 合同预算分配不得超过项目预算可分配余额。
- 目标成本必须审批通过后才能激活。
- 合同审批失败不得产生锁定成本。

### 6.5 费用申请

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 项目、合同、费用分类、成本科目、付款对象、金额、事由、附件 |
| 输出数据 | expenseId、预算占用候选、付款申请来源 |
| 前置条件 | 项目 ACTIVE；合同可付款；预算科目有效 |
| 后置条件 | 审批通过后可生成/关联 PaymentApplication |
| 业务规则 | 一笔费用只归属一个项目和合同；分类决定预算科目；附件至少一份 |
| 异常处理 | 超预算、合同关闭、项目暂停均拒绝 |
| 数据校验 | 金额>0；付款对象必填；项目/合同/对象同租户 |
| 权限要求 | expense:add/edit/submit/query |
| 日志要求 | 创建、修改、提交、转付款申请 |
| 审计要求 | 审批快照包含分类、对象、金额、附件哈希 |

验收标准：

- 必须绑定项目、合同、费用分类、成本科目和付款对象。
- 必须有有效附件。
- 不得超过预算可用余额。
- 审批后不得修改业务字段。
- 同一费用不得重复转为付款申请。

### 6.6 结算申请

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 项目、合同、计量、变更、扣款、质保、附件 |
| 输出数据 | settlementId、冻结结算金额、付款来源 |
| 前置条件 | 合同 PERFORMING/SETTLING；相关变更和计量均完成审批 |
| 后置条件 | APPROVED/FINALIZED 后回写合同 settlementAmount |
| 业务规则 | finalAmount 采用唯一公式；同合同仅一个有效终结算 |
| 异常处理 | 存在待审变更/计量、缺附件、负未付金额时拒绝 |
| 数据校验 | 项目与合同一致；扣款/质保合法；金额不重复 |
| 权限要求 | settlement:add/edit/submit/approve/query |
| 日志要求 | 来源快照、公式输入、审批结果 |
| 审计要求 | 保存每个来源金额和公式版本 |

验收标准：

- 必须绑定同项目合同。
- 提交前必须重新计算并冻结金额快照。
- 不得重复计算已进入 currentAmount 的合同变更。
- 必须有结算依据附件。
- FINALIZED 后不得编辑或删除。

### 6.7 付款申请与完整性校验

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 来源类型、项目、合同、预算行、费用分类、付款对象、申请金额、附件 |
| 输出数据 | paymentApplicationId、校验报告、预算占用、审批实例 |
| 前置条件 | 来源已审批或 DIRECT 获专门权限；项目/合同可支付 |
| 后置条件 | 通过校验后冻结快照、占用预算、进入审批 |
| 业务规则 | 申请金额=来源分配合计；不得超过预算、合同和来源剩余可付金额 |
| 异常处理 | 返回稳定错误码与字段级错误，不产生审批和预算占用 |
| 数据校验 | 项目、合同、附件、分类、对象、预算、来源九项校验 |
| 权限要求 | payment:app:add/edit/submit；DIRECT 需 payment:direct |
| 日志要求 | 校验结果、快照哈希、预算占用号 |
| 审计要求 | 提交前后字段、附件、预算余额和合同余额留痕 |

验收标准：

- 必须绑定项目、合同、预算行、费用分类和付款对象。
- 必须至少有一个有效来源；DIRECT 必须有增强权限和原因。
- 必须有有效附件。
- 必须同时满足预算余额、合同余额、来源余额和付款条款。
- 校验失败不得创建 WfInstance，不得占用预算。
- 重复提交不得重复占用预算或创建审批实例。

### 6.8 审批

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 业务快照、审批模板、金额、项目、合同、发起人 |
| 输出数据 | 实例、节点、任务、记录、最终状态 |
| 前置条件 | 业务完整性通过；预算已占用；模板和审批人可解析 |
| 后置条件 | APPROVED 允许付款；REJECTED/WITHDRAWN 释放预算 |
| 业务规则 | 多级顺序；幂等；每次重提新轮次；关键回调失败整体回滚 |
| 异常处理 | 无审批人、并发处理、重复幂等键明确失败 |
| 数据校验 | 实例项目/合同/业务对象必须与快照一致 |
| 权限要求 | approve/reject/withdraw/resubmit/transfer/addSign 分权 |
| 日志要求 | 所有动作写 WfRecord |
| 审计要求 | 记录不可变，旧轮次永久保留 |

验收标准：

- 支持驳回、撤回、重新提交和多级审批。
- 审批记录必须包含轮次、节点、操作人、意见和时间。
- 只有当前任务审批人可处理任务。
- 审批通过时必须二次校验预算和合同余额。
- 业务回调失败不得出现“审批通过但业务状态未更新”。

### 6.9 财务付款

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 付款申请、付款金额、资金账户、付款方式、paidAt、外部流水号、银行参考号 |
| 输出数据 | Payment、唯一 CashJournal、级联更新结果 |
| 前置条件 | 申请 APPROVED；项目 ACTIVE；合同可付款；账户启用 |
| 后置条件 | 付款 SUCCESS；生成 PENDING_ARCHIVE 日记；更新累计付款 |
| 业务规则 | externalTxnNo 幂等；部分付款允许；不得超申请/合同/预算 |
| 异常处理 | 任一环节失败整体回滚；同流水不同数据报冲突 |
| 数据校验 | 金额精度、时间、账户、方式、流水必填 |
| 权限要求 | payment:record:writeback；账户数据范围校验 |
| 日志要求 | 请求摘要、幂等命中、成功/失败、关联ID |
| 审计要求 | 成功付款不可编辑删除，只能冲销 |

验收标准：

- 审批未通过不得付款。
- 必须记录付款账户、方式、精确时间、金额和流水号。
- 重复相同回调只返回原付款；不同数据复用流水号必须拒绝。
- 不允许超申请、超合同或超预算付款。
- 付款成功后必须且只能生成一条现金日记。
- 日记生成失败必须回滚付款与全部级联更新。

### 6.10 现金日记

| 项目 | 标准 |
| --- | --- |
| 输入数据 | Payment 快照、账户、项目、合同、申请、审批、金额、时间 |
| 输出数据 | PENDING_ARCHIVE/ARCHIVED 日记、账户余额、变更日志 |
| 前置条件 | 成功付款；来源唯一 |
| 后置条件 | 必要凭证齐全后归档并计入现金余额 |
| 业务规则 | 付款来源字段不可人工修改；payRecordId 唯一；红冲生成反向流水 |
| 异常处理 | 附件缺失、账户停用、余额不足保持待归档 |
| 数据校验 | 所有链路 ID 同项目同合同同租户 |
| 权限要求 | query/maintain/archive/reverse/reopen 分权 |
| 日志要求 | 归档、撤销归档、修改、红冲记录 |
| 审计要求 | 变更前后快照不可变 |

验收标准：

- 不允许人工重复录入付款来源日记。
- 必须显式关联项目、合同、付款申请、审批实例和付款记录。
- 必须继承付款账户，禁止归档时改到无关账户。
- 缺银行回单/付款凭证不得归档。
- 已归档金额不得直接修改，只能红冲。

### 6.11 发票与付款凭证

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 发票头、文件、付款分配金额、银行回单、付款凭证 |
| 输出数据 | Invoice、InvoicePaymentAllocation、PaymentDocumentLink |
| 前置条件 | 付款链存在且当前用户有项目权限 |
| 后置条件 | 发票可核验；凭证齐全可归档日记 |
| 业务规则 | 发票号租户内唯一；分配合计不得超过发票金额或付款金额 |
| 异常处理 | 链路不一致、重复发票、文件不安全、超分配均拒绝 |
| 数据校验 | 项目/合同/申请/付款一致；金额、税额、日期合法 |
| 权限要求 | invoice:add/edit/verify；file:upload/query/delete |
| 日志要求 | OCR、人工确认、创建、核验、作废、分配 |
| 审计要求 | OCR 结果与人工确认值均留痕，不记录敏感全文 |

验收标准：

- 发票必须关联同一项目合同下的有效付款。
- payApplicationId 必须与 PayRecord 的申请一致，禁止客户端自由错配。
- 支持一票多付和一付多票，分配金额守恒。
- 电子发票、扫描件、银行回单、付款凭证和合同附件必须可区分。
- 已核验或已分配发票不可删除。

### 6.12 自动更新与会计凭证

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 成功付款、归档日记、预算台账、成本事实 |
| 输出数据 | 合同累计付款、申请实付、预算消耗、现金余额、Voucher |
| 前置条件 | 付款与日记幂等键有效 |
| 后置条件 | 所有汇总可从事实重算 |
| 业务规则 | 一次业务事件只更新一次；失败整体回滚或可靠补偿 |
| 异常处理 | 事件重复幂等；汇总不一致进入对账告警 |
| 数据校验 | 汇总=事实合计；借贷平衡 |
| 权限要求 | 系统内部服务身份；人工只能过账/冲销 |
| 日志要求 | 事件ID、来源ID、更新前后值 |
| 审计要求 | Voucher 与预算台账不可变 |

验收标准：

- 合同累计付款必须等于有效付款事实。
- 预算消耗必须等于已归档有效支出。
- 现金余额必须等于期初加减已归档流水。
- CostSummary 不得把项目总付款重复写入每个科目行。
- 付款必须生成可过账的借贷平衡凭证草稿。

### 6.13 驾驶舱与全链路追溯

| 项目 | 标准 |
| --- | --- |
| 输入数据 | 项目、预算、合同、成本、申请、付款、日记、收入事实 |
| 输出数据 | 合同金额、付款金额、预算执行率、成本偏差、现金流、利润、Trace |
| 前置条件 | 用户有对应项目数据范围 |
| 后置条件 | 每个指标可下钻到事实 |
| 业务规则 | 只读模型；统一公式版本；不从错误中间表推断已批未付 |
| 异常处理 | 数据不完整显示“待补齐”，不得静默按 0 |
| 数据校验 | 指标与事实 SQL 对账；跨项目聚合遵守 dataScope |
| 权限要求 | dashboard:query + 项目数据范围 |
| 日志要求 | 查询条件、公式版本、数据基线时间 |
| 审计要求 | 导出记录操作者和过滤条件 |

验收标准：

- 合同金额来自有效合同 currentAmount。
- 付款金额来自有效付款记录。
- 已批未付来自 APPROVED PaymentApplication 的剩余可付金额，不来自失败付款记录。
- 预算执行率=(预算占用+预算消耗)/生效预算。
- 现金流来自已归档 CashJournal。
- 项目利润采用已确认收入和动态成本。
- 从 CashJournal 一次查询可返回完整反向链路及每一跳权限校验结果。

## 7. 完整验收标准总表

| 节点 | 必须通过的最小验收 |
| --- | --- |
| 项目 | ACTIVE 才可支出；暂停/关闭阻断；有财务事实不可删 |
| 预算 | 唯一生效版本；明细合计守恒；可用余额可重算；并发不为负 |
| 合同 | 必须绑定项目并审批；关闭合同阻断；变更不重复计算 |
| 成本计划 | 合同额度不超预算；审批后锁定；版本留痕 |
| 费用申请 | 项目/合同/分类/对象/附件必填；预算足够 |
| 结算 | 来源均已审批；公式正确；附件齐全；终结后不可改 |
| 付款申请 | 来源、项目、合同、预算、分类、对象、附件齐全；重复提交幂等 |
| 审批 | 多级、驳回、撤回、重提、记录、权限、并发均通过 |
| 付款 | 审批后支付；账户/方式/时间/金额/流水完整；超付和重复付款阻断 |
| 现金日记 | 成功付款自动唯一生成；显式全链关系；附件后归档；只能红冲 |
| 发票/凭证 | 链路一致；金额分配守恒；文件分类；已核验不可删 |
| 自动更新 | 合同、申请、预算、现金、成本和凭证均可对账 |
| 驾驶舱 | 六项指标口径正确且可下钻；无跨租户/项目泄露 |
| 追溯 | 任一步可正反向导航；孤儿关系为零 |

## 8. 测试方案与测试脚本

### 8.1 自动化分层

| 层级 | 建议文件 | 职责 |
| --- | --- | --- |
| 数据库迁移 | MigrationProjectPaymentClosedLoopTest | FK、唯一约束、CHECK、历史回填和双数据库一致性 |
| 领域单元 | BudgetLedgerServiceTest、PaymentCompletenessValidatorTest | 金额、状态、预算和完整性规则 |
| 事务集成 | ProjectPaymentClosedLoopIntegrationTest | 正常链、回滚、幂等、级联和追溯 |
| 并发集成 | ProjectPaymentConcurrencyTest | 预算占用、合同余额、重复付款和日记唯一性 |
| Controller | 各域 MockMvc 测试 | 权限、参数、错误码、租户和数据范围 |
| 前端组件 | 各页面 Vitest | 表单必填、错误展示、状态动作和禁止编辑 |
| E2E | frontend-admin/e2e/project-payment-closed-loop.spec.ts | 真实角色从项目到驾驶舱的闭环 |
| 对账 | ProjectPaymentReconciliationTest | 预算、付款、日记、凭证和驾驶舱事实守恒 |

### 8.2 正常流程脚本 FLOW-001

前置数据：租户 T1；项目经理 U_PM；成本经理 U_COST；一级审批人 U_M1；二级审批人 U_M2；财务 U_FIN；资金账户 A_BANK，期初余额 10,000,000.00。

| 步骤 | 操作 | 断言 |
| ---: | --- | --- |
| 1 | U_PM 新建项目 P1 | 状态 DRAFT，生成 projectId |
| 2 | U_COST 创建预算 B1，总额 1,000,000，两个科目合计 1,000,000 | 明细合计一致，状态 DRAFT |
| 3 | 提交并两级审批预算 | B1=ACTIVE，P1 可激活 |
| 4 | 激活项目 P1 | P1=ACTIVE |
| 5 | 新建合同 C1，金额 600,000，分配预算 600,000 | 合同项目与预算同项目 |
| 6 | 提交并审批合同 | C1=PERFORMING，锁定成本生成一次 |
| 7 | 创建费用申请 E1，分类 MATERIAL，付款对象 S1，金额 100,000，上传依据 | 完整性校验通过 |
| 8 | 生成付款申请 PA1 并提交 | 预算 RESERVE 100,000，仅一条；审批实例 RUNNING |
| 9 | 一级和二级审批通过 | PA1.approvalStatus=APPROVED，旧任务与记录保留 |
| 10 | U_FIN 付款 60,000，账户 A_BANK，外部流水 TXN-001 | Payment PYM1=SUCCESS；PA1=PARTIALLY_PAID |
| 11 | 检查现金日记 | 仅一条 CJ1，PENDING_ARCHIVE，显式关联 P1/C1/PA1/WF/PYM1/A_BANK |
| 12 | 上传银行回单和付款凭证并归档 CJ1 | CJ1=ARCHIVED；现金余额减少 60,000 |
| 13 | 检查预算 | RESERVE 40,000，CONSUME 60,000，可用 900,000 |
| 14 | 再付款 40,000，归档 CJ2 | PA1=PAID；预算占用归零，消耗 100,000 |
| 15 | 登记发票 I1 100,000，分配 PYM1=60,000、PYM2=40,000并核验 | 分配守恒，I1=VERIFIED |
| 16 | 生成并过账两个付款凭证 | 每张借贷平衡，关联对应付款和日记 |
| 17 | 查询驾驶舱 | 合同600,000；付款100,000；预算执行10%；现金流-100,000 |
| 18 | 从 CJ2 查询 trace | 返回付款、申请、费用、审批两级记录、合同、项目、预算台账 |

### 8.3 异常、边界与重复测试脚本

| ID | 场景 | Given | When | Then |
| --- | --- | --- | --- | --- |
| VAL-001 | 缺项目 | 申请无 projectId | 提交 | PROJECT_REQUIRED；无审批、无预算占用 |
| VAL-002 | 缺合同 | 申请无 contractId | 提交 | CONTRACT_REQUIRED |
| VAL-003 | 项目合同不一致 | 合同属于 P2 | P1 申请绑定该合同 | CONTRACT_PROJECT_MISMATCH |
| VAL-004 | 缺预算 | 项目无 ACTIVE 预算 | 提交 | ACTIVE_BUDGET_REQUIRED |
| VAL-005 | 预算不足 | 可用 99.99 | 申请 100.00 | BUDGET_INSUFFICIENT |
| VAL-006 | 缺附件 | 其他字段完整 | 提交 | ATTACHMENT_REQUIRED |
| VAL-007 | 缺费用分类 | category 为空 | 提交 | EXPENSE_CATEGORY_REQUIRED |
| VAL-008 | 缺付款对象 | payee 为空 | 提交 | PAYEE_REQUIRED |
| VAL-009 | 未知来源类型 | sourceType 非白名单 | 提交 | SOURCE_TYPE_UNSUPPORTED |
| VAL-010 | 来源金额不足 | 来源剩余 80 | 申请 100 | SOURCE_BALANCE_EXCEEDED |
| STA-001 | 项目暂停 | Project=SUSPENDED | 新建/提交/付款 | 三个动作均拒绝 |
| STA-002 | 项目归档 | Project=ARCHIVED | 新建申请 | PROJECT_NOT_ACTIVE |
| STA-003 | 合同草稿 | Contract=DRAFT | 提交付款申请 | CONTRACT_NOT_PAYABLE |
| STA-004 | 合同关闭 | Contract=SETTLED/TERMINATED | 新建/支付 | 均拒绝 |
| STA-005 | 已付款申请修改 | PA=PAID | 修改金额/来源 | IMMUTABLE |
| APR-001 | 一级驳回 | 两级审批运行中 | 一级拒绝 | PA=REJECTED；预算全额释放 |
| APR-002 | 驳回后重提 | 修订后的 PA | resubmit | round+1；旧记录保留；重新占用一次 |
| APR-003 | 重复审批 | 相同 task/idempotencyKey | 连续 approve | 仅一次有效 |
| APR-004 | 并发审批 | 两线程处理同任务 | approve | 仅一线程成功 |
| PAY-001 | 审批未通过付款 | PA=APPROVING | writeback | PAY_APP_NOT_APPROVED |
| PAY-002 | 付款为零 | amount=0 | writeback | PAY_AMOUNT_INVALID |
| PAY-003 | 三位小数 | amount=1.001 | writeback | PAY_AMOUNT_INVALID |
| PAY-004 | 缺账户 | accountId=null | writeback | FUND_ACCOUNT_REQUIRED |
| PAY-005 | 缺精确时间 | paidAt=null | writeback | PAID_AT_REQUIRED |
| PAY-006 | 缺流水 | externalTxnNo 空 | writeback | EXTERNAL_TXN_NO_REQUIRED |
| PAY-007 | 重复相同付款 | 相同流水/申请/金额/时间 | writeback 两次 | 返回同一 Payment 和同一日记 |
| PAY-008 | 流水冲突 | 相同流水、不同金额 | writeback | IDEMPOTENCY_CONFLICT |
| PAY-009 | 超申请余额 | 剩余 50 | 支付 50.01 | PAY_OVERPAYMENT |
| PAY-010 | 超合同余额 | 合同剩余 50 | 支付 50.01 | EXCEED_CONTRACT_BALANCE |
| PAY-011 | 超预算消耗 | 预算可用不足 | 付款 | BUDGET_INSUFFICIENT，全部回滚 |
| PAY-012 | 日记生成失败 | 模拟 CashJournal 异常 | writeback | Payment/合同/申请/预算均不变化 |
| CASH-001 | 日记重复生成 | 重复付款回调 | 生成日记 | payRecordId 唯一，只一条 |
| CASH-002 | 缺回单归档 | 日记无附件 | archive | ATTACHMENT_REQUIRED |
| CASH-003 | 账户余额不足 | 支出后为负 | archive | FUND_ACCOUNT_INSUFFICIENT_BALANCE |
| CASH-004 | 修改来源金额 | 付款来源日记 | update amount | SOURCE_LOCKED |
| CASH-005 | 红冲 | 已归档支出 | reverse | 原记录 REVERSED；反向记录 ARCHIVED；预算恢复 |
| INV-001 | 重复发票 | 同租户同 invoiceNo | create | INVOICE_NO_DUPLICATE |
| INV-002 | 错链申请 | 发票申请与付款所属申请不同 | create | INVOICE_PAYMENT_CHAIN_MISMATCH |
| INV-003 | 发票超分配 | 发票100，分配100.01 | save allocation | INVOICE_ALLOCATION_EXCEEDED |
| INV-004 | 付款超分配 | 付款100，发票分配累计100.01 | save allocation | PAYMENT_INVOICE_EXCEEDED |
| INV-005 | 核验后删除 | Invoice=VERIFIED | delete | INVOICE_VERIFIED_LOCKED |
| VOU-001 | 借贷不平 | 生成策略返回不等额分录 | generate | ENTRY_UNBALANCED，全回滚 |
| VOU-002 | 重复生成 | 同付款/凭证类型 | generate 两次 | 只一张有效凭证 |
| DASH-001 | 已批未付 | PA APPROVED，尚无 PayRecord | 查询驾驶舱 | 已批未付=批准金额 |
| DASH-002 | 现金流口径 | 有待归档与已归档日记 | 查询 | 仅已归档进入现金流 |
| TRACE-001 | 完整追溯 | CJ 来源付款 | GET trace | 每一跳存在且 ID 一致 |
| TRACE-002 | 孤儿保护 | 尝试删除被引用合同 | delete | FK/业务门禁拒绝 |
| SEC-001 | 跨租户读取 | T2 用户访问 T1 日记 | query trace | NOT_FOUND，不泄露任何节点 |
| SEC-002 | 项目数据范围 | 非项目成员访问 | query | PROJECT_ACCESS_DENIED |

### 8.4 并发与事务测试

1. 两个付款申请同时占用同一预算行：总占用不得超过预算，必须使用行锁或乐观锁重试。
2. 两个付款回调同时支付同一申请：有效付款合计不得超过批准金额。
3. 两个不同申请同时支付同一合同：合同累计付款不得超过 currentAmount。
4. 相同 externalTxnNo 同时回调：只能产生一个 Payment、一个 CashJournal、一个预算消耗链。
5. 付款成功后现金日记插入失败：整个事务回滚。
6. 现金日记归档后预算消耗写入失败：日记状态与账户余额均回滚。
7. 审批通过回调二次执行：业务状态、预算占用和审批记录不重复。
8. 发票分配并发：同发票和同付款的已分配金额都不得超限。

### 8.5 数据库与迁移测试

- MySQL 与 H2 迁移版本、字段、索引、FK 和 CHECK 语义镜像。
- 加 FK 前输出孤儿数据报告；存在孤儿时 fail-close，禁止静默删除。
- 结算公式修复必须对历史 finalAmount 生成差异预览，不直接覆盖已终结数据。
- 增量字段对旧客户端可空兼容；生效门禁只在新闭环入口启用。
- 唯一约束必须兼容逻辑删除墓碑策略。
- 每个迁移均提供向前修复方案；金额事实迁移不以回滚 DDL 删除历史数据。

### 8.6 E2E 角色脚本

1. 项目经理创建费用申请并看到缺字段错误。
2. 成本经理查看预算占用前后变化。
3. 一级审批人驳回，申请人修改后重提。
4. 两级审批人依次通过。
5. 财务选择账户并部分付款，重复提交不重复。
6. 财务进入自动生成的现金日记，上传银行回单并归档。
7. 财务登记并核验发票。
8. 会计查看付款生成的凭证并过账。
9. 管理层查看驾驶舱指标并下钻。
10. 从现金日记执行全链追溯，验证每一步可达且无越权数据。

## 9. 开发路线图

### 9.1 P0：必须完成

| 顺序 | 工作包 | 最小交付 | 验收门 |
| ---: | --- | --- | --- |
| P0-1 | 金额口径与历史数据基线 | 区分正式合同变更与现场签证；集中金额策略；写入口径版本；形成只读历史差异预览 | 金额回归与历史数据处置方案通过 |
| P0-2 | 数据关系与迁移 | 为项目、合同、预算、申请、付款、日记、发票、凭证补显式关系、FK、唯一约束、版本字段 | MySQL/H2 迁移与孤儿扫描通过 |
| P0-3 | 项目状态与项目预算 | 受控项目状态机；ProjectBudget/Line/Ledger；占用、释放、消耗、冲销 | 并发预算不为负 |
| P0-4 | 费用申请与统一来源 | Expense；PaymentApplicationSource；来源余额；费用分类与付款对象 | 三种申请来源均可闭环 |
| P0-5 | 完整性校验中心 | 项目、合同、预算、附件、分类、对象、来源、状态统一预检 | 未通过不得创建审批 |
| P0-6 | 付款事实增强 | fundAccountId、paidAt、外部流水、失败/冲销状态；项目/合同/预算二次门禁 | 幂等、超付、事务回滚通过 |
| P0-7 | 现金日记显式链路 | 自动唯一生成；显式项目/合同/申请/审批/付款 FK；凭证齐全归档 | 一笔成功付款一条日记 |
| P0-8 | 发票与付款凭证 | 文档类型、发票分配、链路一致性、不可变规则 | 金额分配守恒 |
| P0-9 | 付款会计凭证 | 至少实现 PAY_RECORD 生成策略；借贷平衡；过账/冲销 | 付款自动生成凭证草稿 |
| P0-10 | 驾驶舱与 Trace | 修正已批未付；预算执行率、现金流、利润；统一 trace API | 指标对账与反向追溯通过 |
| P0-11 | 自动化总验收 | 后端、并发、迁移、前端、E2E 和权限矩阵 | FLOW-001 与全部 P0 负向用例通过 |

P0 完成前禁止宣称“项目资金支出闭环已完成”。

实施状态（2026-07-16）：P0-1 至 P0-11 已完成；数据库迁移为 V156—V167，闭环主验收由 `PaymentApplicationClosedLoopIntegrationTest`、预算/费用/跨域事项集成测试及全量回归共同证明。生产历史数据仍必须遵循下述只读预览和人工确认流程。

P0-1 历史金额处置必须遵循以下顺序：

1. 调用 `GET /settlements/amount-baseline` 分页生成只读差异预览，禁止在预览阶段更新业务表。
2. `MISSING_CONTRACT` 先按孤儿数据阻塞处理；`REVIEW_AMOUNT_DRIFT` 必须逐笔核对合同、正式合同变更、现场签证、计量、扣款和付款来源。
3. 只有金额完全一致的历史记录可以把口径版本从 `LEGACY_UNVERIFIED` 回填为当前版本；回填必须使用独立、可回滚的数据迁移并记录影响行数。
4. 已定案记录存在金额差异时禁止自动覆盖，必须形成差异清单并走财务确认、冲销或更正流程。
5. 同一业务事项同时出现在 `CT_CHANGE` 与 `VAR_ORDER` 时必须人工确认唯一事实来源；P0-2 数据关系改造完成前不得批量修正。

### 9.2 P1：建议完成

实施状态（2026-07-16）：已完成。V168 及付款冲销服务覆盖失败、退款、冲销、预算反向恢复、预算运营、付款计划、预警、日终对账、审计导出和发票异常处理。

- 付款失败、退款、冲销和预算恢复的完整业务入口。
- 预算调整、预算调拨、合同额度释放和版本对比。
- 一票多付/一付多票的 UI、核销进度和异常票处理。
- 自动对账任务：申请、付款、日记、预算、凭证、驾驶舱日终核对。
- 付款计划、到期提醒、现金日记待归档超时和发票未到预警。
- 全链路导出与审计报告。

### 9.3 P2：优化

实施状态（2026-07-16）：已完成。V169 及资金运营服务覆盖驾驶舱快照、OCR 人工复核、批量导入差异、审批路由和冷热审计检索；事实重算能力保留。

- 驾驶舱物化视图和增量刷新，保留事实重算能力。
- 发票 OCR 置信度、票面字段比对和人工复核工作台。
- 大批量预算/合同分配导入与差异校验。
- 审批模板按金额、合同类型和费用分类路由。
- 性能、归档、冷热数据和审计检索优化。

### 9.4 P3：未来版本

实施状态（2026-07-16）：产品内核与集成契约已完成。V170 及资金运营服务覆盖集成端点、幂等消息/重试、回调、银行回单匹配、现金流预测、资金池和会计外部同步状态；真实银行、ERP、总账、税务/电子发票平台的生产凭据、沙箱认证和厂商验收属于第 13.4 节生产启用前置，不得以本地模拟代替。

- 银企直联、银行回单自动匹配和付款状态回调。
- 电子发票平台验真、查重和税务集成。
- 资金计划、滚动现金流预测和融资占用分析。
- 与 ERP/总账/税务平台的双向集成。
- 多公司资金池和集团级资金归集。

## 10. 风险与控制

| 风险 | 等级 | 控制 |
| --- | --- | --- |
| 历史孤儿数据导致 FK 迁移失败 | 高 | 先扫描、分类、人工确认；禁止静默删除 |
| 结算历史金额受重复变更影响 | 高 | 差异报告、冻结已结算事实、按业务审批修正 |
| 并发预算占用/付款导致超支 | 高 | 行锁/乐观锁、唯一幂等键、事务集成测试 |
| 审批与业务状态分裂 | 高 | 关键 Handler 同事务、失败回滚、补偿对账 |
| 付款与现金账户脱节 | 高 | accountId 进入付款事实并自动传递日记 |
| 多态 sourceType/sourceId 产生错链 | 高 | P0 核心链改显式 FK，多态字段仅保留兼容查询 |
| 附件存在但类型或内容无效 | 中高 | documentType、病毒扫描、哈希、业务状态冻结 |
| 驾驶舱重复汇总或错误口径 | 高 | 公式版本、事实对账、指标下钻 |
| 红冲未同步预算/现金/凭证 | 高 | 同一业务编排、反向台账、端到端测试 |
| 大范围一次改造难回滚 | 高 | 按 P0 工作包分阶段迁移；新旧读模型对账后切换 |

## 11. 当前源码证据索引

| 结论 | 主要证据 |
| --- | --- |
| P0 数据模型、约束与菜单 | backend/src/main/resources/db/migration/V156—V167 |
| 项目预算、占用/释放/消耗 | backend/src/main/java/com/cgcpms/budget/service/ProjectBudgetService.java |
| 费用申请与统一付款来源 | backend/src/main/java/com/cgcpms/expense/service/ExpenseApplicationService.java；backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java |
| 付款唯一写回、级联与冲销 | backend/src/main/java/com/cgcpms/payment/service/PayRecordService.java；backend/src/main/java/com/cgcpms/payment/service/PaymentReversalService.java |
| 自动现金日记与显式追溯 | backend/src/main/java/com/cgcpms/cashbook/service/CashJournalService.java；backend/src/main/java/com/cgcpms/payment/service/PaymentTraceService.java |
| 审批引擎与重提 | backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java |
| 结算金额与审批回写 | backend/src/main/java/com/cgcpms/settlement/service/StlSettlementWriteService.java；backend/src/main/java/com/cgcpms/settlement/handler/SettlementWorkflowHandler.java |
| 合同变更进入 currentAmount | backend/src/main/java/com/cgcpms/contract/change/handler/CtContractChangeWorkflowHandler.java |
| 目标成本与激活 | backend/src/main/java/com/cgcpms/cost/service/CostTargetService.java |
| 成本汇总付款更新 | backend/src/main/java/com/cgcpms/cost/service/CostSummaryService.java |
| 发票分配与凭证类型 | backend/src/main/resources/db/migration/V163__invoice_allocation_and_document_type.sql；backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java |
| 付款自动会计凭证 | backend/src/main/resources/db/migration/V164__payment_accounting_entry_trace.sql；backend/src/main/java/com/cgcpms/accounting/service/EntryGenerator.java |
| 驾驶舱与统一 Trace | backend/src/main/java/com/cgcpms/dashboard/service/DashboardFinanceManagementService.java；backend/src/main/java/com/cgcpms/payment/controller/PaymentTraceController.java |
| 跨域事项唯一登记 | backend/src/main/resources/db/migration/V167__prevent_cross_domain_business_matter_duplicates.sql；backend/src/main/java/com/cgcpms/contract/service/BusinessMatterRegistryService.java |
| P1—P3 资金运营 | backend/src/main/resources/db/migration/V168—V170；backend/src/main/java/com/cgcpms/financeops/service/FinanceOperationsService.java |
| 闭环自动化总验收 | backend/src/test/java/com/cgcpms/payment/PaymentApplicationClosedLoopIntegrationTest.java；backend/src/test/java/com/cgcpms/financeops/FinanceOperationsIntegrationTest.java；backend/src/test/java/com/cgcpms/contract/BusinessMatterRegistryIntegrationTest.java |

## 12. 上线验收裁决规则

只有同时满足以下条件，才允许判定“项目资金支出闭环通过”：

1. P0 工作包全部完成且无悬空缺陷。
2. FLOW-001、全部 P0 负向用例、并发、迁移、权限和 E2E 全部通过。
3. 任取一条现金日记可以返回完整正反向链，并且每个关系有数据库或不可绕过的业务约束。
4. 预算、合同付款、申请实付、现金余额、发票分配、会计凭证和驾驶舱金额全部对账一致。
5. 驳回、撤回、失败、重复、冲销和跨租户场景均不产生孤儿数据或重复金额。
6. 历史数据迁移有正式差异报告、处置结果和回滚/补偿方案。

当前裁决：开发验收通过；生产上线有条件不通过。原 P0 阻塞项已全部关闭，但 PROD-GATE-001（历史数据差异处置）和 PROD-GATE-002（真实外部平台联调认证）完成前禁止生产启用。两项均已在第 13.4 节唯一承接，不属于悬空问题。

## 13. 实施收口与验收证据

### 13.1 路线图完成度

| 优先级 | 完成内容 | 关键实现 | 结论 |
| --- | --- | --- | --- |
| P0 | 金额口径、预算、费用、统一来源、完整性校验、付款事实、现金日记、发票分配、会计凭证、驾驶舱、Trace、自动化验收 | V156—V167；预算/费用/付款/追溯/跨域事项服务 | 已完成 |
| P1 | 失败、退款、冲销、预算恢复与运营、付款计划、预警、对账、审计导出 | V168；`PaymentReversalService`；资金运营接口 | 已完成 |
| P2 | 驾驶舱快照、OCR 复核、批量导入差异、审批路由、性能索引和审计冷热检索 | V169；资金运营接口 | 已完成 |
| P3 | 集成端点与消息、银行回单、现金预测、资金池、会计外部同步 | V170；`FinanceOperationsService`/`Controller`；资金运营页面 | 产品内核与集成契约已完成；生产联调受 PROD-GATE-002 约束 |

### 13.2 自动化与构建证据

| 验证层 | 结果 |
| --- | --- |
| 后端全量测试 | 196 个测试套件，1853 个测试；失败 0、错误 0、跳过 1 |
| 前端全量测试 | 103 个测试文件、633 个测试全部通过 |
| 前端生产构建 | `vue-tsc --noEmit && vite build` 通过 |
| 闭环专项 | 项目预算、费用、完整性校验、审批付款、现金日记、会计凭证、发票分配、Trace、跨域重复事项、P1—P3 资金运营集成测试通过 |

唯一允许跳过的用例必须有明确测试注解和原因；不得把失败或环境错误改写为跳过。本次跳过项不影响项目资金闭环业务主线。

### 13.3 运行态与页面验收

- 本地后端重新构建并启动成功，`/api/actuator/health` 返回 `UP`。
- 前端 `http://localhost:5173/` 返回 200，开发登录后可进入驾驶舱。
- 已只读验收 `/dashboard`、`/payment/application`、`/payment/expense`、`/budget`、`/invoice`、`/cash-journal`、`/finance-operations`；关键操作入口可见，无页面级告警。
- 资金运营页面实际调用付款计划、集成端点和预警接口并返回 200；本地运行态刷新产生的环境问题已关闭。

### 13.4 生产启用前置（唯一承接）

| 编号 | 优先级 | 前置与原因 | 解除条件 | 验收标准 |
| --- | --- | --- | --- | --- |
| PROD-GATE-001 | 生产 P0 | 本地无权接触生产数据；FK 加固、金额口径和预算台账上线前必须先证明历史数据无孤儿、错链或金额漂移 | 在生产等价脱敏副本执行只读扫描与金额差异预览，由财务/合同负责人逐笔签认，并形成可回滚迁移方案 | 孤儿数据为 0；待确认金额差异为 0；迁移前后合同、结算、付款、预算和现金日记金额对账一致；回滚演练通过 |
| PROD-GATE-002 | 生产 P0 | 银行、ERP/总账、税务/电子发票平台的凭据、网络白名单、沙箱和厂商规则不在仓库内，不能用模拟端点宣称生产可用 | 各平台凭据与白名单到位，在沙箱完成双向幂等、签名验签、回调、超时重试和人工补偿演练 | 真实沙箱端到端成功；重复回调不重复入账；失败可重试/补偿；凭据不落库明文；厂商与企业财务共同签字验收 |

两项仅阻塞生产启用，不阻塞本地开发验收。完成后必须把证据摘要回写本节并重新执行第 12 章上线裁决，禁止另建重复条目。

### 13.5 后续项收口

- 本轮修复并复验：第 9 章 P0—P3 全部开发工作包及回归兼容问题。
- 超出当前仓库范围并正式承接：PROD-GATE-001、PROD-GATE-002。
- 证据不足或无明确价值而关闭：无。
- 新增后续项：2；关闭后续项：0；后续项净变化：+2。两项均为生产启用外部前置，不是开发缺陷。
