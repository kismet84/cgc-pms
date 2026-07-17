package com.cgcpms.supplierreturn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.supplierreturn.dto.SupplierReturnRequest;
import com.cgcpms.supplierreturn.entity.MatQualityDisposition;
import com.cgcpms.supplierreturn.entity.MatSupplierReturn;
import com.cgcpms.supplierreturn.entity.MatSupplierReturnItem;
import com.cgcpms.supplierreturn.mapper.MatQualityDispositionMapper;
import com.cgcpms.supplierreturn.mapper.MatSupplierReturnItemMapper;
import com.cgcpms.supplierreturn.mapper.MatSupplierReturnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatSupplierReturnService {
    private static final String QUALIFIED = "QUALIFIED";
    private static final String REJECTED = "REJECTED";

    private final MatSupplierReturnMapper returnMapper;
    private final MatSupplierReturnItemMapper returnItemMapper;
    private final MatQualityDispositionMapper dispositionMapper;
    private final MatReceiptMapper receiptMapper;
    private final MatReceiptItemMapper receiptItemMapper;
    private final MatPurchaseOrderItemMapper orderItemMapper;
    private final MatStockTxnMapper stockTxnMapper;
    private final CostItemMapper costItemMapper;
    private final MatStockService stockService;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(rollbackFor = Exception.class)
    public Long confirm(SupplierReturnRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        MatSupplierReturn existing = findByIdempotencyKey(tenantId, request.idempotencyKey());
        if (existing != null) return existing.getId();

        // 以验收明细作为合格品和不合格品两类累计退货的共同串行化锚点。
        MatReceiptItem receiptItem = receiptItemMapper.selectForUpdate(request.receiptItemId(), tenantId);
        if (receiptItem == null) {
            throw new BusinessException("SUPPLIER_RETURN_RECEIPT_ITEM_NOT_FOUND", "原验收明细不存在");
        }
        existing = findByIdempotencyKey(tenantId, request.idempotencyKey());
        if (existing != null) return existing.getId();

        MatReceipt receipt = receiptMapper.selectById(receiptItem.getReceiptId());
        if (receipt == null || !tenantId.equals(receipt.getTenantId())
                || !"APPROVED".equals(receipt.getApprovalStatus())) {
            throw new BusinessException("SUPPLIER_RETURN_RECEIPT_INVALID", "原验收单不存在或未审批通过");
        }
        projectAccessChecker.checkAccess(receipt.getProjectId(), "确认供应商退货");
        if (receipt.getPartnerId() == null || receipt.getContractId() == null || receipt.getOrderId() == null
                || receiptItem.getOrderItemId() == null
                || receiptItem.getMaterialId() == null) {
            throw new BusinessException("SUPPLIER_RETURN_SOURCE_INCOMPLETE", "原验收供应商、订单明细或材料关系不完整");
        }

        boolean rejected = request.qualityDispositionId() != null || "UNQUALIFIED".equals(request.returnKind());
        if (request.qualityDispositionId() != null && "ACCEPTED".equals(request.returnKind())) {
            throw new BusinessException("SUPPLIER_RETURN_SOURCE_CONFLICT", "合格品退货不能关联不合格处置");
        }
        String source = rejected ? REJECTED : QUALIFIED;
        MatQualityDisposition disposition = null;
        MatStockTxn originalTxn = null;
        CostItem originalCost = null;
        BigDecimal limit;
        BigDecimal unitCost;
        if (REJECTED.equals(source)) {
            disposition = request.qualityDispositionId() == null
                    ? dispositionMapper.selectReturnForUpdate(receiptItem.getId(), tenantId)
                    : dispositionMapper.selectForUpdate(request.qualityDispositionId(), tenantId);
            if (disposition == null || !Objects.equals(disposition.getReceiptItemId(), receiptItem.getId())
                    || !"RETURN_TO_SUPPLIER".equals(disposition.getDispositionAction())
                    || "CANCELLED".equals(disposition.getStatus())) {
                throw new BusinessException("QUALITY_DISPOSITION_INVALID", "不合格处置不存在或不是供应商退货处置");
            }
            limit = disposition.getRejectedQuantity();
            unitCost = nvl(receiptItem.getUnitPrice());
        } else if ("DIRECT_CONSUMPTION".equals(receipt.getReceiptMode())) {
            originalCost = costItemMapper.selectOne(new LambdaQueryWrapper<CostItem>()
                    .eq(CostItem::getTenantId, tenantId)
                    .eq(CostItem::getSourceType, "MAT_RECEIPT")
                    .eq(CostItem::getSourceId, receipt.getId())
                    .eq(CostItem::getSourceItemId, receiptItem.getId())
                    .eq(CostItem::getCostStatus, "CONFIRMED"));
            if (originalCost == null) {
                throw new BusinessException("SUPPLIER_RETURN_ORIGINAL_COST_NOT_FOUND", "原直耗验收成本不存在");
            }
            limit = receiptItem.getQualifiedQuantity();
            unitCost = divideUnitCost(originalCost.getAmount(), receiptItem.getQualifiedQuantity());
        } else {
            originalTxn = stockTxnMapper.selectReceiptInForUpdate(tenantId, receipt.getId(), receiptItem.getId());
            if (originalTxn == null || !Objects.equals(originalTxn.getWarehouseId(), receipt.getWarehouseId())
                    || !Objects.equals(originalTxn.getMaterialId(), receiptItem.getMaterialId())) {
                throw new BusinessException("SUPPLIER_RETURN_STOCK_SOURCE_NOT_FOUND", "原验收入库流水不存在或关系不匹配");
            }
            limit = originalTxn.getQuantity();
            unitCost = originalTxn.getUnitCost();
        }

        BigDecimal returned = nvl(returnItemMapper.sumConfirmedQuantity(tenantId, receiptItem.getId(), source));
        if (returned.add(request.quantity()).compareTo(nvl(limit)) > 0) {
            throw new BusinessException("SUPPLIER_RETURN_EXCEEDS_RECEIPT", "累计供应商退货数量超过原验收可退数量");
        }
        BigDecimal amount = request.quantity().multiply(nvl(unitCost)).setScale(2, RoundingMode.HALF_UP);

        MatSupplierReturn supplierReturn = new MatSupplierReturn();
        supplierReturn.setTenantId(tenantId);
        supplierReturn.setProjectId(receipt.getProjectId());
        supplierReturn.setContractId(receipt.getContractId());
        supplierReturn.setPartnerId(receipt.getPartnerId());
        supplierReturn.setPurchaseOrderId(receipt.getOrderId());
        supplierReturn.setReceiptId(receipt.getId());
        supplierReturn.setWarehouseId(originalTxn == null ? null : originalTxn.getWarehouseId());
        supplierReturn.setReturnCode("SRT-" + request.returnDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        supplierReturn.setReturnDate(request.returnDate());
        supplierReturn.setReturnQuantity(request.quantity());
        supplierReturn.setStatus("CONFIRMED");
        supplierReturn.setIdempotencyKey(request.idempotencyKey());
        supplierReturn.setTotalAmount(amount);
        supplierReturn.setReason(request.reason().trim());
        supplierReturn.setConfirmedBy(UserContext.getCurrentUserId());
        supplierReturn.setConfirmedAt(LocalDateTime.now());
        supplierReturn.setVersion(0);
        returnMapper.insert(supplierReturn);

        MatSupplierReturnItem item = new MatSupplierReturnItem();
        item.setTenantId(tenantId);
        item.setReturnId(supplierReturn.getId());
        item.setReceiptItemId(receiptItem.getId());
        item.setOrderItemId(receiptItem.getOrderItemId());
        item.setQualityDispositionId(disposition == null ? null : disposition.getId());
        item.setOriginalStockTxnId(originalTxn == null ? null : originalTxn.getId());
        item.setOriginalCostItemId(originalCost == null ? null : originalCost.getId());
        item.setMaterialId(receiptItem.getMaterialId());
        item.setReturnSource(source);
        item.setQuantity(request.quantity());
        item.setUnitCost(nvl(unitCost));
        item.setAmount(amount);
        returnItemMapper.insert(item);

        if (REJECTED.equals(source)) {
            applyDispositionQuantity(disposition, request.quantity(), true);
        } else {
            MatPurchaseOrderItem orderItem = lockOrderItem(receiptItem.getOrderItemId(), tenantId);
            BigDecimal nextReceived = nvl(orderItem.getReceivedQuantity()).subtract(request.quantity());
            if (nextReceived.signum() < 0) {
                throw new BusinessException("SUPPLIER_RETURN_ORDER_QUANTITY_INVALID", "采购订单已收数量不足以执行退货");
            }
            orderItem.setReceivedQuantity(nextReceived);
            orderItemMapper.updateById(orderItem);
            if (originalTxn != null) {
                stockService.stockOutAtUnitCost(originalTxn.getWarehouseId(), receiptItem.getMaterialId(),
                        request.quantity(), unitCost, "SUPPLIER_RETURN", supplierReturn.getId(), item.getId());
            } else {
                insertCostFact(receipt, originalCost, supplierReturn.getId(), item.getId(), amount.negate(),
                        request.returnDate(), "SUPPLIER_RETURN", "供应商退货冲销原直耗验收成本");
            }
        }
        return supplierReturn.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long reverse(Long returnId, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException("SUPPLIER_RETURN_REVERSAL_REASON_INVALID", "冲销原因不能为空且最多500字");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        MatSupplierReturn supplierReturn = returnMapper.selectForUpdate(returnId, tenantId);
        if (supplierReturn == null) {
            throw new BusinessException("SUPPLIER_RETURN_NOT_FOUND", "供应商退货单不存在");
        }
        projectAccessChecker.checkAccess(supplierReturn.getProjectId(), "冲销供应商退货");
        if ("REVERSED".equals(supplierReturn.getStatus())) return returnId;
        if (!"CONFIRMED".equals(supplierReturn.getStatus())) {
            throw new BusinessException("SUPPLIER_RETURN_NOT_REVERSIBLE", "当前供应商退货状态不允许冲销");
        }
        List<MatSupplierReturnItem> items = getItemsInternal(tenantId, returnId);
        if (items.isEmpty()) {
            throw new BusinessException("SUPPLIER_RETURN_ITEM_MISSING", "供应商退货明细不存在");
        }
        MatReceipt receipt = receiptMapper.selectById(supplierReturn.getReceiptId());
        if (receipt == null || !tenantId.equals(receipt.getTenantId())) {
            throw new BusinessException("SUPPLIER_RETURN_RECEIPT_INVALID", "原验收单不存在");
        }
        for (MatSupplierReturnItem item : items) {
            if (REJECTED.equals(item.getReturnSource())) {
                MatQualityDisposition disposition = dispositionMapper.selectForUpdate(
                        item.getQualityDispositionId(), tenantId);
                if (disposition == null) {
                    throw new BusinessException("QUALITY_DISPOSITION_INVALID", "不合格处置不存在");
                }
                applyDispositionQuantity(disposition, item.getQuantity(), false);
                continue;
            }
            MatPurchaseOrderItem orderItem = lockOrderItem(item.getOrderItemId(), tenantId);
            orderItem.setReceivedQuantity(nvl(orderItem.getReceivedQuantity()).add(item.getQuantity()));
            orderItemMapper.updateById(orderItem);
            if (item.getOriginalStockTxnId() != null) {
                MatStockTxn original = stockTxnMapper.selectForUpdate(item.getOriginalStockTxnId(), tenantId);
                if (original == null) {
                    throw new BusinessException("SUPPLIER_RETURN_STOCK_SOURCE_NOT_FOUND", "原验收入库流水不存在");
                }
                stockService.stockInValued(original.getWarehouseId(), item.getMaterialId(), item.getQuantity(),
                        item.getUnitCost(), "SUPPLIER_RETURN_REVERSAL", returnId, item.getId());
            } else {
                CostItem originalCost = costItemMapper.selectById(item.getOriginalCostItemId());
                if (originalCost == null || !tenantId.equals(originalCost.getTenantId())) {
                    throw new BusinessException("SUPPLIER_RETURN_ORIGINAL_COST_NOT_FOUND", "原直耗验收成本不存在");
                }
                insertCostFact(receipt, originalCost, returnId, item.getId(), item.getAmount(),
                        LocalDate.now(), "SUPPLIER_RETURN_REVERSAL", "冲销供应商退货");
            }
        }
        supplierReturn.setStatus("REVERSED");
        supplierReturn.setReversedBy(UserContext.getCurrentUserId());
        supplierReturn.setReversedAt(LocalDateTime.now());
        supplierReturn.setReversalReason(reason.trim());
        returnMapper.updateById(supplierReturn);
        return returnId;
    }

    public MatSupplierReturn getById(Long id) {
        MatSupplierReturn result = returnMapper.selectById(id);
        if (result == null || !UserContext.getCurrentTenantId().equals(result.getTenantId())) {
            throw new BusinessException("SUPPLIER_RETURN_NOT_FOUND", "供应商退货单不存在");
        }
        projectAccessChecker.checkAccess(result.getProjectId(), "查看供应商退货");
        return result;
    }

    public List<MatSupplierReturnItem> getItems(Long id) {
        getById(id);
        return getItemsInternal(UserContext.getCurrentTenantId(), id);
    }

    private MatSupplierReturn findByIdempotencyKey(Long tenantId, String key) {
        return returnMapper.selectOne(new LambdaQueryWrapper<MatSupplierReturn>()
                .eq(MatSupplierReturn::getTenantId, tenantId)
                .eq(MatSupplierReturn::getIdempotencyKey, key));
    }

    private List<MatSupplierReturnItem> getItemsInternal(Long tenantId, Long returnId) {
        return returnItemMapper.selectList(new LambdaQueryWrapper<MatSupplierReturnItem>()
                .eq(MatSupplierReturnItem::getTenantId, tenantId)
                .eq(MatSupplierReturnItem::getReturnId, returnId));
    }

    private MatPurchaseOrderItem lockOrderItem(Long id, Long tenantId) {
        MatPurchaseOrderItem item = orderItemMapper.selectForUpdate(id, tenantId);
        if (item == null) {
            throw new BusinessException("SUPPLIER_RETURN_ORDER_ITEM_NOT_FOUND", "采购订单明细不存在");
        }
        return item;
    }

    private void applyDispositionQuantity(MatQualityDisposition disposition, BigDecimal quantity, boolean increase) {
        BigDecimal next = increase
                ? nvl(disposition.getResolvedQuantity()).add(quantity)
                : nvl(disposition.getResolvedQuantity()).subtract(quantity);
        if (next.signum() < 0 || next.compareTo(disposition.getRejectedQuantity()) > 0) {
            throw new BusinessException("QUALITY_DISPOSITION_QUANTITY_INVALID", "不合格处置数量越界");
        }
        disposition.setResolvedQuantity(next);
        disposition.setStatus(next.compareTo(disposition.getRejectedQuantity()) == 0 ? "RESOLVED" : "OPEN");
        disposition.setResolvedAt("RESOLVED".equals(disposition.getStatus()) ? LocalDateTime.now() : null);
        dispositionMapper.updateById(disposition);
    }

    private void insertCostFact(MatReceipt receipt, CostItem original, Long sourceId, Long sourceItemId,
                                BigDecimal amount, LocalDate costDate, String sourceType, String remark) {
        CostItem fact = new CostItem();
        fact.setTenantId(receipt.getTenantId());
        fact.setProjectId(receipt.getProjectId());
        fact.setContractId(receipt.getContractId());
        fact.setPartnerId(receipt.getPartnerId());
        fact.setCostSubjectId(original.getCostSubjectId());
        fact.setCostType("MATERIAL");
        fact.setAmount(amount);
        fact.setTaxAmount(BigDecimal.ZERO);
        fact.setAmountWithoutTax(amount);
        fact.setSourceType(sourceType);
        fact.setSourceId(sourceId);
        fact.setSourceItemId(sourceItemId);
        fact.setCostDate(costDate);
        fact.setCostStatus("CONFIRMED");
        fact.setGeneratedFlag(1);
        fact.setRemark(remark + " " + original.getId());
        costItemMapper.insert(fact);
    }

    private BigDecimal divideUnitCost(BigDecimal amount, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("SUPPLIER_RETURN_QUANTITY_INVALID", "原验收合格数量无效");
        }
        return nvl(amount).divide(quantity, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
