package com.cgcpms.document.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayApplicationBasis;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.mapper.PayApplicationBasisMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.service.PaymentTraceService;
import com.cgcpms.payment.vo.PaymentTraceVO;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.workflow.entity.WfRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Profile("!document-test-provider")
@RequiredArgsConstructor
public class PaymentDocumentDataProvider implements DocumentDataProvider {
    public static final String SCHEMA_VERSION = "payment.v1";

    private final PayApplicationMapper applicationMapper;
    private final PaymentTraceService traceService;
    private final PayApplicationBasisMapper basisMapper;
    private final MdPartnerMapper partnerMapper;
    private final PayInvoiceMapper invoiceMapper;
    private final SysFileMapper fileMapper;

    @Override
    public String businessType() { return "PAYMENT"; }

    @Override
    public DocumentDataSnapshot load(Long businessId) {
        return loadSnapshot(businessId, true);
    }

    @Override
    public DocumentDataSnapshot loadPreview(Long businessId) {
        return loadSnapshot(businessId, false);
    }

    private DocumentDataSnapshot loadSnapshot(Long businessId, boolean formal) {
        Long tenantId = UserContext.getCurrentTenantId();
        PayApplication payment = applicationMapper.selectOne(new LambdaQueryWrapper<PayApplication>()
                .eq(PayApplication::getId, businessId)
                .eq(PayApplication::getTenantId, tenantId));
        if (payment == null) {
            throw new BusinessException("DOCUMENT_BUSINESS_NOT_FOUND", "付款申请不存在");
        }
        if (formal && !"APPROVED".equals(payment.getApprovalStatus())) {
            throw new BusinessException("DOCUMENT_PAYMENT_NOT_APPROVED", "正式付款申请单仅允许审批通过后生成");
        }
        if (!formal && !List.of("APPROVING", "APPROVED").contains(payment.getApprovalStatus())) {
            throw new BusinessException("DOCUMENT_PAYMENT_PREVIEW_STATE_INVALID", "付款申请仅允许审批中或审批通过后预览");
        }

        PaymentTraceVO trace = traceService.byApplication(businessId);
        List<PayApplicationBasis> basis = basisMapper.selectList(new LambdaQueryWrapper<PayApplicationBasis>()
                .eq(PayApplicationBasis::getTenantId, tenantId)
                .eq(PayApplicationBasis::getPayApplicationId, businessId)
                .orderByAsc(PayApplicationBasis::getCreatedAt));
        List<PayInvoice> invoices = invoiceMapper.selectList(new LambdaQueryWrapper<PayInvoice>()
                .eq(PayInvoice::getTenantId, tenantId)
                .eq(PayInvoice::getPayApplicationId, businessId)
                .orderByAsc(PayInvoice::getInvoiceDate));
        List<SysFile> attachments = fileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId)
                .eq(SysFile::getBusinessType, "PAYMENT")
                .eq(SysFile::getBusinessId, businessId)
                .ne(SysFile::getDocumentType, "GENERATED_DOCUMENT")
                .orderByAsc(SysFile::getCreatedAt));
        MdPartner payee = payment.getPartnerId() == null ? null : partnerMapper.selectById(payment.getPartnerId());
        if (payee != null && !Objects.equals(payee.getTenantId(), tenantId)) payee = null;

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("payment", payment(payment));
        root.put("project", project(trace.getProject()));
        root.put("contract", contract(trace.getContract()));
        root.put("payee", payee(payee));
        root.put("sources", rows(trace.getApplicationSources(), this::source));
        root.put("basis", rows(basis, this::basis));
        root.put("invoices", rows(invoices, this::invoice));
        root.put("attachments", rows(attachments, this::attachment));
        root.put("approval", approval(trace));
        root.put("approvalRecords", rows(trace.getApprovalRecords(), this::approvalRecord));
        return new DocumentDataSnapshot(SCHEMA_VERSION, root);
    }

    private Map<String, Object> payment(PayApplication value) {
        Map<String, Object> row = row();
        put(row, "id", value.getId());
        put(row, "applyCode", value.getApplyCode());
        put(row, "applyAmount", money(value.getApplyAmount()));
        put(row, "approvedAmount", money(value.getApprovedAmount()));
        put(row, "actualPayAmount", money(value.getActualPayAmount()));
        put(row, "payType", value.getPayType());
        put(row, "payStatus", value.getPayStatus());
        put(row, "approvalStatus", value.getApprovalStatus());
        put(row, "applyReason", value.getApplyReason());
        put(row, "createdAt", value.getCreatedAt());
        return row;
    }

    private Map<String, Object> project(PmProject value) {
        Map<String, Object> row = row();
        put(row, "code", value == null ? null : value.getProjectCode());
        put(row, "name", value == null ? null : value.getProjectName());
        put(row, "address", value == null ? null : value.getProjectAddress());
        put(row, "ownerUnit", value == null ? null : value.getOwnerUnit());
        return row;
    }

    private Map<String, Object> contract(CtContract value) {
        Map<String, Object> row = row();
        put(row, "code", value == null ? null : value.getContractCode());
        put(row, "name", value == null ? null : value.getContractName());
        put(row, "amount", money(value == null ? null : value.getCurrentAmount()));
        put(row, "paidAmount", money(value == null ? null : value.getPaidAmount()));
        return row;
    }

    private Map<String, Object> payee(MdPartner value) {
        Map<String, Object> row = row();
        put(row, "name", value == null ? null : value.getPartnerName());
        put(row, "bankName", value == null ? null : value.getBankName());
        put(row, "bankAccount", maskBank(value == null ? null : value.getBankAccount()));
        put(row, "contactName", value == null ? null : value.getContactName());
        put(row, "contactPhone", maskPhone(value == null ? null : value.getContactPhone()));
        return row;
    }

    private Map<String, Object> source(PaymentApplicationSource value) {
        Map<String, Object> row = row();
        put(row, "type", value.getSourceType());
        put(row, "referenceId", value.getSourceRefId());
        put(row, "amount", money(value.getSourceAmount()));
        put(row, "paidAmount", money(value.getPaidAmount()));
        return row;
    }

    private Map<String, Object> basis(PayApplicationBasis value) {
        Map<String, Object> row = row();
        put(row, "type", value.getBasisType());
        put(row, "referenceId", value.getBasisId());
        put(row, "amount", money(value.getBasisAmount()));
        return row;
    }

    private Map<String, Object> invoice(PayInvoice value) {
        Map<String, Object> row = row();
        put(row, "number", value.getInvoiceNo());
        put(row, "type", value.getInvoiceType());
        put(row, "amount", money(value.getInvoiceAmount()));
        put(row, "taxAmount", money(value.getTaxAmount()));
        put(row, "date", value.getInvoiceDate());
        put(row, "sellerName", value.getSellerName());
        put(row, "buyerName", value.getBuyerName());
        put(row, "verifyStatus", value.getVerifyStatus());
        return row;
    }

    private Map<String, Object> attachment(SysFile value) {
        Map<String, Object> row = row();
        put(row, "name", value.getOriginalName());
        put(row, "type", value.getDocumentType());
        put(row, "size", value.getFileSize());
        return row;
    }

    private Map<String, Object> approval(PaymentTraceVO trace) {
        Map<String, Object> row = row();
        put(row, "status", trace.getApprovalInstance() == null ? null : trace.getApprovalInstance().getInstanceStatus());
        put(row, "startedAt", trace.getApprovalInstance() == null ? null : trace.getApprovalInstance().getStartedAt());
        put(row, "endedAt", trace.getApprovalInstance() == null ? null : trace.getApprovalInstance().getEndedAt());
        return row;
    }

    private Map<String, Object> approvalRecord(WfRecord value) {
        Map<String, Object> row = row();
        put(row, "node", value.getNodeName());
        put(row, "action", value.getActionName());
        put(row, "operator", value.getOperatorName());
        put(row, "comment", value.getComment());
        put(row, "time", value.getCreatedAt());
        return row;
    }

    private <T> List<Map<String, Object>> rows(List<T> values, java.util.function.Function<T, Map<String, Object>> mapper) {
        if (values == null || values.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>(values.size());
        values.forEach(value -> result.add(mapper.apply(value)));
        return result;
    }

    private Map<String, Object> row() { return new LinkedHashMap<>(); }

    private void put(Map<String, Object> target, String key, Object value) {
        target.put(key, value == null ? "" : value instanceof TemporalAccessor ? value.toString() : String.valueOf(value));
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String maskBank(String value) {
        if (value == null || value.isBlank()) return "";
        String compact = value.replaceAll("\\s+", "");
        return "****" + compact.substring(Math.max(0, compact.length() - 4));
    }

    private String maskPhone(String value) {
        if (value == null || value.isBlank()) return "";
        String compact = value.replaceAll("\\s+", "");
        if (compact.length() <= 7) return "***" + compact.substring(Math.max(0, compact.length() - 4));
        return compact.substring(0, 3) + "****" + compact.substring(compact.length() - 4);
    }
}
