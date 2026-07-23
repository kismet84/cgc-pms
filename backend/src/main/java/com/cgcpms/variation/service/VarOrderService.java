package com.cgcpms.variation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.service.BusinessMatterRegistryService;
import com.cgcpms.contract.service.CtContractChangeService;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.entity.VarOrderItem;
import com.cgcpms.variation.mapper.VarOrderItemMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.variation.vo.VarOrderItemVO;
import com.cgcpms.variation.vo.VarOrderVO;
import com.cgcpms.variation.dto.VariationClaimModels.OwnerReviewLine;
import com.cgcpms.variation.dto.VariationClaimModels.OwnerReviewRequest;
import com.cgcpms.variation.dto.VariationClaimModels.OwnerSubmissionRequest;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VarOrderService {

    private final VarOrderMapper varOrderMapper;
    private final VarOrderItemMapper varOrderItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final WorkflowEngine workflowEngine;
    private final ProjectAccessChecker projectAccessChecker;
    private final BusinessMatterRegistryService businessMatterRegistryService;
    private final CtContractChangeService ctContractChangeService;
    private final JdbcTemplate jdbcTemplate;

    public IPage<VarOrderVO> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                      Long partnerId, String varType, String direction, String varCode,
                                      LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<VarOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VarOrder::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查看变更签证");
            wrapper.eq(VarOrder::getProjectId, projectId);
        } else {
            List<Long> accessibleProjectIds = projectAccessChecker.accessibleProjectIds();
            if (accessibleProjectIds.isEmpty()) {
                wrapper.eq(VarOrder::getProjectId, -1L);
            } else {
                wrapper.in(VarOrder::getProjectId, accessibleProjectIds);
            }
        }
        if (contractId != null) wrapper.eq(VarOrder::getContractId, contractId);
        if (partnerId != null) wrapper.eq(VarOrder::getPartnerId, partnerId);
        if (StringUtils.hasText(varType)) wrapper.eq(VarOrder::getVarType, varType);
        if (StringUtils.hasText(direction)) wrapper.eq(VarOrder::getDirection, direction);
        if (StringUtils.hasText(varCode)) wrapper.like(VarOrder::getVarCode, varCode);
        if (startDate != null) wrapper.ge(VarOrder::getEventDate, startDate);
        if (endDate != null) wrapper.le(VarOrder::getEventDate, endDate);
        wrapper.orderByDesc(VarOrder::getCreatedAt);

        Page<VarOrder> page = varOrderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-prefetch related project/contract/partner names to avoid N+1
        List<VarOrder> records = page.getRecords();
        Set<Long> projectIds = records.stream()
                .map(VarOrder::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> contractIds = records.stream()
                .map(VarOrder::getContractId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream()
                .map(VarOrder::getPartnerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectByIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectByIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectByIds(partnerIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));

        return page.convert(t -> toVO(t, projectNames, contractNames, partnerNames));
    }

    public VarOrderVO getById(Long id) {
        VarOrder order = varOrderMapper.selectById(id);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(order.getProjectId(), "查看变更签证");

        VarOrderVO vo = toVO(order);

        // Load items
        List<VarOrderItem> items = varOrderItemMapper.selectList(
                new LambdaQueryWrapper<VarOrderItem>()
                        .eq(VarOrderItem::getVarOrderId, id));
        vo.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));
        vo.setOwnerSubmissions(ownerSubmissions(id));

        return vo;
    }

    public List<VarOrderVO> toVOList(List<VarOrder> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        NameMaps nameMaps = resolveNameMaps(records);
        return records.stream()
                .map(record -> toVO(record, nameMaps.projectNames(), nameMaps.contractNames(), nameMaps.partnerNames()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(VarOrder order) {
        normalizeDirection(order);
        applyClaimDefaults(order);
        validateDraftOrder(order);
        validateProjectAndContract(order.getProjectId(), order.getContractId(), "创建变更签证");
        validatePartner(order.getPartnerId());

        // Auto-generate var code: VO-yyyyMMdd-XXX（含软删除记录查询最大编号，避免 UK 冲突）
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "VO-" + today + "-";

        String lastCode = varOrderMapper.selectLastCodeByPrefix(prefix, UserContext.getCurrentTenantId());

        int seq = 1;
        if (lastCode != null && lastCode.startsWith(prefix)) {
            try {
                seq = Integer.parseInt(lastCode.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", lastCode, e);
            }
        }
        order.setVarCode(prefix + String.format("%03d", seq));

        // Default approval status
        if (order.getApprovalStatus() == null || order.getApprovalStatus().isBlank()) {
            order.setApprovalStatus("DRAFT");
        }

        // Default direction
        if (order.getDirection() == null || order.getDirection().isBlank()) {
            order.setDirection("COST");
        }

        order.setOwnerConfirmFlag(0);
        order.setApprovedAmount(BigDecimal.ZERO);
        order.setConfirmedAmount(BigDecimal.ZERO);
        order.setEstimatedCostAmount(BigDecimal.ZERO);
        order.setOwnerStatus("NOT_READY");
        order.setInternalApprovalInstanceId(null);
        order.setGeneratedContractChangeId(null);
        order.setCostGeneratedFlag(0);
        order.setVersion(0);

        // Default impactDays
        if (order.getImpactDays() == null) {
            order.setImpactDays(0);
        }

        order.setTenantId(UserContext.getCurrentTenantId());
        order.setBusinessMatterKey(businessMatterRegistryService.normalize(order.getBusinessMatterKey()));
        varOrderMapper.insert(order);
        businessMatterRegistryService.register(BusinessMatterRegistryService.SOURCE_VARIATION_ORDER,
                order.getId(), order.getProjectId(), order.getContractId(), order.getBusinessMatterKey());
        return order.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(VarOrder order) {
        VarOrder existing = varOrderMapper.selectById(order.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(existing.getProjectId(), "编辑变更签证");

        if (!Set.of("DRAFT", "REJECTED").contains(existing.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_IN_APPROVAL", "签证变更审批中或已审批，不可编辑");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");
        int expectedVersion = requiredVersion(order.getVersion());
        if (!java.util.Objects.equals(existing.getVersion(), expectedVersion))
            throw new BusinessException("VAR_ORDER_VERSION_CONFLICT", "签证已被其他人修改，请刷新后重试");

        order.setProjectId(existing.getProjectId());
        order.setContractId(existing.getContractId());
        normalizeDirection(order);
        applyClaimDefaults(order);
        validateDraftOrder(order);
        validateProjectAndContract(existing.getProjectId(), existing.getContractId(), "编辑变更签证");
        validatePartner(order.getPartnerId());

        if (order.getBusinessMatterKey() != null) {
            businessMatterRegistryService.replace(BusinessMatterRegistryService.SOURCE_VARIATION_ORDER,
                    existing.getId(), existing.getProjectId(), existing.getContractId(),
                    existing.getBusinessMatterKey(), order.getBusinessMatterKey());
            order.setBusinessMatterKey(businessMatterRegistryService.normalize(order.getBusinessMatterKey()));
        }
        int updated = varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                .eq(VarOrder::getId, existing.getId())
                .eq(VarOrder::getTenantId, existing.getTenantId())
                .eq(VarOrder::getVersion, expectedVersion)
                .in(VarOrder::getApprovalStatus, "DRAFT", "REJECTED")
                .set(VarOrder::getPartnerId, order.getPartnerId())
                .set(VarOrder::getVarName, order.getVarName())
                .set(VarOrder::getEventDate, order.getEventDate())
                .set(VarOrder::getClaimDeadline, order.getClaimDeadline())
                .set(VarOrder::getEventDescription, order.getEventDescription())
                .set(VarOrder::getCauseCategory, order.getCauseCategory())
                .set(VarOrder::getResponsibleParty, order.getResponsibleParty())
                .set(VarOrder::getBusinessMatterKey, order.getBusinessMatterKey())
                .set(VarOrder::getVarType, order.getVarType())
                .set(VarOrder::getDirection, order.getDirection())
                .set(VarOrder::getImpactDays, order.getImpactDays())
                .set(VarOrder::getRemark, order.getRemark())
                .set(VarOrder::getVersion, expectedVersion + 1));
        if (updated != 1)
            throw new BusinessException("VAR_ORDER_VERSION_CONFLICT", "签证已被其他人修改，请刷新后重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long varOrderId, List<VarOrderItem> items) {
        VarOrder current = requireOrder(varOrderId, "编辑变更签证明细");
        saveItems(varOrderId, items, current.getVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long varOrderId, List<VarOrderItem> items, Integer version) {
        // Verify order exists and belongs to tenant
        VarOrder order = varOrderMapper.selectById(varOrderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(order.getProjectId(), "编辑变更签证明细");

        if (!Set.of("DRAFT", "REJECTED").contains(order.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_IN_APPROVAL", "签证变更审批中或已审批，不可编辑");
        if (order.getCostGeneratedFlag() != null && order.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");
        int expectedVersion = requiredVersion(version);
        if (!java.util.Objects.equals(order.getVersion(), expectedVersion))
            throw new BusinessException("VAR_ORDER_VERSION_CONFLICT", "签证已被其他人修改，请刷新后重试");

        List<VarOrderItem> validItems = normalizeDraftItems(items);

        // Delete old items
        varOrderItemMapper.delete(new LambdaQueryWrapper<VarOrderItem>()
                .eq(VarOrderItem::getVarOrderId, varOrderId));

        // 双口径：amount 为内部预计成本，claimAmount 为对业主申报金额。
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalClaim = BigDecimal.ZERO;
        for (VarOrderItem item : validItems) {
            item.setVarOrderId(varOrderId);
            item.setTenantId(UserContext.getCurrentTenantId());
            item.setId(null);
            BigDecimal costPrice = nvl(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP);
            BigDecimal claimPrice = item.getClaimUnitPrice() == null ? costPrice
                    : item.getClaimUnitPrice().setScale(4, RoundingMode.HALF_UP);
            if (costPrice.signum() < 0 || claimPrice.signum() < 0)
                throw new BusinessException("VAR_ORDER_ITEM_PRICE_INVALID", "成本单价和申报单价不得小于0");
            item.setUnitPrice(costPrice);
            item.setClaimUnitPrice(claimPrice);
            item.setAmount(item.getQuantity().multiply(costPrice).setScale(2, RoundingMode.HALF_UP));
            item.setClaimAmount(item.getQuantity().multiply(claimPrice).setScale(2, RoundingMode.HALF_UP));
            if (item.getClaimAmount().signum() <= 0)
                throw new BusinessException("VAR_ORDER_ITEM_CLAIM_AMOUNT_INVALID", "申报明细金额必须大于0");
            totalCost = totalCost.add(item.getAmount());
            totalClaim = totalClaim.add(item.getClaimAmount());
        }
        Db.saveBatch(validItems, 50);

        // Update header reported amount
        int updated = varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                .eq(VarOrder::getId, varOrderId)
                .eq(VarOrder::getTenantId, order.getTenantId())
                .eq(VarOrder::getVersion, expectedVersion)
                .in(VarOrder::getApprovalStatus, "DRAFT", "REJECTED")
                .set(VarOrder::getEstimatedCostAmount, totalCost)
                .set(VarOrder::getReportedAmount, totalClaim)
                .set(VarOrder::getVersion, expectedVersion + 1));
        if (updated != 1)
            throw new BusinessException("VAR_ORDER_VERSION_CONFLICT", "签证已被其他人修改，请刷新后重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        VarOrder current = requireOrder(id, "删除变更签证");
        delete(id, current.getVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer version) {
        VarOrder existing = varOrderMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(existing.getProjectId(), "删除变更签证");

        if (!Set.of("DRAFT", "REJECTED").contains(existing.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_IN_APPROVAL", "签证变更审批中或已审批，不可删除");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可删除，请走冲销");

        int deleted = varOrderMapper.delete(new LambdaQueryWrapper<VarOrder>()
                .eq(VarOrder::getId, id)
                .eq(VarOrder::getTenantId, existing.getTenantId())
                .eq(VarOrder::getVersion, requiredVersion(version))
                .in(VarOrder::getApprovalStatus, "DRAFT", "REJECTED"));
        if (deleted != 1)
            throw new BusinessException("VAR_ORDER_VERSION_CONFLICT", "签证已被其他人修改，请刷新后重试");
        businessMatterRegistryService.release(BusinessMatterRegistryService.SOURCE_VARIATION_ORDER,
                id, "现场签证草稿删除");
    }

    /**
     * 提交签证变更审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long varOrderId) {
        VarOrder current = requireOrder(varOrderId, "提交变更签证审批");
        submitForApproval(varOrderId, current.getVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long varOrderId, Integer version) {
        VarOrder order = varOrderMapper.selectById(varOrderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(order.getProjectId(), "提交变更签证审批");

        if (!Set.of("DRAFT", "REJECTED").contains(order.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_ALREADY_SUBMITTED", "签证已提交审批，不可重复提交");

        validateSubmissionCompleteness(order);

        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        WfInstance instance = "REJECTED".equals(order.getApprovalStatus()) && order.getInternalApprovalInstanceId() != null
                ? workflowEngine.resubmit(order.getInternalApprovalInstanceId(), userId, username)
                : workflowEngine.submit(userId, username, tenantId,
                        "VAR_ORDER", varOrderId, order.getVarCode(), order.getReportedAmount(),
                        order.getProjectId(), order.getContractId(), null, null, null);

        LambdaUpdateWrapper<VarOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(VarOrder::getId, varOrderId)
                .eq(VarOrder::getTenantId, tenantId)
                .eq(VarOrder::getVersion, requiredVersion(version))
                .in(VarOrder::getApprovalStatus, "DRAFT", "REJECTED")
                .set(VarOrder::getApprovalStatus, "APPROVING")
                .set(VarOrder::getInternalApprovalInstanceId, instance.getId())
                .set(VarOrder::getOwnerStatus, "NOT_READY")
                .set(VarOrder::getVersion, requiredVersion(version) + 1);
        if (varOrderMapper.update(null, updateWrapper) != 1)
            throw new BusinessException("VAR_ORDER_CONCURRENT_SUBMIT", "签证状态已变化，请刷新后重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitToOwner(Long varOrderId, OwnerSubmissionRequest request) {
        VarOrder current = requireOrder(varOrderId, "提交业主申报");
        return submitToOwner(varOrderId, request, current.getVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitToOwner(Long varOrderId, OwnerSubmissionRequest request, Integer version) {
        VarOrder order = requireOrder(varOrderId, "提交业主申报");
        if (!"APPROVED".equals(order.getApprovalStatus()))
            throw new BusinessException("VARIATION_INTERNAL_APPROVAL_REQUIRED", "内部审批通过后才能向业主申报");
        if (!"INCOME".equals(normalizedDirection(order.getDirection())))
            throw new BusinessException("VARIATION_OWNER_DIRECTION_INVALID", "只有收入向变更签证可以向业主申报");
        if (!Set.of("INTERNAL_APPROVED", "OWNER_RETURNED").contains(order.getOwnerStatus()))
            throw new BusinessException("VARIATION_OWNER_STATUS_INVALID", "当前状态不允许提交业主申报");
        validateOwnerContract(order);
        List<VarOrderItem> items = loadItems(varOrderId);
        if (items.isEmpty()) throw new BusinessException("VAR_ORDER_ITEMS_REQUIRED", "变更签证缺少申报明细");

        Map<String, Object> last = latestOwnerSubmission(varOrderId);
        LocalDateTime after = last == null ? null : toLocalDateTime(last.get("submitted_at"));
        if (countDocument(varOrderId, "OWNER_SUBMISSION", after) == 0)
            throw new BusinessException("VARIATION_OWNER_SUBMISSION_ATTACHMENT_REQUIRED", "请先上传本版业主申报文件");

        int revision = last == null ? 1 : ((Number) last.get("revision_no")).intValue() + 1;
        long submissionId = IdWorker.getId();
        BigDecimal submittedAmount = items.stream().map(i -> nvl(i.getClaimAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (submittedAmount.signum() <= 0)
            throw new BusinessException("VARIATION_OWNER_SUBMITTED_AMOUNT_INVALID", "业主申报金额必须大于0");
        int expectedVersion = requiredVersion(version);
        int reserved = varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                .eq(VarOrder::getId, varOrderId)
                .eq(VarOrder::getTenantId, order.getTenantId())
                .eq(VarOrder::getVersion, expectedVersion)
                .in(VarOrder::getOwnerStatus, "INTERNAL_APPROVED", "OWNER_RETURNED")
                .set(VarOrder::getOwnerStatus, "OWNER_SUBMITTED")
                .set(VarOrder::getVersion, expectedVersion + 1));
        if (reserved != 1)
            throw new BusinessException("VARIATION_OWNER_CONCURRENT_SUBMIT", "业主申报状态已变化，请刷新后重试");
        String code = order.getVarCode() + "-R" + revision;
        jdbcTemplate.update("INSERT INTO variation_owner_submission " +
                        "(id,tenant_id,project_id,contract_id,var_order_id,revision_no,submission_code,external_document_no," +
                        "submitted_amount,status,submitted_at,submitted_by,confirmed_amount,created_by,remark) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,'SUBMITTED',?,?,0,?,?)",
                submissionId, order.getTenantId(), order.getProjectId(), order.getContractId(), order.getId(),
                revision, code, request.externalDocumentNo(), submittedAmount, request.submittedAt(),
                UserContext.getCurrentUserId(), UserContext.getCurrentUserId(), request.remark());
        for (VarOrderItem item : items) {
            jdbcTemplate.update("INSERT INTO variation_owner_submission_item " +
                            "(id,tenant_id,submission_id,var_order_item_id,item_name,unit,quantity,claimed_unit_price,claimed_amount,created_by) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    IdWorker.getId(), order.getTenantId(), submissionId, item.getId(), item.getItemName(), item.getUnit(),
                    item.getQuantity(), item.getClaimUnitPrice(), item.getClaimAmount(), UserContext.getCurrentUserId());
        }
        return ownerSubmission(submissionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewOwnerSubmission(Long varOrderId, Long submissionId, OwnerReviewRequest request) {
        VarOrder current = requireOrder(varOrderId, "登记业主核定");
        return reviewOwnerSubmission(varOrderId, submissionId, request, current.getVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewOwnerSubmission(Long varOrderId, Long submissionId,
                                                      OwnerReviewRequest request, Integer version) {
        VarOrder order = requireOrder(varOrderId, "登记业主核定");
        int expectedVersion = requiredVersion(version);
        if (!java.util.Objects.equals(order.getVersion(), expectedVersion))
            throw new BusinessException("VAR_ORDER_VERSION_CONFLICT", "签证已被其他人修改，请刷新后重试");
        Map<String, Object> submission = ownerSubmission(submissionId);
        if (!java.util.Objects.equals(((Number) submission.get("var_order_id")).longValue(), varOrderId))
            throw new BusinessException("VARIATION_OWNER_SUBMISSION_MISMATCH", "业主申报版本不属于当前签证");
        if (!"SUBMITTED".equals(submission.get("status")) || !"OWNER_SUBMITTED".equals(order.getOwnerStatus()))
            throw new BusinessException("VARIATION_OWNER_REVIEW_DUPLICATE", "该申报版本已处理或状态已变化");
        String conclusion = request.conclusion().trim().toUpperCase();
        if (!Set.of("CONFIRMED", "RETURNED").contains(conclusion))
            throw new BusinessException("VARIATION_OWNER_REVIEW_INVALID", "业主处理结论只能为 CONFIRMED 或 RETURNED");
        if (countDocument(varOrderId, "OWNER_CONFIRMATION", toLocalDateTime(submission.get("submitted_at"))) == 0)
            throw new BusinessException("VARIATION_OWNER_CONFIRMATION_ATTACHMENT_REQUIRED", "请先上传业主核定或退回文件");

        if ("RETURNED".equals(conclusion)) {
            if (!StringUtils.hasText(request.responseComment()))
                throw new BusinessException("VARIATION_OWNER_RETURN_REASON_REQUIRED", "业主退回时必须填写原因");
            updateSubmissionReview(submissionId, "RETURNED", request, BigDecimal.ZERO, null);
            int updated = varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                    .eq(VarOrder::getId, varOrderId)
                    .eq(VarOrder::getTenantId, order.getTenantId())
                    .eq(VarOrder::getVersion, expectedVersion)
                    .eq(VarOrder::getOwnerStatus, "OWNER_SUBMITTED")
                    .set(VarOrder::getOwnerStatus, "OWNER_RETURNED")
                    .set(VarOrder::getVersion, expectedVersion + 1));
            if (updated != 1)
                throw new BusinessException("VARIATION_OWNER_REVIEW_DUPLICATE", "该申报版本已被处理，请刷新后重试");
            return ownerSubmission(submissionId);
        }

        List<Map<String, Object>> snapshots = ownerSubmissionItems(submissionId);
        Map<Long, OwnerReviewLine> lines = new HashMap<>();
        if (request.items() != null) request.items().forEach(line -> lines.put(line.submissionItemId(), line));
        if (lines.size() != snapshots.size())
            throw new BusinessException("VARIATION_OWNER_REVIEW_LINES_INCOMPLETE", "必须逐项登记业主核定金额");
        BigDecimal confirmed = BigDecimal.ZERO;
        for (Map<String, Object> snapshot : snapshots) {
            long itemId = ((Number) snapshot.get("id")).longValue();
            OwnerReviewLine line = lines.get(itemId);
            if (line == null) throw new BusinessException("VARIATION_OWNER_REVIEW_LINES_INCOMPLETE", "核定明细不完整");
            BigDecimal claimed = toBigDecimal(snapshot.get("claimed_amount"));
            BigDecimal amount = line.confirmedAmount().setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() < 0 || amount.compareTo(claimed) > 0)
                throw new BusinessException("VARIATION_OWNER_CONFIRMED_AMOUNT_INVALID", "核定金额必须在0和申报金额之间");
            if (amount.compareTo(claimed) < 0 && !StringUtils.hasText(line.reductionReason()))
                throw new BusinessException("VARIATION_OWNER_REDUCTION_REASON_REQUIRED", "核减金额必须填写核减原因");
            jdbcTemplate.update("UPDATE variation_owner_submission_item SET confirmed_amount=?,reduction_reason=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                    amount, line.reductionReason(), UserContext.getCurrentUserId(), itemId, order.getTenantId());
            confirmed = confirmed.add(amount);
        }
        if (confirmed.signum() <= 0)
            throw new BusinessException("VARIATION_OWNER_CONFIRMED_AMOUNT_INVALID", "业主核定总金额必须大于0");
        updateSubmissionReview(submissionId, "CONFIRMED", request, confirmed, null);
        Long changeId = ctContractChangeService.createFromVariationAndSubmit(order, confirmed);
        jdbcTemplate.update("UPDATE variation_owner_submission SET status='CHANGE_PENDING',generated_contract_change_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                changeId, submissionId, order.getTenantId());
        int updated = varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>().eq(VarOrder::getId, varOrderId)
                .eq(VarOrder::getTenantId, order.getTenantId())
                .eq(VarOrder::getVersion, expectedVersion)
                .eq(VarOrder::getOwnerStatus, "OWNER_SUBMITTED")
                .set(VarOrder::getConfirmedAmount, confirmed).set(VarOrder::getOwnerConfirmFlag, 1)
                .set(VarOrder::getGeneratedContractChangeId, changeId).set(VarOrder::getOwnerStatus, "CHANGE_PENDING")
                .set(VarOrder::getVersion, expectedVersion + 1));
        if (updated != 1)
            throw new BusinessException("VARIATION_OWNER_REVIEW_DUPLICATE", "该申报版本已被处理，请刷新后重试");
        return ownerSubmission(submissionId);
    }

    public Map<String, Object> trace(Long varOrderId) {
        VarOrderVO order = getById(varOrderId);
        Long tenantId = UserContext.getCurrentTenantId();
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("variation", order);
        trace.put("attachments", jdbcTemplate.queryForList("SELECT id,document_type,file_name,created_at FROM sys_file WHERE tenant_id=? AND business_type='VARIATION' AND business_id=? AND deleted_flag=0 ORDER BY created_at", tenantId, varOrderId));
        trace.put("internalApproval", jdbcTemplate.queryForList("SELECT id,instance_status,started_at,ended_at FROM wf_instance WHERE tenant_id=? AND business_type='VAR_ORDER' AND business_id=? ORDER BY id", tenantId, varOrderId));
        trace.put("ownerSubmissions", ownerSubmissions(varOrderId));
        Long changeId = order.getGeneratedContractChangeId() == null ? null : Long.valueOf(order.getGeneratedContractChangeId());
        trace.put("contractChange", changeId == null ? List.of() : jdbcTemplate.queryForList("SELECT * FROM ct_contract_change WHERE tenant_id=? AND id=?", tenantId, changeId));
        trace.put("measurements", changeId == null ? List.of() : jdbcTemplate.queryForList("SELECT pm.* FROM production_measurement pm JOIN production_measurement_line pml ON pml.measurement_id=pm.id WHERE pm.tenant_id=? AND pml.source_type='CONTRACT_CHANGE' AND pml.contract_change_id=?", tenantId, changeId));
        trace.put("ownerMeasurementSubmissions", downstream(changeId,
                "SELECT DISTINCT oms.* FROM owner_measurement_submission oms JOIN production_measurement pm ON pm.id=oms.measurement_id JOIN production_measurement_line pml ON pml.measurement_id=pm.id WHERE oms.tenant_id=? AND pml.contract_change_id=? AND oms.deleted_flag=0 ORDER BY oms.id"));
        trace.put("ownerSettlements", downstream(changeId,
                "SELECT DISTINCT os.* FROM owner_settlement os JOIN production_measurement pm ON pm.id=os.production_measurement_id JOIN production_measurement_line pml ON pml.measurement_id=pm.id WHERE os.tenant_id=? AND pml.contract_change_id=? AND os.deleted_flag=0 ORDER BY os.id"));
        trace.put("receivables", downstream(changeId,
                "SELECT DISTINCT ar.* FROM account_receivable ar JOIN owner_settlement os ON os.id=ar.settlement_id JOIN production_measurement pm ON pm.id=os.production_measurement_id JOIN production_measurement_line pml ON pml.measurement_id=pm.id WHERE ar.tenant_id=? AND pml.contract_change_id=? AND ar.deleted_flag=0 ORDER BY ar.id"));
        trace.put("salesInvoices", downstream(changeId,
                "SELECT DISTINCT si.* FROM sales_invoice si JOIN sales_invoice_allocation sia ON sia.invoice_id=si.id JOIN account_receivable ar ON ar.id=sia.receivable_id JOIN owner_settlement os ON os.id=ar.settlement_id JOIN production_measurement pm ON pm.id=os.production_measurement_id JOIN production_measurement_line pml ON pml.measurement_id=pm.id WHERE si.tenant_id=? AND pml.contract_change_id=? AND si.deleted_flag=0 ORDER BY si.id"));
        trace.put("collections", downstream(changeId,
                "SELECT DISTINCT cr.* FROM collection_record cr JOIN collection_allocation ca ON ca.collection_id=cr.id JOIN account_receivable ar ON ar.id=ca.receivable_id JOIN owner_settlement os ON os.id=ar.settlement_id JOIN production_measurement pm ON pm.id=os.production_measurement_id JOIN production_measurement_line pml ON pml.measurement_id=pm.id WHERE cr.tenant_id=? AND pml.contract_change_id=? AND cr.deleted_flag=0 ORDER BY cr.id"));
        trace.put("cashJournal", downstream(changeId,
                "SELECT DISTINCT cj.* FROM cash_journal_entry cj JOIN collection_record cr ON cj.source_type='COLLECTION_RECORD' AND cj.source_id=cr.id JOIN collection_allocation ca ON ca.collection_id=cr.id JOIN account_receivable ar ON ar.id=ca.receivable_id JOIN owner_settlement os ON os.id=ar.settlement_id JOIN production_measurement pm ON pm.id=os.production_measurement_id JOIN production_measurement_line pml ON pml.measurement_id=pm.id WHERE cj.tenant_id=? AND pml.contract_change_id=? AND cj.deleted_flag=0 ORDER BY cj.id"));
        return trace;
    }

    private List<Map<String, Object>> downstream(Long changeId, String sql) {
        return changeId == null ? List.of() : jdbcTemplate.queryForList(sql,
                UserContext.getCurrentTenantId(), changeId);
    }

    private void applyClaimDefaults(VarOrder order) {
        if (order.getEventDate() == null) order.setEventDate(LocalDate.now());
        if (!StringUtils.hasText(order.getEventDescription())) order.setEventDescription(order.getVarName());
        if (!StringUtils.hasText(order.getCauseCategory())) order.setCauseCategory(order.getVarType());
    }

    private void normalizeDirection(VarOrder order) {
        order.setDirection(normalizedDirection(order.getDirection()));
    }

    private String normalizedDirection(String direction) {
        if (!StringUtils.hasText(direction)) return "COST";
        return "REVENUE".equalsIgnoreCase(direction) ? "INCOME" : direction.trim().toUpperCase();
    }

    private void validateSubmissionCompleteness(VarOrder order) {
        if (loadItems(order.getId()).isEmpty())
            throw new BusinessException("VAR_ORDER_ITEMS_REQUIRED", "请先保存变更签证明细");
        if (order.getReportedAmount() == null || order.getReportedAmount().signum() <= 0)
            throw new BusinessException("VAR_ORDER_AMOUNT_REQUIRED", "申报金额必须大于0");
        if (order.getEventDate() == null || !StringUtils.hasText(order.getEventDescription())
                || !StringUtils.hasText(order.getCauseCategory()))
            throw new BusinessException("VARIATION_EVENT_REQUIRED", "事件日期、事件说明和原因分类不能为空");
        if (order.getClaimDeadline() != null && order.getClaimDeadline().isBefore(order.getEventDate()))
            throw new BusinessException("VARIATION_CLAIM_DEADLINE_INVALID", "索赔截止日不得早于事件日期");
        if (order.getClaimDeadline() != null && LocalDate.now().isAfter(order.getClaimDeadline()))
            throw new BusinessException("VARIATION_CLAIM_DEADLINE_EXPIRED", "已超过合同约定的索赔申报截止日");
        if (countDocument(order.getId(), "SITE_EVIDENCE", null) == 0)
            throw new BusinessException("VARIATION_SITE_EVIDENCE_REQUIRED", "请上传现场证据后再提交审批");
        if ("INCOME".equals(normalizedDirection(order.getDirection()))) validateOwnerContract(order);
        PmProject project = pmProjectMapper.selectById(order.getProjectId());
        if (project == null || !Set.of("ACTIVE", "RUNNING", "IN_PROGRESS").contains(project.getStatus()))
            throw new BusinessException("VARIATION_PROJECT_NOT_ACTIVE", "只有在建项目可以提交变更签证");
    }

    private void validateOwnerContract(VarOrder order) {
        CtContract contract = ctContractMapper.selectById(order.getContractId());
        if (contract == null || !java.util.Objects.equals(contract.getProjectId(), order.getProjectId()))
            throw new BusinessException("CONTRACT_PROJECT_MISMATCH", "合同不存在或不属于当前项目");
        if (!"MAIN".equals(contract.getContractType()) || !"APPROVED".equals(contract.getApprovalStatus())
                || !"PERFORMING".equals(contract.getContractStatus()))
            throw new BusinessException("OWNER_CONTRACT_NOT_PERFORMING", "必须绑定已审批且履约中的业主主合同");
    }

    private VarOrder requireOrder(Long id, String action) {
        VarOrder order = varOrderMapper.selectById(id);
        if (order == null || !java.util.Objects.equals(order.getTenantId(), UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(order.getProjectId(), action);
        return order;
    }

    private List<VarOrderItem> loadItems(Long varOrderId) {
        return varOrderItemMapper.selectList(new LambdaQueryWrapper<VarOrderItem>()
                .eq(VarOrderItem::getTenantId, UserContext.getCurrentTenantId())
                .eq(VarOrderItem::getVarOrderId, varOrderId));
    }

    private long countDocument(Long varOrderId, String documentType, LocalDateTime after) {
        String sql = "SELECT COUNT(*) FROM sys_file WHERE tenant_id=? AND business_type='VARIATION' " +
                "AND business_id=? AND document_type=? AND deleted_flag=0";
        Long count = after == null
                ? jdbcTemplate.queryForObject(sql, Long.class, UserContext.getCurrentTenantId(), varOrderId, documentType)
                : jdbcTemplate.queryForObject(sql + " AND created_at>=?", Long.class,
                        UserContext.getCurrentTenantId(), varOrderId, documentType, after);
        return count == null ? 0 : count;
    }

    private Map<String, Object> latestOwnerSubmission(Long varOrderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM variation_owner_submission WHERE tenant_id=? AND var_order_id=? AND deleted_flag=0 ORDER BY revision_no DESC LIMIT 1",
                UserContext.getCurrentTenantId(), varOrderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> ownerSubmission(Long submissionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM variation_owner_submission WHERE tenant_id=? AND id=? AND deleted_flag=0",
                UserContext.getCurrentTenantId(), submissionId);
        if (rows.isEmpty()) throw new BusinessException("VARIATION_OWNER_SUBMISSION_NOT_FOUND", "业主申报版本不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("items", ownerSubmissionItems(submissionId));
        return result;
    }

    private List<Map<String, Object>> ownerSubmissionItems(Long submissionId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM variation_owner_submission_item WHERE tenant_id=? AND submission_id=? ORDER BY id",
                UserContext.getCurrentTenantId(), submissionId);
    }

    private List<Map<String, Object>> ownerSubmissions(Long varOrderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM variation_owner_submission WHERE tenant_id=? AND var_order_id=? AND deleted_flag=0 ORDER BY revision_no",
                UserContext.getCurrentTenantId(), varOrderId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> withItems = new LinkedHashMap<>(row);
            withItems.put("items", ownerSubmissionItems(((Number) row.get("id")).longValue()));
            result.add(withItems);
        }
        return result;
    }

    private void updateSubmissionReview(Long submissionId, String status, OwnerReviewRequest request,
                                        BigDecimal confirmedAmount, Long changeId) {
        int updated = jdbcTemplate.update("UPDATE variation_owner_submission SET status=?,response_document_no=?,response_comment=?," +
                        "confirmed_amount=?,reviewed_at=?,reviewed_by=?,generated_contract_change_id=?,updated_at=CURRENT_TIMESTAMP " +
                        "WHERE id=? AND tenant_id=? AND status='SUBMITTED'",
                status, request.responseDocumentNo(), request.responseComment(), confirmedAmount, request.reviewedAt(),
                UserContext.getCurrentUserId(), changeId, submissionId, UserContext.getCurrentTenantId());
        if (updated != 1)
            throw new BusinessException("VARIATION_OWNER_REVIEW_DUPLICATE", "该申报版本已被处理，请刷新后重试");
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return value == null ? null : LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateDraftOrder(VarOrder order) {
        if (order.getProjectId() == null) {
            throw new BusinessException("VAR_ORDER_PROJECT_REQUIRED", "请选择项目");
        }
        if (order.getContractId() == null) {
            throw new BusinessException("VAR_ORDER_CONTRACT_REQUIRED", "请选择合同");
        }
        if (!StringUtils.hasText(order.getVarType())) {
            throw new BusinessException("VAR_ORDER_TYPE_REQUIRED", "请选择变更类型");
        }
    }

    private void validateProjectAndContract(Long projectId, Long contractId, String action) {
        checkProjectAccess(projectId, action);
        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        }
        if (!java.util.Objects.equals(contract.getProjectId(), projectId)) {
            throw new BusinessException("CONTRACT_PROJECT_MISMATCH", "合同不属于当前项目");
        }
    }

    private void validatePartner(Long partnerId) {
        if (partnerId == null) return;
        MdPartner partner = mdPartnerMapper.selectById(partnerId);
        if (partner == null || !java.util.Objects.equals(partner.getTenantId(), UserContext.getCurrentTenantId()))
            throw new BusinessException("PARTNER_NOT_FOUND", "合作方不存在");
    }

    private int requiredVersion(Integer version) {
        if (version == null || version < 0)
            throw new BusinessException("VAR_ORDER_VERSION_REQUIRED", "请刷新后携带有效版本重试");
        return version;
    }

    private void checkProjectAccess(Long projectId, String action) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_REQUIRED", "变更签证缺少项目关系");
        }
        projectAccessChecker.checkAccess(projectId, action);
    }

    private List<VarOrderItem> normalizeDraftItems(List<VarOrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("VAR_ORDER_ITEMS_REQUIRED", "请至少保留一条有效明细");
        }
        List<VarOrderItem> validItems = new ArrayList<>();
        for (VarOrderItem item : items) {
            if (item == null) {
                continue;
            }
            BigDecimal quantity = item.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (!StringUtils.hasText(item.getItemName())) {
                throw new BusinessException("VAR_ORDER_ITEM_NAME_REQUIRED", "请填写明细名称");
            }
            if (item.getCostSubjectId() == null) {
                throw new BusinessException("VAR_ORDER_ITEM_COST_SUBJECT_REQUIRED", "请选择成本科目");
            }
            validItems.add(item);
        }
        if (validItems.isEmpty()) {
            throw new BusinessException("VAR_ORDER_ITEMS_REQUIRED", "请至少保留一条有效明细");
        }
        return validItems;
    }

    // ---- VO conversion helpers ----

    private VarOrderVO toVO(VarOrder m) {
        VarOrderVO vo = buildBaseVO(m);
        if (m.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(m.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        if (m.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(m.getContractId());
            if (contract != null) vo.setContractName(contract.getContractName());
        }
        if (m.getPartnerId() != null) {
            MdPartner partner = mdPartnerMapper.selectById(m.getPartnerId());
            if (partner != null) vo.setPartnerName(partner.getPartnerName());
        }
        return vo;
    }

    /**
     * Null-safe map lookup that guards against null keys and Map.of().
     */
    private String safeGet(Map<Long, String> names, Long id) {
        if (id == null) return null;
        return names.get(id);
    }

    private NameMaps resolveNameMaps(List<VarOrder> records) {
        Set<Long> projectIds = records.stream()
                .map(VarOrder::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> contractIds = records.stream()
                .map(VarOrder::getContractId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream()
                .map(VarOrder::getPartnerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectByIds(projectIds).stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectByIds(contractIds).stream()
                .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectByIds(partnerIds).stream()
                .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));
        return new NameMaps(projectNames, contractNames, partnerNames);
    }

    private VarOrderVO toVO(VarOrder m, Map<Long, String> projectNames,
                              Map<Long, String> contractNames, Map<Long, String> partnerNames) {
        VarOrderVO vo = buildBaseVO(m);
        vo.setProjectName(safeGet(projectNames, m.getProjectId()));
        vo.setContractName(safeGet(contractNames, m.getContractId()));
        vo.setPartnerName(safeGet(partnerNames, m.getPartnerId()));
        return vo;
    }

    private VarOrderVO buildBaseVO(VarOrder m) {
        VarOrderVO vo = new VarOrderVO();
        vo.setId(m.getId() != null ? m.getId().toString() : null);
        vo.setTenantId(m.getTenantId() != null ? m.getTenantId().toString() : null);
        vo.setProjectId(m.getProjectId() != null ? m.getProjectId().toString() : null);
        vo.setContractId(m.getContractId() != null ? m.getContractId().toString() : null);
        vo.setPartnerId(m.getPartnerId() != null ? m.getPartnerId().toString() : null);
        vo.setVarCode(m.getVarCode());
        vo.setVarName(m.getVarName());
        vo.setEventDate(m.getEventDate() != null ? m.getEventDate().toString() : null);
        vo.setClaimDeadline(m.getClaimDeadline() != null ? m.getClaimDeadline().toString() : null);
        vo.setEventDescription(m.getEventDescription());
        vo.setCauseCategory(m.getCauseCategory());
        vo.setResponsibleParty(m.getResponsibleParty());
        vo.setBusinessMatterKey(m.getBusinessMatterKey());
        vo.setVarType(m.getVarType());
        vo.setDirection(m.getDirection());
        vo.setReportedAmount(m.getReportedAmount() != null ? m.getReportedAmount().toPlainString() : null);
        vo.setApprovedAmount(m.getApprovedAmount() != null ? m.getApprovedAmount().toPlainString() : null);
        vo.setConfirmedAmount(m.getConfirmedAmount() != null ? m.getConfirmedAmount().toPlainString() : null);
        vo.setOwnerConfirmFlag(m.getOwnerConfirmFlag());
        vo.setEstimatedCostAmount(m.getEstimatedCostAmount() != null ? m.getEstimatedCostAmount().toPlainString() : null);
        vo.setOwnerStatus(m.getOwnerStatus());
        vo.setInternalApprovalInstanceId(m.getInternalApprovalInstanceId() != null ? m.getInternalApprovalInstanceId().toString() : null);
        vo.setGeneratedContractChangeId(m.getGeneratedContractChangeId() != null ? m.getGeneratedContractChangeId().toString() : null);
        vo.setImpactDays(m.getImpactDays());
        vo.setApprovalStatus(m.getApprovalStatus());
        vo.setCostGeneratedFlag(m.getCostGeneratedFlag());
        vo.setVersion(m.getVersion());
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(m.getRemark());
        return vo;
    }

    private VarOrderItemVO toItemVO(VarOrderItem item) {
        VarOrderItemVO vo = new VarOrderItemVO();
        vo.setId(item.getId() != null ? item.getId().toString() : null);
        vo.setTenantId(item.getTenantId() != null ? item.getTenantId().toString() : null);
        vo.setVarOrderId(item.getVarOrderId() != null ? item.getVarOrderId().toString() : null);
        vo.setItemName(item.getItemName());
        vo.setUnit(item.getUnit());
        vo.setQuantity(item.getQuantity() != null ? item.getQuantity().toPlainString() : null);
        vo.setUnitPrice(item.getUnitPrice() != null ? item.getUnitPrice().toPlainString() : null);
        vo.setAmount(item.getAmount() != null ? item.getAmount().toPlainString() : null);
        vo.setClaimUnitPrice(item.getClaimUnitPrice() != null ? item.getClaimUnitPrice().toPlainString() : null);
        vo.setClaimAmount(item.getClaimAmount() != null ? item.getClaimAmount().toPlainString() : null);
        vo.setCostSubjectId(item.getCostSubjectId() != null ? item.getCostSubjectId().toString() : null);
        vo.setCreatedBy(item.getCreatedBy() != null ? item.getCreatedBy().toString() : null);
        vo.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }

    private record NameMaps(
            Map<Long, String> projectNames,
            Map<Long, String> contractNames,
            Map<Long, String> partnerNames) {
    }
}
