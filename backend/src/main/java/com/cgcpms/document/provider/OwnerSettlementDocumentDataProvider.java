package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.revenue.service.RevenueOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class OwnerSettlementDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "owner-settlement.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("OWNER_SETTLEMENT", SCHEMA,
            field("settlement.code", "业主结算编号", "TEXT", false), field("settlement.period", "结算期间", "TEXT", true),
            field("settlement.date", "结算日期", "DATE", true), field("settlement.grossAmount", "含税结算金额", "MONEY", false),
            field("settlement.taxAmount", "税额", "MONEY", false), field("settlement.retentionAmount", "保留金", "MONEY", false),
            field("settlement.netReceivableAmount", "应收净额", "MONEY", false), field("settlement.dueDate", "到期日期", "DATE", true),
            field("settlement.status", "审批状态", "ENUM", false), field("settlement.remark", "备注", "TEXT", true));

    private final RevenueOperationsService service;
    public String businessType() { return "OWNER_SETTLEMENT"; }
    public String displayName() { return "业主结算"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "revenue:operations:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> value = service.settlement(id);
        String status = text(value(value, "status"));
        requireApproval(status, formal, "DOCUMENT_OWNER_SETTLEMENT_STATE_INVALID", "业主结算文档");
        return snapshot(SCHEMA, "settlement", map("code", text(value(value, "settlement_code")),
                "period", text(value(value, "settlement_period")), "date", text(value(value, "settlement_date")),
                "grossAmount", money(value(value, "gross_amount")), "taxAmount", money(value(value, "tax_amount")),
                "retentionAmount", money(value(value, "retention_amount")),
                "netReceivableAmount", money(value(value, "net_receivable_amount")),
                "dueDate", text(value(value, "due_date")), "status", status, "remark", text(value(value, "remark"))));
    }
}
