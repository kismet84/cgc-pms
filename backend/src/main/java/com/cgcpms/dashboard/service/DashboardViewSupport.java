package com.cgcpms.dashboard.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.dashboard.vo.DashboardBusinessItemVO;
import com.cgcpms.dashboard.vo.DashboardContractItemVO;
import com.cgcpms.dashboard.vo.DashboardProjectSummaryVO;
import com.cgcpms.dashboard.vo.DashboardTaskItemVO;
import com.cgcpms.dashboard.vo.ProductionManagerDashboardVO;
import com.cgcpms.dashboard.vo.PurchaseManagerDashboardVO;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.tech.entity.TechItem;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class DashboardViewSupport {

    private DashboardViewSupport() {
    }

    static YearMonth parseDashboardMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException("INVALID_DASHBOARD_MONTH", "报告月份格式无效，应为 yyyy-MM", e);
        }
    }

    static void applyEmptyPurchaseManager(PurchaseManagerDashboardVO vo) {
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

    static void applyEmptyProductionManager(ProductionManagerDashboardVO vo) {
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

    static Map<Long, String> projectNameMap(List<PmProject> projects) {
        return projects.stream().collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
    }

    static DashboardTaskItemVO toTaskItem(WfTask task, WfInstance instance, Map<Long, String> projectNameMap) {
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

    static boolean isProjectManagerWorkflowTask(WfTask task, WfInstance instance) {
        return !isPaymentWorkflowType(task.getBusinessType())
                && (instance == null || !isPaymentWorkflowType(instance.getBusinessType()));
    }

    static boolean isWorkflowInstanceInProject(WfInstance instance, Long projectId) {
        return instance != null && Objects.equals(instance.getProjectId(), projectId);
    }

    private static boolean isPaymentWorkflowType(String businessType) {
        return WorkflowBusinessTypes.PAY_REQUEST.equals(businessType)
                || "PAY_APPLICATION".equals(businessType);
    }

    static boolean isChiefEngineerOverdueItem(TechItem item) {
        return !"CLOSED".equals(item.getItemStatus()) && overdueDays(item.getDueDate()) > 0;
    }

    static boolean isTechItemInMonth(TechItem item, YearMonth month) {
        LocalDateTime effectiveDate = item.getDueDate() != null ? item.getDueDate() : item.getDiscoveredAt();
        if (effectiveDate == null) {
            return false;
        }
        LocalDate date = effectiveDate.toLocalDate();
        return !date.isBefore(month.atDay(1)) && !date.isAfter(month.atEndOfMonth());
    }

    static long overdueDays(LocalDate deliveryDate) {
        return deliveryDate == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(deliveryDate, LocalDate.now()));
    }

    static long pendingDays(LocalDate receiptDate) {
        return receiptDate == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(receiptDate, LocalDate.now()));
    }

    static long pendingDays(LocalDateTime receivedAt) {
        return receivedAt == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(receivedAt, LocalDateTime.now()));
    }

    static long overdueDays(LocalDateTime dueDate) {
        return dueDate == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(dueDate.toLocalDate(), LocalDate.now()));
    }

    static BigDecimal amountOrZero(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    static DashboardBusinessItemVO toBusinessItem(String sourceType, MatPurchaseRequest request,
                                                   Map<Long, String> projectNames,
                                                   Map<Long, String> ownerNames,
                                                   Map<Long, String> summaries) {
        DashboardBusinessItemVO item = new DashboardBusinessItemVO();
        String summary = summaries.get(request.getId());
        item.setSourceType(sourceType);
        item.setSourceId(String.valueOf(request.getId()));
        item.setCode(request.getRequestCode());
        item.setTitle(StringUtils.hasText(summary) ? summary : request.getRequestCode());
        item.setItemSummary(summary);
        item.setStatus(request.getApprovalStatus());
        item.setAmount(null);
        item.setDate(request.getCreatedTime() != null ? DateTimeUtils.DTF.format(request.getCreatedTime()) : null);
        item.setProjectId(request.getProjectId() != null ? String.valueOf(request.getProjectId()) : null);
        item.setProjectName(projectNames.get(request.getProjectId()));
        item.setOwnerName(ownerNames.get(request.getCreatedBy()));
        return item;
    }

    static DashboardBusinessItemVO toBusinessItem(String sourceType, MatPurchaseOrder order,
                                                   Map<Long, String> projectNames,
                                                   Map<Long, String> partnerNames,
                                                   Map<Long, String> summaries) {
        DashboardBusinessItemVO item = new DashboardBusinessItemVO();
        String summary = summaries.get(order.getId());
        item.setSourceType(sourceType);
        item.setSourceId(String.valueOf(order.getId()));
        item.setCode(order.getOrderCode());
        item.setTitle(StringUtils.hasText(summary) ? summary : order.getOrderCode());
        item.setItemSummary(summary);
        item.setStatus(order.getOrderStatus());
        item.setAmount(order.getTotalAmount() != null ? order.getTotalAmount().toPlainString() : "0");
        item.setDate(order.getDeliveryDate() != null ? order.getDeliveryDate().toString() : null);
        item.setProjectId(order.getProjectId() != null ? String.valueOf(order.getProjectId()) : null);
        item.setProjectName(projectNames.get(order.getProjectId()));
        item.setPartnerName(partnerNames.get(order.getPartnerId()));
        item.setOverdueDays(overdueDays(order.getDeliveryDate()));
        return item;
    }

    static DashboardBusinessItemVO toBusinessItem(String sourceType, MatReceipt receipt,
                                                   Map<Long, String> projectNames,
                                                   Map<Long, String> partnerNames,
                                                   Map<Long, String> summaries) {
        return toBusinessItem(sourceType, receipt, projectNames, partnerNames, Collections.emptyMap(), summaries);
    }

    static DashboardBusinessItemVO toBusinessItem(String sourceType, MatReceipt receipt,
                                                   Map<Long, String> projectNames,
                                                   Map<Long, String> partnerNames,
                                                   Map<Long, String> ownerNames,
                                                   Map<Long, String> summaries) {
        DashboardBusinessItemVO item = new DashboardBusinessItemVO();
        String summary = summaries.get(receipt.getId());
        item.setSourceType(sourceType);
        item.setSourceId(String.valueOf(receipt.getId()));
        item.setCode(receipt.getReceiptCode());
        item.setTitle(summary);
        item.setItemSummary(summary);
        item.setStatus(receipt.getApprovalStatus());
        item.setAmount(receipt.getTotalAmount() != null ? receipt.getTotalAmount().toPlainString() : "0");
        item.setDate(receipt.getReceiptDate() != null ? receipt.getReceiptDate().toString() : null);
        item.setProjectId(receipt.getProjectId() != null ? String.valueOf(receipt.getProjectId()) : null);
        item.setProjectName(projectNames.get(receipt.getProjectId()));
        item.setPartnerName(partnerNames.get(receipt.getPartnerId()));
        item.setOwnerName(ownerNames.get(receipt.getReceiverId()));
        item.setPendingDays(pendingDays(receipt.getReceiptDate()));
        return item;
    }

    static DashboardBusinessItemVO toBusinessItem(String sourceType, MatRequisition requisition,
                                                   Map<Long, String> projectNames,
                                                   Map<Long, String> partnerNames,
                                                   Map<Long, String> ownerNames) {
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
        item.setProjectName(projectNames.get(requisition.getProjectId()));
        item.setPartnerName(partnerNames.get(requisition.getPartnerId()));
        item.setOwnerName(ownerNames.get(requisition.getRequisitionerId()));
        return item;
    }

    static DashboardBusinessItemVO toBusinessItem(String sourceType, SubMeasure measure,
                                                   Map<Long, String> projectNames,
                                                   Map<Long, String> partnerNames) {
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
        item.setProjectName(projectNames.get(measure.getProjectId()));
        item.setPartnerName(partnerNames.get(measure.getPartnerId()));
        return item;
    }

    static DashboardBusinessItemVO toTechBusinessItem(String sourceType, TechItem item,
                                                       Map<Long, String> projectNames,
                                                       Map<Long, String> ownerNames) {
        DashboardBusinessItemVO vo = new DashboardBusinessItemVO();
        vo.setSourceType(sourceType);
        vo.setSourceId(String.valueOf(item.getId()));
        vo.setCode(item.getItemCode());
        vo.setTitle(item.getItemTitle());
        vo.setStatus(item.getItemStatus());
        vo.setAmount(item.getItemLevel());
        vo.setDate(item.getDueDate() != null ? DateTimeUtils.DTF.format(item.getDueDate()) : null);
        vo.setProjectId(item.getProjectId() != null ? String.valueOf(item.getProjectId()) : null);
        vo.setProjectName(projectNames.get(item.getProjectId()));
        vo.setOwnerName(ownerNames.get(item.getResponsibleUserId()));
        long overdue = overdueDays(item.getDueDate());
        if (overdue > 0) {
            vo.setOverdueDays(overdue);
        }
        return vo;
    }

    static DashboardProjectSummaryVO toProjectSummary(PmProject project) {
        DashboardProjectSummaryVO vo = new DashboardProjectSummaryVO();
        vo.setProjectId(String.valueOf(project.getId()));
        vo.setProjectName(project.getProjectName());
        vo.setProjectCode(project.getProjectCode());
        vo.setStatus(project.getStatus());
        vo.setTargetCost(project.getTargetCost() != null ? project.getTargetCost().toPlainString() : "0");
        return vo;
    }

    static DashboardContractItemVO toContractItem(CtContract contract) {
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
}
