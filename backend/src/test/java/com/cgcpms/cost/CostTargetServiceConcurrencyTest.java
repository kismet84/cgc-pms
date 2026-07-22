package com.cgcpms.cost;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.mapper.CostTargetItemMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.cost.service.CostTargetService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.entity.WfInstance;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostTargetServiceConcurrencyTest {

    @Mock CostTargetMapper targetMapper;
    @Mock CostSummaryMapper summaryMapper;
    @Mock CostTargetItemMapper itemMapper;
    @Mock PmProjectMapper projectMapper;
    @Mock ProjectAccessChecker accessChecker;
    @Mock JdbcTemplate jdbc;
    @Mock ObjectProvider<WorkflowEngine> workflowEngine;

    private CostTargetService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        initTableInfo(CostTarget.class);
        initTableInfo(PmProject.class);
        service = new CostTargetService(targetMapper, summaryMapper, itemMapper, projectMapper,
                accessChecker, jdbc, workflowEngine);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void staleUpdateFailsBeforeWrite() {
        CostTarget current = target(1L, 4, "DRAFT");
        when(targetMapper.selectById(1L)).thenReturn(current);
        CostTarget command = target(1L, 3, "DRAFT");

        assertConcurrent(() -> service.update(command));
        verify(targetMapper, never()).updateById(any(CostTarget.class));
    }

    @Test
    void activateSerializesOnProjectAndFailsClosedWhenConditionalWriteLosesRace() {
        CostTarget target = target(2L, 7, "APPROVED");
        PmProject project = new PmProject();
        project.setId(10001L);
        project.setTenantId(TestUserContext.TENANT_0);
        project.setStatus("ACTIVE");
        when(targetMapper.selectById(2L)).thenReturn(target);
        when(projectMapper.selectOne(any())).thenReturn(project);
        when(targetMapper.selectOne(any())).thenReturn(target);
        when(targetMapper.update(isNull(), any())).thenReturn(1, 0);

        assertConcurrent(() -> service.activate(2L, 7));
        verify(projectMapper).selectOne(any());
    }

    @Test
    void rejectedTargetUsesExistingWorkflowInstanceWhenResubmittedAfterEdit() {
        CostTarget target = target(3L, 5, "REJECTED");
        target.setApprovalInstanceId(9001L);
        target.setTotalBidCostAmount(new BigDecimal("10.00"));
        target.setTotalTargetAmount(new BigDecimal("10.00"));
        target.setTotalResponsibilityAmount(new BigDecimal("10.00"));
        CostTargetItem item = new CostTargetItem();
        item.setCostSubjectId(101L);
        item.setResponsibleUserId(1L);
        item.setBidCostAmount(new BigDecimal("10.00"));
        item.setTargetAmount(new BigDecimal("10.00"));
        item.setResponsibilityAmount(new BigDecimal("10.00"));
        PmProject project = new PmProject();
        project.setId(10001L);
        project.setTenantId(TestUserContext.TENANT_0);
        project.setStatus("ACTIVE");
        WfInstance resubmitted = new WfInstance();
        resubmitted.setId(9001L);
        WorkflowEngine engine = org.mockito.Mockito.mock(WorkflowEngine.class);
        when(targetMapper.selectOne(any())).thenReturn(target);
        when(targetMapper.selectById(3L)).thenReturn(target);
        when(projectMapper.selectById(10001L)).thenReturn(project);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(jdbc.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(workflowEngine.getObject()).thenReturn(engine);
        when(engine.resubmitCostTarget(9001L, TestUserContext.USER_ADMIN, "admin")).thenReturn(resubmitted);
        when(targetMapper.update(isNull(), any())).thenReturn(1);

        service.submitForApproval(3L, 5);

        verify(engine).resubmitCostTarget(9001L, TestUserContext.USER_ADMIN, "admin");
    }

    private CostTarget target(Long id, int version, String approvalStatus) {
        CostTarget target = new CostTarget();
        target.setId(id);
        target.setTenantId(TestUserContext.TENANT_0);
        target.setProjectId(10001L);
        target.setVersion(version);
        target.setApprovalStatus(approvalStatus);
        target.setIsActive(0);
        target.setVersionNo("V" + id);
        target.setVersionName("测试版本");
        target.setTotalTargetAmount(java.math.BigDecimal.ZERO);
        return target;
    }

    private void assertConcurrent(Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals("COST_TARGET_CONCURRENT_UPDATE", error.getCode());
    }

    private static void initTableInfo(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) != null) return;
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("CostTargetServiceConcurrencyTest." + type.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, type);
    }
}
