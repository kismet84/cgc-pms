package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.org.mapper.OrgPositionMapper;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApproverResolverTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private OrgPositionMapper orgPositionMapper;
    @Mock
    private PmProjectMemberMapper pmProjectMemberMapper;

    @Test
    @DisplayName("PROJECT_ROLE审批人查询显式包含tenant条件")
    @SuppressWarnings("unchecked")
    void projectRoleResolverAddsTenantCondition() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), PmProjectMemberMapper.class.getName()),
                PmProjectMember.class);
        ApproverResolver resolver = new ApproverResolver(
                sysUserMapper,
                sysUserRoleMapper,
                sysRoleMapper,
                orgPositionMapper,
                pmProjectMemberMapper,
                new ObjectMapper());
        when(pmProjectMemberMapper.selectList(any())).thenReturn(List.of());

        try {
            resolver.resolve("{\"type\":\"PROJECT_ROLE\",\"roleCode\":\"PM\"}", 7L, 10001L);
        } catch (Exception ignored) {
            // The resolver throws NO_APPROVER after the mapper returns no rows; only the query is under test here.
        }

        ArgumentCaptor<LambdaQueryWrapper<PmProjectMember>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(pmProjectMemberMapper).selectList(captor.capture());
        String sqlSegment = captor.getValue().getCustomSqlSegment();
        assertTrue(sqlSegment.contains("tenant_id") || sqlSegment.contains("tenantId"),
                "PROJECT_ROLE项目成员查询必须显式携带tenant条件，当前SQL片段: " + sqlSegment);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(7L),
                "PROJECT_ROLE项目成员查询必须绑定当前租户ID");
    }
}
