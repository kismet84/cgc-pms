package com.cgcpms.dashboard.service;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.dashboard.vo.*;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
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
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.tech.entity.TechItem;
import com.cgcpms.tech.mapper.TechItemMapper;
import com.cgcpms.tech.vo.ChiefEngineerDashboardVO;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DashboardService integration tests — business correctness for all 6 views.
 * <p>
 * Uses {@code @ActiveProfiles("local")} (H2 in-memory).
 * Each test method uses {@code @Transactional} so seed data is rolled back.
 * Seed codes use unique suffixes per test (MyBatis-Plus snowflake IDs guarantee uniqueness).
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("DashboardService — 6 views business correctness")
abstract class DashboardServiceTestSupport {

    protected static final long USER_ADMIN = 1L;
    protected static final long TENANT_ID = 0L;

    @Autowired protected DashboardService dashboardService;
    @Autowired protected PmProjectMapper projectMapper;
    @Autowired protected CtContractMapper ctContractMapper;
    @Autowired protected WfInstanceMapper wfInstanceMapper;
    @Autowired protected WfTaskMapper wfTaskMapper;
    @Autowired protected VarOrderMapper varOrderMapper;
    @Autowired protected SubMeasureMapper subMeasureMapper;
    @Autowired protected StlSettlementMapper stlSettlementMapper;
    @Autowired protected PayApplicationMapper payApplicationMapper;
    @Autowired protected PayRecordMapper payRecordMapper;
    @Autowired protected AlertLogMapper alertLogMapper;
    @Autowired protected CostSummaryMapper costSummaryMapper;
    @Autowired protected CostSubjectMapper costSubjectMapper;
    @Autowired protected CostItemMapper costItemMapper;
    @Autowired protected MatPurchaseRequestMapper purchaseRequestMapper;
    @Autowired protected MatPurchaseRequestItemMapper purchaseRequestItemMapper;
    @Autowired protected MatPurchaseOrderMapper purchaseOrderMapper;
    @Autowired protected MatPurchaseOrderItemMapper purchaseOrderItemMapper;
    @Autowired protected MatReceiptMapper receiptMapper;
    @Autowired protected MatReceiptItemMapper receiptItemMapper;
    @Autowired protected MatRequisitionMapper requisitionMapper;
    @Autowired protected MatWarehouseMapper warehouseMapper;
    @Autowired protected MatStockMapper stockMapper;
    @Autowired protected MdPartnerMapper partnerMapper;
    @Autowired protected MdMaterialMapper materialMapper;
    @Autowired protected SysUserMapper userMapper;
    @Autowired protected TechItemMapper techItemMapper;

