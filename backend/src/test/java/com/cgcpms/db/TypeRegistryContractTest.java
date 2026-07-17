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

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
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
}
