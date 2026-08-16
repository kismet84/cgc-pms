package com.cgcpms.cost.constant;

import java.util.Map;
import java.util.Set;

public final class AccountingSubjectCatalog {

    public static final String CASH = "1001";
    public static final String BANK_BASIC = "1002.01";
    public static final String BANK_GENERAL = "1002.02";
    public static final String BANK_PROJECT = "1002.03";
    public static final String RECEIVABLE = "1122";
    public static final String CONTRACT_ASSET_UNBILLED = "1126.01";
    public static final String CONTRACT_ASSET_RETENTION = "1126.02";
    public static final String PAYABLE_MATERIAL = "2202.01";
    public static final String PAYABLE_EQUIPMENT = "2202.02";
    public static final String PAYABLE_LABOR = "2202.03";
    public static final String PAYABLE_SUBCONTRACT = "2202.04";
    public static final String PAYABLE_MACHINERY = "2202.05";
    public static final String CONTRACT_LIABILITY_ADVANCE = "2206.01";
    public static final String CONTRACT_LIABILITY_UNPERFORMED = "2206.02";
    public static final String PRICE_SETTLEMENT = "4401.01";
    public static final String REVENUE_CARRYOVER = "4401.02";
    public static final String CONSTRUCTION_REVENUE = "6001.01";

    public static final Map<String, String> FULFILLMENT_BY_CATEGORY = Map.of(
            "MATERIAL", "1451.01", "EQUIPMENT", "1451.02", "LABOR", "1451.03",
            "MACHINERY", "1451.04", "SUBCONTRACT", "1451.05", "MEASURES", "1451.06",
            "SITE_MANAGEMENT", "1451.07", "OTHER", "1451.08");
    public static final Map<String, String> EXPENSE_BY_CATEGORY = Map.of(
            "MATERIAL", "6401.01", "EQUIPMENT", "6401.02", "LABOR", "6401.03",
            "MACHINERY", "6401.04", "SUBCONTRACT", "6401.05", "MEASURES", "6401.06",
            "SITE_MANAGEMENT", "6401.07", "OTHER", "6401.08");

    public static final Set<String> GOVERNED_CODES = Set.of(
            "1001", "1002", "1002.01", "1002.02", "1002.03", "1122", "1126", "1126.01", "1126.02",
            "1451", "1451.01", "1451.02", "1451.03", "1451.04", "1451.05", "1451.06", "1451.07", "1451.08",
            "1601", "1601.01", "1601.02", "1601.03", "1601.04", "1601.05", "2001", "2001.01", "2001.02",
            "2202", "2202.01", "2202.02", "2202.03", "2202.04", "2202.05", "2206", "2206.01", "2206.02",
            "2211", "2211.01", "2221", "2221.01", "2221.01.01", "2221.01.02", "2221.01.03", "2221.02", "2221.03", "2221.04",
            "4401", "4401.01", "4401.02", "6001", "6001.01", "6401", "6401.01", "6401.02", "6401.03",
            "6401.04", "6401.05", "6401.06", "6401.07", "6401.08", "6402", "6402.01", "6402.02", "6402.03",
            "6403", "6403.01", "6403.02", "6403.03", "6403.04", "6403.05", "6403.06", "6403.07", "6403.08",
            "6602", "6602.01", "6602.02", "6602.03", "6602.04", "6602.05", "6602.06", "6603", "6603.01", "6603.02", "6801");

    private AccountingSubjectCatalog() {
    }
}
