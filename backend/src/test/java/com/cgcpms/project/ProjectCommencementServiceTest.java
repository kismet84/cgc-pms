package com.cgcpms.project;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.ProjectCommencement;
import com.cgcpms.project.handler.ProjectCommencementWorkflowHandler;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.ProjectCommencementMapper;
import com.cgcpms.project.service.ProjectLifecycleService;
import com.cgcpms.project.vo.ProjectActivationReadinessVO;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
@DisplayName("项目开工准入服务端闭环")
class ProjectCommencementServiceTest {
    private static final long PROJECT = 96900001L;
    private static final long CONTRACT = 96900002L;
    private static final long TARGET = 96900003L;
    private static final long BUDGET = 96900004L;
    private static final long SCHEDULE = 96900005L;
    private static final long COMMENCEMENT = 96900006L;
    private static final long FILE = 96900007L;

    @Autowired JdbcTemplate jdbc;
    @Autowired PmProjectMapper projectMapper;
    @Autowired ProjectCommencementMapper commencementMapper;
    @Autowired ProjectLifecycleService lifecycleService;
    @Autowired ProjectCommencementWorkflowHandler handler;

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                .add("tenantId", 0L).add("roleCodes", List.of("ADMIN")).build());
        jdbc.update("""
                INSERT INTO pm_project(id,tenant_id,project_code,project_name,contract_amount,target_cost,
                  status,approval_status,initiation_basis,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,'ML69-COMMENCEMENT','主线69开工准入测试',0,0,'PREPARING','APPROVED','DIRECT_APPROVAL',
                  1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, PROJECT);
        jdbc.update("""
                INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,
                  party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,
                  version,created_at,updated_at,deleted_flag)
                VALUES(?,0,?,'ML69-MAIN','主线69权威主合同','MAIN',20001,20002,1000,1000,0,'PERFORMING','APPROVED',
                  0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, CONTRACT, PROJECT);
        jdbc.update("UPDATE pm_project SET owner_contract_id=?,contract_amount=1000 WHERE id=?", CONTRACT, PROJECT);
        jdbc.update("""
                INSERT INTO cost_target(id,tenant_id,project_id,version_no,version_name,total_target_amount,
                  total_bid_cost_amount,total_responsibility_amount,source_contract_id,source_contract_amount,
                  target_cost_rate,is_active,approval_status,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'ML69-V1','主线69目标成本',850,0,850,?,1000,0.85,1,'APPROVED','ACTIVE',0,
                  1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, TARGET, PROJECT, CONTRACT);
        jdbc.update("""
                INSERT INTO project_budget(id,tenant_id,project_id,budget_code,version_no,budget_name,total_amount,
                  source_cost_target_id,approval_status,status,active_flag,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'ML69-BUDGET','V1','主线69执行预算',850,?,'APPROVED','ACTIVE',1,0,
                  1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, BUDGET, PROJECT, TARGET);
        jdbc.update("""
                INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,
                  planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,'ML69-WBS','主线69WBS','BASELINE',1,CURRENT_DATE,DATEADD('DAY',30,CURRENT_DATE),
                  'ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, SCHEDULE, PROJECT);
        jdbc.update("""
                INSERT INTO project_commencement(id,tenant_id,project_id,planned_start_date,basis_type,approval_status,
                  version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,CURRENT_DATE,'OWNER_NOTICE','APPROVING',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, COMMENCEMENT, PROJECT);
        jdbc.update("""
                INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,
                  file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,
                  created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,'PROJECT_COMMENCEMENT','COMMENCEMENT_BASIS',?,'basis.pdf','basis.pdf',100,
                  'application/pdf','PROJECT_COMMENCEMENT/test/basis.pdf','test','CLEAN',CURRENT_TIMESTAMP,
                  1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, FILE, COMMENCEMENT);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("开工审批通过集中CAS启用项目并可幂等回调")
    void approvalActivatesProjectOnce() {
        handler.onApproved(context());

        PmProject project = projectMapper.selectById(PROJECT);
        ProjectCommencement commencement = commencementMapper.selectById(COMMENCEMENT);
        assertEquals("ACTIVE", project.getStatus());
        assertEquals(LocalDate.now(), project.getActualStartDate());
        assertEquals("APPROVED", commencement.getApprovalStatus());
        assertEquals(PROJECT, lifecycleService.activateFromCommencementApproval(PROJECT, 0L, COMMENCEMENT).getId());
        assertDoesNotThrow(() -> handler.onApproved(context()));
    }

    @Test
    @DisplayName("旧审批回调即使全部准入事实存在也不再自动启用")
    void legacyCallbackCannotActivate() {
        jdbc.update("UPDATE project_commencement SET approval_status='APPROVED',actual_start_date=CURRENT_DATE WHERE id=?", COMMENCEMENT);
        assertFalse(lifecycleService.activateIfReady(PROJECT, 0L));
        assertEquals("PREPARING", projectMapper.selectById(PROJECT).getStatus());
    }

    @Test
    @DisplayName("历史未知来源保持阻塞且不猜测启用")
    void legacySourceFailsClosed() {
        jdbc.update("UPDATE pm_project SET initiation_basis='LEGACY_UNCLASSIFIED' WHERE id=?", PROJECT);
        ProjectActivationReadinessVO readiness = lifecycleService.getActivationReadiness(PROJECT);
        assertFalse(readiness.ready());
        assertTrue(readiness.blockers().contains("PROJECT_INITIATION_BASIS_INVALID"));
    }

    private WorkflowContext context() {
        WfInstance instance = new WfInstance();
        instance.setBusinessId(COMMENCEMENT);
        instance.setTenantId(0L);
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);
        return context;
    }
}
