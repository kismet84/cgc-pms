package com.cgcpms.revenue.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.accounting.service.EntryGenerator;
import com.cgcpms.accounting.strategy.OwnerSettlementEntryGenerationStrategy;
import com.cgcpms.audit.service.MandatoryAuditService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.revenue.dto.RevenueOperationsModels.*;
import com.cgcpms.revenue.vo.RevenueOperationsVOs.*;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RevenueOperationsService {
    private final JdbcTemplate jdbc;
    private final WorkflowEngine workflowEngine;
    private final EntryGenerator entryGenerator;
    private final ProjectAccessChecker projectAccessChecker;
    private final AccountingPeriodGuard periodGuard;
    private final SysDictDataService sysDictDataService;
    private final MandatoryAuditService mandatoryAuditService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createSettlement(OwnerSettlementRequest request) {
        if (request.revenueId() == null) {
            throw error("OWNER_SETTLEMENT_SOURCE_REQUIRED", "人工业主结算必须关联已审批收入确认");
        }
        return createSettlement(request, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createMeasurementSettlement(OwnerSettlementRequest request, Long measurementId,
                                                           Long submissionId, BigDecimal reportedAmount,
                                                           BigDecimal deductedAmount) {
        Map<String,Object> source = one("""
                SELECT s.id,m.id measurement_id,s.project_id,s.contract_id,s.status
                  FROM owner_measurement_submission s
                  JOIN production_measurement m ON m.id=s.measurement_id AND m.tenant_id=s.tenant_id
                 WHERE s.id=? AND m.id=? AND s.tenant_id=?
                """, submissionId, measurementId, tenant());
        if (source == null || !"CONFIRMED".equals(source.get("status"))
                || !Objects.equals(longValue(source.get("project_id")), request.projectId())
                || !Objects.equals(longValue(source.get("contract_id")), request.contractId())) {
            throw error("OWNER_SETTLEMENT_MEASUREMENT_SOURCE_INVALID", "业主结算计量来源不存在、未核定或上下文不一致");
        }
        return createSettlement(request, measurementId, submissionId, reportedAmount, deductedAmount);
    }

    private Map<String,Object> createSettlement(OwnerSettlementRequest request, Long measurementId, Long submissionId,
                                                 BigDecimal reportedAmount, BigDecimal deductedAmount) {
        Map<String,Object> contract = requireRevenueContract(request.projectId(), request.contractId(), request.customerId());
        if (request.retentionAmount().compareTo(request.grossAmount()) > 0) {
            throw error("OWNER_SETTLEMENT_RETENTION_EXCEEDED", "保留金不能超过业主确认金额");
        }
        if (request.dueDate().isBefore(request.settlementDate())) {
            throw error("OWNER_SETTLEMENT_DUE_DATE_INVALID", "应收到期日不能早于结算日期");
        }
        if (request.revenueId() != null) {
            Map<String,Object> revenue = one("SELECT id,project_id,contract_id,approval_status FROM contract_revenue WHERE id=? AND tenant_id=? AND deleted_flag=0",
                    request.revenueId(), tenant());
            if (revenue == null || !"APPROVED".equals(revenue.get("approval_status"))
                    || !Objects.equals(longValue(revenue.get("project_id")), request.projectId())
                    || !Objects.equals(longValue(revenue.get("contract_id")), request.contractId())) {
                throw error("OWNER_SETTLEMENT_REVENUE_MISMATCH", "业主结算关联的收入确认不存在、未审批或不属于同一项目合同");
            }
        }
        BigDecimal contractAmount = decimal(contract.get("current_amount"));
        ensureSettlementWithinContract(request.contractId(), null, request.grossAmount(), contractAmount);
        Long id = IdWorker.getId();
        String code = "OS-" + id;
        BigDecimal gross = money(request.grossAmount());
        BigDecimal retention = money(request.retentionAmount());
        jdbc.update("""
                INSERT INTO owner_settlement(id,tenant_id,project_id,contract_id,revenue_id,settlement_code,
                 settlement_period,settlement_date,gross_amount,tax_amount,retention_amount,net_receivable_amount,
                 due_date,customer_id,status,attachment_count,formula_version,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                 VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,'OWNER_SETTLEMENT_V1',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                 """, id, tenant(), request.projectId(), request.contractId(), request.revenueId(), code,
                 request.settlementPeriod().trim(), request.settlementDate(), gross, money(request.taxAmount()), retention,
                 gross.subtract(retention), request.dueDate(), request.customerId(),
                0, user(), user(), request.remark());
        if (submissionId != null) {
            jdbc.update("""
                    UPDATE owner_settlement
                       SET production_measurement_id=?,owner_submission_id=?,reported_amount=?,deducted_amount=?,
                           formula_version='OWNER_CONFIRMED_MEASUREMENT_V1'
                     WHERE id=? AND tenant_id=?
                    """, measurementId, submissionId, money(reportedAmount), money(deductedAmount), id, tenant());
        }
        return settlement(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> submitSettlement(Long id) {
        Map<String,Object> settlement = one("""
                SELECT id,status,project_id,contract_id,customer_id,gross_amount,approval_instance_id,settlement_code
                  FROM owner_settlement
                 WHERE id=? AND tenant_id=? AND deleted_flag=0
                 FOR UPDATE
                """, id, tenant());
        if (settlement == null) throw error("OWNER_SETTLEMENT_NOT_FOUND", "业主结算不存在");
        String status = string(settlement.get("status"));
        if (!Set.of("DRAFT", "REJECTED").contains(status)) {
            throw error("OWNER_SETTLEMENT_NOT_SUBMITTABLE", "只有草稿或驳回状态可以提交");
        }
        Map<String,Object> contract = requireRevenueContract(longValue(settlement.get("project_id")),
                longValue(settlement.get("contract_id")), longValue(settlement.get("customer_id")));
        ensureSettlementWithinContract(longValue(settlement.get("contract_id")), id,
                decimal(settlement.get("gross_amount")), decimal(contract.get("current_amount")));
        int cleanAttachmentCount = cleanOwnerSettlementAttachmentCount(id);
        if (cleanAttachmentCount < 1) {
            throw error("OWNER_SETTLEMENT_ATTACHMENT_REQUIRED", "业主结算提交前必须上传病毒扫描通过的确认单或结算附件");
        }
        if (jdbc.update("""
                UPDATE owner_settlement
                   SET attachment_count=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND tenant_id=? AND status=?
                """, cleanAttachmentCount, user(), id, tenant(), status) != 1) {
            throw error("OWNER_SETTLEMENT_CONCURRENT_MODIFICATION", "业主结算已被修改，请刷新后重试");
        }
        WfInstance instance;
        Long existingInstanceId = longValue(settlement.get("approval_instance_id"));
        if ("REJECTED".equals(status) && existingInstanceId != null) {
            instance = workflowEngine.resubmit(existingInstanceId, user(), UserContext.getCurrentUsername());
        } else {
            instance = workflowEngine.submit(user(), UserContext.getCurrentUsername(), tenant(),
                    WorkflowBusinessTypes.OWNER_SETTLEMENT, id, string(settlement.get("settlement_code")),
                    decimal(settlement.get("gross_amount")), longValue(settlement.get("project_id")),
                    longValue(settlement.get("contract_id")), "业主结算", null, null);
        }
        if (jdbc.update("""
                UPDATE owner_settlement
                   SET status='PENDING',approval_instance_id=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND tenant_id=? AND status=?
                """, instance.getId(), user(), id, tenant(), status) != 1) {
            throw error("OWNER_SETTLEMENT_CONCURRENT_MODIFICATION", "业主结算已被修改，请刷新后重试");
        }
        return settlement(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onSettlementApproved(Long id) {
        Map<String,Object> settlement = one("""
                SELECT id,status,project_id,contract_id,customer_id,net_receivable_amount,retention_amount,due_date
                  FROM owner_settlement
                 WHERE id=? AND tenant_id=? AND deleted_flag=0
                 FOR UPDATE
                """, id, tenant());
        if (settlement == null) throw error("OWNER_SETTLEMENT_NOT_FOUND", "业主结算不存在");
        if ("RECEIVABLE_CREATED".equals(settlement.get("status"))) {
            entryGenerator.generateEntry(OwnerSettlementEntryGenerationStrategy.SOURCE_TYPE, id,
                    OwnerSettlementEntryGenerationStrategy.ENTRY_TYPE);
            mandatoryAuditService.verifyRevenue("OWNER_SETTLEMENT_AR_CONFIRMED", "OWNER_SETTLEMENT", id,
                    "RECEIVABLE_CREATED", ownerSettlementAuditPayload(settlement));
            return;
        }
        if (jdbc.update("UPDATE owner_settlement SET status='APPROVED',version=version+1 WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant()) != 1) {
            throw error("OWNER_SETTLEMENT_APPROVAL_STATE_INVALID", "业主结算审批状态不正确");
        }
        createReceivable(settlement, "PROGRESS", decimal(settlement.get("net_receivable_amount")), localDate(settlement.get("due_date")));
        BigDecimal retention = decimal(settlement.get("retention_amount"));
        if (retention.signum() > 0) createReceivable(settlement, "RETENTION", retention, localDate(settlement.get("due_date")));
        jdbc.update("UPDATE owner_settlement SET status='RECEIVABLE_CREATED',version=version+1 WHERE id=? AND tenant_id=?", id, tenant());
        entryGenerator.generateEntry(OwnerSettlementEntryGenerationStrategy.SOURCE_TYPE, id,
                OwnerSettlementEntryGenerationStrategy.ENTRY_TYPE);
        auditOwnerSettlementReceivable(settlement);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onSettlementRejected(Long id) {
        jdbc.update("UPDATE owner_settlement SET status='REJECTED',version=version+1 WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createSalesInvoice(SalesInvoiceRequest request) {
        requireRevenueContract(request.projectId(), request.contractId(), request.customerId());
        String invoiceType = sysDictDataService.requireEnabledValue(
                "invoice_type", request.invoiceType(), "INVOICE_TYPE_INVALID", "发票类型不合法");
        BigDecimal total = money(request.amountWithoutTax()).add(money(request.taxAmount()));
        BigDecimal allocationTotal = allocationTotal(request.allocations());
        if (total.signum() <= 0 || allocationTotal.compareTo(total) != 0) {
            throw error("SALES_INVOICE_ALLOCATION_UNBALANCED", "销项发票分配金额必须等于价税合计");
        }
        validateInvoiceAllocations(request.allocations(), request.projectId(), request.contractId(), request.customerId());
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO sales_invoice(id,tenant_id,project_id,contract_id,customer_id,invoice_code,invoice_no,invoice_type,
                     invoice_date,amount_without_tax,tax_amount,total_amount,allocated_amount,status,verification_status,
                     attachment_count,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0,'PENDING_EVIDENCE','UNVERIFIED',0,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), request.projectId(), request.contractId(), request.customerId(), request.invoiceCode(),
                    request.invoiceNo().trim(), invoiceType, request.invoiceDate(),
                    money(request.amountWithoutTax()), money(request.taxAmount()), total,
                    user(), user(), request.remark());
        } catch (DuplicateKeyException e) {
            throw error("SALES_INVOICE_DUPLICATE", "销项发票号码或应收分配重复");
        }
        return one("SELECT * FROM sales_invoice WHERE id=? AND tenant_id=?", id, tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> confirmSalesInvoice(Long id, AllocationConfirmationRequest request) {
        if (request.allocations() == null || request.allocations().isEmpty()) {
            throw error("SALES_INVOICE_ALLOCATION_REQUIRED", "销项发票必须提供应收分配");
        }
        Map<String,Object> invoice = one("""
                SELECT id,project_id,contract_id,customer_id,invoice_code,invoice_no,invoice_type,invoice_date,
                       amount_without_tax,tax_amount,total_amount,allocated_amount,status,verification_status,
                       attachment_count,version,remark
                  FROM sales_invoice
                 WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE
                """,
                id, tenant());
        if (invoice == null) throw error("SALES_INVOICE_NOT_FOUND", "销项发票不存在");
        projectAccessChecker.checkAccess(longValue(invoice.get("project_id")), "确认销项发票");
        if (!"PENDING_EVIDENCE".equals(invoice.get("status"))) {
            if (Set.of("FULLY_ALLOCATED", "ISSUED").contains(string(invoice.get("status")))) {
                Map<String,Object> result = requireSameInvoiceConfirmation(invoice, request.allocations());
                mandatoryAuditService.verifyRevenue("SALES_INVOICE_CONFIRMED", "SALES_INVOICE", id, "CONFIRMED",
                        Map.of("totalAmount", decimal(invoice.get("total_amount"))));
                return result;
            }
            throw error("SALES_INVOICE_CONFIRM_STATE_INVALID", "只有待证据销项发票可以确认");
        }
        int attachmentCount = cleanAttachmentCount("SALES_INVOICE", id,
                List.of("ELECTRONIC_INVOICE", "SCANNED_INVOICE"));
        if (attachmentCount < 1) throw error("SALES_INVOICE_CLEAN_EVIDENCE_REQUIRED", "销项发票确认前必须上传扫描通过的发票文件");
        BigDecimal total = decimal(invoice.get("total_amount"));
        if (allocationTotal(request.allocations()).compareTo(total) != 0) {
            throw error("SALES_INVOICE_ALLOCATION_UNBALANCED", "销项发票分配金额必须等于价税合计");
        }
        validateInvoiceAllocations(request.allocations(), longValue(invoice.get("project_id")),
                longValue(invoice.get("contract_id")), longValue(invoice.get("customer_id")));
        for (AmountAllocation allocation : request.allocations()) {
            jdbc.update("INSERT INTO sales_invoice_allocation(id,tenant_id,invoice_id,receivable_id,allocated_amount,created_by,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                    IdWorker.getId(), tenant(), id, allocation.receivableId(), money(allocation.amount()), user());
        }
        if (jdbc.update("""
                UPDATE sales_invoice
                   SET allocated_amount=?,status='FULLY_ALLOCATED',attachment_count=?,version=version+1,
                       updated_by=?,updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND tenant_id=? AND status='PENDING_EVIDENCE'
                """, total, attachmentCount, user(), id, tenant()) != 1) {
            throw error("SALES_INVOICE_CONFIRM_CONFLICT", "销项发票已被并发确认");
        }
        mandatoryAuditService.revenue("SALES_INVOICE_CONFIRMED", "SALES_INVOICE", id,
                longValue(invoice.get("project_id")), "CONFIRMED", Map.of("totalAmount", total));
        return one("""
                SELECT id,project_id,contract_id,customer_id,invoice_code,invoice_no,invoice_type,invoice_date,
                       amount_without_tax,tax_amount,total_amount,allocated_amount,status,verification_status,
                       attachment_count,version,remark
                  FROM sales_invoice
                 WHERE id=? AND tenant_id=? AND deleted_flag=0
                """, id, tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createCollection(CollectionRequest request) {
        Map<String,Object> existing = one("SELECT * FROM collection_record WHERE tenant_id=? AND external_txn_no=? AND deleted_flag=0",
                tenant(), request.externalTxnNo().trim());
        if (existing != null) return requireSameCollection(existing, request);
        requireRevenueContract(request.projectId(), request.contractId(), request.customerId());
        Map<String,Object> account = one("SELECT id,opening_date,enabled_flag FROM fund_account WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE",
                request.fundAccountId(), tenant());
        if (account == null || intValue(account.get("enabled_flag")) != 1) throw error("COLLECTION_ACCOUNT_INVALID", "收款账户不存在或已停用");
        // 同一账户上的回款串行后再次检查，避免两个并发请求在首次幂等查询后同时落库。
        existing = one("SELECT * FROM collection_record WHERE tenant_id=? AND external_txn_no=? AND deleted_flag=0",
                tenant(), request.externalTxnNo().trim());
        if (existing != null) return requireSameCollection(existing, request);
        LocalDate openingDate = localDate(account.get("opening_date"));
        if (request.collectedAt().toLocalDate().isBefore(openingDate)) throw error("COLLECTION_BEFORE_ACCOUNT_OPENING", "到账时间不能早于账户启用日期");
        List<AmountAllocation> allocations = request.allocations() == null ? List.of() : request.allocations();
        BigDecimal amount = money(request.amount());
        BigDecimal allocated = allocationTotal(allocations);
        if (allocated.compareTo(amount) > 0) throw error("COLLECTION_ALLOCATION_EXCEEDED", "回款分配金额不能超过到账金额");
        validateAllocations(allocations, request.projectId(), request.contractId(), request.customerId(), true);
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO collection_record(id,tenant_id,project_id,contract_id,customer_id,fund_account_id,collection_code,
                     external_txn_no,collected_at,amount,allocated_amount,unallocated_amount,payer_name,status,attachment_count,
                     version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,0,?,?,'PENDING_EVIDENCE',0,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), request.projectId(), request.contractId(), request.customerId(), request.fundAccountId(),
                    "CR-" + id, request.externalTxnNo().trim(), request.collectedAt(), amount, amount,
                    request.payerName().trim(),
                    user(), user(), request.remark());
        } catch (DuplicateKeyException e) {
            Map<String,Object> duplicate = one("SELECT * FROM collection_record WHERE tenant_id=? AND external_txn_no=? AND deleted_flag=0",
                    tenant(), request.externalTxnNo().trim());
            if (duplicate != null) return requireSameCollection(duplicate, request);
            throw e;
        }
        return one("SELECT * FROM collection_record WHERE id=? AND tenant_id=?", id, tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> confirmCollection(Long id, AllocationConfirmationRequest request) {
        if (request.allocations() == null || request.allocations().isEmpty()) {
            throw error("COLLECTION_ALLOCATION_REQUIRED", "回款确认必须提供应收分配");
        }
        Map<String,Object> collection = one("""
                SELECT id,project_id,contract_id,customer_id,fund_account_id,collection_code,external_txn_no,
                       collected_at,amount,allocated_amount,unallocated_amount,payer_name,status,
                       attachment_count,version,remark
                  FROM collection_record
                 WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE
                """,
                id, tenant());
        if (collection == null) throw error("COLLECTION_NOT_FOUND", "回款不存在");
        projectAccessChecker.checkAccess(longValue(collection.get("project_id")), "确认回款");
        if (!"PENDING_EVIDENCE".equals(collection.get("status"))) {
            if ("SUCCESS".equals(collection.get("status"))) {
                Map<String,Object> result = requireSameCollectionConfirmation(collection, request.allocations());
                mandatoryAuditService.verifyRevenue("COLLECTION_CONFIRMED", "COLLECTION_RECORD", id, "CONFIRMED",
                        Map.of("amount", decimal(collection.get("amount")),
                                "allocatedAmount", allocationTotal(request.allocations())));
                return result;
            }
            throw error("COLLECTION_CONFIRM_STATE_INVALID", "只有待证据回款可以确认");
        }
        int attachmentCount = cleanAttachmentCount("COLLECTION_RECORD", id, List.of("BANK_RECEIPT"));
        if (attachmentCount < 1) throw error("COLLECTION_CLEAN_EVIDENCE_REQUIRED", "回款确认前必须上传扫描通过的银行回单");
        Map<String,Object> account = one("SELECT id,enabled_flag FROM fund_account WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE",
                collection.get("fund_account_id"), tenant());
        if (account == null || intValue(account.get("enabled_flag")) != 1) {
            throw error("COLLECTION_ACCOUNT_INVALID", "收款账户不存在或已停用");
        }
        BigDecimal amount = decimal(collection.get("amount"));
        BigDecimal allocated = allocationTotal(request.allocations());
        if (allocated.compareTo(amount) > 0) throw error("COLLECTION_ALLOCATION_EXCEEDED", "回款分配金额不能超过到账金额");
        validateAllocations(request.allocations(), longValue(collection.get("project_id")),
                longValue(collection.get("contract_id")), longValue(collection.get("customer_id")), true);
        for (AmountAllocation allocation : request.allocations()) applyCollectionAllocation(id, allocation);
        if (jdbc.update("""
                UPDATE collection_record
                   SET allocated_amount=?,unallocated_amount=?,status='SUCCESS',attachment_count=?,version=version+1,
                       updated_by=?,updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND tenant_id=? AND status='PENDING_EVIDENCE'
                """, allocated, amount.subtract(allocated), attachmentCount, user(), id, tenant()) != 1) {
            throw error("COLLECTION_CONFIRM_CONFLICT", "回款已被并发确认");
        }
        insertCollectionJournal(id, collection, amount);
        entryGenerator.generateEntry("COLLECTION_RECORD", id, "COLLECTION");
        mandatoryAuditService.revenue("COLLECTION_CONFIRMED", "COLLECTION_RECORD", id,
                longValue(collection.get("project_id")), "CONFIRMED", Map.of("amount", amount, "allocatedAmount", allocated));
        return one("""
                SELECT id,project_id,contract_id,customer_id,fund_account_id,collection_code,external_txn_no,
                       collected_at,amount,allocated_amount,unallocated_amount,payer_name,status,
                       attachment_count,version,remark
                  FROM collection_record
                 WHERE id=? AND tenant_id=? AND deleted_flag=0
                """, id, tenant());
    }

    public List<Map<String,Object>> settlements(Long projectId, String status) {
        List<Long> projectIds = visibleProjectIds(projectId, "查看收入结算");
        if (projectIds.isEmpty()) return List.of();
        List<Object> arguments = scopedArguments(projectIds, status);
        return jdbc.queryForList("SELECT * FROM owner_settlement WHERE tenant_id=? AND deleted_flag=0 AND project_id IN(" +
                        placeholders(projectIds.size()) + ") AND (? IS NULL OR status=?) ORDER BY settlement_date DESC,id DESC",
                arguments.toArray());
    }

    public List<Map<String,Object>> receivables(Long projectId, String status) {
        List<Long> projectIds = visibleProjectIds(projectId, "查看应收款");
        if (projectIds.isEmpty()) return List.of();
        List<Object> arguments = scopedArguments(projectIds, status);
        return jdbc.queryForList("SELECT r.*,CASE WHEN r.outstanding_amount>0 AND r.due_date<CURRENT_DATE THEN 1 ELSE 0 END overdue_flag FROM account_receivable r WHERE tenant_id=? AND deleted_flag=0 AND project_id IN(" +
                        placeholders(projectIds.size()) + ") AND (? IS NULL OR status=?) ORDER BY due_date,id",
                arguments.toArray());
    }

    public List<Map<String,Object>> invoices(Long projectId) {
        List<Long> projectIds = visibleProjectIds(projectId, "查看销项发票");
        if (projectIds.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT * FROM sales_invoice WHERE tenant_id=? AND deleted_flag=0 AND project_id IN(" +
                        placeholders(projectIds.size()) + ") ORDER BY invoice_date DESC,id DESC",
                args(tenant(), projectIds));
    }

    public List<Map<String,Object>> collections(Long projectId, String status) {
        List<Long> projectIds = visibleProjectIds(projectId, "查看回款");
        if (projectIds.isEmpty()) return List.of();
        List<Object> arguments = scopedArguments(projectIds, status);
        return jdbc.queryForList("SELECT * FROM collection_record WHERE tenant_id=? AND deleted_flag=0 AND project_id IN(" +
                        placeholders(projectIds.size()) + ") AND (? IS NULL OR status=?) ORDER BY collected_at DESC,id DESC",
                arguments.toArray());
    }

    public Map<String,Object> dashboard(Long projectId) {
        requireProjectVisible(projectId);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("confirmedRevenue", scalar("SELECT COALESCE(SUM(revenue_amount),0) FROM contract_revenue WHERE tenant_id=? AND project_id=? AND approval_status='APPROVED' AND deleted_flag=0", projectId));
        result.put("settledAmount", scalar("SELECT COALESCE(SUM(gross_amount),0) FROM owner_settlement WHERE tenant_id=? AND project_id=? AND status='RECEIVABLE_CREATED' AND deleted_flag=0", projectId));
        BigDecimal receivable = scalar("SELECT COALESCE(SUM(original_amount),0) FROM account_receivable WHERE tenant_id=? AND project_id=? AND deleted_flag=0", projectId);
        BigDecimal outstanding = scalar("SELECT COALESCE(SUM(outstanding_amount),0) FROM account_receivable WHERE tenant_id=? AND project_id=? AND deleted_flag=0", projectId);
        BigDecimal collected = scalar("SELECT COALESCE(SUM(amount),0) FROM collection_record WHERE tenant_id=? AND project_id=? AND status='SUCCESS' AND deleted_flag=0", projectId);
        result.put("receivableAmount", receivable);
        result.put("outstandingAmount", outstanding);
        result.put("collectedAmount", collected);
        result.put("overdueAmount", scalar("SELECT COALESCE(SUM(outstanding_amount),0) FROM account_receivable WHERE tenant_id=? AND project_id=? AND outstanding_amount>0 AND due_date<CURRENT_DATE AND deleted_flag=0", projectId));
        result.put("invoicedAmount", scalar("SELECT COALESCE(SUM(total_amount),0) FROM sales_invoice WHERE tenant_id=? AND project_id=? AND status<>'VOIDED' AND deleted_flag=0", projectId));
        result.put("collectionRate", receivable.signum() == 0 ? BigDecimal.ZERO.setScale(2) : receivable.subtract(outstanding).divide(receivable, 2, RoundingMode.HALF_UP));
        return result;
    }

    public Map<String,Object> traceByCashJournal(Long journalId) {
        Map<String,Object> journal = one("SELECT * FROM cash_journal_entry WHERE id=? AND tenant_id=? AND deleted_flag=0", journalId, tenant());
        if (journal == null || journal.get("collection_record_id") == null) throw error("REVENUE_TRACE_NOT_FOUND", "现金日记不存在或不是回款收入流水");
        Long collectionId = longValue(journal.get("collection_record_id"));
        Map<String,Object> collection = one("SELECT * FROM collection_record WHERE id=? AND tenant_id=?", collectionId, tenant());
        if (collection == null) throw error("REVENUE_TRACE_NOT_FOUND", "回款记录不存在");
        projectAccessChecker.checkAccess(longValue(collection.get("project_id")), "查看收入回款追溯");
        List<Map<String,Object>> allocations = jdbc.queryForList("SELECT ca.*,r.receivable_code,r.settlement_id,r.outstanding_amount FROM collection_allocation ca JOIN account_receivable r ON r.id=ca.receivable_id WHERE ca.tenant_id=? AND ca.collection_id=? ORDER BY ca.id", tenant(), collectionId);
        Set<Long> receivableIds = new LinkedHashSet<>();
        Set<Long> settlementIds = new LinkedHashSet<>();
        for (Map<String,Object> row : allocations) {
            receivableIds.add(longValue(row.get("receivable_id")));
            settlementIds.add(longValue(row.get("settlement_id")));
        }
        List<Map<String,Object>> receivables = receivableIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM account_receivable WHERE tenant_id=? AND id IN(" + placeholders(receivableIds.size()) + ")", args(tenant(), receivableIds));
        List<Map<String,Object>> settlements = settlementIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM owner_settlement WHERE tenant_id=? AND id IN(" + placeholders(settlementIds.size()) + ")", args(tenant(), settlementIds));
        Set<Long> revenueIds = new LinkedHashSet<>();
        Set<Long> measurementIds = new LinkedHashSet<>();
        Set<Long> ownerSubmissionIds = new LinkedHashSet<>();
        Set<Long> approvalInstanceIds = new LinkedHashSet<>();
        for (Map<String,Object> row : settlements) {
            if (longValue(row.get("revenue_id")) != null) revenueIds.add(longValue(row.get("revenue_id")));
            if (longValue(row.get("production_measurement_id")) != null) measurementIds.add(longValue(row.get("production_measurement_id")));
            if (longValue(row.get("owner_submission_id")) != null) ownerSubmissionIds.add(longValue(row.get("owner_submission_id")));
            if (longValue(row.get("approval_instance_id")) != null) approvalInstanceIds.add(longValue(row.get("approval_instance_id")));
        }
        List<Map<String,Object>> revenues = revenueIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM contract_revenue WHERE tenant_id=? AND id IN(" + placeholders(revenueIds.size()) + ")", args(tenant(), revenueIds));
        List<Map<String,Object>> measurements = measurementIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM production_measurement WHERE tenant_id=? AND id IN(" + placeholders(measurementIds.size()) + ")", args(tenant(), measurementIds));
        List<Map<String,Object>> measurementLines = measurementIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM production_measurement_line WHERE tenant_id=? AND measurement_id IN(" + placeholders(measurementIds.size()) + ") ORDER BY measurement_id,sort_order,id", args(tenant(), measurementIds));
        List<Map<String,Object>> ownerSubmissions = ownerSubmissionIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM owner_measurement_submission WHERE tenant_id=? AND id IN(" + placeholders(ownerSubmissionIds.size()) + ")", args(tenant(), ownerSubmissionIds));
        List<Map<String,Object>> ownerReviewLines = ownerSubmissionIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM owner_measurement_review_line WHERE tenant_id=? AND submission_id IN(" + placeholders(ownerSubmissionIds.size()) + ") ORDER BY submission_id,id", args(tenant(), ownerSubmissionIds));
        for (Map<String,Object> row : revenues) {
            if (longValue(row.get("approval_instance_id")) != null) approvalInstanceIds.add(longValue(row.get("approval_instance_id")));
        }
        for (Map<String,Object> row : measurements) {
            if (longValue(row.get("approval_instance_id")) != null) approvalInstanceIds.add(longValue(row.get("approval_instance_id")));
        }
        List<Map<String,Object>> approvalInstances = approvalInstanceIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM wf_instance WHERE tenant_id=? AND id IN(" + placeholders(approvalInstanceIds.size()) + ")", args(tenant(), approvalInstanceIds));
        List<Map<String,Object>> approvalTasks = approvalInstanceIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM wf_task WHERE tenant_id=? AND instance_id IN(" + placeholders(approvalInstanceIds.size()) + ") ORDER BY received_at,id", args(tenant(), approvalInstanceIds));
        List<Map<String,Object>> approvalRecords = approvalInstanceIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM wf_record WHERE tenant_id=? AND instance_id IN(" + placeholders(approvalInstanceIds.size()) + ") AND deleted_flag=0 ORDER BY created_at,id", args(tenant(), approvalInstanceIds));
        List<Map<String,Object>> invoices = allocations.isEmpty() ? List.of() : jdbc.queryForList("SELECT DISTINCT i.* FROM sales_invoice i JOIN sales_invoice_allocation a ON a.invoice_id=i.id JOIN collection_allocation c ON c.receivable_id=a.receivable_id WHERE i.tenant_id=? AND c.collection_id=?", tenant(), collectionId);
        Map<String,Object> contract = one("SELECT * FROM ct_contract WHERE id=? AND tenant_id=? AND deleted_flag=0", longValue(collection.get("contract_id")), tenant());
        Map<String,Object> project = one("SELECT * FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0", longValue(collection.get("project_id")), tenant());
        List<Map<String,Object>> entries = jdbc.queryForList("SELECT * FROM accounting_entry WHERE tenant_id=? AND collection_record_id=? AND deleted_flag=0", tenant(), collectionId);
        Map<String,Object> trace = new LinkedHashMap<>();
        trace.put("journal", journal); trace.put("collection", collection); trace.put("allocations", allocations);
        trace.put("receivables", receivables); trace.put("settlements", settlements); trace.put("revenues", revenues);
        trace.put("productionMeasurements", measurements); trace.put("productionMeasurementLines", measurementLines);
        trace.put("ownerMeasurementSubmissions", ownerSubmissions); trace.put("ownerMeasurementReviewLines", ownerReviewLines);
        trace.put("approvalInstances", approvalInstances); trace.put("approvalTasks", approvalTasks); trace.put("approvalRecords", approvalRecords);
        trace.put("salesInvoices", invoices);
        trace.put("accountingEntries", entries); trace.put("contract", contract); trace.put("project", project);
        return trace;
    }

    public Map<String,Object> settlement(Long id) {
        Map<String,Object> result = one("SELECT * FROM owner_settlement WHERE id=? AND tenant_id=? AND deleted_flag=0", id, tenant());
        if (result == null) throw error("OWNER_SETTLEMENT_NOT_FOUND", "业主结算不存在");
        projectAccessChecker.checkAccess(longValue(result.get("project_id")), "查看收入结算");
        return result;
    }

    public List<OwnerSettlementVO> settlementViews(Long projectId, String status) {
        return settlements(projectId, status).stream().map(this::ownerSettlementView).toList();
    }

    public List<ReceivableVO> receivableViews(Long projectId, String status) {
        return receivables(projectId, status).stream().map(this::receivableView).toList();
    }

    public List<SalesInvoiceVO> invoiceViews(Long projectId) {
        return invoices(projectId).stream().map(this::salesInvoiceView).toList();
    }

    public List<CollectionVO> collectionViews(Long projectId, String status) {
        return collections(projectId, status).stream().map(this::collectionView).toList();
    }

    public OwnerSettlementVO ownerSettlementView(Map<String,Object> row) {
        return new OwnerSettlementVO(text(row, "id"), text(row, "project_id"),
                text(row, "contract_id"), text(row, "revenue_id"), text(row, "customer_id"),
                text(row, "settlement_code"), text(row, "settlement_period"),
                text(row, "settlement_date"), text(row, "gross_amount"), text(row, "tax_amount"),
                text(row, "retention_amount"), text(row, "net_receivable_amount"),
                text(row, "due_date"), text(row, "status"), integer(row, "attachment_count"),
                text(row, "approval_instance_id"), text(row, "formula_version"),
                text(row, "version"), text(row, "remark"));
    }

    public SalesInvoiceVO salesInvoiceView(Map<String,Object> row) {
        return new SalesInvoiceVO(text(row, "id"), text(row, "project_id"),
                text(row, "contract_id"), text(row, "customer_id"), text(row, "invoice_code"),
                text(row, "invoice_no"), text(row, "invoice_type"), text(row, "invoice_date"),
                text(row, "amount_without_tax"), text(row, "tax_amount"), text(row, "total_amount"),
                text(row, "allocated_amount"), text(row, "status"),
                text(row, "verification_status"), integer(row, "attachment_count"),
                text(row, "version"), text(row, "remark"));
    }

    public CollectionVO collectionView(Map<String,Object> row) {
        return new CollectionVO(text(row, "id"), text(row, "project_id"),
                text(row, "contract_id"), text(row, "customer_id"), text(row, "fund_account_id"),
                text(row, "collection_code"), text(row, "external_txn_no"),
                text(row, "collected_at"), text(row, "amount"), text(row, "allocated_amount"),
                text(row, "unallocated_amount"), text(row, "payer_name"), text(row, "status"),
                integer(row, "attachment_count"), text(row, "version"), text(row, "remark"));
    }

    public ReceivableAdjustmentVO receivableAdjustmentView(Map<String,Object> row) {
        return new ReceivableAdjustmentVO(text(row, "id"), text(row, "receivable_id"),
                text(row, "adjustment_type"), text(row, "amount"), text(row, "reason"),
                text(row, "idempotency_key"), text(row, "status"));
    }

    public CollectionReversalVO collectionReversalView(Map<String,Object> row) {
        return new CollectionReversalVO(text(row, "id"), text(row, "collection_id"),
                text(row, "idempotency_key"), text(row, "reason"), text(row, "status"));
    }

    public RevenueDashboardVO dashboardView(Long projectId) {
        Map<String,Object> row = dashboard(projectId);
        return new RevenueDashboardVO(text(row, "projectId"), text(row, "confirmedRevenue"),
                text(row, "settledAmount"), text(row, "receivableAmount"),
                text(row, "outstandingAmount"), text(row, "collectedAmount"),
                text(row, "overdueAmount"), text(row, "invoicedAmount"),
                text(row, "collectionRate"));
    }

    private ReceivableVO receivableView(Map<String,Object> row) {
        return new ReceivableVO(text(row, "id"), text(row, "project_id"),
                text(row, "contract_id"), text(row, "settlement_id"), text(row, "customer_id"),
                text(row, "receivable_code"), text(row, "receivable_type"),
                text(row, "original_amount"), text(row, "collected_amount"),
                text(row, "credited_amount"), text(row, "outstanding_amount"),
                text(row, "due_date"), text(row, "status"),
                integer(row, "overdue_flag") != null && integer(row, "overdue_flag") == 1,
                text(row, "version"));
    }

    private void createReceivable(Map<String,Object> settlement, String type, BigDecimal amount, LocalDate dueDate) {
        if (amount.signum() <= 0) return;
        Long settlementId = longValue(settlement.get("id"));
        try {
            jdbc.update("""
                    INSERT INTO account_receivable(id,tenant_id,project_id,contract_id,settlement_id,customer_id,receivable_type,
                     receivable_code,original_amount,collected_amount,credited_amount,outstanding_amount,due_date,status,version,
                     created_by,created_at,updated_by,updated_at,deleted_flag)
                    VALUES(?,?,?,?,?,?,?, ?,?,0,0,?,?,'OPEN',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)
                    """, IdWorker.getId(), tenant(), settlement.get("project_id"), settlement.get("contract_id"), settlementId,
                    settlement.get("customer_id"), type, "AR-" + settlementId + "-" + type, amount, amount, dueDate, user(), user());
        } catch (DuplicateKeyException ignored) {
            // 审批回调幂等：同一结算同一类型只允许一个应收。
        }
    }

    private void auditOwnerSettlementReceivable(Map<String,Object> settlement) {
        Map<String,Object> payload = ownerSettlementAuditPayload(settlement);
        mandatoryAuditService.revenue("OWNER_SETTLEMENT_AR_CONFIRMED", "OWNER_SETTLEMENT",
                longValue(settlement.get("id")), longValue(settlement.get("project_id")),
                "RECEIVABLE_CREATED", payload);
    }

    private Map<String,Object> ownerSettlementAuditPayload(Map<String,Object> settlement) {
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("netReceivableAmount", decimal(settlement.get("net_receivable_amount")));
        payload.put("retentionAmount", decimal(settlement.get("retention_amount")));
        payload.put("dueDate", localDate(settlement.get("due_date")));
        return payload;
    }

    private void applyCollectionAllocation(Long collectionId, AmountAllocation allocation) {
        Map<String,Object> receivable = one("SELECT * FROM account_receivable WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE", allocation.receivableId(), tenant());
        BigDecimal amount = money(allocation.amount());
        BigDecimal outstanding = decimal(receivable.get("outstanding_amount"));
        if (outstanding.compareTo(amount) < 0) throw error("COLLECTION_RECEIVABLE_EXCEEDED", "回款核销金额超过应收余额");
        BigDecimal remaining = outstanding.subtract(amount);
        String status = remaining.signum() == 0 ? "COLLECTED" : "PARTIALLY_COLLECTED";
        jdbc.update("UPDATE account_receivable SET collected_amount=collected_amount+?,outstanding_amount=?,status=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                amount, remaining, status, user(), allocation.receivableId(), tenant());
        jdbc.update("INSERT INTO collection_allocation(id,tenant_id,collection_id,receivable_id,allocated_amount,allocation_type,created_by,created_at) VALUES(?,?,?,?,?,'COLLECTION',?,CURRENT_TIMESTAMP)",
                IdWorker.getId(), tenant(), collectionId, allocation.receivableId(), amount, user());
    }

    private void insertCollectionJournal(Long id, Map<String,Object> collection, BigDecimal amount) {
        LocalDateTime collectedAt = localDateTime(collection.get("collected_at"));
        periodGuard.assertWritable(collectedAt.toLocalDate());
        jdbc.update("""
                INSERT INTO cash_journal_entry(id,tenant_id,entry_no,account_id,direction,amount,business_date,counterparty_name,
                 summary,project_id,contract_id,source_type,source_id,collection_record_id,status,closure_due_at,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                VALUES(?,?,?,?, 'IN',?,?,?,?,?,?,'COLLECTION_RECORD',?,?,'PENDING_ARCHIVE',?,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                """, IdWorker.getId(), tenant(), "CJ-IN-" + id, collection.get("fund_account_id"), amount,
                collectedAt.toLocalDate(), string(collection.get("payer_name")), "项目回款：" + collection.get("external_txn_no"),
                collection.get("project_id"), collection.get("contract_id"), id, id, collectedAt.plusHours(72),
                user(), user(), collection.get("remark"));
    }

    private void validateInvoiceAllocations(List<AmountAllocation> allocations, Long projectId,
                                            Long contractId, Long customerId) {
        Set<Long> ids = new HashSet<>();
        for (AmountAllocation allocation : allocations) {
            if (!ids.add(allocation.receivableId())) throw error("RECEIVABLE_ALLOCATION_DUPLICATE", "同一应收不能重复分配");
            Map<String,Object> row = one("""
                    SELECT r.project_id,r.contract_id,r.customer_id,r.original_amount,
                           COALESCE((SELECT SUM(a.allocated_amount)
                                       FROM sales_invoice_allocation a
                                       JOIN sales_invoice i ON i.id=a.invoice_id AND i.tenant_id=a.tenant_id
                                      WHERE a.tenant_id=r.tenant_id AND a.receivable_id=r.id
                                        AND i.deleted_flag=0 AND i.status<>'REJECTED'),0) invoiced_amount
                      FROM account_receivable r
                     WHERE r.id=? AND r.tenant_id=? AND r.deleted_flag=0
                     FOR UPDATE
                    """, allocation.receivableId(), tenant());
            if (row == null || !Objects.equals(longValue(row.get("project_id")), projectId)
                    || !Objects.equals(longValue(row.get("contract_id")), contractId)
                    || !Objects.equals(longValue(row.get("customer_id")), customerId)) {
                throw error("RECEIVABLE_CONTEXT_MISMATCH", "应收不属于同一项目、合同或客户");
            }
            BigDecimal available = decimal(row.get("original_amount")).subtract(decimal(row.get("invoiced_amount")));
            if (money(allocation.amount()).compareTo(available) > 0) {
                throw error("SALES_INVOICE_RECEIVABLE_EXCEEDED", "销项发票累计分配超过应收原值");
            }
        }
    }

    private void validateAllocations(List<AmountAllocation> allocations, Long projectId, Long contractId, Long customerId, boolean lock) {
        Set<Long> ids = new HashSet<>();
        for (AmountAllocation allocation : allocations) {
            if (!ids.add(allocation.receivableId())) throw error("RECEIVABLE_ALLOCATION_DUPLICATE", "同一应收不能重复分配");
            String sql = "SELECT project_id,contract_id,customer_id,outstanding_amount FROM account_receivable WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : "");
            Map<String,Object> row = one(sql, allocation.receivableId(), tenant());
            if (row == null || !Objects.equals(longValue(row.get("project_id")), projectId)
                    || !Objects.equals(longValue(row.get("contract_id")), contractId)
                    || !Objects.equals(longValue(row.get("customer_id")), customerId)) {
                throw error("RECEIVABLE_CONTEXT_MISMATCH", "应收不属于同一项目、合同或客户");
            }
            if (money(allocation.amount()).compareTo(decimal(row.get("outstanding_amount"))) > 0) {
                throw error("RECEIVABLE_ALLOCATION_EXCEEDED", "分配金额超过应收未核销余额");
            }
        }
    }

    private Map<String,Object> requireSameCollection(Map<String,Object> existing, CollectionRequest request) {
        projectAccessChecker.checkAccess(longValue(existing.get("project_id")), "复用回款幂等结果");
        List<AmountAllocation> requestedAllocations = request.allocations() == null ? List.of() : request.allocations();
        Map<Long,BigDecimal> requested = new LinkedHashMap<>();
        for (AmountAllocation allocation : requestedAllocations) {
            if (requested.put(allocation.receivableId(), money(allocation.amount())) != null) {
                throw error("COLLECTION_IDEMPOTENCY_CONFLICT", "银行流水号已被不同回款事实使用");
            }
        }
        List<Map<String,Object>> stored = jdbc.queryForList("""
                SELECT receivable_id,allocated_amount
                  FROM collection_allocation
                 WHERE tenant_id=? AND collection_id=?
                 ORDER BY receivable_id
                """, tenant(), longValue(existing.get("id")));
        boolean pending = "PENDING_EVIDENCE".equals(existing.get("status"));
        boolean same = Objects.equals(longValue(existing.get("project_id")), request.projectId())
                && Objects.equals(longValue(existing.get("contract_id")), request.contractId())
                && Objects.equals(longValue(existing.get("customer_id")), request.customerId())
                && Objects.equals(longValue(existing.get("fund_account_id")), request.fundAccountId())
                && money(decimal(existing.get("amount"))).compareTo(money(request.amount())) == 0
                && Objects.equals(string(existing.get("payer_name")), request.payerName().trim())
                && (pending || money(decimal(existing.get("allocated_amount"))).compareTo(allocationTotal(requestedAllocations)) == 0)
                && (pending || stored.size() == requested.size());
        if (same && !pending) {
            for (Map<String,Object> row : stored) {
                BigDecimal expected = requested.get(longValue(row.get("receivable_id")));
                if (expected == null || expected.compareTo(money(decimal(row.get("allocated_amount")))) != 0) {
                    same = false;
                    break;
                }
            }
        }
        if (!same) {
            throw error("COLLECTION_IDEMPOTENCY_CONFLICT", "银行流水号已被不同回款事实使用");
        }
        return existing;
    }

    private Map<String,Object> requireSameInvoiceConfirmation(Map<String,Object> invoice,
                                                              List<AmountAllocation> allocations) {
        if (!sameStoredAllocations("sales_invoice_allocation", "invoice_id", longValue(invoice.get("id")), allocations)) {
            throw error("SALES_INVOICE_CONFIRM_IDEMPOTENCY_CONFLICT", "销项发票已按不同应收分配确认");
        }
        return invoice;
    }

    private Map<String,Object> requireSameCollectionConfirmation(Map<String,Object> collection,
                                                                 List<AmountAllocation> allocations) {
        if (!sameStoredAllocations("collection_allocation", "collection_id", longValue(collection.get("id")), allocations)) {
            throw error("COLLECTION_CONFIRM_IDEMPOTENCY_CONFLICT", "回款已按不同应收分配确认");
        }
        return collection;
    }

    private boolean sameStoredAllocations(String table, String ownerColumn, Long ownerId,
                                          List<AmountAllocation> allocations) {
        Map<Long,BigDecimal> requested = new HashMap<>();
        for (AmountAllocation allocation : allocations) {
            if (requested.put(allocation.receivableId(), money(allocation.amount())) != null) return false;
        }
        List<Map<String,Object>> stored = jdbc.queryForList("SELECT receivable_id,allocated_amount FROM " + table
                + " WHERE tenant_id=? AND " + ownerColumn + "=? ORDER BY receivable_id", tenant(), ownerId);
        if (stored.size() != requested.size()) return false;
        return stored.stream().allMatch(row -> {
            BigDecimal expected = requested.get(longValue(row.get("receivable_id")));
            return expected != null && expected.compareTo(decimal(row.get("allocated_amount"))) == 0;
        });
    }

    private Map<String,Object> requireRevenueContract(Long projectId, Long contractId, Long customerId) {
        projectAccessChecker.checkAccess(projectId, "办理收入业务");
        Map<String,Object> row = one("""
                SELECT c.id,c.project_id,c.party_a_id,c.contract_type,c.contract_status,c.approval_status,c.current_amount,p.status project_status
                  FROM ct_contract c JOIN pm_project p ON p.id=c.project_id AND p.tenant_id=c.tenant_id AND p.deleted_flag=0
                 WHERE c.id=? AND c.tenant_id=? AND c.deleted_flag=0
                 FOR UPDATE
                """, contractId, tenant());
        if (row == null || !Objects.equals(longValue(row.get("project_id")), projectId)) throw error("REVENUE_CONTRACT_PROJECT_MISMATCH", "业主合同不属于所选项目");
        if (!ProjectStatusConstants.allowsFinancialSettlement(string(row.get("project_status")))) {
            throw error("REVENUE_PROJECT_NOT_ACTIVE", "只有施工中、竣工或质保阶段项目可以办理收入业务");
        }
        if (!"MAIN".equals(row.get("contract_type")) || !"APPROVED".equals(row.get("approval_status")) || !"PERFORMING".equals(row.get("contract_status"))) {
            throw error("REVENUE_CONTRACT_NOT_PERFORMING", "只有已审批且履约中的 MAIN 业主合同可以办理收入业务");
        }
        if (!Objects.equals(longValue(row.get("party_a_id")), customerId)) throw error("REVENUE_CUSTOMER_MISMATCH", "客户必须为业主合同甲方");
        return row;
    }

    private void requireProjectVisible(Long projectId) {
        projectAccessChecker.checkAccess(projectId, "查看收入回款");
    }

    private void ensureSettlementWithinContract(Long contractId, Long excludedSettlementId,
                                                BigDecimal grossAmount, BigDecimal contractAmount) {
        BigDecimal reserved = decimal(jdbc.queryForObject("""
                SELECT COALESCE(SUM(gross_amount),0)
                  FROM owner_settlement
                 WHERE tenant_id=? AND contract_id=? AND deleted_flag=0
                   AND status<>'REJECTED' AND (? IS NULL OR id<>?)
                """, BigDecimal.class, tenant(), contractId, excludedSettlementId, excludedSettlementId));
        if (reserved.add(money(grossAmount)).compareTo(contractAmount) > 0) {
            throw error("OWNER_SETTLEMENT_CONTRACT_EXCEEDED", "累计业主结算金额不能超过合同当前金额");
        }
    }

    private int cleanOwnerSettlementAttachmentCount(Long settlementId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_file f
                 WHERE f.tenant_id=? AND f.deleted_flag=0 AND f.virus_scan_status='CLEAN'
                   AND f.document_type<>'GENERATED_DOCUMENT'
                   AND ((f.business_type='OWNER_SETTLEMENT' AND f.business_id=?)
                     OR (f.business_type='OWNER_MEASUREMENT_SUBMISSION'
                       AND f.document_type='OWNER_CONFIRMATION'
                       AND f.business_id=(SELECT s.owner_submission_id FROM owner_settlement s
                                          WHERE s.id=? AND s.tenant_id=? AND s.deleted_flag=0)))
                """, Integer.class, tenant(), settlementId, settlementId, tenant());
        return count == null ? 0 : count;
    }

    private int cleanAttachmentCount(String businessType, Long businessId, List<String> documentTypes) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenant());
        parameters.add(businessType);
        parameters.add(businessId);
        parameters.addAll(documentTypes);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_file WHERE tenant_id=? AND business_type=?"
                        + " AND business_id=? AND deleted_flag=0 AND virus_scan_status='CLEAN' AND document_type IN ("
                        + placeholders(documentTypes.size()) + ")",
                Integer.class, parameters.toArray());
        return count == null ? 0 : count;
    }

    private List<Long> visibleProjectIds(Long projectId, String action) {
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, action);
            return List.of(projectId);
        }
        return projectAccessChecker.accessibleProjectIds();
    }

    private List<Object> scopedArguments(List<Long> projectIds, String status) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenant());
        arguments.addAll(projectIds);
        arguments.add(status);
        arguments.add(status);
        return arguments;
    }

    private BigDecimal allocationTotal(List<AmountAllocation> allocations) {
        if (allocations == null) return BigDecimal.ZERO.setScale(2);
        return allocations.stream().map(a -> money(a.amount())).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalar(String sql, Long projectId) {
        return decimal(jdbc.queryForObject(sql, BigDecimal.class, tenant(), projectId));
    }

    private Map<String,Object> one(String sql, Object... args) {
        try { return jdbc.queryForMap(sql, args); }
        catch (EmptyResultDataAccessException e) { return null; }
    }

    private Long tenant() { return UserContext.getCurrentTenantId(); }
    private Long user() { return UserContext.getCurrentUserId(); }
    private BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP); }
    private Long longValue(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private int intValue(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private String string(Object value) { return value == null ? null : value.toString(); }
    private String text(Map<String,Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }
    private Integer integer(Map<String,Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).intValue();
    }
    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }
    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
    private String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
    private Object[] args(Object first, Collection<?> rest) { List<Object> values = new ArrayList<>(); values.add(first); values.addAll(rest); return values.toArray(); }
}
