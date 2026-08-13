package com.cgcpms.workflow;

import com.cgcpms.common.exception.BusinessException;

import java.util.Map;
import java.util.Objects;

import static java.util.Map.entry;

/**
 * Immutable registry for workflow submission permissions and protected entry routes.
 */
public final class WorkflowSubmissionPolicy {

    private static final Map<String, Rule> REGISTRY = Map.ofEntries(
            standard(WorkflowBusinessTypes.CONTRACT_APPROVAL, "contract:submit"),
            standard(WorkflowBusinessTypes.PROJECT_APPROVAL, "project:submit"),
            standard(WorkflowBusinessTypes.CONTRACT_REVENUE, "revenue:submit"),
            standard(WorkflowBusinessTypes.PURCHASE_ORDER, "purchase:order:submit"),
            standard(WorkflowBusinessTypes.PURCHASE_REQUEST, "purchase:request:submit"),
            standard(WorkflowBusinessTypes.MATERIAL_RECEIPT, "receipt:submit"),
            standard(WorkflowBusinessTypes.SUB_MEASURE, "subcontract:measure:submit"),
            standard(WorkflowBusinessTypes.PAY_REQUEST, "payment:app:submit"),
            standard(WorkflowBusinessTypes.VAR_ORDER, "variation:order:submit"),
            standard(WorkflowBusinessTypes.CT_CHANGE, "contract:change:submit"),
            standard(WorkflowBusinessTypes.SETTLEMENT, "settlement:submit"),
            standard(WorkflowBusinessTypes.COST_TARGET, "cost:target:submit"),
            standard(WorkflowBusinessTypes.COST_CORRECTIVE_ACTION, "cost:corrective:submit"),
            standard(WorkflowBusinessTypes.MATERIAL_REQUISITION, "requisition:submit"),
            standard(WorkflowBusinessTypes.PROJECT_BUDGET, "budget:submit"),
            standard(WorkflowBusinessTypes.EXPENSE, "expense:submit"),
            standard(WorkflowBusinessTypes.OWNER_SETTLEMENT, "revenue:settlement:submit"),
            standard(WorkflowBusinessTypes.PRODUCTION_MEASUREMENT, "measurement:submit"),
            standard(WorkflowBusinessTypes.PROJECT_SCHEDULE, "schedule:submit"),
            standard(WorkflowBusinessTypes.PROJECT_COMMENCEMENT, "project:commencement:submit"),
            standard(WorkflowBusinessTypes.PROJECT_PERIOD_PLAN, "schedule:submit"),
            standard(WorkflowBusinessTypes.PROJECT_CORRECTIVE_ACTION, "schedule:correct"),
            standard(WorkflowBusinessTypes.TECHNICAL_SCHEME, "technical:scheme:submit"),
            standard(WorkflowBusinessTypes.PROJECT_FINAL_ACCEPTANCE, "closeout:acceptance:submit"),
            dedicated(WorkflowBusinessTypes.BID_COST_TARGET_TRANSFER, "cost:subject:transfer:submit"),
            dedicated(WorkflowBusinessTypes.FINANCE_COST_ALLOCATION, "cost:subject:allocation:submit"),
            dedicated(WorkflowBusinessTypes.QS_RECTIFICATION, "quality:rectification:submit"),
            dedicated(WorkflowBusinessTypes.QS_CONSEQUENCE, "quality:consequence:submit"));

    private WorkflowSubmissionPolicy() {
    }

    public static Map<String, Rule> registry() {
        return REGISTRY;
    }

    public static String requiredPermission(String businessType) {
        return requireRule(businessType).requiredPermission();
    }

    public static void requireGenericEntryAllowed(String businessType) {
        if (requireRule(businessType).dedicatedEntryOnly()) {
            throw new BusinessException("DEDICATED_WORKFLOW_REQUIRED", "该业务必须通过业务单据入口提交审批");
        }
    }

    private static Rule requireRule(String businessType) {
        Rule rule = businessType == null || businessType.isBlank() ? null : REGISTRY.get(businessType);
        if (rule == null) {
            throw new BusinessException("UNSUPPORTED_BUSINESS_TYPE", "不支持的业务类型: " + businessType);
        }
        return rule;
    }

    private static Map.Entry<String, Rule> standard(String businessType, String permission) {
        return entry(businessType, new Rule(permission, false));
    }

    private static Map.Entry<String, Rule> dedicated(String businessType, String permission) {
        return entry(businessType, new Rule(permission, true));
    }

    public record Rule(String requiredPermission, boolean dedicatedEntryOnly) {
        public Rule {
            Objects.requireNonNull(requiredPermission, "requiredPermission");
        }
    }
}
