package com.cgcpms.tech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 总工程师最小技术域事项表。
 * 只承载技术方案、设计协调、技术审核、重大技术问题的闭环状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tech_item")
public class TechItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long projectId;

    /**
     * 来源类型：TECH_PLAN / DESIGN_COORDINATION / TECH_REVIEW / TECH_ISSUE
     */
    private String itemType;

    private String itemCode;

    private String itemTitle;

    private String itemLevel;

    private String itemStatus;

    private LocalDateTime discoveredAt;

    private LocalDateTime dueDate;

    private LocalDateTime closedAt;

    private Long responsibleUserId;
}
