package com.cgcpms.security;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.closeout.controller.ProjectCloseoutController;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.financeops.controller.FinanceOperationsController;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.payment.vo.PaymentTraceVO;
import com.cgcpms.procurement.vo.ProcurementTraceVO;
import com.cgcpms.settlement.entity.SettlementSubMeasure;
import com.cgcpms.site.vo.SiteDailyDeliveryVO;
import com.cgcpms.site.vo.SiteDailyLogVO;
import com.cgcpms.site.vo.SiteDailyPlannedTaskVO;
import com.cgcpms.site.vo.SiteDailyRequisitionVO;
import com.cgcpms.tech.controller.TechnicalManagementController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessAmountFieldCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void redactsRealContractVoTextAmountsAcrossPageShape() {
        CtContractVO contract = new CtContractVO();
        contract.setId("1001");
        contract.setContractCode("HT-001");
        contract.setCurrentAmount("120.00");
        contract.setAmountWithoutTax("110.09");
        contract.setPayableAmount("80.00");
        contract.setTaxRate("0.09");
        PageResult<CtContractVO> page = new PageResult<>(1, 20, 1, List.of(contract));

        JsonNode result = BusinessAmountFieldCatalog.redact(objectMapper, CtContractVO.class, page);

        JsonNode row = result.path("records").get(0);
        assertEquals("1001", row.path("id").asText());
        assertEquals("HT-001", row.path("contractCode").asText());
        assertTrue(row.path("currentAmount").isNull());
        assertTrue(row.path("amountWithoutTax").isNull());
        assertTrue(row.path("payableAmount").isNull());
        assertEquals("0.09", row.path("taxRate").asText());
    }

    @Test
    void redactsRealContractItemAndPaymentTermShapes() {
        CtContractItem item = new CtContractItem();
        item.setQuantity(new BigDecimal("2.00"));
        item.setUnitPrice(new BigDecimal("5.00"));
        item.setAmountWithoutTax(new BigDecimal("9.17"));
        CtContractPaymentTerm term = new CtContractPaymentTerm();
        term.setPaymentRatio(new BigDecimal("0.30"));
        term.setPaymentAmount(new BigDecimal("30.00"));

        JsonNode itemResult = BusinessAmountFieldCatalog.redact(objectMapper, CtContractItem.class, item);
        JsonNode termResult = BusinessAmountFieldCatalog.redact(
                objectMapper, CtContractPaymentTerm.class, term);

        assertEquals("2.00", itemResult.path("quantity").asText());
        assertTrue(itemResult.path("unitPrice").isNull());
        assertTrue(itemResult.path("amountWithoutTax").isNull());
        assertEquals("0.30", termResult.path("paymentRatio").asText());
        assertTrue(termResult.path("paymentAmount").isNull());
    }

    @Test
    void redactsRealPaymentTraceEntitiesAndPreservesReviewedRatio() {
        CtContract contract = new CtContract();
        contract.setCurrentAmount(new BigDecimal("500.00"));
        contract.setTaxRate(new BigDecimal("0.09"));
        SettlementSubMeasure snapshot = new SettlementSubMeasure();
        snapshot.setApprovedAmountSnapshot(new BigDecimal("75.00"));
        CostSubject subject = new CostSubject();
        subject.setDefaultTargetRatio(new BigDecimal("0.35"));
        PaymentTraceVO trace = new PaymentTraceVO();
        trace.setContract(contract);
        trace.setSettlementSubMeasures(List.of(snapshot));
        trace.setCostSubject(subject);
        trace.setBudgetConservation(Map.of("netReserved", "20.00", "netPaid", "10.00"));

        JsonNode result = BusinessAmountFieldCatalog.redact(objectMapper, PaymentTraceVO.class, trace);

        assertTrue(result.at("/contract/currentAmount").isNull());
        assertEquals("0.09", result.at("/contract/taxRate").asText());
        assertTrue(result.at("/settlementSubMeasures/0/approvedAmountSnapshot").isNull());
        assertEquals(0, new BigDecimal("0.35")
                .compareTo(result.at("/costSubject/defaultTargetRatio").decimalValue()));
        assertTrue(result.at("/budgetConservation/netReserved").isNull());
        assertTrue(result.at("/budgetConservation/netPaid").isNull());
    }

    @Test
    void redactsRealProcurementTraceAmountsAndKeepsQuantities() {
        MatStockTxn stock = new MatStockTxn();
        stock.setQuantity(new BigDecimal("3.00"));
        stock.setAvailableAfter(new BigDecimal("12.00"));
        stock.setUnitCost(new BigDecimal("8.50"));
        stock.setAmount(new BigDecimal("25.50"));
        ProcurementTraceVO trace = new ProcurementTraceVO();
        trace.setStockTransactions(List.of(stock));

        JsonNode result = BusinessAmountFieldCatalog.redact(objectMapper, ProcurementTraceVO.class, trace);

        assertEquals(0, new BigDecimal("3.00")
                .compareTo(result.at("/stockTransactions/0/quantity").decimalValue()));
        assertEquals(0, new BigDecimal("12.00")
                .compareTo(result.at("/stockTransactions/0/availableAfter").decimalValue()));
        assertTrue(result.at("/stockTransactions/0/unitCost").isNull());
        assertTrue(result.at("/stockTransactions/0/amount").isNull());
    }

    @Test
    void preservesReviewedSiteDailyQuantitiesAndProgress() {
        SiteDailyDeliveryVO delivery = new SiteDailyDeliveryVO();
        delivery.setActualQuantity("12.50");
        delivery.setQualifiedQuantity("12.00");
        SiteDailyRequisitionVO requisition = new SiteDailyRequisitionVO();
        requisition.setQuantity("3.25");
        SiteDailyPlannedTaskVO task = new SiteDailyPlannedTaskVO();
        task.setProgressPercent("45.50");
        SiteDailyLogVO dailyLog = new SiteDailyLogVO();
        dailyLog.setDeliveries(List.of(delivery));
        dailyLog.setRequisitions(List.of(requisition));
        dailyLog.setPlannedTasks(List.of(task));

        JsonNode result = BusinessAmountFieldCatalog.redact(objectMapper, SiteDailyLogVO.class, dailyLog);

        assertEquals("12.50", result.at("/deliveries/0/actualQuantity").asText());
        assertEquals("12.00", result.at("/deliveries/0/qualifiedQuantity").asText());
        assertEquals("3.25", result.at("/requisitions/0/quantity").asText());
        assertEquals("45.50", result.at("/plannedTasks/0/progressPercent").asText());
    }

    @Test
    void redactsSupplierWorkspaceReturnAmountsAndKeepsReviewedScoresAndQuantities() {
        Map<String, Object> workspace = Map.of(
                "events", Map.of("records", List.of()),
                "performance", Map.of("records", List.of(Map.of(
                        "deliveryScore", "90.00",
                        "qualityScore", "91.00",
                        "serviceScore", "92.00",
                        "commercialScore", "93.00",
                        "totalScore", "91.50"))),
                "returns", Map.of("records", List.of(Map.of(
                        "returnQuantity", "2.0000",
                        "returnAmount", "20.00"))));

        JsonNode result = BusinessAmountFieldCatalog.redact(
                objectMapper,
                com.cgcpms.supplier.dto.SupplierSourcingModels.WorkspacePage.class,
                workspace);

        assertEquals("91.50", result.at("/performance/records/0/totalScore").asText());
        assertEquals("2.0000", result.at("/returns/records/0/returnQuantity").asText());
        assertTrue(result.at("/returns/records/0/returnAmount").isNull());
    }

    @Test
    void redactsReviewedJdbcMapShapeThroughEndpointContract() throws NoSuchMethodException {
        Map<String, Object> allocation = Map.of("allocated_amount", new BigDecimal("40.00"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoice_amount", new BigDecimal("100.00"));
        data.put("allocatedAmount", new BigDecimal("40.00"));
        data.put("unallocatedAmount", new BigDecimal("60.00"));
        data.put("writeOffRate", new BigDecimal("0.40"));
        data.put("allocations", List.of(allocation));
        MethodParameter returnType = new MethodParameter(FinanceOperationsController.class
                .getDeclaredMethod("writeOff", Long.class), -1);

        JsonNode result = BusinessAmountFieldCatalog.redact(
                objectMapper, returnType, ApiResponse.success(data));

        assertTrue(result.at("/data/invoice_amount").isNull());
        assertTrue(result.at("/data/allocatedAmount").isNull());
        assertTrue(result.at("/data/unallocatedAmount").isNull());
        assertEquals(0, new BigDecimal("0.40")
                .compareTo(result.at("/data/writeOffRate").decimalValue()));
        assertTrue(result.at("/data/allocations/0/allocated_amount").isNull());
    }

    @Test
    void redactsCloseoutOverviewAmountsThroughEndpointContract() throws NoSuchMethodException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("detailPageNo", 1);
        data.put("wbsReadiness", Map.of(
                "totalTasks", 1L,
                "incompleteTasks", new BigDecimal("0")));
        data.put("settlements", List.of(Map.of(
                "id", 11L,
                "grossAmount", new BigDecimal("100.00"),
                "retentionAmount", new BigDecimal("5.00"),
                "netReceivableAmount", new BigDecimal("95.00"))));
        data.put("receivables", List.of(Map.of(
                "originalAmount", new BigDecimal("95.00"),
                "collectedAmount", new BigDecimal("90.00"),
                "outstandingAmount", new BigDecimal("5.00"))));
        data.put("warranties", List.of(Map.of("warrantyAmount", new BigDecimal("5.00"))));
        data.put("wbsTasks", List.of(Map.of(
                "id", 21L,
                "actualProgress", new BigDecimal("75.00"))));
        MethodParameter returnType = new MethodParameter(ProjectCloseoutController.class
                .getDeclaredMethod("overview", Long.class, int.class, int.class), -1);

        JsonNode result = BusinessAmountFieldCatalog.redact(
                objectMapper, returnType, ApiResponse.success(data));

        assertEquals(1, result.at("/data/detailPageNo").intValue());
        assertEquals(0, result.at("/data/wbsReadiness/incompleteTasks").decimalValue()
                .compareTo(BigDecimal.ZERO));
        assertEquals(1L, result.at("/data/wbsReadiness/totalTasks").longValue());
        assertEquals(11L, result.at("/data/settlements/0/id").longValue());
        assertTrue(result.at("/data/settlements/0/grossAmount").isNull());
        assertTrue(result.at("/data/settlements/0/retentionAmount").isNull());
        assertTrue(result.at("/data/settlements/0/netReceivableAmount").isNull());
        assertTrue(result.at("/data/receivables/0/originalAmount").isNull());
        assertTrue(result.at("/data/receivables/0/collectedAmount").isNull());
        assertTrue(result.at("/data/receivables/0/outstandingAmount").isNull());
        assertTrue(result.at("/data/warranties/0/warrantyAmount").isNull());
        assertEquals(0, new BigDecimal("75.00")
                .compareTo(result.at("/data/wbsTasks/0/actualProgress").decimalValue()));
        assertEquals(21L, result.at("/data/wbsTasks/0/id").longValue());
    }

    @Test
    void preservesTechnicalOverviewProgressAndQuantityThroughEndpointContract() throws NoSuchMethodException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("constructionFacts", List.of(Map.of(
                "progressId", 31L,
                "currentProgress", new BigDecimal("62.50"),
                "completedQuantity", new BigDecimal("18.75"))));
        MethodParameter returnType = new MethodParameter(TechnicalManagementController.class
                .getDeclaredMethod("overview", Long.class), -1);

        JsonNode result = BusinessAmountFieldCatalog.redact(
                objectMapper, returnType, ApiResponse.success(data));

        assertEquals(31L, result.at("/data/constructionFacts/0/progressId").longValue());
        assertEquals(0, new BigDecimal("62.50")
                .compareTo(result.at("/data/constructionFacts/0/currentProgress").decimalValue()));
        assertEquals(0, new BigDecimal("18.75")
                .compareTo(result.at("/data/constructionFacts/0/completedQuantity").decimalValue()));

        data.put("unreviewedDecimal", new BigDecimal("1.25"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> BusinessAmountFieldCatalog.redact(
                        objectMapper, returnType, ApiResponse.success(data)));
        assertEquals("AMOUNT_SCHEMA_UNCLASSIFIED", exception.getCode());
    }

    @Test
    void redactsTechnicalTraceApprovalAmountThroughEndpointContract() throws NoSuchMethodException {
        Map<String, Object> data = Map.of("schemeApprovals", List.of(Map.of(
                "id", 41L,
                "amount", new BigDecimal("125.00"))));
        MethodParameter returnType = new MethodParameter(TechnicalManagementController.class
                .getDeclaredMethod("trace", Long.class), -1);

        JsonNode result = BusinessAmountFieldCatalog.redact(
                objectMapper, returnType, ApiResponse.success(data));

        assertEquals(41L, result.at("/data/schemeApprovals/0/id").longValue());
        assertTrue(result.at("/data/schemeApprovals/0/amount").isNull());
    }

    @Test
    void redactsReviewedCostControlJdbcMapShapesAndKeepsRatios() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expected_saving_amount", "10.00");
        data.put("profit_margin", "0.25");
        data.put("activeTarget", Map.of(
                "source_contract_amount", new BigDecimal("1000.00"),
                "target_cost_rate", new BigDecimal("0.80")));
        data.put("forecastItems", List.of(Map.of(
                "actual_cost_amount", new BigDecimal("150.00"),
                "cost_variance_amount", new BigDecimal("20.00"))));

        JsonNode result = BusinessAmountFieldCatalog.redact(objectMapper, Map.class, data);

        assertTrue(result.path("expected_saving_amount").isNull());
        assertEquals("0.25", result.path("profit_margin").asText());
        assertTrue(result.at("/activeTarget/source_contract_amount").isNull());
        assertEquals(0, new BigDecimal("0.80")
                .compareTo(result.at("/activeTarget/target_cost_rate").decimalValue()));
        assertTrue(result.at("/forecastItems/0/actual_cost_amount").isNull());
        assertTrue(result.at("/forecastItems/0/cost_variance_amount").isNull());
    }

    @Test
    void failsClosedForUnknownNumericTextAndUnknownAmountPath() {
        BusinessException numericText = assertThrows(BusinessException.class,
                () -> BusinessAmountFieldCatalog.redact(objectMapper, CtContractVO.class,
                        Map.of("unreviewedMetric", "1.25")));
        BusinessException amountPath = assertThrows(BusinessException.class,
                () -> BusinessAmountFieldCatalog.redact(objectMapper, CtContractVO.class,
                        Map.of("nested", Map.of("currentAmount", "1"))));

        assertEquals("AMOUNT_SCHEMA_UNCLASSIFIED", numericText.getCode());
        assertEquals("AMOUNT_SCHEMA_UNCLASSIFIED", amountPath.getCode());
    }

    @Test
    void unknownResponseTypeFailsClosedOnlyWhenAmountEvidenceExists() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> BusinessAmountFieldCatalog.redact(objectMapper, UnreviewedPayload.class,
                        new UnreviewedPayload("UNKNOWN", new BigDecimal("1.25"))));

        assertEquals("AMOUNT_SCHEMA_UNCLASSIFIED", exception.getCode());
        assertEquals(objectMapper.valueToTree(Map.of("status", "CREATED")),
                BusinessAmountFieldCatalog.redact(objectMapper, UnreviewedPayload.class,
                        Map.of("status", "CREATED")));
    }

    @Test
    void authorizedSerializationHelperRemainsSemanticallyIdentical() {
        CtContractVO contract = new CtContractVO();
        contract.setCurrentAmount("123.45");

        assertEquals(objectMapper.valueToTree(contract),
                BusinessAmountFieldCatalog.preserve(objectMapper, contract));
    }

    private record UnreviewedPayload(String status, BigDecimal metric) {
    }
}
