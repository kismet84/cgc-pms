package com.cgcpms.document.provider;

import com.cgcpms.contract.service.CtContractService;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ContractApprovalDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "contract-approval.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("CONTRACT_APPROVAL", SCHEMA,
            field("contract.code", "合同编号", "TEXT", false), field("contract.name", "合同名称", "TEXT", false),
            field("contract.type", "合同类型", "ENUM", true), field("contract.partyA", "甲方", "TEXT", true),
            field("contract.partyB", "乙方", "TEXT", true), field("contract.amount", "合同金额", "MONEY", false),
            field("contract.currentAmount", "当前合同金额", "MONEY", false), field("contract.taxRate", "税率", "NUMBER", true),
            field("contract.taxAmount", "税额", "MONEY", true), field("contract.amountWithoutTax", "不含税金额", "MONEY", true),
            field("contract.signedDate", "签订日期", "DATE", true), field("contract.startDate", "开始日期", "DATE", true),
            field("contract.endDate", "结束日期", "DATE", true), field("contract.paymentMethod", "付款方式", "TEXT", true),
            field("contract.settlementMethod", "结算方式", "TEXT", true), field("contract.paidAmount", "已付金额", "MONEY", false),
            field("contract.payableAmount", "应付金额", "MONEY", false), field("contract.settlementAmount", "结算金额", "MONEY", false),
            field("contract.status", "合同状态", "ENUM", false), field("contract.approvalStatus", "审批状态", "ENUM", false),
            field("contract.createdAt", "创建时间", "DATETIME", true), field("contract.updatedAt", "更新时间", "DATETIME", true),
            field("contract.remark", "备注", "TEXT", true), field("project.name", "项目名称", "TEXT", true));

    private final CtContractService service;
    public String businessType() { return "CONTRACT_APPROVAL"; }
    public String displayName() { return "合同审批"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "contract:query"; }
    public String defaultTemplatePolicy() { return "NONE"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        CtContractVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_CONTRACT_STATE_INVALID", "合同审批文档");
        return snapshot(SCHEMA, "contract", map("code", text(value.getContractCode()), "name", text(value.getContractName()),
                "type", text(value.getContractType()), "partyA", text(value.getPartyAName()), "partyB", text(value.getPartyBName()),
                "amount", money(value.getContractAmount()), "currentAmount", money(value.getCurrentAmount()), "taxRate", number(value.getTaxRate()),
                "taxAmount", money(value.getTaxAmount()), "amountWithoutTax", money(value.getAmountWithoutTax()),
                "signedDate", text(value.getSignedDate()), "startDate", text(value.getStartDate()), "endDate", text(value.getEndDate()),
                "paymentMethod", text(value.getPaymentMethod()), "settlementMethod", text(value.getSettlementMethod()),
                "paidAmount", money(value.getPaidAmount()), "payableAmount", money(value.getPayableAmount()),
                "settlementAmount", money(value.getSettlementAmount()), "status", text(value.getContractStatus()),
                "approvalStatus", text(value.getApprovalStatus()), "createdAt", text(value.getCreatedAt()),
                "updatedAt", text(value.getUpdatedAt()), "remark", text(value.getRemark())),
                "project", map("name", text(value.getProjectName())));
    }
}
