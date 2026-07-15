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
abstract class DashboardSharedSupport {
    protected final CostSummaryService costSummaryService;
    protected final CostSummaryMapper costSummaryMapper;
    protected final CostSubjectMapper costSubjectMapper;
    protected final CostItemMapper costItemMapper;
    protected final PmProjectMapper projectMapper;
    protected final CtContractMapper ctContractMapper;
    protected final WfTaskMapper wfTaskMapper;
    protected final WfInstanceMapper wfInstanceMapper;
    protected final PayRecordMapper payRecordMapper;
    protected final StlSettlementMapper stlSettlementMapper;
    protected final VarOrderMapper varOrderMapper;
    protected final SubMeasureMapper subMeasureMapper;
    protected final AlertLogMapper alertLogMapper;
    protected final MatPurchaseRequestMapper purchaseRequestMapper;
    protected final MatPurchaseRequestItemMapper purchaseRequestItemMapper;
    protected final MatPurchaseOrderMapper purchaseOrderMapper;
    protected final MatPurchaseOrderItemMapper purchaseOrderItemMapper;
    protected final MatReceiptMapper receiptMapper;
    protected final MatReceiptItemMapper receiptItemMapper;
    protected final MatRequisitionMapper requisitionMapper;
    protected final MatWarehouseMapper warehouseMapper;
    protected final MatStockMapper stockMapper;
    protected final TechItemMapper techItemMapper;
    protected final MdPartnerMapper partnerMapper;
    protected final MdMaterialMapper materialMapper;
    protected final SysUserMapper userMapper;

    protected DashboardSharedSupport(
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
            SysUserMapper userMapper) {
        this.costSummaryService = costSummaryService;
        this.costSummaryMapper = costSummaryMapper;
        this.costSubjectMapper = costSubjectMapper;
        this.costItemMapper = costItemMapper;
        this.projectMapper = projectMapper;
        this.ctContractMapper = ctContractMapper;
        this.wfTaskMapper = wfTaskMapper;
        this.wfInstanceMapper = wfInstanceMapper;
        this.payRecordMapper = payRecordMapper;
        this.stlSettlementMapper = stlSettlementMapper;
        this.varOrderMapper = varOrderMapper;
        this.subMeasureMapper = subMeasureMapper;
        this.alertLogMapper = alertLogMapper;
        this.purchaseRequestMapper = purchaseRequestMapper;
        this.purchaseRequestItemMapper = purchaseRequestItemMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderItemMapper = purchaseOrderItemMapper;
        this.receiptMapper = receiptMapper;
        this.receiptItemMapper = receiptItemMapper;
        this.requisitionMapper = requisitionMapper;
        this.warehouseMapper = warehouseMapper;
        this.stockMapper = stockMapper;
        this.techItemMapper = techItemMapper;
        this.partnerMapper = partnerMapper;
        this.materialMapper = materialMapper;
        this.userMapper = userMapper;
    }

