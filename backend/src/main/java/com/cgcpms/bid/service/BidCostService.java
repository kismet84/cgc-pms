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

    public IPage<BidCost> getPage(long pageNo, long pageSize, String bidStatus, String keyword) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<BidCost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BidCost::getTenantId, tenantId);
        if (bidStatus != null && !bidStatus.isEmpty()) wrapper.eq(BidCost::getBidStatus, bidStatus);
        if (keyword != null && !keyword.isEmpty()) wrapper.like(BidCost::getBidProjectName, keyword);
        wrapper.orderByDesc(BidCost::getCreatedAt);
        return mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    public BidCost getById(Long id) {
        return requireExisting(id);
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
        if (!"BIDDING".equals(existing.getBidStatus())) {
            throw new BusinessException("BID_STATUS_NOT_EDITABLE", "仅投标中状态可编辑");
        }
        mapper.updateById(bid);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BidCost existing = requireExisting(id);
        if (!"BIDDING".equals(existing.getBidStatus())) {
            throw new BusinessException("BID_STATUS_NOT_DELETABLE", "仅投标中状态可删除");
        }
        mapper.deleteById(id);
    }

    /**
     * 标记为中标：关联项目 → 费用结转 → 刷新汇总。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsWon(Long bidCostId, Long projectId) {
        BidCost bid = requireExisting(bidCostId);
        if (!"BIDDING".equals(bid.getBidStatus())) {
            throw new BusinessException("BID_STATUS_INVALID", "仅投标中状态可标记为中标");
        }
        PmProject project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        // 项目必须属于当前租户
        if (!Objects.equals(project.getTenantId(), UserContext.getCurrentTenantId()))
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        projectAccessChecker.checkAccess(project, "关联中标项目");

        bid.setProjectId(projectId);
        bid.setBidStatus("WON");
        mapper.updateById(bid);

        // 原投标成本事实保持不变；V2 由独立、审批通过的目标成本转入事实承担项目归集。
        log.info("投标项目中标，等待目标成本转入 bidCostId={} projectId={}", bidCostId, projectId);
    }

    /**
     * 标记为未中标：费用冲销 → cost_status = WRITE_OFF。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsLost(Long bidCostId) {
        BidCost bid = requireExisting(bidCostId);
        if (!"BIDDING".equals(bid.getBidStatus())) {
            throw new BusinessException("BID_STATUS_INVALID", "仅投标中状态可标记为未中标");
        }

        bid.setBidStatus("LOST");
        mapper.updateById(bid);

        costItemMapper.update(null, new LambdaUpdateWrapper<CostItem>()
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
}
