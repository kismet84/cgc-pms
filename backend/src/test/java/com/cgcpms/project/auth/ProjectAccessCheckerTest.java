package com.cgcpms.project.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectAccessCheckerTest {

    private static final long TENANT_ID = 20L;
    private static final long USER_ID = 7L;

    private final PmProjectMapper projectMapper = mock(PmProjectMapper.class);
    private final PmProjectMemberMapper projectMemberMapper = mock(PmProjectMemberMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final ProjectAccessChecker checker =
            new ProjectAccessChecker(projectMapper, projectMemberMapper, roleMapper);

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEPT_AND_CHILD", "3", "UNKNOWN"})
    void unknownOrUnimplementedScopeDoesNotExpandToTenantWide(String dataScope) {
        setUser("PROJECT_MANAGER");
        when(roleMapper.selectList(any())).thenReturn(List.of(role("PROJECT_MANAGER", dataScope)));
        when(projectMemberMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectList(any())).thenReturn(List.of(
                project(1L, TENANT_ID, USER_ID, 99L),
                project(2L, TENANT_ID, 88L, USER_ID),
                project(3L, TENANT_ID, 88L, 99L)));

        assertEquals(List.of(1L), checker.accessibleProjectIds());
    }

    @Test
    void allScopeStillReturnsTenantProjectsOnly() {
        setUser("VIEWER");
        when(roleMapper.selectList(any())).thenReturn(List.of(role("VIEWER", "ALL")));
        when(projectMemberMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectList(any())).thenReturn(List.of(
                project(1L, TENANT_ID, 88L, 99L),
                project(2L, 21L, 88L, 99L)));

        assertEquals(List.of(1L), checker.accessibleProjectIds());
    }

    @Test
    void activeProjectMembershipGrantsOnlyTheAssignedProject() {
        setUser("PROJECT_OPERATOR");
        when(roleMapper.selectList(any())).thenReturn(List.of(role("PROJECT_OPERATOR", "UNKNOWN")));
        when(projectMemberMapper.selectList(any())).thenReturn(List.of(member(2L, USER_ID, "ACTIVE")));
        when(projectMapper.selectList(any())).thenReturn(List.of(
                project(1L, TENANT_ID, 88L, 99L),
                project(2L, TENANT_ID, 88L, 99L),
                project(3L, TENANT_ID, 88L, 99L)));

        assertEquals(List.of(2L), checker.accessibleProjectIds());
    }

    private void setUser(String roleCode) {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ID)
                .add("username", "scope-user")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of(roleCode))
                .build());
    }

    private SysRole role(String roleCode, String dataScope) {
        SysRole role = new SysRole();
        role.setTenantId(TENANT_ID);
        role.setRoleCode(roleCode);
        role.setDataScope(dataScope);
        return role;
    }

    private PmProject project(Long id, Long tenantId, Long managerId, Long creatorId) {
        PmProject project = new PmProject();
        project.setId(id);
        project.setTenantId(tenantId);
        project.setProjectManagerId(managerId);
        project.setCreatedBy(creatorId);
        return project;
    }

    private PmProjectMember member(Long projectId, Long userId, String status) {
        PmProjectMember member = new PmProjectMember();
        member.setTenantId(TENANT_ID);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setStatus(status);
        return member;
    }
}
