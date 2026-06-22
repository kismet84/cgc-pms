package com.cgcpms.inventory.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 库存 KPI VO — 合同台账 KPI 的库存对应版。
 */
@Data
public class StockKpiVO implements Serializable {

    /** 仓库总数 */
    private long warehouseCount;

    /** 低库存物料数（可用量 < 10） */
    private long lowStockCount;

    /** 当前页出入库统计：入库条数 */
    private long txnInCount;

    /** 当前页出入库统计：出库条数 */
    private long txnOutCount;

    /** 有库存的物料种类数 */
    private long materialTypeCount;
}
