package com.cgcpms.receipt.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
import com.cgcpms.receipt.vo.MatReceiptItemVO;
import com.cgcpms.receipt.vo.MatReceiptVO;
import com.cgcpms.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/**
 * VO Assembler for MatReceipt — extracts all VO conversion logic from MatReceiptService.
 * <p>
 * Provides two overloads:
 * <ul>
 *   <li>{@link #assemble(MatReceipt)} — single-object (delegates to batch)</li>
 *   <li>{@link #assembleBatch(List)} — batch prefetch of project/order/partner/contract names
 *       to avoid N+1 queries</li>
 * </ul>
 * Item-level assembly via {@link #assembleItem(MatReceiptItem, Map)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatReceiptAssembler {

    private final PmProjectMapper pmProjectMapper;
    private final MatPurchaseOrderMapper matPurchaseOrderMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final CtContractMapper ctContractMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final MatPurchaseOrderItemMapper matPurchaseOrderItemMapper;

    // ────────────────────────── Receipt VO ──────────────────────────

    /** Single-object assemble — delegates to batch for consistency. */
    public MatReceiptVO assemble(MatReceipt r) {
        if (r == null) return null;
        List<MatReceipt> list = List.of(r);
        var pre = prefetchRelationals(list);
        return toVO(r, pre.projectNames, pre.orderCodes, pre.partnerNames, pre.contractNames);
    }

    /** Batch assemble with single round-trip prefetch for all relational names. */
    public List<MatReceiptVO> assembleBatch(List<MatReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) return List.of();
        var pre = prefetchRelationals(receipts);
        return receipts.stream().map(r -> toVO(r, pre.projectNames, pre.orderCodes, pre.partnerNames, pre.contractNames)).toList();
    }

    // ──────────────── Item VO ────────────────

    /** Assemble a single receipt item with pre-resolved material names. */
    public MatReceiptItemVO assembleItem(MatReceiptItem item, Map<Long, String> materialNames) {
        return toItemVO(item, materialNames);
    }

    /** Assemble a list of receipt items, prefetching material names in one query. */
    public List<MatReceiptItemVO> assembleItems(List<MatReceiptItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        Set<Long> materialIds = ids(items, MatReceiptItem::getMaterialId);
        Map<Long, String> materialNames = resolveNames(materialIds, mdMaterialMapper,
                MdMaterial::getId, MdMaterial::getMaterialName);
        return items.stream().map(i -> toItemVO(i, materialNames)).toList();
    }

    /**
     * Build receipt-item VOs from purchase order items, with material names/specs/unit
     * pre-resolved, plus orderedQty/receivedQty/remainingQty calculation.
     */
    public List<MatReceiptItemVO> assembleOrderItemsForReceipt(List<MatPurchaseOrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) return List.of();
        Set<Long> materialIds = ids(orderItems, MatPurchaseOrderItem::getMaterialId);
        Map<Long, String> materialNames = resolveNames(materialIds, mdMaterialMapper,
                MdMaterial::getId, MdMaterial::getMaterialName);
        Map<Long, MdMaterial> materialMap = resolveEntities(materialIds, mdMaterialMapper);
        return orderItems.stream().map(i -> {
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

    // ──────────────── Prefetch helpers ────────────────

    private record PrefetchResult(
            Map<Long, String> projectNames,
            Map<Long, String> orderCodes,
            Map<Long, String> partnerNames,
            Map<Long, String> contractNames) {}

    private PrefetchResult prefetchRelationals(List<MatReceipt> records) {
        Set<Long> projectIds = ids(records, MatReceipt::getProjectId);
        Set<Long> orderIds = ids(records, MatReceipt::getOrderId);
        Set<Long> partnerIds = ids(records, MatReceipt::getPartnerId);
        Set<Long> contractIds = ids(records, MatReceipt::getContractId);
        return new PrefetchResult(
                resolveNames(projectIds, pmProjectMapper, PmProject::getId, PmProject::getProjectName),
                resolveOrderCodes(orderIds),
                resolveNames(partnerIds, mdPartnerMapper, MdPartner::getId, MdPartner::getPartnerName),
                resolveNames(contractIds, ctContractMapper, CtContract::getId, CtContract::getContractName));
    }

    // ──────── internal VO builders ────────

    private MatReceiptVO toVO(MatReceipt r, Map<Long, String> projectNames, Map<Long, String> orderCodes,
                               Map<Long, String> partnerNames, Map<Long, String> contractNames) {
        MatReceiptVO vo = new MatReceiptVO();
        vo.setId(r.getId() != null ? r.getId().toString() : null);
        vo.setTenantId(r.getTenantId() != null ? r.getTenantId().toString() : null);
        vo.setProjectId(r.getProjectId() != null ? r.getProjectId().toString() : null);
        vo.setOrderId(r.getOrderId() != null ? r.getOrderId().toString() : null);
        vo.setContractId(r.getContractId() != null ? r.getContractId().toString() : null);
        vo.setPartnerId(r.getPartnerId() != null ? r.getPartnerId().toString() : null);
        vo.setReceiptCode(r.getReceiptCode());
        vo.setReceiptDate(r.getReceiptDate() != null ? DateTimeUtils.DATE_FMT.format(r.getReceiptDate()) : null);
        vo.setWarehouseId(r.getWarehouseId() != null ? r.getWarehouseId().toString() : null);
        vo.setReceiverId(r.getReceiverId() != null ? r.getReceiverId().toString() : null);
        vo.setQualityStatus(r.getQualityStatus());
        vo.setTotalAmount(r.getTotalAmount() != null ? r.getTotalAmount().toPlainString() : null);
        vo.setApprovalStatus(r.getApprovalStatus());
        vo.setCostGeneratedFlag(r.getCostGeneratedFlag());
        vo.setCreatedBy(r.getCreatedBy() != null ? r.getCreatedBy().toString() : null);
        vo.setCreatedAt(r.getCreatedAt() != null ? DateTimeUtils.DTF.format(r.getCreatedAt()) : null);
        vo.setUpdatedAt(r.getUpdatedAt() != null ? DateTimeUtils.DTF.format(r.getUpdatedAt()) : null);
        vo.setRemark(r.getRemark());
        if (r.getProjectId() != null) vo.setProjectName(projectNames.get(r.getProjectId()));
        if (r.getOrderId() != null) vo.setOrderCode(orderCodes.get(r.getOrderId()));
        if (r.getPartnerId() != null) vo.setPartnerName(partnerNames.get(r.getPartnerId()));
        if (r.getContractId() != null) vo.setContractName(contractNames.get(r.getContractId()));
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
        vo.setCreatedAt(i.getCreatedAt() != null ? DateTimeUtils.DTF.format(i.getCreatedAt()) : null);
        vo.setUpdatedAt(i.getUpdatedAt() != null ? DateTimeUtils.DTF.format(i.getUpdatedAt()) : null);
        vo.setRemark(i.getRemark());
        return vo;
    }

    // ──────────────── generic utilities ────────────────

    /** Extracts non-null IDs from entity list using given extractor. */
    @SuppressWarnings("unused") // used by assembleOrderItemsForReceipt caller via public API
    public static <T> Set<Long> ids(List<T> records, Function<T, Long> extractor) {
        Set<Long> set = new HashSet<>();
        for (T r : records) {
            Long v = extractor.apply(r);
            if (v != null) set.add(v);
        }
        return set;
    }

    private Map<Long, String> resolveOrderCodes(Set<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        List<MatPurchaseOrder> orders = matPurchaseOrderMapper.selectByIds(orderIds);
        Map<Long, String> map = new HashMap<>();
        for (MatPurchaseOrder o : orders) {
            map.put(o.getId(), o.getOrderCode());
        }
        return map;
    }

    private <T> Map<Long, String> resolveNames(Set<Long> ids,
                                                BaseMapper<T> mapper,
                                                Function<T, Long> idExtractor,
                                                Function<T, String> nameExtractor) {
        if (ids.isEmpty()) return Map.of();
        List<T> entities = mapper.selectByIds(ids);
        Map<Long, String> map = new HashMap<>();
        for (T e : entities) {
            map.put(idExtractor.apply(e), nameExtractor.apply(e));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private <T> Map<Long, T> resolveEntities(Set<Long> ids, BaseMapper<T> mapper) {
        if (ids.isEmpty()) return Map.of();
        List<T> entities = mapper.selectByIds(ids);
        Map<Long, T> map = new HashMap<>();
        for (T e : entities) {
            try {
                Long id = (Long) e.getClass().getMethod("getId").invoke(e);
                map.put(id, e);
            } catch (Exception ignored) {
                log.warn("Failed to extract entity id via reflection", ignored);
            }
        }
        return map;
    }
}
