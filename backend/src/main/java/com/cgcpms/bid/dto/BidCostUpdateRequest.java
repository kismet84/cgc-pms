package com.cgcpms.bid.dto;

import com.cgcpms.bid.entity.BidCost;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BidCostUpdateRequest(
        @NotBlank @Size(max = 200) String bidProjectName,
        @Size(max = 200) String bidSectionName,
        @Size(max = 200) String tendereeName,
        @Size(max = 200) String agencyName,
        @Size(max = 300) String projectLocation,
        @Size(max = 100) String tenderMethod,
        @Size(max = 100) String sourcePlatform,
        @Size(max = 100) String externalBidNo,
        @Size(max = 1000) String sourceUrl,
        Long ownerId,
        LocalDate documentReceivedDate,
        LocalDateTime bidDeadlineAt,
        LocalDateTime openingAt,
        LocalDate bidValidUntil,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal ceilingPrice,
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal finalBidPrice,
        @Size(max = 500) String remark) {

    public BidCostUpdateRequest {
        bidProjectName = trim(bidProjectName);
        bidSectionName = trim(bidSectionName);
        tendereeName = trim(tendereeName);
        agencyName = trim(agencyName);
        projectLocation = trim(projectLocation);
        tenderMethod = trim(tenderMethod);
        sourcePlatform = trim(sourcePlatform);
        externalBidNo = trim(externalBidNo);
        sourceUrl = trim(sourceUrl);
        remark = trim(remark);
    }

    public BidCost toEntity(Long id) {
        BidCost bid = new BidCost();
        bid.setId(id);
        bid.setBidProjectName(bidProjectName);
        bid.setBidSectionName(bidSectionName);
        bid.setTendereeName(tendereeName);
        bid.setAgencyName(agencyName);
        bid.setProjectLocation(projectLocation);
        bid.setTenderMethod(tenderMethod);
        bid.setSourcePlatform(sourcePlatform);
        bid.setExternalBidNo(externalBidNo);
        bid.setSourceUrl(sourceUrl);
        bid.setOwnerId(ownerId);
        bid.setDocumentReceivedDate(documentReceivedDate);
        bid.setBidDeadlineAt(bidDeadlineAt);
        bid.setOpeningAt(openingAt);
        bid.setBidValidUntil(bidValidUntil);
        bid.setPlannedStartDate(plannedStartDate);
        bid.setPlannedEndDate(plannedEndDate);
        bid.setCeilingPrice(ceilingPrice);
        bid.setFinalBidPrice(finalBidPrice);
        bid.setRemark(remark);
        return bid;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
