package com.cgcpms.auth.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ENABLED_STATUS = "ENABLE";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public LoginResponse login(LoginRequest request) {
        SysUser user = findUserByUsername(request.getTenantId(), request.getUsername());
        if (user == null) {
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }
        if (!ENABLED_STATUS.equals(user.getStatus())) {
            throw new BusinessException("AUTH_DISABLED", "账号已被禁用");
        }

        log.info("User login: {}", request.getUsername());
        return issueLogin(user);
    }

    public LoginResponse loginById(Long tenantId, Long userId) {
        SysUser user = sysUserMapper.selectByTenantAndId(tenantId, userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        if (!ENABLED_STATUS.equals(user.getStatus())) {
            throw new BusinessException("AUTH_DISABLED", "账号已被禁用");
        }
        return issueLogin(user);
    }

    public LoginResponse loginByUsernameEnsuringDevAccount(String username, String defaultUsername) {
        String effectiveUsername = normalizeUsername(username);
        if (!StringUtils.hasText(effectiveUsername)) {
            effectiveUsername = normalizeUsername(defaultUsername);
        }
        SysUser user = findUserByUsername(0L, effectiveUsername);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return loginById(0L, user.getId());
    }

    public UserInfo getUserInfo(Long tenantId, Long userId) {
        SysUser user = sysUserMapper.selectByTenantAndId(tenantId, userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return buildUserInfo(user, getRoleCodes(tenantId, userId), getPermissionCodes(tenantId, userId));
    }

    /** Fail closed for tokens issued before password reset, disabled users, or tenant changes. */
    public boolean isCurrentCredential(Claims claims) {
        if (claims == null) return false;
        try {
            Long userId = claims.get(JwtUtils.CLAIM_USER_ID, Long.class);
            Long tenantId = claims.get(JwtUtils.CLAIM_TENANT_ID, Long.class);
            if (userId == null || tenantId == null) return false;
            SysUser user = sysUserMapper.selectCredentialByTenantAndId(tenantId, userId);
            return matchesCurrentCredential(claims, tenantId, user);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Load credential and authorization state once per access-token request and fail closed. */
    public boolean isCurrentAuthentication(Claims claims) {
        if (claims == null) return false;
        try {
            Long userId = claims.get(JwtUtils.CLAIM_USER_ID, Long.class);
            Long tenantId = claims.get(JwtUtils.CLAIM_TENANT_ID, Long.class);
            if (userId == null || tenantId == null) return false;

            SysUser user = sysUserMapper.selectCredentialByTenantAndId(tenantId, userId);
            Set<String> roles = Set.copyOf(getRoleCodes(tenantId, userId));
            Set<String> permissions = Set.copyOf(getPermissionCodes(tenantId, userId));
            return matchesCurrentCredential(claims, tenantId, user)
                    && matchesCurrentAuthorization(claims, roles, permissions);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Fail closed when roles or permissions changed after an access token was issued. */
    public boolean isCurrentAuthorization(Claims claims) {
        if (claims == null) return false;
        try {
            Long userId = claims.get(JwtUtils.CLAIM_USER_ID, Long.class);
            Long tenantId = claims.get(JwtUtils.CLAIM_TENANT_ID, Long.class);
            if (userId == null || tenantId == null) return false;
            SysUser user = sysUserMapper.selectCredentialByTenantAndId(tenantId, userId);
            if (user == null || !tenantId.equals(user.getTenantId()) || !ENABLED_STATUS.equals(user.getStatus())) {
                return false;
            }
            Set<String> roles = Set.copyOf(getRoleCodes(tenantId, userId));
            Set<String> permissions = Set.copyOf(getPermissionCodes(tenantId, userId));
            return matchesCurrentAuthorization(claims, roles, permissions);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean matchesCurrentCredential(Claims claims, Long tenantId, SysUser user) {
        String version = claims.get(JwtUtils.CLAIM_CREDENTIAL_VERSION, String.class);
        return version != null && !version.isBlank()
                && user != null
                && tenantId.equals(user.getTenantId())
                && ENABLED_STATUS.equals(user.getStatus())
                && version.equals(jwtUtils.credentialVersion(user.getPassword()));
    }

    private boolean matchesCurrentAuthorization(Claims claims, Set<String> roles, Set<String> permissions) {
        return roles.equals(Set.copyOf(roleClaim(claims)))
                && permissions.equals(Set.copyOf(
                JwtUtils.decodePermissionClaim(claims.get(JwtUtils.CLAIM_PERMISSIONS))));
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
        return getRoleCodes(requireCurrentTenant(), userId);
    }

    public List<String> getRoleCodes(Long tenantId, Long userId) {
        return sysUserMapper.selectEnabledRoleCodesByTenantAndUserId(tenantId, userId);
    }

    /**
     * 根据 userId 查询权限编码列表。
     * 供 {@link com.cgcpms.system.service.ProfileService} 等内部调用。
     */
    public List<String> getPermissionCodes(Long userId) {
        return getPermissionCodes(requireCurrentTenant(), userId);
    }

    public List<String> getPermissionCodes(Long tenantId, Long userId) {
        return sysUserMapper.selectEnabledPermissionCodesByTenantAndUserId(tenantId, userId);
    }

    private SysUser findUserByUsername(Long tenantId, String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return null;
        }
        return sysUserMapper.selectByTenantAndUsername(tenantId, normalizedUsername);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return username.trim();
    }

    private LoginResponse issueLogin(SysUser user) {
        List<String> roleCodes = getRoleCodes(user.getTenantId(), user.getId());
        List<String> permCodes = getPermissionCodes(user.getTenantId(), user.getId());
        String credentialVersion = jwtUtils.credentialVersion(user.getPassword());
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getTenantId(),
                roleCodes, permCodes, credentialVersion);
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getTenantId(), credentialVersion);
        return new LoginResponse(token, refreshToken, buildUserInfo(user, roleCodes, permCodes));
    }

    private UserInfo buildUserInfo(SysUser user, List<String> roleCodes, List<String> permCodes) {
        return UserInfo.builder()
                .tenantId(String.valueOf(user.getTenantId()))
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

    private Long requireCurrentTenant() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException("AUTH_TOKEN_INVALID", "缺少租户上下文");
        }
        return tenantId;
    }
}
