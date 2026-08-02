package com.cgcpms.cost.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("CostTargetWorkflowHandler — approval lifecycle tests")
class CostTargetWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private CostTargetWorkflowHandler handler;

    @Autowired
    private CostTargetMapper costTargetMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("supportBusinessType -> COST_TARGET")
    void testSupportBusinessType() {
        assertEquals("COST_TARGET", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical(), "目标成本审批处理器应标记为关键");
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> DRAFT→APPROVED, activates version")
    void testOnApproved_Success() {
        long projectId = 93000001L;
        jdbc.update("""
                INSERT INTO pm_project
                  (id,tenant_id,project_code,project_name,status,approval_status,deleted_flag)
                VALUES (?,0,?,'项目成本预算审批测试','PREPARING','APPROVED',0)
                """, projectId, "CT-HDLR-PROJECT-" + System.nanoTime());
        jdbc.update("""
                INSERT INTO pm_project_member
                  (id,tenant_id,project_id,user_id,role_code,status,deleted_flag)
                VALUES (?,0,?,1,'CSTM','ACTIVE',0)
                """, projectId + 1, projectId);
        CostTarget target = new CostTarget();
        target.setProjectId(projectId);
        target.setVersionNo("V1.0");
        target.setVersionName("CT-HDLR-TEST-" + System.nanoTime());
        target.setApprovalStatus("APPROVING");
        target.setStatus("APPROVING");
        target.setTenantId(0L);
        target.setTotalTargetAmount(new BigDecimal("100.00"));
        target.setTotalBidCostAmount(new BigDecimal("100.00"));
        target.setTotalResponsibilityAmount(new BigDecimal("100.00"));
        costTargetMapper.insert(target);
        Long subjectId = jdbc.queryForObject("""
                SELECT s.id FROM cost_subject s
                 WHERE s.tenant_id=0 AND s.deleted_flag=0 AND s.status='ENABLE'
                   AND NOT EXISTS (SELECT 1 FROM cost_subject c
                                    WHERE c.tenant_id=s.tenant_id AND c.parent_id=s.id AND c.deleted_flag=0)
                 LIMIT 1
                """, Long.class);
        jdbc.update("""
                INSERT INTO cost_target_item
                  (id,tenant_id,target_id,project_id,cost_subject_id,target_amount,bid_cost_amount,
                   responsibility_amount,responsible_user_id,responsibility_unit,sort_order,deleted_flag)
                VALUES (?,0,?,?,?,?,?,?,1,'项目部',1,0)
                """, target.getId() + 1, target.getId(), target.getProjectId(), subjectId,
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"));
        attachWorkflow(target, 2200001L);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(target.getId());
        instance.setId(2200001L);
        instance.setTenantId(TENANT_0);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        CostTarget updated = costTargetMapper.selectById(target.getId());
        assertNotNull(updated, "目标成本应仍然存在");
        assertEquals("APPROVED", updated.getApprovalStatus(), "审批状态应变为 APPROVED");
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_budget
                 WHERE tenant_id=0 AND project_id=? AND source_cost_target_id=?
                   AND approval_status='APPROVED' AND status='ACTIVE' AND active_flag=1
                """, Integer.class, target.getProjectId(), target.getId()));
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessIdGuard() {
        WfInstance instance = new WfInstance();
        instance.setId(2200002L);
        instance.setBusinessId(null);
        instance.setTenantId(TENANT_0);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        assertThrows(IllegalStateException.class, () -> handler.onApproved(ctx),
                "businessId 为 null 时应抛出 IllegalStateException");
    }

    @Test
    @Transactional
    @DisplayName("onRejected -> APPROVING→REJECTED")
    void testOnRejected() {
        CostTarget target = new CostTarget();
        target.setProjectId(10001L);
        target.setVersionNo("V1.0");
        target.setVersionName("CT-HDLR-REJ-" + System.nanoTime());
        target.setApprovalStatus("APPROVING");
        target.setStatus("APPROVING");
        target.setTenantId(0L);
        costTargetMapper.insert(target);
        attachWorkflow(target, 2200003L);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(target.getId());
        instance.setId(2200003L);
        instance.setTenantId(TENANT_0);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);

        CostTarget updated = costTargetMapper.selectById(target.getId());
        assertEquals("REJECTED", updated.getApprovalStatus(), "审批状态应变为 REJECTED");
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> APPROVING→DRAFT")
    void testOnWithdrawn() {
        CostTarget target = new CostTarget();
        target.setProjectId(10001L);
        target.setVersionNo("V1.0");
        target.setVersionName("CT-HDLR-WTH-" + System.nanoTime());
        target.setApprovalStatus("APPROVING");
        target.setStatus("APPROVING");
        target.setTenantId(0L);
        costTargetMapper.insert(target);
        attachWorkflow(target, 2200004L);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(target.getId());
        instance.setId(2200004L);
        instance.setTenantId(TENANT_0);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);

        CostTarget updated = costTargetMapper.selectById(target.getId());
        assertEquals("DRAFT", updated.getApprovalStatus(), "撤回后审批状态应变为 DRAFT");
    }

    private void attachWorkflow(CostTarget target, long instanceId) {
        jdbc.update("""
                INSERT INTO wf_instance
                  (id,tenant_id,template_id,business_type,business_id,title,instance_status,initiator_id,deleted_flag)
                VALUES (?,0,1,'COST_TARGET',?,'目标成本测试','RUNNING',1,0)
                """, instanceId, target.getId());
        jdbc.update("UPDATE cost_target SET approval_instance_id=? WHERE id=?", instanceId, target.getId());
    }
}
