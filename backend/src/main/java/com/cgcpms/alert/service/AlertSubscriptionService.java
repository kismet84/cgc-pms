package com.cgcpms.alert.service;

import com.cgcpms.alert.auth.AlertAccessScopeResolver;
import com.cgcpms.alert.notification.AlertNotificationChannel;
import com.cgcpms.alert.notification.AlertNotificationChannelProperties;
import com.cgcpms.system.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AlertSubscriptionService {

    private static final String PREFERENCE_KEY = "alertSubscription";
    private static final List<String> SEVERITY_ORDER = List.of("LOW", "MEDIUM", "HIGH");

    private final PreferenceService preferenceService;
    private final AlertAccessScopeResolver accessScopeResolver;
    private final AlertNotificationChannelProperties channelProperties;

    public Map<String, Object> getCurrentUserSubscription(Long tenantId, Long userId, Collection<String> roleCodes) {
        Subscription defaults = buildDefaults(roleCodes);
        Subscription rawOverrides = readRawOverrides(userId, tenantId);
        Subscription effective = applyOverrides(defaults, rawOverrides);
        return buildResponse(defaults, rawOverrides, effective);
    }

    public Map<String, Object> updateCurrentUserSubscription(Long tenantId, Long userId, Collection<String> roleCodes,
                                                             Map<String, Object> request) {
        Subscription defaults = buildDefaults(roleCodes);
        Subscription requested = fromMap(request);
        Subscription sanitized = sanitizeOverrides(defaults, requested);
        preferenceService.savePreferences(userId, tenantId, Map.of(PREFERENCE_KEY, sanitized.toMap()));
        Subscription effective = applyOverrides(defaults, sanitized);
        return buildResponse(defaults, sanitized, effective);
    }

    public Map<String, Object> getEffectiveSubscription(Long tenantId, Long userId, Collection<String> roleCodes) {
        Subscription defaults = buildDefaults(roleCodes);
        Subscription rawOverrides = readRawOverrides(userId, tenantId);
        return applyOverrides(defaults, rawOverrides).toMap();
    }

    private Map<String, Object> buildResponse(Subscription defaults, Subscription rawOverrides, Subscription effective) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("defaultSubscription", defaults.toMap());
        response.put("rawUserOverrides", rawOverrides == null ? null : rawOverrides.toMap());
        response.put("effectiveSubscription", effective.toMap());
        response.put("availableOptions", Map.of(
                "domains", defaults.domains,
                "channels", defaults.channels,
                "minSeverityOptions", availableSeverities(defaults.minSeverity)
        ));
        return response;
    }

    private Subscription buildDefaults(Collection<String> roleCodes) {
        Set<String> domains = accessScopeResolver.allowedSubscriptionDomainsForRoles(roleCodes);
        List<String> channels = availableChannels();
        boolean enabled = !domains.isEmpty() && !channels.isEmpty();
        return new Subscription(
                enabled,
                channels,
                new ArrayList<>(domains),
                enabled ? "LOW" : "HIGH",
                enabled
        );
    }

    @SuppressWarnings("unchecked")
    private Subscription readRawOverrides(Long userId, Long tenantId) {
        Map<String, Object> preferences = preferenceService.getPreferences(userId, tenantId);
        Object raw = preferences.get(PREFERENCE_KEY);
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return null;
        }
        return fromMap((Map<String, Object>) rawMap);
    }

    private Subscription sanitizeOverrides(Subscription defaults, Subscription requested) {
        if (requested == null) {
            return new Subscription(null, null, null, null, null);
        }
        Boolean enabled = requested.enabled == null ? null : defaults.enabled && requested.enabled;
        List<String> channels = requested.channels == null
                ? null
                : requested.channels.stream()
                .filter(defaults.channels::contains)
                .distinct()
                .toList();
        List<String> domains = requested.domains == null
                ? null
                : requested.domains.stream()
                .filter(defaults.domains::contains)
                .distinct()
                .toList();
        String minSeverity = requested.minSeverity == null
                ? null
                : stricterSeverity(defaults.minSeverity, requested.minSeverity);
        Boolean notifyOnStatusChanged = requested.notifyOnStatusChanged == null
                ? null
                : defaults.notifyOnStatusChanged && requested.notifyOnStatusChanged;
        return new Subscription(enabled, channels, domains, minSeverity, notifyOnStatusChanged);
    }

    private Subscription applyOverrides(Subscription defaults, Subscription rawOverrides) {
        Subscription overrides = sanitizeOverrides(defaults, rawOverrides);
        boolean enabled = overrides.enabled != null ? overrides.enabled : defaults.enabled;
        List<String> channels = overrides.channels != null ? overrides.channels : defaults.channels;
        List<String> domains = overrides.domains != null ? overrides.domains : defaults.domains;
        String minSeverity = overrides.minSeverity != null ? overrides.minSeverity : defaults.minSeverity;
        boolean notifyOnStatusChanged = overrides.notifyOnStatusChanged != null
                ? overrides.notifyOnStatusChanged
                : defaults.notifyOnStatusChanged;
        return new Subscription(enabled, channels, domains, minSeverity, notifyOnStatusChanged);
    }

    private Subscription fromMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        return new Subscription(
                readBoolean(source.get("enabled")),
                readStringList(source.get("channels")),
                readStringList(source.get("domains")),
                readSeverity(source.get("minSeverity")),
                readBoolean(source.get("notifyOnStatusChanged"))
        );
    }

    private Boolean readBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        return collection.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(item -> item.toUpperCase(Locale.ROOT))
                .toList();
    }

    private String readSeverity(Object value) {
        if (!StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        String severity = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return SEVERITY_ORDER.contains(severity) ? severity : null;
    }

    private String stricterSeverity(String left, String right) {
        int leftIndex = SEVERITY_ORDER.indexOf(left);
        int rightIndex = SEVERITY_ORDER.indexOf(right);
        if (leftIndex < 0) {
            return right;
        }
        if (rightIndex < 0) {
            return left;
        }
        return SEVERITY_ORDER.get(Math.max(leftIndex, rightIndex));
    }

    private List<String> availableSeverities(String minSeverity) {
        int minIndex = Math.max(0, SEVERITY_ORDER.indexOf(minSeverity));
        return SEVERITY_ORDER.subList(minIndex, SEVERITY_ORDER.size());
    }

    private List<String> availableChannels() {
        return Arrays.stream(AlertNotificationChannel.values())
                .filter(channelProperties::isConfigured)
                .map(AlertNotificationChannel::name)
                .toList();
    }

    private record Subscription(Boolean enabled, List<String> channels, List<String> domains,
                                String minSeverity, Boolean notifyOnStatusChanged) {

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("enabled", enabled);
            map.put("channels", channels != null ? channels : List.of());
            map.put("domains", domains != null ? domains : List.of());
            map.put("minSeverity", minSeverity);
            map.put("notifyOnStatusChanged", notifyOnStatusChanged);
            return map;
        }
    }
}
