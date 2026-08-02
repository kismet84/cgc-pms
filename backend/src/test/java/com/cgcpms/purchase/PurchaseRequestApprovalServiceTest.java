package com.cgcpms.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.dto.PurchaseRequestApprovalCommand;
import com.cgcpms.purchase.dto.PurchaseRequestApprovalItemCommand;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.service.PurchaseRequestApprovalService;
import com.cgcpms.purchase.service.MatPurchaseRequestService;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseRequestApprovalServiceTest {
    @Mock WfTaskMapper taskMapper;
    @Mock MatPurchaseRequestItemMapper itemMapper;
    @Mock WorkflowEngine workflowEngine;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock MatPurchaseRequestService requestService;
    PurchaseRequestApprovalService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MatPurchaseRequestItem.class);
        TestUserContext.setAdmin(0L, 1L);
        service = new PurchaseRequestApprovalService(taskMapper, itemMapper, workflowEngine, jdbcTemplate, requestService);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void updatesApprovedQuantityWithCasAndWritesAuditBeforeDedicatedApproval() {
        WfTask task = task(100L, 200L);
        MatPurchaseRequestItem item = item(300L, 200L, "10", "10", 0);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(itemMapper.update(eq(null), any())).thenReturn(1);

        service.approve(200L, 100L, new PurchaseRequestApprovalCommand(
                "同意", "pr-approve-12345678",
                List.of(new PurchaseRequestApprovalItemCommand(
                        300L, new BigDecimal("8"), 0, "现场计划调整"))));

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
        verify(workflowEngine).approvePurchaseRequest(
                100L, 1L, "admin", "同意", "pr-approve-12345678");
    }

    @Test
    void rejectsIncompleteCoverageWithoutTouchingWorkflow() {
        when(taskMapper.selectById(100L)).thenReturn(task(100L, 200L));
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(300L, 200L, "10", "10", 0),
                item(301L, 200L, "5", "5", 0)));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.approve(200L, 100L, new PurchaseRequestApprovalCommand(
                        "同意", "pr-approve-12345678",
                        List.of(new PurchaseRequestApprovalItemCommand(
                                300L, new BigDecimal("8"), 0, "调整")))));

        assertEquals("PURCHASE_REQUEST_APPROVAL_ITEMS_INCOMPLETE", exception.getCode());
        verify(workflowEngine, never()).approvePurchaseRequest(
                anyLong(), anyLong(), anyString(), any(), anyString());
    }

    @Test
    void approvalItemsRequireCurrentPendingTaskOwnership() {
        when(taskMapper.selectById(100L)).thenReturn(task(100L, 200L));
        when(requestService.getItems(200L)).thenReturn(List.of());

        assertEquals(List.of(), service.getItemsForApproval(200L, 100L));
        verify(requestService).getItems(200L);

        WfTask otherRequestTask = task(101L, 201L);
        when(taskMapper.selectById(101L)).thenReturn(otherRequestTask);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getItemsForApproval(200L, 101L));
        assertEquals("PURCHASE_REQUEST_APPROVAL_TASK_MISMATCH", exception.getCode());
    }

    private WfTask task(Long id, Long requestId) {
        WfTask task = new WfTask();
        task.setId(id);
        task.setTenantId(0L);
        task.setInstanceId(50L);
        task.setBusinessType("PURCHASE_REQUEST");
        task.setBusinessId(requestId);
        task.setTaskStatus(WorkflowConstants.TASK_PENDING);
        task.setApproverId(1L);
        return task;
    }

    private MatPurchaseRequestItem item(Long id, Long requestId, String quantity,
                                        String approvedQuantity, int version) {
        MatPurchaseRequestItem item = new MatPurchaseRequestItem();
        item.setId(id);
        item.setTenantId(0L);
        item.setRequestId(requestId);
        item.setQuantity(new BigDecimal(quantity));
        item.setApprovedQuantity(new BigDecimal(approvedQuantity));
        item.setApprovalVersion(version);
        return item;
    }
}
