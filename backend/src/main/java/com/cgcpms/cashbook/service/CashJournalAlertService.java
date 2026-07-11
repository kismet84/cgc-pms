package com.cgcpms.cashbook.service;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.notification.AlertNotificationDispatcher;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.file.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CashJournalAlertService {

    public static final String RULE_TYPE = "CASH_JOURNAL_ARCHIVE_OVERDUE";
    public static final String DOMAIN = "FINANCE";
    public static final String CATEGORY = "CASH_JOURNAL_CLOSURE";
    public static final String SOURCE_TYPE = "CASH_JOURNAL";
    public static final String TITLE = "资金流水归档逾期预警";

    private final CashJournalEntryMapper entryMapper;
    private final SysFileMapper fileMapper;
    private final AlertLogMapper alertLogMapper;
    private final CashJournalAlertRecipientResolver recipientResolver;
    private final AlertNotificationDispatcher notificationDispatcher;

    public Set<Long> pendingTenantIds() {
        return new LinkedHashSet<>(entryMapper.selectPendingArchiveTenantIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public int evaluateOverdue(Long tenantId) {
        if (tenantId == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        for (CashJournalEntry candidate : entryMapper.selectOverdueForTenant(tenantId, now)) {
            CashJournalEntry entry = entryMapper.selectByIdForUpdate(candidate.getId(), tenantId);
            if (entry == null || !Objects.equals(tenantId, entry.getTenantId())
                    || !List.of(CashbookConstants.Status.DRAFT, CashbookConstants.Status.PENDING_ARCHIVE)
                    .contains(entry.getStatus())
                    || entry.getClosureDueAt() == null || entry.getClosureDueAt().isAfter(now)) {
                continue;
            }
            String dedupKey = dedupKey(tenantId, entry.getId());
            if (!alertLogMapper.selectOpenByDedupKey(tenantId, dedupKey).isEmpty()) continue;

            AlertLog alert = new AlertLog();
            alert.setTenantId(tenantId);
            alert.setProjectId(0L);
            alert.setContractId(entry.getContractId());
            alert.setAlertDomain(DOMAIN);
            alert.setAlertCategory(CATEGORY);
            alert.setSourceType(SOURCE_TYPE);
            alert.setSourceId(entry.getId());
            alert.setDedupKey(dedupKey);
            alert.setRuleType(RULE_TYPE);
            alert.setSeverity("MEDIUM");
            alert.setMessage(message(entry));
            alert.setTriggeredAt(now);
            alert.setIsRead(0);
            alert.setProcessStatus("OPEN");
            alert.setDeletedFlag(0);
            alertLogMapper.insert(alert);
            for (Long userId : recipientResolver.resolve(tenantId)) {
                notificationDispatcher.dispatchAlertCreated(tenantId, userId, alert, TITLE);
            }
            created++;
        }
        return created;
    }

    @Transactional(rollbackFor = Exception.class)
    public void archiveForEntry(CashJournalEntry entry) {
        if (entry == null || entry.getId() == null || entry.getTenantId() == null) return;
        String dedupKey = dedupKey(entry.getTenantId(), entry.getId());
        LocalDateTime now = LocalDateTime.now();
        for (AlertLog alert : alertLogMapper.selectOpenByDedupKey(entry.getTenantId(), dedupKey)) {
            int updated = alertLogMapper.archiveCashJournalAlert(
                    alert.getId(), entry.getTenantId(), now, "资金流水归档完成", UserContext.getCurrentUserId());
            if (updated == 0) continue;
            alert.setProcessStatus("ARCHIVED");
            alert.setArchivedAt(now);
            alert.setStatusRemark("资金流水归档完成");
            for (Long userId : recipientResolver.resolve(entry.getTenantId())) {
                notificationDispatcher.dispatchStatusChanged(
                        entry.getTenantId(), userId, alert, "资金流水已归档", "资金流水归档完成");
            }
        }
    }

    private String message(CashJournalEntry entry) {
        Set<String> missing = new LinkedHashSet<>();
        if (entry.getAccountId() == null) missing.add("资金账户");
        if (fileMapper.countActiveByBusiness(entry.getTenantId(), SOURCE_TYPE, entry.getId()) == 0) missing.add("附件");
        missing.add("归档确认");
        return "资金流水 " + entry.getEntryNo() + "，金额 " + entry.getAmount().toPlainString()
                + "，创建时间 " + entry.getCreatedAt() + "，超过24小时未归档；缺失："
                + String.join("、", missing) + "。";
    }

    private String dedupKey(Long tenantId, Long entryId) {
        return RULE_TYPE + ":" + tenantId + ":" + entryId;
    }
}
