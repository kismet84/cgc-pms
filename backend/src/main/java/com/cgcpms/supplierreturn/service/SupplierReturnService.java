package com.cgcpms.supplierreturn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.service.BudgetLedgerService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.procurement.service.ProcurementIntegrityService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.supplierreturn.dto.SupplierReturnRequest;
import com.cgcpms.supplierreturn.entity.SupplierReturn;
import com.cgcpms.supplierreturn.entity.SupplierReturnItem;
import com.cgcpms.supplierreturn.mapper.SupplierReturnItemMapper;
import com.cgcpms.supplierreturn.mapper.SupplierReturnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierReturnService {
    private final SupplierReturnMapper returnMapper;
    private final SupplierReturnItemMapper returnItemMapper;
    private final MatReceiptMapper receiptMapper;
    private final MatReceiptItemMapper receiptItemMapper;
    private final MatPurchaseOrderMapper orderMapper;
    private final MatPurchaseOrderItemMapper orderItemMapper;
    private final MatStockTxnMapper stockTxnMapper;
    private final CostItemMapper costItemMapper;
    private final MatStockService stockService;
    private final BudgetLedgerService budgetLedgerService;
    private final ProcurementIntegrityService integrityService;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(rollbackFor = Exception.class)
    public Long create(SupplierReturnRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        SupplierReturn existing = returnMapper.selectOne(new LambdaQueryWrapper<SupplierReturn>()
                .eq(SupplierReturn::getTenantId, tenantId)
                .eq(SupplierReturn::getIdempotencyKey, request.idempotencyKey()));
        if (existing != null) return existing.getId();

        Source source = requireSource(request.receiptItemId(), request.returnKind());
        validateReturnCapacity(source.item(), request.returnKind(), request.quantity());

        BigDecimal unitCost = source.item().getUnitPrice() == null ? BigDecimal.ZERO : source.item().getUnitPrice();
        BigDecimal amount = request.quantity().multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
        SupplierReturn header = new SupplierReturn();
        header.setTenantId(tenantId);
        header.setProjectId(source.receipt().getProjectId());
        header.setContractId(source.receipt().getContractId());
        header.setPartnerId(source.receipt().getPartnerId());
        header.setWarehouseId(source.receipt().getWarehouseId());
        header.setReceiptId(source.receipt().getId());
        header.setReturnCode("SRT-" + request.returnDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        header.setReturnDate(request.returnDate());
        header.setReturnKind(request.returnKind());
        header.setStatus("DRAFT");
        header.setReason(request.reason().trim());
        header.setIdempotencyKey(request.idempotencyKey());
        header.setTotalAmount(amount);
        returnMapper.insert(header);

        SupplierReturnItem item = new SupplierReturnItem();
        item.setTenantId(tenantId);
        item.setReturnId(header.getId());
        item.setReceiptItemId(source.item().getId());
        item.setOrderItemId(source.item().getOrderItemId());
        item.setMaterialId(source.item().getMaterialId());
        item.setQuantity(request.quantity());
        item.setUnitCost(unitCost);
        item.setAmount(amount);
        returnItemMapper.insert(item);
        return header.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long returnId) {
        SupplierReturn header = requireReturn(returnId);
        if ("CONFIRMED".equals(header.getStatus())) return;
        if (!"DRAFT".equals(header.getStatus())) {
            throw new BusinessException("SUPPLIER_RETURN_STATUS_INVALID", "退货单当前状态不可确认");
        }
        projectAccessChecker.checkAccess(header.getProjectId(), "确认供应商退货");
        integrityService.requireActiveProject(header.getProjectId(), "确认供应商退货");
        integrityService.requireCleanAttachment("SUPPLIER_RETURN", returnId);

        SupplierReturnItem item = returnItemMapper.selectOne(new LambdaQueryWrapper<SupplierReturnItem>()
                .eq(SupplierReturnItem::getTenantId, header.getTenantId())
                .eq(SupplierReturnItem::getReturnId, returnId));
        if (item == null) throw new BusinessException("SUPPLIER_RETURN_ITEM_REQUIRED", "供应商退货缺少明细");
        Source source = requireSource(item.getReceiptItemId(), header.getReturnKind());
        validateReturnCapacity(source.item(), header.getReturnKind(), item.getQuantity());

        MatPurchaseOrderItem orderItem = orderItemMapper.selectById(item.getOrderItemId());
        if (orderItem == null || !Objects.equals(orderItem.getTenantId(), header.getTenantId())) {
            throw new BusinessException("SUPPLIER_RETURN_ORDER_ITEM_NOT_FOUND", "退货对应采购订单明细不存在");
        }

        if ("ACCEPTED".equals(header.getReturnKind())) {
            reverseAcceptedMaterial(header, item, source, orderItem);
            decrementReceived(orderItem, item.getQuantity());
            orderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                    .eq(MatPurchaseOrder::getId, source.receipt().getOrderId())
                    .eq(MatPurchaseOrder::getTenantId, header.getTenantId())
                    .eq(MatPurchaseOrder::getOrderStatus, "COMPLETED")
                    .set(MatPurchaseOrder::getOrderStatus, "APPROVED"));
        } else {
            BigDecimal confirmed = nvl(returnItemMapper.sumConfirmedQuantity(header.getTenantId(), item.getReceiptItemId(), "UNQUALIFIED"));
            if (confirmed.add(item.getQuantity()).compareTo(nvl(source.item().getUnqualifiedQuantity())) == 0) {
                receiptItemMapper.update(null, new LambdaUpdateWrapper<MatReceiptItem>()
                        .eq(MatReceiptItem::getId, source.item().getId())
                        .eq(MatReceiptItem::getDispositionStatus, "PENDING")
                        .set(MatReceiptItem::getDispositionStatus, "COMPLETED"));
            }
        }

        BigDecimal budgetAmount = item.getQuantity().multiply(nvl(orderItem.getUnitPrice()))
                .setScale(2, RoundingMode.HALF_UP);
        budgetLedgerService.reverse(orderItem.getBudgetLineId(), "SUPPLIER_RETURN", returnId, budgetAmount,
                "SUPPLIER_RETURN:" + returnId + ":ITEM:" + item.getId() + ":REVERSE");

        int claimed = returnMapper.update(null, new LambdaUpdateWrapper<SupplierReturn>()
                .eq(SupplierReturn::getId, returnId)
                .eq(SupplierReturn::getTenantId, header.getTenantId())
                .eq(SupplierReturn::getStatus, "DRAFT")
                .set(SupplierReturn::getStatus, "CONFIRMED")
                .set(SupplierReturn::getConfirmedBy, UserContext.getCurrentUserId())
                .set(SupplierReturn::getConfirmedAt, LocalDateTime.now()));
        if (claimed != 1) throw new BusinessException("SUPPLIER_RETURN_CONCURRENT_CONFLICT", "退货单已被其他用户处理");
    }

    public SupplierReturn getById(Long id) {
        SupplierReturn result = requireReturn(id);
        projectAccessChecker.checkAccess(result.getProjectId(), "查看供应商退货");
        return result;
    }

    public List<SupplierReturnItem> getItems(Long id) {
        getById(id);
        return returnItemMapper.selectList(new LambdaQueryWrapper<SupplierReturnItem>()
                .eq(SupplierReturnItem::getTenantId, UserContext.getCurrentTenantId())
                .eq(SupplierReturnItem::getReturnId, id));
    }

    private void reverseAcceptedMaterial(SupplierReturn header, SupplierReturnItem item, Source source,
                                         MatPurchaseOrderItem orderItem) {
        if ("DIRECT_CONSUMPTION".equals(source.receipt().getReceiptMode())) {
            CostItem original = costItemMapper.selectOne(new LambdaQueryWrapper<CostItem>()
                    .eq(CostItem::getTenantId, header.getTenantId())
                    .eq(CostItem::getSourceType, "MAT_RECEIPT")
                    .eq(CostItem::getSourceId, source.receipt().getId())
                    .eq(CostItem::getSourceItemId, source.item().getId()));
            if (original == null) throw new BusinessException("SUPPLIER_RETURN_COST_NOT_FOUND", "直耗材料原成本不存在");
            item.setOriginalCostItemId(original.getId());
            returnItemMapper.updateById(item);
            CostItem reversal = new CostItem();
            reversal.setTenantId(header.getTenantId());
            reversal.setProjectId(header.getProjectId());
            reversal.setContractId(header.getContractId());
            reversal.setPartnerId(header.getPartnerId());
            reversal.setCostType("MATERIAL");
            reversal.setCostSubjectId(original.getCostSubjectId());
            reversal.setAmount(item.getAmount().negate());
            reversal.setTaxAmount(BigDecimal.ZERO);
            reversal.setAmountWithoutTax(item.getAmount().negate());
            reversal.setSourceType("SUPPLIER_RETURN");
            reversal.setSourceId(header.getId());
            reversal.setSourceItemId(item.getId());
            reversal.setCostDate(header.getReturnDate());
            reversal.setCostStatus("CONFIRMED");
            reversal.setGeneratedFlag(1);
            reversal.setRemark("供应商退货冲销验收成本 " + original.getId());
            costItemMapper.insert(reversal);
            return;
        }

        MatStockTxn originalTxn = stockTxnMapper.selectOne(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getTenantId, header.getTenantId())
                .eq(MatStockTxn::getTxnType, "IN")
                .eq(MatStockTxn::getSourceType, "MAT_RECEIPT")
                .eq(MatStockTxn::getSourceId, source.receipt().getId())
                .eq(MatStockTxn::getSourceLineId, source.item().getId()));
        if (originalTxn == null) throw new BusinessException("SUPPLIER_RETURN_STOCK_SOURCE_NOT_FOUND", "原验收入库流水不存在");
        item.setOriginalStockTxnId(originalTxn.getId());
        item.setUnitCost(originalTxn.getUnitCost());
        item.setAmount(item.getQuantity().multiply(originalTxn.getUnitCost()).setScale(2, RoundingMode.HALF_UP));
        returnItemMapper.updateById(item);
        stockService.stockOutValued(header.getWarehouseId(), item.getMaterialId(), item.getQuantity(),
                "SUPPLIER_RETURN", header.getId(), item.getId());
    }

    private void decrementReceived(MatPurchaseOrderItem original, BigDecimal quantity) {
        for (int retry = 0; retry < 3; retry++) {
            MatPurchaseOrderItem current = orderItemMapper.selectById(original.getId());
            BigDecimal next = nvl(current.getReceivedQuantity()).subtract(quantity);
            if (next.signum() < 0) throw new BusinessException("SUPPLIER_RETURN_EXCEEDS_ACCEPTED", "退货数量超过累计合格验收数量");
            current.setReceivedQuantity(next);
            if (orderItemMapper.updateById(current) == 1) return;
        }
        throw new BusinessException("SUPPLIER_RETURN_CONCURRENT_CONFLICT", "采购订单已验收数量并发更新冲突");
    }

    private Source requireSource(Long receiptItemId, String returnKind) {
        Long tenantId = UserContext.getCurrentTenantId();
        MatReceiptItem item = receiptItemMapper.selectById(receiptItemId);
        if (item == null || !Objects.equals(item.getTenantId(), tenantId)) {
            throw new BusinessException("SUPPLIER_RETURN_RECEIPT_ITEM_NOT_FOUND", "原验收明细不存在");
        }
        MatReceipt receipt = receiptMapper.selectById(item.getReceiptId());
        if (receipt == null || !Objects.equals(receipt.getTenantId(), tenantId)
                || !"APPROVED".equals(receipt.getApprovalStatus())) {
            throw new BusinessException("SUPPLIER_RETURN_RECEIPT_NOT_APPROVED", "只有审批通过的验收单可以退货");
        }
        projectAccessChecker.checkAccess(receipt.getProjectId(), "供应商退货");
        if ("UNQUALIFIED".equals(returnKind)
                && (!"RETURN".equals(item.getDispositionType()) || !"PENDING".equals(item.getDispositionStatus()))) {
            throw new BusinessException("SUPPLIER_RETURN_DISPOSITION_INVALID", "不合格退货必须来源于待处置的退货决定");
        }
        return new Source(receipt, item);
    }

    private void validateReturnCapacity(MatReceiptItem item, String kind, BigDecimal quantity) {
        BigDecimal limit = "UNQUALIFIED".equals(kind) ? nvl(item.getUnqualifiedQuantity()) : nvl(item.getQualifiedQuantity());
        BigDecimal confirmed = nvl(returnItemMapper.sumConfirmedQuantity(UserContext.getCurrentTenantId(), item.getId(), kind));
        if (confirmed.add(quantity).compareTo(limit) > 0) {
            throw new BusinessException("SUPPLIER_RETURN_EXCEEDS_SOURCE", "累计退货数量超过原验收可退数量");
        }
    }

    private SupplierReturn requireReturn(Long id) {
        SupplierReturn result = returnMapper.selectById(id);
        if (result == null || !Objects.equals(result.getTenantId(), UserContext.getCurrentTenantId())) {
            throw new BusinessException("SUPPLIER_RETURN_NOT_FOUND", "供应商退货单不存在");
        }
        return result;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Source(MatReceipt receipt, MatReceiptItem item) {}
}
