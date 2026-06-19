package com.cgcpms.accounting.strategy;

import com.cgcpms.accounting.entity.AccountingEntry;

/**
 * 会计凭证生成策略接口 — 模式对齐 CostGenerationStrategy。
 */
public interface EntryGenerationStrategy {

    /**
     * 支持的来源类型（与 cost_item.source_type 对应）。
     */
    String supportSourceType();

    /**
     * 根据来源单据生成会计分录。
     *
     * @param sourceId  来源单据ID
     * @param entryType 凭证类型（BID_COST/MATERIAL/LABOR/OVERHEAD/REVENUE/SETTLEMENT）
     * @return 生成的凭证，null 表示无需生成
     */
    AccountingEntry generate(Long sourceId, String entryType);
}
