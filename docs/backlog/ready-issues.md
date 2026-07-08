# Ready Issues

## 任务来源

- 长期总任务池：`docs/backlog/cgc-pms-production-enhancement-plan.md`
- 本文件是当前执行队列，不是任务源全集。
- 当本文件没有合格 Ready Issue 时，AutoPilot 应先从长期总任务池中按 `current-focus.md` 拆出最多 5 个一轮可执行 Issue；该轮只更新 backlog，不直接修改业务代码。

## AutoPilot 自动合并门禁

- Ready Issue 在允许修改范围内完成实现与自审后，允许自动合并；`autoPush=false`，不自动推送。
- 自动合并前必须同时满足：
  - 该 Issue 自带验证命令全部通过。
  - `git diff --check` 通过。
  - 自审结论为 PASS。
  - 已更新 iteration report。
  - 已更新 backlog 的 done/blocked 状态。
  - 合并前再次确认不存在 `.codex-autopilot/stop.flag` 或 `.codex-autopilot/pause.flag`。
- 任一门禁失败即转为 blocked，不进入人工等待态。

## 运行前置与验证命令规范

- 浏览器验收前必须先检查 `http://localhost:8080/api/actuator/health`、`http://localhost:5173/`、`http://localhost:5173/api/auth/dev-login?redirect=/dashboard`。
- 任一 health gate 不通，先归类为环境前置类；执行 runtime refresh，稳定等待 `180秒` 后复验，不直接判业务代码失败。
- Docker / backend / frontend 未启动或端口不通，按环境前置类处理；恢复失败再 blocked。
- PowerShell 下包含逗号的 Maven `-Dtest=` 参数必须整体加引号，避免参数拆分或 `ParserError`。
- 验证命令中的测试类、测试方法选择器、脚本入口必须先校验存在；若不存在，归类为 Ready Issue 配置问题，可做最小等价替换，但必须在正式报告写明替换前后内容与原因。

## 执行顺序建议

1. `ISSUE-004-004`
2. `ISSUE-004-005`
3. `ISSUE-004-006`
4. `ISSUE-005-002`
5. `ISSUE-006-002`

## P0

