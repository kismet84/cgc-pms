package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.constant.TargetCostSubjectCatalog;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.mapper.CostTargetItemMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostTargetService {

    private static final BigDecimal TARGET_COST_RATE = new BigDecimal("0.850000");

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
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查看目标成本");
            wrapper.eq(CostTarget::getProjectId, projectId);
        } else {
            List<Long> accessibleProjectIds = projectAccessChecker.accessibleProjectIds();
            if (accessibleProjectIds.isEmpty()) {
                wrapper.eq(CostTarget::getProjectId, -1L);
            } else {
                wrapper.in(CostTarget::getProjectId, accessibleProjectIds);
            }
        }
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
        projectAccessChecker.checkAccess(target.getProjectId(), "查看目标成本");
        return target;
    }

    public DefaultAllocation getDefaultAllocation(Long projectId) {
        PmProject project = requireWritableProject(projectId, "生成目标成本默认分配");
        BigDecimal contractAmount = money(project.getContractAmount());
        if (project.getContractAmount() == null || contractAmount.signum() <= 0) {
            throw new BusinessException("PROJECT_CONTRACT_AMOUNT_INVALID", "项目合同金额必须大于0才能生成目标成本");
        }
        List<TargetSubject> subjects = getTargetSubjects(false);
        BigDecimal total = money(contractAmount.multiply(TARGET_COST_RATE));
        List<DefaultAllocationItem> items = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO.setScale(2);
        for (TargetSubject subject : subjects) {
            BigDecimal amount = money(total.multiply(subject.ratio()).divide(new BigDecimal("100")));
            allocated = allocated.add(amount);
            items.add(new DefaultAllocationItem(String.valueOf(subject.id()), subject.code(), subject.name(), subject.type(),
                    subject.ratio(), amount, BigDecimal.ZERO.setScale(2), amount,
                    project.getProjectManagerId() == null ? null : String.valueOf(project.getProjectManagerId())));
        }
        BigDecimal residual = total.subtract(allocated);
        for (int i = 0; residual.signum() != 0 && i < items.size(); i++) {
            DefaultAllocationItem item = items.get(i);
            if ("5401.03.02".equals(item.subjectCode())) {
                BigDecimal adjusted = item.targetAmount().add(residual);
                items.set(i, new DefaultAllocationItem(item.costSubjectId(), item.subjectCode(), item.subjectName(),
                        item.subjectType(), item.defaultTargetRatio(), adjusted, item.bidCostAmount(), adjusted,
                        item.responsibleUserId()));
            }
        }
        return new DefaultAllocation(String.valueOf(project.getId()),
                project.getProjectManagerId() == null ? null : String.valueOf(project.getProjectManagerId()), contractAmount,
                TARGET_COST_RATE, total, items);
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
        applyContractSnapshot(target, project);
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
        updateHeader(target, true);
    }

    private void updateHeader(CostTarget target, boolean validateStoredItems) {
        CostTarget existing = getOwnedTarget(target.getId());
        if (existing == null) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        }
        int expectedVersion = requireVersion(target.getVersion());
        assertVersion(expectedVersion, existing.getVersion());

        if (!Set.of("DRAFT", "REJECTED").contains(existing.getApprovalStatus()) || Integer.valueOf(1).equals(existing.getIsActive())) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "仅草稿或驳回且未生效的目标成本可编辑");
        }
        requireWritableProject(existing.getProjectId(), "编辑目标成本");
        target.setTenantId(existing.getTenantId());
        target.setProjectId(existing.getProjectId());
        target.setApprovalStatus(existing.getApprovalStatus());
        target.setStatus(existing.getStatus());
        target.setIsActive(0);
        target.setApprovalInstanceId(existing.getApprovalInstanceId());
        target.setVersion(existing.getVersion());
        target.setSourceContractAmount(existing.getSourceContractAmount());
        target.setTargetCostRate(existing.getTargetCostRate());
        if (existing.getSourceContractAmount() != null) {
            target.setTotalTargetAmount(existing.getTotalTargetAmount());
            if (target.getTotalBidCostAmount() == null) {
                target.setTotalBidCostAmount(existing.getTotalBidCostAmount());
            }
            target.setTotalResponsibilityAmount(existing.getTotalTargetAmount());
        }
        normalizeHeaderAmounts(target);
        if (validateStoredItems) validateExistingItemsTotal(target.getId(), target);
        try {
            if (costTargetMapper.updateById(target) != 1) throw new BusinessException("COST_TARGET_CONCURRENT_UPDATE", "目标成本已被其他用户修改，请刷新后重试");
        } catch (DuplicateKeyException e) {
            throw new BusinessException("COST_TARGET_VERSION_DUPLICATE", "该项目目标成本版本号已存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(CostTarget target, List<CostTargetItem> items, Long projectManagerId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("COST_TARGET_NO_ITEMS", "项目成本预算至少需要一条科目明细");
        }
        requireEnabledProjectManager(projectManagerId);
        Long projectId = target.getProjectId();
        if (target.getId() != null) {
            CostTarget existing = getOwnedTarget(target.getId());
            if (existing == null) throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
            projectId = existing.getProjectId();
        }
        lockWritableProject(projectId, "保存项目成本预算");
        normalizeItems(items);
        target.setTotalTargetAmount(sum(items, CostTargetItem::getTargetAmount));
        target.setTotalBidCostAmount(sum(items, CostTargetItem::getBidCostAmount));
        target.setTotalResponsibilityAmount(sum(items, CostTargetItem::getResponsibilityAmount));
        if (target.getTotalResponsibilityAmount().compareTo(target.getTotalTargetAmount()) != 0) {
            throw new BusinessException("COST_TARGET_RESPONSIBILITY_MISMATCH", "责任预算必须完整分解且与目标成本总额一致");
        }
        if (target.getId() == null) {
            Long id = create(target);
            batchSaveItems(id, 0, items);
            syncProjectManager(projectId, projectManagerId);
            return id;
        }
        int expectedVersion = requireVersion(target.getVersion());
        updateHeader(target, false);
        batchSaveItems(target.getId(), expectedVersion + 1, items);
        syncProjectManager(projectId, projectManagerId);
        return target.getId();
    }

    // ── Delete ──

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer version) {
        CostTarget existing = getOwnedTargetForUpdate(id);
        projectAccessChecker.checkAccess(existing.getProjectId(), "删除目标成本");
        assertVersion(requireVersion(version), existing.getVersion());

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

        LambdaQueryWrapper<CostTarget> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(CostTarget::getId, id)
                .eq(CostTarget::getTenantId, UserContext.getCurrentTenantId())
                .eq(CostTarget::getVersion, existing.getVersion());
        if (costTargetMapper.delete(deleteWrapper) != 1) {
            throw new BusinessException("COST_TARGET_CONCURRENT_UPDATE", "目标成本已被其他用户修改，请刷新后重试");
        }
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
    public void activate(Long id, Integer version) {
        Long tenantId = UserContext.getCurrentTenantId();
        CostTarget snapshot = getOwnedTarget(id);
        if (snapshot == null) throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        lockWritableProject(snapshot.getProjectId(), "生效目标成本");
        CostTarget target = getOwnedTargetForUpdate(id);
        assertVersion(requireVersion(version), target.getVersion());

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
        LambdaUpdateWrapper<CostTarget> activateWrapper = new LambdaUpdateWrapper<>();
        activateWrapper.eq(CostTarget::getId, id)
                .eq(CostTarget::getTenantId, tenantId)
                .eq(CostTarget::getVersion, target.getVersion())
                .set(CostTarget::getIsActive, 1)
                .set(CostTarget::getStatus, "ACTIVE")
                .set(target.getEffectiveDate() == null, CostTarget::getEffectiveDate, java.time.LocalDate.now())
                .setSql("version = version + 1");
        if (costTargetMapper.update(null, activateWrapper) != 1) {
            throw new BusinessException("COST_TARGET_CONCURRENT_UPDATE", "目标成本已被其他用户修改，请刷新后重试");
        }
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
        projectAccessChecker.checkAccess(target.getProjectId(), "查看目标成本");

        LambdaQueryWrapper<CostTargetItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostTargetItem::getTargetId, targetId)
                .eq(CostTargetItem::getTenantId, UserContext.getCurrentTenantId());
        return costTargetItemMapper.selectList(wrapper);
    }

    /**
     * 批量保存目标成本明细项（先删后插）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveItems(Long targetId, Integer version, List<CostTargetItem> items) {
        CostTarget target = getOwnedTargetForUpdate(targetId);
        int expectedVersion = requireVersion(version);
        assertVersion(expectedVersion, target.getVersion());

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

        List<CostTargetItem> savedItems = costTargetItemMapper.selectList(new LambdaQueryWrapper<CostTargetItem>()
                .eq(CostTargetItem::getTargetId, targetId)
                .eq(CostTargetItem::getTenantId, UserContext.getCurrentTenantId()));
        validateItemsTotal(target, savedItems);

        LambdaUpdateWrapper<CostTarget> touchWrapper = new LambdaUpdateWrapper<>();
        touchWrapper.eq(CostTarget::getId, targetId)
                .eq(CostTarget::getTenantId, UserContext.getCurrentTenantId())
                .eq(CostTarget::getVersion, target.getVersion())
                .setSql("version = version + 1");
        if (costTargetMapper.update(null, touchWrapper) != 1) {
            throw new BusinessException("COST_TARGET_CONCURRENT_UPDATE", "目标成本已被其他用户修改，请刷新后重试");
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
        if (target.getSourceContractAmount() != null) {
            validateTargetSubjectSet(items);
            BigDecimal expected = money(target.getSourceContractAmount().multiply(TARGET_COST_RATE));
            if (target.getTargetCostRate() == null || target.getTargetCostRate().compareTo(TARGET_COST_RATE) != 0
                    || money(target.getTotalTargetAmount()).compareTo(expected) != 0) {
                throw new BusinessException("COST_TARGET_SNAPSHOT_INVALID", "目标成本合同金额与85%快照不一致");
            }
        }
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
    public void submitForApproval(Long targetId, Integer version) {
        Long tenantId = UserContext.getCurrentTenantId();

        CostTarget target = getOwnedTargetForUpdate(targetId);
        assertVersion(requireVersion(version), target.getVersion());

        if (!Set.of("DRAFT", "REJECTED").contains(target.getApprovalStatus())) {
            throw new BusinessException("COST_TARGET_ALREADY_SUBMITTED", "仅草稿或驳回状态可以提交审批");
        }
        requireWritableProject(target.getProjectId(), "提交目标成本审批");
        validateForSubmit(target);
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        WfInstance instance = "REJECTED".equals(target.getApprovalStatus()) && target.getApprovalInstanceId() != null
                ? workflowEngineProvider.getObject().resubmitCostTarget(target.getApprovalInstanceId(), userId, username)
                : workflowEngineProvider.getObject().submitCostTarget(userId, username, tenantId, "COST_TARGET", targetId,
                    target.getVersionName() != null ? target.getVersionName() : target.getVersionNo(), target.getTotalTargetAmount(),
                    target.getProjectId(), null, "投标成本→目标成本→责任预算", null, null);
        int updated = costTargetMapper.update(null, new LambdaUpdateWrapper<CostTarget>()
                .eq(CostTarget::getId, targetId).eq(CostTarget::getTenantId, tenantId).eq(CostTarget::getVersion, target.getVersion())
                .set(CostTarget::getApprovalStatus, "APPROVING")
                .set(CostTarget::getStatus, "APPROVING")
                .set(CostTarget::getApprovalInstanceId, instance.getId())
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException("COST_TARGET_CONCURRENT_UPDATE", "目标成本已被其他用户修改，请刷新后重试");
        }

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

    private void validateTargetSubjectSet(List<CostTargetItem> items) {
        Set<Long> required = getTargetSubjects(true).stream().map(TargetSubject::id).collect(java.util.stream.Collectors.toSet());
        Set<Long> actual = items == null ? Set.of() : items.stream()
                .map(CostTargetItem::getCostSubjectId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (items == null || items.size() != required.size() || !actual.equals(required)) {
            throw new BusinessException("COST_TARGET_SUBJECT_SET_INVALID", "目标成本必须完整包含固定10类科目且不得重复");
        }
    }

    private List<TargetSubject> getTargetSubjects(boolean allowMissingRatio) {
        List<TargetSubject> subjects = jdbc.query("""
                SELECT s.id, s.subject_code, s.subject_name, s.subject_type, s.default_target_ratio
                FROM cost_subject s
                JOIN cost_subject p ON p.id=s.parent_id AND p.tenant_id=s.tenant_id AND p.deleted_flag=0
                WHERE s.tenant_id=? AND p.subject_code=? AND s.deleted_flag=0 AND s.status='ENABLE'
                ORDER BY s.subject_code
                """, (rs, rowNum) -> new TargetSubject(rs.getLong("id"), rs.getString("subject_code"),
                rs.getString("subject_name"), rs.getString("subject_type"), rs.getBigDecimal("default_target_ratio")),
                UserContext.getCurrentTenantId(), TargetCostSubjectCatalog.PARENT_CODE);
        Set<String> codes = subjects.stream().map(TargetSubject::code).collect(java.util.stream.Collectors.toSet());
        if (subjects.size() != TargetCostSubjectCatalog.ITEMS.size() || !codes.equals(TargetCostSubjectCatalog.CODES)) {
            throw new BusinessException("TARGET_COST_SUBJECT_SET_INVALID", "项目目标成本固定10类科目不完整");
        }
        if (!allowMissingRatio) {
            BigDecimal sum = subjects.stream().map(TargetSubject::ratio)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (subjects.stream().anyMatch(subject -> subject.ratio() == null)
                    || sum.compareTo(new BigDecimal("100.0000")) != 0) {
                throw new BusinessException("TARGET_COST_RATIO_SUM_INVALID", "10类目标成本默认比例必须完整且合计100%");
            }
        }
        return subjects;
    }

    private PmProject requireWritableProject(Long projectId, String action) {
        PmProject project = projectMapper.selectById(projectId);
        projectAccessChecker.checkAccess(project, action);
        if (!Set.of(ProjectStatusConstants.PREPARING, ProjectStatusConstants.ACTIVE).contains(project.getStatus())) {
            throw new BusinessException("PROJECT_NOT_ACTIVE", "只有筹备或在建项目可以维护目标成本");
        }
        return project;
    }

    private PmProject lockWritableProject(Long projectId, String action) {
        PmProject project = projectMapper.selectOne(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getId, projectId)
                .eq(PmProject::getTenantId, UserContext.getCurrentTenantId())
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        projectAccessChecker.checkAccess(project, action);
        if (!Set.of(ProjectStatusConstants.PREPARING, ProjectStatusConstants.ACTIVE).contains(project.getStatus())) {
            throw new BusinessException("PROJECT_NOT_ACTIVE", "只有筹备或在建项目可以维护目标成本");
        }
        return project;
    }

    private void requireEnabledUser(Long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND tenant_id=? AND status='ENABLE' AND deleted_flag=0", Integer.class, userId, UserContext.getCurrentTenantId());
        if (count == null || count != 1) throw new BusinessException("COST_TARGET_RESPONSIBLE_INVALID", "责任人不存在、跨租户或已停用");
    }

    private void requireEnabledProjectManager(Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id=? AND tenant_id=? AND status='ENABLE' AND deleted_flag=0",
                Integer.class, userId, UserContext.getCurrentTenantId());
        if (count == null || count != 1) {
            throw new BusinessException("PROJECT_MANAGER_INVALID", "项目经理不存在、跨租户或已停用");
        }
    }

    private void syncProjectManager(Long projectId, Long projectManagerId) {
        Long tenantId = UserContext.getCurrentTenantId();
        Long operatorId = UserContext.getCurrentUserId();
        if (jdbc.update("""
                UPDATE pm_project
                SET project_manager_id=?, updated_by=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND tenant_id=? AND deleted_flag=0
                """, projectManagerId, operatorId, projectId, tenantId) != 1) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        int restored = jdbc.update("""
                UPDATE pm_project_member
                SET role_code='PM', position_name='项目经理', start_date=COALESCE(start_date,CURRENT_DATE),
                    end_date=NULL, status='ACTIVE', updated_by=?, updated_at=CURRENT_TIMESTAMP, deleted_flag=0
                WHERE tenant_id=? AND project_id=? AND user_id=?
                """, operatorId, tenantId, projectId, projectManagerId);
        if (restored == 0) {
            jdbc.update("""
                    INSERT INTO pm_project_member
                      (id,tenant_id,project_id,user_id,role_code,position_name,start_date,status,
                       created_by,created_at,updated_by,updated_at,deleted_flag)
                    VALUES (?, ?, ?, ?, 'PM', '项目经理', CURRENT_DATE, 'ACTIVE',
                            ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, 0)
                    """, IdWorker.getId(), tenantId, projectId, projectManagerId, operatorId, operatorId);
        }
        Integer memberCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pm_project_member
                WHERE tenant_id=? AND project_id=? AND user_id=? AND role_code='PM'
                  AND status='ACTIVE' AND deleted_flag=0
                """, Integer.class, tenantId, projectId, projectManagerId);
        if (memberCount == null || memberCount != 1) {
            throw new BusinessException("PROJECT_MANAGER_SYNC_FAILED", "项目经理回填项目成员失败");
        }
    }

    private static void normalizeHeaderAmounts(CostTarget target) {
        target.setTotalTargetAmount(money(target.getTotalTargetAmount()));
        target.setTotalBidCostAmount(target.getTotalBidCostAmount() == null ? target.getTotalTargetAmount() : money(target.getTotalBidCostAmount()));
        target.setTotalResponsibilityAmount(target.getTotalResponsibilityAmount() == null ? target.getTotalTargetAmount() : money(target.getTotalResponsibilityAmount()));
        if (target.getTotalResponsibilityAmount().compareTo(target.getTotalTargetAmount()) != 0) throw new BusinessException("COST_TARGET_RESPONSIBILITY_MISMATCH", "责任预算总额必须等于目标成本总额");
    }

    private static void applyContractSnapshot(CostTarget target, PmProject project) {
        BigDecimal contractAmount = money(project.getContractAmount());
        if (project.getContractAmount() == null || contractAmount.signum() <= 0) {
            throw new BusinessException("PROJECT_CONTRACT_AMOUNT_INVALID", "项目合同金额必须大于0才能创建目标成本");
        }
        BigDecimal total = money(contractAmount.multiply(TARGET_COST_RATE));
        target.setSourceContractAmount(contractAmount);
        target.setTargetCostRate(TARGET_COST_RATE);
        target.setTotalTargetAmount(total);
        target.setTotalBidCostAmount(BigDecimal.ZERO.setScale(2));
        target.setTotalResponsibilityAmount(total);
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
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    private CostTarget getOwnedTarget(Long id) {
        CostTarget target = costTargetMapper.selectById(id);
        if (target == null || !target.getTenantId().equals(UserContext.getCurrentTenantId())) {
            return null;
        }
        return target;
    }

    private CostTarget getOwnedTargetForUpdate(Long id) {
        LambdaQueryWrapper<CostTarget> lockQw = new LambdaQueryWrapper<>();
        lockQw.eq(CostTarget::getId, id)
                .eq(CostTarget::getTenantId, UserContext.getCurrentTenantId())
                .last("FOR UPDATE"); // SQL-SAFETY: fixed-sql-fragment
        CostTarget target = costTargetMapper.selectOne(lockQw);
        if (target == null) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在");
        }
        return target;
    }

    private int requireVersion(Integer version) {
        if (version == null) {
            throw new BusinessException("COST_TARGET_VERSION_REQUIRED", "缺少最新版本，请刷新后重试");
        }
        return version;
    }

    private void assertVersion(Integer expectedVersion, Integer actualVersion) {
        if (!Objects.equals(expectedVersion, actualVersion)) {
            throw new BusinessException("COST_TARGET_CONCURRENT_UPDATE", "目标成本已被其他用户修改，请刷新后重试");
        }
    }

    private record TargetSubject(Long id, String code, String name, String type, BigDecimal ratio) {
    }

    public record DefaultAllocation(String projectId, String projectManagerId,
                                    @JsonSerialize(using = ToStringSerializer.class) BigDecimal sourceContractAmount,
                                    @JsonSerialize(using = ToStringSerializer.class) BigDecimal targetCostRate,
                                    @JsonSerialize(using = ToStringSerializer.class) BigDecimal totalTargetAmount,
                                    List<DefaultAllocationItem> items) {
    }

    public record DefaultAllocationItem(String costSubjectId, String subjectCode, String subjectName,
                                        String subjectType,
                                        @JsonSerialize(using = ToStringSerializer.class) BigDecimal defaultTargetRatio,
                                        @JsonSerialize(using = ToStringSerializer.class) BigDecimal targetAmount,
                                        @JsonSerialize(using = ToStringSerializer.class) BigDecimal bidCostAmount,
                                        @JsonSerialize(using = ToStringSerializer.class) BigDecimal responsibilityAmount,
                                        String responsibleUserId) {
    }
}
