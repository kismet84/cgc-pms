package com.cgcpms.system.controller;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.system.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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

    private static final Set<String> UI_KEYS = Set.of(
            "sidebarCollapsed", "notificationEnabled", "theme", "tableDensity");
    private static final Set<String> THEMES = Set.of("light", "dark");
    private static final Set<String> TABLE_DENSITIES = Set.of("default", "middle", "small");

    private final PreferenceService preferenceService;

    /**
     * Get the current user's preferences. Returns defaults if none saved.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getPreferences() {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        return ApiResponse.success(uiPreferences(preferenceService.getPreferences(userId, tenantId)));
    }

    /**
     * Upsert the current user's preferences. Merges new values onto existing.
     */
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> savePreferences(@RequestBody Map<String, Object> newPrefs) {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        validateUiPreferences(newPrefs);
        return ApiResponse.success(uiPreferences(
                preferenceService.savePreferences(userId, tenantId, newPrefs)));
    }

    private static void validateUiPreferences(Map<String, Object> preferences) {
        if (preferences == null || preferences.keySet().stream().anyMatch(key -> !UI_KEYS.contains(key))) {
            throw invalidPreferences();
        }
        if (preferences.containsKey("sidebarCollapsed")
                && !(preferences.get("sidebarCollapsed") instanceof Boolean)
                || preferences.containsKey("notificationEnabled")
                && !(preferences.get("notificationEnabled") instanceof Boolean)
                || preferences.containsKey("theme")
                && !THEMES.contains(preferences.get("theme"))
                || preferences.containsKey("tableDensity")
                && !TABLE_DENSITIES.contains(preferences.get("tableDensity"))) {
            throw invalidPreferences();
        }
    }

    private static Map<String, Object> uiPreferences(Map<String, Object> preferences) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sidebarCollapsed",
                preferences.get("sidebarCollapsed") instanceof Boolean value ? value : false);
        result.put("notificationEnabled",
                preferences.get("notificationEnabled") instanceof Boolean value ? value : true);
        result.put("theme",
                THEMES.contains(preferences.get("theme")) ? preferences.get("theme") : "light");
        result.put("tableDensity",
                TABLE_DENSITIES.contains(preferences.get("tableDensity"))
                        ? preferences.get("tableDensity") : "middle");
        return result;
    }

    private static BusinessException invalidPreferences() {
        return new BusinessException("PROFILE_PREFERENCES_INVALID", "偏好设置包含不支持的字段或值");
    }
}
