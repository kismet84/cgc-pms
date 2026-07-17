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
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ENABLED_STATUS = "ENABLE";
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String DEV_LOGIN_DEMO_PASSWORD = "DevOnly#Demo123";
    private static final String DEV_LOGIN_DEMO_REAL_NAME = "开发演示超级管理员";
    private static final String DEV_LOGIN_DEMO_PHONE = "13800019999";
    private static final String DEV_LOGIN_DEMO_REMARK = "仅供 dev/local 免密登录自动补齐的演示超级管理员账号";

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
        if (!ENABLED_STATUS.equals(user.getStatus())) {
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

    public LoginResponse loginByUsername(String username) {
        SysUser user = findUserByUsername(username);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return loginById(user.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginByUsernameEnsuringDevAccount(String username, String defaultUsername) {
        String effectiveUsername = normalizeUsername(username);
        SysUser user = isDefaultDevLoginUsername(effectiveUsername, defaultUsername)
                ? ensureDevSuperAdminAccount(effectiveUsername)
                : findUserByUsername(effectiveUsername);
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

    private SysUser findUserByTenantIdAndUsername(Long tenantId, String username) {
        String normalizedUsername = normalizeUsername(username);
        if (tenantId == null || !StringUtils.hasText(normalizedUsername)) {
            return null;
        }
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, normalizedUsername));
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return username.trim();
    }

    private boolean isDefaultDevLoginUsername(String username, String defaultUsername) {
        String normalizedDefaultUsername = normalizeUsername(defaultUsername);
        return StringUtils.hasText(username) && username.equals(normalizedDefaultUsername);
    }

    private SysUser ensureDevSuperAdminAccount(String username) {
        SysRole superAdminRole = getDefaultSuperAdminRole();
        SysUser user = findUserByTenantIdAndUsername(superAdminRole.getTenantId(), username);
        if (user == null) {
            user = insertDevSuperAdminUser(username, superAdminRole.getTenantId());
        } else if (patchDevSuperAdminUser(user, superAdminRole.getTenantId(), username)) {
            sysUserMapper.updateById(user);
        }
        ensureUserRole(user.getId(), superAdminRole.getId());
        return user;
    }

    private SysRole getDefaultSuperAdminRole() {
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, SUPER_ADMIN_ROLE_CODE)
                .eq(SysRole::getStatus, ENABLED_STATUS)
                .orderByAsc(SysRole::getTenantId)
                .orderByAsc(SysRole::getId));
        if (roles.isEmpty()) {
            throw new BusinessException("DEV_LOGIN_ROLE_NOT_FOUND", "dev login 默认超级管理员角色不存在");
        }
        return roles.get(0);
    }

    private SysUser insertDevSuperAdminUser(String username, Long tenantId) {
        SysUser user = buildDevSuperAdminUser(username, tenantId);
        try {
            sysUserMapper.insert(user);
            return user;
        } catch (DuplicateKeyException ex) {
            SysUser existing = findUserByTenantIdAndUsername(tenantId, username);
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    private SysUser buildDevSuperAdminUser(String username, Long tenantId) {
        SysUser user = new SysUser();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEV_LOGIN_DEMO_PASSWORD));
        user.setRealName(DEV_LOGIN_DEMO_REAL_NAME);
        user.setPhone(DEV_LOGIN_DEMO_PHONE);
        user.setEmail(username + "@cgc-pms.dev.local");
        user.setStatus(ENABLED_STATUS);
        user.setIsAdmin(1);
        user.setRemark(DEV_LOGIN_DEMO_REMARK);
        return user;
    }

    private boolean patchDevSuperAdminUser(SysUser user, Long tenantId, String username) {
        boolean changed = false;
        if (!tenantId.equals(user.getTenantId())) {
            user.setTenantId(tenantId);
            changed = true;
        }
        if (!ENABLED_STATUS.equals(user.getStatus())) {
            user.setStatus(ENABLED_STATUS);
            changed = true;
        }
        if (!Integer.valueOf(1).equals(user.getIsAdmin())) {
            user.setIsAdmin(1);
            changed = true;
        }
        if (!StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(DEV_LOGIN_DEMO_PASSWORD));
            changed = true;
        }
        if (!StringUtils.hasText(user.getRealName())) {
            user.setRealName(DEV_LOGIN_DEMO_REAL_NAME);
            changed = true;
        }
        if (!StringUtils.hasText(user.getPhone())) {
            user.setPhone(DEV_LOGIN_DEMO_PHONE);
            changed = true;
        }
        if (!StringUtils.hasText(user.getEmail())) {
            user.setEmail(username + "@cgc-pms.dev.local");
            changed = true;
        }
        if (!StringUtils.hasText(user.getRemark())) {
            user.setRemark(DEV_LOGIN_DEMO_REMARK);
            changed = true;
        }
        return changed;
    }

    private void ensureUserRole(Long userId, Long roleId) {
        SysUser user = sysUserMapper.selectById(userId);
        SysRole role = sysRoleMapper.selectById(roleId);
        if (user == null || role == null || !user.getTenantId().equals(role.getTenantId())) {
            throw new BusinessException("AUTH_ROLE_TENANT_MISMATCH", "用户与角色不属于同一租户");
        }
        Long count = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, user.getTenantId())
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, roleId));
        if (count != null && count > 0) {
            return;
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setTenantId(user.getTenantId());
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        sysUserRoleMapper.insert(userRole);
    }

    private Long requireUserTenant(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return user.getTenantId();
    }
}
