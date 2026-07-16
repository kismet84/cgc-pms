package com.cgcpms.expense.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.budget.service.BudgetLedgerService;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExpenseWorkflowHandler implements WorkflowBusinessHandler {
    private final ExpenseApplicationMapper expenseMapper;
    private final BudgetLedgerService ledgerService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.EXPENSE;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void onApproved(WorkflowContext context) {
        Long expenseId = resolveId(context.getInstance());
        int rows = expenseMapper.update(null, new LambdaUpdateWrapper<ExpenseApplication>()
                .eq(ExpenseApplication::getId, expenseId)
                .eq(ExpenseApplication::getApprovalStatus, "APPROVING")
                .set(ExpenseApplication::getApprovalStatus, "APPROVED")
                .set(ExpenseApplication::getStatus, "APPROVED"));
        if (rows != 1) throw new IllegalStateException("费用申请审批状态冲突，expenseId=" + expenseId);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        releaseOutstandingReservation(context.getInstance(), "REJECT");
        expenseMapper.update(null, new LambdaUpdateWrapper<ExpenseApplication>()
                .eq(ExpenseApplication::getId, resolveId(context.getInstance()))
                .set(ExpenseApplication::getApprovalStatus, "REJECTED")
                .set(ExpenseApplication::getStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        releaseOutstandingReservation(context.getInstance(), "WITHDRAW");
        expenseMapper.update(null, new LambdaUpdateWrapper<ExpenseApplication>()
                .eq(ExpenseApplication::getId, resolveId(context.getInstance()))
                .set(ExpenseApplication::getApprovalStatus, "DRAFT")
                .set(ExpenseApplication::getStatus, "DRAFT"));
    }

    private void releaseOutstandingReservation(WfInstance instance, String action) {
        Long expenseId = resolveId(instance);
        ExpenseApplication expense = expenseMapper.selectById(expenseId);
        if (expense == null) throw new IllegalStateException("费用申请不存在，expenseId=" + expenseId);
        List<BudgetLedger> ledgers = ledgerService.getBusinessLedger(WorkflowBusinessTypes.EXPENSE, expenseId);
        BigDecimal outstanding = ledgers.stream()
                .map(ledger -> switch (ledger.getEntryType()) {
                    case BudgetStatusConstants.ENTRY_RESERVE -> ledger.getAmount();
                    case BudgetStatusConstants.ENTRY_RELEASE, BudgetStatusConstants.ENTRY_CONSUME -> ledger.getAmount().negate();
                    default -> BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
            String key = "EXPENSE:" + action + ":" + expenseId + ":R" + instance.getCurrentRound();
            ledgerService.release(expense.getBudgetLineId(), WorkflowBusinessTypes.EXPENSE,
                    expenseId, outstanding, key);
        }
    }

    private static Long resolveId(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("审批实例缺少费用申请ID");
        return instance.getBusinessId();
    }
}
