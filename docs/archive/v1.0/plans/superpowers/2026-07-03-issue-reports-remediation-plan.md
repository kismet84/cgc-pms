# 2026-07-03 问题报告修复计划书

来源报告：
- `docs/issue/CGC-PMS-生产事故诊断报告-20260702.md`
- `docs/issue/CGC-PMS-全量代码审计报告-20260703-074400.md`

主线结论：先处理已造成生产白屏的成本科目树环引用问题，再闭环全量审计中的高危安全、数据一致性和部署风险；中低危事项进入分批治理，避免一次性大改导致回归面失控。

## 一、任务拆解

### 模块 A：生产事故止血与防复发

**任务 A1：成本科目树递归环检测加固**
- 简要说明：为 `CostSubjectService.getTree()` 的树构建逻辑增加环检测，避免 `cost_subject` 出现循环 parent_id 时触发 `StackOverflowError` 并导致 `/dashboard` 白屏。
- 输入/输出：
  - 输入：`cost_subject` 全量科目数据、当前租户上下文、`parent_id` 分组结果。
  - 输出：可容错返回的科目树；检测到环时跳过异常分支并记录告警日志。
- 涉及的文件建议：
  - 🔨修改 `backend/src/main/java/com/cgcpms/cost/service/CostSubjectService.java`
  - ✨新增或🔨修改 `backend/src/test/java/com/cgcpms/cost/service/CostSubjectServiceTest.java`
- 复杂度：P0（核心阻塞）
- 验收标准：
  - Given `cost_subject` 存在 `900001 -> 0 -> 900001` 循环，When 调用成本科目树接口，Then 接口不抛出 `StackOverflowError`，返回结果中异常分支被跳过或明确降级。
  - Given 正常多层科目树，When 调用树接口，Then 层级、排序、字段格式与原行为一致。
  - Given 检测到循环引用，When 查看后端日志，Then 能定位租户、科目 id 和循环风险。
- 依赖：无

**任务 A2：成本科目写入侧 parent_id 合法性校验**
- 简要说明：在新增/更新成本科目时禁止自身挂载、禁止 id=0 僵尸数据参与业务、禁止跨租户父节点，降低循环数据再次进入系统的概率。
- 输入/输出：
  - 输入：`CostSubject` create/update 请求体、当前租户、父科目记录。
  - 输出：合法数据写入；非法 parent_id 返回业务异常。
- 涉及的文件建议：
  - 🔨修改 `backend/src/main/java/com/cgcpms/cost/service/CostSubjectService.java`
  - ✨新增或🔨修改 `backend/src/test/java/com/cgcpms/cost/service/CostSubjectServiceTest.java`
- 复杂度：P0（核心阻塞）
- 验收标准：
  - Given 更新科目时 `parentId == id`，When 提交保存，Then 返回明确业务错误。
  - Given `parentId` 指向其他租户科目，When 提交保存，Then 返回父科目不存在或无权访问。
  - Given 根节点保存，When `parentId` 为空或 0，Then 可按现有规则保存为根节点。
- 依赖：任务 A1

**任务 A3：异常数据扫描与清理脚本**
- 简要说明：提供只读扫描脚本识别树表循环、孤儿 parent_id、id=0 异常记录；清理动作必须单独审核执行。
- 输入/输出：
  - 输入：`cost_subject` 表、后续可扩展到部门/分类等树表。
  - 输出：异常数据报告；必要时输出待人工确认的清理 SQL。
- 涉及的文件建议：
  - ✨新增 `scripts/check-tree-integrity.sql` 或 `scripts/check_tree_integrity.py`
  - ✨新增 `docs/quality/tree-integrity-check-2026-07-03.md`
- 复杂度：P1（重要）
- 验收标准：
  - Given 存在循环 parent_id，When 执行扫描，Then 报告能列出循环链路。
  - Given 存在孤儿 parent_id，When 执行扫描，Then 报告能列出缺失父节点。
  - Given 生产数据扫描，When 未显式传入清理参数，Then 不修改任何数据。
- 依赖：任务 A1

### 模块 B：高危审计项优先修复

