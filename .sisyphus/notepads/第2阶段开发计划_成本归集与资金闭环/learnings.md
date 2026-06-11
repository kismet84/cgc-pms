## A1: 材料字典模块完成

### 后端文件（5个）
- MdMaterial.java (entity)
- MdMaterialMapper.java
- MdMaterialService.java
- MdMaterialController.java
- MdMaterialVO.java

### 前端文件（3个）
- material.ts (API)
- material.ts (types)
- dictionary.vue (CRUD页面)

### 路由
- 新增 '基础数据/材料字典' 菜单 (icon: DatabaseOutlined)
- 路径: /material/dictionary

### API端点
- GET /materials — 分页列表（支持materialCode/materialName/categoryId/status筛选）
- GET /materials/{id} — 详情
- POST /materials — 新增
- PUT /materials/{id} — 编辑
- PUT /materials/{id}/status — 启用/禁用

### 验证
✅ 后端编译通过 (./mvnw compile -q)
✅ 前端构建通过 (pnpm run build)

### 符合规范
✅ Entity: extends BaseEntity, @TableName('md_material'), @TableId(ASSIGN_ID), BigDecimal用ToStringSerializer
✅ VO: Long→String, BigDecimal→String
✅ Mapper: @Mapper extends BaseMapper
✅ Service: 租户隔离 (UserContext.getCurrentTenantId()), 返回PageResult<VO>
✅ Controller: @PreAuthorize权限控制 (material:dict:list/query/add/edit)
✅ Frontend: 遵循partner.ts模式（named exports, request wrapper）
✅ Types: MaterialVO接口匹配后端VO
✅ 页面: Filter + Table + Pagination + Modal (新增/编辑/启用/禁用)

## A2: CostSubject 成本科目树形结构实现总结

### 实现完成
- Entity: `CostSubject.java` — 继承 BaseEntity，使用 `@TableField(insertStrategy/updateStrategy = FieldStrategy.IGNORED)` 处理 DDL 中缺失的 createdBy/updatedBy/remark 字段
- Mapper: `CostSubjectMapper.java` — 标准 MyBatis-Plus BaseMapper
- VO: `CostSubjectVO.java` (扁平) + `CostSubjectTreeNodeVO.java` (树形，含 children 字段)
- Service: `CostSubjectService.java`
  - `getTree()` — 递归构建父子树结构，使用 Stream groupingBy 优化查询
  - `getList()` — 扁平列表用于下拉选择
  - `create()` — 校验 parent 存在性，自动设置 level = parent.level + 1，唯一键校验 subject_code
  - `update()` — 唯一键校验（排除自身）
  - `toggleStatus()` — ENABLE ↔ DISABLE 切换
  - `delete()` — 校验无子节点才允许删除
  - 全链路 tenantId 过滤和二次校验
- Controller: `CostSubjectController.java` — 7 个端点，全部 @PreAuthorize("cost:*")

### 关键设计
1. **DDL 字段不匹配处理**：V4 DDL 的 cost_subject 表缺少 created_by/updated_by/remark，但 Entity 继承了 BaseEntity。解决方案：使用 `@TableField(insertStrategy = FieldStrategy.IGNORED, updateStrategy = FieldStrategy.IGNORED)` 覆盖这些字段，让 MyBatis-Plus 在 INSERT/UPDATE 时忽略 NULL 值，避免字段不存在的 SQL 错误。
2. **树形查询优化**：一次查询所有节点，使用 `groupingBy(parentId)` 分组后递归构建，避免 N+1 查询。
3. **status vs enabledFlag**：任务规格提到 enabled_flag，但实际 DDL 使用 VARCHAR(50) status 字段（ENABLE/DISABLE）。最终按实际表结构实现，VO 也暴露 status 字段保持一致性。
4. **验证规则**：
   - 创建时父节点必须存在（或 parentId=0 表示根节点）
   - subject_code 在租户内唯一（uk_cost_subject_code）
   - 删除时不能有子节点

