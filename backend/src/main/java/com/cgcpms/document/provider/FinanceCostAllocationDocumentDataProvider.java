package com.cgcpms.document.provider;

import com.cgcpms.cost.service.CostSubjectV2Service;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class FinanceCostAllocationDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "finance-cost-allocation.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("FINANCE_COST_ALLOCATION", SCHEMA,
            field("allocation.batchCode", "分摊批次号", "TEXT", false), field("allocation.sourceType", "来源类型", "ENUM", false),
            field("allocation.sourceAmount", "来源金额", "MONEY", false), field("allocation.allocationBasis", "分摊依据", "ENUM", false),
            field("allocation.accountingPeriod", "会计期间", "TEXT", false), field("allocation.subjectCode", "成本科目编码", "TEXT", false),
            field("allocation.subjectName", "成本科目名称", "TEXT", false), field("allocation.status", "状态", "ENUM", false),
            field("allocation.postedAt", "过账时间", "DATETIME", true), field("allocation.remark", "备注", "TEXT", true),
            item("items.projectCode", "项目编码", "TEXT", "items"), item("items.projectName", "项目名称", "TEXT", "items"),
            item("items.basisValue", "依据值", "NUMBER", "items"), item("items.allocatedAmount", "分摊金额", "MONEY", "items"));

    private final CostSubjectV2Service service;
    public String businessType() { return "FINANCE_COST_ALLOCATION"; }
    public String displayName() { return "财务费用分摊"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "cost:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> result = service.financeAllocationDetail(id);
        Map<String, Object> main = mapValue(result, "main");
        requireState(text(value(main, "status")), formal, Set.of("POSTED"), Set.of("POSTED"),
                "DOCUMENT_FINANCE_ALLOCATION_STATE_INVALID", "财务费用分摊单");
        return snapshot(SCHEMA, "allocation", allocation(main), "items", rows(mapRows(result, "items"), this::itemRow));
    }

    private Map<String, Object> allocation(Map<String, Object> v) {
        return map("batchCode", text(value(v, "batchCode")), "sourceType", text(value(v, "sourceType")),
                "sourceAmount", money(value(v, "sourceAmount")), "allocationBasis", text(value(v, "allocationBasis")),
                "accountingPeriod", text(value(v, "accountingPeriod")), "subjectCode", text(value(v, "subjectCode")),
                "subjectName", text(value(v, "subjectName")), "status", text(value(v, "status")),
                "postedAt", text(value(v, "postedAt")), "remark", text(value(v, "remark")));
    }

    private Map<String, Object> itemRow(Map<String, Object> v) {
        return map("projectCode", text(value(v, "projectCode")), "projectName", text(value(v, "projectName")),
                "basisValue", number(value(v, "basisValue")), "allocatedAmount", money(value(v, "allocatedAmount")));
    }
}
