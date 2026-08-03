package com.cgcpms.bid.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Project-domain port used by the bid WON transaction. */
public interface BidAwardProjectCreator {

    Long createOrGet(BidAwardProjectCommand command);

    record BidAwardProjectCommand(
            Long tenantId,
            Long bidCostId,
            String bidCode,
            String projectName,
            String ownerUnit,
            String projectAddress,
            BigDecimal contractAmount,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate) {
    }
}
