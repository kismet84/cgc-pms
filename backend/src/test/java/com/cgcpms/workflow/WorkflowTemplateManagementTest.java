package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.workflow.controller.WorkflowTemplateController;
import com.cgcpms.workflow.dto.WorkflowTemplateNodeReorderRequest;
import com.cgcpms.workflow.dto.WorkflowTemplateNodeRequest;
import com.cgcpms.workflow.dto.WorkflowTemplateUpdateRequest;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfNodeInstance;
import com.cgcpms.workflow.entity.WfTemplate;
import com.cgcpms.workflow.entity.WfTemplateNode;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfNodeInstanceMapper;
import com.cgcpms.workflow.mapper.WfTemplateMapper;
import com.cgcpms.workflow.mapper.WfTemplateNodeMapper;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class WorkflowTemplateManagementTest {

    private static final long TEMPLATE_ID = 230000000000000001L;
    private static final long NODE_1_ID = 230000000000000101L;
    private static final long NODE_2_ID = 230000000000000102L;
    private static final long TENANT_ID = 982300L;
    private static final long USER_ADMIN = 98230001L;
    private static final long PROJECT_ID = 98230002L;
    private static final long PARTY_A_ID = 98230003L;
    private static final long PARTY_B_ID = 98230004L;
    private static final long BUSINESS_ID = 230000000000000901L;
    private static final String BUSINESS_TYPE = WorkflowBusinessTypes.CONTRACT_APPROVAL;
    private static final BigDecimal TEMPLATE_TEST_AMOUNT = new BigDecimal("1000000000.00");

    @Autowired private WorkflowTemplateService workflowTemplateService;
    @Autowired private WorkflowEngine workflowEngine;
    @Autowired private WfTemplateMapper templateMapper;
    @Autowired private WfTemplateNodeMapper nodeMapper;
    @Autowired private WfInstanceMapper instanceMapper;
    @Autowired private WfNodeInstanceMapper nodeInstanceMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
        cleanup();
        seedUser();
        seedPartners();
        seedTemplate();
    }

    @AfterEach
    void tearDown() {
        cleanup();
        UserContext.clear();
    }

    @Test
    @DisplayName("管理员可分页查询审批模板并读取节点详情")
    void listAndDetailReturnTemplatesWithNodes() {
        PageResult<WfTemplateVO> page = workflowTemplateService.listTemplates(1, 20, BUSINESS_TYPE, 1, "测试流程");

        assertEquals(1, page.getTotal());
        WfTemplateVO summary = page.getRecords().get(0);
        assertEquals(String.valueOf(TEMPLATE_ID), summary.getId());
        assertEquals("测试流程", summary.getTemplateName());
        assertEquals(BUSINESS_TYPE, summary.getBusinessType());
        assertEquals(2, summary.getNodeCount());

        WfTemplateVO detail = workflowTemplateService.getTemplateDetail(TEMPLATE_ID);
        assertEquals(2, detail.getNodes().size());
        assertEquals("项目经理审批", detail.getNodes().get(0).getNodeName());
        assertEquals("总经理审批", detail.getNodes().get(1).getNodeName());
    }

    @Test
    @DisplayName("管理员可更新模板基础信息")
    void updateTemplateChangesEditableFieldsOnly() {
        WorkflowTemplateUpdateRequest request = new WorkflowTemplateUpdateRequest();
        request.setTemplateName("合同审批流程-调整后");
        request.setEnabled(0);
        request.setAmountMin(new BigDecimal("1000.00"));
        request.setAmountMax(new BigDecimal("9000.00"));
        request.setRemark("仅影响新发起实例");

        workflowTemplateService.updateTemplate(TEMPLATE_ID, request);

        WfTemplate updated = templateMapper.selectById(TEMPLATE_ID);
        assertEquals("合同审批流程-调整后", updated.getTemplateName());
        assertEquals(0, updated.getEnabled());
        assertEquals(new BigDecimal("1000.00"), updated.getAmountMin());
        assertEquals(new BigDecimal("9000.00"), updated.getAmountMax());
        assertEquals(BUSINESS_TYPE, updated.getBusinessType(), "业务类型不可被编辑接口改写");
    }

    @Test
    @DisplayName("管理员可新增、编辑、删除节点并自动重排")
    void createUpdateDeleteAndReorderNodes() {
        WorkflowTemplateNodeRequest createRequest = new WorkflowTemplateNodeRequest();
        createRequest.setNodeName("法务审批");
        createRequest.setNodeType("APPROVAL");
        createRequest.setApproveMode("OR_SIGN");
        createRequest.setApproverConfig(userApproverConfig());
        createRequest.setAllowTransfer(1);
        createRequest.setAllowAddSign(0);
        createRequest.setTimeoutHours(24);

        String newNodeId = workflowTemplateService.createNode(TEMPLATE_ID, createRequest).getId();
        WfTemplateNode created = nodeMapper.selectById(Long.valueOf(newNodeId));
        assertEquals("N3", created.getNodeCode());
        assertEquals(3, created.getNodeOrder());
        assertEquals("法务审批", created.getNodeName());

        WorkflowTemplateNodeRequest updateRequest = new WorkflowTemplateNodeRequest();
        updateRequest.setNodeName("法务复核");
        updateRequest.setNodeCode("LEGAL");
        updateRequest.setNodeOrder(2);
        updateRequest.setNodeType("APPROVAL");
        updateRequest.setApproveMode("COUNTERSIGN");
        updateRequest.setApproverConfig(userApproverConfig());
        updateRequest.setAllowTransfer(0);
        updateRequest.setAllowAddSign(1);
        updateRequest.setTimeoutHours(48);
        workflowTemplateService.updateNode(TEMPLATE_ID, Long.valueOf(newNodeId), updateRequest);

        WfTemplateNode updated = nodeMapper.selectById(Long.valueOf(newNodeId));
        assertEquals("LEGAL", updated.getNodeCode());
        assertEquals("法务复核", updated.getNodeName());
        assertEquals("COUNTERSIGN", updated.getApproveMode());

        WorkflowTemplateNodeReorderRequest reorderRequest = new WorkflowTemplateNodeReorderRequest();
        reorderRequest.setNodeIds(List.of(Long.valueOf(newNodeId), NODE_1_ID, NODE_2_ID));
        workflowTemplateService.reorderNodes(TEMPLATE_ID, reorderRequest);

        List<WfTemplateNode> reordered = selectNodes();
        assertEquals(Long.valueOf(newNodeId), reordered.get(0).getId());
        assertEquals(1, reordered.get(0).getNodeOrder());
        assertEquals(NODE_1_ID, reordered.get(1).getId());
        assertEquals(2, reordered.get(1).getNodeOrder());

        workflowTemplateService.deleteNode(TEMPLATE_ID, Long.valueOf(newNodeId));

        List<WfTemplateNode> afterDelete = selectNodes();
        assertEquals(2, afterDelete.size());
        assertEquals(1, afterDelete.get(0).getNodeOrder());
        assertEquals(2, afterDelete.get(1).getNodeOrder());
    }

    @Test
    @DisplayName("禁止删除最后一个有效审批节点")
    void deleteNodeRejectsRemovingLastEffectiveNode() {
        workflowTemplateService.deleteNode(TEMPLATE_ID, NODE_2_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowTemplateService.deleteNode(TEMPLATE_ID, NODE_1_ID));

        assertEquals("TEMPLATE_LAST_NODE", ex.getCode());
    }

    @Test
    @DisplayName("模板变更只影响新发起审批实例，不回写旧实例节点快照")
    void templateChangesOnlyAffectNewInstances() {
        seedContract(BUSINESS_ID);
        WfInstance oldInstance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_ID,
                BUSINESS_TYPE, BUSINESS_ID,
                "模板快照测试-旧实例", TEMPLATE_TEST_AMOUNT,
                null, null, "{}", "{}", null);

        WorkflowTemplateNodeRequest createRequest = new WorkflowTemplateNodeRequest();
        createRequest.setNodeName("新增节点");
        createRequest.setNodeType("APPROVAL");
        createRequest.setApproveMode("SEQUENTIAL");
        createRequest.setApproverConfig(userApproverConfig());
        createRequest.setAllowTransfer(1);
        createRequest.setAllowAddSign(1);
        workflowTemplateService.createNode(TEMPLATE_ID, createRequest);

        seedContract(BUSINESS_ID + 1);
        WfInstance newInstance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_ID,
                BUSINESS_TYPE, BUSINESS_ID + 1,
                "模板快照测试-新实例", TEMPLATE_TEST_AMOUNT,
                null, null, "{}", "{}", null);

        List<WfNodeInstance> oldNodes = selectNodeInstances(oldInstance.getId());
        List<WfNodeInstance> newNodes = selectNodeInstances(newInstance.getId());
        assertEquals(2, oldNodes.size());
        assertEquals(3, newNodes.size());
        assertEquals("新增节点", newNodes.get(2).getNodeName());
    }

    @Test
    @DisplayName("模板管理控制器仅允许管理员角色")
    void controllerRequiresAdminRole() {
        PreAuthorize classAuth = WorkflowTemplateController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classAuth);
        assertTrue(classAuth.value().contains("ADMIN"));
        assertTrue(classAuth.value().contains("SUPER_ADMIN"));
    }

    private void seedTemplate() {
        WfTemplate template = new WfTemplate();
        template.setId(TEMPLATE_ID);
        template.setTenantId(TENANT_ID);
        template.setTemplateCode("TASK023-CONTRACT-APPROVAL");
        template.setTemplateName("测试流程");
        template.setBusinessType(BUSINESS_TYPE);
        template.setEnabled(1);
        template.setAmountMin(TEMPLATE_TEST_AMOUNT);
        template.setAmountMax(TEMPLATE_TEST_AMOUNT);
        templateMapper.insert(template);

        WfTemplateNode first = new WfTemplateNode();
        first.setId(NODE_1_ID);
        first.setTenantId(TENANT_ID);
        first.setTemplateId(TEMPLATE_ID);
        first.setNodeCode("N1");
        first.setNodeName("项目经理审批");
        first.setNodeOrder(1);
        first.setNodeType("APPROVAL");
        first.setApproveMode("SEQUENTIAL");
        first.setApproverConfig(userApproverConfig());
        first.setAllowTransfer(1);
        first.setAllowAddSign(1);
        nodeMapper.insert(first);

        WfTemplateNode second = new WfTemplateNode();
        second.setId(NODE_2_ID);
        second.setTenantId(TENANT_ID);
        second.setTemplateId(TEMPLATE_ID);
        second.setNodeCode("N2");
        second.setNodeName("总经理审批");
        second.setNodeOrder(2);
        second.setNodeType("APPROVAL");
        second.setApproveMode("SEQUENTIAL");
        second.setApproverConfig(userApproverConfig());
        second.setAllowTransfer(1);
        second.setAllowAddSign(1);
        nodeMapper.insert(second);
    }

    private List<WfTemplateNode> selectNodes() {
        return nodeMapper.selectList(new LambdaQueryWrapper<WfTemplateNode>()
                .eq(WfTemplateNode::getTemplateId, TEMPLATE_ID)
                .orderByAsc(WfTemplateNode::getNodeOrder));
    }

    private List<WfNodeInstance> selectNodeInstances(Long instanceId) {
        return nodeInstanceMapper.selectList(new LambdaQueryWrapper<WfNodeInstance>()
                .eq(WfNodeInstance::getInstanceId, instanceId)
                .orderByAsc(WfNodeInstance::getNodeOrder));
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM wf_record WHERE business_id IN (?, ?)", BUSINESS_ID, BUSINESS_ID + 1);
        jdbcTemplate.update("DELETE FROM wf_task WHERE business_id IN (?, ?)", BUSINESS_ID, BUSINESS_ID + 1);
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_id IN (?, ?))", BUSINESS_ID, BUSINESS_ID + 1);
        jdbcTemplate.update("DELETE FROM wf_instance WHERE business_id IN (?, ?)", BUSINESS_ID, BUSINESS_ID + 1);
        jdbcTemplate.update("DELETE FROM ct_contract WHERE id IN (?, ?)", BUSINESS_ID, BUSINESS_ID + 1);
        jdbcTemplate.update("DELETE FROM pm_project WHERE id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM md_partner WHERE id IN (?, ?)", PARTY_A_ID, PARTY_B_ID);
        jdbcTemplate.update("DELETE FROM wf_template_node WHERE template_id = ?", TEMPLATE_ID);
        jdbcTemplate.update("DELETE FROM wf_template WHERE id = ?", TEMPLATE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ADMIN);
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
                businessId, TENANT_ID, PROJECT_ID, "WF-TPL-" + businessId, "workflow模板测试合同-" + businessId, "SUB",
                PARTY_A_ID, PARTY_B_ID, new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO,
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
                PROJECT_ID, TENANT_ID, "WF-TPL-PRJ-98230002", "workflow模板测试项目",
                USER_ADMIN, USER_ADMIN, PROJECT_ID);
    }

    private void seedUser() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, tenant_id, username, password, real_name, status, is_admin,
                    created_by, updated_by, deleted_flag, remark
                ) VALUES (?, ?, 'workflow-template-admin', '{noop}test', '流程模板测试管理员',
                          'ENABLE', 1, ?, ?, 0, 'workflow-template-isolation-test')
                """, USER_ADMIN, TENANT_ID, USER_ADMIN, USER_ADMIN);
    }

    private void seedPartners() {
        jdbcTemplate.update("""
                INSERT INTO md_partner (
                    id, tenant_id, partner_code, partner_name, partner_type, status,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, ?, 'WF-TPL-PA', '流程模板测试甲方', 'CUSTOMER', 'ENABLE', ?, ?, 0)
                """, PARTY_A_ID, TENANT_ID, USER_ADMIN, USER_ADMIN);
        jdbcTemplate.update("""
                INSERT INTO md_partner (
                    id, tenant_id, partner_code, partner_name, partner_type, status,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, ?, 'WF-TPL-PB', '流程模板测试乙方', 'SUPPLIER', 'ENABLE', ?, ?, 0)
                """, PARTY_B_ID, TENANT_ID, USER_ADMIN, USER_ADMIN);
    }

    private String userApproverConfig() {
        return "{\"type\":\"USER\",\"userId\":" + USER_ADMIN + "}";
    }
}
