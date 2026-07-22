package com.cgcpms.closeout;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.closeout.dto.ProjectCloseoutModels.*;
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

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
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
    private static final AtomicLong IDS = new AtomicLong(99191100L);

    @Autowired ProjectCloseoutService service;
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
                INSERT INTO qs_inspection_record(id,tenant_id,plan_id,project_id,inspection_code,inspection_date,location,
                 inspector_user_id,conclusion,summary,status,submitted_by,submitted_at,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'QI-CLOSEOUT',?,'全场',1,'PASS','单位工程质量验收通过','SUBMITTED',1,CURRENT_TIMESTAMP,0,
                 1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, QUALITY_INSPECTION, QUALITY_PLAN, PROJECT, LocalDate.now());
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
                VALUES(?,0,?,?,?,?, 'REGULAR','AR-REGULAR',900,0,0,900,?,'OPEN',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, REGULAR_RECEIVABLE, PROJECT, CONTRACT, SETTLEMENT, PARTNER, LocalDate.now());
        jdbc.update("""
                INSERT INTO account_receivable(id,tenant_id,project_id,contract_id,settlement_id,customer_id,receivable_type,
                 receivable_code,original_amount,collected_amount,credited_amount,outstanding_amount,due_date,status,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,?, 'RETENTION','AR-RETENTION',100,0,0,100,?,'OPEN',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, RETENTION_RECEIVABLE, PROJECT, CONTRACT, SETTLEMENT, PARTNER, LocalDate.now());
        jdbc.update("""
                INSERT INTO fund_account(id,tenant_id,account_code,account_name,account_type,opening_date,opening_balance,
                 enabled_flag,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,'FA-CLOSEOUT','收尾测试银行账户','BANK',?,0,1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, FUND_ACCOUNT, LocalDate.now().minusYears(1));
    }

    @AfterEach
    void teardown() {
        cleanup();
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void closesSectionFinalAcceptanceSettlementCollectionsWarrantyDefectArchiveAndProject() {
        BusinessException directClose = assertThrows(BusinessException.class,
                () -> projectService.transitionStatus(PROJECT, "CLOSED", "绕过收尾闭环"));
        assertEquals("PROJECT_CLOSEOUT_ACTION_REQUIRED", directClose.getCode());

        long closeoutId = id(service.initiate(new InitiateCommand(PROJECT, "PC-001", LocalDate.now(), "启动收尾")));
        long sectionId = id(service.createSectionAcceptance(closeoutId, new SectionAcceptanceCommand(
                WBS, QUALITY_INSPECTION, "SA-001", "单位工程分部分项验收", LocalDate.now(), "PASS", null)));
        assertEquals("CLOSEOUT_ATTACHMENT_REQUIRED", assertThrows(BusinessException.class,
                () -> service.confirmSectionAcceptance(sectionId)).getCode());
        evidence("CLOSEOUT_SECTION_ACCEPTANCE", sectionId, "SECTION_ACCEPTANCE_RECORD");
        service.confirmSectionAcceptance(sectionId);

        long finalId = id(service.createFinalAcceptance(closeoutId, new FinalAcceptanceCommand(
                "FA-001", LocalDate.now(), "建设单位", "建设、监理、设计、施工单位",
                "PASS", "工程实体、资料和功能验收通过", null)));
        evidence("CLOSEOUT_FINAL_ACCEPTANCE", finalId, "FINAL_ACCEPTANCE_CERTIFICATE");
        service.submitFinalAcceptance(finalId);
        approveAll("PROJECT_FINAL_ACCEPTANCE", finalId);
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM closeout_final_acceptance WHERE id=?", String.class, finalId));

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
        assertEquals("CLOSEOUT_RESPONSIBLE_PROJECT_MEMBER_INVALID", assertThrows(BusinessException.class,
                () -> service.createDefect(warrantyId, new DefectCommand(
                        "DF-OUTSIDE", "非成员缺陷", "责任人不属于项目", OUTSIDE_USER,
                        LocalDate.now().plusDays(7), null))).getCode());
        long defectId = id(service.createDefect(warrantyId, new DefectCommand(
                "DF-001", "屋面局部渗水", "雨后屋面局部出现渗水", RESPONSIBLE_USER, LocalDate.now().plusDays(7), null)));
        evidence("CLOSEOUT_DEFECT", defectId, "DEFECT_RECTIFICATION_EVIDENCE");
        service.rectifyDefect(defectId, new RectificationCommand("完成防水层修补并通过淋水试验"));
        assertEquals("CLOSEOUT_DEFECT_REVIEWER_CONFLICT", assertThrows(BusinessException.class,
                () -> service.verifyDefect(defectId, new DefectVerificationCommand("ACCEPTED", "复验通过"))).getCode());
        asUser(2L);
        service.verifyDefect(defectId, new DefectVerificationCommand("ACCEPTED", "复验通过，无渗漏"));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM closeout_defect WHERE id=?", String.class, defectId));

        assertEquals("CLOSEOUT_RETENTION_COLLECTION_INCOMPLETE", assertThrows(BusinessException.class,
                () -> service.releaseWarranty(warrantyId)).getCode());
        collect(RETENTION_RECEIVABLE, new BigDecimal("100.00"), "RETENTION");
        evidence("CLOSEOUT_WARRANTY", warrantyId, "WARRANTY_RELEASE_VOUCHER");
        service.releaseWarranty(warrantyId);

        long archiveId = id(service.createArchiveTransfer(closeoutId, new ArchiveTransferCommand(
                "AT-001", LocalDate.now(), "建设单位档案室", "档案管理员", "城建档案馆A区",
                "竣工图、验收记录、结算资料、质保与缺陷责任资料", null)));
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
        jdbc.update("DELETE FROM collection_allocation WHERE receivable_id IN(?,?)", REGULAR_RECEIVABLE, RETENTION_RECEIVABLE);
        jdbc.update("DELETE FROM collection_record WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM account_receivable WHERE settlement_id=?", SETTLEMENT);
        jdbc.update("DELETE FROM owner_settlement WHERE id=?", SETTLEMENT);
        jdbc.update("DELETE FROM fund_account WHERE id=?", FUND_ACCOUNT);
        jdbc.update("DELETE FROM qs_issue WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM qs_inspection_record WHERE id=?", QUALITY_INSPECTION);
        jdbc.update("DELETE FROM qs_inspection_plan WHERE id=?", QUALITY_PLAN);
        jdbc.update("DELETE FROM project_wbs_task WHERE id=?", WBS);
        jdbc.update("DELETE FROM project_schedule_plan WHERE id=?", SCHEDULE);
        jdbc.update("DELETE FROM ct_contract WHERE id=?", CONTRACT);
        jdbc.update("DELETE FROM md_partner WHERE id=?", PARTNER);
        jdbc.update("DELETE FROM pm_project_member WHERE id=?", PROJECT_MEMBER);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
        jdbc.update("DELETE FROM sys_user WHERE id IN(?,?)", RESPONSIBLE_USER, OUTSIDE_USER);
    }
}
