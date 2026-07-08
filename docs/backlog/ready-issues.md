# Ready Issues

## 任务来源

- 长期总任务池：`docs/backlog/cgc-pms-production-enhancement-plan.md`
- 本文件是当前执行队列，不是任务源全集。
- 当本文件没有合格 Ready Issue 时，AutoPilot 应先从长期总任务池中按 `current-focus.md` 拆出最多 3 个一轮可执行 Issue；该轮只更新 backlog，不直接修改业务代码。

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

1. ISSUE-007-001

## P0

## P1

### ISSUE-007-001：访问日志上下文与备份清单补强

优先级：P1  
类型：运维 / 文档 / 后端  
状态：Ready  
自动合并：允许；必须通过自动合并门禁；`autoPush=false`  
来源：`docs/backlog/cgc-pms-production-enhancement-plan.md#77-p1-4生产监控日志与备份恢复`
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
