package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ApproverResolver, POC removal, and resubmit state machine.
 * <p>
 * Validates:
 * <ul>
 *   <li>ROLE-configured approver receives task (not submitter)</li>
 *   <li>Empty/no approverConfig → NO_APPROVER error</li>
 *   <li>Withdraw → resubmit creates new round with consistent roundNo, fresh nodes</li>
 *   <li>Reject → resubmit creates new round, old tasks canceled, fresh nodes</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Workflow ApproverResolver and Resubmit State Machine")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowApproverResolverTest {

    private static final long TENANT_0 = 0L;
    private static final long USER_SUBMITTER = TestUserContext.USER_ADMIN; // 1
    private static final long USER_APPROVER = 2L; // manager

    // Test-specific entity IDs (avoid collisions with seed data)
    private static final long TEST_ROLE_ID = 99001L;
    private static final long TEST_USER_ROLE_ID = 99002L;
    private static final long TEST_TEMPLATE_ID = 99003L;
    private static final long TEST_NODE_ID_ROLE = 99004L;
    private static final long TEST_NODE_ID_EMPTY = 99005L;

    // Business IDs for test runs
    private static final long BIZ_ROLE_APPROVER = 98001L;
    private static final long BIZ_NO_APPROVER = 98002L;
    private static final long BIZ_WITHDRAW_RESUBMIT = 98003L;
    private static final long BIZ_REJECT_RESUBMIT = 98004L;

    @Autowired private WorkflowEngine workflowEngine;
    @Autowired private WfTemplateMapper templateMapper;
    @Autowired private WfTemplateNodeMapper templateNodeMapper;
    @Autowired private WfInstanceMapper instanceMapper;
    @Autowired private WfTaskMapper taskMapper;
    @Autowired private WfNodeInstanceMapper nodeInstanceMapper;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private SysRoleMapper sysRoleMapper;
    @Autowired private SysUserRoleMapper sysUserRoleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * V85 deleted the demo admin user; workflow templates reference userId=1 as approver,
     * and role-based resolution requires users to exist in tenant 0.
     * Re-seed users 1-5 in tenant 0 so that submit/approve/resolve flows work.
     * Uses raw SQL to bypass MyBatis tenant isolation and ensure cross-class robustness.
     */
    @BeforeAll
    void seedBaseUsers() {
        // 1. Restore any test-seed users that were moved to other tenants by prior tests
        jdbcTemplate.update("UPDATE sys_user SET tenant_id = 0 WHERE id BETWEEN 1 AND 5 AND remark = 'test-seed'");
        // 2. Ensure all 5 test users exist in tenant 0
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 2, 0, 'manager', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '项目经理', '13800000001', 'manager@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 2)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 3, 0, 'gm', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '总经理', '13800000002', 'gm@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 3)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 4, 0, 'biz', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '商务人员', '13800000003', 'biz@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 4)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 5, 0, 'cost', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '成本人员', '13800000004', 'cost@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 5)");
    }

    @BeforeEach
    void setupContext() {
        TestUserContext.setAdmin(TENANT_0, USER_SUBMITTER);
        seedTestData();
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    @AfterAll
    void cleanupTestData() {
        // Cleanup handled by @Transactional on each test (auto-rollback).
        // Base users (1-5) are kept for other test classes.
    }

    // ── seed data for role-based tests ──

    private void seedTestData() {
        // Ensure approver user exists with the realName expected by the role-based resolver test.
        // User 2 was seeded by @BeforeAll seedBaseUsers() with real_name='项目经理', but this test
        // needs it to be '审批人' for the role-based resolution verification.
        // Use raw SQL to bypass MyBatis tenant isolation.
        jdbcTemplate.update("UPDATE sys_user SET real_name = '审批人', status = 'ENABLE' WHERE id = ? AND tenant_id = ?",
                USER_APPROVER, TENANT_0);
        if (sysUserMapper.selectById(USER_APPROVER) == null) {
            SysUser approver = new SysUser();
            approver.setId(USER_APPROVER);
            approver.setTenantId(TENANT_0);
            approver.setUsername("approver");
            approver.setPassword("$2a$10$dummy");
            approver.setRealName("审批人");
            approver.setStatus("ENABLE");
            approver.setIsAdmin(0);
            sysUserMapper.insert(approver);
        }

        // Ensure role exists
        if (sysRoleMapper.selectById(TEST_ROLE_ID) == null) {
            SysRole role = new SysRole();
            role.setId(TEST_ROLE_ID);
            role.setTenantId(TENANT_0);
            role.setRoleCode("TEST_APPROVER");
            role.setRoleName("测试审批角色");
            role.setRoleType("CUSTOM");
            role.setStatus("ENABLE");
            role.setDataScope("SELF");
            sysRoleMapper.insert(role);
        }

        // Ensure user-role assignment exists  
        if (sysUserRoleMapper.selectById(TEST_USER_ROLE_ID) == null) {
            SysUserRole userRole = new SysUserRole();
            userRole.setId(TEST_USER_ROLE_ID);
            userRole.setUserId(USER_APPROVER);
            userRole.setRoleId(TEST_ROLE_ID);
            sysUserRoleMapper.insert(userRole);
        }

        // Ensure role-based template + node exists (for ROLE test)
        if (templateMapper.selectById(TEST_TEMPLATE_ID) == null) {
            WfTemplate template = new WfTemplate();
            template.setId(TEST_TEMPLATE_ID);
            template.setTenantId(TENANT_0);
            template.setTemplateCode("TPL-TEST-ROLE-001");
            template.setTemplateName("角色审批测试模板");
            template.setBusinessType("TEST_ROLE_APPROVAL");
            template.setEnabled(1);
            templateMapper.insert(template);
        }

        if (templateNodeMapper.selectById(TEST_NODE_ID_ROLE) == null) {
            WfTemplateNode node = new WfTemplateNode();
            node.setId(TEST_NODE_ID_ROLE);
            node.setTenantId(TENANT_0);
            node.setTemplateId(TEST_TEMPLATE_ID);
            node.setNodeCode("N1");
            node.setNodeName("角色审批节点");
            node.setNodeOrder(1);
            node.setNodeType("APPROVAL");
            node.setApproveMode("SEQUENTIAL");
            node.setApproverConfig("{\"type\":\"ROLE\",\"roleId\":" + TEST_ROLE_ID + "}");
            node.setAllowTransfer(1);
            node.setAllowAddSign(1);
            templateNodeMapper.insert(node);
        }

        // Ensure empty-config template + node exists (for NO_APPROVER test)
        if (templateNodeMapper.selectById(TEST_NODE_ID_EMPTY) == null) {
            // Reuse same template but different node with empty config
            // Actually, create a separate template for clarity
            WfTemplate emptyTemplate = new WfTemplate();
            emptyTemplate.setId(TEST_TEMPLATE_ID + 1);
            emptyTemplate.setTenantId(TENANT_0);
            emptyTemplate.setTemplateCode("TPL-TEST-EMPTY-001");
            emptyTemplate.setTemplateName("空审批配置测试模板");
            emptyTemplate.setBusinessType("TEST_EMPTY_APPROVAL");
            emptyTemplate.setEnabled(1);
            // INSERT IGNORE style: check before insert
            if (templateMapper.selectById(TEST_TEMPLATE_ID + 1) == null) {
                templateMapper.insert(emptyTemplate);
            }

            if (templateNodeMapper.selectById(TEST_NODE_ID_EMPTY) == null) {
                WfTemplateNode emptyNode = new WfTemplateNode();
                emptyNode.setId(TEST_NODE_ID_EMPTY);
                emptyNode.setTenantId(TENANT_0);
                emptyNode.setTemplateId(TEST_TEMPLATE_ID + 1);
                emptyNode.setNodeCode("N1");
                emptyNode.setNodeName("空配置节点");
                emptyNode.setNodeOrder(1);
                emptyNode.setNodeType("APPROVAL");
                emptyNode.setApproveMode("SEQUENTIAL");
                emptyNode.setApproverConfig("{}");
                emptyNode.setAllowTransfer(1);
                emptyNode.setAllowAddSign(1);
                templateNodeMapper.insert(emptyNode);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 1: ROLE-configured approver receives task, not submitter
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("T1: ROLE-configured approver receives task, not submitter")
    void testRoleConfiguredApproverReceivesTask() {
        WfInstance instance = workflowEngine.submit(
                USER_SUBMITTER, "admin", TENANT_0,
                "TEST_ROLE_APPROVAL", BIZ_ROLE_APPROVER,
                "角色审批测试", new BigDecimal("100000.00"),
                100L, 100L, "{}", "{}", null);

        assertNotNull(instance, "应创建审批实例");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, instance.getInstanceStatus());

        // Verify task is assigned to the role member (USER_APPROVER), not the submitter
        List<WfTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertFalse(tasks.isEmpty(), "应创建审批任务");
        assertEquals(1, tasks.size(), "SEQUENTIAL模式应只有1个任务");

        WfTask task = tasks.get(0);
        assertEquals(USER_APPROVER, task.getApproverId(),
                "审批人应为角色成员(USER_APPROVER=" + USER_APPROVER + ")，而非提交者(USER_SUBMITTER=" + USER_SUBMITTER + ")");
        assertEquals("审批人", task.getApproverName(), "审批人姓名应为真实姓名");

        System.out.println("✅ T1 通过: 审批人ID=" + task.getApproverId()
                + " (提交者ID=" + USER_SUBMITTER + ")");
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 2: Empty/no approverConfig → NO_APPROVER error
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("T2: empty approverConfig throws NO_APPROVER")
    void testEmptyApproverConfigThrowsNoApprover() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.submit(
                        USER_SUBMITTER, "admin", TENANT_0,
                        "TEST_EMPTY_APPROVAL", BIZ_NO_APPROVER,
                        "空配置测试", new BigDecimal("100000.00"),
                        100L, 100L, "{}", "{}", null),
                "空approverConfig应抛出BusinessException");

        assertEquals("NO_APPROVER", ex.getCode(), "错误码应为NO_APPROVER");
        assertTrue(ex.getMessage().contains("审批节点未配置审批人")
                || ex.getMessage().contains("未找到可用的审批人"),
                "错误消息应说明审批人配置问题");

        System.out.println("✅ T2 通过: 错误码=" + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 3: Withdraw → resubmit creates new round, fresh nodes
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("T3: withdraw → resubmit creates new round with fresh node instances")
    void testWithdrawAndResubmitCreatesNewRound() {
        // Submit using existing CONTRACT_APPROVAL template (USER type, userId=1)
        WfInstance instance = workflowEngine.submit(
                USER_SUBMITTER, "admin", TENANT_0,
                "CONTRACT_APPROVAL", BIZ_WITHDRAW_RESUBMIT,
                "撤回重提测试", new BigDecimal("200000.00"),
                100L, 100L, "{}", "{}", null);
        assertNotNull(instance);
        assertEquals(1, instance.getCurrentRound());

        // Record node instance count before withdraw
        long nodesBefore = nodeInstanceMapper.selectCount(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instance.getId()));

        // Withdraw
        workflowEngine.withdraw(instance.getId(), USER_SUBMITTER, "admin");
        instance = instanceMapper.selectById(instance.getId());
        assertEquals(WorkflowConstants.INSTANCE_WITHDRAWN, instance.getInstanceStatus());

        // Resubmit
        workflowEngine.resubmit(instance.getId(), USER_SUBMITTER, "admin");
        instance = instanceMapper.selectById(instance.getId());
        assertEquals(2, instance.getCurrentRound(), "重提后轮次应为2");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, instance.getInstanceStatus());

        // Verify new node instances were created for round 2
        List<WfNodeInstance> round2Nodes = nodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instance.getId())
                        .eq(WfNodeInstance::getRoundNo, 2));
        assertFalse(round2Nodes.isEmpty(), "新轮次应创建节点实例");
        assertEquals(nodesBefore, round2Nodes.size(),
                "新轮次节点数应与原始节点数一致");

        // Verify exactly one active node
        long activeCount = round2Nodes.stream()
                .filter(n -> WorkflowConstants.NODE_ACTIVE.equals(n.getNodeStatus())).count();
        assertEquals(1, activeCount, "新轮次应有且仅有1个ACTIVE节点");

        // Verify pending tasks exist for the new round
        List<WfTask> pendingTasks = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getRoundNo, 2)
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertFalse(pendingTasks.isEmpty(), "新轮次应创建待审批任务");

        // Verify no pending tasks in old round
        long oldRoundPending = taskMapper.selectCount(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getRoundNo, 1)
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertEquals(0, oldRoundPending, "旧轮次不应有PENDING任务");

        System.out.println("✅ T3 通过: 新轮次=" + instance.getCurrentRound()
                + ", 新节点数=" + round2Nodes.size()
                + ", ACTIVE节点=" + activeCount
                + ", 待审批任务=" + pendingTasks.size());
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 4: Reject → resubmit creates new round, old tasks canceled
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("T4: reject → resubmit creates new round, old tasks canceled")
    void testRejectAndResubmitCreatesNewRound() {
        // Submit
        WfInstance instance = workflowEngine.submit(
                USER_SUBMITTER, "admin", TENANT_0,
                "CONTRACT_APPROVAL", BIZ_REJECT_RESUBMIT,
                "驳回重提测试", new BigDecimal("300000.00"),
                100L, 100L, "{}", "{}", null);
        assertNotNull(instance);
        assertEquals(1, instance.getCurrentRound());

        // Get the pending task and reject it
        WfTask task = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);
        workflowEngine.reject(task.getId(), USER_SUBMITTER, "admin",
                "测试驳回", "t4-reject-" + System.currentTimeMillis());

        instance = instanceMapper.selectById(instance.getId());
        assertEquals(WorkflowConstants.INSTANCE_REJECTED, instance.getInstanceStatus());

        // Verify old round tasks are not PENDING
        long oldPending = taskMapper.selectCount(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getRoundNo, 1)
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertEquals(0, oldPending, "驳回后旧轮次不应有PENDING任务");

        // Resubmit
        workflowEngine.resubmit(instance.getId(), USER_SUBMITTER, "admin");
        instance = instanceMapper.selectById(instance.getId());
        assertEquals(2, instance.getCurrentRound(), "重提后轮次应为2");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, instance.getInstanceStatus());

        // Verify new round has node instances
        List<WfNodeInstance> round2Nodes = nodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instance.getId())
                        .eq(WfNodeInstance::getRoundNo, 2));
        assertFalse(round2Nodes.isEmpty(), "新轮次应有节点实例");

        // Verify exactly one active node
        long activeCount = round2Nodes.stream()
                .filter(n -> WorkflowConstants.NODE_ACTIVE.equals(n.getNodeStatus())).count();
        assertEquals(1, activeCount, "新轮次应有且仅有1个ACTIVE节点");

        // Verify pending tasks for new round
        long newPending = taskMapper.selectCount(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getRoundNo, 2)
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertTrue(newPending >= 1, "新轮次至少应有1个待审批任务");

        System.out.println("✅ T4 通过: 新轮次=" + instance.getCurrentRound()
                + ", 新节点数=" + round2Nodes.size()
                + ", ACTIVE节点=" + activeCount
                + ", 新待审批=" + newPending);
    }
}
