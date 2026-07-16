package com.cgcpms.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.BudgetLedgerMapper;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BudgetLedgerService {
    private final ProjectBudgetMapper budgetMapper;
    private final ProjectBudgetLineMapper lineMapper;
    private final BudgetLedgerMapper ledgerMapper;

    @Transactional(rollbackFor = Exception.class)
    public BudgetLedger reserve(Long lineId, String businessType, Long businessId,
                                BigDecimal amount, String idempotencyKey) {
        return mutate(lineId, businessType, businessId, amount, idempotencyKey,
                BudgetStatusConstants.ENTRY_RESERVE);
    }

    @Transactional(rollbackFor = Exception.class)
    public BudgetLedger release(Long lineId, String businessType, Long businessId,
                                BigDecimal amount, String idempotencyKey) {
        return mutate(lineId, businessType, businessId, amount, idempotencyKey,
                BudgetStatusConstants.ENTRY_RELEASE);
    }

    @Transactional(rollbackFor = Exception.class)
    public BudgetLedger consume(Long lineId, String businessType, Long businessId,
                                BigDecimal amount, String idempotencyKey) {
        return mutate(lineId, businessType, businessId, amount, idempotencyKey,
                BudgetStatusConstants.ENTRY_CONSUME);
    }

    @Transactional(rollbackFor = Exception.class)
    public BudgetLedger reverse(Long lineId, String businessType, Long businessId,
                                BigDecimal amount, String idempotencyKey) {
        return mutate(lineId, businessType, businessId, amount, idempotencyKey,
                BudgetStatusConstants.ENTRY_REVERSE);
    }

    /** 付款冲销专用：将原付款消耗原子恢复为审批仍有效的预算占用。 */
    @Transactional(rollbackFor = Exception.class)
    public BudgetLedger restoreReservation(Long lineId, String businessType, Long businessId,
                                            BigDecimal amount, String idempotencyKey) {
        return mutate(lineId, businessType, businessId, amount, idempotencyKey,
                BudgetStatusConstants.ENTRY_RESTORE_RESERVATION);
    }

    public List<BudgetLedger> getBusinessLedger(String businessType, Long businessId) {
        return ledgerMapper.selectList(new LambdaQueryWrapper<BudgetLedger>()
                .eq(BudgetLedger::getTenantId, UserContext.getCurrentTenantId())
                .eq(BudgetLedger::getBusinessType, businessType)
                .eq(BudgetLedger::getBusinessId, businessId)
                .orderByAsc(BudgetLedger::getCreatedAt));
    }

    private BudgetLedger mutate(Long lineId, String businessType, Long businessId,
                                BigDecimal rawAmount, String idempotencyKey, String entryType) {
        validateBusiness(businessType, businessId, idempotencyKey);
        BigDecimal amount = ProjectBudgetService.money(rawAmount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("BUDGET_AMOUNT_INVALID", "预算台账金额必须大于0");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        BudgetLedger existing = ledgerMapper.selectOne(new LambdaQueryWrapper<BudgetLedger>()
                .eq(BudgetLedger::getTenantId, tenantId)
                .eq(BudgetLedger::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            if (!Objects.equals(existing.getBudgetLineId(), lineId)
                    || !Objects.equals(existing.getBusinessType(), businessType)
                    || !Objects.equals(existing.getBusinessId(), businessId)
                    || !Objects.equals(existing.getEntryType(), entryType)
                    || existing.getAmount().compareTo(amount) != 0) {
                throw new BusinessException("BUDGET_IDEMPOTENCY_CONFLICT", "幂等键已被不同预算操作使用");
            }
            return existing;
        }

        ProjectBudgetLine line = lineMapper.selectById(lineId);
        if (line == null || !Objects.equals(line.getTenantId(), tenantId)) {
            throw new BusinessException("BUDGET_LINE_NOT_FOUND", "预算科目不存在");
        }
        ProjectBudget budget = budgetMapper.selectById(line.getBudgetId());
        if (budget == null || !Objects.equals(budget.getTenantId(), tenantId)) {
            throw new BusinessException("BUDGET_NOT_FOUND", "预算版本不存在");
        }
        if (BudgetStatusConstants.ENTRY_RESERVE.equals(entryType)
                && (!BudgetStatusConstants.STATUS_ACTIVE.equals(budget.getStatus())
                || !Integer.valueOf(1).equals(budget.getActiveFlag()))) {
            throw new BusinessException("BUDGET_NOT_ACTIVE", "只有当前生效预算可以新增占用");
        }
        if (!BudgetStatusConstants.ENTRY_RESERVE.equals(entryType)
                && !BudgetStatusConstants.STATUS_ACTIVE.equals(budget.getStatus())
                && !BudgetStatusConstants.STATUS_SUPERSEDED.equals(budget.getStatus())) {
            throw new BusinessException("BUDGET_STATUS_INVALID", "当前预算状态不允许释放、消耗或冲销");
        }

        int rows = switch (entryType) {
            case BudgetStatusConstants.ENTRY_RESERVE -> lineMapper.reserveIfAvailable(lineId, tenantId, amount);
            case BudgetStatusConstants.ENTRY_RELEASE -> lineMapper.releaseReserved(lineId, tenantId, amount);
            case BudgetStatusConstants.ENTRY_CONSUME -> lineMapper.consumeReserved(lineId, tenantId, amount);
            case BudgetStatusConstants.ENTRY_REVERSE -> lineMapper.reverseConsumed(lineId, tenantId, amount);
            case BudgetStatusConstants.ENTRY_RESTORE_RESERVATION ->
                    lineMapper.restoreConsumedToReserved(lineId, tenantId, amount);
            default -> throw new BusinessException("BUDGET_ENTRY_TYPE_INVALID", "不支持的预算台账类型");
        };
        if (rows != 1) {
            String code = BudgetStatusConstants.ENTRY_RESERVE.equals(entryType)
                    ? "BUDGET_INSUFFICIENT" : "BUDGET_BALANCE_CONFLICT";
            throw new BusinessException(code, "预算余额不足或已被并发操作，请刷新后重试");
        }

        ProjectBudgetLine updated = lineMapper.selectByIdForUpdate(lineId, tenantId);
        BudgetLedger ledger = new BudgetLedger();
        ledger.setTenantId(tenantId);
        ledger.setBudgetId(updated.getBudgetId());
        ledger.setBudgetLineId(updated.getId());
        ledger.setProjectId(updated.getProjectId());
        ledger.setBusinessType(businessType);
        ledger.setBusinessId(businessId);
        ledger.setEntryType(entryType);
        ledger.setAmount(amount);
        ledger.setReservedBalance(ProjectBudgetService.money(updated.getReservedAmount()));
        ledger.setConsumedBalance(ProjectBudgetService.money(updated.getConsumedAmount()));
        ledger.setIdempotencyKey(idempotencyKey);
        ledger.setCreatedBy(UserContext.getCurrentUserId());
        ledger.setCreatedAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);
        return ledger;
    }

    private static void validateBusiness(String businessType, Long businessId, String idempotencyKey) {
        if (businessType == null || businessType.isBlank() || businessId == null) {
            throw new BusinessException("BUDGET_BUSINESS_REQUIRED", "预算台账必须关联业务类型和业务ID");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BusinessException("BUDGET_IDEMPOTENCY_REQUIRED", "预算操作必须提供有效幂等键");
        }
    }
}
