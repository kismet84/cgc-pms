package com.cgcpms.cost.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.strategy.CostSubjectResolver;
import com.cgcpms.financeclose.service.FinancialCloseService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowQueryService;
import com.cgcpms.workflow.vo.WfInstanceVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cgcpms_m89_cost_workflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=300000",
        "spring.flyway.locations=classpath:db/migration-h2,filesystem:src/main/resources/db/migration-h2-legacy,classpath:com/cgcpms/common/migration,classpath:com/cgcpms/common/migration/**/*.class"
})
@ActiveProfiles("local")
@Transactional
class CostSubjectV2WorkflowIntegrationTest {

    private static final long PROJECT_A = 994_890_000_001L;
    private static final long PROJECT_B = 994_890_000_002L;
    private static final long ACCOUNTANT_USER = 994_890_000_003L;
    private static final long FINANCE_USER = 994_890_000_004L;
    private static final long PROJECT_MEMBER_A = 994_890_000_005L;
    private static final long PROJECT_MEMBER_B = 994_890_000_006L;
    private static final long SOURCE_SUBJECT = 994_890_000_007L;
    private static final long TARGET_SUBJECT = 994_890_000_008L;
    private static final long MAPPING_VERSION = 994_890_000_009L;
    private static final long MAPPING_ITEM = 994_890_000_010L;
    private static final long BID = 994_890_000_011L;
    private static final long TARGET = 994_890_000_012L;
    private static final long BID_COST_ITEM = 994_890_000_013L;
    private static final long ACCOUNTING_ENTRY = 994_890_000_014L;
    private static final long ACCOUNTING_LINE = 994_890_000_015L;
    private static final long PARTIAL_APPROVAL = 994_890_000_016L;
    private static final long PARTIAL_BATCH = 994_890_000_017L;
    private static final long PARTIAL_LINE = 994_890_000_018L;
    private static final long PARTIAL_COST_ITEM = 994_890_000_019L;
    private static final long ACCOUNTANT_ROLE_LINK = 994_890_000_020L;
    private static final long FINANCE_ROLE_LINK = 994_890_000_021L;
    private static final long SUPER_ADMIN_ROLE_LINK = 994_890_000_022L;
    private static final long FINANCE_RULE = 994_890_000_023L;
    private static final long BID_RULE = 994_890_000_024L;
    private static final long FINANCE_AUTHOR_USER = 994_890_000_025L;
    private static final long FINANCE_AUTHOR_ROLE_LINK = 994_890_000_026L;
    private static final long DISABLED_OVERHEAD_RULE = 994_890_000_027L;

    @Autowired
    private CostSubjectV2Service service;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WorkflowQueryService workflowQueryService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CostSubjectResolver costSubjectResolver;

    @Autowired
    private FinancialCloseService financialCloseService;