**任务 B1：付款申请已审批金额查询补租户过滤（VUL-024）**
- 简要说明：`PayApplicationService.getApprovedSumForContract()` 查询已 APPROVED 付款申请时补充 tenantId 条件，避免跨租户金额汇总污染付款校验。
- 输入/输出：
  - 输入：合同 id、排除的付款申请 id、当前租户 id。
  - 输出：仅当前租户范围内的已审批申请金额合计。
- 涉及的文件建议：
  - 🔨修改 `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java`
  - ✨新增或🔨修改 `backend/src/test/java/com/cgcpms/payment/service/PayApplicationServiceTest.java`
- 复杂度：P0（核心阻塞）
- 验收标准：
  - Given 两个租户存在相同 contractId 或脏数据，When 校验付款金额，Then 只统计当前租户付款申请。
  - Given `excludePayAppId` 不为空，When 汇总金额，Then 当前申请不会被重复计入。
  - Given 正常付款审批流程，When 提交申请，Then 原有防超付规则仍生效。
- 依赖：无

**任务 B2：事务 rollbackFor 统一修复（VUL-014）**
- 简要说明：分批为核心业务服务的 `@Transactional` 增加 `rollbackFor = Exception.class`，优先覆盖合同、付款、发票、库存、成本、审批等资金和状态流模块。
- 输入/输出：
  - 输入：现有服务层事务方法、异常抛出路径。
  - 输出：受检异常与运行时异常均触发事务回滚。
- 涉及的文件建议：
  - 🔨修改 `backend/src/main/java/com/cgcpms/**/service/*.java`
  - 🔨修改相关 `backend/src/test/java/com/cgcpms/**` 事务回归测试
- 复杂度：P1（重要）
- 验收标准：
  - Given 服务方法在写入后抛出受检异常，When 事务结束，Then 数据库无半提交记录。
  - Given 服务方法抛出业务运行时异常，When 事务结束，Then 原有回滚行为不变。
  - 检查项：禁止只机械替换注解而不跑核心业务回归。
- 依赖：任务 B1 可并行

**任务 B3：数据库索引专项迁移（VUL-016/VUL-017）**
- 简要说明：基于高频查询列补充复合索引，优先覆盖 `cost_item`、`pay_record`、`ct_contract`、`wf_instance` 及报告点名的成本、付款、审批链路。
- 输入/输出：
  - 输入：现有 Mapper 查询条件、慢查询/报告建议、当前 Flyway 版本。
  - 输出：新增版本化 Flyway migration，不修改已应用脚本。
- 涉及的文件建议：
  - ✨新增 `backend/src/main/resources/db/migration/V*_add_audit_recommended_indexes.sql`
  - ✨新增 `backend/src/main/resources/db/migration-h2/V*_add_audit_recommended_indexes.sql`
  - 🔨修改或新增数据库相关测试
- 复杂度：P1（重要）
- 验收标准：
  - Given 新库执行 Flyway，When 应用全部迁移，Then MySQL/H2 均可启动且索引存在。
  - Given 典型列表查询，When 查看执行计划或集成测试，Then 能使用新增索引或至少不退化。
  - 检查项：不得修改已应用 Flyway 迁移脚本。
- 依赖：需先确认当前最新 migration 版本

**任务 B4：部署凭据与镜像可复现治理（VUL-009/VUL-010/VUL-011）**
- 简要说明：清理 `deploy/.env` 实际凭据风险，固定部署镜像标签，评估生产 JDBC SSL 参数。
- 输入/输出：
  - 输入：`deploy/.env`、`.env.example`、compose 文件、生产数据库连接要求。
  - 输出：示例配置与真实密钥分离；部署镜像版本可追踪；SSL 配置有环境化开关。
- 涉及的文件建议：
  - 🔨修改 `deploy/.env.example`
  - 🔨修改 `deploy/docker-compose.deploy.yml`
  - 🔨修改 `deploy/docker-compose.prod.yml`
  - ✨新增 `docs/quality/deploy-secret-remediation-2026-07-03.md`
- 复杂度：P1（重要）
- 验收标准：
  - Given 仓库文件扫描，When 搜索 JWT/Jasypt/MinIO 等真实密钥，Then 仅示例占位符存在。
  - Given 部署 compose，When 检查镜像配置，Then 不再使用 `:latest` 作为生产部署标签。
  - Given 生产数据库要求 SSL，When 使用 prod profile，Then JDBC SSL 参数可启用且文档说明清晰。
