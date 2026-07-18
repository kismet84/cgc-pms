package com.cgcpms.auth.service;

import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysMenuMapper;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — dev-login 显式账号")
class AuthServiceDevLoginTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysRoleMenuMapper sysRoleMenuMapper;
    @Mock private SysMenuMapper sysMenuMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(sysUserMapper, sysUserRoleMapper, sysRoleMapper,
                sysRoleMenuMapper, sysMenuMapper, passwordEncoder, jwtUtils);
    }

    @Test
    @DisplayName("已存在的 bootstrap 管理员可用于 dev-login")
    void existingBootstrapAccountCanLogin() {
        String username = "admin";
        SysRole role = role(1L, 0L);
        SysUser user = user(910001L, 0L, username);
        SysUserRole userRole = new SysUserRole();
        userRole.setTenantId(0L);
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(sysUserMapper.selectById(user.getId())).thenReturn(user);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role));
        when(sysMenuMapper.selectList(any())).thenReturn(List.of(menu("system:user:query")));
        when(jwtUtils.generateToken(eq(user.getId()), eq(username), eq(0L), any(), any()))
                .thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(anyLong())).thenReturn("refresh-token");

        LoginResponse response = authService.loginByUsernameEnsuringDevAccount(username, username);

        assertEquals("910001", response.getUserInfo().getUserId());
        assertTrue(response.getUserInfo().getRoles().contains("SUPER_ADMIN"));
        verify(sysUserMapper, never()).insert(any(SysUser.class));
        verify(sysUserRoleMapper, never()).insert(any(SysUserRole.class));
    }

    @Test
    @DisplayName("默认或显式用户名不存在时不得静默创建超级管理员")
    void missingAccountFailsClosedWithoutCreation() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> authService.loginByUsernameEnsuringDevAccount("admin", "admin"));

        verify(sysUserMapper, never()).insert(any(SysUser.class));
        verify(sysUserRoleMapper, never()).insert(any(SysUserRole.class));
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
