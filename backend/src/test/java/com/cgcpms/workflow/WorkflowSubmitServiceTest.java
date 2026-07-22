package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.service.ProjectBudgetService;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.service.CostTargetService;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowBusinessAccessValidator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD GREEN phase — workflow duplicate and logical-delete fixes.
 * <p>
 * Fixes applied:
 * <ul>
 *   <li>Duplicate (businessType,businessId) now throws
 *       {@code BusinessException("WORKFLOW_INSTANCE_EXISTS")}
 *       instead of DuplicateKeyException → SYSTEM_ERROR (500)</li>
 *   <li>Logically-deleted stale instances are hard-deleted before
 *       insert, freeing the unique key slot for resubmission</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("WorkflowSubmitService — duplicate and logical-delete conflicts")
class WorkflowSubmitServiceTest {

    private static final long PROJECT_ID = 10001L;
    private static final long PARTNER_A_ID = 20001L;
    private static final long PARTNER_B_ID = 20002L;
    private static final long CONTRACT_ID_DUPLICATE = 31001L;
    private static final long CONTRACT_ID_DELETED = 31002L;
    private static final long CONTRACT_ID_TEMPLATE = 31999L;
    private static final String BUSINESS_TYPE = "CONTRACT_APPROVAL";

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private MdPartnerMapper partnerMapper;

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private VarOrderMapper varOrderMapper;

    @Autowired
    private WorkflowBusinessAccessValidator businessAccessValidator;

    @Autowired
    private CostTargetService costTargetService;

    @Autowired
    private ProjectBudgetService projectBudgetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupContext() {
        // 恢复 V85 删除的 admin 用户并清理前序测试遗留的工作流数据
        jdbcTemplate.update(
            "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
            "SELECT 1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed' " +
            "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)");
        jdbcTemplate.update("DELETE FROM wf_task");
        jdbcTemplate.update("DELETE FROM wf_cc");
        jdbcTemplate.update("DELETE FROM wf_record");
        jdbcTemplate.update("DELETE FROM wf_instance");
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setAuthentication("ROLE_ADMIN");
        seedReferenceData();
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void seedReferenceData() {
        if (projectMapper.selectById(PROJECT_ID) == null) {
            PmProject project = new PmProject();
            project.setId(PROJECT_ID);
            project.setProjectCode("PRJ-TEST-WF");
            project.setProjectName("测试工作流项目");
            project.setProjectType("CONSTRUCTION");
            project.setContractAmount(new BigDecimal("5000000.00"));
            project.setTargetCost(new BigDecimal("4200000.00"));
            project.setStatus("RUNNING");
            project.setApprovalStatus("APPROVED");
            projectMapper.insert(project);
        }

        if (partnerMapper.selectById(PARTNER_A_ID) == null) {
            MdPartner partyA = new MdPartner();
            partyA.setId(PARTNER_A_ID);
            partyA.setPartnerCode("PT-TEST-WF-A");
            partyA.setPartnerName("工作流测试甲方");
            partyA.setPartnerType("PARTY_A");
            partyA.setBlacklistFlag(0);
            partyA.setStatus("ENABLE");
            partnerMapper.insert(partyA);
        }

        if (partnerMapper.selectById(PARTNER_B_ID) == null) {
            MdPartner partyB = new MdPartner();
            partyB.setId(PARTNER_B_ID);
            partyB.setPartnerCode("PT-TEST-WF-B");
            partyB.setPartnerName("工作流测试乙方");
            partyB.setPartnerType("PARTY_B");
            partyB.setBlacklistFlag(0);
            partyB.setStatus("ENABLE");
            partnerMapper.insert(partyB);
        }

        ensureContract(CONTRACT_ID_DUPLICATE, "CT-TEST-WF-DUP", "工作流重复提交测试合同");
        ensureContract(CONTRACT_ID_DELETED, "CT-TEST-WF-DEL", "工作流逻辑删除测试合同");
        ensureContract(CONTRACT_ID_TEMPLATE, "CT-TEST-WF-TPL", "工作流模板存在测试合同");
    }

    private void ensureContract(Long id, String code, String name) {
        if (contractMapper.selectById(id) != null) return;
        CtContract contract = new CtContract();
        contract.setId(id);
        contract.setProjectId(PROJECT_ID);
        contract.setContractCode(code);
        contract.setContractName(name);
        contract.setContractType("SUB");
        contract.setPartyAId(PARTNER_A_ID);
        contract.setPartyBId(PARTNER_B_ID);
        contract.setContractAmount(new BigDecimal("640000.00"));
        contract.setCurrentAmount(new BigDecimal("640000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setContractStatus("DRAFT");
        contract.setApprovalStatus("DRAFT");
        contractMapper.insert(contract);
    }

    private void markContractApproving(long contractId) {
        CtContract contract = contractMapper.selectById(contractId);
        contract.setApprovalStatus("APPROVING");
        contractMapper.updateById(contract);
    }

    private void setAuthentication(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "workflow-test", "n/a",
                        java.util.Arrays.stream(authorities)
                                .map(SimpleGrantedAuthority::new)
                                .toList()));
    }

    @Test
    @Transactional
    @DisplayName("VAR_ORDER 驳回后允许按原业务范围重新提交")
    void variationRejectedCanResubmit() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID_DUPLICATE);
        order.setPartnerId(PARTNER_A_ID);
        order.setVarCode("VO-WF-RESUBMIT");
        order.setVarName("驳回重提测试");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setApprovalStatus("REJECTED");
        varOrderMapper.insert(order);

