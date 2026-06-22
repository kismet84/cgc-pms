package com.cgcpms.inventory.vo;

import com.cgcpms.common.result.PageResult;
import lombok.Data;

/**
 * 库存台账响应对象：当前库存余额 + 分页流水记录。
 */
@Data
public class MatStockLedgerVO {

    /** 当前库存余额（可能为 null，表示该仓库+物料尚无库存记录） */
    private MatStockVO stock;

    /** 分页流水记录 */
    private PageResult<MatStockTxnVO> txns;
}
