# Iteration Report - 2026-07-09

Issue：ISSUE-005-007 列表页导出与批量操作权限态回归

目标：
- 回归核心列表页导出按钮、批量操作按钮和权限态展示。
- 不新增导出后端能力，不改变权限模型；不通过前端按钮隐藏替代后端权限校验。

修改范围摘要：
- `frontend-admin/src/pages/alert/index.vue`：新增预警列表管理/导出权限计算态，并在批量、行级写操作和导出函数入口加前端权限态 guard。
- `frontend-admin/src/pages/alert/components/AlertTablePanel.vue`：按权限态控制批量处理、标记已读、归档、导出和行级写操作入口展示。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`：补充无编辑/导出权限时入口隐藏的回归测试。
- `docs/quality/issue-005-007-list-export-batch-permission.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-005-007 收口为 Done，并在 Ready 队列为空后拆出 5 个新 Ready Issue。

验证命令摘要：
- `cd frontend-admin; pnpm test:unit src/pages/alert/__tests__/index.test.ts -- --runInBand`：通过，`1` 个文件、`12` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：非失败分类
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未做真实浏览器验收，结论基于指定 Vitest、类型检查、构建和代码审查。
- 本轮只覆盖实际存在导出/批量入口的预警列表权限态；未新增导出后端能力。

---

Backlog 拆解：Ready 队列补充

来源：
- `docs/backlog/current-focus.md`
- `docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3`、`7.7 P1-4`

新增 Ready Issue：
- `ISSUE-006-006`：文件上传大小与 MIME/扩展名校验回归。
- `ISSUE-006-007`：私有桶默认策略与公开 URL 禁用回归。
- `ISSUE-006-008`：文件下载临时链接过期与鉴权失败提示回归。
- `ISSUE-007-009`：JVM 与数据库连接池指标回归。
- `ISSUE-007-010`：备份清单脱敏与恢复演练报告模板回归。

拆解边界：
- 本轮只更新 backlog，不继续执行新拆出的业务任务。
- 新任务均禁止修改已应用 Flyway migration、生产凭据、外部平台配置和生产部署配置。

---

Issue：ISSUE-006-006 文件上传大小与 MIME/扩展名校验回归

目标：
- 回归文件大小、MIME、扩展名三类上传限制，确保非法文件在后端被拒绝。
- 对齐前端上传大小提示，避免前端提示与后端拒绝原因不一致。
- 不新增病毒扫描服务，不改变生产对象存储配置。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/service/FileTypeValidator.java`：收紧 Office Open XML MIME 对应关系，拒绝 DOCX 声明为 Excel/PPT 等跨类型 MIME。
- `backend/src/test/java/com/cgcpms/file/FileTypeValidatorTest.java`：补充 Office MIME 错配拒绝、XLSX 合法文件、20MB 超限边界回归。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：上传服务超限边界测试口径对齐为 `>20MB`。
- `frontend-admin/src/pages/invoice/components/InvoiceFormModal.vue`：发票 PDF 上传大小提示对齐后端 20MB。
- `frontend-admin/src/pages/invoice/__tests__/invoice-pdf.test.ts`：补充 15MB 可通过、21MB 被拦截的前端回归，并补齐测试路由 mock。
- `frontend-admin/src/pages/help/index.vue`：帮助中心上传说明从 50MB 对齐为 20MB。
- `docs/quality/issue-006-006-file-upload-validation.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-006 收口为 Done，Ready 队列推进到 ISSUE-006-007。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileTypeValidatorTest" test`：通过，`16` 个用例通过。
- `cd frontend-admin; pnpm vitest run src/pages/invoice/__tests__/invoice-pdf.test.ts`：通过，`1` 个文件、`6` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，`36` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；测试前置问题已更正
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未新增病毒扫描能力，符合 Issue 禁止事项；恶意内容深度检测仍不在本轮范围。
- Office Open XML 仍按现有轻量魔术字节 / 内容标记识别，不做完整 ZIP 包解析；如需更精确内部 content type 识别，需另立任务。

---

Issue：ISSUE-006-007 私有桶默认策略与公开 URL 禁用回归

