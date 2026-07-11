package com.cgcpms.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.entity.AlertRuleConfig;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.mapper.AlertRuleConfigMapper;
import com.cgcpms.alert.dto.AlertProcessingReportVO;
import com.cgcpms.alert.service.AlertEvaluationService;
import com.cgcpms.alert.service.AlertSubscriptionService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.mapper.SysNotificationMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@DisplayName("AlertEvaluationService — 告警评估引擎测试")
class AlertEvaluationServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long USER_CREATOR = 2L;
    private static final long USER_PROJECT_MANAGER = 91001L;
    private static final long USER_PURCHASE_MANAGER = 91002L;
    private static final long USER_COMMERCIAL_MANAGER = 91003L;
    private static final long USER_PRODUCTION_MANAGER = 91004L;
    private static final long USER_CHIEF_ENGINEER = 91005L;

    @Autowired
    private AlertEvaluationService alertService;
    @Autowired
    private AlertSubscriptionService alertSubscriptionService;
    @Autowired
    private AlertLogMapper alertLogMapper;
    @Autowired
    private AlertRuleConfigMapper alertRuleConfigMapper;
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
    @Autowired
    private MatPurchaseOrderMapper purchaseOrderMapper;
    @Autowired
    private MatReceiptMapper receiptMapper;
    @Autowired
    private MatStockTxnMapper stockTxnMapper;
    @Autowired
    private SysNotificationMapper notificationMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                .in(AlertLog::getProjectId, List.of(testProjectId, 82001L, 82002L, 83001L, 83002L)));
        purchaseOrderMapper.delete(new LambdaQueryWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getTenantId, TENANT_ID)
                .in(MatPurchaseOrder::getProjectId, List.of(testProjectId, 82001L, 82002L, 83001L, 83002L))
                .likeRight(MatPurchaseOrder::getOrderCode, "ALERT-PO-"));
        stockTxnMapper.delete(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getTenantId, TENANT_ID)
                .eq(MatStockTxn::getSourceType, "MAT_RECEIPT")
                .eq(MatStockTxn::getWarehouseId, 91001L)
                .eq(MatStockTxn::getMaterialId, 91001L));
        receiptMapper.delete(new LambdaQueryWrapper<MatReceipt>()
                .eq(MatReceipt::getTenantId, TENANT_ID)
                .in(MatReceipt::getProjectId, List.of(testProjectId, 82001L, 82002L, 83001L, 83002L))
                .likeRight(MatReceipt::getReceiptCode, "ALERT-RC-"));
        notificationMapper.delete(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getTenantId, TENANT_ID)
                .in(SysNotification::getBizType, List.of("ALERT", "ALERT_STATUS")));
        jdbcTemplate.update("""
                delete from sys_user_preference
                where tenant_id = ? and user_id in (?, ?, ?, ?, ?, ?)
                """, TENANT_ID, USER_ADMIN, USER_CREATOR, USER_PROJECT_MANAGER, USER_PURCHASE_MANAGER,
                USER_COMMERCIAL_MANAGER, USER_CHIEF_ENGINEER);
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
        deleteRuleConfig("CONTRACT_EXPIRING");
        // 合同在 15 天后到期
        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().plusDays(15));
        contractMapper.updateById(contract);

        int count = alertService.evaluateProject(TENANT_ID, testProjectId);
        List<AlertLog> alerts = alertService.list(TENANT_ID, testProjectId, null, null);
        assertTrue(alerts.stream().anyMatch(a -> "CONTRACT_EXPIRING".equals(a.getRuleType())),
                "15天后到期应触发 CONTRACT_EXPIRING");
        assertTrue(alerts.stream()
                        .filter(a -> "CONTRACT_EXPIRING".equals(a.getRuleType()))
                        .allMatch(a -> "LOW".equals(a.getSeverity())),
                "未配置 severity_override 时应保留 CONTRACT_EXPIRING 默认 LOW 严重度");
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

    @Test
    @Transactional
    @DisplayName("TA4b: evaluateContractExpiring — window_days 配置扩大扫描窗口后触发")
    void testEvaluateContractExpiring_UsesConfiguredWindowDays() {
        deleteRuleConfig("CONTRACT_EXPIRING");
        deleteAlerts("CONTRACT_EXPIRING");
        insertRuleConfig("CONTRACT_EXPIRING", null, 60, null);

        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().plusDays(45));
        contractMapper.updateById(contract);

        alertService.evaluateProject(TENANT_ID, testProjectId);

        List<AlertLog> alerts = alertsByRuleType("CONTRACT_EXPIRING");
        assertEquals(1, alerts.size(), "配置 window_days=60 后，45天后到期合同应进入扫描窗口");
    }

    @Test
    @Transactional
    @DisplayName("TA4c: evaluateContractExpiring — severity_override 覆盖生成告警严重度")
    void testEvaluateContractExpiring_UsesSeverityOverride() {
        deleteRuleConfig("CONTRACT_EXPIRING");
        deleteAlerts("CONTRACT_EXPIRING");
        insertRuleConfig("CONTRACT_EXPIRING", null, 30, "HIGH");

        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().plusDays(15));
        contractMapper.updateById(contract);

        alertService.evaluateProject(TENANT_ID, testProjectId);

        List<AlertLog> alerts = alertsByRuleType("CONTRACT_EXPIRING");
        assertEquals(1, alerts.size());
        assertEquals("HIGH", alerts.get(0).getSeverity(), "severity_override=HIGH 应覆盖规则默认 LOW");
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

    @Test
    @Transactional
    @DisplayName("TA6b: evaluateDynamicCostExceedsTarget — threshold_ratio 配置改变触发边界")
    void testDynamicCostExceedsTarget_UsesConfiguredThresholdRatio() {
        deleteRuleConfig("DYNAMIC_COST_EXCEEDS_TARGET");
        deleteAlerts("DYNAMIC_COST_EXCEEDS_TARGET");
        costSummaryMapper.delete(new LambdaQueryWrapper<CostSummary>()
                .eq(CostSummary::getTenantId, TENANT_ID)
                .eq(CostSummary::getProjectId, testProjectId));

        CostSummary summary = new CostSummary();
        summary.setTenantId(TENANT_ID);
        summary.setProjectId(testProjectId);
        summary.setSummaryDate(LocalDate.now());
        summary.setTargetCost(new BigDecimal("100.00"));
        summary.setDynamicCost(new BigDecimal("105.00"));
        summary.setContractLockedCost(BigDecimal.ZERO);
        summary.setActualCost(BigDecimal.ZERO);
        summary.setPaidAmount(BigDecimal.ZERO);
        summary.setEstimatedRemainingCost(BigDecimal.ZERO);
        summary.setContractIncome(BigDecimal.ZERO);
        summary.setExpectedProfit(BigDecimal.ZERO);
        summary.setCostDeviation(new BigDecimal("5.00"));
        costSummaryMapper.insert(summary);

        insertRuleConfig("DYNAMIC_COST_EXCEEDS_TARGET", new BigDecimal("1.10"), null, null);
        alertService.evaluateProject(TENANT_ID, testProjectId);
        assertTrue(alertsByRuleType("DYNAMIC_COST_EXCEEDS_TARGET").isEmpty(),
                "threshold_ratio=1.10 时，105/100 不应触发");

        deleteRuleConfig("DYNAMIC_COST_EXCEEDS_TARGET");
        insertRuleConfig("DYNAMIC_COST_EXCEEDS_TARGET", new BigDecimal("1.04"), null, null);
        alertService.evaluateProject(TENANT_ID, testProjectId);

        List<AlertLog> alerts = alertsByRuleType("DYNAMIC_COST_EXCEEDS_TARGET");
        assertEquals(1, alerts.size(), "threshold_ratio=1.04 时，105/100 应触发");
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
    @DisplayName("TA8b: page — 后端分页并支持规则类型、分类、触发时间、已读、项目筛选")
    void testPage_FilterByRuleDomainTriggeredReadAndProject() {
        AlertLog hit = new AlertLog();
        hit.setTenantId(TENANT_ID);
        hit.setProjectId(testProjectId);
        hit.setRuleType("PURCHASE_DELIVERY_OVERDUE");
        hit.setAlertDomain("PURCHASE");
        hit.setSeverity("MEDIUM");
        hit.setMessage("命中过滤条件");
        hit.setTriggeredAt(LocalDateTime.now().minusHours(1));
        hit.setIsRead(0);
        hit.setDeletedFlag(0);
        alertLogMapper.insert(hit);

        AlertLog miss = new AlertLog();
        miss.setTenantId(TENANT_ID);
        miss.setProjectId(testProjectId);
        miss.setRuleType("CONTRACT_OVERDUE");
        miss.setAlertDomain("CONTRACT");
        miss.setSeverity("HIGH");
        miss.setMessage("不应命中过滤条件");
        miss.setTriggeredAt(LocalDateTime.now().minusDays(3));
        miss.setIsRead(1);
        miss.setDeletedFlag(0);
        alertLogMapper.insert(miss);

        var page = alertService.page(TENANT_ID, 1, 1, testProjectId,
                "PURCHASE_DELIVERY_OVERDUE", "PURCHASE", null, 0,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), null);

        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        AlertLog only = page.getRecords().get(0);
        assertEquals("PURCHASE_DELIVERY_OVERDUE", only.getRuleType());
        assertEquals("PURCHASE", only.getAlertDomain());
    }

    @Test
    @Transactional
    @DisplayName("TA8b-2: page — 支持 processStatus 筛选")
    void testPage_FilterByProcessStatus() {
        AlertLog processed = new AlertLog();
        processed.setTenantId(TENANT_ID);
        processed.setProjectId(testProjectId);
        processed.setRuleType("PROCESS_TEST");
        processed.setAlertDomain("CONTRACT");
        processed.setAlertCategory("CONTRACT_TERM");
        processed.setSeverity("LOW");
        processed.setMessage("已处理预警");
        processed.setTriggeredAt(LocalDateTime.now());
        processed.setIsRead(1);
        processed.setProcessStatus("PROCESSED");
        processed.setDeletedFlag(0);
        alertLogMapper.insert(processed);

        AlertLog open = new AlertLog();
        open.setTenantId(TENANT_ID);
        open.setProjectId(testProjectId);
        open.setRuleType("PROCESS_TEST_OPEN");
        open.setAlertDomain("CONTRACT");
        open.setAlertCategory("CONTRACT_TERM");
        open.setSeverity("LOW");
        open.setMessage("待处理预警");
        open.setTriggeredAt(LocalDateTime.now());
        open.setIsRead(0);
        open.setProcessStatus("OPEN");
        open.setDeletedFlag(0);
        alertLogMapper.insert(open);

        var page = alertService.page(TENANT_ID, 1, 10, testProjectId,
                null, null, null, null, null, null, "PROCESSED");

        assertEquals(1, page.getRecords().size());
        assertEquals("PROCESSED", page.getRecords().get(0).getProcessStatus());
    }

    @Test
    @Transactional
    @DisplayName("TA8b-3: processingReport — 预警总数、严重度、已读和处理状态与列表口径一致")
    void testProcessingReportAggregatesListFilters() {
        AlertLog highOpenUnread = reportAlert("HIGH", 0, "OPEN");
        alertLogMapper.insert(highOpenUnread);
        AlertLog highProcessedUnread = reportAlert("HIGH", 0, "PROCESSED");
        alertLogMapper.insert(highProcessedUnread);
        AlertLog mediumProcessedRead = reportAlert("MEDIUM", 1, "PROCESSED");
        alertLogMapper.insert(mediumProcessedRead);

        AlertLog missDomain = reportAlert("LOW", 0, "OPEN");
        missDomain.setAlertDomain("CONTRACT");
        alertLogMapper.insert(missDomain);

        var page = alertService.page(TENANT_ID, 1, 10, testProjectId,
                "REPORT_TEST", "PURCHASE", null, null, null, null, null);
        AlertProcessingReportVO report = alertService.processingReport(TENANT_ID, testProjectId,
                "REPORT_TEST", "PURCHASE", null, null, null, null, null);

        assertEquals(page.getTotal(), report.getTotalCount(), "报表总数应与同筛选条件列表一致");
        assertEquals(3, report.getTotalCount());
        assertEquals(2, report.getUnreadCount());
        assertEquals(1, report.getReadCount());
        assertEquals(2L, report.getSeverityCounts().get("HIGH"));
        assertEquals(1L, report.getSeverityCounts().get("MEDIUM"));
        assertFalse(report.getSeverityCounts().containsKey("LOW"), "不同域预警不应进入报表");
        assertEquals(1L, report.getProcessStatusCounts().get("OPEN"));
        assertEquals(2L, report.getProcessStatusCounts().get("PROCESSED"));
    }

    @Test
    @Transactional
    @DisplayName("TA8c: evaluatePurchaseDeliveryOverdue — 逾期未完成采购订单触发可跳转预警")
    void testEvaluatePurchaseDeliveryOverdue_TriggersWithSource() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(TENANT_ID);
        order.setProjectId(testProjectId);
        order.setOrderCode("ALERT-PO-OVERDUE-001");
        order.setOrderType("MATERIAL");
        order.setOrderDate(LocalDate.now().minusDays(10));
        order.setDeliveryDate(LocalDate.now().minusDays(2));
        order.setOrderStatus("APPROVED");
        order.setApprovalStatus("APPROVED");
        order.setTotalAmount(new BigDecimal("1200.00"));
        order.setDeletedFlag(0);
        purchaseOrderMapper.insert(order);

        int count = alertService.evaluateProject(TENANT_ID, testProjectId);

        assertTrue(count > 0);
        List<AlertLog> alerts = alertLogMapper.selectList(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, testProjectId)
                .eq(AlertLog::getRuleType, "PURCHASE_DELIVERY_OVERDUE"));
        assertEquals(1, alerts.size());
        AlertLog alert = alerts.get(0);
        assertEquals("PURCHASE", alert.getAlertDomain());
        assertEquals("PURCHASE_DELIVERY", alert.getAlertCategory());
        assertEquals("PURCHASE_ORDER", alert.getSourceType());
        assertEquals(order.getId(), alert.getSourceId());
        assertEquals("OPEN", alert.getProcessStatus());
    }

    @Test
    @Transactional
    @DisplayName("TA8c-2: evaluatePurchaseDeliveryOverdue — 已完成收货和入库的逾期采购订单不再触发")
    void testEvaluatePurchaseDeliveryOverdue_SkipsCompletedReceiptAndStockIn() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(TENANT_ID);
        order.setProjectId(testProjectId);
        order.setOrderCode("ALERT-PO-OVERDUE-DONE-001");
        order.setOrderType("MATERIAL");
        order.setOrderDate(LocalDate.now().minusDays(10));
        order.setDeliveryDate(LocalDate.now().minusDays(2));
        order.setOrderStatus("APPROVED");
        order.setApprovalStatus("APPROVED");
        order.setTotalAmount(new BigDecimal("1200.00"));
        order.setDeletedFlag(0);
        purchaseOrderMapper.insert(order);

        MatReceipt receipt = new MatReceipt();
        receipt.setTenantId(TENANT_ID);
        receipt.setProjectId(testProjectId);
        receipt.setOrderId(order.getId());
        receipt.setContractId(order.getContractId());
        receipt.setPartnerId(order.getPartnerId());
        receipt.setReceiptCode("ALERT-RC-DONE-001");
        receipt.setReceiptDate(LocalDate.now().minusDays(1));
        receipt.setWarehouseId(91001L);
        receipt.setTotalAmount(order.getTotalAmount());
        receipt.setApprovalStatus("APPROVED");
        receipt.setDeletedFlag(0);
        receiptMapper.insert(receipt);

        MatStockTxn stockIn = new MatStockTxn();
        stockIn.setTenantId(TENANT_ID);
        stockIn.setWarehouseId(91001L);
        stockIn.setMaterialId(91001L);
        stockIn.setTxnType("IN");
        stockIn.setQuantity(BigDecimal.ONE);
        stockIn.setAvailableAfter(BigDecimal.ONE);
        stockIn.setSourceType("MAT_RECEIPT");
        stockIn.setSourceId(receipt.getId());
        stockTxnMapper.insert(stockIn);

        alertService.evaluateProject(TENANT_ID, testProjectId);

        Long alertCount = alertLogMapper.selectCount(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, testProjectId)
                .eq(AlertLog::getRuleType, "PURCHASE_DELIVERY_OVERDUE")
                .eq(AlertLog::getSourceId, order.getId()));
        assertEquals(0L, alertCount, "已完成收货和入库的采购订单不应继续生成逾期预警");
    }

    @Test
    @Transactional
    @DisplayName("TA8d: page — alertDomain 为空的旧合同预警按规则类型归入合同类")
    void testPage_LegacyNullDomainMatchesContractDomain() {
        AlertLog legacy = new AlertLog();
        legacy.setTenantId(TENANT_ID);
        legacy.setProjectId(testProjectId);
        legacy.setRuleType("CONTRACT_OVERDUE");
        legacy.setSeverity("HIGH");
        legacy.setMessage("旧合同类告警");
        legacy.setTriggeredAt(LocalDateTime.now());
        legacy.setIsRead(0);
        legacy.setDeletedFlag(0);
        alertLogMapper.insert(legacy);

        var page = alertService.page(TENANT_ID, 1, 10, testProjectId,
                null, "CONTRACT", null, null, null, null, null);

        assertTrue(page.getRecords().stream()
                .anyMatch(a -> "CONTRACT_OVERDUE".equals(a.getRuleType())));
    }

    @Test
    @Transactional
    @DisplayName("TA8e: evaluateProject — 新预警按成员订阅派发，仅向有效站内信接收人发送")
    void testEvaluateProject_DispatchesToSubscribedMembersOnly() {
        seedMember(testProjectId, USER_PROJECT_MANAGER, "PROJECT_MANAGER");
        seedMember(testProjectId, USER_COMMERCIAL_MANAGER, "COMMERCIAL_MANAGER");
        seedMember(testProjectId, USER_PURCHASE_MANAGER, "PURCHASE_MANAGER");
        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().minusDays(1));
        contractMapper.updateById(contract);

        TestUserContext.setUser(TENANT_ID, USER_PROJECT_MANAGER, "pm", List.of("PROJECT_MANAGER"));
        alertSubscriptionService.updateCurrentUserSubscription(TENANT_ID, USER_PROJECT_MANAGER, List.of("PROJECT_MANAGER"),
                java.util.Map.of("enabled", false));
        TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);

        alertService.evaluateProject(TENANT_ID, testProjectId);

        AlertLog alert = alertLogMapper.selectOne(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, testProjectId)
                .eq(AlertLog::getRuleType, "CONTRACT_OVERDUE"));
        assertNotNull(alert);

        List<SysNotification> notifications = notificationMapper.selectList(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getTenantId, TENANT_ID)
                .eq(SysNotification::getBizType, "ALERT")
                .eq(SysNotification::getBizId, alert.getId()));
        Set<Long> recipients = notifications.stream().map(SysNotification::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(USER_ADMIN, USER_COMMERCIAL_MANAGER), recipients,
                "合同域新预警应只通知具备该域权限且订阅开启的成员");

        Integer channels = jdbcTemplate.queryForObject("""
                select count(*) from alert_notification_send_record
                where tenant_id = ? and alert_id = ?
                """, Integer.class, TENANT_ID, alert.getId());
        assertEquals(2, channels, "默认订阅仅发送 IN_APP，且仅为有效接收人落记录");

        Integer sent = jdbcTemplate.queryForObject("""
                select count(*) from alert_notification_send_record
                where tenant_id = ? and alert_id = ? and channel = 'IN_APP' and send_status = 'SENT'
                """, Integer.class, TENANT_ID, alert.getId());
        assertEquals(2, sent);
    }

    @Test
    @Transactional
    @DisplayName("TA8f: notification — 外部渠道通过适配接口占位，不直接复用站内信服务")
    void testAlertNotification_ExternalChannelsHaveAdapters() {
        assertDoesNotThrow(() -> Class.forName("com.cgcpms.alert.notification.AlertNotificationSender"));
        assertDoesNotThrow(() -> Class.forName("com.cgcpms.alert.notification.InAppAlertNotificationSender"));
        assertDoesNotThrow(() -> Class.forName("com.cgcpms.alert.notification.EmailAlertNotificationSender"));
        assertDoesNotThrow(() -> Class.forName("com.cgcpms.alert.notification.WechatAlertNotificationSender"));
        assertDoesNotThrow(() -> Class.forName("com.cgcpms.alert.notification.SmsAlertNotificationSender"));
        assertDoesNotThrow(() -> Class.forName("com.cgcpms.alert.notification.AlertNotificationChannelProperties"));
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

    @Test
    @Transactional
    @DisplayName("TA11b: updateStatus — 可将预警标记为已处理与已归档")
    void testUpdateStatus_Success() {
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(testProjectId);
        alert.setRuleType("TEST_STATUS");
        alert.setAlertDomain("CONTRACT");
        alert.setAlertCategory("CONTRACT_TERM");
        alert.setSeverity("LOW");
        alert.setMessage("状态测试");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);

        assertTrue(alertService.updateStatus(TENANT_ID, alert.getId(), "PROCESSED", "已处理"));
        AlertLog processed = alertLogMapper.selectById(alert.getId());
        assertEquals("PROCESSED", processed.getProcessStatus());
        assertNotNull(processed.getProcessedAt());

        assertTrue(alertService.updateStatus(TENANT_ID, alert.getId(), "ARCHIVED", "归档"));
        AlertLog archived = alertLogMapper.selectById(alert.getId());
        assertEquals("ARCHIVED", archived.getProcessStatus());
        assertNotNull(archived.getArchivedAt());
    }

    @Test
    @Transactional
    @DisplayName("TA11b-2: updateStatus — 暴露 handled 与 biz 兼容语义")
    void testUpdateStatus_ExposesHandledAndBizAliases() {
        seedMember(testProjectId, USER_PROJECT_MANAGER, "PROJECT_MANAGER");
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(testProjectId);
        alert.setRuleType("PURCHASE_DELIVERY_OVERDUE");
        alert.setAlertDomain("PURCHASE");
        alert.setAlertCategory("PURCHASE_DELIVERY");
        alert.setSourceType("PURCHASE_ORDER");
        alert.setSourceId(91002001L);
        alert.setSeverity("MEDIUM");
        alert.setMessage("状态兼容字段测试");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);

        TestUserContext.setUser(TENANT_ID, USER_PROJECT_MANAGER, "pm", List.of("PROJECT_MANAGER"));
        assertTrue(alertService.updateStatus(TENANT_ID, alert.getId(), "PROCESSED", "已处理"));

        AlertLog processed = alertLogMapper.selectById(alert.getId());
        assertEquals("PURCHASE_ORDER", processed.getBizType());
        assertEquals(91002001L, processed.getBizId());
        assertEquals("PURCHASE_ORDER", processed.getBusinessType());
        assertEquals(91002001L, processed.getBusinessId());
        assertEquals("PROCESSED", processed.getHandledStatus());
        assertEquals(USER_PROJECT_MANAGER, processed.getHandledBy());
        assertNotNull(processed.getHandledAt());
    }

    @Test
    @Transactional
    @DisplayName("TA11c: updateStatus — 状态流转严格遵守订阅偏好和域角色边界")
    void testUpdateStatus_DispatchesStatusNotificationBySubscription() {
        seedMember(testProjectId, USER_ADMIN, "PM");
        seedMember(testProjectId, USER_PROJECT_MANAGER, "PROJECT_MANAGER");
        seedMember(testProjectId, USER_COMMERCIAL_MANAGER, "COMMERCIAL_MANAGER");
        seedMember(testProjectId, USER_PURCHASE_MANAGER, "PURCHASE_MANAGER");

        TestUserContext.setUser(TENANT_ID, USER_COMMERCIAL_MANAGER, "commercial", List.of("COMMERCIAL_MANAGER"));
        alertSubscriptionService.updateCurrentUserSubscription(TENANT_ID, USER_COMMERCIAL_MANAGER,
                List.of("COMMERCIAL_MANAGER"), java.util.Map.of("notifyOnStatusChanged", false));
        TestUserContext.setUser(TENANT_ID, USER_PROJECT_MANAGER, "pm", List.of("PROJECT_MANAGER"));
        alertSubscriptionService.updateCurrentUserSubscription(TENANT_ID, USER_PROJECT_MANAGER,
                List.of("PROJECT_MANAGER"), java.util.Map.of("minSeverity", "HIGH"));
        TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);

        AlertLog processedAlert = insertStatusAlert("TEST_STATUS_NOTIFY_PROCESSED");
        AlertLog archivedAlert = insertStatusAlert("TEST_STATUS_NOTIFY_ARCHIVED");
        AlertLog invalidAlert = insertStatusAlert("TEST_STATUS_NOTIFY_INVALID");

        assertTrue(alertService.updateStatus(TENANT_ID, processedAlert.getId(), "PROCESSED", "已处理"));
        assertTrue(alertService.updateStatus(TENANT_ID, archivedAlert.getId(), "ARCHIVED", "已归档"));
        assertTrue(alertService.updateStatus(TENANT_ID, invalidAlert.getId(), "INVALID", "已失效"));

        List<Long> alertIds = List.of(processedAlert.getId(), archivedAlert.getId(), invalidAlert.getId());
        List<SysNotification> notifications = notificationMapper.selectList(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getTenantId, TENANT_ID)
                .eq(SysNotification::getBizType, "ALERT_STATUS")
                .in(SysNotification::getBizId, alertIds));
        Set<Long> recipients = notifications.stream().map(SysNotification::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(USER_ADMIN), recipients,
                "状态通知不能越过 notifyOnStatusChanged、minSeverity 或角色域边界");
        Set<String> titles = notifications.stream().map(SysNotification::getTitle)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("预警已处理", "预警已归档", "预警已失效"), titles);

        Integer sent = jdbcTemplate.queryForObject("""
                select count(*) from alert_notification_send_record
                where tenant_id = ? and alert_id in (?, ?, ?) and channel = 'IN_APP'
                  and event_type = 'STATUS_CHANGED' and send_status = 'SENT'
                """, Integer.class, TENANT_ID, processedAlert.getId(), archivedAlert.getId(), invalidAlert.getId());
        assertEquals(3, sent);
    }

    private AlertLog reportAlert(String severity, int isRead, String processStatus) {
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(testProjectId);
        alert.setRuleType("REPORT_TEST");
        alert.setAlertDomain("PURCHASE");
        alert.setAlertCategory("PURCHASE_DELIVERY");
        alert.setSeverity(severity);
        alert.setMessage("预警报表测试");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(isRead);
        alert.setProcessStatus(processStatus);
        alert.setDeletedFlag(0);
        return alert;
    }

    private void insertRuleConfig(String ruleType, BigDecimal thresholdRatio, Integer windowDays, String severityOverride) {
        insertRuleConfig(ruleType, thresholdRatio, windowDays, severityOverride, 1);
    }

    private void insertRuleConfig(String ruleType, BigDecimal thresholdRatio, Integer windowDays,
                                  String severityOverride, Integer dedupHours) {
        AlertRuleConfig config = new AlertRuleConfig();
        config.setTenantId(TENANT_ID);
        config.setRuleType(ruleType);
        config.setAlertDomain(alertDomain(ruleType));
        config.setAlertCategory(alertCategory(ruleType));
        config.setEnabled(1);
        config.setDedupHours(dedupHours);
        config.setThresholdRatio(thresholdRatio);
        config.setWindowDays(windowDays);
        config.setSeverityOverride(severityOverride);
        config.setDeletedFlag(0);
        alertRuleConfigMapper.insert(config);
    }

    private String alertDomain(String ruleType) {
        return switch (ruleType) {
            case "DYNAMIC_COST_EXCEEDS_TARGET" -> "COST";
            case "CONTRACT_OVERDUE" -> "CONTRACT";
            case "CONTRACT_EXPIRING" -> "CONTRACT";
            case "PURCHASE_DELIVERY_OVERDUE" -> "PURCHASE";
            default -> "OTHER";
        };
    }

    private String alertCategory(String ruleType) {
        return switch (ruleType) {
            case "DYNAMIC_COST_EXCEEDS_TARGET" -> "COST_DYNAMIC";
            case "CONTRACT_OVERDUE" -> "CONTRACT_TERM";
            case "CONTRACT_EXPIRING" -> "CONTRACT_TERM";
            case "PURCHASE_DELIVERY_OVERDUE" -> "PURCHASE_DELIVERY";
            default -> "OTHER";
        };
    }

    private void deleteRuleConfig(String ruleType) {
        jdbcTemplate.update("""
                delete from alert_rule_config
                where tenant_id = ? and rule_type = ?
                """, TENANT_ID, ruleType);
    }

    private void deleteAlerts(String ruleType) {
        alertLogMapper.delete(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, testProjectId)
                .eq(AlertLog::getRuleType, ruleType));
    }

    private List<AlertLog> alertsByRuleType(String ruleType) {
        return alertLogMapper.selectList(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, testProjectId)
                .eq(AlertLog::getRuleType, ruleType));
    }

    private AlertLog insertDedupAlert(Long projectId, String ruleType, String alertDomain, String alertCategory,
                                      String dedupKey, LocalDateTime triggeredAt) {
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(projectId);
        alert.setRuleType(ruleType);
        alert.setAlertDomain(alertDomain);
        alert.setAlertCategory(alertCategory);
        alert.setDedupKey(dedupKey);
        alert.setSeverity("HIGH");
        alert.setMessage("去重测试已有告警");
        alert.setTriggeredAt(triggeredAt);
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);
        return alert;
    }

    private MatPurchaseOrder overduePurchaseOrder(String orderCode, LocalDate deliveryDate) {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(TENANT_ID);
        order.setProjectId(testProjectId);
        order.setOrderCode(orderCode);
        order.setOrderType("MATERIAL");
        order.setOrderDate(deliveryDate.minusDays(7));
        order.setDeliveryDate(deliveryDate);
        order.setOrderStatus("APPROVED");
        order.setApprovalStatus("APPROVED");
        order.setTotalAmount(new BigDecimal("1200.00"));
        order.setDeletedFlag(0);
        return order;
    }

    @Test
    @Transactional
    @DisplayName("TA11i: subscription — 默认订阅按角色与可见域计算")
    void testSubscription_DefaultsByRole() {
        TestUserContext.setUser(TENANT_ID, USER_PURCHASE_MANAGER, "purchase", List.of("PURCHASE_MANAGER"));

        var result = alertSubscriptionService.getCurrentUserSubscription(TENANT_ID, USER_PURCHASE_MANAGER,
                List.of("PURCHASE_MANAGER"));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> effective = (java.util.Map<String, Object>) result.get("effectiveSubscription");
        assertEquals(true, effective.get("enabled"));
        assertEquals(List.of("IN_APP"), effective.get("channels"));
        assertEquals(List.of("PURCHASE"), effective.get("domains"));
        assertEquals("LOW", effective.get("minSeverity"));
        assertEquals(true, effective.get("notifyOnStatusChanged"));
    }

    @Test
    @Transactional
    @DisplayName("TA11j: subscription — 用户覆盖只能缩小不能放大，保存时统一收敛")
    void testSubscription_OverridesAreClamped() {
        TestUserContext.setUser(TENANT_ID, USER_COMMERCIAL_MANAGER, "commercial", List.of("COMMERCIAL_MANAGER"));

        alertSubscriptionService.updateCurrentUserSubscription(TENANT_ID, USER_COMMERCIAL_MANAGER,
                List.of("COMMERCIAL_MANAGER"), java.util.Map.of(
                        "enabled", true,
                        "channels", List.of("IN_APP", "EMAIL"),
                        "domains", List.of("CONTRACT", "PURCHASE"),
                        "minSeverity", "LOW",
                        "notifyOnStatusChanged", true
                ));

        var result = alertSubscriptionService.getCurrentUserSubscription(TENANT_ID, USER_COMMERCIAL_MANAGER,
                List.of("COMMERCIAL_MANAGER"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> effective = (java.util.Map<String, Object>) result.get("effectiveSubscription");
        assertEquals(List.of("IN_APP"), effective.get("channels"));
        assertEquals(List.of("CONTRACT"), effective.get("domains"));
        assertEquals("LOW", effective.get("minSeverity"));
        assertEquals(true, effective.get("notifyOnStatusChanged"));

        alertSubscriptionService.updateCurrentUserSubscription(TENANT_ID, USER_COMMERCIAL_MANAGER,
                List.of("COMMERCIAL_MANAGER"), java.util.Map.of(
                        "enabled", false,
                        "channels", List.of("EMAIL"),
                        "domains", List.of("PAYMENT"),
                        "minSeverity", "HIGH",
                        "notifyOnStatusChanged", false
                ));

        result = alertSubscriptionService.getCurrentUserSubscription(TENANT_ID, USER_COMMERCIAL_MANAGER,
                List.of("COMMERCIAL_MANAGER"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> narrowed = (java.util.Map<String, Object>) result.get("effectiveSubscription");
        assertEquals(false, narrowed.get("enabled"));
        assertEquals(List.of(), narrowed.get("channels"));
        assertEquals(List.of("PAYMENT"), narrowed.get("domains"));
        assertEquals("HIGH", narrowed.get("minSeverity"));
        assertEquals(false, narrowed.get("notifyOnStatusChanged"));
    }

    @Test
    @Transactional
    @DisplayName("TA11k: subscription — fail-close 角色默认关闭且不能被用户打开")
    void testSubscription_FailCloseCannotEnable() {
        TestUserContext.setUser(TENANT_ID, USER_CHIEF_ENGINEER, "chief", List.of("CHIEF_ENGINEER"));

        alertSubscriptionService.updateCurrentUserSubscription(TENANT_ID, USER_CHIEF_ENGINEER,
                List.of("CHIEF_ENGINEER"), java.util.Map.of(
                        "enabled", true,
                        "channels", List.of("IN_APP"),
                        "domains", List.of("CONTRACT"),
                        "minSeverity", "LOW",
                        "notifyOnStatusChanged", true
                ));

        var result = alertSubscriptionService.getCurrentUserSubscription(TENANT_ID, USER_CHIEF_ENGINEER,
                List.of("CHIEF_ENGINEER"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> effective = (java.util.Map<String, Object>) result.get("effectiveSubscription");
        assertEquals(false, effective.get("enabled"));
        assertEquals(List.of(), effective.get("domains"));
        assertEquals(List.of("IN_APP"), effective.get("channels"));
        assertEquals("HIGH", effective.get("minSeverity"));
        assertEquals(false, effective.get("notifyOnStatusChanged"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 权限隔离
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TA11d: PURCHASE_MANAGER — 只能看到采购域，不能看到同项目合同域")
    void testAccess_PurchaseManagerOnlyPurchaseDomain() {
        seedMember(testProjectId, USER_PURCHASE_MANAGER, "PURCHASE_MANAGER");
        insertAlert(testProjectId, "PURCHASE", "PURCHASE_DELIVERY_OVERDUE");
        insertAlert(testProjectId, "CONTRACT", "CONTRACT_OVERDUE");
        TestUserContext.setUser(TENANT_ID, USER_PURCHASE_MANAGER, "purchase", List.of("PURCHASE_MANAGER"));

        var page = alertService.page(TENANT_ID, 1, 10, testProjectId,
                null, null, null, null, null, null, null);

        assertEquals(1, page.getRecords().size());
        assertEquals("PURCHASE", page.getRecords().get(0).getAlertDomain());

        var contractPage = alertService.page(TENANT_ID, 1, 10, testProjectId,
                null, "CONTRACT", null, null, null, null, null);
        assertEquals(0, contractPage.getTotal(), "指定未授权域应返回空页，不放大范围");
    }

    @Test
    @Transactional
    @DisplayName("TA11e: COMMERCIAL_MANAGER — 只能看到合同、付款、变更域")
    void testAccess_CommercialManagerDomains() {
        seedMember(testProjectId, USER_COMMERCIAL_MANAGER, "COMMERCIAL_MANAGER");
        insertAlert(testProjectId, "CONTRACT", "CONTRACT_OVERDUE");
        insertAlert(testProjectId, "PAYMENT", "PAYMENT_EXCEEDS_RATIO");
        insertAlert(testProjectId, "VARIATION", "VARIATION_UNCONFIRMED");
        insertAlert(testProjectId, "PURCHASE", "PURCHASE_DELIVERY_OVERDUE");
        TestUserContext.setUser(TENANT_ID, USER_COMMERCIAL_MANAGER, "commercial", List.of("COMMERCIAL_MANAGER"));

        var page = alertService.page(TENANT_ID, 1, 10, testProjectId,
                null, null, null, null, null, null, null);

        assertEquals(3, page.getRecords().size());
        Set<String> domains = page.getRecords().stream().map(AlertLog::getAlertDomain).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("CONTRACT", "PAYMENT", "VARIATION"), domains);
    }

    @Test
    @Transactional
    @DisplayName("TA11f: PROJECT_MANAGER — 可见本人项目全域，但不能看无权限项目")
    void testAccess_ProjectManagerOwnProjectsOnly() {
        Long otherProjectId = 82001L;
        seedProject(otherProjectId, "ALERT-OTHER-001", USER_CREATOR);
        seedMember(testProjectId, USER_PROJECT_MANAGER, "PROJECT_MANAGER");
        insertAlert(testProjectId, "COST", "DYNAMIC_COST_EXCEEDS_TARGET");
        insertAlert(testProjectId, "CONTRACT", "CONTRACT_OVERDUE");
        insertAlert(otherProjectId, "PURCHASE", "PURCHASE_DELIVERY_OVERDUE");
        TestUserContext.setUser(TENANT_ID, USER_PROJECT_MANAGER, "pm", List.of("PROJECT_MANAGER"));

        var page = alertService.page(TENANT_ID, 1, 10, null,
                null, null, null, null, null, null, null);

        assertEquals(2, page.getRecords().size());
        assertTrue(page.getRecords().stream().allMatch(a -> testProjectId.equals(a.getProjectId())));
        assertThrows(BusinessException.class, () -> alertService.page(TENANT_ID, 1, 10, otherProjectId,
                null, null, null, null, null, null, null));
    }

    @Test
    @Transactional
    @DisplayName("TA11f2: PROJECT_MANAGER — 无权限 projectId 与越权域并存时仍优先拒绝项目")
    void testAccess_ProjectManagerUnauthorizedProjectBeatsUnauthorizedDomain() {
        Long otherProjectId = 82002L;
        seedProject(otherProjectId, "ALERT-OTHER-002", USER_CREATOR);
        seedMember(testProjectId, USER_PROJECT_MANAGER, "PROJECT_MANAGER");
        TestUserContext.setUser(TENANT_ID, USER_PROJECT_MANAGER, "pm", List.of("PROJECT_MANAGER"));

        assertThrows(BusinessException.class, () -> alertService.page(TENANT_ID, 1, 10, otherProjectId,
                null, "OTHER", null, null, null, null, null));
    }

    @Test
    @Transactional
    @DisplayName("TA11f3: PROJECT_MANAGER — projectId=0 不能绕过 FINANCE 域校验")
    void testAccess_ProjectManagerCannotUseTenantLevelProjectToReadFinanceDomain() {
        seedMember(testProjectId, USER_PROJECT_MANAGER, "PROJECT_MANAGER");
        insertAlert(0L, "FINANCE", "CASH_JOURNAL_ARCHIVE_OVERDUE");
        TestUserContext.setUser(TENANT_ID, USER_PROJECT_MANAGER, "pm", List.of("PROJECT_MANAGER"));

        var page = alertService.page(TENANT_ID, 1, 10, 0L,
                null, null, null, null, null, null, null);
        var financePage = alertService.page(TENANT_ID, 1, 10, 0L,
                null, "FINANCE", null, null, null, null, null);

        assertEquals(0, page.getTotal());
        assertEquals(0, financePage.getTotal());
    }

    @Test
    @Transactional
    @DisplayName("TA11g: PRODUCTION_MANAGER / CHIEF_ENGINEER — 预警中心 fail-close")
    void testAccess_FailCloseRoles() {
        seedMember(testProjectId, USER_PRODUCTION_MANAGER, "PRODUCTION_MANAGER");
        seedMember(testProjectId, USER_CHIEF_ENGINEER, "CHIEF_ENGINEER");
        insertAlert(testProjectId, "COST", "DYNAMIC_COST_EXCEEDS_TARGET");

        TestUserContext.setUser(TENANT_ID, USER_PRODUCTION_MANAGER, "production", List.of("PRODUCTION_MANAGER"));
        var productionPage = alertService.page(TENANT_ID, 1, 10, null,
                null, null, null, null, null, null, null);
        assertEquals(0, productionPage.getTotal());

        TestUserContext.setUser(TENANT_ID, USER_CHIEF_ENGINEER, "chief", List.of("CHIEF_ENGINEER"));
        var chiefPage = alertService.page(TENANT_ID, 1, 10, null,
                null, null, null, null, null, null, null);
        assertEquals(0, chiefPage.getTotal());
    }

    @Test
    @Transactional
    @DisplayName("TA11h: markRead / updateStatus — 按项目与域双重校验越权失败")
    void testAccess_MarkReadAndUpdateStatusDenied() {
        seedMember(testProjectId, USER_PURCHASE_MANAGER, "PURCHASE_MANAGER");
        AlertLog contractAlert = insertAlert(testProjectId, "CONTRACT", "CONTRACT_OVERDUE");
        TestUserContext.setUser(TENANT_ID, USER_PURCHASE_MANAGER, "purchase", List.of("PURCHASE_MANAGER"));

        assertThrows(BusinessException.class, () -> alertService.markRead(TENANT_ID, contractAlert.getId()));
        assertThrows(BusinessException.class, () -> alertService.updateStatus(TENANT_ID, contractAlert.getId(), "PROCESSED", "done"));

        AlertLog unchanged = alertLogMapper.selectById(contractAlert.getId());
        assertEquals(0, unchanged.getIsRead());
        assertEquals("OPEN", unchanged.getProcessStatus());
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

    @Test
    @Transactional
    @DisplayName("TA12b: batchEvaluate — 非管理员只评估可访问项目集合")
    void testBatchEvaluate_NonAdminOnlyAccessibleProjects() {
        Long ownProjectId = 83001L;
        Long otherProjectId = 83002L;
        seedProject(ownProjectId, "ALERT-BATCH-OWN", USER_CREATOR);
        seedProject(otherProjectId, "ALERT-BATCH-OTHER", USER_CREATOR);
        seedMember(ownProjectId, USER_PROJECT_MANAGER, "PROJECT_MANAGER");
        seedOverdueContract(83001L, ownProjectId, "A-CT-BATCH-OWN");
        seedOverdueContract(83002L, otherProjectId, "A-CT-BATCH-OTHER");
        TestUserContext.setUser(TENANT_ID, USER_PROJECT_MANAGER, "pm", List.of("PROJECT_MANAGER"));

        alertService.batchEvaluate(TENANT_ID);

        Long ownAlerts = alertLogMapper.selectCount(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, ownProjectId)
                .eq(AlertLog::getRuleType, "CONTRACT_OVERDUE"));
        Long otherAlerts = alertLogMapper.selectCount(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, otherProjectId)
                .eq(AlertLog::getRuleType, "CONTRACT_OVERDUE"));

        assertTrue(ownAlerts > 0, "本人可访问项目仍应被评估");
        assertEquals(0L, otherAlerts, "非管理员批量评估不能越界到无权限项目");
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
    @DisplayName("TA14: 告警去重 — 24小时内同 dedupKey 的活跃告警不重复生成")
    void testAlertDeduplication() {
        // 插入一条 1 小时前已读但仍未归档的 CONTRACT_OVERDUE 告警
        AlertLog existing = new AlertLog();
        existing.setTenantId(TENANT_ID);
        existing.setProjectId(testProjectId);
        existing.setRuleType("CONTRACT_OVERDUE");
        existing.setAlertDomain("CONTRACT");
        existing.setAlertCategory("CONTRACT_TERM");
        existing.setDedupKey("P:" + testProjectId + ":R:CONTRACT_OVERDUE");
        existing.setSeverity("HIGH");
        existing.setMessage("已有告警");
        existing.setTriggeredAt(LocalDateTime.now().minusHours(1));
        existing.setIsRead(1);
        existing.setProcessStatus("OPEN");
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

    @Test
    @Transactional
    @DisplayName("TA14b: 规则治理 — dedup_hours 窗口内重复评估不生成第二条有效告警")
    void testRuleGovernance_DedupHoursSuppressesWithinConfiguredWindow() {
        deleteRuleConfig("CONTRACT_OVERDUE");
        deleteAlerts("CONTRACT_OVERDUE");
        insertRuleConfig("CONTRACT_OVERDUE", null, null, null, 2);

        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().minusDays(1));
        contractMapper.updateById(contract);

        alertService.evaluateProject(TENANT_ID, testProjectId);
        alertService.evaluateProject(TENANT_ID, testProjectId);

        List<AlertLog> alerts = alertsByRuleType("CONTRACT_OVERDUE");
        assertEquals(1, alerts.size(), "dedup_hours=2 窗口内重复评估不应新增第二条有效告警");
        assertEquals("P:" + testProjectId + ":R:CONTRACT_OVERDUE", alerts.get(0).getDedupKey());
    }

    @Test
    @Transactional
    @DisplayName("TA14c: 规则治理 — 缩小 dedup_hours 后窗口外旧告警允许重新生成")
    void testRuleGovernance_DedupHoursAllowsAfterWindowShrinks() {
        deleteRuleConfig("CONTRACT_OVERDUE");
        deleteAlerts("CONTRACT_OVERDUE");
        insertRuleConfig("CONTRACT_OVERDUE", null, null, null, 1);
        insertDedupAlert(testProjectId, "CONTRACT_OVERDUE", "CONTRACT", "CONTRACT_TERM",
                "P:" + testProjectId + ":R:CONTRACT_OVERDUE", LocalDateTime.now().minusHours(2));

        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().minusDays(1));
        contractMapper.updateById(contract);

        alertService.evaluateProject(TENANT_ID, testProjectId);

        assertEquals(2, alertsByRuleType("CONTRACT_OVERDUE").size(),
                "旧告警在默认24小时内但已超出配置 dedup_hours=1 时，应允许重新生成");
    }

    @Test
    @Transactional
    @DisplayName("TA14d: 规则治理 — 去重键不串并不同规则、业务键和项目边界")
    void testRuleGovernance_DedupKeySeparatesRuleSourceAndProjectBoundaries() {
        deleteRuleConfig("CONTRACT_OVERDUE");
        deleteRuleConfig("CONTRACT_EXPIRING");
        deleteRuleConfig("PURCHASE_DELIVERY_OVERDUE");
        deleteAlerts("CONTRACT_OVERDUE");
        deleteAlerts("CONTRACT_EXPIRING");
        deleteAlerts("PURCHASE_DELIVERY_OVERDUE");
        insertRuleConfig("CONTRACT_OVERDUE", null, null, null, 24);
        insertRuleConfig("CONTRACT_EXPIRING", null, 30, null, 24);
        insertRuleConfig("PURCHASE_DELIVERY_OVERDUE", null, null, null, 24);

        insertDedupAlert(testProjectId, "CONTRACT_OVERDUE", "CONTRACT", "CONTRACT_TERM",
                "P:" + testProjectId + ":R:CONTRACT_OVERDUE", LocalDateTime.now().minusMinutes(30));
        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().plusDays(15));
        contractMapper.updateById(contract);
        alertService.evaluateProject(TENANT_ID, testProjectId);
        assertEquals(1, alertsByRuleType("CONTRACT_EXPIRING").size(),
                "同项目不同规则类型不能被 CONTRACT_OVERDUE 的去重键抑制");

        MatPurchaseOrder firstOrder = overduePurchaseOrder("ALERT-PO-DEDUP-001", LocalDate.now().minusDays(3));
        purchaseOrderMapper.insert(firstOrder);
        MatPurchaseOrder secondOrder = overduePurchaseOrder("ALERT-PO-DEDUP-002", LocalDate.now().minusDays(2));
        purchaseOrderMapper.insert(secondOrder);
        insertDedupAlert(testProjectId, "PURCHASE_DELIVERY_OVERDUE", "PURCHASE", "PURCHASE_DELIVERY",
                "S:PURCHASE_ORDER:" + firstOrder.getId() + ":R:PURCHASE_DELIVERY_OVERDUE",
                LocalDateTime.now().minusMinutes(30));
        alertService.evaluateProject(TENANT_ID, testProjectId);
        Long secondOrderAlerts = alertLogMapper.selectCount(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, testProjectId)
                .eq(AlertLog::getRuleType, "PURCHASE_DELIVERY_OVERDUE")
                .eq(AlertLog::getSourceId, secondOrder.getId()));
        assertEquals(1L, secondOrderAlerts, "同规则不同采购订单 sourceId 不能被错误串并");

        Long otherProjectId = 84001L;
        seedProject(otherProjectId, "ALERT-DEDUP-OTHER", USER_CREATOR);
        seedOverdueContract(84001L, otherProjectId, "A-CT-DEDUP-OTHER");
        alertService.evaluateProject(TENANT_ID, otherProjectId);
        Long otherProjectAlerts = alertLogMapper.selectCount(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, otherProjectId)
                .eq(AlertLog::getRuleType, "CONTRACT_OVERDUE"));
        assertEquals(1L, otherProjectAlerts, "同规则不同项目不能被其他项目的 project dedupKey 抑制");
    }

    @Test
    @Transactional
    @DisplayName("TA15: 规则配置 — CONTRACT_EXPIRING 的 windowDays 可覆盖默认 30 天")
    void testRuleConfig_WindowDaysOverride() {
        AlertRuleConfig config = alertRuleConfigMapper.selectOne(new LambdaQueryWrapper<AlertRuleConfig>()
                .eq(AlertRuleConfig::getTenantId, TENANT_ID)
                .eq(AlertRuleConfig::getRuleType, "CONTRACT_EXPIRING"));
        assertNotNull(config);
        config.setWindowDays(10);
        alertRuleConfigMapper.updateById(config);

        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().plusDays(15));
        contractMapper.updateById(contract);

        alertService.evaluateProject(TENANT_ID, testProjectId);
        List<AlertLog> alerts = alertLogMapper.selectList(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, testProjectId)
                .eq(AlertLog::getRuleType, "CONTRACT_EXPIRING"));
        assertTrue(alerts.isEmpty(), "windowDays=10 时，15天后的合同不应触发到期预警");
    }

    @Test
    @Transactional
    @DisplayName("TA15b: 规则治理 — enabled=0 的规则即使命中条件也不生成告警")
    void testRuleGovernance_DisabledRuleDoesNotTrigger() {
        AlertRuleConfig config = alertRuleConfigMapper.selectOne(new LambdaQueryWrapper<AlertRuleConfig>()
                .eq(AlertRuleConfig::getTenantId, TENANT_ID)
                .eq(AlertRuleConfig::getRuleType, "CONTRACT_OVERDUE"));
        assertNotNull(config);
        config.setEnabled(0);
        alertRuleConfigMapper.updateById(config);

        CtContract contract = contractMapper.selectById(testContractId);
        contract.setEndDate(LocalDate.now().minusDays(1));
        contractMapper.updateById(contract);

        alertService.evaluateProject(TENANT_ID, testProjectId);

        Long disabledRuleAlerts = alertLogMapper.selectCount(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getProjectId, testProjectId)
                .eq(AlertLog::getRuleType, "CONTRACT_OVERDUE"));
        assertEquals(0L, disabledRuleAlerts, "规则关闭后不能生成 CONTRACT_OVERDUE 预警");
    }

    private void seedProject(Long projectId, String projectCode, Long createdBy) {
        if (projectMapper.selectById(projectId) != null) {
            return;
        }
        PmProject project = new PmProject();
        project.setId(projectId);
        project.setProjectCode(projectCode);
        project.setProjectName(projectCode);
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("1000000.00"));
        project.setTargetCost(new BigDecimal("800000.00"));
        project.setStatus("ACTIVE");
        project.setApprovalStatus("APPROVED");
        project.setTenantId(TENANT_ID);
        project.setCreatedBy(createdBy);
        projectMapper.insert(project);
    }

    private void seedMember(Long projectId, Long userId, String roleCode) {
        Long existing = projectMemberMapper.selectCount(new LambdaQueryWrapper<PmProjectMember>()
                .eq(PmProjectMember::getTenantId, TENANT_ID)
                .eq(PmProjectMember::getProjectId, projectId)
                .eq(PmProjectMember::getUserId, userId)
                .eq(PmProjectMember::getStatus, "ACTIVE"));
        if (existing != null && existing > 0) {
            return;
        }
        PmProjectMember member = new PmProjectMember();
        member.setTenantId(TENANT_ID);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setRoleCode(roleCode);
        member.setStatus("ACTIVE");
        projectMemberMapper.insert(member);
    }

    private AlertLog insertAlert(Long projectId, String alertDomain, String ruleType) {
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(projectId);
        alert.setRuleType(ruleType);
        alert.setAlertDomain(alertDomain);
        alert.setSeverity("LOW");
        alert.setMessage("权限隔离测试预警");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);
        return alert;
    }

    private AlertLog insertStatusAlert(String ruleType) {
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(testProjectId);
        alert.setRuleType(ruleType);
        alert.setAlertDomain("CONTRACT");
        alert.setAlertCategory("CONTRACT_TERM");
        alert.setSeverity("LOW");
        alert.setMessage("状态通知测试");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setCreatedBy(USER_CREATOR);
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);
        return alert;
    }

    private void seedOverdueContract(Long contractId, Long projectId, String contractCode) {
        CtContract contract = new CtContract();
        contract.setId(contractId);
        contract.setProjectId(projectId);
        contract.setContractCode(contractCode);
        contract.setContractName(contractCode);
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
        contract.setEndDate(LocalDate.now().minusDays(1));
        contract.setTenantId(TENANT_ID);
        contractMapper.insert(contract);
    }
}
