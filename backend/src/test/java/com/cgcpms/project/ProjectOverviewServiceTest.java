package com.cgcpms.project;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.PaymentTestFixtures;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.service.PmProjectMemberService;
import com.cgcpms.project.service.PmProjectService;
import com.cgcpms.project.service.ProjectOverviewService;
import com.cgcpms.project.vo.ProjectOverviewVO;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;

import java.util.ArrayList;
import java.util.List;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectOverviewServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;
    private static final long TEST_USER_1 = 11001L;
    private static final long TEST_USER_2 = 11002L;

    @Autowired
    private PmProjectService projectService;

    @Autowired
    private PmProjectMemberService memberService;

    @Autowired
    private ProjectOverviewService overviewService;

    @Autowired
    private CtContractMapper ctContractMapper;

    @Autowired
    private CostSummaryMapper costSummaryMapper;

    @Autowired
    private PayRecordMapper payRecordMapper;

    @Autowired
    private PayApplicationMapper payApplicationMapper;

    @Autowired
    private AlertLogMapper alertLogMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    private Long testProjectId;

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
        UserContext.clear();
    }

    private Long createTestProject() {
        PmProject project = new PmProject();
        project.setTenantId(TENANT_0);
        project.setProjectCode("TST-OV-" + System.currentTimeMillis());
        project.setProjectName("概览测试项目");
        project.setProjectType("CONSTRUCTION");
        project.setStatus("ACTIVE");
        project.setTargetCost(new BigDecimal("5000000.00"));
        project.setContractAmount(new BigDecimal("8000000.00"));
        return projectService.create(project);
    }

    /** Ensure test users exist in H2 for member tests. */
    private void ensureTestUser(Long userId, String username, String realName) {
        SysUser existing = sysUserMapper.selectById(userId);
        if (existing == null) {
            SysUser user = new SysUser();
            user.setId(userId);
            user.setTenantId(TENANT_0);
            user.setUsername(username);
            user.setRealName(realName);
            user.setPassword("test");
            user.setStatus("ACTIVE");
            sysUserMapper.insert(user);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TC1: Project with full data — all fields populated
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @Transactional
    @DisplayName("TC1: 项目概览完整数据 → contracts/costs/payments/warnings/members 全部聚合正确")
    void test01_fullOverview() {
        testProjectId = createTestProject();

        // Create 3 contracts
        List<Long> contractIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            CtContract contract = new CtContract();
            contract.setTenantId(TENANT_0);
            contract.setProjectId(testProjectId);
            contract.setContractCode("CT-OV-" + i);
            contract.setContractName("概览合同" + (i + 1));
            contract.setContractType("MAIN");
            contract.setContractAmount(new BigDecimal("1000000.00"));
            contract.setCurrentAmount(new BigDecimal("1000000.00"));
            contract.setContractStatus("PERFORMING");
            ctContractMapper.insert(contract);
            contractIds.add(contract.getId());
        }

        // Create cost_summary rows (2 subjects)
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 2; i++) {
            CostSummary summary = new CostSummary();
            summary.setTenantId(TENANT_0);
            summary.setProjectId(testProjectId);
            summary.setSummaryDate(today);
            summary.setCostSubjectId(100L + i);
            summary.setTargetCost(new BigDecimal("5000000.00"));
            summary.setContractLockedCost(new BigDecimal("1500000.00"));
            summary.setActualCost(new BigDecimal("800000.00"));
            summary.setPaidAmount(new BigDecimal("400000.00"));
            summary.setDynamicCost(new BigDecimal("4500000.00"));
            summary.setEstimatedRemainingCost(new BigDecimal("3700000.00"));
            summary.setContractIncome(new BigDecimal("8000000.00"));
            summary.setExpectedProfit(new BigDecimal("3500000.00"));
            summary.setCostDeviation(new BigDecimal("-500000.00"));
            costSummaryMapper.insert(summary);
        }

        // Create pay_records (2 successful, 1 failed)
        for (int i = 0; i < 3; i++) {
            long applicationId = IdWorker.getId();
            PaymentTestFixtures.insertApplication(payApplicationMapper, applicationId, TENANT_0,
                    testProjectId, contractIds.get(i), null, new BigDecimal("200000.00"));
            PayRecord record = new PayRecord();
            record.setTenantId(TENANT_0);
            record.setProjectId(testProjectId);
            record.setPayApplicationId(applicationId);
            record.setContractId(contractIds.get(i));
            record.setPayAmount(new BigDecimal("200000.00"));
            record.setPayDate(LocalDate.now());
            record.setPayStatus(i < 2 ? "SUCCESS" : "PENDING");
            payRecordMapper.insert(record);
        }

        // Create alert_logs (this month)
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 2; i++) {
            AlertLog alert = new AlertLog();
            alert.setTenantId(TENANT_0);
            alert.setProjectId(testProjectId);
            alert.setRuleType("DYNAMIC_COST_EXCEEDS_TARGET");
            alert.setSeverity("HIGH");
            alert.setMessage("预警消息" + (i + 1));
            alert.setTriggeredAt(now);
            alert.setIsRead(0);
            alertLogMapper.insert(alert);
        }

        // Create members with real users
        ensureTestUser(TEST_USER_1, "user1", "张三");
        ensureTestUser(TEST_USER_2, "user2", "李四");

        for (int i = 0; i < 2; i++) {
            PmProjectMember member = new PmProjectMember();
            member.setUserId(i == 0 ? TEST_USER_1 : TEST_USER_2);
            member.setRoleCode(i == 0 ? "PM" : "CM");
            member.setPositionName(i == 0 ? "项目经理" : "商务经理");
            memberService.create(testProjectId, member);
        }

        // ACT: Get overview
        ProjectOverviewVO vo = overviewService.getOverview(testProjectId);

        // ASSERT
        assertNotNull(vo, "概览不应为null");
        assertEquals(String.valueOf(testProjectId), vo.getProjectId());

        // Contract aggregations
        assertEquals("3", vo.getContractCount(), "应有3个合同");
        assertEquals("3000000.00", vo.getTotalContractAmount(), "合同总金额应为3*100万=300万");

        // Cost aggregations (2 subjects, dynamicCost = 4.5M each → sum = 9.0M)
        assertEquals("9000000.00", vo.getDynamicCost(), "动态成本应为2*450万=900万");
        // paidAmount from pay_records SUCCESS = 2*200k = 400k
        assertEquals("400000.00", vo.getPaidAmount(), "已付金额应为2*20万=40万");

        // Warning count
        assertEquals("2", vo.getWarningCount(), "本月应有2条预警");

        // Member aggregations
        assertEquals("2", vo.getMemberCount(), "应有2个成员");
        assertNotNull(vo.getMembers(), "成员列表不应为null");
        assertEquals(2, vo.getMembers().size());

        // Member 1
        ProjectOverviewVO.MemberBriefVO m1 = vo.getMembers().stream()
                .filter(m -> "PM".equals(m.getRoleCode()))
                .findFirst().orElse(null);
        assertNotNull(m1, "应有PM角色成员");
        assertEquals(String.valueOf(TEST_USER_1), m1.getUserId());
        assertEquals("张三", m1.getUserName(), "用户名应通过batch查询填充");

        // Member 2
        ProjectOverviewVO.MemberBriefVO m2 = vo.getMembers().stream()
                .filter(m -> "CM".equals(m.getRoleCode()))
                .findFirst().orElse(null);
        assertNotNull(m2, "应有CM角色成员");
        assertEquals(String.valueOf(TEST_USER_2), m2.getUserId());
        assertEquals("李四", m2.getUserName());

        System.out.println("✅ TC1 通过: contracts=3, totalAmount=300万, dynamicCost=900万, paid=40万, warnings=2, members=2");
    }

    // ═══════════════════════════════════════════════════════════
    // TC2: Empty project — all zeros
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @Transactional
    @DisplayName("TC2: 空白项目概览 → 所有聚合字段为0/空")
    void test02_emptyOverview() {
        testProjectId = createTestProject();

        ProjectOverviewVO vo = overviewService.getOverview(testProjectId);

        assertNotNull(vo);
        assertEquals("0", vo.getContractCount());
        assertEquals("0", vo.getTotalContractAmount());
        assertEquals("0", vo.getDynamicCost());
        assertEquals("0", vo.getPaidAmount());
        assertEquals("0", vo.getWarningCount());
        assertEquals("0", vo.getMemberCount());
        assertNotNull(vo.getMembers());
        assertTrue(vo.getMembers().isEmpty());

        System.out.println("✅ TC2 通过: 空白项目所有字段为0");
    }

    // ═══════════════════════════════════════════════════════════
    // TC3: Tenant isolation — wrong tenant fails
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @Transactional
    @DisplayName("TC3: 跨租户隔离 → 不同tenantId无法查询")
    void test03_crossTenantIsolation() {
        testProjectId = createTestProject();

        // Switch to another tenant
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());

        assertThrows(BusinessException.class,
                () -> overviewService.getOverview(testProjectId),
                "不同租户查询概览应抛出BusinessException");

        System.out.println("✅ TC3 通过: 跨租户隔离正确");
    }

    // ═══════════════════════════════════════════════════════════
    // TC4: Non-existent project
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @Transactional
    @DisplayName("TC4: 不存在的项目 → 抛出BusinessException")
    void test04_nonExistentProject() {
        assertThrows(BusinessException.class,
                () -> overviewService.getOverview(99999999L),
                "不存在的项目应抛出BusinessException");

        System.out.println("✅ TC4 通过: 不存在的项目正确拦截");
    }
}
