package com.cgcpms.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
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
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.receipt.vo.MatReceiptItemVO;
import com.cgcpms.receipt.vo.MatReceiptVO;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MatReceiptService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MatReceiptMapper matReceiptMapper;
    private final MatReceiptItemMapper matReceiptItemMapper;
    private final MatPurchaseOrderMapper matPurchaseOrderMapper;
    private final MatPurchaseOrderItemMapper matPurchaseOrderItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final CtContractMapper ctContractMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final WorkflowEngine workflowEngine;

    public IPage<MatReceiptVO> getPage(long pageNum, long pageSize, Long projectId, Long orderId,
                                        Long contractId, Long partnerId, String receiptCode, String qualityStatus) {
        LambdaQueryWrapper<MatReceipt> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) wrapper.eq(MatReceipt::getProjectId, projectId);
        if (orderId != null) wrapper.eq(MatReceipt::getOrderId, orderId);
        if (contractId != null) wrapper.eq(MatReceipt::getContractId, contractId);
        if (partnerId != null) wrapper.eq(MatReceipt::getPartnerId, partnerId);
        if (StringUtils.hasText(receiptCode)) wrapper.like(MatReceipt::getReceiptCode, receiptCode);
        if (StringUtils.hasText(qualityStatus)) wrapper.eq(MatReceipt::getQualityStatus, qualityStatus);
        wrapper.eq(MatReceipt::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(MatReceipt::getCreatedAt);

        Page<MatReceipt> page = matReceiptMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // Batch-prefetch related names
        List<MatReceipt> records = page.getRecords();
        Set<Long> projectIds = ids(records, MatReceipt::getProjectId);
        Set<Long> orderIds = ids(records, MatReceipt::getOrderId);
        Set<Long> partnerIds = ids(records, MatReceipt::getPartnerId);
        Set<Long> contractIds = ids(records, MatReceipt::getContractId);

        Map<Long, String> projectNames = resolveNames(projectIds, pmProjectMapper,
                PmProject::getId, PmProject::getProjectName);
        Map<Long, String> orderCodes = resolveOrderCodes(orderIds);
        Map<Long, String> partnerNames = resolveNames(partnerIds, mdPartnerMapper,
                MdPartner::getId, MdPartner::getPartnerName);
        Map<Long, String> contractNames = resolveNames(contractIds, ctContractMapper,
                CtContract::getId, CtContract::getContractName);

        return page.convert(o -> toVO(o, projectNames, orderCodes, partnerNames, contractNames));
    }

    public MatReceiptVO getById(Long id) {
        MatReceipt receipt = matReceiptMapper.selectById(id);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");

        MatReceiptVO vo = toVO(receipt);

        // Load items
        LambdaQueryWrapper<MatReceiptItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(MatReceiptItem::getReceiptId, id)
                .eq(MatReceiptItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatReceiptItem::getCreatedAt);
        List<MatReceiptItem> items = matReceiptItemMapper.selectList(itemWrapper);

        // Resolve material names
        Set<Long> materialIds = ids(items, MatReceiptItem::getMaterialId);
        Map<Long, String> materialNames = resolveNames(materialIds, mdMaterialMapper,
                MdMaterial::getId, MdMaterial::getMaterialName);

        vo.setItems(items.stream().map(i -> toItemVO(i, materialNames)).toList());
        return vo;
    }

    @Transactional
    public Long create(MatReceipt receipt) {
        // Auto-generate receipt code: MR-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "MR-" + today + "-";

        LambdaQueryWrapper<MatReceipt> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(MatReceipt::getReceiptCode, prefix)
                .eq(MatReceipt::getTenantId, UserContext.getCurrentTenantId())
                .orderByDesc(MatReceipt::getReceiptCode)
                .last("LIMIT 1");
        MatReceipt last = matReceiptMapper.selectOne(wrapper);

        int seq = 1;
        if (last != null && last.getReceiptCode() != null && last.getReceiptCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getReceiptCode().substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
            }
        }
        receipt.setReceiptCode(prefix + String.format("%03d", seq));
        receipt.setApprovalStatus("DRAFT");
        receipt.setCostGeneratedFlag(0);

        // Validate order if present
        if (receipt.getOrderId() != null) {
            MatPurchaseOrder order = matPurchaseOrderMapper.selectById(receipt.getOrderId());
            if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
                throw new BusinessException("ORDER_NOT_FOUND", "关联采购订单不存在");
            // Auto-fill contract and partner from order
            if (receipt.getContractId() == null) receipt.setContractId(order.getContractId());
            if (receipt.getPartnerId() == null) receipt.setPartnerId(order.getPartnerId());
        }

        matReceiptMapper.insert(receipt);
        return receipt.getId();
    }

    @Transactional
    public void update(MatReceipt receipt) {
        MatReceipt existing = matReceiptMapper.selectById(receipt.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("RECEIPT_IN_APPROVAL", "验收单审批中或已审批，不可编辑");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        // Prevent overwriting generated flags
        receipt.setApprovalStatus(existing.getApprovalStatus());
        receipt.setCostGeneratedFlag(existing.getCostGeneratedFlag());
        receipt.setReceiptCode(existing.getReceiptCode());

        matReceiptMapper.updateById(receipt);
    }

    @Transactional
    public void delete(Long id) {
        MatReceipt receipt = matReceiptMapper.selectById(id);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");

        if (!"DRAFT".equals(receipt.getApprovalStatus()))
            throw new BusinessException("RECEIPT_IN_APPROVAL", "验收单审批中或已审批，不可删除");
        if (receipt.getCostGeneratedFlag() != null && receipt.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可删除");

        LambdaUpdateWrapper<MatReceipt> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MatReceipt::getId, id)
                .set(MatReceipt::getDeletedFlag, 1);
        matReceiptMapper.update(null, wrapper);
    }

    /**
     * 提交材料验收审批。
     */
    @Transactional
    public void submitForApproval(Long receiptId) {
        MatReceipt receipt = matReceiptMapper.selectById(receiptId);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");

        // 只允许草稿状态提交
        if (!"DRAFT".equals(receipt.getApprovalStatus()))
            throw new BusinessException("RECEIPT_ALREADY_SUBMITTED", "验收单已提交审批，不可重复提交");

        // 更新审批状态为审批中
        LambdaUpdateWrapper<MatReceipt> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatReceipt::getId, receiptId)
                .set(MatReceipt::getApprovalStatus, "APPROVING");
        matReceiptMapper.update(null, updateWrapper);

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submit(userId, username, tenantId,
                "MATERIAL_RECEIPT",
                receiptId,
                receipt.getReceiptCode(),
                receipt.getTotalAmount(),
                receipt.getProjectId(),
                receipt.getContractId(),
                null, null);
    }

    public List<MatReceiptItemVO> getItems(Long receiptId) {
        MatReceipt receipt = matReceiptMapper.selectById(receiptId);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");

        LambdaQueryWrapper<MatReceiptItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatReceiptItem::getReceiptId, receiptId)
                .eq(MatReceiptItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatReceiptItem::getCreatedAt);
        List<MatReceiptItem> items = matReceiptItemMapper.selectList(wrapper);

        Set<Long> materialIds = ids(items, MatReceiptItem::getMaterialId);
        Map<Long, String> materialNames = resolveNames(materialIds, mdMaterialMapper,
                MdMaterial::getId, MdMaterial::getMaterialName);

        return items.stream().map(i -> toItemVO(i, materialNames)).toList();
    }

    /**
     * Load order items for receipt line selection.
     * Includes ordered_qty, already_received_qty, remaining_qty.
     */
    public List<MatReceiptItemVO> getOrderItemsForReceipt(Long orderId) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("ORDER_NOT_FOUND", "采购订单不存在");

        LambdaQueryWrapper<MatPurchaseOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatPurchaseOrderItem::getOrderId, orderId)
                .eq(MatPurchaseOrderItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatPurchaseOrderItem::getCreatedAt);
        List<MatPurchaseOrderItem> items = matPurchaseOrderItemMapper.selectList(wrapper);

        Set<Long> materialIds = ids(items, MatPurchaseOrderItem::getMaterialId);
        Map<Long, String> materialNames = resolveNames(materialIds, mdMaterialMapper,
                MdMaterial::getId, MdMaterial::getMaterialName);
        Map<Long, MdMaterial> materialMap = resolveEntities(materialIds, mdMaterialMapper);

        return items.stream().map(i -> {
            MatReceiptItemVO vo = new MatReceiptItemVO();
            vo.setOrderItemId(i.getId() != null ? i.getId().toString() : null);
            vo.setMaterialId(i.getMaterialId() != null ? i.getMaterialId().toString() : null);
            vo.setMaterialName(i.getMaterialId() != null ? materialNames.get(i.getMaterialId()) : null);
            if (i.getMaterialId() != null) {
                MdMaterial mat = materialMap.get(i.getMaterialId());
                if (mat != null) {
                    vo.setSpecification(mat.getSpecification());
                    vo.setUnit(mat.getUnit());
                }
            }
            BigDecimal orderedQty = i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO;
            BigDecimal receivedQty = i.getReceivedQuantity() != null ? i.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal remainingQty = orderedQty.subtract(receivedQty);
            vo.setOrderedQuantity(orderedQty.toPlainString());
            vo.setReceivedQuantity(receivedQty.toPlainString());
            vo.setRemainingQuantity(remainingQty.toPlainString());
            vo.setActualQuantity("0");
            vo.setQualifiedQuantity("0");
            vo.setUnitPrice(i.getUnitPrice() != null ? i.getUnitPrice().toPlainString() : "0");
            vo.setAmount("0");
            return vo;
        }).toList();
    }

    /**
     * Batch save receipt items with quantity validation.
     * W0 Decision 3: WARN but don't block when receipt qty > order remaining qty.
     */
    @Transactional
    public void saveItemsBatch(Long receiptId, List<MatReceiptItem> items) {
        MatReceipt receipt = matReceiptMapper.selectById(receiptId);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");

        if (!"DRAFT".equals(receipt.getApprovalStatus()))
            throw new BusinessException("RECEIPT_IN_APPROVAL", "验收单审批中或已审批，不可编辑");
        if (receipt.getCostGeneratedFlag() != null && receipt.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        Long tenantId = UserContext.getCurrentTenantId();

        // Subtract old item quantities from order items before deletion
        LambdaQueryWrapper<MatReceiptItem> oldItemWrapper = new LambdaQueryWrapper<>();
        oldItemWrapper.eq(MatReceiptItem::getReceiptId, receiptId)
                .eq(MatReceiptItem::getTenantId, tenantId);
        List<MatReceiptItem> oldItems = matReceiptItemMapper.selectList(oldItemWrapper);
        for (MatReceiptItem oldItem : oldItems) {
            if (oldItem.getOrderItemId() != null) {
                MatPurchaseOrderItem orderItem = matPurchaseOrderItemMapper.selectById(oldItem.getOrderItemId());
                if (orderItem != null) {
                    BigDecimal oldQty = oldItem.getActualQuantity() != null ? oldItem.getActualQuantity() : BigDecimal.ZERO;
                    BigDecimal currentReceived = orderItem.getReceivedQuantity() != null
                            ? orderItem.getReceivedQuantity() : BigDecimal.ZERO;
                    BigDecimal newReceived = currentReceived.subtract(oldQty);
                    if (newReceived.compareTo(BigDecimal.ZERO) < 0) newReceived = BigDecimal.ZERO;
                    orderItem.setReceivedQuantity(newReceived);
                    matPurchaseOrderItemMapper.updateById(orderItem);
                }
            }
        }

        // Delete old items
        LambdaQueryWrapper<MatReceiptItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MatReceiptItem::getReceiptId, receiptId)
                .eq(MatReceiptItem::getTenantId, tenantId);
        matReceiptItemMapper.delete(deleteWrapper);

        // Insert new items
        for (MatReceiptItem item : items) {
            item.setReceiptId(receiptId);
            item.setTenantId(tenantId);
            if (item.getActualQuantity() == null) item.setActualQuantity(BigDecimal.ZERO);
            if (item.getQualifiedQuantity() == null) item.setQualifiedQuantity(BigDecimal.ZERO);
            if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
            if (item.getAmount() == null) item.setAmount(BigDecimal.ZERO);
            matReceiptItemMapper.insert(item);

            // Update order item received_quantity (W0 Decision 3: always update, warn if exceeds)
            if (item.getOrderItemId() != null) {
                MatPurchaseOrderItem orderItem = matPurchaseOrderItemMapper.selectById(item.getOrderItemId());
                if (orderItem != null) {
                    BigDecimal currentReceived = orderItem.getReceivedQuantity() != null
                            ? orderItem.getReceivedQuantity() : BigDecimal.ZERO;
                    BigDecimal newReceived = currentReceived.add(item.getActualQuantity());
                    orderItem.setReceivedQuantity(newReceived);
                    matPurchaseOrderItemMapper.updateById(orderItem);
                }
            }
        }

        // Recalculate total amount
        BigDecimal totalAmount = items.stream()
                .map(MatReceiptItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LambdaUpdateWrapper<MatReceipt> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatReceipt::getId, receiptId)
                .set(MatReceipt::getTotalAmount, totalAmount);
        matReceiptMapper.update(null, updateWrapper);
    }

    // --- VO conversion helpers ---

    private MatReceiptVO toVO(MatReceipt r) {
        MatReceiptVO vo = buildBaseVO(r);
        if (r.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(r.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        if (r.getOrderId() != null) {
            MatPurchaseOrder order = matPurchaseOrderMapper.selectById(r.getOrderId());
            if (order != null) vo.setOrderCode(order.getOrderCode());
        }
        if (r.getPartnerId() != null) {
            MdPartner partner = mdPartnerMapper.selectById(r.getPartnerId());
            if (partner != null) vo.setPartnerName(partner.getPartnerName());
        }
        if (r.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(r.getContractId());
            if (contract != null) vo.setContractName(contract.getContractName());
        }
        return vo;
    }

    private MatReceiptVO toVO(MatReceipt r, Map<Long, String> projectNames, Map<Long, String> orderCodes,
                               Map<Long, String> partnerNames, Map<Long, String> contractNames) {
        MatReceiptVO vo = buildBaseVO(r);
        if (r.getProjectId() != null) vo.setProjectName(projectNames.get(r.getProjectId()));
        if (r.getOrderId() != null) vo.setOrderCode(orderCodes.get(r.getOrderId()));
        if (r.getPartnerId() != null) vo.setPartnerName(partnerNames.get(r.getPartnerId()));
        if (r.getContractId() != null) vo.setContractName(contractNames.get(r.getContractId()));
        return vo;
    }

    private MatReceiptVO buildBaseVO(MatReceipt r) {
        MatReceiptVO vo = new MatReceiptVO();
        vo.setId(r.getId() != null ? r.getId().toString() : null);
        vo.setTenantId(r.getTenantId() != null ? r.getTenantId().toString() : null);
        vo.setProjectId(r.getProjectId() != null ? r.getProjectId().toString() : null);
        vo.setOrderId(r.getOrderId() != null ? r.getOrderId().toString() : null);
        vo.setContractId(r.getContractId() != null ? r.getContractId().toString() : null);
        vo.setPartnerId(r.getPartnerId() != null ? r.getPartnerId().toString() : null);
        vo.setReceiptCode(r.getReceiptCode());
        vo.setReceiptDate(r.getReceiptDate() != null ? DATE_FMT.format(r.getReceiptDate()) : null);
        vo.setWarehouseId(r.getWarehouseId() != null ? r.getWarehouseId().toString() : null);
        vo.setReceiverId(r.getReceiverId() != null ? r.getReceiverId().toString() : null);
        vo.setQualityStatus(r.getQualityStatus());
        vo.setTotalAmount(r.getTotalAmount() != null ? r.getTotalAmount().toPlainString() : null);
        vo.setApprovalStatus(r.getApprovalStatus());
        vo.setCostGeneratedFlag(r.getCostGeneratedFlag());
        vo.setCreatedBy(r.getCreatedBy() != null ? r.getCreatedBy().toString() : null);
        vo.setCreatedAt(r.getCreatedAt() != null ? DTF.format(r.getCreatedAt()) : null);
        vo.setUpdatedAt(r.getUpdatedAt() != null ? DTF.format(r.getUpdatedAt()) : null);
        vo.setRemark(r.getRemark());
        return vo;
    }

    private MatReceiptItemVO toItemVO(MatReceiptItem i, Map<Long, String> materialNames) {
        MatReceiptItemVO vo = new MatReceiptItemVO();
        vo.setId(i.getId() != null ? i.getId().toString() : null);
        vo.setTenantId(i.getTenantId() != null ? i.getTenantId().toString() : null);
        vo.setReceiptId(i.getReceiptId() != null ? i.getReceiptId().toString() : null);
        vo.setOrderItemId(i.getOrderItemId() != null ? i.getOrderItemId().toString() : null);
        vo.setMaterialId(i.getMaterialId() != null ? i.getMaterialId().toString() : null);
        vo.setMaterialName(i.getMaterialId() != null ? materialNames.get(i.getMaterialId()) : null);
        vo.setActualQuantity(i.getActualQuantity() != null ? i.getActualQuantity().toPlainString() : null);
        vo.setQualifiedQuantity(i.getQualifiedQuantity() != null ? i.getQualifiedQuantity().toPlainString() : null);
        vo.setUnitPrice(i.getUnitPrice() != null ? i.getUnitPrice().toPlainString() : null);
        vo.setAmount(i.getAmount() != null ? i.getAmount().toPlainString() : null);
        vo.setUseLocation(i.getUseLocation());
        vo.setBatchNo(i.getBatchNo());
        vo.setCreatedBy(i.getCreatedBy() != null ? i.getCreatedBy().toString() : null);
        vo.setCreatedAt(i.getCreatedAt() != null ? DTF.format(i.getCreatedAt()) : null);
        vo.setUpdatedAt(i.getUpdatedAt() != null ? DTF.format(i.getUpdatedAt()) : null);
        vo.setRemark(i.getRemark());
        return vo;
    }

    // --- Utility helpers ---

    private <T> Set<Long> ids(List<T> records, java.util.function.Function<T, Long> extractor) {
        Set<Long> set = new HashSet<>();
        for (T r : records) {
            Long v = extractor.apply(r);
            if (v != null) set.add(v);
        }
        return set;
    }

    private Map<Long, String> resolveOrderCodes(Set<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        List<MatPurchaseOrder> orders = matPurchaseOrderMapper.selectBatchIds(orderIds);
        Map<Long, String> map = new HashMap<>();
        for (MatPurchaseOrder o : orders) {
            map.put(o.getId(), o.getOrderCode());
        }
        return map;
    }

    private <T> Map<Long, String> resolveNames(Set<Long> ids,
                                                com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                                java.util.function.Function<T, Long> idExtractor,
                                                java.util.function.Function<T, String> nameExtractor) {
        if (ids.isEmpty()) return Map.of();
        List<T> entities = mapper.selectBatchIds(ids);
        Map<Long, String> map = new HashMap<>();
        for (T e : entities) {
            map.put(idExtractor.apply(e), nameExtractor.apply(e));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private <T> Map<Long, T> resolveEntities(Set<Long> ids,
                                              com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper) {
        if (ids.isEmpty()) return Map.of();
        List<T> entities = mapper.selectBatchIds(ids);
        Map<Long, T> map = new HashMap<>();
        for (T e : entities) {
            try {
                Long id = (Long) e.getClass().getMethod("getId").invoke(e);
                map.put(id, e);
            } catch (Exception ignored) {
            }
        }
        return map;
    }
}
