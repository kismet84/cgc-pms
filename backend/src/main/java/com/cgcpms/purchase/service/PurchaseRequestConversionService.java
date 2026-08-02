package com.cgcpms.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.entity.ContractBudgetAllocation;
import com.cgcpms.budget.mapper.ContractBudgetAllocationMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.purchase.dto.PurchaseOrderFromRequestCommand;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRequestConversionService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final MatPurchaseRequestMapper requestMapper;
    private final MatPurchaseRequestItemMapper requestItemMapper;
    private final MatPurchaseOrderMapper orderMapper;
    private final MatPurchaseOrderItemMapper orderItemMapper;
    private final CtContractMapper contractMapper;
    private final ContractBudgetAllocationMapper contractBudgetAllocationMapper;
    private final PurchaseOrderPricingService pricingService;
    private final ProjectAccessChecker projectAccessChecker;

    /** 显式从已审批申请建单；订单商业事实全部由合同/最近入库事实推导。 */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromApprovedRequest(PurchaseOrderFromRequestCommand command) {
        Long tenantId = UserContext.getCurrentTenantId();
        MatPurchaseRequest request = requestMapper.selectById(command.requestId());
        if (request == null || !Objects.equals(request.getTenantId(), tenantId)) {
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");
        }
        if (!"APPROVED".equals(request.getApprovalStatus()) || !"APPROVED".equals(request.getStatus())) {
            throw new BusinessException("PURCHASE_REQUEST_NOT_APPROVED", "采购申请尚未审批通过");
        }
        if (!Objects.equals(request.getProjectId(), command.projectId())) {
            throw new BusinessException("PURCHASE_REQUEST_PROJECT_MISMATCH", "采购申请项目与命令项目不一致");
        }
        projectAccessChecker.checkAccess(request.getProjectId(), "从采购申请创建采购订单");

        Long existing = orderMapper.selectCount(new LambdaQueryWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getTenantId, tenantId)
                .eq(MatPurchaseOrder::getRequestId, request.getId()));
        if (existing != null && existing > 0) {
            throw new BusinessException("REQUEST_ALREADY_CONVERTED", "采购申请已创建采购订单，不可重复转单");
        }

        CtContract contract = pricingService.requirePurchaseContract(
                contractMapper.selectById(command.contractId()), tenantId);
        if (!Objects.equals(contract.getProjectId(), request.getProjectId())) {
            throw new BusinessException("CONTRACT_PROJECT_MISMATCH", "关联合同不属于采购申请项目");
        }
        List<MatPurchaseRequestItem> requestItems = requestItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getTenantId, tenantId)
                        .eq(MatPurchaseRequestItem::getRequestId, request.getId()));
        if (requestItems.isEmpty()) {
            throw new BusinessException("PURCHASE_REQUEST_NO_ITEMS", "采购申请没有明细，无法创建采购订单");
        }
        Long budgetLineId = resolveContractBudgetLine(contract.getId(), contract.getProjectId(), tenantId);

        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(tenantId);
        order.setProjectId(request.getProjectId());
        order.setRequestId(request.getId());
        order.setContractId(contract.getId());
        order.setPartnerId(contract.getPartyBId());
        order.setOrderType("PURCHASE");
        order.setOrderDate(command.orderDate() == null ? LocalDate.now() : command.orderDate());
        order.setDeliveryDate(command.deliveryDate());
        order.setDeliveryTerms(command.deliveryTerms());
        order.setRemark(command.remark());
        order.setExceptionPurchaseFlag(0);
        order.setExceptionReason(null);
        order.setPricingMode(contract.getPricingMode());
        order.setApprovalStatus("DRAFT");
        order.setOrderStatus("DRAFT");
        order.setBudgetRevision(0);
        order.setTotalAmount(BigDecimal.ZERO);
        insertWithGeneratedCode(order, tenantId);

        BigDecimal total = BigDecimal.ZERO;
        for (MatPurchaseRequestItem requestItem : requestItems) {
            MatPurchaseOrderItem orderItem = toOrderItem(requestItem, order, contract, budgetLineId, tenantId);
            orderItemMapper.insert(orderItem);
            total = total.add(orderItem.getAmount());
        }
        orderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getId, order.getId())
                .eq(MatPurchaseOrder::getTenantId, tenantId)
                .set(MatPurchaseOrder::getTotalAmount, total.setScale(2, RoundingMode.HALF_UP)));

        int marked = requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, request.getId())
                .eq(MatPurchaseRequest::getTenantId, tenantId)
                .eq(MatPurchaseRequest::getApprovalStatus, "APPROVED")
                .eq(MatPurchaseRequest::getStatus, "APPROVED")
                .set(MatPurchaseRequest::getStatus, "CONVERTED"));
        if (marked != 1) {
            throw new BusinessException("REQUEST_ALREADY_CONVERTED", "采购申请状态已变化，不可重复转单");
        }
        log.info("采购申请显式创建采购订单完成 requestId={} -> orderId={} poCode={}",
                request.getId(), order.getId(), order.getOrderCode());
        return order.getId();
    }

    private void insertWithGeneratedCode(MatPurchaseOrder order, Long tenantId) {
        String prefix = "PO-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            order.setOrderCode(nextOrderCode(prefix, tenantId, attempt));
            try {
                orderMapper.insert(order);
                return;
            } catch (DuplicateKeyException exception) {
                log.warn("采购申请显式转单编号冲突，重试生成 orderCode={}", order.getOrderCode());
            }
        }
        throw new BusinessException("PURCHASE_ORDER_CODE_CONFLICT", "采购订单编号生成冲突，请重试");
    }

    private String nextOrderCode(String prefix, Long tenantId, int offset) {
        Page<MatPurchaseOrder> page = orderMapper.selectPage(new Page<>(1, 1),
                new LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getTenantId, tenantId)
                        .likeRight(MatPurchaseOrder::getOrderCode, prefix)
                        .orderByDesc(MatPurchaseOrder::getOrderCode));
        MatPurchaseOrder last = page.getRecords().isEmpty() ? null : page.getRecords().getFirst();
        int sequence = 1 + offset;
        if (last != null && last.getOrderCode() != null && last.getOrderCode().startsWith(prefix)) {
            try {
                sequence = Integer.parseInt(last.getOrderCode().substring(prefix.length())) + 1 + offset;
            } catch (NumberFormatException exception) {
                log.warn("采购订单编号后缀无法解析，改用候选序号：{}", last.getOrderCode());
            }
        }
        return prefix + String.format("%03d", sequence);
    }

    private Long resolveContractBudgetLine(Long contractId, Long projectId, Long tenantId) {
        List<ContractBudgetAllocation> allocations = contractBudgetAllocationMapper.selectList(
                new LambdaQueryWrapper<ContractBudgetAllocation>()
                        .eq(ContractBudgetAllocation::getTenantId, tenantId)
                        .eq(ContractBudgetAllocation::getContractId, contractId)
                        .eq(ContractBudgetAllocation::getProjectId, projectId));
        if (allocations.isEmpty()) {
            throw new BusinessException("PURCHASE_CONTRACT_BUDGET_ALLOCATION_REQUIRED", "采购合同必须先完成预算分摊");
        }
        if (allocations.size() > 1) {
            throw new BusinessException("PURCHASE_CONTRACT_BUDGET_ALLOCATION_AMBIGUOUS", "采购合同存在多个预算分摊，无法自动确定订单预算科目");
        }
        return allocations.getFirst().getBudgetLineId();
    }

    private MatPurchaseOrderItem toOrderItem(MatPurchaseRequestItem requestItem,
                                              MatPurchaseOrder order, CtContract contract,
                                              Long budgetLineId, Long tenantId) {
        BigDecimal quantity = requestItem.getApprovedQuantity();
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("PURCHASE_REQUEST_APPROVED_QUANTITY_INVALID", "采购申请审批数量缺失或非法");
        }
        if (requestItem.getMaterialId() == null) {
            throw new BusinessException("PURCHASE_REQUEST_ITEM_INCOMPLETE", "采购申请明细物料不能为空");
        }
        CtContractItem contractItem = pricingService.requireUniqueContractItem(contract, requestItem.getMaterialId());
        BigDecimal unitPrice;
        String priceSource;
        Long receiptItemId = null;
        if ("FIXED".equals(contract.getPricingMode())) {
            unitPrice = contractItem.getUnitPrice();
            if (unitPrice == null || unitPrice.signum() <= 0) {
                throw new BusinessException("PURCHASE_CONTRACT_PRICE_INVALID", "固定价合同材料单价缺失");
            }
            priceSource = "CONTRACT_ITEM";
        } else {
            Map<String, Object> recent = pricingService.findRecentReceipt(
                    tenantId, contract.getPartyBId(), requestItem.getMaterialId());
            if (recent == null || recent.get("unit_price") == null
                    || ((BigDecimal) recent.get("unit_price")).signum() <= 0) {
                throw new BusinessException("PURCHASE_ORDER_RECENT_PRICE_REQUIRED", "实际价合同缺少最近入库价，无法自动创建订单");
            }
            unitPrice = (BigDecimal) recent.get("unit_price");
            priceSource = "RECENT_RECEIPT";
            receiptItemId = ((Number) recent.get("id")).longValue();
        }
        BigDecimal taxRate = contractItem.getTaxRate() == null ? BigDecimal.ZERO : contractItem.getTaxRate();
        BigDecimal amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal withoutTax = taxRate.signum() == 0 ? amount
                : amount.multiply(new BigDecimal("100"))
                .divide(new BigDecimal("100").add(taxRate), 2, RoundingMode.HALF_UP);

        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setId(IdWorker.getId());
        item.setTenantId(tenantId);
        item.setOrderId(order.getId());
        item.setRequestItemId(requestItem.getId());
        item.setWbsTaskId(requestItem.getWbsTaskId());
        item.setBudgetLineId(budgetLineId);
        item.setProjectId(order.getProjectId());
        item.setMaterialId(requestItem.getMaterialId());
        item.setContractItemId(contractItem.getId());
        item.setMaterialName(StringUtils.hasText(requestItem.getMaterialName())
                ? requestItem.getMaterialName() : contractItem.getItemName());
        item.setSpecification(StringUtils.hasText(requestItem.getSpecification())
                ? requestItem.getSpecification() : contractItem.getItemSpec());
        item.setUnit(StringUtils.hasText(requestItem.getUnit()) ? requestItem.getUnit() : contractItem.getUnit());
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setTaxRate(taxRate);
        item.setAmount(amount);
        item.setAmountWithoutTax(withoutTax);
        item.setTaxAmount(amount.subtract(withoutTax));
        item.setReceivedQuantity(BigDecimal.ZERO);
        item.setPriceSource(priceSource);
        item.setPriceSourceReceiptItemId(receiptItemId);
        item.setCreatedBy(UserContext.getCurrentUserId());
        item.setUpdatedBy(UserContext.getCurrentUserId());
        return item;
    }
}
