package com.cgcpms.schedule;

import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.service.AlertLifecycleService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.schedule.service.ProjectScheduleService;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProjectScheduleServiceQueryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProjectAccessChecker projectAccessChecker = mock(ProjectAccessChecker.class);
    private final ProjectScheduleService service = new ProjectScheduleService(
            jdbc,
            mock(WorkflowEngine.class),
            projectAccessChecker,
            mock(AlertLogMapper.class),
            mock(AlertLifecycleService.class));

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void allProjectsUsesOnlyAccessibleProjectIdsInsideCurrentTenant() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", 1L).add("username", "admin")
                .add("tenantId", 7L).add("roleCodes", List.of("ADMIN")).build());
        when(projectAccessChecker.accessibleProjectIds()).thenReturn(List.of(11L, 12L));
        doReturn(List.of(Map.of("id", 101L, "projectId", 11L)))
                .when(jdbc).queryForList(anyString(), (Object[]) any(Object[].class));

        List<Map<String, Object>> rows = service.schedules(null);

        assertEquals(1, rows.size());
        verify(jdbc).queryForList(
                argThat(sql -> sql.contains("s.tenant_id=?") && sql.contains("s.project_id IN (?,?)")),
                eq(7L), eq(11L), eq(12L));
        verify(projectAccessChecker, never()).checkAccess(anyLong(), anyString());
    }

    @Test
    void allProjectsReturnsEmptyWithoutIssuingSqlWhenNothingIsAccessible() {
        when(projectAccessChecker.accessibleProjectIds()).thenReturn(List.of());

        assertEquals(List.of(), service.schedules(null));

        verifyNoInteractions(jdbc);
    }
}