- 依赖：需人工确认真实密钥轮换窗口

### 模块 C：前端安全与权限闭环

**任务 C1：登录密码复杂度提示与校验（VUL-007）**
- 简要说明：登录页补充密码复杂度提示和客户端校验，但不能替代后端认证；目标是减少弱密码输入和提升错误反馈。
- 输入/输出：
  - 输入：用户名、密码表单字段。
  - 输出：不满足复杂度时前端阻断提交并提示。
- 涉及的文件建议：
  - 🔨修改 `frontend-admin/src/pages/login/index.vue`
  - ✨新增或🔨修改 `frontend-admin/src/pages/login/__tests__/*`
- 复杂度：P1（重要）
- 验收标准：
  - Given 密码少于 8 位，When 点击登录，Then 前端显示明确提示且不发起登录请求。
  - Given 密码缺少字母或数字，When 点击登录，Then 前端显示复杂度提示。
  - Given 合法密码但账号错误，When 点击登录，Then 仍走后端认证并展示原有错误。
- 依赖：无

**任务 C2：路由权限 meta.permission 梳理（VUL-006）**
- 简要说明：为需要权限控制的路由补齐 `meta.permission`，或明确删除无效守卫逻辑；优先选择补齐权限码以对齐后端 `module:action` 模型。
- 输入/输出：
  - 输入：后端权限码、菜单/路由定义、当前用户权限列表。
  - 输出：前端路由守卫能按页面权限做拦截。
- 涉及的文件建议：
  - 🔨修改 `frontend-admin/src/router/index.ts`
  - 🔨修改 `frontend-admin/src/router/**`
  - 🔨修改权限相关测试
- 复杂度：P2（优化）
- 验收标准：
  - Given 用户缺少某页面权限，When 直接访问路由，Then 被重定向到无权限页或安全落点。
  - Given 用户具备权限，When 访问路由，Then 页面正常加载。
  - 检查项：前端权限仅作为体验防线，后端 `@PreAuthorize` 仍是最终准入。
- 依赖：需确认路由与权限码映射表

**任务 C3：生产 console 输出治理（VUL-005）**
- 简要说明：通过 Vite/Terser 配置或统一日志封装移除生产环境 `console.error()` 等调试输出，降低内部状态泄露。
- 输入/输出：
  - 输入：前端构建配置、现有 console 调用。
  - 输出：生产 bundle 不含调试 console；开发环境保留排错能力。
- 涉及的文件建议：
  - 🔨修改 `frontend-admin/vite.config.ts`
  - 🔨修改必要的前端日志工具或调用点
- 复杂度：P3（规划修复）
- 验收标准：
  - Given 执行生产构建，When 检查 bundle，Then 不包含非必要 console 输出。
  - Given 开发环境运行，When 接口失败，Then 仍有足够本地调试信息。
- 依赖：无

### 模块 D：业务校验、并发与数据安全

**任务 D1：批量接口参数校验补齐（VUL-027/VUL-028）**
- 简要说明：`CostTargetController.batchSaveItems()` 补 `@Valid`，`SysUserController.updateStatus()` 改为 DTO + `@Pattern`，减少非法数据穿透到服务层。
- 输入/输出：
  - 输入：目标成本明细列表、用户状态更新请求。
  - 输出：字段级校验自动触发；非法状态值返回 400 或统一业务错误。
- 涉及的文件建议：
  - 🔨修改 `backend/src/main/java/com/cgcpms/cost/controller/CostTargetController.java`
  - 🔨修改 `backend/src/main/java/com/cgcpms/system/controller/SysUserController.java`
  - ✨新增 `backend/src/main/java/com/cgcpms/system/dto/UpdateUserStatusRequest.java`
  - 🔨修改对应 Controller 测试
- 复杂度：P1（重要）
- 验收标准：
  - Given 批量明细字段不合法，When 调用保存接口，Then 字段级校验生效。
  - Given 用户状态为非法字符串，When 调用状态更新接口，Then 请求被拒绝且不会调用状态变更。
  - Given 状态为 `ENABLE` 或 `DISABLE`，When 调用接口，Then 行为与原功能一致。
- 依赖：无

