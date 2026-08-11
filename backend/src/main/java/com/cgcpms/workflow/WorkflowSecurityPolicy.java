package com.cgcpms.workflow;

import com.cgcpms.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/** Strict, executable workflow security policy stored in template conditionRule. */
public record WorkflowSecurityPolicy(
        boolean preventInitiatorApproval,
        int maxApprovalsPerUser,
        boolean requireProjectMembership,
        boolean allowAdminFallback) {

    private static final Set<String> FIELDS = Set.of(
            "preventInitiatorApproval", "maxApprovalsPerUser",
            "requireProjectMembership", "allowAdminFallback");

    public static WorkflowSecurityPolicy parse(ObjectMapper objectMapper, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()
                    || root.size() != FIELDS.size()
                    || !FIELDS.stream().allMatch(root::has)
                    || !root.get("preventInitiatorApproval").isBoolean()
                    || !root.get("maxApprovalsPerUser").isIntegralNumber()
                    || !root.get("requireProjectMembership").isBoolean()
                    || !root.get("allowAdminFallback").isBoolean()) {
                throw invalid();
            }
            int maximum = root.get("maxApprovalsPerUser").intValue();
            if (maximum < 1 || maximum > 100) throw invalid();
            return new WorkflowSecurityPolicy(
                    root.get("preventInitiatorApproval").booleanValue(), maximum,
                    root.get("requireProjectMembership").booleanValue(),
                    root.get("allowAdminFallback").booleanValue());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("WORKFLOW_SECURITY_POLICY_INVALID", "工作流安全策略格式无效", exception);
        }
    }

    public static WorkflowSecurityPolicy legacy() {
        return new WorkflowSecurityPolicy(false, 100, false, true);
    }

    public static WorkflowSecurityPolicy defaultFor(String businessType) {
        boolean separated = WorkflowBusinessTypes.CONTRACT_APPROVAL.equals(businessType)
                || WorkflowBusinessTypes.OWNER_SETTLEMENT.equals(businessType)
                || WorkflowBusinessTypes.PAY_REQUEST.equals(businessType)
                || WorkflowBusinessTypes.PROJECT_BUDGET.equals(businessType);
        return new WorkflowSecurityPolicy(separated, 1, true, false);
    }

    public static WorkflowSecurityPolicy parseOrLegacy(ObjectMapper objectMapper, String json) {
        return json == null || json.isBlank() ? legacy() : parse(objectMapper, json);
    }

    public String toCanonicalJson(ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("preventInitiatorApproval", preventInitiatorApproval);
        root.put("maxApprovalsPerUser", maxApprovalsPerUser);
        root.put("requireProjectMembership", requireProjectMembership);
        root.put("allowAdminFallback", allowAdminFallback);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化工作流安全策略", exception);
        }
    }

    private static BusinessException invalid() {
        return new BusinessException("WORKFLOW_SECURITY_POLICY_INVALID", "工作流安全策略必须严格包含四个受支持字段");
    }
}
