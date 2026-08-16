package com.cgcpms.cost.strategy;

import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionItemMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        MatRequisition requisition = new MatRequisition();
        requisition.setId(10L);
        requisition.setTenantId(1L);
        requisition.setProjectId(20L);
        requisition.setRequisitionDate(LocalDate.of(2026, 8, 14));
        MatRequisitionItem item = new MatRequisitionItem();
        item.setId(30L);
        item.setWbsTaskId(40L);
        item.setAmount(new BigDecimal("12.34"));
        when(requisitionMapper.selectById(10L)).thenReturn(requisition);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(resolver.resolveForFact(1L, 20L, "MAT_REQUISITION", "*", 10L, 30L, null,
                LocalDate.of(2026, 8, 14)))
                .thenReturn(classifiedDecision(50L));

        new MaterialRequisitionCostStrategy(requisitionMapper, itemMapper, costMapper, resolver, periodGuard)
                .generateCost(10L);

        ArgumentCaptor<CostItem> captor = ArgumentCaptor.forClass(CostItem.class);
        verify(costMapper).insert(captor.capture());
        verify(periodGuard).assertWritable(LocalDate.of(2026, 8, 14));
        assertEquals(40L, captor.getValue().getWbsTaskId());
    }

    @Test
    void subcontractCostInheritsSubTaskWbs() {
        SubMeasureMapper measureMapper = mock(SubMeasureMapper.class);
        SubMeasureItemMapper itemMapper = mock(SubMeasureItemMapper.class);
        SubTaskMapper taskMapper = mock(SubTaskMapper.class);
        CostItemMapper costMapper = mock(CostItemMapper.class);
        CostSubjectResolver resolver = mock(CostSubjectResolver.class);
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        SubMeasure measure = new SubMeasure();
        measure.setId(11L);
        measure.setTenantId(1L);
        measure.setProjectId(21L);
        measure.setSubTaskId(31L);
        measure.setMeasureDate(LocalDate.of(2026, 8, 15));
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
        when(resolver.resolveForFact(1L, 21L, "SUB_MEASURE", "*", 11L, 41L, null,
                LocalDate.of(2026, 8, 15)))
                .thenReturn(classifiedDecision(61L));

        new SubMeasureCostStrategy(measureMapper, itemMapper, taskMapper, costMapper, resolver, periodGuard)
                .generateCost(11L);

        ArgumentCaptor<CostItem> captor = ArgumentCaptor.forClass(CostItem.class);
        verify(costMapper).insert(captor.capture());
        verify(periodGuard).assertWritable(LocalDate.of(2026, 8, 15));
        assertEquals(51L, captor.getValue().getWbsTaskId());
    }

    @Test
    void directReceiptCostChecksAccountingPeriodAndInheritsWbs() {
        MatReceiptMapper receiptMapper = mock(MatReceiptMapper.class);
        MatReceiptItemMapper itemMapper = mock(MatReceiptItemMapper.class);
        CostItemMapper costMapper = mock(CostItemMapper.class);
        CostSubjectResolver resolver = mock(CostSubjectResolver.class);
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        MatReceipt receipt = new MatReceipt();
        receipt.setId(12L);
        receipt.setTenantId(1L);
        receipt.setProjectId(22L);
        receipt.setReceiptMode("DIRECT_USE");
        receipt.setReceiptDate(LocalDate.of(2026, 8, 16));
        MatReceiptItem item = new MatReceiptItem();
        item.setId(42L);
        item.setWbsTaskId(52L);
        item.setAmount(new BigDecimal("20.00"));
        when(receiptMapper.selectById(12L)).thenReturn(receipt);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(resolver.resolveForFact(1L, 22L, "MAT_RECEIPT", "DIRECT_USE", 12L, 42L, null,
                LocalDate.of(2026, 8, 16)))
                .thenReturn(classifiedDecision(62L));

        new MaterialReceiptCostStrategy(receiptMapper, itemMapper, costMapper, resolver, periodGuard)
                .generateCost(12L);

        ArgumentCaptor<CostItem> captor = ArgumentCaptor.forClass(CostItem.class);
        verify(costMapper).insert(captor.capture());
        verify(periodGuard).assertWritable(LocalDate.of(2026, 8, 16));
        assertEquals(52L, captor.getValue().getWbsTaskId());
    }

    private static CostSubjectResolver.Decision classifiedDecision(Long subjectId) {
        return new CostSubjectResolver.Decision(subjectId, 2L, 3L, null, null, "CLASSIFIED", 4L);
    }
}