目标：
- 回归文件访问必须经鉴权接口或临时链接。
- 禁止服务层透传公开桶直链或永久 URL。
- 验证未授权下载被拒、合法授权下载不回退。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`：在 `genPresignedUrl` 统一出口校验返回 URL 必须包含 `X-Amz-Signature=`，未签名 URL 转为 `FILE_URL_ERROR`。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：新增未签名公开桶 URL 拒绝回归，并补齐上传成功测试的签名临时链接 mock。
- `docs/quality/issue-006-007-private-bucket-public-url-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-007 收口为 Done，Ready 队列推进到 ISSUE-006-008。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testGetPresignedUrlRejectsUnsignedPublicUrl" test`：先失败，原因是未签名公开桶 URL 被原样透传。
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testGetPresignedUrlRejectsUnsignedPublicUrl" test`：修复后通过，`1` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，`37` 个用例通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；测试夹具问题已更正
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮不修改 MinIO 桶策略和生产对象存储配置；真实桶私有策略仍需由部署环境配置保证。
- 当前兜底以 MinIO/S3 预签名 URL 的 `X-Amz-Signature` 参数作为临时链接判据；如未来切换非 S3 签名方案，需要同步调整判据。

---

Issue：ISSUE-006-008 文件下载临时链接过期与鉴权失败提示回归

目标：
- 回归下载临时链接的过期时间、鉴权失败响应和前端失败提示。
- 不新增外部文件网关，不改变权限模型。
- 确保合法下载不回退。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`：预签名 URL 兜底校验要求同时包含 `X-Amz-Signature` 与 `X-Amz-Expires=300`。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：新增缺少明确过期参数的签名 URL 拒绝回归，并补齐合法临时链接夹具。
- `frontend-admin/src/api/modules/file.ts`：文件下载 URL 请求携带专用失败提示。
- `frontend-admin/src/api/request.ts`：请求拦截器优先展示每个请求配置的错误提示。
- `frontend-admin/src/api/__tests__/request.test.ts`、`frontend-admin/src/api/modules/__tests__/system-modules.test.ts`：补充前端失败提示回归。
- `docs/quality/issue-006-008-file-download-expiry-auth-prompt.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-008 收口为 Done，Ready 队列推进到 ISSUE-007-009。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testGetPresignedUrlRejectsSignedUrlWithoutExplicitExpiry" test`：先失败，原因是有签名但无 `X-Amz-Expires=300` 的 URL 被接受。
- `cd frontend-admin; pnpm exec vitest run src/api/modules/__tests__/system-modules.test.ts src/api/__tests__/request.test.ts`：先失败，原因是文件下载 URL 请求未携带专用失败提示。
- 上述后端单测修复后通过，`1` 个用例通过。
- 上述前端目标测试修复后通过，`2` 个文件、`10` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，`38` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；前端提示回归已补齐
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮不修改 MinIO 桶策略、生产对象存储配置或外部文件网关。
- 当前过期判据绑定 MinIO/S3 的 `X-Amz-Expires=300` 参数；如未来切换非 S3 签名方案，需要同步调整判据与测试。

---

Issue：ISSUE-007-009 JVM 与数据库连接池指标回归

目标：
- 回归 actuator 暴露 JVM 与数据库连接池关键指标，确保本地监控入口可用。
- 不引入外部监控平台，不修改生产部署配置，不放宽鉴权边界。

修改范围摘要：
- `backend/src/main/resources/application-dev.yml`：默认开放 `health,info,metrics`，恢复开发环境监控入口。
- `backend/src/main/resources/application-local.yml`：新增 `metrics` 暴露配置，确保 local/H2 测试环境可读取指标。
- `backend/src/test/java/com/cgcpms/config/ActuatorMetricsTest.java`：补充 `metrics` 列表、JVM 指标、HikariCP 指标和未登录拒绝回归断言。
- `docs/quality/issue-007-009-jvm-datasource-metrics-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-009 收口为 Done，Ready 队列推进到 ISSUE-007-010。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=ActuatorMetricsTest" test`：先失败后通过；失败原因为鉴权后访问 `/api/actuator/metrics` 返回 `404`，说明端点未暴露。
- `cd backend; .\mvnw.cmd "-Dtest=ActuatorMetricsTest,GlobalWriteRateLimitFilterTest" test`：通过，`9` 个用例通过；确认 `metrics` 仍受鉴权保护，`health` 白名单不回退。
- `cd backend; .\mvnw.cmd test`：未通过；失败点集中在既有 `dashboard`、`invoice`、`workflow`、`payment`、`revenue` 集成测试，不属于本次改动引入。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 生产默认值未改动，线上如需暴露 `metrics` 仍需通过环境变量显式开启。
- 本轮只覆盖本地 profile 与测试链路，未做真实部署环境监控采集验证。

---

Issue：ISSUE-007-010 备份清单脱敏与恢复演练报告模板回归

目标：
- 回归备份范围清单、敏感配置脱敏要求和恢复演练报告模板。
- 确保清单覆盖数据库、MinIO 文件、配置文件、密钥、日志归档。
- 确保模板包含恢复耗时、恢复数据范围和失败原因字段，且文档不包含真实密钥或生产连接串。

