package com.cgcpms.bid.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 招投标前期费用服务。
 * <p>
 * 核心流程：投标中录入费用 -> 中标/未中标 -> 费用结转/冲销。
 * 金额统一由 cost_item 聚合，bid_cost 仅存状态和项目关联。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidCostService {

    private final BidCostMapper mapper;
    private final CostItemMapper costItemMapper;
    private final PmProjectMapper projectMapper;
    private final ProjectAccessChecker projectAccessChecker;

    public IPage<BidCost> getPage(long pageNo, long pageSize, String bidStatus, String keyword,
                                  Long projectId, LocalDate startDate, LocalDate endDate) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<BidCost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BidCost::getTenantId, tenantId);
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查询投标成本");
            wrapper.eq(BidCost::getProjectId, projectId);
        } else {
            List<Long> accessibleProjectIds = projectAccessChecker.accessibleProjectIds();
            wrapper.and(w -> {
                w.isNull(BidCost::getProjectId);
                if (!accessibleProjectIds.isEmpty()) {
                    w.or().in(BidCost::getProjectId, accessibleProjectIds);
                }
            });
        }
        if (bidStatus != null && !bidStatus.isEmpty()) wrapper.eq(BidCost::getBidStatus, bidStatus);
        if (keyword != null && !keyword.isEmpty()) wrapper.like(BidCost::getBidProjectName, keyword);
        if (startDate != null) wrapper.ge(BidCost::getCreatedAt, startDate.atStartOfDay());
        if (endDate != null) wrapper.lt(BidCost::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        wrapper.orderByDesc(BidCost::getCreatedAt);
        return mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    public BidCost getById(Long id) {
        BidCost bid = requireExisting(id);
        ensureBoundProjectVisible(bid, "查看投标成本");
        return bid;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(BidCost bid) {
        bid.setId(null);
        bid.setTenantId(UserContext.getCurrentTenantId());
        bid.setProjectId(null);
        bid.setBidStatus("BIDDING");
        mapper.insert(bid);
        return bid.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(BidCost bid) {
        BidCost existing = requireExisting(bid.getId());
        ensureBoundProjectVisible(existing, "编辑投标成本");
        if (!"BIDDING".equals(existing.getBidStatus())) {
            throw new BusinessException("BID_STATUS_NOT_EDITABLE", "仅投标中状态可编辑");
        }
        int updated = mapper.update(null, new LambdaUpdateWrapper<BidCost>()
                .eq(BidCost::getId, bid.getId())
                .eq(BidCost::getTenantId, UserContext.getCurrentTenantId())
                .eq(BidCost::getBidStatus, "BIDDING")
                .set(BidCost::getBidProjectName, bid.getBidProjectName())
                .set(BidCost::getRemark, bid.getRemark()));
        if (updated != 1)
            throw new BusinessException("BID_CONCURRENT_STATE_CHANGE", "投标状态已变化，请刷新后重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BidCost existing = requireExisting(id);
        ensureBoundProjectVisible(existing, "删除投标成本");
        if (!"BIDDING".equals(existing.getBidStatus())) {
            throw new BusinessException("BID_STATUS_NOT_DELETABLE", "仅投标中状态可删除");
        }
        int deleted = mapper.delete(new LambdaQueryWrapper<BidCost>()
                .eq(BidCost::getId, id)
                .eq(BidCost::getTenantId, UserContext.getCurrentTenantId())
                .eq(BidCost::getBidStatus, "BIDDING"));
        if (deleted != 1)
            throw new BusinessException("BID_CONCURRENT_STATE_CHANGE", "投标状态已变化，请刷新后重试");
    }

    /**
     * 标记为中标：关联项目 → 费用结转 → 刷新汇总。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsWon(Long bidCostId, Long projectId) {
        BidCost bid = requireExisting(bidCostId);
        ensureBoundProjectVisible(bid, "标记投标中标");
        if (!"BIDDING".equals(bid.getBidStatus())) {
            throw new BusinessException("BID_STATUS_INVALID", "仅投标中状态可标记为中标");
        }
        PmProject project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        // 项目必须属于当前租户
        if (!Objects.equals(project.getTenantId(), UserContext.getCurrentTenantId()))
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        projectAccessChecker.checkAccess(project, "关联中标项目");

        int updated = mapper.update(null, new LambdaUpdateWrapper<BidCost>()
                .eq(BidCost::getId, bidCostId)
                .eq(BidCost::getTenantId, UserContext.getCurrentTenantId())
                .eq(BidCost::getBidStatus, "BIDDING")
                .isNull(BidCost::getProjectId)
                .set(BidCost::getProjectId, projectId)
                .set(BidCost::getBidStatus, "WON"));
        if (updated != 1)
            throw new BusinessException("BID_CONCURRENT_STATE_CHANGE", "投标状态已变化，请刷新后重试");

        // 原投标成本事实保持不变；V2 由独立、审批通过的目标成本转入事实承担项目归集。
        log.info("投标项目中标，等待目标成本转入 bidCostId={} projectId={}", bidCostId, projectId);
    }

    /**
     * 标记为未中标：费用冲销 → cost_status = WRITE_OFF。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsLost(Long bidCostId) {
        BidCost bid = requireExisting(bidCostId);
        ensureBoundProjectVisible(bid, "标记投标未中标");
        if (!"BIDDING".equals(bid.getBidStatus())) {
            throw new BusinessException("BID_STATUS_INVALID", "仅投标中状态可标记为未中标");
        }

        int updated = mapper.update(null, new LambdaUpdateWrapper<BidCost>()
                .eq(BidCost::getId, bidCostId)
                .eq(BidCost::getTenantId, UserContext.getCurrentTenantId())
                .eq(BidCost::getBidStatus, "BIDDING")
                .isNull(BidCost::getProjectId)
                .set(BidCost::getBidStatus, "LOST"));
        if (updated != 1)
            throw new BusinessException("BID_CONCURRENT_STATE_CHANGE", "投标状态已变化，请刷新后重试");

        costItemMapper.update(null, new LambdaUpdateWrapper<CostItem>()
                .eq(CostItem::getTenantId, UserContext.getCurrentTenantId())
                .eq(CostItem::getSourceType, "BID_COST")
                .eq(CostItem::getSourceId, bidCostId)
                .set(CostItem::getCostStatus, "WRITE_OFF"));

        log.info("投标项目未中标 bidCostId={}", bidCostId);
    }

    private BidCost requireExisting(Long id) {
        BidCost bid = mapper.selectById(id);
        if (bid == null) throw new BusinessException("BID_COST_NOT_FOUND", "投标项目不存在");
        if (!Objects.equals(bid.getTenantId(), UserContext.getCurrentTenantId()))
            throw new BusinessException("BID_COST_NOT_FOUND", "投标项目不存在");
        return bid;
    }

    private void ensureBoundProjectVisible(BidCost bid, String action) {
        if (bid.getProjectId() != null) {
            projectAccessChecker.checkAccess(bid.getProjectId(), action);
        }
    }
}
