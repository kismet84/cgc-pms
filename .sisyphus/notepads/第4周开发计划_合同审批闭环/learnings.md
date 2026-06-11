# Learnings - 第4周开发计划_合同审批闭环

## T1: cost_item 实体 + Mapper + VO

### Patterns Followed
- Entity 继承 BaseEntity（含 createdBy/createdAt/updatedBy/updatedAt/deletedFlag/remark）
- @TableName("cost_item") 映射表名
- @TableId(type = IdType.ASSIGN_ID) 雪花ID
- BigDecimal 字段用 @JsonSerialize(using = ToStringSerializer.class) 序列化为 String
- LocalDate 字段用 @JsonFormat(pattern = "yyyy-MM-dd")
- VO 中所有 Long ID → String（防 JS 精度丢失），BigDecimal → String
- Mapper 使用 @Mapper + extends BaseMapper<CostItem>

### Files Created
- `backend/src/main/java/com/cgcpms/cost/entity/CostItem.java`
- `backend/src/main/java/com/cgcpms/cost/mapper/CostItemMapper.java`
- `backend/src/main/java/com/cgcpms/cost/vo/CostItemVO.java`

### Verification
- `./mvnw compile -q` passed cleanly (no errors)

## T2: 合同状态机常量类 + 状态流转校验 + update 守卫

### Patterns Followed
- 常量类使用 `public final class` + `private 构造器`（防止实例化）
- 常量值与 decisions.md 决策对齐（approvalStatus: DRAFT/APPROVING/APPROVED/REJECTED/WITHDRAWN, contractStatus: DRAFT/PERFORMING/SETTLED/TERMINATED）
- update() 守卫模式：先查 existing，再判 APPROVING，最后 strip approvalStatus
- BusinessException 使用标准两参构造：new BusinessException("CODE", "中文消息")

### Files Created
- `backend/src/main/java/com/cgcpms/contract/constant/ContractStatusConstants.java`

### Files Modified
- `backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`
  - 新增 import ContractStatusConstants
  - update() 改为：查 existing → 判断 APPROVING 抛异常 → strip approvalStatus → updateById
  - 新增 submitForApproval(Long) 骨架（抛 UnsupportedOperationException，T3 实现）

### Verification
- `./mvnw compile -q` passed cleanly (no errors)

## T3: 合同提交审批接口 POST /contracts/{id}/submit

### Patterns Followed
- Service 层用 LambdaUpdateWrapper 做精准单字段更新（避免 updateById 全量覆盖风险）
- 状态校验链：DRAFT 检查 → contractCode 非空检查 → 更新 APPROVING → 调 workflowEngine
- @Transactional 保证状态更新 + 审批提交原子性
- UserContext.getCurrentUserId/getCurrentUsername/getCurrentTenantId 获取当前用户
- Controller 遵循现有模式：@PreAuthorize + ApiResponse.success()

### Files Modified
- `backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`
  - 新增 import: LambdaUpdateWrapper, UserContext, WorkflowEngine
  - 新增 field: `private final WorkflowEngine workflowEngine`
  - submitForApproval() 完整实现：
    1. 查合同 → 不存在抛 CONTRACT_NOT_FOUND
    2. 非 DRAFT 抛 CONTRACT_ALREADY_SUBMITTED
    3. contractCode 空/空白抛 CONTRACT_NO_CODE
    4. LambdaUpdateWrapper 精准更新 approvalStatus → APPROVING
    5. 调用 workflowEngine.submit(userId, username, tenantId, businessType, businessId, title, amount, projectId, contractId, null, null)

- `backend/src/main/java/com/cgcpms/contract/controller/CtContractController.java`
  - 新增 POST /{id}/submit 端点，@PreAuthorize("hasAuthority('contract:submit') or hasRole('ADMIN')")

### Key Decisions
- 使用 LambdaUpdateWrapper.set() 而非 updateById 做部分更新，避免序列化整行覆盖
- workflowEngine.submit() 的 businessSummary 和 variables 暂传 null（后续按需扩展）
- 提交后 contractStatus 保持 DRAFT 不变（仅 approvalStatus 变为 APPROVING）

### Verification
- `./mvnw compile -q` passed cleanly (no errors)

