package com.cgcpms.project;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.dto.CreateProjectMemberRequest;
import com.cgcpms.project.dto.UpdateProjectMemberRequest;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.project.service.PmProjectMemberService;
import com.cgcpms.project.vo.PmProjectMemberRoleRowVO;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PmProjectMemberServiceTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void getByIdChecksProjectAccess() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        PmProject project = new PmProject();
        project.setId(21L);
        project.setTenantId(11L);
        when(projectMapper.selectById(21L)).thenReturn(project);
        PmProjectMember member = new PmProjectMember();
        member.setId(31L);
        member.setTenantId(11L);
        member.setProjectId(21L);
        when(memberMapper.selectById(31L)).thenReturn(member);

        service.getById(21L, 31L);

        verify(accessChecker).checkAccess(21L, "访问项目成员");
    }

    @Test
    void createBuildsEntityFromRequestAndServerContext() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        PmProject project = new PmProject();
        project.setId(21L);
        project.setTenantId(11L);
        when(projectMapper.selectById(21L)).thenReturn(project);
        when(userMapper.selectByTenantAndId(11L, 31L)).thenReturn(enabledUser(31L));
        allowRole(roleMapper, userMapper, 31L, "EMPLOYEE");
        when(memberMapper.selectIdIncludingDeleted(11L, 21L, 31L)).thenReturn(null);
        CreateProjectMemberRequest request = member(31L);

        service.create(21L, request);

        org.mockito.ArgumentCaptor<PmProjectMember> inserted =
                org.mockito.ArgumentCaptor.forClass(PmProjectMember.class);
        verify(memberMapper).insert(inserted.capture());
        assertEquals(11L, inserted.getValue().getTenantId());
        assertEquals(21L, inserted.getValue().getProjectId());
        assertEquals(31L, inserted.getValue().getUserId());
        assertNull(inserted.getValue().getCreatedAt());
        assertNull(inserted.getValue().getUpdatedAt());
    }

    @Test
    void createRestoresDeletedMemberButRejectsActiveDuplicate() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        PmProject project = new PmProject();
        project.setId(21L);
        project.setTenantId(11L);
        when(projectMapper.selectById(21L)).thenReturn(project);
        when(userMapper.selectByTenantAndId(11L, 31L)).thenReturn(enabledUser(31L));
        allowRole(roleMapper, userMapper, 31L, "EMPLOYEE");
        when(memberMapper.selectIdIncludingDeleted(11L, 21L, 31L)).thenReturn(41L);

        CreateProjectMemberRequest restored = member(31L);
        when(memberMapper.restoreDeleted(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.eq(21L),
                org.mockito.ArgumentMatchers.any(PmProjectMember.class), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(1);
        assertEquals(41L, service.create(21L, restored));
        verify(memberMapper, never()).insert(org.mockito.ArgumentMatchers.<PmProjectMember>any());

        CreateProjectMemberRequest duplicate = member(31L);
        when(memberMapper.restoreDeleted(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.eq(21L),
                org.mockito.ArgumentMatchers.any(PmProjectMember.class), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(21L, duplicate));
        assertEquals("MEMBER_ALREADY_EXISTS", ex.getCode());
    }

    @Test
    void updateSendsOnlyWhitelistedFieldsToMapper() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        PmProject project = new PmProject();
        project.setId(21L);
        project.setTenantId(11L);
        when(projectMapper.selectById(21L)).thenReturn(project);
        PmProjectMember existing = new PmProjectMember();
        existing.setId(41L);
        existing.setTenantId(11L);
        existing.setProjectId(21L);
        existing.setUserId(31L);
        existing.setRoleCode("EMPLOYEE");
        when(memberMapper.selectById(41L)).thenReturn(existing);
        when(userMapper.selectByTenantAndId(11L, 31L)).thenReturn(enabledUser(31L));
        allowRole(roleMapper, userMapper, 31L, "PROJECT_ACCOUNTANT");

        service.update(21L, 41L, new UpdateProjectMemberRequest(
                31L, "PROJECT_ACCOUNTANT", "项目会计", null, null, "INACTIVE", "updated"));

        org.mockito.ArgumentCaptor<PmProjectMember> updated =
                org.mockito.ArgumentCaptor.forClass(PmProjectMember.class);
        verify(memberMapper).updateById(updated.capture());
        assertEquals(41L, updated.getValue().getId());
        assertEquals("PROJECT_ACCOUNTANT", updated.getValue().getRoleCode());
        assertEquals("项目会计", updated.getValue().getPositionName());
        assertEquals("INACTIVE", updated.getValue().getStatus());
        assertEquals("updated", updated.getValue().getRemark());
        assertNull(updated.getValue().getTenantId());
        assertNull(updated.getValue().getProjectId());
        assertNull(updated.getValue().getUserId());
        assertNull(updated.getValue().getCreatedBy());
        assertNull(updated.getValue().getCreatedAt());
        assertNull(updated.getValue().getUpdatedBy());
        assertNull(updated.getValue().getUpdatedAt());
        assertNull(updated.getValue().getDeletedFlag());
    }

    @Test
    void createRejectsMissingDisabledOrCrossTenantUser() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        PmProject project = new PmProject();
        project.setId(21L);
        project.setTenantId(11L);
        when(projectMapper.selectById(21L)).thenReturn(project);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.create(21L, member(31L)));
        assertEquals("PROJECT_MEMBER_USER_INVALID", missing.getCode());
        verify(memberMapper, never()).insert(org.mockito.ArgumentMatchers.<PmProjectMember>any());

        SysUser disabled = enabledUser(31L);
        disabled.setStatus("DISABLE");
        when(userMapper.selectByTenantAndId(11L, 31L)).thenReturn(disabled);
        BusinessException inactive = assertThrows(BusinessException.class,
                () -> service.create(21L, member(31L)));
        assertEquals("PROJECT_MEMBER_USER_INVALID", inactive.getCode());
    }

    @Test
    void optionsGroupMultipleEnabledProjectRolesPerUser() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        when(projectMapper.selectById(21L)).thenReturn(project(21L));
        when(roleMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                role("EMPLOYEE", "员工"), role("PROJECT_MANAGER", "项目经理")));
        when(memberMapper.selectEnabledProjectRoleRows(11L, 21L, "", -1L)).thenReturn(List.of(
                row(31L, "multi", "多角色用户", "EMPLOYEE"),
                row(31L, "multi", "多角色用户", "PROJECT_MANAGER")));

        var options = service.getOptions(21L);

        assertEquals(List.of("PROJECT_MANAGER", "EMPLOYEE"),
                options.roles().stream().map(item -> item.roleCode()).toList());
        assertEquals(1, options.users().size());
        assertEquals(List.of("PROJECT_MANAGER", "EMPLOYEE"), options.users().getFirst().roleCodes());
        assertEquals(false, options.usersTruncated());
    }

    @Test
    void optionsBoundTenantCandidatesAndReportTruncation() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        when(projectMapper.selectById(21L)).thenReturn(project(21L));
        when(roleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(role("EMPLOYEE", "员工")));
        when(memberMapper.selectEnabledProjectRoleRows(11L, 21L, "候选", -1L)).thenReturn(
                LongStream.rangeClosed(1, 101)
                        .mapToObj(id -> row(id, "candidate-" + id, "候选" + id, "EMPLOYEE"))
                        .toList());

        var options = service.getOptions(21L, " 候选 ", null);

        assertEquals(100, options.users().size());
        assertEquals(true, options.usersTruncated());
        verify(memberMapper).selectEnabledProjectRoleRows(11L, 21L, "候选", -1L);
    }

    @Test
    void createRejectsRoleNotHeldByEnabledUser() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        when(projectMapper.selectById(21L)).thenReturn(project(21L));
        when(userMapper.selectByTenantAndId(11L, 31L)).thenReturn(enabledUser(31L));
        when(roleMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(role("PROJECT_MANAGER", "项目经理"));
        when(userMapper.selectEnabledRoleCodesByTenantAndUserId(11L, 31L))
                .thenReturn(List.of("EMPLOYEE"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(
                21L, new CreateProjectMemberRequest(
                        31L, "PROJECT_MANAGER", null, null, null, null, null)));

        assertEquals("PROJECT_MEMBER_ROLE_MISMATCH", error.getCode());
        verify(memberMapper, never()).insert(org.mockito.ArgumentMatchers.<PmProjectMember>any());
    }

    @Test
    void updateAllowsUnchangedLegacyRoleWithoutCurrentSystemAssignment() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        when(projectMapper.selectById(21L)).thenReturn(project(21L));
        PmProjectMember existing = new PmProjectMember();
        existing.setId(41L);
        existing.setTenantId(11L);
        existing.setProjectId(21L);
        existing.setUserId(31L);
        existing.setRoleCode("PM");
        existing.setStatus("ACTIVE");
        when(memberMapper.selectById(41L)).thenReturn(existing);

        service.update(21L, 41L,
                new UpdateProjectMemberRequest(31L, "PM", "原岗位", null, null, "ACTIVE", null));

        verify(memberMapper).updateById(org.mockito.ArgumentMatchers.<PmProjectMember>argThat(
                member -> "PM".equals(member.getRoleCode())));
        verify(userMapper, never()).selectEnabledRoleCodesByTenantAndUserId(11L, 31L);
    }

    @Test
    void updateRevalidatesRoleWhenReactivatingMember() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PmProjectMemberService service = new PmProjectMemberService(
                memberMapper, projectMapper, accessChecker, userMapper, roleMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        when(projectMapper.selectById(21L)).thenReturn(project(21L));
        PmProjectMember existing = new PmProjectMember();
        existing.setId(41L);
        existing.setTenantId(11L);
        existing.setProjectId(21L);
        existing.setUserId(31L);
        existing.setRoleCode("EMPLOYEE");
        existing.setStatus("INACTIVE");
        when(memberMapper.selectById(41L)).thenReturn(existing);
        when(userMapper.selectByTenantAndId(11L, 31L)).thenReturn(enabledUser(31L));
        when(roleMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(role("EMPLOYEE", "员工"));
        when(userMapper.selectEnabledRoleCodesByTenantAndUserId(11L, 31L)).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(
                21L, 41L,
                new UpdateProjectMemberRequest(31L, "EMPLOYEE", null, null, null, "ACTIVE", null)));

        assertEquals("PROJECT_MEMBER_ROLE_MISMATCH", error.getCode());
        verify(memberMapper, never()).updateById(
                org.mockito.ArgumentMatchers.<PmProjectMember>any());
    }

    private CreateProjectMemberRequest member(Long userId) {
        return new CreateProjectMemberRequest(userId, "EMPLOYEE", null, null, null, null, null);
    }

    private SysUser enabledUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(11L);
        user.setStatus("ENABLE");
        return user;
    }

    private PmProject project(Long id) {
        PmProject project = new PmProject();
        project.setId(id);
        project.setTenantId(11L);
        return project;
    }

    private SysRole role(String code, String name) {
        SysRole role = new SysRole();
        role.setTenantId(11L);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setStatus("ENABLE");
        role.setDataScope("PROJECT_MEMBER");
        return role;
    }

    private PmProjectMemberRoleRowVO row(Long userId, String username, String realName, String roleCode) {
        PmProjectMemberRoleRowVO row = new PmProjectMemberRoleRowVO();
        row.setUserId(userId);
        row.setUsername(username);
        row.setRealName(realName);
        row.setRoleCode(roleCode);
        return row;
    }

    private void allowRole(SysRoleMapper roleMapper, SysUserMapper userMapper,
                           Long userId, String roleCode) {
        when(roleMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(role(roleCode, roleCode));
        when(userMapper.selectEnabledRoleCodesByTenantAndUserId(11L, userId))
                .thenReturn(List.of(roleCode));
    }
}
