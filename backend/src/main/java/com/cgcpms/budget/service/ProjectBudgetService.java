package com.cgcpms.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.budget.mapper.BudgetLedgerMapper;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.budget.mapper.ContractBudgetAllocationMapper;
import com.cgcpms.budget.vo.BudgetAvailabilityVO;
import com.cgcpms.budget.vo.ProjectBudgetVO;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostTargetItemMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectBudgetService {
    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final ProjectBudgetMapper budgetMapper;
    private final ProjectBudgetLineMapper lineMapper;
    private final ContractBudgetAllocationMapper contractBudgetAllocationMapper;
    private final BudgetLedgerMapper budgetLedgerMapper;
    private final PmProjectMapper projectMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final CostTargetMapper costTargetMapper;
    private final CostTargetItemMapper costTargetItemMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final WorkflowEngine workflowEngine;
    private final WfInstanceMapper wfInstanceMapper;
    private final CodeGenerationService codeGenerationService;

    /**
     * 将已审批目标成本投影为唯一生效执行预算。预算行按科目原位更新，保留占用、消耗和业务引用。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectBudget syncFromApprovedCostTarget(Long targetId, Long tenantId) {
        CostTarget target = costTargetMapper.selectById(targetId);
        if (target == null || !Objects.equals(target.getTenantId(), tenantId)) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "项目成本预算来源不存在");
        }
        if (!"APPROVED".equals(target.getApprovalStatus()) || !"ACTIVE".equals(target.getStatus())
                || !Integer.valueOf(1).equals(target.getIsActive())) {
            throw new BusinessException("COST_TARGET_NOT_ACTIVE", "项目成本预算审批生效后才能生成执行预算");
        }

        PmProject project = projectMapper.selectOne(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getId, target.getProjectId())
                .eq(PmProject::getTenantId, tenantId)
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        if (project == null) throw new BusinessException("PROJECT_NOT_FOUND", "项目成本预算所属项目不存在");

        List<CostTargetItem> targetItems = costTargetItemMapper.selectList(
                new LambdaQueryWrapper<CostTargetItem>()
                        .eq(CostTargetItem::getTargetId, targetId)
                        .eq(CostTargetItem::getTenantId, tenantId)
                        .orderByAsc(CostTargetItem::getSortOrder));
        Map<Long, CostTargetItem> positiveItems = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (CostTargetItem item : targetItems) {
            if (item.getCostSubjectId() == null) {
                throw new BusinessException("BUDGET_SUBJECT_REQUIRED", "项目成本预算科目不能为空");
            }
            BigDecimal amount = money(item.getResponsibilityAmount());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("BUDGET_AMOUNT_INVALID", "责任预算金额不能为负数");
            }
            total = total.add(amount);
            if (amount.signum() > 0 && positiveItems.put(item.getCostSubjectId(), item) != null) {
                throw new BusinessException("BUDGET_SUBJECT_DUPLICATE", "项目成本预算科目不能重复");
            }
        }
        if (total.signum() <= 0 || total.compareTo(money(target.getTotalResponsibilityAmount())) != 0) {
            throw new BusinessException("BUDGET_TOTAL_MISMATCH", "责任预算明细合计必须等于责任预算总额且大于零");
        }

        ProjectBudget budget = budgetMapper.selectActiveByProjectForUpdate(target.getProjectId(), tenantId);
        if (budget == null) budget = createProjectionBudget(target, total);
        else if (Objects.equals(budget.getSourceCostTargetId(), targetId)) return budget;
        else updateProjectionBudget(budget, target, total);
        reconcileProjectionLines(budget, positiveItems, tenantId);
        return budgetMapper.selectById(budget.getId());
    }

    private ProjectBudget createProjectionBudget(CostTarget target, BigDecimal total) {
        ProjectBudget budget = new ProjectBudget();
        budget.setTenantId(target.getTenantId());
        budget.setProjectId(target.getProjectId());
        budget.setSourceCostTargetId(target.getId());
        budget.setVersionNo("CT-" + target.getId());
        budget.setBudgetName(target.getVersionName());
        budget.setTotalAmount(total);
        budget.setApprovalStatus(BudgetStatusConstants.APPROVAL_APPROVED);
        budget.setStatus(BudgetStatusConstants.STATUS_ACTIVE);
        budget.setActiveFlag(1);
        budget.setActiveToken(target.getProjectId());
        budget.setEffectiveAt(LocalDateTime.now());
        budget.setVersion(0);
        budget.setRemark("由项目成本预算审批自动生成");
        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            budget.setId(null);
            budget.setBudgetCode(codeGenerationService.nextCode(
                    budgetMapper, ProjectBudget::getBudgetCode, "BUD-", target.getTenantId(), true, attempt));
            try {
                budgetMapper.insert(budget);
                return budget;
            } catch (DuplicateKeyException ignored) {
                ProjectBudget existing = budgetMapper.selectActiveByProjectForUpdate(
                        target.getProjectId(), target.getTenantId());
                if (existing != null) {
                    updateProjectionBudget(existing, target, total);
                    return existing;
                }
            }
        }
        throw new BusinessException("BUDGET_CODE_CONFLICT", "执行预算编号生成冲突，请重试");
    }

    private void updateProjectionBudget(ProjectBudget budget, CostTarget target, BigDecimal total) {
        budget.setSourceCostTargetId(target.getId());
        budget.setBudgetName(target.getVersionName());
        budget.setTotalAmount(total);
        budget.setApprovalStatus(BudgetStatusConstants.APPROVAL_APPROVED);
        budget.setStatus(BudgetStatusConstants.STATUS_ACTIVE);
        budget.setActiveFlag(1);
        budget.setActiveToken(target.getProjectId());
        budget.setEffectiveAt(LocalDateTime.now());
        budget.setRemark("由项目成本预算审批自动更新");
        if (budgetMapper.updateById(budget) != 1) throw concurrentUpdate();
    }

    private void reconcileProjectionLines(ProjectBudget budget, Map<Long, CostTargetItem> targetItems,
                                          Long tenantId) {
        Map<Long, ProjectBudgetLine> existing = lineMapper
                .selectByBudgetForUpdate(budget.getId(), tenantId).stream()
                .collect(Collectors.toMap(ProjectBudgetLine::getCostSubjectId, Function.identity()));
        for (CostTargetItem targetItem : targetItems.values()) {
            BigDecimal amount = money(targetItem.getResponsibilityAmount());
            ProjectBudgetLine line = existing.remove(targetItem.getCostSubjectId());
            if (line == null) {
                line = new ProjectBudgetLine();
                line.setTenantId(tenantId);
                line.setBudgetId(budget.getId());
                line.setProjectId(budget.getProjectId());
                line.setCostSubjectId(targetItem.getCostSubjectId());
                line.setBudgetAmount(amount);
                line.setReservedAmount(BigDecimal.ZERO.setScale(2));
                line.setConsumedAmount(BigDecimal.ZERO.setScale(2));
                line.setVersion(0);
                line.setRemark(targetItem.getRemark());
                lineMapper.insert(line);
                recordAdjustment(budget, line, targetItem.getTargetId(), amount);
                continue;
            }
            BigDecimal previousAmount = money(line.getBudgetAmount());
            BigDecimal floor = requiredBudgetFloor(line, tenantId);
            if (amount.compareTo(floor) < 0) {
                throw new BusinessException("BUDGET_BELOW_OCCUPIED",
                        "责任预算不能低于已占用、已消耗或合同已分配金额，costSubjectId=" + line.getCostSubjectId());
            }
            line.setBudgetAmount(amount);
            line.setRemark(targetItem.getRemark());
            if (lineMapper.updateById(line) != 1) {
                throw new BusinessException("BUDGET_LINE_CONCURRENT_UPDATE", "执行预算科目已被并发修改");
            }
            recordAdjustment(budget, line, targetItem.getTargetId(), amount.subtract(previousAmount));
        }
        for (ProjectBudgetLine obsolete : existing.values()) {
            BigDecimal previousAmount = money(obsolete.getBudgetAmount());
            if (requiredBudgetFloor(obsolete, tenantId).signum() > 0) {
                throw new BusinessException("BUDGET_OCCUPIED_SUBJECT_REMOVED",
                        "存在占用、消耗或合同分配的预算科目不能从项目成本预算中删除，costSubjectId="
                                + obsolete.getCostSubjectId());
            }
            obsolete.setBudgetAmount(BigDecimal.ZERO.setScale(2));
            obsolete.setRemark("已从最新项目成本预算移除，保留历史引用");
            if (lineMapper.updateById(obsolete) != 1) {
                throw new BusinessException("BUDGET_LINE_CONCURRENT_UPDATE", "执行预算科目已被并发修改");
            }
            recordAdjustment(budget, obsolete, budget.getSourceCostTargetId(),
                    previousAmount.negate());
        }
    }

    private void recordAdjustment(ProjectBudget budget, ProjectBudgetLine line, Long targetId,
                                  BigDecimal rawDelta) {
        BigDecimal delta = money(rawDelta);
        if (delta.signum() == 0) return;
        String key = "COST_TARGET:" + targetId + ":LINE:" + line.getId();
        BudgetLedger existing = budgetLedgerMapper.selectOne(new LambdaQueryWrapper<BudgetLedger>()
                .eq(BudgetLedger::getTenantId, budget.getTenantId())
                .eq(BudgetLedger::getIdempotencyKey, key));
        if (existing != null) {
            if (existing.getAmount().compareTo(delta) != 0) {
                throw new BusinessException("BUDGET_IDEMPOTENCY_CONFLICT", "项目成本预算调整幂等键冲突");
            }
            return;
        }
        BudgetLedger ledger = new BudgetLedger();
        ledger.setTenantId(budget.getTenantId());
        ledger.setBudgetId(budget.getId());
        ledger.setBudgetLineId(line.getId());
        ledger.setProjectId(budget.getProjectId());
        ledger.setBusinessType("COST_TARGET");
        ledger.setBusinessId(targetId);
        ledger.setEntryType(BudgetStatusConstants.ENTRY_ADJUST);
        ledger.setAmount(delta);
        ledger.setReservedBalance(money(line.getReservedAmount()));
        ledger.setConsumedBalance(money(line.getConsumedAmount()));
        ledger.setIdempotencyKey(key);
        ledger.setCreatedBy(UserContext.getCurrentUserId());
        ledger.setCreatedAt(LocalDateTime.now());
        ledger.setRemark("项目成本预算审批调整");
        budgetLedgerMapper.insert(ledger);
    }

    private BigDecimal requiredBudgetFloor(ProjectBudgetLine line, Long tenantId) {
        BigDecimal occupied = money(line.getReservedAmount()).add(money(line.getConsumedAmount()));
        BigDecimal allocated = money(contractBudgetAllocationMapper
                .sumAllocatedByBudgetLine(line.getId(), tenantId));
        return occupied.max(allocated);
    }

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
        throw standaloneBudgetDisabled();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ProjectBudget input, Integer expectedVersion) {
        throw standaloneBudgetDisabled();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveLines(Long budgetId, Integer expectedVersion, List<ProjectBudgetLine> lines) {
        throw standaloneBudgetDisabled();
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
        throw standaloneBudgetDisabled();
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

    private static BusinessException standaloneBudgetDisabled() {
        return new BusinessException("PROJECT_BUDGET_MANAGED_BY_COST_TARGET",
                "项目预算由项目成本预算审批自动生成，禁止独立新增、编辑或提交");
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
        if (!Set.of(ProjectStatusConstants.PREPARING, ProjectStatusConstants.ACTIVE).contains(project.getStatus())) {
            throw new BusinessException("PROJECT_STATUS_INVALID", "仅筹备或在建项目可执行预算操作");
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
        vo.setSourceCostTargetId(budget.getSourceCostTargetId() == null
                ? null : String.valueOf(budget.getSourceCostTargetId()));
        vo.setBudgetCode(budget.getBudgetCode());
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

    private boolean budgetVersionExists(Long tenantId, Long projectId, String versionNo) {
        return budgetMapper.selectCount(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getTenantId, tenantId)
                .eq(ProjectBudget::getProjectId, projectId)
                .eq(ProjectBudget::getVersionNo, versionNo)) > 0;
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
