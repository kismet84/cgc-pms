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
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.jsonwebtoken.Claims;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ENABLED_STATUS = "ENABLE";

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public LoginResponse login(LoginRequest request) {
        SysUser user = findUserByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }
        if (!ENABLED_STATUS.equals(user.getStatus())) {
            throw new BusinessException("AUTH_DISABLED", "账号已被禁用");
        }

        List<String> roleCodes = getRoleCodes(user.getId());
        List<String> permCodes = getPermissionCodes(user.getId());

        log.info("User login: {}", request.getUsername());
        String credentialVersion = jwtUtils.credentialVersion(user.getPassword());
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getTenantId(), roleCodes, permCodes, credentialVersion);
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getTenantId(), credentialVersion);
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
        if (!ENABLED_STATUS.equals(user.getStatus())) {
            throw new BusinessException("AUTH_DISABLED", "账号已被禁用");
        }
        List<String> roleCodes = getRoleCodes(userId);
        List<String> permCodes = getPermissionCodes(userId);
        String credentialVersion = jwtUtils.credentialVersion(user.getPassword());
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getTenantId(), roleCodes, permCodes, credentialVersion);
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getTenantId(), credentialVersion);
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

    public LoginResponse loginByUsername(String username) {
        SysUser user = findUserByUsername(username);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return loginById(user.getId());
    }

    public LoginResponse loginByUsernameEnsuringDevAccount(String username, String defaultUsername) {
        String effectiveUsername = normalizeUsername(username);
        SysUser user = findUserByUsername(effectiveUsername);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return loginById(user.getId());
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

    /** Fail closed for tokens issued before password reset, disabled users, or tenant changes. */
    public boolean isCurrentCredential(Claims claims) {
        if (claims == null) return false;
        Long userId = claims.get(JwtUtils.CLAIM_USER_ID, Long.class);
        Long tenantId = claims.get(JwtUtils.CLAIM_TENANT_ID, Long.class);
        String version = claims.get(JwtUtils.CLAIM_CREDENTIAL_VERSION, String.class);
        if (userId == null || tenantId == null || version == null || version.isBlank()) return false;
        SysUser user = sysUserMapper.selectById(userId);
        return user != null && tenantId.equals(user.getTenantId())
                && ENABLED_STATUS.equals(user.getStatus())
                && version.equals(jwtUtils.credentialVersion(user.getPassword()));
    }

    /** Fail closed when roles or permissions changed after an access token was issued. */
    public boolean isCurrentAuthorization(Claims claims) {
        if (claims == null) return false;
        Long userId = claims.get(JwtUtils.CLAIM_USER_ID, Long.class);
        Long tenantId = claims.get(JwtUtils.CLAIM_TENANT_ID, Long.class);
        if (userId == null || tenantId == null) return false;
        try {
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null || !tenantId.equals(user.getTenantId()) || !ENABLED_STATUS.equals(user.getStatus())) {
                return false;
            }
            return Set.copyOf(getRoleCodes(userId)).equals(Set.copyOf(roleClaim(claims)))
                    && Set.copyOf(getPermissionCodes(userId)).equals(
                    Set.copyOf(JwtUtils.decodePermissionClaim(claims.get(JwtUtils.CLAIM_PERMISSIONS))));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<String> roleClaim(Claims claims) {
        Object value = claims.get(JwtUtils.CLAIM_ROLES);
        if (value instanceof List<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(role -> !role.isBlank())
                    .toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.split(",")).stream()
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .toList();
        }
        return List.of();
    }

    /**
     * 根据 userId 查询角色编码列表。
     * 供 {@link com.cgcpms.system.service.ProfileService} 等内部调用。
     */
    public List<String> getRoleCodes(Long userId) {
        Long tenantId = requireUserTenant(userId);
        var userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<com.cgcpms.system.entity.SysUserRole>()
                .eq(com.cgcpms.system.entity.SysUserRole::getTenantId, tenantId)
                .eq(com.cgcpms.system.entity.SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream()
                .map(com.cgcpms.system.entity.SysUserRole::getRoleId)
                .toList();
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .in(SysRole::getId, roleIds)).stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
    }

    /**
     * 根据 userId 查询权限编码列表。
     * 供 {@link com.cgcpms.system.service.ProfileService} 等内部调用。
     */
    public List<String> getPermissionCodes(Long userId) {
        Long tenantId = requireUserTenant(userId);
        var userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<com.cgcpms.system.entity.SysUserRole>()
                .eq(com.cgcpms.system.entity.SysUserRole::getTenantId, tenantId)
                .eq(com.cgcpms.system.entity.SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream()
                .map(com.cgcpms.system.entity.SysUserRole::getRoleId)
                .toList();
        List<String> roleCodes = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .in(SysRole::getId, roleIds)).stream()
                .map(SysRole::getRoleCode)
                .toList();
        if (roleCodes.contains("SUPER_ADMIN") || roleCodes.contains("ADMIN")) {
            return sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                            .eq(SysMenu::getTenantId, tenantId)
                            .isNotNull(SysMenu::getPerms)
                            .ne(SysMenu::getPerms, ""))
                    .stream()
                    .map(SysMenu::getPerms)
                    .distinct()
                    .collect(Collectors.toList());
        }

        var roleMenus = sysRoleMenuMapper.selectList(new LambdaQueryWrapper<com.cgcpms.system.entity.SysRoleMenu>()
                .eq(com.cgcpms.system.entity.SysRoleMenu::getTenantId, tenantId)
                .in(com.cgcpms.system.entity.SysRoleMenu::getRoleId, roleIds));
        if (roleMenus.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> menuIds = roleMenus.stream()
                .map(com.cgcpms.system.entity.SysRoleMenu::getMenuId)
                .distinct()
                .toList();

        return sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getTenantId, tenantId)
                        .in(SysMenu::getId, menuIds)).stream()
                .map(SysMenu::getPerms)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toList());
    }

    private SysUser findUserByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return null;
        }
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, normalizedUsername));
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return username.trim();
    }

    private Long requireUserTenant(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return user.getTenantId();
    }
}
