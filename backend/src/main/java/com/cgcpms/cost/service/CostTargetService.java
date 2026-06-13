package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostTargetService {

    private final CostTargetMapper costTargetMapper;
    private final CostSummaryMapper costSummaryMapper;

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

    @Transactional
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

    @Transactional
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

        costTargetMapper.updateById(target);
    }

    // ── Delete ──

    @Transactional
    public void delete(Long id) {
        CostTarget existing = costTargetMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
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
     */
    @Transactional
    public void activate(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();

        // SELECT FOR UPDATE 锁定目标行，防止并发
        LambdaQueryWrapper<CostTarget> lockQw = new LambdaQueryWrapper<>();
        lockQw.eq(CostTarget::getId, id)
                .eq(CostTarget::getTenantId, tenantId)
                .last("FOR UPDATE");
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
}
