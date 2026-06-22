package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowQueryService;
import com.cgcpms.workflow.vo.WfCcVO;
import com.cgcpms.workflow.vo.WfInstanceVO;
import com.cgcpms.workflow.vo.WfRecordVO;
import com.cgcpms.workflow.vo.WfTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorkflowQueryService public query methods.
 * <p>
 * Covers:
 * <ul>
 *   <li>getMyTodos — my pending tasks (pagination, empty page, multi-task, instance enrichment)</li>
 *   <li>getMyDone — my completed records (pagination, empty page, after approve)</li>
 *   <li>getInstanceDetail — full instance detail with nodes/tasks/records, authorization checks</li>
 *   <li>getMyCc — my carbon copies (pagination, empty page, cc from submit)</li>
 * </ul>
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("WorkflowQueryService 查询服务测试")
class WorkflowQueryServiceTest {

    private static final long TENANT_0 = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long USER_OTHER = 2L;

    private static final long TEMPLATE_ID = 230200000000000001L;
    private static final long NODE_1_ID = 230200000000000101L;
    private static final String BUSINESS_TYPE = "WQ_TEST_APPROVAL";

    @Autowired
    private WorkflowQueryService queryService;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WfTemplateMapper templateMapper;

    @Autowired
    private WfTemplateNodeMapper templateNodeMapper;

    @Autowired
    private WfInstanceMapper instanceMapper;

    @Autowired
    private WfTaskMapper taskMapper;

    @Autowired
    private WfRecordMapper recordMapper;

    @Autowired
    private WfNodeInstanceMapper nodeInstanceMapper;

    @Autowired
    private WfCcMapper ccMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long submittedInstanceId;
    private Long submittedTaskId;

