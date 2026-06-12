package com.cgcpms.invoice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_invoice")
public class PayInvoice extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long payRecordId;

    private Long payApplicationId;

    private String invoiceNo;

    private String invoiceType;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal invoiceAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxRate;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate invoiceDate;

    private String verifyStatus;
}
