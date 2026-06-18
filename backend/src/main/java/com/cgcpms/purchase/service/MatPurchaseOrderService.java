package com.cgcpms.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.vo.MatPurchaseOrderItemVO;
import com.cgcpms.purchase.vo.MatPurchaseOrderVO;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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

    private final MatPurchaseOrderMapper matPurchaseOrderMapper;
    private final MatPurchaseOrderItemMapper matPurchaseOrderItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final CtContractMapper ctContractMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final WorkflowEngine workflowEngine;

    public IPage<MatPurchaseOrderVO> getPage(long pageNum, long pageSize, Long projectId, Long contractId,
                                              Long partnerId, String orderStatus, String orderType, String orderCode) {
        LambdaQueryWrapper<MatPurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) wrapper.eq(MatPurchaseOrder::getProjectId, projectId);
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

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectBatchIds(partnerIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectBatchIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));

        return page.convert(o -> toVO(o, projectNames, partnerNames, contractNames));
    }

    public MatPurchaseOrderVO getById(Long id) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(id);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");

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
                : mdMaterialMapper.selectBatchIds(materialIds).stream()
                        .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));

        vo.setItems(items.stream().map(i -> toItemVO(i, materialNames)).toList());
        return vo;
    }

    public List<MatPurchaseOrderItemVO> getItems(Long orderId) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");

        LambdaQueryWrapper<MatPurchaseOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatPurchaseOrderItem::getOrderId, orderId)
                .eq(MatPurchaseOrderItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatPurchaseOrderItem::getCreatedAt);
        List<MatPurchaseOrderItem> items = matPurchaseOrderItemMapper.selectList(wrapper);

        Set<Long> materialIds = items.stream().map(MatPurchaseOrderItem::getMaterialId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> materialNames = materialIds.isEmpty() ? Map.of()
                : mdMaterialMapper.selectBatchIds(materialIds).stream()
                        .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));

        return items.stream().map(i -> toItemVO(i, materialNames)).toList();
    }

    @Transactional
    public Long create(MatPurchaseOrder order) {
        // Auto-generate order code: PO-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "PO-" + today + "-";

        LambdaQueryWrapper<MatPurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(MatPurchaseOrder::getOrderCode, prefix)
                .eq(MatPurchaseOrder::getTenantId, UserContext.getCurrentTenantId())
                .orderByDesc(MatPurchaseOrder::getOrderCode);
        Page<MatPurchaseOrder> page = new Page<>(0, 1);
        Page<MatPurchaseOrder> result = matPurchaseOrderMapper.selectPage(page, wrapper);
        MatPurchaseOrder last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        int seq = 1;
        if (last != null && last.getOrderCode() != null && last.getOrderCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getOrderCode().substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getOrderCode(), e);
            }
        }
        order.setOrderCode(prefix + String.format("%03d", seq));
        order.setOrderStatus("DRAFT");
        order.setApprovalStatus("DRAFT");

        // Contract validation: if contractId is set, validate contract exists and is PERFORMING
        if (order.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(order.getContractId());
            if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId()))
                throw new BusinessException("CONTRACT_NOT_FOUND", "关联合同不存在");
            if (!ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus()))
                throw new BusinessException("CONTRACT_NOT_PERFORMING", "关联合同非执行中状态，无法创建采购订单");
        }

        matPurchaseOrderMapper.insert(order);
        return order.getId();
    }

    @Transactional
    public void update(MatPurchaseOrder order) {
        MatPurchaseOrder existing = matPurchaseOrderMapper.selectById(order.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");

        // Guard: cannot edit if approving or approved
        if ("APPROVED".equals(existing.getApprovalStatus()) || "APPROVING".equals(existing.getApprovalStatus()))
            throw new BusinessException("ORDER_IN_APPROVAL", "采购订单审批中或已审批，不可编辑");

        // Contract validation
        if (order.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(order.getContractId());
            if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId()))
                throw new BusinessException("CONTRACT_NOT_FOUND", "关联合同不存在");
            if (!ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus()))
                throw new BusinessException("CONTRACT_NOT_PERFORMING", "关联合同非执行中状态，无法关联");
        }

        // Prevent overwriting approval status via update
        order.setApprovalStatus(existing.getApprovalStatus());

        matPurchaseOrderMapper.updateById(order);
    }

    /**
     * 提交采购订单审批。
     */
    @Transactional
    public void submitForApproval(Long orderId) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");

        // 只允许草稿状态提交
        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("PURCHASE_ORDER_ALREADY_SUBMITTED", "采购订单已提交审批，不可重复提交");

        // 必须有订单编号
        if (order.getOrderCode() == null || order.getOrderCode().isBlank())
            throw new BusinessException("PURCHASE_ORDER_NO_CODE", "订单编号不能为空，无法提交审批");

        // 更新审批状态为审批中
        LambdaUpdateWrapper<MatPurchaseOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatPurchaseOrder::getId, orderId)
                .set(MatPurchaseOrder::getApprovalStatus, "APPROVING");
        matPurchaseOrderMapper.update(null, updateWrapper);

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submit(userId, username, tenantId,
                "PURCHASE_ORDER",
                orderId,
                order.getOrderCode(),
                order.getTotalAmount(),
                order.getProjectId(),
                order.getContractId(),
                null, null, null);
    }

    @Transactional
    public void delete(Long id) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(id);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");

        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("ORDER_IN_APPROVAL", "采购订单审批中或已审批，不可删除");

        // @TableLogic on BaseEntity handles soft-delete automatically
        matPurchaseOrderMapper.deleteById(id);
    }

    @Transactional
    public void saveItemsBatch(Long orderId, List<MatPurchaseOrderItem> items) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_ORDER_NOT_FOUND", "采购订单不存在");

        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("ORDER_IN_APPROVAL", "采购订单审批中或已审批，不可编辑明细");

        // Delete old items (tenant isolation)
        LambdaQueryWrapper<MatPurchaseOrderItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MatPurchaseOrderItem::getOrderId, orderId)
                .eq(MatPurchaseOrderItem::getTenantId, UserContext.getCurrentTenantId());
        matPurchaseOrderItemMapper.delete(deleteWrapper);

        // Insert new items
        Long tenantId = UserContext.getCurrentTenantId();
        for (MatPurchaseOrderItem item : items) {
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
                .set(MatPurchaseOrder::getTotalAmount, totalAmount);
        matPurchaseOrderMapper.update(null, updateWrapper);
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
        return vo;
    }

    private MatPurchaseOrderVO toVO(MatPurchaseOrder o, Map<Long, String> projectNames,
                                     Map<Long, String> partnerNames, Map<Long, String> contractNames) {
        MatPurchaseOrderVO vo = buildBaseVO(o);
        if (o.getProjectId() != null) vo.setProjectName(projectNames.get(o.getProjectId()));
        if (o.getPartnerId() != null) vo.setPartnerName(partnerNames.get(o.getPartnerId()));
        if (o.getContractId() != null) vo.setContractName(contractNames.get(o.getContractId()));
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
        vo.setTotalAmount(o.getTotalAmount() != null ? o.getTotalAmount().toPlainString() : null);
        vo.setApprovalStatus(o.getApprovalStatus());
        vo.setOrderStatus(o.getOrderStatus());
        vo.setCreatedBy(o.getCreatedBy() != null ? o.getCreatedBy().toString() : null);
        vo.setCreatedAt(o.getCreatedAt() != null ? DateTimeUtils.DTF.format(o.getCreatedAt()) : null);
        vo.setUpdatedAt(o.getUpdatedAt() != null ? DateTimeUtils.DTF.format(o.getUpdatedAt()) : null);
        vo.setRemark(o.getRemark());
        return vo;
    }

    private MatPurchaseOrderItemVO toItemVO(MatPurchaseOrderItem i, Map<Long, String> materialNames) {
        MatPurchaseOrderItemVO vo = new MatPurchaseOrderItemVO();
        vo.setId(i.getId() != null ? i.getId().toString() : null);
        vo.setTenantId(i.getTenantId() != null ? i.getTenantId().toString() : null);
        vo.setOrderId(i.getOrderId() != null ? i.getOrderId().toString() : null);
        vo.setProjectId(i.getProjectId() != null ? i.getProjectId().toString() : null);
        vo.setMaterialId(i.getMaterialId() != null ? i.getMaterialId().toString() : null);
        vo.setMaterialName(i.getMaterialId() != null ? materialNames.get(i.getMaterialId()) : i.getMaterialName());
        vo.setSpecification(i.getSpecification());
        vo.setUnit(i.getUnit());
        vo.setQuantity(i.getQuantity() != null ? i.getQuantity().toPlainString() : null);
        vo.setUnitPrice(i.getUnitPrice() != null ? i.getUnitPrice().toPlainString() : null);
        vo.setAmount(i.getAmount() != null ? i.getAmount().toPlainString() : null);
        vo.setReceivedQuantity(i.getReceivedQuantity() != null ? i.getReceivedQuantity().toPlainString() : "0");
        vo.setCreatedBy(i.getCreatedBy() != null ? i.getCreatedBy().toString() : null);
        vo.setCreatedAt(i.getCreatedAt() != null ? DateTimeUtils.DTF.format(i.getCreatedAt()) : null);
        vo.setUpdatedAt(i.getUpdatedAt() != null ? DateTimeUtils.DTF.format(i.getUpdatedAt()) : null);
        vo.setRemark(i.getRemark());
        return vo;
    }
}
