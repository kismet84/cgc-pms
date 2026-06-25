package com.cgcpms.workflow.service;

import com.cgcpms.common.TestUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
class ApproverResolverTenantIntegrationTest {

    private static final long PROJECT_ID = 99012001L;
    private static final long TENANT_7_USER = 99012007L;
    private static final long TENANT_8_USER = 99012008L;

    @Autowired
    private ApproverResolver approverResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM pm_project_member WHERE project_id = ?", PROJECT_ID);
        TestUserContext.clear();
    }

    @Test
    @DisplayName("PROJECT_ROLE只解析当前租户项目成员")
    void projectRoleResolverOnlyReturnsCurrentTenantMembers() {
        TestUserContext.setAdmin(7L, TENANT_7_USER);
        jdbcTemplate.update("""
                INSERT INTO pm_project_member
                    (id, tenant_id, project_id, user_id, role_code, status, deleted_flag)
                VALUES
                    (?, 7, ?, ?, 'PM', 'ACTIVE', 0),
                    (?, 8, ?, ?, 'PM', 'ACTIVE', 0)
                """,
                9901200701L, PROJECT_ID, TENANT_7_USER,
                9901200801L, PROJECT_ID, TENANT_8_USER);

        List<Long> approvers = approverResolver.resolve(
                "{\"type\":\"PROJECT_ROLE\",\"roleCode\":\"PM\"}",
                7L,
                PROJECT_ID);

        assertEquals(List.of(TENANT_7_USER), approvers);
    }
}
