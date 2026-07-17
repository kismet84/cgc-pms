package com.cgcpms.financeops.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.financeops.dto.FinanceOperationsModels.*;
import com.cgcpms.payment.dto.PaymentFailureRequest;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.service.PayRecordService;
import com.cgcpms.payment.service.PaymentReversalService;
import com.cgcpms.revenue.dto.RevenueOperationsModels.AmountAllocation;
import com.cgcpms.revenue.dto.RevenueOperationsModels.CollectionRequest;
import com.cgcpms.revenue.service.RevenueOperationsService;
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
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinanceIntegrationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PayRecordService payRecordService;
    private final PaymentReversalService paymentReversalService;
    private final RevenueOperationsService revenueOperationsService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createEndpoint(IntegrationEndpointRequest request) {
        String type = request.endpointType().trim().toUpperCase();
        if (!Set.of("BANK", "E_INVOICE", "ERP", "GENERAL_LEDGER", "TAX").contains(type)) {
            throw error("INTEGRATION_ENDPOINT_TYPE_INVALID", "不支持的财务集成端点类型");
        }
        Long id = IdWorker.getId();
        try {
            jdbc.update("INSERT INTO finance_integration_endpoint(id,tenant_id,endpoint_type,endpoint_code,endpoint_name,base_url,credential_ref,callback_secret_hash,enabled_flag,config_json,version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,1,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    id, tenant(), type, request.endpointCode().trim(), request.endpointName().trim(), blank(request.baseUrl()),
                    blank(request.credentialRef()), request.callbackSecret()==null?null:sha256(request.callbackSecret()), json(request.config()));
        } catch (DuplicateKeyException e) { throw error("INTEGRATION_ENDPOINT_CODE_DUPLICATE", "集成端点编码已存在"); }
        return endpoint(id);
    }

    public List<Map<String,Object>> endpoints() {
        return jdbc.queryForList("SELECT id,endpoint_type,endpoint_code,endpoint_name,base_url,credential_ref,enabled_flag,config_json,version,created_at,updated_at FROM finance_integration_endpoint WHERE tenant_id=? ORDER BY endpoint_type,endpoint_code", tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> enqueue(IntegrationMessageRequest request) {
        Map<String,Object> endpoint = requireEndpoint(request.endpointId());
        Map<String,Object> existing = one("SELECT * FROM finance_integration_message WHERE tenant_id=? AND endpoint_id=? AND direction='OUTBOUND' AND idempotency_key=?",
                tenant(), request.endpointId(), request.idempotencyKey());
        if (existing != null) return existing;
        Long id = IdWorker.getId();
        jdbc.update("INSERT INTO finance_integration_message(id,tenant_id,endpoint_id,direction,message_type,business_type,business_id,idempotency_key,status,payload_json,retry_count,next_retry_at,created_at) VALUES(?,?,?,'OUTBOUND',?,?,?,?, 'PENDING',?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, tenant(), request.endpointId(), request.messageType().trim().toUpperCase(), request.businessType().trim().toUpperCase(),
                request.businessId(), request.idempotencyKey().trim(), json(request.payload()));
        return message(id);
    }

    /** 出站派发由受控连接器获取；本接口只租约锁定消息，不在业务进程中任意访问外部 URL。 */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String,Object>> leaseOutbound(Long endpointId, int limit) {
        requireEndpoint(endpointId);
        int size = Math.min(100, Math.max(1, limit));
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM finance_integration_message WHERE tenant_id=? AND endpoint_id=? AND direction='OUTBOUND' AND status IN ('PENDING','RETRY') AND (next_retry_at IS NULL OR next_retry_at<=CURRENT_TIMESTAMP) ORDER BY created_at LIMIT ? FOR UPDATE",
                tenant(), endpointId, size);
        for (Map<String,Object> row : rows) jdbc.update("UPDATE finance_integration_message SET status='LEASED',next_retry_at=? WHERE id=?",
                LocalDateTime.now().plusMinutes(5), row.get("id"));
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> acknowledgeOutbound(Long messageId, boolean success, Map<String,Object> response, String errorMessage) {
        Map<String,Object> message = one("SELECT * FROM finance_integration_message WHERE id=? AND tenant_id=? FOR UPDATE", messageId, tenant());
        if (message == null || !"OUTBOUND".equals(message.get("direction"))) throw error("INTEGRATION_MESSAGE_NOT_FOUND", "出站消息不存在");
        int retry = ((Number)message.get("retry_count")).intValue() + (success ? 0 : 1);
        String status = success ? "SUCCEEDED" : retry >= 8 ? "DEAD_LETTER" : "RETRY";
        jdbc.update("UPDATE finance_integration_message SET status=?,response_json=?,retry_count=?,next_retry_at=?,processed_at=?,error_message=? WHERE id=?",
                status, json(response), retry, success||retry>=8?null:LocalDateTime.now().plusMinutes(Math.min(60, 1L<<Math.min(retry,5))),
                success||retry>=8?LocalDateTime.now():null, blank(errorMessage), messageId);
        return message(messageId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> acceptCallback(String endpointCode, String callbackSecret, IntegrationCallbackRequest request) {
        Map<String,Object> endpoint = one("SELECT * FROM finance_integration_endpoint WHERE tenant_id=? AND endpoint_code=? AND enabled_flag=1 FOR UPDATE",
                tenant(), endpointCode);
        if (endpoint == null) throw error("INTEGRATION_ENDPOINT_NOT_FOUND", "集成端点不存在或已停用");
        if (endpoint.get("callback_secret_hash") == null || callbackSecret == null
                || !constantTimeEquals(String.valueOf(endpoint.get("callback_secret_hash")), sha256(callbackSecret))) {
            throw error("INTEGRATION_CALLBACK_SIGNATURE_INVALID", "回调凭证校验失败");
        }
        Map<String,Object> existing = one("SELECT * FROM finance_integration_message WHERE tenant_id=? AND endpoint_id=? AND direction='INBOUND' AND idempotency_key=?",
                tenant(), endpoint.get("id"), request.idempotencyKey());
        if (existing != null) return existing;
        Long id=IdWorker.getId();
        jdbc.update("INSERT INTO finance_integration_message(id,tenant_id,endpoint_id,direction,message_type,business_type,business_id,idempotency_key,status,payload_json,retry_count,processed_at,created_at) VALUES(?,?,?,'INBOUND',?,?,?,?,'SUCCEEDED',?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id,tenant(),endpoint.get("id"),request.messageType().trim().toUpperCase(),blankUpper(request.businessType()),request.businessId(),request.idempotencyKey().trim(),json(request.payload()));
        applyInbound(String.valueOf(endpoint.get("endpoint_type")), request);
        return message(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> ingestBankReceipt(BankReceiptRequest request) {
        Map<String,Object> endpoint=requireEndpoint(request.endpointId());
        if(!"BANK".equals(endpoint.get("endpoint_type")))throw error("BANK_ENDPOINT_REQUIRED","银行回单必须来自 BANK 类型端点");
        String direction=request.direction().trim().toUpperCase();
        if(!Set.of("IN","OUT").contains(direction))throw error("BANK_RECEIPT_DIRECTION_INVALID","银行回单方向仅支持 IN 或 OUT");
        validateIncomingContext(request,direction);
        Map<String,Object> old=one("SELECT * FROM bank_receipt WHERE tenant_id=? AND endpoint_id=? AND bank_txn_no=?",tenant(),request.endpointId(),request.bankTxnNo());
        if(old!=null){
            if("IN".equals(direction)&&request.projectId()!=null){
                jdbc.update("UPDATE bank_receipt SET project_id=COALESCE(project_id,?),contract_id=COALESCE(contract_id,?),customer_id=COALESCE(customer_id,?),fund_account_id=COALESCE(fund_account_id,?),allocation_json=COALESCE(allocation_json,?) WHERE id=? AND tenant_id=? AND match_status='UNMATCHED'",
                        request.projectId(),request.contractId(),request.customerId(),request.fundAccountId(),json(request.allocations()),old.get("id"),tenant());
            }
            autoMatchReceipt(longValue(old.get("id")));return one("SELECT * FROM bank_receipt WHERE id=? AND tenant_id=?",old.get("id"),tenant());
        }
        Long id=IdWorker.getId();
        jdbc.update("INSERT INTO bank_receipt(id,tenant_id,endpoint_id,bank_txn_no,account_no_masked,transaction_time,direction,amount,counterparty_name,purpose_text,project_id,contract_id,customer_id,fund_account_id,allocation_json,match_status,raw_payload_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'UNMATCHED',?,CURRENT_TIMESTAMP)",id,tenant(),request.endpointId(),request.bankTxnNo().trim(),blank(request.accountNoMasked()),request.transactionTime(),direction,money(request.amount()),blank(request.counterpartyName()),blank(request.purposeText()),request.projectId(),request.contractId(),request.customerId(),request.fundAccountId(),json(request.allocations()),json(request.rawPayload()));
        autoMatchReceipt(id);
        return one("SELECT * FROM bank_receipt WHERE id=? AND tenant_id=?",id,tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> autoMatchReceipt(Long receiptId) {
        Map<String,Object> receipt=one("SELECT * FROM bank_receipt WHERE id=? AND tenant_id=? FOR UPDATE",receiptId,tenant());
        if(receipt==null)throw error("BANK_RECEIPT_NOT_FOUND","银行回单不存在");
        if(!"UNMATCHED".equals(receipt.get("match_status")))return receipt;
        if("IN".equals(receipt.get("direction"))){
            if(receipt.get("project_id")==null)return receipt;
            List<AmountAllocation> allocations=loadReceiptAllocations(receipt);
            Map<String,Object> collection=revenueOperationsService.createCollection(new CollectionRequest(
                    longValue(receipt.get("project_id")),longValue(receipt.get("contract_id")),
                    longValue(receipt.get("customer_id")),longValue(receipt.get("fund_account_id")),
                    String.valueOf(receipt.get("bank_txn_no")),dateTime(receipt.get("transaction_time")),
                    decimal(receipt.get("amount")),requiredReceiptPayer(receipt),1,allocations,
                    "银行回单自动入账："+Objects.toString(receipt.get("purpose_text"),"")));
            Map<String,Object> journal=one("SELECT id FROM cash_journal_entry WHERE tenant_id=? AND collection_record_id=? AND deleted_flag=0",tenant(),collection.get("id"));
            jdbc.update("UPDATE bank_receipt SET match_status='MATCHED',collection_record_id=?,cash_journal_id=?,confidence=1.0000,matched_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                    collection.get("id"),journal==null?null:journal.get("id"),receiptId,tenant());
            return one("SELECT * FROM bank_receipt WHERE id=? AND tenant_id=?",receiptId,tenant());
        }
        Map<String,Object> pay=one("SELECT id FROM pay_record WHERE tenant_id=? AND external_txn_no=? AND pay_status='SUCCESS' AND deleted_flag=0",tenant(),receipt.get("bank_txn_no"));
        BigDecimal confidence=new BigDecimal("1.0000");
        if(pay==null){
            LocalDateTime transactionTime=dateTime(receipt.get("transaction_time"));
            List<Map<String,Object>> candidates=jdbc.queryForList("SELECT id FROM pay_record WHERE tenant_id=? AND pay_status='SUCCESS' AND pay_amount=? AND paid_at BETWEEN ? AND ? AND deleted_flag=0",tenant(),receipt.get("amount"),transactionTime.minusDays(1),transactionTime.plusDays(1));
            if(candidates.size()==1){pay=candidates.getFirst();confidence=new BigDecimal("0.8000");}
        }
        if(pay!=null){Map<String,Object>journal=one("SELECT id FROM cash_journal_entry WHERE tenant_id=? AND pay_record_id=?",tenant(),pay.get("id"));jdbc.update("UPDATE bank_receipt SET match_status='MATCHED',pay_record_id=?,cash_journal_id=?,confidence=?,matched_at=CURRENT_TIMESTAMP WHERE id=?",pay.get("id"),journal==null?null:journal.get("id"),confidence,receiptId);}
        return one("SELECT * FROM bank_receipt WHERE id=?",receiptId);
    }

    public List<Map<String,Object>> bankReceipts(String status){return jdbc.queryForList("SELECT * FROM bank_receipt WHERE tenant_id=? AND (? IS NULL OR match_status=?) ORDER BY transaction_time DESC",tenant(),status,status);}

    public Map<String,Object> traceBankReceipt(Long receiptId){
        Map<String,Object> receipt=one("SELECT * FROM bank_receipt WHERE id=? AND tenant_id=?",receiptId,tenant());
        if(receipt==null)throw error("BANK_RECEIPT_NOT_FOUND","银行回单不存在");
        Map<String,Object> result=new LinkedHashMap<>();result.put("bankReceipt",receipt);
        if(receipt.get("collection_record_id")!=null){
            Long collectionId=longValue(receipt.get("collection_record_id"));
            result.put("collection",one("SELECT * FROM collection_record WHERE id=? AND tenant_id=? AND deleted_flag=0",collectionId,tenant()));
            result.put("cashJournal",one("SELECT * FROM cash_journal_entry WHERE collection_record_id=? AND tenant_id=? AND deleted_flag=0",collectionId,tenant()));
            result.put("accountingEntries",jdbc.queryForList("SELECT * FROM accounting_entry WHERE collection_record_id=? AND tenant_id=? AND deleted_flag=0 ORDER BY id",collectionId,tenant()));
            result.put("reversal",one("SELECT * FROM collection_reversal WHERE collection_id=? AND tenant_id=?",collectionId,tenant()));
        }else if(receipt.get("pay_record_id")!=null){
            result.put("payRecord",one("SELECT * FROM pay_record WHERE id=? AND tenant_id=? AND deleted_flag=0",receipt.get("pay_record_id"),tenant()));
            result.put("cashJournal",receipt.get("cash_journal_id")==null?null:one("SELECT * FROM cash_journal_entry WHERE id=? AND tenant_id=? AND deleted_flag=0",receipt.get("cash_journal_id"),tenant()));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createForecast(CashForecastRequest request){
        if(request.projectId()!=null&&one("SELECT id FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0",request.projectId(),tenant())==null)throw error("PROJECT_NOT_FOUND","预测项目不存在");
        Long id=IdWorker.getId();jdbc.update("INSERT INTO cash_forecast(id,tenant_id,project_id,forecast_date,scenario,inflow_amount,outflow_amount,financing_amount,source_type,source_id,confidence,status,version,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',0,?,CURRENT_TIMESTAMP)",id,tenant(),request.projectId(),request.forecastDate(),request.scenario().trim().toUpperCase(),money(request.inflowAmount()),money(request.outflowAmount()),money(request.financingAmount()),request.sourceType().trim().toUpperCase(),request.sourceId(),request.confidence()==null?BigDecimal.ONE:request.confidence(),user());return one("SELECT * FROM cash_forecast WHERE id=?",id);
    }

    public Map<String,Object> forecastSummary(String scenario, LocalDate from, LocalDate to){
        List<Map<String,Object>>rows=jdbc.queryForList("SELECT forecast_date,SUM(inflow_amount) inflow,SUM(outflow_amount) outflow,SUM(financing_amount) financing FROM cash_forecast WHERE tenant_id=? AND status='ACTIVE' AND scenario=? AND forecast_date BETWEEN ? AND ? GROUP BY forecast_date ORDER BY forecast_date",tenant(),scenario,from,to);
        BigDecimal balance=BigDecimal.ZERO;for(Map<String,Object>r:rows){balance=balance.add(decimal(r.get("inflow"))).add(decimal(r.get("financing"))).subtract(decimal(r.get("outflow")));r.put("rollingBalance",balance);}
        return Map.of("scenario",scenario,"from",from,"to",to,"rows",rows,"endingBalance",balance);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createFundPool(FundPoolRequest request){Long id=IdWorker.getId();try{jdbc.update("INSERT INTO fund_pool(id,tenant_id,pool_code,pool_name,currency_code,status,control_mode,version,created_at) VALUES(?,?,?,?,?,'ACTIVE',?,0,CURRENT_TIMESTAMP)",id,tenant(),request.poolCode().trim(),request.poolName().trim(),request.currencyCode()==null?"CNY":request.currencyCode().trim().toUpperCase(),request.controlMode()==null?"QUOTA":request.controlMode().trim().toUpperCase());}catch(DuplicateKeyException e){throw error("FUND_POOL_CODE_DUPLICATE","资金池编码已存在");}return one("SELECT * FROM fund_pool WHERE id=?",id);}

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> addFundPoolMember(FundPoolMemberRequest request){
        if(one("SELECT id FROM fund_pool WHERE id=? AND tenant_id=? AND status='ACTIVE'",request.poolId(),tenant())==null)throw error("FUND_POOL_NOT_ACTIVE","资金池不存在或未启用");
        if(one("SELECT id FROM fund_account WHERE id=? AND tenant_id=?",request.fundAccountId(),tenant())==null)throw error("FUND_ACCOUNT_NOT_FOUND","资金账户不存在");
        Long id=IdWorker.getId();jdbc.update("INSERT INTO fund_pool_member(id,tenant_id,pool_id,company_id,fund_account_id,quota_amount,occupied_amount,status,version,created_at) VALUES(?,?,?,?,?,?,0,'ACTIVE',0,CURRENT_TIMESTAMP)",id,tenant(),request.poolId(),request.companyId(),request.fundAccountId(),money(request.quotaAmount()));return one("SELECT * FROM fund_pool_member WHERE id=?",id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> transferFundPool(FundPoolTransferRequest request){
        Map<String,Object>existing=one("SELECT * FROM fund_pool_transaction WHERE tenant_id=? AND idempotency_key=?",tenant(),request.idempotencyKey());if(existing!=null)return existing;
        if(Objects.equals(request.fromMemberId(),request.toMemberId()))throw error("FUND_POOL_SAME_MEMBER","资金池调拨双方不能相同");
        Long first=Math.min(request.fromMemberId(),request.toMemberId()),second=Math.max(request.fromMemberId(),request.toMemberId());Map<String,Object>a=one("SELECT * FROM fund_pool_member WHERE id=? AND tenant_id=? FOR UPDATE",first,tenant()),b=one("SELECT * FROM fund_pool_member WHERE id=? AND tenant_id=? FOR UPDATE",second,tenant());if(a==null||b==null)throw error("FUND_POOL_MEMBER_NOT_FOUND","资金池成员不存在");Map<String,Object>from=Objects.equals(first,request.fromMemberId())?a:b,to=Objects.equals(first,request.fromMemberId())?b:a;if(!Objects.equals(longValue(from.get("pool_id")),request.poolId())||!Objects.equals(longValue(to.get("pool_id")),request.poolId()))throw error("FUND_POOL_MEMBER_MISMATCH","资金池成员不属于同一资金池");BigDecimal amount=money(request.amount()),available=decimal(from.get("quota_amount")).subtract(decimal(from.get("occupied_amount")));if(amount.signum()<=0||available.compareTo(amount)<0)throw error("FUND_POOL_QUOTA_INSUFFICIENT","转出成员可用额度不足");jdbc.update("UPDATE fund_pool_member SET occupied_amount=occupied_amount+?,version=version+1 WHERE id=?",amount,request.fromMemberId());jdbc.update("UPDATE fund_pool_member SET quota_amount=quota_amount+?,version=version+1 WHERE id=?",amount,request.toMemberId());Long id=IdWorker.getId();jdbc.update("INSERT INTO fund_pool_transaction(id,tenant_id,pool_id,from_member_id,to_member_id,transaction_type,amount,status,idempotency_key,external_txn_no,occurred_at,created_by,created_at) VALUES(?,?,?,?,?,'TRANSFER',?,'COMPLETED',?,?,?,?,CURRENT_TIMESTAMP)",id,tenant(),request.poolId(),request.fromMemberId(),request.toMemberId(),amount,request.idempotencyKey(),blank(request.externalTxnNo()),request.occurredAt(),user());return one("SELECT * FROM fund_pool_transaction WHERE id=?",id);
    }

    public Map<String,Object> fundPoolView(Long poolId){Map<String,Object>pool=one("SELECT * FROM fund_pool WHERE id=? AND tenant_id=?",poolId,tenant());if(pool==null)throw error("FUND_POOL_NOT_FOUND","资金池不存在");return Map.of("pool",pool,"members",jdbc.queryForList("SELECT * FROM fund_pool_member WHERE tenant_id=? AND pool_id=? ORDER BY company_id",tenant(),poolId),"transactions",jdbc.queryForList("SELECT * FROM fund_pool_transaction WHERE tenant_id=? AND pool_id=? ORDER BY occurred_at DESC LIMIT 200",tenant(),poolId));}

    private void applyInbound(String endpointType,IntegrationCallbackRequest request){
        if("BANK".equals(endpointType)&&"PAYMENT_STATUS".equalsIgnoreCase(request.messageType())){
            String status=String.valueOf(request.payload().getOrDefault("status","FAILED")).toUpperCase();
            if("SUCCESS".equals(status)){
                PayRecord record=new PayRecord();record.setPayApplicationId(requiredLong(request.payload(),"payApplicationId"));record.setPayAmount(requiredMoney(request.payload(),"amount"));record.setFundAccountId(requiredLong(request.payload(),"fundAccountId"));record.setPaidAt(requiredDateTime(request.payload(),"paidAt"));record.setPayMethod(String.valueOf(request.payload().getOrDefault("payMethod","BANK_TRANSFER")));record.setExternalTxnNo(requiredText(request.payload(),"externalTxnNo"));payRecordService.writeback(record);
            }else if("FAILED".equals(status)){
                PaymentFailureRequest failed=new PaymentFailureRequest();failed.setPayApplicationId(requiredLong(request.payload(),"payApplicationId"));failed.setPayAmount(requiredMoney(request.payload(),"amount"));failed.setFundAccountId(optionalLong(request.payload().get("fundAccountId")));failed.setAttemptedAt(requiredDateTime(request.payload(),"attemptedAt"));failed.setPayMethod(String.valueOf(request.payload().getOrDefault("payMethod","BANK_TRANSFER")));failed.setExternalTxnNo(requiredText(request.payload(),"externalTxnNo"));failed.setFailureReason(String.valueOf(request.payload().getOrDefault("failureReason","银行付款失败")));paymentReversalService.recordFailure(failed);
            }else throw error("BANK_PAYMENT_STATUS_INVALID","银行付款回调状态仅支持 SUCCESS 或 FAILED");
        }
        if("E_INVOICE".equals(endpointType)&&request.businessId()!=null){String result=String.valueOf(request.payload().getOrDefault("verifyStatus","PENDING")).toUpperCase();if(!Set.of("VERIFIED","REJECTED","PENDING").contains(result))throw error("E_INVOICE_RESULT_INVALID","电子发票验真结果不合法");jdbc.update("UPDATE pay_invoice SET verify_status=?,exception_status=?,exception_reason=?,version=version+1 WHERE id=? AND tenant_id=? AND deleted_flag=0",result,"REJECTED".equals(result)?"SUSPECT":"NORMAL",request.payload().get("reason"),request.businessId(),tenant());}
        if(Set.of("ERP","GENERAL_LEDGER","TAX").contains(endpointType)&&request.businessId()!=null&&"ACCOUNTING_ENTRY".equalsIgnoreCase(request.businessType())){jdbc.update("UPDATE accounting_entry SET external_sync_status=?,external_sync_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND deleted_flag=0",String.valueOf(request.payload().getOrDefault("status","ACKNOWLEDGED")),request.businessId(),tenant());}
    }
    private void validateIncomingContext(BankReceiptRequest request,String direction){
        boolean any=request.projectId()!=null||request.contractId()!=null||request.customerId()!=null||request.fundAccountId()!=null;
        boolean all=request.projectId()!=null&&request.contractId()!=null&&request.customerId()!=null&&request.fundAccountId()!=null;
        if("OUT".equals(direction)&&(any||(request.allocations()!=null&&!request.allocations().isEmpty())))
            throw error("BANK_RECEIPT_CONTEXT_DIRECTION_INVALID","出账回单不能携带收款业务上下文");
        if("IN".equals(direction)&&any&&!all)
            throw error("BANK_RECEIPT_CONTEXT_INCOMPLETE","自动入账必须同时提供项目、合同、客户和收款账户");
        if("IN".equals(direction)&&all&&!org.springframework.util.StringUtils.hasText(request.counterpartyName()))
            throw error("BANK_RECEIPT_PAYER_REQUIRED","自动入账必须提供付款方名称");
        if("IN".equals(direction)&&!all&&request.allocations()!=null&&!request.allocations().isEmpty())
            throw error("BANK_RECEIPT_ALLOCATION_CONTEXT_REQUIRED","应收分配必须同时提供完整收款业务上下文");
    }
    private List<AmountAllocation> loadReceiptAllocations(Map<String,Object> receipt){
        String jsonValue=jdbc.queryForObject("SELECT allocation_json FROM bank_receipt WHERE id=? AND tenant_id=?",String.class,receipt.get("id"),tenant());
        if(jsonValue==null||jsonValue.isBlank())return List.of();
        try{
            BankReceiptAllocation[] values=objectMapper.readValue(jsonValue,BankReceiptAllocation[].class);
            if(values==null)return List.of();
            return Arrays.stream(values).map(v->new AmountAllocation(v.receivableId(),v.amount())).toList();
        }catch(Exception e){throw error("BANK_RECEIPT_ALLOCATION_INVALID","银行回单应收分配数据无法解析");}
    }
    private String requiredReceiptPayer(Map<String,Object> receipt){
        String payer=blank(Objects.toString(receipt.get("counterparty_name"),null));
        if(payer==null)throw error("BANK_RECEIPT_PAYER_REQUIRED","自动入账必须提供付款方名称");
        return payer;
    }
    private Map<String,Object>requireEndpoint(Long id){Map<String,Object>v=one("SELECT * FROM finance_integration_endpoint WHERE id=? AND tenant_id=? AND enabled_flag=1",id,tenant());if(v==null)throw error("INTEGRATION_ENDPOINT_NOT_FOUND","集成端点不存在或已停用");return v;}
    private Map<String,Object>endpoint(Long id){return one("SELECT id,endpoint_type,endpoint_code,endpoint_name,base_url,credential_ref,enabled_flag,config_json,version,created_at,updated_at FROM finance_integration_endpoint WHERE id=? AND tenant_id=?",id,tenant());}
    private Map<String,Object>message(Long id){return one("SELECT * FROM finance_integration_message WHERE id=? AND tenant_id=?",id,tenant());}
    private Map<String,Object>one(String sql,Object...args){List<Map<String,Object>>r=jdbc.queryForList(sql,args);return r.isEmpty()?null:r.getFirst();}
    private Long tenant(){Long v=UserContext.getCurrentTenantId();if(v==null)throw error("TENANT_CONTEXT_REQUIRED","缺少租户上下文");return v;}private Long user(){return UserContext.getCurrentUserId();}
    private String json(Object v){try{return objectMapper.writeValueAsString(v==null?Map.of():v);}catch(JsonProcessingException e){throw error("FINANCE_JSON_ERROR","集成数据序列化失败");}}
    private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean constantTimeEquals(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private static BigDecimal money(BigDecimal v){return(v==null?BigDecimal.ZERO:v).setScale(2,RoundingMode.HALF_UP);}private static BigDecimal decimal(Object v){return money(v==null?BigDecimal.ZERO:new BigDecimal(v.toString()));}private static Long longValue(Object v){return v==null?null:Long.valueOf(v.toString());}
    private static String blank(String v){return v==null||v.isBlank()?null:v.trim();}private static String blankUpper(String v){String x=blank(v);return x==null?null:x.toUpperCase();}private static BusinessException error(String c,String m){return new BusinessException(c,m);}
    private static String requiredText(Map<String,Object> payload,String key){Object v=payload.get(key);if(v==null||v.toString().isBlank())throw error("INTEGRATION_PAYLOAD_FIELD_REQUIRED","集成报文缺少字段: "+key);return v.toString().trim();}
    private static Long requiredLong(Map<String,Object> payload,String key){try{return Long.valueOf(requiredText(payload,key));}catch(NumberFormatException e){throw error("INTEGRATION_PAYLOAD_FIELD_INVALID","集成报文字段类型错误: "+key);}}
    private static Long optionalLong(Object value){return value==null?null:Long.valueOf(value.toString());}
    private static BigDecimal requiredMoney(Map<String,Object> payload,String key){try{BigDecimal v=money(new BigDecimal(requiredText(payload,key)));if(v.signum()<=0)throw new NumberFormatException();return v;}catch(NumberFormatException e){throw error("INTEGRATION_PAYLOAD_FIELD_INVALID","集成报文金额字段错误: "+key);}}
    private static LocalDateTime requiredDateTime(Map<String,Object> payload,String key){try{return LocalDateTime.parse(requiredText(payload,key).replace(' ','T'));}catch(Exception e){throw error("INTEGRATION_PAYLOAD_FIELD_INVALID","集成报文时间字段错误: "+key);}}
    private static LocalDateTime dateTime(Object value){if(value instanceof LocalDateTime v)return v;if(value instanceof java.sql.Timestamp v)return v.toLocalDateTime();return LocalDateTime.parse(String.valueOf(value).replace(' ','T'));}
}
