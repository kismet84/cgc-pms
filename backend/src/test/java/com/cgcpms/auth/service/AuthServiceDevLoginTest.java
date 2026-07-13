package com.cgcpms.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysMenuMapper;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — dev-login 默认账号")
class AuthServiceDevLoginTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysRoleMenuMapper sysRoleMenuMapper;
    @Mock
    private SysMenuMapper sysMenuMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtils jwtUtils;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                sysUserMapper,
                sysUserRoleMapper,
                sysRoleMapper,
                sysRoleMenuMapper,
                sysMenuMapper,
                passwordEncoder,
                jwtUtils);
    }

    @Test
    @DisplayName("默认演示账号按超管租户和用户名查询，避开全局 username selectOne")
    void defaultDevAccountUsesTenantScopedUsernameLookup() {
        String username = "demo_dev_super_admin";
        SysRole superAdminRole = role(1L, 0L);
        SysUser defaultUser = user(910001L, 0L, username);
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(superAdminRole.getId());

        when(sysRoleMapper.selectList(any())).thenReturn(List.of(superAdminRole));
        when(sysUserMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<SysUser> wrapper = invocation.getArgument(0);
            if (wrapper.getExpression().getNormal().size() < 4) {
                throw new TooManyResultsException("global username lookup matched multiple rows");
            }
            return defaultUser;
        });
        when(sysUserRoleMapper.selectCount(any())).thenReturn(1L);
        when(sysUserMapper.selectById(defaultUser.getId())).thenReturn(defaultUser);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectByIds(any())).thenReturn(List.of(superAdminRole));
        when(sysMenuMapper.selectList(any())).thenReturn(List.of(
                menu("inventory:transaction:add"),
                menu("requisition:add"),
                menu("requisition:submit"),
                menu("workflow:approve")));
        when(jwtUtils.generateToken(eq(defaultUser.getId()), eq(username), eq(defaultUser.getTenantId()), any(), any()))
                .thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(anyLong())).thenReturn("refresh-token");

        LoginResponse response = authService.loginByUsernameEnsuringDevAccount(username, username);

        assertEquals("910001", response.getUserInfo().getUserId());
        assertEquals(username, response.getUserInfo().getUsername());
        assertTrue(response.getUserInfo().getRoles().contains("SUPER_ADMIN"));
        assertTrue(response.getUserInfo().getPermissions().contains("inventory:transaction:add"));
        assertTrue(response.getUserInfo().getPermissions().contains("requisition:add"));
        assertTrue(response.getUserInfo().getPermissions().contains("requisition:submit"));
        assertTrue(response.getUserInfo().getPermissions().contains("workflow:approve"));
    }

    private SysRole role(Long id, Long tenantId) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setTenantId(tenantId);
        role.setRoleCode("SUPER_ADMIN");
        role.setStatus("ENABLE");
        return role;
    }

    private SysUser user(Long id, Long tenantId, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setStatus("ENABLE");
        return user;
    }

    private SysMenu menu(String perms) {
        SysMenu menu = new SysMenu();
        menu.setPerms(perms);
        return menu;
    }
}
