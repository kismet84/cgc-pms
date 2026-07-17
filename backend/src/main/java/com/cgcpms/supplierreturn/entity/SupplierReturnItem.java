package com.cgcpms.supplierreturn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_supplier_return_item")
public class SupplierReturnItem extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long returnId;
    private Long receiptItemId;
    private Long orderItemId;
    private Long originalStockTxnId;
    private Long originalCostItemId;
    private Long materialId;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal amount;
}
