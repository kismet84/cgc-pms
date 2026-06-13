package com.cgcpms.system.controller;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.system.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User preference CRUD endpoints for the currently authenticated user.
 * <p>
 * Uses {@code isAuthenticated()} — any logged-in user can manage their own preferences.
 * No admin permission required.
 */
@RestController
@RequestMapping("/profile/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    /**
     * Get the current user's preferences. Returns defaults if none saved.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getPreferences() {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        Map<String, Object> preferences = preferenceService.getPreferences(userId, tenantId);
        return ApiResponse.success(preferences);
    }

    /**
     * Upsert the current user's preferences. Merges new values onto existing.
     */
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> savePreferences(@RequestBody Map<String, Object> newPrefs) {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        Map<String, Object> updated = preferenceService.savePreferences(userId, tenantId, newPrefs);
        return ApiResponse.success(updated);
    }
}