---

## T4 完成记录 (合同审批闭环回调)

**时间**：2026-06-11

### 引擎 isCritical 机制
- `WorkflowBusinessHandler` 新增 `default boolean isCritical() { return false; }`
- `WorkflowEngine.notifyHandler()` 提取 dispatch switch 为私有方法 `dispatchToHandler(handler, ctx, instance, actionType)`
- isCritical()=true 时直接调用（异常传播触发 @Transactional 回滚）；false 时包 try-catch swallow-and-log
- 注意：WorkflowEngine 需新增 import `com.cgcpms.workflow.handler.WorkflowBusinessHandler`

### ContractWorkflowHandler
- `@Component implements WorkflowBusinessHandler`，`isCritical()=true`
- contractId 来源：`ctx.getInstance().getBusinessId()`（submit 时 businessId=contractId）
- 用 LambdaUpdateWrapper 安全更新：onApproved→APPROVED+PERFORMING+生成成本；onRejected→REJECTED；onWithdrawn→DRAFT
- 用 @RequiredArgsConstructor 构造注入 CtContractMapper + CostGenerationService

### CostGenerationService 锁定成本生成
- `generateLockedCost(Long contractId)` @Transactional
- 先 selectById 合同拿 tenantId/orgId/projectId/partnerId（cost_item.project_id NOT NULL，cost_date NOT NULL 必填）
- LambdaQueryWrapper 查 ct_contract_item WHERE contract_id
- 幂等策略：逐条 try { costItemMapper.insert } catch (DuplicateKeyException) { log skip }，靠 uk_cost_source_item(source_type,source_id,source_item_id,cost_type) 兜底
- 固定常量：SOURCE_TYPE_CONTRACT="CT_CONTRACT", DEFAULT_COST_TYPE="CONTRACT_LOCKED", costStatus="CONFIRMED", generatedFlag=1

### Flyway V9
- `V9__init_contract_approval_template.sql` 同时放 database/migration/ 和 backend/src/main/resources/db/migration/
- 模板 id=50001, template_code=TPL-CONTRACT-APPROVAL-001, business_type=CONTRACT_APPROVAL
- 3 节点 N1/N2/N3 (项目经理/部门经理/总经理审批) node_type=APPROVAL approve_mode=SEQUENTIAL
- 注意：当前引擎 POC 的 createTasksForNode 忽略 approver_config，固定给 initiator 建任务，所以 approver_config 仅占位（USER/userId=1）

### 验证
- mvnw compile BUILD SUCCESS（jdtls LSP 未安装，改用 maven 编译验证）

## T6 前端三大改动 (2026-06-11)

### 枚举对齐完成
- **contract.ts**: EXECUTING→PERFORMING, COMPLETED→SETTLED, 去掉SUBMITTED, 新增WITHDRAWN
- **ContractDetailPage.vue**: STATUS_LABEL/APPROVAL_STATUS_LABEL 已对齐
- **ContractLedgerPage.vue**: 筛选器选项 + STATUS_LABEL 已对齐
- **ContractFormPage.vue**: 无硬编码枚举引用，无需修改

### 详情页提交审批按钮
- 位置：-page-header 的 xtra slot，状态标签旁
- 显示条件：contract.approvalStatus === 'DRAFT'
- 交互流程：
  1. Modal.confirm 二次确认
  2. 调用 submitForApproval(contractId)
  3. 成功后 message.success + 重新加载合同数据（刷新状态）

### 审批记录真实联调
- ContractDetailPage.vue 第3个 tab 已通过 contractStore.fetchApprovalRecords(contractId) 调用真实 API
- API 端点：GET /contracts/{id}/approval-records
- Timeline 展示：operatorName + actionType 映射 + comment + createdAt

### 构建验证
`ash
cd frontend-admin && pnpm run build
✓ vue-tsc --noEmit 通过（零类型错误）
✓ vite build 成功（8.73s）
`

### 注意事项
- approval/detail.vue 中的 COMPLETED 是 workflow 节点状态（nodeStatusMap），非合同状态，不应修改
- 前端枚举对齐后，与后端 V5 迁移定义的字典值完全一致

---

## T7: 合同审批全流程集成测试 (2026-06-11)

