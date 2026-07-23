package com.cgcpms.measurement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.measurement.dto.MeasurementModels.*;
import com.cgcpms.measurement.handler.ProductionMeasurementWorkflowHandler;
import com.cgcpms.measurement.service.ProductionMeasurementService;
import com.cgcpms.revenue.service.RevenueOperationsService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.handler.WorkflowContext;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
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
    @Autowired ProductionMeasurementWorkflowHandler measurementWorkflowHandler;
    @Autowired WfInstanceMapper instanceMapper;
    @Autowired WfTaskMapper taskMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", 1L).add("username", "admin")
                .add("tenantId", 0L).add("roleCodes", List.of("ADMIN")).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) SELECT 1,0,'admin','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','系统管理员','ENABLE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'MEASURE-IT-P','产值计量测试项目','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'MEASURE-IT-CUSTOMER','测试业主','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CUSTOMER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'MEASURE-IT-C','业主总包合同','MAIN',?,?,12000,12000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CONTRACT, PROJECT, CUSTOMER, CUSTOMER);
        jdbc.update("INSERT INTO ct_contract_item(id,tenant_id,contract_id,item_code,item_name,item_spec,unit,quantity,unit_price,amount,tax_rate,tax_amount,amount_without_tax,sort_order,created_at,updated_at,deleted_flag) VALUES(?,0,?,'BOQ-001','混凝土工程','C30','m3',100,10,1000,0,0,1000,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", ITEM, CONTRACT);
        jdbc.update("INSERT INTO ct_contract_change(id,tenant_id,project_id,contract_id,change_code,change_name,change_type,before_amount,change_amount,after_amount,approval_status,effective_flag,cost_generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,'CHG-001','新增临建工程','AMOUNT',10000,200,10200,'APPROVED',1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", CHANGE, PROJECT, CONTRACT);
    }

    @AfterEach
    void teardown() { cleanup(); UserContext.clear(); SecurityContextHolder.clearContext(); }

    @Test
    void fullChainFromBoqAndChangeToOwnerSettlementReceivableAndTrace() {
        long periodId = createPeriod("2026-07");
        var measurement = service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, periodId, LocalDate.of(2026, 7, 15), 1,
                List.of(new MeasurementLineRequest(ITEM, null, new BigDecimal("20"), 1),
                        new MeasurementLineRequest(null, CHANGE, new BigDecimal("0.5"), 1)), "本期完成量"));
        assertTrue(String.valueOf(measurement.get("measure_code")).matches("PM-202607-\\d{3}"));
        long measurementId = id(measurement);
        addMeasurementEvidence(measurementId);
        service.submitMeasurement(measurementId, version("production_measurement", measurementId));
        approveAll("PRODUCTION_MEASUREMENT", measurementId);
        assertEquals("INTERNAL_APPROVED", jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?", String.class, measurementId));

        addCleanFile("PRODUCTION_MEASUREMENT", measurementId, "OWNER_SUBMISSION");
        var submission = service.submitToOwner(measurementId, version("production_measurement", measurementId), new OwnerSubmissionRequest("OWNER-REPORT-001", 999, "业主报量"));
        assertEquals("OMS-" + String.valueOf(measurement.get("measure_code")).substring(3) + "-R1", submission.get("submission_code"));
        long submissionId = id(submission);
        addCleanFile("OWNER_MEASUREMENT_SUBMISSION", submissionId, "OWNER_CONFIRMATION");
        assertEquals("300.00", submission.get("submitted_amount"));
        @SuppressWarnings("unchecked") List<java.util.Map<String,Object>> lines = (List<java.util.Map<String,Object>>) service.submission(submissionId).get("lines");
        assertEquals("10.0000", String.valueOf(lines.get(0).get("unit_price")));
        assertEquals("200.00", String.valueOf(lines.get(0).get("submitted_amount")));
        long itemLine = lines.stream().filter(row -> "BOQ-001".equals(row.get("item_code"))).map(row -> ((Number) row.get("measurement_line_id")).longValue()).findFirst().orElseThrow();
        long changeLine = lines.stream().filter(row -> "CHG-001".equals(row.get("item_code"))).map(row -> ((Number) row.get("measurement_line_id")).longValue()).findFirst().orElseThrow();
        var reviewed = service.review(submissionId, version("owner_measurement_submission", submissionId), new OwnerReviewRequest("CONFIRMED", "业主代表", "核定通过",
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
        assertEquals("12000.00", cast(trace.get("contract")).get("current_amount"));
        assertEquals("280.00", cast(trace.get("settlement")).get("gross_amount"));
        assertTrue(((List<?>) trace.get("receivables")).stream()
                .map(ProductionMeasurementClosedLoopIntegrationTest::cast)
                .map(row -> row.get("original_amount"))
                .allMatch(String.class::isInstance));
    }

    @Test
    void measurementCodesUseMonthlyThreeDigitSequence() {
        long firstPeriod = createPeriod("2026-01");
        long secondPeriod = id(service.createPeriod(new PeriodRequest(PROJECT, CONTRACT, "2026-01-B", "2026年1月补充计量",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 20), LocalDate.of(2026, 1, 25), null)));

        var first = service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, firstPeriod, LocalDate.of(2026, 1, 10), 1,
                List.of(new MeasurementLineRequest(ITEM, null, BigDecimal.ONE, 1)), null));
        var second = service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, secondPeriod, LocalDate.of(2026, 1, 11), 1,
                List.of(new MeasurementLineRequest(ITEM, null, BigDecimal.ONE, 1)), null));

        assertEquals("PM-202601-001", first.get("measure_code"));
        assertEquals("PM-202601-002", second.get("measure_code"));
    }

    @Test
    void rejectsOverMeasurementAndMissingEvidence() {
        long periodId = createPeriod("2026-08");
        assertThrows(BusinessException.class, () -> service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, periodId,
                LocalDate.of(2026, 8, 10), 1, List.of(new MeasurementLineRequest(ITEM, null, new BigDecimal("101"), 1)), null)));
        var draft = service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, periodId, LocalDate.of(2026, 8, 10), 1,
                List.of(new MeasurementLineRequest(ITEM, null, new BigDecimal("10"), 0)), null));
        long draftId = id(draft);
        Long lineId = jdbc.queryForObject("SELECT id FROM production_measurement_line WHERE measurement_id=?", Long.class, draftId);
        jdbc.update("UPDATE production_measurement SET attachment_count=99 WHERE id=?", draftId);
        jdbc.update("UPDATE production_measurement_line SET evidence_count=99 WHERE id=?", lineId);
        WfInstance fakeInstance = new WfInstance();
        fakeInstance.setTenantId(0L);
        fakeInstance.setBusinessId(draftId);
        WorkflowContext fakeContext = new WorkflowContext();
        fakeContext.setInstance(fakeInstance);
        BusinessException fakeCounts = assertThrows(BusinessException.class,
                () -> measurementWorkflowHandler.beforeSubmit(fakeContext));
        assertEquals("PRODUCTION_MEASUREMENT_ATTACHMENT_REQUIRED", fakeCounts.getCode());
        addFile("PRODUCTION_MEASUREMENT", draftId + 1, "MEASUREMENT_GENERAL", "CLEAN");
        addFile("PRODUCTION_MEASUREMENT", draftId, "MEASUREMENT_GENERAL", "FAILED");
        BusinessException missingGeneral = assertThrows(BusinessException.class,
                () -> service.submitMeasurement(draftId, version("production_measurement", draftId)));
        assertEquals("PRODUCTION_MEASUREMENT_ATTACHMENT_REQUIRED", missingGeneral.getCode());
        addCleanFile("PRODUCTION_MEASUREMENT", draftId, "MEASUREMENT_GENERAL");
        addFile("OWNER_MEASUREMENT_SUBMISSION", draftId, "ML_" + lineId, "CLEAN");
        addFile("PRODUCTION_MEASUREMENT", draftId, "ML_" + lineId, "FAILED");
        BusinessException missingLine = assertThrows(BusinessException.class,
                () -> service.submitMeasurement(draftId, version("production_measurement", draftId)));
        assertEquals("PRODUCTION_MEASUREMENT_LINE_EVIDENCE_REQUIRED", missingLine.getCode());
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?", String.class, id(draft)));
    }

    @Test
    void ownerDeductionRequiresReasonAndDuplicateConfirmationIsIdempotent() {
        long measurementId = internallyApprovedMeasurement("2026-09", new BigDecimal("10"));
        addCleanFile("PRODUCTION_MEASUREMENT", measurementId, "OWNER_SUBMISSION");
        long submissionId = id(service.submitToOwner(measurementId, version("production_measurement", measurementId), new OwnerSubmissionRequest("OWNER-REPORT-002", 1, null)));
        addCleanFile("OWNER_MEASUREMENT_SUBMISSION", submissionId, "OWNER_CONFIRMATION");
        @SuppressWarnings("unchecked") List<java.util.Map<String,Object>> lines = (List<java.util.Map<String,Object>>) service.submission(submissionId).get("lines");
        long lineId = ((Number) lines.get(0).get("measurement_line_id")).longValue();
        OwnerReviewRequest invalid = new OwnerReviewRequest("CONFIRMED", "业主代表", null, LocalDate.now(), LocalDate.now().plusDays(30), BigDecimal.ZERO, BigDecimal.ZERO, 1,
                List.of(new OwnerReviewLineRequest(lineId, new BigDecimal("8"), null)));
        assertThrows(BusinessException.class, () -> service.review(submissionId, version("owner_measurement_submission", submissionId), invalid));
        OwnerReviewRequest valid = new OwnerReviewRequest("CONFIRMED", "业主代表", "核定", LocalDate.now(), LocalDate.now().plusDays(30), BigDecimal.ZERO, BigDecimal.ZERO, 1,
                List.of(new OwnerReviewLineRequest(lineId, new BigDecimal("8"), "业主核减2m3")));
        int reviewVersion = version("owner_measurement_submission", submissionId);
        long firstSettlement = id((java.util.Map<?,?>) service.review(submissionId, reviewVersion, valid).get("settlement"));
        long secondSettlement = id((java.util.Map<?,?>) service.review(submissionId, reviewVersion, valid).get("settlement"));
        assertEquals(firstSettlement, secondSettlement);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM owner_settlement WHERE owner_submission_id=?", Integer.class, submissionId));
    }

    @Test
    void ownerReturnCreatesNewRevisionAndInactiveProjectBlocksNewPeriod() {
        long measurementId = internallyApprovedMeasurement("2026-10", new BigDecimal("10"));
        addCleanFile("PRODUCTION_MEASUREMENT", measurementId, "OWNER_SUBMISSION");
        long first = id(service.submitToOwner(measurementId, version("production_measurement", measurementId), new OwnerSubmissionRequest("OWNER-REPORT-R1", 1, null)));
        service.review(first, version("owner_measurement_submission", first), new OwnerReviewRequest("RETURNED", "业主代表", "签章页缺失", null, null, null, null, null, List.of()));
        long second = id(service.submitToOwner(measurementId, version("production_measurement", measurementId), new OwnerSubmissionRequest("OWNER-REPORT-R2", 1, null)));
        assertEquals(2, jdbc.queryForObject("SELECT revision_no FROM owner_measurement_submission WHERE id=?", Integer.class, second));
        assertTrue(jdbc.queryForObject("SELECT submission_code FROM owner_measurement_submission WHERE id=?", String.class, second).matches("OMS-202610-\\d{3}-R2"));
        jdbc.update("UPDATE pm_project SET status='SUSPENDED' WHERE id=?", PROJECT);
        assertThrows(BusinessException.class, () -> createPeriod("2026-11"));
    }

    @Test
    void rejectedMeasurementResubmitsSameInstanceAndGenericRouteWritesNothing() {
        long periodId = createPeriod("2026-12");
        long measurementId = id(service.createMeasurement(new MeasurementRequest(PROJECT, CONTRACT, periodId,
                LocalDate.of(2026, 12, 10), 999,
                List.of(new MeasurementLineRequest(ITEM, null, new BigDecimal("10"), 999)), null)));
        addMeasurementEvidence(measurementId);
        service.submitMeasurement(measurementId, 0);
        WfInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, "PRODUCTION_MEASUREMENT")
                .eq(WfInstance::getBusinessId, measurementId));
        Long taskId = jdbc.queryForObject("SELECT id FROM wf_task WHERE instance_id=? AND task_status='PENDING' ORDER BY id LIMIT 1", Long.class, instance.getId());
        workflowEngine.reject(taskId, 1L, "admin", "退回补充", "measure-reject-" + UUID.randomUUID());
        assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?", String.class, measurementId));
        int round = instanceMapper.selectById(instance.getId()).getCurrentRound();
        BusinessException generic = assertThrows(BusinessException.class,
                () -> workflowEngine.resubmit(instance.getId(), 1L, "admin"));
        assertEquals("PRODUCTION_MEASUREMENT_DEDICATED_SUBMIT_REQUIRED", generic.getCode());
        service.submitMeasurement(measurementId, version("production_measurement", measurementId));
        assertEquals(instance.getId(), instanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, "PRODUCTION_MEASUREMENT")
                .eq(WfInstance::getBusinessId, measurementId)).getId());
        assertEquals(round + 1, instanceMapper.selectById(instance.getId()).getCurrentRound());
    }

    @Test
    void reportWindowUsesPeriodOverlapAndServerMeasurementDate() {
        long measurementId = internallyApprovedMeasurement("2026-06", new BigDecimal("10"));
        addCleanFile("PRODUCTION_MEASUREMENT", measurementId, "OWNER_SUBMISSION");
        service.submitToOwner(measurementId, version("production_measurement", measurementId),
                new OwnerSubmissionRequest("OWNER-WINDOW-001", 999, null));

        assertEquals(1, service.periods(PROJECT, CONTRACT, LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 20)).size());
        assertTrue(service.periods(PROJECT, CONTRACT, LocalDate.of(2026, 6, 21), LocalDate.of(2026, 6, 30)).isEmpty());
        assertEquals(1, service.measurements(PROJECT, null, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10)).size());
        assertTrue(service.measurements(PROJECT, null, LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 30)).isEmpty());
        assertEquals(1, service.submissions(PROJECT, null, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10)).size());
        assertTrue(service.submissions(PROJECT, null, LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 30)).isEmpty());
        assertTrue(service.periods(null, CONTRACT, LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 20))
                .stream().anyMatch(row -> PROJECT == ((Number) row.get("project_id")).longValue()));
        assertTrue(service.measurements(null, null, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10))
                .stream().anyMatch(row -> PROJECT == ((Number) row.get("project_id")).longValue()));
        assertTrue(service.submissions(null, null, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10))
                .stream().anyMatch(row -> PROJECT == ((Number) row.get("project_id")).longValue()));
        UserContext.set(Jwts.claims().subject("restricted").add("userId", 2L).add("username", "restricted")
                .add("tenantId", 0L).add("roleCodes", List.of()).build());
        assertTrue(service.measurements(null, null, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10)).isEmpty());
        BusinessException invalid = assertThrows(BusinessException.class, () ->
                service.measurements(PROJECT, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 30)));
        assertEquals("MEASUREMENT_REPORT_DATE_INVALID", invalid.getCode());
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

    private void addMeasurementEvidence(long measurementId) {
        addCleanFile("PRODUCTION_MEASUREMENT", measurementId, "MEASUREMENT_GENERAL");
        for (Long lineId : jdbc.queryForList("SELECT id FROM production_measurement_line WHERE measurement_id=?", Long.class, measurementId)) {
            addCleanFile("PRODUCTION_MEASUREMENT", measurementId, "ML_" + lineId);
        }
    }

    private void addCleanFile(String businessType, long businessId, String documentType) {
        addFile(businessType, businessId, documentType, "CLEAN");
    }

    private void addFile(String businessType, long businessId, String documentType, String scanStatus) {
        long fileId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("""
                INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,
                  content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,?,?,100,'application/pdf',?,'test',?,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, fileId, businessType, documentType, businessId, fileId + ".pdf", "evidence-" + fileId + ".pdf",
                businessType + "/" + businessId + "/" + fileId, scanStatus);
    }

    private int version(String table, long id) {
        return jdbc.queryForObject("SELECT version FROM " + table + " WHERE id=?", Integer.class, id);
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
    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Object value) { return (Map<String, Object>) value; }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_file WHERE (business_type='PRODUCTION_MEASUREMENT' AND business_id IN(SELECT id FROM production_measurement WHERE project_id=?)) OR (business_type='OWNER_MEASUREMENT_SUBMISSION' AND business_id IN(SELECT id FROM owner_measurement_submission WHERE project_id=?))", PROJECT, PROJECT);
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
