package com.cgcpms.system.controller;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.system.dto.ChangePasswordRequest;
import com.cgcpms.system.dto.UpdateProfileRequest;
import com.cgcpms.system.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Self-service profile endpoints for the currently authenticated user.
 * <p>
 * Uses {@code isAuthenticated()} — any logged-in user can manage their own profile.
 * No {@code system:user:edit} permission required.
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Update the current user's own profile (realName, phone, email, avatar).
     * Restricted fields (username, roles, status, isAdmin, tenantId, orgId) are
     * ignored server-side, regardless of what the client sends.
     */
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserInfo> updateProfile(@RequestBody UpdateProfileRequest request) {
        Long userId = UserContext.getCurrentUserId();
        UserInfo updated = profileService.updateProfile(userId, request);
        return ApiResponse.success(updated);
    }

    /**
     * Change the current user's password.
     * Requires old password verification; encodes new password with BCrypt.
     */
    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = UserContext.getCurrentUserId();
        profileService.changePassword(userId, request);
        return ApiResponse.success();
    }
}
