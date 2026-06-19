package com.cgcpms.bid.entity;

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

/**
 * 投标保证金实体 — 独立于合同履约成本核算。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bid_deposit")
public class BidDeposit extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long bidCostId;

    /** BID 投标保证金 / PERFORMANCE 履约保证金 */
    private String depositType;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal depositAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal returnedAmount;

    /** PAID 已缴 / RETURNED 已退回 / FORFEITED 已没收 */
    private String depositStatus;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate paidDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnedDate;
}
