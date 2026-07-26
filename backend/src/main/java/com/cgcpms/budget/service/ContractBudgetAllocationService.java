package com.cgcpms.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ContractBudgetAllocation;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ContractBudgetAllocationMapper;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContractBudgetAllocationService {
    private final ContractBudgetAllocationMapper allocationMapper;
    private final ProjectBudgetLineMapper lineMapper;
    private final ProjectBudgetMapper budgetMapper;
    private final CtContractMapper contractMapper;
    private final PayApplicationMapper applicationMapper;
    private final ProjectAccessChecker projectAccessChecker;

    public List<ContractBudgetAllocation> list(Long contractId) {
        CtContract contract = requireContract(contractId, "查看合同预算分配");
        return allocations(contract.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(Long contractId, List<ContractBudgetAllocation> input) {
        CtContract contract = requireEditableContract(contractId);
        List<ContractBudgetAllocation> rows = input == null ? List.of() : input;
        if (rows.isEmpty()) {
            throw new BusinessException("CONTRACT_BUDGET_ALLOCATIONS_REQUIRED", "合同预算分配至少需要一条");
        }

        Set<Long> lineIds = new HashSet<>();
        BigDecimal contractTotal = BigDecimal.ZERO;
        for (ContractBudgetAllocation row : rows) {
            if (row == null || row.getBudgetLineId() == null || !lineIds.add(row.getBudgetLineId())) {
                throw new BusinessException("CONTRACT_BUDGET_LINE_DUPLICATE", "合同预算科目不能为空且不能重复");
            }
            row.setAllocatedAmount(positiveMoney(row.getAllocatedAmount()));
            contractTotal = contractTotal.add(row.getAllocatedAmount());
        }
        if (contract.getCurrentAmount() != null && contractTotal.compareTo(money(contract.getCurrentAmount())) > 0) {
            throw new BusinessException("CONTRACT_BUDGET_EXCEEDS_CONTRACT", "合同预算分配合计不能超过合同当前金额");
        }

        Set<Long> lockedLineIds = new HashSet<>(lineIds);
        allocations(contractId).forEach(row -> lockedLineIds.add(row.getBudgetLineId()));
        for (Long lineId : lockedLineIds.stream().sorted().toList()) {
            lineMapper.selectByIdForUpdate(lineId, contract.getTenantId());
        }

        for (ContractBudgetAllocation row : rows) {
            ProjectBudgetLine line = requireActiveLine(row.getBudgetLineId(), contract);
            BigDecimal allocatedByOtherContracts = allocationMapper.selectList(
                            new LambdaQueryWrapper<ContractBudgetAllocation>()
                                    .eq(ContractBudgetAllocation::getTenantId, contract.getTenantId())
                                    .eq(ContractBudgetAllocation::getBudgetLineId, line.getId())
                                    .ne(ContractBudgetAllocation::getContractId, contractId))
                    .stream().map(ContractBudgetAllocation::getAllocatedAmount)
                    .map(ContractBudgetAllocationService::money)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (allocatedByOtherContracts.add(row.getAllocatedAmount())
                    .compareTo(money(line.getBudgetAmount())) > 0) {
                throw new BusinessException("CONTRACT_BUDGET_OVERALLOCATED", "预算科目合同分配合计超过预算金额");
            }
        }

        int existing = allocations(contractId).size();
        if (allocationMapper.hardDeleteEditable(contractId, contract.getTenantId()) != existing) {
            throw new BusinessException("CONTRACT_BUDGET_ALLOCATION_LOCKED", "合同预算已产生占用或消耗，不可覆盖");
        }
        for (ContractBudgetAllocation row : rows) {
            row.setId(null);
            row.setTenantId(contract.getTenantId());
            row.setProjectId(contract.getProjectId());
            row.setContractId(contractId);
            row.setReservedAmount(BigDecimal.ZERO.setScale(2));
            row.setConsumedAmount(BigDecimal.ZERO.setScale(2));
            row.setVersion(0);
            allocationMapper.insert(row);
        }
    }

    public void validateForContractSubmit(Long contractId) {
        CtContract contract = requireContract(contractId, "提交合同预算校验");
        List<ContractBudgetAllocation> rows = allocations(contractId);
        if (rows.isEmpty()) {
            throw new BusinessException("CONTRACT_BUDGET_ALLOCATIONS_REQUIRED", "合同提交前必须完成预算科目分配");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ContractBudgetAllocation row : rows) {
            if (money(row.getAllocatedAmount()).signum() <= 0
                    || money(row.getReservedAmount()).signum() != 0
                    || money(row.getConsumedAmount()).signum() != 0) {
                throw new BusinessException("CONTRACT_BUDGET_ALLOCATION_INVALID", "合同提交前预算分配金额必须有效且未被使用");
            }
            requireActiveLine(row.getBudgetLineId(), contract);
            total = total.add(money(row.getAllocatedAmount()));
        }
        if (contract.getCurrentAmount() != null && total.compareTo(money(contract.getCurrentAmount())) > 0) {
            throw new BusinessException("CONTRACT_BUDGET_EXCEEDS_CONTRACT", "合同预算分配合计不能超过合同当前金额");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void reserveForPayment(PayApplication app) {
        ContractBudgetAllocation allocation = requirePaymentAllocation(app);
        BigDecimal amount = money(app.getApplyAmount());
        if (allocationMapper.reserveIfAvailable(allocation.getId(), app.getTenantId(), amount) != 1) {
            throw new BusinessException("CONTRACT_BUDGET_INSUFFICIENT", "合同预算分配可用额度不足或已被并发占用");
        }
        app.setContractBudgetAllocationId(allocation.getId());
        if (applicationMapper.update(null, new LambdaUpdateWrapper<PayApplication>()
                .eq(PayApplication::getId, app.getId())
                .eq(PayApplication::getTenantId, app.getTenantId())
                .set(PayApplication::getContractBudgetAllocationId, allocation.getId())) != 1) {
            throw new BusinessException("PAY_APP_STATUS_CONFLICT", "付款申请已被并发更新，请刷新后重试");
        }
    }

    public void validateReserved(PayApplication app) {
        ContractBudgetAllocation allocation = requireLinkedAllocation(app);
        if (money(allocation.getReservedAmount()).compareTo(money(app.getApplyAmount())) < 0) {
            throw new BusinessException("CONTRACT_BUDGET_RESERVATION_MISSING", "付款申请合同预算占用不足");
        }
    }

    public void validatePaymentAvailable(PayApplication app, BigDecimal amount) {
        ContractBudgetAllocation allocation = requireLinkedAllocation(app);
        if (money(allocation.getReservedAmount()).compareTo(money(amount)) < 0) {
            throw new BusinessException("CONTRACT_BUDGET_INSUFFICIENT", "合同预算占用不足，禁止付款");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseForPayment(PayApplication app) {
        ContractBudgetAllocation allocation = requireLinkedAllocation(app);
        if (allocationMapper.releaseReserved(allocation.getId(), app.getTenantId(), money(app.getApplyAmount())) != 1) {
            throw new BusinessException("CONTRACT_BUDGET_RELEASE_CONFLICT", "合同预算占用释放失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void consumeForArchive(PayApplication app, PayRecord record) {
        ContractBudgetAllocation allocation = requireLinkedAllocation(app);
        if (allocationMapper.consumeReserved(allocation.getId(), app.getTenantId(), money(record.getPayAmount())) != 1) {
            throw new BusinessException("CONTRACT_BUDGET_CONSUME_CONFLICT", "合同预算占用不足或已被并发消耗");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreAfterArchive(PayApplication app, PayRecord record) {
        ContractBudgetAllocation allocation = requireLinkedAllocation(app);
        if (allocationMapper.restoreConsumedToReserved(
                allocation.getId(), app.getTenantId(), money(record.getPayAmount())) != 1) {
            throw new BusinessException("CONTRACT_BUDGET_RESTORE_CONFLICT", "合同预算消耗恢复失败");
        }
    }

    private ContractBudgetAllocation requirePaymentAllocation(PayApplication app) {
        ContractBudgetAllocation allocation = allocationMapper.selectOne(
                new LambdaQueryWrapper<ContractBudgetAllocation>()
                        .eq(ContractBudgetAllocation::getTenantId, app.getTenantId())
                        .eq(ContractBudgetAllocation::getProjectId, app.getProjectId())
                        .eq(ContractBudgetAllocation::getContractId, app.getContractId())
                        .eq(ContractBudgetAllocation::getBudgetLineId, app.getBudgetLineId()));
        if (allocation == null) {
            throw new BusinessException("CONTRACT_BUDGET_ALLOCATION_MISSING", "合同未分配当前付款预算科目");
        }
        return allocationMapper.selectByIdForUpdate(allocation.getId(), app.getTenantId());
    }

    private ContractBudgetAllocation requireLinkedAllocation(PayApplication app) {
        if (app.getContractBudgetAllocationId() == null) {
            throw new BusinessException("CONTRACT_BUDGET_ALLOCATION_MISSING", "付款申请缺少合同预算分配关系");
        }
        ContractBudgetAllocation allocation = allocationMapper.selectByIdForUpdate(
                app.getContractBudgetAllocationId(), app.getTenantId());
        if (allocation == null || !Objects.equals(allocation.getProjectId(), app.getProjectId())
                || !Objects.equals(allocation.getContractId(), app.getContractId())
                || !Objects.equals(allocation.getBudgetLineId(), app.getBudgetLineId())) {
            throw new BusinessException("CONTRACT_BUDGET_ALLOCATION_MISMATCH", "付款申请合同预算分配关系不一致");
        }
        return allocation;
    }

    private ProjectBudgetLine requireActiveLine(Long lineId, CtContract contract) {
        ProjectBudgetLine line = lineMapper.selectById(lineId);
        if (line == null || !Objects.equals(line.getTenantId(), contract.getTenantId())
                || !Objects.equals(line.getProjectId(), contract.getProjectId())) {
            throw new BusinessException("CONTRACT_BUDGET_LINE_INVALID", "预算科目不存在、跨租户或不属于合同项目");
        }
        ProjectBudget budget = budgetMapper.selectById(line.getBudgetId());
        if (budget == null || !Objects.equals(budget.getTenantId(), contract.getTenantId())
                || !BudgetStatusConstants.STATUS_ACTIVE.equals(budget.getStatus())
                || !Integer.valueOf(1).equals(budget.getActiveFlag())) {
            throw new BusinessException("BUDGET_NOT_ACTIVE", "合同预算必须使用当前生效预算");
        }
        return line;
    }

    private CtContract requireEditableContract(Long contractId) {
        CtContract contract = requireContract(contractId, "编辑合同预算分配");
        if (!List.of(ContractStatusConstants.APPROVAL_DRAFT, ContractStatusConstants.APPROVAL_REJECTED)
                .contains(contract.getApprovalStatus())) {
            throw new BusinessException("CONTRACT_BUDGET_NOT_EDITABLE", "只有草稿或驳回合同可以编辑预算分配");
        }
        return contract;
    }

    private CtContract requireContract(Long contractId, String action) {
        CtContract contract = contractMapper.selectById(contractId);
        if (contract == null || !Objects.equals(contract.getTenantId(), UserContext.getCurrentTenantId())) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        }
        projectAccessChecker.checkAccess(contract.getProjectId(), action);
        return contract;
    }

    private List<ContractBudgetAllocation> allocations(Long contractId) {
        return new ArrayList<>(allocationMapper.selectList(
                new LambdaQueryWrapper<ContractBudgetAllocation>()
                        .eq(ContractBudgetAllocation::getTenantId, UserContext.getCurrentTenantId())
                        .eq(ContractBudgetAllocation::getContractId, contractId)
                        .orderByAsc(ContractBudgetAllocation::getBudgetLineId)));
    }

    private static BigDecimal positiveMoney(BigDecimal amount) {
        int integerDigits = amount == null ? 0 : Math.max(0, amount.precision() - amount.scale());
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || integerDigits > 16) {
            throw new BusinessException("CONTRACT_BUDGET_AMOUNT_INVALID", "合同预算分配金额必须大于0且最多16位整数、2位小数");
        }
        return money(amount);
    }

    private static BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }
}
