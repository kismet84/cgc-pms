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
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostTargetService {

    private final CostTargetMapper costTargetMapper;
    private final CostSummaryMapper costSummaryMapper;
    private final CostTargetItemMapper costTargetItemMapper;
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
        target.setTenantId(UserContext.getCurrentTenantId());
        target.setApprovalStatus(target.getApprovalStatus() != null ? target.getApprovalStatus() : "DRAFT");
        target.setStatus(target.getStatus() != null ? target.getStatus() : "DRAFT");
        target.setIsActive(target.getIsActive() != null ? target.getIsActive() : 0);

        costTargetMapper.insert(target);
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

        // 审批中守卫：禁止编辑
        if ("APPROVING".equals(existing.getApprovalStatus())) {
            throw new BusinessException("COST_TARGET_IN_APPROVAL", "目标成本审批中，不可编辑");
        }

        // 保留审批状态（禁止通过 update 接口覆盖）
        target.setApprovalStatus(existing.getApprovalStatus());
        validateExistingItemsTotal(target.getId(), target.getTotalTargetAmount());

        costTargetMapper.updateById(target);
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

        // 审批中守卫：禁止删除
        if ("APPROVING".equals(existing.getApprovalStatus())) {
            throw new BusinessException("COST_TARGET_IN_APPROVAL", "目标成本审批中，不可删除");
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

        // 将该项目下所有其他版本 is_active 置为 0
        LambdaUpdateWrapper<CostTarget> deactivateWrapper = new LambdaUpdateWrapper<>();
        deactivateWrapper.eq(CostTarget::getProjectId, target.getProjectId())
                .eq(CostTarget::getTenantId, tenantId)
                .set(CostTarget::getIsActive, 0);
        costTargetMapper.update(null, deactivateWrapper);

        // 激活当前版本
        target.setIsActive(1);
        target.setStatus("ACTIVE");
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

        // 审批中守卫：禁止编辑
        if ("APPROVING".equals(target.getApprovalStatus())) {
            throw new BusinessException("COST_TARGET_IN_APPROVAL", "目标成本审批中，不可编辑");
        }

        validateItemsTotal(target.getTotalTargetAmount(), items);

        // 删除目标下现有明细
        LambdaQueryWrapper<CostTargetItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(CostTargetItem::getTargetId, targetId)
                .eq(CostTargetItem::getTenantId, UserContext.getCurrentTenantId());
        costTargetItemMapper.delete(deleteWrapper);

        // 插入新明细
        if (items != null && !items.isEmpty()) {
            for (CostTargetItem item : items) {
                item.setTargetId(targetId);
                item.setTenantId(UserContext.getCurrentTenantId());
                item.setProjectId(target.getProjectId());
                costTargetItemMapper.insert(item);
            }
        }

        log.info("Batch saved {} items for cost target {}", items != null ? items.size() : 0, targetId);
    }

    private void validateExistingItemsTotal(Long targetId, BigDecimal totalTargetAmount) {
        List<CostTargetItem> existingItems = getItems(targetId);
        if (existingItems.isEmpty()) {
            return;
        }
        validateItemsTotal(totalTargetAmount, existingItems);
    }

    private void validateItemsTotal(BigDecimal totalTargetAmount, List<CostTargetItem> items) {
        BigDecimal targetTotal = totalTargetAmount == null ? BigDecimal.ZERO : totalTargetAmount;
        BigDecimal itemsTotal = items == null
                ? BigDecimal.ZERO
                : items.stream()
                .map(CostTargetItem::getTargetAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itemsTotal.compareTo(targetTotal) != 0) {
            throw new BusinessException(
                    "COST_TARGET_AMOUNT_MISMATCH",
                    "成本目标总额与科目明细合计不一致");
        }
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

        // 只允许草稿状态提交
        if (!"DRAFT".equals(target.getApprovalStatus())) {
            throw new BusinessException("COST_TARGET_ALREADY_SUBMITTED", "目标成本已提交审批，不可重复提交");
        }

        // 更新审批状态为审批中
        LambdaUpdateWrapper<CostTarget> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(CostTarget::getId, targetId)
                .set(CostTarget::getApprovalStatus, "APPROVING");
        costTargetMapper.update(null, updateWrapper);

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngineProvider.getObject().submit(userId, username, tenantId,
                "COST_TARGET",
                targetId,
                target.getVersionName() != null ? target.getVersionName() : target.getVersionNo(),
                target.getTotalTargetAmount(),
                target.getProjectId(),
                null, // contractId
                null, // businessSummary
                null, // variables
                null); // ccUserIds

        log.info("Submitted cost target {} for approval", targetId);
    }
}
