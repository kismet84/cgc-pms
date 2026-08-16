package com.cgcpms.workflow;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfNodeInstance;
import com.cgcpms.workflow.entity.WfTemplateNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowSecurityPolicyContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsOnlyCanonicalFourFieldPolicy() {
        WorkflowSecurityPolicy policy = WorkflowSecurityPolicy.parse(objectMapper, """
                {"allowAdminFallback":false,"requireProjectMembership":true,
                 "maxApprovalsPerUser":1,"preventInitiatorApproval":true}
                """);

        assertTrue(policy.preventInitiatorApproval());
        assertEquals(1, policy.maxApprovalsPerUser());
        assertTrue(policy.requireProjectMembership());
        assertFalse(policy.allowAdminFallback());
        assertEquals("{\"preventInitiatorApproval\":true,\"maxApprovalsPerUser\":1,"
                        + "\"requireProjectMembership\":true,\"allowAdminFallback\":false}",
                policy.toCanonicalJson(objectMapper));
    }

    @Test
    void rejectsMissingExtraWrongTypeAndOutOfRangeFields() {
        assertInvalid("{\"preventInitiatorApproval\":true,\"maxApprovalsPerUser\":1,"
                + "\"requireProjectMembership\":true}");
        assertInvalid("{\"preventInitiatorApproval\":true,\"maxApprovalsPerUser\":1,"
                + "\"requireProjectMembership\":true,\"allowAdminFallback\":false,\"script\":\"x\"}");
        assertInvalid("{\"preventInitiatorApproval\":\"true\",\"maxApprovalsPerUser\":1,"
                + "\"requireProjectMembership\":true,\"allowAdminFallback\":false}");
        assertInvalid("{\"preventInitiatorApproval\":true,\"maxApprovalsPerUser\":0,"
                + "\"requireProjectMembership\":true,\"allowAdminFallback\":false}");
    }

    @Test
    void runtimeEntitiesCarrySubmittedPolicyAndNodeCapabilities() {
        WfInstance instance = new WfInstance();
        instance.setSecurityPolicyJson("{}");
        assertEquals("{}", instance.getSecurityPolicyJson());

        WfNodeInstance node = new WfNodeInstance();
        node.setNodeType("APPROVAL");
        node.setApproverConfig("{\"type\":\"ROLE\",\"roleCode\":\"PROJECT_MANAGER\"}");
        node.setAllowTransfer(0);
        node.setAllowAddSign(1);
        node.setTimeoutHours(24);
        assertEquals("APPROVAL", node.getNodeType());
        assertEquals(0, node.getAllowTransfer());
        assertEquals(1, node.getAllowAddSign());
        assertEquals(24, node.getTimeoutHours());
    }

    @Test
    void financeGovernanceAlwaysUsesCompanyFinanceSingleNodeSeparation() {
        WorkflowSecurityPolicy configured = new WorkflowSecurityPolicy(false, 99, true, true);
        WorkflowSecurityPolicy enforced = WorkflowSecurityPolicy.enforceBusinessMinimum(
                WorkflowBusinessTypes.COST_RECALCULATION, configured);
        assertTrue(enforced.preventInitiatorApproval());
        assertEquals(1, enforced.maxApprovalsPerUser());
        assertFalse(enforced.requireProjectMembership());
        assertFalse(enforced.allowAdminFallback());

        WfTemplateNode node = new WfTemplateNode();
        node.setNodeType("APPROVAL");
        node.setApproveMode(WorkflowConstants.MODE_OR_SIGN);
        node.setApproverConfig("{\"type\":\"ROLE\",\"roleCode\":\"COMPANY_FINANCE\"}");
        node.setAllowTransfer(0);
        node.setAllowAddSign(0);
        WorkflowSecurityPolicy.validateFinanceTemplateShape(
                WorkflowBusinessTypes.COST_RECALCULATION, List.of(node), objectMapper);

        node.setApproverConfig("{\"type\":\"ROLE\",\"roleCode\":\"PROJECT_ACCOUNTANT\"}");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> WorkflowSecurityPolicy.validateFinanceTemplateShape(
                        WorkflowBusinessTypes.COST_RECALCULATION, List.of(node), objectMapper));
        assertEquals("WORKFLOW_FINANCE_TEMPLATE_INVALID", exception.getCode());
    }

    private void assertInvalid(String json) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> WorkflowSecurityPolicy.parse(objectMapper, json));
        assertEquals("WORKFLOW_SECURITY_POLICY_INVALID", exception.getCode());
    }
}
