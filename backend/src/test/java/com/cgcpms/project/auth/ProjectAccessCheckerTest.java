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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectAccessCheckerTest {

    private static final long TENANT_ID = 20L;
    private static final long USER_ID = 7L;

    private final PmProjectMapper projectMapper = mock(PmProjectMapper.class);
    private final PmProjectMemberMapper projectMemberMapper = mock(PmProjectMemberMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final ProjectAccessChecker checker =
            new ProjectAccessChecker(projectMapper, projectMemberMapper, roleMapper);
    private JdbcTemplate jdbc;

    @BeforeEach
    void createSqlFixture() {
        String database = "project_scope_" + UUID.randomUUID().toString().replace("-", "");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
                CREATE TABLE pm_project (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    created_by BIGINT,
                    deleted_flag INT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE pm_project_member (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    project_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    deleted_flag INT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE sys_role (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    role_code VARCHAR(64) NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    data_scope VARCHAR(50) NOT NULL,
                    deleted_flag INT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE sys_user_role (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    role_id BIGINT NOT NULL
                )
                """);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEPT_AND_CHILD", "3", "UNKNOWN"})
    void unknownOrUnimplementedScopeFailsClosedWithoutManagerBypass(String dataScope) {
        setUser("PROJECT_MANAGER");
        when(roleMapper.selectList(any())).thenReturn(List.of(role("PROJECT_MANAGER", dataScope)));
        when(projectMemberMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectList(any())).thenReturn(List.of(
                project(1L, TENANT_ID, USER_ID, 99L),
                project(2L, TENANT_ID, 88L, USER_ID),
                project(3L, TENANT_ID, 88L, 99L)));

        assertEquals(List.of(), checker.accessibleProjectIds());
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
    void projectMemberScopeUsesMembershipWithoutProjectManagerIdBypass() {
        setUser("PROJECT_MANAGER");
        when(roleMapper.selectList(any())).thenReturn(List.of(role("PROJECT_MANAGER", "PROJECT_MEMBER")));
        when(projectMemberMapper.selectList(any())).thenReturn(List.of(member(2L, USER_ID, "ACTIVE")));
        when(projectMapper.selectList(any())).thenReturn(List.of(
                project(1L, TENANT_ID, USER_ID, 99L),
                project(2L, TENANT_ID, 88L, 99L),
                project(3L, TENANT_ID, 88L, USER_ID)));

        assertEquals(List.of(2L), checker.accessibleProjectIds());
    }

    @Test
    void projectMemberScopeWinsOverNarrowerScopesInRoleUnion() {
        setUser("PROJECT_MANAGER", "LEGACY_SELF");
        when(roleMapper.selectList(any())).thenReturn(List.of(
                role("PROJECT_MANAGER", "PROJECT_MEMBER"),
                role("LEGACY_SELF", "SELF")));
        when(projectMemberMapper.selectList(any())).thenReturn(List.of(
                member(2L, USER_ID, "ACTIVE"),
                member(3L, USER_ID, "ACTIVE")));
        when(projectMapper.selectList(any())).thenReturn(List.of(
                project(1L, TENANT_ID, 88L, USER_ID),
                project(2L, TENANT_ID, 88L, 99L),
                project(3L, TENANT_ID, 88L, 99L)));

        assertEquals(List.of(2L, 3L), checker.accessibleProjectIds());
    }

    @Test
    void sqlAllScopeIncludesOnlyCurrentTenantUndeletedProjects() {
        setUser("VIEWER");
        insertProject(1L, TENANT_ID, 99L, 0);
        insertProject(2L, TENANT_ID + 1, 99L, 0);
        insertProject(3L, TENANT_ID, 99L, 1);
        insertRole(101L, "VIEWER", "ALL", "ENABLE", 0);
        assignRole(201L, 101L);

        assertEquals(List.of(1L), query(checker.sqlScope()));
    }

    @Test
    void sqlSelfAndActiveMembershipFormAUnion() {
        setUser("SELF_ROLE");
        insertProject(1L, TENANT_ID, USER_ID, 0);
        insertProject(2L, TENANT_ID, 99L, 0);
        insertProject(3L, TENANT_ID, 99L, 0);
        insertRole(101L, "SELF_ROLE", "SELF", "ENABLE", 0);
        assignRole(201L, 101L);
        insertMember(301L, TENANT_ID, 2L, "ACTIVE", 0);

        assertEquals(List.of(1L, 2L), query(checker.sqlScope()));
    }

    @Test
    void sqlProjectMemberScopeRejectsCreatorWithoutActiveMembership() {
        setUser("PROJECT_ROLE");
        insertProject(1L, TENANT_ID, USER_ID, 0);
        insertProject(2L, TENANT_ID, 99L, 0);
        insertRole(101L, "PROJECT_ROLE", "PROJECT_MEMBER", "ENABLE", 0);
        assignRole(201L, 101L);

        assertEquals(List.of(), query(checker.sqlScope()));

        insertMember(301L, TENANT_ID, 2L, "ACTIVE", 0);
        assertEquals(List.of(2L), query(checker.sqlScope()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEPT", "DEPT_AND_CHILD", "CUSTOM", "NONE"})
    void sqlUnsupportedScopesFailClosed(String dataScope) {
        setUser("SCOPED_ROLE");
        insertProject(1L, TENANT_ID, USER_ID, 0);
        insertRole(101L, "SCOPED_ROLE", dataScope, "ENABLE", 0);
        assignRole(201L, 101L);

        assertEquals(List.of(), query(checker.sqlScope()));
    }

    @Test
    void sqlHiddenSuperAdminGetsCurrentTenantOnly() {
        setUser("SUPER_ADMIN");
        insertProject(1L, TENANT_ID, 99L, 0);
        insertProject(2L, TENANT_ID + 1, 99L, 0);
        insertRole(101L, "SUPER_ADMIN", "NONE", "ENABLE", 0);
        assignRole(201L, 101L);

        assertEquals(List.of(1L), query(checker.sqlScope()));
    }

    @Test
    void sqlInactiveDeletedAndCrossTenantMembershipDoesNotGrantAccess() {
        setUser("PROJECT_ROLE");
        insertProject(1L, TENANT_ID, 99L, 0);
        insertProject(2L, TENANT_ID, 99L, 0);
        insertProject(3L, TENANT_ID, 99L, 0);
        insertRole(101L, "PROJECT_ROLE", "PROJECT_MEMBER", "ENABLE", 0);
        assignRole(201L, 101L);
        insertMember(301L, TENANT_ID, 1L, "INACTIVE", 0);
        insertMember(302L, TENANT_ID, 2L, "ACTIVE", 1);
        insertMember(303L, TENANT_ID + 1, 3L, "ACTIVE", 0);

        assertEquals(List.of(), query(checker.sqlScope()));
    }

    @Test
    void sqlExtraProjectIdCannotWidenScope() {
        setUser();
        insertProject(1L, TENANT_ID, USER_ID, 0);
        insertProject(2L, TENANT_ID, 99L, 0);
        ProjectAccessChecker.ProjectSqlScope scope = checker.sqlScope();

        assertEquals(List.of(), query(scope, " AND p.id = ?", 2L));
    }

    @Test
    void sqlNoRoleAndInvalidRolesUseSelfScope() {
        insertProject(1L, TENANT_ID, USER_ID, 0);
        insertProject(2L, TENANT_ID, 99L, 0);

        setUser();
        assertEquals(List.of(1L), query(checker.sqlScope()));

        setUser("DISABLED_ALL", "DELETED_ALL");
        insertRole(101L, "DISABLED_ALL", "ALL", "DISABLE", 0);
        insertRole(102L, "DELETED_ALL", "ALL", "ENABLE", 1);
        assignRole(201L, 101L);
        assignRole(202L, 102L);
        assertEquals(List.of(1L), query(checker.sqlScope()));
    }

    @Test
    void sqlScopeConstructionIsDatabaseFreeAndParameterized() {
        setUser("VIEWER' OR 1=1 --");

        ProjectAccessChecker.ProjectSqlScope scope = checker.sqlScope();

        verifyNoInteractions(projectMapper, projectMemberMapper, roleMapper);
        assertFalse(scope.predicate().contains("VIEWER' OR 1=1 --"));
        assertTrue(scope.predicate().contains("UPPER(r.role_code) IN (?)"));
        assertEquals(List.of(TENANT_ID, TENANT_ID, USER_ID, TENANT_ID, USER_ID,
                "VIEWER' OR 1=1 --", USER_ID), scope.parameters());
    }

    private void setUser(String... roleCodes) {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ID)
                .add("username", "scope-user")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of(roleCodes))
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

    private List<Long> query(ProjectAccessChecker.ProjectSqlScope scope) {
        return query(scope, "");
    }

    private List<Long> query(ProjectAccessChecker.ProjectSqlScope scope,
                             String suffix, Object... suffixParameters) {
        List<Object> parameters = new ArrayList<>(scope.parameters());
        parameters.addAll(List.of(suffixParameters));
        return jdbc.queryForList(
                "SELECT p.id FROM pm_project p WHERE " + scope.predicate() + suffix + " ORDER BY p.id",
                Long.class,
                parameters.toArray());
    }

    private void insertProject(Long id, Long tenantId, Long createdBy, int deletedFlag) {
        jdbc.update("INSERT INTO pm_project (id, tenant_id, created_by, deleted_flag) VALUES (?, ?, ?, ?)",
                id, tenantId, createdBy, deletedFlag);
    }

    private void insertMember(Long id, Long tenantId, Long projectId, String status, int deletedFlag) {
        jdbc.update("""
                        INSERT INTO pm_project_member
                            (id, tenant_id, project_id, user_id, status, deleted_flag)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                id, tenantId, projectId, USER_ID, status, deletedFlag);
    }

    private void insertRole(Long id, String roleCode, String dataScope, String status, int deletedFlag) {
        jdbc.update("""
                        INSERT INTO sys_role
                            (id, tenant_id, role_code, status, data_scope, deleted_flag)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                id, TENANT_ID, roleCode, status, dataScope, deletedFlag);
    }

    private void assignRole(Long id, Long roleId) {
        jdbc.update("INSERT INTO sys_user_role (id, tenant_id, user_id, role_id) VALUES (?, ?, ?, ?)",
                id, TENANT_ID, USER_ID, roleId);
    }
}
