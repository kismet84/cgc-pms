package com.cgcpms.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
class DashboardMaterialLookupService {

    private final MatPurchaseRequestItemMapper purchaseRequestItemMapper;
    private final MatPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final MatReceiptItemMapper receiptItemMapper;
    private final MatWarehouseMapper warehouseMapper;
    private final MatStockMapper stockMapper;
    private final MdPartnerMapper partnerMapper;
    private final MdMaterialMapper materialMapper;
    private final SysUserMapper userMapper;

    PurchaseSnapshot loadPurchase(Long tenantId,
                                  List<MatPurchaseRequest> requests,
                                  List<MatPurchaseOrder> orders,
                                  List<MatReceipt> receipts,
                                  List<MatPurchaseOrder> scoreOrders,
                                  List<MatReceipt> allReceipts,
                                  List<Long> projectIds) {
        Set<Long> orderIds = Stream.concat(orders.stream(), scoreOrders.stream())
                .map(MatPurchaseOrder::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> scoreOrderIds = scoreOrders.stream()
                .map(MatPurchaseOrder::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> receiptIds = Stream.concat(
                        receipts.stream(),
                        allReceipts.stream().filter(receipt -> "APPROVED".equals(receipt.getApprovalStatus())
                                && receipt.getOrderId() != null && scoreOrderIds.contains(receipt.getOrderId())))
                .map(MatReceipt::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<MatPurchaseRequestItem> requestItems = loadRequestItems(tenantId, requests);
        List<MatPurchaseOrderItem> orderItems = loadOrderItems(tenantId, orderIds);
        List<MatReceiptItem> receiptItems = loadReceiptItems(tenantId, receiptIds);
        Map<Long, String> materialNames = loadMaterialNames(tenantId,
                Stream.of(
                                requestItems.stream().map(MatPurchaseRequestItem::getMaterialId),
                                orderItems.stream().map(MatPurchaseOrderItem::getMaterialId),
                                receiptItems.stream().map(MatReceiptItem::getMaterialId))
                        .flatMap(Function.identity())
                        .collect(Collectors.toSet()));

        Map<Long, String> orderSummaries = summarizeOrderItems(orderItems, materialNames);
        return new PurchaseSnapshot(
                loadPartnerNames(tenantId, Stream.concat(
                                orders.stream().map(MatPurchaseOrder::getPartnerId),
                                receipts.stream().map(MatReceipt::getPartnerId))
                        .collect(Collectors.toSet())),
                loadUserNames(tenantId, requests.stream()
                        .map(MatPurchaseRequest::getCreatedBy)
                        .collect(Collectors.toSet())),
                summarizeRequestItems(requestItems, materialNames),
                orderSummaries,
                summarizeReceiptItems(receipts, receiptItems, orderSummaries, materialNames),
                orderItems.stream().collect(Collectors.groupingBy(MatPurchaseOrderItem::getOrderId)),
                receiptItems.stream().collect(Collectors.groupingBy(MatReceiptItem::getReceiptId)),
                countLowStockItems(tenantId, projectIds));
    }

    ProductionSnapshot loadProduction(Long tenantId,
                                      List<MatReceipt> receipts,
                                      List<MatRequisition> requisitions,
                                      List<SubMeasure> measures,
                                      List<Long> projectIds) {
        Set<Long> partnerIds = new HashSet<>();
        receipts.stream().map(MatReceipt::getPartnerId).filter(Objects::nonNull).forEach(partnerIds::add);
        requisitions.stream().map(MatRequisition::getPartnerId).filter(Objects::nonNull).forEach(partnerIds::add);
        measures.stream().map(SubMeasure::getPartnerId).filter(Objects::nonNull).forEach(partnerIds::add);

        Set<Long> userIds = new HashSet<>();
        receipts.stream().map(MatReceipt::getReceiverId).filter(Objects::nonNull).forEach(userIds::add);
        requisitions.stream().map(MatRequisition::getRequisitionerId).filter(Objects::nonNull).forEach(userIds::add);

        Set<Long> receiptIds = receipts.stream().map(MatReceipt::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<MatReceiptItem> receiptItems = loadReceiptItems(tenantId, receiptIds);
        Map<Long, String> materialNames = loadMaterialNames(tenantId, receiptItems.stream()
                .map(MatReceiptItem::getMaterialId).collect(Collectors.toSet()));
        return new ProductionSnapshot(
                loadPartnerNames(tenantId, partnerIds),
                loadUserNames(tenantId, userIds),
                summarizeReceiptItems(receipts, receiptItems, Collections.emptyMap(), materialNames),
                countLowStockItems(tenantId, projectIds));
    }

    Map<Long, String> loadUserNames(Long tenantId, Collection<Long> ids) {
        Set<Long> userIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .in(SysUser::getId, userIds))
                .stream()
                .collect(Collectors.toMap(SysUser::getId,
                        user -> StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername(),
                        (left, right) -> left));
    }

    private List<MatPurchaseRequestItem> loadRequestItems(Long tenantId, List<MatPurchaseRequest> requests) {
        List<Long> ids = requests.stream().map(MatPurchaseRequest::getId).filter(Objects::nonNull).toList();
        return ids.isEmpty() ? Collections.emptyList()
                : purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseRequestItem>()
                .eq(MatPurchaseRequestItem::getTenantId, tenantId)
                .in(MatPurchaseRequestItem::getRequestId, ids));
    }

    private List<MatPurchaseOrderItem> loadOrderItems(Long tenantId, Set<Long> orderIds) {
        return orderIds.isEmpty() ? Collections.emptyList()
                : purchaseOrderItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseOrderItem>()
                .eq(MatPurchaseOrderItem::getTenantId, tenantId)
                .in(MatPurchaseOrderItem::getOrderId, orderIds));
    }

    private List<MatReceiptItem> loadReceiptItems(Long tenantId, Set<Long> receiptIds) {
        return receiptIds.isEmpty() ? Collections.emptyList()
                : receiptItemMapper.selectList(new LambdaQueryWrapper<MatReceiptItem>()
                .eq(MatReceiptItem::getTenantId, tenantId)
                .in(MatReceiptItem::getReceiptId, receiptIds));
    }

    private Map<Long, String> loadPartnerNames(Long tenantId, Collection<Long> ids) {
        Set<Long> partnerIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (partnerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return partnerMapper.selectList(new LambdaQueryWrapper<MdPartner>()
                        .eq(MdPartner::getTenantId, tenantId)
                        .in(MdPartner::getId, partnerIds))
                .stream()
                .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (left, right) -> left));
    }

