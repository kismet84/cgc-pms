package com.cgcpms.system.service;

import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.auth.service.AuthService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.dto.ChangePasswordRequest;
import com.cgcpms.system.dto.UpdateProfileRequest;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Self-service profile management for the currently authenticated user.
 * Only allows updating the user's own profile and password — never other users'.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    /**
     * Update the current user's own profile fields.
     * Whitelist: only {@code realName}, {@code phone}, {@code email}, {@code avatar} are updated.
     * All other fields (username, roles, status, isAdmin, tenantId, orgId) are ignored.
     */
    @Transactional
    public UserInfo updateProfile(Long userId, UpdateProfileRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }

        // Build whitelist: only update allowed fields
        boolean changed = false;
        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
            changed = true;
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
            changed = true;
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
            changed = true;
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
            changed = true;
        }

        if (changed) {
            sysUserMapper.updateById(user);
            log.info("用户资料更新完成");
        }

        return buildUserInfo(user);
    }

    /**
     * Change the current user's password.
     * Verifies old password first, then encodes and persists the new one.
     * Uses direct field update — NOT the general {@code SysUserService.update()} method.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }

        // Verify old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("PASSWORD_OLD_INVALID", "旧密码不正确");
        }

        // Encode and update ONLY the password field
        String encoded = passwordEncoder.encode(request.getNewPassword());
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(encoded);
        sysUserMapper.updateById(update);
        log.info("用户密码更新完成");
    }

    /**
     * Build a UserInfo DTO from a SysUser entity.
     * NEVER exposes the password hash.
     */
    private UserInfo buildUserInfo(SysUser user) {
        List<String> roleCodes = authService.getRoleCodes(user.getId());
        List<String> permCodes = authService.getPermissionCodes(user.getId());
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
}
