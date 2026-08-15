package com.cgcpms.requisition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.materialreturn.dto.MaterialReturnRequest;
import com.cgcpms.materialreturn.service.MaterialReturnService;
import com.cgcpms.procurement.service.ProcurementTraceService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class MatRequisitionWorkflowSubmitTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long APPROVAL_WAREHOUSE_ID = 93030001L;
    private static final long APPROVAL_MATERIAL_ID = 93030002L;
    private static final long SCHEDULE_ID = 93030003L;
    private static final long WBS_ID = 93030004L;
    private static final long PROJECT_MANAGER_APPROVER_ID = 93030005L;

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

    @Autowired private MatWarehouseMapper warehouseMapper;
    @Autowired private MdMaterialMapper materialMapper;
    @Autowired private MaterialReturnService materialReturnService;
    @Autowired private ProcurementTraceService traceService;

    @Autowired
    private CostItemMapper costItemMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("business:amount:view"))));
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) VALUES(?,0,'req-project-manager-approver','test','领料项目经理审批人','ENABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", PROJECT_MANAGER_APPROVER_ID);
        jdbc.update("INSERT INTO pm_project_member(id,tenant_id,project_id,user_id,role_code,status,created_at,updated_at,created_by,updated_by,deleted_flag) VALUES(93030007,0,?,?,'PROJECT_MANAGER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1,0)", PROJECT_ID, PROJECT_MANAGER_APPROVER_ID);
        jdbc.update("INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,'REQ-WBS-SP','领料测试基线','BASELINE',9303,'2026-01-01','2026-12-31','ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", SCHEDULE_ID, PROJECT_ID);
        jdbc.update("INSERT INTO project_wbs_task(id,tenant_id,project_id,schedule_plan_id,task_code,task_name,planned_start_date,planned_end_date,weight_percent,actual_quantity,actual_progress,status,sort_order,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,'REQ-WBS','领料测试WBS','2026-01-01','2026-12-31',100,0,0,'NOT_STARTED',1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", WBS_ID, PROJECT_ID, SCHEDULE_ID);
    }

    @Test
    @Transactional
    void formOptionsIncludeActiveWbsTasks() {
        Map<String, Object> options = requisitionService.formOptions(PROJECT_ID);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) options.get("wbsTasks");
        assertTrue(tasks.stream().anyMatch(task ->
                ((Number) task.get("id")).longValue() == WBS_ID
                        && "REQ-WBS".equals(task.get("taskCode"))
                        && "领料测试WBS".equals(task.get("taskName"))));
        assertEquals(List.of(), options.get("materials"));
    }

    @Test
    @Transactional
    void materialOptionsAreProjectWarehouseScopedPositiveStockAndBounded() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setId(APPROVAL_WAREHOUSE_ID);
        warehouse.setTenantId(TENANT_ID);
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-REQ-OPTIONS");
        warehouse.setWarehouseName("领料候选仓");
        warehouse.setStatus("ENABLE");
        warehouseMapper.insert(warehouse);

        MdMaterial available = new MdMaterial();
        available.setId(APPROVAL_MATERIAL_ID);
        available.setTenantId(TENANT_ID);
        available.setMaterialCode("MAT-REQ-AVAILABLE");
        available.setMaterialName("领料候选有库存物料");
        available.setSpecification("A型");
        available.setUnit("件");
        available.setStatus("ENABLE");
        materialMapper.insert(available);
        MdMaterial empty = new MdMaterial();
        empty.setId(APPROVAL_MATERIAL_ID + 1);
        empty.setTenantId(TENANT_ID);
        empty.setMaterialCode("MAT-REQ-EMPTY");
        empty.setMaterialName("领料候选零库存物料");
        empty.setUnit("件");
        empty.setStatus("ENABLE");
        materialMapper.insert(empty);

        MatStock availableStock = new MatStock();
        availableStock.setTenantId(TENANT_ID);
        availableStock.setWarehouseId(APPROVAL_WAREHOUSE_ID);
        availableStock.setMaterialId(APPROVAL_MATERIAL_ID);
        availableStock.setAvailableQty(BigDecimal.TEN);
        availableStock.setInventoryValue(BigDecimal.TEN);
        availableStock.setAverageUnitCost(BigDecimal.ONE);
        availableStock.setVersion(0);
        matStockMapper.insert(availableStock);
        MatStock emptyStock = new MatStock();
        emptyStock.setTenantId(TENANT_ID);
        emptyStock.setWarehouseId(APPROVAL_WAREHOUSE_ID);
        emptyStock.setMaterialId(APPROVAL_MATERIAL_ID + 1);
        emptyStock.setAvailableQty(BigDecimal.ZERO);
        emptyStock.setInventoryValue(BigDecimal.ZERO);
        emptyStock.setAverageUnitCost(BigDecimal.ZERO);
        emptyStock.setVersion(0);
        matStockMapper.insert(emptyStock);

        List<Map<String, Object>> options = requisitionService.materialOptions(
                PROJECT_ID, APPROVAL_WAREHOUSE_ID, "候选");
        assertEquals(1, options.size());
        assertEquals(APPROVAL_MATERIAL_ID, ((Number) options.getFirst().get("id")).longValue());
        assertEquals(List.of(), requisitionService.materialOptions(
                PROJECT_ID, APPROVAL_WAREHOUSE_ID, "不存在的物料"));

        BusinessException invalidWarehouse = assertThrows(BusinessException.class,
                () -> requisitionService.materialOptions(PROJECT_ID, APPROVAL_WAREHOUSE_ID + 99, ""));
        assertEquals("REQUISITION_WAREHOUSE_INVALID", invalidWarehouse.getCode());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
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
        item.setWbsTaskId(WBS_ID);
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
    @DisplayName("M2: 领料审批与仓管实际出库分离，出库逐行幂等且不重复确认材料成本")
    void approvedRequisitionRequiresExplicitStockOut() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setId(APPROVAL_WAREHOUSE_ID);
        warehouse.setTenantId(TENANT_ID);
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-REQ-TEST");
        warehouse.setWarehouseName("领料闭环测试仓");
        warehouse.setStatus("ENABLE");
        warehouseMapper.insert(warehouse);

        MdMaterial material = new MdMaterial();
        material.setId(APPROVAL_MATERIAL_ID);
        material.setTenantId(TENANT_ID);
        material.setMaterialCode("MAT-REQ-TEST");
        material.setMaterialName("领料闭环测试物料");
        material.setUnit("件");
        material.setStatus("ENABLE");
        materialMapper.insert(material);

        MatRequisition requisition = new MatRequisition();
        requisition.setProjectId(PROJECT_ID);
        requisition.setContractId(CONTRACT_ID);
        requisition.setRequisitionDate(LocalDate.of(2026, 7, 6));
        requisition.setWarehouseId(APPROVAL_WAREHOUSE_ID);
        Long requisitionId = requisitionService.create(requisition);

        MatRequisitionItem item = new MatRequisitionItem();
        item.setWbsTaskId(WBS_ID);
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
        stock.setInventoryValue(new BigDecimal("250.00"));
        stock.setAverageUnitCost(new BigDecimal("12.500000"));
        stock.setVersion(0);
        matStockMapper.insert(stock);

        WfInstance instance = new WfInstance();
        instance.setBusinessType(WorkflowBusinessTypes.MATERIAL_REQUISITION);
        instance.setBusinessId(requisitionId);
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);

        MatRequisition approving = requisitionMapper.selectById(requisitionId);
        approving.setApprovalStatus("APPROVING");
        requisitionMapper.updateById(approving);
        requisitionWorkflowHandler.onApproved(context);

        MatRequisition approved = requisitionMapper.selectById(requisitionId);
        assertEquals("APPROVED", approved.getApprovalStatus());
        assertEquals(0, approved.getStockOutFlag());
        assertEquals(0, new BigDecimal("20.00")
                .compareTo(matStockMapper.selectById(stock.getId()).getAvailableQty()));
        assertEquals(0L, matStockTxnMapper.selectCount(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getSourceType, "MAT_REQUISITION")
                .eq(MatStockTxn::getSourceId, requisitionId)));
        assertNull(costItemMapper.selectOne(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getSourceType, "MAT_REQUISITION")
                .eq(CostItem::getSourceId, requisitionId)),
                "审批只授权，不得提前确认材料成本");

        requisitionService.executeStockOut(requisitionId);

        MatStock afterStock = matStockMapper.selectById(stock.getId());
        assertEquals(0, new BigDecimal("12.00").compareTo(afterStock.getAvailableQty()));
        assertEquals(0, new BigDecimal("150.00").compareTo(afterStock.getInventoryValue()));
        MatRequisition issued = requisitionMapper.selectById(requisitionId);
        assertEquals(1, issued.getStockOutFlag());
        assertEquals(USER_ADMIN, issued.getStockOutBy());
        assertNotNull(issued.getStockOutAt());

        MatStockTxn txn = matStockTxnMapper.selectOne(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getWarehouseId, APPROVAL_WAREHOUSE_ID)
                .eq(MatStockTxn::getMaterialId, APPROVAL_MATERIAL_ID)
                .eq(MatStockTxn::getTxnType, "OUT")
                .eq(MatStockTxn::getSourceType, "MAT_REQUISITION")
                .eq(MatStockTxn::getSourceId, requisitionId)
                .eq(MatStockTxn::getSourceLineId, item.getId()));
        assertNotNull(txn, "仓管实际出库后应生成逐行来源追溯的出库流水");
        assertEquals(0, new BigDecimal("8.00").compareTo(txn.getQuantity()));
        assertEquals(0, new BigDecimal("12.00").compareTo(txn.getAvailableAfter()));
        assertEquals(0, new BigDecimal("12.500000").compareTo(txn.getUnitCost()));
        assertEquals(0, new BigDecimal("100.00").compareTo(txn.getAmount()));
        assertEquals(WBS_ID, txn.getWbsTaskId());

        CostItem cost = costItemMapper.selectOne(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getSourceType, "MAT_REQUISITION")
                .eq(CostItem::getSourceId, requisitionId));
        assertNotNull(cost, "库存材料只在仓管实际出库时确认项目材料成本");
        assertEquals(PROJECT_ID, cost.getProjectId());
        assertEquals(CONTRACT_ID, cost.getContractId());
        assertEquals("MATERIAL", cost.getCostType());
        assertEquals("CONFIRMED", cost.getCostStatus());
        assertEquals(WBS_ID, cost.getWbsTaskId());
        assertEquals(0, new BigDecimal("100.00").compareTo(cost.getAmount()));

        requisitionService.executeStockOut(requisitionId);
        assertEquals(0, new BigDecimal("12.00")
                .compareTo(matStockMapper.selectById(stock.getId()).getAvailableQty()));
        assertEquals(1L, matStockTxnMapper.selectCount(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getSourceType, "MAT_REQUISITION")
                .eq(MatStockTxn::getSourceId, requisitionId)
                .eq(MatStockTxn::getSourceLineId, item.getId())));

        MaterialReturnRequest returnRequest = new MaterialReturnRequest(
                item.getId(), txn.getId(), new BigDecimal("3.0000"),
                LocalDate.of(2026, 7, 7), "现场余料退回", "RETURN-TEST-001");
        Long returnId = materialReturnService.confirm(returnRequest);
        assertEquals(returnId, materialReturnService.confirm(returnRequest), "同一幂等键重复提交应返回原退料单");
        MatStock afterReturn = matStockMapper.selectById(stock.getId());
        assertEquals(0, new BigDecimal("15.0000").compareTo(afterReturn.getAvailableQty()));
        assertEquals(0, new BigDecimal("187.50").compareTo(afterReturn.getInventoryValue()));
        CostItem reversal = costItemMapper.selectOne(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getSourceType, "MATERIAL_RETURN")
                .eq(CostItem::getSourceId, returnId));
        assertNotNull(reversal);
        assertEquals(0, new BigDecimal("-37.50").compareTo(reversal.getAmount()));
        assertEquals(1L, matStockTxnMapper.selectCount(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getSourceType, "MATERIAL_RETURN")
                .eq(MatStockTxn::getSourceId, returnId)));
        var returnTrace = traceService.byMaterialReturn(returnId);
        assertEquals(requisitionId, returnTrace.getRequisition().getId());
        assertEquals(returnId, returnTrace.getMaterialReturn().getId());
        assertEquals(2, returnTrace.getCosts().size(), "Trace应同时返回原出库成本和退料冲销成本");

        BusinessException excessiveReturn = assertThrows(BusinessException.class,
                () -> materialReturnService.confirm(new MaterialReturnRequest(
                        item.getId(), txn.getId(), new BigDecimal("6.0000"),
                        LocalDate.of(2026, 7, 8), "超量退料", "RETURN-TEST-002")));
        assertEquals("RETURN_EXCEEDS_ISSUED", excessiveReturn.getCode());

        assertEquals(returnId, materialReturnService.reverse(returnId, "退料录入错误"));
        assertEquals(returnId, materialReturnService.reverse(returnId, "重复冲销保持幂等"));
        assertEquals("REVERSED", materialReturnService.getById(returnId).getStatus());
        MatStock afterReversal = matStockMapper.selectById(stock.getId());
        assertEquals(0, new BigDecimal("12.0000").compareTo(afterReversal.getAvailableQty()));
        assertEquals(0, new BigDecimal("150.00").compareTo(afterReversal.getInventoryValue()));
        CostItem reversalUndo = costItemMapper.selectOne(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getSourceType, "MATERIAL_RETURN_REVERSAL")
                .eq(CostItem::getSourceId, returnId));
        assertNotNull(reversalUndo);
        assertEquals(0, new BigDecimal("37.50").compareTo(reversalUndo.getAmount()));

        Long fullReturnId = materialReturnService.confirm(new MaterialReturnRequest(
                item.getId(), txn.getId(), new BigDecimal("8.0000"),
                LocalDate.of(2026, 7, 9), "冲销后重新全量退料", "RETURN-TEST-003"));
        assertNotNull(fullReturnId, "已冲销退料不应继续占用累计可退数量");
        assertEquals(0, new BigDecimal("20.0000")
                .compareTo(matStockMapper.selectById(stock.getId()).getAvailableQty()));
    }
}
