package com.cgcpms.revenue.vo;

import lombok.Data;

/**
 * 合同资产/负债余额视图对象。
 * 动态计算，不另建表存储。
 */
@Data
public class ContractRevenueBalanceVO {

    private String contractId;

    /** 累计确认收入 */
    private String totalConfirmedRevenue;

    /** 累计业主结算 */
    private String totalBilled;

    /** 合同资产 = MAX(0, 累计确认收入 - 累计业主结算) */
    private String contractAsset;

    /** 合同负债 = MAX(0, 累计业主结算 - 累计确认收入) */
    private String contractLiability;
}