    protected YearMonth parseDashboardMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            log.warn("Invalid dashboard month format '{}', ignoring. Expected yyyy-MM.", month);
            return null;
        }
    }

    protected List<CostSummary> latestSubjectSummaries(List<CostSummary> summaries) {
        Map<String, CostSummary> latest = new HashMap<>();
        for (CostSummary summary : summaries) {
            String key = summary.getProjectId() + ":" + summary.getCostSubjectId();
            CostSummary current = latest.get(key);
            if (current == null
                    || (summary.getSummaryDate() != null
                    && (current.getSummaryDate() == null || summary.getSummaryDate().isAfter(current.getSummaryDate())))) {
                latest.put(key, summary);
            }
        }
        return new ArrayList<>(latest.values());
    }

    protected <T> void applyMonthDateRange(
            LambdaQueryWrapper<T> query,
            YearMonth month,
            SFunction<T, LocalDate> column
    ) {
        if (month == null) {
            return;
        }
        query.ge(column, month.atDay(1))
                .le(column, month.atEndOfMonth());
    }

    protected <T> void applyMonthDateTimeRange(
            LambdaQueryWrapper<T> query,
            YearMonth month,
            SFunction<T, LocalDateTime> column
    ) {
        if (month == null) {
            return;
        }
        query.ge(column, month.atDay(1).atStartOfDay())
                .lt(column, month.plusMonths(1).atDay(1).atStartOfDay());
    }

    protected void applyEmptyPurchaseManager(PurchaseManagerDashboardVO vo) {
        vo.setPendingRequestCount(0L);
        vo.setActiveOrderCount(0L);
        vo.setOverdueDeliveryCount(0L);
        vo.setPendingReceiptCount(0L);
        vo.setLowStockItemCount(0L);
        vo.setTotalOrderAmount("0");
        vo.setRecentRequests(Collections.emptyList());
        vo.setPurchaseOrders(Collections.emptyList());
        vo.setOverdueOrders(Collections.emptyList());
        vo.setPendingReceipts(Collections.emptyList());
        vo.setSupplierScores(Collections.emptyList());
    }

    protected void applyEmptyProductionManager(ProductionManagerDashboardVO vo) {
        vo.setReceiptCount(0L);
        vo.setRequisitionCount(0L);
        vo.setPendingStockOutCount(0L);
        vo.setSubMeasureCount(0L);
        vo.setLowStockItemCount(0L);
        vo.setConfirmedMeasureAmount("0");
        vo.setRecentReceipts(Collections.emptyList());
        vo.setRecentRequisitions(Collections.emptyList());
        vo.setRecentSubMeasures(Collections.emptyList());
    }

    protected List<PmProject> resolveDashboardProjects(Long tenantId, Long projectId) {
        if (projectId != null) {
            return List.of(requireProject(tenantId, projectId));
        }
        return projectMapper.selectList(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getTenantId, tenantId)
                .eq(PmProject::getStatus, "ACTIVE"));
    }

    protected Map<Long, String> projectNameMap(List<PmProject> projects) {
        return projects.stream().collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
    }

    protected DashboardTaskItemVO toTaskItem(WfTask task, WfInstance instance, Map<Long, String> projectNameMap) {
        DashboardTaskItemVO item = new DashboardTaskItemVO();
        item.setTaskId(String.valueOf(task.getId()));
        item.setInstanceId(String.valueOf(task.getInstanceId()));
        item.setBusinessType(task.getBusinessType());
        item.setBusinessId(task.getBusinessId() != null ? String.valueOf(task.getBusinessId()) : null);
        item.setTaskStatus(task.getTaskStatus());
        item.setOwnerName(task.getApproverName());
        if (task.getReceivedAt() != null) {
            item.setReceivedAt(DateTimeUtils.DTF.format(task.getReceivedAt()));
            item.setPendingDays(pendingDays(task.getReceivedAt()));
        }
        if (instance != null) {
            item.setTitle(instance.getTitle());
            item.setItemSummary(instance.getBusinessSummary());
            item.setAmount(instance.getAmount() != null ? instance.getAmount().toPlainString() : null);
            item.setProjectId(instance.getProjectId() != null ? String.valueOf(instance.getProjectId()) : null);
            item.setProjectName(projectNameMap.get(instance.getProjectId()));
        }
        return item;
    }

    protected boolean isProjectManagerWorkflowTask(WfTask task, WfInstance instance) {
        return !isPaymentWorkflowType(task.getBusinessType())
                && (instance == null || !isPaymentWorkflowType(instance.getBusinessType()));
    }

    protected boolean isWorkflowInstanceInProject(WfInstance instance, Long projectId) {
        return instance != null && Objects.equals(instance.getProjectId(), projectId);
    }

    protected boolean isPaymentWorkflowType(String businessType) {
        return WorkflowBusinessTypes.PAY_REQUEST.equals(businessType)
                || "PAY_APPLICATION".equals(businessType);
    }

    protected boolean isChiefEngineerOverdueItem(TechItem item) {
        if ("CLOSED".equals(item.getItemStatus())) return false;
        return overdueDays(item.getDueDate()) > 0;
    }

    protected boolean isTechItemInMonth(TechItem item, YearMonth month) {
        LocalDateTime effectiveDate = item.getDueDate() != null ? item.getDueDate() : item.getDiscoveredAt();
        if (effectiveDate == null) return false;
        LocalDate date = effectiveDate.toLocalDate();
        return !date.isBefore(month.atDay(1)) && !date.isAfter(month.atEndOfMonth());
    }

    protected Map<Long, String> partnerNameMap(Long tenantId, List<MatPurchaseOrder> orders, List<MatReceipt> receipts) {
        Set<Long> partnerIds = new HashSet<>();
        orders.stream().map(MatPurchaseOrder::getPartnerId).filter(Objects::nonNull).forEach(partnerIds::add);
        receipts.stream().map(MatReceipt::getPartnerId).filter(Objects::nonNull).forEach(partnerIds::add);
        return partnerNameMap(tenantId, partnerIds);
    }

    protected Map<Long, String> partnerNameMap(Long tenantId, Set<Long> partnerIds) {
        partnerIds.remove(null);
        if (partnerIds.isEmpty()) return Collections.emptyMap();
        return partnerMapper.selectList(new LambdaQueryWrapper<MdPartner>()
                        .eq(MdPartner::getTenantId, tenantId)
                        .in(MdPartner::getId, partnerIds))
                .stream()
                .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));
    }

    protected Map<Long, String> ownerNameMap(Long tenantId, List<MatPurchaseRequest> requests) {
        Set<Long> userIds = requests.stream()
                .map(MatPurchaseRequest::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return userNameMap(tenantId, userIds);
    }

    protected Map<Long, String> userNameMap(Long tenantId, Set<Long> userIds) {
        userIds.remove(null);
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .in(SysUser::getId, userIds))
                .stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername(),
                        (a, b) -> a));
    }

    protected Set<Long> businessPartnerIds(List<MatReceipt> receipts, List<MatRequisition> requisitions, List<SubMeasure> measures) {
        Set<Long> partnerIds = new HashSet<>();
        receipts.stream().map(MatReceipt::getPartnerId).filter(Objects::nonNull).forEach(partnerIds::add);
        requisitions.stream().map(MatRequisition::getPartnerId).filter(Objects::nonNull).forEach(partnerIds::add);
        measures.stream().map(SubMeasure::getPartnerId).filter(Objects::nonNull).forEach(partnerIds::add);
        return partnerIds;
    }

    protected Set<Long> businessUserIds(List<MatReceipt> receipts, List<MatRequisition> requisitions) {
        Set<Long> userIds = new HashSet<>();
        receipts.stream().map(MatReceipt::getReceiverId).filter(Objects::nonNull).forEach(userIds::add);
        requisitions.stream().map(MatRequisition::getRequisitionerId).filter(Objects::nonNull).forEach(userIds::add);
        return userIds;
    }

    protected Map<Long, String> requestSummaryMap(Long tenantId, List<MatPurchaseRequest> requests) {
        List<Long> requestIds = requests.stream().map(MatPurchaseRequest::getId).filter(Objects::nonNull).toList();
        if (requestIds.isEmpty()) return Collections.emptyMap();
        List<MatPurchaseRequestItem> items = purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseRequestItem>()
                .eq(MatPurchaseRequestItem::getTenantId, tenantId)
                .in(MatPurchaseRequestItem::getRequestId, requestIds));
        Map<Long, String> materialNames = materialNameMap(items.stream().map(MatPurchaseRequestItem::getMaterialId).collect(Collectors.toSet()));
        return items.stream().collect(Collectors.groupingBy(MatPurchaseRequestItem::getRequestId,
                Collectors.collectingAndThen(Collectors.toList(), list -> summarizeNames(list.stream()
                        .map(i -> materialNames.get(i.getMaterialId()))
                        .filter(StringUtils::hasText)
                        .toList()))));
    }

    protected Map<Long, String> orderSummaryMap(Long tenantId, List<MatPurchaseOrder> orders) {
        List<Long> orderIds = orders.stream().map(MatPurchaseOrder::getId).filter(Objects::nonNull).toList();
        if (orderIds.isEmpty()) return Collections.emptyMap();
        List<MatPurchaseOrderItem> items = purchaseOrderItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseOrderItem>()
                .eq(MatPurchaseOrderItem::getTenantId, tenantId)
                .in(MatPurchaseOrderItem::getOrderId, orderIds));
        Map<Long, String> materialNames = materialNameMap(items.stream().map(MatPurchaseOrderItem::getMaterialId).collect(Collectors.toSet()));
        return items.stream().collect(Collectors.groupingBy(MatPurchaseOrderItem::getOrderId,
                Collectors.collectingAndThen(Collectors.toList(), list -> summarizeNames(list.stream()
                        .map(i -> StringUtils.hasText(i.getMaterialName()) ? i.getMaterialName() : materialNames.get(i.getMaterialId()))
                        .filter(StringUtils::hasText)
                        .toList()))));
    }

    protected Map<Long, String> receiptSummaryMap(Long tenantId, List<MatReceipt> receipts, Map<Long, String> orderSummaryMap) {
        List<Long> receiptIds = receipts.stream().map(MatReceipt::getId).filter(Objects::nonNull).toList();
        if (receiptIds.isEmpty()) return Collections.emptyMap();
        Map<Long, String> summaries = receipts.stream()
                .filter(r -> StringUtils.hasText(orderSummaryMap.get(r.getOrderId())))
                .collect(Collectors.toMap(MatReceipt::getId, r -> orderSummaryMap.get(r.getOrderId()), (a, b) -> a));
        List<MatReceiptItem> items = receiptItemMapper.selectList(new LambdaQueryWrapper<MatReceiptItem>()
                .eq(MatReceiptItem::getTenantId, tenantId)
                .in(MatReceiptItem::getReceiptId, receiptIds));
        Map<Long, String> materialNames = materialNameMap(items.stream().map(MatReceiptItem::getMaterialId).collect(Collectors.toSet()));
        summaries.putAll(items.stream().collect(Collectors.groupingBy(MatReceiptItem::getReceiptId,
                Collectors.collectingAndThen(Collectors.toList(), list -> summarizeNames(list.stream()
                        .map(i -> materialNames.get(i.getMaterialId()))
                        .filter(StringUtils::hasText)
                        .toList())))));
        return summaries;
    }

    protected Map<Long, String> materialNameMap(Set<Long> materialIds) {
        materialIds.remove(null);
        if (materialIds.isEmpty()) return Collections.emptyMap();
        return materialMapper.selectList(new LambdaQueryWrapper<MdMaterial>()
                        .in(MdMaterial::getId, materialIds))
                .stream()
                .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));
    }

    protected String summarizeNames(List<String> names) {
        List<String> distinct = names.stream().filter(StringUtils::hasText).distinct().limit(3).toList();
        if (distinct.isEmpty()) return null;
        String summary = String.join("、", distinct);
        long extra = names.stream().filter(StringUtils::hasText).distinct().count() - distinct.size();
        return extra > 0 ? summary + " 等" + (distinct.size() + extra) + "项" : summary;
    }

    protected long overdueDays(LocalDate deliveryDate) {
        return deliveryDate == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(deliveryDate, LocalDate.now()));
    }

    protected long pendingDays(LocalDate receiptDate) {
        return receiptDate == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(receiptDate, LocalDate.now()));
    }

    protected long pendingDays(LocalDateTime receivedAt) {
        return receivedAt == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(receivedAt, LocalDateTime.now()));
    }

    protected long overdueDays(LocalDateTime dueDate) {
        return dueDate == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(dueDate.toLocalDate(), LocalDate.now()));
    }

    protected BigDecimal amountOrZero(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    protected Long countLowStockItems(Long tenantId, List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return 0L;
        }
        List<MatWarehouse> warehouses = warehouseMapper.selectList(new LambdaQueryWrapper<MatWarehouse>()
                .eq(MatWarehouse::getTenantId, tenantId)
                .in(MatWarehouse::getProjectId, projectIds));
        List<Long> warehouseIds = warehouses.stream().map(MatWarehouse::getId).collect(Collectors.toList());
        if (warehouseIds.isEmpty()) {
            return 0L;
        }
        return stockMapper.selectCount(new LambdaQueryWrapper<MatStock>()
                .eq(MatStock::getTenantId, tenantId)
                .in(MatStock::getWarehouseId, warehouseIds)
                .le(MatStock::getAvailableQty, BigDecimal.ZERO));
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatPurchaseRequest request, Map<Long, String> projectNameMap) {
        return toBusinessItem(sourceType, request, projectNameMap, Collections.emptyMap());
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatPurchaseRequest request,
                                                   Map<Long, String> projectNameMap,
                                                   Map<Long, String> ownerNameMap) {
        return toBusinessItem(sourceType, request, projectNameMap, ownerNameMap, Collections.emptyMap());
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatPurchaseRequest request,
                                                   Map<Long, String> projectNameMap,
                                                   Map<Long, String> ownerNameMap,
                                                   Map<Long, String> summaryMap) {
        DashboardBusinessItemVO item = new DashboardBusinessItemVO();
        String summary = summaryMap.get(request.getId());
        item.setSourceType(sourceType);
        item.setSourceId(String.valueOf(request.getId()));
        item.setCode(request.getRequestCode());
        item.setTitle(StringUtils.hasText(summary) ? summary : request.getRequestCode());
        item.setItemSummary(summary);
        item.setStatus(request.getApprovalStatus());
        item.setAmount(null);
        item.setDate(request.getCreatedTime() != null ? DateTimeUtils.DTF.format(request.getCreatedTime()) : null);
        item.setProjectId(request.getProjectId() != null ? String.valueOf(request.getProjectId()) : null);
        item.setProjectName(projectNameMap.get(request.getProjectId()));
        item.setOwnerName(ownerNameMap.get(request.getCreatedBy()));
        return item;
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatPurchaseOrder order, Map<Long, String> projectNameMap) {
        return toBusinessItem(sourceType, order, projectNameMap, Collections.emptyMap());
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatPurchaseOrder order,
                                                   Map<Long, String> projectNameMap,
                                                   Map<Long, String> partnerNameMap) {
        return toBusinessItem(sourceType, order, projectNameMap, partnerNameMap, Collections.emptyMap());
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatPurchaseOrder order,
                                                   Map<Long, String> projectNameMap,
                                                   Map<Long, String> partnerNameMap,
                                                   Map<Long, String> summaryMap) {
        DashboardBusinessItemVO item = new DashboardBusinessItemVO();
        String summary = summaryMap.get(order.getId());
        item.setSourceType(sourceType);
        item.setSourceId(String.valueOf(order.getId()));
        item.setCode(order.getOrderCode());
        item.setTitle(StringUtils.hasText(summary) ? summary : order.getOrderCode());
        item.setItemSummary(summary);
        item.setStatus(order.getOrderStatus());
        item.setAmount(order.getTotalAmount() != null ? order.getTotalAmount().toPlainString() : "0");
        item.setDate(order.getDeliveryDate() != null ? order.getDeliveryDate().toString() : null);
        item.setProjectId(order.getProjectId() != null ? String.valueOf(order.getProjectId()) : null);
        item.setProjectName(projectNameMap.get(order.getProjectId()));
        item.setPartnerName(partnerNameMap.get(order.getPartnerId()));
        item.setOverdueDays(overdueDays(order.getDeliveryDate()));
        return item;
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatReceipt receipt, Map<Long, String> projectNameMap) {
        return toBusinessItem(sourceType, receipt, projectNameMap, Collections.emptyMap());
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatReceipt receipt,
                                                   Map<Long, String> projectNameMap,
                                                   Map<Long, String> partnerNameMap) {
        return toBusinessItem(sourceType, receipt, projectNameMap, partnerNameMap, Collections.emptyMap());
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatReceipt receipt,
                                                   Map<Long, String> projectNameMap,
                                                   Map<Long, String> partnerNameMap,
                                                   Map<Long, String> summaryMap) {
        return toBusinessItem(sourceType, receipt, projectNameMap, partnerNameMap, Collections.emptyMap(), summaryMap);
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatReceipt receipt,
                                                   Map<Long, String> projectNameMap,
                                                   Map<Long, String> partnerNameMap,
                                                   Map<Long, String> ownerNameMap,
                                                   Map<Long, String> summaryMap) {
        DashboardBusinessItemVO item = new DashboardBusinessItemVO();
        String summary = summaryMap.get(receipt.getId());
        item.setSourceType(sourceType);
        item.setSourceId(String.valueOf(receipt.getId()));
        item.setCode(receipt.getReceiptCode());
        item.setTitle(summary);
        item.setItemSummary(summary);
        item.setStatus(receipt.getApprovalStatus());
        item.setAmount(receipt.getTotalAmount() != null ? receipt.getTotalAmount().toPlainString() : "0");
        item.setDate(receipt.getReceiptDate() != null ? receipt.getReceiptDate().toString() : null);
        item.setProjectId(receipt.getProjectId() != null ? String.valueOf(receipt.getProjectId()) : null);
        item.setProjectName(projectNameMap.get(receipt.getProjectId()));
        item.setPartnerName(partnerNameMap.get(receipt.getPartnerId()));
        item.setOwnerName(ownerNameMap.get(receipt.getReceiverId()));
        item.setPendingDays(pendingDays(receipt.getReceiptDate()));
        return item;
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatRequisition requisition, Map<Long, String> projectNameMap) {
        return toBusinessItem(sourceType, requisition, projectNameMap, Collections.emptyMap(), Collections.emptyMap());
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, MatRequisition requisition,
                                                   Map<Long, String> projectNameMap,
                                                   Map<Long, String> partnerNameMap,
                                                   Map<Long, String> ownerNameMap) {
        DashboardBusinessItemVO item = new DashboardBusinessItemVO();
        item.setSourceType(sourceType);
        item.setSourceId(String.valueOf(requisition.getId()));
        item.setCode(requisition.getRequisitionCode());
        item.setTitle(null);
        item.setItemSummary(null);
        item.setStatus(requisition.getApprovalStatus());
        item.setAmount(requisition.getTotalAmount() != null ? requisition.getTotalAmount().toPlainString() : "0");
        item.setDate(requisition.getRequisitionDate() != null ? requisition.getRequisitionDate().toString() : null);
        item.setProjectId(requisition.getProjectId() != null ? String.valueOf(requisition.getProjectId()) : null);
        item.setProjectName(projectNameMap.get(requisition.getProjectId()));
        item.setPartnerName(partnerNameMap.get(requisition.getPartnerId()));
        item.setOwnerName(ownerNameMap.get(requisition.getRequisitionerId()));
        return item;
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, SubMeasure measure, Map<Long, String> projectNameMap) {
        return toBusinessItem(sourceType, measure, projectNameMap, Collections.emptyMap());
    }

    protected DashboardBusinessItemVO toBusinessItem(String sourceType, SubMeasure measure,
                                                   Map<Long, String> projectNameMap,
                                                   Map<Long, String> partnerNameMap) {
        DashboardBusinessItemVO item = new DashboardBusinessItemVO();
        item.setSourceType(sourceType);
        item.setSourceId(String.valueOf(measure.getId()));
        item.setCode(measure.getMeasureCode());
        item.setTitle(null);
        item.setItemSummary(null);
        item.setStatus(measure.getStatus() != null ? measure.getStatus() : measure.getApprovalStatus());
        item.setAmount(measure.getApprovedAmount() != null ? measure.getApprovedAmount().toPlainString() : "0");
        item.setDate(measure.getMeasureDate() != null ? measure.getMeasureDate().toString() : null);
        item.setProjectId(measure.getProjectId() != null ? String.valueOf(measure.getProjectId()) : null);
        item.setProjectName(projectNameMap.get(measure.getProjectId()));
        item.setPartnerName(partnerNameMap.get(measure.getPartnerId()));
        return item;
    }

    protected DashboardBusinessItemVO toTechBusinessItem(String sourceType, TechItem item, Map<Long, String> projectNameMap) {
        return toTechBusinessItem(sourceType, item, projectNameMap, Collections.emptyMap());
    }

    protected DashboardBusinessItemVO toTechBusinessItem(String sourceType, TechItem item,
                                                       Map<Long, String> projectNameMap,
                                                       Map<Long, String> ownerNameMap) {
        DashboardBusinessItemVO vo = new DashboardBusinessItemVO();
        vo.setSourceType(sourceType);
        vo.setSourceId(String.valueOf(item.getId()));
        vo.setCode(item.getItemCode());
        vo.setTitle(item.getItemTitle());
        vo.setStatus(item.getItemStatus());
        vo.setAmount(item.getItemLevel());
        vo.setDate(item.getDueDate() != null ? DateTimeUtils.DTF.format(item.getDueDate()) : null);
        vo.setProjectId(item.getProjectId() != null ? String.valueOf(item.getProjectId()) : null);
        vo.setProjectName(projectNameMap.get(item.getProjectId()));
        vo.setOwnerName(ownerNameMap.get(item.getResponsibleUserId()));
        long overdueDays = overdueDays(item.getDueDate());
        if (overdueDays > 0) {
            vo.setOverdueDays(overdueDays);
        }
        return vo;
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    protected PmProject requireProject(Long tenantId, Long projectId) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "请指定项目");
        }
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        return project;
    }

    protected Map<Long, WfInstance> batchLoadInstances(List<WfTask> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return Collections.emptyMap();
        }
        Set<Long> instanceIds = tasks.stream()
                .map(WfTask::getInstanceId)
                .collect(Collectors.toSet());
        List<WfInstance> instances = wfInstanceMapper.selectByIds(instanceIds);
        return instances.stream().collect(Collectors.toMap(WfInstance::getId, i -> i, (a, b) -> a));
    }

    protected DashboardProjectSummaryVO toProjectSummary(PmProject project) {
        DashboardProjectSummaryVO vo = new DashboardProjectSummaryVO();
        vo.setProjectId(String.valueOf(project.getId()));
        vo.setProjectName(project.getProjectName());
        vo.setProjectCode(project.getProjectCode());
        vo.setStatus(project.getStatus());
        vo.setTargetCost(project.getTargetCost() != null ? project.getTargetCost().toPlainString() : "0");
        return vo;
    }

    protected DashboardContractItemVO toContractItem(CtContract contract) {
        DashboardContractItemVO vo = new DashboardContractItemVO();
        vo.setContractId(String.valueOf(contract.getId()));
        vo.setContractCode(contract.getContractCode());
        vo.setContractName(contract.getContractName());
        vo.setContractType(contract.getContractType());
        vo.setContractAmount(contract.getContractAmount() != null ? contract.getContractAmount().toPlainString() : "0");
        vo.setCurrentAmount(contract.getCurrentAmount() != null ? contract.getCurrentAmount().toPlainString() : "0");
        vo.setPaidAmount(contract.getPaidAmount() != null ? contract.getPaidAmount().toPlainString() : "0");
        vo.setEndDate(contract.getEndDate() != null ? contract.getEndDate().toString() : null);
        vo.setProjectId(contract.getProjectId() != null ? String.valueOf(contract.getProjectId()) : null);
        vo.setContractStatus(contract.getContractStatus());
        return vo;
    }

    protected DashboardAlertItemVO toAlertItem(AlertLog alert) {
        DashboardAlertItemVO vo = new DashboardAlertItemVO();
        vo.setAlertType(alert.getRuleType());
        vo.setSeverity(alert.getSeverity());
        vo.setMessage(alert.getMessage());
        vo.setProjectId(alert.getProjectId() != null ? String.valueOf(alert.getProjectId()) : null);
        if (alert.getTriggeredAt() != null) {
            vo.setTriggeredAt(DateTimeUtils.DTF.format(alert.getTriggeredAt()));
        }
        return vo;
    }
}
