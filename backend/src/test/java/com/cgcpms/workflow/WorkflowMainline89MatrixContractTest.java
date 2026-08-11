package com.cgcpms.workflow;

import com.cgcpms.workflow.service.WorkflowBusinessCodeResolver;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowMainline89MatrixContractTest {

    private static final Map<String, String> MATRIX = new LinkedHashMap<>();

    static {
        MATRIX.put("BID_COST_TARGET_TRANSFER", "cost:subject:transfer:submit");
        MATRIX.put("CONTRACT_APPROVAL", "contract:submit");
        MATRIX.put("EXPENSE", "expense:submit");
        MATRIX.put("FINANCE_COST_ALLOCATION", "cost:subject:allocation:submit");
        MATRIX.put("MATERIAL_RECEIPT", "receipt:submit");
        MATRIX.put("MATERIAL_REQUISITION", "requisition:submit");
        MATRIX.put("OWNER_SETTLEMENT", "revenue:settlement:submit");
        MATRIX.put("PAY_REQUEST", "payment:app:submit");
        MATRIX.put("PRODUCTION_MEASUREMENT", "measurement:submit");
        MATRIX.put("PROJECT_BUDGET", "budget:submit");
        MATRIX.put("PROJECT_COMMENCEMENT", "project:commencement:submit");
        MATRIX.put("PROJECT_SCHEDULE", "schedule:submit");
        MATRIX.put("PROJECT_PERIOD_PLAN", "schedule:submit");
        MATRIX.put("PURCHASE_ORDER", "purchase:order:submit");
        MATRIX.put("PURCHASE_REQUEST", "purchase:request:submit");
        MATRIX.put("SETTLEMENT", "settlement:submit");
        MATRIX.put("SUB_MEASURE", "subcontract:measure:submit");
        MATRIX.put("TECHNICAL_SCHEME", "technical:scheme:submit");
        MATRIX.put("VAR_ORDER", "variation:order:submit");
        MATRIX.put("QS_RECTIFICATION", "quality:rectification:submit");
        MATRIX.put("QS_CONSEQUENCE", "quality:consequence:submit");
    }

    @Test
    void allTwentyOneBusinessTypesHaveConstantsPermissionsAndCodeSources() throws Exception {
        Set<String> constants = Arrays.stream(WorkflowBusinessTypes.class.getFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .map(this::readConstant)
                .collect(Collectors.toSet());
        assertTrue(constants.containsAll(MATRIX.keySet()),
                () -> "missing constants: " + difference(MATRIX.keySet(), constants));

        WorkflowEngine engine = new WorkflowEngine(null, null, null, null, null, null, null);
        MATRIX.forEach((businessType, permission) ->
                assertEquals(permission, engine.getRequiredPermission(businessType), businessType));

        Field sourcesField = WorkflowBusinessCodeResolver.class.getDeclaredField("SOURCES");
        sourcesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> sources = (Map<String, ?>) sourcesField.get(null);
        assertTrue(sources.keySet().containsAll(MATRIX.keySet()),
                () -> "missing code sources: " + difference(MATRIX.keySet(), sources.keySet()));
    }

    private String readConstant(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
        }
    }

    private Set<String> difference(Set<String> expected, Set<String> actual) {
        return expected.stream().filter(value -> !actual.contains(value)).collect(Collectors.toSet());
    }
}
