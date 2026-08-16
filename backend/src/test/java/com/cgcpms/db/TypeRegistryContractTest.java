package com.cgcpms.db;

import com.cgcpms.workflow.WorkflowBusinessTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class TypeRegistryContractTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void everyWorkflowBusinessTypeIsRegisteredExactlyOnce() {
        Set<String> constants = java.util.Arrays.stream(WorkflowBusinessTypes.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
                .map(f -> {
                    try { return (String) f.get(null); }
                    catch (IllegalAccessException e) { throw new IllegalStateException(e); }
                }).collect(Collectors.toSet());
        Set<String> registered = Set.copyOf(jdbc.queryForList(
                "SELECT type_code FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND status='ACTIVE'",
                String.class));
        assertEquals(constants, registered);
    }

    @Test
    void everyGovernedCostWriterSourceIsRegistered() {
        Set<String> governedSources = Set.of(
                "MAT_RECEIPT", "MAT_REQUISITION", "SUB_MEASURE", "VAR_ORDER", "CT_CHANGE", "CT_CONTRACT",
                "QUALITY_SAFETY_CONSEQUENCE", "OVERHEAD_ALLOCATION", "OVERHEAD_ALLOCATION_CLEARING",
                "ACCOUNTING_ENTRY_LINE", "EXPENSE_APPLICATION", "FINANCE_COST_ALLOCATION",
                "FINANCE_COST_ALLOCATION_REVERSAL", "BID_COST", "BID_COST_WRITE_OFF", "MATERIAL_RETURN",
                "MATERIAL_RETURN_REVERSAL", "SUPPLIER_RETURN", "SUPPLIER_RETURN_REVERSAL",
                "COST_RECALCULATION_NEGATIVE", "COST_RECALCULATION_POSITIVE", "COST_RECALCULATION_REVERSAL");
        Set<String> registered = Set.copyOf(jdbc.queryForList(
                "SELECT type_code FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND status='ACTIVE'",
                String.class));
        assertTrue(registered.containsAll(governedSources),
                () -> "缺少成本来源注册: " + governedSources.stream().filter(value -> !registered.contains(value)).toList());
    }
}