**任务 D2：库存并发与负数防御（VUL-025/VUL-026）**
- 简要说明：为验收入库累计收料量和库存可用量更新增加并发保护与负数防御，避免并发验收或异常调用造成库存不一致。
- 输入/输出：
  - 输入：验收明细、采购订单明细、库存记录。
  - 输出：并发更新可重试或失败可感知；库存不允许出现非法负数。
- 涉及的文件建议：
  - 🔨修改 `backend/src/main/java/com/cgcpms/receipt/service/MatReceiptService.java`
  - 🔨修改 `backend/src/main/java/com/cgcpms/inventory/service/MatStockService.java`
  - 🔨修改采购订单明细实体或 Mapper 锁定查询
  - 🔨修改并发/库存测试
- 复杂度：P2（优化）
- 验收标准：
  - Given 两个请求同时验收同一采购订单明细，When 并发提交，Then 收料量不会丢失更新。
  - Given 出库数量超过可用库存，When 调用库存扣减，Then 返回明确错误且不写入负库存。
  - Given 正常入库/出库，When 操作完成，Then 台账和库存余额一致。
- 依赖：任务 D1 可并行

**任务 D3：实体直接暴露修复（VUL-003）**
- 简要说明：`CtContractChangeController.getById` 改为返回 VO，避免实体内部字段泄露并统一 API 契约。
- 输入/输出：
  - 输入：变更单 id、当前租户/权限。
  - 输出：`CtContractChangeVO` 或等价安全响应对象。
- 涉及的文件建议：
  - 🔨修改 `backend/src/main/java/com/cgcpms/contract/controller/CtContractChangeController.java`
  - ✨新增或🔨修改 `backend/src/main/java/com/cgcpms/contract/vo/CtContractChangeVO.java`
  - 🔨修改对应测试和前端类型
- 复杂度：P3（规划修复）
- 验收标准：
  - Given 查询变更单详情，When 接口返回，Then 不包含内部审计、删除标记等不应暴露字段。
  - Given 前端详情页加载，When 使用新 VO，Then 页面字段完整且无类型错误。
- 依赖：需确认前端依赖字段

### 模块 E：工程化质量门禁与可观测性

**任务 E1：CI/CD 最小质量门禁（VUL-008）**
- 简要说明：建立最小流水线，至少包含后端测试、前端类型检查/构建、基础 secret 扫描和静态检查。
- 输入/输出：
  - 输入：当前 Maven、pnpm、Docker 构建命令。
  - 输出：可在 GitHub Actions/GitLab CI/Jenkins 中运行的质量门禁配置。
- 涉及的文件建议：
  - ✨新增 `.github/workflows/ci.yml` 或团队实际 CI 配置文件
  - 🔨修改 `docs/10-部署运维手册.md`
- 复杂度：P1（重要）
- 验收标准：
  - Given 新提交触发 CI，When 流水线运行，Then 后端测试和前端构建至少执行一次。
  - Given 出现测试失败，When 查看 CI，Then 阻断合并或发布。
  - Given 扫描到疑似密钥，When 查看 CI，Then 明确失败并指向文件。
- 依赖：任务 B4

**任务 E2：Resilience4j 熔断与外部依赖保护（VUL-022）**
- 简要说明：为 MinIO、Redis 等外部依赖调用增加超时、重试、熔断或降级策略，避免单点慢故障拖垮主流程。
- 输入/输出：
  - 输入：文件服务、缓存访问、健康检查调用。
  - 输出：外部依赖异常时返回可控业务错误或降级结果。
- 涉及的文件建议：
  - 🔨修改 `backend/pom.xml`
  - 🔨修改文件/缓存相关 service 和配置
  - 🔨修改 `backend/src/main/resources/application-*.yml`
- 复杂度：P2（优化）
- 验收标准：
  - Given MinIO 超时，When 上传/下载文件，Then 请求在预期时间内失败并返回友好错误。
  - Given Redis 短暂不可用，When 非核心缓存读取失败，Then 不影响可降级业务。
  - 检查项：熔断配置不得掩盖真实数据写入失败。
- 依赖：需先盘点外部调用点

