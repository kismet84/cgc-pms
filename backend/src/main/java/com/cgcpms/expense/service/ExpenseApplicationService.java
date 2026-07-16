package com.cgcpms.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.budget.service.BudgetLedgerService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.expense.vo.ExpenseApplicationVO;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExpenseApplicationService {
    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final ExpenseApplicationMapper expenseMapper;
    private final PmProjectMapper projectMapper;
    private final CtContractMapper contractMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final ProjectBudgetLineMapper budgetLineMapper;
    private final ProjectBudgetMapper budgetMapper;
    private final MdPartnerMapper partnerMapper;
    private final SysFileMapper fileMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final BudgetLedgerService ledgerService;
    private final WorkflowEngine workflowEngine;

    public IPage<ExpenseApplicationVO> getPage(long pageNo, long pageSize, Long projectId,
                                                Long contractId, String approvalStatus) {
        LambdaQueryWrapper<ExpenseApplication> wrapper = new LambdaQueryWrapper<ExpenseApplication>()
                .eq(ExpenseApplication::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查看费用申请");
            wrapper.eq(ExpenseApplication::getProjectId, projectId);
        }
        if (contractId != null) wrapper.eq(ExpenseApplication::getContractId, contractId);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(ExpenseApplication::getApprovalStatus, approvalStatus);
        wrapper.orderByDesc(ExpenseApplication::getCreatedAt);
        return expenseMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, pageSize))), wrapper)
                .convert(this::toVO);
    }

    public ExpenseApplicationVO getById(Long id) {
        ExpenseApplication expense = requireExpense(id);
        projectAccessChecker.checkAccess(expense.getProjectId(), "查看费用申请");
        return toVO(expense);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ExpenseApplication expense) {
        validateBusinessContext(expense);
        expense.setTenantId(UserContext.getCurrentTenantId());
        expense.setAmount(money(expense.getAmount()));
        expense.setConvertedAmount(BigDecimal.ZERO.setScale(2));
        expense.setPaidAmount(BigDecimal.ZERO.setScale(2));
        expense.setStatus("DRAFT");
        expense.setApprovalStatus("DRAFT");
        expense.setVersion(0);
        String prefix = "EXP-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            expense.setExpenseCode(nextCode(prefix, attempt));
            try {
                expenseMapper.insert(expense);
                return expense.getId();
            } catch (DuplicateKeyException e) {
                // 编号并发冲突时重试；业务关系冲突会在下一轮或最终失败中暴露。
            }
        }
        throw new BusinessException("EXPENSE_CODE_CONFLICT", "费用申请编号生成冲突，请重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ExpenseApplication input) {
        ExpenseApplication existing = requireExpense(input.getId());
        if (!"DRAFT".equals(existing.getApprovalStatus()) && !"REJECTED".equals(existing.getApprovalStatus())) {
            throw new BusinessException("EXPENSE_NOT_EDITABLE", "只有草稿或驳回状态的费用申请可以编辑");
        }
        input.setTenantId(existing.getTenantId());
        input.setExpenseCode(existing.getExpenseCode());
        input.setAmount(money(input.getAmount()));
        input.setConvertedAmount(existing.getConvertedAmount());
        input.setPaidAmount(existing.getPaidAmount());
        input.setStatus("DRAFT");
        input.setApprovalStatus("DRAFT");
        input.setVersion(existing.getVersion());
        validateBusinessContext(input);
        if (expenseMapper.updateById(input) != 1) {
            throw new BusinessException("EXPENSE_CONCURRENT_UPDATE", "费用申请已被其他用户修改，请刷新后重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExpenseApplication expense = requireExpense(id);
        projectAccessChecker.checkAccess(expense.getProjectId(), "删除费用申请");
        if (!"DRAFT".equals(expense.getApprovalStatus())) {
            throw new BusinessException("EXPENSE_NOT_DELETABLE", "只有未提交的草稿费用申请可以删除");
        }
        expenseMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ExpenseApplication expense = expenseMapper.selectByIdForUpdate(id, UserContext.getCurrentTenantId());
        if (expense == null) throw new BusinessException("EXPENSE_NOT_FOUND", "费用申请不存在");
        if (!"DRAFT".equals(expense.getApprovalStatus())) {
            throw new BusinessException("EXPENSE_INVALID_STATUS", "只有草稿费用申请可以提交");
        }
        validateBusinessContext(expense);
        long attachmentCount = fileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, expense.getTenantId())
                .eq(SysFile::getBusinessType, "EXPENSE")
                .eq(SysFile::getBusinessId, expense.getId()));
        if (attachmentCount == 0) {
            throw new BusinessException("EXPENSE_ATTACHMENT_REQUIRED", "费用申请必须上传附件后才能提交");
        }

        String reserveKey = "EXPENSE:RESERVE:" + expense.getId() + ":V" + expense.getVersion();
        ledgerService.reserve(expense.getBudgetLineId(), WorkflowBusinessTypes.EXPENSE,
                expense.getId(), expense.getAmount(), reserveKey);
        workflowEngine.submit(
                UserContext.getCurrentUserId(), UserContext.getCurrentUsername(), expense.getTenantId(),
                WorkflowBusinessTypes.EXPENSE, expense.getId(), "费用申请 " + expense.getExpenseCode(),
                expense.getAmount(), expense.getProjectId(), expense.getContractId(),
                expense.getDescription(), null, null);
        expenseMapper.update(null, new LambdaUpdateWrapper<ExpenseApplication>()
                .eq(ExpenseApplication::getId, id)
                .eq(ExpenseApplication::getApprovalStatus, "DRAFT")
                .set(ExpenseApplication::getApprovalStatus, "APPROVING")
                .set(ExpenseApplication::getStatus, "APPROVING"));
    }

    private void validateBusinessContext(ExpenseApplication expense) {
        Long tenantId = UserContext.getCurrentTenantId();
        PmProject project = projectMapper.selectById(expense.getProjectId());
        projectAccessChecker.checkAccess(project, "操作费用申请");
        if (!ProjectStatusConstants.ACTIVE.equals(project.getStatus())) {
            throw new BusinessException("PROJECT_NOT_ACTIVE", "只有进行中的项目可以发起费用申请");
        }
        CtContract contract = contractMapper.selectById(expense.getContractId());
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)
                || !Objects.equals(contract.getProjectId(), expense.getProjectId())) {
            throw new BusinessException("EXPENSE_CONTRACT_INVALID", "合同不存在、跨租户或不属于当前项目");
        }
        if (!ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || ContractStatusConstants.STATUS_DRAFT.equals(contract.getContractStatus())
                || ContractStatusConstants.STATUS_SETTLED.equals(contract.getContractStatus())
                || ContractStatusConstants.STATUS_TERMINATED.equals(contract.getContractStatus())) {
            throw new BusinessException("CONTRACT_STATUS_INVALID", "合同未审批通过或已结算/终止，不能发起费用");
        }
        MdPartner partner = partnerMapper.selectById(expense.getPayeePartnerId());
        if (partner == null || !Objects.equals(partner.getTenantId(), tenantId)) {
            throw new BusinessException("EXPENSE_PAYEE_INVALID", "付款对象不存在或跨租户");
        }
        CostSubject subject = costSubjectMapper.selectById(expense.getCostSubjectId());
        if (subject == null || !Objects.equals(subject.getTenantId(), tenantId) || !"ENABLE".equals(subject.getStatus())) {
            throw new BusinessException("EXPENSE_SUBJECT_INVALID", "费用分类科目不存在、跨租户或已停用");
        }
        ProjectBudgetLine line = budgetLineMapper.selectById(expense.getBudgetLineId());
        if (line == null || !Objects.equals(line.getTenantId(), tenantId)
                || !Objects.equals(line.getProjectId(), expense.getProjectId())
                || !Objects.equals(line.getCostSubjectId(), expense.getCostSubjectId())) {
            throw new BusinessException("EXPENSE_BUDGET_LINE_INVALID", "预算科目与项目或费用分类不一致");
        }
        ProjectBudget budget = budgetMapper.selectById(line.getBudgetId());
        if (budget == null || !BudgetStatusConstants.STATUS_ACTIVE.equals(budget.getStatus())
                || !Integer.valueOf(1).equals(budget.getActiveFlag())) {
            throw new BusinessException("BUDGET_NOT_ACTIVE", "费用申请必须关联当前生效预算");
        }
        if (!StringUtils.hasText(expense.getExpenseCategory()) || !StringUtils.hasText(expense.getDescription())
                || expense.getExpenseDate() == null || money(expense.getAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("EXPENSE_REQUIRED_FIELDS_MISSING", "费用分类、日期、金额和说明必须完整");
        }
    }

    private ExpenseApplication requireExpense(Long id) {
        ExpenseApplication expense = expenseMapper.selectById(id);
        if (expense == null || !Objects.equals(expense.getTenantId(), UserContext.getCurrentTenantId())) {
            throw new BusinessException("EXPENSE_NOT_FOUND", "费用申请不存在");
        }
        return expense;
    }

    private String nextCode(String prefix, int offset) {
        Page<ExpenseApplication> page = expenseMapper.selectPage(new Page<>(0, 1),
                new LambdaQueryWrapper<ExpenseApplication>()
                        .eq(ExpenseApplication::getTenantId, UserContext.getCurrentTenantId())
                        .likeRight(ExpenseApplication::getExpenseCode, prefix)
                        .orderByDesc(ExpenseApplication::getExpenseCode));
        int sequence = 1 + offset;
        if (!page.getRecords().isEmpty()) {
            String last = page.getRecords().get(0).getExpenseCode();
            try {
                sequence = Integer.parseInt(last.substring(last.lastIndexOf('-') + 1)) + 1 + offset;
            } catch (RuntimeException ignored) {
                // 非标准历史编号不阻止生成新编号。
            }
        }
        return prefix + String.format("%03d", sequence);
    }

    private ExpenseApplicationVO toVO(ExpenseApplication expense) {
        ExpenseApplicationVO vo = new ExpenseApplicationVO();
        vo.setId(String.valueOf(expense.getId()));
        vo.setProjectId(String.valueOf(expense.getProjectId()));
        vo.setContractId(String.valueOf(expense.getContractId()));
        vo.setCostSubjectId(String.valueOf(expense.getCostSubjectId()));
        vo.setBudgetLineId(String.valueOf(expense.getBudgetLineId()));
        vo.setPayeePartnerId(String.valueOf(expense.getPayeePartnerId()));
        vo.setExpenseCode(expense.getExpenseCode());
        vo.setExpenseCategory(expense.getExpenseCategory());
        vo.setExpenseDate(expense.getExpenseDate() == null ? null : expense.getExpenseDate().toString());
        vo.setAmount(money(expense.getAmount()).toPlainString());
        vo.setConvertedAmount(money(expense.getConvertedAmount()).toPlainString());
        vo.setPaidAmount(money(expense.getPaidAmount()).toPlainString());
        vo.setAvailableToConvert(money(expense.getAmount()).subtract(money(expense.getConvertedAmount())).toPlainString());
        vo.setDescription(expense.getDescription());
        vo.setStatus(expense.getStatus());
        vo.setApprovalStatus(expense.getApprovalStatus());
        vo.setVersion(expense.getVersion());
        vo.setCreatedAt(expense.getCreatedAt() == null ? null : expense.getCreatedAt().format(DateTimeUtils.DTF));
        vo.setUpdatedAt(expense.getUpdatedAt() == null ? null : expense.getUpdatedAt().format(DateTimeUtils.DTF));
        vo.setRemark(expense.getRemark());
        return vo;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