    @BeforeEach
    void setUp() {
        seedUsersAndAccess();
        seedCostFacts();
        asFinanceAuthor();
        assertEquals(1, count("""
                SELECT COUNT(*) FROM wf_template_node n
                JOIN wf_template t ON t.id=n.template_id AND t.tenant_id=n.tenant_id
                WHERE t.tenant_id=0 AND t.enabled=1 AND t.deleted_flag=0 AND n.deleted_flag=0
                  AND t.business_type='BID_COST_TARGET_TRANSFER'
                """));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM wf_template_node n
                JOIN wf_template t ON t.id=n.template_id AND t.tenant_id=n.tenant_id
                WHERE t.tenant_id=0 AND t.enabled=1 AND t.deleted_flag=0 AND n.deleted_flag=0
                  AND t.business_type='FINANCE_COST_ALLOCATION'
                """));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void costGovernanceWritesRequireLiveCompanyFinanceRole() {
        int before = count("SELECT COUNT(*) FROM bid_cost_target_transfer_request WHERE tenant_id=0");
        asUser(ACCOUNTANT_USER, "ADMIN", "cost:subject:bid-transfer");
        BusinessException adminOnly = assertThrows(BusinessException.class,
                () -> service.createBidTransferRequest(new CostSubjectV2Service.BidTransferRequestCommand(
                        BID, PROJECT_A, TARGET, MAPPING_VERSION, "m96-admin-bypass", "管理员不得绕过公司财务")));
        assertEquals("COST_COMPANY_FINANCE_REQUIRED", adminOnly.getCode());
        assertEquals(before, count("SELECT COUNT(*) FROM bid_cost_target_transfer_request WHERE tenant_id=0"));

        asFinanceAuthor();
        jdbc.update("DELETE FROM sys_user_role WHERE id=?", FINANCE_AUTHOR_ROLE_LINK);
        BusinessException staleAuthority = assertThrows(BusinessException.class,
                () -> service.createBidTransferRequest(new CostSubjectV2Service.BidTransferRequestCommand(
                        BID, PROJECT_A, TARGET, MAPPING_VERSION, "m96-stale-authority", "撤销角色后旧权限不得继续写")));
        assertEquals("COST_COMPANY_FINANCE_REQUIRED", staleAuthority.getCode());
        assertEquals(before, count("SELECT COUNT(*) FROM bid_cost_target_transfer_request WHERE tenant_id=0"));
    }

    @Test
    void closedProjectRejectsNormalCostGovernanceButKeepsDraftCancellationAvailable() {
        Map<String, Object> bidDraft = service.createBidTransferRequest(
                new CostSubjectV2Service.BidTransferRequestCommand(
                        BID, PROJECT_A, TARGET, MAPPING_VERSION, "m96-close-bid", "关闭前投标转入草稿"));
        Map<String, Object> financeDraft = service.createFinanceAllocationRequest(
                new CostSubjectV2Service.FinanceAllocationCommand(
                        "ACCOUNTING_ENTRY_LINE", ACCOUNTING_LINE, "DIRECT_PROJECT", "2026-08",
                        TARGET_SUBJECT, null, "m96-close-finance", "关闭前财务分摊草稿",
                        List.of(new CostSubjectV2Service.AllocationLine(PROJECT_A, BigDecimal.ONE))));
        Map<String, Object> configDraft = service.createProjectConfig(
                new CostSubjectV2Service.ProjectConfigCommand(PROJECT_A, "关闭前项目配置草稿",
                        List.of(new CostSubjectV2Service.ProjectConfigLine(
                                TARGET_SUBJECT, false, LocalDate.now(), null))));
        jdbc.update("UPDATE pm_project SET status='CLOSED' WHERE tenant_id=0 AND id=?", PROJECT_A);

        assertEquals("COST_GOVERNANCE_PROJECT_CLOSED", assertThrows(BusinessException.class,
                () -> service.submitBidTransferRequest(number(bidDraft.get("id")))).getCode());
        assertEquals("COST_GOVERNANCE_PROJECT_CLOSED", assertThrows(BusinessException.class,
                () -> service.submitFinanceAllocationRequest(number(financeDraft.get("id")))).getCode());
        assertEquals("COST_GOVERNANCE_PROJECT_CLOSED", assertThrows(BusinessException.class,
                () -> service.submitProjectConfig(number(configDraft.get("id")))).getCode());

        assertEquals("CANCELLED", service.cancelBidTransferRequest(number(bidDraft.get("id"))).get("status"));
        assertEquals("CANCELLED", service.cancelFinanceAllocationRequest(number(financeDraft.get("id"))).get("status"));
        assertEquals("CANCELLED", service.cancelProjectConfig(number(configDraft.get("id"))).get("status"));
    }

    @Test
    void projectConfigApprovalDetailExposesImpactInsteadOfBlindApproval() {
        Map<String, Object> draft = service.createProjectConfig(
                new CostSubjectV2Service.ProjectConfigCommand(PROJECT_A, "审批前查看项目影响",
                        List.of(new CostSubjectV2Service.ProjectConfigLine(
                                TARGET_SUBJECT, false, LocalDate.now(), null))));
        Map<String, Object> submitted = service.submitProjectConfig(number(draft.get("id")));

        WfInstanceVO detail = workflowQueryService.getInstanceDetail(
                0L, number(submitted.get("approvalInstanceId")), FINANCE_AUTHOR_USER);

        assertNotNull(detail);
        assertNotNull(detail.getBusinessDetails());
        assertEquals("M89-COST-A", detail.getBusinessDetails().get("projectCode"));
        List<?> lines = (List<?>) detail.getBusinessDetails().get("lines");
        assertEquals(1, lines.size());
        Map<?, ?> line = (Map<?, ?>) lines.getFirst();
        assertEquals("M89-COST-TARGET", line.get("subjectCode"));
        assertNotNull(line.get("impactSnapshot"));
    }

    @Test
    void closedAccountingPeriodRejectsFinanceAllocationBeforeDraftCreation() {
        long periodId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("""
                INSERT INTO finance_period
                (id,tenant_id,period_code,fiscal_year,fiscal_month,start_date,end_date,status,created_by)
                VALUES (?,0,'2026-08',2026,8,DATE '2026-08-01',DATE '2026-08-31','CLOSED',?)
                """, periodId, FINANCE_USER);
        int before = count("SELECT COUNT(*) FROM finance_cost_allocation_request WHERE tenant_id=0");

        BusinessException closed = assertThrows(BusinessException.class,
                () -> service.createFinanceAllocationRequest(
                        new CostSubjectV2Service.FinanceAllocationCommand(
                                "ACCOUNTING_ENTRY_LINE", ACCOUNTING_LINE, "DIRECT_PROJECT", "2026-08",
                                TARGET_SUBJECT, null, "m96-closed-period", "已结账期间不得分摊",
                                List.of(new CostSubjectV2Service.AllocationLine(PROJECT_A, BigDecimal.ONE)))));

        assertEquals("FINANCE_PERIOD_CLOSED", closed.getCode());
        assertEquals(before, count("SELECT COUNT(*) FROM finance_cost_allocation_request WHERE tenant_id=0"));
    }

    @Test
    void closedAccountingPeriodBlocksHistoryRecalculationUntilReopened() {
        long periodId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        LocalDate today = LocalDate.now();
        jdbc.update("""
                INSERT INTO finance_period
                (id,tenant_id,period_code,fiscal_year,fiscal_month,start_date,end_date,status,created_by)
                VALUES (?,0,?,?,?,? ,?,'CLOSED',?)
                """, periodId, "HIST-" + today.getYear() + today.getMonthValue(), today.getYear(), today.getMonthValue(),
                today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), FINANCE_USER);
        Map<String, Object> draft = service.createRecalculation(new CostSubjectV2Service.RecalculationCommand(
                PROJECT_A, MAPPING_VERSION, LocalDateTime.now().plusDays(1),
                "HISTORY_RECALCULATION", "已结账期间历史重算", "m96-history-period-guard"));
        long batchId = number(draft.get("id"));

        BusinessException closed = assertThrows(BusinessException.class,
                () -> service.submitRecalculation(batchId));
        assertEquals("FINANCE_PERIOD_CLOSED", closed.getCode());
        assertEquals("DRAFT", service.recalculationBatch(batchId).get("status"));
        assertEquals(0, count("SELECT COUNT(*) FROM wf_instance WHERE tenant_id=0 AND business_id=" + batchId));

        jdbc.update("UPDATE finance_period SET status='OPEN' WHERE id=?", periodId);
        Map<String, Object> submitted = service.submitRecalculation(batchId);
        assertNotNull(submitted.get("approvalInstanceId"));
    }

    @Test
    void submittedCostGovernanceBlocksPeriodCloseChecksUntilWithdrawn() {
        LocalDate today = LocalDate.now();
        financialCloseService.ensurePeriod(today.getYear(), today.getMonthValue());
        Map<String, Object> recalcDraft = service.createRecalculation(
                new CostSubjectV2Service.RecalculationCommand(
                        PROJECT_A, MAPPING_VERSION, LocalDateTime.now().plusDays(1),
                        "HISTORY_RECALCULATION", "月结前审批中的历史重算", "m96-close-check-recalc"));
        Map<String, Object> recalcSubmitted = service.submitRecalculation(number(recalcDraft.get("id")));
        Map<String, Object> financeDraft = service.createFinanceAllocationRequest(
                new CostSubjectV2Service.FinanceAllocationCommand(
                        "ACCOUNTING_ENTRY_LINE", ACCOUNTING_LINE, "DIRECT_PROJECT", today.toString().substring(0, 7),
                        TARGET_SUBJECT, null, "m96-close-check-finance", "月结前审批中的财务分摊",
                        List.of(new CostSubjectV2Service.AllocationLine(PROJECT_A, BigDecimal.ONE))));
        Map<String, Object> financeSubmitted = service.submitFinanceAllocationRequest(number(financeDraft.get("id")));

        Map<String, Object> blocked = financialCloseService.runChecks(today.getYear(), today.getMonthValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockedChecks = (List<Map<String, Object>>) blocked.get("checks");
        assertEquals(1, checkIssueCount(blockedChecks, "SUBMITTED_COST_RECALCULATION"));
        assertEquals(1, checkIssueCount(blockedChecks, "SUBMITTED_FINANCE_COST_ALLOCATION"));

        workflowEngine.withdraw(number(recalcSubmitted.get("approvalInstanceId")),
                FINANCE_AUTHOR_USER, "m89-user-" + FINANCE_AUTHOR_USER);
        workflowEngine.withdraw(number(financeSubmitted.get("approvalInstanceId")),
                FINANCE_AUTHOR_USER, "m89-user-" + FINANCE_AUTHOR_USER);
        Map<String, Object> released = financialCloseService.runChecks(today.getYear(), today.getMonthValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> releasedChecks = (List<Map<String, Object>>) released.get("checks");
        assertEquals(0, checkIssueCount(releasedChecks, "SUBMITTED_COST_RECALCULATION"));
        assertEquals(0, checkIssueCount(releasedChecks, "SUBMITTED_FINANCE_COST_ALLOCATION"));
    }

    @Test
    void closedAccountingPeriodBlocksPostCloseAdjustmentUntilReopened() {
        long periodId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        LocalDate today = LocalDate.now();
        jdbc.update("UPDATE pm_project SET status='CLOSED' WHERE tenant_id=0 AND id=?", PROJECT_A);
        jdbc.update("""
                INSERT INTO finance_period
                (id,tenant_id,period_code,fiscal_year,fiscal_month,start_date,end_date,status,created_by)
                VALUES (?,0,?,?,?,? ,?,'CLOSED',?)
                """, periodId, "POST-" + today.getYear() + today.getMonthValue(), today.getYear(), today.getMonthValue(),
                today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), FINANCE_USER);
        Map<String, Object> draft = service.createRecalculation(new CostSubjectV2Service.RecalculationCommand(
                PROJECT_A, MAPPING_VERSION, LocalDateTime.now().plusDays(1),
                "POST_CLOSE_ADJUSTMENT", "已结账期间关闭后调整", "m96-post-close-period-guard"));
        long batchId = number(draft.get("id"));

        BusinessException closed = assertThrows(BusinessException.class,
                () -> service.submitRecalculation(batchId));
        assertEquals("FINANCE_PERIOD_CLOSED", closed.getCode());
        assertEquals("DRAFT", service.recalculationBatch(batchId).get("status"));

        jdbc.update("UPDATE finance_period SET status='OPEN' WHERE id=?", periodId);
        Map<String, Object> submitted = service.submitRecalculation(batchId);
        assertNotNull(submitted.get("approvalInstanceId"));
    }

    @Test
    void financeOverrideResolvesPersistedUnclassifiedCaseAndFreezesResolverDecision() {
        long sourceId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long caseId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("""
                INSERT INTO cost_unclassified_case
                (id,tenant_id,project_id,source_type,source_id,source_item_id,business_category,
                 original_cost_subject_id,error_code,error_message,status)
                VALUES (?,0,?,'MAT_RECEIPT',?,0,'DIRECT_CONSUMPTION',?,
                        'COST_SUBJECT_UNCLASSIFIED','未命中成本规则','OPEN')
                """, caseId, PROJECT_A, sourceId, SOURCE_SUBJECT);

        Map<String, Object> options = service.governanceFormOptions();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pending = (List<Map<String, Object>>) options.get("pendingClassifications");
        assertTrue(pending.stream().anyMatch(row -> number(row.get("caseId")) == caseId));

        long overrideId = service.overrideClassification(new CostSubjectV2Service.ClassificationOverrideCommand(
                caseId, null, TARGET_SUBJECT, "公司财务复核后覆盖到启用末级成本科目"));

        assertEquals("RESOLVED", jdbc.queryForObject(
                "SELECT status FROM cost_unclassified_case WHERE id=?", String.class, caseId));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM cost_classification_override WHERE id=?", String.class, overrideId));
        CostSubjectResolver.Decision decision = costSubjectResolver.resolveForFact(
                0L, PROJECT_A, "MAT_RECEIPT", "DIRECT_CONSUMPTION", sourceId, 0L, SOURCE_SUBJECT,
                LocalDate.now());
        assertEquals(TARGET_SUBJECT, decision.costSubjectId());
        assertEquals(overrideId, decision.overrideId());

        long repeatedId = service.overrideClassification(new CostSubjectV2Service.ClassificationOverrideCommand(
                caseId, null, TARGET_SUBJECT, "公司财务复核后覆盖到启用末级成本科目"));
        assertEquals(overrideId, repeatedId);

        long correctedId = service.overrideClassification(new CostSubjectV2Service.ClassificationOverrideCommand(
                caseId, null, SOURCE_SUBJECT, "更正首次选择，保留原覆盖审计轨迹"));
        assertFalse(overrideId == correctedId);
        assertEquals("RETIRED", jdbc.queryForObject(
                "SELECT status FROM cost_classification_override WHERE id=?", String.class, overrideId));
        assertNull(jdbc.queryForObject(
                "SELECT matched_cost_subject_id FROM cost_classification_override WHERE id=?",
                Long.class, correctedId), "二次更正必须继承首次覆盖前的原始自动匹配溯源");
        CostSubjectResolver.Decision corrected = costSubjectResolver.resolveForFact(
                0L, PROJECT_A, "MAT_RECEIPT", "DIRECT_CONSUMPTION", sourceId, 0L, SOURCE_SUBJECT,
                LocalDate.now());
        assertEquals(SOURCE_SUBJECT, corrected.costSubjectId());
        assertEquals(correctedId, corrected.overrideId());
    }

    @Test
    void financeOverrideRejectsDisabledOverheadSubjectAndKeepsCaseOpen() {
        long sourceId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long caseId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("""
                INSERT INTO overhead_allocation_rule
                (id,tenant_id,cost_subject_id,allocation_basis,allocation_cycle,status,deleted_flag)
                VALUES (?,0,?,'CONTRACT_AMOUNT','MONTHLY','DISABLE',0)
                """, DISABLED_OVERHEAD_RULE, TARGET_SUBJECT);
        jdbc.update("""
                INSERT INTO cost_unclassified_case
                (id,tenant_id,project_id,source_type,source_id,source_item_id,business_category,
                 original_cost_subject_id,error_code,error_message,status)
                VALUES (?,0,?,'MAT_RECEIPT',?,0,'DIRECT_CONSUMPTION',?,
                        'COST_SUBJECT_UNCLASSIFIED','未命中成本规则','OPEN')
                """, caseId, PROJECT_A, sourceId, SOURCE_SUBJECT);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subjects = (List<Map<String, Object>>) service.governanceFormOptions()
                .get("costSubjects");
        Map<String, Object> disabledTarget = subjects.stream()
                .filter(row -> number(row.get("id")) == TARGET_SUBJECT)
                .findFirst().orElseThrow();
        assertEquals("DISABLE", disabledTarget.get("overheadRuleStatus"));

        BusinessException rejected = assertThrows(BusinessException.class,
                () -> service.overrideClassification(new CostSubjectV2Service.ClassificationOverrideCommand(
                        caseId, null, TARGET_SUBJECT, "停用间接费规则不得继续接收新成本")));

        assertEquals("OVERHEAD_RULE_DISABLED_FOR_COST", rejected.getCode());
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT status FROM cost_unclassified_case WHERE id=?", String.class, caseId));
        assertEquals(0, count("SELECT COUNT(*) FROM cost_classification_override WHERE tenant_id=0 AND source_id=?",
                sourceId));
    }

    @Test
    void bidCostTransferDraftRunsTwoNodeWorkflowAndPostsFrozenFacts() {
        Map<String, Object> draft = service.createBidTransferRequest(
                new CostSubjectV2Service.BidTransferRequestCommand(
                        BID, PROJECT_A, TARGET, MAPPING_VERSION,
                        "m89-bid-transfer-workflow", "计划89投标成本移交"));
        long requestId = number(draft.get("id"));
        assertEquals("DRAFT", draft.get("status"));
        assertNull(draft.get("approvalInstanceId"));
        assertNull(draft.get("finalTransferId"));
        assertMoney("1000.00", draft.get("totalAmount"));
        assertMoney("1000.00", jdbc.queryForObject("""
                SELECT amount FROM bid_cost_target_transfer_request_line
                WHERE tenant_id=0 AND request_id=?
                """, BigDecimal.class, requestId));
        Map<String, Object> listRow = service.bidTransferRequests().stream()
                .filter(row -> number(row.get("id")) == requestId).findFirst().orElseThrow();
        assertEquals("M89-BID-WON", listRow.get("bidCode"));
        assertEquals("M89-COST-A", listRow.get("projectCode"));
        assertEquals("计划89成本项目A", listRow.get("projectName"));
        assertEquals("M89-TARGET-V1", listRow.get("targetVersionNo"));
        assertEquals("计划89目标成本", listRow.get("targetVersionName"));

        Map<String, Object> submitted = service.submitBidTransferRequest(requestId);
        long instanceId = number(submitted.get("approvalInstanceId"));
        assertEquals("SUBMITTED", submitted.get("status"));
        assertSubmittedWorkflow(instanceId, WorkflowBusinessTypes.BID_COST_TARGET_TRANSFER,
                requestId, PROJECT_A, "1000.00");

        approveTwoNodeWorkflow(instanceId, () -> {
            assertEquals("SUBMITTED", service.bidTransferRequest(requestId).get("status"));
            assertEquals(0, count("SELECT COUNT(*) FROM bid_cost_target_transfer WHERE tenant_id=0 AND bid_cost_id=?", BID));
            assertMoney("0.00", jdbc.queryForObject(
                    "SELECT total_target_amount FROM cost_target WHERE id=?", BigDecimal.class, TARGET));
        });

        Map<String, Object> posted = service.bidTransferRequest(requestId);
        long transferId = number(posted.get("finalTransferId"));
        assertEquals("POSTED", posted.get("status"));
        assertEquals(instanceId, number(posted.get("approvalInstanceId")));

        Map<String, Object> transfer = jdbc.queryForMap("""
                SELECT bid_cost_id,project_id,target_id,mapping_version_id,total_amount,status,approval_instance_id
                FROM bid_cost_target_transfer WHERE tenant_id=0 AND id=?
                """, transferId);
        assertEquals(BID, number(transfer.get("bid_cost_id")));
        assertEquals(PROJECT_A, number(transfer.get("project_id")));
        assertEquals(TARGET, number(transfer.get("target_id")));
        assertEquals(MAPPING_VERSION, number(transfer.get("mapping_version_id")));
        assertEquals("POSTED", transfer.get("status"));
        assertEquals(instanceId, number(transfer.get("approval_instance_id")));
        assertMoney("1000.00", transfer.get("total_amount"));
        assertMoney("1000.00", jdbc.queryForObject(
                "SELECT amount FROM bid_cost_target_transfer_line WHERE tenant_id=0 AND transfer_id=?",
                BigDecimal.class, transferId));
        assertMoney("1000.00", jdbc.queryForObject(
                "SELECT amount FROM bid_cost_target_transfer_request_line WHERE tenant_id=0 AND request_id=?",
                BigDecimal.class, requestId));
        assertMoney("1000.00", jdbc.queryForObject(
                "SELECT total_target_amount FROM cost_target WHERE id=?", BigDecimal.class, TARGET));
        assertMoney("1000.00", jdbc.queryForObject("""
                SELECT target_amount FROM cost_target_item
                WHERE tenant_id=0 AND target_id=? AND cost_subject_id=? AND deleted_flag=0
                """, BigDecimal.class, TARGET, TARGET_SUBJECT));
        assertEquals("BID_COST", jdbc.queryForObject(
                "SELECT source_type FROM cost_item WHERE id=?", String.class, BID_COST_ITEM));
        assertMoney("1000.00", jdbc.queryForObject(
                "SELECT amount_without_tax FROM cost_item WHERE id=?", BigDecimal.class, BID_COST_ITEM));

        Map<String, Object> reversalDraft = service.createReversal(
                new CostSubjectV2Service.ReversalCommand("BID_TRANSFER", transferId, "误建冲销草稿"));
        assertEquals("CANCELLED", service.cancelReversal(number(reversalDraft.get("id"))).get("status"));
        Map<String, Object> replacement = service.createReversal(
                new CostSubjectV2Service.ReversalCommand("BID_TRANSFER", transferId, "取消后重新发起"));
        assertEquals("DRAFT", replacement.get("status"));
        assertEquals("CANCELLED", service.cancelReversal(number(replacement.get("id"))).get("status"));
    }

    @Test
    void rejectsCompetingActiveBidTransferRequest() {
        service.createBidTransferRequest(new CostSubjectV2Service.BidTransferRequestCommand(
                BID, PROJECT_A, TARGET, MAPPING_VERSION, "m89-bid-transfer-active-1", "首个活动申请"));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.createBidTransferRequest(new CostSubjectV2Service.BidTransferRequestCommand(
                        BID, PROJECT_A, TARGET, MAPPING_VERSION, "m89-bid-transfer-active-2", "重复活动申请")));

        assertEquals("BID_COST_TRANSFER_REQUEST_ACTIVE", duplicate.getCode());
        assertEquals(1, count("SELECT COUNT(*) FROM bid_cost_target_transfer_request "
                + "WHERE tenant_id=0 AND bid_cost_id=? AND target_id=? AND status IN ('DRAFT','SUBMITTED')",
                BID, TARGET));
    }

    @Test
    void financeAllocationDraftFreezesRemainingAmountAfterLegalPartialAllocation() {
        seedPartialFinanceAllocation();

        Map<String, Object> draft = service.createFinanceAllocationRequest(
                new CostSubjectV2Service.FinanceAllocationCommand(
                        "ACCOUNTING_ENTRY_LINE", ACCOUNTING_LINE, "BENEFIT_AMOUNT", "2026-08",
                        TARGET_SUBJECT, null, "m89-finance-allocation-workflow", "冻结剩余待分摊金额",
                        List.of(
                                new CostSubjectV2Service.AllocationLine(PROJECT_A, BigDecimal.ONE),
                                new CostSubjectV2Service.AllocationLine(PROJECT_B, new BigDecimal("3")))));
        long requestId = number(draft.get("id"));
        assertEquals("DRAFT", draft.get("status"));
        assertNull(draft.get("approvalInstanceId"));
        assertNull(draft.get("finalBatchId"));
        assertMoney("700.00", draft.get("sourceAmount"));
        assertFinanceRequestLines(requestId);
        Map<String, Object> listRow = service.financeAllocationRequests().stream()
                .filter(row -> number(row.get("id")) == requestId).findFirst().orElseThrow();
        assertEquals("M89-COST-A", listRow.get("projectCode"));
        assertEquals("计划89成本项目A", listRow.get("projectName"));
        assertEquals("M89-FIN-ENTRY", listRow.get("sourceCode"));
        assertEquals("M89-COST-TARGET", listRow.get("costSubjectCode"));
        assertEquals("计划89成本目标科目", listRow.get("costSubjectName"));

        Map<String, Object> submitted = service.submitFinanceAllocationRequest(requestId);
        long instanceId = number(submitted.get("approvalInstanceId"));
        assertEquals("SUBMITTED", submitted.get("status"));
        assertSubmittedWorkflow(instanceId, WorkflowBusinessTypes.FINANCE_COST_ALLOCATION,
                requestId, PROJECT_A, "700.00");

        approveTwoNodeWorkflow(instanceId, () -> {
            assertEquals("SUBMITTED", service.financeAllocationRequest(requestId).get("status"));
            assertEquals(1, count("""
                    SELECT COUNT(*) FROM finance_cost_allocation_batch
                    WHERE tenant_id=0 AND source_type='ACCOUNTING_ENTRY_LINE' AND source_id=?
                    """, ACCOUNTING_LINE));
            assertFinanceRequestLines(requestId);
        });

        Map<String, Object> posted = service.financeAllocationRequest(requestId);
        long finalBatchId = number(posted.get("finalBatchId"));
        assertEquals("POSTED", posted.get("status"));
        assertEquals(instanceId, number(posted.get("approvalInstanceId")));
        assertMoney("700.00", jdbc.queryForObject(
                "SELECT source_amount FROM finance_cost_allocation_batch WHERE id=?",
                BigDecimal.class, finalBatchId));
        assertEquals(instanceId, number(jdbc.queryForObject(
                "SELECT approval_instance_id FROM finance_cost_allocation_batch WHERE id=?",
                Long.class, finalBatchId)));
        assertEquals(2, count(
                "SELECT COUNT(*) FROM finance_cost_allocation_line WHERE tenant_id=0 AND batch_id=?", finalBatchId));
        assertMoney("700.00", jdbc.queryForObject(
                "SELECT SUM(allocated_amount) FROM finance_cost_allocation_line WHERE tenant_id=0 AND batch_id=?",
                BigDecimal.class, finalBatchId));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM cost_item
                WHERE tenant_id=0 AND source_type='FINANCE_COST_ALLOCATION' AND source_id=? AND deleted_flag=0
                """, finalBatchId));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM cost_item
                WHERE tenant_id=0 AND source_type='FINANCE_COST_ALLOCATION' AND source_id=?
                  AND cost_date=DATE '2026-08-31' AND deleted_flag=0
                """, finalBatchId));
        assertMoney("700.00", jdbc.queryForObject("""
                SELECT SUM(amount_without_tax) FROM cost_item
                WHERE tenant_id=0 AND source_type='FINANCE_COST_ALLOCATION' AND source_id=? AND deleted_flag=0
                """, BigDecimal.class, finalBatchId));
        assertMoney("1000.00", jdbc.queryForObject("""
                SELECT SUM(source_amount) FROM finance_cost_allocation_batch
                WHERE tenant_id=0 AND source_type='ACCOUNTING_ENTRY_LINE' AND source_id=?
                """, BigDecimal.class, ACCOUNTING_LINE));

        assertFinanceRequestLines(requestId);
        assertMoney("300.00", jdbc.queryForObject(
                "SELECT source_amount FROM finance_cost_allocation_batch WHERE id=?",
                BigDecimal.class, PARTIAL_BATCH));
        assertEquals(PARTIAL_APPROVAL, number(jdbc.queryForObject(
                "SELECT approval_instance_id FROM finance_cost_allocation_batch WHERE id=?",
                Long.class, PARTIAL_BATCH)));
        assertMoney("300.00", jdbc.queryForObject(
                "SELECT allocated_amount FROM finance_cost_allocation_line WHERE id=?",
                BigDecimal.class, PARTIAL_LINE));
        assertMoney("300.00", jdbc.queryForObject(
                "SELECT amount_without_tax FROM cost_item WHERE id=?",
                BigDecimal.class, PARTIAL_COST_ITEM));
        assertMoney("1000.00", jdbc.queryForObject(
                "SELECT amount FROM accounting_entry_line WHERE id=?",
                BigDecimal.class, ACCOUNTING_LINE));

        Map<String, Object> reversalDraft = service.createReversal(
                new CostSubjectV2Service.ReversalCommand(
                        "FINANCE_ALLOCATION", finalBatchId, "复核跨项目财务分摊冲销明细"));
        Map<String, Object> reversalSubmitted = service.submitReversal(number(reversalDraft.get("id")));
        WfInstanceVO reversalDetail = workflowQueryService.getInstanceDetail(
                0L, number(reversalSubmitted.get("approvalInstanceId")), FINANCE_AUTHOR_USER);
        assertEquals("FINANCE_ALLOCATION", reversalDetail.getBusinessDetails().get("targetType"));
        assertEquals(2, ((Number) reversalDetail.getBusinessDetails().get("lineGroupCount")).intValue());
        assertEquals(false, reversalDetail.getBusinessDetails().get("linesTruncated"));
        List<?> reversalLines = (List<?>) reversalDetail.getBusinessDetails().get("lines");
        assertEquals(2, reversalLines.size());
        assertTrue(reversalLines.stream().map(Map.class::cast)
                .anyMatch(line -> "M89-COST-A".equals(line.get("projectCode"))));
        assertTrue(reversalLines.stream().map(Map.class::cast)
                .anyMatch(line -> "M89-COST-B".equals(line.get("projectCode"))));
    }

    @Test
    void recalculationUsesDedicatedAdjustmentSourcesAndPreservesRootSource() {
        Map<String, Object> staleTransfer = service.createBidTransferRequest(
                new CostSubjectV2Service.BidTransferRequestCommand(
                        BID, PROJECT_A, TARGET, MAPPING_VERSION,
                        "m96-bid-transfer-before-recalc", "重算前冻结的投标转入"));
        long staleTransferId = number(staleTransfer.get("id"));
        Map<String, Object> draft = service.createRecalculation(new CostSubjectV2Service.RecalculationCommand(
                PROJECT_A, MAPPING_VERSION, LocalDateTime.now().plusDays(1),
                "HISTORY_RECALCULATION", "重分类投标成本", "m96-recalc-root-source"));
        long batchId = number(draft.get("id"));
        assertEquals(1, number(draft.get("changedFactCount")));
        assertEquals(0, number(draft.get("unclassifiedCount")));
        jdbc.update("UPDATE cost_subject SET status='DISABLE' WHERE tenant_id=0 AND id=?", SOURCE_SUBJECT);

        Map<String, Object> submitted = service.submitRecalculation(batchId);
        long instanceId = number(submitted.get("approvalInstanceId"));
        WfInstanceVO approvalDetail = workflowQueryService.getInstanceDetail(
                0L, instanceId, FINANCE_AUTHOR_USER);
        assertNotNull(approvalDetail);
        assertEquals("HISTORY_RECALCULATION", approvalDetail.getBusinessDetails().get("batchType"));
        List<?> differenceLines = (List<?>) approvalDetail.getBusinessDetails().get("lines");
        assertEquals(1, differenceLines.size());
        assertEquals("M89-COST-TARGET", ((Map<?, ?>) differenceLines.getFirst()).get("newSubjectCode"));
        approveTwoNodeWorkflow(instanceId, () -> assertEquals("SUBMITTED",
                service.recalculationBatch(batchId).get("status")));
        asFinanceAuthor();

        assertEquals("POSTED", service.recalculationBatch(batchId).get("status"));
        List<Map<String, Object>> adjustments = jdbc.queryForList("""
                SELECT id,source_type,root_source_type,cost_subject_id,amount,original_cost_item_id
                FROM cost_item WHERE tenant_id=0 AND adjustment_batch_id=? ORDER BY amount
                """, batchId);
        assertEquals(2, adjustments.size());
        assertEquals("COST_RECALCULATION_NEGATIVE", adjustments.get(0).get("source_type"));
        assertEquals("COST_RECALCULATION_POSITIVE", adjustments.get(1).get("source_type"));
        assertEquals("BID_COST", adjustments.get(0).get("root_source_type"));
        assertEquals("BID_COST", adjustments.get(1).get("root_source_type"));
        assertEquals(SOURCE_SUBJECT, number(adjustments.get(0).get("cost_subject_id")));
        assertEquals("DISABLE", jdbc.queryForObject(
                "SELECT status FROM cost_subject WHERE tenant_id=0 AND id=?", String.class, SOURCE_SUBJECT));
        assertEquals(TARGET_SUBJECT, number(adjustments.get(1).get("cost_subject_id")));
        assertEquals(BID_COST_ITEM, number(adjustments.get(0).get("original_cost_item_id")));
        assertEquals(BID_COST_ITEM, number(adjustments.get(1).get("original_cost_item_id")));
        assertMoney("0.00", jdbc.queryForObject("""
                SELECT SUM(amount) FROM cost_item WHERE tenant_id=0 AND adjustment_batch_id=?
                """, BigDecimal.class, batchId));

        BusinessException drift = assertThrows(BusinessException.class,
                () -> service.submitBidTransferRequest(staleTransferId));
        assertEquals("BID_COST_TRANSFER_SOURCE_DRIFT", drift.getCode());
        Map<String, Object> cancelledStaleTransfer = service.cancelBidTransferRequest(staleTransferId);
        assertEquals("CANCELLED", cancelledStaleTransfer.get("status"));

        Map<String, Object> reclassifiedTransfer = service.createBidTransferRequest(
                new CostSubjectV2Service.BidTransferRequestCommand(
                        BID, PROJECT_A, TARGET, MAPPING_VERSION,
                        "m96-bid-transfer-after-recalc", "按重算后的当前事实转入"));
        long reclassifiedTransferId = number(reclassifiedTransfer.get("id"));
        Map<String, Object> reclassifiedLine = jdbc.queryForMap("""
                SELECT source_cost_item_id,source_subject_id,target_subject_id,amount
                FROM bid_cost_target_transfer_request_line WHERE tenant_id=0 AND request_id=?
                """, reclassifiedTransferId);
        assertEquals(number(adjustments.get(1).get("id")), number(reclassifiedLine.get("source_cost_item_id")));
        assertEquals(TARGET_SUBJECT, number(reclassifiedLine.get("source_subject_id")));
        assertEquals(TARGET_SUBJECT, number(reclassifiedLine.get("target_subject_id")));
        assertMoney("1000.00", reclassifiedLine.get("amount"));
        Map<String, Object> reclassifiedSubmitted = service.submitBidTransferRequest(reclassifiedTransferId);
        approveTwoNodeWorkflow(number(reclassifiedSubmitted.get("approvalInstanceId")), () ->
                assertEquals("SUBMITTED", service.bidTransferRequest(reclassifiedTransferId).get("status")));
        asFinanceAuthor();
        long postedTransferId = number(service.bidTransferRequest(reclassifiedTransferId).get("finalTransferId"));

        CostGovernanceOperations governance = (CostGovernanceOperations) ReflectionTestUtils
                .getField(service, "governanceOperations");
        assertNotNull(governance);
        BusinessException transferDependent = assertThrows(BusinessException.class,
                () -> governance.assertNoActiveOperationalDescendants(batchId));
        assertEquals("COST_RECALCULATION_DEPENDENT_TRANSFER_ACTIVE", transferDependent.getCode());

        Map<String, Object> transferReversal = service.createReversal(new CostSubjectV2Service.ReversalCommand(
                "BID_TRANSFER", postedTransferId, "先冲销依赖的投标成本转入"));
        Map<String, Object> transferReversalSubmitted = service.submitReversal(number(transferReversal.get("id")));
        WfInstanceVO transferReversalDetail = workflowQueryService.getInstanceDetail(
                0L, number(transferReversalSubmitted.get("approvalInstanceId")), FINANCE_AUTHOR_USER);
        assertEquals("BID_TRANSFER", transferReversalDetail.getBusinessDetails().get("targetType"));
        assertEquals(1, ((Number) transferReversalDetail.getBusinessDetails().get("lineGroupCount")).intValue());
        assertEquals(false, transferReversalDetail.getBusinessDetails().get("linesTruncated"));
        List<?> transferReversalLines = (List<?>) transferReversalDetail.getBusinessDetails().get("lines");
        assertEquals(1, transferReversalLines.size());
        assertEquals("M89-COST-TARGET", ((Map<?, ?>) transferReversalLines.getFirst()).get("sourceSubjectCode"));
        assertEquals("M89-COST-TARGET", ((Map<?, ?>) transferReversalLines.getFirst()).get("targetSubjectCode"));
        approveTwoNodeWorkflow(number(transferReversalSubmitted.get("approvalInstanceId")), () ->
                assertEquals("SUBMITTED", service.reversalRequest(number(transferReversal.get("id"))).get("status")));
        asFinanceAuthor();
        assertDoesNotThrow(() -> governance.assertNoActiveOperationalDescendants(batchId));

        Map<String, Object> reversal = service.createReversal(new CostSubjectV2Service.ReversalCommand(
                "RECALCULATION", batchId, "撤销重算以验证来源恢复"));
        long reversalId = number(reversal.get("id"));
        Map<String, Object> reversalSubmitted = service.submitReversal(reversalId);
        long reversalInstanceId = number(reversalSubmitted.get("approvalInstanceId"));

        long returnFactId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long positiveFactId = number(adjustments.get(1).get("id"));
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,classification_status,recognition_role,root_source_type,
                 original_cost_item_id,cost_type,amount,tax_amount,amount_without_tax,source_type,source_id,
                 source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,0,?,?, 'REVERSAL','ACTUAL','BID_COST',?,'BID',-40,0,-40,
                        'MATERIAL_RETURN',?,?,CURRENT_DATE,'CONFIRMED',1,0)
                """, returnFactId, PROJECT_A, TARGET_SUBJECT, positiveFactId, returnFactId, returnFactId);
        BusinessException dependent = assertThrows(BusinessException.class,
                () -> governance.assertNoActiveOperationalDescendants(batchId));
        assertEquals("COST_RECALCULATION_DEPENDENT_FACT_ACTIVE", dependent.getCode());
        assertEquals("POSTED", service.recalculationBatch(batchId).get("status"));

        long returnReversalFactId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,classification_status,recognition_role,root_source_type,
                 original_cost_item_id,cost_type,amount,tax_amount,amount_without_tax,source_type,source_id,
                 source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,0,?,?, 'REVERSAL','ACTUAL','BID_COST',?,'BID',40,0,40,
                        'MATERIAL_RETURN_REVERSAL',?,?,CURRENT_DATE,'CONFIRMED',1,0)
                """, returnReversalFactId, PROJECT_A, TARGET_SUBJECT, returnFactId,
                returnReversalFactId, returnReversalFactId);
        assertDoesNotThrow(() -> governance.assertNoActiveOperationalDescendants(batchId));
        approveTwoNodeWorkflow(reversalInstanceId, () -> assertEquals("SUBMITTED",
                service.reversalRequest(reversalId).get("status")));
        asFinanceAuthor();
        assertEquals("REVERSED", service.recalculationBatch(batchId).get("status"));
        jdbc.update("UPDATE cost_subject SET status='ENABLE' WHERE tenant_id=0 AND id=?", SOURCE_SUBJECT);

        Map<String, Object> restoredTransfer = service.createBidTransferRequest(
                new CostSubjectV2Service.BidTransferRequestCommand(
                        BID, PROJECT_A, TARGET, MAPPING_VERSION,
                        "m96-bid-transfer-after-reversal", "冲销重算后恢复原投标事实"));
        Map<String, Object> restoredLine = jdbc.queryForMap("""
                SELECT source_cost_item_id,source_subject_id,target_subject_id,amount
                FROM bid_cost_target_transfer_request_line WHERE tenant_id=0 AND request_id=?
                """, number(restoredTransfer.get("id")));
        assertEquals(BID_COST_ITEM, number(restoredLine.get("source_cost_item_id")));
        assertEquals(SOURCE_SUBJECT, number(restoredLine.get("source_subject_id")));
        assertEquals(TARGET_SUBJECT, number(restoredLine.get("target_subject_id")));
        assertMoney("1000.00", restoredLine.get("amount"));

        Map<String, Object> second = service.createRecalculation(new CostSubjectV2Service.RecalculationCommand(
                PROJECT_A, MAPPING_VERSION, LocalDateTime.now().plusDays(1),
                "HISTORY_RECALCULATION", "复算终态来源", "m96-recalc-root-source-2"));
        assertEquals(1, number(second.get("changedFactCount")));
        assertEquals(0, number(second.get("unclassifiedCount")));
        Map<String, Object> secondLine = jdbc.queryForMap("""
                SELECT old_cost_subject_id,new_cost_subject_id
                FROM cost_recalculation_line
                WHERE tenant_id=0 AND batch_id=? AND original_cost_item_id=?
                """, number(second.get("id")), BID_COST_ITEM);
        assertEquals(SOURCE_SUBJECT, number(secondLine.get("old_cost_subject_id")));
        assertEquals(TARGET_SUBJECT, number(secondLine.get("new_cost_subject_id")));
    }

    @Test
    void approvalDetailRowLimitsRejectInvisibleRuleAndProjectConfigurationChanges() {
        List<CostSubjectV2Service.MappingItem> mappings = new ArrayList<>();
        List<CostSubjectV2Service.MappingRule> rules = new ArrayList<>();
        List<CostSubjectV2Service.ProjectConfigLine> configLines = new ArrayList<>();
        for (int index = 0; index <= 1000; index++) {
            mappings.add(new CostSubjectV2Service.MappingItem(
                    SOURCE_SUBJECT, "LIMIT", TARGET_SUBJECT, "历史来源", "审批可见性上限"));
            rules.add(new CostSubjectV2Service.MappingRule(
                    "M96-LIMIT-" + index, "MAT_RECEIPT", "*", null, TARGET_SUBJECT,
                    100, LocalDate.now(), null, "审批可见性上限"));
            configLines.add(new CostSubjectV2Service.ProjectConfigLine(
                    TARGET_SUBJECT, false, LocalDate.now(), null));
        }

        BusinessException mappingLimit = assertThrows(BusinessException.class,
                () -> service.createMappingVersion(new CostSubjectV2Service.MappingVersionCommand(
                        "M96-MAPPING-LIMIT", "映射超限", LocalDate.now(), "审批可见性上限", mappings, null)));
        assertEquals("COST_RULE_PLAN_MAPPING_LIMIT_EXCEEDED", mappingLimit.getCode());

        BusinessException ruleLimit = assertThrows(BusinessException.class,
                () -> service.createMappingVersion(new CostSubjectV2Service.MappingVersionCommand(
                        "M96-RULE-LIMIT", "规则超限", LocalDate.now(), "审批可见性上限",
                        List.of(mappings.getFirst()), rules)));
        assertEquals("COST_RULE_PLAN_RULE_LIMIT_EXCEEDED", ruleLimit.getCode());

        BusinessException configLimit = assertThrows(BusinessException.class,
                () -> service.createProjectConfig(new CostSubjectV2Service.ProjectConfigCommand(
                        PROJECT_A, "项目配置超限", configLines)));
        assertEquals("COST_PROJECT_CONFIG_LINE_LIMIT_EXCEEDED", configLimit.getCode());
    }

    @Test
    void standardInitialPlanCoversEveryGovernedSource() {
        List<String> requiredSources = List.of(
                "MAT_RECEIPT", "MAT_REQUISITION", "SUB_MEASURE", "VAR_ORDER", "CT_CHANGE", "CT_CONTRACT",
                "QUALITY_SAFETY_CONSEQUENCE", "OVERHEAD_ALLOCATION", "OVERHEAD_ALLOCATION_CLEARING",
                "ACCOUNTING_ENTRY_LINE", "EXPENSE_APPLICATION", "FINANCE_COST_ALLOCATION",
                "FINANCE_COST_ALLOCATION_REVERSAL", "BID_COST", "BID_COST_WRITE_OFF", "MATERIAL_RETURN",
                "MATERIAL_RETURN_REVERSAL", "SUPPLIER_RETURN", "SUPPLIER_RETURN_REVERSAL");

        Map<String, Object> generated = service.generateInitialPlan();
        assertEquals(requiredSources.size(), number(generated.get("generatedRuleCount")));
        Map<?, ?> main = (Map<?, ?>) generated.get("main");
        long planId = number(main.get("id"));
        List<?> rules = (List<?>) generated.get("rules");
        assertEquals(requiredSources.size(), rules.size());
        List<String> actualSources = rules.stream()
                .map(item -> String.valueOf(((Map<?, ?>) item).get("sourceType")))
                .toList();
        assertTrue(actualSources.containsAll(requiredSources));

        Map<String, Object> report = service.validateMappingVersion(planId);
        assertEquals(Boolean.TRUE, report.get("passed"));
        assertEquals(List.of(), report.get("missingSourceTypes"));
    }

    @Test
    void rulePlanApprovalRevalidatesCurrentFactsBeforeRetiringActivePlan() {
        List<String> requiredSources = List.of(
                "MAT_RECEIPT", "MAT_REQUISITION", "SUB_MEASURE", "VAR_ORDER", "CT_CHANGE", "CT_CONTRACT",
                "QUALITY_SAFETY_CONSEQUENCE", "OVERHEAD_ALLOCATION", "OVERHEAD_ALLOCATION_CLEARING",
                "ACCOUNTING_ENTRY_LINE", "EXPENSE_APPLICATION", "FINANCE_COST_ALLOCATION",
                "FINANCE_COST_ALLOCATION_REVERSAL", "BID_COST", "BID_COST_WRITE_OFF", "MATERIAL_RETURN",
                "MATERIAL_RETURN_REVERSAL", "SUPPLIER_RETURN", "SUPPLIER_RETURN_REVERSAL");
        List<CostSubjectV2Service.MappingRule> rules = new ArrayList<>();
        for (int index = 0; index < requiredSources.size(); index++) {
            String source = requiredSources.get(index);
            rules.add(new CostSubjectV2Service.MappingRule(
                    "M96-REVALIDATE-" + index, source, "*", null, TARGET_SUBJECT,
                    100, LocalDate.now(), null, "审批前重验"));
        }
        long candidateId = service.createMappingVersion(new CostSubjectV2Service.MappingVersionCommand(
                "M96-REVALIDATE", "审批前重验方案", LocalDate.now(), "审批前重验",
                List.of(new CostSubjectV2Service.MappingItem(
                        SOURCE_SUBJECT, "REVALIDATE", TARGET_SUBJECT, "历史来源", "审批前重验")), rules));
        Map<String, Object> report = service.validateMappingVersion(candidateId);
        assertEquals(Boolean.TRUE, report.get("passed"));
        Map<String, Object> submitted = service.submitMappingVersion(candidateId);
        long instanceId = number(((Map<?, ?>) submitted.get("main")).get("approvalInstanceId"));

        jdbc.update("UPDATE wf_instance SET instance_status='APPROVED' WHERE tenant_id=0 AND id=?", instanceId);
        jdbc.update("UPDATE cost_subject SET status='DISABLE' WHERE tenant_id=0 AND id=?", TARGET_SUBJECT);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approveRulePlan(candidateId, instanceId));
        assertEquals("COST_RULE_PLAN_REVALIDATION_FAILED", error.getCode());
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM cost_subject_mapping_version WHERE id=?", String.class, MAPPING_VERSION));
        assertEquals("SUBMITTED", jdbc.queryForObject(
                "SELECT status FROM cost_subject_mapping_version WHERE id=?", String.class, candidateId));
    }

    private void approveTwoNodeWorkflow(long instanceId, Runnable afterFirstNode) {
        List<Map<String, Object>> nodes = jdbc.queryForList("""
                SELECT id,node_order,node_status,approver_config
                FROM wf_node_instance WHERE tenant_id=0 AND instance_id=? ORDER BY node_order
                """, instanceId);
        assertEquals(1, nodes.size());
        assertEquals("ACTIVE", nodes.get(0).get("node_status"));

        List<String> expectedRoles = List.of("COMPANY_FINANCE");
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            long nodeId = number(node.get("id"));
            assertTrue(String.valueOf(node.get("approver_config")).contains(expectedRoles.get(index)));
            List<Map<String, Object>> pending = jdbc.queryForList("""
                    SELECT id,approver_id FROM wf_task
                    WHERE tenant_id=0 AND instance_id=? AND node_instance_id=? AND task_status='PENDING'
                    ORDER BY id
                    """, instanceId, nodeId);
            assertFalse(pending.isEmpty());
            long seededApprover = FINANCE_USER;
            assertTrue(pending.stream().anyMatch(task -> number(task.get("approver_id")) == seededApprover));
            Map<String, Object> task = pending.stream()
                    .filter(item -> number(item.get("approver_id")) == seededApprover).findFirst().orElseThrow();
            asApprover(seededApprover, expectedRoles.get(index));
            afterFirstNode.run();
            workflowEngine.approve(number(task.get("id")), seededApprover,
                    "m89-approver-" + seededApprover, "同意",
                    "m89-cost-workflow-" + instanceId + "-" + nodeId + "-" + task.get("id"));
            assertEquals("COMPLETED", jdbc.queryForObject(
                    "SELECT node_status FROM wf_node_instance WHERE id=?", String.class, nodeId));
        }
        assertEquals("APPROVED", jdbc.queryForObject(
                "SELECT instance_status FROM wf_instance WHERE id=?", String.class, instanceId));
        assertEquals(0, count(
                "SELECT COUNT(*) FROM wf_task WHERE tenant_id=0 AND instance_id=? AND task_status='PENDING'", instanceId));
        assertEquals(1, count(
                "SELECT COUNT(*) FROM wf_node_instance WHERE tenant_id=0 AND instance_id=? AND node_status='COMPLETED'", instanceId));
    }

    private void assertSubmittedWorkflow(long instanceId, String businessType, long businessId,
                                         long projectId, String amount) {
        Map<String, Object> instance = jdbc.queryForMap("""
                SELECT business_type,business_id,project_id,amount,instance_status,security_policy_json
                FROM wf_instance WHERE tenant_id=0 AND id=? AND deleted_flag=0
                """, instanceId);
        assertEquals(businessType, instance.get("business_type"));
        assertEquals(businessId, number(instance.get("business_id")));
        assertEquals(projectId, number(instance.get("project_id")));
        assertMoney(amount, instance.get("amount"));
        assertEquals("RUNNING", instance.get("instance_status"));
        assertNotNull(instance.get("security_policy_json"));
        assertEquals(1, count(
                "SELECT COUNT(*) FROM wf_node_instance WHERE tenant_id=0 AND instance_id=?", instanceId));
        assertTrue(count(
                "SELECT COUNT(*) FROM wf_task WHERE tenant_id=0 AND instance_id=? AND task_status='PENDING'", instanceId) > 0);
    }

    private void assertFinanceRequestLines(long requestId) {
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT project_id,basis_value,allocated_amount
                FROM finance_cost_allocation_request_line
                WHERE tenant_id=0 AND request_id=? ORDER BY project_id
                """, requestId);
        assertEquals(2, lines.size());
        assertEquals(PROJECT_A, number(lines.get(0).get("project_id")));
        assertMoney("1.0000", lines.get(0).get("basis_value"));
        assertMoney("175.00", lines.get(0).get("allocated_amount"));
        assertEquals(PROJECT_B, number(lines.get(1).get("project_id")));
        assertMoney("3.0000", lines.get(1).get("basis_value"));
        assertMoney("525.00", lines.get(1).get("allocated_amount"));
    }

    private void seedUsersAndAccess() {
        jdbc.update("""
                INSERT INTO sys_user
                (id,tenant_id,username,password,real_name,status,is_admin,created_by,created_at,updated_at,deleted_flag)
                VALUES (?,0,'m89-cost-accountant','test-hash','计划89项目会计','ENABLE',0,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, ACCOUNTANT_USER, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO sys_user
                (id,tenant_id,username,password,real_name,status,is_admin,created_by,created_at,updated_at,deleted_flag)
                VALUES (?,0,'m89-company-finance','test-hash','计划89公司财务','ENABLE',1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, FINANCE_USER, FINANCE_USER);
        jdbc.update("""
                INSERT INTO sys_user
                (id,tenant_id,username,password,real_name,status,is_admin,created_by,created_at,updated_at,deleted_flag)
                VALUES (?,0,'m96-company-finance-author','test-hash','计划96公司财务发起人','ENABLE',0,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, FINANCE_AUTHOR_USER, FINANCE_AUTHOR_USER);

        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES (?,0,?,?)",
                ACCOUNTANT_ROLE_LINK, ACCOUNTANT_USER, roleId("PROJECT_ACCOUNTANT"));
        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES (?,0,?,?)",
                FINANCE_ROLE_LINK, FINANCE_USER, roleId("COMPANY_FINANCE"));
        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES (?,0,?,?)",
                SUPER_ADMIN_ROLE_LINK, FINANCE_USER, roleId("SUPER_ADMIN"));
        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES (?,0,?,?)",
                FINANCE_AUTHOR_ROLE_LINK, FINANCE_AUTHOR_USER, roleId("COMPANY_FINANCE"));

        insertProject(PROJECT_A, "M89-COST-A", "计划89成本项目A");
        insertProject(PROJECT_B, "M89-COST-B", "计划89成本项目B");
        jdbc.update("""
                INSERT INTO pm_project_member
                (id,tenant_id,project_id,user_id,role_code,status,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES (?,0,?,?,'PROJECT_ACCOUNTANT','ACTIVE',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)
                """, PROJECT_MEMBER_A, PROJECT_A, ACCOUNTANT_USER, ACCOUNTANT_USER, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO pm_project_member
                (id,tenant_id,project_id,user_id,role_code,status,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES (?,0,?,?,'PROJECT_ACCOUNTANT','ACTIVE',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)
                """, PROJECT_MEMBER_B, PROJECT_B, ACCOUNTANT_USER, ACCOUNTANT_USER, ACCOUNTANT_USER);
    }

    private void seedCostFacts() {
        insertSubject(SOURCE_SUBJECT, "M89-BID-SOURCE", "计划89投标来源科目");
        insertSubject(TARGET_SUBJECT, "M89-COST-TARGET", "计划89成本目标科目");
        jdbc.update("""
                INSERT INTO cost_subject_mapping_version
                (id,tenant_id,version_code,version_name,status,effective_date,created_by)
                VALUES (?,0,'M89-COST-WF','计划89成本工作流映射','ACTIVE',CURRENT_DATE,?)
                """, MAPPING_VERSION, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO cost_subject_mapping_item
                (id,tenant_id,mapping_version_id,source_subject_id,target_group_code,target_subject_id,
                 historical_display_name,mapping_reason,created_by)
                VALUES (?,0,?,?,'BID_TARGET',?,'计划89投标历史口径','工作流集成测试',?)
                """, MAPPING_ITEM, MAPPING_VERSION, SOURCE_SUBJECT, TARGET_SUBJECT, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO cost_subject_assignment_rule
                (id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,
                 cost_subject_id,priority,status,effective_from,created_by)
                VALUES (?,0,?,'M89-FINANCE-RULE','ACCOUNTING_ENTRY_LINE','*',NULL,?,100,'ACTIVE',CURRENT_DATE,?)
                """, FINANCE_RULE, MAPPING_VERSION, TARGET_SUBJECT, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO cost_subject_assignment_rule
                (id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,
                 cost_subject_id,priority,status,effective_from,created_by)
                VALUES (?,0,?,'M96-BID-RECALC-RULE','BID_COST','*',NULL,?,100,'ACTIVE',CURRENT_DATE,?)
                """, BID_RULE, MAPPING_VERSION, TARGET_SUBJECT, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO bid_cost
                (id,tenant_id,project_id,bid_code,bid_project_name,bid_status,created_by,deleted_flag)
                VALUES (?,0,?,'M89-BID-WON','计划89已中标项目','WON',?,0)
                """, BID, PROJECT_A, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO cost_target
                (id,tenant_id,project_id,version_no,version_name,total_target_amount,total_bid_cost_amount,
                 total_responsibility_amount,is_active,approval_status,status,created_by,deleted_flag)
                VALUES (?,0,?,'M89-TARGET-V1','计划89目标成本',0,0,0,0,'DRAFT','DRAFT',?,0)
                """, TARGET, PROJECT_A, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,
                 source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,created_by,deleted_flag)
                VALUES (?,0,?,?,'BID',1000,0,1000,'BID_COST',?,0,CURRENT_DATE,'CONFIRMED',1,?,0)
                """, BID_COST_ITEM, PROJECT_A, SOURCE_SUBJECT, BID, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO accounting_entry
                (id,tenant_id,entry_code,entry_date,entry_type,source_type,source_id,entry_status,
                 total_debit,total_credit,created_by,deleted_flag)
                VALUES (?,0,'M89-FIN-ENTRY',CURRENT_DATE,'GENERAL','MANUAL',?,'POSTED',1000,1000,?,0)
                """, ACCOUNTING_ENTRY, ACCOUNTING_ENTRY, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO accounting_entry_line
                (id,tenant_id,entry_id,line_no,direction,cost_subject_id,amount,summary)
                VALUES (?,0,?,1,'DEBIT',?,1000,'计划89财务成本分摊来源')
                """, ACCOUNTING_LINE, ACCOUNTING_ENTRY, TARGET_SUBJECT);
    }

    private void seedPartialFinanceAllocation() {
        Long templateId = jdbc.queryForObject("""
                SELECT id FROM wf_template
                WHERE tenant_id=0 AND business_type='FINANCE_COST_ALLOCATION'
                  AND enabled=1 AND deleted_flag=0 ORDER BY id DESC LIMIT 1
                """, Long.class);
        assertNotNull(templateId);
        jdbc.update("""
                INSERT INTO wf_instance
                (id,tenant_id,template_id,business_type,business_id,project_id,title,amount,instance_status,
                 current_round,resubmit_count,business_revision,initiator_id,created_by,deleted_flag)
                VALUES (?,0,?,'FINANCE_COST_ALLOCATION',?,?,'计划89既有合法部分分摊',300,'APPROVED',
                        1,0,1,?,?,0)
                """, PARTIAL_APPROVAL, templateId, ACCOUNTING_LINE, PROJECT_A,
                ACCOUNTANT_USER, ACCOUNTANT_USER);
        jdbc.update("""
                INSERT INTO finance_cost_allocation_batch
                (id,tenant_id,batch_code,source_type,source_id,source_amount,allocation_basis,accounting_period,
                 cost_subject_id,idempotency_key,status,approval_instance_id,posted_by,remark)
                VALUES (?,0,'M89-PARTIAL-FCA','ACCOUNTING_ENTRY_LINE',?,300,'DIRECT_PROJECT','2026-08',
                        ?,'m89-legal-partial','POSTED',?,?, '计划89既有合法部分分摊')
                """, PARTIAL_BATCH, ACCOUNTING_LINE, TARGET_SUBJECT, PARTIAL_APPROVAL, FINANCE_USER);
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,source_type,
                 source_id,source_item_id,cost_date,cost_status,generated_flag,created_by,created_at,updated_at,deleted_flag,remark)
                VALUES (?,0,?,?,'FINANCE',300,0,300,'FINANCE_COST_ALLOCATION',?,1,CURRENT_DATE,
                        'CONFIRMED',1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'计划89既有合法部分分摊')
                """, PARTIAL_COST_ITEM, PROJECT_A, TARGET_SUBJECT, PARTIAL_BATCH, FINANCE_USER);
        jdbc.update("""
                INSERT INTO finance_cost_allocation_line
                (id,tenant_id,batch_id,project_id,basis_value,allocated_amount,cost_item_id)
                VALUES (?,0,?,?,1,300,?)
                """, PARTIAL_LINE, PARTIAL_BATCH, PROJECT_A, PARTIAL_COST_ITEM);
    }

    private void insertProject(long id, String code, String name) {
        jdbc.update("""
                INSERT INTO pm_project
                (id,tenant_id,project_code,project_name,contract_amount,target_cost,project_manager_id,
                 status,approval_status,created_by,updated_by,deleted_flag)
                VALUES (?,0,?,?,100000,80000,?,'ACTIVE','APPROVED',?,?,0)
                """, id, code, name, ACCOUNTANT_USER, ACCOUNTANT_USER, ACCOUNTANT_USER);
    }

    private void insertSubject(long id, String code, String name) {
        jdbc.update("""
                INSERT INTO cost_subject
                (id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,
                 level,sort_order,status,created_by,deleted_flag)
                VALUES (?,0,0,?,?,'OTHER','COST',1,1,'ENABLE',?,0)
                """, id, code, name, ACCOUNTANT_USER);
    }

    private long roleId(String roleCode) {
        Long id = jdbc.queryForObject("""
                SELECT id FROM sys_role
                WHERE tenant_id=0 AND role_code=? AND status='ENABLE' AND deleted_flag=0
                ORDER BY id DESC LIMIT 1
                """, Long.class, roleCode);
        assertNotNull(id, "缺少计划89角色: " + roleCode);
        return id;
    }

    private void asAccountant(long userId) {
        asUser(userId, "PROJECT_ACCOUNTANT", "cost:subject:transfer:submit", "cost:subject:allocation:submit");
    }

    private void asFinanceAuthor() {
        asUser(FINANCE_AUTHOR_USER, "COMPANY_FINANCE",
                "cost:subject:mapping:edit", "cost:subject:rule:edit", "cost:subject:scope:edit",
                "cost:subject:bid-transfer", "cost:subject:transfer:submit",
                "cost:subject:finance-allocate", "cost:subject:allocation:submit",
                "cost:project-config:edit", "cost:project-config:submit",
                "cost:recalculation:edit", "cost:recalculation:submit",
                "cost:post-close:edit", "cost:post-close:submit",
                "cost:reversal:edit", "cost:reversal:submit", "cost:classification:override");
    }

    private void asApprover(long userId, String roleCode) {
        asUser(userId, roleCode, "workflow:approve");
    }

    private void asUser(long userId, String roleCode, String... authorities) {
        TestUserContext.setUser(0L, userId, "m89-user-" + userId, List.of(roleCode));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "m89-user-" + userId, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private int checkIssueCount(List<Map<String, Object>> checks, String checkType) {
        return checks.stream().filter(row -> checkType.equals(row.get("check_type")))
                .map(row -> ((Number) row.get("issue_count")).intValue())
                .findFirst().orElseThrow();
    }

    private long number(Object value) {
        assertNotNull(value);
        return ((Number) value).longValue();
    }

    private void assertMoney(String expected, Object actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
    }
}
