package com.cgcpms.workflow.service;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfNodeInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfNodeInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link WorkflowTaskService} (transfer + addSign).
 * Uses real H2 database with Spring transactions rolled back after each test.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("WorkflowTaskService 转办与加签测试")
class WorkflowTaskServiceTest {

    private static final long TENANT_0 = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long USER_OTHER = 88888001L;
    private static final long TASK_ID = 880000000000001L;
    private static final long INSTANCE_ID = 880000000000002L;
    private static final long NODE_INSTANCE_ID = 880000000000003L;

    @Autowired
    private WorkflowTaskService workflowTaskService;

    @Autowired
    private WfTaskMapper wfTaskMapper;

    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    @Autowired
    private WfNodeInstanceMapper wfNodeInstanceMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanupTestData();
        seedAdminUser();
        seedOtherUser();
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
        TestUserContext.clear();
    }

    // ── transfer() tests ──

    @Test
    @Transactional
    @DisplayName("transfer: 正常转办给同租户用户")
    void transferHappyPath() {
        seedTransferFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING);

        assertDoesNotThrow(() ->
                workflowTaskService.transfer(TASK_ID, USER_OTHER, USER_ADMIN, "admin", "转办备注"));

        // 原任务变为 TRANSFERRED
        WfTask original = wfTaskMapper.selectById(TASK_ID);
        assertEquals(WorkflowConstants.TASK_TRANSFERRED, original.getTaskStatus());
        assertEquals(WorkflowConstants.ACTION_TRANSFER, original.getActionType());

        // 新任务已创建
        List<WfTask> newTasks = wfTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, INSTANCE_ID)
                        .eq(WfTask::getApproverId, USER_OTHER)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        assertEquals(1, newTasks.size());
    }

    @Test
    @Transactional
    @DisplayName("transfer: 任务不存在抛出TASK_NOT_FOUND")
    void transferTaskNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.transfer(99999999L, USER_OTHER, USER_ADMIN, "admin", "备注"));
        assertEquals("TASK_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("transfer: 任务已处理抛出TASK_ALREADY_HANDLED")
    void transferTaskAlreadyHandled() {
        seedTransferFixture(WorkflowConstants.TASK_APPROVED, WorkflowConstants.INSTANCE_RUNNING);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.transfer(TASK_ID, USER_OTHER, USER_ADMIN, "admin", "备注"));
        assertEquals("TASK_ALREADY_HANDLED", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("transfer: 非当前任务审批人抛出NOT_TASK_OWNER")
    void transferNotTaskOwner() {
        seedTransferFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.transfer(TASK_ID, USER_OTHER, USER_OTHER, "other", "备注"));
        assertEquals("NOT_TASK_OWNER", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("transfer: 当前租户与任务租户不一致时拒绝")
    void transferCurrentTenantMismatch() {
        seedTransferFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING);
        TestUserContext.setAdmin(999L, USER_ADMIN);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.transfer(TASK_ID, USER_OTHER, USER_ADMIN, "admin", "备注"));
        assertEquals("RESOURCE_NOT_FOUND", ex.getCode());

        WfTask task = wfTaskMapper.selectByIdIgnoringTenant(TASK_ID);
        assertEquals(WorkflowConstants.TASK_PENDING, task.getTaskStatus(), "跨租户拒绝不应变更任务状态");
    }

    @Test
    @Transactional
    @DisplayName("transfer: 目标用户不在当前租户抛出WORKFLOW_TARGET_USER_INVALID")
    void transferTargetUserWrongTenant() {
        seedTransferFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING);

        // USER_OTHER is in tenant 0. Create a user in tenant 999 for cross-tenant test
        SysUser crossTenantUser = new SysUser();
        crossTenantUser.setId(88888002L);
        crossTenantUser.setTenantId(999L);
        crossTenantUser.setUsername("othertenant");
        crossTenantUser.setPassword("pw");
        crossTenantUser.setRealName("跨租户用户");
        crossTenantUser.setStatus("ENABLE");
        crossTenantUser.setIsAdmin(0);
        sysUserMapper.insert(crossTenantUser);

        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> workflowTaskService.transfer(TASK_ID, 88888002L, USER_ADMIN, "admin", "备注"));
            assertEquals("WORKFLOW_TARGET_USER_INVALID", ex.getCode());
        } finally {
            sysUserMapper.deleteById(88888002L);
        }
    }

    @Test
    @Transactional
    @DisplayName("transfer: 目标用户不存在抛出WORKFLOW_TARGET_USER_INVALID")
    void transferTargetUserNotFound() {
        seedTransferFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.transfer(TASK_ID, 99999999L, USER_ADMIN, "admin", "备注"));
        assertEquals("WORKFLOW_TARGET_USER_INVALID", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("transfer: 实例状态已变更导致ping失败抛出INSTANCE_STATUS_CONFLICT")
    void transferInstanceStatusConflict() {
        // Seed with INSTANCE_REJECTED so pingInstanceRunning returns 0
        seedTransferFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_REJECTED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.transfer(TASK_ID, USER_OTHER, USER_ADMIN, "admin", "备注"));
        assertEquals("INSTANCE_STATUS_CONFLICT", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("transfer: CAS版本冲突抛出TASK_VERSION_CONFLICT")
    void transferCasVersionConflict() {
        seedTransferFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING);

        // Verify the task exists with expected version
        WfTask beforeTask = wfTaskMapper.selectById(TASK_ID);
        assertNotNull(beforeTask);
        assertEquals(0, beforeTask.getTaskVersion());

        // Bump task_version via raw JDBC
        int rows = jdbcTemplate.update("UPDATE wf_task SET task_version = 99 WHERE id = ?", TASK_ID);
        assertEquals(1, rows);

        // Verify version changed in DB
        Integer dbVersion = jdbcTemplate.queryForObject(
                "SELECT task_version FROM wf_task WHERE id = ?", Integer.class, TASK_ID);
        assertEquals(99, dbVersion);

        // Transfer should fail because expectedVersion=0 but DB has 99
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.transfer(TASK_ID, USER_OTHER, USER_ADMIN, "admin", "备注"));
        assertEquals("TASK_VERSION_CONFLICT", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("transfer: 实例不存在抛出INSTANCE_NOT_FOUND")
    void transferInstanceNotFound() {
        // Seed task without instance
        WfTask task = new WfTask();
        task.setId(TASK_ID);
        task.setTenantId(TENANT_0);
        task.setInstanceId(99999999L);
        task.setNodeInstanceId(NODE_INSTANCE_ID);
        task.setBusinessType("TEST_TRANSFER");
        task.setBusinessId(88000001L);
        task.setApproverId(USER_ADMIN);
        task.setApproverName("admin");
        task.setTaskStatus(WorkflowConstants.TASK_PENDING);
        task.setRoundNo(1);
        task.setTaskVersion(0);
        wfTaskMapper.insert(task);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.transfer(TASK_ID, USER_OTHER, USER_ADMIN, "admin", "备注"));
        assertEquals("INSTANCE_NOT_FOUND", ex.getCode());
    }

    // ── addSign() tests ──

    @Test
    @Transactional
    @DisplayName("addSign: 正常加签创建新任务")
    void addSignHappyPath() {
        seedAddSignFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.NODE_ACTIVE);

        assertDoesNotThrow(() ->
                workflowTaskService.addSign(TASK_ID, List.of(USER_OTHER), USER_ADMIN, "admin", "加签备注"));

        // 新加签任务已创建
        List<WfTask> addSignTasks = wfTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, INSTANCE_ID)
                        .eq(WfTask::getApproverId, USER_OTHER)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        assertEquals(1, addSignTasks.size());
    }

    @Test
    @Transactional
    @DisplayName("addSign: 多用户加签每人得到一个任务")
    void addSignMultipleUsers() {
        seedAddSignFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.NODE_ACTIVE);

        // Create second other user
        SysUser thirdUser = new SysUser();
        thirdUser.setId(88888003L);
        thirdUser.setTenantId(TENANT_0);
        thirdUser.setUsername("other3");
        thirdUser.setPassword("pw");
        thirdUser.setRealName("其他用户3");
        thirdUser.setStatus("ENABLE");
        thirdUser.setIsAdmin(0);
        sysUserMapper.insert(thirdUser);

        try {
            workflowTaskService.addSign(TASK_ID, List.of(USER_OTHER, 88888003L),
                    USER_ADMIN, "admin", "多人加签");

            List<WfTask> addSignTasks = wfTaskMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getInstanceId, INSTANCE_ID)
                            .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
            // Original task (PENDING) + 2 new add-sign tasks
            assertEquals(3, addSignTasks.size());
        } finally {
            jdbcTemplate.update("DELETE FROM wf_task WHERE approver_id = ? AND instance_id = ?",
                    88888003L, INSTANCE_ID);
            sysUserMapper.deleteById(88888003L);
        }
    }

    @Test
    @Transactional
    @DisplayName("addSign: 跳过已有待处理任务的用户不重复加签")
    void addSignSkipExistingPendingUser() {
        seedAddSignFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.NODE_ACTIVE);

        // Pre-create a pending task for USER_OTHER on same node
        WfTask existingTask = new WfTask();
        existingTask.setTenantId(TENANT_0);
        existingTask.setInstanceId(INSTANCE_ID);
        existingTask.setNodeInstanceId(NODE_INSTANCE_ID);
        existingTask.setBusinessType("TEST_ADD_SIGN");
        existingTask.setBusinessId(88000001L);
        existingTask.setApproverId(USER_OTHER);
        existingTask.setApproverName("其他用户");
        existingTask.setTaskStatus(WorkflowConstants.TASK_PENDING);
        existingTask.setRoundNo(1);
        existingTask.setTaskVersion(0);
        wfTaskMapper.insert(existingTask);

        workflowTaskService.addSign(TASK_ID, List.of(USER_OTHER), USER_ADMIN, "admin", "重复加签跳过");

        // Only 2 PENDING tasks: original + pre-existing (no duplicate)
        long count = wfTaskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, INSTANCE_ID)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        assertEquals(2, count);
    }

    @Test
    @Transactional
    @DisplayName("addSign: 任务不存在抛出TASK_NOT_FOUND")
    void addSignTaskNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.addSign(99999999L, List.of(USER_OTHER),
                        USER_ADMIN, "admin", "备注"));
        assertEquals("TASK_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("addSign: 任务已处理抛出TASK_ALREADY_HANDLED")
    void addSignTaskAlreadyHandled() {
        seedAddSignFixture(WorkflowConstants.TASK_APPROVED, WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.NODE_ACTIVE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.addSign(TASK_ID, List.of(USER_OTHER),
                        USER_ADMIN, "admin", "备注"));
        assertEquals("TASK_ALREADY_HANDLED", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("addSign: 非当前任务审批人抛出NOT_TASK_OWNER")
    void addSignNotTaskOwner() {
        seedAddSignFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.NODE_ACTIVE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.addSign(TASK_ID, List.of(USER_OTHER),
                        USER_OTHER, "other", "备注"));
        assertEquals("NOT_TASK_OWNER", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("addSign: 当前租户与任务租户不一致时拒绝")
    void addSignCurrentTenantMismatch() {
        seedAddSignFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.NODE_ACTIVE);
        TestUserContext.setAdmin(999L, USER_ADMIN);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.addSign(TASK_ID, List.of(USER_OTHER),
                        USER_ADMIN, "admin", "备注"));
        assertEquals("RESOURCE_NOT_FOUND", ex.getCode());

        TestUserContext.setAdmin(TENANT_0, USER_ADMIN);
        long pendingCount = wfTaskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, INSTANCE_ID)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        assertEquals(1, pendingCount, "跨租户拒绝不应新增加签任务");
    }

    @Test
    @Transactional
    @DisplayName("addSign: 实例不存在抛出INSTANCE_NOT_FOUND")
    void addSignInstanceNotFound() {
        WfTask task = new WfTask();
        task.setId(TASK_ID);
        task.setTenantId(TENANT_0);
        task.setInstanceId(99999999L);
        task.setNodeInstanceId(NODE_INSTANCE_ID);
        task.setBusinessType("TEST_ADD_SIGN");
        task.setBusinessId(88000001L);
        task.setApproverId(USER_ADMIN);
        task.setApproverName("admin");
        task.setTaskStatus(WorkflowConstants.TASK_PENDING);
        task.setRoundNo(1);
        task.setTaskVersion(0);
        wfTaskMapper.insert(task);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.addSign(TASK_ID, List.of(USER_OTHER),
                        USER_ADMIN, "admin", "备注"));
        assertEquals("INSTANCE_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("addSign: 实例不在运行中抛出INSTANCE_NOT_RUNNING")
    void addSignInstanceNotRunning() {
        seedAddSignFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_APPROVED,
                WorkflowConstants.NODE_ACTIVE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.addSign(TASK_ID, List.of(USER_OTHER),
                        USER_ADMIN, "admin", "备注"));
        assertEquals("INSTANCE_NOT_RUNNING", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("addSign: 节点不活跃抛出NODE_NOT_ACTIVE")
    void addSignNodeNotActive() {
        seedAddSignFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.NODE_COMPLETED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTaskService.addSign(TASK_ID, List.of(USER_OTHER),
                        USER_ADMIN, "admin", "备注"));
        assertEquals("NODE_NOT_ACTIVE", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("addSign: 加签用户不属于当前租户抛出WORKFLOW_TARGET_USER_INVALID")
    void addSignTargetUserWrongTenant() {
        seedAddSignFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.NODE_ACTIVE);

        SysUser crossTenantUser2 = new SysUser();
        crossTenantUser2.setId(88888002L);
        crossTenantUser2.setTenantId(999L);
        crossTenantUser2.setUsername("othertenant2");
        crossTenantUser2.setPassword("pw");
        crossTenantUser2.setRealName("跨租户用户2");
        crossTenantUser2.setStatus("ENABLE");
        crossTenantUser2.setIsAdmin(0);
        sysUserMapper.insert(crossTenantUser2);

        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> workflowTaskService.addSign(TASK_ID, List.of(88888002L),
                            USER_ADMIN, "admin", "备注"));
            assertEquals("WORKFLOW_TARGET_USER_INVALID", ex.getCode());
        } finally {
            sysUserMapper.deleteById(88888002L);
        }
    }

    @Test
    @Transactional
    @DisplayName("addSign: 空加签用户列表不抛出异常")
    void addSignEmptyUserList() {
        seedAddSignFixture(WorkflowConstants.TASK_PENDING, WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.NODE_ACTIVE);

        assertDoesNotThrow(() ->
                workflowTaskService.addSign(TASK_ID, List.of(), USER_ADMIN, "admin", "空加签"));
    }

    // ── Seed helpers ──

    private void seedAdminUser() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, USER_ADMIN);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    USER_ADMIN, TENANT_0, "admin",
                    "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
                    "系统管理员", "13800000000", "admin@cgc-pms.com",
                    "ENABLE", 1, USER_ADMIN, "测试种子数据");
        }
    }

    private void seedOtherUser() {
        SysUser exists = sysUserMapper.selectById(USER_OTHER);
        if (exists != null) return;
        SysUser user = new SysUser();
        user.setId(USER_OTHER);
        user.setTenantId(TENANT_0);
        user.setUsername("other");
        user.setPassword("pw");
        user.setRealName("其他用户");
        user.setStatus("ENABLE");
        user.setIsAdmin(0);
        sysUserMapper.insert(user);
    }

    /**
     * Seed task + instance (no node) for transfer tests.
     */
    private void seedTransferFixture(String taskStatus, String instanceStatus) {
        WfInstance instance = new WfInstance();
        instance.setId(INSTANCE_ID);
        instance.setTenantId(TENANT_0);
        instance.setTemplateId(1L);
        instance.setTitle("转办测试审批");
        instance.setBusinessType("TEST_TRANSFER");
        instance.setBusinessId(88000001L);
        instance.setInstanceStatus(instanceStatus);
        instance.setCurrentRound(1);
        instance.setAmount(BigDecimal.ZERO);
        instance.setInitiatorId(USER_ADMIN);
        wfInstanceMapper.insert(instance);

        WfTask task = new WfTask();
        task.setId(TASK_ID);
        task.setTenantId(TENANT_0);
        task.setInstanceId(INSTANCE_ID);
        task.setNodeInstanceId(NODE_INSTANCE_ID);
        task.setBusinessType("TEST_TRANSFER");
        task.setBusinessId(88000001L);
        task.setApproverId(USER_ADMIN);
        task.setApproverName("admin");
        task.setTaskStatus(taskStatus);
        task.setRoundNo(1);
        task.setTaskVersion(0);
        wfTaskMapper.insert(task);
    }

    /**
     * Seed task + instance + node for addSign tests.
     */
    private void seedAddSignFixture(String taskStatus, String instanceStatus, String nodeStatus) {
        WfInstance instance = new WfInstance();
        instance.setId(INSTANCE_ID);
        instance.setTenantId(TENANT_0);
        instance.setTemplateId(1L);
        instance.setTitle("加签测试审批");
        instance.setBusinessType("TEST_ADD_SIGN");
        instance.setBusinessId(88000001L);
        instance.setInstanceStatus(instanceStatus);
        instance.setCurrentRound(1);
        instance.setAmount(BigDecimal.ZERO);
        instance.setInitiatorId(USER_ADMIN);
        wfInstanceMapper.insert(instance);

        WfNodeInstance node = new WfNodeInstance();
        node.setId(NODE_INSTANCE_ID);
        node.setTenantId(TENANT_0);
        node.setInstanceId(INSTANCE_ID);
        node.setNodeCode("N1");
        node.setNodeName("加签节点");
        node.setNodeOrder(1);
        node.setApproveMode(WorkflowConstants.MODE_SEQUENTIAL);
        node.setNodeStatus(nodeStatus);
        node.setRoundNo(1);
        wfNodeInstanceMapper.insert(node);

        WfTask task = new WfTask();
        task.setId(TASK_ID);
        task.setTenantId(TENANT_0);
        task.setInstanceId(INSTANCE_ID);
        task.setNodeInstanceId(NODE_INSTANCE_ID);
        task.setBusinessType("TEST_ADD_SIGN");
        task.setBusinessId(88000001L);
        task.setApproverId(USER_ADMIN);
        task.setApproverName("admin");
        task.setTaskStatus(taskStatus);
        task.setRoundNo(1);
        task.setTaskVersion(0);
        wfTaskMapper.insert(task);
    }

    private void cleanupTestData() {
        // Clean up in reverse FK order
        jdbcTemplate.update("DELETE FROM wf_record WHERE business_id = ?", 88000001L);
        jdbcTemplate.update("DELETE FROM wf_task WHERE business_id = ? AND business_type IN ('TEST_TRANSFER', 'TEST_ADD_SIGN')",
                88000001L);
        jdbcTemplate.update("DELETE FROM wf_task WHERE id = ?", TASK_ID);
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE id = ?", NODE_INSTANCE_ID);
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE instance_id = ?", INSTANCE_ID);
        jdbcTemplate.update("DELETE FROM wf_instance WHERE id = ?", INSTANCE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_OTHER);
    }
}
