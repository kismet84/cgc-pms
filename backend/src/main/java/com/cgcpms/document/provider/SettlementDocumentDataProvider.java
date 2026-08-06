package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.settlement.service.StlSettlementQueryService;
import com.cgcpms.settlement.vo.SettlementCostItemVO;
import com.cgcpms.settlement.vo.SettlementPaymentItemVO;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.variation.vo.VarOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class SettlementDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "settlement.v2";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("SETTLEMENT", SCHEMA,
            field("settlement.code", "结算编号", "TEXT", false),
            field("settlement.type", "结算类型", "ENUM", true),
            field("settlement.status", "业务状态", "ENUM", true),
            field("settlement.approvalStatus", "审批状态", "ENUM", false),
            field("settlement.finalStatus", "定案状态", "ENUM", false),
            field("settlement.contractAmount", "合同金额", "MONEY", false),
            field("settlement.changeAmount", "变更金额", "MONEY", false),
            field("settlement.measuredAmount", "累计计量金额", "MONEY", false),
            field("settlement.deductionAmount", "扣款金额", "MONEY", false),
            field("settlement.paidAmount", "已付金额", "MONEY", false),
            field("settlement.finalAmount", "定案金额", "MONEY", false),
            field("settlement.unpaidAmount", "未付金额", "MONEY", false),
            field("settlement.warrantyAmount", "质保金额", "MONEY", false),
            field("settlement.finalizedAt", "定案时间", "DATETIME", true),
            field("settlement.createdAt", "创建时间", "DATETIME", true),
            field("settlement.updatedAt", "更新时间", "DATETIME", true),
            field("settlement.remark", "备注", "TEXT", true),
            field("project.name", "项目名称", "TEXT", true),
            field("contract.name", "合同名称", "TEXT", true),
            field("partner.name", "结算对象", "TEXT", true),
            item("items.name", "结算明细名称", "TEXT", "items"),
            item("items.unit", "单位", "TEXT", "items"),
            item("items.quantity", "数量", "NUMBER", "items"),
            item("items.unitPrice", "单价", "MONEY", "items"),
            item("items.amount", "金额", "MONEY", "items"),
            item("items.sourceType", "来源类型", "TEXT", "items"),
            item("items.remark", "明细备注", "TEXT", "items"),
            item("variations.code", "变更编号", "TEXT", "variations"),
            item("variations.name", "变更名称", "TEXT", "variations"),
            item("variations.type", "变更类型", "ENUM", "variations"),
            item("variations.direction", "变更方向", "ENUM", "variations"),
            item("variations.confirmedAmount", "确认金额", "MONEY", "variations"),
            item("variations.status", "审批状态", "ENUM", "variations"),
            item("payments.applyCode", "付款申请编号", "TEXT", "payments"),
            item("payments.payType", "付款类型", "ENUM", "payments"),
            item("payments.applyAmount", "申请金额", "MONEY", "payments"),
            item("payments.approvedAmount", "批准金额", "MONEY", "payments"),
            item("payments.actualPayAmount", "实付金额", "MONEY", "payments"),
            item("payments.status", "付款状态", "ENUM", "payments"),
            item("payments.payDate", "付款日期", "DATE", "payments"),
            item("payments.voucherNo", "付款凭证号", "TEXT", "payments"),
            item("costs.subjectName", "成本科目", "TEXT", "costs"),
            item("costs.type", "成本类型", "ENUM", "costs"),
            item("costs.sourceType", "成本来源类型", "TEXT", "costs"),
            item("costs.amount", "成本含税金额", "MONEY", "costs"),
            item("costs.taxAmount", "成本税额", "MONEY", "costs"),
            item("costs.amountWithoutTax", "成本不含税金额", "MONEY", "costs"),
            item("costs.date", "成本日期", "DATE", "costs"),
            item("costs.status", "成本状态", "ENUM", "costs"));

    private final StlSettlementQueryService service;

    public String businessType() { return "SETTLEMENT"; }
    public String displayName() { return "结算单"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "settlement:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long businessId) { return createSnapshot(businessId, true); }
    public DocumentDataSnapshot loadPreview(Long businessId) { return createSnapshot(businessId, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        StlSettlementVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_SETTLEMENT_STATE_INVALID", "结算文档");
        if (formal && !"FINALIZED".equals(value.getSettlementStatus())) {
            throw new com.cgcpms.common.exception.BusinessException(
                    "DOCUMENT_SETTLEMENT_NOT_FINALIZED", "正式结算文档仅允许定案后生成");
        }
        return snapshot(SCHEMA,
                "settlement", map("code", text(value.getSettlementCode()), "type", text(value.getSettlementType()),
                        "status", text(value.getStatus()), "approvalStatus", text(value.getApprovalStatus()),
                        "finalStatus", text(value.getSettlementStatus()), "contractAmount", money(value.getContractAmount()),
                        "changeAmount", money(value.getChangeAmount()), "measuredAmount", money(value.getMeasuredAmount()),
                        "deductionAmount", money(value.getDeductionAmount()), "paidAmount", money(value.getPaidAmount()),
                        "finalAmount", money(value.getFinalAmount()), "unpaidAmount", money(value.getUnpaidAmount()),
                        "warrantyAmount", money(value.getWarrantyAmount()), "finalizedAt", text(value.getFinalizedAt()),
                        "createdAt", text(value.getCreatedAt()), "updatedAt", text(value.getUpdatedAt()),
                        "remark", text(value.getRemark())),
                "project", map("name", text(value.getProjectName())),
                "contract", map("name", text(value.getContractName())),
                "partner", map("name", text(value.getPartnerName())),
                "items", rows(value.getItems(), this::itemRow),
                "variations", rows(service.getVariations(id), this::variationRow),
                "payments", rows(service.getPayments(id), this::paymentRow),
                "costs", rows(service.getCosts(id), this::costRow));
    }

    private Map<String, Object> itemRow(StlSettlementItemVO value) {
        return map("name", text(value.getItemName()), "unit", text(value.getUnit()),
                "quantity", number(value.getQuantity()), "unitPrice", money(value.getUnitPrice()),
                "amount", money(value.getAmount()), "sourceType", text(value.getSourceType()),
                "remark", text(value.getRemark()));
    }

    private Map<String, Object> variationRow(VarOrderVO value) {
        return map("code", text(value.getVarCode()), "name", text(value.getVarName()),
                "type", text(value.getVarType()), "direction", text(value.getDirection()),
                "confirmedAmount", money(value.getConfirmedAmount()), "status", text(value.getApprovalStatus()));
    }

    private Map<String, Object> paymentRow(SettlementPaymentItemVO value) {
        return map("applyCode", text(value.getApplyCode()), "payType", text(value.getPayType()),
                "applyAmount", money(value.getApplyAmount()), "approvedAmount", money(value.getApprovedAmount()),
                "actualPayAmount", money(value.getActualPayAmount()), "status", text(value.getPayStatus()),
                "payDate", text(value.getPayDate()), "voucherNo", text(value.getVoucherNo()));
    }

    private Map<String, Object> costRow(SettlementCostItemVO value) {
        return map("subjectName", text(value.getCostSubjectName()), "type", text(value.getCostType()),
                "sourceType", text(value.getSourceType()), "amount", money(value.getAmount()),
                "taxAmount", money(value.getTaxAmount()), "amountWithoutTax", money(value.getAmountWithoutTax()),
                "date", text(value.getCostDate()), "status", text(value.getCostStatus()));
    }
}
