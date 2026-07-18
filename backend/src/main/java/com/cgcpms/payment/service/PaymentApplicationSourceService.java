package com.cgcpms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.budget.service.BudgetLedgerService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.payment.constant.PaymentIntegrityConstants;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.entity.PaymentRecordSourceAllocation;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PaymentApplicationSourceMapper;
import com.cgcpms.payment.mapper.PaymentRecordSourceAllocationMapper;
import com.cgcpms.payment.vo.PaymentApplicationSourceVO;
import com.cgcpms.payment.vo.PaymentSourceOptionVO;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.settlement.constant.SettlementStatusConstants;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.SettlementSubMeasure;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.mapper.SettlementSubMeasureMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentApplicationSourceService {
    private final PaymentApplicationSourceMapper sourceMapper;
    private final PayApplicationMapper applicationMapper;
    private final ExpenseApplicationMapper expenseMapper;
    private final StlSettlementMapper settlementMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final SettlementSubMeasureMapper settlementSubMeasureMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final BudgetLedgerService ledgerService;
    private final PaymentRecordSourceAllocationMapper allocationMapper;

    public List<PaymentApplicationSourceVO> list(Long applicationId) {
        PayApplication app = requireApplication(applicationId, "查看付款申请来源");
        return loadSources(app).stream().map(this::toVO).toList();
    }

    public List<PaymentSourceOptionVO> listOptions(Long projectId, Long contractId, Long partnerId,
                                                    String payType, String expenseCategory) {
        projectAccessChecker.checkAccess(projectId, "选择付款申请来源");
        Long tenantId = UserContext.getCurrentTenantId();
        String normalizedPayType = payType == null ? "" : payType.trim().toUpperCase();
        String normalizedCategory = expenseCategory == null ? "" : expenseCategory.trim().toUpperCase();
        if ("PROGRESS".equals(normalizedPayType) && "SUBCONTRACT".equals(normalizedCategory)) {
            return sourceMapper.selectSubMeasureOptions(tenantId, projectId, contractId, partnerId);
        }
        if ("FINAL".equals(normalizedPayType)) {
            return sourceMapper.selectSettlementOptions(tenantId, projectId, contractId, partnerId);
        }
        return List.of();
    }

    public void deleteDraftSources(PayApplication app) {
        sourceMapper.hardDeleteDraftSources(app.getId(), app.getTenantId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(Long applicationId, List<PaymentApplicationSource> inputSources) {
        PayApplication app = requireApplication(applicationId, "编辑付款申请来源");
        if (!"DRAFT".equals(app.getApprovalStatus())) {
            throw new BusinessException("PAYMENT_SOURCE_NOT_EDITABLE", "只有草稿付款申请可以编辑来源");
        }
        List<PaymentApplicationSource> sources = inputSources == null ? List.of() : inputSources;
        normalizeAndValidateShape(app, sources);
        validateSourceBusiness(app, sources, false);
        sourceMapper.hardDeleteDraftSources(app.getId(), app.getTenantId());
        for (PaymentApplicationSource source : sources) {
            source.setId(IdWorker.getId());
            source.setTenantId(app.getTenantId());
            source.setPayApplicationId(app.getId());
            source.setVersion(0);
            source.setPaidAmount(BigDecimal.ZERO.setScale(2));
            sourceMapper.insert(source);
        }
    }

    /** 将一次成功付款按来源顺序分配，并同步消耗对应预算占用。 */
    public void consumeForPayment(PayApplication app, PayRecord record) {
        BigDecimal remaining = money(record.getPayAmount());
        for (PaymentApplicationSource source : loadSources(app)) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal sourceRemaining = money(source.getSourceAmount()).subtract(money(source.getPaidAmount()));
            if (sourceRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal allocated = remaining.min(sourceRemaining);
            if (sourceMapper.consumeForPayment(source.getId(), app.getTenantId(), allocated) != 1) {
                throw new BusinessException("PAYMENT_SOURCE_CONSUME_CONFLICT", "付款来源金额已被并发消耗，请重试");
            }
            if (PaymentIntegrityConstants.SOURCE_EXPENSE.equals(source.getSourceType())) {
                ExpenseApplication expense = expenseMapper.selectByIdForUpdate(source.getExpenseId(), app.getTenantId());
                if (expense == null || expenseMapper.consumePayment(expense.getId(), app.getTenantId(), allocated) != 1) {
                    throw new BusinessException("EXPENSE_PAYMENT_CONSUME_CONFLICT", "费用来源实付金额更新失败");
                }
                ledgerService.consume(expense.getBudgetLineId(), WorkflowBusinessTypes.EXPENSE, expense.getId(), allocated,
                        "EXPENSE:CONSUME:PAY_RECORD:" + record.getId() + ":SOURCE:" + source.getId());
            } else {
                ledgerService.consume(app.getBudgetLineId(), WorkflowBusinessTypes.PAY_REQUEST, app.getId(), allocated,
                        "PAY_REQUEST:CONSUME:PAY_RECORD:" + record.getId() + ":SOURCE:" + source.getId());
            }
            PaymentRecordSourceAllocation allocation = new PaymentRecordSourceAllocation();
            allocation.setTenantId(app.getTenantId());
            allocation.setPayRecordId(record.getId());
            allocation.setPaymentSourceId(source.getId());
            allocation.setSourceType(source.getSourceType());
            allocation.setSourceRefId(source.getSourceRefId());
            allocation.setAllocatedAmount(allocated);
            allocation.setCreatedBy(UserContext.getCurrentUserId());
            allocation.setCreatedAt(java.time.LocalDateTime.now());
            allocationMapper.insert(allocation);
            remaining = remaining.subtract(allocated);
        }
        if (remaining.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("PAYMENT_SOURCE_REMAINING_INSUFFICIENT", "付款来源剩余金额不足");
        }
    }

    /** 付款冲销时按原付款分摊逐笔恢复来源实付与预算消耗，保持金额守恒。 */
    public void reversePayment(PayApplication app, PayRecord record) {
        List<PaymentRecordSourceAllocation> allocations = allocationMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordSourceAllocation>()
                        .eq(PaymentRecordSourceAllocation::getTenantId, app.getTenantId())
                        .eq(PaymentRecordSourceAllocation::getPayRecordId, record.getId())
                        .orderByAsc(PaymentRecordSourceAllocation::getCreatedAt));
        BigDecimal total = allocations.stream().map(PaymentRecordSourceAllocation::getAllocatedAmount)
                .map(PaymentApplicationSourceService::money).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(money(record.getPayAmount())) != 0) {
            throw new BusinessException("PAYMENT_REVERSAL_ALLOCATION_MISMATCH", "原付款来源分摊不完整，禁止冲销");
        }
        for (PaymentRecordSourceAllocation allocation : allocations) {
            BigDecimal amount = money(allocation.getAllocatedAmount());
            PaymentApplicationSource source = sourceMapper.selectById(allocation.getPaymentSourceId());
            if (source == null || !Objects.equals(source.getTenantId(), app.getTenantId())
                    || sourceMapper.reversePayment(source.getId(), app.getTenantId(), amount) != 1) {
                throw new BusinessException("PAYMENT_SOURCE_REVERSE_CONFLICT", "付款来源实付金额恢复失败");
            }
            if (PaymentIntegrityConstants.SOURCE_EXPENSE.equals(allocation.getSourceType())) {
                ExpenseApplication expense = expenseMapper.selectByIdForUpdate(source.getExpenseId(), app.getTenantId());
                if (expense == null || expenseMapper.reversePayment(expense.getId(), app.getTenantId(), amount) != 1) {
                    throw new BusinessException("EXPENSE_PAYMENT_REVERSE_CONFLICT", "费用来源实付金额恢复失败");
                }
                ledgerService.restoreReservation(expense.getBudgetLineId(), WorkflowBusinessTypes.EXPENSE,
                        expense.getId(), amount,
                        "EXPENSE:RESTORE_RESERVATION:PAY_RECORD:" + record.getId() + ":SOURCE:" + source.getId());
            } else {
                ledgerService.restoreReservation(app.getBudgetLineId(), WorkflowBusinessTypes.PAY_REQUEST,
                        app.getId(), amount,
                        "PAY_REQUEST:RESTORE_RESERVATION:PAY_RECORD:" + record.getId() + ":SOURCE:" + source.getId());
            }
        }
    }

    /** 提交事务内调用：锁定来源并冻结费用额度/占用直接付款预算。 */
    public void validateAndAllocateForSubmit(PayApplication app) {
        List<PaymentApplicationSource> sources = loadSources(app);
        normalizeAndValidateShape(app, sources);
        validateSourceBusiness(app, sources, false);
        BigDecimal reserveAmount = BigDecimal.ZERO;
        for (PaymentApplicationSource source : sources) {
            if (PaymentIntegrityConstants.SOURCE_EXPENSE.equals(source.getSourceType())) {
                if (expenseMapper.allocateToPayment(source.getExpenseId(), app.getTenantId(),
                        money(source.getSourceAmount())) != 1) {
                    throw new BusinessException("EXPENSE_AVAILABLE_AMOUNT_INSUFFICIENT",
                            "费用申请可转付款金额不足或已被并发占用");
                }
            } else {
                reserveAmount = reserveAmount.add(money(source.getSourceAmount()));
            }
        }
        if (reserveAmount.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.reserve(app.getBudgetLineId(), WorkflowBusinessTypes.PAY_REQUEST,
                    app.getId(), reserveAmount, "PAY_REQUEST:RESERVE:" + app.getId() + ":V" + app.getVersion());
        }
    }

    /** 审批通过前二次校验；当前申请自身已冻结的额度不重复计入可用额。 */
    public void validateAllocated(PayApplication app) {
        List<PaymentApplicationSource> sources = loadSources(app);
        normalizeAndValidateShape(app, sources);
        validateSourceBusiness(app, sources, true);
    }

    /** 驳回/撤回时释放本申请冻结的费用额度与直接/结算预算。 */
    public void releaseAllocations(PayApplication app, String action, int round) {
        for (PaymentApplicationSource source : loadSources(app)) {
            if (PaymentIntegrityConstants.SOURCE_EXPENSE.equals(source.getSourceType())) {
                int rows = expenseMapper.releasePaymentAllocation(source.getExpenseId(), app.getTenantId(),
                        money(source.getSourceAmount()));
                if (rows != 1) {
                    throw new BusinessException("EXPENSE_ALLOCATION_RELEASE_CONFLICT", "费用申请付款冻结额度释放失败");
                }
            }
        }
        List<BudgetLedger> ledgers = ledgerService.getBusinessLedger(WorkflowBusinessTypes.PAY_REQUEST, app.getId());
        BigDecimal outstanding = ledgers.stream().map(ledger -> switch (ledger.getEntryType()) {
            case BudgetStatusConstants.ENTRY_RESERVE -> ledger.getAmount();
            case BudgetStatusConstants.ENTRY_RELEASE, BudgetStatusConstants.ENTRY_CONSUME -> ledger.getAmount().negate();
            default -> BigDecimal.ZERO;
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.release(app.getBudgetLineId(), WorkflowBusinessTypes.PAY_REQUEST, app.getId(), outstanding,
                    "PAY_REQUEST:" + action + ":" + app.getId() + ":R" + round);
        }
    }

    private void normalizeAndValidateShape(PayApplication app, List<PaymentApplicationSource> sources) {
        if (sources.isEmpty()) {
            throw new BusinessException("PAYMENT_SOURCE_REQUIRED", "付款申请必须至少关联一个费用、分包计量、结算或直接付款来源");
        }
        Set<String> keys = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentApplicationSource source : sources) {
            source.setSourceType(source.getSourceType() == null ? null : source.getSourceType().trim().toUpperCase());
            source.setSourceAmount(money(source.getSourceAmount()));
            if (source.getSourceType() == null || source.getSourceType().isBlank()
                    || source.getSourceAmount().compareTo(BigDecimal.ZERO) <= 0 || source.getSourceRefId() == null) {
                throw new BusinessException("PAYMENT_SOURCE_INVALID", "付款来源及来源金额必须完整且金额大于0");
            }
            switch (source.getSourceType()) {
                case PaymentIntegrityConstants.SOURCE_EXPENSE -> {
                    source.setExpenseId(source.getSourceRefId());
                    source.setSettlementId(null);
                    source.setSubMeasureId(null);
                }
                case PaymentIntegrityConstants.SOURCE_SETTLEMENT -> {
                    source.setSettlementId(source.getSourceRefId());
                    source.setExpenseId(null);
                    source.setSubMeasureId(null);
                }
                case PaymentIntegrityConstants.SOURCE_SUB_MEASURE -> {
                    source.setSubMeasureId(source.getSourceRefId());
                    source.setExpenseId(null);
                    source.setSettlementId(null);
                }
                case PaymentIntegrityConstants.SOURCE_DIRECT -> {
                    if (!Objects.equals(source.getSourceRefId(), app.getId())) {
                        throw new BusinessException("DIRECT_SOURCE_INVALID", "直接付款来源ID必须等于付款申请ID");
                    }
                    source.setExpenseId(null);
                    source.setSettlementId(null);
                    source.setSubMeasureId(null);
                }
                default -> throw new BusinessException("PAYMENT_SOURCE_TYPE_INVALID", "付款来源类型仅支持 EXPENSE、SUB_MEASURE、SETTLEMENT、DIRECT");
            }
            String key = source.getSourceType() + ":" + source.getSourceRefId();
            if (!keys.add(key)) throw new BusinessException("PAYMENT_SOURCE_DUPLICATE", "付款来源重复: " + key);
            total = total.add(source.getSourceAmount());
        }
        if (money(app.getApplyAmount()).compareTo(total) != 0) {
            throw new BusinessException("PAYMENT_SOURCE_AMOUNT_MISMATCH",
                    "付款申请金额与统一来源金额合计不一致");
        }
    }

    private void validateSourceBusiness(PayApplication app, List<PaymentApplicationSource> sources,
                                        boolean alreadyAllocated) {
        for (PaymentApplicationSource source : sources) {
            if (PaymentIntegrityConstants.SOURCE_EXPENSE.equals(source.getSourceType())) {
                ExpenseApplication expense = alreadyAllocated
                        ? expenseMapper.selectById(source.getExpenseId())
                        : expenseMapper.selectByIdForUpdate(source.getExpenseId(), app.getTenantId());
                if (expense == null || !Objects.equals(expense.getTenantId(), app.getTenantId())
                        || !"APPROVED".equals(expense.getApprovalStatus())) {
                    throw new BusinessException("EXPENSE_SOURCE_NOT_APPROVED", "费用来源不存在、跨租户或未审批通过");
                }
                if (!Objects.equals(expense.getProjectId(), app.getProjectId())
                        || !Objects.equals(expense.getContractId(), app.getContractId())
                        || !Objects.equals(expense.getPayeePartnerId(), app.getPartnerId())
                        || !Objects.equals(expense.getCostSubjectId(), app.getCostSubjectId())
                        || !Objects.equals(expense.getBudgetLineId(), app.getBudgetLineId())) {
                    throw new BusinessException("EXPENSE_SOURCE_CONTEXT_MISMATCH", "费用来源与付款申请的项目、合同、付款对象或预算科目不一致");
                }
                BigDecimal available = money(expense.getAmount()).subtract(money(expense.getConvertedAmount()));
                if (alreadyAllocated) available = available.add(source.getSourceAmount());
                if (source.getSourceAmount().compareTo(available) > 0) {
                    throw new BusinessException("EXPENSE_AVAILABLE_AMOUNT_INSUFFICIENT", "费用来源可转付款金额不足");
                }
            } else if (PaymentIntegrityConstants.SOURCE_SETTLEMENT.equals(source.getSourceType())) {
                if (!"FINAL".equals(app.getPayType())) {
                    throw new BusinessException("SETTLEMENT_SOURCE_PAY_TYPE_INVALID", "终期结算来源仅允许发起结算款付款申请");
                }
                StlSettlement settlement = settlementMapper.selectByIdForUpdate(source.getSettlementId(), app.getTenantId());
                if (settlement == null || !SettlementStatusConstants.APPROVAL_APPROVED.equals(settlement.getApprovalStatus())
                        || !SettlementStatusConstants.SETTLEMENT_FINALIZED.equals(settlement.getSettlementStatus())) {
                    throw new BusinessException("SETTLEMENT_SOURCE_NOT_FINALIZED", "结算来源不存在、跨租户、未审批或未定案");
                }
                if (!Objects.equals(settlement.getProjectId(), app.getProjectId())
                        || !Objects.equals(settlement.getContractId(), app.getContractId())
                        || !Objects.equals(settlement.getPartnerId(), app.getPartnerId())) {
                    throw new BusinessException("SETTLEMENT_SOURCE_CONTEXT_MISMATCH", "结算来源与付款申请的项目、合同或付款对象不一致");
                }
                BigDecimal committed = sourceMapper.sumCommittedSettlement(app.getTenantId(), settlement.getId(), app.getId());
                BigDecimal available = money(settlement.getFinalAmount()).subtract(money(settlement.getPaidAmount()))
                        .subtract(money(committed));
                if (source.getSourceAmount().compareTo(available) > 0) {
                    throw new BusinessException("SETTLEMENT_AVAILABLE_AMOUNT_INSUFFICIENT", "结算来源可申请付款金额不足");
                }
            } else if (PaymentIntegrityConstants.SOURCE_SUB_MEASURE.equals(source.getSourceType())) {
                if (!"PROGRESS".equals(app.getPayType()) || !"SUBCONTRACT".equals(app.getExpenseCategory())) {
                    throw new BusinessException("SUB_MEASURE_SOURCE_PAY_TYPE_INVALID", "分包计量来源仅允许发起分包费进度款");
                }
                SubMeasure measure = subMeasureMapper.selectByIdForUpdate(source.getSubMeasureId(), app.getTenantId());
                if (measure == null || !"APPROVED".equals(measure.getApprovalStatus())
                        || !"CONFIRMED".equals(measure.getStatus())) {
                    throw new BusinessException("SUB_MEASURE_SOURCE_NOT_APPROVED", "分包计量来源不存在、跨租户或未审批确认");
                }
                if (!Objects.equals(measure.getProjectId(), app.getProjectId())
                        || !Objects.equals(measure.getContractId(), app.getContractId())
                        || !Objects.equals(measure.getPartnerId(), app.getPartnerId())) {
                    throw new BusinessException("SUB_MEASURE_SOURCE_CONTEXT_MISMATCH", "分包计量与付款申请的项目、合同或付款对象不一致");
                }
                List<SettlementSubMeasure> finalSettlementLinks = settlementSubMeasureMapper.selectList(
                        new LambdaQueryWrapper<SettlementSubMeasure>()
                                .eq(SettlementSubMeasure::getTenantId, app.getTenantId())
                                .eq(SettlementSubMeasure::getSubMeasureId, measure.getId()));
                for (SettlementSubMeasure link : finalSettlementLinks) {
                    StlSettlement finalSettlement = settlementMapper.selectById(link.getSettlementId());
                    if (finalSettlement != null && Set.of("APPROVING", "APPROVED")
                            .contains(finalSettlement.getApprovalStatus())) {
                        throw new BusinessException("SUB_MEASURE_ALREADY_IN_FINAL_SETTLEMENT",
                                "该计量已进入终期结算，禁止再发起进度付款");
                    }
                }
                BigDecimal committed = sourceMapper.sumCommittedSubMeasure(
                        app.getTenantId(), measure.getId(), app.getId());
                BigDecimal available = money(measure.getNetAmount()).subtract(money(committed));
                if (source.getSourceAmount().compareTo(available) > 0) {
                    throw new BusinessException("SUB_MEASURE_AVAILABLE_AMOUNT_INSUFFICIENT", "分包计量可申请付款金额不足");
                }
            }
        }
    }

    private List<PaymentApplicationSource> loadSources(PayApplication app) {
        return sourceMapper.selectList(new LambdaQueryWrapper<PaymentApplicationSource>()
                .eq(PaymentApplicationSource::getTenantId, app.getTenantId())
                .eq(PaymentApplicationSource::getPayApplicationId, app.getId())
                .orderByAsc(PaymentApplicationSource::getCreatedAt));
    }

    private PayApplication requireApplication(Long id, String action) {
        PayApplication app = applicationMapper.selectById(id);
        if (app == null || !Objects.equals(app.getTenantId(), UserContext.getCurrentTenantId())) {
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");
        }
        projectAccessChecker.checkAccess(app.getProjectId(), action);
        return app;
    }

    private PaymentApplicationSourceVO toVO(PaymentApplicationSource source) {
        PaymentApplicationSourceVO vo = new PaymentApplicationSourceVO();
        vo.setId(String.valueOf(source.getId()));
        vo.setPayApplicationId(String.valueOf(source.getPayApplicationId()));
        vo.setSourceType(source.getSourceType());
        vo.setSourceRefId(String.valueOf(source.getSourceRefId()));
        vo.setExpenseId(source.getExpenseId() == null ? null : String.valueOf(source.getExpenseId()));
        vo.setSettlementId(source.getSettlementId() == null ? null : String.valueOf(source.getSettlementId()));
        vo.setSubMeasureId(source.getSubMeasureId() == null ? null : String.valueOf(source.getSubMeasureId()));
        vo.setSourceAmount(money(source.getSourceAmount()).toPlainString());
        vo.setPaidAmount(money(source.getPaidAmount()).toPlainString());
        vo.setRemark(source.getRemark());
        return vo;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
