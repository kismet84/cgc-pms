package com.cgcpms.alert.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.mapper.AlertRuleConfigMapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AlertRuleEvaluatorBatchTest {

    private static final long TENANT_ID = 91L;

    @ParameterizedTest
    @ValueSource(ints = {1, 50, 500})
    void batchSnapshotKeepsElevenReadsIndependentOfProjectCount(int projectCount) {
        AlertLogMapper alerts = emptyMapper(AlertLogMapper.class);
        AlertRuleConfigMapper rules = emptyMapper(AlertRuleConfigMapper.class);
        CostSummaryMapper costs = emptyMapper(CostSummaryMapper.class);
        CtContractMapper contracts = emptyMapper(CtContractMapper.class);
        PayRecordMapper payments = emptyMapper(PayRecordMapper.class);
        SubMeasureMapper measures = emptyMapper(SubMeasureMapper.class);
        MatReceiptMapper receipts = emptyMapper(MatReceiptMapper.class);
        MatPurchaseOrderMapper orders = emptyMapper(MatPurchaseOrderMapper.class);
        VarOrderMapper variations = emptyMapper(VarOrderMapper.class);
        StlSettlementMapper settlements = emptyMapper(StlSettlementMapper.class);
        when(receipts.selectCompletedStockInOrderIds(eq(TENANT_ID), any())).thenReturn(List.of());

        AlertRuleEvaluator evaluator = new AlertRuleEvaluator(alerts, rules, costs, contracts, payments,
                measures, receipts, orders, variations, settlements);
        List<Long> projectIds = LongStream.rangeClosed(1, projectCount).boxed().toList();

        var result = evaluator.evaluateProjects(TENANT_ID, projectIds);

        assertEquals(projectCount, result.size());
        verify(rules).selectList(any());
        verify(costs).selectList(any());
        verify(receipts).selectList(any());
        verify(measures).selectList(any());
        verify(contracts).selectList(any());
        verify(payments).selectList(any());
        verify(settlements).selectList(any());
        verify(orders).selectList(any());
        verify(variations).selectList(any());
        verify(receipts).selectCompletedStockInOrderIds(eq(TENANT_ID),
                argThat(ids -> List.copyOf(ids).equals(projectIds)));
        verify(alerts).selectList(any());
        verifyNoMoreInteractions(alerts, rules, costs, contracts, payments, measures,
                receipts, orders, variations, settlements);
    }

    @Test
    void servicePartitionsAllDistinctProjectsIntoBatchesOfAtMostFiveHundred() {
        List<Long> projects = LongStream.rangeClosed(1, 501).boxed()
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        projects.add(1L);

        List<List<Long>> batches = AlertEvaluationService.projectBatches(projects);

        assertEquals(List.of(500, 1), batches.stream().map(List::size).toList());
        assertEquals(LongStream.rangeClosed(1, 501).boxed().toList(),
                batches.stream().flatMap(List::stream).toList());
    }

    @Test
    void historicalSameKeyBlocksEvenWhenStoredProjectDiffers() {
        AlertLogMapper alerts = emptyMapper(AlertLogMapper.class);
        AlertRuleConfigMapper rules = emptyMapper(AlertRuleConfigMapper.class);
        CostSummaryMapper costs = emptyMapper(CostSummaryMapper.class);
        CtContractMapper contracts = emptyMapper(CtContractMapper.class);
        PayRecordMapper payments = emptyMapper(PayRecordMapper.class);
        SubMeasureMapper measures = emptyMapper(SubMeasureMapper.class);
        MatReceiptMapper receipts = emptyMapper(MatReceiptMapper.class);
        MatPurchaseOrderMapper orders = emptyMapper(MatPurchaseOrderMapper.class);
        VarOrderMapper variations = emptyMapper(VarOrderMapper.class);
        StlSettlementMapper settlements = emptyMapper(StlSettlementMapper.class);
        when(receipts.selectCompletedStockInOrderIds(eq(TENANT_ID), any())).thenReturn(List.of());

        CostSummary hit = new CostSummary();
        hit.setProjectId(1L);
        hit.setDynamicCost(new BigDecimal("2.00"));
        hit.setTargetCost(BigDecimal.ONE);
        when(costs.selectList(any())).thenReturn(List.of(hit));
        AlertLog historical = alert(999L, "P:1:R:DYNAMIC_COST_EXCEEDS_TARGET");
        historical.setProcessStatus("OPEN");
        historical.setTriggeredAt(LocalDateTime.now().minusYears(1));
        when(alerts.selectList(any())).thenReturn(List.of(historical));

        AlertRuleEvaluator evaluator = new AlertRuleEvaluator(alerts, rules, costs, contracts, payments,
                measures, receipts, orders, variations, settlements);
        var result = evaluator.evaluateProjects(TENANT_ID, List.of(1L));

        assertTrue(result.get(1L).isEmpty());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlertLog.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AlertLog>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(alerts).selectList(query.capture());
        String sql = query.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("dedup_key") && sql.contains(" in "));
        assertFalse(sql.contains("project_id"));
    }

    @Test
    void serviceEmitsSharedContractKeyOnlyOnceWithinBatch() {
        List<AlertLog> accepted = new ArrayList<>();
        HashSet<String> emitted = new HashSet<>();

        AlertEvaluationService.addNewAlerts(accepted, List.of(
                alert(1L, "C:9:R:MATERIAL_EXCEEDS_BUDGET"),
                alert(2L, "C:9:R:MATERIAL_EXCEEDS_BUDGET")), emitted);

        assertEquals(1, accepted.size());
        assertEquals(1L, accepted.getFirst().getProjectId());
    }

    @Test
    void serviceKeepsOneSharedKeyAcrossFiveHundredProjectBoundary() {
        List<List<Long>> batches = AlertEvaluationService.projectBatches(
                LongStream.rangeClosed(1, 501).boxed().toList());
        List<AlertLog> accepted = new ArrayList<>();
        HashSet<String> emitted = new HashSet<>();

        for (List<Long> batch : batches) {
            List<AlertLog> candidates = batch.stream()
                    .map(projectId -> alert(projectId, projectId == 501
                            ? "C:9:R:MATERIAL_EXCEEDS_BUDGET"
                            : projectId == 1
                            ? "C:9:R:MATERIAL_EXCEEDS_BUDGET"
                            : "P:" + projectId + ":R:CONTRACT_OVERDUE"))
                    .toList();
            AlertEvaluationService.addNewAlerts(accepted, candidates, emitted);
        }

        assertEquals(500, accepted.size());
        assertEquals(1, accepted.stream()
                .filter(item -> "C:9:R:MATERIAL_EXCEEDS_BUDGET".equals(item.getDedupKey()))
                .count());
    }

    @Test
    void referencedContractKeepsSameTenantLegacySemanticsAndRejectsForeignTenant() {
        AlertLogMapper alerts = emptyMapper(AlertLogMapper.class);
        AlertRuleConfigMapper rules = emptyMapper(AlertRuleConfigMapper.class);
        CostSummaryMapper costs = emptyMapper(CostSummaryMapper.class);
        CtContractMapper contracts = emptyMapper(CtContractMapper.class);
        PayRecordMapper payments = emptyMapper(PayRecordMapper.class);
        SubMeasureMapper measures = emptyMapper(SubMeasureMapper.class);
        MatReceiptMapper receipts = emptyMapper(MatReceiptMapper.class);
        MatPurchaseOrderMapper orders = emptyMapper(MatPurchaseOrderMapper.class);
        VarOrderMapper variations = emptyMapper(VarOrderMapper.class);
        StlSettlementMapper settlements = emptyMapper(StlSettlementMapper.class);
        when(receipts.selectCompletedStockInOrderIds(eq(TENANT_ID), any())).thenReturn(List.of());

        MatReceipt receipt = new MatReceipt();
        receipt.setTenantId(TENANT_ID);
        receipt.setProjectId(1L);
        receipt.setContractId(9L);
        receipt.setTotalAmount(new BigDecimal("2.00"));
        when(receipts.selectList(any())).thenReturn(List.of(receipt));

        CtContract sameTenantOtherProject = contract(9L, TENANT_ID, 999L);
        when(contracts.selectList(any())).thenReturn(List.of(sameTenantOtherProject));
        AlertRuleEvaluator evaluator = new AlertRuleEvaluator(alerts, rules, costs, contracts, payments,
                measures, receipts, orders, variations, settlements);

        var sameTenantResult = evaluator.evaluateProjects(TENANT_ID, List.of(1L));
        assertEquals(List.of("MATERIAL_EXCEEDS_BUDGET"), sameTenantResult.get(1L).stream()
                .map(AlertLog::getRuleType).toList());

        when(contracts.selectList(any())).thenReturn(List.of(contract(9L, 92L, 999L)));
        var foreignTenantResult = evaluator.evaluateProjects(TENANT_ID, List.of(1L));
        assertTrue(foreignTenantResult.get(1L).isEmpty());

        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CtContract.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<CtContract>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(contracts, times(2)).selectList(query.capture());
        String sql = query.getAllValues().getFirst().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("project_id") && sql.contains(" or ") && sql.contains("id in"));
    }

    private static CtContract contract(Long id, Long tenantId, Long projectId) {
        CtContract contract = new CtContract();
        contract.setId(id);
        contract.setTenantId(tenantId);
        contract.setProjectId(projectId);
        contract.setContractAmount(BigDecimal.ONE);
        contract.setContractCode("C-" + id);
        contract.setContractName("合同-" + id);
        return contract;
    }

    private static AlertLog alert(Long projectId, String dedupKey) {
        AlertLog alert = new AlertLog();
        alert.setProjectId(projectId);
        alert.setDedupKey(dedupKey);
        return alert;
    }

    private static <T> T emptyMapper(Class<T> type) {
        T mapper = mock(type);
        when(((com.baomidou.mybatisplus.core.mapper.BaseMapper<?>) mapper).selectList(any())).thenReturn(List.of());
        return mapper;
    }
}
