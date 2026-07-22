package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.workflow.dto.WorkflowTemplateUpdateRequest;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTemplate;
import com.cgcpms.workflow.entity.WfTemplateNode;
import com.cgcpms.workflow.mapper.WfTemplateMapper;
import com.cgcpms.workflow.mapper.WfTemplateNodeMapper;
import com.cgcpms.workflow.service.ApproverResolver;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowTemplateService;
import com.cgcpms.workflow.vo.WfTemplateVO;
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
 * Tests for WorkflowCoreService internal helpers exercised through public services.
 * <p>
 * Covers:
 * <ul>
 *   <li>Template CRUD via WorkflowTemplateService (tests findTemplate, findTemplateNodes internals)</li>
 *   <li>Template amount-range matching via WorkflowEngine submit</li>
 *   <li>Approver config resolution via ApproverResolver (tests USER, ROLE, POSITION, PROJECT_ROLE types)</li>
 *   <li>Error cases: template not found, no nodes, invalid config</li>
 *   <li>Template enabled/disabled behavior</li>
 * </ul>
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("WorkflowCoreService 内部核心逻辑测试")
class WorkflowCoreServiceTest {

    private static final long TENANT_0 = 0L;
    private static final long USER_ADMIN = 1L;

    // Template IDs
    private static final long TEMPLATE_1_ID = 230000000000000001L;
    private static final long TEMPLATE_2_ID = 230000000000000010L;
    private static final long NODE_1_ID = 230000000000000101L;
    private static final long NODE_2_ID = 230000000000000102L;

    private static final String BUSINESS_TYPE = WorkflowBusinessTypes.CONTRACT_APPROVAL;

    @Autowired
    private WorkflowTemplateService workflowTemplateService;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private ApproverResolver approverResolver;

    @Autowired
    private WfTemplateMapper templateMapper;

