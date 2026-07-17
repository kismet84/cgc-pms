package com.cgcpms.tech;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.tech.dto.TechnicalManagementModels.*;
import com.cgcpms.tech.service.TechnicalManagementService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class TechnicalManagementClosedLoopIntegrationTest {
    private static final long PROJECT = 99190001L;
    private static final long SCHEDULE = 99190002L;
    private static final long WBS = 99190003L;
    private static final long DAILY_LOG = 99190004L;
    private static final long DAILY_PROGRESS = 99190005L;
    private static final long QUALITY_PLAN = 99190006L;
    private static final long QUALITY_INSPECTION = 99190007L;
    private static final long WEEKLY_PLAN = 99190008L;
    private static final AtomicLong FILE_ID = new AtomicLong(99190100L);

    @Autowired TechnicalManagementService service;
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
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'TECH-IT','技术闭环测试项目','ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("""
                INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,
                 planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'TECH-SCHEDULE','技术闭环施工计划','BASELINE',1,?,?, 'ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, SCHEDULE, PROJECT, LocalDate.now().minusDays(10), LocalDate.now().plusDays(30));
        jdbc.update("""
                INSERT INTO project_wbs_task(id,tenant_id,project_id,schedule_plan_id,task_code,task_name,work_area,
                 responsible_user_id,planned_start_date,planned_end_date,weight_percent,planned_quantity,unit,
                 actual_quantity,actual_progress,status,sort_order,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'TECH-WBS','主体结构施工','1号楼',1,?,?,100,100,'%',20,20,'IN_PROGRESS',1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, WBS, PROJECT, SCHEDULE, LocalDate.now().minusDays(10), LocalDate.now().plusDays(30));
        jdbc.update("""
                INSERT INTO project_period_plan(id,tenant_id,project_id,schedule_plan_id,period_type,period_code,period_name,
                 start_date,end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'WEEKLY','TECH-WEEK','技术闭环周计划',?,?,'APPROVED',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, WEEKLY_PLAN, PROJECT, SCHEDULE, LocalDate.now().minusDays(3), LocalDate.now().plusDays(3));
        jdbc.update("""
                INSERT INTO site_daily_log(id,tenant_id,project_id,report_date,construction_content,status,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'按批准图纸完成主体结构施工','SUBMITTED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, DAILY_LOG, PROJECT, LocalDate.now());
        jdbc.update("""
                INSERT INTO site_daily_progress(id,tenant_id,daily_log_id,project_id,schedule_plan_id,weekly_plan_id,
                 wbs_task_id,previous_progress,current_progress,completed_quantity,work_description,
                 created_by,created_at,updated_by,updated_at)
                VALUES(?,0,?,?,?,?,?,10,20,20,'主体结构施工进度',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)
                """, DAILY_PROGRESS, DAILY_LOG, PROJECT, SCHEDULE, WEEKLY_PLAN, WBS);
        jdbc.update("""
                INSERT INTO qs_inspection_plan(id,tenant_id,project_id,plan_code,plan_name,inspection_type,frequency_type,
                 start_date,end_date,owner_user_id,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'TECH-QP','主体结构验收计划','QUALITY','SINGLE',?,?,1,'ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, QUALITY_PLAN, PROJECT, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        jdbc.update("""
                INSERT INTO qs_inspection_record(id,tenant_id,plan_id,project_id,inspection_code,inspection_date,location,
                 inspector_user_id,conclusion,summary,status,submitted_by,submitted_at,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'TECH-QI',?,'1号楼',1,'PASS','主体结构按批准图纸验收通过','SUBMITTED',1,CURRENT_TIMESTAMP,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, QUALITY_INSPECTION, QUALITY_PLAN, PROJECT, LocalDate.now());
    }

    @AfterEach
    void teardown() {
        cleanup();
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void closesSchemeDrawingReviewRfiRevisionDisclosureConstructionAndArchive() {
        long schemeId = id(service.createScheme(new SchemeCommand(PROJECT, "TS-001", "主体结构专项施工方案",
                "SPECIAL", 1L, LocalDate.now().plusDays(2), "专项方案")));
        evidence("TECH_SCHEME", schemeId, "SCHEME_FILE");
        service.submitScheme(schemeId);
        approveAll("TECHNICAL_SCHEME", schemeId);
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM technical_scheme WHERE id=?", String.class, schemeId));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT item_status FROM tech_item WHERE source_type='TECHNICAL_SCHEME' AND source_id=?", String.class, schemeId));

        Map<String, Object> received = service.receiveDrawing(new DrawingReceiptCommand(PROJECT, "DR-001", "主体结构施工图",
                "结构", "设计院", "A", LocalDateTime.now(), "首次接收", null));
        long drawingId = id((Map<?, ?>) received.get("drawing"));
        long versionA = id((Map<?, ?>) ((List<?>) received.get("versions")).get(0));
        evidence("TECH_DRAWING_VERSION", versionA, "DRAWING_FILE");
        long reviewA = id(service.createReview(versionA, new ReviewCommand("RV-001", LocalDate.now(), 1L,
                "项目总工、工程部、施工班组", "CONDITIONAL", "节点详图标注不明确", true, null)));
        evidence("TECH_DRAWING_REVIEW", reviewA, "REVIEW_MINUTES");
        service.confirmReview(reviewA);

        long rfiId = id(service.createRfi(reviewA, new RfiCommand("RFI-001", "节点详图标高确认",
                "请设计单位确认节点标高及钢筋锚固做法", "HIGH", LocalDate.now().plusDays(3), null)));
        evidence("TECH_RFI", rfiId, "RFI_EVIDENCE");
        service.submitRfi(rfiId);
        long responseId = id(service.respondRfi(rfiId, new RfiResponseCommand(
                "原图标高有误，按正式变更版B执行", true, "设计院张工")));
        evidence("TECH_RFI_RESPONSE", responseId, "DESIGN_RESPONSE");
        asUser(2L);
        service.reviewResponse(responseId, new ResponseReviewCommand("ACCEPTED", "回复明确，需要接收改版图纸"));
        assertEquals("CHANGE_PENDING", jdbc.queryForObject("SELECT status FROM tech_rfi WHERE id=?", String.class, rfiId));

        long versionB = id(service.receiveVersion(drawingId, new DrawingVersionCommand("B", versionA, rfiId,
                LocalDateTime.now(), "根据RFI-001修正节点标高与锚固做法", null)));
        evidence("TECH_DRAWING_VERSION", versionB, "DRAWING_FILE");
        long reviewB = id(service.createReview(versionB, new ReviewCommand("RV-002", LocalDate.now(), 2L,
                "项目总工、工程部、施工班组", "PASS", "改版内容完整，同意用于施工", false, null)));
        evidence("TECH_DRAWING_REVIEW", reviewB, "REVIEW_MINUTES");
        service.confirmReview(reviewB);
        assertEquals("SUPERSEDED", jdbc.queryForObject("SELECT status FROM tech_drawing_version WHERE id=?", String.class, versionA));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM tech_drawing_version WHERE id=?", String.class, versionB));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM tech_rfi WHERE id=?", String.class, rfiId));

        long disclosureId = id(service.createDisclosure(PROJECT, new DisclosureCommand(versionB, schemeId,
                "TD-001", "主体结构B版图纸技术交底", LocalDate.now(), 2L,
                "工程部及主体结构施工班组", "按B版图纸、专项方案和RFI回复组织施工", null)));
        evidence("TECH_DISCLOSURE", disclosureId, "DISCLOSURE_RECORD");
        service.confirmDisclosure(disclosureId);
        long referenceId = id(service.createConstructionReference(PROJECT, new ConstructionReferenceCommand(
                disclosureId, DAILY_LOG, WBS, LocalDate.now(), "1号楼", "主体结构施工引用B版图纸", null)));
        long archiveId = id(service.createArchive(PROJECT, new ArchiveCommand(referenceId, QUALITY_INSPECTION,
                "TA-001", LocalDate.now(), "PASS", "项目技术档案/主体结构", null)));
        evidence("TECH_ARCHIVE", archiveId, "ACCEPTANCE_ARCHIVE");
        service.confirmArchive(archiveId);

        Map<String, Object> overview = service.overview(PROJECT);
        assertEquals(1, ((List<?>) overview.get("responses")).size());
        assertEquals("ACCEPTED", ((Map<?, ?>) ((List<?>) overview.get("responses")).get(0)).get("REVIEWSTATUS"));

        Map<String, Object> trace = service.trace(drawingId);
        assertEquals(2, ((List<?>) trace.get("versions")).size());
        assertEquals(2, ((List<?>) trace.get("reviews")).size());
        assertEquals(1, ((List<?>) trace.get("rfis")).size());
        assertEquals(1, ((List<?>) trace.get("responses")).size());
        assertEquals(1, ((List<?>) trace.get("disclosures")).size());
        assertEquals(1, ((List<?>) trace.get("schemes")).size());
        assertEquals(1, ((List<?>) trace.get("schemeApprovals")).size());
        assertEquals(1, ((List<?>) trace.get("constructionReferences")).size());
        assertEquals(1, ((List<?>) trace.get("archives")).size());
        assertEquals("ARCHIVED", jdbc.queryForObject("SELECT status FROM tech_acceptance_archive WHERE id=?", String.class, archiveId));
    }

    @Test
    void rejectsMissingEvidenceSelfReviewAndStaleDrawingUse() {
        Map<String, Object> received = service.receiveDrawing(new DrawingReceiptCommand(PROJECT, "DR-EDGE", "边界图纸",
                "结构", "设计院", "A", LocalDateTime.now(), null, null));
        long version = id((Map<?, ?>) ((List<?>) received.get("versions")).get(0));
        BusinessException missing = assertThrows(BusinessException.class, () -> service.createReview(version,
                new ReviewCommand("RV-EDGE", LocalDate.now(), 1L, "会审人员", "PASS", "通过", false, null)));
        assertEquals("TECH_ATTACHMENT_REQUIRED", missing.getCode());

        evidence("TECH_DRAWING_VERSION", version, "DRAWING_FILE");
        long review = id(service.createReview(version, new ReviewCommand("RV-EDGE", LocalDate.now(), 1L,
                "会审人员", "CONDITIONAL", "需要设计澄清", true, null)));
        evidence("TECH_DRAWING_REVIEW", review, "REVIEW_MINUTES");
        service.confirmReview(review);
        long rfi = id(service.createRfi(review, new RfiCommand("RFI-EDGE", "设计澄清", "请确认做法", "NORMAL",
                LocalDate.now().plusDays(1), null)));
        evidence("TECH_RFI", rfi, "RFI_EVIDENCE");
        service.submitRfi(rfi);
        long response = id(service.respondRfi(rfi, new RfiResponseCommand("按原图执行", false, "设计院")));
        evidence("TECH_RFI_RESPONSE", response, "DESIGN_RESPONSE");
        BusinessException selfReview = assertThrows(BusinessException.class, () -> service.reviewResponse(response,
                new ResponseReviewCommand("ACCEPTED", "同意")));
        assertEquals("TECH_RFI_RESPONSE_REVIEWER_CONFLICT", selfReview.getCode());

        authenticate("technical:drawing:receive");
        assertDoesNotThrow(() -> fileAuthorizer.checkUploadAccess("TECH_DRAWING_VERSION", version));
        assertEquals("TECH_DOCUMENT_STAGE_INVALID", assertThrows(BusinessException.class,
                () -> fileAuthorizer.checkVariationDocumentStage("TECH_DRAWING_VERSION", version, "RFI_EVIDENCE")).getCode());
    }

    private long id(Map<?, ?> row) {
        return ((Number) row.get("id")).longValue();
    }

    private void evidence(String businessType, long businessId, String documentType) {
        long id = FILE_ID.incrementAndGet();
        jdbc.update("""
                INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,
                 content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,'evidence.pdf','evidence.pdf',100,'application/pdf',?,'test','CLEAN',CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, id, businessType, documentType, businessId, businessType + "/" + businessId + "/" + id + ".pdf");
    }

    private void asUser(long userId) {
        UserContext.set(Jwts.claims().subject("tech-" + userId).add("userId", userId).add("username", "tech-" + userId)
                .add("tenantId", 0L).add("roleCodes", List.of("ADMIN")).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tech-" + userId, "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "n/a", List.of(new SimpleGrantedAuthority(authority))));
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
                workflowEngine.approve(task.getId(), 1L, "admin", "同意", "technical-it-" + UUID.randomUUID());
            }
        }
        assertEquals("APPROVED", instanceMapper.selectById(instance.getId()).getInstanceStatus());
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_file WHERE business_type IN('TECH_SCHEME','TECH_DRAWING_VERSION','TECH_DRAWING_REVIEW','TECH_RFI','TECH_RFI_RESPONSE','TECH_DISCLOSURE','TECH_ARCHIVE')");
        jdbc.update("DELETE FROM tech_acceptance_archive WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_construction_reference WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_disclosure WHERE project_id=?", PROJECT);
        jdbc.update("UPDATE tech_drawing_version SET source_rfi_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_rfi_response WHERE rfi_id IN(SELECT id FROM tech_rfi WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM tech_rfi WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_drawing_review WHERE project_id=?", PROJECT);
        jdbc.update("UPDATE tech_drawing_version SET previous_version_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_drawing_version WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_drawing WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM tech_item WHERE project_id=? AND source_type IN('TECHNICAL_SCHEME','TECH_RFI')", PROJECT);
        jdbc.update("DELETE FROM technical_scheme WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='TECHNICAL_SCHEME')", PROJECT);
        jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='TECHNICAL_SCHEME')", PROJECT);
        jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='TECHNICAL_SCHEME')", PROJECT);
        jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='TECHNICAL_SCHEME')", PROJECT);
        jdbc.update("DELETE FROM wf_instance WHERE project_id=? AND business_type='TECHNICAL_SCHEME'", PROJECT);
        jdbc.update("DELETE FROM qs_inspection_record WHERE id=?", QUALITY_INSPECTION);
        jdbc.update("DELETE FROM qs_inspection_plan WHERE id=?", QUALITY_PLAN);
        jdbc.update("DELETE FROM site_daily_progress WHERE id=?", DAILY_PROGRESS);
        jdbc.update("DELETE FROM site_daily_log WHERE id=?", DAILY_LOG);
        jdbc.update("DELETE FROM project_period_plan WHERE id=?", WEEKLY_PLAN);
        jdbc.update("DELETE FROM project_wbs_task WHERE id=?", WBS);
        jdbc.update("DELETE FROM project_schedule_plan WHERE id=?", SCHEDULE);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
    }
}
