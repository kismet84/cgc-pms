package com.cgcpms.requisition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.requisition.handler.MaterialRequisitionWorkflowHandler;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.requisition.vo.MatRequisitionVO;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class MatRequisitionWorkflowSubmitTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long APPROVAL_WAREHOUSE_ID = 93030001L;
    private static final long APPROVAL_MATERIAL_ID = 93030002L;

    @Autowired
    private MatRequisitionService requisitionService;

    @Autowired
    private MatRequisitionMapper requisitionMapper;

    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    @Autowired
    private WfTaskMapper wfTaskMapper;

    @Autowired
    private MaterialRequisitionWorkflowHandler requisitionWorkflowHandler;

    @Autowired
    private MatStockMapper matStockMapper;

    @Autowired
    private MatStockTxnMapper matStockTxnMapper;

    @Autowired
    private CostItemMapper costItemMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @Transactional
    @DisplayName("submitForApproval -> MATERIAL_REQUISITION 模板存在时进入 APPROVING 并生成待办")
    void submitForApprovalCreatesRunningWorkflow() {
        MatRequisition requisition = new MatRequisition();
        requisition.setProjectId(PROJECT_ID);
        requisition.setContractId(CONTRACT_ID);
        requisition.setRequisitionDate(LocalDate.of(2026, 7, 5));
        requisition.setWarehouseId(1L);
        Long requisitionId = requisitionService.create(requisition);

        MatRequisitionItem item = new MatRequisitionItem();
        item.setMaterialId(1L);
        item.setQuantity(new BigDecimal("8.00"));
        item.setUnitPrice(new BigDecimal("12.50"));
        item.setAmount(new BigDecimal("100.00"));
        requisitionService.saveItemsBatch(requisitionId, List.of(item));

        requisitionService.submitForApproval(requisitionId);

        MatRequisitionVO vo = requisitionService.getById(requisitionId);
        assertEquals("APPROVING", vo.getApprovalStatus());

        MatRequisition persisted = requisitionMapper.selectById(requisitionId);
        assertEquals("APPROVING", persisted.getApprovalStatus());

        WfInstance instance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, WorkflowBusinessTypes.MATERIAL_REQUISITION)
                .eq(WfInstance::getBusinessId, requisitionId));
        assertNotNull(instance, "提交后应生成审批实例");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, instance.getInstanceStatus());

        long pendingTasks = wfTaskMapper.selectCount(new LambdaQueryWrapper<com.cgcpms.workflow.entity.WfTask>()
                .eq(com.cgcpms.workflow.entity.WfTask::getInstanceId, instance.getId())
                .eq(com.cgcpms.workflow.entity.WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        assertTrue(pendingTasks > 0, "提交后应生成 PENDING 待办");
    }

    @Test
    @Transactional
    @DisplayName("M2: 领料审批通过后写 stockOutFlag 并生成可追溯出库流水")
    void approvedRequisitionCreatesStockOutLedger() {
        MatRequisition requisition = new MatRequisition();
        requisition.setProjectId(PROJECT_ID);
        requisition.setContractId(CONTRACT_ID);
        requisition.setRequisitionDate(LocalDate.of(2026, 7, 6));
        requisition.setWarehouseId(APPROVAL_WAREHOUSE_ID);
        Long requisitionId = requisitionService.create(requisition);

        MatRequisitionItem item = new MatRequisitionItem();
        item.setMaterialId(APPROVAL_MATERIAL_ID);
        item.setQuantity(new BigDecimal("8.00"));
        item.setUnitPrice(new BigDecimal("12.50"));
        item.setAmount(new BigDecimal("100.00"));
        requisitionService.saveItemsBatch(requisitionId, List.of(item));

        MatStock stock = new MatStock();
        stock.setTenantId(TENANT_ID);
        stock.setWarehouseId(APPROVAL_WAREHOUSE_ID);
        stock.setMaterialId(APPROVAL_MATERIAL_ID);
        stock.setAvailableQty(new BigDecimal("20.00"));
        stock.setVersion(0);
        matStockMapper.insert(stock);

        WfInstance instance = new WfInstance();
        instance.setBusinessType(WorkflowBusinessTypes.MATERIAL_REQUISITION);
        instance.setBusinessId(requisitionId);
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);

        requisitionWorkflowHandler.onApproved(context);

        MatRequisition approved = requisitionMapper.selectById(requisitionId);
        assertEquals("APPROVED", approved.getApprovalStatus());
        assertEquals(1, approved.getStockOutFlag());

        MatStock afterStock = matStockMapper.selectById(stock.getId());
        assertEquals(0, new BigDecimal("12.00").compareTo(afterStock.getAvailableQty()));

        MatStockTxn txn = matStockTxnMapper.selectOne(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getWarehouseId, APPROVAL_WAREHOUSE_ID)
                .eq(MatStockTxn::getMaterialId, APPROVAL_MATERIAL_ID)
                .eq(MatStockTxn::getTxnType, "OUT")
                .eq(MatStockTxn::getSourceType, "MAT_REQUISITION")
                .eq(MatStockTxn::getSourceId, requisitionId));
        assertNotNull(txn, "领料审批通过后应生成带来源追溯的出库流水");
        assertEquals(0, new BigDecimal("8.00").compareTo(txn.getQuantity()));
        assertEquals(0, new BigDecimal("12.00").compareTo(txn.getAvailableAfter()));

        CostItem cost = costItemMapper.selectOne(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getSourceType, "MAT_REQUISITION")
                .eq(CostItem::getSourceId, requisitionId));
        assertNotNull(cost, "领料审批通过后应生成项目材料成本");
        assertEquals(PROJECT_ID, cost.getProjectId());
        assertEquals(CONTRACT_ID, cost.getContractId());
        assertEquals("MATERIAL", cost.getCostType());
        assertEquals("CONFIRMED", cost.getCostStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(cost.getAmount()));
    }
}