### ISSUE-004-004：领料出库与项目成本归集回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only；仅限 `backend/src/test/**`、`docs/quality/**`、`docs/iterations/**`
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.4 P1-1` 节，第 4 条“领料 → 出库 → 项目成本”
目标：
- 补齐领料审批通过后的出库流水、库存扣减与项目成本归集一致性断言。
- 产出正式回归报告，明确金额字段、状态字段、来源单据三类证据。
允许修改：
- `backend/src/test/java/com/cgcpms/requisition/**`
- `backend/src/test/java/com/cgcpms/inventory/**`
- `backend/src/test/java/com/cgcpms/cost/**`
- `docs/quality/**`
- `docs/iterations/**`
禁止修改：
- `backend/src/main/**`
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
验收标准：
- 领料审批通过后产生可追溯出库流水。
- 库存数量变化与出库流水一致。
- 项目成本归集金额能回溯到本次领料单据。
- 正式报告记录失败分类、验证结果与剩余风险。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=MatRequisitionWorkflowSubmitTest,MatStockServiceTest,CostLedgerServiceTest" test`
- `git diff --check`

### ISSUE-004-005：分包计量与结算状态链路回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only；仅限 `backend/src/test/**`、`docs/quality/**`、`docs/iterations/**`
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.4 P1-1` 节，第 5 条“分包任务 → 计量 → 结算”
目标：
- 补齐分包计量、结算生成、审批通过后的状态流转与金额口径断言。
- 覆盖“草稿/审批中/审批通过”关键保护条件，避免结算链路回退。
允许修改：
- `backend/src/test/java/com/cgcpms/subcontract/**`
- `backend/src/test/java/com/cgcpms/settlement/**`
- `docs/quality/**`
- `docs/iterations/**`
禁止修改：
- `backend/src/main/**`
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
验收标准：
- 分包计量提交审批、通过、驳回、撤回状态符合既有业务口径。
- 结算金额计算、结算来源与付款关联结果可解释。
- 审批通过后禁止误编辑或误删除。
- 正式报告记录链路样本、断言结果与非阻塞风险。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=SubMeasureServiceTest,StlSettlementServiceTest,SettlementWorkflowHandlerTest,StlSettlementQueryServiceTest" test`
- `git diff --check`

### ISSUE-004-006：审批中心待办/已办/我发起统一筛选回归

优先级：P0
类型：回归 / 后端 / 前端 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only；仅限 `backend/src/test/**`、`frontend-admin/src/pages/approval/**`、`docs/quality/**`、`docs/iterations/**`
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.4 P1-1` 节，第 8 条“审批中心 → 状态流转 → 通知 / 预警”
目标：
- 回归待办、已办、抄送、我发起四类工作台查询口径，保证分页 total、状态筛选和身份边界一致。
- 回归前端审批工作台的筛选项、嵌入详情、撤回/重提入口，避免页面口径与后端查询脱节。
允许修改：
- `backend/src/test/java/com/cgcpms/workflow/**`
- `frontend-admin/src/pages/approval/**`
- `docs/quality/**`
- `docs/iterations/**`
禁止修改：
- `backend/src/main/**`
- `frontend-admin/src/api/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
验收标准：
- 待办、已办、抄送、我发起分页 total 与记录来源一致。
- 统一筛选条件不会突破租户、项目、审批参与人边界。
- 前端工作台只暴露当前冻结口径允许的业务类型和动作入口。
- 正式报告区分后端查询回归结果与前端工作台回归结果。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=WorkflowQueryServiceTest,WorkflowTaskServiceTest,WorkflowSubmitServiceTest" test`
- `cd frontend-admin; pnpm exec vitest run src/pages/approval/__tests__/ApprovalWorkList.test.ts src/pages/approval/__tests__/ApprovalConfirm.test.ts src/pages/approval/__tests__/workflowDisplay.test.ts`
- `git diff --check`

## P1

### ISSUE-005-002：项目与合同列表页生产化补强

优先级：P1
类型：前端 / 生产化
状态：Ready
自动合并：auto-merge/local-commit-only；仅限 `frontend-admin/src/pages/project/**`、`frontend-admin/src/pages/contract/**`、`docs/quality/**`、`docs/iterations/**`
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节，第 1-2 条“项目列表、合同列表”
目标：
- 统一项目与合同列表页的查询条件回显、分页参数、loading/empty/error 态与 URL 参数保留行为。
- 只处理列表页生产化问题，不扩展到详情页重构或新业务字段。
允许修改：
- `frontend-admin/src/pages/project/**`
- `frontend-admin/src/pages/contract/**`
- `docs/quality/**`
- `docs/iterations/**`
禁止修改：
- `backend/**`
- `frontend-admin/src/pages/payment/**`
- `frontend-admin/src/pages/invoice/**`
- `deploy/**`
验收标准：
- 项目与合同列表均使用服务端分页和稳定的查询参数回显。
- 页面出现空态、异常态时有可理解反馈，不靠控制台信息兜底。
- 现有详情、编辑、删除入口不回退。
- 正式报告说明两页共性修复项与各自残留风险。
验证命令：
- `cd frontend-admin; pnpm exec vitest run src/pages/project/__tests__/ProjectLedgerProduction.test.ts src/pages/project/__tests__/ProjectNav.test.ts src/pages/contract/__tests__/ContractLedgerPage.test.ts src/pages/contract/__tests__/ContractFormPage.test.ts src/pages/contract/__tests__/useContractLedger-ui-consistency.test.ts`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-006-002：附件下载鉴权与临时链接回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only；仅限 `backend/src/main/java/com/cgcpms/file/**`、`backend/src/test/java/com/cgcpms/file/**`、`docs/quality/**`、`docs/iterations/**`
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节，第 2 条“文件访问控制”
目标：
- 回归文件下载鉴权、业务对象读权限校验与临时下载链接生成口径。
- 补齐未授权读取、跨业务对象读取、文本附件下载头等安全边界断言。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `docs/quality/**`
- `docs/iterations/**`
禁止修改：
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部存储配置
验收标准：
- 未授权用户无法获取他人业务对象附件下载链接。
- 已授权用户获取到的临时链接仍受既有下载策略约束。
- 文本类附件下载头、审计操作类型与异常路径有明确断言。
- 正式报告记录安全边界、失败分类和剩余风险。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest,BusinessObjectAuthorizerTest" test`
- `git diff --check`


## 已完成/历史

### ISSUE-007-001：访问日志上下文与备份清单补强

优先级：P1
类型：运维 / 文档 / 后端
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-001-access-log-backup-checklist.md`

### ISSUE-000-001：搭建本地 Codex AutoPilot 第一轮治理框架

优先级：P0  
类型：治理 / 脚本  
状态：Done  
自动合并：否  
归档报告：`docs/iterations/iteration-2026-07-08-report.md`

### ISSUE-004-002：采购收货库存数量一致性回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-002-purchase-receipt-inventory-regression.md`

### ISSUE-004-003：付款发票审批状态链路回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-003-payment-invoice-workflow-regression.md`

### ISSUE-005-001：付款与发票列表页生产化补强

优先级：P1
类型：前端 / 生产化
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-005-001-payment-invoice-list-production.md`

### ISSUE-006-001：文件上传白名单与发票识别失败兜底

优先级：P1
类型：安全 / 后端 / 前端
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-001-file-upload-invoice-recognition.md`