修改范围摘要：
- `docs/10-部署运维手册.md`：新增“备份清单与演练记录脱敏规则”，并在恢复演练模板中补入“脱敏检查”字段。
- `docs/quality/issue-007-010-backup-scope-redaction-restore-template.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-010 收口为 Done。

验证命令摘要：
- `git diff --check`：通过。
- 文档人工复核：清单覆盖 MySQL、MinIO、配置/证书、密钥元数据、日志归档；模板包含恢复耗时、恢复数据范围、失败原因分类/说明与脱敏检查。

失败分类或非失败分类：非失败分类
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 文档已明确脱敏要求，但外部工单、截图和主机侧台账仍依赖执行人持续遵守同一脱敏口径。
- 仓库内文档不能替代真实主机 secret 管理和周期性恢复演练落实。

---

Backlog 拆解：Ready 队列补充（P1 第二轮）

来源：
- `docs/backlog/current-focus.md`
- `docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.5 P1-2`、`7.7 P1-4`

新增 Ready Issue：
- `ISSUE-005-008`：核心列表列宽/固定列/金额日期格式统一回归。
- `ISSUE-007-011`：CPU/内存/进程指标回归。
- `ISSUE-007-012`：Redis 健康与黑名单降级告警回归。
- `ISSUE-007-013`：慢 SQL 监控口径回归。
- `ISSUE-007-014`：访问日志 userId/tenantId 字段回归。

拆解边界：
- 本轮只更新 backlog 与 iteration，不执行新拆出的业务任务。
- 新任务均保持在 `current-focus` 当前允许 Epic 内，不扩大到总工程师、BIM、AI、生产发布、生产数据库连接或 migration 改动。
- 每个 Issue 均保留最小验证命令与正式归档路径，供后续单轮串行执行。

---

Issue：ISSUE-005-008 核心列表列宽/固定列/金额日期格式统一回归

目标：
- 回归核心列表页的列宽、固定列、金额格式和日期格式统一口径。
- 不改后端接口语义，不扩展详情页或新业务字段。

修改范围摘要：
- `frontend-admin/src/composables/listTablePresets.ts`：新增共享金额格式与列表列 preset，统一金额/日期/时间/状态/操作列口径。
- `frontend-admin/src/pages/project/index.vue`：项目列表金额列、状态列、操作列接入共享 preset，并移除本地万金额式函数。
- `frontend-admin/src/pages/contract/composables/useContractLedger.ts`：合同列表金额列、签订日期列、状态列、操作列接入共享 preset，并移除本地万金额式函数。
- `frontend-admin/src/pages/payment/pageConfig.ts`、`frontend-admin/src/pages/payment/index.vue`：付款列表金额列、状态列、操作列和金额展示复用共享 helper。
- `frontend-admin/src/pages/settlement/pageConfig.ts`、`frontend-admin/src/pages/settlement/index.vue`：结算列表金额列、状态列、创建时间列、操作列和金额展示复用共享 helper。
- `frontend-admin/src/pages/receipt/composables/useReceiptList.ts`、`frontend-admin/src/pages/requisition/composables/useRequisitionList.ts`：验收/领料列表金额格式、日期列、状态列和操作列接入共享 preset。
- `frontend-admin/src/pages/invoice/composables/useInvoiceList.ts`：发票列表金额格式、金额列、日期列、时间列、状态列和操作列接入共享 preset。
- `frontend-admin/src/pages/__tests__/list-column-format-consistency.test.ts`：新增源码级一致性回归测试。
- `docs/quality/issue-005-008-list-format-column-consistency.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-005-008 收口为 Done，Ready 队列推进到 ISSUE-007-011。

验证命令摘要：
- `cd frontend-admin; pnpm exec vitest run src/pages/__tests__/list-column-format-consistency.test.ts`：先失败后通过，`1` 个文件、`3` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；列表展示口径已统一
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未做真实浏览器窄屏验收，固定列与列宽策略结论基于源码、类型检查和构建结果。
- 仍有部分非本轮代表性页面保留本地金额展示 helper；如需扩展到所有列表页，应另立任务处理。

---

Issue：ISSUE-007-011 CPU/内存/进程指标回归

