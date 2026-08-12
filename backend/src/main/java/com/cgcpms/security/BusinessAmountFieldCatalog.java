package com.cgcpms.security;

import com.cgcpms.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Versioned response-type and normalized-path amount contract. */
public final class BusinessAmountFieldCatalog {

    public static final String VERSION = "2026-08-12.v6";

    private static final Pattern DECIMAL_TEXT = Pattern.compile(
            "[-+]?(?:(?:\\d+\\.\\d+)|(?:\\d+\\.)|(?:\\.\\d+)|(?:\\d+[eE][-+]?\\d+))");
    private static final Set<String> AMOUNT_FIELDS = Set.of(
            "amount", "totalAmount", "taxAmount", "unitPrice", "totalPrice", "price",
            "cost", "costAmount", "totalCost", "budget", "budgetAmount", "balance",
            "profit", "profitAmount", "revenue", "revenueAmount", "contractAmount",
            "applyAmount", "approvedAmount", "payAmount", "paidAmount", "invoiceAmount",
            "settlementAmount", "receiptAmount", "fineAmount", "reworkCostAmount",
            "estimatedUnitPrice", "estimatedAmount", "ceilingPrice", "finalBidPrice",
            "depositAmount", "returnedAmount", "openingBalance", "reservedBalance",
            "consumedBalance", "allocatedAmount", "reservedAmount", "consumedAmount",
            "totalDebit", "totalCredit", "warrantyAmount", "dynamicCost", "targetCost",
            "currentAmount", "amountWithoutTax", "payableAmount", "paymentAmount",
            "unpaidAmount", "totalContractAmount", "ownerContractAmount",
            "contractChangeAmount", "varOrderAmount", "subMeasureAmount", "expectedProfit",
            "contractLockedCost", "actualCost", "costDeviation", "estimatedRemainingCost",
            "contractIncome", "actualAmount", "deviationAmount", "pendingPaymentAmount",
            "approvedUnpaidAmount", "overRatioAmount", "warrantyExpiringAmount",
            "totalPaidAmount", "budgetReservedAmount", "budgetConsumedAmount",
            "cashOutflowAmount", "cashBalance", "projectProfit", "cumulativePaidAmount",
            "approvingAmount", "remainingAmount", "totalDynamicCost", "totalExpectedProfit",
            "confirmedMeasureAmount", "totalOrderAmount", "actualPayAmount", "sourceAmount",
            "convertedAmount", "changeAmount", "measuredAmount", "deductionAmount",
            "finalAmount", "reportedAmount", "netAmount", "netReserved", "netConsumed",
            "netPaid", "netCashOutflow", "unallocatedAmount", "reportedAmountSnapshot",
            "approvedAmountSnapshot", "deductionAmountSnapshot", "netAmountSnapshot",
            "unitCost", "invoice_amount", "allocated_amount");

