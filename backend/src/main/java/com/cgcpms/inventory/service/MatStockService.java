package com.cgcpms.inventory.service;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.common.service.BusinessReferenceService;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatStockTransfer;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatStockTransferMapper;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.vo.MatStockLedgerVO;
import com.cgcpms.inventory.vo.MatStockVO;
import com.cgcpms.inventory.vo.StockKpiVO;
import com.cgcpms.inventory.vo.StockConsumptionBaselineVO;
import com.cgcpms.inventory.vo.StockIncomingSupplyVO;
import com.cgcpms.inventory.vo.StockTransferCandidateVO;
import com.cgcpms.inventory.vo.StockTransferVO;
import com.cgcpms.inventory.dto.StockTransferDTO;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 库存台账服务 — 数量与移动加权平均价值管理，@Version 乐观锁并发控制。
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
public class MatStockService {

    private static final int MAX_RETRIES = 3;
    private static final BigDecimal DEFAULT_SAFETY_STOCK_QTY = new BigDecimal("10.00");

    private final MatStockMapper matStockMapper;
    private final MatStockTransferMapper matStockTransferMapper;
    private final MatStockTxnMapper matStockTxnMapper;
    private final MatWarehouseMapper matWarehouseMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final MatPurchaseOrderMapper matPurchaseOrderMapper;
    private final MatPurchaseOrderItemMapper matPurchaseOrderItemMapper;
    private final BusinessReferenceService businessReferenceService;
    private final MatStockReadOperations readOperations;

