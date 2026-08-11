package com.cgcpms.bid.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.bid.dto.BidOwnerOption;
import com.cgcpms.bid.dto.BidCostOption;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class BidCostService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;
    private static final Set<String> STATUSES = Set.of(
            "PREPARING", "SUBMITTED", "EVALUATING", "WON", "LOST", "CLOSED",
            "WITHDRAWN", "TERMINATED");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PREPARING", Set.of("SUBMITTED", "WITHDRAWN", "TERMINATED"),
            "SUBMITTED", Set.of("EVALUATING", "WITHDRAWN", "TERMINATED"),
            "EVALUATING", Set.of("WON", "LOST"),
            "WON", Set.of("CLOSED"),
            "LOST", Set.of("CLOSED"),
            "WITHDRAWN", Set.of("CLOSED"),
            "TERMINATED", Set.of("CLOSED"));

    private final BidCostMapper mapper;
    private final CostItemMapper costItemMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final CodeGenerationService codeGenerationService;
    private final BidDocumentVersionService documentService;
    private final Optional<BidAwardProjectCreator> awardProjectCreator;

    public BidCostService(BidCostMapper mapper,
                          CostItemMapper costItemMapper,
                          ProjectAccessChecker projectAccessChecker,
                          CodeGenerationService codeGenerationService,
                          BidDocumentVersionService documentService,
                          Optional<BidAwardProjectCreator> awardProjectCreator) {
        this.mapper = mapper;
        this.costItemMapper = costItemMapper;
        this.projectAccessChecker = projectAccessChecker;
        this.codeGenerationService = codeGenerationService;
        this.documentService = documentService;
        this.awardProjectCreator = awardProjectCreator;
    }

    public IPage<BidCost> getPage(long pageNo, long pageSize, String bidStatus, String keyword,
                                  Long projectId, LocalDate startDate, LocalDate endDate) {
        return getPage(pageNo, pageSize, bidStatus, null, keyword, projectId, null,
                null, null, startDate, endDate);
    }

    public IPage<BidCost> getPage(long pageNo, long pageSize, String bidStatus, String result,
                                  String keyword, Long projectId, Long ownerId,
                                  LocalDateTime deadlineFrom, LocalDateTime deadlineTo,
                                  LocalDate createdFrom, LocalDate createdTo) {
        Long tenantId = tenant();
        LambdaQueryWrapper<BidCost> wrapper = new LambdaQueryWrapper<BidCost>()
                .eq(BidCost::getTenantId, tenantId);
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查询投标记录");
            wrapper.eq(BidCost::getProjectId, projectId);
        } else {
            List<Long> accessibleProjectIds = projectAccessChecker.accessibleProjectIds();
            wrapper.and(w -> {
                w.isNull(BidCost::getProjectId);
                if (!accessibleProjectIds.isEmpty()) w.or().in(BidCost::getProjectId, accessibleProjectIds);
            });
        }
        String normalizedStatus = normalizeStatus(bidStatus);
        if (normalizedStatus != null) {
            if ("PREPARING".equals(normalizedStatus)) wrapper.in(BidCost::getBidStatus, "PREPARING", "BIDDING");
            else wrapper.eq(BidCost::getBidStatus, normalizedStatus);
        }
        if (result != null && !result.isBlank()) {
            String normalizedResult = result.trim().toUpperCase();
            if (!Set.of("WON", "LOST").contains(normalizedResult)) {
                throw new BusinessException("BID_RESULT_INVALID", "投标结果筛选值不合法");
            }
            wrapper.eq(BidCost::getBidStatus, normalizedResult);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(BidCost::getBidCode, keyword.trim())
                    .or().like(BidCost::getBidProjectName, keyword.trim())
                    .or().like(BidCost::getBidSectionName, keyword.trim())
                    .or().like(BidCost::getTendereeName, keyword.trim())
                    .or().like(BidCost::getAgencyName, keyword.trim()));
        }
        if (ownerId != null) wrapper.eq(BidCost::getOwnerId, ownerId);
        if (deadlineFrom != null) wrapper.ge(BidCost::getBidDeadlineAt, deadlineFrom);
        if (deadlineTo != null) wrapper.lt(BidCost::getBidDeadlineAt, deadlineTo);
        if (createdFrom != null) wrapper.ge(BidCost::getCreatedAt, createdFrom.atStartOfDay());
        if (createdTo != null) wrapper.lt(BidCost::getCreatedAt, createdTo.plusDays(1).atStartOfDay());
        wrapper.orderByDesc(BidCost::getUpdatedAt).orderByDesc(BidCost::getCreatedAt);
        IPage<BidCost> page = mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        if (page.getRecords().isEmpty()) return page;
        Map<Long, BidCost> stats = mapper.selectListStats(tenantId,
                        page.getRecords().stream().map(BidCost::getId).toList()).stream()
                .collect(Collectors.toMap(BidCost::getId, Function.identity()));
        page.getRecords().forEach(bid -> {
            BidCost row = stats.get(bid.getId());
            if (row != null) {
                bid.setOwnerName(row.getOwnerName());
                bid.setBidExpense(row.getBidExpense());
            }
        });
        return page;
    }

    public BidCost getById(Long id) {
        BidCost bid = requireExisting(id);
        ensureBoundProjectVisible(bid, "查看投标记录");
        return bid;
    }

    public List<BidOwnerOption> listOwnerOptions() {
        return mapper.selectOwnerOptions(tenant());
    }

    public List<BidCostOption> listCostOptions() {
        return mapper.selectCostOptions(tenant(), projectAccessChecker.accessibleProjectIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(BidCost bid) {
        Long tenantId = tenant();
        requireEligibleOwner(bid.getOwnerId());
        bid.setTenantId(tenantId);
        bid.setProjectId(null);
        bid.setBidStatus("PREPARING");
        bid.setResultAt(null);
        bid.setResultReason(null);
        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            bid.setId(null);
            bid.setBidCode(codeGenerationService.nextCode(
                    mapper, BidCost::getBidCode, "BID-", tenantId, true, attempt));
            try {
                mapper.insert(bid);
                return bid.getId();
            } catch (DuplicateKeyException ignored) {
                log.info("投标编号冲突，重试生成 bidCode={}", bid.getBidCode());
            }
        }
        throw new BusinessException("BID_COST_CODE_CONFLICT", "投标编号生成冲突，请重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(BidCost command) {
        BidCost existing = requireExisting(command.getId());
        ensureBoundProjectVisible(existing, "编辑投标记录");
        if (!Objects.equals(existing.getOwnerId(), command.getOwnerId())) {
            requireEligibleOwner(command.getOwnerId());
        }
        String current = normalizeStatus(existing.getBidStatus());
        if (!Set.of("PREPARING", "SUBMITTED", "EVALUATING").contains(current)) {
            throw new BusinessException("BID_STATUS_NOT_EDITABLE", "投标结果登记后不可编辑");
        }
        LambdaUpdateWrapper<BidCost> update = identityAndStatus(command.getId(), current)
                .set(BidCost::getBidProjectName, command.getBidProjectName())
                .set(BidCost::getBidSectionName, command.getBidSectionName())
                .set(BidCost::getTendereeName, command.getTendereeName())
                .set(BidCost::getAgencyName, command.getAgencyName())
                .set(BidCost::getProjectLocation, command.getProjectLocation())
                .set(BidCost::getTenderMethod, command.getTenderMethod())
                .set(BidCost::getSourcePlatform, command.getSourcePlatform())
                .set(BidCost::getExternalBidNo, command.getExternalBidNo())
                .set(BidCost::getSourceUrl, command.getSourceUrl())
                .set(BidCost::getOwnerId, command.getOwnerId())
                .set(BidCost::getDocumentReceivedDate, command.getDocumentReceivedDate())
                .set(BidCost::getBidDeadlineAt, command.getBidDeadlineAt())
                .set(BidCost::getOpeningAt, command.getOpeningAt())
                .set(BidCost::getBidValidUntil, command.getBidValidUntil())
                .set(BidCost::getPlannedStartDate, command.getPlannedStartDate())
                .set(BidCost::getPlannedEndDate, command.getPlannedEndDate())
                .set(BidCost::getCeilingPrice, command.getCeilingPrice())
                .set(BidCost::getFinalBidPrice, command.getFinalBidPrice())
                .set(BidCost::getRemark, command.getRemark());
        requireStateCas(mapper.update(null, update));
    }

    private void requireEligibleOwner(Long ownerId) {
        if (ownerId == null) return;
        boolean eligible = mapper.selectOwnerOptions(tenant()).stream()
                .anyMatch(option -> Objects.equals(option.ownerId(), ownerId));
        if (!eligible) {
            throw new BusinessException("BID_OWNER_INVALID", "投标负责人不存在、已停用或无投标维护权限");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BidCost existing = requireExisting(id);
        ensureBoundProjectVisible(existing, "删除投标记录");
        String current = normalizeStatus(existing.getBidStatus());
        if (!"PREPARING".equals(current)) {
            throw new BusinessException("BID_STATUS_NOT_DELETABLE", "仅准备中状态可删除");
        }
        if (mapper.countDocumentVersions(tenant(), id) > 0 || mapper.countCashEntries(tenant(), id) > 0) {
            throw new BusinessException("BID_COST_HAS_FACTS", "已有文件版本或现金流水的投标记录只能关闭");
        }
        LambdaQueryWrapper<BidCost> query = new LambdaQueryWrapper<BidCost>()
                .eq(BidCost::getId, id).eq(BidCost::getTenantId, tenant());
        if ("PREPARING".equals(current)) query.in(BidCost::getBidStatus, "PREPARING", "BIDDING");
        requireStateCas(mapper.delete(query));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long changeStatus(Long id, String expectedStatus, String targetStatus, String reason) {
        return changeStatus(id, expectedStatus, targetStatus, reason, false);
    }

    @EventListener
    public void advanceStatus(BidDocumentVersionService.BidDocumentFinalizedEvent event) {
        BidCost bid = requireExisting(event.bidCostId());
        String current = normalizeStatus(bid.getBidStatus());
        if ("PREPARING".equals(current) && documentService.hasCurrentFinalGroup(bid.getId(), "TENDER")) {
            changeStatus(bid.getId(), current, "SUBMITTED", null);
            current = "SUBMITTED";
        }
        if ("SUBMITTED".equals(current) && documentService.hasSubmittedFinalBid(bid.getId())) {
            changeStatus(bid.getId(), current, "EVALUATING", null);
            current = "EVALUATING";
        }
        if (!"EVALUATING".equals(current)) return;
        if (documentService.hasCurrentFinal(bid.getId(), "RESULT", "AWARD_NOTICE")) {
            changeStatus(bid.getId(), current, "WON", null);
        } else if (documentService.hasCurrentFinal(bid.getId(), "RESULT", "LOSS_NOTICE")) {
            changeStatus(bid.getId(), current, "LOST", "上传未中标通知");
        }
    }

    private Long changeStatus(Long id, String expectedStatus, String targetStatus, String reason,
                              boolean legacyResultEntry) {
        BidCost bid = requireExisting(id);
        ensureBoundProjectVisible(bid, "变更投标状态");
        String current = normalizeStatus(bid.getBidStatus());
        String expected = normalizeStatus(expectedStatus);
        String target = normalizeStatus(targetStatus);
        if (!STATUSES.contains(target)) throw new BusinessException("BID_STATUS_INVALID", "目标状态不合法");
        if (!Objects.equals(current, expected)) {
            throw new BusinessException("BID_CONCURRENT_STATE_CHANGE", "投标状态已变化，请刷新后重试");
        }
        if (Objects.equals(current, target) && "WON".equals(target) && bid.getProjectId() != null) {
            return bid.getProjectId();
        }
        boolean legacyPreparingResult = legacyResultEntry && "PREPARING".equals(current)
                && Set.of("WON", "LOST").contains(target);
        if (!legacyPreparingResult && !TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BusinessException("BID_STATUS_TRANSITION_INVALID", "不允许从" + current + "变更为" + target);
        }
        validateStatusGate(bid, target, reason);

        Long projectId = null;
        if ("WON".equals(target)) {
            BidAwardProjectCreator creator = awardProjectCreator.orElseThrow(() ->
                    new BusinessException("BID_PROJECT_CREATOR_UNAVAILABLE", "中标项目创建能力不可用"));
            projectId = creator.createOrGet(new BidAwardProjectCreator.BidAwardProjectCommand(
                    tenant(), bid.getId(), bid.getBidCode(), bid.getBidProjectName(), bid.getTendereeName(),
                    bid.getProjectLocation(), bid.getFinalBidPrice(), bid.getPlannedStartDate(),
                    bid.getPlannedEndDate()));
            if (projectId == null) throw new BusinessException("BID_PROJECT_CREATE_FAILED", "中标项目创建失败");
        }

        LambdaUpdateWrapper<BidCost> update = identityAndStatus(id, current)
                .set(BidCost::getBidStatus, target)
                .set(BidCost::getResultReason, reason);
        if (Set.of("WON", "LOST").contains(target)) update.set(BidCost::getResultAt, LocalDate.now());
        if (projectId != null) update.set(BidCost::getProjectId, projectId);
        requireStateCas(mapper.update(null, update));

        if ("LOST".equals(target)) writeOffLegacyCost(id);
        return projectId;
    }

    /** Compatibility input; projectId is intentionally ignored because WON now creates the project. */
    @Deprecated
    public void markAsWon(Long bidCostId, Long projectId) {
        BidCost bid = requireExisting(bidCostId);
        changeStatus(bidCostId, normalizeStatus(bid.getBidStatus()), "WON", null, true);
    }

    @Deprecated
    public void markAsLost(Long bidCostId) {
        BidCost bid = requireExisting(bidCostId);
        changeStatus(bidCostId, normalizeStatus(bid.getBidStatus()), "LOST", "兼容入口登记未中标", true);
    }

    private void validateStatusGate(BidCost bid, String target, String reason) {
        if ("SUBMITTED".equals(target) && !documentService.hasCurrentFinalGroup(bid.getId(), "TENDER")) {
            throw new BusinessException("BID_FINAL_TENDER_REQUIRED", "进入投标阶段前必须存在当前有效的最终招标文件");
        }
        if ("EVALUATING".equals(target) && !documentService.hasSubmittedFinalBid(bid.getId())) {
            throw new BusinessException("BID_FINAL_SUBMISSION_REQUIRED", "进入评标阶段前必须存在带递交凭证的当前有效最终投标文件");
        }
        if ("WON".equals(target)
                && !documentService.hasCurrentFinal(bid.getId(), "RESULT", "AWARD_NOTICE")) {
            throw new BusinessException("BID_AWARD_NOTICE_REQUIRED", "中标前必须存在当前有效的最终中标通知书");
        }
        if ("WON".equals(target) && (isBlank(bid.getBidProjectName())
                || isBlank(bid.getTendereeName())
                || isBlank(bid.getProjectLocation())
                || bid.getFinalBidPrice() == null
                || bid.getFinalBidPrice().signum() <= 0
                || bid.getPlannedStartDate() == null
                || bid.getPlannedEndDate() == null
                || bid.getPlannedStartDate().isAfter(bid.getPlannedEndDate()))) {
            throw new BusinessException("BID_AWARD_PROJECT_INVALID",
                    "中标前必须填写工程名称、招标人、建设地点、有效计划日期和大于0的最终投标价");
        }
        if (Set.of("LOST", "WITHDRAWN", "TERMINATED").contains(target)
                && (reason == null || reason.isBlank())) {
            throw new BusinessException("BID_RESULT_REASON_REQUIRED", "该状态必须填写原因");
        }
        if (Set.of("LOST", "WITHDRAWN", "TERMINATED").contains(target)
                && !documentService.hasCurrentFinalResult(bid.getId())
                && (bid.getSourceUrl() == null || bid.getSourceUrl().isBlank())) {
            throw new BusinessException("BID_RESULT_EVIDENCE_REQUIRED", "该状态必须存在结果证据或可核对外部来源");
        }
    }

    private void writeOffLegacyCost(Long bidCostId) {
        costItemMapper.update(null, new LambdaUpdateWrapper<CostItem>()
                .eq(CostItem::getTenantId, tenant())
                .eq(CostItem::getSourceType, "BID_COST")
                .eq(CostItem::getSourceId, bidCostId)
                .set(CostItem::getCostStatus, "WRITE_OFF"));
    }

    private LambdaUpdateWrapper<BidCost> identityAndStatus(Long id, String current) {
        LambdaUpdateWrapper<BidCost> wrapper = new LambdaUpdateWrapper<BidCost>()
                .eq(BidCost::getId, id)
                .eq(BidCost::getTenantId, tenant());
        if ("PREPARING".equals(current)) wrapper.in(BidCost::getBidStatus, "PREPARING", "BIDDING");
        else wrapper.eq(BidCost::getBidStatus, current);
        return wrapper;
    }

    private BidCost requireExisting(Long id) {
        BidCost bid = mapper.selectById(id);
        if (bid == null || !Objects.equals(bid.getTenantId(), tenant())) {
            throw new BusinessException("BID_COST_NOT_FOUND", "投标记录不存在");
        }
        return bid;
    }

    private void ensureBoundProjectVisible(BidCost bid, String action) {
        if (bid.getProjectId() != null) projectAccessChecker.checkAccess(bid.getProjectId(), action);
    }

    private void requireStateCas(int affected) {
        if (affected != 1) {
            throw new BusinessException("BID_CONCURRENT_STATE_CHANGE", "投标状态已变化，请刷新后重试");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        return "BIDDING".equals(normalized) ? "PREPARING" : normalized;
    }

    private Long tenant() {
        return UserContext.getCurrentTenantId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