    private static final Map<String, Contract> CONTRACTS = contracts(
            contract("com.cgcpms.contract.vo.CtContractVO",
                    amounts("$.contractAmount", "$.currentAmount", "$.taxAmount", "$.amountWithoutTax",
                            "$.paidAmount", "$.payableAmount", "$.settlementAmount"),
                    safe("$.taxRate")),
            contract("com.cgcpms.contract.entity.CtContractItem",
                    amounts("$.unitPrice", "$.amount", "$.taxAmount", "$.amountWithoutTax"),
                    safe("$.quantity", "$.taxRate")),
            contract("com.cgcpms.contract.entity.CtContractPaymentTerm",
                    amounts("$.paymentAmount"), safe("$.paymentRatio")),
            contract("com.cgcpms.project.vo.PmProjectVO",
                    amounts("$.contractAmount", "$.targetCost", "$.finalBidPrice"), safe()),
            contract("com.cgcpms.project.vo.ProjectOverviewVO",
                    amounts("$.totalContractAmount", "$.dynamicCost", "$.paidAmount"), safe()),
            contract("com.cgcpms.project.vo.ProjectActivationReadinessVO",
                    amounts("$.ownerContractAmount"), safe()),
            contract("com.cgcpms.accounting.entity.AccountingEntry",
                    amounts("$.totalDebit", "$.totalCredit"), safe()),
            contract("com.cgcpms.budget.vo.ProjectBudgetVO",
                    amounts("$.totalAmount", "$.lines[*].budgetAmount", "$.lines[*].reservedAmount",
                            "$.lines[*].consumedAmount", "$.lines[*].availableAmount"), safe()),
            contract("com.cgcpms.budget.vo.BudgetAvailabilityVO",
                    amounts("$.budgetAmount", "$.reservedAmount", "$.consumedAmount", "$.availableAmount"), safe()),
            contract("com.cgcpms.cashbook.vo.FundAccountVO", amounts("$.openingBalance"), safe()),
            contract("com.cgcpms.cashbook.vo.CashJournalEntryVO",
                    amounts("$.amount", "$.runningBalance"), safe()),
            contract("com.cgcpms.cashbook.vo.CashJournalSummaryVO",
                    amounts("$.cashBalance", "$.bankBalance", "$.income", "$.expense",
                            "$.cumulativeCashOut", "$.cumulativeCashIn", "$.outstandingDeposit",
                            "$.actualBidExpense", "$.cashNetOutflow"), safe()),
            contract("com.cgcpms.contract.vo.CtContractChangeVO",
                    amounts("$.beforeAmount", "$.changeAmount", "$.afterAmount"), safe()),
            contract("com.cgcpms.cost.vo.CostTargetVO",
                    amounts("$.totalTargetAmount", "$.totalBidCostAmount", "$.totalResponsibilityAmount",
                            "$.sourceContractAmount"), safe("$.targetCostRate")),
            contract("com.cgcpms.cost.vo.CostTargetItemVO",
                    amounts("$.targetAmount", "$.bidCostAmount", "$.responsibilityAmount"), safe()),
            contract("com.cgcpms.cost.service.CostTargetService$DefaultAllocation",
                    amounts("$.sourceContractAmount", "$.totalTargetAmount", "$.items[*].targetAmount",
                            "$.items[*].bidCostAmount", "$.items[*].responsibilityAmount"),
                    safe("$.targetCostRate", "$.items[*].defaultTargetRatio")),
            contract("com.cgcpms.cost.vo.CostProjectSummaryVO", costProjectSummaryAmounts(),
                    safe("$.profitMargin", "$.subjects[*].profitMargin")),
            contract("com.cgcpms.cost.vo.CostSummaryVO", costSummaryAmounts(), safe("$.profitMargin")),
            contract("com.cgcpms.cost.vo.CostSubjectVO", amounts(),
                    safe("$.subjectCode", "$.defaultTargetRatio")),
            contract("com.cgcpms.cost.vo.CostSubjectTreeNodeVO", amounts(),
                    safe("$.subjectCode", "$.defaultTargetRatio", "$.children[*].subjectCode",
                            "$.children[*].defaultTargetRatio")),
            contract("com.cgcpms.payment.vo.PayRecordVO", amounts("$.payAmount"), safe()),
            contract("com.cgcpms.payment.vo.PayApplicationVO",
                    amounts("$.applyAmount", "$.approvedAmount", "$.actualPayAmount",
                            "$.basis[*].sourceAmount", "$.basis[*].paidAmount"), safe()),
            contract("com.cgcpms.payment.vo.PaymentSourceOptionVO",
                    amounts("$.sourceTotalAmount", "$.committedAmount", "$.availableAmount"), safe()),
            contract("com.cgcpms.invoice.vo.InvoiceVO",
                    amounts("$.invoiceAmount", "$.taxAmount"), safe("$.taxRate")),
            contract("com.cgcpms.invoice.vo.InvoiceRecognizeResultVO",
                    amounts("$.invoiceAmount", "$.taxAmount"), safe("$.taxRate", "$.confidence")),
            contract("com.cgcpms.purchase.vo.MatPurchaseRequestVO",
                    amounts("$.totalAmount", "$.items[*].estimatedUnitPrice", "$.items[*].estimatedAmount"),
                    safe("$.items[*].quantity", "$.items[*].approvedQuantity")),
            contract("com.cgcpms.purchase.vo.MatPurchaseRequestItemVO",
                    amounts("$.estimatedUnitPrice", "$.estimatedAmount"),
                    safe("$.quantity", "$.approvedQuantity")),
            contract("com.cgcpms.purchase.vo.MatPurchaseOrderVO",
                    amounts("$.totalAmount", "$.items[*].unitPrice", "$.items[*].amount",
                            "$.items[*].taxAmount", "$.items[*].amountWithoutTax"),
                    safe("$.items[*].quantity", "$.items[*].taxRate", "$.items[*].receivedQuantity")),
            contract("com.cgcpms.receipt.vo.MatReceiptVO",
                    amounts("$.totalAmount", "$.items[*].unitPrice", "$.items[*].amount"),
                    safe("$.items[*].actualQuantity", "$.items[*].qualifiedQuantity",
                            "$.items[*].acceptedQuantity", "$.items[*].unqualifiedQuantity",
                            "$.items[*].orderedQuantity", "$.items[*].receivedQuantity",
                            "$.items[*].remainingQuantity")),
            contract("com.cgcpms.requisition.vo.MatRequisitionVO",
                    amounts("$.totalAmount", "$.items[*].unitPrice", "$.items[*].amount"),
                    safe("$.items[*].quantity")),
            contract("com.cgcpms.inventory.vo.MatStockVO",
                    amounts("$.inventoryValue", "$.averageUnitCost"),
                    safe("$.availableQty", "$.safetyStockQty", "$.replenishmentTargetQty")),
            contract("com.cgcpms.inventory.vo.MatStockLedgerVO",
                    amounts("$.stock.inventoryValue", "$.stock.averageUnitCost",
                            "$.txns.records[*].unitCost", "$.txns.records[*].amount"),
                    safe("$.stock.availableQty", "$.stock.safetyStockQty", "$.stock.replenishmentTargetQty",
                            "$.txns.records[*].quantity", "$.txns.records[*].availableAfter")),
            contract("com.cgcpms.inventory.vo.StockConsumptionBaselineVO", amounts(),
                    safe("$.grossIssued30", "$.returned30", "$.netIssued30", "$.grossIssued90",
                            "$.returned90", "$.netIssued90")),
            contract("com.cgcpms.inventory.vo.StockTransferVO",
                    amounts("$.unitCost", "$.amount"), safe("$.quantity")),
            contract("com.cgcpms.inventory.vo.StockTransferCandidateVO", amounts(),
                    safe("$.availableQty", "$.safetyStockQty", "$.transferableQty")),
            contract("com.cgcpms.overhead.vo.OverheadAllocationExecutionResult",
                    amounts("$.allocatedAmount"), safe()),
            contract("com.cgcpms.revenue.vo.ContractRevenueVO",
                    amounts("$.revenueAmount", "$.revenueTax", "$.revenueAmountWithTax",
                            "$.billedAmount", "$.billedTax"), safe("$.progressPercent")),
            contract("com.cgcpms.settlement.vo.SettlementAmountBaselineVO",
                    amounts("$.storedContractAmount", "$.currentEffectiveContractAmount", "$.storedChangeAmount",
                            "$.currentConfirmedVariationAmount", "$.storedMeasuredAmount",
                            "$.currentApprovedMeasuredAmount", "$.deductionAmount", "$.storedPaidAmount",
                            "$.currentPaidAmount", "$.storedFinalAmount", "$.recalculatedFinalAmount",
                            "$.finalAmountDelta", "$.storedWarrantyAmount", "$.recalculatedWarrantyAmount",
                            "$.storedUnpaidAmount", "$.recalculatedUnpaidAmount"), safe()),
            contract("com.cgcpms.settlement.vo.SettlementCostItemVO",
                    amounts("$.amount", "$.taxAmount", "$.amountWithoutTax"), safe()),
            contract("com.cgcpms.settlement.vo.SettlementPaymentItemVO",
                    amounts("$.applyAmount", "$.approvedAmount", "$.actualPayAmount"), safe()),
            contract("com.cgcpms.subcontract.vo.SubMeasureVO",
                    amounts("$.reportedAmount", "$.approvedAmount", "$.deductionAmount", "$.netAmount",
                            "$.items[*].unitPrice", "$.items[*].amount"),
                    safe("$.items[*].contractQuantity", "$.items[*].currentQuantity",
                            "$.items[*].cumulativeQuantity")),
            contract("com.cgcpms.subcontract.vo.SubMeasureItemVO",
                    amounts("$.unitPrice", "$.amount"),
                    safe("$.contractQuantity", "$.currentQuantity", "$.cumulativeQuantity")),
            contract("com.cgcpms.subcontract.vo.SubTaskVO", amounts(), safe("$.progressPercent")),
            contract("com.cgcpms.variation.vo.VarOrderVO",
                    amounts("$.reportedAmount", "$.approvedAmount", "$.confirmedAmount", "$.estimatedCostAmount",
                            "$.items[*].reportedAmount", "$.items[*].approvedAmount", "$.items[*].amount"), safe()),
            contract("com.cgcpms.workflow.vo.WfTemplateVO",
                    amounts("$.amountMin", "$.amountMax"), safe()),
            contract("java.util.Map",
                    concat(amounts("$.current_reported_amount", "$.submitted_amount",
                                    "$.confirmed_amount", "$.cumulative_reported_amount"),
                            costControlMapAmounts()),
                    costControlMapSafeDecimals()),
            contract("com.cgcpms.site.vo.SiteDailyLogVO", amounts(),
                    safe("$.deliveries[*].actualQuantity", "$.deliveries[*].qualifiedQuantity",
                            "$.requisitions[*].quantity", "$.plannedTasks[*].progressPercent")),
            contract("com.cgcpms.dashboard.vo.BusinessManagerDashboardVO",
                    amounts("$.totalContractAmount", "$.contractChangeAmount", "$.varOrderAmount",
                            "$.subMeasureAmount", "$.recentChanges[*].contractAmount",
                            "$.recentChanges[*].currentAmount", "$.recentChanges[*].paidAmount",
                            "$.settlementItems[*].targetCost", "$.settlementItems[*].dynamicCost",
                            "$.settlementItems[*].contractIncome", "$.settlementItems[*].expectedProfit",
                            "$.settlementItems[*].costDeviation", "$.settlementItems[*].paidAmount",
                            "$.settlementItems[*].contractAmount"),
                    safe("$.paidRatio", "$.settlementProgress")),
            contract("com.cgcpms.dashboard.vo.CostBreakdownVO",
                    amounts("$.targetCost", "$.dynamicCost", "$.expectedProfit",
                            "$.subjectBreakdowns[*].targetCost", "$.subjectBreakdowns[*].contractLockedCost",
                            "$.subjectBreakdowns[*].actualCost", "$.subjectBreakdowns[*].dynamicCost",
                            "$.subjectBreakdowns[*].costDeviation"), safe()),
            contract("com.cgcpms.dashboard.vo.CostManagerDashboardVO",
                    amounts("$.targetCost", "$.dynamicCost", "$.costDeviation", "$.contractLockedCost",
                            "$.actualCost", "$.estimatedRemainingCost", "$.expectedProfit", "$.contractIncome",
                            "$.trendPoints[*].targetCost", "$.trendPoints[*].dynamicCost",
                            "$.trendPoints[*].costDeviation", "$.subjectRankings[*].targetCost",
                            "$.subjectRankings[*].actualCost", "$.subjectRankings[*].dynamicCost",
                            "$.subjectRankings[*].costDeviation", "$.pendingPayments[*].payAmount",
                            "$.ledgerRows[*].budgetAmount", "$.ledgerRows[*].actualAmount",
                            "$.ledgerRows[*].deviationAmount"),
                    safe("$.subjectRankings[*].ratio", "$.ledgerRows[*].completionRatio",
                            "$.ledgerRows[*].deviationRatio")),
            contract("com.cgcpms.dashboard.vo.FinanceDashboardVO",
                    amounts("$.pendingPaymentAmount", "$.approvedUnpaidAmount", "$.overRatioAmount",
                            "$.warrantyExpiringAmount", "$.totalContractAmount", "$.totalPaidAmount",
                            "$.budgetAmount", "$.budgetReservedAmount", "$.budgetConsumedAmount",
                            "$.cashOutflowAmount", "$.cashBalance", "$.projectProfit",
                            "$.trendPoints[*].cashOutflowAmount", "$.trendPoints[*].cumulativePaidAmount",
                            "$.trendPoints[*].pendingPaymentAmount", "$.pendingPayments[*].payAmount",
                            "$.overRatioPayments[*].payAmount", "$.contractFundBreakdowns[*].contractAmount",
                            "$.contractFundBreakdowns[*].paidAmount",
                            "$.contractFundBreakdowns[*].approvingAmount",
                            "$.contractFundBreakdowns[*].approvedUnpaidAmount",
                            "$.contractFundBreakdowns[*].remainingAmount",
                            "$.contractFundBreakdowns[*].paymentRecords[*].payAmount"),
                    safe("$.budgetExecutionRate", "$.contractFundBreakdowns[*].paymentRatio")),
            contract("com.cgcpms.dashboard.vo.ManagementDashboardVO",
                    amounts("$.totalContractAmount", "$.totalDynamicCost", "$.totalExpectedProfit",
                            "$.totalPaidAmount", "$.projectRankings[*].targetCost",
                            "$.projectRankings[*].dynamicCost", "$.projectRankings[*].contractIncome",
                            "$.projectRankings[*].expectedProfit", "$.projectRankings[*].costDeviation",
                            "$.projectRankings[*].paidAmount", "$.projectRankings[*].contractAmount",
                            "$.metricSources[*].contractAmount", "$.metricSources[*].dynamicCost",
                            "$.metricSources[*].expectedProfit", "$.metricSources[*].paidAmount",
                            "$.overdueItems[*].amount"), safe()),
            contract("com.cgcpms.dashboard.vo.ProductionManagerDashboardVO",
                    amounts("$.confirmedMeasureAmount", "$.recentReceipts[*].amount",
                            "$.recentRequisitions[*].amount", "$.recentSubMeasures[*].amount"), safe()),
            contract("com.cgcpms.dashboard.vo.PurchaseManagerDashboardVO",
                    amounts("$.totalOrderAmount", "$.recentRequests[*].amount", "$.purchaseOrders[*].amount",
                            "$.overdueOrders[*].amount", "$.pendingReceipts[*].amount"),
                    safe("$.supplierScores[*].onTimeDeliveryRate", "$.supplierScores[*].performanceScore")),
            contract("com.cgcpms.dashboard.vo.ProjectManagerDashboardVO",
                    amounts("$.laggingProjects[*].targetCost", "$.laggingProjects[*].dynamicCost",
                            "$.laggingProjects[*].contractIncome", "$.laggingProjects[*].expectedProfit",
                            "$.laggingProjects[*].costDeviation", "$.laggingProjects[*].paidAmount",
                            "$.laggingProjects[*].contractAmount", "$.expiringContracts[*].contractAmount",
                            "$.expiringContracts[*].currentAmount", "$.expiringContracts[*].paidAmount",
                            "$.pendingTasks[*].amount", "$.pendingApprovals[*].amount"), safe()),
            contract("com.cgcpms.tech.vo.ChiefEngineerDashboardVO",
                    amounts("$.pendingReviews[*].amount", "$.pendingCoordinations[*].amount",
                            "$.openIssues[*].amount", "$.overdueItems[*].amount"), safe()),
            contract("com.cgcpms.supplier.dto.SupplierSourcingModels$WorkspacePage",
                    amounts("$.returns.records[*].returnAmount"),
                    safe("$.performance.records[*].deliveryScore",
                            "$.performance.records[*].qualityScore",
                            "$.performance.records[*].serviceScore",
                            "$.performance.records[*].commercialScore",
                            "$.performance.records[*].totalScore",
                            "$.returns.records[*].returnQuantity")),
            contract("com.cgcpms.payment.vo.PaymentTraceVO", paymentTraceAmounts(), paymentTraceSafe()),
            contract("com.cgcpms.procurement.vo.ProcurementTraceVO",
                    procurementTraceAmounts(), procurementTraceSafe()),
            contract("com.cgcpms.contract.controller.CtContractController#kpi",
                    amounts("$.totalAmount", "$.paidAmount", "$.unpaidAmount"), safe()),
            contract("com.cgcpms.financeops.controller.FinanceOperationsController#writeOff",
                    amounts("$.invoice_amount", "$.allocatedAmount", "$.unallocatedAmount",
                            "$.allocations[*].allocated_amount"), safe("$.writeOffRate")),
            contract("com.cgcpms.closeout.controller.ProjectCloseoutController#overview",
                    amounts("$.settlements[*].grossAmount", "$.settlements[*].retentionAmount",
                            "$.settlements[*].netReceivableAmount", "$.receivables[*].originalAmount",
                            "$.receivables[*].collectedAmount", "$.receivables[*].outstandingAmount",
                            "$.warranties[*].warrantyAmount"),
                    safe("$.wbsReadiness.incompleteTasks", "$.wbsTasks[*].actualProgress")),
            contract("com.cgcpms.schedule.controller.ProjectScheduleController#dailyProgress",
                    amounts(), safe("$.previousProgress", "$.currentProgress", "$.completedQuantity")),
            contract("com.cgcpms.schedule.controller.ProjectScheduleController#replaceDailyProgress",
                    amounts(), safe("$.previousProgress", "$.currentProgress", "$.completedQuantity")));

