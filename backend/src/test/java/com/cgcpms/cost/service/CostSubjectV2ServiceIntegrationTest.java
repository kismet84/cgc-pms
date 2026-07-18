package com.cgcpms.cost.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class CostSubjectV2ServiceIntegrationTest {

    private static final long PROJECT_ID = 10001L;
    private static final long VERSION_ID = 99351001L;
    private static final long SOURCE_SUBJECT_ID = 99351002L;
    private static final long GLOBAL_SUBJECT_ID = 99351003L;
    private static final long EXACT_SUBJECT_ID = 99351004L;
    private static final long PROJECT_SUBJECT_ID = 99351005L;
    private static final long BID_ID = 99351006L;
    private static final long TARGET_ID = 99351007L;
    private static final long SOURCE_ITEM_ID = 99351008L;
    private static final long TRANSFER_APPROVAL_ID = 99351009L;
    private static final long REVERSAL_APPROVAL_ID = 99351010L;
    private static final long OTHER_PROJECT_ID = 99351011L;
    private static final long ACCOUNTING_ENTRY_ID = 99351012L;
    private static final long ACCOUNTING_LINE_ID = 99351013L;
    private static final long ALLOCATION_APPROVAL_ID = 99351014L;
    private static final long ALLOCATION_REVERSAL_APPROVAL_ID = 99351015L;

    @Autowired
    private CostSubjectV2Service service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims()
                .subject("admin")
                .add("userId", 1L)
                .add("username", "admin")
                .add("tenantId", 0L)
                .add("roleCodes", List.of("ADMIN"))
                .build());
        insertSubject(SOURCE_SUBJECT_ID, "V2-IT-SOURCE", "投标费用来源");
        insertSubject(GLOBAL_SUBJECT_ID, "V2-IT-GLOBAL", "全局兜底成本");
        insertSubject(EXACT_SUBJECT_ID, "V2-IT-EXACT", "精确分类成本");
        insertSubject(PROJECT_SUBJECT_ID, "V2-IT-PROJECT", "项目专用成本");
        jdbc.update("""
                INSERT INTO cost_subject_mapping_version
                (id,tenant_id,version_code,version_name,status,effective_date,created_by)
                VALUES (?,0,'V2-IT','V2集成测试映射','ACTIVE',CURRENT_DATE,1)
                """, VERSION_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void resolvesByProjectThenCategoryThenPriorityAndRejectsSameRankConflict() {
        insertRule(99351101L, "RULE-GLOBAL", "V2_RULE_TEST", "*", null, GLOBAL_SUBJECT_ID, 1);
        insertRule(99351102L, "RULE-EXACT", "V2_RULE_TEST", "QUALITY", null, EXACT_SUBJECT_ID, 20);
        insertRule(99351103L, "RULE-PROJECT", "V2_RULE_TEST", "QUALITY", PROJECT_ID, PROJECT_SUBJECT_ID, 50);
        jdbc.update("""
                INSERT INTO project_cost_subject_scope
                (id,tenant_id,project_id,cost_subject_id,enabled,effective_from,created_by)
                VALUES (?,0,?,?,1,CURRENT_DATE,1)
                """, 99351104L, PROJECT_ID, PROJECT_SUBJECT_ID);

        assertEquals(PROJECT_SUBJECT_ID, service.resolveRule("V2_RULE_TEST", "QUALITY", PROJECT_ID));
        jdbc.update("DELETE FROM cost_subject_assignment_rule WHERE id=?", 99351103L);
        assertEquals(EXACT_SUBJECT_ID, service.resolveRule("V2_RULE_TEST", "QUALITY", PROJECT_ID));

        insertRule(99351105L, "RULE-EXACT-CONFLICT", "V2_RULE_TEST", "QUALITY", null, GLOBAL_SUBJECT_ID, 20);
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.resolveRule("V2_RULE_TEST", "QUALITY", PROJECT_ID));
        assertEquals("COST_SUBJECT_RULE_AMBIGUOUS", conflict.getCode());
    }

    @Test
    void rejectsUnclassifiedFacts() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveRule("UNKNOWN_V2_SOURCE", "UNKNOWN", PROJECT_ID));
        assertEquals("COST_SUBJECT_UNCLASSIFIED", exception.getCode());
    }

    @Test
    void transfersBidCostOncePerTargetVersionAndAllowsRetransferAfterReversal() {
        jdbc.update("""
                INSERT INTO cost_subject_mapping_item
                (id,tenant_id,mapping_version_id,source_subject_id,target_group_code,target_subject_id,
                 historical_display_name,mapping_reason)
                VALUES (?,0,?,?,'BID_TARGET',?,'投标费用历史口径','集成测试')
                """, 99351201L, VERSION_ID, SOURCE_SUBJECT_ID, PROJECT_SUBJECT_ID);
        jdbc.update("""
                INSERT INTO bid_cost
                (id,tenant_id,project_id,bid_project_name,bid_status,created_by,deleted_flag)
                VALUES (?,0,?,'V2集成测试投标','WON',1,0)
                """, BID_ID, PROJECT_ID);
        jdbc.update("""
                INSERT INTO cost_target
                (id,tenant_id,project_id,version_no,version_name,total_target_amount,total_bid_cost_amount,
                 total_responsibility_amount,is_active,approval_status,status,created_by,deleted_flag)
                VALUES (?,0,?,'V2-IT-TARGET','V2集成测试目标成本',0,0,0,0,'DRAFT','DRAFT',1,0)
                """, TARGET_ID, PROJECT_ID);
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,
                 source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,created_by,deleted_flag)
                VALUES (?,0,?,?,'BID',1000,0,1000,'BID_COST',?,0,CURRENT_DATE,'CONFIRMED',1,1,0)
                """, SOURCE_ITEM_ID, PROJECT_ID, SOURCE_SUBJECT_ID, BID_ID);
        insertApprovedWorkflow(TRANSFER_APPROVAL_ID, 50051L, "BID_COST_TARGET_TRANSFER", BID_ID);

        Long firstTransfer = service.transferBidCost(new CostSubjectV2Service.TransferCommand(
                BID_ID, PROJECT_ID, TARGET_ID, VERSION_ID, TRANSFER_APPROVAL_ID, "v2-it-transfer-1", "集成测试"));
        assertEquals(0, new BigDecimal("1000.00").compareTo(jdbc.queryForObject(
                "SELECT total_target_amount FROM cost_target WHERE id=?", BigDecimal.class, TARGET_ID)));
        assertEquals("BID_COST", jdbc.queryForObject(
                "SELECT source_type FROM cost_item WHERE id=?", String.class, SOURCE_ITEM_ID));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.transferBidCost(new CostSubjectV2Service.TransferCommand(
                        BID_ID, PROJECT_ID, TARGET_ID, VERSION_ID, TRANSFER_APPROVAL_ID,
                        "v2-it-transfer-2", "重复转入")));
        assertEquals("BID_COST_TRANSFER_DUPLICATE", duplicate.getCode());

        insertApprovedWorkflow(REVERSAL_APPROVAL_ID, 50052L,
                "BID_COST_TARGET_TRANSFER_REVERSAL", firstTransfer);
        service.reverseBidTransfer(firstTransfer, REVERSAL_APPROVAL_ID, "v2-it-reversal-1", "集成测试冲销");
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "SELECT total_target_amount FROM cost_target WHERE id=?", BigDecimal.class, TARGET_ID)));

        service.transferBidCost(new CostSubjectV2Service.TransferCommand(
                BID_ID, PROJECT_ID, TARGET_ID, VERSION_ID, TRANSFER_APPROVAL_ID,
                "v2-it-transfer-3", "冲销后重新转入"));
        assertEquals(0, new BigDecimal("1000.00").compareTo(jdbc.queryForObject(
                "SELECT total_target_amount FROM cost_target WHERE id=?", BigDecimal.class, TARGET_ID)));
    }

    @Test
    void allocatesFinanceCostByProjectAndKeepsReversalAsNegativeFacts() {
        jdbc.update("""
                INSERT INTO pm_project
                (id,tenant_id,project_code,project_name,contract_amount,target_cost,project_manager_id,
                 status,approval_status,created_by,updated_by,deleted_flag)
                VALUES (?,0,'V2-FIN-PROJECT','V2财务分摊测试项目',300000,200000,1,
                        'ACTIVE','APPROVED',1,1,0)
                """, OTHER_PROJECT_ID);
        jdbc.update("""
                INSERT INTO accounting_entry
                (id,tenant_id,entry_code,entry_date,entry_type,source_type,source_id,entry_status,
                 total_debit,total_credit,created_by,deleted_flag)
                VALUES (?,0,'V2-FIN-ENTRY',CURRENT_DATE,'GENERAL','MANUAL',?, 'POSTED',1000,1000,1,0)
                """, ACCOUNTING_ENTRY_ID, ACCOUNTING_ENTRY_ID);
        jdbc.update("""
                INSERT INTO accounting_entry_line
                (id,tenant_id,entry_id,line_no,direction,cost_subject_id,amount,summary)
                VALUES (?,0,?,1,'DEBIT',?,1000,'V2财务费用分摊测试')
                """, ACCOUNTING_LINE_ID, ACCOUNTING_ENTRY_ID, PROJECT_SUBJECT_ID);
        insertApprovedWorkflow(ALLOCATION_APPROVAL_ID, 50053L,
                "FINANCE_COST_ALLOCATION", ACCOUNTING_LINE_ID);

        Long batchId = service.allocateFinanceCost(new CostSubjectV2Service.FinanceAllocationCommand(
                "ACCOUNTING_ENTRY_LINE", ACCOUNTING_LINE_ID, "BENEFIT_AMOUNT", "2026-07",
                PROJECT_SUBJECT_ID, ALLOCATION_APPROVAL_ID, "v2-it-allocation-1", "受益额分摊",
                List.of(
                        new CostSubjectV2Service.AllocationLine(PROJECT_ID, new BigDecimal("1")),
                        new CostSubjectV2Service.AllocationLine(OTHER_PROJECT_ID, new BigDecimal("3")))));

        assertEquals(0, new BigDecimal("1000.00").compareTo(jdbc.queryForObject(
                "SELECT SUM(allocated_amount) FROM finance_cost_allocation_line WHERE batch_id=?",
                BigDecimal.class, batchId)));
        assertEquals(0, new BigDecimal("250.00").compareTo(jdbc.queryForObject(
                "SELECT allocated_amount FROM finance_cost_allocation_line WHERE batch_id=? AND project_id=?",
                BigDecimal.class, batchId, PROJECT_ID)));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.allocateFinanceCost(new CostSubjectV2Service.FinanceAllocationCommand(
                        "ACCOUNTING_ENTRY_LINE", ACCOUNTING_LINE_ID, "DIRECT_PROJECT", "2026-07",
                        PROJECT_SUBJECT_ID, ALLOCATION_APPROVAL_ID, "v2-it-allocation-2", "重复分摊",
                        List.of(new CostSubjectV2Service.AllocationLine(PROJECT_ID, BigDecimal.ONE)))));
        assertEquals("FINANCE_COST_ALREADY_ALLOCATED", duplicate.getCode());

        insertApprovedWorkflow(ALLOCATION_REVERSAL_APPROVAL_ID, 50054L,
                "FINANCE_COST_ALLOCATION_REVERSAL", batchId);
        service.reverseFinanceAllocation(batchId, ALLOCATION_REVERSAL_APPROVAL_ID,
                "v2-it-allocation-reversal-1", "财务分摊冲销");
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "SELECT SUM(source_amount) FROM finance_cost_allocation_batch WHERE source_type='ACCOUNTING_ENTRY_LINE' AND source_id=?",
                BigDecimal.class, ACCOUNTING_LINE_ID)));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "SELECT SUM(amount_without_tax) FROM cost_item WHERE source_type IN ('FINANCE_COST_ALLOCATION','FINANCE_COST_ALLOCATION_REVERSAL')",
                BigDecimal.class)));

        service.allocateFinanceCost(new CostSubjectV2Service.FinanceAllocationCommand(
                "ACCOUNTING_ENTRY_LINE", ACCOUNTING_LINE_ID, "DIRECT_PROJECT", "2026-07",
                PROJECT_SUBJECT_ID, ALLOCATION_APPROVAL_ID, "v2-it-allocation-3", "冲销后重新分摊",
                List.of(new CostSubjectV2Service.AllocationLine(PROJECT_ID, BigDecimal.ONE))));
    }

    private void insertSubject(long id, String code, String name) {
        jdbc.update("""
                INSERT INTO cost_subject
                (id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,
                 level,sort_order,status,deleted_flag)
                VALUES (?,0,0,?,?,'OTHER','COST',1,1,'ENABLE',0)
                """, id, code, name);
    }

    private void insertRule(long id, String code, String sourceType, String category,
                            Long projectId, long subjectId, int priority) {
        jdbc.update("""
                INSERT INTO cost_subject_assignment_rule
                (id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,
                 cost_subject_id,priority,status,effective_from,created_by)
                VALUES (?,0,?,?,?,?,?,? ,?,'ACTIVE',CURRENT_DATE,1)
                """, id, VERSION_ID, code, sourceType, category, projectId, subjectId, priority);
    }

    private void insertApprovedWorkflow(long id, long templateId, String businessType, long businessId) {
        jdbc.update("""
                INSERT INTO wf_instance
                (id,tenant_id,template_id,business_type,business_id,project_id,title,instance_status,
                 current_round,resubmit_count,business_revision,initiator_id,created_by,deleted_flag)
                VALUES (?,0,?,?,?,?,?,'APPROVED',1,0,1,1,1,0)
                """, id, templateId, businessType, businessId, PROJECT_ID, "V2集成测试审批");
    }
}
