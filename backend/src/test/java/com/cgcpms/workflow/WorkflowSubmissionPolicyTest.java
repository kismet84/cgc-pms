package com.cgcpms.workflow;

import com.cgcpms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowSubmissionPolicyTest {

    private static final Map<String, String> EXPECTED_PERMISSIONS = Map.ofEntries(
            entry(WorkflowBusinessTypes.CONTRACT_APPROVAL, "contract:submit"),
            entry(WorkflowBusinessTypes.PROJECT_APPROVAL, "project:submit"),
            entry(WorkflowBusinessTypes.CONTRACT_REVENUE, "revenue:submit"),
            entry(WorkflowBusinessTypes.PURCHASE_ORDER, "purchase:order:submit"),
            entry(WorkflowBusinessTypes.PURCHASE_REQUEST, "purchase:request:submit"),
            entry(WorkflowBusinessTypes.MATERIAL_RECEIPT, "receipt:submit"),
            entry(WorkflowBusinessTypes.SUB_MEASURE, "subcontract:measure:submit"),
            entry(WorkflowBusinessTypes.PAY_REQUEST, "payment:app:submit"),
            entry(WorkflowBusinessTypes.VAR_ORDER, "variation:order:submit"),
            entry(WorkflowBusinessTypes.CT_CHANGE, "contract:change:submit"),
            entry(WorkflowBusinessTypes.SETTLEMENT, "settlement:submit"),
            entry(WorkflowBusinessTypes.COST_TARGET, "cost:target:submit"),
            entry(WorkflowBusinessTypes.COST_CORRECTIVE_ACTION, "cost:corrective:submit"),
            entry(WorkflowBusinessTypes.MATERIAL_REQUISITION, "requisition:submit"),
            entry(WorkflowBusinessTypes.PROJECT_BUDGET, "budget:submit"),
            entry(WorkflowBusinessTypes.EXPENSE, "expense:submit"),
            entry(WorkflowBusinessTypes.OWNER_SETTLEMENT, "revenue:settlement:submit"),
            entry(WorkflowBusinessTypes.PRODUCTION_MEASUREMENT, "measurement:submit"),
            entry(WorkflowBusinessTypes.PROJECT_SCHEDULE, "schedule:submit"),
            entry(WorkflowBusinessTypes.PROJECT_COMMENCEMENT, "project:commencement:submit"),
            entry(WorkflowBusinessTypes.PROJECT_PERIOD_PLAN, "schedule:submit"),
            entry(WorkflowBusinessTypes.PROJECT_CORRECTIVE_ACTION, "schedule:correct"),
            entry(WorkflowBusinessTypes.TECHNICAL_SCHEME, "technical:scheme:submit"),
            entry(WorkflowBusinessTypes.PROJECT_FINAL_ACCEPTANCE, "closeout:acceptance:submit"),
            entry(WorkflowBusinessTypes.BID_COST_TARGET_TRANSFER, "cost:subject:transfer:submit"),
            entry(WorkflowBusinessTypes.FINANCE_COST_ALLOCATION, "cost:subject:allocation:submit"),
            entry(WorkflowBusinessTypes.QS_RECTIFICATION, "quality:rectification:submit"),
            entry(WorkflowBusinessTypes.QS_CONSEQUENCE, "quality:consequence:submit"));

    @Test
    void registryIsCompleteImmutableAndKeepsPermissionContract() {
        assertEquals(EXPECTED_PERMISSIONS.keySet(), WorkflowSubmissionPolicy.registry().keySet());
        EXPECTED_PERMISSIONS.forEach((businessType, permission) ->
                assertEquals(permission, WorkflowSubmissionPolicy.requiredPermission(businessType), businessType));
        assertThrows(UnsupportedOperationException.class, () -> WorkflowSubmissionPolicy.registry().clear());
    }

    @Test
    void unknownMissingAndUnsupportedTypesFailClosed() {
        for (String businessType : new String[]{null, "", " ", "UNKNOWN", WorkflowBusinessTypes.TECH_ITEM}) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> WorkflowSubmissionPolicy.requiredPermission(businessType));
            assertEquals("UNSUPPORTED_BUSINESS_TYPE", error.getCode());
        }
    }

    @Test
    void sensitiveDedicatedTypesCannotUseGenericSubmissionEntry() {
        for (String businessType : new String[]{
                WorkflowBusinessTypes.BID_COST_TARGET_TRANSFER,
                WorkflowBusinessTypes.FINANCE_COST_ALLOCATION,
                WorkflowBusinessTypes.QS_RECTIFICATION,
                WorkflowBusinessTypes.QS_CONSEQUENCE}) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> WorkflowSubmissionPolicy.requireGenericEntryAllowed(businessType));
            assertEquals("DEDICATED_WORKFLOW_REQUIRED", error.getCode());
        }
        assertDoesNotThrow(() -> WorkflowSubmissionPolicy.requireGenericEntryAllowed(
                WorkflowBusinessTypes.CONTRACT_APPROVAL));
    }
}