### API 端点清单
- GET `/api/cost-subjects/tree` — 树形结构
- GET `/api/cost-subjects` — 扁平列表
- GET `/api/cost-subjects/{id}` — 详情
- POST `/api/cost-subjects` — 新建（自动计算 level）
- PUT `/api/cost-subjects/{id}` — 编辑
- PUT `/api/cost-subjects/{id}/toggle` — 启用/禁用切换
- DELETE `/api/cost-subjects/{id}` — 删除（无子节点校验）

### 编译验证
- `./mvnw compile -q` 通过，无错误

## C1: CostGenerationService 策略模式重构 (2026-06-11)

### 实现概要
将 `CostGenerationService` 从单一职责（仅 CT_CONTRACT）重构为策略模式，支持多种 source_type：
- CT_CONTRACT → ContractCostStrategy
- MAT_RECEIPT → MaterialReceiptCostStrategy
- SUB_MEASURE → SubMeasureCostStrategy
- VAR_ORDER → VarOrderCostStrategy

### 关键决策

1. **策略注册 via Spring 自动注入**
   - 所有 `CostGenerationStrategy` 实现通过 `List<CostGenerationStrategy>` 自动发现
   - @PostConstruct 构建 `Map<String, CostGenerationStrategy>` 实现 O(1) 查找
   - 启动时记录已注册策略，便于观测

2. **保持向后兼容**
   - 保留 `generateLockedCost(contractId)` 便捷方法
   - 委托给 `generateCost("CT_CONTRACT", contractId)`
   - ContractWorkflowHandler 无破坏性改动

3. **最小实体模式**
   - 创建轻量实体（MatReceipt, SubMeasure, VarOrder），仅含成本生成所需字段
   - 放置于对应包：`material`, `subcontract`, `variation`
   - 完整实体将在后续任务（A5, B2, E1）替换
   - 使用 `@TableName` 映射到 V12 表

4. **幂等一致性**
   - 所有策略使用相同模式：try-insert，catch DuplicateKeyException
   - UK: `uk_cost_source_item (source_type, source_id, source_item_id, cost_type)`

5. **cost_generated_flag 管理**
   - MAT_RECEIPT/SUB_MEASURE/VAR_ORDER 策略在生成后更新源实体 `cost_generated_flag = 1`
   - CT_CONTRACT 不更新（合同表无此字段，按 V4 schema）

6. **VAR_ORDER 方向过滤**
   - 仅当 `direction = 'COST'` 时生成成本（W0 决策 4）
   - 其他方向跳过并记录日志

### source_type → cost_type 映射
| source_type   | cost_type        |
|---------------|------------------|
| CT_CONTRACT   | CONTRACT_LOCKED  |
| MAT_RECEIPT   | MATERIAL         |
| SUB_MEASURE   | SUBCONTRACT      |
| VAR_ORDER     | VARIATION        |

### 编译验证
- `./mvnw compile -q` 通过 ✅

### 可复用模式（未来新增 source_type）
1. 创建最小实体/mapper（如需要）
2. 实现 `CostGenerationStrategy` 并加 `@Component`
3. 使用标准幂等模式（try-insert catch DuplicateKeyException）
4. 成功后更新源实体 `cost_generated_flag`
5. Spring 自动注册，无需改动 `CostGenerationService`

## B1: SubTask (分包任务) 全栈 CRUD — 2026-06-11

### 实现要点

**Backend**:
- Entity: SubTask extends BaseEntity, @TableName("sub_task"), @TableId(IdType.ASSIGN_ID)
- BigDecimal: @JsonSerialize(ToStringSerializer), LocalDate: @JsonFormat("yyyy-MM-dd")
- VO: 所有 Long→String, BigDecimal→String, LocalDate→String (使用 DATE_FMT/DTF formatter)
- Mapper: @Mapper extends BaseMapper<SubTask>
- Service: 
  - getPage(): LambdaQueryWrapper + tenant filter, batch-prefetch project/contract/partner names (N+1 优化)
  - create(): 自动生成 taskCode SUB-yyyyMMdd-XXX, 默认 status=NOT_STARTED
  - 双 toVO() 重载: 单条查询用 selectById, 批量查询用 Map<Long, String> 预取
