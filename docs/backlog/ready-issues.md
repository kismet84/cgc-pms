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

1. `ISSUE-006-009：上传文件 hash 生成与重复文件口径回归`
2. `ISSUE-006-010：文件业务对象绑定完整性回归`
3. `ISSUE-006-011：发票识别记录与人工确认审计回归`
4. `ISSUE-006-012：病毒扫描预留状态与失败兜底回归`
5. `ISSUE-007-015：访问日志 traceId/requestId 透传与响应头回归`

## P0

## P1

### ISSUE-006-009：上传文件 hash 生成与重复文件口径回归

优先级：P1
类型：后端 / 文件安全 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“文件 hash”
目标：
- 回归上传链路中的文件 hash 生成、持久化与重复文件判定口径，确保同内容文件不会绕过既有审计与业务绑定约束。
- 不引入外部查毒服务，不改变对象存储生产配置，不扩大为文件中心重构。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 病毒扫描服务、生产对象存储配置
验收标准：
- 后端对上传文件生成稳定 hash，并对重复上传场景给出明确、可断言的处理结果。
- 合法非重复文件上传不回退，既有业务绑定与审计逻辑不被绕过。
- 若前端需要提示，提示口径与后端返回一致，不误导为网络或权限问题。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-006-009-file-hash-duplication-guard.md`

### ISSUE-006-010：文件业务对象绑定完整性回归

优先级：P1
类型：后端 / 文件安全 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“文件必须绑定业务对象”
目标：
- 回归文件与业务对象的绑定校验，确保孤儿附件、错误业务对象或越权绑定在后端被拒绝。
- 不改变现有权限模型，不扩展为通用附件中心改造。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 权限模型重构、生产对象存储配置
验收标准：
- 后端拒绝未绑定业务对象、绑定对象不存在或绑定关系非法的上传/关联请求。
- 合法业务对象绑定路径不回退，既有下载、删除、鉴权接口保持可用。
- 前端失败提示与后端错误原因一致，不误报为成功或上传完成。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-006-010-file-biz-binding-integrity.md`

### ISSUE-006-011：发票识别记录与人工确认审计回归

优先级：P1
类型：后端 / 审计 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“识别记录、人工确认记录”
目标：
- 回归发票识别与人工确认链路的审计记录，确保识别成功、识别失败、人工确认三类关键动作均可追踪。
- 不改变发票识别业务口径，不引入外部平台依赖。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 发票识别供应商配置、外部平台接入
验收标准：
- 识别成功、识别失败、人工确认三类操作均有稳定审计断言，包含必要但不敏感的上下文字段。
- 审计记录不得泄露票据图片直链、凭据、token 或完整敏感载荷。
- 合法识别与人工确认流程不回退，前端提示与后端结果一致。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-006-011-invoice-recognition-audit-regression.md`

### ISSUE-006-012：病毒扫描预留状态与失败兜底回归

优先级：P1
类型：后端 / 文件安全 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“病毒扫描预留接口”
目标：
- 回归病毒扫描预留状态、错误码或扩展点口径，确保在未接入真实查毒服务时，系统行为明确且不误判为已完成安全扫描。
- 不新增病毒扫描服务，不连接外部文件网关，不改变生产对象存储配置。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 新增病毒扫描服务、生产对象存储配置
验收标准：
- 系统对“未扫描”“扫描失败”“未接入查毒能力”等预留状态有明确口径，不伪装为安全通过。
- 未接入真实查毒服务时，合法上传主流程仍按既定策略工作，不引入误拦截或静默放行。
- 前端提示与后端返回一致，不使用“上传成功且已安全扫描”之类误导性文案。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `cd frontend-admin; pnpm type-check`
- `git diff --check`
归档报告：`docs/quality/issue-006-012-virus-scan-placeholder-regression.md`

### ISSUE-007-015：访问日志 traceId/requestId 透传与响应头回归

优先级：P1
类型：后端 / 可观测性 / 测试
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.7 P1-4` 节“日志字段：traceId/requestId”
目标：
- 回归访问日志中的 `traceId`、`requestId` 字段透传、生成与响应头回写，确保成功请求、匿名请求、异常请求均可稳定关联。
- 不放宽鉴权边界，不扩大为整套日志平台改造。
允许修改：
- `backend/src/main/java/com/cgcpms/**`
- `backend/src/test/java/com/cgcpms/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
- 外部日志平台、生产部署配置
验收标准：
- 成功请求、匿名请求、异常请求三类路径均能稳定产出 `traceId/requestId`，缺失时有明确兜底规则。
- 若请求已携带相关标识，日志与响应头透传口径一致；若未携带，系统生成值可被测试稳定断言。
- 日志与响应头不得泄露 token、cookie、密码、完整请求体等敏感内容。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-007-015-trace-request-id-regression.md`

### ISSUE-005-003：采购与收货列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
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
归档报告：`docs/quality/issue-005-003-purchase-receipt-list-production.md`

### ISSUE-005-004：库存与领料列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
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
归档报告：`docs/quality/issue-005-004-inventory-requisition-list-production.md`

### ISSUE-005-006：预警与审批列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
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
归档报告：`docs/quality/issue-005-006-alert-approval-list-production.md`

### ISSUE-005-007：列表页导出与批量操作权限态回归