**任务 E3：结构化日志与 Nginx 限速（VUL-012/VUL-013/VUL-023）**
- 简要说明：生产日志增加 JSON 输出选项，Nginx 增加基础限速策略，提升排障和边界防护能力。
- 输入/输出：
  - 输入：`logback-spring.xml`、`frontend-admin/nginx.conf`、部署环境变量。
  - 输出：可接入 ELK/Loki 的结构化日志；对高频请求有入口限速。
- 涉及的文件建议：
  - 🔨修改 `backend/src/main/resources/logback-spring.xml`
  - 🔨修改 `frontend-admin/nginx.conf`
  - 🔨修改部署说明
- 复杂度：P3（规划修复）
- 验收标准：
  - Given prod profile，When 查看应用日志，Then 可输出 JSON 或保留可配置开关。
  - Given 短时间内高频请求，When 经过 Nginx，Then 超限请求被限制且正常请求不受明显影响。
- 依赖：无

## 二、改动文件清单与计划

- 🔨修改 `backend/src/main/java/com/cgcpms/cost/service/CostSubjectService.java`
  - 增加树构建环检测、parent_id 写入校验和异常日志，直接闭环生产白屏根因。
- 🔨修改 `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java`
  - 为已审批付款金额汇总补充租户过滤，防止跨租户金额污染。
- 🔨修改 `backend/src/main/java/com/cgcpms/**/service/*.java`
  - 分批为核心事务方法补 `rollbackFor = Exception.class`，优先资金、库存、审批和成本链路。
- ✨新增 `backend/src/main/resources/db/migration/V*_add_audit_recommended_indexes.sql`
  - 新增 MySQL 索引迁移，不修改既有 Flyway 文件。
- ✨新增 `backend/src/main/resources/db/migration-h2/V*_add_audit_recommended_indexes.sql`
  - 补齐 H2 测试环境迁移，保证测试启动一致。
- 🔨修改 `backend/src/main/java/com/cgcpms/cost/controller/CostTargetController.java`
  - 为批量明细参数补齐嵌套校验。
- 🔨修改 `backend/src/main/java/com/cgcpms/system/controller/SysUserController.java`
  - 用 DTO 替代 `Map<String,String>`，为状态值增加枚举/正则校验。
- ✨新增 `backend/src/main/java/com/cgcpms/system/dto/UpdateUserStatusRequest.java`
  - 承载用户状态更新请求，统一参数校验。
- 🔨修改 `backend/src/main/java/com/cgcpms/receipt/service/MatReceiptService.java`
  - 为采购订单明细累计收料量增加并发保护。
- 🔨修改 `backend/src/main/java/com/cgcpms/inventory/service/MatStockService.java`
  - 增加库存可用量负数防御。
- 🔨修改 `backend/src/main/java/com/cgcpms/contract/controller/CtContractChangeController.java`
  - 改为返回安全 VO，避免实体直接暴露。
- 🔨修改 `frontend-admin/src/pages/login/index.vue`
  - 增加密码复杂度校验和提示。
- 🔨修改 `frontend-admin/src/router/**`
  - 补齐路由权限元信息或收敛无效守卫。
- 🔨修改 `frontend-admin/vite.config.ts`
  - 配置生产构建移除非必要 console 输出。
- 🔨修改 `deploy/.env.example`、`deploy/docker-compose.deploy.yml`、`deploy/docker-compose.prod.yml`
  - 分离真实密钥、固定镜像标签、环境化 MySQL SSL 配置。
- ✨新增 `.github/workflows/ci.yml` 或等价 CI 配置
  - 建立后端、前端、secret 扫描的最小质量门禁。
- ✨新增 `scripts/check-tree-integrity.sql` 或 `scripts/check_tree_integrity.py`
  - 提供树结构异常扫描，不默认执行清理。
- ✨新增/🔨修改相关测试文件
  - 覆盖成本科目环检测、付款租户过滤、参数校验、事务回滚、库存并发等风险点。
- ✨新增 `docs/quality/tree-integrity-check-2026-07-03.md`
  - 固化树结构扫描结果和剩余风险。
- ✨新增 `docs/quality/deploy-secret-remediation-2026-07-03.md`
  - 记录密钥治理、镜像固定和 SSL 配置的执行证据。

## 三、数据流变化

