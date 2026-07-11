package com.cgcpms.alert.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.service.CashJournalAlertRecipientResolver;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AlertAccessScopeResolver {

    public static final Set<String> ALL_ALERT_DOMAINS = Set.of("COST", "CONTRACT", "PAYMENT", "VARIATION", "PURCHASE", "FINANCE");

    private static final Map<String, Set<String>> ROLE_DOMAINS = Map.of(
            "PROJECT_MANAGER", Set.of("COST", "CONTRACT", "PAYMENT", "VARIATION", "PURCHASE"),
            "COMMERCIAL_MANAGER", Set.of("CONTRACT", "PAYMENT", "VARIATION"),
            "PURCHASE_MANAGER", Set.of("PURCHASE"),
            "FINANCE", Set.of("PAYMENT")
    );

    private final PmProjectMapper projectMapper;
    private final PmProjectMemberMapper projectMemberMapper;
    private final CashJournalAlertRecipientResolver cashJournalRecipientResolver;

    public boolean isAdmin() {
        return UserContext.hasAnyRole("ADMIN", "SUPER_ADMIN");
    }

    public Set<String> allowedDomains() {
        Long tenantId = UserContext.getCurrentTenantId();
        Long userId = UserContext.getCurrentUserId();
        if (tenantId == null || userId == null) {
            return Collections.emptySet();
        }
        Set<String> domains = new LinkedHashSet<>(allowedDomainsForRoles(UserContext.getCurrentRoles()));
        if (!isAdmin() && cashJournalRecipientResolver.isEligible(tenantId, userId)) {
            domains.add("FINANCE");
        }
        return domains;
    }

    public Set<String> allowedDomainsForRoles(Collection<String> roles) {
        if (hasAdminRole(roles)) {
            return ALL_ALERT_DOMAINS;
        }
        Set<String> domains = new LinkedHashSet<>();
        for (String role : roles != null ? roles : Collections.<String>emptyList()) {
            domains.addAll(ROLE_DOMAINS.getOrDefault(normalizeRoleCode(role), Collections.emptySet()));
        }
        return domains;
    }

    public Set<Long> accessibleProjectIds(Long tenantId) {
        if (tenantId == null || !Objects.equals(tenantId, UserContext.getCurrentTenantId())) {
            throw accessDenied();
        }
        if (isAdmin()) {
            return Collections.emptySet();
        }
        Long userId = UserContext.getCurrentUserId();
        if (tenantId == null || userId == null) {
            return Collections.emptySet();
        }

        Set<Long> projectIds = projectMemberMapper.selectList(new LambdaQueryWrapper<PmProjectMember>()
                        .eq(PmProjectMember::getTenantId, tenantId)
                        .eq(PmProjectMember::getUserId, userId)
                        .eq(PmProjectMember::getStatus, "ACTIVE"))
                .stream()
                .map(PmProjectMember::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        projectMapper.selectList(new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE")
                        .eq(PmProject::getCreatedBy, userId))
                .stream()
                .map(PmProject::getId)
                .filter(Objects::nonNull)
                .forEach(projectIds::add);

        if (projectIds.isEmpty()) {
            return projectIds;
        }
        return projectMapper.selectList(new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE")
                        .in(PmProject::getId, projectIds))
                .stream()
                .map(PmProject::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void assertProjectAccess(Long tenantId, Long projectId) {
        if (tenantId == null || !Objects.equals(tenantId, UserContext.getCurrentTenantId())) {
            throw accessDenied();
        }
        if (isAdmin() || projectId == null || projectId == 0L) {
            return;
        }
        if (!accessibleProjectIds(tenantId).contains(projectId)) {
            throw accessDenied();
        }
    }

    public void assertAlertAccess(Long tenantId, AlertLog alert) {
        if (tenantId == null || !Objects.equals(tenantId, UserContext.getCurrentTenantId())
                || alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
            throw accessDenied();
        }
        if (isAdmin()) {
            return;
        }
        assertProjectAccess(tenantId, alert.getProjectId());
        String domain = alertDomain(alert);
        if (!allowedDomains().contains(domain)) {
            throw accessDenied();
        }
    }

    public String alertDomain(AlertLog alert) {
        if (alert == null) {
            return "OTHER";
        }
        return StringUtils.hasText(alert.getAlertDomain()) ? alert.getAlertDomain() : alertDomain(alert.getRuleType());
    }

    public static String alertDomain(String ruleType) {
        return switch (ruleType) {
            case "DYNAMIC_COST_EXCEEDS_TARGET", "MATERIAL_EXCEEDS_BUDGET",
                    "SUBCONTRACT_EXCEEDS_CONTRACT" -> "COST";
            case "CONTRACT_OVERDUE", "CONTRACT_EXPIRING", "WARRANTY_EARLY_RELEASE" -> "CONTRACT";
            case "PAYMENT_EXCEEDS_RATIO" -> "PAYMENT";
            case "VARIATION_UNCONFIRMED" -> "VARIATION";
            case "PURCHASE_DELIVERY_OVERDUE" -> "PURCHASE";
            case "CASH_JOURNAL_ARCHIVE_OVERDUE" -> "FINANCE";
            default -> "OTHER";
        };
    }

    private BusinessException accessDenied() {
        return new BusinessException("ALERT_ACCESS_DENIED", "无权访问该预警");
    }

    private boolean hasAdminRole(Collection<String> roles) {
        if (roles == null) {
            return false;
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role));
    }

    private String normalizeRoleCode(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        return "PM".equalsIgnoreCase(role.trim()) ? "PROJECT_MANAGER" : role.trim().toUpperCase(Locale.ROOT);
    }
}
