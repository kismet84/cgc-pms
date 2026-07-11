package com.cgcpms.project;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.project.service.PmProjectMemberService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
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
}
