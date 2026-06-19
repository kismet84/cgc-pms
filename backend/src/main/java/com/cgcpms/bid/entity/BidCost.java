package com.cgcpms.bid.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 招投标前期费用头表实体。
 * 金额完全由 cost_item 聚合 (source_type=BID_COST)，不在此表冗余存储。
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

    private String bidProjectName;

    /** BIDDING 投标中 / WON 已中标 / LOST 未中标 */
    private String bidStatus;
}
