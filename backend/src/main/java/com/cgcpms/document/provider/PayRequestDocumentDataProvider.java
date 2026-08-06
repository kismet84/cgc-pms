package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PaymentTraceService;
import com.cgcpms.payment.vo.PayApplicationBasisVO;
import com.cgcpms.payment.vo.PayApplicationVO;
import com.cgcpms.payment.vo.PaymentTraceVO;
import com.cgcpms.project.entity.PmProject;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
public class PayRequestDocumentDataProvider implements DocumentDataProvider {
    protected static final DocumentTemplateFieldCatalog.Field[] FIELDS = {
            field("payment.applyCode", "申请编号", "TEXT", false),
            field("payment.applyAmount", "申请金额", "MONEY", false),
            field("payment.approvedAmount", "批准金额", "MONEY", true),
            field("payment.actualPayAmount", "实付金额", "MONEY", false),
            field("payment.payType", "付款类型", "ENUM", true),
            field("payment.payStatus", "付款状态", "ENUM", true),
            field("payment.approvalStatus", "审批状态", "ENUM", false),
            field("payment.expenseCategory", "费用类别", "TEXT", true),
            field("payment.applyReason", "申请事由", "TEXT", true),
            field("payment.createdAt", "申请时间", "DATETIME", true),
            field("payment.updatedAt", "更新时间", "DATETIME", true),
            field("payment.remark", "备注", "TEXT", true),
            field("project.code", "项目编号", "TEXT", true),
            field("project.name", "项目名称", "TEXT", true),
            field("project.address", "项目地址", "TEXT", true),
            field("project.ownerUnit", "建设单位", "TEXT", true),
            field("contract.code", "合同编号", "TEXT", true),
            field("contract.name", "合同名称", "TEXT", true),
            field("contract.amount", "合同金额", "MONEY", true),
            field("payee.name", "收款单位", "TEXT", true),
            item("basis.type", "付款依据类型", "ENUM", "basis"),
            item("basis.amount", "付款依据金额", "MONEY", "basis"),
            item("basis.remark", "付款依据备注", "TEXT", "basis"),
            item("sources.type", "付款来源类型", "ENUM", "sources"),
            item("sources.amount", "付款来源金额", "MONEY", "sources"),
            item("sources.paidAmount", "付款来源已付金额", "MONEY", "sources"),
            item("invoices.number", "发票号码", "TEXT", "invoices"),
            item("invoices.type", "发票类型", "ENUM", "invoices"),
            item("invoices.amount", "发票金额", "MONEY", "invoices"),
            item("invoices.taxAmount", "发票税额", "MONEY", "invoices"),
            item("invoices.date", "开票日期", "DATE", "invoices"),
            item("invoices.sellerName", "销方名称", "TEXT", "invoices"),
            item("invoices.buyerName", "购方名称", "TEXT", "invoices"),
            item("invoices.verifyStatus", "验真状态", "ENUM", "invoices")
    };
    private static final String SCHEMA = "pay-request.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PAY_REQUEST", SCHEMA, FIELDS);

    protected final PayApplicationService service;
    protected final PaymentTraceService traceService;

    public PayRequestDocumentDataProvider(PayApplicationService service, PaymentTraceService traceService) {
        this.service = service;
        this.traceService = traceService;
    }

    public String businessType() { return "PAY_REQUEST"; }
    public String displayName() { return "付款申请"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "payment:app:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long businessId) { return createSnapshot(businessId, true); }
    public DocumentDataSnapshot loadPreview(Long businessId) { return createSnapshot(businessId, false); }

    protected DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        PayApplicationVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_PAY_REQUEST_STATE_INVALID", "付款申请文档");
        PaymentTraceVO trace = traceService.byApplication(id);
        return snapshot(schemaVersion(),
                "payment", map("applyCode", text(value.getApplyCode()), "applyAmount", money(value.getApplyAmount()),
                        "approvedAmount", money(value.getApprovedAmount()),
                        "actualPayAmount", money(value.getActualPayAmount()), "payType", text(value.getPayType()),
                        "payStatus", text(value.getPayStatus()), "approvalStatus", text(value.getApprovalStatus()),
                        "expenseCategory", text(value.getExpenseCategory()), "applyReason", text(value.getApplyReason()),
                        "createdAt", text(value.getCreatedAt()), "updatedAt", text(value.getUpdatedAt()),
                        "remark", text(value.getRemark())),
                "project", project(trace.getProject()),
                "contract", contract(trace.getContract()),
                "payee", map("name", text(value.getPartnerName())),
                "basis", rows(value.getBasis(), this::basis),
                "sources", rows(trace.getApplicationSources(), this::source),
                "invoices", rows(trace.getInvoices(), this::invoice));
    }

    private Map<String, Object> project(PmProject value) {
        return value == null ? map("code", "", "name", "", "address", "", "ownerUnit", "")
                : map("code", text(value.getProjectCode()), "name", text(value.getProjectName()),
                "address", text(value.getProjectAddress()), "ownerUnit", text(value.getOwnerUnit()));
    }

    private Map<String, Object> contract(CtContract value) {
        return value == null ? map("code", "", "name", "", "amount", "0.00")
                : map("code", text(value.getContractCode()), "name", text(value.getContractName()),
                "amount", money(value.getCurrentAmount()));
    }

    private Map<String, Object> basis(PayApplicationBasisVO value) {
        return map("type", text(value.getBasisType()), "amount", money(value.getBasisAmount()),
                "remark", text(value.getRemark()));
    }

    private Map<String, Object> source(PaymentApplicationSource value) {
        return map("type", text(value.getSourceType()), "amount", money(value.getSourceAmount()),
                "paidAmount", money(value.getPaidAmount()));
    }

    private Map<String, Object> invoice(PayInvoice value) {
        return map("number", text(value.getInvoiceNo()), "type", text(value.getInvoiceType()),
                "amount", money(value.getInvoiceAmount()), "taxAmount", money(value.getTaxAmount()),
                "date", text(value.getInvoiceDate()), "sellerName", text(value.getSellerName()),
                "buyerName", text(value.getBuyerName()), "verifyStatus", text(value.getVerifyStatus()));
    }
}
