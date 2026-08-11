package com.cgcpms.requisition.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.vo.MatRequisitionItemVO;
import com.cgcpms.requisition.vo.MatRequisitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * VO Assembler for MatRequisition -- extracts all VO conversion logic from MatRequisitionService.
 * <p>
 * Provides two overloads:
 * <ul>
 *   <li>{@link #assemble(MatRequisition)} -- single-object (delegates to batch)</li>
 *   <li>{@link #assembleBatch(List)} -- batch prefetch of project/contract/partner names
 *       to avoid N+1 queries</li>
 * </ul>
 * Item-level assembly via {@link #assembleItem(MatRequisitionItem, Map, Map)}.
 */
@Component
@RequiredArgsConstructor
public class MatRequisitionAssembler {

    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final MatWarehouseMapper matWarehouseMapper;

    // ────────────────────────── Requisition VO ──────────────────────────

    /** Single-object assemble -- delegates to batch for consistency. */
    public MatRequisitionVO assemble(MatRequisition r) {
        if (r == null) return null;
        List<MatRequisition> list = List.of(r);
        var pre = prefetchRelationals(list);
        return toVO(r, pre.projectNames, pre.contractNames, pre.partnerNames, pre.warehouses);
    }

    /** Batch assemble with single round-trip prefetch for all relational names. */
    public List<MatRequisitionVO> assembleBatch(List<MatRequisition> requisitions) {
        if (requisitions == null || requisitions.isEmpty()) return List.of();
        var pre = prefetchRelationals(requisitions);
        return requisitions.stream()
                .map(r -> toVO(r, pre.projectNames, pre.contractNames, pre.partnerNames, pre.warehouses))
                .toList();
    }

    // ──────────────── Item VO ────────────────

    /** Assemble a single requisition item with pre-resolved material names and units. */
    public MatRequisitionItemVO assembleItem(MatRequisitionItem item,
                                              Map<Long, String> materialNames,
                                              Map<Long, MdMaterial> materialMap) {
        return toItemVO(item, materialNames, materialMap);
    }

    /** Assemble a list of requisition items, prefetching material names and units in one query. */
    public List<MatRequisitionItemVO> assembleItems(List<MatRequisitionItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        Set<Long> materialIds = ids(items, MatRequisitionItem::getMaterialId);
        Map<Long, String> materialNames = resolveNames(materialIds, mdMaterialMapper,
                MdMaterial::getId, MdMaterial::getMaterialName);
        Map<Long, MdMaterial> materialMap = resolveEntities(materialIds, mdMaterialMapper, MdMaterial::getId);
        return items.stream().map(i -> toItemVO(i, materialNames, materialMap)).toList();
    }

    // ──────────────── Prefetch helpers ────────────────

    private record PrefetchResult(
            Map<Long, String> projectNames,
            Map<Long, String> contractNames,
            Map<Long, String> partnerNames,
            Map<Long, MatWarehouse> warehouses) {}

    private PrefetchResult prefetchRelationals(List<MatRequisition> records) {
        Set<Long> projectIds = ids(records, MatRequisition::getProjectId);
        Set<Long> contractIds = ids(records, MatRequisition::getContractId);
        Set<Long> partnerIds = ids(records, MatRequisition::getPartnerId);
        Set<Long> warehouseIds = ids(records, MatRequisition::getWarehouseId);
        return new PrefetchResult(
                resolveNames(projectIds, pmProjectMapper, PmProject::getId, PmProject::getProjectName),
                resolveNames(contractIds, ctContractMapper, CtContract::getId, CtContract::getContractName),
                resolveNames(partnerIds, mdPartnerMapper, MdPartner::getId, MdPartner::getPartnerName),
                resolveEntities(warehouseIds, matWarehouseMapper, MatWarehouse::getId));
    }

    // ──────── internal VO builders ────────

    private MatRequisitionVO toVO(MatRequisition r, Map<Long, String> projectNames,
                                   Map<Long, String> contractNames, Map<Long, String> partnerNames,
                                   Map<Long, MatWarehouse> warehouses) {
        MatRequisitionVO vo = new MatRequisitionVO();
        vo.setId(r.getId() != null ? r.getId().toString() : null);
        vo.setTenantId(r.getTenantId() != null ? r.getTenantId().toString() : null);
        vo.setProjectId(r.getProjectId() != null ? r.getProjectId().toString() : null);
        vo.setContractId(r.getContractId() != null ? r.getContractId().toString() : null);
        vo.setPartnerId(r.getPartnerId() != null ? r.getPartnerId().toString() : null);
        vo.setRequisitionCode(r.getRequisitionCode());
        vo.setRequisitionDate(r.getRequisitionDate() != null
                ? DateTimeUtils.DATE_FMT.format(r.getRequisitionDate()) : null);
        vo.setWarehouseId(r.getWarehouseId() != null ? r.getWarehouseId().toString() : null);
        vo.setRequisitionerId(r.getRequisitionerId() != null ? r.getRequisitionerId().toString() : null);
        vo.setApprovalStatus(r.getApprovalStatus());
        vo.setTotalAmount(r.getTotalAmount() != null ? r.getTotalAmount().toPlainString() : null);
        vo.setStockOutFlag(r.getStockOutFlag() != null ? r.getStockOutFlag().toString() : null);
        vo.setStockOutBy(r.getStockOutBy() != null ? r.getStockOutBy().toString() : null);
        vo.setStockOutAt(r.getStockOutAt() != null ? DateTimeUtils.DTF.format(r.getStockOutAt()) : null);
        vo.setCreatedBy(r.getCreatedBy() != null ? r.getCreatedBy().toString() : null);
        vo.setCreatedAt(r.getCreatedTime() != null ? DateTimeUtils.DTF.format(r.getCreatedTime()) : null);
        vo.setUpdatedAt(r.getUpdatedTime() != null ? DateTimeUtils.DTF.format(r.getUpdatedTime()) : null);
        vo.setRemark(r.getRemark());
        if (r.getProjectId() != null) vo.setProjectName(projectNames.get(r.getProjectId()));
        if (r.getContractId() != null) vo.setContractName(contractNames.get(r.getContractId()));
        if (r.getPartnerId() != null) vo.setPartnerName(partnerNames.get(r.getPartnerId()));
        if (r.getWarehouseId() != null) {
            MatWarehouse warehouse = warehouses.get(r.getWarehouseId());
            if (warehouse != null) {
                vo.setWarehouseCode(warehouse.getWarehouseCode());
                vo.setWarehouseName(warehouse.getWarehouseName());
            }
        }
        return vo;
    }

    private MatRequisitionItemVO toItemVO(MatRequisitionItem i,
                                           Map<Long, String> materialNames,
                                           Map<Long, MdMaterial> materialMap) {
        MatRequisitionItemVO vo = new MatRequisitionItemVO();
        vo.setId(i.getId() != null ? i.getId().toString() : null);
        vo.setTenantId(i.getTenantId() != null ? i.getTenantId().toString() : null);
        vo.setRequisitionId(i.getRequisitionId() != null ? i.getRequisitionId().toString() : null);
        vo.setWbsTaskId(i.getWbsTaskId() != null ? i.getWbsTaskId().toString() : null);
        vo.setMaterialId(i.getMaterialId() != null ? i.getMaterialId().toString() : null);
        vo.setMaterialName(i.getMaterialId() != null ? materialNames.get(i.getMaterialId()) : null);
        if (i.getMaterialId() != null) {
            MdMaterial mat = materialMap.get(i.getMaterialId());
            if (mat != null) {
                vo.setSpecification(mat.getSpecification());
                vo.setUnit(mat.getUnit());
            }
        }
        vo.setQuantity(i.getQuantity() != null ? i.getQuantity().toPlainString() : null);
        vo.setUnitPrice(i.getUnitPrice() != null ? i.getUnitPrice().toPlainString() : null);
        vo.setAmount(i.getAmount() != null ? i.getAmount().toPlainString() : null);
        vo.setUseLocation(i.getUseLocation());
        vo.setBatchNo(i.getBatchNo());
        vo.setCreatedBy(i.getCreatedBy() != null ? i.getCreatedBy().toString() : null);
        vo.setCreatedAt(i.getCreatedTime() != null ? DateTimeUtils.DTF.format(i.getCreatedTime()) : null);
        vo.setUpdatedAt(i.getUpdatedTime() != null ? DateTimeUtils.DTF.format(i.getUpdatedTime()) : null);
        vo.setRemark(i.getRemark());
        return vo;
    }

    // ──────────────── generic utilities ────────────────

    /** Extracts non-null IDs from entity list using given extractor. */
    public static <T> Set<Long> ids(List<T> records, Function<T, Long> extractor) {
        Set<Long> set = new HashSet<>();
        for (T r : records) {
            Long v = extractor.apply(r);
            if (v != null) set.add(v);
        }
        return set;
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

    private <T> Map<Long, T> resolveEntities(Set<Long> ids,
                                              BaseMapper<T> mapper,
                                              Function<T, Long> idExtractor) {
        if (ids.isEmpty()) return Map.of();
        List<T> entities = mapper.selectByIds(ids);
        Map<Long, T> map = new HashMap<>();
        for (T e : entities) {
            map.put(idExtractor.apply(e), e);
        }
        return map;
    }
}
