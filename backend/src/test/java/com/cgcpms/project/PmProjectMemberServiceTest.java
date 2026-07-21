package com.cgcpms.project;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.project.service.PmProjectMemberService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        PmProjectMemberService service = new PmProjectMemberService(memberMapper, projectMapper, accessChecker);
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
    void createOverwritesClientTenantAndProject() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        PmProjectMemberService service = new PmProjectMemberService(memberMapper, projectMapper, accessChecker);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        PmProject project = new PmProject();
        project.setId(21L);
        project.setTenantId(11L);
        when(projectMapper.selectById(21L)).thenReturn(project);
        when(memberMapper.selectIdIncludingDeleted(11L, 21L, 31L)).thenReturn(null);
        PmProjectMember member = new PmProjectMember();
        member.setTenantId(999L);
        member.setProjectId(888L);
        member.setUserId(31L);
        member.setRoleCode("OTH");

        service.create(21L, member);

        org.junit.jupiter.api.Assertions.assertEquals(11L, member.getTenantId());
        org.junit.jupiter.api.Assertions.assertEquals(21L, member.getProjectId());
        verify(memberMapper).insert(member);
    }

    @Test
    void createRestoresDeletedMemberButRejectsActiveDuplicate() {
        PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        PmProjectMemberService service = new PmProjectMemberService(memberMapper, projectMapper, accessChecker);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        PmProject project = new PmProject();
        project.setId(21L);
        project.setTenantId(11L);
        when(projectMapper.selectById(21L)).thenReturn(project);
        when(memberMapper.selectIdIncludingDeleted(11L, 21L, 31L)).thenReturn(41L);

        PmProjectMember restored = member(31L);
        when(memberMapper.restoreDeleted(41L, 11L, 21L, restored, 7L)).thenReturn(1);
        assertEquals(41L, service.create(21L, restored));
        verify(memberMapper, never()).insert(restored);

        PmProjectMember duplicate = member(31L);
        when(memberMapper.restoreDeleted(41L, 11L, 21L, duplicate, 7L)).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(21L, duplicate));
        assertEquals("MEMBER_ALREADY_EXISTS", ex.getCode());
    }

    private PmProjectMember member(Long userId) {
        PmProjectMember member = new PmProjectMember();
        member.setUserId(userId);
        member.setRoleCode("OTH");
        return member;
    }
}
