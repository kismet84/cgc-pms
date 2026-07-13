package com.cgcpms.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.cost.vo.CostProjectSummaryVO;
import com.cgcpms.cost.vo.CostSummaryVO;
import com.cgcpms.dashboard.vo.*;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.tech.entity.TechItem;
import com.cgcpms.tech.mapper.TechItemMapper;
import com.cgcpms.tech.vo.ChiefEngineerDashboardVO;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DashboardMaterialRoleService extends DashboardSharedSupport {

    private final ProjectAccessChecker projectAccessChecker;

    public DashboardMaterialRoleService(
            CostSummaryService costSummaryService,
            CostSummaryMapper costSummaryMapper,
            CostSubjectMapper costSubjectMapper,
            CostItemMapper costItemMapper,
            PmProjectMapper projectMapper,
            CtContractMapper ctContractMapper,
            WfTaskMapper wfTaskMapper,
            WfInstanceMapper wfInstanceMapper,
            PayRecordMapper payRecordMapper,
            StlSettlementMapper stlSettlementMapper,
            VarOrderMapper varOrderMapper,
            SubMeasureMapper subMeasureMapper,
            AlertLogMapper alertLogMapper,
            MatPurchaseRequestMapper purchaseRequestMapper,
            MatPurchaseRequestItemMapper purchaseRequestItemMapper,
            MatPurchaseOrderMapper purchaseOrderMapper,
            MatPurchaseOrderItemMapper purchaseOrderItemMapper,
            MatReceiptMapper receiptMapper,
            MatReceiptItemMapper receiptItemMapper,
            MatRequisitionMapper requisitionMapper,
            MatWarehouseMapper warehouseMapper,
            MatStockMapper stockMapper,
            TechItemMapper techItemMapper,
            MdPartnerMapper partnerMapper,
            MdMaterialMapper materialMapper,
            SysUserMapper userMapper,
            ProjectAccessChecker projectAccessChecker) {
        super(costSummaryService, costSummaryMapper, costSubjectMapper, costItemMapper, projectMapper, ctContractMapper, wfTaskMapper, wfInstanceMapper, payRecordMapper, stlSettlementMapper, varOrderMapper, subMeasureMapper, alertLogMapper, purchaseRequestMapper, purchaseRequestItemMapper, purchaseOrderMapper, purchaseOrderItemMapper, receiptMapper, receiptItemMapper, requisitionMapper, warehouseMapper, stockMapper, techItemMapper, partnerMapper, materialMapper, userMapper);
        this.projectAccessChecker = projectAccessChecker;
    }

    public PurchaseManagerDashboardVO getPurchaseManagerView(Long projectId) {
        return getPurchaseManagerView(projectId, (String) null);
    }

    public PurchaseManagerDashboardVO getPurchaseManagerView(Long projectId, String month) {
        Long tenantId = UserContext.getCurrentTenantId();
        YearMonth selectedMonth = parseDashboardMonth(month);
        List<PmProject> projects = resolvePurchaseProjects(tenantId, projectId);
        List<Long> projectIds = projects.stream().map(PmProject::getId).collect(Collectors.toList());
        Map<Long, String> projectNameMap = projectNameMap(projects);

        PurchaseManagerDashboardVO vo = new PurchaseManagerDashboardVO();
        vo.setProjectId(projectId != null ? projectId.toString() : null);
        vo.setProjectName(projectId != null ? projects.get(0).getProjectName() : "全部项目");
        if (projectIds.isEmpty()) {
            applyEmptyPurchaseManager(vo);
            return vo;
        }

        List<MatPurchaseRequest> requests = purchaseRequestMapper.selectList(new LambdaQueryWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getTenantId, tenantId)
                .in(MatPurchaseRequest::getProjectId, projectIds)
                .orderByDesc(MatPurchaseRequest::getCreatedTime));
        if (selectedMonth != null) {
            requests = requests.stream()
                    .filter(r -> r.getCreatedTime() != null
                            && !r.getCreatedTime().toLocalDate().isBefore(selectedMonth.atDay(1))
                            && !r.getCreatedTime().toLocalDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }

        // Load all orders; split into orderDate-scoped (purchase tabs/amount)
        // and deliveryDate-scoped (overdue delivery) when month is selected.
        List<MatPurchaseOrder> allOrders = purchaseOrderMapper.selectList(new LambdaQueryWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getTenantId, tenantId)
                .in(MatPurchaseOrder::getProjectId, projectIds));

        List<MatPurchaseOrder> orders;
        List<MatPurchaseOrder> overdueOrders;
        if (selectedMonth != null) {
            orders = allOrders.stream()
                    .filter(o -> o.getOrderDate() != null
                            && !o.getOrderDate().isBefore(selectedMonth.atDay(1))
                            && !o.getOrderDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
            overdueOrders = allOrders.stream()
                    .filter(o -> o.getDeliveryDate() != null
                            && !o.getDeliveryDate().isBefore(selectedMonth.atDay(1))
                            && !o.getDeliveryDate().isAfter(selectedMonth.atEndOfMonth())
                            && o.getDeliveryDate().isBefore(LocalDate.now()))
                    .filter(o -> !"COMPLETED".equals(o.getOrderStatus()) && !"CANCELLED".equals(o.getOrderStatus()))
                    .collect(Collectors.toList());
        } else {
            orders = allOrders;
            overdueOrders = allOrders.stream()
                    .filter(o -> o.getDeliveryDate() != null && o.getDeliveryDate().isBefore(LocalDate.now()))
                    .filter(o -> !"COMPLETED".equals(o.getOrderStatus()) && !"CANCELLED".equals(o.getOrderStatus()))
                    .collect(Collectors.toList());
        }

        List<MatReceipt> allReceipts = receiptMapper.selectList(new LambdaQueryWrapper<MatReceipt>()
                .eq(MatReceipt::getTenantId, tenantId)
                .in(MatReceipt::getProjectId, projectIds)
                .orderByDesc(MatReceipt::getReceiptDate));
        List<MatReceipt> receipts = allReceipts;
        if (selectedMonth != null) {
            receipts = allReceipts.stream()
                    .filter(r -> r.getReceiptDate() != null
                            && !r.getReceiptDate().isBefore(selectedMonth.atDay(1))
                            && !r.getReceiptDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }

        List<MatReceipt> pendingReceipts = receipts.stream()
                .filter(r -> !"APPROVED".equals(r.getApprovalStatus()))
                .collect(Collectors.toList());

        // Merge order scopes so partner/order summary maps cover both purchaseOrders and overdueOrders
        List<MatPurchaseOrder> scoreOrders = allOrders.stream()
                .filter(o -> o.getPartnerId() != null && o.getDeliveryDate() != null)
                .filter(o -> "APPROVED".equals(o.getApprovalStatus()) && !"CANCELLED".equals(o.getOrderStatus()))
                .filter(o -> o.getDeliveryDate().isBefore(LocalDate.now()))
                .filter(o -> selectedMonth == null || YearMonth.from(o.getDeliveryDate()).equals(selectedMonth))
                .collect(Collectors.toList());
        List<MatPurchaseOrder> mapOrders = selectedMonth != null
                ? Stream.of(orders, overdueOrders, scoreOrders).flatMap(Collection::stream).distinct().collect(Collectors.toList())
                : orders;
        Map<Long, String> partnerNameMap = partnerNameMap(tenantId, mapOrders, receipts);
        Map<Long, String> ownerNameMap = ownerNameMap(tenantId, requests);
        Map<Long, String> requestSummaryMap = requestSummaryMap(tenantId, requests);
        Map<Long, String> orderSummaryMap = orderSummaryMap(tenantId, mapOrders);
        Map<Long, String> receiptSummaryMap = receiptSummaryMap(tenantId, receipts, orderSummaryMap);

        vo.setPendingRequestCount(requests.stream().filter(r -> !"APPROVED".equals(r.getApprovalStatus())).count());
        vo.setActiveOrderCount(orders.stream().filter(o -> !"COMPLETED".equals(o.getOrderStatus()) && !"CANCELLED".equals(o.getOrderStatus())).count());
        vo.setOverdueDeliveryCount((long) overdueOrders.size());
        vo.setPendingReceiptCount((long) pendingReceipts.size());
        vo.setLowStockItemCount(countLowStockItems(tenantId, projectIds));
        vo.setTotalOrderAmount(orders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .toPlainString());
        vo.setRecentRequests(requests.stream()
                .limit(5)
                .map(r -> toBusinessItem("PURCHASE_REQUEST", r, projectNameMap, ownerNameMap, requestSummaryMap))
                .collect(Collectors.toList()));
        vo.setPurchaseOrders(orders.stream()
                .sorted(Comparator
                        .comparing((MatPurchaseOrder o) -> o.getOrderDate() != null ? o.getOrderDate() : LocalDate.MIN)
                        .reversed()
                        .thenComparing(o -> amountOrZero(o.getTotalAmount()), Comparator.reverseOrder()))
                .limit(5)
                .map(o -> {
                    DashboardBusinessItemVO item = toBusinessItem("PURCHASE_ORDER", o, projectNameMap, partnerNameMap, orderSummaryMap);
                    item.setDate(o.getOrderDate() != null ? o.getOrderDate().toString() : null);
                    return item;
                })
                .collect(Collectors.toList()));
        vo.setOverdueOrders(overdueOrders.stream()
                .sorted(Comparator
                        .comparingLong((MatPurchaseOrder o) -> overdueDays(o.getDeliveryDate())).reversed()
                        .thenComparing(o -> amountOrZero(o.getTotalAmount()), Comparator.reverseOrder()))
                .limit(5)
                .map(o -> toBusinessItem("PURCHASE_ORDER", o, projectNameMap, partnerNameMap, orderSummaryMap))
                .collect(Collectors.toList()));
        vo.setPendingReceipts(pendingReceipts.stream()
                .sorted(Comparator
                        .comparingLong((MatReceipt r) -> pendingDays(r.getReceiptDate())).reversed()
                        .thenComparing(r -> amountOrZero(r.getTotalAmount()), Comparator.reverseOrder()))
                .limit(5)
                .map(r -> toBusinessItem("MATERIAL_RECEIPT", r, projectNameMap, partnerNameMap, receiptSummaryMap))
                .collect(Collectors.toList()));
        vo.setSupplierScores(supplierScores(scoreOrders, allReceipts, partnerNameMap));
        return vo;
    }

    private List<PmProject> resolvePurchaseProjects(Long tenantId, Long projectId) {
        if (projectId != null) {
            PmProject project = requireProject(tenantId, projectId);
            projectAccessChecker.checkAccess(project, "查看采购驾驶舱");
            return List.of(project);
        }
        List<PmProject> projects = projectMapper.selectList(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getTenantId, tenantId)
                .eq(PmProject::getStatus, "ACTIVE"));
        return projectAccessChecker.filterAccessible(projects);
    }

    private List<DashboardSupplierScoreVO> supplierScores(List<MatPurchaseOrder> orders,
                                                           List<MatReceipt> receipts,
                                                           Map<Long, String> partnerNameMap) {
        if (orders.isEmpty()) return Collections.emptyList();
        Set<Long> orderIds = orders.stream().map(MatPurchaseOrder::getId).collect(Collectors.toSet());
        List<MatPurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>().in(MatPurchaseOrderItem::getOrderId, orderIds));
        Map<Long, List<MatPurchaseOrderItem>> orderItemsByOrder = orderItems.stream()
                .collect(Collectors.groupingBy(MatPurchaseOrderItem::getOrderId));

        List<MatReceipt> approvedReceipts = receipts.stream()
                .filter(r -> "APPROVED".equals(r.getApprovalStatus()) && r.getOrderId() != null
                        && orderIds.contains(r.getOrderId()) && r.getReceiptDate() != null)
                .sorted(Comparator.comparing(MatReceipt::getReceiptDate))
                .collect(Collectors.toList());
        Set<Long> receiptIds = approvedReceipts.stream().map(MatReceipt::getId).collect(Collectors.toSet());
        Map<Long, List<MatReceiptItem>> receiptItemsByReceipt = receiptIds.isEmpty()
                ? Collections.emptyMap()
                : receiptItemMapper.selectList(new LambdaQueryWrapper<MatReceiptItem>()
                        .in(MatReceiptItem::getReceiptId, receiptIds)).stream()
                .collect(Collectors.groupingBy(MatReceiptItem::getReceiptId));
        Map<Long, List<MatReceipt>> receiptsByOrder = approvedReceipts.stream()
                .collect(Collectors.groupingBy(MatReceipt::getOrderId));

        return orders.stream()
                .collect(Collectors.groupingBy(MatPurchaseOrder::getPartnerId))
                .entrySet().stream()
                .map(entry -> {
                    long orderCount = entry.getValue().size();
                    long lateCompletedCount = 0;
                    long overdueIncompleteCount = 0;
                    for (MatPurchaseOrder order : entry.getValue()) {
                        LocalDate completedAt = deliveryCompletedAt(order, orderItemsByOrder,
                                receiptsByOrder, receiptItemsByReceipt);
                        if (completedAt == null) {
                            overdueIncompleteCount++;
                        } else if (completedAt.isAfter(order.getDeliveryDate())) {
                            lateCompletedCount++;
                        }
                    }
                    long overdueCount = lateCompletedCount + overdueIncompleteCount;
                    BigDecimal onTimeRate = BigDecimal.valueOf(orderCount - overdueCount)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

                    DashboardSupplierScoreVO score = new DashboardSupplierScoreVO();
                    score.setPartnerId(String.valueOf(entry.getKey()));
                    score.setPartnerName(partnerNameMap.get(entry.getKey()));
                    score.setOrderCount(orderCount);
                    score.setOverdueOrderCount(overdueCount);
                    score.setLateCompletedCount(lateCompletedCount);
                    score.setOverdueIncompleteCount(overdueIncompleteCount);
                    score.setOnTimeDeliveryRate(onTimeRate.toPlainString());
                    score.setPerformanceScore(onTimeRate.setScale(0, RoundingMode.HALF_UP).toPlainString());
                    return score;
                })
                .sorted(Comparator
                        .comparing((DashboardSupplierScoreVO s) -> new BigDecimal(s.getPerformanceScore())).reversed()
                        .thenComparing(DashboardSupplierScoreVO::getPartnerName, Comparator.nullsLast(String::compareTo))
                        .thenComparingLong(s -> Long.parseLong(s.getPartnerId())))
                .limit(5)
                .collect(Collectors.toList());
    }

    private LocalDate deliveryCompletedAt(MatPurchaseOrder order,
                                           Map<Long, List<MatPurchaseOrderItem>> orderItemsByOrder,
                                           Map<Long, List<MatReceipt>> receiptsByOrder,
                                           Map<Long, List<MatReceiptItem>> receiptItemsByReceipt) {
        List<MatPurchaseOrderItem> items = orderItemsByOrder.getOrDefault(order.getId(), Collections.emptyList());
        if (items.isEmpty() || items.stream().anyMatch(i -> i.getQuantity() == null || i.getQuantity().signum() <= 0)) {
            return null;
        }
        Map<Long, BigDecimal> received = new HashMap<>();
        for (MatReceipt receipt : receiptsByOrder.getOrDefault(order.getId(), Collections.emptyList())) {
            for (MatReceiptItem item : receiptItemsByReceipt.getOrDefault(receipt.getId(), Collections.emptyList())) {
                if (item.getOrderItemId() != null && item.getActualQuantity() != null
                        && item.getActualQuantity().signum() > 0) {
                    received.merge(item.getOrderItemId(), item.getActualQuantity(), BigDecimal::add);
                }
            }
            if (items.stream().allMatch(i -> received.getOrDefault(i.getId(), BigDecimal.ZERO)
                    .compareTo(i.getQuantity()) >= 0)) {
                return receipt.getReceiptDate();
            }
        }
        return null;
    }

    // ========================================================================
    // 5. Production Manager Dashboard (MVP)
    // ========================================================================
    public ProductionManagerDashboardVO getProductionManagerView(Long projectId) {
        return getProductionManagerView(projectId, (String) null);
    }

    public ProductionManagerDashboardVO getProductionManagerView(Long projectId, String month) {
        Long tenantId = UserContext.getCurrentTenantId();
        YearMonth selectedMonth = parseDashboardMonth(month);
        List<PmProject> projects = resolveDashboardProjects(tenantId, projectId);
        List<Long> projectIds = projects.stream().map(PmProject::getId).collect(Collectors.toList());
        Map<Long, String> projectNameMap = projectNameMap(projects);

        ProductionManagerDashboardVO vo = new ProductionManagerDashboardVO();
        vo.setProjectId(projectId != null ? projectId.toString() : null);
        vo.setProjectName(projectId != null ? projects.get(0).getProjectName() : "全部项目");
        if (projectIds.isEmpty()) {
            applyEmptyProductionManager(vo);
            return vo;
        }

        List<MatReceipt> receipts = receiptMapper.selectList(new LambdaQueryWrapper<MatReceipt>()
                .eq(MatReceipt::getTenantId, tenantId)
                .in(MatReceipt::getProjectId, projectIds)
                .orderByDesc(MatReceipt::getReceiptDate));
        if (selectedMonth != null) {
            receipts = receipts.stream()
                    .filter(r -> r.getReceiptDate() != null
                            && !r.getReceiptDate().isBefore(selectedMonth.atDay(1))
                            && !r.getReceiptDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }

        List<MatRequisition> requisitions = requisitionMapper.selectList(new LambdaQueryWrapper<MatRequisition>()
                .eq(MatRequisition::getTenantId, tenantId)
                .in(MatRequisition::getProjectId, projectIds)
                .orderByDesc(MatRequisition::getRequisitionDate));
        if (selectedMonth != null) {
            requisitions = requisitions.stream()
                    .filter(r -> r.getRequisitionDate() != null
                            && !r.getRequisitionDate().isBefore(selectedMonth.atDay(1))
                            && !r.getRequisitionDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }

        List<SubMeasure> measures = subMeasureMapper.selectList(new LambdaQueryWrapper<SubMeasure>()
                .eq(SubMeasure::getTenantId, tenantId)
                .in(SubMeasure::getProjectId, projectIds)
                .orderByDesc(SubMeasure::getMeasureDate));
        if (selectedMonth != null) {
            measures = measures.stream()
                    .filter(m -> m.getMeasureDate() != null
                            && !m.getMeasureDate().isBefore(selectedMonth.atDay(1))
                            && !m.getMeasureDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }

        Map<Long, String> partnerNameMap = partnerNameMap(tenantId, businessPartnerIds(receipts, requisitions, measures));
        Map<Long, String> ownerNameMap = userNameMap(tenantId, businessUserIds(receipts, requisitions));
        Map<Long, String> receiptSummaryMap = receiptSummaryMap(tenantId, receipts, Collections.emptyMap());

        vo.setReceiptCount((long) receipts.size());
        vo.setRequisitionCount((long) requisitions.size());
        vo.setPendingStockOutCount(requisitions.stream().filter(r -> r.getStockOutFlag() == null || r.getStockOutFlag() == 0).count());
        vo.setSubMeasureCount((long) measures.size());
        vo.setLowStockItemCount(countLowStockItems(tenantId, projectIds));
        vo.setConfirmedMeasureAmount(measures.stream()
                .filter(m -> "APPROVED".equals(m.getApprovalStatus()) || "CONFIRMED".equals(m.getStatus()))
                .map(m -> m.getApprovedAmount() != null ? m.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .toPlainString());
        vo.setRecentReceipts(receipts.stream().limit(5).map(r -> toBusinessItem("MATERIAL_RECEIPT", r, projectNameMap, partnerNameMap, ownerNameMap, receiptSummaryMap)).collect(Collectors.toList()));
        vo.setRecentRequisitions(requisitions.stream().limit(5).map(r -> toBusinessItem("MATERIAL_REQUISITION", r, projectNameMap, partnerNameMap, ownerNameMap)).collect(Collectors.toList()));
        vo.setRecentSubMeasures(measures.stream().limit(5).map(m -> toBusinessItem("SUB_MEASURE", m, projectNameMap, partnerNameMap)).collect(Collectors.toList()));
        return vo;
    }

    // ========================================================================
    // 6. Chief Engineer Dashboard
    // ========================================================================
    public ChiefEngineerDashboardVO getChiefEngineerView(Long projectId) {
        return getChiefEngineerView(projectId, (String) null);
    }

    public ChiefEngineerDashboardVO getChiefEngineerView(Long projectId, String month) {
        Long tenantId = UserContext.getCurrentTenantId();
        YearMonth selectedMonth = parseDashboardMonth(month);
        List<PmProject> projects = resolveDashboardProjects(tenantId, projectId);
        List<Long> projectIds = projects.stream().map(PmProject::getId).collect(Collectors.toList());
        Map<Long, String> projectNameMap = projectNameMap(projects);

        ChiefEngineerDashboardVO vo = new ChiefEngineerDashboardVO();
        vo.setProjectId(projectId != null ? projectId.toString() : null);
        vo.setProjectName(projectId != null ? projects.get(0).getProjectName() : "全部项目");
        if (projectIds.isEmpty()) {
            vo.setPendingReviewCount(0L);
            vo.setPendingCoordinationCount(0L);
            vo.setOpenIssueCount(0L);
            vo.setOverdueCount(0L);
            vo.setPendingReviews(Collections.emptyList());
            vo.setPendingCoordinations(Collections.emptyList());
            vo.setOpenIssues(Collections.emptyList());
            vo.setOverdueItems(Collections.emptyList());
            return vo;
        }

        List<TechItem> items = techItemMapper.selectList(new LambdaQueryWrapper<TechItem>()
                .eq(TechItem::getTenantId, tenantId)
                .in(TechItem::getProjectId, projectIds)
                .orderByDesc(TechItem::getDiscoveredAt));
        if (selectedMonth != null) {
            items = items.stream()
                    .filter(i -> isTechItemInMonth(i, selectedMonth))
                    .collect(Collectors.toList());
        }

        Map<Long, String> ownerNameMap = userNameMap(tenantId, items.stream()
                .map(TechItem::getResponsibleUserId)
                .collect(Collectors.toSet()));

        vo.setPendingReviewCount(items.stream().filter(i -> "TECH_REVIEW".equals(i.getItemType())
                && !"CLOSED".equals(i.getItemStatus())).count());
        vo.setPendingCoordinationCount(items.stream().filter(i -> "DESIGN_COORDINATION".equals(i.getItemType())
                && !"CLOSED".equals(i.getItemStatus())).count());
        vo.setOpenIssueCount(items.stream().filter(i -> "TECH_ISSUE".equals(i.getItemType())
                && !"CLOSED".equals(i.getItemStatus())).count());
        vo.setOverdueCount(items.stream().filter(this::isChiefEngineerOverdueItem).count());

        vo.setPendingReviews(items.stream()
                .filter(i -> "TECH_REVIEW".equals(i.getItemType()) && !"CLOSED".equals(i.getItemStatus()))
                .limit(5)
                .map(i -> toTechBusinessItem("TECH_REVIEW", i, projectNameMap, ownerNameMap))
                .collect(Collectors.toList()));
        vo.setPendingCoordinations(items.stream()
                .filter(i -> "DESIGN_COORDINATION".equals(i.getItemType()) && !"CLOSED".equals(i.getItemStatus()))
                .limit(5)
                .map(i -> toTechBusinessItem("DESIGN_COORDINATION", i, projectNameMap, ownerNameMap))
                .collect(Collectors.toList()));
        vo.setOpenIssues(items.stream()
                .filter(i -> "TECH_ISSUE".equals(i.getItemType()) && !"CLOSED".equals(i.getItemStatus()))
                .limit(5)
                .map(i -> toTechBusinessItem("TECH_ISSUE", i, projectNameMap, ownerNameMap))
                .collect(Collectors.toList()));
        vo.setOverdueItems(items.stream()
                .filter(this::isChiefEngineerOverdueItem)
                .limit(5)
                .map(i -> toTechBusinessItem(i.getItemType(), i, projectNameMap, ownerNameMap))
                .collect(Collectors.toList()));
        return vo;
    }

    // ========================================================================
    // 6. Finance Dashboard
}
