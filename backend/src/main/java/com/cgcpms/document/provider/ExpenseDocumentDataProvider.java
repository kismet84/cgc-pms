package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.expense.service.ExpenseApplicationService;
import com.cgcpms.expense.vo.ExpenseApplicationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ExpenseDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "expense.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("EXPENSE", SCHEMA,
            field("expense.code", "费用申请编号", "TEXT", false), field("expense.category", "费用类别", "ENUM", false),
            field("expense.date", "费用日期", "DATE", true), field("expense.amount", "申请金额", "MONEY", false),
            field("expense.convertedAmount", "已转付款金额", "MONEY", false), field("expense.paidAmount", "已付金额", "MONEY", false),
            field("expense.availableToConvert", "可转付款金额", "MONEY", false), field("expense.description", "费用说明", "TEXT", true),
            field("expense.status", "业务状态", "ENUM", false), field("expense.approvalStatus", "审批状态", "ENUM", false),
            field("expense.createdAt", "创建时间", "DATETIME", true), field("expense.updatedAt", "更新时间", "DATETIME", true),
            field("expense.remark", "备注", "TEXT", true));

    private final ExpenseApplicationService service;
    public String businessType() { return "EXPENSE"; }
    public String displayName() { return "费用申请"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "expense:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        ExpenseApplicationVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_EXPENSE_STATE_INVALID", "费用申请文档");
        return snapshot(SCHEMA, "expense", map("code", text(value.getExpenseCode()), "category", text(value.getExpenseCategory()),
                "date", text(value.getExpenseDate()), "amount", money(value.getAmount()),
                "convertedAmount", money(value.getConvertedAmount()), "paidAmount", money(value.getPaidAmount()),
                "availableToConvert", money(value.getAvailableToConvert()), "description", text(value.getDescription()),
                "status", text(value.getStatus()), "approvalStatus", text(value.getApprovalStatus()),
                "createdAt", text(value.getCreatedAt()), "updatedAt", text(value.getUpdatedAt()), "remark", text(value.getRemark())));
    }
}
