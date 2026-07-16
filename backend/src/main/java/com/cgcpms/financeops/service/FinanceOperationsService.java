package com.cgcpms.financeops.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.financeops.dto.FinanceOperationsModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinanceOperationsService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> adjustBudget(BudgetAdjustmentRequest request) {
        requireKey(request.idempotencyKey());
        Map<String,Object> existing = operationByKey(request.idempotencyKey());
        if (existing != null) return existing;
        BigDecimal delta = money(request.deltaAmount());
        if (delta.signum() == 0) throw error("BUDGET_ADJUSTMENT_ZERO", "预算调整金额不能为零");
        Map<String,Object> line = lockBudgetLine(request.budgetLineId());
        requireActiveBudget(longValue(line.get("budget_id")));
        BigDecimal current = decimal(line.get("budget_amount"));
        BigDecimal locked = decimal(line.get("reserved_amount")).add(decimal(line.get("consumed_amount")));
        if (current.add(delta).compareTo(locked) < 0) {
            throw error("BUDGET_ADJUSTMENT_BELOW_LOCKED", "调整后预算不得低于已占用与已消耗合计");
        }
        jdbc.update("UPDATE project_budget_line SET budget_amount=budget_amount+?, version=version+1 WHERE id=? AND tenant_id=?",
                delta, request.budgetLineId(), tenant());
        Long opId = insertOperation("ADJUST", longValue(line.get("project_id")), request.budgetLineId(), null,
                null, delta, request.reason(), request.idempotencyKey());
        insertBudgetAudit(longValue(line.get("budget_id")), request.budgetLineId(), longValue(line.get("project_id")),
                "ADJUST", opId, delta, request.idempotencyKey(), request.reason());
        refreshBudgetTotal(longValue(line.get("budget_id")));
        return operationByKey(request.idempotencyKey());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> transferBudget(BudgetTransferRequest request) {
        requireKey(request.idempotencyKey());
        Map<String,Object> existing = operationByKey(request.idempotencyKey());
        if (existing != null) return existing;
        if (Objects.equals(request.fromBudgetLineId(), request.toBudgetLineId())) {
            throw error("BUDGET_TRANSFER_SAME_LINE", "预算调拨的转出与转入科目不能相同");
        }
        BigDecimal amount = positiveMoney(request.amount(), "BUDGET_TRANSFER_AMOUNT_INVALID");
        long first = Math.min(request.fromBudgetLineId(), request.toBudgetLineId());
        long second = Math.max(request.fromBudgetLineId(), request.toBudgetLineId());
        Map<String,Object> firstLine = lockBudgetLine(first);
        Map<String,Object> secondLine = lockBudgetLine(second);
        Map<String,Object> from = Objects.equals(request.fromBudgetLineId(), first) ? firstLine : secondLine;
        Map<String,Object> to = Objects.equals(request.toBudgetLineId(), first) ? firstLine : secondLine;
        if (!Objects.equals(longValue(from.get("budget_id")), longValue(to.get("budget_id")))) {
            throw error("BUDGET_TRANSFER_VERSION_MISMATCH", "仅允许在同一生效预算版本内调拨");
        }
        requireActiveBudget(longValue(from.get("budget_id")));
        BigDecimal available = decimal(from.get("budget_amount"))
                .subtract(decimal(from.get("reserved_amount"))).subtract(decimal(from.get("consumed_amount")));
        if (available.compareTo(amount) < 0) throw error("BUDGET_TRANSFER_INSUFFICIENT", "转出科目可用预算不足");
        jdbc.update("UPDATE project_budget_line SET budget_amount=budget_amount-?, version=version+1 WHERE id=? AND tenant_id=?",
                amount, request.fromBudgetLineId(), tenant());
        jdbc.update("UPDATE project_budget_line SET budget_amount=budget_amount+?, version=version+1 WHERE id=? AND tenant_id=?",
                amount, request.toBudgetLineId(), tenant());
        Long opId = insertOperation("TRANSFER", longValue(from.get("project_id")), request.fromBudgetLineId(),
                request.toBudgetLineId(), null, amount, request.reason(), request.idempotencyKey());
        insertBudgetAudit(longValue(from.get("budget_id")), request.fromBudgetLineId(), longValue(from.get("project_id")),
                "TRANSFER_OUT", opId, amount.negate(), request.idempotencyKey() + ":OUT", request.reason());
        insertBudgetAudit(longValue(to.get("budget_id")), request.toBudgetLineId(), longValue(to.get("project_id")),
                "TRANSFER_IN", opId, amount, request.idempotencyKey() + ":IN", request.reason());
        return operationByKey(request.idempotencyKey());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> releaseContractQuota(ContractQuotaReleaseRequest request) {
        requireKey(request.idempotencyKey());
        Map<String,Object> existing = operationByKey(request.idempotencyKey());
        if (existing != null) return existing;
        BigDecimal amount = positiveMoney(request.amount(), "CONTRACT_QUOTA_RELEASE_AMOUNT_INVALID");
        Map<String,Object> allocation = one("SELECT * FROM contract_budget_allocation WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE",
                request.contractAllocationId(), tenant());
        if (allocation == null) throw error("CONTRACT_BUDGET_ALLOCATION_NOT_FOUND", "合同预算分配不存在");
        BigDecimal releasable = decimal(allocation.get("allocated_amount"))
                .subtract(decimal(allocation.get("reserved_amount"))).subtract(decimal(allocation.get("consumed_amount")));
        if (releasable.compareTo(amount) < 0) throw error("CONTRACT_QUOTA_RELEASE_EXCEEDED", "释放金额超过合同未使用额度");
        jdbc.update("UPDATE contract_budget_allocation SET allocated_amount=allocated_amount-?, version=version+1 WHERE id=? AND tenant_id=?",
                amount, request.contractAllocationId(), tenant());
        insertOperation("CONTRACT_RELEASE", longValue(allocation.get("project_id")),
                longValue(allocation.get("budget_line_id")), null, request.contractAllocationId(), amount,
                request.reason(), request.idempotencyKey());
        return operationByKey(request.idempotencyKey());
    }

    public Map<String,Object> budgetVersionComparison(Long projectId) {
        List<Map<String,Object>> versions = jdbc.queryForList("""
                SELECT b.id,b.version_no,b.budget_name,b.total_amount,b.status,b.effective_at,
                       COALESCE(SUM(l.reserved_amount),0) reserved_amount,
                       COALESCE(SUM(l.consumed_amount),0) consumed_amount
                  FROM project_budget b LEFT JOIN project_budget_line l ON l.budget_id=b.id AND l.deleted_flag=0
                 WHERE b.tenant_id=? AND b.project_id=? AND b.deleted_flag=0
                 GROUP BY b.id,b.version_no,b.budget_name,b.total_amount,b.status,b.effective_at
                 ORDER BY b.created_at
                """, tenant(), projectId);
        List<Map<String,Object>> lines = jdbc.queryForList("""
                SELECT b.version_no,l.cost_subject_id,s.subject_code,s.subject_name,l.budget_amount,
                       l.reserved_amount,l.consumed_amount
                  FROM project_budget b JOIN project_budget_line l ON l.budget_id=b.id AND l.deleted_flag=0
                  JOIN cost_subject s ON s.id=l.cost_subject_id
                 WHERE b.tenant_id=? AND b.project_id=? AND b.deleted_flag=0
                 ORDER BY s.subject_code,b.created_at
                """, tenant(), projectId);
        return Map.of("projectId", projectId, "versions", versions, "lines", lines);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createSchedule(PaymentScheduleRequest request) {
        Map<String,Object> contract = one("SELECT id,project_id,contract_status FROM ct_contract WHERE id=? AND tenant_id=? AND deleted_flag=0",
                request.contractId(), tenant());
        if (contract == null || !Objects.equals(longValue(contract.get("project_id")), request.projectId())) {
            throw error("PAYMENT_SCHEDULE_CONTRACT_MISMATCH", "付款计划合同不属于所选项目");
        }
        if (request.payApplicationId() != null && one("SELECT id FROM pay_application WHERE id=? AND tenant_id=? AND contract_id=?",
                request.payApplicationId(), tenant(), request.contractId()) == null) {
            throw error("PAYMENT_SCHEDULE_APPLICATION_MISMATCH", "付款计划申请不属于所选合同");
        }
        Long id = IdWorker.getId();
        jdbc.update("""
                INSERT INTO payment_schedule(id,tenant_id,project_id,contract_id,pay_application_id,schedule_name,
                 planned_date,planned_amount,paid_amount,reminder_days,status,version,created_by,created_at,updated_by,updated_at)
                VALUES(?,?,?,?,?,?,?,?,0,?,'PLANNED',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)
                """, id, tenant(), request.projectId(), request.contractId(), request.payApplicationId(),
                request.scheduleName().trim(), request.plannedDate(), money(request.plannedAmount()),
                request.reminderDays() == null ? 7 : request.reminderDays(), user(), user());
        return one("SELECT * FROM payment_schedule WHERE id=? AND tenant_id=?", id, tenant());
    }

    public List<Map<String,Object>> schedules(String status) {
        return status == null || status.isBlank()
                ? jdbc.queryForList("SELECT * FROM payment_schedule WHERE tenant_id=? ORDER BY planned_date,id", tenant())
                : jdbc.queryForList("SELECT * FROM payment_schedule WHERE tenant_id=? AND status=? ORDER BY planned_date,id", tenant(), status);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> runReconciliation(LocalDate businessDate) {
        return runReconciliationForTenant(tenant(), businessDate == null ? LocalDate.now() : businessDate, user());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> generateAlerts() {
        int created = generateAlertsForTenant(tenant(), LocalDateTime.now());
        return Map.of("created", created, "open", count("SELECT COUNT(*) FROM finance_alert WHERE tenant_id=? AND status='OPEN'", tenant()));
    }

    public List<Map<String,Object>> alerts(String status) {
        return jdbc.queryForList("SELECT * FROM finance_alert WHERE tenant_id=? AND (? IS NULL OR status=?) ORDER BY severity DESC,due_at,id",
                tenant(), status, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleAlert(Long id, AlertHandleRequest request) {
        String status = request.status().trim().toUpperCase();
        if (!Set.of("RESOLVED", "IGNORED").contains(status)) throw error("FINANCE_ALERT_STATUS_INVALID", "预警只能处理为 RESOLVED 或 IGNORED");
        if (jdbc.update("UPDATE finance_alert SET status=?,handled_by=?,handled_at=CURRENT_TIMESTAMP,handle_note=? WHERE id=? AND tenant_id=? AND status='OPEN'",
                status, user(), request.note().trim(), id, tenant()) != 1) throw error("FINANCE_ALERT_NOT_OPEN", "预警不存在或已处理");
    }

    @Transactional(rollbackFor = Exception.class)
    public void markInvoiceException(Long invoiceId, InvoiceExceptionRequest request) {
        String status = request.status().trim().toUpperCase();
        if (!Set.of("NORMAL", "SUSPECT", "REJECTED", "PENDING_CREDIT").contains(status)) {
            throw error("INVOICE_EXCEPTION_STATUS_INVALID", "异常票状态不合法");
        }
        if (jdbc.update("UPDATE pay_invoice SET exception_status=?,exception_reason=?,version=version+1 WHERE id=? AND tenant_id=? AND deleted_flag=0",
                status, request.reason().trim(), invoiceId, tenant()) != 1) throw error("INVOICE_NOT_FOUND", "发票不存在");
    }

    public Map<String,Object> invoiceWriteOffProgress(Long invoiceId) {
        Map<String,Object> invoice = one("SELECT id,invoice_no,invoice_amount,verify_status,exception_status FROM pay_invoice WHERE id=? AND tenant_id=? AND deleted_flag=0",
                invoiceId, tenant());
        if (invoice == null) throw error("INVOICE_NOT_FOUND", "发票不存在");
        BigDecimal allocated = decimal(jdbc.queryForObject("SELECT COALESCE(SUM(allocated_amount),0) FROM invoice_payment_allocation WHERE tenant_id=? AND invoice_id=?",
                BigDecimal.class, tenant(), invoiceId));
        BigDecimal total = decimal(invoice.get("invoice_amount"));
        Map<String,Object> result = new LinkedHashMap<>(invoice);
        result.put("allocatedAmount", allocated);
        result.put("unallocatedAmount", total.subtract(allocated));
        result.put("writeOffRate", total.signum() == 0 ? BigDecimal.ZERO : allocated.divide(total, 4, RoundingMode.HALF_UP));
        result.put("allocations", jdbc.queryForList("SELECT * FROM invoice_payment_allocation WHERE tenant_id=? AND invoice_id=? ORDER BY created_at", tenant(), invoiceId));
        return result;
    }

    public byte[] exportAudit(Long projectId, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.of(2000,1,1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT p.project_code,c.contract_code,a.apply_code,r.id pay_record_id,r.external_txn_no,r.pay_status,
                       r.pay_amount,r.paid_at,j.entry_no,j.status journal_status,i.invoice_no,i.verify_status,
                       e.entry_code,e.entry_status
                  FROM pay_record r JOIN pm_project p ON p.id=r.project_id
                  JOIN ct_contract c ON c.id=r.contract_id JOIN pay_application a ON a.id=r.pay_application_id
                  LEFT JOIN cash_journal_entry j ON j.pay_record_id=r.id
                  LEFT JOIN pay_invoice i ON i.pay_record_id=r.id AND i.deleted_flag=0
                  LEFT JOIN accounting_entry e ON e.pay_record_id=r.id AND e.deleted_flag=0
                 WHERE r.tenant_id=? AND r.project_id=? AND r.pay_date BETWEEN ? AND ?
                 ORDER BY r.paid_at,r.id
                """, tenant(), projectId, start, end);
        StringBuilder csv = new StringBuilder("projectCode,contractCode,applyCode,payRecordId,externalTxnNo,payStatus,payAmount,paidAt,journalNo,journalStatus,invoiceNo,invoiceStatus,entryCode,entryStatus\r\n");
        for (Map<String,Object> row : rows) {
            csv.append(csv(row.get("project_code"))).append(',').append(csv(row.get("contract_code"))).append(',')
                    .append(csv(row.get("apply_code"))).append(',').append(csv(row.get("pay_record_id"))).append(',')
                    .append(csv(row.get("external_txn_no"))).append(',').append(csv(row.get("pay_status"))).append(',')
                    .append(csv(row.get("pay_amount"))).append(',').append(csv(row.get("paid_at"))).append(',')
                    .append(csv(row.get("entry_no"))).append(',').append(csv(row.get("journal_status"))).append(',')
                    .append(csv(row.get("invoice_no"))).append(',').append(csv(row.get("verify_status"))).append(',')
                    .append(csv(row.get("entry_code"))).append(',').append(csv(row.get("entry_status"))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Scheduled(cron = "0 15 1 * * ?")
    public void scheduledReconciliationAndAlerts() {
        for (Long tenantId : jdbc.queryForList("SELECT DISTINCT tenant_id FROM pay_application", Long.class)) {
            try { runReconciliationForTenant(tenantId, LocalDate.now().minusDays(1), null); } catch (RuntimeException ignored) { }
            try { generateAlertsForTenant(tenantId, LocalDateTime.now()); } catch (RuntimeException ignored) { }
        }
    }

    private Map<String,Object> runReconciliationForTenant(Long tenantId, LocalDate date, Long operator) {
        Map<String,Object> existing = one("SELECT * FROM finance_reconciliation_run WHERE tenant_id=? AND business_date=? AND run_type='DAILY'", tenantId, date);
        if (existing != null && "COMPLETED".equals(existing.get("status"))) return existing;
        Long runId = existing == null ? IdWorker.getId() : longValue(existing.get("id"));
        if (existing == null) jdbc.update("INSERT INTO finance_reconciliation_run(id,tenant_id,business_date,run_type,status,issue_count,started_at,created_by) VALUES(?,?,?,'DAILY','RUNNING',0,CURRENT_TIMESTAMP,?)",
                runId, tenantId, date, operator);
        else jdbc.update("UPDATE finance_reconciliation_run SET status='RUNNING',issue_count=0,started_at=CURRENT_TIMESTAMP,finished_at=NULL WHERE id=?", runId);
        jdbc.update("DELETE FROM finance_reconciliation_issue WHERE run_id=?", runId);
        int issues = 0;
        issues += insertIssues(runId, tenantId, """
                SELECT 'APPLICATION' dimension_type,a.id business_id,'APPLICATION_PAID_MISMATCH' issue_code,
                       a.actual_pay_amount expected_amount,COALESCE(SUM(CASE WHEN r.pay_status='SUCCESS' THEN r.pay_amount ELSE 0 END),0) actual_amount,
                       '申请实付金额与有效付款合计不一致' detail
                  FROM pay_application a LEFT JOIN pay_record r ON r.pay_application_id=a.id AND r.deleted_flag=0
                 WHERE a.tenant_id=? AND a.deleted_flag=0 GROUP BY a.id,a.actual_pay_amount
                HAVING a.actual_pay_amount<>COALESCE(SUM(CASE WHEN r.pay_status='SUCCESS' THEN r.pay_amount ELSE 0 END),0)
                """);
        issues += insertIssues(runId, tenantId, """
                SELECT 'PAYMENT' dimension_type,r.id business_id,'PAYMENT_JOURNAL_CARDINALITY' issue_code,
                       1 expected_amount,COUNT(j.id) actual_amount,'有效付款必须且只能关联一条现金日记' detail
                  FROM pay_record r LEFT JOIN cash_journal_entry j ON j.pay_record_id=r.id
                 WHERE r.tenant_id=? AND r.pay_status='SUCCESS' AND r.deleted_flag=0 GROUP BY r.id HAVING COUNT(j.id)<>1
                """);
        issues += insertIssues(runId, tenantId, """
                SELECT 'PAYMENT' dimension_type,r.id business_id,'PAYMENT_VOUCHER_MISSING' issue_code,
                       1 expected_amount,COUNT(e.id) actual_amount,'有效付款缺少会计凭证' detail
                  FROM pay_record r LEFT JOIN accounting_entry e ON e.pay_record_id=r.id AND e.deleted_flag=0
                 WHERE r.tenant_id=? AND r.pay_status='SUCCESS' AND r.deleted_flag=0 GROUP BY r.id HAVING COUNT(e.id)<>1
                """);
        issues += insertIssues(runId, tenantId, """
                SELECT 'BUDGET_LINE' dimension_type,l.id business_id,'BUDGET_BALANCE_INVALID' issue_code,
                       l.budget_amount expected_amount,l.reserved_amount+l.consumed_amount actual_amount,
                       '预算占用与消耗不得为负且合计不得超过预算' detail
                  FROM project_budget_line l WHERE l.tenant_id=? AND l.deleted_flag=0
                   AND (l.reserved_amount<0 OR l.consumed_amount<0 OR l.reserved_amount+l.consumed_amount>l.budget_amount)
                """);
        Map<String,Object> summary = Map.of("application", "actualPayAmount vs payment", "payment", "journal and voucher cardinality",
                "budget", "nonnegative and capacity", "issueCount", issues);
        jdbc.update("UPDATE finance_reconciliation_run SET status=?,issue_count=?,summary_json=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",
                issues == 0 ? "COMPLETED" : "COMPLETED_WITH_ISSUES", issues, json(summary), runId);
        return one("SELECT * FROM finance_reconciliation_run WHERE id=?", runId);
    }

    private int generateAlertsForTenant(Long tenantId, LocalDateTime now) {
        int created = 0;
        List<Map<String,Object>> schedules = jdbc.queryForList("SELECT * FROM payment_schedule WHERE tenant_id=? AND status IN ('PLANNED','PARTIALLY_PAID') AND planned_date<=?",
                tenantId, now.toLocalDate().plusDays(30));
        for (Map<String,Object> row : schedules) {
            LocalDate due = row.get("planned_date") instanceof LocalDate value
                    ? value : LocalDate.parse(String.valueOf(row.get("planned_date")));
            int reminder = ((Number)row.get("reminder_days")).intValue();
            if (!due.minusDays(reminder).isAfter(now.toLocalDate())) created += alert(tenantId, "PAYMENT_DUE", "PAYMENT_SCHEDULE",
                    longValue(row.get("id")), due.isBefore(now.toLocalDate()) ? "HIGH" : "MEDIUM",
                    due.atStartOfDay(), "付款计划已到提醒窗口：" + row.get("schedule_name"), "PAYMENT_DUE:" + row.get("id") + ":" + due);
        }
        for (Map<String,Object> row : jdbc.queryForList("SELECT id,closure_due_at,entry_no FROM cash_journal_entry WHERE tenant_id=? AND status='PENDING_ARCHIVE' AND closure_due_at<?", tenantId, now)) {
            created += alert(tenantId, "JOURNAL_ARCHIVE_OVERDUE", "CASH_JOURNAL", longValue(row.get("id")), "HIGH",
                    (LocalDateTime)row.get("closure_due_at"), "现金日记待归档超时：" + row.get("entry_no"), "JOURNAL_OVERDUE:" + row.get("id"));
        }
        for (Map<String,Object> row : jdbc.queryForList("""
                SELECT r.id,r.paid_at FROM pay_record r LEFT JOIN invoice_payment_allocation a ON a.pay_record_id=r.id
                 WHERE r.tenant_id=? AND r.pay_status='SUCCESS' AND r.paid_at<? GROUP BY r.id,r.paid_at HAVING COALESCE(SUM(a.allocated_amount),0)<MAX(r.pay_amount)
                """, tenantId, now.minusDays(30))) {
            created += alert(tenantId, "INVOICE_MISSING", "PAYMENT", longValue(row.get("id")), "MEDIUM",
                    now, "付款超过30天仍未足额取得发票", "INVOICE_MISSING:" + row.get("id"));
        }
        return created;
    }

    private int alert(Long tenantId, String type, String businessType, Long businessId, String severity,
                      LocalDateTime dueAt, String message, String key) {
        try {
            return jdbc.update("INSERT INTO finance_alert(id,tenant_id,alert_type,business_type,business_id,severity,due_at,status,message,alert_key,created_at) VALUES(?,?,?,?,?,?,?,'OPEN',?,?,CURRENT_TIMESTAMP)",
                    IdWorker.getId(), tenantId, type, businessType, businessId, severity, dueAt, message, key);
        } catch (DuplicateKeyException ignored) { return 0; }
    }

    private int insertIssues(Long runId, Long tenantId, String sql) {
        List<Map<String,Object>> rows = jdbc.queryForList(sql, tenantId);
        for (Map<String,Object> row : rows) jdbc.update("""
                INSERT INTO finance_reconciliation_issue(id,tenant_id,run_id,dimension_type,business_id,issue_code,
                 expected_amount,actual_amount,status,detail,created_at) VALUES(?,?,?,?,?,?,?,?,'OPEN',?,CURRENT_TIMESTAMP)
                """, IdWorker.getId(), tenantId, runId, row.get("dimension_type"), row.get("business_id"),
                row.get("issue_code"), row.get("expected_amount"), row.get("actual_amount"), row.get("detail"));
        return rows.size();
    }

    private Long insertOperation(String type, Long projectId, Long fromLine, Long toLine, Long allocation,
                                 BigDecimal amount, String reason, String key) {
        Long id = IdWorker.getId();
        jdbc.update("INSERT INTO budget_operation(id,tenant_id,operation_type,project_id,from_budget_line_id,to_budget_line_id,contract_allocation_id,amount,status,reason,idempotency_key,operator_id,created_at) VALUES(?,?,?,?,?,?,?,?,'COMPLETED',?,?,?,CURRENT_TIMESTAMP)",
                id, tenant(), type, projectId, fromLine, toLine, allocation, amount, reason.trim(), key.trim(), user());
        return id;
    }

    private void insertBudgetAudit(Long budgetId, Long lineId, Long projectId, String entryType, Long businessId,
                                   BigDecimal amount, String key, String remark) {
        Map<String,Object> line = lockBudgetLine(lineId);
        jdbc.update("INSERT INTO budget_ledger(id,tenant_id,budget_id,budget_line_id,project_id,business_type,business_id,entry_type,amount,reserved_balance,consumed_balance,idempotency_key,created_by,created_at,remark) VALUES(?,?,?,?,?,'BUDGET_OPERATION',?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)",
                IdWorker.getId(), tenant(), budgetId, lineId, projectId, businessId, entryType, amount,
                line.get("reserved_amount"), line.get("consumed_amount"), key, user(), remark.trim());
    }

    private void refreshBudgetTotal(Long budgetId) {
        jdbc.update("UPDATE project_budget SET total_amount=(SELECT COALESCE(SUM(budget_amount),0) FROM project_budget_line WHERE budget_id=? AND deleted_flag=0),version=version+1 WHERE id=? AND tenant_id=?",
                budgetId, budgetId, tenant());
    }

    private Map<String,Object> lockBudgetLine(Long id) {
        Map<String,Object> line = one("SELECT * FROM project_budget_line WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE", id, tenant());
        if (line == null) throw error("BUDGET_LINE_NOT_FOUND", "预算科目不存在");
        return line;
    }

    private void requireActiveBudget(Long id) {
        Map<String,Object> budget = one("SELECT status,active_flag FROM project_budget WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE", id, tenant());
        if (budget == null || !"ACTIVE".equals(budget.get("status")) || ((Number)budget.get("active_flag")).intValue() != 1) {
            throw error("BUDGET_NOT_ACTIVE", "仅当前生效预算允许调整或调拨");
        }
    }

    private Map<String,Object> operationByKey(String key) {
        return one("SELECT * FROM budget_operation WHERE tenant_id=? AND idempotency_key=?", tenant(), key.trim());
    }
    private Map<String,Object> one(String sql, Object... args) {
        List<Map<String,Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private Long tenant() { Long v=UserContext.getCurrentTenantId(); if(v==null) throw error("TENANT_CONTEXT_REQUIRED","缺少租户上下文"); return v; }
    private Long user() { return UserContext.getCurrentUserId(); }
    private static BigDecimal money(BigDecimal v) { return (v==null?BigDecimal.ZERO:v).setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal positiveMoney(BigDecimal v,String code){ BigDecimal m=money(v); if(m.signum()<=0) throw error(code,"金额必须大于0"); return m; }
    private static BigDecimal decimal(Object v){ return v==null?BigDecimal.ZERO:money(v instanceof BigDecimal b?b:new BigDecimal(v.toString())); }
    private static Long longValue(Object v){ return v==null?null:((Number)v).longValue(); }
    private static void requireKey(String key){ if(key==null||key.isBlank()||key.length()>128) throw error("IDEMPOTENCY_KEY_INVALID","幂等键不能为空且不得超过128字符"); }
    private String json(Object v){ try{return objectMapper.writeValueAsString(v);}catch(JsonProcessingException e){throw error("FINANCE_JSON_ERROR","财务数据序列化失败");} }
    private static String csv(Object value){ if(value==null)return ""; String s=String.valueOf(value); String stripped=s.stripLeading(); if(!stripped.isEmpty()&&"=+-@".indexOf(stripped.charAt(0))>=0)s="'"+s; return '"'+s.replace("\"","\"\"")+'"'; }
    private static BusinessException error(String code,String message){ return new BusinessException(code,message); }
}
