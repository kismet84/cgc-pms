package com.cgcpms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Data maintenance table policy")
class DataMaintenanceTablePolicyTest {

    @Test
    @DisplayName("manifest explicitly classifies all 200 current base tables and retains exactly 25")
    void shouldCoverCurrentSchema() {
        DataMaintenanceTablePolicy policy = new DataMaintenanceTablePolicy(new ObjectMapper());

        assertEquals(200, policy.groups().stream().mapToInt(group -> group.tables().size()).sum());
        assertEquals(25, policy.groups().stream()
                .filter(group -> group.disposition() == DataMaintenanceTablePolicy.Disposition.RETAIN)
                .mapToInt(group -> group.tables().size()).sum());
        assertNotNull(policy.fingerprint());
        assertEquals(64, policy.fingerprint().length());
    }
}
