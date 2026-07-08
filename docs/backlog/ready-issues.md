# Ready Issues

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

## 执行顺序建议

1. ISSUE-004-002
2. ISSUE-004-003
3. ISSUE-005-001
4. ISSUE-006-001
5. ISSUE-007-001

## P0

### ISSUE-004-002：采购收货库存数量一致性回归

优先级：P0  
类型：回归 / 后端 / 测试  
状态：Ready  
自动合并：允许；必须通过自动合并门禁；`autoPush=false`  
目标：
- 仅围绕采购单、收货单、库存流水数量链路做一致性回归，修正最小必要实现或测试，避免一次扩散到付款、发票、审批域。

允许修改：
- `backend/src/main/java/**/purchase/**`
- `backend/src/main/java/**/receipt/**`
- `backend/src/main/java/**/inventory/**`
- `backend/src/test/java/**/purchase/**`
- `backend/src/test/java/**/receipt/**`
- `backend/src/test/java/**/inventory/**`
- `docs/quality/**`
- `docs/iterations/**`

禁止修改：
- `frontend-admin/**`
- `backend/src/main/java/**/payment/**`
- `backend/src/main/java/**/invoice/**`
- `backend/src/main/resources/db/migration/**`
- `.codex-autopilot/**`

验收标准：
- 采购下单、收货、入出库后的数量链路一致。
- 回归测试覆盖至少一个正常链路和一个边界链路。
- 质量报告能解释修复点与未覆盖范围。

验证命令：
- `cd backend; .\mvnw.cmd -Dtest=MatPurchaseOrderServiceTest,MatReceiptServiceTest,InventoryServiceTest test`
- `git diff --check`

### ISSUE-004-003：付款发票审批状态链路回归

优先级：P0  
类型：回归 / 后端 / 测试  
状态：Ready  
自动合并：允许；必须通过自动合并门禁；`autoPush=false`  
目标：
- 仅聚焦付款申请、发票登记、审批状态三者的一致性回归，补齐状态断言与报告，不扩展到财务集成。

允许修改：
- `backend/src/main/java/**/payment/**`
- `backend/src/main/java/**/invoice/**`
- `backend/src/main/java/**/workflow/**`
- `backend/src/test/java/**/payment/**`
- `backend/src/test/java/**/invoice/**`
- `backend/src/test/java/**/workflow/**`
- `docs/quality/**`
- `docs/iterations/**`

禁止修改：
- `frontend-admin/**`
- `backend/src/main/java/**/accounting/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.codex-autopilot/**`

验收标准：
- 付款状态与审批状态一致，不出现已通过但未流转或已驳回仍可付款的状态错位。
- 发票必须关联付款记录或明确给出失败提示。
- 回归报告列出状态矩阵和通过结论。

验证命令：
- `cd backend; .\mvnw.cmd -Dtest=PayApplicationServiceTest,InvoiceServiceTest,WorkflowSubmitServiceTest test`
- `git diff --check`

## P1

### ISSUE-005-001：付款与发票列表页生产化补强

优先级：P1  
类型：前端 / 生产化  
状态：Ready  
自动合并：允许；必须通过自动合并门禁；`autoPush=false`  
目标：
- 仅补强付款列表与发票列表的筛选回显、空态、错误态和重试入口，避免一次性扩到全部列表页。

允许修改：
- `frontend-admin/src/pages/payment/**`
- `frontend-admin/src/pages/invoice/**`
- `frontend-admin/src/api/modules/payment.ts`
- `frontend-admin/src/api/modules/invoice.ts`
- `frontend-admin/src/components/**`
- `frontend-admin/src/composables/**`
- `docs/quality/**`
- `docs/iterations/**`

禁止修改：
- `backend/**`
- `frontend-admin/src/pages/purchase/**`
- `frontend-admin/src/pages/inventory/**`
- `frontend-admin/src/router/**`
- `.codex-autopilot/**`

验收标准：
- 筛选条件可回显、可重置、可带入刷新后页面。
- 空态、错误态、重试入口完整可用。
- 前端验证和浏览器验收报告齐全。

验证命令：
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`

### ISSUE-006-001：文件上传白名单与发票识别失败兜底

优先级：P1  
类型：安全 / 后端 / 前端  
状态：Ready  
自动合并：允许；必须通过自动合并门禁；`autoPush=false`  
目标：
- 以最小改动补齐文件上传大小与类型白名单校验，并让发票识别失败返回可理解错误，不在本轮引入病毒扫描或外部安全服务。

允许修改：
- `backend/src/main/java/**/file/**`
- `backend/src/main/java/**/invoice/**`
- `backend/src/test/java/**/file/**`
- `backend/src/test/java/**/invoice/**`
- `frontend-admin/src/pages/invoice/**`
- `frontend-admin/src/api/modules/invoice.ts`
- `docs/quality/**`
- `docs/iterations/**`

禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `frontend-admin/src/pages/system/**`
- 外部存储配置
- `.codex-autopilot/**`

验收标准：
- 非白名单类型或超限文件被拒绝，并返回明确错误。
- 发票识别失败不导致页面卡死，且有可见错误提示。
- 至少补 1 个后端校验测试和 1 个前端或接口回归证据。

验证命令：
- `cd backend; .\mvnw.cmd -Dtest=FileServiceTest,InvoiceServiceTest test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`

### ISSUE-007-001：访问日志上下文与备份清单补强

优先级：P1  
类型：运维 / 文档 / 后端  
状态：Ready  
自动合并：允许；必须通过自动合并门禁；`autoPush=false`  
目标：
- 仅补 traceId/requestId 等访问日志上下文字段及最小备份清单文档，不在本轮做完整监控平台接入。

允许修改：
- `backend/src/main/java/**/config/**`
- `backend/src/main/java/**/common/**`
- `deploy/**`
- `docs/10-部署运维手册.md`
- `docs/quality/**`
- `docs/iterations/**`

禁止修改：
- `frontend-admin/**`
- `backend/src/main/resources/db/migration/**`
- 生产环境凭据
- 外部监控平台配置
- `.codex-autopilot/**`

验收标准：
- 访问日志可稳定输出 traceId/requestId 和基础请求上下文。
- 备份范围、恢复入口、演练要求在文档中有明确清单。
- 验证记录能证明日志字段生效或文档清单已补齐。

验证命令：
- `cd backend; .\mvnw.cmd -Dtest=LoggingConfigTest test`
- `git diff --check`

## 已完成/历史

### ISSUE-000-001：搭建本地 Codex AutoPilot 第一轮治理框架

优先级：P0  
类型：治理 / 脚本  
状态：Done  
自动合并：否  
归档报告：`docs/iterations/iteration-2026-07-08-report.md`
