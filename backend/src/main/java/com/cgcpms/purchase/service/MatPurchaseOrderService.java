package com.cgcpms.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.service.BudgetLedgerService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.service.FileLifecycleGateway;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.procurement.service.ProcurementIntegrityService;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.vo.MatPurchaseOrderItemVO;
import com.cgcpms.purchase.vo.MatPurchaseOrderVO;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatPurchaseOrderService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final MatPurchaseOrderMapper matPurchaseOrderMapper;
    private final MatPurchaseOrderItemMapper matPurchaseOrderItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final CtContractMapper ctContractMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final WorkflowEngine workflowEngine;
    private final ProjectAccessChecker projectAccessChecker;
    private final ProcurementIntegrityService integrityService;
    private final BudgetLedgerService budgetLedgerService;
    private final MatPurchaseRequestItemMapper purchaseRequestItemMapper;
    private final MatPurchaseRequestMapper purchaseRequestMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final PurchaseOrderPricingService pricingService;
    private final FileLifecycleGateway fileLifecycleGateway;

    public IPage<MatPurchaseOrderVO> getPage(long pageNum, long pageSize, Long projectId, Long contractId,
                                              Long partnerId, String orderStatus, String orderType, String orderCode) {
        LambdaQueryWrapper<MatPurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            checkProjectAccess(projectId, "查询采购订单");
            wrapper.eq(MatPurchaseOrder::getProjectId, projectId);
        } else {
            List<Long> accessibleProjectIds = projectAccessChecker.accessibleProjectIds();
            if (accessibleProjectIds.isEmpty()) {
                wrapper.eq(MatPurchaseOrder::getProjectId, -1L);
            } else {
                wrapper.in(MatPurchaseOrder::getProjectId, accessibleProjectIds);
            }
        }
        if (contractId != null) wrapper.eq(MatPurchaseOrder::getContractId, contractId);
        if (partnerId != null) wrapper.eq(MatPurchaseOrder::getPartnerId, partnerId);
        if (StringUtils.hasText(orderStatus)) wrapper.eq(MatPurchaseOrder::getOrderStatus, orderStatus);
        if (StringUtils.hasText(orderType)) wrapper.eq(MatPurchaseOrder::getOrderType, orderType);
        if (StringUtils.hasText(orderCode)) wrapper.like(MatPurchaseOrder::getOrderCode, orderCode);
        wrapper.eq(MatPurchaseOrder::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(MatPurchaseOrder::getCreatedAt);

        Page<MatPurchaseOrder> page = matPurchaseOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // Batch-prefetch related names to avoid N+1
        List<MatPurchaseOrder> records = page.getRecords();
        Set<Long> projectIds = records.stream().map(MatPurchaseOrder::getProjectId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream().map(MatPurchaseOrder::getPartnerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> contractIds = records.stream().map(MatPurchaseOrder::getContractId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> requestIds = records.stream().map(MatPurchaseOrder::getRequestId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectByIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectByIds(partnerIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectByIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> requestCodes = requestIds.isEmpty() ? Map.of()
                : purchaseRequestMapper.selectByIds(requestIds).stream()
                        .filter(request -> request.getRequestCode() != null)
                        .collect(Collectors.toMap(MatPurchaseRequest::getId, MatPurchaseRequest::getRequestCode,
                                (a, b) -> a));

        return page.convert(o -> toVO(o, projectNames, partnerNames, contractNames, requestCodes));
    }

    public MatPurchaseOrderVO getById(Long id) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(id);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");
        checkProjectAccess(order.getProjectId(), "查看采购订单");

        MatPurchaseOrderVO vo = toVO(order);

        // Load items
        LambdaQueryWrapper<MatPurchaseOrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(MatPurchaseOrderItem::getOrderId, id)
                .eq(MatPurchaseOrderItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatPurchaseOrderItem::getCreatedAt);
        List<MatPurchaseOrderItem> items = matPurchaseOrderItemMapper.selectList(itemWrapper);

        // Resolve material names
        Set<Long> materialIds = items.stream().map(MatPurchaseOrderItem::getMaterialId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> materialNames = materialIds.isEmpty() ? Map.of()
                : mdMaterialMapper.selectByIds(materialIds).stream()
                        .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));

        vo.setItems(items.stream().map(i -> toItemVO(i, materialNames, order.getPricingMode())).toList());
        return vo;
    }

    public List<MatPurchaseOrderItemVO> getItems(Long orderId) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");
        checkProjectAccess(order.getProjectId(), "查看采购订单明细");

        LambdaQueryWrapper<MatPurchaseOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatPurchaseOrderItem::getOrderId, orderId)
                .eq(MatPurchaseOrderItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatPurchaseOrderItem::getCreatedAt);
        List<MatPurchaseOrderItem> items = matPurchaseOrderItemMapper.selectList(wrapper);

        Set<Long> materialIds = items.stream().map(MatPurchaseOrderItem::getMaterialId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> materialNames = materialIds.isEmpty() ? Map.of()
                : mdMaterialMapper.selectByIds(materialIds).stream()
                        .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));

        return items.stream().map(i -> toItemVO(i, materialNames, order.getPricingMode())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MatPurchaseOrder order) {
        checkProjectAccess(order.getProjectId(), "创建采购订单");
        if (order.getRequestId() != null) {
            throw new BusinessException("PURCHASE_REQUEST_CONVERSION_REQUIRED",
                    "采购申请必须通过转换接口生成采购订单");
        }
        MatPurchaseOrder toInsert = new MatPurchaseOrder();
        toInsert.setTenantId(UserContext.getCurrentTenantId());
        toInsert.setProjectId(order.getProjectId());
        toInsert.setContractId(order.getContractId());
        toInsert.setPartnerId(order.getPartnerId());
        toInsert.setOrderType(order.getOrderType());
        toInsert.setOrderDate(order.getOrderDate());
        toInsert.setDeliveryDate(order.getDeliveryDate());
        toInsert.setDeliveryTerms(order.getDeliveryTerms());
        boolean exceptionPurchase = Integer.valueOf(1).equals(order.getExceptionPurchaseFlag());
        toInsert.setExceptionPurchaseFlag(exceptionPurchase ? 1 : 0);
        toInsert.setExceptionReason(exceptionPurchase ? order.getExceptionReason() : null);
        toInsert.setRemark(order.getRemark());
        toInsert.setTotalAmount(BigDecimal.ZERO);
        // Auto-generate order code: PO-yyyyMMdd-XXX
        String prefix = "PO-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        toInsert.setOrderStatus("DRAFT");
        toInsert.setApprovalStatus("DRAFT");

        // Contract validation: if contractId is set, validate contract exists and is PERFORMING
        if (toInsert.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(toInsert.getContractId());
            pricingService.requirePurchaseContract(contract, UserContext.getCurrentTenantId());
            if (!java.util.Objects.equals(contract.getProjectId(), toInsert.getProjectId()))
                throw new BusinessException("CONTRACT_PROJECT_MISMATCH", "关联合同不属于当前项目");
            if (toInsert.getPartnerId() != null && !java.util.Objects.equals(toInsert.getPartnerId(), contract.getPartyBId()))
                throw new BusinessException("PURCHASE_ORDER_PARTNER_MISMATCH", "采购订单供应商必须与合同乙方一致");
            toInsert.setPartnerId(contract.getPartyBId());
            toInsert.setPricingMode(contract.getPricingMode());
        }

        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            toInsert.setOrderCode(nextOrderCode(prefix, attempt));
            try {
                matPurchaseOrderMapper.insert(toInsert);
                return toInsert.getId();
            } catch (DuplicateKeyException e) {
                log.warn("采购订单编号冲突，重试生成 orderCode={}", toInsert.getOrderCode());
            }
        }
        throw new BusinessException("PURCHASE_ORDER_CODE_CONFLICT", "采购订单编号生成冲突，请重试");
    }

    private String nextOrderCode(String prefix, int offset) {
        LambdaQueryWrapper<MatPurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(MatPurchaseOrder::getOrderCode, prefix)
                .eq(MatPurchaseOrder::getTenantId, UserContext.getCurrentTenantId())
                .orderByDesc(MatPurchaseOrder::getOrderCode);
        Page<MatPurchaseOrder> page = new Page<>(0, 1);
        Page<MatPurchaseOrder> result = matPurchaseOrderMapper.selectPage(page, wrapper);
        MatPurchaseOrder last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        int seq = 1 + offset;
        if (last != null && last.getOrderCode() != null && last.getOrderCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getOrderCode().substring(prefix.length())) + 1 + offset;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getOrderCode(), e);
            }
        }
        return prefix + String.format("%03d", seq);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MatPurchaseOrder order) {
        MatPurchaseOrder existing = matPurchaseOrderMapper.selectById(order.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");
        checkProjectAccess(existing.getProjectId(), "编辑采购订单");

        // Guard: cannot edit if approving or approved
        if ("APPROVED".equals(existing.getApprovalStatus()) || "APPROVING".equals(existing.getApprovalStatus()))
            throw new BusinessException("ORDER_IN_APPROVAL", "采购订单审批中或已审批，不可编辑");

        // Contract validation
        if (order.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(order.getContractId());
            pricingService.requirePurchaseContract(contract, UserContext.getCurrentTenantId());
            if (!java.util.Objects.equals(contract.getProjectId(), existing.getProjectId()))
                throw new BusinessException("CONTRACT_PROJECT_MISMATCH", "关联合同不属于当前项目");
            if (order.getPartnerId() != null && !java.util.Objects.equals(order.getPartnerId(), contract.getPartyBId()))
                throw new BusinessException("PURCHASE_ORDER_PARTNER_MISMATCH", "采购订单供应商必须与合同乙方一致");
            order.setPartnerId(contract.getPartyBId());
            order.setPricingMode(contract.getPricingMode());
        }

        MatPurchaseOrder changes = new MatPurchaseOrder();
        changes.setId(existing.getId());
        changes.setContractId(order.getContractId());
        changes.setPartnerId(order.getPartnerId());
        changes.setPricingMode(order.getPricingMode());
        changes.setOrderDate(order.getOrderDate());
        changes.setDeliveryDate(order.getDeliveryDate());
        changes.setDeliveryTerms(order.getDeliveryTerms());
        changes.setRemark(order.getRemark());
        if (existing.getRequestId() == null) {
            boolean exceptionPurchase = Integer.valueOf(1).equals(order.getExceptionPurchaseFlag());
            changes.setExceptionPurchaseFlag(exceptionPurchase ? 1 : 0);
            changes.setExceptionReason(exceptionPurchase ? order.getExceptionReason() : null);
        } else {
            changes.setExceptionPurchaseFlag(0);
        }
        // 驳回后编辑即恢复草稿，允许修正商业条件后重新提交；审批历史由工作流保留。
        changes.setApprovalStatus("REJECTED".equals(existing.getApprovalStatus())
                ? "DRAFT" : existing.getApprovalStatus());
        changes.setTotalAmount(matPurchaseOrderItemMapper.selectList(
                        new LambdaQueryWrapper<MatPurchaseOrderItem>()
                                .eq(MatPurchaseOrderItem::getOrderId, existing.getId())
                                .eq(MatPurchaseOrderItem::getTenantId, existing.getTenantId()))
                .stream()
                .map(MatPurchaseOrderItem::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        matPurchaseOrderMapper.updateById(changes);
    }

    /**
     * 提交采购订单审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long orderId) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");
        checkProjectAccess(order.getProjectId(), "提交采购订单审批");

        // 只允许草稿状态提交
        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("PURCHASE_ORDER_ALREADY_SUBMITTED", "采购订单已提交审批，不可重复提交");

        // 必须有订单编号
        if (order.getOrderCode() == null || order.getOrderCode().isBlank())
            throw new BusinessException("PURCHASE_ORDER_NO_CODE", "订单编号不能为空，无法提交审批");

        validateOrderForSubmission(order);
        if (order.getRequestId() != null) {
            pricingService.requirePurchaseRequestDocument(order.getRequestId(), order.getTenantId());
        }

        int previousRevision = order.getBudgetRevision() == null ? 0 : order.getBudgetRevision();
        int budgetRevision = previousRevision + 1;
        int revisionUpdated = matPurchaseOrderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getId, orderId)
                .eq(MatPurchaseOrder::getTenantId, order.getTenantId())
                .eq(MatPurchaseOrder::getApprovalStatus, "DRAFT")
                .eq(MatPurchaseOrder::getBudgetRevision, previousRevision)
                .set(MatPurchaseOrder::getBudgetRevision, budgetRevision));
        if (revisionUpdated != 1) {
            throw new BusinessException("PURCHASE_ORDER_BUDGET_REVISION_CONFLICT", "采购订单审批轮次已变化，请刷新后重试");
        }
        order.setBudgetRevision(budgetRevision);

        // 采购申请阶段不再占预算；所有采购订单均在订单审批轮次预占。
        for (MatPurchaseOrderItem item : getOrderEntities(order)) {
            budgetLedgerService.reserve(item.getBudgetLineId(), "PURCHASE_ORDER", orderId,
                    item.getAmount(), budgetKey(orderId, item.getId(), budgetRevision, "RESERVE"));
        }

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submitPurchaseOrder(userId, username, tenantId,
                "PURCHASE_ORDER",
                orderId,
                order.getOrderCode(),
                order.getTotalAmount(),
                order.getProjectId(),
                order.getContractId(),
                null, null, null);

        // 更新审批状态为审批中
        LambdaUpdateWrapper<MatPurchaseOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatPurchaseOrder::getId, orderId)
                .set(MatPurchaseOrder::getApprovalStatus, "APPROVING");
        matPurchaseOrderMapper.update(null, updateWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resubmitForApproval(Long orderId, Long instanceId) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(orderId);
        WfInstance instance = wfInstanceMapper.selectById(instanceId);
        if (order == null || !java.util.Objects.equals(order.getTenantId(), UserContext.getCurrentTenantId())
                || instance == null || !java.util.Objects.equals(instance.getBusinessId(), orderId)
                || !"PURCHASE_ORDER".equals(instance.getBusinessType())) {
            throw new BusinessException("PURCHASE_ORDER_RESUBMIT_MISMATCH", "采购订单与审批实例不匹配");
        }
        checkProjectAccess(order.getProjectId(), "重新提交采购订单审批");
        validateOrderForSubmission(order);
        if (order.getRequestId() != null) {
            pricingService.requirePurchaseRequestDocument(order.getRequestId(), order.getTenantId());
        }
        int previousRevision = order.getBudgetRevision() == null ? 0 : order.getBudgetRevision();
        int budgetRevision = previousRevision + 1;
        int updated = matPurchaseOrderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getId, orderId)
                .eq(MatPurchaseOrder::getTenantId, order.getTenantId())
                .eq(MatPurchaseOrder::getBudgetRevision, previousRevision)
                .set(MatPurchaseOrder::getBudgetRevision, budgetRevision));
        if (updated != 1) {
            throw new BusinessException("PURCHASE_ORDER_BUDGET_REVISION_CONFLICT", "采购订单审批轮次已变化，请刷新后重试");
        }
        for (MatPurchaseOrderItem item : getOrderEntities(order)) {
            budgetLedgerService.reserve(item.getBudgetLineId(), "PURCHASE_ORDER", orderId, item.getAmount(),
                    budgetKey(orderId, item.getId(), budgetRevision, "RESERVE"));
        }
        workflowEngine.resubmitPurchaseOrder(instanceId, UserContext.getCurrentUserId(), UserContext.getCurrentUsername());
        matPurchaseOrderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getId, orderId)
                .set(MatPurchaseOrder::getApprovalStatus, "APPROVING"));
    }

    private void validateOrderForSubmission(MatPurchaseOrder order) {
        integrityService.requireActiveProject(order.getProjectId(), "提交采购订单");
        if (order.getContractId() == null) {
            throw new BusinessException("PURCHASE_ORDER_CONTRACT_REQUIRED", "采购订单必须关联执行中的采购合同");
        }
        if (order.getPartnerId() == null) {
            throw new BusinessException("PURCHASE_ORDER_PARTNER_REQUIRED", "采购订单必须填写供应商");
        }
        if (order.getOrderDate() == null || order.getDeliveryDate() == null) {
            throw new BusinessException("PURCHASE_ORDER_DATE_REQUIRED", "订单日期和交货日期不能为空");
        }
        if (order.getDeliveryDate().isBefore(order.getOrderDate())) {
            throw new BusinessException("PURCHASE_ORDER_DATE_INVALID", "交货日期不得早于订单日期");
        }
        if (!StringUtils.hasText(order.getDeliveryTerms())) {
            throw new BusinessException("PURCHASE_ORDER_DELIVERY_TERMS_REQUIRED", "采购订单必须填写交付条件");
        }
        boolean exceptionPurchase = Integer.valueOf(1).equals(order.getExceptionPurchaseFlag());
        if (order.getRequestId() == null && (!exceptionPurchase || !StringUtils.hasText(order.getExceptionReason()))) {
            throw new BusinessException("PURCHASE_ORDER_SOURCE_REQUIRED", "无采购申请来源时必须标记例外采购并填写原因");
        }
        if (order.getRequestId() != null && exceptionPurchase) {
            throw new BusinessException("PURCHASE_ORDER_SOURCE_CONFLICT", "有采购申请来源的订单不得标记为例外采购");
        }
        CtContract contract = ctContractMapper.selectById(order.getContractId());
        pricingService.requirePurchaseContract(contract, order.getTenantId());
        if (!java.util.Objects.equals(contract.getProjectId(), order.getProjectId())) {
            throw new BusinessException("CONTRACT_PROJECT_MISMATCH", "关联合同不属于当前项目");
        }
        if (!java.util.Objects.equals(contract.getPartyBId(), order.getPartnerId())) {
            throw new BusinessException("PURCHASE_ORDER_PARTNER_MISMATCH", "采购订单供应商必须与合同乙方一致");
        }
        if (!java.util.Objects.equals(contract.getPricingMode(), order.getPricingMode())) {
            throw new BusinessException("PURCHASE_ORDER_PRICING_MODE_MISMATCH", "采购订单计价模式与合同不一致");
        }
        MdPartner supplier = mdPartnerMapper.selectById(order.getPartnerId());
        if (supplier == null || !java.util.Objects.equals(supplier.getTenantId(), order.getTenantId())) {
            throw new BusinessException("PURCHASE_ORDER_PARTNER_NOT_FOUND", "采购订单供应商不存在");
        }
        if (!"SUPPLIER".equals(supplier.getPartnerType()) || !"ENABLE".equals(supplier.getStatus())) {
            throw new BusinessException("PURCHASE_ORDER_PARTNER_DISABLED", "采购订单供应商类型不正确或已停用");
        }
        if (java.util.Objects.equals(supplier.getBlacklistFlag(), 1)) {
            throw new BusinessException("PURCHASE_ORDER_PARTNER_BLACKLISTED", "黑名单供应商禁止提交采购订单审批");
        }

        List<MatPurchaseOrderItem> items = matPurchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, order.getId())
                        .eq(MatPurchaseOrderItem::getTenantId, order.getTenantId()));
        if (items.isEmpty()) {
            throw new BusinessException("PURCHASE_ORDER_NO_ITEMS", "采购订单没有明细，无法提交审批");
        }
        Map<Long, MatPurchaseRequestItem> requestItemsById = validateRequestSources(order, items);

        BigDecimal itemTotal = BigDecimal.ZERO;
        for (MatPurchaseOrderItem item : items) {
            if (item.getMaterialId() == null) {
                throw new BusinessException("PURCHASE_ORDER_ITEM_NO_MATERIAL", "采购订单明细物料不能为空");
            }
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new BusinessException("PURCHASE_ORDER_ITEM_QUANTITY_INVALID", "采购订单明细数量必须大于 0");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().signum() <= 0) {
                throw new BusinessException("PURCHASE_ORDER_ITEM_PRICE_INVALID", "采购订单明细单价必须大于 0");
            }
            var contractItem = pricingService.requireUniqueContractItem(contract, item.getMaterialId());
            if (!java.util.Objects.equals(contractItem.getId(), item.getContractItemId())) {
                throw new BusinessException("PURCHASE_ORDER_CONTRACT_ITEM_MISMATCH", "采购订单合同材料清单关系不一致");
            }
            if ("FIXED".equals(contract.getPricingMode())
                    && (contractItem.getUnitPrice() == null
                    || contractItem.getUnitPrice().compareTo(item.getUnitPrice()) != 0)) {
                throw new BusinessException("PURCHASE_ORDER_FIXED_PRICE_MISMATCH", "固定价订单单价必须等于合同清单单价");
            }
            integrityService.requireActiveBudgetLine(order.getProjectId(), item.getBudgetLineId());
            MatPurchaseRequestItem requestItem = item.getRequestItemId() == null
                    ? null : requestItemsById.get(item.getRequestItemId());
            if (requestItem != null) requireQuantityReason(item, requestItem);
            if (item.getTaxRate() == null || item.getTaxRate().signum() < 0
                    || item.getTaxRate().compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException("PURCHASE_ORDER_TAX_RATE_INVALID", "订单税率必须在0到100之间");
            }
            BigDecimal expectedAmount = item.getQuantity().multiply(item.getUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            if (item.getAmount() == null
                    || expectedAmount.compareTo(item.getAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw new BusinessException("PURCHASE_ORDER_ITEM_AMOUNT_MISMATCH", "采购订单明细金额必须等于数量乘以单价");
            }
            itemTotal = itemTotal.add(expectedAmount);
        }

        BigDecimal normalizedTotal = itemTotal.setScale(2, RoundingMode.HALF_UP);
        if (normalizedTotal.signum() <= 0
                || order.getTotalAmount() == null
                || normalizedTotal.compareTo(order.getTotalAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BusinessException("PURCHASE_ORDER_TOTAL_MISMATCH", "采购订单总金额必须等于明细金额合计且大于 0");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectByIdForUpdate(id, UserContext.getCurrentTenantId());
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");
        checkProjectAccess(order.getProjectId(), "删除采购订单");

        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("ORDER_IN_APPROVAL", "采购订单审批中或已审批，不可删除");

        fileLifecycleGateway.deleteAllForBusinessCascade("PURCHASE_ORDER", id);

        // @TableLogic on BaseEntity handles soft-delete automatically
        matPurchaseOrderMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveItemsBatch(Long orderId, List<MatPurchaseOrderItem> items) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");
        checkProjectAccess(order.getProjectId(), "编辑采购订单明细");

        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("ORDER_IN_APPROVAL", "采购订单审批中或已审批，不可编辑明细");

        Map<Long, MatPurchaseRequestItem> requestSources = validateRequestSources(order, items);

        CtContract contract = pricingService.requirePurchaseContract(
                ctContractMapper.selectById(order.getContractId()), order.getTenantId());
        if (!java.util.Objects.equals(contract.getProjectId(), order.getProjectId())
                || !java.util.Objects.equals(contract.getPartyBId(), order.getPartnerId())) {
            throw new BusinessException("PURCHASE_ORDER_CONTRACT_CONTEXT_MISMATCH", "采购订单项目或供应商与合同不一致");
        }

        // Delete old items (tenant isolation)
        LambdaQueryWrapper<MatPurchaseOrderItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MatPurchaseOrderItem::getOrderId, orderId)
                .eq(MatPurchaseOrderItem::getTenantId, UserContext.getCurrentTenantId());
        matPurchaseOrderItemMapper.delete(deleteWrapper);

        // Insert new items
        Long tenantId = UserContext.getCurrentTenantId();
        for (MatPurchaseOrderItem item : items) {
            MdMaterial material = item.getMaterialId() == null
                    ? null : mdMaterialMapper.selectById(item.getMaterialId());
            if (material == null || !tenantId.equals(material.getTenantId())
                    || !"ENABLE".equals(material.getStatus())) {
                throw new BusinessException("MATERIAL_INVALID", "订单物料不存在、不属于当前租户或已停用");
            }
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0
                    || ("ACTUAL".equals(contract.getPricingMode())
                    && (item.getUnitPrice() == null || item.getUnitPrice().signum() <= 0))) {
                throw new BusinessException("PURCHASE_ORDER_ITEM_INVALID", "订单明细数量和单价必须大于0");
            }
            var contractItem = pricingService.requireUniqueContractItem(contract, item.getMaterialId());
            item.setContractItemId(contractItem.getId());
            item.setMaterialName(material.getMaterialName());
            item.setSpecification(material.getSpecification());
            MatPurchaseRequestItem requestSource = item.getRequestItemId() == null
                    ? null : requestSources.get(item.getRequestItemId());
            if (requestSource == null) {
                item.setUnit(material.getUnit());
            } else {
                item.setWbsTaskId(requestSource.getWbsTaskId());
                if (requestSource.getBudgetLineId() != null) {
                    item.setBudgetLineId(requestSource.getBudgetLineId());
                }
                item.setUnit(requestSource.getUnit());
            }
            if ("FIXED".equals(contract.getPricingMode())) {
                if (contractItem.getUnitPrice() == null || contractItem.getUnitPrice().signum() <= 0) {
                    throw new BusinessException("PURCHASE_CONTRACT_PRICE_INVALID", "固定价合同材料单价缺失");
                }
                item.setUnitPrice(contractItem.getUnitPrice());
                item.setPriceSource("CONTRACT_ITEM");
                item.setPriceSourceReceiptItemId(null);
            } else {
                Map<String, Object> recent = pricingService.findRecentReceipt(
                        order.getTenantId(), contract.getPartyBId(), item.getMaterialId());
                Long recentId = recent == null ? null : ((Number) recent.get("id")).longValue();
                boolean verifiedRecent = "RECENT_RECEIPT".equals(item.getPriceSource())
                        && recentId != null
                        && java.util.Objects.equals(recentId, item.getPriceSourceReceiptItemId())
                        && item.getUnitPrice().compareTo((BigDecimal) recent.get("unit_price")) == 0;
                item.setPriceSource(verifiedRecent ? "RECENT_RECEIPT" : "MANUAL");
                item.setPriceSourceReceiptItemId(verifiedRecent ? recentId : null);
            }
            if (item.getBudgetLineId() != null) {
                integrityService.requireActiveBudgetLine(order.getProjectId(), item.getBudgetLineId());
            }
            BigDecimal taxRate = item.getTaxRate() == null ? BigDecimal.ZERO : item.getTaxRate();
            if (taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException("PURCHASE_ORDER_TAX_RATE_INVALID", "订单税率必须在0到100之间");
            }
            BigDecimal amount = item.getQuantity().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal withoutTax = taxRate.signum() == 0 ? amount
                    : amount.multiply(new BigDecimal("100")).divide(new BigDecimal("100").add(taxRate), 2, RoundingMode.HALF_UP);
            item.setTaxRate(taxRate);
            item.setAmount(amount);
            item.setAmountWithoutTax(withoutTax);
            item.setTaxAmount(amount.subtract(withoutTax));
            item.setOrderId(orderId);
            item.setTenantId(tenantId);
            item.setProjectId(order.getProjectId());
            if (item.getReceivedQuantity() == null) item.setReceivedQuantity(BigDecimal.ZERO);
            matPurchaseOrderItemMapper.insert(item);
        }

        // Recalculate total amount
        BigDecimal totalAmount = items.stream()
                .map(MatPurchaseOrderItem::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LambdaUpdateWrapper<MatPurchaseOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatPurchaseOrder::getId, orderId)
                .set(MatPurchaseOrder::getTotalAmount, totalAmount)
                .set(MatPurchaseOrder::getPricingMode, contract.getPricingMode());
        matPurchaseOrderMapper.update(null, updateWrapper);
    }

    private Map<Long, MatPurchaseRequestItem> validateRequestSources(
            MatPurchaseOrder order, List<MatPurchaseOrderItem> items) {
        if (order.getRequestId() == null) {
            if (items.stream().anyMatch(item -> item.getRequestItemId() != null)) {
                throw new BusinessException("PURCHASE_ORDER_SOURCE_CONFLICT", "例外采购订单不得关联采购申请明细");
            }
            return Map.of();
        }

        Set<Long> sourceIds = items.stream()
                .map(MatPurchaseOrderItem::getRequestItemId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<MatPurchaseRequestItem> requestItems = purchaseRequestItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getTenantId, order.getTenantId())
                        .eq(MatPurchaseRequestItem::getRequestId, order.getRequestId()));
        if (sourceIds.size() != items.size() || requestItems.size() != items.size()) {
            throw new BusinessException("PURCHASE_ORDER_REQUEST_ITEM_MISMATCH", "订单明细必须完整对应来源采购申请");
        }

        Map<Long, MatPurchaseRequestItem> byId = requestItems.stream()
                .collect(Collectors.toMap(MatPurchaseRequestItem::getId, item -> item));
        for (MatPurchaseOrderItem item : items) {
            MatPurchaseRequestItem source = byId.get(item.getRequestItemId());
            if (source == null
                    || !java.util.Objects.equals(source.getMaterialId(), item.getMaterialId())
                    || !java.util.Objects.equals(source.getWbsTaskId(), item.getWbsTaskId())
                    || !java.util.Objects.equals(source.getUnit(), item.getUnit())
                    || item.getQuantity() == null) {
                throw new BusinessException("PURCHASE_ORDER_REQUEST_ITEM_MISMATCH", "订单明细来源字段不得变更");
            }
            requireQuantityReason(item, source);
        }
        return byId;
    }

    private void requireQuantityReason(MatPurchaseOrderItem item, MatPurchaseRequestItem source) {
        BigDecimal approved = source.getApprovedQuantity() == null ? source.getQuantity() : source.getApprovedQuantity();
        if (approved != null && item.getQuantity() != null && approved.compareTo(item.getQuantity()) != 0
                && !StringUtils.hasText(item.getQuantityAdjustReason())) {
            throw new BusinessException("PURCHASE_ORDER_QUANTITY_ADJUST_REASON_REQUIRED", "订单数量与审批数量不一致时必须填写调整原因");
        }
    }

    private List<MatPurchaseOrderItem> getOrderEntities(MatPurchaseOrder order) {
        return matPurchaseOrderItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseOrderItem>()
                .eq(MatPurchaseOrderItem::getOrderId, order.getId())
                .eq(MatPurchaseOrderItem::getTenantId, order.getTenantId()));
    }

    private String budgetKey(Long orderId, Long itemId, int revision, String action) {
        return "ORDER:" + orderId + ":ITEM:" + itemId + ":REV:" + revision + ":" + action;
    }

    private MatPurchaseOrderVO toVO(MatPurchaseOrder o) {
        MatPurchaseOrderVO vo = buildBaseVO(o);
        if (o.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(o.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        if (o.getPartnerId() != null) {
            MdPartner partner = mdPartnerMapper.selectById(o.getPartnerId());
            if (partner != null) vo.setPartnerName(partner.getPartnerName());
        }
        if (o.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(o.getContractId());
            if (contract != null) vo.setContractName(contract.getContractName());
        }
        if (o.getRequestId() != null) {
            MatPurchaseRequest request = purchaseRequestMapper.selectById(o.getRequestId());
            if (request != null) vo.setRequestCode(request.getRequestCode());
        }
        return vo;
    }

    private void checkProjectAccess(Long projectId, String action) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_REQUIRED", "采购订单缺少项目关系");
        }
        projectAccessChecker.checkAccess(projectId, action);
    }

    private MatPurchaseOrderVO toVO(MatPurchaseOrder o, Map<Long, String> projectNames,
                                     Map<Long, String> partnerNames, Map<Long, String> contractNames,
                                     Map<Long, String> requestCodes) {
        MatPurchaseOrderVO vo = buildBaseVO(o);
        if (o.getProjectId() != null) vo.setProjectName(projectNames.get(o.getProjectId()));
        if (o.getPartnerId() != null) vo.setPartnerName(partnerNames.get(o.getPartnerId()));
        if (o.getContractId() != null) vo.setContractName(contractNames.get(o.getContractId()));
        if (o.getRequestId() != null) vo.setRequestCode(requestCodes.get(o.getRequestId()));
        return vo;
    }

    private MatPurchaseOrderVO buildBaseVO(MatPurchaseOrder o) {
        MatPurchaseOrderVO vo = new MatPurchaseOrderVO();
        vo.setId(o.getId() != null ? o.getId().toString() : null);
        vo.setTenantId(o.getTenantId() != null ? o.getTenantId().toString() : null);
        vo.setProjectId(o.getProjectId() != null ? o.getProjectId().toString() : null);
        vo.setRequestId(o.getRequestId() != null ? o.getRequestId().toString() : null);
        vo.setContractId(o.getContractId() != null ? o.getContractId().toString() : null);
        vo.setPartnerId(o.getPartnerId() != null ? o.getPartnerId().toString() : null);
        vo.setOrderCode(o.getOrderCode());
        vo.setOrderType(o.getOrderType());
        vo.setOrderDate(o.getOrderDate() != null ? DateTimeUtils.DATE_FMT.format(o.getOrderDate()) : null);
        vo.setDeliveryDate(o.getDeliveryDate() != null ? DateTimeUtils.DATE_FMT.format(o.getDeliveryDate()) : null);
        vo.setDeliveryTerms(o.getDeliveryTerms());
        vo.setExceptionPurchaseFlag(o.getExceptionPurchaseFlag());
        vo.setExceptionReason(o.getExceptionReason());
        vo.setTotalAmount(o.getTotalAmount() != null ? o.getTotalAmount().toPlainString() : null);
        vo.setApprovalStatus(o.getApprovalStatus());
        vo.setOrderStatus(o.getOrderStatus());
        vo.setCreatedBy(o.getCreatedBy() != null ? o.getCreatedBy().toString() : null);
        vo.setCreatedAt(o.getCreatedAt() != null ? DateTimeUtils.DTF.format(o.getCreatedAt()) : null);
        vo.setUpdatedAt(o.getUpdatedAt() != null ? DateTimeUtils.DTF.format(o.getUpdatedAt()) : null);
        vo.setRemark(o.getRemark());
        return vo;
    }

    private MatPurchaseOrderItemVO toItemVO(MatPurchaseOrderItem i, Map<Long, String> materialNames,
                                             String pricingMode) {
        MatPurchaseOrderItemVO vo = new MatPurchaseOrderItemVO();
        vo.setId(i.getId() != null ? i.getId().toString() : null);
        vo.setTenantId(i.getTenantId() != null ? i.getTenantId().toString() : null);
        vo.setOrderId(i.getOrderId() != null ? i.getOrderId().toString() : null);
        vo.setRequestItemId(i.getRequestItemId() != null ? i.getRequestItemId().toString() : null);
        vo.setWbsTaskId(i.getWbsTaskId() != null ? i.getWbsTaskId().toString() : null);
        vo.setBudgetLineId(i.getBudgetLineId() != null ? i.getBudgetLineId().toString() : null);
        vo.setProjectId(i.getProjectId() != null ? i.getProjectId().toString() : null);
        vo.setMaterialId(i.getMaterialId() != null ? i.getMaterialId().toString() : null);
        vo.setContractItemId(i.getContractItemId() != null ? i.getContractItemId().toString() : null);
        vo.setQuantityAdjustReason(i.getQuantityAdjustReason());
        vo.setPricingMode(pricingMode);
        vo.setPriceSource(i.getPriceSource());
        vo.setPriceSourceReceiptItemId(i.getPriceSourceReceiptItemId() == null
                ? null : i.getPriceSourceReceiptItemId().toString());
        vo.setMaterialName(i.getMaterialId() != null ? materialNames.get(i.getMaterialId()) : i.getMaterialName());
        vo.setSpecification(i.getSpecification());
        vo.setUnit(i.getUnit());
        vo.setQuantity(i.getQuantity() != null ? i.getQuantity().toPlainString() : null);
        vo.setUnitPrice(i.getUnitPrice() != null ? i.getUnitPrice().toPlainString() : null);
        vo.setTaxRate(i.getTaxRate() != null ? i.getTaxRate().toPlainString() : "0");
        vo.setAmount(i.getAmount() != null ? i.getAmount().toPlainString() : null);
        vo.setTaxAmount(i.getTaxAmount() != null ? i.getTaxAmount().toPlainString() : "0");
        vo.setAmountWithoutTax(i.getAmountWithoutTax() != null ? i.getAmountWithoutTax().toPlainString() : "0");
        vo.setReceivedQuantity(i.getReceivedQuantity() != null ? i.getReceivedQuantity().toPlainString() : "0");
        vo.setCreatedBy(i.getCreatedBy() != null ? i.getCreatedBy().toString() : null);
        vo.setCreatedAt(i.getCreatedAt() != null ? DateTimeUtils.DTF.format(i.getCreatedAt()) : null);
        vo.setUpdatedAt(i.getUpdatedAt() != null ? DateTimeUtils.DTF.format(i.getUpdatedAt()) : null);
        vo.setRemark(i.getRemark());
        return vo;
    }
}
