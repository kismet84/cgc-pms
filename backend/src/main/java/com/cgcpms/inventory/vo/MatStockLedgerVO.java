package com.cgcpms.inventory.vo;

import com.cgcpms.common.result.PageResult;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatStockTxn;
import lombok.Data;

/**
 * 库存台账响应对象：当前库存余额 + 分页流水记录。
 */
@Data
public class MatStockLedgerVO {

    /** 当前库存余额（可能为 null，表示该仓库+物料尚无库存记录） */
    // TODO: 创建 MatStockVO 替代直接暴露实体（stock 字段含 version 等内部字段）
    private MatStock stock;

    /** 分页流水记录 */
    // TODO: 创建 MatStockTxnVO 替代直接暴露实体（txns 字段直接暴露 MatStockTxn 实体）
    private PageResult<MatStockTxn> txns;
}