优先级：P1
类型：前端 / 权限 / 测试
状态：Done
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
归档报告：`docs/quality/issue-005-007-list-export-batch-permission.md`

### ISSUE-006-006：文件上传大小与 MIME/扩展名校验回归

优先级：P1
类型：安全 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“文件上传限制”
目标：
- 回归文件大小、MIME、扩展名三类上传限制，确保非法文件在后端被拒绝，前端提示不误导。
- 不新增病毒扫描服务，不改变生产对象存储配置。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `frontend-admin/src/types/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 非白名单扩展名、伪造 MIME、超大文件均不可上传。
- 前端提示与后端拒绝原因一致，不只依赖前端校验。
- 不影响合法 PDF/Word/Excel/图片上传。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-006-006-file-upload-validation.md`

### ISSUE-006-007：私有桶默认策略与公开 URL 禁用回归

优先级：P1
类型：安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“文件访问控制”
目标：
- 回归文件访问必须经鉴权接口或临时链接，不暴露公开桶直链。
- 不修改生产 MinIO 配置，不连接生产对象存储。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 文件服务不返回永久公开 URL。
- 未授权下载仍被拒绝。
- 合法授权下载路径不回退。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`
- `git diff --check`
归档报告：`docs/quality/issue-006-007-private-bucket-public-url-regression.md`

### ISSUE-006-008：文件下载临时链接过期与鉴权失败提示回归

优先级：P1
类型：安全 / 后端 / 前端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3` 节“临时访问链接设置过期时间”
目标：
- 回归下载临时链接的过期时间、鉴权失败响应和前端失败提示。
- 不新增外部文件网关，不改变权限模型。
允许修改：
- `backend/src/main/java/com/cgcpms/file/**`
- `backend/src/test/java/com/cgcpms/file/**`
- `frontend-admin/src/pages/**`
- `frontend-admin/src/components/**`
- `frontend-admin/src/api/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 临时链接具备明确过期时间。
- 过期或无权下载时返回可识别错误。
- 前端下载失败提示清晰，合法下载不回退。
验证命令：
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`
- `cd frontend-admin; pnpm type-check`
- `cd frontend-admin; pnpm build`
- `git diff --check`
归档报告：`docs/quality/issue-006-008-file-download-expiry-auth-prompt.md`

### ISSUE-007-009：JVM 与数据库连接池指标回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.7 P1-4` 节“监控指标”
目标：
- 回归 actuator 暴露 JVM 与数据库连接池关键指标，确保本地监控入口可用。
- 不引入外部监控平台，不修改生产部署配置。
允许修改：
- `backend/src/main/java/com/cgcpms/**`
- `backend/src/main/resources/**`
- `backend/src/test/java/com/cgcpms/**`
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- health/metrics 可读取 JVM 与 datasource 相关指标。
- 指标缺失时有明确测试失败或质量报告说明。
- 不放宽鉴权边界。
验证命令：
- `cd backend; .\mvnw.cmd test`
- `git diff --check`
归档报告：`docs/quality/issue-007-009-jvm-datasource-metrics-regression.md`

### ISSUE-007-010：备份清单脱敏与恢复演练报告模板回归

优先级：P1
类型：运维 / 文档 / 归档
状态：Done
自动合并：auto-merge/local-commit-only
来源锚点：`docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.7 P1-4` 节“备份范围、恢复演练”
目标：
- 回归备份范围清单、敏感配置脱敏要求和恢复演练报告模板。
- 不执行真实备份恢复，不读取生产凭据。
允许修改：
- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`
- `docs/**`
禁止修改：
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- 生产凭据与外部平台配置
验收标准：
- 清单覆盖数据库、MinIO 文件、配置文件、密钥、日志归档。
- 模板包含恢复耗时、恢复数据范围和失败原因字段。
- 文档不得包含真实密钥或生产连接串。
验证命令：
- `git diff --check`
归档报告：`docs/quality/issue-007-010-backup-scope-redaction-restore-template.md`

## 已完成/历史

### ISSUE-005-008：核心列表列宽/固定列/金额日期格式统一回归

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-005-008-list-format-column-consistency.md`

### ISSUE-007-008：预警批处理执行结果指标回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-008-alert-batch-result-metrics.md`

### ISSUE-007-009：JVM 与数据库连接池指标回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-009-jvm-datasource-metrics-regression.md`

### ISSUE-007-010：备份清单脱敏与恢复演练报告模板回归

优先级：P1
类型：运维 / 文档 / 归档
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-010-backup-scope-redaction-restore-template.md`

### ISSUE-007-011：CPU/内存/进程指标回归

优先级：P1
类型：运维 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-011-process-memory-metrics-regression.md`

### ISSUE-007-012：Redis 健康与黑名单降级告警回归

优先级：P1
类型：运维 / 安全 / 后端 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-007-012-redis-blacklist-observability.md`

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

### ISSUE-005-006：预警与审批列表页生产化补强

优先级：P1
类型：前端 / 生产化 / 测试
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-005-006-alert-approval-list-production.md`

### ISSUE-006-001：文件上传白名单与发票识别失败兜底

优先级：P1
类型：安全 / 后端 / 前端
状态：Done
自动合并：auto-merge/local-commit-only
归档报告：`docs/quality/issue-006-001-file-upload-invoice-recognition.md`