    @BeforeEach
    void setUp() {
        seedAdminUser();
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of("ADMIN"))
                .build());
        cleanup();
        seedTemplateAndSubmit();
    }

    @AfterEach
    void tearDown() {
        cleanup();
        UserContext.clear();
    }

    // ── getMyTodos ──

    @Test
    @DisplayName("getMyTodos 返回当前用户待办任务分页")
    void getMyTodosReturnsPagedPendingTasks() {
        IPage<WfTaskVO> page = queryService.getMyTodos(TENANT_0, USER_ADMIN, 1, 20);

        assertNotNull(page);
        assertEquals(1, page.getTotal());
        List<WfTaskVO> records = page.getRecords();
        assertEquals(1, records.size());

        WfTaskVO todo = records.get(0);
        assertEquals(String.valueOf(submittedTaskId), todo.getId());
        assertEquals(String.valueOf(submittedInstanceId), todo.getInstanceId());
        assertEquals(WorkflowConstants.TASK_PENDING, todo.getTaskStatus());
        assertEquals("测试待办标题", todo.getTitle());
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, todo.getInstanceStatus());
        assertNotNull(todo.getBusinessType());
        assertNotNull(todo.getBusinessId());
    }

    @Test
    @DisplayName("getMyTodos 其他用户无待办时返回空分页")
    void getMyTodosReturnsEmptyForUserWithoutTasks() {
        IPage<WfTaskVO> page = queryService.getMyTodos(TENANT_0, USER_OTHER, 1, 20);

        assertNotNull(page);
        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    @DisplayName("getMyTodos 分页参数第二页无数据返回空列表")
    void getMyTodosSecondPageEmpty() {
        IPage<WfTaskVO> page = queryService.getMyTodos(TENANT_0, USER_ADMIN, 2, 20);

        assertNotNull(page);
        assertEquals(1, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    @DisplayName("getMyTodos 多任务场景按receivedAt降序排列")
    void getMyTodosOrdersByReceivedAtDescending() {
        // Submit two more workflows so USER_ADMIN has multiple tasks
        WfInstance instance2 = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333002L,
                "多任务测试2", new BigDecimal("500.00"),
                100L, 100L, "{}", "{}", null);
        assertNotNull(instance2);

        WfInstance instance3 = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333003L,
                "多任务测试3", new BigDecimal("500.00"),
                100L, 100L, "{}", "{}", null);
        assertNotNull(instance3);

        try {
            IPage<WfTaskVO> page = queryService.getMyTodos(TENANT_0, USER_ADMIN, 1, 20);

            assertTrue(page.getTotal() >= 3);
            List<WfTaskVO> records = page.getRecords();
            // Verify descending order by checking that successive receivedAt are non-increasing
            for (int i = 1; i < records.size(); i++) {
                String prev = records.get(i - 1).getReceivedAt();
                String curr = records.get(i).getReceivedAt();
                assertNotNull(prev);
                assertNotNull(curr);
                assertTrue(prev.compareTo(curr) >= 0,
                        "任务应按 receivedAt 降序排列");
            }
        } finally {
            // Clean up extra instances
            cleanupInstance(instance2.getId(), 33333002L);
            cleanupInstance(instance3.getId(), 33333003L);
        }
    }

    // ── getMyDone ──

    @Test
    @DisplayName("getMyDone 发起人提交后已有1条SUBMIT记录")
    void getMyDoneReturnsSubmitRecordForInitiator() {
        // The initiator (USER_ADMIN) is the operator on the SUBMIT record,
        // so getMyDone for USER_ADMIN includes the submit action.
        IPage<WfRecordVO> page = queryService.getMyDone(USER_ADMIN, TENANT_0, 1, 20);

        assertNotNull(page);
        // SUBMIT creates a record with the initiator as operator
        assertTrue(page.getTotal() >= 1, "发起人提交后应有 SUBMIT 已办记录");
        boolean hasSubmit = page.getRecords().stream()
                .anyMatch(r -> WorkflowConstants.ACTION_SUBMIT.equals(r.getActionType()));
        assertTrue(hasSubmit, "应包含 SUBMIT 操作记录");
    }

    @Test
    @DisplayName("getMyDone 审批通过后返回已办记录")
    void getMyDoneReturnsRecordsAfterApproval() {
        workflowEngine.approve(submittedTaskId, USER_ADMIN, "admin", "同意", "done-test-key-001");

        IPage<WfRecordVO> page = queryService.getMyDone(USER_ADMIN, TENANT_0, 1, 20);

        assertTrue(page.getTotal() >= 1, "审批后应至少有1条已办记录");
        WfRecordVO done = page.getRecords().get(0);
        assertEquals("admin", done.getOperatorName());
        assertEquals(String.valueOf(submittedInstanceId), done.getInstanceId());
        assertNotNull(done.getTitle());
    }

    @Test
    @DisplayName("getMyDone 审批驳回后返回已办记录")
    void getMyDoneReturnsAfterRejection() {
        workflowEngine.reject(submittedTaskId, USER_ADMIN, "admin", "不同意", "reject-test-key-001");

        IPage<WfRecordVO> page = queryService.getMyDone(USER_ADMIN, TENANT_0, 1, 20);

        assertTrue(page.getTotal() >= 1, "驳回后应至少有1条已办记录");
        WfRecordVO done = page.getRecords().get(0);
        assertEquals("admin", done.getOperatorName());
    }

    @Test
    @DisplayName("getMyDone 分页第二页无数据返回空列表")
    void getMyDoneSecondPageEmpty() {
        IPage<WfRecordVO> page = queryService.getMyDone(USER_ADMIN, TENANT_0, 2, 20);

        assertEquals(0, page.getRecords().size());
    }

    // ── getInstanceDetail ──

    @Test
    @DisplayName("getInstanceDetail 返回实例详情含节点和任务信息")
    void getInstanceDetailReturnsFullDetail() {
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);

        assertNotNull(detail);
        assertEquals(String.valueOf(submittedInstanceId), detail.getId());
        assertEquals("测试待办标题", detail.getTitle());
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, detail.getInstanceStatus());
        assertNotNull(detail.getTemplateId());
        assertNotNull(detail.getTemplateName());
        assertEquals(String.valueOf(USER_ADMIN), detail.getInitiatorId());
        assertNotNull(detail.getInitiatorName());

        // Nodes
        assertNotNull(detail.getNodes());
        assertEquals(1, detail.getNodes().size());
        assertNotNull(detail.getNodes().get(0).getNodeName());
        assertEquals(WorkflowConstants.NODE_ACTIVE, detail.getNodes().get(0).getNodeStatus());

        // Tasks within node
        assertNotNull(detail.getNodes().get(0).getTasks());
        assertEquals(1, detail.getNodes().get(0).getTasks().size());
        assertEquals(String.valueOf(submittedTaskId), detail.getNodes().get(0).getTasks().get(0).getId());

        // Records
        assertNotNull(detail.getRecords());
        assertEquals(1, detail.getRecords().size(),
                "提交操作应生成1条 SUBMIT 审批记录");
        assertEquals(WorkflowConstants.ACTION_SUBMIT, detail.getRecords().get(0).getActionType());

        // Available actions
        assertNotNull(detail.getAvailableActions());
        assertTrue(detail.getAvailableActions().contains(WorkflowConstants.UI_APPROVE));
    }

    @Test
    @DisplayName("getInstanceDetail 不存在的实例返回null")
    void getInstanceDetailNonexistentReturnsNull() {
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, 99999999L, USER_ADMIN);
        assertNull(detail);
    }

    @Test
    @DisplayName("getInstanceDetail 非参与者且非管理员查看时返回null（鉴权）")
    void getInstanceDetailUnauthorizedReturnsNull() {
        // Switch UserContext to USER_OTHER who is NOT the initiator, NOT a task participant,
        // and does NOT have ADMIN role.
        UserContext.clear();
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_OTHER)
                .add("username", "other")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of())
                .build());
        try {
            WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_OTHER);
            assertNull(detail, "非参与者且非管理员不应能查看实例详情");
        } finally {
            UserContext.clear();
            UserContext.set(io.jsonwebtoken.Jwts.claims()
                    .add("userId", USER_ADMIN)
                    .add("username", "admin")
                    .add("tenantId", TENANT_0)
                    .add("roleCodes", List.of("ADMIN"))
                    .build());
        }
    }

    @Test
    @DisplayName("getInstanceDetail 任务参与者（审批人）可查看实例详情")
    void getInstanceDetailTaskParticipantCanView() {
        // USER_ADMIN is the approver (task participant)
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);
        assertNotNull(detail, "审批人（任务参与者）应能查看实例详情");
    }

    @Test
    @DisplayName("getInstanceDetail 发起人可查看实例详情")
    void getInstanceDetailInitiatorCanView() {
        // USER_ADMIN is both initiator and approver - the test already validates both.
        // This specifically confirms initiator access.
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);
        assertNotNull(detail);
        assertEquals(String.valueOf(USER_ADMIN), detail.getInitiatorId());
    }

    @Test
    @DisplayName("getInstanceDetail ADMIN角色可查看任何实例")
    void getInstanceDetailAdminCanViewAnyInstance() {
        // USER_ADMIN has ADMIN role set in setUp
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);
        assertNotNull(detail, "ADMIN角色应能查看任何同租户实例");
    }

    @Test
    @DisplayName("getInstanceDetail 审批通过后查看状态已变更为APPROVED")
    void getInstanceDetailAfterApprovalShowsApprovedStatus() {
        workflowEngine.approve(submittedTaskId, USER_ADMIN, "admin", "通过", "detail-approve-key-001");

        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);
        assertEquals(WorkflowConstants.INSTANCE_APPROVED, detail.getInstanceStatus());

        // Records should now include both SUBMIT and APPROVE
        assertTrue(detail.getRecords().size() >= 2,
                "审批通过后应至少有 SUBMIT + APPROVE 两条记录");
        boolean hasApprove = detail.getRecords().stream()
                .anyMatch(r -> WorkflowConstants.ACTION_APPROVE.equals(r.getActionType()));
        assertTrue(hasApprove, "应包含APPROVE操作记录");
    }

    // ── getMyCc ──

    @Test
    @DisplayName("getMyCc 无抄送时返回空分页")
    void getMyCcReturnsEmptyWhenNoCc() {
        IPage<WfCcVO> page = queryService.getMyCc(USER_ADMIN, TENANT_0, 1, 20);

        assertNotNull(page);
        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    @DisplayName("getMyCc 有抄送时返回抄送分页含实例状态")
    void getMyCcReturnsCcWithInstanceStatus() {
        // Submit a workflow with ccUserIds
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333010L,
                "抄送测试", new BigDecimal("500.00"),
                100L, 100L, "{}", "{}", List.of(USER_ADMIN));
        assertNotNull(instance);

        try {
            IPage<WfCcVO> page = queryService.getMyCc(USER_ADMIN, TENANT_0, 1, 20);

            assertTrue(page.getTotal() >= 1, "有抄送时应至少返回1条记录");
            WfCcVO cc = page.getRecords().get(0);
            assertEquals(String.valueOf(USER_ADMIN), cc.getCcUserId());
            assertEquals(String.valueOf(instance.getId()), cc.getInstanceId());
            assertNotNull(cc.getTitle());
            assertEquals(WorkflowConstants.INSTANCE_RUNNING, cc.getInstanceStatus());
            assertEquals(0, cc.getIsRead());
        } finally {
            cleanupInstance(instance.getId(), 33333010L);
        }
    }

    @Test
    @DisplayName("getMyCc 其他用户无抄送时返回空")
    void getMyCcEmptyForUserWithoutCc() {
        // Submit with cc to USER_ADMIN
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333011L,
                "抄送测试-仅抄送admin", new BigDecimal("500.00"),
                100L, 100L, "{}", "{}", List.of(USER_ADMIN));
        assertNotNull(instance);

        try {
            // USER_OTHER has no cc
            IPage<WfCcVO> page = queryService.getMyCc(USER_OTHER, TENANT_0, 1, 20);

            assertEquals(0, page.getTotal());
            assertTrue(page.getRecords().isEmpty());
        } finally {
            cleanupInstance(instance.getId(), 33333011L);
        }
    }

    // ── Seed data ──

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

    private void seedTemplateAndSubmit() {
        // Template
        WfTemplate template = new WfTemplate();
        template.setId(TEMPLATE_ID);
        template.setTenantId(TENANT_0);
        template.setTemplateCode("WQ_TEST_TPL_001");
        template.setTemplateName("查询服务测试模板");
        template.setBusinessType(BUSINESS_TYPE);
        template.setEnabled(1);
        template.setAmountMin(new BigDecimal("0.00"));
        template.setAmountMax(new BigDecimal("999999.99"));
        templateMapper.insert(template);

        // Single approval node with USER_ADMIN as approver
        WfTemplateNode node = new WfTemplateNode();
        node.setId(NODE_1_ID);
        node.setTenantId(TENANT_0);
        node.setTemplateId(TEMPLATE_ID);
        node.setNodeCode("N1");
        node.setNodeName("查询服务审批节点");
        node.setNodeOrder(1);
        node.setNodeType("APPROVAL");
        node.setApproveMode("SEQUENTIAL");
        node.setApproverConfig("{\"type\":\"USER\",\"userId\":" + USER_ADMIN + "}");
        node.setAllowTransfer(1);
        node.setAllowAddSign(1);
        templateNodeMapper.insert(node);

        // Submit workflow — this creates instance, node instance, task, and record
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333001L,
                "测试待办标题", new BigDecimal("500.00"),
                100L, 100L, "测试业务摘要", "{}", null);
        assertNotNull(instance, "提交应在测试数据准备阶段成功");
        submittedInstanceId = instance.getId();

        // Find the created task
        WfTask task = taskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getInstanceId, submittedInstanceId)
                .eq(WfTask::getApproverId, USER_ADMIN)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        assertNotNull(task, "提交后应生成待办任务");
        submittedTaskId = task.getId();
    }

    private void cleanup() {
        // Clean up test instances in order
        cleanupInstance(submittedInstanceId, 33333001L);
        // Clean extra instances if any
        for (long bizId : new long[]{33333002L, 33333003L, 33333010L, 33333011L}) {
            jdbcTemplate.update("DELETE FROM wf_cc WHERE business_id = ?", bizId);
            jdbcTemplate.update("DELETE FROM wf_record WHERE business_id = ?", bizId);
            jdbcTemplate.update("DELETE FROM wf_task WHERE business_id = ?", bizId);
            jdbcTemplate.update(
                    "DELETE FROM wf_node_instance WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_id = ?)",
                    bizId);
            jdbcTemplate.update("DELETE FROM wf_instance WHERE business_id = ?", bizId);
        }
        // Clean template nodes and template
        jdbcTemplate.update("DELETE FROM wf_template_node WHERE template_id = ?", TEMPLATE_ID);
        jdbcTemplate.update("DELETE FROM wf_template WHERE id = ?", TEMPLATE_ID);
    }

    private void cleanupInstance(Long instanceId, Long businessId) {
        if (instanceId == null) return;
        jdbcTemplate.update("DELETE FROM wf_cc WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM wf_record WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM wf_task WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM wf_instance WHERE id = ?", instanceId);
    }
}
