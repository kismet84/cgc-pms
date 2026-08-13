package com.cgcpms.inventory.service;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.common.service.BusinessReferenceService;
import com.cgcpms.common.util.BigDecimalUtils;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.vo.MatStockLedgerVO;
import com.cgcpms.inventory.vo.MatStockTxnVO;
import com.cgcpms.inventory.vo.MatStockVO;
import com.cgcpms.inventory.vo.StockConsumptionBaselineVO;
import com.cgcpms.inventory.vo.StockIncomingSupplyVO;
import com.cgcpms.inventory.vo.StockKpiVO;
import com.cgcpms.inventory.vo.StockTransferCandidateVO;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class MatStockReadOperations {

    private final MatStockMapper matStockMapper;
    private final MatStockTxnMapper matStockTxnMapper;
    private final MatWarehouseMapper matWarehouseMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final MatPurchaseOrderMapper matPurchaseOrderMapper;
    private final MatPurchaseOrderItemMapper matPurchaseOrderItemMapper;
    private final BusinessReferenceService businessReferenceService;

    MatStockReadOperations(MatStockMapper matStockMapper,
                           MatStockTxnMapper matStockTxnMapper,
                           MatWarehouseMapper matWarehouseMapper,
                           MdMaterialMapper mdMaterialMapper,
                           ProjectAccessChecker projectAccessChecker,
                           MatPurchaseOrderMapper matPurchaseOrderMapper,
                           MatPurchaseOrderItemMapper matPurchaseOrderItemMapper,
                           BusinessReferenceService businessReferenceService) {
        this.matStockMapper = matStockMapper;
        this.matStockTxnMapper = matStockTxnMapper;
        this.matWarehouseMapper = matWarehouseMapper;
        this.mdMaterialMapper = mdMaterialMapper;
        this.projectAccessChecker = projectAccessChecker;
        this.matPurchaseOrderMapper = matPurchaseOrderMapper;
        this.matPurchaseOrderItemMapper = matPurchaseOrderItemMapper;
        this.businessReferenceService = businessReferenceService;
    }

    PageResult<MatStockVO> getPage(Long warehouseId, Long materialId,
                                   Long projectId, String keyword,
                                   long pageNo, long pageSize) {
        Long tenantId = UserContext.getCurrentTenantId();
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查询库存台账");
        }
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.min(200, Math.max(1, pageSize));
        List<Long> warehouseIds = findEnabledWarehouseIds(tenantId, warehouseId, projectId);
        if (warehouseIds.isEmpty()) {
            return new PageResult<>(safePageNo, safePageSize, 0, List.of());
        }

        List<Long> keywordMaterialIds = null;
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            keywordMaterialIds = mdMaterialMapper.selectList(new LambdaQueryWrapper<MdMaterial>()
                            .eq(MdMaterial::getTenantId, tenantId)
                            .and(wrapper -> wrapper
                                    .like(MdMaterial::getMaterialCode, normalized)
                                    .or()
                                    .like(MdMaterial::getMaterialName, normalized)
                                    .or()
                                    .like(MdMaterial::getSpecification, normalized)))
                    .stream()
                    .map(MdMaterial::getId)
                    .toList();
            if (keywordMaterialIds.isEmpty()) {
                return new PageResult<>(safePageNo, safePageSize, 0, List.of());
            }
        }

        LambdaQueryWrapper<MatStock> wrapper = new LambdaQueryWrapper<MatStock>()
                .eq(MatStock::getTenantId, tenantId)
                .in(MatStock::getWarehouseId, warehouseIds);
        if (materialId != null) wrapper.eq(MatStock::getMaterialId, materialId);
        if (keywordMaterialIds != null) wrapper.in(MatStock::getMaterialId, keywordMaterialIds);
        wrapper.orderByDesc(MatStock::getUpdatedTime).orderByAsc(MatStock::getId);

        Page<MatStock> page = matStockMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        Map<Long, MatWarehouse> warehouseMap = getWarehouseMap(tenantId);
        Map<Long, MdMaterial> materialMap = getMaterialMap(tenantId);
        Map<Long, String> projectNameMap = getProjectNameMap();
        List<MatStockVO> records = page.getRecords().stream()
                .map(stock -> toStockVO(stock, warehouseMap, materialMap, projectNameMap))
                .toList();
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    MatStockLedgerVO getLedger(Long warehouseId, Long materialId, Long projectId,
                                String keyword, String sortField, String sortOrder,
                                long pageNo, long pageSize) {
        Long tenantId = UserContext.getCurrentTenantId();
        if (projectId != null) projectAccessChecker.checkAccess(projectId, "查询库存台账");
        List<Long> warehouseIds = findEnabledWarehouseIds(tenantId, warehouseId, projectId);
        if (warehouseIds.isEmpty()) {
            MatStockLedgerVO ledger = new MatStockLedgerVO();
            ledger.setStock(null);
            ledger.setTxns(new PageResult<>(pageNo, pageSize, 0, List.of()));
            return ledger;
        }

        List<MatStock> stocks = matStockMapper.selectList(new LambdaQueryWrapper<MatStock>()
                .eq(MatStock::getTenantId, tenantId)
                .in(MatStock::getWarehouseId, warehouseIds)
                .eq(MatStock::getMaterialId, materialId));
        LambdaQueryWrapper<MatStockTxn> txnWrapper = new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getTenantId, tenantId)
                .in(MatStockTxn::getWarehouseId, warehouseIds)
                .eq(MatStockTxn::getMaterialId, materialId);
        if (StringUtils.hasText(keyword)) {
            txnWrapper.and(w -> w.like(MatStockTxn::getId, keyword).or().like(MatStockTxn::getSourceId, keyword));
        }
        applySort(txnWrapper, sortField, sortOrder);
        Page<MatStockTxn> page = matStockTxnMapper.selectPage(new Page<>(pageNo, pageSize), txnWrapper);

        Map<Long, MatWarehouse> warehouseMap = getWarehouseMap(tenantId);
        Map<Long, String> warehouseNameMap = warehouseMap.values().stream()
                .collect(Collectors.toMap(MatWarehouse::getId, MatWarehouse::getWarehouseName));
        Map<Long, MdMaterial> materialMap = getMaterialMap(tenantId);
        Map<Long, String> projectNameMap = getProjectNameMap();
        MatStockLedgerVO ledger = new MatStockLedgerVO();
        ledger.setStock(warehouseId == null
                ? aggregateStock(stocks, materialId, materialMap)
                : toStockVO(stocks.isEmpty() ? null : stocks.getFirst(),
                        warehouseMap, materialMap, projectNameMap));

        Map<String, Map<Long, String>> sourceCodes = page.getRecords().stream()
                .filter(txn -> txn.getSourceType() != null && txn.getSourceId() != null)
                .collect(Collectors.groupingBy(
                        MatStockTxn::getSourceType,
                        Collectors.mapping(MatStockTxn::getSourceId, Collectors.toSet())))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> businessReferenceService.resolve(entry.getKey(), entry.getValue())));
        List<MatStockTxnVO> txnVOs = page.getRecords().stream()
                .map(txn -> toTxnVO(txn, warehouseNameMap, materialMap,
                        sourceCode(sourceCodes, txn.getSourceType(), txn.getSourceId())))
                .collect(Collectors.toList());
        ledger.setTxns(new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), txnVOs));
        return ledger;
    }

    StockKpiVO getKpi(Long warehouseId, Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();
        if (projectId != null) projectAccessChecker.checkAccess(projectId, "查询库存指标");
        StockKpiVO kpi = new StockKpiVO();
        List<Long> warehouseIds = findEnabledWarehouseIds(tenantId, warehouseId, projectId);
        kpi.setWarehouseCount(warehouseIds.size());
        if (warehouseIds.isEmpty()) return kpi;

        LambdaQueryWrapper<MatStock> stockWrapper = new LambdaQueryWrapper<MatStock>()
                .eq(MatStock::getTenantId, tenantId)
                .in(MatStock::getWarehouseId, warehouseIds)
                .gt(MatStock::getAvailableQty, BigDecimal.ZERO);
        kpi.setMaterialTypeCount(matStockMapper.selectCount(stockWrapper));
        LambdaQueryWrapper<MatStock> lowStockWrapper = new LambdaQueryWrapper<MatStock>()
                .eq(MatStock::getTenantId, tenantId)
                .in(MatStock::getWarehouseId, warehouseIds)
                .gt(MatStock::getAvailableQty, BigDecimal.ZERO)
                .apply("available_qty < safety_stock_qty"); // SQL-SAFETY: fixed-sql-fragment
        kpi.setLowStockCount(matStockMapper.selectCount(lowStockWrapper));
        List<MatStockTxn> allTxns = matStockTxnMapper.selectList(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getTenantId, tenantId)
                .in(MatStockTxn::getWarehouseId, warehouseIds)
                .select(MatStockTxn::getTxnType));
        kpi.setTxnInCount(allTxns.stream().filter(t -> "IN".equals(t.getTxnType())).count());
        kpi.setTxnOutCount(allTxns.stream().filter(t -> "OUT".equals(t.getTxnType())).count());
        return kpi;
    }

    List<StockTransferCandidateVO> getTransferCandidates(MatStock currentStock, MatWarehouse currentWarehouse) {
        Long tenantId = UserContext.getCurrentTenantId();
        List<MatWarehouse> warehouses = matWarehouseMapper.selectList(new LambdaQueryWrapper<MatWarehouse>()
                .eq(MatWarehouse::getTenantId, tenantId)
                .eq(MatWarehouse::getProjectId, currentWarehouse.getProjectId())
                .eq(MatWarehouse::getStatus, "ENABLE")
                .ne(MatWarehouse::getId, currentWarehouse.getId()));
        if (warehouses.isEmpty()) return List.of();
        Map<Long, MatWarehouse> warehouseMap = warehouses.stream()
                .collect(Collectors.toMap(MatWarehouse::getId, warehouse -> warehouse));
        List<MatStock> stocks = matStockMapper.selectList(new LambdaQueryWrapper<MatStock>()
                .eq(MatStock::getTenantId, tenantId)
                .eq(MatStock::getMaterialId, currentStock.getMaterialId())
                .in(MatStock::getWarehouseId, warehouseMap.keySet()));
        return stocks.stream()
                .map(stock -> toTransferCandidate(stock, warehouseMap.get(stock.getWarehouseId())))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(StockTransferCandidateVO::getTransferableQty).reversed()
                        .thenComparing(StockTransferCandidateVO::getWarehouseId))
                .collect(Collectors.toList());
    }

    StockConsumptionBaselineVO getConsumptionBaseline(MatStock currentStock) {
        Long tenantId = UserContext.getCurrentTenantId();
        LocalDateTime cutoffAt = LocalDateTime.now();
        LocalDateTime window30Start = cutoffAt.toLocalDate().minusDays(29).atStartOfDay();
        LocalDateTime window90Start = cutoffAt.toLocalDate().minusDays(89).atStartOfDay();
        StockConsumptionBaselineVO baseline = matStockTxnMapper.selectConsumptionBaseline(
                tenantId, currentStock.getWarehouseId(), currentStock.getMaterialId(),
                window30Start, window90Start, cutoffAt);
        if (baseline == null) baseline = new StockConsumptionBaselineVO();
        BigDecimal issued30 = scaleQuantity(baseline.getGrossIssued30());
        BigDecimal returned30 = scaleQuantity(baseline.getReturned30());
        BigDecimal issued90 = scaleQuantity(baseline.getGrossIssued90());
        BigDecimal returned90 = scaleQuantity(baseline.getReturned90());
        baseline.setWindow30Start(window30Start.toLocalDate());
        baseline.setWindow90Start(window90Start.toLocalDate());
        baseline.setCutoffAt(cutoffAt);
        baseline.setGrossIssued30(issued30);
        baseline.setReturned30(returned30);
        baseline.setNetIssued30(issued30.subtract(returned30));
        baseline.setGrossIssued90(issued90);
        baseline.setReturned90(returned90);
        baseline.setNetIssued90(issued90.subtract(returned90));
        return baseline;
    }

    List<StockIncomingSupplyVO> getIncomingSupplies(MatStock currentStock, MatWarehouse currentWarehouse) {
        Long tenantId = UserContext.getCurrentTenantId();
        List<MatPurchaseOrder> orders = matPurchaseOrderMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getTenantId, tenantId)
                        .eq(MatPurchaseOrder::getProjectId, currentWarehouse.getProjectId())
                        .eq(MatPurchaseOrder::getApprovalStatus, "APPROVED")
                        .in(MatPurchaseOrder::getOrderStatus, List.of("PERFORMING", "PARTIAL_RECEIVED"))
                        .isNotNull(MatPurchaseOrder::getDeliveryDate)
                        .orderByAsc(MatPurchaseOrder::getDeliveryDate)
                        .orderByAsc(MatPurchaseOrder::getId));
        if (orders.isEmpty()) return List.of();
        Set<Long> orderIds = orders.stream().map(MatPurchaseOrder::getId).collect(Collectors.toSet());
        List<MatPurchaseOrderItem> items = matPurchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getTenantId, tenantId)
                        .eq(MatPurchaseOrderItem::getMaterialId, currentStock.getMaterialId())
                        .in(MatPurchaseOrderItem::getOrderId, orderIds));
        Map<Long, BigDecimal> remainingByOrder = new HashMap<>();
        for (MatPurchaseOrderItem item : items) {
            BigDecimal remaining = nvl(item.getQuantity()).subtract(nvl(item.getReceivedQuantity())).max(BigDecimal.ZERO);
            remainingByOrder.merge(item.getOrderId(), remaining, BigDecimal::add);
        }
        return orders.stream()
                .map(order -> toIncomingSupply(order, remainingByOrder.get(order.getId())))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    MatStockVO toStockVO(MatStock entity) {
        Long tenantId = UserContext.getCurrentTenantId();
        return toStockVO(entity, getWarehouseMap(tenantId), getMaterialMap(tenantId), getProjectNameMap());
    }

    private MatStockVO aggregateStock(List<MatStock> stocks, Long materialId, Map<Long, MdMaterial> materialMap) {
        if (stocks.isEmpty()) return null;
        BigDecimal availableQty = stocks.stream().map(MatStock::getAvailableQty).map(BigDecimalUtils::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal inventoryValue = stocks.stream().map(MatStock::getInventoryValue).map(BigDecimalUtils::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal safetyStockQty = stocks.stream().map(MatStock::getSafetyStockQty).map(BigDecimalUtils::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> targets = stocks.stream().map(MatStock::getReplenishmentTargetQty)
                .filter(Objects::nonNull).toList();
        MatStockVO aggregate = new MatStockVO();
        aggregate.setMaterialId(materialId);
        aggregate.setWarehouseName("全部仓库");
        aggregate.setAvailableQty(availableQty);
        aggregate.setInventoryValue(inventoryValue);
        aggregate.setAverageUnitCost(availableQty.signum() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : inventoryValue.divide(availableQty, 2, RoundingMode.HALF_UP));
        aggregate.setSafetyStockQty(safetyStockQty);
        aggregate.setReplenishmentTargetQty(targets.isEmpty() ? null
                : targets.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        MdMaterial material = materialMap.get(materialId);
        if (material != null) {
            aggregate.setMaterialName(material.getMaterialName());
            aggregate.setMaterialCode(material.getMaterialCode());
            aggregate.setUnit(material.getUnit());
        }
        return aggregate;
    }

    private StockTransferCandidateVO toTransferCandidate(MatStock stock, MatWarehouse warehouse) {
        BigDecimal available = nvl(stock.getAvailableQty());
        BigDecimal safety = nvl(stock.getSafetyStockQty());
        BigDecimal transferable = available.subtract(safety).setScale(2, RoundingMode.HALF_UP);
        if (warehouse == null || transferable.signum() <= 0) return null;
        StockTransferCandidateVO candidate = new StockTransferCandidateVO();
        candidate.setStockId(stock.getId());
        candidate.setWarehouseId(warehouse.getId());
        candidate.setWarehouseName(warehouse.getWarehouseName());
        candidate.setAvailableQty(available.setScale(2, RoundingMode.HALF_UP));
        candidate.setSafetyStockQty(safety.setScale(2, RoundingMode.HALF_UP));
        candidate.setTransferableQty(transferable);
        return candidate;
    }

    private StockIncomingSupplyVO toIncomingSupply(MatPurchaseOrder order, BigDecimal remainingQty) {
        if (remainingQty == null || remainingQty.signum() <= 0) return null;
        StockIncomingSupplyVO supply = new StockIncomingSupplyVO();
        supply.setOrderId(order.getId());
        supply.setOrderCode(order.getOrderCode());
        supply.setDeliveryDate(order.getDeliveryDate());
        supply.setRemainingQty(remainingQty.setScale(2, RoundingMode.HALF_UP));
        return supply;
    }

    private List<Long> findEnabledWarehouseIds(Long tenantId, Long warehouseId, Long projectId) {
        List<Long> accessibleProjectIds = projectAccessChecker.accessibleProjectIds();
        if (accessibleProjectIds.isEmpty()) return List.of();
        LambdaQueryWrapper<MatWarehouse> wrapper = new LambdaQueryWrapper<MatWarehouse>()
                .eq(MatWarehouse::getTenantId, tenantId)
                .eq(MatWarehouse::getStatus, "ENABLE")
                .in(MatWarehouse::getProjectId, accessibleProjectIds);
        if (warehouseId != null) wrapper.eq(MatWarehouse::getId, warehouseId);
        if (projectId != null) wrapper.eq(MatWarehouse::getProjectId, projectId);
        wrapper.select(MatWarehouse::getId);
        return matWarehouseMapper.selectList(wrapper).stream().map(MatWarehouse::getId).collect(Collectors.toList());
    }

    private void applySort(LambdaQueryWrapper<MatStockTxn> wrapper, String sortField, String sortOrder) {
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        if ("quantity".equals(sortField)) {
            if (asc) wrapper.orderByAsc(MatStockTxn::getQuantity);
            else wrapper.orderByDesc(MatStockTxn::getQuantity);
        } else if ("createdTime".equals(sortField)) {
            if (asc) wrapper.orderByAsc(MatStockTxn::getCreatedTime).orderByAsc(MatStockTxn::getId);
            else wrapper.orderByDesc(MatStockTxn::getCreatedTime).orderByDesc(MatStockTxn::getId);
        } else {
            wrapper.orderByDesc(MatStockTxn::getCreatedTime).orderByDesc(MatStockTxn::getId);
        }
    }

    private Map<Long, MatWarehouse> getWarehouseMap(Long tenantId) {
        List<MatWarehouse> warehouses = matWarehouseMapper.selectList(new LambdaQueryWrapper<MatWarehouse>()
                .eq(MatWarehouse::getTenantId, tenantId)
                .select(MatWarehouse::getId, MatWarehouse::getProjectId, MatWarehouse::getWarehouseName));
        return warehouses.stream().collect(Collectors.toMap(MatWarehouse::getId, warehouse -> warehouse));
    }

    private Map<Long, String> getProjectNameMap() {
        return projectAccessChecker.accessibleProjects().stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName));
    }

    private Map<Long, MdMaterial> getMaterialMap(Long tenantId) {
        List<MdMaterial> materials = mdMaterialMapper.selectList(new LambdaQueryWrapper<MdMaterial>()
                .eq(MdMaterial::getTenantId, tenantId));
        return materials.stream().collect(Collectors.toMap(MdMaterial::getId, m -> m, (a, b) -> a));
    }

    private MatStockVO toStockVO(MatStock entity, Map<Long, MatWarehouse> warehouseMap,
                                  Map<Long, MdMaterial> materialMap, Map<Long, String> projectNameMap) {
        if (entity == null) return null;
        MatStockVO vo = new MatStockVO();
        vo.setId(entity.getId());
        vo.setWarehouseId(entity.getWarehouseId());
        vo.setMaterialId(entity.getMaterialId());
        vo.setAvailableQty(entity.getAvailableQty());
        vo.setInventoryValue(entity.getInventoryValue());
        vo.setAverageUnitCost(entity.getAverageUnitCost());
        vo.setSafetyStockQty(entity.getSafetyStockQty());
        vo.setReplenishmentTargetQty(entity.getReplenishmentTargetQty());
        vo.setReplenishmentLeadDays(entity.getReplenishmentLeadDays());
        vo.setCreatedTime(entity.getCreatedTime() != null ? entity.getCreatedTime().toString() : null);
        vo.setUpdatedTime(entity.getUpdatedTime() != null ? entity.getUpdatedTime().toString() : null);
        MatWarehouse warehouse = warehouseMap.get(entity.getWarehouseId());
        if (warehouse != null) {
            vo.setWarehouseName(warehouse.getWarehouseName());
            vo.setProjectId(warehouse.getProjectId());
            vo.setProjectName(projectNameMap.get(warehouse.getProjectId()));
        }
        MdMaterial material = materialMap.get(entity.getMaterialId());
        if (material != null) {
            vo.setMaterialName(material.getMaterialName());
            vo.setMaterialCode(material.getMaterialCode());
            vo.setUnit(material.getUnit());
        }
        return vo;
    }

    private MatStockTxnVO toTxnVO(MatStockTxn entity, Map<Long, String> warehouseNameMap,
                                   Map<Long, MdMaterial> materialMap, String sourceCode) {
        MatStockTxnVO vo = new MatStockTxnVO();
        vo.setId(entity.getId());
        vo.setWarehouseId(entity.getWarehouseId());
        vo.setMaterialId(entity.getMaterialId());
        vo.setTxnType(entity.getTxnType());
        vo.setQuantity(entity.getQuantity());
        vo.setAvailableAfter(entity.getAvailableAfter());
        vo.setUnitCost(entity.getUnitCost());
        vo.setAmount(entity.getAmount());
        vo.setSourceType(entity.getSourceType());
        vo.setSourceId(entity.getSourceId());
        vo.setSourceCode(sourceCode);
        vo.setSourceLineId(entity.getSourceLineId());
        vo.setCreatedTime(entity.getCreatedTime() != null ? entity.getCreatedTime().toString() : null);
        vo.setWarehouseName(warehouseNameMap.get(entity.getWarehouseId()));
        MdMaterial material = materialMap.get(entity.getMaterialId());
        if (material != null) vo.setMaterialName(material.getMaterialName());
        return vo;
    }

    private String sourceCode(Map<String, Map<Long, String>> sourceCodes, String sourceType, Long sourceId) {
        if (sourceType == null || sourceId == null) return null;
        Map<Long, String> codes = sourceCodes.get(sourceType);
        return codes == null ? null : codes.get(sourceId);
    }

    private BigDecimal scaleQuantity(BigDecimal value) {
        return nvl(value).setScale(2, RoundingMode.HALF_UP);
    }

}
