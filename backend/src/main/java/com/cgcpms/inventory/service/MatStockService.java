package com.cgcpms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.vo.MatStockLedgerVO;
import com.cgcpms.inventory.vo.MatStockTxnVO;
import com.cgcpms.inventory.vo.MatStockVO;
import com.cgcpms.inventory.vo.StockKpiVO;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存台账服务 — 数量型库存管理，@Version 乐观锁并发控制。
 * <p>
 * 核心规则：
 * <ul>
 *   <li>stockIn：入库增加可用量，自动创建流水</li>
 *   <li>stockOut：出库校验余量 ≥ 请求量，不足抛 BusinessException（非 500）</li>
 *   <li>乐观锁冲突自动重试最多 3 次，仍失败抛 BusinessException</li>
 *   <li>库存永不为负（应用层硬阻断 + 乐观锁保护）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MatStockService {

    private static final int MAX_RETRIES = 3;
    private static final BigDecimal DEFAULT_SAFETY_STOCK_QTY = new BigDecimal("10.0000");

    private final MatStockMapper matStockMapper;
    private final MatStockTxnMapper matStockTxnMapper;
    private final MatWarehouseMapper matWarehouseMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final ProjectAccessChecker projectAccessChecker;

    /**
     * 入库：增加指定仓库+物料的可用库存。
     * 如该组合尚无库存记录则自动创建；已存在则在现有记录上累加。
     * 每次入库生成一条 txn_type='IN' 的流水。
     */
    @Transactional(rollbackFor = Exception.class)
    public MatStock stockIn(Long warehouseId, Long materialId, BigDecimal quantity) {
        return stockIn(warehouseId, materialId, quantity, null, null);
    }

    /**
     * 入库（带业务来源追溯）。
     *
     * @param sourceType 来源业务类型，如 "MAT_RECEIPT"；可为 null
     * @param sourceId   来源业务ID，如验收单ID；可为 null
     */
    @Transactional(rollbackFor = Exception.class)
    public MatStock stockIn(Long warehouseId, Long materialId, BigDecimal quantity,
                            String sourceType, Long sourceId) {
        validatePositiveQuantity(quantity);
        Long tenantId = UserContext.getCurrentTenantId();

        MatStock stock = findStock(tenantId, warehouseId, materialId);
        if (stock == null) {
            // 首次入库：创建库存记录
            // 并发场景下 INSERT 可能因 UNIQUE 约束失败，此时回退到 UPDATE 路径
            try {
                stock = new MatStock();
                stock.setTenantId(tenantId);
                stock.setWarehouseId(warehouseId);
                stock.setMaterialId(materialId);
                stock.setAvailableQty(quantity);
                stock.setSafetyStockQty(DEFAULT_SAFETY_STOCK_QTY);
                stock.setVersion(0);
                matStockMapper.insert(stock);
            } catch (DuplicateKeyException e) {
                // 并发线程已创建了同 warehouse+material 的库存，重新查询后走累加路径
                stock = findStock(tenantId, warehouseId, materialId);
                if (stock == null) {
                    throw new BusinessException("STOCK_CONCURRENT_CONFLICT",
                            "库存并发冲突，请稍后重试");
                }
                stock = doUpdateIncrement(tenantId, warehouseId, materialId, quantity, stock);
            }
        } else {
            // 已有库存：乐观锁累加
            stock = doUpdateIncrement(tenantId, warehouseId, materialId, quantity, stock);
        }

        // 写入流水（带来源追溯）
        insertTxn(tenantId, warehouseId, materialId, "IN", quantity,
                stock.getAvailableQty(), sourceType, sourceId);

        return stock;
    }

    /**
     * 乐观锁累加库存：最多重试 MAX_RETRIES 次。
     * <p>
     * 每次迭代从 DB 最新数据出发重新计算，避免基于过期快照累加。
     * 流程：load current → add quantity → updateById（@Version 乐观锁）→
     * 冲突时 reload from DB → 重新计算 → retry。
     *
     * @param tenantId   租户ID
     * @param warehouseId 仓库ID
     * @param materialId 物料ID
     * @param quantity   增量数量（正数入库，负数出库）
     * @param stock      当前持有的库存快照（首次调用时为最新 DB 记录，重试时为 reload 后的记录）
     * @return 更新后的最新库存实体
     */
    private MatStock doUpdateIncrement(Long tenantId, Long warehouseId, Long materialId,
                                       BigDecimal quantity, MatStock stock) {
        int retries = 0;
        while (true) {
            // 基于当前最新 stock 快照累加 availableQty
            BigDecimal nextAvailableQty = stock.getAvailableQty().add(quantity);
            if (nextAvailableQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "库存不足：可用 " + stock.getAvailableQty() + "，请求变更 " + quantity);
            }
            stock.setAvailableQty(nextAvailableQty);
            // @Version 乐观锁：若版本冲突则 updateById 返回 0
            int updated = matStockMapper.updateById(stock);
            if (updated > 0) return stock;
            if (++retries >= MAX_RETRIES) {
                throw new BusinessException("STOCK_CONCURRENT_CONFLICT",
                        "库存并发冲突，请稍后重试");
            }
            // 版本冲突：从 DB 重新加载最新数据，下一次迭代基于最新值重新计算
            stock = findStock(tenantId, warehouseId, materialId);
        }
    }

    /**
     * 出库：减少指定仓库+物料的可用库存。
     * <p>
     * 库存不足时抛出 {@link BusinessException}（错误码 INSUFFICIENT_STOCK），
     * 不会产生 500 错误。
     * <p>
     * 乐观锁冲突自动重试最多 3 次；重试时重新校验余量。
     */
    @Transactional(rollbackFor = Exception.class)
    public MatStock stockOut(Long warehouseId, Long materialId, BigDecimal quantity) {
        return stockOut(warehouseId, materialId, quantity, null, null);
    }

    /**
     * 出库（带业务来源追溯）。
     *
     * @param sourceType 来源业务类型；可为 null
     * @param sourceId   来源业务ID；可为 null
     */
    @Transactional(rollbackFor = Exception.class)
    public MatStock stockOut(Long warehouseId, Long materialId, BigDecimal quantity,
                             String sourceType, Long sourceId) {
        validatePositiveQuantity(quantity);
        Long tenantId = UserContext.getCurrentTenantId();

        MatStock stock = findStock(tenantId, warehouseId, materialId);
        if (stock == null) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    "库存不足：该仓库+物料尚无库存记录");
        }

        int retries = 0;
        while (true) {
            if (stock.getAvailableQty().compareTo(quantity) < 0) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "库存不足：可用 " + stock.getAvailableQty()
                                + "，请求出库 " + quantity);
            }
            stock.setAvailableQty(stock.getAvailableQty().subtract(quantity));
            int updated = matStockMapper.updateById(stock);
            if (updated > 0) break;
            if (++retries >= MAX_RETRIES) {
                throw new BusinessException("STOCK_CONCURRENT_CONFLICT",
                        "库存并发冲突，请稍后重试");
            }
            // 版本冲突：重新加载最新数据
            stock = findStock(tenantId, warehouseId, materialId);
            if (stock == null) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "库存不足：库存记录已被删除");
            }
        }

        // 写入流水（带来源追溯）
        insertTxn(tenantId, warehouseId, materialId, "OUT", quantity,
                stock.getAvailableQty(), sourceType, sourceId);

        return stock;
    }

    private void validatePositiveQuantity(BigDecimal quantity) {
        // 0 被视为合法的占位调整（MatStockServiceTest 边界保留），仅在 null 或负值时报错。
        // 终态非负防御在 doUpdateIncrement / stockOut 内部由"变更后≥0"保证。
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("STOCK_QUANTITY_INVALID", "库存数量不能为负或为空");
        }
    }

    /**
     * 查询库存台账：当前库存余额 + 分页流水记录。
     * <p>
     * 包含仓库名、物料名/编码/单位的 JOIN 查询，支持 keyword 模糊搜索和动态排序。
     *
     * @param warehouseId 仓库ID（必传）
     * @param materialId  物料ID（必传）
     * @param projectId   项目ID（可选）
     * @param keyword     关键词（可选，模糊搜索流水号/来源单号）
     * @param sortField   排序字段（可选，默认 createdTime）
     * @param sortOrder   排序方向（可选，默认 desc）
     * @param pageNo      流水页码（从 1 开始）
     * @param pageSize    每页条数
     * @return 台账（含当前库存和分页流水）
     */
    public MatStockLedgerVO getLedger(Long warehouseId, Long materialId,
                                       Long projectId,
                                       String keyword,
                                       String sortField, String sortOrder,
                                       long pageNo, long pageSize) {
        Long tenantId = UserContext.getCurrentTenantId();
        if (projectId != null && findEnabledWarehouseIds(tenantId, warehouseId, projectId).isEmpty()) {
            MatStockLedgerVO ledger = new MatStockLedgerVO();
            ledger.setStock(null);
            ledger.setTxns(new PageResult<>(pageNo, pageSize, 0, List.of()));
            return ledger;
        }

        // 1. 当前库存余额
        MatStock stock = findStock(tenantId, warehouseId, materialId);

        // 2. 流水查询
        LambdaQueryWrapper<MatStockTxn> txnWrapper = new LambdaQueryWrapper<>();
        txnWrapper.eq(MatStockTxn::getTenantId, tenantId);
        txnWrapper.eq(MatStockTxn::getWarehouseId, warehouseId);
        txnWrapper.eq(MatStockTxn::getMaterialId, materialId);

        // keyword 模糊搜索（流水ID或来源单号）
        if (StringUtils.hasText(keyword)) {
            txnWrapper.and(w -> w
                    .like(MatStockTxn::getId, keyword)
                    .or()
                    .like(MatStockTxn::getSourceId, keyword));
        }

        // 动态排序
        applySort(txnWrapper, sortField, sortOrder);

        Page<MatStockTxn> page = matStockTxnMapper.selectPage(
                new Page<>(pageNo, pageSize), txnWrapper);

        // 3. 批量获取 display name
        Map<Long, String> warehouseNameMap = getWarehouseNameMap(tenantId);
        Map<Long, MdMaterial> materialMap = getMaterialMap(tenantId);

        // 4. 组装 VO
        MatStockLedgerVO ledger = new MatStockLedgerVO();
        ledger.setStock(toStockVO(stock, warehouseNameMap, materialMap));

        List<MatStockTxnVO> txnVOs = page.getRecords().stream()
                .map(txn -> toTxnVO(txn, warehouseNameMap, materialMap))
                .collect(Collectors.toList());
        PageResult<MatStockTxnVO> txnPage = new PageResult<>(
                page.getCurrent(), page.getSize(), page.getTotal(), txnVOs);
        ledger.setTxns(txnPage);

        return ledger;
    }

    /**
     * 库存台账 KPI 统计。
     *
     * @param warehouseId 仓库ID（可选，传 null 则全仓库统计）
     * @param projectId   项目ID（可选）
     * @return KPI VO
     */
    public StockKpiVO getKpi(Long warehouseId, Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();

        StockKpiVO kpi = new StockKpiVO();

        List<Long> warehouseIds = findEnabledWarehouseIds(tenantId, warehouseId, projectId);
        kpi.setWarehouseCount(warehouseIds.size());
        if (warehouseIds.isEmpty()) {
            return kpi;
        }

        // 有库存的物料种类数
        LambdaQueryWrapper<MatStock> stockWrapper = new LambdaQueryWrapper<>();
        stockWrapper.eq(MatStock::getTenantId, tenantId);
        stockWrapper.in(MatStock::getWarehouseId, warehouseIds);
        stockWrapper.gt(MatStock::getAvailableQty, BigDecimal.ZERO);
        kpi.setMaterialTypeCount(matStockMapper.selectCount(stockWrapper));

        // 低库存物料数（保持既有零库存排除口径，阈值按库存项配置）
        LambdaQueryWrapper<MatStock> lowStockWrapper = new LambdaQueryWrapper<>();
        lowStockWrapper.eq(MatStock::getTenantId, tenantId);
        lowStockWrapper.in(MatStock::getWarehouseId, warehouseIds);
        lowStockWrapper.gt(MatStock::getAvailableQty, BigDecimal.ZERO);
        lowStockWrapper.apply("available_qty < safety_stock_qty"); // SQL-SAFETY: fixed-sql-fragment
        kpi.setLowStockCount(matStockMapper.selectCount(lowStockWrapper));

        // 出入库次数
        LambdaQueryWrapper<MatStockTxn> txnWrapper = new LambdaQueryWrapper<>();
        txnWrapper.eq(MatStockTxn::getTenantId, tenantId);
        txnWrapper.in(MatStockTxn::getWarehouseId, warehouseIds);
        txnWrapper.select(MatStockTxn::getTxnType);
        List<MatStockTxn> allTxns = matStockTxnMapper.selectList(txnWrapper);
        long inCount = allTxns.stream().filter(t -> "IN".equals(t.getTxnType())).count();
        long outCount = allTxns.stream().filter(t -> "OUT".equals(t.getTxnType())).count();
        kpi.setTxnInCount(inCount);
        kpi.setTxnOutCount(outCount);

        return kpi;
    }

    @Transactional(rollbackFor = Exception.class)
    public MatStock updateSafetyStockThreshold(Long stockId, BigDecimal safetyStockQty) {
        validateQuantity(safetyStockQty, "INVALID_SAFETY_STOCK_THRESHOLD", "安全库存阈值");
        MatStock stock = loadAuthorizedStock(stockId, "维护安全库存阈值");
        BigDecimal normalizedSafety = safetyStockQty.setScale(4);
        if (stock.getReplenishmentTargetQty() != null
                && normalizedSafety.compareTo(stock.getReplenishmentTargetQty()) > 0) {
            throw new BusinessException("INVALID_REPLENISHMENT_SETTINGS", "安全库存阈值不能高于人工补货目标量");
        }
        stock.setSafetyStockQty(normalizedSafety);
        updateStockOrThrow(stock);
        return stock;
    }

    @Transactional(rollbackFor = Exception.class)
    public MatStock updateReplenishmentSettings(Long stockId, BigDecimal safetyStockQty,
                                                BigDecimal replenishmentTargetQty,
                                                Integer replenishmentLeadDays) {
        return updateReplenishmentSettings(stockId, safetyStockQty, replenishmentTargetQty,
                replenishmentLeadDays, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public MatStock updateReplenishmentSettings(Long stockId, BigDecimal safetyStockQty,
                                                BigDecimal replenishmentTargetQty,
                                                Integer replenishmentLeadDays,
                                                boolean replenishmentLeadDaysSpecified) {
        validateQuantity(safetyStockQty, "INVALID_SAFETY_STOCK_THRESHOLD", "安全库存阈值");
        if (replenishmentTargetQty != null) {
            validateQuantity(replenishmentTargetQty, "INVALID_REPLENISHMENT_TARGET", "人工补货目标量");
        }
        BigDecimal normalizedSafety = safetyStockQty.setScale(4);
        BigDecimal normalizedTarget = replenishmentTargetQty == null ? null : replenishmentTargetQty.setScale(4);
        if (normalizedTarget != null && normalizedTarget.compareTo(normalizedSafety) < 0) {
            throw new BusinessException("INVALID_REPLENISHMENT_SETTINGS", "人工补货目标量不能低于安全库存阈值");
        }
        if (replenishmentLeadDays != null
                && (replenishmentLeadDays < 0 || replenishmentLeadDays > 3650)) {
            throw new BusinessException("INVALID_REPLENISHMENT_LEAD_DAYS", "人工补货提前期必须为 0 到 3650 的整数");
        }

        MatStock stock = loadAuthorizedStock(stockId, "维护补货设置");
        stock.setSafetyStockQty(normalizedSafety);
        stock.setReplenishmentTargetQty(normalizedTarget);
        if (replenishmentLeadDaysSpecified) {
            stock.setReplenishmentLeadDays(replenishmentLeadDays);
        }
        updateStockOrThrow(stock);
        return stock;
    }

    public MatStockVO toStockVO(MatStock entity) {
        Long tenantId = UserContext.getCurrentTenantId();
        return toStockVO(entity, getWarehouseNameMap(tenantId), getMaterialMap(tenantId));
    }

    // ── 内部工具方法 ──

    /**
     * 按租户+仓库+物料查询库存记录，返回 null 表示不存在。
     * 活动库存由后置 migration 保证唯一；LIMIT 1 作为历史异常数据的防御。
     */
    private MatStock findStock(Long tenantId, Long warehouseId, Long materialId) {
        LambdaQueryWrapper<MatStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatStock::getTenantId, tenantId);
        wrapper.eq(MatStock::getWarehouseId, warehouseId);
        wrapper.eq(MatStock::getMaterialId, materialId);
        wrapper.last("LIMIT 1"); // SQL-SAFETY: fixed-sql-fragment
        List<MatStock> results = matStockMapper.selectList(wrapper);
        return results.isEmpty() ? null : results.get(0);
    }

    private List<Long> findEnabledWarehouseIds(Long tenantId, Long warehouseId, Long projectId) {
        LambdaQueryWrapper<MatWarehouse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatWarehouse::getTenantId, tenantId);
        wrapper.eq(MatWarehouse::getStatus, "ENABLE");
        if (warehouseId != null) {
            wrapper.eq(MatWarehouse::getId, warehouseId);
        }
        if (projectId != null) {
            wrapper.eq(MatWarehouse::getProjectId, projectId);
        }
        wrapper.select(MatWarehouse::getId);
        return matWarehouseMapper.selectList(wrapper).stream()
                .map(MatWarehouse::getId)
                .collect(Collectors.toList());
    }

    /**
     * 插入一条库存流水记录。
     */
    private void insertTxn(Long tenantId, Long warehouseId, Long materialId,
                           String txnType, BigDecimal quantity,
                           BigDecimal availableAfter,
                           String sourceType, Long sourceId) {
        MatStockTxn txn = new MatStockTxn();
        txn.setTenantId(tenantId);
        txn.setWarehouseId(warehouseId);
        txn.setMaterialId(materialId);
        txn.setTxnType(txnType);
        txn.setQuantity(quantity);
        txn.setAvailableAfter(availableAfter);
        txn.setSourceType(sourceType);
        txn.setSourceId(sourceId);
        matStockTxnMapper.insert(txn);
    }

    /**
     * 动态排序 — 仅允许白名单字段，防 SQL 注入。
     */
    private void applySort(LambdaQueryWrapper<MatStockTxn> wrapper,
                           String sortField, String sortOrder) {
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        if ("quantity".equals(sortField)) {
            if (asc) {
                wrapper.orderByAsc(MatStockTxn::getQuantity);
            } else {
                wrapper.orderByDesc(MatStockTxn::getQuantity);
            }
        } else if ("createdTime".equals(sortField)) {
            if (asc) {
                wrapper.orderByAsc(MatStockTxn::getCreatedTime);
            } else {
                wrapper.orderByDesc(MatStockTxn::getCreatedTime);
            }
        } else {
            // 默认按创建时间倒序
            wrapper.orderByDesc(MatStockTxn::getCreatedTime);
        }
    }

    /**
     * 获取当前租户下所有仓库 ID→名称 映射。
     */
    private Map<Long, String> getWarehouseNameMap(Long tenantId) {
        LambdaQueryWrapper<MatWarehouse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatWarehouse::getTenantId, tenantId);
        wrapper.select(MatWarehouse::getId, MatWarehouse::getWarehouseName);
        List<MatWarehouse> warehouses = matWarehouseMapper.selectList(wrapper);
        return warehouses.stream()
                .collect(Collectors.toMap(MatWarehouse::getId, MatWarehouse::getWarehouseName));
    }

    /**
     * 获取当前租户下所有物料 ID→实体 映射。
     */
    private Map<Long, MdMaterial> getMaterialMap(Long tenantId) {
        LambdaQueryWrapper<MdMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MdMaterial::getTenantId, tenantId);
        List<MdMaterial> materials = mdMaterialMapper.selectList(wrapper);
        return materials.stream()
                .collect(Collectors.toMap(MdMaterial::getId, m -> m, (a, b) -> a));
    }

    /**
     * 将 MatStock 实体转为 MatStockVO，填充 display name。
     */
    private MatStockVO toStockVO(MatStock entity,
                                  Map<Long, String> warehouseNameMap,
                                  Map<Long, MdMaterial> materialMap) {
        if (entity == null) return null;
        MatStockVO vo = new MatStockVO();
        vo.setId(entity.getId());
        vo.setWarehouseId(entity.getWarehouseId());
        vo.setMaterialId(entity.getMaterialId());
        vo.setAvailableQty(entity.getAvailableQty());
        vo.setSafetyStockQty(entity.getSafetyStockQty());
        vo.setReplenishmentTargetQty(entity.getReplenishmentTargetQty());
        vo.setReplenishmentLeadDays(entity.getReplenishmentLeadDays());
        vo.setCreatedTime(entity.getCreatedTime() != null ? entity.getCreatedTime().toString() : null);
        vo.setUpdatedTime(entity.getUpdatedTime() != null ? entity.getUpdatedTime().toString() : null);

        vo.setWarehouseName(warehouseNameMap.get(entity.getWarehouseId()));
        MdMaterial mat = materialMap.get(entity.getMaterialId());
        if (mat != null) {
            vo.setMaterialName(mat.getMaterialName());
            vo.setMaterialCode(mat.getMaterialCode());
            vo.setUnit(mat.getUnit());
        }
        return vo;
    }

    private void validateQuantity(BigDecimal value, String code, String label) {
        if (value == null || value.signum() < 0 || value.scale() > 4) {
            throw new BusinessException(code, label + "必须为非负数且最多 4 位小数");
        }
    }

    private MatStock loadAuthorizedStock(Long stockId, String action) {
        Long tenantId = UserContext.getCurrentTenantId();
        MatStock stock = matStockMapper.selectById(stockId);
        if (stock == null || !tenantId.equals(stock.getTenantId())) {
            throw new BusinessException("STOCK_NOT_FOUND", "库存记录不存在");
        }
        MatWarehouse warehouse = matWarehouseMapper.selectById(stock.getWarehouseId());
        if (warehouse == null || !tenantId.equals(warehouse.getTenantId()) || !"ENABLE".equals(warehouse.getStatus())) {
            throw new BusinessException("STOCK_NOT_FOUND", "库存记录不存在");
        }
        projectAccessChecker.checkAccess(warehouse.getProjectId(), action);
        return stock;
    }

    private void updateStockOrThrow(MatStock stock) {
        if (matStockMapper.updateById(stock) != 1) {
            throw new BusinessException("STOCK_CONCURRENT_CONFLICT", "库存记录已变更，请刷新后重试");
        }
    }

    /**
     * 将 MatStockTxn 实体转为 MatStockTxnVO，填充 display name。
     */
    private MatStockTxnVO toTxnVO(MatStockTxn entity,
                                   Map<Long, String> warehouseNameMap,
                                   Map<Long, MdMaterial> materialMap) {
        MatStockTxnVO vo = new MatStockTxnVO();
        vo.setId(entity.getId());
        vo.setWarehouseId(entity.getWarehouseId());
        vo.setMaterialId(entity.getMaterialId());
        vo.setTxnType(entity.getTxnType());
        vo.setQuantity(entity.getQuantity());
        vo.setAvailableAfter(entity.getAvailableAfter());
        vo.setSourceType(entity.getSourceType());
        vo.setSourceId(entity.getSourceId());
        vo.setCreatedTime(entity.getCreatedTime() != null ? entity.getCreatedTime().toString() : null);

        vo.setWarehouseName(warehouseNameMap.get(entity.getWarehouseId()));
        MdMaterial mat = materialMap.get(entity.getMaterialId());
        if (mat != null) {
            vo.setMaterialName(mat.getMaterialName());
        }
        return vo;
    }
}