目标：
- 回归 actuator/prometheus 下 CPU、内存和进程级基础指标可读性。
- 确保本地可验证相关指标已注册且可通过 actuator/prometheus 读取。
- 不引入外部监控平台，不修改生产部署配置，不放宽鉴权边界。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/config/PrometheusRegistryConfig.java`：补充最小 Prometheus registry fallback，避免本地应用上下文缺少 `PrometheusMeterRegistry` 时抓取端点不可用。
- `backend/src/main/java/com/cgcpms/config/PrometheusScrapeController.java`：新增受现有 actuator 安全链路保护的 `/actuator/prometheus` 抓取端点。
- `backend/src/test/java/com/cgcpms/config/ActuatorMetricsTest.java`：补充 CPU、内存、进程与 `prometheus` 抓取回归断言，并扩展未登录拒绝访问断言。
- `docs/quality/issue-007-011-process-memory-metrics-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-011 收口为 Done，Ready 队列推进到 ISSUE-007-012。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=ActuatorMetricsTest" test`：先失败后通过；失败原因为鉴权后访问 `/api/actuator/prometheus` 返回 `404`，说明抓取端点未注册。
- `cd backend; .\mvnw.cmd test`：首次因命令超时未形成质量结论；放宽超时后再次运行未通过，失败点集中在既有 `dashboard`、`invoice`、`workflow`、`payment`、`revenue`、`purchase`、`migration` 集成测试，不属于本次改动引入。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败；首次全量测试存在工具执行时限问题
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未做真实运行态 Prometheus 抓取，只覆盖本地 MockMvc 与 meter registry 注册断言。
- `executor.completed` 相关 Prometheus duplicate tag key 警告仍存在，但当前抓取结果与断言未受影响。

---

Issue：ISSUE-007-012 Redis 健康与黑名单降级告警回归

目标：
- 回归 Redis 健康口径与 Token blacklist 相关降级告警信号。
- 确保本地可验证 `BLACKLIST_UNAVAILABLE`、`TOKEN_BLACKLIST_WRITE_FAILED`、`TOKEN_BLACKLIST_CHECK_FAILED` 等关键口径。
- 不修改生产 Redis 配置，不把生产 Redis 强依赖降级为“正常运行”语义。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/config/TokenBlacklistHealthIndicator.java`：新增 blacklist 健康组件；prod 缺少服务返回 `DOWN`，local 缺少服务返回 `UNKNOWN`，Redis 探测失败返回 `DOWN`。
- `backend/src/main/java/com/cgcpms/auth/service/TokenBlacklistService.java`：新增 `isAvailable()` 探针，并将 Redis 异常日志改为固定告警码 + 异常类型，避免输出 Redis 连接串或密码。
- `backend/src/main/java/com/cgcpms/auth/controller/AuthController.java`：refresh/logout prod fail-close 分支补充 `BLACKLIST_UNAVAILABLE` / `TOKEN_BLACKLIST_WRITE_FAILED` 告警日志。
- `backend/src/test/java/com/cgcpms/config/TokenBlacklistHealthIndicatorTest.java`：新增 health indicator 回归测试。
- `backend/src/test/java/com/cgcpms/auth/service/TokenBlacklistServiceTest.java`、`backend/src/test/java/com/cgcpms/auth/controller/AuthControllerTest.java`、`backend/src/test/java/com/cgcpms/auth/filter/JwtAuthenticationFilterTest.java`：补充三类告警码、fail-close 和日志脱敏断言。
- `docs/quality/issue-007-012-redis-blacklist-observability.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-012 收口为 Done，Ready 队列推进到 ISSUE-007-013。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=TokenBlacklistServiceTest,TokenBlacklistHealthIndicatorTest" test`：先失败，原因是缺少 `TokenBlacklistHealthIndicator` 和 `TokenBlacklistService.isAvailable()`。
- `cd backend; .\mvnw.cmd "-Dtest=AuthControllerTest,JwtAuthenticationFilterTest" test`：先失败，原因是 `AuthController` 的 prod refresh fail-close 分支未输出 `BLACKLIST_UNAVAILABLE` / `TOKEN_BLACKLIST_WRITE_FAILED`。
- `cd backend; .\mvnw.cmd "-Dtest=TokenBlacklistServiceTest,TokenBlacklistHealthIndicatorTest,JwtAuthenticationFilterTest,AuthControllerTest" test`：修复后通过，`28` 个用例通过。
- `cd backend; .\mvnw.cmd test`：未通过；失败类未命中本轮 Redis blacklist 相关目标测试，按既有无关后端测试红灯分类。失败类包括 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`PayRecordControllerTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest`、`WorkflowApproverResolverTest`、`WorkflowConcurrencyTest`、`WorkflowCoreServiceTest`、`WorkflowEngineIntegrationTest`、`WorkflowTemplateManagementTest`。
- `git diff --check`：通过，仅有换行符转换提示。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮不连接真实 Redis，不验证外部监控平台采集，只做本地健康组件、日志告警码和 fail-close 回归。
- local profile 仍允许 blacklist 服务缺失时继续请求，但健康组件返回 `UNKNOWN + BLACKLIST_UNAVAILABLE`，不会把该状态伪装成正常。

