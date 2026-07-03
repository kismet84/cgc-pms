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
import java.util.List;

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
class DashboardServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired private DashboardService dashboardService;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private CtContractMapper ctContractMapper;
    @Autowired private WfInstanceMapper wfInstanceMapper;
    @Autowired private WfTaskMapper wfTaskMapper;
    @Autowired private VarOrderMapper varOrderMapper;
    @Autowired private SubMeasureMapper subMeasureMapper;
    @Autowired private StlSettlementMapper stlSettlementMapper;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private AlertLogMapper alertLogMapper;
    @Autowired private CostSummaryMapper costSummaryMapper;
    @Autowired private CostSubjectMapper costSubjectMapper;
    @Autowired private CostItemMapper costItemMapper;
    @Autowired private MatPurchaseRequestMapper purchaseRequestMapper;
    @Autowired private MatPurchaseRequestItemMapper purchaseRequestItemMapper;
    @Autowired private MatPurchaseOrderMapper purchaseOrderMapper;
    @Autowired private MatPurchaseOrderItemMapper purchaseOrderItemMapper;
    @Autowired private MatReceiptMapper receiptMapper;
    @Autowired private MatReceiptItemMapper receiptItemMapper;
    @Autowired private MatRequisitionMapper requisitionMapper;
    @Autowired private MatWarehouseMapper warehouseMapper;
    @Autowired private MatStockMapper stockMapper;
    @Autowired private MdPartnerMapper partnerMapper;
    @Autowired private MdMaterialMapper materialMapper;
    @Autowired private SysUserMapper userMapper;
    @Autowired private TechItemMapper techItemMapper;

    private void setAdminContext() {
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
    private SeedResult seed(String suffix) {
        setAdminContext();
        LocalDate currentMonthDate = LocalDate.now().withDayOfMonth(1).plusDays(1);

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
        contract.setEndDate(currentMonthDate.plusDays(10));
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

        // PayRecord
        PayRecord payRecord = new PayRecord();
        payRecord.setTenantId(TENANT_ID);
        payRecord.setPayApplicationId(1L);
        payRecord.setContractId(contractId);
        payRecord.setProjectId(projectId);
        payRecord.setPayAmount(new BigDecimal("100000.00"));
        payRecord.setPayDate(currentMonthDate);
        payRecord.setPayStatus("SUCCESS");
        payRecordMapper.insert(payRecord);

        PayRecord pendingPayRecord = new PayRecord();
        pendingPayRecord.setTenantId(TENANT_ID);
        pendingPayRecord.setPayApplicationId(2L);
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

    static class SeedResult {
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

    @Test
    @Transactional
    @DisplayName("1.1 PM view: single project returns correct KPI counts")
    void testPMView_SingleProject() {
        SeedResult sr = seed("PM1");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);

        assertNotNull(vo, "PM VO should not be null");
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals(sr.projectName, vo.getProjectName());

        assertTrue(vo.getPendingTaskCount() >= 1, "At least 1 pending task");
        assertTrue(vo.getLaggingProjectCount() >= 1, "At least 1 lagging project");
        assertTrue(vo.getExpiringContractCount() >= 1, "At least 1 expiring contract");
        assertNotNull(vo.getPendingApprovals(), "pendingApprovals should not be null");
    }

    @Test
    @Transactional
    @DisplayName("1.1a PM view: pending approvals load instance details outside current user tasks")
    void testPMView_PendingApprovalsLoadOwnInstanceDetails() {
        SeedResult sr = seed("PM_APPROVAL_SUMMARY");

        WfInstance externalInstance = new WfInstance();
        externalInstance.setTenantId(TENANT_ID);
        externalInstance.setTemplateId(1L);
        externalInstance.setBusinessType("CONTRACT");
        externalInstance.setBusinessId(970000000000L);
        externalInstance.setProjectId(sr.projectId);
        externalInstance.setTitle("非当前用户审批-PM_APPROVAL_SUMMARY");
        externalInstance.setBusinessSummary("非当前用户审批摘要-PM_APPROVAL_SUMMARY");
        externalInstance.setAmount(new BigDecimal("123456.00"));
        externalInstance.setInstanceStatus("RUNNING");
        externalInstance.setInitiatorId(USER_ADMIN);
        wfInstanceMapper.insert(externalInstance);

        WfTask externalTask = new WfTask();
        externalTask.setTenantId(TENANT_ID);
        externalTask.setInstanceId(externalInstance.getId());
        externalTask.setNodeInstanceId(2L);
        externalTask.setBusinessType("CONTRACT");
        externalTask.setBusinessId(970000000000L);
        externalTask.setApproverId(2L);
        externalTask.setApproverName("项目总监");
        externalTask.setTaskStatus(WorkflowConstants.TASK_PENDING);
        externalTask.setReceivedAt(LocalDateTime.now().minusDays(4));
        wfTaskMapper.insert(externalTask);

        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);

        DashboardTaskItemVO item = vo.getPendingApprovals().stream()
                .filter(i -> externalTask.getId().toString().equals(i.getTaskId()))
                .findFirst()
                .orElseThrow();
        assertEquals("非当前用户审批-PM_APPROVAL_SUMMARY", item.getTitle());
        assertEquals("非当前用户审批摘要-PM_APPROVAL_SUMMARY", item.getItemSummary());
        assertEquals("项目总监", item.getOwnerName());
        assertEquals("123456.00", item.getAmount());
        assertEquals(4L, item.getPendingDays());
        assertEquals(sr.projectId.toString(), item.getProjectId());
        assertEquals(sr.projectName, item.getProjectName());
    }

    @Test
    @Transactional
    @DisplayName("1.1b PM view: payment approvals are not PM dashboard main-axis tasks")
    void testPMView_ExcludesPaymentApprovalTasks() {
        SeedResult sr = seed("PM_NO_PAYMENT");

        WfInstance payInstance = new WfInstance();
        payInstance.setTenantId(TENANT_ID);
        payInstance.setTemplateId(50005L);
        payInstance.setBusinessType("PAY_APPLICATION");
        payInstance.setBusinessId(970000000001L);
        payInstance.setProjectId(sr.projectId);
        payInstance.setTitle("待审批付款-PM_NO_PAYMENT");
        payInstance.setBusinessSummary("付款审批摘要-PM_NO_PAYMENT");
        payInstance.setAmount(new BigDecimal("98765.00"));
        payInstance.setInstanceStatus("RUNNING");
        payInstance.setInitiatorId(USER_ADMIN);
        wfInstanceMapper.insert(payInstance);

        WfTask payTask = new WfTask();
        payTask.setTenantId(TENANT_ID);
        payTask.setInstanceId(payInstance.getId());
        payTask.setNodeInstanceId(3L);
        payTask.setBusinessType("CONTRACT");
        payTask.setBusinessId(970000000001L);
        payTask.setApproverId(USER_ADMIN);
        payTask.setApproverName("项目经理");
        payTask.setTaskStatus(WorkflowConstants.TASK_PENDING);
        payTask.setReceivedAt(LocalDateTime.now().minusDays(1));
        wfTaskMapper.insert(payTask);

        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);

        assertTrue(vo.getPendingTasks().stream().noneMatch(i -> WorkflowBusinessTypes.PAY_REQUEST.equals(i.getBusinessType())));
        assertTrue(vo.getPendingTasks().stream().noneMatch(i -> "PAY_APPLICATION".equals(i.getBusinessType())));
        assertTrue(vo.getPendingApprovals().stream().noneMatch(i -> WorkflowBusinessTypes.PAY_REQUEST.equals(i.getBusinessType())));
        assertTrue(vo.getPendingApprovals().stream().noneMatch(i -> "PAY_APPLICATION".equals(i.getBusinessType())));
        assertTrue(vo.getPendingTasks().stream().noneMatch(i -> "待审批付款-PM_NO_PAYMENT".equals(i.getTitle())));
        assertTrue(vo.getPendingApprovals().stream().noneMatch(i -> "待审批付款-PM_NO_PAYMENT".equals(i.getTitle())));
    }

    @Test
    @Transactional
    @DisplayName("1.1c PM view: single project pending tasks stay scoped and carry readable fields")
    void testPMView_SingleProjectPendingTasksStayScopedAndCarryReadableFields() {
        SeedResult selected = seed("PM_SCOPE_A");
        SeedResult other = seed("PM_SCOPE_B");

        wfTaskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, TENANT_ID)
                        .eq(WfTask::getApproverId, USER_ADMIN))
                .stream()
                .filter(task -> {
                    WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
                    return instance != null && selected.projectId.equals(instance.getProjectId());
                })
                .forEach(task -> {
                    task.setReceivedAt(LocalDateTime.now().minusDays(9));
                    wfTaskMapper.updateById(task);
                });

        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(selected.projectId);

        assertTrue(vo.getPendingTasks().stream()
                .noneMatch(i -> other.projectId.toString().equals(i.getProjectId())),
                "single-project PM view must not include pending tasks from another project");

        DashboardTaskItemVO item = vo.getPendingTasks().stream()
                .filter(i -> selected.projectId.toString().equals(i.getProjectId()))
                .findFirst()
                .orElseThrow();
        assertEquals("审批-PM_SCOPE_A", item.getTitle());
        assertEquals("合同审批摘要-PM_SCOPE_A", item.getItemSummary());
        assertEquals("成本经理", item.getOwnerName());
        assertEquals("5000000.00", item.getAmount());
        assertEquals(9L, item.getPendingDays());
        assertEquals(selected.projectName, item.getProjectName());
    }

    @Test
    @Transactional
    @DisplayName("1.2 PM view: null projectId returns all-projects aggregate")
    void testPMView_AllProjects() {
        seed("PM2");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertTrue(vo.getPendingTaskCount() >= 1);
    }

    // ========================================================================
    // 2. Business Manager View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("2.1 BM view: single project returns correct contract totals")
    void testBMView_SingleProject() {
        SeedResult sr = seed("BM1");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertTrue(new BigDecimal(vo.getTotalContractAmount()).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(new BigDecimal(vo.getVarOrderAmount()).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(new BigDecimal(vo.getSubMeasureAmount()).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(vo.getPaidRatio().contains("%"));
        assertNotNull(vo.getSettlementProgress());
        assertNotNull(vo.getRecentChanges());
        assertTrue(vo.getRecentChanges().size() <= 5);
    }

    @Test
    @Transactional
    @DisplayName("2.2 BM view: null projectId returns tenant-wide aggregate")
    void testBMView_AllProjects() {
        seed("BM2");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertNotNull(vo.getTotalContractAmount());
        assertNotNull(vo.getPaidRatio());
    }

    @Test
    @Transactional
    @DisplayName("2.3 BM view: grace over zero contracts")
    void testBMView_GracefulEmpty() {
        seed("BM3");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(null);
        assertNotNull(vo);
        assertNotNull(vo.getTotalContractAmount());
    }

    // ========================================================================
    // 3. Cost Manager View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("3.1 Cost view: returns cost data from cost_summary")
    void testCostView_SingleProject() {
        SeedResult sr = seed("COST1");
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getTargetCost());
        assertNotNull(vo.getDynamicCost());
        assertNotNull(vo.getCostDeviation());
        assertNotNull(vo.getActualCost());
        assertNotNull(vo.getExpectedProfit());
        assertTrue(vo.getOverBudgetAlerts().size() >= 1);
        assertEquals(sr.projectName, vo.getOverBudgetAlerts().get(0).getProjectName());
    }

    @Test
    @Transactional
    @DisplayName("3.1b Cost view: returns dashboard contract lists from real project data")
    void testCostView_ReturnsDashboardContractLists() {
        SeedResult sr = seed("COST_FULL");
        wfTaskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, TENANT_ID)
                        .eq(WfTask::getApproverId, USER_ADMIN))
                .stream()
                .filter(task -> {
                    WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
                    return instance != null && sr.projectId.equals(instance.getProjectId());
                })
                .forEach(task -> {
                    task.setReceivedAt(LocalDateTime.now().minusDays(9));
                    wfTaskMapper.updateById(task);
                });
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals("8000000.00", vo.getTargetCost());
        assertEquals("8200000.00", vo.getDynamicCost());

        assertNotNull(vo.getTrendPoints());
        assertEquals(2, vo.getTrendPoints().size(), "monthly trend should come from cost_summary dates");
        assertTrue(vo.getTrendPoints().get(0).getMonth().compareTo(vo.getTrendPoints().get(1).getMonth()) <= 0);
        assertEquals("7600000.00", vo.getTrendPoints().get(0).getDynamicCost());
        assertEquals("8200000.00", vo.getTrendPoints().get(1).getDynamicCost());

        assertNotNull(vo.getSubjectRankings());
        assertEquals(1, vo.getSubjectRankings().size());
        assertEquals("人工费", vo.getSubjectRankings().get(0).getCostSubjectName());
        assertEquals("1500000.00", vo.getSubjectRankings().get(0).getActualCost());

        assertNotNull(vo.getOverdueItems());
        assertEquals(1, vo.getOverdueItems().size());
        assertEquals("审批-COST_FULL", vo.getOverdueItems().get(0).getTitle());
        assertTrue(vo.getOverdueItems().get(0).getOverdueDays() >= 2);
        assertEquals("成本经理", vo.getOverdueItems().get(0).getOwnerName());

        assertNotNull(vo.getPendingPayments());
        assertEquals(1, vo.getPendingPayments().size());
        assertEquals("230000.00", vo.getPendingPayments().get(0).getPayAmount());
        assertEquals("PENDING_APPROVAL", vo.getPendingPayments().get(0).getPayStatus());
        assertEquals("Contract COST_FULL", vo.getPendingPayments().get(0).getContractName());

        assertNotNull(vo.getLedgerRows());
        CostManagerDashboardVO.LedgerRow costRow = vo.getLedgerRows().stream()
                .filter(row -> "cost".equals(row.getRowType()))
                .findFirst()
                .orElseThrow();
        assertEquals("人工费", costRow.getCostSubjectName());
        assertEquals("CT-COST_FULL", costRow.getContractCode());
        assertEquals("Contract COST_FULL", costRow.getContractName());
        assertEquals("正常", costRow.getStatus());
        assertEquals((long) vo.getLedgerRows().size(), vo.getLedgerTotal());
    }

    @Test
    @Transactional
    @DisplayName("3.1c Cost view: selected month uses cost snapshot up to that month")
    void testCostView_SelectedMonthUsesMonthlySnapshot() {
        SeedResult sr = seed("COST_MONTH");
        String lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1).toString().substring(0, 7);

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId, lastMonth);

        assertNotNull(vo);
        assertEquals("7600000.00", vo.getTargetCost());
        assertEquals("7600000.00", vo.getDynamicCost());
        assertEquals("3500000.00", vo.getActualCost());
        assertEquals(1, vo.getTrendPoints().size());
        assertEquals(lastMonth, vo.getTrendPoints().get(0).getMonth());
    }

    @Test
    @Transactional
    @DisplayName("3.1d Cost view: selected month keeps latest subject snapshot")
    void testCostView_SelectedMonthKeepsLatestSubjectSnapshot() {
        SeedResult sr = seed("COST_SUBJECT_MONTH");
        LocalDate lastMonthDate = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        for (CostSummary summary : costSummaryMapper.selectList(null)) {
            if (sr.projectId.equals(summary.getProjectId()) && summary.getCostSubjectId() != null) {
                summary.setSummaryDate(lastMonthDate);
                costSummaryMapper.updateById(summary);
            }
        }

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId, LocalDate.now().toString().substring(0, 7));

        assertEquals(1, vo.getSubjectRankings().size());
        assertEquals("人工费", vo.getSubjectRankings().get(0).getCostSubjectName());
        assertTrue(vo.getLedgerRows().stream().anyMatch(row -> "人工费".equals(row.getCostSubjectName())));
    }

    @Test
    @Transactional
    @DisplayName("3.2 Purchase manager view: aggregates existing purchase and receipt signals")
    void testPurchaseManagerView_MvpSignals() {
        SeedResult sr = seed("PUR_DASH");

        MatPurchaseOrder newestOrder = new MatPurchaseOrder();
        newestOrder.setTenantId(TENANT_ID);
        newestOrder.setProjectId(sr.projectId);
        newestOrder.setPartnerId(sr.partnerId);
        newestOrder.setOrderCode("PO-PUR_DASH-NEWEST");
        newestOrder.setOrderDate(LocalDate.now());
        newestOrder.setDeliveryDate(LocalDate.now().plusDays(7));
        newestOrder.setTotalAmount(new BigDecimal("140000.00"));
        newestOrder.setApprovalStatus("APPROVED");
        newestOrder.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(newestOrder);

        MatPurchaseOrderItem newestOrderItem = new MatPurchaseOrderItem();
        newestOrderItem.setTenantId(TENANT_ID);
        newestOrderItem.setProjectId(sr.projectId);
        newestOrderItem.setOrderId(newestOrder.getId());
        newestOrderItem.setMaterialId(sr.materialId);
        newestOrderItem.setMaterialName("止水钢板-PUR_DASH");
        newestOrderItem.setQuantity(new BigDecimal("10.0000"));
        newestOrderItem.setUnit("吨");
        newestOrderItem.setUnitPrice(new BigDecimal("14000.0000"));
        newestOrderItem.setAmount(new BigDecimal("140000.00"));
        purchaseOrderItemMapper.insert(newestOrderItem);

        MatPurchaseOrder olderLowerAmountOrder = new MatPurchaseOrder();
        olderLowerAmountOrder.setTenantId(TENANT_ID);
        olderLowerAmountOrder.setProjectId(sr.projectId);
        olderLowerAmountOrder.setPartnerId(sr.partnerId);
        olderLowerAmountOrder.setOrderCode("PO-PUR_DASH-OLD-LOW");
        olderLowerAmountOrder.setOrderDate(LocalDate.now().minusDays(8));
        olderLowerAmountOrder.setDeliveryDate(LocalDate.now().minusDays(5));
        olderLowerAmountOrder.setTotalAmount(new BigDecimal("110000.00"));
        olderLowerAmountOrder.setApprovalStatus("APPROVED");
        olderLowerAmountOrder.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(olderLowerAmountOrder);

        MatPurchaseOrder olderHigherAmountOrder = new MatPurchaseOrder();
        olderHigherAmountOrder.setTenantId(TENANT_ID);
        olderHigherAmountOrder.setProjectId(sr.projectId);
        olderHigherAmountOrder.setPartnerId(sr.partnerId);
        olderHigherAmountOrder.setOrderCode("PO-PUR_DASH-OLD-HIGH");
        olderHigherAmountOrder.setOrderDate(LocalDate.now().minusDays(8));
        olderHigherAmountOrder.setDeliveryDate(LocalDate.now().minusDays(5));
        olderHigherAmountOrder.setTotalAmount(new BigDecimal("130000.00"));
        olderHigherAmountOrder.setApprovalStatus("APPROVED");
        olderHigherAmountOrder.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(olderHigherAmountOrder);

        MatPurchaseOrderItem olderHigherAmountOrderItem = new MatPurchaseOrderItem();
        olderHigherAmountOrderItem.setTenantId(TENANT_ID);
        olderHigherAmountOrderItem.setProjectId(sr.projectId);
        olderHigherAmountOrderItem.setOrderId(olderHigherAmountOrder.getId());
        olderHigherAmountOrderItem.setMaterialId(sr.materialId);
        olderHigherAmountOrderItem.setMaterialName("高强螺栓-PUR_DASH");
        olderHigherAmountOrderItem.setQuantity(new BigDecimal("20.0000"));
        olderHigherAmountOrderItem.setUnit("套");
        olderHigherAmountOrderItem.setUnitPrice(new BigDecimal("6500.0000"));
        olderHigherAmountOrderItem.setAmount(new BigDecimal("130000.00"));
        purchaseOrderItemMapper.insert(olderHigherAmountOrderItem);

        MatReceipt olderLowerAmountReceipt = new MatReceipt();
        olderLowerAmountReceipt.setTenantId(TENANT_ID);
        olderLowerAmountReceipt.setProjectId(sr.projectId);
        olderLowerAmountReceipt.setPartnerId(sr.partnerId);
        olderLowerAmountReceipt.setReceiptCode("RC-PUR_DASH-OLD-LOW");
        olderLowerAmountReceipt.setReceiptDate(LocalDate.now().minusDays(3));
        olderLowerAmountReceipt.setWarehouseId(sr.warehouseId);
        olderLowerAmountReceipt.setTotalAmount(new BigDecimal("50000.00"));
        olderLowerAmountReceipt.setApprovalStatus("APPROVING");
        receiptMapper.insert(olderLowerAmountReceipt);

        MatReceipt olderHigherAmountReceipt = new MatReceipt();
        olderHigherAmountReceipt.setTenantId(TENANT_ID);
        olderHigherAmountReceipt.setProjectId(sr.projectId);
        olderHigherAmountReceipt.setPartnerId(sr.partnerId);
        olderHigherAmountReceipt.setReceiptCode("RC-PUR_DASH-OLD-HIGH");
        olderHigherAmountReceipt.setReceiptDate(LocalDate.now().minusDays(3));
        olderHigherAmountReceipt.setWarehouseId(sr.warehouseId);
        olderHigherAmountReceipt.setTotalAmount(new BigDecimal("90000.00"));
        olderHigherAmountReceipt.setApprovalStatus("APPROVING");
        receiptMapper.insert(olderHigherAmountReceipt);

        MatReceiptItem olderHigherAmountReceiptItem = new MatReceiptItem();
        olderHigherAmountReceiptItem.setTenantId(TENANT_ID);
        olderHigherAmountReceiptItem.setReceiptId(olderHigherAmountReceipt.getId());
        olderHigherAmountReceiptItem.setMaterialId(sr.materialId);
        olderHigherAmountReceiptItem.setActualQuantity(new BigDecimal("8.0000"));
        olderHigherAmountReceiptItem.setQualifiedQuantity(new BigDecimal("8.0000"));
        olderHigherAmountReceiptItem.setUnitPrice(new BigDecimal("11250.0000"));
        olderHigherAmountReceiptItem.setAmount(new BigDecimal("90000.00"));
        receiptItemMapper.insert(olderHigherAmountReceiptItem);

        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals(1L, vo.getPendingRequestCount());
        assertEquals(4L, vo.getActiveOrderCount());
        assertEquals(3L, vo.getOverdueDeliveryCount());
        assertEquals(3L, vo.getPendingReceiptCount());
        assertEquals(1L, vo.getLowStockItemCount());
        assertEquals("500000.00", vo.getTotalOrderAmount());

        assertNotNull(vo.getPurchaseOrders());
        assertFalse(vo.getPurchaseOrders().isEmpty());
        assertEquals("PO-PUR_DASH-NEWEST", vo.getPurchaseOrders().get(0).getCode());
        assertTrue(vo.getPurchaseOrders().stream()
                .allMatch(item -> "PURCHASE_ORDER".equals(item.getSourceType())));
        assertTrue(vo.getPurchaseOrders().stream()
                .noneMatch(item -> item.getCode() != null && item.getCode().startsWith("RC-")));

        DashboardBusinessItemVO request = vo.getRecentRequests().get(0);
        assertEquals("PR-PUR_DASH", request.getCode());
        assertEquals("钢筋-PUR_DASH", request.getTitle());
        assertEquals("钢筋-PUR_DASH", request.getItemSummary());
        assertEquals(sr.projectName, request.getProjectName());
        assertEquals("抄送用户1", request.getOwnerName());
        assertNull(request.getAmount(), "采购申请无真实金额字段时不返回假金额");

        DashboardBusinessItemVO overdueOrder = vo.getOverdueOrders().get(0);
        assertEquals("PO-PUR_DASH-OLD-HIGH", overdueOrder.getCode());
        assertEquals("高强螺栓-PUR_DASH", overdueOrder.getTitle());
        assertEquals("高强螺栓-PUR_DASH", overdueOrder.getItemSummary());
        assertEquals("供应商-PUR_DASH", overdueOrder.getPartnerName());
        assertEquals(5L, overdueOrder.getOverdueDays());
        assertEquals("130000.00", overdueOrder.getAmount());

        DashboardBusinessItemVO pendingReceipt = vo.getPendingReceipts().get(0);
        assertEquals("RC-PUR_DASH-OLD-HIGH", pendingReceipt.getCode());
        assertEquals("钢筋-PUR_DASH", pendingReceipt.getTitle());
        assertEquals("钢筋-PUR_DASH", pendingReceipt.getItemSummary());
        assertEquals("供应商-PUR_DASH", pendingReceipt.getPartnerName());
        assertEquals(3L, pendingReceipt.getPendingDays());
        assertEquals("90000.00", pendingReceipt.getAmount());
    }

    @Test
    @Transactional
    @DisplayName("3.2a Default demo project: purchase orders are limited, sorted, and include long summary")
    void testDefaultDemoProject_PurchaseOrdersOverflowDemoData() {
        setAdminContext();

        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(10001L);
        List<DashboardBusinessItemVO> purchaseOrders = vo.getPurchaseOrders();
        Long candidateCount = purchaseOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getTenantId, TENANT_ID)
                        .eq(MatPurchaseOrder::getProjectId, 10001L)
                        .eq(MatPurchaseOrder::getDeletedFlag, 0));

        assertEquals(5, purchaseOrders.size());
        assertTrue(candidateCount > 5L);
        assertIterableEquals(List.of(
                "PO-DEMO-PUR-OVERFLOW-001",
                "PO-DEMO-PUR-OVERFLOW-002",
                "PO-DEMO-PUR-OVERFLOW-003",
                "PO-DEMO-PUR-OVERFLOW-004",
                "PO-DEMO-PUR-OVERFLOW-005"
        ), purchaseOrders.stream().map(DashboardBusinessItemVO::getCode).toList());
        assertTrue(purchaseOrders.stream().allMatch(i -> "PURCHASE_ORDER".equals(i.getSourceType())));
        assertTrue(purchaseOrders.stream().allMatch(i -> i.getCode() != null && i.getCode().startsWith("PO-")));
        assertTrue(purchaseOrders.stream().noneMatch(i -> i.getCode().startsWith("RC-")));
        assertTrue(purchaseOrders.stream().anyMatch(i -> i.getItemSummary() != null
                && i.getItemSummary().contains("超长摘要")
                && i.getItemSummary().length() > 50));
    }

    @Test
    @Transactional
    @DisplayName("3.2b Default demo project: V105 realistic purchase and production seed is readable")
    void testDefaultDemoProject_DashboardRealisticDemoDistribution() {
        setAdminContext();

        MdMaterial longSummaryMaterial = new MdMaterial();
        longSummaryMaterial.setTenantId(TENANT_ID);
        longSummaryMaterial.setMaterialCode("MAT-DEMO-LONG-SUMMARY");
        longSummaryMaterial.setMaterialName("超长摘要-驾驶舱测试补充采购申请摘要用于验证最近请求展示以及额外字符超过三十个");
        longSummaryMaterial.setUnit("批");
        longSummaryMaterial.setStatus("ENABLE");
        materialMapper.insert(longSummaryMaterial);

        MatPurchaseRequest longSummaryRequest = new MatPurchaseRequest();
        longSummaryRequest.setTenantId(TENANT_ID);
        longSummaryRequest.setProjectId(10001L);
        longSummaryRequest.setContractId(30001L);
        longSummaryRequest.setRequestCode("PR-DEMO-LONG-SUMMARY");
        longSummaryRequest.setApprovalStatus("APPROVING");
        longSummaryRequest.setStatus("DRAFT");
        longSummaryRequest.setCreatedBy(1L);
        purchaseRequestMapper.insert(longSummaryRequest);

        MatPurchaseRequestItem longSummaryItem = new MatPurchaseRequestItem();
        longSummaryItem.setTenantId(TENANT_ID);
        longSummaryItem.setRequestId(longSummaryRequest.getId());
        longSummaryItem.setMaterialId(longSummaryMaterial.getId());
        longSummaryItem.setQuantity(new BigDecimal("1.0000"));
        longSummaryItem.setUnit("批");
        longSummaryItem.setPlannedDate(LocalDate.now().plusDays(3));
        purchaseRequestItemMapper.insert(longSummaryItem);

        PurchaseManagerDashboardVO purchase = dashboardService.getPurchaseManagerView(10001L);
        ProductionManagerDashboardVO production = dashboardService.getProductionManagerView(10001L);

        assertEquals(5, purchase.getRecentRequests().size());
        assertEquals(5, purchase.getOverdueOrders().size());
        assertEquals(5, purchase.getPendingReceipts().size());
        assertTrue(purchase.getRecentRequests().stream().anyMatch(i -> "PR-DEMO-LONG-SUMMARY".equals(i.getCode())
                && i.getItemSummary() != null
                && i.getItemSummary().contains("超长摘要")
                && i.getItemSummary().length() > 30));

        List<MatPurchaseRequest> requests = purchaseRequestMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseRequest>()
                        .eq(MatPurchaseRequest::getTenantId, TENANT_ID)
                        .likeRight(MatPurchaseRequest::getRequestCode, "PR-DEMO-REAL-")
                        .eq(MatPurchaseRequest::getDeletedFlag, 0));
        assertEquals(6, requests.size());
        assertTrue(requests.stream().map(MatPurchaseRequest::getApprovalStatus).distinct().count() >= 3);

        Long overdueOrderCount = purchaseOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getTenantId, TENANT_ID)
                        .likeRight(MatPurchaseOrder::getOrderCode, "PO-DEMO-REAL-OVD-")
                        .eq(MatPurchaseOrder::getDeletedFlag, 0));
        Long receiptCount = receiptMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatReceipt>()
                        .eq(MatReceipt::getTenantId, TENANT_ID)
                        .likeRight(MatReceipt::getReceiptCode, "RC-DEMO-REAL-")
                        .eq(MatReceipt::getDeletedFlag, 0));
        assertEquals(5L, overdueOrderCount);
        assertEquals(6L, receiptCount);

        assertEquals(5, production.getRecentReceipts().size());
        assertEquals(5, production.getRecentRequisitions().size());
        assertEquals(5, production.getRecentSubMeasures().size());
        assertTrue(production.getLowStockItemCount() >= 5L);
        assertTrue(production.getRecentReceipts().stream().anyMatch(i -> i.getItemSummary() != null
                && i.getItemSummary().contains("施工部位")
                && i.getItemSummary().length() > 30));

        List<MatRequisition> requisitions = requisitionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatRequisition>()
                        .eq(MatRequisition::getTenantId, TENANT_ID)
                        .likeRight(MatRequisition::getRequisitionCode, "REQ-DEMO-REAL-")
                        .eq(MatRequisition::getDeletedFlag, 0));
        List<SubMeasure> measures = subMeasureMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubMeasure>()
                        .eq(SubMeasure::getTenantId, TENANT_ID)
                        .likeRight(SubMeasure::getMeasureCode, "SM-DEMO-REAL-")
                        .eq(SubMeasure::getDeletedFlag, 0));
        assertEquals(5, requisitions.size());
        assertEquals(5, measures.size());
        assertTrue(requisitions.stream().map(MatRequisition::getApprovalStatus).distinct().count() >= 3);
        assertTrue(measures.stream().map(SubMeasure::getStatus).distinct().count() >= 3);
    }

    @Test
    @Transactional
    @DisplayName("3.3 Production manager MVP view: uses receipt, requisition, sub-measure and stock signals")
    void testProductionManagerView_MvpSignals() {
        SeedResult sr = seed("PROD_DASH");

        MatReceipt noSummaryReceipt = new MatReceipt();
        noSummaryReceipt.setTenantId(TENANT_ID);
        noSummaryReceipt.setProjectId(sr.projectId);
        noSummaryReceipt.setPartnerId(sr.partnerId);
        noSummaryReceipt.setReceiptCode("RC-PROD_DASH-NO-SUMMARY");
        noSummaryReceipt.setReceiptDate(LocalDate.now());
        noSummaryReceipt.setWarehouseId(sr.warehouseId);
        noSummaryReceipt.setReceiverId(sr.signalUserId);
        noSummaryReceipt.setQualityStatus("PENDING");
        noSummaryReceipt.setTotalAmount(new BigDecimal("1000.00"));
        noSummaryReceipt.setApprovalStatus("APPROVING");
        receiptMapper.insert(noSummaryReceipt);

        ProductionManagerDashboardVO vo = dashboardService.getProductionManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals(2L, vo.getReceiptCount());
        assertEquals(1L, vo.getRequisitionCount());
        assertEquals(1L, vo.getPendingStockOutCount());
        assertEquals(1L, vo.getSubMeasureCount());
        assertEquals(1L, vo.getLowStockItemCount());
        assertEquals("80000.00", vo.getConfirmedMeasureAmount());

        DashboardBusinessItemVO receipt = vo.getRecentReceipts().stream()
                .filter(i -> "RC-PROD_DASH".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("RC-PROD_DASH", receipt.getCode());
        assertNotEquals("RC-PROD_DASH", receipt.getTitle());
        assertEquals("钢筋-PROD_DASH", receipt.getItemSummary());
        assertEquals("供应商-PROD_DASH", receipt.getPartnerName());
        assertNotNull(receipt.getOwnerName());
        assertEquals(1L, receipt.getPendingDays());

        DashboardBusinessItemVO receiptWithoutSummary = vo.getRecentReceipts().stream()
                .filter(i -> "RC-PROD_DASH-NO-SUMMARY".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertNull(receiptWithoutSummary.getTitle());
        assertNull(receiptWithoutSummary.getItemSummary());

        DashboardBusinessItemVO requisition = vo.getRecentRequisitions().get(0);
        assertEquals("RQ-PROD_DASH", requisition.getCode());
        assertEquals("供应商-PROD_DASH", requisition.getPartnerName());
        assertNotNull(requisition.getOwnerName());
        assertEquals(0, new BigDecimal("50000.00").compareTo(new BigDecimal(requisition.getAmount())));
        assertNull(requisition.getItemSummary());

        DashboardBusinessItemVO subMeasure = vo.getRecentSubMeasures().get(0);
        assertEquals("SM-PROD_DASH", subMeasure.getCode());
        assertNull(subMeasure.getItemSummary());
        assertEquals("供应商-PROD_DASH", subMeasure.getPartnerName());
        assertEquals("80000.00", subMeasure.getAmount());

        assertTrue(vo.getRecentRequisitions().stream()
                .noneMatch(i -> "PENDING_STOCK_OUT".equals(i.getItemSummary()) || "STOCKED_OUT".equals(i.getItemSummary())));
        assertTrue(vo.getRecentSubMeasures().stream()
                .noneMatch(i -> "2026-06".equals(i.getItemSummary())));
    }

    @Test
    @Transactional
    @DisplayName("3.5 Chief engineer view: maps owner and overdue days from tech item")
    void testChiefEngineerView_TechItemOwnerAndOverdueDays() {
        SeedResult sr = seed("CHIEF_DASH");

        TechItem item = new TechItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(sr.projectId);
        item.setItemType("TECH_ISSUE");
        item.setItemCode("TECH-CHIEF_DASH");
        item.setItemTitle("重大技术问题-CHIEF_DASH");
        item.setItemLevel("MAJOR");
        item.setItemStatus("OPEN");
        item.setDiscoveredAt(LocalDateTime.now().minusDays(5));
        item.setDueDate(LocalDateTime.now().minusDays(2));
        item.setResponsibleUserId(sr.signalUserId);
        techItemMapper.insert(item);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId);

        DashboardBusinessItemVO issue = vo.getOpenIssues().stream()
                .filter(i -> "TECH-CHIEF_DASH".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("重大技术问题-CHIEF_DASH", issue.getTitle());
        assertEquals("MAJOR", issue.getAmount());
        assertNotNull(issue.getOwnerName());
        assertEquals(2L, issue.getOverdueDays());
    }

    @Test
    @Transactional
    @DisplayName("3.5a Chief engineer view: future due date does not emit overdueDays 0")
    void testChiefEngineerView_FutureDueDateHasNoOverdueDays() {
        SeedResult sr = seed("CHIEF_FUTURE");

        TechItem item = new TechItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(sr.projectId);
        item.setItemType("TECH_ISSUE");
        item.setItemCode("TECH-CHIEF_FUTURE");
        item.setItemTitle("未来到期技术问题-CHIEF_FUTURE");
        item.setItemLevel("MAJOR");
        item.setItemStatus("OPEN");
        item.setDiscoveredAt(LocalDateTime.now().minusDays(1));
        item.setDueDate(LocalDateTime.now().plusDays(2));
        item.setResponsibleUserId(sr.signalUserId);
        techItemMapper.insert(item);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId);

        DashboardBusinessItemVO issue = vo.getOpenIssues().stream()
                .filter(i -> "TECH-CHIEF_FUTURE".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertNull(issue.getOverdueDays());
        assertTrue(vo.getOverdueItems().stream().noneMatch(i -> "TECH-CHIEF_FUTURE".equals(i.getCode())));
    }

    @Test
    @Transactional
    @DisplayName("3.5b Chief engineer view: yesterday due item is overdue by calendar day")
    void testChiefEngineerView_YesterdayDueDateHasPositiveOverdueDays() {
        SeedResult sr = seed("CHIEF_YESTERDAY");

        TechItem item = new TechItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(sr.projectId);
        item.setItemType("TECH_ISSUE");
        item.setItemCode("TECH-CHIEF_YESTERDAY");
        item.setItemTitle("昨日到期技术问题-CHIEF_YESTERDAY");
        item.setItemLevel("MAJOR");
        item.setItemStatus("OPEN");
        item.setDiscoveredAt(LocalDateTime.now().minusDays(2));
        item.setDueDate(LocalDate.now().minusDays(1).atTime(23, 59));
        item.setResponsibleUserId(sr.signalUserId);
        techItemMapper.insert(item);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId);

        DashboardBusinessItemVO overdue = vo.getOverdueItems().stream()
                .filter(i -> "TECH-CHIEF_YESTERDAY".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertTrue(overdue.getOverdueDays() > 0L);
        assertEquals(1L, overdue.getOverdueDays());
    }

    @Test
    @Transactional
    @DisplayName("3.5c Chief engineer view: today due item is open but not overdue")
    void testChiefEngineerView_TodayDueDateIsNotOverdue() {
        SeedResult sr = seed("CHIEF_TODAY");

        TechItem item = new TechItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(sr.projectId);
        item.setItemType("TECH_ISSUE");
        item.setItemCode("TECH-CHIEF_TODAY");
        item.setItemTitle("今日到期技术问题-CHIEF_TODAY");
        item.setItemLevel("MAJOR");
        item.setItemStatus("OPEN");
        item.setDiscoveredAt(LocalDateTime.now().minusDays(1));
        item.setDueDate(LocalDate.now().atTime(0, 1));
        item.setResponsibleUserId(sr.signalUserId);
        techItemMapper.insert(item);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId);

        DashboardBusinessItemVO issue = vo.getOpenIssues().stream()
                .filter(i -> "TECH-CHIEF_TODAY".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertNull(issue.getOverdueDays());
        assertTrue(vo.getOverdueItems().stream().noneMatch(i -> "TECH-CHIEF_TODAY".equals(i.getCode())));
    }

    @Test
    @Transactional
    @DisplayName("3.4 Default demo project: purchase and production dashboards are not blank")
    void testDefaultDemoProject_PurchaseAndProductionDashboardsNotBlank() {
        setAdminContext();

        PurchaseManagerDashboardVO purchase = dashboardService.getPurchaseManagerView(null);
        ProductionManagerDashboardVO production = dashboardService.getProductionManagerView(null);

        assertNotNull(purchase);
        assertEquals("全部项目", purchase.getProjectName());
        assertTrue(purchase.getPendingRequestCount() > 0L);
        assertTrue(purchase.getActiveOrderCount() > 0L);
        assertFalse(purchase.getRecentRequests().isEmpty());
        assertFalse(purchase.getPurchaseOrders().isEmpty());
        assertTrue(purchase.getPurchaseOrders().stream()
                .allMatch(item -> "PURCHASE_ORDER".equals(item.getSourceType())));
        assertTrue(purchase.getPurchaseOrders().stream()
                .noneMatch(item -> item.getCode() != null && item.getCode().startsWith("RC-")));
        assertFalse(purchase.getOverdueOrders().isEmpty());
        assertFalse(purchase.getPendingReceipts().isEmpty());

        assertNotNull(production);
        assertEquals("全部项目", production.getProjectName());
        assertTrue(production.getReceiptCount() > 0L);
        assertTrue(production.getRequisitionCount() > 0L);
        assertTrue(production.getPendingStockOutCount() > 0L);
        assertTrue(production.getSubMeasureCount() > 0L);
        assertFalse(production.getRecentReceipts().isEmpty());
        assertFalse(production.getRecentRequisitions().isEmpty());
        assertFalse(production.getRecentSubMeasures().isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("3.5 Default demo project: project manager dashboard is not blank and excludes payment workflows")
    void testDefaultDemoProject_ProjectManagerDashboardNotBlank() {
        setAdminContext();

        PmProject defaultProject = projectMapper.selectById(10001L);
        if (defaultProject == null) {
            defaultProject = projectMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PmProject>()
                                    .eq(PmProject::getTenantId, TENANT_ID)
                                    .eq(PmProject::getStatus, "ACTIVE")
                                    .eq(PmProject::getDeletedFlag, 0)
                                    .orderByDesc(PmProject::getCreatedAt)
                                    .orderByDesc(PmProject::getId))
                    .stream()
                    .findFirst()
                    .orElseThrow();
        }

        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(defaultProject.getId());

        assertNotNull(vo);
        assertEquals(defaultProject.getId().toString(), vo.getProjectId());
        assertTrue(vo.getPendingTaskCount() > 0L);
        assertTrue(vo.getPendingApprovalCount() > 0L);
        assertTrue(vo.getExpiringContractCount() > 0L);
        assertTrue(vo.getLaggingProjectCount() > 0L);
        assertFalse(vo.getPendingTasks().isEmpty());
        assertFalse(vo.getPendingApprovals().isEmpty());
        assertFalse(vo.getExpiringContracts().isEmpty());
        assertTrue(vo.getPendingTasks().stream().noneMatch(i -> WorkflowBusinessTypes.PAY_REQUEST.equals(i.getBusinessType())));
        assertTrue(vo.getPendingTasks().stream().noneMatch(i -> "PAY_APPLICATION".equals(i.getBusinessType())));
        assertTrue(vo.getPendingApprovals().stream().noneMatch(i -> WorkflowBusinessTypes.PAY_REQUEST.equals(i.getBusinessType())));
        assertTrue(vo.getPendingApprovals().stream().noneMatch(i -> "PAY_APPLICATION".equals(i.getBusinessType())));
    }

    @Test
    @Transactional
    @DisplayName("3.6 Default demo project: chief engineer dashboard has a today-due item")
    void testDefaultDemoProject_ChiefEngineerDashboardHasTodayDueItem() {
        setAdminContext();

        PmProject defaultProject = projectMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PmProject>()
                                .eq(PmProject::getTenantId, TENANT_ID)
                                .eq(PmProject::getDeletedFlag, 0)
                                .in(PmProject::getId, java.util.List.of(2071032241708793858L, 10001L)))
                .stream()
                .sorted(java.util.Comparator
                        .comparing((PmProject p) -> p.getId().equals(2071032241708793858L) ? 0 : 1)
                        .thenComparing(PmProject::getId))
                .findFirst()
                .orElseGet(() -> projectMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PmProject>()
                                        .eq(PmProject::getTenantId, TENANT_ID)
                                        .eq(PmProject::getStatus, "ACTIVE")
                                        .eq(PmProject::getDeletedFlag, 0)
                                        .orderByAsc(PmProject::getId))
                        .stream()
                        .findFirst()
                        .orElseThrow());

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(defaultProject.getId());

        DashboardBusinessItemVO todayDueItem = vo.getOpenIssues().stream()
                .filter(i -> "TECH-DEMO-105".equals(i.getCode()))
                .findFirst()
                .orElseThrow();

        assertNotNull(vo);
        assertEquals(defaultProject.getId().toString(), vo.getProjectId());
        assertTrue(vo.getOpenIssueCount() > 0L);
        assertEquals("TECH-DEMO-105", todayDueItem.getCode());
        assertTrue(todayDueItem.getDate().startsWith(LocalDate.now().toString()));
        assertNull(todayDueItem.getOverdueDays());
        assertTrue(vo.getOverdueItems().stream().noneMatch(i -> "TECH-DEMO-105".equals(i.getCode())));
    }

    @Test
    @Transactional
    @DisplayName("3.1e Cost view: fallback rankings include variation cost items")
    void testCostView_FallbackRankingsIncludeVariationCostItems() {
        SeedResult sr = seed("COST_VAR_ITEM");
        costSummaryMapper.physicalDeleteByTenantAndProject(TENANT_ID, sr.projectId);

        CostSubject subject = costSubjectMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostSubject>()
                        .eq(CostSubject::getTenantId, TENANT_ID)
                        .eq(CostSubject::getSubjectCode, "SUBJ-COST_VAR_ITEM"))
                .stream()
                .findFirst()
                .orElseThrow();

        CostItem variationCost = new CostItem();
        variationCost.setTenantId(TENANT_ID);
        variationCost.setProjectId(sr.projectId);
        variationCost.setCostSubjectId(subject.getId());
        variationCost.setCostType("VARIATION");
        variationCost.setAmount(new BigDecimal("30000.00"));
        variationCost.setTaxAmount(BigDecimal.ZERO);
        variationCost.setAmountWithoutTax(new BigDecimal("30000.00"));
        variationCost.setSourceType("VAR_ORDER");
        variationCost.setSourceId(1L);
        variationCost.setSourceItemId(1L);
        variationCost.setCostDate(LocalDate.now());
        variationCost.setCostStatus("CONFIRMED");
        variationCost.setGeneratedFlag(1);
        costItemMapper.insert(variationCost);

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        CostManagerDashboardVO.SubjectRanking ranking = vo.getSubjectRankings().stream()
                .filter(item -> subject.getId().toString().equals(item.getCostSubjectId()))
                .findFirst()
                .orElseThrow();
        assertEquals("人工费", ranking.getCostSubjectName());
        assertEquals("30000.00", ranking.getActualCost());
        assertTrue(vo.getLedgerRows().stream()
                .anyMatch(row -> subject.getId().toString().equals(row.getCostSubjectId())
                        && "30000.00".equals(row.getActualAmount())));
    }

    @Test
    @Transactional
    @DisplayName("3.2 Cost view: null projectId returns tenant-wide cost")
    void testCostView_AllProjects() {
        seed("COST2");
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertNotNull(vo.getTargetCost());
        assertNotNull(vo.getDynamicCost());
        assertNotNull(vo.getTrendPoints());
        assertNotNull(vo.getSubjectRankings());
        assertNotNull(vo.getOverdueItems());
        assertNotNull(vo.getPendingPayments());
        assertNotNull(vo.getLedgerRows());
        assertTrue(vo.getLedgerTotal() >= 1L);
    }

    // ========================================================================
    // 4. Finance View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("4.1 Finance view: single project payment analysis")
    void testFinanceView_SingleProject() {
        SeedResult sr = seed("FIN1");
        FinanceDashboardVO vo = dashboardService.getFinanceView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getPendingPaymentAmount());
        assertNotNull(vo.getPendingPaymentCount());
        assertNotNull(vo.getApprovedUnpaidAmount());
        assertNotNull(vo.getOverRatioAmount());
        assertNotNull(vo.getWarrantyExpiringAmount());
        assertNotNull(vo.getPendingPayments());
    }

    @Test
    @Transactional
    @DisplayName("4.2 Finance view: null projectId returns tenant-wide finance")
    void testFinanceView_AllProjects() {
        seed("FIN2");
        FinanceDashboardVO vo = dashboardService.getFinanceView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertNotNull(vo.getPendingPaymentAmount());
    }

    // ========================================================================
    // 5. Management View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("5.1 Management view: returns project rankings and aggregates")
    void testManagementView() {
        seed("MGMT1");
        ManagementDashboardVO vo = dashboardService.getManagementView();

        assertNotNull(vo);
        assertTrue(vo.getActiveProjectCount() >= 1);
        assertNotNull(vo.getTotalContractAmount());
        assertNotNull(vo.getTotalDynamicCost());
        assertNotNull(vo.getTotalExpectedProfit());
        assertNotNull(vo.getTotalPaidAmount());
        assertNotNull(vo.getProjectRankings());
        assertTrue(vo.getProjectRankings().size() >= 1);

        // Rankings sorted by expectedProfit DESC
        List<DashboardProjectSummaryVO> rankings = vo.getProjectRankings();
        for (int i = 1; i < rankings.size(); i++) {
            BigDecimal prev = new BigDecimal(
                    rankings.get(i - 1).getExpectedProfit() != null
                            ? rankings.get(i - 1).getExpectedProfit() : "0");
            BigDecimal curr = new BigDecimal(
                    rankings.get(i).getExpectedProfit() != null
                            ? rankings.get(i).getExpectedProfit() : "0");
            assertTrue(prev.compareTo(curr) >= 0,
                    "Rankings should be sorted by expectedProfit DESC");
        }

        assertNotNull(vo.getOverdueItems());
        assertNotNull(vo.getMajorRisks());
        assertTrue(vo.getMajorRisks().size() >= 1);
        assertTrue(vo.getTotalPendingTaskCount() >= 1);
    }

    @Test
    @Transactional
    @DisplayName("5.2 Management view: graceful with existing projects")
    void testManagementView_Graceful() {
        seed("MGMT2");
        ManagementDashboardVO vo = dashboardService.getManagementView();
        assertNotNull(vo);
        assertNotNull(vo.getProjectRankings());
        assertNotNull(vo.getTotalContractAmount());
    }

    // ========================================================================
    // 6. Cost Breakdown
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("6.1 Cost breakdown: returns subject drill-down with level ≤ 2")
    void testCostBreakdown() {
        SeedResult sr = seed("BD1");
        CostBreakdownVO vo = dashboardService.getCostBreakdown(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getTargetCost());
        assertNotNull(vo.getDynamicCost());
        assertNotNull(vo.getExpectedProfit());
        assertNotNull(vo.getSubjectBreakdowns());
        assertTrue(vo.getSubjectBreakdowns().size() >= 1);

        for (CostBreakdownVO.SubjectBreakdown bd : vo.getSubjectBreakdowns()) {
            assertNotNull(bd.getLevel());
            assertTrue(bd.getLevel() <= 2, "Only level ≤ 2 subjects");
        }
    }

    @Test
    @Transactional
    @DisplayName("6.2 Cost breakdown: graceful empty summaries")
    void testCostBreakdown_EmptyGraceful() {
        SeedResult sr = seed("BD2");
        CostBreakdownVO vo = dashboardService.getCostBreakdown(sr.projectId);
        assertNotNull(vo);
        assertNotNull(vo.getSubjectBreakdowns());
    }

    @Test
    @Transactional
    @DisplayName("6.3 Cost breakdown: missing subject name falls back to empty")
    void testCostBreakdown_SubjectNameFallback() {
        SeedResult sr = seed("BD3");
        CostBreakdownVO vo = dashboardService.getCostBreakdown(sr.projectId);
        assertNotNull(vo);
        for (CostBreakdownVO.SubjectBreakdown bd : vo.getSubjectBreakdowns()) {
            assertNotNull(bd.getCostSubjectName(), "subjectName should not be null");
        }
    }

    // ========================================================================
    // 7. Edges
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("7.1 Paid ratio: format is N%")
    void testPaidRatio_Format() {
        SeedResult sr = seed("EDGE1");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(sr.projectId);
        assertNotNull(vo.getPaidRatio());
        assertTrue(vo.getPaidRatio().endsWith("%"), "paidRatio should end with %");
    }

    @Test
    @Transactional
    @DisplayName("7.2 Var order: only APPROVED amounts counted")
    void testVarOrder_OnlyApproved() {
        SeedResult sr = seed("EDGE2");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(sr.projectId);
        // BigDecimal.toPlainString() produces "100000.00"
        assertEquals("100000.00", vo.getVarOrderAmount());
    }

    @Test
    @Transactional
    @DisplayName("7.3 Lagging project: past plannedEndDate appears")
    void testLaggingProject_Present() {
        SeedResult sr = seed("EDGE3");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);
        boolean found = vo.getLaggingProjects().stream()
                .anyMatch(p -> sr.projectId.toString().equals(p.getProjectId()));
        assertTrue(found, "Seeded project should appear in lagging list");
    }

    // ========================================================================
    // 8. Month parameter tests
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("8.1 PM view: accepts month parameter and filters by WfTask.receivedAt / CtContract.endDate")
    void testPMView_WithMonthParameter() {
        SeedResult sr = seed("PM_MONTH");
        String currentMonth = LocalDate.now().toString().substring(0, 7); // yyyy-MM

        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId, currentMonth);

        assertNotNull(vo);
        assertTrue(vo.getPendingTaskCount() >= 1, "Should have tasks in current month");
        assertTrue(vo.getLaggingProjectCount() >= 1, "Lagging projects should NOT be filtered by month");

        // Test with month in the future — should filter out all date-scoped items
        String futureMonth = LocalDate.now().plusMonths(12).toString().substring(0, 7);
        ProjectManagerDashboardVO futureVo = dashboardService.getProjectManagerView(sr.projectId, futureMonth);
        assertEquals(0L, futureVo.getPendingTaskCount(), "Future month should have 0 pending tasks");
        assertEquals(0L, futureVo.getExpiringContractCount(), "Future month should have 0 expiring contracts");
        // Lagging projects are NOT month-filtered
        assertTrue(futureVo.getLaggingProjectCount() >= 1, "Lagging projects still visible even with month filter");
    }

    @Test
    @Transactional
    @DisplayName("8.1a PM view: invalid month returns data without 500")
    void testPMView_InvalidMonthDoesNotThrow() {
        SeedResult sr = seed("PM_BAD_MONTH");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId, "not-a-month");
        assertNotNull(vo);
        assertTrue(vo.getPendingTaskCount() >= 1, "Invalid month should be ignored, return full data");
    }

    @Test
    @Transactional
    @DisplayName("8.2 Purchase view: accepts month parameter and filters requests/orders/receipts")
    void testPurchaseView_WithMonthParameter() {
        SeedResult sr = seed("PUR_MONTH");
        String currentMonth = LocalDate.now().toString().substring(0, 7);

        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(sr.projectId, currentMonth);

        assertNotNull(vo);
        assertTrue(vo.getPendingRequestCount() >= 1, "Should have requests in current month");

        // Test future month filters all
        String futureMonth = LocalDate.now().plusMonths(12).toString().substring(0, 7);
        PurchaseManagerDashboardVO futureVo = dashboardService.getPurchaseManagerView(sr.projectId, futureMonth);
        assertEquals(0L, futureVo.getPendingRequestCount(), "Future month should have 0 purchase requests");
        assertEquals(0L, futureVo.getActiveOrderCount(), "Future month should have 0 active orders");
        assertEquals("0", futureVo.getTotalOrderAmount(), "Future month should have 0 order amount");
        // lowStock is NOT month-filtered (current inventory state)
        assertEquals(vo.getLowStockItemCount(), futureVo.getLowStockItemCount(), "LowStock should not be month-filtered");
    }

    @Test
    @Transactional
    @DisplayName("8.2a Purchase view: invalid month returns data without 500")
    void testPurchaseView_InvalidMonthDoesNotThrow() {
        SeedResult sr = seed("PUR_BAD_MONTH");
        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(sr.projectId, "garbage");
        assertNotNull(vo);
        assertTrue(vo.getActiveOrderCount() >= 1, "Invalid month should be ignored");
    }

    @Test
    @Transactional
    @DisplayName("8.2b Purchase view: overdueOrders uses deliveryDate scope NOT orderDate pre-filter")
    void testPurchaseView_OverdueUsesDeliveryDateScope() {
        SeedResult sr = seed("PUR_OVERDUE_DLV");
        String currentMonth = LocalDate.now().toString().substring(0, 7);
        String lastMonth = LocalDate.now().minusMonths(1).toString().substring(0, 7);

        // Order with orderDate=lastMonth, deliveryDate=currentMonth (overdue)
        MatPurchaseOrder crossOrder = new MatPurchaseOrder();
        crossOrder.setTenantId(TENANT_ID);
        crossOrder.setProjectId(sr.projectId);
        crossOrder.setPartnerId(sr.partnerId);
        crossOrder.setOrderCode("PO-CROSS-DLV");
        crossOrder.setOrderDate(LocalDate.now().minusMonths(1));         // last month
        crossOrder.setDeliveryDate(LocalDate.now().minusDays(1));        // current month, overdue
        crossOrder.setTotalAmount(new BigDecimal("50000.00"));
        crossOrder.setApprovalStatus("APPROVED");
        crossOrder.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(crossOrder);

        MatPurchaseOrderItem crossOrderItem = new MatPurchaseOrderItem();
        crossOrderItem.setTenantId(TENANT_ID);
        crossOrderItem.setProjectId(sr.projectId);
        crossOrderItem.setOrderId(crossOrder.getId());
        crossOrderItem.setMaterialId(sr.materialId);
        crossOrderItem.setMaterialName("钢筋-CROSS-DLV");
        crossOrderItem.setQuantity(new BigDecimal("5.0000"));
        crossOrderItem.setUnit("吨");
        crossOrderItem.setUnitPrice(new BigDecimal("10000.0000"));
        crossOrderItem.setAmount(new BigDecimal("50000.00"));
        purchaseOrderItemMapper.insert(crossOrderItem);

        // Query by lastMonth: only cross-order matches by orderDate
        PurchaseManagerDashboardVO lastMonthVo = dashboardService.getPurchaseManagerView(sr.projectId, lastMonth);
        assertTrue(lastMonthVo.getActiveOrderCount() >= 1, "Last month should include cross order by orderDate");
        assertTrue(lastMonthVo.getPurchaseOrders().stream()
                .anyMatch(o -> "PO-CROSS-DLV".equals(o.getCode())),
                "Cross order should appear in purchaseOrders when filtered by lastMonth");

        // Query by currentMonth: orderDate does NOT match → NOT in purchaseOrders
        PurchaseManagerDashboardVO currentMonthVo = dashboardService.getPurchaseManagerView(sr.projectId, currentMonth);
        assertTrue(currentMonthVo.getPurchaseOrders().stream()
                .noneMatch(o -> "PO-CROSS-DLV".equals(o.getCode())),
                "Cross order should NOT appear in purchaseOrders (orderDate not in current month)");

        // CRITICAL: deliveryDate IS in current month → MUST appear in overdueOrders
        assertTrue(currentMonthVo.getOverdueOrders().stream()
                .anyMatch(o -> "PO-CROSS-DLV".equals(o.getCode())),
                "Cross order MUST appear in overdueOrders because deliveryDate is in current month");
        assertTrue(currentMonthVo.getOverdueDeliveryCount() >= 1,
                "overdueDeliveryCount should count orders with deliveryDate in selected month");
    }

    @Test
    @Transactional
    @DisplayName("8.3 Production view: accepts month parameter and filters receipts/requisitions/measures")
    void testProductionView_WithMonthParameter() {
        SeedResult sr = seed("PROD_MONTH");
        String currentMonth = LocalDate.now().toString().substring(0, 7);

        ProductionManagerDashboardVO vo = dashboardService.getProductionManagerView(sr.projectId, currentMonth);

        assertNotNull(vo);
        assertTrue(vo.getReceiptCount() >= 1, "Should have receipts in current month");

        String futureMonth = LocalDate.now().plusMonths(12).toString().substring(0, 7);
        ProductionManagerDashboardVO futureVo = dashboardService.getProductionManagerView(sr.projectId, futureMonth);
        assertEquals(0L, futureVo.getReceiptCount(), "Future month should have 0 receipts");
        assertEquals(0L, futureVo.getRequisitionCount(), "Future month should have 0 requisitions");
        assertEquals(0L, futureVo.getSubMeasureCount(), "Future month should have 0 measures");
        assertEquals("0", futureVo.getConfirmedMeasureAmount(), "Future month should have 0 confirmed amount");
        // lowStock is NOT month-filtered
        assertEquals(vo.getLowStockItemCount(), futureVo.getLowStockItemCount(), "LowStock should not be month-filtered");
    }

    @Test
    @Transactional
    @DisplayName("8.3a Production view: invalid month returns data without 500")
    void testProductionView_InvalidMonthDoesNotThrow() {
        SeedResult sr = seed("PROD_BAD_MONTH");
        ProductionManagerDashboardVO vo = dashboardService.getProductionManagerView(sr.projectId, "invalid-month");
        assertNotNull(vo);
        assertTrue(vo.getReceiptCount() >= 1, "Invalid month should be ignored");
    }

    @Test
    @Transactional
    @DisplayName("8.4 Chief engineer view: accepts month parameter and filters by dueDate/discoveredAt")
    void testChiefEngineerView_WithMonthParameter() {
        SeedResult sr = seed("CHIEF_MONTH");
        String currentMonth = LocalDate.now().toString().substring(0, 7);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId, currentMonth);

        assertNotNull(vo);

        String futureMonth = LocalDate.now().plusMonths(12).toString().substring(0, 7);
        ChiefEngineerDashboardVO futureVo = dashboardService.getChiefEngineerView(sr.projectId, futureMonth);
        // Future month should have 0 items (no tech items seeded with future dates)
        assertEquals(0L, futureVo.getOpenIssueCount(), "Future month should have 0 tech items");
        assertEquals(0L, futureVo.getPendingReviewCount(), "Future month should have 0 reviews");
        assertEquals(0L, futureVo.getOverdueCount(), "Future month should have 0 overdue items");
    }

    @Test
    @Transactional
    @DisplayName("8.4a Chief engineer view: invalid month returns data without 500")
    void testChiefEngineerView_InvalidMonthDoesNotThrow() {
        SeedResult sr = seed("CHIEF_BAD_MONTH");
        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId, "not-valid");
        assertNotNull(vo);
    }

    @Test
    @Transactional
    @DisplayName("8.5 Cost view: invalid month returns data without 500")
    void testCostView_InvalidMonthDoesNotThrow() {
        SeedResult sr = seed("COST_BAD_MONTH");
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId, "bad-month");
        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
    }
}
