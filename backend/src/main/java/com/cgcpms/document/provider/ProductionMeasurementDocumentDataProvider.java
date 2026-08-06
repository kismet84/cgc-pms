package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.measurement.service.ProductionMeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ProductionMeasurementDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "production-measurement.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PRODUCTION_MEASUREMENT", SCHEMA,
            field("measurement.code", "产值计量编号", "TEXT", false), field("measurement.periodCode", "计量周期编号", "TEXT", true),
            field("measurement.periodName", "计量周期名称", "TEXT", true), field("measurement.date", "计量日期", "DATE", true),
            field("measurement.currentReportedAmount", "本期申报金额", "MONEY", false),
            field("measurement.cumulativeReportedAmount", "累计申报金额", "MONEY", false),
            field("measurement.status", "业务状态", "ENUM", false), field("measurement.approvalStatus", "审批状态", "ENUM", false),
            field("measurement.createdAt", "创建时间", "DATETIME", true), field("measurement.updatedAt", "更新时间", "DATETIME", true),
            field("measurement.remark", "备注", "TEXT", true), item("lines.itemCode", "清单编码", "TEXT", "lines"),
            item("lines.itemName", "清单名称", "TEXT", "lines"), item("lines.specification", "规格型号", "TEXT", "lines"),
            item("lines.unit", "单位", "TEXT", "lines"), item("lines.sourceType", "来源类型", "ENUM", "lines"),
            item("lines.contractQuantity", "合同数量", "NUMBER", "lines"),
            item("lines.priorApprovedQuantity", "上期累计数量", "NUMBER", "lines"),
            item("lines.currentReportedQuantity", "本期申报数量", "NUMBER", "lines"),
            item("lines.cumulativeReportedQuantity", "累计申报数量", "NUMBER", "lines"),
            item("lines.unitPrice", "单价", "MONEY", "lines"), item("lines.currentReportedAmount", "本期申报金额", "MONEY", "lines"),
            item("lines.cumulativeReportedAmount", "累计申报金额", "MONEY", "lines"),
            item("submissions.code", "业主报量编号", "TEXT", "submissions"), item("submissions.revisionNo", "报量版本", "NUMBER", "submissions"),
            item("submissions.submittedAt", "报送时间", "DATETIME", "submissions"),
            item("submissions.externalDocumentNo", "外部文号", "TEXT", "submissions"),
            item("submissions.submittedAmount", "报送金额", "MONEY", "submissions"),
            item("submissions.confirmedAmount", "确认金额", "MONEY", "submissions"),
            item("submissions.deductedAmount", "扣减金额", "MONEY", "submissions"),
            item("submissions.status", "报量状态", "ENUM", "submissions"), item("submissions.remark", "报量备注", "TEXT", "submissions"));

    private final ProductionMeasurementService service;
    public String businessType() { return "PRODUCTION_MEASUREMENT"; }
    public String displayName() { return "产值计量"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "measurement:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> value = service.measurement(id);
        String approval = text(value(value, "approval_status"));
        requireApproval(approval, formal, "DOCUMENT_MEASUREMENT_STATE_INVALID", "产值计量文档");
        return snapshot(SCHEMA, "measurement", map("code", text(value(value, "measure_code")),
                "periodCode", text(value(value, "period_code")), "periodName", text(value(value, "period_name")),
                "date", text(value(value, "measure_date")), "currentReportedAmount", money(value(value, "current_reported_amount")),
                "cumulativeReportedAmount", money(value(value, "cumulative_reported_amount")),
                "status", text(value(value, "status")), "approvalStatus", approval,
                "createdAt", text(value(value, "created_at")), "updatedAt", text(value(value, "updated_at")),
                "remark", text(value(value, "remark"))), "lines", rows(mapRows(value, "lines"), this::lineRow),
                "submissions", rows(mapRows(value, "submissions"), this::submissionRow));
    }

    private Map<String, Object> lineRow(Map<String, Object> value) {
        return map("itemCode", text(value(value, "item_code")), "itemName", text(value(value, "item_name")),
                "specification", text(value(value, "item_spec")), "unit", text(value(value, "unit")),
                "sourceType", text(value(value, "source_type")), "contractQuantity", number(value(value, "contract_quantity")),
                "priorApprovedQuantity", number(value(value, "prior_approved_quantity")),
                "currentReportedQuantity", number(value(value, "current_reported_quantity")),
                "cumulativeReportedQuantity", number(value(value, "cumulative_reported_quantity")),
                "unitPrice", money(value(value, "unit_price")), "currentReportedAmount", money(value(value, "current_reported_amount")),
                "cumulativeReportedAmount", money(value(value, "cumulative_reported_amount")));
    }

    private Map<String, Object> submissionRow(Map<String, Object> value) {
        return map("code", text(value(value, "submission_code")), "revisionNo", number(value(value, "revision_no")),
                "submittedAt", text(value(value, "submitted_at")), "externalDocumentNo", text(value(value, "external_document_no")),
                "submittedAmount", money(value(value, "submitted_amount")), "confirmedAmount", money(value(value, "confirmed_amount")),
                "deductedAmount", money(value(value, "deducted_amount")), "status", text(value(value, "status")),
                "remark", text(value(value, "remark")));
    }
}
