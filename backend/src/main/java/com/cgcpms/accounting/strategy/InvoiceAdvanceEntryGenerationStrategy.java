package com.cgcpms.accounting.strategy;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class InvoiceAdvanceEntryGenerationStrategy implements EntryGenerationStrategy {
    public static final String SOURCE_TYPE = "PAY_INVOICE";
    public static final String AP_CONFIRMATION_ENTRY_TYPE = "ADVANCE_AP_CONFIRMATION";
    public static final String PREPAY_RECLASS_ENTRY_TYPE = "ADVANCE_PREPAY_RECLASS";

    private final PayInvoiceMapper invoiceMapper;
    private final PayApplicationMapper applicationMapper;
    private final CostSubjectMapper costSubjectMapper;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public AccountingEntry generate(Long sourceId, String entryType) {
        if (!AP_CONFIRMATION_ENTRY_TYPE.equals(entryType) && !PREPAY_RECLASS_ENTRY_TYPE.equals(entryType)) {
            throw new BusinessException("PAY_INVOICE_ENTRY_TYPE_INVALID", "付款发票凭证类型不合法");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        PayInvoice invoice = invoiceMapper.selectById(sourceId);
        if (invoice == null || !Objects.equals(invoice.getTenantId(), tenantId)) {
            throw new BusinessException("PAY_INVOICE_NOT_FOUND", "付款发票不存在或不属于当前租户");
        }
        if (!"VERIFIED".equals(invoice.getVerifyStatus())) {
            throw new BusinessException("PAY_INVOICE_NOT_VERIFIED", "只有已核验付款发票可以生成会计凭证");
        }
        if (invoice.getPayApplicationId() == null) return null;

        PayApplication application = applicationMapper.selectById(invoice.getPayApplicationId());
        if (application == null || !Objects.equals(application.getTenantId(), tenantId)) {
            throw new BusinessException("PAY_APPLICATION_NOT_FOUND", "付款发票关联付款申请不存在或跨租户");
        }
        if (!"ADVANCE".equals(application.getPayType())) return null;
        if (!Objects.equals(invoice.getProjectId(), application.getProjectId())
                || !Objects.equals(invoice.getContractId(), application.getContractId())) {
            throw new BusinessException("PAY_INVOICE_APPLICATION_MISMATCH", "付款发票与付款申请项目或合同不一致");
        }

        BigDecimal amount = invoice.getInvoiceAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("PAY_INVOICE_AMOUNT_INVALID", "付款发票金额必须大于0");
        }
        CostSubject subject = costSubjectMapper.selectById(application.getCostSubjectId());
        if (subject == null || !Objects.equals(subject.getTenantId(), tenantId)
                || !"ENABLE".equals(subject.getStatus())) {
            throw new BusinessException("PAYMENT_COST_SUBJECT_INVALID", "费用分类科目不存在、跨租户或已停用");
        }

        AccountingEntry entry = new AccountingEntry();
        entry.setEntryCode((AP_CONFIRMATION_ENTRY_TYPE.equals(entryType) ? "ADV-AP-" : "ADV-RECLASS-") + sourceId);
        entry.setEntryType(entryType);
        entry.setEntryDate(LocalDate.now());
        entry.setProjectId(invoice.getProjectId());
        entry.setContractId(invoice.getContractId());
        entry.setPayApplicationId(application.getId());
        entry.setLines(AP_CONFIRMATION_ENTRY_TYPE.equals(entryType)
                ? List.of(
                    line("DEBIT", subject.getSubjectCode(), subject.getSubjectName(), subject.getId(), amount,
                            "预付款发票确认成本：" + invoice.getInvoiceNo()),
                    line("CREDIT", "2202-AP", "应付账款", null, amount,
                            "预付款发票确认应付：" + invoice.getInvoiceNo()))
                : List.of(
                    line("DEBIT", "2202-AP", "应付账款", null, amount,
                            "预付款发票冲减应付：" + invoice.getInvoiceNo()),
                    line("CREDIT", "1123-PREPAY", "预付账款", null, amount,
                            "预付款发票结转：" + invoice.getInvoiceNo())));
        return entry;
    }

    private static AccountingEntryLine line(String direction, String accountCode, String accountName,
                                            Long costSubjectId, BigDecimal amount, String summary) {
        AccountingEntryLine line = new AccountingEntryLine();
        line.setDirection(direction);
        line.setAccountCode(accountCode);
        line.setAccountName(accountName);
        line.setCostSubjectId(costSubjectId);
        line.setAmount(amount);
        line.setSummary(summary);
        return line;
    }
}
