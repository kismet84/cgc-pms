package com.cgcpms.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.service.PmProjectService;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Project archive guard tests — verify that active dependencies block archiving
 * and that physical delete is restricted to SUPER_ADMIN on empty projects.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PmProjectArchiveTest {

    private static final long TENANT_0 = 0L;
    private static final long USER_ADMIN = 1L;

    @Autowired
    private PmProjectService projectService;
    @Autowired
    private PmProjectMapper projectMapper;
    @Autowired
    private CtContractMapper contractMapper;
    @Autowired
    private PayApplicationMapper payApplicationMapper;
    @Autowired
    private PayRecordMapper payRecordMapper;
    @Autowired
    private StlSettlementMapper settlementMapper;
    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    private Long projectId;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void clearContext() {
        if (projectId != null) {
            // Clean up test data in reverse dependency order
            wfInstanceMapper.delete(new LambdaQueryWrapper<WfInstance>()
                    .eq(WfInstance::getProjectId, projectId));
            settlementMapper.delete(new LambdaQueryWrapper<StlSettlement>()
                    .eq(StlSettlement::getProjectId, projectId));
            payRecordMapper.delete(new LambdaQueryWrapper<PayRecord>()
                    .eq(PayRecord::getProjectId, projectId));
            payApplicationMapper.delete(new LambdaQueryWrapper<PayApplication>()
                    .eq(PayApplication::getProjectId, projectId));
            contractMapper.delete(new LambdaQueryWrapper<CtContract>()
                    .eq(CtContract::getProjectId, projectId));
            projectMapper.deleteById(projectId);
        }
        UserContext.clear();
    }

    private Long createProject(String code, String name, String status) {
        PmProject project = new PmProject();
        project.setTenantId(TENANT_0);
        project.setProjectCode(code);
        project.setProjectName(name);
        project.setProjectType("BUILDING");
        project.setStatus(status);
        project.setTargetCost(new BigDecimal("1000000.00"));
        project.setContractAmount(new BigDecimal("2000000.00"));
        Long id = projectService.create(project);
        PmProject stored = projectMapper.selectById(id);
        if (!status.equals(stored.getStatus())) {
            stored.setStatus(status);
            projectMapper.updateById(stored);
        }
        return id;
    }

    private Long createContract(Long projectId, String code, String contractStatus) {
        CtContract contract = new CtContract();
        contract.setTenantId(TENANT_0);
        contract.setProjectId(projectId);
        contract.setContractCode(code);
        contract.setContractName("测试合同");
        contract.setContractType("MAIN");
        contract.setContractAmount(new BigDecimal("500000.00"));
        contract.setCurrentAmount(new BigDecimal("500000.00"));
        contract.setContractStatus(contractStatus);
        contract.setApprovalStatus("APPROVED");
        contractMapper.insert(contract);
        return contract.getId();
    }

    private Long createPayApplication(Long projectId, Long contractId, String code, String payStatus) {
        PayApplication app = new PayApplication();
        app.setTenantId(TENANT_0);
        app.setProjectId(projectId);
        app.setContractId(contractId);
        app.setApplyCode(code);
        app.setApplyAmount(new BigDecimal("100000.00"));
        app.setPayType("PROGRESS"); // NOT NULL column
        app.setPayStatus(payStatus);
        app.setApprovalStatus("APPROVED");
        payApplicationMapper.insert(app);
        return app.getId();
    }

    private Long createSettlement(Long projectId, Long contractId, String code, boolean finalized) {
        StlSettlement stl = new StlSettlement();
        stl.setTenantId(TENANT_0);
        stl.setProjectId(projectId);
        stl.setContractId(contractId);
        stl.setSettlementCode(code);
        stl.setSettlementType("FINAL");
        stl.setContractAmount(new BigDecimal("500000.00"));
        stl.setMeasuredAmount(new BigDecimal("480000.00"));
        stl.setStatus("DRAFT");
        if (finalized) {
            stl.setFinalizedAt(LocalDateTime.now());
        }
        settlementMapper.insert(stl);
        return stl.getId();
    }

    private Long createWorkflowInstance(Long projectId, String businessType, Long businessId, String instanceStatus) {
        WfInstance wf = new WfInstance();
        wf.setTenantId(TENANT_0);
        wf.setTemplateId(100L); // dummy template ID for test
        wf.setProjectId(projectId);
        wf.setBusinessType(businessType);
        wf.setBusinessId(businessId);
        wf.setTitle("测试审批");
        wf.setInstanceStatus(instanceStatus);
        wf.setCurrentRound(1);
        wf.setInitiatorId(USER_ADMIN);
        wf.setStartedAt(LocalDateTime.now());
        wfInstanceMapper.insert(wf);
        return wf.getId();
    }

    // ═══════════════════════════════════════════════════════════
    // TC1: Empty project can be archived
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @Transactional
    @DisplayName("TC1: 空项目可以归档")
    void testEmptyProjectCanBeArchived() {
        projectId = createProject("PRJ-ARCH-EMPTY-" + System.currentTimeMillis(), "空项目归档测试", "CLOSED");

        projectService.archive(projectId);

        PmProject archived = projectMapper.selectById(projectId);
        assertEquals("ARCHIVED", archived.getStatus(), "空项目应可归档");
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("TC8: 同租户但无项目数据范围的用户不能归档")
    void testArchiveRequiresProjectDataScope() {
        projectId = createProject("PRJ-ARCH-SCOPE-" + System.currentTimeMillis(), "项目范围归档测试", "DRAFT");
        UserContext.set(Jwts.claims()
                .add("userId", 999999L)
                .add("username", "scoped-user")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of())
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.archive(projectId));
        assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());
        assertEquals("DRAFT", projectMapper.selectById(projectId).getStatus());
    }

    // ═══════════════════════════════════════════════════════════
    // TC2: Project with active contract blocks archiving
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @Transactional
    @DisplayName("TC2: 存在进行中合同时无法归档")
    void testActiveContractBlocksArchive() {
        projectId = createProject("PRJ-ARCH-CT-" + System.currentTimeMillis(), "合同归档测试", "CLOSED");
        Long contractId = createContract(projectId, "CT-ARCH-TEST", "PERFORMING");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.archive(projectId),
                "存在进行中合同时归档应抛出BusinessException");
        assertEquals("PROJECT_HAS_ACTIVE_CONTRACTS", ex.getCode());

        // Settle the contract, then archive should succeed
        CtContract contract = contractMapper.selectById(contractId);
        contract.setContractStatus("SETTLED");
        contractMapper.updateById(contract);

        projectService.archive(projectId);
        assertEquals("ARCHIVED", projectMapper.selectById(projectId).getStatus(),
                "合同结算后应可归档");
    }

    // ═══════════════════════════════════════════════════════════
    // TC3: Project with unpaid payment blocks archiving
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @Transactional
    @DisplayName("TC3: 存在未付完的付款申请时无法归档")
    void testActivePaymentBlocksArchive() {
        projectId = createProject("PRJ-ARCH-PAY-" + System.currentTimeMillis(), "付款归档测试", "CLOSED");
        Long contractId = createContract(projectId, "CT-PAY-TEST", "SETTLED");
        createPayApplication(projectId, contractId, "PAY-ARCH-TEST", "APPROVED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.archive(projectId),
                "存在未付完的付款申请时归档应抛出BusinessException");
        assertEquals("PROJECT_HAS_ACTIVE_PAYMENTS", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // TC4: Project with unfinalized settlement blocks archiving
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @Transactional
    @DisplayName("TC4: 存在未完成的结算时无法归档")
    void testUnfinalizedSettlementBlocksArchive() {
        projectId = createProject("PRJ-ARCH-STL-" + System.currentTimeMillis(), "结算归档测试", "CLOSED");
        Long contractId = createContract(projectId, "CT-STL-TEST", "SETTLED");
        createSettlement(projectId, contractId, "STL-ARCH-TEST", false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.archive(projectId),
                "存在未完成的结算时归档应抛出BusinessException");
        assertEquals("PROJECT_HAS_ACTIVE_SETTLEMENTS", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // TC5: Project with running workflow blocks archiving
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @Transactional
    @DisplayName("TC5: 存在运行中审批流程时无法归档")
    void testRunningWorkflowBlocksArchive() {
        projectId = createProject("PRJ-ARCH-WF-" + System.currentTimeMillis(), "工作流归档测试", "CLOSED");
        createWorkflowInstance(projectId, "CONTRACT_APPROVAL", projectId + 100, "RUNNING");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.archive(projectId),
                "存在运行中审批流程时归档应抛出BusinessException");
        assertEquals("PROJECT_HAS_RUNNING_WORKFLOWS", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // TC6: ADMIN cannot physically delete any project
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @Transactional
    @DisplayName("TC6: 普通管理员无法物理删除项目")
    void testAdminCannotPhysicalDelete() {
        projectId = createProject("PRJ-DEL-ADMIN-" + System.currentTimeMillis(), "管理员删除测试", "DRAFT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.delete(projectId),
                "非SUPER_ADMIN物理删除应抛出BusinessException");
        assertEquals("DELETE_FORBIDDEN", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // TC7: SUPER_ADMIN can physically delete empty project
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @Transactional
    @DisplayName("TC7: SUPER_ADMIN可以物理删除空项目")
    void testSuperAdminCanPhysicalDeleteEmptyProject() {
        projectId = createProject("PRJ-DEL-SA-" + System.currentTimeMillis(), "超管删除测试", "DRAFT");

        // Switch to SUPER_ADMIN role
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "superadmin")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of("SUPER_ADMIN"))
                .build());

        projectService.delete(projectId);

        // After physical delete (soft-delete via MyBatis-Plus @TableLogic), the project should be gone
        PmProject deleted = projectMapper.selectById(projectId);
        assertNull(deleted, "SUPER_ADMIN物理删除(软删)后项目应不可查询");
    }

    // ═══════════════════════════════════════════════════════════
    // TC8: SUPER_ADMIN cannot physically delete project with dependencies
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @Transactional
    @DisplayName("TC8: SUPER_ADMIN无法物理删除有依赖的项目")
    void testSuperAdminCannotDeleteProjectWithDependencies() {
        projectId = createProject("PRJ-DEL-DEP-" + System.currentTimeMillis(), "有依赖删除测试", "ACTIVE");
        createContract(projectId, "CT-DEL-DEP", "SETTLED");

        // Switch to SUPER_ADMIN role
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "superadmin")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of("SUPER_ADMIN"))
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.delete(projectId),
                "SUPER_ADMIN无法物理删除有依赖合同的项目");
        assertEquals("PROJECT_HAS_DEPENDENCIES", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // TC9: Project already archived cannot be re-archived
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @Transactional
    @DisplayName("TC9: 已归档项目无法再次归档")
    void testAlreadyArchivedProjectRejectsReArchive() {
        projectId = createProject("PRJ-ARCH-DUP-" + System.currentTimeMillis(), "重复归档测试", "CLOSED");

        // First archive
        projectService.archive(projectId);
        assertEquals("ARCHIVED", projectMapper.selectById(projectId).getStatus());

        // Second archive should fail
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.archive(projectId),
                "已归档项目再次归档应抛出BusinessException");
        assertEquals("PROJECT_ALREADY_ARCHIVED", ex.getCode());
    }
}
