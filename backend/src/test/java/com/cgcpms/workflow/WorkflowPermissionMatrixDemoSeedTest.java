package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.service.AuthService;
import com.cgcpms.workflow.service.WorkflowQueryService;
import com.cgcpms.workflow.vo.WfCcVO;
import com.cgcpms.workflow.vo.WfMyInstanceVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class WorkflowPermissionMatrixDemoSeedTest {

    private static final long TENANT_ID = 0L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkflowQueryService workflowQueryService;

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("V110 workflow-only 账号具备三类流程样本但不带业务查询权限")
    void v110WorkflowOnlySeedCreatesIsolatedWorkflowSamples() {
        long userId = findUserId("demo_workflow_only");

        assertFalse(authService.getPermissionCodes(userId).contains("contract:query"));
        assertFalse(authService.getPermissionCodes(userId).contains("purchase:request:list"));
        assertFalse(authService.getPermissionCodes(userId).contains("subcontract:measure:query"));

        assertEquals(3L, count("""
                SELECT COUNT(*)
                FROM wf_instance
                WHERE tenant_id = 0
                  AND initiator_id = ?
                  AND deleted_flag = 0
                  AND remark = 'V110审批中心权限矩阵workflow-only样本'
                """, userId));

        assertEquals(3L, count("""
                SELECT COUNT(DISTINCT business_type)
                FROM wf_instance
                WHERE tenant_id = 0
                  AND initiator_id = ?
                  AND deleted_flag = 0
                  AND remark = 'V110审批中心权限矩阵workflow-only样本'
                """, userId));

        IPage<WfMyInstanceVO> page = workflowQueryService.getMyStarted(TENANT_ID, userId, 1, 20);
        assertEquals(3L, page.getTotal());
        assertEquals(3, page.getRecords().size());
    }

    @Test
    @DisplayName("V110 cc-readonly 账号仅补最小抄送样本")
    void v110CcReadonlySeedCreatesReadonlyCcSamples() {
        long userId = findUserId("demo_cc_readonly");

        assertFalse(authService.getPermissionCodes(userId).contains("contract:query"));
        assertFalse(authService.getPermissionCodes(userId).contains("purchase:request:list"));
        assertFalse(authService.getPermissionCodes(userId).contains("subcontract:measure:query"));

        assertEquals(2L, count("""
                SELECT COUNT(*)
                FROM wf_cc
                WHERE tenant_id = 0
                  AND cc_user_id = ?
                  AND id IN (980000000000051001, 980000000000051002)
                """, userId));

        IPage<WfCcVO> page = workflowQueryService.getMyCc(userId, TENANT_ID, 1, 20);
        assertTrue(page.getTotal() >= 2);
        assertEquals(2L, page.getRecords().stream()
                .filter(cc -> "980000000000051001".equals(cc.getId())
                        || "980000000000051002".equals(cc.getId()))
                .count());
    }

    @Test
    @DisplayName("V110 non-participant 账号无流程参与关系")
    void v110NonParticipantSeedKeepsUserOutOfWorkflowRelations() {
        long userId = findUserId("demo_non_participant");

        assertEquals(0L, count("""
                SELECT COUNT(*)
                FROM wf_instance
                WHERE tenant_id = 0
                  AND initiator_id = ?
                  AND deleted_flag = 0
                """, userId));
        assertEquals(0L, count("""
                SELECT COUNT(*)
                FROM wf_task
                WHERE tenant_id = 0
                  AND approver_id = ?
                  AND deleted_flag = 0
                """, userId));
        assertEquals(0L, count("""
                SELECT COUNT(*)
                FROM wf_cc
                WHERE tenant_id = 0
                  AND cc_user_id = ?
                """, userId));

        assertEquals(0L, workflowQueryService.getMyStarted(TENANT_ID, userId, 1, 20).getTotal());
        assertEquals(0L, workflowQueryService.getMyTodos(TENANT_ID, userId, 1, 20).getTotal());
        assertEquals(0L, workflowQueryService.getMyCc(userId, TENANT_ID, 1, 20).getTotal());
    }

    private long findUserId(String username) {
        Long userId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM sys_user
                WHERE tenant_id = 0
                  AND username = ?
                  AND deleted_flag = 0
                """, Long.class, username);
        if (userId == null) {
            throw new IllegalStateException("User not found: " + username);
        }
        return userId;
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