        assertDoesNotThrow(() -> businessAccessValidator.validateResubmit(
                WorkflowBusinessTypes.VAR_ORDER, order.getId(), TestUserContext.TENANT_0,
                PROJECT_ID, CONTRACT_ID_DUPLICATE));

        order.setApprovalStatus("DRAFT");
        varOrderMapper.updateById(order);
        BusinessException wrongState = assertThrows(BusinessException.class,
                () -> businessAccessValidator.validateResubmit(
                        WorkflowBusinessTypes.VAR_ORDER, order.getId(), TestUserContext.TENANT_0,
                        PROJECT_ID, CONTRACT_ID_DUPLICATE));
        assertEquals("WORKFLOW_STATUS_NOT_SUBMITTABLE", wrongState.getCode());
    }

    @Test
    @Transactional
    @DisplayName("COST_TARGET generic submit fails before workflow or business mutation")
    void costTargetGenericSubmitFailsWithoutMutation() {
        CostTarget target = createCostTarget("WF-GENERIC-BLOCK");
        int version = target.getVersion();
        long instancesBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_instance WHERE business_type='COST_TARGET' AND business_id=?",
                Long.class, target.getId());

        BusinessException error = assertThrows(BusinessException.class, () -> workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                WorkflowBusinessTypes.COST_TARGET, target.getId(), "禁止通用提交",
                target.getTotalTargetAmount(), PROJECT_ID, null, null, null, null));

        assertEquals("COST_TARGET_DEDICATED_SUBMIT_REQUIRED", error.getCode());
        CostTarget unchanged = jdbcTarget(target.getId());
        assertEquals("DRAFT", unchanged.getApprovalStatus());
        assertEquals(version, unchanged.getVersion());
        assertNull(unchanged.getApprovalInstanceId());
        assertEquals(instancesBefore, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_instance WHERE business_type='COST_TARGET' AND business_id=?",
                Long.class, target.getId()));
    }

    @Test
    @Transactional
    @DisplayName("PROJECT_BUDGET generic submit fails before workflow mutation")
    void projectBudgetGenericSubmitFailsWithoutMutation() {
        long instancesBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_instance WHERE business_type='PROJECT_BUDGET' AND business_id=999991",
                Long.class);
        BusinessException error = assertThrows(BusinessException.class, () -> workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                WorkflowBusinessTypes.PROJECT_BUDGET, 999991L, "禁止通用提交",
                BigDecimal.ONE, PROJECT_ID, null, null, null, null));
        assertEquals("PROJECT_BUDGET_DEDICATED_SUBMIT_REQUIRED", error.getCode());
        assertEquals(instancesBefore, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_instance WHERE business_type='PROJECT_BUDGET' AND business_id=999991",
                Long.class));
    }

    @Test
    @Transactional
    @DisplayName("PRODUCTION_MEASUREMENT generic submit fails before workflow mutation")
    void productionMeasurementGenericSubmitFailsWithoutMutation() {
        BusinessException error = assertThrows(BusinessException.class, () -> workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                WorkflowBusinessTypes.PRODUCTION_MEASUREMENT, 999992L, "禁止通用提交",
                BigDecimal.ONE, PROJECT_ID, CONTRACT_ID_DUPLICATE, null, null, null));
        assertEquals("PRODUCTION_MEASUREMENT_DEDICATED_SUBMIT_REQUIRED", error.getCode());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_instance WHERE business_type='PRODUCTION_MEASUREMENT' AND business_id=999992",
                Long.class));
    }

    @Test
    @Transactional
    @DisplayName("PROJECT_BUDGET rejected edit keeps state and resubmits same instance")
    void projectBudgetRejectedEditUsesDedicatedSameInstanceResubmit() {
        PmProject project = projectMapper.selectById(PROJECT_ID);
        project.setStatus("ACTIVE");
        projectMapper.updateById(project);
        jdbcTemplate.update("""
                INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,level,sort_order,status,account_category,created_at,updated_at,deleted_flag)
                SELECT 31991,0,0,'WF-BUDGET-31991','工作流预算科目','DIRECT',1,0,'ENABLE','COST',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
                WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id=31991)
                """);
        ProjectBudget budget = new ProjectBudget();
        budget.setProjectId(PROJECT_ID); budget.setVersionNo("WF-BUDGET-" + System.nanoTime());
        budget.setBudgetName("预算工作流重提"); budget.setTotalAmount(new BigDecimal("100.00"));
        Long budgetId = projectBudgetService.create(budget);
        ProjectBudgetLine line = new ProjectBudgetLine(); line.setCostSubjectId(31991L); line.setBudgetAmount(new BigDecimal("100.00"));
        projectBudgetService.saveLines(budgetId, 0, List.of(line));
        projectBudgetService.submit(budgetId, 1);
        WfInstance instance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, WorkflowBusinessTypes.PROJECT_BUDGET)
                .eq(WfInstance::getBusinessId, budgetId));
        Long taskId = jdbcTemplate.queryForObject("SELECT id FROM wf_task WHERE instance_id=? AND task_status='PENDING' ORDER BY id LIMIT 1", Long.class, instance.getId());
        workflowEngine.reject(taskId, TestUserContext.USER_ADMIN, "admin", "退回修改", "budget-reject-1");
        ProjectBudget edit = new ProjectBudget(); edit.setId(budgetId); edit.setVersionNo(budget.getVersionNo());
        edit.setBudgetName("驳回后编辑"); edit.setTotalAmount(new BigDecimal("100.00"));
        projectBudgetService.update(edit, 3);
        assertEquals("REJECTED", jdbcTemplate.queryForObject("SELECT approval_status FROM project_budget WHERE id=?", String.class, budgetId));
        BusinessException generic = assertThrows(BusinessException.class,
                () -> workflowEngine.resubmit(instance.getId(), TestUserContext.USER_ADMIN, "admin"));
        assertEquals("PROJECT_BUDGET_DEDICATED_SUBMIT_REQUIRED", generic.getCode());
        projectBudgetService.submit(budgetId, 4);
        assertEquals(instance.getId(), wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, WorkflowBusinessTypes.PROJECT_BUDGET)
                .eq(WfInstance::getBusinessId, budgetId)).getId());
        assertEquals(2, wfInstanceMapper.selectById(instance.getId()).getCurrentRound());
    }

    @Test
    @Transactional
    @DisplayName("COST_TARGET rejected edit resubmits same instance only through dedicated service")
    void costTargetRejectedEditUsesRealResubmitChain() {
        CostTarget target = createCostTarget("WF-REAL-RESUBMIT");
        CostTargetItem item = new CostTargetItem();
        item.setCostSubjectId(31991L);
        item.setTargetAmount(new BigDecimal("100.00"));
        item.setBidCostAmount(new BigDecimal("100.00"));
        item.setResponsibilityAmount(new BigDecimal("100.00"));
        item.setResponsibleUserId(TestUserContext.USER_ADMIN);
        item.setResponsibilityUnit("成本组");
        costTargetService.batchSaveItems(target.getId(), target.getVersion(), List.of(item));
        costTargetService.submitForApproval(target.getId(), jdbcTarget(target.getId()).getVersion());

        CostTarget approving = jdbcTarget(target.getId());
        Long instanceId = approving.getApprovalInstanceId();
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM wf_task WHERE instance_id=? AND task_status='PENDING' ORDER BY id LIMIT 1",
                Long.class, instanceId);
        workflowEngine.reject(taskId, TestUserContext.USER_ADMIN, "admin", "退回修改", "ct-reject-1");

        CostTarget rejected = jdbcTarget(target.getId());
        assertEquals("REJECTED", rejected.getApprovalStatus());
        rejected.setVersionName("驳回后已编辑");
        costTargetService.update(rejected);
        CostTarget edited = jdbcTarget(target.getId());
        assertEquals("REJECTED", edited.getApprovalStatus());

        WfInstance rejectedInstance = wfInstanceMapper.selectById(instanceId);
        int instanceRound = rejectedInstance.getCurrentRound();
        int businessVersion = edited.getVersion();
        BusinessException genericError = assertThrows(BusinessException.class,
                () -> workflowEngine.resubmit(instanceId, TestUserContext.USER_ADMIN, "admin"));
        assertEquals("COST_TARGET_DEDICATED_SUBMIT_REQUIRED", genericError.getCode());
        assertEquals(instanceRound, wfInstanceMapper.selectById(instanceId).getCurrentRound());
        assertEquals(businessVersion, jdbcTarget(target.getId()).getVersion());
        assertEquals("REJECTED", jdbcTarget(target.getId()).getApprovalStatus());

        costTargetService.submitForApproval(target.getId(), businessVersion);

        CostTarget resubmitted = jdbcTarget(target.getId());
        WfInstance sameInstance = wfInstanceMapper.selectById(instanceId);
        assertEquals(instanceId, resubmitted.getApprovalInstanceId());
        assertEquals("APPROVING", resubmitted.getApprovalStatus());
        assertEquals(instanceRound + 1, sameInstance.getCurrentRound());
        assertEquals(1, sameInstance.getResubmitCount());
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, sameInstance.getInstanceStatus());
    }

    private CostTarget createCostTarget(String versionNo) {
        PmProject project = projectMapper.selectById(PROJECT_ID);
        project.setStatus("ACTIVE");
        projectMapper.updateById(project);
        jdbcTemplate.update("""
                INSERT INTO cost_subject
                  (id,tenant_id,parent_id,subject_code,subject_name,subject_type,level,sort_order,status,account_category,created_at,updated_at,deleted_flag)
                SELECT 31991,0,0,'WF-CT-31991','工作流目标成本科目','DIRECT',1,0,'ENABLE','COST',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
                WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id=31991)
                """);
        CostTarget target = new CostTarget();
        target.setProjectId(PROJECT_ID);
        target.setVersionNo(versionNo);
        target.setVersionName("目标成本工作流测试");
        target.setTotalTargetAmount(new BigDecimal("100.00"));
        target.setTotalBidCostAmount(new BigDecimal("100.00"));
        target.setTotalResponsibilityAmount(new BigDecimal("100.00"));
        costTargetService.create(target);
        return jdbcTarget(target.getId());
    }

    private CostTarget jdbcTarget(Long id) {
        return jdbcTemplate.queryForObject("""
                SELECT id,tenant_id,project_id,version_no,version_name,total_target_amount,
                       total_bid_cost_amount,total_responsibility_amount,is_active,approval_status,
                       effective_date,status,approval_instance_id,version,remark
                FROM cost_target WHERE id=? AND deleted_flag=0
                """, (rs, row) -> {
            CostTarget target = new CostTarget();
            target.setId(rs.getLong("id"));
            target.setTenantId(rs.getLong("tenant_id"));
            target.setProjectId(rs.getLong("project_id"));
            target.setVersionNo(rs.getString("version_no"));
            target.setVersionName(rs.getString("version_name"));
            target.setTotalTargetAmount(rs.getBigDecimal("total_target_amount"));
            target.setTotalBidCostAmount(rs.getBigDecimal("total_bid_cost_amount"));
            target.setTotalResponsibilityAmount(rs.getBigDecimal("total_responsibility_amount"));
            target.setIsActive(rs.getInt("is_active"));
            target.setApprovalStatus(rs.getString("approval_status"));
            target.setEffectiveDate(rs.getObject("effective_date", java.time.LocalDate.class));
            target.setStatus(rs.getString("status"));
            target.setApprovalInstanceId((Long) rs.getObject("approval_instance_id"));
            target.setVersion(rs.getInt("version"));
            target.setRemark(rs.getString("remark"));
            return target;
        }, id);
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-1: Duplicate submission produces explicit BusinessException
    // Submitting the same (businessType, businessId) twice now
    // throws BusinessException with code WORKFLOW_INSTANCE_EXISTS
    // instead of a raw DuplicateKeyException → SYSTEM_ERROR (500).
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("GREEN-1: duplicate (businessType,businessId) submit throws BusinessException")
    void testDuplicateSubmitCausesUniqueError() {
        long businessId = CONTRACT_ID_DUPLICATE;
        markContractApproving(businessId);

        // First submit — should succeed (CONTRACT_APPROVAL template exists in H2 V9)
        WfInstance first = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, businessId,
                "工作流重复提交测试", new BigDecimal("640000.00"),
                PROJECT_ID, businessId,
                null, null, null);
        assertNotNull(first, "首次提交应创建实例");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, first.getInstanceStatus());

        // Second submit with SAME businessType+businessId → BusinessException
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.submit(
                        TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                        BUSINESS_TYPE, businessId,
                        "工作流重复提交测试-第二次", new BigDecimal("640000.00"),
                        PROJECT_ID, businessId,
                        null, null, null));
        assertEquals("WORKFLOW_INSTANCE_EXISTS", ex.getCode(), "重复提交应返回业务错误码");
        assertEquals("该业务已提交审批，请勿重复提交", ex.getMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-2: Logically deleted stale instances are hard-deleted
    // before insert, freeing the unique key slot for resubmission.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("GREEN-2: logically deleted instance cleaned up, resubmit succeeds")
    void testDeletedInstanceBlocksResubmission() {
        long businessId = CONTRACT_ID_DELETED;
        markContractApproving(businessId);

        // First submit — create an active instance
        WfInstance first = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, businessId,
                "工作流逻辑删除测试", new BigDecimal("640000.00"),
                PROJECT_ID, businessId,
                null, null, null);
        assertNotNull(first, "首次提交应创建实例");

        // Logically delete the instance (set deleted_flag=1 via MyBatis-Plus)
        wfInstanceMapper.deleteById(first.getId());

        // Verify the instance is logically deleted (MyBatis-Plus hides it)
        WfInstance deleted = wfInstanceMapper.selectById(first.getId());
        assertNull(deleted, "逻辑删除后 MyBatis-Plus 查询应返回 null");

        // Resubmit with same (businessType, businessId) — should succeed now
        // because submit() hard-deletes stale logically-deleted rows first
        WfInstance second = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, businessId,
                "工作流逻辑删除后重新提交", new BigDecimal("640000.00"),
                PROJECT_ID, businessId,
                null, null, null);
        assertNotNull(second, "清理逻辑删除旧数据后应允许重新提交");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, second.getInstanceStatus());
        assertNotEquals(first.getId(), second.getId(), "新实例应有不同的ID");
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-3: Verify CONTRACT_APPROVAL template exists
    // Prerequisite for RED-1 and RED-2. If the template is missing,
    // the workflow submit would fail with TEMPLATE_NOT_FOUND instead
    // of hitting the duplicate key issue.
    // This test verifies the template exists.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("RED-3: CONTRACT_APPROVAL workflow template exists and is enabled")
    void testContractApprovalTemplateExists() {
        markContractApproving(CONTRACT_ID_TEMPLATE);
        WfInstance instance = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, CONTRACT_ID_TEMPLATE,
                "模板存在性验证", new BigDecimal("1000.00"),
                PROJECT_ID, CONTRACT_ID_TEMPLATE,
                null, null, null);
        assertNotNull(instance, "CONTRACT_APPROVAL 模板应存在并可创建实例");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, instance.getInstanceStatus());
    }

    @Test
    @Transactional
    @DisplayName("M3: submit stores canonical project and contract metadata")
    void testSubmitStoresCanonicalBusinessMetadata() {
        markContractApproving(CONTRACT_ID_TEMPLATE);
        WfInstance instance = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, CONTRACT_ID_TEMPLATE,
                "canonical 元数据验证", new BigDecimal("1000.00"),
                null, null,
                null, null, null);

        assertNotNull(instance, "缺省请求项目时仍应基于真实业务对象创建实例");
        assertEquals(PROJECT_ID, instance.getProjectId(), "实例项目应保存真实业务对象项目");
        assertEquals(CONTRACT_ID_TEMPLATE, instance.getContractId(), "实例合同应保存真实业务对象合同");
    }

    @Test
    @Transactional
    @DisplayName("M2: submit rejects forged projectId that does not match business object")
    void testSubmitRejectsForgedProjectId() {
        markContractApproving(CONTRACT_ID_DUPLICATE);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.submit(
                        TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                        BUSINESS_TYPE, CONTRACT_ID_DUPLICATE,
                        "伪造项目提交", new BigDecimal("640000.00"),
                        99999L, CONTRACT_ID_DUPLICATE,
                        null, null, null));
        assertEquals("WORKFLOW_PROJECT_MISMATCH", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("M4: resubmit syncs contract approval status back to APPROVING")
    void testResubmitSyncsContractApprovalStatus() {
        markContractApproving(CONTRACT_ID_TEMPLATE);
        WfInstance first = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, CONTRACT_ID_TEMPLATE,
                "重新提交状态同步", new BigDecimal("1000.00"),
                PROJECT_ID, CONTRACT_ID_TEMPLATE,
                null, null, null);
        assertNotNull(first);

        CtContract contract = contractMapper.selectById(CONTRACT_ID_TEMPLATE);
        assertEquals("APPROVING", contract.getApprovalStatus());

        first.setInstanceStatus(WorkflowConstants.INSTANCE_WITHDRAWN);
        wfInstanceMapper.updateById(first);
        contract.setApprovalStatus("WITHDRAWN");
        contractMapper.updateById(contract);

        WfInstance resubmitted = workflowEngine.resubmit(first.getId(),
                TestUserContext.USER_ADMIN, "admin");
        assertEquals(2, resubmitted.getCurrentRound());

        CtContract after = contractMapper.selectById(CONTRACT_ID_TEMPLATE);
        assertEquals("APPROVING", after.getApprovalStatus());
    }

    @Test
    @Transactional
    @DisplayName("P1: generic CONTRACT_APPROVAL submit on DRAFT contract fail-close")
    void testGenericContractSubmitRequiresApprovingStatus() {
        CtContract contract = contractMapper.selectById(CONTRACT_ID_DUPLICATE);
        contract.setApprovalStatus("DRAFT");
        contractMapper.updateById(contract);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.submit(
                        TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                        BUSINESS_TYPE, CONTRACT_ID_DUPLICATE,
                        "generic 绕过提交", new BigDecimal("640000.00"),
                        PROJECT_ID, CONTRACT_ID_DUPLICATE,
                        null, null, null));
        assertEquals("WORKFLOW_STATUS_NOT_SUBMITTABLE", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("P2: CONTRACT_APPROVAL resubmit requires contract:submit permission")
    void testResubmitRequiresContractSubmitPermission() {
        markContractApproving(CONTRACT_ID_TEMPLATE);
        WfInstance first = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, CONTRACT_ID_TEMPLATE,
                "权限校验重提", new BigDecimal("1000.00"),
                PROJECT_ID, CONTRACT_ID_TEMPLATE,
                null, null, null);

        first.setInstanceStatus(WorkflowConstants.INSTANCE_WITHDRAWN);
        wfInstanceMapper.updateById(first);

        CtContract contract = contractMapper.selectById(CONTRACT_ID_TEMPLATE);
        contract.setApprovalStatus("WITHDRAWN");
        contractMapper.updateById(contract);

        TestUserContext.setUser(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN, "admin", List.of("USER"));
        setAuthentication("workflow:resubmit");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowEngine.resubmit(first.getId(), TestUserContext.USER_ADMIN, "admin"));
        assertEquals("WORKFLOW_PERMISSION_DENIED", ex.getCode());
    }
}