---

Issue：ISSUE-007-013 慢 SQL 监控口径回归

目标：
- 回归项目内慢 SQL 监控口径，明确阈值、日志/指标输出和测试覆盖。
- 不引入外部 APM，不修改生产数据库配置。
- 不输出完整 SQL 中的敏感值或连接串。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/common/aspect/SlowSqlObservationAspect.java`：新增 mapper 调用级慢 SQL 观测切面；默认阈值为 `observability.slow-sql.threshold-ms:500`；输出 `SLOW_SQL_DETECTED` 日志、`db.sql.duration` timer 和 `db.sql.slow.count` counter。
- `backend/src/test/java/com/cgcpms/common/aspect/SlowSqlObservationAspectTest.java`：新增阈值、指标、非慢调用不误报和敏感参数不泄露回归测试。
- `docs/quality/issue-007-013-slow-sql-observability.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-013 收口为 Done，Ready 队列推进到 ISSUE-007-014。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=SlowSqlObservationAspectTest" test`：先失败后通过；失败原因为缺少 `SlowSqlObservationAspect`，修复后 `3` 个用例通过。
- `cd backend; .\mvnw.cmd test`：首次失败暴露 `SlowSqlObservationAspect` 多构造器未显式标注注入构造器，已补充 `@Autowired` 修复；复验后仍未通过，失败类集中在既有 `dashboard`、`invoice`、`workflow`、`payment`、`revenue`、`purchase`、`migration` 集成测试，不属于本次改动引入。
- `cd backend; .\mvnw.cmd "-Dtest=AccountingEntryControllerTest" test`：修复后通过，确认 Spring 上下文可加载慢 SQL 切面。
- `git diff --check`：通过，仅有换行符转换提示。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮观测粒度为 mapper 方法调用耗时，不在日志中输出完整 SQL 或绑定参数。
- `operation` 指标 tag 以 mapper 方法名为维度，后续 mapper 数量继续增长时需关注指标基数。

---

Issue：ISSUE-007-014 访问日志 userId/tenantId 字段回归

目标：
- 回归访问日志中的 `userId`、`tenantId` 字段口径。
- 确保成功请求、匿名请求、异常请求都有稳定断言。
- 日志不得泄露 Token、Cookie、密码、完整请求体等敏感内容。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/common/filter/TraceIdFilter.java`：`HTTP_ACCESS` 新增 `userId`、`tenantId` 字段；无法识别身份时输出 `-`。
- `backend/src/main/java/com/cgcpms/auth/filter/JwtAuthenticationFilter.java`：JWT 校验通过后将 `userId`、`tenantId` 写入请求属性，供最外层访问日志在认证上下文清理后读取。
- `backend/src/test/java/com/cgcpms/common/filter/TraceIdFilterLoggingTest.java`：补充成功、匿名、异常请求下身份字段和敏感信息不泄漏断言。
- `backend/src/test/java/com/cgcpms/auth/filter/JwtAuthenticationFilterTest.java`：补充认证成功后访问日志身份请求属性断言。
- `docs/quality/issue-007-014-access-log-user-tenant-context.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-014 收口为 Done，Ready 队列标记为当前无 Ready Issue。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=TraceIdFilterLoggingTest,JwtAuthenticationFilterTest" test`：先失败后通过；失败原因为访问日志缺少 `userId`/`tenantId` 且 JWT 过滤器未写入访问日志身份请求属性，修复后 `7` 个用例通过。
- `cd backend; .\mvnw.cmd test`：未通过，失败类集中在既有 `dashboard`、`invoice`、`workflow`、`payment`、`revenue`、`purchase`、`migration` 集成测试，不属于本次改动引入。
- `git diff --check`：通过，仅有换行符转换提示。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮只覆盖应用内访问日志格式与本地单元测试，不验证外部日志平台字段解析规则。
- 异步请求若在认证过滤器之外创建新的请求链路，仍需依赖请求属性或 `UserContext` 正确传递；本轮未扩展异步日志链路。

---

Issue：ISSUE-006-009 上传文件 hash 生成与重复文件口径回归