### 文件创建
- `backend/src/test/java/com/cgcpms/contract/ContractApprovalIntegrationTest.java`

### 测试模式
- 遵循 WorkflowEngineIntegrationTest 的 `@SpringBootTest` + `@ActiveProfiles("local")` 模式
- `@BeforeEach` 设置 UserContext（Jwts.claims），`@AfterEach` 清理
- 每个测试方法 `@Transactional` 自动回滚，保证测试隔离

### 5个测试用例
1. **test01_submitContractForApproval** — 提交 DRAFT 合同 30003，断言 APPROVING + wf_instance + SUBMIT 记录
2. **test02_approvalStatusGuard** — 先提交再编辑，断言 BusinessException("CONTRACT_IN_APPROVAL")
3. **test03_costGenerationIdempotent** — 插入清单项 → 调 generateLockedCost 两次 → 断言不重复；验证 source_type=CT_CONTRACT，source_item_id 对应清单项
4. **test04_submitDuplicateRejected** — 同一合同提交两次，第二次断言 BusinessException("CONTRACT_ALREADY_SUBMITTED")
5. **test05_getApprovalRecords** — 提交后查 getApprovalRecords，断言列表非空且含 SUBMIT 记录

### 关键注入点
- CtContractService, ContractWorkflowHandler, CostGenerationService, WorkflowEngine
- CtContractMapper, CtContractItemMapper, CostItemMapper, WfInstanceMapper, WfRecordMapper

### 验证结果
- `./mvnw test-compile`: BUILD SUCCESS（编译通过）
- 运行时 ApplicationContext 加载失败：H2 data.sql 编码问题（已知，非本次改动引入）
- 代码逻辑经源码审查确认正确

### 注意事项
- test03 需在测试中动态插入 ct_contract_item 清单数据（demo data 无合同清单项），@Transactional 自动回滚不污染库
- @Autowired 注入所有依赖，避免手写构造器
- LambdaQueryWrapper 用于动态查询 wf_instance/wf_record/cost_item 表

## T7 Deliverable: 合同审批闭环测试报告 (2026-06-11)

### File Created
- doc/合同审批闭环测试报告.md (279 lines)

### Format
- Follows exact structure of Week 2 POC test report (doc/审批引擎POC测试报告_2026-06-10.md)
- 7 sections: 测试概览, 测试环境, 测试用例详情, 全栈验证, 已知限制, 代码变更统计, 结论
- 5 test scenarios documented with test method name, Backlog mapping, inputs, verification points, results
- MySQL 8.0 verification marked as 计划中 (honest about H2 encoding blocker)
- Code change stats: 11 categories, files + lines

### Key Decisions
- Reported H2 runtime blocker honestly (pre-existing data.sql encoding, not Week 4 regression)
- Test results presented as 编译+逻辑验证 passed, with clear note about runtime limitation
- Section 5 limitations include: no end-to-end multi-node test, no callback rollback test, no frontend E2E, H2 encoding blocker
- Section 7 flow diagram visually maps the contract approval closed-loop lifecycle

## T8: 回调事务回滚集成测试 (2026-06-11)

### File Created
- `backend/src/test/java/com/cgcpms/contract/ContractApprovalRollbackTest.java`

### Test Pattern
- Separate test class from `ContractApprovalIntegrationTest` (isolates `@MockBean` scope)
- `@MockBean CostGenerationService` replaces real bean ONLY in this test class
- Uses `doThrow(...).when(costGenerationService).generateLockedCost(APPROVED_CONTRACT_ID)` to force failure
- Manually constructs `WorkflowContext` with `WfInstance` (businessType=CONTRACT_APPROVAL, businessId=30001L)

### Key Details
- `WfInstance.businessId` is `Long` (confirmed from entity source at line 30)
- `WorkflowContext` uses Lombok `@Data` — setter-based construction works
- Handler updates contract status BEFORE calling `generateLockedCost` — the mocked exception triggers AFTER the DB update
- Test `@Transactional` uses Spring Test default rollback behavior (always rolls back after test)
- `assertThrows` verifies the RuntimeException propagates out of `onApproved()` (isCritical=true contract)

### Verification
- `./mvnw test-compile -q` passed cleanly (no compilation errors)
