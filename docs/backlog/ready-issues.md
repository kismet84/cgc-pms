# Ready Issues

## 任务来源

- 长期总任务池：`docs/backlog/cgc-pms-production-enhancement-plan.md`
- 本文件是当前执行队列，不是任务源全集。
- 当本文件没有合格 Ready Issue 时，AutoPilot 应先从长期总任务池中按 `current-focus.md` 拆出最多 5 个一轮可执行 Ready Issue；该轮只更新 backlog，不直接修改业务代码；后续执行仍按每轮最多处理 1 个 Ready Issue。

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

1. `ISSUE-005-002`
2. `ISSUE-006-002`

## P0

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

### ISSUE-004-004：领料出库与项目成本归集回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-004-requisition-stock-cost-regression.md`

### ISSUE-004-005：分包计量与结算状态链路回归

优先级：P0
类型：回归 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-005-subcontract-settlement-regression.md`

### ISSUE-004-006：审批中心待办/已办/我发起统一筛选回归

优先级：P0
类型：回归 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-004-006-approval-workbench-regression.md`

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
