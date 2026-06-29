package com.cgcpms.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }
        if (!"ENABLE".equals(user.getStatus())) {
            throw new BusinessException("AUTH_DISABLED", "账号已被禁用");
        }

        List<String> roleCodes = getRoleCodes(user.getId());
        List<String> permCodes = getPermissionCodes(user.getId());

        log.info("User login: {}", request.getUsername());
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getTenantId(), roleCodes, permCodes);
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        UserInfo userInfo = UserInfo.builder()
                .userId(String.valueOf(user.getId()))
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .roles(roleCodes)
                .permissions(permCodes)
                .roleName(roleCodes.isEmpty() ? null : roleCodes.get(0))
                .build();

        return new LoginResponse(token, refreshToken, userInfo);
    }

    public LoginResponse loginById(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        if (!"ENABLE".equals(user.getStatus())) {
            throw new BusinessException("AUTH_DISABLED", "账号已被禁用");
        }
        List<String> roleCodes = getRoleCodes(userId);
        List<String> permCodes = getPermissionCodes(userId);
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getTenantId(), roleCodes, permCodes);
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        UserInfo userInfo = UserInfo.builder()
                .userId(String.valueOf(user.getId()))
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .roles(roleCodes)
                .permissions(permCodes)
                .roleName(roleCodes.isEmpty() ? null : roleCodes.get(0))
                .build();
        return new LoginResponse(token, refreshToken, userInfo);
    }

    public UserInfo getUserInfo(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        List<String> roleCodes = getRoleCodes(userId);
        List<String> permCodes = getPermissionCodes(userId);
        return UserInfo.builder()
                .userId(String.valueOf(user.getId()))
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .roles(roleCodes)
                .permissions(permCodes)
                .roleName(roleCodes.isEmpty() ? null : roleCodes.get(0))
                .build();
    }

    /**
     * 根据 userId 查询角色编码列表。
     * 供 {@link com.cgcpms.system.service.ProfileService} 等内部调用。
     */
    public List<String> getRoleCodes(Long userId) {
        var userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<com.cgcpms.system.entity.SysUserRole>()
                .eq(com.cgcpms.system.entity.SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream()
                .map(com.cgcpms.system.entity.SysUserRole::getRoleId)
                .toList();
        return sysRoleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
    }

    /**
     * 根据 userId 查询权限编码列表。
     * 供 {@link com.cgcpms.system.service.ProfileService} 等内部调用。
     */
    public List<String> getPermissionCodes(Long userId) {
        var userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<com.cgcpms.system.entity.SysUserRole>()
                .eq(com.cgcpms.system.entity.SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream()
                .map(com.cgcpms.system.entity.SysUserRole::getRoleId)
                .toList();
        List<String> roleCodes = sysRoleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getRoleCode)
                .toList();
        if (roleCodes.contains("SUPER_ADMIN") || roleCodes.contains("ADMIN")) {
            return sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                            .isNotNull(SysMenu::getPerms)
                            .ne(SysMenu::getPerms, ""))
                    .stream()
                    .map(SysMenu::getPerms)
                    .distinct()
                    .collect(Collectors.toList());
        }

        var roleMenus = sysRoleMenuMapper.selectList(new LambdaQueryWrapper<com.cgcpms.system.entity.SysRoleMenu>()
                .in(com.cgcpms.system.entity.SysRoleMenu::getRoleId, roleIds));
        if (roleMenus.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> menuIds = roleMenus.stream()
                .map(com.cgcpms.system.entity.SysRoleMenu::getMenuId)
                .distinct()
                .toList();

        return sysMenuMapper.selectBatchIds(menuIds).stream()
                .map(SysMenu::getPerms)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toList());
    }
}
