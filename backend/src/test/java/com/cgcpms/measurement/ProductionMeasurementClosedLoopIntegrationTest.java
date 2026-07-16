package com.cgcpms.measurement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.measurement.dto.MeasurementModels.*;
import com.cgcpms.measurement.service.ProductionMeasurementService;
import com.cgcpms.revenue.service.RevenueOperationsService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class ProductionMeasurementClosedLoopIntegrationTest {
    private static final long PROJECT = 99175001L;
    private static final long CUSTOMER = 99175002L;
    private static final long CONTRACT = 99175003L;
    private static final long ITEM = 99175004L;
    private static final long CHANGE = 99175005L;

    @Autowired ProductionMeasurementService service;
    @Autowired RevenueOperationsService revenueService;
    @Autowired WorkflowEngine workflowEngine;
    @Autowired WfInstanceMapper instanceMapper;
    @Autowired WfTaskMapper taskMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", 1L).add("username", "admin")
                .add("tenantId", 0L).add("roleCodes", List.of("ADMIN")).build());
        cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) SELECT 1,0,'admin','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','系统管理员','ENABLE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'MEASURE-IT-P','产值计量测试项目','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'MEASURE-IT-CUSTOMER','测试业主','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CUSTOMER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'MEASURE-IT-C','业主总包合同','MAIN',?,?,12000,12000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CONTRACT, PROJECT, CUSTOMER, CUSTOMER);
        jdbc.update("INSERT INTO ct_contract_item(id,tenant_id,contract_id,item_code,item_name,item_spec,unit,quantity,unit_price,amount,tax_rate,tax_amount,amount_without_tax,sort_order,created_at,updated_at,deleted_flag) VALUES(?,0,?,'BOQ-001','混凝土工程','C30','m3',100,10,1000,0,0,1000,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", ITEM, CONTRACT);
        jdbc.update("INSERT INTO ct_contract_change(id,tenant_id,project_id,contract_id,change_code,change_name,change_type,before_amount,change_amount,after_amount,approval_status,effective_flag,cost_generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,'CHG-001','新增临建工程','AMOUNT',10000,200,10200,'APPROVED',1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", CHANGE, PROJECT, CONTRACT);
    }

    @AfterEach
    void teardown() { cleanup(); UserContext.clear(); }

    @Test
    void fullChainFromBoqAndChangeToOwnerSettlementReceivableAndTrace() {
        long periodId = createPeriod("2026-07");
        var measurement = service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, periodId, LocalDate.of(2026, 7, 15), 1,
                List.of(new MeasurementLineRequest(ITEM, null, new BigDecimal("20"), 1),
                        new MeasurementLineRequest(null, CHANGE, new BigDecimal("0.5"), 1)), "本期完成量"));
        long measurementId = id(measurement);
        service.submitMeasurement(measurementId);
        approveAll("PRODUCTION_MEASUREMENT", measurementId);
        assertEquals("INTERNAL_APPROVED", jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?", String.class, measurementId));

        var submission = service.submitToOwner(measurementId, new OwnerSubmissionRequest("OWNER-REPORT-001", 1, "业主报量"));
        long submissionId = id(submission);
        @SuppressWarnings("unchecked") List<java.util.Map<String,Object>> lines = (List<java.util.Map<String,Object>>) service.submission(submissionId).get("lines");
        long itemLine = lines.stream().filter(row -> "BOQ-001".equals(row.get("item_code"))).map(row -> ((Number) row.get("measurement_line_id")).longValue()).findFirst().orElseThrow();
        long changeLine = lines.stream().filter(row -> "CHG-001".equals(row.get("item_code"))).map(row -> ((Number) row.get("measurement_line_id")).longValue()).findFirst().orElseThrow();
        var reviewed = service.review(submissionId, new OwnerReviewRequest("CONFIRMED", "业主代表", "核定通过",
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 20), BigDecimal.ZERO, new BigDecimal("30"), 1,
                List.of(new OwnerReviewLineRequest(itemLine, new BigDecimal("18"), "现场抽检核减2m3"),
                        new OwnerReviewLineRequest(changeLine, new BigDecimal("0.5"), null))));
        @SuppressWarnings("unchecked") var settlement = (java.util.Map<String,Object>) reviewed.get("settlement");
        long settlementId = id(settlement);
        assertEquals(new BigDecimal("280.00"), jdbc.queryForObject("SELECT gross_amount FROM owner_settlement WHERE id=?", BigDecimal.class, settlementId));
        assertEquals(new BigDecimal("20.00"), jdbc.queryForObject("SELECT deducted_amount FROM owner_settlement WHERE id=?", BigDecimal.class, settlementId));
        assertEquals(submissionId, jdbc.queryForObject("SELECT owner_submission_id FROM owner_settlement WHERE id=?", Long.class, settlementId));

        revenueService.submitSettlement(settlementId);
        approveAll("OWNER_SETTLEMENT", settlementId);
        assertEquals("RECEIVABLE_CREATED", jdbc.queryForObject("SELECT status FROM owner_settlement WHERE id=?", String.class, settlementId));
        assertEquals(new BigDecimal("280.00"), jdbc.queryForObject("SELECT SUM(original_amount) FROM account_receivable WHERE settlement_id=?", BigDecimal.class, settlementId));

        var trace = service.traceBySettlement(settlementId);
        assertEquals(measurementId, id((java.util.Map<?,?>) trace.get("measurement")));
        assertEquals(2, ((List<?>) trace.get("measurementLines")).size());
        assertEquals(2, ((List<?>) trace.get("ownerReviewLines")).size());
        assertFalse(((List<?>) trace.get("approvalInstances")).isEmpty());
        assertFalse(((List<?>) trace.get("receivables")).isEmpty());
    }

    @Test
    void rejectsOverMeasurementAndMissingEvidence() {
        long periodId = createPeriod("2026-08");
        assertThrows(BusinessException.class, () -> service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, periodId,
                LocalDate.of(2026, 8, 10), 1, List.of(new MeasurementLineRequest(ITEM, null, new BigDecimal("101"), 1)), null)));
        var draft = service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, periodId, LocalDate.of(2026, 8, 10), 1,
                List.of(new MeasurementLineRequest(ITEM, null, new BigDecimal("10"), 0)), null));
        assertThrows(BusinessException.class, () -> service.submitMeasurement(id(draft)));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?", String.class, id(draft)));
    }

    @Test
    void ownerDeductionRequiresReasonAndDuplicateConfirmationIsIdempotent() {
        long measurementId = internallyApprovedMeasurement("2026-09", new BigDecimal("10"));
        long submissionId = id(service.submitToOwner(measurementId, new OwnerSubmissionRequest("OWNER-REPORT-002", 1, null)));
        @SuppressWarnings("unchecked") List<java.util.Map<String,Object>> lines = (List<java.util.Map<String,Object>>) service.submission(submissionId).get("lines");
        long lineId = ((Number) lines.get(0).get("measurement_line_id")).longValue();
        OwnerReviewRequest invalid = new OwnerReviewRequest("CONFIRMED", "业主代表", null, LocalDate.now(), LocalDate.now().plusDays(30), BigDecimal.ZERO, BigDecimal.ZERO, 1,
                List.of(new OwnerReviewLineRequest(lineId, new BigDecimal("8"), null)));
        assertThrows(BusinessException.class, () -> service.review(submissionId, invalid));
        OwnerReviewRequest valid = new OwnerReviewRequest("CONFIRMED", "业主代表", "核定", LocalDate.now(), LocalDate.now().plusDays(30), BigDecimal.ZERO, BigDecimal.ZERO, 1,
                List.of(new OwnerReviewLineRequest(lineId, new BigDecimal("8"), "业主核减2m3")));
        long firstSettlement = id((java.util.Map<?,?>) service.review(submissionId, valid).get("settlement"));
        long secondSettlement = id((java.util.Map<?,?>) service.review(submissionId, valid).get("settlement"));
        assertEquals(firstSettlement, secondSettlement);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM owner_settlement WHERE owner_submission_id=?", Integer.class, submissionId));
    }

    @Test
    void ownerReturnCreatesNewRevisionAndInactiveProjectBlocksNewPeriod() {
        long measurementId = internallyApprovedMeasurement("2026-10", new BigDecimal("10"));
        long first = id(service.submitToOwner(measurementId, new OwnerSubmissionRequest("OWNER-REPORT-R1", 1, null)));
        service.review(first, new OwnerReviewRequest("RETURNED", "业主代表", "签章页缺失", null, null, null, null, null, List.of()));
        long second = id(service.submitToOwner(measurementId, new OwnerSubmissionRequest("OWNER-REPORT-R2", 1, null)));
        assertEquals(2, jdbc.queryForObject("SELECT revision_no FROM owner_measurement_submission WHERE id=?", Integer.class, second));
        jdbc.update("UPDATE pm_project SET status='SUSPENDED' WHERE id=?", PROJECT);
        assertThrows(BusinessException.class, () -> createPeriod("2026-11"));
    }

    private long internallyApprovedMeasurement(String period, BigDecimal quantity) {
        long periodId = createPeriod(period);
        int month = Integer.parseInt(period.substring(5));
        long measurementId = id(service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, periodId, LocalDate.of(2026, month, 10), 1,
                List.of(new MeasurementLineRequest(ITEM, null, quantity, 1)), null)));
        jdbc.update("UPDATE production_measurement SET status='PENDING',approval_status='PENDING' WHERE id=?", measurementId);
        service.onApproved(measurementId);
        return measurementId;
    }

    private long createPeriod(String period) {
        int month = Integer.parseInt(period.substring(5));
        return id(service.createPeriod(new PeriodRequest(PROJECT, CONTRACT, period, period + "月度计量",
                LocalDate.of(2026, month, 1), LocalDate.of(2026, month, 20), LocalDate.of(2026, month, 25), null)));
    }

    private void approveAll(String businessType, long businessId) {
        WfInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>().eq(WfInstance::getBusinessType, businessType).eq(WfInstance::getBusinessId, businessId));
        assertNotNull(instance);
        for (int i = 0; i < 10; i++) {
            List<WfTask> pending = taskMapper.selectList(new LambdaQueryWrapper<WfTask>().eq(WfTask::getInstanceId, instance.getId()).eq(WfTask::getTaskStatus, "PENDING"));
            if (pending.isEmpty()) break;
            for (WfTask task : pending) workflowEngine.approve(task.getId(), 1L, "admin", "同意", "measure-it-" + UUID.randomUUID());
        }
        assertEquals("APPROVED", instanceMapper.selectById(instance.getId()).getInstanceStatus());
    }

    private long id(java.util.Map<?,?> row) { return ((Number) row.get("id")).longValue(); }

    private void cleanup() {
        jdbc.update("DELETE FROM account_receivable WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM owner_settlement WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM owner_measurement_review_line WHERE submission_id IN(SELECT id FROM owner_measurement_submission WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM owner_measurement_submission WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM production_measurement_line WHERE measurement_id IN(SELECT id FROM production_measurement WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM production_measurement WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM measurement_period WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('PRODUCTION_MEASUREMENT','OWNER_SETTLEMENT'))", PROJECT);
        jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('PRODUCTION_MEASUREMENT','OWNER_SETTLEMENT'))", PROJECT);
        jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('PRODUCTION_MEASUREMENT','OWNER_SETTLEMENT'))", PROJECT);
        jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('PRODUCTION_MEASUREMENT','OWNER_SETTLEMENT'))", PROJECT);
        jdbc.update("DELETE FROM wf_instance WHERE project_id=? AND business_type IN('PRODUCTION_MEASUREMENT','OWNER_SETTLEMENT')", PROJECT);
        jdbc.update("DELETE FROM ct_contract_change WHERE id=?", CHANGE);
        jdbc.update("DELETE FROM ct_contract_item WHERE id=?", ITEM);
        jdbc.update("DELETE FROM ct_contract WHERE id=?", CONTRACT);
        jdbc.update("DELETE FROM md_partner WHERE id=?", CUSTOMER);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
    }
}
