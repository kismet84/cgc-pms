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

1. `ISSUE-005-003`
2. `ISSUE-005-004`
3. `ISSUE-005-005`
4. `ISSUE-005-006`
5. `ISSUE-005-007`

## P0

## P1

### ISSUE-005-003：采购与收货列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节“采购列表、收货列表”
目标：
- 补强采购列表与收货列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口。
- 不扩展详情页重构、新业务字段或后端接口语义。
允许修改：
- `frontend-admin/src/pages/purchase/**`
- `frontend-admin/src/pages/receipt/**`
- `frontend-admin/src/api/modules/purchase.ts`
- `frontend-admin/src/api/modules/receipt.ts`
- `frontend-admin/src/composables/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 查询条件刷新后可回显，分页参数不丢失。
- loading、empty、error、retry 状态可达且不遮挡主要操作。
- 无权限按钮不可见的既有逻辑不回退。
验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`

### ISSUE-005-004：库存与领料列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节“库存列表、领料列表”
目标：
- 补强库存列表与领料列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口。
- 不改库存数量业务口径，不扩展后端接口语义。
允许修改：
- `frontend-admin/src/pages/inventory/**`
- `frontend-admin/src/pages/requisition/**`
- `frontend-admin/src/api/modules/inventory.ts`
- `frontend-admin/src/api/modules/requisition.ts`
- `frontend-admin/src/composables/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 查询条件刷新后可回显，分页参数不丢失。
- loading、empty、error、retry 状态可达且不遮挡主要操作。
- 库存数量和领料状态展示不因前端补强改变业务含义。
验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`

### ISSUE-005-005：分包与结算列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节“分包列表、结算列表”
目标：
- 补强分包列表与结算列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口。
- 不改分包计量、结算审批或付款关联业务口径。
允许修改：
- `frontend-admin/src/pages/subcontract/**`
- `frontend-admin/src/pages/settlement/**`
- `frontend-admin/src/api/modules/subcontract.ts`
- `frontend-admin/src/api/modules/settlement.ts`
- `frontend-admin/src/composables/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 查询条件刷新后可回显，分页参数不丢失。
- loading、empty、error、retry 状态可达且不遮挡主要操作。
- 状态字段、金额字段展示不因前端补强改变业务含义。
验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`

### ISSUE-005-006：预警与审批列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节“预警列表、审批列表”
目标：
- 补强预警列表与审批列表的筛选回显、分页参数保留、loading/empty/error 态与重试入口。
- 不改预警规则、审批状态机或后端权限边界。
允许修改：
- `frontend-admin/src/pages/alert/**`
- `frontend-admin/src/pages/approval/**`
- `frontend-admin/src/stores/alert.ts`
- `frontend-admin/src/api/modules/alert.ts`
- `frontend-admin/src/api/modules/workflow.ts`
- `frontend-admin/src/composables/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 查询条件刷新后可回显，分页参数不丢失。
- loading、empty、error、retry 状态可达且不遮挡主要操作。
- 预警权限域、审批待办/已办/我发起口径不回退。
验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`

### ISSUE-005-007：列表页导出与批量操作权限态回归

优先级：P1
类型：前端 / 权限 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2` 节“批量操作、权限按钮控制、导出权限”
目标：
- 回归核心列表页导出按钮、批量操作按钮和权限态展示。
- 不新增导出后端能力，不改变权限模型。
允许修改：
- `frontend-admin/src/pages/project/**`
- `frontend-admin/src/pages/contract/**`
- `frontend-admin/src/pages/purchase/**`
- `frontend-admin/src/pages/receipt/**`
- `frontend-admin/src/pages/inventory/**`
- `frontend-admin/src/pages/requisition/**`
- `frontend-admin/src/pages/subcontract/**`
- `frontend-admin/src/pages/settlement/**`
- `frontend-admin/src/pages/payment/**`
- `frontend-admin/src/pages/invoice/**`
- `frontend-admin/src/pages/alert/**`
- `frontend-admin/src/pages/approval/**`
- `frontend-admin/src/composables/**`
- `frontend-admin/src/stores/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 无权限时导出和批量操作入口不可见或不可用。
- 有权限时既有可用入口不回退。
- 不通过前端按钮隐藏替代后端权限校验；本轮只回归前端权限态。
验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`

## 已完成/历史

### ISSUE-007-008：预警批处理执行结果指标回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-008-alert-batch-result-metrics.md`

### ISSUE-007-007：登录失败与文件失败次数指标回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-007-login-file-failure-metrics.md`

### ISSUE-007-003：操作审计字段与文件操作审计回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-003-operation-audit-file-actions.md`

### ISSUE-007-004：接口性能与错误率监控指标回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-004-actuator-metrics-regression.md`

### ISSUE-007-005：访问日志 projectId/status/duration/exception 字段回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-005-access-log-fields-regression.md`

### ISSUE-007-006：备份范围与恢复演练报告模板补强

优先级：P1
类型：运维 / 文档 / 归档
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-006-backup-scope-restore-drill-template.md`

### ISSUE-006-005：发票识别失败原因与人工确认口径回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-005-invoice-recognition-manual-confirmation.md`

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

### ISSUE-007-002：MinIO 健康指标与文件失败监控回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-002-minio-health-upload-monitoring.md`

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
