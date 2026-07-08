package com.cgcpms.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.alert.auth.AlertAccessScopeResolver;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.entity.AlertRuleConfig;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.notification.AlertNotificationDispatcher;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Alert evaluation engine — orchestrates rule evaluation, scheduling,
 * query, and CRUD operations for {@code alert_log}.
 *
 * <p>Rule evaluation logic is delegated to {@link AlertRuleEvaluator}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private final AlertLogMapper alertLogMapper;
    private final PmProjectMapper projectMapper;
    private final PmProjectMemberMapper projectMemberMapper;
    private final AlertNotificationDispatcher notificationDispatcher;
    private final AlertAccessScopeResolver accessScopeResolver;
    private final AlertSubscriptionService alertSubscriptionService;
    private final AlertRuleEvaluator ruleEvaluator;

    private final AtomicBoolean scheduledEvaluateRunning = new AtomicBoolean(false);

    // ──────────────────────────────────────────────
    // Scheduled entry point
    // ──────────────────────────────────────────────

    @Scheduled(cron = "0 */30 * * * ?")
    public void scheduledEvaluate() {
        if (!scheduledEvaluateRunning.compareAndSet(false, true)) {
            log.warn("Previous scheduled alert evaluation still running, skipping this trigger");
            return;
        }
        log.info("Starting scheduled alert evaluation...");
        try {
            LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PmProject::getStatus, "ACTIVE");
            List<PmProject> activeProjects = projectMapper.selectList(wrapper);

            log.info("Found {} active projects for alert evaluation", activeProjects.size());
            for (PmProject project : activeProjects) {
                try {
                    ((AlertEvaluationService) AopContext.currentProxy()).evaluateProject(project.getTenantId(), project.getId());
                } catch (Exception e) {
                    log.error("Failed to evaluate alerts for project {}", project.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Scheduled alert evaluation failed", e);
        } finally {
            scheduledEvaluateRunning.set(false);
        }
        log.info("Scheduled alert evaluation completed");
    }

    // ──────────────────────────────────────────────
    // Public API — manual trigger
    // ──────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public int batchEvaluate(Long tenantId) {
        log.info("Manual alert evaluation triggered for tenantId={}", tenantId);
        LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getTenantId, tenantId)
                .eq(PmProject::getStatus, "ACTIVE");
        if (!accessScopeResolver.isAdmin()) {
            Set<Long> accessibleProjectIds = accessScopeResolver.accessibleProjectIds(tenantId);
            if (accessibleProjectIds.isEmpty()) {
                return 0;
            }
            wrapper.in(PmProject::getId, accessibleProjectIds);
        }
        List<PmProject> activeProjects = projectMapper.selectList(wrapper);
        int totalAlerts = 0;
        for (PmProject project : activeProjects) {
            totalAlerts += evaluateProject(tenantId, project.getId());
        }
        log.info("Manual alert evaluation done: {} alerts generated for tenantId={}", totalAlerts, tenantId);
        return totalAlerts;
    }

    // ──────────────────────────────────────────────
    // Core evaluation — delegates to AlertRuleEvaluator
    // ──────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public int evaluateProject(Long tenantId, Long projectId) {
        Map<String, AlertRuleConfig> ruleConfigs = ruleEvaluator.loadRuleConfigs(tenantId);
        List<AlertLog> alerts = new ArrayList<>();

        alerts.addAll(ruleEvaluator.evaluateDynamicCostExceedsTarget(tenantId, projectId, ruleConfigs));
        alerts.addAll(ruleEvaluator.evaluateMaterialExceedsBudget(tenantId, projectId, ruleConfigs));
        alerts.addAll(ruleEvaluator.evaluateSubcontractExceedsContract(tenantId, projectId, ruleConfigs));
        alerts.addAll(ruleEvaluator.evaluateContractOverdue(tenantId, projectId, ruleConfigs));
        alerts.addAll(ruleEvaluator.evaluatePaymentExceedsRatio(tenantId, projectId, ruleConfigs));
        alerts.addAll(ruleEvaluator.evaluateWarrantyEarlyRelease(tenantId, projectId, ruleConfigs));
        alerts.addAll(ruleEvaluator.evaluateContractExpiring(tenantId, projectId, ruleConfigs));
        alerts.addAll(ruleEvaluator.evaluateVariationUnconfirmed(tenantId, projectId, ruleConfigs));
        alerts.addAll(ruleEvaluator.evaluatePurchaseDeliveryOverdue(tenantId, projectId, ruleConfigs));

        if (!alerts.isEmpty()) {
            com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch(alerts, 50);
            for (AlertLog alert : alerts) {
                try {
                    createAlertNotification(tenantId, projectId, alert);
                } catch (Exception e) {
                    log.warn("Failed to create notification for alert id={}, ruleType={}: {}",
                            alert.getId(), alert.getRuleType(), e.getMessage());
                }
            }
            log.info("Project {}: {} alert(s) generated", projectId, alerts.size());
        }
        return alerts.size();
    }

    // ──────────────────────────────────────────────
    // Query helpers for Controller
    // ──────────────────────────────────────────────

    public List<AlertLog> list(Long tenantId, Long projectId, String severity, Integer isRead) {
        return page(tenantId, 1, 1000, projectId, null, null, severity, isRead, null, null, null).getRecords();
    }

    public IPage<AlertLog> page(Long tenantId, long pageNum, long pageSize, Long projectId,
                                String ruleType, String alertDomain, String severity, Integer isRead,
                                LocalDateTime triggeredStart, LocalDateTime triggeredEnd, String processStatus) {
        Set<String> allowedDomains = accessScopeResolver.allowedDomains();
        if (!accessScopeResolver.isAdmin()) {
            if (projectId != null) {
                accessScopeResolver.assertProjectAccess(tenantId, projectId);
            }
            if (StringUtils.hasText(alertDomain) && !allowedDomains.contains(alertDomain)) {
                return emptyPage(pageNum, pageSize);
            }
            if (allowedDomains.isEmpty()) {
                return emptyPage(pageNum, pageSize);
            }
        }

        LambdaQueryWrapper<AlertLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertLog::getTenantId, tenantId);
        if (projectId != null) {
            wrapper.eq(AlertLog::getProjectId, projectId);
        } else if (!accessScopeResolver.isAdmin()) {
            Set<Long> accessibleProjectIds = accessScopeResolver.accessibleProjectIds(tenantId);
            if (accessibleProjectIds.isEmpty()) {
                return emptyPage(pageNum, pageSize);
            }
            wrapper.in(AlertLog::getProjectId, accessibleProjectIds);
        }
        if (StringUtils.hasText(ruleType)) {
            wrapper.eq(AlertLog::getRuleType, ruleType);
        }
        if (StringUtils.hasText(alertDomain)) {
            Set<String> domainRuleTypes = ruleTypesForDomain(alertDomain);
            if (domainRuleTypes.isEmpty()) {
                wrapper.eq(AlertLog::getAlertDomain, alertDomain);
            } else {
                wrapper.and(w -> w.eq(AlertLog::getAlertDomain, alertDomain)
                        .or(legacy -> legacy.in(AlertLog::getRuleType, domainRuleTypes)
                                .and(blank -> blank.isNull(AlertLog::getAlertDomain)
                                        .or()
                                        .eq(AlertLog::getAlertDomain, ""))));
            }
        } else if (!accessScopeResolver.isAdmin()) {
            applyDomainScope(wrapper, allowedDomains);
        }
        if (StringUtils.hasText(severity)) {
            wrapper.eq(AlertLog::getSeverity, severity);
        }
        if (isRead != null) {
            wrapper.eq(AlertLog::getIsRead, isRead);
        }
        if (StringUtils.hasText(processStatus)) {
            wrapper.eq(AlertLog::getProcessStatus, processStatus);
        }
        if (triggeredStart != null) {
            wrapper.ge(AlertLog::getTriggeredAt, triggeredStart);
        }
        if (triggeredEnd != null) {
            wrapper.le(AlertLog::getTriggeredAt, triggeredEnd);
        }
        wrapper.orderByDesc(AlertLog::getTriggeredAt);
        return alertLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private IPage<AlertLog> emptyPage(long pageNum, long pageSize) {
        return new Page<>(pageNum, pageSize);
    }

    private void applyDomainScope(LambdaQueryWrapper<AlertLog> wrapper, Set<String> allowedDomains) {
        Set<String> domainRuleTypes = ruleTypesForDomains(allowedDomains);
        wrapper.and(w -> w.in(AlertLog::getAlertDomain, allowedDomains)
                .or(legacy -> legacy.in(AlertLog::getRuleType, domainRuleTypes)
                        .and(blank -> blank.isNull(AlertLog::getAlertDomain)
                                .or()
                                .eq(AlertLog::getAlertDomain, ""))));
    }

    private Set<String> ruleTypesForDomain(String domain) {
        return AlertMessageTemplates.TEMPLATES.keySet().stream()
                .filter(ruleType -> domain.equals(AlertMessageTemplates.domain(ruleType)))
                .collect(Collectors.toSet());
    }

    private Set<String> ruleTypesForDomains(Set<String> domains) {
        return AlertMessageTemplates.TEMPLATES.keySet().stream()
                .filter(ruleType -> domains.contains(AlertMessageTemplates.domain(ruleType)))
                .collect(Collectors.toSet());
    }

    // ──────────────────────────────────────────────
    // CRUD operations
    // ──────────────────────────────────────────────

    public boolean markRead(Long tenantId, Long alertId) {
        AlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
            return false;
        }
        accessScopeResolver.assertAlertAccess(tenantId, alert);
        alert.setIsRead(1);
        return alertLogMapper.updateById(alert) > 0;
    }

    public Map<String, Object> batchMarkRead(Long tenantId, List<Long> alertIds) {
        List<Long> ids = alertIds == null ? List.of() : alertIds;
        if (ids.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("total", 0);
            result.put("success", 0);
            result.put("failed", 0);
            result.put("metrics", batchMetrics(0, 0, 0));
            result.put("successIds", List.of());
            result.put("failures", List.of());
            return result;
        }
        List<AlertLog> alerts = alertLogMapper.selectBatchIds(ids);
        Map<Long, AlertLog> alertMap = alerts.stream()
                .collect(Collectors.toMap(AlertLog::getId, a -> a));
        List<Long> successIds = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Long id : ids) {
            AlertLog alert = alertMap.get(id);
            if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
                failures.add(batchFailure(id, "预警不存在或不属于当前租户"));
                continue;
            }
            try {
                accessScopeResolver.assertAlertAccess(tenantId, alert);
                alert.setIsRead(1);
                alertLogMapper.updateById(alert);
                successIds.add(id);
            } catch (BusinessException e) {
                failures.add(batchFailure(id, e.getMessage()));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", ids.size());
        result.put("success", successIds.size());
        result.put("failed", failures.size());
        result.put("metrics", batchMetrics(ids.size(), successIds.size(), failures.size()));
        result.put("successIds", successIds);
        result.put("failures", failures);
        return result;
    }

    public boolean updateStatus(Long tenantId, Long alertId, String processStatus, String statusRemark) {
        if (!List.of("PROCESSED", "ARCHIVED", "INVALID").contains(processStatus)) {
            throw new BusinessException("ALERT_STATUS_INVALID", "预警状态不合法");
        }
        AlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
            return false;
        }
        accessScopeResolver.assertAlertAccess(tenantId, alert);
        alert.setProcessStatus(processStatus);
        alert.setStatusRemark(StringUtils.hasText(statusRemark) ? statusRemark.trim() : null);
        alert.setUpdatedBy(UserContext.getCurrentUserId());
        if ("PROCESSED".equals(processStatus)) {
            alert.setProcessedAt(LocalDateTime.now());
        }
        if ("ARCHIVED".equals(processStatus) || "INVALID".equals(processStatus)) {
            alert.setArchivedAt(LocalDateTime.now());
        }
        boolean updated = alertLogMapper.updateById(alert) > 0;
        if (updated) {
            try {
                dispatchStatusNotification(tenantId, alert, processStatus, statusRemark);
            } catch (Exception e) {
                log.warn("Failed to create alert status notification: alertId={}, status={}",
                        alertId, processStatus, e);
            }
        }
        return updated;
    }

    public Map<String, Object> batchUpdateStatus(Long tenantId, List<Long> alertIds,
                                                 String processStatus, String statusRemark) {
        return batch(alertIds, alertId -> updateStatus(tenantId, alertId, processStatus, statusRemark));
    }

    // ──────────────────────────────────────────────
    // Notification helpers
    // ──────────────────────────────────────────────

    private void createAlertNotification(Long tenantId, Long projectId, AlertLog alert) {
        String title = AlertMessageTemplates.title(alert.getRuleType());
        dispatchToSubscribedRecipients(tenantId, projectId, alert, subscription ->
                        Boolean.TRUE.equals(subscription.get("enabled")) && matchesSeverity(alert.getSeverity(), subscription),
                (userId, subscription) -> notificationDispatcher.dispatchAlertCreated(
                        tenantId, userId, alert, title, selectedChannels(subscription)));
    }

    private void dispatchStatusNotification(Long tenantId, AlertLog alert, String processStatus, String statusRemark) {
        dispatchToSubscribedRecipients(tenantId, alert.getProjectId(), alert, subscription ->
                        Boolean.TRUE.equals(subscription.get("enabled"))
                                && Boolean.TRUE.equals(subscription.get("notifyOnStatusChanged"))
                                && matchesSeverity(alert.getSeverity(), subscription),
                (userId, subscription) -> notificationDispatcher.dispatchStatusChanged(
                        tenantId, userId, alert, getStatusTitle(processStatus), statusRemark, selectedChannels(subscription)));
    }

    private void dispatchToSubscribedRecipients(Long tenantId, Long projectId, AlertLog alert,
                                                Predicate<Map<String, Object>> subscriptionFilter,
                                                SubscriptionDispatchAction action) {
        List<PmProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<PmProjectMember>()
                        .eq(PmProjectMember::getTenantId, tenantId)
                        .eq(PmProjectMember::getProjectId, projectId)
                        .eq(PmProjectMember::getStatus, "ACTIVE")
                        .orderByAsc(PmProjectMember::getId));
        if (members.isEmpty()) {
            log.debug("No active alert recipients for projectId={}, skipping notification", projectId);
            return;
        }

        Set<Long> seen = new LinkedHashSet<>();
        for (PmProjectMember m : members) {
            if (m.getUserId() == null || !seen.add(m.getUserId())) {
                continue;
            }
            Map<String, Object> subscription = alertSubscriptionService.getEffectiveSubscription(
                    tenantId, m.getUserId(), List.of(m.getRoleCode()));
            @SuppressWarnings("unchecked")
            List<String> domains = (List<String>) subscription.get("domains");
            if (domains == null || !domains.contains(accessScopeResolver.alertDomain(alert))) {
                continue;
            }
            if (!subscriptionFilter.test(subscription)) {
                continue;
            }
            action.dispatch(m.getUserId(), subscription);
        }
    }

    private String getStatusTitle(String processStatus) {
        return switch (processStatus) {
            case "PROCESSED" -> "预警已处理";
            case "ARCHIVED" -> "预警已归档";
            case "INVALID" -> "预警已失效";
            default -> "预警状态变更";
        };
    }

    private boolean matchesSeverity(String alertSeverity, Map<String, Object> subscription) {
        String minSeverity = String.valueOf(subscription.getOrDefault("minSeverity", "LOW"));
        return severityLevel(alertSeverity) >= severityLevel(minSeverity);
    }

    @SuppressWarnings("unchecked")
    private Set<String> selectedChannels(Map<String, Object> subscription) {
        Object channels = subscription.get("channels");
        if (!(channels instanceof Collection<?> collection)) {
            return Set.of();
        }
        return collection.stream().map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int severityLevel(String severity) {
        return switch (String.valueOf(severity).trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    // ── batch helpers ──

    private Map<String, Object> batch(List<Long> alertIds, AlertBatchAction action) {
        List<Long> ids = alertIds == null ? List.of() : alertIds;
        List<Long> successIds = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Long alertId : ids) {
            try {
                if (Boolean.TRUE.equals(action.apply(alertId))) {
                    successIds.add(alertId);
                } else {
                    failures.add(batchFailure(alertId, "预警不存在或不属于当前租户"));
                }
            } catch (BusinessException e) {
                failures.add(batchFailure(alertId, e.getMessage()));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", ids.size());
        result.put("success", successIds.size());
        result.put("failed", failures.size());
        result.put("metrics", batchMetrics(ids.size(), successIds.size(), failures.size()));
        result.put("successIds", successIds);
        result.put("failures", failures);
        return result;
    }

    private Map<String, Object> batchMetrics(int total, int success, int failed) {
        return Map.of(
                "total", total,
                "success", success,
                "failed", failed,
                "skipped", Math.max(0, total - success - failed));
    }

    private Map<String, Object> batchFailure(Long alertId, String reason) {
        Map<String, Object> failure = new HashMap<>();
        failure.put("alertId", alertId);
        failure.put("reason", reason);
        return failure;
    }

    @FunctionalInterface
    private interface SubscriptionDispatchAction {
        void dispatch(Long userId, Map<String, Object> subscription);
    }

    @FunctionalInterface
    private interface AlertBatchAction {
        boolean apply(Long alertId);
    }
}
