package com.cgcpms.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record_source_allocation")
public class PaymentRecordSourceAllocation {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long payRecordId;
    private Long paymentSourceId;
    private String sourceType;
    private Long sourceRefId;
    private BigDecimal allocatedAmount;
    private Long createdBy;
    private LocalDateTime createdAt;
}