- 成本科目树：前端仪表盘或成本页面请求 `/api/**/cost-subject/**/tree` → 后端按租户读取 `cost_subject` → 按 parent_id 分组 → 带 visited 路径构建树 → 发现循环则记录告警并跳过异常分支 → 返回可渲染树，避免白屏。
- 成本科目写入：用户新增/编辑成本科目 → 后端校验父节点存在、同租户、非自身挂载 → 写入数据库 → 后续树接口不再接收明显非法 parent_id。
- 付款校验：用户提交付款申请 → 后端读取合同与已审批付款申请 → 汇总时增加 tenantId 条件 → 执行防超付校验 → 审批流继续流转。
- 参数校验：前端提交批量明细或用户状态更新 → Controller DTO/`@Valid` 先拦截非法值 → Service 只处理通过契约校验的数据。
- 部署治理：CI 执行构建/测试/扫描 → 部署使用固定镜像和环境变量密钥 → 生产启动时按 profile 应用 SSL、日志和限速配置。

## 四、影响范围与回归测试建议

- 仪表盘与成本模块
  - 回归建议：构造正常树、空树、循环树、孤儿节点数据，验证接口不 500、页面不白屏。
- 付款与合同模块
  - 回归建议：构造同租户/跨租户付款申请，验证防超付金额只按当前租户统计。
- 事务密集业务
  - 回归建议：合同、付款、发票、库存、审批各抽一条写入后失败路径，验证无半提交。
- 数据库迁移
  - 回归建议：MySQL/H2 从空库执行 Flyway，验证迁移成功且核心查询不退化。
- 登录与权限
  - 回归建议：验证弱密码前端拦截、合法密码仍走后端认证、无权限路由被拦截。
- 系统管理
  - 回归建议：用户状态只能在 `ENABLE`/`DISABLE` 间切换，非法值不落库。
- 库存与验收
  - 回归建议：并发验收同一采购明细、库存不足出库、正常入库出库三类场景必须覆盖。
- 部署与运维
  - 回归建议：使用 `.env.example` 初始化环境，验证无真实密钥入库，compose 不依赖 `latest`。

## 五、冒烟测试方案

1. 登录后台并进入 `/dashboard`，预期首页正常渲染，成本相关接口无 500，浏览器无白屏。
2. 调用成本科目树接口加载正常树，预期返回完整层级且字段格式与前端兼容。
3. 使用测试数据制造成本科目循环引用后调用树接口，预期接口不栈溢出，日志记录循环风险。
4. 新增/编辑成本科目时将父节点设为自身，预期请求失败且数据库不写入非法关系。
5. 提交付款申请，预期防超付校验只统计当前租户已审批金额。
6. 调用目标成本批量保存接口传入非法明细字段，预期被参数校验拦截。
7. 调用用户状态更新接口传入非法状态，预期返回校验错误；传入 `ENABLE`/`DISABLE` 成功。
8. 执行一次库存出库超过可用量的请求，预期失败且库存余额不变。
9. 执行后端相关测试和 Flyway 启动验证，预期无迁移失败、无核心测试失败。
10. 执行前端 `pnpm build`，预期类型检查和生产构建通过。

## 六、计划书写入

- 路径：`D:\projects-test\cgc-pms\docs\superpowers\plans`
- 文件：`D:\projects-test\cgc-pms\docs\superpowers\plans\2026-07-03-issue-reports-remediation-plan.md`

## 七、执行顺序建议

1. 第 0 批（当天必须完成）：A1、A2、B1，并补对应后端测试。
2. 第 1 批（1-2 天）：B2、D1、C1、B4，先修数据隔离、校验和部署凭据风险。
3. 第 2 批（1 周内）：B3、D2、E1，补索引、并发保护和 CI 门禁。
4. 第 3 批（1 月内）：C2、C3、D3、E2、E3，处理权限体验、可观测性和工程化增强。

## 八、退出标准

- P0/P1 项全部有代码修复、自动化测试或明确证据。
- 后端至少通过相关单测/集成测试与一次构建或 `mvn test` 等价验证。
- 前端涉及登录/路由改动时至少通过 `pnpm build`，必要时补 Vitest/Playwright 验证。
- Flyway 新增迁移在 MySQL/H2 目标环境均可执行。
- 文档记录每个未完成项的责任、风险和下一步，不再用旧报告替代当前验证结论。
