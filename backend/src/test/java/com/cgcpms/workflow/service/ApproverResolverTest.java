package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.org.mapper.OrgPositionMapper;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import com.cgcpms.system.role.SystemRoleContract;
import com.cgcpms.workflow.WorkflowSecurityPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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
    @Mock
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("显式租户在审批解析期间绑定并在结束后恢复")
    void explicitTenantIsScopedForResolutionAndRestored() {
        ApproverResolver resolver = resolver();
        when(sysUserMapper.selectOne(any())).thenAnswer(invocation -> {
            assertEquals(7L, UserContext.getCurrentTenantId());
            return user(1L);
        });

        assertEquals(List.of(1L), resolver.resolve(
                "{\"type\":\"USER\",\"userId\":1}", 7L, null,
                new WorkflowSecurityPolicy(false, 1, false, false)));
        assertNull(UserContext.getCurrentTenantId());
    }

    @Test
    @DisplayName("审批解析拒绝覆盖不一致的现有租户上下文")
    void mismatchedExistingTenantContextIsRejected() {
        TestUserContext.setAdmin(8L, 2L);

        BusinessException exception = assertThrows(BusinessException.class, () -> resolver().resolve(
                "{\"type\":\"USER\",\"userId\":1}", 7L, null,
                new WorkflowSecurityPolicy(false, 1, false, false)));

        assertEquals("TENANT_CONTEXT_MISMATCH", exception.getCode());
        assertEquals(8L, UserContext.getCurrentTenantId());
    }

    @Test
    @DisplayName("PROJECT_ROLE审批人查询显式包含tenant条件")
    @SuppressWarnings("unchecked")
    void projectRoleResolverAddsTenantCondition() {
        initTableInfo();
        ApproverResolver resolver = resolver();
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(1L)));
        when(pmProjectMemberMapper.selectList(any())).thenReturn(List.of(member(1L)));

        assertEquals(List.of(1L), resolver.resolve("{\"type\":\"PROJECT_ROLE\",\"roleCode\":\"PM\"}",
                7L, 10001L, new WorkflowSecurityPolicy(false, 1, true, false)));

        ArgumentCaptor<LambdaQueryWrapper<PmProjectMember>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(pmProjectMemberMapper).selectList(captor.capture());
        String sqlSegment = captor.getValue().getCustomSqlSegment();
        assertTrue(sqlSegment.contains("tenant_id") || sqlSegment.contains("tenantId"),
                "PROJECT_ROLE项目成员查询必须显式携带tenant条件，当前SQL片段: " + sqlSegment);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(7L),
                "PROJECT_ROLE项目成员查询必须绑定当前租户ID");
    }

    @Test
    @DisplayName("项目级ROLE按系统角色与活动项目成员取交集")
    void roleResolverIntersectsActiveProjectMembers() {
        initTableInfo();
        ApproverResolver resolver = resolver();
        SysRole role = role(21L, "PROJECT_MANAGER");
        when(sysRoleMapper.selectOne(any())).thenReturn(role);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRole(1L, 21L), userRole(2L, 21L)));
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(1L), user(2L)));
        when(pmProjectMemberMapper.selectList(any())).thenReturn(List.of(member(2L), member(3L)));

        List<Long> result = resolver.resolve(
                "{\"type\":\"ROLE\",\"roleCode\":\"PROJECT_MANAGER\"}",
                7L, 10001L, new WorkflowSecurityPolicy(false, 1, true, false));

        assertEquals(List.of(2L), result);
    }

    @Test
    @DisplayName("默认无管理员回退且无可用审批人时失败关闭")
    void roleResolverDoesNotFallbackUnlessPolicyAllowsIt() {
        initTableInfo();
        ApproverResolver resolver = resolver();
        when(sysRoleMapper.selectOne(any())).thenReturn(role(21L, "PROJECT_MANAGER"));
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> resolver.resolve(
                "{\"type\":\"ROLE\",\"roleCode\":\"PROJECT_MANAGER\"}",
                7L, 10001L, new WorkflowSecurityPolicy(false, 1, true, false)));

        assertEquals("NO_APPROVER", exception.getCode());
        verify(sysRoleMapper, times(1)).selectOne(any());
    }

    @Test
    @DisplayName("历史ROLE roleId按签认别名解析到固定角色")
    void legacyRoleIdResolvesCanonicalEnabledRole() {
        initTableInfo();
        ApproverResolver resolver = resolver();
        SysRole legacy = role(31L, "COST_MANAGER");
        legacy.setStatus("DISABLE");
        when(sysRoleMapper.selectOne(any())).thenReturn(legacy, role(21L, "PROJECT_ACCOUNTANT"));
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRole(2L, 21L)));
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(2L)));

        assertEquals(List.of(2L), resolver.resolve(
                "{\"type\":\"ROLE\",\"roleId\":31}", 7L, 10001L,
                new WorkflowSecurityPolicy(false, 1, false, false)));
    }

    @Test
    @DisplayName("USER审批人停用后失败关闭")
    void disabledDirectUserIsRejected() {
        ApproverResolver resolver = resolver();
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> resolver.resolve(
                "{\"type\":\"USER\",\"userId\":1}", 7L, 10001L,
                new WorkflowSecurityPolicy(false, 1, false, false)));

        assertEquals("WORKFLOW_APPROVER_INVALID", exception.getCode());
    }

    @Test
    @DisplayName("PROJECT_ROLE按固定角色与历史成员角色别名匹配")
    void projectRoleResolverCanonicalizesLegacyMemberRole() {
        initTableInfo();
        ApproverResolver resolver = resolver();
        when(pmProjectMemberMapper.selectList(any())).thenReturn(List.of(member(1L)));
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(1L)));

        assertEquals(List.of(1L), resolver.resolve(
                "{\"type\":\"PROJECT_ROLE\",\"roleCode\":\"PROJECT_MANAGER\"}",
                7L, 10001L, new WorkflowSecurityPolicy(false, 1, true, false)));
    }

    @Test
    @DisplayName("PROJECT_ROLE拒绝未知或公司级角色")
    void projectRoleResolverRejectsNonProjectRole() {
        ApproverResolver resolver = resolver();

        for (String roleCode : List.of("UNKNOWN_LEGACY_ROLE", SystemRoleContract.COMPANY_OWNER,
                SystemRoleContract.COMPANY_FINANCE)) {
            BusinessException exception = assertThrows(BusinessException.class, () -> resolver.resolve(
                    "{\"type\":\"PROJECT_ROLE\",\"roleCode\":\"" + roleCode + "\"}",
                    7L, 10001L, new WorkflowSecurityPolicy(false, 1, true, false)));
            assertEquals("PROJECT_ROLE_INVALID", exception.getCode());
        }
    }

    @Test
    @DisplayName("七类历史项目角色均映射到项目范围系统角色")
    void legacyProjectRolesMapToCanonicalProjectRoles() {
        assertEquals(SystemRoleContract.PROJECT_MANAGER, SystemRoleContract.canonicalRoleCode("PM"));
        assertEquals(SystemRoleContract.PROJECT_ACCOUNTANT, SystemRoleContract.canonicalRoleCode("CM"));
        assertEquals(SystemRoleContract.PROJECT_ACCOUNTANT, SystemRoleContract.canonicalRoleCode("CSTM"));
        assertEquals(SystemRoleContract.PROJECT_ACCOUNTANT, SystemRoleContract.canonicalRoleCode("FIN"));
        assertEquals(SystemRoleContract.PROCUREMENT_LEAD, SystemRoleContract.canonicalRoleCode("MAT"));
        assertEquals(SystemRoleContract.CONSTRUCTION_LEAD, SystemRoleContract.canonicalRoleCode("SUBC"));
        assertEquals(SystemRoleContract.EMPLOYEE, SystemRoleContract.canonicalRoleCode("OTH"));
    }

    private ApproverResolver resolver() {
        return new ApproverResolver(sysUserMapper, sysUserRoleMapper, sysRoleMapper,
                orgPositionMapper, pmProjectMemberMapper, new ObjectMapper(), jdbcTemplate);
    }

    private void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), PmProjectMemberMapper.class.getName()),
                PmProjectMember.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), SysRoleMapper.class.getName()), SysRole.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), SysUserRoleMapper.class.getName()), SysUserRole.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), SysUserMapper.class.getName()), SysUser.class);
    }

    private SysRole role(Long id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setTenantId(7L);
        role.setRoleCode(code);
        role.setStatus("ENABLE");
        return role;
    }

    private SysUserRole userRole(Long userId, Long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setTenantId(7L);
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(7L);
        user.setStatus("ENABLE");
        return user;
    }

    private PmProjectMember member(Long userId) {
        PmProjectMember member = new PmProjectMember();
        member.setTenantId(7L);
        member.setProjectId(10001L);
        member.setUserId(userId);
        member.setRoleCode("PM");
        member.setStatus("ACTIVE");
        return member;
    }
}
