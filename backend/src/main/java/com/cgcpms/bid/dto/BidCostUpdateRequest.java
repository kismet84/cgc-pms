package com.cgcpms.bid.dto;

import com.cgcpms.bid.entity.BidCost;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BidCostUpdateRequest(
        @NotBlank(message = "投标项目名称不能为空")
        @Size(max = 200, message = "投标项目名称不能超过200字")
        String bidProjectName,
        @Size(max = 500, message = "备注不能超过500字")
        String remark) {

    public BidCostUpdateRequest {
        bidProjectName = bidProjectName == null ? null : bidProjectName.trim();
        remark = remark == null ? null : remark.trim();
    }

    public BidCost toEntity(Long id) {
        BidCost bid = new BidCost();
        bid.setId(id);
        bid.setBidProjectName(bidProjectName);
        bid.setRemark(remark == null || remark.isEmpty() ? null : remark);
        return bid;
    }
}