    private BusinessAmountFieldCatalog() {
    }

    public static JsonNode redact(ObjectMapper objectMapper, MethodParameter returnType, Object value) {
        ResolvedContract resolved = resolve(returnType);
        return redact(objectMapper, resolved.contract(), resolved.responseType(), value);
    }

    public static JsonNode redact(ObjectMapper objectMapper, Class<?> responseType, Object value) {
        return redact(objectMapper, CONTRACTS.get(responseType.getName()), responseType.getName(), value);
    }

    public static JsonNode preserve(ObjectMapper objectMapper, Object value) {
        return objectMapper.valueToTree(value);
    }

    public static boolean isAmountField(String fieldName) {
        return AMOUNT_FIELDS.contains(fieldName);
    }

    private static JsonNode redact(ObjectMapper objectMapper, Contract contract,
                                   String responseType, Object value) {
        JsonNode root = objectMapper.valueToTree(value);
        redactNode(root, "$", contract, responseType);
        return root;
    }

    private static void redactNode(JsonNode node, String path, Contract contract, String responseType) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childName = field.getKey();
                JsonNode child = field.getValue();
                String childPath = path + "." + childName;
                Kind kind = classify(contract, childPath);
                if (kind == Kind.AMOUNT) {
                    if (!child.isNull() && !child.isValueNode()) unclassified(responseType, childPath);
                    object.set(childName, NullNode.instance);
                } else if (kind == Kind.SAFE_DECIMAL) {
                    if (!child.isNull() && !child.isValueNode()) unclassified(responseType, childPath);
                } else {
                    if (AMOUNT_FIELDS.contains(childName)) unclassified(responseType, childPath);
                    redactNode(child, childPath, contract, responseType);
                }
            }
            return;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (JsonNode child : array) redactNode(child, path + "[*]", contract, responseType);
            return;
        }
        if (node.isFloatingPointNumber()
                || node.isTextual() && DECIMAL_TEXT.matcher(node.textValue().trim()).matches()) {
            unclassified(responseType, path);
        }
    }

    private static Kind classify(Contract contract, String path) {
        return contract == null ? null : contract.paths().get(normalize(path));
    }

    private static String normalize(String path) {
        if (path.startsWith("$.data.records[*]")) path = "$" + path.substring("$.data.records[*]".length());
        else if (path.startsWith("$.data[*]")) path = "$" + path.substring("$.data[*]".length());
        else if (path.startsWith("$.data.")) path = "$" + path.substring("$.data".length());
        else if (path.startsWith("$.records[*]")) path = "$" + path.substring("$.records[*]".length());
        else if (path.startsWith("$[*]")) path = "$" + path.substring("$[*]".length());
        while (path.startsWith("$.children[*].children[*]")) {
            path = "$.children[*]" + path.substring("$.children[*].children[*]".length());
        }
        return path;
    }

    private static void unclassified(String responseType, String path) {
        throw new BusinessException("AMOUNT_SCHEMA_UNCLASSIFIED",
                "响应金额字段未分类: type=" + responseType + ", path=" + normalize(path)
                        + " (catalog=" + VERSION + ")");
    }

    private static ResolvedContract resolve(MethodParameter returnType) {
        Method method = returnType.getMethod();
        if (method != null) {
            String endpoint = method.getDeclaringClass().getName() + "#" + method.getName();
            Contract endpointContract = CONTRACTS.get(endpoint);
            if (endpointContract != null) return new ResolvedContract(endpointContract, endpoint);
        }
        Type type = returnType.getGenericParameterType();
        Contract contract = findContract(type);
        return new ResolvedContract(contract, type.getTypeName());
    }

    private static Contract findContract(Type type) {
        if (type instanceof Class<?> clazz) return CONTRACTS.get(clazz.getName());
        if (!(type instanceof ParameterizedType parameterized)) return null;
        for (Type argument : parameterized.getActualTypeArguments()) {
            Contract contract = findContract(argument);
            if (contract != null) return contract;
        }
        return findContract(parameterized.getRawType());
    }

    private static Map<String, Contract> contracts(Contract... contracts) {
        Map<String, Contract> result = new HashMap<>();
        for (Contract contract : contracts) {
            if (result.put(contract.responseType(), contract) != null) {
                throw new IllegalStateException("重复金额响应契约: " + contract.responseType());
            }
        }
        return Map.copyOf(result);
    }

    private static Contract contract(String responseType, String[] amountPaths, String[] safePaths) {
        Map<String, Kind> paths = new HashMap<>();
        addPaths(paths, Kind.AMOUNT, amountPaths);
        addPaths(paths, Kind.SAFE_DECIMAL, safePaths);
        return new Contract(responseType, Map.copyOf(paths));
    }

    private static void addPaths(Map<String, Kind> paths, Kind kind, String[] values) {
        for (String path : values) {
            Kind previous = paths.put(path, kind);
            if (previous != null) throw new IllegalStateException("重复金额路径: " + path);
        }
    }

    private static String[] amounts(String... paths) {
        return paths;
    }

    private static String[] safe(String... paths) {
        return paths;
    }

    private static String[] concat(String[]... groups) {
        int size = 0;
        for (String[] group : groups) size += group.length;
        String[] result = new String[size];
        int offset = 0;
        for (String[] group : groups) {
            System.arraycopy(group, 0, result, offset, group.length);
            offset += group.length;
        }
        return result;
    }

    private static String[] prefixedFields(String[] prefixes, String... fields) {
        String[] result = new String[prefixes.length * fields.length];
        int index = 0;
        for (String prefix : prefixes) {
            for (String field : fields) result[index++] = prefix + "." + field;
        }
        return result;
    }

    private static String[] costControlMapAmounts() {
        String[] prefixes = {
                "$", "$.project", "$.activeTarget", "$.target", "$.main", "$.latestForecast",
                "$.forecast", "$.projects[*]", "$.targetItems[*]", "$.forecastInputItems[*]",
                "$.forecastItems[*]", "$.correctiveActions[*]", "$.forecastHistory[*]",
                "$.costSources[*]", "$.summary[*]", "$.items[*]"
        };
        return prefixedFields(prefixes,
                "amount", "contract_amount", "target_cost", "source_contract_amount",
                "total_target_amount", "total_bid_cost_amount", "total_responsibility_amount",
                "target_amount", "bid_cost_amount", "target_cost_amount", "responsibility_amount",
                "committed_amount", "committed_cost_amount", "actual_amount", "actual_cost_amount",
                "recommended_remaining_amount", "estimated_remaining_amount",
                "forecast_at_completion_amount", "contract_income_amount", "forecast_profit_amount",
                "cost_variance_amount", "expected_saving_amount", "actual_saving_amount", "paid_amount",
                "estimated_remaining_cost", "dynamic_cost", "contract_income", "confirmed_revenue",
                "expected_profit", "cost_deviation", "responsibility_cost",
                "forecast_at_completion_cost", "forecast_profit", "contract_locked_cost",
                "source_amount", "allocated_amount", "unit_price", "gross_amount", "deducted_amount",
                "tax_amount", "retention_amount", "targetCost", "targetAmount", "bidCostAmount",
                "responsibilityAmount", "committedAmount", "actualAmount", "recommendedRemainingAmount",
                "estimatedRemainingAmount", "forecastAtCompletionAmount", "contractIncomeAmount",
                "forecastProfitAmount", "costVarianceAmount", "expectedSavingAmount",
                "actualSavingAmount", "unitPrice", "contractIncome", "dynamicCost",
                "forecastAtCompletionCost", "forecastProfit", "contractLockedCost", "actualCost",
                "paidAmount", "estimatedRemainingCost", "confirmedRevenue", "expectedProfit",
                "costDeviation", "responsibilityCost");
    }

    private static String[] costControlMapSafeDecimals() {
        return prefixedFields(new String[]{
                        "$", "$.project", "$.activeTarget", "$.target", "$.main", "$.latestForecast",
                        "$.forecast", "$.projects[*]", "$.targetItems[*]", "$.forecastInputItems[*]",
                        "$.forecastItems[*]", "$.correctiveActions[*]", "$.forecastHistory[*]",
                        "$.costSources[*]", "$.summary[*]", "$.items[*]"
                },
                "profit_margin", "profitMargin", "target_cost_rate", "targetCostRate",
                "default_target_ratio", "defaultTargetRatio");
    }

    private static String[] paymentTraceAmounts() {
        return amounts(
                "$.project.contractAmount", "$.project.targetCost", "$.contract.contractAmount",
                "$.contract.currentAmount", "$.contract.paidAmount", "$.contract.payableAmount",
                "$.contract.taxAmount", "$.contract.amountWithoutTax", "$.contract.settlementAmount",
                "$.paymentApplication.applyAmount", "$.paymentApplication.approvedAmount",
                "$.paymentApplication.actualPayAmount", "$.applicationSources[*].sourceAmount",
                "$.applicationSources[*].paidAmount", "$.expenses[*].amount",
                "$.expenses[*].convertedAmount", "$.expenses[*].paidAmount",
                "$.settlements[*].contractAmount", "$.settlements[*].changeAmount",
                "$.settlements[*].measuredAmount", "$.settlements[*].deductionAmount",
                "$.settlements[*].paidAmount", "$.settlements[*].finalAmount",
                "$.settlements[*].unpaidAmount", "$.settlements[*].warrantyAmount",
                "$.settlementSubMeasures[*].reportedAmountSnapshot",
                "$.settlementSubMeasures[*].approvedAmountSnapshot",
                "$.settlementSubMeasures[*].deductionAmountSnapshot",
                "$.settlementSubMeasures[*].netAmountSnapshot", "$.subMeasures[*].reportedAmount",
                "$.subMeasures[*].approvedAmount", "$.subMeasures[*].deductionAmount",
                "$.subMeasures[*].netAmount", "$.paymentRecords[*].payAmount",
                "$.paymentSourceAllocations[*].allocatedAmount", "$.cashJournals[*].amount",
                "$.invoices[*].invoiceAmount", "$.invoices[*].taxAmount",
                "$.invoiceAllocations[*].allocatedAmount", "$.budgetLedgers[*].amount",
                "$.budgetLedgers[*].reservedBalance", "$.budgetLedgers[*].consumedBalance",
                "$.contractBudgetAllocation.allocatedAmount", "$.contractBudgetAllocation.reservedAmount",
                "$.contractBudgetAllocation.consumedAmount", "$.projectBudget.totalAmount",
                "$.projectBudgetLine.budgetAmount", "$.projectBudgetLine.reservedAmount",
                "$.projectBudgetLine.consumedAmount", "$.materialReceiptItems[*].unitPrice",
                "$.materialReceiptItems[*].amount", "$.materialReceipts[*].totalAmount",
                "$.budgetConservation.netReserved", "$.budgetConservation.netConsumed",
                "$.budgetConservation.netPaid", "$.budgetConservation.netCashOutflow",
                "$.accountingEntries[*].totalDebit", "$.accountingEntries[*].totalCredit",
                "$.accountingEntryLines[*].amount");
    }

    private static String[] costSummaryAmounts() {
        return amounts("$.targetCost", "$.contractLockedCost", "$.actualCost", "$.paidAmount",
                "$.estimatedRemainingCost", "$.dynamicCost", "$.contractIncome", "$.confirmedRevenue",
                "$.expectedProfit", "$.costDeviation", "$.responsibilityCost",
                "$.forecastAtCompletionCost", "$.forecastProfit");
    }

    private static String[] costProjectSummaryAmounts() {
        String[] root = costSummaryAmounts();
        String[] nested = new String[root.length];
        for (int i = 0; i < root.length; i++) nested[i] = "$.subjects[*]" + root[i].substring(1);
        String[] combined = new String[root.length + nested.length];
        System.arraycopy(root, 0, combined, 0, root.length);
        System.arraycopy(nested, 0, combined, root.length, nested.length);
        return combined;
    }

    private static String[] paymentTraceSafe() {
        return safe(
                "$.contract.taxRate", "$.subTasks[*].progressPercent", "$.invoices[*].taxRate",
                "$.costSubject.defaultTargetRatio", "$.materialReceiptItems[*].actualQuantity",
                "$.materialReceiptItems[*].qualifiedQuantity", "$.materialReceiptItems[*].acceptedQuantity",
                "$.materialReceiptItems[*].unqualifiedQuantity");
    }

    private static String[] procurementTraceAmounts() {
        return amounts(
                "$.project.contractAmount", "$.project.targetCost", "$.contract.contractAmount",
                "$.contract.currentAmount", "$.contract.paidAmount", "$.contract.payableAmount",
                "$.contract.taxAmount", "$.contract.amountWithoutTax", "$.contract.settlementAmount",
                "$.purchaseRequestItems[*].estimatedUnitPrice", "$.purchaseRequestItems[*].estimatedAmount",
                "$.purchaseOrder.totalAmount", "$.purchaseOrderItems[*].unitPrice",
                "$.purchaseOrderItems[*].amount", "$.purchaseOrderItems[*].taxAmount",
                "$.purchaseOrderItems[*].amountWithoutTax", "$.receipt.totalAmount",
                "$.receiptItems[*].unitPrice", "$.receiptItems[*].amount", "$.requisition.totalAmount",
                "$.requisitionItems[*].unitPrice", "$.requisitionItems[*].amount",
                "$.stockTransactions[*].unitCost", "$.stockTransactions[*].amount",
                "$.costs[*].amount", "$.costs[*].taxAmount", "$.costs[*].amountWithoutTax",
                "$.materialReturn.totalAmount", "$.materialReturnItems[*].unitCost",
                "$.materialReturnItems[*].amount", "$.supplierReturn.totalAmount",
                "$.supplierReturnItems[*].unitCost", "$.supplierReturnItems[*].amount");
    }

    private static String[] procurementTraceSafe() {
        return safe(
                "$.contract.taxRate", "$.purchaseRequestItems[*].quantity",
                "$.purchaseRequestItems[*].approvedQuantity", "$.purchaseOrderItems[*].quantity",
                "$.purchaseOrderItems[*].taxRate", "$.purchaseOrderItems[*].receivedQuantity",
                "$.receiptItems[*].actualQuantity", "$.receiptItems[*].qualifiedQuantity",
                "$.receiptItems[*].acceptedQuantity", "$.receiptItems[*].unqualifiedQuantity",
                "$.requisitionItems[*].quantity", "$.stockTransactions[*].quantity",
                "$.stockTransactions[*].availableAfter", "$.materialReturnItems[*].quantity",
                "$.supplierReturn.returnQuantity", "$.supplierReturnItems[*].quantity");
    }

    private enum Kind {
        AMOUNT,
        SAFE_DECIMAL
    }

    private record Contract(String responseType, Map<String, Kind> paths) {
    }

    private record ResolvedContract(Contract contract, String responseType) {
    }
}
