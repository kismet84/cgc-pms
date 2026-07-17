package com.cgcpms.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.alert.auth.AlertAccessScopeResolver;
import com.cgcpms.alert.dto.AlertProcessingReportVO;
import com.cgcpms.alert.dto.AlertRuleEffectVO;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.entity.AlertLifecycleEvent;
import com.cgcpms.alert.entity.AlertNotificationSendRecord;
import com.cgcpms.alert.entity.AlertRuleConfig;
import com.cgcpms.alert.mapper.AlertLifecycleEventMapper;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.mapper.AlertNotificationSendRecordMapper;
import com.cgcpms.alert.notification.AlertNotificationDispatcher;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.mapper.SysNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
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
    private final AlertEscalationRecipientResolver escalationRecipientResolver;
    private final AlertRuleEvaluator ruleEvaluator;
    private final AlertLifecycleService lifecycleService;
    private final AlertLifecycleEventMapper lifecycleEventMapper;
    private final AlertNotificationSendRecordMapper notificationSendRecordMapper;
    private final SysNotificationMapper notificationMapper;
    private final com.cgcpms.cashbook.service.CashJournalAlertService cashJournalAlertService;

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
            Set<Long> cashJournalTenants = new LinkedHashSet<>();

            log.info("Found {} active projects for alert evaluation", activeProjects.size());
            for (PmProject project : activeProjects) {
                cashJournalTenants.add(project.getTenantId());
                try {
                    ((AlertEvaluationService) AopContext.currentProxy()).evaluateProject(project.getTenantId(), project.getId());
                } catch (Exception e) {
                    log.error("Failed to evaluate alerts for project {}", project.getId(), e);
                }
            }
            cashJournalTenants.addAll(cashJournalAlertService.pendingTenantIds());
            cashJournalTenants.addAll(alertLogMapper.selectPendingEscalationTenantIds());
            for (Long tenantId : cashJournalTenants) {
                try {
                    cashJournalAlertService.evaluateOverdue(tenantId);
                    ((AlertEvaluationService) AopContext.currentProxy()).escalateOverdueAlerts(tenantId);
                } catch (Exception e) {
                    log.error("Failed to evaluate or escalate alerts for tenant {}", tenantId, e);
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
        boolean skipProjects = false;
        if (!accessScopeResolver.isAdmin()) {
            Set<Long> accessibleProjectIds = accessScopeResolver.accessibleProjectIds(tenantId);
            if (accessibleProjectIds.isEmpty()) {
                skipProjects = true;
            } else {
                wrapper.in(PmProject::getId, accessibleProjectIds);
            }
        }
        List<PmProject> activeProjects = skipProjects ? List.of() : projectMapper.selectList(wrapper);
        int totalAlerts = cashJournalAlertService.evaluateOverdue(tenantId);
        for (PmProject project : activeProjects) {
            totalAlerts += evaluateProject(tenantId, project.getId());
        }
        int escalated = escalateOverdueAlerts(tenantId);
        log.info("Manual alert escalation done: {} alert(s) escalated for tenantId={}", escalated, tenantId);
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
            alerts.forEach(lifecycleService::initialize);
            com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch(alerts, 50);
            for (AlertLog alert : alerts) {
                lifecycleService.recordCreated(alert);
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

    @Transactional(rollbackFor = Exception.class)
    public int escalateOverdueAlerts(Long tenantId) {
        LocalDateTime now = LocalDateTime.now();
        List<AlertLog> alerts = alertLogMapper.selectList(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, tenantId)
                .eq(AlertLog::getProcessStatus, "OPEN")
                .and(wrapper -> wrapper
                        .and(response -> response.isNull(AlertLog::getAcknowledgedAt)
                                .le(AlertLog::getResponseDueAt, now)
                                .lt(AlertLog::getEscalationLevel, 1))
                        .or(resolution -> resolution.le(AlertLog::getResolutionDueAt, now)
                                .lt(AlertLog::getEscalationLevel, 2)))
                .orderByAsc(AlertLog::getResolutionDueAt)
                .orderByAsc(AlertLog::getResponseDueAt));
        int escalated = 0;
        for (AlertLog alert : alerts) {
            int currentLevel = Optional.ofNullable(alert.getEscalationLevel()).orElse(0);
            int targetLevel = alert.getResolutionDueAt() != null && !alert.getResolutionDueAt().isAfter(now)
                    ? 2 : 1;
            if (targetLevel <= currentLevel) continue;
            String reason = targetLevel >= 2
                    ? "预警超过处置时限仍未完成处理"
                    : "预警超过响应时限仍未接单";
            Set<Long> recipients = escalationRecipientResolver.resolve(alert, targetLevel);
            alert.setEscalationLevel(targetLevel);
            alert.setLastEscalatedAt(now);
            alert.setUpdatedBy(0L);
            if (alertLogMapper.updateById(alert) == 0) {
                log.info("Alert escalation skipped after concurrent update: alertId={}", alert.getId());
                continue;
            }
            lifecycleService.record(alert, "ESCALATED_L" + targetLevel, "OPEN", "OPEN", 0L,
                    reason + "；通知对象=" + recipients);
            for (Long userId : recipients) {
                notificationDispatcher.dispatchEscalated(tenantId, userId, alert, targetLevel, reason);
            }
            escalated++;
        }
        return escalated;
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
        LambdaQueryWrapper<AlertLog> wrapper = buildAlertQuery(tenantId, projectId, ruleType, alertDomain,
                severity, isRead, triggeredStart, triggeredEnd, processStatus);
        if (wrapper == null) {
            return emptyPage(pageNum, pageSize);
        }
        wrapper.orderByDesc(AlertLog::getTriggeredAt);
        return alertLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public AlertProcessingReportVO processingReport(Long tenantId, Long projectId,
                                                    String ruleType, String alertDomain, String severity, Integer isRead,
                                                    LocalDateTime triggeredStart, LocalDateTime triggeredEnd,
                                                    String processStatus) {
        LambdaQueryWrapper<AlertLog> wrapper = buildAlertQuery(tenantId, projectId, ruleType, alertDomain,
                severity, isRead, triggeredStart, triggeredEnd, processStatus);
        if (wrapper == null) {
            return new AlertProcessingReportVO();
        }
        List<AlertLog> alerts = alertLogMapper.selectList(wrapper);

        AlertProcessingReportVO report = new AlertProcessingReportVO();
        report.setTotalCount(alerts.size());
        report.setUnreadCount(alerts.stream().filter(a -> Objects.equals(a.getIsRead(), 0)).count());
        report.setReadCount(alerts.stream().filter(a -> Objects.equals(a.getIsRead(), 1)).count());
        LocalDateTime now = LocalDateTime.now();
        report.setUnacknowledgedCount(alerts.stream()
                .filter(a -> "OPEN".equals(a.getProcessStatus()) && a.getAcknowledgedAt() == null).count());
        report.setOverdueOpenCount(alerts.stream()
                .filter(a -> "OPEN".equals(a.getProcessStatus()) && a.getResponseDueAt() != null
                        && a.getResponseDueAt().isBefore(now)).count());
        report.setEscalatedCount(alerts.stream()
                .filter(a -> a.getEscalationLevel() != null && a.getEscalationLevel() > 0).count());
        if (!alerts.isEmpty()) {
            report.setFailedNotificationCount(notificationSendRecordMapper.selectCount(
                    new LambdaQueryWrapper<AlertNotificationSendRecord>()
                            .eq(AlertNotificationSendRecord::getTenantId, tenantId)
                            .in(AlertNotificationSendRecord::getAlertId,
                                    alerts.stream().map(AlertLog::getId).toList())
                            .eq(AlertNotificationSendRecord::getSendStatus, "FAILED")));
        }
        report.setSeverityCounts(groupBy(alerts, AlertLog::getSeverity));
        report.setProcessStatusCounts(groupBy(alerts, AlertLog::getProcessStatus));
        return report;
    }

    public List<AlertRuleEffectVO> ruleEffectReport(Long tenantId, Long projectId,
                                                    String ruleType, String alertDomain,
                                                    LocalDateTime triggeredStart,
                                                    LocalDateTime triggeredEnd) {
        LambdaQueryWrapper<AlertLog> wrapper = buildAlertQuery(tenantId, projectId, ruleType, alertDomain,
                null, null, triggeredStart, triggeredEnd, null);
        if (wrapper == null) return List.of();
        List<AlertLog> alerts = alertLogMapper.selectList(wrapper);
        Map<Long, Long> failedByAlert = alerts.isEmpty() ? Map.of()
                : notificationSendRecordMapper.selectList(new LambdaQueryWrapper<AlertNotificationSendRecord>()
                        .eq(AlertNotificationSendRecord::getTenantId, tenantId)
                        .in(AlertNotificationSendRecord::getAlertId, alerts.stream().map(AlertLog::getId).toList())
                        .eq(AlertNotificationSendRecord::getSendStatus, "FAILED"))
                .stream().collect(Collectors.groupingBy(AlertNotificationSendRecord::getAlertId, Collectors.counting()));

        return alerts.stream()
                .collect(Collectors.groupingBy(alert -> StringUtils.hasText(alert.getRuleType())
                        ? alert.getRuleType() : "UNKNOWN", TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> buildRuleEffect(entry.getKey(), entry.getValue(), failedByAlert))
                .toList();
    }

    private AlertRuleEffectVO buildRuleEffect(String ruleType, List<AlertLog> alerts,
                                              Map<Long, Long> failedByAlert) {
        AlertRuleEffectVO effect = new AlertRuleEffectVO();
        effect.setRuleType(ruleType);
        effect.setGeneratedCount(alerts.size());
        effect.setAcknowledgedCount(alerts.stream().filter(a -> a.getAcknowledgedAt() != null).count());
        effect.setWithinResponseSlaCount(alerts.stream()
                .filter(a -> a.getAcknowledgedAt() != null && a.getResponseDueAt() != null
                        && !a.getAcknowledgedAt().isAfter(a.getResponseDueAt())).count());
        effect.setEscalatedCount(alerts.stream()
                .filter(a -> a.getEscalationLevel() != null && a.getEscalationLevel() > 0).count());
        effect.setProcessedCount(alerts.stream()
                .filter(a -> List.of("PROCESSED", "ARCHIVED").contains(a.getProcessStatus())).count());
        effect.setArchivedCount(alerts.stream().filter(a -> "ARCHIVED".equals(a.getProcessStatus())).count());
        effect.setInvalidCount(alerts.stream().filter(a -> "INVALID".equals(a.getProcessStatus())).count());
        effect.setFailedNotificationCount(alerts.stream()
                .mapToLong(a -> failedByAlert.getOrDefault(a.getId(), 0L)).sum());
        OptionalDouble averageMinutes = alerts.stream()
                .filter(a -> a.getTriggeredAt() != null && a.getAcknowledgedAt() != null)
                .mapToLong(a -> Math.max(0, Duration.between(a.getTriggeredAt(), a.getAcknowledgedAt()).toMinutes()))
                .average();
        effect.setAverageResponseMinutes(averageMinutes.isPresent() ? Math.round(averageMinutes.getAsDouble()) : null);
        return effect;
    }

    private LambdaQueryWrapper<AlertLog> buildAlertQuery(Long tenantId, Long projectId,
                                                         String ruleType, String alertDomain, String severity,
                                                         Integer isRead, LocalDateTime triggeredStart,
                                                         LocalDateTime triggeredEnd, String processStatus) {
        Set<String> allowedDomains = accessScopeResolver.allowedDomains();
        accessScopeResolver.assertProjectAccess(tenantId, projectId);
        if (!accessScopeResolver.isAdmin()) {
            if (StringUtils.hasText(alertDomain) && !allowedDomains.contains(alertDomain)) {
                return null;
            }
            if (allowedDomains.isEmpty()) {
                return null;
            }
        }

        LambdaQueryWrapper<AlertLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertLog::getTenantId, tenantId);
        if (projectId != null) {
            wrapper.eq(AlertLog::getProjectId, projectId);
        } else if (!accessScopeResolver.isAdmin()) {
            Set<Long> accessibleProjectIds = accessScopeResolver.accessibleProjectIds(tenantId);
            boolean financeAllowed = allowedDomains.contains("FINANCE");
            if (accessibleProjectIds.isEmpty() && !financeAllowed) {
                return null;
            }
            if (accessibleProjectIds.isEmpty()) {
                wrapper.eq(AlertLog::getProjectId, 0L).eq(AlertLog::getAlertDomain, "FINANCE");
            } else if (financeAllowed) {
                wrapper.and(w -> w.in(AlertLog::getProjectId, accessibleProjectIds)
                        .or(finance -> finance.eq(AlertLog::getProjectId, 0L)
                                .eq(AlertLog::getAlertDomain, "FINANCE")));
            } else {
                wrapper.in(AlertLog::getProjectId, accessibleProjectIds);
            }
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
        return wrapper;
    }

    private IPage<AlertLog> emptyPage(long pageNum, long pageSize) {
        return new Page<>(pageNum, pageSize);
    }

    private Map<String, Long> groupBy(List<AlertLog> alerts, java.util.function.Function<AlertLog, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        alerts.stream()
                .map(classifier)
                .filter(StringUtils::hasText)
                .sorted()
                .forEach(value -> counts.merge(value, 1L, Long::sum));
        return counts;
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

    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(Long tenantId, Long alertId) {
        AlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
            return false;
        }
        accessScopeResolver.assertAlertAccess(tenantId, alert);
        if (Objects.equals(alert.getIsRead(), 1)) return true;
        Long userId = UserContext.getCurrentUserId();
        alert.setIsRead(1);
        alert.setReadBy(userId);
        alert.setReadAt(LocalDateTime.now());
        if (alertLogMapper.updateById(alert) == 0) {
            throw new BusinessException("ALERT_CONCURRENT_UPDATE", "预警已被其他用户更新，请刷新后重试");
        }
        lifecycleService.record(alert, "READ", alert.getProcessStatus(), alert.getProcessStatus(), userId, "已阅读");
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean acknowledge(Long tenantId, Long alertId, String remark) {
        AlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) return false;
        accessScopeResolver.assertAlertAccess(tenantId, alert);
        if (!"OPEN".equals(alert.getProcessStatus())) {
            throw new BusinessException("ALERT_ACK_STATE_INVALID", "只有待处理预警可以接单");
        }
        Long userId = UserContext.getCurrentUserId();
        if (alert.getAcknowledgedBy() != null) {
            if (Objects.equals(alert.getAcknowledgedBy(), userId)) return true;
            throw new BusinessException("ALERT_ALREADY_ACKNOWLEDGED", "该预警已由其他责任人接单");
        }
        LocalDateTime now = LocalDateTime.now();
        alert.setAcknowledgedBy(userId);
        alert.setAcknowledgedAt(now);
        if (!Objects.equals(alert.getIsRead(), 1)) {
            alert.setIsRead(1);
            alert.setReadBy(userId);
            alert.setReadAt(now);
        }
        alert.setUpdatedBy(userId);
        if (alertLogMapper.updateById(alert) == 0) {
            throw new BusinessException("ALERT_CONCURRENT_UPDATE", "预警已被其他用户接单，请刷新后重试");
        }
        lifecycleService.record(alert, "ACKNOWLEDGED", "OPEN", "OPEN", userId,
                StringUtils.hasText(remark) ? remark.trim() : "接单处理");
        return true;
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
        List<AlertLog> alerts = alertLogMapper.selectByIds(ids);
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
                markRead(tenantId, id);
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

    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long tenantId, Long alertId, String processStatus, String statusRemark) {
        if (!List.of("PROCESSED", "ARCHIVED", "INVALID").contains(processStatus)) {
            throw new BusinessException("ALERT_STATUS_INVALID", "预警状态不合法");
        }
        if (!StringUtils.hasText(statusRemark)) {
            throw new BusinessException("ALERT_STATUS_REMARK_REQUIRED", "状态变更必须填写处理说明");
        }
        AlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
            return false;
        }
        accessScopeResolver.assertAlertAccess(tenantId, alert);
        String current = StringUtils.hasText(alert.getProcessStatus()) ? alert.getProcessStatus() : "OPEN";
        if (current.equals(processStatus)) return true;
        if (List.of("ARCHIVED", "INVALID").contains(current)) {
            throw new BusinessException("ALERT_TERMINAL_IMMUTABLE", "已归档或已失效预警不允许再次修改");
        }
        Long userId = UserContext.getCurrentUserId();
        if ("PROCESSED".equals(processStatus)) {
            if (!"OPEN".equals(current)) {
                throw new BusinessException("ALERT_STATUS_TRANSITION_INVALID", "当前状态不能标记为已处理");
            }
            if (alert.getAcknowledgedBy() == null || !Objects.equals(alert.getAcknowledgedBy(), userId)) {
                throw new BusinessException("ALERT_HANDLER_REQUIRED", "必须由当前接单责任人完成处理");
            }
        } else if ("ARCHIVED".equals(processStatus) && !"PROCESSED".equals(current)) {
            throw new BusinessException("ALERT_STATUS_TRANSITION_INVALID", "只有已处理预警可以归档");
        } else if ("INVALID".equals(processStatus) && !"OPEN".equals(current)) {
            throw new BusinessException("ALERT_STATUS_TRANSITION_INVALID", "只有待处理预警可以标记失效");
        }
        alert.setProcessStatus(processStatus);
        alert.setStatusRemark(statusRemark.trim());
        alert.setUpdatedBy(userId);
        LocalDateTime now = LocalDateTime.now();
        if ("PROCESSED".equals(processStatus)) {
            alert.setProcessedAt(now);
            alert.setProcessedBy(userId);
        }
        if ("ARCHIVED".equals(processStatus) || "INVALID".equals(processStatus)) {
            alert.setArchivedAt(now);
            alert.setArchivedBy(userId);
        }
        boolean updated = alertLogMapper.updateById(alert) > 0;
        if (!updated) {
            throw new BusinessException("ALERT_CONCURRENT_UPDATE", "预警已被其他用户更新，请刷新后重试");
        }
        lifecycleService.record(alert, "STATUS_CHANGED", current, processStatus, userId, statusRemark.trim());
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

    public Map<String, Object> trace(Long tenantId, Long alertId) {
        AlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
            throw new BusinessException("ALERT_NOT_FOUND", "预警不存在或不属于当前租户");
        }
        accessScopeResolver.assertAlertAccess(tenantId, alert);
        List<AlertLifecycleEvent> events = lifecycleEventMapper.selectList(
                new LambdaQueryWrapper<AlertLifecycleEvent>()
                        .eq(AlertLifecycleEvent::getTenantId, tenantId)
                        .eq(AlertLifecycleEvent::getAlertId, alertId)
                        .orderByAsc(AlertLifecycleEvent::getOccurredAt)
                        .orderByAsc(AlertLifecycleEvent::getId));
        List<AlertNotificationSendRecord> sends = notificationSendRecordMapper.selectList(
                new LambdaQueryWrapper<AlertNotificationSendRecord>()
                        .eq(AlertNotificationSendRecord::getTenantId, tenantId)
                        .eq(AlertNotificationSendRecord::getAlertId, alertId)
                        .orderByAsc(AlertNotificationSendRecord::getRequestedAt));
        List<SysNotification> notifications = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getTenantId, tenantId)
                        .in(SysNotification::getBizType, List.of("ALERT", "ALERT_STATUS", "ALERT_ESCALATION"))
                        .eq(SysNotification::getBizId, alertId)
                        .orderByAsc(SysNotification::getCreatedTime));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("projectId", alert.getProjectId());
        source.put("contractId", alert.getContractId());
        source.put("sourceType", alert.getSourceType());
        source.put("sourceId", alert.getSourceId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alert", alert);
        result.put("source", source);
        result.put("lifecycleEvents", events);
        result.put("notificationSendRecords", sends);
        result.put("notifications", notifications);
        return result;
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