    private Map<Long, String> loadMaterialNames(Long tenantId, Collection<Long> ids) {
        Set<Long> materialIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (materialIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return materialMapper.selectList(new LambdaQueryWrapper<MdMaterial>()
                        .eq(MdMaterial::getTenantId, tenantId)
                        .in(MdMaterial::getId, materialIds))
                .stream()
                .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (left, right) -> left));
    }

    private Map<Long, String> summarizeRequestItems(List<MatPurchaseRequestItem> items,
                                                    Map<Long, String> materialNames) {
        return items.stream().collect(Collectors.groupingBy(MatPurchaseRequestItem::getRequestId,
                Collectors.collectingAndThen(Collectors.toList(), grouped -> summarize(grouped.stream()
                        .map(item -> materialNames.get(item.getMaterialId())).toList()))));
    }

    private Map<Long, String> summarizeOrderItems(List<MatPurchaseOrderItem> items,
                                                  Map<Long, String> materialNames) {
        return items.stream().collect(Collectors.groupingBy(MatPurchaseOrderItem::getOrderId,
                Collectors.collectingAndThen(Collectors.toList(), grouped -> summarize(grouped.stream()
                        .map(item -> StringUtils.hasText(item.getMaterialName())
                                ? item.getMaterialName() : materialNames.get(item.getMaterialId()))
                        .toList()))));
    }

    private Map<Long, String> summarizeReceiptItems(List<MatReceipt> receipts,
                                                    List<MatReceiptItem> items,
                                                    Map<Long, String> orderSummaries,
                                                    Map<Long, String> materialNames) {
        Map<Long, String> summaries = receipts.stream()
                .filter(receipt -> StringUtils.hasText(orderSummaries.get(receipt.getOrderId())))
                .collect(Collectors.toMap(MatReceipt::getId,
                        receipt -> orderSummaries.get(receipt.getOrderId()), (left, right) -> left));
        summaries.putAll(items.stream().collect(Collectors.groupingBy(MatReceiptItem::getReceiptId,
                Collectors.collectingAndThen(Collectors.toList(), grouped -> summarize(grouped.stream()
                        .map(item -> materialNames.get(item.getMaterialId())).toList())))));
        return summaries;
    }

    private String summarize(List<String> names) {
        List<String> distinct = names.stream().filter(StringUtils::hasText).distinct().toList();
        if (distinct.isEmpty()) {
            return null;
        }
        String summary = String.join("、", distinct.stream().limit(3).toList());
        return distinct.size() > 3 ? summary + " 等" + distinct.size() + "项" : summary;
    }

    private Long countLowStockItems(Long tenantId, List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return 0L;
        }
        List<Long> warehouseIds = warehouseMapper.selectList(new LambdaQueryWrapper<MatWarehouse>()
                        .eq(MatWarehouse::getTenantId, tenantId)
                        .in(MatWarehouse::getProjectId, projectIds))
                .stream().map(MatWarehouse::getId).toList();
        if (warehouseIds.isEmpty()) {
            return 0L;
        }
        return stockMapper.selectCount(new LambdaQueryWrapper<MatStock>()
                .eq(MatStock::getTenantId, tenantId)
                .in(MatStock::getWarehouseId, warehouseIds)
                .le(MatStock::getAvailableQty, BigDecimal.ZERO));
    }

    record PurchaseSnapshot(
            Map<Long, String> partnerNames,
            Map<Long, String> ownerNames,
            Map<Long, String> requestSummaries,
            Map<Long, String> orderSummaries,
            Map<Long, String> receiptSummaries,
            Map<Long, List<MatPurchaseOrderItem>> orderItemsByOrder,
            Map<Long, List<MatReceiptItem>> receiptItemsByReceipt,
            Long lowStockItemCount) {
    }

    record ProductionSnapshot(
            Map<Long, String> partnerNames,
            Map<Long, String> ownerNames,
            Map<Long, String> receiptSummaries,
            Long lowStockItemCount) {
    }
}
