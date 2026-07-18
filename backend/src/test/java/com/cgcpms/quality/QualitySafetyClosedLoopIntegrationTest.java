package com.cgcpms.quality;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.quality.dto.QualitySafetyModels.*;
import com.cgcpms.quality.entity.*;
import com.cgcpms.quality.service.QualitySafetyService;
import io.jsonwebtoken.Jwts;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class QualitySafetyClosedLoopIntegrationTest {
    private static final long PROJECT = 99188001L;
    private static final long PARTNER = 99188002L;
    private static final long CONTRACT = 99188003L;
    private static final long SUBJECT = 99188004L;
    private static final long MAPPING_VERSION = 99188005L;
    private static final long ASSIGNMENT_RULE = 99188006L;
    private static final AtomicLong FILE_ID = new AtomicLong(99188100L);

    @Autowired QualitySafetyService service;
    @Autowired BusinessObjectAuthorizer fileAuthorizer;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        asUser(1L);
        cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) SELECT 1,0,'admin','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','系统管理员','ENABLE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,contract_amount,target_cost,project_manager_id,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'QS-IT','质量安全闭环测试项目',100000,80000,1,'ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'QS-SUP','测试供应商','SUPPLIER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", PARTNER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'QS-PO','测试采购合同','PURCHASE',?,?,10000,10000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CONTRACT, PROJECT, PARTNER, PARTNER);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'QS-COST','质量安全返工','质量安全','COST',1,1,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", SUBJECT);
        jdbc.update("INSERT INTO cost_subject_mapping_version(id,tenant_id,version_code,version_name,status,effective_date,created_by) VALUES(?,0,'QS-TEST-V2','质量安全测试映射','ACTIVE',CURRENT_DATE,1)", MAPPING_VERSION);
        jdbc.update("INSERT INTO cost_subject_assignment_rule(id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,cost_subject_id,priority,status,effective_from,created_by) VALUES(?,0,?,'QS-REWORK','QUALITY_SAFETY_CONSEQUENCE','SAFETY',NULL,?,1,'ACTIVE',CURRENT_DATE,1)", ASSIGNMENT_RULE, MAPPING_VERSION, SUBJECT);
    }

    @AfterEach
    void teardown() {
        cleanup();
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void closesPlanInspectionIssueRectificationReinspectionCostAndPartnerEvaluation() {
        QualityInspectionPlan plan = service.createPlan(planCommand("QS-PLAN-001"));
        assertEquals("ACTIVE", service.activatePlan(plan.getId()).getStatus());

        QualityInspectionRecord inspection = service.createInspection(new InspectionCommand(
                plan.getId(), "QS-CHK-001", LocalDate.now(), "A区主体结构", 1L, "模板支撑专项检查", null));
        QualitySafetyIssue issue = service.createIssue(inspection.getId(), new IssueCommand(
                inspection.getId(), "模板支撑", "HIGH", "立杆间距超标", "局部立杆间距超过方案要求",
                "PARTNER", PARTNER, 1L, LocalDate.now().plusDays(7), null));
        evidence("QS_INSPECTION", inspection.getId(), "INSPECTION_EVIDENCE");
        evidence("QS_ISSUE", issue.getId(), "ISSUE_EVIDENCE");
        assertEquals("ISSUES", service.submitInspection(inspection.getId()).getConclusion());
        assertEquals("RECTIFYING", service.listIssues(PROJECT, null).get(0).getStatus());

        QualityRectification rectification = service.createRectification(new RectificationCommand(
                issue.getId(), "按专项方案重新布置立杆并由班组自检", 1L, LocalDate.now().plusDays(5), null));
        evidence("QS_RECTIFICATION", rectification.getId(), "RECTIFICATION_EVIDENCE");
        assertEquals("SUBMITTED", service.submitRectification(rectification.getId()).getStatus());

        asUser(2L);
        evidence("QS_RECTIFICATION", rectification.getId(), "REINSPECTION_EVIDENCE");
        assertEquals("PASSED", service.reinspect(rectification.getId(),
                new ReinspectionCommand("PASS", "复测间距符合方案，现场清理完成")).getStatus());
        assertEquals("CLOSED", service.listIssues(PROJECT, null).get(0).getStatus());

        QualityConsequence consequence = service.createConsequence(new ConsequenceCommand(
                issue.getId(), PARTNER, CONTRACT, "QS-C-001", "BOTH",
                new BigDecimal("100.00"), new BigDecimal("500.00"), new BigDecimal("60.00"),
                "本次高等级质量问题扣减履约评分", null));
        consequence = service.postConsequence(consequence.getId());
        assertEquals("POSTED", consequence.getStatus());
        assertNotNull(consequence.getCostItemId());
        assertEquals(SUBJECT, consequence.getCostSubjectId());
        assertNotNull(consequence.getEvaluationId());
        assertEquals(0, new BigDecimal("500.00").compareTo(jdbc.queryForObject(
                "SELECT amount FROM cost_item WHERE id=?", BigDecimal.class, consequence.getCostItemId())));
        assertEquals(0, new BigDecimal("60.00").compareTo(jdbc.queryForObject(
                "SELECT score FROM qs_partner_evaluation WHERE id=?", BigDecimal.class, consequence.getEvaluationId())));

        Trace trace = service.trace(issue.getId());
        assertEquals(plan.getId(), trace.plan().getId());
        assertEquals(inspection.getId(), trace.inspection().getId());
        assertEquals(issue.getId(), trace.issue().getId());
        assertEquals(1, trace.rectifications().size());
        assertEquals(consequence.getId(), trace.consequence().getId());
        assertEquals(consequence.getCostItemId(), trace.costItem().getId());
        assertEquals("COMPLETED", service.completePlan(plan.getId()).getStatus());
    }

    @Test
    void rejectsMissingEvidenceDuplicateSubmitAndSelfReinspectionThenSupportsRejectResubmit() {
        QualityInspectionPlan plan = service.activatePlan(service.createPlan(planCommand("QS-PLAN-EDGE")).getId());
        QualityInspectionRecord inspection = service.createInspection(new InspectionCommand(
                plan.getId(), "QS-CHK-EDGE", LocalDate.now(), "B区", 1L, "临边防护检查", null));
        BusinessException missing = assertThrows(BusinessException.class, () -> service.submitInspection(inspection.getId()));
        assertEquals("QS_EVIDENCE_REQUIRED", missing.getCode());

        QualitySafetyIssue issue = service.createIssue(inspection.getId(), new IssueCommand(
                inspection.getId(), "临边防护", "CRITICAL", "防护栏缺失", "作业层临边防护缺失",
                "PARTNER", PARTNER, 1L, LocalDate.now().plusDays(3), null));
        evidence("QS_INSPECTION", inspection.getId(), "INSPECTION_EVIDENCE");
        evidence("QS_ISSUE", issue.getId(), "ISSUE_EVIDENCE");
        service.submitInspection(inspection.getId());
        assertEquals("QS_STATE_IMMUTABLE", assertThrows(BusinessException.class,
                () -> service.submitInspection(inspection.getId())).getCode());

        QualityRectification first = service.createRectification(new RectificationCommand(
                issue.getId(), "恢复临边防护", 1L, LocalDate.now().plusDays(2), null));
        evidence("QS_RECTIFICATION", first.getId(), "RECTIFICATION_EVIDENCE");
        service.submitRectification(first.getId());
        evidence("QS_RECTIFICATION", first.getId(), "REINSPECTION_EVIDENCE");
        assertEquals("QS_REINSPECTION_SEGREGATION_REQUIRED", assertThrows(BusinessException.class,
                () -> service.reinspect(first.getId(), new ReinspectionCommand("PASS", "自验"))).getCode());

        asUser(2L);
        assertEquals("REJECTED", service.reinspect(first.getId(),
                new ReinspectionCommand("REJECT", "立柱固定不牢，退回整改")).getStatus());
        asUser(1L);
        QualityRectification second = service.createRectification(new RectificationCommand(
                issue.getId(), "更换固定件并重新安装防护栏", 1L, LocalDate.now().plusDays(3), null));
        assertEquals(2, second.getRoundNo());
    }

    @Test
    void rejectsNewPlanWhenProjectIsSuspended() {
        jdbc.update("UPDATE pm_project SET status='SUSPENDED' WHERE id=?", PROJECT);
        BusinessException suspended = assertThrows(BusinessException.class,
                () -> service.createPlan(planCommand("QS-PLAN-SUSPENDED")));
        assertEquals("QS_PROJECT_NOT_ACTIVE", suspended.getCode());
    }

    @Test
    void enforcesQualityEvidencePermissionAndImmutableDocumentStages() {
        QualityInspectionPlan plan = service.activatePlan(service.createPlan(planCommand("QS-PLAN-FILE")).getId());
        QualityInspectionRecord inspection = service.createInspection(new InspectionCommand(
                plan.getId(), "QS-CHK-FILE", LocalDate.now(), "C区", 1L, "文件阶段检查", null));
        QualitySafetyIssue issue = service.createIssue(inspection.getId(), new IssueCommand(
                inspection.getId(), "脚手架", "HIGH", "连墙件缺失", "局部连墙件未设置",
                "PARTNER", PARTNER, 1L, LocalDate.now().plusDays(3), null));

        authenticate("quality:safety:inspection:maintain");
        assertDoesNotThrow(() -> fileAuthorizer.checkUploadAccess("QS_INSPECTION", inspection.getId()));
        assertDoesNotThrow(() -> fileAuthorizer.checkVariationDocumentStage(
                "QS_INSPECTION", inspection.getId(), "INSPECTION_EVIDENCE"));
        assertDoesNotThrow(() -> fileAuthorizer.checkVariationDocumentStage(
                "QS_ISSUE", issue.getId(), "ISSUE_EVIDENCE"));
        BusinessException wrongType = assertThrows(BusinessException.class, () -> fileAuthorizer.checkVariationDocumentStage(
                "QS_INSPECTION", inspection.getId(), "REINSPECTION_EVIDENCE"));
        assertEquals("QS_DOCUMENT_STAGE_INVALID", wrongType.getCode());

        evidence("QS_INSPECTION", inspection.getId(), "INSPECTION_EVIDENCE");
        evidence("QS_ISSUE", issue.getId(), "ISSUE_EVIDENCE");
        service.submitInspection(inspection.getId());
        assertEquals("QS_DOCUMENT_STAGE_INVALID", assertThrows(BusinessException.class,
                () -> fileAuthorizer.checkVariationDocumentStage(
                        "QS_INSPECTION", inspection.getId(), "INSPECTION_EVIDENCE")).getCode());
    }

    private PlanCommand planCommand(String code) {
        return new PlanCommand(PROJECT, code, "专项质量安全检查", "SAFETY", "SINGLE",
                LocalDate.now(), LocalDate.now().plusDays(30), 1L, null);
    }

    private void evidence(String businessType, Long businessId, String documentType) {
        long id = FILE_ID.incrementAndGet();
        jdbc.update("INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(0+?,0,?,?,?,'evidence.pdf','evidence.pdf',100,'application/pdf',?,'test','CLEAN',CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",
                id, businessType, documentType, businessId, businessType + "/" + businessId + "/" + id + ".pdf");
    }

    private void asUser(long userId) {
        UserContext.set(Jwts.claims().subject("admin-" + userId).add("userId", userId).add("username", "admin-" + userId)
                .add("tenantId", 0L).add("roleCodes", List.of("ADMIN")).build());
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    private void cleanup() {
        jdbc.update("UPDATE qs_consequence SET evaluation_id=NULL,cost_item_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_partner_evaluation WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_item WHERE project_id=? AND source_type='QUALITY_SAFETY_CONSEQUENCE'", PROJECT);
        jdbc.update("DELETE FROM qs_consequence WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM sys_file WHERE business_type IN('QS_INSPECTION','QS_ISSUE','QS_RECTIFICATION') AND business_id IN (SELECT id FROM qs_inspection_record WHERE project_id=? UNION SELECT id FROM qs_issue WHERE project_id=? UNION SELECT id FROM qs_rectification WHERE project_id=?)", PROJECT, PROJECT, PROJECT);
        jdbc.update("DELETE FROM qs_rectification WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_issue WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_inspection_record WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_inspection_plan WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM ct_contract WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM md_partner WHERE id=?", PARTNER);
        jdbc.update("DELETE FROM cost_subject_assignment_rule WHERE id=?", ASSIGNMENT_RULE);
        jdbc.update("DELETE FROM cost_subject_mapping_version WHERE id=?", MAPPING_VERSION);
        jdbc.update("DELETE FROM cost_subject WHERE id=?", SUBJECT);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
    }
}
