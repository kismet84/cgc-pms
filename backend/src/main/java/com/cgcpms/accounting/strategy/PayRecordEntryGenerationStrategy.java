package com.cgcpms.accounting.strategy;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.constant.AccountingSubjectCatalog;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PayRecordEntryGenerationStrategy implements EntryGenerationStrategy {
    public static final String SOURCE_TYPE = "PAY_RECORD";
    public static final String ENTRY_TYPE = "PAYMENT";

    private final PayRecordMapper recordMapper;
    private final PayApplicationMapper applicationMapper;
    private final AccountingSubjectResolver subjectResolver;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public AccountingEntry generate(Long sourceId, String entryType) {
        if (!ENTRY_TYPE.equals(entryType)) {
            throw new BusinessException("PAYMENT_ENTRY_TYPE_INVALID", "付款记录只能生成 PAYMENT 类型凭证");
        }
        PayRecord record = recordMapper.selectById(sourceId);
        if (record == null || !Objects.equals(record.getTenantId(), UserContext.getCurrentTenantId())
                || !"SUCCESS".equals(record.getPayStatus())) {
            throw new BusinessException("PAY_RECORD_NOT_SUCCESS", "只有当前租户的成功付款可以生成会计凭证");
        }
        PayApplication application = applicationMapper.selectById(record.getPayApplicationId());
        if (application == null || !Objects.equals(application.getTenantId(), record.getTenantId())) {
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款记录关联的付款申请不存在");
        }

        AccountingEntry entry = new AccountingEntry();
        entry.setEntryCode("PAY-" + record.getId());
        entry.setEntryType(ENTRY_TYPE);
        entry.setEntryDate(record.getPaidAt() == null ? record.getPayDate() : record.getPaidAt().toLocalDate());
        entry.setProjectId(record.getProjectId());
        entry.setContractId(record.getContractId());
        entry.setPayApplicationId(record.getPayApplicationId());
        entry.setPayRecordId(record.getId());

        AccountingEntryLine debit = new AccountingEntryLine();
        debit.setDirection("DEBIT");
        boolean advance = "ADVANCE".equals(application.getPayType());
        CostSubject debitSubject = subjectResolver.require(
                advance ? AccountingSubjectCatalog.PREPAYMENT : AccountingSubjectCatalog.PAYABLE,
                advance ? "ASSET" : "LIABILITY");
        debit.setAccountCode(debitSubject.getSubjectCode());
        debit.setAccountName(debitSubject.getSubjectName());
        debit.setCostSubjectId(application.getCostSubjectId());
        debit.setAmount(record.getPayAmount());
        debit.setSummary((advance ? "支付预付款：" : "支付合同款，冲减应付：") + record.getExternalTxnNo());

        AccountingEntryLine credit = new AccountingEntryLine();
        credit.setDirection("CREDIT");
        CostSubject bankSubject = subjectResolver.require(AccountingSubjectCatalog.BANK, "ASSET");
        credit.setAccountCode(bankSubject.getSubjectCode() + "-" + record.getFundAccountId());
        credit.setAccountName(bankSubject.getSubjectName());
        credit.setAmount(record.getPayAmount());
        credit.setSummary("银行付款：" + record.getExternalTxnNo());
        entry.setLines(List.of(debit, credit));
        return entry;
    }
}
