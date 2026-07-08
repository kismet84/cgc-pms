# ISSUE-004-007：合同清单金额与付款条件回归

## 结论

- 结论：通过
- 阻塞：非阻塞
- 范围结论：已补齐合同主表、合同清单与付款条件之间金额、日期、状态一致性的稳定回归断言。
- 边界结论：本轮未修改生产代码，未修改合同业务语义，未新增数据库结构或 migration，未放宽租户、项目、权限或业务状态边界。

## 本轮修改

- `backend/src/test/java/com/cgcpms/contract/ContractCompositeSaveTest.java`
  - 新增 `ISSUE-004-007` 回归用例，覆盖复合保存后合同头、清单项、付款条件三类数据的同源一致性。
  - 锁定合同头 `contractAmount/currentAmount`、签订日期、开始日期、结束日期、合同状态和审批状态。
  - 锁定两条合同清单金额合计等于合同金额，且清单排序与编码稳定。
  - 锁定三条付款条件金额合计等于合同金额、付款比例合计为 100%，并断言付款计划日期和条款状态不漂移。

## TDD 证据

- 先补充 `ContractCompositeSaveTest` 回归断言后执行：
  - `cd backend; .\mvnw.cmd "-Dtest=ContractCompositeSaveTest" test`
  - 结果：通过，`7` 个用例通过。
- 说明：当前 `CtContractService.compositeSave` 既有实现已满足本 Issue 的一致性口径，因此本轮不改生产代码，只补测试门禁和正式归档。

## 验证证据

### 1. 合同目标回归

- `cd backend; .\mvnw.cmd "-Dtest=ContractCompositeSaveTest" test`
  - 通过，`7` 个用例通过。
  - 覆盖：
    - 合同头 `contractAmount=640000.00`、`currentAmount=640000.00`。
    - 合同清单两项合计 `640000.00`。
    - 付款条件三项合计 `640000.00`，付款比例合计 `100.00`。
    - 合同 `signedDate/startDate/endDate` 与付款条件 `plannedDate` 稳定落库。
    - 合同状态保持 `DRAFT`，审批状态保持 `DRAFT`，付款条件状态保持输入值。

### 2. Ready Issue 指定全量验证

- `cd backend; .\mvnw.cmd test`
  - 未通过，失败类未命中本轮合同清单/付款条件目标测试，按仓库规则分类为既有真实质量/测试存量问题。
  - 本轮扫描到的失败类包括：
    - `com.cgcpms.dashboard.service.DashboardChiefEngineerServiceTest`
    - `com.cgcpms.invoice.InvoiceValidationTest`
    - `com.cgcpms.MigrationSoftDeleteBehaviorTest`
    - `com.cgcpms.payment.PayRecordControllerTest`
    - `com.cgcpms.Phase2FullChainIntegrationTest`
    - `com.cgcpms.Phase4IntegrationTest`
    - `com.cgcpms.purchase.PurchaseRequestServiceTest`
    - `com.cgcpms.revenue.ContractRevenueServiceTest`
    - `com.cgcpms.workflow.WorkflowApproverResolverTest`
    - `com.cgcpms.workflow.WorkflowConcurrencyTest`
    - `com.cgcpms.workflow.WorkflowCoreServiceTest`
    - `com.cgcpms.workflow.WorkflowEngineIntegrationTest`
    - `com.cgcpms.workflow.WorkflowTemplateManagementTest`

### 3. diff 门禁

- `git diff --check`
  - 通过。

## 金额、清单、付款条件一致性口径

- 合同金额：本轮不改变业务语义，不由服务层自动重算合同金额；测试锁定请求中的合同头金额稳定持久化。
- 清单金额：合同清单项 `amount` 由测试输入的 `quantity * unitPrice` 形成，保存后按 `contractId` 查询并汇总，汇总值必须等于合同头金额。
- 付款条件：付款条件 `paymentAmount` 与 `paymentRatio` 按输入保存，保存后金额合计必须等于合同头金额，比例合计必须为 100%。
- 日期与状态：合同头日期、付款计划日期、合同状态、审批状态和付款条款状态均按输入或既有 `DRAFT` 规则稳定落库，不发生静默漂移。

## 权限、租户、项目边界

- 本轮测试仍使用既有 `TestUserContext` 的租户 0 管理员上下文，合同、清单和付款条件查询均按 `contractId` 与既有 mapper/service 路径执行。
- 本轮未新增查询入口、接口权限、租户条件或项目权限逻辑，未放宽现有边界。
- 本轮未连接生产数据库，未修改生产配置、部署配置或外部平台配置。

## 剩余风险

- 本轮是后端合同复合保存回归，不覆盖前端合同表单展示和浏览器交互。
- 本轮不新增合同金额自动校验或自动重算规则；如果后续需要强制“保存时拒绝清单合计与合同金额不一致”，需另立业务规则确认任务。
