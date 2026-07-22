package com.cgcpms.budget.service;

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
import com.cgcpms.budget.vo.BudgetAvailabilityVO;
import com.cgcpms.budget.vo.ProjectBudgetVO;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectBudgetService {
    private final ProjectBudgetMapper budgetMapper;
    private final ProjectBudgetLineMapper lineMapper;
    private final PmProjectMapper projectMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final WorkflowEngine workflowEngine;
    private final WfInstanceMapper wfInstanceMapper;

    public IPage<ProjectBudgetVO> getPage(long pageNo, long pageSize, Long projectId, String status,
                                          LocalDate startDate, LocalDate endDate) {
        validateDateWindow(startDate, endDate);
        LambdaQueryWrapper<ProjectBudget> wrapper = new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查看项目预算");
            wrapper.eq(ProjectBudget::getProjectId, projectId);
        } else {
            List<Long> accessibleProjectIds = projectAccessChecker.accessibleProjectIds();
            if (accessibleProjectIds.isEmpty()) {
                wrapper.eq(ProjectBudget::getProjectId, -1L);
            } else {
                wrapper.in(ProjectBudget::getProjectId, accessibleProjectIds);
            }
        }
        if (status != null && !status.isBlank()) wrapper.eq(ProjectBudget::getStatus, status);
        // Budget has no business occurrence date; immutable server audit creation time is the report date.
        if (startDate != null) wrapper.ge(ProjectBudget::getCreatedAt, startDate.atStartOfDay());
        if (endDate != null) wrapper.lt(ProjectBudget::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        wrapper.orderByDesc(ProjectBudget::getCreatedAt);
        return budgetMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, pageSize))), wrapper)
                .convert(budget -> toVO(budget, false));
    }

    private static void validateDateWindow(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException("BUDGET_REPORT_DATE_INVALID", "预算报表开始日期不能晚于结束日期");
        }
    }

    public ProjectBudgetVO getById(Long id) {
        ProjectBudget budget = requireBudget(id);
        projectAccessChecker.checkAccess(budget.getProjectId(), "查看项目预算");
        return toVO(budget, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ProjectBudget budget) {
        PmProject project = requireWritableProject(budget.getProjectId(), "创建项目预算");
        Long tenantId = UserContext.getCurrentTenantId();
        budget.setTenantId(tenantId);
        budget.setProjectId(project.getId());
        budget.setTotalAmount(money(budget.getTotalAmount()));
        budget.setApprovalStatus(BudgetStatusConstants.APPROVAL_DRAFT);
        budget.setStatus(BudgetStatusConstants.STATUS_DRAFT);
        budget.setActiveFlag(0);
        budget.setActiveToken(null);
        budget.setVersion(0);
        try {
            budgetMapper.insert(budget);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("BUDGET_VERSION_DUPLICATE", "该项目预算版本号已存在");
        }
        return budget.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ProjectBudget input, Integer expectedVersion) {
        ProjectBudget existing = requireEditableBudget(input.getId());
        requireVersion(expectedVersion, existing);
        requireWritableProject(existing.getProjectId(), "编辑项目预算");
        existing.setVersionNo(input.getVersionNo());
        existing.setBudgetName(input.getBudgetName());
        existing.setTotalAmount(money(input.getTotalAmount()));
        existing.setRemark(input.getRemark());
        try {
            if (budgetMapper.updateById(existing) != 1) {
                throw new BusinessException("BUDGET_CONCURRENT_UPDATE", "预算已被其他用户修改，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException("BUDGET_VERSION_DUPLICATE", "该项目预算版本号已存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveLines(Long budgetId, Integer expectedVersion, List<ProjectBudgetLine> lines) {
        ProjectBudget budget = requireEditableBudget(budgetId);
        requireVersion(expectedVersion, budget);
        requireWritableProject(budget.getProjectId(), "编辑项目预算科目");
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("BUDGET_LINES_REQUIRED", "项目预算至少需要一条科目明细");
        }

        Long tenantId = UserContext.getCurrentTenantId();
        Set<Long> subjectIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ProjectBudgetLine line : lines) {
            if (line == null || line.getCostSubjectId() == null) {
                throw new BusinessException("BUDGET_SUBJECT_REQUIRED", "预算明细成本科目不能为空");
            }
            if (!subjectIds.add(line.getCostSubjectId())) {
                throw new BusinessException("BUDGET_SUBJECT_DUPLICATE", "同一预算版本内成本科目不能重复");
            }
            BigDecimal amount = money(line.getBudgetAmount());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("BUDGET_AMOUNT_INVALID", "预算科目金额必须大于0");
            }
            line.setBudgetAmount(amount);
            total = total.add(amount);
        }
        if (total.compareTo(money(budget.getTotalAmount())) != 0) {
            throw new BusinessException("BUDGET_TOTAL_MISMATCH", "预算科目合计必须等于预算总额");
        }

        Map<Long, CostSubject> subjects = costSubjectMapper.selectByIds(subjectIds).stream()
                .filter(subject -> Objects.equals(subject.getTenantId(), tenantId))
                .collect(Collectors.toMap(CostSubject::getId, Function.identity()));
        if (subjects.size() != subjectIds.size()
                || subjects.values().stream().anyMatch(subject -> !"ENABLE".equals(subject.getStatus()))) {
            throw new BusinessException("BUDGET_SUBJECT_INVALID", "预算包含不存在、跨租户或已停用的成本科目");
        }

        // Reserve parent version before replacing children; loser fails before any unique-key side effect.
        bumpVersion(budgetId, expectedVersion);
        lineMapper.hardDeleteDraftLines(budgetId, tenantId);
        for (ProjectBudgetLine line : lines) {
            line.setId(null);
            line.setTenantId(tenantId);
            line.setBudgetId(budgetId);
            line.setProjectId(budget.getProjectId());
            line.setReservedAmount(BigDecimal.ZERO.setScale(2));
            line.setConsumedAmount(BigDecimal.ZERO.setScale(2));
            line.setVersion(0);
            lineMapper.insert(line);
        }
    }

    /** Internal compatibility path; HTTP writes always provide an explicit version. */
    public void saveLines(Long budgetId, List<ProjectBudgetLine> lines) {
        saveLines(budgetId, requireBudget(budgetId).getVersion(), lines);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer expectedVersion) {
        ProjectBudget budget = requireEditableBudget(id);
        requireVersion(expectedVersion, budget);
        projectAccessChecker.checkAccess(budget.getProjectId(), "删除项目预算");
        bumpVersion(id, expectedVersion);
        lineMapper.hardDeleteDraftLines(id, UserContext.getCurrentTenantId());
        int deleted = budgetMapper.delete(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getId, id)
                .eq(ProjectBudget::getTenantId, UserContext.getCurrentTenantId())
                .eq(ProjectBudget::getVersion, expectedVersion + 1));
        if (deleted != 1) throw concurrentUpdate();
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Integer expectedVersion) {
        ProjectBudget budget = requireEditableBudget(id);
        requireVersion(expectedVersion, budget);
        validateForSubmit(budget);
        // Claim this business revision before creating workflow children.
        bumpVersion(id, expectedVersion);
        WfInstance instance = BudgetStatusConstants.APPROVAL_REJECTED.equals(budget.getApprovalStatus())
                ? workflowEngine.resubmitProjectBudget(findWorkflowInstance(id), UserContext.getCurrentUserId(), UserContext.getCurrentUsername())
                : workflowEngine.submitProjectBudget(
                UserContext.getCurrentUserId(),
                UserContext.getCurrentUsername(),
                UserContext.getCurrentTenantId(),
                WorkflowBusinessTypes.PROJECT_BUDGET,
                budget.getId(),
                budget.getBudgetName(),
                budget.getTotalAmount(),
                budget.getProjectId(),
                null,
                null, null, null);
        int submitted = budgetMapper.update(null, new LambdaUpdateWrapper<ProjectBudget>()
                .eq(ProjectBudget::getId, id)
                .eq(ProjectBudget::getTenantId, UserContext.getCurrentTenantId())
                .eq(ProjectBudget::getApprovalStatus, budget.getApprovalStatus())
                .eq(ProjectBudget::getVersion, expectedVersion + 1)
                .set(ProjectBudget::getApprovalStatus, BudgetStatusConstants.APPROVAL_APPROVING));
        if (submitted != 1) throw concurrentUpdate();
    }

    public void validateForSubmit(ProjectBudget budget) {
        requireWritableProject(budget.getProjectId(), "提交项目预算审批");
        List<ProjectBudgetLine> lines = lines(budget.getId());
        if (lines.isEmpty()) throw new BusinessException("BUDGET_LINES_REQUIRED", "项目预算至少需要一条科目明细");
        BigDecimal total = lines.stream().map(ProjectBudgetLine::getBudgetAmount)
                .map(ProjectBudgetService::money).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(money(budget.getTotalAmount())) != 0) {
            throw new BusinessException("BUDGET_TOTAL_MISMATCH", "预算科目合计必须等于预算总额");
        }
    }

    public List<BudgetAvailabilityVO> getAvailability(Long budgetId) {
        ProjectBudget budget = requireBudget(budgetId);
        projectAccessChecker.checkAccess(budget.getProjectId(), "查看预算余额");
        return lines(budgetId).stream().map(this::toAvailability).toList();
    }

    public ProjectBudget findActiveByProject(Long projectId) {
        return budgetMapper.selectOne(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getTenantId, UserContext.getCurrentTenantId())
                .eq(ProjectBudget::getProjectId, projectId)
                .eq(ProjectBudget::getActiveFlag, 1)
                .eq(ProjectBudget::getStatus, BudgetStatusConstants.STATUS_ACTIVE));
    }

    private ProjectBudget requireEditableBudget(Long id) {
        ProjectBudget budget = requireBudget(id);
        if (!BudgetStatusConstants.APPROVAL_DRAFT.equals(budget.getApprovalStatus())
                && !BudgetStatusConstants.APPROVAL_REJECTED.equals(budget.getApprovalStatus())) {
            throw new BusinessException("BUDGET_NOT_EDITABLE", "只有草稿或驳回状态的预算可以编辑");
        }
        if (Integer.valueOf(1).equals(budget.getActiveFlag())) {
            throw new BusinessException("BUDGET_ACTIVE_LOCKED", "已生效预算不可编辑");
        }
        return budget;
    }

    private void requireVersion(Integer expectedVersion, ProjectBudget budget) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new BusinessException("BUDGET_VERSION_REQUIRED", "客户端版本不能为空且必须大于等于0");
        }
        if (!Objects.equals(expectedVersion, budget.getVersion())) throw concurrentUpdate();
    }

    private void bumpVersion(Long id, Integer expectedVersion) {
        int updated = budgetMapper.update(null, new LambdaUpdateWrapper<ProjectBudget>()
                .eq(ProjectBudget::getId, id)
                .eq(ProjectBudget::getTenantId, UserContext.getCurrentTenantId())
                .eq(ProjectBudget::getVersion, expectedVersion)
                .set(ProjectBudget::getVersion, expectedVersion + 1));
        if (updated != 1) throw concurrentUpdate();
    }

    private Long findWorkflowInstance(Long budgetId) {
        WfInstance instance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, WorkflowBusinessTypes.PROJECT_BUDGET)
                .eq(WfInstance::getBusinessId, budgetId));
        if (instance == null) throw new BusinessException("BUDGET_WORKFLOW_INSTANCE_NOT_FOUND", "驳回预算缺少原审批实例");
        return instance.getId();
    }

    private static BusinessException concurrentUpdate() {
        return new BusinessException("BUDGET_CONCURRENT_UPDATE", "预算已被其他用户修改，请刷新后重试");
    }

    private ProjectBudget requireBudget(Long id) {
        ProjectBudget budget = budgetMapper.selectById(id);
        if (budget == null || !Objects.equals(budget.getTenantId(), UserContext.getCurrentTenantId())) {
            throw new BusinessException("BUDGET_NOT_FOUND", "项目预算不存在");
        }
        return budget;
    }

    private PmProject requireWritableProject(Long projectId, String action) {
        PmProject project = projectMapper.selectById(projectId);
        projectAccessChecker.checkAccess(project, action);
        if ("CLOSED".equals(project.getStatus()) || "ARCHIVED".equals(project.getStatus())) {
            throw new BusinessException("PROJECT_STATUS_INVALID", "已关闭或已归档项目不可执行预算操作");
        }
        return project;
    }

    private List<ProjectBudgetLine> lines(Long budgetId) {
        return lineMapper.selectList(new LambdaQueryWrapper<ProjectBudgetLine>()
                .eq(ProjectBudgetLine::getTenantId, UserContext.getCurrentTenantId())
                .eq(ProjectBudgetLine::getBudgetId, budgetId)
                .orderByAsc(ProjectBudgetLine::getCostSubjectId));
    }

    private ProjectBudgetVO toVO(ProjectBudget budget, boolean includeLines) {
        ProjectBudgetVO vo = new ProjectBudgetVO();
        vo.setId(String.valueOf(budget.getId()));
        vo.setProjectId(String.valueOf(budget.getProjectId()));
        vo.setVersionNo(budget.getVersionNo());
        vo.setBudgetName(budget.getBudgetName());
        vo.setTotalAmount(money(budget.getTotalAmount()).toPlainString());
        vo.setApprovalStatus(budget.getApprovalStatus());
        vo.setStatus(budget.getStatus());
        vo.setActive(Integer.valueOf(1).equals(budget.getActiveFlag()));
        vo.setEffectiveAt(budget.getEffectiveAt() == null ? null : budget.getEffectiveAt().format(DateTimeUtils.DTF));
        vo.setVersion(budget.getVersion());
        vo.setCreatedAt(budget.getCreatedAt() == null ? null : budget.getCreatedAt().format(DateTimeUtils.DTF));
        vo.setUpdatedAt(budget.getUpdatedAt() == null ? null : budget.getUpdatedAt().format(DateTimeUtils.DTF));
        vo.setRemark(budget.getRemark());
        if (includeLines) {
            List<ProjectBudgetLine> lines = lines(budget.getId());
            Set<Long> ids = lines.stream().map(ProjectBudgetLine::getCostSubjectId).collect(Collectors.toSet());
            Map<Long, String> names = ids.isEmpty() ? Map.of() : costSubjectMapper.selectByIds(ids).stream()
                    .collect(Collectors.toMap(CostSubject::getId, CostSubject::getSubjectName));
            vo.setLines(lines.stream().map(line -> toLineVO(line, names.get(line.getCostSubjectId()))).toList());
        }
        return vo;
    }

    private ProjectBudgetVO.BudgetLineVO toLineVO(ProjectBudgetLine line, String subjectName) {
        ProjectBudgetVO.BudgetLineVO vo = new ProjectBudgetVO.BudgetLineVO();
        vo.setId(String.valueOf(line.getId()));
        vo.setCostSubjectId(String.valueOf(line.getCostSubjectId()));
        vo.setCostSubjectName(subjectName);
        vo.setBudgetAmount(money(line.getBudgetAmount()).toPlainString());
        vo.setReservedAmount(money(line.getReservedAmount()).toPlainString());
        vo.setConsumedAmount(money(line.getConsumedAmount()).toPlainString());
        vo.setAvailableAmount(available(line).toPlainString());
        vo.setVersion(line.getVersion());
        vo.setRemark(line.getRemark());
        return vo;
    }

    private BudgetAvailabilityVO toAvailability(ProjectBudgetLine line) {
        BudgetAvailabilityVO vo = new BudgetAvailabilityVO();
        vo.setBudgetId(String.valueOf(line.getBudgetId()));
        vo.setBudgetLineId(String.valueOf(line.getId()));
        vo.setProjectId(String.valueOf(line.getProjectId()));
        vo.setCostSubjectId(String.valueOf(line.getCostSubjectId()));
        vo.setBudgetAmount(money(line.getBudgetAmount()).toPlainString());
        vo.setReservedAmount(money(line.getReservedAmount()).toPlainString());
        vo.setConsumedAmount(money(line.getConsumedAmount()).toPlainString());
        vo.setAvailableAmount(available(line).toPlainString());
        return vo;
    }

    private static BigDecimal available(ProjectBudgetLine line) {
        return money(line.getBudgetAmount()).subtract(money(line.getReservedAmount())).subtract(money(line.getConsumedAmount()));
    }

    static BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }
}
