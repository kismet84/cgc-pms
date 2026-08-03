package com.cgcpms.bid.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工程投标记录根实体。
 * 投标费用仍由现金日记账/成本事实聚合，不在此表冗余存储。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bid_cost")
public class BidCost extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 中标后关联的项目ID，未中标时为 NULL */
    private Long projectId;

    private String bidCode;

    private String bidProjectName;

    /** PREPARING/SUBMITTED/EVALUATING/WON/LOST/CLOSED/WITHDRAWN/TERMINATED */
    private String bidStatus;

    private String bidSectionName;
    private String tendereeName;
    private String agencyName;
    private String projectLocation;
    private String tenderMethod;
    private String sourcePlatform;
    private String externalBidNo;
    private String sourceUrl;
    private Long ownerId;
    @TableField(exist = false)
    private String ownerName;
    private LocalDate documentReceivedDate;
    private LocalDateTime bidDeadlineAt;
    private LocalDateTime openingAt;
    private LocalDate bidValidUntil;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private BigDecimal ceilingPrice;
    private BigDecimal finalBidPrice;
    private LocalDate resultAt;
    private String resultReason;
    /** 服务端按归档现金日记账和成本类科目计算，不落库。 */
    @TableField(exist = false)
    private BigDecimal bidExpense;
}