- Controller: @RequestMapping("/sub-tasks"), @PreAuthorize 使用 subtask:query/add/edit/delete
- Status 枚举: NOT_STARTED/IN_PROGRESS/COMPLETED/SUSPENDED

**Frontend**:
- Types: SubTaskVO + SubTaskStatus 类型导出
- API: CRUD 5 endpoints (list/detail/create/update/delete)
- Page: Ant Design Vue -table + modal 表单
  - Filter: project/contract/partner 下拉 (从对应 API 预加载 500 条), status/taskCode/taskName
  - Table: taskCode, taskName (链接), projectName, contractName, partnerName, workArea, progressPercent (进度条), status (tag), 计划日期, 操作 (编辑/删除)
  - Modal: 完整表单, date-picker 直接 v-model string (Vue 自动转换), input-number v-model string (自动转数字)
- Route: /subcontract/task 挂在新增的 分包管理 菜单节点下

**验证**:
- ./mvnw compile -q: ✅ 通过
- pnpm run build: ✅ 通过 (9.65s)

### 关键模式

1. **N+1 优化模式** (CtContractService 继承):
   `java
   Set<Long> projectIds = records.stream().map(SubTask::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
   Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
       : pmProjectMapper.selectBatchIds(projectIds).stream()
           .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
   return page.convert(t -> toVO(t, projectNames, contractNames, partnerNames));
   `

2. **编号自动生成模式** (ContractService 继承):
   `java
   String prefix = "SUB-" + LocalDate.now().format(ofPattern("yyyyMMdd")) + "-";
   wrapper.likeRight(SubTask::getTaskCode, prefix).orderByDesc(SubTask::getTaskCode).last("LIMIT 1");
   SubTask last = subTaskMapper.selectOne(wrapper);
   int seq = 1;
   if (last != null && last.getTaskCode().length() == prefix.length() + 3) {
       seq = Integer.parseInt(last.getTaskCode().substring(prefix.length())) + 1;
   }
   task.setTaskCode(prefix + String.format("%03d", seq));
   `

3. **租户隔离守卫** (所有 Service CRUD):
   - create(): 自动注入 tenantId (MyBatis-Plus FieldFill)
   - getPage(): wrapper.eq(SubTask::getTenantId, UserContext.getCurrentTenantId())
   - getById()/update()/delete(): 先查 + 校验 !existing.getTenantId().equals(currentTenantId) → throw BusinessException

4. **VO 转换双重载** (性能优化):
   - 	oVO(entity): 单条详情, 3 个 selectById (getById 场景)
   - 	oVO(entity, maps): 批量列表, 0 个查询 (getPage 场景)

### 前端特性

- **下拉预加载**: 组件挂载时预加载 project/contract/partner 列表 (pageSize=500), 筛选和表单复用
- **进度条渲染**: <a-progress :percent="parseFloat(record.progressPercent)" />
- **状态标签**: STATUS_COLOR/STATUS_LABEL 映射, -tag :color="STATUS_COLOR[status]"
- **Modal 复用**: 同一 modal + reactive formData, 新建/编辑切换 modalTitle 和 editingId
- **删除确认**: Modal.confirm() 二次确认

### 权限码约定

- subtask:query (列表+详情)
- subtask:add (新建)
- subtask:edit (编辑)
- subtask:delete (删除)

### 文件清单

