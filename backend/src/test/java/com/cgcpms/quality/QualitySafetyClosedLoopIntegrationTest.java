package com.cgcpms.quality;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.quality.dto.QualitySafetyModels.*;
import com.cgcpms.quality.entity.*;
import com.cgcpms.quality.service.QualitySafetyService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class QualitySafetyClosedLoopIntegrationTest {
    private static final long PROJECT = 99188001L;
    private static final long PARTNER = 99188002L;
    private static final long CONTRACT = 99188003L;
    private static final long SUBJECT = 99188004L;
    private static final long MAPPING_VERSION = 99188005L;
    private static final long ASSIGNMENT_RULE = 99188006L;
    private static final long CONTRACT_COUNTERPARTY = 99188007L;
    private static final long OTHER_PROJECT = 99188008L;
    private static final long OTHER_PARTNER_A = 99188009L;
    private static final long OTHER_PARTNER_B = 99188010L;
    private static final long OTHER_PROJECT_CONTRACT = 99188011L;
    private static final long UNRELATED_CONTRACT = 99188012L;
    private static final long CROSS_TENANT_PROJECT = 99188013L;
    private static final long CROSS_TENANT_PARTNER_A = 99188014L;
    private static final long CROSS_TENANT_PARTNER_B = 99188015L;
    private static final long CROSS_TENANT_CONTRACT = 99188016L;
    private static final long OUTSIDE_USER = 99188017L;
    private static final long DISABLED_USER = 99188018L;
    private static final long CROSS_TENANT_USER = 99188019L;
    private static final long MEMBER_ONE = 99188020L;
    private static final long MEMBER_TWO = 99188021L;
    private static final long MEMBER_DISABLED = 99188022L;
    private static final long SCHEDULE = 99188023L;
    private static final long WBS = 99188024L;
    private static final long OTHER_SCHEDULE = 99188025L;
    private static final long OTHER_WBS = 99188026L;
    private static final long SAFETY_LEAD_ROLE = 99188910L;
    private static final long RECTIFICATION_TEMPLATE = 99188920L;
    private static final long CONSEQUENCE_TEMPLATE = 99188921L;
    private static final long AMBIGUOUS_SUBJECT = 99188922L;
    private static final long AMBIGUOUS_RULE = 99188923L;
    private static final AtomicLong FILE_ID = new AtomicLong(99188100L);

    @Autowired QualitySafetyService service;
    @Autowired BusinessObjectAuthorizer fileAuthorizer;
    @Autowired WorkflowEngine workflowEngine;
    @Autowired WfInstanceMapper instanceMapper;
    @Autowired WfTaskMapper taskMapper;
    @Autowired JdbcTemplate jdbc;
    private String projectManagerDataScope;

    @BeforeEach
    void setup() {
        asUser(1L);
        cleanup();
        ensureWorkflowFixture();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) SELECT 1,0,'admin','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','系统管理员','ENABLE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) SELECT 2,0,'qs-reviewer-2','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','质量复检人','ENABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=2)");
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) VALUES(?,0,'qs-outsider','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','非项目成员','ENABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", OUTSIDE_USER);
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) VALUES(?,0,'qs-disabled','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','停用项目成员','DISABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", DISABLED_USER);
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) VALUES(?,1,'qs-cross-tenant','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','跨租户用户','ENABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CROSS_TENANT_USER);
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,contract_amount,target_cost,project_manager_id,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'QS-IT','质量安全闭环测试项目',100000,80000,1,'ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,contract_amount,target_cost,project_manager_id,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'QS-OTHER','其他项目',100000,80000,1,'ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", OTHER_PROJECT);
        insertWbs(SCHEDULE, WBS, PROJECT, "QS-WBS");
        insertWbs(OTHER_SCHEDULE, OTHER_WBS, OTHER_PROJECT, "QS-OTHER-WBS");
        jdbc.update("INSERT INTO pm_project_member(id,tenant_id,project_id,user_id,role_code,status,created_at,updated_at,created_by,updated_by,deleted_flag) VALUES(?,0,?,1,'PROJECT_MANAGER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1,0)", MEMBER_ONE, PROJECT);
        jdbc.update("INSERT INTO pm_project_member(id,tenant_id,project_id,user_id,role_code,status,created_at,updated_at,created_by,updated_by,deleted_flag) VALUES(?,0,?,2,'QUALITY_REVIEWER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1,0)", MEMBER_TWO, PROJECT);
        jdbc.update("INSERT INTO pm_project_member(id,tenant_id,project_id,user_id,role_code,status,created_at,updated_at,created_by,updated_by,deleted_flag) VALUES(?,0,?,?,'QUALITY_OWNER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1,0)", MEMBER_DISABLED, PROJECT, DISABLED_USER);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'QS-SUP','测试供应商','SUPPLIER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", PARTNER);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'QS-OWNER','测试合同相对方','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CONTRACT_COUNTERPARTY);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'QS-PO','测试采购合同','PURCHASE',?,?,10000,10000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CONTRACT, PROJECT, CONTRACT_COUNTERPARTY, PARTNER);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'QS-COST','质量安全返工','质量安全','COST',1,1,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", SUBJECT);
        jdbc.update("INSERT INTO cost_subject_mapping_version(id,tenant_id,version_code,version_name,status,effective_date,created_by) VALUES(?,0,'QS-TEST-V2','质量安全测试映射','ACTIVE',CURRENT_DATE,1)", MAPPING_VERSION);
        jdbc.update("INSERT INTO cost_subject_assignment_rule(id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,cost_subject_id,priority,status,effective_from,created_by) VALUES(?,0,?,'QS-REWORK','QUALITY_SAFETY_CONSEQUENCE','SAFETY',NULL,?,1,'ACTIVE',CURRENT_DATE,1)", ASSIGNMENT_RULE, MAPPING_VERSION, SUBJECT);
        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) SELECT 99188901,0,2,r.id FROM sys_role r WHERE r.tenant_id=0 AND r.role_code='SAFETY_LEAD' AND r.deleted_flag=0 AND NOT EXISTS(SELECT 1 FROM sys_user_role ur WHERE ur.tenant_id=0 AND ur.user_id=2 AND ur.role_id=r.id)");
        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) SELECT 99188902,0,1,r.id FROM sys_role r WHERE r.tenant_id=0 AND r.role_code='PROJECT_MANAGER' AND r.deleted_flag=0 AND NOT EXISTS(SELECT 1 FROM sys_user_role ur WHERE ur.tenant_id=0 AND ur.user_id=1 AND ur.role_id=r.id)");
        authenticate("ROLE_ADMIN");
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
        assertTrue(plan.getPlanCode().matches("QPL-\\d{8}-\\d{3}"));
        assertNotEquals("QS-PLAN-001", plan.getPlanCode());
        assertEquals("ACTIVE", service.activatePlan(plan.getId()).getStatus());

        QualityInspectionRecord inspection = service.createInspection(new InspectionCommand(
                plan.getId(), WBS, "QS-CHK-001", LocalDate.now(), "A区主体结构", 1L, "模板支撑专项检查", null));
        assertTrue(inspection.getInspectionCode().matches("QIN-\\d{8}-\\d{3}"));
        assertNotEquals("QS-CHK-001", inspection.getInspectionCode());
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
        rectification = service.submitRectification(rectification.getId());
        assertEquals("SUBMITTED", rectification.getStatus());
        assertNotNull(rectification.getApprovalInstanceId());

        asUser(2L);
        evidence("QS_RECTIFICATION", rectification.getId(), "REINSPECTION_EVIDENCE");
        assertEquals("SUBMITTED", service.reinspect(rectification.getId(),
                new ReinspectionCommand("PASS", "复测间距符合方案，现场清理完成")).getStatus());
        assertEquals("PENDING_REINSPECTION", service.listIssues(PROJECT, null).get(0).getStatus());
        approveAll("QS_RECTIFICATION", rectification.getId());
        assertEquals("PASSED", service.trace(issue.getId()).rectifications().get(0).getStatus());
        assertEquals("CLOSED", service.listIssues(PROJECT, null).get(0).getStatus());

        BusinessException missingContract = assertThrows(BusinessException.class,
                () -> service.createConsequence(consequenceCommand(issue.getId(), null, "QS-C-MISSING")));
        assertEquals("QS_CONTRACT_REQUIRED", missingContract.getCode());

        insertContractValidationFixtures();
        BusinessException wrongProject = assertThrows(BusinessException.class,
                () -> service.createConsequence(consequenceCommand(
                        issue.getId(), OTHER_PROJECT_CONTRACT, "QS-C-WRONG-PROJECT")));
        assertEquals("QS_CONTRACT_PROJECT_MISMATCH", wrongProject.getCode());

        BusinessException wrongPartner = assertThrows(BusinessException.class,
                () -> service.createConsequence(consequenceCommand(
                        issue.getId(), UNRELATED_CONTRACT, "QS-C-WRONG-PARTNER")));
        assertEquals("QS_CONTRACT_PARTNER_MISMATCH", wrongPartner.getCode());

        BusinessException crossTenant = assertThrows(BusinessException.class,
                () -> service.createConsequence(consequenceCommand(
                        issue.getId(), CROSS_TENANT_CONTRACT, "QS-C-CROSS-TENANT")));
        assertEquals("QS_CONTRACT_NOT_FOUND", crossTenant.getCode());

        QualityConsequence consequence = service.createConsequence(new ConsequenceCommand(
                issue.getId(), PARTNER, CONTRACT, "QS-C-001", "BOTH",
                new BigDecimal("100.00"), new BigDecimal("500.00"), new BigDecimal("60.00"),
                "本次高等级质量问题扣减履约评分", null));
        assertTrue(consequence.getConsequenceCode().matches("QCO-\\d{8}-\\d{3}"));
        assertNotEquals("QS-C-001", consequence.getConsequenceCode());
        Long consequenceId = consequence.getId();
        assertEquals("WORKFLOW_REQUIRED", assertThrows(BusinessException.class,
                () -> service.postConsequence(consequenceId)).getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cost_item WHERE source_type='QUALITY_SAFETY_CONSEQUENCE' AND source_id=?", Integer.class, consequenceId));

        jdbc.update("UPDATE cost_subject_assignment_rule SET status='RETIRED' WHERE id=?", ASSIGNMENT_RULE);
        assertEquals("COST_SUBJECT_UNCLASSIFIED", assertThrows(BusinessException.class,
                () -> service.submitConsequence(consequenceId)).getCode());
        jdbc.update("UPDATE cost_subject_assignment_rule SET status='ACTIVE' WHERE id=?", ASSIGNMENT_RULE);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'QS-COST-AMBIGUOUS','质量安全返工歧义科目','质量安全','COST',1,2,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", AMBIGUOUS_SUBJECT);
        jdbc.update("INSERT INTO cost_subject_assignment_rule(id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,cost_subject_id,priority,status,effective_from,created_by) VALUES(?,0,?,'QS-REWORK-AMBIGUOUS','QUALITY_SAFETY_CONSEQUENCE','SAFETY',NULL,?,1,'ACTIVE',CURRENT_DATE,1)", AMBIGUOUS_RULE, MAPPING_VERSION, AMBIGUOUS_SUBJECT);
        assertEquals("COST_SUBJECT_RULE_AMBIGUOUS", assertThrows(BusinessException.class,
                () -> service.submitConsequence(consequenceId)).getCode());
        assertEquals("DRAFT", service.trace(issue.getId()).consequence().getStatus());
        assertNull(service.trace(issue.getId()).consequence().getCostSubjectId());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM wf_instance WHERE business_type='QS_CONSEQUENCE' AND business_id=?",
                Integer.class, consequenceId));
        jdbc.update("DELETE FROM cost_subject_assignment_rule WHERE id=?", AMBIGUOUS_RULE);
        jdbc.update("DELETE FROM cost_subject WHERE id=?", AMBIGUOUS_SUBJECT);

        consequence = service.submitConsequence(consequenceId);
        assertEquals("SUBMITTED", consequence.getStatus());
        assertNotNull(consequence.getApprovalInstanceId());
        assertEquals(SUBJECT, consequence.getCostSubjectId());
        jdbc.update("UPDATE cost_subject_assignment_rule SET status='RETIRED' WHERE id=?", ASSIGNMENT_RULE);
        rejectCurrent("QS_CONSEQUENCE", consequence.getId());
        asUser(1L);
        consequence = service.submitConsequence(consequence.getId());
        assertEquals("SUBMITTED", consequence.getStatus());
        assertEquals(SUBJECT, consequence.getCostSubjectId());
        approveAll("QS_CONSEQUENCE", consequence.getId());
        consequence = service.trace(issue.getId()).consequence();
        assertEquals("POSTED", consequence.getStatus());
        assertNotNull(consequence.getCostItemId());
        assertEquals(SUBJECT, consequence.getCostSubjectId());
        assertNotNull(consequence.getEvaluationId());
        assertEquals(0, new BigDecimal("500.00").compareTo(jdbc.queryForObject(
                "SELECT amount FROM cost_item WHERE id=?", BigDecimal.class, consequence.getCostItemId())));
        assertEquals(WBS, jdbc.queryForObject(
                "SELECT wbs_task_id FROM cost_item WHERE id=?", Long.class, consequence.getCostItemId()));
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
                plan.getId(), null, "QS-CHK-EDGE", LocalDate.now(), "B区", 1L, "临边防护检查", null));
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
        long firstId = first.getId();
        evidence("QS_RECTIFICATION", firstId, "RECTIFICATION_EVIDENCE");
        service.submitRectification(firstId);
        evidence("QS_RECTIFICATION", firstId, "REINSPECTION_EVIDENCE");
        assertEquals("QS_REINSPECTION_SEGREGATION_REQUIRED", assertThrows(BusinessException.class,
                () -> service.reinspect(firstId, new ReinspectionCommand("PASS", "自验"))).getCode());

        asUser(2L);
        QualityRectification reinspected = service.reinspect(firstId,
                new ReinspectionCommand("PASS", "首轮复验通过，但审批退回"));
        assertEquals("SUBMITTED", reinspected.getStatus());
        LocalDateTime firstReinspectedAt = reinspected.getReinspectedAt();
        assertEquals("QS_CONCURRENT_MODIFICATION", assertThrows(BusinessException.class,
                () -> service.reinspect(firstId,
                        new ReinspectionCommand("REJECT", "重复复验不得覆盖首轮结果"))).getCode());
        QualityRectification afterDuplicate = service.trace(issue.getId()).rectifications().get(0);
        assertEquals(firstReinspectedAt, afterDuplicate.getReinspectedAt());
        assertEquals("PASS：首轮复验通过，但审批退回", afterDuplicate.getReinspectionComment());
        rejectCurrent("QS_RECTIFICATION", firstId);
        assertEquals("REJECTED", service.trace(issue.getId()).rectifications().get(0).getStatus());

        asUser(1L);
        first = service.submitRectification(firstId);
        assertEquals("SUBMITTED", first.getStatus());
        assertNull(first.getReinspectionComment());
        assertNull(first.getReinspectedBy());
        assertNull(first.getReinspectedAt());
        assertFalse(first.getSubmittedAt().isBefore(firstReinspectedAt));

        asUser(2L);
        first = service.reinspect(firstId, new ReinspectionCommand("PASS", "第二轮复验通过"));
        asUser(1L);
        workflowEngine.withdraw(first.getApprovalInstanceId(), 1L, "admin-1");
        assertEquals("WITHDRAWN", service.trace(issue.getId()).rectifications().get(0).getStatus());

        asUser(1L);
        first = service.submitRectification(firstId);
        assertNull(first.getReinspectedAt());
        WfInstance running = instanceMapper.selectById(first.getApprovalInstanceId());
        assertEquals("QS_REINSPECTION_REQUIRED", assertThrows(BusinessException.class,
                () -> service.onRectificationApproved(running)).getCode());

        asUser(2L);
        first = service.reinspect(firstId, new ReinspectionCommand("PASS", "第三轮复验通过"));
        assertFalse(first.getReinspectedAt().isBefore(first.getSubmittedAt()));
        approveAll("QS_RECTIFICATION", firstId);
        assertEquals("PASSED", service.trace(issue.getId()).rectifications().get(0).getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void formOptionsReturnOnlyActiveWbsForTheRequestedProject() {
        Map<String, Object> options = service.formOptions(PROJECT);
        List<Map<String, Object>> wbsTasks = (List<Map<String, Object>>) options.get("wbsTasks");

        assertEquals(1, wbsTasks.size());
        assertEquals(WBS, ((Number) wbsTasks.getFirst().get("id")).longValue());
        assertEquals("QS-WBS", wbsTasks.getFirst().get("taskCode"));
    }

    @Test
    void qualityInspectionRequiresSameProjectWbsWhileSafetyMayOmitIt() {
        QualityInspectionPlan quality = service.activatePlan(service.createPlan(new PlanCommand(
                PROJECT, null, "质量WBS检查", "QUALITY", "SINGLE", LocalDate.now(),
                LocalDate.now().plusDays(3), 1L, null)).getId());
        BusinessException missing = assertThrows(BusinessException.class, () -> service.createInspection(
                new InspectionCommand(quality.getId(), null, null, LocalDate.now(), "质量区", 1L, "缺少WBS", null)));
        assertEquals("PROJECT_WBS_REQUIRED", missing.getCode());
        BusinessException foreign = assertThrows(BusinessException.class, () -> service.createInspection(
                new InspectionCommand(quality.getId(), OTHER_WBS, null, LocalDate.now(), "质量区", 1L, "跨项目WBS", null)));
        assertEquals("PROJECT_WBS_MISMATCH", foreign.getCode());

        QualityInspectionRecord valid = service.createInspection(new InspectionCommand(
                quality.getId(), WBS, null, LocalDate.now(), "质量区", 1L, "同项目WBS", null));
        assertEquals(WBS, valid.getWbsTaskId());
        jdbc.update("UPDATE project_schedule_plan SET status='SUPERSEDED' WHERE id=?", SCHEDULE);
        assertEquals("PROJECT_WBS_MISMATCH", assertThrows(BusinessException.class,
                () -> service.submitInspection(valid.getId())).getCode());
    }

    @Test
    void rejectsNewPlanWhenProjectIsSuspended() {
        jdbc.update("UPDATE pm_project SET status='SUSPENDED' WHERE id=?", PROJECT);
        BusinessException suspended = assertThrows(BusinessException.class,
                () -> service.createPlan(planCommand("QS-PLAN-SUSPENDED")));
        assertEquals("QS_PROJECT_NOT_ACTIVE", suspended.getCode());
    }

    @Test
    void rejectsOutsiderDisabledAndCrossTenantResponsibilityAssignments() {
        BusinessException outsider = assertThrows(BusinessException.class, () -> service.createPlan(new PlanCommand(
                PROJECT, "QS-PLAN-OUTSIDER", "越界责任人检查", "SAFETY", "SINGLE",
                LocalDate.now(), LocalDate.now().plusDays(3), OUTSIDE_USER, null)));
        assertEquals("QS_RESPONSIBLE_PROJECT_MEMBER_INVALID", outsider.getCode());

        QualityInspectionPlan plan = service.activatePlan(service.createPlan(planCommand("QS-PLAN-MEMBER-BOUNDARY")).getId());
        BusinessException disabled = assertThrows(BusinessException.class, () -> service.createInspection(new InspectionCommand(
                plan.getId(), null, "QS-CHK-DISABLED", LocalDate.now(), "边界区", DISABLED_USER, "停用成员边界", null)));
        assertEquals("QS_RESPONSIBLE_PROJECT_MEMBER_INVALID", disabled.getCode());

        QualityInspectionRecord inspection = service.createInspection(new InspectionCommand(
                plan.getId(), null, "QS-CHK-MEMBER-BOUNDARY", LocalDate.now(), "边界区", 1L, "跨租户责任人边界", null));
        BusinessException crossTenant = assertThrows(BusinessException.class, () -> service.createIssue(
                inspection.getId(), new IssueCommand(inspection.getId(), "边界", "HIGH", "责任人越界", "跨租户责任人",
                        "PARTNER", PARTNER, CROSS_TENANT_USER, LocalDate.now().plusDays(2), null)));
        assertEquals("QS_RESPONSIBLE_PROJECT_MEMBER_INVALID", crossTenant.getCode());
    }

    @Test
    void enforcesQualityEvidencePermissionAndImmutableDocumentStages() {
        QualityInspectionPlan plan = service.activatePlan(service.createPlan(planCommand("QS-PLAN-FILE")).getId());
        QualityInspectionRecord inspection = service.createInspection(new InspectionCommand(
                plan.getId(), null, "QS-CHK-FILE", LocalDate.now(), "C区", 1L, "文件阶段检查", null));
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

    @Test
    void separatesRectificationAndReinspectionEvidenceAuthorities() {
        QualityInspectionPlan plan = service.activatePlan(service.createPlan(planCommand("QS-PLAN-SPLIT-FILE")).getId());
        QualityInspectionRecord inspection = service.createInspection(new InspectionCommand(
                plan.getId(), null, "QS-CHK-SPLIT-FILE", LocalDate.now(), "D区", 1L, "整改复检分权", null));
        QualitySafetyIssue issue = service.createIssue(inspection.getId(), new IssueCommand(
                inspection.getId(), "临电", "HIGH", "接地缺失", "配电箱接地缺失",
                "PARTNER", PARTNER, 1L, LocalDate.now().plusDays(3), null));
        evidence("QS_INSPECTION", inspection.getId(), "INSPECTION_EVIDENCE");
        evidence("QS_ISSUE", issue.getId(), "ISSUE_EVIDENCE");
        service.submitInspection(inspection.getId());
        QualityRectification rectification = service.createRectification(new RectificationCommand(
                issue.getId(), "补齐接地并复测", 1L, LocalDate.now().plusDays(2), null));

        authenticate("quality:safety:rectify");
        assertDoesNotThrow(() -> fileAuthorizer.checkVariationDocumentStage(
                "QS_RECTIFICATION", rectification.getId(), "RECTIFICATION_EVIDENCE"));
        assertEquals("FILE_ACCESS_DENIED", assertThrows(BusinessException.class,
                () -> fileAuthorizer.checkVariationDocumentStage(
                        "QS_RECTIFICATION", rectification.getId(), "REINSPECTION_EVIDENCE")).getCode());

        authenticate("quality:safety:reinspect");
        assertEquals("FILE_ACCESS_DENIED", assertThrows(BusinessException.class,
                () -> fileAuthorizer.checkVariationDocumentStage(
                        "QS_RECTIFICATION", rectification.getId(), "RECTIFICATION_EVIDENCE")).getCode());

        authenticate("quality:safety:rectify");
        evidence("QS_RECTIFICATION", rectification.getId(), "RECTIFICATION_EVIDENCE");
        authenticate("quality:rectification:submit");
        service.submitRectification(rectification.getId());

        asUser(1L);
        authenticate("quality:safety:reinspect");
        assertEquals("QS_REINSPECTION_SEGREGATION_REQUIRED", assertThrows(BusinessException.class,
                () -> fileAuthorizer.checkVariationDocumentStage(
                        "QS_RECTIFICATION", rectification.getId(), "REINSPECTION_EVIDENCE")).getCode());

        asUser(2L);
        authenticate("quality:safety:reinspect");
        assertDoesNotThrow(() -> fileAuthorizer.checkVariationDocumentStage(
                "QS_RECTIFICATION", rectification.getId(), "REINSPECTION_EVIDENCE"));
    }

    @Test
    void replaysOfflineIssueAndRectificationRequestsIdempotently() {
        QualityInspectionPlan plan = service.activatePlan(service.createPlan(planCommand("QS-PLAN-OFFLINE")).getId());
        QualityInspectionRecord inspection = service.createInspection(new InspectionCommand(
                plan.getId(), null, "QS-CHK-OFFLINE", LocalDate.now(), "E区", 1L, "离线幂等检查", null));
        IssueCommand issueCommand = new IssueCommand(
                inspection.getId(), "临边防护", "HIGH", "防护栏缺失", "局部防护栏未安装",
                "PARTNER", PARTNER, 1L, LocalDate.now().plusDays(3), null, "issue-replay");

        QualitySafetyIssue issue = service.createIssue(inspection.getId(), issueCommand);
        assertEquals(issue.getId(), service.createIssue(inspection.getId(), issueCommand).getId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM qs_issue WHERE tenant_id=0 AND created_by=1 AND client_request_id='issue-replay'",
                Integer.class));
        IssueCommand changedIssue = new IssueCommand(
                inspection.getId(), "临边防护", "HIGH", "防护栏缺失", "内容已变更",
                "PARTNER", PARTNER, 1L, LocalDate.now().plusDays(3), null, "issue-replay");
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(BusinessException.class,
                () -> service.createIssue(inspection.getId(), changedIssue)).getCode());

        evidence("QS_INSPECTION", inspection.getId(), "INSPECTION_EVIDENCE");
        evidence("QS_ISSUE", issue.getId(), "ISSUE_EVIDENCE");
        service.submitInspection(inspection.getId());
        RectificationCommand rectificationCommand = new RectificationCommand(
                issue.getId(), "补齐防护栏并固定", 1L, LocalDate.now().plusDays(2), null, "rect-replay");

        QualityRectification rectification = service.createRectification(rectificationCommand);
        assertEquals(rectification.getId(), service.createRectification(rectificationCommand).getId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM qs_rectification WHERE tenant_id=0 AND created_by=1 AND client_request_id='rect-replay'",
                Integer.class));
        RectificationCommand changedRectification = new RectificationCommand(
                issue.getId(), "整改内容已变更", 1L, LocalDate.now().plusDays(2), null, "rect-replay");
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(BusinessException.class,
                () -> service.createRectification(changedRectification)).getCode());
    }

    @Test
    void issueCodeStopsAtThreeDigitCapacity() {
        QualityInspectionPlan plan = service.activatePlan(service.createPlan(planCommand("QS-PLAN-CAPACITY")).getId());
        QualityInspectionRecord inspection = service.createInspection(new InspectionCommand(
                plan.getId(), null, "QS-CHK-CAPACITY", LocalDate.now(), "F区", 1L, "编号容量检查", null));
        List<Object[]> rows = IntStream.rangeClosed(1, 998)
                .mapToObj(sequence -> new Object[]{
                        99200000L + sequence, plan.getId(), inspection.getId(), PROJECT,
                        inspection.getInspectionCode() + "-ISS-" + String.format("%03d", sequence), PARTNER
                })
                .toList();
        jdbc.batchUpdate("""
                INSERT INTO qs_issue(
                    id,tenant_id,plan_id,inspection_id,project_id,issue_code,issue_type,category,severity,
                    title,description,responsible_kind,responsible_partner_id,responsible_user_id,due_date,
                    status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,?, 'SAFETY','CAPACITY','LOW','容量检查','容量检查',
                       'PARTNER',?,1,CURRENT_DATE,'OPEN',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, rows);

        IssueCommand command = new IssueCommand(
                inspection.getId(), "容量检查", "LOW", "第999个问题", "三位子序号边界",
                "PARTNER", PARTNER, 1L, LocalDate.now().plusDays(1), null);
        QualitySafetyIssue last = service.createIssue(inspection.getId(), command);
        assertTrue(last.getIssueCode().endsWith("-ISS-999"));

        BusinessException exhausted = assertThrows(BusinessException.class,
                () -> service.createIssue(inspection.getId(), command));
        assertEquals("BUSINESS_CODE_SEQUENCE_EXHAUSTED", exhausted.getCode());
        assertEquals("该检查单问题编号已达到999条，请联系管理员", exhausted.getMessage());
    }

    private PlanCommand planCommand(String code) {
        return new PlanCommand(PROJECT, code, "专项质量安全检查", "SAFETY", "SINGLE",
                LocalDate.now(), LocalDate.now().plusDays(30), 1L, null);
    }

    private ConsequenceCommand consequenceCommand(Long issueId, Long contractId, String code) {
        return new ConsequenceCommand(issueId, PARTNER, contractId, code, "NONE",
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("80.00"),
                "合同引用边界测试", null);
    }

    private void insertContractValidationFixtures() {
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'QS-OTHER-A','无关甲方','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", OTHER_PARTNER_A);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'QS-OTHER-B','无关乙方','SUPPLIER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", OTHER_PARTNER_B);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'QS-OTHER-PROJECT-CT','其他项目合同','PURCHASE',?,?,10000,10000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", OTHER_PROJECT_CONTRACT, OTHER_PROJECT, CONTRACT_COUNTERPARTY, PARTNER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'QS-UNRELATED-CT','无关合作方合同','PURCHASE',?,?,10000,10000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", UNRELATED_CONTRACT, PROJECT, OTHER_PARTNER_A, OTHER_PARTNER_B);

        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,contract_amount,target_cost,project_manager_id,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,1,'QS-CROSS-TENANT','跨租户项目',100000,80000,1,'ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", CROSS_TENANT_PROJECT);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,1,'QS-CROSS-A','跨租户甲方','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CROSS_TENANT_PARTNER_A);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,1,'QS-CROSS-B','跨租户乙方','SUPPLIER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CROSS_TENANT_PARTNER_B);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,1,?,'QS-CROSS-TENANT-CT','跨租户合同','PURCHASE',?,?,10000,10000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CROSS_TENANT_CONTRACT, CROSS_TENANT_PROJECT, CROSS_TENANT_PARTNER_A, CROSS_TENANT_PARTNER_B);
    }

    private void evidence(String businessType, Long businessId, String documentType) {
        long id = FILE_ID.incrementAndGet();
        jdbc.update("INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(0+?,0,?,?,?,'evidence.pdf','evidence.pdf',100,'application/pdf',?,'test','CLEAN',CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",
                id, businessType, documentType, businessId, businessType + "/" + businessId + "/" + id + ".pdf");
    }

    private void insertWbs(long scheduleId, long wbsId, long projectId, String code) {
        jdbc.update("INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,'BASELINE',1,CURRENT_DATE,DATEADD('DAY',30,CURRENT_DATE),'ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", scheduleId, projectId, code + "-SP", code + "基线");
        jdbc.update("INSERT INTO project_wbs_task(id,tenant_id,project_id,schedule_plan_id,task_code,task_name,planned_start_date,planned_end_date,weight_percent,actual_quantity,actual_progress,status,sort_order,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,?,CURRENT_DATE,DATEADD('DAY',30,CURRENT_DATE),100,0,0,'NOT_STARTED',1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", wbsId, projectId, scheduleId, code, code + "任务");
    }

    private void approveAll(String businessType, long businessId) {
        WfInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, businessType).eq(WfInstance::getBusinessId, businessId));
        assertNotNull(instance);
        for (int i = 0; i < 10; i++) {
            List<WfTask> pending = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getInstanceId, instance.getId()).eq(WfTask::getTaskStatus, "PENDING"));
            if (pending.isEmpty()) break;
            for (WfTask task : pending) {
                asUser(task.getApproverId());
                authenticate("ROLE_ADMIN");
                workflowEngine.approve(task.getId(), task.getApproverId(), "qs-approver", "同意",
                        "qs-it-" + UUID.randomUUID());
            }
        }
        assertEquals("APPROVED", instanceMapper.selectById(instance.getId()).getInstanceStatus());
    }

    private void rejectCurrent(String businessType, long businessId) {
        WfInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, businessType).eq(WfInstance::getBusinessId, businessId));
        WfTask task = taskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getInstanceId, instance.getId()).eq(WfTask::getTaskStatus, "PENDING"));
        asUser(task.getApproverId());
        authenticate("ROLE_ADMIN");
        workflowEngine.reject(task.getId(), task.getApproverId(), "qs-rejector", "退回整改",
                "qs-it-" + UUID.randomUUID());
        assertEquals("REJECTED", instanceMapper.selectById(instance.getId()).getInstanceStatus());
    }

    private void asUser(long userId) {
        String roleCode = userId == 1L ? "PROJECT_MANAGER" : userId == 2L ? "SAFETY_LEAD" : "EMPLOYEE";
        UserContext.set(Jwts.claims().subject("admin-" + userId).add("userId", userId).add("username", "admin-" + userId)
                .add("tenantId", 0L).add("roleCodes", List.of(roleCode)).build());
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    private void ensureWorkflowFixture() {
        jdbc.execute("ALTER TABLE qs_rectification ADD COLUMN IF NOT EXISTS approval_instance_id BIGINT");
        jdbc.execute("ALTER TABLE qs_consequence ADD COLUMN IF NOT EXISTS approval_instance_id BIGINT");
        jdbc.execute("ALTER TABLE wf_instance ADD COLUMN IF NOT EXISTS security_policy_json VARCHAR(1000)");
        jdbc.execute("ALTER TABLE wf_node_instance ADD COLUMN IF NOT EXISTS node_type VARCHAR(50)");
        jdbc.execute("ALTER TABLE wf_node_instance ADD COLUMN IF NOT EXISTS approver_config VARCHAR(1000)");
        jdbc.execute("ALTER TABLE wf_node_instance ADD COLUMN IF NOT EXISTS allow_transfer SMALLINT");
        jdbc.execute("ALTER TABLE wf_node_instance ADD COLUMN IF NOT EXISTS allow_add_sign SMALLINT");
        jdbc.execute("ALTER TABLE wf_node_instance ADD COLUMN IF NOT EXISTS timeout_hours INT");
        projectManagerDataScope = jdbc.queryForObject(
                "SELECT data_scope FROM sys_role WHERE tenant_id=0 AND role_code='PROJECT_MANAGER' AND deleted_flag=0",
                String.class);
        jdbc.update("UPDATE sys_role SET data_scope='PROJECT_MEMBER' WHERE tenant_id=0 AND role_code='PROJECT_MANAGER' AND deleted_flag=0");
        jdbc.update("INSERT INTO sys_role(id,tenant_id,role_code,role_name,role_type,status,data_scope,created_by,created_at,updated_by,updated_at,deleted_flag,remark,role_level) " +
                "SELECT ?,0,'SAFETY_LEAD','安全负责人','SYSTEM','ENABLE','PROJECT_MEMBER',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0,'QS-IT',2 " +
                "WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE tenant_id=0 AND role_code='SAFETY_LEAD' AND deleted_flag=0)", SAFETY_LEAD_ROLE);
        insertWorkflowTemplate(RECTIFICATION_TEMPLATE, "QS-IT-RECTIFICATION", "QS_RECTIFICATION", "质量安全整改审批", 99188930L, 99188931L);
        insertWorkflowTemplate(CONSEQUENCE_TEMPLATE, "QS-IT-CONSEQUENCE", "QS_CONSEQUENCE", "质量安全金额后果审批", 99188932L, 99188933L);
    }

    private void insertWorkflowTemplate(long templateId, String templateCode, String businessType,
                                        String templateName, long safetyNodeId, long managerNodeId) {
        String policy = "{\"preventInitiatorApproval\":false,\"maxApprovalsPerUser\":1," +
                "\"requireProjectMembership\":true,\"allowAdminFallback\":false}";
        jdbc.update("INSERT INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,condition_rule,form_schema,created_by,created_at,updated_by,updated_at,deleted_flag,remark) " +
                        "VALUES(?,0,?,?,?,1,NULL,NULL,?,NULL,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0,'QS-IT')",
                templateId, templateCode, templateName, businessType, policy);
        jdbc.update("INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,pass_rule_json,reject_rule_json,condition_rule,node_config,allow_transfer,allow_add_sign,timeout_hours,created_by,created_at,updated_by,updated_at,deleted_flag,remark) " +
                        "VALUES(?,0,?,'QS_IT_01','安全负责人审批',1,'APPROVAL','SEQUENTIAL','{\"type\":\"ROLE\",\"roleCode\":\"SAFETY_LEAD\"}',NULL,NULL,NULL,NULL,1,1,48,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0,'QS-IT')",
                safetyNodeId, templateId);
        jdbc.update("INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,pass_rule_json,reject_rule_json,condition_rule,node_config,allow_transfer,allow_add_sign,timeout_hours,created_by,created_at,updated_by,updated_at,deleted_flag,remark) " +
                        "VALUES(?,0,?,'QS_IT_02','项目经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\":\"ROLE\",\"roleCode\":\"PROJECT_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0,'QS-IT')",
                managerNodeId, templateId);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_user_role WHERE id IN (99188901,99188902)");
        jdbc.update("DELETE FROM pm_project_member WHERE id IN (?,?,?)", MEMBER_ONE, MEMBER_TWO, MEMBER_DISABLED);
        jdbc.update("UPDATE qs_consequence SET evaluation_id=NULL,cost_item_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_partner_evaluation WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_item WHERE project_id=? AND source_type='QUALITY_SAFETY_CONSEQUENCE'", PROJECT);
        jdbc.update("DELETE FROM qs_consequence WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM sys_file WHERE business_type IN('QS_INSPECTION','QS_ISSUE','QS_RECTIFICATION') AND business_id IN (SELECT id FROM qs_inspection_record WHERE project_id=? UNION SELECT id FROM qs_issue WHERE project_id=? UNION SELECT id FROM qs_rectification WHERE project_id=?)", PROJECT, PROJECT, PROJECT);
        jdbc.update("DELETE FROM qs_rectification WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_issue WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_inspection_record WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_inspection_plan WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM wf_idempotency WHERE business_type IN('QS_RECTIFICATION','QS_CONSEQUENCE')");
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('QS_RECTIFICATION','QS_CONSEQUENCE'))", PROJECT);
        jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('QS_RECTIFICATION','QS_CONSEQUENCE'))", PROJECT);
        jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('QS_RECTIFICATION','QS_CONSEQUENCE'))", PROJECT);
        jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('QS_RECTIFICATION','QS_CONSEQUENCE'))", PROJECT);
        jdbc.update("DELETE FROM sys_notification WHERE biz_type='WORKFLOW' AND biz_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('QS_RECTIFICATION','QS_CONSEQUENCE'))", PROJECT);
        jdbc.update("DELETE FROM wf_instance WHERE project_id=? AND business_type IN('QS_RECTIFICATION','QS_CONSEQUENCE')", PROJECT);
        jdbc.update("DELETE FROM wf_template_node WHERE template_id IN (?,?)", RECTIFICATION_TEMPLATE, CONSEQUENCE_TEMPLATE);
        jdbc.update("DELETE FROM wf_template WHERE id IN (?,?)", RECTIFICATION_TEMPLATE, CONSEQUENCE_TEMPLATE);
        jdbc.update("DELETE FROM sys_role WHERE id=?", SAFETY_LEAD_ROLE);
        jdbc.update("DELETE FROM ct_contract WHERE id IN (?,?,?,?)", CONTRACT, OTHER_PROJECT_CONTRACT, UNRELATED_CONTRACT, CROSS_TENANT_CONTRACT);
        jdbc.update("DELETE FROM md_partner WHERE id IN (?,?,?,?,?,?)", PARTNER, CONTRACT_COUNTERPARTY, OTHER_PARTNER_A, OTHER_PARTNER_B, CROSS_TENANT_PARTNER_A, CROSS_TENANT_PARTNER_B);
        jdbc.update("DELETE FROM cost_subject_assignment_rule WHERE id IN (?,?)", ASSIGNMENT_RULE, AMBIGUOUS_RULE);
        jdbc.update("DELETE FROM cost_subject_mapping_version WHERE id=?", MAPPING_VERSION);
        jdbc.update("DELETE FROM cost_subject WHERE id IN (?,?)", SUBJECT, AMBIGUOUS_SUBJECT);
        jdbc.update("DELETE FROM project_wbs_task WHERE id IN (?,?)", WBS, OTHER_WBS);
        jdbc.update("DELETE FROM project_schedule_plan WHERE id IN (?,?)", SCHEDULE, OTHER_SCHEDULE);
        jdbc.update("DELETE FROM pm_project WHERE id IN (?,?,?)", PROJECT, OTHER_PROJECT, CROSS_TENANT_PROJECT);
        jdbc.update("DELETE FROM sys_user WHERE id IN (?,?,?)", OUTSIDE_USER, DISABLED_USER, CROSS_TENANT_USER);
        if (projectManagerDataScope != null) {
            jdbc.update("UPDATE sys_role SET data_scope=? WHERE tenant_id=0 AND role_code='PROJECT_MANAGER' AND deleted_flag=0",
                    projectManagerDataScope);
            projectManagerDataScope = null;
        }
    }
}
