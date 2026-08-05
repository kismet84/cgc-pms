package com.cgcpms.auth.service;

import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
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
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(sysUserMapper, passwordEncoder, jwtUtils);
    }

    @Test
    @DisplayName("已存在的 bootstrap 管理员可用于 dev-login")
    void existingBootstrapAccountCanLogin() {
        String username = "admin";
        SysUser user = user(910001L, 0L, username);

        when(sysUserMapper.selectByTenantAndUsername(0L, username)).thenReturn(user);
        when(sysUserMapper.selectByTenantAndId(0L, user.getId())).thenReturn(user);
        when(sysUserMapper.selectEnabledRoleCodesByTenantAndUserId(0L, user.getId()))
                .thenReturn(List.of("SUPER_ADMIN"));
        when(sysUserMapper.selectEnabledPermissionCodesByTenantAndUserId(0L, user.getId()))
                .thenReturn(List.of("system:user:query"));
        when(jwtUtils.generateToken(eq(user.getId()), eq(username), eq(0L), any(), any(), any()))
                .thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(anyLong(), anyLong(), any())).thenReturn("refresh-token");

        LoginResponse response = authService.loginByUsernameEnsuringDevAccount(username, username);

        assertEquals("910001", response.getUserInfo().getUserId());
        assertTrue(response.getUserInfo().getRoles().contains("SUPER_ADMIN"));
        verify(sysUserMapper, never()).insert(org.mockito.ArgumentMatchers.any(SysUser.class));
    }

    @Test
    @DisplayName("默认或显式用户名不存在时不得静默创建超级管理员")
    void missingAccountFailsClosedWithoutCreation() {
        when(sysUserMapper.selectByTenantAndUsername(0L, "admin")).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> authService.loginByUsernameEnsuringDevAccount("admin", "admin"));

        verify(sysUserMapper, never()).insert(org.mockito.ArgumentMatchers.any(SysUser.class));
    }

    private SysUser user(Long id, Long tenantId, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setStatus("ENABLE");
        return user;
    }

}