    protected void setAdminContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .build());
    }

    /**
     * Helper that seeds a full test project and returns its ID.
     * Each call produces unique codes via a suffix so tests don't clash on UK constraints.
     */
    protected SeedResult seed(String suffix) {
        setAdminContext();
        LocalDate today = LocalDate.now();
        LocalDate currentMonthDate = today.withDayOfMonth(1).plusDays(1);
        LocalDate expiringContractDate = today.plusDays(
                Math.min(10, today.lengthOfMonth() - today.getDayOfMonth()));

        // Project
        PmProject project = new PmProject();
        project.setProjectCode("DSH-" + suffix);
        project.setProjectName("Dashboard Test " + suffix);
        project.setProjectType("房建工程");
        project.setStatus("ACTIVE");
        project.setContractAmount(new BigDecimal("10000000.00"));
        project.setTargetCost(new BigDecimal("8000000.00"));
        project.setPlannedEndDate(LocalDate.now().minusDays(10));
        projectMapper.insert(project);
        Long projectId = project.getId();

        // Contract
        CtContract contract = new CtContract();
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(projectId);
        contract.setContractCode("CT-" + suffix);
        contract.setContractName("Contract " + suffix);
        contract.setContractType("SUB");
        contract.setContractAmount(new BigDecimal("5000000.00"));
        contract.setCurrentAmount(new BigDecimal("5500000.00"));
        contract.setPaidAmount(new BigDecimal("2000000.00"));
        contract.setContractStatus("PERFORMING");
        contract.setEndDate(expiringContractDate);
        ctContractMapper.insert(contract);
        Long contractId = contract.getId();

        MdPartner partner = new MdPartner();
        partner.setTenantId(TENANT_ID);
        partner.setPartnerCode("PT-" + suffix);
        partner.setPartnerName("供应商-" + suffix);
        partner.setPartnerType("SUPPLIER");
        partner.setStatus("ENABLE");
        partnerMapper.insert(partner);
        Long partnerId = partner.getId();

        MdMaterial material = new MdMaterial();
        material.setTenantId(TENANT_ID);
        material.setMaterialCode("MAT-" + suffix);
        material.setMaterialName("钢筋-" + suffix);
        material.setUnit("吨");
        material.setStatus("ENABLE");
        materialMapper.insert(material);
        Long materialId = material.getId();

        SysUser signalUser = new SysUser();
        signalUser.setTenantId(TENANT_ID);
        signalUser.setUsername("dashboard-user-" + suffix);
        signalUser.setPassword("{noop}dashboard-test");
        signalUser.setRealName("驾驶舱用户-" + suffix);
        signalUser.setStatus("ENABLE");
        userMapper.insert(signalUser);
        Long signalUserId = signalUser.getId();

        // WfInstance + WfTask
        WfInstance instance = new WfInstance();
        instance.setTenantId(TENANT_ID);
        instance.setTemplateId(1L);
        instance.setBusinessType("CONTRACT");
        instance.setBusinessId(contractId);
        instance.setProjectId(projectId);
        instance.setTitle("审批-" + suffix);
        instance.setAmount(new BigDecimal("5000000.00"));
        instance.setBusinessSummary("合同审批摘要-" + suffix);
        instance.setInstanceStatus("RUNNING");
        instance.setInitiatorId(USER_ADMIN);
        wfInstanceMapper.insert(instance);

        WfTask task = new WfTask();
        task.setTenantId(TENANT_ID);
        task.setInstanceId(instance.getId());
        task.setNodeInstanceId(1L);
        task.setBusinessType("CONTRACT");
        task.setBusinessId(contractId);
        task.setApproverId(USER_ADMIN);
        task.setApproverName("成本经理");
        task.setTaskStatus(WorkflowConstants.TASK_PENDING);
        task.setReceivedAt(currentMonthDate.atTime(10, 0));
        wfTaskMapper.insert(task);

        // VarOrder
        VarOrder varOrder = new VarOrder();
        varOrder.setTenantId(TENANT_ID);
        varOrder.setProjectId(projectId);
        varOrder.setVarCode("VO-" + suffix);
        varOrder.setVarName("签证-" + suffix);
        varOrder.setApprovedAmount(new BigDecimal("100000.00"));
        varOrder.setApprovalStatus("APPROVED");
        varOrderMapper.insert(varOrder);

        // SubMeasure
        SubMeasure subMeasure = new SubMeasure();
        subMeasure.setTenantId(TENANT_ID);
        subMeasure.setProjectId(projectId);
        subMeasure.setPartnerId(partnerId);
        subMeasure.setMeasureCode("SM-" + suffix);
        subMeasure.setMeasurePeriod("2026-06");
        subMeasure.setReportedAmount(new BigDecimal("90000.00"));
        subMeasure.setApprovedAmount(new BigDecimal("80000.00"));
        subMeasure.setApprovalStatus("APPROVED");
        subMeasure.setStatus("CONFIRMED");
        subMeasureMapper.insert(subMeasure);

        // Purchase / receipt / requisition / inventory signals for role dashboards
        MatPurchaseRequest purchaseRequest = new MatPurchaseRequest();
        purchaseRequest.setTenantId(TENANT_ID);
        purchaseRequest.setProjectId(projectId);
        purchaseRequest.setContractId(contractId);
        purchaseRequest.setRequestCode("PR-" + suffix);
        purchaseRequest.setApprovalStatus("APPROVING");
        purchaseRequest.setStatus("DRAFT");
        purchaseRequest.setCreatedBy(999L);
        purchaseRequestMapper.insert(purchaseRequest);

        MatPurchaseRequestItem requestItem = new MatPurchaseRequestItem();
        requestItem.setTenantId(TENANT_ID);
        requestItem.setRequestId(purchaseRequest.getId());
        requestItem.setMaterialId(materialId);
        requestItem.setQuantity(new BigDecimal("10.0000"));
        requestItem.setUnit("吨");
        requestItem.setPlannedDate(currentMonthDate.plusDays(7));
        purchaseRequestItemMapper.insert(requestItem);

        MatPurchaseOrder purchaseOrder = new MatPurchaseOrder();
        purchaseOrder.setTenantId(TENANT_ID);
        purchaseOrder.setProjectId(projectId);
        purchaseOrder.setRequestId(purchaseRequest.getId());
        purchaseOrder.setContractId(contractId);
        purchaseOrder.setPartnerId(partnerId);
        purchaseOrder.setOrderCode("PO-" + suffix);
        purchaseOrder.setOrderDate(currentMonthDate);
        purchaseOrder.setDeliveryDate(currentMonthDate.minusDays(1));
        purchaseOrder.setTotalAmount(new BigDecimal("120000.00"));
        purchaseOrder.setApprovalStatus("APPROVED");
        purchaseOrder.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(purchaseOrder);

        MatPurchaseOrderItem purchaseOrderItem = new MatPurchaseOrderItem();
        purchaseOrderItem.setTenantId(TENANT_ID);
        purchaseOrderItem.setProjectId(projectId);
        purchaseOrderItem.setOrderId(purchaseOrder.getId());
        purchaseOrderItem.setMaterialId(materialId);
        purchaseOrderItem.setMaterialName("钢筋-" + suffix);
        purchaseOrderItem.setQuantity(new BigDecimal("10.0000"));
        purchaseOrderItem.setUnit("吨");
        purchaseOrderItem.setUnitPrice(new BigDecimal("12000.0000"));
        purchaseOrderItem.setAmount(new BigDecimal("120000.00"));
        purchaseOrderItemMapper.insert(purchaseOrderItem);

        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setTenantId(TENANT_ID);
        warehouse.setProjectId(projectId);
        warehouse.setWarehouseCode("WH-" + suffix);
        warehouse.setWarehouseName("Warehouse " + suffix);
        warehouse.setStatus("ENABLE");
        warehouseMapper.insert(warehouse);

        MatReceipt receipt = new MatReceipt();
        receipt.setTenantId(TENANT_ID);
        receipt.setProjectId(projectId);
        receipt.setOrderId(purchaseOrder.getId());
        receipt.setContractId(contractId);
        receipt.setPartnerId(partnerId);
        receipt.setReceiptCode("RC-" + suffix);
        receipt.setReceiptDate(currentMonthDate);
        receipt.setWarehouseId(warehouse.getId());
        receipt.setReceiverId(signalUserId);
        receipt.setQualityStatus("PENDING");
        receipt.setTotalAmount(new BigDecimal("80000.00"));
        receipt.setApprovalStatus("APPROVING");
        receiptMapper.insert(receipt);

        MatReceiptItem receiptItem = new MatReceiptItem();
        receiptItem.setTenantId(TENANT_ID);
        receiptItem.setReceiptId(receipt.getId());
        receiptItem.setOrderItemId(purchaseOrderItem.getId());
        receiptItem.setMaterialId(materialId);
        receiptItem.setActualQuantity(new BigDecimal("6.0000"));
        receiptItem.setQualifiedQuantity(new BigDecimal("6.0000"));
        receiptItem.setUnitPrice(new BigDecimal("12000.0000"));
        receiptItem.setAmount(new BigDecimal("72000.00"));
        receiptItemMapper.insert(receiptItem);

        MatRequisition requisition = new MatRequisition();
        requisition.setTenantId(TENANT_ID);
        requisition.setProjectId(projectId);
        requisition.setContractId(contractId);
        requisition.setRequisitionCode("RQ-" + suffix);
        requisition.setRequisitionDate(currentMonthDate);
        requisition.setWarehouseId(warehouse.getId());
        requisition.setRequisitionerId(signalUserId);
        requisition.setPartnerId(partnerId);
        requisition.setApprovalStatus("APPROVED");
        requisition.setTotalAmount(new BigDecimal("50000.00"));
        requisition.setStockOutFlag(0);
        requisitionMapper.insert(requisition);

        MatStock stock = new MatStock();
        stock.setTenantId(TENANT_ID);
        stock.setWarehouseId(warehouse.getId());
        stock.setMaterialId(1L);
        stock.setAvailableQty(BigDecimal.ZERO);
        stockMapper.insert(stock);

        // Settlement
        StlSettlement settlement = new StlSettlement();
        settlement.setTenantId(TENANT_ID);
        settlement.setProjectId(projectId);
        settlement.setSettlementCode("STL-" + suffix);
        settlement.setSettlementStatus("FINALIZED");
        stlSettlementMapper.insert(settlement);

        // Payment applications are the authoritative approval/payment state facts.
        PayApplication paidApplication = new PayApplication();
        paidApplication.setTenantId(TENANT_ID);
        paidApplication.setProjectId(projectId);
        paidApplication.setContractId(contractId);
        paidApplication.setPartnerId(partnerId);
        paidApplication.setApplyCode("PAY-PAID-" + suffix);
        paidApplication.setPayType("PROGRESS");
        paidApplication.setApplyAmount(new BigDecimal("100000.00"));
        paidApplication.setApprovedAmount(new BigDecimal("100000.00"));
        paidApplication.setActualPayAmount(new BigDecimal("100000.00"));
        paidApplication.setApprovalStatus("APPROVED");
        paidApplication.setPayStatus("PAID");
        payApplicationMapper.insert(paidApplication);

        PayApplication pendingApplication = new PayApplication();
        pendingApplication.setTenantId(TENANT_ID);
        pendingApplication.setProjectId(projectId);
        pendingApplication.setContractId(contractId);
        pendingApplication.setPartnerId(partnerId);
        pendingApplication.setApplyCode("PAY-PENDING-" + suffix);
        pendingApplication.setPayType("PROGRESS");
        pendingApplication.setApplyAmount(new BigDecimal("230000.00"));
        pendingApplication.setApprovedAmount(new BigDecimal("230000.00"));
        pendingApplication.setActualPayAmount(BigDecimal.ZERO);
        pendingApplication.setApprovalStatus("APPROVED");
        pendingApplication.setPayStatus("UNPAID");
        payApplicationMapper.insert(pendingApplication);

        // PayRecord
        PayRecord payRecord = new PayRecord();
        payRecord.setTenantId(TENANT_ID);
        payRecord.setPayApplicationId(paidApplication.getId());
        payRecord.setContractId(contractId);
        payRecord.setProjectId(projectId);
        payRecord.setPayAmount(new BigDecimal("100000.00"));
        payRecord.setPayDate(currentMonthDate);
        payRecord.setPayStatus("SUCCESS");
        payRecordMapper.insert(payRecord);

        PayRecord pendingPayRecord = new PayRecord();
        pendingPayRecord.setTenantId(TENANT_ID);
        pendingPayRecord.setPayApplicationId(pendingApplication.getId());
        pendingPayRecord.setContractId(contractId);
        pendingPayRecord.setProjectId(projectId);
        pendingPayRecord.setPayAmount(new BigDecimal("230000.00"));
        pendingPayRecord.setPayDate(currentMonthDate.minusDays(1));
        pendingPayRecord.setPayStatus("PENDING_APPROVAL");
        payRecordMapper.insert(pendingPayRecord);

        // CostSummary (project-level)
        CostSummary summary = new CostSummary();
        summary.setTenantId(TENANT_ID);
        summary.setProjectId(projectId);
        summary.setSummaryDate(currentMonthDate);
        summary.setTargetCost(new BigDecimal("8000000.00"));
        summary.setContractLockedCost(new BigDecimal("3000000.00"));
        summary.setActualCost(new BigDecimal("4000000.00"));
        summary.setPaidAmount(new BigDecimal("2000000.00"));
        summary.setEstimatedRemainingCost(new BigDecimal("4200000.00"));
        summary.setDynamicCost(new BigDecimal("8200000.00"));
        summary.setContractIncome(new BigDecimal("10000000.00"));
        summary.setExpectedProfit(new BigDecimal("1800000.00"));
        summary.setCostDeviation(new BigDecimal("200000.00"));
        costSummaryMapper.insert(summary);

        CostSummary lastMonthSummary = new CostSummary();
        lastMonthSummary.setTenantId(TENANT_ID);
        lastMonthSummary.setProjectId(projectId);
        lastMonthSummary.setSummaryDate(currentMonthDate.minusMonths(1).withDayOfMonth(1));
        lastMonthSummary.setTargetCost(new BigDecimal("7600000.00"));
        lastMonthSummary.setContractLockedCost(new BigDecimal("2800000.00"));
        lastMonthSummary.setActualCost(new BigDecimal("3500000.00"));
        lastMonthSummary.setPaidAmount(new BigDecimal("1800000.00"));
        lastMonthSummary.setEstimatedRemainingCost(new BigDecimal("3900000.00"));
        lastMonthSummary.setDynamicCost(new BigDecimal("7600000.00"));
        lastMonthSummary.setContractIncome(new BigDecimal("10000000.00"));
        lastMonthSummary.setExpectedProfit(new BigDecimal("2400000.00"));
        lastMonthSummary.setCostDeviation(BigDecimal.ZERO);
        costSummaryMapper.insert(lastMonthSummary);

        // CostSubject
        CostSubject subject = new CostSubject();
        subject.setTenantId(TENANT_ID);
        subject.setSubjectCode("SUBJ-" + suffix);
        subject.setSubjectName("人工费");
        subject.setLevel(1);
        costSubjectMapper.insert(subject);
        Long subjectId = subject.getId();

        // CostSummary with cost_subject_id
        CostSummary subjectSummary = new CostSummary();
        subjectSummary.setTenantId(TENANT_ID);
        subjectSummary.setProjectId(projectId);
        subjectSummary.setSummaryDate(currentMonthDate);
        subjectSummary.setCostSubjectId(subjectId);
        subjectSummary.setTargetCost(new BigDecimal("3000000.00"));
        subjectSummary.setContractLockedCost(new BigDecimal("1000000.00"));
        subjectSummary.setActualCost(new BigDecimal("1500000.00"));
        subjectSummary.setDynamicCost(new BigDecimal("3100000.00"));
        subjectSummary.setCostDeviation(new BigDecimal("100000.00"));
        costSummaryMapper.insert(subjectSummary);

        CostItem costItem = new CostItem();
        costItem.setTenantId(TENANT_ID);
        costItem.setProjectId(projectId);
        costItem.setContractId(contractId);
        costItem.setCostSubjectId(subjectId);
        costItem.setCostType("CT_LABOR");
        costItem.setAmount(new BigDecimal("1500000.00"));
        costItem.setTaxAmount(BigDecimal.ZERO);
        costItem.setAmountWithoutTax(new BigDecimal("1500000.00"));
        costItem.setSourceType("CT_CONTRACT");
        costItem.setSourceId(contractId);
        costItem.setSourceItemId(1L);
        costItem.setCostDate(currentMonthDate);
        costItem.setCostStatus("CONFIRMED");
        costItem.setGeneratedFlag(1);
        costItemMapper.insert(costItem);

        // AlertLog
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(projectId);
        alert.setRuleType("DYNAMIC_COST_EXCEEDS_TARGET");
        alert.setSeverity("HIGH");
        alert.setMessage("动态成本超出目标成本");
        alert.setIsRead(0);
        alert.setTriggeredAt(LocalDateTime.now());
        alertLogMapper.insert(alert);
        // Do NOT clear UserContext here — test methods need it

        return new SeedResult(projectId, project.getProjectName(), partnerId, warehouse.getId(), materialId, signalUserId);
    }

    protected static class SeedResult {
        final Long projectId;
        final String projectName;
        final Long partnerId;
        final Long warehouseId;
        final Long materialId;
        final Long signalUserId;
        SeedResult(Long projectId, String projectName, Long partnerId, Long warehouseId, Long materialId, Long signalUserId) {
            this.projectId = projectId;
            this.projectName = projectName;
            this.partnerId = partnerId;
            this.warehouseId = warehouseId;
            this.materialId = materialId;
            this.signalUserId = signalUserId;
        }
    }

    // ========================================================================
    // 1. Project Manager View — Single Project
    // ========================================================================

}