**Backend** (5 files):
- ackend/src/main/java/com/cgcpms/subcontract/entity/SubTask.java
- ackend/src/main/java/com/cgcpms/subcontract/mapper/SubTaskMapper.java
- ackend/src/main/java/com/cgcpms/subcontract/vo/SubTaskVO.java
- ackend/src/main/java/com/cgcpms/subcontract/service/SubTaskService.java (198 lines)
- ackend/src/main/java/com/cgcpms/subcontract/controller/SubTaskController.java

**Frontend** (3 files):
- rontend-admin/src/types/subcontract.ts
- rontend-admin/src/api/modules/subcontract.ts
- rontend-admin/src/pages/subcontract/task.vue (414 lines)
- rontend-admin/src/router/index.ts (新增 subcontract 节点)

## A4: PurchaseOrderWorkflowHandler + submitForApproval — 2026-06-11

### 实现概要
为采购订单模块添加完整的审批流程：WorkflowBusinessHandler + submitForApproval + controller endpoint + Flyway V13 迁移。

### 文件清单

**新建 (3 files)**:
- `backend/src/main/java/com/cgcpms/purchase/handler/PurchaseOrderWorkflowHandler.java` — 审批回调处理器
- `database/migration/V13__init_purchase_approval_template.sql` — Flyway 迁移（database/）
- `backend/src/main/resources/db/migration/V13__init_purchase_approval_template.sql` — Flyway 迁移（backend/）

**修改 (2 files)**:
- `backend/src/main/java/com/cgcpms/purchase/service/MatPurchaseOrderService.java` — 新增 submitForApproval() + 注入 WorkflowEngine
- `backend/src/main/java/com/cgcpms/purchase/controller/MatPurchaseOrderController.java` — 新增 POST /{id}/submit endpoint

### PurchaseOrderWorkflowHandler 设计
- `@Component`, `@Slf4j`, `@RequiredArgsConstructor`, `implements WorkflowBusinessHandler`
- `supportBusinessType()` → `"PURCHASE_ORDER"`（与 V13 模板 business_type 一致）
- `isCritical()` → `true`（审批回调失败触发事务回滚）
- 注入 `MatPurchaseOrderMapper orderMapper`
- `onApproved()`: `approval_status = APPROVED`, `order_status = APPROVED`
- `onRejected()`: `approval_status = REJECTED`
- `onWithdrawn()`: `approval_status = DRAFT`
- **不生成成本**（与 ContractWorkflowHandler 的关键区别：采购订单审批不触发成本生成）

### submitForApproval 方法
- `@Transactional`
- 校验顺序：order 存在 + tenant 匹配 → approvalStatus == DRAFT → orderCode 非空
- 更新 `approval_status = APPROVING`
- 调用 `workflowEngine.submit(userId, username, tenantId, "PURCHASE_ORDER", orderId, order.getOrderCode(), order.getTotalAmount(), order.getProjectId(), order.getContractId(), null, null)`
- 完全遵循 CtContractService.submitForApproval() 模式

### Controller endpoint
- `POST /purchase-orders/{id}/submit`
- `@PreAuthorize("hasAuthority('purchase:order:submit') or hasRole('ADMIN')")`
- 委托给 `matPurchaseOrderService.submitForApproval(id)`

### V13 迁移
- 模板 ID: 50002, 业务类型: PURCHASE_ORDER
- 3 节点（SEQENTIAL）：项目经理审批(N1) → 部门经理审批(N2) → 总经理审批(N3)
- 节点 ID: 50201, 50202, 50203
- `INSERT IGNORE` 保证幂等
- 两份迁移文件内容相同（database/migration/ 和 backend/src/main/resources/db/migration/）

### 编译验证
- `./mvnw compile -q` 通过 ✅

## D1: PayApplication + PayApplicationBasis 全栈 — 2026-06-11

### 实现概要
创建付款申请模块的完整后端 CRUD，包含付款申请头表 + 付款依据明细表。

### 文件清单 (8 files)

