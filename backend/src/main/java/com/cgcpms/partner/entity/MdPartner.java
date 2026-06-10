package com.cgcpms.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_partner")
public class MdPartner extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private String partnerCode;

    private String partnerName;

    private String partnerType;

    private String creditCode;

    private String legalPerson;

    private String contactName;

    private String contactPhone;

    private String bankName;

    private String bankAccount;

    private String qualificationLevel;

    private Integer blacklistFlag;

    private String riskLevel;

    private String status;
}
