package com.cgcpms.workflow;

import com.cgcpms.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.cgcpms.workflow.entity.WfTemplateNode;

import java.util.List;
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
    private static final Set<String> FINANCE_SEPARATION_TYPES = Set.of(
            WorkflowBusinessTypes.BID_COST_TARGET_TRANSFER,
            WorkflowBusinessTypes.FINANCE_COST_ALLOCATION,
            "BID_COST_TARGET_TRANSFER_REVERSAL", "FINANCE_COST_ALLOCATION_REVERSAL",
            WorkflowBusinessTypes.COST_RULE_PLAN,
            WorkflowBusinessTypes.COST_PROJECT_CONFIG,
            WorkflowBusinessTypes.COST_RECALCULATION,
            WorkflowBusinessTypes.COST_POST_CLOSE_ADJUSTMENT,
            WorkflowBusinessTypes.COST_REVERSAL);

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
                || WorkflowBusinessTypes.PROJECT_BUDGET.equals(businessType)
                || requiresFinanceSeparation(businessType);
        return new WorkflowSecurityPolicy(separated, 1, true, false);
    }

    public static boolean requiresFinanceSeparation(String businessType) {
        return businessType != null && FINANCE_SEPARATION_TYPES.contains(businessType);
    }

    public static WorkflowSecurityPolicy enforceBusinessMinimum(String businessType,
                                                                 WorkflowSecurityPolicy configured) {
        if (!requiresFinanceSeparation(businessType)) return configured;
        return new WorkflowSecurityPolicy(true, 1, false, false);
    }

    public static void validateFinanceTemplateShape(String businessType, List<WfTemplateNode> nodes,
                                                    ObjectMapper objectMapper) {
        if (!requiresFinanceSeparation(businessType)) return;
        if (nodes.size() != 1) throw financeTemplateInvalid();
        WfTemplateNode node = nodes.getFirst();
        try {
            JsonNode config = objectMapper.readTree(node.getApproverConfig());
            boolean valid = "APPROVAL".equals(node.getNodeType())
                    && WorkflowConstants.MODE_OR_SIGN.equals(node.getApproveMode())
                    && Integer.valueOf(0).equals(node.getAllowTransfer())
                    && Integer.valueOf(0).equals(node.getAllowAddSign())
                    && config != null && config.isObject() && config.size() == 2
                    && "ROLE".equalsIgnoreCase(config.path("type").asText())
                    && "COMPANY_FINANCE".equals(config.path("roleCode").asText());
            if (!valid) throw financeTemplateInvalid();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw financeTemplateInvalid();
        }
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

    private static BusinessException financeTemplateInvalid() {
        return new BusinessException("WORKFLOW_FINANCE_TEMPLATE_INVALID",
                "财务治理流程必须且只能由 COMPANY_FINANCE 单节点会签，禁止转办和加签");
    }
}