**Entities**:
- `backend/src/main/java/com/cgcpms/payment/entity/PayApplication.java` — extends BaseEntity, @TableName("pay_application")
- `backend/src/main/java/com/cgcpms/payment/entity/PayApplicationBasis.java` — extends BaseEntity, @TableName("pay_application_basis")

**Mappers**:
- `backend/src/main/java/com/cgcpms/payment/mapper/PayApplicationMapper.java`
- `backend/src/main/java/com/cgcpms/payment/mapper/PayApplicationBasisMapper.java`

**VOs**:
- `backend/src/main/java/com/cgcpms/payment/vo/PayApplicationVO.java` — all String types + projectName/contractName/partnerName + basis list
- `backend/src/main/java/com/cgcpms/payment/vo/PayApplicationBasisVO.java` — all String types

**Service**:
- `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java`

**Controller**:
- `backend/src/main/java/com/cgcpms/payment/controller/PayApplicationController.java`

### API 端点
- GET `/pay-applications` — 分页列表（project/contract/partner/payStatus/approvalStatus/applyCode 筛选）
- GET `/pay-applications/{id}` — 详情（含 basis 列表）
- POST `/pay-applications` — 新建（自动生成编号 PAY-yyyyMMdd-XXX）
- PUT `/pay-applications/{id}` — 编辑
- DELETE `/pay-applications/{id}` — 删除（级联删除 basis）
- GET `/pay-applications/{id}/basis` — 依据列表
- POST `/pay-applications/{id}/basis/batch` — 批量保存依据（header.applyAmount == SUM(basis.basisAmount) 校验）

### 关键设计
1. **编号自动生成模式** (继承 SubMeasureService): PAY-yyyyMMdd-XXX, likeRight 查询当日最大编号 + 自增
2. **N+1 批量名称解析** (继承 SubMeasureService): 列表查询预取 project/contract/partner names via selectBatchIds
3. **双 toVO() 重载**: 单条用 selectById, 批量用预取 Map
4. **Basis 批量保存校验**: header.applyAmount == SUM(basis.basisAmount), 不一致抛 AMOUNT_MISMATCH
5. **级联删除**: delete 时先删 basis 再删 header
6. **租户隔离**: all queries filtered by tenantId, single-entity ops verify tenant match
7. **BigDecimal 序列化**: @JsonSerialize(ToStringSerializer) on entity fields, toPlainString() in VO
8. **默认状态**: payStatus=PENDING, approvalStatus=DRAFT

### 权限码
- payment:app:query (列表+详情)
- payment:app:add (新建)
- payment:app:edit (编辑+批量保存依据)
- payment:app:delete (删除)

### 编译验证
- `./mvnw compile -q` 通过 ✅

---

## Phase 2 完成总结 (2026-06-11)

### 交付统计

| 指标 | 数值 |
|------|------|
| 完成日期 | 2026-06-11 |
| 新建 Java 源文件 | ~100+ |
| 新建前端文件 | ~20+ |
| Flyway 迁移 | V12~V17 + V19 (7 个脚本) |
| 集成测试 | 7 (Phase2FullChainIntegrationTest, 696 行) |
| WorkflowHandler | 6 (PurchaseOrder / MaterialReceipt / SubMeasure / PayRequest / PayRecord / VarOrder) |
| CostGeneration 策略 | 4 (CT_CONTRACT / MAT_RECEIPT / SUB_MEASURE / VAR_ORDER) |

### 模块清单

