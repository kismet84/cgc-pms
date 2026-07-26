package com.cgcpms.requisition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.mapper.MatRequisitionItemMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatRequisitionAccessScopeTest {

    private final MatRequisitionMapper requisitionMapper = mock(MatRequisitionMapper.class);
    private final ProjectAccessChecker projectAccessChecker = mock(ProjectAccessChecker.class);
    private final MatRequisitionAssembler assembler = mock(MatRequisitionAssembler.class);
    private final MatRequisitionService service = new MatRequisitionService(
            requisitionMapper,
            mock(MatRequisitionItemMapper.class),
            mock(CtContractMapper.class),
            mock(MatWarehouseMapper.class),
            mock(MdMaterialMapper.class),
            mock(MatStockService.class),
            mock(CostGenerationService.class),
            projectAccessChecker,
            mock(WorkflowEngine.class),
            assembler);

    @BeforeAll
    static void initializeLambdaMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MatRequisition.class);
    }

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims()
                .add("userId", 77L)
                .add("username", "outsider")
                .add("tenantId", 7L)
                .add("roleCodes", List.of())
                .build());
        when(assembler.assembleBatch(anyList())).thenReturn(List.of());
        when(requisitionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void outsiderListIsRestrictedToAccessibleProjects() {
        when(projectAccessChecker.accessibleProjectIds()).thenReturn(List.of(11L));

        service.getPage(1, 10, null, null, null, null, null, null, null);

        ArgumentCaptor<LambdaQueryWrapper<MatRequisition>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(requisitionMapper).selectPage(any(Page.class), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("project_id IN"));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(11L));
        verify(projectAccessChecker).accessibleProjectIds();
        verify(projectAccessChecker, never()).checkAccess(anyLong(), anyString());
    }

    @Test
    void listReturnsEmptyWhenUserHasNoAccessibleProject() {
        when(projectAccessChecker.accessibleProjectIds()).thenReturn(List.of());

        var result = service.getPage(1, 10, null, null, null, null, null, null, null);

        assertEquals(0, result.getTotal());
        assertEquals(List.of(), result.getRecords());
        verifyNoInteractions(requisitionMapper);
    }

    @Test
    void explicitUnauthorizedProjectFailsBeforeQuery() {
        doThrow(new BusinessException("PROJECT_ACCESS_DENIED", "无权访问项目"))
                .when(projectAccessChecker).checkAccess(12L, "查询领料申请");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getPage(1, 10, 12L, null, null, null, null, null, null));

        assertEquals("PROJECT_ACCESS_DENIED", error.getCode());
        verifyNoInteractions(requisitionMapper);
    }

    @Test
    void reportPeriodUsesInclusiveServerSideDateBoundaries() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(projectAccessChecker.accessibleProjectIds()).thenReturn(List.of(11L));

        service.getPage(1, 10, null, null, null, null, null, from, to);

        ArgumentCaptor<LambdaQueryWrapper<MatRequisition>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(requisitionMapper).selectPage(any(Page.class), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("requisition_date >="));
        assertTrue(wrapper.getValue().getSqlSegment().contains("requisition_date <="));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(from));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(to));
    }

    @Test
    void updateChecksTargetProjectBeforePersisting() {
        MatRequisition existing = new MatRequisition();
        existing.setId(101L);
        existing.setTenantId(7L);
        existing.setProjectId(11L);
        existing.setApprovalStatus("DRAFT");
        existing.setStockOutFlag(0);
        when(requisitionMapper.selectById(101L)).thenReturn(existing);
        doThrow(new BusinessException("PROJECT_ACCESS_DENIED", "无权访问项目"))
                .when(projectAccessChecker).checkAccess(12L, "编辑领料申请");

        MatRequisition update = new MatRequisition();
        update.setId(101L);
        update.setProjectId(12L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(update));

        assertEquals("PROJECT_ACCESS_DENIED", error.getCode());
        verify(projectAccessChecker).checkAccess(11L, "编辑领料申请");
        verify(projectAccessChecker).checkAccess(12L, "编辑领料申请");
        verify(requisitionMapper, never()).updateById(any(MatRequisition.class));
    }
}
