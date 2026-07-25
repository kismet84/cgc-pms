package com.cgcpms.subcontract.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.subcontract.service.SubMeasureService;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("SubMeasureWorkflowHandler — approval lifecycle tests")
class SubMeasureWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private SubMeasureWorkflowHandler handler;

    @Autowired
    private SubMeasureMapper subMeasureMapper;

    @Autowired private CostItemMapper costItemMapper;
    @Autowired private SubMeasureService subMeasureService;
    @Autowired private SubTaskMapper subTaskMapper;
    @Autowired private CtContractItemMapper contractItemMapper;
    @Autowired private SysFileMapper fileMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("supportBusinessType -> SUB_MEASURE")
    void testSupportBusinessType() {
        assertEquals("SUB_MEASURE", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical(), "分包计量审批处理器应标记为关键");
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> APPROVING→APPROVED")
    void testOnApproved_Success() {
        SubMeasure measure = createApprovalReadyMeasure(List.of(new BigDecimal("100.00")));

        WfInstance instance = new WfInstance();
        instance.setBusinessId(measure.getId());
        instance.setId(2100001L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        SubMeasure updated = subMeasureMapper.selectById(measure.getId());
        assertNotNull(updated, "分包计量应仍然存在");
        assertEquals("APPROVED", updated.getApprovalStatus(), "审批状态应变为 APPROVED");
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> 成本明细按净计量额分摊且金额守恒")
    void testOnApproved_AllocatesNetMeasureCost() {
        SubMeasure measure = createApprovalReadyMeasure(
                List.of(new BigDecimal("60.00"), new BigDecimal("40.00")));
        measure.setApprovedAmount(new BigDecimal("95.00"));
        measure.setDeductionAmount(new BigDecimal("5.00"));
        measure.setNetAmount(new BigDecimal("90.00"));
        subMeasureMapper.updateById(measure);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(measure.getId());
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);
        handler.onApproved(context);

        List<CostItem> costs = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_0)
                .eq(CostItem::getSourceType, "SUB_MEASURE")
                .eq(CostItem::getSourceId, measure.getId()));
        assertEquals(2, costs.size());
        BigDecimal total = costs.stream().map(CostItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("90.00").compareTo(total));
        assertTrue(costs.stream().anyMatch(cost -> new BigDecimal("54.00").compareTo(cost.getAmount()) == 0));
        assertTrue(costs.stream().anyMatch(cost -> new BigDecimal("36.00").compareTo(cost.getAmount()) == 0));
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessIdGuard() {
        WfInstance instance = new WfInstance();
        instance.setId(2100002L);
        instance.setBusinessId(null);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        assertThrows(IllegalStateException.class, () -> handler.onApproved(ctx),
                "businessId 为 null 时应抛出 IllegalStateException");
    }

    @Test
    @Transactional
    @DisplayName("onRejected -> APPROVING→REJECTED")
    void testOnRejected() {
        SubMeasure measure = new SubMeasure();
        measure.setProjectId(10001L);
        measure.setContractId(30001L);
        measure.setPartnerId(50001L);
        measure.setMeasureCode("SM-HDLR-REJ-" + System.nanoTime());
        measure.setApprovalStatus("APPROVING");
        measure.setStatus("APPROVING");
        measure.setTenantId(0L);
        subMeasureMapper.insert(measure);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(measure.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);

        SubMeasure updated = subMeasureMapper.selectById(measure.getId());
        assertEquals("REJECTED", updated.getApprovalStatus(), "审批状态应变为 REJECTED");
        assertEquals("REJECTED", updated.getStatus(), "业务状态应同步变为 REJECTED");
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> APPROVING→DRAFT")
    void testOnWithdrawn() {
        SubMeasure measure = new SubMeasure();
        measure.setProjectId(10001L);
        measure.setContractId(30001L);
        measure.setPartnerId(50001L);
        measure.setMeasureCode("SM-HDLR-WTH-" + System.nanoTime());
        measure.setApprovalStatus("APPROVING");
        measure.setStatus("APPROVING");
        measure.setTenantId(0L);
        subMeasureMapper.insert(measure);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(measure.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);

        SubMeasure updated = subMeasureMapper.selectById(measure.getId());
        assertEquals("DRAFT", updated.getApprovalStatus(), "撤回后审批状态应变为 DRAFT");
        assertEquals("DRAFT", updated.getStatus(), "撤回后业务状态应同步恢复为 DRAFT");
    }

    @Test
    @Transactional
    @DisplayName("workflow callback rejects stale state")
    void testCallbackRejectsStaleState() {
        SubMeasure measure = new SubMeasure();
        measure.setProjectId(10001L);
        measure.setContractId(30001L);
        measure.setPartnerId(20002L);
        measure.setMeasureCode("SM-HDLR-STALE-" + System.nanoTime());
        measure.setApprovalStatus("APPROVED");
        measure.setStatus("CONFIRMED");
        measure.setTenantId(TENANT_0);
        subMeasureMapper.insert(measure);
        WfInstance instance = new WfInstance();
        instance.setBusinessId(measure.getId());
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);

        BusinessException error = assertThrows(BusinessException.class,
                () -> handler.onRejected(context));
        assertEquals("SUB_MEASURE_WORKFLOW_STATE_INVALID", error.getCode());
    }

    private SubMeasure createApprovalReadyMeasure(List<BigDecimal> amounts) {
        SubTask task = new SubTask();
        task.setTenantId(TENANT_0);
        task.setProjectId(10001L);
        task.setContractId(30001L);
        task.setPartnerId(20002L);
        task.setTaskCode("HDLR-TASK-" + System.nanoTime());
        task.setTaskName("审批回调测试任务");
        task.setStatus("IN_PROGRESS");
        subTaskMapper.insert(task);

        SubMeasure measure = new SubMeasure();
        measure.setProjectId(10001L);
        measure.setContractId(30001L);
        measure.setPartnerId(20002L);
        measure.setSubTaskId(task.getId());
        measure.setMeasurePeriod("2026-07");
        measure.setMeasureDate(LocalDate.now());
        Long measureId = subMeasureService.create(measure);

        List<SubMeasureItem> commands = amounts.stream().map(amount -> {
            CtContractItem contractItem = new CtContractItem();
            contractItem.setTenantId(TENANT_0);
            contractItem.setContractId(30001L);
            contractItem.setItemCode("HDLR-ITEM-" + System.nanoTime());
            contractItem.setItemName("成本分摊项");
            contractItem.setUnit("项");
            contractItem.setQuantity(amount);
            contractItem.setUnitPrice(BigDecimal.ONE);
            contractItem.setAmount(amount);
            contractItemMapper.insert(contractItem);
            SubMeasureItem command = new SubMeasureItem();
            command.setContractItemId(contractItem.getId());
            command.setCurrentQuantity(amount);
            return command;
        }).toList();
        subMeasureService.saveItems(measureId, commands);

        SysFile file = new SysFile();
        file.setTenantId(TENANT_0);
        file.setBusinessType("SUBCONTRACT");
        file.setBusinessId(measureId);
        file.setDocumentType("OTHER");
        file.setFileName("handler-proof.pdf");
        file.setOriginalName("审批依据.pdf");
        file.setFileSize(10L);
        file.setContentType("application/pdf");
        file.setStoragePath("SUBCONTRACT/" + measureId + "/handler-proof.pdf");
        file.setBucketName("test");
        file.setVirusScanStatus("CLEAN");
        fileMapper.insert(file);

        SubMeasure ready = subMeasureMapper.selectById(measureId);
        ready.setApprovalStatus("APPROVING");
        ready.setStatus("APPROVING");
        subMeasureMapper.updateById(ready);
        return ready;
    }
}