| 模块 | Backend | Frontend | Handler | Cost Gen | 文件数 |
|------|---------|----------|---------|----------|--------|
| md_material | ✅ | ✅ | — | — | 8 |
| mat_purchase_order | ✅ | ✅ | PurchaseOrder | — | ~12 |
| mat_receipt | ✅ | ✅ | MaterialReceipt | MAT_RECEIPT | ~12 |
| sub_task | ✅ | ✅ | — | — | ~8 |
| sub_measure | ✅ | ✅ | SubMeasure | SUB_MEASURE | ~10 |
| cost_subject | ✅ | — | — | — | 5 |
| cost_ledger | ✅ | ✅ | — | — | ~6 |
| cost_summary | ✅ | ✅ | — | — | ~5 |
| pay_application | ✅ | ✅ | PayRequest | — | ~10 |
| pay_record | ✅ | ✅(writeback) | PayRecord | — | ~6 |
| var_order | ✅ | ✅ | VarOrder | VAR_ORDER | ~10 |
| CostGenerationService | ✅ | — | — | — | 6 (service + 4 strategies) |

### 关键架构决策

1. **CostGenerationService 策略模式**: 4 种 source_type 通过 Spring `List<CostGenerationStrategy>` 自动发现和注册, `@PostConstruct` 构建 O(1) Map。新增 source_type 仅需实现接口 + 加 `@Component`。

2. **成本幂等机制**: 所有策略使用统一的 uk_cost_source_item (source_type, source_id, source_item_id, cost_type) 唯一键 + try-insert catch DuplicateKeyException 模式。

3. **付款回写状态机**: PayApplication: PENDING → PARTIALLY_PAID → PAID。每次回写自动累加 contract.paid_amount, 超付时抛出 PAY_OVERPAYMENT。

4. **Basis 金额校验**: 付款依据金额合计必须等于申请金额 (AMOUNT_MISMATCH), 且单条依据金额不得超过对应的源单据明细金额 (BASIS_EXCEED_SOURCE)。

5. **isCritical = true**: 所有 Phase 2 WorkflowHandler 均设置 isCritical=true, 审批回调异常传播触发 @Transactional 回滚。

6. **cost_generated_flag 管理**: MAT_RECEIPT / SUB_MEASURE / VAR_ORDER 策略在成本生成后更新源实体的 cost_generated_flag=1, CT_CONTRACT 不更新 (合同表无此字段)。

7. **VAR_ORDER direction 过滤**: 仅 direction='COST' 时生成成本, 其他方向跳过并记录日志。

### Known Issues (Final Wave Review)

| # | 问题 | 详情 |
|---|------|------|
| F1 | 前端未完全集成 | 部分页面路由已注册但页面功能需手动验证 |
| F2 | payment writeback 不在 @Transactional 内 | PayRecordService.writeback() 逐条操作, 缺少整体事务包裹 |
| F3 | 事务边界设计需复核 | 审批回调中成本生成与状态更新的事务边界需结合实际情况调整 |

### source_type → cost_type 映射

| source_type | cost_type | 说明 |
|-------------|-----------|------|
| CT_CONTRACT | CONTRACT_LOCKED | 合同锁定成本 |
| MAT_RECEIPT | MATERIAL | 材料成本 |
| SUB_MEASURE | SUBCONTRACT | 分包成本 |
| VAR_ORDER | VARIATION | 签证变更成本 |

---

## F1 Architecture Review Cleanup (2026-06-11)

### #10 — Unify MatReceipt entities
- Deleted `com.cgcpms.material.entity.MatReceipt.java` and `MatReceiptItem.java` — these were minimal stubs marked "for cost generation, full impl in task A5"
- No code imported from `com.cgcpms.material.entity` — all production code already uses `com.cgcpms.receipt.entity`
- `MaterialReceiptCostStrategy` already had correct imports (receipt.entity + receipt.mapper), no changes needed

### #11 — Remove redundant cost_generated_flag from handlers
- Removed LambdaUpdateWrapper `.set(Xxx::getCostGeneratedFlag, 1)` from 3 handlers:
  - `MaterialReceiptWorkflowHandler.onApproved()` — lines 48-50 removed
  - `SubMeasureWorkflowHandler.onApproved()` — lines 49-51 removed
  - `VarOrderWorkflowHandler.onApproved()` — lines 54-56 removed