目标：
- 回归上传链路中的文件 hash 生成、持久化与重复文件判定口径。
- 确保同内容文件不会绕过既有审计与业务绑定约束。
- 确保合法非重复文件上传不回退。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`：上传文件以 `SHA-256` 内容摘要生成 `{sha256}.{extension}` 存储文件名，并通过既有 `file_name/storage_path` 持久化 hash；同租户、同业务对象、同 hash 文件在写对象存储前拒绝，返回 `FILE_DUPLICATE`。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：补充 hash 文件名/路径持久化、重复内容拒绝且不二次写 MinIO、不同内容合法上传三类回归断言。
- `docs/quality/issue-006-009-file-hash-duplication-guard.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-009 收口为 Done，Ready 队列推进到 ISSUE-006-010。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testUploadStoresStableContentHashInFileNameAndStoragePath+testUploadRejectsDuplicateContentForSameBusinessObject+testUploadAllowsDifferentContentForSameBusinessObject" test`：先失败后通过；失败原因为既有实现使用 UUID 文件名且未拦截重复内容，修复后 `3` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest" test`：通过，`25` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，`41` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd backend; .\mvnw.cmd test`：未通过；失败类集中在既有 `dashboard`、`invoice`、`workflow`、`payment`、`revenue`、`purchase`、`migration` 集成测试，不属于本次文件上传 hash 改动引入。
- `git diff --check`：通过，仅有换行符转换提示。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未新增独立 `file_hash` 数据库字段，hash 通过既有 `file_name/storage_path` 持久化；如后续需要独立字段或唯一索引，需另立 migration 任务确认。
- 重复判定范围限定为同租户同业务对象，不做跨业务对象或跨租户级去重。

---

Issue：ISSUE-006-010 文件业务对象绑定完整性回归

目标：
- 回归文件与业务对象的绑定校验，确保孤儿附件、错误业务对象或越权绑定在后端被拒绝。
- 合法业务对象绑定路径不回退，既有下载、删除、鉴权接口保持可用。
- 前端失败提示与后端错误原因一致。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`：抽出并复用 `validateBusinessBindingParams`；上传、列表和显式读权限检查入口先校验 `businessType/businessId`，再进入 authorizer 或数据库查询。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：新增非法业务类型、业务对象不存在、越权绑定、列表读权限失败和临时链接不生成的回归断言。
- `docs/quality/issue-006-010-file-biz-binding-integrity.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-010 收口为 Done，Ready 队列推进到 ISSUE-006-011。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testUploadRejectsInvalidBusinessTypeBeforeAuthorizer+testUploadRejectsMissingBusinessObjectBeforeSideEffects+testUploadRejectsUnauthorizedBusinessObjectBeforeSideEffects+testListByBusinessRejectsInvalidBusinessTypeBeforeAuthorizer+testListByBusinessRejectsReadDeniedBeforeTemporaryUrlGeneration" test`：先失败后通过；失败原因为非法 `businessType` 未在 authorizer/list 查询前统一拒绝，修复后 `5` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，`46` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd backend; .\mvnw.cmd test`：未通过；失败类集中在既有 `dashboard`、`invoice`、`workflow`、`payment`、`revenue`、`purchase`、`migration` 集成测试，不属于本次文件绑定改动引入。
- `git diff --check`：通过，仅有换行符转换提示。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮不新增文件绑定表或 schema，绑定完整性依赖既有 `businessType/businessId` 与 `BusinessObjectAuthorizer`。
- 新增业务类型仍需在 authorizer 中补充存在性、租户与项目关系口径。
- 本轮未做真实 MinIO 集成验收，MinIO 交互由 MockBean 覆盖。

---

Issue：ISSUE-006-011 发票识别记录与人工确认审计回归

目标：
- 回归发票识别与人工确认链路的审计记录，确保识别成功、识别失败、人工确认三类关键动作均可追踪。
- 审计记录不得泄露票据图片直链、凭据、token 或完整敏感载荷。
- 合法识别与人工确认流程不回退，前端提示与后端结果一致。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/audit/InvoiceRecognitionAuditAspect.java`：新增发票识别与人工确认专用审计切面，补充 `INVOICE_RECOGNITION` 与 `INVOICE_MANUAL_CONFIRM` 事件。
- `backend/src/test/java/com/cgcpms/file/InvoiceRecognitionAuditAspectTest.java`：新增识别成功、识别失败、人工确认三类审计断言，并覆盖敏感信息不泄漏。
- `docs/quality/issue-006-011-invoice-recognition-audit-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-011 收口为 Done，Ready 队列推进到 ISSUE-006-012。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceRecognitionAuditAspectTest" test`：先失败后通过；失败原因为缺少发票识别与人工确认专用审计事件，修复后 `3` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceRecognitionAuditAspectTest,InvoiceRecognitionTest,InvoiceServiceTest,OperationAuditAspectTest,OperationAuditServiceTest" test`：通过，`44` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd backend; .\mvnw.cmd test`：工具层 120 秒超时后 Surefire 报告继续生成；汇总为 `1540` 个测试、`10` 个 failures、`30` 个 errors、`1` 个 skipped，失败类集中在既有 `dashboard`、`invoice validation`、`workflow`、`payment`、`revenue`、`purchase`、`migration` 测试，不属于本次审计补强引入。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败；全量 Maven 命令首次存在工具执行时限问题
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未验证外部日志平台或审计报表展示，结论基于 Spring 事件发布与 MockMvc 回归测试。
- 当前为专用补充审计事件，保留既有通用 `@AuditedOperation` 审计事件；如后续需要统一审计去重或展示聚合，应另立任务处理。

