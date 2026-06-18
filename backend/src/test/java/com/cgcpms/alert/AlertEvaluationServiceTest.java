package com.cgcpms.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.service.AlertEvaluationService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@DisplayName("AlertEvaluationService — 告警评估引擎测试")
class AlertEvaluationServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;

    @Autowired
    private AlertEvaluationService alertService;
    @Autowired
    private AlertLogMapper alertLogMapper;
    @Autowired
    private PmProjectMapper projectMapper;
    @Autowired
    private PmProjectMemberMapper projectMemberMapper;
    @Autowired
    private CtContractMapper contractMapper;
    @Autowired
    private MdPartnerMapper partnerMapper;
    @Autowired
    private CostSummaryMapper costSummaryMapper;
    @Autowired
    private CostSubjectMapper costSubjectMapper;

    private Long testProjectId;
    private Long testContractId;

    @BeforeEach
    void setup() {
        TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);

        // 种子项目
        PmProject project = new PmProject();
        project.setId(81001L);
        project.setProjectCode("ALERT-TEST-001");
        project.setProjectName("告警测试项目");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("5000000.00"));
        project.setTargetCost(new BigDecimal("4000000.00"));
        project.setStatus("ACTIVE");
        project.setApprovalStatus("APPROVED");
        project.setTenantId(TENANT_ID);
        if (projectMapper.selectById(81001L) == null) projectMapper.insert(project);
        testProjectId = 81001L;

        // 种子项目成员（通知目标）
        if (projectMemberMapper.selectCount(
                new LambdaQueryWrapper<PmProjectMember>()
                        .eq(PmProjectMember::getTenantId, TENANT_ID)
                        .eq(PmProjectMember::getProjectId, testProjectId)) == 0) {
            PmProjectMember member = new PmProjectMember();
            member.setTenantId(TENANT_ID);
            member.setProjectId(testProjectId);
            member.setUserId(USER_ADMIN);
            member.setRoleCode("PM");
            member.setStatus("ACTIVE");
            projectMemberMapper.insert(member);
        }

        // 种子合作方
        if (partnerMapper.selectById(81001L) == null) {
            MdPartner pa = new MdPartner();
            pa.setId(81001L); pa.setPartnerCode("A-PA"); pa.setPartnerName("测试甲方");
            pa.setPartnerType("PARTY_A"); pa.setStatus("ENABLE"); pa.setTenantId(TENANT_ID);
            partnerMapper.insert(pa);
        }
        if (partnerMapper.selectById(81002L) == null) {
            MdPartner pb = new MdPartner();
            pb.setId(81002L); pb.setPartnerCode("A-PB"); pb.setPartnerName("测试乙方");
            pb.setPartnerType("PARTY_B"); pb.setStatus("ENABLE"); pb.setTenantId(TENANT_ID);
            partnerMapper.insert(pb);
        }

        // 种子合同（PERFORMING + 未超期）
        CtContract contract = new CtContract();
        contract.setId(81001L);
        contract.setProjectId(testProjectId);
        contract.setContractCode("A-CT-001");
        contract.setContractName("告警测试合同");
        contract.setContractType("SUB");
        contract.setPartyAId(81001L);
        contract.setPartyBId(81002L);
        contract.setContractAmount(new BigDecimal("1000000.00"));
        contract.setCurrentAmount(new BigDecimal("1000000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setTaxRate(new BigDecimal("13.00"));
        contract.setContractStatus("PERFORMING");
        contract.setApprovalStatus("APPROVED");
        contract.setStartDate(LocalDate.of(2024, 1, 1));
        contract.setEndDate(LocalDate.now().plusDays(365));
        contract.setTenantId(TENANT_ID);
        if (contractMapper.selectById(81001L) == null) contractMapper.insert(contract);
        testContractId = 81001L;
    }

    @AfterEach
    void cleanup() {
        // 清理告警日志
        alertLogMapper.delete(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, testProjectId));
        TestUserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // Rule 4: 合同超期告警
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TA1: evaluateContractOverdue — PERFORMING 合同 endDate 在今天之前触发告警")
    void testEvaluateContractOverdue_Triggers() {
        // 将合同 endDate 设为昨天
        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().minusDays(1));
        contractMapper.updateById(contract);

        int count = alertService.evaluateProject(TENANT_ID, testProjectId);
        assertTrue(count > 0, "应生成至少1条告警");

        // 验证告警记录
        List<AlertLog> alerts = alertService.list(TENANT_ID, testProjectId, null, null);
        assertTrue(alerts.stream().anyMatch(a -> "CONTRACT_OVERDUE".equals(a.getRuleType())),
                "应包含 CONTRACT_OVERDUE 告警");
    }

    @Test
    @Transactional
    @DisplayName("TA2: evaluateContractOverdue — 合同未超期不生成告警")
    void testEvaluateContractOverdue_NoTrigger() {
        // 合同 endDate 是未来，不应生成 CONTRACT_OVERDUE
        int count = alertService.evaluateProject(TENANT_ID, testProjectId);

        List<AlertLog> alerts = alertService.list(TENANT_ID, testProjectId, null, null);
        boolean hasOverdue = alerts.stream().anyMatch(a -> "CONTRACT_OVERDUE".equals(a.getRuleType()));
        // 可能生成其他类型告警，但不应有 CONTRACT_OVERDUE
        assertFalse(hasOverdue, "合同未超期时不应生成 CONTRACT_OVERDUE");
    }

    // ═══════════════════════════════════════════════════════════════
    // Rule 7: 合同到期告警（30天内）
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TA3: evaluateContractExpiring — 合同 endDate 在 30 天内触发到期告警")
    void testEvaluateContractExpiring_Triggers() {
        // 合同在 15 天后到期
        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().plusDays(15));
        contractMapper.updateById(contract);

        int count = alertService.evaluateProject(TENANT_ID, testProjectId);
        List<AlertLog> alerts = alertService.list(TENANT_ID, testProjectId, null, null);
        assertTrue(alerts.stream().anyMatch(a -> "CONTRACT_EXPIRING".equals(a.getRuleType())),
                "15天后到期应触发 CONTRACT_EXPIRING");
    }

    @Test
    @Transactional
    @DisplayName("TA4: evaluateContractExpiring — 合同 endDate 超过 30 天不触发")
    void testEvaluateContractExpiring_NoTrigger() {
        // 合同在 60 天后到期（超出阈值）
        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().plusDays(60));
        contractMapper.updateById(contract);

        alertService.evaluateProject(TENANT_ID, testProjectId);
        List<AlertLog> alerts = alertService.list(TENANT_ID, testProjectId, null, null);
        boolean hasExpiring = alerts.stream().anyMatch(a -> "CONTRACT_EXPIRING".equals(a.getRuleType()));
        assertFalse(hasExpiring, "60天后到期不应触发 CONTRACT_EXPIRING");
    }

    // ═══════════════════════════════════════════════════════════════
    // Rule 1: 动态成本超目标
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TA5: evaluateDynamicCostExceedsTarget — 动态成本超目标触发告警")
    void testDynamicCostExceedsTarget_Triggers() {
        // 创建成本汇总行（动态成本 > 目标成本）
        CostSummary summary = new CostSummary();
        summary.setTenantId(TENANT_ID);
        summary.setProjectId(testProjectId);
        summary.setSummaryDate(LocalDate.now());
        summary.setTargetCost(new BigDecimal("1000000.00"));
        summary.setDynamicCost(new BigDecimal("1500000.00"));
        summary.setContractLockedCost(BigDecimal.ZERO);
        summary.setActualCost(BigDecimal.ZERO);
        summary.setPaidAmount(BigDecimal.ZERO);
        summary.setEstimatedRemainingCost(BigDecimal.ZERO);
        summary.setContractIncome(BigDecimal.ZERO);
        summary.setExpectedProfit(BigDecimal.ZERO);
        summary.setCostDeviation(new BigDecimal("500000.00"));
        costSummaryMapper.insert(summary);

        int count = alertService.evaluateProject(TENANT_ID, testProjectId);
        List<AlertLog> alerts = alertService.list(TENANT_ID, testProjectId, null, null);
        assertTrue(alerts.stream().anyMatch(a -> "DYNAMIC_COST_EXCEEDS_TARGET".equals(a.getRuleType())),
                "动态成本 > 目标成本时应触发 HIGH 告警");
    }

    @Test
    @Transactional
    @DisplayName("TA6: evaluateDynamicCostExceedsTarget — 动态成本不超目标不触发")
    void testDynamicCostExceedsTarget_NoTrigger() {
        // 动态成本 <= 目标成本
        CostSummary summary = new CostSummary();
        summary.setTenantId(TENANT_ID);
        summary.setProjectId(testProjectId);
        summary.setSummaryDate(LocalDate.now());
        summary.setTargetCost(new BigDecimal("1000000.00"));
        summary.setDynamicCost(new BigDecimal("800000.00"));
        summary.setContractLockedCost(BigDecimal.ZERO);
        summary.setActualCost(BigDecimal.ZERO);
        summary.setPaidAmount(BigDecimal.ZERO);
        summary.setEstimatedRemainingCost(BigDecimal.ZERO);
        summary.setContractIncome(BigDecimal.ZERO);
        summary.setExpectedProfit(BigDecimal.ZERO);
        summary.setCostDeviation(new BigDecimal("-200000.00"));
        costSummaryMapper.insert(summary);

        alertService.evaluateProject(TENANT_ID, testProjectId);
        List<AlertLog> alerts = alertService.list(TENANT_ID, testProjectId, null, null);
        boolean hasDynamicCost = alerts.stream().anyMatch(a -> "DYNAMIC_COST_EXCEEDS_TARGET".equals(a.getRuleType()));
        assertFalse(hasDynamicCost, "动态成本不超目标不应触发告警");
    }

    // ═══════════════════════════════════════════════════════════════
    // list / markRead
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TA7: list — 按 severity 筛选")
    void testList_FilterBySeverity() {
        // 手动插入一条 HIGH 告警
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(testProjectId);
        alert.setRuleType("TEST_RULE");
        alert.setSeverity("HIGH");
        alert.setMessage("测试HIGH告警");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);

        List<AlertLog> highAlerts = alertService.list(TENANT_ID, testProjectId, "HIGH", null);
        assertFalse(highAlerts.isEmpty());
        assertTrue(highAlerts.stream().allMatch(a -> "HIGH".equals(a.getSeverity())));
    }

    @Test
    @Transactional
    @DisplayName("TA8: list — 按 isRead 筛选")
    void testList_FilterByIsRead() {
        // 插入已读和未读各一条
        AlertLog read = new AlertLog();
        read.setTenantId(TENANT_ID);
        read.setProjectId(testProjectId);
        read.setRuleType("TEST_READ");
        read.setSeverity("LOW");
        read.setMessage("已读告警");
        read.setTriggeredAt(LocalDateTime.now());
        read.setIsRead(1);
        read.setDeletedFlag(0);
        alertLogMapper.insert(read);

        AlertLog unread = new AlertLog();
        unread.setTenantId(TENANT_ID);
        unread.setProjectId(testProjectId);
        unread.setRuleType("TEST_UNREAD");
        unread.setSeverity("LOW");
        unread.setMessage("未读告警");
        unread.setTriggeredAt(LocalDateTime.now());
        unread.setIsRead(0);
        unread.setDeletedFlag(0);
        alertLogMapper.insert(unread);

        List<AlertLog> unreadOnly = alertService.list(TENANT_ID, testProjectId, null, 0);
        assertTrue(unreadOnly.stream().allMatch(a -> a.getIsRead() == 0), "筛选 isRead=0 应只返回未读");

        List<AlertLog> readOnly = alertService.list(TENANT_ID, testProjectId, null, 1);
        assertTrue(readOnly.stream().allMatch(a -> a.getIsRead() == 1), "筛选 isRead=1 应只返回已读");
    }

    @Test
    @Transactional
    @DisplayName("TA9: markRead — 标记已读成功")
    void testMarkRead_Success() {
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(testProjectId);
        alert.setRuleType("TEST_MARK");
        alert.setSeverity("LOW");
        alert.setMessage("待标记告警");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);

        boolean result = alertService.markRead(TENANT_ID, alert.getId());
        assertTrue(result, "markRead 应返回 true");

        AlertLog updated = alertLogMapper.selectById(alert.getId());
        assertEquals(1, updated.getIsRead(), "标记后 isRead 应为 1");
    }

    @Test
    @Transactional
    @DisplayName("TA10: markRead — 跨租户标记读返回 false")
    void testMarkRead_CrossTenant() {
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(testProjectId);
        alert.setRuleType("TEST_XTNT");
        alert.setSeverity("LOW");
        alert.setMessage("跨租户告警");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);

        boolean result = alertService.markRead(888L, alert.getId());
        assertFalse(result, "跨租户标记应返回 false");

        AlertLog unchanged = alertLogMapper.selectById(alert.getId());
        assertEquals(0, unchanged.getIsRead(), "跨租户标记不应改变 isRead");
    }

    @Test
    @Transactional
    @DisplayName("TA11: markRead — 不存在的告警返回 false")
    void testMarkRead_NotFound() {
        boolean result = alertService.markRead(TENANT_ID, 999999L);
        assertFalse(result, "不存在的告警应返回 false");
    }

    // ═══════════════════════════════════════════════════════════════
    // batchEvaluate
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TA12: batchEvaluate — 批量评估项目")
    void testBatchEvaluate() {
        int count = alertService.batchEvaluate(TENANT_ID);
        // 至少不会抛异常
        assertTrue(count >= 0, "batchEvaluate 应返回 >=0 的告警数");
    }

    // ═══════════════════════════════════════════════════════════════
    // 边界条件
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TA13: evaluateProject — 非 ACTIVE 项目不出告警")
    void testEvaluateProject_ArchivedProject() {
        // 将项目标记为 ARCHIVED
        PmProject project = projectMapper.selectById(testProjectId);
        project.setStatus("ARCHIVED");
        projectMapper.updateById(project);

        // batchEvaluate 只对 ACTIVE 项目评估
        int count = alertService.batchEvaluate(TENANT_ID);
        // archived 项目不在评估范围内
        List<AlertLog> alerts = alertService.list(TENANT_ID, testProjectId, null, null);
        assertTrue(alerts.isEmpty(), "ARCHIVED 项目不应生成新告警");
    }

    @Test
    @Transactional
    @DisplayName("TA14: 告警去重 — 24小时内同规则未读告警不重复生成")
    void testAlertDeduplication() {
        // 插入一条 1 小时前未读的 CONTRACT_OVERDUE 告警
        AlertLog existing = new AlertLog();
        existing.setTenantId(TENANT_ID);
        existing.setProjectId(testProjectId);
        existing.setRuleType("CONTRACT_OVERDUE");
        existing.setSeverity("HIGH");
        existing.setMessage("已有告警");
        existing.setTriggeredAt(LocalDateTime.now().minusHours(1));
        existing.setIsRead(0);
        existing.setDeletedFlag(0);
        alertLogMapper.insert(existing);

        // 合同超期条件也满足
        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().minusDays(1));
        contractMapper.updateById(contract);

        // 评估项目
        alertService.evaluateProject(TENANT_ID, testProjectId);

        // 应只有 1 条 CONTRACT_OVERDUE（去重后不重复插入）
        List<AlertLog> overdueAlerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<AlertLog>()
                        .eq(AlertLog::getTenantId, TENANT_ID)
                        .eq(AlertLog::getProjectId, testProjectId)
                        .eq(AlertLog::getRuleType, "CONTRACT_OVERDUE"));
        assertEquals(1, overdueAlerts.size(), "24小时内去重应生效，同规则只保留1条");
    }
}
