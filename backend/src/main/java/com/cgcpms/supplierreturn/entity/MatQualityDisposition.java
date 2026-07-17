package com.cgcpms.supplierreturn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_quality_disposition")
public class MatQualityDisposition extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long receiptId;
    private Long receiptItemId;
    private BigDecimal rejectedQuantity;
    private String dispositionAction;
    private String status;
    private BigDecimal resolvedQuantity;
    private LocalDateTime resolvedAt;
    @Version
    private Integer version;
}