---

Issue：ISSUE-006-012 病毒扫描预留状态与失败兜底回归

目标：
- 回归病毒扫描预留状态、错误码或扩展点口径，确保未接入真实查毒服务时行为明确。
- 不把“未扫描”“未接入能力”伪装为“已完成安全扫描”或“安全通过”。
- 合法上传主流程仍按既定策略工作，不引入误拦截或静默放行。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/file/vo/FileVirusScanStatus.java`：新增 `NOT_SCANNED`、`NOT_CONFIGURED`、`FAILED` 三类预留状态，全部为 `passed=false`。
- `backend/src/main/java/com/cgcpms/file/vo/SysFileVO.java`：新增 `virusScanStatus`、`virusScanCode`、`virusScanMessage`、`virusScanPassed`。
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`：上传和列表响应默认返回 `NOT_CONFIGURED`，不阻断合法上传，不伪装为安全检查通过。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：新增合法上传预留状态和三类非通过状态断言。
- `frontend-admin/src/types/file.ts`、`frontend-admin/src/types/__tests__/file.test.ts`：同步前端类型与状态枚举，确保不暴露 `PASSED` 或“已安全扫描”口径。
- `docs/quality/issue-006-012-virus-scan-placeholder-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-006-012 收口为 Done，Ready 队列推进到 ISSUE-007-015。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=FileServiceTest#testUploadReturnsVirusScanPlaceholderWithoutSafePass+testReservedVirusScanStatusesNeverPass" test`：先失败后通过；失败原因为缺少病毒扫描状态字段/枚举且文案仍含“安全通过”误导词组，修复后 `2` 个用例通过。
- `cd frontend-admin; pnpm exec vitest run src/types/__tests__/file.test.ts`：先失败后通过；失败原因为前端缺少病毒扫描状态枚举，修复后 `1` 个文件、`2` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=*File*" test`：通过，`48` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd backend; .\mvnw.cmd test`：未通过；Surefire 汇总 `1542` 个测试、`10` 个 failures、`30` 个 errors、`1` 个 skipped，失败类集中在既有 `dashboard`、`invoice validation`、`workflow`、`payment`、`revenue`、`purchase`、`migration` 测试，不属于本次文件病毒扫描预留口径引入。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮不提供真实病毒扫描能力，只提供预留状态与失败兜底口径。
- 当前未新增 `sys_file` 扫描状态持久化字段；如后续接入真实查毒服务并需要逐文件持久化状态，需要另立 migration 任务确认。
- 本轮未做真实 MinIO 集成验收，文件存储交互由 MockBean 覆盖。

---

Issue：ISSUE-007-015 访问日志 traceId/requestId 透传与响应头回归

目标：
- 回归访问日志中的 `traceId`、`requestId` 字段透传、生成与响应头回写。
- 确保成功请求、匿名请求、异常请求均可稳定关联。
- 日志与响应头不得泄露 token、cookie、密码、完整请求体等敏感内容。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/common/filter/TraceIdFilterLoggingTest.java`：补充成功、匿名、异常请求下 `traceId/requestId` 响应头与访问日志一致性断言。
- `backend/src/test/java/com/cgcpms/common/filter/TraceIdFilterLoggingTest.java`：补充缺失标识时生成 32 位十六进制关联 ID 的可断言口径。
- `backend/src/test/java/com/cgcpms/common/filter/TraceIdFilterLoggingTest.java`：补充 Authorization、Cookie、password、token、请求体敏感值和异常消息敏感值不泄漏断言。
- `docs/quality/issue-007-015-trace-request-id-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-007-015 收口为 Done，Ready 队列标记为当前无 Ready Issue。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=TraceIdFilterLoggingTest" test`：通过，`3` 个用例通过；当前生产实现已满足 traceId/requestId 透传、生成、响应头回写和访问日志字段输出，本轮未修改生产代码。
- `cd backend; .\mvnw.cmd test`：未通过，失败类集中在既有 `dashboard`、`invoice validation`、`workflow`、`payment`、`revenue`、`purchase`、`migration` 集成测试，不属于本次访问日志 trace/request 回归引入。
- `git diff --check`：通过。

