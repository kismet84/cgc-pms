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

1. `ISSUE-006-005`
2. `ISSUE-007-002`
3. `ISSUE-007-003`

## P0

## P1

### ISSUE-006-005：发票识别失败原因与人工确认口径回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节，第 3 条“发票识别增强”
目标：
- 回归 PDF 解析失败时的错误原因、主流程不被阻断和人工确认前不自动写入高风险结果的口径。
- 补齐失败兜底、人工确认状态和异常路径断言。
允许修改：
- `backend/src/main/java/com/cgcpms/invoice/**`
- `backend/src/test/java/com/cgcpms/invoice/**`
- `docs/quality/**`
- `docs/iterations/**`
禁止修改：
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部发票识别服务配置
验收标准：
- PDF 解析失败返回明确错误原因，且不影响付款/发票主流程继续处理。
- 未经人工确认的识别结果不得直接覆盖关键发票字段。
- 正式报告记录安全边界、失败分类和剩余风险。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceServiceTest" test`
- `git diff --check`

### ISSUE-007-002：MinIO 健康指标与文件失败监控回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.7 P1-4` 节，第 1 条“监控指标”
目标：
- 回归 MinIO 健康检查、文件上传失败可观测性和异常分类口径。
- 补齐本地测试不依赖真实外部对象存储的断言。
允许修改：
- `backend/src/main/java/com/cgcpms/config/**`
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/config/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `docs/quality/**`
- `docs/iterations/**`
禁止修改：
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部对象存储配置
验收标准：
- MinIO 健康检查成功、失败路径都有稳定断言。
- 文件上传失败不会泄露敏感配置，并能归类为文件服务不可用或上传失败。
- 正式报告记录运行前置、失败分类和剩余风险。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=MinioHealthIndicatorTest,FileServiceTest" test`
- `git diff --check`

### ISSUE-007-003：操作审计字段与文件操作审计回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节第 4 条“审计”和第 `7.7 P1-4` 节第 2 条“日志字段”
目标：
- 回归上传、下载、删除等文件操作的审计类型和关键上下文字段。
- 补齐 trace/user/tenant/path/status 等字段缺失时的正式说明或测试断言。
允许修改：
- `backend/src/main/java/com/cgcpms/audit/**`
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/audit/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `docs/quality/**`
- `docs/iterations/**`
禁止修改：
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与日志采集平台配置
验收标准：
- 文件上传、下载、删除的审计操作类型与业务对象信息可追踪。
- 审计异常不应放大为业务数据损坏；若当前实现只记录部分字段，报告必须明确剩余风险。
- 正式报告记录验证命令、失败分类和剩余风险。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=OperationAuditServiceTest,OperationAuditAspectTest,FileServiceTest" test`
- `git diff --check`


## 已完成/历史

### ISSUE-006-004：发票识别重复发票与付款关联回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-004-invoice-duplicate-payment-link.md`

### ISSUE-006-003：附件删除鉴权与审计回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-003-file-delete-auth-audit.md`

### ISSUE-006-002：附件下载鉴权与临时链接回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-002-file-download-auth-temp-link.md`

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

### ISSUE-005-002：项目与合同列表页生产化补强

优先级：P1
类型：前端 / 生产化
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-005-002-project-contract-list-production.md`

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
