package com.cgcpms.financeops.vo;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FinanceWorkspaceVOs {
    private FinanceWorkspaceVOs() {}

    public record PaymentScheduleVO(String id, String projectId, String contractId, String payApplicationId,
                                    String scheduleName, LocalDate plannedDate, String plannedAmount,
                                    String paidAmount, String status) {
        public static PaymentScheduleVO from(Map<String,Object> row) {
            return new PaymentScheduleVO(idString(row.get("id")), idString(row.get("project_id")), idString(row.get("contract_id")),
                    idString(row.get("pay_application_id")), text(row.get("schedule_name")), date(row.get("planned_date")),
                    money(row.get("planned_amount")), money(row.get("paid_amount")), text(row.get("status")));
        }
    }

    public record FinanceAlertVO(String id, String projectId, String alertType, String businessType,
                                 String businessId, String severity, LocalDateTime dueAt,
                                 String status, String message) {
        public static FinanceAlertVO from(Map<String,Object> row) {
            return new FinanceAlertVO(idString(row.get("id")), idString(row.get("project_id")), text(row.get("alert_type")),
                    text(row.get("business_type")), idString(row.get("business_id")), text(row.get("severity")),
                    dateTime(row.get("due_at")), text(row.get("status")), text(row.get("message")));
        }
    }

    public record FinanceSnapshotVO(String id, String projectId, String projectName, LocalDate snapshotDate,
                                     String contractAmount, String approvedUnpaidAmount, String paidAmount,
                                     String budgetAmount, String cashInflow, String cashOutflow,
                                     String actualCost, String profitAmount) {
        public static FinanceSnapshotVO from(Map<String,Object> row) {
            return new FinanceSnapshotVO(idString(row.get("id")), idString(row.get("project_id")), text(row.get("project_name")),
                    date(row.get("snapshot_date")), money(row.get("contract_amount")),
                    money(row.get("approved_unpaid_amount")), money(row.get("paid_amount")),
                    money(row.get("budget_amount")), money(row.get("cash_inflow")),
                    money(row.get("cash_outflow")), money(row.get("actual_cost")), money(row.get("profit_amount")));
        }
    }

    public record FinanceOperationsSummaryVO(Integer projectCount, String fundBalance,
                                             String forecastInflow, String forecastOutflow,
                                             String financingAmount, String fundingGap) {
        public static FinanceOperationsSummaryVO from(Map<String,Object> row) {
            return new FinanceOperationsSummaryVO(integer(row.get("projectCount")), money(row.get("fundBalance")),
                    money(row.get("forecastInflow")), money(row.get("forecastOutflow")),
                    money(row.get("financingAmount")), money(row.get("fundingGap")));
        }
    }

    public record FinanceOperationsWorkspaceVO(FinanceOperationsSummaryVO summary,
                                               List<PaymentScheduleVO> schedules,
                                               List<FinanceAlertVO> alerts,
                                               List<FinanceSnapshotVO> snapshots) {}

    public record CashForecastCycleVO(String id, String projectId, String projectName, String cycleCode, String forecastName,
                                       LocalDate asOfDate, LocalDate horizonStart, LocalDate horizonEnd,
                                      String scenario, String openingBalance, String status,
                                      Integer versionNo, String previousCycleId, LocalDateTime sourceCutoffAt) {
        public static CashForecastCycleVO from(Map<String,Object> row) {
            return new CashForecastCycleVO(idString(row.get("id")), idString(row.get("project_id")), text(row.get("project_name")),
                    text(row.get("cycle_code")), text(row.get("forecast_name")), date(row.get("as_of_date")),
                    date(row.get("horizon_start")), date(row.get("horizon_end")), text(row.get("scenario")),
                    money(row.get("opening_balance")), text(row.get("status")), integer(row.get("version_no")),
                    idString(row.get("previous_cycle_id")), dateTime(row.get("source_cutoff_at")));
        }
    }

    public record CashForecastLineVO(String id, String cycleId, LocalDate forecastDate,
                                     String plannedInflow, String plannedOutflow, String financingAmount,
                                     String projectedBalance, String gapAmount, String actualInflow,
                                     String actualOutflow, String inflowVariance, String outflowVariance) {
        public static CashForecastLineVO from(Map<String,Object> row) {
            return new CashForecastLineVO(idString(row.get("id")), idString(row.get("cycle_id")),
                    date(row.get("forecast_date")), money(row.get("planned_inflow")),
                    money(row.get("planned_outflow")), money(row.get("financing_amount")),
                    money(row.get("projected_balance")), money(row.get("gap_amount")),
                    money(row.get("actual_inflow")), money(row.get("actual_outflow")),
                    money(row.get("inflow_variance")), money(row.get("outflow_variance")));
        }
    }

    public record CashFundingActionVO(String id, String cycleId, String projectId, String lineId,
                                      String actionType, LocalDate plannedDate, String amount,
                                      String status, String actualAmount, String completionReference) {
        public static CashFundingActionVO from(Map<String,Object> row) {
            return new CashFundingActionVO(idString(row.get("id")), idString(row.get("cycle_id")), idString(row.get("project_id")),
                    idString(row.get("line_id")), text(row.get("action_type")), date(row.get("planned_date")),
                    money(row.get("amount")), text(row.get("status")), money(row.get("actual_amount")),
                    text(row.get("completion_reference")));
        }
    }

    public record CashJournalFactVO(String id, String entryNo, String direction, String amount,
                                    LocalDate businessDate, String sourceType, String sourceId, String status) {
        public static CashJournalFactVO from(Map<String,Object> row) {
            return new CashJournalFactVO(idString(row.get("id")), text(row.get("entry_no")),
                    text(row.get("direction")), money(row.get("amount")), date(row.get("business_date")),
                    text(row.get("source_type")), idString(row.get("source_id")), text(row.get("status")));
        }
    }

    public record CashForecastTraceVO(CashForecastCycleVO cycle, List<CashForecastLineVO> lines,
                                      List<CashFundingActionVO> actions, List<CashJournalFactVO> actualJournals) {}

    public record AccountingEntryVO(String id, String entryCode, LocalDate entryDate, String entryType,
                                    String sourceType, String sourceId, String projectId, String contractId,
                                    String entryStatus, String reviewStatus, String totalDebit,
                                    String totalCredit, String originalEntryId, String reversedEntryId,
                                    LocalDateTime postedAt, LocalDateTime reversedAt, Integer version) {
        public static AccountingEntryVO from(AccountingEntry entry) {
            return new AccountingEntryVO(idString(entry.getId()), entry.getEntryCode(), entry.getEntryDate(),
                    entry.getEntryType(), entry.getSourceType(), idString(entry.getSourceId()), idString(entry.getProjectId()),
                    idString(entry.getContractId()), entry.getEntryStatus(), entry.getReviewStatus(),
                    money(entry.getTotalDebit()), money(entry.getTotalCredit()), idString(entry.getOriginalEntryId()),
                    idString(entry.getReversedEntryId()), entry.getPostedAt(), entry.getReversedAt(), entry.getVersion());
        }

        public static AccountingEntryVO from(Map<String,Object> row) {
            return new AccountingEntryVO(idString(row.get("id")), text(row.get("entry_code")),
                    date(row.get("entry_date")), text(row.get("entry_type")), text(row.get("source_type")),
                    idString(row.get("source_id")), idString(row.get("project_id")), idString(row.get("contract_id")),
                    text(row.get("entry_status")), text(row.get("review_status")), money(row.get("total_debit")),
                    money(row.get("total_credit")), idString(row.get("original_entry_id")), idString(row.get("reversed_entry_id")),
                    dateTime(row.get("posted_at")), dateTime(row.get("reversed_at")), integer(row.get("version")));
        }
    }

    public record AccountingEntryLineVO(String id, Integer lineNo, String direction, String costSubjectId,
                                        String costSubjectName, String accountCode, String accountName,
                                        String amount, String summary) {
        public static AccountingEntryLineVO from(AccountingEntryLine line, Map<Long,String> subjectNames) {
            return new AccountingEntryLineVO(idString(line.getId()), line.getLineNo(), line.getDirection(),
                    idString(line.getCostSubjectId()), line.getCostSubjectId() == null ? null : subjectNames.get(line.getCostSubjectId()), line.getAccountCode(),
                    line.getAccountName(), money(line.getAmount()), line.getSummary());
        }
    }

    public record AccountingEntryDetailVO(AccountingEntryVO entry, List<AccountingEntryLineVO> lines) {}

    public record FinancePeriodVO(String id, String periodCode, Integer fiscalYear, Integer fiscalMonth,
                                  LocalDate startDate, LocalDate endDate, String status, Integer issueCount,
                                  LocalDateTime lastCheckAt, LocalDateTime closedAt,
                                  LocalDateTime reopenedAt, String reopenReason, Integer version) {
        public static FinancePeriodVO from(Map<String,Object> row) {
            return new FinancePeriodVO(idString(row.get("id")), text(row.get("period_code")),
                    integer(row.get("fiscal_year")), integer(row.get("fiscal_month")),
                    date(row.get("start_date")), date(row.get("end_date")), text(row.get("status")),
                    integer(row.get("issue_count")), dateTime(row.get("last_check_at")),
                    dateTime(row.get("closed_at")), dateTime(row.get("reopened_at")),
                    text(row.get("reopen_reason")), integer(row.get("version")));
        }
    }

    public record PeriodCheckVO(String id, String checkType, String status, Integer issueCount, String detail) {
        public static PeriodCheckVO from(Map<String,Object> row) {
            return new PeriodCheckVO(idString(row.get("id")), text(row.get("check_type")),
                    text(row.get("status") == null ? row.get("check_status") : row.get("status")),
                    integer(row.get("issue_count")),
                    text(row.get("detail_json") == null ? row.get("detail") : row.get("detail_json")));
        }
    }

    public record ReconciliationVO(String id, String type, String status, String expectedAmount,
                                   String actualAmount, String differenceAmount, String businessId) {
        public static ReconciliationVO from(Map<String,Object> row) {
            String type = text(row.get("account_type") == null ? row.get("direction") : row.get("account_type"));
            return new ReconciliationVO(idString(row.get("id")), type, text(row.get("status")),
                    money(row.get("expected_amount") == null ? row.get("bank_amount") : row.get("expected_amount")),
                    money(row.get("actual_amount") != null ? row.get("actual_amount")
                            : row.get("ledger_amount") != null ? row.get("ledger_amount") : row.get("business_amount")),
                    money(row.get("difference_amount")), idString(row.get("business_id")));
        }
    }

    public record FinancialCloseTraceVO(FinancePeriodVO period, List<PeriodCheckVO> checks,
                                        List<ReconciliationVO> accountReconciliations,
                                        List<ReconciliationVO> bankReconciliations,
                                        List<AccountingEntryVO> entries) {}

    public record TrialBalanceVO(String accountCode, String accountName, String debit, String credit) {
        public static TrialBalanceVO from(Map<String,Object> row) {
            return new TrialBalanceVO(text(row.get("account_code")), text(row.get("account_name")),
                    money(row.get("debit")), money(row.get("credit")));
        }
    }

    public record CashFlowVO(String inflow, String outflow) {
        public static CashFlowVO from(Map<String,Object> row) {
            return new CashFlowVO(money(row.get("inflow")), money(row.get("outflow")));
        }
    }

    public record FinancialStatementVO(FinancePeriodVO period, List<TrialBalanceVO> trialBalance,
                                       String receivableOutstanding, String payableOutstanding,
                                       CashFlowVO cashFlow) {}

    private static String idString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String money(Object value) {
        return value == null ? null : new BigDecimal(value.toString()).toPlainString();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer integer(Object value) {
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private static LocalDate date(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate result) return result;
        if (value instanceof java.sql.Date result) return result.toLocalDate();
        if (value instanceof LocalDateTime result) return result.toLocalDate();
        if (value instanceof java.sql.Timestamp result) return result.toLocalDateTime().toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private static LocalDateTime dateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime result) return result;
        if (value instanceof java.sql.Timestamp result) return result.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }
}
