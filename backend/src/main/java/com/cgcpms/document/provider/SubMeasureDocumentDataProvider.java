package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.subcontract.service.SubMeasureService;
import com.cgcpms.subcontract.vo.SubMeasureItemVO;
import com.cgcpms.subcontract.vo.SubMeasureVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class SubMeasureDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "sub-measure.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("SUB_MEASURE", SCHEMA,
            field("measure.code", "计量编号", "TEXT", false), field("measure.period", "计量期间", "TEXT", true),
            field("measure.date", "计量日期", "DATE", true), field("measure.reportedAmount", "报量金额", "MONEY", false),
            field("measure.approvedAmount", "审定金额", "MONEY", false), field("measure.deductionAmount", "扣款金额", "MONEY", false),
            field("measure.netAmount", "净额", "MONEY", false), field("measure.approvalStatus", "审批状态", "ENUM", false),
            field("measure.status", "业务状态", "ENUM", false), field("measure.costGenerated", "是否已生成成本", "BOOLEAN", false),
            field("measure.createdAt", "创建时间", "DATETIME", true), field("measure.updatedAt", "更新时间", "DATETIME", true),
            field("measure.remark", "备注", "TEXT", true), field("project.name", "项目名称", "TEXT", true),
            field("contract.name", "合同名称", "TEXT", true), field("partner.name", "分包单位", "TEXT", true),
            field("task.code", "任务编号", "TEXT", true), field("task.name", "任务名称", "TEXT", true),
            item("items.name", "清单项名称", "TEXT", "items"), item("items.unit", "单位", "TEXT", "items"),
            item("items.contractQuantity", "合同数量", "NUMBER", "items"), item("items.currentQuantity", "本期数量", "NUMBER", "items"),
            item("items.cumulativeQuantity", "累计数量", "NUMBER", "items"), item("items.unitPrice", "单价", "MONEY", "items"),
            item("items.amount", "金额", "MONEY", "items"), item("items.remark", "明细备注", "TEXT", "items"));

    private final SubMeasureService service;

    public String businessType() { return "SUB_MEASURE"; }
    public String displayName() { return "分包计量"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "subcontract:measure:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        SubMeasureVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_SUB_MEASURE_STATE_INVALID", "分包计量文档");
        return snapshot(SCHEMA,
                "measure", map("code", text(value.getMeasureCode()), "period", text(value.getMeasurePeriod()),
                        "date", text(value.getMeasureDate()), "reportedAmount", money(value.getReportedAmount()),
                        "approvedAmount", money(value.getApprovedAmount()), "deductionAmount", money(value.getDeductionAmount()),
                        "netAmount", money(value.getNetAmount()), "approvalStatus", text(value.getApprovalStatus()),
                        "status", text(value.getStatus()), "costGenerated", Integer.valueOf(1).equals(value.getCostGeneratedFlag()),
                        "createdAt", text(value.getCreatedAt()), "updatedAt", text(value.getUpdatedAt()), "remark", text(value.getRemark())),
                "project", map("name", text(value.getProjectName())), "contract", map("name", text(value.getContractName())),
                "partner", map("name", text(value.getPartnerName())),
                "task", map("code", text(value.getSubTaskCode()), "name", text(value.getSubTaskName())),
                "items", rows(value.getItems(), this::itemRow));
    }

    private Map<String, Object> itemRow(SubMeasureItemVO value) {
        return map("name", text(value.getItemName()), "unit", text(value.getUnit()),
                "contractQuantity", number(value.getContractQuantity()), "currentQuantity", number(value.getCurrentQuantity()),
                "cumulativeQuantity", number(value.getCumulativeQuantity()), "unitPrice", money(value.getUnitPrice()),
                "amount", money(value.getAmount()), "remark", text(value.getRemark()));
    }
}
