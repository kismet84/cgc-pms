package com.cgcpms.cost.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.service.WorkflowEngine;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Autowired
    private CostSubjectV2Service service;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        seedUsersAndAccess();
        seedCostFacts();
        asAccountant(ACCOUNTANT_USER);
        assertEquals(2, count("""
                SELECT COUNT(*) FROM wf_template_node n
                JOIN wf_template t ON t.id=n.template_id AND t.tenant_id=n.tenant_id
                WHERE t.tenant_id=0 AND t.enabled=1 AND t.deleted_flag=0 AND n.deleted_flag=0
                  AND t.business_type='BID_COST_TARGET_TRANSFER'
                """));
        assertEquals(2, count("""
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
    }

    private void approveTwoNodeWorkflow(long instanceId, Runnable afterFirstNode) {
        List<Map<String, Object>> nodes = jdbc.queryForList("""
                SELECT id,node_order,node_status,approver_config
                FROM wf_node_instance WHERE tenant_id=0 AND instance_id=? ORDER BY node_order
                """, instanceId);
        assertEquals(2, nodes.size());
        assertEquals("ACTIVE", nodes.get(0).get("node_status"));
        assertEquals("WAITING", nodes.get(1).get("node_status"));

        List<String> expectedRoles = List.of("PROJECT_ACCOUNTANT", "COMPANY_FINANCE");
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
            long seededApprover = index == 0 ? ACCOUNTANT_USER : FINANCE_USER;
            assertTrue(pending.stream().anyMatch(task -> number(task.get("approver_id")) == seededApprover));
            Map<String, Object> task = pending.stream()
                    .filter(item -> number(item.get("approver_id")) == seededApprover).findFirst().orElseThrow();
            asApprover(seededApprover, expectedRoles.get(index));
            workflowEngine.approve(number(task.get("id")), seededApprover,
                    "m89-approver-" + seededApprover, "同意",
                    "m89-cost-workflow-" + instanceId + "-" + nodeId + "-" + task.get("id"));
            assertEquals("COMPLETED", jdbc.queryForObject(
                    "SELECT node_status FROM wf_node_instance WHERE id=?", String.class, nodeId));
            if (index == 0) {
                assertEquals("ACTIVE", jdbc.queryForObject(
                        "SELECT node_status FROM wf_node_instance WHERE id=?",
                        String.class, number(nodes.get(1).get("id"))));
                afterFirstNode.run();
            }
        }
        assertEquals("APPROVED", jdbc.queryForObject(
                "SELECT instance_status FROM wf_instance WHERE id=?", String.class, instanceId));
        assertEquals(0, count(
                "SELECT COUNT(*) FROM wf_task WHERE tenant_id=0 AND instance_id=? AND task_status='PENDING'", instanceId));
        assertEquals(2, count(
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
        assertEquals(2, count(
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

        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES (?,0,?,?)",
                ACCOUNTANT_ROLE_LINK, ACCOUNTANT_USER, roleId("PROJECT_ACCOUNTANT"));
        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES (?,0,?,?)",
                FINANCE_ROLE_LINK, FINANCE_USER, roleId("COMPANY_FINANCE"));
        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES (?,0,?,?)",
                SUPER_ADMIN_ROLE_LINK, FINANCE_USER, roleId("SUPER_ADMIN"));

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

    private long number(Object value) {
        assertNotNull(value);
        return ((Number) value).longValue();
    }

    private void assertMoney(String expected, Object actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
    }
}
