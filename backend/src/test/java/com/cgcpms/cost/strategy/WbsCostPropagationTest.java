package com.cgcpms.cost.strategy;

import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionItemMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WbsCostPropagationTest {

    @Test
    void materialCostInheritsRequisitionItemWbs() {
        MatRequisitionMapper requisitionMapper = mock(MatRequisitionMapper.class);
        MatRequisitionItemMapper itemMapper = mock(MatRequisitionItemMapper.class);
        CostItemMapper costMapper = mock(CostItemMapper.class);
        CostSubjectResolver resolver = mock(CostSubjectResolver.class);
        MatRequisition requisition = new MatRequisition();
        requisition.setId(10L);
        requisition.setTenantId(1L);
        requisition.setProjectId(20L);
        MatRequisitionItem item = new MatRequisitionItem();
        item.setId(30L);
        item.setWbsTaskId(40L);
        item.setAmount(new BigDecimal("12.34"));
        when(requisitionMapper.selectById(10L)).thenReturn(requisition);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(resolver.resolveDefaultSubjectId(1L, "材料")).thenReturn(50L);

        new MaterialRequisitionCostStrategy(requisitionMapper, itemMapper, costMapper, resolver).generateCost(10L);

        ArgumentCaptor<CostItem> captor = ArgumentCaptor.forClass(CostItem.class);
        verify(costMapper).insert(captor.capture());
        assertEquals(40L, captor.getValue().getWbsTaskId());
    }

    @Test
    void subcontractCostInheritsSubTaskWbs() {
        SubMeasureMapper measureMapper = mock(SubMeasureMapper.class);
        SubMeasureItemMapper itemMapper = mock(SubMeasureItemMapper.class);
        SubTaskMapper taskMapper = mock(SubTaskMapper.class);
        CostItemMapper costMapper = mock(CostItemMapper.class);
        CostSubjectResolver resolver = mock(CostSubjectResolver.class);
        SubMeasure measure = new SubMeasure();
        measure.setId(11L);
        measure.setTenantId(1L);
        measure.setProjectId(21L);
        measure.setSubTaskId(31L);
        measure.setNetAmount(new BigDecimal("10.00"));
        SubMeasureItem item = new SubMeasureItem();
        item.setId(41L);
        item.setAmount(new BigDecimal("10.00"));
        SubTask task = new SubTask();
        task.setTenantId(1L);
        task.setProjectId(21L);
        task.setWbsTaskId(51L);
        when(measureMapper.selectById(11L)).thenReturn(measure);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(taskMapper.selectById(31L)).thenReturn(task);
        when(resolver.resolveDefaultSubjectId(1L, "分包")).thenReturn(61L);

        new SubMeasureCostStrategy(measureMapper, itemMapper, taskMapper, costMapper, resolver).generateCost(11L);

        ArgumentCaptor<CostItem> captor = ArgumentCaptor.forClass(CostItem.class);
        verify(costMapper).insert(captor.capture());
        assertEquals(51L, captor.getValue().getWbsTaskId());
    }
}
