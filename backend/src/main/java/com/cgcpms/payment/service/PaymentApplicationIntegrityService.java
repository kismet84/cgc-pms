package com.cgcpms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentApplicationIntegrityService {
    private final PmProjectMapper projectMapper;
    private final CtContractMapper contractMapper;
    private final MdPartnerMapper partnerMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final ProjectBudgetLineMapper budgetLineMapper;
    private final ProjectBudgetMapper budgetMapper;
    private final SysFileMapper fileMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final PaymentApplicationSourceService sourceService;

    public void validateAndAllocateForSubmit(PayApplication app) {
        validateCore(app, true);
        sourceService.validateAndAllocateForSubmit(app);
    }

    public void validateForApproval(PayApplication app) {
        validateCore(app, true);
        sourceService.validateAllocated(app);
    }

    private void validateCore(PayApplication app, boolean requireAttachment) {
        Long tenantId = UserContext.getCurrentTenantId();
        if (!Objects.equals(app.getTenantId(), tenantId)) {
            throw new BusinessException("PAYMENT_TENANT_MISMATCH", "付款申请不属于当前租户");
        }
        PmProject project = projectMapper.selectById(app.getProjectId());
        projectAccessChecker.checkAccess(project, "校验付款申请");
        if (!ProjectStatusConstants.ACTIVE.equals(project.getStatus())) {
            throw new BusinessException("PROJECT_NOT_ACTIVE", "只有进行中的项目可以提交付款申请");
        }

        CtContract contract = contractMapper.selectById(app.getContractId());
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)
                || !Objects.equals(contract.getProjectId(), app.getProjectId())) {
            throw new BusinessException("PAYMENT_CONTRACT_INVALID", "付款合同不存在、跨租户或不属于当前项目");
        }
        if (!ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus())) {
            throw new BusinessException("CONTRACT_STATUS_INVALID", "只有审批通过且履约中的合同可以提交付款申请");
        }

        MdPartner partner = partnerMapper.selectById(app.getPartnerId());
        if (partner == null || !Objects.equals(partner.getTenantId(), tenantId)) {
            throw new BusinessException("PAYMENT_PAYEE_INVALID", "付款对象不存在或跨租户");
        }
        if (!Objects.equals(contract.getPartyBId(), app.getPartnerId())) {
            throw new BusinessException("PAYMENT_PAYEE_CONTRACT_MISMATCH", "付款对象必须是合同乙方");
        }

        CostSubject subject = costSubjectMapper.selectById(app.getCostSubjectId());
        if (subject == null || !Objects.equals(subject.getTenantId(), tenantId)
                || !"ENABLE".equals(subject.getStatus())) {
            throw new BusinessException("PAYMENT_COST_SUBJECT_INVALID", "费用分类科目不存在、跨租户或已停用");
        }
        ProjectBudgetLine line = budgetLineMapper.selectById(app.getBudgetLineId());
        if (line == null || !Objects.equals(line.getTenantId(), tenantId)
                || !Objects.equals(line.getProjectId(), app.getProjectId())
                || !Objects.equals(line.getCostSubjectId(), app.getCostSubjectId())) {
            throw new BusinessException("PAYMENT_BUDGET_LINE_INVALID", "预算科目与项目或费用分类不一致");
        }
        ProjectBudget budget = budgetMapper.selectById(line.getBudgetId());
        if (budget == null || !Objects.equals(budget.getTenantId(), tenantId)
                || !BudgetStatusConstants.STATUS_ACTIVE.equals(budget.getStatus())
                || !Integer.valueOf(1).equals(budget.getActiveFlag())) {
            throw new BusinessException("BUDGET_NOT_ACTIVE", "付款申请必须关联当前生效预算");
        }
        if (!StringUtils.hasText(app.getExpenseCategory())
                || !StringUtils.hasText(app.getApplyReason())
                || app.getApplyAmount() == null || app.getApplyAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("PAYMENT_REQUIRED_FIELDS_MISSING", "费用分类、付款对象、金额和申请事由必须完整");
        }
        if (requireAttachment && fileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId)
                .eq(SysFile::getBusinessType, "PAYMENT")
                .eq(SysFile::getBusinessId, app.getId())) == 0) {
            throw new BusinessException("PAYMENT_ATTACHMENT_REQUIRED", "付款申请必须上传附件后才能提交");
        }
    }
}
