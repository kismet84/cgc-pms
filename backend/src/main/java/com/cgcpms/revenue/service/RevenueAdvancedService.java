package com.cgcpms.revenue.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.revenue.dto.RevenueOperationsModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RevenueAdvancedService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RevenueOperationsService core;
    private final CashJournalService cashJournalService;
    private final AccountingEntryService accountingEntryService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> creditReceivable(Long receivableId, ReceivableCreditRequest request) {
        Map<String,Object> existing = one("SELECT * FROM receivable_adjustment WHERE tenant_id=? AND idempotency_key=?", tenant(), request.idempotencyKey());
        if (existing != null) return existing;
        Map<String,Object> ar = one("SELECT * FROM account_receivable WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE", receivableId, tenant());
        if (ar == null) throw error("RECEIVABLE_NOT_FOUND", "应收不存在");
        BigDecimal amount = money(request.amount());
        BigDecimal outstanding = decimal(ar.get("outstanding_amount"));
        if (amount.compareTo(outstanding) > 0) throw error("RECEIVABLE_CREDIT_EXCEEDED", "冲减金额不能超过应收余额");
        BigDecimal remaining = outstanding.subtract(amount);
        String status = remaining.signum() == 0 ? "CREDITED" : "PARTIALLY_COLLECTED";
        Long id = IdWorker.getId();
        jdbc.update("INSERT INTO receivable_adjustment(id,tenant_id,receivable_id,adjustment_type,amount,reason,idempotency_key,status,created_by,created_at) VALUES(?,?,?,'CREDIT',?,?,?,'COMPLETED',?,CURRENT_TIMESTAMP)",
                id,tenant(),receivableId,amount,request.reason().trim(),request.idempotencyKey().trim(),user());
        jdbc.update("UPDATE account_receivable SET credited_amount=credited_amount+?,outstanding_amount=?,status=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                amount,remaining,status,user(),receivableId,tenant());
        audit("RECEIVABLE_CREDIT","ACCOUNT_RECEIVABLE",receivableId,longValue(ar.get("project_id")),Map.of("amount",amount,"reason",request.reason()));
        return one("SELECT * FROM receivable_adjustment WHERE id=?",id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> reverseCollection(Long collectionId, CollectionReverseRequest request) {
        Map<String,Object> done = one("SELECT * FROM collection_reversal WHERE tenant_id=? AND idempotency_key=?",tenant(),request.idempotencyKey());
        if (done != null) return done;
        Map<String,Object> collection = one("SELECT * FROM collection_record WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE",collectionId,tenant());
        if (collection == null) throw error("COLLECTION_NOT_FOUND","回款不存在");
        if (!"SUCCESS".equals(collection.get("status"))) throw error("COLLECTION_NOT_REVERSIBLE","只有成功且未冲销的回款可以冲销");
        List<Map<String,Object>> allocations = jdbc.queryForList("SELECT * FROM collection_allocation WHERE tenant_id=? AND collection_id=? AND allocation_type='COLLECTION'",tenant(),collectionId);
        for (Map<String,Object> allocation : allocations) {
            Long arId = longValue(allocation.get("receivable_id"));
            Map<String,Object> ar = one("SELECT * FROM account_receivable WHERE id=? AND tenant_id=? FOR UPDATE",arId,tenant());
            BigDecimal amount = decimal(allocation.get("allocated_amount"));
            BigDecimal collected = decimal(ar.get("collected_amount"));
            if (collected.compareTo(amount)<0) throw error("COLLECTION_REVERSE_LEDGER_MISMATCH","应收已核销金额不足，禁止冲销");
            BigDecimal restored = decimal(ar.get("outstanding_amount")).add(amount);
            jdbc.update("UPDATE account_receivable SET collected_amount=collected_amount-?,outstanding_amount=?,status='OPEN',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                    amount,restored,user(),arId,tenant());
        }
        Long reversalId=IdWorker.getId();
        jdbc.update("INSERT INTO collection_reversal(id,tenant_id,collection_id,idempotency_key,reason,status,created_by,created_at) VALUES(?,?,?,?,?,'COMPLETED',?,CURRENT_TIMESTAMP)",
                reversalId,tenant(),collectionId,request.idempotencyKey().trim(),request.reason().trim(),user());
        jdbc.update("UPDATE collection_record SET status='REVERSED',reversed_at=CURRENT_TIMESTAMP,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",user(),collectionId,tenant());
        Map<String,Object> journal=one("SELECT id,status FROM cash_journal_entry WHERE tenant_id=? AND collection_record_id=? AND deleted_flag=0",tenant(),collectionId);
        if (journal!=null) {
            Long journalId=longValue(journal.get("id"));
            if ("ARCHIVED".equals(journal.get("status"))) cashJournalService.reverse(journalId,request.reason());
            else jdbc.update("UPDATE cash_journal_entry SET status='REVERSED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",user(),journalId,tenant());
        }
        Map<String,Object> entry=one("SELECT id,entry_status FROM accounting_entry WHERE tenant_id=? AND collection_record_id=? AND deleted_flag=0",tenant(),collectionId);
        if(entry!=null && !"REVERSED".equals(entry.get("entry_status"))) {
            if("POSTED".equals(entry.get("entry_status"))) accountingEntryService.reverse(longValue(entry.get("id")));
            else jdbc.update("UPDATE accounting_entry SET entry_status='REVERSED',reversed_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",longValue(entry.get("id")));
        }
        audit("COLLECTION_REVERSED","COLLECTION_RECORD",collectionId,longValue(collection.get("project_id")),Map.of("reason",request.reason()));
        return one("SELECT * FROM collection_reversal WHERE id=?",reversalId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createSchedule(CollectionScheduleRequest request) {
        if (request.plannedDate().isBefore(LocalDate.now())) throw error("COLLECTION_SCHEDULE_DATE_INVALID","计划日期不能早于今天");
        if(request.receivableId()!=null && one("SELECT id FROM account_receivable WHERE id=? AND tenant_id=? AND project_id=? AND contract_id=? AND deleted_flag=0",request.receivableId(),tenant(),request.projectId(),request.contractId())==null)
            throw error("COLLECTION_SCHEDULE_RECEIVABLE_MISMATCH","计划应收不属于所选项目合同");
        Long id=IdWorker.getId();
        jdbc.update("INSERT INTO collection_schedule(id,tenant_id,project_id,contract_id,receivable_id,planned_date,planned_amount,collected_amount,reminder_days,status,note,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,0,?,'PLANNED',?,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",
                id,tenant(),request.projectId(),request.contractId(),request.receivableId(),request.plannedDate(),money(request.plannedAmount()),request.reminderDays()==null?7:request.reminderDays(),request.note().trim(),user(),user());
        return one("SELECT * FROM collection_schedule WHERE id=?",id);
    }

    public List<Map<String,Object>> schedules(String status){return jdbc.queryForList("SELECT * FROM collection_schedule WHERE tenant_id=? AND (? IS NULL OR status=?) ORDER BY planned_date,id",tenant(),status,status);}

    public Map<String,Object> aging(Long projectId){
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("current",agingBucket(projectId,-99999,0));r.put("days1To30",agingBucket(projectId,1,30));
        r.put("days31To60",agingBucket(projectId,31,60));r.put("days61To90",agingBucket(projectId,61,90));r.put("daysOver90",agingBucket(projectId,91,99999));
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> reconcile(LocalDate date){
        LocalDate businessDate=date==null?LocalDate.now():date;
        Map<String,Object> existing=one("SELECT * FROM revenue_reconciliation_run WHERE tenant_id=? AND business_date=?",tenant(),businessDate);
        Long runId=existing==null?IdWorker.getId():longValue(existing.get("id"));
        if(existing==null) jdbc.update("INSERT INTO revenue_reconciliation_run(id,tenant_id,business_date,status,issue_count,started_at,created_by) VALUES(?,?,?,'RUNNING',0,CURRENT_TIMESTAMP,?)",runId,tenant(),businessDate,user());
        else {jdbc.update("DELETE FROM revenue_reconciliation_issue WHERE run_id=?",runId);jdbc.update("UPDATE revenue_reconciliation_run SET status='RUNNING',issue_count=0,started_at=CURRENT_TIMESTAMP,finished_at=NULL WHERE id=?",runId);}
        int issues=0;
        issues+=insertIssues(runId,"SELECT 'RECEIVABLE',id,'AR_BALANCE_MISMATCH',original_amount,collected_amount+credited_amount+outstanding_amount,'应收原值与已收、冲减、余额不一致' FROM account_receivable WHERE tenant_id=? AND deleted_flag=0 AND original_amount<>collected_amount+credited_amount+outstanding_amount");
        issues+=insertIssues(runId,"SELECT 'COLLECTION',id,'COLLECTION_BALANCE_MISMATCH',amount,allocated_amount+unallocated_amount,'回款金额与已分配、未分配不一致' FROM collection_record WHERE tenant_id=? AND deleted_flag=0 AND amount<>allocated_amount+unallocated_amount");
        issues+=insertIssues(runId,"SELECT 'COLLECTION',c.id,'COLLECTION_JOURNAL_CARDINALITY',1,COUNT(j.id),'成功回款与现金日记不是一对一' FROM collection_record c LEFT JOIN cash_journal_entry j ON j.collection_record_id=c.id AND j.deleted_flag=0 WHERE c.tenant_id=? AND c.status='SUCCESS' AND c.deleted_flag=0 GROUP BY c.id HAVING COUNT(j.id)<>1");
        issues+=insertIssues(runId,"SELECT 'SALES_INVOICE',i.id,'INVOICE_ALLOCATION_MISMATCH',i.total_amount,COALESCE(SUM(a.allocated_amount),0),'销项发票分配金额不守恒' FROM sales_invoice i LEFT JOIN sales_invoice_allocation a ON a.invoice_id=i.id WHERE i.tenant_id=? AND i.deleted_flag=0 GROUP BY i.id,i.total_amount HAVING i.total_amount<>COALESCE(SUM(a.allocated_amount),0)");
        jdbc.update("UPDATE revenue_reconciliation_run SET status='COMPLETED',issue_count=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",issues,runId);
        return one("SELECT * FROM revenue_reconciliation_run WHERE id=?",runId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> rebuildSnapshot(Long projectId,LocalDate date,String mode){
        Map<String,Object>d=core.dashboard(projectId);Long id=IdWorker.getId();LocalDate day=date==null?LocalDate.now():date;
        jdbc.update("DELETE FROM revenue_dashboard_snapshot WHERE tenant_id=? AND project_id=? AND snapshot_date=?",tenant(),projectId,day);
        jdbc.update("INSERT INTO revenue_dashboard_snapshot(id,tenant_id,project_id,snapshot_date,formula_version,confirmed_revenue,settled_amount,receivable_amount,outstanding_amount,overdue_amount,collected_amount,invoiced_amount,collection_rate,refreshed_at,refresh_mode) VALUES(?,?,?,?,'REVENUE_COLLECTION_V1',?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)",
                id,tenant(),projectId,day,d.get("confirmedRevenue"),d.get("settledAmount"),d.get("receivableAmount"),d.get("outstandingAmount"),d.get("overdueAmount"),d.get("collectedAmount"),d.get("invoicedAmount"),d.get("collectionRate"),mode==null?"MANUAL":mode);
        return one("SELECT * FROM revenue_dashboard_snapshot WHERE id=?",id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createInvoiceReview(SalesInvoiceReviewRequest request){
        if(one("SELECT id FROM sales_invoice WHERE id=? AND tenant_id=? AND deleted_flag=0",request.invoiceId(),tenant())==null)throw error("SALES_INVOICE_NOT_FOUND","销项发票不存在");
        Long id=IdWorker.getId();jdbc.update("INSERT INTO sales_invoice_review(id,tenant_id,invoice_id,raw_result_json,confidence,comparison_json,review_status,created_at) VALUES(?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP)",id,tenant(),request.invoiceId(),json(request.rawResult()),request.confidence(),json(request.comparison()));
        return one("SELECT * FROM sales_invoice_review WHERE id=?",id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> decideReview(Long id,ReviewDecisionRequest request){
        String decision=request.decision().trim().toUpperCase();if(!Set.of("APPROVED","REJECTED").contains(decision))throw error("SALES_INVOICE_REVIEW_INVALID","复核结论只能为 APPROVED 或 REJECTED");
        if(jdbc.update("UPDATE sales_invoice_review SET review_status=?,reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP,review_note=? WHERE id=? AND tenant_id=? AND review_status='PENDING'",decision,user(),request.note().trim(),id,tenant())!=1)throw error("SALES_INVOICE_REVIEW_NOT_PENDING","复核记录不存在或已处理");
        return one("SELECT * FROM sales_invoice_review WHERE id=?",id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> previewImport(RevenueImportRequest request){
        Map<String,Object> existing=one("SELECT * FROM revenue_import_batch WHERE tenant_id=? AND import_type=? AND project_id=? AND file_hash=?",tenant(),request.importType(),request.projectId(),request.fileHash());if(existing!=null)return existing;
        Long id=IdWorker.getId();int invalid=0;for(RevenueImportRow row:request.rows())if(row.values().isEmpty())invalid++;
        jdbc.update("INSERT INTO revenue_import_batch(id,tenant_id,import_type,project_id,file_name,file_hash,status,total_rows,valid_rows,invalid_rows,diff_summary_json,created_by,created_at) VALUES(?,?,?,?,?,?,'PREVIEW',?,?,?,?,?,CURRENT_TIMESTAMP)",id,tenant(),request.importType().toUpperCase(),request.projectId(),request.fileName(),request.fileHash(),request.rows().size(),request.rows().size()-invalid,invalid,json(Map.of("invalidRows",invalid)),user());
        for(RevenueImportRow row:request.rows()){boolean ok=!row.values().isEmpty();jdbc.update("INSERT INTO revenue_import_row(id,tenant_id,batch_id,row_no,input_json,diff_json,validation_status,validation_message) VALUES(?,?,?,?,?,? ,?,?)",IdWorker.getId(),tenant(),id,row.rowNo(),json(row.values()),json(Map.of()),ok?"VALID":"INVALID",ok?null:"空行");}
        return one("SELECT * FROM revenue_import_batch WHERE id=?",id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createForecast(Long projectId,Long contractId,LocalDate date,String scenario,BigDecimal amount,BigDecimal confidence,String sourceType,Long sourceId){
        Long id=IdWorker.getId();jdbc.update("INSERT INTO collection_forecast(id,tenant_id,project_id,contract_id,forecast_date,scenario,expected_amount,confidence,source_type,source_id,status,version,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVE',0,?,CURRENT_TIMESTAMP)",id,tenant(),projectId,contractId,date,scenario.toUpperCase(),money(amount),confidence==null?BigDecimal.ONE:confidence,sourceType,sourceId,user());return one("SELECT * FROM collection_forecast WHERE id=?",id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> refreshCustomerCredit(Long customerId){
        BigDecimal overdue=decimal(jdbc.queryForObject("SELECT COALESCE(SUM(outstanding_amount),0) FROM account_receivable WHERE tenant_id=? AND customer_id=? AND outstanding_amount>0 AND due_date<CURRENT_DATE AND deleted_flag=0",BigDecimal.class,tenant(),customerId));
        List<Map<String,Object>> overdueRows=jdbc.queryForList("SELECT due_date FROM account_receivable WHERE tenant_id=? AND customer_id=? AND outstanding_amount>0 AND due_date<CURRENT_DATE AND deleted_flag=0",tenant(),customerId);
        int dso=overdueRows.isEmpty()?0:(int)Math.round(overdueRows.stream().mapToLong(r->ChronoUnit.DAYS.between(localDate(r.get("due_date")),LocalDate.now())).average().orElse(0));
        String risk=overdue.signum()==0?"NORMAL":dso>90?"HIGH":"WATCH";BigDecimal score=BigDecimal.valueOf(Math.max(0,100-dso));
        jdbc.update("DELETE FROM customer_credit_profile WHERE tenant_id=? AND customer_id=?",tenant(),customerId);Long id=IdWorker.getId();
        jdbc.update("INSERT INTO customer_credit_profile(id,tenant_id,customer_id,credit_limit,risk_level,dso_days,overdue_amount,score,formula_version,refreshed_at) VALUES(?,?,?,0,?,?,?,?, 'CUSTOMER_CREDIT_V1',CURRENT_TIMESTAMP)",id,tenant(),customerId,risk,dso,overdue,score);return one("SELECT * FROM customer_credit_profile WHERE id=?",id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> enqueueIntegration(RevenueIntegrationRequest request){
        Map<String,Object> existing=one("SELECT * FROM revenue_external_sync WHERE tenant_id=? AND endpoint_id=? AND idempotency_key=?",tenant(),request.endpointId(),request.idempotencyKey());if(existing!=null)return existing;
        if(one("SELECT id FROM finance_integration_endpoint WHERE id=? AND tenant_id=? AND enabled_flag=1",request.endpointId(),tenant())==null)throw error("REVENUE_ENDPOINT_NOT_FOUND","收入集成端点不存在或已停用");
        Long messageId=IdWorker.getId();jdbc.update("INSERT INTO finance_integration_message(id,tenant_id,endpoint_id,direction,message_type,business_type,business_id,idempotency_key,status,payload_json,retry_count,created_at) VALUES(?,?,?,'OUTBOUND',?,?,?,?, 'PENDING',?,0,CURRENT_TIMESTAMP)",messageId,tenant(),request.endpointId(),request.messageType(),request.businessType(),request.businessId(),request.idempotencyKey(),json(request.payload()));
        Long id=IdWorker.getId();jdbc.update("INSERT INTO revenue_external_sync(id,tenant_id,endpoint_id,business_type,business_id,message_id,sync_status,idempotency_key,created_at) VALUES(?,?,?,?,?,?,'PENDING',?,CURRENT_TIMESTAMP)",id,tenant(),request.endpointId(),request.businessType(),request.businessId(),messageId,request.idempotencyKey());return one("SELECT * FROM revenue_external_sync WHERE id=?",id);
    }

    public List<Map<String,Object>> auditEvents(String type,Long businessId){return jdbc.queryForList("SELECT * FROM revenue_audit_event WHERE tenant_id=? AND (? IS NULL OR business_type=?) AND (? IS NULL OR business_id=?) ORDER BY event_at DESC,id DESC",tenant(),type,type,businessId,businessId);}

    public byte[] exportAudit(Long projectId){List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.receivable_code,r.original_amount,r.collected_amount,r.outstanding_amount,r.due_date,r.status,c.collection_code,c.external_txn_no,c.amount collection_amount FROM account_receivable r LEFT JOIN collection_allocation a ON a.receivable_id=r.id AND a.allocation_type='COLLECTION' LEFT JOIN collection_record c ON c.id=a.collection_id WHERE r.tenant_id=? AND r.project_id=? AND r.deleted_flag=0 ORDER BY r.due_date,r.id",tenant(),projectId);StringBuilder csv=new StringBuilder("receivableCode,original,collected,outstanding,dueDate,status,collectionCode,externalTxnNo,collectionAmount\r\n");for(Map<String,Object>row:rows){csv.append(csv(row.get("receivable_code"))).append(',').append(csv(row.get("original_amount"))).append(',').append(csv(row.get("collected_amount"))).append(',').append(csv(row.get("outstanding_amount"))).append(',').append(csv(row.get("due_date"))).append(',').append(csv(row.get("status"))).append(',').append(csv(row.get("collection_code"))).append(',').append(csv(row.get("external_txn_no"))).append(',').append(csv(row.get("collection_amount"))).append("\r\n");}return csv.toString().getBytes(StandardCharsets.UTF_8);}

    private BigDecimal agingBucket(Long projectId,int min,int max){BigDecimal total=BigDecimal.ZERO;for(Map<String,Object>row:jdbc.queryForList("SELECT due_date,outstanding_amount FROM account_receivable WHERE tenant_id=? AND project_id=? AND deleted_flag=0 AND outstanding_amount>0",tenant(),projectId)){long days=ChronoUnit.DAYS.between(localDate(row.get("due_date")),LocalDate.now());if(days>=min&&days<=max)total=total.add(decimal(row.get("outstanding_amount")));}return total.setScale(2,RoundingMode.HALF_UP);}
    private int insertIssues(Long runId,String query){List<Map<String,Object>>rows=jdbc.queryForList(query,tenant());for(Map<String,Object>r:rows)jdbc.update("INSERT INTO revenue_reconciliation_issue(id,tenant_id,run_id,dimension_type,business_id,issue_code,expected_amount,actual_amount,status,detail,created_at) VALUES(?,?,?,?,?,?,?,?,'OPEN',?,CURRENT_TIMESTAMP)",IdWorker.getId(),tenant(),runId,r.get("dimension_type"),r.get("business_id"),r.get("issue_code"),r.get("expected_amount"),r.get("actual_amount"),r.get("detail"));return rows.size();}
    private void audit(String event,String type,Long businessId,Long projectId,Object payload){String body=json(payload);jdbc.update("INSERT INTO revenue_audit_event(id,tenant_id,event_type,business_type,business_id,project_id,operator_id,event_at,archive_bucket,payload_json,payload_hash) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,'HOT',?,?)",IdWorker.getId(),tenant(),event,type,businessId,projectId,user(),body,sha256(body));}
    private String json(Object value){if(value==null)return null;try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){throw error("REVENUE_JSON_INVALID","收入业务数据无法序列化");}}
    private String sha256(String value){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(hash);}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> one(String sql,Object...args){try{return jdbc.queryForMap(sql,args);}catch(EmptyResultDataAccessException e){return null;}}
    private Long tenant(){return UserContext.getCurrentTenantId();}private Long user(){return UserContext.getCurrentUserId();}
    private LocalDate localDate(Object value){if(value instanceof LocalDate d)return d;if(value instanceof java.sql.Date d)return d.toLocalDate();return LocalDate.parse(value.toString());}
    private BigDecimal money(BigDecimal value){if(value==null||value.signum()<0)throw error("REVENUE_AMOUNT_INVALID","金额不能为空或为负数");return value.setScale(2,RoundingMode.HALF_UP);}private BigDecimal decimal(Object value){return value==null?BigDecimal.ZERO.setScale(2):new BigDecimal(value.toString()).setScale(2,RoundingMode.HALF_UP);}private Long longValue(Object value){return value==null?null:((Number)value).longValue();}
    private BusinessException error(String code,String message){return new BusinessException(code,message);}private String csv(Object value){if(value==null)return "";String s=value.toString();if(s.stripLeading().matches("^[=+\\-@].*"))s="'"+s;return '"'+s.replace("\"","\"\"")+'"';}
}
