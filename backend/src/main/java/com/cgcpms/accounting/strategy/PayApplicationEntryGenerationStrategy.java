package com.cgcpms.accounting.strategy;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.constant.AccountingSubjectCatalog;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
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
public class PayApplicationEntryGenerationStrategy implements EntryGenerationStrategy {
    public static final String SOURCE_TYPE = "PAY_APPLICATION";
    public static final String ENTRY_TYPE = "AP_CONFIRMATION";

    private final PayApplicationMapper applicationMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final AccountingSubjectResolver subjectResolver;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public AccountingEntry generate(Long sourceId, String entryType) {
        if (!ENTRY_TYPE.equals(entryType)) {
            throw new BusinessException("PAY_APPLICATION_ENTRY_TYPE_INVALID", "付款申请只能生成 AP_CONFIRMATION 类型凭证");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        PayApplication application = applicationMapper.selectById(sourceId);
        if (application == null || !Objects.equals(application.getTenantId(), tenantId)) {
            throw new BusinessException("PAY_APPLICATION_NOT_FOUND", "付款申请不存在或不属于当前租户");
        }
        if ("ADVANCE".equals(application.getPayType())) return null;
        if (!"APPROVED".equals(application.getApprovalStatus())) {
            throw new BusinessException("PAY_APPLICATION_NOT_APPROVED", "只有审批通过的付款申请可以确认应付");
        }
        BigDecimal amount = application.getApprovedAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("PAY_APPLICATION_APPROVED_AMOUNT_INVALID", "付款申请审批金额必须大于0");
        }

        CostSubject subject = costSubjectMapper.selectById(application.getCostSubjectId());
        if (subject == null || !Objects.equals(subject.getTenantId(), tenantId)
                || !"ENABLE".equals(subject.getStatus()) || !"COST".equals(subject.getAccountCategory())) {
            throw new BusinessException("PAYMENT_COST_SUBJECT_INVALID", "费用分类科目不存在、跨租户、非成本类或已停用");
        }
        CostSubject payableSubject = subjectResolver.require(AccountingSubjectCatalog.PAYABLE, "LIABILITY");

        AccountingEntry entry = new AccountingEntry();
        entry.setEntryCode("AP-CONF-" + sourceId);
        entry.setEntryType(ENTRY_TYPE);
        entry.setEntryDate(LocalDate.now());
        entry.setProjectId(application.getProjectId());
        entry.setContractId(application.getContractId());
        entry.setPayApplicationId(sourceId);
        entry.setLines(List.of(
                line("DEBIT", subject.getSubjectCode(), subject.getSubjectName(), subject.getId(), amount,
                        "付款申请确认成本：" + application.getApplyCode()),
                line("CREDIT", payableSubject.getSubjectCode(), payableSubject.getSubjectName(), null, amount,
                        "付款申请确认应付：" + application.getApplyCode())));
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
