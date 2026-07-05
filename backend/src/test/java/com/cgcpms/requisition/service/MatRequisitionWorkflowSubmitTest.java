package com.cgcpms.requisition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.requisition.vo.MatRequisitionVO;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
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

    @Autowired
    private MatRequisitionService requisitionService;

    @Autowired
    private MatRequisitionMapper requisitionMapper;

    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    @Autowired
    private WfTaskMapper wfTaskMapper;

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
}
