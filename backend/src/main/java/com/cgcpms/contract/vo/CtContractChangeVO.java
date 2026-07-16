package com.cgcpms.contract.vo;

import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContractChange;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CtContractChangeVO {

    private String id;
    private String projectId;
    private String contractId;
    private String changeCode;
    private String changeName;
    private String businessMatterKey;
    private String changeType;
    private BigDecimal beforeAmount;
    private BigDecimal changeAmount;
    private BigDecimal afterAmount;
    private String reason;
    private String approvalStatus;
    private Integer effectiveFlag;
    private Integer costGeneratedFlag;
    private String remark;
    private String createdAt;
    private String updatedAt;

    public static CtContractChangeVO fromEntity(CtContractChange entity) {
        CtContractChangeVO vo = new CtContractChangeVO();
        vo.setId(entity.getId() == null ? null : entity.getId().toString());
        vo.setProjectId(entity.getProjectId() == null ? null : entity.getProjectId().toString());
        vo.setContractId(entity.getContractId() == null ? null : entity.getContractId().toString());
        vo.setChangeCode(entity.getChangeCode());
        vo.setChangeName(entity.getChangeName());
        vo.setBusinessMatterKey(entity.getBusinessMatterKey());
        vo.setChangeType(entity.getChangeType());
        vo.setBeforeAmount(entity.getBeforeAmount());
        vo.setChangeAmount(entity.getChangeAmount());
        vo.setAfterAmount(entity.getAfterAmount());
        vo.setReason(entity.getReason());
        vo.setApprovalStatus(entity.getApprovalStatus());
        vo.setEffectiveFlag(entity.getEffectiveFlag());
        vo.setCostGeneratedFlag(entity.getCostGeneratedFlag());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedTime() == null ? null : DateTimeUtils.DTF.format(entity.getCreatedTime()));
        vo.setUpdatedAt(entity.getUpdatedTime() == null ? null : DateTimeUtils.DTF.format(entity.getUpdatedTime()));
        return vo;
    }
}
