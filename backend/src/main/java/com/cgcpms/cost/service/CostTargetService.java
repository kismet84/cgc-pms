package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.mapper.CostTargetItemMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostTargetService {

    private final CostTargetMapper costTargetMapper;
    private final CostSummaryMapper costSummaryMapper;
    private final CostTargetItemMapper costTargetItemMapper;
    private final PmProjectMapper projectMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final JdbcTemplate jdbc;
    // 使用 ObjectProvider + @Lazy 解决 WorkflowEngine → CostTargetService 循环依赖
    private final ObjectProvider<WorkflowEngine> workflowEngineProvider;

    // ── Query ──

    public IPage<CostTarget> getPage(long pageNo, long pageSize,
                                     Long projectId, String versionNo,
                                     String approvalStatus, Integer isActive) {
        LambdaQueryWrapper<CostTarget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostTarget::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) wrapper.eq(CostTarget::getProjectId, projectId);
        if (StringUtils.hasText(versionNo)) wrapper.eq(CostTarget::getVersionNo, versionNo);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(CostTarget::getApprovalStatus, approvalStatus);
        if (isActive != null) wrapper.eq(CostTarget::getIsActive, isActive);
        wrapper.orderByDesc(CostTarget::getCreatedTime);

        return costTargetMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    public CostTarget getById(Long id) {
        CostTarget target = costTargetMapper.selectById(id);
        if (target == null || !target.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        }
        return target;
    }

    // ── Create ──

    @Transactional(rollbackFor = Exception.class)
    public Long create(CostTarget target) {
        PmProject project = requireWritableProject(target.getProjectId(), "创建目标成本");
        target.setTenantId(UserContext.getCurrentTenantId());
        target.setProjectId(project.getId());
        target.setApprovalStatus("DRAFT");
        target.setStatus("DRAFT");
        target.setIsActive(0);
        target.setApprovalInstanceId(null);
        target.setVersion(0);
        normalizeHeaderAmounts(target);
        try {
            costTargetMapper.insert(target);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("COST_TARGET_VERSION_DUPLICATE", "该项目目标成本版本号已存在");
        }
        log.info("Creating cost target: projectId={}", target.getProjectId());
        return target.getId();
    }

    // ── Update ──

    @Transactional(rollbackFor = Exception.class)
    public void update(CostTarget target) {
        CostTarget existing = costTargetMapper.selectById(target.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        }

        if (!Set.of("DRAFT", "REJECTED").contains(existing.getApprovalStatus()) || Integer.valueOf(1).equals(existing.getIsActive())) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "仅草稿或驳回且未生效的目标成本可编辑");
        }
        requireWritableProject(existing.getProjectId(), "编辑目标成本");
        target.setTenantId(existing.getTenantId());
        target.setProjectId(existing.getProjectId());
        target.setApprovalStatus("DRAFT");
        target.setStatus("DRAFT");
        target.setIsActive(0);
        target.setApprovalInstanceId(existing.getApprovalInstanceId());
        target.setVersion(existing.getVersion());
        normalizeHeaderAmounts(target);
        validateExistingItemsTotal(target.getId(), target);
        try {
            if (costTargetMapper.updateById(target) != 1) throw new BusinessException("COST_TARGET_CONCURRENT_UPDATE", "目标成本已被其他用户修改，请刷新后重试");
        } catch (DuplicateKeyException e) {
            throw new BusinessException("COST_TARGET_VERSION_DUPLICATE", "该项目目标成本版本号已存在");
        }
    }

    // ── Delete ──

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CostTarget existing = costTargetMapper.selectById(id);
        if (existing == null) {
            // 幂等删除：记录已被逻辑删除或不存在，直接返回成功
            log.info("目标成本已不存在或已被删除，跳过删除操作 targetId={}", id);
            return;
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        }

        // 删除守卫：被 cost_summary.cost_target_id 引用时禁止删除
        LambdaQueryWrapper<CostSummary> summaryQw = new LambdaQueryWrapper<>();
        summaryQw.eq(CostSummary::getCostTargetId, id);
        Long refCount = costSummaryMapper.selectCount(summaryQw);
        if (refCount != null && refCount > 0) {
            throw new BusinessException("COST_TARGET_IN_USE",
                    "目标成本已被成本汇总引用，无法删除。请先清理关联的成本汇总数据");
        }

        if (!Set.of("DRAFT", "REJECTED").contains(existing.getApprovalStatus()) || Integer.valueOf(1).equals(existing.getIsActive())) {
            throw new BusinessException("COST_TARGET_NOT_DELETABLE", "仅草稿或驳回且未生效的目标成本可删除");
        }

        costTargetMapper.deleteById(id); // MyBatis-Plus 逻辑删除（BaseEntity @TableLogic）
    }

    // ── Activate (版本切换) ──

    /**
     * 激活指定版本的目标成本。
     * <p>
     * 在同一事务内：先将该项目下所有其他版本的 is_active 置为 0，
     * 再将当前版本的 is_active 置为 1，状态改为 ACTIVE。
     * 使用 SELECT FOR UPDATE 防止并发激活同一项目的不同版本。
     * <p>
     * <b>H2 兼容性注意</b>：FOR UPDATE 在 MySQL 中锁定返回行，在 H2 中行为等价但锁粒度可能不同。
     * 该方法有 @Transactional 保护，在单事务内保证原子性，H2 环境测试通过。
     */
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();

        // SELECT FOR UPDATE 锁定目标行，防止并发
        LambdaQueryWrapper<CostTarget> lockQw = new LambdaQueryWrapper<>();
        lockQw.eq(CostTarget::getId, id)
                .eq(CostTarget::getTenantId, tenantId)
                .last("FOR UPDATE"); // SQL-SAFETY: fixed-sql-fragment
        CostTarget target = costTargetMapper.selectOne(lockQw);
        if (target == null) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        }

        if (!"APPROVED".equals(target.getApprovalStatus())) {
            throw new BusinessException("COST_TARGET_NOT_APPROVED", "目标成本审批通过后才能生效");
        }

        // 将该项目下所有其他版本 is_active 置为 0
        LambdaUpdateWrapper<CostTarget> deactivateWrapper = new LambdaUpdateWrapper<>();
        deactivateWrapper.eq(CostTarget::getProjectId, target.getProjectId())
                .eq(CostTarget::getTenantId, tenantId)
                .set(CostTarget::getIsActive, 0);
        costTargetMapper.update(null, deactivateWrapper);

        // 激活当前版本
        target.setIsActive(1);
        target.setStatus("ACTIVE");
        if (target.getEffectiveDate() == null) target.setEffectiveDate(java.time.LocalDate.now());
        costTargetMapper.updateById(target);
    }

    // ── Items ──

    /**
     * 获取目标成本的明细项列表。
     */
    public List<CostTargetItem> getItems(Long targetId) {
        // 验证目标成本存在且属于当前租户
        CostTarget target = costTargetMapper.selectById(targetId);
        if (target == null || !target.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        }

        LambdaQueryWrapper<CostTargetItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostTargetItem::getTargetId, targetId)
                .eq(CostTargetItem::getTenantId, UserContext.getCurrentTenantId());
        return costTargetItemMapper.selectList(wrapper);
    }

    /**
     * 批量保存目标成本明细项（先删后插）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveItems(Long targetId, List<CostTargetItem> items) {
        // 验证目标成本存在且属于当前租户
        CostTarget target = costTargetMapper.selectById(targetId);
        if (target == null || !target.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        }

        if (!Set.of("DRAFT", "REJECTED").contains(target.getApprovalStatus()) || Integer.valueOf(1).equals(target.getIsActive())) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "仅草稿或驳回且未生效的目标成本可编辑");
        }
        requireWritableProject(target.getProjectId(), "编辑目标成本责任预算");
        normalizeItems(items);
        validateItemsTotal(target, items);

        // 删除目标下现有明细
        LambdaQueryWrapper<CostTargetItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(CostTargetItem::getTargetId, targetId)
                .eq(CostTargetItem::getTenantId, UserContext.getCurrentTenantId());
        costTargetItemMapper.delete(deleteWrapper);

        // 插入新明细
        if (items != null && !items.isEmpty()) {
            Set<Long> subjects = new HashSet<>();
            int sort = 1;
            for (CostTargetItem item : items) {
                if (!subjects.add(item.getCostSubjectId())) throw new BusinessException("COST_TARGET_SUBJECT_DUPLICATE", "同一目标成本版本内成本科目不能重复");
                requireLeafCostSubject(item.getCostSubjectId(), target.getProjectId());
                requireEnabledUser(item.getResponsibleUserId());
                item.setTargetId(targetId);
                item.setTenantId(UserContext.getCurrentTenantId());
                item.setProjectId(target.getProjectId());
                item.setSortOrder(item.getSortOrder() == null ? sort : item.getSortOrder());
                costTargetItemMapper.insert(item);
                sort++;
            }
        }

        if ("REJECTED".equals(target.getApprovalStatus())) {
            target.setApprovalStatus("DRAFT");
            target.setStatus("DRAFT");
            costTargetMapper.updateById(target);
        }

        log.info("Batch saved {} items for cost target {}", items != null ? items.size() : 0, targetId);
    }

    private void validateExistingItemsTotal(Long targetId, CostTarget target) {
        List<CostTargetItem> existingItems = getItems(targetId);
        if (existingItems.isEmpty()) {
            return;
        }
        validateItemsTotal(target, existingItems);
    }

    private void validateItemsTotal(CostTarget target, List<CostTargetItem> items) {
        BigDecimal bid = sum(items, CostTargetItem::getBidCostAmount);
        BigDecimal targetAmount = sum(items, CostTargetItem::getTargetAmount);
        BigDecimal responsibility = sum(items, CostTargetItem::getResponsibilityAmount);
        if (targetAmount.compareTo(money(target.getTotalTargetAmount())) != 0) throw new BusinessException("COST_TARGET_AMOUNT_MISMATCH", "目标成本总额与科目明细合计不一致");
        if (bid.compareTo(money(target.getTotalBidCostAmount())) != 0) throw new BusinessException("COST_TARGET_BID_AMOUNT_MISMATCH", "投标成本总额与科目明细合计不一致");
        if (responsibility.compareTo(money(target.getTotalResponsibilityAmount())) != 0 || responsibility.compareTo(targetAmount) != 0) throw new BusinessException("COST_TARGET_RESPONSIBILITY_MISMATCH", "责任预算必须完整分解且与目标成本总额一致");
    }

    // ── Submit ──

    /**
     * 提交目标成本审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long targetId) {
        Long tenantId = UserContext.getCurrentTenantId();

        CostTarget target = costTargetMapper.selectById(targetId);
        if (target == null || !target.getTenantId().equals(tenantId)) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        }

        if (!Set.of("DRAFT", "REJECTED").contains(target.getApprovalStatus())) {
            throw new BusinessException("COST_TARGET_ALREADY_SUBMITTED", "仅草稿或驳回状态可以提交审批");
        }
        requireWritableProject(target.getProjectId(), "提交目标成本审批");
        validateForSubmit(target);
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        WfInstance instance = "REJECTED".equals(target.getApprovalStatus()) && target.getApprovalInstanceId() != null
                ? workflowEngineProvider.getObject().resubmit(target.getApprovalInstanceId(), userId, username)
                : workflowEngineProvider.getObject().submit(userId, username, tenantId, "COST_TARGET", targetId,
                    target.getVersionName() != null ? target.getVersionName() : target.getVersionNo(), target.getTotalTargetAmount(),
                    target.getProjectId(), null, "投标成本→目标成本→责任预算", null, null);
        costTargetMapper.update(null, new LambdaUpdateWrapper<CostTarget>()
                .eq(CostTarget::getId, targetId).eq(CostTarget::getTenantId, tenantId)
                .set(CostTarget::getApprovalStatus, "APPROVING")
                .set(CostTarget::getStatus, "APPROVING")
                .set(CostTarget::getApprovalInstanceId, instance.getId()));

        log.info("Submitted cost target {} for approval", targetId);
    }

    public void validateForSubmit(CostTarget target) {
        List<CostTargetItem> items = getItems(target.getId());
        if (items.isEmpty()) throw new BusinessException("COST_TARGET_NO_ITEMS", "目标成本至少需要一条科目明细");
        validateItemsTotal(target, items);
        Set<Long> subjects = new HashSet<>();
        for (CostTargetItem item : items) {
            if (item.getCostSubjectId() == null || !subjects.add(item.getCostSubjectId())) throw new BusinessException("COST_TARGET_ITEM_INVALID", "目标成本科目不能为空且不能重复");
            requireLeafCostSubject(item.getCostSubjectId(), target.getProjectId());
            if (item.getResponsibleUserId() == null) throw new BusinessException("COST_TARGET_RESPONSIBLE_REQUIRED", "责任预算必须落实到责任人");
            requireEnabledUser(item.getResponsibleUserId());
        }
    }

    private void requireLeafCostSubject(Long subjectId, Long projectId) {
        Integer valid = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject s
                WHERE s.tenant_id=? AND s.id=? AND s.deleted_flag=0 AND s.status='ENABLE' AND s.account_category='COST'
                  AND NOT EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id AND c.parent_id=s.id AND c.deleted_flag=0)
                  AND (NOT EXISTS (SELECT 1 FROM project_cost_subject_scope p WHERE p.tenant_id=s.tenant_id AND p.project_id=?)
                       OR EXISTS (SELECT 1 FROM project_cost_subject_scope p WHERE p.tenant_id=s.tenant_id AND p.project_id=?
                         AND p.cost_subject_id=s.id AND p.enabled=1 AND p.effective_from<=CURRENT_DATE
                         AND (p.effective_to IS NULL OR p.effective_to>=CURRENT_DATE)))
                """, Integer.class, UserContext.getCurrentTenantId(), subjectId, projectId, projectId);
        if (valid == null || valid != 1) {
            throw new BusinessException("COST_TARGET_SUBJECT_INVALID", "目标成本必须使用项目适用范围内的启用末级成本科目");
        }
    }

    private PmProject requireWritableProject(Long projectId, String action) {
        PmProject project = projectMapper.selectById(projectId);
        projectAccessChecker.checkAccess(project, action);
        if (!"ACTIVE".equals(project.getStatus())) throw new BusinessException("PROJECT_NOT_ACTIVE", "只有进行中的项目可以维护目标成本");
        return project;
    }

    private void requireEnabledUser(Long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND tenant_id=? AND status='ENABLE' AND deleted_flag=0", Integer.class, userId, UserContext.getCurrentTenantId());
        if (count == null || count != 1) throw new BusinessException("COST_TARGET_RESPONSIBLE_INVALID", "责任人不存在、跨租户或已停用");
    }

    private static void normalizeHeaderAmounts(CostTarget target) {
        target.setTotalTargetAmount(money(target.getTotalTargetAmount()));
        target.setTotalBidCostAmount(target.getTotalBidCostAmount() == null ? target.getTotalTargetAmount() : money(target.getTotalBidCostAmount()));
        target.setTotalResponsibilityAmount(target.getTotalResponsibilityAmount() == null ? target.getTotalTargetAmount() : money(target.getTotalResponsibilityAmount()));
        if (target.getTotalResponsibilityAmount().compareTo(target.getTotalTargetAmount()) != 0) throw new BusinessException("COST_TARGET_RESPONSIBILITY_MISMATCH", "责任预算总额必须等于目标成本总额");
    }

    private void normalizeItems(List<CostTargetItem> items) {
        if (items == null) return;
        for (CostTargetItem item : items) {
            item.setTargetAmount(money(item.getTargetAmount()));
            if (item.getBidCostAmount() == null) item.setBidCostAmount(item.getTargetAmount());
            else item.setBidCostAmount(money(item.getBidCostAmount()));
            if (item.getResponsibilityAmount() == null) item.setResponsibilityAmount(item.getTargetAmount());
            else item.setResponsibilityAmount(money(item.getResponsibilityAmount()));
            if (item.getResponsibleUserId() == null) item.setResponsibleUserId(UserContext.getCurrentUserId());
            if (!StringUtils.hasText(item.getResponsibilityUnit())) item.setResponsibilityUnit("项目成本责任人");
        }
    }

    private static BigDecimal sum(List<CostTargetItem> items, java.util.function.Function<CostTargetItem, BigDecimal> mapper) {
        return items == null ? BigDecimal.ZERO.setScale(2) : items.stream().map(mapper).map(CostTargetService::money).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