    @Autowired
    private WfTemplateNodeMapper templateNodeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        seedTemplates();
    }

    @AfterEach
    void tearDown() {
        cleanup();
        UserContext.clear();
    }

    // ── Template CRUD (exercising findTemplate / findTemplateNodes) ──

    @Test
    @DisplayName("分页查询审批模板并获取节点详情")
    void listTemplatesAndGetDetail() {
        PageResult<WfTemplateVO> page = workflowTemplateService.listTemplates(
                1, 20, BUSINESS_TYPE, null, "核心服务测试模板");

        assertEquals(1, page.getTotal(), "应该只查到一个已启用的模板");
        WfTemplateVO summary = page.getRecords().get(0);
        assertEquals("核心服务测试模板", summary.getTemplateName());
        assertEquals(BUSINESS_TYPE, summary.getBusinessType());
        assertEquals(2, summary.getNodeCount());

        WfTemplateVO detail = workflowTemplateService.getTemplateDetail(
                Long.valueOf(summary.getId()));
        assertNotNull(detail);
        assertEquals(2, detail.getNodes().size());
        assertEquals("部门主管审批", detail.getNodes().get(0).getNodeName());
        assertEquals("财务审批", detail.getNodes().get(1).getNodeName());
    }

    @Test
    @DisplayName("更新模板可编辑字段，业务类型不受影响")
    void updateTemplateChangesEditableFields() {
        WorkflowTemplateUpdateRequest request = new WorkflowTemplateUpdateRequest();
        request.setTemplateName("已更新模板名称");
        request.setEnabled(0);
        request.setAmountMin(new BigDecimal("500.00"));
        request.setAmountMax(new BigDecimal("5000.00"));
        request.setRemark("测试更新备注");

        workflowTemplateService.updateTemplate(TEMPLATE_1_ID, request);

        WfTemplate updated = templateMapper.selectById(TEMPLATE_1_ID);
        assertEquals("已更新模板名称", updated.getTemplateName());
        assertEquals(0, updated.getEnabled());
        assertEquals(new BigDecimal("500.00"), updated.getAmountMin());
        assertEquals(new BigDecimal("5000.00"), updated.getAmountMax());
        assertEquals(BUSINESS_TYPE, updated.getBusinessType(), "业务类型不可被编辑改写");
    }

    @Test
    @DisplayName("禁用模板后分页查询只查启用状态时不再返回")
    void disabledTemplateExcludedFromEnabledList() {
        WorkflowTemplateUpdateRequest disableRequest = new WorkflowTemplateUpdateRequest();
        disableRequest.setTemplateName("核心服务测试模板");
        disableRequest.setEnabled(0);
        workflowTemplateService.updateTemplate(TEMPLATE_1_ID, disableRequest);

        PageResult<WfTemplateVO> page = workflowTemplateService.listTemplates(
                1, 20, BUSINESS_TYPE, 1, "核心服务测试模板");
        assertEquals(0, page.getTotal(), "过滤enabled=1时禁用模板不应出现");
    }

    @Test
    @DisplayName("模板无节点的详情查询返回空节点列表")
    void templateWithoutNodesReturnsEmptyNodeList() {
        // TEMPLATE_2_ID has no nodes seeded -- getTemplateDetail returns empty node list
        WfTemplateVO detail = workflowTemplateService.getTemplateDetail(TEMPLATE_2_ID);
        assertNotNull(detail);
        assertEquals("无节点模板", detail.getTemplateName());
        assertEquals(0, detail.getNodeCount());
        assertTrue(detail.getNodes().isEmpty());
    }

    @Test
    @DisplayName("不存在的模板ID获取详情抛出异常")
    void getDetailWithNonexistentIdThrowsException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTemplateService.getTemplateDetail(99999999L));
        assertEquals("TEMPLATE_NOT_FOUND", ex.getCode());
    }

    // ── Amount range matching (exercises queryTemplate with amount filtering) ──

    @Test
    @DisplayName("金额在范围内可匹配到模板")
    void submitWithMatchingAmountFindsTemplate() {
        seedContract(88888001L);
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 88888001L,
                "金额匹配测试", new BigDecimal("3000.00"),
                null, null, "{}", "{}", null);
        assertNotNull(instance);
        assertNotNull(instance.getId());
        assertEquals("RUNNING", instance.getInstanceStatus());
    }

    @Test
    @DisplayName("金额超出范围时抛出模板未找到异常")
    void submitWithOutOfRangeAmountThrowsTemplateNotFound() {
        seedContract(88888002L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowEngine.submit(
                        USER_ADMIN, "admin", TENANT_0,
                        BUSINESS_TYPE, 88888002L,
                        "金额超范围测试", new BigDecimal("1000000000.00"),
                        null, null, "{}", "{}", null));
        assertEquals("TEMPLATE_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("金额正好等于边界值可匹配到模板")
    void submitWithBoundaryAmountMatchesTemplate() {
        seedContract(88888003L);
        WfInstance instanceMin = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 88888003L,
                "最小边界金额", new BigDecimal("0.00"),
                null, null, "{}", "{}", null);
        assertNotNull(instanceMin);
        assertEquals("RUNNING", instanceMin.getInstanceStatus());

        seedContract(88888004L);
        WfInstance instanceMax = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 88888004L,
                "最大边界金额", new BigDecimal("10000.00"),
                null, null, "{}", "{}", null);
        assertNotNull(instanceMax);
        assertEquals("RUNNING", instanceMax.getInstanceStatus());
    }

    @Test
    @DisplayName("审批路由规则在唯一模板入口按合同类型生效")
    void routingRuleOverridesFallbackAmountRange() {
        long businessId = 88888005L;
        seedContract(businessId);
        jdbcTemplate.update("""
                INSERT INTO approval_routing_rule(
                 id,tenant_id,rule_name,business_type,min_amount,max_amount,contract_type,
                 workflow_template_id,priority,enabled_flag,rule_signature,active_rule_token,version,created_at,updated_at)
                VALUES(230000000000000201,0,'合同类型路由测试',?,0,50000,'SUB',?,1,1,
                 'CONTRACT_APPROVAL|0.00|50000.00|SUB|*',0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, BUSINESS_TYPE, TEMPLATE_1_ID);

        WfInstance instance = workflowEngine.submit(USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, businessId, "路由覆盖金额范围", new BigDecimal("20000.00"),
                null, null, "{}", "{}", null);

        assertEquals(TEMPLATE_1_ID, instance.getTemplateId());
    }

    // ── Approver config resolution ──

    @Test
    @DisplayName("USER类型审批人解析成功")
    void resolveUserTypeApprover() {
        String config = "{\"type\":\"USER\",\"userId\":" + USER_ADMIN + "}";
        List<Long> userIds = approverResolver.resolve(config, TENANT_0, null);
        assertEquals(1, userIds.size());
        assertEquals(USER_ADMIN, userIds.get(0));
    }

    @Test
    @DisplayName("USER类型审批人不属于当前租户时抛出异常")
    void resolveUserTypeWrongTenantThrowsException() {
        String config = "{\"type\":\"USER\",\"userId\":" + USER_ADMIN + "}";
        // Tenant 999 doesn't match USER_ADMIN's tenant (0)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approverResolver.resolve(config, 999L, null));
        assertEquals("WORKFLOW_APPROVER_INVALID", ex.getCode());
    }

    @Test
    @DisplayName("空审批人配置抛出NO_APPROVER异常")
    void resolveEmptyConfigThrowsException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approverResolver.resolve("{}", TENANT_0, null));
        assertEquals("NO_APPROVER", ex.getCode());
    }

    @Test
    @DisplayName("null审批人配置抛出NO_APPROVER异常")
    void resolveNullConfigThrowsException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approverResolver.resolve(null, TENANT_0, null));
        assertEquals("NO_APPROVER", ex.getCode());
    }

    @Test
    @DisplayName("无效JSON格式抛出INVALID_APPROVER_CONFIG异常")
    void resolveInvalidJsonThrowsException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approverResolver.resolve("not-valid-json", TENANT_0, null));
        assertEquals("INVALID_APPROVER_CONFIG", ex.getCode());
    }

    @Test
    @DisplayName("缺少type字段抛出INVALID_APPROVER_CONFIG异常")
    void resolveMissingTypeThrowsException() {
        String config = "{\"userId\":" + USER_ADMIN + "}";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approverResolver.resolve(config, TENANT_0, null));
        assertEquals("INVALID_APPROVER_CONFIG", ex.getCode());
    }

    @Test
    @DisplayName("不支持的类型抛出UNSUPPORTED_APPROVER_TYPE异常")
    void resolveUnsupportedTypeThrowsException() {
        String config = "{\"type\":\"UNKNOWN\",\"id\":1}";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approverResolver.resolve(config, TENANT_0, null));
        assertEquals("UNSUPPORTED_APPROVER_TYPE", ex.getCode());
    }

    @Test
    @DisplayName("USER类型缺少userId抛出INVALID_APPROVER_CONFIG异常")
    void resolveUserTypeMissingUserIdThrowsException() {
        String config = "{\"type\":\"USER\"}";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approverResolver.resolve(config, TENANT_0, null));
        assertEquals("INVALID_APPROVER_CONFIG", ex.getCode());
    }

    @Test
    @DisplayName("PROJECT_ROLE类型缺少项目ID抛出NO_PROJECT异常")
    void resolveProjectRoleWithoutProjectThrowsException() {
        String config = "{\"type\":\"PROJECT_ROLE\",\"roleCode\":\"PM\"}";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approverResolver.resolve(config, TENANT_0, null));
        assertEquals("NO_PROJECT", ex.getCode());
    }

    @Test
    @DisplayName("POSITION类型无匹配用户时抛出异常")
    void resolvePositionTypeNoMatchThrowsException() {
        // POSITION type with non-existent positionId — no matching users
        String config = "{\"type\":\"POSITION\",\"positionId\":99999999}";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approverResolver.resolve(config, TENANT_0, null));
        assertEquals("NO_APPROVER", ex.getCode());
    }

    @Test
    @DisplayName("ROLE类型审批人解析返回匹配用户")
    void resolveRoleTypeApprover() {
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, status) VALUES (?, ?, ?, ?, ?, ?)",
                99901L, TENANT_0, "TEST_APPROVER", "测试审批角色", "CUSTOM", "ENABLE");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (id, user_id, role_id) VALUES (?, ?, ?)",
                99902L, USER_ADMIN, 99901L);

        try {
            String config = "{\"type\":\"ROLE\",\"roleId\":99901}";
            List<Long> userIds = approverResolver.resolve(config, TENANT_0, null);
            assertFalse(userIds.isEmpty(), "应该解析到至少一个用户");
            assertTrue(userIds.contains(USER_ADMIN), "应该包含管理员用户");
        } finally {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE id = ?", 99902L);
            jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", 99901L);
        }
    }

    // ── Template fallback to tenant 0 ──

    @Test
    @DisplayName("租户特定模板不存在时回退到租户0模板")
    void tenantSpecificNotFoundFallsBackToTenantZero() {
        LambdaQueryWrapper<WfTemplate> wrapperTenant0 = new LambdaQueryWrapper<WfTemplate>()
                .eq(WfTemplate::getBusinessType, BUSINESS_TYPE)
                .eq(WfTemplate::getTenantId, TENANT_0)
                .eq(WfTemplate::getEnabled, 1);
        assertTrue(templateMapper.selectCount(wrapperTenant0) > 0,
                "租户0的模板必须存在");

        LambdaQueryWrapper<WfTemplate> wrapperTenant999 = new LambdaQueryWrapper<WfTemplate>()
                .eq(WfTemplate::getBusinessType, BUSINESS_TYPE)
                .eq(WfTemplate::getTenantId, 999L)
                .eq(WfTemplate::getEnabled, 1);
        assertEquals(0, templateMapper.selectCount(wrapperTenant999),
                "租户999不应有该业务类型的模板");
    }

    // ── Seed data ──

    /**
     * Seed admin user because V85 migration removes the default admin.
     * This is required for approver resolution and workflow engine operations.
     */
    private void seedAdminUser() {
        // Only insert if admin user doesn't exist (idempotent for repeated test runs)
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

    private void seedTemplates() {
        // Template 1: enabled, has nodes (for CRUD and list)
        WfTemplate t1 = new WfTemplate();
        t1.setId(TEMPLATE_1_ID);
        t1.setTenantId(TENANT_0);
        t1.setTemplateCode("CORE_TEST_TPL_001");
        t1.setTemplateName("核心服务测试模板");
        t1.setBusinessType(BUSINESS_TYPE);
        t1.setEnabled(1);
        t1.setAmountMin(new BigDecimal("0.00"));
        t1.setAmountMax(new BigDecimal("10000.00"));
        templateMapper.insert(t1);

        WfTemplateNode t1n1 = new WfTemplateNode();
        t1n1.setId(NODE_1_ID);
        t1n1.setTenantId(TENANT_0);
        t1n1.setTemplateId(TEMPLATE_1_ID);
        t1n1.setNodeCode("N1");
        t1n1.setNodeName("部门主管审批");
        t1n1.setNodeOrder(1);
        t1n1.setNodeType("APPROVAL");
        t1n1.setApproveMode("SEQUENTIAL");
        t1n1.setApproverConfig("{\"type\":\"USER\",\"userId\":" + USER_ADMIN + "}");
        t1n1.setAllowTransfer(1);
        t1n1.setAllowAddSign(1);
        templateNodeMapper.insert(t1n1);

        WfTemplateNode t1n2 = new WfTemplateNode();
        t1n2.setId(NODE_2_ID);
        t1n2.setTenantId(TENANT_0);
        t1n2.setTemplateId(TEMPLATE_1_ID);
        t1n2.setNodeCode("N2");
        t1n2.setNodeName("财务审批");
        t1n2.setNodeOrder(2);
        t1n2.setNodeType("APPROVAL");
        t1n2.setApproveMode("SEQUENTIAL");
        t1n2.setApproverConfig("{\"type\":\"USER\",\"userId\":" + USER_ADMIN + "}");
        t1n2.setAllowTransfer(1);
        t1n2.setAllowAddSign(1);
        templateNodeMapper.insert(t1n2);

        // Template 2: enabled but no nodes (for error case test)
        WfTemplate t2 = new WfTemplate();
        t2.setId(TEMPLATE_2_ID);
        t2.setTenantId(TENANT_0);
        t2.setTemplateCode("CORE_TEST_TPL_002");
        t2.setTemplateName("无节点模板");
        t2.setBusinessType("CORE_NO_NODE_TYPE");
        t2.setEnabled(1);
        t2.setAmountMin(new BigDecimal("0.00"));
        t2.setAmountMax(new BigDecimal("999999.99"));
        templateMapper.insert(t2);
    }

    private void cleanup() {
        // Clean up workflow data in correct FK order
        for (long bizId : new long[]{88888001L, 88888002L, 88888003L, 88888004L, 88888005L}) {
            jdbcTemplate.update("DELETE FROM wf_record WHERE business_id = ?", bizId);
            jdbcTemplate.update("DELETE FROM wf_task WHERE business_id = ?", bizId);
            jdbcTemplate.update(
                    "DELETE FROM wf_node_instance WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_id = ?)",
                    bizId);
            jdbcTemplate.update("DELETE FROM wf_instance WHERE business_id = ?", bizId);
            jdbcTemplate.update("DELETE FROM ct_contract WHERE id = ?", bizId);
        }
        jdbcTemplate.update("DELETE FROM approval_routing_rule WHERE id = 230000000000000201");
        // Clean template nodes first (FK to template)
        jdbcTemplate.update("DELETE FROM wf_template_node WHERE template_id IN (?, ?)",
                TEMPLATE_1_ID, TEMPLATE_2_ID);
        jdbcTemplate.update("DELETE FROM wf_template WHERE id IN (?, ?)",
                TEMPLATE_1_ID, TEMPLATE_2_ID);
    }

    private void seedContract(long businessId) {
        seedProject();
        jdbcTemplate.update("""
                INSERT INTO ct_contract (
                    id, tenant_id, project_id, contract_code, contract_name, contract_type,
                    party_a_id, party_b_id, contract_amount, current_amount, paid_amount,
                    contract_status, approval_status, created_by, updated_by
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM ct_contract WHERE id = ?)
                """,
                businessId, TENANT_0, 100L, "WF-CORE-" + businessId, "workflow核心测试合同-" + businessId, "SUB",
                20001L, 20002L, new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO,
                "DRAFT", "APPROVING", USER_ADMIN, USER_ADMIN,
                businessId);
    }

    private void seedProject() {
        jdbcTemplate.update("""
                INSERT INTO pm_project (
                    id, tenant_id, project_code, project_name, project_type,
                    contract_amount, target_cost, status, approval_status,
                    created_by, updated_by, deleted_flag
                )
                SELECT ?, ?, ?, ?, '房建工程', 10000, 8000, 'ACTIVE', 'APPROVED', ?, ?, 0
                WHERE NOT EXISTS (SELECT 1 FROM pm_project WHERE id = ?)
                """,
                100L, TENANT_0, "WF-CORE-PRJ-100", "workflow核心测试项目",
                USER_ADMIN, USER_ADMIN, 100L);
    }
}
