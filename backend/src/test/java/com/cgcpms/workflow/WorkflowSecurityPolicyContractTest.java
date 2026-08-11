package com.cgcpms.workflow;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfNodeInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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

    private void assertInvalid(String json) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> WorkflowSecurityPolicy.parse(objectMapper, json));
        assertEquals("WORKFLOW_SECURITY_POLICY_INVALID", exception.getCode());
    }
}
