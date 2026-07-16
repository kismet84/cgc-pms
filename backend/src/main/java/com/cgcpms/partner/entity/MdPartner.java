package com.cgcpms.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_partner")
public class MdPartner extends BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    private String partnerCode;

    @NotBlank
    private String partnerName;

    @NotBlank
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

    @DecimalMin(value = "0", message = "供应商默认提前期不能小于0天")
    @DecimalMax(value = "3650", message = "供应商默认提前期不能大于3650天")
    @Digits(integer = 4, fraction = 0, message = "供应商默认提前期必须为整数")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal defaultLeadDays;

    @JsonIgnore
    @TableField(exist = false)
    private boolean defaultLeadDaysSpecified;

    @JsonSetter("defaultLeadDays")
    public void setDefaultLeadDays(BigDecimal defaultLeadDays) {
        this.defaultLeadDays = defaultLeadDays;
        this.defaultLeadDaysSpecified = true;
    }

    public void preserveDefaultLeadDays(BigDecimal defaultLeadDays) {
        this.defaultLeadDays = defaultLeadDays;
    }
}