    public MatStockService(MatStockMapper matStockMapper,
                           MatStockTransferMapper matStockTransferMapper,
                           MatStockTxnMapper matStockTxnMapper,
                           MatWarehouseMapper matWarehouseMapper,
                           MdMaterialMapper mdMaterialMapper,
                           ProjectAccessChecker projectAccessChecker,
                           MatPurchaseOrderMapper matPurchaseOrderMapper,
                           MatPurchaseOrderItemMapper matPurchaseOrderItemMapper,
                           BusinessReferenceService businessReferenceService) {
        this.matStockMapper = matStockMapper;
        this.matStockTransferMapper = matStockTransferMapper;
        this.matStockTxnMapper = matStockTxnMapper;
        this.matWarehouseMapper = matWarehouseMapper;
        this.mdMaterialMapper = mdMaterialMapper;
        this.projectAccessChecker = projectAccessChecker;
        this.matPurchaseOrderMapper = matPurchaseOrderMapper;
        this.matPurchaseOrderItemMapper = matPurchaseOrderItemMapper;
        this.businessReferenceService = businessReferenceService;
        this.readOperations = new MatStockReadOperations(
                matStockMapper, matStockTxnMapper, matWarehouseMapper, mdMaterialMapper,
                projectAccessChecker, matPurchaseOrderMapper, matPurchaseOrderItemMapper,
                businessReferenceService);
    }

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
        return stockIn(warehouseId, materialId, quantity, sourceType, sourceId, null);
    }

    /**
     * 入库（带来源单据和来源明细）。来源明细存在时，相同业务事件重复调用不会重复增加库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public MatStock stockIn(Long warehouseId, Long materialId, BigDecimal quantity,
                            String sourceType, Long sourceId, Long sourceLineId) {
        return stockInValued(warehouseId, materialId, quantity, BigDecimal.ZERO,
                sourceType, sourceId, sourceLineId);
    }

    /** 入库并按移动加权平均法更新库存价值。 */
    @Transactional(rollbackFor = Exception.class)
    public MatStock stockInValued(Long warehouseId, Long materialId, BigDecimal quantity,
                                  BigDecimal unitCost, String sourceType, Long sourceId,
                                  Long sourceLineId) {
        quantity = normalizeQuantity(quantity);
        if (unitCost == null || unitCost.signum() < 0) {
            throw new BusinessException("STOCK_UNIT_COST_INVALID", "入库单位成本不能为负或为空");
        }
        Long tenantId = UserContext.getCurrentTenantId();

        if (findProcessedSourceLine(tenantId, "IN", sourceType, sourceId, sourceLineId) != null) {
            MatStock existing = findStock(tenantId, warehouseId, materialId);
            if (existing == null) {
                throw new BusinessException("STOCK_IDEMPOTENCY_INCONSISTENT", "入库流水已存在但库存余额不存在");
            }
            return existing;
        }

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
                BigDecimal movementValue = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
                stock.setInventoryValue(movementValue);
                stock.setAverageUnitCost(quantity.signum() == 0 ? BigDecimal.ZERO
                        : movementValue.divide(quantity, 2, RoundingMode.HALF_UP));
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
                stock = doUpdateIncrement(tenantId, warehouseId, materialId, quantity, unitCost, stock);
            }
        } else {
            // 已有库存：乐观锁累加
            stock = doUpdateIncrement(tenantId, warehouseId, materialId, quantity, unitCost, stock);
        }

        // 写入流水（带来源追溯）
        BigDecimal movementValue = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
        insertTxn(tenantId, warehouseId, materialId, "IN", quantity,
                stock.getAvailableQty(), unitCost, movementValue, sourceType, sourceId, sourceLineId, null);

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
                                       BigDecimal quantity, BigDecimal unitCost, MatStock stock) {
        int retries = 0;
        while (true) {
            // 基于当前最新 stock 快照累加 availableQty
            BigDecimal nextAvailableQty = stock.getAvailableQty().add(quantity);
            if (nextAvailableQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "库存不足：可用 " + stock.getAvailableQty() + "，请求变更 " + quantity);
            }
            BigDecimal currentValue = nvl(stock.getInventoryValue());
            BigDecimal movementValue = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal nextValue = currentValue.add(movementValue);
            stock.setAvailableQty(nextAvailableQty);
            stock.setInventoryValue(nextValue);
            stock.setAverageUnitCost(nextAvailableQty.signum() == 0 ? BigDecimal.ZERO
                    : nextValue.divide(nextAvailableQty, 2, RoundingMode.HALF_UP));
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
        return stockOut(warehouseId, materialId, quantity, sourceType, sourceId, null);
    }

    /**
     * 出库（带来源单据和来源明细）。来源明细存在时，相同业务事件重复调用不会重复扣减库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public MatStock stockOut(Long warehouseId, Long materialId, BigDecimal quantity,
                             String sourceType, Long sourceId, Long sourceLineId) {
        return stockOutValued(warehouseId, materialId, quantity, sourceType, sourceId, sourceLineId).stock();
    }

    /** 出库并返回按移动加权平均法计算的本次出库价值。 */
    @Transactional(rollbackFor = Exception.class)
    public StockMovementResult stockOutValued(Long warehouseId, Long materialId, BigDecimal quantity,
                                               String sourceType, Long sourceId, Long sourceLineId) {
        return stockOutValuedInternal(warehouseId, materialId, quantity, null,
                sourceType, sourceId, sourceLineId, null);
    }

    /** 项目领料出库：库存流水继承服务端校验后的WBS任务。 */
    @Transactional(rollbackFor = Exception.class)
    public StockMovementResult stockOutValued(Long warehouseId, Long materialId, BigDecimal quantity,
                                               String sourceType, Long sourceId, Long sourceLineId,
                                               Long wbsTaskId) {
        return stockOutValuedInternal(warehouseId, materialId, quantity, null,
                sourceType, sourceId, sourceLineId, wbsTaskId);
    }

    /** 出库并按指定历史单位成本冲减库存价值，用于原事实的精确冲销。 */
    @Transactional(rollbackFor = Exception.class)
    public StockMovementResult stockOutAtUnitCost(Long warehouseId, Long materialId, BigDecimal quantity,
                                                   BigDecimal unitCost, String sourceType,
                                                   Long sourceId, Long sourceLineId) {
        if (unitCost == null || unitCost.signum() < 0) {
            throw new BusinessException("STOCK_UNIT_COST_INVALID", "冲销单位成本不能为负或为空");
        }
        return stockOutValuedInternal(warehouseId, materialId, quantity, unitCost,
                sourceType, sourceId, sourceLineId, null);
    }

    private StockMovementResult stockOutValuedInternal(Long warehouseId, Long materialId,
                                                         BigDecimal quantity, BigDecimal fixedUnitCost,
                                                         String sourceType, Long sourceId,
                                                         Long sourceLineId, Long wbsTaskId) {
        quantity = normalizeQuantity(quantity);
        Long tenantId = UserContext.getCurrentTenantId();

        MatStockTxn processed = findProcessedSourceLine(tenantId, "OUT", sourceType, sourceId, sourceLineId);
        if (processed != null) {
            MatStock existing = findStock(tenantId, warehouseId, materialId);
            if (existing == null) {
                throw new BusinessException("STOCK_IDEMPOTENCY_INCONSISTENT", "出库流水已存在但库存余额不存在");
            }
            return new StockMovementResult(existing, nvl(processed.getUnitCost()), nvl(processed.getAmount()));
        }

        MatStock stock = findStock(tenantId, warehouseId, materialId);
        if (stock == null) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    "库存不足：该仓库+物料尚无库存记录");
        }

        int retries = 0;
        BigDecimal issuedUnitCost;
        BigDecimal issuedAmount;
        while (true) {
            if (stock.getAvailableQty().compareTo(quantity) < 0) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "库存不足：可用 " + stock.getAvailableQty()
                                + "，请求出库 " + quantity);
            }
            issuedUnitCost = fixedUnitCost == null ? nvl(stock.getAverageUnitCost()) : fixedUnitCost;
            issuedAmount = quantity.multiply(issuedUnitCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal nextQuantity = stock.getAvailableQty().subtract(quantity);
            BigDecimal nextValue = nvl(stock.getInventoryValue()).subtract(issuedAmount);
            if (nextValue.signum() < 0 && nextValue.abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new BusinessException("STOCK_VALUE_INSUFFICIENT", "库存价值不足，无法按历史成本冲销");
            }
            if (nextQuantity.signum() == 0) {
                if (fixedUnitCost != null && nextValue.abs().compareTo(new BigDecimal("0.01")) > 0) {
                    throw new BusinessException("STOCK_VALUE_MISMATCH", "清空库存后仍有价值余额，禁止冲销");
                }
                nextValue = BigDecimal.ZERO;
            }
            if (nextValue.signum() < 0 && nextValue.abs().compareTo(new BigDecimal("0.01")) <= 0) {
                nextValue = BigDecimal.ZERO;
            }
            stock.setAvailableQty(nextQuantity);
            stock.setInventoryValue(nextValue);
            stock.setAverageUnitCost(nextQuantity.signum() == 0 ? BigDecimal.ZERO
                    : nextValue.divide(nextQuantity, 2, RoundingMode.HALF_UP));
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
                stock.getAvailableQty(), issuedUnitCost, issuedAmount,
                sourceType, sourceId, sourceLineId, wbsTaskId);

        return new StockMovementResult(stock, issuedUnitCost, issuedAmount);
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        // 0 被视为合法的占位调整（MatStockServiceTest 边界保留），仅在 null 或负值时报错。
        // 终态非负防御在 doUpdateIncrement / stockOut 内部由"变更后≥0"保证。
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("STOCK_QUANTITY_INVALID", "库存数量不能为负或为空");
        }
        if (quantity.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("STOCK_QUANTITY_INVALID", "库存数量最多保留2位小数");
        }
        return quantity.setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * 分页查询当前可访问项目下的库存余额。
     * 列表只返回服务端库存事实，不在客户端聚合数量或金额。
     */
    public PageResult<MatStockVO> getPage(Long warehouseId, Long materialId,
                                          Long projectId, String keyword,
                                          long pageNo, long pageSize) {
        return readOperations.getPage(warehouseId, materialId, projectId, keyword, pageNo, pageSize);
    }

    /**
     * 查询库存台账：当前库存余额 + 分页流水记录。
     * <p>
     * 包含仓库名、物料名/编码/单位的 JOIN 查询，支持 keyword 模糊搜索和动态排序。
     *
     * @param warehouseId 仓库ID（可选，传 null 则汇总全部启用仓库）
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
        return readOperations.getLedger(
                warehouseId, materialId, projectId, keyword, sortField, sortOrder, pageNo, pageSize);
    }

    /**
     * 库存台账 KPI 统计。
     *
     * @param warehouseId 仓库ID（可选，传 null 则全仓库统计）
     * @param projectId   项目ID（可选）
     * @return KPI VO
     */
    public StockKpiVO getKpi(Long warehouseId, Long projectId) {
        return readOperations.getKpi(warehouseId, projectId);
    }

    /**
     * 查询当前库存项在同项目其他启用仓库的可调拨余量。
     * 结果只是查询快照，不预占库存；项目范围由当前库存项所属仓库反查。
     */
    public List<StockTransferCandidateVO> getTransferCandidates(Long stockId) {
        MatStock currentStock = loadAuthorizedStock(stockId, "查询跨仓可调拨余量");
        MatWarehouse currentWarehouse = matWarehouseMapper.selectById(currentStock.getWarehouseId());
        return readOperations.getTransferCandidates(currentStock, currentWarehouse);
    }

    /**
     * 汇总当前库存项含今日的 30/90 个本地自然日净领料事实。
     * 只计已过账领料与材料退料；结果用于历史分析，不代表需求预测。
     */
    public StockConsumptionBaselineVO getConsumptionBaseline(Long stockId) {
        MatStock currentStock = loadAuthorizedStock(stockId, "查询库存历史净领料基线");
        return readOperations.getConsumptionBaseline(currentStock);
    }

    /**
     * 将同项目同物料从来源库存原子调拨到目标库存。
     * 两端库存按 ID 稳定加锁，避免反向并发调拨死锁；调拨事实、成对流水与余额在同一事务提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public StockTransferVO transfer(StockTransferDTO dto) {
        if (dto == null || dto.getQuantity() == null || dto.getQuantity().signum() <= 0
                || dto.getQuantity().stripTrailingZeros().scale() > 2) {
            throw new BusinessException("STOCK_TRANSFER_QUANTITY_INVALID", "调拨数量必须大于0且最多2位小数");
        }
        if (dto.getSourceStockId() == null || dto.getTargetStockId() == null) {
            throw new BusinessException("STOCK_TRANSFER_ROUTE_INVALID", "调拨两端库存不能为空");
        }
        if (!StringUtils.hasText(dto.getIdempotencyKey()) || dto.getIdempotencyKey().trim().length() > 100) {
            throw new BusinessException("STOCK_TRANSFER_IDEMPOTENCY_KEY_INVALID", "幂等键不能为空且不能超过100个字符");
        }
        if (!StringUtils.hasText(dto.getReason()) || dto.getReason().trim().length() > 500) {
            throw new BusinessException("STOCK_TRANSFER_REASON_INVALID", "调拨原因不能为空且不能超过500个字符");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        BigDecimal quantity = dto.getQuantity().setScale(2, RoundingMode.UNNECESSARY);
        String idempotencyKey = dto.getIdempotencyKey().trim();
        String reason = dto.getReason().trim();

        MatStockTransfer existing = findTransfer(tenantId, idempotencyKey);
        if (existing != null) {
            return resolveExistingTransfer(existing, dto, quantity, reason);
        }

        MatStockTransfer transfer = new MatStockTransfer();
        transfer.setTenantId(tenantId);
        transfer.setSourceStockId(dto.getSourceStockId());
        transfer.setTargetStockId(dto.getTargetStockId());
        transfer.setQuantity(quantity);
        transfer.setUnitCost(BigDecimal.ZERO.setScale(2));
        transfer.setAmount(BigDecimal.ZERO.setScale(2));
        transfer.setIdempotencyKey(idempotencyKey);
        transfer.setStatus("PENDING");
        transfer.setRemark(reason);

        if (Objects.equals(dto.getSourceStockId(), dto.getTargetStockId())) {
            throw new BusinessException("STOCK_TRANSFER_ROUTE_INVALID", "来源库存与目标库存不能相同");
        }
        long firstId = Math.min(dto.getSourceStockId(), dto.getTargetStockId());
        long secondId = Math.max(dto.getSourceStockId(), dto.getTargetStockId());
        MatStock first = matStockMapper.selectByIdForUpdate(firstId, tenantId);
        MatStock second = matStockMapper.selectByIdForUpdate(secondId, tenantId);
        if (first == null || second == null) {
            throw new BusinessException("STOCK_TRANSFER_ROUTE_INVALID", "调拨库存不存在");
        }
        // 并发同键请求可能在等待库存锁期间由先行事务完成；必须先重查幂等事实，
        // 否则会基于已扣减库存误报安全库存不足，而不是返回先行事务结果。
        existing = matStockTransferMapper.selectByTenantAndKeyForUpdate(tenantId, idempotencyKey);
        if (existing != null) {
            return resolveExistingTransfer(existing, dto, quantity, reason);
        }
        MatStock source = Objects.equals(first.getId(), dto.getSourceStockId()) ? first : second;
        MatStock target = Objects.equals(first.getId(), dto.getTargetStockId()) ? first : second;

        MatWarehouse sourceWarehouse = loadTransferWarehouse(source.getWarehouseId(), tenantId);
        MatWarehouse targetWarehouse = loadTransferWarehouse(target.getWarehouseId(), tenantId);
        if (Objects.equals(sourceWarehouse.getId(), targetWarehouse.getId())
                || !Objects.equals(sourceWarehouse.getProjectId(), targetWarehouse.getProjectId())
                || !Objects.equals(source.getMaterialId(), target.getMaterialId())) {
            throw new BusinessException("STOCK_TRANSFER_ROUTE_INVALID", "调拨仅支持同项目、同物料的不同启用仓库");
        }
        projectAccessChecker.checkAccess(sourceWarehouse.getProjectId(), "执行库存调拨");

        BigDecimal safety = nvl(source.getSafetyStockQty());
        BigDecimal transferable = nvl(source.getAvailableQty()).subtract(safety);
        if (transferable.compareTo(quantity) < 0) {
            throw new BusinessException("STOCK_TRANSFER_SAFETY_LIMIT",
                    "可调拨数量不足：当前最多可调拨 " + transferable.max(BigDecimal.ZERO));
        }

        transfer.setProjectId(sourceWarehouse.getProjectId());
        transfer.setSourceWarehouseId(sourceWarehouse.getId());
        transfer.setTargetWarehouseId(targetWarehouse.getId());
        transfer.setMaterialId(source.getMaterialId());
        try {
            matStockTransferMapper.insert(transfer);
        } catch (DuplicateKeyException duplicate) {
            MatStockTransfer winner = findTransfer(tenantId, idempotencyKey);
            if (winner == null) {
                throw new BusinessException("STOCK_TRANSFER_CONCURRENT_CONFLICT", "调拨请求并发冲突，请稍后重试");
            }
            return resolveExistingTransfer(winner, dto, quantity, reason);
        }

        StockMovementResult movement = stockOutValued(sourceWarehouse.getId(), source.getMaterialId(), quantity,
                "STOCK_TRANSFER", transfer.getId(), source.getId());
        stockInValued(targetWarehouse.getId(), target.getMaterialId(), quantity, movement.unitCost(),
                "STOCK_TRANSFER", transfer.getId(), target.getId());

        transfer.setUnitCost(movement.unitCost());
        transfer.setAmount(movement.amount());
        transfer.setStatus("COMPLETED");
        transfer.setCompletedAt(LocalDateTime.now());
        if (matStockTransferMapper.updateById(transfer) != 1) {
            throw new BusinessException("STOCK_TRANSFER_CONCURRENT_CONFLICT", "调拨事实更新失败，请稍后重试");
        }
        return toTransferVO(transfer);
    }

    /**
     * 查询当前库存项对应的已审批采购订单未收货余量。
     * 结果按订单汇总，只是未入库查询快照，不改变采购、验收或库存事实。
     */
    public List<StockIncomingSupplyVO> getIncomingSupplies(Long stockId) {
        MatStock currentStock = loadAuthorizedStock(stockId, "查询采购在途余量");
        MatWarehouse currentWarehouse = matWarehouseMapper.selectById(currentStock.getWarehouseId());
        return readOperations.getIncomingSupplies(currentStock, currentWarehouse);
    }

    @Transactional(rollbackFor = Exception.class)
    public MatStock updateSafetyStockThreshold(Long stockId, BigDecimal safetyStockQty) {
        validateQuantity(safetyStockQty, "INVALID_SAFETY_STOCK_THRESHOLD", "安全库存阈值");
        MatStock stock = loadAuthorizedStock(stockId, "维护安全库存阈值");
        BigDecimal normalizedSafety = safetyStockQty.setScale(2);
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
        BigDecimal normalizedSafety = safetyStockQty.setScale(2);
        BigDecimal normalizedTarget = replenishmentTargetQty == null ? null : replenishmentTargetQty.setScale(2);
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
        return readOperations.toStockVO(entity);
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

    private MatWarehouse loadTransferWarehouse(Long warehouseId, Long tenantId) {
        MatWarehouse warehouse = matWarehouseMapper.selectById(warehouseId);
        if (warehouse == null || !tenantId.equals(warehouse.getTenantId())
                || !"ENABLE".equals(warehouse.getStatus())) {
            throw new BusinessException("STOCK_TRANSFER_ROUTE_INVALID", "调拨仓库不存在或未启用");
        }
        return warehouse;
    }

    private MatStockTransfer findTransfer(Long tenantId, String idempotencyKey) {
        return matStockTransferMapper.selectOne(new LambdaQueryWrapper<MatStockTransfer>()
                .eq(MatStockTransfer::getTenantId, tenantId)
                .eq(MatStockTransfer::getIdempotencyKey, idempotencyKey));
    }

    private StockTransferVO resolveExistingTransfer(MatStockTransfer existing, StockTransferDTO dto,
                                                     BigDecimal quantity, String reason) {
        boolean samePayload = Objects.equals(existing.getSourceStockId(), dto.getSourceStockId())
                && Objects.equals(existing.getTargetStockId(), dto.getTargetStockId())
                && existing.getQuantity() != null && existing.getQuantity().compareTo(quantity) == 0
                && Objects.equals(existing.getRemark(), reason);
        if (!samePayload) {
            throw new BusinessException("STOCK_TRANSFER_IDEMPOTENCY_CONFLICT", "幂等键已被其他调拨载荷使用");
        }
        if (!"COMPLETED".equals(existing.getStatus())) {
            throw new BusinessException("STOCK_TRANSFER_IN_PROGRESS", "调拨正在处理中，请稍后查询");
        }
        return toTransferVO(existing);
    }

    private StockTransferVO toTransferVO(MatStockTransfer transfer) {
        StockTransferVO vo = new StockTransferVO();
        vo.setId(transfer.getId());
        vo.setProjectId(transfer.getProjectId());
        vo.setSourceStockId(transfer.getSourceStockId());
        vo.setTargetStockId(transfer.getTargetStockId());
        vo.setSourceWarehouseId(transfer.getSourceWarehouseId());
        vo.setTargetWarehouseId(transfer.getTargetWarehouseId());
        vo.setMaterialId(transfer.getMaterialId());
        vo.setQuantity(transfer.getQuantity());
        vo.setUnitCost(transfer.getUnitCost());
        vo.setAmount(transfer.getAmount());
        vo.setIdempotencyKey(transfer.getIdempotencyKey());
        vo.setStatus(transfer.getStatus());
        vo.setReason(transfer.getRemark());
        vo.setCompletedAt(transfer.getCompletedAt());
        return vo;
    }

    /**
     * 插入一条库存流水记录。
     */
    private void insertTxn(Long tenantId, Long warehouseId, Long materialId,
                           String txnType, BigDecimal quantity,
                           BigDecimal availableAfter, BigDecimal unitCost, BigDecimal amount,
                           String sourceType, Long sourceId, Long sourceLineId, Long wbsTaskId) {
        MatStockTxn txn = new MatStockTxn();
        txn.setTenantId(tenantId);
        txn.setWarehouseId(warehouseId);
        txn.setMaterialId(materialId);
        txn.setTxnType(txnType);
        txn.setQuantity(quantity);
        txn.setAvailableAfter(availableAfter);
        txn.setUnitCost(unitCost);
        txn.setAmount(amount);
        txn.setSourceType(sourceType);
        txn.setSourceId(sourceId);
        txn.setSourceLineId(sourceLineId);
        txn.setWbsTaskId(wbsTaskId);
        matStockTxnMapper.insert(txn);
    }

    private MatStockTxn findProcessedSourceLine(Long tenantId, String txnType,
                                                String sourceType, Long sourceId, Long sourceLineId) {
        if (!StringUtils.hasText(sourceType) || sourceId == null || sourceLineId == null) {
            return null;
        }
        return matStockTxnMapper.selectOne(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getTenantId, tenantId)
                .eq(MatStockTxn::getTxnType, txnType)
                .eq(MatStockTxn::getSourceType, sourceType)
                .eq(MatStockTxn::getSourceId, sourceId)
                .eq(MatStockTxn::getSourceLineId, sourceLineId));
    }

    private void validateQuantity(BigDecimal value, String code, String label) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(code, label + "必须为非负数且最多 2 位小数");
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

    public record StockMovementResult(MatStock stock, BigDecimal unitCost, BigDecimal amount) {}
}