- Rationale: The cost strategy already sets this flag in `generateCost()`. The handler's second update was a no-op redundant write.

### #12 — Add costGeneratedFlag to CtContract
- Created `V20__add_contract_cost_generated_flag.sql` in both `database/migration/` and `backend/src/main/resources/db/migration/`
- Added `private Integer costGeneratedFlag;` to `CtContract` entity (after `approvalStatus`)
- Updated `ContractCostStrategy.generateCost()`: after generating all cost items, sets `contract.setCostGeneratedFlag(1)` and calls `contractMapper.updateById(contract)`
- Now all 4 cost-generating entities (CtContract, MatReceipt, SubMeasure, VarOrder) consistently track cost generation status

### #13 — Extract nvl() to BigDecimalUtils
- Created `com.cgcpms.common.util.BigDecimalUtils` with `public static BigDecimal nvl(BigDecimal)` 
- Removed private `nvl()` from all 4 strategy files: ContractCostStrategy, MaterialReceiptCostStrategy, SubMeasureCostStrategy, VarOrderCostStrategy
- Added `import static com.cgcpms.common.util.BigDecimalUtils.nvl;` to all 4 — transparent replacement, no call-site changes needed

### #14 — Unify businessType constants
- Created `com.cgcpms.workflow.WorkflowBusinessTypes` with 6 constants: CONTRACT_APPROVAL, PURCHASE_ORDER, MATERIAL_RECEIPT, SUB_MEASURE, PAY_REQUEST, VAR_ORDER
- Updated all 6 handlers' `supportBusinessType()` to use constants instead of hardcoded strings
- ContractWorkflowHandler: changed from `ContractStatusConstants.BUSINESS_TYPE_CONTRACT_APPROVAL` to `WorkflowBusinessTypes.CONTRACT_APPROVAL`

### Bonus fixes
- Added missing `import com.cgcpms.payment.service.PayApplicationService` to `PayRequestWorkflowHandler.java` (pre-existing compile error)

### Compilation
- `./mvnw compile -q` passes clean ✅

---

## 14 遗留问题修复记录 (2026-06-11)

### P0 实现间隙 (4 项)
| # | 问题 | 修复 |
|---|------|------|
| 1 | 采购订单不校验合同余额 | PurchaseOrderWorkflowHandler.onApproved() 新增 SUM(approved orders) vs contract.currentAmount |
| 2 | 悲观锁缺失 | CtContractMapper.selectByIdForUpdate() + submitForApproval 加锁 |
| 3 | 边界测试缺失 | Phase2FullChainIntegrationTest 新增 test06c/test06d (恰好等于/超1分) |
| 4 | MySQL 未验证 | 延后至生产环境部署 |

### P1 Oracle 增强 (5 项)
| # | 问题 | 修复 |
|---|------|------|
| 5 | M2 重复依据 | saveBasis() HashSet 去重 |
| 6 | M3 合同不匹配 | validateBasisAmount() 逐条校验 contract_id |
| 7 | Rule 2 付款比例 | validatePaymentAmount() 新增 ct_payment_term 比例约束 |
| 8 | 悲观锁 (同#2) | 同上 |
| 9 | 两阶段校验 | submit(建议性) + PayRequestWorkflowHandler.onApproved(权威性) |

### P1 架构清理 (5 项)
| # | 问题 | 修复 |
|---|------|------|
| 10 | 双实体 | 删除 material.entity.MatReceipt，统一用 receipt.entity |
| 11 | 冗余 flag | 3 个 Handler 删除 cost_generated_flag LambdaUpdateWrapper |
| 12 | CtContract 缺 flag | V20 migration + entity 字段 + ContractCostStrategy 写入 |
| 13 | nvl() 重复 | 提取 BigDecimalUtils.nvl()，4 个 strategy 静态导入 |
| 14 | 常量硬编码 | 新建 WorkflowBusinessTypes，6 个 Handler 统一引用 |

