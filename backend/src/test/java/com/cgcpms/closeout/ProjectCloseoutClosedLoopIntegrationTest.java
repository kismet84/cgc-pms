package com.cgcpms.closeout;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.closeout.dto.ProjectCloseoutModels.*;
import com.cgcpms.closeout.service.ProjectCloseGateService;
import com.cgcpms.closeout.service.ProjectCloseoutService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.project.service.PmProjectService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class ProjectCloseoutClosedLoopIntegrationTest {
    private static final long PROJECT = 99191001L;
    private static final long PARTNER = 99191002L;
    private static final long CONTRACT = 99191003L;
    private static final long SCHEDULE = 99191004L;
    private static final long WBS = 99191005L;
    private static final long QUALITY_PLAN = 99191006L;
    private static final long QUALITY_INSPECTION = 99191007L;
    private static final long SETTLEMENT = 99191008L;
    private static final long REGULAR_RECEIVABLE = 99191009L;
    private static final long RETENTION_RECEIVABLE = 99191010L;
    private static final long FUND_ACCOUNT = 99191011L;
    private static final long RESPONSIBLE_USER = 99191012L;
    private static final long OUTSIDE_USER = 99191013L;
    private static final long PROJECT_MEMBER = 99191014L;
    private static final long SUPERSEDED_SCHEDULE = 99191015L;
    private static final long SUPERSEDED_WBS = 99191016L;
    private static final long TECH_DRAWING = 99191017L;
    private static final long TECH_VERSION = 99191018L;
    private static final long TECH_REVIEW = 99191019L;
    private static final long TECH_RFI = 99191020L;
    private static final long TECH_DISCLOSURE = 99191021L;
    private static final long DAILY_LOG = 99191022L;
    private static final long TECH_REFERENCE = 99191023L;
    private static final long TECH_ARCHIVE = 99191024L;
    private static final long SAFETY_PLAN = 99191025L;
    private static final long SAFETY_INSPECTION = 99191026L;
    private static final long OVERHEAD_SUBJECT = 99191027L;
    private static final long OVERHEAD_RULE = 99191028L;
    private static final long OVERHEAD_SOURCE = 99191029L;
    private static final long OVERHEAD_CLEARING = 99191030L;
    private static final long REVERSAL_OTHER_PROJECT = 99191031L;
    private static final long REVERSAL_BATCH = 99191032L;
    private static final long REVERSAL_LINE_FIRST = 99191033L;
    private static final long REVERSAL_LINE_CURRENT = 99191034L;
    private static final long REVERSAL_REQUEST = 99191035L;
    private static final long REVERSAL_APPROVAL = 99191036L;
    private static final long UNCLASSIFIED_COST = 99191037L;
    private static final AtomicLong IDS = new AtomicLong(99191100L);

    @Autowired ProjectCloseoutService service;
    @Autowired ProjectCloseGateService gate;
    @Autowired PmProjectService projectService;
    @Autowired BusinessObjectAuthorizer fileAuthorizer;
    @Autowired WorkflowEngine workflowEngine;
    @Autowired WfInstanceMapper instanceMapper;
    @Autowired WfTaskMapper taskMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        asUser(1L);
        cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) SELECT 1,0,'admin','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','系统管理员','ENABLE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) VALUES(?,0,'closeout-responsible','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','收尾责任人','ENABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", RESPONSIBLE_USER);
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) VALUES(?,0,'closeout-outsider','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','非项目成员','ENABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", OUTSIDE_USER);
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'CLOSEOUT-IT','竣工收尾闭环测试项目','ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO pm_project_member(id,tenant_id,project_id,user_id,role_code,status,created_at,updated_at,created_by,updated_by,deleted_flag) VALUES(?,0,?,?,'PROJECT_MANAGER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1,0)", PROJECT_MEMBER, PROJECT, RESPONSIBLE_USER);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'OWNER-CLOSEOUT','收尾测试业主','OWNER','ENABLE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PARTNER);
        jdbc.update("""
                INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,
                 contract_amount,current_amount,contract_status,approval_status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'CT-CLOSEOUT','项目总承包合同','MAIN',?,1000,1000,'SETTLED','APPROVED',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, CONTRACT, PROJECT, PARTNER);
        jdbc.update("""
                INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,
                 planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'CLOSEOUT-SCHEDULE','竣工基线计划','BASELINE',1,?,?,'ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, SCHEDULE, PROJECT, LocalDate.now().minusMonths(3), LocalDate.now());
        jdbc.update("""
                INSERT INTO project_wbs_task(id,tenant_id,project_id,schedule_plan_id,task_code,task_name,work_area,
                 responsible_user_id,planned_start_date,planned_end_date,actual_start_date,actual_end_date,weight_percent,
                 planned_quantity,unit,actual_quantity,actual_progress,status,sort_order,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'WBS-CLOSEOUT','单位工程施工','全场',1,?,?,?,?,100,100,'%',100,100,'COMPLETED',1,0,
                 1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, WBS, PROJECT, SCHEDULE, LocalDate.now().minusMonths(3), LocalDate.now(),
                LocalDate.now().minusMonths(3), LocalDate.now());
        jdbc.update("""
                INSERT INTO qs_inspection_plan(id,tenant_id,project_id,plan_code,plan_name,inspection_type,frequency_type,
                 start_date,end_date,owner_user_id,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'QP-CLOSEOUT','单位工程验收计划','QUALITY','SINGLE',?,?,1,'COMPLETED',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, QUALITY_PLAN, PROJECT, LocalDate.now().minusDays(2), LocalDate.now());
        jdbc.update("""
                INSERT INTO qs_inspection_record(id,tenant_id,plan_id,project_id,wbs_task_id,inspection_code,inspection_date,location,
                 inspector_user_id,conclusion,summary,status,submitted_by,submitted_at,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,'QI-CLOSEOUT',?,'全场',1,'PASS','单位工程质量验收通过','SUBMITTED',1,CURRENT_TIMESTAMP,0,
                 1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, QUALITY_INSPECTION, QUALITY_PLAN, PROJECT, WBS, LocalDate.now());
        jdbc.update("""
                INSERT INTO qs_inspection_plan(id,tenant_id,project_id,plan_code,plan_name,inspection_type,frequency_type,
                 start_date,end_date,owner_user_id,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'SP-CLOSEOUT','收尾安全检查计划','SAFETY','SINGLE',?,?,1,'COMPLETED',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, SAFETY_PLAN, PROJECT, LocalDate.now().minusDays(2), LocalDate.now());
        jdbc.update("""
                INSERT INTO qs_inspection_record(id,tenant_id,plan_id,project_id,wbs_task_id,inspection_code,inspection_date,location,
                 inspector_user_id,conclusion,summary,status,submitted_by,submitted_at,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,'SI-CLOSEOUT',?,'全场',1,'PASS','安全检查通过','SUBMITTED',1,CURRENT_TIMESTAMP,0,
                 1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, SAFETY_INSPECTION, SAFETY_PLAN, PROJECT, WBS, LocalDate.now());
        jdbc.update("""
                INSERT INTO owner_settlement(id,tenant_id,project_id,contract_id,settlement_code,settlement_period,
                 settlement_date,gross_amount,tax_amount,retention_amount,net_receivable_amount,due_date,customer_id,status,
                 attachment_count,formula_version,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'OS-CLOSEOUT','FINAL',?,1000,0,100,900,?,?, 'RECEIVABLE_CREATED',1,'OWNER_SETTLEMENT_V1',0,
                 1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, SETTLEMENT, PROJECT, CONTRACT, LocalDate.now(), LocalDate.now(), PARTNER);
        jdbc.update("""
                INSERT INTO account_receivable(id,tenant_id,project_id,contract_id,settlement_id,customer_id,receivable_type,
                 receivable_code,original_amount,collected_amount,credited_amount,outstanding_amount,due_date,status,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,?, 'PROGRESS','AR-PROGRESS',900,0,0,900,?,'OPEN',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, REGULAR_RECEIVABLE, PROJECT, CONTRACT, SETTLEMENT, PARTNER, LocalDate.now());
        jdbc.update("""
                INSERT INTO account_receivable(id,tenant_id,project_id,contract_id,settlement_id,customer_id,receivable_type,
                 receivable_code,original_amount,collected_amount,credited_amount,outstanding_amount,due_date,status,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,?, 'RETENTION','AR-RETENTION',100,0,0,100,?,'OPEN',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, RETENTION_RECEIVABLE, PROJECT, CONTRACT, SETTLEMENT, PARTNER, LocalDate.now());
        jdbc.update("""
                INSERT INTO fund_account(id,tenant_id,account_code,account_name,account_type,accounting_subject_code,opening_date,opening_balance,
                 enabled_flag,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,'FA-CLOSEOUT','收尾测试银行账户','BANK','1002.02',?,0,1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, FUND_ACCOUNT, LocalDate.now().minusYears(1));
    }

    @AfterEach
    void teardown() {
        cleanup();
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void overviewExposesUnboundSettlementReceivablesAndInspectionWbsForCandidateFiltering() {
        Map<String, Object> overview = service.overview(PROJECT);
        assertEquals(1, ((List<?>) overview.get("settlements")).size());
        assertEquals(2, ((List<?>) overview.get("receivables")).size());
        List<?> qualityInspections = (List<?>) overview.get("qualityInspections");
        assertEquals(1, qualityInspections.size());
        Map<?, ?> inspection = (Map<?, ?>) qualityInspections.get(0);
        assertEquals(WBS, ((Number) inspection.get("wbsTaskId")).longValue());
        List<?> responsibleMembers = (List<?>) overview.get("responsibleMembers");
        assertEquals(1, responsibleMembers.size());
        Map<?, ?> responsibleMember = (Map<?, ?>) responsibleMembers.getFirst();
        assertEquals(RESPONSIBLE_USER, ((Number) responsibleMember.get("userId")).longValue());
        assertEquals("收尾责任人", responsibleMember.get("realName"));
        assertEquals(1L, ((Number) ((Map<?, ?>) overview.get("detailTotals")).get("qualityInspections")).longValue());
        assertNull(overview.get("closeout"));
    }

    @Test
    void closesSectionFinalAcceptanceSettlementCollectionsWarrantyDefectArchiveAndProject() {
        BusinessException directClose = assertThrows(BusinessException.class,
                () -> projectService.transitionStatus(PROJECT, "CLOSED", "绕过收尾闭环"));
        assertEquals("PROJECT_CLOSEOUT_ACTION_REQUIRED", directClose.getCode());

        long closeoutId = id(service.initiate(new InitiateCommand(PROJECT, "PC-001", LocalDate.now(), "启动收尾")));
        assertTrue(jdbc.queryForObject("SELECT closeout_code FROM project_closeout WHERE id=?", String.class, closeoutId)
                .matches("PC-\\d{8}-\\d{3}"));
        BusinessException safetyInspection = assertThrows(BusinessException.class,
                () -> service.createSectionAcceptance(closeoutId, new SectionAcceptanceCommand(
                        WBS, SAFETY_INSPECTION, "SA-SAFETY", "安全检查不得冒充质量验收", LocalDate.now(), "PASS", null)));
        assertEquals("CLOSEOUT_QUALITY_NOT_PASSED", safetyInspection.getCode());
        long sectionId = id(service.createSectionAcceptance(closeoutId, new SectionAcceptanceCommand(
                WBS, QUALITY_INSPECTION, "SA-001", "单位工程分部分项验收", LocalDate.now(), "PASS", null)));
        assertTrue(jdbc.queryForObject("SELECT acceptance_code FROM closeout_section_acceptance WHERE id=?", String.class, sectionId)
                .matches("SA-\\d{8}-\\d{3}"));
        assertEquals("CLOSEOUT_ATTACHMENT_REQUIRED", assertThrows(BusinessException.class,
                () -> service.confirmSectionAcceptance(sectionId)).getCode());
        evidence("CLOSEOUT_SECTION_ACCEPTANCE", sectionId, "SECTION_ACCEPTANCE_RECORD");
        jdbc.update("UPDATE closeout_section_acceptance SET quality_inspection_id=? WHERE id=?", SAFETY_INSPECTION, sectionId);
        assertEquals("CLOSEOUT_QUALITY_NOT_PASSED", assertThrows(BusinessException.class,
                () -> service.confirmSectionAcceptance(sectionId)).getCode());
        jdbc.update("UPDATE closeout_section_acceptance SET quality_inspection_id=? WHERE id=?", QUALITY_INSPECTION, sectionId);
        service.confirmSectionAcceptance(sectionId);

        long finalId = id(service.createFinalAcceptance(closeoutId, new FinalAcceptanceCommand(
                "FA-001", LocalDate.now(), "建设单位", "建设、监理、设计、施工单位",
                "PASS", "工程实体、资料和功能验收通过", null)));
        assertTrue(jdbc.queryForObject("SELECT acceptance_code FROM closeout_final_acceptance WHERE id=?", String.class, finalId)
                .matches("FA-\\d{8}-\\d{3}"));
        evidence("CLOSEOUT_FINAL_ACCEPTANCE", finalId, "FINAL_ACCEPTANCE_CERTIFICATE");
        service.submitFinalAcceptance(finalId);
        approveAll("PROJECT_FINAL_ACCEPTANCE", finalId);
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM closeout_final_acceptance WHERE id=?", String.class, finalId));
        Map<String, Object> finalDetail = service.finalAcceptanceDetail(finalId);
        assertEquals(finalId, id((Map<?, ?>) finalDetail.get("main")));
        assertEquals("工程实体、资料和功能验收通过", ((Map<?, ?>) finalDetail.get("main")).get("acceptanceSummary"));
        assertEquals(1, ((List<?>) finalDetail.get("items")).size());
        assertFalse(((Map<?, ?>) finalDetail.get("main")).containsKey("tenant_id"));
        assertEquals("CLOSEOUT_FINAL_ACCEPTANCE_NOT_FOUND", assertThrows(BusinessException.class,
                () -> service.finalAcceptanceDetail(Long.MAX_VALUE)).getCode());
        UserContext.set(Jwts.claims().subject("closeout-reader").add("userId", 99999L).add("username", "closeout-reader")
                .add("tenantId", 0L).add("roleCodes", List.of()).build());
        assertEquals("PROJECT_ACCESS_DENIED", assertThrows(BusinessException.class,
                () -> service.finalAcceptanceDetail(finalId)).getCode());
        UserContext.set(Jwts.claims().subject("other-tenant").add("userId", 1L).add("username", "other-tenant")
                .add("tenantId", 99L).add("roleCodes", List.of("ADMIN")).build());
        assertEquals("CLOSEOUT_FINAL_ACCEPTANCE_NOT_FOUND", assertThrows(BusinessException.class,
                () -> service.finalAcceptanceDetail(finalId)).getCode());
        asUser(1L);

        service.bindFinalSettlement(closeoutId, new SettlementBindingCommand(SETTLEMENT));
        assertEquals("FINAL", jdbc.queryForObject("SELECT settlement_type FROM owner_settlement WHERE id=?", String.class, SETTLEMENT));
        assertEquals("CLOSEOUT_TAIL_COLLECTION_INCOMPLETE", assertThrows(BusinessException.class,
                () -> service.verifyTailCollection(closeoutId)).getCode());
        collect(REGULAR_RECEIVABLE, new BigDecimal("900.00"), "REGULAR");
        service.verifyTailCollection(closeoutId);

        assertEquals("CLOSEOUT_RESPONSIBLE_PROJECT_MEMBER_INVALID", assertThrows(BusinessException.class,
                () -> service.registerWarranty(closeoutId, new WarrantyCommand(
                        CONTRACT, RETENTION_RECEIVABLE, "W-OUTSIDE", new BigDecimal("100.00"),
                        LocalDate.now().minusMonths(12), LocalDate.now(), OUTSIDE_USER, null))).getCode());
        long warrantyId = id(service.registerWarranty(closeoutId, new WarrantyCommand(
                CONTRACT, RETENTION_RECEIVABLE, "W-001", new BigDecimal("100.00"),
                LocalDate.now().minusMonths(12), LocalDate.now(), RESPONSIBLE_USER, null)));
        assertTrue(jdbc.queryForObject("SELECT warranty_code FROM closeout_warranty WHERE id=?", String.class, warrantyId)
                .matches("WAR-\\d{8}-\\d{3}"));
        assertEquals("CLOSEOUT_RESPONSIBLE_PROJECT_MEMBER_INVALID", assertThrows(BusinessException.class,
                () -> service.createDefect(warrantyId, new DefectCommand(
                        "DF-OUTSIDE", "非成员缺陷", "责任人不属于项目", OUTSIDE_USER,
                        LocalDate.now().plusDays(7), null))).getCode());
        long defectId = id(service.createDefect(warrantyId, new DefectCommand(
                "DF-001", "屋面局部渗水", "雨后屋面局部出现渗水", RESPONSIBLE_USER, LocalDate.now().plusDays(7), null)));
        assertTrue(jdbc.queryForObject("SELECT defect_code FROM closeout_defect WHERE id=?", String.class, defectId)
                .matches("DEF-\\d{8}-\\d{3}"));
        evidence("CLOSEOUT_DEFECT", defectId, "DEFECT_RECTIFICATION_EVIDENCE");
        service.rectifyDefect(defectId, new RectificationCommand("完成防水层修补并通过淋水试验"));
        assertEquals("CLOSEOUT_DEFECT_REVIEWER_CONFLICT", assertThrows(BusinessException.class,
                () -> service.verifyDefect(defectId, new DefectVerificationCommand("ACCEPTED", "复验通过"))).getCode());
        asUser(2L);
        int versionBeforeVerification = jdbc.queryForObject(
                "SELECT version FROM closeout_defect WHERE id=?", Integer.class, defectId);
        Map<String, Object> verifiedDefect = service.verifyDefect(
                defectId, new DefectVerificationCommand("ACCEPTED", "复验通过，无渗漏"));
        assertEquals(versionBeforeVerification + 1, ((Number) verifiedDefect.get("version")).intValue());
        assertEquals(versionBeforeVerification + 1, jdbc.queryForObject(
                "SELECT version FROM closeout_defect WHERE id=?", Integer.class, defectId));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM closeout_defect WHERE id=?", String.class, defectId));

        assertEquals("CLOSEOUT_RETENTION_COLLECTION_INCOMPLETE", assertThrows(BusinessException.class,
                () -> service.releaseWarranty(warrantyId)).getCode());
        collect(RETENTION_RECEIVABLE, new BigDecimal("100.00"), "RETENTION");
        evidence("CLOSEOUT_WARRANTY", warrantyId, "WARRANTY_RELEASE_VOUCHER");
        service.releaseWarranty(warrantyId);

        long archiveId = id(service.createArchiveTransfer(closeoutId, new ArchiveTransferCommand(
                "AT-001", LocalDate.now(), "建设单位档案室", "档案管理员", "城建档案馆A区",
                "竣工图、验收记录、结算资料、质保与缺陷责任资料", null)));
        assertTrue(jdbc.queryForObject("SELECT transfer_code FROM closeout_archive_transfer WHERE id=?", String.class, archiveId)
                .matches("ATR-\\d{8}-\\d{3}"));
        evidence("CLOSEOUT_ARCHIVE_TRANSFER", archiveId, "ARCHIVE_TRANSFER_LIST");
        service.acceptArchiveTransfer(archiveId);

        Map<String, Object> trace = service.closeProject(closeoutId,
                new CloseProjectCommand(LocalDate.now(), "全部收尾条件满足"));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM pm_project WHERE id=?", String.class, PROJECT));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM project_closeout WHERE id=?", String.class, closeoutId));
        assertEquals(1, ((List<?>) trace.get("sectionAcceptances")).size());
        assertEquals(1, ((List<?>) trace.get("finalAcceptances")).size());
        assertEquals(5, ((List<?>) trace.get("approvalRecords")).size());
        assertEquals(2, ((List<?>) trace.get("receivables")).size());
        assertEquals(2, ((List<?>) trace.get("collectionAllocations")).size());
        assertEquals(1, ((List<?>) trace.get("warranties")).size());
        assertEquals(1, ((List<?>) trace.get("defects")).size());
        assertEquals(1, ((List<?>) trace.get("archiveTransfers")).size());
        assertNotNull(service.overview(PROJECT).get("closeout"));
    }

    @Test
    void rejectsIncompleteWbsWrongFileStageAndPrematureClose() {
        long closeoutId = id(service.initiate(new InitiateCommand(PROJECT, "PC-EDGE", LocalDate.now(), null)));
        jdbc.update("UPDATE project_wbs_task SET status='IN_PROGRESS' WHERE id=?", WBS);
        BusinessException incomplete = assertThrows(BusinessException.class, () -> service.createFinalAcceptance(closeoutId,
                new FinalAcceptanceCommand("FA-EDGE", LocalDate.now(), "建设单位", "参建单位", "PASS", "准备验收", null)));
        assertEquals("CLOSEOUT_WBS_INCOMPLETE", incomplete.getCode());
        jdbc.update("UPDATE project_wbs_task SET status='COMPLETED' WHERE id=?", WBS);

        long sectionId = id(service.createSectionAcceptance(closeoutId, new SectionAcceptanceCommand(
                WBS, QUALITY_INSPECTION, "SA-EDGE", "边界验收", LocalDate.now(), "PASS", null)));
        authenticate("closeout:section:maintain");
        assertDoesNotThrow(() -> fileAuthorizer.checkUploadAccess("CLOSEOUT_SECTION_ACCEPTANCE", sectionId));
        assertEquals("CLOSEOUT_DOCUMENT_STAGE_INVALID", assertThrows(BusinessException.class,
                () -> fileAuthorizer.checkVariationDocumentStage("CLOSEOUT_SECTION_ACCEPTANCE", sectionId,
                        "FINAL_ACCEPTANCE_CERTIFICATE")).getCode());
        asUser(1L);
        BusinessException premature = assertThrows(BusinessException.class, () -> service.closeProject(closeoutId,
                new CloseProjectCommand(LocalDate.now(), "提前关闭")));
        assertEquals("CLOSEOUT_STAGE_INVALID", premature.getCode());
    }

    @Test
    void constructionGateIncludesTechnicalDrawingRfiDisclosureAndArchiveFacts() {
        jdbc.update("""
                INSERT INTO tech_drawing(id,tenant_id,project_id,drawing_code,drawing_name,specialty,source_organization,status,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'TD-CLOSEOUT','竣工施工图','CIVIL','设计单位','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, TECH_DRAWING, PROJECT);
        jdbc.update("""
                INSERT INTO tech_drawing_version(id,tenant_id,project_id,drawing_id,version_no,received_at,received_by,status,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'V1',CURRENT_TIMESTAMP,1,'RECEIVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, TECH_VERSION, PROJECT, TECH_DRAWING);
        jdbc.update("UPDATE tech_drawing SET current_version_id=? WHERE id=?", TECH_VERSION, TECH_DRAWING);
        assertTrue(gateCodes().contains("CONSTRUCTION_DRAWING_NOT_APPROVED"));

        jdbc.update("UPDATE tech_drawing_version SET status='APPROVED', approved_at=CURRENT_TIMESTAMP WHERE id=?", TECH_VERSION);
        jdbc.update("""
                INSERT INTO tech_drawing_review(id,tenant_id,project_id,drawing_version_id,review_code,review_date,chair_user_id,
                 participant_summary,conclusion,review_summary,requires_rfi,status,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'TR-CLOSEOUT',CURRENT_DATE,1,'参建单位','PASS','会审通过',1,'CONFIRMED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, TECH_REVIEW, PROJECT, TECH_VERSION);
        jdbc.update("""
                INSERT INTO tech_rfi(id,tenant_id,project_id,drawing_version_id,review_id,rfi_code,subject,question,priority,
                 raised_by,raised_at,response_due_date,status,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,'RFI-CLOSEOUT','节点澄清','请确认节点做法','NORMAL',1,CURRENT_TIMESTAMP,CURRENT_DATE,'SUBMITTED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, TECH_RFI, PROJECT, TECH_VERSION, TECH_REVIEW);
        assertTrue(gateCodes().contains("CONSTRUCTION_RFI_OPEN"));

        jdbc.update("UPDATE tech_rfi SET status='CLOSED', closed_by=1, closed_at=CURRENT_TIMESTAMP WHERE id=?", TECH_RFI);
        jdbc.update("""
                INSERT INTO tech_disclosure(id,tenant_id,project_id,drawing_version_id,disclosure_code,disclosure_title,
                 disclosure_date,presenter_user_id,recipient_summary,disclosure_content,status,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'DISC-CLOSEOUT','节点施工交底',CURRENT_DATE,1,'施工班组','按批准图纸施工','DRAFT',
                 1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, TECH_DISCLOSURE, PROJECT, TECH_VERSION);
        assertTrue(gateCodes().contains("CONSTRUCTION_DISCLOSURE_UNCONFIRMED"));

        jdbc.update("UPDATE tech_disclosure SET status='CONFIRMED', confirmed_by=1, confirmed_at=CURRENT_TIMESTAMP WHERE id=?", TECH_DISCLOSURE);
        jdbc.update("INSERT INTO site_daily_log(id,tenant_id,project_id,report_date,construction_content,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,CURRENT_DATE,'节点施工','SUBMITTED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", DAILY_LOG, PROJECT);
        jdbc.update("""
                INSERT INTO tech_construction_reference(id,tenant_id,project_id,drawing_version_id,disclosure_id,daily_log_id,
                 wbs_task_id,reference_date,work_area,reference_description,status,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,?,?,CURRENT_DATE,'全场','按批准图纸及交底施工','RECORDED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, TECH_REFERENCE, PROJECT, TECH_VERSION, TECH_DISCLOSURE, DAILY_LOG, WBS);
        assertTrue(gateCodes().contains("CONSTRUCTION_REFERENCE_NOT_ARCHIVED"));

        jdbc.update("""
                INSERT INTO tech_acceptance_archive(id,tenant_id,project_id,drawing_version_id,construction_reference_id,
                 quality_inspection_id,archive_code,acceptance_date,acceptance_conclusion,archive_location,status,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,?, 'TA-CLOSEOUT',CURRENT_DATE,'PASS','项目档案室','ARCHIVED',
                 1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, TECH_ARCHIVE, PROJECT, TECH_VERSION, TECH_REFERENCE, QUALITY_INSPECTION);
        assertFalse(gateCodes().stream().anyMatch(code -> code.startsWith("CONSTRUCTION_DRAWING_")
                || code.equals("CONSTRUCTION_RFI_OPEN") || code.equals("CONSTRUCTION_DISCLOSURE_UNCONFIRMED")
                || code.equals("CONSTRUCTION_REFERENCE_NOT_ARCHIVED")));
    }

    @Test
    void safetyInspectionCannotSatisfyConstructionQualityGate() {
        assertFalse(gateCodes().contains("CONSTRUCTION_QUALITY_ACCEPTANCE_MISSING"));
        jdbc.update("UPDATE qs_inspection_record SET deleted_flag=1 WHERE id=?", QUALITY_INSPECTION);
        assertTrue(gateCodes().contains("CONSTRUCTION_QUALITY_ACCEPTANCE_MISSING"));
    }

    @Test
    void pendingOverheadAllocationBlocksConstructionCompletionUntilCleared() {
        jdbc.update("""
                INSERT INTO cost_subject
                (id,tenant_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,deleted_flag)
                VALUES (?,0,'5401.04.98','收尾待分摊间接费','OVERHEAD','COST',3,1,'ENABLE',0)
                """, OVERHEAD_SUBJECT);
        jdbc.update("""
                INSERT INTO overhead_allocation_rule
                (id,tenant_id,cost_subject_id,allocation_basis,allocation_cycle,status,deleted_flag)
                VALUES (?,0,?,'CONTRACT_AMOUNT','MONTHLY','DISABLE',0)
                """, OVERHEAD_RULE, OVERHEAD_SUBJECT);
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,classification_status,recognition_role,cost_type,
                 amount,tax_amount,amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,0,?,?,'CLASSIFIED','ACTUAL','OVERHEAD',100,0,100,'MANUAL_COST',?,?,CURRENT_DATE,'CONFIRMED',1,0)
                """, OVERHEAD_SOURCE, PROJECT, OVERHEAD_SUBJECT, OVERHEAD_SOURCE, OVERHEAD_SOURCE);

        assertTrue(gateCodes().contains("CONSTRUCTION_OVERHEAD_ALLOCATION_PENDING"));

        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,classification_status,recognition_role,original_cost_item_id,
                 cost_type,amount,tax_amount,amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,
                 generated_flag,deleted_flag)
                VALUES (?,0,?,?,'REVERSAL','ACTUAL',?,'OVERHEAD_CLEARING',-100,0,-100,
                        'OVERHEAD_ALLOCATION_CLEARING',?,?,CURRENT_DATE,'CONFIRMED',1,0)
                """, OVERHEAD_CLEARING, PROJECT, OVERHEAD_SUBJECT, OVERHEAD_SOURCE,
                OVERHEAD_RULE, OVERHEAD_SOURCE);
        assertFalse(gateCodes().contains("CONSTRUCTION_OVERHEAD_ALLOCATION_PENDING"));
    }

    @Test
    void unclassifiedCostFactBlocksConstructionCompletionUntilClassified() {
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,classification_status,recognition_role,cost_type,amount,tax_amount,
                 amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,0,?,'UNCLASSIFIED','ACTUAL','MATERIAL',100,0,100,'MAT_RECEIPT',?,?,CURRENT_DATE,'CONFIRMED',1,0)
                """, UNCLASSIFIED_COST, PROJECT, UNCLASSIFIED_COST, UNCLASSIFIED_COST);

        assertTrue(gateCodes().contains("CONSTRUCTION_UNCLASSIFIED_COST_FACT"));

        jdbc.update("UPDATE cost_item SET classification_status='CLASSIFIED' WHERE id=?", UNCLASSIFIED_COST);
        assertFalse(gateCodes().contains("CONSTRUCTION_UNCLASSIFIED_COST_FACT"));
    }

    @Test
    void multiProjectCostReversalBlocksEveryAffectedProjectFromClosing() {
        jdbc.update("""
                INSERT INTO pm_project
                (id,tenant_id,project_code,project_name,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES (?,0,'CLOSEOUT-REV-OTHER','冲销首项目','ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, REVERSAL_OTHER_PROJECT);
        jdbc.update("""
                INSERT INTO cost_subject
                (id,tenant_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,deleted_flag)
                VALUES (?,0,'5401.04.98','收尾冲销测试成本','OVERHEAD','COST',3,1,'ENABLE',0)
                """, OVERHEAD_SUBJECT);
        Long templateId = jdbc.queryForObject("""
                SELECT id FROM wf_template
                WHERE tenant_id=0 AND business_type='FINANCE_COST_ALLOCATION'
                  AND enabled=1 AND deleted_flag=0 ORDER BY id DESC LIMIT 1
                """, Long.class);
        jdbc.update("""
                INSERT INTO wf_instance
                (id,tenant_id,template_id,business_type,business_id,project_id,title,instance_status,
                 current_round,resubmit_count,business_revision,initiator_id,created_by,deleted_flag)
                VALUES (?,0,?,'FINANCE_COST_ALLOCATION',?,?,?,'APPROVED',1,0,1,1,1,0)
                """, REVERSAL_APPROVAL, templateId, REVERSAL_BATCH, REVERSAL_OTHER_PROJECT, "跨项目分摊审批");
        jdbc.update("""
                INSERT INTO finance_cost_allocation_batch
                (id,tenant_id,batch_code,source_type,source_id,source_amount,allocation_basis,accounting_period,
                 cost_subject_id,idempotency_key,status,approval_instance_id,posted_by,posted_at)
                VALUES (?,0,'FCAB-CLOSEOUT-REV','ACCOUNTING_ENTRY_LINE',1,100,'DIRECT_PROJECT','2026-07',
                        ?,'closeout-reversal','POSTED',?,1,CURRENT_TIMESTAMP)
                """, REVERSAL_BATCH, OVERHEAD_SUBJECT, REVERSAL_APPROVAL);
        jdbc.update("""
                INSERT INTO finance_cost_allocation_line
                (id,tenant_id,batch_id,project_id,basis_value,allocated_amount)
                VALUES (?,0,?,?,1,50),(?,0,?,?,1,50)
                """, REVERSAL_LINE_FIRST, REVERSAL_BATCH, REVERSAL_OTHER_PROJECT,
                REVERSAL_LINE_CURRENT, REVERSAL_BATCH, PROJECT);
        jdbc.update("""
                INSERT INTO cost_reversal_request
                (id,tenant_id,request_code,target_type,target_id,project_id,status,version,created_by,reason)
                VALUES (?,0,'CRR-CLOSEOUT-REV','FINANCE_ALLOCATION',?,?,'DRAFT',0,1,'跨项目分摊冲销')
                """, REVERSAL_REQUEST, REVERSAL_BATCH, REVERSAL_OTHER_PROJECT);

        assertTrue(gateCodes().contains("CONSTRUCTION_COST_REVERSAL_OPEN"),
                "冲销申请头绑定其他项目时，仍须阻断明细涉及项目关闭");
    }

    @Test
    void activeQualityPlanBlocksConstructionCompletion() {
        assertFalse(gateCodes().contains("CONSTRUCTION_QUALITY_PLAN_OPEN"));
        jdbc.update("UPDATE qs_inspection_plan SET status='ACTIVE' WHERE id=?", QUALITY_PLAN);
        assertTrue(gateCodes().contains("CONSTRUCTION_QUALITY_PLAN_OPEN"));
    }

    @Test
    void ignoresSupersededScheduleTasksInCloseoutReadiness() {
        long closeoutId = id(service.initiate(new InitiateCommand(PROJECT, "PC-SUPERSEDED", LocalDate.now(), null)));
        long sectionId = id(service.createSectionAcceptance(closeoutId, new SectionAcceptanceCommand(
                WBS, QUALITY_INSPECTION, "SA-SUPERSEDED", "当前计划验收", LocalDate.now(), "PASS", null)));
        evidence("CLOSEOUT_SECTION_ACCEPTANCE", sectionId, "SECTION_ACCEPTANCE_RECORD");
        service.confirmSectionAcceptance(sectionId);
        jdbc.update("""
                INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,
                 planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'CLOSEOUT-SUPERSEDED','被取代计划','BASELINE',0,?,?,'SUPERSEDED',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, SUPERSEDED_SCHEDULE, PROJECT, LocalDate.now().minusMonths(4), LocalDate.now().minusMonths(1));
        jdbc.update("""
                INSERT INTO project_wbs_task(id,tenant_id,project_id,schedule_plan_id,task_code,task_name,work_area,
                 responsible_user_id,planned_start_date,planned_end_date,weight_percent,actual_progress,status,sort_order,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'WBS-SUPERSEDED','被取代历史任务','全场',1,?,?,100,0,'NOT_STARTED',1,0,
                 1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, SUPERSEDED_WBS, PROJECT, SUPERSEDED_SCHEDULE,
                LocalDate.now().minusMonths(4), LocalDate.now().minusMonths(1));

        Map<String, Object> overview = service.overview(PROJECT);
        assertEquals(1L, ((Number) ((Map<?, ?>) overview.get("wbsReadiness")).get("totalTasks")).longValue());
        assertEquals(1, ((List<?>) overview.get("wbsTasks")).size());
        assertDoesNotThrow(() -> service.createFinalAcceptance(closeoutId, new FinalAcceptanceCommand(
                "FA-SUPERSEDED", LocalDate.now(), "建设单位", "参建单位", "PASS", "当前计划验收完成", null)));
    }

    private void collect(long receivableId, BigDecimal amount, String suffix) {
        long collectionId = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO collection_record(id,tenant_id,project_id,contract_id,customer_id,fund_account_id,
                 collection_code,external_txn_no,collected_at,amount,allocated_amount,unallocated_amount,payer_name,status,
                 attachment_count,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,?,0,'收尾测试业主','SUCCESS',1,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)
                """, collectionId, PROJECT, CONTRACT, PARTNER, FUND_ACCOUNT,
                "COL-" + suffix, "TXN-" + suffix, amount, amount, user(), user());
        jdbc.update("INSERT INTO collection_allocation(id,tenant_id,collection_id,receivable_id,allocated_amount,allocation_type,created_by,created_at) VALUES(?,0,?,?,?,'COLLECTION',?,CURRENT_TIMESTAMP)",
                IDS.incrementAndGet(), collectionId, receivableId, amount, user());
        jdbc.update("""
                INSERT INTO cash_journal_entry(id,tenant_id,entry_no,account_id,direction,amount,business_date,summary,
                 project_id,contract_id,source_type,source_id,collection_record_id,status,closure_due_at,archived_by,archived_at,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?, 'IN',?,CURRENT_DATE,'收尾回款归档',?,?,'COLLECTION_RECORD',?,?,'ARCHIVED',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,
                 ?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)
                """, IDS.incrementAndGet(), "CJ-CLOSEOUT-" + suffix, FUND_ACCOUNT, amount, PROJECT, CONTRACT,
                collectionId, collectionId, user(), user(), user());
        jdbc.update("UPDATE account_receivable SET collected_amount=collected_amount+?,outstanding_amount=outstanding_amount-?,status='COLLECTED',version=version+1 WHERE id=?",
                amount, amount, receivableId);
    }

    private long id(Map<?, ?> row) { return ((Number) row.get("id")).longValue(); }

    private void evidence(String businessType, long businessId, String documentType) {
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,
                 content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,'evidence.pdf','evidence.pdf',100,'application/pdf',?,'test','CLEAN',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)
                """, id, businessType, documentType, businessId, businessType + "/" + businessId + "/" + id + ".pdf", user(), user());
    }

    private void asUser(long userId) {
        UserContext.set(Jwts.claims().subject("closeout-" + userId).add("userId", userId).add("username", "closeout-" + userId)
                .add("tenantId", 0L).add("roleCodes", List.of("ADMIN")).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "closeout-" + userId, "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    private Long user() { return UserContext.getCurrentUserId(); }

    private List<String> gateCodes() {
        return gate.constructionCompletion(0L, PROJECT).stream().map(ProjectCloseGateService.GateBlocker::gateCode).toList();
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
                workflowEngine.approve(task.getId(), 1L, "admin", "同意", "closeout-it-" + UUID.randomUUID());
            }
        }
        assertEquals("APPROVED", instanceMapper.selectById(instance.getId()).getInstanceStatus());
    }

    private void cleanup() {
        jdbc.update("DELETE FROM cost_reversal_request WHERE id=?", REVERSAL_REQUEST);
        jdbc.update("DELETE FROM finance_cost_allocation_line WHERE id IN(?,?)", REVERSAL_LINE_FIRST, REVERSAL_LINE_CURRENT);
        jdbc.update("DELETE FROM finance_cost_allocation_batch WHERE id=?", REVERSAL_BATCH);
        jdbc.update("DELETE FROM wf_instance WHERE id=?", REVERSAL_APPROVAL);
        jdbc.update("DELETE FROM pm_project WHERE id=?", REVERSAL_OTHER_PROJECT);
        jdbc.update("DELETE FROM cost_item WHERE id=?", OVERHEAD_CLEARING);
        jdbc.update("DELETE FROM cost_item WHERE id=?", OVERHEAD_SOURCE);
        jdbc.update("DELETE FROM cost_item WHERE id=?", UNCLASSIFIED_COST);
        jdbc.update("DELETE FROM overhead_allocation_run WHERE rule_id=?", OVERHEAD_RULE);
        jdbc.update("DELETE FROM overhead_allocation_rule WHERE id=?", OVERHEAD_RULE);
        jdbc.update("DELETE FROM cost_subject WHERE id=?", OVERHEAD_SUBJECT);
        jdbc.update("DELETE FROM tech_acceptance_archive WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_construction_reference WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_disclosure WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_rfi WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_drawing_review WHERE project_id=?", PROJECT);
        jdbc.update("UPDATE tech_drawing SET current_version_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_drawing_version WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_drawing WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM site_daily_log WHERE id=?", DAILY_LOG);
        jdbc.update("DELETE FROM sys_file WHERE business_type IN('CLOSEOUT_SECTION_ACCEPTANCE','CLOSEOUT_FINAL_ACCEPTANCE','CLOSEOUT_DEFECT','CLOSEOUT_WARRANTY','CLOSEOUT_ARCHIVE_TRANSFER')");
        jdbc.update("DELETE FROM closeout_archive_transfer WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM closeout_defect WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM closeout_warranty WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM closeout_final_acceptance WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM closeout_section_acceptance WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM project_closeout WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='PROJECT_FINAL_ACCEPTANCE')", PROJECT);
        jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='PROJECT_FINAL_ACCEPTANCE')", PROJECT);
        jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='PROJECT_FINAL_ACCEPTANCE')", PROJECT);
        jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='PROJECT_FINAL_ACCEPTANCE')", PROJECT);
        jdbc.update("DELETE FROM wf_instance WHERE project_id=? AND business_type='PROJECT_FINAL_ACCEPTANCE'", PROJECT);
        jdbc.update("DELETE FROM cash_journal_entry WHERE project_id=? AND source_type='COLLECTION_RECORD'", PROJECT);
        jdbc.update("DELETE FROM collection_allocation WHERE receivable_id IN(?,?)", REGULAR_RECEIVABLE, RETENTION_RECEIVABLE);
        jdbc.update("DELETE FROM collection_record WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM account_receivable WHERE settlement_id=?", SETTLEMENT);
        jdbc.update("DELETE FROM owner_settlement WHERE id=?", SETTLEMENT);
        jdbc.update("DELETE FROM fund_account WHERE id=?", FUND_ACCOUNT);
        jdbc.update("DELETE FROM qs_issue WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_inspection_record WHERE id IN(?,?)", QUALITY_INSPECTION, SAFETY_INSPECTION);
        jdbc.update("DELETE FROM qs_inspection_plan WHERE id IN(?,?)", QUALITY_PLAN, SAFETY_PLAN);
        jdbc.update("DELETE FROM project_wbs_task WHERE id IN(?,?)", WBS, SUPERSEDED_WBS);
        jdbc.update("DELETE FROM project_schedule_plan WHERE id IN(?,?)", SCHEDULE, SUPERSEDED_SCHEDULE);
        jdbc.update("DELETE FROM ct_contract WHERE id=?", CONTRACT);
        jdbc.update("DELETE FROM md_partner WHERE id=?", PARTNER);
        jdbc.update("DELETE FROM pm_project_member WHERE id=?", PROJECT_MEMBER);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
        jdbc.update("DELETE FROM sys_user WHERE id IN(?,?)", RESPONSIBLE_USER, OUTSIDE_USER);
    }
}
