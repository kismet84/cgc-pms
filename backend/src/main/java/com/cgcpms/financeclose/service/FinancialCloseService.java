package com.cgcpms.financeclose.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.financeclose.dto.FinancialCloseModels.AdjustmentRequest;
import com.cgcpms.financeclose.dto.FinancialCloseModels.BankResolveRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinancialCloseService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AccountingEntryMapper entryMapper;
    private final AccountingEntryLineMapper lineMapper;
    private final AccountingPeriodGuard periodGuard;

    public List<Map<String, Object>> periods(Integer year) {
        return jdbc.queryForList("SELECT * FROM finance_period WHERE tenant_id=? AND (? IS NULL OR fiscal_year=?) ORDER BY fiscal_year DESC,fiscal_month DESC",
                tenant(), year, year);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ensurePeriod(int year, int month) {
        validateYearMonth(year, month);
        Map<String, Object> existing = period(year, month);
        if (existing != null) return existing;
        YearMonth ym = YearMonth.of(year, month);
        Long id = IdWorker.getId();
        try {
            jdbc.update("INSERT INTO finance_period(id,tenant_id,period_code,fiscal_year,fiscal_month,start_date,end_date,status,issue_count,version,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?,'OPEN',0,0,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    id, tenant(), ym.toString(), year, month, ym.atDay(1), ym.atEndOfMonth(), user());
        } catch (DuplicateKeyException ignored) {
            return period(year, month);
        }
        audit("FINANCE_PERIOD_CREATED", "FINANCE_PERIOD", id, Map.of("period", ym.toString()));
        return byId(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runChecks(int year, int month) {
        Map<String, Object> period = ensurePeriod(year, month);
        String originalStatus = String.valueOf(period.get("status"));
        if ("CLOSED".equals(originalStatus)) throw error("FINANCE_PERIOD_CLOSED", "已结账期间不能重复检查，请先反结账");
        Long periodId = longValue(period.get("id"));
        LocalDate start = date(period.get("start_date"));
        LocalDate end = date(period.get("end_date"));
        LocalDate endExclusive = end.plusDays(1);
        jdbc.update("UPDATE finance_period SET status='CHECKING',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", periodId);
        jdbc.update("DELETE FROM finance_period_check WHERE tenant_id=? AND period_id=?", tenant(), periodId);
        jdbc.update("DELETE FROM finance_account_reconciliation WHERE tenant_id=? AND period_id=?", tenant(), periodId);
        jdbc.update("DELETE FROM finance_bank_reconciliation WHERE tenant_id=? AND period_id=?", tenant(), periodId);

        int issues = 0;
        int unposted = count("SELECT COUNT(*) FROM accounting_entry WHERE tenant_id=? AND entry_date BETWEEN ? AND ? AND deleted_flag=0 AND entry_status='DRAFT'", tenant(), start, end);
        issues += addCheck(periodId, "UNPOSTED_ENTRY", unposted, Map.of("message", "期间内仍有未过账凭证"));
        int unbalanced = count("SELECT COUNT(*) FROM accounting_entry WHERE tenant_id=? AND entry_date BETWEEN ? AND ? AND deleted_flag=0 AND total_debit<>total_credit", tenant(), start, end);
        issues += addCheck(periodId, "UNBALANCED_ENTRY", unbalanced, Map.of("message", "凭证借贷不平衡"));
        int missingVoucher = count("SELECT COUNT(*) FROM pay_record p WHERE p.tenant_id=? AND p.pay_status='SUCCESS' AND p.deleted_flag=0 AND COALESCE(p.paid_at,pay_date)>=? AND COALESCE(p.paid_at,pay_date)<? AND NOT EXISTS(SELECT 1 FROM accounting_entry e WHERE e.tenant_id=p.tenant_id AND e.pay_record_id=p.id AND e.entry_status='POSTED' AND e.deleted_flag=0)", tenant(), start, endExclusive)
                + count("SELECT COUNT(*) FROM collection_record c WHERE c.tenant_id=? AND c.status='SUCCESS' AND c.deleted_flag=0 AND c.collected_at>=? AND c.collected_at<? AND NOT EXISTS(SELECT 1 FROM accounting_entry e WHERE e.tenant_id=c.tenant_id AND e.collection_record_id=c.id AND e.entry_status='POSTED' AND e.deleted_flag=0)", tenant(), start.atStartOfDay(), endExclusive.atStartOfDay());
        issues += addCheck(periodId, "SOURCE_VOUCHER_COMPLETENESS", missingVoucher, Map.of("message", "成功收付款必须存在已过账凭证"));

        int bankIssues = buildBankReconciliation(periodId, start, endExclusive);
        issues += addCheck(periodId, "BANK_RECONCILIATION", bankIssues, Map.of("message", "银行回单与收付款、现金日记账必须一一匹配"));
        issues += buildAccountReconciliation(periodId, "AR", start, endExclusive);
        issues += buildAccountReconciliation(periodId, "AP", start, endExclusive);

        String nextStatus = "REOPENED".equals(originalStatus) ? "REOPENED" : "OPEN";
        jdbc.update("UPDATE finance_period SET status=?,last_check_at=CURRENT_TIMESTAMP,issue_count=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                nextStatus, issues, periodId);
        audit("FINANCE_PERIOD_CHECKED", "FINANCE_PERIOD", periodId, Map.of("issueCount", issues));
        return trace(periodId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> close(int year, int month, String comment) {
        Map<String, Object> period = requiredPeriod(year, month);
        if ("CLOSED".equals(period.get("status"))) throw error("FINANCE_PERIOD_ALREADY_CLOSED", "会计期间已结账");
        if (period.get("last_check_at") == null) throw error("FINANCE_PERIOD_CHECK_REQUIRED", "月结前必须运行完整性检查");
        if (((Number) period.get("issue_count")).intValue() > 0) throw error("FINANCE_PERIOD_ISSUES_EXIST", "月结检查存在未解决异常，禁止结账");
        Long id = longValue(period.get("id"));
        LocalDate start = date(period.get("start_date"));
        LocalDate end = date(period.get("end_date"));
        LocalDateTime checkedAt = dateTime(period.get("last_check_at"));
        int changed = count("SELECT COUNT(*) FROM accounting_entry WHERE tenant_id=? AND entry_date BETWEEN ? AND ? AND deleted_flag=0 AND updated_at>?", tenant(), start, end, checkedAt);
        if (changed > 0) throw error("FINANCE_PERIOD_CHECK_STALE", "检查后凭证发生变化，请重新运行月结检查");
        jdbc.update("UPDATE accounting_entry SET period_id=? WHERE tenant_id=? AND entry_date BETWEEN ? AND ? AND deleted_flag=0", id, tenant(), start, end);
        jdbc.update("UPDATE finance_period SET status='CLOSED',closed_by=?,closed_at=CURRENT_TIMESTAMP,close_comment=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                user(), blank(comment), id);
        audit("FINANCE_PERIOD_CLOSED", "FINANCE_PERIOD", id, Map.of("comment", comment == null ? "" : comment));
        return trace(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reopen(int year, int month, String reason) {
        if (reason == null || reason.isBlank()) throw error("FINANCE_REOPEN_REASON_REQUIRED", "反结账必须填写原因");
        Map<String, Object> period = requiredPeriod(year, month);
        if (!"CLOSED".equals(period.get("status"))) throw error("FINANCE_PERIOD_NOT_CLOSED", "仅已结账期间可反结账");
        Long id = longValue(period.get("id"));
        jdbc.update("UPDATE finance_period SET status='REOPENED',reopened_by=?,reopened_at=CURRENT_TIMESTAMP,reopen_reason=?,last_check_at=NULL,issue_count=0,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                user(), reason.trim(), id);
        audit("FINANCE_PERIOD_REOPENED", "FINANCE_PERIOD", id, Map.of("reason", reason.trim()));
        return trace(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resolveBank(Long reconciliationId, BankResolveRequest request) {
        Map<String, Object> row = one("SELECT * FROM finance_bank_reconciliation WHERE id=? AND tenant_id=? FOR UPDATE", reconciliationId, tenant());
        if (row == null) throw error("BANK_RECONCILIATION_NOT_FOUND", "银行对账记录不存在");
        if (!"EXCEPTION".equals(row.get("status"))) throw error("BANK_RECONCILIATION_STATUS_INVALID", "仅异常对账记录可人工处理");
        String type = request.businessType().trim().toUpperCase();
        if (!Set.of("PAY_RECORD", "COLLECTION_RECORD").contains(type)) throw error("BANK_RECONCILIATION_TYPE_INVALID", "业务类型仅支持 PAY_RECORD 或 COLLECTION_RECORD");
        String expectedDirection = "PAY_RECORD".equals(type) ? "OUT" : "IN";
        if (!expectedDirection.equals(row.get("direction"))) throw error("BANK_RECONCILIATION_DIRECTION_MISMATCH", "银行收支方向与业务类型不一致");
        String table = "PAY_RECORD".equals(type) ? "pay_record" : "collection_record";
        String amountColumn = "PAY_RECORD".equals(type) ? "pay_amount" : "amount";
        Map<String, Object> business = one("SELECT " + amountColumn + " amount FROM " + table + " WHERE id=? AND tenant_id=? AND deleted_flag=0", request.businessId(), tenant());
        if (business == null) throw error("BANK_RECONCILIATION_BUSINESS_NOT_FOUND", "匹配业务记录不存在");
        BigDecimal bankAmount = money(row.get("bank_amount"));
        BigDecimal businessAmount = money(business.get("amount"));
        if (bankAmount.compareTo(businessAmount) != 0) throw error("BANK_RECONCILIATION_AMOUNT_MISMATCH", "银行回单金额与业务金额不一致");
        Map<String, Object> journal = one("SELECT id FROM cash_journal_entry WHERE id=? AND tenant_id=? AND deleted_flag=0", request.cashJournalId(), tenant());
        if (journal == null) throw error("BANK_RECONCILIATION_JOURNAL_NOT_FOUND", "现金日记账不存在");
        Long receiptId = longValue(row.get("bank_receipt_id"));
        if ("PAY_RECORD".equals(type)) jdbc.update("UPDATE bank_receipt SET match_status='MATCHED',pay_record_id=?,collection_record_id=NULL,cash_journal_id=?,confidence=1,matched_at=CURRENT_TIMESTAMP WHERE id=?", request.businessId(), request.cashJournalId(), receiptId);
        else jdbc.update("UPDATE bank_receipt SET match_status='MATCHED',collection_record_id=?,pay_record_id=NULL,cash_journal_id=?,confidence=1,matched_at=CURRENT_TIMESTAMP WHERE id=?", request.businessId(), request.cashJournalId(), receiptId);
        jdbc.update("UPDATE finance_bank_reconciliation SET business_type=?,business_id=?,cash_journal_id=?,business_amount=?,difference_amount=0,status='RESOLVED',match_method='MANUAL',resolved_by=?,resolved_at=CURRENT_TIMESTAMP,resolution_note=? WHERE id=?",
                type, request.businessId(), request.cashJournalId(), businessAmount, user(), request.note().trim(), reconciliationId);
        audit("BANK_RECONCILIATION_RESOLVED", "FINANCE_BANK_RECONCILIATION", reconciliationId, Map.of("businessType", type, "businessId", request.businessId()));
        return one("SELECT * FROM finance_bank_reconciliation WHERE id=?", reconciliationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public AccountingEntry createAdjustment(AdjustmentRequest request) {
        periodGuard.assertWritable(request.entryDate());
        BigDecimal debit = request.lines().stream().filter(v -> "DEBIT".equalsIgnoreCase(v.direction())).map(v -> v.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = request.lines().stream().filter(v -> "CREDIT".equalsIgnoreCase(v.direction())).map(v -> v.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debit.signum() <= 0 || debit.compareTo(credit) != 0) throw error("ADJUSTMENT_ENTRY_UNBALANCED", "调整凭证必须借贷平衡且金额大于零");
        if (request.lines().stream().anyMatch(v -> !Set.of("DEBIT", "CREDIT").contains(v.direction().toUpperCase()))) throw error("ADJUSTMENT_DIRECTION_INVALID", "分录方向仅支持 DEBIT 或 CREDIT");
        AccountingEntry entry = new AccountingEntry();
        entry.setTenantId(tenant()); entry.setEntryCode("ADJ-" + request.entryDate().toString().replace("-", "") + "-" + IdWorker.getId());
        entry.setEntryDate(request.entryDate()); entry.setEntryType("ADJUSTMENT"); entry.setSourceType("MANUAL_ADJUSTMENT"); entry.setSourceId(IdWorker.getId());
        entry.setProjectId(request.projectId()); entry.setContractId(request.contractId()); entry.setEntryStatus("DRAFT"); entry.setReviewStatus("PENDING");
        entry.setTotalDebit(debit); entry.setTotalCredit(credit); entry.setPeriodId(periodGuard.findPeriodId(request.entryDate())); entry.setAdjustmentFlag(1); entry.setVersion(0); entry.setRemark(request.reason());
        entryMapper.insert(entry);
        int lineNo = 1;
        for (var item : request.lines()) {
            AccountingEntryLine line = new AccountingEntryLine(); line.setTenantId(tenant()); line.setEntryId(entry.getId()); line.setLineNo(lineNo++);
            line.setDirection(item.direction().toUpperCase()); line.setAccountCode(blank(item.accountCode())); line.setAccountName(blank(item.accountName()));
            line.setCostSubjectId(item.costSubjectId()); line.setAmount(item.amount()); line.setSummary(item.summary()); lineMapper.insert(line);
        }
        audit("ADJUSTMENT_ENTRY_CREATED", "ACCOUNTING_ENTRY", entry.getId(), Map.of("reason", request.reason()));
        return entry;
    }

    public Map<String, Object> statements(int year, int month) {
        Map<String, Object> period = requiredPeriod(year, month);
        LocalDate start = date(period.get("start_date")); LocalDate end = date(period.get("end_date"));
        Map<String, Object> result = new LinkedHashMap<>(); result.put("period", period);
        result.put("trialBalance", jdbc.queryForList("SELECT l.account_code,l.account_name,SUM(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE 0 END) debit,SUM(CASE WHEN l.direction='CREDIT' THEN l.amount ELSE 0 END) credit FROM accounting_entry e JOIN accounting_entry_line l ON l.entry_id=e.id AND l.tenant_id=e.tenant_id WHERE e.tenant_id=? AND e.entry_date BETWEEN ? AND ? AND e.entry_status='POSTED' AND e.deleted_flag=0 AND l.deleted_flag=0 GROUP BY l.account_code,l.account_name ORDER BY l.account_code", tenant(), start, end));
        result.put("receivableOutstanding", amount("SELECT COALESCE(SUM(outstanding_amount),0) FROM account_receivable WHERE tenant_id=? AND deleted_flag=0", tenant()));
        result.put("payableOutstanding", amount("SELECT COALESCE(SUM(GREATEST(approved_amount-actual_pay_amount,0)),0) FROM pay_application WHERE tenant_id=? AND approval_status='APPROVED' AND deleted_flag=0", tenant()));
        result.put("cashFlow", one("SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE 0 END),0) inflow,COALESCE(SUM(CASE WHEN direction='OUT' THEN amount ELSE 0 END),0) outflow FROM cash_journal_entry WHERE tenant_id=? AND business_date BETWEEN ? AND ? AND deleted_flag=0", tenant(), start, end));
        return result;
    }

    public Map<String, Object> trace(Long periodId) {
        Map<String, Object> period = byId(periodId);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("period", period);
        result.put("checks", jdbc.queryForList("SELECT * FROM finance_period_check WHERE tenant_id=? AND period_id=? ORDER BY check_type", tenant(), periodId));
        result.put("accountReconciliations", jdbc.queryForList("SELECT * FROM finance_account_reconciliation WHERE tenant_id=? AND period_id=? ORDER BY account_type", tenant(), periodId));
        result.put("bankReconciliations", jdbc.queryForList("SELECT * FROM finance_bank_reconciliation WHERE tenant_id=? AND period_id=? ORDER BY created_at", tenant(), periodId));
        result.put("entries", jdbc.queryForList("SELECT id,entry_code,entry_date,entry_type,source_type,source_id,entry_status,review_status,total_debit,total_credit,original_entry_id,reversed_entry_id FROM accounting_entry WHERE tenant_id=? AND entry_date BETWEEN ? AND ? AND deleted_flag=0 ORDER BY entry_date,entry_code", tenant(), date(period.get("start_date")), date(period.get("end_date"))));
        result.put("auditTrail", jdbc.queryForList("SELECT event_type,operator_id,event_at,payload_json,payload_hash FROM finance_audit_event WHERE tenant_id=? AND business_type='FINANCE_PERIOD' AND business_id=? ORDER BY event_at", tenant(), periodId));
        return result;
    }

    private int buildBankReconciliation(Long periodId, LocalDate start, LocalDate endExclusive) {
        List<Map<String, Object>> receipts = jdbc.queryForList("SELECT * FROM bank_receipt WHERE tenant_id=? AND transaction_time>=? AND transaction_time<? ORDER BY transaction_time", tenant(), start.atStartOfDay(), endExclusive.atStartOfDay());
        int issues = 0;
        for (Map<String, Object> receipt : receipts) {
            String direction = String.valueOf(receipt.get("direction")); Long businessId = null; String businessType = null; BigDecimal businessAmount = BigDecimal.ZERO;
            if ("OUT".equals(direction) && receipt.get("pay_record_id") != null) { businessId=longValue(receipt.get("pay_record_id")); businessType="PAY_RECORD"; businessAmount=amount("SELECT pay_amount FROM pay_record WHERE id=? AND tenant_id=?",businessId,tenant()); }
            if ("IN".equals(direction) && receipt.get("collection_record_id") != null) { businessId=longValue(receipt.get("collection_record_id")); businessType="COLLECTION_RECORD"; businessAmount=amount("SELECT amount FROM collection_record WHERE id=? AND tenant_id=?",businessId,tenant()); }
            BigDecimal bankAmount=money(receipt.get("amount")),difference=bankAmount.subtract(businessAmount).abs();
            boolean matched=businessId!=null&&receipt.get("cash_journal_id")!=null&&difference.signum()==0&&"MATCHED".equals(receipt.get("match_status"));
            if(!matched)issues++;
            jdbc.update("INSERT INTO finance_bank_reconciliation(id,tenant_id,period_id,bank_receipt_id,direction,business_type,business_id,cash_journal_id,bank_amount,business_amount,difference_amount,status,match_method,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'AUTO',CURRENT_TIMESTAMP)",IdWorker.getId(),tenant(),periodId,receipt.get("id"),direction,businessType,businessId,receipt.get("cash_journal_id"),bankAmount,businessAmount,difference,matched?"MATCHED":"EXCEPTION");
        }
        issues += count("SELECT COUNT(*) FROM pay_record p WHERE p.tenant_id=? AND p.pay_status='SUCCESS' AND p.deleted_flag=0 AND COALESCE(p.paid_at,p.pay_date)>=? AND COALESCE(p.paid_at,p.pay_date)<? AND NOT EXISTS(SELECT 1 FROM bank_receipt b WHERE b.tenant_id=p.tenant_id AND b.direction='OUT' AND b.pay_record_id=p.id AND b.match_status='MATCHED')",tenant(),start,endExclusive);
        issues += count("SELECT COUNT(*) FROM collection_record c WHERE c.tenant_id=? AND c.status='SUCCESS' AND c.deleted_flag=0 AND c.collected_at>=? AND c.collected_at<? AND NOT EXISTS(SELECT 1 FROM bank_receipt b WHERE b.tenant_id=c.tenant_id AND b.direction='IN' AND b.collection_record_id=c.id AND b.match_status='MATCHED')",tenant(),start.atStartOfDay(),endExclusive.atStartOfDay());
        return issues;
    }

    private int buildAccountReconciliation(Long periodId, String type, LocalDate start, LocalDate endExclusive) {
        BigDecimal expected; BigDecimal ledger;
        if ("AR".equals(type)) {
            expected=amount("SELECT COALESCE(SUM(original_amount-collected_amount-credited_amount),0) FROM account_receivable WHERE tenant_id=? AND deleted_flag=0 AND created_at<?",tenant(),endExclusive.atStartOfDay());
            ledger=amount("SELECT COALESCE(SUM(outstanding_amount),0) FROM account_receivable WHERE tenant_id=? AND deleted_flag=0 AND created_at<?",tenant(),endExclusive.atStartOfDay());
        } else {
            expected=amount("SELECT COALESCE(SUM(GREATEST(p.approved_amount-COALESCE((SELECT SUM(r.pay_amount) FROM pay_record r WHERE r.tenant_id=p.tenant_id AND r.pay_application_id=p.id AND r.pay_status='SUCCESS' AND r.deleted_flag=0 AND COALESCE(r.paid_at,r.pay_date)<?),0),0)),0) FROM pay_application p WHERE p.tenant_id=? AND p.approval_status='APPROVED' AND p.deleted_flag=0 AND p.created_at<?",endExclusive,tenant(),endExclusive.atStartOfDay());
            ledger=amount("SELECT COALESCE(SUM(GREATEST(approved_amount-actual_pay_amount,0)),0) FROM pay_application WHERE tenant_id=? AND approval_status='APPROVED' AND deleted_flag=0 AND created_at<?",tenant(),endExclusive.atStartOfDay());
        }
        BigDecimal difference=expected.subtract(ledger).abs(); String status=difference.signum()==0?"MATCHED":"EXCEPTION";
        jdbc.update("INSERT INTO finance_account_reconciliation(id,tenant_id,period_id,account_type,expected_amount,ledger_amount,difference_amount,status,detail_json,reconciled_by,reconciled_at) VALUES(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",IdWorker.getId(),tenant(),periodId,type,expected,ledger,difference,status,json(Map.of("periodStart",start.toString(),"periodEndExclusive",endExclusive.toString())),user());
        return addCheck(periodId,type+"_RECONCILIATION","MATCHED".equals(status)?0:1,Map.of("expected",expected,"ledger",ledger,"difference",difference));
    }

    private int addCheck(Long periodId,String type,int issueCount,Object detail){jdbc.update("INSERT INTO finance_period_check(id,tenant_id,period_id,check_type,check_status,issue_count,detail_json,checked_by,checked_at) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",IdWorker.getId(),tenant(),periodId,type,issueCount==0?"PASS":"FAIL",issueCount,json(detail),user());return issueCount;}
    private Map<String,Object> requiredPeriod(int year,int month){validateYearMonth(year,month);Map<String,Object>v=period(year,month);if(v==null)throw error("FINANCE_PERIOD_NOT_FOUND","会计期间不存在");return v;}
    private Map<String,Object> period(int year,int month){return one("SELECT * FROM finance_period WHERE tenant_id=? AND fiscal_year=? AND fiscal_month=?",tenant(),year,month);}
    private Map<String,Object> byId(Long id){Map<String,Object>v=one("SELECT * FROM finance_period WHERE id=? AND tenant_id=?",id,tenant());if(v==null)throw error("FINANCE_PERIOD_NOT_FOUND","会计期间不存在");return v;}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>>rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.getFirst();}
    private int count(String sql,Object...args){Number v=jdbc.queryForObject(sql,Number.class,args);return v==null?0:v.intValue();}
    private BigDecimal amount(String sql,Object...args){Object v=jdbc.queryForObject(sql,Object.class,args);return money(v);}
    private static BigDecimal money(Object v){return(v==null?BigDecimal.ZERO:new BigDecimal(v.toString())).setScale(2,RoundingMode.HALF_UP);}
    private Long tenant(){Long v=UserContext.getCurrentTenantId();if(v==null)throw error("TENANT_CONTEXT_REQUIRED","缺少租户上下文");return v;}
    private Long user(){return UserContext.getCurrentUserId();}
    private static Long longValue(Object v){return v==null?null:Long.valueOf(v.toString());}
    private static LocalDate date(Object v){if(v instanceof LocalDate value)return value;if(v instanceof java.sql.Date value)return value.toLocalDate();return LocalDate.parse(v.toString());}
    private static LocalDateTime dateTime(Object v){if(v instanceof LocalDateTime value)return value;if(v instanceof java.sql.Timestamp value)return value.toLocalDateTime();return LocalDateTime.parse(v.toString().replace(' ','T'));}
    private static String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    private static void validateYearMonth(int year,int month){if(year<2000||month<1||month>12)throw error("FINANCE_PERIOD_INVALID","会计期间年月不合法");}
    private void audit(String event,String type,Long id,Object payload){String body=json(payload);jdbc.update("INSERT INTO finance_audit_event(id,tenant_id,event_type,business_type,business_id,operator_id,event_at,archive_bucket,payload_json,payload_hash) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,'HOT',?,?)",IdWorker.getId(),tenant(),event,type,id,user(),body,sha256(body));}
    private String json(Object v){try{return objectMapper.writeValueAsString(v);}catch(JsonProcessingException e){throw error("FINANCE_JSON_ERROR","财务数据序列化失败");}}
    private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static BusinessException error(String code,String message){return new BusinessException(code,message);}
}
