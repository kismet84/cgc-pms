package com.cgcpms.financeops.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.financeops.dto.FinanceOperationsModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinanceAnalyticsService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> rebuildSnapshot(Long projectId, LocalDate snapshotDate, String mode) {
        Long tenant = tenant();
        LocalDate date = snapshotDate == null ? LocalDate.now() : snapshotDate;
        if (one("SELECT id FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0", projectId, tenant) == null) {
            throw error("PROJECT_NOT_FOUND", "项目不存在");
        }
        Map<String,Object> fact = jdbc.queryForMap("""
                SELECT
                 COALESCE((SELECT SUM(current_amount) FROM ct_contract WHERE tenant_id=? AND project_id=? AND deleted_flag=0),0) contract_amount,
                 COALESCE((SELECT SUM(GREATEST(apply_amount-actual_pay_amount,0)) FROM pay_application WHERE tenant_id=? AND project_id=? AND approval_status='APPROVED' AND deleted_flag=0),0) approved_unpaid,
                 COALESCE((SELECT SUM(pay_amount) FROM pay_record WHERE tenant_id=? AND project_id=? AND pay_status='SUCCESS' AND deleted_flag=0),0) paid_amount,
                 COALESCE((SELECT SUM(total_amount) FROM project_budget WHERE tenant_id=? AND project_id=? AND status='ACTIVE' AND active_flag=1 AND deleted_flag=0),0) budget_amount,
                 COALESCE((SELECT SUM(l.reserved_amount) FROM project_budget_line l JOIN project_budget b ON b.id=l.budget_id WHERE l.tenant_id=? AND l.project_id=? AND b.status='ACTIVE' AND b.active_flag=1 AND l.deleted_flag=0),0) budget_reserved,
                 COALESCE((SELECT SUM(l.consumed_amount) FROM project_budget_line l JOIN project_budget b ON b.id=l.budget_id WHERE l.tenant_id=? AND l.project_id=? AND b.status='ACTIVE' AND b.active_flag=1 AND l.deleted_flag=0),0) budget_consumed,
                 COALESCE((SELECT SUM(amount) FROM cash_journal_entry WHERE tenant_id=? AND project_id=? AND status='ARCHIVED' AND direction='IN'),0) cash_inflow,
                 COALESCE((SELECT SUM(amount) FROM cash_journal_entry WHERE tenant_id=? AND project_id=? AND status='ARCHIVED' AND direction='OUT'),0) cash_outflow,
                 COALESCE((SELECT SUM(actual_cost) FROM cost_summary WHERE tenant_id=? AND project_id=? AND deleted_flag=0),0) actual_cost
                """, tenant, projectId, tenant, projectId, tenant, projectId, tenant, projectId,
                tenant, projectId, tenant, projectId, tenant, projectId, tenant, projectId, tenant, projectId);
        BigDecimal profit = decimal(fact.get("cash_inflow")).subtract(decimal(fact.get("actual_cost")));
        Long id = IdWorker.getId();
        jdbc.update("""
                INSERT INTO dashboard_finance_snapshot(id,tenant_id,project_id,snapshot_date,formula_version,
                 contract_amount,approved_unpaid_amount,paid_amount,budget_amount,budget_reserved,budget_consumed,
                 cash_inflow,cash_outflow,actual_cost,profit_amount,refreshed_at,refresh_mode)
                VALUES(?,?,?,?,'FINANCE_CLOSED_LOOP_V1',?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)
                ON DUPLICATE KEY UPDATE contract_amount=VALUES(contract_amount),approved_unpaid_amount=VALUES(approved_unpaid_amount),
                 paid_amount=VALUES(paid_amount),budget_amount=VALUES(budget_amount),budget_reserved=VALUES(budget_reserved),
                 budget_consumed=VALUES(budget_consumed),cash_inflow=VALUES(cash_inflow),cash_outflow=VALUES(cash_outflow),
                 actual_cost=VALUES(actual_cost),profit_amount=VALUES(profit_amount),refreshed_at=CURRENT_TIMESTAMP,refresh_mode=VALUES(refresh_mode)
                """, id, tenant, projectId, date, decimal(fact.get("contract_amount")), decimal(fact.get("approved_unpaid")),
                decimal(fact.get("paid_amount")), decimal(fact.get("budget_amount")), decimal(fact.get("budget_reserved")),
                decimal(fact.get("budget_consumed")), decimal(fact.get("cash_inflow")), decimal(fact.get("cash_outflow")),
                decimal(fact.get("actual_cost")), profit, mode == null ? "FULL_REBUILD" : mode);
        audit("SNAPSHOT_REFRESH", "PROJECT", projectId, projectId, Map.of("date", date, "mode", mode == null ? "FULL_REBUILD" : mode));
        return one("SELECT * FROM dashboard_finance_snapshot WHERE tenant_id=? AND project_id=? AND snapshot_date=?", tenant, projectId, date);
    }

    public List<Map<String,Object>> snapshots(Long projectId) {
        return jdbc.queryForList("SELECT * FROM dashboard_finance_snapshot WHERE tenant_id=? AND project_id=? ORDER BY snapshot_date DESC", tenant(), projectId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createOcrReview(OcrReviewCreateRequest request) {
        if (one("SELECT id FROM pay_invoice WHERE id=? AND tenant_id=? AND deleted_flag=0", request.invoiceId(), tenant()) == null) {
            throw error("INVOICE_NOT_FOUND", "发票不存在");
        }
        Long id = IdWorker.getId();
        String status = request.confidence().compareTo(new BigDecimal("0.9000")) >= 0 ? "AUTO_ACCEPTABLE" : "PENDING";
        jdbc.update("INSERT INTO invoice_ocr_review(id,tenant_id,invoice_id,raw_result_json,confidence,comparison_json,review_status,created_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, tenant(), request.invoiceId(), json(request.rawResult()), request.confidence(), json(request.comparison()), status);
        return one("SELECT * FROM invoice_ocr_review WHERE id=? AND tenant_id=?", id, tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> decideOcrReview(Long id, OcrReviewDecisionRequest request) {
        String decision = request.decision().trim().toUpperCase();
        if (!Set.of("APPROVED", "REJECTED", "CORRECTED").contains(decision)) throw error("OCR_REVIEW_DECISION_INVALID", "OCR复核结论不合法");
        if (jdbc.update("UPDATE invoice_ocr_review SET review_status=?,reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP,review_note=? WHERE id=? AND tenant_id=? AND review_status IN ('PENDING','AUTO_ACCEPTABLE')",
                decision, user(), request.note().trim(), id, tenant()) != 1) throw error("OCR_REVIEW_NOT_PENDING", "OCR任务不存在或已复核");
        return one("SELECT * FROM invoice_ocr_review WHERE id=? AND tenant_id=?", id, tenant());
    }

    public List<Map<String,Object>> ocrWorkbench(String status) {
        return jdbc.queryForList("""
                SELECT r.*,i.invoice_no,i.invoice_amount,i.invoice_date FROM invoice_ocr_review r
                 JOIN pay_invoice i ON i.id=r.invoice_id WHERE r.tenant_id=? AND (? IS NULL OR r.review_status=?)
                 ORDER BY r.confidence,r.created_at
                """, tenant(), status, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> previewImport(ImportPreviewRequest request) {
        String type = request.importType().trim().toUpperCase();
        if (!Set.of("BUDGET_LINE", "CONTRACT_ALLOCATION").contains(type)) throw error("IMPORT_TYPE_INVALID", "仅支持预算科目或合同分配导入");
        Map<String,Object> existing = one("SELECT * FROM finance_import_batch WHERE tenant_id=? AND import_type=? AND project_id=? AND file_hash=?",
                tenant(), type, request.projectId(), request.fileHash());
        if (existing != null) return existing;
        Long batchId = IdWorker.getId();
        int valid = 0;
        jdbc.update("INSERT INTO finance_import_batch(id,tenant_id,import_type,project_id,file_name,file_hash,status,total_rows,valid_rows,invalid_rows,created_by,created_at) VALUES(?,?,?,?,?,?,'PREVIEW',?,0,0,?,CURRENT_TIMESTAMP)",
                batchId, tenant(), type, request.projectId(), request.fileName(), request.fileHash(), request.rows().size(), user());
        for (ImportRow row : request.rows()) {
            String validation = validateImportRow(type, request.projectId(), row.values());
            boolean ok = validation == null;
            if (ok) valid++;
            Map<String,Object> diff = buildDiff(type, row.values());
            jdbc.update("INSERT INTO finance_import_row(id,tenant_id,batch_id,row_no,business_key,input_json,diff_json,validation_status,validation_message) VALUES(?,?,?,?,?,?,?,?,?)",
                    IdWorker.getId(), tenant(), batchId, row.rowNo(), businessKey(type, row.values()), json(row.values()), json(diff), ok ? "VALID" : "INVALID", validation);
        }
        int invalid = request.rows().size() - valid;
        jdbc.update("UPDATE finance_import_batch SET valid_rows=?,invalid_rows=?,diff_summary_json=? WHERE id=?",
                valid, invalid, json(Map.of("valid", valid, "invalid", invalid)), batchId);
        return one("SELECT * FROM finance_import_batch WHERE id=?", batchId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> applyImport(Long batchId) {
        Map<String,Object> batch = one("SELECT * FROM finance_import_batch WHERE id=? AND tenant_id=? FOR UPDATE", batchId, tenant());
        if (batch == null) throw error("IMPORT_BATCH_NOT_FOUND", "导入批次不存在");
        if ("APPLIED".equals(batch.get("status"))) return batch;
        if (((Number)batch.get("invalid_rows")).intValue() > 0) throw error("IMPORT_BATCH_HAS_ERRORS", "存在无效行，禁止应用导入");
        String type = String.valueOf(batch.get("import_type"));
        for (Map<String,Object> row : jdbc.queryForList("SELECT input_json FROM finance_import_row WHERE batch_id=? ORDER BY row_no", batchId)) {
            Map<String,Object> values = parseMap(String.valueOf(row.get("input_json")));
            if ("BUDGET_LINE".equals(type)) applyBudgetLine(longValue(batch.get("project_id")), values);
            else applyContractAllocation(longValue(batch.get("project_id")), values);
        }
        jdbc.update("UPDATE finance_import_batch SET status='APPLIED',applied_at=CURRENT_TIMESTAMP WHERE id=?", batchId);
        audit("IMPORT_APPLIED", "IMPORT_BATCH", batchId, longValue(batch.get("project_id")), Map.of("type", type));
        return one("SELECT * FROM finance_import_batch WHERE id=?", batchId);
    }

    public List<Map<String,Object>> importRows(Long batchId) {
        return jdbc.queryForList("SELECT * FROM finance_import_row WHERE tenant_id=? AND batch_id=? ORDER BY row_no", tenant(), batchId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createRoutingRule(RoutingRuleRequest request) {
        BigDecimal min = request.minAmount() == null ? BigDecimal.ZERO : money(request.minAmount());
        BigDecimal max = request.maxAmount();
        if (max != null && money(max).compareTo(min) < 0) throw error("ROUTING_AMOUNT_RANGE_INVALID", "审批路由金额上限不得小于下限");
        if (one("SELECT id FROM wf_template WHERE id=? AND tenant_id=? AND enabled=1 AND deleted_flag=0", request.workflowTemplateId(), tenant()) == null) {
            throw error("WORKFLOW_TEMPLATE_NOT_AVAILABLE", "审批模板不存在或未启用");
        }
        Long id = IdWorker.getId();
        String businessType=request.businessType().trim().toUpperCase();
        BigDecimal normalizedMax=max==null?null:money(max);
        String contractType=blank(request.contractType()),expenseCategory=blank(request.expenseCategory());
        int priority=request.priority()==null?100:request.priority();
        int enabled=Boolean.FALSE.equals(request.enabled())?0:1;
        String signature=routingSignature(businessType,min,normalizedMax,contractType,expenseCategory);
        try {
            jdbc.update("INSERT INTO approval_routing_rule(id,tenant_id,rule_name,business_type,min_amount,max_amount,contract_type,expense_category,workflow_template_id,priority,enabled_flag,rule_signature,active_rule_token,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",
                    id, tenant(), request.ruleName().trim(), businessType, min, normalizedMax, contractType, expenseCategory,
                    request.workflowTemplateId(), priority, enabled, signature, enabled==1?0L:id, user(), user());
        } catch (DuplicateKeyException e) {
            throw error("APPROVAL_ROUTING_DUPLICATE", "相同优先级下已存在完全相同的启用审批路由");
        }
        return one("SELECT * FROM approval_routing_rule WHERE id=?", id);
    }

    public Map<String,Object> matchRouting(RoutingMatchRequest request) {
        List<Map<String,Object>> rules = jdbc.queryForList("""
                SELECT * FROM approval_routing_rule WHERE tenant_id=? AND business_type=? AND enabled_flag=1
                 AND (min_amount IS NULL OR min_amount<=?) AND (max_amount IS NULL OR max_amount>=?)
                 AND (contract_type IS NULL OR contract_type=?) AND (expense_category IS NULL OR expense_category=?)
                 ORDER BY priority,id
                """, tenant(), request.businessType().trim().toUpperCase(), money(request.amount()), money(request.amount()),
                blank(request.contractType()), blank(request.expenseCategory()));
        if (rules.isEmpty()) throw error("APPROVAL_ROUTING_NOT_FOUND", "没有匹配的审批模板路由");
        int bestPriority=((Number)rules.getFirst().get("priority")).intValue();
        List<Map<String,Object>> best=rules.stream()
                .filter(r->((Number)r.get("priority")).intValue()==bestPriority).toList();
        long templates=best.stream().map(r->longValue(r.get("workflow_template_id"))).distinct().count();
        if(templates>1)throw error("APPROVAL_ROUTING_AMBIGUOUS","存在同优先级且指向不同模板的审批路由，请先消除冲突");
        return best.getFirst();
    }

    public List<Map<String,Object>> auditSearch(String businessType, Long businessId, LocalDateTime from, LocalDateTime to, String bucket) {
        return jdbc.queryForList("""
                SELECT * FROM finance_audit_event WHERE tenant_id=?
                 AND (? IS NULL OR business_type=?) AND (? IS NULL OR business_id=?)
                 AND (? IS NULL OR event_at>=?) AND (? IS NULL OR event_at<=?)
                 AND (? IS NULL OR archive_bucket=?) ORDER BY event_at DESC,id DESC LIMIT 1000
                """, tenant(), businessType,businessType,businessId,businessId,from,from,to,to,bucket,bucket);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> archiveAuditBefore(LocalDateTime before) {
        if (before == null || before.isAfter(LocalDateTime.now().minusMonths(6))) throw error("AUDIT_ARCHIVE_CUTOFF_INVALID", "仅允许将六个月前审计事件标记为冷数据");
        int rows = jdbc.update("UPDATE finance_audit_event SET archive_bucket='COLD' WHERE tenant_id=? AND archive_bucket='HOT' AND event_at<?", tenant(), before);
        return Map.of("archived", rows, "cutoff", before);
    }

    private String validateImportRow(String type, Long projectId, Map<String,Object> values) {
        try {
            if ("BUDGET_LINE".equals(type)) {
                Long budgetId = longValue(values.get("budgetId")); Long subjectId = longValue(values.get("costSubjectId"));
                BigDecimal amount = moneyValue(values.get("budgetAmount"));
                if (budgetId==null||subjectId==null||amount.signum()<=0) return "budgetId、costSubjectId、budgetAmount 必须完整且金额大于0";
                if (one("SELECT id FROM project_budget WHERE id=? AND tenant_id=? AND project_id=? AND status='ACTIVE' AND deleted_flag=0", budgetId, tenant(), projectId)==null) return "预算版本不存在或未生效";
                if (one("SELECT id FROM cost_subject WHERE id=? AND tenant_id=?", subjectId, tenant())==null) return "成本科目不存在";
            } else {
                Long contractId=longValue(values.get("contractId")); Long lineId=longValue(values.get("budgetLineId"));
                BigDecimal amount=moneyValue(values.get("allocatedAmount"));
                if(contractId==null||lineId==null||amount.signum()<=0)return "contractId、budgetLineId、allocatedAmount 必须完整且金额大于0";
                if(one("SELECT id FROM ct_contract WHERE id=? AND tenant_id=? AND project_id=? AND deleted_flag=0",contractId,tenant(),projectId)==null)return "合同不存在或不属于项目";
                if(one("SELECT id FROM project_budget_line WHERE id=? AND tenant_id=? AND project_id=? AND deleted_flag=0",lineId,tenant(),projectId)==null)return "预算科目不存在或不属于项目";
            }
            return null;
        } catch (RuntimeException ex) { return "字段类型不合法"; }
    }

    private Map<String,Object> buildDiff(String type, Map<String,Object> values) {
        Map<String,Object> old = "BUDGET_LINE".equals(type)
                ? one("SELECT budget_amount,reserved_amount,consumed_amount FROM project_budget_line WHERE budget_id=? AND cost_subject_id=? AND tenant_id=? AND deleted_flag=0",
                longValue(values.get("budgetId")), longValue(values.get("costSubjectId")), tenant())
                : one("SELECT allocated_amount,reserved_amount,consumed_amount FROM contract_budget_allocation WHERE contract_id=? AND budget_line_id=? AND tenant_id=? AND deleted_flag=0",
                longValue(values.get("contractId")), longValue(values.get("budgetLineId")), tenant());
        return Map.of("operation", old == null ? "INSERT" : "UPDATE", "before", old == null ? Map.of() : old, "after", values);
    }

    private void applyBudgetLine(Long projectId, Map<String,Object> v) {
        Long budgetId=longValue(v.get("budgetId")), subjectId=longValue(v.get("costSubjectId")); BigDecimal amount=moneyValue(v.get("budgetAmount"));
        Map<String,Object> old=one("SELECT * FROM project_budget_line WHERE budget_id=? AND cost_subject_id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE",budgetId,subjectId,tenant());
        if(old==null) jdbc.update("INSERT INTO project_budget_line(id,tenant_id,budget_id,project_id,cost_subject_id,budget_amount,reserved_amount,consumed_amount,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,?,?,?,?,?,0,0,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)",IdWorker.getId(),tenant(),budgetId,projectId,subjectId,amount,user(),user());
        else { BigDecimal locked=decimal(old.get("reserved_amount")).add(decimal(old.get("consumed_amount"))); if(amount.compareTo(locked)<0) throw error("IMPORT_BUDGET_BELOW_LOCKED","导入预算低于已锁定金额"); jdbc.update("UPDATE project_budget_line SET budget_amount=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",amount,user(),old.get("id")); }
        jdbc.update("UPDATE project_budget SET total_amount=(SELECT SUM(budget_amount) FROM project_budget_line WHERE budget_id=? AND deleted_flag=0),version=version+1 WHERE id=?",budgetId,budgetId);
    }

    private void applyContractAllocation(Long projectId, Map<String,Object> v) {
        Long contractId=longValue(v.get("contractId")),lineId=longValue(v.get("budgetLineId"));BigDecimal amount=moneyValue(v.get("allocatedAmount"));
        Map<String,Object> old=one("SELECT * FROM contract_budget_allocation WHERE contract_id=? AND budget_line_id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE",contractId,lineId,tenant());
        if(old==null) jdbc.update("INSERT INTO contract_budget_allocation(id,tenant_id,project_id,contract_id,budget_line_id,allocated_amount,reserved_amount,consumed_amount,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,?,?,?,?,?,0,0,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)",IdWorker.getId(),tenant(),projectId,contractId,lineId,amount,user(),user());
        else {BigDecimal locked=decimal(old.get("reserved_amount")).add(decimal(old.get("consumed_amount")));if(amount.compareTo(locked)<0)throw error("IMPORT_ALLOCATION_BELOW_LOCKED","导入合同额度低于已锁定金额");jdbc.update("UPDATE contract_budget_allocation SET allocated_amount=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",amount,user(),old.get("id"));}
    }

    private String businessKey(String type,Map<String,Object> v){return "BUDGET_LINE".equals(type)?v.get("budgetId")+":"+v.get("costSubjectId"):v.get("contractId")+":"+v.get("budgetLineId");}
    private void audit(String eventType,String businessType,Long businessId,Long projectId,Object payload){String body=json(payload);jdbc.update("INSERT INTO finance_audit_event(id,tenant_id,event_type,business_type,business_id,project_id,operator_id,event_at,archive_bucket,payload_json,payload_hash) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,'HOT',?,?)",IdWorker.getId(),tenant(),eventType,businessType,businessId,projectId,user(),body,sha256(body));}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>>r=jdbc.queryForList(sql,args);return r.isEmpty()?null:r.getFirst();}
    private Long tenant(){Long v=UserContext.getCurrentTenantId();if(v==null)throw error("TENANT_CONTEXT_REQUIRED","缺少租户上下文");return v;}
    private Long user(){return UserContext.getCurrentUserId();}
    private String json(Object v){try{return objectMapper.writeValueAsString(v==null?Map.of():v);}catch(JsonProcessingException e){throw error("FINANCE_JSON_ERROR","财务数据序列化失败");}}
    private Map<String,Object> parseMap(String v){try{return objectMapper.readValue(v,new TypeReference<>(){});}catch(JsonProcessingException e){throw error("IMPORT_ROW_JSON_INVALID","导入行数据无法解析");}}
    private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static BigDecimal money(BigDecimal v){return(v==null?BigDecimal.ZERO:v).setScale(2,RoundingMode.HALF_UP);}
    private static BigDecimal moneyValue(Object v){return money(v==null?BigDecimal.ZERO:new BigDecimal(v.toString()));}
    private static BigDecimal decimal(Object v){return moneyValue(v);}
    private static Long longValue(Object v){return v==null?null:Long.valueOf(v.toString());}
    private static String blank(String v){return v==null||v.isBlank()?null:v.trim().toUpperCase();}
    private static String routingSignature(String businessType,BigDecimal min,BigDecimal max,String contractType,String expenseCategory){
        return businessType+"|"+(min==null?"*":min.toPlainString())+"|"+(max==null?"*":max.toPlainString())+"|"+
                (contractType==null?"*":contractType)+"|"+(expenseCategory==null?"*":expenseCategory);
    }
    private static BusinessException error(String c,String m){return new BusinessException(c,m);}
}