失败分类或非失败分类：现有生产实现满足目标；测试门禁已补强；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮只覆盖应用内访问日志与响应头行为，不验证外部日志平台字段解析、索引或链路追踪展示。
- `TraceIdContext` 当前只保存 `traceId`，`requestId` 通过 MDC 与访问日志覆盖；如未来业务代码需要直接读取 `requestId` 上下文，需另立任务扩展上下文对象。

---

Issue：ISSUE-004-007 合同清单金额与付款条件回归

目标：
- 回归合同主表、合同清单和付款条件之间的金额、日期与状态口径。
- 确保来源单据与汇总字段一致。
- 不改合同业务语义，不扩大为合同模块重构或数据库结构调整。

修改范围摘要：
- `backend/src/test/java/com/cgcpms/contract/ContractCompositeSaveTest.java`：新增合同复合保存一致性回归，覆盖合同头金额、清单合计、付款条件合计、付款比例合计、合同日期、付款计划日期和状态字段。
- `docs/quality/issue-004-007-contract-payment-terms-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-004-007 收口为 Done，Ready 队列推进到 ISSUE-004-008。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=ContractCompositeSaveTest" test`：通过，`7` 个用例通过；当前生产实现已满足合同金额、清单合计、付款条件金额/日期/状态稳定落库口径，本轮未修改生产代码。
- `cd backend; .\mvnw.cmd test`：未通过，失败类集中在既有 `dashboard`、`invoice validation`、`workflow`、`payment`、`revenue`、`purchase`、`migration` 集成测试，不属于本次合同清单与付款条件回归引入。
- `git diff --check`：通过。

失败分类或非失败分类：现有生产实现满足目标；测试门禁已补强；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮只覆盖后端合同复合保存，不覆盖前端合同表单展示和真实浏览器交互。
- 本轮不新增合同金额自动校验或自动重算规则；如需保存时强制拒绝清单合计与合同金额不一致，需另立业务规则确认任务。

---

Issue：ISSUE-004-008 签证变更成本与收入调整回归

目标：
- 回归签证、合同变更对成本与收入调整链路的影响，确保调整结果与来源单据一致。
- 覆盖重复审批回调下的重复累计或漏记风险。
- 不扩大为收入、成本、合同模块重构，不修改已存在的 migration。

修改范围摘要：
- `backend/src/main/java/com/cgcpms/contract/change/handler/CtContractChangeWorkflowHandler.java`：新增合同变更审批回调幂等退出，避免已审批、已生效且成本已生成的合同变更再次递增合同 `currentAmount`。
- `backend/src/test/java/com/cgcpms/contract/change/handler/CtContractChangeWorkflowHandlerTest.java`：新增合同变更审批重复回调断言，覆盖 `CT_CHANGE` 成本项来源、金额、项目、合同和状态一致性。
- `backend/src/test/java/com/cgcpms/variation/handler/VarOrderWorkflowHandlerTest.java`：新增 COST 方向签证审批断言，覆盖 `VAR_ORDER` 成本项与签证明细金额合计一致，重复回调不重复生成。
- `backend/src/test/java/com/cgcpms/revenue/ContractRevenueServiceTest.java`：新增收入确认审批重复回调断言，覆盖 `CT_REVENUE` 收入调整项与来源收入确认单一致。
- `docs/quality/issue-004-008-variation-cost-revenue-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-004-008 收口为 Done，Ready 队列推进到 ISSUE-004-009。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=CtContractChangeWorkflowHandlerTest,VarOrderWorkflowHandlerTest,ContractRevenueServiceTest" test`：先失败，新增合同变更重复回调断言暴露 `currentAmount` 被二次递增；同次运行中既有 `ContractRevenueServiceTest.testSubmitForApproval` 失败，分类为既有无关断言问题。
- `cd backend; .\mvnw.cmd "-Dtest=CtContractChangeWorkflowHandlerTest,VarOrderWorkflowHandlerTest,ContractRevenueServiceTest#testOnApproved_RevenueAdjustmentIsIdempotent" test`：通过，`15` 个用例通过。
- `cd backend; .\mvnw.cmd test`：未通过，失败类集中在既有 `dashboard`、`invoice validation`、`migration`、`payment`、`purchase`、`revenue submitForApproval`、`workflow` 集成测试；本轮目标回归断言已通过。
- `git diff --check`：通过。

失败分类或非失败分类：真实代码质量问题已修复；全量测试存在既有无关失败
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 全量后端测试仍有既有无关红灯，需按对应 Ready Issue 分别治理。
- 本轮未修复收入确认提交审批既有失败断言；收入调整链路以审批回调 `onApproved` 幂等为本 Issue 验收口径。
