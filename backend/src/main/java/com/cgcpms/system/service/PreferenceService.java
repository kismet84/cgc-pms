package com.cgcpms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.system.entity.SysUserPreference;
import com.cgcpms.system.mapper.SysUserPreferenceMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PreferenceService {

    private final SysUserPreferenceMapper preferenceMapper;
    private final ObjectMapper objectMapper;

    private static final Map<String, Object> DEFAULTS = Map.of(
            "sidebarCollapsed", false,
            "notificationEnabled", true,
            "theme", "light",
            "tableDensity", "middle"
    );

    /**
     * Get preferences for a user. Returns defaults if no record exists.
     */
    public Map<String, Object> getPreferences(Long userId, Long tenantId) {
        SysUserPreference record = queryByUser(userId, tenantId);
        if (record == null || record.getPreferences() == null) {
            return new HashMap<>(DEFAULTS);
        }
        Map<String, Object> parsed = parsePreferencesJson(record.getPreferences(), userId);
        return parsed != null ? parsed : new HashMap<>(DEFAULTS);
    }

    /**
     * Upsert preferences. Merges new values onto existing (or defaults).
     */
    public Map<String, Object> savePreferences(Long userId, Long tenantId, Map<String, Object> newPrefs) {
        SysUserPreference existing = queryByUser(userId, tenantId);

        Map<String, Object> merged = new HashMap<>(DEFAULTS);
        if (existing != null && existing.getPreferences() != null) {
            Map<String, Object> current = parsePreferencesJson(existing.getPreferences(), userId);
            if (current != null) {
                merged.putAll(current);
            }
        }
        merged.putAll(newPrefs);

        SysUserPreference entity;
        if (existing != null) {
            entity = existing;
        } else {
            entity = new SysUserPreference();
            entity.setUserId(userId);
            entity.setTenantId(tenantId);
        }

        try {
            entity.setPreferences(objectMapper.writeValueAsString(merged));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize preferences", e);
        }

        preferenceMapper.insertOrUpdate(entity);
        return merged;
    }

    /**
     * Parse a JSON preferences string to a Map. Returns null on parse failure.
     */
    private Map<String, Object> parsePreferencesJson(String json, Long userId) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse preferences JSON for userId={}, json={}", userId, json, e);
            return null;
        }
    }

    private SysUserPreference queryByUser(Long userId, Long tenantId) {
        return preferenceMapper.selectOne(
                new LambdaQueryWrapper<SysUserPreference>()
                        .eq(SysUserPreference::getTenantId, tenantId)
                        .eq(SysUserPreference::getUserId, userId));
    }
}
