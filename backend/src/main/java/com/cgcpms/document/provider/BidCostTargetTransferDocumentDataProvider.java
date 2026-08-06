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
public class BidCostTargetTransferDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "bid-cost-target-transfer.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("BID_COST_TARGET_TRANSFER", SCHEMA,
            field("transfer.bidCode", "投标成本编码", "TEXT", false), field("transfer.bidProjectName", "投标项目名称", "TEXT", false),
            field("transfer.projectCode", "项目编码", "TEXT", false), field("transfer.projectName", "项目名称", "TEXT", false),
            field("transfer.targetVersionNo", "目标成本版本号", "NUMBER", false), field("transfer.targetVersionName", "目标成本版本名", "TEXT", false),
            field("transfer.mappingVersionCode", "映射版本编码", "TEXT", false), field("transfer.mappingVersionName", "映射版本名", "TEXT", false),
            field("transfer.transferCode", "转入单号", "TEXT", false), field("transfer.totalAmount", "转入总额", "MONEY", false),
            field("transfer.status", "状态", "ENUM", false), field("transfer.postedAt", "过账时间", "DATETIME", true),
            field("transfer.remark", "备注", "TEXT", true), item("items.sourceSubjectCode", "源科目编码", "TEXT", "items"),
            item("items.sourceSubjectName", "源科目名称", "TEXT", "items"), item("items.targetSubjectCode", "目标科目编码", "TEXT", "items"),
            item("items.targetSubjectName", "目标科目名称", "TEXT", "items"), item("items.amount", "转入金额", "MONEY", "items"));

    private final CostSubjectV2Service service;
    public String businessType() { return "BID_COST_TARGET_TRANSFER"; }
    public String displayName() { return "投标成本转入"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "cost:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> result = service.bidCostTransferDetail(id);
        Map<String, Object> main = mapValue(result, "main");
        requireState(text(value(main, "status")), formal, Set.of("POSTED"), Set.of("POSTED"),
                "DOCUMENT_BID_COST_TRANSFER_STATE_INVALID", "投标成本转入单");
        return snapshot(SCHEMA, "transfer", transfer(main), "items", rows(mapRows(result, "items"), this::itemRow));
    }

    private Map<String, Object> transfer(Map<String, Object> v) {
        return map("bidCode", text(value(v, "bidCode")), "bidProjectName", text(value(v, "bidProjectName")),
                "projectCode", text(value(v, "projectCode")), "projectName", text(value(v, "projectName")),
                "targetVersionNo", number(value(v, "targetVersionNo")), "targetVersionName", text(value(v, "targetVersionName")),
                "mappingVersionCode", text(value(v, "mappingVersionCode")), "mappingVersionName", text(value(v, "mappingVersionName")),
                "transferCode", text(value(v, "transferCode")), "totalAmount", money(value(v, "totalAmount")),
                "status", text(value(v, "status")), "postedAt", text(value(v, "postedAt")), "remark", text(value(v, "remark")));
    }

    private Map<String, Object> itemRow(Map<String, Object> v) {
        return map("sourceSubjectCode", text(value(v, "sourceSubjectCode")), "sourceSubjectName", text(value(v, "sourceSubjectName")),
                "targetSubjectCode", text(value(v, "targetSubjectCode")), "targetSubjectName", text(value(v, "targetSubjectName")),
                "amount", money(value(v, "amount")));
    }
}
